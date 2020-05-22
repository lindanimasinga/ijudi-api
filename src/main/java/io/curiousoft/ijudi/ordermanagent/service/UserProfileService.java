package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.UserProfile;
import io.curiousoft.ijudi.ordermanagent.repo.ProfileRepo;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService extends ProfileServiceImpl<UserProfile>{

    public UserProfileService(ProfileRepo<UserProfile> userProfileRepo) {
        super(userProfileRepo);
    }
}
