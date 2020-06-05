package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.BusinessHours;
import io.curiousoft.ijudi.ordermanagent.model.Stock;
import io.curiousoft.ijudi.ordermanagent.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagent.repo.StoreRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.Assert.fail;
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


    @Test
    public void findFeatured() {

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
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);

        StoreProfile initialProfile2 = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                "customer",
                businessHours);
        initialProfile2.setBusinessHours(new ArrayList<>());
        initialProfile2.setFeatured(true);
        Date date2 = Date.from(LocalDateTime.now().minusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile2.setFeaturedExpiry(date2);

        List<StoreProfile> initialProfiles = new ArrayList<>();
        initialProfiles.add(initialProfile);
        initialProfiles.add(initialProfile2);

        //when
        when(storeRepository.findByFeatured(true)).thenReturn(initialProfiles);
        List<StoreProfile> returnedProfiles = storeService.findFeatured();

        //verify
        verify(storeRepository).findByFeatured(true);
        verify(storeRepository, never()).save(initialProfile);
        Assert.assertEquals(1, returnedProfiles.size());
    }

    @Test
    public void findFeaturedNoExpiry() {

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
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);

        StoreProfile initialProfile2 = new StoreProfile(
                "name",
                "address",
                "https://image.url",
                "081mobilenumb",
                "customer",
                businessHours);
        initialProfile2.setBusinessHours(new ArrayList<>());
        initialProfile2.setFeatured(true);

        List<StoreProfile> initialProfiles = new ArrayList<>();
        initialProfiles.add(initialProfile);
        initialProfiles.add(initialProfile2);

        //when
        when(storeRepository.findByFeatured(true)).thenReturn(initialProfiles);
        List<StoreProfile> returnedProfiles = storeService.findFeatured();

        //verify
        verify(storeRepository).findByFeatured(true);
        verify(storeRepository, never()).save(initialProfile);
        Assert.assertEquals(1, returnedProfiles.size());
    }

    @Test
    public void getStock() throws Exception {
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
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);

        Stock stock1 = new Stock("bananas 1kg", 24, 15, 0);
        Stock stock2 = new Stock("bananas 1kg", 24, 15, 0);
        List<Stock> stockList = new ArrayList<>();
        stockList.add(stock1);
        stockList.add(stock2);
        initialProfile.setStockList(stockList);

        //when
        when(storeRepository.findById(profileId)).thenReturn(Optional.of(initialProfile));
        List<Stock> returnedProfiles = storeService.findStockForShop(profileId);

        //verify
        verify(storeRepository).findById(profileId);
        Assert.assertNotNull(returnedProfiles);
        Assert.assertEquals(2, returnedProfiles.size());

    }

    @Test
    public void addStock() throws Exception {
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
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);

        Stock stock1 = new Stock("bananas 1kg", 24, 15, 0);
        List<Stock> stockList = new ArrayList<>();
        stockList.add(stock1);
        initialProfile.setStockList(stockList);

        Stock stock2 = new Stock("bananas 1kg", 24, 15, 0);

        //when
        when(storeRepository.findById(profileId)).thenReturn(Optional.of(initialProfile));
        storeService.addStockForShop(profileId, stock2);

        //verify
        verify(storeRepository).findById(profileId);
        verify(storeRepository).save(initialProfile);

    }

    @Test
    public void addStockNoPricing() {
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
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);

        Stock stock1 = new Stock("bananas 1kg", 24, 12, 0);
        List<Stock> stockList = new ArrayList<>();
        stockList.add(stock1);
        initialProfile.setStockList(stockList);

        Stock stock2 = new Stock("bananas 1kg", 24, 0, 0);

        //when
        try {
            storeService.addStockForShop(profileId, stock2);
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("stock price must be greater than or equal to 0.01", e.getMessage());
        }
        //verify
        verifyNoInteractions(storeRepository);

    }

    @Test
    public void addStockNoName() {
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
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);

        Stock stock1 = new Stock("bananas 1kg", 24, 12, 0);
        List<Stock> stockList = new ArrayList<>();
        stockList.add(stock1);
        initialProfile.setStockList(stockList);

        Stock stock2 = new Stock("", 24, 12, 0);

        //when
        try {
            storeService.addStockForShop(profileId, stock2);
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("stock name must not be blank", e.getMessage());
        }
        //verify
        verifyNoInteractions(storeRepository);
    }
}