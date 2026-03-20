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
            String packageName = getContext().getPackageName();
            android.os.PowerManager pm = (android.os.PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                getContext().startActivity(intent);
            }
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
        Integer port = call.getInt("port"); // Retrieve port from call
        Boolean vibrationEnabled = call.getBoolean("vibrationEnabled");
        String vibrationPattern = call.getString("vibrationPattern");

        android.content.SharedPreferences.Editor editor = getContext()
                .getSharedPreferences("com.smartbell.pro.settings", android.content.Context.MODE_PRIVATE).edit();

        // uriがnullまたは空文字列の場合はシステムデフォルトを意味するのでnullとして保存
        if (uri == null || uri.isEmpty()) {
            editor.remove("ringtone_uri");
        } else {
            editor.putString("ringtone_uri", uri);
        }

        if (host != null) {
            editor.putString("mqtt_host", host);
        }
        if (port != null) { // Save port to shared preferences
            editor.putInt("mqtt_port", port);
        }
        if (vibrationEnabled != null) {
            editor.putBoolean("vibration_enabled", vibrationEnabled);
        }
        if (vibrationPattern != null) {
            editor.putString("vibration_pattern", vibrationPattern);
        }
        editor.apply();

        // Sync to CapacitorStorage for widget and other parts that might use it
        android.content.SharedPreferences.Editor widgetEditor = getContext()
                .getSharedPreferences("CapacitorStorage", android.content.Context.MODE_PRIVATE).edit();
        if (host != null) {
            widgetEditor.putString("mqtt_host", host);
        }
        if (port != null) { // Also save port to CapacitorStorage for consistency
            widgetEditor.putInt("mqtt_port", port);
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
        if (uri != null && !uri.isEmpty()) {
            editor.putString("chat_ringtone_uri", uri);
        } else {
            editor.remove("chat_ringtone_uri");
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

        // 重要: 常時起動やウィジェットでの復旧のために設定を永続化する
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("com.smartbell.pro.settings",
                Context.MODE_PRIVATE);
        prefs.edit()
                .putString("mqtt_host", host)
                .putInt("mqtt_port", port)
                .putString("mqtt_topic", topic)
                .putString("mqtt_client_id", clientId)
                .putString("mqtt_device_id", deviceId)
                .apply();

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
            Context context = getContext();
            android.content.SharedPreferences prefs = context.getSharedPreferences("WidgetPrefs",
                    android.content.Context.MODE_PRIVATE);
            prefs.edit().putString("contacts_list", contactsJson).apply();

            // Dynamic Shortcuts Implementation
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                try {
                    android.content.pm.ShortcutManager shortcutManager = context
                            .getSystemService(android.content.pm.ShortcutManager.class);
                    if (shortcutManager != null) {
                        org.json.JSONArray contactsArray = new org.json.JSONArray(contactsJson);
                        java.util.List<android.content.pm.ShortcutInfo> dynamicShortcuts = new java.util.ArrayList<>();

                        // Load MQTT settings for shortcuts
                        android.content.SharedPreferences settings = context.getSharedPreferences("com.smartbell.pro.settings", Context.MODE_PRIVATE);
                        String host = settings.getString("mqtt_host", null);
                        int port = settings.getInt("mqtt_port", 1883);

                        // Limit to 3 dynamic shortcuts to leave room for static one
                        int limit = Math.min(contactsArray.length(), 3);
                        for (int i = 0; i < limit; i++) {
                            org.json.JSONObject contact = contactsArray.getJSONObject(i);
                            String id = contact.getString("id");
                            String name = contact.getString("name");

                            // Trigger ShortcutHandlerActivity
                            Intent intent = new Intent(context, ShortcutHandlerActivity.class);
                            intent.setAction(MqttForegroundService.ACTION_CALL);
                            intent.putExtra("targetId", id);
                            intent.putExtra("targetName", name);
                            intent.putExtra("host", host);
                            intent.putExtra("port", port);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                            android.content.pm.ShortcutInfo shortcut = new android.content.pm.ShortcutInfo.Builder(context,
                                    "call_contact_" + id)
                                    .setShortLabel(name + "を呼ぶ")
                                    .setLongLabel("Smart Bellで" + name + "を呼び出す")
                                    .setIcon(android.graphics.drawable.Icon.createWithResource(context,
                                            R.drawable.ic_bell_white))
                                    .setIntent(intent)
                                    .build();
                            dynamicShortcuts.add(shortcut);
                        }
                        shortcutManager.setDynamicShortcuts(dynamicShortcuts);
                        android.util.Log.d("IncomingCallPlugin", "Dynamic shortcuts updated: " + dynamicShortcuts.size());
                    }
                } catch (Exception e) {
                    android.util.Log.e("IncomingCallPlugin", "Failed to update dynamic shortcuts", e);
                }
            }

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

    @PluginMethod
    public void getPendingWidgetCall(PluginCall call) {
        call.resolve(new JSObject());
    }

    @PluginMethod
    public void clearPendingWidgetCall(PluginCall call) {
        call.resolve();
    }
}
