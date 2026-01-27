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

            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            mediaPlayer.setAudioAttributes(attributes);

            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

            // Vibration logic
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = { 0, 1000, 1000 }; // Wait 0ms, vibrate 1000ms, sleep 1000ms
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)); // 0 means repeat from index 0
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to play ringtone", e);
            // Fallback attempt or silent fail
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
