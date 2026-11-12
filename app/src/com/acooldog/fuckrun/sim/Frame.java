package com.acooldog.fuckrun.sim;

/** 单个模拟帧（WGS84）。 */
public class Frame {
    public double lat;
    public double lng;
    public double altitude;
    public double bearing;
    public double speed;
    public double distance;
    public long elapsedMs;
    public boolean finished;
    public int loop;
}
