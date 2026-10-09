import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import * as sfc from '@vue/compiler-sfc';
import * as Vue from 'vue';
import { createRequire } from 'node:module';
// 产品 SFC 离线 bundle 为 CJS；真实 router 同入口加载，避免 ESM/CJS 重复 injection Symbol。
const { createMemoryHistory, createRouter } = createRequire(import.meta.url)('vue-router');
import {
  paths,
  O,
  OTHER_O,
  Q,
  Q2,
  ENTERED,
  CONFIRM_ID,
  TOKEN,
  TOKEN_B,
  TERMS,
  copy,
  deferred,
  memoryStorage,
  loadOrderProduction,
  offlineRequest
} from './test-certified-order-api.mjs';

const FLAGS = ['termsAddAllowed', 'termsConfirmAllowed', 'approveAllowed', 'closeAllowed', 'cancelAllowed'];
const ORDER = 'ORDER_COMMITTED_NOT_TIMELY_SUPPLY';
let sequence = 0,
  production;
const hash = 'a'.repeat(64);
function quote(orderId = O, patch = {}) {
  return {
    quoteId: Q,
    orderId,
    quoteVersion: 1,
    enteredBy: ENTERED,
    requestKey: 'synthetic-original-quote',
    quoteHash: hash,
    quantity: '123.0000',
    ...TERMS,
    confirmationId: null,
    confirmedBy: null,
    confirmationHash: null,
    reviewNote: null,
    ...patch
  };
}
/** 完整原生 OrderHead/QuoteView/AllocationView；不是旧 VO 或 allocations:[] 假成功。 */
function document(orderId = O, quotes = []) {
  return {
    order: {
      tenantId: '000001',
      orderId,
      orderNo: orderId === O ? 'PO-SYNTHETIC-A' : 'PO-SYNTHETIC-B',
      requisitionId: '9007199254741201',
      vendorId: '9007199254741211',
      status: 'DRAFT',
      version: 0,
      quoteId: null,
      selectFlowId: null,
      requisitionSourceType: 'AI_DECISION',
      decisionRunId: '9007199254741221',
      decisionActionId: '9007199254741231',
      sourceRequestKey: 'synthetic-original-source',
      originalCommandPresent: true
    },
    quotes,
    allocations: [
      {
        allocationId: '9007199254741301',
        requisitionLineId: '9007199254741311',
        orderLineId: '9007199254741321',
        allocationStatus: ORDER,
        version: 2
      },
      {
        allocationId: '9007199254741331',
        requisitionLineId: '9007199254741341',
        orderLineId: '9007199254741351',
        allocationStatus: ORDER,
        version: 4
      }
    ],
    priceVisible: true,
    serverDate: '2026-10-09',
    genericEditAllowedHint: false
  };
}
function quoteReceipt(q, d) {
  const result = {
    ...copy(q),
    tenantId: d.order.tenantId,
    requestHash: 'c'.repeat(64),
    enteredAt: '2026-10-09 09:00:00',
    vendorId: d.order.vendorId,
    materialId: '9007199254741401',
    baseUomId: '9007199254741411',
    sourceRequestKey: d.order.sourceRequestKey,
    sourceFingerprint: 'd'.repeat(64),
    catalogFingerprint: 'e'.repeat(64),
    orderFingerprint: 'f'.repeat(64),
    roundingPolicy: 'HALF_UP_2'
  };
  for (const key of ['confirmationId', 'confirmedBy', 'confirmationHash', 'reviewNote']) delete result[key];
  return result;
}
function host(tag, text = '') {
  return { tag, text, props: {}, children: [], parent: null };
}
/** 使用真实 Vue lifecycle/reactivity/render；Element Plus 只替换宿主与人工确认弹窗边界。 */
function renderer() {
  return Vue.createRenderer({
    createElement: (tag) => host(tag),
    createText: (text) => host('#text', text),
    createComment: (text) => host('#comment', text),
    setText: (n, text) => {
      n.text = text;
    },
    setElementText: (n, text) => {
      n.text = text;
      n.children = [];
    },
    patchProp: (n, key, _old, value) => {
      n.props[key] = value;
    },
    parentNode: (n) => n.parent,
    nextSibling: (n) => n.parent?.children[n.parent.children.indexOf(n) + 1] ?? null,
    insert: (n, p, anchor = null) => {
      if (n.parent) n.parent.children.splice(n.parent.children.indexOf(n), 1);
      const index = anchor ? p.children.indexOf(anchor) : -1;
      p.children.splice(index < 0 ? p.children.length : index, 0, n);
      n.parent = p;
    },
    remove: (n) => {
      if (n.parent) n.parent.children.splice(n.parent.children.indexOf(n), 1);
      n.parent = null;
    }
  });
}
const all = (n) => [n, ...n.children.flatMap(all)];
const text = (n) => n.text + (typeof n.props.title === 'string' ? n.props.title : '') + n.children.map(text).join('');
const button = (root, label) => all(root).find((n) => n.tag === 'el-button' && text(n).trim() === label);
const LABEL = { terms: '保存不可变报价版本', confirm: '独立确认原报价', approve: '批准原订单', close: '关闭原订单', cancel: '取消原订单' };
async function flush() {
  for (let n = 0; n < 4; n++) {
    await new Promise((r) => setTimeout(r, 0));
    await Vue.nextTick();
  }
}

async function compiledProduction() {
  if (production) return production;
  const { descriptor, errors } = sfc.parse(readFileSync(paths.component, 'utf8'), { filename: paths.component });
  assert.deepEqual(errors, [], '真实 SFC parse bootstrap');
  const script = sfc.compileScript(descriptor, { id: 'order-sidecar', genDefaultAs: '__OrderDialog' });
  const template = sfc.compileTemplate({
    source: descriptor.template.content,
    filename: paths.component,
    id: 'order-sidecar',
    compilerOptions: { bindingMetadata: script.bindings }
  });
  assert.deepEqual(template.errors, [], '真实 SFC template bootstrap');
  const normalized = (p) => p.replaceAll('\\', '/');
  production = await loadOrderProduction(
    `${script.content}\n${template.code}\n__OrderDialog.render = render;
    export { __OrderDialog as component };
    export * as api from '${normalized(paths.api)}'; export * as helper from '${normalized(paths.helper)}';
    export * as controller from '${normalized(paths.controller)}';`.replaceAll('\\n', '\n')
  );
  return production;
}

