package com.acooldog.fuckrun.xposed;

import android.content.ContentResolver;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** 反检测：root / Magisk / Xposed / 模拟定位标记。 */
public class AntiDetect {
    private static final String TAG = "FuckRun/Anti";
    private static final Pattern SUSPECT = Pattern.compile(
            "(?i).*(/su$|/su/|/magisk|/busybox$|xposed|frida|zygisk|supersu|superuser).*");

    public static void install(ClassLoader cl, final Simulator sim) {
        hookFileExists(sim);
        hookSettings(sim);
        hookRootBeer(cl, sim);
        hookBuild();
        hookDebug();
    }

    private static void hookFileExists(final Simulator sim) {
        try {
            XposedHelpers.findAndHookMethod(File.class, "exists", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    State s = sim.state();
                    if (s == null || !s.antiRoot) return;
                    Object self = p.thisObject;
                    if (!(self instanceof File)) return;
                    String path = ((File) self).getAbsolutePath();
                    if (SUSPECT.matcher(path).matches()) p.setResult(false);
                }
            });
        } catch (Throwable t) {
            log("File.exists hook failed: " + t);
        }
    }

    private static void hookSettings(final Simulator sim) {
        try {
            XposedHelpers.findAndHookMethod(Settings.Secure.class, "getString",
                    ContentResolver.class, String.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            State s = sim.state();
                            if (s == null || !s.antiMock) return;
                            if ("mock_location".equals(p.args[1])) p.setResult("0");
                        }
                    });
        } catch (Throwable t) {
            log("Settings.Secure hook failed: " + t);
        }
    }

    private static void hookRootBeer(ClassLoader cl, final Simulator sim) {
        Class<?> rb = null;
        try {
            rb = XposedHelpers.findClass("com.scottyab.rootbeer.RootBeer", cl);
        } catch (Throwable ignored) {
        }
        if (rb == null) return;
        String[] names = {
                "isRooted", "isRootedWithBusyBoxCheck", "checkForRoot",
                "detectRootManagementApps", "detectPotentiallyDangerousApps",
                "checkForSuBinary", "checkForBusyBoxBinary", "checkForDangerousProps",
                "checkForRWPaths", "checkSuExists", "checkForMagiskBinary"
        };
        for (String n : names) {
            try {
                Hooks.hookAll(rb, n, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        State s = sim.state();
                        if (s == null || !s.antiRoot) return;
                        if (p.method instanceof java.lang.reflect.Method
                                && ((java.lang.reflect.Method) p.method).getReturnType() == boolean.class) {
                            p.setResult(false);
                        }
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private static void hookBuild() {
        try {
            Object tags = XposedHelpers.getStaticObjectField(Build.class, "TAGS");
            if (tags instanceof String && ((String) tags).contains("test-keys")) {
                XposedHelpers.setStaticObjectField(Build.class, "TAGS", "release-keys");
            }
            Object type = XposedHelpers.getStaticObjectField(Build.class, "TYPE");
            if (type instanceof String && !"user".equals(type)) {
                XposedHelpers.setStaticObjectField(Build.class, "TYPE", "user");
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookDebug() {
        try {
            XposedHelpers.findAndHookMethod(android.os.Debug.class, "isDebuggerConnected",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            p.setResult(false);
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private static void log(String msg) {
        try {
            XposedBridge.log(TAG + ": " + msg);
        } catch (Throwable ignored) {
        }
    }
}
