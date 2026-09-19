# WA-LINES-01 Pre-Deploy Runbook

## Purpose

This runbook describes the MongoDB index migrations and data adoption steps required before deploying the WA-LINES-01 feature to production.

**Important:** These are manual one-time mongosh commands. They are NOT run by application startup code (SA-2: index migration is a pre-deploy runbook, not startup code).

Run these on the target MongoDB Atlas cluster **before** deploying the new image.

---

## Step 0: Prerequisites

```bash
# Connect to the correct cluster
mongosh "mongodb+srv://<cluster>.mongodb.net/<dbname>" --username <username>

# Switch to the correct database
use <dbname>
```

Replace `<cluster>`, `<dbname>`, and `<username>` with actual values from the environment secrets.

---

## Step 1: Add compound index on whatsapp_session (from + phoneNumberId)

SEC-02 / REQ-13: Sessions are now looked up by `(from, phoneNumberId)` in addition to `from` alone.

```javascript
db.whatsapp_session.createIndex(
  { from: 1, phoneNumberId: 1 },
  { name: "idx_from_phoneNumberId", sparse: true, background: true }
)
```

Verify:
```javascript
db.whatsapp_session.getIndexes().filter(i => i.name === "idx_from_phoneNumberId")
// Expected: array with one entry
```

---

## Step 2: Add index on ai_conversation_histories (driverPhoneNumber + agentName)

REQ-15: Conversation history is now scoped per `(driverPhoneNumber, agentName)`.

```javascript
db.ai_conversation_histories.createIndex(
  { driverPhoneNumber: 1, agentName: 1 },
  { name: "idx_phone_agentName", background: true }
)
```

Verify:
```javascript
db.ai_conversation_histories.getIndexes().filter(i => i.name === "idx_phone_agentName")
```

---

## Step 3: Stamp legacy whatsapp_session documents

REQ-14 / SA-2: Existing sessions have no `phoneNumberId`. Stamp them with the legacy customer phone line ID so compound-key lookups work.

Replace `<LEGACY_PHONE_NUMBER_ID>` with the value of `whatsapp.cloud.phoneId` from the production secrets:

```javascript
const legacyPhoneNumberId = "<LEGACY_PHONE_NUMBER_ID>";

db.whatsapp_session.updateMany(
  { phoneNumberId: { $exists: false } },
  { $set: { phoneNumberId: legacyPhoneNumberId, agentName: "customer_support" } }
)
```

Verify:
```javascript
db.whatsapp_session.countDocuments({ phoneNumberId: { $exists: false } })
// Expected: 0
```

---

## Step 4: Create whatsapp_lines collection and seed default lines

The `WhatsappLineBootstrap` component seeds lines automatically on application startup.  
After deploying, verify the seed ran:

```javascript
db.whatsapp_lines.find({}, { phoneNumberId: 1, audience: 1, isDefault: 1, active: 1 })
```

Expected output (at minimum):
```
{ phoneNumberId: "<CUSTOMER_PHONE_ID>", audience: "CUSTOMER", isDefault: true, active: true }
{ phoneNumberId: "<DRIVER_PHONE_ID>",   audience: "DRIVER",   isDefault: false, active: true }
```

If the driver line is absent because `whatsapp.cloud.driverPhoneId` was not set, insert it manually:

```javascript
db.whatsapp_lines.insertOne({
  phoneNumberId: "<DRIVER_PHONE_NUMBER_ID>",
  displayNumber: "<DRIVER_PHONE_NUMBER_ID>",
  audience: "DRIVER",
  agentName: "driver_support",
  storeId: null,
  active: true,
  isDefault: false
})
```

---

## Step 5: Verify ai_agent_configs seeded

```javascript
db.ai_agent_configs.find({ active: true }, { agentName: 1, active: 1 })
// Expected: driver_support + customer_support both present
```

---

## Step 6: Rollback procedure

If a rollback is required:

1. Deploy the previous image.
2. The new fields (`phoneNumberId`, `agentName`, `storeId`) on `whatsapp_session` are additive — the old code ignores them. No schema rollback needed.
3. The `whatsapp_lines` and `whatsapp_line_audit` collections can remain.
4. Drop the new indexes only if they cause issues:

```javascript
db.whatsapp_session.dropIndex("idx_from_phoneNumberId")
db.ai_conversation_histories.dropIndex("idx_phone_agentName")
```