async function mount(options = {}) {
  const modules = await compiledProduction();
  const userId = String(9007199254770000n + BigInt(++sequence));
  const f = {
    credential: Vue.ref(TOKEN),
    user: Vue.reactive({ token: TOKEN, tenantId: '000001', userId }),
    ctx: {
      tenantId: '000001',
      userId,
      termsAddAllowed: true,
      termsConfirmAllowed: true,
      approveAllowed: true,
      closeAllowed: true,
      cancelAllowed: true,
      priceVisible: true
    },
    rows: new Map([
      [O, document()],
      [OTHER_O, document(OTHER_O)]
    ]),
    calls: [],
    posts: [],
    confirms: [],
    changed: [],
    listeners: new Map(),
    storage: options.storage ?? memoryStorage(),
    outcome: 'success',
    ...options
  };
  if (options.quotes) f.rows.get(O).quotes = copy(options.quotes);
  if (options.confirmed)
    Object.assign(f.rows.get(O).quotes[0], {
      confirmationId: CONFIRM_ID,
      confirmedBy: '9007199254741051',
      confirmationHash: hash,
      reviewNote: '原生独立复核记录'
    });
  if (options.context) Object.assign(f.ctx, options.context);
  if (!f.ctx.priceVisible)
    for (const d of f.rows.values()) {
      d.priceVisible = false;
      d.quotes = [];
    }
  if (options.head) Object.assign(f.rows.get(O).order, options.head);
  f.request = offlineRequest(f, async (call) => {
    if (call.method === 'get') {
      if (call.url.endsWith('/workflow-context')) {
        const captured = copy(f.ctx);
        if (f.contextGate) await f.contextGate.promise;
        return { data: { code: 200, data: captured } };
      }
      const id = call.url.split('/').at(-2),
        captured = copy(f.rows.get(id));
      if (f.readGate) await f.readGate.promise;
      return { data: { code: 200, data: captured } };
    }
    const path = call.url.split('/'),
      operation = path.at(-1),
      id = path[5],
      body = call.body;
    assert.ok(['terms', 'confirm', 'approve', 'close', 'cancel'].includes(operation), '不得进入旧普通/收货/Flow API');
    f.posts.push({ operation, id, body: copy(body), quoteId: operation === 'confirm' ? path.at(-2) : body.quoteId });
    if (f.postGate) await f.postGate.promise;
    if (f.outcome === 'network') throw new Error('离线原始错误 ' + TOKEN);
    if (f.outcome === 'http500') return { status: 500, data: { code: 403, msg: TOKEN } };
    if (f.outcome === 'r500') return { data: { code: 500, msg: TOKEN } };
    if (f.outcome === 'refused') return { status: 403, data: { code: 403, msg: TOKEN } };
    if (f.outcome === 'malformed') return { data: { code: 200, data: {} } };
    const d = f.rows.get(id),
      before = copy(d);
    let result;
    if (operation === 'terms') {
      const q = quote(id, {
        ...body,
        quoteId: d.quotes.length ? Q2 : Q,
        quoteVersion: d.quotes.length + 1,
        enteredBy: f.ctx.userId,
        requestKey: body.requestKey,
        quoteHash: hash
      });
      delete q.expectedOrderVersion;
      if (f.nativeDecimalScale) {
        q.unitPriceExcludingTax = '12.3400';
        q.taxRate = '0.1300';
        q.exchangeRate = '1.00000000';
      }
      d.quotes.push(q);
      result = { quote: quoteReceipt(q, d), confirmation: null, replayed: false };
    } else if (operation === 'confirm') {
      const q = d.quotes.find((q) => q.quoteId === path.at(-2));
      Object.assign(q, { confirmationId: CONFIRM_ID, confirmedBy: f.ctx.userId, confirmationHash: q.quoteHash, reviewNote: body.reviewNote });
      result = {
        quote: quoteReceipt(q, d),
        confirmation: {
          confirmationId: CONFIRM_ID,
          tenantId: f.ctx.tenantId,
          quoteId: q.quoteId,
          quoteHash: q.quoteHash,
          actorId: f.ctx.userId,
          requestKey: body.requestKey,
          requestHash: 'c'.repeat(64),
          permissionCode: 'pu:order:terms:confirm',
          confirmedAt: '2026-10-09 09:05:00',
          reviewNote: body.reviewNote
        },
        replayed: false
      };
    } else {
      d.order.version++;
      d.order.status = { approve: 'APPROVED', close: 'CLOSED', cancel: 'CANCELLED' }[operation];
      if (operation === 'approve') d.order.quoteId = body.quoteId;
      for (const a of d.allocations) {
        a.version++;
        if (operation !== 'approve') a.allocationStatus = 'RELEASED';
      }
      result = {
        orderId: id,
        orderNo: d.order.orderNo,
        requestKey: body.requestKey,
        status: d.order.status,
        version: d.order.version,
        quoteId: d.order.quoteId,
        quoteHash: hash,
        expectedArrivalDate: '2026-10-15',
        totalAmount: '1715.00',
        amountExcludingTax: '1517.70',
        taxAmount: '197.30',
        replayed: false,
        allocations: d.allocations.map((a, i) => ({
          eventId: String(9007199254750001n + BigInt(i)),
          allocationId: a.allocationId,
          requisitionLineId: a.requisitionLineId,
          orderLineId: a.orderLineId,
          actorId: f.ctx.userId,
          operation: { approve: 'APPROVE_ORDER', close: 'CLOSE_ORDER', cancel: 'CANCEL_ORDER' }[operation],
          fromStatus: before.allocations[i].allocationStatus,
          toStatus: a.allocationStatus,
          replayed: false
        }))
      };
    }
    if (f.afterPost) f.afterPost(d, result, f, before);
    return { data: { code: 200, data: result } };
  });
  f.confirm = async (...args) => {
    f.confirms.push(args);
    if (f.modalGate) return f.modalGate.promise;
    if (f.cancelConfirmation) throw new Error('合成人工取消');
    return 'confirm';
  };
  globalThis.__orderSidecarEnv = f;
  const previousWindow = globalThis.window;
  globalThis.window = {
    localStorage: f.storage,
    addEventListener: (name, fn) => f.listeners.set(name, fn),
    removeEventListener: (name) => f.listeners.delete(name)
  };
  const props = Vue.reactive({ readOnly: options.readOnly === true }),
    exposed = Vue.ref(),
    root = host('root'),
    overlay = host('overlay');
  const pageActive = Vue.ref(true);
  let instance;
  const orderDialog = () =>
    Vue.h(modules.component, {
      readOnly: props.readOnly,
      ref: exposed,
      onChanged: () => f.changed.push('changed'),
      onVnodeMounted: (vnode) => {
        instance = vnode.component;
      }
    });
  const Page = Vue.defineComponent({ name: 'OrderOwner', setup: () => orderDialog });
  const Other = Vue.defineComponent({ name: 'OtherOwner', setup: () => () => Vue.h('other-owner') });
  const app = renderer().createApp({
    render: () =>
      options.keepAlive
        ? Vue.h(Vue.KeepAlive, null, { default: () => (pageActive.value ? Vue.h(Page, { key: 'order' }) : Vue.h(Other, { key: 'other' })) })
        : orderDialog()
  });
  // 真实 memory router/useRoute；缓存切换和路由变更可以分别驱动，不手工调用产品生命周期。
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/order', component: Page },
      { path: '/elsewhere', component: Other }
    ]
  });
  app.use(router);
  await router.push('/order');
  await router.isReady();
  for (const name of [
    'el-dialog',
    'el-alert',
    'el-descriptions',
    'el-descriptions-item',
    'el-tabs',
    'el-tab-pane',
    'el-form',
    'el-form-item',
    'el-row',
    'el-col',
    'el-input',
    'el-date-picker',
    'el-checkbox',
    'el-button',
    'el-table',
    'el-table-column',
    'el-select',
    'el-option'
  ]) {
    app.component(
      name,
      Vue.defineComponent({
        inheritAttrs: false,
        setup: (_, ctx) => () => {
          if (name === 'el-dialog' && !(ctx.attrs.modelValue ?? ctx.attrs['model-value'])) return null;
          const content = Vue.h(name, { ...ctx.attrs }, [...(ctx.slots.default?.() ?? []), ...(ctx.slots.footer?.() ?? [])]);
          return name === 'el-dialog' && options.teleport ? Vue.h(Vue.Teleport, { to: overlay }, content) : content;
        }
      })
    );
  }
  app.mount(root);
  const openedDialog = exposed.value;
  const surface = options.teleport ? overlay : root;
  const bindings = instance.setupState; // 仅测试读取编译器返回的 setup 绑定；产品仍只 expose open。
  const m = {
    f,
    props,
    exposed,
    bindings,
    instance,
    root,
    overlay,
    surface,
    app,
    modules,
    router,
    pageActive,
    navigate: async (path) => {
      await router.push(path);
      await Vue.nextTick();
    },
    deactivate: async () => {
      pageActive.value = false;
      await Vue.nextTick();
    },
    activate: async () => {
      pageActive.value = true;
      await Vue.nextTick();
    },
    open: async (id = O, settings = {}) => {
      await openedDialog.open(id, settings);
      await Vue.nextTick();
    },
    fill: async (patch = {}) => {
      Object.assign(bindings.terms, copy(TERMS), patch);
      bindings.materialReviewed = true;
      await Vue.nextTick();
    },
    select: async (id = Q) => {
      bindings.selectedQuoteId = id;
      bindings.reviewNote = '合成独立完整资料复核';
      await Vue.nextTick();
    },
    click: async (op) => {
      const n = button(surface, LABEL[op]);
      assert.ok(n, `缺少真实 ${op} 按钮`);
      return n.props.onClick();
    },
    phase: () => bindings.state.phase,
    blocked: () =>
      modules.helper.createBusinessIntentStore(f.storage).isBlocked({ tenantId: '000001', userId, feature: 'certified-order', documentId: O }),
    cleanup: () => {
      app.unmount();
      globalThis.window = previousWindow;
      delete globalThis.__orderSidecarEnv;
    }
  };
  return m;
}
const run = (name, options, fn) =>
  test(name, async () => {
    const m = await mount(options);
    try {
      await m.open();
      await fn(m);
    } finally {
      m.cleanup();
    }
  });

