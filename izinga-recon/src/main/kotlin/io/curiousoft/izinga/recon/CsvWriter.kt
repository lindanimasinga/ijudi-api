package io.curiousoft.izinga.recon

import com.opencsv.CSVWriter
import com.opencsv.bean.ColumnPositionMappingStrategy
import com.opencsv.bean.StatefulBeanToCsv
import com.opencsv.bean.StatefulBeanToCsvBuilder
import com.opencsv.exceptions.CsvException
import io.curiousoft.izinga.recon.payout.Payout
import io.curiousoft.izinga.recon.payout.PayoutBundle
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter


class WriteCsvToResponse

    private val LOGGER = LoggerFactory.getLogger(WriteCsvToResponse::class.java)

    fun payoutBundleToCsv(writer: PrintWriter, payoutBundle: PayoutBundle) {
        try {
            getStatefulBean(writer, payoutBundle.payouts)
        } catch (ex: CsvException) {
            LOGGER.error("Error mapping Bean to CSV", ex)
        }
    }

    private fun getStatefulBean(writer: PrintWriter, payouts: List<Payout>): StatefulBeanToCsv<Payout> {
        //add headers for fnb
        writer.append("BInSol - U ver 1.00,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,\n")
        writer.append("${OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-YYYY"))},,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,\n")
        writer.append("62900076119,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,\n")
        val mapStrategy: ColumnPositionMappingStrategy<Payout> = ColumnPositionMappingStrategy<Payout>()
        mapStrategy.type = Payout::class.java
        writer.csvAppend("RECIPIENT NAME","RECIPIENT ACCOUNT","RECIPIENT ACCOUNT TYPE","BRANCHCODE","AMOUNT","OWN REFERENCE","RECIPIENT REFERENCE","EMAIL 1 NOTIFY","EMAIL 1 ADDRESS","EMAIL 1 SUBJECT","EMAIL 2 NOTIFY","EMAIL 2 ADDRESS","EMAIL 2 SUBJECT","EMAIL 3 NOTIFY","EMAIL 3 ADDRESS","EMAIL 3 SUBJECT","EMAIL 4 NOTIFY","EMAIL 4 ADDRESS","EMAIL 4 SUBJECT","EMAIL 5 NOTIFY","EMAIL 5 ADDRESS","EMAIL 5 SUBJECT","FAX 1 NOTIFY","FAX 1 CODE","FAX 1 NUMBER","FAX 1 SUBJECT","FAX 2 NOTIFY","FAX 2 CODE","FAX 2 NUMBER","FAX 2 SUBJECT","SMS 1 NOTIFY","SMS 1 CODE","SMS 1 NUMBER","SMS 2 NOTIFY","SMS 2 CODE","SMS 2 NUMBER")
        writer.append("\n")
        payouts.forEach {
            writer.csvAppend(it.toName, "${it.toAccountNumber}", it.toType.code, "${it.toBranchCode}", "${it.total}", it.fromReference, it.toReference, "yes", it.emailAddress ?: "", it.emailSubject ?: "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "")
            writer.append("\n")
        }
        return StatefulBeanToCsvBuilder<Payout>(writer)
            .withQuotechar(CSVWriter.DEFAULT_QUOTE_CHARACTER)
            //.withMappingStrategy(mapStrategy)
            .withSeparator(',')
            .build()
    }

/**
 * BUG-7: RFC 4180 CSV field encoding.
 *
 * FNB BInSol format has no documented quoting restrictions; RFC 4180 quoting is the
 * safe default. Each field is wrapped in double-quotes; internal double-quotes are
 * escaped by doubling them ("" inside a quoted field). This prevents comma-containing
 * values (e.g. a store name "Food & Co, Durban") from corrupting the row.
 */
fun PrintWriter.csvAppend(vararg value: String) {
    value.forEach {
        val escaped = it.replace("\"", "\"\"")
        this.append("\"$escaped\",")
    }
}