# IntentOS Development Plan

## Goal

Deliver the PRD's complete autonomous-purchase path in four weekly delivery increments: one developer, 20 hours per week, about 80 hours total. Use the PRD-recommended React, TypeScript, Tailwind, Java 21, and Spring Boot stack, and deliver a public demo.

PayPal Sandbox, Channel3 product discovery, and the required Elastic capabilities are integration-readiness gates. PayPal Catalog remains useful for product metadata, but it is not a live offer/price source. Use Channel3's backend API for priced retailer offers; its MCP is optional when demonstrating tool interoperability. Keep provider keys server-side. Total hosted-service and model/API spend must stay within $25. If required PayPal Sandbox or product-discovery access is blocked after Week 1, pause for access resolution rather than inventing prices or substituting an unapproved source.

## Current Status

The project owner has verified all Week 1–3 work and exit gates complete. Week 4 implementation is in place and locally validated; live Kibana Agent Builder provisioning, the clean deployed end-to-end acceptance run, and the final demo recording remain.

## Weekly Plan

### Week 1: Feasibility and Foundation (20 hours) — COMPLETE

- Provision PayPal Sandbox, Channel3 product discovery, Elastic Cloud, and candidate LLM services; retain PayPal Catalog for metadata where useful.
- Validate server-side Channel3 search/offer retrieval (merchant, price, availability, buy URL), PayPal Sandbox buyer approval/capture, webhook feasibility, and the permitted tool surfaces. Channel3 MCP is optional; do not require PayPal MCP for product pricing.
- Verify webhook delivery feasibility, Elastic indexing/search/Agent Builder availability, and public-hosting options and cost.
- Validate Bedrock access, model features, privacy/routing, and current token prices for the configured Nova Micro/Nova Lite roles; estimate actual monthly usage and keep combined hosted-service/model spend within $25.
- Scaffold the React client and Spring Boot modular monolith, including secrets/configuration handling and basic health checks.
- Deploy a public health-check page.

**Exit gate:** PayPal Sandbox buyer approval/capture and Channel3 priced-offer discovery work; required Elastic capabilities are accessible; costs fit the $25 cap; and the public deployment is reachable. PayPal Catalog metadata and optional MCP tool demonstrations are supplementary, not prerequisites for offer pricing. If PayPal Sandbox or Channel3 access is blocked, pause for access resolution.

### Week 2: IntentVault and Guarded Agent Loop (20 hours) — COMPLETE

- Implement natural-language intent submission and structured Intent Contract generation with Nova Micro via Amazon Bedrock Converse. The model may extract explicit facts and suggest clarification; validate its JSON and never treat its response as policy approval.
- Clarify low-confidence or ambiguous intent with the user; do not fill missing product or budget constraints by guessing.
- Use Channel3 priced offers for laptop discovery; use PayPal Catalog only for supplemental product metadata.
- Have AI formulate or refine product-search queries and propose candidates from Channel3. Treat returned offers as untrusted, time-sensitive evidence; retain merchant, offer URL, price, availability, and retrieval time. Java policy determines which candidates are admissible; final choice must be explicit user selection or a documented deterministic server strategy. Never infer absent attributes, tax, or shipping.
- Implement candidate proposals and deterministic policy evaluation. At minimum, cover budget, recurring payments, and unrequested add-ons. Add category, required-attribute, merchant, and quantity checks where verified offer data supports them.
- Implement APPROVE, MODIFY, HOLD, and BLOCK outcomes.
- Add an automatic repair loop capped at three attempts, then request user review.
- Persist immutable decision/event records and build the live activity, cart, and validation UI.
- Use Nova Micro for extraction/explanations and Nova Lite for ambiguity/conflict review and repair advice, with task-specific model IDs supplied by configuration. Treat every model output as advisory. Deterministic Java policies alone own APPROVE, MODIFY, HOLD, and BLOCK, calculate totals, and enforce the payment boundary; the model, MCP, and Elastic Agent Builder must not authorize or execute payments.

**Exit gate:** Integration tests show the intentionally noncompliant proposal is blocked with all three required violations, repaired, revalidated, and approved. Ambiguous intent prompts for clarification; repair exhaustion sends the user to review.

### Week 3: PayPal Payment Lifecycle and AgentReceipt (20 hours) — COMPLETE

- Implement server-side PayPal OAuth and Orders v2 operations.
- Re-fetch or otherwise verify the selected Channel3 offer, then create a PayPal Sandbox order only after deterministic policy approval; compute the approved amount server-side and preserve the offer provenance in the proposal/receipt. If tax or shipping is unknown, stop for a quote/confirmation instead of treating it as zero.
- Render PayPal JavaScript SDK v6 checkout, require buyer approval, then capture server-side.
- Verify webhook signatures, handle duplicate events idempotently, and persist authoritative order, capture, and webhook evidence.
- Create AgentReceipt with intent/contract, proposal history, violations, repairs, final decision, amount, PayPal IDs/status, and a timestamped activity timeline.
- Index intents, decisions, transactions, events, and receipts into Elasticsearch.
- Deploy an integrated test environment with Channel3, PayPal, and Elastic secrets held server-side. Label Sandbox checkout accurately: a PayPal capture is evidence of a payment to the configured PayPal Sandbox merchant, not proof that the Channel3 retailer received payment or fulfilled the offer.

**Exit gate:** A Channel3 offer can be discovered, deterministically approved, and used in the clearly labeled PayPal Sandbox buyer-approval/capture flow; its verified webhook produces exactly one complete, searchable receipt. Blocked or unapproved proposals cannot create or capture an order. The demo must not claim PayPal settled with or purchased from the Channel3 retailer without a supported merchant/payment integration.

