package com.example.fitness;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;

/**
 * JS 桥接：window.FitnessNative.*
 * - fire/dismiss：强提醒（锁屏弹窗 + 系统闹铃音 + 震动 + 屏幕唤醒），委托给 Reminder
 * - scheduleCountdown/cancelCountdown：把倒计时交给系统精确闹钟，锁屏/后台准点提醒
 * - getPermissionState 等：设置页的权限检查与申请
 */
public class FitnessNativeBridge {

    private static final String TAG = "FitnessNative";

    private final MainActivity activity;
    private final Context app;

    public FitnessNativeBridge(MainActivity activity) {
        this.activity = activity;
        this.app = activity.getApplicationContext();
        Reminder.ensureChannel(app);
    }

    /* ==================== JS 调用入口 ==================== */

    @JavascriptInterface
    public void fire(String json) {
        final int restSeconds = parseRest(json);
        final boolean isTest = parseTest(json);
        runOnUi(() -> {
            Reminder.fire(app, restSeconds, isTest); // 强提醒（弹窗 + 声音 + 震动 + 唤醒）
            tryStartOverlay();                       // 可选：悬浮窗
        });
    }

    @JavascriptInterface
    public void dismiss() {
        runOnUi(() -> Reminder.dismiss(app));
    }

    /** 登记倒计时：启动前台服务保活 + 系统精确闹钟兜底，锁屏/后台到点准时提醒 */
    @JavascriptInterface
    public void scheduleCountdown(int restSeconds, long endAtMillis) {
        CountdownService.start(app, restSeconds, endAtMillis);
    }

    /** 取消倒计时（暂停/重置/记录完成时调用）：停前台服务并撤销系统闹钟 */
    @JavascriptInterface
    public void cancelCountdown() {
        CountdownService.stop(app);
    }

    /** 设置闹铃音模式：0=内置门铃音（叮咚），1=跟随系统闹铃音 */
    @JavascriptInterface
    public void setAlarmSound(int mode) {
        Reminder.setSoundMode(app, mode);
    }

    /** 跳转「精确闹钟」授权页（Android 12+ 生效） */
    @JavascriptInterface
    public void requestExactAlarm() { activity.requestExactAlarmPermission(); }

    /** 跳转「忽略电池优化」授权页（防系统杀后台） */
    @JavascriptInterface
    public void requestBattery() { activity.requestIgnoreBatteryOptimizations(); }

    /* ==================== 权限查询与申请（设置页 JS 调用） ==================== */

    /** 返回各系统权限状态：{native,sdk,notifications,overlay,vibrate,sound,exactAlarm,battery,fullScreen} */
    @JavascriptInterface
    public String getPermissionState() {
        try {
            JSONObject o = new JSONObject();
            o.put("native", true);
            o.put("sdk", Build.VERSION.SDK_INT);
            o.put("notifications", hasNotificationPermission());
            o.put("overlay", Settings.canDrawOverlays(app));
            o.put("vibrate", true);
            o.put("sound", Reminder.soundMode(app));
            o.put("exactAlarm", canExactAlarm());
            o.put("battery", ignoringBatteryOptimizations());
            o.put("fullScreen", canUseFullScreenIntent());
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

    /** Android 12+ 精确闹钟是否可用（决定到点是否分秒不差；Android 14+ 非闹钟类 App 需在系统设置手动授权） */
    private boolean canExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    /** Android 14+ 全屏通知（锁屏弹窗）是否可用 */
    private boolean canUseFullScreenIntent() {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.canUseFullScreenIntent();
        }
        return true;
    }

    /** 是否已关闭电池优化（防系统杀后台） */
    private boolean ignoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(app.getPackageName());
        }
        return true;
    }

    /* ==================== 悬浮窗（可选增强） ==================== */
    private void tryStartOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) {
            activity.openOverlaySettings();
            return;
        }
        try { OverlayService.start(app); } catch (Throwable t) { Log.w(TAG, "overlay fail", t); }
    }

    /* ==================== 工具 ==================== */
    private int parseRest(String json) {
        int rest = 0;
        try { if (json != null && !json.isEmpty()) rest = new JSONObject(json).optInt("restSeconds", 0); } catch (Throwable ignored) {}
        return rest;
    }

    private boolean parseTest(String json) {
        try { if (json != null && !json.isEmpty()) return new JSONObject(json).optBoolean("test", false); } catch (Throwable ignored) {}
        return false;
    }

    private void runOnUi(Runnable r) { new Handler(Looper.getMainLooper()).post(r); }
}
