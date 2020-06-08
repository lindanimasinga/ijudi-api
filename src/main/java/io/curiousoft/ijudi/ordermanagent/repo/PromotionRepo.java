package io.curiousoft.ijudi.ordermanagent.repo;

import io.curiousoft.ijudi.ordermanagent.model.Promotion;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PromotionRepo extends MongoRepository<Promotion, String> {
}
