package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagement.repo.UserProfileRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StoreService extends ProfileServiceImpl<StoreRepository, StoreProfile> {

    private UserProfileRepo userProfileRepo;
    private String mainPayAccount;

    public StoreService(StoreRepository storeRepository,
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
        profile.setBank(user.getBank());
        StoreProfile newStore = super.create(profile);
        user.setRole(ProfileRoles.STORE_ADMIN);
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

    public List<StoreProfile> findFeatured() {
        List<StoreProfile> profiles = profileRepo.findByFeatured(true);
        return profiles.stream()
                .filter(profile -> profile.getFeaturedExpiry() != null)
                .filter(profile -> profile.getFeaturedExpiry().after(new Date()))
                .map(profile -> {
                    profile.getBank().setAccountId(mainPayAccount);
                    return profile;
                })
                .collect(Collectors.toList());
    }

    public Set<Stock> findStockForShop(String profileId) throws Exception {
        StoreProfile store = profileRepo.findById(profileId).orElseThrow(() -> new Exception("Profile not found"));
        return store.getStockList();
    }

    public void addStockForShop(String profileId, Stock stock) throws Exception {
        validate(stock);
        StoreProfile store = profileRepo.findById(profileId).orElseThrow(() -> new Exception("Profile not found"));
        store.getStockList().add(stock);
        profileRepo.save(store);
    }
}
