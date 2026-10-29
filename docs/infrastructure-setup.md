# Infrastructure Setup

This guide covers a new local installation and a new AWS deployment, both using Elastic Cloud. Use separate provider applications, API keys, and Elasticsearch deployments for development and hosted environments. Never reuse another installation's Terraform state or resource identifiers.

## Architecture and Prerequisites

The React frontend sends same-origin `/api` requests to Spring Boot. Locally, Vite proxies those requests to port 8080. On AWS, CloudFront serves the private S3 frontend and forwards API traffic to a private EC2 instance through a VPC Origin. EC2 reaches PayPal, Bedrock, Elastic Cloud, and Systems Manager through a NAT instance. GitHub Actions deploys through OIDC and SSM, without SSH or static AWS deployment keys.

Install Git, Java 21, Node.js 22.12 or newer supported by Vite 8, and npm. AWS setup additionally requires AWS CLI v2 and Terraform matching [../infra/terraform/versions.tf](../infra/terraform/versions.tf). Gradle is supplied by the repository wrapper. GitHub's hosted runner supplies the container build tools for CI; no local container runtime is required for the local Gradle/Vite workflow. Local cloud-backed operation needs AWS credentials with Bedrock invocation access. Hosted provisioning needs permissions for VPC, EC2, IAM/OIDC, S3, SSM, and CloudFront VPC Origins, plus sufficient service quotas.

Required accounts: PayPal Developer with Sandbox buyer/business accounts, AWS with the configured Nova models available, Elastic Cloud with Elasticsearch and Kibana. Secured receipt analysis also requires an Elastic version/subscription supporting Agent Builder and remote MCP connectors, with a working model connector selected in Agent Builder. Provider costs apply; set budgets and restrict account permissions.

```sh
git clone https://github.com/aashanashanu/intent-os.git
cd intent-os
java -version
node --version
npm ci --prefix frontend
```

## 1. Configure Elastic Cloud

Create an Elasticsearch deployment and open Kibana from Elastic Cloud. Copy the Elasticsearch HTTPS endpoint and the actual Kibana URL separately; do not derive one hostname from the other. Allow HTTPS access from the local machine and, for hosted use, the NAT instance's outbound address. A private endpoint is not reachable without additional networking.

The application owns exactly these indexes:

```text
intent-v1
intent-event-v1
merchant-quote-v1
shopping-run-v1
shopping-revision-v1
shopping-review-v1
paypal-checkout-v1
paypal-webhook-v1
agent-receipt-v1
```

### Create Indexes and Keys

Use a short-lived setup identity in Kibana Dev Tools. For each of the first eight names above, create an empty index, replacing the example name as needed:

```http
PUT intent-v1
{}
```

For receipt search, start with an explicit lexical mapping unless semantic inference is already configured:

```http
PUT agent-receipt-v1
{
  "mappings": {
    "properties": {
      "searchText": { "type": "text" },
      "createdAt": { "type": "date" }
    }
  }
}
```

Other fields use dynamic mappings, including the `.keyword` subfields used by intent/status filters. Do not apply a blanket keyword-only template: current queries expect those subfields. Date values written in receipt documents must be ISO timestamps. Creating an already-existing index returns a conflict; inspect existing mappings instead of deleting records.

Create a separate application API key scoped to the nine exact indexes with `read`, `write`, and `view_index_metadata` privileges. Precreating indexes avoids granting broad index creation privileges. The current receipt store attempts mapping initialization; with precreated lexical mappings, a denied mapping update falls back to lexical search. If you want the application to initialize or change mappings, explicitly grant the required index-management privileges only on these indexes, or perform setup with a separate setup identity. Validate the chosen privileges on your Elastic version. Do not grant cluster-wide `all` or unrelated index access.

Store the returned **encoded** API key privately as `INTENTOS_ELASTIC_API_KEY`. Elasticsearch persistence keys, Kibana management keys, and runtime Agent Builder keys have different purposes and should not be shared. Remove setup access when configuration is complete.

### Optional Semantic Search

