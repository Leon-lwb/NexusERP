import { mkdtemp, access, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import test from 'node:test';
import assert from 'node:assert/strict';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { createMcpBridge } from '../dist/mcp-bridge.js';
import { LocalMcpClient } from '../dist/local-mcp-client.js';
import { setImmediate as turn } from 'node:timers/promises';

/** 使用SDK的真实双向传输验证阶段ACK，避免进度通知假充权限门。 */
async function connected(adapter, options = {}) {
  const [a, b] = InMemoryTransport.createLinkedPair();
  const server = createMcpBridge(adapter, process.cwd(), options);
  const worker = new LocalMcpClient('unused', { transport: b });
  await Promise.all([server.connect(a), worker.connect()]);
  return {
    worker,
    async close() {
      await worker.close();
      await server.close();
    }
  };
}
const task = { taskId: '1', attempt: 1, action: 'CREATE_PCB_DRAFT', parameters: {} };

/** 服务端只有收到客户端阶段确认后才能继续执行副作用。 */
test('nexus/stage请求等候真正ACK后才继续执行', async () => {
  let acknowledge, entered;
  const ready = new Promise((r) => {
    entered = r;
  });
  const gate = new Promise((r) => {
    acknowledge = r;
  });
  let writes = 0;
  const c = await connected({
    execute: async (t, signal, stage) => {
      await stage('GENERATING');
      signal.throwIfAborted();
      writes++;
      return { artifacts: [], errors: [] };
    }
  });
  try {
    const running = c.worker.execute(task, undefined, async () => {
      entered();
      await gate;
    });
    await ready;
    await turn();
    assert.equal(writes, 0);
    acknowledge();
    await running;
    assert.equal(writes, 1);
  } finally {
    acknowledge();
    await c.close();
  }
});

/** 阶段拒绝必须终止请求且不可留下未处理拒绝或继续改图。 */
test('阶段回报拒绝后不执行副作用，错误可被调用者捕获', async () => {
  let writes = 0;
  const c = await connected({
    execute: async (t, signal, stage) => {
      await stage('GENERATING');
      writes++;
      return { artifacts: [], errors: [] };
    }
  });
  try {
    await assert.rejects(
      c.worker.execute(task, undefined, async () => {
        throw Error('LEASE_REJECTED');
      }),
      /LEASE_REJECTED|LOCAL_EXECUTION_FAILED/
    );
    assert.equal(writes, 0);
  } finally {
    await c.close();
  }
});

/** ACK等待受独立超时与工具取消约束。 */
test('ACK超时后不得继续执行，取消会传到阶段处理器', async () => {
  let writes = 0,
    handlerAborted = false;
  const c = await connected(
    {
      execute: async (t, signal, stage) => {
        await stage('GENERATING');
        writes++;
        return { artifacts: [], errors: [] };
      }
    },
    { stageTimeoutMs: 20 }
  );
  try {
    await assert.rejects(
      c.worker.execute(
        task,
        undefined,
        async (stage, signal) =>
          new Promise((resolve, reject) => {
            signal.addEventListener(
              'abort',
              () => {
                handlerAborted = true;
                reject(signal.reason);
              },
              { once: true }
            );
          })
      ),
      /STAGE_ACK|LOCAL_EXECUTION_FAILED/
    );
    await turn();
    assert.equal(writes, 0);
    assert.equal(handlerAborted, true);
  } finally {
    await c.close();
  }
});

/** 使用真实Node子进程与STDIO JSON-RPC验证ACK后才执行，并在关闭时回收自有进程。 */
test('真实STDIO双向阶段ACK阻止提前副作用', async () => {
  const root = await mkdtemp(join(tmpdir(), 'nexus-stdio-ack-'));
  let release, entered;
  const gate = new Promise((r) => {
    release = r;
  });
  const ready = new Promise((r) => {
    entered = r;
  });
  const transport = new StdioClientTransport({
    command: process.execPath,
    args: [fileURLToPath(new URL('./fixtures/stage-bridge.mjs', import.meta.url)), root],
    stderr: 'pipe'
  });
  transport.stderr?.resume();
  const worker = new LocalMcpClient('unused', { transport });
  try {
    await worker.connect();
    const running = worker.execute(task, undefined, async () => {
      entered();
      await gate;
    });
    await ready;
    await assert.rejects(access(join(root, 'effect.txt')));
    release();
    await running;
    await access(join(root, 'effect.txt'));
  } finally {
    release();
    await worker.close();
    await rm(root, { recursive: true, force: true });
  }
});

/** 跨进程阶段拒绝不能导致后续执行或Node的未处理拒绝退出。 */
test('真实STDIO阶段拒绝保持副作用未执行', async () => {
  const root = await mkdtemp(join(tmpdir(), 'nexus-stdio-reject-'));
  const transport = new StdioClientTransport({
    command: process.execPath,
    args: [fileURLToPath(new URL('./fixtures/stage-bridge.mjs', import.meta.url)), root],
    stderr: 'pipe'
  });
  transport.stderr?.resume();
  const worker = new LocalMcpClient('unused', { transport });
  try {
    await worker.connect();
    await assert.rejects(
      worker.execute(task, undefined, async () => {
        throw Error('STAGE_REJECTED');
      }),
      /STAGE_REJECTED|STAGE_ACK/
    );
    await assert.rejects(access(join(root, 'effect.txt')));
  } finally {
    await worker.close();
    await rm(root, { recursive: true, force: true });
  }
});
