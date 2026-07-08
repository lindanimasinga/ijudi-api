package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.IcaAcceptanceLogRepo
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher

@ExtendWith(MockitoExtension::class)
class UserProfileServicePhoneGuardTest {

    @Mock
    private lateinit var userProfileRepo: UserProfileRepo

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Mock
    private lateinit var userConfigService: UserConfigService

    @Mock
    private lateinit var icaAcceptanceLogRepo: IcaAcceptanceLogRepo

    private lateinit var service: UserProfileService

    @BeforeEach
    fun setUp() {
        Mockito.`when`(userConfigService.findAll()).thenReturn(emptyList())
        service = UserProfileService(userProfileRepo, eventPublisher, userConfigService, icaAcceptanceLogRepo)
    }

    @Test
    fun findUserByPhone_returnsNullForShortPhone() {
        val result = service.findUserByPhone("123")
        assertNull(result)
    }

    @Test
    fun create_rejectsShortPhone() {
        val profile = UserProfile(
            "Name",
            UserProfile.SignUpReason.BUY,
            "Address",
            "https://image.url",
            "123",
            ProfileRoles.CUSTOMER,
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.create(profile)
        }
    }
}
