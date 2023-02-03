package io.curiousoft.izinga.recon

import io.curiousoft.izinga.recon.payout.PayoutBundle
import io.curiousoft.izinga.recon.payout.PayoutBundleResults
import io.curiousoft.izinga.recon.payout.PayoutType
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.*

@RestController
@RequestMapping("/recon")
class ReconController(val reconService: ReconService) {

    @GetMapping("/shopPayoutBundle")
    fun shopPayoutBundle() = reconService.generateNextPayoutsToShop()

    @PatchMapping("/shopPayoutBundle")
    fun shopPayoutBundle(@RequestBody payoutResults: PayoutBundleResults) = reconService.updatePayoutStatus(payoutResults)

    @GetMapping("/messengerPayoutBundle")
    fun messengerPayoutBundle() = reconService.generateNextPayoutsToMessenger()

    @PatchMapping("/messengerPayoutBundle")
    fun messengerPayoutBundle(@RequestBody payoutResults: PayoutBundleResults) = reconService.updatePayoutStatus(payoutResults)

    @GetMapping("/payoutBundle")
    fun getAllPayouts(@RequestParam payoutType: PayoutType, @RequestParam from: Date,
                      @RequestParam toDate: Date): List<PayoutBundle> = reconService.getAllPayouts(payoutType, from, toDate)

    @GetMapping("/payoutBundle/{payoutId}")
    fun getPayouts(@PathVariable payoutId: String): PayoutBundle? = reconService.findPayout(payoutId)
}