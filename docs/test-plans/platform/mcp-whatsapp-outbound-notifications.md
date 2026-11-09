---
userType: platform (MCP / AI agent surface)
Feature: MCP WhatsApp outbound notification tools — ADR-029; two new tools (send_landing_options_notification, send_shop_options_notification); @McpPublicTool annotation; ArchUnit R7; FIX-1/2/3/4/5 fix round (DEF-1 DataAccessException, AC-09 boolean truthfulness, SEC-02 false Javadoc, SEC-03 frozen allow-list ArchTest, SEC-05 phone masking)
App: izinga-ordermanager + izinga-messaging (NotificationMcpController, WhatsappNotificationService, McpPublicTool, GuardedToolCallback, McpArchitectureTest, NotificationMcpControllerTest, WhatsappNotificationServiceTest)
Branch: feature/mcp-whatsapp-outbound-notifications
Last verified: 2026-11-07 | Full reactor ./mvnw clean test | PASS (see Validation Commands)
---

## Scope

Covers:

- **Tool 1 (`send_landing_options_notification`)**: authenticated tool, SEC-MCP-01 null-audience hard reject, delegation to `WhatsappNotificationService.sendLandingOptions(mobileNumber, name, null, audience, storeId)`, all 5 audience ALLOW paths (CUSTOMER, DRIVER, STORE, ADMIN, OTP), audit violation recorded on null-audience DENY.
- **Tool 2 (`send_shop_options_notification`)**: `@McpPublicTool` — no identity gate; tokenless callers allowed; delegates to `WhatsappNotificationService.sendShopOptions(mobileNumber, name)`; boolean return: `true` → success string; `false` → degraded-outcome string (AC-09 truthfulness).
- **`WhatsappNotificationService.sendShopOptions`**: null/blank mobileNumber guard (returns `false` early); phone normalisation (`0` → `+27`); `izinga_shop_options` template construction; `catch (Exception)` absorbs both `IOException` and unchecked `DataAccessException` from `WhatsappSenderResolver.resolve()` (DEF-1 fix); returns `boolean`; phone logged at DEBUG masked to last 4 digits (SEC-05 fix).
- **`@McpPublicTool` annotation**: documentation-only marker + ArchUnit R7 exemption; `GuardedToolCallback` does NOT read it at runtime.
- **ArchUnit R7**: `toolMethodsMustHaveIdentityGateOrBePublicTool` — asserts all `@Tool` methods on `@McpToolController` classes either call an identity gate or carry `@McpPublicTool`.
- **SEC-03 frozen allow-list ArchTest**: `sec03_frozenPublicToolAllowList_onlySendShopOptionsNotificationIsPublic` — asserts exactly `{"send_shop_options_notification"}` carries both `@Tool` and `@McpPublicTool`; fires on any new public tool.
- **False Javadoc sweep (FIX-3)**: 5 locations corrected — `NotificationMcpController`, `McpPublicTool`, `WhatsappNotificationService`, `ADR-029`.

Out of scope: live WhatsApp template approval (requires Meta Business Manager access); rate limiting / per-number cooldown (SEC-04 deferred — see ADR-029); POPIA opt-in check for Tool 2 (open question deferred — see ADR-029).

## Repos affected

| Repo | Work |
|---|---|
| ijudi-api / izinga-ordermanager | `NotificationMcpController` (new controller, 2 tools), `McpPublicTool` (new annotation), `McpConfig` (NotificationMcpController bean wired), `McpArchitectureTest` (R7 + SEC-03 frozen allow-list + count updates), `NotificationMcpControllerTest` (9 unit tests), `McpToolContractSnapshotTest` (count 17→19, new tool names), `McpAccessMatrixTest` (12 new matrix rows) |
| ijudi-api / izinga-messaging | `WhatsappNotificationService` (`sendShopOptions` new method: boolean return, null guard, catch Exception, masked DEBUG log, corrected Javadoc), `WhatsappNotificationServiceTest` (4 updated `sendShopOptions` tests: happy path, IOException, DataAccessException defect proof, null mobileNumber) |
| ijudi-api / docs | `docs/adr/ADR-029-mcp-whatsapp-outbound-notification-tools.md` (new ADR), `docs/test-plans/platform/mcp-whatsapp-outbound-notifications.md` (this file), `docs/test-plans/README.md` (index row) |

## Known-good test fixtures

### Unit test fixtures (mocked — no real MongoDB or Meta API)

| Field | Value |
|---|---|
| Test mobile number (normalised) | `+27821234567` |
| Test mobile number (0-prefix input) | `0821234567` → normalised to `+27821234567` |
| Test customer name | `"Test User"` |
| Template name (Tool 2) | `izinga_shop_options` |
| Template language | `en` |
| Template component type | `BODY`, single TEXT parameter (customer name) |
| Mocked phone ID from senderResolver | `"testPhoneId"` (lenient stub in setUp) |
| DataAccessException message (defect proof) | `"MongoDB timeout: senderResolver.resolve"` |
| IOException message (absorption path) | `"Meta API: template not approved"` |

