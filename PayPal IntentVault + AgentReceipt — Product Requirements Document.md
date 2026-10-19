# Product Requirements Document

## Product Name

**IntentVault + AgentReceipt**

### Working Product Brand

**IntentOS**

### Tagline

> **Your AI can decide. IntentOS makes sure it decides what you meant.**

---

# 1. Executive Summary

IntentOS is an AI-powered trust and accountability layer for autonomous commerce.

As AI agents begin shopping and making payments on behalf of users, a new problem emerges:

> An AI agent may technically follow a user's instruction while making a purchase that violates the user's actual intent.

For example:

> "Buy me a laptop for under $1,000."

An AI agent could construct:

- Laptop — $899
- Extended warranty — $199
- Antivirus subscription — $79/year

The laptop itself is under $1,000, but the overall transaction violates the user's intent.

IntentOS converts natural-language instructions into an **Intent Contract**, evaluates the agent's proposed transaction against that contract, and only allows the payment to proceed when the transaction is consistent with the user's intent.

After the transaction, IntentOS creates an **AgentReceipt**: an explainable, searchable record of:

- what the user asked for
- what the AI understood
- what the agent considered
- what it purchased
- why it made the decision
- what PayPal charged
- whether the purchase complied with the original intent

The system combines:

**AI + PayPal + Elastic + Agentic Commerce**

to create a new trust layer for autonomous payments.

---

# 2. Problem Statement

## 2.1 Current Problem

Traditional e-commerce assumes that a human makes the final purchasing decision.

The flow is approximately:

```text
Human
  ↓
Browse
  ↓
Select
  ↓
Review Cart
  ↓
Pay
```

Agentic commerce changes this:

```text
Human
  ↓
Natural-language goal
  ↓
AI Agent
  ↓
Search
  ↓
Compare
  ↓
Select
  ↓
Modify cart
  ↓
Pay
```

The AI may make dozens of decisions before money moves.

The user therefore loses visibility into:

- why something was purchased
- why a merchant was selected
- why additional products were added
- whether recurring charges were introduced
- whether the final amount matches the original request
- whether the AI interpreted the user's instruction correctly

---

# 3. Product Vision

IntentOS should become:

> **The trust and accountability layer between human intent and autonomous commerce.**

The product has two major capabilities.

## IntentVault

Protects the user **before payment**.

```text
Human Intent
     ↓
Intent Contract
     ↓
AI Agent
     ↓
Proposed Purchase
     ↓
Intent Validation
     ↓
APPROVE / MODIFY / HOLD / BLOCK
     ↓
PayPal
```

## AgentReceipt

Explains and records what happened **after and during payment**.

```text
Intent
  +
Agent decisions
  +
Cart
  +
PayPal transaction
  +
Payment events
  +
Outcome
       ↓
AgentReceipt
```

---

# 4. Goals

## Primary Goals

1. Convert natural-language user requests into machine-readable intent.
2. Allow an AI shopping agent to autonomously search and construct purchases.
3. Detect when a proposed purchase violates user intent.
4. Prevent invalid purchases from reaching payment execution.
5. Use PayPal sandbox as the actual payment execution layer.
6. Create an explainable AgentReceipt for every autonomous purchase.
7. Store and search transaction/decision history using Elastic.
8. Provide natural-language queries over historical agent decisions.
9. Demonstrate real-time PayPal payment lifecycle events.
10. Provide a compelling end-to-end hackathon demonstration.

---

# 5. Non-Goals

The MVP will NOT attempt to:

- become a full e-commerce marketplace
- replace PayPal checkout
- process real money
- build a full fraud detection platform
- replace PayPal's existing fraud/security systems
- become a general-purpose personal finance application
- automatically access a user's real PayPal personal transaction history
- support production-grade multi-merchant settlement
- support every possible payment method

The hackathon MVP will operate primarily in the **PayPal Sandbox**.

---

# 6. Target Users

## Primary User

Consumers using AI agents to make purchases.

Example:

> "Find me a laptop for software development under $1,000."

## Secondary Users

### Families

> "Buy everything needed for my child's birthday party for under $300."

### Business users

> "Purchase office supplies for the team. Stay under $500."

### Developers / Agent Builders

> "I want my agent to transact safely without giving it unrestricted purchasing authority."

---

# 7. Core User Journey

## Scenario

User wants a laptop.

### Step 1 — User creates request

```text
Buy me a laptop for software development.

Budget: $1,000.
At least 16GB RAM.
No recurring subscriptions.
```

---

## Step 2 — Intent Agent creates Intent Contract

Example:

```json
{
  "intentId": "INT-1001",
  "goal": "Purchase a laptop for software development",

  "budget": {
    "maximum": 1000,
    "currency": "USD"
  },

  "requirements": {
    "ram": ">=16GB",
    "category": "laptop"
  },

  "restrictions": {
    "recurringPayments": false,
    "unrequestedServices": false,
    "maximumTotalSpend": 1000
  }
}
```

---

# 8. Intent Contract

The Intent Contract is the central data structure of the product.

## Required fields

```text
intentId
userId
createdAt

naturalLanguageRequest

goal

budget
  maxAmount
  currency

allowedCategories

requiredAttributes

preferredAttributes

prohibitedItems

allowRecurringPayments

allowSubscriptions

allowUnrequestedAddons

maximumTransactionAmount

merchantRestrictions

expiration

approvalPolicy
```

---

# 9. Intent Extraction AI

