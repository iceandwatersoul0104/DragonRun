package com.acooldog.fuckrun.xposed;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.acooldog.fuckrun.sim.Frame;
import com.acooldog.fuckrun.util.Geo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** 定位注入：接管高德 AMapLocation getter + 系统 Location/Listener 主动推送。 */
public class LocationHooks {
    private static final String TAG = "FuckRun/Loc";
    private static final long PULSE_MS = 200;

    private static final CopyOnWriteArrayList<LocationListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Object> AMAP_LISTENERS = new CopyOnWriteArrayList<>();
    private static Class<?> amapClass;
    private static volatile boolean pulseStarted = false;
    private static Simulator simulator;

    public static void install(ClassLoader cl, final Simulator sim) {
        simulator = sim;
        Class<?> amap = null;
        try {
            amap = XposedHelpers.findClass("com.amap.api.location.AMapLocation", cl);
        } catch (Throwable ignored) {
        }
        if (amap == null) {
            try {
                amap = Class.forName("com.amap.api.location.AMapLocation", false, cl);
            } catch (Throwable ignored) {
            }
        }
        if (amap != null) {
            amapClass = amap;
            hookAmap(amap, sim);
            log("AMapLocation hooked: " + amap.getName());
        } else {
            log("AMapLocation not found, fallback to android.location");
        }
        hookAmapClient(cl);
        hookAndroidLocation(sim);
        hookLocationManager(sim);
        startPulse();
    }

