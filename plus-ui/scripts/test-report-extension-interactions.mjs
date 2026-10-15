import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { readFile, mkdir } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { parse, compileScript } from '@vue/compiler-sfc';
import { createRenderer, reactive, nextTick, h } from 'vue';
await mkdir('node_modules/.tmp', { recursive: true });
/**
 * 保存当前页面或测试的apiNames状态。
 */
const apiNames = [
  'listSubscriptions',
  'subscriptionDetail',
  'createSubscription',
  'saveSubscription',
  'publishSubscription',
  'runSubscription',
  'subscriptionState',
  'subscriptionExecutions',
  'subscriptionSchedule',
  'retrySubscriptionExecution',
  'cancelSubscriptionExecution',
  'subscriptionCapabilities',
  'subscriptionSource',
  'listReportDeliveries',
  'claimReportDelivery',
  'queryCatalogs',
  'listQueries',
  'createQuery',
  'saveQuery',
  'publishQuery',
  'previewQuery',
  'listDashboards',
  'dashboardFavorites',
  'dashboardPublished',
  'renderDashboard',
  'dashboardDetail',
  'dashboardSource',
  'createDashboard',
  'saveDashboard',
  'publishDashboard',
  'favoriteDashboard',
  'runPivot',
  'runDrill',
  'publishedReport',
  'createRun',
  'runPage',
  'runLayout',
  'enqueueExport',
  'listDatasets',
  'listCatalogs',
  'reportDetail',
  'createReport',
  'saveReport',
  'validateDraft',
  'previewDraft',
  'publishReport',
  'listReports',
  'listVersions',
  'rollbackReport',
  'listProviders',
  'registerDataset',
  'datasetState',
  'sampleDataset',
  'listGrants',
  'saveGrants',
  'listExportJobs',
  'cancelExport',
  'downloadExport',
  'createCatalog',
  'bootstrapReports',
  'reportState',
  'listReportAudit',
  'datasetVersion',
  'datasetVersionDiff',
  'reportEngines',
  'exportTemplatePackage',
  'importTemplatePackage',
  'copyReport',
  'reportLookup',
  'reportLookupBindings',
  'businessReportTemplates',
  'retryExport'
];
/**
 * 保存当前页面或测试的mockPlugin状态。
 */
const mockPlugin = {
  name: 'report-ui-fixtures',
  /** 处理当前报表的 `setup` 操作。 */
  setup(b) {
    b.onResolve(
      {
        filter:
          /^(@\/api\/report(?:\/query|\/dashboard|\/subscription)?|@\/utils\/permission|vue-router|element-plus|file-saver|@element-plus\/icons-vue)$/
      },
      (a) => ({
        path: a.path,
        namespace: 'fixtures'
      })
    );
    b.onLoad({ filter: /.*/, namespace: 'fixtures' }, (a) => {
      let contents = '';
      if (a.path === '@/utils/permission') contents = 'export const checkPermi=()=>true;';
      if (a.path.startsWith('@/api/report'))
        contents = apiNames.map((n) => 'export const ' + n + '=(...args)=>globalThis.__reportExt.api["' + n + '"](...args);').join('\n');
      if (a.path === 'vue-router')
        contents =
          'export const useRoute=()=>globalThis.__reportExt.route; export const useRouter=()=>globalThis.__reportExt.router; export const onBeforeRouteLeave=f=>{globalThis.__reportExt.guards.leave=f;};export const onBeforeRouteUpdate=f=>{globalThis.__reportExt.guards.update=f;};';
      if (a.path === 'element-plus')
        contents =
          'export const ElMessage={success:(...a)=>globalThis.__reportExt.messages.push(a),warning:(...a)=>globalThis.__reportExt.messages.push(a),error:(...a)=>globalThis.__reportExt.messages.push(a)};export const ElMessageBox={confirm:(...a)=>globalThis.__reportExt.confirm(...a),prompt:(...a)=>globalThis.__reportExt.prompt(...a)};';
      if (a.path === 'file-saver') contents = 'export const saveAs=(...args)=>globalThis.__reportExt.downloads.push(args);';
      if (a.path === '@element-plus/icons-vue') contents = 'export const ArrowDown={render:()=>null};';
      return { contents, loader: 'js' };
    });
    b.onResolve({ filter: /^@\// }, (a) => ({
      path: resolve(
        'src',
        a.path.slice(2) + (a.path.endsWith('.vue') || a.path.endsWith('.ts') ? '' : a.path === '@/types/report' ? '/index.ts' : '.ts')
      )
    }));
    b.onLoad({ filter: /\.vue$/ }, async (a) => {
      const { descriptor } = parse(await readFile(a.path, 'utf8'));
      const script = compileScript(descriptor, { id: a.path });
      return { contents: script.content, loader: 'ts', resolveDir: dirname(a.path) };
    });
  }
};

