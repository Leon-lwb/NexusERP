import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, isAbsolute, join, relative, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';
import axios from 'axios';
import ts from 'typescript';

// 每次独立输出，不争用其他 worker 的构建目录；只替换网络边界，绝不调用真实 API。
process.env.GOMAXPROCS = '1';
const source = fileURLToPath(new URL('../src/utils/certified-business.ts', import.meta.url));
const present = existsSync(source);
const requestSlot = Symbol.for('nexus:certified-business:offline-request');
let outputDirectory;
let output;
let sequence = 0;
if (present) {
  outputDirectory = mkdtempSync(join(tmpdir(), 'nexus-certified-business-'));
  output = join(outputDirectory, 'business.mjs');
  const { build } = await import('esbuild');
  await build({
    entryPoints: [source],
    outfile: output,
    bundle: true,
    format: 'esm',
    platform: 'node',
    target: 'es2022',
    logLevel: 'silent',
    plugins: [
      {
        name: 'offline-normal-request-boundary',
        setup(builder) {
          builder.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'request', namespace: 'offline' }));
          builder.onLoad({ filter: /.*/, namespace: 'offline' }, () => ({
            contents: "export default globalThis[Symbol.for('nexus:certified-business:offline-request')];",
            loader: 'js'
          }));
        }
      }
    ]
  });
}
process.on('exit', () => {
  if (!outputDirectory) return;
  const directory = resolve(outputDirectory);
  const withinTemp = relative(resolve(tmpdir()), directory);
  // Windows 递归删除只允许已核对的本次独立临时目录。
  if (withinTemp && !withinTemp.startsWith('..') && !isAbsolute(withinTemp) && basename(directory).startsWith('nexus-certified-business-')) {
    rmSync(directory, { recursive: true, force: true });
  }
});

async function loadApi(request = axios.create()) {
  assert.ok(present, '共享业务护栏尚未实现：必须先提供实际 certified-business.ts，而不是加载错误或伪造导出');
  globalThis[requestSlot] = request;
  try {
    return await import(pathToFileURL(output).href + '?instance=' + ++sequence);
  } finally {
    delete globalThis[requestSlot];
  }
}

const TOKEN_A = 'offline-certified-business-a';
const TOKEN_B = 'offline-certified-business-b';
const RAW_ERROR = '原始错误与凭证不得外显 ' + TOKEN_A;
const ID = '9007199254740993';
const MAX_ID = '9223372036854775807';
const ok = (data) => ({ code: 200, data, msg: '业务成功' });
const scope = (patch = {}) => ({ tenantId: '000001', userId: '101', feature: 'purchase-order', documentId: ID, ...patch });

function safeError(error) {
  assert.ok(error instanceof Error);
  assert.ok(error.message.length > 0);
  assert.doesNotMatch(error.message, /offline-certified-business|原始错误|Authorization|Bearer/);
  assert.equal(error.cause, undefined, '不得经 cause 或 Axios config 回传原始凭证');
  assert.equal(error.config, undefined);
  assert.equal(error.response, undefined);
  return true;
}

/** 真实 Axios 变换、请求/响应拦截器和 HTTP 结算；只有 adapter 不联网。 */
async function transport() {
  const f = { token: TOKEN_A, status: 200, envelope: ok({ documentId: ID }), calls: [], intercepted: 0 };
  const request = axios.create({
    adapter: async (config) => {
      f.calls.push({
        url: config.url,
        method: config.method,
        params: config.params,
        body: config.data === undefined ? undefined : JSON.parse(config.data),
        authorization: config.headers.get('Authorization'),
        isToken: config.headers.get('isToken'),
        repeatSubmit: config.headers.get('repeatSubmit')
      });
      f.afterDispatch?.();
      if (f.networkFailure) throw new Error(RAW_ERROR);
      const response = { data: f.rawBody ?? JSON.stringify(f.envelope), status: f.status, statusText: 'offline', headers: {}, config, request: {} };
      if (typeof config.validateStatus === 'function' && !config.validateStatus(response.status)) {
        throw new axios.AxiosError(RAW_ERROR, axios.AxiosError.ERR_BAD_RESPONSE, config, {}, response);
      }
      return response;
    }
  });
  request.interceptors.request.use((config) => {
    f.intercepted++;
    // 对齐普通请求拦截器的换证行为；共享 API 必须以 isToken:false 阻止它替换固定 token。
    if (config.headers.get('isToken') !== false) config.headers.set('Authorization', 'Bearer ' + f.token);
    f.beforeDispatch?.(config);
    return config;
  });
  request.interceptors.response.use((response) => {
    if (f.rewrite !== undefined) return f.rewrite;
    if (response.data?.code !== 200) throw new Error(RAW_ERROR);
    return response.data;
  });
  f.request = request;
  f.api = await loadApi(request);
  f.session = () => f.api.createBusinessSession(() => f.token, f.assertScope);
  return f;
}

function memoryStorage() {
  const values = new Map();
  const calls = [];
  const storage = {
    values,
    calls,
    getItem(key) {
      calls.push({ method: 'get', key });
      storage.before?.('get', key);
      const value = values.has(key) ? values.get(key) : null;
      storage.after?.('get', key);
      return value;
    },
    setItem(key, value) {
      calls.push({ method: 'set', key, value });
      storage.before?.('set', key);
      values.set(key, value);
      storage.after?.('set', key);
    },
    removeItem(key) {
      calls.push({ method: 'remove', key });
      storage.before?.('remove', key);
      values.delete(key);
      storage.after?.('remove', key);
    }
  };
  return storage;
}

async function storageFixture() {
  const api = await loadApi();
  const storage = memoryStorage();
  const store = api.createBusinessIntentStore(storage);
  const currentScope = scope();
  const intent = store.begin(currentScope, 'approve', 'offline-original-request-key');
  const primary = [...storage.values.keys()].find((key) => !key.endsWith(':release-fence'));
  return { api, storage, store, scope: currentScope, intent, primary, fence: primary + ':release-fence' };
}

