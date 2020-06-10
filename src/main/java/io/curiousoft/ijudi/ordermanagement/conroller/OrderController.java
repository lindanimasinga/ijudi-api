package io.curiousoft.ijudi.ordermanagement.conroller;

import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/order")
public class OrderController {


    private OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<Order> startOrder(@RequestBody @Valid Order order) throws Exception {
        return ResponseEntity.ok(orderService.startOrder(order));
    }

    @PatchMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Order> finishOrder(@RequestBody @Valid Order order) throws Exception {
        return ResponseEntity.ok(orderService.finishOder(order));
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        Order order = orderService.findOrder(id);
        return order != null ? ResponseEntity.ok(order) : ResponseEntity.notFound().build();
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<List<Order>> getAllOrderForUser(@RequestParam(required = false) String userId,
                                                          @RequestParam(required = false) String phone) throws Exception {
        List<Order> order = !StringUtils.isEmpty(userId) ?
                orderService.findOrderByUserId(userId) : !StringUtils.isEmpty(phone) ?
                                                            orderService.findOrderByPhone(phone) : null;
        return order != null ? ResponseEntity.ok(order) : ResponseEntity.notFound().build();
    }
}
