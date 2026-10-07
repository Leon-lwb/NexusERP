import assert from 'node:assert/strict';
import test from 'node:test';
import { deferred, flush, http500, mountPage, text } from './component-harness.mjs';

const knowledgeA = { id: '2103871286244564993', knowledgeName: '装配作业指导', status: '0', vectorIndexStatus: 'READY' };
const knowledgeB = { ...knowledgeA, id: '2103871286244564995', knowledgeName: '检验作业指导' };
const retrievalUrl = '/ai/knowledge/retrieval';

async function setup(t) {
  const page = await mountPage('src/views/ai/knowledge/index.vue');
  t.after(page.unmount);
  await select(page, knowledgeA);
  return page;
}

async function select(page, knowledge) {
  page.nodes('el-table')[0].props.onCurrentChange(knowledge);
  await flush();
}

async function search(page, query = '装配规范') {
  const input = page.nodes('el-input').find((node) => node.props.placeholder === '输入检索文本');
  assert.ok(input, '必须通过真实检索输入框更新查询文本');
  input.props['onUpdate:modelValue'](query);
  await flush();
  page.button('测试').props.onClick();
  await flush();
}

function emptyResults(page) {
  return page.nodes('el-empty').filter((node) => node.props.description === '未检索到相关内容');
}

function errors(page) {
  return page.nodes('el-alert').filter((node) => node.props.type === 'error');
}

for (const [label, response] of [
  ['HTTP 500', () => Promise.reject(http500())],
  ['HTTP 200 + 业务 code=500', () => ({ code: 500, msg: '离线检索业务失败', data: [] })]
]) {
  test(`${label} 显示检索失败而不是成功无命中，且不残留上轮结果`, async (t) => {
    const page = await setup(t);
    page.routes.set(retrievalUrl, () => ({ code: 200, data: ['上轮命中内容'] }));
    await search(page);
    assert.match(text(page.root), /上轮命中内容/);
    page.routes.set(retrievalUrl, response);
    await search(page);
    assert.equal(emptyResults(page).length, 0, '失败不能显示为正常空结果');
    assert.equal(errors(page).length, 1, '失败必须留有明确可见的面板错误');
    assert.match(errors(page)[0].props.title, /检索失败/);
    assert.doesNotMatch(text(page.root), /上轮命中内容/);
    assert.equal(page.button('测试').props.loading, false);
  });
}

test('正常空数组只有成功完成后才显示无命中', async (t) => {
  const page = await setup(t);
  const pending = deferred();
  page.routes.set(retrievalUrl, () => pending.promise);
  await search(page);
  assert.equal(emptyResults(page).length, 0, '请求等待中不是成功无命中');
  assert.equal(page.button('测试').props.loading, true);
  pending.resolve({ code: 200, data: [] });
  await flush();
  assert.equal(emptyResults(page).length, 1);
  assert.equal(errors(page).length, 0);
  assert.equal(page.button('测试').props.loading, false);
});

test('非空结果真实渲染文本和片段内容，大 ID 原字符串进入请求', async (t) => {
  const page = await setup(t);
  page.routes.set(retrievalUrl, () => ({ code: 200, data: ['装配扭矩标准', { content: '检验复核步骤' }] }));
  await search(page);
  assert.match(text(page.root), /装配扭矩标准/);
  assert.match(text(page.root), /检验复核步骤/);
  assert.equal(page.calls.findLast((call) => call.url === retrievalUrl).data.knowledgeId, knowledgeA.id);
  assert.equal(emptyResults(page).length, 0);
  assert.equal(errors(page).length, 0);
});

test('新检索一开始立即清除上轮命中，不把旧内容留在等待面板', async (t) => {
  const page = await setup(t);
  page.routes.set(retrievalUrl, () => ({ code: 200, data: ['上轮命中内容'] }));
  await search(page);
  const pending = deferred();
  page.routes.set(retrievalUrl, () => pending.promise);
  await search(page, '新查询');
  assert.doesNotMatch(text(page.root), /上轮命中内容/);
  pending.resolve({ code: 200, data: [] });
  await flush();
});

test('同库连续检索逆序返回，只展示最新一次结果', async (t) => {
  const page = await setup(t);
  const first = deferred();
  const second = deferred();
  page.routes.set(retrievalUrl, ({ data }) => (data.query === '第一次' ? first.promise : second.promise));
  await search(page, '第一次');
  await search(page, '第二次');
  second.resolve({ code: 200, data: ['最新查询命中'] });
  await flush();
  first.resolve({ code: 200, data: ['过期查询命中'] });
  await flush();
  assert.match(text(page.root), /最新查询命中/);
  assert.doesNotMatch(text(page.root), /过期查询命中/);
});

