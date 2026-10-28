import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const toolIds = ['search_receipts', 'get_receipt', 'get_decisions', 'get_metrics'].map((name) => `intentos.${name}`);

function provision(type, ids = toolIds) {
  return spawnSync(process.execPath, ['--input-type=module', '-e', `
    globalThis.fetch = async (url, options) => {
      const path = new URL(url).pathname;
      if (options.method === 'GET' && path.includes('/tools/')) {
        return Response.json({type: ${JSON.stringify(type)}});
      }
      if (options.method === 'GET' && path.includes('/agents/')) {
        return new Response(null, {status: 404});
      }
      if (options.method === 'POST' && path.endsWith('/agents')) {
        const body = JSON.parse(options.body);
        if (JSON.stringify(body.configuration.tools) !== JSON.stringify([{tool_ids: ${JSON.stringify(ids)}}])) {
          throw new Error('Unexpected attached tool set');
        }
        return Response.json({id: body.id});
      }
      throw new Error('Unexpected API request: ' + options.method + ' ' + path);
    };
    await import('./scripts/provision-elastic-agent-builder.mjs');
  `], {
    cwd: new URL('../', import.meta.url),
    encoding: 'utf8',
    env: { ...process.env, KIBANA_URL: 'https://kibana.test', KIBANA_API_KEY: 'test-key', KIBANA_SPACE: 'default', INTENTOS_MCP_TOOL_IDS: ids.join(',') },
  });
}

test('attaches exactly four validated remote MCP tools', () => {
  const result = provision('mcp');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Attached 4 imported MCP tools/);
});

test('rejects raw Elasticsearch tools instead of attaching them', () => {
  const result = provision('esql');
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /not a remote MCP tool/);
});

test('rejects incomplete or duplicate MCP tool sets', () => {
  assert.notEqual(provision('mcp', toolIds.slice(0, 3)).status, 0);
  assert.notEqual(provision('mcp', [toolIds[0], toolIds[0], toolIds[2], toolIds[3]]).status, 0);
});