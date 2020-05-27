package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.UserProfile;
import io.curiousoft.ijudi.ordermanagent.repo.UserProfileRepo;
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
