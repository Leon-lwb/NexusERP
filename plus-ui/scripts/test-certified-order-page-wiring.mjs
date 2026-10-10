import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parse } from '@babel/parser';
import { build, transformSync } from 'esbuild';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const source = readFileSync(resolve(root, 'src/views/erp/pu/order/form.vue'), 'utf8').match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1];
const ast = parse(source, { sourceType: 'module', plugins: ['typescript'] });
const names = ast.program.body.filter((n) => n.type === 'FunctionDeclaration').map((n) => n.id.name);
const functions = ast.program.body
  .filter((n) => n.type === 'FunctionDeclaration')
  .map((n) => source.slice(n.start, n.end))
  .join('\n');
const js = transformSync(functions, { loader: 'ts', target: 'es2022' }).code;
const temp = mkdtempSync(resolve(tmpdir(), 'order-page-'));
await build({
  entryPoints: [resolve(root, 'src/utils/certified-business.ts')],
  bundle: true,
  platform: 'node',
  format: 'esm',
  outfile: resolve(temp, 'helper.mjs'),
  plugins: [
    {
      name: 'mock-request',
      setup(b) {
        b.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'request', namespace: 'mock' }));
        b.onLoad({ filter: /.*/, namespace: 'mock' }, () => ({
          contents:
            'export default Object.assign(async()=>{throw new Error("no real network")},{defaults:{transformRequest:[],transformResponse:[]}});',
          loader: 'js'
        }));
      }
    }
  ]
});
const helper = await import(pathToFileURL(resolve(temp, 'helper.mjs')).href);
process.on('exit', () => rmSync(temp, { recursive: true, force: true }));
const A = '9007199254740993',
  B = '9007199254740994',
  F = '9007199254740995';