run('bootstrap：真实 SFC/Vue/controller/helper/API 挂载，产品只 expose open，读取新原生 Detail', {}, async (m) => {
  assert.deepEqual(Object.keys(m.instance.exposed), ['open']);
  assert.equal(m.phase(), 'READY');
  assert.equal(m.f.posts.length, 0);
  assert.equal(m.bindings.state.detail.order.orderId, O);
  assert.equal(m.bindings.state.detail.allocations.length, 2);
  assert.equal(m.f.calls[0].url, '/erp/pu/order/certified/workflow-context');
});
run(
  'query-only 可打开详情、价格不可见提示；直接调用所有真实按钮仍为 0 POST',
  { context: Object.fromEntries([...FLAGS.map((k) => [k, false]), ['priceVisible', false]]) },
  async (m) => {
    assert.ok(text(m.root).includes('没有报价价格可见权限'));
    await m.fill();
    for (const op of FLAGS.map(
      (k) =>
        ({ termsAddAllowed: 'terms', termsConfirmAllowed: 'confirm', approveAllowed: 'approve', closeAllowed: 'close', cancelAllowed: 'cancel' })[k]
    ))
      await m.click(op);
    assert.equal(m.f.posts.length, 0);
    assert.equal(m.f.confirms.length, 0);
  }
);
for (const mode of ['prop', 'open'])
  run(`只读 ${mode} 不能借 view/直接 handler 升级为写`, { readOnly: mode === 'prop' }, async (m) => {
    if (mode === 'open') await m.open(O, { readOnly: true });
    await m.fill();
    for (const op of ['terms', 'confirm', 'approve', 'close', 'cancel']) await m.click(op);
    assert.equal(m.f.posts.length, 0);
    assert.ok(text(m.root).includes('只读查看'));
  });
run('空报价、人工字段与 review checkbox 默认 false，不补 price/ETA/证据，不默认选报价', {}, async (m) => {
  assert.equal(m.bindings.materialReviewed, false);
  assert.equal(m.bindings.selectedQuoteId, '');
  for (const key of ['unitPriceExcludingTax', 'taxRate', 'promisedArrivalDate', 'validFrom', 'validUntil', 'evidenceReference', 'evidenceSha256'])
    assert.equal(m.bindings.terms[key], '');
  await m.click('terms');
  assert.equal(m.f.posts.length, 0);
  assert.equal(m.f.confirms.length, 0);
  await m.fill();
  m.bindings.materialReviewed = false;
  await m.click('terms');
  assert.equal(m.f.posts.length, 0);
});
run('原生已有报价不填人工表单；必须单独选择，不自动批准', { quotes: [quote()] }, async (m) => {
  assert.equal(m.bindings.terms.unitPriceExcludingTax, '');
  assert.equal(m.bindings.terms.promisedArrivalDate, '');
  assert.equal(m.bindings.selectedQuoteId, '');
  await m.click('confirm');
  await m.click('approve');
  assert.equal(m.f.posts.length, 0);
});
for (const [op, flag] of [
  ['terms', 'termsAddAllowed'],
  ['confirm', 'termsConfirmAllowed'],
  ['approve', 'approveAllowed'],
  ['close', 'closeAllowed'],
  ['cancel', 'cancelAllowed']
]) {
  run(`独立权限 ${flag} 缺失不借其他权限派发 ${op}`, { quotes: [quote()], confirmed: op === 'approve', context: { [flag]: false } }, async (m) => {
    await m.fill();
    await m.select();
    await m.click(op);
    assert.equal(m.f.posts.length, 0);
    assert.equal(m.f.confirms.length, 0);
  });
}
run('人工报价录入只提交一次真实 BO，回读一致后只 changed，不自动 confirm/approve/到货', {}, async (m) => {
  await m.fill();
  await m.click('terms');
  assert.equal(m.phase(), 'SUCCEEDED');
  assert.equal(m.f.posts.length, 1);
  assert.equal(m.f.posts[0].operation, 'terms');
  assert.equal(m.f.posts[0].body.unitPriceExcludingTax, TERMS.unitPriceExcludingTax);
  assert.equal(m.f.posts[0].body.expectedOrderVersion, 0);
  assert.equal(m.bindings.materialReviewed, false);
  assert.deepEqual(m.f.changed, ['changed']);
});
run('不同 actor 独立确认报价，quoteId 只在 URL，回读后不自动批准', { quotes: [quote()] }, async (m) => {
  await m.select();
  await m.click('confirm');
  assert.equal(m.phase(), 'SUCCEEDED');
  assert.equal(m.f.posts.length, 1);
  assert.equal(m.f.posts[0].quoteId, Q);
  assert.deepEqual(Object.keys(m.f.posts[0].body).sort(), ['quoteHash', 'requestKey', 'reviewNote']);
  assert.equal(m.bindings.state.detail.quotes[0].confirmedBy, m.f.ctx.userId);
  assert.equal(m.bindings.state.detail.order.status, 'DRAFT');
});
run('同 actor 不允许自审，直接事件 handler 也不派发', { quotes: [quote()] }, async (m) => {
  m.f.rows.get(O).quotes[0].enteredBy = m.f.ctx.userId;
  await m.open();
  await m.select();
  await m.click('confirm');
  assert.equal(m.f.posts.length, 0);
});
run('已有独立确认的原报价可单独批准，完整逐来源回读，不自动下一步', { quotes: [quote()], confirmed: true }, async (m) => {
  await m.select();
  await m.click('approve');
  assert.equal(m.phase(), 'SUCCEEDED');
  assert.equal(m.f.posts.length, 1);
  assert.equal(m.bindings.state.detail.order.status, 'APPROVED');
  assert.equal(m.bindings.state.detail.order.quoteId, Q);
  assert.equal(m.f.posts[0].operation, 'approve');
  assert.equal(m.bindings.state.detail.allocations.length, 2);
});
for (const op of ['close', 'cancel'])
  run(`原单 ${op} 是独立 POST，逐来源结果不移植`, {}, async (m) => {
    await m.click(op);
    assert.equal(m.phase(), 'SUCCEEDED');
    assert.equal(m.f.posts.length, 1);
    assert.deepEqual(Object.keys(m.f.posts[0].body).sort(), ['expectedVersion', 'requestKey']);
  });