function typeDiagnostics() {
  // 只检查共享实现和确定导出契约，request 边界使用真实 AxiosInstance 声明，不载入整个前端。
  const directory = resolve(source, '..');
  const declaration = join(directory, '__shared_business_request__.d.ts');
  const contract = join(directory, '__shared_business_contract__.ts');
  const virtual = new Map([
    [declaration, "import type { AxiosInstance } from 'axios'; declare const request: AxiosInstance; export default request;"],
    [
      contract,
      `import { normalizeBusinessId, normalizeBusinessVersion, createBusinessSession, readCertifiedBusiness, postCertifiedBusiness,
        createBusinessIntentStore, type CertifiedBusinessSession, type CertifiedBusinessResponse, type BusinessScope, type BusinessIntent } from './certified-business';
      const id: (value: unknown) => string = normalizeBusinessId;
      const version: (value: unknown) => number = normalizeBusinessVersion;
      const session: (read: () => string | null | undefined, scope?: () => void) => CertifiedBusinessSession = createBusinessSession;
      const read: <T>(url: string, session: CertifiedBusinessSession, params?: object) => Promise<T> = readCertifiedBusiness;
      const post: <T>(url: string, body: object | undefined, session: CertifiedBusinessSession) => Promise<CertifiedBusinessResponse<T>> = postCertifiedBusiness;
      const scope: BusinessScope = { tenantId: '000001', userId: '101', feature: 'orders', documentId: '1' };
      const store = createBusinessIntentStore({ getItem: (key: string): string | null => null, setItem: (key: string, value: string): void => {}, removeItem: (key: string): void => {} });
      const blocked: boolean = store.isBlocked(scope);
      const intent: BusinessIntent = store.begin(scope, 'approve', 'request-key');
      const s: BusinessScope = intent.scope;
      const operation: string = intent.operation;
      const requestKey: string = intent.requestKey;
      const nonce: string = intent.nonce;
      const pending: void = store.assertPending(intent);
      const released: void = store.release(intent);`
    ]
  ]);
  const options = {
    target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.ESNext,
    moduleResolution: ts.ModuleResolutionKind.Bundler,
    strict: true,
    skipLibCheck: true,
    noEmit: true,
    types: [],
    baseUrl: directory,
    paths: { '@/utils/request': [declaration] }
  };
  const host = ts.createCompilerHost(options);
  const originalRead = host.readFile.bind(host);
  const originalExists = host.fileExists.bind(host);
  host.readFile = (path) => virtual.get(resolve(path)) ?? originalRead(path);
  host.fileExists = (path) => virtual.has(resolve(path)) || originalExists(path);
  host.getSourceFile = (path, languageVersion) => {
    const text = host.readFile(path);
    return text === undefined ? undefined : ts.createSourceFile(path, text, languageVersion);
  };
  return ts.getPreEmitDiagnostics(ts.createProgram([source, contract], options, host));
}

test('共享护栏必须存在且实际导出六个确定函数：缺失功能产生断言 RED', async () => {
  const api = await loadApi();
  for (const name of [
    'normalizeBusinessId',
    'normalizeBusinessVersion',
    'createBusinessSession',
    'readCertifiedBusiness',
    'postCertifiedBusiness',
    'createBusinessIntentStore'
  ]) {
    assert.equal(typeof api[name], 'function', `缺少共享护栏函数 ${name}`);
  }
});

