/** 仓储认证离线回归：真实 TS/API 被执行，网络/共享传输/存储只作为隔离边界。 */
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { test } from 'node:test';
import ts from 'typescript';
import axios from 'axios';

export function loadTS(path, modules = {}) {
  const source = readFileSync(new URL(path, import.meta.url), 'utf8');
  const { outputText, diagnostics } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    reportDiagnostics: true
  });
  assert.deepEqual(
    diagnostics.filter((d) => d.category === ts.DiagnosticCategory.Error),
    []
  );
  const exports = {};
  new Function('exports', 'require', outputText)(exports, (name) => {
    assert.ok(Object.hasOwn(modules, name), `禁止未隔离依赖 ${name}`);
    return modules[name];
  });
  return exports;
}
export const ID = '9007199254740993';
export const LINE = '9007199254740995';
export function snapshot(kind = 'ORDER', domain = 'arrivalnotice', patch = {}) {
  return {
    domain,
    kind,
    documentId: ID,
    protection: 'CERTIFIED',
    sourceComplete: true,
    flowState: kind === 'ORDER' ? 'NOT_APPLICABLE' : 'NOT_STARTED',
    order: {
      tenantId: '000001',
      orderId: ID,
      orderNo: 'offline-order',
      status: 'APPROVED',
      version: 2,
      selectFlowId: null,
      termsId: LINE,
      delFlag: '0'
    },
    orderLines: [
      {
        tenantId: '000001',
        lineId: LINE,
        orderId: ID,
        materialId: '3',
        baseUomId: '4',
        orderQuantity: '12.0000',
        receivedQuantity: '0.0000',
        delFlag: '0'
      }
    ],
    native:
      kind === 'ORDER'
        ? null
        : { tenantId: '000001', [kind === 'NOTICE' ? 'noticeId' : 'receiveId']: ID, status: kind === 'NOTICE' ? 'DRAFT' : 'APPROVED', delFlag: '0' },
    lines:
      kind === 'ORDER'
        ? []
        : [{ tenantId: '000001', lineId: LINE, materialId: '3', baseUomId: '4', purchaseLineId: LINE, quantity: '2.0000', delFlag: '0' }],
    iqcEvidence:
      kind === 'RECEIVE'
        ? [
            {
              tenantId: '000001',
              receiveId: ID,
              materialId: '3',
              baseUomId: '4',
              inspectedQuantity: '2.0000',
              sourceKind: 'MES_QC_IQC_RECEIVE_MATERIAL_V2',
              inspectResult: 'QUALIFIED',
              birthId: '8',
              birthSha256: 'c'.repeat(64),
              evidenceSha256: 'd'.repeat(64)
            }
          ]
        : [],
    births:
      kind === 'RECEIVE'
        ? [{ tenantId: '000001', receiveId: ID, materialId: '3', baseUomId: '4', quantity: '2.0000', birthId: '8', birthSha256: 'c'.repeat(64) }]
        : [],
    details:
      kind === 'RECEIVE'
        ? [{ tenantId: '000001', lineId: LINE, batchId: '8', warehouseId: '5', areaId: '6', locationId: '7', quantity: '2.0000', delFlag: '0' }]
        : [],
    ...patch
  };
}
export function helperPort() {
  const norm = (value) =>
    typeof value === 'string' && /^[1-9][0-9]{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n
      ? value
      : typeof value === 'number' && Number.isSafeInteger(value) && value > 0
        ? String(value)
        : null;
  return {
    normalizeBusinessId: norm,
    normalizeBusinessVersion: (value) => (typeof value === 'number' && Number.isInteger(value) && value >= 0 && value < 2147483647 ? value : null),
    createBusinessSession(readCredential, assertScope) {
      const token = readCredential();
      return {
        token,
        assertCurrent() {
          assert.ok(token && token === readCredential(), '会话变化');
          assertScope?.();
        }
      };
    },
    createBusinessIntentStore(storage) {
      const key = (scope) => JSON.stringify(scope);
      return {
        isBlocked(scope) {
          try {
            return storage.getItem(key(scope)) !== null;
          } catch {
            return true;
          }
        },
        begin(scope, operation, requestKey) {
          if (this.isBlocked(scope)) throw Error('阻断');
          const intent = { scope, operation, requestKey, nonce: 'offline-nonce' };
          storage.setItem(key(scope), JSON.stringify(intent));
          if (!storage.getItem(key(scope))) throw Error('持久失败');
          return intent;
        },
        assertPending(intent) {
          assert.equal(storage.getItem(key(intent.scope)), JSON.stringify(intent));
        },
        release(intent) {
          this.assertPending(intent);
          storage.removeItem(key(intent.scope));
          assert.equal(storage.getItem(key(intent.scope)), null);
        }
      };
    }
  };
}
export function fixture(options = {}) {
  const map = options.map ?? new Map();
  const storage = options.storage ?? { getItem: (k) => map.get(k) ?? null, setItem: (k, v) => map.set(k, v), removeItem: (k) => map.delete(k) };
  const log = [];
  let token = 'offline-fixed-token';
  let routeKey = 'warehouse-a';
  let detail = snapshot(options.kind, options.domain, options.patch);
  const scope = { tenantId: '000001', userId: '101', routeKey };
  const helper = options.realHelper
    ? loadTS('../src/utils/certified-business.ts', { '@/utils/request': { default: { defaults: {} } } })
    : helperPort();
  const workflow = loadTS('../src/views/erp/wm/certified/warehouseWorkflow.ts', { '@/utils/certified-business': helper });
  const ports = {
    domain: options.domain ?? 'arrivalnotice',
    storage,
    readCredential: () => token,
    readScope: () => ({ ...scope, routeKey }),
    hasPermission: (p) => (p === 'wm:arrivalnotice:query' ? options.noticePermission !== false : options.permission !== false),
    context: async (session) => {
      log.push('context');
      session.assertCurrent();
      return {
        code: 200,
        data: {
          ...scope,
          domain: ports.domain,
          username: 'offline',
          deptId: null,
          databaseDate: '2026-10-09',
          createAllowed: true,
          submitAllowed: true,
          shelfAllowed: true,
          executeAllowed: true
        }
      };
    },
    detail: async (kind, id, session) => {
      log.push('detail');
      session.assertCurrent();
      const post = log.findLast((x) => typeof x === 'object');
      if (post && !options.read) {
        const after = snapshot(kind, ports.domain);
        after.native = {
          ...after.native,
          status: kind === 'NOTICE' || post.operation === 'createReceive' ? 'DRAFT' : 'SHELVED',
          receiveCode: 'offline-receipt',
          noticeId: post.operation === 'createReceive' ? (post.body?.noticeId ?? null) : ID,
          purId: ID,
          createBy: '101',
          shelfBy: '101',
          shelfTime: '2026-10-09 10:00:00',
          noticeCode: 'offline-notice',
          noticeName: post.body?.noticeName,
          arrivalDate: post.body?.arrivalDate
        };
        after.creations = [
          {
            tenantId: '000001',
            actorId: '101',
            requestKey: post.body?.requestKey,
            orderId: ID,
            orderVersion: post.body?.expectedOrderVersion,
            purchaseLineId: LINE,
            receiveId: ID,
            receiveCode: 'offline-receipt',
            lineId: LINE,
            warehouseId: post.body?.warehouseId,
            areaId: post.body?.areaId,
            locationId: post.body?.locationId,
            arrivalDate: post.body?.actualArrivalDate,
            noticeId: post.operation === 'createReceive' ? (post.body?.noticeId ?? null) : ID,
            noticeLineId: post.operation === 'createReceive' ? (post.body?.noticeLineId ?? null) : LINE,
            materialId: '3',
            baseUomId: '4',
            quantity: post.body?.quantity,
            requestHash: 'b'.repeat(64)
          }
        ];
        after.lines[0].lineId = LINE;
        after.lines[0].noticeId = ID;
        after.lines[0].receiveId = ID;
        after.lines[0].noticeLineId = post.operation === 'createReceive' ? (post.body?.noticeLineId ?? null) : LINE;
        after.flowState = kind === 'NOTICE' || post.operation === 'createReceive' ? 'NOT_STARTED' : 'APPROVED';
        if (post.operation === 'submitNotice' || post.operation === 'synchronizeNotice') {
          after.flowState = post.operation === 'submitNotice' ? 'APPROVING' : 'APPROVED';
          after.native.status = after.flowState;
          after.workflow = {
            tenantId: '000001',
            dataId: ID,
            initiatorName: 'offline',
            processInstanceId: 'offline-instance',
            processDefinitionId: 'offline-definition:5:9',
            nextStatus: after.flowState
          };
        }
        if (post.operation === 'executeReceive') {
          after.native.status = 'FINISHED';
          after.native.updateBy = '101';
          after.posting = {
            tenantId: '000001',
            receiveId: ID,
            actorId: '101',
            materialId: '3',
            quantity: '2.0000',
            sourceFingerprint: 'a'.repeat(64)
          };
        }
        options.mutateReadback?.(after, post);
        return { code: 200, data: { ...after, ...options.readbackPatch } };
      }
      return options.read ? options.read(kind, id) : { code: 200, data: structuredClone(detail) };
    },
    bindingContext: async () => {
      log.push('notice-context');
      return { code: 200, data: { ...scope, domain: 'arrivalnotice' } };
    },
    bindingDetail: async (noticeId) => {
      log.push('notice-detail');
      return {
        code: 200,
        data: snapshot('NOTICE', 'arrivalnotice', {
          documentId: noticeId,
          flowState: 'APPROVED',
          native: { tenantId: '000001', noticeId, status: 'APPROVED', delFlag: '0' },
          lines: [
            { tenantId: '000001', noticeId, lineId: '21', purchaseLineId: LINE, materialId: '3', baseUomId: '4', quantity: '2.0000', delFlag: '0' }
          ]
        })
      };
    },
    confirm: async () => {
      log.push('confirm');
      return options.confirm ? options.confirm() : true;
    },
    write: async (operation, id, body, session, flowId) => {
      log.push({ operation, id, body, flowId, token: session.token });
      return options.write
        ? options.write(operation, id, body)
        : {
            code: 200,
            data:
              operation === 'createNotice'
                ? { noticeId: ID, noticeCode: 'offline-notice', noticeLineId: LINE, status: 'DRAFT', replayed: false }
                : undefined
          };
    },
    recovery: async () => {
      log.push('recovery');
      return {
        code: 200,
        data: {
          receiveId: ID,
          orderId: ID,
          materialId: '3',
          quantity: '2.0000',
          sourceFingerprint: 'a'.repeat(64),
          notificationStatus: 'PENDING',
          consumptionState: 'PENDING',
          postedAt: '2026-10-09',
          consumptionEvents: []
        }
      };
    }
  };
  const controller = workflow.createWarehouseWorkflow(ports);
  controller.setTarget(options.kind ?? 'ORDER', ID);
  return { controller, ports, log, map, setToken: (v) => (token = v), setRoute: (v) => (routeKey = v), setDetail: (v) => (detail = v) };
}
export const input = () => ({
  requestKey: 'offline-key-1',
  purchaseLineId: LINE,
  quantity: '2.0000',
  noticeName: '真实通知名称',
  arrivalDate: '2026-10-09',
  actualArrivalDate: '2026-10-09',
  warehouseId: '5',
  areaId: '6',
  locationId: '7',
  remark: '',
  materialsConfirmed: true,
  flowId: '29'
});

// API 导出 RED 在现有真实文件上断言，不能用模块缺失冒充行为失败。
test('实际 wm API 暴露分领域 context/detail 与既有 POST 合同', async () => {
  const calls = [];
  const transport = {
    readCertifiedBusiness: async (...args) => {
      calls.push(['GET', ...args]);
      return { code: 200 };
    },
    postCertifiedBusiness: async (...args) => {
      calls.push(['POST', ...args]);
      return { code: 200 };
    },
    ...helperPort()
  };
  const a = loadTS('../src/api/erp/wm/arrivalnotice/index.ts', {
    '@/utils/request': { default: () => assert.fail('不得调用真 request') },
    '@/utils/certified-business': transport
  });
  const traceModel = loadTS('../src/components/material-trace/model.ts');
  const traceDocument = loadTS('../src/api/erp/wm/identity/document.ts', {
    '@/utils/request': { default: () => assert.fail('认证流程不得调用普通追溯 request') },
    '@/components/material-trace/model': traceModel
  });
  const r = loadTS('../src/api/erp/wm/receive/index.ts', {
    '@/api/erp/wm/identity/document': traceDocument,
    '@/utils/request': { default: () => assert.fail('不得调用真 request') },
    '@/utils/certified-business': transport
  });
  for (const name of [
    'getCertifiedArrivalContext',
    'getCertifiedArrivalDetail',
    'createCertifiedArrival',
    'submitCertifiedArrival',
    'synchronizeCertifiedArrival'
  ])
    assert.equal(typeof a[name], 'function', name);
  for (const name of [
    'getCertifiedReceiveContext',
    'getCertifiedReceiveDetail',
    'createCertifiedReceiveDraft',
    'shelfCertifiedReceive',
    'executeCertifiedReceive',
    'recoverCertifiedReceive'
  ])
    assert.equal(typeof r[name], 'function', name);
  const session = { token: 'offline', assertCurrent() {} };
  await a.submitCertifiedArrival(ID, 'flow:/a?&=', session);
  await r.shelfCertifiedReceive(ID, session);
  await r.executeCertifiedReceive(ID, session);
  await r.recoverCertifiedReceive(ID, session);
  assert.equal(calls[0][1], `/erp/wm/arrivalnotice/certified/${ID}/submit?selectFlowId=flow%3A%2Fa%3F%26%3D`);
  assert.equal(calls[0][2], undefined);
  assert.equal(calls[1][1], `/erp/wm/receive/certified/shelf/${ID}`);
  assert.equal(calls[2][1], `/erp/wm/receive/certified/execute/${ID}`);
  assert.equal(calls[3][0], 'GET');
});

const path = new URL('../src/views/erp/wm/certified/warehouseWorkflow.ts', import.meta.url);
test('新增真实仓储控制器必须存在', () => assert.ok(existsSync(path), '缺少独立仓储状态控制器'));
if (existsSync(path)) {
  test('每次写先 fresh context + 完整 native detail，再独立 confirm，仅创建通知', async () => {
    const f = fixture();
    await f.controller.inspect();
    await f.controller.inspect();
    f.log.length = 0;
    assert.equal(await f.controller.perform('createNotice', input()), true);
    assert.deepEqual(f.log.slice(0, 3), ['context', 'detail', 'confirm']);
    const posts = f.log.filter((x) => typeof x === 'object');
    assert.equal(posts.length, 1);
    assert.deepEqual(posts[0].body, {
      requestKey: 'offline-key-1',
      orderId: ID,
      expectedOrderVersion: 2,
      purchaseLineId: LINE,
      quantity: '2.0000',
      noticeName: '真实通知名称',
      arrivalDate: '2026-10-09',
      remark: ''
    });
    assert.equal(f.map.size, 0);
    assert.equal(JSON.stringify([...f.map]).includes('offline-fixed-token'), false);
  });
  for (const [label, patch] of [
    ['NULL version', { order: { ...snapshot().order, version: null } }],
    ['旧born version', { order: { ...snapshot().order, version: 0 } }],
    ['非认证', { protection: 'LEGACY' }],
    ['来源坏', { sourceComplete: false }],
    ['多行不明确', { orderLines: [] }]
  ]) {
    test(`${label} 不猜来源、不 POST`, async () => {
      const f = fixture({ patch });
      await f.controller.inspect();
      await f.controller.perform('createNotice', input());
      assert.equal(
        f.log.some((x) => typeof x === 'object'),
        false
      );
    });
  }
  test('quantity 不使用订购量默认、必需真实材料及实际日期', async () => {
    for (const patch of [{ quantity: '' }, { quantity: '1e2' }, { materialsConfirmed: false }, { arrivalDate: '' }, { purchaseLineId: Number(ID) }]) {
      const f = fixture();
      await f.controller.inspect();
      await f.controller.perform('createNotice', { ...input(), ...patch });
      assert.equal(
        f.log.some((x) => typeof x === 'object'),
        false
      );
    }
  });
  test('权限为提示但 query-only 绝不写', async () => {
    const f = fixture({ permission: false });
    await f.controller.inspect();
    await f.controller.inspect();
    assert.ok(f.controller.state.detail);
    await f.controller.perform('createNotice', input());
    assert.equal(
      f.log.some((x) => typeof x === 'object'),
      false
    );
  });
  test('双击、5xx 后持久 UNKNOWN，刷新及换key不可解锁', async () => {
    let resolve;
    const pending = new Promise((done) => (resolve = done));
    const f = fixture({ write: () => pending });
    await f.controller.inspect();
    const first = f.controller.perform('createNotice', input());
    await new Promise((done) => setImmediate(done));
    await f.controller.perform('createNotice', input());
    resolve({ code: 500 });
    await first;
    await f.controller.inspect();
    await f.controller.perform('createNotice', { ...input(), requestKey: 'different-key' });
    assert.equal(f.log.filter((x) => typeof x === 'object').length, 1);
    assert.equal(f.controller.state.blocked, true);
    const reload = fixture({ map: f.map });
    await reload.controller.inspect();
    await reload.controller.perform('createNotice', input());
    assert.equal(
      reload.log.some((x) => typeof x === 'object'),
      false
    );
  });
  for (const [label, write] of [
    [
      '网络',
      () => {
        throw Error('offline-fixed-token raw secret');
      }
    ],
    ['畸形200', () => ({ code: 200, data: {} })],
    ['重放结果先对账', () => ({ code: 200, data: { noticeId: ID, noticeCode: 'n', noticeLineId: LINE, status: 'DRAFT', replayed: true } })]
  ])
    test(`${label} 锁 UNKNOWN 且不外显异常原文`, async () => {
      const f = fixture({ write });
      await f.controller.inspect();
      await f.controller.perform('createNotice', input());
      assert.equal(f.controller.state.blocked, true);
      assert.equal(f.controller.state.message.includes('offline-fixed-token'), false);
    });
  test('收货可选通知引用必须手填成对并独立 arrival query fresh detail，不借 receive query', async () => {
    const result = {
      receiveId: ID,
      receiveCode: 'offline-receipt',
      lineId: LINE,
      purchaseLineId: LINE,
      materialId: '3',
      baseUomId: '4',
      quantity: '2.0000',
      status: 'DRAFT_CREATED',
      replayed: false,
      requestHash: 'b'.repeat(64)
    };
    const f = fixture({ domain: 'receive', write: () => ({ code: 200, data: result }) });
    await f.controller.inspect();
    assert.equal(await f.controller.perform('createReceive', { ...input(), noticeId: '19', noticeLineId: '21' }), true);
    assert.ok(f.log.includes('notice-context') && f.log.includes('notice-detail'));
    const post = f.log.find((x) => typeof x === 'object');
    assert.equal(post.body.noticeId, '19');
    assert.equal(post.body.noticeLineId, '21');
    const denied = fixture({ domain: 'receive', noticePermission: false });
    await denied.controller.inspect();
    await denied.controller.perform('createReceive', { ...input(), noticeId: '19', noticeLineId: '21' });
    assert.equal(
      denied.log.some((x) => typeof x === 'object'),
      false
    );
  });
  test('成功必须核对原生源行/请求键/actor，不以 code200 释放', async () => {
    for (const patch of [
      { creations: [{ actorId: '999', requestKey: 'offline-key-1' }] },
      { native: { ...snapshot('NOTICE').native, createBy: '999' } },
      { lines: [] },
      { order: { ...snapshot().order, orderId: '99' } }
    ]) {
      const f = fixture({ readbackPatch: patch });
      await f.controller.inspect();
      await f.controller.perform('createNotice', input());
      assert.equal(f.controller.state.blocked, true);
      assert.equal(f.map.size, 1);
    }
  });
  test('storage 故障封闭、不得派发 POST', async () => {
    const f = fixture({
      storage: {
        getItem() {
          throw Error('storage');
        }
      }
    });
    await f.controller.inspect();
    await f.controller.perform('createNotice', input());
    assert.equal(
      f.log.some((x) => typeof x === 'object'),
      false
    );
  });
  test('confirmation 待回调换路由/账号/对象，不跨对象写', async () => {
    for (const change of [(f) => f.setToken('changed'), (f) => f.setRoute('warehouse-b'), (f) => f.controller.setTarget('ORDER', '99')]) {
      let resolve;
      const f = fixture({ confirm: () => new Promise((done) => (resolve = done)) });
      await f.controller.inspect();
      const first = f.controller.perform('createNotice', input());
      await new Promise((done) => setImmediate(done));
      change(f);
      resolve(true);
      await first;
      assert.equal(
        f.log.some((x) => typeof x === 'object'),
        false
      );
    }
  });
  test('收货缺 IQC V2 或永久出生事实时只读，不用写 API 探测或追认旧born', async () => {
    for (const patch of [
      { iqcEvidence: [] },
      { births: [] },
      { iqcEvidence: [{ sourceKind: 'MES_QC_IQC_RECEIVE_MATERIAL_V1', inspectResult: 'QUALIFIED' }] }
    ]) {
      const f = fixture({ kind: 'RECEIVE', domain: 'receive', patch: { flowState: 'APPROVED', ...patch } });
      await f.controller.inspect();
      await f.controller.perform('shelfReceive', input());
      assert.equal(
        f.log.some((x) => typeof x === 'object'),
        false
      );
    }
  });
  test('通知 Flowable 未知禁止 submit/sync；原单成功历史禁止 shelf/execute replay', async () => {
    const f = fixture({ kind: 'NOTICE', patch: { flowState: 'UNKNOWN' } });
    await f.controller.inspect();
    await f.controller.perform('submitNotice', input());
    await f.controller.perform('synchronizeNotice', input());
    assert.equal(
      f.log.some((x) => typeof x === 'object'),
      false
    );
    const r = fixture({
      kind: 'RECEIVE',
      domain: 'receive',
      patch: { flowState: 'APPROVED', native: { ...snapshot('RECEIVE', 'receive').native, status: 'FINISHED' } }
    });
    await r.controller.inspect();
    await r.controller.perform('executeReceive', input());
    assert.equal(
      r.log.some((x) => typeof x === 'object'),
      false
    );
  });
  test('recovery 仅 GET，不release、不 POST、不MQ，UNKNOWN 保留', async () => {
    const f = fixture({ kind: 'RECEIVE', domain: 'receive', patch: { flowState: 'APPROVED' }, write: () => ({ code: 500 }) });
    await f.controller.inspect();
    await f.controller.perform('shelfReceive', input());
    const before = JSON.stringify([...f.map]);
    await f.controller.recover();
    assert.equal(JSON.stringify([...f.map]), before);
    assert.equal(f.log.filter((x) => typeof x === 'object').length, 1);
    assert.ok(f.controller.state.recovery);
  });
}

if (existsSync(path)) {
  test('用户已 reviewed A，但 fresh context/detail 为 B 不能静默采用新版本/状态/源行/报价指纹', async () => {
    for (const patch of [
      { order: { ...snapshot().order, version: 3 } },
      { order: { ...snapshot().order, status: 'PARTIALLY_RECEIVED' } },
      { orderLines: [{ ...snapshot().orderLines[0], materialId: '99' }] },
      { approvals: [{ actorId: '101', quoteHash: 'b'.repeat(64) }] }
    ]) {
      const f = fixture();
      await f.controller.inspect();
      f.setDetail(snapshot('ORDER', 'arrivalnotice', patch));
      await f.controller.perform('createNotice', input());
      assert.equal(
        f.log.some((x) => typeof x === 'object'),
        false
      );
      assert.equal(f.controller.state.detail, null);
    }
  });
  test('confirm 已显示 A，确认等待期间原生事实变更为 B，第二次 fresh 检测后不 POST', async () => {
    let resolve;
    const f = fixture({ confirm: () => new Promise((done) => (resolve = done)) });
    await f.controller.inspect();
    const action = f.controller.perform('createNotice', input());
    await new Promise((done) => setImmediate(done));
    f.setDetail(snapshot('ORDER', 'arrivalnotice', { order: { ...snapshot().order, version: 3 } }));
    resolve(true);
    await action;
    assert.equal(
      f.log.some((x) => typeof x === 'object'),
      false
    );
    assert.equal(f.map.size, 0);
    assert.equal(f.controller.state.detail, null);
  });
}

/** 真正共享 helper + 真正 wm API + 真正控制器；只替换 Axios adapter 与 Storage，不替换传输语义。 */
for (const failure of [null, 403, 500])
  test(`真实 helper 整合：${failure ?? '成功'} body/参数/固定会话/持久意图边界`, async () => {
    const values = new Map();
    const storage = { getItem: (k) => values.get(k) ?? null, setItem: (k, v) => values.set(k, v), removeItem: (k) => values.delete(k) };
    let posted = false;
    const calls = [];
    const scope = { tenantId: '000001', userId: '105', routeKey: 'offline-real-helper' };
    const after = snapshot('NOTICE');
    after.native = { ...after.native, noticeCode: 'offline-notice', noticeName: '真实通知名称', arrivalDate: '2026-10-09', createBy: '105' };
    after.creations = [
      {
        tenantId: '000001',
        actorId: '105',
        requestKey: 'offline-key-1',
        orderId: ID,
        orderVersion: 2,
        purchaseLineId: LINE,
        noticeId: ID,
        noticeLineId: LINE,
        materialId: '3',
        baseUomId: '4',
        quantity: '2.0000'
      }
    ];
    const request = axios.create({
      adapter: async (config) => {
        calls.push({ method: config.method, url: config.url, body: config.data });
        assert.equal(config.headers.Authorization, 'Bearer offline-real-credential');
        let body;
        if (config.method === 'post') {
          assert.ok(
            [...values].some(([k]) => !k.endsWith(':release-fence')),
            '持久意图必须先于 POST adapter'
          );
          posted = true;
          assert.deepEqual(JSON.parse(config.data), {
            requestKey: 'offline-key-1',
            orderId: ID,
            expectedOrderVersion: 2,
            purchaseLineId: LINE,
            quantity: '2.0000',
            noticeName: '真实通知名称',
            arrivalDate: '2026-10-09',
            remark: ''
          });
          body = failure
            ? { code: failure, msg: '不传播 offline-real-credential' }
            : { code: 200, data: { noticeId: ID, noticeCode: 'offline-notice', noticeLineId: LINE, status: 'DRAFT', replayed: false } };
        } else
          body = {
            code: 200,
            data: config.url.endsWith('/workflow-context')
              ? { ...scope, domain: 'arrivalnotice', createAllowed: true, username: 'offline', databaseDate: '2026-10-09' }
              : posted
                ? after
                : snapshot()
          };
        return { status: 200, statusText: 'OK', headers: {}, config, data: JSON.stringify(body) };
      }
    });
    // 与生产 request 的 R 解包边界一致；HTTP adapter 仍完全离线。
    request.interceptors.response.use((response) => response.data);
    const helper = loadTS('../src/utils/certified-business.ts', { '@/utils/request': { default: request } });
    const api = loadTS('../src/api/erp/wm/arrivalnotice/index.ts', { '@/utils/request': { default: request }, '@/utils/certified-business': helper });
    const workflow = loadTS('../src/views/erp/wm/certified/warehouseWorkflow.ts', { '@/utils/certified-business': helper });
    const controller = workflow.createWarehouseWorkflow({
      domain: 'arrivalnotice',
      storage,
      readCredential: () => 'offline-real-credential',
      readScope: () => scope,
      hasPermission: () => true,
      context: api.getCertifiedArrivalContext,
      detail: api.getCertifiedArrivalDetail,
      confirm: async () => true,
      write: (op, id, body, session) => api.createCertifiedArrival(body, session),
      recovery: async () => assert.fail('到货不可恢复收货')
    });
    controller.setTarget('ORDER', ID);
    await controller.inspect();
    const result = await controller.perform('createNotice', input());
    assert.equal(result, failure === null);
    assert.equal(calls.filter((c) => c.method === 'post').length, 1);
    const blocked = helper
      .createBusinessIntentStore(storage)
      .isBlocked({ tenantId: scope.tenantId, userId: scope.userId, feature: 'wm:arrivalnotice:ORDER', documentId: ID });
    assert.equal(blocked, failure === 500);
    assert.equal(controller.state.message.includes('offline-real-credential'), false);
    assert.equal(JSON.stringify([...values]).includes('真实通知名称'), false);
    assert.equal(JSON.stringify([...values]).includes('offline-real-credential'), false);
  });

if (existsSync(path))
  for (const op of ['createReceive', 'submitNotice', 'synchronizeNotice', 'shelfReceive', 'executeReceive'])
    test(`${op} 正常人工一步与真实 native 回执归属，绝不自动执行下一步`, async () => {
      const receive = op.includes('Receive');
      const kind = op === 'createReceive' ? 'ORDER' : receive ? 'RECEIVE' : 'NOTICE';
      const patch =
        kind === 'NOTICE'
          ? { flowState: op === 'submitNotice' ? 'NOT_STARTED' : 'APPROVED' }
          : kind === 'RECEIVE'
            ? { flowState: 'APPROVED', native: { ...snapshot('RECEIVE', 'receive').native, status: op === 'shelfReceive' ? 'APPROVED' : 'SHELVED' } }
            : {};
      const result =
        op === 'createReceive'
          ? {
              receiveId: ID,
              receiveCode: 'offline-receipt',
              lineId: LINE,
              purchaseLineId: LINE,
              materialId: '3',
              baseUomId: '4',
              quantity: '2.0000',
              status: 'DRAFT_CREATED',
              replayed: false,
              requestHash: 'b'.repeat(64)
            }
          : op === 'executeReceive'
            ? { receiveId: ID, materialId: '3', quantity: '2.0000', sourceFingerprint: 'a'.repeat(64), replay: false, notificationPending: false }
            : undefined;
      const f = fixture({ domain: receive ? 'receive' : 'arrivalnotice', kind, patch, write: () => ({ code: 200, data: result }) });
      await f.controller.inspect();
      assert.equal(await f.controller.perform(op, input()), true);
      assert.equal(f.log.filter((x) => typeof x === 'object').length, 1);
      assert.equal(f.map.size, 0);
      if (op !== 'createReceive') assert.equal(f.log.find((x) => typeof x === 'object').body, undefined);
    });

const draftReceipt = (patch = {}) => ({
  receiveId: ID,
  receiveCode: 'offline-receipt',
  lineId: LINE,
  purchaseLineId: LINE,
  materialId: '3',
  baseUomId: '4',
  quantity: '2.0000',
  status: 'DRAFT_CREATED',
  replayed: false,
  requestHash: 'b'.repeat(64),
  ...patch
});
if (existsSync(path)) {
  for (const [label, values, receipt] of [
    ['无通知 null 身份', input(), draftReceipt()],
    ['精确可选通知 19/21', { ...input(), noticeId: '19', noticeLineId: '21' }, draftReceipt()],
    ['decimal 文本等值，不经 Number', input(), draftReceipt({ quantity: '2.0' })]
  ])
    test(`review createReceive 正常对照：${label} request/receipt/native 一致才 release`, async () => {
      const f = fixture({ domain: 'receive', realHelper: true, write: () => ({ code: 200, data: receipt }) });
      await f.controller.inspect();
      assert.equal(await f.controller.perform('createReceive', values), true);
      assert.equal(f.controller.state.blocked, false);
      assert.equal(f.log.filter((x) => typeof x === 'object').length, 1);
      assert.equal(f.map.size, 0);
    });

  for (const [field, value] of [
    ['purchaseLineId', '9007199254740996'],
    ['materialId', '8'],
    ['baseUomId', '7'],
    ['quantity', '9.0000'],
    ['quantity', '2.0001']
  ])
    test(`review createReceive 回执 ${field}=${value}：shape 有效但不属于人工请求，UNKNOWN 不 release`, async () => {
      const f = fixture({ domain: 'receive', realHelper: true, write: () => ({ code: 200, data: draftReceipt({ [field]: value }) }) });
      await f.controller.inspect();
      assert.equal(await f.controller.perform('createReceive', input()), false);
      assert.equal(f.controller.state.blocked, true);
      assert.equal(f.controller.state.result, null);
      assert.match(f.controller.state.message, /UNKNOWN/);
      const pending = [...f.map];
      assert.ok(pending.length > 0);
      await f.controller.inspect();
      assert.equal(await f.controller.perform('createReceive', { ...input(), requestKey: 'offline-changed-key' }), false);
      assert.deepEqual([...f.map], pending);
      assert.equal(f.log.filter((x) => typeof x === 'object').length, 1);
    });

  for (const [label, values, mutateReadback] of [
    [
      'native line.purchaseLineId',
      input(),
      (after) => {
        after.lines[0].purchaseLineId = '9007199254740996';
      }
    ],
    [
      'native line.receiveId',
      input(),
      (after) => {
        after.lines[0].receiveId = '77';
      }
    ],
    [
      'creation.receiveId',
      input(),
      (after) => {
        after.creations[0].receiveId = '77';
      }
    ],
    [
      'creation.receiveCode',
      input(),
      (after) => {
        after.creations[0].receiveCode = 'another-receipt';
      }
    ],
    [
      'native.noticeId',
      { ...input(), noticeId: '19', noticeLineId: '21' },
      (after) => {
        after.native.noticeId = '77';
      }
    ],
    [
      'creation.noticeId',
      { ...input(), noticeId: '19', noticeLineId: '21' },
      (after) => {
        after.creations[0].noticeId = '77';
      }
    ],
    [
      'creation.noticeLineId',
      { ...input(), noticeId: '19', noticeLineId: '21' },
      (after) => {
        after.creations[0].noticeLineId = '79';
      }
    ],
    [
      'line.noticeLineId',
      { ...input(), noticeId: '19', noticeLineId: '21' },
      (after) => {
        after.lines[0].noticeLineId = '79';
      }
    ],
    [
      '请求19/21但自洽原生77/79',
      { ...input(), noticeId: '19', noticeLineId: '21' },
      (after) => {
        after.native.noticeId = after.creations[0].noticeId = '77';
        after.lines[0].noticeLineId = after.creations[0].noticeLineId = '79';
      }
    ],
    [
      '请求有通知但原生均NULL',
      { ...input(), noticeId: '19', noticeLineId: '21' },
      (after) => {
        after.native.noticeId = after.creations[0].noticeId = null;
        after.lines[0].noticeLineId = after.creations[0].noticeLineId = null;
      }
    ],
    [
      '请求无通知但原生自洽19/21',
      input(),
      (after) => {
        after.native.noticeId = after.creations[0].noticeId = '19';
        after.lines[0].noticeLineId = after.creations[0].noticeLineId = '21';
      }
    ],
    [
      '未给通知但空字符串不当NULL',
      input(),
      (after) => {
        after.native.noticeId = '';
      }
    ]
  ])
    test(`review createReceive ${label} 不等值：保留真实 helper UNKNOWN 意图`, async () => {
      const f = fixture({ domain: 'receive', realHelper: true, write: () => ({ code: 200, data: draftReceipt() }), mutateReadback });
      await f.controller.inspect();
      assert.equal(await f.controller.perform('createReceive', values), false);
      assert.equal(f.controller.state.blocked, true);
      assert.equal(f.controller.state.result, null);
      assert.ok(f.map.size > 0);
      assert.equal(f.log.filter((x) => typeof x === 'object').length, 1);
    });
}