const ref = (value) => ({ value });
const clone = (x) => JSON.parse(JSON.stringify(x));
function fixture(protectedOrder = true) {
  const f = {
    protectedOrder,
    nativeFlow: null,
    allocations: [],
    updates: [],
    submits: [],
    route: { path: '/erp/pu/order/form', query: { id: A, action: 'edit' } },
    token: 'fixture-token',
    validate: null,
    holdValidation: false,
    holdUpdate: false,
    resolveUpdate: null
  };
  const native = (id) => ({
    order: {
      tenantId: '000000',
      orderId: id,
      orderNo: 'fixture-order',
      requisitionId: f.protectedOrder ? B : null,
      vendorId: F,
      status: 'DRAFT',
      version: f.protectedOrder ? 0 : null,
      quoteId: null,
      selectFlowId: f.nativeFlow,
      requisitionSourceType: f.protectedOrder ? 'AI_DECISION' : null,
      decisionRunId: f.protectedOrder ? B : null,
      decisionActionId: f.protectedOrder ? F : null,
      sourceRequestKey: f.protectedOrder ? 'fixture-source' : null,
      originalCommandPresent: f.protectedOrder
    },
    quotes: [],
    allocations: f.allocations,
    priceVisible: false,
    serverDate: '2026-10-09',
    genericEditAllowedHint: !f.protectedOrder
  });
  const env = {
    ...helper,
    console,
    JSON,
    Object,
    String,
    Number,
    Promise,
    Map,
    Set,
    route: f.route,
    props: { disabled: false, customFormData: null },
    form: ref({ orderId: A, orderNo: 'fixture-order', status: 'DRAFT', selectFlowId: F }),
    lineList: ref([]),
    isViewMode: ref(false),
    autoGenFlag: ref(false),
    legacyProtection: ref(null),
    legacyWritePending: ref(false),
    loadedCredential: ref(null),
    loadedActor: ref(null),
    nativeOrderSnapshot: ref(null),
    originalOrderId: ref(null),
    detailRequestSeq: ref(0),
    loadedRouteSignature: ref(''),
    sourceProtected: ref(true),
    loadingFlow: ref(false),
    flowOptions: ref([]),
    title: ref(''),
    getToken: () => f.token,
    getCertifiedOrderContext: async () => ({
      tenantId: '000000',
      userId: F,
      termsAddAllowed: false,
      termsConfirmAllowed: false,
      approveAllowed: false,
      closeAllowed: false,
      cancelAllowed: false,
      priceVisible: false
    }),
    getCertifiedOrderDetail: async (id) => native(id),
    getPurchaseOrder: async (id) => ({
      code: 200,
      data: { orderId: id, orderNo: 'fixture-order', requisitionId: f.protectedOrder ? B : null, status: 'DRAFT', selectFlowId: F, lineList: [] }
    }),
    updatePurchaseOrder: (body, session) => {
      session?.assertCurrent();
      f.updates.push(clone(body));
      if (!f.holdUpdate) f.nativeFlow = body.selectFlowId ?? null;
      return f.holdUpdate
        ? new Promise((r) => {
            f.resolveUpdate = r;
          })
        : Promise.resolve({ code: 200 });
    },
    addPurchaseOrder: () => {
      f.updates.push('add');
      return Promise.resolve({ code: 200 });
    },
    submitOrderFlow: (id, flow, session) => {
      session?.assertCurrent();
      f.submits.push({ id, flow });
      return Promise.resolve({ code: 200 });
    },
    router: { push: () => {} },
    proxy: {
      $modal: { msgWarning: () => {}, msgSuccess: () => {}, msgError: () => {} },
      $refs: {
        formRef: {
          validate: (cb) => {
            f.validate = cb;
            if (!f.holdValidation) cb(true);
          }
        }
      }
    },
    formDisabled: ref(false)
  };
  const ctx = vm.createContext(env);
  vm.runInContext(js + '\nthis.handlers={' + names.map((n) => n + ':' + n).join(',') + '};', ctx);
  ctx.recalcLines = () => {};
  ctx.tryAutoSelect = () => {};
  f.env = ctx;
  f.handlers = ctx.handlers;
  return f;
}
const tick = async () => {
  for (let i = 0; i < 12; i++) await Promise.resolve();
};
async function load(f, id = A) {
  await f.handlers.getInfo(id);
  await tick();
}
test('认证订单旧保存在原生查询后封闭', async () => {
  const f = fixture();
  await load(f);
  await f.handlers.submitForm();
  await tick();
  assert.equal(f.updates.length, 0);
});
test('改本地requisitionId不能解开原生保护', async () => {
  const f = fixture();
  await load(f);
  f.env.form.value.requisitionId = null;
  f.env.form.value.certifiedVersion = null;
  await f.handlers.submitForm();
  await tick();
  assert.equal(f.updates.length, 0);
});
test('认证订单旧Flowable入口不发UPDATE或submit', async () => {
  const f = fixture();
  await load(f);
  await f.handlers.handleSubmitFlow();
  await tick();
  assert.equal(f.updates.length, 0);
  assert.equal(f.submits.length, 0);
});
test('disabled和view只读入口不能靠直接调用handler写入', async () => {
  for (const field of ['disabled', 'view']) {
    const f = fixture(false);
    await load(f);
    if (field === 'disabled') f.env.props.disabled = true;
    else f.env.isViewMode.value = true;
    await f.handlers.submitForm();
    await f.handlers.handleSubmitFlow();
    await tick();
    assert.equal(f.updates.length, 0);
  }
});
test('明确普通历史单仍可保存既有业务数据', async () => {
  const f = fixture(false);
  await load(f);
  await f.handlers.submitForm();
  await tick();
  assert.equal(f.updates.length, 1);
  assert.equal(f.updates[0].orderId, A);
});
test('普通A的迟到update不能向随后保护B提交Flowable', async () => {
  const f = fixture(false);
  await load(f);
  f.holdUpdate = true;
  const result = f.handlers.handleSubmitFlow();
  await tick();
  assert.equal(f.updates.length, 1);
  f.route.query.id = B;
  f.protectedOrder = true;
  await load(f, B);
  f.resolveUpdate({ code: 200 });
  await result;
  await tick();
  assert.equal(f.submits.length, 0);
});
test('普通A的迟到validate不能写随后保护B', async () => {
  const f = fixture(false);
  await load(f);
  f.holdValidation = true;
  const result = f.handlers.handleSubmitFlow();
  await tick();
  assert.equal(typeof f.validate, 'function');
  f.route.query.id = B;
  f.protectedOrder = true;
  await load(f, B);
  f.validate(true);
  await result;
  await tick();
  assert.equal(f.updates.length, 0);
  assert.equal(f.submits.length, 0);
});
test('原生保护读取失败不能猜普通单再保存', async () => {
  const f = fixture(false);
  f.env.getCertifiedOrderDetail = async () => {
    throw new Error('fixture read failure');
  };
  await load(f);
  await f.handlers.submitForm();
  await tick();
  assert.equal(f.updates.length, 0);
});

