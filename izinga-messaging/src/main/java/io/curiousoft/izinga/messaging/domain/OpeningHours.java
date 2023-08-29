package io.curiousoft.izinga.messaging.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class OpeningHours implements Serializable {

    @SerializedName("open_now")
    @Expose
    private boolean openNow;
    private final static long serialVersionUID = 2210425825647602239L;

    /**
     * No args constructor for use in serialization
     */
    public OpeningHours() {
    }

    /**
     * @param openNow
     */
    public OpeningHours(boolean openNow) {
        super();
        this.openNow = openNow;
    }

    /**
     * @return The openNow
     */
    public boolean isOpenNow() {
        return openNow;
    }

    /**
     * @param openNow The open_now
     */
    public void setOpenNow(boolean openNow) {
        this.openNow = openNow;
    }

}
