package io.curiousoft.ijudi.ordermanagement.repo;

import io.curiousoft.ijudi.ordermanagement.model.ProfileRoles;
import io.curiousoft.ijudi.ordermanagement.model.UserProfile;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepo extends ProfileRepo<UserProfile> {

    Optional<UserProfile> findByMobileNumber(String phone);

    List<UserProfile> findByRoleAndLatitudeBetweenAndLongitudeBetween(ProfileRoles messenger, double latMin, double latMax, double longMin, double longMax);
}
