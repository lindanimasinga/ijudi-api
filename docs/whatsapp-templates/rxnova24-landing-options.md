# WhatsApp Template — `rxnova24_landing_options`

RxNova24's first-contact/landing template, parallel to `izinga_landing_options` but
store-scoped: 3 buttons — order from the website, track an order, get help.

## Why this is RxNova24-specific, not the shared `izinga_store_greeting`

A WhatsApp URL button's domain must be **static text** at template-submission time
— Meta only allows one dynamic variable, appended to a fixed base URL (e.g.
`https://example.com/{{1}}`), never a variable full domain. RxNova24 runs its own
custom storefront at `demo.rxnova24.co.za`, not a listing under a shared
`shop.izinga.co.za` domain, so its Order Now button can't be parameterized into a
generic multi-store template. This template is submitted once for RxNova24; a
different store on a shared iZinga domain could still use a dynamic-suffix
variant of `izinga_store_greeting` later if needed — not needed now.

## Meta submission spec

Submit via WhatsApp Manager or `POST /{waba-id}/message_templates`.

| Field | Value |
|---|---|
| Name | `rxnova24_landing_options` |
| Category | UTILITY |
| Language | `en` |
| Body | `Hi {{1}}! I'm RxNova24's WhatsApp assistant. Choose an option below, or just ask me about our products, hours, or location.` |
| Example (Meta review) | `{{1}}` → "Thandi" |
| Buttons | see below — CTA button(s) must be listed before quick-reply buttons |

### Buttons (3 total — Meta max, 1 CTA + 2 quick reply)

| # | Type | Button text | Behaviour |
|---|---|---|---|
| 1 | URL (static) | `Order Now` | Opens `https://demo.rxnova24.co.za` directly — no app logic involved, this is a native link button |
| 2 | QUICK_REPLY | `Track Order` | Sends "Track Order" back as a normal inbound message. `store_support_77db94fe-6cc6-4da9-bc62-5fa26045389c` has no order-tracking tool ([izinga-store-support-rxnova24.md](../../../izinga-onboarding/.claude/agents/izinga-store-support-rxnova24.md)) — it replies with the existing redirect: "For order tracking, please check https://demo.rxnova24.co.za or message our Customer Support line." No new capability needed — the quick reply just surfaces the option, the agent's existing boundary handles the reply. |
| 3 | QUICK_REPLY | `Help` | Sends "Help" back as a normal inbound message. The agent treats this like any ambiguous/general opener — answers with what it can do (products, hours, location) or escalates to the store team per its existing Escalation section. |

Button text is within Meta's ~20-char limit on each.

## Meta template creation payload

```json
{
  "name": "rxnova24_landing_options",
  "category": "UTILITY",
  "language": "en",
  "components": [
    {
      "type": "BODY",
      "text": "Hi {{1}}! I'm RxNova24's WhatsApp assistant. Choose an option below, or just ask me about our products, hours, or location.",
      "example": { "body_text": [["Thandi"]] }
    },
    {
      "type": "BUTTONS",
      "buttons": [
        { "type": "URL", "text": "Order Now", "url": "https://demo.rxnova24.co.za" },
        { "type": "QUICK_REPLY", "text": "Track Order" },
        { "type": "QUICK_REPLY", "text": "Help" }
      ]
    }
  ]
}
```

## Send-request shape (runtime call)

None of the three buttons carry a dynamic part (the URL is fully static, both
quick replies are fully static), so the outbound send call needs no `BUTTONS`
component — same pattern as the existing `izinga_landing_options` send call in
`WhatsappNotificationService.sendLandingOptions()`, just the template name and a
`storeId`/audience-appropriate sender line:

```json
{
  "name": "rxnova24_landing_options",
  "language": { "code": "en" },
  "components": [
    {
      "type": "BODY",
      "parameters": [
        { "type": "TEXT", "text": "#name" }
      ]
    }
  ]
}
```

Suggested call site: a `sendStoreLandingOptions(String mobileNumber, String customerName)`
method alongside `sendLandingOptions()`, sent via the RxNova24 store line's own
`phoneNumberId` (per WA-LINES-01's line-scoped sender resolution) rather than the
default iZinga support line — and as the first-contact message when a new
conversation opens on that number, same rule as the existing driver-line
first-contact requirement.

## What happens after each tap

- **Order Now** — customer leaves WhatsApp for `demo.rxnova24.co.za`; no bot
  involvement, this is the fastest path to an actual purchase.
- **Track Order** / **Help** — both land back in the same WhatsApp conversation as
  a normal customer message, handled by `store_support_77db94fe-6cc6-4da9-bc62-5fa26045389c`
  under its existing rules — no agent-prompt or MCP-tool changes needed for this
  template to work.

No `ijudi-api` code was changed to produce this file — it's a submission spec and
reference doc. Submitting it to Meta and wiring the send call is Backend
Developer / DevOps work once you're ready to provision RxNova24's line.
