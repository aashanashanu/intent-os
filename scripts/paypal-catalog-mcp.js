import { spawn } from 'node:child_process'

const clientId = process.env.PAYPAL_CLIENT_ID
const clientSecret = process.env.PAYPAL_CLIENT_SECRET
const environment = process.env.PAYPAL_ENVIRONMENT ?? 'SANDBOX'
const apiBase = environment.toUpperCase() === 'PRODUCTION'
  ? 'https://api-m.paypal.com'
  : 'https://api-m.sandbox.paypal.com'

if (!clientId || !clientSecret) {
  console.error('PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET are required to start the PayPal Catalog MCP server.')
  process.exit(1)
}

try {
  const credentials = Buffer.from(`${clientId}:${clientSecret}`).toString('base64')
  const tokenResponse = await fetch(`${apiBase}/v1/oauth2/token`, {
    method: 'POST',
    headers: {
      Authorization: `Basic ${credentials}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: 'grant_type=client_credentials',
  })

  if (!tokenResponse.ok) {
    console.error(`PayPal token request failed with HTTP ${tokenResponse.status}. Check the Sandbox app credentials.`)
    process.exit(1)
  }

  const token = await tokenResponse.json()
  const server = spawn('npx', [
    '--yes',
    '@paypal/mcp',
    '--tools=products.list,products.show',
    `--paypal-environment=${environment.toLowerCase()}`,
  ], {
    stdio: 'inherit',
    env: {
      ...process.env,
      PAYPAL_ACCESS_TOKEN: token.access_token,
      PAYPAL_ENVIRONMENT: environment,
    },
  })

  server.on('error', (error) => {
    console.error(`Could not start PayPal MCP server: ${error.message}`)
    process.exitCode = 1
  })
  server.on('exit', (code, signal) => {
    process.exitCode = signal ? 1 : (code ?? 1)
  })
} catch (error) {
  console.error(`Could not initialize PayPal Catalog MCP: ${error instanceof Error ? error.message : 'unknown error'}`)
  process.exit(1)
}
