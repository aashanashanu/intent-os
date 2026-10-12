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

Important Catalog limitation: PayPal Catalog Products stores product metadata (such as product ID, name, and description), not live purchasable offer pricing/availability. It is not a merchant marketplace search endpoint. A separately authorized merchant catalog or product-offer source is needed before a catalog product can become a priced checkout cart. Do not invent a price from Catalog metadata.

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

## Elastic Cloud persistence

1. In [Elastic Cloud](https://cloud.elastic.co/), create/open a deployment with Elasticsearch and Kibana. Confirm the required Agent Builder/Search features are available in the subscription/trial attached to that deployment.
2. Copy the Elasticsearch HTTPS endpoint (not the Kibana URL) and create an Elasticsearch API key limited to the IntentOS index operations. Keep the encoded API key private.
3. Set these backend variables locally:

   ```dotenv
   INTENTOS_ELASTIC_ENABLED=true
   INTENTOS_ELASTIC_URL=https://your-deployment.es.region.gcp.cloud.es.io
   INTENTOS_ELASTIC_API_KEY=your-encoded-api-key
   ```

4. Restart the backend. Intent Contract writes and reads use `intent-v1`; list is sorted by `createdAt`. When Elastic is disabled, the app uses process-memory storage instead.
5. Validate by creating an intent, restarting the backend, and retrieving it. A missing endpoint/key while Elastic is enabled is a startup error by design.

Current Elastic persistence scope is Intent Contracts only. Proposals, decisions, events, PayPal transactions, AgentReceipts, semantic search, and Agent Builder tools have not been integrated yet.

## Required values to finish live-provider verification

Do not send any secrets. After you configure accounts, provide only:

- Confirmation that Sandbox REST app credentials are entered locally and the Catalog list call works (or its HTTP status/error identifier).
- Confirmation that Log in with PayPal is enabled, plus the exact non-secret Return URL configured in PayPal.
- Elastic deployment HTTPS endpoint hostname (not its key), whether the API key was entered locally, and whether the `intent-v1` restart persistence check passed.
- Whether the `paypal-catalog-readonly` MCP server starts and lists products, without copying its token/output if sensitive.
