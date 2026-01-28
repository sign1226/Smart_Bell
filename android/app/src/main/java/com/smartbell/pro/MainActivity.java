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
            String targetId = intent.getStringExtra("targetId");
            String targetName = intent.getStringExtra("targetName");
            if (targetId == null)
                targetId = "";
            if (targetName == null)
                targetName = "全員";

            String jsFunc = String.format(
                    "window.dispatchEvent(new CustomEvent('widgetCall', { detail: { targetId: '%s', targetName: '%s' } }))",
                    targetId, targetName);
            bridge.getWebView().evaluateJavascript(jsFunc, null);
        } else if ("com.smartbell.pro.ACTION_OPEN_CHAT".equals(action)) {
            bridge.getWebView().evaluateJavascript("window.dispatchEvent(new CustomEvent('openChat'))", null);
        }
    }
}
