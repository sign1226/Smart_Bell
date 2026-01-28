package com.smartbell.pro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import org.json.JSONObject;

@SuppressWarnings({ "deprecation", "unchecked" })
public class MqttForegroundService extends Service {
    private static final String TAG = "MqttService";
    private static final String CHANNEL_ID = "MqttServiceChannel";
    public static final String ACTION_START = "com.smartbell.pro.action.START";
    public static final String ACTION_STOP = "com.smartbell.pro.action.STOP";

    private MqttClient mqttClient;
    private String host;
    private int port;
    private String topic;
    private String clientId;
    private String deviceId;
    private android.os.PowerManager.WakeLock wakeLock;
    private final java.util.Map<Integer, String> deliveryTargets = new java.util.HashMap<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                // Acquire WakeLock
                if (wakeLock == null) {
                    android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(
                            Context.POWER_SERVICE);
                    wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,
                            "SmartBell:MqttWakeLock");
                }
                if (!wakeLock.isHeld()) {
                    wakeLock.acquire();
                }

                host = intent.getStringExtra("host");
                port = intent.getIntExtra("port", 1883);
                topic = intent.getStringExtra("topic");
                clientId = intent.getStringExtra("clientId");
                deviceId = intent.getStringExtra("deviceId");

                createNotificationChannel();
                Intent notificationIntent = new Intent(this, MainActivity.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(this,
                        0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

                Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("SmartBell Service")
                        .setContentText("Listening for incoming calls...")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .build();

                startForeground(1, notification);

                connectMqtt();
            } else if ("com.smartbell.pro.ACTION_TRIGGER_CALL".equals(action)) {
                // ウィジェットからの呼び出し - 既存のMQTT接続を使用してメッセージを送信
                if (mqttClient != null && mqttClient.isConnected()) {
                    String targetId = intent.getStringExtra("targetId");
                    String targetName = intent.getStringExtra("targetName");
                    if (targetName == null)
                        targetName = "全員";
                    Log.d(TAG, "Triggering call from service to target: " + (targetId != null ? targetId : "全員"));

                    JSONObject payload = new JSONObject();
                    try {
                        payload.put("cmd", "call");
                        payload.put("from", clientId != null ? clientId : "デバイス");
                        if (targetId != null && !targetId.isEmpty()) {
                            org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token = mqttClient
                                    .publish("smartbell/call/" + targetId, payload.toString().getBytes(), 1, false);
                            deliveryTargets.put(token.getMessageId(), targetName);
                        } else {
                            org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token = mqttClient.publish(topic,
                                    payload.toString().getBytes(), 1, false);
                            deliveryTargets.put(token.getMessageId(), "全員");
                        }
                        Log.d(TAG, "Call publish initiated.");
                        // Immediate Feedback: Sending...
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        final String finalTargetName = targetName;
                        mainHandler.post(() -> android.widget.Toast
                                .makeText(this, (targetId != null ? finalTargetName : "全員") + " へ呼出中...",
                                        android.widget.Toast.LENGTH_SHORT)
                                .show());
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to send call from intent", e);
                    }
                } else {
                    Log.e(TAG, "MQTT client not connected, cannot trigger call.");
                    // ユーザーに接続されていないことを通知
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> android.widget.Toast
                            .makeText(this, "接続エラー: アプリを開いて再試行してください", android.widget.Toast.LENGTH_LONG).show());
                }
                // 注意: stopSelf()を削除 - サービスを継続して実行
            } else if (ACTION_STOP.equals(action)) {
                disconnectMqtt();
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                } else {
                    stopForeground(true);
                }
                stopSelf();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void connectMqtt() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                return;
            }

            String brokerUrl = "tcp://" + host + ":" + port; // Paho uses tcp:// for non-ws
            // Note: If using WebSocket via Paho, it's ws://, but standard Paho often
            // prefers TCP.
            // However, the web client uses WebSockets.
            // If the broker supports both, we should likely use TCP for Android Native for
            // better stability?
            // Or use WS if that's what the broker is configured for.
            // Let's assume the user might have configured a WS port for the web app.
            // If the broker is mosquitto, usually 1883 is TCP, 9001 or 8083 is WS.
            // The user config has "port" which is likely the WS port since the React app
            // uses it.
            // We might need to ask or assume.
            // For now, let's try to use the URI provided. Paho Java supports "ws://".

            // Adjust schema based on port if needed, but let's trust the input for a moment
            // or fallback.
            // Actually, usually React app uses WS port (e.g. 8083), but Android Native
            // might work better with TCP (1883).
            // But we only have one port in config currently.
            // Let's assume we use the same protocol (WS) if the port indicates it, or we
            // try to guess.
            // Paho Java client supports `ws://host:port`.

            brokerUrl = "ws://" + host + ":" + port;

            Log.d(TAG, "Connecting to " + brokerUrl);

            mqttClient = new MqttClient(brokerUrl, clientId + "_android", new MemoryPersistence());
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    Log.e(TAG, "Connection lost", cause);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload());
                    Log.d(TAG, "Message arrived: " + payload);
                    handleMessage(payload);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    Log.d(TAG, "Delivery complete for msgId: " + token.getMessageId());
                    String targetName = deliveryTargets.remove(token.getMessageId());
                    if (targetName != null) {
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> android.widget.Toast.makeText(MqttForegroundService.this,
                                targetName + " へ送信しました", android.widget.Toast.LENGTH_SHORT).show());
                    }
                }
            });

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);
            options.setConnectionTimeout(60);
            options.setKeepAliveInterval(60);

            // LWT (Last Will and Testament)
            if (deviceId != null && !deviceId.isEmpty()) {
                String presenceTopic = "smartbell/presence/" + deviceId + "/android";
                options.setWill(presenceTopic, "offline".getBytes(), 1, true);
            }

            mqttClient.connect(options, null, new org.eclipse.paho.client.mqttv3.IMqttActionListener() {
                @Override
                public void onSuccess(org.eclipse.paho.client.mqttv3.IMqttToken asyncActionToken) {
                    Log.d(TAG, "MQTT Connected successfully");
                    publishOnlineStatus();
                    subscribeAll();
                }

                @Override
                public void onFailure(org.eclipse.paho.client.mqttv3.IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "MQTT Connect Failed", exception);
                }
            });

        } catch (MqttException e) {
            Log.e(TAG, "MQTT Connect Error", e);
        }
    }

    private void publishOnlineStatus() {
        if (mqttClient != null && mqttClient.isConnected() && deviceId != null && !deviceId.isEmpty()) {
            try {
                String presenceTopic = "smartbell/presence/" + deviceId + "/android";
                mqttClient.publish(presenceTopic, "online".getBytes(), 1, true);
                Log.d(TAG, "Published Online Status");
            } catch (MqttException e) {
                Log.e(TAG, "Failed to publish online status", e);
            }
        }
    }

    private void subscribeAll() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                if (topic != null)
                    mqttClient.subscribe(topic);
                if (deviceId != null && !deviceId.isEmpty()) {
                    mqttClient.subscribe("smartbell/call/" + deviceId);
                    mqttClient.subscribe("smartbell/chat/" + deviceId);
                    mqttClient.subscribe("smartbell/chat/all");
                }
                Log.d(TAG, "Subscribed to all topics");
            }
        } catch (MqttException e) {
            Log.e(TAG, "MQTT Connect Error", e);
        }
    }

    private void disconnectMqtt() {
        if (mqttClient != null) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
            } catch (MqttException e) {
                e.printStackTrace();
            }
            mqttClient = null;
        }
    }

    private void handleMessage(String payloadStr) {
        try {
            JSONObject payload = new JSONObject(payloadStr);
            String cmd = payload.optString("cmd");

            if ("call".equals(cmd)) {
                String caller = payload.optString("from", "呼び出し中");
                String callerId = payload.optString("fromId", "");

                // Skip if from self
                if (callerId.equals(deviceId))
                    return;

                // Send Ack back
                if (mqttClient != null && mqttClient.isConnected() && !callerId.isEmpty()) {
                    try {
                        JSONObject ackPayload = new JSONObject();
                        ackPayload.put("cmd", "ack");
                        ackPayload.put("forCmd", "call");
                        ackPayload.put("from", clientId);
                        ackPayload.put("fromId", deviceId);
                        ackPayload.put("timestamp", System.currentTimeMillis());
                        mqttClient.publish("smartbell/call/" + callerId, ackPayload.toString().getBytes(), 1, false);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to send ack", e);
                    }
                }

                // Launch Activity
                Intent intent = new Intent(this, IncomingCallActivity.class);
                intent.putExtra("caller_name", caller);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);

                // Also broadcast to JS if app is running
                Intent broadcast = new Intent("com.smartbell.pro.SHOW_CALL");
                broadcast.putExtra("data", payloadStr);
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
            } else if ("ack".equals(cmd)) {
                String forCmd = payload.optString("forCmd");
                if ("call".equals(forCmd)) {
                    String fromName = payload.optString("from", "相手");
                    showCallDeliveredNotification(fromName);
                }
            } else if ("chat".equals(cmd)) {
                String sender = payload.optString("from", "誰か");
                String text = payload.optString("text", "メッセージが届きました");
                String fromId = payload.optString("fromId", "");

                // Skip if message is from self
                if (fromId.equals(deviceId)) {
                    Log.d(TAG, "Ignoring self-sent chat message");
                    return;
                }

                // Broadcast chat message to JS
                Intent broadcast = new Intent("com.smartbell.pro.CHAT_MESSAGE");
                broadcast.putExtra("data", payloadStr);
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

                // Store message for later retrieval by UI (Drain logic)
                savePendingChatMessage(payloadStr);

                // Send Ack for chat if targeted to us or broadcast
                String msgId = payload.optString("id");
                if (!msgId.isEmpty()) {
                    try {
                        JSONObject ackPayload = new JSONObject();
                        ackPayload.put("cmd", "ack");
                        ackPayload.put("from", clientId);
                        ackPayload.put("fromId", deviceId);
                        ackPayload.put("forCmd", "chat");
                        ackPayload.put("msgId", msgId);
                        ackPayload.put("timestamp", System.currentTimeMillis());
                        mqttClient.publish("smartbell/chat/" + fromId, ackPayload.toString().getBytes(), 1, false);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to send chat ack", e);
                    }
                }

                // Show local notification for chat
                showChatNotification(sender, text);
            } else if ("ack".equals(cmd)) {
                String forCmd = payload.optString("forCmd");
                if ("call".equals(forCmd)) {
                    String fromName = payload.optString("from", "相手");
                    showCallDeliveredNotification(fromName);
                } else if ("chat".equals(forCmd)) {
                    // Chat ack received - could notify or just let JS handle it if app is open
                    // For now, let's broadcast it to JS
                    Intent broadcast = new Intent("com.smartbell.pro.CHAT_ACK");
                    broadcast.putExtra("data", payloadStr);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "JSON Parse error", e);
        }
    }

    private void showCallDeliveredNotification(String targetName) {
        // Show Toast for immediate feedback even in background
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> android.widget.Toast
                .makeText(this, targetName + " に着信中", android.widget.Toast.LENGTH_SHORT).show());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            // Reuse service channel or create a new high priority one for feedback
            // Using service channel for now but with higher visibility if possible, or just
            // a toast?
            // Background service can't show Toast easily on Android 12+.
            // Notification is better.

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("呼び出し完了")
                    .setContentText(targetName + " に呼び出しが届きました")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);

            manager.notify(3, builder.build()); // ID 3 for ack notifications
        }
    }

    public static final String ACTION_OPEN_CHAT = "com.smartbell.pro.ACTION_OPEN_CHAT";
    private static String currentChatChannelId = "ChatChannel_V3";

    private void showChatNotification(String sender, String text) {
        // 更新された通知音設定を読み込む
        android.content.SharedPreferences prefs = getSharedPreferences("com.smartbell.pro.settings",
                Context.MODE_PRIVATE);
        String chatUri = prefs.getString("chat_ringtone_uri", null);

        // Androidの仕様により、一度作成されたチャンネルの音は変更できないため、
        // 設定が変わっていたら別のIDでチャンネルを再作成する
        String newChannelId = "ChatChannel_" + (chatUri != null ? chatUri.hashCode() : "default");

        if (!newChannelId.equals(currentChatChannelId)) {
            currentChatChannelId = newChannelId;
            createChatNotificationChannel(chatUri);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(ACTION_OPEN_CHAT);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, currentChatChannelId)
                .setContentTitle(sender + " からのメッセージ")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setFullScreenIntent(pendingIntent, true);

        if (chatUri == null || chatUri.isEmpty()) {
            // システムデフォルト音を設定
            builder.setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION));
            // 全てのデフォルト（音・振動・ライト）を有効化
            builder.setDefaults(NotificationCompat.DEFAULT_ALL);
        } else {
            // カスタム音を設定
            builder.setSound(Uri.parse(chatUri));
            // 振動とライトはデフォルトを使用（音は明示的に設定済み）
            builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE | NotificationCompat.DEFAULT_LIGHTS);
        }

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(2, builder.build());
    }

    private void createChatNotificationChannel(String soundUri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel chatChannel = new NotificationChannel(
                    currentChatChannelId,
                    "SmartBell Chat Messages",
                    NotificationManager.IMPORTANCE_HIGH);
            chatChannel.setDescription("Notifications for incoming chat messages");
            chatChannel.enableVibration(true);
            chatChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            chatChannel.setBypassDnd(true);

            if (soundUri != null && !soundUri.isEmpty()) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                chatChannel.setSound(Uri.parse(soundUri), attributes);
            }

            manager.createNotificationChannel(chatChannel);
        }
    }

    private synchronized void savePendingChatMessage(String payload) {
        android.content.SharedPreferences prefs = getSharedPreferences("PendingChats", Context.MODE_PRIVATE);
        String current = prefs.getString("messages", "[]");
        try {
            org.json.JSONArray array = new org.json.JSONArray(current);
            array.put(new JSONObject(payload));
            prefs.edit().putString("messages", array.toString()).apply();
            Log.d(TAG, "Chat message stored in SharedPreferences");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save pending chat", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);

            // Service Channel
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "SmartBell Background Service",
                    NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(serviceChannel);

            // 初回のチャットチャンネル作成 (現在の設定を反映)
            android.content.SharedPreferences prefs = getSharedPreferences("com.smartbell.pro.settings",
                    Context.MODE_PRIVATE);
            String chatUri = prefs.getString("chat_ringtone_uri", null);
            currentChatChannelId = "ChatChannel_" + (chatUri != null ? chatUri.hashCode() : "default");
            createChatNotificationChannel(chatUri);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
