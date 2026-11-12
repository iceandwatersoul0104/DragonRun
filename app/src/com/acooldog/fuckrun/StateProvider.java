package com.acooldog.fuckrun;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.SystemClock;

import com.acooldog.fuckrun.data.AppPrefs;
import com.acooldog.fuckrun.sim.SimController;

import org.json.JSONObject;

/** 向 LSPosed 模块暴露运行状态（同 UID 直读 SP 之外的跨进程通道）。 */
public class StateProvider extends ContentProvider {
    public static final String AUTHORITY = "com.acooldog.fuckrun.state";
    private static final String[] COLS = {"json"};

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        String json;
        try {
            String state = SimController.readStateFile(getContext());
            if (state.isEmpty()) {
                AppPrefs p = new AppPrefs(getContext());
                state = p.state();
            }
            if (state.isEmpty()) {
                JSONObject o = new JSONObject();
                o.put("running", false);
                o.put("ts", System.currentTimeMillis());
                json = o.toString();
            } else {
                json = state;
            }
            JSONObject o = new JSONObject(json);
            o.put("providerUptime", SystemClock.elapsedRealtime());
            json = o.toString();
        } catch (Exception e) {
            json = "{\"running\":false}";
        }
        MatrixCursor c = new MatrixCursor(COLS);
        c.addRow(new Object[]{json});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "application/json";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
