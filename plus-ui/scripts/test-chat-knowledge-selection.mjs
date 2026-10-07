import assert from 'node:assert/strict';
import { mkdirSync, readFileSync } from 'node:fs';
import { buildSync, transformSync } from 'esbuild';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { parse } from '@vue/compiler-sfc';
import { createRenderer, defineComponent, h, ref, nextTick, onUnmounted } from 'vue';
import SelectScript from '../node_modules/element-plus/es/components/select/src/select.vue_vue_type_script_lang.mjs';
import { ID_INJECTION_KEY } from '../node_modules/element-plus/es/hooks/use-id/index.mjs';

const chatApi = readFileSync(new URL('../src/api/ai/chat.ts', import.meta.url), 'utf8');
const assistant = parse(readFileSync(new URL('../src/views/ai/assistant/index.vue', import.meta.url), 'utf8')).descriptor;

test('聊天请求必须支持无损字符串知识库ID，沿用现有后端knowledgeId契约', () => {
  assert.match(chatApi, /knowledgeId\?:\s*string/, '不能只选界面而遗漏后端知识库范围');
});

test('知识库模式必须显式选库，不能把Milvus全库空检索冒充企业资料回答', () => {
  assert.match(assistant.template.content, /v-model="selectedKnowledgeId"/, '实际页面需要知识库选择入口');
  assert.match(assistant.scriptSetup.content, /prepareChatKnowledgeRequest/, '发送前必须验证所选知识库和模式');
});

// 测试产物写入本工作树忽略目录，不写共享node_modules或用旧bundle冒充本轮。
const outputDirectory = new URL('../../.codex/chat-knowledge-tests/', import.meta.url);
mkdirSync(outputDirectory, { recursive: true });
const bundle = new URL(`knowledge-selection-${process.pid}.mjs`, outputDirectory);
buildSync({
  entryPoints: [fileURLToPath(new URL('../src/views/ai/assistant/knowledgeSelection.ts', import.meta.url))],
  bundle: true,
  platform: 'node',
  format: 'esm',
  outfile: fileURLToPath(bundle)
});
const { normalizeChatKnowledgeId, chatKnowledgeChoices, prepareChatKnowledgeRequest } = await import(bundle.href);
const bigId = '2103871286244564993';
const available = [{ id: bigId, label: '生产交付风险处置指引' }];
const ready = { loading: false, error: '', canList: true };

for (const id of [bigId, '9223372036854775807', '1']) {
  test(`字符串知识库ID无损接受：${id}`, () => assert.equal(normalizeChatKnowledgeId(id), id));
}

for (const id of [
  Number(bigId),
  0,
  -1,
  1.5,
  NaN,
  Infinity,
  null,
  undefined,
  {},
  [],
  '0',
  '-1',
  '01',
  ' 1',
  '1 ',
  '1e3',
  '9223372036854775808',
  '1/../../'
]) {
  test(`非法或已失真知识库ID拒绝：${String(id)}`, () => assert.equal(normalizeChatKnowledgeId(id), undefined));
}

test('历史安全小ID可规范为字符串，不进行大ID取整', () => assert.equal(normalizeChatKnowledgeId(26), '26'));

test('列表只采用启用、就绪且有正常名称的知识库', () => {
  const row = { id: bigId, knowledgeName: '生产交付风险处置指引', status: '0', vectorIndexStatus: 'READY' };
  assert.deepEqual(chatKnowledgeChoices([row]), available);
  for (const invalid of [
    { ...row, status: '1' },
    { ...row, status: null },
    { ...row, vectorIndexStatus: 'REBUILDING' },
    { ...row, vectorIndexStatus: 'FAILED' },
    { ...row, vectorIndexStatus: undefined },
    { ...row, id: Number(bigId) },
    { ...row, knowledgeName: '' }
  ]) {
    assert.deepEqual(chatKnowledgeChoices([invalid]), []);
  }
});

