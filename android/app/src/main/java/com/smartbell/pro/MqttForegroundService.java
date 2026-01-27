package com.smartbell.pro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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

@SuppressWarnings("deprecation")
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
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .build();

                startForeground(1, notification);

                connectMqtt();
            } else if ("com.smartbell.pro.ACTION_TRIGGER_CALL".equals(action)) {
                // This action is likely from a widget or direct intent to trigger a call
                // without necessarily starting the full foreground service for listening.
                // We need to ensure MQTT client is connected to send the message.
                // For a one-shot action, we might connect, send, then disconnect/stop.
                // Or, if the service is already running (e.g., from ACTION_START), it uses the
                // existing connection.
                // For simplicity, let's assume it's a one-shot trigger and connect if not
                // connected.
                // If the service is not running, it will be started, connect, send, then stop.

                host = intent.getStringExtra("host"); // Need host/port/clientId to connect if not already connected
                port = intent.getIntExtra("port", 1883);
                topic = intent.getStringExtra("topic");
                clientId = intent.getStringExtra("clientId"); // Used for 'from' field in payload

                if (mqttClient == null || !mqttClient.isConnected()) {
                    connectMqtt(); // Attempt to connect if not already connected
                }

                if (mqttClient != null && mqttClient.isConnected()) {
                    String targetId = intent.getStringExtra("targetId");
                    Log.d(TAG, "Triggering call from service to target: " + (targetId != null ? targetId : "全員"));

                    JSONObject payload = new JSONObject();
                    try {
                        payload.put("cmd", "call");
                        payload.put("from", clientId != null ? clientId : "デバイス");
                        if (targetId != null && !targetId.isEmpty()) {
                            payload.put("to", targetId);
                            mqttClient.publish("smartbell/call/" + targetId, payload.toString().getBytes(), 1, false);
                        } else {
                            mqttClient.publish(topic, payload.toString().getBytes(), 1, false);
                        }
                        Log.d(TAG, "Call triggered successfully.");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to send call from intent", e);
                    }
                } else {
                    Log.e(TAG, "MQTT client not connected, cannot trigger call.");
                }
                stopSelf(); // Stop the service after triggering the call
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
                    // Reconnect logic could go here, but Paho has automatic reconnect options
                    // usually?
                    // Or we just try to reconnect after a delay.
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload());
                    Log.d(TAG, "Message arrived: " + payload);
                    handleMessage(payload);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not needed for subscriber
                }
            });

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(false); // Changed to false for better persistence
            options.setConnectionTimeout(30);
            options.setKeepAliveInterval(30); // Shorter keepalive

            mqttClient.connect(options);

            // Subscribe to global topic
            if (topic != null) {
                mqttClient.subscribe(topic);
            }

            // Subscribe to device specific topic if available
            if (deviceId != null && !deviceId.isEmpty()) {
                mqttClient.subscribe("smartbell/call/" + deviceId);
                mqttClient.subscribe("smartbell/chat/" + deviceId);
            }

            Log.d(TAG, "MQTT Connected");

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
            } else if ("chat".equals(cmd)) {
                String sender = payload.optString("from", "誰か");
                String text = payload.optString("text", "メッセージが届きました");

                // Broadcast chat message to JS
                Intent broadcast = new Intent("com.smartbell.pro.CHAT_MESSAGE");
                broadcast.putExtra("data", payloadStr);
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

                // Show local notification for chat
                showChatNotification(sender, text);
            }

        } catch (Exception e) {
            Log.e(TAG, "JSON Parse error", e);
        }
    }

    public static final String ACTION_OPEN_CHAT = "com.smartbell.pro.ACTION_OPEN_CHAT";
    private static final String CHAT_CHANNEL_ID = "ChatChannel_V3";

    private void showChatNotification(String sender, String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(ACTION_OPEN_CHAT);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHAT_CHANNEL_ID)
                .setContentTitle(sender + " からのメッセージ")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setFullScreenIntent(pendingIntent, true) // Force heads-up
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(2, notification);
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

            // Chat Channel (High importance for Heads-up)
            NotificationChannel chatChannel = new NotificationChannel(
                    CHAT_CHANNEL_ID,
                    "SmartBell Chat Messages",
                    NotificationManager.IMPORTANCE_HIGH);
            chatChannel.setDescription("Notifications for incoming chat messages");
            chatChannel.enableVibration(true);
            chatChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            chatChannel.setBypassDnd(true); // Attempt to bypass if allowed
            manager.createNotificationChannel(chatChannel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
