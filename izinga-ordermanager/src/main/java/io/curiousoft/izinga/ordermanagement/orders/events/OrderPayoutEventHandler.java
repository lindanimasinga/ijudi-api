package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.payout.events.OrderPayoutEvent;
import io.curiousoft.izinga.commons.payout.events.PayoutBalanceUpdatedEvent;
import io.curiousoft.izinga.recon.ReconService;
import io.curiousoft.izinga.commons.model.DeviceType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

@Service
public record OrderPayoutEventHandler(OrderRepository orderRepository,
                                      ApplicationEventPublisher eventPublisher,
                                      ReconService reconService) {

    @EventListener
    public void handleNewOrderEvent(OrderPayoutEvent event) {
        orderRepository.findById(event.getOrderId())
                .ifPresent(order -> {
                    order.setShopPaid(event.isStorePaid());
                    order.setMessengerPaid(event.isMessengerPaid());
                    orderRepository.save(order);

                    //get payout balance send event to update payout
                    reconService.getCurrentPayoutBundleForMessenger().getPayouts().stream()
                            .filter(payout -> payout.getToId().equals(order.getId()))
                            .findFirst()
                            .ifPresent(payout -> {
                                var payoutTotal = payout.getTotal().setScale(2, RoundingMode.HALF_UP);
                                var balanceEventAndroid = new PayoutBalanceUpdatedEvent(order.getShippingData().getMessengerId(),
                                        payoutTotal,
                                        DeviceType.ANDROID,
                                        this);
                                eventPublisher.publishEvent(balanceEventAndroid);
                                var balanceEventIOS = new PayoutBalanceUpdatedEvent(order.getShippingData().getMessengerId(),
                                        payoutTotal,
                                        DeviceType.APPLE,
                                        this);
                                eventPublisher.publishEvent(balanceEventIOS);
                            });
                });
    }

}
