package com.acooldog.fuckrun.util;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import com.acooldog.fuckrun.ui.AmbientView;
import com.acooldog.fuckrun.ui.DragonIntroView;

/** 页面装饰：环境粒子层与冷启动飞龙入场。 */
public final class Ui {

    private static final String TAG_AMBIENT = "dragonrun-ambient";
    private static final String TAG_INTRO = "dragonrun-intro";
    private static boolean introPlayed;

    private Ui() {
    }

    private static FrameLayout content(Activity a) {
        View v = a.findViewById(android.R.id.content);
        return v instanceof FrameLayout ? (FrameLayout) v : null;
    }

    /** 在内容层最底部插入环境粒子（不拦截触摸）。 */
    public static void ambient(Activity a) {
        FrameLayout root = content(a);
        if (root == null) return;
        if (root.findViewWithTag(TAG_AMBIENT) != null) return;
        AmbientView v = new AmbientView(a);
        v.setTag(TAG_AMBIENT);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(v, 0, lp);
    }

    /**
     * 冷启动入场：龙从上方俯冲、横掠喷焰，最后落到左上角。
     * 播放期间隐藏常驻龙标，结束后弹入。
     */
    public static void playIntro(Activity a, View logo, boolean play) {
        if (!play || introPlayed) {
            if (logo != null) logo.setAlpha(1f);
            return;
        }
        final FrameLayout root = content(a);
        if (root == null) {
            if (logo != null) logo.setAlpha(1f);
            return;
        }
        introPlayed = true;
        if (logo != null) logo.setAlpha(0f);

        DragonIntroView intro = new DragonIntroView(a);
        intro.setTag(TAG_INTRO);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(intro, lp);
        intro.play(() -> {
            if (logo == null) return;
            logo.setAlpha(0f);
            logo.setScaleX(0.55f);
            logo.setScaleY(0.55f);
            logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(420)
                    .setInterpolator(new OvershootInterpolator(1.4f))
                    .start();
        });
    }
}