Use configurable Amazon Bedrock Converse model assignments: **Nova Micro** for Intent extraction and explanation generation, and **Nova Lite** for intent reasoning and cart-repair suggestions. The model converts natural language into a structured Intent Contract draft and suggests clarification questions. The backend validates the response before presenting it for user review. Nova does not support Bedrock constrained JSON output, so generated JSON must be parsed and validated server-side; invalid output fails closed. Model output is advisory; it is not a policy decision.

The target architecture uses configurable task-specific Bedrock model IDs for Intent extraction, Shopping Agent candidate generation, intent reasoning, cart-repair suggestions, and explanation generation. Defaults are Nova Micro for extraction/explanations and Nova Lite for reasoning/repair advice. These are model proposals only: Java/Spring Boot validates all proposals, controls the payment authorization boundary, and orchestrates PayPal APIs, webhooks, idempotency, and security. Amazon Bedrock uses the backend AWS credential chain; model credentials never enter the browser. Current implementation includes extraction, ambiguity/conflict review, repair advice, and explanations grounded in persisted event evidence; Shopping Agent candidate generation remains planned.

Elastic Agent Builder is the separate receipt-analysis and historical-search layer, grounded in persisted receipt and decision evidence. Any MCP/tool integration must not bypass backend owner authorization or the deterministic payment boundary. The current implementation includes Bedrock extraction, bounded Nova Lite candidate selection, Java policy, same-run repair, and simulator-backed PayPal review/checkout; deployed provider verification, secured attached Agent Builder tools, and release acceptance remain open.

### Input

> "Get me a good laptop for development under $1,000. I don't want subscriptions."

### Output

```text
Goal:
Development laptop

Maximum:
$1,000

Required:
16GB+ RAM

Prohibited:
Subscriptions

Prohibited:
Unrequested add-ons

Payment:
One-time purchase

Confidence:
94%
```

---

# 10. Intent Clarification

If the model cannot confidently determine intent, IntentOS should ask a clarification question. Missing or ambiguous values must not be invented.

Example:

> "Do you want the $1,000 limit to include tax and shipping?"

Options:

- Yes
- No
- Only product price

The system stores the answer in the Intent Contract.

---

# 11. Shopping Agent

The Shopping Agent searches a clearly disclosed configurable simulated merchant catalog and creates a candidate cart. The simulator is the sole shopping source; there is no live retailer offer feed or PayPal Catalog shopping surface.

The agent may use:

- simulator product search/detail/quote tools

Persist stable candidate/product/variant IDs, explicit price, stock, quantity limit, purchase type, tax treatment, destination rules, quote fingerprint, and validity. The model may select only supplied candidate IDs; Java hydrates facts and calculates the quote. Treat listing text and model output as untrusted. Missing tax, shipping, subscription terms, stock, or destination rules mean HOLD, never a guessed zero/default. Candidate selection does not authorize payment; Java policy validation and exact-revision user review are required.

Simulation is not retailer checkout or settlement. Clearly label the PayPal Sandbox order as a payment to the configured Sandbox merchant and do not claim physical fulfillment.

The Shopping Agent should NOT directly capture PayPal payments.

It can:

- search
- compare
- select
- add items
- remove items
- modify cart
- propose payment

The final payment decision belongs to IntentOS.

---

# 12. Proposed Transaction

The Shopping Agent creates a normalized transaction proposal.

Example:

```json
{
  "proposalId": "PROP-2001",
  "intentId": "INT-1001",

  "items": [
    {
      "name": "Developer Laptop",
      "quantity": 1,
      "unitPrice": 899
    },
    {
      "name": "Extended Warranty",
      "quantity": 1,
      "unitPrice": 199
    },
    {
      "name": "Antivirus Subscription",
      "quantity": 1,
      "unitPrice": 79,
      "recurring": true
    }
  ],

  "shipping": 0,
  "tax": 72,
  "total": 1249
}
```

---

# 13. Intent Validation Agent

This is the heart of IntentVault.

The agent evaluates:

## Budget compliance

```text
Requested maximum: $1,000
Proposed total: $1,249

Result: FAIL
```

## Product compliance

```text
Laptop: PASS
Warranty: UNKNOWN
Antivirus: FAIL
```

## Recurring payment

```text
User allowed recurring payments: NO

Detected recurring payment: YES

Result: FAIL
```

## Add-on compliance

```text
User requested laptop.

Warranty was not requested.

Result: FAIL
```

---

# 14. Intent Validation Output

```json
{
  "decision": "BLOCK",

  "confidence": 0.98,

  "violations": [
    {
      "type": "BUDGET_EXCEEDED",
      "amount": 249
    },
    {
      "type": "UNREQUESTED_ADDON",
      "item": "Extended Warranty"
    },
    {
      "type": "RECURRING_PAYMENT",
      "item": "Antivirus Subscription"
    }
  ],

  "explanation":
    "The proposed purchase exceeds the user's $1,000 budget
     and contains unrequested recurring services."
}
```

---

# 15. Four Possible Decisions

## APPROVE

Purchase satisfies intent.

```text
🟢 APPROVED
```

## MODIFY

Agent can automatically correct the cart.

```text
🟡 MODIFY

Remove:
Extended Warranty
Antivirus Subscription
```

The Shopping Agent retries.

## HOLD

User must make a decision.

```text
🟠 USER APPROVAL REQUIRED
```

## BLOCK

Purchase cannot proceed.

```text
🔴 BLOCKED
```

---

# 16. Autonomous Repair Loop

This is a key AI feature.

The system should not simply block.

It should attempt to repair the transaction.

```text
Shopping Agent
      ↓
Proposal
      ↓
Intent Validator
      ↓
Violation
      ↓
Shopping Agent receives violations
      ↓
Modify cart
      ↓
New proposal
      ↓
Intent Validator
      ↓
PASS
```

