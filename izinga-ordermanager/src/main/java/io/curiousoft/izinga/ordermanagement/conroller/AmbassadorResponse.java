package io.curiousoft.izinga.ordermanagement.conroller;

public class AmbassadorResponse {
    private String userId;
    private String referralUrl;

    public AmbassadorResponse() {}

    public AmbassadorResponse(String userId, String referralUrl) {
        this.userId = userId;
        this.referralUrl = referralUrl;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getReferralUrl() { return referralUrl; }
    public void setReferralUrl(String referralUrl) { this.referralUrl = referralUrl; }
}
