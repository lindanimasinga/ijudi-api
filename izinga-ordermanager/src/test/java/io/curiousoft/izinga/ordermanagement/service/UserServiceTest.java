package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.Profile;
import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {

    //system under test
    private UserProfileService profileService;
    @Mock
    private UserProfileRepo profileRepo;

    @Before
    public void setUp() {
        profileService = new UserProfileService(profileRepo);
    }

    @Test
    public void create() throws Exception {

        //given
        UserProfile initialProfile = new UserProfile(
                "name",
                UserProfile.SignUpReason.BUY,
                "address",
                "https://image.url",
                "081mobilenumb",
                ProfileRoles.CUSTOMER);

        //when
        when(profileRepo.save(initialProfile)).thenReturn(initialProfile);
        when(profileRepo.existsByMobileNumber(initialProfile.getMobileNumber())).thenReturn(false);
        Profile profile = profileService.create(initialProfile);

        //verify
        verify(profileRepo).existsByMobileNumber(initialProfile.getMobileNumber());
        verify(profileRepo).save(initialProfile);
        Assert.assertNotNull(profile.getId());
    }

    @Test
    public void createAlreadyExists() throws Exception {

        //given
        UserProfile initialProfile = new UserProfile(
                "name",
                UserProfile.SignUpReason.BUY,
                "address",
                "https://image.url",
                "081mobilenumb",
                ProfileRoles.CUSTOMER);

        //when
        when(profileRepo.existsByMobileNumber(initialProfile.getMobileNumber())).thenReturn(true);
        try {
            Profile profile = profileService.create(initialProfile);
            fail();
        } catch (Exception e) {
            Assert.assertEquals("User with phone number " + initialProfile.getMobileNumber() + " already exist.",e.getMessage());
        }
    }

    @Test
    public void update() throws Exception {

        //given
        String profileId = "myID";
        UserProfile initialProfile = new UserProfile(
                "name",
                UserProfile.SignUpReason.BUY,
                "address",
                "https://image.url",
                "081mobilenumb",
                ProfileRoles.CUSTOMER);

        UserProfile patchProfileRequest = new UserProfile(
                "secondName",
                UserProfile.SignUpReason.BUY,
                "address2",
                "https://image.url2",
                "078mobilenumb",
                ProfileRoles.MESSENGER);

        //when
        when(profileRepo.findById(profileId)).thenReturn(Optional.of(initialProfile));
        when(profileRepo.save(initialProfile)).thenReturn(initialProfile);
        Profile profile = profileService.update(profileId, patchProfileRequest);

        //verify
        verify(profileRepo).findById(profileId);
        verify(profileRepo).save(initialProfile);
    }

    @Test
    public void delete() {
        //given
        String profileId = "myID";
        //when

        profileService.delete(profileId);

        //verify
        verify(profileRepo).deleteById(profileId);
    }

    @Test
    public void find() {

        //given
        String profileId = "myID";
        //when

        Profile profile = profileService.find(profileId);

        //verify
        verify(profileRepo).findById(profileId);
    }

    @Test
    public void findUserByPhone() {
        //given
        String phone = "myID";
        //when

        Profile profile = profileService.findUserByPhone(phone);

        //verify
        verify(profileRepo).findByMobileNumber(phone);
    }

    @Test
    public void findMessengerByLocation() {
        //given
        double latitude = 10;
        double longitude = 10;
        double range =  0.2;

        UserProfile patchProfileRequest = new UserProfile(
                "secondName",
                UserProfile.SignUpReason.BUY,
                "address2",
                "https://image.url2",
                "078mobilenumb",
                ProfileRoles.MESSENGER);

        //when
        when(profileRepo.findByRoleAndLatitudeBetweenAndLongitudeBetween(ProfileRoles.MESSENGER,
                latitude - range, latitude + range, longitude - range, longitude + range))
                .thenReturn(Collections.singletonList(patchProfileRequest));

        List<UserProfile> messangers = profileService.findByLocation(ProfileRoles.MESSENGER, latitude, longitude, range);

        //verify
        Assert.assertEquals(1, messangers.size());
        verify(profileRepo).findByRoleAndLatitudeBetweenAndLongitudeBetween(ProfileRoles.MESSENGER,
                latitude - range, latitude + range, longitude - range, longitude + range);
    }
}