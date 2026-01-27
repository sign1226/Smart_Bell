package com.smartbell.pro;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class IncomingCallActivity extends AppCompatActivity {

    private RingtoneUtils ringtoneUtils;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ロック画面の上に表示するための設定
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        } else {
            // Android 8.0以下向けの互換処理
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }

        // 画面を常にオンにする
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // シンプルなレイアウトをプログラムで作成
        setContentView(createCallLayout());

        // 着信音再生
        ringtoneUtils = new RingtoneUtils(this);
        ringtoneUtils.playRingtone(null); // デフォルト着信音

        // 戻るボタンの無効化（モダンな方法）
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 何もしない（確認ボタンでのみ終了可能にする）
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (ringtoneUtils != null) {
            ringtoneUtils.stopRingtone();
        }
        super.onDestroy();
    }

    private View createCallLayout() {
        String callerName = getIntent().getStringExtra("caller_name");
        if (callerName == null)
            callerName = "呼び出し";

        android.widget.RelativeLayout layout = new android.widget.RelativeLayout(this);
        layout.setBackgroundColor(0xFFFF4D4D); // 赤色

        // ベルアイコン（白色のVectorDrawableを使用）
        android.widget.ImageView bellIcon = new android.widget.ImageView(this);
        bellIcon.setImageResource(R.drawable.ic_bell_white);
        bellIcon.setId(View.generateViewId());
        android.widget.RelativeLayout.LayoutParams bellParams = new android.widget.RelativeLayout.LayoutParams(
                500, // Width
                500 // Height
        );
        bellParams.addRule(android.widget.RelativeLayout.CENTER_IN_PARENT);
        bellIcon.setLayoutParams(bellParams);
        layout.addView(bellIcon);

        // アニメーションの設定
        android.animation.ObjectAnimator animator = android.animation.ObjectAnimator.ofFloat(bellIcon, "rotation", 0f,
                20f, -20f, 20f, -20f, 0f);
        animator.setDuration(1000);
        animator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        animator.start();

        // 拡大縮小も追加
        android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(bellIcon, "scaleX", 1f, 1.2f,
                1f);
        scaleX.setDuration(1000);
        scaleX.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        scaleX.start();
        android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(bellIcon, "scaleY", 1f, 1.2f,
                1f);
        scaleY.setDuration(1000);
        scaleY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        scaleY.start();

        // 発信者名テキスト
        TextView nameText = new TextView(this);
        nameText.setText(callerName);
        nameText.setTextSize(48);
        nameText.setTextColor(0xFFFFFFFF);
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        nameText.setId(View.generateViewId());
        android.widget.RelativeLayout.LayoutParams nameParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        nameParams.addRule(android.widget.RelativeLayout.ABOVE, bellIcon.getId());
        nameParams.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
        nameParams.setMargins(0, 0, 0, 20);
        nameText.setLayoutParams(nameParams);
        layout.addView(nameText);

        // 「呼び出し中！」テキスト
        TextView title = new TextView(this);
        title.setText("呼び出し中！");
        title.setTextSize(24);
        title.setTextColor(0xCCFFFFFF);
        android.widget.RelativeLayout.LayoutParams titleParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        titleParams.addRule(android.widget.RelativeLayout.BELOW, bellIcon.getId());
        titleParams.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
        titleParams.setMargins(0, 0, 0, 0);
        title.setLayoutParams(titleParams);
        layout.addView(title);

        // 確認ボタン
        Button confirmButton = new Button(this);
        confirmButton.setText("確認・停止");
        confirmButton.setTextSize(24);
        confirmButton.setBackgroundColor(0xFFFFFFFF);
        confirmButton.setTextColor(0xFFFF4D4D);
        confirmButton.setTypeface(null, android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setCornerRadius(100);
        shape.setColor(0xFFFFFFFF);
        confirmButton.setBackground(shape);

        android.widget.RelativeLayout.LayoutParams buttonParams = new android.widget.RelativeLayout.LayoutParams(
                700, // Width
                200 // Height
        );
        buttonParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
        buttonParams.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
        buttonParams.setMargins(0, 0, 0, 150);
        confirmButton.setLayoutParams(buttonParams);

        confirmButton.setOnClickListener(v -> {
            if (ringtoneUtils != null) {
                ringtoneUtils.stopRingtone();
            }
            android.app.NotificationManager notificationManager = (android.app.NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(1001);
            }
            finish();
        });
        layout.addView(confirmButton);

        return layout;
    }
}
