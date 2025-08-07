package io.curiousoft.izinga.ordermanagement.shoppinglist;

import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class ShoppingListService {

    private ShoppingListRepository shoppingListRepository;

    @Autowired
    public ShoppingListService(ShoppingListRepository shoppingListRepository) {
        this.shoppingListRepository = shoppingListRepository;
    }

    public ShoppingList createShoppingList(ShoppingList shoppingList) {
        return shoppingListRepository.save(shoppingList);
    }

    public ShoppingList updateShoppingList(String id, ShoppingList updatedList) {
        return shoppingListRepository.findById(id).map(item -> {
            item.setSchedule(updatedList.getSchedule());
            item.setStartDate(updatedList.getStartDate());
            item.setEndDate(updatedList.getEndDate());
            item.setUserIds(updatedList.getUserIds());
            return shoppingListRepository.save(item);
        }).orElseThrow(() -> new ResourceNotFoundException("Shopping List not found with id %d".formatted(id)));
    }

    public ShoppingList addItemToShoppingList(String id, ShoppingItem item) {
        return shoppingListRepository.findById(id).map(list -> {
            list.getItems().add(item);
            return shoppingListRepository.save(list);
        }).orElseThrow(() -> new ResourceNotFoundException("Shopping List not found with id " + id));
    }

    public ShoppingList removeItemFromShoppingList(String id, String itemName) {
        return shoppingListRepository.findById(id).map(list -> {
            list.getItems().removeIf(item -> item.getName().equalsIgnoreCase(itemName));
            return shoppingListRepository.save(list);
        }).orElseThrow(() -> new ResourceNotFoundException("Shopping List not found with id " + id));
    }

    public List<ShoppingList> getAllShoppingLists(String userId) {
        return shoppingListRepository.findByUserIds(userId);
    }

    public ShoppingList getShoppingListById(String id) {
        return shoppingListRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shopping List not found with id " + id));
    }

    public void deleteShoppingList(String id) {
        shoppingListRepository.deleteById(id);
    }

    public List<ShoppingList> getShoppingListsScheduledBetween(Date dateFrom, Date dateTo) {
        return shoppingListRepository.findByNextRunDateBetween(dateFrom, dateTo);
    }
}