### Live sandbox fixtures

| Field | Value | Notes |
|---|---|---|
| Sandbox WhatsApp test number | Configure via `WA_PHONE_ID` env var | Do NOT use production phone IDs in test |
| Meta template status | Must be APPROVED before live calls succeed | Tool absorbs failure if not approved |
| senderResolver CUSTOMER line | The `WhatsappSenderConfig` document with `audience=CUSTOMER, default=true` | Must exist in MongoDB for happy path |

## Test cases

### TC-01 — Tool 1: null-audience DENY (SEC-MCP-01)

**Precondition**: No scope token set (`StoreScopeContext.setAudience(null)` / no `X-MCP-Scope` header).  
**Action**: Invoke `send_landing_options_notification("+27821234567", "Test User")` over MCP.  
**Expected**:
- `CallerIdentityViolationException` thrown with `IDENTITY_REFUSAL:` prefix message.
- `agentIdentityAuditService.recordViolation` called with `AUDIENCE_VIOLATION`, tool name `"send_landing_options_notification"`, `callerPhone=null`.
- `WhatsappNotificationService.sendLandingOptions` NOT called.  
**Test**: `NotificationMcpControllerTest#sendLandingOptionsNotification_nullAudience_firesGateAndThrows`

---

### TC-02 — Tool 1: DRIVER audience ALLOW

**Precondition**: Valid scope token with `audience=DRIVER`.  
**Action**: Invoke `send_landing_options_notification("+27821234567", "Test User")`.  
**Expected**: Returns `"Landing options notification sent to +27821234567."`. `sendLandingOptions` called with `audience=DRIVER, storeId=null`.  
**Test**: `NotificationMcpControllerTest#sendLandingOptionsNotification_driverAudience_delegatesAndReturnsSuccessString`

---

### TC-03 — Tool 1: STORE audience with storeId forwarded

**Precondition**: Scope token with `audience=STORE, permittedStoreId="store-id-99"`.  
**Expected**: `sendLandingOptions` called with `storeId="store-id-99"` — correct branded line selected.  
**Test**: `NotificationMcpControllerTest#sendLandingOptionsNotification_storeAudienceWithStoreId_forwardsStoreIdToService`

---

### TC-04 — Tool 2: tokenless caller ALLOW (public tool)

**Precondition**: No scope token. `WhatsappNotificationService.sendShopOptions` returns `true`.  
**Action**: Invoke `send_shop_options_notification("+27821234567", "Test User")`.  
**Expected**: Returns success string. No identity gate fires. No audit violation recorded.  
**Test**: `NotificationMcpControllerTest#sendShopOptionsNotification_nullAudience_sendsNotificationWithoutGate`

---

### TC-05 — Tool 2: service returns false → degraded-outcome string (AC-09 truthfulness, FIX-2)

**Precondition**: No scope token. `sendShopOptions` returns `false` (simulating absorbed failure).  
**Expected**: Controller returns `"Shop options notification could not be delivered to +27821234567 at this time."` NOT the success string.  
**Test**: `NotificationMcpControllerTest#sendShopOptionsNotification_serviceReturnsFalse_returnsDegradedOutcomeString`

---

### TC-06 — `sendShopOptions` happy path (DEF-1 / AC-09)

**Action**: Call `sendShopOptions("+27821234567", "Test User")` with mocked sender resolver and WhatsApp service returning normally.  
**Expected**: Returns `true`. `senderResolver.resolve(CUSTOMER, null)` called. Template `izinga_shop_options` sent to `+27821234567`.  
**Test**: `WhatsappNotificationServiceTest#sendShopOptions_happyPath_sendsIzingaShopOptionsTemplateAndReturnsNormally`

---

### TC-07 — `sendShopOptions` absorbs IOException (AC-09)

**Action**: `whatsAppService.sendMessage().execute()` throws `IOException`.  
**Expected**: Returns `false`. No exception propagates.  
**Test**: `WhatsappNotificationServiceTest#sendShopOptions_whatsappSendThrowsIOException_absorbsExceptionAndDoesNotRethrow`

---

### TC-08 — `sendShopOptions` absorbs DataAccessException (DEF-1 fix, FIX-1)

**Action**: `senderResolver.resolve()` throws `DataAccessResourceFailureException("MongoDB timeout")`.  
**Expected**: Returns `false`. No exception propagates. This was the specific defect: old code only caught `IOException`, letting `DataAccessException` escape.  
**Test**: `WhatsappNotificationServiceTest#sendShopOptions_senderResolverThrowsDataAccessException_absorbsExceptionAndDoesNotRethrow`

