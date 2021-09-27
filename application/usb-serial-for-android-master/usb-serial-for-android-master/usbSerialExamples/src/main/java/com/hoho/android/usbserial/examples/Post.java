package com.hoho.android.usbserial.examples;

import com.google.gson.annotations.SerializedName;

public class Post {
//    @SerializedName("x")
//    private double x;
//    @SerializedName("y")
//    private double y;
//    @SerializedName("z")
//    private double z;
    @SerializedName("deviceId")
    private String deviceId;
    @SerializedName("pos_x")
    private double pos_x;
    @SerializedName("pos_y")
    private double pos_y;
    @SerializedName("pos_z")
    private double pos_z;
    @SerializedName("pressure")
    private double pressure;

    public Post(String deviceId, double pos_x, double pos_y, double pos_z, double pressure)  {
        this.deviceId = deviceId;
        this.pos_x = pos_x;
        this.pos_y = pos_y;
        this.pos_z = pos_z;
        this.pressure = pressure;
    }
    public double getPos_x() { return pos_x;}
    public double getPos_y() { return pos_y;}
    public double getPos_z() { return pos_z;}
    public double getPressure() { return pressure;}
}
