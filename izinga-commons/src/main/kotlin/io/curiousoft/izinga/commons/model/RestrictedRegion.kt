package io.curiousoft.izinga.commons.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "restricted_regions")
data class RestrictedRegion(
    @Id
    var id: String,
    var name: String,
    var description: String? = null,
    var center: GeoPointImpl,
    var radius: Double,
    var active: Boolean = true
)

