package com.smartbell.pro;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class CallReceiver extends BroadcastReceiver {

    public static final String ACTION_SHOW_CALL = "com.smartbell.pro.SHOW_CALL";
    public static final String ACTION_TRIGGER_CALL = "com.smartbell.pro.ACTION_TRIGGER_CALL";
    public static final String CHANNEL_ID = "bell_calls";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_SHOW_CALL.equals(intent.getAction())) {
            showFullScreenNotification(context);
        } else if (ACTION_TRIGGER_CALL.equals(intent.getAction())) {
            triggerCall(context);
        }
    }

    private void triggerCall(Context context) {
        Log.d("CallReceiver", "Triggering Call... starting MainActivity");

        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction("com.smartbell.pro.ACTION_WIDGET_CALL");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    private void showFullScreenNotification(Context context) {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        // 通知チャンネルの作成（Android 8.0以上）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "呼び出し通知",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("カウンターからの呼び出しを通知します");
            channel.enableVibration(true);
            channel.enableLights(true);
            notificationManager.createNotificationChannel(channel);
        }

        // フルスクリーンインテントの作成
        Intent fullScreenIntent = new Intent(context, IncomingCallActivity.class);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                0,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 通知の作成
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("🔔 呼び出し！")
                .setContentText("呼び出しが発生しました")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent);

        // 通知を表示
        notificationManager.notify(1, builder.build());
    }

    /**
     * 静的メソッドとして呼び出し画面を表示する
     */
    public static void showIncomingCall(Context context) {
        Intent intent = new Intent(context, IncomingCallActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }
}