Semantic ranking requires a compatible Elastic subscription, an available inference endpoint/model, and `agent-receipt-v1.searchText` mapped as `semantic_text` before ingestion. Configure this using the deployment's supported inference UI/API. Do not change a populated `text` field into `semantic_text` in place; plan a new index/reindex migration and validate queries. `INTENTOS_ELASTIC_SEMANTIC_FIELD=searchText` does not itself provision inference. Search mode must be verified, not inferred from configuration; lexical mode remains usable without semantic inference.

## 2. Configure PayPal Sandbox

1. In PayPal Developer Dashboard, select Sandbox and create a REST app for a Sandbox business account. Keep its client ID and secret server-side.
2. Use a separate Sandbox personal account for buyer approval. Enable Log in with PayPal with `openid`, `profile`, and `email`; configure required policy URLs in the provider dashboard.
3. Configure the exact login callback for your chosen public origin: `https://YOUR_HOST/api/auth/paypal/callback`.
4. Register `https://YOUR_HOST/api/webhooks/paypal` for `PAYMENT.CAPTURE.COMPLETED` on the **same REST app used for Orders**, and store the resulting webhook ID. A webhook ID from another app is not sufficient. AWS deployment verifies/creates this registration automatically.

PayPal cannot deliver webhooks to localhost. A local full payment lifecycle requires a publicly reachable HTTPS tunnel to the application. Forward the frontend port so browser API requests and backend redirects share one origin; Vite forwards `/api` to Spring. Configure the tunnel hostname in Vite's allowed-host settings if required by your provider, without globally disabling host checks. Set the frontend URL, login callback, webhook URL, and cookie settings consistently. Register each environment's callback separately.

For a localhost-only UI/API session, use `http://localhost:5173` and `http://localhost:8080/api/auth/paypal/callback` only if PayPal accepts that callback. This is not sufficient for external webhook or MCP delivery.

## 3. Local Installation with Elastic Cloud

```sh
cp .env.example .env
```

Edit the ignored environment file privately. It must contain shell-compatible assignments; quote values containing shell special characters. Never put secrets in `VITE_*` variables, screenshots, command history, or source files.

Set these groups in that file:

| Group | Values |
| --- | --- |
| Persistence | `INTENTOS_ELASTIC_ENABLED=true`, your Elasticsearch HTTPS endpoint, encoded `INTENTOS_ELASTIC_API_KEY` |
| Model execution | `INTENTOS_LLM_ENABLED=true`, `INTENTOS_LLM_REGION=us-east-1`, default per-task Nova model IDs from the template |
| PayPal | `PAYPAL_ENVIRONMENT=sandbox`, REST client ID/secret, webhook ID for the registered public callback |
| Login | Login app credentials; when using the same app, populate them with the REST credentials rather than leaving explicit blank values; exact `PAYPAL_LOGIN_REDIRECT_URI` |
| Browser origin | `INTENTOS_FRONTEND_URL=http://localhost:5173` for localhost or the HTTPS tunnel origin |
| Access protection | `INTENTOS_AUTH_REQUIRED=true` once login is configured; `SESSION_COOKIE_SECURE=true` for public HTTPS, `false` only for local HTTP |

Authenticate AWS SDK access using your profile/SSO, then export `AWS_PROFILE` in the backend shell. Its role needs `bedrock:InvokeModel` for the configured Nova foundation models in the configured region. Do not place AWS access keys in frontend configuration. If changing models, verify region availability, account access, and IAM resources.

Start the backend from the repository root:

```sh
export AWS_PROFILE=YOUR_PROFILE
set -a
source .env
set +a
./backend/gradlew -p backend bootRun
```

In a second terminal:

```sh
npm ci --prefix frontend
npm run dev --prefix frontend -- --host 127.0.0.1 --port 5173
```

Open <http://localhost:5173/> or the configured public tunnel URL. Use that origin consistently for sign-in and checkout. For a different backend, set `INTENTOS_API_PROXY_TARGET` in the frontend shell before starting Vite. Root environment files are not automatically loaded by Spring Boot: sourcing them in the backend shell is required.

```sh
curl --fail http://localhost:8080/api/health
curl --fail http://localhost:8080/api/auth/session
curl --fail http://localhost:8080/api/integrations/status
```

