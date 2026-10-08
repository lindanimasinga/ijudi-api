package io.curiousoft.izinga.commons.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Contract test — ONB-02 / ADR-022
 *
 * Guards the exact, ordered set of SubscriptionTier enum values that downstream
 * systems depend on. SubscriptionTier is replicated in:
 *
 *   - izinga-onboarding  src/app/model/storeProfile.ts   (T-12)
 *   - ijudi              lib/model/storeProfile.dart      (T-15)
 *
 * If this test fails it means a value was added, removed, or reordered without
 * coordinating the corresponding frontend changes. Update the frontend repos
 * listed above BEFORE merging a change to this enum.
 *
 * To update this test legitimately: confirm all frontend repos above have been
 * updated and deployed, then change EXPECTED_ORDERED_VALUES below to match the
 * new declaration and update this comment accordingly.
 *
 * null subscriptionTier on an existing StoreProfile document must always be
 * treated as FREE in all business logic — this invariant is NOT enforced by
 * the enum itself; it is a contract obligation on every consumer.
 */
class SubscriptionTierContractTest {

    /**
     * Baseline established: ONB-02, 2 October 2026 — 3 values.
     * Do NOT change this list without updating the frontend repos listed above.
     */
    private val expectedOrderedValues = listOf("FREE", "PREMIUM_1", "PREMIUM_2")

    @Test
    fun subscriptionTier_exactOrderedValuesMatchContractBaseline() {
        val actual = SubscriptionTier.values().map { it.name }

        val message = """
            ============================================================
            CONTRACT VIOLATION — SubscriptionTier enum has changed.
            ============================================================
            Expected (ordered): $expectedOrderedValues
            Actual   (ordered): $actual

            SubscriptionTier enum has changed. You must also update it in
            these frontend repos BEFORE this backend change may merge:

              izinga-onboarding  src/app/model/storeProfile.ts
              ijudi              lib/model/storeProfile.dart

            See ONB-02 feature brief / ADR-022 for rollout sequencing.
            Only update expectedOrderedValues once all frontend repos
            above have been updated and deployed.
            ============================================================
        """.trimIndent()

        assertEquals(expectedOrderedValues, actual) { message }
    }
}
