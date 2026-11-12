package com.acooldog.fuckrun.model;

import com.acooldog.fuckrun.util.Geo;

import org.json.JSONException;
import org.json.JSONObject;

/** 路线点，统一保存 WGS84。 */
public class RoutePoint {
    public double lat;
    public double lng;
    public double altitude;

    public RoutePoint() {}

    public RoutePoint(double lat, double lng, double altitude) {
        this.lat = lat;
        this.lng = lng;
        this.altitude = altitude;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("lat", lat);
        o.put("lng", lng);
        o.put("alt", altitude);
        return o;
    }

    public static RoutePoint fromJson(JSONObject o) {
        RoutePoint p = new RoutePoint();
        p.lat = o.optDouble("lat", 0);
        p.lng = o.optDouble("lng", 0);
        p.altitude = o.optDouble("alt", 0);
        return p;
    }

    public double[] gcj02() {
        return Geo.wgs84ToGcj02(lat, lng);
    }

    public double[] bd09() {
        double[] g = gcj02();
        return Geo.gcj02ToBd09(g[0], g[1]);
    }
}
