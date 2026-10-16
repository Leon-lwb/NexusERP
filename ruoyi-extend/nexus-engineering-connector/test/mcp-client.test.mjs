import test from 'node:test';
import assert from 'node:assert/strict';
import { EasyedaClient } from '../dist/easyeda/mcp-client.js';
/** 验证真实CLI返回包裹与固定参数，模型不能提供任意脚本。 */
test('CLI兼容模式核对ok字段，失败不伪造成成功', async () => {
  const calls = [];
  const c = new EasyedaClient('/test/lceda.exe', async (exe, args) => {
    calls.push(args);
    return { code: 0, stdout: JSON.stringify({ ok: true, value: { sessionId: 's1' } }) };
  });
  const session = await c.open('/work/task.eprj3');
  assert.equal(session, 's1');
  assert.deepEqual(calls[0], ['open', '--path', '/work/task.eprj3']);
  const bad = new EasyedaClient('/test/lceda.exe', async () => ({ code: 1, stdout: '{"ok":false,"error":{"code":"EXECUTION_ERROR"}}' }));
  await assert.rejects(bad.open('/work/task.eprj3'), /EXECUTION_ERROR/);
  await assert.rejects(c.invoke('s1', 'untrusted-script', {}), /不允许/);
});
