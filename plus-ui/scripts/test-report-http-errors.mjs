import test, { beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { AxiosError } from 'axios';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const mocks = {
  '@/store/modules/user': 'export const useUserStore=()=>({logout:async()=>{globalThis.__reportHttpErrors.logouts++}});',
  '@/utils/auth': "export const getToken=()=> '';",
  '@/utils/ruoyi': 'export const tansParams=params=>new URLSearchParams(params).toString(); export const blobValidate=()=>false;',
  '@/plugins/cache': 'export default {session:{getJSON:()=>null,setJSON:()=>{}}};',
  '@/router': "export default {currentRoute:{value:{fullPath:'/report'}},replace:()=>{globalThis.__reportHttpErrors.redirects++}};",
  '@/utils/crypto':
    "export const encryptBase64=value=>value,encryptWithAes=value=>value,generateAesKey=()=>'',decryptWithAes=value=>value,decryptBase64=value=>value;",
  '@/utils/jsencrypt': 'export const encrypt=value=>value,decrypt=value=>value;',
  'file-saver': 'export default {saveAs:()=>{}};'
};
const compiled = await build({
  stdin: {
    contents: `export {default as request,isRelogin} from './src/utils/request.ts'; export {readReportFailure} from './src/api/report/errors.ts';`,
    resolveDir: root,
    loader: 'ts'
  },
  bundle: true,
  write: false,
  platform: 'node',
  format: 'esm',
  define: {
    'import.meta.env.VITE_APP_ENCRYPT': '"false"',
    'import.meta.env.VITE_APP_CLIENT_ID': '"fixture-client"',
    'import.meta.env.VITE_APP_BASE_API': '"/dev-api"'
  },
  plugins: [
    {
      name: 'report-http-dependencies',
      setup(builder) {
        builder.onResolve({ filter: /^axios$/ }, () => ({
          path: pathToFileURL(resolve(root, 'node_modules/axios/index.js')).href,
          external: true
        }));
        builder.onResolve({ filter: /^@\/|^file-saver$/ }, ({ path }) =>
          mocks[path] ? { path, namespace: 'fixture' } : { path: resolve(root, 'src', path.slice(2) + '.ts') }
        );
        builder.onLoad({ filter: /.*/, namespace: 'fixture' }, ({ path }) => ({ contents: mocks[path], loader: 'ts' }));
      }
    }
  ]
});
const api = await import('data:text/javascript;base64,' + Buffer.from(compiled.outputFiles[0].text).toString('base64'));
let state;
beforeEach(() => {
  state = { messages: [], notifications: [], logins: 0, logouts: 0, redirects: 0 };
  globalThis.__reportHttpErrors = state;
  globalThis.ElMessage = (options) => state.messages.push(options.message);
  globalThis.ElNotification = { error: (options) => state.notifications.push(options.title) };
  globalThis.ElMessageBox = {
    confirm: () => {
      state.logins++;
      return new Promise(() => {});
    }
  };
  api.isRelogin.show = false;
});

const controlledFailures = [
  { code: 400, errorCode: 'INVALID_PARAMETER', msg: '报表请求参数无效' },
  { code: 403, errorCode: 'SCOPE_DENIED', msg: '无权访问该报表资源或数据' },
  { code: 409, errorCode: 'QUERY_SOURCE_DISABLED', msg: '请配置并启用独立SELECT报表数据源' },
  { code: 500, errorCode: 'REPORT_EXECUTION_FAILED', msg: '报表处理失败，请稍后重试' }
];
const queryDisabled = controlledFailures[2];
const genericFailure = '报表处理失败，请稍后重试';

function send({ status = 200, data = queryDisabled, url = '/report/queries/preview', responseType, headers = {}, ...config } = {}) {
  api.request.defaults.adapter = async (requestConfig) => {
    const request = { responseType: requestConfig.responseType || '' };
    state.response = { config: requestConfig, data, status, statusText: 'fixture', headers: { 'content-type': 'application/json' }, request };
    if (status >= 400) {
      state.error = new AxiosError('Request failed with status code ' + status, AxiosError.ERR_BAD_RESPONSE, requestConfig, request, state.response);
      throw state.error;
    }
    return state.response;
  };
  return api.request({ url, responseType, headers: { repeatSubmit: false, ...headers }, ...config });
}

async function rejectResponse(options) {
  let rejection;
  await assert.rejects(send(options), (error) => {
    rejection = error;
    return true;
  });
  return rejection;
}

function messages() {
  return [...state.messages, ...state.notifications];
}

for (const data of controlledFailures) {
  test(`报表HTTP${data.code}展示受控中文原因并保留原始Axios错误与响应`, async () => {
    const error = await rejectResponse({ status: data.code, data });
    assert.deepEqual(messages(), [data.msg]);
    assert.equal(error, state.error);
    assert.equal(error.response, state.response);
    assert.equal(error.response.data, data);
    assert.equal((await api.readReportFailure(error)).code, data.errorCode);
    assert.equal(state.logins, 0);
  });

  test(`报表HTTP200业务${data.code}保留错误对象、业务码和原始响应`, async () => {
    const error = await rejectResponse({ data });
    assert.ok(error instanceof Error);
    assert.equal(error.message, data.msg);
    assert.equal(error.errorCode, data.errorCode);
    assert.equal(error.response, state.response);
    assert.equal(error.response.data, data);
    assert.equal((await api.readReportFailure(error)).code, data.errorCode);
    assert.deepEqual(messages(), [data.msg]);
  });
}

for (const [errorCode, action] of [
  ['REPORT_VERSION_CHANGED', 'SELECT_TEMPLATE'],
  ['BATCH_CODE_CONFLICT', 'NEW_RUN'],
  ['SCOPE_DENIED', 'ACCESS']
]) {
  test(`HTTP200错误经真实report helper仍可分类${errorCode}`, async () => {
    const error = await rejectResponse({ data: { code: 409, errorCode, msg: '报表状态已变化，请核对后重试' } });
    const failure = await api.readReportFailure(error);
    assert.equal(failure.code, errorCode);
    assert.equal(failure.action, action);
  });
}

for (const status of [200, 403, 409, 500]) {
  test(`报表${status}遵守ignoreErrorMsg并保留可分类错误`, async () => {
    const data = controlledFailures.find((failure) => failure.code === status) || queryDisabled;
    const error = await rejectResponse({ status, data, headers: { ignoreErrorMsg: 'true' } });
    assert.deepEqual(messages(), []);
    assert.equal((await api.readReportFailure(error)).code, data.errorCode);
  });
}

for (const url of ['/report/queries/preview?sample=1', '/dev-api/report/queries/preview', 'https://example.invalid/dev-api/report/queries/preview']) {
  test(`报表路径正确限定API作用域：${url}`, async () => {
    await rejectResponse({ status: 409, url });
    assert.deepEqual(messages(), [queryDisabled.msg]);
  });
}

for (const url of ['/erp/orders', '/reporting/preview', '/erp/report/preview', '/erp/preview?next=/report/queries/preview']) {
  test(`非报表路径保留HTTP及业务失败的既有行为：${url}`, async () => {
    const httpError = await rejectResponse({ status: 409, url });
    assert.equal(httpError, state.error);
    assert.deepEqual(messages(), ['系统接口409异常']);
    const businessError = await rejectResponse({ url, data: controlledFailures[1] });
    assert.equal(businessError, 'error');
    assert.deepEqual(messages(), ['系统接口409异常', '当前操作没有权限']);
  });
}

for (const errorCode of [undefined, null, '', '   ', 'bad code', 'lowercase', 409, 'SCOPE_DENIED<script>']) {
  test(`没有有效errorCode的报表响应保留既有行为：${JSON.stringify(errorCode)}`, async () => {
    const data = { code: 403, errorCode, msg: 'SYNTHETIC_RAW_EXCEPTION' };
    const error = await rejectResponse({ status: 403, data });
    assert.equal(error, state.error);
    assert.deepEqual(messages(), ['系统接口403异常']);
    assert.equal(await rejectResponse({ data }), 'error');
    assert.deepEqual(messages(), ['系统接口403异常', '当前操作没有权限']);
  });
}

for (const msg of [
  undefined,
  null,
  {},
  '',
  '   ',
  'java.sql.SQLException: SYNTHETIC_RAW_EXCEPTION',
  '报表异常 java.lang.IllegalStateException: SYNTHETIC_RAW_EXCEPTION',
  '报表失败 Authorization: Bearer SYNTHETIC_CREDENTIAL',
  '报表失败 token=SYNTHETIC_CREDENTIAL',
  '报表失败 eyJhbGciOiJIUzI1NiJ9.synthetic.signature'
]) {
  test(`报表错误文案异常或包含凭证时只显示安全兜底：${typeof msg === 'string' ? msg.slice(0, 24) : typeof msg}`, async () => {
    for (const status of [200, 500]) {
      const error = await rejectResponse({ status, data: { code: 500, errorCode: 'REPORT_EXECUTION_FAILED', msg } });
      assert.equal((await api.readReportFailure(error)).code, 'REPORT_EXECUTION_FAILED');
      assert.equal(messages().at(-1), genericFailure);
    }
  });
}

test('HTTP200认证401保持重新登录行为且不显示原始凭证', async () => {
  const error = await rejectResponse({ data: { code: 401, errorCode: 'SCOPE_DENIED', msg: 'token SYNTHETIC_CREDENTIAL' } });
  assert.equal(error, '无效的会话，或者会话已过期，请重新登录。');
  assert.equal(state.logins, 1);
  assert.deepEqual(messages(), []);
});

test('HTTP401保持原HTTP错误分支，不解释报表信封', async () => {
  const error = await rejectResponse({ status: 401, data: { code: 401, errorCode: 'SCOPE_DENIED', msg: 'token SYNTHETIC_CREDENTIAL' } });
  assert.equal(error, state.error);
  assert.equal(state.logins, 0);
  assert.deepEqual(messages(), ['系统接口401异常']);
});

for (const msg of ['NotLoginException SYNTHETIC_CREDENTIAL', 'token 已被冻结：SYNTHETIC_CREDENTIAL', 'eyJhbGciOiJIUzI1NiJ9.synthetic.signature']) {
  test(`HTTP200业务500认证失效继续触发登录且不泄露：${msg.slice(0, 18)}`, async () => {
    await rejectResponse({ data: { code: 500, errorCode: 'REPORT_EXECUTION_FAILED', msg } });
    assert.equal(state.logins, 1);
    assert.deepEqual(messages(), []);
  });
}

for (const status of [200, 403, 409, 500]) {
  test(`certifiedErrorBoundary优先隔离报表${status}的全部副作用`, async () => {
    const error = await rejectResponse({ status, headers: { certifiedErrorBoundary: 'true' } });
    assert.ok(error instanceof Error);
    assert.deepEqual(messages(), []);
    assert.equal(state.logins, 0);
    assert.equal(state.logouts, 0);
    assert.equal(state.redirects, 0);
    assert.equal(state.response.config.headers.has('certifiedErrorBoundary'), false);
  });
}

for (const responseType of ['blob', 'arraybuffer']) {
  test(`${responseType}真实文件仍原样返回`, async () => {
    const data = responseType === 'blob' ? new Blob(['%PDF-1.7']) : new TextEncoder().encode('%PDF-1.7').buffer;
    assert.equal(await send({ data, responseType }), data);
    assert.deepEqual(messages(), []);
  });

  test(`${responseType}的HTTP409保留原始文件错误及原有提示`, async () => {
    const body = JSON.stringify(queryDisabled);
    const data = responseType === 'blob' ? new Blob([body], { type: 'application/json' }) : new TextEncoder().encode(body).buffer;
    const error = await rejectResponse({ status: 409, data, responseType });
    assert.equal(error, state.error);
    assert.equal(error.response.data, data);
    assert.deepEqual(messages(), ['系统接口409异常']);
    if (responseType === 'blob') assert.equal((await api.readReportFailure(error)).code, 'QUERY_SOURCE_DISABLED');
  });
}

test('blob的HTTP200错误文件仍交由现有report helper识别', async () => {
  const data = new Blob([JSON.stringify({ code: 410, errorCode: 'SNAPSHOT_EXPIRED', msg: '固定快照已过期' })], { type: 'application/json' });
  const blob = await send({ data, responseType: 'blob' });
  assert.equal(blob, data);
  assert.deepEqual(messages(), []);
  assert.equal((await api.readReportFailure({ response: { data: blob } })).action, 'NEW_RUN');
});

for (const code of [401, 403, 409, 500]) {
  test(`arraybuffer的HTTP200业务${code}仍沿用既有JSON文件识别`, async () => {
    const data = new TextEncoder().encode(JSON.stringify({ code, errorCode: 'SCOPE_DENIED', msg: '报表文件不可用' })).buffer;
    const error = await rejectResponse({ data, responseType: 'arraybuffer' });
    assert.ok(error instanceof Error);
    assert.equal(error.code, code);
    if (code === 401) {
      assert.equal(state.logins, 1);
      assert.deepEqual(messages(), []);
    } else assert.deepEqual(messages(), [code === 403 ? '当前操作没有权限' : '报表文件不可用']);
  });
}

test('arraybuffer错误文件的ignoreErrorMsg仍生效', async () => {
  const data = new TextEncoder().encode(JSON.stringify(queryDisabled)).buffer;
  await rejectResponse({ data, responseType: 'arraybuffer', headers: { ignoreErrorMsg: 'true' } });
  assert.deepEqual(messages(), []);
});

for (const [message, expected] of [
  ['Network Error', '后端接口连接异常'],
  ['timeout of 50000ms exceeded', '系统接口请求超时']
]) {
  test(`报表${message}保留原网络错误及提示`, async () => {
    const original = new AxiosError(message);
    api.request.defaults.adapter = async () => {
      throw original;
    };
    await assert.rejects(api.request({ url: '/report/queries/preview', headers: { ignoreErrorMsg: 'true' } }), (error) => error === original);
    assert.deepEqual(messages(), [expected]);
  });
}

test('报表成功信封包含errorCode字段也仍原样成功', async () => {
  const data = { code: 200, errorCode: 'QUERY_SOURCE_DISABLED', data: [] };
  assert.equal(await send({ data }), data);
  assert.deepEqual(messages(), []);
});
