package io.curiousoft.izinga.usermanagement.walletpass.google

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

internal class GooglePassGeneratorTest {

    private lateinit var sut: GooglePassGenerator

    @BeforeEach
    internal fun setUp() {
        val config = GoogleConfig(
        projectId = "izinga-proj",
        privateKeyId = "707a3b4b18f37678e404114c75d154d0c32b0a04",
        privateKey = "-----BEGIN PRIVATE KEY-----\nMIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCytJ9NuqeG0cvA\ni64O+4pYrFL0IS2gGqYNMszuFYmOPWwg0M/A+9qGsFIo7tj20+kWLoSnb00IEsmB\ng44g2dOo3hE34UZ2MuwM5Obx6DF5CYr5W/l6Ums+hkv/R2DDTmwSbOw7IN7d9RbU\nFOACjuJVxMObnF7zV7OXQ079nbgV50Lo4GdpDFHHkDB4QwKjsmAWxYcPP+sRN4UM\nHiG43+DY67Sr+ZDq5+5hEdf+i5Q0bfy69R6pst6LeVjbWNBRYwTQRkUs8oEmponU\n35JHISf1aWsYK1AXoXt9qtcUrJV9bhelbQ3N7u+dmgcQiwRa4fKsYTvLSpBWiuZP\nd0X1DFSLAgMBAAECggEAFErUq2e5Ezg/z0GX27quIdXmO4jdnJ26DQCflZ9wyxaS\nxdfVkiqRzL6OgyMQFFh0J3Z+eLbM7qWXfR3vpdpQ9w7JshM3buo8X1XKGwkMk8Zv\nd1OoLL7Zel7sl/RxJUGtSEWsaNBu45GrlxyMYq36lIEEhzyGlFJOodayTF6Rozw6\ntWqP7LFXE0iDlL1WdXuTnMTavxuI/YVm8sBhO/cqKRxF8FvzQM++nIq5O8T4VsFc\nCIKdNCPeQzygWh1CCVMPwHOdoPhhZ5haw1jx7WJa+lk0QyJo8XB2Azu8Su4RZZxo\ninP2yBpDA+uMS6W5Uy/OEPYoUKEhP0DKiIvf56T/4QKBgQDYaD+IgGc91kt5/98z\noJhC3GU8vQDEiQCskXycNjNGVOm0sB3nwb728jGLgBmqmAkX+h383DgFGjSCYYT+\nFS7eHdsepFZjEo2FGHM9UwIAKLT+aLiWnTYBmv9kl4TDMvFiBUhWR3niZy2hO20D\nlL4/LR0CkvrPcD1ulTndkpCXYQKBgQDTZo+c8wBrD/BUBRVdIYWfbSVogjmkpBln\n6SoaG2uFGoQw9ZV2pYuTv7dmKBrpMLtqyshcffM2iXwTWxfGA7oP/4Z3ma1+bak3\nnNNCUIoU318+/A6ZrLu6P0K1m/ucnWjccfSSUy6unhGeseWDY3stfmYXpxxKGNou\n2cbH1TpvawKBgFPNkwTmTJS6vsHA34ubnjsOJDowKvLw5wzsziekKZkL2vD43qvX\n8H6q7ZrLAaV5eNQLLL+1/A+lxT3US+8k/uBPCYLFOxrknV80R7Qwx+6N1QFMRmc0\n/CwE/O/UaTo6KRW/W/LlfQK62AW/AIlJeZ73dmt/xZa9LVz8dssiLdIhAoGAdORL\nGM/NNWX0z6CX9Y0po8PoYWdQlFcgrTJiU1lCFLFa0u+Ym6hiC2qA8H3Qk4yctSo3\nq90A9PEEqHdAJ/+7jEkQg2V4Z2YfoV6X+h0qnOv+cIdTU2oNy6hbLKx5blojt3FT\neO2B7I/+0i8v53zEiZ1Quldu7xHa4vkMT0b6Ls0CgYBQHaGeSstQygy4YHT5j/Aw\n1kuViZuDLb9vkXAFwInDCXXwJ0R2iYtQWbUSYrML+THtZRdpTqWFXkqa/TxJnilr\nA5JTA//ORHIoaLPnOD8RL4UOQgTn7nDRGWRZkM6PI5M6o7uBLtjn1NXjd6GzuMes\nqL9Lr3cuunKP10GXaCeKUA==\n-----END PRIVATE KEY-----\n",
        clientEmail = "google-wallet-service-account@izinga-proj.iam.gserviceaccount.com",
        clientId = "102622872752921211507",
        authUri = "https://accounts.google.com/o/oauth2/auth",
        tokenUri = "https://oauth2.googleapis.com/token",
        authProviderX509CertUrl = "https://www.googleapis.com/oauth2/v1/certs",
        clientX509CertUrl = "https://www.googleapis.com/robot/v1/metadata/x509/google-wallet-service-account%40izinga-proj.iam.gserviceaccount.com",
        universeDomain = "googleapis.com",
        type = "service_account")
        sut = GooglePassGenerator(config);
    }

    @Disabled
    @Test
    fun updateJWTBalanceObjects() {
        //given
        val user = UserProfile(
                "TestUser",
                UserProfile.SignUpReason.BUY,
                "21 Mgobhozi Rd, Enkanyisweni, KwaMashu, 4051",
                "path to image",
                "+27812815701",
                ProfileRoles.CUSTOMER
            ).apply { id = "61ab2993-f342-48b8-b7cb-36cfeer3214312" }

        //when
        val boolean = sut.updateBalance(user, BigDecimal.ONE);

        //then
        Assertions.assertEquals("", boolean)
    }

    @Disabled
    @Test
    fun createJWTNewObjects() {
        //given
        val user = UserProfile(
            "TestUser",
            UserProfile.SignUpReason.BUY,
            "21 Mgobhozi Rd, Enkanyisweni, KwaMashu, 4051",
            "path to image",
            "0812815707",
            ProfileRoles.CUSTOMER
        ).apply { id = "3f71416c-a7bb-4f89-a02c-649f28e5b042" }

        //when
        val jwt = sut.createJWTNewObjects(user);

        //then
        Assertions.assertEquals("", jwt)
    }
}