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

    @GetMapping("/messengerPayoutBundle")
    fun messengerPayoutBundle() = reconService.generateNextPayoutsToMessenger()

    @PatchMapping("/messengerPayoutBundle")
    fun messengerPayoutBundle(payoutResults: PayoutBundleResults) = reconService.updatePayoutStatus(payoutResults)

    fun getAllPayouts(@RequestParam payoutType: PayoutType, @RequestParam from: Date,
                      @RequestParam toDate: Date): List<PayoutBundle> {
        return reconService.getAllPayouts(payoutType, from, toDate)
    }
}