/** 使用真实Vue运行时挂载新页面，模板渲染与接口都独立替身。 */
const renderer = createRenderer({
  createElement: () => ({}),
  createComment: () => ({}),
  createText: () => ({}),
  insert: () => {},
  remove: () => {},
  setElementText: () => {},
  setText: () => {},
  patchProp: () => {},
  parentNode: () => null,
  nextSibling: () => null
});
/**
 * 保存当前页面或测试的pages状态。
 */
const pages = {
  query: 'src/views/report/query/index.vue',
  dashboard: 'src/views/report/dashboard/index.vue',
  pivotTable: 'src/components/report/PivotTable.vue',
  subscription: 'src/views/report/subscription/index.vue',
  delivery: 'src/views/report/delivery/index.vue',
  acl: 'src/components/report/AclDialog.vue'
};
for (const [name, path] of Object.entries(pages))
  await build({
    entryPoints: [path],
    outfile: 'node_modules/.tmp/report-ext-' + name + '.mjs',
    bundle: true,
    format: 'esm',
    platform: 'node',
    external: ['vue'],
    plugins: [
      {
        name: 'chart-fixture',
        /**
         * 处理当前报表的setup契约或受控操作。
         * @param b 当前契约的b配置或状态
         */
        setup(b) {
          b.onResolve({ filter: /^echarts$/ }, (a) => ({ path: a.path, namespace: 'chart' }));
          b.onLoad({ filter: /.*/, namespace: 'chart' }, () => ({
            contents: 'export const init=()=>({setOption(){},on(){},dispose(){},resize(){}});',
            loader: 'js'
          }));
        }
      },
      mockPlugin
    ]
  });
/**
 * 保存当前页面或测试的components状态。
 */
const components = Object.fromEntries(
  await Promise.all(
    Object.keys(pages).map(async (name) => [name, (await import(pathToFileURL(resolve('node_modules/.tmp/report-ext-' + name + '.mjs')))).default])
  )
);
/** 等待真实组件的异步生命周期与响应式更新。 */
async function flush() {
  await new Promise((resolve) => setImmediate(resolve));
  await nextTick();
}
/** 可控制回包次序的真实Promise。 */
function defer() {
  let resolve, reject;
  const promise = new Promise((a, b) => {
    resolve = a;
    reject = b;
  });
  return { promise, resolve, reject };
}
/** 挂载组件，记录副作用并允许控制接口和可见性。 */
function mount(component, overrides = {}, props = {}) {
  const listeners = new Map();
  globalThis.document = {
    visibilityState: 'visible',
    addEventListener: (name, fn) => listeners.set(name, fn),
    removeEventListener: (name) => listeners.delete(name)
  };
  const fixture = {
    route: reactive({ query: {} }),
    guards: {},
    messages: [],
    downloads: [],
    router: { push: async () => {}, replace: async () => {} },
    confirm: async () => true,
    prompt: async () => ({ value: 'value' }),
    api: {
      listReports: async () => ({ data: [] }),
      listSubscriptions: async () => ({ data: [] }),
      listReportDeliveries: async () => ({ data: [] }),
      subscriptionCapabilities: async () => ({ data: { schedulingEnabled: false, notificationsEnabled: false } }),
      listGrants: async () => ({ data: [] }),
      queryCatalogs: async () => ({ data: [] }),
      listQueries: async () => ({ data: [] }),
      listDashboards: async () => ({ data: [] }),
      dashboardFavorites: async () => ({ data: [] }),
      ...overrides
    }
  };
  globalThis.__reportExt = fixture;
  const subject = { ...component, render: () => null };
  const app = renderer.createApp(subject, props);
  let state;
  app.mixin({
    /**
     * 处理当前报表的mounted契约或受控操作。
     */
    mounted() {
      if (this.$options === subject || this.$options.name === subject.name) state = this.$.setupState;
    }
  });
  app.config.warnHandler = () => {};
  const vm = app.mount({});
  state = state || vm.$.setupState;
  return { state, fixture, listeners, unmount: () => app.unmount() };
}

