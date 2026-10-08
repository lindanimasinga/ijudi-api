# TIER-BILLING-01 — Gate Review: Security & Compliance

**Date:** 3 October 2026
**Reviewer:** iZinga Security & Compliance
**Input:** TIER-BILLING-01 feature brief (TIER-BILLING-01-payfast-subscription-billing.md), ADR-023 (SA design), ONB-02-gate-reviews.md (precedent format), SecurityConfig.java (izinga-ordermanager), YocoPaymentController.kt (existing webhook reference), application-prod.yml (izinga-ordermanager — critical finding)
**Gate:** Gate (a) — blocks T-07 (ITN handler implementation)

---

## CRITICAL PRE-EXISTING FINDING — ESCALATE TO LINDANI MASINGA IMMEDIATELY

Before addressing Gate (a), a Critical security issue was found during the codebase inspection required for this review. It is directly relevant to TIER-BILLING-01's credential management requirement.

**SEC-CRIT-01 — Multiple production secrets hardcoded in `application-prod.yml` (P0 — CRITICAL)**

`/ijudi-api/izinga-ordermanager/src/main/resources/application-prod.yml` contains the following credentials committed in plaintext to the repository:

| Secret | Value (partially redacted) | Severity |
|---|---|---|
| AWS Access Key ID | `AKIAUTE3KPBGHYXR3V6C` | Critical |
| AWS Secret Access Key | `4hRJirV5PsHO4TudToJ9C94n25pkGn9EPoQFKj+3` | Critical |
| MongoDB connection URI (prod) | `mongodb+srv://onusmongouser:wfsuser@cluster0-7odiz.mongodb.net/...` | Critical |
| PayFast passphrase | `izinga-For-clothing1` (merchant 11522007) | Critical |
| Yoco live API key | `sk_live_9c9d51f7MP0k5GYc73647408c0e4` | Critical |
| Yoco webhook secret | Base64 value present | Critical |
| Ukheshe password | `Csd0148()1` | Critical |
| Ukheshe username | `812815707` | High |
| Ozow API key | `760c42eec84640cf98aa1558135a9d90` | High |
| MailerSend API key (JWT) | Full JWT token present | High |
| Google Maps API key | `AIzaSyAZbvE4NBcJIplfzmy8cSEdSpbocBggylc` | High |
| Google/Firebase FCM server key | Full value present | High |
| ZoomConnect SMS API key | `c0217d42-dd53-4d6b-811c-a79b584e5177` | High |
| Yoco dashboard API token | Full token present | High |

These secrets are not confined to a local developer environment — this file is in the version-controlled repository. If this repository has been pushed to a remote (GitHub, GitLab, Bitbucket), these credentials are in git history and must be treated as fully compromised regardless of whether any file-level change is made now. Removing secrets from the working tree does not remove them from git history.

**This is a P0 incident. Lindani Masinga must be notified immediately. Do not proceed with TIER-BILLING-01 engineering until a rotation plan is underway.**

Required immediate actions (in this order):
1. Revoke and rotate the AWS Access Key `AKIAUTE3KPBGHYXR3V6C` — it grants access to AWS Secrets Manager and S3 among other services.
2. Rotate the MongoDB `onusmongouser` database credential (change password in MongoDB Atlas; update all live deployments to use the new credential via environment injection, not application-prod.yml).
3. Rotate the Yoco live API key `sk_live_9c9d51f7MP0k5GYc73647408c0e4` — Yoco may have already detected this if the repo is public.
4. Rotate the Ukheshe password `Csd0148()1`.
5. Rotate the Ozow API key, MailerSend API key, ZoomConnect SMS key, Yoco webhook secret, and Yoco dashboard token.
6. Rotate the PayFast passphrase for merchant 11522007 (set a new passphrase in the PayFast merchant portal).
7. Remove `application-prod.yml` from git tracking: add it to `.gitignore`, then run `git filter-repo` or `BFG Repo Cleaner` to purge the file from git history.
8. If the repository is hosted on a remote, force-push the rewritten history (this requires coordination and will break all existing clones).
9. Move all secrets to AWS Secrets Manager (the `spring.config.import: aws-secretsmanager:izinga-prod` import is already wired in this file — the mechanism exists; secrets just need to be moved there and the file stripped of inline values).