if (present) {
  test('定向 TypeScript 严格检查实现及并行调用者导出契约', () => {
    const diagnostics = typeDiagnostics();
    assert.equal(
      diagnostics.length,
      0,
      ts.formatDiagnosticsWithColorAndContext(diagnostics, {
        getCurrentDirectory: () => process.cwd(),
        getCanonicalFileName: (file) => file,
        getNewLine: () => '\n'
      })
    );
  });
  for (const value of ['1', ID, MAX_ID, 1, Number.MAX_SAFE_INTEGER]) {
    test(`精确业务 ID 接受 canonical ${String(value)}`, async () => {
      const api = await loadApi();
      assert.equal(api.normalizeBusinessId(value), String(value));
    });
  }
  for (const value of [
    null,
    undefined,
    true,
    {},
    [],
    0,
    -1,
    1.5,
    NaN,
    Infinity,
    Number.MAX_SAFE_INTEGER + 1,
    Number(MAX_ID),
    1n,
    '',
    '0',
    '01',
    '+1',
    '-1',
    ' 1',
    '1 ',
    '1.0',
    '1e3',
    '１',
    '9223372036854775808',
    '99999999999999999999'
  ]) {
    test(`业务 ID 拒绝非 canonical/越界 ${String(value)}`, async () => {
      const api = await loadApi();
      assert.throws(() => api.normalizeBusinessId(value), safeError);
    });
  }
  for (const value of [0, 1, 2147483647]) {
    test(`原生版本接受含边界整数 ${value}`, async () => {
      const api = await loadApi();
      assert.equal(api.normalizeBusinessVersion(value), value);
    });
  }
  for (const value of [null, undefined, '0', '1', true, {}, [], -1, 1.1, 2147483648, NaN, Infinity]) {
    test(`原生版本拒绝缺失/非整数 number ${String(value)}`, async () => {
      const api = await loadApi();
      assert.throws(() => api.normalizeBusinessVersion(value), safeError);
    });
  }
  test('会话固定内存凭证，不跟随换用户，scope 回调在复核时执行', async () => {
    const api = await loadApi();
    let token = TOKEN_A;
    let checks = 0;
    const session = api.createBusinessSession(
      () => token,
      () => checks++
    );
    assert.equal(session.token, TOKEN_A);
    session.assertCurrent();
    assert.ok(checks > 0);
    token = TOKEN_B;
    assert.throws(() => session.assertCurrent(), safeError);
    assert.equal(session.token, TOKEN_A);
  });
  for (const value of [null, undefined, '']) {
    test(`缺失凭证不能建立会话 ${String(value)}`, async () => {
      const api = await loadApi();
      assert.throws(() => api.createBusinessSession(() => value), safeError);
    });
  }
  test('凭证读取和作用域异常全部脱敏，包括 scope 回调中换证', async () => {
    const api = await loadApi();
    assert.throws(
      () =>
        api.createBusinessSession(() => {
          throw new Error(RAW_ERROR);
        }),
      safeError
    );
    let token = TOKEN_A;
    const session = api.createBusinessSession(
      () => token,
      () => {
        token = TOKEN_B;
      }
    );
    assert.throws(() => session.assertCurrent(), safeError);
    const badScope = api.createBusinessSession(
      () => TOKEN_A,
      () => {
        throw new Error(RAW_ERROR);
      }
    );
    assert.throws(() => badScope.assertCurrent(), safeError);
  });
  test('正常 POST 经默认变换发送原 body，固定 Bearer 且禁全局换证/重复缓存', async () => {
    const f = await transport();
    const body = { requestKey: 'same-key', expectedVersion: 2147483647 };
    const result = await f.api.postCertifiedBusiness('/offline/approve', body, f.session());
    assert.equal(result.code, 200);
    assert.deepEqual(result.data, { documentId: ID });
    assert.deepEqual(f.calls[0], {
      url: '/offline/approve',
      method: 'post',
      params: undefined,
      body,
      authorization: 'Bearer ' + TOKEN_A,
      isToken: false,
      repeatSubmit: false
    });
    assert.equal(f.intercepted, 1, '必须经过普通请求拦截器');
  });
  test('无 body 的 POST 仍经正常请求与固定会话', async () => {
    const f = await transport();
    assert.equal((await f.api.postCertifiedBusiness('/offline/no-body', undefined, f.session())).code, 200);
    assert.equal(f.calls[0].body, undefined);
  });
  test('正常 GET 经 params 与固定会话读取，不执行 POST', async () => {
    const f = await transport();
    const params = { documentId: ID };
    assert.deepEqual(await f.api.readCertifiedBusiness('/offline/detail', f.session(), params), { documentId: ID });
    assert.equal(f.calls[0].method, 'get');
    assert.deepEqual(f.calls[0].params, params);
    assert.equal(f.calls[0].authorization, 'Bearer ' + TOKEN_A);
    assert.equal(f.calls[0].isToken, false);
  });
  for (const method of ['get', 'post']) {
    for (const moment of ['beforeCall', 'beforeDispatch', 'afterDispatch']) {
      test(`${method} 凭证在 ${moment} 切换时封闭；dispatch 前切换不得发出请求`, async () => {
        const f = await transport();
        const session = f.session();
        if (moment === 'beforeCall') f.token = TOKEN_B;
        else
          f[moment] = () => {
            f.token = TOKEN_B;
          };
        const action =
          method === 'get' ? f.api.readCertifiedBusiness('/offline/detail', session) : f.api.postCertifiedBusiness('/offline/approve', {}, session);
        await assert.rejects(action, safeError);
        assert.equal(f.calls.length, moment === 'afterDispatch' ? 1 : 0);
      });
    }
  }
  test('默认 transformRequest 中换证仍被最后的 assertCurrent 拦住', async () => {
    const f = await transport();
    const session = f.session();
    f.beforeDispatch = (config) => {
      config.transformRequest.splice(config.transformRequest.length - 1, 0, (body) => {
        f.token = TOKEN_B;
        return body;
      });
    };
    await assert.rejects(f.api.postCertifiedBusiness('/offline/approve', {}, session), safeError);
    assert.equal(f.calls.length, 0);
  });
  test('响应后作用域改变，不得将已派发写入认作成功或可重试拒绝', async () => {
    const f = await transport();
    let current = true;
    f.assertScope = () => {
      if (!current) throw new Error(RAW_ERROR);
    };
    const session = f.session();
    f.afterDispatch = () => {
      current = false;
    };
    await assert.rejects(f.api.postCertifiedBusiness('/offline/approve', {}, session), safeError);
    assert.equal(f.calls.length, 1);
  });
  for (const status of [200, 201, 204, 299, 400, 401, 403, 409, 422, 499]) {
    test(`真实 HTTP ${status} + 数字 R409 才可返回明确拒绝，错误及 msg 不泄露凭证`, async () => {
      const f = await transport();
      f.status = status;
      f.envelope = { code: 409, data: { secret: TOKEN_A }, msg: RAW_ERROR };
      const result = await f.api.postCertifiedBusiness('/offline/approve', {}, f.session());
      assert.equal(result.code, 409);
      assert.equal(result.data, undefined);
      assert.doesNotMatch(JSON.stringify(result), /offline-certified-business|原始错误/);
      assert.equal(f.calls.length, 1);
    });
  }
  for (const [status, envelope] of [
    [201, ok({})],
    [204, ok({})],
    [299, ok({})],
    [400, ok({})],
    [500, ok({})],
    [503, { code: 409 }],
    [0, { code: 409 }],
    [302, { code: 409 }],
    [200, { code: 500 }],
    [200, { code: 503 }],
    [200, {}],
    [200, { code: '200' }],
    [200, { code: '409' }],
    [200, { code: 400.5 }],
    [200, { code: 200, msg: {} }],
    [200, [{ code: 200 }]],
    [200, null]
  ]) {
    test(`POST HTTP ${status}/R ${JSON.stringify(envelope)} 不确定结果抛安全异常且绝不重试`, async () => {
      const f = await transport();
      f.status = status;
      f.envelope = envelope;
      await assert.rejects(f.api.postCertifiedBusiness('/offline/approve', { requestKey: 'original-key' }, f.session()), safeError);
      assert.equal(f.calls.length, 1);
    });
  }
  test('网络失败、非 JSON 200、拦截器伪造 200 都不能冒充提交回执', async () => {
    for (const patch of [{ networkFailure: true }, { rawBody: '<html>bad gateway</html>' }, { envelope: {}, rewrite: ok({}) }]) {
      const f = await transport();
      Object.assign(f, patch);
      await assert.rejects(f.api.postCertifiedBusiness('/offline/approve', {}, f.session()), safeError);
      assert.equal(f.calls.length, 1);
    }
  });
  test('R4xx 到达后凭证变化也必须抛未知结果，不返回可释放的拒绝', async () => {
    const f = await transport();
    f.envelope = { code: 403, msg: RAW_ERROR };
    const session = f.session();
    f.afterDispatch = () => {
      f.token = TOKEN_B;
    };
    await assert.rejects(f.api.postCertifiedBusiness('/offline/approve', {}, session), safeError);
  });
  for (const patch of [
    { status: 201 },
    { status: 500 },
    { envelope: { code: 200 } },
    { envelope: { code: 403 } },
    { envelope: { code: '200', data: {} } },
    { networkFailure: true },
    { rawBody: 'not-json' }
  ]) {
    test(`GET 严格 HTTP200/R200/有 data，失败安全抛出 ${JSON.stringify(patch)}`, async () => {
      const f = await transport();
      Object.assign(f, patch);
      await assert.rejects(f.api.readCertifiedBusiness('/offline/detail', f.session()), safeError);
      assert.equal(f.calls.length, 1);
    });
  }
  test('GET 保留泛型 data 的数组、false、0、null，不用 truthy 冒充契约判断', async () => {
    for (const data of [[], false, 0, null]) {
      const f = await transport();
      f.envelope = ok(data);
      assert.deepEqual(await f.api.readCertifiedBusiness('/offline/detail', f.session()), data);
    }
  });
  test('begin 持久 set/精确读回先于 POST，metadata 只含身份/意图四字段', async () => {
    const f = await transport();
    const storage = memoryStorage();
    const store = f.api.createBusinessIntentStore(storage);
    const input = scope({ token: TOKEN_A, version: 3, quantity: '5', quote: '100' });
    const intent = store.begin(input, 'approve', 'original-key');
    const text = [...storage.values.values()][0];
    assert.deepEqual(Object.keys(JSON.parse(text)).sort(), ['nonce', 'operation', 'requestKey', 'scope']);
    assert.deepEqual(JSON.parse(text).scope, scope());
    assert.doesNotMatch(text, /offline-certified-business|quote|quantity|version/);
    assert.ok(intent.nonce);
    assert.notEqual(intent.nonce, intent.requestKey);
    store.assertPending(intent);
    assert.equal(f.calls.length, 0);
    await f.api.postCertifiedBusiness('/offline/approve', { requestKey: intent.requestKey }, f.session());
    assert.equal(f.calls.length, 1);
    assert.ok(storage.calls.some((call) => call.method === 'set'));
    assert.ok(store.isBlocked(scope()), '成功响应本身不自动清意图，必须调用者核验回执后 release');
  });
  test('新 store/换版本/换操作/换键均不能绕过同 scope 已知锁', async () => {
    const f = await storageFixture();
    const other = f.api.createBusinessIntentStore(f.storage);
    for (const current of [f.scope, scope({ version: 99 }), scope({ expectedVersion: 0 })]) {
      assert.equal(other.isBlocked(current), true);
      assert.throws(() => other.begin(current, 'cancel', 'new-key'), safeError);
    }
    assert.equal(f.storage.values.get(f.primary), JSON.stringify(f.intent));
  });
  test('锁严格按租户/用户/功能/文档区分，分隔符编码不碰撞', async () => {
    const f = await storageFixture();
    for (const patch of [{ tenantId: '000002' }, { userId: '102' }, { feature: 'receive' }, { documentId: '2' }]) {
      assert.equal(f.store.isBlocked(scope(patch)), false);
      f.store.begin(scope(patch), 'approve', 'new-key');
    }
    const one = scope({ feature: 'a:b', documentId: 'c' });
    const two = scope({ feature: 'a', documentId: 'b:c' });
    f.store.begin(one, 'approve', 'key');
    assert.equal(f.store.isBlocked(two), false);
    assert.equal(f.storage.values.size, 6);
  });
  test('业务 scope/操作/key 缺失时拒绝，不存客户端业务数据', async () => {
    const api = await loadApi();
    const storage = memoryStorage();
    const store = api.createBusinessIntentStore(storage);
    for (const invalid of [null, {}, scope({ tenantId: '' }), scope({ userId: 1 }), scope({ feature: '' }), scope({ documentId: '' })]) {
      assert.throws(() => store.begin(invalid, 'approve', 'key'), safeError);
      assert.equal(store.isBlocked(invalid), true);
    }
    assert.throws(() => store.begin(scope(), '', 'key'), safeError);
    assert.throws(() => store.begin(scope(), 'approve', ''), safeError);
    assert.equal(storage.values.size, 0);
  });
  test('返回意图和 scope 固定；不能以外部对象/篡改字段释放原意图', async () => {
    const f = await storageFixture();
    assert.ok(Object.isFrozen(f.intent));
    assert.ok(Object.isFrozen(f.intent.scope));
    assert.throws(() => {
      f.intent.requestKey = 'other';
    });
    assert.throws(() => f.store.assertPending({ ...f.intent }), safeError);
    assert.throws(() => f.store.release({ ...f.intent }), safeError);
    const other = f.api.createBusinessIntentStore(f.storage);
    assert.throws(() => other.release(f.intent), safeError);
    assert.equal(f.storage.values.size, 1);
  });
  for (const method of ['get', 'set']) {
    for (const timing of ['before', 'after']) {
      test(`begin 存储 ${method}/${timing} 故障封闭，不能以新实例和另一 storage 绕开内存 latch`, async () => {
        const api = await loadApi();
        const storage = memoryStorage();
        const store = api.createBusinessIntentStore(storage);
        storage[timing] = (current) => {
          if (current === method) throw new Error(RAW_ERROR);
        };
        assert.throws(() => store.begin(scope(), 'approve', 'key'), safeError);
        storage.before = storage.after = undefined;
        assert.equal(store.isBlocked(scope()), true);
        const other = api.createBusinessIntentStore(memoryStorage());
        assert.equal(other.isBlocked(scope()), true);
        assert.throws(() => other.begin(scope(), 'cancel', 'new-key'), safeError);
      });
    }
  }
  test('begin 的 set 被静默忽略/改写时读回不符，禁止发送且保留 latch', async () => {
    for (const rewrite of [null, 'corrupted']) {
      const api = await loadApi();
      const storage = memoryStorage();
      storage.after = (method, key) => {
        if (method === 'set') rewrite === null ? storage.values.delete(key) : storage.values.set(key, rewrite);
      };
      const store = api.createBusinessIntentStore(storage);
      assert.throws(() => store.begin(scope(), 'approve', 'key'), safeError);
      storage.after = undefined;
      assert.equal(store.isBlocked(scope()), true);
    }
  });
  test('isBlocked 首次 get 故障返回 true，并在同模块新实例继续锁存', async () => {
    const api = await loadApi();
    const storage = memoryStorage();
    const store = api.createBusinessIntentStore(storage);
    storage.before = () => {
      throw new Error(RAW_ERROR);
    };
    assert.equal(store.isBlocked(scope()), true);
    storage.before = undefined;
    assert.equal(api.createBusinessIntentStore(storage).isBlocked(scope()), true);
  });
  test('primary 非 NULL 必须先 latch 后读 fence；fence 抛错后清 storage/新实例仍锁住', async () => {
    const original = await storageFixture();
    const api = await loadApi();
    const fresh = api.createBusinessIntentStore(original.storage);
    original.storage.calls.length = 0;
    original.storage.before = (method, key) => {
      if (method === 'get' && key === original.fence) throw new Error(RAW_ERROR);
    };
    assert.equal(fresh.isBlocked(original.scope), true);
    assert.equal(original.storage.calls[0].key, original.primary);
    original.storage.values.clear();
    original.storage.before = undefined;
    assert.equal(api.createBusinessIntentStore(original.storage).isBlocked(original.scope), true);
    assert.throws(() => fresh.begin(original.scope, 'cancel', 'new-key'), safeError);
    assert.equal(original.storage.values.get(original.primary), JSON.stringify(original.intent), '恢复存储时只尽力补回已知锁，不重发');
  });
  test('刷新加载持久 primary、孤立 fence、畸形 metadata 都只阻塞不自动擦 UNKNOWN', async () => {
    for (const fenceOnly of [false, true]) {
      const f = await storageFixture();
      if (fenceOnly) {
        f.storage.values.set(f.fence, f.storage.values.get(f.primary));
        f.storage.values.delete(f.primary);
      }
      const api = await loadApi();
      const store = api.createBusinessIntentStore(f.storage);
      assert.equal(store.isBlocked(f.scope), true);
      assert.throws(() => store.begin(f.scope, 'approve', 'new-key'), safeError);
      assert.equal(f.storage.calls.filter((call) => call.method === 'remove').length, 0);
    }
    const f = await storageFixture();
    f.storage.values.set(f.primary, 'not-json-' + RAW_ERROR);
    assert.equal((await loadApi()).createBusinessIntentStore(f.storage).isBlocked(f.scope), true);
  });
  test('GET 成功、只读刷新、POST 网络/5xx 不会清原 requestKey', async () => {
    const f = await transport();
    const storage = memoryStorage();
    const store = f.api.createBusinessIntentStore(storage);
    const intent = store.begin(scope(), 'approve', 'original-key');
    await f.api.readCertifiedBusiness('/offline/detail', f.session());
    store.assertPending(intent);
    f.networkFailure = true;
    await assert.rejects(f.api.postCertifiedBusiness('/offline/approve', { requestKey: intent.requestKey }, f.session()), safeError);
    store.assertPending(intent);
    f.networkFailure = false;
    f.status = 503;
    await assert.rejects(f.api.postCertifiedBusiness('/offline/approve', { requestKey: intent.requestKey }, f.session()), safeError);
    store.assertPending(intent);
    assert.equal(storage.calls.filter((call) => call.method === 'remove').length, 0);
    assert.equal(store.isBlocked(scope()), true);
  });
  test('release 必须先写 fence 并读回，再清 primary；最后读回后解除内存 latch', async () => {
    const f = await storageFixture();
    f.storage.calls.length = 0;
    f.store.release(f.intent);
    const events = f.storage.calls;
    const fenceSet = events.findIndex((call) => call.method === 'set' && call.key === f.fence);
    const fenceRead = events.findIndex((call, index) => index > fenceSet && call.method === 'get' && call.key === f.fence);
    const primaryRemove = events.findIndex((call) => call.method === 'remove' && call.key === f.primary);
    const fenceRemove = events.findIndex((call) => call.method === 'remove' && call.key === f.fence);
    assert.ok(fenceSet >= 0 && fenceRead > fenceSet && primaryRemove > fenceRead && fenceRemove > primaryRemove);
    assert.ok(events.some((call, index) => index > fenceRemove && call.method === 'get' && call.key === f.fence));
    assert.equal(f.storage.values.size, 0);
    assert.equal(f.store.isBlocked(f.scope), false);
    assert.equal(f.api.createBusinessIntentStore(f.storage).isBlocked(f.scope), false);
    const next = f.store.begin(f.scope, 'cancel', 'new-key');
    assert.notEqual(next.nonce, f.intent.nonce);
    assert.throws(() => f.store.release(f.intent), safeError);
    f.store.assertPending(next);
  });
  test('明确 R4xx 仅调用方在固定会话/意图复核后主动 release；绝不自动释放', async () => {
    const f = await transport();
    const storage = memoryStorage();
    const store = f.api.createBusinessIntentStore(storage);
    const intent = store.begin(scope(), 'approve', 'original-key');
    f.envelope = { code: 409, msg: RAW_ERROR };
    const result = await f.api.postCertifiedBusiness('/offline/approve', { requestKey: intent.requestKey }, f.session());
    assert.equal(result.code, 409);
    assert.equal(store.isBlocked(scope()), true);
    store.assertPending(intent);
    store.release(intent);
    assert.equal(store.isBlocked(scope()), false);
  });
  for (const method of ['get', 'set', 'remove']) {
    for (const timing of ['before', 'after']) {
      test(`release 存储 ${method}/${timing} 失败继续锁存，新实例不能绕锁`, async () => {
        const f = await storageFixture();
        f.storage[timing] = (current) => {
          if (current === method) throw new Error(RAW_ERROR);
        };
        assert.throws(() => f.store.release(f.intent), safeError);
        f.storage.before = f.storage.after = undefined;
        assert.equal(f.store.isBlocked(f.scope), true);
        assert.equal(f.api.createBusinessIntentStore(f.storage).isBlocked(f.scope), true);
        assert.throws(() => f.store.begin(f.scope, 'cancel', 'new-key'), safeError);
      });
    }
  }
  for (const point of ['fenceReadback', 'primaryRemove', 'primaryReadback', 'fenceRemove', 'fenceReadbackAfterRemoval']) {
    test(`release 阶段 ${point} 故障持久 fence 存活，刷新后仍封闭`, async () => {
      const f = await storageFixture();
      let wroteFence = false;
      let removedPrimary = false;
      let removedFence = false;
      f.storage.after = (method, key) => {
        if (method === 'set' && key === f.fence) wroteFence = true;
        if (method === 'remove' && key === f.primary) removedPrimary = true;
        if (method === 'remove' && key === f.fence) removedFence = true;
      };
      f.storage.before = (method, key) => {
        if (
          (point === 'fenceReadback' && wroteFence && method === 'get' && key === f.fence) ||
          (point === 'primaryRemove' && method === 'remove' && key === f.primary) ||
          (point === 'primaryReadback' && removedPrimary && method === 'get' && key === f.primary) ||
          (point === 'fenceRemove' && method === 'remove' && key === f.fence) ||
          (point === 'fenceReadbackAfterRemoval' && removedFence && method === 'get' && key === f.fence)
        )
          throw new Error(RAW_ERROR);
      };
      assert.throws(() => f.store.release(f.intent), safeError);
      f.storage.before = f.storage.after = undefined;
      const fresh = (await loadApi()).createBusinessIntentStore(f.storage);
      assert.equal(fresh.isBlocked(f.scope), true);
      assert.ok(f.storage.values.has(f.primary) || f.storage.values.has(f.fence));
      assert.throws(() => fresh.begin(f.scope, 'approve', 'new-key'), safeError);
    });
  }
  test('release 的 fence 写入静默丢失/损坏，绝不能移除 primary', async () => {
    for (const rewrite of [null, 'foreign-fence']) {
      const f = await storageFixture();
      f.storage.after = (method, key) => {
        if (method === 'set' && key === f.fence) rewrite === null ? f.storage.values.delete(key) : f.storage.values.set(key, rewrite);
      };
      assert.throws(() => f.store.release(f.intent), safeError);
      assert.equal(f.storage.values.get(f.primary), JSON.stringify(f.intent));
      assert.equal(
        f.storage.calls.some((call) => call.method === 'remove'),
        false
      );
    }
  });
  test('release 的 remove 静默忽略也不能解锁', async () => {
    const f = await storageFixture();
    f.storage.removeItem = (key) => {
      f.storage.calls.push({ method: 'remove', key });
    };
    assert.throws(() => f.store.release(f.intent), safeError);
    assert.equal(f.store.isBlocked(f.scope), true);
  });
  test('assertPending primary/nonce/operation/key 不匹配或存在 fence 均拒绝，不删除他人意图', async () => {
    for (const change of ['nonce', 'operation', 'requestKey', 'scope']) {
      const f = await storageFixture();
      const other = JSON.parse(f.storage.values.get(f.primary));
      if (change === 'scope') other.scope.documentId = '2';
      else other[change] = 'different-original-intent';
      const text = JSON.stringify(other);
      f.storage.values.set(f.primary, text);
      assert.throws(() => f.store.assertPending(f.intent), safeError);
      assert.throws(() => f.store.release(f.intent), safeError);
      assert.equal(f.storage.values.get(f.primary), text);
      assert.equal(
        f.storage.calls.some((call) => call.method === 'remove'),
        false
      );
    }
    const f = await storageFixture();
    f.storage.values.set(f.fence, 'foreign-fence');
    assert.throws(() => f.store.assertPending(f.intent), safeError);
    assert.throws(() => f.store.release(f.intent), safeError);
    assert.equal(f.storage.values.get(f.fence), 'foreign-fence');
  });
  test('release 写 fence 后 primary 被替换，不得删除其他意图', async () => {
    const f = await storageFixture();
    f.storage.after = (method, key) => {
      if (method === 'set' && key === f.fence) f.storage.values.set(f.primary, 'foreign-primary');
    };
    assert.throws(() => f.store.release(f.intent), safeError);
    assert.equal(f.storage.values.get(f.primary), 'foreign-primary');
    assert.equal(
      f.storage.calls.some((call) => call.method === 'remove'),
      false
    );
  });
  test('assertPending get 故障安全封闭，不清原锁', async () => {
    const f = await storageFixture();
    f.storage.before = (method) => {
      if (method === 'get') throw new Error(RAW_ERROR);
    };
    assert.throws(() => f.store.assertPending(f.intent), safeError);
    f.storage.before = undefined;
    assert.equal(f.store.isBlocked(f.scope), true);
  });
  test('所有持久内容丢失且写全失败时只保证当前模块 latch，不伪称硬刷新仍能恢复', async () => {
    const f = await storageFixture();
    f.storage.values.clear();
    f.storage.before = (method) => {
      if (method === 'set') throw new Error(RAW_ERROR);
    };
    assert.equal(f.store.isBlocked(f.scope), true);
    assert.equal(f.api.createBusinessIntentStore(f.storage).isBlocked(f.scope), true);
    f.storage.before = undefined;
    const reloaded = (await loadApi()).createBusinessIntentStore(f.storage);
    assert.equal(reloaded.isBlocked(f.scope), false, '新模块无任何持久事实时确实无法恢复，这是已声明的浏览器边界');
  });
}

