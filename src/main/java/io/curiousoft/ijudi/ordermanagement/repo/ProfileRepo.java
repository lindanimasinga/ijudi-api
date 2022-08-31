package io.curiousoft.ijudi.ordermanagement.repo;

import io.curiousoft.ijudi.ordermanagement.model.Profile;
import io.curiousoft.ijudi.ordermanagement.model.ProfileRoles;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProfileRepo<U extends Profile> extends MongoRepository<U, String> {

    boolean existsByMobileNumber(String id);

    List<U> findByRole(ProfileRoles role);
}
