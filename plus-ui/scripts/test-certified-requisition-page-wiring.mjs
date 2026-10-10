import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { test } from 'node:test';
import { parse as parseSfc } from '@vue/compiler-sfc';
import ts from 'typescript';
import * as Vue from 'vue';

const base = new URL('../src/views/erp/pu/requisition/', import.meta.url);
const controllerUrl = new URL('certifiedWorkflow.ts', base);
const protocolCache = new Map();
/** 执行真实纯协议依赖，不用常量true替代认证来源判断。 */
function protocol() {
  if (!existsSync(controllerUrl)) return undefined;
  function load(url) {
    if (protocolCache.has(url.href)) return protocolCache.get(url.href);
    const module = { exports: {} };
    protocolCache.set(url.href, module.exports);
    const js = ts.transpileModule(readFileSync(url, 'utf8'), {
      compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
    }).outputText;
    const require = (path) => {
      if (path === './certifiedPurchase') return load(new URL('certifiedPurchase.ts', base));
      throw new Error(`未声明协议依赖: ${path}`);
    };
    new Function('require', 'module', 'exports', js)(require, module, module.exports);
    return module.exports;
  }
  return load(controllerUrl);
}

/** 只给真实页面脚本隔离UI框架及网络端口，实际handler、响应加载与条件计算保持原代码。 */
function page(name, sourceType = 'AI_DECISION', overrides = {}) {
  const source = readFileSync(new URL(name, base), 'utf8');
  const { descriptor, errors } = parseSfc(source);
  assert.deepEqual(errors, []);
  const ast = ts.createSourceFile(name + '.ts', descriptor.scriptSetup.content, ts.ScriptTarget.ES2022, true, ts.ScriptKind.TS);
  const body = ast.statements
    .filter((node) => !ts.isImportDeclaration(node) && !ts.isExportDeclaration(node))
    .map((node) => node.getText(ast))
    .join('\n');
  const js = ts.transpileModule(body, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None } }).outputText;
  const document = {
    requisitionId: '9007199254740993',
    requisitionNo: 'TEST-REQUEST',
    sourceType,
    status: 'DRAFT',
    version: 0,
    decisionIdempotencyKey: sourceType === 'MANUAL' ? null : 'TEST-SOURCE-KEY',
    decisionRunId: sourceType === 'MANUAL' ? null : '31',
    decisionActionId: sourceType === 'MANUAL' ? null : '41',
    selectFlowId: null,
    puPurchaseRequisitionLineList: []
  };
  const calls = [];
  const messages = [];
  const modal = Object.fromEntries(['msgSuccess', 'msgError', 'msgWarning', 'msgInfo'].map((name) => [name, (message) => messages.push(message)]));
  const route = { path: '/erp/pu/requisition/form', query: { id: document.requisitionId, action: 'edit' } };
  const router = { push: () => {} };
  const p = protocol();
  const missingProtocol = () => assert.fail('真实认证生命周期协议尚未接入');
  const deps = {
    ref: Vue.ref,
    computed: Vue.computed,
    reactive: Vue.reactive,
    nextTick: Vue.nextTick,
    getCurrentInstance: () => ({
      proxy: {
        $modal: modal,
        $refs: { formRef: { validate: (callback) => callback(true) } },
        useDict: () => ({ pu_purchase_requisition_status: [], pu_source_type: [] })
      }
    }),
    toRefs: (value) => Object.fromEntries(Object.entries(value).map(([key, value]) => [key, Vue.ref(value)])),
    onMounted: () => {},
    onActivated: () => {},
    onBeforeUnmount: () => {},
    watch: () => {},
    defineProps: () => ({ disabled: false, model: {}, customFormData: {}, isNew: false }),
    useRoute: () => route,
    useRouter: () => router,
    useCrud: () => ({
      loading: Vue.ref(false),
      list: Vue.ref([document]),
      total: Vue.ref(1),
      ids: Vue.ref([document.requisitionId]),
      single: Vue.ref(false),
      multiple: Vue.ref(false),
      showSearch: Vue.ref(true),
      getList: () => {},
      handleQuery: () => {},
      handleDelete: () => {},
      handleExport: () => {},
      handleSelectionChange: () => {}
    }),
    getPurchaseRequisition: async () => ({ code: 200, data: { ...document } }),
    listPurchaseRequisition: async () => ({ rows: [document], total: 1 }),
    updatePurchaseRequisition: async () => {
      calls.push('legacy-update');
      return { code: 200 };
    },
    delPurchaseRequisition: async () => {},
    convertPurchaseRequisition: async () => {
      calls.push('legacy-convert');
      return { code: 200, data: '51' };
    },
    submitRequisitionFlow: async () => {
      calls.push('legacy-flowable');
      return { code: 200 };
    },
    getRequisitionProcId: async () => ({ code: 200, data: null }),
    getProcesss: async () => ({ rows: [], data: [] }),
    previewCode: async () => 'TEST-CODE',
    decisionPurchaseResultQuery: () => ({ valid: false }),
    verifiedDecisionErpResult: async () => undefined,
    isProtectedRequisitionHint: p?.isProtectedRequisitionHint ?? missingProtocol,
    normalizeRequisitionId: p?.normalizeRequisitionId ?? missingProtocol,
    ...overrides
  };
  const names = Object.keys(deps);
  const expose =
    name === 'index.vue'
      ? '{handleConvert, handleUpdate, selectedRequisition:typeof selectedRequisition!=="undefined"?selectedRequisition:undefined}'
      : '{handleConvert,handleSubmitFlow,submitForm,getInfo,form,isViewMode,formDisabled,flowOptions}';
  const state = new Function(...names, js + '\nreturn ' + expose)(...names.map((name) => deps[name]));
  return { state, calls, messages, source, route };
}

