package io.curiousoft.izinga.recon

import io.curiousoft.izinga.recon.payout.Payout
import io.curiousoft.izinga.recon.payout.PayoutBundle
import io.curiousoft.izinga.recon.payout.PayoutBundleResults
import io.curiousoft.izinga.recon.payout.PayoutType
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/recon")
class ReconController(val reconService: ReconService) {

    @GetMapping("/shopPayoutBundle")
    fun shopPayoutBundle() = reconService.getCurrentPayoutBundleForShops()

    @PatchMapping("/shopPayoutBundle")
    fun shopPayoutBundle(@RequestBody payoutResults: PayoutBundleResults) = reconService.updatePayoutStatus(payoutResults)

    @GetMapping("/messengerPayoutBundle")
    fun messengerPayoutBundle() = reconService.getCurrentPayoutBundleForMessenger()

    @PatchMapping("/messengerPayoutBundle")
    fun messengerPayoutBundle(@RequestBody payoutResults: PayoutBundleResults) = reconService.updatePayoutStatus(payoutResults)

    @GetMapping("/payoutBundle")
    fun getAllPayoutBundles(@RequestParam payoutType: PayoutType, @RequestParam from: Date,
                      @RequestParam toDate: Date): List<PayoutBundle> = reconService.getAllPayoutBundles(payoutType, from, toDate)

    @GetMapping("/payout")
    fun getAllPayouts(@RequestParam payoutType: PayoutType, @RequestParam fromDate: Date,
                      @RequestParam toDate: Date, @RequestParam toId: String): List<Payout> = reconService.getAllPayouts(payoutType, fromDate, toDate, toId)

    @GetMapping("/payoutBundle/{bundleId}/payout/{payoutId}")
    fun getPayouts(@PathVariable bundleId: String, @PathVariable payoutId: String): Payout? = reconService.findPayout(bundleId, payoutId)
}