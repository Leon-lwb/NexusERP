import assert from 'node:assert/strict';
import { existsSync, readFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import { build } from 'esbuild';
import ts from 'typescript';
import axios from 'axios';

export const uiRoot = fileURLToPath(new URL('..', import.meta.url));
export const controllerPath = join(uiRoot, 'src/views/erp/bd/measure/quantityConfirmation.ts');
export const componentPath = join(uiRoot, 'src/views/erp/bd/measure/CertifiedQuantityConfirmationDialog.vue');
export const ID = '9007199254740993';
export const ROOT_ID = '9007199254740995';
export const OTHER_ID = '9007199254740997';
export const context = (patch = {}) => ({ tenantId: '000001', userId: '101', confirmAllowed: true, ...patch });
export const unit = (patch = {}) => ({
  uomId: ID,
  uomCode: 'Q-SAMPLE',
  uomName: '合成箱',
  uomSymbol: '箱',
  category: '数量',
  isBase: '0',
  primaryId: ROOT_ID,
  primaryName: '合成件',
  status: '1',
  conversionRate: null,
  quantityScale: null,
  conversionVersion: null,
  conversionVerifiedAt: null,
  ...patch
});
export const rootUnit = (patch = {}) =>
  unit({
    uomId: ROOT_ID,
    uomCode: 'Q-ROOT',
    uomName: '合成件',
    uomSymbol: '件',
    isBase: '1',
    primaryId: ROOT_ID,
    conversionRate: '1',
    quantityScale: 0,
    conversionVersion: 'Q-existing-root',
    conversionVerifiedAt: '2026-10-08 09:00:00',
    ...patch
  });
export const copy = (value) => JSON.parse(JSON.stringify(value));
export const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
};
export function memoryStorage() {
  const values = new Map();
  return {
    values,
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key)
  };
}

