package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.BusinessHours;
import io.curiousoft.ijudi.ordermanagent.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagent.repo.StoreRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Optional;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class StoreServiceTest {

    //system under test
    private StoreService storeService;
    @Mock
    private StoreRepository storeRepository;

    @Before
    public void setUp() {
        storeService = new StoreService(storeRepository);
    }

    @Test
    public void findWithNoBusinessHours() {
        //given
        String profileId = "myID";
        StoreProfile initialProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                "customer",
                null);

        //when
        when(storeRepository.findById(profileId)).thenReturn(Optional.of(initialProfile));
        StoreProfile profile = storeService.find(profileId);

        //verify
        verify(storeRepository).findById(profileId);
        verify(storeRepository).save(initialProfile);
    }

    @Test
    public void findWithWithBusinessHours() {
        //given
        String profileId = "myID";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        StoreProfile initialProfile = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                "customer",
                businessHours);
        initialProfile.setBusinessHours(new ArrayList<>());

        //when
        when(storeRepository.findById(profileId)).thenReturn(Optional.of(initialProfile));
        StoreProfile profile = storeService.find(profileId);

        //verify
        verify(storeRepository).findById(profileId);
        verify(storeRepository, never()).save(initialProfile);
    }
}