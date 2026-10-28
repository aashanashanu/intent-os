import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const kibanaUrl = process.env.KIBANA_URL;
const apiKey = process.env.KIBANA_API_KEY;
const space = process.env.KIBANA_SPACE;
if (!kibanaUrl || !apiKey) {
  throw new Error('Set KIBANA_URL and KIBANA_API_KEY in the shell before provisioning.');
}

let baseUrl;
try {
  baseUrl = new URL(kibanaUrl);
} catch {
  throw new Error('KIBANA_URL must be an absolute URL.');
}
if (baseUrl.protocol !== 'https:') throw new Error('KIBANA_URL must use HTTPS.');
if (baseUrl.hostname.includes('.es.') && baseUrl.hostname.endsWith('.elastic.cloud')) {
  throw new Error('KIBANA_URL points to Elasticsearch, not Kibana. Copy the Kibana URL from Elastic Cloud (Open Kibana); do not use the Elasticsearch endpoint.');
}
if (baseUrl.username || baseUrl.password || baseUrl.search || baseUrl.hash) {
  throw new Error('KIBANA_URL must not contain credentials, a query, or a fragment.');
}
if (space && !/^[A-Za-z0-9_-]+$/.test(space)) throw new Error('KIBANA_SPACE has an invalid format.');

const configPath = fileURLToPath(new URL('../elastic/agent-builder/receipt-analyst.json', import.meta.url));
const config = JSON.parse(readFileSync(configPath, 'utf8'));
const mcpToolIds = (process.env.INTENTOS_MCP_TOOL_IDS || '').split(',').map((id) => id.trim()).filter(Boolean);
if (mcpToolIds.length !== 4 || new Set(mcpToolIds).size !== 4) {
  throw new Error('Set INTENTOS_MCP_TOOL_IDS to the four tool IDs imported from the IntentOS MCP connector.');
}
const apiPrefix = `${space && space !== 'default' ? `/s/${space}` : ''}/api/agent_builder`;

async function request(method, path, body) {
  const response = await fetch(`${baseUrl.origin}${baseUrl.pathname.replace(/\/$/, '')}${apiPrefix}${path}`, {
    method,
    headers: {
      Authorization: `ApiKey ${apiKey}`,
      'kbn-xsrf': 'true',
      'Content-Type': 'application/json',
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  if (response.ok) return response.status === 204 ? null : response.json();
  const status = response.status;
  await response.body?.cancel();
  throw new Error(`Kibana Agent Builder API ${method} ${path} failed with HTTP ${status}.`);
}

async function upsertAgent(agent) {
  const path = `/agents/${encodeURIComponent(agent.id)}`;
  let exists = false;
  try {
    await request('GET', path);
    exists = true;
  } catch (error) {
    if (!String(error.message).includes('HTTP 404')) throw error;
  }
  const updateBody = agent;
  if (exists) await request('PUT', path, updateBody);
  else await request('POST', '/agents', agent);
}

const remoteNames = new Set();
for (const id of mcpToolIds) {
  const tool = await request('GET', `/tools/${encodeURIComponent(id)}`);
  if (tool.type !== 'mcp') throw new Error(`Tool ${id} is not a remote MCP tool.`);
  const name = ['search_receipts', 'get_receipt', 'get_decisions', 'get_metrics'].find((candidate) => id.endsWith(`.${candidate}`));
  if (!name || remoteNames.has(name)) throw new Error(`Tool ${id} does not match the required IntentOS MCP tool set.`);
  remoteNames.add(name);
}
config.agent.configuration.tools = [{ tool_ids: mcpToolIds }];
await upsertAgent(config.agent);
console.log(`Attached ${mcpToolIds.length} imported MCP tools to agent "${config.agent.id}".`);