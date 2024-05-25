package io.curiousoft.izinga.usermanagement.walletpass.google

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.UUID

internal class GooglePassGeneratorTest {

    private lateinit var sut: GooglePassGenerator

    @BeforeEach
    internal fun setUp() {
      //  sut = GooglePassGenerator()
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
                "+27812815707",
                ProfileRoles.CUSTOMER
            ).apply { id = "71ab2993-f345-48b8-b7cb-36cfebf2143b" }

        //when
        val jwt = sut.createJWTNewObjects(user);

        //then
        Assertions.assertEquals("", jwt)
    }
}