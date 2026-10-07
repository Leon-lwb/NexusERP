import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { LATEST_PROTOCOL_VERSION } from '@modelcontextprotocol/sdk/types.js';
import {
  copyServiceToFixture, createFixture, DISPLAY_SEGMENTS, DOCUMENT_NAMES,
  REPOSITORY_ROOT, SERVER_PATH,
} from './fixtures.mjs';

function assertEntryExists() {
  assert.ok(existsSync(SERVER_PATH), 'stdio 协议服务尚未实现：缺少 server.mjs');
}

async function connectClient(entry, cwd) {
  const transport = new StdioClientTransport({
    command: process.execPath,
    args: [entry],
    cwd,
    stderr: 'pipe',
  });
  const client = new Client({ name: '只读资料协议验收', version: '1.0.0' });
  const stderr = [];
  transport.stderr.on('data', (chunk) => stderr.push(chunk.toString('utf8')));
  const close = async () => {
    await client.close();
    assert.equal(transport.pid, null, 'stdio 子进程必须已退出');
  };
  try {
    await client.connect(transport);
    assert.equal(client.getServerVersion().name, '制造运营资料服务');
    assert.equal(client.getServerVersion().version, '1.0.0');
    assert.deepEqual(Object.keys(client.getServerCapabilities()), ['tools']);
    return { client, stderr, close };
  } catch (error) {
    await close();
    throw error;
  }
}

function assertTools(tools) {
  assert.deepEqual(tools.map((tool) => tool.name), ['list_documents', 'read_document']);
  for (const tool of tools) {
    assert.equal(tool.annotations.readOnlyHint, true);
    assert.equal(tool.annotations.destructiveHint, false);
    assert.equal(tool.annotations.idempotentHint, true);
    assert.equal(tool.annotations.openWorldHint, false);
  }
  assert.equal(tools[0].inputSchema.additionalProperties, false);
  assert.equal(tools[1].inputSchema.additionalProperties, false);
  assert.deepEqual(tools[1].inputSchema.required, ['document_name']);
}

test('官方 Client 验收真实入口：initialize / tools/list / tools/call / 关闭', { timeout: 15000 }, async (t) => {
  assertEntryExists();
  const fixture = await createFixture(t);
  const { client, close } = await connectClient(SERVER_PATH, fixture.workingDirectory);
  try {
    assertTools((await client.listTools()).tools);
    const listing = await client.callTool({ name: 'list_documents', arguments: {} });
    assert.notEqual(listing.isError, true);
    const documents = JSON.parse(listing.content[0].text).documents;
    assert.deepEqual(documents.map((document) => document.name), DOCUMENT_NAMES);
    const first = documents[0];
    const result = await client.callTool({ name: 'read_document', arguments: { document_name: first.name } });
    if (first.available) {
      const text = await readFile(path.join(REPOSITORY_ROOT, ...DISPLAY_SEGMENTS, first.name), 'utf8');
      assert.notEqual(result.isError, true);
      assert.equal(result.content[0].text, text);
    } else {
      assert.equal(result.isError, true);
      assert.equal(result.structuredContent.error.code, first.error.code);
      assert.match(result.content[0].text, /缺失|软链接|越界|普通文件|无法读取/);
    }
    const invalid = await client.callTool({ name: 'read_document', arguments: { document_name: '../README.md' } });
    assert.equal(invalid.isError, true);
    assert.equal(invalid.structuredContent.error.code, 'DOCUMENT_NOT_ALLOWED');
  } finally {
    await close();
  }
});

test('官方 Client 验收成功读取：复制真实入口，仓库根与 cwd 不同', { timeout: 15000 }, async (t) => {
  assertEntryExists();
  const fixture = await createFixture(t);
  const entry = await copyServiceToFixture(fixture);
  const { client, close } = await connectClient(entry, fixture.workingDirectory);
  try {
    assertTools((await client.listTools()).tools);
    const listing = await client.callTool({ name: 'list_documents', arguments: {} });
    const documents = listing.structuredContent.documents;
    assert.deepEqual(documents.map((document) => document.name), DOCUMENT_NAMES);
    assert.ok(documents.every((document) => document.available));
    for (const name of DOCUMENT_NAMES) {
      const result = await client.callTool({ name: 'read_document', arguments: { document_name: name } });
      assert.notEqual(result.isError, true);
      assert.equal(result.content[0].text, fixture.contents[name]);
      assert.equal(result.structuredContent.name, name);
    }
    const unknownTool = await client.callTool({ name: 'write_document', arguments: {} });
    assert.equal(unknownTool.isError, true);
    const extraArgument = await client.callTool({ name: 'read_document', arguments: {
      document_name: DOCUMENT_NAMES[0], path: '/etc/passwd',
    } });
    assert.equal(extraArgument.isError, true, '额外路径参数不得静默接受');
  } finally {
    await close();
  }
});

