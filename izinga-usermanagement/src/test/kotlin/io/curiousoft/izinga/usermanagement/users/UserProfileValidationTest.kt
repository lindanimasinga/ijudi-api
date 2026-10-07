package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.Bank
import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.IcaAcceptanceLogRepo
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

/**
 * Tests for the explicit required-field validation added to UserProfileService.
 *
 * Background: Profile.kt and UserProfile.kt declare @NotBlank/@NotNull on constructor parameters
 * without an explicit @field: use-site target.  Kotlin routes bare constructor-parameter
 * annotations to the @param: target, which Hibernate Validator ignores when validating an
 * object graph.  This means all annotation-based constraints on these fields are silently
 * inert at runtime — empirically proven by PATCHing with name:"" and receiving HTTP 200.
 *
 * The fix adds explicit manual validation in UserProfileService mirroring the proven-safe
 * StoreService.validateBankForCreate() pattern, throwing ResponseStatusException(400, reason)
 * for any missing or blank required field.
 */
@ExtendWith(MockitoExtension::class)
class UserProfileValidationTest {

    @Mock lateinit var userProfileRepo: UserProfileRepo
    @Mock lateinit var eventPublisher: ApplicationEventPublisher
    @Mock lateinit var userConfigService: UserConfigService
    @Mock lateinit var icaAcceptanceLogRepo: IcaAcceptanceLogRepo
    @Mock lateinit var referralCodeService: ReferralCodeService

    private lateinit var profileService: UserProfileService

    @BeforeEach
    fun setUp() {
        `when`(userConfigService.findAll()).thenReturn(emptyList())
        profileService = UserProfileService(
            userProfileRepo, eventPublisher, userConfigService, icaAcceptanceLogRepo, referralCodeService
        )
    }

    // -----------------------------------------------------------------------
    // create() — required-field guards
    // -----------------------------------------------------------------------

