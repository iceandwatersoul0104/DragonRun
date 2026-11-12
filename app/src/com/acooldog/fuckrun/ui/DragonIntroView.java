package com.acooldog.fuckrun.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.acooldog.fuckrun.util.Palette;

import java.util.Random;

/**
 * 启动入场动画：一条龙从屏幕上方俯冲下来，横掠喷焰，再转弯落到左上角。
 * 播放结束后回调，由宿主把常驻龙标显示出来。
 */
public class DragonIntroView extends View {

    public interface OnEnd {
        void onEnd();
    }

    private static final int MAX_P = 180;

    private final Path path = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] pos = new float[2];
    private final float[] tan = new float[2];
    private final Random rnd = new Random();

    private final float[] px = new float[MAX_P];
    private final float[] py = new float[MAX_P];
    private final float[] pvx = new float[MAX_P];
    private final float[] pvy = new float[MAX_P];
    private final float[] plife = new float[MAX_P];
    private final float[] pmax = new float[MAX_P];
    private final float[] psize = new float[MAX_P];
    private final int[] pcol = new int[MAX_P];

    private Palette palette;
    private ValueAnimator anim;
    private float progress;
    private long lastNano;
    private float len;
    private float sBase;
    private float endX, endY;
    private float dp = 1f;
    private OnEnd onEnd;
    private int spawnCarry;

    public DragonIntroView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        dp = c.getResources().getDisplayMetrics().density;
    }

    public void play(OnEnd cb) {
        this.onEnd = cb;
        palette = Palette.of(getContext());
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(3000);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.addUpdateListener(v -> {
            progress = (float) v.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (onEnd != null) onEnd.onEnd();
                if (getParent() instanceof android.view.ViewGroup) {
                    ((android.view.ViewGroup) getParent()).removeView(DragonIntroView.this);
                }
            }
        });
        setClickable(true);
        setOnClickListener(v -> {
            if (anim != null && anim.isRunning()) anim.cancel();
        });
        anim.start();
        lastNano = System.nanoTime();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        buildPath(w, h);
    }

    private void buildPath(int w, int h) {
        path.reset();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        sBase = Math.max(1.6f, dm.widthPixels / 320f);

        float sx = w * 0.64f;
        float sy = -h * 0.22f;
        float mx = w * 0.68f;
        float my = h * 0.52f;
        endX = 62f * dp;
        endY = 46f * dp;

        path.moveTo(sx, sy);
        // 俯冲 + 横掠
        path.cubicTo(w * 0.14f, h * 0.18f, w * 0.26f, h * 0.60f, mx, my);
        // 转弯回到左上角
        path.cubicTo(w * 1.06f, h * 0.34f, w * 0.52f, h * 0.02f, endX, endY);

        measure.setPath(path, false);
        len = measure.getLength();
    }

    private float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private void spawn(float x, float y, float dirX, float dirY, float spd, float life, float size, int color) {
        for (int i = 0; i < MAX_P; i++) {
            if (plife[i] <= 0) {
                px[i] = x;
                py[i] = y;
                float a = (float) (Math.atan2(dirY, dirX) + (rnd.nextFloat() - 0.5) * 0.55f);
                float v = spd * (0.55f + rnd.nextFloat() * 0.9f);
                pvx[i] = (float) Math.cos(a) * v;
                pvy[i] = (float) Math.sin(a) * v;
                pmax[i] = life;
                plife[i] = life;
                psize[i] = size * (0.6f + rnd.nextFloat());
                pcol[i] = color;
                return;
            }
        }
    }

    private void stepParticles(float dt) {
        for (int i = 0; i < MAX_P; i++) {
            if (plife[i] <= 0) continue;
            plife[i] -= dt;
            px[i] += pvx[i] * dt;
            py[i] += pvy[i] * dt;
            pvx[i] *= (1f - 1.6f * dt);
            pvy[i] = pvy[i] * (1f - 1.2f * dt) + 60f * dp * dt;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || len <= 0) return;
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNano) / 1e9f);
        lastNano = now;

        float t = Math.min(1f, Math.max(0f, progress));
        float ease = easeOutCubic(t);
        float dist = ease * len;
        measure.getPosTan(dist, pos, tan);
        float angle = (float) Math.toDegrees(Math.atan2(tan[1], tan[0]));

        // 尺寸：起步略大，中段全尺寸，落点缩到左上角龙标大小
        float land = t < 0.72f ? 0f : (t - 0.72f) / 0.28f;
        land = land * land * (3f - 2f * land);
        float s = sBase * (1.06f - 0.08f * Math.min(1f, t / 0.5f) - 0.43f * land);

        // 喷焰强度：入场后到落点前
        float breath = 0f;
        if (t > 0.16f && t < 0.80f) {
            float a = (t - 0.16f) / 0.64f;
            breath = (float) Math.sin(a * Math.PI);
            breath = Math.max(0.12f, breath);
        }

        // 从嘴里喷粒子
        if (breath > 0.05f) {
            float mouthA = (float) Math.toRadians(angle);
            float mx = pos[0] + (float) Math.cos(mouthA) * 20f * s;
            float my = pos[1] + (float) Math.sin(mouthA) * 20f * s;
            int[] fl = DragonArt.flame(palette);
            spawnCarry += breath * 5f;
            while (spawnCarry >= 1f) {
                spawnCarry -= 1f;
                int col = rnd.nextInt(3) == 0 ? fl[2] : (rnd.nextBoolean() ? fl[1] : fl[0]);
                spawn(mx, my, (float) Math.cos(mouthA), (float) Math.sin(mouthA),
                        (180f + 260f * breath) * dp, 0.45f + rnd.nextFloat() * 0.45f,
                        (2.4f + 4.6f * breath) * dp, col);
            }
        }
        stepParticles(dt);

        // 粒子
        for (int i = 0; i < MAX_P; i++) {
            if (plife[i] <= 0) continue;
            float k = plife[i] / pmax[i];
            int a = (int) (200 * k);
            int r = Math.max(1, (int) (psize[i] * (0.4f + k * 0.9f)));
            paint.setShader(new RadialGradient(px[i], py[i], r,
                    new int[]{DragonArt.alpha(pcol[i], Math.min(230, a + 40)),
                            DragonArt.alpha(pcol[i], a / 2), 0x00000000},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(px[i], py[i], r, paint);
            paint.setShader(null);
        }

        // 龙本体
        canvas.save();
        canvas.translate(pos[0], pos[1]);
        canvas.rotate(angle);
        int alpha = 255;
        if (t < 0.10f) alpha = (int) (255 * (t / 0.10f));
        if (t > 0.88f) alpha = (int) (255 * (1f - (t - 0.88f) / 0.12f));
        int save = canvas.saveLayerAlpha(-400 * s, -400 * s, 400 * s, 400 * s, Math.max(0, Math.min(255, alpha)));
        canvas.scale(s, s);
        DragonArt.drawFlying(canvas, 1f, palette, t, breath);
        canvas.restoreToCount(save);
        canvas.restore();

        postInvalidateOnAnimation();
    }
}
