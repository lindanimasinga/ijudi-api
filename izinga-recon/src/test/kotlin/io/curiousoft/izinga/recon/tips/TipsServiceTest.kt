package io.curiousoft.izinga.recon.tips

import io.curiousoft.izinga.recon.payout.Tip
import io.curiousoft.izinga.yocopay.api.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class TipsServiceTest {

    private val yocoApi: YocoTransactionsClient = mockk()
    private lateinit var tipService: TipsService

    @Before
    fun setUp() {
        tipService = TipsService(yocoDashboardApi = yocoApi)
    }

    @Test
    fun getTodayTips() {
        //given
        val data: Data = mockk<Data>().apply {
            every { transactions }.returns(
                setOf(
                    mockk<Transaction>().apply {
                        every { subItems }.returns(
                            setOf(
                                SubItem(latitude = "0.00", longitude = "0.00",
                                    user = User(firstname = "test", lastname = "user", email = "testuser@email.com"),
                                    amounts = Amounts(totalAmount = 100.00, tipAmount = 12.00), created = Date(),
                                    details = Details(receiptNumber = "2024/01/11002")
                                )
                            )
                        )
                    }
                )
            )
        }
        val transactionResponse = TransactionsResponse(status = 200, data = data)
        every { yocoApi.transactions(any()) }.returns(transactionResponse)

        //when
        val response  = tipService.getTodayTips()

        //then
        val today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val filter = """{"transactionType":[],"transactionState":[],"userUUID":[],"serialNumber":[],"created":["${today}T00:00:00+02:00","${today}T23:59:59+02:00"]}""".let {
            URLEncoder.encode(it, StandardCharsets.UTF_8.toString())
        }
        verify { yocoApi.transactions(filter) }
    }
}