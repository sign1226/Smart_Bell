package com.smartbell.pro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {

            Log.d(TAG, "Device booted, starting MqttForegroundService...");

            // Retrieve saved settings to start the service with correct parameters
            SharedPreferences settings = context.getSharedPreferences("com.smartbell.pro.settings",
                    Context.MODE_PRIVATE);
            String host = settings.getString("mqtt_host", null);
            int port = settings.getInt("mqtt_port", 1883);
            String topic = settings.getString("mqtt_topic", null);
            String clientId = settings.getString("mqtt_client_id", null);
            String deviceId = settings.getString("mqtt_device_id", null);

            if (host != null) {
                Intent serviceIntent = new Intent(context, MqttForegroundService.class);
                serviceIntent.setAction(MqttForegroundService.ACTION_START);
                serviceIntent.putExtra("host", host);
                serviceIntent.putExtra("port", port);
                serviceIntent.putExtra("topic", topic);
                serviceIntent.putExtra("clientId", clientId);
                serviceIntent.putExtra("deviceId", deviceId);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                Log.w(TAG, "No saved MQTT settings found. Service not started.");
            }
        }
    }
}