const listSource = readFileSync(resolve(root, 'src/views/erp/pu/order/index.vue'), 'utf8').match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1];
const listAst = parse(listSource, { sourceType: 'module', plugins: ['typescript'] });
const listNames = listAst.program.body.filter((n) => n.type === 'FunctionDeclaration').map((n) => n.id.name);
const listJs = transformSync(
  listAst.program.body
    .filter((n) => n.type === 'FunctionDeclaration')
    .map((n) => listSource.slice(n.start, n.end))
    .join('\n'),
  { loader: 'ts', target: 'es2022' }
).code;
function listFixture(protectedOrder = true) {
  const f = { receives: [], deletes: [], routes: [], opens: [], protectedOrder, allocations: [] };
  const env = {
    ...helper,
    JSON,
    Object,
    String,
    Number,
    Promise,
    ids: ref([A]),
    orderList: ref([{ orderId: A, requisitionId: protectedOrder ? B : null }]),
    mutationReadPending: ref(false),
    certifiedOrderRef: ref({ open: (id) => f.opens.push(id) }),
    getToken: () => 'fixture-token',
    getCertifiedOrderContext: async () => ({ tenantId: '000000', userId: F }),
    getCertifiedOrderDetail: async (id) => ({
      order: {
        orderId: id,
        tenantId: '000000',
        requisitionId: f.protectedOrder ? B : null,
        version: f.protectedOrder ? 0 : null,
        quoteId: null,
        decisionRunId: f.protectedOrder ? B : null,
        decisionActionId: f.protectedOrder ? F : null,
        sourceRequestKey: f.protectedOrder ? 'source' : null,
        originalCommandPresent: f.protectedOrder,
        requisitionSourceType: f.protectedOrder ? 'AI_DECISION' : null
      },
      genericEditAllowedHint: !f.protectedOrder,
      allocations: f.allocations
    }),
    router: { push: (r) => f.routes.push(r) },
    proxy: { $modal: { msgWarning: () => {}, msgSuccess: () => {}, confirm: async () => true } },
    getList: () => {},
    receivePurchaseOrder: async (id, s) => {
      s?.assertCurrent();
      f.receives.push(id);
      return { code: 200, data: B };
    },
    delPurchaseOrder: async (ids, s) => {
      s?.assertCurrent();
      f.deletes.push(ids);
      return { code: 200 };
    }
  };
  const ctx = vm.createContext(env);
  vm.runInContext(listJs + '\nthis.handlers={' + listNames.map((n) => n + ':' + n).join(',') + '};', ctx);
  f.env = ctx;
  f.handlers = ctx.handlers;
  return f;
}
test('订单列表旧收货联动不能办理认证单', async () => {
  const f = listFixture();
  await f.handlers.handleReceive();
  await tick();
  assert.equal(f.receives.length, 0);
});
test('订单列表修改认证单转专属query入口而非旧编辑', async () => {
  const f = listFixture();
  await f.handlers.handleUpdate();
  await tick();
  assert.equal(f.routes.length, 0);
  assert.deepEqual(f.opens, [A]);
});
test('订单列表普通历史单仍可编辑', async () => {
  const f = listFixture(false);
  await f.handlers.handleUpdate();
  assert.equal(f.routes.length, 1);
  assert.equal(f.routes[0].query.id, A);
});
test('订单列表删除必须先核验原生归属', async () => {
  const f = listFixture();
  assert.equal(typeof f.handlers.handleDelete, 'function');
  await f.handlers.handleDelete();
  assert.equal(f.deletes.length, 0);
});
test('订单列表原生读取失败不猜普通单收货', async () => {
  const f = listFixture(false);
  f.env.getCertifiedOrderDetail = async () => {
    throw new Error('fixture');
  };
  await f.handlers.handleReceive();
  await tick();
  assert.equal(f.receives.length, 0);
});

test('普通历史单合法保存所选Flow后仍能提交该原Flow', async () => {
  const f = fixture(false);
  await load(f);
  await f.handlers.handleSubmitFlow();
  await tick();
  assert.equal(f.updates.length, 1);
  assert.equal(f.submits.length, 1);
  assert.equal(f.submits[0].id, A);
  assert.equal(f.submits[0].flow, F);
});

test('普通表单会话改变后不能借fresh新token保存原页面', async () => {
  const f = fixture(false);
  await load(f);
  f.token = 'new-fixture-token';
  await f.handlers.submitForm();
  await tick();
  assert.equal(f.updates.length, 0);
});

test('普通提示头但有原生allocation的旧保存和Flowable封闭', async () => {
  const f = fixture(false);
  f.allocations = [{ allocationId: B, requisitionLineId: F, orderLineId: A, allocationStatus: 'ORDER_COMMITTED_NOT_TIMELY_SUPPLY', version: 2 }];
  await load(f);
  await f.handlers.submitForm();
  await f.handlers.handleSubmitFlow();
  await tick();
  assert.equal(f.updates.length, 0);
  assert.equal(f.submits.length, 0);
});
test('普通提示头但有原生allocation的旧列表收货和删除封闭', async () => {
  const f = listFixture(false);
  f.allocations = [{ allocationId: B, requisitionLineId: F, orderLineId: A }];
  await f.handlers.handleReceive();
  await f.handlers.handleDelete();
  assert.equal(f.receives.length, 0);
  assert.equal(f.deletes.length, 0);
});
