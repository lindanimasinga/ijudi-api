package io.curiousoft.izinga.commons.security

import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog.ViolationType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * SEC-MCP-01 / SEC-MCP-02 / AMD-01: Unit tests for [CallerIdentityValidator].
 *
 * Migrated from JUnit 4 to JUnit 5 (Jupiter) so surefire picks them up —
 * izinga-commons has no junit-vintage-engine and JUnit 4 tests were silently skipped.
 *
 * Covers:
 *   - validateCallerPhone: null-audience hard reject on MCP path (SEC-MCP-01)
 *   - validateCallerPhone: null-audience pass-through on non-MCP path (REST regression)
 *   - validateCallerPhone: phone mismatch enforcement for CUSTOMER / DRIVER
 *   - validateCallerOwnsId: null-audience hard reject on MCP path (SEC-MCP-01)
 *   - validateCallerOwnsId: null-audience pass-through on non-MCP path (REST regression)
 *   - validateMcpAudience: null audience hard reject, non-permitted audience reject, permitted passes, non-MCP no-op
 *   - isMcpRequest / setMcpRequest / clear in CallerIdentityContext
 *   - AMD-01 (REQ-A10): ADMIN audience passes validateCallerPhone and validateCallerOwnsId on MCP
 *   - AMD-01 (REQ-A11): null audience on MCP still hard-rejects even with ADMIN tests present
 */
class CallerIdentityValidatorTest {

    @BeforeEach
    fun before() = CallerIdentityContext.clear()

    @AfterEach
    fun after() = CallerIdentityContext.clear()

    // ── CallerIdentityContext flag ──────────────────────────────────────────

    @Test
    fun `isMcpRequest returns false when not set`() {
        assertFalse(CallerIdentityContext.isMcpRequest())
    }

    @Test
    fun `isMcpRequest returns true after setMcpRequest(true)`() {
        CallerIdentityContext.setMcpRequest(true)
        assertTrue(CallerIdentityContext.isMcpRequest())
    }

    @Test
    fun `clear removes IS_MCP_REQUEST flag`() {
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.clear()
        assertFalse(CallerIdentityContext.isMcpRequest())
    }

    // ── validateCallerPhone: MCP path, null audience → HARD REJECT ──────────

    @Test
    fun `validateCallerPhone throws on MCP request with null audience`() {
        CallerIdentityContext.setMcpRequest(true)
        // audience is null (no scope token)
        assertThrows<CallerIdentityViolationException> {
            CallerIdentityValidator.validateCallerPhone("+27812345678")
        }
    }

    @Test
    fun `validateCallerPhone MCP rejection message contains IDENTITY_REFUSAL`() {
        CallerIdentityContext.setMcpRequest(true)
        val ex = assertThrows<CallerIdentityViolationException> {
            CallerIdentityValidator.validateCallerPhone("+27812345678")
        }
        assertTrue(ex.message!!.startsWith("IDENTITY_REFUSAL"), "Message must start with IDENTITY_REFUSAL")
    }

    // ── validateCallerPhone: non-MCP path, null audience → pass-through ────

    @Test
    fun `validateCallerPhone passes on non-MCP request with null audience (REST regression)`() {
        // IS_MCP_REQUEST not set → isMcpRequest() == false
        // audience == null → existing guard: not CUSTOMER/DRIVER → return
        CallerIdentityValidator.validateCallerPhone("+27812345678") // must not throw
    }

    // ── validateCallerPhone: CUSTOMER audience ──────────────────────────────

    @Test
    fun `validateCallerPhone CUSTOMER audience phone mismatch throws`() {
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("CUSTOMER")
        CallerIdentityContext.setCallerPhone("+27811111111")
        assertThrows<CallerIdentityViolationException> {
            CallerIdentityValidator.validateCallerPhone("+27822222222")
        }
    }

    @Test
    fun `validateCallerPhone CUSTOMER audience matching phone passes`() {
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("CUSTOMER")
        CallerIdentityContext.setCallerPhone("+27812345678")
        CallerIdentityValidator.validateCallerPhone("+27812345678") // must not throw
    }

    @Test
    fun `validateCallerPhone CUSTOMER audience null callerPhone throws`() {
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("CUSTOMER")
        // callerPhone not set → null
        assertThrows<CallerIdentityViolationException> {
            CallerIdentityValidator.validateCallerPhone("+27812345678")
        }
    }

    // ── validateCallerPhone: STORE audience → no-op ─────────────────────────

    @Test
    fun `validateCallerPhone STORE audience is a no-op`() {
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("STORE")
        // no callerPhone set — must not throw
        CallerIdentityValidator.validateCallerPhone("+27899999999")
    }

    // ── validateCallerOwnsId: MCP path, null audience → HARD REJECT ─────────

