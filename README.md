# IntentOS

IntentOS is a trust layer for agentic commerce. It turns explicit purchase constraints into an Intent Contract, validates proposals deterministically, and records an auditable decision before payment.

## Current implementation

- Create and retrieve Intent Contracts through the Spring Boot API.
- Enforce budgets, categories, required attributes, recurring/subscription permissions, prohibited products, merchant restrictions, per-item quantity caps, and proposal-total consistency.
- Repair proposals conservatively: clamp configured quantities, remove clearly disallowed extras, and remove optional add-ons to satisfy budget. Unsafe or unresolved changes require user review.
- Optional PayPal Login using an authorization-code flow, state validation, server-side token exchange, HTTP-only session, and per-user contract access.
- PayPal Catalog read endpoint and VS Code MCP server restricted to catalog list/detail tools.
- Optional Elasticsearch persistence for Intent Contracts, append-only decision/repair events, PayPal checkout state, webhook deduplication, and AgentReceipts. In-memory storage is the local default.
- Configurable Amazon Bedrock Converse model assignments: Nova Micro for intent extraction and explanation; Nova Lite for reasoning and repair suggestions. Model output is advisory and deterministic Java policies make all purchase decisions.

PayPal Orders v2 creation is gated by fresh deterministic approval, the frontend uses the PayPal JavaScript SDK v6 for buyer approval, and the backend verifies approval before server-side capture. AgentReceipts are finalized only from signature-verified completed-capture webhooks and can be persisted in Elasticsearch. Shopping-agent offer discovery, semantic search, and Elastic Agent Builder remain future work. The shared Bedrock Converse client supports per-task model IDs: extraction with Nova Micro, ambiguity/conflict review and repair advice with Nova Lite, and event-grounded explanations with Nova Micro. Model suggestions are advisory; Java policy validation remains authoritative. Nova currently produces prompted JSON rather than Bedrock constrained structured output, so the backend validates the response and fails closed if it is invalid. PayPal Catalog returns product metadata rather than live merchant offers/prices, so a separate offer source is required before building a catalog-priced cart.

## Requirements

- Java 21
- Node.js 18+ and npm
- A PayPal Developer account for Sandbox integrations
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

For a public PayPal Login callback without managing a domain or DNS, follow [docs/aws-deployment.md](docs/aws-deployment.md). It serves the frontend from private S3 through CloudFront and deploys the backend to EC2 through GitHub Actions and Systems Manager.

## Development plan

See [PLAN.md](PLAN.md) for weekly delivery goals, integration gates, and verification steps.