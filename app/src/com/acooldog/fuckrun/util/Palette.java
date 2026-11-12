package com.acooldog.fuckrun.util;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;

import com.acooldog.fuckrun.R;

/** 运行时取当前主题的调色板；换主题只需换 Theme（AppTheme / AppTheme.Red）。 */
public final class Palette {
    public final int bg;
    public final int card;
    public final int primary;
    public final int primaryDark;
    public final int text;
    public final int textSub;
    public final int green;
    public final int red;
    public final int line;
    public final int chipBg;
    public final int onPrimary;
    public final boolean dark;

    private Palette(boolean dark, int bg, int card, int primary, int primaryDark, int text,
                    int textSub, int green, int red, int line, int chipBg, int onPrimary) {
        this.dark = dark;
        this.bg = bg;
        this.card = card;
        this.primary = primary;
        this.primaryDark = primaryDark;
        this.text = text;
        this.textSub = textSub;
        this.green = green;
        this.red = red;
        this.line = line;
        this.chipBg = chipBg;
        this.onPrimary = onPrimary;
    }

    public static Palette of(Context c) {
        boolean dark = Theme.isRed(c);
        return new Palette(dark,
                color(c, R.attr.bg, dark ? 0xFF120A0C : 0xFFF4F6FA),
                color(c, R.attr.card, dark ? 0xFF1E1114 : 0xFFFFFFFF),
                color(c, R.attr.primary, dark ? 0xFFE23A2E : 0xFF2F6FED),
                color(c, R.attr.primary_dark, dark ? 0xFF8E1B13 : 0xFF1F54C0),
                color(c, R.attr.text, dark ? 0xFFF5E9EA : 0xFF1B2333),
                color(c, R.attr.text_sub, dark ? 0xFFB08A8E : 0xFF6B7485),
                color(c, R.attr.green, dark ? 0xFF3DDC84 : 0xFF18A058),
                color(c, R.attr.red, dark ? 0xFFFF4D4D : 0xFFD03050),
                color(c, R.attr.line, dark ? 0xFF3A2226 : 0xFFE4E8F0),
                color(c, R.attr.chip_bg, dark ? 0xFF33161A : 0xFFEAF1FF),
                color(c, R.attr.on_primary, 0xFFFFFFFF));
    }

    /** 语义色：开启 / 关闭 / 危险。 */
    public int state(boolean enabled) {
        return enabled ? green : textSub;
    }

    private static int color(Context c, int attr, int fallback) {
        try {
            TypedValue tv = new TypedValue();
            if (c.getTheme().resolveAttribute(attr, tv, true)) {
                if (tv.resourceId != 0) {
                    Resources r = c.getResources();
                    return r.getColor(tv.resourceId, c.getTheme());
                }
                return tv.data;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }
}
