package de.robv.android.xposed;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/** Compile-only stub; signatures mirror the real XposedHelpers used by this module. */
public final class XposedHelpers {
    private XposedHelpers() {}

    public static Class<?> findClass(String className, ClassLoader loader) { return null; }

    public static Class<?> findClassIfExists(String className, ClassLoader loader) { return null; }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName,
                                                         Object... parameterTypesAndCallback) {
        return null;
    }

    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> clazz, String methodName,
                                                           XC_MethodHook callback) {
        return new HashSet<>();
    }

    public static Field findField(Class<?> clazz, String fieldName) { return null; }

    public static Object getObjectField(Object obj, String fieldName) { return null; }

    public static void setObjectField(Object obj, String fieldName, Object value) {}

    public static boolean getBooleanField(Object obj, String fieldName) { return false; }

    public static void setBooleanField(Object obj, String fieldName, boolean value) {}

    public static int getIntField(Object obj, String fieldName) { return 0; }

    public static void setIntField(Object obj, String fieldName, int value) {}

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) { return null; }

    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {}

    public static Object callMethod(Object obj, String methodName, Object... args) { return null; }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) { return null; }

    public static Object newInstance(Class<?> clazz, Object... args) { return null; }

    public static Object getAdditionalInstanceField(Object obj, String key) { return null; }

    public static Object setAdditionalInstanceField(Object obj, String key, Object value) { return null; }
}
