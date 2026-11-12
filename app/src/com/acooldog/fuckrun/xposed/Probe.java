package com.acooldog.fuckrun.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 临时诊断：打印 Activity 启动参数与弹窗内容，用于判断版本检查弹窗与
 * MapRunActivity 的运行态参数。安装/关闭都很轻，留作现场排查用。
 */
final class Probe {
    private static final String TAG = "FuckRun/Probe";
    private static boolean installed;

    private Probe() {
    }

    static void install() {
        if (installed) return;
        installed = true;
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Activity a = (Activity) p.thisObject;
                        Intent i = a.getIntent();
                        StringBuilder sb = new StringBuilder("activity ")
                                .append(a.getClass().getName());
                        if (i != null) {
                            sb.append(" action=").append(i.getAction());
                            Bundle ex = i.getExtras();
                            if (ex != null) {
                                sb.append(" extras=");
                                for (String k : ex.keySet()) {
                                    Object v;
                                    try {
                                        v = ex.get(k);
                                    } catch (Throwable t) {
                                        v = "<" + t.getClass().getSimpleName() + ">";
                                    }
                                    sb.append(k).append('=').append(v).append(';');
                                }
                            }
                        }
                        Log.i(TAG, sb.toString());
                    } catch (Throwable t) {
                        Log.e(TAG, "activity probe failed", t);
                    }
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "activity hook failed", t);
        }
        try {
            XposedHelpers.findAndHookMethod(Dialog.class, "show", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Dialog d = (Dialog) p.thisObject;
                        StringBuilder sb = new StringBuilder("dialog ").append(d.getClass().getName());
                        if (d instanceof AlertDialog) {
                            AlertDialog ad = (AlertDialog) d;
                            sb.append(" text=").append(text(ad, android.R.id.message));
                        }
                        Log.i(TAG, sb.toString());
                        final String cn = d.getClass().getName();
                        if (cn.startsWith("com.lptiyu.lp_base.uitls.dialog")
                                || cn.startsWith("com.lptiyu.tanke.widget.dialog")) {
                            StringBuilder st = new StringBuilder("stack:");
                            StackTraceElement[] es = new Throwable().getStackTrace();
                            for (int i = 0; i < es.length && i < 12; i++) {
                                st.append(" <- ").append(es[i].getClassName()).append('#').append(es[i].getMethodName());
                            }
                            Log.i(TAG, st.toString());
                            final String body = texts(d);
                            Log.i(TAG, "text: " + body);
                            final Dialog dd = d;
                            if (body.contains("未完成的跑步记录")) {
                                clickLater(dd, "恢复", 300);
                            } else if (body.contains("发现新版本")) {
                                dismissLater(dd, 800);
                            }
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "dialog probe failed", t);
                    }
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "dialog hook failed", t);
        }
        Log.i(TAG, "probe installed");
        XposedBridge.log("FuckRun/Probe: installed");
    }

    private static void dismissLater(final Dialog d, long delayMs) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    d.dismiss();
                    Log.i(TAG, "dismissed " + d.getClass().getName());
                } catch (Throwable t) {
                    Log.e(TAG, "dismiss failed", t);
                }
            }
        }, delayMs);
    }

    private static void clickLater(final Dialog d, final String label, long delayMs) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean ok = click(d.getWindow() == null ? null : d.getWindow().getDecorView(), label);
                    Log.i(TAG, "click " + label + " -> " + ok);
                } catch (Throwable t) {
                    Log.e(TAG, "click failed", t);
                }
            }
        }, delayMs);
    }

    private static boolean click(android.view.View v, String label) {
        if (v == null) return false;
        if (v instanceof android.widget.TextView) {
            CharSequence cs = ((android.widget.TextView) v).getText();
            if (cs != null && label.contentEquals(cs)) {
                return v.performClick();
            }
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (click(g.getChildAt(i), label)) return true;
            }
        }
        return false;
    }

    private static String texts(Dialog d) {
        try {
            android.view.View root = d.getWindow() == null ? null : d.getWindow().getDecorView();
            StringBuilder sb = new StringBuilder();
            collect(root, sb, 0);
            return sb.toString();
        } catch (Throwable t) {
            return "<" + t + ">";
        }
    }

    private static void collect(android.view.View v, StringBuilder sb, int depth) {
        if (v == null || depth > 20 || sb.length() > 800) return;
        if (v instanceof android.widget.TextView) {
            CharSequence cs = ((android.widget.TextView) v).getText();
            if (cs != null && cs.length() > 0) sb.append('[').append(cs).append(']');
        } else if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), sb, depth + 1);
        }
    }

    private static CharSequence text(AlertDialog ad, int id) {
        try {
            android.view.View v = ad.findViewById(id);
            if (v instanceof android.widget.TextView) return ((android.widget.TextView) v).getText();
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
