package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.BusinessHours;
import io.curiousoft.ijudi.ordermanagent.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagent.repo.StoreRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StoreService extends ProfileServiceImpl<StoreRepository, StoreProfile> {

    public StoreService(StoreRepository storeRepository) {
        super(storeRepository);
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

    public List<StoreProfile> findFeatured() {
        List<StoreProfile> profiles = profileRepo.findByFeatured(true);
        return profiles.stream()
                .filter(profile -> profile.getFeaturedExpiry() != null)
                .filter(profile -> profile.getFeaturedExpiry().after(new Date())).collect(Collectors.toList());
    }
}
