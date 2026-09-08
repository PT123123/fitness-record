package com.example.fitness;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;

/**
 * JS 桥接：window.FitnessNative.fire(...) / .dismiss()
 * 倒计时归零 → 锁屏全屏弹窗 + 悬浮窗 + 震动 + 高优先级通知。
 */
public class FitnessNativeBridge {

    private static final String TAG = "FitnessNative";
    private static final String CHANNEL_ID = "fitness_alarm";
    public static final int NOTIFY_ID = 9001;

    private final MainActivity activity;
    private final Context app;
    private android.os.PowerManager.WakeLock wakeLock;
    private Vibrator vibrator;

    public FitnessNativeBridge(MainActivity activity) {
        this.activity = activity;
        this.app = activity.getApplicationContext();
        ensureChannel();
    }

    /* ==================== JS 调用入口 ==================== */

    @JavascriptInterface
    public void fire(String json) {
        final int restSeconds = parseRest(json);
        final boolean isTest = parseTest(json);
        runOnUi(() -> {
            showLockScreenAlarm(restSeconds, isTest); // ① 锁屏全屏弹窗（最核心）
            showHeadsUpNotification(restSeconds); // ② 高优先级通知（下拉/状态栏）
            tryStartOverlay();                    // ③ 悬浮窗（需授权，可选）
            startVibrate();                       // ④ 持续震动
            acquireWakeLock();                    // ⑤ 点亮屏幕并保持
        });
    }

    @JavascriptInterface
    public void dismiss() {
        runOnUi(() -> {
            stopVibrate();
            releaseWakeLock();
            cancelNotification();
            AlarmActivity.dismiss(activity);
            OverlayService.stop(app);
        });
    }

    /* ==================== 权限查询与申请（设置页 JS 调用） ==================== */

    /** 返回各系统权限状态：{native,sdk,notifications,overlay,vibrate} */
    @JavascriptInterface
    public String getPermissionState() {
        try {
            JSONObject o = new JSONObject();
            o.put("native", true);
            o.put("sdk", Build.VERSION.SDK_INT);
            o.put("notifications", hasNotificationPermission());
            o.put("overlay", Settings.canDrawOverlays(app));
            o.put("vibrate", true);
            return o.toString();
        } catch (Throwable t) { return "{}"; }
    }

    /** 申请通知权限（Android 13+ 运行时权限） */
    @JavascriptInterface
    public void requestNotificationPermission() { activity.requestNotificationPermission(); }

    /** 跳转悬浮窗授权页（SYSTEM_ALERT_WINDOW 需用户手动开） */
    @JavascriptInterface
    public void openOverlaySettings() { activity.openOverlaySettings(); }

    /** 打开本应用系统设置页（通知/全屏通知开关） */
    @JavascriptInterface
    public void openAppSettings() { activity.openAppSettings(); }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Android 12-：安装即授予
    }

    /* ==================== ① 锁屏全屏弹窗 ==================== */
    private void showLockScreenAlarm(int restSeconds, boolean isTest) {
        Intent i = new Intent(app, AlarmActivity.class);
        i.putExtra(AlarmActivity.EXTRA_REST, restSeconds);
        i.putExtra(AlarmActivity.EXTRA_TEST, isTest);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        app.startActivity(i);
    }

    /* ==================== ② 高优先级通知（可全屏） ==================== */
    private void showHeadsUpNotification(int restSeconds) {
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent open = new Intent(app, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(app, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | flagImmutable());

        long[] pattern = {0, 600, 200, 600, 200, 600};

        Notification.Builder b = new Notification.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("🏋️ 休息结束！")
                .setContentText("该做下一组了（休息 " + restSeconds + " 秒）")
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)   // Android 10- 锁屏直接展开
                .setVibrate(pattern);

        // Android 8+ 设置提示音/震动通道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build();
            b.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, aa);
        } else {
            b.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI);
        }
        nm.notify(NOTIFY_ID, b.build());
    }

    /* ==================== ③ 悬浮窗（SYSTEM_ALERT_WINDOW） ==================== */
    private void tryStartOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) {
            // 未授权：引导用户在设置里打开（首次会弹引导；之后走 MainActivity 的设置页）
            activity.openOverlaySettings();
            return;
        }
        try { OverlayService.start(app); } catch (Throwable t) { Log.w(TAG, "overlay fail", t); }
    }

    /* ==================== ④ 震动 ==================== */
    private void startVibrate() {
        try {
            vibrator = getVibrator();
            if (vibrator == null) return;
            long[] pattern = {0, 600, 200, 600, 200, 600, 200, 600, 200, 600};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 1));
            } else {
                vibrator.vibrate(pattern, 1);
            }
        } catch (Throwable t) { Log.w(TAG, "vibrate fail", t); }
    }

    private void stopVibrate() {
        try { if (vibrator != null) vibrator.cancel(); } catch (Throwable ignored) {}
    }

    /* ==================== ⑤ 唤醒屏幕 ==================== */
    private void acquireWakeLock() {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) app.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            wakeLock = pm.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                            | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | android.os.PowerManager.ON_AFTER_RELEASE,
                    "Fitness::AlarmWakeLock");
            wakeLock.acquire(30 * 1000L); // 最多 30 秒自动释放，防止耗电
        } catch (Throwable t) { Log.w(TAG, "wakelock fail", t); }
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {}
    }

    private void cancelNotification() {
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFY_ID);
    }

    /* ==================== 工具 ==================== */
    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "训练提醒",
                    NotificationManager.IMPORTANCE_HIGH); // 高重要度 = 弹出（heads-up）
            ch.setDescription("倒计时归零时的强提醒");
            ch.enableVibration(true);
            ch.setBypassDnd(true); // 绕过勿扰
            ch.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI,
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            nm.createNotificationChannel(ch);
        }
    }

    private int parseRest(String json) {
        int rest = 0;
        try { if (json != null && !json.isEmpty()) rest = new JSONObject(json).optInt("restSeconds", 0); } catch (Throwable ignored) {}
        return rest;
    }

    private boolean parseTest(String json) {
        try { if (json != null && !json.isEmpty()) return new JSONObject(json).optBoolean("test", false); } catch (Throwable ignored) {}
        return false;
    }

    private Vibrator getVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm == null ? null : vm.getDefaultVibrator();
        }
        return (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
    }

    private int flagImmutable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private void runOnUi(Runnable r) { new Handler(Looper.getMainLooper()).post(r); }
}
