package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.Order;
import io.curiousoft.ijudi.ordermanagent.model.UserProfile;
import io.curiousoft.ijudi.ordermanagent.repo.OrderRepository;
import io.curiousoft.ijudi.ordermanagent.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagent.repo.UserProfileRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class OrderServiceImpl implements OrderService {

    public static final String DATE_FORMAT = "SSSmmHHyyddMMss";
    private final OrderRepository orderRepo;
    private final StoreRepository storeRepository;
    private final UserProfileRepo userProfileRepo;
    private final Validator validator;
    private final PaymentVerifier paymentVerifier;

    @Autowired
    public OrderServiceImpl(OrderRepository orderRepository,
                            StoreRepository storeRepository,
                            UserProfileRepo userProfileRepo, PaymentVerifier paymentVerifier) {
        this.orderRepo = orderRepository;
        this.storeRepository = storeRepository;
        this.userProfileRepo = userProfileRepo;
        this.paymentVerifier = paymentVerifier;
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

    }

    @Override
    public Order startOrder(Order order) throws Exception {

        validate(order);
        if(!userProfileRepo.existsById(order.getCustomerId())) {
            throw new Exception("user with id "+ order.getCustomerId() + " does not exist");
        }

        if(!storeRepository.existsById(order.getShopId())) {
            throw new Exception("shop with id "+ order.getShopId() + " does not exist");
        }

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

        Order persistedOrder = orderRepo.findById(order.getId())
                .orElseThrow(() -> new Exception("Order with id " +order.getId()+ " not found."));

        persistedOrder.setDescription(order.getDescription());
        persistedOrder.setPaymentType(order.getPaymentType());

        if(!paymentVerifier.paymentReceived(persistedOrder)) {
            throw new Exception("Payment not received....");
        }

        persistedOrder.setStage(1);
        return orderRepo.save(persistedOrder);
    }

    @Override
    public Order findOrder(String orderId) {
        return orderRepo.findById(orderId).orElse(null);
    }

    @Override
    public List<Order> findOrderByUserId(String userId) {
        return orderRepo.findByCustomerId(userId).orElse(new ArrayList<>());
    }

    @Override
    public List<Order> findOrderByPhone(String phone) throws Exception {
        UserProfile user = userProfileRepo.findByMobileNumber(phone)
                .orElseThrow(() -> new Exception("User not found"));
        return orderRepo.findByCustomerId(user.getId()).orElse(new ArrayList<>());
    }

    private void validate(Order order) throws Exception {
        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        if(violations.size() > 0) {
            throw new Exception(violations.iterator().next().getMessage());
        }
    }


}
