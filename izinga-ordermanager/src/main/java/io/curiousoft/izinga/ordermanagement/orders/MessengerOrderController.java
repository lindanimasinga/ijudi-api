package io.curiousoft.izinga.ordermanagement.orders;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.QouteApproval;
import io.curiousoft.izinga.commons.order.MessengerOrderDto;
import io.curiousoft.izinga.commons.order.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

import static org.springframework.util.StringUtils.isEmpty;

/**
 * Messenger-facing order API.
 * All order reads apply iZinga commission deduction via MessengerOrderDto,
 * so fees visible to the messenger reflect their actual payout.
 *
 * Endpoints:
 *   GET    /messenger/order                        - list orders (messengerId or messengerAdminId)
 *   GET    /messenger/order/{id}                   - single order with deducted fees
 *   GET    /messenger/order/{orderId}/nextstage    - advance order stage
 *   PATCH  /messenger/order/{orderId}/quote        - accept/reject a quote
 */
@RestController
@RequestMapping({"/messenger/order", "//messenger/order"})
public class MessengerOrderController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessengerOrderController.class);

    private final OrderService orderService;
    private final double izingaCommissionPerc;

    public MessengerOrderController(OrderService orderService,
                                    @Value("${service.commission.perc}") double izingaCommissionPerc) {
        this.orderService = orderService;
        this.izingaCommissionPerc = izingaCommissionPerc;
    }

    /**
     * Get all orders for a messenger or messenger admin.
     * Returns commission-deducted MessengerOrderDto for every order.
     *
     * @param messengerId      filter by assigned messenger
     * @param messengerAdminId filter by messenger admin team
     * @param allStages        include completed/cancelled orders (default false)
     */
    @GetMapping(produces = "application/json")
    public ResponseEntity<Collection<MessengerOrderDto>> getOrders(
            @RequestParam(required = false) String messengerId,
            @RequestParam(required = false) String messengerAdminId,
            @RequestParam(defaultValue = "false") boolean allStages) {

        if (!isEmpty(messengerId)) {
            // findOrderByMessengerId already creates MessengerOrderDto instances internally
            var orders = orderService.findOrderByMessengerId(messengerId, allStages);
            if (orders == null) return ResponseEntity.notFound().build();
            List<MessengerOrderDto> result = orders.stream()
                    .map(o -> (MessengerOrderDto) o)
                    .toList();
            return ResponseEntity.ok(result);
        }

        if (!isEmpty(messengerAdminId)) {
            // findOrdersByMessengerAdminId returns raw Order — wrap each in MessengerOrderDto
            List<Order> raw = orderService.findOrdersByMessengerAdminId(messengerAdminId, allStages);
            if (raw == null) return ResponseEntity.notFound().build();
            List<MessengerOrderDto> wrapped = raw.stream()
                    .map(o -> new MessengerOrderDto(o, izingaCommissionPerc))
                    .toList();
            return ResponseEntity.ok(wrapped);
        }

        return ResponseEntity.badRequest().build();
    }

    /**
     * Get a single order with commission deducted from all delivery fees.
     */
    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<MessengerOrderDto> getOrder(@PathVariable String id) {
        // findOrderForMessenger already wraps in MessengerOrderDto
        Order order = orderService.findOrderForMessenger(id);
        return order != null ? ResponseEntity.ok((MessengerOrderDto) order) : ResponseEntity.notFound().build();
    }

    /**
     * Advance an order to the next stage.
     * Optionally records the messenger's current GPS coordinates.
     */
    @GetMapping(value = "/{orderId}/nextstage", produces = "application/json")
    public ResponseEntity<MessengerOrderDto> progressNextStage(
            @PathVariable String orderId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) throws Exception {
        Order updated = (latitude != null || longitude != null)
                ? orderService.progressNextStage(orderId, latitude, longitude)
                : orderService.progressNextStage(orderId);
        return ResponseEntity.ok(new MessengerOrderDto(updated, izingaCommissionPerc));
    }

    /**
     * Accept or reject a delivery quote.
     */
    @PatchMapping(value = "/{orderId}/quote", consumes = "application/json", produces = "application/json")
    public ResponseEntity<MessengerOrderDto> acceptQuote(
            @PathVariable String orderId,
            @RequestBody QouteApproval quoteApproval) throws Exception {
        Order result = orderService.acceptQuote(orderId, quoteApproval);
        return ResponseEntity.ok(new MessengerOrderDto(result, izingaCommissionPerc));
    }
}
