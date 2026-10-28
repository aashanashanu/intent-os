import assert from 'node:assert/strict';
import test from 'node:test';
import { provisionPayPalWebhook } from './provision-paypal-webhook.mjs';

const webhook = {
  id: 'WH-TEST', url: 'https://app.example/api/webhooks/paypal',
  event_types: [{ name: 'PAYMENT.CAPTURE.COMPLETED' }],
};

function fixture(responses) {
  const requests = [];
  const fetchImpl = async (url, options) => {
    requests.push({ url, ...options });
    assert.ok(responses.length, 'Unexpected PayPal request');
    return responses.shift();
  };
  const options = { clientId: 'test-client', clientSecret: 'test-secret',
    publicAppUrl: 'https://app.example', fetchImpl };
  return { requests, options };
}

const response = body => new Response(JSON.stringify(body), { status: 200 });
const tokenResponse = () => response({ access_token: 'test-token' });

test('registers capture webhook when the REST app has none', async () => {
  const { requests, options } = fixture([tokenResponse(), response({ webhooks: [] }), response(webhook)]);
  assert.equal(await provisionPayPalWebhook(options), webhook.id);
  assert.equal(requests[2].method, 'POST');
  assert.deepEqual(JSON.parse(requests[2].body), { url: webhook.url, event_types: webhook.event_types });
});

test('reuses the matching registration without changing other app webhooks', async () => {
  const { requests, options } = fixture([tokenResponse(), response({ webhooks: [
    { ...webhook, id: 'OTHER', url: 'https://other.example/webhook' }, webhook,
  ] })]);
  assert.equal(await provisionPayPalWebhook(options), webhook.id);
  assert.equal(requests.length, 2);
});

test('adds capture completion to an existing subscription', async () => {
  const existing = { ...webhook, event_types: [{ name: 'PAYMENT.CAPTURE.DENIED' }] };
  const updated = { ...webhook, event_types: [...existing.event_types, ...webhook.event_types] };
  const { requests, options } = fixture([tokenResponse(), response({ webhooks: [existing] }), response(updated)]);
  assert.equal(await provisionPayPalWebhook(options), webhook.id);
  assert.equal(requests[2].method, 'PATCH');
  assert.deepEqual(JSON.parse(requests[2].body)[0].value, updated.event_types);
});

test('accepts an existing wildcard subscription', async () => {
  const { requests, options } = fixture([tokenResponse(), response({ webhooks: [
    { ...webhook, event_types: [{ name: '*' }] },
  ] })]);
  assert.equal(await provisionPayPalWebhook(options), webhook.id);
  assert.equal(requests.length, 2);
});

test('fails closed without leaking provider responses on authentication failure', async () => {
  const { options } = fixture([new Response('private response', { status: 401 })]);
  await assert.rejects(provisionPayPalWebhook(options), /^Error: PayPal token request failed with HTTP 401\.$/);
});

test('rejects a registration without the required event', async () => {
  const { options } = fixture([tokenResponse(), response({ webhooks: [] }),
    response({ ...webhook, event_types: [] })]);
  await assert.rejects(provisionPayPalWebhook(options), /did not confirm/);
});

test('fails closed when PayPal refuses registration', async () => {
  const { options } = fixture([tokenResponse(), response({ webhooks: [] }),
    new Response('private response', { status: 403 })]);
  await assert.rejects(provisionPayPalWebhook(options), /^Error: PayPal webhook request failed with HTTP 403\.$/);
});

test('rejects a returned registration for a different callback', async () => {
  const { options } = fixture([tokenResponse(), response({ webhooks: [] }),
    response({ ...webhook, url: 'https://other.example/webhook' })]);
  await assert.rejects(provisionPayPalWebhook(options), /did not confirm/);
});

test('rejects invalid environments and insecure callback origins before network access', async () => {
  const { requests, options } = fixture([]);
  await assert.rejects(provisionPayPalWebhook({ ...options, environment: 'typo' }), /Invalid PayPal environment/);
  await assert.rejects(provisionPayPalWebhook({ ...options, publicAppUrl: 'http://app.example' }), /HTTPS origin/);
  assert.equal(requests.length, 0);
});