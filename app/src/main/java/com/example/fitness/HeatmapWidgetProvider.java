package com.example.fitness;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.util.Map;
import java.util.Set;

/**
 * 桌面 2x2 训练热力图控件。
 * 按月历渲染「哪天练了什么」（强度=当天练到的部位数），数据来自 WorkoutStore，
 * 由 App 内 JS 在每次记录变更后通过 FitnessNative.saveWorkoutSummary 同步。
 * 点击控件任意位置 → 打开 App；跨日/改时间后自动重绘当月视图。
 */
public class HeatmapWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateWidget(context, appWidgetManager, id);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        updateWidget(context, appWidgetManager, appWidgetId);
    }

    /** 数据变化或跨日后由外部触发：重绘所有已添加的控件 */
    public static void requestUpdate(Context context) {
        try {
            AppWidgetManager am = AppWidgetManager.getInstance(context);
            int[] ids = am.getAppWidgetIds(new ComponentName(context, HeatmapWidgetProvider.class));
            for (int id : ids) {
                updateWidget(context, am, id);
            }
        } catch (Throwable t) {
            // 控件刷新失败不影响 App 本体
        }
    }

    private static void updateWidget(Context context, AppWidgetManager am, int widgetId) {
        try {
            // 2x2 控件：取系统实际给到的宽高（dp），换算成像素渲染位图
            int wDp = 110, hDp = 110;
            Bundle opts = am.getAppWidgetOptions(widgetId);
            if (opts != null) {
                int w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
                int h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
                if (w > 0) wDp = w;
                if (h > 0) hDp = h;
            }
            float density = context.getResources().getDisplayMetrics().density;
            int wPx = Math.max(150, Math.round(wDp * density));
            int hPx = Math.max(150, Math.round(hDp * density));

            Map<String, Set<String>> days = WorkoutStore.getDays(context);
            Bitmap bmp = HeatmapRenderer.render(context, days, wPx, hPx, System.currentTimeMillis());

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_heatmap);
            views.setImageViewBitmap(R.id.heatmap_img, bmp);

            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.heatmap_root, pi);

            am.updateAppWidget(widgetId, views);
        } catch (Throwable t) {
            // 单次渲染失败直接跳过，避免拖垮广播线程
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            requestUpdate(context);
        }
    }
}
