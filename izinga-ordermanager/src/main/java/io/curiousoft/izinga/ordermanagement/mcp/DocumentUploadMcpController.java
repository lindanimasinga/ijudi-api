package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeValidator;
import io.curiousoft.izinga.usermanagement.users.AttachUploadedDocumentResponse;
import io.curiousoft.izinga.usermanagement.users.DocumentUploadMcpService;
import io.curiousoft.izinga.usermanagement.users.RequiredDocumentsForUserResponse;
import io.curiousoft.izinga.usermanagement.users.UploadSessionInstructionsResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * ADR-026 Phase 1 MCP controller for document-upload tools.
 *
 * <p>Owns the four document tools extracted from {@code DocumentUploadMcpService}.
 * The controller-layer concern (caller-phone identity enforcement) is applied here
 * before delegating to the service layer.</p>
 *
 * <h3>R5 compliance</h3>
 * <p>This class depends only on {@link DocumentUploadMcpService} (a service) and
 * {@link AgentIdentityAuditService}. It never depends on any {@code *Repo} or
 * {@code *Repository} type.</p>
 */
@McpToolController
public class DocumentUploadMcpController {

    // ---- Collaborators ------------------------------------------------------------------

    private final DocumentUploadMcpService documentUploadMcpService;
    private final AgentIdentityAuditService agentIdentityAuditService;

    public DocumentUploadMcpController(DocumentUploadMcpService documentUploadMcpService,
                                        AgentIdentityAuditService agentIdentityAuditService) {
        this.documentUploadMcpService = documentUploadMcpService;
        this.agentIdentityAuditService = agentIdentityAuditService;
    }

    // ---- Tool methods -------------------------------------------------------------------

    @McpAudience({Audience.DRIVER, Audience.CUSTOMER, Audience.STORE, Audience.ADMIN, Audience.OTP})
    @Tool(
        name = "get_required_documents_for_user",
        description = "Returns required document fields and missing fields for the user linked to a phone number."
    )
    public RequiredDocumentsForUserResponse getRequiredDocumentsForUser(
            @ToolParam(description = "User phone number, for example +27812815577") String phone) {
        enforceCallerPhoneOrAudit(phone, "get_required_documents_for_user");
        return documentUploadMcpService.getRequiredDocumentsForUser(phone);
    }

    @McpAudience({Audience.DRIVER, Audience.CUSTOMER, Audience.STORE, Audience.ADMIN, Audience.OTP})
    @Tool(
        name = "create_document_upload_session",
        description = "Creates upload instructions for a required document field before linking it to a user profile."
    )
    public UploadSessionInstructionsResponse createDocumentUploadSession(
            @ToolParam(description = "User phone number, for example +27812815577") String phone,
            @ToolParam(description = "Document field name from user config, for example identityDocument") String fieldName,
            @ToolParam(description = "Original file name, for example id-card.jpg") String originalFileName,
            @ToolParam(description = "MIME type, for example image/jpeg or application/pdf") String mimeType) {
        enforceCallerPhoneOrAudit(phone, "create_document_upload_session");
        return documentUploadMcpService.createDocumentUploadSession(phone, fieldName, originalFileName, mimeType);
    }

    @McpAudience({Audience.DRIVER, Audience.CUSTOMER, Audience.STORE, Audience.ADMIN, Audience.OTP})
    @Tool(
        name = "attach_uploaded_document_to_user_tag",
        description = "Attaches an uploaded document URL to user.tag[fieldName], then returns remaining missing fields."
    )
    public AttachUploadedDocumentResponse attachUploadedDocumentToUserTag(
            @ToolParam(description = "User phone number, for example +27812815577") String phone,
            @ToolParam(description = "Document field name from user config, for example identityDocument") String fieldName,
            @ToolParam(description = "Uploaded file public URL") String fileUrl) {
        enforceCallerPhoneOrAudit(phone, "attach_uploaded_document_to_user_tag");
        return documentUploadMcpService.attachUploadedDocumentToUserTag(phone, fieldName, fileUrl);
    }

    @McpAudience({Audience.DRIVER, Audience.CUSTOMER, Audience.STORE, Audience.ADMIN, Audience.OTP})
    @Tool(
        name = "recheck_required_documents",
        description = "Rechecks required fields and required document fields after uploads."
    )
    public RequiredDocumentsForUserResponse recheckRequiredDocuments(
            @ToolParam(description = "User phone number, for example +27812815577") String phone) {
        enforceCallerPhoneOrAudit(phone, "recheck_required_documents");
        return documentUploadMcpService.recheckRequiredDocuments(phone);
    }

    // ---- Private helpers ----------------------------------------------------------------

    /**
     * ADR-026: Validates that the supplied phone belongs to the JWT caller.
     * Writes an async audit record on violation and re-throws so the tool method
     * surfaces the refusal message to the AI agent.
     *
     * <p>FIX-04: audience is captured here on the REQUEST thread before the {@literal @}Async dispatch.</p>
     */
    private void enforceCallerPhoneOrAudit(String phone, String toolName) {
        try {
            StoreScopeValidator.validateCallerPhone(phone);
        } catch (CallerIdentityViolationException e) {
            // FIX-04: capture audience on the request thread before the @Async audit dispatch
            String auditAudience = CallerIdentityContext.getAudience() != null
                ? CallerIdentityContext.getAudience() : "UNKNOWN";
            agentIdentityAuditService.recordViolation(
                CallerIdentityContext.getCallerPhone(), phone,
                AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH, toolName,
                auditAudience, null, null);
            throw e;
        }
    }
}
