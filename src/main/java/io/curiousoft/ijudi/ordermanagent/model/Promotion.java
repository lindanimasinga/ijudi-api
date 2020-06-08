package io.curiousoft.ijudi.ordermanagent.model;

import org.springframework.data.mongodb.core.index.Indexed;

import javax.validation.constraints.NotBlank;

public class Promotion {

    @Indexed(unique = true)
    private String id;
    @NotBlank(message = "promotion image url not valid")
    private String imageUrl;
    private String actionUrl;
    @Indexed(unique = true)
    private String title;
    private String message;
    @NotBlank(message = "promotion shop id url not valid")
    private String shopId;

    public Promotion(@NotBlank(message = "promotion image url not valid") String imageUrl,
                     @NotBlank(message = "promotion shop id url not valid") String shopId) {
        this.imageUrl = imageUrl;
        this.actionUrl = actionUrl;
        this.shopId = shopId;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }
}
