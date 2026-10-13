import { writeFileSync } from 'node:fs';

const publicAppUrl = process.env.PUBLIC_APP_URL ?? '';
let appUrl;
try {
  appUrl = new URL(publicAppUrl);
} catch {
  throw new Error('PUBLIC_APP_URL must be the app_url Terraform output.');
}

if (appUrl.protocol !== 'https:' || !appUrl.hostname.endsWith('.cloudfront.net')
  || appUrl.pathname !== '/' || appUrl.search || appUrl.hash) {
  throw new Error('PUBLIC_APP_URL must be the HTTPS CloudFront URL with no path.');
}

const required = ['AWS_REGION', 'BACKEND_ARTIFACT_BUCKET', 'BACKEND_INSTANCE_ID', 'RUNTIME_PARAMETER_NAME', 'DEPLOYMENT_SHA', 'PAYPAL_CLIENT_ID', 'PAYPAL_CLIENT_SECRET'];
for (const key of required) {
  if (!process.env[key]) throw new Error(`${key} is required.`);
}
if (!/^[0-9a-f]{40}$/.test(process.env.DEPLOYMENT_SHA)) {
  throw new Error('DEPLOYMENT_SHA must be a 40-character Git commit SHA.');
}
if (!/^[a-z0-9][a-z0-9.-]{1,62}$/.test(process.env.BACKEND_ARTIFACT_BUCKET)) {
  throw new Error('BACKEND_ARTIFACT_BUCKET must be a valid S3 bucket name.');
}
if (!/^i-[0-9a-f]+$/.test(process.env.BACKEND_INSTANCE_ID)) {
  throw new Error('BACKEND_INSTANCE_ID must be an EC2 instance ID.');
}
if (!/^\/[A-Za-z0-9_.-]+(?:\/[A-Za-z0-9_.-]+)*$/.test(process.env.RUNTIME_PARAMETER_NAME)) {
  throw new Error('RUNTIME_PARAMETER_NAME must be an absolute SSM parameter path.');
}
if (!/^[a-z]{2}(?:-gov)?-[a-z]+-\d$/.test(process.env.AWS_REGION)) {
  throw new Error('AWS_REGION must be a valid AWS region name.');
}
if (process.env.INTENTOS_ELASTIC_ENABLED === 'true'
  && (!process.env.INTENTOS_ELASTIC_URL || !process.env.INTENTOS_ELASTIC_API_KEY)) {
  throw new Error('Elastic is enabled; set INTENTOS_ELASTIC_URL and INTENTOS_ELASTIC_API_KEY.');
}

const environment = {
  SESSION_COOKIE_SECURE: 'true',
  INTENTOS_AUTH_REQUIRED: 'true',
  INTENTOS_FRONTEND_URL: appUrl.origin,
  PAYPAL_ENVIRONMENT: process.env.PAYPAL_ENVIRONMENT || 'sandbox',
  PAYPAL_CLIENT_ID: process.env.PAYPAL_CLIENT_ID,
  PAYPAL_CLIENT_SECRET: process.env.PAYPAL_CLIENT_SECRET,
  PAYPAL_WEBHOOK_ID: process.env.PAYPAL_WEBHOOK_ID || '',
  PAYPAL_LOGIN_CLIENT_ID: process.env.PAYPAL_LOGIN_CLIENT_ID || process.env.PAYPAL_CLIENT_ID,
  PAYPAL_LOGIN_CLIENT_SECRET: process.env.PAYPAL_LOGIN_CLIENT_SECRET || process.env.PAYPAL_CLIENT_SECRET,
  PAYPAL_LOGIN_REDIRECT_URI: `${appUrl.origin}/api/auth/paypal/callback`,
  INTENTOS_ELASTIC_ENABLED: process.env.INTENTOS_ELASTIC_ENABLED || 'false',
  INTENTOS_ELASTIC_URL: process.env.INTENTOS_ELASTIC_URL || '',
  INTENTOS_ELASTIC_API_KEY: process.env.INTENTOS_ELASTIC_API_KEY || '',
  INTENTOS_LLM_ENABLED: process.env.INTENTOS_LLM_ENABLED || 'true',
  INTENTOS_LLM_REGION: process.env.INTENTOS_LLM_REGION || process.env.AWS_REGION,
  INTENTOS_LLM_MODEL_EXTRACTION: process.env.INTENTOS_LLM_MODEL_EXTRACTION || 'amazon.nova-micro-v1:0',
  INTENTOS_LLM_MODEL_REASONING: process.env.INTENTOS_LLM_MODEL_REASONING || 'amazon.nova-lite-v1:0',
  INTENTOS_LLM_MODEL_REPAIR: process.env.INTENTOS_LLM_MODEL_REPAIR || 'amazon.nova-lite-v1:0',
  INTENTOS_LLM_MODEL_EXPLANATION: process.env.INTENTOS_LLM_MODEL_EXPLANATION || 'amazon.nova-micro-v1:0',
  INTENTOS_LLM_MAX_TOKENS: process.env.INTENTOS_LLM_MAX_TOKENS || '700',
};

for (const [key, value] of Object.entries(environment)) {
  if (/[\r\n]/.test(value)) throw new Error(`${key} must not contain newline characters.`);
}

const runtimeValue = Object.entries(environment).map(([key, value]) => `${key}=${value}`).join('\n');
writeFileSync('runtime-parameter.json', JSON.stringify({
  Name: process.env.RUNTIME_PARAMETER_NAME,
  Type: 'SecureString',
  Value: runtimeValue,
  Overwrite: true,
}, null, 2), { mode: 0o600 });

const artifact = `s3://${process.env.BACKEND_ARTIFACT_BUCKET}/releases/${process.env.DEPLOYMENT_SHA}/backend-image.tar.gz`;
const commands = [
  'set -euo pipefail',
  `aws ssm get-parameter --name '${process.env.RUNTIME_PARAMETER_NAME}' --with-decryption --query Parameter.Value --output text > /opt/intent-os/backend.env`,
  'chmod 600 /opt/intent-os/backend.env',
  `aws s3 cp '${artifact}' /tmp/backend-image.tar.gz`,
  'gunzip -c /tmp/backend-image.tar.gz | docker load',
  'docker rm --force intentos-backend >/dev/null 2>&1 || true',
  `docker run --detach --name intentos-backend --restart unless-stopped --env-file /opt/intent-os/backend.env --publish 8080:8080 intentos-backend:${process.env.DEPLOYMENT_SHA}`,
  'rm -f /tmp/backend-image.tar.gz',
];
writeFileSync('ssm-command.json', JSON.stringify({ commands }, null, 2), { mode: 0o600 });