package com.acooldog.fuckrun.sim;

import com.acooldog.fuckrun.model.Route;
import com.acooldog.fuckrun.model.RoutePoint;
import com.acooldog.fuckrun.util.Geo;

import java.util.List;

/**
 * 路线模拟引擎：按 tick 推进，输出 WGS84 帧。
 * 进程内预览与 LSPosed 注入端使用同一实现与同一 seed，保证两端轨迹一致。
 */
public class SimulationEngine {
    private static final double STEP_LENGTH_METERS = 0.8;

    private final List<RoutePoint> pts;
    private final SimConfig cfg;
    private final double[] cum;
    private final double total;
    private final double baseSpeed;

    private double traveled;
    private double speed;
    private long ticks;
    private double lateralSmooth;
    private double altSmooth;
    private double bearingSmooth;
    private boolean started;

    public SimulationEngine(Route route, SimConfig config) {
        this.pts = route.points;
        this.cfg = config;
        int n = pts.size();
        cum = new double[Math.max(1, n)];
        double d = 0;
        for (int i = 1; i < n; i++) {
            RoutePoint a = pts.get(i - 1), b = pts.get(i);
            d += Geo.haversine(a.lat, a.lng, b.lat, b.lng);
            cum[i] = d;
        }
        total = d;
        if (cfg.mode == SimConfig.MODE_CADENCE) {
            baseSpeed = Math.max(0.5, STEP_LENGTH_METERS * cfg.cadenceSpm / 60.0);
        } else {
            baseSpeed = Math.max(0.5, cfg.speedMps);
        }
        speed = baseSpeed;
    }

    public double totalDistance() {
        return total;
    }

    public double baseSpeed() {
        return baseSpeed;
    }

    public boolean isFinished() {
        return total <= 0 || traveled >= total * cfg.loopCount;
    }

    /** 一圈（按配置圈数）跑完所需的毫秒数，用于长期运行时循环回绕。 */
    public long durationMs() {
        double v = Math.max(0.5, baseSpeed);
        double dist = total * Math.max(1, cfg.loopCount);
        return (long) Math.max(1000.0, dist / v * 1000.0);
    }

    /** 确定性伪随机，[-1,1]。 */
    private double noise(double x) {
        double v = Math.sin(x * 12.9898 + cfg.seed * 0.0001) * 43758.5453;
        return ((v - Math.floor(v)) * 2.0) - 1.0;
    }

    public Frame next() {
        Frame f = new Frame();
        double dt = cfg.tickMillis / 1000.0;
        ticks++;

        double target = baseSpeed;
        if (cfg.dynamicIntensity) {
            double slow = noise(ticks * 0.02);
            target = baseSpeed + slow * cfg.intensityRangeMps * 0.8;
        }
        double lo = Math.max(0.5, baseSpeed * 0.35);
        double hi = baseSpeed + cfg.intensityRangeMps;
        target = Math.max(lo, Math.min(hi, target));
        speed += (target - speed) * 0.18;

        traveled += speed * dt;
        double limit = total * cfg.loopCount;
        if (total > 0 && traveled >= limit) {
            traveled = limit;
            f.finished = true;
        }

        double along = total <= 0 ? 0 : traveled % total;
        int idx = 0;
        for (int i = 1; i < pts.size(); i++) {
            if (cum[i] >= along) { idx = i; break; }
            idx = i;
        }
        RoutePoint a = pts.get(Math.max(0, Math.min(pts.size() - 1, idx - 1)));
        RoutePoint b = pts.get(Math.max(0, Math.min(pts.size() - 1, idx)));
        double segStart = cum[Math.max(0, idx - 1)];
        double segLen = Math.max(1e-6, Geo.haversine(a.lat, a.lng, b.lat, b.lng));
        double t = Math.max(0, Math.min(1, (along - segStart) / segLen));

        double lat = a.lat + (b.lat - a.lat) * t;
        double lng = a.lng + (b.lng - a.lng) * t;
        double altitude = a.altitude + (b.altitude - a.altitude) * t;
        double bearing = Geo.bearing(a.lat, a.lng, b.lat, b.lng);
        if (bearingSmooth == 0) bearingSmooth = bearing;

        if (cfg.pathVariation && total > 0) {
            double lateral = cfg.pathAmplitudeMeters * noise(ticks * 0.05) * 0.6;
            lateralSmooth += (lateral - lateralSmooth) * 0.18;
            double[] off = Geo.offset(lat, lng, bearing + 90, lateralSmooth);
            lat = off[0];
            lng = off[1];
        }

        if (cfg.altitudeVariation) {
            double baseAlt = cfg.altitudeBaseMeters + cfg.altitudeHeightCm / 100.0;
            double targetAlt = baseAlt + cfg.altitudeRangeMeters * noise(ticks * 0.03) * 0.5;
            if (!started) altSmooth = targetAlt;
            altSmooth += (targetAlt - altSmooth) * 0.12;
            altitude = altSmooth;
        }
        started = true;

        f.lat = lat;
        f.lng = lng;
        f.altitude = altitude;
        f.bearing = bearing;
        f.speed = speed;
        f.distance = traveled;
        f.elapsedMs = ticks * cfg.tickMillis;
        f.loop = (int) (total <= 0 ? 0 : traveled / total);
        return f;
    }

    /** 回到起点状态，使 frameAt 每次都是纯函数（支持循环回绕）。 */
    private void resetState() {
        traveled = 0;
        speed = baseSpeed;
        ticks = 0;
        lateralSmooth = 0;
        altSmooth = 0;
        bearingSmooth = 0;
        started = false;
    }

    /** 实时预览：从开始时间推导当前帧。 */
    public Frame frameAt(long elapsedMs) {
        resetState();
        long targetTicks = Math.max(1, elapsedMs / cfg.tickMillis);
        Frame f = next();
        while (ticks < targetTicks) {
            f = next();
            if (f.finished) break;
        }
        return f;
    }
}
