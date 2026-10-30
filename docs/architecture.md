# IntentOS Architecture

IntentOS is a Spring Boot modular monolith and React single-page application. The browser gathers intent and destination, shows the Nova-proposed simulator cart/quote, records final review, and hosts PayPal buyer approval. The backend owns authorization, policy evaluation, quote calculation, order creation/capture, webhook verification, persistence, history and analyst evidence scoping.

## System Context

```mermaid

flowchart LR

       Person[User / PayPal Sandbox buyer]

       Browser[React + TypeScript dashboard]

       Api[Spring Boot API]

       JavaPolicy[Deterministic Java policy]

       Bedrock[Amazon Bedrock Nova]

       Merchant[Configurable simulated merchant]

       PayPalOrders[PayPal OAuth + Orders v2]

       PayPalApproval[PayPal hosted buyer approval]

       PayPalWebhook[PayPal signed webhook]

       Elasticsearch[(Elasticsearch indices)]

       Kibana[Kibana Agent Builder]

       Person --> Browser

       Browser -->|Same-origin /api requests, session cookie and CSRF token| Api

       Api -->|Extraction, bounded candidate selection and explanation| Bedrock

       Api -->|Search, detail, quote and quote verification| Merchant

       Api --> JavaPolicy

       JavaPolicy -->|Only APPROVE may proceed| PayPalOrders

       Browser -->|Follow order approval URL| PayPalApproval

       PayPalApproval -->|Return to application| Browser

       Browser -->|Capture request after buyer approval| Api

       Api -->|GET order, verify approval, POST capture| PayPalOrders

       PayPalWebhook -->|Signature headers + event| Api

       Api -->|Contracts, events, checkout state, receipts| Elasticsearch

       Api -->|Authorized receipts + deterministic metrics only| Kibana

       Kibana --> Api

       Api --> Browser

```

## Backend Modules

| Module | Responsibility | Important routes/data |
| --- | --- | --- |
| `auth` | PayPal Login, session identity, CSRF enforcement | `/api/auth/session`, `/api/auth/paypal`, `/api/auth/logout` |
| `intent` | Intent contracts, extraction, decision history, model interface | `/api/intents`, `/api/intents/extract`, `/api/intents/{id}/events` |
| `merchant` | Configurable simulated catalog, calculated quotes and freshness checks | `/api/intents/{id}/merchant/**` |
| `shopping` | Server-owned runs, immutable quote-backed revisions, repair, review, history and simulator checkout | `/api/intents/{id}/shopping-runs/**`, `/api/shopping-history` |
| `paypal` | Orders/capture, checkout state, webhook verification, AgentReceipts and receipt intelligence | `/api/intents/{id}/orders/{orderId}/capture`, `/api/webhooks/paypal`, `/api/intents/{id}/receipts/**` |
| `validation` | Deterministic policy decisions and total calculation | `/api/intents/{id}/validation` |
| `repair` | Bounded deterministic proposal repair and repair history | `/api/intents/{id}/repair` |
| `health` | Health and non-secret integration readiness | `/api/health`, `/api/integrations/status` |

The model client is advisory. It may extract intent or write evidence-grounded explanations, but cannot approve, price, create/capture PayPal payments, process webhooks, or change receipts.

## Browser Flow

1. **Overview / sign-in:** The UI checks `/api/health`, `/api/integrations/status`, and `/api/auth/session`. With auth required, intent routes use the PayPal session cookie and unsafe requests include the session CSRF token.

2. **Intent contract:** The user types a request. Nova extraction/clarification is reviewed before `POST /api/intents` creates the contract.

3. **Shopping run:** The user enters country, region, and shipping method. `POST /api/intents/{id}/shopping-runs/{runId}/propose` supplies up to 50 simulator candidates to Nova Lite, validates the returned candidate IDs, creates a computed quote, validates it in Java, and persists a revision.

4. **Repair and review:** A non-approved proposal may use deterministic repair within the same run. A repaired cart receives a new quote/revision. Only an `APPROVE` revision can be reviewed, and the review binds to its exact revision and quote.

5. **Payment:** The backend reconstructs order facts from the persisted revision/quote, checks review and current policy, then creates a PayPal Sandbox order. The buyer follows the PayPal hosted approval URL and returns to the application, then chooses **Check approval and capture**. The server verifies buyer approval; URL parameters are not payment evidence. Immediately before capture, the backend rechecks expiry, current quote and policy; the browser never captures funds directly.