### Week 4: Elastic Intelligence, Public Demo, and Submission (20 hours) — IN PROGRESS

- [x] Add intent-scoped receipt search with exact status filters, lexical relevance, and semantic ranking when Elasticsearch `semantic_text` inference is available; retain lexical fallback.
- [x] Add deterministic compliance, violation, repair, user-review, captured-total, and prevented-amount metrics. Read proposal history with ES|QL when Elastic is enabled; never ask a model to calculate metrics.
- [x] Add idempotent provisioning for bounded Elastic Agent Builder ES|QL tools and a Receipt Analyst. The application retrieves receipts only after backend intent authorization and passes only that scoped evidence plus deterministic metrics to the agent.
- [x] Finish intent creation, live activity, checkout, receipt/timeline, and analyst user flows, including responsive states and a concise dashboard.
- [ ] Provision the Agent Builder tools/agent in the target Kibana space and run the complete acceptance scenario from a clean deployed setup.
- [ ] Verify the deployed failure states, budget usage, and server-side secret handling; record the functional demo using [docs/demo-script.md](docs/demo-script.md) in under three minutes.
- [x] Complete README setup steps and architecture/integration documentation; retain the existing license.

**Exit gate:** The public deployment supports the verified PayPal Sandbox flow, searchable AgentReceipts, accurate deterministic metrics, and an evidence-grounded Receipt Analyst. Clean-install instructions, architecture diagram, open-source license, and an under-three-minute working demo are complete.

## Verification Plan

1. **Week 1 integration spike:** Retrieve a priced Channel3 offer and its merchant/availability/buy URL; complete a clearly labeled PayPal Sandbox order-to-buyer-approval-to-capture test; optionally verify Channel3 or PayPal MCP tool connectivity; confirm webhook reachability; confirm Elastic capabilities and actual service/model costs fit the $25 cap; deploy a public health-check page.
2. **Week 2 automated tests:** Cover contract parsing/clarification, policy rules and evidence, all four decisions, the three-attempt repair ceiling, and the guarantee that payment operations cannot be reached before APPROVE.
3. **Week 3 integration tests:** Verify only approved orders are created, buyer approval precedes capture, invalid webhook signatures are rejected, duplicate webhook delivery is idempotent, and one AgentReceipt is generated from authoritative PayPal events.
4. **Week 4 end-to-end test:** Use the PRD laptop scenario to block budget/add-on/recurring violations, repair and revalidate, complete buyer-approved Sandbox capture, persist the receipt from the webhook, search lexically and semantically when configured, verify deterministic ES|QL-backed metrics, and get a grounded Receipt Analyst answer citing retrieved receipt IDs.
5. **Clean-install/public-demo check:** Follow the README on a clean environment; verify the public URL, license, secret handling, full-scenario logs, and video runtime under three minutes.

## Decisions and Scope

- One developer, four weeks, 20 hours per week. These are weekly delivery increments; formal Scrum sprint length was not separately specified.
- Use the PRD-recommended React + TypeScript + Tailwind and Java 21 + Spring Boot stack.
- Public hosted demo required. Hosted third-party services and account signup are allowed, with a total hosted-service and LLM/API budget of at most $25. Elastic Cloud is acceptable only within that cap.
- Channel3 is the preferred priced-offer discovery source; PayPal Catalog supplies metadata only. PayPal Orders/Checkout and verified webhooks provide the Sandbox payment boundary and evidence. Channel3 MCP is optional for tool-interoperability demonstration; all product results remain advisory inputs to deterministic Java policy.
- Use Nova Micro for extraction/explanations and Nova Lite for reasoning/repair suggestions through configurable Bedrock model IDs. Validate account access, AWS IAM, JSON parsing, current pricing, and actual cost before enabling production inference. Nova JSON is prompt-generated and backend-validated rather than constrained by Bedrock schema mode.
- All PRD MUST HAVE items and acceptance criteria are release gates.
- Buyer approves PayPal checkout; unclear intent is clarified; after three failed repair attempts, user review is required.
- If PayPal Sandbox or Channel3 offer discovery is blocked after Week 1, pause for access resolution; do not invent offer prices. Keep the integrated demo explicit about the distinction between Sandbox payment to the configured merchant and a retailer purchase.
- No real-money processing. Refunds, disputes, shipment tracking, intent drift, A2A, and other optional/stretch features are out of scope.

## Risks

1. Four weeks (about 80 hours) is tight for one developer across integrations, AI, search, hosting, documentation, and video. Protect the end-to-end vertical slice and defer optional polish until acceptance flow is stable.
2. Channel3 access/pricing, PayPal Sandbox, and Elastic Agent Builder/search capabilities may not be available within the $25 cap. Treat Week 1 feasibility and cost checks as hard gates; Channel3 MCP is optional and the backend API keeps its key server-side.
3. Channel3/PayPal Catalog data may omit reliable RAM, subscription, tax, shipping, or add-on attributes, and offers can change. Preserve source and retrieval time, recheck before checkout, and make the policy hold for required-but-missing facts rather than guessing.

## Repository Touchpoints

- `README.md`: Add setup, environment variables, local run/deploy, and demo instructions.
- `PayPal IntentVault + AgentReceipt — Product Requirements Document.md`: Source for contract fields, policy behavior, acceptance criteria, and Definition of Done.
- `LICENSE`: Retain and verify for the public submission.
- Planned application structure: `frontend/`, `backend/`, `elastic/`, and `docs/`, following the PRD's proposed modular-monolith layout.
