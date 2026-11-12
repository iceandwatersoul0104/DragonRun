package com.acooldog.fuckrun.xposed;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/** 通过 ContentProvider 读取宿主应用状态（目标进程无权读宿主私有目录）。 */
public class ProviderClient {
    private static final Uri URI = Uri.parse("content://com.acooldog.fuckrun.state/state");

    private final ContentResolver resolver;
    private volatile State last;
    private volatile long lastErrorAt;

    public ProviderClient(Context ctx) {
        resolver = ctx.getContentResolver();
    }

    public State fetch() {
        Cursor c = null;
        try {
            c = resolver.query(URI, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                String json = c.getString(0);
                if (json != null && !json.isEmpty()) {
                    State s = State.parse(json);
                    last = s;
                    return s;
                }
            }
        } catch (Throwable t) {
            lastErrorAt = System.currentTimeMillis();
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return last;
    }

    public long lastErrorAt() {
        return lastErrorAt;
    }
}
