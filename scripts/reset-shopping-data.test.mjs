import assert from 'node:assert/strict';
import test from 'node:test';
import { indexes, resetDocuments } from './reset-shopping-data.mjs';

test('preview never mutates records', async () => {
  const calls = [];
  const results = await resetDocuments({ url: 'https://elastic.test', apiKey: 'test', fetchImpl: async (url, options) => {
    calls.push({ url, options });
    return Response.json({ count: 3 });
  } });
  assert.equal(results.length, 9);
  assert.ok(calls.every(call => call.options.method === 'GET'));
});

test('reset deletes documents only from exact application indexes and verifies empty', async () => {
  const cleared = new Set();
  const results = await resetDocuments({ url: 'https://elastic.test', apiKey: 'test', execute: true,
    fetchImpl: async (url, options) => {
      const path = new URL(url).pathname.split('/');
      assert.ok(indexes.includes(path[1]));
      assert.notEqual(options.method, 'DELETE');
      if (options.method === 'POST') {
        assert.deepEqual(JSON.parse(options.body), { query: { match_all: {} } });
        cleared.add(path[1]);
        return Response.json({ deleted: 2, failures: [], version_conflicts: 0 });
      }
      return Response.json({ count: cleared.has(path[1]) ? 0 : 2 });
    } });
  assert.equal(results.reduce((sum, result) => sum + result.deleted, 0), 18);
});

test('reset stops on a partial delete instead of claiming success', async () => {
  await assert.rejects(resetDocuments({ url: 'https://elastic.test', apiKey: 'test', execute: true,
    fetchImpl: async (url, options) => Response.json(options.method === 'POST'
      ? { deleted: 0, failures: ['failure'] } : { count: 1 }),
  }), /deletion incomplete/);
});

test('absent application indexes remain absent', async () => {
  const results = await resetDocuments({ url: 'https://elastic.test', apiKey: 'test', execute: true,
    fetchImpl: async () => new Response(null, { status: 404 }) });
  assert.ok(results.every(result => result.before === 0 && result.deleted === 0));
});