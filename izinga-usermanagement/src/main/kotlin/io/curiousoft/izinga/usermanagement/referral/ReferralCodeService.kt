package io.curiousoft.izinga.usermanagement.referral

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.SecureRandom

/**
 * RP-003: Referral Code generation and lookup for REFERRAL_PARTNER users.
 *
 * A referral code is:
 * - Unique per Referral Partner.
 * - Permanent — assigned once at enrollment, never regenerated (idempotent).
 * - Non-sequential and non-guessable: 8 uppercase alphanumeric characters drawn
 *   from a 32-character Crockford-style alphabet (excludes I, L, O, U to avoid
 *   visual ambiguity), giving ~1.1 trillion possible values.
 * - Stored directly on UserProfile.referralCode (same pattern as ambassadorId).
 * - Indexed via UserProfileRepo.findByReferralCode for O(1) attribution lookup
 *   (MongoDB unique sparse index recommended — see docs/referral-partner-agreement.md).
 *
 * One code works across all referral link formats:
 *   izinga.co.za/join?ref=CODE          (food/furniture customer referral)
 *   biz.izinga.co.za/register?ref=CODE  (store partner referral)
 */
@Service
class ReferralCodeService(private val userProfileRepo: UserProfileRepo) {

    private val log = LoggerFactory.getLogger(ReferralCodeService::class.java)

    companion object {
        // Crockford Base32 subset — excludes I, L, O, U to avoid visual ambiguity
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val CODE_LENGTH = 8
        private const val MAX_GENERATION_ATTEMPTS = 10
    }

    private val random = SecureRandom()

    /**
     * Ensures the given REFERRAL_PARTNER user has a referral code.
     * If one already exists this is a no-op (idempotent).
     * If the user is not a REFERRAL_PARTNER, throws IllegalArgumentException.
     *
     * Called by RP-002 enrollment flow after the profile is persisted.
     *
     * @return the profile with referralCode guaranteed to be set.
     */
    fun assignReferralCode(profile: UserProfile): UserProfile {
        require(profile.role == ProfileRoles.REFERRAL_PARTNER) {
            "referralCode may only be assigned to REFERRAL_PARTNER profiles, got role=${profile.role}"
        }

        // Idempotency guard — never regenerate an existing code
        if (!profile.referralCode.isNullOrBlank()) {
            log.info("referralCode already set for userId={}, skipping generation", profile.id)
            return profile
        }

        val code = generateUniqueCode()
        profile.referralCode = code
        val saved = userProfileRepo.save(profile)
        log.info("Assigned referralCode={} to userId={}", code, saved.id)
        return saved
    }

    /**
     * Resolves a referral code string to its owning REFERRAL_PARTNER profile.
     * Returns null if the code is unknown — callers should treat null as "no attribution".
     *
     * Used by RP-004 (customer attribution) and RP-005 (store attribution).
     */
    fun resolveCode(referralCode: String): UserProfile? {
        if (referralCode.isBlank()) return null
        return userProfileRepo.findByReferralCode(referralCode.uppercase().trim())
    }

    // --- internal ---

    internal fun generateUniqueCode(): String {
        repeat(MAX_GENERATION_ATTEMPTS) {
            val candidate = buildCode()
            if (userProfileRepo.findByReferralCode(candidate) == null) {
                return candidate
            }
            log.warn("referralCode collision on candidate={}, retrying", candidate)
        }
        throw IllegalStateException("Failed to generate a unique referral code after $MAX_GENERATION_ATTEMPTS attempts")
    }

    private fun buildCode(): String {
        val sb = StringBuilder(CODE_LENGTH)
        repeat(CODE_LENGTH) { sb.append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        return sb.toString()
    }
}
