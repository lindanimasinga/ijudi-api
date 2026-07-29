package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.model.Device;
import io.curiousoft.izinga.commons.model.OrderStage;
import io.curiousoft.izinga.commons.model.StoreType;
import io.curiousoft.izinga.commons.order.events.OrderCancelledEvent;
import io.curiousoft.izinga.commons.order.events.OrderUpdatedEvent;
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommission;
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo;
import io.curiousoft.izinga.commons.referral.FurnitureCustomerReferralCommission;
import io.curiousoft.izinga.commons.referral.FurnitureCustomerReferralCommissionRepo;
import io.curiousoft.izinga.commons.referral.ReferralCommissionType;
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo;
import io.curiousoft.izinga.commons.referral.StorePartnerStage2Commission;
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.messaging.AdminOnlyNotificationService;
import io.curiousoft.izinga.ordermanagement.service.DeviceService;
import io.curiousoft.izinga.commons.order.events.NewOrderEvent;
import io.curiousoft.izinga.recon.ReconService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreOrderEventHandler implements OrderEventHandler {

    private final FirebaseNotificationService pushNotificationService;
    private final AdminOnlyNotificationService adminOnlyNotificationService;
    private final EmailNotificationService emailNotificationService;
    private final DeviceService deviceService;
    private final UserProfileService userProfileService;
    private final ReconService reconService;
    private final UserProfileRepo userProfileRepo;
    private final FoodCustomerReferralCommissionRepo foodCustomerCommissionRepo;
    private final FurnitureCustomerReferralCommissionRepo furnitureCustomerCommissionRepo;
    private final StorePartnerStage1CommissionRepo storeStage1CommissionRepo;
    private final StorePartnerStage2CommissionRepo storeStage2CommissionRepo;

    /**
     * RP-012: The service fee percentage applied to the base delivery fee to arrive at the
     * Total Delivery Charge. Must match the same property injected in OrderServiceImpl.
     */
    @Value("${service.fee.perc}")
    private double serviceFeePerc;

    @Async
    @Override
    @EventListener
    public void handleNewOrderEvent(NewOrderEvent event) throws Exception {
        var store = event.getReceivingStore();
        var order = event.getOrder();
        List<Device> shopDevices = deviceService.findByUserId(store.getOwnerId());
        if (!shopDevices.isEmpty()) {
            pushNotificationService.notifyStoreOrderPlaced(store.getName(), shopDevices, order);
        }
    }

    @Async
    @EventListener
    @Override
    public void handleNewOrderEventToEmail(NewOrderEvent event) throws Exception {
        var store = event.getReceivingStore();
        var order = event.getOrder();
        if (StringUtils.hasText(store.getEmailAddress())) {
            emailNotificationService.notifyShops(order, List.of(store.getEmailAddress()));
        }
    }

    @Async
    @EventListener
    public void handleNewOrderEventToWhatsapp(NewOrderEvent event) throws Exception {
        var store = event.getReceivingStore();
        var order = event.getOrder();
        adminOnlyNotificationService.notifyShopOrderPlaced(order, store);
    }

    @Async
    @EventListener
    @Override
    public void handleOrderUpdatedEvent(OrderUpdatedEvent event) {
        var order = event.getOrder();
        var store = event.getReceivingStore();
        log.info("Received order udpated event {} for order {}", event.getOrder().getStage(), event.getOrder().getId());
        if (order.getStage() == OrderStage.STAGE_7_ALL_PAID) {
            reconService.generatePayoutForShopAndOrder(order);
            // RP-006: food customer referral commission (first order only, R15 flat)
            if (store.getStoreType() == StoreType.FOOD) {
                triggerFoodCustomerCommissionIfEligible(order.getId(), order.getCustomerId());
                // RP-008: store partner stage 2 commission (store's first completed order, R150 flat)
                triggerStoreStage2CommissionIfEligible(order.getId(), store.getId(), store.getReferredByPartnerId());
            }
            // RP-012: furniture customer referral commission (first order only, 5% of Total Delivery Charge)
            if (store.getStoreType() == StoreType.MOVERS) {
                triggerFurnitureCustomerCommissionIfEligible(order, order.getCustomerId());
            }
        }
    }

    /**
     * RP-006: Creates a R15 FoodCustomerReferralCommission the first time a referred food customer
     * completes an order. Idempotency is enforced by the unique index on customerId — a
     * DuplicateKeyException is caught and logged rather than propagated.
     */
    private void triggerFoodCustomerCommissionIfEligible(String orderId, String customerId) {
        if (!StringUtils.hasText(customerId)) return;
        var customerOpt = userProfileRepo.findById(customerId);
        if (customerOpt.isEmpty()) {
            log.warn("[rp-006] customer {} not found, skipping commission", customerId);
            return;
        }
        var customer = customerOpt.get();
        var partnerId = customer.getReferredByPartnerId();
        if (!StringUtils.hasText(partnerId)) return; // no referral attribution

        try {
            var commission = new FoodCustomerReferralCommission(
                    java.util.UUID.randomUUID().toString(),
                    customerId,
                    partnerId,
                    orderId,
                    new java.math.BigDecimal("15.00"),
                    io.curiousoft.izinga.commons.referral.ReferralCommissionStatus.PENDING,
                    new java.util.Date()
            );
            foodCustomerCommissionRepo.insert(commission);
            log.info("[rp-006] food customer commission created: customerId={} partnerId={} orderId={}",
                    customerId, partnerId, orderId);
            // RP-009: wire the commission into a payout immediately
            reconService.generatePayoutForReferralPartner(
                    partnerId,
                    commission.getAmount(),
                    ReferralCommissionType.FOOD_CUSTOMER_REFERRAL,
                    customerId
            );
        } catch (DuplicateKeyException e) {
            log.info("[rp-006] commission already exists for customerId={}, skipping (idempotent)", customerId);
        } catch (Exception e) {
            log.error("[rp-006] failed to create commission for customerId={}: {}", customerId, e.getMessage());
        }
    }

    /**
     * RP-008: Creates a R150 StorePartnerStage2Commission on the store's first completed order,
     * provided Stage 1 commission already exists. Idempotency via unique index on storeId.
     */
    private void triggerStoreStage2CommissionIfEligible(String orderId, String storeId, String partnerId) {
        if (!StringUtils.hasText(partnerId)) return; // store not referred

        var stage1 = storeStage1CommissionRepo.findByStoreId(storeId);
        if (stage1 == null) {
            log.info("[rp-008] no stage1 commission found for storeId={}, skipping stage2", storeId);
            return;
        }

        try {
            var commission = new StorePartnerStage2Commission(
                    java.util.UUID.randomUUID().toString(),
                    storeId,
                    partnerId,
                    orderId,
                    new java.math.BigDecimal("150.00"),
                    io.curiousoft.izinga.commons.referral.ReferralCommissionStatus.PENDING,
                    new java.util.Date()
            );
            storeStage2CommissionRepo.insert(commission);
            log.info("[rp-008] store stage2 commission created: storeId={} partnerId={} orderId={}",
                    storeId, partnerId, orderId);
            // RP-009: wire the commission into a payout immediately
            reconService.generatePayoutForReferralPartner(
                    partnerId,
                    commission.getAmount(),
                    ReferralCommissionType.STORE_PARTNER_STAGE_2,
                    storeId
            );
        } catch (DuplicateKeyException e) {
            log.info("[rp-008] stage2 commission already exists for storeId={}, skipping (idempotent)", storeId);
        } catch (Exception e) {
            log.error("[rp-008] failed to create stage2 commission for storeId={}: {}", storeId, e.getMessage());
        }
    }

    /**
     * RP-012: Creates a FurnitureCustomerReferralCommission the first time a referred furniture
     * customer completes a MOVERS order (STAGE_7_ALL_PAID).
     *
     * Commission = Total Delivery Charge × 0.05, where:
     *   Total Delivery Charge = shippingData.fee × (1 + serviceFeePerc)
     *
     * ROUNDING NOTE: RoundingMode.HALF_UP is used here intentionally, not the codebase's usual
     * HALF_EVEN. This is a deliberate, scoped exception required by Schedule 1 Clause 2 of the
     * signed Referral Partner Agreement. The agreement's worked example (R500 base → R532.50 total
     * → R26.625 → R26.63) mandates HALF_UP. HALF_EVEN would produce R26.62, underpaying by one
     * cent versus the contract.
     *
     * Idempotency is enforced by the unique index on customerId — a DuplicateKeyException is caught
     * and logged rather than propagated.
     */
    private void triggerFurnitureCustomerCommissionIfEligible(
            io.curiousoft.izinga.commons.model.Order order, String customerId) {

        if (!StringUtils.hasText(customerId)) return;

        if (order.getShippingData() == null) {
            log.warn("[rp-012] order {} has null shippingData, skipping furniture commission", order.getId());
            return;
        }

        var customerOpt = userProfileRepo.findById(customerId);
        if (customerOpt.isEmpty()) {
            log.warn("[rp-012] customer {} not found, skipping furniture commission", customerId);
            return;
        }
        var customer = customerOpt.get();
        var partnerId = customer.getReferredByPartnerId();
        if (!StringUtils.hasText(partnerId)) return; // no referral attribution

        // Compute commission: (baseFee × (1 + serviceFeePerc)) × 0.05
        // HALF_UP required by Schedule 1 Clause 2 of the Referral Partner Agreement — see method Javadoc.
        var baseFee = BigDecimal.valueOf(order.getShippingData().getFee());
        var totalDeliveryCharge = baseFee.multiply(BigDecimal.valueOf(1.0 + serviceFeePerc));
        var commissionAmount = totalDeliveryCharge
                .multiply(new BigDecimal("0.05"))
                .setScale(2, RoundingMode.HALF_UP);

        try {
            var commission = new FurnitureCustomerReferralCommission(
                    java.util.UUID.randomUUID().toString(),
                    customerId,
                    partnerId,
                    order.getId(),
                    commissionAmount,
                    io.curiousoft.izinga.commons.referral.ReferralCommissionStatus.PENDING,
                    new java.util.Date()
            );
            furnitureCustomerCommissionRepo.insert(commission);
            log.info("[rp-012] furniture customer commission created: customerId={} partnerId={} orderId={} amount={}",
                    customerId, partnerId, order.getId(), commissionAmount);
            // RP-012: wire the commission into a payout immediately
            reconService.generatePayoutForReferralPartner(
                    partnerId,
                    commission.getAmount(),
                    ReferralCommissionType.FURNITURE_CUSTOMER_REFERRAL,
                    customerId
            );
        } catch (DuplicateKeyException e) {
            log.info("[rp-012] commission already exists for customerId={}, skipping (idempotent)", customerId);
        } catch (Exception e) {
            log.error("[rp-012] failed to create furniture commission for customerId={}: {}", customerId, e.getMessage());
        }
    }

    @Override
    public void handleOrderCancelledEvent(OrderCancelledEvent newOrderEvent) {

    }
}
