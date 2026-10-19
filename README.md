# IntentOS

IntentOS is a trust layer for agentic commerce. It turns explicit purchase constraints into an Intent Contract, validates proposals deterministically, and records an auditable decision before payment.

## Current implementation

- Create and retrieve Intent Contracts through the Spring Boot API.
- Enforce budgets, categories, required attributes, recurring/subscription permissions, prohibited products, merchant restrictions, per-item quantity caps, and proposal-total consistency.
- Persist shopping runs and immutable quote-backed revisions; Nova Lite selects only supplied simulator candidate IDs, while Java calculates totals and validates policy.
- Repair a cart deterministically in the same run, refresh its quote, and require explicit review of the exact approved revision before PayPal order creation.
- Optional PayPal Login using an authorization-code flow, state validation, server-side token exchange, HTTP-only session, and per-user contract access.
- A clearly disclosed, configurable simulated merchant catalog and computed quotes. It does not reserve stock or place/fulfill a physical retailer order.
- Optional Elasticsearch persistence for intents, runs, revisions, reviews, decision events, checkout state, webhook deduplication, and receipts. Cross-intent run history and deterministic lineage metrics are available; quote snapshots still require a durable deployment mount.
- Configurable Amazon Bedrock Converse tasks: Nova Micro for extraction/explanation and Nova Lite for reasoning, candidate selection, and repair. Candidate IDs are validated and Java policies remain authoritative.

The shopping flow uses the simulator only. PayPal Orders v2 remains the payment boundary; AgentReceipts are finalized only from signature-verified capture webhooks. Analyst answers may use the configured Elastic Agent Builder narrator or a visibly labeled Bedrock fallback, but attached secured Agent Builder tools are not implemented yet. The PayPal Sandbox payment goes to the configured Sandbox merchant and does not imply physical fulfillment. See [docs/prd-remediation-plan.md](docs/prd-remediation-plan.md) for remaining durability, history, analyst, and release gates.

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

The local default permits the demo user and stores contracts in memory. Local quote snapshots are written under `backend/.intentos/merchant-quotes`; preserve or mount that directory across process/container replacement. With Elasticsearch enabled, quotes are stored in `merchant-quote-v1`. Once PayPal Login is configured, set `INTENTOS_AUTH_REQUIRED=true` and restart the backend to require PayPal sessions for intent, shopping, and history routes.

## Tests

```sh
./backend/gradlew -p backend test
npm --prefix frontend run build
npm --prefix frontend run lint
```

## Integration setup

See [docs/integrations.md](docs/integrations.md) for simulator configuration, PayPal Sandbox/Login, Elastic Cloud, and Amazon Bedrock setup. PayPal client secrets and Elastic API keys remain server-side; Bedrock uses local AWS credentials or the EC2 instance role.

The Week 4 architecture and timed demo walkthrough are in [docs/architecture.md](docs/architecture.md) and [docs/demo-script.md](docs/demo-script.md). To configure Elastic Agent Builder, follow the provisioner/API-key steps in [docs/integrations.md](docs/integrations.md); provisioning uses `KIBANA_URL` and a short-lived `KIBANA_API_KEY` supplied only through the shell.

For a public PayPal Login callback without managing a domain or DNS, follow [docs/aws-deployment.md](docs/aws-deployment.md). It serves the frontend from private S3 through CloudFront and deploys the backend to EC2 through GitHub Actions and Systems Manager.

## Development plan

See [PLAN.md](PLAN.md) for weekly delivery goals, integration gates, and verification steps.