Health should be `ok`; the session should report login configured; persistence readiness should be enabled. These flags do not prove provider requests succeed. Sign in, create a purchase request, confirm extracted boundaries, and verify a run appears in history. Restart the backend and confirm the same owned records remain accessible after signing in again.

With Elastic disabled, intents and payment state are process-local. Only file quote snapshots persist under the configured directory, by default `backend/.intentos/merchant-quotes` for the Gradle launch. That mode is not durable payment storage. With Elastic enabled, quotes also persist in `merchant-quote-v1`; no quote-directory mount is required for that store.

## 4. AWS Installation from Scratch

### Provision a New Account Environment

Authenticate AWS CLI locally and select the intended account/profile. Verify identity before provisioning. Do not run Terraform using the repository author's account values or an existing environment's state.

```sh
export AWS_PROFILE=YOUR_PROFILE
aws sts get-caller-identity
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
```

Set a unique `project_name`, `aws_region`, desired EC2 instance type, and exact `github_oidc_subject` for your repository and GitHub environment named `main`. The template contains this repository's subject; replace it for a fork. GitHub subject formats depend on repository/organization settings and can include immutable owner/repository IDs. Inspect your actual OIDC subject configuration rather than guessing it. Restrict the `main` environment's deployment branch to `main`.

If the AWS account already has GitHub's OIDC provider or identically named infrastructure, import/reconcile those resources before applying; do not create a second unmanaged copy. Protect Terraform state as sensitive infrastructure data. Never commit state, credentials, or runtime settings. Coordinate state access and configure encrypted remote state/locking for multiple operators.

```sh
terraform init
terraform fmt -check -recursive
terraform validate
terraform plan -out=tfplan
terraform apply tfplan
terraform output
```

Terraform creates VPC/subnets, NAT instance, S3 endpoint, private backend EC2/role, encrypted storage, runtime SecureString, private S3 buckets, CloudFront VPC Origin/distribution, and GitHub OIDC deployment role. Wait for CloudFront deployment and SSM registration. The placeholder SSM value does not start a configured application; the first successful workflow deploys runtime settings and a backend image.

### Configure GitHub Actions

Create GitHub Environment **main** and restrict allowed deployment branches. Set the following variables explicitly from your Terraform outputs; workflow fallbacks refer to the existing installation and must not be used for a new one:

| GitHub variable | Terraform output / value |
| --- | --- |
| `AWS_REGION` | Provisioned region |
| `AWS_DEPLOY_ROLE_ARN` | `github_deploy_role_arn` |
| `PUBLIC_APP_URL` | `app_url`, HTTPS CloudFront origin without a path |
| `CLOUDFRONT_DISTRIBUTION_ID` | `cloudfront_distribution_id` |
| `FRONTEND_BUCKET` | `frontend_bucket_name` |
| `BACKEND_ARTIFACT_BUCKET` | `artifact_bucket_name` |
| `RUNTIME_PARAMETER_NAME` | `runtime_parameter_name` |
| `PAYPAL_ENVIRONMENT` | `sandbox` |
| `INTENTOS_ELASTIC_ENABLED` | `true` |
| `INTENTOS_ELASTIC_URL` | Elasticsearch HTTPS endpoint |
| `INTENTOS_LLM_ENABLED` | `true` |
| `INTENTOS_LLM_REGION` | Model region allowed by the EC2 role |

The workflow discovers one running EC2 instance named `intent-os-backend`. If you change `project_name`, update the workflow's instance-tag filter to match `YOUR_PROJECT-backend`; `BACKEND_INSTANCE_ID` alone does not override this discovery. Other per-task model and semantic-field overrides are described in [integrations.md](integrations.md). The deployment generator currently accepts CloudFront hostnames only; custom-domain deployment needs an explicit validation/configuration change.

