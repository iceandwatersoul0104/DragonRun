package com.acooldog.fuckrun.xposed;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * 兼容层：部分 LSPosed 构建缺少 XposedHelpers.hookAllMethods，
 * 这里统一退回到稳定的 XposedBridge.hookMethod(Member, callback)。
 */
final class Hooks {
    private Hooks() {
    }

    static int hookAll(Class<?> clazz, String name, XC_MethodHook callback) {
        if (clazz == null || name == null) return 0;
        List<Method> targets = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(name)) targets.add(m);
                }
            } catch (Throwable ignored) {
            }
        }
        int n = 0;
        for (Method m : targets) {
            try {
                m.setAccessible(true);
                XposedBridge.hookMethod(m, callback);
                n++;
            } catch (Throwable ignored) {
                // 重复 hook 或不可 hook 的方法直接跳过
            }
        }
        return n;
    }
}
