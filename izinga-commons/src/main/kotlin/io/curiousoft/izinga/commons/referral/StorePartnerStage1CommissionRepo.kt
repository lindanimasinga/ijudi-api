package io.curiousoft.izinga.commons.referral

import org.springframework.data.mongodb.repository.MongoRepository

interface StorePartnerStage1CommissionRepo : MongoRepository<StorePartnerStage1Commission, String> {
    /** Returns the Stage 1 commission for the given store, or null if not yet created. */
    fun findByStoreId(storeId: String): StorePartnerStage1Commission?
}
