package com.acooldog.fuckrun.model;

import com.acooldog.fuckrun.util.Geo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 一条路线。 */
public class Route {
    public String id = "";
    public String name = "未命名路线";
    public long createdAt;
    public long updatedAt;
    public final List<RoutePoint> points = new ArrayList<>();

    public double distance() {
        double d = 0;
        for (int i = 1; i < points.size(); i++) {
            RoutePoint a = points.get(i - 1), b = points.get(i);
            d += Geo.haversine(a.lat, a.lng, b.lat, b.lng);
        }
        return d;
    }

    public double durationAt(double speedMps) {
        return speedMps > 0.1 ? distance() / speedMps : 0;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("createdAt", createdAt);
        o.put("updatedAt", updatedAt);
        JSONArray arr = new JSONArray();
        for (RoutePoint p : points) arr.put(p.toJson());
        o.put("points", arr);
        return o;
    }

    public static Route fromJson(JSONObject o) {
        Route r = new Route();
        r.id = o.optString("id", "");
        r.name = o.optString("name", "未命名路线");
        r.createdAt = o.optLong("createdAt", 0);
        r.updatedAt = o.optLong("updatedAt", 0);
        JSONArray arr = o.optJSONArray("points");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject po = arr.optJSONObject(i);
                if (po != null) r.points.add(RoutePoint.fromJson(po));
            }
        }
        return r;
    }
}
