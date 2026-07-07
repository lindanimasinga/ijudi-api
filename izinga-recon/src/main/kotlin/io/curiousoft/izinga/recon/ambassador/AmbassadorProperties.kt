package io.curiousoft.izinga.recon.ambassador

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal

@ConfigurationProperties(prefix = "izinga.ambassador")
data class AmbassadorProperties(
    val commissionAmount: BigDecimal = BigDecimal("70.00")
)
