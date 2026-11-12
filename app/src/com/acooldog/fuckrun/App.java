package com.acooldog.fuckrun;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.acooldog.fuckrun.sim.SimController;

public class App extends Application {
    public static final String CHANNEL_ID = "fuckrun_status";

    private static App instance;
    private static long hookSeenAt;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "模拟状态", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        SimController.restore(this);
        SimController.refresh(this);
        SimController.ensureService(this);
    }

    public static App get() {
        return instance;
    }

    public static void markHookSeen() {
        hookSeenAt = System.currentTimeMillis();
    }

    public static long hookSeenAt() {
        return hookSeenAt;
    }
}