Maximum retries:

```text
3
```

After 3 failed attempts:

```text
USER REVIEW REQUIRED
```

---

# 17. Example Successful Repair

Initial proposal:

```text
Laptop              $899
Warranty            $199
Antivirus            $79
Tax                   $72
-------------------------
Total              $1,249
```

Intent Validator:

```text
BLOCK
```

Shopping Agent removes extras.

New proposal:

```text
Laptop              $899
Tax                   $72
-------------------------
Total                $971
```

Intent Validator:

```text
APPROVED
```

---

# 18. PayPal Integration

PayPal must be a **core part of the transaction lifecycle**, not an afterthought.

The MVP should use PayPal Sandbox.

## Required PayPal capabilities

### 1. OAuth / credentials

Use:

```text
PAYPAL_CLIENT_ID
PAYPAL_CLIENT_SECRET
```

Obtain sandbox access tokens using PayPal OAuth.

PayPal recommends server-side use of the Orders API rather than making these API calls directly from the browser.

---

# 19. PayPal Orders API

The MVP should use:

### Create Order

```http
POST /v2/checkout/orders
```

Purpose:

Create the PayPal order only **after IntentOS approves the transaction**.

The order should include:

- item names
- quantities
- unit prices
- tax
- shipping
- total
- currency
- custom identifiers where appropriate

Create the order only from a persisted approved/reviewed revision and its current server-stored simulator quote. Preserve simulation provenance and the exact run/revision/quote IDs. Recheck intent expiry, review, amount, currency, policy and quote freshness before capture. The Sandbox checkout demonstrates buyer approval, capture, and PayPal lifecycle evidence; it is not physical retailer settlement.

PayPal's Orders v2 API supports creating, retrieving, authorizing and capturing orders.

---

# 20. PayPal Capture

After buyer approval:

```http
POST /v2/checkout/orders/{ORDER-ID}/capture
```

This is the actual payment execution step.

PayPal's standard flow is:

```text
Create Order
      ↓
Buyer approval
      ↓
Capture Order
```



---

# 21. Why this flow is important

IntentOS should NOT:

```text
AI
 ↓
PayPal capture
```

Instead:

```text
AI
 ↓
Intent Contract
 ↓
Intent Validation
 ↓
Approved
 ↓
PayPal Order
 ↓
Buyer Approval
 ↓
PayPal Capture
```

This demonstrates that IntentOS controls the **decision boundary immediately before payment**.

---

# 22. PayPal JavaScript SDK

Use PayPal JavaScript SDK v6 for the demo checkout UI.

The current SDK supports PayPal, Pay Later, Venmo in the US, Google Pay, Apple Pay, Fastlane, and cards.

For the MVP:

```text
PayPal button
+
Sandbox buyer
```

is sufficient.

Do not spend hackathon time implementing every funding source.

The checkout UI must distinguish the simulated merchant facts from the PayPal Sandbox merchant receiving the test payment. A successful Sandbox capture proves only the configured Sandbox transaction and must not be presented as physical fulfillment.

---

# 23. PayPal Webhooks

Webhooks are REQUIRED for the AgentReceipt architecture.

Create a webhook endpoint:

```http
POST /api/paypal/webhooks
```

Subscribe to relevant payment lifecycle events.

At minimum:

```text
PAYMENT.CAPTURE.COMPLETED
```

Potential additional events:

```text
PAYMENT.CAPTURE.REFUNDED
PAYMENT.CAPTURE.DENIED
CHECKOUT.ORDER.APPROVED
```

Exact event subscriptions should be configured according to the sandbox capabilities available to the PayPal application.

PayPal webhooks are server callbacks for payment events and can be verified using the webhook ID and PayPal's verification mechanism.

---

# 24. AgentReceipt

Every purchase gets a unique:

```text
receiptId
```

Example:

```text
AR-2026-000001
```

The receipt should contain:

### User Intent

```text
"Buy me a development laptop under $1,000."
```

### Intent Contract

```text
Budget: $1,000
Recurring payments: prohibited
RAM: >=16GB
```

### Agent Decisions

```text
14 products evaluated
3 shortlisted
1 selected
```

### Original Proposal

```text
$1,249
```

### Violations

```text
Budget exceeded
Unrequested warranty
Recurring subscription
```

### Corrected Proposal

```text
$971
```

### PayPal

```text
PayPal Order ID
Capture ID
Amount
Currency
Status
Timestamp
```

### Final Decision

```text
APPROVED
```

---

# 25. Agent Decision Timeline

Each AgentReceipt should have an event timeline.

Example:

```text
10:31:02
Intent created

10:31:05
Intent Contract generated

10:31:12
Shopping Agent started

10:31:28
14 products evaluated

10:31:41
Cart created

10:31:43
Intent violation detected

10:31:44
Shopping Agent instructed to repair

10:31:49
Cart modified

10:31:50
Intent validation passed

10:31:51
PayPal Order created

10:32:08
Buyer approved

10:32:10
PayPal capture completed

10:32:11
AgentReceipt generated
```

This timeline is one of the strongest parts of the demo.

---

# 26. "Why Did My Agent Do This?"

The user should be able to ask:

> Why did you buy this laptop?

IntentOS retrieves the AgentReceipt and answers using grounded transaction evidence.

Example:

> "You asked for a development laptop under $1,000. The agent initially selected a $899 laptop but attempted to add a $199 warranty and $79 recurring antivirus subscription. IntentOS rejected those additions because they were outside your stated intent. The agent removed them and purchased the laptop for $971 including tax."

The answer must be grounded in stored evidence, not reconstructed from the LLM's memory.

---