Add GitHub Environment secrets `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, `PAYPAL_LOGIN_CLIENT_ID`, `PAYPAL_LOGIN_CLIENT_SECRET`, and `INTENTOS_ELASTIC_API_KEY`. The login values may use the same app; the workflow falls back to REST credentials if omitted. Do not add a static AWS access-key secret. The confirmed webhook ID is provisioned automatically; a stale manually supplied value is not trusted.

Configure the actual Terraform CloudFront URL in the PayPal login return URL before sign-in. Then run **Deploy to AWS** on `main`, or push a reviewed change to `main`. CI builds React, runs Java and script tests, uploads frontend assets and a versioned backend image archive, validates the PayPal webhook registration, writes encrypted runtime settings, and launches the backend through SSM. It invalidates CloudFront and checks public API health.

### Verify the Hosted Installation

```sh
APP_URL="$(terraform output -raw app_url)"
curl --fail "$APP_URL/api/health"
curl --fail "$APP_URL/api/auth/session"
curl --fail "$APP_URL/api/integrations/status"
```

Review the workflow result and SSM command result, not only the frontend build. Sign in with the exact configured hostname; API caching is disabled and cookies must be forwarded. Complete a protected purchase, review, Sandbox approval, capture, and verified receipt. PayPal return parameters are cleaned from the address bar; URL cleanup does not prove a successful payment. Do not retry payment just because receipt delivery is delayed.

## 5. Elastic Agent Builder and MCP

The backend exposes `/api/analyst/mcp`; discovery does not contain user data. Data calls require a short-lived backend-issued capability scoped to the authorized owner/intent. Never add a PayPal cookie, persistence key, or static user scope to the connector.

1. Deploy/run the backend at a public HTTPS origin reachable by Kibana. A localhost connector cannot receive remote callbacks without a tunnel.
2. In Kibana's connector management, create an MCP connector pointing to `https://YOUR_HOST/api/analyst/mcp` and test discovery.
3. Import exactly `search_receipts`, `get_receipt`, `get_decisions`, and `get_metrics` under namespace `intentos`. Do not attach raw Elasticsearch, arbitrary HTTP, write, order, or capture tools.
4. Create a short-lived management API key with Agent Builder tool/agent privileges for the correct space. Supply `KIBANA_URL` and `KIBANA_API_KEY` privately through your terminal environment, then run:

```sh
export KIBANA_SPACE=default
export INTENTOS_MCP_TOOL_IDS=intentos.search_receipts,intentos.get_receipt,intentos.get_decisions,intentos.get_metrics
node scripts/provision-elastic-agent-builder.mjs
unset KIBANA_API_KEY
```

Use actual imported IDs if Kibana generates different IDs; each must end with the matching remote tool name. Use the root Kibana URL and `KIBANA_SPACE` for provisioning, avoiding a doubled `/s/SPACE` prefix. For runtime, use the actual space-scoped base URL when not using the default space.

5. Configure a separate restricted runtime key with permission to converse with the agent and use its MCP connector. Set `ELASTIC_KIBANA_URL`, `ELASTIC_AGENT_BUILDER_API_KEY`, and `ELASTIC_AGENT_BUILDER_AGENT_ID=receipt-analyst` locally. For AWS, add the URL and ID as environment variables in GitHub and the API key as an environment secret, then redeploy. Revoke the temporary management key.
6. Ask a question from an authorized purchase and verify `AGENT_BUILDER_SECURED_MCP`, completed tool calls, and citations to retrieved evidence. Also test a blocked unpaid run and cross-owner denial. When Agent Builder is unconfigured, Bedrock-grounded narration is a separate mode; a configured but failing Agent Builder fails closed rather than silently switching providers.

Capabilities expire after two minutes, allow at most eight tool attempts, and are process-local. Multiple backend replicas need shared capability/session state or affinity before scaling. Restrict trace retention/access: tool-request capabilities are transient secrets and must not be logged or exported.

## 6. Operations and Troubleshooting

