package com.example.fitness;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * 倒计时前台服务：
 *  - 倒计时期间常驻前台（静音常驻通知），防止国产 ROM 杀后台进程；
 *  - 到点由进程内 Handler 准时触发强提醒，不依赖精确闹钟权限/后台广播；
 *  - 同时登记系统精确闹钟作兜底：若本服务被系统/厂商杀掉，闹钟广播仍会到点拉起提醒。
 */
public class CountdownService extends Service {

    private static final String TAG = "CountdownService";
    private static final String CHANNEL_CD = "countdown_ongoing";
    private static final int NOTIFY_CD = 9002;

    private static final String EXTRA_REST = "rest";
    private static final String EXTRA_END = "end";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long endAtMillis = 0L;

    /** 开始倒计时保活（JS 桥接调用）。restSeconds 秒后到点触发提醒。 */
    public static void start(Context ctx, int restSeconds, long endAtMillis) {
        Reminder.resetDedup(); // 新一轮倒计时：清除上一轮的去重标记，避免误吞本次提醒
        Intent i = new Intent(ctx, CountdownService.class)
                .putExtra(EXTRA_REST, restSeconds)
                .putExtra(EXTRA_END, endAtMillis);
        try {
            ContextCompat.startForegroundService(ctx, i);
        } catch (Throwable t) {
            Log.w(TAG, "startForegroundService fail, alarm only", t);
            CountdownScheduler.schedule(ctx, restSeconds, endAtMillis);
        }
    }

    /** 停止倒计时保活（暂停/重置/记录完成时调用），同时撤销兜底闹钟 */
    public static void stop(Context ctx) {
        try { ctx.stopService(new Intent(ctx, CountdownService.class)); } catch (Throwable ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int rest = intent != null ? intent.getIntExtra(EXTRA_REST, 0) : 0;
        long end = intent != null ? intent.getLongExtra(EXTRA_END, 0) : 0;
        if (end <= System.currentTimeMillis()) { stopSelf(); return START_NOT_STICKY; }

        endAtMillis = end;
        startForeground(NOTIFY_CD, buildNotification(rest)); // manifest 已声明 specialUse 类型

        // 兜底：系统精确闹钟（前台服务被系统回收时仍能到点拉起广播提醒）
        CountdownScheduler.schedule(this, rest, end);

        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::fire, Math.max(0, end - System.currentTimeMillis()));
        handler.post(ticker);
        return START_NOT_STICKY;
    }

    /** 到点：撤掉兜底闹钟 → 停止服务 → 触发强提醒（弹窗 + 门铃/系统闹铃音 + 震动 + 亮屏） */
    private void fire() {
        int rest = (int) Math.max(0, (endAtMillis - System.currentTimeMillis() + 500) / 1000);
        CountdownScheduler.cancel(this);
        stopForeground(true);
        stopSelf();
        Reminder.fire(this, rest, false);
    }

    /** 常驻通知倒计时文案每秒刷新 */
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long left = endAtMillis - System.currentTimeMillis();
            if (left <= 0) return;
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFY_CD, buildNotification((int) (left / 1000)));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        CountdownScheduler.cancel(this); // 保险：正常停止一律撤销系统闹钟，避免重复/残留提醒
        super.onDestroy();
    }

    private Notification buildNotification(int rest) {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CHANNEL_CD)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("休息倒计时进行中")
                .setContentText("剩余 " + fmt(rest) + "，到点自动提醒")
                .setOngoing(true)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) b.setSound(null, null);
        else b.setSound(null);
        return b.build();
    }

    private static String fmt(int sec) {
        return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL_CD, "倒计时进行中",
                    NotificationManager.IMPORTANCE_LOW); // 静音常驻，不打扰
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