test('原始 stdio 验收：stdout 每行都是 JSON-RPC，日志在 stderr，EOF 后退出', { timeout: 15000 }, async (t) => {
  assertEntryExists();
  const fixture = await createFixture(t);
  const entry = await copyServiceToFixture(fixture);
  const child = spawn(process.execPath, [entry], {
    cwd: fixture.workingDirectory,
    stdio: ['pipe', 'pipe', 'pipe'],
  });
  const exit = new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('exit', (code, signal) => resolve({ code, signal }));
  });
  const close = async () => {
    if (child.exitCode === null && child.signalCode === null) child.kill();
    await exit;
  };
  try {
    let buffer = '';
    let stderr = '';
    const messages = [];
    const pending = new Map();
    const parseErrors = [];
    child.stderr.on('data', (chunk) => { stderr += chunk.toString('utf8'); });
    child.stdout.setEncoding('utf8');
    child.stdout.on('data', (chunk) => {
      buffer += chunk;
      let newline;
      while ((newline = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, newline);
        buffer = buffer.slice(newline + 1);
        try {
          const message = JSON.parse(line);
          messages.push(message);
          const resolve = pending.get(message.id);
          if (resolve) {
            pending.delete(message.id);
            resolve(message);
          }
        } catch (error) {
          parseErrors.push({ line, error: error.message });
        }
      }
    });
    const request = (id, method, params) => new Promise((resolve) => {
      pending.set(id, resolve);
      child.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', id, method, params })}\n`);
    });
    const initialized = await request(1, 'initialize', {
      protocolVersion: LATEST_PROTOCOL_VERSION,
      capabilities: {},
      clientInfo: { name: '原始标准输出验收', version: '1.0.0' },
    });
    assert.equal(initialized.result.serverInfo.name, '制造运营资料服务');
    child.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' })}\n`);
    const listing = await request(2, 'tools/list', {});
    assertTools(listing.result.tools);
    const read = await request(3, 'tools/call', {
      name: 'read_document', arguments: { document_name: DOCUMENT_NAMES[0] },
    });
    assert.equal(read.result.content[0].text, fixture.contents[DOCUMENT_NAMES[0]]);
    child.stdin.end();
    assert.deepEqual(await exit, { code: 0, signal: null });
    assert.deepEqual(parseErrors, []);
    assert.equal(buffer, '');
    assert.equal(messages.length, 3);
    assert.ok(messages.every((message) => message.jsonrpc === '2.0'));
    assert.match(stderr, /制造运营资料服务/);
  } finally {
    await close();
  }
});


test('命令行默认 STDIO，HTTP 仅显式开启；端口合法且不能改监听地址/资料根', async () => {
  assertEntryExists();
  const { parseServerArguments } = await import('../server.mjs');
  assert.deepEqual(parseServerArguments([]), { mode: 'stdio' });
  assert.deepEqual(parseServerArguments(['--http']), { mode: 'http', port: 19540 });
  assert.deepEqual(parseServerArguments(['--http', '--port', '0']), { mode: 'http', port: 0 });
  assert.deepEqual(parseServerArguments(['--http', '--port', '19540']), { mode: 'http', port: 19540 });
  for (const args of [
    ['--port', '19540'], ['--http', '--port', '-1'], ['--http', '--port', '65536'],
    ['--http', '--port', 'abc'], ['--http', '--port', '1.5'], ['--http', '--port'],
    ['--http', '--host', '0.0.0.0'], ['--root', '/etc'], ['../another-directory'],
  ]) {
    assert.throws(() => parseServerArguments(args), /参数|端口|用法/);
  }
});
