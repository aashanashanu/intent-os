# IntentOS Buyer Demo and Scenario Guide

## The Story

**Your AI can decide. IntentOS makes sure it decides what you meant.**

A buyer asks a Shopping Agent to buy something. The Intent Agent translates the request into visible purchase boundaries. The Shopping Agent proposes a cart; deterministic backend checks decide whether it can proceed. IntentOS explains violations, attempts safe repairs, and preserves the buyer's control of payment. A verified PayPal webhook creates the AgentReceipt, and the Receipt Analyst explains the recorded evidence.

This is not a chatbot with unrestricted spending. It is an agent shopping journey with an enforceable payment boundary and an auditable outcome.

## Before Presenting

1. Deploy catalog version **2** and the buyer-first frontend. Local changes do not update the public demo until deployed. The seed catalog is [../backend/src/main/resources/merchant/catalog.json](../backend/src/main/resources/merchant/catalog.json). `INTENTOS_MERCHANT_CATALOG` can override it; confirm the deployed version through the merchant products API.
2. Sign in with a PayPal Sandbox buyer. Confirm Bedrock extraction/shopping, PayPal credentials and webhook registration, and Elastic persistence. Agent Builder requires its own live acceptance; configuration alone does not prove its tools work.
3. Use **United States / California / Standard** unless stated otherwise. The prices below are simulator calculations, not real retailer offers or tax advice.
4. Start each scenario as a separate purchase request. For another attempt on the same intent, choose **New run**, then **Find my cart**. The backend accepts one initial model proposal per run.
5. Confirm the extracted goal, budget, categories, requirements, and permissions before **Confirm intent & find my cart**. Missing values must not be silently invented.
6. Finish checkout within the quote lifetime, normally five minutes. Catalog changes invalidate existing quotes; create fresh runs after deploying this catalog.

All products and shipping are simulated. PayPal Sandbox executes a test payment to the configured merchant; it does not place a physical order or process real money.

## Live Outcomes Versus Repeatable Tests

**Nova selection is not scripted.** It may choose a compliant alternative or return no eligible candidate rather than propose a bad cart. Avoiding a violation is also a valid result. An empty selection is not a recorded BLOCK; do not claim repair if none happened.

The expected outcomes below apply **when the listed items are proposed and the stated contract is confirmed**. Repeatable quote/policy scenarios use the real bundled catalog in [../backend/src/test/java/com/intentos/merchant/SeedCatalogScenarioTest.java](../backend/src/test/java/com/intentos/merchant/SeedCatalogScenarioTest.java). Shopping API tests exercise candidate selection and same-run repair with an explicitly mocked model.

For a guaranteed bad-agent demonstration, show deterministic tests and identify them as tests. The UI has no manual candidate picker or seeded bad-cart mode. Do not secretly force model selection, weaken policy, or fabricate payment events to produce a recording.

## The Buyer Journey

| Buyer sees | Actual behavior | What to explain |
| --- | --- | --- |
| **What would you like me to buy?** | Buyer enters the request and delivery choices. | Start with the goal, not a contract form. |
| **Start shopping** | Intent Agent extracts a draft or asks for clarification. | Interpretation is visible, not assumed. |
| **Here is what I understood** | Buyer confirms goal, budget, permissions, and extracted specifications/categories. | These are the boundaries the cart must satisfy. |
| **Confirm intent & find my cart** | Intent saved; Nova selects catalog IDs; Java calculates the quote and checks policy. | The agent proposes; it cannot authorize its own payment. |
| Approval, findings, or a pause | Actual deterministic result and supporting evidence. | Explain why this cart fits or fails the intent. |
| **Ask IntentOS to repair this cart** | Safe correction, fresh quote, revalidation; user review may still be necessary. | Repair does not relax the intent to force success. |
| **I have reviewed this cart** | Exact-revision review, only for an approved fresh quote. | Policy approval is not buyer payment approval. |
| **Continue to PayPal Sandbox** | Server creates an itemized order; buyer follows the approval link. | PayPal executes the approved transaction. |
| **Check approval and capture** | Server rechecks eligibility and PayPal buyer approval before capture. | No direct AI-to-capture shortcut. |
| Waiting for evidence, then AgentReceipt | Verified `PAYMENT.CAPTURE.COMPLETED` webhook finalizes the receipt. | A capture response is not the final receipt. |
| Receipt Analyst answer | Retrieved receipt and decisions ground the explanation. | Inspect citations and tool execution, not just fluent narration. |

