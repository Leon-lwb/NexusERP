import assert from 'node:assert/strict';
import { existsSync, readFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import { build } from 'esbuild';
import axios from 'axios';

export const uiRoot = fileURLToPath(new URL('..', import.meta.url));
export const paths = {
  api: join(uiRoot, 'src/api/erp/pu/order/index.ts'),
  controller: join(uiRoot, 'src/views/erp/pu/order/certifiedOrderWorkflow.ts'),
  component: join(uiRoot, 'src/views/erp/pu/order/CertifiedOrderWorkflowDialog.vue'),
  helper: join(uiRoot, 'src/utils/certified-business.ts')
};
export const O = '9007199254740993',
  OTHER_O = '9007199254741111',
  Q = '9007199254740997',
  Q2 = '9007199254740999';
export const ENTERED = '9007199254741011',
  CONFIRM_ID = '9007199254741021';
export const TOKEN = 'order-sidecar-offline-a',
  TOKEN_B = 'order-sidecar-offline-b';
export const TERMS = Object.freeze({
  unitPriceExcludingTax: '99999999999999.1234',
  taxRate: '0.1300',
  currency: 'CNY',
  exchangeRate: '1',
  promisedArrivalDate: '2026-10-15',
  validFrom: '2026-10-01',
  validUntil: '2026-10-30',
  supplierQuoteReference: '合成供应规格第3页',
  supplierContact: '合成测试联系人',
  evidenceReference: 'fixture:真实资料替身',
  evidenceSha256: 'b'.repeat(64),
  paymentTerms: '合成付款条款',
  deliveryTerms: '合成交付条款'
});
export const copy = (x) => JSON.parse(JSON.stringify(x));
export function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
export function memoryStorage() {
  const values = new Map();
  return { values, getItem: (k) => values.get(k) ?? null, setItem: (k, v) => values.set(k, v), removeItem: (k) => values.delete(k) };
}

/** 只编译实际产品模块；Axios/config 适配器和弹窗/用户存储是离线边界，不 mock 整个 API。 */
export async function loadOrderProduction(componentEntry) {
  process.env.GOMAXPROCS = '1';
  const directory = mkdtempSync(join(tmpdir(), 'order-recovery-api-'));
  const outfile = join(directory, 'order.cjs');
  const normalized = (p) => p.replaceAll('\\', '/');
  const entry =
    componentEntry ??
    `export * as api from '${normalized(paths.api)}';
    export * as helper from '${normalized(paths.helper)}'; export * as controller from '${normalized(paths.controller)}';`;
  try {
    await build({
      stdin: { contents: entry, resolveDir: componentEntry ? dirname(paths.component) : uiRoot, loader: 'ts' },
      bundle: true,
      platform: 'node',
      format: 'cjs',
      packages: 'external',
      outfile,
      logLevel: 'silent',
      define: { 'import.meta.env': JSON.stringify({ VITE_APP_BASE_API: '/dev-api', VITE_APP_ENCRYPT: 'false' }) },
      plugins: [
        {
          name: 'order-offline-boundaries',
          setup(b) {
            b.onResolve({ filter: /^@\/utils\/(request|auth)$/ }, (a) => ({ path: a.path.split('/').at(-1), namespace: 'order-port' }));
            b.onResolve({ filter: /^@\/store\/modules\/user$/ }, () => ({ path: 'user', namespace: 'order-port' }));
            b.onResolve({ filter: /^element-plus$/ }, () => ({ path: 'message', namespace: 'order-port' }));
            b.onResolve({ filter: /^@\// }, (a) => {
              const p = resolve(uiRoot, 'src', a.path.slice(2));
              return { path: existsSync(join(p, 'index.ts')) ? join(p, 'index.ts') : p + '.ts' };
            });
            b.onLoad({ filter: /.*/, namespace: 'order-port' }, (a) => ({
              loader: 'ts',
              contents: {
                request:
                  'const request = (...args) => globalThis.__orderSidecarEnv.request(...args); Object.defineProperty(request, "defaults", { get: () => globalThis.__orderSidecarEnv.request.defaults }); export default request;',
                auth: 'export const getToken = () => globalThis.__orderSidecarEnv.credential.value;',
                user: 'export const useUserStore = () => globalThis.__orderSidecarEnv.user;',
                message: 'export const ElMessageBox = { confirm: (...args) => globalThis.__orderSidecarEnv.confirm(...args) };'
              }[a.path]
            }));
          }
        }
      ]
    });
    const req = createRequire(join(uiRoot, 'package.json'));
    const Module = req('node:module');
    const module = new Module(outfile);
    module.filename = outfile;
    module.paths = [join(uiRoot, 'node_modules')];
    module._compile(readFileSync(outfile, 'utf8'), outfile);
    return module.exports;
  } finally {
    assert.ok(directory.startsWith(join(tmpdir(), 'order-recovery-api-')), '临时目录必须属于本次 sidecar');
    rmSync(directory, { recursive: true, force: true });
  }
}

/** 实际 Axios transform/interceptor/adapter；不创建实际 HTTP socket。 */
export function offlineRequest(f, responseFor) {
  const request = axios.create({
    adapter: async (config) => {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data;
      const call = {
        url: config.url,
        method: config.method,
        body: body === undefined ? undefined : copy(body),
        params: config.params,
        authorization: config.headers.get('Authorization'),
        isToken: config.headers.get('isToken'),
        repeatSubmit: config.headers.get('repeatSubmit')
      };
      f.calls.push(call);
      const result = await responseFor(call, config);
      const response = { status: result.status ?? 200, statusText: 'offline', config, headers: {}, data: JSON.stringify(result.data) };
      if (config.validateStatus && !config.validateStatus(response.status)) {
        throw new axios.AxiosError('原始离线错误不能外显 ' + TOKEN, 'ERR_BAD_RESPONSE', config, null, response);
      }
      return response;
    }
  });
  request.interceptors.response.use((response) => response.data);
  return request;
}

let fixtureSequence = 0;
function fixture(modules) {
  const f = {
    calls: [],
    credential: { value: TOKEN },
    assertions: 0,
    scopeCurrent: true,
    status: 200,
    code: 200,
    data: { synthetic: true },
    storage: memoryStorage(),
    userId: String(20000 + ++fixtureSequence)
  };
  f.request = offlineRequest(f, async () => {
    if (f.gate) await f.gate.promise;
    if (f.network) throw new Error('原始网络错误 ' + TOKEN);
    return { status: f.status, data: { code: f.code, data: f.data, msg: '原始响应含合成凭证 ' + TOKEN } };
  });
  globalThis.__orderSidecarEnv = f;
  const original = modules.helper.createBusinessSession(
    () => f.credential.value,
    () => {
      assert.equal(f.scopeCurrent, true);
    }
  );
  f.session = Object.freeze({
    token: original.token,
    assertCurrent() {
      f.assertions++;
      original.assertCurrent();
    }
  });
  return f;
}
const command = (operation) =>
  operation === 'terms'
    ? { ...TERMS, requestKey: 'api-order-terms-1', expectedOrderVersion: 0 }
    : operation === 'confirm'
      ? { quoteId: Q, quoteHash: 'a'.repeat(64), requestKey: 'api-order-confirm-1', reviewNote: '合成独立资料复核' }
      : operation === 'approve'
        ? { expectedVersion: 0, quoteId: Q, requestKey: 'api-order-approve-1' }
        : { expectedVersion: 0, requestKey: 'api-order-' + operation + '-1' };

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  let loaded;
  const run = (name, fn) =>
    test(name, async () => {
      loaded ??= await loadOrderProduction();
      const f = fixture(loaded);
      try {
        await fn(f, loaded);
      } finally {
        delete globalThis.__orderSidecarEnv;
      }
    });
  run('bootstrap：实际订单 API/helper 导出可执行，不发送真实网络', async (f, m) => {
    for (const name of ['getCertifiedOrderContext', 'getCertifiedOrderDetail', 'submitCertifiedOrderOperation'])
      assert.equal(typeof m.api[name], 'function');
    assert.equal(typeof m.helper.createBusinessSession, 'function');
    assert.equal(f.calls.length, 0);
  });
  run('query-only 真实 GET 路径、固定 token、dispatch/assert，返回原生 NULL 版本，不触碰意图锁', async (f, m) => {
    const scope = { tenantId: '000001', userId: f.userId, feature: 'certified-order', documentId: O };
    const store = m.helper.createBusinessIntentStore(f.storage);
    store.begin(scope, 'approve', 'api-read-only-pending');
    f.data = {
      tenantId: '000001',
      userId: f.userId,
      termsAddAllowed: false,
      termsConfirmAllowed: false,
      approveAllowed: false,
      closeAllowed: false,
      cancelAllowed: false,
      priceVisible: false
    };
    assert.deepEqual(await m.api.getCertifiedOrderContext(f.session), f.data);
    f.data = { order: { orderId: O, version: null }, quotes: [], allocations: [], priceVisible: false };
    const detail = await m.api.getCertifiedOrderDetail(O, f.session);
    assert.equal(detail.order.orderId, O);
    assert.equal(detail.order.version, null);
    assert.deepEqual(
      f.calls.map((c) => c.url),
      ['/erp/pu/order/certified/workflow-context', '/erp/pu/order/certified/' + O + '/workflow-detail']
    );
    assert.ok(f.calls.every((c) => c.method === 'get' && c.authorization === 'Bearer ' + TOKEN && c.isToken === false));
    assert.ok(f.assertions >= 6);
    assert.equal(store.isBlocked(scope), true);
  });
  for (const operation of ['terms', 'confirm', 'approve', 'close', 'cancel']) {
    run(`真实 ${operation} API 精确 URL/字段/版本，只派发一次，不走旧 PUT/Flow`, async (f, m) => {
      const body = command(operation),
        frozen = copy(body);
      await m.api.submitCertifiedOrderOperation(operation, O, body, f.session);
      assert.equal(f.calls.length, 1);
      const call = f.calls[0];
      assert.equal(call.url, '/erp/pu/order/certified/' + O + (operation === 'confirm' ? '/terms/' + Q + '/confirm' : '/' + operation));
      assert.equal(call.method, 'post');
      assert.equal(call.authorization, 'Bearer ' + TOKEN);
      assert.equal(call.isToken, false);
      assert.equal(call.repeatSubmit, false);
      assert.ok(f.assertions >= 3);
      const expected = copy(body);
      if (operation === 'confirm') delete expected.quoteId;
      assert.deepEqual(call.body, expected);
      assert.deepEqual(body, frozen, '不能修改原输入对象');
      if (operation === 'terms') assert.equal(call.body.unitPriceExcludingTax, TERMS.unitPriceExcludingTax);
      if (operation === 'confirm') assert.deepEqual(Object.keys(call.body).sort(), ['quoteHash', 'requestKey', 'reviewNote']);
    });
  }
  for (const badId of [null, undefined, '', '01', '-1', '1.0', O + '/approve', '9223372036854775808', Number(O)]) {
    run(`实际 API 拒绝不精确/非法 ID ${String(badId)}，不发送请求`, async (f, m) => {
      await assert.rejects(async () => m.api.getCertifiedOrderDetail(badId, f.session));
      await assert.rejects(async () => m.api.submitCertifiedOrderOperation('close', badId, command('close'), f.session));
      assert.equal(f.calls.length, 0);
    });
  }
  for (const operation of ['terms', 'approve', 'close', 'cancel'])
    for (const version of [null, undefined, '0', -1, 0.5, 2147483648]) {
      run(`${operation} 不默认/强转原生坏版本 ${String(version)}`, async (f, m) => {
        const body = command(operation);
        body[operation === 'terms' ? 'expectedOrderVersion' : 'expectedVersion'] = version;
        await assert.rejects(async () => m.api.submitCertifiedOrderOperation(operation, O, body, f.session));
        assert.equal(f.calls.length, 0);
      });
    }
  for (const operation of ['terms', 'confirm', 'approve', 'close', 'cancel']) {
    run(`${operation} 白名单拒绝缺字段及夹带 quantity/verified/actor/time，不静默通过`, async (f, m) => {
      const body = command(operation);
      const missing = { ...body };
      delete missing.requestKey;
      await assert.rejects(async () => m.api.submitCertifiedOrderOperation(operation, O, missing, f.session));
      for (const key of ['quantity', 'verified', 'actorId', 'tenantId', 'confirmedAt']) {
        await assert.rejects(async () => m.api.submitCertifiedOrderOperation(operation, O, { ...body, [key]: 'unsafe' }, f.session));
      }
      assert.equal(f.calls.length, 0);
    });
  }
  run('confirm quoteId 只进 URL；非法报价 ID、夹带 expectedVersion 均不派发', async (f, m) => {
    for (const quoteId of [Number(Q), Q + '?other=1', '01', null]) {
      await assert.rejects(async () => m.api.submitCertifiedOrderOperation('confirm', O, { ...command('confirm'), quoteId }, f.session));
    }
    await assert.rejects(async () => m.api.submitCertifiedOrderOperation('confirm', O, { ...command('confirm'), expectedVersion: 0 }, f.session));
    assert.equal(f.calls.length, 0);
  });
  run('固定凭证：全局拦截器不得改用当前账号；派发前 token 或 scope 切换阻止 adapter', async (f, m) => {
    f.request.interceptors.request.use((config) => {
      if (config.headers.get('isToken') !== false) config.headers.set('Authorization', 'Bearer ' + TOKEN_B);
      return config;
    });
    await m.api.submitCertifiedOrderOperation('close', O, command('close'), f.session);
    assert.equal(f.calls[0].authorization, 'Bearer ' + TOKEN);
    f.calls.length = 0;
    f.request.interceptors.request.use((config) => {
      f.credential.value = TOKEN_B;
      return config;
    });
    await assert.rejects(async () => m.api.submitCertifiedOrderOperation('close', O, command('close'), f.session));
    assert.equal(f.calls.length, 0);
  });
  run('默认 serializer 之后的 token/scope 切换也必须在实际 dispatch 前阻断', async (f, m) => {
    f.request.defaults.transformRequest = [
      ...f.request.defaults.transformRequest,
      (body) => {
        f.scopeCurrent = false;
        return body;
      }
    ];
    await assert.rejects(async () => m.api.submitCertifiedOrderOperation('cancel', O, command('cancel'), f.session));
    assert.equal(f.calls.length, 0);
  });
  run('响应迟到遇到账号变化不能把原响应认作新账号成功，也不重发', async (f, m) => {
    f.gate = deferred();
    const pending = m.api.submitCertifiedOrderOperation('close', O, command('close'), f.session);
    await new Promise((r) => setTimeout(r, 0));
    assert.equal(f.calls.length, 1);
    f.credential.value = TOKEN_B;
    f.gate.resolve();
    await assert.rejects(pending, (error) => !error.message.includes(TOKEN));
    assert.equal(f.calls.length, 1);
  });
  for (const [status, code, refusal] of [
    [200, 200, false],
    [200, 403, true],
    [403, 403, true],
    [409, 409, true],
    [500, 403, false],
    [403, 500, false],
    [200, 500, false],
    [201, 200, false],
    [204, 200, false],
    [400, 200, false],
    [200, '200', false]
  ]) {
    run(`真实 HTTP ${status}/R ${String(code)} 只按严格组合判断成功/明确拒绝/UNKNOWN`, async (f, m) => {
      f.status = status;
      f.code = code;
      if ((status === 200 && code === 200) || refusal) {
        const result = await m.api.submitCertifiedOrderOperation('close', O, command('close'), f.session);
        assert.equal(result.code, code);
        assert.ok(!JSON.stringify(result).includes(TOKEN));
        if (!refusal) assert.deepEqual(result, { code: 200, data: f.data });
      } else
        await assert.rejects(
          async () => m.api.submitCertifiedOrderOperation('close', O, command('close'), f.session),
          (error) => !JSON.stringify(error).includes(TOKEN) && !error.message.includes(TOKEN)
        );
      assert.equal(f.calls.length, 1);
    });
  }
  run('网络失败仅发送一次；原错误、config、token 不外显，原意图不释放', async (f, m) => {
    const scope = { tenantId: '000001', userId: f.userId, feature: 'certified-order', documentId: O };
    const store = m.helper.createBusinessIntentStore(f.storage);
    store.begin(scope, 'cancel', 'api-network-pending');
    f.network = true;
    await assert.rejects(
      async () => m.api.submitCertifiedOrderOperation('cancel', O, command('cancel'), f.session),
      (error) => {
        assert.equal(error.config, undefined);
        assert.equal(error.cause, undefined);
        assert.ok(!error.message.includes(TOKEN));
        return true;
      }
    );
    assert.equal(f.calls.length, 1);
    assert.equal(store.isBlocked(scope), true);
  });
  for (const name of ['getPurchaseOrder', 'updatePurchaseOrder', 'delPurchaseOrder', 'receivePurchaseOrder']) {
    const invoke = (m, f) => (name === 'updatePurchaseOrder' ? m.api[name]({ orderId: O }, f.session) : m.api[name](O, f.session));
    run(`旧${name}必须拒绝已经失效的固定会话`, async (f, m) => {
      f.credential.value = TOKEN_B;
      await assert.rejects(async () => invoke(m, f));
      assert.equal(f.calls.length, 0);
    });
    run(`旧${name}实际派发使用原Bearer及最后assertCurrent`, async (f, m) => {
      await invoke(m, f);
      assert.equal(f.calls.length, 1);
      assert.equal(f.calls[0].authorization, 'Bearer ' + TOKEN);
      assert.ok(f.assertions >= 2);
    });
    run(`旧${name}请求拦截器中切换账号不能进入adapter`, async (f, m) => {
      f.request.interceptors.request.use((config) => {
        f.credential.value = TOKEN_B;
        return config;
      });
      await assert.rejects(async () => invoke(m, f));
      assert.equal(f.calls.length, 0);
    });
  }
}
