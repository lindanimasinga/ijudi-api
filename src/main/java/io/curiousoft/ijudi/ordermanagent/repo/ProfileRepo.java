package io.curiousoft.ijudi.ordermanagent.repo;

import io.curiousoft.ijudi.ordermanagent.model.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProfileRepo<U extends Profile> extends MongoRepository<U, String> {
}