test('重复知识库身份不得自动选用任意一个名称', () => {
  const row = { id: bigId, knowledgeName: '处置指引', status: '0', vectorIndexStatus: 'READY' };
  assert.deepEqual(chatKnowledgeChoices([row, { ...row, knowledgeName: '另一名称' }]), []);
  assert.deepEqual(chatKnowledgeChoices({ rows: [row] }), []);
});

test('RAG请求明确透传原字符串ID，JSON往返不损失', () => {
  assert.deepEqual(prepareChatKnowledgeRequest('rag', bigId, available, ready), { knowledgeId: bigId });
  assert.equal(JSON.parse(JSON.stringify(prepareChatKnowledgeRequest('rag', bigId, available, ready))).knowledgeId, bigId);
});

for (const mode of ['auto', 'chat', 'agent']) {
  test(`${mode}不夹带选库状态也不受RAG加载阻断`, () => {
    assert.deepEqual(prepareChatKnowledgeRequest(mode, bigId, available, { loading: true, error: '失败', canList: false }), {});
  });
}

for (const [label, selected, choices, state] of [
  ['未选择', '', available, ready],
  ['已失真', Number(bigId), available, ready],
  ['选择失效', bigId, [], ready],
  ['未知知识库', '26', available, ready],
  ['列表加载中', bigId, available, { ...ready, loading: true }],
  ['读取失败', bigId, available, { ...ready, error: '失败' }],
  ['没有列表权限', bigId, available, { ...ready, canList: false }]
]) {
  test(`RAG发送前拒绝：${label}`, () => {
    const request = prepareChatKnowledgeRequest('rag', selected, choices, state);
    assert.ok(request.error);
    assert.equal(request.knowledgeId, undefined);
  });
}

