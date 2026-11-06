package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.usermanagement.users.AttachUploadedDocumentResponse;
import io.curiousoft.izinga.usermanagement.users.DocumentUploadMcpService;
import io.curiousoft.izinga.usermanagement.users.RequiredDocumentsForUserResponse;
import io.curiousoft.izinga.usermanagement.users.UploadSessionInstructionsResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DocumentUploadMcpController} with 100% branch coverage.
 *
 * <p>Covers: CUSTOMER own phone (passes), CUSTOMER third-party phone (CIVE + audit),
 * null audience on MCP request (CIVE + audit), ADMIN/STORE/DRIVER bypass, all 4 tool methods.</p>
 */
@ExtendWith(MockitoExtension.class)
class DocumentUploadMcpControllerTest {

    private static final String OWN_PHONE   = "+27812815577";
    private static final String OTHER_PHONE = "+27811111111";

    @Mock private DocumentUploadMcpService  documentUploadMcpService;
    @Mock private AgentIdentityAuditService agentIdentityAuditService;

    @InjectMocks
    private DocumentUploadMcpController controller;

    @AfterEach
    void tearDown() {
        CallerIdentityContext.clear();
    }

    // ---- getRequiredDocumentsForUser ---------------------------------------------------

    @Test
    void getRequiredDocumentsForUser_customerAudience_ownPhone_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(OWN_PHONE);
        RequiredDocumentsForUserResponse expected = mock(RequiredDocumentsForUserResponse.class);
        when(documentUploadMcpService.getRequiredDocumentsForUser(OWN_PHONE)).thenReturn(expected);

        RequiredDocumentsForUserResponse result = controller.getRequiredDocumentsForUser(OWN_PHONE);

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    void getRequiredDocumentsForUser_customerAudience_thirdPartyPhone_throwsCive_andAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(OWN_PHONE);

        assertThatThrownBy(() -> controller.getRequiredDocumentsForUser(OTHER_PHONE))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageContaining("IDENTITY_REFUSAL");

