import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { request as httpRequest } from 'node:http';
import { rm } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import { LATEST_PROTOCOL_VERSION } from '@modelcontextprotocol/sdk/types.js';
import {
  copyServiceToFixture, createFixture, DOCUMENT_NAMES, SERVER_PATH,
} from './fixtures.mjs';

async function startFixtureHttp(t, fixture) {
  assert.ok(existsSync(SERVER_PATH), 'HTTP 协议服务尚未实现：缺少 server.mjs');
  const entry = await copyServiceToFixture(fixture);
  const child = spawn(process.execPath, [entry, '--http', '--port', '0'], {
    cwd: fixture.workingDirectory,
    windowsHide: true,
    stdio: ['pipe', 'pipe', 'pipe'],
  });
  let stdout = '';
  let stderr = '';
  child.stdout.on('data', (chunk) => { stdout += chunk.toString('utf8'); });
  const exit = new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('exit', (code, signal) => resolve({ code, signal }));
  });
  const stop = async () => {
    if (child.exitCode === null && child.signalCode === null) child.kill();
    await exit;
    assert.equal(stdout, '', 'HTTP 模式也不得向 stdout 输出日志');
  };
  // 连接/启动异常也清理；正常测试另在 finally 中显式关闭。
  t.after(stop);
  const endpoint = await new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('exit', () => reject(new Error('临时 HTTP 夹具服务未就绪即退出')));
    child.stderr.on('data', (chunk) => {
      stderr += chunk.toString('utf8');
      const match = stderr.match(/http:\/\/127\.0\.0\.1:(\d+)\/mcp/);
      if (match) resolve(new URL(match[0]));
    });
  });
  assert.notEqual(endpoint.port, '19540', '测试只用随机端口，不使用正式展示端口');
  return { endpoint, stop };
}

function assertNoLeak(result, fixture) {
  const text = JSON.stringify(result);
  assert.ok(!text.includes(fixture.temporaryRoot));
  assert.ok(!text.includes(fixture.temporaryRoot.replaceAll('\\', '\\\\')));
  assert.doesNotMatch(text, /node:internal|Error:.*\n\s+at|\bstack\s*:/);
}

test('官方 HTTP Client：随机夹具端口 initialize / tools/list / tools/call，finally 关闭', { timeout: 20000 }, async (t) => {
  const fixture = await createFixture(t);
  const service = await startFixtureHttp(t, fixture);
  const transport = new StreamableHTTPClientTransport(service.endpoint);
  const client = new Client({ name: '资料 HTTP 协议验收', version: '1.0.0' });
  try {
    await client.connect(transport);
    assert.equal(client.getServerVersion().name, '制造运营资料服务');
    assert.deepEqual(Object.keys(client.getServerCapabilities()), ['tools']);
    assert.equal(transport.sessionId, undefined, '官方 stateless 模式不能创建会话');
    const { tools } = await client.listTools();
    assert.deepEqual(tools.map((tool) => tool.name), ['list_documents', 'read_document']);
    assert.ok(tools.every((tool) => tool.annotations.readOnlyHint && !tool.annotations.openWorldHint));
    const listing = await client.callTool({ name: 'list_documents', arguments: {} });
    assert.deepEqual(listing.structuredContent.documents.map((document) => document.name), DOCUMENT_NAMES);
    for (const name of DOCUMENT_NAMES) {
      const read = await client.callTool({ name: 'read_document', arguments: { document_name: name } });
      assert.notEqual(read.isError, true);
      assert.equal(read.content[0].text, fixture.contents[name]);
    }
    for (const name of ['../README.md', path.join(fixture.outsideDirectory, '秘密.md')]) {
      const rejected = await client.callTool({ name: 'read_document', arguments: { document_name: name } });
      assert.equal(rejected.isError, true);
      assert.equal(rejected.structuredContent.error.code, 'DOCUMENT_NOT_ALLOWED');
      assertNoLeak(rejected, fixture);
    }
  } finally {
    await client.close();
    await service.stop();
  }
});

test('HTTP 缺文件失败不吐磁盘路径/堆栈、不生成正文', { timeout: 20000 }, async (t) => {
  const fixture = await createFixture(t);
  await rm(path.join(fixture.displayDirectory, DOCUMENT_NAMES[0]));
  const service = await startFixtureHttp(t, fixture);
  const client = new Client({ name: '资料 HTTP 错误验收', version: '1.0.0' });
  try {
    await client.connect(new StreamableHTTPClientTransport(service.endpoint));
    const result = await client.callTool({ name: 'read_document', arguments: { document_name: DOCUMENT_NAMES[0] } });
    assert.equal(result.isError, true);
    assert.equal(result.structuredContent.error.code, 'DOCUMENT_NOT_FOUND');
    assert.match(result.content[0].text, /采购与库存作业规范.*缺失.*不会编造/);
    assertNoLeak(result, fixture);
  } finally {
    await client.close();
    await service.stop();
  }
});

