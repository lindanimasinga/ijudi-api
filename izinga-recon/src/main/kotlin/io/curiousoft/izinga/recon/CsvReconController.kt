package io.curiousoft.izinga.recon

import io.curiousoft.izinga.recon.payout.PayoutType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/reconcsv")
class CsvReconController(val reconService: ReconService) {

    @GetMapping(value = ["/shop-payout-bundle"] , produces = ["text/csv"])
    fun shopPayoutBundle(response: HttpServletResponse) {
        reconService.generateNextPayoutsToShop()?.let {
            payoutBundleToCsv(response.writer, it)
        }
    }

    @GetMapping(value = ["/messenger-payout-bundle"], produces = ["text/csv"])
    fun messengerPayoutBundle(response: HttpServletResponse) {
        reconService.generateNextPayoutsToMessenger()?.let {
            payoutBundleToCsv(response.writer, it)
        }
    }
}