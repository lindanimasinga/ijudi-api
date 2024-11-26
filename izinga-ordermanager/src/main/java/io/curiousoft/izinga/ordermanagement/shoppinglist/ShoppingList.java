package io.curiousoft.izinga.ordermanagement.shoppinglist;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Slf4j
public class ShoppingList {

    private List<ShoppingItem> items = new ArrayList<>();
    private Schedule schedule;
    private Date startDate, endDate;
    private List<String> userIds;
    private String name;

    public ShoppingList(String name, Schedule schedule, Date startDate, Date endDate, List<String> userIds) {
        this.name = name;
        this.schedule = schedule;
        this.startDate = startDate;
        this.endDate = endDate;
        this.userIds = userIds;
    }

    public ShoppingList() {
    }

    public enum Schedule {
        ONCE_OFF, DAILY, WEEKLY, MONTHLY, YEARLY
    }
}
