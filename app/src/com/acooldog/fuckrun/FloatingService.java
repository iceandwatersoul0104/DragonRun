package com.acooldog.fuckrun;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.acooldog.fuckrun.data.AppPrefs;
import com.acooldog.fuckrun.sim.SimController;

/** 悬浮开关：拖拽移动，点按切换步频接管。 */
public class FloatingService extends Service {
    private static final int NOTIF_ID = 41;
    private TextView view;
    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private AppPrefs prefs;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new AppPrefs(this);
    }

    private Notification notification() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, App.CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("FuckRun 运行中")
                .setContentText("悬浮开关已启用")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, notification());
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (view == null) showOverlay();
        return START_STICKY;
    }

    private void showOverlay() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        view = new TextView(this);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setPadding(28, 14, 28, 14);
        updateLook();

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 36;
        lp.y = 320;

        view.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - downX);
                        int dy = (int) (e.getRawY() - downY);
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true;
                        lp.x = startX + dx;
                        lp.y = startY + dy;
                        wm.updateViewLayout(view, lp);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) toggle();
                        return true;
                }
                return false;
            }
        });

        try {
            wm.addView(view, lp);
        } catch (Exception e) {
            view = null;
            stopSelf();
        }
    }

    private void toggle() {
        boolean on = !prefs.cadenceOn();
        prefs.setCadenceOn(on);
        SimController.refresh(this);
        updateLook();
        Toast.makeText(this, on ? "步频接管已开启" : "步频接管已关闭", Toast.LENGTH_SHORT).show();
    }

    private void updateLook() {
        if (view == null) return;
        boolean on = prefs.cadenceOn();
        GradientDrawable bg = new GradientDrawable();
        int pc = com.acooldog.fuckrun.util.Palette.of(this).primary;
        bg.setColor(on ? (0xF2000000 | (pc & 0x00FFFFFF)) : 0xF290A4AE);
        bg.setCornerRadius(44);
        view.setBackground(bg);
        view.setText(on ? "步频 ON" : "步频 OFF");
    }

    @Override
    public void onDestroy() {
        if (view != null && wm != null) {
            try {
                wm.removeView(view);
            } catch (Exception ignored) {
            }
            view = null;
        }
        super.onDestroy();
    }
}
