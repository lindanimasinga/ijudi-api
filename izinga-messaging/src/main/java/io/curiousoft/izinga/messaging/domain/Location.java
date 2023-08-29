package io.curiousoft.izinga.messaging.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class Location implements Serializable {

    @SerializedName("lat")
    @Expose
    private double lat;
    @SerializedName("lng")
    @Expose
    private double lng;
    private final static long serialVersionUID = -4538293348158118038L;

    /**
     * No args constructor for use in serialization
     */
    public Location() {
    }

    /**
     * @param lng
     * @param lat
     */
    public Location(double lat, double lng) {
        super();
        this.lat = lat;
        this.lng = lng;
    }

    /**
     * @return The lat
     */
    public double getLat() {
        return lat;
    }

    /**
     * @param lat The lat
     */
    public void setLat(double lat) {
        this.lat = lat;
    }

    /**
     * @return The lng
     */
    public double getLng() {
        return lng;
    }

    /**
     * @param lng The lng
     */
    public void setLng(double lng) {
        this.lng = lng;
    }

}
