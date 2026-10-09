import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import axios from 'axios';

const apiUrl = new URL('../src/api/erp/pu/requisition/index.ts', import.meta.url);

/** 加载实际 TypeScript；只替换网络边界，不伪造被测 API 或生产导出。 */
function loadTs(url, request) {
  const { outputText, diagnostics } = ts.transpileModule(readFileSync(url, 'utf8'), {
    fileName: url.pathname,
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
    reportDiagnostics: true
  });
  assert.deepEqual(
    diagnostics.filter((entry) => entry.category === ts.DiagnosticCategory.Error),
    []
  );
  const exports = {};
  new Function('exports', 'require', outputText)(exports, (name) => {
    assert.equal(name, '@/utils/request', '纯控制器只能使用 type-only API 依赖');
    return { default: request };
  });
  return exports;
}

test('认证生命周期 API 必须真实导出四个方法（缺失功能的断言 RED，不是模块加载失败）', () => {
  const request = axios.create({ adapter: async () => assert.fail('导出契约检查不能发送网络请求') });
  const api = loadTs(apiUrl, request);
  for (const name of [
    'getCertifiedRequisitionWorkflowContext',
    'getCertifiedRequisitionWorkflowDetail',
    'approveCertifiedPurchaseRequisition',
    'convertCertifiedPurchaseRequisition'
  ]) {
    assert.equal(typeof api[name], 'function', `缺少认证生命周期方法 ${name}`);
  }
});

const workflowUrl = new URL('../src/views/erp/pu/requisition/certifiedWorkflow.ts', import.meta.url);
const ID = '9007199254740993';
const OTHER_ID = '9007199254740995';
const ORDER_ID = '9007199254740997';
const TOKEN_A = 'offline-credential-a';
const TOKEN_B = 'offline-credential-b';
const RAW_ERROR = '原始异常不得外显 offline-credential-a';
const ok = (data) => ({ code: 200, data });
const copy = (data) => JSON.parse(JSON.stringify(data));
const context = () => ({ tenantId: '000001', userId: '101', approveAllowed: true, convertAllowed: true });
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
const workflowResult = (patch = {}) => ({
  requisitionId: ID,
  requisitionNo: 'PR-20261009-001',
  status: 'APPROVED',
  version: 6,
  orderId: null,
  orderNo: null,
  replayed: false,
  allocations: [
    {
      eventId: '901',
      allocationId: '902',
      actorId: '101',
      requisitionLineId: '903',
      operation: patch.status === 'ORDERED' ? 'TRANSFER_TO_ORDER' : 'APPROVE',
      fromStatus: 'REQUEST_INTENT',
      toStatus: patch.status === 'ORDERED' ? 'ORDER_COMMITTED_NOT_TIMELY_SUPPLY' : 'REQUEST_INTENT',
      occupiedBefore: '4.50',
      occupiedAfter: '4.50',
      orderLineId: patch.status === 'ORDERED' ? '904' : null,
      replayed: patch.replayed ?? false
    }
  ],
  ...patch
});

function memoryStorage() {
  const values = new Map();
  const removed = [];
  return {
    values,
    removed,
    getItem: (key) => (values.has(key) ? values.get(key) : null),
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => {
      removed.push(key);
      values.delete(key);
    }
  };
}

/** 使用真实 Axios 变换和拦截器，仅 adapter 为离线替身，绝不访问服务或数据库。 */
function apiFixture() {
  const f = { token: TOKEN_A, context: context(), detail: document(), calls: [], writes: [], storage: memoryStorage() };
  const service = axios.create({
    adapter: async (config) => {
      const call = {
        url: config.url,
        method: config.method,
        headers: { ...config.headers.toJSON(), isToken: config.headers.get('isToken'), repeatSubmit: config.headers.get('repeatSubmit') },
        data: config.data ? JSON.parse(config.data) : undefined
      };
      f.calls.push(call);
      let envelope;
      let status = 200;
      if (config.url.endsWith('/certified/workflow-context')) {
        envelope = f.contextEnvelope ?? ok(copy(f.context));
        status = f.readStatus ?? 200;
        if (f.contextWait) await f.contextWait(call);
      } else if (config.method === 'get') {
        envelope = f.detailEnvelope ?? ok(copy(f.detail));
        status = f.readStatus ?? 200;
        if (f.detailWait) await f.detailWait(call);
      } else {
        f.writes.push(call);
        if (f.writeWait) await f.writeWait(call);
        if (f.networkFault) throw new axios.AxiosError(RAW_ERROR, axios.AxiosError.ETIMEDOUT, config);
        status = f.writeStatus ?? 200;
        if (f.writeEnvelope !== undefined) envelope = f.writeEnvelope;
        else {
          const ordered = config.url.endsWith('/convert');
          const replayed = ordered && f.detail.status === 'ORDERED';
          f.detail = { ...f.detail, status: ordered ? 'ORDERED' : 'APPROVED', version: replayed ? f.detail.version : f.detail.version + 1 };
          envelope = ok(
            workflowResult({
              requisitionId: f.detail.requisitionId,
              requisitionNo: f.detail.requisitionNo,
              status: f.detail.status,
              version: f.detail.version,
              orderId: ordered ? ORDER_ID : null,
              orderNo: ordered ? 'PO-20261009-001' : null,
              replayed
            })
          );
        }
      }
      const response = { data: JSON.stringify(envelope), status, statusText: '', headers: {}, config, request: {} };
      if (config.validateStatus && !config.validateStatus(status)) {
        throw new axios.AxiosError(RAW_ERROR, axios.AxiosError.ERR_BAD_RESPONSE, config, {}, response);
      }
      return response;
    }
  });
  // 和生产请求器一样：只有没有固定凭证时才读共享缓存。
  service.interceptors.request.use(async (config) => {
    if (config.headers.isToken !== false) config.headers.set('Authorization', 'Bearer ' + f.token);
    if (f.beforeDispatch) await f.beforeDispatch(config);
    return config;
  });
  // 模拟全局业务信封解包及错误拒绝；HTTP 错误仍由真实 Axios 处理。
  service.interceptors.response.use((response) => {
    if ((response.data?.code ?? 200) !== 200) throw new Error(RAW_ERROR);
    return response.data;
  });
  f.api = loadTs(apiUrl, service);
  f.session = () => ({
    token: f.token,
    assertCurrent() {
      if (f.token !== this.token) throw new Error(RAW_ERROR);
    }
  });
  return f;
}

