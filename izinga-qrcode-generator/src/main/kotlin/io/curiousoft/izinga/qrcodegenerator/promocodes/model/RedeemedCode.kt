package io.curiousoft.izinga.qrcodegenerator.promocodes.model

import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document
data class RedeemedCode(val code: String, val date: LocalDateTime, val userId: String)