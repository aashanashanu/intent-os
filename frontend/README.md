# IntentOS Frontend

React 19, TypeScript, Vite 8, Tailwind, and Lucide power the buyer-facing workspace. Use Java 21 for the separate backend and Node.js 22.12 or newer supported by Vite 8 for frontend development.

## Run Locally

From the repository root:

```sh
npm ci --prefix frontend
npm run dev --prefix frontend -- --host 127.0.0.1 --port 5173
```

Open <http://localhost:5173/>. Start Spring Boot separately on port 8080 using [../README.md](../README.md). Vite proxies `/api` to that backend; set `INTENTOS_API_PROXY_TARGET` in the frontend shell to use a different backend. The public deployment uses CloudFront to route `/api` instead of Vite.

The root environment file configures the backend only when exported in its shell. Never put PayPal secrets, Elastic keys, AWS credentials, or MCP capabilities in frontend environment variables or browser storage. The browser only stores the pending PayPal order metadata needed to resume approval; server-side ownership, policy, and PayPal approval checks still control capture.

## Buyer Flow

The initial request runs intent extraction, then exposes interpreted boundaries for buyer confirmation. Shopping uses catalog candidate IDs and server-computed quotes. The UI shows real operation states, policy findings, safe repair comparisons, exact-cart review, hosted PayPal approval, server capture, and verified-receipt polling. Filters do not determine payment completion; verified receipt evidence is tracked separately.

The bundled catalog supports US/California Standard or Express and US/New York Standard delivery. These controls are currently frontend-defined; coordinate them with destination rules when changing the catalog. Incomplete evidence must remain blocked or held rather than guessed. Local API-unavailable states do not imply the external providers are configured.

## Checks

```sh
npm run build --prefix frontend
npm run lint --prefix frontend
```

The build typechecks and bundles into `frontend/dist`. These checks do not prove live provider integration; verify PayPal return, expired sessions, repair responses with no replacement cart, receipt polling, keyboard access, and mobile layouts separately.

Full local/AWS and Elastic Cloud setup: [../docs/infrastructure-setup.md](../docs/infrastructure-setup.md). Purchase scenarios: [../docs/scenario-guide.md](../docs/scenario-guide.md).