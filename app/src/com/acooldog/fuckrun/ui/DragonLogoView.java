package com.acooldog.fuckrun.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.acooldog.fuckrun.util.Palette;

/** 左上角常驻飞龙：呼吸、浮动、喷焰。 */
public class DragonLogoView extends View {

    private final ValueAnimator anim;
    private float phase;
    private Palette palette;

    public DragonLogoView(Context c) {
        this(c, null);
    }

    public DragonLogoView(Context c, AttributeSet a) {
        super(c, a);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(2200);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(v -> {
            phase = (float) v.getAnimatedValue();
            invalidate();
        });
    }

    private Palette palette() {
        if (palette == null) palette = Palette.of(getContext());
        return palette;
    }

    public void refreshPalette() {
        palette = null;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        palette = Palette.of(getContext());
        if (!anim.isStarted()) anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        DragonArt.drawLogo(canvas, w, h, palette(), phase);
    }
}