test('旧请求完成不能提前结束新请求的加载态', async (t) => {
  const page = await setup(t);
  const first = deferred();
  const second = deferred();
  page.routes.set(retrievalUrl, ({ data }) => (data.query === '第一次' ? first.promise : second.promise));
  await search(page, '第一次');
  await search(page, '第二次');
  first.resolve({ code: 200, data: ['过期内容'] });
  await flush();
  assert.equal(page.button('测试').props.loading, true);
  assert.doesNotMatch(text(page.root), /过期内容/);
  second.resolve({ code: 200, data: [] });
  await flush();
});

test('切换知识库立即重置状态，旧库迟到成功不污染新库', async (t) => {
  const page = await setup(t);
  const pending = deferred();
  page.routes.set(retrievalUrl, () => pending.promise);
  await search(page);
  await select(page, knowledgeB);
  assert.equal(page.button('测试').props.loading, false, '切库后不能继承旧库的等待态');
  pending.resolve({ code: 200, data: ['旧库迟到内容'] });
  await flush();
  assert.doesNotMatch(text(page.root), /旧库迟到内容/);
  assert.equal(emptyResults(page).length, 0);
  assert.equal(errors(page).length, 0);
});

test('切库后旧请求迟到失败不清掉新库成功结果', async (t) => {
  const page = await setup(t);
  const pending = deferred();
  page.routes.set(retrievalUrl, ({ data }) => (data.knowledgeId === knowledgeA.id ? pending.promise : { code: 200, data: ['新库命中'] }));
  await search(page);
  await select(page, knowledgeB);
  await search(page);
  pending.reject(http500());
  await flush();
  assert.match(text(page.root), /新库命中/);
  assert.equal(errors(page).length, 0);
  assert.equal(emptyResults(page).length, 0);
});

test('切库后旧请求迟到不能结束新库正在进行的加载态', async (t) => {
  const page = await setup(t);
  const first = deferred();
  const second = deferred();
  page.routes.set(retrievalUrl, ({ data }) => (data.knowledgeId === knowledgeA.id ? first.promise : second.promise));
  await search(page);
  await select(page, knowledgeB);
  await search(page);
  first.resolve({ code: 200, data: [] });
  await flush();
  assert.equal(page.button('测试').props.loading, true);
  assert.equal(emptyResults(page).length, 0);
  second.resolve({ code: 200, data: [] });
  await flush();
});

test('A→B→A 切回同一个知识库也丢弃第一次 A 的迟到结果', async (t) => {
  const page = await setup(t);
  const first = deferred();
  page.routes.set(retrievalUrl, ({ data }) => (data.query === '旧查询' ? first.promise : { code: 200, data: ['切回后的新结果'] }));
  await search(page, '旧查询');
  await select(page, knowledgeB);
  await select(page, knowledgeA);
  await search(page, '新查询');
  first.resolve({ code: 200, data: ['切回前的旧结果'] });
  await flush();
  assert.match(text(page.root), /切回后的新结果/);
  assert.doesNotMatch(text(page.root), /切回前的旧结果/);
});

test('成功信封缺失数组不能被误报成成功无命中', async (t) => {
  const page = await setup(t);
  page.routes.set(retrievalUrl, () => ({ code: 200 }));
  await search(page);
  assert.equal(emptyResults(page).length, 0);
  assert.equal(errors(page).length, 1);
});

test('空白查询不发请求、不显示成功无命中', async (t) => {
  const page = await setup(t);
  await search(page, '   ');
  assert.equal(page.calls.filter((call) => call.url === retrievalUrl).length, 0);
  assert.equal(emptyResults(page).length, 0);
  assert.ok(page.messages.some((message) => message.type === 'warning'));
});

test('失败后重试开始清空面板错误，成功空数组再显示正常无命中', async (t) => {
  const page = await setup(t);
  page.routes.set(retrievalUrl, () => Promise.reject(http500()));
  await search(page);
  assert.equal(errors(page).length, 1);
  const pending = deferred();
  page.routes.set(retrievalUrl, () => pending.promise);
  await search(page);
  assert.equal(errors(page).length, 0);
  assert.equal(emptyResults(page).length, 0);
  pending.resolve({ code: 200, data: [] });
  await flush();
  assert.equal(emptyResults(page).length, 1);
  assert.equal(errors(page).length, 0);
});

test('取消选库后迟到响应不恢复详情或残留结果', async (t) => {
  const page = await setup(t);
  const pending = deferred();
  page.routes.set(retrievalUrl, () => pending.promise);
  await search(page);
  await select(page, null);
  pending.resolve({ code: 200, data: ['取消选库后的旧内容'] });
  await flush();
  assert.equal(page.nodes('el-tab-pane').length, 0);
  await select(page, knowledgeB);
  assert.doesNotMatch(text(page.root), /取消选库后的旧内容/);
  assert.equal(emptyResults(page).length, 0);
  assert.equal(page.button('测试').props.loading, false);
});
