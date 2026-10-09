/** 真实 Vue SFC/script/template 隔离挂载；不调用真实 API、DB 或浏览器服务。 */
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync, existsSync } from 'node:fs';
import * as sfc from '@vue/compiler-sfc';
import * as Vue from 'vue';
import ts from 'typescript';
const url = new URL('../src/views/erp/wm/certified/CertifiedWarehouseWorkflowDialog.vue', import.meta.url);
function evaluate(source, modules) {
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const exports = {};
  new Function('exports', 'require', code)(exports, (name) => {
    assert.ok(Object.hasOwn(modules, name), `未隔离依赖 ${name}`);
    return modules[name];
  });
  return exports;
}
function host() {
  return Vue.createRenderer({
    createElement: (tag) => ({ tag, children: [], props: {}, parent: null }),
    createText: (text) => ({ text, children: [], parent: null }),
    createComment: (text) => ({ text, children: [], parent: null }),
    setText: (n, text) => (n.text = text),
    setElementText(n, text) {
      n.children = [];
      n.text = text;
    },
    parentNode: (n) => n?.parent ?? null,
    nextSibling(n) {
      const siblings = n?.parent?.children ?? [];
      return siblings[siblings.indexOf(n) + 1] ?? null;
    },
    insert(n, parent, anchor = null) {
      if (n.parent) n.parent.children = n.parent.children.filter((x) => x !== n);
      n.parent = parent;
      const index = parent.children.indexOf(anchor);
      if (index < 0) parent.children.push(n);
      else parent.children.splice(index, 0, n);
    },
    remove(n) {
      if (n.parent) n.parent.children = n.parent.children.filter((x) => x !== n);
      n.parent = null;
    },
    patchProp: (n, k, p, v) => (n.props[k] = v)
  });
}
function mount(options = {}) {
  const source = readFileSync(url, 'utf8');
  const { descriptor, errors } = sfc.parse(source);
  assert.deepEqual(errors, []);
  const script = sfc.compileScript(descriptor, { id: 'warehouse-dialog' });
  const template = sfc.compileTemplate({
    source: descriptor.template.content,
    filename: url.pathname,
    id: 'warehouse-dialog',
    compilerOptions: { bindingMetadata: script.bindings }
  });
  assert.deepEqual(template.errors, []);
  const log = [];
  const user = Vue.reactive({ userId: '101', tenantId: '000001' });
  const route = Vue.reactive({ fullPath: `/erp/wm/${options.properties?.domain ?? 'arrivalnotice'}` });
  let token = 'offline';
  let controller = {
    state: {
      kind: 'ORDER',
      documentId: '',
      detail: null,
      context: null,
      busy: false,
      reading: false,
      blocked: false,
      message: '',
      result: null,
      recovery: null
    },
    setTarget(kind, value) {
      log.push(['target', kind, value]);
      this.state.kind = kind;
      this.state.documentId = value;
    },
    inspect() {
      log.push('inspect');
    },
    perform(op, input) {
      log.push(['perform', op, structuredClone(input)]);
    },
    recover() {
      log.push('GET recovery');
    },
    available: () => true,
    invalidate() {
      log.push('invalidate');
    }
  };
  const helper = options.realWorkflow
    ? evaluate(readFileSync(new URL('../src/utils/certified-business.ts', import.meta.url), 'utf8'), {
        '@/utils/request': { default: { defaults: {} } }
      })
    : null;
  const realWorkflow = options.realWorkflow
    ? evaluate(readFileSync(new URL('../src/views/erp/wm/certified/warehouseWorkflow.ts', import.meta.url), 'utf8'), {
        '@/utils/certified-business': helper
      })
    : null;
  let ports;
  const modules = {
    vue: Vue,
    'vue-router': { useRoute: () => route },
    '@/utils/auth': { getToken: () => token },
    '@/store/modules/user': { useUserStore: () => user },
    '@/plugins/auth': { default: { hasPermi: () => true } },
    'element-plus': { ElMessageBox: { confirm: options.confirm ?? (async () => true) } },
    './warehouseWorkflow': {
      createWarehouseWorkflow: (p) => {
        ports = p;
        if (realWorkflow) controller = realWorkflow.createWarehouseWorkflow(p);
        return controller;
      }
    },
    '@/api/erp/wm/arrivalnotice': options.arrivalApi ?? {},
    '@/api/erp/wm/receive': options.receiveApi ?? {}
  };
  const component = evaluate(script.content, modules).default;
  component.render = evaluate(template.code, { vue: Vue }).render;
  const root = { children: [] };
  const keep = Vue.ref(true);
  const dialogRef = Vue.ref();
  const properties = Vue.reactive({
    modelValue: true,
    domain: 'arrivalnotice',
    initialId: '9007199254740993',
    initialKind: 'ORDER',
    ...options.properties
  });
  const fallback = { render: () => Vue.h('div') };
  const parent = {
    render: () =>
      Vue.h(Vue.KeepAlive, null, {
        default: () =>
          keep.value
            ? Vue.h(component, {
                ...properties,
                ref: dialogRef,
                'onUpdate:modelValue': (value) => {
                  if (!options.ignoreClose) properties.modelValue = value;
                }
              })
            : Vue.h(fallback)
      })
  };
  const app = options.keepAlive ? host().createApp(parent) : host().createApp(component, properties);
  const teleport = { tag: 'teleport-target', children: [] };
  const stub = {
    setup(props, { slots }) {
      return () => Vue.h('section', null, slots.default?.());
    }
  };
  for (const tag of [
    'ElAlert',
    'ElForm',
    'ElFormItem',
    'ElInput',
    'ElButton',
    'ElCheckbox',
    'ElRadioGroup',
    'ElRadioButton',
    'ElDescriptions',
    'ElDescriptionsItem'
  ])
    app.component(tag, stub);
  if (!options.keepAlive) app.component('ElDialog', stub);
  if (options.keepAlive)
    app.component('ElDialog', {
      props: { modelValue: Boolean },
      setup(props, { slots }) {
        return () => Vue.h(Vue.Teleport, { to: teleport }, props.modelValue ? [Vue.h('section', null, slots.default?.())] : []);
      }
    });
  app.config.warnHandler = () => {};
  const previousWindow = Object.getOwnPropertyDescriptor(globalThis, 'window');
  if (options.boundary) Object.defineProperty(globalThis, 'window', { configurable: true, writable: true, value: options.boundary.window });
  const unmount = app.unmount.bind(app);
  app.unmount = () => {
    try {
      unmount();
    } finally {
      if (options.boundary) {
        if (previousWindow) Object.defineProperty(globalThis, 'window', previousWindow);
        else delete globalThis.window;
      }
    }
  };
  const vm = app.mount(root);
  const instance = options.keepAlive ? dialogRef.value.$ : vm.$;
  const setup = instance.setupState;
  return { setup, ports, log, route, user, app, controller, keep, properties, teleport, instance, dialogRef, helper, setToken: (v) => (token = v) };
}
test('独立真实仓储 SFC 必须存在', () => assert.ok(existsSync(url), '缺少认证仓储对话框'));
if (existsSync(url)) {
  test('真实 setup 不默认数量、日期、IQC、批次；人工写 handler 委托一个独立 operation', async () => {
    const f = mount();
    assert.equal(f.setup.input.quantity, '');
    assert.equal(f.setup.input.arrivalDate, '');
    assert.equal(f.setup.input.actualArrivalDate, '');
    assert.equal(f.setup.input.materialsConfirmed, false);
    f.setup.input.quantity = '2.5000';
    await f.setup.perform('createNotice');
    assert.equal(f.log.filter((x) => Array.isArray(x) && x[0] === 'perform').length, 1);
    assert.equal(f.log.at(-1)[2].quantity, '2.5000');
    assert.equal(f.log.at(-1)[2].materialsConfirmed, false);
    f.app.unmount();
  });
  test('真实恢复 handler 只委托 GET；watch 路由/账号/卸载均 invalidate', async () => {
    const f = mount();
    await f.setup.recover();
    assert.ok(f.log.includes('GET recovery'));
    f.route.fullPath = '/other';
    await Vue.nextTick();
    f.user.tenantId = '000002';
    await Vue.nextTick();
    f.app.unmount();
    assert.ok(f.log.filter((x) => x === 'invalidate').length >= 3);
    assert.equal(
      f.log.some((x) => Array.isArray(x) && x[0] === 'perform'),
      false
    );
  });
  test('scope 捕获完整用户/租户/路由，对象选择不对 ID 做 Number 精度截断', async () => {
    const f = mount();
    f.setup.targetId = '9007199254740995';
    f.setup.targetKind = 'NOTICE';
    await f.setup.inspect();
    assert.deepEqual(f.log.at(-2), ['target', 'NOTICE', '9007199254740995']);
    assert.equal(f.ports.readScope().tenantId, '000001');
    assert.equal(f.ports.readScope().userId, '101');
    f.app.unmount();
  });
}

