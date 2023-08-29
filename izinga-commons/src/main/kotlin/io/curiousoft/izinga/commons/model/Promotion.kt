package io.curiousoft.izinga.commons.model

import org.springframework.data.mongodb.core.index.Indexed
import java.util.*
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

class Promotion(
    var imageUrl: @NotBlank(message = "promotion image url not valid") String?,
    var shopId: @NotBlank(message = "promotion shop id not valid") String?,
    var shopType: @NotNull(message = "promotion shop type not valid") StoreType?,
    var expiryDate: @NotNull(message = "promotion expiry date not valid") Date?
) {
    @Indexed(unique = true)
    var id: String? = null
    var actionUrl: String? = null

    @Indexed(unique = true)
    var title: String? = null
    var message: String? = null
    var stockId: String? = null
    var position = 10000

}