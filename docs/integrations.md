# Integration Setup

Never post credentials in chat, commit them, or place them in frontend variables. Use a local ignored `.env` file or environment variables in the backend process. `.env.example` contains the variable names.

## PayPal Sandbox, Catalog, and Login

1. Sign in to the [PayPal Developer Dashboard](https://developer.paypal.com/dashboard/) and switch to **Sandbox**.
2. Under **Apps & Credentials**, create or select a Sandbox REST app associated with a Sandbox business account. Copy its Client ID and Secret into local `PAYPAL_CLIENT_ID` and `PAYPAL_CLIENT_SECRET` values.
3. Under Sandbox **Accounts**, create/use a Personal test account for the buyer and a Business account for the app/merchant. Keep the buyer login for the checkout testing phase.
4. Enable **Log in with PayPal** for the app. In Advanced Settings, set the Return URL to the exact public callback for the environment. Local testing may use `http://localhost:8080/api/auth/paypal/callback` only if PayPal accepts localhost for that app. For the DNS-free AWS deployment, use `https://<cloudfront-host>.cloudfront.net/api/auth/paypal/callback` from the Terraform `app_url` output; see [aws-deployment.md](aws-deployment.md). Enable only `openid`, `profile`, and `email` claims, and set Privacy Policy and User Agreement URLs. Save the app.
5. Set `PAYPAL_LOGIN_REDIRECT_URI` to exactly the same Return URL. The AWS GitHub Actions workflow sets it from `PUBLIC_APP_URL`. The Login implementation uses the Sandbox authorization endpoint, exchanges the authorization code server-side, fetches the OpenID Connect profile, validates OAuth `state`, rotates the session ID, and stores the PayPal user ID in an HTTP-only session.
6. Keep `INTENTOS_AUTH_REQUIRED=false` until Login is configured and tested. Then set it to `true`, restart the backend, open the app, and choose **Sign in with PayPal**. Protected intent/catalog routes return 401 without a valid session; records are scoped to the authenticated PayPal user.
7. The backend Catalog endpoint is `GET /api/catalog/products?page=1&pageSize=20`. It requests an OAuth client-credentials token server-side and calls PayPal Catalog Products v1 `GET /v1/catalogs/products`. Page size is limited to 20.
8. In the PayPal app's Sandbox **Webhooks** settings, register `https://<cloudfront-host>.cloudfront.net/api/webhooks/paypal` (or `http://localhost:8080/api/webhooks/paypal` locally) for `PAYMENT.CAPTURE.COMPLETED`. Store the resulting webhook ID as `PAYPAL_WEBHOOK_ID`. The API verifies every delivery against PayPal's webhook-signature verification endpoint before reading or mutating checkout state. The callback is intentionally outside user-session authentication; signature verification is mandatory.

## Channel3 priced-offer discovery

PayPal Catalog is retained for product metadata; Channel3 supplies current product offers and retailer prices. Create a Channel3 developer API key in the [Channel3 dashboard](https://trychannel3.com/dashboard/api) and set it as `CHANNEL3_API_KEY` in the backend environment. The AWS workflow reads the same-named GitHub Actions secret and stores it only in the backend runtime `SecureString`; never expose it to Vite or the browser. Channel3 search uses the saved intent goal, extracted required attributes, budget ceiling, and in-stock filter through `POST https://api.trychannel3.com/v1/search`. Results are filtered and normalized server-side. `GET /api/intents/{intentId}/offers` returns each product/offer with retailer, price/currency, availability, category/attributes when present, offer URL, and retrieval time.

The user explicitly selects an offer. The proposal records its Channel3 product/offer provenance and structured attributes. Before PayPal order creation, the backend looks up the offer URL again and rejects changes to product, retailer, price, currency, stock, category, or attributes. Java policy validation remains the authority for budget and intent constraints. Tax and shipping must be explicitly confirmed; unknown values are blocked rather than defaulted to zero.

Channel3's buy link is a retailer link, not PayPal settlement. The PayPal Sandbox order charges the configured Sandbox merchant and demonstrates buyer approval, capture, and webhooks only; it does not pay the Channel3 retailer or place a retailer order. The UI labels this distinction. Channel3 MCP is optional; the integrated search uses the server API to keep the key private.

Checkout is available only for a proposal that passes a fresh deterministic policy validation as `APPROVE`. The backend creates an itemized PayPal Orders v2 order using the validated total, tax, shipping, and currency. The browser uses PayPal JavaScript SDK v6 to obtain buyer approval; only the approval callback calls the server capture endpoint. The server independently fetches the PayPal order, checks its approval status and the stored intent/proposal/amount/currency binding, then captures with a stable `PayPal-Request-Id`.

`PAYMENT.CAPTURE.COMPLETED` webhook evidence, not the browser or capture response, finalizes an AgentReceipt. Receipt lookup is `GET /api/intents/{intentId}/receipts`. The receipt includes the Intent Contract, proposal snapshots available from validation/repair events, policy violations, repair events, final decision, authoritative PayPal order/capture IDs and status, amount, webhook ID, and activity timeline. Duplicate webhook IDs and duplicate receipt writes are idempotent.

PayPal Catalog Products stores product metadata (such as product ID, name, and description), not live purchasable offer pricing/availability. Channel3 is the product-offer source for priced candidates; do not invent prices or infer missing product attributes, tax, or shipping from either source.

For local development, export `.env` values into the backend shell before starting it:

```sh
set -a
source .env
set +a
./backend/gradlew -p backend bootRun
```

### Read-only PayPal MCP in VS Code

`.vscode/mcp.json` registers a local stdio MCP server. VS Code prompts for the Sandbox app Client ID and Secret without saving them to the repository. The launcher obtains a short-lived Sandbox access token, then starts the official `@paypal/mcp` server with exactly these actions enabled:

- `products.list`
- `products.show`

It does not enable product create/update, order create/capture, payments, refunds, subscriptions, invoices, or disputes. Product results are read-only MCP context; payment execution remains a separate backend responsibility. Sandbox tokens expire; restart the MCP server if PayPal returns an expired-token error.

Node.js 18+ and network access to npm and PayPal Sandbox are required. In VS Code, open the MCP server picker and start `paypal-catalog-readonly`. Check the PayPal Developer Dashboard/API logs if the server cannot obtain a token; do not paste the secret into logs or issues.

## Amazon Bedrock intent extraction

The backend uses Amazon Bedrock Converse with model IDs configured per task: Nova Micro for intent extraction and explanation, and Nova Lite for intent reasoning and cart-repair suggestions. Extraction and ambiguity/conflict review are used by the draft endpoint; repair suggestions accompany deterministic repair results; explanations use persisted intent/event evidence. Java policy validation remains authoritative for APPROVE, MODIFY, HOLD, and BLOCK; model output cannot create PayPal orders, authorize payments, or capture funds. Shopping-agent candidate generation is not yet wired.

The defaults use `amazon.nova-micro-v1:0` and `amazon.nova-lite-v1:0` in `us-east-1`. The Terraform EC2 role is scoped to invoke those two Nova foundation models in that region. Model availability and pricing can change; verify model access in the Bedrock console before enabling inference.

Nova Micro and Nova Lite do not currently support Bedrock constrained structured output. The extraction prompt requests JSON, then the backend parses and validates required fields, types, USD currency, confidence, and clarification state. Malformed output returns an error; there is no rule-based fallback.

For local use, configure AWS SDK credentials through an AWS profile or environment/SSO credentials with `bedrock:InvokeModel` permission. Do not put AWS credentials in Vite variables or frontend code. In `.env`, set:

```dotenv
INTENTOS_LLM_ENABLED=true
INTENTOS_LLM_REGION=us-east-1
INTENTOS_LLM_MODEL_EXTRACTION=amazon.nova-micro-v1:0
INTENTOS_LLM_MODEL_REASONING=amazon.nova-lite-v1:0
INTENTOS_LLM_MODEL_REPAIR=amazon.nova-lite-v1:0
INTENTOS_LLM_MODEL_EXPLANATION=amazon.nova-micro-v1:0
INTENTOS_LLM_MAX_TOKENS=700
```

The AWS deployment uses the EC2 instance role; no Bedrock API key is needed. Apply the Terraform IAM policy update before enabling `INTENTOS_LLM_ENABLED` in the GitHub `main` environment variables. GitHub Actions stores the non-secret model settings in the SSM SecureString runtime configuration; invocation uses the instance role credentials. Override each `INTENTOS_LLM_MODEL_*` deployment variable to change a task's model without changing Java code; update IAM permissions if the replacement model requires different resource ARNs.

Use the same request set to compare models: clear laptop request, missing budget, multiple possible categories, explicit RAM, and conflicting payment permissions. Measure schema validity, supported facts, clarification behavior, latency, and actual token usage. Never use the model's response as authorization evidence or as a substitute for deterministic backend validation.

## Elastic Cloud persistence

1. In [Elastic Cloud](https://cloud.elastic.co/), create/open a deployment with Elasticsearch and Kibana. Confirm the required Agent Builder/Search features are available in the subscription/trial attached to that deployment.
2. Copy the Elasticsearch HTTPS endpoint (not the Kibana URL) and create an Elasticsearch API key limited to the `intent-v1`, `intent-event-v1`, `paypal-checkout-v1`, `paypal-webhook-v1`, and `agent-receipt-v1` indexes. It needs document read/write privileges on those indexes. Keep the encoded API key private.
3. Set these backend variables locally:

   ```dotenv
   INTENTOS_ELASTIC_ENABLED=true
   INTENTOS_ELASTIC_URL=https://your-deployment.es.region.gcp.cloud.es.io
   INTENTOS_ELASTIC_API_KEY=your-encoded-api-key
   ```

4. Restart the backend. Intent Contracts use `intent-v1`; immutable validation and repair events use append-only `intent-event-v1`; checkout state uses `paypal-checkout-v1`; processed webhook IDs use `paypal-webhook-v1`; and unique AgentReceipts use `agent-receipt-v1`.
5. Create an intent, validate or repair a proposal, and retrieve its activity with `GET /api/intents/{intentId}/events`. With Elastic enabled, confirm contracts, checkout state, events, and receipts remain available after a backend restart. Missing endpoint/key while Elastic is enabled is a startup error by design. With Elastic disabled, payment sessions, deduplication markers, and receipts are in-memory and reset at process restart; do not treat that mode as durable payment evidence.

The GitHub Actions deployment accepts `PAYPAL_WEBHOOK_ID` as a GitHub environment variable or secret and writes it into the backend runtime `SecureString` in AWS Systems Manager Parameter Store. `CHANNEL3_API_KEY`, PayPal client secrets, webhook ID, and Elastic API key remain server-side; the browser receives only the public PayPal client ID and non-secret readiness flags.

For AWS, add `CHANNEL3_API_KEY` as a GitHub Actions repository/environment secret. The deployment script writes it only into the backend runtime `SecureString`; it is not a Vite variable or part of frontend assets.

Elastic persistence covers Intent Contracts, decision/repair events, checkout records, processed webhook IDs, and AgentReceipts. Semantic/hybrid search and Agent Builder tools remain Week 4 work.

## Required values to finish live-provider verification

Do not send any secrets. After you configure accounts, provide only:

- Confirmation that Sandbox REST app credentials are entered locally and the Catalog list call works (or its HTTP status/error identifier).
- Confirmation that `CHANNEL3_API_KEY` is configured server-side and `GET /api/intents/{intentId}/offers` returns offers with price, currency, retailer, availability, URL, category/attributes when available, and retrieval timestamp. Do not share the key.
- Confirmation that Log in with PayPal is enabled, plus the exact non-secret Return URL configured in PayPal.
- Elastic deployment HTTPS endpoint hostname (not its key), whether the API key was entered locally, and whether `intent-v1` and `intent-event-v1` restart persistence checks passed.
- Whether the `paypal-catalog-readonly` MCP server starts and lists products, without copying its token/output if sensitive.