# 27. Elastic Integration

Elastic should be the **AgentReceipt intelligence and audit layer**.

Do not use Elastic merely as a database.

The hackathon specifically encourages using Elasticsearch together with Agent Builder, Workflows, ES|QL, Search, Security and MCP/A2A.

---

# 28. Elastic Data Model

Create several Elasticsearch indices.

## Index 1 — intents

```text
intent-v1
```

Fields:

```text
intentId
userId
timestamp
naturalLanguageRequest
goal
budget
currency
constraints
prohibitedItems
recurringPaymentAllowed
intentEmbedding
```

Use semantic/vector representation for:

```text
naturalLanguageRequest
goal
constraints
```

Elastic supports semantic search using semantic_text/vector search and hybrid keyword + semantic search.

---

# 29. Index 2 — agent_decisions

```text
agent-decisions-v1
```

Fields:

```text
decisionId
intentId
proposalId
agentId
timestamp

action
reason

decision
confidence

violations[]

candidateItems[]

model
promptVersion

inputHash
```

---

# 30. Index 3 — transactions

```text
paypal-transactions-v1
```

Fields:

```text
paypalOrderId
paypalCaptureId

intentId
receiptId

amount
currency

items[]

merchant

status

createdAt
capturedAt

paypalEventType
```

---

# 31. Index 4 — agent_events

```text
agent-events-v1
```

This becomes the complete event stream.

Example:

```json
{
  "eventId": "EVT-123",
  "type": "INTENT_VALIDATION_FAILED",
  "timestamp": "...",
  "intentId": "INT-1001",
  "proposalId": "PROP-2001",
  "agentId": "shopping-agent",
  "severity": "HIGH",
  "details": {
    "reason": "RECURRING_PAYMENT_NOT_ALLOWED"
  }
}
```

---

# 32. Elasticsearch Search

Use Elasticsearch to answer:

> "Find purchases where the agent attempted to exceed my budget."

Example search dimensions:

```text
decision = BLOCK
violation = BUDGET_EXCEEDED
```

Also support semantic queries:

> "Show me purchases where the agent almost bought something I didn't ask for."

This is where semantic search adds value.

---

# 33. Hybrid Search

Use:

```text
Keyword search
        +
Semantic search
        +
Structured filters
```

Example:

```text
Query:
"unrequested subscriptions"

Filters:
decision = BLOCK
userId = USER-1
date > last_30_days
```

This can combine exact event filtering with semantic intent/decision retrieval.

---

# 34. Elastic Agent Builder

Use Elastic Agent Builder to create a:

# **Receipt Analyst Agent**

The agent can answer:

> What did my agents buy this month?

> Which purchases were blocked?

> How much money did IntentOS prevent from being overspent?

> Show me purchases where an agent violated my intent.

> Why was my last purchase approved?

Elastic Agent Builder supports custom agents/tools, Elasticsearch-backed reasoning, MCP/A2A connectivity and REST APIs.

---

# 35. Receipt Analyst Tools

Create custom Agent Builder tools such as:

### get_receipt

Input:

```text
receiptId
```

Returns:

```text
intent
decisions
transaction
timeline
```

### search_receipts

Input:

```text
natural language query
date range
status
```

### calculate_saved_amount

Returns:

```text
original proposed amount
final amount
avoided amount
```

### get_intent_violations

Returns:

```text
violations
frequency
categories
```

---

# 36. Elastic Workflows

Use Elastic Workflows for deterministic operations.

This is important.

LLMs should NOT be responsible for:

- persisting payment events
- calculating totals
- changing transaction state
- processing webhook events
- determining whether a PayPal capture event occurred
- deciding or bypassing deterministic policy approval

Those should be deterministic workflows and backend policies. Nova Micro/Lite may propose extraction, candidate products, repair suggestions, and explanations; Spring Boot policy validation remains the authority for APPROVE, MODIFY, HOLD, and BLOCK.

Elastic Workflows can orchestrate repeatable actions and can be triggered from agents.

---

# 37. Example Elastic Workflow

```text
PayPal Webhook
      ↓
Validate webhook
      ↓
Normalize event
      ↓
Store transaction
      ↓
Update AgentReceipt
      ↓
Calculate metrics
      ↓
Trigger notification
```

---

# 38. Elastic MCP

Optional but highly valuable.

Expose Elastic Agent Builder through MCP:

```text
KIBANA_URL/api/agent_builder/mcp
```

This allows external agent clients to interact with Elastic Agent Builder tools.

Potential architecture:

```text
IntentOS Agent
      │
      │ MCP
      ▼
Elastic Agent Builder
      │
      ▼
Elasticsearch
```

---

# 39. PayPal Agent Toolkit

The PayPal Agent Toolkit should be evaluated for the agent-facing PayPal integration.

It currently provides tools including:

```text
create_order
get_order
pay_order
create_refund
get_refund

list_transactions

list_disputes
get_dispute

create_shipment_tracking
get_shipment_tracking

create_product
list_products

subscription management
```



For our MVP, the most relevant are:

```text
create_order
get_order
pay_order
create_refund
get_refund
```

and optionally:

```text
list_transactions
```

---

# 40. PayPal MCP Alternative

Instead of manually implementing every PayPal tool, the project can use PayPal's official MCP server.

PayPal provides an MCP server that allows compatible AI clients to interact with PayPal capabilities through natural language.

The project should choose one of:

### Option A — REST APIs

Best for:

- Spring Boot
- maximum control
- deterministic transaction flow

### Option B — PayPal Agent Toolkit

Best for:

- agent-oriented implementation
- rapid development

### Option C — PayPal MCP

Best for:

- demonstrating MCP-based agentic commerce
- showing AI-to-PayPal tool interaction

### Recommended

Use:

**Spring Boot + PayPal REST APIs for payment execution**

and optionally:

**PayPal MCP/Agent Toolkit for the AI agent layer.**

This gives us both deterministic payment control and an obvious agentic-commerce story.

---

# 41. Recommended System Architecture

```text
                         USER
                           │
                           ▼
                 ┌──────────────────┐
                 │    React UI      │
                 └────────┬─────────┘
                          │
                          ▼
                ┌────────────────────┐
                │ Spring Boot API    │
                └─────────┬──────────┘
                          │
             ┌────────────┼─────────────┐
             │            │             │
             ▼            ▼             ▼
      Intent Agent   Shopping Agent   Receipt API
             │            │             │
             └──────┬─────┘             │
                    ▼                   │
             Intent Validator           │
                    │                   │
          ┌─────────┴─────────┐         │
          │                   │         │
       APPROVE             REPAIR       │
          │                   │         │
          └─────────┬─────────┘         │
                    ▼                   │
             PayPal Order               │
                    │                   │
                    ▼                   │
             PayPal Checkout            │
                    │                   │
                    ▼                   │
             PayPal Capture             │
                    │                   │
                    ▼                   │
             PayPal Webhook             │
                    │                   │
                    └──────────┬────────┘
                               ▼
                        Event Processor
                               │
                    ┌──────────┴───────────┐
                    ▼                      ▼
              Elasticsearch          AgentReceipt
                    │
                    ▼
             Elastic Agent Builder
                    │
                    ▼
              Receipt Analyst
```

---

# 42. Recommended Technology Stack

## Frontend

```text
React
TypeScript
Tailwind
```

Screens:

```text
Dashboard
Intent Creation
Intent Contract
Agent Activity
Cart Review
Intent Validation
PayPal Checkout
AgentReceipt
Receipt Analyst
```

---

# 43. Backend

Recommended:

```text
Java 21
Spring Boot
Spring WebFlux
```

Services/modules:

```text
intent-service
agent-orchestrator
shopping-service
intent-validator
paypal-service
receipt-service
event-service
```

For a hackathon, these can remain modules within a single Spring Boot application rather than separate deployable microservices.

---

# 44. AI Layer

Use an LLM for:

### Intent Agent

Natural language → Intent Contract.

### Shopping Agent

Product search and cart construction.

### Intent Validator

Transaction → compliance decision.

### Receipt Analyst

Transaction history → natural-language explanation.

The deterministic parts should remain code-based.

---

# 45. Critical AI Design Principle

Do NOT ask the LLM:

> "Is this payment okay?"

and blindly trust the answer.

Instead:

```text
LLM
 ↓
Structured decision
 ↓
Deterministic policy engine
 ↓
Final decision
```

For example:

```text
LLM detects:
"recurring subscription"

Policy engine:
recurringAllowed = false

Final:
BLOCK
```

This makes the product much more defensible technically.

---

# 46. Intent Validation Rules

The MVP should implement at least:

## Rule 1 — Maximum amount

```text
total <= maximumBudget
```

## Rule 2 — Recurring payments

```text
recurring == false
```

## Rule 3 — Category

```text
item.category ∈ allowedCategories
```

## Rule 4 — Required attributes

```text
RAM >= requestedRAM
```

## Rule 5 — Unrequested add-ons

```text
addon ∉ requestedItems
```

## Rule 6 — Merchant restrictions

```text
merchant ∉ blockedMerchants
```

## Rule 7 — Quantity

```text
quantity <= requestedQuantity
```

---

# 47. Risk Score

Add an AI-generated intent-risk score:

```text
0–20   LOW
21–50  MEDIUM
51–75  HIGH
76–100 CRITICAL
```

Example:

```text
Intent Risk Score: 91

Reasons:
+ $249 budget violation
+ recurring payment
+ unrequested warranty
```

Important:

The score is advisory.

The deterministic policy rules remain authoritative.

---

# 48. AgentReceipt Dashboard

The main dashboard should show:

```text
┌──────────────────────────────────────┐
│ IntentOS                             │
│                                      │
│ Autonomous purchases:       17      │
│ Intent violations:           4      │
│ Blocked transactions:         3      │
│ Amount prevented:          $438      │
│                                      │
│ Intent compliance:         94.2%     │
└──────────────────────────────────────┘
```

---

# 49. Agent Activity Screen

Show live activity:

```text
Shopping Agent

✓ Searching products
✓ Comparing 14 products
✓ Selected laptop
⚠ Added warranty
⚠ Added subscription

IntentOS

🔴 Intent violation detected

Shopping Agent

✓ Removed warranty
✓ Removed subscription

IntentOS

🟢 Purchase approved
```

This should be the primary demo screen.

---

# 50. AgentReceipt UI

Example:

```text
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
       AGENT RECEIPT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Receipt:
AR-2026-000001

Intent:
"Buy development laptop under $1,000"

Original proposed:
$1,249

Final purchase:
$971

Amount avoided:
$278

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

INTENT VIOLATIONS

🔴 Budget exceeded
🔴 Unrequested warranty
🔴 Recurring subscription

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

FINAL PURCHASE

Laptop                $899
Tax                    $72
────────────────────────────
Total                 $971

PayPal Order:
XXXXXXX

Status:
✓ COMPLETED

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

# 51. "Ask My Receipt" Feature

User can ask:

> Why was this purchase rejected?

> What did my agent remove?

> How much money did IntentOS save me?

> Show all recurring payments the agent attempted.

> Which purchases had intent violations?

> What were the most common agent mistakes?

Elastic Agent Builder should answer these questions from the indexed receipt/event data rather than relying on the model's conversational memory.

---

# 52. Metrics

## Product metrics

### Intent Compliance Rate

```text
approved proposals without violations
/
total proposals
```

### Violation Rate

```text
violating proposals
/
total proposals
```

### Prevention Amount

```text
original proposed amount
-
final approved amount
```

### Repair Rate

```text
automatically repaired proposals
/
violating proposals
```

### Human Intervention Rate

```text
proposals requiring user intervention
/
total proposals
```

---

# 53. Security Requirements

## Never expose

```text
PAYPAL_CLIENT_SECRET
```

to the frontend.

## Store secrets as:

```text
environment variables
```

or secure secret storage.

## Webhook verification

Every PayPal webhook must be verified before processing.

## Idempotency

Use PayPal request IDs where supported.

This is particularly important for:

```text
create order
capture
refund
```

PayPal's refund API explicitly supports `PayPal-Request-Id` for idempotency.

---

# 54. Event Idempotency

Every event should have:

```text
eventId
source
timestamp
processed
```

Before processing:

```text
if eventId already processed:
    ignore
```

This prevents duplicate AgentReceipts.

---

# 55. Auditability

Every decision should be immutable.

Store:

```text
intent
intentVersion
proposal
validationResult
policyVersion
agent
model
timestamp
PayPal order
PayPal capture
webhook event
```

The goal is to answer:

> "Why did the system allow this payment?"

with evidence.

---

# 56. Explainability

Every decision must have:

```text
Decision
Reason
Evidence
Policy
```

Example:

```text
Decision:
BLOCK

Reason:
Purchase exceeds budget.

Evidence:
User maximum = $1,000
Proposed total = $1,249

Policy:
MAX_TOTAL_SPEND

Source:
Intent Contract INT-1001
```

---

# 57. MVP Scope

The MVP should include ONLY these core capabilities:

### MUST HAVE

1. Natural-language intent creation
2. Intent Contract generation
3. Product catalog
4. AI Shopping Agent
5. Candidate cart
6. Intent Validator
7. Budget validation
8. Recurring payment detection
9. Unrequested add-on detection
10. Automatic cart repair
11. PayPal Sandbox order creation
12. PayPal checkout
13. PayPal capture
14. PayPal webhook
15. AgentReceipt
16. Elasticsearch storage
17. Elastic Agent Builder
18. Natural-language receipt search
19. Live activity timeline
20. End-to-end demo

---

# 58. Phase 2 Features

If time remains:

### Additional intent policies

- merchant restrictions
- category restrictions
- quantity limits
- minimum product rating
- shipping limits
- geographic restrictions

### More PayPal functionality

- refunds
- disputes
- shipment tracking
- subscriptions

PayPal's current Agent Toolkit supports these capabilities.

---

# 59. Stretch Feature — Refund Intent

This would be a powerful extension.

User:

> "Return this because it arrived damaged."

AI:

```text
Find AgentReceipt
       ↓
Find PayPal transaction
       ↓
Determine eligibility
       ↓
Create refund
       ↓
Update AgentReceipt
```

PayPal supports refunding captured payments through Payments v2.

---

# 60. Stretch Feature — Intent Drift

This is potentially one of the most impressive AI features.

Suppose the user initially says:

> "Buy a laptop under $1,000."

During a 20-minute shopping session, the agent receives additional information and starts considering:

> $1,499 laptop.

IntentOS detects:

```text
INTENT DRIFT DETECTED
```

It compares the agent's current behavior with the original Intent Contract.

This creates another important concept:

# **Intent Drift Detection**

---

# 61. Stretch Feature — Agent Behavior Analytics

Elastic can detect patterns such as:

```text
Agent attempted recurring charges:
12 times

Agent exceeded budget:
5 times

Agent frequently selects:
premium accessories

Agent frequently adds:
extended warranties
```

This allows the user to ask:

> "Is my shopping agent behaving differently than usual?"

---

# 62. Stretch Feature — Counterfactual Receipt

AgentReceipt could show:

```text
Selected:
Laptop A — $899

Alternative:
Laptop B — $849

Why A?

+ Better battery
+ 32GB RAM
+ Better warranty

Additional cost:
$50

Decision confidence:
87%
```

This makes the receipt a decision explanation rather than merely a payment receipt.

---

# 63. Elastic Usage Summary

Elastic should perform four jobs:

### 1. Storage

Store:

```text
intents
proposals
decisions
transactions
events
receipts
```

### 2. Search

Keyword + semantic search.

### 3. Analytics

ES|QL queries for:

```text
violations
amount prevented
agent behavior
transaction history
```

### 4. AI

Elastic Agent Builder:

```text
Ask questions
Analyze receipts
Investigate violations
Explain decisions
```

Elastic supports semantic/vector search, ES|QL search, Agent Builder, Workflows, MCP and A2A capabilities.

---

# 64. Recommended Elastic Architecture

```text
                    ┌──────────────────────┐
                    │   IntentOS Backend   │
                    └──────────┬───────────┘
                               │
                         Events / JSON
                               │
                               ▼
                    ┌──────────────────────┐
                    │    Elasticsearch     │
                    │                      │
                    │ intents              │
                    │ proposals            │
                    │ decisions            │
                    │ transactions         │
                    │ events               │
                    │ receipts             │
                    └──────────┬───────────┘
                               │
                    ┌──────────┴───────────┐
                    ▼                      ▼
              Semantic Search       ES|QL Analytics
                    │                      │
                    └──────────┬───────────┘
                               ▼
                     Elastic Agent Builder
                               │
                               ▼
                       Receipt Analyst