for (const op of ['terms', 'confirm', 'approve', 'close', 'cancel'])
  run(`人工取消 ${op} 确认不生成意图/POST，不自动再发`, { quotes: [quote()], confirmed: op === 'approve' }, async (m) => {
    await m.fill();
    await m.select();
    m.f.cancelConfirmation = true;
    await m.click(op);
    assert.equal(m.f.posts.length, 0);
    assert.equal(m.blocked(), false);
    assert.deepEqual(m.f.changed, []);
  });
for (const version of [null, '0'])
  run(`原生版本 ${String(version)} 仅查看，不能默认整数0执行`, { head: { version } }, async (m) => {
    assert.equal(m.phase(), 'BLOCKED');
    if (version === null) assert.ok(text(m.root).includes('NULL（禁止猜为0）'));
    await m.fill();
    for (const op of ['terms', 'confirm', 'approve', 'close', 'cancel']) await m.click(op);
    assert.equal(m.f.posts.length, 0);
  });
for (const boundary of ['token', 'tenant', 'user', 'readOnly', 'unmount'])
  run(`确认弹窗等待期间 ${boundary} 切换不提交旧/新对象`, {}, async (m) => {
    await m.fill();
    m.f.modalGate = deferred();
    const writing = m.click('terms');
    await Vue.nextTick();
    if (boundary === 'token') {
      m.f.credential.value = TOKEN_B;
      m.f.user.token = TOKEN_B;
    }
    if (boundary === 'tenant') {
      m.f.ctx.tenantId = '000009';
      m.f.user.tenantId = '000009';
    }
    if (boundary === 'user') {
      m.f.ctx.userId = ENTERED;
      m.f.user.userId = ENTERED;
    }
    if (boundary === 'readOnly') m.props.readOnly = true;
    if (boundary === 'unmount') m.app.unmount();
    await Vue.nextTick();
    m.f.modalGate.resolve('confirm');
    await writing;
    assert.equal(m.f.posts.length, 0);
    assert.deepEqual(m.f.changed, []);
  });
run('确认期间调用 open 其它单不得把原请求发送到其它单，busy scope 不迁移', {}, async (m) => {
  await m.fill();
  m.f.modalGate = deferred();
  const writing = m.click('terms');
  await m.open(OTHER_O);
  m.f.modalGate.resolve('confirm');
  await writing;
  assert.ok(m.f.posts.every((p) => p.id === O));
  assert.equal(m.f.rows.get(OTHER_O).quotes.length, 0);
});
for (const field of ['unitPriceExcludingTax', 'promisedArrivalDate', 'evidenceReference', 'materialReviewed'])
  run(`确认期间人工字段 ${field} 改变必须中止原确认`, {}, async (m) => {
    await m.fill();
    m.f.modalGate = deferred();
    const writing = m.click('terms');
    await Vue.nextTick();
    if (field === 'materialReviewed') m.bindings.materialReviewed = false;
    else
      m.bindings.terms[field] = { unitPriceExcludingTax: '15.0000', promisedArrivalDate: '2026-10-20', evidenceReference: '另一份合成材料' }[field];
    m.f.modalGate.resolve('confirm');
    await writing;
    assert.equal(m.f.posts.length, 0, '确认窗口不能沿用已改变的人工资料或声明');
  });
run(
  '确认期间 selectedQuote 切换必须中止，不能移植先前/后来报价的确认',
  { quotes: [quote(), quote(O, { quoteId: Q2, quoteVersion: 2, quoteHash: 'd'.repeat(64) })] },
  async (m) => {
    await m.select(Q2);
    m.f.modalGate = deferred();
    const writing = m.click('confirm');
    await Vue.nextTick();
    m.bindings.selectedQuoteId = Q;
    m.f.modalGate.resolve('confirm');
    await writing;
    assert.equal(m.f.posts.length, 0);
  }
);
run('双击独立操作只出现一次实际 POST、一次 changed，不重建意图', {}, async (m) => {
  await m.fill();
  m.f.postGate = deferred();
  const first = m.click('terms');
  const second = m.click('terms');
  await flush();
  assert.equal(m.f.posts.length, 1);
  assert.equal(m.f.confirms.length, 1);
  assert.equal(m.blocked(), true);
  m.f.postGate.resolve();
  await Promise.all([first, second]);
  assert.equal(m.f.posts.length, 1);
  assert.equal(m.f.changed.length, 1);
});
for (const outcome of ['network', 'http500', 'r500', 'malformed'])
  run(`UNKNOWN ${outcome} 刷新/重开/新版本不再派发`, {}, async (m) => {
    await m.fill();
    m.f.outcome = outcome;
    await m.click('terms');
    assert.equal(m.phase(), 'UNKNOWN');
    assert.equal(m.blocked(), true);
    assert.deepEqual(m.f.changed, []);
    m.f.rows.get(O).order.version = 2;
    await button(m.root, '只读刷新／核对').props.onClick();
    await m.open();
    await m.fill();
    await m.click('terms');
    assert.equal(m.f.posts.length, 1);
    assert.equal(m.phase(), 'UNKNOWN');
    assert.ok(!text(m.root).includes(TOKEN));
  });
run('明确 4xx 拒绝不自动重发，仅提示且释放本次意图', {}, async (m) => {
  await m.fill();
  m.f.outcome = 'refused';
  await m.click('terms');
  assert.equal(m.phase(), 'REFUSED');
  assert.equal(m.f.posts.length, 1);
  assert.equal(m.blocked(), false);
  assert.deepEqual(m.f.changed, []);
  assert.ok(!text(m.root).includes(TOKEN));
});
for (const field of ['tenantId', 'orderId', 'requestKey', 'enteredBy'])
  run(`错误报价回执 ${field} 必须 UNKNOWN，不能 changed/解锁`, {}, async (m) => {
    await m.fill();
    m.f.afterPost = (_d, result) => {
      result.quote[field] = { tenantId: '000099', orderId: OTHER_O, requestKey: 'wrong-intent', enteredBy: ENTERED }[field];
    };
    await m.click('terms');
    assert.equal(m.phase(), 'UNKNOWN');
    assert.equal(m.blocked(), true);
    assert.deepEqual(m.f.changed, []);
  });
for (const field of ['sourceRequestKey', 'requisitionId', 'decisionRunId', 'originalCommandPresent'])
  run(`POST 后原生订单来源 ${field} 移植必须 UNKNOWN`, {}, async (m) => {
    await m.fill();
    m.f.afterPost = (d) => {
      d.order[field] = { sourceRequestKey: 'foreign-source', requisitionId: OTHER_O, decisionRunId: OTHER_O, originalCommandPresent: false }[field];
    };
    await m.click('terms');
    assert.equal(m.phase(), 'UNKNOWN');
    assert.equal(m.blocked(), true);
    assert.deepEqual(m.f.changed, []);
  });
