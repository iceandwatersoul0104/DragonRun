package com.acooldog.fuckrun.sim;

import org.json.JSONException;
import org.json.JSONObject;

/** 模拟参数。与序列化到 Provider state 的字段一一对应。 */
public class SimConfig {
    public static final int MODE_SPEED = 0;
    public static final int MODE_CADENCE = 1;

    public int mode = MODE_SPEED;
    public double speedMps = 15.0 / 3.6;
    public double cadenceSpm = 176.0;
    public int loopCount = 100;

    public boolean dynamicIntensity = true;
    public double intensityRangeMps = 2.0;
    public double intensityFrequency = 0.35;

    public boolean pathVariation = true;
    public double pathAmplitudeMeters = 1.0;

    public boolean altitudeVariation = true;
    public double altitudeBaseMeters = 55.0;
    public double altitudeRangeMeters = 0.6;
    public double altitudeHeightCm = 170.0;
    public double altitudeProbability = 0.35;

    public boolean nmeaEnabled = true;
    public int satellites = 9;
    public double hdop = 1.5;
    public double accuracy = 3.0;
    public int tickMillis = 200;
    public long seed = 20250101L;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("mode", mode);
        o.put("speedMps", speedMps);
        o.put("cadenceSpm", cadenceSpm);
        o.put("loopCount", loopCount);
        o.put("dynamicIntensity", dynamicIntensity);
        o.put("intensityRangeMps", intensityRangeMps);
        o.put("intensityFrequency", intensityFrequency);
        o.put("pathVariation", pathVariation);
        o.put("pathAmplitudeMeters", pathAmplitudeMeters);
        o.put("altitudeVariation", altitudeVariation);
        o.put("altitudeBaseMeters", altitudeBaseMeters);
        o.put("altitudeRangeMeters", altitudeRangeMeters);
        o.put("altitudeHeightCm", altitudeHeightCm);
        o.put("altitudeProbability", altitudeProbability);
        o.put("nmeaEnabled", nmeaEnabled);
        o.put("satellites", satellites);
        o.put("hdop", hdop);
        o.put("accuracy", accuracy);
        o.put("tickMillis", tickMillis);
        o.put("seed", seed);
        return o;
    }

    public static SimConfig fromJson(JSONObject o) {
        SimConfig c = new SimConfig();
        if (o == null) return c;
        c.mode = o.optInt("mode", c.mode);
        c.speedMps = o.optDouble("speedMps", c.speedMps);
        c.cadenceSpm = o.optDouble("cadenceSpm", c.cadenceSpm);
        c.loopCount = Math.max(1, o.optInt("loopCount", c.loopCount));
        c.dynamicIntensity = o.optBoolean("dynamicIntensity", c.dynamicIntensity);
        c.intensityRangeMps = o.optDouble("intensityRangeMps", c.intensityRangeMps);
        c.intensityFrequency = o.optDouble("intensityFrequency", c.intensityFrequency);
        c.pathVariation = o.optBoolean("pathVariation", c.pathVariation);
        c.pathAmplitudeMeters = o.optDouble("pathAmplitudeMeters", c.pathAmplitudeMeters);
        c.altitudeVariation = o.optBoolean("altitudeVariation", c.altitudeVariation);
        c.altitudeBaseMeters = o.optDouble("altitudeBaseMeters", c.altitudeBaseMeters);
        c.altitudeRangeMeters = o.optDouble("altitudeRangeMeters", c.altitudeRangeMeters);
        c.altitudeHeightCm = o.optDouble("altitudeHeightCm", c.altitudeHeightCm);
        c.altitudeProbability = o.optDouble("altitudeProbability", c.altitudeProbability);
        c.nmeaEnabled = o.optBoolean("nmeaEnabled", c.nmeaEnabled);
        c.satellites = o.optInt("satellites", c.satellites);
        c.hdop = o.optDouble("hdop", c.hdop);
        c.accuracy = o.optDouble("accuracy", c.accuracy);
        c.tickMillis = Math.max(50, o.optInt("tickMillis", c.tickMillis));
        c.seed = o.optLong("seed", c.seed);
        return c;
    }
}
