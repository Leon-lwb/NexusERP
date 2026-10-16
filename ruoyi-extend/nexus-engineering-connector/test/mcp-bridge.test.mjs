import test from 'node:test';
import assert from 'node:assert/strict';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { createMcpBridge } from '../dist/mcp-bridge.js';
/** 使用真实SDK协议发现与调用白名单工具，文件内容不进入AI响应。 */
test('本地MCP只暴露六个受控动作并删除绝对路径', async () => {
  let received;
  const server = createMcpBridge(
    {
      execute: async (task) => {
        received = task;
        return {
          artifacts: [{ localPath: '/work/tasks/1/1/project/exports/preview/top.png', relativePath: 'preview/top.png', type: 'PCB_PREVIEW_2D' }],
          projectPath: 'C:/private/project',
          errors: []
        };
      }
    },
    '/work'
  );
  const [a, b] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'test', version: '1' });
  await Promise.all([server.connect(a), client.connect(b)]);
  try {
    assert.equal((await client.listTools()).tools.length, 6);
    const r = await client.callTool({ name: 'easyeda_check_connection', arguments: { taskId: '1', attempt: 1, parameters: {} } });
    assert.equal(received.action, 'CHECK_CONNECTION');
    assert.equal(JSON.stringify(r).includes('C:/private'), false);
    const bad = await client.callTool({ name: 'execute_shell', arguments: {} });
    assert.equal(bad.isError, true);
  } finally {
    await client.close();
    await server.close();
  }
});