for (const field of ['allocationId', 'requisitionLineId', 'orderLineId', 'allocationStatus', 'version'])
  run(`报价 POST 后原生 AllocationView ${field} 改变必须 UNKNOWN`, {}, async (m) => {
    await m.fill();
    m.f.afterPost = (d) => {
      d.allocations[0][field] = {
        allocationId: OTHER_O,
        requisitionLineId: OTHER_O,
        orderLineId: OTHER_O,
        allocationStatus: 'RELEASED',
        version: 99
      }[field];
    };
    await m.click('terms');
    assert.equal(m.phase(), 'UNKNOWN', '报价不改来源占用，必须对照原始 AllocationView');
    assert.equal(m.blocked(), true);
    assert.deepEqual(m.f.changed, []);
  });
run('真实 BigDecimal 等价尺度回读不浮点化，未税/税率/汇率原生文本应可对账', {}, async (m) => {
  await m.fill({ unitPriceExcludingTax: '12.34', taxRate: '0.13' });
  m.f.nativeDecimalScale = true;
  await m.click('terms');
  assert.equal(m.phase(), 'SUCCEEDED', 'BigDecimal 12.34/12.3400、0.13/0.1300、1/1.00000000 是同一资料值');
  assert.equal(m.bindings.state.detail.quotes[0].unitPriceExcludingTax, '12.3400');
  assert.equal(m.f.posts.length, 1);
});
run('POST 已派发后卸载，迟到成功不 changed，不释放原未知意图', {}, async (m) => {
  await m.fill();
  m.f.postGate = deferred();
  const writing = m.click('terms');
  await flush();
  assert.equal(m.f.posts.length, 1);
  m.app.unmount();
  m.f.postGate.resolve();
  await writing;
  assert.deepEqual(m.f.changed, []);
  assert.equal(m.blocked(), true);
});

// 原 saved RED 全部保留；扩展人工输入字段和“变化后恢复”也不能沿用先前确认。
for (const field of Object.keys(TERMS).filter((k) => !['unitPriceExcludingTax', 'promisedArrivalDate', 'evidenceReference'].includes(k)))
  run(`确认期间其余人工条款 ${field} 变化必须中止，不换发请求`, {}, async (m) => {
    await m.fill();
    m.f.modalGate = deferred();
    const writing = m.click('terms');
    await Vue.nextTick();
    m.bindings.terms[field] = '';
    m.f.modalGate.resolve('confirm');
    await writing;
    assert.equal(m.f.posts.length, 0);
    assert.equal(m.blocked(), false);
    assert.deepEqual(m.f.changed, []);
  });
for (const field of ['terms', 'materialReviewed', 'selectedQuoteId', 'reviewNote'])
  run(`确认期间 ${field} 变化再恢复仍撤销原确认`, { quotes: [quote()] }, async (m) => {
    await m.fill();
    await m.select();
    m.f.modalGate = deferred();
    const writing = m.click(field === 'terms' || field === 'materialReviewed' ? 'terms' : 'confirm');
    await Vue.nextTick();
    if (field === 'terms') {
      const old = m.bindings.terms.evidenceReference;
      m.bindings.terms.evidenceReference = '变更的合成资料';
      m.bindings.terms.evidenceReference = old;
    } else {
      const old = m.bindings[field];
      m.bindings[field] = field === 'materialReviewed' ? false : '';
      m.bindings[field] = old;
    }
    m.f.modalGate.resolve('confirm');
    await writing;
    assert.equal(m.f.posts.length, 0);
    assert.equal(m.blocked(), false);
  });
run('确认原报价时 reviewNote 变化必须中止，不采纳前后任一份新复核', { quotes: [quote()] }, async (m) => {
  await m.select();
  m.f.modalGate = deferred();
  const writing = m.click('confirm');
  await Vue.nextTick();
  m.bindings.reviewNote = '另一份未经本次确认的复核';
  m.f.modalGate.resolve('confirm');
  await writing;
  assert.equal(m.f.posts.length, 0);
  assert.equal(m.blocked(), false);
});
run('批准 modal 等待时 selectedQuote 改变不批准旧/新报价', { quotes: [quote()], confirmed: true }, async (m) => {
  await m.select();
  m.f.modalGate = deferred();
  const writing = m.click('approve');
  await Vue.nextTick();
  m.bindings.selectedQuoteId = '';
  m.f.modalGate.resolve('confirm');
  await writing;
  assert.equal(m.f.posts.length, 0);
  assert.equal(m.blocked(), false);
});
for (const boundary of ['tenantId', 'userId', 'token'])
  run(`modal 期间仅登录 store ${boundary} 变化也撤销，不等待 query 主体同步`, {}, async (m) => {
    await m.fill();
    m.f.modalGate = deferred();
    const writing = m.click('terms');
    await Vue.nextTick();
    m.f.user[boundary] = { tenantId: '000009', userId: ENTERED, token: TOKEN_B }[boundary];
    await Vue.nextTick();
    m.f.modalGate.resolve('confirm');
    await writing;
    assert.equal(m.f.posts.length, 0);
    assert.equal(m.blocked(), false);
    assert.deepEqual(m.f.changed, []);
  });
for (const field of ['allocationId', 'requisitionLineId', 'orderLineId', 'allocationStatus', 'version'])
  run(`独立确认 POST 后原占用 ${field} 变化必须 UNKNOWN`, { quotes: [quote()] }, async (m) => {
    await m.select();
    m.f.afterPost = (d) => {
      d.allocations[0][field] = {
        allocationId: OTHER_O,
        requisitionLineId: OTHER_O,
        orderLineId: OTHER_O,
        allocationStatus: 'RELEASED',
        version: 99
      }[field];
    };
    await m.click('confirm');
    assert.equal(m.phase(), 'UNKNOWN');
    assert.equal(m.blocked(), true);
    assert.deepEqual(m.f.changed, []);
  });

