package io.curiousoft.izinga.commons.referral

import org.springframework.data.mongodb.repository.MongoRepository

interface FoodCustomerReferralCommissionRepo : MongoRepository<FoodCustomerReferralCommission, String> {
    /** Returns the commission for the given customer, or null if one has not been created yet. */
    fun findByCustomerId(customerId: String): FoodCustomerReferralCommission?
}
