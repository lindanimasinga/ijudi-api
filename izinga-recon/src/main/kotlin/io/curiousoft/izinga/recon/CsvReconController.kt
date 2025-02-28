package io.curiousoft.izinga.recon

import io.curiousoft.izinga.recon.payout.PayoutStage
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/reconcsv")
class CsvReconController(val reconService: ReconService) {

    @GetMapping(value = ["/shop-payout-bundle"] , produces = ["application/csv"])
    fun shopPayoutBundle(response: HttpServletResponse) {
        response.apply {
            contentType = "application/csv"
            addHeader("Content-Disposition", "attachment; filename=\"shop-payout-bundle.csv\"")
        }
        val bundle = reconService.getCurrentPayoutBundleForShops()
        payoutBundleToCsv(response.writer, bundle)
        bundle.payouts.forEach { it.payoutStage = PayoutStage.PROCESSING }
        reconService.updateBundle(bundle)
    }

    @GetMapping(value = ["/messenger-payout-bundle"], produces = ["application/csv"])
    fun messengerPayoutBundle(response: HttpServletResponse) {
        reconService.getCurrentPayoutBundleForMessenger().let {
            response.contentType = "application/csv"
            val fileName = "messenger-payout-bundle-${it.createdDate}.csv"
            response.addHeader("Content-Disposition", "attachment; filename=\"${fileName}\"")
            payoutBundleToCsv(response.writer, it)
            it.payouts.forEach { it.payoutStage = PayoutStage.PROCESSING }
            reconService.updateBundle(it)
        }
    }
}