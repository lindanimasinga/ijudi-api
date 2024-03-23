package io.curiousoft.izinga.usermanagement.walletpass.apple;

import de.brendamour.jpasskit.PKBarcode;
import de.brendamour.jpasskit.PKField;
import de.brendamour.jpasskit.PKPass;
import de.brendamour.jpasskit.enums.PKBarcodeFormat;
import de.brendamour.jpasskit.enums.PKPassType;
import de.brendamour.jpasskit.passes.PKStoreCard;
import de.brendamour.jpasskit.signing.*;
import io.curiousoft.izinga.commons.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ApplePassGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplePassGenerator.class);

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy MMM dd");

    public byte[] generatePass(final UserProfile user) throws PKSigningException, CertificateException, IOException, URISyntaxException {

            PKPass pass = PKPass.builder()
                    .pass(PKStoreCard.builder()
                            .passType(PKPassType.PKStoreCard)
                            .primaryField(PKField.builder()
                                    .key(String.format("Tips received since %s",dateFormat.format(user.getCreatedDate())))
                                    .label(String.format("Tips received since %s",dateFormat.format(user.getCreatedDate())))
                                    .value(user.getServicesCompleted()).build())
                            .secondaryFields(
                                    List.of(PKField.builder().key("Name").label("Name").value(user.getName()).build(),
                                            PKField.builder().key("Contact").label("Contact").value(user.getMobileNumber()).build()))
                            .backFields(
                                    List.of(PKField.builder().key("Role").label("Role").value(user.getRole().toString()).build(),
                                            PKField.builder().key("Balance").label("Balance").value(150).currencyCode("ZAR").build())))
                    .barcodeBuilder(PKBarcode.builder()
                    .format(PKBarcodeFormat.PKBarcodeFormatQR)
                    .message("https://tips.izinga.co.za/tip?messengerId=" + user.getId())
                    .messageEncoding(StandardCharsets.UTF_8))
                    .formatVersion(1)
                    .passTypeIdentifier("pass.co.za.izinga")
                    .serialNumber(UUID.randomUUID().toString())
                    .teamIdentifier("QRRU6DVRG2")
                    .organizationName("Curiousoft Pty Ltd")
                    .description(user.getMobileNumber())
                    .backgroundColor("rgb(248,247,247 )")
                    .foregroundColor("rgb(47,48,49 )")
                    .build();

            String appleWWDRCA = "./walletpass/applecert/AppleWWDRCA.pem"; // this is apple's developer relation cert
            String privateKeyPath = "./walletpass/applecert/izinga-user-id-pass.p12"; // the private key you exported from keychain
            String privateKeyPassword = "izinga"; // the password you used to export
            File templatePathFile =  new File("walletpass/payme");

            PKSigningInformation pkSigningInformation = new PKSigningInformationUtil().loadSigningInformationFromPKCS12AndIntermediateCertificate(privateKeyPath,  privateKeyPassword, appleWWDRCA);
            PKPassTemplateFolder passTemplate = new PKPassTemplateFolder(templatePathFile.exists()? templatePathFile.getAbsolutePath() : Paths.get(ClassLoader.getSystemResource(templatePathFile.getPath()).toURI()).toString());
            PKFileBasedSigningUtil pkSigningUtil = new PKFileBasedSigningUtil();
            return pkSigningUtil.createSignedAndZippedPkPassArchive(pass, passTemplate, pkSigningInformation);
    }
}
