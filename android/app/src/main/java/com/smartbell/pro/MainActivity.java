package com.smartbell.pro;

import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.os.PowerManager;
import android.net.Uri;
import android.provider.Settings;
import android.content.Context;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(IncomingCallPlugin.class);
        super.onCreate(savedInstanceState);

        // Check for updates from GitHub
        AutoUpdater updater = new AutoUpdater(this);
        updater.checkUpdate(true); // silent mode for startup check

        requestBatteryOptimizationExemption();
        handleIntent(getIntent());
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            String packageName = getPackageName();
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }
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
        if ("com.smartbell.pro.ACTION_OPEN_CHAT".equals(action)) {
            if (bridge != null && bridge.getWebView() != null) {
                bridge.getWebView().evaluateJavascript("window.dispatchEvent(new CustomEvent('openChat'))", null);
            }
        }
    }
}
