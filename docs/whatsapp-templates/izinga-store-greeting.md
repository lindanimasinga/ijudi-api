> **Superseded for RxNova24** — Lindani asked for a 3-button version (Order Now /
> Track Order / Help). A URL button's domain must be static per Meta's rules, and
> RxNova24 has its own domain (`demo.rxnova24.co.za`) unlike a generic PRO store —
> so the buttoned version is RxNova24-specific, not shared. See
> [rxnova24-landing-options.md](./rxnova24-landing-options.md). This file is kept
> as the reference pattern for a future plain-greeting template on stores that
> don't have their own custom domain (e.g. ones living under `shop.izinga.co.za`).

# WhatsApp Template — `izinga_store_greeting` (RxNova24 pilot)

## Why this isn't `rxnova24_landing_options`

`izinga_landing_options` is the **generic support line** template — it presents a
Driver/Customer menu because that line serves two audiences. A PRO store line
(RxNova24 included) is single-purpose per [[izinga-store-support-rxnova24.md]] — "there is
no driver/customer menu to choose from" — so a menu-shaped template doesn't fit.

More importantly: `store_support_default` (WA-LINES-02) is architected as **one
prompt cloned per store**, not one prompt per store rewritten from scratch — that's
the whole point of the clone mechanism (`store_support_<storeId>`). WhatsApp
Business templates should follow the same pattern. Meta template approval is a
manual review (hours to days) — submitting a brand-new template per store doesn't
scale to "every PRO store gets a line" the way the AI agent clone does.

So this is **one shared template, parameterized by store name**, submitted to Meta
once and reused for RxNova24 today and every future PRO store line — same shape as
`izinga_landing_options`, just with a second variable.

## Meta submission spec

Submit via WhatsApp Manager (Business Manager) or `POST /{waba-id}/message_templates`.

| Field | Value |
|---|---|
| Name | `izinga_store_greeting` |
| Category | UTILITY |
| Language | `en` |
| Body | `Hi {{1}}! I'm {{2}}'s WhatsApp assistant — happy to help with our products, hours, or location.` |
| Example (for Meta review) | `{{1}}` → "Thandi", `{{2}}` → "RxNova24" |

This is a direct lift of the AI-disclosure line already approved in the store
agent's system prompt ([izinga-store-support-rxnova24.md](../../../izinga-onboarding/.claude/agents/izinga-store-support-rxnova24.md)) —
the template and the agent's own first-message disclosure say the same thing, so
whichever fires first (business-initiated template vs. agent-generated reply) is
consistent.

No buttons/quick-replies — a store line doesn't need a menu, just the warm
disclosure. If a future store type genuinely needs first-contact options, that's a
new template, not an overload of this one.

## Send-request shape (for when this is wired up in `WhatsappNotificationService`)

Same JSON envelope as `sendLandingOptions()`'s call to `izinga_landing_options`,
with the second BODY parameter added for the store name:

```json
{
  "name": "izinga_store_greeting",
  "language": { "code": "en" },
  "components": [
    {
      "type": "BODY",
      "parameters": [
        { "type": "TEXT", "text": "#name" },
        { "type": "TEXT", "text": "#storeName" }
      ]
    }
  ]
}
```

Suggested call site: a `sendStoreGreeting(String mobileNumber, String customerName, String storeName)`
method alongside `sendLandingOptions()` in `WhatsappNotificationService`, invoked
the same way a new-number/new-conversation template send is required outside the
24-hour service window (per WA-LINES-01's "first contact must be a template" rule).
`storeName` should come from `StoreProfile.name` — no hardcoding per store.

## RxNova24 concretely

For RxNova24, this template renders as:

> Hi Thandi! I'm RxNova24's WhatsApp assistant — happy to help with our products, hours, or location.

No code in `ijudi-api` was changed to produce this file — it's a submission spec
and reference doc. Submitting it to Meta and wiring the send call is Backend
Developer / DevOps work once you're ready to provision RxNova24's line.
