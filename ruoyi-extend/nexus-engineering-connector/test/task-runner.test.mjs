import test from 'node:test';
import assert from 'node:assert/strict';
import { TaskRunner } from '../dist/task-runner.js';
/** 任务执行和服务端状态分离，工具结束不代表板子已生成。 */
test('串行回报修订号，CHECK不要求文件且只执行一次', async () => {
  let executions = 0;
  let revision = 1;
  const events = [];
  const api = {
    request: async (path, body) => {
      if (path.endsWith('/events')) {
        assert.equal(body.expectedRevision, revision);
        events.push(body);
        return { revision: ++revision, cancelRequested: false };
      }
      if (path.endsWith('/complete')) {
        assert.equal(body.expectedRevision, revision);
        return { state: 'SUCCEEDED' };
      }
      throw Error(path);
    }
  };
  const adapter = {
    execute: async (task, signal, stage) => {
      executions++;
      await stage('CHECKING');
      return { artifacts: [], errors: [], diagnostic: { connected: true } };
    }
  };
  const runner = new TaskRunner(api, adapter, '/fixture', { renewIntervalMs: 0 });
  const result = await runner.executeLease({
    taskId: '1',
    action: 'CHECK_CONNECTION',
    attempt: 1,
    revision: 1,
    leaseToken: 'lease',
    leaseExpiresAt: new Date(Date.now() + 90000).toISOString(),
    deadlineAt: new Date(Date.now() + 600000).toISOString(),
    parameters: {}
  });
  assert.equal(executions, 1);
  assert.equal(result.state, 'SUCCEEDED');
  assert.ok(events.length > 0);
  assert.deepEqual(
    events.map((x) => x.sequence),
    events.map((_, i) => i + 1)
  );
});
