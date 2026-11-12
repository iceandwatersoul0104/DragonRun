package com.acooldog.fuckrun.xposed;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** LSPosed 入口：仅作用于步道乐跑。 */
public class Module implements IXposedHookLoadPackage {
    public static final String TARGET = "com.lptiyu.tanke";
    public static final String ACTION_STATUS = "com.acooldog.fuckrun.STATUS";
    public static final String ACTION_READY = "com.acooldog.fuckrun.HOOK_READY";

    private static final Simulator SIM = new Simulator();
    private static boolean started;
    private static boolean hooksInstalled;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName)) return;
        synchronized (Module.class) {
            if (started) return;
            started = true;
        }
        Probe.install();
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Application app = (Application) p.thisObject;
                        init(app, lp.classLoader);
                    } catch (Throwable t) {
                        log("init failed: " + t);
                    }
                }
            });
            log("waiting for Application.onCreate");
        } catch (Throwable t) {
            log("Application hook failed: " + t);
        }
    }

    private static synchronized void init(Context ctx, ClassLoader cl) {
        if (hooksInstalled) return;
        hooksInstalled = true;
        try {
            final ProviderClient client = new ProviderClient(ctx);
            State first = client.fetch();
            SIM.update(first);
            send(ctx, ACTION_READY, false);

            LocationHooks.install(cl, SIM);
            SensorHooks.install(cl, SIM);
            AntiDetect.install(cl, SIM);
            Ads.install(cl, SIM);
            StateFile.start(SIM);
            StateChannel.start(SIM);
            log("hooks installed");

            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    long lastBeat = 0;
                    while (true) {
                        try {
                            long now = SystemClock.elapsedRealtime();
                            SIM.update(client.fetch());
                            if (now - lastBeat > 2000) {
                                lastBeat = now;
                                send(ctx, ACTION_STATUS, SIM.running());
                            }
                            Thread.sleep(700);
                        } catch (InterruptedException e) {
                            return;
                        } catch (Throwable e) {
                            try {
                                Thread.sleep(1500);
                            } catch (InterruptedException ie) {
                                return;
                            }
                        }
                    }
                }
            }, "fuckrun-state");
            t.setDaemon(true);
            t.start();
        } catch (Throwable t) {
            log("init body failed: " + t);
        }
    }

    private static void send(Context ctx, String action, boolean running) {
        try {
            Intent i = new Intent(action);
            i.setPackage("com.acooldog.fuckrun");
            i.putExtra("running", running);
            i.putExtra("ts", System.currentTimeMillis());
            ctx.sendBroadcast(i);
        } catch (Throwable ignored) {
        }
    }

    private static void log(String msg) {
        try {
            XposedBridge.log("FuckRun: " + msg);
        } catch (Throwable ignored) {
        }
    }
}
