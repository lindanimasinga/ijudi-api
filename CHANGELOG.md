# Changelog

All notable changes to ijudi-api are documented here.

---

## [1.7.0] — 2026-09-04

**Release type:** Feature

**Summary:** WhatsApp OTP login, CPA-compliant customer cancellation fee, store endpoint authentication hardening, and ownership-transfer prevention on PATCH.

### Changes

- [NEW] **WhatsApp OTP login** — customers can now authenticate via a WhatsApp one-time password instead of SMS OTP. Backend generates and validates OTP tokens delivered through the WhatsApp Business API channel.
- [NEW] **ADR-019: CPA-compliant customer cancellation fee** — implements Phase 1 of the Consumer Protection Act-aligned cancellation fee policy for customer-initiated order cancellations. Fee calculation and enforcement logic added to the order cancellation flow.
- [NEW] **SEC-01-02: Store endpoint authentication + Lambda token-refresh fix** — all store write endpoints now require authentication. The `store-menu-to-izinga-menu` Lambda now carries a token-refresh interceptor so outbound requests to izinga-api authenticate correctly.
- [FIX] **NOTE-01: Strip `ownerId` from PATCH body** — ownership transfer is no longer possible via the PATCH endpoint; `ownerId` in the request body is silently ignored. Prevents inadvertent or malicious ownership reassignment.

### Breaking changes

None.

### Accepted risk — documented for audit

**SEC-01-02 / IZINGA_SERVICE_TOKEN provisioning on `store-menu-to-izinga-menu` Lambda not confirmed.**
The Lambda token-refresh interceptor requires the `IZINGA_SERVICE_TOKEN` environment variable to be provisioned in the Lambda execution environment. As of this release, DevOps has not confirmed that this secret is present in production. If the variable is absent, franchise menu sync requests from the Lambda to izinga-api will receive HTTP 401 responses and menu sync will silently fail until the secret is provisioned.

**Risk accepted by:** Lindani Masinga (co-founder, iZinga) — 2026-09-04

**Mitigation:** DevOps to provision `IZINGA_SERVICE_TOKEN` in the `store-menu-to-izinga-menu` Lambda environment immediately after this release. Monitor CloudWatch logs for 401s on the `/store/{storeId}/menu` endpoint in the 24 hours post-deploy.

### Deployment sequence

1. `ijudi-api` (this release) — backend first.
2. No frontend repos are affected by this release.

### Rollback steps

1. On ECS: update the service to the previous task definition revision (task def that ran `1.6.0` image).
2. In ECR: the `1.6.0` image is tagged and retained — redeploy it via `aws-ecr-push.yml` with `IMAGE_TAG=1.6.0` or trigger a manual ECS task definition rollback.
3. If the `IZINGA_SERVICE_TOKEN` Lambda issue causes cascading 401s before rollback is possible, the Lambda can be temporarily disabled in the AWS console without affecting the main API.
4. No database migrations in this release — no data rollback required.

### Smoke test plan

1. **WhatsApp OTP login** — initiate login with a valid phone number; confirm OTP is delivered via WhatsApp and accepted by the API. Expected: HTTP 200 with a valid auth token.
2. **Customer cancellation fee** — cancel an eligible order via the customer app; confirm the CPA cancellation fee is calculated and returned in the cancel response. Expected: fee amount present in response body, audit log entry created.
3. **Store write endpoint auth** — attempt a store update (`PUT /store/{storeId}`) without an auth header. Expected: HTTP 401.
4. **Store write endpoint auth (authenticated)** — perform the same store update with a valid Bearer token. Expected: HTTP 200, update persisted.
5. **ownerId PATCH** — send a `PATCH /store/{storeId}` body containing `ownerId` pointing to a different user. Expected: HTTP 200 but `ownerId` unchanged in the database.
6. **Franchise menu sync (Lambda)** — trigger a menu sync from the `store-menu-to-izinga-menu` Lambda (if `IZINGA_SERVICE_TOKEN` is provisioned) and confirm menus update correctly. Expected: HTTP 200 from izinga-api menu endpoint; if token not provisioned, 401 expected — monitor CloudWatch.

### Post-deployment monitoring

- **15 min:** Check ECS service health — task count stable, no crash loops.
- **1 hour:** CloudWatch logs — scan for 401s on `/store/*` endpoints from the Lambda (flag if seen and `IZINGA_SERVICE_TOKEN` not yet provisioned by DevOps).
- **24 hours:** Order cancellation fee volume, WhatsApp OTP success/failure rate, store update error rate.
- **Growth & Analytics:** Watch for anomalies in order cancellation volume (CPA fee may affect cancellation behaviour).

### Approved by

Lindani Masinga — 2026-09-04

---

## [1.6.0] — 2026-08 (previous release)

Payout Reconciliation initiative — ambassador and referral partner payout bundle endpoints, admin-auth enforcement on recon endpoints.
