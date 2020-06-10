package io.curiousoft.ijudi.ordermanagement.service.ukheshe;

import java.util.Date;

public class UkhesheAuthtoken {
    private Date expires;
    private String headerValue;

    public Date getExpires() {
        return expires;
    }

    public void setExpires(Date expires) {
        this.expires = expires;
    }

    public String getHeaderValue() {
        return headerValue;
    }

    public void setHeaderValue(String headerValue) {
        this.headerValue = headerValue;
    }

    public boolean hasExpired() {
        return expires.before(new Date());
    }
}