/** 独占临时 esbuild 输出；真实共享 helper，只替换离线 HTTP 及凭证读取边界。 */
export async function loadQuantityModules(componentSource) {
  process.env.GOMAXPROCS = '1';
  const directory = mkdtempSync(join(tmpdir(), 'nexus-quantity-'));
  const outfile = join(directory, 'quantity.cjs');
  const entry =
    componentSource ??
    `export * as controller from '${controllerPath.replaceAll('\\', '/')}';
    export * as api from '${join(uiRoot, 'src/api/erp/bd/measure/index.ts').replaceAll('\\', '/')}';
    export * as helper from '${join(uiRoot, 'src/utils/certified-business.ts').replaceAll('\\', '/')}';`;
  try {
    await build({
      stdin: { contents: entry, resolveDir: componentSource ? dirname(componentPath) : uiRoot, loader: 'ts' },
      bundle: true,
      platform: 'node',
      format: 'cjs',
      outfile,
      packages: 'external',
      logLevel: 'silent',
      define: { 'import.meta.env': JSON.stringify({ VITE_APP_BASE_API: '/dev-api', VITE_APP_CLIENT_ID: 'offline', VITE_APP_ENCRYPT: 'false' }) },
      plugins: [
        {
          name: 'quantity-offline-ports',
          setup(b) {
            b.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'request', namespace: 'quantity-port' }));
            b.onResolve({ filter: /^@\/utils\/auth$/ }, () => ({ path: 'auth', namespace: 'quantity-port' }));
            b.onResolve({ filter: /^vue-router$/ }, () => ({ path: 'route', namespace: 'quantity-port' }));
            b.onResolve({ filter: /^@\// }, (a) => {
              const path = resolve(uiRoot, 'src', a.path.slice(2));
              return { path: existsSync(join(path, 'index.ts')) ? join(path, 'index.ts') : path + (path.endsWith('.vue') ? '' : '.ts') };
            });
            b.onLoad({ filter: /.*/, namespace: 'quantity-port' }, (a) => ({
              contents:
                a.path === 'request'
                  ? 'const request = (...args) => globalThis.__quantityRequest(...args); Object.defineProperty(request, "defaults", { get: () => globalThis.__quantityRequest.defaults }); export default request;'
                  : a.path === 'auth'
                    ? 'export const getToken = () => globalThis.__quantityCredential?.value ?? globalThis.__quantityCredential;'
                    : 'export const useRoute = () => globalThis.__quantityRoute;',
              loader: 'ts'
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
    assert.ok(directory.startsWith(join(tmpdir(), 'nexus-quantity-')));
    rmSync(directory, { recursive: true, force: true });
  }
}

let fixtureCounter = 1000;

/** 合成 HTTP 适配器运行真实 API/会话/持久意图，无真实业务连接。 */
export function fixture(modules, patch = {}) {
  const f = {
    credential: 'quantity-offline-a',
    route: '/measure',
    selected: ID,
    ctx: context({ userId: String(++fixtureCounter) }),
    rows: new Map([
      [ID, unit()],
      [ROOT_ID, rootUnit()],
      [OTHER_ID, unit({ uomId: OTHER_ID })]
    ]),
    calls: [],
    posts: [],
    outcome: 'success',
    storage: memoryStorage(),
    ...patch
  };
  globalThis.__quantityRequest = axios.create({
    adapter: async (config) => {
      f.calls.push({ url: config.url, method: config.method, params: config.params, token: config.headers?.Authorization });
      let data;
      if (config.method === 'post') {
        const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data;
        f.posts.push({ id: config.url.split('/').at(-2), body: copy(body) });
        if (f.postGate) await f.postGate.promise;
        if (f.outcome === 'network') throw new Error('raw secret quantity-offline-a');
        if (f.outcome === 'http-500' || f.outcome === 'http-403' || f.outcome === 'http403-r500') {
          const response = {
            status: f.outcome === 'http-500' ? 500 : 403,
            data: { code: f.outcome === 'http403-r500' ? 500 : 403, msg: 'raw secret' },
            config,
            headers: {}
          };
          throw new axios.AxiosError('raw secret', 'ERR_BAD_RESPONSE', config, null, response);
        }
        if (f.outcome === 'r-500' || f.outcome === 'r-403') data = { code: f.outcome === 'r-500' ? 500 : 403, msg: 'raw secret' };
        else if (f.outcome === 'malformed') data = { code: 200, data: { verified: true } };
        else if (f.outcome === 'code-string') data = { code: '200', data: unit() };
        else if (f.outcome === 'data-null') data = { code: 200, data: null };
        else if (f.outcome === 'data-array') data = { code: 200, data: [] };
        else {
          const id = f.posts.at(-1).id;
          const confirmed = unit({
            ...f.rows.get(id),
            primaryId: body.primaryId,
            conversionRate: body.conversionRate,
            quantityScale: body.quantityScale,
            conversionVersion: 'Q-next-family',
            conversionVerifiedAt: '2026-10-09 09:00:00'
          });
          f.rows.set(id, confirmed);
          if (id !== ROOT_ID) f.rows.set(ROOT_ID, { ...f.rows.get(ROOT_ID), conversionVersion: 'Q-next-family' });
          data = { code: 200, data: copy(confirmed) };
          if (f.afterWrite) f.afterWrite(f);
        }
      } else if (config.url.endsWith('/quantity-context')) {
        if (f.contextGate) await f.contextGate.promise;
        data = { code: 200, data: copy(f.ctx) };
      } else {
        const id = config.url.split('/').at(-1);
        const captured = copy(f.rows.get(id) ?? null);
        if (f.detailGate) await f.detailGate.promise;
        if (f.afterDetail) f.afterDetail(f, id);
        data = { code: 200, data: captured };
      }
      return { status: 200, statusText: 'OK', config, headers: {}, data };
    }
  });
  globalThis.__quantityRequest.interceptors.response.use((response) => response.data);
  f.make = () =>
    modules.controller.createQuantityConfirmation({
      api: {
        getContext: modules.api.getQuantityConfirmationContext,
        getDetail: modules.api.getQuantityConfirmationDetail,
        confirm: modules.api.confirmMeasureQuantity
      },
      storage: f.storage,
      readCredential: () => f.credential,
      readDocumentId: () => f.selected,
      readRouteKey: () => f.route,
      requestKey: () => '00000000-0000-4000-8000-000000000001',
      now: () => Date.parse('2026-10-09T12:00:00+08:00')
    });
  f.flow = f.make();
  f.fill = (patch = {}) =>
    Object.assign(f.flow.state.form, { conversionRate: '12.500000', quantityScale: '2', reference: '合成规格书第3页', ...patch });
  return f;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  test('新增 API 真实导出只读 context/detail 与既有数量 BO 写入口', () => {
    const output = ts.transpileModule(readFileSync(join(uiRoot, 'src/api/erp/bd/measure/index.ts'), 'utf8'), {
      compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
    }).outputText;
    const exports = {};
    new Function('exports', 'require', output)(exports, () => ({}));
    for (const name of ['getQuantityConfirmationContext', 'getQuantityConfirmationDetail', 'confirmMeasureQuantity']) {
      assert.equal(typeof exports[name], 'function', `缺少独立数量 API ${name}`);
    }
  });
  test('数量控制器及独立 query-only 后端必须存在（功能缺失 RED）', () => {
    assert.ok(existsSync(controllerPath), '缺少 quantityConfirmation.ts');
    for (const part of ['controller/BdQuantityConfirmationContextController.java', 'service/BdQuantityConfirmationContextService.java']) {
      assert.ok(existsSync(join(uiRoot, '../ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd', part)), `缺少 ${part}`);
    }
  });
  let loaded;
  const modules = async () => (loaded ??= await loadQuantityModules());
  const run = (name, fn) => test(name, async () => fn(fixture(await modules()), await modules()));

  run('query-only 可看原生详情；NULL 原版本保留，表单不自动填转换率/精度/证据', async (f) => {
    f.ctx.confirmAllowed = false;
    await f.flow.open(ID);
    assert.equal(f.flow.state.snapshot.unit.conversionVersion, null);
    assert.deepEqual(f.flow.state.form, { conversionRate: '', quantityScale: '', reference: '' });
    f.fill();
    assert.equal(f.flow.canSubmit(), false);
    await f.flow.submit();
    assert.equal(f.posts.length, 0);
    assert.equal(f.calls[0].url, '/erp/bd/measure/quantity-context');
  });
  run('显式资料成功写入只包含 BO 字段、decimal 字符串、NULL 原版本；真实回读后解锁', async (f) => {
    await f.flow.open(ID);
    f.fill();
    assert.equal(await f.flow.submit(), true);
    assert.equal(f.posts.length, 1);
    assert.deepEqual(f.posts[0].body, {
      expectedVersion: null,
      primaryId: ROOT_ID,
      conversionRate: '12.500000',
      quantityScale: 2,
      reference: '合成规格书第3页'
    });
    assert.equal(f.flow.state.phase, 'confirmed');
    assert.equal(f.flow.isBlocked(), false);
    assert.ok(f.calls.filter((c) => c.url === '/erp/bd/measure/' + ID).length >= 3);
  });
  for (const bad of [
    { primaryId: null },
    { isBase: 'true' },
    { category: '' },
    { status: '0' },
    { conversionVersion: '' },
    { conversionVersion: undefined },
    { conversionVerifiedAt: '2026-02-30 08:00:00' },
    { conversionVerifiedAt: '2026-10-10 08:00:00' },
    { uomId: 9007199254740993 }
  ]) {
    run(`损坏单位合同不猜修复 ${JSON.stringify(bad)}`, async (f) => {
      f.rows.set(ID, unit(bad));
      await f.flow.open(ID);
      f.fill();
      await f.flow.submit();
      assert.equal(f.posts.length, 0);
      assert.equal(f.flow.canSubmit(), false);
    });
  }
  run('衍生单位根详情必须是同维度、已核验恒等自映射且非多级链', async (f) => {
    for (const bad of [{ category: '重量' }, { primaryId: OTHER_ID }, { conversionRate: '2' }, { conversionVersion: null }, { quantityScale: 5 }]) {
      f.rows.set(ROOT_ID, rootUnit(bad));
      await f.flow.open(ID);
      f.fill();
      await f.flow.submit();
      assert.equal(f.posts.length, 0);
    }
  });
  run('明确根自映射仅提示，必须手填一及精度；无实际 primaryId 阻塞', async (f) => {
    f.selected = ROOT_ID;
    await f.flow.open(ROOT_ID);
    assert.equal(f.flow.state.form.conversionRate, '');
    f.fill({ conversionRate: '2' });
    await f.flow.submit();
    assert.equal(f.posts.length, 0);
    f.rows.set(ROOT_ID, rootUnit({ primaryId: null }));
    await f.flow.open(ROOT_ID);
    f.fill({ conversionRate: '1' });
    await f.flow.submit();
    assert.equal(f.posts.length, 0);
  });
  for (const patch of [
    { conversionRate: '' },
    { conversionRate: '1e3' },
    { conversionRate: '0' },
    { conversionRate: '9999999999999' },
    { conversionRate: '1.1234567' },
    { conversionRate: '1.0000000' },
    { quantityScale: '' },
    { quantityScale: '2.5' },
    { quantityScale: '5' },
    { reference: ' ' }
  ]) {
    run(`人工资料不明确/超精度不得发送 ${JSON.stringify(patch)}`, async (f) => {
      await f.flow.open(ID);
      f.fill(patch);
      await f.flow.submit();
      assert.equal(f.posts.length, 0);
    });
  }
  for (const change of ['tenant', 'user', 'permission', 'version', 'root', 'category']) {
    run(`写前 fresh context/detail ${change} 改变必须重新打开`, async (f) => {
      await f.flow.open(ID);
      f.fill();
      if (change === 'tenant') f.ctx.tenantId = '000002';
      if (change === 'user') f.ctx.userId = '999999';
      if (change === 'permission') f.ctx.confirmAllowed = false;
      if (change === 'version') f.rows.set(ID, unit({ conversionVersion: 'Q-changed' }));
      if (change === 'root') f.rows.set(ID, unit({ primaryId: OTHER_ID }));
      if (change === 'category') f.rows.set(ID, unit({ category: '长度' }));
      await f.flow.submit();
      assert.equal(f.posts.length, 0);
    });
  }
  run('双击只发一次；持久意图先于 HTTP 派发', async (f) => {
    await f.flow.open(ID);
    f.fill();
    f.postGate = deferred();
    const first = f.flow.submit();
    for (let n = 0; n < 30 && !f.posts.length; n++) await new Promise((r) => setTimeout(r, 0));
    assert.equal(f.posts.length, 1);
    assert.ok(f.storage.values.size > 0);
    await f.flow.submit();
    assert.equal(f.posts.length, 1);
    f.postGate.resolve();
    await first;
  });
  for (const outcome of ['network', 'http-500', 'r-500', 'malformed', 'code-string', 'data-null', 'data-array', 'http403-r500']) {
    run(`UNKNOWN ${outcome} 不因开关/新版本/新实例解锁`, async (f) => {
      await f.flow.open(ID);
      f.fill();
      f.outcome = outcome;
      await f.flow.submit();
      assert.equal(f.flow.isBlocked(), true);
      assert.equal(f.flow.state.phase, 'unknown');
      assert.ok(!f.flow.state.message.includes('raw secret'));
      f.flow.close();
      f.rows.set(ID, unit({ conversionVersion: 'Q-later', conversionRate: '12.5', quantityScale: 2, conversionVerifiedAt: '2026-10-09 09:00:00' }));
      f.flow = f.make();
      await f.flow.open(ID);
      f.fill();
      await f.flow.submit();
      assert.equal(f.posts.length, 1);
      assert.equal(f.flow.isBlocked(), true);
    });
  }
  for (const outcome of ['http-403', 'r-403']) {
    run(`明确拒绝 ${outcome} 可释放但不自动重发`, async (f) => {
      await f.flow.open(ID);
      f.fill();
      f.outcome = outcome;
      await f.flow.submit();
      assert.equal(f.posts.length, 1);
      assert.equal(f.flow.isBlocked(), false);
      assert.equal(f.flow.state.phase, 'rejected');
    });
  }
  for (const field of ['uomId', 'conversionVersion', 'conversionRate', 'quantityScale', 'actor']) {
    run(`R200 后原生回读 ${field} 不符保留 UNKNOWN`, async (f) => {
      await f.flow.open(ID);
      f.fill();
      f.afterWrite = () => {
        if (field === 'actor') f.ctx.userId = '999999';
        else
          f.rows.set(ID, {
            ...f.rows.get(ID),
            [field]: { uomId: OTHER_ID, conversionVersion: 'Q-other', conversionRate: '3', quantityScale: 1 }[field]
          });
      };
      await f.flow.submit();
      assert.equal(f.flow.isBlocked(), true);
      assert.equal(f.flow.state.phase, 'unknown');
    });
  }
  for (const change of ['document', 'route', 'credential', 'close']) {
    run(`迟到 POST ${change} 切换不写错对象、不释放原意图`, async (f) => {
      await f.flow.open(ID);
      f.fill();
      f.postGate = deferred();
      const pending = f.flow.submit();
      for (let n = 0; n < 30 && !f.posts.length; n++) await new Promise((r) => setTimeout(r, 0));
      assert.equal(f.posts.length, 1);
      if (change === 'document') f.selected = OTHER_ID;
      if (change === 'route') f.route = '/elsewhere';
      if (change === 'credential') f.credential = 'quantity-offline-b';
      if (change === 'close') f.flow.close();
      f.postGate.resolve();
      assert.equal(await pending, false);
      f.selected = ID;
      f.route = '/measure';
      f.credential = 'quantity-offline-a';
      f.flow = f.make();
      await f.flow.open(ID);
      assert.equal(f.flow.isBlocked(), true);
    });
  }
  run('迟到 GET 不覆盖新单位的 snapshot', async (f) => {
    f.detailGate = deferred();
    const old = f.flow.open(ID);
    for (let n = 0; n < 5; n++) await new Promise((r) => setTimeout(r, 0));
    const gate = f.detailGate;
    f.detailGate = null;
    f.selected = OTHER_ID;
    await f.flow.open(OTHER_ID);
    gate.resolve();
    await old;
    assert.equal(f.flow.state.snapshot.unit.uomId, OTHER_ID);
  });
  run('存储失败不得派发；普通编辑仍不自动补证', async (f) => {
    await f.flow.open(ID);
    f.fill();
    f.storage.setItem = () => {
      throw new Error('storage unavailable');
    };
    await f.flow.submit();
    assert.equal(f.posts.length, 0);
    const source = readFileSync(join(uiRoot, 'src/views/erp/bd/measure/index.vue'), 'utf8');
    const submit = source.slice(source.indexOf('function submitForm'), source.indexOf('function submitForm') + 2600);
    assert.ok(!submit.includes('confirmMeasureQuantity'));
  });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  let loaded;
  const run = (name, fn) =>
    test(name, async () => {
      loaded ??= await loadQuantityModules();
      await fn(fixture(loaded), loaded);
    });
  for (const field of ['tenantId', 'userId', 'confirmAllowed']) {
    run(`fresh 明细读取途中 ${field} 切换在 POST 前封闭`, async (f) => {
      await f.flow.open(ID);
      f.fill();
      f.afterDetail = () => {
        f.ctx[field] = { tenantId: '000009', userId: '999999', confirmAllowed: false }[field];
      };
      await f.flow.submit();
      assert.equal(f.posts.length, 0, '读完实际单位后必须复核主体');
    });
  }
  run('原始字符串族版本逐字提交，不修剪、不走订单整数版本函数', async (f) => {
    const original = ' Q-original-family ';
    f.rows.set(ID, unit({ conversionRate: '12.50', quantityScale: 2, conversionVersion: original, conversionVerifiedAt: '2026-10-08 09:00:00' }));
    f.rows.set(ROOT_ID, rootUnit({ conversionVersion: original }));
    await f.flow.open(ID);
    f.fill();
    assert.equal(await f.flow.submit(), true);
    assert.equal(f.posts[0].body.expectedVersion, original);
  });
  run('最大合法高精度 decimal 保持文本，API 剥离所有额外认证/审计/意图字段', async (f, modules) => {
    await f.flow.open(ID);
    f.fill({ conversionRate: '999999999999.123456' });
    assert.equal(await f.flow.submit(), true);
    assert.equal(f.posts[0].body.conversionRate, '999999999999.123456');
    const session = modules.helper.createBusinessSession(() => f.credential);
    await modules.api.confirmMeasureQuantity(
      ID,
      {
        expectedVersion: null,
        primaryId: ROOT_ID,
        conversionRate: '2.000000',
        quantityScale: 0,
        reference: '合成资料',
        verified: true,
        actorId: '888',
        time: 'now',
        tenantId: '000001',
        requestKey: 'unsafe-key'
      },
      session
    );
    assert.deepEqual(Object.keys(f.posts[1].body).sort(), ['conversionRate', 'expectedVersion', 'primaryId', 'quantityScale', 'reference'].sort());
  });
  run('首次根确认只依据明确 isBase=1 自映射合同；保留原生 NULL 映射与版本，不偷偷填一', async (f) => {
    f.selected = ROOT_ID;
    f.rows.set(
      ROOT_ID,
      rootUnit({ primaryId: null, conversionRate: null, quantityScale: null, conversionVersion: null, conversionVerifiedAt: null })
    );
    await f.flow.open(ROOT_ID);
    assert.equal(f.flow.state.snapshot?.unit.primaryId, null, '服务器原始快照不能被改成自映射');
    assert.equal(f.flow.state.form.conversionRate, '');
    f.fill({ conversionRate: '1.000000', quantityScale: '0' });
    assert.equal(await f.flow.submit(), true);
    assert.deepEqual(f.posts[0].body, {
      expectedVersion: null,
      primaryId: ROOT_ID,
      conversionRate: '1.000000',
      quantityScale: 0,
      reference: '合成规格书第3页'
    });
  });
  run('明确根自映射实际确认成功但不默认一，不接受既有损坏根恒等率', async (f) => {
    f.selected = ROOT_ID;
    await f.flow.open(ROOT_ID);
    assert.equal(f.flow.state.form.conversionRate, '');
    f.fill({ conversionRate: '1.000000', quantityScale: '0' });
    assert.equal(await f.flow.submit(), true);
    assert.equal(f.posts[0].body.primaryId, ROOT_ID);
    f.rows.set(ROOT_ID, rootUnit({ conversionRate: '2' }));
    await f.flow.open(ROOT_ID);
    f.fill({ conversionRate: '1' });
    await f.flow.submit();
    assert.equal(f.posts.length, 1);
  });
  run('UNKNOWN 已锁存后读取存储失败/重开仍不可重发', async (f) => {
    await f.flow.open(ID);
    f.fill();
    f.outcome = 'network';
    await f.flow.submit();
    f.storage.getItem = () => {
      throw new Error('合成 storage 读取失败');
    };
    f.flow = f.make();
    await f.flow.open(ID);
    f.fill();
    await f.flow.submit();
    assert.equal(f.posts.length, 1);
    assert.equal(f.flow.isBlocked(), true);
  });
  run('写响应及原生回读成功但 durable release 失败保留 UNKNOWN', async (f) => {
    await f.flow.open(ID);
    f.fill();
    f.afterWrite = () => {
      f.storage.removeItem = () => {
        throw new Error('合成 fence 移除失败');
      };
    };
    await f.flow.submit();
    assert.equal(f.posts.length, 1);
    assert.equal(f.flow.isBlocked(), true);
    assert.equal(f.flow.state.phase, 'unknown');
    f.flow.close();
    f.flow = f.make();
    await f.flow.open(ID);
    f.fill();
    await f.flow.submit();
    assert.equal(f.posts.length, 1);
  });
  run('返回新版本但核验时间比原版本倒退不能认定完成', async (f) => {
    f.rows.set(
      ID,
      unit({ conversionRate: '12.5', quantityScale: 2, conversionVersion: 'Q-existing-root', conversionVerifiedAt: '2026-10-08 09:00:00' })
    );
    await f.flow.open(ID);
    f.fill();
    const originalRequest = globalThis.__quantityRequest;
    originalRequest.interceptors.response.use((r) => {
      if (r.data?.conversionVersion === 'Q-next-family') r.data.conversionVerifiedAt = '2026-10-07 09:00:00';
      return r;
    });
    f.afterWrite = () => {
      f.rows.get(ID).conversionVerifiedAt = '2026-10-07 09:00:00';
    };
    await f.flow.submit();
    assert.equal(f.flow.isBlocked(), true);
    assert.equal(f.flow.state.phase, 'unknown');
  });
}
