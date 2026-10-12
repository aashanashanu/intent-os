# IntentOS Development Plan

## Goal

Deliver the PRD's complete autonomous-purchase path in four weekly delivery increments: one developer, 20 hours per week, about 80 hours total. Use the PRD-recommended React, TypeScript, Tailwind, Java 21, and Spring Boot stack, and deliver a public demo.

PayPal Sandbox, PayPal Catalog, PayPal MCP, and Elastic Agent Builder are required. Integration readiness is the Week 1 gate. Total hosted-service and model/API spend must stay within $25. If PayPal Sandbox, MCP, or Catalog remains blocked after Week 1, pause for access resolution rather than substituting a mock or REST-only fallback.

## Weekly Plan

### Week 1: Feasibility and Foundation (20 hours)

- Provision PayPal Sandbox, PayPal Catalog, PayPal MCP, Elastic Cloud, and candidate LLM services.
- Validate credentials, catalog product retrieval, MCP connectivity and tool availability, and a Sandbox order through buyer approval and capture.
- Verify webhook delivery feasibility, Elastic indexing/search/Agent Builder availability, and public-hosting options and cost.
- Compare LLM options for structured output, tool calls, privacy, access, and cost; select one within the budget.
- Scaffold the React client and Spring Boot modular monolith, including secrets/configuration handling and basic health checks.
- Deploy a public health-check page.

**Exit gate:** Required PayPal Sandbox/Catalog/MCP integrations work; required Elastic capabilities are accessible; costs fit the $25 cap; and the public deployment is reachable. If PayPal Sandbox, MCP, or Catalog access is blocked, pause for access resolution.

### Week 2: IntentVault and Guarded Agent Loop (20 hours)

- Implement natural-language intent submission and structured Intent Contract generation.
- Clarify low-confidence or ambiguous intent with the user.
- Use products from PayPal Catalog for the laptop demo scenario.
- Implement candidate proposals and deterministic policy evaluation. At minimum, cover budget, recurring payments, and unrequested add-ons. Add category, required-attribute, merchant, and quantity checks where catalog data supports them.
- Implement APPROVE, MODIFY, HOLD, and BLOCK outcomes.
- Add an automatic repair loop capped at three attempts, then request user review.
- Persist immutable decision/event records and build the live activity, cart, and validation UI.
- Keep model extraction and repair advisory: deterministic backend policies own the final decision. Do not permit MCP or an agent to bypass validation or capture payment.

**Exit gate:** Integration tests show the intentionally noncompliant proposal is blocked with all three required violations, repaired, revalidated, and approved. Ambiguous intent prompts for clarification; repair exhaustion sends the user to review.

### Week 3: PayPal Payment Lifecycle and AgentReceipt (20 hours)

- Implement server-side PayPal OAuth and Orders v2 operations.
- Create PayPal orders only after deterministic policy approval.
- Render PayPal JavaScript SDK v6 checkout, require buyer approval, then capture server-side.
- Verify webhook signatures, handle duplicate events idempotently, and persist authoritative order, capture, and webhook evidence.
- Create AgentReceipt with intent/contract, proposal history, violations, repairs, final decision, amount, PayPal IDs/status, and a timestamped activity timeline.
- Index intents, decisions, transactions, events, and receipts into Elasticsearch.
- Deploy an integrated test environment with secrets held server-side.

**Exit gate:** Sandbox checkout/capture produces a verified webhook and exactly one complete, searchable receipt. Blocked or unapproved proposals cannot create or capture an order.

### Week 4: Elastic Intelligence, Public Demo, and Submission (20 hours)

- Add structured and semantic/hybrid receipt search.
- Add ES|QL metrics, including violations and prevented amount.
- Configure Elastic Agent Builder tools and a Receipt Analyst grounded in retrieved receipt evidence.
- Finish intent creation, live activity, checkout, receipt/timeline, and analyst user flows, including responsive states and a concise dashboard.
- Run the complete acceptance scenario from a clean setup against deployed services.
- Verify security, webhook verification/idempotency, failure states, budget usage, and server-side secret handling.
- Publish the repository, complete README setup steps and architecture/integration documentation, retain the existing license, and record a functional demo video under three minutes.

**Exit gate:** All PRD Definition of Done items and the end-to-end acceptance test pass on the public deployment.

## Verification Plan

1. **Week 1 integration spike:** Complete a PayPal Sandbox catalog-to-order-to-buyer-approval-to-capture test; verify MCP connectivity and permitted tools; confirm webhook reachability; confirm Elastic capabilities and actual service/model costs fit the $25 cap; deploy a public health-check page.
2. **Week 2 automated tests:** Cover contract parsing/clarification, policy rules and evidence, all four decisions, the three-attempt repair ceiling, and the guarantee that payment operations cannot be reached before APPROVE.
3. **Week 3 integration tests:** Verify only approved orders are created, buyer approval precedes capture, invalid webhook signatures are rejected, duplicate webhook delivery is idempotent, and one AgentReceipt is generated from authoritative PayPal events.
4. **Week 4 end-to-end test:** Use the PRD laptop scenario to block budget/add-on/recurring violations, repair and revalidate, complete buyer-approved Sandbox capture, persist the receipt from the webhook, search in Elasticsearch, and get a grounded Receipt Analyst answer.
5. **Clean-install/public-demo check:** Follow the README on a clean environment; verify the public URL, license, secret handling, full-scenario logs, and video runtime under three minutes.

## Decisions and Scope

- One developer, four weeks, 20 hours per week. These are weekly delivery increments; formal Scrum sprint length was not separately specified.
- Use the PRD-recommended React + TypeScript + Tailwind and Java 21 + Spring Boot stack.
- Public hosted demo required. Hosted third-party services and account signup are allowed, with a total hosted-service and LLM/API budget of at most $25. Elastic Cloud is acceptable only within that cap.
- PayPal Catalog is the required product source. PayPal MCP is required in addition to deterministic PayPal REST payment execution. Both must be proven in Week 1.
- LLM provider remains undecided until a Week 1 access/cost/capability comparison.
- All PRD MUST HAVE items and acceptance criteria are release gates.
- Buyer approves PayPal checkout; unclear intent is clarified; after three failed repair attempts, user review is required.
- If PayPal Sandbox, MCP, or Catalog is blocked after Week 1, pause for access resolution. No unapproved mock or REST-only fallback.
- No real-money processing. Refunds, disputes, shipment tracking, intent drift, A2A, and other optional/stretch features are out of scope.

## Risks

1. Four weeks (about 80 hours) is tight for one developer across integrations, AI, search, hosting, documentation, and video. Protect the end-to-end vertical slice and defer optional polish until acceptance flow is stable.
2. The required PayPal Catalog/MCP Sandbox combination and Elastic Agent Builder/search capabilities may not be available within the $25 cap. Treat Week 1 feasibility and cost checks as hard gates.
3. Catalog attributes may not include reliable RAM, subscription, or add-on metadata. Confirm the PayPal Catalog representation in Week 1; policy tests need explicit, inspectable attributes so decisions remain deterministic.

## Repository Touchpoints

- `README.md`: Add setup, environment variables, local run/deploy, and demo instructions.
- `PayPal IntentVault + AgentReceipt — Product Requirements Document.md`: Source for contract fields, policy behavior, acceptance criteria, and Definition of Done.
- `LICENSE`: Retain and verify for the public submission.
- Planned application structure: `frontend/`, `backend/`, `elastic/`, and `docs/`, following the PRD's proposed modular-monolith layout.
