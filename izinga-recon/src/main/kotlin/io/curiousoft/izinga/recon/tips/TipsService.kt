package io.curiousoft.izinga.recon.tips

import io.curiousoft.izinga.recon.payout.Tip
import io.curiousoft.izinga.yocopay.api.YocoTransactionsClient
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class TipsService(private val yocoDashboardApi: YocoTransactionsClient) {

    fun getTodayTips(): Set<Tip>? {
        val today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val filter = """{"transactionType":[],"transactionState":[],"userUUID":[],"serialNumber":[],"created":["${today}T00:00:00+02:00","${today}T23:59:59+02:00"]}""".let {
            URLEncoder.encode(it, StandardCharsets.UTF_8.toString())
        }
        return yocoDashboardApi.transactions(filter)?.data?.transactions
            ?.flatMap { it.subItems }
            ?.map {
                Tip(date = it.created, receiptId = it.details.receiptNumber,
                amount = it.amounts.tipAmount, emailAddress = it.user.email)
            }?.toSet()
    }
}

