package com.smartbell.pro;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class CallWidget extends AppWidgetProvider {
    private static final String ACTION_CALL = "com.smartbell.pro.ACTION_WIDGET_CALL";
    private static final String TAG = "CallWidget";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

        // Load targetId from prefs
        SharedPreferences prefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE);
        String targetId = prefs.getString("targetId_" + appWidgetId, null);
        String targetName = prefs.getString("targetName_" + appWidgetId, "全員");

        // Load MQTT settings from SharedPreferences
        SharedPreferences settings = context.getSharedPreferences("com.smartbell.pro.settings", Context.MODE_PRIVATE);
        String host = settings.getString("mqtt_host", null);
        int port = settings.getInt("mqtt_port", 1883);

        // Create Intent to start service directly
        Intent serviceIntent = new Intent(context, MqttForegroundService.class);
        serviceIntent.setAction(MqttForegroundService.ACTION_TRIGGER_CALL);
        serviceIntent.putExtra("targetId", targetId);
        serviceIntent.putExtra("targetName", targetName);
        serviceIntent.putExtra("host", host);
        serviceIntent.putExtra("port", port);

        // Use getService instead of getBroadcast for higher reliability
        PendingIntent pendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            pendingIntent = PendingIntent.getForegroundService(
                    context,
                    appWidgetId,
                    serviceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getService(
                    context,
                    appWidgetId,
                    serviceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        views.setOnClickPendingIntent(R.id.widget_button, pendingIntent);
        views.setTextViewText(R.id.widget_target_name, targetName);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