test('HTTP 端点/Origin/非法请求受限，stateless 初始化没有 session header', { timeout: 20000 }, async (t) => {
  const fixture = await createFixture(t);
  const service = await startFixtureHttp(t, fixture);
  const headers = { 'content-type': 'application/json', accept: 'application/json, text/event-stream' };
  const body = JSON.stringify({
    jsonrpc: '2.0', id: 1, method: 'initialize', params: {
      protocolVersion: LATEST_PROTOCOL_VERSION,
      capabilities: {}, clientInfo: { name: 'HTTP 端点验收', version: '1.0.0' },
    },
  });
  try {
    const initialize = await fetch(service.endpoint, { method: 'POST', headers, body });
    assert.equal(initialize.status, 200);
    assert.equal(initialize.headers.get('mcp-session-id'), null);
    assert.equal((await initialize.json()).result.serverInfo.name, '制造运营资料服务');
    const otherPath = await fetch(new URL('/not-mcp', service.endpoint));
    assert.equal(otherPath.status, 404);
    assertNoLeak(await otherPath.json(), fixture);
    const foreignOrigin = await fetch(service.endpoint, { method: 'POST', headers: {
      ...headers, origin: 'https://untrusted.invalid',
    }, body });
    assert.equal(foreignOrigin.status, 403);
    assertNoLeak(await foreignOrigin.json(), fixture);
    // 规范要求所有进入 MCP 端点的连接先校验 Origin，不能被 GET/DELETE 的 405 分支绕过。
    for (const method of ['GET', 'DELETE']) {
      const foreign = await fetch(service.endpoint, { method, headers: {
        accept: 'text/event-stream', origin: 'https://untrusted.invalid',
      } });
      assert.equal(foreign.status, 403, `${method} 也必须拒绝非法 Origin`);
      assertNoLeak(await foreign.json(), fixture);
    }
    const malformed = await fetch(service.endpoint, { method: 'POST', headers, body: '{' });
    assert.equal(malformed.status, 400);
    assertNoLeak(await malformed.json(), fixture);
    const get = await fetch(service.endpoint, { headers: { accept: 'text/event-stream' } });
    assert.equal(get.status, 405, 'stateless 服务不保留 GET SSE 常驻流');
    assertNoLeak(await get.json(), fixture);
  } finally {
    await service.stop();
  }
});

// 真实 TCP 请求允许精确覆盖 Host；连接目的地始终是本轮随机端口的127.0.0.1。
async function getJsonWithHeaders(url, headers = {}) {
  return new Promise((resolve, reject) => {
    const request = httpRequest(url, { method: 'GET', headers }, (response) => {
      const peer = { address: response.socket.remoteAddress, port: response.socket.remotePort };
      let body = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => { body += chunk; });
      response.once('error', reject);
      response.once('end', () => {
        try {
          resolve({ status: response.statusCode, headers: response.headers, json: JSON.parse(body), peer });
        } catch (error) {
          reject(error);
        }
      });
    });
    request.once('error', reject);
    request.end();
  });
}

