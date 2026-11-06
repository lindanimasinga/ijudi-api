package io.curiousoft.izinga.recon

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.PrintWriter
import java.io.StringWriter

/**
 * BUG-7: RFC 4180 CSV field encoding.
 * Verifies that csvAppend wraps fields in double-quotes and correctly escapes
 * internal double-quote characters so comma-containing values do not corrupt the row.
 */
class CsvWriterQuotingTest {

    @Test
    fun `BUG-7 - plain field is wrapped in double quotes`() {
        val sw = StringWriter()
        val writer = PrintWriter(sw)

        writer.csvAppend("FoodStore")

        val output = sw.toString()
        assertEquals("\"FoodStore\",", output)
    }

    @Test
    fun `BUG-7 - field containing comma is quoted and comma is preserved`() {
        val sw = StringWriter()
        val writer = PrintWriter(sw)

        writer.csvAppend("Food & Co, Durban")

        val output = sw.toString()
        // The entire field must be wrapped in quotes so the embedded comma is not a separator
        assertEquals("\"Food & Co, Durban\",", output)
    }

    @Test
    fun `BUG-7 - field containing double-quote escapes it as two double-quotes (RFC 4180)`() {
        val sw = StringWriter()
        val writer = PrintWriter(sw)

        writer.csvAppend("He said \"hello\"")

        val output = sw.toString()
        assertEquals("\"He said \"\"hello\"\"\",", output)
    }

    @Test
    fun `BUG-7 - multiple fields each get their own quoted cell`() {
        val sw = StringWriter()
        val writer = PrintWriter(sw)

        writer.csvAppend("FoodStore", "0812345678", "1", "250655", "1500.00")

        val output = sw.toString()
        assertEquals("\"FoodStore\",\"0812345678\",\"1\",\"250655\",\"1500.00\",", output)
    }

    @Test
    fun `BUG-7 - empty field produces empty quoted cell`() {
        val sw = StringWriter()
        val writer = PrintWriter(sw)

        writer.csvAppend("")

        val output = sw.toString()
        assertEquals("\"\",", output)
    }

    @Test
    fun `BUG-7 - field containing newline is correctly quoted`() {
        val sw = StringWriter()
        val writer = PrintWriter(sw)

        writer.csvAppend("Line1\nLine2")

        val output = sw.toString()
        assertEquals("\"Line1\nLine2\",", output)
    }

    @Test
    fun `BUG-7 - field containing only quotes escapes them all`() {
        val sw = StringWriter()
        val writer = PrintWriter(sw)

        writer.csvAppend("\"\"")

        val output = sw.toString()
        // Each " → "", wrapped in outer quotes → """"""""
        assertEquals("\"\"\"\"\"\"",  output.trimEnd(','))
    }
}
