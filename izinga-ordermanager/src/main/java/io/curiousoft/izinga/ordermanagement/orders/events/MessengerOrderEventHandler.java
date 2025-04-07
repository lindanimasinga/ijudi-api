package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.order.events.OrderCancelledEvent;
import io.curiousoft.izinga.commons.order.events.OrderUpdatedEvent;
import io.curiousoft.izinga.commons.payout.events.PayoutBalanceUpdatedEvent;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.PushNotificationService;
import io.curiousoft.izinga.ordermanagement.promocodes.PromoCodeClient;
import io.curiousoft.izinga.ordermanagement.service.AdminOnlyNotificationService;
import io.curiousoft.izinga.ordermanagement.service.DeviceService;
import io.curiousoft.izinga.commons.order.events.NewOrderEvent;
import io.curiousoft.izinga.recon.ReconService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public record MessengerOrderEventHandler(PushNotificationService pushNotificationService,
                                         AdminOnlyNotificationService smsNotificationService,
                                         EmailNotificationService emailNotificationService,
                                         DeviceService deviceService,
                                         UserProfileService userProfileService,
                                         ApplicationEventPublisher eventPublisher,
                                         ReconService reconService,
                                         PromoCodeClient promoCodeClient,
                                         OrderRepository orderRepository) implements OrderEventHandler {

    @EventListener
    @Override
    public void handleNewOrderEvent(NewOrderEvent event) throws Exception {
        var order = event.getOrder();
        var store = event.getReceivingStore();
        var messenger = userProfileService.find(event.getMessenger());

        if(messenger == null) {
            return;
        }

        boolean isDelivery = order.getShippingData() != null
                && order.getShippingData().getType() == ShippingData.ShippingType.DELIVERY
                && store.getStoreType() != StoreType.TIPS && store.getStoreType() != StoreType.CAR_WASH;

        // notify messenger
        if (isDelivery) {
            List<Device> messengerDevices = deviceService.findByUserId(order.getShippingData().getMessengerId());
            pushNotificationService.notifyMessengerOrderPlaced(messengerDevices, order, store);

            if (StringUtils.hasText(messenger.getEmailAddress())) {
                emailNotificationService.notifyShops(order, List.of(messenger.getEmailAddress()));
            }
        }

        if (store.getStoreType() == StoreType.TIPS) {
            var promoCode = qualifiesForPromotion(order);
            //if user qualified for incentive, create a new order with incentive amount.
            if (promoCode.isPresent()) {
                createAndFinishPromoOrder(order, promoCode.get());
                promoCodeClient.redeemed(promoCode.get());
            }

            //get payout balance send event to update payout
            Optional.ofNullable(reconService.generatePayoutForMessengerAndOrder(order))
                    .ifPresent( payout -> {
                        var payoutTotal = payout.getTotal().setScale(2, RoundingMode.HALF_UP);

                        var mobileNumber = userProfileService.find(order.getShippingData().getMessengerId()).getMobileNumber();
                        var tip = BigDecimal.valueOf(order.getTip()).setScale(2, RoundingMode.HALF_UP);
                        var tipReceivedMessage = promoCode.map(userPromoDetails -> String.format("Congratulations! You have received a tip of R%.2f and rewarded with an extra R%.2f, Your balance is R%.2f. Thank you for your service.%niZinga.", tip, userPromoDetails.amount(), payoutTotal))
                                .orElseGet(() -> String.format("You have received a tip of R%s, Your balance is R%s. Thank you for your service.%niZinga.", tip, payoutTotal));
                        smsNotificationService.sendMessage(mobileNumber, tipReceivedMessage);

                        var balanceEventAndroid  = new PayoutBalanceUpdatedEvent(order.getShippingData().getMessengerId(),
                                payoutTotal,
                                DeviceType.ANDROID,
                                this);
                        eventPublisher.publishEvent(balanceEventAndroid);
                        var balanceEventIOS  = new PayoutBalanceUpdatedEvent(order.getShippingData().getMessengerId(),
                                payoutTotal,
                                DeviceType.APPLE,
                                this);
                        eventPublisher.publishEvent(balanceEventIOS);
                    });
        }
    }

    private void createAndFinishPromoOrder(Order order, PromoCodeClient.UserPromoDetails promoCode) {
        var incentiveOrder = new Order();
        BeanUtils.copyProperties(order, incentiveOrder);
        Basket basket = new Basket();
        basket.setItems(List.of(new BasketItem(promoCode.promo(), 1, promoCode.amount(), 0)));
        incentiveOrder.setId(null);
        incentiveOrder.setStage(OrderStage.STAGE_7_ALL_PAID);
        incentiveOrder.setBasket(basket);
        incentiveOrder.setOrderType(OrderType.INSTORE);
        incentiveOrder.setServiceFee(0.00);
        incentiveOrder.getShippingData().setFee(0.00);
        incentiveOrder.setTip(promoCode.amount());
        orderRepository.save(incentiveOrder);
    }

    private Optional<PromoCodeClient.UserPromoDetails> qualifiesForPromotion(Order order) {
        try {
            var promos = promoCodeClient.getPromoCodes("CASH");
            if (promos.isEmpty()) return Optional.empty();
            return Optional.ofNullable(promoCodeClient.findForUser(order.getId(), order.getShippingData().getMessengerId(), promos.get(0).code()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @EventListener
    @Override
    public void handleOrderUpdatedEvent(OrderUpdatedEvent event) {
        var order = event.getOrder();
        var store = event.getReceivingStore();

        if(event.getMessenger() == null) {
            return;
        }

        if (order.getStage() == OrderStage.STAGE_7_ALL_PAID) {
            reconService.generatePayoutForMessengerAndOrder(order);
        }
    }

    @Override
    public void handleOrderCancelledEvent(OrderCancelledEvent newOrderEvent) {

    }
}