if (existsSync(url))
  test('真实 KeepAlive + Teleport：停用关闭、不重取 session、不在异页再 open，重激活不自动重开', async (t) => {
    const f = mount({ keepAlive: true });
    t.after(() => f.app.unmount());
    await Vue.nextTick();
    const uid = f.instance.uid;
    f.log.length = 0;
    f.keep.value = false;
    await Vue.nextTick();
    assert.equal(f.instance.isDeactivated, true);
    assert.equal(f.properties.modelValue, false);
    assert.ok(f.log.includes('invalidate'));
    const before = f.log.length;
    await f.setup.inspect();
    await f.setup.perform('createNotice');
    await f.setup.recover();
    assert.equal(f.log.length, before);
    f.route.fullPath = '/unrelated-page';
    f.properties.modelValue = true;
    await Vue.nextTick();
    assert.equal(f.properties.modelValue, false);
    assert.equal(
      f.teleport.children.some((n) => n.tag === 'section'),
      false
    );
    f.route.fullPath = '/erp/wm/arrivalnotice';
    f.keep.value = true;
    await Vue.nextTick();
    assert.equal(f.dialogRef.value.$.uid, uid);
    assert.equal(f.properties.modelValue, false);
    assert.equal(f.setup.active, true);
    f.properties.modelValue = true;
    await Vue.nextTick();
    await f.setup.inspect();
    assert.equal(f.log.at(-1), 'inspect');
    f.app.unmount();
  });

