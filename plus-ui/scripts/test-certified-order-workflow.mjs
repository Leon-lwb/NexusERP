import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
process.env.GOMAXPROCS = '1';
const temp = mkdtempSync(resolve(tmpdir(), 'order-recovery-core-'));
let module, loadError;
try {
  await build({
    entryPoints: [resolve(root, 'src/views/erp/pu/order/certifiedOrderWorkflow.ts')],
    bundle: true,
    platform: 'node',
    format: 'esm',
    outfile: resolve(temp, 'core.mjs'),
    plugins: [
      {
        name: 'isolated',
        setup(b) {
          b.onResolve({ filter: /^@\// }, (a) =>
            a.path === '@/utils/request' ? { path: 'request', namespace: 'stub' } : { path: resolve(root, 'src', a.path.slice(2) + '.ts') }
          );
          b.onLoad({ filter: /.*/, namespace: 'stub' }, () => ({
            contents:
              'export default Object.assign(async()=>{throw new Error("unexpected network")},{defaults:{transformRequest:[],transformResponse:[]}});',
            loader: 'js'
          }));
        }
      }
    ]
  });
  module = await import(pathToFileURL(resolve(temp, 'core.mjs')).href);
} catch (e) {
  loadError = e;
}
process.on('exit', () => {
  assert.ok(temp.startsWith(resolve(tmpdir(), 'order-recovery-core-')), '只清理本次独立临时目录');
  rmSync(temp, { recursive: true, force: true });
});
const O = '9007199254740993',
  R = '9007199254740994',
  U = '9007199254740995',
  OTHER = '9007199254740996',
  Q = '9007199254740997';
const clone = (x) => JSON.parse(JSON.stringify(x));
const terms = {
  unitPriceExcludingTax: '12.3456',
  taxRate: '0.13',
  currency: 'CNY',
  exchangeRate: '1',
  promisedArrivalDate: '2026-10-12',
  validFrom: '2026-10-01',
  validUntil: '2026-10-20',
  supplierQuoteReference: 'fixture-quote',
  supplierContact: '合成测试联系人',
  evidenceReference: 'fixture:attachment',
  evidenceSha256: 'b'.repeat(64),
  paymentTerms: '合成条款',
  deliveryTerms: '合成交付'
};
function quote() {
  return {
    quoteId: Q,
    orderId: O,
    quoteVersion: 1,
    enteredBy: OTHER,
    requestKey: 'old-quote',
    quoteHash: 'a'.repeat(64),
    quantity: '12.0000',
    ...terms,
    confirmationId: null,
    confirmedBy: null,
    confirmationHash: null,
    reviewNote: null
  };
}
let fixtureSequence = 0;
function fixture({ readyQuote = false, confirmed = false, readOnly = false } = {}) {
  assert.ifError(loadError);
  const f = {
    token: 'fixture-token',
    calls: [],
    snapshots: [],
    context: {
      tenantId: '000000',
      userId: String(BigInt(U) + 100n * BigInt(++fixtureSequence)),
      termsAddAllowed: true,
      termsConfirmAllowed: true,
      approveAllowed: true,
      closeAllowed: true,
      cancelAllowed: true,
      priceVisible: true
    },
    detail: {
      order: {
        tenantId: '000000',
        orderId: O,
        orderNo: 'fixture-order',
        requisitionId: R,
        vendorId: OTHER,
        status: 'DRAFT',
        version: 0,
        quoteId: null,
        selectFlowId: null,
        requisitionSourceType: 'AI_DECISION',
        decisionRunId: OTHER,
        decisionActionId: Q,
        sourceRequestKey: 'fixture-source',
        originalCommandPresent: true
      },
      quotes: readyQuote ? [quote()] : [],
      allocations: [{ allocationId: Q, requisitionLineId: R, orderLineId: O, allocationStatus: 'ORDER_COMMITTED_NOT_TIMELY_SUPPLY', version: 2 }],
      priceVisible: true,
      serverDate: '2026-10-09',
      genericEditAllowedHint: false
    },
    map: new Map(),
    readOnly
  };
  if (confirmed) Object.assign(f.detail.quotes[0], { confirmationId: OTHER, confirmedBy: f.context.userId, confirmationHash: 'a'.repeat(64) });
  f.storage = { getItem: (k) => f.map.get(k) ?? null, setItem: (k, v) => f.map.set(k, v), removeItem: (k) => f.map.delete(k) };
  f.post = async (operation, id, body) => {
    f.calls.push({ operation, id, body: clone(body) });
    if (operation === 'terms') {
      const q = { ...quote(), ...body, requestKey: body.requestKey, enteredBy: f.context.userId };
      delete q.expectedOrderVersion;
      f.detail.quotes.push(q);
      return { code: 200, data: { quote: { ...q, tenantId: '000000' }, confirmation: null, replayed: false } };
    }
    if (operation === 'confirm') {
      const q = f.detail.quotes.find((q) => q.quoteId === body.quoteId);
      Object.assign(q, { confirmationId: OTHER, confirmedBy: f.context.userId, confirmationHash: q.quoteHash, reviewNote: body.reviewNote });
      return {
        code: 200,
        data: {
          quote: { ...q, tenantId: '000000' },
          confirmation: {
            confirmationId: OTHER,
            tenantId: '000000',
            quoteId: Q,
            quoteHash: q.quoteHash,
            actorId: f.context.userId,
            requestKey: body.requestKey
          },
          replayed: false
        }
      };
    }
    const status = { approve: 'APPROVED', close: 'CLOSED', cancel: 'CANCELLED' }[operation];
    f.detail.order.version++;
    f.detail.order.status = status;
    for (const a of f.detail.allocations) {
      a.version++;
      if (operation !== 'approve') a.allocationStatus = 'RELEASED';
    }
    if (operation === 'approve') f.detail.order.quoteId = body.quoteId;
    const allocations = [
      {
        eventId: OTHER,
        allocationId: Q,
        operation: { approve: 'APPROVE_ORDER', close: 'CLOSE_ORDER', cancel: 'CANCEL_ORDER' }[operation],
        fromStatus: 'ORDER_COMMITTED_NOT_TIMELY_SUPPLY',
        toStatus: operation === 'approve' ? 'ORDER_COMMITTED_NOT_TIMELY_SUPPLY' : 'RELEASED',
        requisitionLineId: R,
        orderLineId: O,
        actorId: f.context.userId,
        replayed: false
      }
    ];
    return {
      code: 200,
      data: {
        orderId: O,
        orderNo: 'fixture-order',
        requestKey: body.requestKey,
        status,
        version: f.detail.order.version,
        quoteId: f.detail.order.quoteId,
        replayed: false,
        allocations
      }
    };
  };
  f.newController = () =>
    module.createCertifiedOrderWorkflow({
      readCredential: () => f.token,
      readContext: async () => clone(f.context),
      readDetail: async () => clone(f.detail),
      submit: (...a) => f.post(...a),
      storage: f.storage,
      onChange: (snapshot) => f.snapshots.push(clone(snapshot))
    });
  f.controller = f.newController();
  return f;
}
async function open(f) {
  await f.controller.open(O, { readOnly: f.readOnly });
}
test('订单打开是query-only，不创建报价或执行动作', async () => {
  const f = fixture();
  await open(f);
  assert.equal(f.calls.length, 0);
  assert.equal(f.controller.snapshot().phase, 'READY');
  assert.equal(f.controller.snapshot().detail.order.orderId, O);
});
test('真实CNY条款逐字段发送，身份/数量/verified不入请求', async () => {
  const f = fixture();
  await open(f);
  await f.controller.execute('terms', terms);
  assert.equal(f.controller.snapshot().phase, 'SUCCEEDED');
  const body = f.calls[0].body;
  assert.equal(body.expectedOrderVersion, 0);
  assert.equal(body.unitPriceExcludingTax, '12.3456');
  assert.match(body.requestKey, /^[A-Za-z0-9:_-]{1,128}$/);
  assert.deepEqual(Object.keys(body).sort(), [...Object.keys(terms), 'requestKey', 'expectedOrderVersion'].sort());
  assert.equal(f.calls[0].id, O);
});
test('独立复核只能引用fresh原报价hash和id', async () => {
  const f = fixture({ readyQuote: true });
  await open(f);
  await f.controller.execute('confirm', { quoteId: Q, reviewNote: '实际测试复核' });
  assert.equal(f.controller.snapshot().phase, 'SUCCEEDED');
  assert.deepEqual(Object.keys(f.calls[0].body).sort(), ['quoteId', 'quoteHash', 'requestKey', 'reviewNote'].sort());
  assert.equal(f.calls[0].body.quoteHash, 'a'.repeat(64));
});
test('录入人不能自行复核', async () => {
  const f = fixture({ readyQuote: true });
  f.detail.quotes[0].enteredBy = f.context.userId;
  await open(f);
  await f.controller.execute('confirm', { quoteId: Q, reviewNote: '自审' });
  assert.equal(f.calls.length, 0);
});
test('已确认真实报价方可批准且不自动创建到货', async () => {
  const f = fixture({ readyQuote: true, confirmed: true });
  await open(f);
  await f.controller.execute('approve', { quoteId: Q });
  assert.equal(f.controller.snapshot().phase, 'SUCCEEDED');
  assert.equal(f.calls.length, 1);
  assert.deepEqual(Object.keys(f.calls[0].body).sort(), ['expectedVersion', 'quoteId', 'requestKey'].sort());
});
test('未确认报价不能批准', async () => {
  const f = fixture({ readyQuote: true });
  await open(f);
  await f.controller.execute('approve', { quoteId: Q });
  assert.equal(f.calls.length, 0);
});
for (const operation of ['close', 'cancel'])
  test(`草稿${operation}通过独立认证操作，不走旧PUT`, async () => {
    const f = fixture();
    await open(f);
    await f.controller.execute(operation, {});
    assert.equal(f.controller.snapshot().phase, 'SUCCEEDED');
    assert.deepEqual(Object.keys(f.calls[0].body).sort(), ['expectedVersion', 'requestKey'].sort());
  });
for (const bad of [null, '0', -1, 1.5, 2147483648])
  test(`原生坏版本${String(bad)}不默认0`, async () => {
    const f = fixture();
    f.detail.order.version = bad;
    await open(f);
    await f.controller.execute('terms', terms);
    assert.equal(f.calls.length, 0);
  });
for (const field of ['termsAddAllowed', 'termsConfirmAllowed', 'approveAllowed', 'closeAllowed', 'cancelAllowed'])
  test(`权限${field}不借用edit或query`, async () => {
    const f = fixture({ readyQuote: true, confirmed: true });
    f.context[field] = false;
    await open(f);
    const op = {
      termsAddAllowed: 'terms',
      termsConfirmAllowed: 'confirm',
      approveAllowed: 'approve',
      closeAllowed: 'close',
      cancelAllowed: 'cancel'
    }[field];
    await f.controller.execute(
      op,
      op === 'terms' ? terms : op === 'confirm' ? { quoteId: Q, reviewNote: 'fixture' } : op === 'approve' ? { quoteId: Q } : {}
    );
    assert.equal(f.calls.length, 0);
  });
for (const [key, value] of [
  ['unitPriceExcludingTax', '0'],
  ['unitPriceExcludingTax', 12.3456],
  ['unitPriceExcludingTax', '999999999999999'],
  ['taxRate', '1.01'],
  ['currency', 'USD'],
  ['exchangeRate', '2'],
  ['evidenceSha256', 'bad'],
  ['evidenceReference', ''],
  ['validUntil', '2026-10-08'],
  ['promisedArrivalDate', '2026-10-08'],
  ['validFrom', '2026-10-10'],
  ['promisedArrivalDate', '2026-02-30']
])
  test(`条款${key}坏值不派发`, async () => {
    const f = fixture();
    await open(f);
    await f.controller.execute('terms', { ...terms, [key]: value });
    assert.equal(f.calls.length, 0);
  });
test('不接收数量、操作者或verified夹带', async () => {
  for (const extra of ['quantity', 'userId', 'verified']) {
    const f = fixture();
    await open(f);
    await f.controller.execute('terms', { ...terms, [extra]: 'fixture' });
    assert.equal(f.calls.length, 0);
  }
});
test('NULL/缺失原始command不能升级为认证来源', async () => {
  const f = fixture();
  f.detail.order.originalCommandPresent = null;
  await open(f);
  await f.controller.execute('terms', terms);
  assert.equal(f.calls.length, 0);
});
test('Flowable绑定不能复用认证生命周期写入口', async () => {
  const f = fixture();
  f.detail.order.selectFlowId = Q;
  await open(f);
  await f.controller.execute('terms', terms);
  assert.equal(f.calls.length, 0);
});
test('只读props不变成写资格', async () => {
  const f = fixture({ readOnly: true });
  await open(f);
  await f.controller.execute('terms', terms);
  assert.equal(f.calls.length, 0);
});
test('5xx/网络锁存UNKNOWN，refresh/新实例/换版本不重发', async () => {
  const f = fixture();
  f.post = async () => {
    f.calls.push({});
    throw new Error('network');
  };
  await open(f);
  await f.controller.execute('terms', terms);
  assert.equal(f.controller.snapshot().phase, 'UNKNOWN');
  f.detail.order.version = 2;
  await f.controller.refresh();
  await f.controller.execute('terms', terms);
  f.controller = f.newController();
  await open(f);
  await f.controller.execute('terms', terms);
  assert.equal(f.calls.length, 1);
});
test('畸形R200不能释放未知锁', async () => {
  const f = fixture();
  f.post = async () => {
    f.calls.push({});
    return { code: 200, data: {} };
  };
  await open(f);
  await f.controller.execute('terms', terms);
  assert.equal(f.controller.snapshot().phase, 'UNKNOWN');
  await f.controller.execute('terms', terms);
  assert.equal(f.calls.length, 1);
});
test('移植其他tenant或order的成功报价回执不能释放锁', async () => {
  for (const [key, value] of [
    ['tenantId', '999999'],
    ['orderId', OTHER]
  ]) {
    const f = fixture();
    const original = f.post;
    f.post = async (...a) => {
      const r = await original(...a);
      r.data.quote[key] = value;
      return r;
    };
    await open(f);
    await f.controller.execute('terms', terms);
    assert.equal(f.controller.snapshot().phase, 'UNKNOWN');
  }
});
test('操作回执actor必须为本次主体', async () => {
  const f = fixture();
  const original = f.post;
  f.post = async (...a) => {
    const r = await original(...a);
    r.data.allocations[0].actorId = OTHER;
    return r;
  };
  await open(f);
  await f.controller.execute('cancel', {});
  assert.equal(f.controller.snapshot().phase, 'UNKNOWN');
});
test('明确4xx拒绝可释放本次意图但不自动再发', async () => {
  const f = fixture();
  f.post = async () => {
    f.calls.push({});
    return { code: 403, msg: 'fixture refusal' };
  };
  await open(f);
  await f.controller.execute('terms', terms);
  assert.equal(f.controller.snapshot().phase, 'REFUSED');
  assert.equal(f.calls.length, 1);
});
test('存储禁用时禁止POST，不降级为内存重试', async () => {
  const f = fixture();
  f.storage.setItem = () => {
    throw new Error('denied');
  };
  await open(f);
  await f.controller.execute('terms', terms);
  assert.equal(f.calls.length, 0);
});
test('查询主体切换后不得把旧结果给新账号', async () => {
  const f = fixture();
  await open(f);
  f.context.userId = OTHER;
  await f.controller.execute('terms', terms);
  assert.equal(f.calls.length, 0);
});
test('固定token在实际派发前失效不能重发', async () => {
  const f = fixture();
  await open(f);
  f.token = 'another-fixture-token';
  await f.controller.execute('terms', terms);
  assert.equal(f.calls.length, 0);
});
test('snapshot修改不能污染内部原生来源', async () => {
  const f = fixture();
  await open(f);
  f.controller.snapshot().detail.order.version = 999;
  await f.controller.execute('terms', terms);
  assert.equal(f.calls[0].body.expectedOrderVersion, 0);
});
test('双击在fresh读取期间也只派发一次', async () => {
  const f = fixture();
  await open(f);
  await Promise.all([f.controller.execute('terms', terms), f.controller.execute('terms', terms)]);
  assert.equal(f.calls.length, 1);
});

for (const [label, change] of [
  [
    '原版本',
    (f) => {
      f.detail.order.version = 1;
    }
  ],
  [
    '原来源键',
    (f) => {
      f.detail.order.sourceRequestKey = 'changed-original';
    }
  ],
  [
    '原占用身份',
    (f) => {
      f.detail.allocations[0].allocationId = OTHER;
    }
  ],
  [
    '原状态',
    (f) => {
      f.detail.order.status = 'APPROVED';
    }
  ]
])
  test(`用户确认后${label}变化必须刷新，不能自动采用新事实提交`, async () => {
    const f = fixture();
    await open(f);
    change(f);
    await f.controller.execute('cancel', {});
    assert.equal(f.calls.length, 0);
  });
test('用户复核后报价价格/hash变化不能静默确认新条款', async () => {
  const f = fixture({ readyQuote: true });
  await open(f);
  f.detail.quotes[0].unitPriceExcludingTax = '22.3456';
  f.detail.quotes[0].quoteHash = 'c'.repeat(64);
  await f.controller.execute('confirm', { quoteId: Q, reviewNote: '只复核原条款' });
  assert.equal(f.calls.length, 0);
});

test('JDBC原生DECIMAL尾零不把合法报价误判UNKNOWN', async () => {
  const f = fixture();
  const original = f.post;
  f.post = async (...args) => {
    const r = await original(...args);
    Object.assign(f.detail.quotes[0], { unitPriceExcludingTax: '12.3400', taxRate: '0.1300', exchangeRate: '1.000000' });
    return r;
  };
  await open(f);
  await f.controller.execute('terms', { ...terms, unitPriceExcludingTax: '12.34' });
  assert.equal(f.controller.snapshot().phase, 'SUCCEEDED');
});
test('不同十进制价格不能借尾零兼容解除UNKNOWN', async () => {
  const f = fixture();
  const original = f.post;
  f.post = async (...args) => {
    const r = await original(...args);
    f.detail.quotes[0].unitPriceExcludingTax = '12.3500';
    return r;
  };
  await open(f);
  await f.controller.execute('terms', { ...terms, unitPriceExcludingTax: '12.34' });
  assert.equal(f.controller.snapshot().phase, 'UNKNOWN');
});

// 报价与独立确认不改变任何来源占用；核对原生身份全集，而非数量或 KPI。
for (const operation of ['terms', 'confirm']) {
  for (const [field, value] of Object.entries({
    allocationId: OTHER,
    requisitionLineId: OTHER,
    orderLineId: OTHER,
    allocationStatus: 'RELEASED',
    version: 3
  }))
    test(`${operation} 成功读回 AllocationView ${field} 变化锁 UNKNOWN，刷新不释放原意图`, async () => {
      const f = fixture({ readyQuote: operation === 'confirm' });
      const originalPost = f.post;
      f.post = async (...args) => {
        const result = await originalPost(...args);
        f.detail.allocations[0][field] = value;
        return result;
      };
      await open(f);
      const input = operation === 'terms' ? terms : { quoteId: Q, reviewNote: '仅复核原占用来源报价' };
      await f.controller.execute(operation, input);
      assert.equal(f.calls.length, 1);
      assert.equal(f.controller.snapshot().phase, 'UNKNOWN');
      const saved = clone([...f.map]);
      f.controller = f.newController();
      await open(f);
      assert.equal(f.controller.snapshot().phase, 'UNKNOWN');
      await f.controller.execute(operation, input);
      assert.equal(f.calls.length, 1, '只读重开不能换键重发');
      assert.deepEqual([...f.map], saved, '原未知意图不得解除或替换');
    });
  for (const shape of ['missing', 'added', 'duplicate', 'empty'])
    test(`${operation} 成功读回占用全集 ${shape} 锁 UNKNOWN`, async () => {
      const f = fixture({ readyQuote: operation === 'confirm' });
      f.detail.allocations.push({
        allocationId: OTHER,
        requisitionLineId: Q,
        orderLineId: R,
        allocationStatus: 'ORDER_COMMITTED_NOT_TIMELY_SUPPLY',
        version: 4
      });
      const originalPost = f.post;
      f.post = async (...args) => {
        const result = await originalPost(...args);
        if (shape === 'missing') f.detail.allocations.pop();
        if (shape === 'added') f.detail.allocations.push({ ...f.detail.allocations[0], allocationId: U });
        if (shape === 'duplicate') f.detail.allocations[1] = clone(f.detail.allocations[0]);
        if (shape === 'empty') f.detail.allocations = [];
        return result;
      };
      await open(f);
      await f.controller.execute(operation, operation === 'terms' ? terms : { quoteId: Q, reviewNote: '全集核对' });
      assert.equal(f.calls.length, 1);
      assert.equal(f.controller.snapshot().phase, 'UNKNOWN');
    });
  test(`${operation} 原占用全集仅排序变化仍可精确成功，不按数组位置或数量计算`, async () => {
    const f = fixture({ readyQuote: operation === 'confirm' });
    f.detail.allocations.push({
      allocationId: OTHER,
      requisitionLineId: Q,
      orderLineId: R,
      allocationStatus: 'ORDER_COMMITTED_NOT_TIMELY_SUPPLY',
      version: 4
    });
    const originalPost = f.post;
    f.post = async (...args) => {
      const result = await originalPost(...args);
      f.detail.allocations.reverse();
      return result;
    };
    await open(f);
    await f.controller.execute(operation, operation === 'terms' ? terms : { quoteId: Q, reviewNote: '全集同值核对' });
    assert.equal(f.calls.length, 1);
    assert.equal(f.controller.snapshot().phase, 'SUCCEEDED');
    assert.deepEqual(f.controller.snapshot().detail.allocations, f.detail.allocations);
  });
}

// Lifecycle producer: persistLifecycle -> ledger.updateWithVersion(expected), SQL version = version + 1.
for (const operation of ['approve', 'close', 'cancel']) {
  for (const corruption of ['null', 'missing', 'negative', 'string', 'noninteger', 'wrongincrement'])
    test(`${operation} lifecycle native allocation version ${corruption} 必须 UNKNOWN，不解锁/不报告成功`, async () => {
      for (const increment of corruption === 'wrongincrement' ? [0, 2] : [1]) {
        const f = fixture({ readyQuote: operation === 'approve', confirmed: operation === 'approve' });
        const input = operation === 'approve' ? { quoteId: Q } : {};
        const beforeVersion = f.detail.allocations[0].version;
        const originalPost = f.post;
        f.post = async (...args) => {
          const result = await originalPost(...args);
          const row = f.detail.allocations[0];
          if (corruption === 'missing') delete row.version;
          else
            row.version = {
              null: null,
              negative: -1,
              string: String(beforeVersion + 1),
              noninteger: beforeVersion + 1.5,
              wrongincrement: beforeVersion + increment
            }[corruption];
          return result;
        };
        await open(f);
        await f.controller.execute(operation, input);
        assert.equal(f.calls.length, 1);
        assert.equal(f.controller.snapshot().phase, 'UNKNOWN');
        assert.equal(f.snapshots.filter((x) => x.phase === 'SUCCEEDED').length, 0);
        const saved = clone([...f.map]);
        assert.ok(saved.length > 0, '保留已派发的持久原意图');
        f.controller = f.newController();
        await open(f);
        assert.equal(f.controller.snapshot().phase, 'UNKNOWN');
        await f.controller.execute(operation, input);
        assert.equal(f.calls.length, 1);
        assert.deepEqual([...f.map], saved, '新控制器/只读回查不解除 UNKNOWN、不换键');
      }
    });
  test(`${operation} 原生 ledger CAS 每操作恰好 +1，原身份/状态全集匹配才成功`, async () => {
    const f = fixture({ readyQuote: operation === 'approve', confirmed: operation === 'approve' });
    const before = clone(f.detail.allocations);
    await open(f);
    await f.controller.execute(operation, operation === 'approve' ? { quoteId: Q } : {});
    assert.equal(f.controller.snapshot().phase, 'SUCCEEDED');
    assert.equal(f.calls.length, 1);
    assert.equal(f.snapshots.filter((x) => x.phase === 'SUCCEEDED').length, 1);
    assert.deepEqual(
      f.controller.snapshot().detail.allocations,
      before.map((row) => ({ ...row, version: row.version + 1, allocationStatus: operation === 'approve' ? row.allocationStatus : 'RELEASED' }))
    );
  });
}