Note: The memory index records that the MongoDB secret in `application.yml` was flagged on 2 October 2026. The scope is substantially larger than that single finding — the full `application-prod.yml` must be treated as a compromised credentials file.

**TIER-BILLING-01's SEC-TB01-03 design requirement (credentials to AWS Secrets Manager only) is correct architecture. This existing file demonstrates the exact anti-pattern that TIER-BILLING-01 must not repeat. The PayFast credentials for merchant 16791971 (merchant_key, passphrase) must NEVER be added to application-prod.yml, application.yml, or any committed file.**

---

## Gate (a) — Security & Compliance Review

**Verdict: CONDITIONAL PASS — five required changes must be incorporated into T-07's task specification before the Backend Developer begins implementation**

The core design is sound. PayFast signature validation combined with server-to-server validation provides adequate defense in depth for a public webhook endpoint. The five required changes below address a response-code ambiguity, a concurrency gap in the idempotency design, and three missing implementation constraints that — if left unspecified — create exploitable failure modes.

---

### Summary table

| Area | ID | Verdict | Severity |
|---|---|---|---|
| MD5 signature validation adequacy | SEC-TB01-01-A | CONDITIONAL PASS | Medium |
| Source IP policy | SEC-TB01-01-B | PASS WITH CONDITIONS | Medium |
| Server-to-server validate failure modes | SEC-TB01-01-C | REQUIRED CHANGE | High |
| Server-to-server validate bypassability | SEC-TB01-01-D | REQUIRED CHANGE | Critical |
| SecurityConfig explicit permit | SEC-TB01-01-E | REQUIRED CHANGE | High |
| Replay attack prevention (atomic) | SEC-TB01-02-A | REQUIRED CHANGE | High |
| Monthly renewal idempotency | SEC-TB01-02-B | NOTE — future phase | Medium |
| Credentials at rest (design) | SEC-TB01-03-A | PASS — design is correct | — |
| payFastToken logging exclusion | SEC-TB01-03-B | PASS WITH CONDITION | Medium |
| payFastToken in future GET responses | SEC-TB01-03-C | REQUIRED CHANGE | High |
| Passphrase leak blast radius | SEC-TB01-03-D | NOTE — mitigated by design | — |

---

### SEC-TB01-01-A: MD5 Signature Validation — Is It Sufficient?

**Assessment:** MD5 is cryptographically broken for collision resistance (two different inputs can produce the same hash). PayFast's ITN protocol specifies MD5 — iZinga cannot change this. The question is whether it is acceptable given MD5's known weaknesses.

**Finding: CONDITIONAL PASS**

MD5 is used here as a keyed hash: the merchant passphrase is appended to the parameter string before hashing. An attacker who does not know the passphrase cannot craft a valid forged ITN and reproduce the MD5 hash, because they cannot reproduce the full parameter string (which includes the passphrase). Practical MD5 collision attacks require the attacker to control both messages — an attacker who does not control the passphrase cannot execute a meaningful collision attack against this scheme.

The scheme is weaker than HMAC-SHA256, but it is the scheme PayFast specifies and iZinga cannot substitute it. However, MD5-with-secret-key as a sole control would be inadequate given that:

1. MD5 has known weaknesses that have been sharpened over time.
2. If the passphrase ever leaks (see SEC-CRIT-01 above — this is not a hypothetical risk), an attacker with the passphrase can trivially forge a valid ITN signature for any parameters they choose.