test('GET /catalog 返回一个真实服务，baseUrl 使用实际 bound port，官方 Client 可连接发现调用', { timeout: 20000 }, async (t) => {
  const fixture = await createFixture(t);
  const service = await startFixtureHttp(t, fixture);
  const client = new Client({ name: '市场目录真实来源验收', version: '1.0.0' });
  try {
    const result = await getJsonWithHeaders(new URL('/catalog', service.endpoint));
    assert.equal(result.status, 200, '必须提供只读市场目录，不能把市场 URL 当成 /mcp');
    assert.match(result.headers['content-type'], /^application\/json/);
    assert.deepEqual(Object.keys(result.json), ['servers']);
    assert.equal(result.json.servers.length, 1, '市场条目是一个服务，不是两个协议工具');
    const entry = result.json.servers[0];
    assert.equal(entry.name, 'manufacturing_operations_documents');
    assert.match(entry.description, /制造运营资料服务/);
    assert.match(entry.description, /业务资料/);
    assert.equal(entry.version, '1.0.0');
    assert.deepEqual(entry.tools, ['list_documents', 'read_document']);
    assert.equal(result.peer.address, '127.0.0.1');
    assert.equal(entry.baseUrl, `http://127.0.0.1:${result.peer.port}/mcp`);
    assert.equal(entry.baseUrl, service.endpoint.href);
    t.diagnostic(JSON.stringify({
      catalog_url: new URL('/catalog', service.endpoint).href,
      bound_peer: result.peer,
      servers: result.json.servers,
    }));
    assertNoLeak(result.json, fixture);
    await client.connect(new StreamableHTTPClientTransport(new URL(entry.baseUrl)));
    assert.equal(client.getServerVersion().name, '制造运营资料服务');
    assert.deepEqual((await client.listTools()).tools.map((tool) => tool.name), ['list_documents', 'read_document']);
    const read = await client.callTool({ name: 'read_document', arguments: { document_name: DOCUMENT_NAMES[0] } });
    assert.notEqual(read.isError, true);
    assert.equal(read.content[0].text, fixture.contents[DOCUMENT_NAMES[0]]);
  } finally {
    await client.close();
    await service.stop();
  }
});

test('目录拒绝注入 Host/Origin，合法裸 Host 和伪造转发头不能改写 bound baseUrl', { timeout: 20000 }, async (t) => {
  const fixture = await createFixture(t);
  const service = await startFixtureHttp(t, fixture);
  const catalogUrl = new URL('/catalog', service.endpoint);
  try {
    const otherPort = Number(service.endpoint.port) === 65535 ? 65534 : Number(service.endpoint.port) + 1;
    for (const host of ['untrusted.invalid', `127.0.0.1:${otherPort}`, `untrusted.invalid:${service.endpoint.port}`]) {
      const rejected = await getJsonWithHeaders(catalogUrl, { host });
      assert.equal(rejected.status, 403, '注入 Host 必须被拒绝，不能改变目录来源');
      assertNoLeak(rejected.json, fixture);
      assert.ok(!JSON.stringify(rejected.json).includes(host), '不得反射注入 Host');
    }
    const foreignOrigin = await getJsonWithHeaders(catalogUrl, { origin: 'https://untrusted.invalid' });
    assert.equal(foreignOrigin.status, 403);
    assertNoLeak(foreignOrigin.json, fixture);
    const bareHost = await getJsonWithHeaders(catalogUrl, {
      host: '127.0.0.1',
      'x-forwarded-host': 'untrusted.invalid:1',
      'x-forwarded-port': '1',
      'x-forwarded-proto': 'https',
      forwarded: 'host=untrusted.invalid:1;proto=https',
    });
    assert.equal(bareHost.status, 200);
    assert.equal(bareHost.json.servers.length, 1);
    assert.equal(bareHost.json.servers[0].baseUrl, `http://127.0.0.1:${bareHost.peer.port}/mcp`);
    assert.equal(bareHost.json.servers[0].baseUrl, service.endpoint.href, '不是请求 Host 或转发头生成的 URL');
  } finally {
    await service.stop();
  }
});

test('目录与协议路由分离：GET /catalog、POST /mcp；拒绝目录参数/远端 URL、未知路由404', { timeout: 20000 }, async (t) => {
  const fixture = await createFixture(t);
  const service = await startFixtureHttp(t, fixture);
  try {
    const postCatalog = await fetch(new URL('/catalog', service.endpoint), { method: 'POST' });
    assert.equal(postCatalog.status, 405);
    assert.equal(postCatalog.headers.get('allow'), 'GET');
    assertNoLeak(await postCatalog.json(), fixture);
    const getMcp = await fetch(service.endpoint, { headers: { accept: 'text/event-stream' } });
    assert.equal(getMcp.status, 405);
    assert.equal(getMcp.headers.get('allow'), 'POST');
    assertNoLeak(await getMcp.json(), fixture);
    for (const route of [
      '/unknown', '/catalog/', '/catalog?baseUrl=https%3A%2F%2Funtrusted.invalid%2Fmcp',
      '/catalog?url=http%3A%2F%2F127.0.0.1%3A19540%2Fmcp', '/mcp?url=https%3A%2F%2Funtrusted.invalid',
    ]) {
      const rejected = await fetch(new URL(route, service.endpoint));
      assert.equal(rejected.status, 404, '目录参数不得变成远端来源或代理入口');
      assertNoLeak(await rejected.json(), fixture);
    }
  } finally {
    await service.stop();
  }
});
