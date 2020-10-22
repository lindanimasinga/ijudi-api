package io.curiousoft.ijudi.ordermanagement.conroller;

import io.curiousoft.ijudi.ordermanagement.model.Promotion;
import io.curiousoft.ijudi.ordermanagement.service.PromotionService;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/promotion")
public class PromotionsController {

    private PromotionService promotionService;

    public PromotionsController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public ResponseEntity<Promotion> create(@Valid @RequestBody Promotion promotion) throws Exception {
        return ResponseEntity.ok(promotionService.create(promotion));
    }

    @PatchMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Promotion> update(@PathVariable String id, @Valid @RequestBody Promotion promotion) throws Exception {
        return ResponseEntity.ok(promotionService.update(id, promotion));
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<Promotion> findPromotion(@PathVariable String id) {
        Promotion promotion = promotionService.find(id);
        return promotion != null ? ResponseEntity.ok(promotion) : ResponseEntity.notFound().build();
    }

    @DeleteMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity deletePromotion(@PathVariable String id) {
        promotionService.delete(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<List<Promotion>> findAllPromotions(@RequestParam(required = false) String storeId) {
        List<Promotion> promotions = promotionService.findAll(storeId);
        return promotions != null ? ResponseEntity.ok(promotions) : ResponseEntity.notFound().build();
    }
}
