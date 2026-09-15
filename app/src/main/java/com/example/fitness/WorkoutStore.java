package com.example.fitness;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 训练热力图数据源（桌面控件 + 应用内热力图共用）。
 * 由 WebView 的 JS 在每次记录变更后整体覆盖同步：
 *   {"2026-09-16":{"胸":3,"背":1},"2026-09-15":{"腿":5},...}
 * （值为当日各部位训练组数，控件按部位数分级上色）。
 * 每天一条，全年约 365 条，整体读写开销可忽略。
 */
public class WorkoutStore {

    private static final String TAG = "WorkoutStore";
    private static final String PREFS = "fitness_heatmap";
    private static final String KEY_DAYS = "days";
    private static final String[] PARTS = {"胸", "背", "腿", "肩", "腹"};

    /** 校验并规整 JS 传来的整份映射：只保留合法日期与已知部位，组数取整 */
    public static void saveDaysJson(Context ctx, String json) {
        if (ctx == null || json == null) return;
        String clean = sanitize(json);
        if (clean == null) return;
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_DAYS, clean).apply();
        } catch (Throwable t) {
            Log.w(TAG, "save failed", t);
        }
    }

    private static String sanitize(String json) {
        try {
            JSONObject in = new JSONObject(json);
            JSONObject out = new JSONObject();
            Iterator<String> it = in.keys();
            while (it.hasNext()) {
                String date = it.next();
                if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) continue;
                JSONObject day = in.optJSONObject(date);
                if (day == null) continue;
                JSONObject cleanDay = new JSONObject();
                for (String p : PARTS) {
                    int n = day.optInt(p, 0);
                    if (n > 0) cleanDay.put(p, n);
                }
                if (cleanDay.length() > 0) out.put(date, cleanDay);
            }
            return out.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    /** 读取「日期 -> 当日练到的部位集合」，供控件渲染 */
    public static Map<String, Set<String>> getDays(Context ctx) {
        Map<String, Set<String>> map = new HashMap<>();
        try {
            String json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_DAYS, null);
            if (json == null || json.isEmpty()) return map;
            JSONObject o = new JSONObject(json);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String date = it.next();
                JSONObject day = o.optJSONObject(date);
                if (day == null) continue;
                Set<String> parts = new HashSet<>();
                Iterator<String> pit = day.keys();
                while (pit.hasNext()) parts.add(pit.next());
                map.put(date, parts);
            }
        } catch (Throwable t) {
            Log.w(TAG, "load failed", t);
        }
        return map;
    }
}
