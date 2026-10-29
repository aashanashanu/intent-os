# IntentOS Buyer Scenario Guide

## Choose the Right Kind of Scenario

This guide contains both buyer journeys and controlled policy tests. They are **not all instructions that force a particular live model outcome**. The first deployed verification found real extraction defects; see [scenario-validation.md](scenario-validation.md) for measured results and the fixes. Do not treat every unexpected BLOCK as proof that the requested purchase was unsafe: first inspect the buyer-confirmed contract.

| Goal | Use | Expected live behavior |
| --- | --- | --- |
| Complete an ordinary purchase | 1, 9, 11 | A matching cart passes after the extracted boundaries are reviewed. |
| Show expensive or infeasible requirements | 3, 5, conflicting variant of 10 | BLOCK for an evaluated violating cart, or no selection if the agent declines it. |
| Show missing merchant evidence | 7, merchant-limit variant of 6 | HOLD if selected, or no selection; neither permits payment. |
| Guarantee extras removal or quantity repair | Controlled cases in 2 and 6 | Run the automated tests; live requests explicitly forbid these mistakes and often produce clean carts. |
| Guarantee bad specifications or MODIFY | Controlled cases in 4 and 8 | Run the automated tests with the stated contracts/candidates. A live agent may select the correct product. |

### Review the Extracted Boundaries

Before confirmation, inspect **Allowed product categories** and **Required product specifications**, not only goal and budget. The review screen allows category edits and removal/correction of incorrectly inferred specifications. Do not remove a genuine buyer requirement merely to get approval.

Use the actual seed categories: `laptop`, `support`, `tablet`, `monitor`, `keyboard`, `mouse`, `accessory`, `warranty`, `software`, `furniture`, `stationery`, `party`, `audio`, `camera`, `printer`, `storage`, `projector`. Product names, `electronics`, `notebooks`, and `party supplies` are not equivalent policy identifiers. An empty category list means no category restriction; do not clear it as a shortcut.

| Request | Category | Genuine required specifications |
| --- | --- | --- |
| 16GB laptop | `laptop` | `ram: 16GB` (case-insensitive key) |
| 32GB laptop | `laptop` | `ram: 32GB` |
| Tablet with 8GB RAM | `tablet` | `ram: 8GB` |
| Keyboard | `keyboard` | Only specifications explicitly requested and represented by catalog attributes |
| Ergonomic chair | `furniture` | Explicit requested attributes if correctly extracted, such as `material: mesh` |
| Three Five Pack Office Notebooks | `stationery` | No `quantity` attribute; quantity belongs to cart selection and the explicit quantity-cap fields |
| Birthday Party Kit for Twelve | `party` | No `for: Twelve` attribute; the full phrase is the product name |
| Optional 4K monitor | `monitor` | No hard `resolution` requirement unless the buyer actually requires it |

The model remains advisory and variable. Revised extraction instructions reduce these errors; buyer confirmation and deterministic policy checks remain necessary. Free-text quantity requests inform selection, but an enforceable maximum must be entered in **Limit applies to item / Maximum quantity** using the exact product name. Preferred attributes and some other contract fields still require API setup.

## The Story

**Your AI can decide. IntentOS makes sure it decides what you meant.**

A buyer asks a Shopping Agent to buy something. The Intent Agent translates the request into visible purchase boundaries. The Shopping Agent proposes a cart; deterministic backend checks decide whether it can proceed. IntentOS explains violations, attempts safe repairs, and preserves the buyer's control of payment. A verified PayPal webhook creates the AgentReceipt, and the Receipt Analyst explains the recorded evidence.

This is not a chatbot with unrestricted spending. It is an agent shopping journey with an enforceable payment boundary and an auditable outcome.

## Before Presenting

1. Deploy catalog version **2** and the buyer-first frontend. Local changes do not update the public example until deployed. The seed catalog is [../backend/src/main/resources/merchant/catalog.json](../backend/src/main/resources/merchant/catalog.json). `INTENTOS_MERCHANT_CATALOG` can override it; confirm the deployed version through the merchant products API.
2. Sign in with a PayPal Sandbox buyer. Confirm Bedrock extraction/shopping, PayPal credentials and webhook registration, and Elastic persistence. Agent Builder requires its own live acceptance; configuration alone does not prove its tools work.
3. Use **United States / California / Standard** unless stated otherwise. The prices below are simulator calculations, not real retailer offers or tax advice.
4. Start each scenario as a separate purchase request. For another attempt on the same intent, choose **New run**, then **Find my cart**. The backend accepts one initial model proposal per run.
5. Confirm the extracted goal, budget, categories, requirements, and permissions before **Confirm intent & find my cart**. Missing values must not be silently invented.
6. Finish checkout within the quote lifetime, normally five minutes. Catalog changes invalidate existing quotes; create fresh runs after deploying this catalog.