function loadWorkflow() {
  // 文件缺失也是功能断言失败，绝不把 ENOENT / import 异常充当 RED。
  assert.doesNotThrow(() => readFileSync(workflowUrl), '缺少纯认证生命周期控制器文件');
  return loadTs(workflowUrl);
}

function controllerFixture(f = apiFixture()) {
  const changes = [];
  const deps = {
    readCredential: () => f.token,
    readContext: (session) => f.api.getCertifiedRequisitionWorkflowContext(session),
    readDetail: (id, session) => f.api.getCertifiedRequisitionWorkflowDetail(id, session),
    submit: (operation, id, version, session) =>
      operation === 'approve'
        ? f.api.approveCertifiedPurchaseRequisition(id, version, session)
        : f.api.convertCertifiedPurchaseRequisition(id, version, session),
    storage: f.storage,
    onChange: (state) => changes.push(state)
  };
  const make = () => loadWorkflow().createCertifiedRequisitionWorkflow(deps);
  return { f, changes, deps, make, controller: make() };
}

function deferred() {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

const lockEntry = (f) => {
  assert.equal(f.storage.values.size, 1, '仅允许文档作用域锁，凭证不应入存储');
  const [key, text] = [...f.storage.values][0];
  assert.ok(!text.includes(TOKEN_A) && !text.includes(TOKEN_B) && !key.includes(TOKEN_A));
  return { key, text };
};
const safeMessage = (state) => {
  assert.equal(typeof state.message, 'string');
  assert.ok(!state.message.includes(RAW_ERROR) && !state.message.includes(TOKEN_A) && !state.message.includes(TOKEN_B));
};

test('真实 API：独立只读 context / 正常详情 / 两种 POST 均固定凭证，仅写 expectedVersion', async () => {
  const f = apiFixture();
  const session = f.session();
  assert.deepEqual(await f.api.getCertifiedRequisitionWorkflowContext(session), context());
  assert.deepEqual(await f.api.getCertifiedRequisitionWorkflowDetail(ID, session), document());
  const approved = await f.api.approveCertifiedPurchaseRequisition(ID, 5, session);
  const converted = await f.api.convertCertifiedPurchaseRequisition(ID, 6, session);
  assert.equal(approved.code, 200);
  assert.equal(converted.data.orderId, ORDER_ID);
  assert.deepEqual(
    f.calls.map((call) => [call.method, call.url]),
    [
      ['get', '/erp/pu/requisition/certified/workflow-context'],
      ['get', '/erp/pu/requisition/' + ID],
      ['post', '/erp/pu/requisition/certified/' + ID + '/approve'],
      ['post', '/erp/pu/requisition/certified/' + ID + '/convert']
    ]
  );
  for (const call of f.calls) {
    assert.equal(call.headers.Authorization, 'Bearer ' + TOKEN_A);
    assert.equal(call.headers.isToken, false);
    assert.equal(call.headers.repeatSubmit, false);
  }
  assert.deepEqual(
    f.writes.map((call) => call.data),
    [{ expectedVersion: 5 }, { expectedVersion: 6 }]
  );
});

for (const id of [
  '01',
  '0',
  '-1',
  '1.0',
  ' 1',
  '1 ',
  '+1',
  '1e3',
  '9223372036854775808',
  '',
  0,
  -1,
  1.5,
  Number.MAX_SAFE_INTEGER + 1,
  null,
  undefined
]) {
  test(`ID 边界：拒绝非 canonical / 不安全 ID ${String(id)}`, async () => {
    const normalize = loadWorkflow().normalizeRequisitionId;
    assert.throws(() => normalize(id));
    const f = apiFixture();
    await assert.rejects(() => f.api.getCertifiedRequisitionWorkflowDetail(id, f.session()));
    await assert.rejects(() => f.api.approveCertifiedPurchaseRequisition(id, 0, f.session()));
    assert.equal(f.calls.length, 0);
  });
}

test('ID 边界：Long.MAX 字符串保持逐字精确，安全正整数允许转换为字符串', () => {
  const normalize = loadWorkflow().normalizeRequisitionId;
  assert.equal(normalize('9223372036854775807'), '9223372036854775807');
  assert.equal(normalize(ID), ID);
  assert.equal(normalize(Number.MAX_SAFE_INTEGER), String(Number.MAX_SAFE_INTEGER));
  assert.equal(normalize(42), '42');
});

for (const version of [null, undefined, '5', 1.5, -1, 2147483647, Infinity, NaN]) {
  test(`版本边界：API 与控制器禁止未知 / 非整数版本 ${String(version)}`, async () => {
    const fixture = controllerFixture();
    fixture.f.detail.version = version;
    await fixture.controller.open(ID);
    assert.equal(fixture.controller.snapshot().canApprove, false);
    await fixture.controller.approve();
    assert.equal(fixture.f.writes.length, 0);
    await assert.rejects(() => fixture.f.api.convertCertifiedPurchaseRequisition(ID, version, fixture.f.session()));
  });
}

test('旧写保护只放行明确 MANUAL 且无认证标记；保护不证明永久 command', () => {
  const protectedHint = loadWorkflow().isProtectedRequisitionHint;
  assert.equal(protectedHint({ sourceType: 'MANUAL' }), false);
  assert.equal(protectedHint({ sourceType: 'MANUAL', decisionRunId: null, decisionActionId: null, decisionIdempotencyKey: null }), false);
  for (const row of [
    null,
    {},
    { sourceType: 'manual' },
    { sourceType: ' MANUAL' },
    { sourceType: 'MRP' },
    { sourceType: 'AI_DECISION' },
    { sourceType: 'OTHER' },
    ...['decisionRunId', 'decisionActionId', 'decisionIdempotencyKey'].flatMap((key) => [
      { sourceType: 'MANUAL', [key]: '1' },
      { sourceType: 'MANUAL', [key]: '' },
      { sourceType: 'MANUAL', [key]: 0 }
    ])
  ])
    assert.equal(protectedHint(row), true);
});

for (const patch of [
  { decisionIdempotencyKey: null },
  { decisionIdempotencyKey: '' },
  { decisionIdempotencyKey: '  key' },
  { sourceType: undefined },
  { sourceType: 'MANUAL' },
  { sourceType: 'OTHER' },
  { selectFlowId: '123' },
  { selectFlowId: 0 },
  { selectFlowId: '' },
  { sourceType: 'AI_DECISION' },
  { decisionRunId: '401' },
  { decisionActionId: '402' },
  { sourceType: 'AI_DECISION', decisionRunId: '401' },
  { sourceType: 'AI_DECISION', decisionRunId: '401', decisionActionId: Number.MAX_SAFE_INTEGER + 1 },
  { requisitionId: OTHER_ID }
]) {
  test('详情 fail-closed：不完整标记 / Flowable / 未知来源不能写 ' + JSON.stringify(patch), async () => {
    const { f, controller } = controllerFixture();
    f.detail = document(patch);
    await controller.open(ID);
    assert.equal(controller.snapshot().canApprove, false);
    await controller.approve();
    assert.equal(f.writes.length, 0);
    safeMessage(controller.snapshot());
  });
}

test('完整 AI 标记仅提示可尝试后端认证，不额外提交 actor / tenant / 数量', async () => {
  const { f, controller } = controllerFixture();
  f.detail = document({ sourceType: 'AI_DECISION', decisionRunId: '401', decisionActionId: '402' });
  await controller.open(ID);
  assert.equal(controller.snapshot().canApprove, true);
  await controller.approve();
  assert.deepEqual(f.writes[0].data, { expectedVersion: 5 });
});

test('读操作：权限提示 false / malformed context / unsafe actor 均不允许写，query 不依赖 add', async () => {
  for (const patch of [
    { approveAllowed: false, convertAllowed: false },
    { approveAllowed: 'true' },
    { userId: Number.MAX_SAFE_INTEGER + 1 },
    { userId: 1.5 },
    { tenantId: '' }
  ]) {
    const { f, controller } = controllerFixture();
    f.context = { ...context(), ...patch };
    await controller.open(ID);
    assert.equal(controller.snapshot().canApprove, false);
    await controller.approve();
    assert.equal(f.writes.length, 0);
  }
  const { f, controller } = controllerFixture();
  f.context.userId = 101;
  await controller.open(ID);
  assert.equal(controller.snapshot().canApprove, true);
  assert.ok(f.calls.every((call) => !call.url.endsWith('/certified/context')));
});

for (const [status, envelope] of [
  [500, { code: 500 }],
  [200, { code: 403 }],
  [200, { code: 200 }],
  [201, ok(context())]
]) {
  test(`只读 HTTP/R 非正常信封不能刷新授权事实 HTTP ${status} / R ${envelope.code}`, async () => {
    const { f, controller } = controllerFixture();
    f.readStatus = status;
    f.contextEnvelope = envelope;
    await controller.open(ID);
    assert.equal(controller.snapshot().canApprove, false);
    assert.equal(f.writes.length, 0);
  });
}

test('生命周期成功：每次写前重新读详情和 context，批准后只读刷新，转单与 ORDERED 回查用同一 ID', async () => {
  const { f, controller, changes } = controllerFixture();
  assert.equal(controller.snapshot().phase, 'IDLE');
  await controller.open(ID);
  assert.equal(controller.snapshot().phase, 'READY');
  f.calls.length = 0;
  f.detail.version = 8;
  await controller.approve();
  let state = controller.snapshot();
  assert.equal(state.phase, 'SUCCEEDED');
  assert.equal(state.document.status, 'APPROVED');
  assert.equal(state.canApprove, false);
  assert.equal(state.canConvert, true);
  assert.equal(state.unknownLocked, false);
  const firstWrite = f.calls.findIndex((call) => call.method === 'post');
  assert.ok(f.calls.slice(0, firstWrite).some((call) => call.url.endsWith('/workflow-context')));
  assert.ok(f.calls.slice(0, firstWrite).some((call) => call.url.endsWith('/' + ID)));
  assert.deepEqual(f.writes[0].data, { expectedVersion: 8 });
  assert.ok(f.calls.slice(firstWrite + 1).some((call) => call.url.endsWith('/' + ID)));
  await controller.convert();
  state = controller.snapshot();
  assert.equal(state.phase, 'SUCCEEDED');
  assert.equal(state.document.status, 'ORDERED');
  assert.equal(state.result.orderId, ORDER_ID);
  assert.equal(state.canConvert, true);
  const oldVersion = state.document.version;
  await controller.convert();
  assert.equal(controller.snapshot().result.replayed, true);
  assert.equal(controller.snapshot().result.orderId, ORDER_ID);
  assert.deepEqual(f.writes.at(-1).data, { expectedVersion: oldVersion });
  assert.ok(changes.some((entry) => entry.phase === 'SENDING'));
  assert.equal(f.storage.values.size, 0);
});

test('写前完整刷新发现权限、状态、版本或标记变化时不派发', async () => {
  for (const mutate of [
    (f) => {
      f.context.approveAllowed = false;
    },
    (f) => {
      f.detail.status = 'CANCELLED';
    },
    (f) => {
      f.detail.version = null;
    },
    (f) => {
      f.detail.decisionIdempotencyKey = null;
    }
  ]) {
    const { f, controller } = controllerFixture();
    await controller.open(ID);
    mutate(f);
    await controller.approve();
    assert.equal(f.writes.length, 0);
    assert.equal(controller.snapshot().canApprove, false);
  }
});

test('双击 / 并发刷新封闭，派发前意图已持久化且无凭证', async () => {
  const { f, controller } = controllerFixture();
  const entered = deferred();
  const gate = deferred();
  f.writeWait = async () => {
    lockEntry(f);
    entered.resolve();
    await gate.promise;
  };
  await controller.open(ID);
  const first = controller.approve();
  const second = controller.approve();
  await entered.promise;
  await controller.refresh();
  await second;
  assert.equal(f.writes.length, 1);
  assert.equal(controller.snapshot().phase, 'SENDING');
  gate.resolve();
  await first;
  assert.equal(controller.snapshot().phase, 'SUCCEEDED');
});

for (const fault of ['get', 'set', 'silentSet', 'remove', 'silentRemove']) {
  test(`存储 ${fault} 故障封闭；未知锁不被删除`, async () => {
    const { f, controller } = controllerFixture();
    await controller.open(ID);
    if (fault === 'get')
      f.storage.getItem = () => {
        throw new Error(RAW_ERROR);
      };
    if (fault === 'set')
      f.storage.setItem = () => {
        throw new Error(RAW_ERROR);
      };
    if (fault === 'silentSet') f.storage.setItem = () => {};
    if (fault === 'remove')
      f.storage.removeItem = () => {
        throw new Error(RAW_ERROR);
      };
    if (fault === 'silentRemove') f.storage.removeItem = () => {};
    await controller.approve();
    const state = controller.snapshot();
    assert.equal(state.canApprove, false);
    assert.equal(state.canConvert, false);
    assert.equal(f.writes.length, ['remove', 'silentRemove'].includes(fault) ? 1 : 0);
    assert.ok(['BLOCKED', 'UNKNOWN'].includes(state.phase));
    safeMessage(state);
  });
}

for (const [http, code] of [
  [200, 409],
  [400, 400],
  [403, 403]
]) {
  test(`明确拒绝 HTTP ${http}/R ${code} 只能手动刷新后再次操作，不自动重试`, async () => {
    const { f, controller } = controllerFixture();
    f.writeStatus = http;
    f.writeEnvelope = { code, msg: RAW_ERROR };
    await controller.open(ID);
    await controller.approve();
    assert.equal(controller.snapshot().phase, 'REFUSED');
    safeMessage(controller.snapshot());
    assert.equal(f.writes.length, 1);
    assert.equal(f.storage.values.size, 0);
    await controller.approve();
    assert.equal(f.writes.length, 1);
    delete f.writeStatus;
    delete f.writeEnvelope;
    await controller.refresh();
    await controller.approve();
    assert.equal(controller.snapshot().phase, 'SUCCEEDED');
    assert.equal(f.writes.length, 2);
  });
}

for (const [http, envelope, network] of [
  [500, { code: 500, msg: RAW_ERROR }],
  [500, { code: 409, msg: RAW_ERROR }],
  [200, { code: 500, msg: RAW_ERROR }],
  [502, ok(workflowResult())],
  [400, {}],
  [201, ok(workflowResult())],
  [200, { code: 200 }],
  [200, { code: '200', data: workflowResult() }],
  [200, {}, true]
]) {
  test(`UNKNOWN 故障矩阵：HTTP ${http}/R ${envelope.code}/network ${!!network} reload 不清锁、不换版本键`, async () => {
    const fixture = controllerFixture();
    const { f, controller } = fixture;
    f.writeStatus = http;
    f.writeEnvelope = envelope;
    f.networkFault = network;
    await controller.open(ID);
    await controller.approve();
    assert.equal(controller.snapshot().phase, 'UNKNOWN');
    assert.equal(controller.snapshot().unknownLocked, true);
    safeMessage(controller.snapshot());
    const original = lockEntry(f);
    f.detail.version = 100;
    await controller.refresh();
    await controller.open(ID);
    await controller.approve();
    const reloaded = fixture.make();
    await reloaded.open(ID);
    await reloaded.refresh();
    await reloaded.approve();
    assert.equal(reloaded.snapshot().unknownLocked, true);
    assert.equal(f.writes.length, 1);
    assert.deepEqual(lockEntry(f), original);
    assert.equal(f.storage.removed.length, 0);
  });
}

for (const patch of [
  { requisitionId: OTHER_ID },
  { requisitionId: Number.MAX_SAFE_INTEGER + 1 },
  { requisitionNo: 'OTHER' },
  { status: 'DRAFT' },
  { version: 5 },
  { version: '6' },
  { replayed: undefined },
  { allocations: null },
  { allocations: [] },
  { orderId: ORDER_ID }
]) {
  test('畸形成功信封不能放行：' + JSON.stringify(patch), async () => {
    const { f, controller } = controllerFixture();
    f.writeEnvelope = ok(workflowResult(patch));
    await controller.open(ID);
    await controller.approve();
    assert.equal(controller.snapshot().phase, 'UNKNOWN');
    assert.equal(controller.snapshot().unknownLocked, true);
    lockEntry(f);
  });
}

for (const patch of [
  { orderId: null },
  { orderId: '' },
  { orderId: Number.MAX_SAFE_INTEGER + 1 },
  { orderId: '01' },
  { orderNo: null },
  { orderNo: '' },
  { orderNo: ' PO-001' },
  { version: 6 },
  { replayed: false }
]) {
  test('ORDERED 回查只能确认精确非空订单和原版本，不能推测新订单：' + JSON.stringify(patch), async () => {
    const { f, controller } = controllerFixture();
    f.detail = document({ status: 'ORDERED', version: 7 });
    f.writeEnvelope = ok(workflowResult({ status: 'ORDERED', version: 7, replayed: true, orderId: ORDER_ID, orderNo: 'PO-001', ...patch }));
    await controller.open(ID);
    await controller.convert();
    assert.equal(controller.snapshot().phase, 'UNKNOWN');
    assert.equal(f.writes[0].url, '/erp/pu/requisition/certified/' + ID + '/convert');
  });
}

test('成功后实际详情丢失认证标记，不能按 MRP / AI 来源猜可继续转单', async () => {
  const { f, controller } = controllerFixture();
  f.writeWait = () => {
    f.detail.decisionIdempotencyKey = null;
  };
  await controller.open(ID);
  await controller.approve();
  assert.equal(controller.snapshot().phase, 'SUCCEEDED');
  assert.equal(controller.snapshot().canConvert, false);
  await controller.convert();
  assert.equal(f.writes.length, 1);
});

test('dispatch 前 token 改变：固定 session 最后一跳拒绝，已存意图保留 UNKNOWN', async () => {
  const { f, controller } = controllerFixture();
  await controller.open(ID);
  f.beforeDispatch = (config) => {
    if (config.method === 'post') f.token = TOKEN_B;
  };
  await controller.approve();
  assert.equal(f.writes.length, 0);
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  assert.equal(controller.snapshot().unknownLocked, true);
  lockEntry(f);
});

test('只读返回断点 token 变化 / 同 token 的 tenant 或 actor 漂移均拒绝派发', async () => {
  for (const change of [
    (f) => {
      f.token = TOKEN_B;
    },
    (f) => {
      f.context.tenantId = '000002';
    },
    (f) => {
      f.context.userId = '202';
    }
  ]) {
    const { f, controller } = controllerFixture();
    await controller.open(ID);
    let once = false;
    f.detailWait = () => {
      if (!once) {
        once = true;
        change(f);
      }
    };
    await controller.approve();
    assert.equal(f.writes.length, 0);
    assert.equal(controller.snapshot().canApprove, false);
    safeMessage(controller.snapshot());
  }
});

test('已发请求完成时同 token 的租户/账号漂移不得删除锁或应用响应', async () => {
  for (const field of ['tenantId', 'userId']) {
    const { f, controller } = controllerFixture();
    await controller.open(ID);
    f.writeWait = () => {
      f.context[field] = field === 'tenantId' ? '000002' : '202';
    };
    await controller.approve();
    assert.equal(controller.snapshot().phase, 'UNKNOWN');
    assert.equal(controller.snapshot().result, null);
    lockEntry(f);
  }
});

for (const action of ['invalidate', 'close', 'newScope']) {
  test(`在途 ${action}：迟到成功/拒绝均不能污染新 state，旧文档锁安全留存`, async () => {
    for (const refused of [false, true]) {
      const fixture = controllerFixture();
      const { f, controller } = fixture;
      const entered = deferred();
      const gate = deferred();
      f.writeWait = async () => {
        entered.resolve();
        await gate.promise;
      };
      if (refused) f.writeEnvelope = { code: 409, msg: RAW_ERROR };
      await controller.open(ID);
      const sending = controller.approve();
      await entered.promise;
      const saved = lockEntry(f);
      if (action === 'newScope') {
        f.token = TOKEN_B;
        f.context = { ...context(), tenantId: '000002', userId: '202' };
        f.detail = document({ requisitionId: OTHER_ID });
        await controller.open(OTHER_ID);
      } else controller[action]();
      const before = controller.snapshot();
      gate.resolve();
      await sending;
      assert.deepEqual(controller.snapshot(), before);
      assert.deepEqual(lockEntry(f), saved);
      assert.equal(f.storage.removed.length, 0);
      if (action === 'newScope') {
        assert.equal(controller.snapshot().document.requisitionId, OTHER_ID);
        assert.equal(controller.snapshot().context.userId, '202');
        assert.equal(controller.snapshot().result, null);
      }
    }
  });
}

test('只读迟到 response 不能覆盖后打开的文档/作用域', async () => {
  const { f, controller } = controllerFixture();
  const entered = deferred();
  const gate = deferred();
  let once = false;
  f.detailWait = async () => {
    if (!once) {
      once = true;
      entered.resolve();
      await gate.promise;
    }
  };
  const oldOpen = controller.open(ID);
  await entered.promise;
  f.token = TOKEN_B;
  f.context = { ...context(), tenantId: '000002', userId: '202' };
  f.detail = document({ requisitionId: OTHER_ID });
  await controller.open(OTHER_ID);
  const before = controller.snapshot();
  gate.resolve();
  await oldOpen;
  assert.deepEqual(controller.snapshot(), before);
});

test('多个控制器共享存储：已有 pending / 损坏锁 / 其他版本锁不覆盖不删除', async () => {
  const fixture = controllerFixture();
  const { f, controller } = fixture;
  f.networkFault = true;
  await controller.open(ID);
  await controller.approve();
  const { key } = lockEntry(f);
  for (const raw of [f.storage.getItem(key), '', '{corrupt', JSON.stringify({ expectedVersion: 0 })]) {
    f.storage.setItem(key, raw);
    const second = fixture.make();
    await second.open(ID);
    await second.convert();
    await second.refresh();
    assert.equal(second.snapshot().unknownLocked, true);
    assert.equal(f.storage.getItem(key), raw);
    assert.equal(f.storage.removed.length, 0);
  }
  assert.equal(f.writes.length, 1);
});

test('快照和 onChange 是隔离副本，不能通过外部改动伪造状态/权限', async () => {
  const { controller, changes } = controllerFixture();
  await controller.open(ID);
  const snap = controller.snapshot();
  snap.document.version = 99;
  snap.context.approveAllowed = false;
  changes.at(-1).document.decisionIdempotencyKey = null;
  assert.equal(controller.snapshot().document.version, 5);
  assert.equal(controller.snapshot().canApprove, true);
});

test('成功只读刷新必须采用最后一次 context 的权限提示，不缓存较早提示', async () => {
  const { f, controller } = controllerFixture();
  await controller.open(ID);
  f.detailWait = () => {
    if (f.writes.length) f.context.convertAllowed = false;
  };
  await controller.approve();
  assert.equal(controller.snapshot().phase, 'SUCCEEDED');
  assert.equal(controller.snapshot().context.convertAllowed, false);
  assert.equal(controller.snapshot().canConvert, false);
});

test('清除已确认的自有 intent 后仍须复核 token，作用域漂移恢复旧锁并进入 UNKNOWN', async () => {
  const { f, controller } = controllerFixture();
  await controller.open(ID);
  const remove = f.storage.removeItem;
  f.storage.removeItem = (key) => {
    remove(key);
    f.token = TOKEN_B;
  };
  await controller.approve();
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  assert.equal(controller.snapshot().result, null);
  assert.equal(controller.snapshot().unknownLocked, true);
  lockEntry(f);
});

test('清除已确认 intent 的同步 storage 回调打开新作用域，旧响应仍不能污染新 state', async () => {
  const { f, controller } = controllerFixture();
  await controller.open(ID);
  let newOpen;
  const remove = f.storage.removeItem;
  f.storage.removeItem = (key) => {
    remove(key);
    f.token = TOKEN_B;
    f.context = { ...context(), tenantId: '000002', userId: '202' };
    f.detail = document({ requisitionId: OTHER_ID });
    newOpen = controller.open(OTHER_ID);
  };
  await controller.approve();
  await newOpen;
  assert.equal(controller.snapshot().document.requisitionId, OTHER_ID);
  assert.equal(controller.snapshot().result, null);
  lockEntry(f);
});

test('已确认订单的受控回查必须仍是原订单 ID/编号，不能接受另一订单的成功信封', async () => {
  for (const patch of [{ orderId: OTHER_ID }, { orderNo: 'PO-OTHER' }]) {
    const { f, controller } = controllerFixture();
    f.detail = document({ status: 'APPROVED', version: 6 });
    await controller.open(ID);
    await controller.convert();
    assert.equal(controller.snapshot().result.orderId, ORDER_ID);
    f.writeEnvelope = ok(workflowResult({ status: 'ORDERED', version: 7, replayed: true, orderId: ORDER_ID, orderNo: 'PO-20261009-001', ...patch }));
    await controller.convert();
    assert.equal(controller.snapshot().phase, 'UNKNOWN');
    lockEntry(f);
  }
});

for (const allocation of [
  {},
  { eventId: Number.MAX_SAFE_INTEGER + 1 },
  { occupiedBefore: null },
  { replayed: 'true' },
  { operation: 'TRANSFER_TO_ORDER' }
]) {
  test('畸形账本回执必须 UNKNOWN，不展示猜测的认证事件：' + JSON.stringify(allocation), async () => {
    const { f, controller } = controllerFixture();
    const valid = workflowResult().allocations[0];
    f.writeEnvelope = ok(workflowResult({ allocations: [Object.keys(allocation).length ? { ...valid, ...allocation } : allocation] }));
    await controller.open(ID);
    await controller.approve();
    assert.equal(controller.snapshot().phase, 'UNKNOWN');
    lockEntry(f);
  });
}

test('context 仅暴露四个白名单字段，不把额外服务端认证异常/凭据带进快照', async () => {
  const { f, controller } = controllerFixture();
  f.context = { ...context(), token: TOKEN_A, msg: RAW_ERROR };
  await controller.open(ID);
  assert.deepEqual(controller.snapshot().context, context());
});

test('派发前 setItem 部分成功后抛错：不发送，reload 仍保留原 PENDING 锁', async () => {
  const fixture = controllerFixture();
  const { f, controller } = fixture;
  await controller.open(ID);
  const set = f.storage.setItem;
  f.storage.setItem = (key, value) => {
    set(key, value);
    throw new Error(RAW_ERROR);
  };
  await controller.approve();
  assert.equal(f.writes.length, 0);
  const original = lockEntry(f);
  const reloaded = fixture.make();
  await reloaded.open(ID);
  assert.equal(reloaded.snapshot().unknownLocked, true);
  assert.deepEqual(lockEntry(f), original);
});

test('返回前另一个控制器改写锁：不清除非自有记录，成功也封闭为 UNKNOWN', async () => {
  const { f, controller } = controllerFixture();
  await controller.open(ID);
  f.writeWait = () => {
    const { key } = lockEntry(f);
    f.storage.setItem(key, 'foreign-or-corrupt-lock');
  };
  await controller.approve();
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  assert.equal(lockEntry(f).text, 'foreign-or-corrupt-lock');
  assert.equal(f.storage.removed.length, 0);
});

test('LOADING / SENDING 回调同步 invalidate，不得派发或应用旧响应', async () => {
  for (const phase of ['LOADING', 'SENDING']) {
    const fixture = controllerFixture();
    const { f, controller, deps } = fixture;
    await controller.open(ID);
    deps.onChange = (state) => {
      if (state.phase === phase) controller.invalidate();
    };
    await controller.approve();
    assert.equal(f.writes.length, 0);
    assert.equal(controller.snapshot().phase, 'BLOCKED');
    if (phase === 'SENDING') lockEntry(f);
  }
});

test('读取详情故障不冒充成功详情；已确认写响应及主体仍可显示成功，但不能再写', async () => {
  const { f, controller } = controllerFixture();
  await controller.open(ID);
  f.detailWait = () => {
    if (f.writes.length) throw new Error(RAW_ERROR);
  };
  await controller.approve();
  assert.equal(controller.snapshot().phase, 'SUCCEEDED');
  assert.equal(controller.snapshot().document, null);
  assert.equal(controller.snapshot().canConvert, false);
  assert.equal(controller.snapshot().result.version, 6);
  safeMessage(controller.snapshot());
});

test('请求方法配置前、只读 dispatch 前和只读回包后均复核同一个 session', async () => {
  for (const breakpoint of ['beforeConfig', 'readDispatch', 'readResponse']) {
    const f = apiFixture();
    const session = f.session();
    if (breakpoint === 'beforeConfig') f.token = TOKEN_B;
    if (breakpoint === 'readDispatch')
      f.beforeDispatch = () => {
        f.token = TOKEN_B;
      };
    if (breakpoint === 'readResponse')
      f.contextWait = () => {
        f.token = TOKEN_B;
      };
    await assert.rejects(() => f.api.getCertifiedRequisitionWorkflowContext(session));
    assert.equal(f.calls.length, breakpoint === 'readResponse' ? 1 : 0);
  }
});

test('定向语义类型检查：实际 API/类型/纯控制器使用仓库 tsconfig，不写共享构建输出', () => {
  const configUrl = new URL('../tsconfig.json', import.meta.url);
  const configPath = fileURLToPath(configUrl);
  const base = fileURLToPath(new URL('../', import.meta.url));
  const config = ts.readConfigFile(configPath, ts.sys.readFile);
  assert.equal(config.error, undefined);
  const { options, errors } = ts.parseJsonConfigFileContent(config.config, ts.sys, base);
  assert.deepEqual(errors, []);
  Object.assign(options, { noEmit: true, incremental: false, composite: false, types: [], tsBuildInfoFile: undefined });
  const host = ts.createCompilerHost(options);
  const virtualRequest = fileURLToPath(new URL('../src/utils/__certified_workflow_request_test__.d.ts', import.meta.url));
  const originalRead = host.readFile.bind(host);
  const originalExists = host.fileExists.bind(host);
  host.readFile = (name) =>
    name === virtualRequest
      ? "import type { AxiosInstance } from 'axios'; declare const request: AxiosInstance; export default request;"
      : originalRead(name);
  host.fileExists = (name) => name === virtualRequest || originalExists(name);
  host.resolveModuleNames = (names, containingFile) =>
    names.map((name) =>
      name === '@/utils/request'
        ? { resolvedFileName: virtualRequest, extension: ts.Extension.Dts }
        : ts.resolveModuleName(name, containingFile, options, host).resolvedModule
    );
  const program = ts.createProgram({
    rootNames: [apiUrl, new URL('../src/api/erp/pu/requisition/types.ts', import.meta.url), workflowUrl].map(fileURLToPath),
    options,
    host
  });
  const diagnostics = ts.getPreEmitDiagnostics(program);
  assert.equal(
    diagnostics.length,
    0,
    ts.formatDiagnosticsWithColorAndContext(diagnostics, {
      getCanonicalFileName: (file) => file,
      getCurrentDirectory: () => base,
      getNewLine: () => '\n'
    })
  );
});

test('P2-1 RED：primary 删除后读取和恢复均故障，refresh 不能忘记同文档 UNKNOWN', async () => {
  const fixture = controllerFixture();
  const { f, controller } = fixture;
  await controller.open(ID);
  const get = f.storage.getItem;
  const set = f.storage.setItem;
  const remove = f.storage.removeItem;
  let unavailable = false;
  f.storage.getItem = (key) => {
    if (unavailable) throw new Error(RAW_ERROR);
    return get(key);
  };
  f.storage.setItem = (key, value) => {
    if (unavailable) throw new Error(RAW_ERROR);
    set(key, value);
  };
  f.storage.removeItem = (key) => {
    remove(key);
    unavailable = true;
  };
  await controller.approve();
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  unavailable = false;
  await controller.refresh();
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  assert.equal(controller.snapshot().unknownLocked, true);
  assert.equal(controller.snapshot().canConvert, false);
  f.detail.version = 100;
  controller.close();
  await controller.open(ID);
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  await controller.convert();
  assert.equal(f.writes.length, 1);
  const reloaded = fixture.make();
  await reloaded.open(ID);
  assert.equal(reloaded.snapshot().phase, 'UNKNOWN', '存储恢复后持久栅栏仍保留原文档未知事实');
  assert.equal(reloaded.snapshot().canConvert, false);
});

test('P2-2 RED：ORDERED 回查换成另一条有效 orderLineId 不能确认原回执', async () => {
  const { f, controller } = controllerFixture();
  f.detail = document({ status: 'APPROVED', version: 6 });
  await controller.open(ID);
  await controller.convert();
  const original = controller.snapshot().result;
  assert.equal(controller.snapshot().phase, 'SUCCEEDED');
  f.writeEnvelope = ok({
    ...copy(original),
    replayed: true,
    allocations: original.allocations.map((row) => ({
      ...row,
      orderLineId: '1904',
      replayed: true
    }))
  });
  await controller.convert();
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  assert.equal(controller.snapshot().unknownLocked, true);
  assert.equal(controller.snapshot().result, null);
  lockEntry(f);
});

test('P2-1：存储整体丢失时同实例锁独立于 close/账号/tenant/版本，恢复存储后尽力重新留存', async () => {
  const fixture = controllerFixture();
  const { f, controller } = fixture;
  await controller.open(ID);
  const get = f.storage.getItem;
  const set = f.storage.setItem;
  const remove = f.storage.removeItem;
  let unavailable = false;
  f.storage.getItem = (key) => {
    if (unavailable) throw new Error(RAW_ERROR);
    return get(key);
  };
  f.storage.setItem = (key, value) => {
    if (unavailable) throw new Error(RAW_ERROR);
    set(key, value);
  };
  f.storage.removeItem = (key) => {
    remove(key);
    f.storage.values.clear();
    unavailable = true;
  };
  await controller.approve();
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  assert.equal(f.storage.values.size, 0, '该故障明确丢失所有持久记录，不冒充硬 reload 保证');
  controller.close();
  unavailable = false;
  f.token = TOKEN_B;
  f.context = { ...context(), tenantId: '000002', userId: '202' };
  await controller.open(ID);
  assert.equal(controller.snapshot().phase, 'READY', '旧主体锁不能污染另一 tenant/user');
  f.token = TOKEN_A;
  f.context = context();
  f.detail.version = 200;
  f.detail.decisionIdempotencyKey = 'different-key-cannot-unlock-document';
  await controller.open(ID);
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  await controller.refresh();
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  assert.equal(controller.snapshot().canConvert, false);
  await controller.convert();
  assert.equal(f.writes.length, 1);
  lockEntry(f);
  const reloadedAfterRepair = fixture.make();
  await reloadedAfterRepair.open(ID);
  assert.equal(reloadedAfterRepair.snapshot().phase, 'UNKNOWN', '只在同实例已重新持久留存后核验 reload');
});

test('P2-1：删除 primary 之前已存在可读的无凭证栅栏，完整确认才清自有双记录', async () => {
  const { f, controller } = controllerFixture();
  await controller.open(ID);
  let primary;
  let fencedBeforeRemoval = false;
  f.writeWait = () => {
    primary = lockEntry(f);
  };
  const remove = f.storage.removeItem;
  f.storage.removeItem = (key) => {
    if (key === primary.key) {
      const guards = [...f.storage.values].filter(([storedKey]) => storedKey !== primary.key);
      fencedBeforeRemoval = guards.length === 1 && guards[0][1] === primary.text;
      assert.ok(guards.every(([storedKey, value]) => !storedKey.includes(TOKEN_A) && !value.includes(TOKEN_A)));
    }
    remove(key);
  };
  await controller.approve();
  assert.equal(controller.snapshot().phase, 'SUCCEEDED');
  assert.equal(fencedBeforeRemoval, true);
  assert.equal(f.storage.values.size, 0);
});

for (const fault of ['set', 'silentSet', 'get']) {
  test(`P2-1：删除前栅栏 ${fault} 故障不能先删除唯一 primary`, async () => {
    const { f, controller } = controllerFixture();
    await controller.open(ID);
    let primary;
    const set = f.storage.setItem;
    const get = f.storage.getItem;
    f.writeWait = () => {
      primary = lockEntry(f);
    };
    f.storage.setItem = (key, value) => {
      if (primary && key !== primary.key) {
        if (fault === 'set') throw new Error(RAW_ERROR);
        if (fault === 'silentSet') return;
      }
      set(key, value);
    };
    f.storage.getItem = (key) => {
      if (fault === 'get' && primary && key !== primary.key) throw new Error(RAW_ERROR);
      return get(key);
    };
    await controller.approve();
    assert.equal(controller.snapshot().phase, 'UNKNOWN');
    assert.equal(controller.snapshot().canConvert, false);
    assert.equal(get(primary.key), primary.text);
    assert.equal(f.storage.removed.length, 0);
  });
}

test('P2-1：最后栅栏删除后 get 故障但 set 可用时尽力恢复，恢复读后新实例也 UNKNOWN', async () => {
  const fixture = controllerFixture();
  const { f, controller } = fixture;
  await controller.open(ID);
  let primary;
  let readUnavailable = false;
  const get = f.storage.getItem;
  const remove = f.storage.removeItem;
  f.writeWait = () => {
    primary = lockEntry(f);
  };
  f.storage.getItem = (key) => {
    if (readUnavailable) throw new Error(RAW_ERROR);
    return get(key);
  };
  f.storage.removeItem = (key) => {
    remove(key);
    if (key !== primary.key) readUnavailable = true;
  };
  await controller.approve();
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  assert.equal(controller.snapshot().unknownLocked, true);
  lockEntry(f);
  readUnavailable = false;
  const reloaded = fixture.make();
  await reloaded.open(ID);
  await reloaded.refresh();
  assert.equal(reloaded.snapshot().phase, 'UNKNOWN');
  assert.equal(reloaded.snapshot().canConvert, false);
});

test('P2-1：UNKNOWN 外部删除记录后，同实例 readonly refresh 仍锁存并尽力恢复记录', async () => {
  const fixture = controllerFixture();
  const { f, controller } = fixture;
  f.networkFault = true;
  await controller.open(ID);
  await controller.approve();
  const saved = lockEntry(f);
  f.storage.values.clear();
  await controller.refresh();
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  assert.deepEqual(lockEntry(f), saved);
  controller.close();
  await controller.open(ID);
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  assert.equal(f.writes.length, 1);
});

for (const field of ['eventId', 'allocationId', 'requisitionLineId', 'orderLineId', 'actorId']) {
  test(`P2-2：同头同版本 ORDERED 回查改变原 ${field} 仍须 UNKNOWN 保留锁`, async () => {
    const { f, controller } = controllerFixture();
    f.detail = document({ status: 'APPROVED', version: 6 });
    await controller.open(ID);
    await controller.convert();
    const original = controller.snapshot().result;
    await controller.refresh();
    f.writeEnvelope = ok({
      ...copy(original),
      replayed: true,
      allocations: original.allocations.map((row) => ({
        ...row,
        [field]: '1901',
        replayed: true
      }))
    });
    await controller.convert();
    assert.equal(controller.snapshot().phase, 'UNKNOWN');
    assert.equal(controller.snapshot().result, null);
    assert.equal(f.storage.removed.length, 2, '只有首次成功操作清记录，异常回查不能清新 intent');
    lockEntry(f);
    safeMessage(controller.snapshot());
  });
}

/** 两条真实形状回执，供回查集合绑定检查；不计算或汇总业务数量。 */
async function knownMultiAllocationFixture() {
  const fixture = controllerFixture();
  const { f, controller } = fixture;
  f.detail = document({ status: 'APPROVED', version: 6 });
  await controller.open(ID);
  const result = workflowResult({ status: 'ORDERED', version: 7, orderId: ORDER_ID, orderNo: 'PO-20261009-001' });
  result.allocations.push({ ...result.allocations[0], eventId: '911', allocationId: '912', requisitionLineId: '913', orderLineId: '914' });
  f.writeEnvelope = ok(result);
  f.writeWait = () => {
    f.detail = { ...f.detail, status: 'ORDERED', version: 7 };
  };
  await controller.convert();
  assert.equal(controller.snapshot().phase, 'SUCCEEDED');
  delete f.writeWait;
  return fixture;
}

test('P2-2：稳定原回执集合不因顺序、safe ID 表示或 replayed 变化拒绝正常同单回查', async () => {
  const { f, controller } = await knownMultiAllocationFixture();
  const original = controller.snapshot().result;
  f.writeEnvelope = ok({
    ...copy(original),
    replayed: true,
    allocations: [...original.allocations].reverse().map((row) => ({
      ...row,
      eventId: Number(row.eventId),
      allocationId: Number(row.allocationId),
      requisitionLineId: Number(row.requisitionLineId),
      orderLineId: Number(row.orderLineId),
      actorId: Number(row.actorId),
      replayed: true
    }))
  });
  await controller.convert();
  assert.equal(controller.snapshot().phase, 'SUCCEEDED');
  assert.equal(controller.snapshot().result.orderId, ORDER_ID);
  assert.equal(controller.snapshot().result.replayed, true);
  assert.deepEqual(f.writes.at(-1).data, { expectedVersion: 7 });
  assert.equal(f.storage.values.size, 0);
});

test('P2-2：回查不能遗漏已确认集合中的另一条有效原回执', async () => {
  const { f, controller } = await knownMultiAllocationFixture();
  const original = controller.snapshot().result;
  f.writeEnvelope = ok({ ...copy(original), replayed: true, allocations: [original.allocations[0]] });
  await controller.convert();
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  lockEntry(f);
});

/** 使用实际生产策略的33字符枚举，不能用缩短的COMMITMENT夹具制造假绿。 */
test('实际TRANSFER_TO_ORDER回执33字符状态必须识别为明确成功', async () => {
  const f = apiFixture();
  f.detail = document({ status: 'ORDERED', version: 7 });
  f.writeEnvelope = ok(workflowResult({ status: 'ORDERED', version: 7, replayed: true, orderId: ORDER_ID, orderNo: 'PO-CANONICAL' }));
  assert.equal(f.writeEnvelope.data.allocations[0].toStatus, 'ORDER_COMMITTED_NOT_TIMELY_SUPPLY');
  assert.equal(f.writeEnvelope.data.allocations[0].toStatus.length, 33);
  const { controller } = controllerFixture(f);
  await controller.open(ID);
  await controller.convert();
  assert.equal(controller.snapshot().phase, 'SUCCEEDED');
});
for (const value of ['COMMITMENT', 'ORDER_COMMITTED', 'RELEASED', 'REQUEST_INTENT']) {
  test(`TRANSFER_TO_ORDER不能用非原生状态${value}冒充回执`, async () => {
    const f = apiFixture();
    f.detail = document({ status: 'ORDERED', version: 7 });
    const result = workflowResult({ status: 'ORDERED', version: 7, replayed: true, orderId: ORDER_ID, orderNo: 'PO-CANONICAL' });
    result.allocations[0].toStatus = value;
    f.writeEnvelope = ok(result);
    const { controller } = controllerFixture(f);
    await controller.open(ID);
    await controller.convert();
    assert.equal(controller.snapshot().phase, 'UNKNOWN');
    assert.equal(controller.snapshot().unknownLocked, true);
  });
}

test('P2-1首次读到primary后fence抛错，已知UNKNOWN不能被同实例refresh或reopen抹掉', async () => {
  const fixture = controllerFixture();
  const { f, controller } = fixture;
  await controller.open(ID);
  f.writeEnvelope = { code: 500, msg: RAW_ERROR };
  await controller.approve();
  assert.equal(controller.snapshot().phase, 'UNKNOWN');
  const original = lockEntry(f);
  const fresh = fixture.make();
  const get = f.storage.getItem;
  let fault = true;
  f.storage.getItem = (key) => {
    if (fault && key.endsWith(':release-fence')) throw new Error(RAW_ERROR);
    return get(key);
  };
  await fresh.open(ID);
  assert.equal(fresh.snapshot().unknownLocked, true, 'primary已明确存在，后续fence故障不能丢其事实');
  f.storage.values.clear();
  fault = false;
  await fresh.refresh();
  assert.equal(fresh.snapshot().phase, 'UNKNOWN');
  assert.equal(fresh.snapshot().canApprove, false);
  assert.equal(f.storage.values.get(original.key), original.text, '恢复后仅尽力补回原记录');
  fresh.close();
  f.storage.values.clear();
  await fresh.open(ID);
  assert.equal(fresh.snapshot().phase, 'UNKNOWN');
  await fresh.approve();
  assert.equal(f.writes.length, 1, '只读恢复不能重新派发原事务');
});
