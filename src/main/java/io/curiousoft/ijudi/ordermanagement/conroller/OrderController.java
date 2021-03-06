package io.curiousoft.ijudi.ordermanagement.conroller;

import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

import static io.curiousoft.ijudi.ordermanagement.model.PaymentType.PAYFAST;

@RestController
@RequestMapping("/order")
public class OrderController {

    private List<String> allowedOrigins;
    private OrderService orderService;

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
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return !order.getId().equals(id)? ResponseEntity.badRequest().build() : ResponseEntity.ok(orderService.finishOder(order));
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

    @GetMapping(produces = "application/json")
    public ResponseEntity<List<Order>> getAllOrders(@RequestParam(required = false) String userId,
                                                    @RequestParam(required = false) String phone,
                                                    @RequestParam(required = false) String messengerId,
                                                    @RequestParam(required = false) String storeId) throws Exception {
        List<Order> order = !StringUtils.isEmpty(userId) ? orderService.findOrderByUserId(userId) :
                !StringUtils.isEmpty(phone) ? orderService.findOrderByPhone(phone) :
                !StringUtils.isEmpty(storeId) ? orderService.findOrderByStoreId(storeId) :
                !StringUtils.isEmpty(messengerId) ? orderService.findOrderByMessengerId(messengerId) :
                        orderService.findAll();
        return order != null ? ResponseEntity.ok(order) : ResponseEntity.notFound().build();
    }
}
