package com.acooldog.fuckrun.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.acooldog.fuckrun.util.Palette;

import java.util.Random;

/**
 * 全屏环境粒子：红黑主题下是缓缓上升的红色火星，蓝白主题下是飘落的雪花。
 * 作为内容层最底部的一层绘制，不拦截触摸。
 */
public class AmbientView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final ValueAnimator anim;
    private final float[] x = new float[64];
    private final float[] y = new float[64];
    private final float[] v = new float[64];
    private final float[] r = new float[64];
    private final float[] ph = new float[64];
    private Palette palette;
    private long lastNano;
    private float time;
    private int count;

    public AmbientView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setClickable(false);
        setFocusable(false);
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(6000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> invalidate());
    }

    private boolean red() {
        if (palette == null) palette = Palette.of(getContext());
        return palette.dark;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        palette = Palette.of(getContext());
        initParticles();
        if (!anim.isStarted()) anim.start();
        lastNano = System.nanoTime();
    }

    @Override
    protected void onDetachedFromWindow() {
        anim.cancel();
        super.onDetachedFromWindow();
    }

    private void initParticles() {
        boolean ember = red();
        count = ember ? 20 : 38;
        for (int i = 0; i < 64; i++) {
            x[i] = rnd.nextFloat();
            y[i] = rnd.nextFloat();
            ph[i] = rnd.nextFloat() * 6.283f;
            if (ember) {
                v[i] = 0.010f + rnd.nextFloat() * 0.022f;      // 屏高/秒，向上
                r[i] = 1.1f + rnd.nextFloat() * 2.0f;
            } else {
                v[i] = 0.020f + rnd.nextFloat() * 0.035f;      // 向下
                r[i] = 1.4f + rnd.nextFloat() * 2.6f;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNano) / 1e9f);
        lastNano = now;
        time += dt;

        boolean ember = red();
        float d = getResources().getDisplayMetrics().density;

        for (int i = 0; i < count; i++) {
            if (ember) {
                y[i] -= v[i] * dt;
                if (y[i] < -0.05f) {
                    y[i] = 1.05f;
                    x[i] = rnd.nextFloat();
                    ph[i] = rnd.nextFloat() * 6.283f;
                }
                float sway = (float) Math.sin(time * 0.9f + ph[i]) * 0.010f;
                float sx = (x[i] + sway) * w;
                float sy = y[i] * h;
                float rad = r[i] * d;
                int a = (int) (90 + 90 * (0.5f + 0.5f * Math.sin(time * 2.4f + ph[i])));
                int col = (i % 5 == 0) ? 0xFFFFB35A : 0xFFFF5A2E;
                paint.setShader(new RadialGradient(sx, sy, rad * 3.2f,
                        new int[]{DragonArt.alpha(col, Math.min(220, a + 40)),
                                DragonArt.alpha(col, a / 2), 0x00000000},
                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawCircle(sx, sy, rad * 3.2f, paint);
                paint.setShader(null);
            } else {
                y[i] += v[i] * dt;
                if (y[i] > 1.05f) {
                    y[i] = -0.05f;
                    x[i] = rnd.nextFloat();
                    ph[i] = rnd.nextFloat() * 6.283f;
                }
                float sway = (float) Math.sin(time * 0.7f + ph[i]) * 0.016f;
                float sx = (x[i] + sway) * w;
                float sy = y[i] * h;
                float rad = r[i] * d;
                int a = 150 + (int) (70 * (0.5f + 0.5f * Math.sin(time + ph[i])));
                int col = (i % 7 == 0) ? 0xFFFFFFFF : 0xFF9CC4F0;
                paint.setShader(new RadialGradient(sx, sy, rad * 1.9f,
                        new int[]{DragonArt.alpha(col, a), DragonArt.alpha(col, a / 2), 0x00000000},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawCircle(sx, sy, rad * 1.9f, paint);
                paint.setShader(null);
                paint.setColor(DragonArt.alpha(col, Math.min(235, a + 30)));
                canvas.drawCircle(sx, sy, rad * 0.55f, paint);
            }
        }
        postInvalidateOnAnimation();
    }
}
