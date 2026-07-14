package io.curiousoft.izinga.commons.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Contract test — RP-019
 *
 * This test encodes the exact, ordered set of ProfileRoles enum values that all
 * downstream systems depend on. It exists because ProfileRoles is duplicated in
 * multiple frontend repos that cannot be updated atomically with the backend.
 *
 * If this test fails it means a value was added, removed, or reordered in the
 * enum without coordinating the corresponding frontend changes.
 *
 * WHEN THIS TEST FAILS you must update the role enum independently in each of
 * the following frontend repos BEFORE the backend change may be merged:
 *
 *   - ijudi                  lib/model/profile.dart
 *   - izinga-onboarding      src/app/model/userProfile.ts
 *                            src/app/model/profile.ts
 *                            src/app/model/storeProfile.ts
 *                            src/app/model/store-summary.ts
 *   - cs-lifestyle           src/app/model/userProfile.ts
 *                            src/app/model/profile.ts
 *                            src/app/model/storeProfile.ts
 *   - furniture-delivery-app src/app/model/userProfile.ts
 *                            src/app/model/profile.ts
 *                            src/app/model/storeProfile.ts
 *
 * See ADR-018 for the full rollout sequencing — frontend changes must deploy
 * before the backend starts accepting a new role value in production.
 *
 * To update this test legitimately: confirm all frontend repos above have been
 * updated and deployed, then change EXPECTED_ORDERED_VALUES below to match the
 * new enum declaration and update this comment accordingly.
 */
class ProfileRolesContractTest {

    /**
     * The canonical ordered list of ProfileRoles values.
     *
     * Baseline established: RP-019, 2026-07-14 — 8 values.
     * Note: REFERRAL_PARTNER was added by ticket #98 (already on develop at
     * the time RP-019 branched). This test captures the full 8-value baseline.
     *
     * Do NOT change this list without updating all frontend repos listed above.
     */
    private val expectedOrderedValues = listOf(
        "CUSTOMER",
        "STORE_ADMIN",
        "STORE",
        "MESSENGER",
        "MESSENGER_ADMIN",
        "ADMIN",
        "AMBASSADOR",
        "REFERRAL_PARTNER"
    )

    @Test
    fun profileRoles_exactOrderedValuesMatchContractBaseline() {
        val actual = ProfileRoles.values().map { it.name }

        val message = """
            ============================================================
            CONTRACT VIOLATION — ProfileRoles enum has changed.
            ============================================================
            Expected (ordered): $expectedOrderedValues
            Actual   (ordered): $actual

            ProfileRoles enum has changed. You must also update the role
            enum independently in these frontend repos BEFORE this backend
            change may merge:

              ijudi                  lib/model/profile.dart
              izinga-onboarding      src/app/model/userProfile.ts
                                     src/app/model/profile.ts
                                     src/app/model/storeProfile.ts
                                     src/app/model/store-summary.ts
              cs-lifestyle           src/app/model/userProfile.ts
                                     src/app/model/profile.ts
                                     src/app/model/storeProfile.ts
              furniture-delivery-app src/app/model/userProfile.ts
                                     src/app/model/profile.ts
                                     src/app/model/storeProfile.ts

            See ADR-018 for rollout sequencing — frontends must deploy
            BEFORE the backend accepts the new role value in production.

            Only update expectedOrderedValues in this test once all
            frontend repos above have been updated and deployed.
            ============================================================
        """.trimIndent()

        assertEquals(expectedOrderedValues, actual) { message }
    }
}