/** 浏览器事件/持久存储的离线边界；Vue KeepAlive 与 Teleport 仍为真实运行时。 */
function browserBoundary() {
  const values = new Map();
  const listeners = new Map();
  const storage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key)
  };
  return {
    values,
    storage,
    window: {
      localStorage: storage,
      addEventListener(name, listener) {
        if (!listeners.has(name)) listeners.set(name, new Set());
        listeners.get(name).add(listener);
      },
      removeEventListener: (name, listener) => listeners.get(name)?.delete(listener)
    },
    count: (name) => listeners.get(name)?.size ?? 0,
    dispatch(name) {
      for (const listener of [...(listeners.get(name) ?? [])]) listener({ type: name });
    }
  };
}
async function settle() {
  for (let i = 0; i < 8; i++) {
    await Vue.nextTick();
    await new Promise((done) => setImmediate(done));
  }
}
function nativeOrder(domain = 'arrivalnotice', kind = 'ORDER') {
  return {
    domain,
    kind,
    documentId: '9007199254740993',
    protection: 'CERTIFIED',
    sourceComplete: true,
    flowState: 'NOT_APPLICABLE',
    order: {
      tenantId: '000001',
      orderId: '9007199254740993',
      status: 'APPROVED',
      version: 2,
      termsId: '9007199254740995',
      selectFlowId: null,
      delFlag: '0'
    },
    orderLines: [
      {
        tenantId: '000001',
        lineId: '9007199254740995',
        orderId: '9007199254740993',
        materialId: '3',
        baseUomId: '4',
        orderQuantity: '12.0000',
        delFlag: '0'
      }
    ],
    native: null,
    lines: [],
    details: []
  };
}
function manualNoticeInput() {
  return {
    requestKey: 'offline-recovery-key',
    purchaseLineId: '9007199254740995',
    quantity: '2.0000',
    noticeName: '人工真实通知',
    arrivalDate: '2026-10-09',
    materialsConfirmed: true
  };
}
function offlineArrival(calls) {
  return {
    getCertifiedArrivalContext: async () => {
      calls.push('GET context');
      return { code: 200, data: { tenantId: '000001', userId: '101', domain: 'arrivalnotice', createAllowed: true, databaseDate: '2026-10-09' } };
    },
    getCertifiedArrivalDetail: async () => {
      calls.push('GET detail');
      return { code: 200, data: nativeOrder() };
    },
    createCertifiedArrival: async () => {
      calls.push('POST createNotice');
      return { code: 500 };
    }
  };
}

