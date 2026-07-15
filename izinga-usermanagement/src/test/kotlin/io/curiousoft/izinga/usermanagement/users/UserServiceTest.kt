package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.IcaAcceptanceLog
import io.curiousoft.izinga.commons.model.Profile
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.StoreType
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.IcaAcceptanceLogRepo
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Disabled
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.context.ApplicationEventPublisher
import java.util.*

@RunWith(MockitoJUnitRunner::class)
class UserServiceTest {

    @Mock
    lateinit var userConfigService: UserConfigService

    //system under test
    lateinit var profileService: UserProfileService
    @Mock
    lateinit var profileRepo: UserProfileRepo
    @Mock
    lateinit var profileUpdatedEventPublisher: ApplicationEventPublisher
    @Mock
    lateinit var icaAcceptanceLogRepo: IcaAcceptanceLogRepo

    @Mock
    lateinit var referralCodeService: ReferralCodeService

    @Before
    fun setUp() {
        profileService = UserProfileService(profileRepo, profileUpdatedEventPublisher, userConfigService, icaAcceptanceLogRepo, referralCodeService)
    }

    @Test
    @Throws(Exception::class)
    fun create() {

        //given
        val initialProfile = UserProfile(
            "name",
            UserProfile.SignUpReason.BUY,
            "address",
            "https://image.url",
            "081mobilenumb",
            ProfileRoles.CUSTOMER
        )

        //when
        Mockito.`when`(profileRepo.save(initialProfile)).thenReturn(initialProfile)
        Mockito.`when`(profileRepo.existsByMobileNumber(initialProfile.mobileNumber!!)).thenReturn(false)
        val profile: Profile = profileService.create(initialProfile)

        //verify
        Mockito.verify(profileRepo).existsByMobileNumber(initialProfile.mobileNumber!!)
        Mockito.verify(profileRepo).save(initialProfile)
        Assert.assertNotNull(profile.id)
    }

    @Test
    @Throws(Exception::class)
    fun createAlreadyExists() {

        //given
        val initialProfile = UserProfile(
            "name",
            UserProfile.SignUpReason.BUY,
            "address",
            "https://image.url",
            "081mobilenumb",
            ProfileRoles.CUSTOMER
        )

        //when
        Mockito.`when`(profileRepo.existsByMobileNumber(initialProfile.mobileNumber!!)).thenReturn(true)
        try {
            val profile: Profile = profileService.create(initialProfile)
            Assert.fail()
        } catch (e: Exception) {
            Assert.assertEquals("User with phone number " + initialProfile.mobileNumber + " already exist.", e.message)
        }
    }

    @Test
    @Throws(Exception::class)
    fun update() {

        //given
        val profileId = "myID"
        val initialProfile = UserProfile(
            "name",
            UserProfile.SignUpReason.BUY,
            "address",
            "https://image.url",
            "081mobilenumb",
            ProfileRoles.CUSTOMER
        )
        val patchProfileRequest = UserProfile(
            "secondName",
            UserProfile.SignUpReason.BUY,
            "address2",
            "https://image.url2",
            "078mobilenumb",
            ProfileRoles.MESSENGER
        )

        //when
        Mockito.`when`(profileRepo.findById(profileId)).thenReturn(Optional.of(initialProfile))
        Mockito.`when`(profileRepo.save(initialProfile)).thenReturn(initialProfile)
        val profile: Profile = profileService.update(profileId, patchProfileRequest)

        //verify
        Mockito.verify(profileRepo).findById(profileId)
        Mockito.verify(profileRepo).save(initialProfile)
    }

    @Test
    fun delete() {
        //given
        val profileId = "myID"
        //when
        profileService.delete(profileId)

        //verify
        Mockito.verify(profileRepo).deleteById(profileId)
    }

    @Test
    fun find() {
        //given
        val profileId = "myID"
        Mockito.`when`(profileRepo.findById(profileId)).thenReturn(Optional.of(Mockito.mock(UserProfile::class.java)))


        //when
        val profile: Profile? = profileService.find(profileId)

        //verify
        Mockito.verify(profileRepo).findById(profileId)
    }

    @Test
    fun findUserByPhone() {
        //given
        val phone = "08128155778"
        //when
        val profile: Profile? = profileService.findUserByPhone(phone)

        //verify
        Mockito.verify(profileRepo).findByMobileNumber(phone)
    }

