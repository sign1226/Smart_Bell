package com.smartbell.pro;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import java.io.IOException;

public class RingtoneUtils {
    private static final String TAG = "RingtoneUtils";
    private static MediaPlayer mediaPlayer;
    private static Vibrator vibrator;
    private Context context;

    public RingtoneUtils(Context context) {
        this.context = context;
    }

    @SuppressWarnings("deprecation")
    public synchronized void playRingtone(String uriString) {
        stopRingtone(); // Stop if already playing

        Uri ringtoneUri = null;
        if (uriString != null && !uriString.isEmpty()) {
            ringtoneUri = Uri.parse(uriString);
        } else {
            // Check SharedPreferences
            android.content.SharedPreferences prefs = context.getSharedPreferences("com.smartbell.pro.settings",
                    Context.MODE_PRIVATE);
            String savedUri = prefs.getString("ringtone_uri", null);
            if (savedUri != null) {
                ringtoneUri = Uri.parse(savedUri);
            }

            if (ringtoneUri == null) {
                // Default to system ringtone
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                if (ringtoneUri == null) {
                    ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }
            }
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(context, ringtoneUri);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            mediaPlayer.setAudioAttributes(audioAttributes);

            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to play ringtone", e);
            // MediaPlayer failed, but we still want to try vibration if enabled
        }

        // Vibration logic
        android.content.SharedPreferences prefs = context.getSharedPreferences("com.smartbell.pro.settings",
                Context.MODE_PRIVATE);
        boolean vibrationEnabled = prefs.getBoolean("vibration_enabled", true);
        String vibrationPatternStr = prefs.getString("vibration_pattern", "standard");

        if (vibrationEnabled) {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern;
                switch (vibrationPatternStr) {
                    case "short":
                        pattern = new long[] { 0, 200, 500 };
                        break;
                    case "rapid":
                        pattern = new long[] { 0, 300, 200 };
                        break;
                    case "heartbeat":
                        pattern = new long[] { 0, 100, 100, 100, 600 };
                        break;
                    case "standard":
                    default:
                        pattern = new long[] { 0, 1000, 1000 };
                        break;
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)); // 0 means repeat from index 0
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        }
    }

    public synchronized void stopRingtone() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }
}
