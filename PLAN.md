# IntentOS Development Plan

## Goal

Deliver the PRD's complete autonomous-purchase path in four weekly delivery increments: one developer, 20 hours per week, about 80 hours total. Use the PRD-recommended React, TypeScript, Tailwind, Java 21, and Spring Boot stack, and deliver a public demo.

Use a clearly labeled simulated merchant as the sole shopping catalog and quote source. Nova Lite through Bedrock will power the autonomous Shopping Agent; Spring Boot will execute its bounded tools and enforce policy. PayPal Sandbox approval/capture and verified webhooks remain actual provider integrations, and Elastic remains the audit/intelligence layer. Channel3 is removed from the target architecture, not kept as an optional parallel journey. Keep keys server-side and combined hosted-service/model spend within $25. Simulated catalog data is allowed; scripted agent choices, invented model prices, fabricated PayPal events, and predetermined success are not.

## Current Status

The project owner previously reported Week 1–3 live verification and Agent Builder provisioning. A later source audit found seven unresolved PRD gaps, reopening Weeks 2–4 gates. The simulated merchant is now the active product path in source; this does not supersede prior live verification, and no revised deployed journey has been verified. Follow [docs/prd-remediation-plan.md](docs/prd-remediation-plan.md) for remaining gates and [docs/agent-roles.md](docs/agent-roles.md) for implementation status.

## Weekly Plan

Remediation progress: source now includes simulator quotes, candidate-ID-checked Nova selection, immutable run/revision lineage, same-run repair, exact-revision review, simulator-backed PayPal order creation, capture-time revalidation, run-scoped receipts, owner-scoped run history, lineage metrics, decision-only analyst retrieval, and removal of active Channel3/Catalog discovery. See [docs/simulated-merchant.md](docs/simulated-merchant.md). Persistent quote storage across replacement, distributed concurrency/idempotency, semantic retrieval, attached Agent Builder tool invocation, detailed receipt UI, and external deployed acceptance remain open.

### Week 1: Feasibility and Foundation (20 hours) - BASELINE VERIFIED; SIMULATOR GATE OPEN

- Provision PayPal Sandbox, Elastic Cloud, Bedrock Nova, and a separately bounded simulated merchant service.
- Validate merchant search/detail and computed cart quotes, explicit one-time/subscription terms, stock, destination-based tax/shipping, quote expiry, and stable product/variant IDs. Verify PayPal Sandbox approval/capture and webhook access separately.
- Verify webhook delivery feasibility, Elastic indexing/search/Agent Builder availability, and public-hosting options and cost.
- Validate Bedrock access, model features, privacy/routing, and current token prices for the configured Nova Micro/Nova Lite roles; estimate actual monthly usage and keep combined hosted-service/model spend within $25.
- Scaffold the React client and Spring Boot modular monolith, including secrets/configuration handling and basic health checks.
- Deploy a public health-check page.

**Exit gate:** The simulator supplies complete, reproducible merchant evidence through APIs; PayPal Sandbox and Elastic capabilities work; costs fit the cap; and the deployment is reachable. Simulator data is visibly labeled and never presented as live retailer inventory.

### Week 2: IntentVault and Guarded Agent Loop (20 hours) - REOPENED

- Implement natural-language intent submission and structured Intent Contract generation with Nova Micro via Amazon Bedrock Converse. The model may extract explicit facts and suggest clarification; validate its JSON and never treat its response as policy approval.
- Clarify low-confidence or ambiguous intent with the user; do not fill missing product or budget constraints by guessing.
- Use the configurable simulated merchant catalog for product discovery; no external catalog is required for the main journey.
- Implement Nova Lite as the Shopping Agent's reasoning model with server-side search, detail, and quote tools. It proposes candidate IDs and revisions; Java hydrates merchant facts, computes totals, and validates them. Review the final approved revision before PayPal buyer approval. No fixed winning product or scripted bad cart.
- Implement candidate proposals and deterministic policy evaluation. At minimum, cover budget, recurring payments, and unrequested add-ons. Add category, required-attribute, merchant, and quantity checks where verified offer data supports them.
- Implement APPROVE, MODIFY, HOLD, and BLOCK outcomes.
- Add an automatic repair loop capped at three attempts, then request user review.
- Persist immutable decision/event records and build the live activity, cart, and validation UI.
- Use Nova Micro for extraction/explanations and Nova Lite for ambiguity/conflict review and repair advice, with task-specific model IDs supplied by configuration. Treat every model output as advisory. Deterministic Java policies alone own APPROVE, MODIFY, HOLD, and BLOCK, calculate totals, and enforce the payment boundary; the model, MCP, and Elastic Agent Builder must not authorize or execute payments.

