package io.curiousoft.izinga.ordermanagement.promocodes;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

@FeignClient(value = "promo-code-service", url = "${promocodes.verifier.url}")
public interface PromoCodeClient {

    @GetMapping(value = "/promocodes/forUser", consumes = "application/json")
    UserPromoDetails findForUser(@RequestParam String orderId, @RequestParam String userId, @RequestParam String promoCode);

    @PostMapping(value = "/promocodes/redeem", consumes = "application/json", produces = "application/json")
    Redeemed redeemed(@RequestBody UserPromoDetails userPromoDetails);

    record UserPromoDetails(String userId, String promo, Boolean verified, Double amount, LocalDateTime expiry, String orderId){
    }

    record Redeemed(String code, LocalDateTime date, String userId){
    }

}
