package io.curiousoft.izinga.usermanagement.referral

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommission
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.FurnitureCustomerReferralCommission
import io.curiousoft.izinga.commons.referral.FurnitureCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.ReferralCommissionStatus
import io.curiousoft.izinga.commons.referral.ReferralCommissionType
import io.curiousoft.izinga.commons.referral.StorePartnerStage1Commission
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage2Commission
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.util.Date
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ReferralPartnerDashboardServiceTest {

    @Mock lateinit var userProfileRepo: UserProfileRepo
    @Mock lateinit var foodCommissionRepo: FoodCustomerReferralCommissionRepo
    @Mock lateinit var furnitureCommissionRepo: FurnitureCustomerReferralCommissionRepo
    @Mock lateinit var stage1CommissionRepo: StorePartnerStage1CommissionRepo
    @Mock lateinit var stage2CommissionRepo: StorePartnerStage2CommissionRepo

    @InjectMocks lateinit var service: ReferralPartnerDashboardService

    private val partnerId = "rp-001"

    // ─── getSummary ────────────────────────────────────────────────────────────

    @Test
    fun `getSummary returns correct counts for partner with mixed referrals`() {
        val partner = referralPartner(partnerId)
        val customer1 = customer("c-1")
        val store1 = store("s-1")

        `when`(userProfileRepo.findById(partnerId)).thenReturn(Optional.of(partner))
        `when`(userProfileRepo.findByReferredByPartnerId(partnerId)).thenReturn(listOf(customer1, store1))
        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(foodCommission("c-1")))
        `when`(furnitureCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(stage1Commission("s-1")))
        `when`(stage2CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())

        val summary = service.getSummary(partnerId)

        assertEquals(partnerId, summary.partnerId)
        assertEquals("REFCODE1", summary.referralCode)
        assertEquals(1, summary.referralCounts.foodCustomers)
        assertEquals(0, summary.referralCounts.furnitureCustomers)
        assertEquals(1, summary.referralCounts.storePartners)
        assertEquals(1, summary.conversionCounts.foodCustomers)
        assertEquals(0, summary.conversionCounts.furnitureCustomers)
        assertEquals(1, summary.conversionCounts.storePartnersStage1)
        assertEquals(0, summary.conversionCounts.storePartnersStage2)
    }

    @Test
    fun `getSummary returns all zeros for partner with no referrals`() {
        val partner = referralPartner(partnerId)
        `when`(userProfileRepo.findById(partnerId)).thenReturn(Optional.of(partner))
        `when`(userProfileRepo.findByReferredByPartnerId(partnerId)).thenReturn(emptyList())
        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(furnitureCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage2CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())

        val summary = service.getSummary(partnerId)

        assertEquals(0, summary.referralCounts.foodCustomers)
        assertEquals(0, summary.referralCounts.furnitureCustomers)
        assertEquals(0, summary.referralCounts.storePartners)
        assertEquals(0, summary.conversionCounts.foodCustomers)
        assertEquals(0, summary.conversionCounts.furnitureCustomers)
        assertEquals(0, summary.conversionCounts.storePartnersStage1)
        assertEquals(0, summary.conversionCounts.storePartnersStage2)
    }

    @Test
    fun `getSummary throws when partner profile not found`() {
        `when`(userProfileRepo.findById(partnerId)).thenReturn(Optional.empty())

        assertThrows(IllegalStateException::class.java) {
            service.getSummary(partnerId)
        }
        verifyNoInteractions(foodCommissionRepo, stage1CommissionRepo, stage2CommissionRepo)
    }

    @Test
    fun `getSummary throws when profile is not REFERRAL_PARTNER role`() {
        val customer = customer("c-wrong")
        `when`(userProfileRepo.findById("c-wrong")).thenReturn(Optional.of(customer))

        assertThrows(IllegalArgumentException::class.java) {
            service.getSummary("c-wrong")
        }
    }

    @Test
    fun `getSummary auth scoping - partnerId from caller is used, never overridden`() {
        // Verifies that the service only queries data for the given partnerId
        val partner = referralPartner(partnerId)
        `when`(userProfileRepo.findById(partnerId)).thenReturn(Optional.of(partner))
        `when`(userProfileRepo.findByReferredByPartnerId(partnerId)).thenReturn(emptyList())
        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(furnitureCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage2CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())

        service.getSummary(partnerId)

        // Must never query another partner's data
        verify(userProfileRepo).findByReferredByPartnerId(partnerId)
        verify(foodCommissionRepo).findByReferralPartnerId(partnerId)
        verify(furnitureCommissionRepo).findByReferralPartnerId(partnerId)
        verify(stage1CommissionRepo).findByReferralPartnerId(partnerId)
        verify(stage2CommissionRepo).findByReferralPartnerId(partnerId)
        verify(userProfileRepo, never()).findByReferredByPartnerId("rp-other")
        verify(furnitureCommissionRepo, never()).findByReferralPartnerId("rp-other")
    }

    @Test
    fun `getSummary returns correct non-zero furniture counts when furniture commissions exist`() {
        val partner = referralPartner(partnerId)
        val customer1 = customer("c-1")

        `when`(userProfileRepo.findById(partnerId)).thenReturn(Optional.of(partner))
        `when`(userProfileRepo.findByReferredByPartnerId(partnerId)).thenReturn(listOf(customer1))
        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(furnitureCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(
            listOf(furnitureCommission("c-1"), furnitureCommission("c-2"))
        )
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage2CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())

        val summary = service.getSummary(partnerId)

        assertEquals(2, summary.referralCounts.furnitureCustomers)
        assertEquals(2, summary.conversionCounts.furnitureCustomers)
    }

    @Test
    fun `getSummary furniture counts are independent of food customer counts`() {
        val partner = referralPartner(partnerId)
        val customer1 = customer("c-1")
        val customer2 = customer("c-2")

        `when`(userProfileRepo.findById(partnerId)).thenReturn(Optional.of(partner))
        `when`(userProfileRepo.findByReferredByPartnerId(partnerId)).thenReturn(listOf(customer1, customer2))
        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(foodCommission("c-1")))
        `when`(furnitureCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(furnitureCommission("c-2")))
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage2CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())

        val summary = service.getSummary(partnerId)

        assertEquals(2, summary.referralCounts.foodCustomers)  // all referred CUSTOMER profiles
        assertEquals(1, summary.referralCounts.furnitureCustomers)
        assertEquals(1, summary.conversionCounts.foodCustomers)
        assertEquals(1, summary.conversionCounts.furnitureCustomers)
    }

    // ─── getReferrals ──────────────────────────────────────────────────────────

    @Test
    fun `getReferrals maps food customer with converted=true when commission exists`() {
        val customer1 = customer("c-1")
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdDate"))
        val page = PageImpl(listOf(customer1), pageable, 1)

        `when`(userProfileRepo.findByReferredByPartnerId(partnerId, pageable)).thenReturn(page)
        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(foodCommission("c-1")))
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())

        val result = service.getReferrals(partnerId, pageable)

        assertEquals(1, result.content.size)
        val item = result.content[0]
        assertEquals("c-1", item.customerId)
        assertEquals(ReferralType.FOOD_CUSTOMER, item.type)
        assertTrue(item.converted)
    }

    @Test
    fun `getReferrals maps food customer with converted=false when no commission`() {
        val customer1 = customer("c-1")
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdDate"))
        val page = PageImpl(listOf(customer1), pageable, 1)

        `when`(userProfileRepo.findByReferredByPartnerId(partnerId, pageable)).thenReturn(page)
        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())

        val result = service.getReferrals(partnerId, pageable)

        assertFalse(result.content[0].converted)
    }

    @Test
    fun `getReferrals maps store partner with converted=true when stage1 commission exists`() {
        val store1 = store("s-1")
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdDate"))
        val page = PageImpl(listOf(store1), pageable, 1)

        `when`(userProfileRepo.findByReferredByPartnerId(partnerId, pageable)).thenReturn(page)
        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(stage1Commission("s-1")))

        val result = service.getReferrals(partnerId, pageable)

        val item = result.content[0]
        assertEquals(ReferralType.STORE_PARTNER, item.type)
        assertTrue(item.converted)
    }

    @Test
    fun `getReferrals auth scoping - only queries data for the given partnerId`() {
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(emptyList<UserProfile>(), pageable, 0)

        `when`(userProfileRepo.findByReferredByPartnerId(partnerId, pageable)).thenReturn(page)
        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())

        service.getReferrals(partnerId, pageable)

        verify(userProfileRepo).findByReferredByPartnerId(partnerId, pageable)
        verify(userProfileRepo, never()).findByReferredByPartnerId("rp-other", pageable)
    }

    // ─── getCommissions ────────────────────────────────────────────────────────

    @Test
    fun `getCommissions returns correct totals across all commission types including furniture`() {
        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(foodCommission("c-1")))
        `when`(furnitureCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(furnitureCommission("c-2")))
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(stage1Commission("s-1")))
        `when`(stage2CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(stage2Commission("s-1")))

        val result = service.getCommissions(partnerId)

        // R15 (food) + R25 (furniture) + R100 (stage1) + R150 (stage2) = R290 pending
        assertEquals(BigDecimal("290.00"), result.totals.pending)
        assertEquals(BigDecimal.ZERO, result.totals.paid)
        assertEquals(4, result.lineItems.size)
    }

    @Test
    fun `getCommissions returns zero totals when no commissions exist`() {
        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(furnitureCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage2CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())

        val result = service.getCommissions(partnerId)

        assertEquals(BigDecimal.ZERO, result.totals.pending)
        assertEquals(BigDecimal.ZERO, result.totals.paid)
        assertTrue(result.lineItems.isEmpty())
    }

    @Test
    fun `getCommissions line items are sorted by createdAt descending`() {
        val older = foodCommission("c-1").copy(createdAt = Date(1000))
        val newer = foodCommission("c-2").copy(createdAt = Date(2000), customerId = "c-2")

        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(older, newer))
        `when`(furnitureCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage2CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())

        val result = service.getCommissions(partnerId)

        assertEquals("c-2", result.lineItems[0].triggerReferenceId)
        assertEquals("c-1", result.lineItems[1].triggerReferenceId)
    }

    @Test
    fun `getCommissions auth scoping - only queries data for the given partnerId`() {
        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(furnitureCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage2CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())

        service.getCommissions(partnerId)

        verify(foodCommissionRepo).findByReferralPartnerId(partnerId)
        verify(furnitureCommissionRepo).findByReferralPartnerId(partnerId)
        verify(stage1CommissionRepo).findByReferralPartnerId(partnerId)
        verify(stage2CommissionRepo).findByReferralPartnerId(partnerId)
        verify(foodCommissionRepo, never()).findByReferralPartnerId("rp-other")
        verify(furnitureCommissionRepo, never()).findByReferralPartnerId("rp-other")
    }

    @Test
    fun `getCommissions line item types map to correct ReferralCommissionType including furniture`() {
        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(foodCommission("c-1")))
        `when`(furnitureCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(furnitureCommission("c-2")))
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(stage1Commission("s-1")))
        `when`(stage2CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(stage2Commission("s-1")))

        val result = service.getCommissions(partnerId)

        val types = result.lineItems.map { it.commissionType }.toSet()
        assertTrue(types.contains(ReferralCommissionType.FOOD_CUSTOMER_REFERRAL))
        assertTrue(types.contains(ReferralCommissionType.FURNITURE_CUSTOMER_REFERRAL))
        assertTrue(types.contains(ReferralCommissionType.STORE_PARTNER_STAGE_1))
        assertTrue(types.contains(ReferralCommissionType.STORE_PARTNER_STAGE_2))
    }

    @Test
    fun `getCommissions buckets furniture PENDING and PAID into separate totals`() {
        val pending = furnitureCommission("c-1")  // R25.00 PENDING
        val paid = furnitureCommission("c-2").copy(
            id = "fc-furn-c-2",
            customerId = "c-2",
            status = ReferralCommissionStatus.PAID
        )
        `when`(foodCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(furnitureCommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(listOf(pending, paid))
        `when`(stage1CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())
        `when`(stage2CommissionRepo.findByReferralPartnerId(partnerId)).thenReturn(emptyList())

        val result = service.getCommissions(partnerId)

        assertEquals(BigDecimal("25.00"), result.totals.pending)
        assertEquals(BigDecimal("25.00"), result.totals.paid)
        assertEquals(2, result.lineItems.size)
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun referralPartner(id: String): UserProfile {
        val p = UserProfile("Test Partner", UserProfile.SignUpReason.BUY,
            "1 Main St", "https://img.test/p.png", "+27820000001", ProfileRoles.REFERRAL_PARTNER)
        p.id = id
        p.referralCode = "REFCODE1"
        return p
    }

    private fun customer(id: String): UserProfile {
        val p = UserProfile("Customer $id", UserProfile.SignUpReason.BUY,
            "2 Main St", "https://img.test/c.png", "+27820000002", ProfileRoles.CUSTOMER)
        p.id = id
        p.referredByPartnerId = partnerId
        return p
    }

    private fun store(id: String): UserProfile {
        val p = UserProfile("Store $id", UserProfile.SignUpReason.SELL,
            "3 Main St", "https://img.test/s.png", "+27820000003", ProfileRoles.STORE)
        p.id = id
        p.referredByPartnerId = partnerId
        return p
    }

    private fun foodCommission(customerId: String) = FoodCustomerReferralCommission(
        id = "fc-$customerId",
        customerId = customerId,
        referralPartnerId = partnerId,
        triggeringOrderId = "order-1",
        amount = BigDecimal("15.00"),
        status = ReferralCommissionStatus.PENDING,
        createdAt = Date()
    )

    private fun stage1Commission(storeId: String) = StorePartnerStage1Commission(
        id = "s1-$storeId",
        storeId = storeId,
        referralPartnerId = partnerId,
        amount = BigDecimal("100.00"),
        status = ReferralCommissionStatus.PENDING,
        createdAt = Date()
    )

    private fun stage2Commission(storeId: String) = StorePartnerStage2Commission(
        id = "s2-$storeId",
        storeId = storeId,
        referralPartnerId = partnerId,
        triggeringOrderId = "order-2",
        amount = BigDecimal("150.00"),
        status = ReferralCommissionStatus.PENDING,
        createdAt = Date()
    )

    private fun furnitureCommission(customerId: String) = FurnitureCustomerReferralCommission(
        id = "fc-furn-$customerId",
        customerId = customerId,
        referralPartnerId = partnerId,
        triggeringOrderId = "order-furn-1",
        amount = BigDecimal("25.00"),
        status = ReferralCommissionStatus.PENDING,
        createdAt = Date()
    )
}
