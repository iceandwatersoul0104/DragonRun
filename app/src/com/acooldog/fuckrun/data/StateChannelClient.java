package com.acooldog.fuckrun.data;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.OutputStream;

/**
 * 把运行状态推给注入在步道乐跑里的模块。
 * MIUI 会拦截「目标应用 → 本应用」的 Provider/广播唤醒，所以改用本机 LocalSocket。
 */
public final class StateChannelClient {
    public static final String NAME = "com.acooldog.fuckrun.state";

    private static final Object LOCK = new Object();
    private static volatile String pending;
    private static boolean worker;

    private StateChannelClient() {
    }

    public static void push(String json) {
        if (json == null) return;
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
                        send(j);
                        try {
                            Thread.sleep(120);
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
        }, "fuckrun-push");
        t.setDaemon(true);
        t.start();
    }

    private static void send(String json) {
        LocalSocket s = null;
        try {
            s = new LocalSocket();
            s.connect(new LocalSocketAddress(NAME, LocalSocketAddress.Namespace.ABSTRACT));
            OutputStream out = s.getOutputStream();
            out.write(json.getBytes("UTF-8"));
            out.flush();
            s.shutdownOutput();
            android.util.Log.i("FuckRun/Push", "pushed " + json.length() + " bytes");
        } catch (Throwable t) {
            android.util.Log.e("FuckRun/Push", "push failed: " + t);
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
