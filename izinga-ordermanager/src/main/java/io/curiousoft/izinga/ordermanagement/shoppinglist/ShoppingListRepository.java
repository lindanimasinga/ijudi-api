package io.curiousoft.izinga.ordermanagement.shoppinglist;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShoppingListRepository extends MongoRepository<ShoppingList, Long> {
    List<ShoppingList> findByUserIds(String userId);
}

