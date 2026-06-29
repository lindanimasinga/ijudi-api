---
name: meta-templates-developer
description: "Use when creating, updating, listing, and sending WhatsApp Business templates through Meta Graph API using curl. Focus on utility templates, payload correctness, and repeatable command workflows."
argument-hint: "Goal (create/update/list/send), template category, language code, and payload details"
---

# Meta Templates Developer

## Outcome
Use this skill to safely manage WhatsApp Business templates with curl commands.

This skill helps you:
- Create utility templates via Meta Graph API.
- Update existing templates with correct payload shapes.
- List and verify templates and approval status.
- Send approved templates to recipients.
- Keep commands portable and environment driven.

## When To Use
Use this skill for:
- WhatsApp template creation and update tasks.
- Utility template workflows (order confirmations, status updates, reminders).
- Generating and validating curl commands.
- Troubleshooting Graph API template request errors.

Do not use this skill for:
- Frontend or UI development tasks.
- Non-WhatsApp Meta APIs unrelated to templates.

## Required Environment Variables
Set these before running any command:

```bash
export WA_TOKEN="<META_SYSTEM_USER_OR_BUSINESS_TOKEN>"
export WA_WABA_ID="<WHATSAPP_BUSINESS_ACCOUNT_ID>"
export WA_PHONE_ID="<WHATSAPP_BUSINESS_PHONE_NUMBER_ID>"
export WA_GRAPH_VERSION="v25.0"
```

Default version if missing:

```bash
WA_GRAPH_VERSION="${WA_GRAPH_VERSION:-v25.0}"
```

## Core API Endpoints
- Create template: `POST /{WABA_ID}/message_templates`
- Update template: `POST /{TEMPLATE_ID}`
- List templates: `GET /{WABA_ID}/message_templates`
- Send message: `POST /{PHONE_ID}/messages`

Base URL pattern:

```bash
https://graph.facebook.com/${WA_GRAPH_VERSION}
```

## Standard Curl Patterns

### 1. Create Utility Template
```bash
curl -sS -X POST "https://graph.facebook.com/${WA_GRAPH_VERSION}/${WA_WABA_ID}/message_templates" \
  -H "Authorization: Bearer ${WA_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @template-create.json
```

### 2. Update Existing Template
```bash
curl -sS -X POST "https://graph.facebook.com/${WA_GRAPH_VERSION}/${TEMPLATE_ID}" \
  -H "Authorization: Bearer ${WA_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @template-update.json
```

### 3. List Templates
```bash
curl -sS -X GET "https://graph.facebook.com/${WA_GRAPH_VERSION}/${WA_WABA_ID}/message_templates?limit=200" \
  -H "Authorization: Bearer ${WA_TOKEN}"
```

### 4. Send Approved Template
```bash
curl -sS -X POST "https://graph.facebook.com/${WA_GRAPH_VERSION}/${WA_PHONE_ID}/messages" \
  -H "Authorization: Bearer ${WA_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @template-send.json
```

## Utility Template Rules
- Use category `UTILITY` for transactional updates.
- Avoid marketing copy in utility templates.
- Match template language code exactly (`en_US`, etc.).
- Match components and parameter structure exactly with approved template.
- If using named parameters, set `parameter_format` to `named` when creating.

## Payload Examples

### Utility Template Create (Named Params)
```json
{
  "name": "order_update_utility_v1",
  "language": "en_US",
  "category": "UTILITY",
  "parameter_format": "named",
  "components": [
    {
      "type": "BODY",
      "text": "Hi {{customer_name}}, your order {{order_id}} is now {{status}}.",
      "example": {
        "body_text_named_params": [
          { "param_name": "customer_name", "example": "Linda" },
          { "param_name": "order_id", "example": "ORD12345" },
          { "param_name": "status", "example": "ready for pickup" }
        ]
      }
    }
  ]
}
```

### Send Approved Template
```json
{
  "messaging_product": "whatsapp",
  "recipient_type": "individual",
  "to": "27826401157",
  "type": "template",
  "template": {
    "name": "order_update_utility_v1",
    "language": { "code": "en_US" },
    "components": [
      {
        "type": "body",
        "parameters": [
          { "type": "text", "parameter_name": "customer_name", "text": "Linda" },
          { "type": "text", "parameter_name": "order_id", "text": "ORD12345" },
          { "type": "text", "parameter_name": "status", "text": "ready for pickup" }
        ]
      }
    ]
  }
}
```

## Recommended Workflow
1. Validate required environment variables are set.
2. Create the template from JSON file.
3. Poll/list templates until status is `APPROVED`.
4. Send template with matching language and parameter names.
5. Capture API response IDs for audit/troubleshooting.

## Error Handling Playbook
- OAuth/token errors: refresh token and confirm app permissions.
- Invalid parameter errors: compare component type and parameter names with approved template.
- Category mismatch: remove promotional wording from utility templates.
- Template not approved: wait for approval before send.
- Recipient format issues: send phone in international format without spaces.

## MCP Agent Guidance
When asked to automate template operations:
- Prefer generating curl commands and JSON payload files first.
- Keep requests deterministic and explicit.
- Return command output plus a short interpretation.
- Do not hardcode secrets in scripts or source files.

## Completion Criteria
Task is complete only when:
- Curl command is generated or executed for requested operation.
- Payload conforms to Meta template requirements.
- Response is captured and status/result is clearly reported.
- Any required next action is identified (for example waiting for approval).
