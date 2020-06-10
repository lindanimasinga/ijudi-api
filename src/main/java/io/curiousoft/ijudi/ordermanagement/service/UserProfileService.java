package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.UserProfile;
import io.curiousoft.ijudi.ordermanagement.repo.UserProfileRepo;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService extends ProfileServiceImpl<UserProfileRepo, UserProfile>{

    public UserProfileService(UserProfileRepo userProfileRepo) {
        super(userProfileRepo);
    }

    public UserProfile findOrderByPhone(String phone) {
        return profileRepo.findByMobileNumber(phone).orElse(null);
    }
}
