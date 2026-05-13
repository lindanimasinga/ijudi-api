---
name: izinga-backend-developer
description: "Use when working on the iZinga backend in ijudi-api: Java 17 and Kotlin on Spring Boot 3, Spring Web REST APIs, JWT/OAuth2 resource server, MongoDB with Spring Data Mongo, Spring AI MCP server tools, and domain flows for users, stores, orders, drivers, WhatsApp/push messaging, store menus, Yoco payments, and recon payouts. Includes architecture-safe workflows for scanning modules, fixing bugs, and adding features without breaking existing contracts."
argument-hint: "Backend task goal, affected domain, and target module"
---

# iZinga Backend Developer Skill

## Outcome
Use this skill to make safe, architecture-aligned backend changes in ijudi-api.

This skill helps you:
- Map the correct module before editing.
- Preserve API contracts and shared models.
- Implement features and bug fixes with minimal cross-module risk.
- Validate with focused checks before finishing.

## When To Use
Use this skill for:
- Kotlin and Java 17 Spring Boot 3 changes.
- Spring Web controllers, services, validation, JSON payload changes.
- Spring Data Mongo repositories and query logic.
- JWT and OAuth2 resource server behavior.
- MCP tool additions and MCP server debugging.
- iZinga domain work: order management, users, stores, user roles, store type, WhatsApp, push notifications, order stages, drivers, store menu sync, Yoco payment, recon payouts.

Do not use this skill for:
- Frontend Angular work.
- Large schema redesign without migration planning.

## Project Architecture Snapshot
Repository root is multi-module Maven with Spring Boot parent.

Core modules and responsibilities:
- izinga-ordermanager:
  - Runtime API host and main integration hub.
  - Entry point: src/main/java/io/curiousoft/izinga/ordermanagement/IjudiApplication.java
  - Domain APIs: order, store, device, promotion, restricted regions, shopping-list.
  - Security and JWT/OAuth2 resource server config.
  - MCP server wiring via McpConfig.
- izinga-commons:
  - Shared models, enums, repositories, and validators.
  - MongoDB shared entities and reusable data contracts.
- izinga-usermanagement:
  - User profile CRUD, pending approvals, user config, wallet pass.
  - User config templates are stored in `userTypeConfig` and now support role mapping via `UserConfig.userRole`.
  - MCP tools for user lookup/create/location-based search.
- izinga-recon:
  - Payout and payout bundles (shop and messenger), CSV exports, bank config.
  - MCP tool for payouts lookup.
- izinga-messaging:
  - WhatsApp webhook and forwarding, Firebase push and Firestore integration, SMS support, AI agent config classes.
- izinga-yoco-pay:
  - Yoco payment initiation, webhook finalization, refunds.
- izinga-documentmanagement:
  - Document upload/delete and S3 integration.
- izinga-qrcode-generator:
  - Tip QR and promo code management.
- store-menu-to-izinga-menu:
  - Store menu ingestion/update utility (AWS/serverless style integration module).

## Key Domain Contracts
Shared enums and models are in izinga-commons and must be treated as stable contracts:
- Profile roles: CUSTOMER, STORE_ADMIN, STORE, MESSENGER, MESSENGER_ADMIN, ADMIN.
- Store type: FOOD, CLOTHING, SALON, CAR_WASH, MOVERS, TIPS, LICENSING, PARTS.
- Order stages: STAGE_0_CUSTOMER_NOT_PAID through STAGE_7_ALL_PAID, CANCELLED.

Messenger admin ownership contract:
- Messenger assignment is resolved via `UserProfile.tag["messengerAdminId"]`.
- Team-scoped reads must only return messengers/orders/payouts that map to the authenticated messenger admin.

Primary API surfaces:
- /order, /store, /user, /user-config, /recon, /document, /yoco/payment, /whatsapp/webhook.

User config contract:
- `UserConfig` documents are keyed by `name` and patched via `/user-config/{userType}`.
- `UserConfig.userRole` is an additive optional `ProfileRoles` mapping and must be preserved on update paths.
- `UserConfigService.update` keeps the persisted key (`name`) and updates label/mandatoryFields/optionalFields/userRole.

Recent additive query surfaces:
- `/user?role=MESSENGER&messengerAdminId=<id>` for messenger admin team lookup.
- `/order?messengerAdminId=<id>&allStages=<bool>` for team orders.
- `/recon/payout?payoutType=MESSENGER&messengerAdminId=<id>&fromDate=<iso>&toDate=<iso>&messengerId=<optional>` for team payouts and drill-down.

## Standard Workflow

### 1. Understand The Request And Domain
- Identify business domain first: users, stores, orders, payments, messaging, recon, docs, QR.
- Identify whether change is API behavior, model shape, persistence, security, or integration.

### 2. Map To The Correct Module
- Start from controller location and trace to service and repository.
- If a model is in izinga-commons, treat it as cross-module impact.

### 3. Contract Impact Check (Required)
Before editing, answer:
- Will request/response JSON change?
- Will enum values or stage transitions change?
- Will repository query semantics change?
- Will security requirements change for /v2 APIs or MCP endpoints?

