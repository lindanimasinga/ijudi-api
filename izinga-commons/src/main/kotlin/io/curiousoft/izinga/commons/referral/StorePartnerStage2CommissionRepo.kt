package io.curiousoft.izinga.commons.referral

import org.springframework.data.mongodb.repository.MongoRepository

interface StorePartnerStage2CommissionRepo : MongoRepository<StorePartnerStage2Commission, String> {
    /** Returns the Stage 2 commission for the given store, or null if not yet created. */
    fun findByStoreId(storeId: String): StorePartnerStage2Commission?

    /** RP-010: Returns all Stage 2 commissions earned by the given referral partner. */
    fun findByReferralPartnerId(referralPartnerId: String): List<StorePartnerStage2Commission>
}
