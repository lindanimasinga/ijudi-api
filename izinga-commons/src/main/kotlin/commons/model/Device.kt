package io.curiousoft.izinga.commons.model

import org.springframework.data.mongodb.core.index.Indexed
import java.util.*
import javax.validation.constraints.NotBlank

class Device : BaseModel {
    @Indexed(unique = true)
    var token: @NotBlank(message = "device token required") String? = null
    var userId: String? = null

    constructor(token: @NotBlank(message = "device token required") String?) : super(UUID.randomUUID().toString()) {
        this.token = token
    }

    constructor() : super(null)
}