if (present) {
  test('nonce 在 randomUUID 不可用但 getRandomValues 可用时仍可安全生成', async () => {
    const api = await loadApi();
    const original = Object.getOwnPropertyDescriptor(globalThis, 'crypto');
    const crypto = globalThis.crypto;
    Object.defineProperty(globalThis, 'crypto', { configurable: true, value: { getRandomValues: crypto.getRandomValues.bind(crypto) } });
    try {
      const store = api.createBusinessIntentStore(memoryStorage());
      const first = store.begin(scope(), 'approve', 'original-key');
      const second = store.begin(scope({ documentId: '2' }), 'approve', 'other-key');
      assert.equal(typeof first.nonce, 'string');
      assert.ok(first.nonce.length >= 32);
      assert.notEqual(first.nonce, second.nonce);
      store.assertPending(first);
    } finally {
      Object.defineProperty(globalThis, 'crypto', original);
    }
  });
  test('已经观察到他人 primary 后，持久内容丢失/补回也不能重新赋予旧对象派发资格', async () => {
    const f = await storageFixture();
    f.storage.values.set(f.primary, 'foreign-original-intent');
    assert.throws(() => f.store.assertPending(f.intent), safeError);
    f.storage.values.clear();
    assert.equal(f.store.isBlocked(f.scope), true);
    assert.throws(() => f.store.assertPending(f.intent), safeError);
    assert.throws(() => f.store.release(f.intent), safeError);
    assert.equal(
      f.storage.calls.some((call) => call.method === 'remove'),
      false
    );
  });
  test('release 失败后存储恢复，旧对象仍不可重新派发或擦 UNKNOWN', async () => {
    const f = await storageFixture();
    f.storage.before = (method) => {
      if (method === 'get') throw new Error(RAW_ERROR);
    };
    assert.throws(() => f.store.release(f.intent), safeError);
    f.storage.before = undefined;
    assert.equal(f.store.isBlocked(f.scope), true);
    assert.throws(() => f.store.assertPending(f.intent), safeError);
    assert.throws(() => f.store.release(f.intent), safeError);
    assert.equal(
      f.storage.calls.some((call) => call.method === 'remove'),
      false
    );
  });
  for (const code of [400, 401, 403, 422, 499]) {
    test(`原生整数 R${code} 的明确 HTTP200 拒绝不依赖 msg 或某个固定错误码`, async () => {
      const f = await transport();
      f.envelope = { code, msg: RAW_ERROR };
      const response = await f.api.postCertifiedBusiness('/offline/approve', {}, f.session());
      assert.equal(response.code, code);
      assert.doesNotMatch(response.msg, /offline-certified-business|原始错误/);
    });
  }
}