6. **Activity and receipt:** Only a signature-verified `PAYMENT.CAPTURE.COMPLETED` webhook finalizes an AgentReceipt. Receipt events are scoped to the captured run (legacy records remain proposal-scoped).

7. **History and intelligence:** Authenticated users can search shopping runs across owned intents, including unpaid decisions. After intent authorization, the analyst issues a short-lived capability and Agent Builder invokes four read-only backend MCP tools for receipts, decisions, detail, and deterministic metrics. Completed calls and retrieved evidence citations accompany tool-backed answers. Without Agent Builder configuration, Bedrock narration remains explicitly labeled. Deployment requires importing/attaching the remote MCP tools; see the integration guide.

With Elastic enabled, quote snapshots persist in `merchant-quote-v1`. Otherwise they use local server-owned files; that directory needs a durable mount across container replacement. Other in-memory payment adapters remain non-durable when Elastic is disabled. The simulator does not reserve inventory or place a physical order.

## Shopping and Quote Sequence

```mermaid

sequenceDiagram

       actor User

       participant UI as React dashboard

       participant API as Spring Boot

       participant Model as Bedrock Nova

       participant Merchant as Simulated merchant

       participant Policy as Java policy

       User->>UI: Enter purchase request

       opt Draft assistance

           UI->>API: POST /api/intents/extract

           API->>Model: Extract explicit facts / clarification

           Model-->>API: Advisory structured draft

           API-->>UI: Validated draft and questions

           UI->>User: Review and clarify

       end

       User->>UI: Confirm contract fields

       UI->>API: POST /api/intents

       API-->>UI: IntentContract + intentId

       UI->>API: POST /api/intents/{id}/shopping-runs

       API-->>UI: Server-generated run ID

       UI->>API: POST /{runId}/propose (destination)

       API->>Merchant: Search bounded products and rules

       Merchant-->>API: Simulator candidate facts and IDs

       API->>Model: Nova Lite selects supplied candidate IDs

       Model-->>API: Candidate IDs and quantities

       API->>Merchant: Calculate exact cart quote

       Merchant-->>API: Immutable quote snapshot or HOLD evidence

       API->>Policy: Validate quote-backed proposal

       Policy-->>API: APPROVE / MODIFY / HOLD / BLOCK

       API->>API: Persist immutable revision and validation event

       API-->>UI: Revision, quote and decision

```

## PayPal Checkout and Receipt Sequence

```mermaid

sequenceDiagram

       actor User

       participant UI as React browser

       participant API as Spring Boot

       participant Policy as Java policy

       participant PayPal as PayPal Orders v2

       participant Elastic as Elasticsearch

       User->>UI: Review exact approved revision

       UI->>API: POST /{runId}/revisions/{revisionId}/review

       API->>API: Verify owner, approval, and current quote

       API-->>UI: Immutable review ID

       UI->>API: POST /{runId}/revisions/{revisionId}/orders

       API->>API: Reconstruct proposal from stored revision and quote

       API->>Policy: Revalidate current intent and quote total

       Policy-->>API: APPROVE only

       API->>PayPal: OAuth then create itemized CAPTURE order

       PayPal-->>API: Order ID + buyer approval URL

       API->>Elastic: Persist checkout session with run/revision IDs

       API-->>UI: Order ID + approval URL

       UI->>PayPal: Follow hosted approval URL

       PayPal-->>UI: Return to application after buyer approval

       User->>UI: Check approval and capture

       UI->>API: POST /orders/{orderId}/capture

       API->>API: Recheck intent expiry, review, quote and policy

       API->>PayPal: GET order and verify APPROVED and bound amount/intent/proposal

       API->>PayPal: POST capture with stable request ID

       PayPal-->>API: Capture response

       API-->>UI: Capture status (not yet AgentReceipt authority)

       PayPal->>API: PAYMENT.CAPTURE.COMPLETED + transmission signature

       API->>PayPal: Verify webhook signature with configured webhook ID

       PayPal-->>API: Verification SUCCESS

       API->>Elastic: Deduplicate webhook and persist capture and one receipt

```

A Sandbox capture is a payment to the configured PayPal Sandbox merchant. It does **not** place a physical order or prove retailer settlement. The UI and example must preserve that distinction.

## Receipt Search, Metrics, and Analyst Sequence

