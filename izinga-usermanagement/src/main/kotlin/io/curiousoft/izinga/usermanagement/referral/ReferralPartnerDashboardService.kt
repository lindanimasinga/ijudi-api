package io.curiousoft.izinga.usermanagement.referral

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.ReferralCommissionStatus
import io.curiousoft.izinga.commons.referral.ReferralCommissionType
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.Date

/**
 * RP-010: Read-only aggregation service for the Referral Partner Dashboard.
 *
 * All methods are scoped to the authenticated partner — the partnerId MUST come from
 * the JWT principal (authentication.name), never from a request parameter.
 *
 * Auth scoping invariant: a partner must never see another partner's data.
 * This is enforced at the controller layer via @PreAuthorize and confirmed by
 * this service never accepting a partnerId that differs from the principal.
 */
@Service
class ReferralPartnerDashboardService(
    private val userProfileRepo: UserProfileRepo,
    private val foodCommissionRepo: FoodCustomerReferralCommissionRepo,
    private val stage1CommissionRepo: StorePartnerStage1CommissionRepo,
    private val stage2CommissionRepo: StorePartnerStage2CommissionRepo
) {

    private val log = LoggerFactory.getLogger(ReferralPartnerDashboardService::class.java)

    /**
     * Returns the summary payload for GET /referral-partner/me/summary.
     *
     * furnitureCustomers counts are 0 — RP-012 is not built yet. The fields exist
     * so the API shape is stable and frontends can render them without a contract break
     * when RP-012 ships.
     */
    fun getSummary(partnerId: String): ReferralPartnerSummary {
        val partner = userProfileRepo.findById(partnerId).orElseThrow {
            IllegalStateException("Authenticated partner not found in UserProfile store: $partnerId")
        }
        require(partner.role == ProfileRoles.REFERRAL_PARTNER) {
            "Profile $partnerId is not a REFERRAL_PARTNER (role=${partner.role})"
        }

        val referrals = userProfileRepo.findByReferredByPartnerId(partnerId)
        val foodCustomerCount = referrals.count { it.role == ProfileRoles.CUSTOMER }
        val storePartnerCount = referrals.count { it.role == ProfileRoles.STORE }

        val foodCommissions = foodCommissionRepo.findByReferralPartnerId(partnerId)
        val stage1Commissions = stage1CommissionRepo.findByReferralPartnerId(partnerId)
        val stage2Commissions = stage2CommissionRepo.findByReferralPartnerId(partnerId)

        // Conversion = a commission record exists for that customer / store
        val convertedFoodCustomers = foodCommissions.size
        val convertedStoreStage1 = stage1Commissions.size
        val convertedStoreStage2 = stage2Commissions.size

        log.debug(
            "Dashboard summary partnerId={} foodCustomers={} storePartners={} convertedFood={} stage1={} stage2={}",
            partnerId, foodCustomerCount, storePartnerCount, convertedFoodCustomers, convertedStoreStage1, convertedStoreStage2
        )

        return ReferralPartnerSummary(
            partnerId = partnerId,
            referralCode = partner.referralCode,
            referralCounts = ReferralCounts(
                foodCustomers = foodCustomerCount,
                furnitureCustomers = 0,   // RP-012 not built yet
                storePartners = storePartnerCount
            ),
            conversionCounts = ConversionCounts(
                foodCustomers = convertedFoodCustomers,
                furnitureCustomers = 0,   // RP-012 not built yet
                storePartnersStage1 = convertedStoreStage1,
                storePartnersStage2 = convertedStoreStage2
            )
        )
    }

    /**
     * Returns a paginated list of UserProfile records referred by this partner.
     * Each is mapped to a ReferralItem — callers should not expose the raw UserProfile.
     */
    fun getReferrals(partnerId: String, pageable: Pageable): Page<ReferralItem> {
        val referralPage = userProfileRepo.findByReferredByPartnerId(partnerId, pageable)

        val foodCommissionCustomerIds = foodCommissionRepo
            .findByReferralPartnerId(partnerId)
            .map { it.customerId }
            .toSet()

        val stage1StoreIds = stage1CommissionRepo
            .findByReferralPartnerId(partnerId)
            .map { it.storeId }
            .toSet()

        return referralPage.map { profile ->
            val type = if (profile.role == ProfileRoles.STORE) ReferralType.STORE_PARTNER else ReferralType.FOOD_CUSTOMER
            val converted = when (type) {
                ReferralType.FOOD_CUSTOMER -> profile.id != null && foodCommissionCustomerIds.contains(profile.id)
                ReferralType.STORE_PARTNER -> profile.id != null && stage1StoreIds.contains(profile.id)
            }
            ReferralItem(
                customerId = profile.id ?: "",
                name = profile.name ?: "",
                referredAt = profile.createdDate,
                type = type,
                converted = converted
            )
        }
    }

    /**
     * Aggregates all commission records across the three commission types for this partner.
     * Returns totals by status and a flat line-item list ordered by createdAt descending.
     */
    fun getCommissions(partnerId: String): CommissionSummary {
        val foodCommissions = foodCommissionRepo.findByReferralPartnerId(partnerId)
        val stage1Commissions = stage1CommissionRepo.findByReferralPartnerId(partnerId)
        val stage2Commissions = stage2CommissionRepo.findByReferralPartnerId(partnerId)

        val lineItems = mutableListOf<CommissionLineItem>()

        foodCommissions.forEach { c ->
            lineItems += CommissionLineItem(
                commissionType = ReferralCommissionType.FOOD_CUSTOMER_REFERRAL,
                amount = c.amount,
                status = c.status,
                triggerReferenceId = c.customerId,
                createdAt = c.createdAt
            )
        }
        stage1Commissions.forEach { c ->
            lineItems += CommissionLineItem(
                commissionType = ReferralCommissionType.STORE_PARTNER_STAGE_1,
                amount = c.amount,
                status = c.status,
                triggerReferenceId = c.storeId,
                createdAt = c.createdAt
            )
        }
        stage2Commissions.forEach { c ->
            lineItems += CommissionLineItem(
                commissionType = ReferralCommissionType.STORE_PARTNER_STAGE_2,
                amount = c.amount,
                status = c.status,
                triggerReferenceId = c.storeId,
                createdAt = c.createdAt
            )
        }

        lineItems.sortByDescending { it.createdAt }

        val pendingTotal = lineItems
            .filter { it.status == ReferralCommissionStatus.PENDING }
            .fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }

        val paidTotal = lineItems
            .filter { it.status == ReferralCommissionStatus.PAID }
            .fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }

        return CommissionSummary(
            totals = CommissionTotals(pending = pendingTotal, paid = paidTotal),
            lineItems = lineItems
        )
    }
}

// ─── Response DTOs ─────────────────────────────────────────────────────────────

data class ReferralPartnerSummary(
    val partnerId: String,
    val referralCode: String?,
    val referralCounts: ReferralCounts,
    val conversionCounts: ConversionCounts
)

data class ReferralCounts(
    val foodCustomers: Int,
    val furnitureCustomers: Int,
    val storePartners: Int
)

data class ConversionCounts(
    val foodCustomers: Int,
    val furnitureCustomers: Int,
    val storePartnersStage1: Int,
    val storePartnersStage2: Int
)

data class ReferralItem(
    val customerId: String,
    val name: String,
    val referredAt: Date,
    val type: ReferralType,
    val converted: Boolean
)

enum class ReferralType {
    FOOD_CUSTOMER,
    STORE_PARTNER
}

data class CommissionSummary(
    val totals: CommissionTotals,
    val lineItems: List<CommissionLineItem>
)

data class CommissionTotals(
    val pending: BigDecimal,
    val paid: BigDecimal
)

data class CommissionLineItem(
    val commissionType: ReferralCommissionType,
    val amount: BigDecimal,
    val status: ReferralCommissionStatus,
    val triggerReferenceId: String,
    val createdAt: Date
)
