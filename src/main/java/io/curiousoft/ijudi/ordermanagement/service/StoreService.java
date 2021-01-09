package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagement.repo.UserProfileRepo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.curiousoft.ijudi.ordermanagement.model.GeoDistance.getDistanceInKiloMetersBetweenTwoGeoPoints;

@Service
public class StoreService extends ProfileServiceImpl<StoreRepository, StoreProfile> {

    private UserProfileRepo userProfileRepo;
    private String mainPayAccount;

    public  StoreService(StoreRepository storeRepository,
                        UserProfileRepo userProfileRepo,
                        @Value("${ukheshe.main.account}") String mainPayAccount) {
        super(storeRepository);
        this.userProfileRepo = userProfileRepo;
        this.mainPayAccount = mainPayAccount;
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
        userProfileRepo.save(user);
        return newStore;
    }

    @Override
    public List<StoreProfile> findAll() {
        return super.findAll()
                .stream()
                .peek(profile -> profile.getBank().setAccountId(mainPayAccount))
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
        return store;
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

        List<StoreProfile> stores = profileRepo.findByLatitudeBetweenAndLongitudeBetweenAndStoreType(minLat,
                maxLat, minLong, maxLong, storeType).stream()
                .peek(profile -> profile.getBank().setAccountId(mainPayAccount))
                .collect(Collectors.toList());

        GeoPoint origin = new GeoPointImpl(latitude, longitude);
        stores.sort((a, b) -> {
            double distanceToA = getDistanceInKiloMetersBetweenTwoGeoPoints(origin, a);
            double distanceToB = getDistanceInKiloMetersBetweenTwoGeoPoints(origin, b);
            return (int) (distanceToA - distanceToB);
        });

        maxLocations = maxLocations <= 0 ? 30 : maxLocations;
        return maxLocations > stores.size() ?  stores : stores.subList(0, maxLocations);
    }

    public StoreProfile findOneByIdOrShortName(String id, String shortname) throws Exception {
        return profileRepo.findOneByIdOrShortName(id, shortname).orElseThrow(() -> new Exception("Shop Profile not found"));
    }
}
