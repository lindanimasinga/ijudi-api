package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagement.repo.UserProfileRepo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class StoreServiceTest {

    public static final String MAIN_PAY_ACCOUNT = "123456";
    //system under test
    private StoreService storeService;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    UserProfileRepo userProfileRepo;

    @Before
    public void setUp() {
        storeService = new StoreService(storeRepository, userProfileRepo, MAIN_PAY_ACCOUNT, 0.1);
    }

    @Test
    public void create() throws Exception {

        //given
        UserProfile user = new UserProfile(
                "name",
                UserProfile.SignUpReason.BUY,
                "address",
                "https://image.url",
                "081mobilenumb",
                ProfileRoles.CUSTOMER);

        Bank bank = new Bank();
        bank.setAccountId("accountId");
        bank.setName("ukheshe");
        bank.setPhone("phoneNumber");
        bank.setType("wallet");
        user.setBank(bank);

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        BusinessHours hours = new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date());
        businessHours.add(hours);
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                businessHours,
                "ownerId",
                new Bank());


        //when
        when(userProfileRepo.findById(initialProfile.getOwnerId())).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(initialProfile.getId(), initialProfile.getShortName())).thenReturn(Optional.empty());
        when(storeRepository.save(initialProfile)).thenReturn(initialProfile);
        StoreProfile profile = storeService.create(initialProfile);

        //verify
        verify(userProfileRepo).findById(initialProfile.getOwnerId());
        verify(storeRepository).save(initialProfile);
        verify(userProfileRepo).save(user);

        Assert.assertNotNull(profile.getId());
        Assert.assertNotNull(profile.getOwnerId());
        Assert.assertNotNull(profile.getBank());
        Assert.assertEquals(user.getBank().getAccountId(), profile.getBank().getAccountId());
        Assert.assertEquals(user.getBank().getPhone(), profile.getBank().getPhone());
        Assert.assertEquals(ProfileRoles.STORE_ADMIN, user.getRole());
    }

    @Test
    public void createStore_with_stock() throws Exception {

        //given
        UserProfile user = new UserProfile(
                "name",
                UserProfile.SignUpReason.BUY,
                "address",
                "https://image.url",
                "081mobilenumb",
                ProfileRoles.CUSTOMER);

        Bank bank = new Bank();
        bank.setAccountId("accountId");
        bank.setName("ukheshe");
        bank.setPhone("phoneNumber");
        bank.setType("wallet");
        user.setBank(bank);

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        BusinessHours hours = new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date());
        businessHours.add(hours);
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                businessHours,
                "ownerId",
                new Bank());
        HashSet<Stock> set = new HashSet<>();
        Stock stock2 = new Stock("bananas 1kg", 24, 15, 0, Collections.emptyList());
        Stock stock3 = new Stock("orange 1kg", 24, 15.90, 0, Collections.emptyList());
        Stock stock4 = new Stock("mango 1kg", 24, 15.99, 0, Collections.emptyList());
        Stock stock5 = new Stock("litchi 1kg", 24, 15.45, 0, Collections.emptyList());
        Stock stock6 = new Stock("Chicken", 24, 199.90, 0, Collections.emptyList());
        set.add(stock2);
        set.add(stock3);
        set.add(stock4);
        set.add(stock5);
        set.add(stock6);
        initialProfile.setStockList(set);

        when(userProfileRepo.findById(initialProfile.getOwnerId())).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(initialProfile.getId(), initialProfile.getShortName())).thenReturn(Optional.empty());
        when(storeRepository.save(initialProfile)).thenReturn(initialProfile);

        //when
        StoreProfile profile = storeService.create(initialProfile);

        //verify
        verify(userProfileRepo).findById(initialProfile.getOwnerId());
        verify(storeRepository).save(initialProfile);
        verify(userProfileRepo).save(user);

        Assert.assertNotNull(profile.getId());
        Assert.assertNotNull(profile.getOwnerId());
        Assert.assertNotNull(profile.getBank());
        Assert.assertEquals(user.getBank().getAccountId(), profile.getBank().getAccountId());
        Assert.assertEquals(user.getBank().getPhone(), profile.getBank().getPhone());
        Assert.assertEquals(ProfileRoles.STORE_ADMIN, user.getRole());

        /*
        if mark % is 10% calculate markup. If the decimals of the original price was .00 then keep .00 in the
        mark up price. If decimals was .90 or .99 then keep the same decimals
        * */
        Assert.assertEquals(17.00, stock2.getPrice(), 0.001);
        Assert.assertEquals(17.90, stock3.getPrice(), 0.001);
        Assert.assertEquals(17.99, stock4.getPrice(), 0.001);
        Assert.assertEquals(17.45, stock5.getPrice(), 0.001);
        Assert.assertEquals(219.9, stock6.getPrice(), 0.001);
    }

    @Test
    public void createStore_with_stock_no_markup() throws Exception {

        //given
        UserProfile user = new UserProfile(
                "name",
                UserProfile.SignUpReason.BUY,
                "address",
                "https://image.url",
                "081mobilenumb",
                ProfileRoles.CUSTOMER);

        Bank bank = new Bank();
        bank.setAccountId("accountId");
        bank.setName("ukheshe");
        bank.setPhone("phoneNumber");
        bank.setType("wallet");
        user.setBank(bank);

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        BusinessHours hours = new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date());
        businessHours.add(hours);
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setMarkUpPrice(false);
        HashSet<Stock> set = new HashSet<>();
        Stock stock2 = new Stock("bananas 1kg", 24, 15, 0, Collections.emptyList());
        Stock stock3 = new Stock("orange 1kg", 24, 15.90, 0, Collections.emptyList());
        Stock stock4 = new Stock("mango 1kg", 24, 15.99, 0, Collections.emptyList());
        Stock stock5 = new Stock("litchi 1kg", 24, 15.45, 0, Collections.emptyList());
        Stock stock6 = new Stock("Chicken", 24, 199.90, 0, Collections.emptyList());
        set.add(stock2);
        set.add(stock3);
        set.add(stock4);
        set.add(stock5);
        set.add(stock6);
        initialProfile.setStockList(set);

        when(userProfileRepo.findById(initialProfile.getOwnerId())).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(initialProfile.getId(), initialProfile.getShortName())).thenReturn(Optional.empty());
        when(storeRepository.save(initialProfile)).thenReturn(initialProfile);

        //when
        StoreProfile profile = storeService.create(initialProfile);

        //verify
        verify(userProfileRepo).findById(initialProfile.getOwnerId());
        verify(storeRepository).save(initialProfile);
        verify(userProfileRepo).save(user);

        Assert.assertNotNull(profile.getId());
        Assert.assertNotNull(profile.getOwnerId());
        Assert.assertNotNull(profile.getBank());
        Assert.assertEquals(user.getBank().getAccountId(), profile.getBank().getAccountId());
        Assert.assertEquals(user.getBank().getPhone(), profile.getBank().getPhone());
        Assert.assertEquals(ProfileRoles.STORE_ADMIN, user.getRole());

        /*
        if mark % is 10% calculate markup. If the decimals of the original price was .00 then keep .00 in the
        mark up price. If decimals was .90 or .99 then keep the same decimals
        * */
        Assert.assertEquals(15.00, stock2.getPrice(), 0.001);
        Assert.assertEquals(15.90, stock3.getPrice(), 0.001);
        Assert.assertEquals(15.99, stock4.getPrice(), 0.001);
        Assert.assertEquals(15.45, stock5.getPrice(), 0.001);
        Assert.assertEquals(199.9, stock6.getPrice(), 0.001);
    }

    @Test
    public void create_invalid_shortName() throws Exception {

        //given
        UserProfile user = new UserProfile(
                "name",
                UserProfile.SignUpReason.BUY,
                "address",
                "https://image.url",
                "081mobilenumb",
                ProfileRoles.CUSTOMER);

        Bank bank = new Bank();
        bank.setAccountId("accountId");
        bank.setName("ukheshe");
        bank.setPhone("phoneNumber");
        bank.setType("wallet");
        user.setBank(bank);

        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        BusinessHours hours = new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date());
        businessHours.add(hours);
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());


        //when
        when(userProfileRepo.findById(initialProfile.getOwnerId())).thenReturn(Optional.of(user));
        when(storeRepository.findOneByIdOrShortName(initialProfile.getId(), initialProfile.getShortName())).thenReturn(Optional.of(initialProfile));

        try {
            StoreProfile profile = storeService.create(initialProfile);
            fail();
        } catch (Exception e) {
            assertEquals("Shop shortname or id already exists. Please try a different shortname", e.getMessage());
        }

        //verify
        verify(userProfileRepo).findById(initialProfile.getOwnerId());
        verify(storeRepository).findOneByIdOrShortName(initialProfile.getId(), initialProfile.getShortName());
        Assert.assertEquals(ProfileRoles.CUSTOMER, user.getRole());
    }

    @Test
    public void createOwnerNotExist() throws Exception {

        //given
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                null,
                "ownerId",
                new Bank());

        //when
        when(userProfileRepo.findById(initialProfile.getOwnerId())).thenReturn(Optional.empty());
        try {
            storeService.create(initialProfile);
        } catch (Exception e) {
            Assert.assertEquals("Store owner with user id does not exist.", e.getMessage());
        }

        //verify
        verify(userProfileRepo).findById(initialProfile.getOwnerId());
    }

    @Test
    public void findWithNoBusinessHours() {
        //given
        String profileId = "myID";
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                null,
                "ownerId",
                new Bank());

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
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
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
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);
        Bank bank = new Bank();
        bank.setAccountId("34567890");
        initialProfile.setBank(bank);

        StoreProfile initialProfile2 = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        initialProfile2.setBusinessHours(new ArrayList<>());
        initialProfile2.setFeatured(true);
        Date date2 = Date.from(LocalDateTime.now().minusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile2.setFeaturedExpiry(date2);
        Bank bank2 = new Bank();
        bank2.setAccountId("34567890");
        initialProfile2.setBank(bank2);

        List<StoreProfile> initialProfiles = new ArrayList<>();
        initialProfiles.add(initialProfile);
        initialProfiles.add(initialProfile2);

        double latitude = 1;
        double longitude = 1;
        double range = 1;
        int maxStores = 1;

        //when
        when(storeRepository.findByLatitudeBetweenAndLongitudeBetweenAndStoreType(latitude - range,
                latitude + range, longitude - range, longitude + range, StoreType.FOOD))
                .thenReturn(initialProfiles);
        List<StoreProfile> returnedProfiles = storeService.findFeatured(latitude, longitude, StoreType.FOOD, range, maxStores);

        //verify
        verify(storeRepository).findByLatitudeBetweenAndLongitudeBetweenAndStoreType(latitude - range,
                latitude + range, longitude - range, longitude + range, StoreType.FOOD);
        verify(storeRepository, never()).save(initialProfile);
        Assert.assertEquals(1, returnedProfiles.size());
        Assert.assertEquals(MAIN_PAY_ACCOUNT, returnedProfiles.get(0).getBank().getAccountId());
    }

    @Test
    public void findStoresByLocation() {

        //given
        String profileId = "myID";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);

        Bank bank = new Bank();
        bank.setAccountId("34567890");
        initialProfile.setBank(bank);

        StoreProfile initialProfile2 = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        initialProfile2.setBusinessHours(new ArrayList<>());
        initialProfile2.setFeatured(true);
        Date date2 = Date.from(LocalDateTime.now().minusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile2.setFeaturedExpiry(date2);
        Bank bank2 = new Bank();
        bank2.setAccountId("34567890");
        initialProfile2.setBank(bank2);

        List<StoreProfile> initialProfiles = new ArrayList<>();
        initialProfiles.add(initialProfile);
        initialProfiles.add(initialProfile2);

        double latitude = 10.10, longitude = 10.10, range = 10;
        int maxLocations = 10;

        //when
        when(storeRepository
                .findByLatitudeBetweenAndLongitudeBetweenAndStoreType(latitude - range, latitude + range, longitude - range, longitude + range, StoreType.FOOD))
                .thenReturn(initialProfiles);

        List<StoreProfile> returnedProfiles = storeService.findNearbyStores(longitude, latitude, StoreType.FOOD,
                range, maxLocations);

        //verify
        verify(storeRepository).findByLatitudeBetweenAndLongitudeBetweenAndStoreType(latitude - range, latitude + range, longitude - range, longitude + range, StoreType.FOOD);
        verify(storeRepository, never()).save(initialProfile);
        Assert.assertEquals(2, returnedProfiles.size());
        Assert.assertEquals(MAIN_PAY_ACCOUNT, returnedProfiles.get(0).getBank().getAccountId());
    }

    @Test
    public void findFeaturedNoExpiry() {

        //given
        String profileId = "myID";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);
        Bank bank = new Bank();
        bank.setAccountId("453453453456");
        initialProfile.setBank(bank);

        StoreProfile initialProfile2 = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        initialProfile2.setBusinessHours(new ArrayList<>());
        initialProfile2.setFeatured(true);

        List<StoreProfile> initialProfiles = new ArrayList<>();
        initialProfiles.add(initialProfile);
        initialProfiles.add(initialProfile2);

        double latitude = 10.10, longitude = 10.10, range = 10;
        int maxLocations = 10;

        //when
        when(storeRepository
                .findByLatitudeBetweenAndLongitudeBetweenAndStoreType(latitude - range,
                        latitude + range,
                        longitude - range,
                        longitude + range, StoreType.FOOD))
                .thenReturn(initialProfiles);
        List<StoreProfile> returnedProfiles = storeService.findFeatured(latitude, latitude, StoreType.FOOD, range, maxLocations);

        //verify
        verify(storeRepository).findByLatitudeBetweenAndLongitudeBetweenAndStoreType(latitude - range,
                latitude + range,
                longitude - range,
                longitude + range, StoreType.FOOD);
        verify(storeRepository, never()).save(initialProfile);
        Assert.assertEquals(1, returnedProfiles.size());
        Assert.assertEquals(MAIN_PAY_ACCOUNT, returnedProfiles.get(0).getBank().getAccountId());
    }

    @Test
    public void findStoreByOwerId() {
        //given
        String ownerId = "hakshdakjshdkl";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);
        Bank bank = new Bank();
        bank.setAccountId("453453453456");
        initialProfile.setBank(bank);

        StoreProfile initialProfile2 = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        initialProfile2.setBusinessHours(new ArrayList<>());
        initialProfile2.setFeatured(true);

        List<StoreProfile> initialProfiles = new ArrayList<>();
        initialProfiles.add(initialProfile);
        initialProfiles.add(initialProfile2);

        //when

        when(storeRepository.findByOwnerId(ownerId)).thenReturn(initialProfiles);
        List<StoreProfile> returnedProfiles = storeService.findByOwner(ownerId);

        //verify
        verify(storeRepository).findByOwnerId(ownerId);
        verify(storeRepository, never()).save(initialProfile);
        Assert.assertEquals(2, returnedProfiles.size());
        Assert.assertEquals(initialProfile.getBank().getAccountId(), returnedProfiles.get(0).getBank().getAccountId());
    }

    @Test
    public void getStock() throws Exception {
        //given
        String profileId = "myID";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);

        Stock stock1 = new Stock("bananas 1kg", 24, 15, 0, Collections.emptyList());
        Stock stock2 = new Stock("bananas 1kg", 24, 16, 0, Collections.emptyList());
        stock1.setPrice(15.0);
        stock2.setPrice(16.0);
        Set<Stock> stockList = new HashSet<>();
        stockList.add(stock1);
        stockList.add(stock2);
        initialProfile.setStockList(stockList);

        //when
        when(storeRepository.findById(profileId)).thenReturn(Optional.of(initialProfile));
        Set<Stock> stockForShop = storeService.findStockForShop(profileId);

        //verify
        verify(storeRepository).findById(profileId);
        Assert.assertNotNull(stockForShop);
        Assert.assertEquals(1, stockForShop.size());
        Assert.assertEquals(15, stockForShop.iterator().next().getPrice(), 0);
        Assert.assertEquals(15, stockForShop.iterator().next().getStorePrice(), 0);

    }

    @Test
    public void updateStore() throws Exception {
        //given
        String profileId = "myID";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);

        Stock stock2 = new Stock("bananas 1kg", 24, 15, 0, Collections.emptyList());
        Stock stock3 = new Stock("orange 1kg", 24, 15.90, 0, Collections.emptyList());
        Stock stock4 = new Stock("mango 1kg", 24, 15.99, 0, Collections.emptyList());
        Stock stock5 = new Stock("litchi 1kg", 24, 15.45, 0, Collections.emptyList());
        Stock stock6 = new Stock("Chicken", 24, 199.90, 0, Collections.emptyList());

        initialProfile.getStockList().add(stock2);
        initialProfile.getStockList().add(stock3);
        initialProfile.getStockList().add(stock4);
        initialProfile.getStockList().add(stock5);
        initialProfile.getStockList().add(stock6);

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(initialProfile));

        //when
        storeService.update(profileId, initialProfile);

        //verify
        verify(storeRepository).findById(profileId);
        Assert.assertEquals(5, initialProfile.getStockList().size());
        verify(storeRepository).save(initialProfile);

        /*
        if mark % is 10% calculate markup. If the decimals of the original price was .00 then keep .00 in the
        mark up price. If decimals was .90 or .99 then keep the same decimals
        * */
        Assert.assertEquals(17.00, stock2.getPrice(), 0.001);
        Assert.assertEquals(17.90, stock3.getPrice(), 0.001);
        Assert.assertEquals(17.99, stock4.getPrice(), 0.001);
        Assert.assertEquals(17.45, stock5.getPrice(), 0.001);
        Assert.assertEquals(219.9, stock6.getPrice(), 0.001);
    }

    @Test
    public void updateStore_markup_off() throws Exception {
        //given
        String profileId = "myID";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        initialProfile.setMarkUpPrice(false);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);

        Stock stock2 = new Stock("bananas 1kg", 24, 15, 0, Collections.emptyList());
        Stock stock3 = new Stock("orange 1kg", 24, 15.90, 0, Collections.emptyList());
        Stock stock4 = new Stock("mango 1kg", 24, 15.99, 0, Collections.emptyList());
        Stock stock5 = new Stock("litchi 1kg", 24, 15.45, 0, Collections.emptyList());
        Stock stock6 = new Stock("Chicken", 24, 199.90, 0, Collections.emptyList());

        initialProfile.getStockList().add(stock2);
        initialProfile.getStockList().add(stock3);
        initialProfile.getStockList().add(stock4);
        initialProfile.getStockList().add(stock5);
        initialProfile.getStockList().add(stock6);

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(initialProfile));

        //when
        storeService.update(profileId, initialProfile);

        //verify
        verify(storeRepository).findById(profileId);
        Assert.assertEquals(5, initialProfile.getStockList().size());
        verify(storeRepository).save(initialProfile);

        /*
        if mark % is 10% calculate markup. If the decimals of the original price was .00 then keep .00 in the
        mark up price. If decimals was .90 or .99 then keep the same decimals
        * */
        Assert.assertEquals(15.00, stock2.getPrice(), 0.001);
        Assert.assertEquals(15.90, stock3.getPrice(), 0.001);
        Assert.assertEquals(15.99, stock4.getPrice(), 0.001);
        Assert.assertEquals(15.45, stock5.getPrice(), 0.001);
        Assert.assertEquals(199.90, stock6.getPrice(), 0.001);
    }

    @Test
    public void addStock() throws Exception {
        //given
        String profileId = "myID";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);

        Stock stock2 = new Stock("bananas 1kg", 24, 15, 0, Collections.emptyList());
        Stock stock3 = new Stock("orange 1kg", 24, 15.90, 0, Collections.emptyList());
        Stock stock4 = new Stock("mango 1kg", 24, 15.99, 0, Collections.emptyList());
        Stock stock5 = new Stock("litchi 1kg", 24, 15.45, 0, Collections.emptyList());
        Stock stock6 = new Stock("Chicken", 24, 199.90, 0, Collections.emptyList());

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(initialProfile));

        //when
        storeService.addStockForShop(profileId, stock2);
        storeService.addStockForShop(profileId, stock3);
        storeService.addStockForShop(profileId, stock4);
        storeService.addStockForShop(profileId, stock5);
        storeService.addStockForShop(profileId, stock6);

        //verify
        verify(storeRepository, times(5)).findById(profileId);
        Assert.assertEquals(5, initialProfile.getStockList().size());
        verify(storeRepository, times(5)).save(initialProfile);

        /*
        if mark % is 10% calculate markup. If the decimals of the original price was .00 then keep .00 in the
        mark up price. If decimals was .90 or .99 then keep the same decimals
        * */
        Assert.assertEquals(17.00, stock2.getPrice(), 0.001);
        Assert.assertEquals(17.90, stock3.getPrice(), 0.001);
        Assert.assertEquals(17.99, stock4.getPrice(), 0.001);
        Assert.assertEquals(17.45, stock5.getPrice(), 0.001);
        Assert.assertEquals(219.9, stock6.getPrice(), 0.001);
    }

    @Test
    public void addStock_AlreadyExist() throws Exception {
        //given
        String profileId = "myID";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);

        Stock stock1 = new Stock("bananas 1kg", 24, 15, 0, Collections.emptyList());
        Set<Stock> stockList = new HashSet<>();
        stockList.add(stock1);
        initialProfile.setStockList(stockList);

        Stock stock2 = new Stock("bananas 1kg", 24, 18, 0, Collections.emptyList());

        //when
        when(storeRepository.findById(profileId)).thenReturn(Optional.of(initialProfile));
        storeService.addStockForShop(profileId, stock2);

        //verify
        verify(storeRepository).findById(profileId);
        Assert.assertEquals(1, initialProfile.getStockList().size());
        Assert.assertEquals(18, initialProfile.getStockList().iterator().next().getStorePrice(), 0);
        Assert.assertEquals(20, initialProfile.getStockList().iterator().next().getPrice(), 0);
        verify(storeRepository).save(initialProfile);

    }

    @Test
    public void addStockNoPricing() {
        //given
        String profileId = "myID";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,
                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        initialProfile.setId(profileId);

        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);

        Stock stock1 = new Stock("bananas 1kg", 24, 12, 0, Collections.emptyList());
        Set<Stock> stockList = new HashSet<>();
        stockList.add(stock1);
        initialProfile.setStockList(stockList);

        Stock stock2 = new Stock("bananas 1kg", 24, 0, 0, Collections.emptyList());

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(initialProfile));
        when(storeRepository.save(initialProfile)).thenReturn(initialProfile);

        //when
        try {
            storeService.addStockForShop(profileId, stock2);
        } catch (Exception ignored) {
        }

        //verify
        verify(storeRepository).findById(profileId);
        verify(storeRepository).save(initialProfile);
    }

    @Test
    public void addStockNoName() {
        //given
        String profileId = "myID";
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        List<String> tags = Collections.singletonList("Pizza");
        StoreProfile initialProfile = new StoreProfile(
                StoreType.FOOD,
                "name", "shortname",
                "address",
                "https://image.url",
                "081mobilenumb",
                tags,

                businessHours,
                "ownerId",
                new Bank());
        initialProfile.setBusinessHours(new ArrayList<>());
        initialProfile.setFeatured(true);
        Date date = Date.from(LocalDateTime.now().plusDays(5).atZone(ZoneId.systemDefault()).toInstant());
        initialProfile.setFeaturedExpiry(date);

        Stock stock1 = new Stock("bananas 1kg", 24, 12, 0, Collections.emptyList());
        Set<Stock> stockList = new HashSet<>();
        stockList.add(stock1);
        initialProfile.setStockList(stockList);

        Stock stock2 = new Stock("", 24, 12, 0, Collections.emptyList());

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