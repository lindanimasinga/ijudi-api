package io.curiousoft.izinga.messaging.whatsapp.verification;

import io.curiousoft.izinga.commons.model.CriminalCheckData;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.whatsapp.webhooks.WhatsappWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * Service to handle verification consent responses from WhatsApp messages.
 * Processes "I Consent" / "Decline" button replies from the criminal_check_consent template.
 */
@Service
public class VerificationConsentService {

    private static final Logger LOG = LoggerFactory.getLogger(VerificationConsentService.class);

    private final UserProfileRepo userProfileRepo;

    public VerificationConsentService(UserProfileRepo userProfileRepo) {
        this.userProfileRepo = userProfileRepo;
    }

    /**
     * Handles verification consent replies from WhatsApp interactive messages.
     * Checks if user clicked "I Consent" or "Decline" buttons from the criminal_check_consent template.
     *
     * @param message The WhatsApp message
     * @param from    The sender's phone number
     * @return true if this was a verification consent reply and was handled, false otherwise
     */
    public boolean handleVerificationConsentReply(WhatsappWebhookPayload.Value.Message message, String from) {
        try {
            // Check for interactive button reply
            var interactive = message.getInteractive();
            if (interactive == null) {
                return false;
            }

            var buttonReply = interactive.getButtonReply();
            if (buttonReply == null) {
                return false;
            }

            String buttonId = buttonReply.getId();
            String buttonTitle = buttonReply.getTitle();

            // Check if this is a verification consent response
            if (isVerificationConsentAccept(buttonId) || isVerificationConsentAccept(buttonTitle)) {
                LOG.info("User {} accepted verification consent", from);
                updateVerificationConsent(from, true);
                return true;
            } else if (isVerificationConsentDecline(buttonId) || isVerificationConsentDecline(buttonTitle)) {
                LOG.info("User {} declined verification consent", from);
                updateVerificationConsent(from, false);
                return true;
            }

            return false;
        } catch (Exception e) {
            LOG.error("Error handling verification consent reply for {}", from, e);
            return false;
        }
    }

    /**
     * Checks if the button response indicates consent acceptance.
     */
    public boolean isVerificationConsentAccept(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase();
        return lower.contains("i consent")
                || lower.contains("accept verification")
                || lower.contains("consent_accept")
                || lower.equals("consent_yes");
    }

    /**
     * Checks if the button response indicates consent decline.
     */
    public boolean isVerificationConsentDecline(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase();
        return lower.contains("decline")
                || lower.contains("consent_decline")
                || lower.equals("consent_no");
    }

    /**
     * Updates the user's verification consent status in the database.
     *
     * @param mobileNumber The user's mobile number
     * @param accepted     Whether consent was accepted or declined
     */
    public void updateVerificationConsent(String mobileNumber, boolean accepted) {
        try {
            // Normalize phone number for lookup (handle +27 vs 27 formats)
            String normalizedNumber = normalizePhoneNumber(mobileNumber);
            UserProfile user = userProfileRepo.findByMobileNumber(normalizedNumber);

            if (user == null) {
                // Try with original number
                user = userProfileRepo.findByMobileNumber(mobileNumber);
            }

            if (user == null) {
                LOG.warn("No user found for mobile {} to update verification consent", mobileNumber);
                return;
            }

            // Initialize CriminalCheckData if null
            if (user.getCrminalCheckData() == null) {
                user.setCrminalCheckData(new CriminalCheckData());
            }

            // Update consent fields
            user.getCrminalCheckData().setCriminalRecordCheckAccepted(accepted);
            user.getCrminalCheckData().setCriminalRecordCheckDate(new Date());

            userProfileRepo.save(user);
            LOG.info("Updated verification consent for user {} (mobile={}) accepted={}", user.getId(), mobileNumber, accepted);

            // Log confirmation
            if (accepted) {
                LOG.info("User {} provided consent for verification checks", mobileNumber);
            } else {
                LOG.info("User {} declined consent for verification checks", mobileNumber);
            }

        } catch (Exception e) {
            LOG.error("Failed to update verification consent for {}", mobileNumber, e);
        }
    }

    /**
     * Normalizes phone number format for database lookup.
     * Handles variations like 27... vs +27...
     */
    public String normalizePhoneNumber(String phone) {
        if (phone == null) return null;
        // Remove any non-digit characters except +
        String cleaned = phone.replaceAll("[^\\d+]", "");
        // If starts with 27, add +
        if (cleaned.startsWith("27") && !cleaned.startsWith("+")) {
            return "+" + cleaned;
        }
        return cleaned;
    }

    public Boolean isVerificationMessage(WhatsappWebhookPayload.Value.Message message) {
        return message.getInteractive() != null
                && message.getInteractive().getButtonReply() != null
                && (isVerificationConsentAccept(message.getInteractive().getButtonReply().getId())
                || isVerificationConsentDecline(message.getInteractive().getButtonReply().getId())
                || isVerificationConsentAccept(message.getInteractive().getButtonReply().getTitle())
                || isVerificationConsentDecline(message.getInteractive().getButtonReply().getTitle()));
    }
}

