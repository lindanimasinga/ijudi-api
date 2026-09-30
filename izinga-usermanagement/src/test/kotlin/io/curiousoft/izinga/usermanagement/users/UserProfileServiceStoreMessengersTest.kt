package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.IcaAcceptanceLogRepo
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher

/**
 * Written as a JUnit 5 test (not appended to the sibling JUnit 4-style UserServiceTest.kt):
 * this module has no junit-vintage-engine dependency, so @org.junit.Test methods there are
 * silently never executed by Surefire (0 run, no failure — a known pre-existing gap, not
 * introduced here). A test added there would never actually verify anything.
 */
@ExtendWith(MockitoExtension::class)
class UserProfileServiceStoreMessengersTest {

    @Mock lateinit var profileRepo: UserProfileRepo
    @Mock lateinit var eventPublisher: ApplicationEventPublisher
    @Mock lateinit var userConfigService: UserConfigService
    @Mock lateinit var icaAcceptanceLogRepo: IcaAcceptanceLogRepo
    @Mock lateinit var referralCodeService: ReferralCodeService

    @InjectMocks
    lateinit var profileService: UserProfileService

    @Test
    fun `findMessengersByStoreId returns messengers linked to the given storeId unchanged`() {
        val storeId = "store-abc-123"
        val driver = UserProfile("Driver Name", UserProfile.SignUpReason.DELIVERY_DRIVER, "address", "https://img.url", "+27821234567", ProfileRoles.MESSENGER)
        driver.id = "driverId1"
        val expected = listOf(driver)

        `when`(profileRepo.findByRoleAndStoreId(ProfileRoles.MESSENGER, storeId)).thenReturn(expected)

        val result = profileService.findMessengersByStoreId(storeId)

        assertEquals(expected, result)
        verify(profileRepo).findByRoleAndStoreId(ProfileRoles.MESSENGER, storeId)
    }
}
