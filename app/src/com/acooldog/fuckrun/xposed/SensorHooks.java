package com.acooldog.fuckrun.xposed;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** 步频注入：接管目标进程的加速度计与计步器事件。 */
public class SensorHooks {
    private static final String TAG = "FuckRun/Sensor";
    private static final Set<String> HOOKED = new HashSet<>();
    private static final Random RND = new Random();

    private static long mockStartTime;
    private static float baseStepOffset = -1f;
    private static float currentCadence = 176f;
    private static boolean lastOn;
    private static long lastLogAt;

    public static void install(ClassLoader cl, final Simulator sim) {
        hookRegister(SensorManager.class, sim);
        hookRegister(SystemSensorManagerClass(cl), sim);
        try {
            Class<?> oppo = XposedHelpers.findClass("com.oplus.sensor.OplusSensorManager", cl);
            hookRegister(oppo, sim);
        } catch (Throwable ignored) {
        }
    }

    private static Class<?> SystemSensorManagerClass(ClassLoader cl) {
        try {
            return XposedHelpers.findClass("android.hardware.SystemSensorManager", cl);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void hookRegister(Class<?> c, final Simulator sim) {
        if (c == null) return;
        try {
            Hooks.hookAll(c, "registerListener", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    for (Object a : p.args) {
                        if (a instanceof SensorEventListener) {
                            hookListener(a.getClass(), sim);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            log("registerListener hook failed: " + t);
        }
    }

    private static void hookListener(final Class<?> listener, final Simulator sim) {
        String key = listener.getName();
        synchronized (HOOKED) {
            if (HOOKED.contains(key)) return;
            HOOKED.add(key);
        }
        try {
            int n = Hooks.hookAll(listener, "onSensorChanged", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        rewrite(p, sim);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable t) {
            log("onSensorChanged hook failed for " + key + ": " + t);
        }
    }

    private static void rewrite(XC_MethodHook.MethodHookParam p, Simulator sim) {
        State s = sim.state();
        boolean on = s != null && s.cadenceOn;
        if (on != lastOn) log("cadence " + (on ? "ON" : "OFF") + " min=" + (s == null ? -1 : s.cadenceMin));
        if (on && !lastOn) {
            mockStartTime = SystemClock.elapsedRealtime();
            baseStepOffset = -1f;
            currentCadence = s.cadenceMin + RND.nextFloat() * Math.max(1f, s.cadenceMax - s.cadenceMin);
        }
        lastOn = on;
        if (!on) return;
        if (p.args == null || p.args.length == 0 || !(p.args[0] instanceof SensorEvent)) return;
        SensorEvent e = (SensorEvent) p.args[0];
        if (e == null || e.values == null) return;
        int type = e.sensor == null ? -1 : e.sensor.getType();

        if (type == Sensor.TYPE_ACCELEROMETER && e.values.length >= 3) {
            double t = (SystemClock.elapsedRealtime() - mockStartTime) / 1000.0;
            double f = currentCadence / 60.0;
            double phase = 2 * Math.PI * f * t;
            e.values[0] = 0f;
            e.values[1] = (float) (Math.cos(phase) * 1.5 + (RND.nextDouble() - 0.5) * 0.3);
            e.values[2] = (float) (9.81 + Math.sin(phase) * 3.5 + (RND.nextDouble() - 0.5) * 0.3);
        } else if (type == Sensor.TYPE_STEP_COUNTER && e.values.length >= 1) {
            if (baseStepOffset < 0) baseStepOffset = e.values[0];
            double elapsed = (SystemClock.elapsedRealtime() - mockStartTime) / 1000.0;
            e.values[0] = (float) (baseStepOffset + elapsed * (currentCadence / 60.0));
        } else if (type == Sensor.TYPE_STEP_DETECTOR && e.values.length >= 1) {
            e.values[0] = 1f;
        }
        e.timestamp = SystemClock.elapsedRealtimeNanos();
    }

    private static void log(String msg) {
        try {
            XposedBridge.log(TAG + ": " + msg);
        } catch (Throwable ignored) {
        }
    }
}
