package com.smartbell.pro;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AutoUpdater {
    private static final String TAG = "AutoUpdater";
    private static final String REPO_URL = "https://api.github.com/repos/sign1226/Smart_Bell/releases/latest";
    private final Context context;

    public AutoUpdater(Context context) {
        this.context = context;
    }

    public void checkUpdate(boolean silent) {
        new Thread(() -> {
            try {
                String response = fetchReleaseInfo();
                if (response == null)
                    return;

                JSONObject json = new JSONObject(response);
                String latestVersion = json.getString("tag_name"); // e.g. "v1.6.1"
                JSONArray assets = json.getJSONArray("assets");
                String downloadUrl = null;

                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url");
                        break;
                    }
                }

                if (downloadUrl != null && isNewerVersion(latestVersion)) {
                    final String finalDownloadUrl = downloadUrl;
                    new Handler(Looper.getMainLooper()).post(() -> showUpdateDialog(latestVersion, finalDownloadUrl));
                } else if (!silent) {
                    new Handler(Looper.getMainLooper()).post(() -> android.widget.Toast
                            .makeText(context, "最新バージョンです (" + latestVersion + ")", android.widget.Toast.LENGTH_SHORT)
                            .show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Update check failed", e);
            }
        }).start();
    }

    private String fetchReleaseInfo() throws Exception {
        URL url = new URL(REPO_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        if (conn.getResponseCode() == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
                sb.append(line);
            reader.close();
            return sb.toString();
        }
        return null;
    }

    private boolean isNewerVersion(String latestVersion) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            String currentVersion = pInfo.versionName;

            // "v1.6.0" -> "1.6.0"
            String cleanLatest = latestVersion.replaceAll("[^0-9.]", "");
            String cleanCurrent = currentVersion.replaceAll("[^0-9.]", "");

            String[] latestParts = cleanLatest.split("\\.");
            String[] currentParts = cleanCurrent.split("\\.");

            int length = Math.max(latestParts.length, currentParts.length);
            for (int i = 0; i < length; i++) {
                int l = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
                int c = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                if (l > c)
                    return true;
                if (l < c)
                    return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Version comparison error", e);
        }
        return false;
    }

    private void showUpdateDialog(String version, String downloadUrl) {
        new AlertDialog.Builder(context)
                .setTitle("アップデートがあります")
                .setMessage("新しいバージョン (" + version + ") が公開されています。ダウンロードしてインストールしますか？")
                .setPositiveButton("ダウンロード", (dialog, which) -> startDownload(downloadUrl, version))
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void startDownload(String downloadUrl, String version) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request.setTitle("SmartBell アップデート");
        request.setDescription("新バージョン " + version + " をダウンロード中...");
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SmartBell_" + version + ".apk");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = manager.enqueue(request);

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    installApk(manager.getUriForDownloadedFile(downloadId));
                    context.unregisterReceiver(this);
                }
            }
        }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        android.widget.Toast.makeText(context, "ダウンロードを開始しました", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void installApk(Uri apkUri) {
        if (apkUri == null)
            return;

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
    }
}
