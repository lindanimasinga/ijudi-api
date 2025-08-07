package io.curiousoft.izinga.ordermanagement.analytics;

public record FavoriteItems(
        String customerId,
        String customerName,
        String itemId,
        String itemName,
        String itemImageUrl
) {
}
