package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StoreService extends ProfileServiceImpl<StoreRepository, StoreProfile> {

    private final UserProfileRepo userProfileRepo;
    private final String mainPayAccount;
    private final double markupPercentage;

    public  StoreService(StoreRepository storeRepository,
                        UserProfileRepo userProfileRepo,
                        @Value("${ukheshe.main.account}") String mainPayAccount,
                        @Value("${service.markup.perc}") double markupPercentage) {
        super(storeRepository);
        this.userProfileRepo = userProfileRepo;
        this.mainPayAccount = mainPayAccount;
        this.markupPercentage = markupPercentage;
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
    public StoreProfile update(String profileId, StoreProfile profile) throws Exception {
        // #58 — PATCH / merge semantics for categories.
        // Load the persisted store first so we can decide whether to preserve or replace categories.
        // super.update() will call findById again internally (BeanUtils.copyProperties path), which is
        // acceptable — the extra load is cheap and keeps the merge logic self-contained here.
        StoreProfile persisted = profileRepo.findById(profileId)
                .orElseThrow(() -> new Exception("Profile not found"));

        List<Category> incomingCategories = profile.getCategories();

        if (incomingCategories != null && !incomingCategories.isEmpty()) {
            // #59 — Name/tag validation: the caller is explicitly providing categories.
            // Each Category.name must match a StockItem tag exactly (case-sensitive).
            // Stock.tags is a List<String> per stock item; we collect the full tag universe for this store.
            // NOTE: If no stock items have tags yet, all category names will fail validation.
            // In that scenario the store admin must add tagged stock items first, or use the seeded defaults
            // (which are seeded before any category update is accepted).
            validateCategoryNames(persisted, incomingCategories);
            // Validation passed — allow the incoming categories to overwrite.
        } else {
            // Caller sent empty/absent categories — preserve persisted categories (PATCH semantics).
            profile.setCategories(persisted.getCategories());
        }

        // #65 — PATCH data-loss fix: merge only non-null fields from the incoming request onto
        // the already-loaded persisted entity. Calling super.update() would do a blind
        // BeanUtils.copyProperties(profile, persisted) which overwrites every field — including
        // fields the caller never sent — with null.  We own the save here instead.
        mergeNonNullFields(profile, persisted);
        persisted.setMarkUp(markupPercentage);
        return profileRepo.save(persisted);
    }

    /**
     * Copies non-null StoreProfile and Profile fields from {@code src} onto {@code dest}.
     * Fields that are null in {@code src} are left unchanged on {@code dest}, preserving
     * PATCH semantics.  Only the fields declared on StoreProfile and its Profile superclass
     * that are meaningful to update are enumerated here — id, role, and internal state
     * fields (stockList etc.) are intentionally excluded or handled elsewhere.
     */
    private void mergeNonNullFields(StoreProfile src, StoreProfile dest) {
        // Profile base fields
        if (src.getName() != null)          dest.setName(src.getName());
        if (src.getAddress() != null)       dest.setAddress(src.getAddress());
        if (src.getImageUrl() != null)      dest.setImageUrl(src.getImageUrl());
        if (src.getMobileNumber() != null)  dest.setMobileNumber(src.getMobileNumber());
        if (src.getSurname() != null)       dest.setSurname(src.getSurname());
        if (src.getDescription() != null)   dest.setDescription(src.getDescription());
        if (src.getEmailAddress() != null)  dest.setEmailAddress(src.getEmailAddress());
        if (src.getBank() != null)          dest.setBank(src.getBank());
        if (src.getLatitude() != 0.0)       dest.setLatitude(src.getLatitude());
        if (src.getLongitude() != 0.0)      dest.setLongitude(src.getLongitude());
        // StoreProfile-specific fields
        if (src.getStoreType() != null)     dest.setStoreType(src.getStoreType());
        if (src.getShortName() != null)     dest.setShortName(src.getShortName());
        if (src.getTags() != null)          dest.setTags(src.getTags());
        if (src.getBusinessHours() != null) dest.setBusinessHours(src.getBusinessHours());
        if (src.getOwnerId() != null)       dest.setOwnerId(src.getOwnerId());
        if (src.getRegNumber() != null)     dest.setRegNumber(src.getRegNumber());
        if (src.getStoreWebsiteUrl() != null) dest.setStoreWebsiteUrl(src.getStoreWebsiteUrl());
        if (src.getFranchiseName() != null) dest.setFranchiseName(src.getFranchiseName());
        if (src.getFeaturedExpiry() != null) dest.setFeaturedExpiry(src.getFeaturedExpiry());
        // Primitive/boolean fields — always copy (no null sentinel available)
        dest.setHasVat(src.getHasVat());
        dest.setFeatured(src.getFeatured());
        dest.setIzingaTakesCommission(src.getIzingaTakesCommission());
        dest.setScheduledDeliveryAllowed(src.getScheduledDeliveryAllowed());
        dest.setAvailability(src.getAvailability());
        dest.setFreeDeliveryMinAmount(src.getFreeDeliveryMinAmount());
        dest.setMarkUpPrice(src.getMarkUpPrice());
        dest.setMinimumDepositAllowedPerc(src.getMinimumDepositAllowedPerc());
        dest.setStandardDeliveryPrice(src.getStandardDeliveryPrice());
        dest.setStandardDeliveryKm(src.getStandardDeliveryKm());
        dest.setRatePerKm(src.getRatePerKm());
        // categories: already resolved above (persisted or incoming) — always copy the resolved value
        dest.setCategories(src.getCategories());
    }

    /**
     * Validates that each category name matches a known StockItem tag (case-sensitive) in this store.
     *
     * @throws IllegalArgumentException if any category name is not present in the store's StockItem tags
     */
    private void validateCategoryNames(StoreProfile store, List<Category> categories) {
        // #59 — New stores have no stock yet; tag-matching is meaningless without stock items.
        // Skip validation entirely so the default seeded categories can be saved.
        if (store.getStockList() == null || store.getStockList().isEmpty()) {
            return;
        }
        Set<String> knownTags = store.getStockList().stream()
                .filter(stock -> stock.getTags() != null)
                .flatMap(stock -> stock.getTags().stream())
                .collect(Collectors.toSet());

        for (Category category : categories) {
            if (!knownTags.contains(category.getName())) {
                throw new IllegalArgumentException(
                        "Category name '" + category.getName() + "' does not match any known StockItem tag. " +
                        "Category names must match StockItem tags exactly (case-sensitive).");
            }
        }
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

    public StoreProfile findOneByIdOrShortName(String id, String shortname) throws Exception {
        return profileRepo.findOneByIdOrShortName(id, shortname).orElseThrow(() -> new Exception("Shop Profile not found"));
    }
}
