package io.curiousoft.izinga.messaging.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class AltId implements Serializable {

    @SerializedName("place_id")
    @Expose
    private String placeId;
    @SerializedName("scope")
    @Expose
    private String scope;
    private final static long serialVersionUID = 8073405616524364555L;

    /**
     * No args constructor for use in serialization
     */
    public AltId() {
    }

    /**
     * @param scope
     * @param placeId
     */
    public AltId(String placeId, String scope) {
        super();
        this.placeId = placeId;
        this.scope = scope;
    }

    /**
     * @return The placeId
     */
    public String getPlaceId() {
        return placeId;
    }

    /**
     * @param placeId The place_id
     */
    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }

    /**
     * @return The scope
     */
    public String getScope() {
        return scope;
    }

    /**
     * @param scope The scope
     */
}