## 1. A Purchase That Matches Intent

**Buyer request:** “Find me a laptop for software development with at least 16GB RAM under $1,000 total, including tax and shipping. No subscriptions or accessories.”

Confirm $1,000 maximum, category `laptop`, RAM `16GB`, and recurring payments/subscriptions disabled. Candidate `portable-computer:16gb-512gb` is **Portable Computer 16**, $780, with 16GB RAM and 512GB storage.

| Cost | California Standard |
| --- | ---: |
| Laptop | $780.00 |
| Shipping | $12.00 |
| Tax | $62.40 |
| Total | **$854.40** |

Expected if selected: **APPROVE**. Review, create the Sandbox order, approve in PayPal, capture, and wait for the verified receipt.

Say: “The selected laptop meets your recorded specifications and the full cost is below your limit. You still decide whether to pay.”

## 2. Block Extras, Repair, and Recheck

Use a laptop-only intent with $1,000 maximum, allowed category `laptop`, no required attributes for this isolated repair example, and recurring payments/subscriptions disabled. If the proposed cart contains:

| Candidate | Price |
| --- | ---: |
| `portable-computer:16gb-512gb` | $780.00 |
| `device-warranty:2year` | $199.00 |
| `antivirus-plan:annual` | $79.00 |
| California Standard shipping | $12.00 |
| Tax on $1,058 of products | $84.64 |
| Initial total | **$1,154.64** |

Expected: **BLOCK** for budget, forbidden categories, recurring payment, and subscription. Safe repair removes warranty and antivirus; the fresh quote is **$854.40** and passes. The reduction is **$300.24**, including recalculated tax, not a separate refund or charge.

Say: “The cart included coverage and an annual subscription outside your laptop-only request. IntentOS stopped payment, removed the disallowed extras, recalculated the quote, and checked again.”

The live agent may never add these extras; show that honestly. The existing repeatable API-test variant is laptop plus `support-plan:monthly`: **$874.92** before repair, **$854.40** after re-quote.

**Implementation boundaries:** catalog-backed items currently hydrate with `addOn=false`. The warranty is removed because it is outside the allowed category, not because its name makes it an add-on. `UNREQUESTED_ADDON` is separately tested with explicit proposal flags. Required attributes apply to every non-add-on item: a RAM requirement on a mixed laptop/software cart can introduce a non-repairable specification violation instead of the simple removal path.

## 3. Core Product Is Too Expensive

**Buyer request:** “Buy a laptop with at least 32GB RAM under $1,000 total. No subscriptions.”

`workstation-computer:32gb-1tb` costs $1,120; California Standard total is **$1,221.60**. If proposed: **BLOCK / BUDGET_EXCEEDED**. Repair does not replace the core product with a cheaper laptop or increase the budget. User review/new proposal is required. Nova may return no eligible match instead.

Say: “These specifications and this budget cannot both be met by the selected product. IntentOS will not quietly lower your requirements or spend more.”

## 4. Cheap Does Not Mean Compliant

Use the first scenario's intent. `compact-computer:8gb-256gb` totals **$508.80**, but has only 8GB RAM. If proposed: **BLOCK / REQUIRED_ATTRIBUTE_NOT_MET**. `unconfirmed-laptop:specs-pending` totals **$681.60**, but has no RAM evidence; it also fails a required RAM check.

The unconfirmed laptop has complete price, stock, and purchase terms: its quote can be READY while the intent decision is BLOCK. “Quote complete” is not “purchase approved.” Repair must not invent specifications.

## 5. Delivery Pushes the Cart Over Budget

**Buyer request:** “Buy a laptop with 16GB RAM for no more than $860 including delivery and tax.”

| Portable Computer 16 destination | Total | Decision under $860 |
| --- | ---: | --- |
| California / Standard | $854.40 | APPROVE |
| California / Express | $872.40 | BLOCK |
| New York / Standard | $869.82 | BLOCK |

Say: “The product price fits, but the complete purchase does not. IntentOS checks the full quote.” For a delivery change, create a **New run** and obtain fresh quote/review; repair does not automatically change delivery rules.

## 6. Buyer Quantity Limit Versus Merchant Limit

