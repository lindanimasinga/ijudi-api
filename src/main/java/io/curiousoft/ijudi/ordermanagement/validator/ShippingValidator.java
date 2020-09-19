package io.curiousoft.ijudi.ordermanagement.validator;

import io.curiousoft.ijudi.ordermanagement.model.BuildingType;
import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.ShippingData;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Date;

public class ShippingValidator implements ConstraintValidator<ValidDeliveryInfo, Order> {

        @Override
        public boolean isValid(Order order, ConstraintValidatorContext context) {
            ShippingData value = order.getShippingData();
            if(value == null) return true;
            boolean isValidBuildingType = value.getType() == ShippingData.ShippingType.COLLECTION
                    || value.getBuildingType() == BuildingType.HOUSE ||
                    (!StringUtils.isEmpty(value.getUnitNumber()) && !StringUtils.isEmpty(value.getBuildingName()));
            boolean isValidPickup = value.getType() == ShippingData.ShippingType.COLLECTION
                    && value.getPickUpTime() != null && value.getPickUpTime().after(new Date());
            boolean hasMessengerForDelivery = value.getMessengerId() != null;
            return isValidBuildingType && ( isValidPickup || hasMessengerForDelivery);
        }
}
