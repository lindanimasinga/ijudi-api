package io.curiousoft.izinga.ordermanagement.service.messenger;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MessengerLookUpServiceTest {

    @Mock
    private UserProfileRepo userProfileRepo;

    private MessengerLookUpService sut;

    @BeforeEach
    void setUp() {
        sut = new MessengerLookUpService(userProfileRepo);
    }

    @Nested
    class FindNearbyMessengersSuccess {

        private UserProfile messenger;
        private Order order;
        private final double lat = -25.7;
        private final double lon = 28.1;
        private final double radius = 100.0;

        @BeforeEach
        void setUp() {
            // set up messenger
            messenger = new UserProfile(
                    "John Doe",
                    UserProfile.SignUpReason.DELIVERY_DRIVER,
                    "123 Test Street",
                    "https://image.url",
                    "0812345678",
                    ProfileRoles.MESSENGER
            );
            messenger.setTermsAccepted(true);
            messenger.setProfileApproved(true);
            messenger.setAvailabilityStatus(ProfileAvailabilityStatus.ONLINE);
            messenger.setDescription("motorbike");
            messenger.getTag().put("loadCapacity", "50.0");
            messenger.setLatitude(lat);
            messenger.setLongitude(lon);

            // set up order
            order = new Order();
            Basket basket = new Basket();
            List<BasketItem> items = new ArrayList<>();
            items.add(new BasketItem("item1", 1, 10.0, 0));
            basket.setItems(items);
            order.setBasket(basket);
            order.setShopId("shopId");
            order.setCustomerId("customerId");
            order.setDescription("test order");
            order.setOrderType(OrderType.ONLINE);
            order.setStage(OrderStage.STAGE_0_CUSTOMER_NOT_PAID);

            ShippingData shippingData = new ShippingData(
                    "fromAddress",
                    "toAddress",
                    ShippingData.ShippingType.DELIVERY
            );
            shippingData.setCategory(List.of("motorbike"));
            order.setShippingData(shippingData);
        }

        @Test
        void testFindNearbyMessengers_Success() {
            // given
            when(userProfileRepo.findByRoleAndLatitudeBetweenAndLongitudeBetween(
                    eq(ProfileRoles.MESSENGER), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(List.of(messenger));

            // when
            List<UserProfile> result = sut.findNearbyMessengers(lat, lon, radius, order);

            // then
            assertEquals(1, result.size());
        }

        @Test
        void testFindNearbyMessengers_AwayStatus() {
            // given
            messenger.setAvailabilityStatus(ProfileAvailabilityStatus.AWAY);
            when(userProfileRepo.findByRoleAndLatitudeBetweenAndLongitudeBetween(
                    eq(ProfileRoles.MESSENGER), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(List.of(messenger));

            // when
            List<UserProfile> result = sut.findNearbyMessengers(lat, lon, radius, order);

            // then
            assertEquals(1, result.size());
        }
    }
}
