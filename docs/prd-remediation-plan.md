# PRD Remediation Plan

Status: implementation in progress; release acceptance remains open. This plan tracks all seven findings from the PRD implementation audit. It supersedes completion claims for the affected release gates, not the owner's previously reported live-provider verification.

Implementation progress: source now has configurable simulator inventory/quotes, bounded Nova candidate selection, immutable run/revision lineage, same-run repair, final review binding, simulator-backed PayPal order creation, capture-time checks, run-scoped receipt evidence, owner-scoped run history, corrected logical-run metrics, decision-only analyst evidence, and active Channel3/Catalog discovery removal. See [simulated-merchant.md](simulated-merchant.md). Container durability, reservation/concurrency, fully complete pagination, semantic verification, secured Agent Builder tool invocation, detailed receipt UI, live-provider verification, and deployment acceptance remain open.

## Confirmed Decisions

- Nova Lite through Bedrock will power automatic search, comparison, selection, and repair over a simulated merchant catalog. The user reviews the final cart before mandatory PayPal buyer approval. Channel3 is removed from the target architecture.
- Simulator-issued product facts, explicit recurring/subscription terms, inventory, and calculated tax/shipping quotes are accepted as labeled simulation evidence only. Missing facts produce `HOLD`; model or user assumptions cannot replace a quote.
- Receipt Analyst tools enforce user scope in backend code through a secured integration. The model cannot supply or override the authorization scope.
- Configurable simulator products, prices, explicit terms, inventory, tax rules, and shipping rates are permitted runtime demo data. Store them as merchant data, not constants embedded in agent/UI decision logic. No fixed winner, forced violation, scripted repair, or predetermined successful journey.
- Mock transport responses remain test-only. The running simulator is a disclosed service, not a hidden successful-response fallback. Never simulate PayPal buyer approval, capture, signature verification, or authoritative payment history.
- UI, receipt evidence, exports, analyst answers, and demo must distinguish simulated facts from actual PayPal evidence: "Simulated merchant catalog and quotes. Real PayPal Sandbox payment lifecycle. No physical order or retailer settlement."

## Finding Coverage

| Audit finding | Work packages | Required outcome |
| --- | --- | --- |
| 1. Disconnected repair and payment journey | R1, R3 | One persisted proposal lineage from discovery through repair, review, capture, and receipt |
| 2. No autonomous shopping agent | R2, R3 | Model-assisted search, selection, and bounded repair over provider-backed candidates |
| 3. Repair retains removed-item evidence | R1, R3 | Each revision contains evidence only for its current items; historical evidence remains immutable |
| 4. Receipt evidence/capture attribution mixes proposals | R1, R4, R5 | Receipt and metrics reference the captured proposal revision explicitly |
| 5. Unverified payment facts and expiry at capture | R0, R2, R4 | Unknown facts hold; current authorization is checked again immediately before capture |
| 6. Active-intent-only intelligence and weak retrieval | R5, R6 | Owner-scoped historical search, date filters, decision-only explanations, reopenable intents |
| 7. Partial Elastic behavior and receipt-detail UX | R5, R6, R7 | Observable semantic relevance, secured analyst tools, complete receipt/timeline UI |

## Delivery Order

R0 merchant simulator -> R1 lineage -> R2 Nova shopping agent -> R3 repair/review -> R4 payment/receipt -> R5 history/metrics -> R6 analyst -> R7 integrated UI -> R8 final cleanup/release verification. Remove obsolete code alongside each replacement; R8 is a final sweep, not a reason to defer cleanup indefinitely.

Ship and test each work package before proceeding. R5/R6 must use the lineage identifiers established in R1. Keep the existing payment authority in Java throughout; do not introduce model-authorized payments while migrating.

### R0: Build the Simulated Merchant Evidence Service

