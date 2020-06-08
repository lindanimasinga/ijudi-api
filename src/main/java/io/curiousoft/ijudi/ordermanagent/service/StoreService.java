package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.BusinessHours;
import io.curiousoft.ijudi.ordermanagent.model.Stock;
import io.curiousoft.ijudi.ordermanagent.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagent.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagent.repo.UserProfileRepo;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StoreService extends ProfileServiceImpl<StoreRepository, StoreProfile> {

    private UserProfileRepo userProfileRepo;

    public StoreService(StoreRepository storeRepository, UserProfileRepo userProfileRepo) {
        super(storeRepository);
        this.userProfileRepo = userProfileRepo;
    }

    @Override
    public StoreProfile create(StoreProfile profile) throws Exception {
        if(Objects.nonNull(profile.getOwnerId()) && !userProfileRepo.existsById(profile.getOwnerId())) {
            throw new Exception("Store owner with user id does not exist.");
        }
        return super.create(profile);
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
                .filter(profile -> profile.getFeaturedExpiry().after(new Date())).collect(Collectors.toList());
    }

    public List<Stock> findStockForShop(String profileId) throws Exception {
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
