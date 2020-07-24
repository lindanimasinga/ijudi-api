package io.curiousoft.ijudi.ordermanagement.model;

import org.springframework.data.mongodb.core.index.Indexed;

import javax.validation.constraints.NotBlank;
import java.util.UUID;

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

    public Device() {
        super(null);
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
