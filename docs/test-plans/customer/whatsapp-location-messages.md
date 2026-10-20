---
userType: customer
Feature: WhatsApp inbound location-message handling — receive, persist to Firestore with meta, route to AI agent (CUSTOMER audience), and block from StoreAiAgent (AC-07)
App: izinga-messaging (WhatsApp webhook / Firestore)
Last verified: 2026-10-20 | Gate 1 automated (feature/whatsapp-location-messages) + Gate 2 full regression (release/1.16.0) | PASS
---

## Scope

Covers the full inbound path for a WhatsApp location share from a customer:

- `WhatsappWebhookPayload.Value.Message.Location#describe()` — natural-language description generation (all null/blank combinations)
- `WhatsappInboundEventHandler#handleLocationMessage()` — Firestore persistence of location message + AI-reply message
- AI-agent routing: CUSTOMER audience → `AiCustomerServiceAgent` (NOT `StoreAiAgent`)
- AI-agent absent path: location persisted to Firestore only; no AI reply attempted
- STORE audience location: routed to `StoreAiAgent`, not `AiCustomerServiceAgent`

Out of scope: location accuracy, Google Maps geocoding, downstream order address population, frontend display.

## Repos affected

| Repo | Work |
|---|---|
| ijudi-api / izinga-messaging | All new code; all unit tests |

## Known-good test fixtures

| Field | Value |
|---|---|
| Test location name | "Sandton City" |
| Test location address | "83 Rivonia Rd, Sandton" |
| Test latitude | -26.1076 |
| Test longitude | 28.0526 |
| Expected describe() full output | "Customer shared their location: Sandton City, 83 Rivonia Rd, Sandton (latitude: -26.1076, longitude: 28.0526)" |
| Expected describe() coords-only output | "Customer shared their location (latitude: -26.1076, longitude: 28.0526)" |
| FireStoreTextMessage.MessageType | LOCATION |

## Test cases

| ID | Description | Type | Input | Expected output | Branch covered |
|---|---|---|---|---|---|
| LOC-01 | describe() — all fields present | Unit | name + address + lat + lon | Full label + coords string | Main path |
| LOC-02 | describe() — name and address absent | Unit | null name, null address, lat + lon | Coords-only string, no "location:" label | Null name + null address |
| LOC-03 | describe() — name present, address absent | Unit | name + null address + lat + lon | Name-only label + coords | Null address |
| LOC-04 | describe() — address present, name absent | Unit | null name + address + lat + lon | Address-only label + coords | Null name |
| LOC-05 | describe() — latitude missing | Unit | name + address + null lat + lon | Label without coords | Null latitude |
| LOC-06 | describe() — blank name and address treated as absent | Unit | "  " name + "" address + lat + lon | Coords-only string | Blank strings |
| LOC-07 | describe() — both lat and lon null (AC-03) | Unit | name + address + null lat + null lon | Label only, no coords, no NPE | Both null |
| LOC-08 | handleInbound CUSTOMER audience + AI active — persists location and AI reply to Firestore (AC-04) | Unit | Location message, AI agent active, CUSTOMER audience | Firestore write for LOCATION type (with meta), then AI reply (sender=STORE) | Happy path |
| LOC-09 | handleInbound CUSTOMER audience + AI inactive — persists location only | Unit | Location message, no active AI agent | Only location message written to Firestore, no AI reply | AI-absent path |
| LOC-10 | handleInbound STORE audience — routes to StoreAiAgent (AC-06) | Unit | Location message, STORE audience | handleLocationMessage invoked; StoreAiAgent receives the describe() text | STORE audience branch |
| LOC-11 | handleInbound CUSTOMER audience — StoreAiAgent never called (AC-07) | Unit | Location message, CUSTOMER audience | StoreAiAgent.handleMessage never invoked | CUSTOMER audience guard |

## Integration test scenarios

None required at Gate 1 level; the Firestore persistence is tested via mocks in `WhatsappInboundEventHandlerTest`. Live E2E would require a real WhatsApp sandbox send and Firestore inspection — reserved for manual UAT.

## Regression checks

- [ ] Text message handling (non-location) still persisted to Firestore correctly
- [ ] AI-agent routing for text messages unchanged
- [ ] STORE audience text messages still routed to StoreAiAgent
- [ ] handlePreDispatchFlows called exactly once for CUSTOMER location messages (same as text path)

## Known gaps / Known defects

| Item | Severity | Location | Date found |
|---|---|---|---|
| Live E2E not run (Gate 1 — mocked tests only) | Low | Manual only | 2026-10-20 |

No open defects as of 2026-10-20.

## Validation commands

```bash
export JAVA_HOME=/Users/lindanimasinga/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home
cd /Users/lindanimasinga/Documents/GitHub/ijudi-api

# Scoped (fast iteration during development):
./mvnw -pl izinga-messaging -am test

# Final Gate check (mandatory — full unscoped reactor):
./mvnw test
```
