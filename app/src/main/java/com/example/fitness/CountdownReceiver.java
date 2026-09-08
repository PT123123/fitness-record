package com.example.fitness;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 倒计时到点接收器：系统闹钟触发 → 拉起锁屏强提醒，并同步页面状态。
 * 即使 App 被系统杀死、屏幕锁定，这里也会被执行。
 */
public class CountdownReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !CountdownScheduler.ACTION_FIRE.equals(intent.getAction())) return;
        final int rest = intent.getIntExtra(CountdownScheduler.EXTRA_REST, 0);
        // 若 WebView 进程还活着，让页面同步成「已归零」（idempotent，前台路径已处理则无操作）
        FitnessApp.evalJs("onNativeCountdownFinish(" + rest + ")");
        Reminder.fire(context, rest, false);
    }
}
