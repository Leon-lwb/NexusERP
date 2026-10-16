import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm, mkdir, writeFile, symlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { EasyedaAdapter } from '../dist/easyeda/adapter.js';
/** 模型请求必须通过动作和模板校验，错误参数不能触达编辑器。 */
test('拒绝未知动作和伪造模板摘要，保留真实离线状态', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'nexus-adapter-'));
  try {
    let calls = 0;
    const cli = {
      doctor: async () => {
        calls++;
        return { connected: false };
      },
      open: async () => {
        calls++;
        return 's';
      }
    };
    const adapter = new EasyedaAdapter({ workspaceRoot: dir, executable: '/fixture' }, cli);
    await assert.rejects(adapter.execute({ taskId: '1', attempt: 1, action: 'SHELL', parameters: {} }), /动作/);
    assert.equal(calls, 0);
    await assert.rejects(
      adapter.execute({
        taskId: '1',
        attempt: 1,
        action: 'CREATE_PCB_DRAFT',
        templateId: 'temperature-humidity-demo',
        templateVersion: '1.0.0',
        templateSha256: 'bad',
        parameters: { boardWidthMm: '50', boardHeightMm: '35', layers: 2 }
      }),
      /摘要/
    );
    assert.equal(calls, 0);
    const result = await adapter.execute({ taskId: '2', attempt: 1, action: 'CHECK_CONNECTION', parameters: {} });
    assert.equal(result.diagnostic.connected, false);
    assert.equal(calls, 1);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

/** 所有导出路径必须在写入前拒绝junction逃逸。 */
test('已有exports链接不能把报告写到工作目录外', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'nexus-junction-'));
  const outside = await mkdtemp(join(tmpdir(), 'nexus-outside-'));
  try {
    const project = join(dir, 'project');
    await mkdir(project);
    await writeFile(join(project, 'P.eprj3'), '{}');
    await mkdir(join(dir, 'references'));
    await writeFile(join(dir, 'references/1.json'), JSON.stringify({ projectPath: join(project, 'P.eprj3') }));
    await symlink(outside, join(project, 'exports'), process.platform === 'win32' ? 'junction' : 'dir');
    let invoked = 0;
    const adapter = new EasyedaAdapter(
      { workspaceRoot: dir, executable: '/fixture' },
      {
        open: async () => 's',
        invoke: async () => {
          invoked++;
          return { status: 'PASS', errorCount: 0 };
        }
      }
    );
    await assert.rejects(adapter.execute({ taskId: '2', attempt: 1, action: 'RUN_DRC', parentTaskId: '1', parameters: {} }), /链接|逃逸/);
    assert.equal(invoked, 0);
  } finally {
    await rm(dir, { recursive: true, force: true });
    await rm(outside, { recursive: true, force: true });
  }
});
/** 取消确认后不能再进入编辑器动作。 */
test('阶段回调触发取消时不再调用导出CLI', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'nexus-abort-'));
  try {
    const project = join(dir, 'project');
    await mkdir(project);
    await writeFile(join(project, 'P.eprj3'), '{}');
    await mkdir(join(dir, 'references'));
    await writeFile(join(dir, 'references/1.json'), JSON.stringify({ projectPath: join(project, 'P.eprj3') }));
    let invoked = 0;
    const adapter = new EasyedaAdapter(
      { workspaceRoot: dir, executable: '/fixture' },
      {
        open: async () => 's',
        invoke: async () => {
          invoked++;
          return { status: 'PASS', errorCount: 0 };
        }
      }
    );
    const cancel = new AbortController();
    await assert.rejects(
      adapter.execute({ taskId: '2', attempt: 1, action: 'RUN_DRC', parentTaskId: '1', parameters: {} }, cancel.signal, async () => cancel.abort()),
      /abort/i
    );
    assert.equal(invoked, 0);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
/** 派生任务继续继承本机工程映射。 */
test('打开派生任务后仍可再次打开同一工程', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'nexus-derived-'));
  try {
    const project = join(dir, 'project');
    await mkdir(project);
    await writeFile(join(project, 'P.eprj3'), '{}');
    await mkdir(join(dir, 'references'));
    await writeFile(join(dir, 'references/1.json'), JSON.stringify({ projectPath: join(project, 'P.eprj3') }));
    const adapter = new EasyedaAdapter({ workspaceRoot: dir, executable: '/fixture' }, { open: async () => 's' });
    await adapter.execute({ taskId: '2', attempt: 1, action: 'OPEN_PROJECT', parentTaskId: '1', parameters: {} });
    const result = await adapter.execute({ taskId: '3', attempt: 1, action: 'OPEN_PROJECT', parentTaskId: '2', parameters: {} });
    assert.equal(result.diagnostic.opened, true);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
