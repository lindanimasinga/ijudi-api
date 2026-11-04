package io.curiousoft.izinga.commons.model

import org.springframework.data.mongodb.core.index.Indexed
import java.util.*
import jakarta.validation.constraints.NotBlank

class Device : BaseModel {
    @Indexed(unique = true)
    @field:NotBlank(message = "device token required")
    var token: String? = null
    var userId: String? = null

    constructor(token: String?) : super(UUID.randomUUID().toString()) {
        this.token = token
    }

    constructor() : super(null)
}