```

---

# 65. Recommended PayPal Architecture

```text
                  IntentOS
                     │
                     ▼
             Intent Validation
                     │
                  APPROVED
                     │
                     ▼
             PayPal Orders API
                     │
                     ▼
              PayPal Checkout
                     │
                Buyer approves
                     │
                     ▼
             PayPal Capture API
                     │
                     ▼
              PayPal Webhook
                     │
                     ▼
             IntentOS Event API
                     │
                     ▼
             AgentReceipt
```

---

# 66. PayPal API / Capability Checklist

| PayPal capability | MVP? | Purpose |
|---|---:|---|
| OAuth 2.0 | YES | Authentication |
| Orders v2 | YES | Create PayPal order |
| Get Order | YES | Verify order |
| Capture Order | YES | Execute payment |
| JavaScript SDK v6 | YES | Checkout |
| Webhooks | YES | Payment lifecycle |
| Capture details | YES | Receipt evidence |
| Refund API | Optional | Future refund flow |
| Transaction Search | Optional | Reconciliation/history |
| PayPal Agent Toolkit | Recommended | Agent integration |
| PayPal MCP | Recommended | Agentic-commerce demonstration |
| Subscriptions | Stretch | Detect/manage recurring payments |
| Shipment | Stretch | Fulfillment evidence |
| Disputes | Stretch | Post-purchase lifecycle |

PayPal's current Agent Toolkit exposes many of these capabilities directly to agents.

---

# 67. Elastic Capability Checklist

| Elastic capability | MVP? | Purpose |
|---|---:|---|
| Elasticsearch | YES | Receipt/event storage |
| Hybrid search | YES | Search decisions |
| Semantic/vector search | YES | Natural-language receipt search |
| ES|QL | YES | Analytics |
| Agent Builder | YES | Receipt Analyst |
| Custom Agent Builder tools | YES | Query receipts |
| Workflows | Recommended | Deterministic event processing |
| MCP | Recommended | External agent access |
| A2A | Optional | Future agent-to-agent interaction |
| Security | Optional | Audit/access controls |
| Observability | Optional | System monitoring |

---

# 68. Hackathon Judging Alignment

## Technological Implementation

Strong evidence:

```text
AI agents
+
Intent Contract
+
deterministic validation
+
PayPal Orders API
+
PayPal Webhooks
+
Elastic
+
Agent Builder
+
semantic search
+
Workflows
```

---

## Design

The product should feel like:

> "A control center for autonomous purchasing."

Not:

> "A developer dashboard."

The primary screen should tell the user:

```text
What did my agent do?
Was it safe?
Why?
What did PayPal charge?
```

---

## Potential Impact

The pitch:

> As AI agents gain the ability to spend money, users need a way to ensure those agents remain faithful to human intent.

The product doesn't try to stop autonomous commerce.

It makes autonomous commerce **understandable and accountable**.

---

## Innovation

The novel concept is:

> **Intent is treated as a first-class financial object.**

Traditional payment systems understand:

```text
amount
merchant
payer
payment method
transaction
```

IntentOS adds:

```text
human intent
intent contract
agent decision
intent compliance
intent drift
agent reasoning
```

---

# 69. Best Use of PayPal + AI Strategy

Do NOT describe PayPal as:

> "We use PayPal to process payment."

Instead:

> "IntentOS places PayPal at the final execution boundary of an autonomous commerce decision. The AI creates and validates the purchase intent, while PayPal executes the approved transaction and supplies authoritative payment lifecycle events for the AgentReceipt."

That is much stronger.

---

# 70. Best Use of Agentic Commerce Strategy

The agentic flow should be:

```text
Human
 ↓
Goal
 ↓
Intent Agent
 ↓
Shopping Agent
 ↓
Intent Validator
 ↓
PayPal
 ↓
AgentReceipt
```

The AI is actually:

- deciding
- searching
- negotiating/modifying
- validating
- executing through PayPal
- explaining afterward

This satisfies the spirit of agentic commerce much better than a chatbot wrapped around checkout.

---

# 71. Demo Scenario

Use one extremely clear scenario.

## User

> "Find me a laptop for software development under $1,000. I don't want subscriptions."

## Agent

Finds:

```text
Laptop        $899
Warranty      $199
Subscription $79/year
```

## IntentOS

```text
🚨 INTENT VIOLATION

Budget exceeded
Recurring payment detected
Unrequested add-on detected
```

## Agent

Automatically repairs cart:

```text
Laptop        $899
Tax            $72

Total         $971
```

## IntentOS

```text
🟢 INTENT MATCH
```

## PayPal

User approves and completes the clearly labeled Sandbox checkout with the configured PayPal merchant. Do not describe the simulated cart as a completed physical purchase.

## Webhook

```text
PAYMENT.CAPTURE.COMPLETED
```

## AgentReceipt

Created automatically.

## User asks:

> "Why did my agent remove the warranty?"

Agent Builder answers:

> "The warranty was not part of your requested purchase and would have caused the total to exceed your $1,000 limit."

---

# 72. Demo Scenario #2 — Bad Agent

For the video, I strongly recommend showing a deliberately flawed Shopping Agent.

This is better than making the AI perfect.

```text
User:
"Buy laptop under $1,000."

Shopping Agent:
Laptop $899
Warranty $199
Subscription $79

IntentOS:
BLOCKED

Shopping Agent:
Fixing cart...

Final:
Laptop $899

