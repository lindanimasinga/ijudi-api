package io.curiousoft.izinga.commons.model

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests for the six nullable fields added to StoreProfile in ONB-02 (ADR-022 Decision 2/1/5).
 *
 * All fields must default to null so that existing MongoDB documents deserialise without error
 * and null subscriptionTier is correctly interpreted as FREE by consumers.
 *
 * The fields carry no business logic themselves — gate logic lives in StoreService (T-06, T-07).
 */
class StoreProfileONB02FieldsTest {

    private fun buildMinimalStoreProfile(): StoreProfile {
        val bank = Bank().apply {
            accountId = "12345678"
            name = "FNB"
            branchCode = "250655"
            phone = "0800110132"
            type = BankAccType.CHEQUE
        }
        return StoreProfile(
            storeType = StoreType.FOOD,
            name = "Test Store",
            shortName = "teststore",
            address = "1 Test Street",
            imageUrl = "https://example.com/img.png",
            mobileNumber = "0821234567",
            tags = mutableListOf("pizza"),
            businessHours = mutableListOf(),
            ownerId = "owner-001",
            bank = bank
        )
    }

    @Test
    fun subscriptionTier_defaultsToNull_treatedAsFreeByCaller() {
        val store = buildMinimalStoreProfile()
        assertNull(store.subscriptionTier,
            "subscriptionTier must default to null — callers treat null as FREE (ADR-022 Decision 2)")
    }

    @Test
    fun subscriptionTierSince_defaultsToNull() {
        val store = buildMinimalStoreProfile()
        assertNull(store.subscriptionTierSince,
            "subscriptionTierSince must default to null — set server-side only on tier change")
    }

    @Test
    fun icaAccepted_defaultsToNull_treatedAsFalseByCaller() {
        val store = buildMinimalStoreProfile()
        assertNull(store.icaAccepted,
            "icaAccepted must default to null — callers treat null as false (C-04 gate)")
    }

    @Test
    fun icaAcceptedDate_defaultsToNull() {
        val store = buildMinimalStoreProfile()
        assertNull(store.icaAcceptedDate,
            "icaAcceptedDate must default to null — populated on PATCH /store/{id}/ica-acceptance")
    }

    @Test
    fun icaVersion_defaultsToNull() {
        val store = buildMinimalStoreProfile()
        assertNull(store.icaVersion,
            "icaVersion must default to null — populated on ICA acceptance")
    }

    @Test
    fun whatsappLineRequested_defaultsToNull_signalFieldOnly() {
        val store = buildMinimalStoreProfile()
        assertNull(store.whatsappLineRequested,
            "whatsappLineRequested must default to null — signal only, no provisioning side-effect (ADR-022 Decision 5)")
    }

    @Test
    fun allSixFieldsCanBeSetAndRead() {
        val store = buildMinimalStoreProfile()
        val now = java.util.Date()

        store.subscriptionTier = SubscriptionTier.PREMIUM_1
        store.subscriptionTierSince = now
        store.icaAccepted = true
        store.icaAcceptedDate = now
        store.icaVersion = "merchant-v2"
        store.whatsappLineRequested = true

        assert(store.subscriptionTier == SubscriptionTier.PREMIUM_1)
        assert(store.subscriptionTierSince == now)
        assert(store.icaAccepted == true)
        assert(store.icaAcceptedDate == now)
        assert(store.icaVersion == "merchant-v2")
        assert(store.whatsappLineRequested == true)
    }
}
