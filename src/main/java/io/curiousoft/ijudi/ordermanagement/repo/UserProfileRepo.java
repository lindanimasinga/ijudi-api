package io.curiousoft.ijudi.ordermanagement.repo;

import io.curiousoft.ijudi.ordermanagement.model.UserProfile;

import java.util.Optional;

public interface UserProfileRepo extends ProfileRepo<UserProfile> {

    Optional<UserProfile> findByMobileNumber(String phone);
}