/** 新查询确认被取消时，不丢弃当前未保存字段。 */
test('query new draft confirmation preserves dirty design on cancellation', async () => {
  const f = mount(components.query);
  await flush();
  f.state.entity = 'stock';
  f.state.title = '尚未保存';
  f.fixture.confirm = async () => {
    throw new Error('cancel');
  };
  await f.state.startNew();
  assert.equal(f.state.title, '尚未保存');
  f.unmount();
});
/** 清除联动必须废弃带旧筛选的在途回包。 */
test('clearing dashboard links forces an empty selection refresh and rejects stale result', async () => {
  const pending = defer();
  let calls = 0;
  const sent = [];
  const f = mount(components.dashboard, {
    renderDashboard: async (id, parameters, selections) => {
      sent.push(JSON.parse(JSON.stringify(selections)));
      return ++calls === 1 ? pending.promise : { data: { versionId: 'v', refreshSeconds: 30, widgets: [{ id: 'new', data: 'empty' }] } };
    }
  });
  await flush();
  f.state.selected = '1';
  f.state.selections = [{ sourceWidget: 'a', targetWidget: 'b', sourceField: 'code', value: { type: 'STRING', value: 'P' } }];
  const old = f.state.refresh();
  await flush();
  await f.state.clearSelections();
  pending.resolve({ data: { versionId: 'old', refreshSeconds: 30, widgets: [{ id: 'old' }] } });
  await old;
  assert.equal(sent.length, 2);
  assert.equal(sent[1].length, 0);
  assert.equal(f.state.rendered[0].id, 'new');
  f.unmount();
});
/** 页面隐藏会移除自动刷新，恢复可见性后才重新调度。 */
test('hidden dashboard stops refresh timers and unmount removes visibility listener', async () => {
  const nativeSet = globalThis.setInterval,
    nativeClear = globalThis.clearInterval;
  const timers = new Map();
  let serial = 0;
  globalThis.setInterval = (fn) => {
    const id = ++serial;
    timers.set(id, fn);
    return id;
  };
  globalThis.clearInterval = (id) => timers.delete(id);
  try {
    const f = mount(components.dashboard);
    await flush();
    f.state.selected = '1';
    f.state.published = { formatVersion: 1, title: '测试', parameters: [], widgets: [], refreshSeconds: 30 };
    f.state.schedule();
    assert.equal(timers.size, 1);
    document.visibilityState = 'hidden';
    f.listeners.get('visibilitychange')();
    assert.equal(timers.size, 0);
    f.unmount();
    assert.equal(f.listeners.size, 0);
  } finally {
    globalThis.setInterval = nativeSet;
    globalThis.clearInterval = nativeClear;
  }
});
/** 钻取只能使用当前运行给出的服务端坐标。 */
test('pivot component ignores a previous run drill response', async () => {
  const pending = defer();
  const f = mount(
    components.pivotTable,
    { runPivot: async () => ({ data: { rows: [], columns: [], totals: [], sourceRows: 0 } }), runDrill: async () => pending.promise },
    { runId: 'run-a' }
  );
  await flush();
  const request = f.state.open('coordinate-a');
  await flush();
  f.unmount();
  pending.resolve({ data: { rows: [{ id: { type: 'ID', value: '9223372036854775807' } }], total: 1 } });
  await request;
  assert.equal(f.state.drillRows.length, 0);
});

