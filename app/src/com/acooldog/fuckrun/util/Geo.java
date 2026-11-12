package com.acooldog.fuckrun.util;

/** 坐标系转换与地理计算。内部统一使用 WGS84。 */
public final class Geo {
    private static final double PI = 3.1415926535897932384626;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;
    private static final double X_PI = PI * 3000.0 / 180.0;
    public static final double EARTH_RADIUS = 6371000.0;

    private Geo() {}

    public static boolean outOfChina(double lat, double lng) {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }

    /** WGS84 -> GCJ02。 */
    public static double[] wgs84ToGcj02(double lat, double lng) {
        if (outOfChina(lat, lng)) return new double[]{lat, lng};
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{lat + dLat, lng + dLng};
    }

    /** GCJ02 -> WGS84（迭代反解，误差 < 0.5m）。 */
    public static double[] gcj02ToWgs84(double lat, double lng) {
        if (outOfChina(lat, lng)) return new double[]{lat, lng};
        double wLat = lat, wLng = lng;
        for (int i = 0; i < 6; i++) {
            double[] g = wgs84ToGcj02(wLat, wLng);
            wLat -= (g[0] - lat);
            wLng -= (g[1] - lng);
        }
        return new double[]{wLat, wLng};
    }

    /** GCJ02 -> BD09。 */
    public static double[] gcj02ToBd09(double lat, double lng) {
        double z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * X_PI);
        double theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * X_PI);
        return new double[]{z * Math.sin(theta) + 0.006, z * Math.cos(theta) + 0.0065};
    }

    /** BD09 -> GCJ02。 */
    public static double[] bd09ToGcj02(double lat, double lng) {
        double x = lng - 0.0065, y = lat - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI);
        return new double[]{z * Math.sin(theta), z * Math.cos(theta)};
    }

    public static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** 方位角 0..360。 */
    public static double bearing(double lat1, double lng1, double lat2, double lng2) {
        double phi1 = Math.toRadians(lat1), phi2 = Math.toRadians(lat2);
        double dLng = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLng) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLng);
        double b = Math.toDegrees(Math.atan2(y, x));
        return (b + 360.0) % 360.0;
    }

    /** 沿 bearing 方向偏移米数，返回新的 WGS84 {lat,lng}。 */
    public static double[] offset(double lat, double lng, double bearingDeg, double distanceMeters) {
        double dLat = Math.toDegrees(distanceMeters / EARTH_RADIUS);
        double dLng = Math.toDegrees(distanceMeters / (EARTH_RADIUS * Math.cos(Math.toRadians(lat))));
        double rad = Math.toRadians(bearingDeg);
        return new double[]{lat + dLat * Math.cos(rad), lng + dLng * Math.sin(rad)};
    }
}
