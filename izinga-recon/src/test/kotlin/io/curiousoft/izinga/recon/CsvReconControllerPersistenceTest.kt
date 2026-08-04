package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.commons.referral.ReferralCommissionType
import io.curiousoft.izinga.recon.payout.AmbassadorPayout
import io.curiousoft.izinga.recon.payout.MessengerPayout
import io.curiousoft.izinga.recon.payout.Payout
import io.curiousoft.izinga.recon.payout.PayoutBundle
import io.curiousoft.izinga.recon.payout.PayoutStage
import io.curiousoft.izinga.recon.payout.PayoutType
import io.curiousoft.izinga.recon.payout.ReferralPartnerPayout
import io.curiousoft.izinga.recon.payout.ShopPayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

/**
 * Persistence side-effect tests for CsvReconController.
 *
 * Verifies that after a successful CSV download each endpoint:
 *   1. mutates every payout's payoutStage to PayoutStage.PROCESSING, and
 *   2. calls reconService.updateBundle() with the mutated bundle.
 *
 * Strategy: the mock stub returns a PayoutBundle whose payout list contains
 * real Payout instances we hold references to. The controller mutates the payout
 * objects in-place, so after the MockMvc call we can assert on those references
 * directly. We then verify updateBundle() was called with the same bundle object
 * using Mockito's exact-reference matching — no ArgumentCaptor needed (which
 * would require mockito-kotlin to avoid Kotlin null-safety conflicts).
 *
 * Follows the same @WebMvcTest + @MockBean + @WithMockUser conventions as
 * CsvReconControllerSecurityTest.
 */
