package com.smartbell.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Android OSの「ショートカット作成」インテントに対応するActivity。
 * Taskerやホーム画面のショートカット追加時に呼び出される。
 */
public class ShortcutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 呼び出し相手を選択するダイアログを表示
        showSelectionDialog();
    }

    private void showSelectionDialog() {
        SharedPreferences prefs = getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE);
        String contactsJson = prefs.getString("contacts_list", "[]");
        
        try {
            JSONArray contactsArray = new JSONArray(contactsJson);
            final String[] names = new String[contactsArray.length() + 1];
            final String[] ids = new String[contactsArray.length() + 1];

            names[0] = "全員を呼ぶ";
            ids[0] = "";

            for (int i = 0; i < contactsArray.length(); i++) {
                JSONObject contact = contactsArray.getJSONObject(i);
                names[i + 1] = contact.getString("name");
                ids[i + 1] = contact.getString("id");
            }

            new AlertDialog.Builder(this)
                .setTitle("ショートカットを作成")
                .setItems(names, (dialog, which) -> {
                    createShortcut(names[which], ids[which]);
                })
                .setOnCancelListener(dialog -> finish())
                .show();

        } catch (Exception e) {
            // エラー時や空時は「全員」のみ
            createShortcut("全員を呼ぶ", "");
        }
    }

    private void createShortcut(String label, String targetId) {
        // Load MQTT settings from SharedPreferences to pass to service
        android.content.SharedPreferences settings = getSharedPreferences("com.smartbell.pro.settings", Context.MODE_PRIVATE);
        String host = settings.getString("mqtt_host", null);
        int port = settings.getInt("mqtt_port", 1883);

        // Shortcut that triggers ShortcutHandlerActivity (Transparent)
        // This avoids the "App not installed" error while performing background work
        Intent targetIntent = new Intent(this, ShortcutHandlerActivity.class);
        targetIntent.setAction(MqttForegroundService.ACTION_CALL);
        targetIntent.putExtra("targetId", targetId);
        targetIntent.putExtra("targetName", label.replace("を呼ぶ", ""));
        targetIntent.putExtra("host", host);
        targetIntent.putExtra("port", port);
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // OSに返すショートカット情報の設定
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, targetIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, label);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, 
            Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_bell_white));

        setResult(RESULT_OK, intent);
        finish();
    }
}
