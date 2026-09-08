package com.example.fitness;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

/**
 * 倒计时归零弹出的「强提弹窗」：
 *  - 锁屏上也能显示（showOnLockScreen + turnScreenOn + 窗口 Flag）
 *  - 持续震动，直到用户操作
 *  - 三个按钮：记录这组 / 再休息1分钟 / 关闭
 *  - 测试模式（test=true，来自设置页「测试系统提醒」）：不写记录，仅验证链路
 */
public class AlarmActivity extends Activity {

    public static final String EXTRA_REST = "rest";
    public static final String EXTRA_TEST = "test";
    private static Activity sInstance; // 用于 Bridge.dismiss() 关闭
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = this;

        // 锁屏/点亮屏幕相关 Flag（Manifest 里也声明了 showOnLockScreen）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_alarm);

        int rest = getIntent().getIntExtra(EXTRA_REST, 0);
        boolean isTest = getIntent().getBooleanExtra(EXTRA_TEST, false);
        TextView title = findViewById(R.id.alarm_title);
        TextView sub = findViewById(R.id.alarm_sub);
        Button btnRecord = findViewById(R.id.btn_record);
        Button btnSnooze = findViewById(R.id.btn_snooze);
        Button btnClose = findViewById(R.id.btn_close);

        if (isTest) {
            // 测试模式：只验证「弹窗 + 系统声音 + 震动」链路，不写训练记录
            title.setText("🔔 测试提醒");
            sub.setText("系统提醒链路正常" + (rest > 0 ? "（测试 " + rest + " 秒）" : ""));
            btnRecord.setText("知道了");
            btnRecord.setOnClickListener(v -> finish());
            btnSnooze.setVisibility(View.GONE);
            btnClose.setText("关闭");
            btnClose.setOnClickListener(v -> finish());
        } else {
            // ① 记录这组 → 调用 JS 的 recordRestGroup()，自动关弹窗/停震动
            title.setText("休息结束！");
            sub.setText("该做下一组了" + (rest > 0 ? "（休息 " + rest + " 秒）" : ""));
            btnRecord.setOnClickListener(v -> {
                FitnessApp.evalJs("recordRestGroup()");
                finish();
            });
            // ② 再休息 1 分钟 → 调用 JS 重启倒计时
            btnSnooze.setOnClickListener(v -> {
                FitnessApp.evalJs("restartCountdown(60)");
                finish();
            });
            // ③ 关闭 → 仅关闭弹窗、停震动（不记录）
            btnClose.setOnClickListener(v -> finish());
        }

        startVibrate();
    }

    private void startVibrate() {
        try {
            vibrator = getVibrator();
            if (vibrator == null) return;
            long[] p = {0, 600, 200, 600, 200, 600, 200, 600};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(p, 1));
            } else {
                vibrator.vibrate(p, 1);
            }
        } catch (Throwable ignored) {}
    }

    private void stopVibrate() {
        try { if (vibrator != null) vibrator.cancel(); } catch (Throwable ignored) {}
    }

    @Override
    protected void onDestroy() {
        stopVibrate();
        if (sInstance == this) sInstance = null;
        super.onDestroy();
    }

    /** 供 Bridge.dismiss() 在其它线程关闭弹窗 */
    static void dismiss(Activity context) {
        if (sInstance != null) {
            try { sInstance.finish(); } catch (Throwable ignored) {}
        }
    }

    @SuppressWarnings("deprecation")
    private Vibrator getVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            return vm == null ? null : vm.getDefaultVibrator();
        }
        return (Vibrator) getSystemService(VIBRATOR_SERVICE);
    }
}
