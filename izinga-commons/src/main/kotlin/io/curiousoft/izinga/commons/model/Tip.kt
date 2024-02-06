package io.curiousoft.izinga.recon.payout

import java.util.Date

data class Tip(val date: Date, val receiptId: String, val amount: Double, val emailAddress: String) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        other as Tip
        if (receiptId != other.receiptId) return false
        return true
    }

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + receiptId.hashCode()
        return result
    }
}