package com.smartbell.pro;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

@SuppressWarnings("unchecked")
@CapacitorPlugin(name = "IncomingCall")
public class IncomingCallPlugin extends Plugin {

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        Context context = getContext();
        JSObject ret = new JSObject();

        // Overlay (SYSTEM_ALERT_WINDOW)
        boolean hasOverlay = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            hasOverlay = Settings.canDrawOverlays(context);
        }
        ret.put("overlay", hasOverlay);

        // Battery Optimization
        boolean isIgnoringBattery = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            isIgnoringBattery = pm.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        ret.put("batteryOptimization", isIgnoringBattery);

        // Notifications (Android 13+)
        boolean hasNotificationPermission = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = androidx.core.content.ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        ret.put("notifications", hasNotificationPermission);

        call.resolve(ret);
    }

    @PluginMethod
    public void requestNotificationPermission(PluginCall call) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.app.ActivityCompat.requestPermissions(getActivity(),
                    new String[] { android.Manifest.permission.POST_NOTIFICATIONS }, 1002);
        }
        call.resolve();
    }

    @PluginMethod
    public void requestOverlayPermission(PluginCall call) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getContext().getPackageName()));
            getContext().startActivity(intent);
        }
        call.resolve();
    }

    @PluginMethod
    public void requestIgnoreBatteryOptimization(PluginCall call) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            getContext().startActivity(intent);
        }
        call.resolve();
    }

    @PluginMethod
    public void show(PluginCall call) {
        Context context = getContext();
        String callerName = call.getString("name", "知らない人");

        // 通知チャンネルの作成（静かな通知用）
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) context
                .getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        String channelId = "bell_incoming_calls_silent";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId,
                    "Silent Incoming Calls",
                    android.app.NotificationManager.IMPORTANCE_LOW); // 低優先度（音なし・バナーなし）
            channel.setDescription("Shows incoming call overlay silently");
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            channel.setSound(null, null);
            channel.enableVibration(false);
            notificationManager.createNotificationChannel(channel);
        }

        // フルスクリーンインテントの作成
        Intent fullScreenIntent = new Intent(context, IncomingCallActivity.class);
        fullScreenIntent.putExtra("caller_name", callerName);
        fullScreenIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);

        android.app.PendingIntent fullScreenPendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                fullScreenIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        // 通知の作成
        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context,
                channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("")
                .setContentText("")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN) // 最小優先度
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_SECRET)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setSilent(true) // 完全に静かに
                .setAutoCancel(true);

        notificationManager.notify(1001, builder.build());

        // バックアップとして通常のstartActivityも試みる（権限がある場合など）
        // ただしAndroid 10+のバックグラウンドからはブロックされる可能性が高い
        try {
            context.startActivity(fullScreenIntent);
        } catch (Exception e) {
            // Ignore
        }

        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void getRingtones(PluginCall call) {
        String type = call.getString("type", "ringtone");
        android.media.RingtoneManager manager = new android.media.RingtoneManager(getContext());

        if ("notification".equals(type)) {
            manager.setType(android.media.RingtoneManager.TYPE_NOTIFICATION);
        } else {
            manager.setType(android.media.RingtoneManager.TYPE_RINGTONE);
        }

        android.database.Cursor cursor = manager.getCursor();

        com.getcapacitor.JSArray items = new com.getcapacitor.JSArray();

        if (cursor != null && cursor.moveToFirst()) {
            do {
                String title = cursor.getString(android.media.RingtoneManager.TITLE_COLUMN_INDEX);
                String id = cursor.getString(android.media.RingtoneManager.ID_COLUMN_INDEX);
                String uri = cursor.getString(android.media.RingtoneManager.URI_COLUMN_INDEX);

                JSObject item = new JSObject();
                item.put("title", title);
                item.put("uri", uri + "/" + id);
                items.put(item);
            } while (cursor.moveToNext());
        }

        JSObject ret = new JSObject();
        ret.put("ringtones", items);
        call.resolve(ret);
    }

    @PluginMethod
    public void saveRingtoneSettings(PluginCall call) {
        String uri = call.getString("uri");
        String host = call.getString("host");

        android.content.SharedPreferences.Editor editor = getContext()
                .getSharedPreferences("com.smartbell.pro.settings", android.content.Context.MODE_PRIVATE).edit();
        if (uri != null) {
            editor.putString("ringtone_uri", uri);
        }
        if (host != null) {
            editor.putString("mqtt_host", host);
        }
        editor.apply();

        // Widget用にも保存
        android.content.SharedPreferences.Editor widgetEditor = getContext()
                .getSharedPreferences("CapacitorStorage", android.content.Context.MODE_PRIVATE).edit();
        if (host != null) {
            widgetEditor.putString("mqtt_host", host);
        }
        widgetEditor.apply();

        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void saveChatSettings(PluginCall call) {
        String uri = call.getString("uri");
        android.content.SharedPreferences.Editor editor = getContext()
                .getSharedPreferences("com.smartbell.pro.settings", android.content.Context.MODE_PRIVATE).edit();
        if (uri != null) {
            editor.putString("chat_ringtone_uri", uri);
        }
        editor.apply();
        call.resolve();
    }

    @PluginMethod
    public void startRingtone(PluginCall call) {
        new RingtoneUtils(getContext()).playRingtone(null); // Utils handles the preference lookup
        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void stopRingtone(PluginCall call) {
        new RingtoneUtils(getContext()).stopRingtone();
        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void startService(PluginCall call) {
        String host = call.getString("host");
        Integer port = call.getInt("port");
        String topic = call.getString("topic");
        String clientId = call.getString("clientId");
        String deviceId = call.getString("deviceId");

        if (host == null || port == null) {
            call.reject("Host and Port are required");
            return;
        }

        Context context = getContext();
        Intent intent = new Intent(context, MqttForegroundService.class);
        intent.setAction(MqttForegroundService.ACTION_START);
        intent.putExtra("host", host);
        intent.putExtra("port", port);
        intent.putExtra("topic", topic);
        intent.putExtra("clientId", clientId);
        intent.putExtra("deviceId", deviceId);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        call.resolve();
    }

    @PluginMethod
    public void stopService(PluginCall call) {
        Context context = getContext();
        Intent intent = new Intent(context, MqttForegroundService.class);
        intent.setAction(MqttForegroundService.ACTION_STOP);
        context.startService(intent);
        call.resolve();
    }

    @PluginMethod
    public void dismiss(PluginCall call) {
        // 通知を消す
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) getContext()
                .getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(1001);

        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void syncContacts(PluginCall call) {
        String contactsJson = call.getString("contacts");
        if (contactsJson != null) {
            android.content.SharedPreferences prefs = getContext().getSharedPreferences("WidgetPrefs",
                    android.content.Context.MODE_PRIVATE);
            prefs.edit().putString("contacts_list", contactsJson).apply();
            call.resolve();
        } else {
            call.reject("Contacts data missing");
        }
    }

    @PluginMethod
    public void getPendingChatMessages(PluginCall call) {
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("PendingChats",
                android.content.Context.MODE_PRIVATE);
        String messagesJson = prefs.getString("messages", "[]");

        // Clear after reading
        prefs.edit().remove("messages").apply();

        try {
            com.getcapacitor.JSArray array = new com.getcapacitor.JSArray(messagesJson);
            JSObject ret = new JSObject();
            ret.put("messages", array);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to parse pending messages", e);
        }
    }
}
