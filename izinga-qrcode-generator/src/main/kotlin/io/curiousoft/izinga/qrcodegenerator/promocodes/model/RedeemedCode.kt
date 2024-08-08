package io.curiousoft.izinga.qrcodegenerator.promocodes.model

import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document
data class RedeemedCode(val code: String, val expiryDate: LocalDateTime, val userId: Int)