package io.curiousoft.ijudi.ordermanagement.service;

import org.springframework.data.annotation.Id;

import java.util.Date;

public class BaseModel {

    @Id
    private String id;
    private Date createdDate = new Date();

    public BaseModel() {
    }

    public BaseModel(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }
}
