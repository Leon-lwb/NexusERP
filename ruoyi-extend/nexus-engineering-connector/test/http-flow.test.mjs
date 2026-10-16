import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { mkdtemp, mkdir, writeFile, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';
import { zipSync, strToU8 } from 'fflate';
import { NexusClient } from '../dist/nexus-client.js';
import { TaskRunner } from '../dist/task-runner.js';
import { collectArtifacts, validateModelReferences } from '../dist/artifacts/manifest.js';

/** 使用真实HTTP和multipart核对连接器协议；测试端点只保存在内存，不连接已有Nexus或数据库。 */
test('HTTP闭环保持雪花ID、七类产物摘要和单次执行，慢上传期间继续续租', async () => {
  const root = await mkdtemp(join(tmpdir(), 'nexus-http-flow-'));
  const taskId = '9223372036854775806';
  const taskPath = `/ai/engineering/connector/tasks/${taskId}`;
  const errors = [];
  let revision = 1,
    renewals = 0,
    slowUploadRenewals = 0,
    executed = 0,
    completed = 0;
  const events = [],
    tickets = new Map(),
    confirmed = new Map();
  const server = createServer(async (request, response) => {
    const reply = (data, status = 200) => {
      response.writeHead(status, { 'Content-Type': 'application/json' });
      response.end(JSON.stringify({ code: status, data }));
    };
    try {
      assert.equal(request.headers['x-nexus-device-token'], 'synthetic-memory-only-token');
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const buffer = Buffer.concat(chunks);
      const body = buffer.length && request.headers['content-type']?.includes('application/json') ? JSON.parse(buffer.toString('utf8')) : undefined;
      const path = request.url;
      if (path === `${taskPath}/manifest`) return reply({ taskId, artifacts: [...confirmed.values()] });
      if (path === taskPath) return reply({ taskId, state: 'RUNNING', attempt: 1, revision: String(revision), events, cancelRequested: false });
      if (path === `${taskPath}/artifacts`) {
        const token = request.headers['x-nexus-upload-ticket'];
        const ticket = tickets.get(token);
        assert.ok(ticket, '上传必须携带本任务已签发的票据');
        const form = await new Response(buffer, { headers: { 'Content-Type': request.headers['content-type'] } }).formData();
        assert.deepEqual([...form.keys()], ['file']);
        const file = form.get('file');
        const content = Buffer.from(await file.arrayBuffer());
        assert.equal(file.name, ticket.originalFilename);
        assert.equal(content.length, ticket.size);
        assert.equal(createHash('sha256').update(content).digest('hex'), ticket.sha256);
        if (confirmed.size === 0) {
          const before = renewals;
          await delay(120);
          slowUploadRenewals = renewals - before;
        }
        const artifact = { ...ticket, artifactId: String(9000 + confirmed.size) };
        confirmed.set(ticket.relativePath, artifact);
        return reply(artifact);
      }
      assert.equal(body.attempt, 1);
      assert.equal(body.leaseToken, 'synthetic-attempt-lease');
      assert.equal(body.expectedRevision, revision, '控制请求使用最新CAS修订号');
      if (path === `${taskPath}/renew`) {
        renewals++;
        return reply({ revision: String(++revision), leaseExpiresAt: new Date(Date.now() + 90_000).toISOString(), cancelRequested: false });
      }
      if (path === `${taskPath}/events`) {
        assert.equal(body.sequence, events.length + 1);
        assert.equal(body.errorCode, undefined);
        events.push(body);
        return reply({ revision: String(++revision), cancelRequested: false });
      }
      if (path === `${taskPath}/upload-ticket`) {
        assert.ok(body.relativePath && !body.relativePath.includes('..'));
        const token = `memory-ticket-${tickets.size}`;
        tickets.set(token, body);
        return reply({ uploadTicket: token, alreadyConfirmed: false });
      }
      if (path === `${taskPath}/complete`) {
        completed++;
        assert.equal(confirmed.size, 7);
        return reply({ taskId, state: 'PARTIAL_SUCCESS', drcStatus: 'VIOLATIONS', canManufacture: false });
      }
      assert.fail(`未预期的协议路径: ${path}`);
    } catch (error) {
      errors.push(error);
      reply({ errorCode: 'FIXTURE_ASSERTION_FAILED' }, 500);
    }
  });
  try {
    await mkdir(join(root, 'output/model'), { recursive: true });
    await mkdir(join(root, 'output/preview'), { recursive: true });
    await mkdir(join(root, 'output/reports'), { recursive: true });
    const files = {
      'source.zip': zipSync({ 'draft.eprj': strToU8('synthetic fixture, not a manufacturing design') }),
      'preview/top.png': Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2lhQAAAAASUVORK5CYII=', 'base64'),
      'model/board.obj': 'mtllib board.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl board\nf 1 2 3\n',
      'model/board.mtl': 'newmtl board\nKd 0.1 0.5 0.2\n',
      'reports/bom.json': JSON.stringify({ items: [{ designator: 'R1', quantity: 1 }] }),
      'reports/drc.json': JSON.stringify({ status: 'VIOLATIONS', errorCount: 1, warningCount: 0, items: [] }),
      'reports/netlist.net': 'synthetic fixture netlist\n'
    };
    for (const [name, content] of Object.entries(files)) await writeFile(join(root, 'output', name), content);
    const artifacts = await collectArtifacts(join(root, 'output'));
    await validateModelReferences(artifacts);
    assert.equal(artifacts.length, 7);
    const expected = new Map(artifacts.map((artifact) => [artifact.relativePath, artifact]));
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    const api = new NexusClient(`http://127.0.0.1:${server.address().port}`);
    api.setAccessToken('synthetic-memory-only-token');
    const executor = {
      async execute(task, signal, stage) {
        executed++;
        assert.equal(task.taskId, taskId);
        await stage('PREPARING');
        signal.throwIfAborted();
        await stage('GENERATING');
        signal.throwIfAborted();
        await stage('CHECKING_DRC');
        await stage('EXPORTING');
        return { artifacts, errors: [], drc: { status: 'VIOLATIONS' } };
      }
    };
    const runner = new TaskRunner(api, executor, root, { renewIntervalMs: 15 });
    const result = await runner.executeLease({
      taskId,
      action: 'CREATE_PCB_DRAFT',
      attempt: 1,
      revision: 1,
      leaseToken: 'synthetic-attempt-lease',
      leaseExpiresAt: new Date(Date.now() + 90_000).toISOString(),
      deadlineAt: new Date(Date.now() + 600_000).toISOString(),
      parameters: {}
    });
    assert.deepEqual(errors, []);
    assert.equal(result.taskId, taskId);
    assert.equal(result.state, 'PARTIAL_SUCCESS');
    assert.equal(result.canManufacture, false);
    assert.equal(executed, 1);
    assert.equal(completed, 1);
    assert.ok(slowUploadRenewals >= 1, '真实multipart阻塞期间仍有独立续租请求');
    assert.equal(events.at(-1).stage, 'FINALIZING');
    for (const [name, artifact] of confirmed) {
      assert.equal(artifact.sha256, expected.get(name).sha256);
      assert.equal(artifact.artifactType, expected.get(name).type);
    }
    assert.deepEqual(await readFile(join(root, 'output/source.zip')), Buffer.from(files['source.zip']));
  } finally {
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
    await rm(root, { recursive: true, force: true });
  }
});