if (present) {
  test('普通传输 defaults 读取失败也只能抛安全未知异常，不回传原错误或凭证', async () => {
    const f = await transport();
    Object.defineProperty(f.request.defaults, 'transformResponse', {
      get() {
        throw new Error(RAW_ERROR);
      }
    });
    await assert.rejects(f.api.postCertifiedBusiness('/offline/approve', {}, f.session()), safeError);
    assert.equal(f.calls.length, 0);
  });
  test('损坏/额外业务字段 metadata 只阻塞，不补写凭证、报价或数量', async () => {
    for (const corrupted of [
      'not-json-' + RAW_ERROR,
      JSON.stringify({ scope: scope(), operation: 'approve', requestKey: 'key', nonce: 'nonce', token: TOKEN_A, quote: '100', quantity: '5' })
    ]) {
      const original = await storageFixture();
      original.storage.values.set(original.primary, corrupted);
      const api = await loadApi();
      const store = api.createBusinessIntentStore(original.storage);
      assert.equal(store.isBlocked(original.scope), true);
      original.storage.values.clear();
      original.storage.calls.length = 0;
      assert.equal(store.isBlocked(original.scope), true);
      assert.equal(
        original.storage.calls.some((call) => call.method === 'set'),
        false,
        '不能把损坏槽内容当作本模块 metadata 回写'
      );
      assert.throws(() => store.begin(original.scope, 'approve', 'new-key'), safeError);
    }
  });
}