const cacheRun = (name, fn) => run(name, { keepAlive: true, teleport: true }, fn);
cacheRun('KeepAlive bootstrap：真实缓存页面、Teleport overlay、router 注入，非手工 lifecycle', async (m) => {
  assert.equal(m.instance.parent.type.name, 'OrderOwner');
  assert.ok(button(m.overlay, LABEL.terms));
  assert.equal(button(m.root, LABEL.terms), undefined);
  assert.equal(m.router.currentRoute.value.path, '/order');
  assert.equal(m.f.posts.length, 0);
});
cacheRun('KeepAlive 停用（路由未切换）立即隐藏 Teleport，并拆 storage/focus listeners', async (m) => {
  await m.deactivate();
  assert.equal(m.router.currentRoute.value.path, '/order');
  assert.equal(m.instance.isUnmounted, false, '页面仍缓存而非卸载');
  assert.equal(button(m.overlay, LABEL.terms), undefined);
  assert.equal(m.f.listeners.has('storage'), false);
  assert.equal(m.f.listeners.has('focus'), false);
  assert.equal(m.f.posts.length, 0);
});
cacheRun('所属路由离开立即关闭 Teleport，不从新路由领取写 session', async (m) => {
  await m.fill();
  const staleClick = button(m.overlay, LABEL.terms).props.onClick;
  await m.navigate('/elsewhere');
  await staleClick();
  await m.open(OTHER_O);
  assert.equal(button(m.overlay, LABEL.terms), undefined);
  assert.equal(m.f.listeners.has('storage'), false);
  assert.equal(m.f.listeners.has('focus'), false);
  assert.equal(m.f.posts.length, 0);
  assert.deepEqual(m.f.changed, []);
});
cacheRun('active false 的迟到列表 open 不再 GET；激活不自动重开，需要人工重新 open', async (m) => {
  await m.fill();
  await m.deactivate();
  const before = m.f.calls.length;
  await m.open(OTHER_O);
  await m.navigate('/elsewhere');
  m.f.credential.value = TOKEN_B;
  m.f.user.token = TOKEN_B;
  await m.activate();
  assert.equal(button(m.overlay, LABEL.terms), undefined);
  assert.equal(m.f.calls.length, before);
  assert.equal(m.f.listeners.has('focus'), false);
  await m.navigate('/order');
  assert.equal(button(m.overlay, LABEL.terms), undefined);
  assert.equal(m.f.calls.length, before);
  assert.equal(m.f.listeners.has('focus'), true);
  assert.equal(m.f.listeners.has('storage'), true);
  await m.open();
  assert.ok(button(m.overlay, LABEL.terms));
  assert.equal(m.phase(), 'READY');
  assert.equal(m.f.posts.length, 0);
});
cacheRun('同页 fullPath/query 变化也撤销原 scope，不自动 GET/open', async (m) => {
  await m.fill();
  const oldClick = button(m.overlay, LABEL.terms).props.onClick;
  const before = m.f.calls.length;
  await m.navigate('/order?tab=another');
  await oldClick();
  assert.equal(button(m.overlay, LABEL.terms), undefined);
  assert.equal(m.f.calls.length, before);
  assert.equal(m.f.posts.length, 0);
  await m.open();
  assert.ok(button(m.overlay, LABEL.terms));
});
for (const boundary of ['deactivated', 'route'])
  cacheRun(`modal pending ${boundary} 立即撤销；恢复页面也不执行旧确认`, async (m) => {
    await m.fill();
    m.f.modalGate = deferred();
    const writing = m.click('terms');
    await Vue.nextTick();
    if (boundary === 'deactivated') await m.deactivate();
    else await m.navigate('/elsewhere');
    assert.equal(button(m.overlay, LABEL.terms), undefined);
    if (boundary === 'deactivated') await m.activate();
    else await m.navigate('/order');
    m.f.modalGate.resolve('confirm');
    await writing;
    await flush();
    assert.equal(button(m.overlay, LABEL.terms), undefined);
    assert.equal(m.f.posts.length, 0);
    assert.deepEqual(m.f.changed, []);
    assert.equal(m.blocked(), false);
    await m.open();
    assert.equal(m.phase(), 'READY');
  });
for (const boundary of ['deactivated', 'route', 'tenant', 'user'])
  cacheRun(`delayed preflight ${boundary} 不派发，不生成持久意图`, async (m) => {
    await m.fill();
    m.f.contextGate = deferred();
    const gate = m.f.contextGate;
    const writing = m.click('terms');
    await flush();
    assert.equal(m.f.posts.length, 0);
    if (boundary === 'deactivated') await m.deactivate();
    if (boundary === 'route') await m.navigate('/elsewhere');
    if (boundary === 'tenant') m.f.user.tenantId = '000009';
    if (boundary === 'user') m.f.user.userId = ENTERED;
    await Vue.nextTick();
    m.f.contextGate = null;
    gate.resolve();
    await writing;
    assert.equal(m.f.posts.length, 0);
    assert.deepEqual(m.f.changed, []);
    assert.equal(m.blocked(), false);
    if (boundary === 'deactivated' || boundary === 'route') assert.equal(button(m.overlay, LABEL.terms), undefined);
  });
cacheRun('已派发 POST 停用再激活：迟到成功不得 changed 或释放 UNKNOWN，原单不能重发', async (m) => {
  await m.fill();
  m.f.postGate = deferred();
  const writing = m.click('terms');
  await flush();
  assert.equal(m.f.posts.length, 1);
  const saved = copy([...m.f.storage.values]);
  await m.deactivate();
  await m.activate();
  assert.equal(button(m.overlay, LABEL.terms), undefined);
  assert.equal(m.blocked(), true);
  m.f.postGate.resolve();
  await writing;
  await flush();
  assert.deepEqual(m.f.changed, []);
  assert.equal(m.blocked(), true);
  assert.deepEqual([...m.f.storage.values], saved);
  await m.open();
  await m.fill();
  await m.click('terms');
  assert.equal(m.phase(), 'UNKNOWN');
  assert.equal(m.f.posts.length, 1);
  assert.equal(m.blocked(), true);
});
cacheRun('UNKNOWN 跨路由/停用/激活仍原锁；读回版本变化不解锁', async (m) => {
  await m.fill();
  m.f.outcome = 'network';
  await m.click('terms');
  assert.equal(m.phase(), 'UNKNOWN');
  const saved = copy([...m.f.storage.values]);
  await m.navigate('/elsewhere');
  await m.deactivate();
  m.f.rows.get(O).order.version = 3;
  await m.navigate('/order');
  await m.activate();
  assert.equal(button(m.overlay, LABEL.terms), undefined);
  assert.deepEqual([...m.f.storage.values], saved);
  await m.open();
  await m.fill();
  await m.click('terms');
  assert.equal(m.phase(), 'UNKNOWN');
  assert.equal(m.f.posts.length, 1);
  assert.equal(m.blocked(), true);
  assert.deepEqual([...m.f.storage.values], saved);
  assert.deepEqual(m.f.changed, []);
});
cacheRun('迟到 GET 在缓存停用后不重显 Teleport；重新激活仍须明确 open', async (m) => {
  m.f.readGate = deferred();
  const gate = m.f.readGate;
  const opening = m.open();
  await flush();
  await m.deactivate();
  await m.activate();
  m.f.readGate = null;
  gate.resolve();
  await opening;
  await flush();
  assert.equal(button(m.overlay, LABEL.terms), undefined);
  assert.equal(m.f.posts.length, 0);
  await m.open();
  assert.equal(m.phase(), 'READY');
  assert.ok(button(m.overlay, LABEL.terms));
});
cacheRun('focus pending 不重置用户输入/session；listeners 随 active 管理且卸载归零', async (m) => {
  await m.fill();
  m.f.modalGate = deferred();
  const writing = m.click('terms');
  await Vue.nextTick();
  const before = m.f.calls.length;
  m.f.listeners.get('focus')();
  assert.equal(m.f.calls.length, before);
  assert.deepEqual({ ...m.bindings.terms }, TERMS);
  m.f.modalGate.resolve('confirm');
  await writing;
  assert.equal(m.f.posts.length, 1);
  assert.equal(m.f.changed.length, 1);
  await m.deactivate();
  assert.equal(m.f.listeners.size, 0);
  await m.activate();
  assert.equal(m.f.listeners.size, 2);
  assert.equal(button(m.overlay, LABEL.terms), undefined);
  m.app.unmount();
  assert.equal(m.f.listeners.size, 0);
});

for (const field of ['terms', 'materialReviewed', 'selectedQuoteId', 'reviewNote'])
  run(`确认通过后 delayed preflight ${field} 变化也必须停止原意图，不换发请求`, { quotes: [quote()] }, async (m) => {
    await m.fill();
    await m.select();
    m.f.contextGate = deferred();
    const gate = m.f.contextGate;
    const writing = m.click(field === 'terms' || field === 'materialReviewed' ? 'terms' : 'confirm');
    await flush();
    assert.equal(m.f.posts.length, 0);
    if (field === 'terms') m.bindings.terms.evidenceReference = '确认后另一份合成材料';
    else m.bindings[field] = field === 'materialReviewed' ? false : '';
    m.f.contextGate = null;
    gate.resolve();
    await writing;
    assert.equal(m.f.posts.length, 0, '确认后继续读取并不授权使用变化的资料或报价');
    assert.deepEqual(m.f.changed, []);
    assert.equal(m.blocked(), false);
  });
