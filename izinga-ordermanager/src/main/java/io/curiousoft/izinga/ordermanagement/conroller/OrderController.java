package io.curiousoft.izinga.ordermanagement.conroller;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.ordermanagement.orders.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

import static io.curiousoft.izinga.commons.model.PaymentType.PAYFAST;
import static org.springframework.util.StringUtils.isEmpty;

@RestController
@RequestMapping({"/order", "//order"})
public class OrderController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);

    private final List<String> allowedOrigins;
    private final OrderService orderService;

    public OrderController(OrderService orderService, @Value("${allowed.origins}") List<String> origins) {
        this.orderService = orderService;
        this.allowedOrigins = origins;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<Order> startOrder(@RequestBody @Valid Order order) throws Exception {
        return ResponseEntity.ok(orderService.startOrder(order));
    }

    @PatchMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Order> finishOrder(@PathVariable String id,
                                             @RequestBody @Valid Order order,
                                             @RequestHeader(value = "Origin", required = false) String origin) throws Exception {
        if(order.getPaymentType() == PAYFAST && (origin == null || !allowedOrigins.contains(origin.toLowerCase()))) {
            LOGGER.error("Unknown origin " + origin + ". Request not allowed");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return !order.getId().equals(id)? ResponseEntity.badRequest().build() : ResponseEntity.ok(orderService.finishOder(order));
    }

    @PatchMapping(value = "/{orderId}/promocode/{promocode}")
    public ResponseEntity<Order> applyPromoCode(@PathVariable String orderId, @PathVariable String promocode) throws Exception {
        var order = orderService.findOrder(orderId);
        order = orderService.applyPromoCode(promocode, order);
        return ResponseEntity.ok(order);
    }

    @GetMapping(value = "/{orderId}/nextstage", produces = "application/json")
    public ResponseEntity<Order> progressNextStage(@PathVariable String orderId) throws Exception {
        return ResponseEntity.ok(orderService.progressNextStage(orderId));
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        Order order = orderService.findOrder(id);
        return order != null ? ResponseEntity.ok(order) : ResponseEntity.notFound().build();
    }

    @DeleteMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<Order> cancelOrder(@PathVariable String id) throws Exception {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<List<Order>> getAllOrders(@RequestParam(required = false) String userId,
                                                    @RequestParam(required = false) String phone,
                                                    @RequestParam(required = false) String messengerId,
                                                    @RequestParam(required = false) String storeId) throws Exception {
        List<Order> order = !isEmpty(userId) ? orderService.findOrderByUserId(userId) :
                !isEmpty(phone) ? orderService.findOrderByPhone(phone) :
                !isEmpty(storeId) ? orderService.findOrderByStoreId(storeId) :
                !isEmpty(messengerId) ? orderService.findOrderByMessengerId(messengerId) :
                        orderService.findAll();
        return order != null ? ResponseEntity.ok(order) : ResponseEntity.notFound().build();
    }
}
