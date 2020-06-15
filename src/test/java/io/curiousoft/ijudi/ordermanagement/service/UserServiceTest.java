package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.Profile;
import io.curiousoft.ijudi.ordermanagement.model.ProfileRoles;
import io.curiousoft.ijudi.ordermanagement.model.UserProfile;
import io.curiousoft.ijudi.ordermanagement.repo.UserProfileRepo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

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
                "address",
                "https://image.url",
                "081mobilenumb",
                ProfileRoles.CUSTOMER);

        //when
        when(profileRepo.save(initialProfile)).thenReturn(initialProfile);
        Profile profile = profileService.create(initialProfile);

        //verify
        verify(profileRepo).save(initialProfile);
        Assert.assertNotNull(profile.getId());
    }

    @Test
    public void update() throws Exception {

        //given
        String profileId = "myID";
        UserProfile initialProfile = new UserProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                ProfileRoles.CUSTOMER);

        UserProfile patchProfileRequest = new UserProfile(
                "secondName",
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
    public void findOrderByPhone() {
        //given
        String phone = "myID";
        //when

        Profile profile = profileService.findOrderByPhone(phone);

        //verify
        verify(profileRepo).findByMobileNumber(phone);
    }
}