@WebMvcTest(controllers = [CsvReconController::class])
class CsvReconControllerPersistenceTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var reconService: ReconService

    // ---------------------------------------------------------------------------
    // Fixture helpers
    // ---------------------------------------------------------------------------

    private fun shopPayout(stage: PayoutStage = PayoutStage.PENDING): ShopPayout =
        ShopPayout(
            toId = "shop-001",
            toName = "Test Shop",
            toBankName = "FNB",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "123456789",
            orders = mutableSetOf(),
            toBranchCode = "250655",
            fromReference = "iZinga",
            toReference = "SHOP-REF",
            emailNotify = null,
            emailAddress = null,
            emailSubject = null,
            payoutStage = stage
        )

    private fun messengerPayout(stage: PayoutStage = PayoutStage.PENDING): MessengerPayout =
        MessengerPayout(
            toId = "messenger-001",
            toName = "Test Driver",
            toBankName = "Standard Bank",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "987654321",
            toBranchCode = "051001",
            fromReference = "iZinga",
            toReference = "MSG-REF",
            emailNotify = null,
            orders = mutableSetOf(),
            emailAddress = null,
            emailSubject = null,
            payoutStage = stage
        )

    private fun ambassadorPayout(stage: PayoutStage = PayoutStage.PENDING): AmbassadorPayout =
        AmbassadorPayout(
            toId = "ambassador-001",
            toName = "Test Ambassador",
            toBankName = "Nedbank",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "111222333",
            toBranchCode = "198765",
            fromReference = "iZinga",
            toReference = "AMB-REF",
            emailNotify = null,
            emailAddress = null,
            emailSubject = null,
            payoutStage = stage,
            commissionAmount = BigDecimal("250.00"),
            triggerDriverId = "driver-001"
        )

    private fun referralPartnerPayout(stage: PayoutStage = PayoutStage.PENDING): ReferralPartnerPayout =
        ReferralPartnerPayout(
            toId = "partner-001",
            toName = "Test Partner",
            toBankName = "Absa",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "444555666",
            toBranchCode = "632005",
            fromReference = "iZinga",
            toReference = "REF-PARTNER",
            emailNotify = null,
            emailAddress = null,
            emailSubject = null,
            payoutStage = stage,
            commissionAmount = BigDecimal("100.00"),
            commissionType = ReferralCommissionType.FOOD_CUSTOMER_REFERRAL,
            triggerReferenceId = "customer-001"
        )

    // ---------------------------------------------------------------------------
    // shop-payout-bundle — single payout advanced to PROCESSING
    // ---------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET shop-payout-bundle advances payout to PROCESSING and calls updateBundle`() {
        val payout = shopPayout(PayoutStage.PENDING)
        val bundle = PayoutBundle(PayoutType.SHOP, listOf(payout), "admin")
        given(reconService.getCurrentPayoutBundleForShops()).willReturn(bundle)

        mockMvc.perform(get("/reconcsv/shop-payout-bundle"))
            .andExpect(status().isOk)

        // Controller mutates payout objects in-place before calling updateBundle.
        assertEquals(
            PayoutStage.PROCESSING, payout.payoutStage,
            "Payout must be PROCESSING after CSV export"
        )
        // Verify updateBundle was called with the same bundle reference.
        verify(reconService).updateBundle(bundle)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET shop-payout-bundle with multiple payouts advances all to PROCESSING`() {
        val payout1 = shopPayout(PayoutStage.PENDING)
        val payout2 = shopPayout(PayoutStage.PENDING)
        val bundle = PayoutBundle(PayoutType.SHOP, listOf(payout1, payout2), "admin")
        given(reconService.getCurrentPayoutBundleForShops()).willReturn(bundle)

        mockMvc.perform(get("/reconcsv/shop-payout-bundle"))
            .andExpect(status().isOk)

        assertEquals(PayoutStage.PROCESSING, payout1.payoutStage)
        assertEquals(PayoutStage.PROCESSING, payout2.payoutStage)
        verify(reconService).updateBundle(bundle)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET shop-payout-bundle with empty payout list still calls updateBundle`() {
        val bundle = PayoutBundle(PayoutType.SHOP, emptyList<Payout>(), "admin")
        given(reconService.getCurrentPayoutBundleForShops()).willReturn(bundle)

        mockMvc.perform(get("/reconcsv/shop-payout-bundle"))
            .andExpect(status().isOk)

        verify(reconService).updateBundle(bundle)
    }

    // ---------------------------------------------------------------------------
    // messenger-payout-bundle — single payout advanced to PROCESSING
    // ---------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET messenger-payout-bundle advances payout to PROCESSING and calls updateBundle`() {
        val payout = messengerPayout(PayoutStage.PENDING)
        val bundle = PayoutBundle(PayoutType.MESSENGER, listOf(payout), "admin")
        given(reconService.getCurrentPayoutBundleForMessenger()).willReturn(bundle)

        mockMvc.perform(get("/reconcsv/messenger-payout-bundle"))
            .andExpect(status().isOk)

        assertEquals(
            PayoutStage.PROCESSING, payout.payoutStage,
            "Payout must be PROCESSING after CSV export"
        )
        verify(reconService).updateBundle(bundle)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET messenger-payout-bundle with multiple payouts advances all to PROCESSING`() {
        val payout1 = messengerPayout(PayoutStage.PENDING)
        val payout2 = messengerPayout(PayoutStage.PENDING)
        val bundle = PayoutBundle(PayoutType.MESSENGER, listOf(payout1, payout2), "admin")
        given(reconService.getCurrentPayoutBundleForMessenger()).willReturn(bundle)

        mockMvc.perform(get("/reconcsv/messenger-payout-bundle"))
            .andExpect(status().isOk)

        assertEquals(PayoutStage.PROCESSING, payout1.payoutStage)
        assertEquals(PayoutStage.PROCESSING, payout2.payoutStage)
        verify(reconService).updateBundle(bundle)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET messenger-payout-bundle with empty payout list still calls updateBundle`() {
        val bundle = PayoutBundle(PayoutType.MESSENGER, emptyList<Payout>(), "admin")
        given(reconService.getCurrentPayoutBundleForMessenger()).willReturn(bundle)

        mockMvc.perform(get("/reconcsv/messenger-payout-bundle"))
            .andExpect(status().isOk)

        verify(reconService).updateBundle(bundle)
    }

    // ---------------------------------------------------------------------------
    // ambassador-payout-bundle — single payout advanced to PROCESSING
    // ---------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET ambassador-payout-bundle advances payout to PROCESSING and calls updateBundle`() {
        val payout = ambassadorPayout(PayoutStage.PENDING)
        val bundle = PayoutBundle(PayoutType.AMBASSADOR, listOf(payout), "admin")
        given(reconService.getCurrentPayoutBundleForAmbassadors()).willReturn(bundle)

        mockMvc.perform(get("/reconcsv/ambassador-payout-bundle"))
            .andExpect(status().isOk)

        assertEquals(
            PayoutStage.PROCESSING, payout.payoutStage,
            "Payout must be PROCESSING after CSV export"
        )
        verify(reconService).updateBundle(bundle)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET ambassador-payout-bundle with multiple payouts advances all to PROCESSING`() {
        val payout1 = ambassadorPayout(PayoutStage.PENDING)
        val payout2 = ambassadorPayout(PayoutStage.PENDING)
        val bundle = PayoutBundle(PayoutType.AMBASSADOR, listOf(payout1, payout2), "admin")
        given(reconService.getCurrentPayoutBundleForAmbassadors()).willReturn(bundle)

        mockMvc.perform(get("/reconcsv/ambassador-payout-bundle"))
            .andExpect(status().isOk)

        assertEquals(PayoutStage.PROCESSING, payout1.payoutStage)
        assertEquals(PayoutStage.PROCESSING, payout2.payoutStage)
        verify(reconService).updateBundle(bundle)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET ambassador-payout-bundle with empty payout list still calls updateBundle`() {
        val bundle = PayoutBundle(PayoutType.AMBASSADOR, emptyList<Payout>(), "admin")
        given(reconService.getCurrentPayoutBundleForAmbassadors()).willReturn(bundle)

        mockMvc.perform(get("/reconcsv/ambassador-payout-bundle"))
            .andExpect(status().isOk)

        verify(reconService).updateBundle(bundle)
    }

    // ---------------------------------------------------------------------------
    // referral-partner-payout-bundle — single payout advanced to PROCESSING
    // ---------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET referral-partner-payout-bundle advances payout to PROCESSING and calls updateBundle`() {
        val payout = referralPartnerPayout(PayoutStage.PENDING)
        val bundle = PayoutBundle(PayoutType.REFERRAL_PARTNER, listOf(payout), "admin")
        given(reconService.getCurrentPayoutBundleForReferralPartners()).willReturn(bundle)

        mockMvc.perform(get("/reconcsv/referral-partner-payout-bundle"))
            .andExpect(status().isOk)

        assertEquals(
            PayoutStage.PROCESSING, payout.payoutStage,
            "Payout must be PROCESSING after CSV export"
        )
        verify(reconService).updateBundle(bundle)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET referral-partner-payout-bundle with multiple payouts advances all to PROCESSING`() {
        val payout1 = referralPartnerPayout(PayoutStage.PENDING)
        val payout2 = referralPartnerPayout(PayoutStage.PENDING)
        val bundle = PayoutBundle(PayoutType.REFERRAL_PARTNER, listOf(payout1, payout2), "admin")
        given(reconService.getCurrentPayoutBundleForReferralPartners()).willReturn(bundle)

        mockMvc.perform(get("/reconcsv/referral-partner-payout-bundle"))
            .andExpect(status().isOk)

        assertEquals(PayoutStage.PROCESSING, payout1.payoutStage)
        assertEquals(PayoutStage.PROCESSING, payout2.payoutStage)
        verify(reconService).updateBundle(bundle)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET referral-partner-payout-bundle with empty payout list still calls updateBundle`() {
        val bundle = PayoutBundle(PayoutType.REFERRAL_PARTNER, emptyList<Payout>(), "admin")
        given(reconService.getCurrentPayoutBundleForReferralPartners()).willReturn(bundle)

        mockMvc.perform(get("/reconcsv/referral-partner-payout-bundle"))
            .andExpect(status().isOk)

        verify(reconService).updateBundle(bundle)
    }
}
