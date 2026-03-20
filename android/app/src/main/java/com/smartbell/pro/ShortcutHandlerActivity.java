package com.smartbell.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

/**
 * 透明なActivity。ショートカットから呼び出され、
 * 即座にMqttForegroundServiceを起動して自分を閉じる。
 * これにより「インストールされていません」エラーを回避しつつ、
 * ユーザーには画面が開いたことを意識させずにバックグラウンド処理を実行する。
 */
public class ShortcutHandlerActivity extends Activity {
    private static final String TAG = "ShortcutHandler";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "Received shortcut intent: " + action);

            // MqttForegroundServiceへリクエストを転送
            Intent serviceIntent = new Intent(this, MqttForegroundService.class);
            serviceIntent.setAction(MqttForegroundService.ACTION_CALL);
            
            // パラメータをすべてコピー
            Bundle extras = intent.getExtras();
            if (extras != null) {
                serviceIntent.putExtras(extras);
            }

            try {
                // Show immediate feedback from the activity itself
                String name = intent.getStringExtra("targetName");
                if (name == null) name = "全員";
                android.widget.Toast.makeText(this, name + " へ呼出中...", android.widget.Toast.LENGTH_SHORT).show();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to start service", e);
            }
        }

        // 即座に終了（透明なのでユーザーには見えない）
        finish();
    }
}
