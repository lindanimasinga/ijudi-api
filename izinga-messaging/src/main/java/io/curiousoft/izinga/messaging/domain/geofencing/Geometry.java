package io.curiousoft.izinga.messaging.domain.geofencing;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Geometry {

    @SerializedName("location")
    @Expose
    private Location location;
    @SerializedName("location_type")
    @Expose
    private String locationType;
    @SerializedName("viewport")
    @Expose
    private Viewport viewport;

    /**
     * No args constructor for use in serialization
     */
    public Geometry() {
    }

    /**
     * @param viewport
     * @param location
     * @param locationType
     */
    public Geometry(Location location, String locationType, Viewport viewport) {
        super();
        this.location = location;
        this.locationType = locationType;
        this.viewport = viewport;
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

    /**
     * @return The locationType
     */
    public String getLocationType() {
        return locationType;
    }

    /**
     * @param locationType The location_type
     */
    public void setLocationType(String locationType) {
        this.locationType = locationType;
    }

    /**
     * @return The viewport
     */
    public Viewport getViewport() {
        return viewport;
    }

    /**
     * @param viewport The viewport
     */
    public void setViewport(Viewport viewport) {
        this.viewport = viewport;
    }

}
