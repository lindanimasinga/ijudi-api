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
    var isAIAgentActive: Boolean = true
    var isNewSession: Boolean = true

    /** REQ-13: phone_number_id of the inbound line that created this session */
    var phoneNumberId: String? = null

    /** REQ-13: AI agent name resolved from the line at session creation */
    var agentName: String? = null

    /** REQ-13: optional store ID if the inbound line is store-scoped */
    var storeId: String? = null

    constructor()

    constructor(from: String) {
        this.from = from
    }
}