**Buyer request:** “Buy one Everyday USB Keyboard under $100 total.” In intent review, set **Limit applies to item** to the exact product name **Everyday USB Keyboard** and **Maximum quantity** to **1**.

If two `usb-keyboard:wired` units are proposed, the merchant allows it but the buyer does not: **BLOCK / QUANTITY_EXCEEDED**. Safe repair clamps to one; California Standard total changes from **$66.00** to **$39.00** after re-quote.

Separately, six `mechanical-keyboard:wireless` units exceed the merchant maximum of five: **HOLD / QUANTITY_LIMIT**. Policy repair cannot override seller limits or create stock.

## 7. Missing Evidence Means Hold

Use a sufficient budget and request the listing by name. If Nova refuses it, report “no safe candidate selected.” If selected, these quote outcomes apply:

| Candidate | Missing fact / request | Evidence reason |
| --- | --- | --- |
| `tax-pending-printer:wireless` | Tax treatment unknown | `UNKNOWN_TAX_TREATMENT` |
| `terms-pending-software:license-pending` | One-time versus recurring unknown | `UNKNOWN_PURCHASE_TERMS` |
| `interval-pending-plan:billing-pending` | Billing interval unknown | `UNKNOWN_BILLING_INTERVAL` |
| `stock-pending-drive:1tb` | Stock unknown | `INSUFFICIENT_STOCK` |
| `sold-out-camera:4k` | Stock is zero | `INSUFFICIENT_STOCK` |
| `limited-headphones:noise-cancelling`, quantity 2 | Only one in stock | `INSUFFICIENT_STOCK` |
| `price-pending-projector:1080p` | Price unknown | `INVALID_PRICE` |
| Any listing sent through API with US / TX / Standard | No destination rule | `DESTINATION_SHIPPING_RULE_UNAVAILABLE` |

These quotes are **HOLD**, with no final total, tax, or shipping amount. Unknown does not mean zero. Incomplete listings are intentional demo data, not catalog-load errors. The UI only offers supported delivery choices; unsupported destinations are API/test scenarios.

Say: “The agent cannot prove the final cost or purchase terms. IntentOS pauses instead of guessing.” Never proceed to PayPal on HOLD.

## 8. Preference Is Different from a Requirement

For an API/test contract with category `monitor`, budget $350, and `preferredAttributes: {"resolution":"4K"}`, `desk-monitor:24inch-1080p` yields **MODIFY** with a recommendation. There is no hard budget violation, but checkout is not approved.

The hard-required version (`requiredAttributes`) yields BLOCK. Repair does not choose a different monitor to satisfy a preference. `studio-monitor:27inch-4k` is the complete 4K alternative, **$292.80** California Standard.

This is an API/test case: the current frontend does not persist preferred attributes from extraction. Do not claim a free-text preference is enforced without checking the saved contract.

## 9. Everyday Purchases Beyond Laptops

Complete one-time listings, with California Standard totals if the matching product is selected and the confirmed intent permits it:

| Buyer request | Candidate | Total |
| --- | --- | ---: |
| “Buy a tablet with 8GB RAM under $400 total.” | `travel-tablet:8gb-128gb` | $357.60 |
| “Buy a wireless mechanical keyboard under $100 total.” | `mechanical-keyboard:wireless` | $93.00 |
| “Buy an ergonomic mesh desk chair under $250 total.” | `desk-chair:ergonomic` | $206.40 |
| “Buy three Five Pack Office Notebooks under $80 total.” | `notebook-pack:5pack`, quantity 3 | $60.60 |
| “Buy a Birthday Party Kit for Twelve under $80 total.” | `party-kit:12guests` | $60.60 |

The premium chair totals **$573.60** and fails the $250 chair budget. A party speaker is a different category from the party kit; whether allowed must come from the buyer's confirmed contract, not an assumption that all party-related products are requested.

The catalog also includes a wireless mouse, USB-C dock, monthly support, and monthly cloud backup. Subscription metadata demonstrates recurring-charge detection; Orders v2 checkout here is not recurring billing/subscription management. Do not present an order as establishing a real recurring plan.

## 10. Clarification Before Shopping

**Buyer request:** “Buy me a good laptop.”

The Intent Agent should ask for missing/ambiguous boundaries. Inspect its actual questions; wording is model-dependent. No shopping run starts until sufficient detail is supplied and the buyer confirms the interpretation. Repeat with conflicting budget/specifications; do not claim identical model behavior every time.

