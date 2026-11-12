package com.acooldog.fuckrun.xposed;

import com.acooldog.fuckrun.model.Route;
import com.acooldog.fuckrun.model.RoutePoint;
import com.acooldog.fuckrun.sim.SimConfig;

import org.json.JSONArray;
import org.json.JSONObject;

/** 从宿主应用 Provider 读到的运行状态。 */
public class State {
    public boolean running;
    public long startedAt;
    public String routeId = "";
    public String routeName = "";
    public SimConfig config = new SimConfig();
    public Route route;
    public boolean cadenceOn;
    public float cadenceMin = 172f;
    public float cadenceMax = 182f;
    public boolean antiRoot = true;
    public boolean antiMock = true;
    public boolean silentLog;
    public int satellites = 9;
    public double hdop = 1.5;
    public double accuracy = 3.0;
    public boolean adBlock = true;
    public long fetchedAt;

    public static State parse(String json) {
        State s = new State();
        try {
            JSONObject o = new JSONObject(json);
            s.running = o.optBoolean("running", false);
            s.startedAt = o.optLong("startedAt", 0);
            s.routeId = o.optString("routeId", "");
            s.routeName = o.optString("routeName", "");
            s.cadenceOn = o.optBoolean("cadenceOn", false);
            s.cadenceMin = (float) o.optDouble("cadenceMin", 172);
            s.cadenceMax = (float) o.optDouble("cadenceMax", 182);
            s.antiRoot = o.optBoolean("antiRoot", true);
            s.antiMock = o.optBoolean("antiMock", true);
            s.silentLog = o.optBoolean("silentLog", false);
            s.adBlock = o.optBoolean("adBlock", true);
            s.satellites = o.optInt("satellites", 9);
            s.hdop = o.optDouble("hdop", 1.5);
            s.accuracy = o.optDouble("accuracy", 3.0);
            JSONObject cfg = o.optJSONObject("config");
            if (cfg != null) s.config = SimConfig.fromJson(cfg);
            JSONArray arr = o.optJSONArray("points");
            if (arr != null) {
                Route r = new Route();
                r.id = s.routeId;
                r.name = s.routeName;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject p = arr.optJSONObject(i);
                    if (p != null) r.points.add(RoutePoint.fromJson(p));
                }
                s.route = r;
            }
        } catch (Exception ignored) {
        }
        s.fetchedAt = System.currentTimeMillis();
        return s;
    }

    public boolean hasRoute() {
        return route != null && route.points.size() >= 2;
    }
}
