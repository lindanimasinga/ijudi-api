package io.curiousoft.izinga.recon.ambassador

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal

@ConfigurationProperties(prefix = "izinga.ambassador")
data class AmbassadorProperties(
    val commissionAmount: BigDecimal = BigDecimal("70.00"),
    val commissionAmountBike: BigDecimal = BigDecimal("50.00"),
    val commissionAmountCar: BigDecimal = BigDecimal("60.00"),
    val commissionAmountBakkie: BigDecimal = BigDecimal("70.00"),
    val commissionAmountTruck: BigDecimal = BigDecimal("70.00")
)