/** 订阅切换固定版本时，迟到参数契约不能覆盖新版本。 */
test('subscription source switching preserves string IDs and rejects stale contracts', async () => {
  const pending = defer();
  const f = mount(components.subscription, {
    subscriptionSource: async (id) => (id === '9223372036854775807' ? pending.promise : { data: { parameters: [{ name: 'new', type: 'ID' }] } })
  });
  await flush();
  await f.state.newSubscription();
  const first = f.state.chooseReport('9223372036854775807');
  await flush();
  await f.state.chooseReport('9223372036854775806');
  pending.resolve({ data: { parameters: [{ name: 'old', type: 'DATE' }] } });
  await first;
  assert.equal(f.state.model.reportVersionId, '9223372036854775806');
  assert.equal(f.state.sourceParameters[0].name, 'new');
  f.unmount();
});
/** 网络失败重试使用原始整体手动键，不制造另一组接收人任务。 */
test('subscription manual retry retains occurrence request key until success', async () => {
  const keys = [];
  const f = mount(components.subscription, {
    runSubscription: async (id, key) => {
      keys.push(key);
      if (keys.length === 1) throw Error('synthetic network error');
    },
    subscriptionExecutions: async () => ({ data: [] }),
    subscriptionSchedule: async () => ({ data: null })
  });
  await flush();
  await assert.rejects(f.state.run('9223372036854775807'));
  await f.state.run('9223372036854775807');
  assert.equal(keys[0], keys[1]);
  f.unmount();
});
/** 离开交付页面后迟到领取回包不能继续启动下载。 */
test('delivery unmount fences late claim before download side effect', async () => {
  const pending = defer();
  let downloads = 0;
  const f = mount(components.delivery, {
    claimReportDelivery: async () => pending.promise,
    downloadExport: async () => {
      downloads++;
      return new Blob(['synthetic'], { type: 'application/pdf' });
    }
  });
  await flush();
  const request = f.state.download({ id: '9223372036854775807', state: 'READY', jobId: '9223372036854775806' });
  f.unmount();
  pending.resolve({ data: '9223372036854775806' });
  await request;
  assert.equal(downloads, 0);
  assert.equal(f.fixture.downloads.length, 0);
});
/** 扩展资源授权使用同一真实弹窗，类型与精确ID都原样送到服务端。 */
test('extension resource ACL uses exact resource type and ID', async () => {
  for (const type of ['QUERY', 'DASHBOARD', 'SUBSCRIPTION', 'ASSET', 'BATCH']) {
    const calls = [];
    const f = mount(
      components.acl,
      {
        listGrants: async (t, id) => {
          calls.push([t, id]);
          return { data: [] };
        }
      },
      { open: true, resourceType: type, resourceId: '9223372036854775807' }
    );
    await flush();
    assert.deepEqual(calls, [[type, '9223372036854775807']]);
    f.unmount();
  }
});

/** 新增目录错误反馈可重试且不会吞掉仍在使用的新目录数据。 */
for (const [page, api, list] of [
  ['dashboard', 'listDashboards', 'dashboards'],
  ['subscription', 'listSubscriptions', 'subscriptions'],
  ['delivery', 'listReportDeliveries', 'deliveries']
]) {
  test(page + '目录载入失败与重试使用独立状态', async () => {
    const x = mount(components[page], {
      [api]: async () => {
        throw Error('合成网络失败');
      }
    });
    await flush();
    assert.equal(x.state.loadFailed, true);
    assert.equal(x.state.loading, false);
    x.fixture.api[api] = async () => ({ data: [{ id: '9223372036854775807' }] });
    await x.state.load();
    assert.equal(x.state.loadFailed, false);
    assert.equal(x.state[list][0].id, '9223372036854775807');
    x.unmount();
  });
}