/** 明确等待响应microtask，不sleep或联网。 */
async function loadForm(value) {
  value.state.getInfo('9007199254740993');
  for (let i = 0; i < 6; i++) await Promise.resolve();
  assert.equal(value.state.form.value.requisitionId, '9007199254740993');
}

test('列表认证来源不能发送旧PUT转单', async () => {
  const value = page('index.vue');
  await value.state.handleConvert();
  assert.deepEqual(value.calls, [], '必须走专属认证入口，不能依赖旧接口报错阻止');
});

test('表单认证来源不能发送旧转单，即使本地来源被改为MANUAL', async () => {
  const value = page('form.vue');
  await loadForm(value);
  value.state.form.value.sourceType = 'MANUAL';
  value.state.form.value.decisionIdempotencyKey = null;
  value.state.form.value.decisionRunId = null;
  value.state.form.value.decisionActionId = null;
  await value.state.handleConvert();
  assert.deepEqual(value.calls, [], '加载的原始保护事实不能随可编辑表单一起消失');
});

test('表单认证来源不能进入旧更新再Flowable提交', async () => {
  const value = page('form.vue');
  await loadForm(value);
  value.state.form.value.selectFlowId = '71';
  await value.state.handleSubmitFlow();
  for (let i = 0; i < 3; i++) await Promise.resolve();
  assert.deepEqual(value.calls, []);
});

test('认证来源表单只读，直接调用保存handler也没有普通更新', async () => {
  const value = page('form.vue');
  await loadForm(value);
  await value.state.submitForm();
  assert.deepEqual(value.calls, []);
  assert.equal(value.state.formDisabled.value, true);
});

test('普通MANUAL旧转单能力不被误禁用', async () => {
  const value = page('index.vue', 'MANUAL');
  await value.state.handleConvert();
  assert.deepEqual(value.calls, ['legacy-convert']);
});

test('普通MANUAL表单保存能力保留', async () => {
  const value = page('form.vue', 'MANUAL');
  await loadForm(value);
  await value.state.submitForm();
  assert.deepEqual(value.calls, ['legacy-update']);
});

test('认证生命周期组件、列表和表单均有实际接线，旧edit不作为认证批准权限', () => {
  const dialog = new URL('CertifiedRequisitionWorkflowDialog.vue', base);
  const source = existsSync(dialog) ? readFileSync(dialog, 'utf8') : '';
  assert.match(source, /createCertifiedRequisitionWorkflow/, '缺少实际专属流程组件');
  assert.match(source, /pu:requisition:approve/);
  assert.match(source, /pu:requisition:convert/);
  for (const name of ['index.vue', 'form.vue']) {
    const text = readFileSync(new URL(name, base), 'utf8');
    assert.match(text, /<CertifiedRequisitionWorkflowDialog/);
    assert.match(text, /handleCertifiedWorkflow/);
  }
});

