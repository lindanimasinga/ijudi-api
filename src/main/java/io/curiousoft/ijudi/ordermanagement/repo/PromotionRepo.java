package io.curiousoft.ijudi.ordermanagement.repo;

import io.curiousoft.ijudi.ordermanagement.model.Promotion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface PromotionRepo extends MongoRepository<Promotion, String> {

    List<Promotion> findByExpiryDateBefore(Date date);

    List<Promotion> findByExpiryDateAfter(Date date);

    List<Promotion> findByShopIdAndExpiryDateAfter(String storeId, Date date);
}
