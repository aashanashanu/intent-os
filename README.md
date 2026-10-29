# IntentOS

**Your AI can decide. IntentOS makes sure it decides what you meant.**

IntentOS turns a buyer's request into an explicit Intent Contract, checks agent-proposed purchases against deterministic policies, and joins the decisions with verified PayPal payment evidence in an AgentReceipt.

## Purchase Flow

Request a purchase, review the Intent Agent's interpretation, and let the Shopping Agent select from a configurable simulated merchant catalog. Java calculates the full quote and enforces budget, category, specifications, quantity, merchant, and recurring-payment constraints. Safe repairs receive a fresh quote and another policy check. Only an approved, explicitly reviewed revision can reach PayPal buyer approval and server-side capture.

AgentReceipts finalize only from signature-verified `PAYMENT.CAPTURE.COMPLETED` webhooks. Elasticsearch stores intents, quotes, shopping history, decisions, checkout state, and receipts. Elastic Agent Builder can analyze that evidence through four owner-scoped, read-only MCP tools; Bedrock-grounded narration is a separate mode when Agent Builder is unconfigured.

**The catalog and shipping rules are simulated. PayPal Sandbox payments do not place a physical order, reserve inventory, or prove retailer fulfillment.** Semantic search and Agent Builder require provider setup and live verification; readiness flags alone do not prove successful integration.

## Quick Start

Requirements: **Java 21**, **Node.js 22.12+ compatible with Vite 8**, npm, and Git. For the complete shopping experience, configure Elastic Cloud, AWS Bedrock access, and PayPal Sandbox first using [docs/infrastructure-setup.md](docs/infrastructure-setup.md).

```sh
git clone https://github.com/aashanashanu/intent-os.git
cd intent-os
npm ci --prefix frontend
cp .env.example .env
```

Edit the ignored environment file privately. For persistent, model-backed operation, set `INTENTOS_ELASTIC_ENABLED=true`, the Elasticsearch endpoint/key, `INTENTOS_LLM_ENABLED=true`, your AWS profile, and the PayPal configuration described in the setup guide. Root environment files are not automatically loaded by Spring Boot.

Start the backend:

```sh
set -a
source .env
set +a
./backend/gradlew -p backend bootRun
```

In a second terminal:

```sh
npm run dev --prefix frontend -- --host 127.0.0.1 --port 5173
```

Open <http://localhost:5173/>. Vite proxies `/api` to port 8080. Verify the API with `curl --fail http://localhost:8080/api/health`.

The untouched template starts an unprotected, model-disabled local API with process-local storage. It is suitable only for local development; it cannot extract/shop without Bedrock, execute payments without PayPal, or receive external callbacks on localhost. A public HTTPS origin is required for PayPal webhooks and remote MCP callbacks. Once login is configured, enable `INTENTOS_AUTH_REQUIRED=true` before exposing the app.

Never commit credentials or place secrets in frontend variables. With Elastic enabled, quote snapshots persist in `merchant-quote-v1`. With Elastic disabled, payment records and most application data reset on restart; file quote snapshots alone are not durable payment evidence.

## Installation and Operations

- [docs/infrastructure-setup.md](docs/infrastructure-setup.md): complete local and AWS setup, Elastic indexes/keys, PayPal callbacks, Bedrock, Agent Builder/MCP, troubleshooting, and release checks.
- [docs/aws-deployment.md](docs/aws-deployment.md): Terraform resources, GitHub OIDC deployment, cost, and infrastructure lifecycle.
- [docs/integrations.md](docs/integrations.md): provider-specific settings and authorization boundaries.
- [docs/scenario-guide.md](docs/scenario-guide.md): exact buyer requests, expected policy outcomes, repair limits, and payment/receipt walkthroughs.
- [docs/architecture.md](docs/architecture.md): system context, sequences, persistence, and trust boundaries.
- [docs/prd-remediation-plan.md](docs/prd-remediation-plan.md): remaining implementation and acceptance gates.

For AWS, provision your own infrastructure and set every workflow resource variable from your Terraform outputs. Existing workflow defaults belong to an existing installation, not a portable new-account configuration. See the installation guide before deploying a fork.

## Verification

```sh
./backend/gradlew -p backend test bootJar
npm run build --prefix frontend
npm run lint --prefix frontend
node --test scripts/*.test.mjs
git diff --check
```

Automated tests cover policy, quote evidence, bounded repair, authorization, checkout, webhook handling, and scoped analyst tools. Separately verify live login, model execution, Sandbox capture/webhook, restart persistence, and analyst citations. A passing test suite does not establish provider availability or high availability.

## License

See [LICENSE](LICENSE). Implementation status and delivery work are tracked in [PLAN.md](PLAN.md).