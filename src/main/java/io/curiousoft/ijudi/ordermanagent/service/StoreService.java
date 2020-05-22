package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagent.repo.StoreRepository;
import org.springframework.stereotype.Service;

@Service
public class StoreService extends ProfileServiceImpl<StoreProfile>{

    public StoreService(StoreRepository storeRepository) {
        super(storeRepository);
    }
}
