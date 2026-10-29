import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

export const indexes = Object.freeze([
  'intent-v1', 'intent-event-v1', 'merchant-quote-v1',
  'shopping-run-v1', 'shopping-revision-v1', 'shopping-review-v1',
  'paypal-checkout-v1', 'paypal-webhook-v1', 'agent-receipt-v1',
]);

export async function resetDocuments({ url, apiKey, execute = false, fetchImpl = fetch }) {
  const endpoint = new URL(url);
  if (endpoint.protocol !== 'https:' || endpoint.username || endpoint.password || endpoint.search || endpoint.hash) {
    throw new Error('Elasticsearch must use an HTTPS endpoint without embedded credentials.');
  }
  if (!apiKey) throw new Error('Elasticsearch API key is missing.');
  async function request(index, operation, body) {
    const response = await fetchImpl(`${endpoint.href.replace(/\/$/, '')}/${index}/${operation}`, {
      method: body ? 'POST' : 'GET',
      headers: { Authorization: `ApiKey ${apiKey}`, 'Content-Type': 'application/json' },
      ...(body ? { body: JSON.stringify(body) } : {}),
      signal: AbortSignal.timeout(120_000),
    });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`${index}: ${operation.split('?')[0]} failed with HTTP ${response.status}`);
    return response.json();
  }
  const results = [];
  for (const index of indexes) {
    const before = await request(index, '_count');
    if (before && (before._shards?.failed || !Number.isInteger(before.count))) {
      throw new Error(`${index}: incomplete count response`);
    }
    let deleted = 0;
    if (execute && before) {
      const result = await request(index, '_delete_by_query?refresh=true&conflicts=abort', { query: { match_all: {} } });
      if (!result || result.timed_out || result.failures?.length || result.version_conflicts
        || !Number.isInteger(result.deleted)) throw new Error(`${index}: deletion incomplete; inspect before retrying`);
      deleted = result.deleted;
      const after = await request(index, '_count');
      if (!after || after.count !== 0 || after._shards?.failed) throw new Error(`${index}: zero remaining records could not be verified`);
    }
    results.push({ index, before: before?.count ?? 0, deleted });
  }
  return results;
}

async function main() {
  const args = process.argv.slice(2);
  if (args.includes('--help')) {
    console.log('Usage: node scripts/reset-shopping-data.mjs --profile PROFILE --region REGION --instance-id INSTANCE --parameter-name PARAMETER [--confirm-delete-shopping-data]');
    console.log('Default: count-only preview. Explicit confirmation deletes app documents, preserves mappings/configuration, and restarts the backend.');
    return;
  }
  const options = {};
  for (let position = 0; position < args.length; position++) {
    const key = args[position];
    if (key === '--confirm-delete-shopping-data') { options.execute = true; continue; }
    if (!['--profile', '--region', '--instance-id', '--parameter-name'].includes(key)
      || !args[position + 1] || args[position + 1].startsWith('--')) throw new Error('Invalid arguments; use --help.');
    options[key.slice(2)] = args[++position];
  }
  for (const key of ['profile', 'region', 'instance-id', 'parameter-name']) {
    if (!options[key]) throw new Error(`Missing --${key}; no default production target is assumed.`);
  }
  if (!/^i-[a-f0-9]+$/.test(options['instance-id'])) throw new Error('Invalid instance ID.');
  function aws(args) {
    try {
      return execFileSync('aws', ['--profile', options.profile, '--region', options.region, ...args], {
        encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 1024 * 1024,
      });
    } catch { throw new Error(`AWS ${args[0]} ${args[1]} failed. Check identity, region, permissions, and SSM status.`); }
  }
  const identity = JSON.parse(aws(['sts', 'get-caller-identity', '--output', 'json']));
  const parameter = JSON.parse(aws(['ssm', 'get-parameter', '--name', options['parameter-name'], '--with-decryption', '--output', 'json'])).Parameter;
  const env = Object.fromEntries(parameter.Value.split('\n').filter(line => line.includes('=')).map(line => {
    const separator = line.indexOf('=');
    return [line.slice(0, separator), line.slice(separator + 1)];
  }));
  if (env.INTENTOS_ELASTIC_ENABLED !== 'true') throw new Error('Target must use Elasticsearch persistence. Nothing was deleted.');
  const instance = JSON.parse(aws(['ec2', 'describe-instances', '--instance-ids', options['instance-id'], '--output', 'json']));
  const resource = instance.Reservations?.[0]?.Instances?.[0];
  if (resource?.State?.Name !== 'running'
    || !resource.Tags?.some(tag => tag.Key === 'ManagedBy' && tag.Value === 'Terraform')
    || !resource.Tags?.some(tag => tag.Key === 'Name' && tag.Value.endsWith('-backend'))) {
    throw new Error('Expected a running Terraform-managed backend instance. Nothing was deleted.');
  }
  function remote(commands) {
    const commandId = aws(['ssm', 'send-command', '--instance-ids', options['instance-id'],
      '--document-name', 'AWS-RunShellScript', '--parameters', JSON.stringify({ commands: ['set -eu', ...commands] }),
      '--query', 'Command.CommandId', '--output', 'text']).trim();
    aws(['ssm', 'wait', 'command-executed', '--command-id', commandId, '--instance-id', options['instance-id']]);
    const result = JSON.parse(aws(['ssm', 'get-command-invocation', '--command-id', commandId,
      '--instance-id', options['instance-id'], '--output', 'json']));
    if (result.Status !== 'Success') throw new Error(`Backend command failed: ${commandId}`);
  }
  console.log(`Account: ${identity.Account}; region: ${options.region}; instance: ${options['instance-id']}`);
  console.log(`Elasticsearch: ${new URL(env.INTENTOS_ELASTIC_URL).hostname}`);
  console.log(options.execute ? 'DELETING shopping records. Coordinate with other users and pause deployments first.' : 'PREVIEW ONLY: no changes will be made.');
  if (options.execute) {
    remote([
      'test "$(docker inspect --format \'{{.State.Running}}\' intentos-backend)" = true',
      'test "$(docker inspect --format \'{{len .Mounts}}\' intentos-backend)" = 0',
      'test -z "$(docker exec intentos-backend sh -c \'find /app/.intentos -type f -print -quit 2>/dev/null || true\')"',
    ]);
  }
  let stopAttempted = false;
  try {
    if (options.execute) {
      stopAttempted = true;
      remote(['docker stop intentos-backend >/dev/null']);
    }
    const results = await resetDocuments({ url: env.INTENTOS_ELASTIC_URL, apiKey: env.INTENTOS_ELASTIC_API_KEY, execute: options.execute });
    for (const result of results) console.log(`${result.index}: ${options.execute ? `deleted ${result.deleted}; verified empty` : `${result.before} records`}`);
  } finally {
    if (stopAttempted) remote(['docker start intentos-backend >/dev/null']);
  }
  console.log(options.execute
    ? 'Reset complete. Backend restarted; sign in again and refresh your browser. Provider transaction history is unchanged.'
    : 'Add --confirm-delete-shopping-data only after reviewing this target and counts.');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}