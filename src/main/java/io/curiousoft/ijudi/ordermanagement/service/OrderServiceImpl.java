package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.*;
import io.curiousoft.ijudi.ordermanagement.repo.OrderRepository;
import io.curiousoft.ijudi.ordermanagement.repo.StoreRepository;
import io.curiousoft.ijudi.ordermanagement.repo.UserProfileRepo;
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
    private final PaymentService paymentService;

    @Autowired
    public OrderServiceImpl(OrderRepository orderRepository,
                            StoreRepository storeRepository,
                            UserProfileRepo userProfileRepo, PaymentService paymentService) {
        this.orderRepo = orderRepository;
        this.storeRepository = storeRepository;
        this.userProfileRepo = userProfileRepo;
        this.paymentService = paymentService;
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

    }

    @Override
    public Order startOrder(Order order) throws Exception {

        validate(order);
        if (!userProfileRepo.existsById(order.getCustomerId())) {
            throw new Exception("user with id " + order.getCustomerId() + " does not exist");
        }

        Optional<StoreProfile> storeOptional = storeRepository.findById(order.getShopId());
        if (!storeOptional.isPresent()) {
            throw new Exception("shop with id " + order.getShopId() + " does not exist");
        }
        order.setHasVat(storeOptional.get().getHasVat());
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
                .orElseThrow(() -> new Exception("Order with id " + order.getId() + " not found."));

        persistedOrder.setDescription(order.getDescription());
        persistedOrder.setPaymentType(order.getPaymentType());
        persistedOrder.setDate(new Date());

        if (!paymentService.paymentReceived(persistedOrder)) {
            throw new Exception("Payment not received....");
        }

        if(persistedOrder.getOrderType() == OrderType.INSTORE) {
            paymentService.completePaymentToShop(persistedOrder);
            persistedOrder.setStage(5);
            persistedOrder.setShopPaid(true);
        } else {
            persistedOrder.setStage(1);
        }

        //decrease stock available
        Optional<StoreProfile> optional = storeRepository.findById(order.getShopId());
        if (optional.isPresent()) {
            StoreProfile store = optional.get();
            Set<Stock> stock = store.getStockList();
            order.getBasket()
                    .getItems()
                    .stream()
                    .forEach(item -> {
                        stock.stream()
                                .filter(sto -> sto.getName().equals(item.getName()))
                                .forEach(stockItem -> stockItem.setQuantity(stockItem.getQuantity() - item.getQuantity()));
                    });
            storeRepository.save(store);
        }
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
        if (violations.size() > 0) {
            throw new Exception(violations.iterator().next().getMessage());
        }
    }
}
