import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { ProtectedResumeStore } from '../dist/resume-state.js';
import { TaskRunner } from '../dist/task-runner.js';
import { backend } from './helpers/task-backend.mjs';

/** 内存仓库仅供任务恢复测试，真实CLI必须使用CurrentUser DPAPI仓库。 */
function memoryStore(value) {
  return {
    value: structuredClone(value),
    async load() {
      return structuredClone(this.value);
    },
    async save(next) {
      this.value = structuredClone(next);
    },
    async clear() {
      this.value = null;
    }
  };
}
/** 构造已经完成本地执行、尚未上传的受保护恢复记录。 */
function record(b, extra = {}) {
  return {
    protocolVersion: 'nexus-engineering/1',
    lease: structuredClone(b.lease),
    sequence: 0,
    stage: 'CHECKING',
    phase: 'EXECUTED',
    result: { artifacts: [], diagnostic: { connected: true }, errors: [] },
    ...extra
  };
}

/** 使用真实操作系统加密完整租约身份，不能在恢复文件中保存明文Token。 */
test('恢复记录经现有CredentialStore DPAPI保护并绑定设备和服务地址', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'nexus-protected-resume-'));
  try {
    const b = backend();
    const state = record(b);
    const store = new ProtectedResumeStore(dir, 'device', 'https://nexus.test');
    if (process.platform !== 'win32') {
      await assert.rejects(store.save(state), /Windows/);
      return;
    }
    await store.save(state);
    const bytes = await readFile(join(dir, '.resume', 'identity.dpapi'), 'utf8');
    assert.equal(bytes.includes('synthetic-lease'), false);
    assert.equal(bytes.includes('sequence'), false);
    assert.deepEqual(await store.load(), state);
    await assert.rejects(new ProtectedResumeStore(dir, 'other-device', 'https://nexus.test').load(), /RESUME_IDENTITY_MISMATCH/);
    await assert.rejects(new ProtectedResumeStore(dir, 'device', 'https://other.test').load(), /RESUME_IDENTITY_MISMATCH/);
    await store.clear();
    assert.equal(await store.load(), null);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

/** 认证查询、有效租约续约后只恢复上传，不重复调用执行器。 */
test('启动时先恢复busy任务，完成后才允许claim新任务', async () => {
  const b = backend();
  const store = memoryStore(record(b));
  let executions = 0;
  const abort = new AbortController();
  const original = b.api.request;
  b.api.request = async (path, ...args) => {
    const result = await original(path, ...args);
    if (path === '/connector/claim') abort.abort();
    return result;
  };
  await new TaskRunner(
    b.api,
    {
      execute: async () => {
        executions++;
        throw Error('must not replay');
      }
    },
    'unused',
    { renewIntervalMs: 0, pollIntervalMs: 0, resumeStore: store }
  ).run(abort.signal, async () => {});
  assert.equal(executions, 0);
  assert.equal(store.value, null);
  assert.equal(b.state.state, 'SUCCEEDED');
  assert.ok(b.state.renewals > 0);
  const paths = b.state.calls.map((c) => c.path);
  assert.ok(paths.indexOf('/connector/tasks/1') < paths.indexOf('/connector/claim'));
  assert.ok(paths.findIndex((p) => p.endsWith('/complete')) < paths.indexOf('/connector/claim'));
});

/** 崩溃时已进入执行但没有持久完成回执，宁可失败也不重做设计。 */
test('EXECUTING恢复为EXECUTION_UNCERTAIN且不会重放设计动作', async () => {
  const b = backend('CREATE_PCB_DRAFT');
  const store = memoryStore(record(b, { phase: 'EXECUTING', result: undefined }));
  let writes = 0;
  await new TaskRunner(
    b.api,
    {
      execute: async () => {
        writes++;
        return { artifacts: [] };
      }
    },
    'unused',
    { renewIntervalMs: 0, resumeStore: store }
  ).resume();
  assert.equal(writes, 0);
  assert.equal(b.state.errorCode, 'EXECUTION_UNCERTAIN');
  assert.equal(b.state.state, 'FAILED');
  assert.equal(store.value, null);
});

/** 待确认事件保存原始负载，重启时先重取同一回执再续约。 */
test('阶段响应丢失后恢复原事件和连续sequence，既不覆盖也不重复记账', async () => {
  const b = backend();
  const pending = {
    attempt: 1,
    leaseToken: b.lease.leaseToken,
    expectedRevision: 1,
    sequence: 1,
    stage: 'UPLOADING',
    message: '本地工程动作执行进度',
    cancelAcknowledged: false
  };
  const store = memoryStore(record(b, { pendingEvent: pending }));
  await b.api.request('/connector/tasks/1/events', pending);
  await new TaskRunner(
    b.api,
    {
      execute: async () => {
        throw Error('must not replay');
      }
    },
    'unused',
    { renewIntervalMs: 0, resumeStore: store }
  ).resume();
  assert.deepEqual(
    b.state.events.map((e) => e.sequence),
    b.state.events.map((_, i) => i + 1)
  );
  const replay = b.state.calls.filter((c) => c.path.endsWith('/events') && c.body.sequence === 1);
  assert.equal(replay.length, 2);
  assert.deepEqual(replay[0].body, replay[1].body);
  assert.equal(store.value, null);
});

/** 重启后发现浏览器取消，也必须以当前修订号完成取消确认。 */
test('恢复任务先核对取消，不执行或上传并回报CANCELED', async () => {
  const b = backend('CREATE_PCB_DRAFT');
  const store = memoryStore(record(b, { phase: 'EXECUTING', result: undefined }));
  b.cancel();
  let writes = 0;
  const result = await new TaskRunner(
    b.api,
    {
      execute: async () => {
        writes++;
        return { artifacts: [] };
      }
    },
    'unused',
    { renewIntervalMs: 0, resumeStore: store }
  ).resume();
  assert.equal(result.state, 'CANCELED');
  assert.equal(writes, 0);
  assert.equal(b.state.state, 'CANCELED');
  assert.equal(store.value, null);
});

/** 过期租约绝不发写回调，原有工程文件不受影响。 */
test('恢复记录已过期时清除领取身份并停止旧尝试', async () => {
  const b = backend();
  b.lease.leaseExpiresAt = new Date(Date.now() - 1000).toISOString();
  const store = memoryStore(record(b));
  let writes = 0;
  await new TaskRunner(
    b.api,
    {
      execute: async () => {
        writes++;
      }
    },
    'unused',
    { renewIntervalMs: 0, resumeStore: store }
  ).resume();
  assert.equal(writes, 0);
  assert.equal(store.value, null);
  assert.equal(
    b.state.calls.some((c) => c.path.endsWith('/events') || c.path.endsWith('/renew') || c.path.endsWith('/complete')),
    false
  );
});

/** 当前身份被撤销后不尝试领取、续约或执行。 */
test('恢复认证401会停止运行，不丢弃记录或领取新任务', async () => {
  const b = backend();
  const store = memoryStore(record(b));
  let calls = 0;
  const api = {
    request: async () => {
      calls++;
      throw Object.assign(Error('revoked'), { status: 401 });
    }
  };
  await assert.rejects(
    new TaskRunner(
      api,
      {
        execute: async () => {
          throw Error('must not run');
        }
      },
      'unused',
      { resumeStore: store }
    ).run(new AbortController().signal, async () => {}),
    (e) => e.status === 401
  );
  assert.equal(calls, 1);
  assert.ok(store.value);
});

/** 多产物恢复记录须完整保护，不能因重复路径元数据超过DPAPI管道限制而丢失恢复能力。 */
test('完整64产物恢复记录可通过加密仓库往返', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'nexus-large-resume-'));
  try {
    const b = backend();
    const artifacts = Array.from({ length: 64 }, (_, i) => ({
      type: 'PCB_MODEL_TEXTURE',
      relativePath: `model/${i}-${'a'.repeat(235)}.png`,
      workspaceRelativePath: `tasks/1/1/project/exports/model/${i}-${'a'.repeat(235)}.png`,
      sha256: 'a'.repeat(64),
      size: 1024,
      contentType: 'image/png',
      groupId: 'model-1'
    }));
    const state = record(b, { result: { artifacts } });
    const store = new ProtectedResumeStore(dir, 'device', 'https://nexus.test');
    if (process.platform !== 'win32') return;
    await store.save(state);
    assert.deepEqual(await store.load(), state);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

/** 服务端已切换尝试时丢弃旧身份，不提交任何旧尝试回调。 */
test('恢复发现attempt变化时不再发送旧事件或上传', async () => {
  const b = backend();
  const store = memoryStore(record(b));
  const original = b.api.request;
  b.api.request = async (path, ...args) =>
    path === '/connector/tasks/1' ? { ...(await original(path, ...args)), attempt: 2 } : original(path, ...args);
  const result = await new TaskRunner(
    b.api,
    {
      execute: async () => {
        throw Error('must not replay');
      }
    },
    'unused',
    { renewIntervalMs: 0, resumeStore: store }
  ).resume();
  assert.equal(result.errorCode, 'STALE_ATTEMPT');
  assert.equal(store.value, null);
  assert.equal(
    b.state.calls.some((c) => c.path.endsWith('/events') || c.path.endsWith('/renew') || c.path.endsWith('/complete')),
    false
  );
});

/** 完成回执丢失时读取已终结状态，不重做执行或重复完成。 */
test('完成响应丢失后以详情终态清除恢复记录', async () => {
  const b = backend();
  const store = memoryStore(record(b, { phase: 'COMPLETING' }));
  await b.api.request('/connector/tasks/1/complete', { attempt: 1, leaseToken: b.lease.leaseToken, expectedRevision: 1 });
  await new TaskRunner(
    b.api,
    {
      execute: async () => {
        throw Error('must not replay');
      }
    },
    'unused',
    { renewIntervalMs: 0, resumeStore: store }
  ).resume();
  assert.equal(store.value, null);
  assert.equal(b.state.calls.filter((c) => c.path.endsWith('/complete')).length, 1);
});
