package io.curiousoft.izinga.commons.security

/**
 * ADR-024: Thrown when an MCP tool is invoked with a phone/id that does not
 * belong to the authenticated caller represented by the scope JWT.
 *
 * The message text is intentionally human-readable and machine-parseable:
 * the [IDENTITY_REFUSAL:] prefix allows log-based alerting to detect
 * and count refusals without parsing free text.  OpenAI relays this text to
 * the WhatsApp caller — do NOT change the wording without a product/legal review.
 */
class CallerIdentityViolationException(message: String) : RuntimeException(message)
