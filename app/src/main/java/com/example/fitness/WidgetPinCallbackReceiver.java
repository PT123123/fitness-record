package com.example.fitness;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * requestPinAppWidget 的钉选结果回调：
 * 用户确认放置后收到 EXTRA_APPWIDGET_ID，提示添加成功。
 * （后续如需「已添加后隐藏菜单项」，可在这里同步动态快捷方式。）
 */
public class WidgetPinCallbackReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (widgetId != -1) {
            Toast.makeText(context, R.string.widget_pin_success, Toast.LENGTH_SHORT).show();
        }
    }
}