test('实际发送在追加消息和调用API之前验证知识库，不把错误当空命中', () => {
  const source = assistant.scriptSetup.content;
  const start = source.indexOf('async function sendMessage()');
  const end = source.indexOf('/** 打开 MCP 工具选择器', start);
  assert.ok(start >= 0 && end > start, '必须准确截取发送函数，缺失标记不能静默通过');
  const body = source.slice(start, end);
  const guardIndex = body.indexOf('prepareChatKnowledgeRequest');
  const messageIndex = body.indexOf('messages.value.push');
  assert.ok(guardIndex >= 0 && messageIndex >= 0 && guardIndex < messageIndex, '校验调用必须真实存在且位于追加消息之前');
  assert.match(body, /if \(knowledgeRequest\.error\) \{[\s\S]*?return;/);
  assert.match(body, /knowledgeId:\s*knowledgeRequest\.knowledgeId/);
  assert.match(source, /listKnowledge\(\{[\s\S]*?pageSize:\s*100/);
  assert.match(source, /version !== knowledgeRequestVersion/);
  assert.match(source, /checkPermi\(\['ai:knowledge:list'\]\)/);
});

for (const badFirst of [true, false]) {
  for (const bad of [{ knowledgeName: '' }, { status: '1' }]) {
    test(`重复身份拒绝与无效记录顺序无关：${badFirst}/${JSON.stringify(bad)}`, () => {
      const valid = { id: bigId, knowledgeName: '处置指引', status: '0', vectorIndexStatus: 'READY' };
      const invalid = { ...valid, ...bad };
      assert.deepEqual(chatKnowledgeChoices(badFirst ? [invalid, valid] : [valid, invalid]), []);
    });
  }
}

// 执行实际选择组件的输入、防抖及选中事件；只替换列表来源，不挂浏览器 DOM。
test('远程输入后立即选中分页外知识库，排队回调不能清除明确选择', async () => {
  const source = assistant.scriptSetup.content;
  const start = source.indexOf("async function loadChatKnowledge(query = '')");
  const end = source.indexOf('\nwatch(chatMode', start);
  assert.ok(start >= 0 && end > start, '实际页面列表加载器必须存在');
  const loader = transformSync(source.slice(start, end), { loader: 'ts', format: 'esm' }).code;
  const make = new Function(
    'ref',
    'listKnowledge',
    'chatKnowledgeChoices',
    `
    const selectedKnowledgeId = ref('');
    const knowledgeOptions = ref([]);
    const knowledgeLoading = ref(false);
    const knowledgeLoadError = ref('');
    const canListKnowledge = ref(true);
    let knowledgeRequestVersion = 0;
    ${loader}
    return { loadChatKnowledge, selectedKnowledgeId, knowledgeOptions, knowledgeLoading,
      dispose() { knowledgeRequestVersion++; } };
  `
  );
  const calls = [];
  const controller = make(
    ref,
    async (query) => {
      calls.push(query.knowledgeName ?? '');
      return { rows: [{ id: query.knowledgeName ? bigId : '1', knowledgeName: '工艺处置指引', status: '0', vectorIndexStatus: 'READY' }] };
    },
    chatKnowledgeChoices
  );
  const findSelect = (node) => {
    if (node?.tag === 'el-select' && node.props?.some((prop) => prop.name === 'model' && prop.exp?.content === 'selectedKnowledgeId')) return node;
    for (const child of node?.children ?? []) {
      const match = findSelect(child);
      if (match) return match;
    }
  };
  const selectTemplate = findSelect(assistant.template.ast);
  assert.ok(selectTemplate, '必须取实际页面知识库选择控件，不能单独写固定的修复桩');
  const debounceProp = selectTemplate.props.find((prop) => prop.name === 'bind' && prop.arg?.content === 'debounce');
  const templateProps = {};
  if (debounceProp) {
    assert.match(debounceProp.exp?.content ?? '', /^\d+$/, '本回归读取实际模板的数值防抖配置');
    templateProps.debounce = Number(debounceProp.exp.content);
  }
  const renderer = createRenderer({
    patchProp() {},
    insert(node, parent) {
      node.parent = parent;
      (parent.children ??= []).push(node);
    },
    remove(node) {
      if (node.parent) node.parent.children = node.parent.children.filter((item) => item !== node);
    },
    createElement: (tag) => ({ tag, children: [] }),
    createText: (text) => ({ text }),
    createComment: (text) => ({ comment: text }),
    setText(node, text) {
      node.text = text;
    },
    setElementText(node, text) {
      node.text = text;
    },
    parentNode: (node) => node.parent,
    nextSibling: () => null
  });
  let select;
  const Child = defineComponent({
    ...SelectScript,
    setup(props, ctx) {
      select = SelectScript.setup(props, ctx);
      return select;
    },
    render() {
      return null;
    }
  });
  const Page = defineComponent({
    setup() {
      onUnmounted(() => controller.dispose());
      return () =>
        h(Child, {
          ...templateProps,
          filterable: true,
          remote: true,
          loading: controller.knowledgeLoading.value,
          remoteMethod: controller.loadChatKnowledge,
          modelValue: controller.selectedKnowledgeId.value,
          'onUpdate:modelValue': (value) => {
            controller.selectedKnowledgeId.value = value;
          }
        });
    }
  });
  const app = renderer.createApp(Page);
  app.provide(ID_INJECTION_KEY, { prefix: 1, current: 0 });
  app.mount({ children: [] });
  const settleDebounce = async () => {
    await new Promise((resolve) => setTimeout(resolve, 350));
    await nextTick();
  };
  try {
    select.expanded.value = true;
    await nextTick();
    select.onInput({ target: { value: '目标库' } });
    await settleDebounce();
    assert.deepEqual(
      controller.knowledgeOptions.value.map((item) => item.id),
      [bigId]
    );
    select.onInput({ target: { value: '目标库A' } });
    select.handleOptionSelect({ value: bigId, currentLabel: '工艺处置指引' });
    await nextTick();
    assert.equal(controller.selectedKnowledgeId.value, bigId);
    await settleDebounce();
    assert.equal(controller.selectedKnowledgeId.value, bigId, '明确选中不能被随后空查询清除');
    assert.deepEqual(
      controller.knowledgeOptions.value.map((item) => item.id),
      [bigId]
    );
    assert.deepEqual(calls, ['', '目标库', '目标库A'], '不能在选中后发起默认首屏空查询');
  } finally {
    app.unmount();
  }
});
