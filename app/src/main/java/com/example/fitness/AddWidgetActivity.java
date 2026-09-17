package com.example.fitness;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * 长按 App 图标 →「添加热力图控件」菜单项的中转页。
 * 透明无界面：进来后直接请求系统把热力图控件钉选到桌面（Android 8.0+ 系统级流程），随后立即关闭。
 */
public class AddWidgetActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName widget = new ComponentName(this, HeatmapWidgetProvider.class);

        if (!manager.isRequestPinAppWidgetSupported()) {
            // 桌面不支持钉选（个别三方桌面），降级引导手动添加
            Toast.makeText(this, R.string.widget_pin_not_supported, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 可选：向桌面提示控件期望的默认尺寸（2x2，与 heatmap_widget_info.xml 保持一致）
        Bundle extras = new Bundle();
        extras.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110);
        extras.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);

        // 钉选结果回调：成功时 Intent 里带 EXTRA_APPWIDGET_ID
        Intent callbackIntent = new Intent(this, WidgetPinCallbackReceiver.class);
        PendingIntent callback = PendingIntent.getBroadcast(this, 0, callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        manager.requestPinAppWidget(widget, extras, callback);
        finish(); // 系统接管后续的放置界面，这里立即关闭
    }
}
