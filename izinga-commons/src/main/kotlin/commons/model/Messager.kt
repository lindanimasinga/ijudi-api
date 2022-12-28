package io.curiousoft.izinga.commons.model

import javax.validation.constraints.DecimalMin
import javax.validation.constraints.NotEmpty

class Messager(
    var name: @NotEmpty(message = "Messenger name not valid") String?,
    var standardDeliveryPrice: @DecimalMin(
        value = "0.001",
        message = "delivery price must be greater than or equal to 0.001"
    ) Double,
    var standardDeliveryKm: @DecimalMin(
        value = "0.1",
        message = "Distance must be greater than or equal to 0.1km"
    ) Double,
    var ratePerKm: @DecimalMin(value = "0.01", message = "ratePerKm must be greater than or equal to 0.01") Double
) : BaseModel()