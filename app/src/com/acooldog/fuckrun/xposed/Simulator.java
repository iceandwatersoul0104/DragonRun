package com.acooldog.fuckrun.xposed;

import android.os.SystemClock;

import com.acooldog.fuckrun.sim.Frame;
import com.acooldog.fuckrun.sim.SimulationEngine;
import com.acooldog.fuckrun.util.Geo;

/** 目标进程内的轨迹计算器（与宿主同算法、同 seed）。 */
public class Simulator {
    private static final long CACHE_MS = 80;

    private SimulationEngine engine;
    private String signature = "";
    private long startRealtime;
    private volatile State state;

    private Frame cached;
    private long cachedAt;

    public synchronized void update(State s) {
        if (s == null) return;
        state = s;
        if (!s.running || !s.hasRoute()) {
            engine = null;
            signature = "";
            cached = null;
            return;
        }
        String sig = s.routeId + "|" + s.startedAt + "|" + s.config.seed + "|"
                + s.route.points.size() + "|" + s.config.mode + "|" + s.config.speedMps
                + "|" + s.config.cadenceSpm + "|" + s.config.loopCount;
        if (!sig.equals(signature)) {
            engine = new SimulationEngine(s.route, s.config);
            signature = sig;
            startRealtime = s.startedAt;
            cached = null;
        }
    }

    public State state() {
        return state;
    }

    public boolean running() {
        return engine != null && state != null && state.running;
    }

    public synchronized Frame frame() {
        SimulationEngine e = engine;
        if (e == null) return null;
        long now = SystemClock.elapsedRealtime();
        if (cached != null && now - cachedAt < CACHE_MS) return cached;
        long elapsed = now - startRealtime;
        if (elapsed < 0) elapsed = 0;
        if (elapsed > 8L * 3600 * 1000) return null;
        long dur = e.durationMs();
        if (dur > 1000) elapsed %= dur;
        cached = e.frameAt(elapsed);
        cachedAt = now;
        return cached;
    }

    public double[] gcj() {
        Frame f = frame();
        if (f == null) return null;
        return Geo.wgs84ToGcj02(f.lat, f.lng);
    }

    public double[] bd09() {
        Frame f = frame();
        if (f == null) return null;
        double[] g = Geo.wgs84ToGcj02(f.lat, f.lng);
        return Geo.gcj02ToBd09(g[0], g[1]);
    }
}