---

### TC-09 — `sendShopOptions` null mobileNumber guard (FIX-1)

**Action**: Call `sendShopOptions(null, null)`.  
**Expected**: Returns `false` immediately. No call to `senderResolver.resolve`. No NPE.  
**Test**: `WhatsappNotificationServiceTest#sendShopOptions_nullMobileNumber_doesNotThrowNullPointerException`

---

### TC-10 — SEC-03 frozen allow-list ArchUnit test (FIX-4)

**Action**: Run `McpArchitectureTest#sec03_frozenPublicToolAllowList_onlySendShopOptionsNotificationIsPublic`.  
**Expected**: PASS when only `send_shop_options_notification` carries both `@Tool` and `@McpPublicTool`. Fails (AssertionError) if any other tool is marked public.  
**Non-vacuity**: Temporarily annotating a second tool with `@McpPublicTool` causes the test to FAIL — confirming the test is live.

---

### TC-11 — SEC-05 phone not logged at INFO (FIX-5)

**Action**: Run `sendShopOptions` happy path with any phone number.  
**Expected**: No `INFO` or `WARN` log line contains the full phone number. `DEBUG` log at most contains `...XXXX` (last 4 digits only).  
**Verification**: Read `WhatsappNotificationService.java` — confirm `LOGGER.info` on the phone line is replaced with `LOGGER.debug("Sent shop options to ...{}", to.length() > 4 ? to.substring(to.length() - 4) : "****")`.

---

### TC-12 — ArchUnit R7 non-vacuity (R7ViolatorFixture)

**Action**: Run `McpArchitectureTest#r7_toolMethodsMustHaveIdentityGateOrBePublicTool`.  
**Expected**: PASS. The `R7ViolatorFixture` class (outside production package scan) triggers an inner `AssertionError` for `R7ViolatorFixture#ungatedTool`, confirming R7 fires on actual violations.

---

### TC-13 — Tool count and name snapshot

**Action**: Run `McpToolContractSnapshotTest`.  
**Expected**: 19 tools registered (17 original + 2 new). `send_landing_options_notification` and `send_shop_options_notification` present in golden snapshot.

---

### TC-14 — Access matrix for new tools

**Action**: Run `McpAccessMatrixTest`.  
**Expected**: 12 new matrix rows pass — 6 for each tool covering all audience combinations. Tool 2 passes for null audience (public). Tool 1 throws `CallerIdentityViolationException` for null audience.

## Regression checks

| Check | How |
|---|---|
| All 17 existing tools still reject null audience | `McpAccessMatrixTest` DENY rows for existing tools — no rows changed |
| `GuardedToolCallback` unchanged | Confirm `GuardedToolCallback.call()` only reads `CallerIdentityContext.isMcpRequest()` — no annotation inspection |
| `WhatsappNotificationService.sendLandingOptions` unchanged | All existing overloads (3-arg scheduler + 5-arg inbound-handler) still present; nothing removed |
| `McpConfig.java` wires `NotificationMcpController` | `MethodToolCallbackProvider` list includes the new controller bean |
| Existing `mcp-tools.md` test plan still green | Re-run `admin/mcp-controller-layer-separation.md` validation commands |

## Validation commands

```bash
# Mandatory: set JDK 17 (Homebrew default is 23 and breaks ordermanager tests)
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Full reactor — the ONLY valid final gate (no -pl, no exclusions, always with clean)
cd /Users/lindanimasinga/Documents/GitHub/ijudi-api-mcp-whatsapp-notifications
./mvnw clean test

# Targeted module runs for development iteration (not a substitute for the full reactor)
./mvnw -pl izinga-messaging -am test
./mvnw -pl izinga-ordermanager -am test

# Confirm SEC-03 non-vacuity (must FAIL with AssertionError mentioning test name)
# Temporarily add @McpPublicTool to a second tool, run the test, then revert.
```

## Deferred / open items

| ID | Severity | Description | Tracking |
|---|---|---|---|
| SEC-04 | MEDIUM | No rate limit, cooldown, or per-number dedup on `send_shop_options_notification`. Risk: AI agent loops or misconfigurations could spam a user. Recommendation: add per-number cooldown (minimum 24 h) reusing `acquireCooldown`/`PayoutNotificationCooldownRepository` pattern (ADR-028) before Tool 2 is enabled in production. | ADR-029 §Deferred Follow-ups |
| POPIA-01 | OPEN | Tool 2 accepts an arbitrary mobile number from an unauthenticated caller with no opt-in check. The "transactional messages" exemption premise (Meta WhatsApp Business Policy) may not hold for a marketing shop-options message. Escalate to Lindani before enabling Tool 2 in production. | ADR-029 §Deferred Follow-ups |
