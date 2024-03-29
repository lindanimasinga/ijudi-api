package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.Profile;
import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.repo.ProfileRepo;
import org.springframework.beans.BeanUtils;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public abstract class ProfileServiceImpl<E extends ProfileRepo<U>, U extends Profile> implements ProfileService<U> {

    private final Validator validator;
    protected final E profileRepo;

    public ProfileServiceImpl(E userProfileRepo) {
        this.profileRepo = userProfileRepo;
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Override
    public U create(U profile) throws Exception {
        validate(profile);
        profile.setId(UUID.randomUUID().toString());
        return profileRepo.save(profile);
    }

    @Override
    public U update(String profileId, U profile) throws Exception {

        U persistedProfile = profileRepo.findById(profileId)
                .orElseThrow(() -> new Exception("Profile not found"));
        BeanUtils.copyProperties(profile, persistedProfile);

        return profileRepo.save(persistedProfile);
    }

    @Override
    public void delete(String id) {
        profileRepo.deleteById(id);
    }

    @Override
    public U find(String profileId) {
        return profileRepo.findById(profileId).orElse(null);
    }

    @Override
    public List<U> findAll() {
        return profileRepo.findAll();
    }

    protected void validate(Object profile) throws Exception {
        Set<ConstraintViolation<Object>> violations = validator.validate(profile);
        if(violations.size() > 0) {
            throw new Exception(violations.iterator().next().getMessage());
        }
    }

    public List<U> findByRole(ProfileRoles role) {
        return profileRepo.findByRole(role);
    }
}
