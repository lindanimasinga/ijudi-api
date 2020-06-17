package io.curiousoft.ijudi.ordermanagement.model;

import io.curiousoft.ijudi.ordermanagement.service.BaseModel;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.NotBlank;
import java.util.UUID;

@Document
public class Device extends BaseModel {

    @NotBlank(message = "device token required")
    @Indexed(unique = true)
    private String token;
    private String userId;

    public Device(@NotBlank(message = "device token required") String token) {
        super(UUID.randomUUID().toString());
        this.token = token;
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
