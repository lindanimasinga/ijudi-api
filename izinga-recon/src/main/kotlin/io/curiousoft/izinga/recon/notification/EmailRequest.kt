package io.curiousoft.izinga.recon.notification

import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.recon.payout.Payout
import java.text.SimpleDateFormat

internal class Data(val payout: Payout) {
    val order: List<OrderReport> = payout.orders.map { OrderReport(it) }
    val date: String = SimpleDateFormat("YYYY/MM/dd HH:mm").format(payout.createdDate)

}

internal class OrderReport(order: Order) {
    var date: String? = null
    var id: String? = null
    var basketAmount: String? = null

    init {
        this.id = order.id
        this.date = SimpleDateFormat("YYYY/MM/dd HH:mm").format(order.createdDate)
        this.basketAmount = "R${order.basketAmount}"
    }
}

internal class From {
    var email: String? = null
}

internal class Personalization(var email: String, var data: Data)

internal class EmailRequest (
    var from: From? = null,
    var to: List<To>? = null,
    var personalization: List<Personalization>? = null,
    var template_id: String? = null
)

internal class To(var email: String)