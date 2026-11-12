package com.acooldog.fuckrun;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.acooldog.fuckrun.sim.SimController;

/** 常驻前台服务：保持宿主进程存活，供被注入的目标进程查询模拟状态。 */
public class StateService extends Service {
    public static final int NOTIF_ID = 42;
    public static final String CHANNEL = "fuckrun_state";

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NOTIF_ID, buildNotification());
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (SimController.isRunning()) SimController.refresh(StateService.this);
                } catch (Throwable ignored) {
                }
                h.postDelayed(this, 2000);
            }
        }, 2000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground(NOTIF_ID, buildNotification());
        } catch (Throwable ignored) {
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "模拟状态", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setContentTitle("路线模拟运行中")
                .setContentText("步道乐跑可读取模拟定位")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        try {
            stopForeground(true);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
