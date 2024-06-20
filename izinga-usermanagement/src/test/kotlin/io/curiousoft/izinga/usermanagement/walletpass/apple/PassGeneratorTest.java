package io.curiousoft.izinga.usermanagement.walletpass.apple;

import io.curiousoft.izinga.usermanagement.walletpass.apple.ApplePassGenerator;
import de.brendamour.jpasskit.signing.PKSigningException;
import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.cert.CertificateException;

class PassGeneratorTest {

    private ApplePassGenerator sut = new ApplePassGenerator();

    @Test
    void generatePass() throws PKSigningException, CertificateException, IOException, URISyntaxException {
        //give a registered user
        UserProfile user = new UserProfile("Lindani",
                UserProfile.SignUpReason.DELIVERY_DRIVER,
                "123",
                "123",
                "0812814457",
                ProfileRoles.MESSENGER);

        //when a pass is generated
        var bytes = sut.generatePass(user);

        //verify the pass exists
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        String outputFile = "./mypass1.pkpass";
        IOUtils.copy(inputStream, new FileOutputStream(outputFile));
    }
}