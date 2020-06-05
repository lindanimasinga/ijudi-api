package io.curiousoft.ijudi.ordermanagent.repo;

import io.curiousoft.ijudi.ordermanagent.model.StoreProfile;

import java.util.List;

public interface StoreRepository extends ProfileRepo<StoreProfile> {

    List<StoreProfile> findByFeatured(boolean b);
}
