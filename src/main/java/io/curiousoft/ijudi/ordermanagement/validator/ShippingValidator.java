package io.curiousoft.ijudi.ordermanagement.validator;

import io.curiousoft.ijudi.ordermanagement.model.BuildingType;
import io.curiousoft.ijudi.ordermanagement.model.ShippingData;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class ShippingValidator implements ConstraintValidator<ValidDeliveryInfo, ShippingData> {

        @Override
        public boolean isValid(ShippingData value, ConstraintValidatorContext context) {
            if(value == null) return false;
            boolean isValidBuildingType = value.getType() == ShippingData.ShippingType.COLLECTION
                    || value.getBuildingType() == BuildingType.HOUSE ||
                    (!StringUtils.isEmpty(value.getUnitNumber()) && !StringUtils.isEmpty(value.getBuildingName()));
            boolean isValidPickup = value.getType() == ShippingData.ShippingType.COLLECTION && value.getPickUpTime() != null;
            boolean hasMessengerForDelivery = value.getMessenger() != null;
            return isValidBuildingType && ( isValidPickup || hasMessengerForDelivery);
        }
}
