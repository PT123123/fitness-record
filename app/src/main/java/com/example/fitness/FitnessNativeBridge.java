package com.example.fitness;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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

    /** 登记系统精确闹钟：restSeconds 秒后（endAtMillis 时刻）准时提醒，锁屏/后台可用 */
    @JavascriptInterface
    public void scheduleCountdown(int restSeconds, long endAtMillis) {
        CountdownScheduler.schedule(app, restSeconds, endAtMillis);
    }

    /** 取消已登记的倒计时闹钟（暂停/重置/记录完成时调用） */
    @JavascriptInterface
    public void cancelCountdown() {
        CountdownScheduler.cancel(app);
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
