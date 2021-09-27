package org.pleaselast.administrator;

import com.google.gson.annotations.SerializedName;

public class Post {
    @SerializedName("device_id_1")
    private String device_id_1;
    @SerializedName("device_id_2")
    private String device_id_2;

    public Post(String device_id_1, String device_id_2)  {
        this.device_id_1 = device_id_1;
        this.device_id_2 = device_id_2;
    }
    public String getDevice_id_1() {return device_id_1;}
    public String getDevice_id_2() {return device_id_2;}
}