- Implement a bounded merchant simulator with search/detail, availability, and cart-quote APIs. It can share the current deployment to control cost, but must expose a service contract rather than an inline canned-cart helper.
- Store configurable products/variants, stable IDs, category/attributes, prices, stock, explicit one-time/subscription terms, quantity limits, and tax/shipping rules. A labeled simulation seed dataset is allowed; modifying it must affect results without editing agent logic and must not force a chosen product or outcome.
- Compute quotes for exact items, variants, quantities, destination, and shipping method. Include item/tax/shipping breakdown, currency, quote ID, catalog/rules version, timestamps, expiry, and simulation provenance. Configured simulation tax rules are not actual retailer rates or legal tax advice.
- Protect simulator administration; persist catalog and quote state needed across restarts. Record source=simulated-merchant and simulation=true in run/evidence/receipt data, with corresponding PayPal metadata where supported.
- Store evidence provenance, retrieval time, validity, and explicit unknown states. Distinguish zero, false, and unknown; never convert missing values to zero or one-time purchase.
- Gate order creation on a complete quote. A shipping destination may be supplied by the user to obtain the quote; amounts must come from the quote source.
- If the simulator lacks a required fact, rule, quote, or stock, HOLD or return a documented unavailable result. Never fill unknowns silently. Simulator quotes apply only to simulator-owned products, not external retailer URLs.

**Exit tests:** Missing tax, shipping, subscription terms, required attribute, or expired quote each returns HOLD with a machine-readable missing-evidence reason. Complete provider evidence can proceed to policy evaluation. Changed destination/variant/quantity invalidates the quote.

### R1: Persist Immutable Cart Lineage

- Introduce a server-owned shopping run ID, stable proposal lineage ID, immutable revision ID/number, parent revision, item IDs, and selected offer/variant/quote IDs.
- Persist candidates considered, selection rationale, model/task metadata, validation results, repair actions, timestamps, and final-review binding. Model output may reference supplied candidate IDs only; reject unknown IDs or invented fields.
- Bind validation events, orders, capture state, webhook processing, and receipts to the exact approved revision. Do not infer the captured proposal from the first receipt snapshot.
- During repair, retain old revision evidence for audit but rebuild the current item-to-offer evidence set; additions/replacements require verified provider evidence and fresh quotes.
- Define migration behavior for existing receipts lacking lineage: preserve them as legacy evidence, flag missing attribution, and never invent ancestors or rewrite immutable payment history.

**Exit tests:** Two unrelated runs under the same intent remain isolated; removing two items leaves one current evidence entry; repeated validation cannot mutate an earlier revision; legacy data remains readable without fabricated lineage.

### R2: Implement the Actual Shopping Agent

- Add a backend shopping-run orchestration service and a configurable candidate-generation model role, initially Nova Lite. Keep provider keys and model calls server-side.
- Let the model formulate search queries, compare provider-backed candidates, and propose a cart using candidate IDs. Java hydrates cart facts from provider responses, calculates totals, and evaluates policy.
- Treat merchant descriptions and model responses as untrusted data. Ignore instructions embedded in listings; validate response schemas and reject invented offers, prices, attributes, or payment actions.
- Bound searches, candidates, model rounds, elapsed time, and token/API spend. Expose progress and a durable outcome rather than silently falling back to a fixed cart.
- Replace runtime sample-cart/repair and manual product-selection checkout with the Nova-controlled simulator-tool loop. Remove estimated Catalog prices and Channel3 search paths. If no eligible product exists, explain the gap rather than selecting a canned laptop.

**Exit tests:** Different requests/provider results produce appropriate different proposals. The run does not require per-item manual selection. Empty results, malformed model JSON, provider failures, injected listing instructions, and budget exhaustion fail safely without PayPal calls.

### R3: Connect Repair, Final Review, and Checkout

- Feed structured policy findings back into the same shopping run. Produce new cart revisions by removing disallowed items, correcting quantities, or searching verified alternatives.
- Cap automatic repair at three attempts per run, persisted across requests/restarts. Never increase budget, relax permissions, or substitute missing terms to force approval.
- Re-quote revised carts, revalidate each revision, and stop at APPROVE, unresolved HOLD, or review-required exhaustion.
- Add final user cart review bound to the exact revision and quote. Any material change invalidates review and requires the user to review again before creating an order.
- Buyer approval in PayPal remains mandatory even after IntentOS cart review.

**Exit tests:** An initially noncompliant offer-backed cart is blocked and repaired within one lineage, then the approved revision reaches checkout. Failed repair reaches review, not a synthetic success. Replaying an old review or editing client-side prices cannot authorize a new revision.

### R4: Correct Payment and Receipt Integrity

