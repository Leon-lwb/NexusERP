/**
 * 认证采购生命周期对话框的有界离线挂载测试。
 *
 * 真实 @vue/compiler-sfc 分别编译 script/template，真实 Vue renderer 执行 setup、
 * 模板事件、指令及卸载钩子；certifiedWorkflow.ts 原样转译执行，不替换控制器。
 * 仅 Element Plus、API、auth、Storage/window 是隔离 port，不发送 HTTP，不访问 DB。
 * 编译/挂载失败属于 fixture bootstrap，不冒充生产行为 RED；通过也不代表生产验收。
 * @example node --test --test-concurrency=1 scripts/test-certified-requisition-dialog.mjs
 */
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import * as sfc from '@vue/compiler-sfc';
import ts from 'typescript';
import * as Vue from 'vue';

const componentUrl = new URL('../src/views/erp/pu/requisition/CertifiedRequisitionWorkflowDialog.vue', import.meta.url);
const controllerUrl = new URL('../src/views/erp/pu/requisition/certifiedWorkflow.ts', import.meta.url);
const ID = '9007199254740993';
const OTHER_ID = '9007199254740995';
const ORDER_ID = '9007199254740997';
const TOKEN_A = 'dialog-offline-credential-a';
const TOKEN_B = 'dialog-offline-credential-b';
const RAW_ERROR = `隔离 port 原始错误不得外显 ${TOKEN_A}`;
const copy = (value) => JSON.parse(JSON.stringify(value));
const digest = (value) => createHash('sha256').update(value).digest('hex');

const context = (patch = {}) => ({ tenantId: '000001', userId: '101', approveAllowed: true, convertAllowed: true, ...patch });
const document = (patch = {}) => ({
  requisitionId: ID,
  requisitionNo: 'PR-20261009-001',
  sourceType: 'MRP',
  status: 'DRAFT',
  version: 5,
  decisionIdempotencyKey: 'original-command-key',
  decisionRunId: null,
  decisionActionId: null,
  selectFlowId: null,
  puPurchaseRequisitionLineList: [{ lineId: '9007199254740999', sourceType: 'MRP', applyQuantity: '4.50' }],
  ...patch
});

/** 沿用 Task2 fixture 的真实 EventReceipt 字段，不使用 allocations: [] 假成功。 */
function workflowResult(detail, operation, replayed) {
  const ordered = operation === 'convert';
  return {
    requisitionId: detail.requisitionId,
    requisitionNo: detail.requisitionNo,
    status: detail.status,
    version: detail.version,
    orderId: ordered ? ORDER_ID : null,
    orderNo: ordered ? 'PO-20261009-001' : null,
    replayed,
    allocations: [
      {
        eventId: '901',
        allocationId: '902',
        actorId: '101',
        requisitionLineId: '903',
        operation: ordered ? 'TRANSFER_TO_ORDER' : 'APPROVE',
        fromStatus: 'REQUEST_INTENT',
        toStatus: ordered ? 'ORDER_COMMITTED_NOT_TIMELY_SUPPLY' : 'REQUEST_INTENT',
        occupiedBefore: '4.50',
        occupiedAfter: '4.50',
        orderLineId: ordered ? '904' : null,
        replayed
      }
    ]
  };
}

/** 转译只为 Node 执行；不写共享 target，不把此步骤称为 vue-tsc 类型检查。 */
function transpile(source, filename) {
  const result = ts.transpileModule(source, {
    fileName: filename,
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
    reportDiagnostics: true
  });
  assert.deepEqual(
    result.diagnostics.filter((entry) => entry.category === ts.DiagnosticCategory.Error),
    [],
    `fixture bootstrap: ${filename} 转译失败`
  );
  return result.outputText;
}

/** 白名单 module port：未列出的运行依赖立即报错，不回退到真实网络/浏览器模块。 */
function evaluate(code, modules = {}, windowPort, bindings = []) {
  const exports = {};
  new Function('exports', 'require', 'window', code)(
    exports,
    (name) => {
      assert.ok(Object.hasOwn(modules, name), `fixture bootstrap: 禁止未隔离依赖 ${name}`);
      bindings.push({ name, module: modules[name] });
      return modules[name];
    },
    windowPort
  );
  return exports;
}

let artifacts;
/** 每次 Node 运行读取当前主线源码；保留编译器绑定元数据，事件不靠字符串重写。 */
function compileProduction() {
  if (artifacts) return artifacts;
  const componentSource = readFileSync(componentUrl, 'utf8');
  const controllerSource = readFileSync(controllerUrl, 'utf8');
  const filename = fileURLToPath(componentUrl);
  const { descriptor, errors } = sfc.parse(componentSource, { filename });
  assert.deepEqual(errors, [], 'fixture bootstrap: SFC parse 失败');
  assert.ok(descriptor.scriptSetup && descriptor.template, 'fixture bootstrap: 缺少真实 script setup/template');
  const compiledScript = sfc.compileScript(descriptor, { id: 'certified-requisition-dialog-sidecar' });
  const compiledTemplate = sfc.compileTemplate({
    source: descriptor.template.content,
    filename,
    id: 'certified-requisition-dialog-sidecar',
    compilerOptions: { bindingMetadata: compiledScript.bindings }
  });
  assert.deepEqual(compiledTemplate.errors, [], 'fixture bootstrap: 真实模板编译失败');
  const controller = evaluate(transpile(controllerSource, fileURLToPath(controllerUrl)));
  assert.equal(typeof controller.createCertifiedRequisitionWorkflow, 'function', 'fixture bootstrap: 缺少真实控制器导出');
  artifacts = {
    controller,
    script: transpile(compiledScript.content, filename + '.ts'),
    render: evaluate(transpile(compiledTemplate.code, filename + '.render.ts'), { vue: Vue }).render,
    componentSha256: digest(componentSource),
    controllerSha256: digest(controllerSource)
  };
  return artifacts;
}