    /** 接管 AMapLocationClient 的监听器，主动回调合成定位（高德 SDK 自带引擎不经系统 LocationManager 时仍然生效）。 */
    private static void hookAmapClient(ClassLoader cl) {
        Class<?> client = null;
        try {
            client = XposedHelpers.findClass("com.amap.api.location.AMapLocationClient", cl);
        } catch (Throwable t) {
            log("AMapLocationClient not found: " + t);
            return;
        }
        hookAll(client, "setLocationListener", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args == null || p.args.length == 0) return;
                Object l = p.args[0];
                if (l != null && !AMAP_LISTENERS.contains(l)) AMAP_LISTENERS.add(l);
            }
        });
        hookAll(client, "startLocation", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                log("AMap startLocation, listeners=" + AMAP_LISTENERS.size() + " running=" + (simulator != null && simulator.running()));
            }
        });
    }

    /** 构造一个 AMapLocation 实例（字段无需正确，getter 已被 hook）。 */
    private static Object newAmapLocation() {
        Class<?> c = amapClass;
        if (c == null) return null;
        Location loc = build();
        if (loc == null) return null;
        try {
            java.lang.reflect.Constructor<?> k = c.getDeclaredConstructor(Location.class);
            k.setAccessible(true);
            Object a = k.newInstance(loc);
            fillAmap(a);
            return a;
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Constructor<?> k = c.getDeclaredConstructor();
            k.setAccessible(true);
            Object a = k.newInstance();
            fillAmap(a);
            return a;
        } catch (Throwable ignored) {
        }
        return null;
    }


    private static void fillAmap(Object a) {
        Frame f = simulator == null ? null : simulator.frame();
        if (f == null) return;
        State s = simulator.state();
        double[] g = Geo.wgs84ToGcj02(f.lat, f.lng);
        trySet(a, "setLatitude", g[0]);
        trySet(a, "setLongitude", g[1]);
        trySet(a, "setAltitude", f.altitude);
        trySet(a, "setBearing", (float) f.bearing);
        trySet(a, "setSpeed", (float) f.speed);
        trySet(a, "setAccuracy", (float) (s == null ? 3.0 : Math.max(3.0, s.accuracy)));
        trySet(a, "setTime", System.currentTimeMillis());
    }

    private static void trySet(Object o, String name, Object v) {
        try {
            for (java.lang.reflect.Method m : o.getClass().getMethods()) {
                if (!m.getName().equals(name) || m.getParameterTypes().length != 1) continue;
                Class<?> pt = m.getParameterTypes()[0];
                if (pt == double.class && v instanceof Double) { m.invoke(o, v); return; }
                if (pt == float.class && v instanceof Float) { m.invoke(o, v); return; }
                if (pt == long.class && v instanceof Long) { m.invoke(o, v); return; }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void dispatchAmap() {
        if (AMAP_LISTENERS.isEmpty()) return;
        Object loc = newAmapLocation();
        if (loc == null) return;
        for (Object l : AMAP_LISTENERS) {
            try {
                java.lang.reflect.Method m = l.getClass().getMethod("onLocationChanged", amapClass);
                m.invoke(l, loc);
            } catch (Throwable t) {
                AMAP_LISTENERS.remove(l);
            }
        }
    }

    private static void startPulse() {
        if (pulseStarted) return;
        pulseStarted = true;
        log("pulse starting on main looper=" + (Looper.getMainLooper() != null));
        final Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    tick++;
                    if (tick <= 3 || tick % 25 == 0) {
                        log("pulse running=" + (simulator != null && simulator.running()));
                    }
                    dispatch();
                } catch (Throwable t) {
                    long n = SystemClock.elapsedRealtime();
                    if (n - lastLogAt > 5000) {
                        lastLogAt = n;
                        log("dispatch failed: " + t);
                    }
                }
                h.postDelayed(this, PULSE_MS);
            }
        }, PULSE_MS);
    }

    private static Location build() {
        Simulator sim = simulator;
        if (sim == null || !sim.running()) return null;
        Frame f = sim.frame();
        if (f == null) return null;
        State s = sim.state();
        Location loc = new Location(LocationManager.GPS_PROVIDER);
        loc.setLatitude(f.lat);
        loc.setLongitude(f.lng);
        loc.setAltitude(f.altitude);
        loc.setBearing((float) f.bearing);
        loc.setSpeed((float) f.speed);
        loc.setAccuracy((float) (s == null ? 3.0 : Math.max(3.0, s.accuracy)));
        loc.setTime(System.currentTimeMillis());
        loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        try {
            android.os.Bundle b = new android.os.Bundle();
            b.putInt("satellites", s == null ? 9 : s.satellites);
            b.putDouble("hdop", s == null ? 1.5 : s.hdop);
            b.putInt("meanCn0", 36);
            b.putInt("maxCn0", 42);
            b.putString("source", "gps");
            loc.setExtras(b);
        } catch (Throwable ignored) {
        }
        if (s != null && s.antiMock) {
            try {
                XposedHelpers.setBooleanField(loc, "mIsFromMockProvider", false);
            } catch (Throwable ignored) {
            }
        }
        return loc;
    }

    private static long lastLogAt;
    private static long tick;

    private static void dispatch() {
        Location loc = null;
        try {
            loc = build();
        } catch (Throwable t) {
            log("build threw: " + t);
        }
        long now = SystemClock.elapsedRealtime();
        if (loc == null) {
            if (now - lastLogAt > 5000) {
                lastLogAt = now;
                log("build null running=" + (simulator != null && simulator.running())
                        + " gps=" + LISTENERS.size() + " amap=" + AMAP_LISTENERS.size());
            }
            return;
        }
        for (LocationListener l : LISTENERS) {
            try {
                l.onLocationChanged(loc);
            } catch (Throwable t) {
                LISTENERS.remove(l);
            }
        }
        dispatchAmap();

        if (now - lastLogAt > 15000) {
            lastLogAt = now;
            log("pulse gps=" + LISTENERS.size() + " amap=" + AMAP_LISTENERS.size()
                    + " lat=" + loc.getLatitude() + " lng=" + loc.getLongitude()
                    + " spd=" + loc.getSpeed());
        }
    }

    private static void hookAmap(Class<?> c, final Simulator sim) {
        hookDouble(c, "getLatitude", sim, 0);
        hookDouble(c, "getLongitude", sim, 1);
        hookDouble(c, "getAltitude", sim, 2);
        hookDouble(c, "getBearing", sim, 3);
        hookDouble(c, "getSpeed", sim, 4);
        hookDouble(c, "getAccuracy", sim, 5);
        hookAll(c, "getTime", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (!sim.running()) return;
                p.setResult(System.currentTimeMillis());
            }
        });
        hookAll(c, "getLocationType", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (!sim.running()) return;
                p.setResult(1);
            }
        });
        hookAll(c, "getErrorCode", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (!sim.running()) return;
                p.setResult(0);
            }
        });
        hookAll(c, "getErrorInfo", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (!sim.running()) return;
                p.setResult("success");
            }
        });
        hookAll(c, "isMock", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                State s = sim.state();
                if (s != null && s.antiMock) p.setResult(false);
            }
        });
        hookAll(c, "getSatellites", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                State s = sim.state();
                if (sim.running() && s != null) p.setResult(s.satellites);
            }
        });
        hookAll(c, "getHdop", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                State s = sim.state();
                if (sim.running() && s != null) p.setResult(s.hdop);
            }
        });
        hookAll(c, "getGpsAccuracyStatus", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (sim.running()) p.setResult(1);
            }
        });
        hookAll(c, "getLocationQuality", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (sim.running()) p.setResult(4);
            }
        });
        hookAll(c, "getProvider", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (sim.running()) p.setResult("gps");
            }
        });
        hookAll(c, "getCoordType", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (sim.running()) p.setResult("GCJ02");
            }
        });
    }

    private static void hookDouble(Class<?> c, String method, final Simulator sim, final int idx) {
        hookAll(c, method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (!sim.running()) return;
                Frame f = sim.frame();
                if (f == null) return;
                State s = sim.state();
                double v;
                switch (idx) {
                    case 0: v = Geo.wgs84ToGcj02(f.lat, f.lng)[0]; break;
                    case 1: v = Geo.wgs84ToGcj02(f.lat, f.lng)[1]; break;
                    case 2: v = f.altitude; break;
                    case 3: v = f.bearing; break;
                    case 4: v = f.speed; break;
                    default: v = s == null ? 3.0 : s.accuracy; break;
                }
                if (((java.lang.reflect.Method) p.method).getReturnType() == float.class) p.setResult((float) v);
                else p.setResult(v);
            }
        });
    }

    private static void hookAndroidLocation(final Simulator sim) {
        hookAll(Location.class, "getLatitude", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                Frame f = sim.frame();
                if (f != null && sim.running()) p.setResult(f.lat);
            }
        });
        hookAll(Location.class, "getLongitude", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                Frame f = sim.frame();
                if (f != null && sim.running()) p.setResult(f.lng);
            }
        });
        hookAll(Location.class, "getAltitude", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                Frame f = sim.frame();
                if (f != null && sim.running()) p.setResult((double) f.altitude);
            }
        });
        hookAll(Location.class, "getBearing", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                Frame f = sim.frame();
                if (f != null && sim.running()) p.setResult((float) f.bearing);
            }
        });
        hookAll(Location.class, "getSpeed", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                Frame f = sim.frame();
                if (f != null && sim.running()) p.setResult((float) f.speed);
            }
        });
        hookAll(Location.class, "getAccuracy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                State s = sim.state();
                if (sim.running() && s != null) p.setResult((float) s.accuracy);
            }
        });
        hookAll(Location.class, "getTime", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (sim.running()) p.setResult(System.currentTimeMillis());
            }
        });
        hookAll(Location.class, "getElapsedRealtimeNanos", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (sim.running()) p.setResult(SystemClock.elapsedRealtimeNanos());
            }
        });
        hookAll(Location.class, "isFromMockProvider", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                State s = sim.state();
                if (s != null && s.antiMock) p.setResult(false);
            }
        });
    }

    private static void hookLocationManager(final Simulator sim) {
        hookAll(LocationManager.class, "getLastKnownLocation", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                Location loc = build();
                if (loc != null) p.setResult(loc);
            }
        });
        hookAll(LocationManager.class, "requestLocationUpdates", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                for (Object a : p.args) {
                    if (a instanceof LocationListener && !LISTENERS.contains(a)) {
                        LISTENERS.add((LocationListener) a);
                    }
                }
            }
        });
        hookAll(LocationManager.class, "requestSingleUpdate", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                for (Object a : p.args) {
                    if (a instanceof LocationListener && !LISTENERS.contains(a)) {
                        LISTENERS.add((LocationListener) a);
                    }
                }
            }
        });
        hookAll(LocationManager.class, "removeUpdates", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                for (Object a : p.args) {
                    if (a instanceof LocationListener) LISTENERS.remove(a);
                }
            }
        });
        hookAll(LocationManager.class, "isProviderEnabled", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (sim.running()) p.setResult(true);
            }
        });
    }

    private static void hookAll(Class<?> c, String name, XC_MethodHook hook) {
        if (c == null) return;
        try {
            int n = Hooks.hookAll(c, name, hook);
            if (n == 0) log("hook " + name + " matched nothing");
        } catch (Throwable t) {
            log("hook " + name + " failed: " + t);
        }
    }

    private static void log(String msg) {
        try {
            XposedBridge.log(TAG + ": " + msg);
        } catch (Throwable ignored) {
        }
    }
}
