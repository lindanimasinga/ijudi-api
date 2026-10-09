package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.FurnitureCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.ambassador.AmbassadorProperties
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.PayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal

/**
 * Unit tests for [ReconServiceImpl.resolveAmbassadorCommission].
 *
 * Covers ALL branches of the when expression:
 *   - null description  → fallback rate + UNKNOWN
 *   - blank description → fallback rate + UNKNOWN
 *   - "bike" (case-insensitive) → Bike tier
 *   - "small" (case-insensitive) → Car tier
 *   - "medium" (case-insensitive) → Car tier
 *   - "bakkie" (case-insensitive) → Bakkie tier
 *   - "truck" (case-insensitive) → Truck tier
 *   - unrecognised string → fallback rate + UNKNOWN
 */
class ResolveAmbassadorCommissionTest {

    private val ambassadorProperties = AmbassadorProperties(
        commissionAmount = BigDecimal("70.00"),
        commissionAmountBike = BigDecimal("50.00"),
        commissionAmountCar = BigDecimal("60.00"),
        commissionAmountBakkie = BigDecimal("70.00"),
        commissionAmountTruck = BigDecimal("70.00")
    )

    private lateinit var sut: ReconServiceImpl

    @BeforeEach
    fun setUp() {
        sut = ReconServiceImpl(
            storeRepo = mockk<StoreRepository>(),
            userProfileRepo = mockk<UserProfileRepo>(),
            shopPayoutRepo = mockk<ShopPayoutRepository>(),
            messengerPayoutRepository = mockk<MessengerPayoutRepository>(),
            ambassadorPayoutRepository = mockk<AmbassadorPayoutRepository>(),
            referralPartnerPayoutRepository = mockk<ReferralPartnerPayoutRepository>(),
            payoutBundleRepository = mockk<PayoutRepository>(),
            foodCustomerCommissionRepo = mockk<FoodCustomerReferralCommissionRepo>(),
            furnitureCustomerCommissionRepo = mockk<FurnitureCustomerReferralCommissionRepo>(),
            storeStage1CommissionRepo = mockk<StorePartnerStage1CommissionRepo>(),
            storeStage2CommissionRepo = mockk<StorePartnerStage2CommissionRepo>(),
            applicationEventPublisher = mockk<ApplicationEventPublisher>(),
            ambassadorProperties = ambassadorProperties
        )
    }

    private fun makeDriver(description: String?): UserProfile =
        UserProfile(
            "Test Driver",
            UserProfile.SignUpReason.DELIVERY_DRIVER,
            "1 Driver St",
            "img.jpg",
            "0831111111",
            ProfileRoles.MESSENGER
        ).also {
            it.id = "driver-test"
            it.description = description
        }

    // ── null description ──────────────────────────────────────────────────────

