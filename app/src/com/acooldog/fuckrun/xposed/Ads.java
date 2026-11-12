package com.acooldog.fuckrun.xposed;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;

/**
 * 去广告：目标 App 使用穿山甲 GroMore 聚合（含 GDT / 百度 / 快手适配器）。
 * 思路是「不展示」，而不是「不加载」：
 *   1) 拦截所有广告 SDK Activity（开屏/激励视频/落地页）；
 *   2) 拦截广告 SDK 的 View 被加入布局（开屏、Banner、信息流、原生模板）；
 *   3) 拦截 Splash 类的 show/showAd/showSplashView 调用。
 * 广告继续在后台下载也无妨，界面干净即可；若目标 App 因缺广告回调卡住，可在设置里关闭本功能。
 */
public final class Ads {
    private static final String TAG = "FuckRun/Ads";

    private static final String[] ROOTS = {
            "com.qq.e.",
            "com.baidu.mobads.",
            "com.kwad.",
            "com.bytedance.sdk.openadsdk.",
            "com.bytedance.msdk.",
            "com.bytedance.gromore.",
            "com.bytedance.pangle.ad.",
            "com.sigmob.",
            "com.mintegral.",
            "com.anythink.",
    };

    private static final Set<String> SEEN = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<Class<?>> HOOKED = Collections.synchronizedSet(new HashSet<Class<?>>());

    private static Simulator sim;
    private static volatile boolean installed;

    private Ads() {
    }

    static void install(ClassLoader cl, Simulator s) {
        if (installed) return;
        installed = true;
        sim = s;

        hookStarts();
        hookViews();
        hookClassLoader();
        hookSplash();
        log("ad block ready");
    }

    private static boolean enabled() {
        Simulator s = sim;
        State st = s == null ? null : s.state();
        return st == null || st.adBlock;
    }

    private static boolean isAdName(String name) {
        if (name == null || name.length() < 8) return false;
        if (name.charAt(0) != 'c') return false;
        for (String r : ROOTS) {
            if (name.startsWith(r)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- activities

    private static void hookStarts() {
        XC_MethodHook cb = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (!enabled() || p.args == null || p.args.length == 0) return;
                Object a0 = p.args[0];
                if (!(a0 instanceof Intent)) return;
                String target = componentOf((Intent) a0);
                if (target != null && isAdName(target)) {
                    p.setResult(null);
                    logOnce("block activity " + target);
                }
            }
        };
        Hooks.hookAll(Activity.class, "startActivity", cb);
        Hooks.hookAll(Activity.class, "startActivityForResult", cb);
        Hooks.hookAll(Activity.class, "startActivityIfNeeded", cb);
        try {
            Class<?> impl = Class.forName("android.app.ContextImpl");
            Hooks.hookAll(impl, "startActivity", cb);
        } catch (Throwable ignored) {
        }
    }

    private static String componentOf(Intent i) {
        try {
            if (i.getComponent() != null) return i.getComponent().getClassName();
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ---------------------------------------------------------------- views

    private static void hookViews() {
        XC_MethodHook cb = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (!enabled() || p.args == null || p.args.length == 0) return;
                Object a0 = p.args[0];
                if (!(a0 instanceof View)) return;
                String cn = a0.getClass().getName();
                if (isAdName(cn)) {
                    p.setResult(null);          // 不加入布局
                    logOnce("block view " + cn);
                }
            }
        };
        Hooks.hookAll(ViewGroup.class, "addView", cb);
        Hooks.hookAll(ViewGroup.class, "addViewInLayout", cb);
    }

    // ---------------------------------------------------------------- ad classes

    private static void hookClassLoader() {
        XC_MethodHook cb = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                Object r = p.getResult();
                if (!(r instanceof Class)) return;
                Class<?> c = (Class<?>) r;
                String n = c.getName();
                if (isAdName(n)) onAdClass(c);
            }
        };
        Hooks.hookAll(ClassLoader.class, "loadClass", cb);
    }

    private static void onAdClass(Class<?> c) {
        if (c == null) return;
        if (!HOOKED.add(c)) return;
        String n = c.getName();
        logOnce("ad class " + n);
        if (n.contains("Splash")) {
            XC_MethodHook hide = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    if (!enabled()) return;
                    p.setResult(null);
                    logOnce("hide splash " + p.method.getName());
                }
            };
            Hooks.hookAll(c, "showAd", hide);
            Hooks.hookAll(c, "showSplashView", hide);
            Hooks.hookAll(c, "showAdView", hide);
        }
    }

    // ---------------------------------------------------------------- splash

    /**
     * 开屏广告的 View 被拦掉后会留下一个空的 splash 容器（白屏 + 倒计时），
     * 体验上像卡死。这里在 SplashActivity 出现后直接切到主页，让用户看不到空白等待。
     */
    private static void hookSplash() {
        XC_MethodHook cb = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                if (!enabled()) return;
                final Object o = p.thisObject;
                if (!(o instanceof Activity)) return;
                final Activity act = (Activity) o;
                final String name = act.getClass().getName();
                if (!name.contains("Splash")) return;
                if (!"com.lptiyu.tanke".equals(act.getPackageName())) return;
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!enabled() || act.isFinishing() || act.isDestroyed()) return;
                            Intent i = new Intent();
                            i.setClassName("com.lptiyu.tanke",
                                    "com.lptiyu.tanke.activities.main.MainActivity");
                            act.startActivity(i);
                            act.finish();
                            logOnce("skip splash -> main");
                        } catch (Throwable t) {
                            log("skip splash failed: " + t);
                        }
                    }
                }, 350);
            }
        };
        Hooks.hookAll(Activity.class, "onCreate", cb);
    }

    // ---------------------------------------------------------------- log

    private static void logOnce(String msg) {
        if (SEEN.add(msg)) log(msg);
    }

    static void log(String msg) {
        try {
            de.robv.android.xposed.XposedBridge.log(TAG + ": " + msg);
        } catch (Throwable ignored) {
        }
    }
}
