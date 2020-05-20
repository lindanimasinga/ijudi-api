package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.Order;
import io.curiousoft.ijudi.ordermanagent.repo.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

@Service
public class OrderServiceImpl implements OrderService {

    public static final String DATE_FORMAT = "SSSmmHHyyddMMss";
    private final OrderRepository orderRepo;
    private final Validator validator;

    @Autowired
    public OrderServiceImpl(OrderRepository orderRepository) {
        this.orderRepo = orderRepository;
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

    }

    @Override
    public Order startOrder(Order order) throws Exception {

        validate(order);
        Date orderDate = new Date();
        String orderId = new SimpleDateFormat(DATE_FORMAT).format(orderDate);
        order.setId(orderId);
        order.setStage(0);
        order.setDate(orderDate);
        return orderRepo.save(order);
    }

    @Override
    public Order finishOder(Order order) throws Exception {
        validate(order);
        if(order.getStage() != 2) {
            throw new Exception("Invalid stage to finish the order stage must be 2");
        }
        order.setStage(3);
        return orderRepo.save(order);
    }

    @Override
    public Order findOrder(String orderId) {
        return orderRepo.findById(orderId).orElse(null);
    }

    private void validate(Order order) throws Exception {
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        if(violations.size() > 0) {
            throw new Exception(violations.iterator().next().getMessage());
        }
    }


}