Say: “Understanding intent includes admitting what the agent does not know.”

## 11. Payment, Receipt, and Explanation

Use an approved cart. Review, create the Sandbox order, follow buyer approval, return, and capture. Distinguish **policy approved**, **capture response received**, and **verified receipt ready**.

Show the AgentReceipt's original request, proposal history, findings/repairs, final amount, PayPal order/capture IDs, and webhook ID. Search by actual product or PayPal ID. If this run has no repairs, say so; do not attribute another run's prevention amount to it.

Ask the Receipt Analyst:

- “Why was this purchase approved?”
- “What did the agent change before payment?”
- “Did this run contain subscription violations?”
- “What amount did PayPal confirm, and what evidence supports it?”

Queries are scoped to the currently accessible intent, not every user's purchases. For Agent Builder, inspect completed `toolCalls`, citations, and `AGENT_BUILDER_SECURED_MCP`. Bedrock-grounded narration is a different mode, not proof of Agent Builder tool execution.

## Additional Safety Cases

These are API/test/provider checks, not outcomes a catalog alone can guarantee:

| Scenario | Boundary |
| --- | --- |
| Expired quote or changed catalog | Fresh quote required; stale evidence cannot authorize checkout. |
| Changed cart/revision after review | New exact-revision review required. |
| Buyer has not approved PayPal order | Server refuses capture. |
| Duplicate verified webhook | Idempotent receipt write; no duplicate receipt. |
| Invalid/unsigned webhook | Rejected before payment-state mutation. |
| Another user requests an intent, run, or receipt | Owner authorization denies access. |
| Unknown model-selected candidate | Fail closed; invented products cannot authorize payment. |
| Non-repairable core-product violation | User decision/new proposal, not silent requirement relaxation. |
| Repeated repair attempts | At most three persisted attempts; many cases stop earlier. |
| Low-confidence contract | HOLD; not product-specific. |
| Explicit add-on flag, prohibited item, restricted merchant | Deterministic rules; some fields require API/test setup. All seed products share one merchant. |
| Refunds, disputes, real inventory reservation, fulfillment | Not implemented by this demo catalog. Do not claim them. |

## Sub-Three-Minute Recording

| Time | Show | Narration |
| --- | --- | --- |
| 0:00-0:20 | Request and agent journey | “Ask an agent to buy something. IntentOS keeps the purchase true to your intent.” |
| 0:20-0:40 | Extracted goal, budget, requirements, permissions | “Confirm what the agent understood before it shops.” |
| 0:40-1:10 | Real cart and actual check result | “The Shopping Agent proposes. Java calculates the full cost and enforces your boundaries.” |
| 1:10-1:30 | A real repair if one occurs, or a separately labeled test result | “Extras can be removed and re-quoted. Core requirements are not quietly weakened.” |
| 1:30-2:05 | Exact review, Sandbox approval, capture | “You control payment. PayPal is the execution boundary, not a tool the model can call freely.” |
| 2:05-2:30 | Receipt after the genuine webhook arrives | “Intent, decisions, and authoritative payment evidence are joined in one record.” |
| 2:30-2:50 | Evidence-grounded analyst answer | “Ask why. The answer comes from evidence, not a story invented after the fact.” |

If delivery is delayed, resume recording once the genuine receipt arrives; never represent a simulated event as the real capture webhook. Keep credentials, account details, capability tokens, and unrelated provider screens out of the recording.

## Repeatable Verification

```sh
./backend/gradlew -p backend test \
  --tests com.intentos.merchant.SeedCatalogScenarioTest \
  --tests com.intentos.merchant.SimulatedMerchantApiTest \
  --tests com.intentos.merchant.SimulatedMerchantServiceTest \
  --tests com.intentos.repair.IntentRepairServiceTest \
  --tests com.intentos.shopping.ShoppingCheckoutControllerTest \
  --tests com.intentos.paypal.PayPalWebhookControllerTest
```

Catalog tests load the actual bundled JSON and check exact totals, HOLD reasons, policy results, and repair boundaries. API tests verify same-run quote-backed repair/review. Service tests cover expired/changed quotes. Payment/webhook tests cover authorization and evidence handling. Automated success does not prove a completed live PayPal/Agent Builder demonstration.