/** B 只播种合法持久旧事实；被测 A/B/C 全部使用同一个新模块，不用重载逃逸其全局 latch。 */
async function conflictFixture() {
  const storageB = memoryStorage();
  const seedApi = await loadApi();
  const intentB = seedApi.createBusinessIntentStore(storageB).begin(scope(), 'approve', 'old-intent-b-key');
  const primary = [...storageB.values.keys()][0];
  const encodedB = storageB.values.get(primary);
  const api = await loadApi();
  const storageA = memoryStorage();
  const storeA = api.createBusinessIntentStore(storageA);
  const intentA = storeA.begin(scope(), 'approve', 'current-intent-a-key');
  const storeB = api.createBusinessIntentStore(storageB);
  const storageC = memoryStorage();
  const storeC = api.createBusinessIntentStore(storageC);
  return { api, storageA, storageB, storageC, storeA, storeB, storeC, intentA, intentB, primary, fence: primary + ':release-fence', encodedB };
}

function allowed(action) {
  try {
    action();
    return true;
  } catch (error) {
    safeError(error);
    return false;
  }
}

if (present) {
  test('P1 conflict：完整 B观察→A复核/释放→C空storage查询/换键顺序，已知B不能被A清全局锁', async () => {
    const f = await conflictFixture();
    // 先记录完整真实结果，最后断言，避免首个失败掩盖旧实现第三 storage 重新 begin 的路径。
    const actual = {
      bObserved: f.storeB.isBlocked(scope()),
      aPendingAllowed: allowed(() => f.storeA.assertPending(f.intentA)),
      aReleaseAllowed: allowed(() => f.storeA.release(f.intentA)),
      cBlocked: f.storeC.isBlocked(scope()),
      cBeginAllowed: allowed(() => f.storeC.begin(scope(), 'cancel', 'third-new-key')),
      bIntact: f.storageB.values.get(f.primary) === f.encodedB
    };
    assert.deepEqual(actual, {
      bObserved: true,
      aPendingAllowed: false,
      aReleaseAllowed: false,
      cBlocked: true,
      cBeginAllowed: false,
      bIntact: true
    });
    assert.equal(f.storageA.values.get(f.primary), JSON.stringify(f.intentA));
    assert.equal(
      f.storageA.calls.some((call) => call.method === 'remove'),
      false
    );
  });
  test('P1 conflict：不先调用 assertPending，A release 本身也必须拒绝，且不删任何已知意图', async () => {
    const f = await conflictFixture();
    assert.equal(f.storeB.isBlocked(scope()), true);
    assert.throws(() => f.storeA.release(f.intentA), safeError);
    assert.equal(f.storageA.values.get(f.primary), JSON.stringify(f.intentA));
    assert.equal(f.storageB.values.get(f.primary), f.encodedB);
    assert.equal(
      f.storageA.calls.some((call) => call.method === 'remove'),
      false
    );
    assert.equal(f.storeC.isBlocked(scope()), true);
    assert.throws(() => f.storeC.begin(scope(), 'cancel', 'third-new-key'), safeError);
  });
  test('P1 conflict：same-store观察外部合法B后改回A，sticky conflict不能因当前metadata再匹配而消失', async () => {
    const f = await storageFixture();
    const encodedA = JSON.stringify(f.intent);
    const encodedB = JSON.stringify({ ...f.intent, operation: 'cancel', requestKey: 'external-b-key', nonce: 'different-b-nonce' });
    f.storage.values.set(f.primary, encodedB);
    assert.equal(f.store.isBlocked(f.scope), true);
    f.storage.values.set(f.primary, encodedA);
    assert.throws(() => f.store.assertPending(f.intent), safeError);
    assert.throws(() => f.store.release(f.intent), safeError);
    const other = f.api.createBusinessIntentStore(memoryStorage());
    assert.equal(other.isBlocked(f.scope), true);
    assert.throws(() => other.begin(f.scope, 'cancel', 'third-new-key'), safeError);
    assert.equal(
      f.storage.calls.some((call) => call.method === 'remove'),
      false
    );
  });
  test('P1 conflict：同一storage wrapper切换后端观察B再切回A，不能恢复A的释放资格', async () => {
    const f = await conflictFixture();
    let current = f.storageB;
    const wrapper = {
      getItem: (key) => current.getItem(key),
      setItem: (key, value) => current.setItem(key, value),
      removeItem: (key) => current.removeItem(key)
    };
    const observer = f.api.createBusinessIntentStore(wrapper);
    assert.equal(observer.isBlocked(scope()), true);
    current = f.storageA;
    assert.equal(observer.isBlocked(scope()), true);
    assert.throws(() => f.storeA.release(f.intentA), safeError);
    assert.equal(f.storageA.values.get(f.primary), JSON.stringify(f.intentA));
    assert.equal(f.storageB.values.get(f.primary), f.encodedB);
    assert.equal(f.storeC.isBlocked(scope()), true);
    assert.throws(() => f.storeC.begin(scope(), 'approve', 'third-new-key'), safeError);
  });
  test('P1 conflict：只有nonce不同也必须锁存，B槽后续消失且A反复读回不能消除已知冲突', async () => {
    const f = await conflictFixture();
    const sameKeyOtherNonce = { ...f.intentA, nonce: f.intentB.nonce };
    f.storageB.values.set(f.primary, JSON.stringify(sameKeyOtherNonce));
    assert.equal(f.storeB.isBlocked(scope()), true);
    f.storageB.values.clear();
    for (let index = 0; index < 3; index++) assert.equal(f.storeA.isBlocked(scope()), true);
    assert.throws(() => f.storeA.assertPending(f.intentA), safeError);
    assert.throws(() => f.storeA.release(f.intentA), safeError);
    assert.equal(f.storeC.isBlocked(scope()), true);
    assert.throws(() => f.storeC.begin(scope({ version: 99 }), 'cancel', 'third-new-key'), safeError);
  });
  test('P1 conflict：另一storage孤立release-fence的不同metadata同样形成sticky conflict', async () => {
    const f = await conflictFixture();
    f.storageB.values.set(f.fence, f.encodedB);
    f.storageB.values.delete(f.primary);
    assert.equal(f.storeB.isBlocked(scope()), true);
    assert.throws(() => f.storeA.release(f.intentA), safeError);
    assert.equal(f.storageB.values.get(f.fence), f.encodedB);
    assert.equal(
      f.storageB.calls.some((call) => call.method === 'remove'),
      false
    );
    assert.equal(f.storeC.isBlocked(scope()), true);
    assert.throws(() => f.storeC.begin(scope(), 'approve', 'third-new-key'), safeError);
  });
  test('P1 conflict：release写fence期间观察到B，必须在移除A primary前停止并保留durable guard', async () => {
    const f = await conflictFixture();
    f.storageA.after = (method, key) => {
      if (method === 'set' && key === f.fence) assert.equal(f.storeB.isBlocked(scope()), true);
    };
    assert.throws(() => f.storeA.release(f.intentA), safeError);
    assert.equal(f.storageA.values.get(f.primary), JSON.stringify(f.intentA));
    assert.equal(f.storageA.values.get(f.fence), JSON.stringify(f.intentA));
    assert.equal(
      f.storageA.calls.some((call) => call.method === 'remove'),
      false
    );
    assert.equal(f.storageB.values.get(f.primary), f.encodedB);
    assert.equal(f.storeC.isBlocked(scope()), true);
    assert.throws(() => f.storeC.begin(scope(), 'approve', 'third-new-key'), safeError);
  });
  test('P1 conflict：最后fence移除期间观察到B，不得清global latch且必须补回自有durable fence', async () => {
    const f = await conflictFixture();
    f.storageA.after = (method, key) => {
      if (method === 'remove' && key === f.fence) assert.equal(f.storeB.isBlocked(scope()), true);
    };
    assert.throws(() => f.storeA.release(f.intentA), safeError);
    assert.equal(f.storageA.values.get(f.fence), JSON.stringify(f.intentA));
    assert.equal(f.storageB.values.get(f.primary), f.encodedB);
    assert.equal(f.storeC.isBlocked(scope()), true);
    assert.throws(() => f.storeC.begin(scope(), 'approve', 'third-new-key'), safeError);
  });
  test('P1 conflict：同一原metadata经多个wrapper反复观察不误标冲突，正常release仍能清锁', async () => {
    const f = await storageFixture();
    const wrapper = {
      getItem: (key) => f.storage.getItem(key),
      setItem: (key, value) => f.storage.setItem(key, value),
      removeItem: (key) => f.storage.removeItem(key)
    };
    const observer = f.api.createBusinessIntentStore(wrapper);
    for (let index = 0; index < 3; index++) assert.equal(observer.isBlocked(f.scope), true);
    f.store.assertPending(f.intent);
    f.store.release(f.intent);
    assert.equal(observer.isBlocked(f.scope), false);
    assert.equal(f.api.createBusinessIntentStore(memoryStorage()).isBlocked(f.scope), false);
  });
}
