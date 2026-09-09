package com.example.fitness;

import android.app.Activity;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {

    private WebView web;
    private static final int REQ_NOTIFY = 1001;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        web = new WebView(this);
        web.setLayoutParams(new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);                      // 必须：JS 桥接
        s.setDomStorageEnabled(true);                       // 必须：localStorage 存记录
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);       // 允许自动播放提示音

        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new FitnessNativeBridge(this), "FitnessNative");

        // 载入本地 HTML（assets/index.html）
        web.loadUrl("file:///android_asset/index.html");

        ensureNotificationPermission();
    }

    /** Android 13+ 需动态申请通知权限，否则强提醒通知不弹出 */
    private void ensureNotificationPermission() { requestNotificationPermission(); }

    /** 供 JS 桥接调用：申请通知权限（Android 13+） */
    public void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
            }
        }
    }

    /** 打开本应用的系统设置页（通知权限 / Android 14+「全屏通知」开关都在这里） */
    public void openAppSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Throwable t) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Throwable ignored) {}
        }
    }

    /** 跳转「精确闹钟」授权页（Android 12+；到点准点触发依赖它，未授权时前台服务仍可准时提醒） */
    public void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Throwable t) { openAppSettings(); }
            }
        }
    }

    /** 跳转「忽略电池优化」授权页（防系统杀后台导致不提醒） */
    public void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Throwable t) {
                    // 部分国产 ROM 不支持该页面，用户可手动到系统设置里关闭电池优化
                    try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Throwable ignored) {}
                }
            }
        }
    }

    /** 从系统设置返回时通知 JS 刷新权限状态（设置页的「重新检查」也会用到） */
    @Override
    protected void onResume() {
        super.onResume();
        if (web == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            web.evaluateJavascript("window.__onNativeResume && window.__onNativeResume()", null);
        } else {
            web.loadUrl("javascript:window.__onNativeResume && window.__onNativeResume()");
        }
    }

    /** 供 JS 回调用（弹窗按钮「记录这组 / 再休息」） */
    public void evalJs(String js) {
        if (web == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            web.evaluateJavascript(js, null);
        } else {
            web.loadUrl("javascript:" + js);
        }
    }

    /** 悬浮窗权限引导（SYSTEM_ALERT_WINDOW 需用户手动授权） */
    public void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.destroy(); web = null; }
        super.onDestroy();
    }
}
