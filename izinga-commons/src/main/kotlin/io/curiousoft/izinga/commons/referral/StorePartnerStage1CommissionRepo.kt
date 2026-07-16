package io.curiousoft.izinga.commons.referral

import org.springframework.data.mongodb.repository.MongoRepository

interface StorePartnerStage1CommissionRepo : MongoRepository<StorePartnerStage1Commission, String> {
    /** Returns the Stage 1 commission for the given store, or null if not yet created. */
    fun findByStoreId(storeId: String): StorePartnerStage1Commission?

    /** RP-010: Returns all Stage 1 commissions earned by the given referral partner. */
    fun findByReferralPartnerId(referralPartnerId: String): List<StorePartnerStage1Commission>
}
