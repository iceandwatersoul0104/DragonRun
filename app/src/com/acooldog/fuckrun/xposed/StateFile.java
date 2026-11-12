package com.acooldog.fuckrun.xposed;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

import de.robv.android.xposed.XposedBridge;

/**
 * 直接读宿主 App 私有目录下的 state.json。
 * 宿主把该文件设为 other-readable（0644），本模块运行在目标进程里也能读到（LSPosed 下同机 app_data_file 可读）。
 * 不用 Provider / 广播 / LocalSocket：MIUI 拦截跨应用唤醒，SELinux 拦截 cross-uid abstract socket。
 */
final class StateFile {
    private static final String[] CANDIDATES = new String[]{
            "/storage/emulated/0/Android/media/com.acooldog.fuckrun/state.json",
            "/sdcard/Android/media/com.acooldog.fuckrun/state.json",
            "/data/data/com.acooldog.fuckrun/files/state.json",
            "/data/user/0/com.acooldog.fuckrun/files/state.json"
    };
    private static volatile boolean started;
    private static String lastRaw;

    private StateFile() {
    }

    static void start(final Simulator sim) {
        if (started) return;
        started = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        String raw = readAny();
                        if (raw != null && raw.length() > 10 && !raw.equals(lastRaw)) {
                            lastRaw = raw;
                            State st = State.parse(raw);
                            sim.update(st);
                            log("state updated running=" + st.running
                                    + " route=" + st.routeId + " pts="
                                    + (st.route == null ? 0 : st.route.points.size()));
                        }
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        return;
                    } catch (Throwable e) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            return;
                        }
                    }
                }
            }
        }, "fuckrun-state-file");
        t.setDaemon(true);
        t.start();
        log("watching host state.json");
    }

    private static String readAny() {
        for (String p : CANDIDATES) {
            String s = read(new File(p));
            if (s != null && s.length() > 10) return s;
        }
        return null;
    }

    private static String read(File f) {
        try {
            if (!f.exists()) {
                warnOnce("missing " + f);
                return null;
            }
            if (!f.canRead()) {
                warnOnce("cannot read " + f);
                return null;
            }
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            warnOnce("read " + f + " failed: " + t);
            return null;
        }
    }

    private static volatile boolean warned;

    private static void warnOnce(String m) {
        if (warned) return;
        warned = true;
        log(m);
    }

    private static void log(String m) {
        try {
            XposedBridge.log("FuckRun/File: " + m);
        } catch (Throwable ignored) {
        }
    }
}
