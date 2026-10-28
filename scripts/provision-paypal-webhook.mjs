const captureCompleted = 'PAYMENT.CAPTURE.COMPLETED';

export async function provisionPayPalWebhook({
  clientId, clientSecret, environment = 'sandbox', publicAppUrl, fetchImpl = fetch,
}) {
  if (!clientId || !clientSecret) throw new Error('PayPal REST app credentials are required.');
  if (!['sandbox', 'live'].includes(environment)) throw new Error('Invalid PayPal environment.');
  const appUrl = new URL(publicAppUrl);
  if (appUrl.protocol !== 'https:' || appUrl.username || appUrl.password
    || appUrl.pathname !== '/' || appUrl.search || appUrl.hash) {
    throw new Error('PUBLIC_APP_URL must be an HTTPS origin.');
  }
  const baseUrl = environment === 'live' ? 'https://api-m.paypal.com' : 'https://api-m.sandbox.paypal.com';
  const webhookUrl = `${appUrl.origin}/api/webhooks/paypal`;
  const tokenResponse = await fetchImpl(`${baseUrl}/v1/oauth2/token`, {
    method: 'POST',
    headers: {
      Authorization: `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString('base64')}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: 'grant_type=client_credentials',
    signal: AbortSignal.timeout(30_000),
  });
  if (!tokenResponse.ok) throw new Error(`PayPal token request failed with HTTP ${tokenResponse.status}.`);
  const token = await tokenResponse.json();
  if (!token.access_token) throw new Error('PayPal did not return an access token.');
  const headers = { Authorization: `Bearer ${token.access_token}`, 'Content-Type': 'application/json' };
  async function request(path, options = {}) {
    const response = await fetchImpl(`${baseUrl}/v1/notifications/webhooks${path}`, {
      ...options, headers, signal: AbortSignal.timeout(30_000),
    });
    if (!response.ok) throw new Error(`PayPal webhook request failed with HTTP ${response.status}.`);
    return response.json();
  }
  const registered = await request('');
  if (!Array.isArray(registered.webhooks)) throw new Error('PayPal returned an invalid webhook list.');
  let webhook = registered.webhooks.find(candidate => candidate.url === webhookUrl);
  if (!webhook) {
    webhook = await request('', {
      method: 'POST',
      body: JSON.stringify({ url: webhookUrl, event_types: [{ name: captureCompleted }] }),
    });
  } else if (!webhook.event_types?.some(event => [captureCompleted, '*'].includes(event.name))) {
    webhook = await request(`/${encodeURIComponent(webhook.id)}`, {
      method: 'PATCH',
      body: JSON.stringify([{
        op: 'replace', path: '/event_types',
        value: [...(webhook.event_types ?? []), { name: captureCompleted }].map(event => ({ name: event.name })),
      }]),
    });
  }
  if (!webhook.id || webhook.url !== webhookUrl
    || !webhook.event_types?.some(event => [captureCompleted, '*'].includes(event.name))) {
    throw new Error('PayPal did not confirm the capture-completed webhook registration.');
  }
  return webhook.id;
}