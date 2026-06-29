package io.curiousoft.izinga.ordermanagement.shoppinglist;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface ShoppingListRepository extends MongoRepository<ShoppingList, String> {
    List<ShoppingList> findByUserIds(String userId);

    List<ShoppingList> findByNextRunDateBetween(Date dateFrom, Date dateTo);
}

