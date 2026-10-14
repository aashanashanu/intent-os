# IntentOS

IntentOS is a trust layer for agentic commerce. It turns explicit purchase constraints into an Intent Contract, validates proposals deterministically, and records an auditable decision before payment.

## Current implementation

- Create and retrieve Intent Contracts through the Spring Boot API.
- Enforce budgets, categories, required attributes, recurring/subscription permissions, prohibited products, merchant restrictions, per-item quantity caps, and proposal-total consistency.
- Repair proposals conservatively: clamp configured quantities, remove clearly disallowed extras, and remove optional add-ons to satisfy budget. Unsafe or unresolved changes require user review.
- Optional PayPal Login using an authorization-code flow, state validation, server-side token exchange, HTTP-only session, and per-user contract access.
- PayPal Catalog metadata endpoint and read-only PayPal MCP tools; Channel3 server-side product search supplies retailer offers with current price/availability and retained provenance.
- Optional Elasticsearch persistence for Intent Contracts, append-only decision/repair events, PayPal checkout state, webhook deduplication, and AgentReceipts. Intent-scoped hybrid/lexical receipt search, ES|QL-backed deterministic metrics, and an evidence-grounded Receipt Analyst are included; in-memory storage is the local default.
- Configurable Amazon Bedrock Converse model assignments: Nova Micro for intent extraction and explanation; Nova Lite for reasoning and repair suggestions. Model output is advisory and deterministic Java policies make all purchase decisions.

The Shopping Agent searches Channel3 and deterministically validates selected offers. PayPal Orders v2 remains the payment boundary. AgentReceipts are finalized only from signature-verified capture webhooks. Receipt search uses structured filters plus lexical and optional semantic matching; ES|QL-backed metrics and the Receipt Analyst operate on retrieved receipt evidence. When configured, the UI uses Elastic Agent Builder; otherwise it uses the Bedrock explanation model. Agent Builder tools/agent definitions are provisioned using the optional script in `scripts/` and `elastic/agent-builder/`; Kibana credentials are supplied through shell variables only. The Sandbox payment is clearly identified as a payment to the configured Sandbox merchant, not the Channel3 retailer. Model suggestions remain advisory and Java policy validation remains authoritative.

## Requirements

- Java 21
- Node.js 18+ and npm
- A PayPal Developer account for Sandbox integrations
- A Channel3 developer account/API key for priced retailer-offer discovery
- Optional Elastic Cloud deployment and Elasticsearch API key

## Local setup

1. Copy `.env.example` to `.env` and fill in only integrations you have configured. `.env` is git-ignored. Never paste credentials into chat or commit them.
2. Load the environment values into the shell that will run the backend:

	```sh
	set -a
	source .env
	set +a
	```

3. Start the API:

	```sh
	./backend/gradlew -p backend bootRun
	```

4. In a second terminal, start the frontend:

	```sh
	npm --prefix frontend run dev -- --host 0.0.0.0
	```

5. Open <http://localhost:5173/>. Vite proxies `/api` to the backend on port 8080.

The local default permits the demo user and stores contracts in memory. Once PayPal Login is configured, set `INTENTOS_AUTH_REQUIRED=true` and restart the backend to require PayPal sessions for intent and Catalog routes.

## Tests

```sh
./backend/gradlew -p backend test
npm --prefix frontend run build
npm --prefix frontend run lint
```

## Integration setup

See [docs/integrations.md](docs/integrations.md) for PayPal Sandbox, Catalog, Login, the read-only MCP server, Elastic Cloud, and Amazon Bedrock configuration. PayPal client secrets and Elastic API keys remain server-side; Bedrock uses local AWS credentials or the EC2 instance role.

The Week 4 architecture and timed demo walkthrough are in [docs/architecture.md](docs/architecture.md) and [docs/demo-script.md](docs/demo-script.md). To configure Elastic Agent Builder, follow the provisioner/API-key steps in [docs/integrations.md](docs/integrations.md); provisioning uses `KIBANA_URL` and a short-lived `KIBANA_API_KEY` supplied only through the shell.

For a public PayPal Login callback without managing a domain or DNS, follow [docs/aws-deployment.md](docs/aws-deployment.md). It serves the frontend from private S3 through CloudFront and deploys the backend to EC2 through GitHub Actions and Systems Manager.

## Development plan

See [PLAN.md](PLAN.md) for weekly delivery goals, integration gates, and verification steps.