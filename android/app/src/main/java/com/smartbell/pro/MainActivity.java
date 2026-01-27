package com.smartbell.pro;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(IncomingCallPlugin.class);
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null)
            return;
        String action = intent.getAction();
        if ("com.smartbell.pro.ACTION_WIDGET_CALL".equals(action)) {
            // JS側にイベントを通知 (Capacitorのブリッジを使用)
            // ブリッジがロードされるまで少し時間がかかる場合があるため、
            // 実際にはJS側から ready を受け取ってから送るのが理想的だが、
            // ここでは簡易的に window オブジェクトにイベントを投げる
            bridge.getWebView().evaluateJavascript("window.dispatchEvent(new CustomEvent('widgetCall'))", null);

            // ユーザーが「アプリに移動したくない」と言っているので
            // 送信処理が走った直後（あるいは少し待って）にバックグラウンドへ戻す
            // ここでは即座に。
            moveTaskToBack(true);
        } else if ("com.smartbell.pro.ACTION_OPEN_CHAT".equals(action)) {
            bridge.getWebView().evaluateJavascript("window.dispatchEvent(new CustomEvent('openChat'))", null);
        }
    }
}
