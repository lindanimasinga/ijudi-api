package io.curiousoft.izinga.ordermanagement.auth;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Standard Spring Data Mongo repo for WhatsApp OTP documents.
 * The atomic "mark used" operation lives in WhatsAppOtpRepositoryCustom /
 * WhatsAppOtpRepositoryImpl to enforce the SEC-03 findOneAndUpdate contract.
 */
public interface WhatsAppOtpRepository extends MongoRepository<WhatsAppOtpDocument, String>,
        WhatsAppOtpRepositoryCustom {

    /** Find the most recent unused, unexpired OTP for a normalized mobile number. */
    Optional<WhatsAppOtpDocument> findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(
            String mobileNumber);

    /** Count all OTP documents for this number (for rate-limit checks). */
    long countByMobileNumber(String mobileNumber);

    /** Delete all OTPs for a mobile number (cleanup on successful verify). */
    void deleteByMobileNumber(String mobileNumber);
}
