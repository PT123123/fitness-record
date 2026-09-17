package com.example.fitness;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 锁屏通知上的操作按钮：
 * 当系统全屏弹窗因权限/ROM 原因没在锁屏上弹出时，用户仍可从锁屏通知直接
 * 「关闭提醒」或「再休息1分钟」，避免"锁屏只响铃、关不掉"的情况。
 */
public class AlarmActionReceiver extends BroadcastReceiver {

    public static final String ACTION_DISMISS = "com.example.fitness.ACTION_DISMISS_ALARM";
    public static final String ACTION_SNOOZE = "com.example.fitness.ACTION_SNOOZE_ALARM";

    private static final int SNOOZE_SECONDS = 60;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_DISMISS.equals(action)) {
            Reminder.dismiss(context); // 停声音/震动/亮屏，关掉弹窗与通知
        } else if (ACTION_SNOOZE.equals(action)) {
            Reminder.dismiss(context);
            // 重新开始 60 秒休息倒计时：原生前台服务保活 + 页面状态同步（WebView 若还在）
            long endAt = System.currentTimeMillis() + SNOOZE_SECONDS * 1000L;
            CountdownService.start(context, SNOOZE_SECONDS, endAt);
            FitnessApp.evalJs("restartCountdown(" + SNOOZE_SECONDS + ")");
        }
    }
}
