package io.curiousoft.izinga.usermanagement.referral

import io.curiousoft.izinga.commons.referral.ReferralCommissionStatus
import io.curiousoft.izinga.commons.referral.ReferralCommissionType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.security.Principal
import java.util.Date

/**
 * RP-010: Controller tests for ReferralPartnerController.
 *
 * The most critical thing tested here is auth scoping: the controller must use
 * principal.name as the partnerId and never accept it from a request parameter.
 */
@ExtendWith(MockitoExtension::class)
class ReferralPartnerControllerTest {

    @Mock lateinit var dashboardService: ReferralPartnerDashboardService

    private lateinit var controller: ReferralPartnerController

    private val partnerId = "rp-001"
    private val principal = Principal { partnerId }

    @BeforeEach
    fun setUp() {
        controller = ReferralPartnerController(dashboardService)
    }

    // ─── getSummary ────────────────────────────────────────────────────────────

    @Test
    fun `getSummary returns 200 with summary for authenticated partner`() {
        val summary = summaryResponse()
        `when`(dashboardService.getSummary(partnerId)).thenReturn(summary)

        val response = controller.getSummary(principal)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(partnerId, response.body?.partnerId)
        verify(dashboardService).getSummary(partnerId)
    }

    @Test
    fun `getSummary derives partnerId from principal never from request param`() {
        val summary = summaryResponse()
        `when`(dashboardService.getSummary(partnerId)).thenReturn(summary)

        controller.getSummary(principal)

        verify(dashboardService).getSummary(partnerId)
        verify(dashboardService, never()).getSummary("rp-other")
    }

    @Test
    fun `getSummary furnitureCustomers is always 0 until RP-012 ships`() {
        val summary = summaryResponse()
        `when`(dashboardService.getSummary(partnerId)).thenReturn(summary)

        val response = controller.getSummary(principal)

        assertEquals(0, response.body?.referralCounts?.furnitureCustomers)
        assertEquals(0, response.body?.conversionCounts?.furnitureCustomers)
    }

    // ─── getReferrals ──────────────────────────────────────────────────────────

    @Test
    fun `getReferrals returns 200 paginated response`() {
        val items = listOf(referralItem("c-1", ReferralType.FOOD_CUSTOMER, false))
        val page = PageImpl(items)
        val expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdDate"))

        // Use exact values to avoid Kotlin null-check issues with Mockito any() for non-null Pageable
        `when`(dashboardService.getReferrals(partnerId, expectedPageable)).thenReturn(page)

        val response = controller.getReferrals(principal, 0, 20)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, response.body?.content?.size)
    }

    @Test
    fun `getReferrals uses default page and size when not provided`() {
        val page = PageImpl(emptyList<ReferralItem>())
        val expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdDate"))

        `when`(dashboardService.getReferrals(partnerId, expectedPageable)).thenReturn(page)

        val response = controller.getReferrals(principal, 0, 20)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body?.content?.isEmpty() == true)
    }

    @Test
    fun `getReferrals derives partnerId from principal never from request param`() {
        val page = PageImpl(emptyList<ReferralItem>())
        val expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdDate"))

        `when`(dashboardService.getReferrals(partnerId, expectedPageable)).thenReturn(page)

        controller.getReferrals(principal, 0, 20)

        // Verify the service was called with the principal's partnerId, not "rp-other"
        verify(dashboardService).getReferrals(partnerId, expectedPageable)
        verify(dashboardService, never()).getReferrals("rp-other", expectedPageable)
    }

    // ─── getCommissions ────────────────────────────────────────────────────────

    @Test
    fun `getCommissions returns 200 with commission summary`() {
        val summary = commissionSummary()
        `when`(dashboardService.getCommissions(partnerId)).thenReturn(summary)

        val response = controller.getCommissions(principal)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body?.totals)
        assertNotNull(response.body?.lineItems)
        verify(dashboardService).getCommissions(partnerId)
    }

    @Test
    fun `getCommissions derives partnerId from principal never from request param`() {
        val summary = commissionSummary()
        `when`(dashboardService.getCommissions(partnerId)).thenReturn(summary)

        controller.getCommissions(principal)

        verify(dashboardService).getCommissions(partnerId)
        verify(dashboardService, never()).getCommissions("rp-other")
    }

    @Test
    fun `getCommissions totals reflect pending and paid amounts`() {
        val summary = CommissionSummary(
            totals = CommissionTotals(pending = BigDecimal("265.00"), paid = BigDecimal("15.00")),
            lineItems = emptyList()
        )
        `when`(dashboardService.getCommissions(partnerId)).thenReturn(summary)

        val response = controller.getCommissions(principal)

        assertEquals(BigDecimal("265.00"), response.body?.totals?.pending)
        assertEquals(BigDecimal("15.00"), response.body?.totals?.paid)
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun summaryResponse() = ReferralPartnerSummary(
        partnerId = partnerId,
        referralCode = "REFCODE1",
        referralCounts = ReferralCounts(foodCustomers = 2, furnitureCustomers = 0, storePartners = 1),
        conversionCounts = ConversionCounts(foodCustomers = 1, furnitureCustomers = 0, storePartnersStage1 = 1, storePartnersStage2 = 0)
    )

    private fun referralItem(id: String, type: ReferralType, converted: Boolean) = ReferralItem(
        customerId = id,
        name = "Test $id",
        referredAt = Date(),
        type = type,
        converted = converted
    )

    private fun commissionSummary() = CommissionSummary(
        totals = CommissionTotals(pending = BigDecimal("15.00"), paid = BigDecimal.ZERO),
        lineItems = listOf(
            CommissionLineItem(
                commissionType = ReferralCommissionType.FOOD_CUSTOMER_REFERRAL,
                amount = BigDecimal("15.00"),
                status = ReferralCommissionStatus.PENDING,
                triggerReferenceId = "c-1",
                createdAt = Date()
            )
        )
    )
}