function deferred() {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

function memoryStorage() {
  const values = new Map();
  const writes = [];
  const removed = [];
  return {
    values,
    writes,
    removed,
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => {
      writes.push({ key, value });
      values.set(key, value);
    },
    removeItem: (key) => {
      removed.push(key);
      values.delete(key);
    }
  };
}

/** 所有请求都止于这些离线 port；可等待的确认/写响应仅用受控 Promise。 */
function fixture(options = {}) {
  const detail = document(options.detail);
  const f = {
    token: options.token ?? TOKEN_A,
    context: context(options.context),
    documents: new Map([
      [ID, detail],
      [OTHER_ID, document({ requisitionId: OTHER_ID, requisitionNo: 'PR-20261009-002' })]
    ]),
    storage: options.storage ?? memoryStorage(),
    calls: [],
    writes: [],
    confirmations: [],
    changed: [],
    directiveCalls: [],
    warnings: [],
    errors: [],
    listeners: new Map(),
    confirmMode: 'confirm',
    confirmGate: null,
    writeGate: null,
    detailGate: null,
    writeOutcome: 'success'
  };
  f.confirm = async (message, title, settings) => {
    f.confirmations.push({ message, title, settings });
    if (f.confirmGate) await f.confirmGate.promise;
    if (f.confirmMode === 'cancel') throw new Error('cancel');
    return 'confirm';
  };
  const submit = async (operation, id, version, session) => {
    const call = { operation, id, version, session };
    f.calls.push(call);
    f.writes.push(call);
    const before = copy(f.documents.get(id));
    if (f.writeGate) await f.writeGate.promise;
    if (f.writeOutcome === 'http-503') {
      const error = new Error(RAW_ERROR);
      error.response = { status: 503 };
      throw error;
    }
    if (f.writeOutcome === 'envelope-500') return { code: 500, msg: RAW_ERROR };
    const replayed = operation === 'convert' && before.status === 'ORDERED';
    const detail = { ...before, status: operation === 'approve' ? 'APPROVED' : 'ORDERED', version: before.version + (replayed ? 0 : 1) };
    f.documents.set(id, detail);
    const result = workflowResult(detail, operation, replayed);
    if (f.writeOutcome === 'malformed-200') result.allocations = [];
    return { code: 200, data: result };
  };
  f.api = {
    getCertifiedRequisitionWorkflowContext: async (session) => {
      f.calls.push({ operation: 'context', session });
      return copy(f.context);
    },
    getCertifiedRequisitionWorkflowDetail: async (id, session) => {
      f.calls.push({ operation: 'detail', id, session });
      const detail = copy(f.documents.get(id));
      if (f.detailGate) await f.detailGate.promise;
      return detail;
    },
    approveCertifiedPurchaseRequisition: (id, version, session) => submit('approve', id, version, session),
    convertCertifiedPurchaseRequisition: (id, version, session) => submit('convert', id, version, session)
  };
  f.window = {
    localStorage: f.storage,
    addEventListener(name, handler) {
      if (!f.listeners.has(name)) f.listeners.set(name, new Set());
      f.listeners.get(name).add(handler);
    },
    removeEventListener(name, handler) {
      f.listeners.get(name)?.delete(handler);
    }
  };
  f.dispatch = (name) => {
    for (const handler of [...(f.listeners.get(name) ?? [])]) handler();
  };
  return f;
}

function host(tag, text = '') {
  return { tag, text, props: {}, permissions: [], children: [], parent: null };
}

/** 只提供宿主节点操作，reactivity/setup/render/指令/卸载均由真实 Vue 执行。 */
function renderer() {
  return Vue.createRenderer({
    createElement: (tag) => host(tag),
    createText: (text) => host('#text', text),
    createComment: (text) => host('#comment', text),
    setText: (node, text) => {
      node.text = text;
    },
    setElementText: (node, text) => {
      for (const child of node.children) child.parent = null;
      node.text = text;
      node.children = [];
    },
    patchProp: (node, key, _previous, value) => {
      node.props[key] = value;
    },
    insert(child, parent, anchor) {
      if (child.parent) {
        const previous = child.parent.children.indexOf(child);
        if (previous >= 0) child.parent.children.splice(previous, 1);
      }
      const position = anchor ? parent.children.indexOf(anchor) : -1;
      parent.children.splice(position < 0 ? parent.children.length : position, 0, child);
      child.parent = parent;
    },
    remove(child) {
      const position = child.parent?.children.indexOf(child) ?? -1;
      if (position >= 0) child.parent.children.splice(position, 1);
      child.parent = null;
    },
    parentNode: (node) => node.parent,
    nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null
  });
}

const textOf = (node) => (node.tag === '#comment' ? '' : node.text) + node.children.map(textOf).join('');
function findHosts(root, predicate) {
  return [...(predicate(root) ? [root] : []), ...root.children.flatMap((node) => findHosts(node, predicate))];
}

/** UI 仅透传 props 和具名 slot，不能在替身里重做 approve/convert/readOnly 的判断。 */
function uiPort(tag) {
  return Vue.defineComponent({
    name: `Offline-${tag}`,
    inheritAttrs: false,
    setup(_props, { attrs, slots }) {
      return () => Vue.h(tag, { ...attrs }, [slots.default?.(), slots.footer?.()]);
    }
  });
}

function mountDialog(t, f = fixture(), readOnly = false) {
  const compiled = compileProduction();
  const moduleBindings = [];
  const component = evaluate(
    compiled.script,
    {
      vue: Vue,
      'element-plus': { ElMessageBox: { confirm: f.confirm } },
      '@/utils/auth': { getToken: () => f.token },
      '@/api/erp/pu/requisition': f.api,
      './certifiedWorkflow': compiled.controller
    },
    f.window,
    moduleBindings
  ).default;
  component.render = compiled.render;
  const root = host('root');
  const dialogRef = Vue.ref();
  const liveProps = Vue.reactive({ readOnly });
  const app = renderer().createApp({
    setup: () => () => Vue.h(component, { ...liveProps, ref: dialogRef, onChanged: (...args) => f.changed.push(args) })
  });
  for (const tag of ['el-dialog', 'el-alert', 'el-descriptions', 'el-descriptions-item', 'el-button']) app.component(tag, uiPort(tag));
  const permissionPort = (node, binding) => {
    node.permissions = [...binding.value];
    f.directiveCalls.push({ node, value: [...binding.value] });
  };
  app.directive('hasPermi', { mounted: permissionPort, updated: permissionPort });
  app.config.warnHandler = (message) => f.warnings.push(message);
  app.config.errorHandler = (error, _instance, info) => f.errors.push({ error, info });
  app.mount(root);
  const instance = root._vnode.component.subTree.component;
  assert.ok(instance?.setupState && dialogRef.value, 'fixture bootstrap: 没有实际组件 setup/expose');
  let mounted = true;
  const ui = {
    f,
    root,
    instance,
    liveProps,
    moduleBindings,
    vm: dialogRef.value,
    setup: instance.setupState,
    controller: instance.setupState.controller,
    state: () => instance.setupState.state,
    dialog: () => findHosts(root, (node) => node.tag === 'el-dialog')[0],
    button(label) {
      const matches = findHosts(root, (node) => node.tag === 'el-button' && textOf(node).trim() === label);
      assert.equal(matches.length, 1, `应有唯一的已挂载按钮：${label}`);
      return matches[0];
    },
    writeButton(operation) {
      const permission = `pu:requisition:${operation}`;
      const matches = findHosts(root, (node) => node.tag === 'el-button' && node.permissions.includes(permission));
      assert.equal(matches.length, 1, `应有唯一实际 v-hasPermi 按钮：${permission}`);
      return matches[0];
    },
    fire(node) {
      assert.equal(typeof node.props.onClick, 'function', '应调用真实编译模板的事件 handler');
      // 刻意不模拟浏览器 disabled 拦截，验证强制调用 handler 时的生产守卫。
      return node.props.onClick({});
    },
    unmount() {
      if (mounted) {
        mounted = false;
        app.unmount();
      }
    }
  };
  t.after(() => {
    ui.unmount();
    assert.deepEqual(f.warnings, [], 'fixture bootstrap: 不能忽略真实 Vue 警告');
    assert.deepEqual(f.errors, [], 'fixture bootstrap: 不能吞掉真实 Vue render/event 错误');
  });
  return ui;
}

/** 仅排空真实 Vue/Promise microtask；有上限，不 sleep，不用时间猜测在途阶段。 */
async function until(predicate, message) {
  for (let attempt = 0; attempt < 80; attempt++) {
    await Promise.resolve();
    await Vue.nextTick();
    if (predicate()) return;
  }
  assert.fail(`fixture bootstrap: 等待受控阶段失败：${message}`);
}
const settled = async () => {
  await Promise.resolve();
  await Vue.nextTick();
};
const operations = (f) => f.calls.map((call) => call.operation);
function assertNoWrites(f) {
  assert.deepEqual(f.writes, [], '禁止业务写请求');
  assert.deepEqual(f.storage.writes, [], '无写意图时不应持久化锁');
  assert.deepEqual(f.storage.removed, [], '只读行为不能清锁');
  assert.deepEqual(f.confirmations, [], '禁写时不能诱导人工确认');
  assert.deepEqual(f.changed, [], '只读行为不发 changed');
}
function lockEntry(f) {
  assert.equal(f.storage.values.size, 1, '在途/未知操作只有原主体文档级锁');
  const [key, value] = [...f.storage.values][0];
  for (const token of [TOKEN_A, TOKEN_B]) {
    assert.ok(!key.includes(token) && !value.includes(token), '凭证不得进入锁');
  }
  return { key, value };
}
function assertLockRetained(f, saved) {
  assert.deepEqual(lockEntry(f), saved, '原锁内容不因重开/刷新/迟到响应/版本变化而改写');
  assert.deepEqual(f.storage.removed, [], '未知或失效 scope 不可清锁');
}
function assertSafeUi(ui) {
  const rendered =
    JSON.stringify(ui.state()) +
    textOf(ui.root) +
    findHosts(ui.root, (node) => node.tag === 'el-alert')
      .map((node) => node.props.title)
      .join('');
  for (const secret of [RAW_ERROR, TOKEN_A, TOKEN_B]) assert.ok(!rendered.includes(secret), '原始异常及凭据不得展示');
}

test('[fixture bootstrap] 真实 SFC script/template + Vue setup 挂载，控制器原样执行', async (t) => {
  const compiled = compileProduction();
  t.diagnostic(`Vue=${Vue.version}; compiler-sfc=${sfc.version}; TypeScript=${ts.version}`);
  t.diagnostic(`component SHA256=${compiled.componentSha256}; controller SHA256=${compiled.controllerSha256}`);
  const ui = mountDialog(t);
  assert.equal(
    ui.moduleBindings.find((binding) => binding.name === './certifiedWorkflow')?.module,
    compiled.controller,
    '实际 script 的控制器 import 必须绑定原生产 module，不用 mock'
  );
  assert.equal(typeof ui.vm.open, 'function');
  assert.equal(typeof ui.setup.execute, 'function');
  assert.ok(ui.f.listeners.get('storage')?.size === 1 && ui.f.listeners.get('focus')?.size === 1, '实际 setup 注册生命周期监听');
  await ui.vm.open(ID);
  await settled();
  assert.equal(ui.state().phase, 'READY');
  assert.deepEqual(ui.state(), ui.controller.snapshot(), '组件状态来自实际控制器');
});

test('open / 模板只读刷新 / focus 只查询 context-detail-context，不自动写或确认', async (t) => {
  const ui = mountDialog(t);
  await ui.vm.open(ID);
  await settled();
  assert.deepEqual(operations(ui.f), ['context', 'detail', 'context']);
  assert.equal(ui.state().document.requisitionId, ID, '长 ID 不经 Number');
  assert.equal(ui.dialog().props.modelValue, true);
  await ui.fire(ui.button('只读刷新'));
  await settled();
  assert.deepEqual(operations(ui.f), ['context', 'detail', 'context', 'context', 'detail', 'context']);
  ui.f.dispatch('focus');
  await until(() => !ui.setup.busy && ui.f.calls.length === 9, 'focus 只读刷新完成');
  assertNoWrites(ui.f);
});

for (const mode of ['props', 'open-option']) {
  for (const status of ['DRAFT', 'APPROVED', 'ORDERED']) {
    test(`readOnly ${mode} / ${status}：绕过 disabled 强制调用真实批准和转单 handler 仍零写`, async (t) => {
      const f = fixture({ detail: { status, version: status === 'DRAFT' ? 5 : status === 'APPROVED' ? 6 : 7 } });
      const ui = mountDialog(t, f, mode === 'props');
      await ui.vm.open(ID, mode === 'open-option' ? { readOnly: true } : {});
      await settled();
      assert.equal(ui.setup.effectiveReadonly, true);
      assert.equal(ui.state().canApprove, status === 'DRAFT', 'fixture 必须具有真实控制器写资格，不能因坏数据假绿');
      assert.equal(ui.state().canConvert, status !== 'DRAFT');
      for (const operation of ['approve', 'convert']) {
        const button = ui.writeButton(operation);
        assert.equal(button.props.disabled, true);
        await ui.fire(button);
      }
      await ui.fire(ui.button('只读刷新'));
      await settled();
      assert.deepEqual(operations(f), ['context', 'detail', 'context', 'context', 'detail', 'context']);
      assertNoWrites(f);
    });
  }
}

test('open-option 只读只影响本次面板，正常关闭重开默认仍只读加载、不自动批准', async (t) => {
  const ui = mountDialog(t);
  await ui.vm.open(ID, { readOnly: true });
  await settled();
  assert.equal(ui.setup.effectiveReadonly, true);
  ui.fire(ui.button('关闭'));
  await settled();
  assert.equal(ui.dialog().props.modelValue, false);
  await ui.vm.open(ID);
  await settled();
  assert.equal(ui.setup.effectiveReadonly, false);
  assert.equal(ui.writeButton('approve').props.disabled, false);
  assertNoWrites(ui.f);
});

for (const status of ['DRAFT', 'APPROVED', 'ORDERED']) {
  test(`query-only caps / ${status}：真实模板写 handler 零确认零写，刷新仍可读`, async (t) => {
    const f = fixture({ detail: { status }, context: { approveAllowed: false, convertAllowed: false } });
    const ui = mountDialog(t, f);
    await ui.vm.open(ID);
    await settled();
    assert.equal(ui.setup.effectiveReadonly, false, '禁写来自真实控制器权限提示，不依赖只读 props');
    assert.equal(ui.state().phase, 'READY');
    assert.equal(ui.state().canApprove, false);
    assert.equal(ui.state().canConvert, false);
    for (const operation of ['approve', 'convert']) {
      assert.equal(ui.writeButton(operation).props.disabled, true);
      await ui.fire(ui.writeButton(operation));
    }
    assert.equal(ui.button('只读刷新').props.disabled, false);
    await ui.fire(ui.button('只读刷新'));
    await settled();
    assertNoWrites(f);
  });
}

test('实际编译模板连接 approve/convert 的 v-hasPermi 与无写权限指令的只读刷新', async (t) => {
  const ui = mountDialog(t);
  await ui.vm.open(ID);
  await settled();
  const approve = ui.writeButton('approve');
  const convert = ui.writeButton('convert');
  const refresh = ui.button('只读刷新');
  assert.equal(textOf(approve).trim(), '批准原申请');
  assert.equal(textOf(convert).trim(), '转为未定价订单');
  assert.deepEqual(approve.permissions, ['pu:requisition:approve']);
  assert.deepEqual(convert.permissions, ['pu:requisition:convert']);
  assert.deepEqual(refresh.permissions, [], '只读刷新不挂 approve/convert/edit 的写权限');
  assert.ok(ui.f.directiveCalls.some((call) => call.node === approve));
  assert.ok(ui.f.directiveCalls.some((call) => call.node === convert));
  assert.ok(ui.f.directiveCalls.every((call) => call.node === approve || call.node === convert));
  const reads = ui.f.calls.length;
  await ui.fire(refresh);
  await settled();
  assert.equal(ui.f.calls.length, reads + 3, '刷新按钮调用真实控制器 refresh');
  assertNoWrites(ui.f);
});

test('正常 DRAFT 人工确认批准→APPROVED，真实 receipt 成功且不会自动 convert', async (t) => {
  const f = fixture();
  f.confirmGate = deferred();
  const ui = mountDialog(t, f);
  await ui.vm.open(ID);
  await settled();
  const operation = ui.fire(ui.writeButton('approve'));
  await until(() => f.confirmations.length === 1 && ui.setup.busy, '实际批准人工确认');
  assert.equal(f.writes.length, 0, '确认完成前不能写');
  assert.equal(f.storage.values.size, 0, '确认完成前不制造意图');
  assert.equal(ui.writeButton('approve').props.loading, true);
  f.confirmGate.resolve();
  await operation;
  await settled();
  assert.equal(ui.state().phase, 'SUCCEEDED');
  assert.equal(ui.state().document.status, 'APPROVED');
  assert.equal(ui.state().document.version, 6);
  assert.deepEqual(ui.state().result, workflowResult(f.documents.get(ID), 'approve', false));
  assert.deepEqual(
    f.writes.map(({ operation, id, version }) => ({ operation, id, version })),
    [{ operation: 'approve', id: ID, version: 5 }]
  );
  assert.equal(f.writes[0].session.token, TOKEN_A, '真实控制器传递固定凭证 session');
  assert.equal(typeof f.writes[0].session.assertCurrent, 'function');
  assert.deepEqual(f.changed, [[]], '已确认成功才发一次 changed，无虚构结果参数');
  assert.equal(f.storage.values.size, 0, '只有完整成功路径才能释放本窗口持有的锁');
  // 成功可清理原意图及本次持有的 release guard，不把内部清理次数当业务契约。
  assert.ok(f.storage.removed.includes(f.storage.writes[0].key), '完整成功必须清理自己的原业务意图');
  assert.ok(
    f.storage.removed.every((key) => f.storage.writes.some((entry) => entry.key === key)),
    '成功不能清理当前实例未持有的 Storage 键'
  );
  assert.equal(ui.writeButton('approve').props.disabled, true);
  assert.equal(ui.writeButton('convert').props.disabled, false);
  await ui.fire(ui.button('只读刷新'));
  await settled();
  assert.equal(f.writes.length, 1, '批准后手动刷新也不自动推进转单');
  assert.equal(f.confirmations.length, 1);
  assertSafeUi(ui);
});

for (const status of ['APPROVED', 'ORDERED']) {
  test(`${status} convert 需明确人工 confirm，确认前零写，确认后原版本/原订单 receipt`, async (t) => {
    const version = status === 'APPROVED' ? 6 : 7;
    const f = fixture({ detail: { status, version } });
    f.confirmGate = deferred();
    const ui = mountDialog(t, f);
    await ui.vm.open(ID);
    await settled();
    assert.equal(ui.state().canConvert, true);
    const operation = ui.fire(ui.writeButton('convert'));
    await until(() => f.confirmations.length === 1 && ui.setup.busy, '实际转单人工确认');
    assert.equal(f.writes.length, 0);
    assert.equal(f.storage.values.size, 0);
    const confirm = f.confirmations[0];
    assert.equal(confirm.title, '确认认证生命周期操作');
    assert.equal(confirm.settings.cancelButtonText, '取消');
    assert.ok(confirm.message.includes(status === 'ORDERED' ? '回查已转单的原订单' : '未定价 DRAFT 订单'));
    f.confirmGate.resolve();
    await operation;
    await settled();
    assert.equal(ui.state().phase, 'SUCCEEDED');
    assert.equal(ui.state().document.status, 'ORDERED');
    assert.equal(ui.state().document.version, status === 'ORDERED' ? version : version + 1);
    assert.deepEqual(ui.state().result, workflowResult(f.documents.get(ID), 'convert', status === 'ORDERED'));
    assert.equal(ui.state().result.orderId, ORDER_ID);
    assert.deepEqual(
      f.writes.map(({ operation, id, version }) => ({ operation, id, version })),
      [{ operation: 'convert', id: ID, version }]
    );
    assert.equal(f.confirmations.length, 1);
    assert.deepEqual(f.changed, [[]]);
    assert.equal(f.storage.values.size, 0);
    assert.equal(textOf(ui.writeButton('convert')).trim(), '核验既有订单');
    assertSafeUi(ui);
  });
}

for (const [status, operation] of [
  ['DRAFT', 'approve'],
  ['APPROVED', 'convert'],
  ['ORDERED', 'convert']
]) {
  test(`${status} ${operation} 人工 cancel：零业务写/零锁/零 changed`, async (t) => {
    const f = fixture({ detail: { status } });
    f.confirmMode = 'cancel';
    const ui = mountDialog(t, f);
    await ui.vm.open(ID);
    await settled();
    await ui.fire(ui.writeButton(operation));
    await settled();
    assert.equal(f.confirmations.length, 1);
    assert.deepEqual(f.writes, []);
    assert.deepEqual(f.storage.writes, []);
    assert.deepEqual(f.storage.removed, []);
    assert.deepEqual(f.changed, []);
    assert.deepEqual(operations(f), ['context', 'detail', 'context'], 'cancel 不触发控制器写前复读');
    assert.equal(ui.state().phase, 'READY');
    assert.match(ui.setup.localMessage, /取消/);
    assertSafeUi(ui);
  });
}

test('确认尚未完成时 props 变为 readOnly，迟到 confirm 不可触发实际控制器写入', async (t) => {
  const f = fixture({ detail: { status: 'APPROVED', version: 6 } });
  f.confirmGate = deferred();
  const ui = mountDialog(t, f);
  await ui.vm.open(ID);
  await settled();
  const operation = ui.fire(ui.writeButton('convert'));
  await until(() => f.confirmations.length === 1, '在途人工确认');
  ui.liveProps.readOnly = true;
  await settled();
  assert.equal(ui.setup.effectiveReadonly, true);
  f.confirmGate.resolve();
  await operation;
  await settled();
  assert.deepEqual(f.writes, []);
  assert.deepEqual(f.storage.writes, []);
  assert.deepEqual(f.changed, []);
  assert.equal(ui.state().phase, 'READY');
  assert.equal(ui.writeButton('convert').props.disabled, true);
});

for (const outcome of ['http-503', 'envelope-500', 'malformed-200']) {
  for (const [status, operation, version] of [
    ['DRAFT', 'approve', 5],
    ['APPROVED', 'convert', 6],
    ['ORDERED', 'convert', 7]
  ]) {
    test(`${status} ${operation} / ${outcome}：unknown 锁存，关闭重开/刷新/focus/换版本不补发`, async (t) => {
      const f = fixture({ detail: { status, version } });
      f.writeOutcome = outcome;
      const ui = mountDialog(t, f);
      await ui.vm.open(ID);
      await settled();
      await ui.fire(ui.writeButton(operation));
      await settled();
      assert.equal(f.writes.length, 1);
      assert.equal(ui.state().phase, 'UNKNOWN');
      assert.equal(ui.state().unknownLocked, true);
      assert.equal(ui.state().result, null);
      assert.deepEqual(f.changed, []);
      const saved = lockEntry(f);
      assert.deepEqual(JSON.parse(saved.value), {
        schema: 1,
        tenantId: '000001',
        userId: '101',
        requisitionId: ID,
        operation,
        expectedVersion: version,
        state: 'PENDING'
      });
      assert.equal(saved.key, `nexus%3Apu%3Acertified-workflow:000001:101:${ID}`, '原主体/文档级锁不按版本或操作分键');
      const callsAfterUnknown = f.calls.length;
      // 模拟授权人员独立查询到更高版本；不能据此让原未知意图自动重发。
      const newer = { ...f.documents.get(ID), status: operation === 'approve' ? 'APPROVED' : 'ORDERED', version: version + 4 };
      f.documents.set(ID, newer);
      await ui.fire(ui.button('只读刷新'));
      await settled();
      assert.equal(ui.state().document.version, newer.version);
      assert.equal(ui.state().phase, 'UNKNOWN');
      ui.fire(ui.button('关闭'));
      await settled();
      assert.equal(ui.dialog().props.modelValue, false);
      assertLockRetained(f, saved);
      await ui.vm.open(ID);
      await settled();
      assert.equal(ui.state().phase, 'UNKNOWN');
      const reads = f.calls.length;
      f.dispatch('focus');
      await until(() => !ui.setup.busy && f.calls.length === reads + 3, '未知结果下 focus 仅只读刷新');
      for (const forced of ['approve', 'convert']) {
        assert.equal(ui.writeButton(forced).props.disabled, true);
        await ui.fire(ui.writeButton(forced));
      }
      await settled();
      assert.equal(f.writes.length, 1, '原未知请求不能补发，也不能变成另一操作');
      assert.equal(f.confirmations.length, 1);
      assert.ok(f.calls.slice(callsAfterUnknown).every((call) => ['context', 'detail'].includes(call.operation)));
      assert.equal(f.storage.writes.length, 1, '只读刷新与重开不得改写锁');
      assertLockRetained(f, saved);
      assertSafeUi(ui);
    });
  }
}

test('未知锁跨真实组件卸载/重新挂载保留，API port 恢复正常也不自动重试', async (t) => {
  const f = fixture();
  f.writeOutcome = 'http-503';
  const old = mountDialog(t, f);
  await old.vm.open(ID);
  await settled();
  await old.fire(old.writeButton('approve'));
  const saved = lockEntry(f);
  old.unmount();
  f.writeOutcome = 'success';
  const current = mountDialog(t, f);
  await current.vm.open(ID);
  await settled();
  assert.equal(current.state().phase, 'UNKNOWN');
  await current.fire(current.writeButton('approve'));
  await current.fire(current.writeButton('convert'));
  await current.fire(current.button('只读刷新'));
  await settled();
  assert.equal(f.writes.length, 1);
  assert.equal(f.confirmations.length, 1);
  assertLockRetained(f, saved);
});

test('业务写在途：实际关闭/before-close/新 open/刷新/双击都被 busy 拒绝，不伪造强制关闭', async (t) => {
  const f = fixture();
  f.writeGate = deferred();
  const ui = mountDialog(t, f);
  await ui.vm.open(ID);
  await settled();
  const operation = ui.fire(ui.writeButton('approve'));
  await until(() => f.writes.length === 1 && ui.state().phase === 'SENDING', '批准写请求在途');
  const saved = lockEntry(f);
  const calls = f.calls.length;
  await ui.fire(ui.writeButton('approve'));
  await ui.fire(ui.writeButton('convert'));
  await ui.fire(ui.button('只读刷新'));
  ui.fire(ui.button('关闭'));
  let closed = false;
  ui.dialog().props['before-close'](() => {
    closed = true;
  });
  await ui.vm.open(OTHER_ID);
  f.dispatch('focus');
  await settled();
  assert.equal(closed, false);
  assert.equal(ui.dialog().props.modelValue, true);
  assert.equal(ui.state().document.requisitionId, ID);
  assert.equal(ui.state().phase, 'SENDING');
  assert.equal(f.calls.length, calls);
  assert.equal(f.writes.length, 1);
  assert.equal(f.confirmations.length, 1);
  assertLockRetained(f, saved);
  f.writeGate.resolve();
  await operation;
  await settled();
  assert.equal(ui.state().phase, 'SUCCEEDED');
  assert.equal(ui.state().document.requisitionId, ID, '迟到结果仍只属于未允许关闭的原面板');
  assert.equal(f.storage.values.size, 0, '未失效作用域的完整成功可以释放自己的锁');
});

for (const boundary of ['same-credential', 'new-tenant-user-token']) {
  for (const [status, operation, version] of [
    ['DRAFT', 'approve', 5],
    ['APPROVED', 'convert', 6]
  ]) {
    test(`卸载在途 ${operation} / ${boundary}：迟到成功不污染新 panel、不发 changed、原锁保留`, async (t) => {
      const f = fixture({ detail: { status, version } });
      f.writeGate = deferred();
      const old = mountDialog(t, f);
      await old.vm.open(ID);
      await settled();
      const pending = old.fire(old.writeButton(operation));
      await until(() => f.writes.length === 1 && old.state().phase === 'SENDING', '原作用域提交在途');
      const saved = lockEntry(f);
      old.unmount();
      assert.equal(old.controller.snapshot().phase, 'IDLE');
      assert.equal(old.controller.snapshot().document, null);
      assert.equal(old.controller.snapshot().unknownLocked, true);
      assert.equal(f.listeners.get('storage').size, 0, '实际卸载钩子清理监听');
      assert.equal(f.listeners.get('focus').size, 0);
      assertLockRetained(f, saved);
      if (boundary === 'new-tenant-user-token') {
        f.token = TOKEN_B;
        f.context = context({ tenantId: '000002', userId: '202' });
      }
      const current = mountDialog(t, f);
      await current.vm.open(OTHER_ID);
      await settled();
      const newPanel = copy(current.state());
      assert.equal(newPanel.phase, 'READY');
      assert.equal(newPanel.document.requisitionId, OTHER_ID);
      assert.equal(newPanel.unknownLocked, false, '原主体/原单据锁不能污染另一面板的只读事实');
      assert.equal(newPanel.context.tenantId, boundary === 'same-credential' ? '000001' : '000002');
      f.writeGate.resolve();
      await pending;
      await settled();
      assert.deepEqual(current.state(), newPanel, '旧 controller 迟到响应不得替换当前快照');
      assert.equal(current.setup.localMessage, '');
      assert.deepEqual(f.changed, [], '已卸载作用域不得向父页面发成功事件');
      assert.equal(f.writes.length, 1);
      assertLockRetained(f, saved);
      current.fire(current.button('关闭'));
      if (boundary === 'new-tenant-user-token') {
        f.token = TOKEN_A;
        f.context = context();
      }
      await current.vm.open(ID);
      await settled();
      assert.equal(current.state().phase, 'UNKNOWN', '即使 fixture 服务端已成功，原意图仍需独立人工核对');
      await current.fire(current.writeButton('approve'));
      await current.fire(current.writeButton('convert'));
      assert.equal(f.writes.length, 1);
      assertLockRetained(f, saved);
      assertSafeUi(current);
    });
  }
}

test('共享 credential storage 事件使在途 scope 失效，迟到写结果不能恢复显示或删除原锁', async (t) => {
  const f = fixture();
  f.writeGate = deferred();
  const ui = mountDialog(t, f);
  await ui.vm.open(ID);
  await settled();
  const pending = ui.fire(ui.writeButton('approve'));
  await until(() => f.writes.length === 1 && ui.state().phase === 'SENDING', '在途 credential 边界');
  const saved = lockEntry(f);
  f.token = TOKEN_B;
  f.dispatch('storage');
  await settled();
  assert.equal(ui.state().phase, 'BLOCKED');
  assert.equal(ui.state().document, null);
  assert.equal(ui.state().context, null);
  assert.equal(ui.state().unknownLocked, true);
  f.writeGate.resolve();
  await pending;
  await settled();
  assert.equal(ui.state().phase, 'BLOCKED');
  assert.equal(ui.state().result, null);
  assert.deepEqual(f.changed, []);
  assertLockRetained(f, saved);
  assertSafeUi(ui);
});

test('卸载在途人工 confirm：迟到确认不写、不造锁、不影响新只读 panel', async (t) => {
  const f = fixture();
  const gate = deferred();
  f.confirmGate = gate;
  const old = mountDialog(t, f);
  await old.vm.open(ID);
  await settled();
  const pending = old.fire(old.writeButton('approve'));
  await until(() => f.confirmations.length === 1 && old.setup.busy, '卸载前人工确认在途');
  assert.equal(f.writes.length, 0);
  old.unmount();
  f.confirmGate = null;
  const current = mountDialog(t, f);
  await current.vm.open(ID, { readOnly: true });
  await settled();
  const panel = copy(current.state());
  gate.resolve();
  await pending;
  await settled();
  assert.deepEqual(current.state(), panel);
  assert.equal(current.setup.effectiveReadonly, true);
  assert.equal(current.setup.localMessage, '');
  assert.deepEqual(f.writes, []);
  assert.deepEqual(f.storage.writes, []);
  assert.deepEqual(f.storage.removed, []);
  assert.deepEqual(f.changed, []);
  await current.fire(current.writeButton('approve'));
  await current.fire(current.writeButton('convert'));
  assert.equal(f.confirmations.length, 1);
});

test('卸载在途只读 detail：旧读取迟到不污染新 panel，不虚构写意图锁', async (t) => {
  const f = fixture();
  const gate = deferred();
  f.detailGate = gate;
  const old = mountDialog(t, f);
  const pending = old.vm.open(ID);
  await until(() => operations(f).includes('detail') && old.setup.busy, '旧详情读取在途');
  old.unmount();
  f.detailGate = null;
  const current = mountDialog(t, f);
  await current.vm.open(OTHER_ID);
  await settled();
  const panel = copy(current.state());
  gate.resolve();
  await pending;
  await settled();
  assert.deepEqual(current.state(), panel);
  assert.equal(current.state().document.requisitionId, OTHER_ID);
  assertNoWrites(f);
});

test('Storage 读取拒绝：实际组件失败封闭，强制 handler 仍零业务写、不外显原异常', async (t) => {
  const f = fixture();
  f.storage.getItem = () => {
    throw new Error(RAW_ERROR);
  };
  const ui = mountDialog(t, f);
  await ui.vm.open(ID);
  await settled();
  assert.equal(ui.state().phase, 'BLOCKED');
  assert.equal(ui.state().unknownLocked, true);
  for (const operation of ['approve', 'convert']) {
    assert.equal(ui.writeButton(operation).props.disabled, true);
    await ui.fire(ui.writeButton(operation));
  }
  await ui.fire(ui.button('只读刷新'));
  await settled();
  assertNoWrites(f);
  assertSafeUi(ui);
});
