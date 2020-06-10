package io.curiousoft.ijudi.ordermanagement.repo;

import io.curiousoft.ijudi.ordermanagement.model.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProfileRepo<U extends Profile> extends MongoRepository<U, String> {
}
