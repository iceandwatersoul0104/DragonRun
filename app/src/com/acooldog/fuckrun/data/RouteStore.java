package com.acooldog.fuckrun.data;

import android.content.Context;

import com.acooldog.fuckrun.model.Route;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 路线持久化：files/routes/<id>.json。 */
public class RouteStore {
    private static final String DIR = "routes";

    private final File dir;

    public RouteStore(Context ctx) {
        dir = new File(ctx.getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    private File fileOf(String id) {
        return new File(dir, id + ".json");
    }

    public List<Route> list() {
        List<Route> out = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs == null) return out;
        for (File f : fs) {
            if (!f.getName().endsWith(".json")) continue;
            Route r = load(f);
            if (r != null && !r.points.isEmpty()) out.add(r);
        }
        Collections.sort(out, new Comparator<Route>() {
            @Override
            public int compare(Route a, Route b) {
                return Long.compare(b.updatedAt, a.updatedAt);
            }
        });
        return out;
    }

    public Route get(String id) {
        if (id == null || id.isEmpty()) return null;
        return load(fileOf(id));
    }

    private Route load(File f) {
        if (!f.exists()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int read = 0;
            while (read < buf.length) {
                int n = in.read(buf, read, buf.length - read);
                if (n <= 0) break;
                read += n;
            }
            String json = new String(buf, 0, read, StandardCharsets.UTF_8);
            return Route.fromJson(new JSONObject(json));
        } catch (Exception e) {
            return null;
        }
    }

    public boolean save(Route r) {
        if (r == null || r.id == null || r.id.isEmpty()) return false;
        r.updatedAt = System.currentTimeMillis();
        if (r.createdAt == 0) r.createdAt = r.updatedAt;
        try (FileOutputStream fo = new FileOutputStream(fileOf(r.id))) {
            OutputStreamWriter w = new OutputStreamWriter(fo, StandardCharsets.UTF_8);
            w.write(r.toJson().toString());
            w.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void delete(String id) {
        File f = fileOf(id);
        if (f.exists()) f.delete();
    }
}