**Exit gate:** Integration tests show the intentionally noncompliant proposal is blocked with all three required violations, repaired, revalidated, and approved. Ambiguous intent prompts for clarification; repair exhaustion sends the user to review.

### Week 3: PayPal Payment Lifecycle and AgentReceipt (20 hours) - REOPENED

- Implement server-side PayPal OAuth and Orders v2 operations.
- Recheck simulator inventory, terms, and quote validity before order creation. Require deterministic approval and review of the exact revision. Missing simulator facts mean HOLD; user-entered amounts cannot replace merchant quotes. Recheck intent expiry and payment eligibility before capture.
- Render PayPal JavaScript SDK v6 checkout, require buyer approval, then capture server-side.
- Verify webhook signatures, handle duplicate events idempotently, and persist authoritative order, capture, and webhook evidence.
- Create AgentReceipt with intent/contract, proposal history, violations, repairs, final decision, amount, PayPal IDs/status, and a timestamped activity timeline.
- Index intents, decisions, transactions, events, and receipts into Elasticsearch.
- Deploy the simulator and app with credentials server-side. Display: "Simulated merchant catalog and quotes. Real PayPal Sandbox payment lifecycle. No physical order or retailer settlement." Record simulator provenance in receipts alongside actual PayPal evidence.

**Exit gate:** One Nova-generated merchant cart lineage reaches repair when needed, final review, PayPal Sandbox buyer approval/capture, and exactly one verified-webhook receipt. Invalid, incomplete, expired, or unreviewed revisions cannot pay. Simulation and actual PayPal evidence remain distinguishable.

### Week 4: Elastic Intelligence, Public Demo, and Submission (20 hours) — IN PROGRESS

- [ ] Complete user-history search, date filters, relevance-ranking evidence, and uncapped pagination under remediation R5; current run history flags some caps but does not guarantee completeness.
- [ ] Finish metrics complete-data handling, captured-run reconciliation, and Elastic/UI parity under R4-R5; logical run grouping and corrected definitions are implemented, Elastic cap handling remains open.
- [ ] Attach backend-authorized tools to the provisioned Receipt Analyst and verify actual invocation under R6; provisioning alone does not close this gate.
- [ ] Complete historical receipt detail/timeline and browser acceptance under R1-R7; autonomous candidate/repair/review/checkout UI and sample-cart removal are implemented.
- [x] Remove active Channel3/PayPal Catalog discovery services, UI, deployment settings, and MCP launcher; preserve legacy evidence and PayPal OAuth/payment code.
- [ ] Finish R8 documentation/reference scans, deployment migration, and release acceptance.
- [ ] Run the complete acceptance scenario from a clean deployed setup after remediation.
- [ ] Verify the deployed failure states, budget usage, and server-side secret handling; record the functional demo using [docs/demo-script.md](docs/demo-script.md) in under three minutes.
- [x] Complete README setup steps and architecture/integration documentation; retain the existing license.

**Exit gate:** The public deployment supports the verified PayPal Sandbox flow, searchable AgentReceipts, accurate deterministic metrics, and an evidence-grounded Receipt Analyst. Clean-install instructions, architecture diagram, open-source license, and an under-three-minute working demo are complete.

## Verification Plan

