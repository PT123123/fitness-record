package com.example.fitness;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;

/**
 * 全局单例：桥接 JS ↔ 原生。
 * JS 通过 FitnessNative.fire/dismiss 调进来；原生弹窗按钮再通过 evalJs() 回调用 JS。
 */
public class FitnessApp extends Application {

    private static WeakReference<MainActivity> sActivity = new WeakReference<>(null);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) { track(a); }
            @Override public void onActivityStarted(Activity a) { track(a); }
            @Override public void onActivityResumed(Activity a) { track(a); }
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
            private void track(Activity a) { if (a instanceof MainActivity) sActivity = new WeakReference<>((MainActivity) a); }
        });
    }

    /** 让 JS 回调（如 recordRestGroup()）跑在主线程的 WebView 里 */
    public static void evalJs(final String js) {
        UI.post(() -> {
            MainActivity a = sActivity.get();
            if (a != null) a.evalJs(js);
        });
    }
}