    @Test
    fun `null description returns fallback commission and UNKNOWN vehicle type`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver(null))
        assertEquals(BigDecimal("70.00"), amount)
        assertEquals("UNKNOWN", vehicleType)
    }

    // ── blank description ─────────────────────────────────────────────────────

    @Test
    fun `blank description returns fallback commission and UNKNOWN vehicle type`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver("   "))
        assertEquals(BigDecimal("70.00"), amount)
        assertEquals("UNKNOWN", vehicleType)
    }

    @Test
    fun `empty string description returns fallback commission and UNKNOWN vehicle type`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver(""))
        assertEquals(BigDecimal("70.00"), amount)
        assertEquals("UNKNOWN", vehicleType)
    }

    // ── Bike tier ─────────────────────────────────────────────────────────────

    @Test
    fun `description containing bike (lowercase) returns Bike tier at R50`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver("Bike Delivery Driver"))
        assertEquals(BigDecimal("50.00"), amount)
        assertEquals("BIKE", vehicleType)
    }

    @Test
    fun `description containing BIKE (uppercase) returns Bike tier at R50`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver("BIKE DELIVERY DRIVER"))
        assertEquals(BigDecimal("50.00"), amount)
        assertEquals("BIKE", vehicleType)
    }

    @Test
    fun `description containing mixed-case BiKe returns Bike tier at R50`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver("BiKe rider"))
        assertEquals(BigDecimal("50.00"), amount)
        assertEquals("BIKE", vehicleType)
    }

    // ── Car tier (SMALL) ──────────────────────────────────────────────────────

    @Test
    fun `description containing small (lowercase) returns Car tier at R60`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver("Small Vehicle Driver"))
        assertEquals(BigDecimal("60.00"), amount)
        assertEquals("CAR", vehicleType)
    }

    @Test
    fun `description containing SMALL (uppercase) returns Car tier at R60`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver("SMALL/MEDIUM VEHICLE DRIVER"))
        assertEquals(BigDecimal("60.00"), amount)
        assertEquals("CAR", vehicleType)
    }

    // ── Car tier (MEDIUM) ─────────────────────────────────────────────────────

    @Test
    fun `description containing medium (lowercase) returns Car tier at R60`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver("Medium Vehicle Driver"))
        assertEquals(BigDecimal("60.00"), amount)
        assertEquals("CAR", vehicleType)
    }

    @Test
    fun `description containing MEDIUM (uppercase) returns Car tier at R60`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver("MEDIUM VEHICLE DRIVER"))
        assertEquals(BigDecimal("60.00"), amount)
        assertEquals("CAR", vehicleType)
    }

    // ── Bakkie tier ───────────────────────────────────────────────────────────

    @Test
    fun `description containing bakkie (lowercase) returns Bakkie tier at R70`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver("Bakkie Delivery Driver"))
        assertEquals(BigDecimal("70.00"), amount)
        assertEquals("BAKKIE", vehicleType)
    }

    @Test
    fun `description containing BAKKIE (uppercase) returns Bakkie tier at R70`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver("BAKKIE DELIVERY DRIVER"))
        assertEquals(BigDecimal("70.00"), amount)
        assertEquals("BAKKIE", vehicleType)
    }

    // ── Truck tier ────────────────────────────────────────────────────────────

    @Test
    fun `description containing truck (lowercase) returns Truck tier at R70`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver("Truck Delivery Driver"))
        assertEquals(BigDecimal("70.00"), amount)
        assertEquals("TRUCK", vehicleType)
    }

    @Test
    fun `description containing TRUCK (uppercase) returns Truck tier at R70`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver("TRUCK DELIVERY DRIVER"))
        assertEquals(BigDecimal("70.00"), amount)
        assertEquals("TRUCK", vehicleType)
    }

    // ── Unrecognised description ──────────────────────────────────────────────

    @Test
    fun `unrecognised description returns fallback commission and UNKNOWN vehicle type`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver("Scooter Rider"))
        assertEquals(BigDecimal("70.00"), amount)
        assertEquals("UNKNOWN", vehicleType)
    }

    @Test
    fun `description with only punctuation is unrecognised and returns fallback`() {
        val (amount, vehicleType) = sut.resolveAmbassadorCommission(makeDriver("???"))
        assertEquals(BigDecimal("70.00"), amount)
        assertEquals("UNKNOWN", vehicleType)
    }

    // ── Custom property overrides ─────────────────────────────────────────────

    @Test
    fun `custom property values are respected for Bike tier`() {
        val customSut = ReconServiceImpl(
            storeRepo = mockk<StoreRepository>(),
            userProfileRepo = mockk<UserProfileRepo>(),
            shopPayoutRepo = mockk<ShopPayoutRepository>(),
            messengerPayoutRepository = mockk<MessengerPayoutRepository>(),
            ambassadorPayoutRepository = mockk<AmbassadorPayoutRepository>(),
            referralPartnerPayoutRepository = mockk<ReferralPartnerPayoutRepository>(),
            payoutBundleRepository = mockk<PayoutRepository>(),
            foodCustomerCommissionRepo = mockk<FoodCustomerReferralCommissionRepo>(),
            furnitureCustomerCommissionRepo = mockk<FurnitureCustomerReferralCommissionRepo>(),
            storeStage1CommissionRepo = mockk<StorePartnerStage1CommissionRepo>(),
            storeStage2CommissionRepo = mockk<StorePartnerStage2CommissionRepo>(),
            applicationEventPublisher = mockk<ApplicationEventPublisher>(),
            ambassadorProperties = AmbassadorProperties(
                commissionAmount = BigDecimal("100.00"),
                commissionAmountBike = BigDecimal("45.00"),
                commissionAmountCar = BigDecimal("55.00"),
                commissionAmountBakkie = BigDecimal("75.00"),
                commissionAmountTruck = BigDecimal("80.00")
            )
        )
        val (bikeAmount, _) = customSut.resolveAmbassadorCommission(makeDriver("bike delivery"))
        assertEquals(BigDecimal("45.00"), bikeAmount)

        val (carAmount, _) = customSut.resolveAmbassadorCommission(makeDriver("small vehicle"))
        assertEquals(BigDecimal("55.00"), carAmount)

        val (bakkieAmount, _) = customSut.resolveAmbassadorCommission(makeDriver("bakkie"))
        assertEquals(BigDecimal("75.00"), bakkieAmount)

        val (truckAmount, _) = customSut.resolveAmbassadorCommission(makeDriver("truck"))
        assertEquals(BigDecimal("80.00"), truckAmount)

        val (fallbackAmount, fallbackType) = customSut.resolveAmbassadorCommission(makeDriver(null))
        assertEquals(BigDecimal("100.00"), fallbackAmount)
        assertEquals("UNKNOWN", fallbackType)
    }
}
