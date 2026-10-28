# Integration Setup

Never post credentials in chat, commit them, or place them in frontend variables. Use a local ignored `.env` file or environment variables in the backend process. `.env.example` contains the variable names.

## PayPal Sandbox and Login

1. Sign in to the [PayPal Developer Dashboard](https://developer.paypal.com/dashboard/) and switch to **Sandbox**.
2. Under **Apps & Credentials**, create or select a Sandbox REST app associated with a Sandbox business account. Copy its Client ID and Secret into local `PAYPAL_CLIENT_ID` and `PAYPAL_CLIENT_SECRET` values.
3. Under Sandbox **Accounts**, create/use a Personal test account for the buyer and a Business account for the app/merchant. Keep the buyer login for the checkout testing phase.
4. Enable **Log in with PayPal** for the app. In Advanced Settings, set the Return URL to the exact public callback for the environment. Local testing may use `http://localhost:8080/api/auth/paypal/callback` only if PayPal accepts localhost for that app. For the DNS-free AWS deployment, use `https://<cloudfront-host>.cloudfront.net/api/auth/paypal/callback` from the Terraform `app_url` output; see [aws-deployment.md](aws-deployment.md). Enable only `openid`, `profile`, and `email` claims, and set Privacy Policy and User Agreement URLs. Save the app.
5. Set `PAYPAL_LOGIN_REDIRECT_URI` to exactly the same Return URL. The AWS GitHub Actions workflow sets it from `PUBLIC_APP_URL`. The Login implementation uses the Sandbox authorization endpoint, exchanges the authorization code server-side, fetches the OpenID Connect profile, validates OAuth `state`, rotates the session ID, and stores the PayPal user ID in an HTTP-only session.
6. Keep `INTENTOS_AUTH_REQUIRED=false` until Login is configured and tested. Then set it to `true`, restart the backend, open the app, and choose **Sign in with PayPal**. Protected intent/catalog routes return 401 without a valid session; records are scoped to the authenticated PayPal user.
7. The AWS deployment automatically registers `https://<cloudfront-host>.cloudfront.net/api/webhooks/paypal` for `PAYMENT.CAPTURE.COMPLETED` using the same REST app credentials as checkout and stores the confirmed webhook ID. For manual/local deployments, register your publicly reachable HTTPS callback in the PayPal app's Sandbox **Webhooks** settings and set `PAYPAL_WEBHOOK_ID`; PayPal cannot deliver to localhost. The API verifies every delivery against PayPal's webhook-signature verification endpoint before reading or mutating checkout state. The callback is intentionally outside user-session authentication; signature verification is mandatory.

## Simulated Merchant and Shopping Runs

The configurable seed catalog is [../backend/src/main/resources/merchant/catalog.json](../backend/src/main/resources/merchant/catalog.json). It is test data, not a live retailer feed, tax advice, or a promise of fulfillment. The UI labels the simulation and the absence of a physical order/retailer settlement.

Set `INTENTOS_MERCHANT_CATALOG` to `file:/absolute/path/catalog.json` to load operator-managed runtime data. `INTENTOS_MERCHANT_QUOTE_DIRECTORY` stores server-created immutable snapshots (default `.intentos/merchant-quotes`); preserve/mount this directory across deployment replacement before calling the installation durable. `INTENTOS_MERCHANT_QUOTE_TTL_SECONDS` is constrained to 1-3600 seconds. The app does not reserve stock, and quote persistence is not an inventory reservation.

Intent-owned routes under `/api/intents/{intentId}/merchant` expose product search/detail, calculated quotes, and quote verification. `/api/intents/{intentId}/shopping-runs` starts a run; `/propose` invokes the configured Nova Lite SHOPPING task against at most 50 candidate IDs; revisions, deterministic repair, review, and PayPal order creation remain bound to the run and quote. A `READY` quote means evidence is complete, not that Java policy approved it. Missing cost/terms produce `HOLD`.

The user must review the exact approved revision before the backend creates an itemized PayPal Orders v2 order from the stored quote. The browser uses PayPal JavaScript SDK v6 for mandatory buyer approval; only the approval callback calls the server capture endpoint. The server rechecks intent expiry, current policy, quote freshness, review, amount, and currency before capture.

`PAYMENT.CAPTURE.COMPLETED` webhook evidence, not the browser or capture response, finalizes an AgentReceipt. Receipt lookup is `GET /api/intents/{intentId}/receipts`. The receipt includes the Intent Contract, proposal snapshots available from validation/repair events, policy violations, repair events, final decision, authoritative PayPal order/capture IDs and status, amount, webhook ID, and activity timeline. Duplicate webhook IDs and duplicate receipt writes are idempotent.

For local development, export `.env` values into the backend shell before starting it:

```sh
set -a
source .env
set +a
./backend/gradlew -p backend bootRun
```

## Amazon Bedrock intent extraction

The backend uses Amazon Bedrock Converse with model IDs configured per task: Nova Micro for intent extraction and explanation, and Nova Lite for intent reasoning, shopping candidate selection, and repair suggestions. The SHOPPING response is schema-checked and may reference only server-supplied candidate IDs; the server calculates quote amounts and Java policy alone owns APPROVE, MODIFY, HOLD, and BLOCK.

The defaults use `amazon.nova-micro-v1:0` and `amazon.nova-lite-v1:0` in `us-east-1`. The Terraform EC2 role is scoped to invoke those two Nova foundation models in that region. Model availability and pricing can change; verify model access in the Bedrock console before enabling inference.

Nova Micro and Nova Lite do not currently support Bedrock constrained structured output. The extraction prompt requests JSON, then the backend parses and validates required fields, types, USD currency, confidence, and clarification state. Malformed output returns an error; there is no rule-based fallback.

For local use, configure AWS SDK credentials through an AWS profile or environment/SSO credentials with `bedrock:InvokeModel` permission. Do not put AWS credentials in Vite variables or frontend code. In `.env`, set:

```dotenv
INTENTOS_LLM_ENABLED=true
INTENTOS_LLM_REGION=us-east-1
INTENTOS_LLM_MODEL_EXTRACTION=amazon.nova-micro-v1:0
INTENTOS_LLM_MODEL_REASONING=amazon.nova-lite-v1:0
INTENTOS_LLM_MODEL_SHOPPING=amazon.nova-lite-v1:0
INTENTOS_LLM_MODEL_REPAIR=amazon.nova-lite-v1:0
INTENTOS_LLM_MODEL_EXPLANATION=amazon.nova-micro-v1:0
INTENTOS_LLM_MAX_TOKENS=700
```

The AWS deployment uses the EC2 instance role; no Bedrock API key is needed. Apply the Terraform IAM policy update before enabling `INTENTOS_LLM_ENABLED` in the GitHub `main` environment variables. GitHub Actions stores the non-secret model settings in the SSM SecureString runtime configuration; invocation uses the instance role credentials. Override each `INTENTOS_LLM_MODEL_*` deployment variable to change a task's model without changing Java code; update IAM permissions if the replacement model requires different resource ARNs.

Use the same request set to compare models: clear laptop request, missing budget, multiple possible categories, explicit RAM, and conflicting payment permissions. Measure schema validity, supported facts, clarification behavior, latency, and actual token usage. Never use the model's response as authorization evidence or as a substitute for deterministic backend validation.

## Elastic Cloud persistence

1. In [Elastic Cloud](https://cloud.elastic.co/), create/open a deployment with Elasticsearch and Kibana. Confirm the required Agent Builder/Search features are available in the subscription/trial attached to that deployment.
2. Copy the Elasticsearch HTTPS endpoint (not the Kibana URL) and create an Elasticsearch API key limited to the `intent-v1`, `intent-event-v1`, `merchant-quote-v1`, `shopping-run-v1`, `shopping-revision-v1`, `shopping-review-v1`, `paypal-checkout-v1`, `paypal-webhook-v1`, and `agent-receipt-v1` indexes. The runtime key needs document read/write privileges on those indices; use a separate short-lived setup key with index-mapping privileges if you configure mappings. Keep both encoded keys private.
3. Set these backend variables locally:

   ```dotenv
   INTENTOS_ELASTIC_ENABLED=true
   INTENTOS_ELASTIC_URL=https://your-deployment.es.region.gcp.cloud.es.io
   INTENTOS_ELASTIC_API_KEY=your-encoded-api-key
   ```

4. Restart the backend. Intent Contracts use `intent-v1`; immutable decision events use append-only `intent-event-v1`; merchant quotes use create-only `merchant-quote-v1`; runs, revisions, and reviews use their `shopping-*-v1` indexes; checkout state uses `paypal-checkout-v1`; processed webhook IDs use `paypal-webhook-v1`; and unique AgentReceipts use `agent-receipt-v1`. For semantic ranking, configure an Elasticsearch inference endpoint and map `agent-receipt-v1.searchText` as `semantic_text` before receipt ingestion. This capability is not yet verified for the current deployment; lexical search remains the available behavior until verified.
5. Receipt search is `GET /api/intents/{intentId}/receipts/search?q=...&status=...`; results are always scoped to the accessible intent. Metrics are `GET /api/intents/{intentId}/receipts/metrics`. With Elastic enabled, the backend retrieves decision rows with ES|QL and computes compliance, violation, repair, intervention, captured and prevented amounts deterministically in Java. Confirm contracts, checkout state, events, and receipts remain available after a backend restart. Missing Elasticsearch endpoint/key while Elastic is enabled is a startup error by design. With Elastic disabled, payment sessions, deduplication markers, and receipts are in-memory and reset at process restart; do not treat that mode as durable payment evidence.

### Elastic Agent Builder and Receipt Analyst

The UI's **Ask the Receipt Analyst** flow checks intent ownership before issuing a two-minute, read-only MCP capability. Agent Builder then invokes backend tools at `/api/analyst/mcp`. Each data call rechecks the owner and fixes the intent from that capability, not a model argument. Capabilities allow at most eight attempts, are revoked after the answer or any failure, and are stored only in this backend process. A restart invalidates them; multiple replicas require a shared capability registry or instance affinity before this deployment pattern can scale. The endpoint permits protocol discovery without a browser session; discovery contains schemas only, and data calls without a valid capability fail.

Four tools are available: `search_receipts` (at most 20), `get_receipt`, `get_decisions` (at most 50 events), and `get_metrics`. Unknown tools, raw query/scope arguments, foreign receipts, expired tokens, and revoked owners are rejected. No order, capture, or write tools are registered. `get_metrics` uses the same deterministic service as the UI; its existing data-completeness limits still apply. The answer must follow successful evidence and metrics calls, cite retrieved evidence, and omit the capability. The response includes completed `toolCalls` and `AGENT_BUILDER_SECURED_MCP`; Bedrock fallback remains `BEDROCK_GROUNDED_NARRATION` and has no tool trace. A configured Agent Builder that fails to invoke tools fails closed, rather than silently reverting to narration.

For runtime chat, configure `ELASTIC_KIBANA_URL`, `ELASTIC_AGENT_BUILDER_API_KEY`, and `ELASTIC_AGENT_BUILDER_AGENT_ID`. The runtime API key needs Agent Builder access and permission to use the MCP connector in the chosen Kibana space. Do not grant it broad Elasticsearch data access or attach built-in search/HTTP tools: all receipt access must flow through the scoped backend tools. Keep backend persistence credentials separate. Configure the Kibana URL as a GitHub Actions variable and the encoded API key as a secret for AWS deployment. For non-default spaces, include `/s/<space>` in the runtime Kibana base URL.

Deploy the backend first. In Kibana **Stack Management > Connectors**, create an MCP connector pointing to `https://<app-host>/api/analyst/mcp`, then test discovery. No PayPal cookie, backend persistence key, or static user scope belongs in the connector. In Agent Builder's Tools page, import exactly the four backend tools using namespace `intentos`. Depending on the Elastic version, use **Bulk import MCP tools** or add each MCP tool individually. MCP connector support requires a compatible Elastic version/entitlement.

Then attach the imported tools to the agent with a separate short-lived management API key. The provisioner validates that each supplied ID is a remote MCP tool and replaces the agent's tool list with those four IDs only. It no longer creates the legacy raw ES|QL tools. Remove legacy tools from other agents and revoke old broadly privileged runtime keys separately.

Copy the actual Kibana URL using **Open Kibana** in Elastic Cloud. An Elasticsearch hostname containing `.es.` is not a Kibana endpoint and will reject `/api/agent_builder` requests. Do not infer the Kibana hostname by replacing part of the Elasticsearch URL. The provisioning key needs `feature_agentBuilder.all` (or the appropriate read/manage-tools/manage-agents privileges for your version and space). An Elasticsearch document read/write key alone is not sufficient for Kibana management.

```sh
KIBANA_URL=https://your-kibana-host \
KIBANA_API_KEY=your-management-api-key \
KIBANA_SPACE=default \
INTENTOS_MCP_TOOL_IDS=intentos.search_receipts,intentos.get_receipt,intentos.get_decisions,intentos.get_metrics \
node scripts/provision-elastic-agent-builder.mjs
```

`KIBANA_SPACE=default` uses `/api/agent_builder` without a space prefix. Other spaces use `/s/<space>/api/agent_builder`. Set credentials privately in the terminal; do not paste them into chat or commit them. A 401 indicates invalid credentials; a 403 usually indicates missing Kibana privileges or the wrong space. A 404 can indicate that Agent Builder is unavailable on the selected deployment/version.

The provisioner reads agent instructions from `elastic/agent-builder/receipt-analyst.json`. Use the actual imported IDs if Kibana generates different names; each must end with the corresponding remote tool name. Ask a question from an authorized intent, confirm `AGENT_BUILDER_SECURED_MCP` and actual completed `toolCalls`, and inspect the cited receipt/events. Test an unpaid blocked run too. A missing connector, no tool calls, no evidence, or absent citation is an error, not proof of success. Local tests cover scope denial and actual MCP HTTP discovery/calls; deployed Agent Builder callback and citation relevance still need live acceptance. Capability values are transient secrets in the server-to-agent request: never log/export those inputs and restrict Kibana conversation/trace access and retention. Never commit management or runtime API keys.

The GitHub Actions deployment verifies the PayPal webhook registration using the configured REST app credentials. It reuses the registration for `PUBLIC_APP_URL/api/webhooks/paypal`, adds the `PAYMENT.CAPTURE.COMPLETED` subscription if missing, or creates the registration if none exists. The confirmed webhook ID is written into the backend runtime `SecureString` in AWS Systems Manager Parameter Store; a manually supplied stale ID is not trusted. Registration failures stop deployment. For local development, register the callback manually and set `PAYPAL_WEBHOOK_ID`. PayPal client secrets, webhook ID, Elasticsearch API key, and Kibana Agent Builder API key remain server-side; the browser receives only the public PayPal client ID and non-secret readiness flags.

An approved proposal or completed capture response alone does not create an AgentReceipt. Finalization requires a real, signature-verified `PAYMENT.CAPTURE.COMPLETED` delivery. After repairing a missing or incorrect registration, resend existing capture events from PayPal's webhook event history when available. Registering a callback does not retroactively guarantee delivery for older captures; never fabricate webhook evidence to fill that gap.

Elastic persistence covers Intent Contracts, decision/repair events, checkout records, processed webhook IDs, and AgentReceipts. Receipt search supports structured status filters, lexical matching, and semantic ranking when the `searchText` semantic mapping is available; it falls back to lexical search otherwise.

## Required values to finish live-provider verification

Do not send any secrets. After you configure accounts, provide only:

- Confirmation that the simulator products and computed quote routes work, including a HOLD for missing evidence.
- Confirmation that Log in with PayPal is enabled, plus the exact non-secret Return URL configured in PayPal.
- Elastic deployment HTTPS endpoint hostname (not its key), whether the API key was entered locally, and whether `intent-v1` and `intent-event-v1` restart persistence checks passed.
