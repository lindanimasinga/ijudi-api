package io.curiousoft.izinga.commons.referral

import org.springframework.data.mongodb.repository.MongoRepository

interface FoodCustomerReferralCommissionRepo : MongoRepository<FoodCustomerReferralCommission, String> {
    /** Returns the commission for the given customer, or null if one has not been created yet. */
    fun findByCustomerId(customerId: String): FoodCustomerReferralCommission?

    /** RP-010: Returns all commissions earned by the given referral partner. */
    fun findByReferralPartnerId(referralPartnerId: String): List<FoodCustomerReferralCommission>
}
