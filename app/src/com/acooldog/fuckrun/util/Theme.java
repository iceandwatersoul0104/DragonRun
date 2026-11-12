package com.acooldog.fuckrun.util;

import android.app.Activity;
import android.content.Context;

import com.acooldog.fuckrun.R;
import com.acooldog.fuckrun.data.AppPrefs;

/** 主题选择：蓝白（默认）/ 红黑。换主题要在 setContentView 之前 setTheme。 */
public final class Theme {
    public static final String BLUE = "blue";
    public static final String RED = "red";

    private Theme() {
    }

    public static boolean isRed(Context c) {
        return RED.equals(new AppPrefs(c).theme());
    }

    public static void apply(Activity a) {
        a.setTheme(isRed(a) ? R.style.AppTheme_Red : R.style.AppTheme);
    }
}
