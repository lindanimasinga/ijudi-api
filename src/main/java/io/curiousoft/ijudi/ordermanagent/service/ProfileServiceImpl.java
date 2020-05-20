package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.Profile;
import io.curiousoft.ijudi.ordermanagent.repo.ProfileRepo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;
import java.util.UUID;

@Service
public class ProfileServiceImpl implements UserService {

    private final ProfileRepo profileRepo;
    private final Validator validator;

    public ProfileServiceImpl(ProfileRepo profileRepo) {
        this.profileRepo = profileRepo;
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Override
    public Profile create(Profile profile) throws Exception {
        validate(profile);
        profile.setId(UUID.randomUUID().toString());
        return profileRepo.save(profile);
    }

    @Override
    public Profile update(String profileId, Profile profile) throws Exception {

        Profile persistedProfile = profileRepo.findById(profileId)
                .orElseThrow(() -> new Exception("Profile not found"));
        BeanUtils.copyProperties(profile, persistedProfile);

        return profileRepo.save(persistedProfile);
    }

    @Override
    public void delete(String id) {
        profileRepo.deleteById(id);
    }

    @Override
    public Profile find(String profileId) {
        return profileRepo.findById(profileId).orElse(null);
    }

    private void validate(Profile profile) throws Exception {
        Set<ConstraintViolation<Profile>> violations = validator.validate(profile);
        if(violations.size() > 0) {
            throw new Exception(violations.iterator().next().getMessage());
        }
    }
}
