package io.curiousoft.izinga.ordermanagement.shoppinglist;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shopping-list")
public class ShoppingListController {

    @Autowired
    private ShoppingListService shoppingListService;

    @PostMapping
    public ResponseEntity<ShoppingList> createShoppingList(@RequestBody ShoppingList shoppingList) {
        return ResponseEntity.ok(shoppingListService.createShoppingList(shoppingList));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShoppingList> updateShoppingList(@PathVariable Long id, @RequestBody ShoppingList updatedList) {
        return ResponseEntity.ok(shoppingListService.updateShoppingList(id, updatedList));
    }

    @PatchMapping("/{id}/add-item")
    public ResponseEntity<ShoppingList> addItemToShoppingList(@PathVariable Long id, @RequestBody ShoppingItem item) {
        return ResponseEntity.ok(shoppingListService.addItemToShoppingList(id, item));
    }

    @PatchMapping("/{id}/remove-item")
    public ResponseEntity<ShoppingList> removeItemFromShoppingList(@PathVariable Long id, @RequestParam String itemName) {
        return ResponseEntity.ok(shoppingListService.removeItemFromShoppingList(id, itemName));
    }

    @GetMapping
    public ResponseEntity<List<ShoppingList>> getAllShoppingLists(@RequestParam String userId) {
        return ResponseEntity.ok(shoppingListService.getAllShoppingLists(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShoppingList> getShoppingListById(@PathVariable Long id) {
        return ResponseEntity.ok(shoppingListService.getShoppingListById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShoppingList(@PathVariable Long id) {
        shoppingListService.deleteShoppingList(id);
        return ResponseEntity.noContent().build();
    }
}
