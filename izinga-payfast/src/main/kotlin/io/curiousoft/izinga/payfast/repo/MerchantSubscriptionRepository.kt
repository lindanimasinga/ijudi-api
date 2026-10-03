package io.curiousoft.izinga.payfast.repo

import io.curiousoft.izinga.payfast.model.MerchantSubscription
import io.curiousoft.izinga.payfast.model.MerchantSubscriptionStatus
import org.springframework.data.mongodb.repository.MongoRepository

/**
 * TIER-BILLING-01 / REQ-03: Repository for [MerchantSubscription] documents.
 *
 * NOTE: No delete operation is exposed. Subscriptions are soft-cancelled via the
 * [MerchantSubscription.status] and [MerchantSubscription.cancelledDate] fields.
 * Hard delete is not permitted.
 */
interface MerchantSubscriptionRepository : MongoRepository<MerchantSubscription, String> {

    /** Find all subscriptions for a given store, across all statuses. */
    fun findByStoreId(storeId: String): List<MerchantSubscription>

    /** Find subscriptions for a store filtered by a specific status. */
    fun findByStoreIdAndStatus(storeId: String, status: MerchantSubscriptionStatus): List<MerchantSubscription>
}
