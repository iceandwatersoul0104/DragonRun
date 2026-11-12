package com.acooldog.fuckrun.data;

import android.content.Context;
import android.content.SharedPreferences;

/** 应用与模块共享的配置。 */
public class AppPrefs {
    private static final String NAME = "fuckrun";

    public static final String K_STATE = "state";
    public static final String K_CADENCE_ON = "cadence_on";
    public static final String K_CADENCE_MIN = "cadence_min";
    public static final String K_CADENCE_MAX = "cadence_max";
    public static final String K_ANTI_ROOT = "anti_root";
    public static final String K_ANTI_MOCK = "anti_mock";
    public static final String K_SILENT_LOG = "silent_log";
    public static final String K_SATELLITES = "satellites";
    public static final String K_HDOP = "hdop";
    public static final String K_ACCURACY = "accuracy";
    public static final String K_LAST_ROUTE = "last_route_id";
    public static final String K_HOOK_TIME = "hook_time";
    public static final String K_AD_BLOCK = "ad_block";
    public static final String K_THEME = "theme";

    private final SharedPreferences sp;

    public AppPrefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public String state() {
        return sp.getString(K_STATE, "");
    }

    public void setState(String json) {
        sp.edit().putString(K_STATE, json).apply();
    }

    /** 同步落盘，返回是否成功。 */
    public boolean setStateSync(String json) {
        return sp.edit().putString(K_STATE, json).commit();
    }

    public boolean cadenceOn() { return sp.getBoolean(K_CADENCE_ON, false); }
    public void setCadenceOn(boolean v) { sp.edit().putBoolean(K_CADENCE_ON, v).apply(); }

    public float cadenceMin() { return sp.getFloat(K_CADENCE_MIN, 172f); }
    public float cadenceMax() { return sp.getFloat(K_CADENCE_MAX, 182f); }
    public void setCadenceRange(float min, float max) {
        if (min < 140f) min = 140f;
        if (max > 220f) max = 220f;
        if (min > max - 2f) max = min + 2f;
        sp.edit().putFloat(K_CADENCE_MIN, min).putFloat(K_CADENCE_MAX, max).apply();
    }

    public boolean antiRoot() { return sp.getBoolean(K_ANTI_ROOT, true); }
    public void setAntiRoot(boolean v) { sp.edit().putBoolean(K_ANTI_ROOT, v).apply(); }

    public boolean antiMock() { return sp.getBoolean(K_ANTI_MOCK, true); }
    public void setAntiMock(boolean v) { sp.edit().putBoolean(K_ANTI_MOCK, v).apply(); }

    public boolean silentLog() { return sp.getBoolean(K_SILENT_LOG, false); }
    public void setSilentLog(boolean v) { sp.edit().putBoolean(K_SILENT_LOG, v).apply(); }

    public boolean adBlock() { return sp.getBoolean(K_AD_BLOCK, true); }
    public void setAdBlock(boolean v) { sp.edit().putBoolean(K_AD_BLOCK, v).apply(); }

    public String theme() { return sp.getString(K_THEME, "blue"); }
    public void setTheme(String v) { sp.edit().putString(K_THEME, "red".equals(v) ? "red" : "blue").apply(); }

    public int satellites() { return sp.getInt(K_SATELLITES, 9); }
    public float hdop() { return sp.getFloat(K_HDOP, 1.5f); }
    public float accuracy() { return sp.getFloat(K_ACCURACY, 3.0f); }
    public void setSignal(int sat, float hdop, float accuracy) {
        if (sat < 1) sat = 1;
        if (sat > 12) sat = 12;
        if (hdop < 0.5f) hdop = 0.5f;
        if (hdop > 3.0f) hdop = 3.0f;
        if (accuracy < 1f) accuracy = 1f;
        if (accuracy > 30f) accuracy = 30f;
        sp.edit().putInt(K_SATELLITES, sat).putFloat(K_HDOP, hdop).putFloat(K_ACCURACY, accuracy).apply();
    }

    public String lastRouteId() { return sp.getString(K_LAST_ROUTE, ""); }
    public void setLastRouteId(String id) { sp.edit().putString(K_LAST_ROUTE, id).apply(); }

    public void markHookSeen() { sp.edit().putLong(K_HOOK_TIME, System.currentTimeMillis()).apply(); }
    public long hookTime() { return sp.getLong(K_HOOK_TIME, 0); }
}
