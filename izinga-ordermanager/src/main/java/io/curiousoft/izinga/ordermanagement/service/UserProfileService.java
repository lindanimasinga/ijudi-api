package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserProfileService extends ProfileServiceImpl<UserProfileRepo, UserProfile>{

    public UserProfileService(UserProfileRepo userProfileRepo) {
        super(userProfileRepo);
    }

    public UserProfile findUserByPhone(String phone) {
        return profileRepo.findByMobileNumber(phone);
    }

    public List<UserProfile> findByLocation(ProfileRoles role, double latitude, double longitude, double range) {
        double maxLong = longitude + range,
                minLong = longitude - range;
        double maxLat = latitude + range,
                minLat = latitude - range;

        return profileRepo.findByRoleAndLatitudeBetweenAndLongitudeBetween(role, minLat,
                maxLat, minLong, maxLong);
    }

    @Override
    public UserProfile create(UserProfile profile) throws Exception {
        if(profileRepo.existsByMobileNumber(profile.getMobileNumber()))
            throw new Exception("User with phone number " + profile.getMobileNumber() + " already exist.");
        return super.create(profile);
    }
}
