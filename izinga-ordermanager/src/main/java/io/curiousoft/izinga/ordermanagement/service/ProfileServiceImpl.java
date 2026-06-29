package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.Profile;
import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.profile.events.ProfileCreatedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileDeletedEvent;
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent;
import io.curiousoft.izinga.commons.repo.ProfileRepo;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public abstract class ProfileServiceImpl<E extends ProfileRepo<U>, U extends Profile> implements ProfileService<U> {

    private final Validator validator;
    protected final E profileRepo;
    protected final ApplicationEventPublisher applicationEventPublisher;

    public ProfileServiceImpl(E userProfileRepo, ApplicationEventPublisher applicationEventPublisher) {
        this.profileRepo = userProfileRepo;
        this.applicationEventPublisher = applicationEventPublisher;
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Override
    public U create(U profile) throws Exception {
        validate(profile);
        profile.setId(UUID.randomUUID().toString());
        U saved = profileRepo.save(profile);
        try {
            applicationEventPublisher.publishEvent(new ProfileCreatedEvent(this, saved));
        } catch (Exception e) { /* don't fail create on publish errors */ }
        return saved;
    }

    @Override
    public U update(String profileId, U profile) throws Exception {

        U persistedProfile = profileRepo.findById(profileId)
                .orElseThrow(() -> new Exception("Profile not found"));
        BeanUtils.copyProperties(profile, persistedProfile);

        U saved = profileRepo.save(persistedProfile);
        try {
            applicationEventPublisher.publishEvent(new ProfileUpdatedEvent(this, saved));
        } catch (Exception e) { /* swallow */ }
        return saved;
    }

    @Override
    public void delete(String id) {
        U p = profileRepo.findById(id).orElse(null);
        profileRepo.deleteById(id);
        if (p != null) {
            try {
                applicationEventPublisher.publishEvent(new ProfileDeletedEvent(this, p));
            } catch (Exception e) { /* ignore */ }
        }
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