All products and shipping are simulated. PayPal Sandbox executes a test payment to the configured merchant; it does not place a physical order or process real money.

## Live Outcomes Versus Repeatable Tests

**Nova selection is not scripted.** It may choose a compliant alternative or return no eligible candidate rather than propose a bad cart. Avoiding a violation is also a valid result. An empty selection is not a recorded BLOCK; do not claim repair if none happened.

The expected outcomes below apply **when the listed items are proposed and the stated contract is confirmed**. Repeatable quote/policy scenarios use the real bundled catalog in [../backend/src/test/java/com/intentos/merchant/SeedCatalogScenarioTest.java](../backend/src/test/java/com/intentos/merchant/SeedCatalogScenarioTest.java). Shopping API tests exercise candidate selection and same-run repair with an explicitly mocked model.

For a guaranteed bad-agent walkthrough, show deterministic tests and identify them as tests. The UI has no manual candidate picker or seeded bad-cart mode. Do not secretly force model selection, weaken policy, or fabricate payment events to produce a recording.

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

**Controlled repair case, not a guaranteed live bad-cart request.** During deployed verification the request below selected only the laptop and correctly APPROVED. Use that result to show prevention through compliant selection; use the named automated test for reproducible extras removal. There is no live UI button to inject forbidden extras into the model's cart.

**Buyer request:**

```text
Buy one laptop for under $1,000 total, including tax and shipping. Buy only the laptop. Do not include a warranty, support plan, antivirus, accessories, subscriptions, or recurring payments. I have no additional hardware specification requirements.
```

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

**Controlled specification-violation case.** During deployed verification the live request selected Portable Computer 16 and APPROVED. The lower-spec and unknown-spec candidates below are deliberate test inputs, not the promised model selection.

**Buyer request:**

```text
Buy one laptop for software development with at least 16GB RAM under $1,000 total, including tax and shipping. Do not buy a laptop with less than 16GB RAM or unconfirmed memory specifications. No subscriptions or accessories.
```

`compact-computer:8gb-256gb` totals **$508.80**, but has only 8GB RAM. If proposed: **BLOCK / REQUIRED_ATTRIBUTE_NOT_MET**. `unconfirmed-laptop:specs-pending` totals **$681.60**, but has no RAM evidence; it also fails a required RAM check. The request does not force either bad candidate; Nova may correctly choose Portable Computer 16 instead.

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

**Controlled buyer-limit repair case.** During deployed verification the first request selected one keyboard and APPROVED at $39.00, so there was nothing to repair. Use the two-unit test input to exercise quantity correction repeatably.

**Buyer request:** “Buy one Everyday USB Keyboard under $100 total.” In intent review, set **Limit applies to item** to the exact product name **Everyday USB Keyboard** and **Maximum quantity** to **1**.

If two `usb-keyboard:wired` units are proposed, the merchant allows it but the buyer does not: **BLOCK / QUANTITY_EXCEEDED**. Safe repair clamps to one; California Standard total changes from **$66.00** to **$39.00** after re-quote.

Separately, six `mechanical-keyboard:wireless` units exceed the merchant maximum of five: **HOLD / QUANTITY_LIMIT**. Policy repair cannot override seller limits or create stock.

**Merchant-limit buyer request:**

```text
Buy six Wireless Mechanical Keyboards for the office under $600 total, including tax and shipping. I need all six units. No subscriptions or other products.
```

For this variant, do not set a buyer quantity limit below six. The seller's maximum of five is the intended boundary. Nova may decline the unavailable quantity rather than submit it.

## 7. Missing Evidence Means Hold

Use a sufficient budget and request the listing by name. If Nova refuses it, report “no safe candidate selected.” If selected, these quote outcomes apply:

Use each request below as a separate purchase. Do not combine incomplete listings into one cart: that would obscure which missing fact caused the HOLD.

| HOLD example | Exact buyer request text |
| --- | --- |
| Tax pending | Buy one Wireless Printer with Tax Pending under $250 total, including tax and shipping. Do not substitute another product. No subscriptions or accessories. |
| Purchase terms pending | Buy one Design Software with Purchase Terms Pending under $150 total, including tax and shipping. Only a one-time purchase is allowed. No subscriptions or recurring charges. |
| Billing interval pending | Find one Design Subscription with Billing Interval Pending with a maximum initial charge of $100 including tax and shipping. I allow subscriptions and recurring payments, but the billing interval must be confirmed before payment. |
| Stock pending | Buy one Portable SSD with Stock Pending under $150 total, including tax and shipping. Confirm availability before payment. No subscriptions or accessories. |
| Sold out | Buy one Sold Out 4K Webcam under $150 total, including tax and shipping. I need this exact product. Do not substitute another camera. |
| Insufficient stock | Buy two Limited Edition Noise Cancelling Headphones under $250 total, including tax and shipping. I need both units, not just one. No subscriptions or accessories. |
| Price pending | Buy one Portable Projector with Price Pending under $500 total, including tax and shipping. Confirm its price before payment. No subscriptions or accessories. |
| Unsupported destination, API/test only | Buy one Portable Computer 16 under $1,000 total, including tax and shipping, delivered to Texas in the United States using standard shipping. No subscriptions or accessories. |

