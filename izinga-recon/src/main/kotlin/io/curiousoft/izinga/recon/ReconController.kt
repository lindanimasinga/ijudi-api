package io.curiousoft.izinga.recon

import io.curiousoft.izinga.recon.payout.Payout
import io.curiousoft.izinga.recon.payout.PayoutBundleResults
import io.curiousoft.izinga.recon.payout.PayoutType
import io.curiousoft.izinga.recon.payout.ReferralPartnerPayout
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.*

@RestController
@RequestMapping("/recon")
class ReconController(
    val reconService: ReconService,
    val referralPartnerPayoutRepository: ReferralPartnerPayoutRepository
) {

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/shopPayoutBundle")
    fun shopPayoutBundle() = reconService.getCurrentPayoutBundleForShops()

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/shopPayoutBundle")
    fun shopPayoutBundle(@RequestBody payoutResults: PayoutBundleResults) = reconService.updatePayoutStatus(payoutResults)

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/messengerPayoutBundle")
    fun messengerPayoutBundle() = reconService.getCurrentPayoutBundleForMessenger()

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/messengerPayoutBundle")
    fun messengerPayoutBundle(@RequestBody payoutResults: PayoutBundleResults) = reconService.updatePayoutStatus(payoutResults)

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ambassadorPayoutBundle")
    fun ambassadorPayoutBundle() = reconService.getCurrentPayoutBundleForAmbassadors()

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/ambassadorPayoutBundle")
    fun ambassadorPayoutBundle(@RequestBody payoutResults: PayoutBundleResults) = reconService.updatePayoutStatus(payoutResults)

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/referralPartnerPayoutBundle")
    fun referralPartnerPayoutBundle() = reconService.getCurrentPayoutBundleForReferralPartners()

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/referralPartnerPayoutBundle")
    fun referralPartnerPayoutBundle(@RequestBody payoutResults: PayoutBundleResults) = reconService.updatePayoutStatus(payoutResults)

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/payoutBundle")
    fun getAllPayoutBundles(@RequestParam payoutType: PayoutType,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) toDate: Date): List<Payout> = reconService.getAllPayoutBundles(payoutType, fromDate, toDate)

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/payout")
    fun getAllPayouts(@RequestParam payoutType: PayoutType,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: Date,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) toDate: Date,
                      @RequestParam(required = false) toId: String?,
                      @RequestParam(required = false) messengerAdminId: String?,
                      @RequestParam(required = false) messengerId: String?): List<Payout> {
        return if (payoutType == PayoutType.MESSENGER && !messengerAdminId.isNullOrBlank()) {
            reconService.getAllPayoutsForMessengerAdmin(fromDate, toDate, messengerAdminId, messengerId)
        } else {
            if (toId.isNullOrBlank()) {
                throw IllegalArgumentException("toId is required when messengerAdminId is not provided")
            }
            reconService.getAllPayouts(payoutType, fromDate, toDate, toId)
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/payoutBundle/{bundleId}/payout/{payoutId}")
    fun getPayouts(@PathVariable bundleId: String, @PathVariable payoutId: String): Payout? = reconService.findPayout(bundleId, payoutId)

    /**
     * RP-010: GET /recon/referral-partner/me/payouts?page=0&size=20
     *
     * Returns paginated ReferralPartnerPayout records for the authenticated partner,
     * ordered by modifiedDate descending.
     *
     * The partnerId is derived from the JWT principal — never from a request param.
     * @PreAuthorize("hasRole('REFERRAL_PARTNER')") ensures only referral partners
     * can reach this endpoint, and partnerId = principal.name scopes to their own data.
     */
    @PreAuthorize("hasRole('REFERRAL_PARTNER')")
    @GetMapping("/referral-partner/me/payouts")
    fun getReferralPartnerPayouts(
        principal: Principal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): Page<ReferralPartnerPayout> {
        val partnerId = principal.name
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "modifiedDate"))
        return referralPartnerPayoutRepository.findAllByToId(partnerId, pageable)
    }
}