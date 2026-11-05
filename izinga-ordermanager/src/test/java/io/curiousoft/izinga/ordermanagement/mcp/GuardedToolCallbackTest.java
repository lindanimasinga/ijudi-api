package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ADR-026 D2: Unit tests for {@link GuardedToolCallback} with 100% branch coverage.
 *
 * Tests verify:
 * - Both call() overloads delegate when isMcpRequest=true
 * - Both call() overloads throw CallerIdentityViolationException when isMcpRequest=false
 * - getToolDefinition() and getToolMetadata() delegate to the wrapped callback
 * - null delegate throws IllegalArgumentException at construction
 */
@ExtendWith(MockitoExtension.class)
class GuardedToolCallbackTest {

    @Mock private ToolCallback mockDelegate;
    @Mock private ToolDefinition mockDefinition;
    @Mock private ToolMetadata mockMetadata;
    @Mock private ToolContext mockToolContext;

    private static final String TOOL_NAME = "test_tool";
    private static final String JSON_INPUT = "{\"param\":\"value\"}";
    private static final String DELEGATE_RESULT = "delegate-result";

    @BeforeEach
    void setUp() {
        lenient().when(mockDelegate.getToolDefinition()).thenReturn(mockDefinition);
        lenient().when(mockDefinition.name()).thenReturn(TOOL_NAME);
        lenient().when(mockDelegate.getToolMetadata()).thenReturn(mockMetadata);
    }

    @AfterEach
    void clearContext() {
        StoreScopeContext.clear();
        CallerIdentityContext.clear();
    }

    // ---- Constructor ----

    @Test
    void constructor_nullDelegate_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new GuardedToolCallback(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    void constructor_validDelegate_succeeds() {
        GuardedToolCallback guard = new GuardedToolCallback(mockDelegate);
        assertThat(guard).isNotNull();
        assertThat(guard.getDelegate()).isSameAs(mockDelegate);
    }

    // ---- getToolDefinition ----

    @Test
    void getToolDefinition_returnsDelegateDefinition() {
        GuardedToolCallback guard = new GuardedToolCallback(mockDelegate);
        assertThat(guard.getToolDefinition()).isSameAs(mockDefinition);
        verify(mockDelegate).getToolDefinition();
    }

    // ---- getToolMetadata ----

    @Test
    void getToolMetadata_returnsDelegateMetadata() {
        GuardedToolCallback guard = new GuardedToolCallback(mockDelegate);
        assertThat(guard.getToolMetadata()).isSameAs(mockMetadata);
        verify(mockDelegate).getToolMetadata();
    }

    // ---- call(String) — MCP request thread ----

    @Test
    void call_withInput_onMcpThread_delegatesAndReturnsResult() {
        CallerIdentityContext.setMcpRequest(true);
        when(mockDelegate.call(JSON_INPUT)).thenReturn(DELEGATE_RESULT);

        GuardedToolCallback guard = new GuardedToolCallback(mockDelegate);
        String result = guard.call(JSON_INPUT);

        assertThat(result).isEqualTo(DELEGATE_RESULT);
        verify(mockDelegate).call(JSON_INPUT);
    }

    @Test
    void call_withInput_onMcpThread_delegateThrows_exceptionPropagates() {
        CallerIdentityContext.setMcpRequest(true);
        RuntimeException cause = new RuntimeException("service error");
        when(mockDelegate.call(JSON_INPUT)).thenThrow(cause);

        GuardedToolCallback guard = new GuardedToolCallback(mockDelegate);
        assertThatThrownBy(() -> guard.call(JSON_INPUT))
                .isSameAs(cause);
    }

    // ---- call(String) — NOT on MCP request thread ----

    @Test
    void call_withInput_notOnMcpThread_throwsCallerIdentityViolation() {
        // isMcpRequest defaults to false (ThreadLocal not set)
        GuardedToolCallback guard = new GuardedToolCallback(mockDelegate);

        assertThatThrownBy(() -> guard.call(JSON_INPUT))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageStartingWith("IDENTITY_REFUSAL:");

        // Delegate must NOT have been called
        verify(mockDelegate, never()).call(any());
    }

    @Test
    void call_withInput_mcpRequestFalse_throwsCallerIdentityViolation() {
        CallerIdentityContext.setMcpRequest(false);
        GuardedToolCallback guard = new GuardedToolCallback(mockDelegate);

        assertThatThrownBy(() -> guard.call(JSON_INPUT))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessage(McpRefusal.NOT_MCP_REQUEST);

        verify(mockDelegate, never()).call(any());
    }

    // ---- call(String, ToolContext) — MCP request thread ----

    @Test
    void callWithContext_onMcpThread_delegatesAndReturnsResult() {
        CallerIdentityContext.setMcpRequest(true);
        when(mockDelegate.call(JSON_INPUT, mockToolContext)).thenReturn(DELEGATE_RESULT);

        GuardedToolCallback guard = new GuardedToolCallback(mockDelegate);
        String result = guard.call(JSON_INPUT, mockToolContext);

        assertThat(result).isEqualTo(DELEGATE_RESULT);
        verify(mockDelegate).call(JSON_INPUT, mockToolContext);
    }

    // ---- call(String, ToolContext) — NOT on MCP request thread ----

    @Test
    void callWithContext_notOnMcpThread_throwsCallerIdentityViolation() {
        GuardedToolCallback guard = new GuardedToolCallback(mockDelegate);

        assertThatThrownBy(() -> guard.call(JSON_INPUT, mockToolContext))
                .isInstanceOf(CallerIdentityViolationException.class)
                .hasMessageStartingWith("IDENTITY_REFUSAL:");

        verify(mockDelegate, never()).call(any(), any());
    }

    // ---- McpRefusal.NOT_MCP_REQUEST is preserved ----

    @Test
    void notOnMcpThread_messageEqualsNotMcpRequestConstant() {
        GuardedToolCallback guard = new GuardedToolCallback(mockDelegate);

        CallerIdentityViolationException thrown = catchThrowableOfType(
                () -> guard.call(JSON_INPUT), CallerIdentityViolationException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo(McpRefusal.NOT_MCP_REQUEST);
    }

    // ---- toString of delegate definition used in log ----

    @Test
    void call_notOnMcpThread_delegateDefinitionIsNull_doesNotNullPointer() {
        when(mockDelegate.getToolDefinition()).thenReturn(null);
        GuardedToolCallback guard = new GuardedToolCallback(mockDelegate);

        assertThatThrownBy(() -> guard.call(JSON_INPUT))
                .isInstanceOf(CallerIdentityViolationException.class);
    }
}
