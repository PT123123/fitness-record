package com.example.fitness;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;

/**
 * 悬浮窗（SYSTEM_ALERT_WINDOW）：在其它 App 上层显示一个「休息结束」横条。
 * 属于可选增强——需用户在设置里授权「在其他应用上层显示」。
 * 未授权时 Bridge 会自动引导到授权页，不会崩溃。
 */
public class OverlayService extends Service {

    private static final String CH_ID = "overlay";
    private WindowManager wm;
    private TextView view;

    public static void start(Context c) { c.startService(new Intent(c, OverlayService.class)); }
    public static void stop(Context c) { c.stopService(new Intent(c, OverlayService.class)); }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(9002, buildNotification());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        view = new TextView(this);
        view.setText("🏋️ 休息结束 · 点我去记录");
        view.setBackgroundColor(0xEEEF5350);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(18);
        view.setPadding(40, 30, 40, 30);
        view.setOnClickListener(v -> {
            FitnessApp.evalJs("recordRestGroup()");
            stopSelf();
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        try { wm.addView(view, lp); } catch (Throwable t) { stopSelf(); }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (view != null && wm != null) {
            try { wm.removeView(view); } catch (Throwable ignored) {}
        }
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(CH_ID, "悬浮窗",
                        NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(ch);
            }
        }
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
        return new NotificationCompat.Builder(this, CH_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("训练提醒悬浮窗运行中")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
