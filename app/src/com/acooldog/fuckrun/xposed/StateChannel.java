package com.acooldog.fuckrun.xposed;

import android.net.LocalServerSocket;
import android.net.LocalSocket;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import de.robv.android.xposed.XposedBridge;

/**
 * 宿主 → 目标进程的状态通道。
 * 用抽象命名空间的 LocalSocket，绕开 MIUI 对 ContentProvider 跨应用唤醒的拦截。
 */
final class StateChannel {
    static final String NAME = "com.acooldog.fuckrun.state";
    private static volatile boolean started;

    private StateChannel() {
    }

    static void start(final Simulator sim) {
        if (started) return;
        started = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                LocalServerSocket server = null;
                while (true) {
                    try {
                        if (server == null) server = new LocalServerSocket(NAME);
                        LocalSocket s = server.accept();
                        try {
                            String json = read(s);
                            if (json != null && json.length() > 10) {
                                State st = State.parse(json);
                                sim.update(st);
                                log("state pushed running=" + st.running
                                        + " route=" + st.routeId + " pts="
                                        + (st.route == null ? 0 : st.route.points.size()));
                            }
                        } finally {
                            try {
                                s.close();
                            } catch (Throwable ignored) {
                            }
                        }
                    } catch (Throwable e) {
                        log("channel error: " + e);
                        try {
                            if (server != null) server.close();
                        } catch (Throwable ignored) {
                        }
                        server = null;
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            return;
                        }
                    }
                }
            }
        }, "fuckrun-channel");
        t.setDaemon(true);
        t.start();
        log("state channel listening on " + NAME);
    }

    private static String read(LocalSocket s) throws Exception {
        InputStream in = s.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
            if (bo.size() > 1024 * 1024) break;
        }
        return new String(bo.toByteArray(), "UTF-8");
    }

    private static void log(String m) {
        try {
            XposedBridge.log("FuckRun/Channel: " + m);
        } catch (Throwable ignored) {
        }
    }
}
