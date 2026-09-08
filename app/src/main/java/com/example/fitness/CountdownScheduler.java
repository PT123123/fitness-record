package com.example.fitness;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 倒计时精确调度：把「到点」交给系统 AlarmManager。
 * 锁屏/后台/进程被杀时，系统都会在结束时刻准时拉起 CountdownReceiver 触发提醒。
 */
public final class CountdownScheduler {

    public static final String ACTION_FIRE = "com.example.fitness.COUNTDOWN_FIRE";
    public static final String EXTRA_REST = "rest";
    private static final int REQ_CODE = 2001; // 同一时刻只会有一个倒计时

    private CountdownScheduler() {}

    /** 登记一个倒计时：restSeconds 秒后（endAtMillis 时刻）准时提醒 */
    public static void schedule(Context ctx, int restSeconds, long endAtMillis) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(ctx);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // Android 12+ 无精确闹钟权限时降级为不精确（可能延迟，但 USE_EXACT_ALARM 已声明则不会走到这里）
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, endAtMillis, pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, endAtMillis, pi);
        }
    }

    /** 取消倒计时（暂停/重置/记录完成时调用） */
    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pending(ctx));
    }

    private static PendingIntent pending(Context ctx) {
        Intent i = new Intent(ctx, CountdownReceiver.class).setAction(ACTION_FIRE);
        return PendingIntent.getBroadcast(ctx, REQ_CODE, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