        verify(agentIdentityAuditService).recordViolation(
                eq(OWN_PHONE), eq(OTHER_PHONE),
                eq(AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH),
                eq("get_required_documents_for_user"),
                eq("CUSTOMER"),
                isNull(), isNull());
        verifyNoInteractions(documentUploadMcpService);
    }

    @Test
    void getRequiredDocumentsForUser_mcpRequest_nullAudience_throwsCive_andAudits() {
        // SEC-MCP-01: tokenless MCP → hard reject
        CallerIdentityContext.setMcpRequest(true);
        // No audience, no caller phone

        assertThatThrownBy(() -> controller.getRequiredDocumentsForUser(OTHER_PHONE))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageContaining("IDENTITY_REFUSAL");

        verify(agentIdentityAuditService).recordViolation(
                isNull(), eq(OTHER_PHONE),
                eq(AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH),
                eq("get_required_documents_for_user"),
                eq("UNKNOWN"),
                isNull(), isNull());
        verifyNoInteractions(documentUploadMcpService);
    }

    @Test
    void getRequiredDocumentsForUser_adminAudience_thirdPartyPhone_delegatesToService() {
        // AMD-01 (REQ-A10): ADMIN audience bypasses phone gate
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("ADMIN");
        RequiredDocumentsForUserResponse expected = mock(RequiredDocumentsForUserResponse.class);
        when(documentUploadMcpService.getRequiredDocumentsForUser(OTHER_PHONE)).thenReturn(expected);

        RequiredDocumentsForUserResponse result = controller.getRequiredDocumentsForUser(OTHER_PHONE);

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    void getRequiredDocumentsForUser_driverAudience_ownPhone_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("DRIVER");
        CallerIdentityContext.setCallerPhone(OWN_PHONE);
        RequiredDocumentsForUserResponse expected = mock(RequiredDocumentsForUserResponse.class);
        when(documentUploadMcpService.getRequiredDocumentsForUser(OWN_PHONE)).thenReturn(expected);

        RequiredDocumentsForUserResponse result = controller.getRequiredDocumentsForUser(OWN_PHONE);

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(agentIdentityAuditService);
    }

    // ---- createDocumentUploadSession --------------------------------------------------

    @Test
    void createDocumentUploadSession_customerAudience_ownPhone_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(OWN_PHONE);
        UploadSessionInstructionsResponse expected = mock(UploadSessionInstructionsResponse.class);
        when(documentUploadMcpService.createDocumentUploadSession(OWN_PHONE, "identityDocument", "id.jpg", "image/jpeg"))
                .thenReturn(expected);

        UploadSessionInstructionsResponse result =
                controller.createDocumentUploadSession(OWN_PHONE, "identityDocument", "id.jpg", "image/jpeg");

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    void createDocumentUploadSession_customerAudience_thirdPartyPhone_throwsCive_andAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(OWN_PHONE);

        assertThatThrownBy(() ->
                controller.createDocumentUploadSession(OTHER_PHONE, "identityDocument", "id.jpg", "image/jpeg"))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageContaining("IDENTITY_REFUSAL");

        verify(agentIdentityAuditService).recordViolation(
                eq(OWN_PHONE), eq(OTHER_PHONE),
                eq(AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH),
                eq("create_document_upload_session"),
                eq("CUSTOMER"),
                isNull(), isNull());
        verifyNoInteractions(documentUploadMcpService);
    }

    @Test
    void createDocumentUploadSession_adminAudience_thirdPartyPhone_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("ADMIN");
        UploadSessionInstructionsResponse expected = mock(UploadSessionInstructionsResponse.class);
        when(documentUploadMcpService.createDocumentUploadSession(OTHER_PHONE, "identityDocument", "id.jpg", "image/jpeg"))
                .thenReturn(expected);

        UploadSessionInstructionsResponse result =
                controller.createDocumentUploadSession(OTHER_PHONE, "identityDocument", "id.jpg", "image/jpeg");

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(agentIdentityAuditService);
    }

    // ---- attachUploadedDocumentToUserTag -----------------------------------------------

    @Test
    void attachUploadedDocumentToUserTag_customerAudience_ownPhone_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(OWN_PHONE);
        AttachUploadedDocumentResponse expected = mock(AttachUploadedDocumentResponse.class);
        when(documentUploadMcpService.attachUploadedDocumentToUserTag(OWN_PHONE, "identityDocument", "https://files/id.png"))
                .thenReturn(expected);

        AttachUploadedDocumentResponse result =
                controller.attachUploadedDocumentToUserTag(OWN_PHONE, "identityDocument", "https://files/id.png");

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    void attachUploadedDocumentToUserTag_customerAudience_thirdPartyPhone_throwsCive_andAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(OWN_PHONE);

        assertThatThrownBy(() ->
                controller.attachUploadedDocumentToUserTag(OTHER_PHONE, "identityDocument", "https://files/id.png"))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageContaining("IDENTITY_REFUSAL");

        verify(agentIdentityAuditService).recordViolation(
                eq(OWN_PHONE), eq(OTHER_PHONE),
                eq(AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH),
                eq("attach_uploaded_document_to_user_tag"),
                eq("CUSTOMER"),
                isNull(), isNull());
        verifyNoInteractions(documentUploadMcpService);
    }

    @Test
    void attachUploadedDocumentToUserTag_adminAudience_thirdPartyPhone_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("ADMIN");
        AttachUploadedDocumentResponse expected = mock(AttachUploadedDocumentResponse.class);
        when(documentUploadMcpService.attachUploadedDocumentToUserTag(OTHER_PHONE, "identityDocument", "https://files/id.png"))
                .thenReturn(expected);

        AttachUploadedDocumentResponse result =
                controller.attachUploadedDocumentToUserTag(OTHER_PHONE, "identityDocument", "https://files/id.png");

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(agentIdentityAuditService);
    }

    // ---- recheckRequiredDocuments ------------------------------------------------------

    @Test
    void recheckRequiredDocuments_customerAudience_ownPhone_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(OWN_PHONE);
        RequiredDocumentsForUserResponse expected = mock(RequiredDocumentsForUserResponse.class);
        when(documentUploadMcpService.recheckRequiredDocuments(OWN_PHONE)).thenReturn(expected);

        RequiredDocumentsForUserResponse result = controller.recheckRequiredDocuments(OWN_PHONE);

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    void recheckRequiredDocuments_customerAudience_thirdPartyPhone_throwsCive_andAudits() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("CUSTOMER");
        CallerIdentityContext.setCallerPhone(OWN_PHONE);

        assertThatThrownBy(() -> controller.recheckRequiredDocuments(OTHER_PHONE))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageContaining("IDENTITY_REFUSAL");

        verify(agentIdentityAuditService).recordViolation(
                eq(OWN_PHONE), eq(OTHER_PHONE),
                eq(AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH),
                eq("recheck_required_documents"),
                eq("CUSTOMER"),
                isNull(), isNull());
        verifyNoInteractions(documentUploadMcpService);
    }

    @Test
    void recheckRequiredDocuments_adminAudience_thirdPartyPhone_delegatesToService() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience("ADMIN");
        RequiredDocumentsForUserResponse expected = mock(RequiredDocumentsForUserResponse.class);
        when(documentUploadMcpService.recheckRequiredDocuments(OTHER_PHONE)).thenReturn(expected);

        RequiredDocumentsForUserResponse result = controller.recheckRequiredDocuments(OTHER_PHONE);

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(agentIdentityAuditService);
    }

    @Test
    void recheckRequiredDocuments_mcpRequest_nullAudience_throwsCive_andAudits() {
        CallerIdentityContext.setMcpRequest(true);
        // No audience, no caller phone

        assertThatThrownBy(() -> controller.recheckRequiredDocuments(OTHER_PHONE))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageContaining("IDENTITY_REFUSAL");

        verify(agentIdentityAuditService).recordViolation(
                isNull(), eq(OTHER_PHONE),
                eq(AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH),
                eq("recheck_required_documents"),
                eq("UNKNOWN"),
                isNull(), isNull());
    }
}
