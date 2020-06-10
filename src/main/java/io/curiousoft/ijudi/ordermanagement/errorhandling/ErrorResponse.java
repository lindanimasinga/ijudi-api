package io.curiousoft.ijudi.ordermanagement.errorhandling;

import org.springframework.http.HttpStatus;

import java.util.Date;

public class ErrorResponse {

    private Date timestamp = new Date();
    private HttpStatus status;
    private Object message;
    private String path;

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public void setStatus(HttpStatus status) {
        this.status = status;
    }

    public Object getMessage() {
        return message;
    }

    public void setMessage(Object message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
