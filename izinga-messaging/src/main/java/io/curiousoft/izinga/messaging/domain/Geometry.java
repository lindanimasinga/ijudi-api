package io.curiousoft.izinga.messaging.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class Geometry implements Serializable {

    @SerializedName("location")
    @Expose
    private Location location;
    private final static long serialVersionUID = 8750978421790872029L;

    /**
     * No args constructor for use in serialization
     */
    public Geometry() {
    }

    /**
     * @param location
     */
    public Geometry(Location location) {
        super();
        this.location = location;
    }

    /**
     * @return The location
     */
    public Location getLocation() {
        return location;
    }

    /**
     * @param location The location
     */
    public void setLocation(Location location) {
        this.location = location;
    }

}
