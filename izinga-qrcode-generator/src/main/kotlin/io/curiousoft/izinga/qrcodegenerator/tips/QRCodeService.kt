package io.curiousoft.izinga.qrcodegenerator.tips

import org.springframework.beans.factory.annotation.Autowired
import java.util.stream.IntStream
import java.lang.StringBuilder
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.EncodeHintType
import com.google.zxing.BarcodeFormat
import java.awt.image.BufferedImage
import com.google.zxing.client.j2se.MatrixToImageWriter
import io.curiousoft.izinga.qrcodegenerator.tips.controller.LinkCodeUserRequest
import org.springframework.stereotype.Service
import java.awt.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import java.lang.Exception
import java.util.*
import java.util.Map

@Service
class QRCodeService(@Autowired val linkCodeUserRepo: LinkCodeUserRepository) {

    @Throws(Exception::class)
    fun generateQRCodeImage(text: String?, label: String, width: Int, height: Int): ByteArray {
        val qrCodeWriter = QRCodeWriter()
        val hints = Map.of(EncodeHintType.CHARACTER_SET, "UTF-8")
        val bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height, hints)
        val qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix)
        return addTemplateToQRCode(qrImage, label)
    }

    @Throws(IOException::class)
    private fun addTemplateToQRCode(qrImage: BufferedImage, label: String): ByteArray {
        // Bank card dimensions in pixels at 300 DPI
        val cardWidth = 638
        val cardHeight = 1011
        val spacing = 48

        // Load logo
        val logoStream = javaClass.getResourceAsStream("/izinga-logo.png")
        val logo = ImageIO.read(logoStream)

        // Calculate dimensions
        val logoWidth = logo.width / 4
        val logoHeight = logo.height / 4
        val qrWidth = qrImage.width
        val qrHeight = qrImage.height // Make QR code a square

        // Create template with black background
        val template = BufferedImage(cardWidth, cardHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = template.createGraphics()
        graphics.color = Color.BLACK
        graphics.fillRect(0, 0, cardWidth, cardHeight)

        // Draw logo at the top, centered horizontally
        val logoX = (cardWidth - logoWidth) / 2
        graphics.drawImage(logo, logoX, spacing, logoWidth, logoHeight, null)

        // Draw "SCAN TO TIP" text below the logo, centered horizontally and 32px apart
        graphics.color = Color.WHITE
        graphics.font = Font("Arial", Font.PLAIN, 52)
        val metrics = graphics.fontMetrics
        val scanToTipText = "SCAN TO TIP"
        val scanToTipTextX = (cardWidth - metrics.stringWidth(scanToTipText)) / 2
        val scanToTipTextY = spacing + logoHeight + 2 * spacing
        graphics.drawString(scanToTipText, scanToTipTextX, scanToTipTextY)

        // Draw QR code below the "SCAN TO TIP" text, centered horizontally and 32px apart
        val qrX = (cardWidth - qrWidth) / 2
        val qrY = scanToTipTextY + spacing
        graphics.drawImage(qrImage.getScaledInstance(qrWidth, qrHeight, Image.SCALE_SMOOTH), qrX, qrY, null)

        // Draw label at the bottom, centered horizontally
        val labelX = (cardWidth - metrics.stringWidth(label)) / 2
        val labelY = qrY + qrHeight + spacing + spacing
        graphics.drawString(label, labelX, labelY)
        graphics.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(template, "png", baos)
        return baos.toByteArray()
    }

    fun generateRandomText(length: Int): String {
        val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = Random()
        val sb = StringBuilder(length)
        IntStream.range(0, length).forEach { i: Int -> sb.append(characters[random.nextInt(characters.length)]) }
        return sb.toString()
    }

    fun getLinkedUser(linkCode: String): LinkCodeUser? = linkCodeUserRepo.findByLinkCode(linkCode)

    fun linkUser(linkRequest: LinkCodeUserRequest): LinkCodeUser = linkCodeUserRepo.save(linkRequest.let { LinkCodeUser(it.userId, it.linkCode) })

}