- Create orders from persisted, server-approved/reviewed revisions rather than accepting arbitrary client cart facts as authority. Recheck provider quote/offer freshness before order creation.
- Before capture, recheck ownership, intent expiration, approved revision, reviewed amount/currency, quote validity, and current payment eligibility. If material facts changed, stop and request re-quote/review; do not silently capture a stale authorization.
- Preserve PayPal approval verification, stable idempotency keys, lost-response reconciliation, and strict capture amount/currency/order binding.
- Build each payment receipt from its captured lineage only. Include original and final carts, revisions, violations, repairs, quote/offer provenance, approval/review evidence, capture/webhook IDs, and timestamps.
- Make capture claims, webhook deduplication, and receipt uniqueness durable and safe across concurrent deliveries, multiple app instances, restarts, and partial storage failures. A retry must complete missing writes without a second capture or duplicate receipt.

**Exit tests:** Expiry after order creation prevents capture. Webhook-before-response and response-before-webhook converge on one result. Two captured runs under one intent have disjoint purchase histories and correct attribution. Invalid signatures, conflicting capture amounts, duplicate/concurrent deliveries, and interrupted writes never forge payment evidence.

### R5: User History, Search, and Accurate Metrics

- Add authenticated owner-scoped list/detail/search APIs across intents and shopping runs, with date/status/violation/product/merchant filters and bounded pagination. Keep intent-scoped APIs for drill-down.
- Retrieve complete or explicitly paginated data for analytics; remove silent 100-receipt and 10,000-event truncation. Signal incomplete/legacy evidence rather than returning authoritative-looking partial totals.
- Define proposal denominators by logical lineage, not retry events or revisions. Store violation facts independently of decision so HOLD-with-violations is counted correctly.
- Document compliance as approved lineages with a violation-free final validated revision, divided by all evaluated lineages; separately expose first-pass compliance and repaired approvals so a repaired journey is not misrepresented. Reconcile this definition explicitly with the PRD before closing the metrics gate.
- Compute prevented amount only when an original amount and final approved amount for the same lineage exist. Do not treat an unresolved blocked cart's full value as saved spend; report blocked exposure separately. Captured spend comes only from authoritative captures, deduplicated by capture ID.
- Use a supported explicit semantic mapping/inference configuration with migration/backfill for existing receipts. Rank nonempty searches by relevance with stable tie-breaking, not recency alone. Expose semantic/hybrid/lexical/degraded mode and mapping/inference errors without leaking credentials.
- Include decision-only runs in historical analysis without fabricating a payment receipt for blocked proposals. Preserve lexical local-mode behavior as lexical, not semantic.

**Exit tests:** Cross-user searches return no foreign results; month/date filters work; totals remain correct beyond old limits. Test multi-run/multi-capture, repeated validation, HOLD-with-violations, blocked-only, repaired, legacy, and duplicate events. Paraphrase queries must retrieve relevant results through actual inference in a separate live test; lexical fallback cannot pass that gate.

### R6: Attach Secured Analyst Tools

- Provide Agent Builder with read-only backend tools for authorized history search, receipt detail, decision evidence, and deterministic metrics. Use a supported secured MCP/tool integration with a verified user principal or short-lived, audience-bound, server-issued scope capability.
- Bind owner, allowed intents/runs, and expiry in backend authorization. Ignore/reject model-supplied user IDs, scope changes, raw Elasticsearch queries, and unauthorized document IDs.
- Attach these tools to the Receipt Analyst and verify tool invocations occur. Provisioning a tool-less narrator or unused tools is not completion of this requirement.
- Support questions about unpaid blocked/review-required proposals as well as captured purchases. Retrieval must handle ordinary questions without requiring the entire sentence as a literal substring.
- Return citations to actually retrieved receipt/event/revision IDs and validate references before display. Bound evidence size and tool/model rounds; refuse unsupported claims and treat retrieved text as untrusted content.
- Use the same deterministic metrics API in UI and Agent Builder tools. Never calculate financial metrics in model prose. Bedrock fallback must be visibly labeled and cannot count as verification of Agent Builder tool use.

**Exit tests:** Ask about purchases this month, blocked runs, avoided amount, and approval rationale across owned intents. An injected request to switch users or fetch foreign receipt IDs fails at the tool boundary. Verify cited evidence supports answers and no order/capture/write tool is available.

### R7: Complete the Real UI Journey and Release Proof

