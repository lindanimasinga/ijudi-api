package io.curiousoft.ijudi.ordermanagement.repo;

import io.curiousoft.ijudi.ordermanagement.model.Promotion;
import io.curiousoft.ijudi.ordermanagement.model.StoreType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface PromotionRepo extends MongoRepository<Promotion, String> {

    List<Promotion> findByExpiryDateBefore(Date date);

    List<Promotion> findByExpiryDateAfterAndShopType(Date date, StoreType storeType);

    List<Promotion> findByShopIdAndExpiryDateAfterAndShopType(String storeId, Date date, StoreType storeType);
}
