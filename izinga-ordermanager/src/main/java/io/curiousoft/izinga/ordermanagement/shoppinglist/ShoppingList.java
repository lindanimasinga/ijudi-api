package io.curiousoft.izinga.ordermanagement.shoppinglist;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Slf4j
public class ShoppingList {

    @Id
    private String id = UUID.randomUUID().toString();
    private List<ShoppingItem> items = new ArrayList<>();
    private Schedule schedule;
    private Date startDate, endDate;
    private Date nextRunDate;
    private List<String> userIds;
    private String name;
    private String shopId;

    public ShoppingList(String name, Schedule schedule, Date startDate, Date endDate, List<String> userIds) {
        this.name = name;
        this.schedule = schedule;
        this.startDate = startDate;
        this.endDate = endDate;
        this.userIds = userIds;
        this.nextRunDate = startDate;
    }

    public ShoppingList() {
    }

    public enum Schedule {
        ONCE_OFF, DAILY, WEEKLY, MONTHLY, YEARLY
    }

    public void getNextRunDate() {
        nextRunDate = switch (schedule) {
            case ONCE_OFF -> startDate;
            case DAILY -> new Date(startDate.getTime() + 24L * 60 * 60 * 1000);
            case WEEKLY -> LocalDateTime.ofInstant(startDate.toInstant(), ZoneId.systemDefault())
                            .plusWeeks(1)
                            .toInstant(ZoneOffset.UTC)
                            .toEpochMilli() > endDate.getTime() ?
                    endDate : // if next run date exceeds end date, use end date
                    new Date(LocalDateTime.ofInstant(startDate.toInstant(), ZoneId.systemDefault())
                            .plusWeeks(1)
                            .toInstant(ZoneOffset.UTC).toEpochMilli());
            case MONTHLY -> LocalDateTime.ofInstant(startDate.toInstant(), ZoneId.systemDefault())
                    .plusMonths(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli() > endDate.getTime() ?
                    endDate : // if next run date exceeds end date, use end date
                    new Date(LocalDateTime.ofInstant(startDate.toInstant(), ZoneId.systemDefault())
                            .plusMonths(1)
                            .toInstant(ZoneOffset.UTC).toEpochMilli());
            case YEARLY -> {
                LocalDateTime nextYear = LocalDateTime
                        .ofInstant(startDate.toInstant(), ZoneId.systemDefault())
                        .plusYears(1);
                if (nextYear.toInstant(ZoneOffset.UTC).toEpochMilli() > endDate.getTime()) {
                    yield endDate; // if next run date exceeds end date, use end date
                } else {
                    yield Date.from(nextYear.toInstant(ZoneOffset.UTC));
                }
            }
        };
    }
}
