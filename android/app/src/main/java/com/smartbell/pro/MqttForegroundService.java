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
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
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
    public static final String ACTION_TRIGGER_CALL = "com.smartbell.pro.ACTION_TRIGGER_CALL";

    private MqttAsyncClient mqttClient;
    private String host;
    private int port;
    private String topic;
    private String clientId;
    private String deviceId;
    private android.os.PowerManager.WakeLock wakeLock;
    private android.net.wifi.WifiManager.WifiLock wifiLock;
    private final java.util.Map<Integer, String> deliveryTargets = new java.util.HashMap<>();
    private Intent pendingCallIntent;
    private android.net.ConnectivityManager.NetworkCallback networkCallback;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START.equals(action) || ACTION_TRIGGER_CALL.equals(action)) {
                startForegroundAndLocks();

                // Load settings from preferences if they are null (e.g., service restarted by
                // system)
                android.content.SharedPreferences prefs = getSharedPreferences("com.smartbell.pro.settings",
                        Context.MODE_PRIVATE);
                if (host == null)
                    host = prefs.getString("mqtt_host", null);
                if (port == 0)
                    port = prefs.getInt("mqtt_port", 1883);
                if (topic == null)
                    topic = prefs.getString("mqtt_topic", null);
                if (clientId == null)
                    clientId = prefs.getString("mqtt_client_id", null);
                if (deviceId == null)
                    deviceId = prefs.getString("mqtt_device_id", null);

                if (ACTION_TRIGGER_CALL.equals(action)) {
                    // Update from intent if provided
                    String incomingHost = intent.getStringExtra("host");
                    if (incomingHost != null)
                        host = incomingHost;
                    int incomingPort = intent.getIntExtra("port", 0);
                    if (incomingPort != 0)
                        port = incomingPort;

                    if (mqttClient != null && mqttClient.isConnected()) {
                        triggerCall(intent, true);
                    } else {
                        Log.d(TAG, "MQTT client not connected, queuing intent and connecting...");
                        pendingCallIntent = intent;
                        connectMqtt();
                    }
                } else {
                    // Normal START - refresh deviceId and connect
                    deviceId = intent.getStringExtra("deviceId");
                    // Save settings for recovery (Boot/AlarmManager)
                    prefs.edit()
                            .putString("mqtt_host", host)
                            .putInt("mqtt_port", port)
                            .putString("mqtt_topic", topic)
                            .putString("mqtt_client_id", clientId)
                            .putString("mqtt_device_id", deviceId)
                            .apply();
                    connectMqtt();
                }
            } else if (ACTION_STOP.equals(action)) {
                disconnectMqtt();
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                if (wifiLock != null && wifiLock.isHeld()) {
                    wifiLock.release();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                } else {
                    stopForeground(true);
                }
                unregisterNetworkCallback();
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private void startForegroundAndLocks() {
        // Acquire WakeLock
        if (wakeLock == null) {
            android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "SmartBell:MqttWakeLock");
        }
        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        // Acquire WiFi WakeLock
        if (wifiLock == null) {
            android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "SmartBell:WifiLock");
        }
        if (!wifiLock.isHeld()) {
            wifiLock.acquire();
        }

        createNotificationChannel();
        scheduleServiceHealthCheck();
        registerNetworkCallback();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SmartBell Service")
                .setContentText("着信を待機しています...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();

        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterNetworkCallback();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        cancelServiceHealthCheck();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "Task removed (App swiped away). Service continues to run.");
        // We can restart the service or just make sure it's STICKY
        // onStartCommand returns START_STICKY, so OS will try to keep it.
        // Also schedule a quick health check to be safe.
        scheduleServiceHealthCheck();
        super.onTaskRemoved(rootIntent);
    }

    private void scheduleServiceHealthCheck() {
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, MqttForegroundService.class);
        intent.setAction(ACTION_START);
        // Copy current params to recovery intent
        intent.putExtra("host", host);
        intent.putExtra("port", port);
        intent.putExtra("topic", topic);
        intent.putExtra("clientId", clientId);
        intent.putExtra("deviceId", deviceId);

        PendingIntent pendingIntent = PendingIntent.getService(this, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            // Android 6.0+ (M) Doze mode survival
            long nextTime = android.os.SystemClock.elapsedRealtime() + 10 * 60 * 1000; // 10 minutes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                        android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        nextTime,
                        pendingIntent);
            } else {
                alarmManager.set(
                        android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        nextTime,
                        pendingIntent);
            }
            Log.d(TAG, "Health check scheduled with setAndAllowWhileIdle (10 mins)");
        }
    }

    private void cancelServiceHealthCheck() {
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, MqttForegroundService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarmManager != null && pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    private void connectMqtt() {
        if (host == null || topic == null || clientId == null) {
            // Try to load from recovery
            android.content.SharedPreferences prefs = getSharedPreferences("com.smartbell.pro.settings",
                    Context.MODE_PRIVATE);
            if (host == null)
                host = prefs.getString("mqtt_host", null);
            if (port == 0)
                port = prefs.getInt("mqtt_port", 1883);
            if (topic == null)
                topic = prefs.getString("mqtt_topic", null);
            if (clientId == null)
                clientId = prefs.getString("mqtt_client_id", null);
            if (deviceId == null)
                deviceId = prefs.getString("mqtt_device_id", null);

            if (host == null) {
                Log.e(TAG, "Host is still null after recovery attempt. Cannot connect.");
                return;
            }
        }

        try {
            if (mqttClient != null) {
                if (mqttClient.isConnected()) {
                    Log.d(TAG, "MQTT client already connected.");
                    return;
                }
                // If it exists but not connected, try to connect using same client
                Log.d(TAG, "MQTT client exists but not connected. Re-connecting...");
            } else {
                String brokerUrl = "ws://" + host + ":" + port;
                Log.d(TAG, "Creating new MQTT client for " + brokerUrl);
                mqttClient = new MqttAsyncClient(brokerUrl, clientId + "_android", new MemoryPersistence());
                mqttClient.setCallback(new org.eclipse.paho.client.mqttv3.MqttCallbackExtended() {
                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {
                        Log.d(TAG, "MQTT Connection Complete. Reconnect: " + reconnect);
                        if (reconnect) {
                            publishOnlineStatus();
                            subscribeAll();
                            if (pendingCallIntent != null) {
                                Log.d(TAG, "Processing pending intent after reconnect");
                                triggerCall(pendingCallIntent, false);
                                pendingCallIntent = null;
                            }
                        }
                    }

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
                        // Removed redundant Toast here to prevent overlap with "Ringing" (Ack) Toast
                        deliveryTargets.remove(token.getMessageId());
                    }
                });
            }

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);
            options.setConnectionTimeout(30); // 縮小してタイムアウトを早く検知
            options.setKeepAliveInterval(30); // 頻度を上げて接続維持

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
                    if (pendingCallIntent != null) {
                        triggerCall(pendingCallIntent, false); // Silent for widget retry
                        pendingCallIntent = null;
                    }
                }

                @Override
                public void onFailure(org.eclipse.paho.client.mqttv3.IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "MQTT Connect Failed: " + (exception != null ? exception.getMessage() : "unknown"));
                    pendingCallIntent = null;
                }
            });

        } catch (MqttException e) {
            Log.e(TAG, "MQTT Connect Error", e);
        }
    }

    private void triggerCall(Intent intent, boolean showCallingToast) {
        String targetId = intent.getStringExtra("targetId");
        String targetName = intent.getStringExtra("targetName");
        if (targetName == null)
            targetName = "全員";
        Log.d(TAG, "Triggering call to target (silent=" + !showCallingToast + "): "
                + (targetId != null ? targetId : "全員"));

        JSONObject payload = new JSONObject();
        try {
            payload.put("cmd", "call");
            payload.put("from", clientId != null ? clientId : "デバイス");
            payload.put("fromId", deviceId);
            if (targetId != null && !targetId.isEmpty()) {
                mqttClient.publish("smartbell/call/" + targetId, payload.toString().getBytes(), 1, false);
            } else {
                mqttClient.publish(topic, payload.toString().getBytes(), 1, false);
            }
            Log.d(TAG, "Call publish initiated.");

            if (showCallingToast) {
                // Immediate Feedback: Calling...
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                final String finalTargetName = targetName;
                mainHandler.post(() -> {
                    android.widget.Toast.makeText(getApplicationContext(),
                            (targetId != null ? finalTargetName : "全員") + " へ呼出中...", android.widget.Toast.LENGTH_SHORT)
                            .show();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send call", e);
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
                    mqttClient.subscribe(topic, 1);
                if (deviceId != null && !deviceId.isEmpty()) {
                    mqttClient.subscribe("smartbell/call/" + deviceId, 1);
                    mqttClient.subscribe("smartbell/chat/" + deviceId, 1);
                    mqttClient.subscribe("smartbell/chat/all", 1);
                }
                Log.d(TAG, "Subscribed to all topics");
            }
        } catch (MqttException e) {
            Log.e(TAG, "Subscription Error", e);
        }
    }

    private void registerNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            if (networkCallback == null) {
                networkCallback = new android.net.ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(android.net.Network network) {
                        Log.d(TAG, "Network available, ensuring MQTT connection...");
                        // Use a small delay to let network stabilize
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            if (mqttClient == null || !mqttClient.isConnected()) {
                                connectMqtt();
                            } else {
                                publishOnlineStatus(); // Refresh status on new network
                            }
                        }, 1000);
                    }
                };
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
                Log.d(TAG, "NetworkCallback registered");
            }
        }
    }

    private void unregisterNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && networkCallback != null) {
            android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering network callback", e);
            }
            networkCallback = null;
            Log.d(TAG, "NetworkCallback unregistered");
        }
    }

    private void disconnectMqtt() {
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect();
                }
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
                } else if ("chat".equals(forCmd)) {
                    // Chat ack received - could notify or just let JS handle it if app is open
                    // For now, let's broadcast it to JS
                    Intent broadcast = new Intent("com.smartbell.pro.CHAT_ACK");
                    broadcast.putExtra("data", payloadStr);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
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
        // Load updated ringtone settings
        android.content.SharedPreferences prefs = getSharedPreferences("com.smartbell.pro.settings",
                Context.MODE_PRIVATE);
        String chatUri = prefs.getString("chat_ringtone_uri", null);

        // Due to Android limitations, channel sound cannot be changed once created.
        // If settings changed, recreate channel with a new ID.
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
            // Set system default sound
            builder.setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION));
            // Enable all defaults (sound, vibration, lights)
            builder.setDefaults(NotificationCompat.DEFAULT_ALL);
        } else {
            // Set custom sound
            builder.setSound(Uri.parse(chatUri));
            // Use defaults for vibration and lights (sound is explicitly set)
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
                    NotificationManager.IMPORTANCE_DEFAULT); // LOWからDEFAULT/HIGHへ
            serviceChannel.setDescription("SmartBellを常に待機状態にするための常駐サービスです。");
            serviceChannel.setShowBadge(false);
            manager.createNotificationChannel(serviceChannel);

            // Initial chat channel creation (reflecting current settings)
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