- Replace the hard-coded sample action with a shopping-run screen showing real search/candidate/validation/repair progress and provider errors.
- Add final cart review, missing-evidence HOLD states, repair exhaustion review, and quote refresh/re-review states; never auto-fill unknown tax, shipping, or recurring terms.
- Add historical intent/run navigation and server-backed restoration after reload. Receipt detail must render its immutable timeline, original/final totals, violations, revisions, provenance, and actual PayPal evidence.
- Connect historical filters and analyst citations to inspectable details. Display scope, time period, metric definitions, incomplete-data warnings, and search/provider mode clearly.
- Update PRD, architecture, README, plan, and demo script to describe the implemented simulator path and remove stale completion claims. Runtime merchant simulation data is allowed only with disclosure; mock transport/provider responses remain test-only.
- Run the clean-install and deployed acceptance scenario, verify responsive UI, record under-three-minute working demo, and verify public repository/license/submission artifacts and actual combined spend within $25.

**Exit tests:** Browser tests cover create -> Nova proposal -> optional dynamic repair -> final review -> payment contract -> receipt detail -> reload/history -> search/analyst. A separate recorded run uses the running simulator, actual Nova, actual PayPal Sandbox approval/capture/webhook verification, and actual Elastic/Agent Builder. No provider success is inferred from mock transports or flags; merchant facts remain disclosed simulation.

### R8: Remove Superseded Code and Verify the Release

- Inventory references before deletion. Replace the checkout's Channel3-only evidence dependency with simulator quote verification before removing `Channel3OfferService`, `Channel3OfferController`, `Channel3Offer`, their obsolete tests, frontend offer types/search/manual selection, readiness flags, and provider-specific messages.
- Remove `CHANNEL3_API_KEY` from environment templates, workflow inputs, deployment generation, docs, and tests. Remove stored deployment secrets only through an explicit operator action; never print their values. Keep legacy source identifiers readable on historical receipts without retaining executable provider integrations.
- Remove PayPal Catalog-only UI, fake price estimates, controllers/services, read-only Catalog MCP launcher/config, and related dependencies when reference checks confirm they are not used by the new journey. Preserve PayPal OAuth token acquisition, Login, public SDK client configuration, Orders, capture, and webhook verification. Extract shared responsibilities before deleting a class that currently supplies them.
- Delete the hard-coded sample cart and its button, obsolete endpoints/manual cost inputs, placeholder dashboard values, unused model helpers/imports, and superseded data contracts only after replacement tests pass. Avoid alternate unused shopping providers, duplicate orchestration, or generic agent frameworks with no concrete need.
- Review package/build dependency and lockfile usage. Keep shared libraries and necessary historical compatibility; do not perform unrelated refactors. Ignore generated `backend/bin/` output rather than committing it, and do not delete untracked user files without confirming ownership.
- Update the PRD's target design, architecture diagrams, README, integration instructions, examples, and demo script. Do not describe target simulator/Nova behavior as already deployed until validated.
- Run reference/configuration scans, full backend and frontend tests, dependency/build checks, clean deployment with no Channel3 credentials, and the seven-finding acceptance matrix. Verify the final recording and real service spend.

**Exit tests:** App builds and completes a dynamic simulator/Nova/PayPal Sandbox journey without Channel3 or PayPal Catalog configuration. No executable Channel3/sample-cart references or unused dependencies remain. Legacy receipts still render with their original provenance, and real PayPal evidence remains intact. Changing simulator inventory, prices, stock, or terms changes agent decisions or produces HOLD/BLOCK rather than the same scripted result.

## Acceptance Evidence and Remaining Dependencies

- Keep a release checklist containing commit SHA, deployment, sanitized run/revision/quote/PayPal/webhook/receipt IDs, expected and actual results, and reviewer/date. Never record API keys, passwords, full payment payloads, or unnecessary personal data.
- Do not force candidate choices to violate policy for a recording. Cover budget/add-on/recurring rejection in tests; any runtime violation must arise from the actual proposal over simulator facts, not an injected script.
- If simulator evidence, secured Agent Builder identity integration, actual PayPal/Bedrock access, semantic inference entitlement, or budget is unavailable, mark the relevant gate blocked. The simulator exception does not permit fake financial or model-provider evidence.
- Implementation is complete only when all seven finding rows have passing tests and the external release proof is recorded. Prior week labels and prior component-test counts are not substitutes for this acceptance evidence.