for (const badId of [Number(O), O + '?other=1'])
  run(`迟到列表传入非法订单 ${String(badId)} 不能留下原单可写`, {}, async (m) => {
    await m.fill();
    const before = m.f.calls.length;
    await m.open(badId);
    assert.equal(m.phase(), 'BLOCKED');
    assert.equal(m.f.calls.length, before);
    assert.equal(m.f.posts.length, 0);
    if (button(m.root, LABEL.terms)) await m.click('terms');
    assert.equal(m.f.posts.length, 0);
    assert.equal(m.blocked(), false);
  });

for (const boundary of ['tenant', 'user', 'route'])
  cacheRun(`实际 Axios serializer/dispatch 边界 ${boundary} 切换不能派发或换账号重发`, async (m) => {
    await m.fill();
    let serialized = false;
    m.f.request.defaults.transformRequest = [
      ...m.f.request.defaults.transformRequest,
      function (body) {
        if (this.method !== 'post') return body;
        serialized = true;
        assert.equal(m.blocked(), true, '变换的是有持久意图的实际 POST，不是 preflight GET');
        if (boundary === 'tenant') m.f.user.tenantId = '000009';
        if (boundary === 'user') m.f.user.userId = ENTERED;
        // 真实 router 的 reactive currentRoute 是 useRoute 的来源；同步边界不 mock 产品 useRoute。
        if (boundary === 'route')
          m.router.currentRoute.value = {
            ...m.router.currentRoute.value,
            path: '/elsewhere',
            fullPath: '/elsewhere',
            query: {},
            params: {},
            hash: ''
          };
        return body;
      }
    ];
    await m.click('terms');
    assert.equal(serialized, true);
    assert.equal(m.f.posts.length, 0);
    assert.deepEqual(m.f.changed, []);
    assert.equal(m.blocked(), true, '持久意图已生成，不能因 dispatch 撤销自动清锁');
    assert.equal(m.phase(), 'UNKNOWN');
  });

// P2 lifecycle readback: actual controller/helper/API/renderer must keep the original intent on bad native versions.
for (const operation of ['approve', 'close', 'cancel']) {
  for (const corruption of ['null', 'missing', 'negative', 'string', 'noninteger', 'wrongincrement'])
    run(
      `${operation} mounted lifecycle allocation version ${corruption} -> UNKNOWN，0 changed、持久锁`,
      { quotes: [quote()], confirmed: operation === 'approve' },
      async (m) => {
        await m.select();
        const beforeVersion = m.f.rows.get(O).allocations[1].version;
        m.f.afterPost = (d) => {
          const row = d.allocations[1];
          if (corruption === 'missing') delete row.version;
          else
            row.version = {
              null: null,
              negative: -1,
              string: String(beforeVersion + 1),
              noninteger: beforeVersion + 1.5,
              wrongincrement: beforeVersion + 2
            }[corruption];
        };
        await m.click(operation);
        assert.equal(m.f.posts.length, 1);
        assert.equal(m.phase(), 'UNKNOWN');
        assert.equal(m.blocked(), true);
        assert.deepEqual(m.f.changed, []);
        const saved = copy([...m.f.storage.values]);
        await button(m.root, '只读刷新／核对').props.onClick();
        await m.open();
        await m.select();
        await m.click(operation);
        assert.equal(m.phase(), 'UNKNOWN');
        assert.equal(m.f.posts.length, 1);
        assert.deepEqual([...m.f.storage.values], saved);
        assert.deepEqual(m.f.changed, []);
      }
    );
  run(
    `${operation} mounted 合法 native CAS 全集 +1 不误 UNKNOWN，仅 changed 一次`,
    { quotes: [quote()], confirmed: operation === 'approve' },
    async (m) => {
      await m.select();
      const before = copy(m.f.rows.get(O).allocations);
      m.f.afterPost = (d) => d.allocations.reverse();
      await m.click(operation);
      assert.equal(m.phase(), 'SUCCEEDED');
      assert.equal(m.blocked(), false);
      assert.equal(m.f.posts.length, 1);
      assert.deepEqual(m.f.changed, ['changed']);
      assert.deepEqual(
        m.bindings.state.detail.allocations,
        before
          .map((row) => ({ ...row, version: row.version + 1, allocationStatus: operation === 'approve' ? row.allocationStatus : 'RELEASED' }))
          .reverse()
      );
    }
  );
}