    @Test
    fun `validateCallerOwnsId throws on MCP request with null audience`() {
        CallerIdentityContext.setMcpRequest(true)
        assertThrows<CallerIdentityViolationException> {
            CallerIdentityValidator.validateCallerOwnsId("some-id") { "some-id" }
        }
    }

    @Test
    fun `validateCallerOwnsId MCP rejection message contains IDENTITY_REFUSAL`() {
        CallerIdentityContext.setMcpRequest(true)
        val ex = assertThrows<CallerIdentityViolationException> {
            CallerIdentityValidator.validateCallerOwnsId("some-id") { "some-id" }
        }
        assertTrue(ex.message!!.startsWith("IDENTITY_REFUSAL"))
    }

    // ── validateCallerOwnsId: non-MCP path, null audience → pass-through ────

    @Test
    fun `validateCallerOwnsId passes on non-MCP request with null audience (REST regression)`() {
        // IS_MCP_REQUEST not set → pass-through
        CallerIdentityValidator.validateCallerOwnsId("some-id") { "some-id" } // must not throw
    }

    // ── validateMcpAudience ──────────────────────────────────────────────────

    @Test
    fun `validateMcpAudience is no-op when not an MCP request`() {
        // IS_MCP_REQUEST not set — even with null audience, must not throw
        CallerIdentityValidator.validateMcpAudience("STORE")
    }

    @Test
    fun `validateMcpAudience throws on MCP request with null audience`() {
        CallerIdentityContext.setMcpRequest(true)
        assertThrows<CallerIdentityViolationException> {
            CallerIdentityValidator.validateMcpAudience("STORE")
        }
    }

    @Test
    fun `validateMcpAudience throws on MCP request with non-permitted audience`() {
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("CUSTOMER")
        assertThrows<CallerIdentityViolationException> {
            CallerIdentityValidator.validateMcpAudience("STORE")
        }
    }

    @Test
    fun `validateMcpAudience passes on MCP request with permitted audience`() {
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("STORE")
        CallerIdentityValidator.validateMcpAudience("STORE", "DRIVER") // must not throw
    }

    @Test
    fun `validateMcpAudience passes with multiple permitted audiences`() {
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("DRIVER")
        CallerIdentityValidator.validateMcpAudience("CUSTOMER", "DRIVER", "STORE") // must not throw
    }

    // ── normalizeLast9 (unchanged, regression) ──────────────────────────────

    @Test
    fun `normalizeLast9 returns last 9 digits`() {
        assertEquals("812345678", CallerIdentityValidator.normalizeLast9("+27812345678"))
    }

    @Test
    fun `normalizeLast9 null returns null`() {
        assertNull(CallerIdentityValidator.normalizeLast9(null))
    }

    @Test
    fun `normalizeLast9 short phone returns null`() {
        assertNull(CallerIdentityValidator.normalizeLast9("1234567"))
    }

    // ── AMD-01 (REQ-A10): ADMIN audience passes validators on MCP path ────────

    @Test
    fun `validateCallerPhone adminAudience mcpRequest passes`() {
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("ADMIN")
        // no callerPhone set — ADMIN must pass without phone
        CallerIdentityValidator.validateCallerPhone("+27812345678") // must not throw
    }

    @Test
    fun `validateCallerPhone nullAudience mcpRequest stillRejects`() {
        // REQ-A11: null audience is still rejected even after ADMIN support was added
        CallerIdentityContext.setMcpRequest(true)
        // audience is null
        val ex = assertThrows<CallerIdentityViolationException> {
            CallerIdentityValidator.validateCallerPhone("+27812345678")
        }
        assertTrue(ex.message!!.startsWith("IDENTITY_REFUSAL"))
    }

    @Test
    fun `validateCallerOwnsId adminAudience mcpRequest passes`() {
        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("ADMIN")
        // resolver returns null — ADMIN must not trigger id resolution
        CallerIdentityValidator.validateCallerOwnsId("some-id") { null } // must not throw
    }

    @Test
    fun `validateCallerOwnsId nullAudience mcpRequest stillRejects`() {
        // REQ-A11: null audience is still rejected even after ADMIN support was added
        CallerIdentityContext.setMcpRequest(true)
        // audience is null
        val ex = assertThrows<CallerIdentityViolationException> {
            CallerIdentityValidator.validateCallerOwnsId("some-id") { "some-id" }
        }
        assertTrue(ex.message!!.startsWith("IDENTITY_REFUSAL"))
    }

    // ── AUDIENCE_VIOLATION ViolationType (new enum constant) ────────────────

    @Test
    fun `AUDIENCE_VIOLATION ViolationType exists`() {
        val type = ViolationType.AUDIENCE_VIOLATION
        assertNotNull(type)
        assertEquals("AUDIENCE_VIOLATION", type.name)
    }
}
