package com.acooldog.fuckrun.data;

import android.content.Context;
import android.util.Log;

import java.io.File;

/**
 * 用 root 把 state.json 同步进步道乐跑自己的私有目录，供注入模块读取。
 * MIUI 拦截跨应用 Provider/广播唤醒，SELinux 又拦截跨 uid 的 LocalSocket，
 * root 文件同步是这台机器上唯一稳定的通道。
 */
public final class RootSync {
    public static final String DEST_DIR = "/data/data/com.lptiyu.tanke/files";
    public static final String DEST_FILE = DEST_DIR + "/fuckrun_state.json";

    private static final Object LOCK = new Object();
    private static volatile String pending;
    private static boolean worker;

    private RootSync() {
    }

    public static void push(Context ctx, final String json) {
        if (json == null) return;
        final String src = new File(ctx.getFilesDir(), "state.json").getAbsolutePath();
        pending = json;
        synchronized (LOCK) {
            if (worker) return;
            worker = true;
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        String j = pending;
                        if (j == null) break;
                        pending = null;
                        exec(src);
                        try {
                            Thread.sleep(400);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                } finally {
                    synchronized (LOCK) {
                        worker = false;
                    }
                }
            }
        }, "fuckrun-rootsync");
        t.setDaemon(true);
        t.start();
    }

    private static void exec(String src) {
        String cmd = "cp " + src + " " + DEST_FILE
                + " && chown $(stat -c %u /data/data/com.lptiyu.tanke):$(stat -c %g /data/data/com.lptiyu.tanke) " + DEST_FILE
                + " && chmod 600 " + DEST_FILE;
        String[] candidates = new String[]{"su", "/debug_ramdisk/su", "/system/bin/su", "/system/xbin/su"};
        String last = "";
        for (String su : candidates) {
            Process p = null;
            try {
                p = Runtime.getRuntime().exec(new String[]{su, "-c", cmd});
                int code = p.waitFor();
                if (code == 0) {
                    Log.i("FuckRun/Root", "state synced to target via " + su);
                    return;
                }
                last = su + " exit " + code;
            } catch (Throwable t) {
                last = su + ": " + t;
            } finally {
                if (p != null) {
                    try {
                        p.destroy();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        Log.e("FuckRun/Root", "sync failed: " + last);
    }
}