// P2 原不可变报价展示：每个值来自 distinct 原生 fixture，不是表单默认值/新复核输入。
const ORIGINAL_QUOTE_LABELS = {
  quoteId: '原报价ID',
  orderId: '报价所属订单ID',
  quoteVersion: '原报价版本',
  enteredBy: '报价录入人ID',
  requestKey: '原报价请求键',
  quantity: '数量（原值）',
  unitPriceExcludingTax: '未税单价（原值）',
  taxRate: '税率（原值）',
  currency: '币种（原值）',
  exchangeRate: '汇率（原值）',
  promisedArrivalDate: '承诺到货日',
  validFrom: '有效期开始',
  validUntil: '有效期结束',
  supplierQuoteReference: '供应商报价引用',
  supplierContact: '供应联系人',
  evidenceReference: '证据引用',
  evidenceSha256: '证据SHA-256',
  paymentTerms: '付款条款',
  deliveryTerms: '交付条款',
  quoteHash: '原报价Hash',
  confirmationId: '既有确认ID',
  confirmedBy: '既有确认人ID',
  confirmationHash: '既有确认Hash',
  reviewNote: '既有复核说明'
};
function markedQuote(patch = {}) {
  return quote(O, {
    quoteId: Q2,
    quoteVersion: 2,
    enteredBy: '9007199254741701',
    requestKey: 'SOURCE-MARK-original-q-702',
    quantity: '271.4321',
    unitPriceExcludingTax: '7654321.9876',
    taxRate: '0.0750',
    currency: 'CNY',
    exchangeRate: '1.00000000',
    promisedArrivalDate: '2026-10-21',
    validFrom: '2026-09-23',
    validUntil: '2026-10-28',
    supplierQuoteReference: 'SOURCE-MARK-supplier-reference-702',
    supplierContact: 'SOURCE-MARK-contact-702',
    evidenceReference: 'SOURCE-MARK-evidence-702',
    evidenceSha256: '8'.repeat(64),
    paymentTerms: 'SOURCE-MARK-payment-702',
    deliveryTerms: 'SOURCE-MARK-delivery-702',
    quoteHash: '7'.repeat(64),
    confirmationId: '9007199254741711',
    confirmedBy: '9007199254741721',
    confirmationHash: '7'.repeat(64),
    reviewNote: 'SOURCE-MARK-historical-review-702',
    ...patch
  });
}
const originalItem = (m, field) => all(m.surface).find((n) => n.tag === 'el-descriptions-item' && n.props.label === ORIGINAL_QUOTE_LABELS[field]);
function assertOriginalValues(m, q) {
  for (const field of Object.keys(ORIGINAL_QUOTE_LABELS)) {
    const item = originalItem(m, field);
    assert.ok(item, `必须展示原报价 ${field}，不是仅有表格列头`);
    assert.equal(text(item), String(q[field]), `原报价 ${field} 必须精确渲染原生值`);
  }
}
async function chooseOriginal(m, id = Q2) {
  m.bindings.activeTab = 'review';
  await Vue.nextTick();
  const select = all(m.surface).find((n) => n.tag === 'el-select');
  assert.ok(select, '真实所选原报价控件');
  select.props['onUpdate:modelValue'](id);
  await Vue.nextTick();
}
run('人工所选不可变报价完整 24 原字段均可见，金额/数量/哈希不转换不计算', { quotes: [markedQuote()] }, async (m) => {
  const q = copy(m.f.rows.get(O).quotes[0]);
  await chooseOriginal(m);
  assertOriginalValues(m, q);
  assert.equal(m.f.posts.length, 0);
  assert.equal(m.f.confirms.length, 0);
});
run('原历史 confirmation/reviewNote 可见，但新 reviewNote/terms 不预填且 materialReviewed false', { quotes: [markedQuote()] }, async (m) => {
  const initialInput = copy(m.bindings.terms);
  await chooseOriginal(m);
  const q = m.f.rows.get(O).quotes[0];
  for (const field of ['confirmationId', 'confirmedBy', 'confirmationHash', 'reviewNote']) {
    assert.ok(originalItem(m, field));
    assert.equal(text(originalItem(m, field)), String(q[field]));
  }
  assert.deepEqual(m.bindings.terms, initialInput);
  assert.equal(m.bindings.reviewNote, '');
  assert.equal(m.bindings.materialReviewed, false);
  assert.equal(m.f.posts.length, 0);
});
run('报价只读详情不默认选报价，打开仅 GET、不自动复核或批准', { quotes: [markedQuote()] }, async (m) => {
  assert.equal(m.bindings.selectedQuoteId, '');
  assert.equal(originalItem(m, 'paymentTerms'), undefined);
  assert.equal(originalItem(m, 'reviewNote'), undefined);
  assert.equal(m.bindings.reviewNote, '');
  assert.equal(m.bindings.terms.unitPriceExcludingTax, '');
  assert.equal(m.bindings.terms.promisedArrivalDate, '');
  assert.equal(m.f.posts.length, 0);
  assert.equal(m.f.confirms.length, 0);
});
run('新报价/复核输入编辑不改所选 quote 源 snapshot 或只读原值', { quotes: [markedQuote()] }, async (m) => {
  const original = copy(m.f.rows.get(O).quotes[0]);
  await chooseOriginal(m);
  m.bindings.terms.paymentTerms = 'MANUAL-MARK-new-payment';
  m.bindings.terms.deliveryTerms = 'MANUAL-MARK-new-delivery';
  m.bindings.terms.unitPriceExcludingTax = '2.1234';
  m.bindings.terms.evidenceSha256 = '9'.repeat(64);
  m.bindings.reviewNote = 'MANUAL-MARK-new-review';
  await Vue.nextTick();
  assertOriginalValues(m, original);
  assert.deepEqual(m.bindings.state.detail.quotes[0], original);
  assert.equal(m.f.posts.length, 0);
  assert.equal(m.f.confirms.length, 0);
});
for (const role of ['readonly', 'queryonly'])
  run(
    `${role} 能人工选择并看完整原报价；直接调用写 handler 仍 0 POST/0 modal`,
    { quotes: [markedQuote()], ...(role === 'readonly' ? { readOnly: true } : { context: Object.fromEntries(FLAGS.map((k) => [k, false])) }) },
    async (m) => {
      const picker = all(m.surface).find((n) => n.tag === 'el-select');
      assert.equal(picker.props.disabled, false, '只读查看的人工选择不能被写表单锁死');
      for (let parent = picker.parent; parent; parent = parent.parent)
        assert.ok(parent.tag !== 'el-form' || parent.props.disabled !== true, '所选报价查看独立于 disabled 写表单');
      await chooseOriginal(m);
      assertOriginalValues(m, m.f.rows.get(O).quotes[0]);
      await m.fill();
      for (const op of ['terms', 'confirm', 'approve', 'close', 'cancel']) await m.click(op);
      assert.equal(m.f.posts.length, 0);
      assert.equal(m.f.confirms.length, 0);
      assert.deepEqual(m.f.changed, []);
    }
  );
run(
  'priceVisible false 明示权限，残留 quote 也不能进入 table/options/所选详情或泄漏原值',
  { context: { ...Object.fromEntries(FLAGS.map((k) => [k, false])), priceVisible: false } },
  async (m) => {
    const q = markedQuote();
    // 模拟旧可见缓存仍在内存；展示层本身仍必须遵守当前 priceVisible，不借渲染授予资格。
    m.bindings.state.detail.quotes = [q];
    m.bindings.selectedQuoteId = Q2;
    await Vue.nextTick();
    assert.ok(text(m.surface).includes('没有报价价格可见权限'));
    const table = all(m.surface).find((n) => n.tag === 'el-table');
    assert.deepEqual(table.props.data, []);
    assert.equal(all(m.surface).filter((n) => n.tag === 'el-option').length, 0);
    assert.equal(originalItem(m, 'paymentTerms'), undefined);
    assert.equal(originalItem(m, 'unitPriceExcludingTax'), undefined);
    for (const field of ['paymentTerms', 'supplierContact', 'evidenceSha256', 'reviewNote', 'unitPriceExcludingTax'])
      assert.ok(!text(m.surface).includes(q[field]), `不可泄漏 ${field}`);
    for (const op of ['terms', 'confirm', 'approve', 'close', 'cancel']) await m.click(op);
    assert.equal(m.f.posts.length, 0);
    assert.equal(m.f.confirms.length, 0);
  }
);
run(
  '没有原确认时只读详情明确缺失，不编造 actor/hash/note/时间；新复核输入仍空',
  { quotes: [markedQuote({ confirmationId: null, confirmedBy: null, confirmationHash: null, reviewNote: null })] },
  async (m) => {
    await chooseOriginal(m);
    for (const [field, absent] of Object.entries({
      confirmationId: '无原确认',
      confirmedBy: '无原确认人',
      confirmationHash: '无原确认摘要',
      reviewNote: '无原复核说明'
    })) {
      assert.ok(originalItem(m, field));
      assert.equal(text(originalItem(m, field)), absent);
    }
    assert.equal(m.bindings.reviewNote, '');
    assert.ok(!text(m.surface).includes('2026-10-09 09:'));
    assert.equal(m.f.posts.length, 0);
  }
);
run(
  '所选原报价详情随显式选择变化；modal pending 切换仍中止原确认，不自动换 quote 发请求',
  {
    quotes: [
      markedQuote({ quoteId: Q, quoteVersion: 1, paymentTerms: 'SOURCE-MARK-payment-old' }),
      markedQuote({ confirmationId: null, confirmedBy: null, confirmationHash: null, reviewNote: null })
    ]
  },
  async (m) => {
    await chooseOriginal(m);
    assert.ok(originalItem(m, 'paymentTerms'));
    assert.equal(text(originalItem(m, 'paymentTerms')), 'SOURCE-MARK-payment-702');
    m.bindings.reviewNote = 'MANUAL-MARK-one-confirmation';
    m.f.modalGate = deferred();
    const writing = m.click('confirm');
    await Vue.nextTick();
    await chooseOriginal(m, Q);
    assert.equal(text(originalItem(m, 'paymentTerms')), 'SOURCE-MARK-payment-old');
    m.f.modalGate.resolve('confirm');
    await writing;
    assert.equal(m.f.posts.length, 0);
    assert.deepEqual(m.f.changed, []);
    assert.equal(m.blocked(), false);
  }
);
