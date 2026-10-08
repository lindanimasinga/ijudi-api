package io.curiousoft.izinga.ordermanagement.stores;

/**
 * ONB-02 T-06: Request body for PATCH /store/{id}/ica-acceptance.
 *
 * <p>Only two fields are accepted from the client: the acceptance flag and the ICA document version.
 * All other audit fields (userId, IP, userAgent, timestamp) are derived server-side.
 */
public class IcaAcceptanceRequest {

    /** Must be {@code true}. The endpoint rejects requests where this is {@code false}. */
    private boolean icaAccepted;

    /**
     * Version string of the ICA document the user accepted, e.g. "merchant-v2".
     * Must not be blank.
     */
    private String icaVersion;

    public boolean isIcaAccepted() { return icaAccepted; }
    public void setIcaAccepted(boolean icaAccepted) { this.icaAccepted = icaAccepted; }

    public String getIcaVersion() { return icaVersion; }
    public void setIcaVersion(String icaVersion) { this.icaVersion = icaVersion; }
}
