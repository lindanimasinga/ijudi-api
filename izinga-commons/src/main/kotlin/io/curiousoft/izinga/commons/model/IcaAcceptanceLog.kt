package io.curiousoft.izinga.commons.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.util.Date

@Document(collection = "ica_acceptance_log")
class IcaAcceptanceLog(
    val userId: String,
    val mobileNumber: String,
    val icaVersion: String,
    val acceptedAt: Date,
    val ipAddress: String? = null
) {
    @Id
    var id: String? = null
}
