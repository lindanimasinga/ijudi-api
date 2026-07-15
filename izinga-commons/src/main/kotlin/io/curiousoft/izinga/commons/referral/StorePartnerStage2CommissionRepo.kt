package io.curiousoft.izinga.commons.referral

import org.springframework.data.mongodb.repository.MongoRepository

interface StorePartnerStage2CommissionRepo : MongoRepository<StorePartnerStage2Commission, String> {
    /** Returns the Stage 2 commission for the given store, or null if not yet created. */
    fun findByStoreId(storeId: String): StorePartnerStage2Commission?
}
