package io.curiousoft.ijudi.ordermanagent.repo;

import io.curiousoft.ijudi.ordermanagent.model.Promotion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface PromotionRepo extends MongoRepository<Promotion, String> {

    List<Promotion> findByExpiryDateBefore(Date date);
}
