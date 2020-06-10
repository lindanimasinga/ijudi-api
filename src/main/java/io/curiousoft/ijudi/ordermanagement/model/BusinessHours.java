package io.curiousoft.ijudi.ordermanagement.model;

import java.time.DayOfWeek;
import java.util.Date;

public class BusinessHours {

    private DayOfWeek day;
    private Date open;
    private Date close;

    public BusinessHours(DayOfWeek day, Date open, Date close) {
        this.day = day;
        this.open = open;
        this.close = close;
    }

    public DayOfWeek getDay() {
        return day;
    }

    public void setDay(DayOfWeek day) {
        this.day = day;
    }

    public Date getOpen() {
        return open;
    }

    public void setOpen(Date open) {
        this.open = open;
    }

    public Date getClose() {
        return close;
    }

    public void setClose(Date close) {
        this.close = close;
    }
}
