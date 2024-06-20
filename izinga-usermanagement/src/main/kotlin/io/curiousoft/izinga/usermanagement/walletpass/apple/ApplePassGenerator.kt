package io.curiousoft.izinga.usermanagement.walletpass.apple

import de.brendamour.jpasskit.PKBarcode
import de.brendamour.jpasskit.PKField
import de.brendamour.jpasskit.PKPass
import de.brendamour.jpasskit.enums.PKBarcodeFormat
import de.brendamour.jpasskit.enums.PKPassType
import de.brendamour.jpasskit.passes.PKStoreCard
import de.brendamour.jpasskit.signing.PKFileBasedSigningUtil
import de.brendamour.jpasskit.signing.PKPassTemplateFolder
import de.brendamour.jpasskit.signing.PKSigningInformationUtil
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.usermanagement.walletpass.PassGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.List

@Service
class ApplePassGenerator : PassGenerator<ByteArray> {
    private val dateFormat = SimpleDateFormat("yyyy MMM dd")

    override fun generatePass(user: UserProfile): ByteArray {
        val pass = PKPass.builder()
            .pass(
                PKStoreCard.builder()
                    .passType(PKPassType.PKStoreCard)
                    .primaryField(
                        PKField.builder()
                            .key(String.format("Tips received since %s", dateFormat.format(user.createdDate)))
                            .label(String.format("Tips received since %s", dateFormat.format(user.createdDate)))
                            .value(user.servicesCompleted).build())
                    .secondaryFields(
                        listOf(PKField.builder().key("Name").label("Name").value(user.name).build(),
                            PKField.builder().key("Contact").label("Contact").value(user.mobileNumber).build()))
                    .backFields(
                        List.of(PKField.builder().key("Role").label("Role").value(user.role.toString()).build(),
                            PKField.builder().key("Balance").label("Balance").value(150).currencyCode("ZAR").build()))
            )
            .barcodeBuilder(PKBarcode.builder()
                .format(PKBarcodeFormat.PKBarcodeFormatQR)
                .message("https://tips.izinga.co.za/tip?messengerId=" + user.id)
                .messageEncoding(StandardCharsets.UTF_8))
            .formatVersion(1)
            .passTypeIdentifier("pass.co.za.izinga")
            .serialNumber(UUID.randomUUID().toString())
            .teamIdentifier("QRRU6DVRG2")
            .organizationName("Curiousoft Pty Ltd")
            .description(user.mobileNumber)
            .backgroundColor("rgb(248,247,247 )")
            .foregroundColor("rgb(47,48,49 )")
            .build()

        val appleWWDRCA = "./walletpass/applecert/AppleWWDRCA.pem" // this is apple's developer relation cert
        val privateKeyPath = "./walletpass/applecert/izinga-user-id-pass.p12" // the private key you exported from keychain
        val privateKeyPassword = "izinga" // the password you used to export
        val templatePathFile = File("walletpass/payme")
        val pkSigningInformation = PKSigningInformationUtil().loadSigningInformationFromPKCS12AndIntermediateCertificate(
                privateKeyPath,
                privateKeyPassword,
                appleWWDRCA)
        val passTemplate = PKPassTemplateFolder(
            if (templatePathFile.exists()) templatePathFile.absolutePath
            else Paths.get(ClassLoader.getSystemResource(templatePathFile.path).toURI()).toString())
        val pkSigningUtil = PKFileBasedSigningUtil()
        return pkSigningUtil.createSignedAndZippedPkPassArchive(pass, passTemplate, pkSigningInformation)
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ApplePassGenerator::class.java)
    }

    override fun updateBalance(user: UserProfile, balance: BigDecimal): Boolean {
        //todo update apple pass
        return true
    }
}