    @Test
    fun `update - first time ICA acceptance writes audit log`() {
        // given
        val profileId = "icaUserId"
        val persisted = UserProfile("name", UserProfile.SignUpReason.BUY, "address", "https://img.url", "+27821234567", ProfileRoles.CUSTOMER)
        persisted.id = profileId
        persisted.icaAccepted = null  // not yet accepted

        val incoming = UserProfile("name", UserProfile.SignUpReason.BUY, "address", "https://img.url", "+27821234567", ProfileRoles.CUSTOMER)
        incoming.icaAccepted = true
        incoming.icaVersion = "v1"
        incoming.icaAcceptedDate = Date()

        val saved = UserProfile("name", UserProfile.SignUpReason.BUY, "address", "https://img.url", "+27821234567", ProfileRoles.CUSTOMER)
        saved.id = profileId
        saved.icaAccepted = true
        saved.icaVersion = "v1"
        saved.icaAcceptedDate = incoming.icaAcceptedDate
        saved.mobileNumber = "+27821234567"

        Mockito.`when`(profileRepo.findById(profileId)).thenReturn(Optional.of(persisted))
        Mockito.`when`(profileRepo.save(persisted)).thenReturn(saved)

        // when
        profileService.update(profileId, incoming)

        // verify audit log written
        val captor = ArgumentCaptor.forClass(IcaAcceptanceLog::class.java)
        verify(icaAcceptanceLogRepo).insert(captor.capture())
        Assert.assertEquals(profileId, captor.value.userId)
        Assert.assertEquals("v1", captor.value.icaVersion)
        Assert.assertEquals("+27821234567", captor.value.mobileNumber)
    }

    @Test
    fun `update - ICA already accepted does not write audit log`() {
        // given
        val profileId = "icaUserId2"
        val persisted = UserProfile("name", UserProfile.SignUpReason.BUY, "address", "https://img.url", "+27821234567", ProfileRoles.CUSTOMER)
        persisted.id = profileId
        persisted.icaAccepted = true  // already accepted

        val incoming = UserProfile("name", UserProfile.SignUpReason.BUY, "address", "https://img.url", "+27821234567", ProfileRoles.CUSTOMER)
        incoming.icaAccepted = true
        incoming.icaVersion = "v1"

        val saved = UserProfile("name", UserProfile.SignUpReason.BUY, "address", "https://img.url", "+27821234567", ProfileRoles.CUSTOMER)
        saved.id = profileId
        saved.icaAccepted = true
        saved.icaVersion = "v1"
        saved.mobileNumber = "+27821234567"

        Mockito.`when`(profileRepo.findById(profileId)).thenReturn(Optional.of(persisted))
        Mockito.`when`(profileRepo.save(persisted)).thenReturn(saved)

        // when
        profileService.update(profileId, incoming)

        // verify audit log NOT written
        verify(icaAcceptanceLogRepo, never()).insert(Mockito.any(IcaAcceptanceLog::class.java))
    }

    @Test
    fun `update - ICA not set in request does not write audit log`() {
        // given
        val profileId = "icaUserId3"
        val persisted = UserProfile("name", UserProfile.SignUpReason.BUY, "address", "https://img.url", "+27821234567", ProfileRoles.CUSTOMER)
        persisted.id = profileId
        persisted.icaAccepted = null

        val incoming = UserProfile("name", UserProfile.SignUpReason.BUY, "address", "https://img.url", "+27821234567", ProfileRoles.CUSTOMER)
        incoming.icaAccepted = null

        val saved = UserProfile("name", UserProfile.SignUpReason.BUY, "address", "https://img.url", "+27821234567", ProfileRoles.CUSTOMER)
        saved.id = profileId
        saved.icaAccepted = null
        saved.mobileNumber = "+27821234567"

        Mockito.`when`(profileRepo.findById(profileId)).thenReturn(Optional.of(persisted))
        Mockito.`when`(profileRepo.save(persisted)).thenReturn(saved)

        // when
        profileService.update(profileId, incoming)

        // verify audit log NOT written
        verify(icaAcceptanceLogRepo, never()).insert(Mockito.any(IcaAcceptanceLog::class.java))
    }

    @Test
    fun findMessengerByLocation() {
        //given
        val latitude = 10.0
        val longitude = 10.0
        val range = 0.2
        val patchProfileRequest = UserProfile(
            "secondName",
            UserProfile.SignUpReason.BUY,
            "address2",
            "https://image.url2",
            "078mobilenumb",
            ProfileRoles.MESSENGER
        )

        //when
        Mockito.`when`(
            profileRepo.findByRoleAndLatitudeBetweenAndLongitudeBetween(
                ProfileRoles.MESSENGER,
                latitude - range, latitude + range, longitude - range, longitude + range
            )
        )
            .thenReturn(listOf(patchProfileRequest))
        val messangers = profileService.findByLocation(ProfileRoles.MESSENGER, latitude, longitude, range, StoreType.FOOD)

        //verify
        Assert.assertEquals(1L, messangers?.size?.toLong())
        Mockito.verify(profileRepo).findByRoleAndLatitudeBetweenAndLongitudeBetween(
            ProfileRoles.MESSENGER,
            latitude - range, latitude + range, longitude - range, longitude + range
        )
    }
}