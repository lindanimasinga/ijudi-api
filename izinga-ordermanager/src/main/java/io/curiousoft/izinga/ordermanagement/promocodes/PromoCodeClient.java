package io.curiousoft.izinga.ordermanagement.promocodes;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@FeignClient(value = "promo-code-service", url = "${promocodes.verifier.url}")
public interface PromoCodeClient {

    @GetMapping(value = "/promocodes/forUser", consumes = "application/json")
    UserPromoDetails findForUser(@RequestParam String orderId, @RequestParam String userId, @RequestParam String promoCode);

    @PostMapping(value = "/promocodes/redeem", consumes = "application/json", produces = "application/json")
    Redeemed redeemed(@RequestBody UserPromoDetails userPromoDetails);

    @GetMapping(value = "/promocodes", consumes = "application/json", produces = "application/json")
    List<PromoCode> getPromoCodes(@RequestParam String type) throws Exception;

    record UserPromoDetails(String userId, String promo, Boolean verified, Double amount, LocalDateTime expiry, String orderId){
    }

    record Redeemed(String code, LocalDateTime date, String userId){
    }

    record PromoCode(String code, LocalDateTime expiry, String userId, String type, Double amount){
    }

}
