import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { setImmediate as turn } from 'node:timers/promises';
import { TaskRunner } from '../dist/task-runner.js';
import { NexusError } from '../dist/nexus-client.js';

import { backend } from './helpers/task-backend.mjs';

/** 后端取消会推进修订号，连接器须读取详情后确认取消。 */
test('409后读取当前revision并确认浏览器取消，不再完成任务', async () => {
  const b = backend('CREATE_PCB_DRAFT');
  let writes = 0;
  const executor = {
    execute: async (task, signal, stage) => {
      b.cancel();
      await stage('GENERATING');
      signal.throwIfAborted();
      writes++;
      return { artifacts: [] };
    }
  };
  const result = await new TaskRunner(b.api, executor, 'unused', { renewIntervalMs: 0 }).executeLease(b.lease);
  assert.equal(result.state, 'CANCELED');
  assert.equal(writes, 0);
  assert.ok(b.state.calls.some((c) => c.path === '/connector/tasks/1'));
  assert.ok(b.state.calls.some((c) => c.body?.cancelAcknowledged));
  assert.equal(
    b.state.calls.some((c) => c.path.endsWith('/complete')),
    false
  );
});

/** 使用可控时钟验证上传传输期间仍能续约。 */
test('慢上传不占据修订号队列，传输期间仍续约', async (t) => {
  t.mock.timers.enable({ apis: ['Date', 'setInterval'], now: Date.now() });
  const dir = await mkdtemp(join(tmpdir(), 'nexus-renew-test-'));
  let releaseUpload;
  const uploadGate = new Promise((resolve) => {
    releaseUpload = resolve;
  });
  let entered;
  const started = new Promise((resolve) => {
    entered = resolve;
  });
  const b = backend('CREATE_PCB_DRAFT');
  const original = b.api.request;
  b.api.request = async (path, ...args) => {
    if (path.endsWith('/artifacts')) {
      entered();
      await uploadGate;
    }
    return original(path, ...args);
  };
  try {
    const localPath = join(dir, 'bom.json');
    await writeFile(localPath, '{}');
    const artifact = {
      type: 'PCB_BOM',
      relativePath: 'reports/bom.json',
      localPath,
      size: 2,
      sha256: createHash('sha256').update('{}').digest('hex'),
      contentType: 'application/json'
    };
    const running = new TaskRunner(b.api, { execute: async () => ({ artifacts: [artifact] }) }, dir).executeLease(b.lease);
    await started;
    t.mock.timers.tick(30_000);
    await turn();
    const during = b.state.renewals;
    releaseUpload();
    await running;
    assert.ok(during > 0, '上传等待期间必须已完成续约');
  } finally {
    releaseUpload();
    await rm(dir, { recursive: true, force: true });
  }
});

/** 离线检查必须以真实不可用错误回报，而不能提交无错误的完成请求。 */
test('CHECK_CONNECTION离线先报告EDITOR_UNAVAILABLE再完成', async () => {
  const b = backend();
  const runner = new TaskRunner(b.api, { execute: async () => ({ artifacts: [], diagnostic: { connected: false }, errors: [] }) }, 'unused', {
    renewIntervalMs: 0
  });
  await runner.executeLease(b.lease).catch(() => {});
  assert.equal(b.state.errorCode, 'EDITOR_UNAVAILABLE');
  assert.equal(b.state.state, 'FAILED');
});

/** DPAPI写入和续约可以交错，但恢复文件的原子替换绝不可并发。 */
test('续约与执行边界交错时，恢复状态写入仍串行', async (t) => {
  t.mock.timers.enable({ apis: ['Date', 'setInterval'], now: Date.now() });
  const b = backend();
  let release, entered;
  const gate = new Promise((r) => {
    release = r;
  });
  const started = new Promise((r) => {
    entered = r;
  });
  let active = 0,
    overlap = false,
    blocked = false;
  const store = {
    async load() {
      return null;
    },
    async clear() {},
    async save(value) {
      active++;
      overlap ||= active > 1;
      try {
        if (value.phase === 'EXECUTING' && !blocked) {
          blocked = true;
          entered();
          await gate;
        }
      } finally {
        active--;
      }
    }
  };
  const running = new TaskRunner(b.api, { execute: async () => ({ artifacts: [], diagnostic: { connected: true } }) }, 'unused', {
    resumeStore: store
  }).executeLease(b.lease);
  await started;
  t.mock.timers.tick(30_000);
  await turn();
  release();
  await running;
  assert.equal(overlap, false);
  assert.ok(b.state.renewals > 0);
});

/** 非取消修订竞争可重新读取并续约，不误判为失去租约。 */
test('续约409但租约仍有效时刷新revision后续约成功', async (t) => {
  t.mock.timers.enable({ apis: ['Date', 'setInterval'], now: Date.now() });
  const b = backend();
  let started, release;
  const ready = new Promise((r) => {
    started = r;
  });
  const gate = new Promise((r) => {
    release = r;
  });
  let executionSignal;
  const running = new TaskRunner(
    b.api,
    {
      execute: async (task, signal) => {
        executionSignal = signal;
        started();
        await gate;
        signal.throwIfAborted();
        return { artifacts: [], diagnostic: { connected: true } };
      }
    },
    'unused'
  ).executeLease(b.lease);
  await ready;
  b.state.revision++;
  t.mock.timers.tick(30_000);
  await turn();
  const renewed = b.state.renewals;
  release();
  await running;
  assert.equal(executionSignal.aborted, false);
  assert.equal(renewed, 1);
  assert.ok(b.state.calls.some((c) => c.path === '/connector/tasks/1'));
});
