package com.example.fitness;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.util.Log;

/**
 * 强提醒引擎（静态单例）：锁屏/全屏弹窗 + 系统闹铃音 + 震动 + 屏幕唤醒。
 * 前台（JS 桥接）与后台（CountdownReceiver）共用同一套逻辑。
 */
public final class Reminder {

    private static final String TAG = "FitnessReminder";
    private static final String CHANNEL_ID = "fitness_alarm";
    public static final int NOTIFY_ID = 9001;

    private static MediaPlayer alarmPlayer;
    private static Vibrator vibrator;
    private static PowerManager.WakeLock wakeLock;

    private static final String PREFS = "fitness_prefs";
    private static final String KEY_SOUND = "alarm_sound_mode";

    private Reminder() {}

    /** 铃声模式：0=内置尖锐铃声（默认），1=跟随系统闹铃音 */
    public static int soundMode(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SOUND, 0);
    }

    public static void setSoundMode(Context ctx, int mode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_SOUND, mode).apply();
    }

    public static void fire(Context ctx, int restSeconds, boolean isTest) {
        ensureChannel(ctx);
        startAlarmSound(ctx);
        showLockScreen(ctx, restSeconds, isTest);
        startVibrate(ctx);
        acquireWakeLock(ctx);
    }

    public static void dismiss(Context ctx) {
        stopSoundNow();
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFY_ID);
        AlarmActivity.dismiss(ctx);
        OverlayService.stop(ctx);
    }

    /** 仅停声音与震动（弹窗自身关闭时调用，避免连带关掉通知/服务） */
    public static void stopSoundNow() {
        stopAlarmSound();
        stopVibrate();
        releaseWakeLock();
    }

    /* ==================== 锁屏/全屏弹窗 ==================== */
    private static void showLockScreen(Context ctx, int restSeconds, boolean isTest) {
        Intent alarm = new Intent(ctx, AlarmActivity.class);
        alarm.putExtra(AlarmActivity.EXTRA_REST, restSeconds);
        alarm.putExtra(AlarmActivity.EXTRA_TEST, isTest);
        alarm.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // 全屏通知：锁屏/后台时由系统在锁屏上拉起 AlarmActivity（Android 10+ 后台启动的官方通道）
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, alarm,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            Notification.Builder b = new Notification.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(isTest ? "测试提醒" : "休息结束！")
                    .setContentText("该做下一组了（休息 " + restSeconds + " 秒）")
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setPriority(Notification.PRIORITY_MAX)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setFullScreenIntent(pi, true);
            // 声音统一由 MediaPlayer 直接播放（见 startAlarmSound），通知自身静音避免双音
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) b.setSound(null, null);
            else b.setSound(null);
            nm.notify(NOTIFY_ID, b.build());
        }
        // 前台/低版本兜底：直接启动弹窗；后台被系统拒绝时静默忽略
        try { ctx.startActivity(alarm); } catch (Throwable ignored) {}
    }

    /* ==================== 闹铃音：内置尖锐铃声 或 跟随系统闹铃音，不依赖通知权限 ==================== */
    private static void startAlarmSound(Context ctx) {
        try {
            stopAlarmSound();
            MediaPlayer p = new MediaPlayer();
            p.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            if (soundMode(ctx) == 1) {
                // 跟随系统闹铃音
                Uri uri = Settings.System.DEFAULT_ALARM_ALERT_URI;
                if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                if (uri == null) { Log.w(TAG, "no alarm uri"); return; }
                p.setDataSource(ctx, uri);
                p.prepare();
            } else {
                // 内置尖锐铃声（res/raw/alarm_tone.wav，高频方波，与普通铃声明显区分）
                android.content.res.AssetFileDescriptor afd =
                        ctx.getResources().openRawResourceFd(R.raw.alarm_tone);
                if (afd == null) { Log.w(TAG, "no raw tone"); return; }
                p.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                p.prepare();
            }
            p.setLooping(true);
            p.start();
            alarmPlayer = p;
        } catch (Throwable t) { Log.w(TAG, "alarm sound fail", t); }
    }

    private static void stopAlarmSound() {
        try {
            if (alarmPlayer != null) { alarmPlayer.stop(); alarmPlayer.release(); alarmPlayer = null; }
        } catch (Throwable ignored) {}
    }

    /* ==================== 震动 ==================== */
    private static void startVibrate(Context ctx) {
        try {
            vibrator = getVibrator(ctx);
            if (vibrator == null) return;
            long[] pattern = {0, 600, 200, 600, 200, 600, 200, 600, 200, 600};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 1));
            } else {
                vibrator.vibrate(pattern, 1);
            }
        } catch (Throwable t) { Log.w(TAG, "vibrate fail", t); }
    }

    private static void stopVibrate() {
        try { if (vibrator != null) vibrator.cancel(); } catch (Throwable ignored) {}
    }

    /* ==================== 屏幕唤醒 ==================== */
    private static void acquireWakeLock(Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "Fitness::AlarmWakeLock");
            wakeLock.acquire(30 * 1000L); // 最多 30 秒自动释放，防止耗电
        } catch (Throwable t) { Log.w(TAG, "wakelock fail", t); }
    }

    private static void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {}
    }

    /* ==================== 通知渠道 ==================== */
    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "训练提醒",
                    NotificationManager.IMPORTANCE_HIGH); // 高重要度 = 弹出（heads-up）
            ch.setDescription("倒计时归零时的强提醒");
            ch.enableVibration(true);
            ch.setBypassDnd(true);
            ch.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI,
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            nm.createNotificationChannel(ch);
        }
    }

    @SuppressWarnings("deprecation")
    private static Vibrator getVibrator(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm == null ? null : vm.getDefaultVibrator();
        }
        return (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
    }
}
