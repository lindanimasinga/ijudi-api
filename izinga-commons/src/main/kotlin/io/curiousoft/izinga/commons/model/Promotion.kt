package io.curiousoft.izinga.commons.model

import org.springframework.data.mongodb.core.index.Indexed
import java.util.*
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

class Promotion {
    var imageUrl: @NotBlank(message = "promotion image url not valid") String? = null
    var shopId: @NotBlank(message = "promotion shop id not valid") String?  = null
    var shopType: @NotNull(message = "promotion shop type not valid") StoreType?  = null
    var expiryDate: @NotNull(message = "promotion expiry date not valid") Date?  = null

    constructor()
    constructor(
        imageUrl: @NotBlank(message = "promotion image url not valid") String?,
        shopId: @NotBlank(message = "promotion shop id not valid") String?,
        shopType: @NotNull(message = "promotion shop type not valid") StoreType?,
        expiryDate: @NotNull(message = "promotion expiry date not valid") Date?
    ) {
        this.imageUrl = imageUrl
        this.shopId = shopId
        this.shopType = shopType
        this.expiryDate = expiryDate
    }

    @Indexed(unique = true)
    var id: String? = null
    var actionUrl: String? = null

    @Indexed(unique = true)
    var title: String? = null
    var message: String? = null
    var stockId: String? = null
    var position = 10000

}