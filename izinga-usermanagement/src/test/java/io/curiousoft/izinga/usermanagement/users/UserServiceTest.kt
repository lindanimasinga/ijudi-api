package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.Profile
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import java.util.*

@RunWith(MockitoJUnitRunner::class)
class UserServiceTest {
    //system under test
    lateinit var profileService: UserProfileService

    @Mock
    lateinit var profileRepo: UserProfileRepo
    
    @Before
    fun setUp() {
        profileService = UserProfileService(profileRepo)
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
        val profile: Profile = profileService.find(profileId)

        //verify
        Mockito.verify(profileRepo).findById(profileId)
    }

    @Test
    fun findUserByPhone() {
        //given
        val phone = "myID"
        //when
        val profile: Profile? = profileService.findUserByPhone(phone)

        //verify
        Mockito.verify(profileRepo).findByMobileNumber(phone)
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
        val messangers = profileService.findByLocation(ProfileRoles.MESSENGER, latitude, longitude, range)

        //verify
        Assert.assertEquals(1L, messangers?.size?.toLong())
        Mockito.verify(profileRepo).findByRoleAndLatitudeBetweenAndLongitudeBetween(
            ProfileRoles.MESSENGER,
            latitude - range, latitude + range, longitude - range, longitude + range
        )
    }
}