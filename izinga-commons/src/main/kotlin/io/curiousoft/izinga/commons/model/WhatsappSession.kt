package io.curiousoft.izinga.commons.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "whatsapp_session")
class WhatsappSession: BaseModel {

    @Indexed(unique = true)
    var from: String? = null

    var lastMessageDate: Instant? = null

    constructor()

    constructor(from: String, lastMessageDate: Instant) {
        this.from = from
        this.lastMessageDate = lastMessageDate
    }
}

