package io.curiousoft.ijudi.ordermanagement.repo;

import io.curiousoft.ijudi.ordermanagement.model.StoreProfile;

import java.util.List;

public interface StoreRepository extends ProfileRepo<StoreProfile> {

    List<StoreProfile> findByFeatured(boolean b);

    List<StoreProfile> findByOwnerId(String ownerId);


    List<StoreProfile> findByLatitudeBetweenAndLongitudeBetween(double v, double v1, double v2, double v3);
}
