package io.curiousoft.izinga.commons.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * REQ-12: (from, phoneNumberId) is the unique key, not `from` alone — the same
 * sender can hold independent sessions on different WhatsApp lines.
 */
@Document(collection = "whatsapp_session")
@CompoundIndex(def = "{'from': 1, 'phoneNumberId': 1}", unique = true, name = "from_phoneNumberId_unique")
class WhatsappSession: BaseModel {
    @Indexed
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