If yes, apply backward-compatible change where possible:
- Prefer additive fields over renames/removals.
- Keep endpoint paths and HTTP methods stable unless explicitly requested.
- Preserve existing query parameter behavior.

### 4. Implement Minimal Safe Change
- Follow current style in that module (Java or Kotlin).
- Keep logic in service layer, not controller-heavy.
- Reuse shared validators/utilities where available.
- For Mongo changes, preserve repository method naming conventions and index assumptions.

### 5. Domain Decision Points

User management:
- If lookup is by phone, normalize SA numbers consistently (0, +27, 27 patterns).
- If create/update user, preserve role-based behavior and profile approval flow.
- For messenger admin features, use additive filters and resolve team members by `tag.messengerAdminId`.
- For `/user-config`, keep `name` as the stable id and treat `userRole` as additive role metadata (do not rename/remove existing user types).

Store management:
- Keep owner role/store linkage and markup behavior intact.
- Respect location/range filtering semantics.

Order management:
- Preserve stage transition rules and cancellation constraints.
- Keep payment reversal and notification side effects intact.
- Handle quote acceptance without bypassing sanity checks.
- Preserve existing `messengerId` query behavior while adding `messengerAdminId` team scope.

Payments and recon:
- Yoco webhook signature validation must stay strict.
- Recon payouts should remain separated by payout type (SHOP or MESSENGER).
- For `MESSENGER` payouts with `messengerAdminId`, aggregate only managed messenger payout rows.

Messaging:
- WhatsApp webhook verification and payload handling must preserve event publishing behavior.
- Push and Firestore integrations should fail safely and log clearly.

MCP server:
- New MCP capabilities should be exposed via @Tool methods in service classes.
- Ensure service is registered through McpConfig toolObjects.
- Keep tool names descriptive and stable once published.

### 6. Validation Checklist
Run focused validation before completion:
- Compile affected module and dependencies:
  - ./mvnw -pl <module> -am test
- For API changes, check controller mappings and serialization.
- For security changes, confirm protected vs public endpoint behavior.
- For MCP changes:
  - confirm /sse and /mcp/message reachability
  - verify new tool appears and executes.
- For messenger admin scope:
  - confirm cross-admin access is denied.
  - confirm non-admin role cannot query team endpoints.
  - confirm existing single-user query paths remain unchanged.

### 7. Completion Criteria
Task is complete only when:
- Correct module and layers were updated.
- No accidental contract breaks on shared JSON or enums.
- Tests or compile checks pass for affected modules.
- Any new endpoint/tool is documented in code comments or module docs.

## Bug Fix Playbook
1. Reproduce from controller input to service output.
2. Locate failing branch and domain invariant violated.
3. Patch smallest change in service/repo/model layer.
4. Add or update targeted test.
5. Re-run module checks.
6. Verify no cross-module regression in shared contracts.

## Feature Playbook
1. Confirm existing endpoint can be extended additively.
2. Add DTO/field/logic with backward compatibility.
3. Keep persistence and validation aligned.
4. Add role/security checks if needed.
5. Add MCP tool exposure only if feature is intended for AI tools.
6. Validate with module tests and focused endpoint checks.

## Known Architectural Guardrails
- Keep shared domain models in izinga-commons authoritative.
- Avoid duplicating business rules across modules.
- Avoid putting infrastructure logic in controllers.
- Do not bypass security config for v2 protected APIs.
- Do not hardcode secrets in code.
- Never return team-scoped data without ownership checks (`messengerAdminId` must map to caller's scope).

## Recommended Prompt Patterns
- "Fix order stage transition bug in ordermanager without changing response schema."
- "Add an additive field to user profile and update user endpoints safely."
- "Expose store lookup as MCP tool and wire it through McpConfig."
- "Add recon payout filter by date and keep SHOP or MESSENGER behavior intact."
- "Add messenger admin team order query without breaking existing messengerId endpoint behavior."
- "Implement messengerAdminId scoped payouts with ownership validation and tests."

## Quick Start Commands
- Full build:
  - ./mvnw clean install -DskipTests
- Targeted module build with dependencies:
  - ./mvnw -pl izinga-ordermanager -am test
  - ./mvnw -pl izinga-usermanagement -am test
  - ./mvnw -pl izinga-recon -am test

## File Structure Landmarks
- Parent modules list: pom.xml
- Security: izinga-ordermanager/src/main/java/io/curiousoft/izinga/ordermanagement/security
- MCP config: izinga-ordermanager/src/main/java/io/curiousoft/izinga/ordermanagement/McpConfig.java
- Shared domain and repos: izinga-commons/src/main/kotlin/io/curiousoft/izinga/commons
- User APIs: izinga-usermanagement/src/main/kotlin/io/curiousoft/izinga/usermanagement
- Recon and payouts: izinga-recon/src/main/kotlin/io/curiousoft/izinga/recon
- Messaging and WhatsApp: izinga-messaging/src/main/java/io/curiousoft/izinga/messaging
- Payments: izinga-yoco-pay/src/main/kotlin/io/curiousoft/izinga/yocopay
