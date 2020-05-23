package io.curiousoft.ijudi.ordermanagent.conroller;

import io.curiousoft.ijudi.ordermanagent.model.Order;
import io.curiousoft.ijudi.ordermanagent.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
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
    public ResponseEntity<List<Order>> getAllOrderForUser(@NotBlank @RequestParam String userId) {
        List<Order> order = orderService.findOrderByUserId(userId);
        return order != null ? ResponseEntity.ok(order) : ResponseEntity.notFound().build();
    }
}