```mermaid

sequenceDiagram

       actor User

       participant UI as React dashboard

       participant API as Spring Boot

       participant Access as IntentAccessService

       participant Events as Elasticsearch event store

       participant Receipts as AgentReceiptStore

       participant Analyst as Elastic Agent Builder or Bedrock

       User->>UI: Search receipts / request metrics / ask question

       UI->>API: GET /receipts/search, GET /receipts/metrics, or POST /receipts/analyst

       API->>Access: Verify user may access intentId

       Access-->>API: Authorized IntentContract

       alt Search

           API->>Receipts: Hybrid lexical/semantic search + intent/status filters

           Receipts-->>API: Matching receipts for this intent only

       else Metrics

           API->>Events: Ordered search_after retrieval for this intent

           Events-->>API: Ordered deterministic decision rows

           API->>Receipts: Read authoritative captures for this intent

           Receipts-->>API: Captured amounts and receipt evidence

           API->>API: Compute rates and prevented amount deterministically

       else Analyst

           alt Agent Builder configured

               API->>API: Issue short-lived owner/intent-bound capability

               API->>Analyst: Question + transient capability, no browser session or persistence credentials

               Analyst->>API: MCP tools/call with capability

               API->>Access: Recheck owner/intent and capability expiry/call budget

               API->>Receipts: Retrieve scoped receipts or detail

               API->>Events: Retrieve scoped decisions or deterministic metric inputs

               API-->>Analyst: Read-only tool results

               Analyst-->>API: Answer citing retrieved evidence IDs

               API->>API: Validate completed calls/citations and revoke capability

           else Bedrock narration fallback

               API->>Receipts: Search authorized receipt evidence

               API->>Events: Retrieve matching decisions and deterministic metrics

               API->>Analyst: Backend-retrieved evidence, explicitly no tool execution

               Analyst-->>API: Grounded narration

           end

       end

       API-->>UI: Results, metrics, or answer plus receipt/event/run/revision citations

```

Metrics are calculated by Java, not by an LLM. New events are grouped by logical shopping run; legacy events without run IDs are grouped by proposal ID. Final compliance requires APPROVE with no violations on the final validated revision. First-pass compliance is reported separately. Prevented amount includes only a same-run original-to-final-approved delta; unresolved BLOCK/HOLD is reported as blocked exposure, not savings. Current Elastic retrieval caps and semantic mode still require explicit completion before release.

## Persistence

| Index / store | Contents | Write authority |
| --- | --- | --- |
| `intent-v1` | Intent contracts | Intent API |
| `intent-event-v1` | Append-only validation, repair, order, capture and webhook timeline events | Backend event writers |
| `merchant-quote-v1` | Immutable simulator quote snapshots | Merchant quote service |
| `shopping-run-v1`, `shopping-revision-v1`, `shopping-review-v1` | Shopping run, immutable cart revisions, owner-bound final review | Shopping API |
| `paypal-checkout-v1` | Simulator quote-backed proposal, approved amount/currency, capture state and lineage IDs | Checkout and verified webhook handlers |
| `paypal-webhook-v1` | Processed PayPal webhook event IDs | Verified webhook handler |
| `agent-receipt-v1` | One immutable AgentReceipt per captured PayPal order, including `preventedAmount` and `searchText` | Verified completed-capture webhook only |

When Elastic is disabled, the local in-memory adapters support development/tests but do not survive backend restarts.

## External Setup Still Required


- Import the four remote tools from the IntentOS MCP connector, then attach them in the target Kibana space using `INTENTOS_MCP_TOOL_IDS` and `node scripts/provision-elastic-agent-builder.mjs` with a short-lived management key. Raw ES|QL tools must not be attached to this user-facing agent.

- Runtime Agent Builder chat requires `ELASTIC_KIBANA_URL`, a least-privilege `ELASTIC_AGENT_BUILDER_API_KEY` without broad Elasticsearch access, the `receipt-analyst` agent ID, and an MCP connector to `/api/analyst/mcp` with four imported tools attached. Capabilities live in one backend process for two minutes and are revoked when narration ends. Live tool/citation acceptance remains open.

- Semantic ranking requires an Elasticsearch inference endpoint and `agent-receipt-v1.searchText` mapped as `semantic_text`. Otherwise lexical fallback remains active.

- A clean deployed PayPal Sandbox → verified webhook → searchable receipt walkthrough and the under-three-minute example recording remain release checks.
