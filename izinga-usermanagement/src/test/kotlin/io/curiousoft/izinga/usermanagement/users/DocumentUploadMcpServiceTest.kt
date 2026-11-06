package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.security.CallerIdentityContext
import io.curiousoft.izinga.usermanagement.userconfig.FieldDataType
import io.curiousoft.izinga.usermanagement.userconfig.FieldSpec
import io.curiousoft.izinga.usermanagement.userconfig.UserConfig
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class DocumentUploadMcpServiceTest {

    @Mock
    private lateinit var userProfileService: UserProfileService

    @Mock
    private lateinit var userConfigService: UserConfigService

    @Mock
    private lateinit var documentUploadSessionRepo: DocumentUploadSessionRepo

    private lateinit var service: DocumentUploadMcpService

    @BeforeEach
    fun setUp() {
        // ADR-026 Phase 1: DocumentUploadMcpService is a 3-param service (agentIdentityAuditService
        // was removed when the phone-identity gate moved to DocumentUploadMcpController).
        service = DocumentUploadMcpService(userProfileService, userConfigService, documentUploadSessionRepo)
    }

    @AfterEach
    fun tearDown() {
        CallerIdentityContext.clear()
    }

    @Test
    fun getRequiredDocumentsForUser_returnsRequiredDocumentStatus() {
        val phone = "+27812815577"
        val user = testUser(phone)
        user.tag["identityDocument"] = "https://files/id.pdf"

        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userProfileService.getAllMissingFields(phone)).thenReturn(listOf("pdpDocument", "idNumber"))
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))

        val response = service.getRequiredDocumentsForUser(phone)

        assertEquals("user-1", response.userId)
        assertEquals(listOf("pdpDocument", "idNumber"), response.missingFields)
        assertEquals(listOf("pdpDocument"), response.missingDocumentFields)
        assertTrue(response.documentFields.any { it.name == "identityDocument" && it.uploaded })
        assertTrue(response.documentFields.any { it.name == "pdpDocument" && !it.uploaded })
    }

    @Test
    fun attachUploadedDocumentToUserTag_updatesTagAndReturnsRemainingMissing() {
        val phone = "+27812815577"
        val user = testUser(phone)

        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))
        Mockito.`when`(userProfileService.getAllMissingFields(phone)).thenReturn(listOf("idNumber"))
        Mockito.`when`(
            documentUploadSessionRepo.findFirstByUserIdAndFieldNameAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                any(),
                any(),
                any()
            )
        ).thenReturn(
            DocumentUploadSession(
                id = "session-1",
                userId = "user-1",
                fieldName = "identityDocument",
                originalFileName = "id.png",
                mimeType = "image/png",
                createdAt = java.time.Instant.now(),
                expiresAt = java.time.Instant.now().plusSeconds(300),
            )
        )

        val response = service.attachUploadedDocumentToUserTag(phone, "identityDocument", "https://files/id.png")

        assertTrue(response.success)
        assertEquals("https://files/id.png", user.tag["identityDocument"])
        assertEquals(listOf("idNumber"), response.remainingMissingFields)
        Mockito.verify(userProfileService).update("user-1", user)
    }

    @Test
    fun attachUploadedDocumentToUserTag_rejectsWhenNoActiveSession() {
        val phone = "+27812815577"
        val user = testUser(phone)
        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))
        Mockito.`when`(
            documentUploadSessionRepo.findFirstByUserIdAndFieldNameAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                any(),
                any(),
                any()
            )
        ).thenReturn(null)

        val error = assertThrows(IllegalArgumentException::class.java) {
            service.attachUploadedDocumentToUserTag(phone, "identityDocument", "https://files/id.png")
        }

        assertTrue(error.message!!.contains("No active upload session found"))
    }

    @Test
    fun attachUploadedDocumentToUserTag_allowsWhatsappUrlWithoutSession() {
        val phone = "+27812815577"
        val user = testUser(phone)
        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))
        Mockito.`when`(userProfileService.getAllMissingFields(phone)).thenReturn(emptyList())
        Mockito.`when`(
            documentUploadSessionRepo.findFirstByUserIdAndFieldNameAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                any(), any(), any()
            )
        ).thenReturn(null)

        val response = service.attachUploadedDocumentToUserTag(
            phone,
            "identityDocument",
            "https://lookaside.fbsbx.com/whatsapp_business/attachments/file123"
        )

        assertTrue(response.success)
        assertEquals(
            "https://lookaside.fbsbx.com/whatsapp_business/attachments/file123",
            user.tag["identityDocument"]
        )
        Mockito.verify(userProfileService).update("user-1", user)
    }

    @Test
    fun createDocumentUploadSession_savesAndReturnsSession() {
        val phone = "+27812815577"
        val user = testUser(phone)
        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))
        Mockito.`when`(documentUploadSessionRepo.save(Mockito.any(DocumentUploadSession::class.java)))
            .thenAnswer { invocation -> invocation.getArgument(0) }

        val response = service.createDocumentUploadSession(phone, "identityDocument", "id.jpg", "image/jpeg")

        assertEquals("identityDocument", response.fieldName)
        assertEquals("/document?metadata=false", response.uploadEndpoint)
        assertTrue(response.acceptedMimeTypes.contains("image/jpeg"))
        assertNotNull(response.sessionId)
    }

    @Test
    fun createDocumentUploadSession_rejectsUnsupportedMimeType() {
        val phone = "+27812815577"
        val user = testUser(phone)
        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)

        val error = assertThrows(IllegalArgumentException::class.java) {
            service.createDocumentUploadSession(phone, "identityDocument", "id.bmp", "image/bmp")
        }

        assertTrue(error.message!!.contains("Unsupported mimeType"))
    }

    @Test
    fun attachUploadedDocumentToUserTag_rejectsNonDocumentField() {
        val phone = "+27812815577"
        val user = testUser(phone)
        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))

        val error = assertThrows(IllegalArgumentException::class.java) {
            service.attachUploadedDocumentToUserTag(phone, "idNumber", "https://files/id.png")
        }

        assertTrue(error.message!!.contains("is not a document field"))
    }

    @Test
    fun getRequiredDocumentsForUser_rejectsWhenConfigMissing() {
        val phone = "+27812815577"
        val user = testUser(phone)
        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userConfigService.findAll()).thenReturn(emptyList())

        val error = assertThrows(IllegalArgumentException::class.java) {
            service.getRequiredDocumentsForUser(phone)
        }

        assertTrue(error.message!!.contains("No user config found for service type"))
    }

    @Test
    fun getRequiredDocumentsForUser_rejectsShortPhone() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            service.getRequiredDocumentsForUser("123")
        }

        assertTrue(error.message!!.contains("phone must contain at least 9"))
    }

    // ---- ADR-024: caller identity enforcement ----

    @Test
    fun getRequiredDocumentsForUser_customerAudience_ownPhone_passes() {
        val phone = "+27812815577"
        val user = testUser(phone)
        CallerIdentityContext.setAudience("CUSTOMER")
        CallerIdentityContext.setCallerPhone(phone)

        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userProfileService.getAllMissingFields(phone)).thenReturn(emptyList())
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))

        // Must not throw
        service.getRequiredDocumentsForUser(phone)
    }

    @Test
    fun getRequiredDocumentsForUser_customerAudience_thirdPartyPhone_serviceIsUngated_processesNormally() {
        // ADR-026 Phase 1: phone-identity gate moved to DocumentUploadMcpController.
        // The service itself processes any valid phone without restriction.
        val phone = "+27811111111"
        val user = testUser(phone)
        CallerIdentityContext.setAudience("CUSTOMER")
        CallerIdentityContext.setCallerPhone("+27812815577") // different — service no longer cares

        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userProfileService.getAllMissingFields(phone)).thenReturn(emptyList())
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))

        // Must NOT throw CallerIdentityViolationException after Phase 1
        service.getRequiredDocumentsForUser(phone)
    }

    @Test
    fun createDocumentUploadSession_customerAudience_ownPhone_passes() {
        val phone = "+27812815577"
        val user = testUser(phone)
        CallerIdentityContext.setAudience("CUSTOMER")
        CallerIdentityContext.setCallerPhone(phone)

        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))
        Mockito.`when`(documentUploadSessionRepo.save(Mockito.any(DocumentUploadSession::class.java)))
            .thenAnswer { invocation -> invocation.getArgument(0) }

        // Must not throw
        service.createDocumentUploadSession(phone, "identityDocument", "id.jpg", "image/jpeg")
    }

    @Test
    fun createDocumentUploadSession_customerAudience_thirdPartyPhone_serviceIsUngated_processesNormally() {
        // ADR-026 Phase 1: phone-identity gate moved to DocumentUploadMcpController.
        val phone = "+27811111111"
        val user = testUser(phone)
        CallerIdentityContext.setAudience("CUSTOMER")
        CallerIdentityContext.setCallerPhone("+27812815577")

        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))
        Mockito.`when`(documentUploadSessionRepo.save(Mockito.any(DocumentUploadSession::class.java)))
            .thenAnswer { invocation -> invocation.getArgument(0) }

        // Must NOT throw CallerIdentityViolationException after Phase 1
        service.createDocumentUploadSession(phone, "identityDocument", "id.jpg", "image/jpeg")
    }

    @Test
    fun attachUploadedDocumentToUserTag_customerAudience_thirdPartyPhone_serviceIsUngated_processesNormally() {
        // ADR-026 Phase 1: phone-identity gate moved to DocumentUploadMcpController.
        val phone = "+27811111111"
        val user = testUser(phone)
        CallerIdentityContext.setAudience("CUSTOMER")
        CallerIdentityContext.setCallerPhone("+27812815577")

        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))
        Mockito.`when`(userProfileService.getAllMissingFields(phone)).thenReturn(emptyList())
        Mockito.`when`(
            documentUploadSessionRepo.findFirstByUserIdAndFieldNameAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                any(), any(), any()
            )
        ).thenReturn(
            DocumentUploadSession(
                id = "session-1",
                userId = "user-1",
                fieldName = "identityDocument",
                originalFileName = "id.png",
                mimeType = "image/png",
                createdAt = java.time.Instant.now(),
                expiresAt = java.time.Instant.now().plusSeconds(300),
            )
        )

        // Must NOT throw CallerIdentityViolationException after Phase 1
        val response = service.attachUploadedDocumentToUserTag(phone, "identityDocument", "https://files/id.png")
        assertTrue(response.success)
    }

    @Test
    fun recheckRequiredDocuments_customerAudience_thirdPartyPhone_serviceIsUngated_processesNormally() {
        // ADR-026 Phase 1: phone-identity gate moved to DocumentUploadMcpController.
        // The service no longer enforces the ownership check at all.
        val phone = "+27811111111"
        val user = testUser(phone)
        CallerIdentityContext.setAudience("CUSTOMER")
        CallerIdentityContext.setCallerPhone("+27812815577")

        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userProfileService.getAllMissingFields(phone)).thenReturn(emptyList())
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))

        // Must NOT throw CallerIdentityViolationException after Phase 1
        service.recheckRequiredDocuments(phone)
    }

    // ---- AMD-01 (REQ-A10): ADMIN audience passes identity validators for document tools ----

    /**
     * AMD-01 (REQ-A10): get_required_documents_for_user with ADMIN audience must pass the identity
     * gate for a third-party phone.
     */
    @Test
    fun getRequiredDocumentsForUser_adminAudience_thirdPartyPhone_passes() {
        val phone = "+27811111111"
        val user = testUser(phone)
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("ADMIN")
        // No callerPhone set — ADMIN bypasses phone matching

        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userProfileService.getAllMissingFields(phone)).thenReturn(emptyList())
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))

        // Must not throw CallerIdentityViolationException
        service.getRequiredDocumentsForUser(phone)
    }

    /**
     * AMD-01 (REQ-A10): create_document_upload_session with ADMIN audience must pass the identity
     * gate for a third-party phone.
     */
    @Test
    fun createDocumentUploadSession_adminAudience_thirdPartyPhone_passes() {
        val phone = "+27811111111"
        val user = testUser(phone)
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("ADMIN")

        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))
        Mockito.`when`(documentUploadSessionRepo.save(Mockito.any(DocumentUploadSession::class.java)))
            .thenAnswer { invocation -> invocation.getArgument(0) }

        // Must not throw CallerIdentityViolationException
        service.createDocumentUploadSession(phone, "identityDocument", "id.jpg", "image/jpeg")
    }

    /**
     * AMD-01 (REQ-A10): attach_uploaded_document_to_user_tag with ADMIN audience must pass the
     * identity gate for a third-party phone.
     */
    @Test
    fun attachUploadedDocumentToUserTag_adminAudience_thirdPartyPhone_passes() {
        val phone = "+27811111111"
        val user = testUser(phone)
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("ADMIN")

        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))
        Mockito.`when`(userProfileService.getAllMissingFields(phone)).thenReturn(emptyList())
        Mockito.`when`(
            documentUploadSessionRepo.findFirstByUserIdAndFieldNameAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                any(), any(), any()
            )
        ).thenReturn(
            DocumentUploadSession(
                id = "session-admin",
                userId = "user-1",
                fieldName = "identityDocument",
                originalFileName = "id.png",
                mimeType = "image/png",
                createdAt = java.time.Instant.now(),
                expiresAt = java.time.Instant.now().plusSeconds(300),
            )
        )

        // Must not throw CallerIdentityViolationException
        service.attachUploadedDocumentToUserTag(phone, "identityDocument", "https://files/id.png")
    }

    /**
     * AMD-01 (REQ-A10): recheck_required_documents with ADMIN audience must pass the identity
     * gate for a third-party phone (delegates to getRequiredDocumentsForUser).
     */
    @Test
    fun recheckRequiredDocuments_adminAudience_thirdPartyPhone_passes() {
        val phone = "+27811111111"
        val user = testUser(phone)
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("ADMIN")

        Mockito.`when`(userProfileService.findUserByPhone(phone)).thenReturn(user)
        Mockito.`when`(userProfileService.getAllMissingFields(phone)).thenReturn(emptyList())
        Mockito.`when`(userConfigService.findAll()).thenReturn(listOf(testConfig()))

        // Must not throw CallerIdentityViolationException
        service.recheckRequiredDocuments(phone)
    }

    private fun testUser(phone: String): UserProfile {
        val user = UserProfile(
            "Driver",
            UserProfile.SignUpReason.DELIVERY_DRIVER,
            "Address",
            "https://image.url/profile.png",
            phone,
            ProfileRoles.MESSENGER,
        )
        user.id = "user-1"
        user.description = "Driver"
        return user
    }

    private fun testConfig(): UserConfig {
        return UserConfig(
            name = "driver",
            label = "Driver",
            userRole = ProfileRoles.MESSENGER,
            mandatoryFields = listOf(
                FieldSpec("identityDocument", "Identity Document", FieldDataType.DOCUMENT_URL),
                FieldSpec("idNumber", "ID Number", FieldDataType.STRING),
            ),
            optionalFields = listOf(
                FieldSpec("pdpDocument", "PDP Document", FieldDataType.DOCUMENT_URL),
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> any(): T {
        Mockito.any<T>()
        return null as T
    }
}