For the billing-interval case, confirm both subscription and recurring-payment permissions are enabled; the intended missing fact is the interval, not forbidden recurring billing. For the Texas case, the request text does not configure delivery: submit `country: US`, `region: TX`, and `shippingMethod: standard` through the quote/proposal API. Texas is intentionally unavailable in the UI dropdown.

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

These quotes are **HOLD**, with no final total, tax, or shipping amount. Unknown does not mean zero. Incomplete listings are intentional example data, not catalog-load errors. The UI only offers supported delivery choices; unsupported destinations are API/test scenarios.

Say: “The agent cannot prove the final cost or purchase terms. IntentOS pauses instead of guessing.” Never proceed to PayPal on HOLD.

## 8. Preference Is Different from a Requirement

**API/test-only MODIFY case.** The live preference request selected Studio Monitor 27 and APPROVED at $292.80. It did not select the 1080p monitor and therefore did not exercise MODIFY. The original deployed extractor also incorrectly promoted the preference to a hard requirement; inspect the review and keep optional preferences out of hard specifications.

**Preference buyer request, API/test setup required:**

```text
Buy one monitor under $350 total, including tax and shipping. I prefer 4K resolution, but it is a preference rather than a hard requirement. No subscriptions or accessories.
```

For an API/test contract with category `monitor`, budget $350, and `preferredAttributes: {"resolution":"4K"}`, `desk-monitor:24inch-1080p` yields **MODIFY** with a recommendation. There is no hard budget violation, but checkout is not approved.

**Hard-requirement buyer request:**

```text
Buy one monitor with 4K resolution under $350 total, including tax and shipping. 4K is required; do not buy a 1080p monitor. No subscriptions or accessories.
```

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

**Premium-chair budget comparison request:**

```text
Buy one ergonomic mesh desk chair under $250 total, including tax and shipping. Do not exceed the budget for a premium version. No subscriptions or accessories.
```

**Party-kit category-boundary request:**

```text
Buy one Birthday Party Kit for Twelve under $80 total, including tax and shipping. Buy only the party kit. Do not include a speaker, electronics, subscriptions, or any other products.
```

The catalog also includes a wireless mouse, USB-C dock, monthly support, and monthly cloud backup. Subscription metadata shows recurring-charge detection; Orders v2 checkout here is not recurring billing/subscription management. Do not present an order as establishing a real recurring plan.

## 10. Clarification Before Shopping

**Buyer request:** “Buy me a good laptop.”

**Exact follow-up request after the agent asks for details:**

```text
Buy one laptop for software development with at least 16GB RAM. My maximum is $1,000 total including tax and shipping. No subscriptions, recurring payments, or accessories.
```

Replace the original request with that complete text before checking the updated request.

**Conflicting-boundaries variant:**

```text
Buy one laptop with at least 32GB RAM for no more than $500 total, including tax and shipping. Both the RAM requirement and the budget are mandatory. No subscriptions or accessories.
```

The Intent Agent should ask for missing/ambiguous boundaries. Inspect its actual questions; wording is model-dependent. No shopping run starts until sufficient detail is supplied and the buyer confirms the interpretation. Repeat with conflicting budget/specifications; do not claim identical model behavior every time.

Say: “Understanding intent includes admitting what the agent does not know.”

## 11. Payment, Receipt, and Explanation

Live verification of this request stops at approved cart review unless the buyer explicitly authorizes a Sandbox payment. An unpaid shopping run has decision evidence but no payment receipt; an analyst answer must not claim it was captured.

**Buyer request:**

```text
Buy one Portable Computer 16 with at least 16GB RAM under $1,000 total, including tax and shipping. No subscriptions, recurring payments, warranties, or accessories.
```

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

**Exact baseline buyer request for these safety checks:**

```text
Buy one Portable Computer 16 under $1,000 total, including tax and shipping. No subscriptions, recurring payments, warranties, or accessories.
```

Reuse the resulting intent only where the test requires it. Expiry, changed evidence, another user's session, invalid signatures, low confidence, explicit proposal flags, and repeated attempts must be established through the described test/API/provider setup; buyer wording alone cannot create those conditions. Refunds and fulfillment remain unsupported regardless of request text.

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
| Refunds, disputes, real inventory reservation, fulfillment | Not implemented by this example catalog. Do not claim them. |

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

Catalog tests load the actual bundled JSON and check exact totals, HOLD reasons, policy results, and repair boundaries. API tests verify same-run quote-backed repair/review. Service tests cover expired/changed quotes. Payment/webhook tests cover authorization and evidence handling. Automated success does not prove a completed live PayPal/Agent Builder walkthrough.