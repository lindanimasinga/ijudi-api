package io.curiousoft.izinga.commons.model

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.HashMap

open class BaseModel {

    @Id
    var id: String? = null

    @JsonProperty(value = "date")
    @CreatedDate
    var createdDate = Date()

    @LastModifiedDate
    var modifiedDate: Date? = null

    var tag: MutableMap<String, String> = mutableMapOf()

    constructor()
    constructor(id: String?) {
        this.id = id
    }
}