| Symptom | Check |
| --- | --- |
| UI loads but API is unavailable locally | Java 21, backend port 8080, exported environment, Vite proxy target. |
| Login fails or capture/receipt returns 401 | Exact callback/origin, Sandbox login credentials, secure-cookie setting; sign in again after backend restart. |
| Bedrock 403/unavailable | AWS identity, region, account model access, and `bedrock:InvokeModel` resource permissions. |
| Elasticsearch 401/403 | Encoded persistence key, index privileges, network allowlist, endpoint. Do not use the Kibana URL for persistence. |
| Missing index or sort mapping | Confirm all nine indexes exist and receipt `createdAt` is a date; inspect templates before ingestion. |
| No receipt after capture | Confirm webhook registration belongs to the Orders REST app and targets `/api/webhooks/paypal`; inspect signed delivery status and backend errors. Capture is not itself webhook evidence. |
| UI keeps waiting despite stored receipt | Reopen the purchase from Shopping history; use the current frontend and an authenticated session. Do not pay again. |
| Analyst fails | Inspect current backend error, Kibana URL/space, privileges, model connector, MCP discovery, attached tools, completed calls, and citations. A configured flag is not an end-to-end check. |
| Deployment assumes wrong role/resources | Set every resource variable from your Terraform outputs; verify environment `main` and exact OIDC subject. |
| EC2 is not managed by SSM | Instance profile, outbound NAT/HTTPS, agent status, region, and tag discovery. No SSH ingress is configured. |

Use SSM Run Command or Session Manager with operator permissions to inspect `docker logs intentos-backend`. The supplied infrastructure does not configure CloudWatch application log shipping. Do not print the runtime environment or decrypted SecureString into command output. Backend restarts invalidate sessions and capabilities but should not erase Elastic-backed records.

The single backend/NAT design is not highly available. `t3.micro` has 1 GiB RAM; monitor memory/swap and choose a larger instance when needed. Instance-type/bootstrap changes can replace the instance. See [aws-deployment.md](aws-deployment.md) for cost and lifecycle details. Protect Elastic data with a snapshot/retention policy, rotate keys, and use separate environment data stores.

Terraform teardown is destructive: buckets allow forced deletion, and the runtime parameter and application infrastructure are removed. Review a destroy plan and back up needed records first. Elastic Cloud, PayPal registrations, and provider transaction history are external resources and are not destroyed by this Terraform configuration.

## 7. Release Verification

### Reset Shopping Records

The AWS reset utility previews counts by default. It deletes documents only from the nine application indexes, retaining mappings, keys, catalog configuration, infrastructure, and external PayPal records. Use a dedicated environment: every user sharing these indexes is affected. Stop other writers and pause deployments first. The operator needs SSM command access, permission to read/decrypt the runtime parameter, and Elasticsearch delete-by-query privileges through the configured key.

```sh
node scripts/reset-shopping-data.mjs \
  --profile YOUR_PROFILE \
  --region us-east-1 \
  --instance-id YOUR_BACKEND_INSTANCE_ID \
  --parameter-name /intent-os/prod/backend-env
```

Review the printed account, host, instance, and counts. Repeat the same command with `--confirm-delete-shopping-data` to delete records. The backend is stopped during deletion and restarted afterward to clear in-memory sessions/capabilities. Unexpected mounts or local runtime files cause refusal, not unreviewed filesystem deletion. On partial failure, inspect the reported index and repeat only after resolving the cause; completed deletions cannot be undone. If restart fails, use SSM to start the backend and check health.

Refresh the browser and sign in again. Clear any pending checkout state in other open tabs and do not resume old PayPal approvals. Old provider events may retry, but deleted checkout records cannot create replacement receipts. This operation does not refund payments, erase provider transaction history, or remove Kibana conversation/trace data or backups. It is an application-record reset, not a provider-wide erasure tool.

```sh
./backend/gradlew -p backend test bootJar
npm ci --prefix frontend
npm run build --prefix frontend
npm run lint --prefix frontend
node --test scripts/*.test.mjs
git diff --check
```

Verify provider behavior separately from unit tests: login, extraction/clarification, complete/incomplete quotes, policy blocking, same-run repair where safe, exact-revision review, Sandbox approval/capture, verified webhook receipt, owner isolation, restart persistence, and evidence-backed analyst answers. Use [scenario-guide.md](scenario-guide.md) for purchase requests and expected outcomes. Do not label unverified semantic ranking, live provider callbacks, or tool execution as completed functionality.