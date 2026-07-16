package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.stores.StoreService;
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationEventPublisher;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

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
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private ReferralCodeService referralCodeService;

    @Before
    public void setUp() {
        storeService = new StoreService(storeRepository, userProfileRepo, MAIN_PAY_ACCOUNT, 0.1, applicationEventPublisher, referralCodeService);
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
        bank.setType(BankAccType.CHEQUE);
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
        bank.setType(BankAccType.CHEQUE);
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
        bank.setType(BankAccType.CHEQUE);
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
        bank.setType(BankAccType.CHEQUE);
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
        HashSet<Stock> stockList = new HashSet<>();
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
        Assert.assertEquals(17, stockForShop.iterator().next().getPrice(), 0);
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
        HashSet<Stock> stockList = new HashSet<>();
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
        HashSet<Stock> stockList = new HashSet<>();
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

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // #57 — StoreProfile categories field: non-null, defaults to emptyList
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void storeProfile_categories_defaultsToEmptyList() {
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        businessHours.add(new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date()));
        StoreProfile store = new StoreProfile(
                StoreType.FOOD, "name", "shortname", "address",
                "https://image.url", "081mobilenumb",
                Collections.singletonList("tag"),
                businessHours, "ownerId", new Bank());

        Assert.assertNotNull("categories must never be null", store.getCategories());
        Assert.assertTrue("categories must default to empty list", store.getCategories().isEmpty());
    }

    @Test
    public void storeProfile_categories_canBeSetAndRetrieved() {
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        businessHours.add(new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date()));
        StoreProfile store = new StoreProfile(
                StoreType.FOOD, "name", "shortname", "address",
                "https://image.url", "081mobilenumb",
                Collections.singletonList("tag"),
                businessHours, "ownerId", new Bank());

        List<Category> cats = Collections.singletonList(new Category("id1", "tag", "", true));
        store.setCategories(cats);

        Assert.assertEquals(1, store.getCategories().size());
        Assert.assertEquals("tag", store.getCategories().get(0).getName());
        Assert.assertTrue(store.getCategories().get(0).getActive());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // #58 — StoreService.update() merge semantics for categories
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void update_absentCategories_preservesPersistedCategories() throws Exception {
        // given — persisted store has categories; incoming payload has empty list
        String profileId = "myID";
        StoreProfile persisted = makeStore(profileId);
        List<Category> existing = Collections.singletonList(new Category("c1", "Existing", "", true));
        persisted.setCategories(existing);

        StoreProfile incoming = makeStore(profileId); // categories defaults to emptyList()

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        StoreProfile result = storeService.update(profileId, incoming);

        // then — persisted categories must survive
        Assert.assertEquals(1, result.getCategories().size());
        Assert.assertEquals("Existing", result.getCategories().get(0).getName());
    }

    @Test
    public void update_presentCategories_withValidTags_overwritesCategories() throws Exception {
        // given — persisted store has stock with tags; incoming payload sends a matching category
        String profileId = "myID";
        StoreProfile persisted = makeStore(profileId);
        Stock stock = new Stock("item", 1, 10.0, 0, Collections.emptyList());
        stock.setTags(Collections.singletonList("ValidTag"));
        persisted.getStockList().add(stock);
        List<Category> oldCats = Collections.singletonList(new Category("c-old", "OldCat", "", true));
        persisted.setCategories(oldCats);

        StoreProfile incoming = makeStore(profileId);
        List<Category> newCats = Collections.singletonList(new Category("c-new", "ValidTag", "", true));
        incoming.setCategories(newCats);

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        StoreProfile result = storeService.update(profileId, incoming);

        // then — new categories replace old ones
        Assert.assertEquals(1, result.getCategories().size());
        Assert.assertEquals("ValidTag", result.getCategories().get(0).getName());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // #59 — Category name/tag validation
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void update_presentCategories_withInvalidTag_throws400() throws Exception {
        // given — persisted store has stock with a specific tag; incoming category uses a different name
        String profileId = "myID";
        StoreProfile persisted = makeStore(profileId);
        Stock stock = new Stock("item", 1, 10.0, 0, Collections.emptyList());
        stock.setTags(Collections.singletonList("KnownTag"));
        persisted.getStockList().add(stock);

        StoreProfile incoming = makeStore(profileId);
        List<Category> badCats = Collections.singletonList(new Category("c1", "UnknownTag", "", true));
        incoming.setCategories(badCats);

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));

        // when / then
        try {
            storeService.update(profileId, incoming);
            fail("Expected IllegalArgumentException for unknown category name");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("UnknownTag"));
            Assert.assertTrue(e.getMessage().contains("does not match any known StockItem tag"));
        }

        // must NOT save when validation fails
        verify(storeRepository, never()).save(any());
    }

    @Test
    public void update_presentCategories_stockHasNoTags_throws400() throws Exception {
        // given — stock items exist but none have tags set
        String profileId = "myID";
        StoreProfile persisted = makeStore(profileId);
        Stock stock = new Stock("item", 1, 10.0, 0, Collections.emptyList());
        // stock.tags is null by default
        persisted.getStockList().add(stock);

        StoreProfile incoming = makeStore(profileId);
        incoming.setCategories(Collections.singletonList(new Category("c1", "AnyName", "", true)));

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));

        try {
            storeService.update(profileId, incoming);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("AnyName"));
        }
        verify(storeRepository, never()).save(any());
    }

    @Test
    public void update_presentCategories_storeHasNoStock_savesWithoutValidation() throws Exception {
        // #59 — New stores have no stock items; tag-validation must be skipped entirely
        // so the 5 default seeded categories can be saved during onboarding.
        String profileId = "newStoreId";
        StoreProfile persisted = makeStore(profileId);
        // stockList is empty by default for a new store — no need to add any items
        Assert.assertTrue("Pre-condition: stockList must be empty", persisted.getStockList().isEmpty());

        StoreProfile incoming = makeStore(profileId);
        incoming.setCategories(Arrays.asList(
                new Category("c1", "Large Furniture", "", true),
                new Category("c2", "Small Furniture & Large Parcels", "", true),
                new Category("c3", "Small Parcels", "", true),
                new Category("c4", "Medicine", "", true),
                new Category("c5", "Parcels (General)", "", true)
        ));

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // when — must not throw
        StoreProfile result = storeService.update(profileId, incoming);

        // then — categories persisted, save called
        Assert.assertNotNull(result);
        verify(storeRepository, atLeastOnce()).save(any(StoreProfile.class));
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // #60 — Lazy-seed: empty categories triggers seed and persist
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void find_emptyCategories_seedsDefaultsAndPersists() {
        // given — store with no business hours (so save is called once for hours) and no categories
        String profileId = "seedMe";
        StoreProfile store = makeStore(profileId);
        // categories defaults to emptyList — no explicit set needed

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(store));
        when(storeRepository.save(store)).thenReturn(store);

        // when
        StoreProfile result = storeService.find(profileId);

        // then — categories seeded with 5 defaults
        Assert.assertNotNull(result.getCategories());
        Assert.assertEquals(5, result.getCategories().size());
        List<String> names = new ArrayList<>();
        for (Category c : result.getCategories()) {
            names.add(c.getName());
        }
        Assert.assertTrue(names.contains("Large Furniture"));
        Assert.assertTrue(names.contains("Small Furniture & Large Parcels"));
        Assert.assertTrue(names.contains("Small Parcels"));
        Assert.assertTrue(names.contains("Medicine"));
        Assert.assertTrue(names.contains("Parcels (General)"));
        // each seeded category is active and has a non-null UUID id
        for (Category c : result.getCategories()) {
            Assert.assertTrue(c.getActive());
            Assert.assertNotNull(c.getId());
        }
        // save must have been called (at minimum for categories; business hours save also happens since null)
        verify(storeRepository, atLeastOnce()).save(store);
    }

    @Test
    public void find_populatedCategories_doesNotReseed() {
        // given — store already has categories
        String profileId = "noSeed";
        StoreProfile store = makeStore(profileId);
        ArrayList<BusinessHours> hours = new ArrayList<>();
        hours.add(new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date()));
        store.setBusinessHours(hours);
        List<Category> preSeed = Collections.singletonList(new Category("existing", "Large Furniture", "", true));
        store.setCategories(preSeed);

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(store));

        // when
        StoreProfile result = storeService.find(profileId);

        // then — categories unchanged; save NOT called for categories (hours already set so no save at all)
        Assert.assertEquals(1, result.getCategories().size());
        Assert.assertEquals("Large Furniture", result.getCategories().get(0).getName());
        verify(storeRepository, never()).save(store);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // #65 — PATCH data-loss fix: null fields on incoming request must not wipe persisted data
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void update_partialPatch_categoriesOnly_doesNotWipeNameAddressLatitude() throws Exception {
        // given — persisted store has name, address, latitude set
        String profileId = "store-65";
        StoreProfile persisted = makeStore(profileId);
        persisted.setName("Original Name");
        persisted.setAddress("123 Main St");
        persisted.setLatitude(-26.2041);
        persisted.setLongitude(28.0473);
        List<Category> existingCats = Collections.singletonList(new Category("c-old", "OldCategory", "", true));
        persisted.setCategories(existingCats);

        // incoming payload: only categories is set; name/address/latitude are null / 0
        StoreProfile incoming = new StoreProfile(
                StoreType.FOOD,
                null,  // name is null — caller did not send it
                null,  // shortName null
                null,  // address null
                null,  // imageUrl null
                null,  // mobileNumber null
                null,  // tags null
                null,  // businessHours null
                null,  // ownerId null
                null   // bank null
        );
        incoming.setId(profileId);
        // Only categories provided — must match a stock tag (store has no stock, so validation skips)
        incoming.setCategories(Collections.singletonList(new Category("c-new", "NewCategory", "", true)));

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        StoreProfile result = storeService.update(profileId, incoming);

        // then — categories updated
        Assert.assertEquals(1, result.getCategories().size());
        Assert.assertEquals("NewCategory", result.getCategories().get(0).getName());

        // then — persisted fields NOT wiped by the null incoming fields
        Assert.assertEquals("Original Name must be preserved", "Original Name", result.getName());
        Assert.assertEquals("Address must be preserved", "123 Main St", result.getAddress());
        Assert.assertEquals("Latitude must be preserved", -26.2041, result.getLatitude(), 0.0001);
        Assert.assertEquals("Longitude must be preserved", 28.0473, result.getLongitude(), 0.0001);

        // save called exactly once on the persisted object (not on the raw incoming)
        verify(storeRepository, times(1)).save(persisted);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // #65 — profileApproved must never be reset by a partial PATCH
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void update_partialPatch_doesNotResetProfileApproved() throws Exception {
        // given — store is live and approved
        String profileId = "approved-store";
        StoreProfile persisted = makeStore(profileId);
        persisted.setProfileApproved(true);

        // incoming PATCH contains only a name update; profileApproved is not sent (defaults to false)
        StoreProfile incoming = new StoreProfile(
                StoreType.FOOD,
                "Updated Name",
                null, null, null, null, null, null, null, null
        );
        incoming.setId(profileId);
        // profileApproved defaults to false on a freshly constructed StoreProfile

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        StoreProfile result = storeService.update(profileId, incoming);

        // then — name updated; approval state preserved
        Assert.assertEquals("Updated Name", result.getName());
        Assert.assertTrue("profileApproved must remain true after a partial PATCH that omits the field",
                result.getProfileApproved());

        verify(storeRepository, times(1)).save(persisted);
    }

    @Test
    public void update_partialPatch_profileApprovedTrue_setsApproval() throws Exception {
        // given — store is not yet approved
        String profileId = "unapproved-store";
        StoreProfile persisted = makeStore(profileId);
        Assert.assertFalse("Pre-condition: store must not be approved", persisted.getProfileApproved());

        // incoming PATCH explicitly sets profileApproved = true (e.g. admin approval action)
        StoreProfile incoming = new StoreProfile(
                StoreType.FOOD,
                null, null, null, null, null, null, null, null, null
        );
        incoming.setId(profileId);
        incoming.setProfileApproved(true);

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        StoreProfile result = storeService.update(profileId, incoming);

        // then — approval granted
        Assert.assertTrue("profileApproved must be true when incoming sends true", result.getProfileApproved());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // #65 — hasPaymentAgreement must not be reset by a partial PATCH
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void update_partialPatch_doesNotResetHasPaymentAgreement() throws Exception {
        // given — store has a payment agreement in place
        String profileId = "payment-agreement-store";
        StoreProfile persisted = makeStore(profileId);
        persisted.setHasPaymentAgreement(true);

        // incoming PATCH omits hasPaymentAgreement (defaults to false)
        StoreProfile incoming = new StoreProfile(
                StoreType.FOOD, "Updated Name", null, null, null, null, null, null, null, null);
        incoming.setId(profileId);

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        StoreProfile result = storeService.update(profileId, incoming);

        // then — agreement preserved
        Assert.assertTrue("hasPaymentAgreement must remain true after a partial PATCH that omits the field",
                result.getHasPaymentAgreement());
        verify(storeRepository, times(1)).save(persisted);
    }

    @Test
    public void update_partialPatch_hasPaymentAgreementTrue_setsAgreement() throws Exception {
        // given — store does not yet have a payment agreement
        String profileId = "no-agreement-store";
        StoreProfile persisted = makeStore(profileId);
        Assert.assertFalse("Pre-condition: hasPaymentAgreement must be false", persisted.getHasPaymentAgreement());

        // incoming PATCH explicitly sets hasPaymentAgreement = true
        StoreProfile incoming = new StoreProfile(
                StoreType.FOOD, null, null, null, null, null, null, null, null, null);
        incoming.setId(profileId);
        incoming.setHasPaymentAgreement(true);

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        StoreProfile result = storeService.update(profileId, incoming);

        // then — agreement granted
        Assert.assertTrue("hasPaymentAgreement must be true when incoming sends true",
                result.getHasPaymentAgreement());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // #65 — deliversFromMultipleAddresses must not be reset by a partial PATCH
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void update_partialPatch_doesNotResetDeliversFromMultipleAddresses() throws Exception {
        // given — store delivers from multiple addresses
        String profileId = "multi-address-store";
        StoreProfile persisted = makeStore(profileId);
        persisted.setDeliversFromMultipleAddresses(true);

        // incoming PATCH omits deliversFromMultipleAddresses (defaults to false)
        StoreProfile incoming = new StoreProfile(
                StoreType.FOOD, "Updated Name", null, null, null, null, null, null, null, null);
        incoming.setId(profileId);

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        StoreProfile result = storeService.update(profileId, incoming);

        // then — flag preserved
        Assert.assertTrue("deliversFromMultipleAddresses must remain true after a partial PATCH that omits the field",
                result.getDeliversFromMultipleAddresses());
        verify(storeRepository, times(1)).save(persisted);
    }

    @Test
    public void update_partialPatch_deliversFromMultipleAddressesTrue_setsFlag() throws Exception {
        // given — store does not yet deliver from multiple addresses
        String profileId = "single-address-store";
        StoreProfile persisted = makeStore(profileId);
        Assert.assertFalse("Pre-condition: deliversFromMultipleAddresses must be false",
                persisted.getDeliversFromMultipleAddresses());

        // incoming PATCH explicitly sets the flag to true
        StoreProfile incoming = new StoreProfile(
                StoreType.FOOD, null, null, null, null, null, null, null, null, null);
        incoming.setId(profileId);
        incoming.setDeliversFromMultipleAddresses(true);

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        StoreProfile result = storeService.update(profileId, incoming);

        // then — flag set
        Assert.assertTrue("deliversFromMultipleAddresses must be true when incoming sends true",
                result.getDeliversFromMultipleAddresses());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // #65 — generateMissingImages must not be reset by a partial PATCH
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void update_partialPatch_doesNotResetGenerateMissingImages() throws Exception {
        // given — store has image generation enabled
        String profileId = "image-gen-store";
        StoreProfile persisted = makeStore(profileId);
        persisted.setGenerateMissingImages(true);

        // incoming PATCH omits generateMissingImages (defaults to false)
        StoreProfile incoming = new StoreProfile(
                StoreType.FOOD, "Updated Name", null, null, null, null, null, null, null, null);
        incoming.setId(profileId);

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        StoreProfile result = storeService.update(profileId, incoming);

        // then — flag preserved
        Assert.assertTrue("generateMissingImages must remain true after a partial PATCH that omits the field",
                result.getGenerateMissingImages());
        verify(storeRepository, times(1)).save(persisted);
    }

    @Test
    public void update_partialPatch_generateMissingImagesTrue_setsFlag() throws Exception {
        // given — store does not yet have image generation enabled
        String profileId = "no-image-gen-store";
        StoreProfile persisted = makeStore(profileId);
        Assert.assertFalse("Pre-condition: generateMissingImages must be false",
                persisted.getGenerateMissingImages());

        // incoming PATCH explicitly sets the flag to true
        StoreProfile incoming = new StoreProfile(
                StoreType.FOOD, null, null, null, null, null, null, null, null, null);
        incoming.setId(profileId);
        incoming.setGenerateMissingImages(true);

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        StoreProfile result = storeService.update(profileId, incoming);

        // then — flag set
        Assert.assertTrue("generateMissingImages must be true when incoming sends true",
                result.getGenerateMissingImages());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // #65 — quoteRequired must not be reset by a partial PATCH
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void update_partialPatch_doesNotResetQuoteRequired() throws Exception {
        // given — store requires a quote before checkout
        String profileId = "quote-required-store";
        StoreProfile persisted = makeStore(profileId);
        persisted.setQuoteRequired(true);

        // incoming PATCH omits quoteRequired (defaults to false)
        StoreProfile incoming = new StoreProfile(
                StoreType.FOOD, "Updated Name", null, null, null, null, null, null, null, null);
        incoming.setId(profileId);

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        StoreProfile result = storeService.update(profileId, incoming);

        // then — flag preserved
        Assert.assertTrue("quoteRequired must remain true after a partial PATCH that omits the field",
                result.isQuoteRequired());
        verify(storeRepository, times(1)).save(persisted);
    }

    @Test
    public void update_partialPatch_quoteRequiredTrue_setsFlag() throws Exception {
        // given — store does not yet require a quote
        String profileId = "no-quote-store";
        StoreProfile persisted = makeStore(profileId);
        Assert.assertFalse("Pre-condition: quoteRequired must be false", persisted.isQuoteRequired());

        // incoming PATCH explicitly sets quoteRequired = true
        StoreProfile incoming = new StoreProfile(
                StoreType.FOOD, null, null, null, null, null, null, null, null, null);
        incoming.setId(profileId);
        incoming.setQuoteRequired(true);

        when(storeRepository.findById(profileId)).thenReturn(Optional.of(persisted));
        when(storeRepository.save(any(StoreProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        StoreProfile result = storeService.update(profileId, incoming);

        // then — flag set
        Assert.assertTrue("quoteRequired must be true when incoming sends true", result.isQuoteRequired());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    private StoreProfile makeStore(String id) {
        ArrayList<BusinessHours> businessHours = new ArrayList<>();
        StoreProfile store = new StoreProfile(
                StoreType.FOOD, "name", "shortname", "address",
                "https://image.url", "081mobilenumb",
                Collections.singletonList("tag"),
                null, // null triggers business-hours save path in find()
                "ownerId", new Bank());
        store.setId(id);
        Bank bank = new Bank();
        bank.setAccountId("bankAccId");
        store.setBank(bank);
        return store;
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
        HashSet<Stock> stockList = new HashSet<>();
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