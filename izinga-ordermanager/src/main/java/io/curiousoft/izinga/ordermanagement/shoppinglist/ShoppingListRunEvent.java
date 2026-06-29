package io.curiousoft.izinga.ordermanagement.shoppinglist;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ShoppingListRunEvent extends ApplicationEvent {

    private final ShoppingList shoppingList;

    public ShoppingListRunEvent(Object caller, ShoppingList shoppingList) {
        super(caller);
        this.shoppingList = shoppingList;
    }
}
