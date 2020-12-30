package io.curiousoft.ijudi.ordermanagement.model;

import org.springframework.data.mongodb.core.index.Indexed;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

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
    private String stockId;
    @NotNull(message = "promotion shop type not valid")
    private StoreType shopType;
    private Date expiryDate;

    public Promotion(@NotBlank(message = "promotion image url not valid") String imageUrl,
                     @NotBlank(message = "promotion shop id not valid") String shopId,
                     @NotNull(message = "promotion shop type not valid") StoreType shopType,
                     @NotNull(message = "promotion expiry date not valid") Date expiryDate) {
        this.imageUrl = imageUrl;
        this.expiryDate = expiryDate;
        this.shopId = shopId;
        this.shopType = shopType;
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

    public Date getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Date expiryDate) {
        this.expiryDate = expiryDate;
    }

    public StoreType getShopType() {
        return shopType;
    }

    public void setShopType(StoreType shopType) {
        this.shopType = shopType;
    }

    public String getStockId() {
        return stockId;
    }

    public void setStockId(String stockId) {
        this.stockId = stockId;
    }
}
