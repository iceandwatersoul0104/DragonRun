package com.acooldog.fuckrun.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.acooldog.fuckrun.util.Palette;

/**
 * 龙与火焰的绘制原语。局部坐标：龙头在原点、朝向 +X，龙身向 -X 拖尾，火焰从嘴前喷出。
 * 所有尺寸以“设计单位”计，调用方先 scale。
 */
public final class DragonArt {

    private DragonArt() {
    }

    public static int mix(int a, int b, float t) {
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return Color.rgb((int) (ar + (br - ar) * t), (int) (ag + (bg - ag) * t), (int) (ab + (bb - ab) * t));
    }

    public static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    /** 火焰三色：外焰 / 中焰 / 内焰。 */
    public static int[] flame(Palette p) {
        if (p.dark) {
            return new int[]{0xFFFF4D2E, 0xFFFF8A2A, 0xFFFFD36A};
        }
        return new int[]{0xFF3FA9FF, 0xFF8FE4FF, 0xFFFFFFFF};
    }

    /**
     * 画一条“飞龙”。head 已由 canvas 平移/旋转定位。
     *
     * @param s      设计单位缩放
     * @param p      调色板
     * @param phase  0..1 动画相位
     * @param breath 0..1 喷焰强度
     */
    public static void drawFlying(Canvas c, float s, Palette p, float phase, float breath) {
        int body = p.primary;
        int deep = mix(body, Color.BLACK, 0.42f);
        int dark = mix(body, Color.BLACK, 0.62f);
        int belly = mix(body, Color.WHITE, 0.55f);
        int[] fl = flame(p);

        final int n = 34;
        float[] xs = new float[n + 1];
        float[] ys = new float[n + 1];
        float[] rs = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            xs[i] = -u * 88f * s;
            ys[i] = (float) (Math.sin(u * 4.1f) * 12f * (0.32f + u * 0.85f)) * s;
            rs[i] = (13.2f * (1f - u) * (1f - u) + 2.0f) * s;
        }

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);

        // 身体：上下两条采样曲线围成的带状面
        Path bodyPath = new Path();
        bodyPath.moveTo(xs[0], ys[0] - rs[0]);
        for (int i = 1; i <= n; i++) {
            bodyPath.lineTo(xs[i], ys[i] - rs[i]);
        }
        for (int i = n; i >= 0; i--) {
            bodyPath.lineTo(xs[i], ys[i] + rs[i]);
        }
        bodyPath.close();
        paint.setColor(body);
        c.drawPath(bodyPath, paint);

        // 背脊暗边 + 腹甲亮边
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeCap(Paint.Cap.ROUND);
        edge.setStrokeWidth(Math.max(1f, 1.6f * s));
        edge.setColor(deep);
        Path spine = new Path();
        spine.moveTo(xs[0], ys[0] - rs[0]);
        for (int i = 1; i <= n; i++) spine.lineTo(xs[i], ys[i] - rs[i]);
        c.drawPath(spine, edge);

        Paint bellyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bellyPaint.setStyle(Paint.Style.FILL);
        bellyPaint.setColor(alpha(belly, 190));
        for (int i = 4; i <= n - 2; i++) {
            c.drawCircle(xs[i], ys[i] + rs[i] * 0.42f, rs[i] * 0.46f, bellyPaint);
        }

        // 背鳍
        Paint fin = new Paint(Paint.ANTI_ALIAS_FLAG);
        fin.setColor(deep);
        for (int i = 3; i < n - 3; i += 3) {
            float h = (7f + 6f * (float) Math.sin(i * 0.7f)) * s;
            Path t = new Path();
            t.moveTo(xs[i] - 3.5f * s, ys[i] - rs[i] * 0.9f);
            t.lineTo(xs[i] + 1.5f * s, ys[i] - rs[i] * 0.9f);
            t.lineTo(xs[i] - 5.0f * s, ys[i] - rs[i] * 0.9f - h);
            t.close();
            c.drawPath(t, fin);
        }

        // 尾鳍
        Path tail = new Path();
        tail.moveTo(xs[n], ys[n]);
        tail.lineTo(xs[n] - 16f * s, ys[n] - 18f * s);
        tail.lineTo(xs[n] - 12f * s, ys[n] + 2f * s);
        tail.lineTo(xs[n] - 20f * s, ys[n] + 14f * s);
        tail.close();
        fin.setColor(mix(body, Color.WHITE, 0.12f));
        c.drawPath(tail, fin);

        // 两条后爪
        drawClaw(c, xs[16], ys[16] + rs[16] * 0.65f, s, body, deep, -1);
        drawClaw(c, xs[24], ys[24] + rs[24] * 0.55f, s, body, deep, 1);

        // 前肢
        drawClaw(c, -13f * s, 4f * s, s, body, deep, 1);

        // 头
        drawHead(c, s, body, deep, dark, belly, phase);

        // 火焰
        drawBreath(c, s, fl, phase, breath);
    }

    private static void drawClaw(Canvas c, float x, float y, float s, int body, int deep, int dir) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(body);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4.2f * s);
        Path arm = new Path();
        arm.moveTo(x, y);
        arm.lineTo(x + 6f * s, y + 8f * s * dir);
        arm.lineTo(x + 12f * s, y + 7f * s * dir);
        c.drawPath(arm, p);
        p.setColor(deep);
        p.setStrokeWidth(1.8f * s);
        for (int i = -1; i <= 1; i++) {
            Path k = new Path();
            k.moveTo(x + 12f * s, y + 7f * s * dir);
            k.lineTo(x + 16f * s + i * 2.5f * s, y + (11f + i * 2f) * s * dir);
            c.drawPath(k, p);
        }
    }

    private static void drawHead(Canvas c, float s, int body, int deep, int dark, int belly, float phase) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(body);
        c.drawCircle(0, 0, 14f * s, p);

        // 吻部
        RectF snout = new RectF(4f * s, -8f * s, 24f * s, 8f * s);
        p.setColor(mix(body, Color.WHITE, 0.10f));
        c.drawRoundRect(snout, 7f * s, 7f * s, p);

        // 下颚阴影
        p.setColor(deep);
        c.drawRoundRect(new RectF(4f * s, 4f * s, 22f * s, 9f * s), 3f * s, 3f * s, p);

        // 双角
        p.setColor(dark);
        Path horn = new Path();
        horn.moveTo(-2f * s, -11f * s);
        horn.lineTo(-14f * s, -30f * s);
        horn.lineTo(-16.5f * s, -12f * s);
        horn.close();
        c.drawPath(horn, p);
        Path horn2 = new Path();
        horn2.moveTo(-10f * s, -9f * s);
        horn2.lineTo(-24f * s, -22f * s);
        horn2.lineTo(-22f * s, -6f * s);
        horn2.close();
        c.drawPath(horn2, p);

        // 耳鳍
        p.setColor(alpha(mix(body, Color.WHITE, 0.2f), 220));
        Path ear = new Path();
        ear.moveTo(-11f * s, -2f * s);
        ear.lineTo(-22f * s, -6f * s);
        ear.lineTo(-13f * s, 5f * s);
        ear.close();
        c.drawPath(ear, p);

        // 眼
        p.setColor(0xFFFFF4E4);
        c.drawCircle(6f * s, -3.4f * s, 3.6f * s, p);
        p.setColor(dark);
        c.drawCircle(6.8f * s, -3.2f * s, 1.7f * s, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(7.4f * s, -3.9f * s, 0.7f * s, p);

        // 眉骨
        p.setColor(dark);
        p.setStrokeWidth(1.5f * s);
        p.setStyle(Paint.Style.STROKE);
        c.drawLine(2f * s, -8f * s, 11f * s, -7f * s, p);

        // 鼻孔
        p.setColor(dark);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(19f * s, -2f * s, 1.3f * s, p);

        // 龙须
        p.setColor(alpha(belly, 200));
        p.setStrokeWidth(1.4f * s);
        p.setStyle(Paint.Style.STROKE);
        Path w1 = new Path();
        w1.moveTo(20f * s, 1.5f * s);
        w1.quadTo(30f * s, 4f * s, 34f * s, 12f * s);
        c.drawPath(w1, p);
        Path w2 = new Path();
        w2.moveTo(19f * s, -6f * s);
        w2.quadTo(28f * s, -12f * s, 30f * s, -20f * s);
        c.drawPath(w2, p);

        // 齿
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFF4E4);
        for (int i = 0; i < 3; i++) {
            float x = (7f + i * 4.6f) * s;
            Path tooth = new Path();
            tooth.moveTo(x, 5.2f * s);
            tooth.lineTo(x + 1.6f * s, 9.4f * s);
            tooth.lineTo(x + 3.4f * s, 5.2f * s);
            tooth.close();
            c.drawPath(tooth, p);
        }
    }

    private static void drawBreath(Canvas c, float s, int[] fl, float phase, float breath) {
        if (breath <= 0.01f) return;
        float len = (26f + 46f * breath) * s * (0.86f + 0.14f * (float) Math.sin(phase * 12.566f));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path outer = new Path();
        outer.moveTo(22f * s, -4f * s);
        outer.cubicTo(30f * s, -14f * s, (22f + len * 0.6f) * s, -16f * s, (22f + len) * s, -1f * s);
        outer.cubicTo(22f * s + len * 0.7f, 12f * s, 30f * s, 6f * s, 22f * s, 4f * s);
        outer.close();
        p.setColor(alpha(fl[0], 210));
        c.drawPath(outer, p);

        Path mid = new Path();
        mid.moveTo(24f * s, -2.4f * s);
        mid.cubicTo(32f * s, -9f * s, (26f + len * 0.55f) * s, -10f * s, (24f + len * 0.72f) * s, 0f);
        mid.cubicTo(30f * s, 8f * s, 32f * s, 4f * s, 24f * s, 3f * s);
        mid.close();
        p.setColor(alpha(fl[1], 235));
        c.drawPath(mid, p);

        Path inner = new Path();
        inner.moveTo(25f * s, -1f * s);
        inner.cubicTo(30f * s, -4.5f * s, (28f + len * 0.42f) * s, -4.5f * s, (25f + len * 0.42f) * s, 0.6f * s);
        inner.cubicTo(30f * s, 4f * s, 30f * s, 2.4f * s, 25f * s, 1.6f * s);
        inner.close();
        p.setColor(alpha(fl[2], 245));
        c.drawPath(inner, p);
    }

    /** 左上角常驻龙：适配给定画布，头朝右上，喷焰向右。 */
    public static void drawLogo(Canvas c, float w, float h, Palette p, float phase) {
        float s = Math.min(w, h) / 115f;
        float breath = 0.55f + 0.45f * (float) Math.sin(phase * Math.PI * 2f);
        float bob = (float) Math.sin(phase * Math.PI * 2f) * 2.2f * s;

        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setColor(alpha(p.primary, 26));
        c.drawCircle(w * 0.5f, h * 0.5f, Math.min(w, h) * 0.48f, glow);
        glow.setColor(alpha(p.primary, 16));
        c.drawCircle(w * 0.5f, h * 0.5f, Math.min(w, h) * 0.40f, glow);

        c.save();
        c.translate(w * 0.70f, h * 0.55f + bob);
        c.rotate(-12f);
        c.scale(s * 0.92f, s * 0.92f);
        drawFlying(c, 1f, p, phase, breath);
        c.restore();
    }
}
