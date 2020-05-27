package io.curiousoft.ijudi.ordermanagent.repo;

import io.curiousoft.ijudi.ordermanagent.model.UserProfile;

import java.util.Optional;

public interface UserProfileRepo extends ProfileRepo<UserProfile> {

    Optional<UserProfile> findByMobileNumber(String phone);
}