IntentOS:
APPROVED
```

The audience immediately understands why the product exists.

---

# 73. MVP Development Order

## Sprint 1

### Foundation

- React application
- Spring Boot backend
- PayPal sandbox
- Elastic cluster
- basic catalog

---

## Sprint 2

### IntentVault

- natural-language intent
- Intent Contract
- validation rules
- violation detection
- repair loop

---

## Sprint 3

### PayPal

- create order
- checkout
- capture
- webhook
- event persistence

---

## Sprint 4

### AgentReceipt

- receipt model
- event timeline
- PayPal evidence
- receipt UI

---

## Sprint 5

### Elastic

- Elasticsearch indices
- semantic search
- ES|QL
- Agent Builder
- Receipt Analyst

---

## Sprint 6

### Demo polish

- live activity
- animations
- violation visualization
- dashboard
- demo script
- README
- architecture diagram
- video

---

# 74. Repository Structure

```text
intent-os/
│
├── frontend/
│   ├── src/
│   │   ├── pages/
│   │   ├── components/
│   │   ├── api/
│   │   └── types/
│
├── backend/
│   ├── src/main/java/
│   │   └── com/intentos/
│   │       ├── intent/
│   │       ├── agent/
│   │       ├── validation/
│   │       ├── paypal/
│   │       ├── receipt/
│   │       ├── webhook/
│   │       └── elastic/
│
├── elastic/
│   ├── mappings/
│   ├── queries/
│   ├── workflows/
│   └── agent-builder/
│
├── docs/
│   ├── architecture.md
│   ├── paypal-integration.md
│   ├── elastic-integration.md
│   └── demo-script.md
│
├── docker-compose.yml
├── README.md
└── LICENSE
```

---

# 75. API Endpoints — IntentOS

## Intent

```http
POST /api/intents
GET  /api/intents/{intentId}
```

## Shopping

```http
POST /api/agents/shopping
POST /api/agents/shopping/{id}/repair
```

## Validation

```http
POST /api/validation
GET  /api/validation/{proposalId}
```

## PayPal

```http
POST /api/paypal/orders
POST /api/paypal/orders/{orderId}/capture
GET  /api/paypal/orders/{orderId}
```

## Webhooks

```http
POST /api/paypal/webhooks
```

## Receipts

```http
GET /api/receipts/{receiptId}
GET /api/receipts
```

## Search

```http
POST /api/receipts/search
```

---

# 76. Acceptance Criteria

The MVP is complete when the following scenario works end-to-end.

### Given

```text
User:
"Buy a development laptop under $1,000.
No subscriptions."
```

### When

Shopping Agent proposes:

```text
Laptop $899
Warranty $199
Subscription $79
```

### Then

IntentOS must:

1. Detect budget violation.
2. Detect unrequested warranty.
3. Detect recurring payment.
4. Block the initial proposal.
5. Provide machine-readable violations.
6. Ask Shopping Agent to repair.
7. Generate a corrected cart.
8. Revalidate.
9. Approve corrected cart.
10. Create PayPal Sandbox order.
11. Complete the clearly labeled PayPal Sandbox checkout; do not imply retailer settlement without a supported merchant integration.
12. Capture payment.
13. Receive PayPal webhook.
14. Create AgentReceipt.
15. Index the receipt into Elasticsearch.
16. Allow the user to search the receipt.
17. Allow the Receipt Analyst to explain the transaction.

---

# 77. Definition of Done

The project is ready for submission when:

- [ ] PayPal sandbox payment works.
- [ ] AI creates Intent Contract.
- [ ] AI Shopping Agent works.
- [ ] Intent Validator detects violations.
- [ ] Automatic repair works.
- [ ] PayPal order is created only after validation.
- [ ] PayPal capture works.
- [ ] Webhook is received and verified.
- [ ] AgentReceipt is created.
- [ ] Receipt is stored in Elasticsearch.
- [ ] Semantic search works.
- [ ] ES|QL analytics work.
- [ ] Elastic Agent Builder can answer receipt questions.
- [ ] Demo works from clean installation.
- [ ] Public GitHub repository exists.
- [ ] Open-source license is included.
- [ ] README contains complete setup instructions.
- [ ] Architecture diagram exists.
- [ ] Demo video is under 3 minutes.
- [ ] Video demonstrates actual functionality.

The public repository, functional demo, open-source license, and sub-three-minute demo are explicit hackathon submission requirements.

---

# 78. Final Product Positioning

## One sentence

> **IntentOS is the trust layer for agentic commerce that translates human intent into enforceable purchase policies and creates an explainable receipt for every autonomous payment.**

## Short pitch

> AI agents are learning how to shop and pay, but they don't always understand what humans actually mean. IntentOS converts a user's natural-language request into an Intent Contract, validates every proposed purchase against that intent, automatically repairs violations, and uses PayPal to execute the approved transaction. Elastic powers the searchable audit trail and AI-powered AgentReceipt that explains exactly what the agent did, why it did it, and what PayPal charged.

## The memorable line

> **"PayPal moves the money. IntentOS makes sure the AI moves it for the right reason."**

---

# 79. Recommended MVP vs. Stretch Boundary

### Build this for sure

```text
Intent
   ↓
Intent Contract
   ↓
Shopping Agent
   ↓
Violation Detection
   ↓
Automatic Repair
   ↓
PayPal Sandbox
   ↓
Webhook
   ↓
AgentReceipt
   ↓
Elastic
   ↓
Ask My Receipt
```

### Do NOT spend MVP time on

```text
Real merchant onboarding
Real-money transactions
Full marketplace
Multiple currencies
Complex fraud detection
Production identity
Mobile app
Dozens of PayPal payment methods
```

The hackathon rewards a **working, non-trivial, coherent product**, so depth of one end-to-end scenario is more valuable than a large collection of partially implemented features.