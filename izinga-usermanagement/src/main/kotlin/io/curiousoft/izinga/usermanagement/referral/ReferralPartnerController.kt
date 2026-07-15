package io.curiousoft.izinga.usermanagement.referral

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

/**
 * RP-010: Read-only dashboard endpoints for authenticated Referral Partners.
 *
 * All endpoints are scoped to the authenticated partner. The partnerId is always
 * derived from the JWT principal (authentication.name = Firebase UID = profile id).
 * It is NEVER accepted as a request parameter — this is the primary auth scoping
 * invariant enforced here.
 *
 * Security: @PreAuthorize("hasRole('REFERRAL_PARTNER')") blocks any non-partner
 * caller at the method level, before the service layer is reached.
 */
@RestController
@RequestMapping("/referral-partner/me")
class ReferralPartnerController(
    private val dashboardService: ReferralPartnerDashboardService
) {

    private val log = LoggerFactory.getLogger(ReferralPartnerController::class.java)

    /**
     * GET /referral-partner/me/summary
     *
     * Returns the partner's referral code, referral counts, and conversion counts.
     * furnitureCustomers will be 0 until RP-012 ships.
     */
    @PreAuthorize("hasRole('REFERRAL_PARTNER')")
    @GetMapping("/summary")
    fun getSummary(principal: Principal): ResponseEntity<ReferralPartnerSummary> {
        val partnerId = principal.name
        log.info("Dashboard summary request partnerId={}", partnerId)
        val summary = dashboardService.getSummary(partnerId)
        return ResponseEntity.ok(summary)
    }

    /**
     * GET /referral-partner/me/referrals?page=0&size=20
     *
     * Paginated list of UserProfile records referred by this partner.
     * Defaults: page=0, size=20, sorted by createdDate descending.
     */
    @PreAuthorize("hasRole('REFERRAL_PARTNER')")
    @GetMapping("/referrals")
    fun getReferrals(
        principal: Principal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Page<ReferralItem>> {
        val partnerId = principal.name
        log.info("Dashboard referrals request partnerId={} page={} size={}", partnerId, page, size)
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"))
        val result = dashboardService.getReferrals(partnerId, pageable)
        return ResponseEntity.ok(result)
    }

    /**
     * GET /referral-partner/me/commissions
     *
     * Aggregated commission view across all three commission types (RP-006/007/008).
     * Returns totals by status and a flat line-item list, sorted by createdAt descending.
     */
    @PreAuthorize("hasRole('REFERRAL_PARTNER')")
    @GetMapping("/commissions")
    fun getCommissions(principal: Principal): ResponseEntity<CommissionSummary> {
        val partnerId = principal.name
        log.info("Dashboard commissions request partnerId={}", partnerId)
        val summary = dashboardService.getCommissions(partnerId)
        return ResponseEntity.ok(summary)
    }
}
