package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.Promotion;
import io.curiousoft.izinga.commons.model.StoreType;
import io.curiousoft.izinga.commons.repo.PromotionRepo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PromotionService {

    private final Validator validator;
    protected final PromotionRepo promotionRepo;

    public PromotionService(PromotionRepo promotionRepo) {
        this.promotionRepo = promotionRepo;
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    public Promotion create(Promotion promotion) throws Exception {
        validate(promotion);
        promotion.setId(UUID.randomUUID().toString());
        return promotionRepo.save(promotion);
    }

    public Promotion update(String profileId, Promotion profile) throws Exception {

        Promotion persistedProfile = promotionRepo.findById(profileId)
                .orElseThrow(() -> new Exception("Profile not found"));
        BeanUtils.copyProperties(profile, persistedProfile);

        return promotionRepo.save(persistedProfile);
    }

    public void delete(String id) {
        promotionRepo.deleteById(id);
    }

    public Promotion find(String profileId) {
        return promotionRepo.findById(profileId).orElse(null);
    }

    public List<Promotion> findAll(String storeId, StoreType storeType) {
        return StringUtils.isEmpty(storeId) ?
                promotionRepo.findByExpiryDateAfterAndShopType(new Date(), storeType) :
                promotionRepo.findByShopIdAndExpiryDateAfterAndShopType(storeId, new Date(), storeType);
    }

    protected void validate(Object profile) throws Exception {
        Set<ConstraintViolation<Object>> violations = validator.validate(profile);
        if(violations.size() > 0) {
            throw new Exception(violations.iterator().next().getMessage());
        }
    }
}