    @Test
    fun `create rejects null name`() {
        val profile = UserProfile(null, UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("name is required", ex.reason)
        verify(userProfileRepo, never()).save(profile)
    }

    @Test
    fun `create rejects blank name`() {
        val profile = UserProfile("  ", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("name is required", ex.reason)
        verify(userProfileRepo, never()).save(profile)
    }

    @Test
    fun `create rejects null mobileNumber`() {
        val profile = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", null, ProfileRoles.CUSTOMER)
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("mobileNumber is required", ex.reason)
        verify(userProfileRepo, never()).save(profile)
    }

    @Test
    fun `create rejects blank mobileNumber`() {
        val profile = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", "", ProfileRoles.CUSTOMER)
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("mobileNumber is required", ex.reason)
        verify(userProfileRepo, never()).save(profile)
    }

    @Test
    fun `create rejects null role`() {
        val profile = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", null)
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("role is required", ex.reason)
        verify(userProfileRepo, never()).save(profile)
    }

    @Test
    fun `create rejects null signUpReason`() {
        val profile = UserProfile("John", null, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("signUpReason is required", ex.reason)
        verify(userProfileRepo, never()).save(profile)
    }

    @Test
    fun `create rejects null imageUrl`() {
        val profile = UserProfile("John", UserProfile.SignUpReason.BUY, "address", null, "+27812815707", ProfileRoles.CUSTOMER)
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("imageUrl is required", ex.reason)
        verify(userProfileRepo, never()).save(profile)
    }

    @Test
    fun `create rejects blank imageUrl`() {
        val profile = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "", "+27812815707", ProfileRoles.CUSTOMER)
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("imageUrl is required", ex.reason)
        verify(userProfileRepo, never()).save(profile)
    }

    @Test
    fun `create passes with valid minimal profile`() {
        val profile = UserProfile("John", UserProfile.SignUpReason.BUY, "Cape Town", "https://img", "0812815707", ProfileRoles.CUSTOMER)
        `when`(userProfileRepo.existsByMobileNumber("+27812815707")).thenReturn(false)
        `when`(userProfileRepo.save(profile)).thenReturn(profile)
        profileService.create(profile)
        verify(userProfileRepo).save(profile)
    }

    /**
     * Regression guard for the createAmbassador() break introduced when commit 3340ec4
     * added an unconditional imageUrl check to validateUserProfileForCreate().
     *
     * Admin-initiated AMBASSADOR records are created with blank address (no home address
     * yet) and PENDING_PHOTO_URL as imageUrl (no selfie yet).  Both must be accepted by
     * profileService.create() — address is not validated at create time, and imageUrl is
     * non-blank (the placeholder satisfies the check).
     */
    @Test
    fun `create succeeds for ambassador shape — blank address and pending-photo placeholder imageUrl`() {
        val pendingPhotoUrl = AmbassadorAdminController.PENDING_PHOTO_URL
        val profile = UserProfile(
            "Sipho Dlamini",
            UserProfile.SignUpReason.DELIVERY_DRIVER,
            "",                   // blank address — intentional for admin-initiated records
            pendingPhotoUrl,      // placeholder, not blank — satisfies imageUrl check
            "0831234567",
            ProfileRoles.AMBASSADOR
        ).apply { profileApproved = true }
        `when`(userProfileRepo.existsByMobileNumber("+27831234567")).thenReturn(false)
        `when`(userProfileRepo.save(profile)).thenReturn(profile)
        profileService.create(profile)
        verify(userProfileRepo).save(profile)
    }

    /**
     * Confirm the self-signup path (POST /user) still correctly rejects a blank imageUrl.
     * This must NOT regress — the ambassador fix must NOT weaken this guard.
     */
    @Test
    fun `create still rejects blank imageUrl for self-signup CUSTOMER profile`() {
        val profile = UserProfile("Jane", UserProfile.SignUpReason.BUY, "Johannesburg", "", "0821234567", ProfileRoles.CUSTOMER)
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("imageUrl is required", ex.reason)
        verify(userProfileRepo, never()).save(profile)
    }

    @Test
    fun `create rejects bank with missing accountId`() {
        val bank = Bank().apply { accountId = ""; name = "FNB"; branchCode = "250655"; phone = "0800"; type = BankAccType.CHEQUE }
        val profile = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        profile.bank = bank
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Bank account ID is required", ex.reason)
    }

    @Test
    fun `create rejects bank with missing name`() {
        val bank = Bank().apply { accountId = "12345678"; name = ""; branchCode = "250655"; phone = "0800"; type = BankAccType.CHEQUE }
        val profile = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        profile.bank = bank
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Bank name is required", ex.reason)
    }

    @Test
    fun `create rejects bank with missing branchCode`() {
        val bank = Bank().apply { accountId = "12345678"; name = "FNB"; branchCode = ""; phone = "0800"; type = BankAccType.CHEQUE }
        val profile = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        profile.bank = bank
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Bank branch code is required", ex.reason)
    }

    @Test
    fun `create rejects bank with missing phone`() {
        val bank = Bank().apply { accountId = "12345678"; name = "FNB"; branchCode = "250655"; phone = ""; type = BankAccType.CHEQUE }
        val profile = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        profile.bank = bank
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Bank phone is required", ex.reason)
    }

    @Test
    fun `create rejects bank with null type`() {
        val bank = Bank().apply { accountId = "12345678"; name = "FNB"; branchCode = "250655"; phone = "0800"; type = null }
        val profile = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        profile.bank = bank
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Bank account type is required", ex.reason)
    }

    @Test
    fun `create rejects bank with wallet legacy type`() {
        val bank = Bank().apply { accountId = "12345678"; name = "FNB"; branchCode = "250655"; phone = "0800"; type = BankAccType.wallet }
        val profile = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        profile.bank = bank
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Bank account type 'wallet' is not valid", ex.reason)
    }

    @Test
    fun `create rejects bank with string legacy type`() {
        val bank = Bank().apply { accountId = "12345678"; name = "FNB"; branchCode = "250655"; phone = "0800"; type = BankAccType.string }
        val profile = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        profile.bank = bank
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.create(profile) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Bank account type 'string' is not valid", ex.reason)
    }

    // -----------------------------------------------------------------------
    // update() — required-field guards (the empirically proven gap)
    // -----------------------------------------------------------------------

    private fun persistedProfile(id: String = "testId") = UserProfile(
        "John", UserProfile.SignUpReason.BUY, "Cape Town", "https://img", "+27812815707", ProfileRoles.CUSTOMER
    ).also { it.id = id }

    @Test
    fun `update rejects blank name — this is the proven empirical bug`() {
        val incoming = UserProfile("", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.update("testId", incoming) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("name is required", ex.reason)
        // Validation fires before any repo call
        verify(userProfileRepo, never()).findById("testId")
        verify(userProfileRepo, never()).save(incoming)
    }

    @Test
    fun `update rejects whitespace-only name`() {
        val incoming = UserProfile("   ", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.update("testId", incoming) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("name is required", ex.reason)
    }

    @Test
    fun `update allows null name — field not being changed`() {
        // null name means this PATCH is not changing the name field;
        // BeanUtils.copyProperties will set it to null but that is a separate
        // data integrity concern (the persisted record had a valid name).
        val incoming = UserProfile(null, UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        val persisted = persistedProfile()
        `when`(userProfileRepo.findById("testId")).thenReturn(Optional.of(persisted))
        `when`(userProfileRepo.save(persisted)).thenReturn(persisted)
        profileService.update("testId", incoming)
        verify(userProfileRepo).save(persisted)
    }

    @Test
    fun `update rejects blank surname`() {
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        incoming.surname = ""
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.update("testId", incoming) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("surname is required", ex.reason)
        verify(userProfileRepo, never()).findById("testId")
    }

    @Test
    fun `update allows null surname — not required if not provided`() {
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        incoming.surname = null
        val persisted = persistedProfile()
        `when`(userProfileRepo.findById("testId")).thenReturn(Optional.of(persisted))
        `when`(userProfileRepo.save(persisted)).thenReturn(persisted)
        profileService.update("testId", incoming)
        verify(userProfileRepo).save(persisted)
    }

    @Test
    fun `update rejects blank emailAddress`() {
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        incoming.emailAddress = ""
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.update("testId", incoming) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("emailAddress is required", ex.reason)
        verify(userProfileRepo, never()).findById("testId")
    }

    @Test
    fun `update allows null emailAddress — not required if not provided`() {
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        incoming.emailAddress = null
        val persisted = persistedProfile()
        `when`(userProfileRepo.findById("testId")).thenReturn(Optional.of(persisted))
        `when`(userProfileRepo.save(persisted)).thenReturn(persisted)
        profileService.update("testId", incoming)
        verify(userProfileRepo).save(persisted)
    }

    @Test
    fun `update rejects blank address`() {
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.update("testId", incoming) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("address is required", ex.reason)
        verify(userProfileRepo, never()).findById("testId")
    }

    @Test
    fun `update allows null address`() {
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, null, "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        val persisted = persistedProfile()
        `when`(userProfileRepo.findById("testId")).thenReturn(Optional.of(persisted))
        `when`(userProfileRepo.save(persisted)).thenReturn(persisted)
        profileService.update("testId", incoming)
        verify(userProfileRepo).save(persisted)
    }

    @Test
    fun `update rejects blank mobileNumber`() {
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "https://img", "", ProfileRoles.CUSTOMER)
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.update("testId", incoming) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("mobileNumber is required", ex.reason)
        verify(userProfileRepo, never()).findById("testId")
    }

    @Test
    fun `update rejects blank imageUrl`() {
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "address", "", "+27812815707", ProfileRoles.CUSTOMER)
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.update("testId", incoming) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("imageUrl is required", ex.reason)
        verify(userProfileRepo, never()).findById("testId")
    }

    @Test
    fun `update allows null imageUrl — field not being changed`() {
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "address", null, "+27812815707", ProfileRoles.CUSTOMER)
        val persisted = persistedProfile()
        `when`(userProfileRepo.findById("testId")).thenReturn(Optional.of(persisted))
        `when`(userProfileRepo.save(persisted)).thenReturn(persisted)
        profileService.update("testId", incoming)
        verify(userProfileRepo).save(persisted)
    }

    @Test
    fun `update allows null bank — bank is optional`() {
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "Cape Town", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        incoming.bank = null
        val persisted = persistedProfile()
        `when`(userProfileRepo.findById("testId")).thenReturn(Optional.of(persisted))
        `when`(userProfileRepo.save(persisted)).thenReturn(persisted)
        profileService.update("testId", incoming)
        verify(userProfileRepo).save(persisted)
    }

    @Test
    fun `update rejects bank with missing accountId`() {
        val bank = Bank().apply { accountId = null; name = "FNB"; branchCode = "250655"; phone = "0800"; type = BankAccType.CHEQUE }
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "Cape Town", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        incoming.bank = bank
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.update("testId", incoming) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Bank account ID is required", ex.reason)
    }

    @Test
    fun `update rejects bank with missing bank name`() {
        val bank = Bank().apply { accountId = "12345678"; name = null; branchCode = "250655"; phone = "0800"; type = BankAccType.CHEQUE }
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "Cape Town", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        incoming.bank = bank
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.update("testId", incoming) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Bank name is required", ex.reason)
    }

    @Test
    fun `update rejects bank with missing branchCode`() {
        val bank = Bank().apply { accountId = "12345678"; name = "FNB"; branchCode = null; phone = "0800"; type = BankAccType.CHEQUE }
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "Cape Town", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        incoming.bank = bank
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.update("testId", incoming) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Bank branch code is required", ex.reason)
    }

    @Test
    fun `update rejects bank with missing phone`() {
        val bank = Bank().apply { accountId = "12345678"; name = "FNB"; branchCode = "250655"; phone = null; type = BankAccType.CHEQUE }
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "Cape Town", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        incoming.bank = bank
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.update("testId", incoming) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Bank phone is required", ex.reason)
    }

    @Test
    fun `update rejects bank with null type`() {
        val bank = Bank().apply { accountId = "12345678"; name = "FNB"; branchCode = "250655"; phone = "0800"; type = null }
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "Cape Town", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        incoming.bank = bank
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.update("testId", incoming) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Bank account type is required", ex.reason)
    }

    @Test
    fun `update rejects bank with wallet legacy type`() {
        val bank = Bank().apply { accountId = "12345678"; name = "FNB"; branchCode = "250655"; phone = "0800"; type = BankAccType.wallet }
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "Cape Town", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        incoming.bank = bank
        val ex = assertThrows(ResponseStatusException::class.java) { profileService.update("testId", incoming) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Bank account type 'wallet' is not valid", ex.reason)
    }

    @Test
    fun `update with complete valid profile succeeds`() {
        val bank = Bank().apply { accountId = "12345678"; name = "FNB"; branchCode = "250655"; phone = "0800"; type = BankAccType.CHEQUE }
        val incoming = UserProfile("John", UserProfile.SignUpReason.BUY, "Cape Town", "https://img", "+27812815707", ProfileRoles.CUSTOMER)
        incoming.surname = "Doe"
        incoming.emailAddress = "john@example.com"
        incoming.bank = bank
        val persisted = persistedProfile()
        `when`(userProfileRepo.findById("testId")).thenReturn(Optional.of(persisted))
        `when`(userProfileRepo.save(persisted)).thenReturn(persisted)
        profileService.update("testId", incoming)
        verify(userProfileRepo).save(persisted)
    }
}
