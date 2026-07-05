package io.curiousoft.izinga.ordermanagement.leads;

public class LeadStatusUpdateRequest {
    private LeadStatus status;

    public LeadStatusUpdateRequest() {}

    public LeadStatus getStatus() { return status; }
    public void setStatus(LeadStatus status) { this.status = status; }
}
