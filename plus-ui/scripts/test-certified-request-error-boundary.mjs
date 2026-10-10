import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { AxiosError } from 'axios';
import { mkdtempSync, writeFileSync, rmSync, readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath, pathToFileURL } from 'node:url';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const temp = mkdtempSync(resolve(tmpdir(), 'certified-request-boundary-'));
const entry = resolve(temp, 'entry.ts');
writeFileSync(
  entry,
  `export {default as request} from ${JSON.stringify(resolve(root, 'src/utils/request.ts'))}; export {createBusinessSession,postCertifiedBusiness} from ${JSON.stringify(resolve(root, 'src/utils/certified-business.ts'))}; export {confirmMeasureQuantity} from ${JSON.stringify(resolve(root, 'src/api/erp/bd/measure/index.ts'))};`
);
const mocks = {
  '@/store/modules/user': `export const useUserStore=()=>({softLogout:async()=>{globalThis.__boundary.logout++}});`,
  '@/utils/auth': `export const getToken=()=> 'synthetic-token';`,
  '@/utils/ruoyi': `export const tansParams=()=> ''; export const blobValidate=()=>false;`,
  '@/plugins/cache': `export default {session:{getJSON:()=>null,setJSON:()=>{}}};`,
  '@/router': `export default {currentRoute:{value:{fullPath:'/fixture'}},replace:()=>{globalThis.__boundary.redirect++}};`,
  '@/utils/crypto': `export const encryptBase64=x=>x,encryptWithAes=x=>x,generateAesKey=()=>'',decryptWithAes=x=>x,decryptBase64=x=>x;`,
  '@/utils/jsencrypt': `export const encrypt=x=>x,decrypt=x=>x;`,
  'file-saver': `export default {saveAs:()=>{}};`
};
await build({
  entryPoints: [entry],
  bundle: true,
  platform: 'node',
  format: 'esm',
  outfile: resolve(temp, 'test.mjs'),
  packages: 'external',
  define: {
    'import.meta.env.VITE_APP_ENCRYPT': '"false"',
    'import.meta.env.VITE_APP_CLIENT_ID': '"fixture-client"',
    'import.meta.env.VITE_APP_BASE_API': '"/fixture"'
  },
  plugins: [
    {
      name: 'boundary',
      setup(b) {
        b.onResolve({ filter: /^@\/|^file-saver$/ }, (a) =>
          mocks[a.path] ? { path: a.path, namespace: 'mock' } : { path: resolve(root, 'src', a.path.slice(2) + '.ts') }
        );
        b.onLoad({ filter: /.*/, namespace: 'mock' }, (a) => ({ contents: mocks[a.path], loader: 'ts' }));
      }
    }
  ]
});
const bundle = resolve(temp, 'test.mjs');
const axiosPath = resolve(root, 'node_modules/axios/index.js');
writeFileSync(bundle, readFileSync(bundle, 'utf8').replaceAll('from "axios"', 'from ' + JSON.stringify(pathToFileURL(axiosPath).href)));
let state;
function reset() {
  state = { ui: [], logout: 0, redirect: 0, login: 0, requests: [] };
  globalThis.__boundary = state;
  globalThis.ElMessage = (x) => state.ui.push(x.message);
  globalThis.ElNotification = { error: (x) => state.ui.push(x.title) };
  globalThis.ElMessageBox = {
    confirm: async () => {
      state.login++;
    }
  };
  globalThis.ElLoading = { service: () => ({ close() {} }) };
}
reset();
const api = await import(pathToFileURL(bundle).href);
process.on('exit', () => rmSync(temp, { recursive: true, force: true }));
const RAW = 'SYNTHETIC_RAW_ERROR_MARKER';
function response(code, status = 200) {
  api.request.defaults.adapter = async (config) => {
    state.requests.push(config);
    return { config, data: { code, msg: RAW }, status, statusText: 'fixture', headers: {}, request: { responseType: '' } };
  };
}
function failure(message, status) {
  api.request.defaults.adapter = async (config) => {
    state.requests.push(config);
    throw new AxiosError(
      message,
      'ERR_FIXTURE',
      config,
      {},
      status ? { config, data: { code: status, msg: RAW }, status, statusText: 'fixture', headers: {}, request: { responseType: '' } } : undefined
    );
  };
}
async function tick() {
  for (let i = 0; i < 10; i++) await Promise.resolve();
}
const isolated = { headers: { certifiedErrorBoundary: 'true', repeatSubmit: false } };
for (const code of [401, 403, 500, 601, 503])
  test(`认证R${code}在原生UI副作用前封闭`, async () => {
    reset();
    response(code);
    await assert.rejects(api.request({ url: '/offline', method: 'post', data: {}, ...isolated }), (e) => !String(e.message ?? e).includes(RAW));
    await tick();
    assert.deepEqual(state.ui, []);
    assert.equal(state.login, 0);
    assert.equal(state.logout, 0);
  });
for (const [name, message, status] of [
  ['任意网络', RAW, undefined],
  ['HTTP401', RAW, 401],
  ['HTTP500', RAW, 500],
  ['超时', 'timeout ' + RAW, undefined]
])
  test(`认证${name}错误无raw toast`, async () => {
    reset();
    failure(message, status);
    await assert.rejects(api.request({ url: '/offline', method: 'post', data: {}, ...isolated }), (e) => !String(e.message ?? e).includes(RAW));
    assert.deepEqual(state.ui, []);
    assert.equal(state.login, 0);
  });
test('认证正常R200仍交回原始成功信封', async () => {
  reset();
  response(200);
  const r = await api.request({ url: '/offline', method: 'post', data: {}, ...isolated });
  assert.equal(r.code, 200);
  assert.deepEqual(state.ui, []);
});
test('普通CRUD未声明认证边界仍保留默认toast行为', async () => {
  reset();
  failure(RAW);
  await assert.rejects(api.request({ url: '/offline', method: 'post', data: {}, headers: { repeatSubmit: false } }));
  assert.equal(state.ui.includes(RAW), true);
});
test('真实数量API和helper声明同一静默错误边界', async () => {
  reset();
  response(500);
  const session = api.createBusinessSession(() => 'synthetic-token');
  await assert.rejects(
    api.confirmMeasureQuantity(
      '9007199254740993',
      { expectedVersion: null, primaryId: '9007199254740993', conversionRate: '1', quantityScale: 0, reference: 'synthetic-reference' },
      session
    )
  );
  assert.deepEqual(state.ui, []);
  assert.equal(state.requests.length, 1);
});
test('真实helper明确4xx仍返回脱敏拒绝而不弹登录或原文', async () => {
  reset();
  response(403);
  const session = api.createBusinessSession(() => 'synthetic-token');
  const r = await api.postCertifiedBusiness('/offline', {}, session);
  assert.equal(r.code, 403);
  assert.ok(!r.msg.includes(RAW));
  assert.deepEqual(state.ui, []);
});
