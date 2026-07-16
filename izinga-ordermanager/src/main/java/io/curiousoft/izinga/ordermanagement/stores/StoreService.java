package io.curiousoft.izinga.ordermanagement.stores;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.service.ProfileServiceImpl;
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StoreService extends ProfileServiceImpl<StoreRepository, StoreProfile> {

    private static final Logger LOG = LoggerFactory.getLogger(StoreService.class);

    private final UserProfileRepo userProfileRepo;
    private final String mainPayAccount;
    private final double markupPercentage;
    private final ReferralCodeService referralCodeService;

    public  StoreService(StoreRepository storeRepository,
                        UserProfileRepo userProfileRepo,
                        @Value("${ukheshe.main.account}") String mainPayAccount,
                        @Value("${service.markup.perc}") double markupPercentage,
                        ApplicationEventPublisher applicationEventPublisher,
                        ReferralCodeService referralCodeService) {
        super(storeRepository, applicationEventPublisher);
        this.userProfileRepo = userProfileRepo;
        this.mainPayAccount = mainPayAccount;
        this.markupPercentage = markupPercentage;
        this.referralCodeService = referralCodeService;
    }

    /**
     * RP-005a: Store creation with optional referral attribution.
     * If [referralCode] is non-null and resolves to a REFERRAL_PARTNER, sets
     * [StoreProfile.referredByPartnerId] before persisting.
     *
     * Called from StoreControler when a `referralCode` query param is present.
     */
    public StoreProfile create(StoreProfile profile, String referralCode) throws Exception {
        if (StringUtils.hasText(referralCode)) {
            var partner = referralCodeService.resolveCode(referralCode);
            if (partner != null) {
                LOG.info("Referral code {} resolved to partnerId={} for new store ownerId={}",
                        referralCode, partner.getId(), profile.getOwnerId());
                profile.setReferredByPartnerId(partner.getId());
            } else {
                LOG.warn("Referral code {} could not be resolved — no attribution set for store ownerId={}",
                        referralCode, profile.getOwnerId());
            }
        }
        return create(profile);
    }

    @Override
    public StoreProfile create(StoreProfile profile) throws Exception {
        UserProfile user = userProfileRepo.findById(profile.getOwnerId())
                .orElseThrow(() -> new Exception("Store owner with user id does not exist."));
        Optional<StoreProfile> exists = profileRepo.findOneByIdOrShortName(profile.getId(), profile.getShortName());
        if(exists.isPresent()) {
            throw new Exception("Shop shortname or id already exists. Please try a different shortname");
        }
        profile.setBank(user.getBank());
        StoreProfile newStore = super.create(profile);
        user.setRole(ProfileRoles.STORE_ADMIN);
        newStore.setMarkUp(markupPercentage);
        userProfileRepo.save(user);
        return newStore;
    }

    @Override
    public List<StoreProfile> findAll() {
        return super.findAll()
                .stream()
                .peek(profile -> {
                    profile.getBank().setAccountId(mainPayAccount);
                    profile.setMarkUp(markupPercentage);
                })
                .collect(Collectors.toList());
    }

    @Tool(name = "find_store_or_shops_by_id", description = "Find a store profile by its ID. If the store has no business hours set, default hours will be added.")
    @Override
    public StoreProfile find(String id) {
        StoreProfile store = profileRepo.findById(id).orElse(null);
        if (store != null && store.getBusinessHours() == null) {
            ArrayList<BusinessHours> hours = new ArrayList<>();
            Calendar instance1 = Calendar.getInstance();
            instance1.set(2020, 1, 1, 8, 0);
            Calendar instance2 = Calendar.getInstance();
            instance1.set(2020, 1, 1, 17, 0);
            hours.add(new BusinessHours(DayOfWeek.MONDAY, instance1.getTime(), instance2.getTime()));
            store.setBusinessHours(hours);
            profileRepo.save(store);
        }
        if (store != null) {
            // #60 — Lazy-seed default categories when the store has none.
            // This is idempotent: if categories are already populated the branch is skipped entirely.
            // Images are left as empty strings; the onboarding team uploads real images via the S3 endpoint.
            // TODO #62: Confirm S3 bucket ACL allows public-read on category images before uploading (DevOps/Lindani item).
            if (store.getCategories() == null || store.getCategories().isEmpty()) {
                List<Category> defaults = buildDefaultCategories();
                store.setCategories(defaults);
                profileRepo.save(store);
            }
            store.setMarkUp(markupPercentage);
        }
        return store;
    }

    /** Builds the 5 default delivery categories seeded for every new store on first GET. */
    static List<Category> buildDefaultCategories() {
        return Arrays.asList(
                new Category(UUID.randomUUID().toString(), "Large Furniture", "", true),
                new Category(UUID.randomUUID().toString(), "Small Furniture & Large Parcels", "", true),
                new Category(UUID.randomUUID().toString(), "Small Parcels", "", true),
                new Category(UUID.randomUUID().toString(), "Medicine", "", true),
                new Category(UUID.randomUUID().toString(), "Parcels (General)", "", true)
        );
    }


    @Tool(name = "find_stores_by_owner", description = "Find all store profiles owned by a specific user ID.")
    public List<StoreProfile> findByOwner(String ownerId) {
        return profileRepo.findByOwnerId(ownerId);
    }

    public List<StoreProfile> findFeatured(double latitude,
                                           double longitude,
                                           StoreType storeType,
                                           double range,
                                           int maxStores) {
        return findNearbyStores(latitude, longitude, storeType, range, maxStores)
                .stream()
                .filter(storeProfile ->  storeProfile.getFeaturedExpiry() != null)
                .filter(profile -> profile.getFeaturedExpiry().after(new Date()))
                .filter(StoreProfile::getFeatured)
                .collect(Collectors.toList());
    }

    public Set<Stock> findStockForShop(String profileId) throws Exception {
        StoreProfile store = profileRepo.findById(profileId).orElseThrow(() -> new Exception("Profile not found"));
        store.setMarkUp(markupPercentage);
        return store.getStockList();
    }

    public void addStockForShop(String profileId, Stock stock) throws Exception {
        validate(stock);
        StoreProfile store = profileRepo.findById(profileId).orElseThrow(() -> new Exception("Profile not found"));
        Optional<Stock> stockOptional = store.getStockList().stream()
                .filter(item -> stock.getName().equals(item.getName()))
                .findFirst();
        if(stockOptional.isPresent()) {
            Stock oldStock = stockOptional.get();
            String id = oldStock.getId();
            BeanUtils.copyProperties(stock, oldStock);
            oldStock.setId(id);
        } else {
            store.getStockList().add(stock);
        }
        store.setMarkUp(markupPercentage);
        profileRepo.save(store);
    }

    public List<StoreProfile> findNearbyStores(double latitude,
                                               double longitude,
                                               StoreType storeType,
                                               double range,
                                               int maxLocations) {
        double maxLong = longitude + range,
                minLong = longitude - range;
        double maxLat = latitude + range,
                minLat = latitude - range;

        List<StoreProfile> stores = profileRepo.findByLatitudeBetweenAndLongitudeBetweenAndStoreType(
                minLat,maxLat, minLong, maxLong, storeType).stream()
                .peek(profile -> {
                    profile.getBank().setAccountId(mainPayAccount);
                    profile.setMarkUp(markupPercentage);
                })
                .collect(Collectors.toList());

        GeoPoint origin = new GeoPointImpl(latitude, longitude);
        stores.sort((a, b) -> {
            double distanceToA = GeoDistance.Companion.getDistanceInKiloMetersBetweenTwoGeoPoints(origin, a);
            double distanceToB = GeoDistance.Companion.getDistanceInKiloMetersBetweenTwoGeoPoints(origin, b);
            var difference = distanceToA - distanceToB;
            return  difference < 0 ? -1 : difference > 0 ? 1 : 0;
        });

        maxLocations = maxLocations <= 0 ? 30 : maxLocations;
        return maxLocations > stores.size() ?  stores : stores.subList(0, maxLocations);
    }

    public List<StoreProfile> findStoresAdmin(double latitude,
                                               double longitude,
                                               double range,
                                               int maxLocations) {
        double maxLong = longitude + range,
                minLong = longitude - range;
        double maxLat = latitude + range,
                minLat = latitude - range;

        List<StoreProfile> stores = profileRepo.findByLatitudeBetweenAndLongitudeBetween(
                        minLat,maxLat, minLong, maxLong).stream()
                .peek(profile -> {
                    profile.getBank().setAccountId(mainPayAccount);
                    profile.setMarkUp(markupPercentage);
                })
                .collect(Collectors.toList());

        GeoPoint origin = new GeoPointImpl(latitude, longitude);
        stores.sort((a, b) -> {
            double distanceToA = GeoDistance.Companion.getDistanceInKiloMetersBetweenTwoGeoPoints(origin, a);
            double distanceToB = GeoDistance.Companion.getDistanceInKiloMetersBetweenTwoGeoPoints(origin, b);
            var difference = distanceToA - distanceToB;
            return  difference < 0 ? -1 : difference > 0 ? 1 : 0;
        });

        maxLocations = maxLocations <= 0 ? 30 : maxLocations;
        return maxLocations > stores.size() ?  stores : stores.subList(0, maxLocations);
    }

    public StoreProfile findOneByIdOrShortName(String id, String shortname) throws Exception {
        var store = profileRepo.findOneByIdOrShortName(id, shortname).orElseThrow(() -> new Exception("Shop Profile not found"));
        store.setMarkUp(markupPercentage);
        return store;
    }
}
