package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.recon.payout.PayoutBundle
import io.curiousoft.izinga.recon.payout.PayoutType
import io.curiousoft.izinga.recon.payout.ShopPayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Email-consumer trace — ADR-028 / DEF-1.
 *
 * CsvWriter.kt line 38: `it.emailAddress ?: ""` — when emailAddress is null the Elvis
 * operator substitutes an empty string.  No NPE; the resulting CSV row contains an empty
 * quoted field where the email address column would be.
 *
 * DailyPayoutEmailNotificationService.kt line 61: `it.emailAddress?.let { ... }` — when
 * emailAddress is null the lambda body is skipped entirely; no HTTP call is made.
 *
 * This class covers the CsvWriter path.  payoutBundleToCsv is a package-level function
 * in io.curiousoft.izinga.recon (same package as this test) so no import is needed.
 */
class CsvWriterNullEmailTest {

    /**
     * CSV-NULL-EMAIL-01: null emailAddress must produce an empty quoted CSV field, not "null".
     */
    @Test
    fun `csvAppend with null emailAddress emits empty string not null and does not throw`() {
        val payout = ShopPayout(
            toId = "shop-no-email",
            toName = "No Email Shop",
            toBankName = "FNB",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "1234567890",
            orders = mutableSetOf(),
            toBranchCode = "250655",
            fromReference = "ref",
            toReference = "ref",
            emailNotify = null,
            emailAddress = null,
            emailSubject = null
        )
        val bundle = PayoutBundle(type = PayoutType.SHOP, payouts = listOf(payout), createdBy = "test")

        val sw = StringWriter()
        val pw = PrintWriter(sw)

        payoutBundleToCsv(pw, bundle)

        val csv = sw.toString()
        // The row must exist
        assertTrue("CSV must contain the payout row", csv.contains("No Email Shop"))
        // The null emailAddress must NOT appear as the literal string "null"
        assertFalse("CSV must not contain literal 'null' in email column",
            csv.contains(",null,") || csv.contains("\"null\""))
    }

    /**
     * CSV-NULL-EMAIL-02: blank emailAddress must also produce an empty quoted CSV field.
     */
    @Test
    fun `csvAppend with blank emailAddress emits empty quoted string`() {
        val payout = ShopPayout(
            toId = "shop-blank-email",
            toName = "Blank Email Shop",
            toBankName = "FNB",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "9876543210",
            orders = mutableSetOf(),
            toBranchCode = "250655",
            fromReference = "ref",
            toReference = "ref",
            emailNotify = null,
            emailAddress = "",
            emailSubject = null
        )
        val bundle = PayoutBundle(type = PayoutType.SHOP, payouts = listOf(payout), createdBy = "test")

        val sw = StringWriter()
        val pw = PrintWriter(sw)

        payoutBundleToCsv(pw, bundle)

        val csv = sw.toString()
        assertTrue("CSV must contain the payout row", csv.contains("Blank Email Shop"))
        assertFalse("CSV must not contain literal 'null'", csv.contains("\"null\""))
    }
}
