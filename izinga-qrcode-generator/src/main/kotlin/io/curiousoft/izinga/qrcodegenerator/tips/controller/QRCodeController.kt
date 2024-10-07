package io.curiousoft.izinga.qrcodegenerator.tips.controller

import io.curiousoft.izinga.qrcodegenerator.tips.LinkCodeUser
import org.springframework.web.bind.annotation.RestController
import org.springframework.beans.factory.annotation.Autowired
import io.curiousoft.izinga.qrcodegenerator.tips.QRCodeService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.http.ResponseEntity
import org.springframework.core.io.InputStreamResource
import java.util.zip.ZipOutputStream
import java.util.stream.IntStream
import java.io.ByteArrayInputStream
import java.lang.StringBuilder
import java.util.Locale
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.StreamUtils
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry

@RestController
class QRCodeController(@Autowired private val qrCodeService: QRCodeService) {
    private val random = SecureRandom()

    @GetMapping("/generateQRCodes")
    fun generateQRCodes(@RequestParam batchSize: Int, @RequestParam(required = false) heading: String?, @RequestParam(required = false) izingaLogo: Boolean? = true): ResponseEntity<InputStreamResource> {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            IntStream.range(0, batchSize).forEach { i: Int ->
                val label = generateUniqueCode()
                val uniqueUrl = "https://tips.izinga.co.za/tip?linkCode=${label}"
                val qrCodeImage = qrCodeService.generateQRCodeImage(heading, uniqueUrl, label, 450, 450)
                val entry = ZipEntry("QRCode_" + (i + 1) + ".png")
                entry.size = qrCodeImage!!.size.toLong()
                zos.putNextEntry(entry)
                StreamUtils.copy(ByteArrayInputStream(qrCodeImage), zos)
                zos.closeEntry()
            }
            zos.finish()
        }

        val bais = ByteArrayInputStream(baos.toByteArray())
        val headers = HttpHeaders().apply {
            add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=qrcodes.zip")
            add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
        }
        return ResponseEntity.ok()
            .headers(headers)
            .contentLength(baos.size().toLong())
            .body(InputStreamResource(bais))
    }

    @PostMapping("/linkCode")
    fun linkCodeToUser(@RequestBody linkRequest: LinkCodeUserRequest): ResponseEntity<Any> {
        return qrCodeService.getLinkedUser(linkRequest.linkCode)?.let {
            ResponseEntity.badRequest().body("Code already linked")
        } ?: ResponseEntity.ok(qrCodeService.linkUser(linkRequest))
    }

    @GetMapping("/linkCode/{code}")
    fun getLinkCodeUser(@PathVariable code: String): LinkCodeUser? {
        return qrCodeService.getLinkedUser(code)
    }

    fun generateUniqueCode(): String {
        val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val sb = StringBuilder()
        while (sb.length < 5) {
            sb.append(characters[random.nextInt(characters.length)])
        }
        return sb.substring(0, 5).uppercase(Locale.getDefault())
    }
}