import assert from 'node:assert/strict';
import test from 'node:test';
import { flush, http500, loadProductionModule, mountPage, offlineApi, text } from './component-harness.mjs';

const tool = {
  id: '2103871286244564993',
  toolName: 'assembly_guide',
  displayName: '装配指引',
  description: '离线测试工具',
  toolType: 'REMOTE',
  configJson: '{}',
  status: '0'
};
const listUrl = '/ai/mcp/tool/list';

async function setup(t) {
  const page = await mountPage('src/views/ai/mcp/index.vue', ({ routes }) => routes.set(listUrl, () => ({ code: 200, data: [tool] })));
  t.after(page.unmount);
  return page;
}

test('MCP 列表非空及正常空数组由真实页面和 CRUD 展示', async (t) => {
  const page = await setup(t);
  assert.equal(page.nodes('el-table')[0].props.data[0].id, tool.id);
  page.routes.set(listUrl, () => ({ code: 200, data: [] }));
  await page.nodes('right-toolbar')[0].props.onQueryTable();
  await flush();
  assert.deepEqual(page.nodes('el-table')[0].props.data, []);
});

for (const [label, response] of [
  ['HTTP 500', () => Promise.reject(http500())],
  ['业务 code=500', () => ({ code: 500, msg: '工具列表业务失败', data: [tool] })]
]) {
  test(`MCP 列表 ${label} 不保留上一轮工具且有错误提示`, async (t) => {
    t.mock.method(console, 'error', () => {});
    const page = await setup(t);
    page.routes.set(listUrl, response);
    await page.nodes('right-toolbar')[0].props.onQueryTable();
    await flush();
    assert.deepEqual(page.nodes('el-table')[0].props.data, []);
    assert.ok(page.messages.some((message) => message.type === 'error'));
  });
}

test('MCP 新增按钮的点击事件不误当初始化回调，新建确实调用新增 API', async (t) => {
  const page = await setup(t);
  page.button('新增工具').props.onClick({ type: 'click' });
  await flush();
  const form = page.nodes('el-form')[0].props.model;
  form.toolName = 'test_tool';
  form.displayName = '测试工具';
  form.toolType = 'REMOTE';
  form.configJson = '{}';
  await page.button('确 定').props.onClick();
  await flush();
  assert.equal(page.calls.findLast((call) => call.method === 'post').url, '/ai/mcp/tool');
  assert.equal(page.nodes('el-dialog').length, 0);
});

test('MCP 编辑回填、修改、切换状态和连接测试透传大 ID，不进行 Number 截断', async (t) => {
  const page = await setup(t);
  page.button('编辑').props.onClick();
  await flush();
  const form = page.nodes('el-form')[0].props.model;
  assert.equal(form.id, tool.id);
  form.displayName = '修改后的指引';
  await page.button('确 定').props.onClick();
  await flush();
  assert.equal(page.calls.findLast((call) => call.method === 'put').data.id, tool.id);
  assert.equal(tool.displayName, '装配指引', '编辑表单不能直接污染列表原行');
  await page.button('停用').props.onClick();
  await flush();
  assert.ok(page.calls.some((call) => call.url === `/ai/mcp/tool/${tool.id}/status`));
  page.routes.set(`/ai/mcp/tool/${tool.id}/test`, () => ({ code: 200, data: { success: true, message: '离线连接成功' } }));
  await page.button('测试').props.onClick();
  await flush();
  assert.ok(page.calls.some((call) => call.url === `/ai/mcp/tool/${tool.id}/test`));
  assert.ok(page.messages.some((message) => message.message === '离线连接成功'));
});

for (const operation of ['新增', '编辑']) {
  for (const [label, response] of [
    ['HTTP 500', () => Promise.reject(http500())],
    ['业务 code=500', () => ({ code: 500, msg: '工具保存业务失败' })]
  ]) {
    test(`MCP ${operation} ${label} 不报成功、不关闭编辑弹窗`, async (t) => {
      const page = await setup(t);
      page.button(operation === '编辑' ? '编辑' : '新增工具').props.onClick();
      await flush();
      Object.assign(page.nodes('el-form')[0].props.model, { toolName: 'test_tool', displayName: '测试工具', toolType: 'REMOTE', configJson: '{}' });
      page.routes.set('/ai/mcp/tool', response);
      await assert.rejects(page.button('确 定').props.onClick());
      await flush();
      assert.equal(page.nodes('el-dialog').length, 1);
      assert.ok(page.messages.some((message) => message.type === 'error'));
      assert.equal(page.messages.filter((message) => message.type === 'success').length, 0);
    });
  }
}

test('MCP 非法配置 JSON 在调用 API 之前阻止保存', async (t) => {
  const page = await setup(t);
  page.button('编辑').props.onClick();
  await flush();
  page.nodes('el-form')[0].props.model.configJson = '{';
  const before = page.calls.length;
  page.button('确 定').props.onClick();
  await flush();
  assert.equal(page.calls.length, before);
  assert.equal(page.nodes('el-dialog').length, 1);
  assert.ok(page.messages.some((message) => /配置 JSON/.test(message.message)));
  assert.match(text(page.root), /确 定/);
});

for (const [label, data] of [
  ['空数组', []],
  ['大 ID 非空工具', [{ id: tool.id, toolName: tool.toolName }]]
]) {
  test(`工具选择可用列表 ${label} 保留原值，不自行取整`, async () => {
    const api = await offlineApi();
    const { getAvailableMcpTools } = await loadProductionModule('src/api/ai/mcpTool.ts', { '@/utils/request': api.requestModule.default });
    api.routes.set('/ai/mcp/tool/available', () => ({ code: 200, data }));
    assert.deepEqual((await getAvailableMcpTools()).data, data);
  });
}

for (const [label, response] of [
  ['HTTP 500', () => Promise.reject(http500())],
  ['业务 code=500', () => ({ code: 500, msg: '可用工具业务失败', data: [] })]
]) {
  test(`工具选择可用列表 ${label} 拒绝而不是降级为空成功`, async () => {
    const api = await offlineApi();
    const { getAvailableMcpTools } = await loadProductionModule('src/api/ai/mcpTool.ts', { '@/utils/request': api.requestModule.default });
    api.routes.set('/ai/mcp/tool/available', response);
    await assert.rejects(getAvailableMcpTools());
    assert.ok(api.messages.some((message) => message.type === 'error'));
  });
}

for (const [label, response] of [
  ['HTTP 500', () => Promise.reject(http500())],
  ['业务 code=500', () => ({ code: 500, msg: '工具连接业务失败', data: { success: true } })]
]) {
  test(`工具连接测试 ${label} 不报连接成功且恢复按钮`, async (t) => {
    const page = await setup(t);
    page.routes.set(`/ai/mcp/tool/${tool.id}/test`, response);
    await page.button('测试').props.onClick();
    await flush();
    assert.equal(page.messages.filter((message) => message.type === 'success').length, 0);
    assert.ok(page.messages.some((message) => message.type === 'error'));
    assert.equal(page.button('测试').props.loading, false);
  });
}
