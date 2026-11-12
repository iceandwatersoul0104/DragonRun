package com.acooldog.fuckrun.sim;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.acooldog.fuckrun.data.AppPrefs;
import com.acooldog.fuckrun.model.Route;
import com.acooldog.fuckrun.model.RoutePoint;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** 模拟状态中心：生成供 Provider / LSPosed 读取的 state JSON。 */
public final class SimController {
    private static final String STATE_FILE = "state.json";

    private static Route route;
    private static SimConfig config;
    private static long startedAt;
    private static SimulationEngine engine;

    private SimController() {}

    public static synchronized boolean isRunning() {
        return route != null && config != null;
    }

    public static synchronized Route route() { return route; }

    public static synchronized SimConfig config() { return config; }

    public static synchronized long startedAt() { return startedAt; }

    public static synchronized void start(Context ctx, Route r, SimConfig c) {
        route = r;
        config = c;
        startedAt = SystemClock.elapsedRealtime();
        engine = new SimulationEngine(r, c);
        write(ctx, true);
        startService(ctx);
    }

    public static synchronized void stop(Context ctx) {
        write(ctx, false);
        route = null;
        config = null;
        engine = null;
        startedAt = 0;
        stopService(ctx);
    }

    /** 若当前有运行中的模拟，确保前台服务存活。 */
    public static void ensureService(Context ctx) {
        if (isRunning()) startService(ctx);
    }

    private static void startService(Context ctx) {        try {
            Intent i = new Intent(ctx, com.acooldog.fuckrun.StateService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Throwable t) {
            android.util.Log.e("FuckRun/State", "start state service failed", t);
        }
    }

    private static void stopService(Context ctx) {
        try {
            ctx.stopService(new Intent(ctx, com.acooldog.fuckrun.StateService.class));
        } catch (Throwable ignored) {
        }
    }

    /** 当前帧（应用内预览用）。 */
    public static synchronized Frame frame() {
        if (!isRunning() || engine == null) return null;
        long elapsed = SystemClock.elapsedRealtime() - startedAt;
        if (elapsed < 0 || elapsed > 6 * 3600 * 1000L) return null;
        return engine.frameAt(elapsed);
    }

    public static synchronized double totalDistance() {
        return engine == null ? 0 : engine.totalDistance();
    }

    public static synchronized void write(Context ctx, boolean running) {
        AppPrefs p = new AppPrefs(ctx);
        String json = null;
        try {
            JSONObject o = new JSONObject();
            o.put("running", running && route != null);
            o.put("startedAt", running ? startedAt : 0);
            o.put("cadenceOn", p.cadenceOn());
            o.put("cadenceMin", p.cadenceMin());
            o.put("cadenceMax", p.cadenceMax());
            o.put("antiRoot", p.antiRoot());
            o.put("antiMock", p.antiMock());
            o.put("silentLog", p.silentLog());
            o.put("adBlock", p.adBlock());
            o.put("satellites", p.satellites());
            o.put("hdop", p.hdop());
            o.put("accuracy", p.accuracy());
            o.put("ts", System.currentTimeMillis());
            if (route != null) {
                o.put("routeId", route.id);
                o.put("routeName", route.name);
                JSONArray arr = new JSONArray();
                for (RoutePoint pt : route.points) arr.put(pt.toJson());
                o.put("points", arr);
            }
            if (config != null) {
                o.put("config", config.toJson());
            }
            json = o.toString();
        } catch (Exception e) {
            android.util.Log.e("FuckRun/State", "build state failed", e);
        }
        if (json == null) return;
        try {
            File f = new File(ctx.getFilesDir(), STATE_FILE);
            File tmp = new File(ctx.getFilesDir(), STATE_FILE + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(json.getBytes("UTF-8"));
            out.flush();
            out.getFD().sync();
            out.close();
            if (!tmp.renameTo(f)) {
                // 退而求其次：直接覆盖
                FileOutputStream o2 = new FileOutputStream(f);
                o2.write(json.getBytes("UTF-8"));
                o2.close();
            }
            // 让注入模块（运行在目标进程）能读到
            f.setReadable(true, false);
            ctx.getFilesDir().setReadable(true, false);
            ctx.getFilesDir().setExecutable(true, false);
        } catch (Throwable t) {
            android.util.Log.e("FuckRun/State", "write state file failed", t);
        }
        try {
            p.setStateSync(json);
        } catch (Throwable ignored) {}
        writeShared(ctx, json);
    }

    /** 写到 Android/media 下本应用的媒体目录，供注入模块（目标进程）读取。 */
    private static void writeShared(Context ctx, String json) {
        try {
            File dir = null;
            File[] medias = ctx.getExternalMediaDirs();
            if (medias != null && medias.length > 0 && medias[0] != null) dir = medias[0];
            if (dir == null) return;
            if (!dir.isDirectory() && !dir.mkdirs()) {
                android.util.Log.e("FuckRun/State", "mkdirs failed " + dir);
                return;
            }
            File f = new File(dir, STATE_FILE);
            File tmp = new File(dir, STATE_FILE + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(json.getBytes("UTF-8"));
            out.flush();
            out.getFD().sync();
            out.close();
            if (!tmp.renameTo(f)) {
                FileOutputStream o2 = new FileOutputStream(f);
                o2.write(json.getBytes("UTF-8"));
                o2.close();
            }
            f.setReadable(true, false);
        } catch (Throwable t) {
            android.util.Log.e("FuckRun/State", "shared write failed", t);
        }
    }

    /** 读取磁盘上的 state（同进程与跨进程均可）。 */
    public static String readStateFile(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), STATE_FILE);
            if (!f.exists()) return "";
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    /** 进程重启后恢复运行中的模拟（startedAt 基于 elapsedRealtime，重启进程仍有效）。 */
    public static synchronized void restore(Context ctx) {
        if (isRunning()) return;
        String json = readStateFile(ctx);
        if (json.isEmpty()) return;
        try {
            JSONObject o = new JSONObject(json);
            if (!o.optBoolean("running", false)) return;
            long started = o.optLong("startedAt", 0);
            if (started <= 0) return;
            long elapsed = SystemClock.elapsedRealtime() - started;
            if (elapsed < 0 || elapsed > 6 * 3600 * 1000L) return;
            JSONObject ro = new JSONObject();
            ro.put("id", o.optString("routeId", ""));
            ro.put("name", o.optString("routeName", ""));
            ro.put("points", o.optJSONArray("points"));
            Route r = Route.fromJson(ro);
            if (r.points.size() < 2) return;
            JSONObject co = o.optJSONObject("config");
            if (co == null) return;
            SimConfig c = SimConfig.fromJson(co);
            route = r;
            config = c;
            startedAt = started;
            engine = new SimulationEngine(r, c);
            android.util.Log.i("FuckRun/State", "restored running route=" + r.name);
        } catch (Throwable t) {
            android.util.Log.e("FuckRun/State", "restore failed", t);
        }
    }

    /** 仅刷新配置部分（不改变运行状态）。 */
    public static void refresh(Context ctx) {
        write(ctx, isRunning());
    }
}
