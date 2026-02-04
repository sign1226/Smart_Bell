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

        Intent intent = new Intent(context, CallWidget.class);
        intent.setAction(ACTION_CALL);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId, // Use appWidgetId as requestCode for unique PendingIntents
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        views.setOnClickPendingIntent(R.id.widget_button, pendingIntent);
        views.setTextViewText(R.id.widget_target_name, targetName);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_CALL.equals(intent.getAction())) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            Log.d(TAG, "Widget Call Button Pressed for ID: " + appWidgetId);
            sendCallMessage(context, appWidgetId);
        }
    }

    private void sendCallMessage(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE);
        String targetId = prefs.getString("targetId_" + appWidgetId, null);
        String targetName = prefs.getString("targetName_" + appWidgetId, "全員");

        Log.d(TAG, "Starting MainActivity from widget. targetId=" + targetId + ", targetName=" + targetName);

        // アプリ本体を起動し、インテントで情報を渡す
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(ACTION_CALL);
        intent.putExtra("targetId", targetId);
        intent.putExtra("targetName", targetName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        context.startActivity(intent);
    }
}
