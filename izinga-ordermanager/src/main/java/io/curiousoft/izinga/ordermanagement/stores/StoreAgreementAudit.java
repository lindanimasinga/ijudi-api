package io.curiousoft.izinga.ordermanagement.stores;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * ONB-02 / ADR-017: Immutable, insert-only audit record written at every ICA acceptance click
 * for STORE_ADMIN users.
 *
 * <p>Once inserted this document must never be updated or deleted — the acceptance record is
 * a legally significant event log for L-07 Store/Merchant Partner Agreement compliance.
 *
 * <p>SEC-ONB02-01-B: {@code userAgent} and {@code verificationChannel} are required additions
 * from the T-02 Security & Compliance review.
 */
@Document(collection = "store_ica_audit")
public class StoreAgreementAudit {

    @Id
    private String id;

    /** MongoDB ID of the StoreProfile whose owner accepted the ICA. */
    private String storeId;

    /** Profile ID of the authenticated STORE_ADMIN or ADMIN who triggered the acceptance. */
    private String acceptedByUserId;

    /** Version string of the ICA document accepted, e.g. "merchant-v2". */
    private String icaVersion;

    /** Server-side timestamp of the acceptance — set by the service layer, never the client. */
    private Date acceptedAt;

    /** IP address from X-Forwarded-For header, falling back to the direct remote address. Nullable. */
    private String ipAddress;

    /** HTTP User-Agent header value from the acceptance request. SEC-ONB02-01-B required field. */
    private String userAgent;

    /**
     * Channel through which identity was verified before the ICA was shown.
     * SEC-ONB02-01-B required field. Hardcoded to {@code "WHATSAPP_OTP"} for the current
     * verification channel.
     */
    private String verificationChannel;

    public StoreAgreementAudit() { }

    public StoreAgreementAudit(String storeId,
                                String acceptedByUserId,
                                String icaVersion,
                                Date acceptedAt,
                                String ipAddress,
                                String userAgent,
                                String verificationChannel) {
        this.storeId = storeId;
        this.acceptedByUserId = acceptedByUserId;
        this.icaVersion = icaVersion;
        this.acceptedAt = acceptedAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.verificationChannel = verificationChannel;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }

    public String getAcceptedByUserId() { return acceptedByUserId; }
    public void setAcceptedByUserId(String acceptedByUserId) { this.acceptedByUserId = acceptedByUserId; }

    public String getIcaVersion() { return icaVersion; }
    public void setIcaVersion(String icaVersion) { this.icaVersion = icaVersion; }

    public Date getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Date acceptedAt) { this.acceptedAt = acceptedAt; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getVerificationChannel() { return verificationChannel; }
    public void setVerificationChannel(String verificationChannel) { this.verificationChannel = verificationChannel; }
}
