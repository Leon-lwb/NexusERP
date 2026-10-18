import test, { beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const mocks = {
  '@/store/modules/user': 'export const useUserStore=()=>({logout:async()=>{}});',
  '@/utils/auth': "export const getToken=()=> '';",
  '@/utils/ruoyi': 'export const tansParams=params=>new URLSearchParams(params).toString(); export const blobValidate=()=>false;',
  '@/plugins/cache': `export default {session:{getJSON:()=>globalThis.__reportReadonly.session,
    setJSON:(_,value)=>{globalThis.__reportReadonly.session=JSON.parse(JSON.stringify(value))}}};`,
  '@/router': "export default {currentRoute:{value:{fullPath:'/report'}},replace:()=>{}};",
  '@/utils/crypto':
    "export const encryptBase64=value=>value,encryptWithAes=value=>value,generateAesKey=()=>'',decryptWithAes=value=>value,decryptBase64=value=>value;",
  '@/utils/jsencrypt': 'export const encrypt=value=>value,decrypt=value=>value;',
  'file-saver': 'export default {saveAs:()=>{}};'
};
const compiled = await build({
  stdin: {
    contents: `export {default as request} from './src/utils/request.ts';
      export * as report from './src/api/report/index.ts'; export * as query from './src/api/report/query.ts';
      export * as dashboard from './src/api/report/dashboard.ts';`,
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
      name: 'report-readonly-dependencies',
      setup(builder) {
        builder.onResolve({ filter: /^axios$/ }, () => ({ path: pathToFileURL(resolve(root, 'node_modules/axios/index.js')).href, external: true }));
        builder.onResolve({ filter: /^@\/|^file-saver$/ }, ({ path }) =>
          mocks[path] ? { path, namespace: 'fixture' } : { path: resolve(root, 'src', path.slice(2) + '.ts') }
        );
        builder.onLoad({ filter: /.*/, namespace: 'fixture' }, ({ path }) => ({ contents: mocks[path], loader: 'ts' }));
      }
    }
  ]
});
const api = await import('data:text/javascript;base64,' + Buffer.from(compiled.outputFiles[0].text).toString('base64'));
const id = '9223372036854775806';
const parameters = { customerId: id, amount: '12345678901234567890.00100', keyword: '只读预览', optional: '' };
const dsl = { schemaVersion: 1, kind: 'TABLE', dataset: { versionId: id } };
const model = { catalogVersion: id, select: [{ field: 'customerId' }] };
let state;
api.request.interceptors.request.use((config) => {
  state.configs.push({ ...config, headers: { ...config.headers } });
  return config;
});
beforeEach((context) => {
  context.mock.timers.enable({ apis: ['Date'], now: 10000 });
  context.mock.method(console, 'warn', () => {});
  context.mock.method(console, 'error', () => {});
  state = { session: null, configs: [], requests: [], messages: [] };
  globalThis.__reportReadonly = state;
  globalThis.ElMessage = (options) => state.messages.push(options.message);
  api.request.defaults.adapter = async (config) => {
    state.requests.push(config);
    return { config, status: 200, statusText: 'OK', headers: {}, request: { responseType: '' }, data: { code: 200, data: {} } };
  };
});
const reads = [
  ['草稿预览', () => api.report.previewDraft(id, dsl, parameters), '/report/definitions/preview', { reportId: id, dsl, parameters }],
  ['新建草稿预览', () => api.report.previewDraft(null, dsl, parameters), '/report/definitions/preview', { reportId: null, dsl, parameters }],
  ['数据集样例', () => api.report.sampleDataset(id, parameters), '/report/datasets/' + id + '/sample', parameters],
  ['草稿校验', () => api.report.validateDraft(id, dsl), '/report/definitions/validate', { reportId: id, dsl }],
  ['查询预览', () => api.query.previewQuery(model, parameters), '/report/queries/preview', { model, parameters }],
  ['仪表盘刷新', () => api.dashboard.renderDashboard(id, parameters, []), '/report/dashboards/' + id + '/render', { parameters, selections: [] }]
];
for (const [name, invoke, url, data] of reads) {
  test(`${name}成功后立即刷新和499ms内重复读取均发送，完整ID及字符串参数不变`, async (context) => {
    await invoke();
    for (const elapsed of [0, 499]) {
      context.mock.timers.tick(elapsed);
      const outcome = await invoke().then(
        () => '已发送',
        (error) => error.message
      );
      assert.equal(outcome, '已发送');
    }
    assert.equal(state.requests.length, 3);
    for (const config of state.configs) {
      assert.equal(config.url, url);
      assert.equal(config.method, 'post');
      assert.equal(config.headers.repeatSubmit, false);
      assert.equal(config.responseType, undefined);
      assert.deepEqual(config.data, data);
    }
    for (const config of state.requests) assert.deepEqual(JSON.parse(config.data), data);
    assert.equal(state.session, null);
    assert.deepEqual(state.messages, []);
  });
}
const writes = [
  ['报表创建', () => api.report.createReport(dsl, id)],
  ['报表保存', () => api.report.saveReport(id, 7, dsl)],
  ['报表发布', () => api.report.publishReport(id, 7)],
  ['查询创建', () => api.query.createQuery('TEST', '测试', model)],
  ['查询保存', () => api.query.saveQuery({ id, revision: 7 }, '测试', model)],
  ['查询发布', () => api.query.publishQuery({ id, revision: 7 })],
  ['运行创建', () => api.report.createRun(id, 'PREVIEW', parameters, id)],
  ['导出入队', () => api.report.enqueueExport(id, 'PDF', 'readonly-test-key')],
  ['同意图收藏', () => api.dashboard.favoriteDashboard(id, true)]
];
for (const [name, invoke] of writes) {
  test(`${name}保留默认防重：499ms内不发送第二次请求，500ms可重试`, async (context) => {
    await invoke();
    assert.notEqual(state.configs[0].headers.repeatSubmit, false);
    context.mock.timers.tick(499);
    await assert.rejects(invoke, /数据正在处理，请勿重复提交/);
    assert.equal(state.requests.length, 1);
    context.mock.timers.tick(1);
    await invoke();
    assert.equal(state.requests.length, 2);
  });
}
test('字符串false不会跳过真实拦截器的重复提交保护', async () => {
  const invoke = () => api.request({ url: '/report/definitions', method: 'post', data: { dsl }, headers: { repeatSubmit: 'false' } });
  await invoke();
  await assert.rejects(invoke, /数据正在处理，请勿重复提交/);
  assert.equal(state.requests.length, 1);
});

test('收藏与取消收藏的查询参数不同，允许明确切换并继续阻止重复取消', async () => {
  await api.dashboard.favoriteDashboard(id, true);
  await api.dashboard.favoriteDashboard(id, false);
  assert.equal(state.requests.length, 2);
  assert.equal(state.requests[0].params.enabled, true);
  assert.equal(state.requests[1].params.enabled, false);
  await assert.rejects(() => api.dashboard.favoriteDashboard(id, false), /数据正在处理，请勿重复提交/);
  assert.equal(state.requests.length, 2);
});
