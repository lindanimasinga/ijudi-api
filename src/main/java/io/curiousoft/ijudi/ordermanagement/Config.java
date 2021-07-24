package io.curiousoft.ijudi.ordermanagement;

import io.curiousoft.ijudi.ordermanagement.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagement.utils.IjudiUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class Config {

    @Bean
    public Integer updateData(StoreRepository storeRepository, @Value("${service.markup.perc}") double markupPerc) {
        List<StoreProfile> stores = storeRepository.findAll();
        for (StoreProfile store: stores) {
            IjudiUtils.calculateMarkupPrice(store, markupPerc);
        }
        storeRepository.saveAll(stores);
        return 1;
    }
}