1. **Week 1 integration spike:** Retrieve simulator products, explicit terms, and a complete computed quote; verify updates affect quotes and expiry is enforced. Separately verify PayPal Sandbox approval/capture, webhook reachability, Elastic/model access, hosted health, and cost.
2. **Week 2 automated tests:** Cover contract parsing/clarification, policy rules and evidence, all four decisions, the three-attempt repair ceiling, and the guarantee that payment operations cannot be reached before APPROVE.
3. **Week 3 integration tests:** Verify only approved orders are created, buyer approval precedes capture, invalid webhook signatures are rejected, duplicate webhook delivery is idempotent, and one AgentReceipt is generated from authoritative PayPal events.
4. **Week 4 end-to-end test:** Use the PRD laptop scenario to block budget/add-on/recurring violations, repair and revalidate, complete buyer-approved Sandbox capture, persist the receipt from the webhook, search lexically and semantically when configured, verify deterministic ES|QL-backed metrics, and get a grounded Receipt Analyst answer citing retrieved receipt IDs.
5. **Clean-install/public-demo check:** Follow the README on a clean environment; verify the public URL, license, secret handling, full-scenario logs, and video runtime under three minutes.

## Decisions and Scope

- The remediation worklist includes simulator delivery and R8 cleanup. The original four-week estimate is historical; re-estimate the remaining work rather than assuming it fits completed increments.
- Nova shopping uses simulator facts and bounded repair, followed by final review and mandatory PayPal approval. Missing evidence means HOLD. The simulator is authoritative only for its own disclosed test inventory and quotes, not for external retailers.
- Configurable merchant inventory, prices, tax/shipping rules, and explicit terms are allowed runtime simulation data. Never hard-code cart choices, required violations, repairs, or successful outcomes in application logic. Test-only provider fixtures cannot fabricate actual payment evidence. Analyst authorization is backend-enforced.

- One developer, four weeks, 20 hours per week. These are weekly delivery increments; formal Scrum sprint length was not separately specified.
- Use the PRD-recommended React + TypeScript + Tailwind and Java 21 + Spring Boot stack.
- Public hosted demo required. Hosted third-party services and account signup are allowed, with a total hosted-service and LLM/API budget of at most $25. Elastic Cloud is acceptable only within that cap.
- Channel3 and PayPal Catalog are not required product sources in the revised architecture. Remove unused integrations rather than maintaining an unexplained alternate journey. Retain PayPal Orders/Checkout, OAuth, Login where used, and verified webhooks.
- Use Nova Micro for extraction/explanations and Nova Lite for shopping candidate generation, reasoning, and repair through configurable task IDs. These roles may share model/client infrastructure; they do not require independent agent platforms. Elastic Agent Builder is the separate historical Receipt Analyst.
- All PRD MUST HAVE items and acceptance criteria are release gates.
- Buyer approves PayPal checkout; unclear intent is clarified; after three failed repair attempts, user review is required.
- If simulator evidence is incomplete, HOLD. If PayPal or Elastic access is blocked, resolve it; never substitute fake captures, signatures, receipts, or analyst tool executions.
- No real-money processing. Refunds, disputes, shipment tracking, intent drift, A2A, and other optional/stretch features are out of scope.

## Risks

1. Four weeks (about 80 hours) is tight for one developer across integrations, AI, search, hosting, documentation, and video. Protect the end-to-end vertical slice and defer optional polish until acceptance flow is stable.
2. Simulator hosting, Nova usage, PayPal access, and Elastic capabilities must fit the $25 cap. Do not retain unused provider integrations that increase cost or maintenance.
3. Simulated quotes can become stale or omit facts too. Preserve merchant/quote/version provenance, use explicit unknown states, and revalidate before payment. Never present simulated merchant facts as externally verified retail offers.

## Repository Touchpoints

- `README.md`: Add setup, environment variables, local run/deploy, and demo instructions.
- `PayPal IntentVault + AgentReceipt — Product Requirements Document.md`: Source for contract fields, policy behavior, acceptance criteria, and Definition of Done.
- `LICENSE`: Retain and verify for the public submission.
- Planned application structure: `frontend/`, `backend/`, `elastic/`, and `docs/`, following the PRD's proposed modular-monolith layout.