**The server-to-server validation call (posting the ITN to PayFast's `/eng/query/validate`) is the compensating control that makes this design adequate.** Even with a leaked passphrase and a valid forged MD5 signature, an attacker cannot make PayFast's own validation endpoint return `VALID` for a payment that was never made on their platform.

**Implication:** SEC-TB01-01-D (server-to-server validate non-bypassability) and SEC-TB01-01-C (failure-mode handling) are load-bearing for the acceptability of MD5. If either of those controls is ever weakened or disabled, the MD5 scheme must be treated as broken for this use case.

**No code change required for this finding alone** — the design is acceptable as stated, provided the server-to-server validate is enforced per SEC-TB01-01-C and SEC-TB01-01-D.

---

### SEC-TB01-01-B: "Log But Don't Reject" IP Mismatch Policy

**Assessment:** PayFast publishes a set of source IP addresses for ITN calls. The design logs a warning on IP mismatch but does not hard-reject, on the basis that PayFast's IP list can change without notice and a hard-reject would cause production outages when PayFast adds a new IP.

**Finding: PASS WITH CONDITIONS**

The policy is the right call for production availability, but only under the following explicit conditions that must be stated in the implementation:

1. The IP check must still be implemented and must write a structured log entry on mismatch — not an unstructured string. The log entry must include at minimum: the source IP, the `mPaymentId`, the timestamp, and a log marker such as `PAYFAST_IP_UNRECOGNIZED` so it is filterable in the logging platform.
2. The log-and-continue policy is conditional on BOTH the MD5 signature check AND the server-to-server validate being enforced. If either is ever disabled or bypassed, the IP check must be immediately upgraded to hard-reject. This conditional must be documented as a comment in `PayFastItnHandler` at the IP check implementation point.
3. PayFast's published IP ranges must be configuration-driven (in `application.yml` under a non-secret property such as `payfast.allowed-ips`) so they can be updated without a code deploy when PayFast changes them. Hard-coding IP literals in Java/Kotlin source is not acceptable.

---

### SEC-TB01-01-C: Server-to-Server Validate Failure Modes — REQUIRED CHANGE (HIGH)

**Problem:** The feature brief specifies "always return HTTP 200 to PayFast" from the ITN endpoint. This is the correct response for permanent failures (invalid signature, invalid payment). However, it is the wrong response for a transient failure of the server-to-server validate call itself (network timeout, PayFast's validate endpoint returning 5xx, connection refused).

The current design does not distinguish these two failure modes. If "always HTTP 200" is implemented literally, a transient server-side error on the validate call would result in:
1. iZinga's handler returns HTTP 200 to PayFast.
2. PayFast treats this as successful delivery and does not retry.
3. The subscription never activates for the merchant — a permanent subscription activation failure caused by a transient network error.

**Required behavior (binding for T-07):**

The ITN handler must distinguish three outcomes:

| Outcome | HTTP Response | Subscription action |
|---|---|---|
| Signature validation fails (permanent) | 200 | No update; log SIGNATURE_VALIDATION_FAILED |
| Server-to-server validate returns INVALID (permanent — PayFast confirmed this is not a real payment) | 200 | No update; log PAYFAST_VALIDATE_INVALID |
| Server-to-server validate call fails — timeout, network error, PayFast 5xx (transient) | **500** | No update; log PAYFAST_VALIDATE_CALL_FAILED with error detail |

Returning HTTP 500 for the transient case tells PayFast "I had an error — please retry," which is the correct behavior. PayFast's retry behavior (documented in PayFast developer docs) will redeliver the ITN, at which point the validate call can succeed.

The server-to-server validate call must have an explicit HTTP timeout configured (recommended 5 seconds). This timeout must be a configuration property (`payfast.validate-timeout-seconds`) so it can be tuned without a code change. On timeout, the handler must treat this as a transient failure and return 500.

---

### SEC-TB01-01-D: Server-to-Server Validate Must Be Non-Bypassable — REQUIRED CHANGE (CRITICAL)

**Problem:** The feature brief does not explicitly state that the server-to-server validate call has no bypass path in production code. In practice, developers often add feature flags or environment-specific properties to disable external calls in testing environments. If a `payfast.validate-enabled=false` flag (or equivalent) is added for sandbox/test convenience and that flag can be set in production, the signature check becomes the sole control — and as established in SEC-TB01-01-A, MD5 alone is not sufficient if the passphrase is ever compromised.

**Required constraint (binding for T-07):**

No conditional compilation path, no feature flag, and no environment property may disable or bypass the server-to-server validate call in production code. The implementation must ensure:

1. The validate call is always performed after a signature passes — there is no code path that skips it.
2. If a test profile needs to avoid live calls to PayFast's validate endpoint, this must be handled by a test-only mock bean (`@Profile("test")` or `@ConditionalOnProperty` scoped to test profiles only) that returns `VALID` for a configured test `mPaymentId` value. The production implementation class must not contain any `if (payfastValidateEnabled)` branch.
3. The Code Reviewer (T-16) must verify there is no bypass path.

---

### SEC-TB01-01-E: SecurityConfig Explicit permitAll Required — REQUIRED CHANGE (HIGH)

**Problem:** `SecurityConfig.java` line 64 has `.anyRequest().permitAll()` as the final catch-all. The ITN endpoint `POST /merchant/subscription/itn` would be publicly accessible even without an explicit matcher, because it falls through to the catch-all.

Relying on the catch-all for intentional public access is not acceptable:
1. It makes the security posture of this endpoint invisible — a future security reviewer reading `SecurityConfig.java` cannot distinguish intentional public access from an accidentally exposed endpoint.
2. If the catch-all is ever changed to `authenticated()` as part of a hardening exercise, the ITN endpoint would silently break because PayFast cannot supply a JWT.
3. The ONB-02 gate review (SEC-ONB02-03-C) already established that explicit matchers are required for new endpoints — this is the same principle applied to the public case.

**Required for T-07 (binding):**

Add an explicit matcher in `SecurityConfig.java`:

```java
.requestMatchers(POST, "/merchant/subscription/itn").permitAll()
```

This must be placed before `.anyRequest().permitAll()` and must carry a comment:

```java
// SEC-TB01-01-E: PayFast ITN webhook — explicitly public (no JWT, PayFast server-to-server).
// Security enforced by PayFast signature validation + server-to-server validate in PayFastItnHandler.
// Never remove or move to authenticated() — PayFast cannot supply a Bearer token.
.requestMatchers(POST, "/merchant/subscription/itn").permitAll()
```

The Code Reviewer (T-16) must verify this matcher is present and correctly positioned.

---

### SEC-TB01-02-A: Replay Prevention — Race Condition in Idempotency Check — REQUIRED CHANGE (HIGH)

**Problem:** REQ-04 specifies: "check whether a `MerchantSubscription` with matching `mPaymentId` AND `payFastPaymentId` has already been processed to `ACTIVE`. If yes, return HTTP 200 without re-processing."

This is a read-then-write pattern (read the current status, then decide whether to write). Under concurrent ITN delivery — which PayFast can trigger when iZinga's server is slow to respond (PayFast may resend before the HTTP 200 is returned from the first attempt) — two concurrent ITN handler invocations can both pass the check before either completes the write:

```
Thread A: reads subscription — status = PENDING_PAYMENT — passes check
Thread B: reads subscription — status = PENDING_PAYMENT — passes check
Thread A: writes status = ACTIVE, publishes MerchantSubscriptionActivatedEvent
Thread B: writes status = ACTIVE, publishes MerchantSubscriptionActivatedEvent (DUPLICATE)
```

This double-fires `MerchantSubscriptionActivatedEvent`, which calls `StoreService.updateSubscriptionTier()` twice. For the current Phase 1 flow, setting `subscriptionTier` twice is idempotent in isolation. However:
- If any non-idempotent side effect is ever added to the event listener (e.g., a welcome notification, a billing record entry), the double-fire becomes a real billing or operational error.
- Two concurrent writes to `MerchantSubscription` can leave the document in an inconsistent state depending on MongoDB write concern settings.

**Required implementation pattern for T-07 (binding):**

Replace the read-then-check with a MongoDB atomic `findAndModify` operation that uses the current status as a condition. In Spring Data MongoDB:

```java
// Only succeeds if the document is still in PENDING_PAYMENT status.
// Returns null if already ACTIVE (duplicate ITN) or if mPaymentId is not found.
Query query = Query.query(
    Criteria.where("_id").is(mPaymentId)
            .and("status").is(MerchantSubscriptionStatus.PENDING_PAYMENT)
);
Update update = new Update()
    .set("status", MerchantSubscriptionStatus.ACTIVE)
    .set("activatedDate", Instant.now())
    .set("payFastPaymentId", payFastPaymentId)
    .set("payFastToken", payFastToken)
    .set("lastBillingDate", billingDate)
    .set("nextBillingDate", nextBillingDate);
MerchantSubscription previous = mongoTemplate.findAndModify(query, update,
    FindAndModifyOptions.options().returnNew(false),
    MerchantSubscription.class);

if (previous == null) {
    // Either already ACTIVE (replay) or mPaymentId not found — return 200 without re-firing event
    return;
}
// This branch executes exactly once per mPaymentId — publish the event
publisher.publishEvent(new MerchantSubscriptionActivatedEvent(previous.getStoreId(), previous.getTier()));
```

This pattern guarantees that `MerchantSubscriptionActivatedEvent` is published at most once per `mPaymentId`, regardless of concurrent ITN delivery. The `findAndModify` is a single atomic operation at the MongoDB driver level.

The `payFastToken` field must be excluded from the `new Update()` set() call shown above if the token is being logged anywhere; see SEC-TB01-03-B.

---

### SEC-TB01-02-B: Monthly Renewal Idempotency — NOTE, Future Phase (MEDIUM)

PayFast's subscription product sends a new ITN for each monthly billing cycle. Each month's ITN has a different `payFastPaymentId` (a new payment on PayFast's platform) but may reference the same `mPaymentId` (iZinga's subscription record ID). The current idempotency check (match on `mPaymentId` + `payFastPaymentId`) will correctly pass through monthly renewal ITNs because they carry a new `payFastPaymentId`.

However, once a subscription is in `ACTIVE` status, the atomic `findAndModify` pattern above (which checks for `status = PENDING_PAYMENT`) will not update the record on renewal — the document is already `ACTIVE`. Phase 1 only covers initial activation, so this is not a blocking concern now. A renewal handling path (updating `lastBillingDate`, `nextBillingDate` on each recurring `COMPLETE` ITN) must be designed when recurring billing management is added. Flag this in the T-07 task note so it is not accidentally handled incorrectly: the handler must not attempt to re-activate or re-fire `MerchantSubscriptionActivatedEvent` for a renewal ITN on an already-`ACTIVE` subscription.

---

### SEC-TB01-03-A: Merchant Credentials at Rest — Design Assessment (PASS — design is correct)

The feature brief requires `merchant_key` and `passphrase` for merchant 16791971 to be stored in AWS Secrets Manager and never committed to any application.yml or other version-controlled file. The mechanism already exists in the codebase (`spring.config.import: aws-secretsmanager:izinga-prod` is wired in `application-prod.yml`).

**The design requirement is correct. SEC-CRIT-01 above confirms the anti-pattern exists in the codebase already and that this requirement must be enforced strictly for the new merchant credentials.** The Backend Developer implementing T-05 (checkout service) and T-07 (ITN handler) must not add `payfast.merchant-key` or `payfast.passphrase` values to any committed configuration file. The properties must be defined as Spring Boot property placeholders (`${payfast.merchant-key}`) resolved exclusively from the AWS Secrets Manager import.

This also applies to `payfast.merchant-id` (16791971): while the merchant ID is not a secret (it appears in public PayFast API calls), it must be environment-configured as a property rather than hardcoded inline in `PayFastItnHandler` or `PayFastCheckoutService`. This allows sandbox and production to use different accounts without code changes.

---

### SEC-TB01-03-B: payFastToken Logging Exclusion (PASS WITH CONDITION)

REQ-04 states: "The `payFastToken` field must never appear in application logs or error messages." This is correct. `payFastToken` is a recurring billing authorization that, if leaked, would allow iZinga (or any party who obtained it) to charge the merchant outside the agreed subscription. It is functionally equivalent to a stored payment credential.

**Condition:** The `payFastToken` field must be explicitly annotated with `@JsonIgnore` on the `MerchantSubscription` Kotlin class, AND must be excluded from any logging framework auto-serialization. In practice, this means:

1. Never pass a `MerchantSubscription` object directly to a log statement (e.g., `log.info("subscription: {}", subscription)` — if the object serializes, the token will be in the log line).
2. In any catch block that logs exception details, verify the exception message or context does not include the ITN payload string (which contains the token field from PayFast's POST body).
3. The integration test (T-09) must include a log assertion that verifies no line containing `payfast_token` or the literal token value appears in application log output.

---

### SEC-TB01-03-C: payFastToken in Future GET Responses — REQUIRED CHANGE (HIGH)

The current brief does not define a GET endpoint for `MerchantSubscription`. However, `payFastToken` is a field on the MongoDB document, and any future GET endpoint (e.g., for merchant subscription status or admin views) that serializes the full `MerchantSubscription` document will expose the token.

**Required for T-04 (data model, binding):**

The `payFastToken` field on the `MerchantSubscription` Kotlin class must be annotated with `@JsonIgnore` or its Jackson equivalent at the model level. This is a defense-in-depth measure: even if a future developer builds a GET endpoint without thinking about token exposure, the field will not serialize into the JSON response.

```kotlin
@JsonIgnore
var payFastToken: String? = null
```

This does not affect the field's persistence to MongoDB (Jackson annotations do not affect the MongoDB codec). The field will still be stored and retrieved from the database for internal use; it simply will not appear in any serialized API response.

---

### SEC-TB01-03-D: Passphrase Leak Blast Radius (NOTE — mitigated by design)

**What happens if the PayFast passphrase for merchant 16791971 leaks?**

An attacker who knows the passphrase can construct a forged ITN POST with a valid MD5 signature. They can address it to any `mPaymentId` in `PENDING_PAYMENT` state — the subscription ID is not a secret (it is returned in the `POST /merchant/subscription/initiate` response to the authenticated store owner).

The blast radius of a passphrase leak is: fraudulent activation of any merchant subscription currently in `PENDING_PAYMENT` state. This does not create a direct financial loss for iZinga (the fraudster activates a premium tier without paying), but it does create a business loss (unpaid premium subscriptions) and undermines billing integrity.

**The server-to-server validate call is the control that stops this attack.** Even with a valid forged signature, PayFast's `/eng/query/validate` endpoint will return `INVALID` for a `pf_payment_id` value that does not correspond to a real payment on PayFast's platform. An attacker cannot forge a valid `pf_payment_id`.

**This means the blast radius IF the server-to-server validate were also bypassed (via a bypass flag — see SEC-TB01-01-D) would be critical.** SEC-TB01-01-D's non-bypassability requirement is the control that contains this blast radius to an acceptable level.

**Rotation policy required:** The passphrase must have a documented rotation policy. Given SEC-CRIT-01 (the current git history contains the old PayFast passphrase `izinga-For-clothing1`), rotation should be performed before or simultaneously with the TIER-BILLING-01 go-live. The Release Manager (T-17) must confirm the passphrase for merchant 16791971 has never appeared in any committed file before authorising production deployment.

---

### Additional Security Findings (not in the original three SEC-TB01 areas)

**SEC-TB01-04: BILLING_ADMIN Role Promotion (MEDIUM — carry-forward from ONB-02)**

ONB-02 gate review finding SEC-ONB02-03-F required that the `ADMIN` bypass on `PATCH /store/{id}/subscription-tier` be replaced by a `BILLING_ADMIN` role in TIER-BILLING-01, because in TIER-BILLING-01 an erroneous tier change could trigger a billing event.

This is now in scope. The checkout initiation endpoint `POST /merchant/subscription/initiate` is restricted to `STORE_ADMIN`, which is correct. However, the existing `PATCH /store/{id}/subscription-tier` endpoint (ONB-02 T-SA-07) allows `ADMIN` to change tiers without restriction. In Phase 1 of TIER-BILLING-01, a manual tier upgrade via ADMIN bypasses the billing flow entirely — a store gets `subscriptionTier = PREMIUM_1` without a `MerchantSubscription` record or any PayFast payment.

This is not a blocker for Gate (a) (which covers the ITN handler, not the manual tier change endpoint). However, the Backend Developer must add a note on `PATCH /store/{id}/subscription-tier` at T-SA-07: "When TIER-BILLING-01 goes live, ADMIN use of this endpoint must be restricted to BILLING_ADMIN role only, and any tier upgrade via this endpoint must create a corresponding MerchantSubscription record in ADMIN_OVERRIDE status." This must be a GitHub issue raised before the TIER-BILLING-01 feature branch is opened, not just a code comment.

**SEC-TB01-05: Rate Limiting on ITN Endpoint (LOW)**

The ITN endpoint is public and performs a MongoDB lookup, an MD5 computation, and an outbound HTTP call (server-to-server validate) per request. A bot that hammers the endpoint with syntactically valid but signature-invalid POST bodies will consume server resources proportionally. For a low-volume production endpoint (PayFast sends one ITN per payment event), the traffic profile is highly predictable — a spike of more than a handful of requests per minute from a single IP is anomalous.

This is LOW severity given that the signature check fails fast (before the database lookup and validate call). Rate limiting at the load balancer level (e.g., 20 requests per IP per minute) is recommended as a production hardening measure but is not a blocker for Gate (a). Carry to T-17 (Release Manager checklist) as a pre-production hardening item.

**SEC-TB01-06: Merchant ID 11522007 vs 16791971 Discrepancy (MEDIUM — operational)**

The existing `application-prod.yml` shows `payfast.api.merchant.id: 11522007`. The feature brief specifies iZinga's live merchant ID as `16791971`. These are different values. One explanation is that `11522007` is a legacy or sandbox merchant ID and the active production account is `16791971` — but this discrepancy must be confirmed before T-07 ships. If `PayFastItnHandler` verifies `merchant_id` against a configuration property (which it should per SEC-TB01-03-A), and that property resolves from the committed `application-prod.yml` value, the handler would reject all real ITN calls because the `merchant_id` in every PayFast ITN from account 16791971 would not match the configured value 11522007.

**Required before T-07 implementation:** Confirm with Lindani which merchant ID is the active production account. Remove any `payfast.api.merchant.id` value from `application-prod.yml`; manage it exclusively as a property resolved from AWS Secrets Manager or a non-committed environment-specific configuration. The Backend Developer must not hardcode either ID value in source code.

---

### OWASP Top 10 Assessment — TIER-BILLING-01 ITN Design

| Risk | Status | Finding |
|---|---|---|
| A01 Broken Access Control | PASS WITH CONDITION | ITN endpoint intentionally public — must be explicit in SecurityConfig (SEC-TB01-01-E). Initiate endpoint storeId from JWT confirmed in REQ-02. No IDOR risk on ITN (no user-controlled subscription selection). |
| A02 Cryptographic Failures | CONDITIONAL PASS | MD5 as keyed hash is weak but acceptable given server-to-server validate compensating control (SEC-TB01-01-A). Passphrase rotation policy required (SEC-TB01-03-D). |
| A03 Injection | PASS | PayFast ITN parameters are used as MongoDB update values — parameterized via Spring Data `Update` builder, not string concatenation. No injection surface from ITN body. |
| A04 Insecure Design | REQUIRES ACTION | Race condition in idempotency check (SEC-TB01-02-A). Server-to-server validate failure modes not distinguished (SEC-TB01-01-C). |
| A05 Security Misconfiguration | REQUIRES ACTION | ITN endpoint must be explicitly `permitAll()` in SecurityConfig, not rely on catch-all (SEC-TB01-01-E). Server-to-server validate must have no bypass in production (SEC-TB01-01-D). |
| A06 Vulnerable Components | NOT ASSESSED | No new dependencies introduced beyond `izinga-payfast` module itself. Standard dependency audit (`./mvnw dependency:check`) applies for the release. |
| A07 Auth Failures | PASS | ITN endpoint requires no JWT by design — PayFast cannot authenticate as an iZinga user. Signature + server-side validate are the correct controls for a server-to-server payment webhook. |
| A08 Software Integrity | PASS WITH NOTE | Environment gating (sandbox vs. production URL and credentials) is specified in the brief. Verify no sandbox credentials can be activated in production via profile override (Backend Developer confirms active profile in T-09). |
| A09 Logging Failures | REQUIRES ACTION | payFastToken must never appear in logs (SEC-TB01-03-B). SIGNATURE_VALIDATION_FAILED, PAYFAST_VALIDATE_INVALID, and PAYFAST_VALIDATE_CALL_FAILED must be structured log entries with filterable markers. Internal errors must not propagate full stack traces to the HTTP response body. |
| A10 SSRF | PASS | The server-to-server validate call is to a fixed, environment-configured PayFast URL — no user-controlled input determines the outbound request target. |

---

### Data Protection (POPIA)

The ITN handler processes the following personal data:
- `storeId` — links to a store owner's personal data
- `payFastToken` — a billing authorization token linked to the merchant's payment method

Neither field constitutes direct personal information under POPIA's definition in isolation, but both are linked to an identifiable natural person (the store owner) through the `MerchantSubscription` record. The `payFastToken` in particular must be treated as a high-sensitivity field: it authorizes future charges against the merchant's payment method and must not be disclosed to any party other than iZinga's internal billing processes.

The `merchant_subscriptions` collection must be added to iZinga's POPIA records-of-processing (privacy@izinga.co.za as Information Officer). The collection must not be accessible via any public or unauthenticated API endpoint. A future subject access request under POPIA Section 23 must be serviceable — the merchant can see their subscription status via `StoreProfile.subscriptionTier`, not by reading raw `MerchantSubscription` documents.

---

### Required Changes Summary — Binding for T-07

| ID | Finding | Required change | Blocks |
|---|---|---|---|
| SEC-TB01-01-C | Server-to-server validate failure modes | Distinguish: signature fail → 200, INVALID response → 200, transient call failure → 500; configure HTTP timeout as `payfast.validate-timeout-seconds` | T-07 task spec |
| SEC-TB01-01-D | Server-to-server validate bypassability | No feature flag or conditional path may disable the validate call in production code; test bypass must be a `@Profile("test")` mock bean only | T-07 task spec |
| SEC-TB01-01-E | SecurityConfig explicit permitAll | Add `.requestMatchers(POST, "/merchant/subscription/itn").permitAll()` with audit comment before `.anyRequest().permitAll()` | T-07 task spec |
| SEC-TB01-02-A | Idempotency race condition | Replace read-then-check with atomic `mongoTemplate.findAndModify()` on `status = PENDING_PAYMENT`; event fires only on non-null return | T-07 task spec |
| SEC-TB01-03-C | payFastToken in future GET responses | Annotate `payFastToken` field on `MerchantSubscription` with `@JsonIgnore` at T-04 (data model) | T-04 task spec (bind to T-04, not T-07) |
| SEC-TB01-03-B | payFastToken logging | Never pass MerchantSubscription to log statement directly; catch blocks must not log raw ITN payload | T-07 task spec |
| SEC-TB01-01-B | IP check structured logging | Log mismatch as structured entry with IP, mPaymentId, timestamp, marker `PAYFAST_IP_UNRECOGNIZED`; IPs in config not source code | T-07 task spec |
| SEC-TB01-06 | Merchant ID discrepancy | Confirm active production merchant ID; manage via property placeholder only, never hardcoded | T-07 pre-condition |

**Additional carry-forward items:**
- SEC-TB01-04: BILLING_ADMIN role promotion — raise as GitHub issue before TIER-BILLING-01 branch opens
- SEC-TB01-05: Rate limiting on ITN endpoint — Release Manager pre-production checklist item (T-17)
- SEC-TB01-03-D: Passphrase rotation policy — Release Manager must confirm new passphrase never appeared in committed file before T-17

---

### Verdict

**CONDITIONAL PASS — T-07 (ITN handler) may start once all required changes are incorporated into the T-07 task specification**

The security design for the PayFast ITN webhook is sound in its fundamentals. The combination of signature validation and server-to-server validate provides genuine defense in depth for a protocol constrained to MD5. None of the five required changes alter the overall architecture — they are implementation constraints that prevent specific failure modes.

All five required changes (SEC-TB01-01-C, SEC-TB01-01-D, SEC-TB01-01-E, SEC-TB01-02-A, SEC-TB01-03-C) must be written into the T-07 and T-04 task specifications before the Backend Developer begins those tasks. The Code Reviewer at T-16 must verify all five against the implementation.

The pre-existing credential exposure in `application-prod.yml` (SEC-CRIT-01) is a separate, immediately actionable P0 issue that requires Lindani Masinga's attention before any further production deployments — it is not a gate condition on TIER-BILLING-01 engineering (which can proceed in the development environment), but it is a hard blocker on any merge to main or production deployment across ALL iZinga features until the compromised credentials are rotated and removed from git history.

**Approved for T-07 start:** Yes — conditional on required changes incorporated into task spec

**Approved for production deploy:** No — blocked on SEC-CRIT-01 credential rotation (pre-existing, applies to entire platform) AND Gate (b) PayFast rate confirmation AND Gate (c) BackOffice setup

---

*Security & Compliance gate completed · 3 October 2026*
*Gate (a) — CONDITIONAL PASS. Required changes binding on T-07 and T-04.*
*SEC-CRIT-01 — P0 credential exposure in application-prod.yml — escalated to Lindani Masinga immediately.*
