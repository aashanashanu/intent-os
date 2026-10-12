# AWS S3, CloudFront, and EC2 Deployment

This deployment needs **no custom domain or DNS setup**. CloudFront provides a public `https://d....cloudfront.net` hostname and HTTPS. Its default behavior serves the React build from a private S3 bucket; `/api/*`, including the PayPal callback, routes to a small EC2 Spring Boot backend. EC2 accepts port 8080 only from CloudFront origin-facing addresses and is managed by Systems Manager, not SSH.

The default `t3.micro` backend and `t3.micro` NAT instance each have 1 GiB RAM and cost roughly $7.60/month in `us-east-1`. Including the NAT public IPv4 address and the two encrypted gp3 disks, budget roughly $20/month before S3, CloudFront, transfer, and tax. Pricing varies by region and account eligibility; check AWS pricing before applying. The app uses in-memory intent storage unless Elastic is enabled, so data is not durable across backend replacements/restarts.

`t3.micro` is intended for a low-traffic demo. The container JVM is limited to a 45% maximum heap and uses Serial GC; EC2 also configures 1 GiB of encrypted swap as a short-lived memory-pressure buffer. Swap is slower than RAM and is not a substitute for capacity. Upgrade `ec2_instance_type` to `t3.small` if the process swaps persistently, restarts, or becomes slow under concurrent requests.

## Provision infrastructure

Configure AWS CLI credentials locally with permission to create the Terraform resources. The selected AWS account must allow creating a GitHub OIDC provider. Terraform creates public/private subnets, a small NAT instance, an S3 gateway endpoint, the private EC2 backend, and a CloudFront VPC Origin. From the repository root:

```sh
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
```

Set `github_oidc_subject` to the exact subject format GitHub sends for this repository. This repository uses the immutable owner/repository IDs shown in `terraform.tfvars.example`; the branch must match the workflow trust (`main`). Then run:

```sh
terraform init
terraform plan
terraform apply
```

Terraform outputs the values needed by CI:

```sh
terraform output -raw app_url
terraform output -raw cloudfront_distribution_id
terraform output -raw frontend_bucket_name
terraform output -raw artifact_bucket_name
terraform output -raw backend_instance_id
terraform output -raw runtime_parameter_name
terraform output -raw github_deploy_role_arn
```

CloudFront can take several minutes to deploy. The first CI run waits for EC2 to register with Systems Manager before deploying the backend.

## Configure CI/CD

In repository **Settings → Secrets and variables → Actions**, set these repository variables from Terraform outputs:

- `AWS_REGION`: Terraform region; optional when using the default `us-east-1` region. Set it explicitly if Terraform was provisioned in another region.
- `AWS_DEPLOY_ROLE_ARN`: optional override for the `github_deploy_role_arn` output. The workflow defaults to this repository's deployed role.
- `PUBLIC_APP_URL`: `app_url`, including `https://` and no path.
- `CLOUDFRONT_DISTRIBUTION_ID`: `cloudfront_distribution_id`.
- `FRONTEND_BUCKET`: `frontend_bucket_name`.
- `BACKEND_ARTIFACT_BUCKET`: `artifact_bucket_name`.
- `BACKEND_INSTANCE_ID`: `backend_instance_id`.
- `RUNTIME_PARAMETER_NAME`: `runtime_parameter_name`.
- `PAYPAL_ENVIRONMENT`: `sandbox` or `live`.
- `INTENTOS_ELASTIC_ENABLED`: `false` unless Elastic is configured.
- `INTENTOS_ELASTIC_URL`: optional Elasticsearch HTTPS endpoint.

Add these GitHub repository secrets:

- `PAYPAL_CLIENT_ID` and `PAYPAL_CLIENT_SECRET`: PayPal REST app credentials.
- `PAYPAL_LOGIN_CLIENT_ID` and `PAYPAL_LOGIN_CLIENT_SECRET`: optional separate Login credentials; if unset, REST credentials are used.
- `INTENTOS_ELASTIC_API_KEY`: optional encoded Elasticsearch API key.

No AWS access-key secret is needed. GitHub Actions assumes the repository-scoped IAM role using OIDC. Push to `main` or run **Deploy to AWS** from the Actions tab. CI builds and tests Spring Boot, builds React, syncs the frontend to S3, stores the backend image in a private deployment bucket, stores runtime credentials as an SSM `SecureString`, and uses SSM Run Command to restart the container. It invalidates CloudFront after deployment. The workflow role trusts only the configured repository's `main` branch.

## Configure PayPal Login

Get the exact Terraform `app_url` output and set PayPal Developer Dashboard **Log in with PayPal → Advanced Settings → Return URL** to:

```text
https://d123example.cloudfront.net/api/auth/paypal/callback
```

Replace the sample host with the actual CloudFront hostname. Enable `openid`, `profile`, and `email`. CI configures `PAYPAL_LOGIN_REDIRECT_URI` to the identical URL and enables secure session cookies and required authentication.

CloudFront supplies the public HTTPS certificate. PayPal decides which hostnames it permits, so confirm the generated `cloudfront.net` hostname is accepted. If PayPal rejects it, a custom domain and DNS configuration will be required.

After deployment, verify:

```sh
APP_URL="$(cd infra/terraform && terraform output -raw app_url)"
curl -fsS "$APP_URL/api/health"
curl -fsS "$APP_URL/api/auth/session"
```

The session response should show `loginConfigured: true` without exposing credentials. Test sign-in using the exact registered CloudFront hostname.

## Costs and operations

- CloudFront disables caching for `/api/*` and forwards cookies/query strings; frontend files are served from private S3 using Origin Access Control.
- The application EC2 is in a private subnet with no public IP or SSH ingress. CloudFront VPC Origins provide its private API connection. A small NAT instance provides outbound PayPal/SSM connectivity; S3 uses a gateway endpoint. The NAT instance has no inbound Internet rule and only accepts forwarded traffic from the private application subnet.
- Backend image archives expire from S3 after 30 days. The EC2 role reads runtime settings from SSM Parameter Store `SecureString`.
- `terraform destroy` deletes the CloudFront distribution, EC2 instance, SSM parameter, and buckets. The buckets are configured for Terraform-managed deletion.
- This single EC2 instance is a demo deployment, not high availability. Increase `ec2_instance_type` if memory or traffic requires it; changing the instance type replaces/restarts the backend instance.
- Review `terraform plan` before applying the new bootstrap. Because user data now creates swap, Terraform may replace an already-deployed instance; back up any in-memory-only intent data first.
- Rotate credentials previously exposed in chat or a frontend environment file. Store replacements only in GitHub Actions secrets.