if (existsSync(url)) {
  test('父级未响应 close 的真实 Teleport 仍立即隐藏；缓存重激活不沿用旧 true 或自动读取', async (t) => {
    const f = mount({ keepAlive: true, ignoreClose: true });
    t.after(() => f.app.unmount());
    await Vue.nextTick();
    assert.equal(
      f.teleport.children.some((n) => n.tag === 'section'),
      true
    );
    f.keep.value = false;
    await Vue.nextTick();
    assert.equal(f.properties.modelValue, true, '父级故意保留旧可见值');
    assert.equal(
      f.teleport.children.some((n) => n.tag === 'section'),
      false
    );
    const before = f.log.length;
    await f.setup.inspect();
    await f.setup.perform('createNotice');
    await f.setup.recover();
    assert.equal(f.log.length, before, '缓存异页不调用任何 workflow 入口');
    f.keep.value = true;
    await Vue.nextTick();
    assert.equal(
      f.teleport.children.some((n) => n.tag === 'section'),
      false
    );
    const reactivated = f.log.length;
    await f.setup.inspect();
    await f.setup.perform('createNotice');
    await f.setup.recover();
    assert.equal(f.log.length, reactivated, '旧 true 不授予重新打开资格');
    f.properties.modelValue = false;
    await Vue.nextTick();
    f.properties.modelValue = true;
    await Vue.nextTick();
    await f.setup.inspect();
    assert.equal(f.log.at(-1), 'inspect', '仅所属页人工 false→true 后可重新读取');
  });

  for (const boundary of ['route', 'user', 'tenant', 'domain'])
    test(`${boundary} scope 改变立即关闭；异页或旧 domain 不可取得新 session`, async (t) => {
      const f = mount({ keepAlive: true });
      t.after(() => f.app.unmount());
      await Vue.nextTick();
      f.setup.input.materialsConfirmed = true;
      if (boundary === 'route') f.route.fullPath = '/erp/wm/arrivalnotice?other=1';
      if (boundary === 'user') f.user.userId = '102';
      if (boundary === 'tenant') f.user.tenantId = '000002';
      if (boundary === 'domain') f.properties.domain = 'receive';
      await Vue.nextTick();
      assert.equal(f.properties.modelValue, false);
      assert.equal(f.setup.input.materialsConfirmed, false);
      assert.equal(
        f.teleport.children.some((n) => n.tag === 'section'),
        false
      );
      const before = f.log.length;
      await f.setup.inspect();
      await f.setup.perform('createNotice');
      await f.setup.recover();
      assert.equal(f.log.length, before);
      assert.throws(() => f.ports.readScope(), '已关闭 scope 不可被重新捕获');
      if (boundary === 'route' || boundary === 'domain') {
        if (boundary === 'route') f.route.fullPath = '/unrelated-page';
        f.properties.modelValue = true;
        await Vue.nextTick();
        assert.equal(f.properties.modelValue, false);
        assert.equal(
          f.teleport.children.some((n) => n.tag === 'section'),
          false
        );
      }
    });

  test('storage/focus 只撤销内存审阅；监听停用/异页卸下，重激活不重复挂载或自动 GET', async (t) => {
    const boundary = browserBoundary();
    const f = mount({ keepAlive: true, boundary });
    t.after(() => f.app.unmount());
    await Vue.nextTick();
    for (const event of ['storage', 'focus']) assert.equal(boundary.count(event), 1);
    f.setup.input.materialsConfirmed = true;
    const before = f.log.length;
    boundary.dispatch('focus');
    assert.equal(f.setup.input.materialsConfirmed, false);
    assert.deepEqual(f.log.slice(before), ['invalidate']);
    f.setToken('changed-offline-token');
    boundary.dispatch('storage');
    await Vue.nextTick();
    assert.equal(f.properties.modelValue, false, '非响应式 localStorage token 变化也封闭旧 scope');
    assert.throws(() => f.ports.readScope());
    f.keep.value = false;
    await Vue.nextTick();
    for (const event of ['storage', 'focus']) assert.equal(boundary.count(event), 0);
    const inactive = f.log.length;
    boundary.dispatch('focus');
    boundary.dispatch('storage');
    assert.equal(f.log.length, inactive);
    f.keep.value = true;
    await Vue.nextTick();
    for (const event of ['storage', 'focus']) assert.equal(boundary.count(event), 1);
    assert.equal(f.properties.modelValue, false);
    assert.equal(
      f.log.some((x) => x === 'inspect'),
      false
    );
    f.route.fullPath = '/other';
    await Vue.nextTick();
    for (const event of ['storage', 'focus']) assert.equal(boundary.count(event), 0);
    f.app.unmount();
    for (const event of ['storage', 'focus']) assert.equal(boundary.count(event), 0);
  });

  test('真实 SFC+workflow+helper：确认悬停时停用，迟到确认不得 POST 或重取 session', async (t) => {
    const boundary = browserBoundary();
    const calls = [];
    let accept;
    const f = mount({
      keepAlive: true,
      boundary,
      realWorkflow: true,
      arrivalApi: offlineArrival(calls),
      confirm: () =>
        new Promise((done) => {
          accept = done;
        })
    });
    t.after(() => f.app.unmount());
    assert.deepEqual(calls, [], '打开 Dialog 不自动读取或写入');
    await f.setup.inspect();
    assert.equal(f.controller.state.detail.order.version, 2);
    assert.equal(f.setup.input.quantity, '', '原生订购量不复制为实际到货量');
    Object.assign(f.setup.input, manualNoticeInput());
    const pending = f.setup.perform('createNotice');
    await settle();
    assert.equal(typeof accept, 'function');
    const before = calls.length;
    f.keep.value = false;
    await Vue.nextTick();
    accept();
    await pending;
    assert.equal(calls.length, before, '失效后没有 fresh GET/session 或 POST');
    assert.equal(
      calls.some((x) => x.startsWith('POST')),
      false
    );
    assert.equal(f.properties.modelValue, false);
    assert.equal(boundary.values.size, 0, '派发前失效不生成持久写意图');
    await f.setup.inspect();
    await f.setup.perform('createNotice');
    await f.setup.recover();
    assert.equal(calls.length, before);
  });

  test('真实 helper UNKNOWN：停用/监听/重激活/人工只读刷新均不 clear 或换键重发', async (t) => {
    const boundary = browserBoundary();
    const calls = [];
    const f = mount({ keepAlive: true, boundary, realWorkflow: true, arrivalApi: offlineArrival(calls) });
    t.after(() => f.app.unmount());
    await f.setup.inspect();
    Object.assign(f.setup.input, manualNoticeInput());
    await f.setup.perform('createNotice');
    assert.equal(calls.filter((x) => x.startsWith('POST')).length, 1);
    assert.equal(f.controller.state.blocked, true);
    const persisted = [...boundary.values];
    assert.ok(persisted.length > 0);
    boundary.dispatch('storage');
    f.keep.value = false;
    await Vue.nextTick();
    f.keep.value = true;
    await Vue.nextTick();
    assert.equal(f.properties.modelValue, false);
    assert.deepEqual([...boundary.values], persisted);
    f.properties.modelValue = true;
    await Vue.nextTick();
    await f.setup.inspect();
    assert.equal(f.controller.state.blocked, true);
    Object.assign(f.setup.input, manualNoticeInput(), { requestKey: 'another-offline-key' });
    await f.setup.perform('createNotice');
    assert.equal(calls.filter((x) => x.startsWith('POST')).length, 1);
    assert.deepEqual([...boundary.values], persisted);
  });

  test('真实 receive GET 永久恢复：缓存停用不恢复，人工重开只 GET，UNKNOWN 字节保持不变', async (t) => {
    const boundary = browserBoundary();
    const calls = [];
    const f = mount({
      keepAlive: true,
      boundary,
      realWorkflow: true,
      properties: { domain: 'receive', initialKind: 'RECEIVE' },
      receiveApi: {
        getCertifiedReceiveContext: async () => {
          calls.push('GET context');
          return { code: 200, data: { domain: 'receive', tenantId: '000001', userId: '101' } };
        },
        getCertifiedReceiveDetail: async () => {
          calls.push('GET detail');
          return { code: 200, data: nativeOrder('receive', 'RECEIVE') };
        },
        recoverCertifiedReceive: async () => {
          calls.push('GET recovery');
          return {
            code: 200,
            data: {
              receiveId: '9007199254740993',
              orderId: '9007199254740993',
              materialId: '3',
              quantity: '2.0000',
              sourceFingerprint: 'a'.repeat(64),
              postedAt: '2026-10-09 10:00:00',
              notificationStatus: 'PENDING',
              consumptionState: 'PENDING',
              consumptionEvents: []
            }
          };
        }
      }
    });
    t.after(() => f.app.unmount());
    f.helper
      .createBusinessIntentStore(boundary.storage)
      .begin(
        { tenantId: '000001', userId: '101', feature: 'wm:receive:RECEIVE', documentId: '9007199254740993' },
        'executeReceive',
        'offline-existing-pending'
      );
    const persisted = [...boundary.values];
    await f.setup.inspect();
    assert.equal(f.controller.state.blocked, true);
    f.keep.value = false;
    await Vue.nextTick();
    const before = calls.length;
    await f.setup.recover();
    assert.equal(calls.length, before);
    f.keep.value = true;
    await Vue.nextTick();
    assert.equal(calls.length, before);
    f.properties.modelValue = true;
    await Vue.nextTick();
    await f.setup.recover();
    assert.equal(calls.at(-1), 'GET recovery');
    assert.equal(f.controller.state.recovery.notificationStatus, 'PENDING');
    assert.equal(f.controller.state.recovery.consumptionState, 'PENDING');
    assert.equal(
      calls.every((x) => x.startsWith('GET')),
      true,
      '不存在 POST/MQ 补发端口调用'
    );
    assert.deepEqual([...boundary.values], persisted);
  });
}

