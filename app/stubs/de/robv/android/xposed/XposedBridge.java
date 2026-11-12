package de.robv.android.xposed;

/** Compile-only stub. */
public final class XposedBridge {
    private XposedBridge() {}

    public static void log(String text) {}

    public static void log(Throwable t) {}

    public static XC_MethodHook.Unhook hookMethod(java.lang.reflect.Member m, XC_MethodHook cb) {
        return null;
    }
}
