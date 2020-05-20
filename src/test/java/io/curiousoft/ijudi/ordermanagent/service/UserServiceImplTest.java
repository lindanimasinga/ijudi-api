package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.Profile;
import io.curiousoft.ijudi.ordermanagent.repo.ProfileRepo;
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
public class UserServiceImplTest {

    //system under test
    private UserService userService;
    @Mock
    private ProfileRepo profileRepo;

    @Before
    public void setUp() {
        userService = new ProfileServiceImpl(profileRepo);
    }

    @Test
    public void create() throws Exception {

        //given
        Profile initialProfile = new Profile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                "customer");

        //when
        when(profileRepo.save(initialProfile)).thenReturn(initialProfile);
        Profile profile = userService.create(initialProfile);

        //verify
        verify(profileRepo).save(initialProfile);
        Assert.assertNotNull(profile.getId());
    }

    @Test
    public void update() throws Exception {

        //given
        String profileId = "myID";
        Profile initialProfile = new Profile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                "customer");

        Profile patchProfileRequest = new Profile(
                "secondName",
                "address2",
                "https://image.url2",
                "078mobilenumb",
                "messanger");

        //when
        when(profileRepo.findById(profileId)).thenReturn(Optional.of(initialProfile));
        when(profileRepo.save(initialProfile)).thenReturn(initialProfile);
        Profile profile = userService.update(profileId, patchProfileRequest);

        //verify
        verify(profileRepo).findById(profileId);
        verify(profileRepo).save(initialProfile);
    }

    @Test
    public void delete() {
        //given
        String profileId = "myID";
        //when

        userService.delete(profileId);

        //verify
        verify(profileRepo).deleteById(profileId);
    }

    @Test
    public void find() {

        //given
        String profileId = "myID";
        //when

        Profile profile = userService.find(profileId);

        //verify
        verify(profileRepo).findById(profileId);
    }
}