/** 更新和表单验证都是异步边界：原A响应不允许在随后显示的受保护B上续接旧流程。 */
for (const boundary of ['UPDATE', 'VALIDATE']) {
  test(`普通A的迟到${boundary}不能对当前受保护B提交旧Flowable`, async () => {
    const aid = '9007199254740993';
    const bid = '9007199254740999';
    const a = {
      requisitionId: aid,
      requisitionNo: 'TEST-A',
      sourceType: 'MANUAL',
      status: 'DRAFT',
      version: 0,
      decisionIdempotencyKey: null,
      decisionRunId: null,
      decisionActionId: null,
      selectFlowId: '71',
      puPurchaseRequisitionLineList: []
    };
    const b = {
      ...a,
      requisitionId: bid,
      requisitionNo: 'TEST-B',
      sourceType: 'AI_DECISION',
      decisionIdempotencyKey: 'TEST-B-KEY',
      decisionRunId: '31',
      decisionActionId: '41',
      selectFlowId: null
    };
    let releaseUpdate;
    let validation;
    const writes = [];
    const value = page('form.vue', 'MANUAL', {
      getPurchaseRequisition: async (id) => ({ code: 200, data: { ...(id === aid ? a : b) } }),
      updatePurchaseRequisition: (data) => {
        writes.push(['update', data.requisitionId]);
        return new Promise((resolve) => {
          releaseUpdate = resolve;
        });
      },
      submitRequisitionFlow: async (id, flow) => {
        writes.push(['flow', id, flow]);
        return { code: 200 };
      },
      getCurrentInstance: () => ({
        proxy: {
          $modal: { msgSuccess: () => {}, msgError: () => {}, msgWarning: () => {}, msgInfo: () => {} },
          $refs: { formRef: { validate: (callback) => (boundary === 'VALIDATE' ? (validation = callback) : callback(true)) } },
          useDict: () => ({ pu_purchase_requisition_status: [], pu_source_type: [] })
        }
      })
    });
    await loadForm(value);
    value.state.flowOptions.value = [{ id: '71' }];
    value.state.handleSubmitFlow();
    value.route.query = { id: bid, action: 'view' };
    value.state.isViewMode.value = true;
    value.state.getInfo(bid);
    for (let i = 0; i < 8; i++) await Promise.resolve();
    assert.equal(value.state.form.value.requisitionId, bid);
    assert.equal(value.state.formDisabled.value, true);
    assert.equal(value.state.form.value.selectFlowId, '71', 'B原生null，仅真实tryAutoSelect回填，不能伪造DB流程身份');
    if (boundary === 'VALIDATE') validation(true);
    if (releaseUpdate) releaseUpdate({ code: 200 });
    for (let i = 0; i < 8; i++) await Promise.resolve();
    assert.deepEqual(
      writes.filter(([kind]) => kind === 'flow'),
      [],
      '旧响应不得对B或A自动续链'
    );
    assert.equal(writes.filter(([kind]) => kind === 'update').length, boundary === 'UPDATE' ? 1 : 0, '迟到验证不能开始新普通更新');
  });
}

test('普通MANUAL身份未变化时原更新与Flowable顺序保留', async () => {
  const writes = [];
  let releaseUpdate;
  const value = page('form.vue', 'MANUAL', {
    updatePurchaseRequisition: (data) => {
      writes.push(['update', data.requisitionId]);
      return new Promise((resolve) => {
        releaseUpdate = resolve;
      });
    },
    submitRequisitionFlow: async (id, flow) => {
      writes.push(['flow', id, flow]);
      return { code: 200 };
    }
  });
  await loadForm(value);
  value.state.form.value.selectFlowId = '71';
  value.state.handleSubmitFlow();
  assert.deepEqual(writes, [['update', '9007199254740993']]);
  releaseUpdate({ code: 200 });
  for (let i = 0; i < 8; i++) await Promise.resolve();
  assert.deepEqual(writes, [
    ['update', '9007199254740993'],
    ['flow', '9007199254740993', '71']
  ]);
});
