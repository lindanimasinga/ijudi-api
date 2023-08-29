package io.curiousoft.izinga.messaging.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

public class Photo implements Serializable {

    @SerializedName("height")
    @Expose
    private long height;
    @SerializedName("html_attributions")
    @Expose
    private List<Object> htmlAttributions = null;
    @SerializedName("photo_reference")
    @Expose
    private String photoReference;
    @SerializedName("width")
    @Expose
    private long width;
    private final static long serialVersionUID = 3391121852462293039L;

    /**
     * No args constructor for use in serialization
     */
    public Photo() {
    }

    /**
     * @param height
     * @param width
     * @param htmlAttributions
     * @param photoReference
     */
    public Photo(long height, List<Object> htmlAttributions, String photoReference, long width) {
        super();
        this.height = height;
        this.htmlAttributions = htmlAttributions;
        this.photoReference = photoReference;
        this.width = width;
    }

    /**
     * @return The height
     */
    public long getHeight() {
        return height;
    }

    /**
     * @param height The height
     */
    public void setHeight(long height) {
        this.height = height;
    }

    /**
     * @return The htmlAttributions
     */
    public List<Object> getHtmlAttributions() {
        return htmlAttributions;
    }

    /**
     * @param htmlAttributions The html_attributions
     */
    public void setHtmlAttributions(List<Object> htmlAttributions) {
        this.htmlAttributions = htmlAttributions;
    }

    /**
     * @return The photoReference
     */
    public String getPhotoReference() {
        return photoReference;
    }

    /**
     * @param photoReference The photo_reference
     */
    public void setPhotoReference(String photoReference) {
        this.photoReference = photoReference;
    }

    /**
     * @return The width
     */
    public long getWidth() {
        return width;
    }

    /**
     * @param width The width
     */
    public void setWidth(long width) {
        this.width = width;
    }

}
