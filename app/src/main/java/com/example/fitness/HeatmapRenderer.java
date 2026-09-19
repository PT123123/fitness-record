package com.example.fitness;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 把「某月哪天练了什么」渲染成一张位图，供桌面 2x2 热力图控件使用。
 * 布局：月标题（含本月训练天数）→ 周表头（一~日）→ 6 行 × 7 列日历格子（周一起始）。
 * 格子颜色按当天练到的部位数分级（1/2/3+），只练肩/腹为弱色，今天加白色描边。
 */
public class HeatmapRenderer {

    // 分级色：0 未练 / 1 只练肩腹 / 2 练到1个主要部位 / 3 练到2个 / 4 练到3个
    private static final int[] LEVEL_COLORS = {
            0xFF232323,
            0xFF2b4d63,
            0xFF1e5e8f,
            0xFF2f89c9,
            0xFF4fc3f7
    };

    public static Bitmap render(Context ctx, Map<String, Set<String>> days, int wPx, int hPx, long nowMillis) {
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, wPx), Math.max(1, hPx), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float d = ctx.getResources().getDisplayMetrics().density;

        // 背景（深色圆角）
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xFF141414);
        float corner = 14f * d;
        c.drawRoundRect(new RectF(0, 0, wPx, hPx), corner, corner, bg);

        float pad = 7f * d;
        float gap = 1.5f * d;
        float titleH = 15f * d;
        float headerH = 11f * d;
        float footerH = 11f * d;
        float contentW = wPx - 2 * pad;
        float cellW = (contentW - 6 * gap) / 7f;
        float gridTop = pad + titleH + headerH;
        float gridH = hPx - gridTop - pad - footerH;
        float cellH = (gridH - 5 * gap) / 6f;
        if (cellW <= 0 || cellH <= 0) return bmp;

        // 训练日归属：每天截止到早上4点（凌晨0-4点练的算前一天），整体平移4小时判定
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(nowMillis - 4L * 60 * 60 * 1000);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int todayDom = cal.get(Calendar.DAY_OF_MONTH);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        Calendar first = Calendar.getInstance();
        first.set(year, month, 1);
        int firstDow = (first.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7; // 周一起始，周一=0

        // 月标题：2026年9月 · 练N天
        int trained = 0;
        for (int dom = 1; dom <= daysInMonth; dom++) {
            if (days.containsKey(key(year, month, dom))) trained++;
        }
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTextSize(11f * d);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        titlePaint.setTextAlign(Paint.Align.LEFT);
        Paint.FontMetrics fm = titlePaint.getFontMetrics();
        float titleBaseline = pad + (titleH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
        c.drawText(String.format(Locale.CHINA, "%d年%d月 · 练%d天", year, month + 1, trained),
                pad, titleBaseline, titlePaint);

        // 周表头
        String[] wd = {"一", "二", "三", "四", "五", "六", "日"};
        Paint wdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wdPaint.setColor(0xFF8a8a8a);
        wdPaint.setTextSize(8.5f * d);
        wdPaint.setTextAlign(Paint.Align.CENTER);
        fm = wdPaint.getFontMetrics();
        float wdBaseline = pad + titleH + (headerH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
        for (int col = 0; col < 7; col++) {
            c.drawText(wd[col], pad + col * (cellW + gap) + cellW / 2f, wdBaseline, wdPaint);
        }

        // 日历格子
        Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.5f * d);
        stroke.setColor(0xFFFFFFFF);
        Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        numPaint.setTextSize(8f * d);
        numPaint.setTypeface(Typeface.DEFAULT_BOLD);
        numPaint.setTextAlign(Paint.Align.CENTER);
        float cellCorner = 4f * d;

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                int dom = row * 7 + col - firstDow + 1; // 该格对应的日号
                if (dom < 1 || dom > daysInMonth) continue;
                float x = pad + col * (cellW + gap);
                float y = gridTop + row * (cellH + gap);
                RectF rc = new RectF(x, y, x + cellW, y + cellH);

                Set<String> parts = days.get(key(year, month, dom));
                int level = level(parts);
                cellPaint.setColor(LEVEL_COLORS[level]);
                c.drawRoundRect(rc, cellCorner, cellCorner, cellPaint);
                if (dom == todayDom) {
                    c.drawRoundRect(rc, cellCorner, cellCorner, stroke);
                }

                numPaint.setColor(level == 0 ? 0xFF6b6b6b : 0xFFFFFFFF);
                fm = numPaint.getFontMetrics();
                float cx = x + cellW / 2f;
                float cy = y + cellH / 2f + (fm.descent - fm.ascent) / 2f - fm.descent;
                c.drawText(String.valueOf(dom), cx, cy, numPaint);
            }
        }

        // 底部：各部位最近一次训练距今天数（胸/背/腿分色，与日历格子主色一致）
        Paint footPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footPaint.setTextSize(8.5f * d);
        footPaint.setTextAlign(Paint.Align.CENTER);
        fm = footPaint.getFontMetrics();
        float footY = hPx - pad - (footerH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
        float segW = contentW / 3f;
        String[] partNames = {"胸", "背", "腿"};
        int[] partColors = {0xFF4fc3f7, 0xFF66bb6a, 0xFFffa726};
        for (int i = 0; i < 3; i++) {
            int ago = lastTrainedDaysAgo(days, partNames[i], year, month, todayDom);
            String text = ago < 0 ? partNames[i] + " —" : partNames[i] + " " + ago + "天前";
            footPaint.setColor(partColors[i]);
            c.drawText(text, pad + segW * i + segW / 2f, footY, footPaint);
        }
        return bmp;
    }

    /** 某部位最近一次训练是几天前（以"今天"= 平移4点后的日期为基准；从未练过返回 -1） */
    private static int lastTrainedDaysAgo(Map<String, Set<String>> days, String part,
                                          int year, int month, int todayDom) {
        Calendar probe = Calendar.getInstance();
        probe.clear();
        probe.set(year, month, todayDom);
        for (int i = 0; i < 400; i++) {
            Set<String> parts = days.get(key(probe.get(Calendar.YEAR),
                    probe.get(Calendar.MONTH), probe.get(Calendar.DAY_OF_MONTH)));
            if (parts != null && parts.contains(part)) return i;
            probe.add(Calendar.DAY_OF_MONTH, -1);
        }
        return -1;
    }

    /** 分级：0 未练；1 只练肩/腹；2/3/4 = 练到 1/2/3 个主要部位（胸背腿） */
    private static int level(Set<String> parts) {
        if (parts == null || parts.isEmpty()) return 0;
        int main = 0;
        boolean other = false;
        for (String p : parts) {
            if ("胸".equals(p) || "背".equals(p) || "腿".equals(p)) main++;
            else other = true;
        }
        if (main == 0 && other) return 1;
        if (main == 1) return 2;
        if (main == 2) return 3;
        return 4;
    }

    private static String key(int year, int month, int dom) {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dom);
    }
}
