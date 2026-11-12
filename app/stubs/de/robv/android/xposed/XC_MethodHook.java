package de.robv.android.xposed;

import java.lang.reflect.Member;

/** Compile-only stub. */
public abstract class XC_MethodHook {
    public XC_MethodHook() {}

    public XC_MethodHook(int priority) {}

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    /** Only present so stubs match framework usage sites. */
    public static class Unhook {
        public void unhook() {}
    }

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        private boolean returnEarly;

        public Object getResult() { return result; }

        public void setResult(Object r) {
            result = r;
            returnEarly = true;
            throwable = null;
        }

        public Throwable getThrowable() { return throwable; }

        public void setThrowable(Throwable t) {
            throwable = t;
            returnEarly = true;
        }

        public boolean hasThrowable() { return throwable != null; }

        public Object getObjectExtra(String key) { return null; }

        public void setObjectExtra(String key, Object o) {}
    }
}