if (existsSync(url)) {
  test('真实 SFC+helper：停用后迟到 context GET 不继续取 detail、不覆盖新页面', async (t) => {
    const boundary = browserBoundary();
    const calls = [];
    let contextAck;
    const api = offlineArrival(calls);
    api.getCertifiedArrivalContext = () => {
      calls.push('GET context');
      return new Promise((done) => {
        contextAck = done;
      });
    };
    const f = mount({ keepAlive: true, boundary, realWorkflow: true, arrivalApi: api });
    t.after(() => f.app.unmount());
    const reading = f.setup.inspect();
    assert.equal(typeof contextAck, 'function');
    f.keep.value = false;
    await Vue.nextTick();
    f.route.fullPath = '/other';
    await Vue.nextTick();
    contextAck({ code: 200, data: { tenantId: '000001', userId: '101', domain: 'arrivalnotice', createAllowed: true } });
    await reading;
    assert.deepEqual(calls, ['GET context']);
    assert.equal(f.controller.state.context, null);
    assert.equal(f.controller.state.detail, null);
    assert.equal(
      f.teleport.children.some((n) => n.tag === 'section'),
      false
    );
    assert.equal(boundary.values.size, 0);
  });

  test('真实 SFC+helper：POST 已派发时强制停用，迟到成功不 readback/release；重开仍 UNKNOWN', async (t) => {
    const boundary = browserBoundary();
    const calls = [];
    let postAck;
    const api = offlineArrival(calls);
    api.createCertifiedArrival = () => {
      calls.push('POST createNotice');
      return new Promise((done) => {
        postAck = done;
      });
    };
    const f = mount({ keepAlive: true, boundary, realWorkflow: true, arrivalApi: api });
    t.after(() => f.app.unmount());
    await f.setup.inspect();
    Object.assign(f.setup.input, manualNoticeInput());
    const posting = f.setup.perform('createNotice');
    await settle();
    assert.equal(typeof postAck, 'function');
    assert.equal(f.controller.state.busy, true);
    const persisted = [...boundary.values];
    const before = calls.length;
    assert.ok(persisted.length > 0);
    f.keep.value = false;
    await Vue.nextTick();
    assert.equal(f.properties.modelValue, false, '生命周期封闭不受普通关闭 busy 限制');
    postAck({
      code: 200,
      data: { noticeId: '9007199254740993', noticeCode: 'offline', noticeLineId: '9007199254740995', status: 'DRAFT', replayed: false }
    });
    await posting;
    assert.equal(calls.length, before);
    assert.equal(f.controller.state.result, null);
    assert.equal(f.controller.state.blocked, true);
    assert.deepEqual([...boundary.values], persisted);
    f.keep.value = true;
    await Vue.nextTick();
    assert.equal(calls.length, before, '重激活不自动捕获 session 或 GET');
    f.properties.modelValue = true;
    await Vue.nextTick();
    await f.setup.inspect();
    assert.equal(f.controller.state.blocked, true);
    Object.assign(f.setup.input, manualNoticeInput(), { requestKey: 'offline-new-key' });
    await f.setup.perform('createNotice');
    assert.equal(calls.filter((x) => x.startsWith('POST')).length, 1);
    assert.deepEqual([...boundary.values], persisted);
  });
}
