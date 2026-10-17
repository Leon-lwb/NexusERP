import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { readFile, mkdir, access } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { parse, compileScript } from '@vue/compiler-sfc';
import { createRenderer, reactive, nextTick } from 'vue';

/** 编译真实组件脚本，只替换网络、路由、账号及浏览器下载边界。 */
const apiNames = [
  'businessReportTemplates',
  'publishedReport',
  'createRun',
  'runStatus',
  'runPage',
  'runLayout',
  'enqueueExport',
  'listExportJobs',
  'downloadExport',
  'retryExport',
  'listReports',
  'listBatches',
  'batchItems',
  'createBatch',
  'startBatch',
  'batchPdf'
];
const paths = {
  button: 'src/components/report/BusinessReportButton.vue',
  dialog: 'src/components/report/BusinessBatchDialog.vue',
  pdf: 'src/components/report/ReportPdfPreview.vue',
  batch: 'src/views/report/batch/index.vue',
  viewer: 'src/views/report/viewer/index.vue',
  drawer: 'src/components/report/BusinessReportDrawer.vue'
};
const plugin = {
  name: 'business-print-boundaries',
  /** 保留Vue响应式和请求围栏的实际实现。 */
  setup(builder) {
    builder.onResolve(
      {
        filter:
          /^(@\/api\/report(?:\/batch)?|@\/store\/modules\/user|@\/utils\/permission|vue-router|element-plus|file-saver|@element-plus\/icons-vue)$/
      },
      ({ path }) => ({ path, namespace: 'fixture' })
    );
    builder.onLoad({ filter: /.*/, namespace: 'fixture' }, ({ path }) => {
      let contents;
      if (path.startsWith('@/api/report'))
        contents = apiNames.map((name) => `export const ${name}=(...args)=>globalThis.__businessPrint.api.${name}(...args);`).join('\n');
      if (path === '@/store/modules/user') contents = 'export const useUserStore=()=>globalThis.__businessPrint.user;';
      if (path === '@/utils/permission')
        contents = 'export const checkPermi=keys=>keys.some(key=>globalThis.__businessPrint.permissions.includes(key));';
      if (path === 'vue-router')
        contents = 'export const useRoute=()=>globalThis.__businessPrint.route;export const useRouter=()=>globalThis.__businessPrint.router;';
      if (path === 'element-plus')
        contents =
          'export const ElMessage={success:(...args)=>globalThis.__businessPrint.messages.push(args),warning:(...args)=>globalThis.__businessPrint.messages.push(args),error:(...args)=>globalThis.__businessPrint.messages.push(args)};';
      if (path === 'file-saver') contents = 'export const saveAs=(...args)=>globalThis.__businessPrint.downloads.push(args);';
      if (path === '@element-plus/icons-vue') contents = 'export const ArrowDown={};';
      return { contents, loader: 'js' };
    });
    builder.onResolve({ filter: /^@\// }, ({ path }) => ({
      path: resolve('src', path.slice(2) + (/\.(vue|ts)$/.test(path) ? '' : path === '@/types/report' ? '/index.ts' : '.ts'))
    }));
    builder.onLoad({ filter: /\.vue$/ }, async ({ path }) => {
      if (!Object.values(paths).some((value) => resolve(value) === path)) return { contents: 'export default {};', loader: 'js' };
      const { descriptor } = parse(await readFile(path, 'utf8'));
      return { contents: compileScript(descriptor, { id: path }).content, loader: 'ts', resolveDir: dirname(path) };
    });
  }
};
await mkdir('node_modules/.tmp', { recursive: true });
const components = {};
for (const [name, path] of Object.entries(paths)) {
  try {
    await access(path);
  } catch {
    components[name] = {};
    continue;
  }
  const outfile = `node_modules/.tmp/report-business-print-${name}.mjs`;
  await build({ entryPoints: [path], outfile, bundle: true, format: 'esm', platform: 'node', external: ['vue'], plugins: [plugin] });
  components[name] = (await import(pathToFileURL(resolve(outfile)))).default;
}
const renderer = createRenderer({
  createElement: () => ({}),
  createComment: () => ({}),
  createText: () => ({}),
  insert() {},
  remove() {},
  setElementText() {},
  setText() {},
  patchProp() {},
  parentNode: () => null,
  nextSibling: () => null
});
/** 等待受测组件的异步和响应式更新完成。 */
async function flush() {
  await new Promise((done) => setImmediate(done));
  await nextTick();
}
/** 人工控制网络返回次序。 */
function deferred() {
  let resolve, reject;
  const promise = new Promise((accept, decline) => {
    resolve = accept;
    reject = decline;
  });
  return { promise, resolve, reject };
}
/** 只使用合成标识与内存接口，不访问数据库。 */
function mount(name, overrides = {}, props = {}, query = {}) {
  const fixture = {
    messages: [],
    downloads: [],
    routes: [],
    user: reactive({ userId: '9223372036854775807' }),
    permissions: reactive(['report:runtime:preview', 'report:runtime:export', 'report:batch:manage', 'report:batch:list', 'report:definition:list']),
    route: reactive({ query }),
    api: {
      businessReportTemplates: async () => ({ data: [] }),
      listReports: async () => ({ data: [] }),
      listBatches: async () => ({ data: [] }),
      batchItems: async () => ({ data: [] }),
      listExportJobs: async () => ({ data: [] }),
      publishedReport: async () => ({ data: published() }),
      createRun: async () => ({ data: { id: 'run', mode: 'SNAPSHOT' } }),
      runPage: async () => ({ data: { rows: [], columns: [], total: 1, summaries: [] } }),
      runLayout: async () => ({ data: {} }),
      ...overrides
    }
  };
  fixture.router = { push: (route) => fixture.routes.push(route) };
  globalThis.__businessPrint = fixture;
  const subject = { ...components[name], render: () => null };
  const app = renderer.createApp(subject, props);
  app.config.warnHandler = () => {};
  const vm = app.mount({});
  return { state: vm.$.setupState, fixture, props: vm.$.props, unmount: () => app.unmount() };
}
/** 合成的已发布固定单据DSL。 */
function published(versionId = 'version-A') {
  return {
    versionId,
    number: 1,
    dsl: {
      kind: 'DOCUMENT',
      title: '合成采购单',
      datasetVersionId: 'dataset-A',
      parameters: [{ name: 'orderId', type: 'ID', label: '采购单', required: true }]
    }
  };
}
const exactId = '9223372036854775807';

test('业务列表在原页打开单据抽屉并保留模板和完整参数', async (context) => {
  const f = mount(
    'button',
    {
      businessReportTemplates: async () => ({ data: [{ id: 'doc', name: '合成单据', kind: 'DOCUMENT', versionId: 'version-A' }] })
    },
    { datasetCode: 'ERP_SALES_ORDER_DOCUMENT', parameter: 'orderId', recordId: exactId }
  );
  context.after(f.unmount);
  await f.state.load();
  f.state.open('doc');
  assert.equal(f.fixture.routes.length, 0);
  assert.equal(f.state.previewOpen, true);
  assert.equal(f.state.selectedTemplate.versionId, 'version-A');
  assert.equal(f.state.exactRecordId, exactId);
});

test('隐藏预览抽屉不查询，打开完整页携带当前run而非重复取数', async (context) => {
  let reads = 0;
  let creates = 0;
  const f = mount(
    'drawer',
    {
      publishedReport: async () => {
        reads++;
        return { data: published() };
      },
      createRun: async () => {
        creates++;
        return { data: { id: 'drawer-run', version: 'version-A', mode: 'SNAPSHOT' } };
      }
    },
    { open: false, reportId: 'doc', versionId: 'version-A', parameters: { orderId: exactId } }
  );
  context.after(f.unmount);
  await flush();
  assert.equal(reads, 0);
  f.props.open = true;
  await flush();
  assert.equal(reads, 1);
  assert.equal(creates, 1);
  f.state.fullPage();
  assert.equal(f.fixture.routes[0].query.runId, 'drawer-run');
  assert.equal(f.fixture.routes[0].query.orderId, exactId);
  assert.equal(f.fixture.routes[0].query.auto, undefined);
});

test('抽屉关闭时旧取数响应失效，重新打开自动预览仅执行一次', async (context) => {
  const late = deferred();
  let creates = 0;
  const f = mount(
    'drawer',
    {
      createRun: async () => {
        creates++;
        return creates === 1 ? late.promise : { data: { id: 'new-run', version: 'version-A', mode: 'SNAPSHOT' } };
      }
    },
    { open: true, reportId: 'doc', versionId: 'version-A', parameters: { orderId: exactId } }
  );
  context.after(f.unmount);
  await flush();
  f.props.open = false;
  await flush();
  late.resolve({ data: { id: 'old-run', version: 'version-A', mode: 'SNAPSHOT' } });
  await flush();
  assert.equal(f.state.viewer.run.value, undefined);
  f.props.open = true;
  await flush();
  assert.equal(creates, 2);
  assert.equal(f.state.viewer.run.value.id, 'new-run');
});

test('已完成入队的确认抽屉关闭重开保留原批次，不自动新建', async (context) => {
  let creates = 0;
  const f = mount(
    'dialog',
    {
      businessReportTemplates: async () => ({ data: [{ id: 'doc', kind: 'DOCUMENT', versionId: 'version-A' }] }),
      createBatch: async () => {
        creates++;
        return { data: { id: 'known-batch', owner: exactId } };
      },
      startBatch: async () => ({}),
      batchItems: async () => ({ data: [] })
    },
    { open: true, datasetCode: 'ERP_SALES_ORDER_DOCUMENT', parameter: 'orderId', recordIds: [exactId] }
  );
  context.after(f.unmount);
  await flush();
  f.state.versionId = 'version-A';
  await f.state.submit();
  f.props.open = false;
  await flush();
  f.props.open = true;
  await flush();
  assert.equal(creates, 1);
  assert.equal(f.state.created.id, 'known-batch');
  assert.equal(f.state.locked, true);
});

test('运行请求在取数前传递界面确认的固定发布版本', async (context) => {
  const calls = [];
  const f = mount(
    'viewer',
    {
      createRun: async (...args) => {
        calls.push(args);
        return { data: { id: 'bound-run', version: 'version-A', mode: 'SNAPSHOT' } };
      }
    },
    {},
    { id: 'document', orderId: exactId }
  );
  context.after(f.unmount);
  await flush();
  await f.state.execute();
  assert.equal(calls.length, 1);
  assert.equal(calls[0][3], 'version-A');
});

test('完整页接收已有run时重新鉴权复用，不重复创建业务快照', async (context) => {
  let creates = 0;
  let reads = 0;
  const f = mount(
    'viewer',
    {
      runStatus: async (id) => {
        reads++;
        assert.equal(id, 'existing-run');
        return { data: { id, reportId: 'document', version: 'version-A', mode: 'SNAPSHOT', state: 'READY', expires: '2099-01-01' } };
      },
      createRun: async () => {
        creates++;
        throw Error('不得重新取数');
      }
    },
    {},
    { id: 'document', runId: 'existing-run', orderId: exactId, auto: '1' }
  );
  context.after(f.unmount);
  await flush();
  assert.equal(reads, 1);
  assert.equal(creates, 0);
  assert.equal(f.state.run.id, 'existing-run');
});

test('预览权限在取数期间撤销时立即清空模板并拒绝迟到结果', async (context) => {
  const late = deferred();
  const f = mount('viewer', { createRun: () => late.promise }, {}, { id: 'doc', orderId: exactId });
  context.after(f.unmount);
  await flush();
  const running = f.state.execute();
  f.fixture.permissions.splice(f.fixture.permissions.indexOf('report:runtime:preview'), 1);
  await flush();
  late.resolve({ data: { id: 'late-run', version: 'version-A', mode: 'SNAPSHOT' } });
  await running;
  assert.equal(f.state.published, undefined);
  assert.equal(f.state.run, undefined);
  assert.equal(f.state.plan, undefined);
});

test('文件已过期即使所有任务成功也不能领取合并PDF', async (context) => {
  let reads = 0;
  const f = mount('batch', {
    listBatches: async () => ({ data: [{ id: 'batch', owner: exactId }] }),
    batchItems: async () => ({ data: [{ number: 0, jobId: 'job', state: 'SUCCEEDED', expires: '2000-01-01', reportVersionId: 'v' }] }),
    batchPdf: async () => {
      reads++;
      return new Blob(['%PDF-']);
    }
  });
  context.after(f.unmount);
  await flush();
  await f.state.open('batch');
  assert.equal(f.state.allSucceeded, false);
  await assert.rejects(() => f.state.loadPdf());
  assert.equal(reads, 0);
});

test('创建返回前打印权限撤销时不继续start，保留已知批次以供核对', async (context) => {
  const late = deferred();
  let starts = 0;
  const f = mount(
    'dialog',
    {
      businessReportTemplates: async () => ({ data: [{ id: 'doc', kind: 'DOCUMENT', versionId: 'version-A' }] }),
      createBatch: () => late.promise,
      startBatch: async () => {
        starts++;
      }
    },
    { open: true, datasetCode: 'ERP_SALES_ORDER_DOCUMENT', parameter: 'orderId', recordIds: [exactId] }
  );
  context.after(f.unmount);
  await flush();
  f.state.versionId = 'version-A';
  const pending = f.state.submit();
  f.fixture.permissions.splice(f.fixture.permissions.indexOf('report:runtime:export'), 1);
  late.resolve({ data: { id: 'known', owner: exactId } });
  await pending;
  assert.equal(starts, 0);
  assert.equal(f.state.created.id, 'known');
  assert.equal(f.state.locked, true);
});

test('PDF入队期间撤销导出权限拒绝晚响应，恢复权限后保留原请求键', async (context) => {
  const late = deferred();
  const keys = [];
  const f = mount(
    'viewer',
    {
      enqueueExport: async (runId, format, key) => {
        keys.push(key);
        return keys.length === 1 ? late.promise : { data: { id: 'same-job', runId, state: 'SUCCEEDED', format } };
      }
    },
    {},
    { id: 'doc', orderId: exactId }
  );
  context.after(f.unmount);
  await flush();
  await f.state.execute();
  const printing = f.state.printPdf();
  f.fixture.permissions.splice(f.fixture.permissions.indexOf('report:runtime:export'), 1);
  late.resolve({ data: { id: 'same-job', runId: 'run', state: 'SUCCEEDED', format: 'PDF' } });
  await printing;
  assert.equal(f.state.printJob, undefined);
  assert.equal(f.state.printing, false);
  assert.equal(f.state.pdfOpen, false);
  f.fixture.permissions.push('report:runtime:export');
  await f.state.printPdf();
  assert.equal(keys.length, 2);
  assert.equal(keys[0], keys[1]);
  assert.equal(f.state.printJob.id, 'same-job');
});

test('逐项刷新确认撤权后关闭已打开的合并文件入口', async (context) => {
  let blocked = false;
  const f = mount('batch', {
    listBatches: async () => ({ data: [{ id: 'batch', owner: exactId }] }),
    batchItems: async () => ({
      data: [
        {
          number: 0,
          jobId: blocked ? null : 'job',
          state: blocked ? 'BLOCKED' : 'SUCCEEDED',
          error: blocked ? 'SCOPE_DENIED' : null,
          expires: '2099-01-01'
        }
      ]
    })
  });
  context.after(f.unmount);
  await flush();
  await f.state.open('batch');
  f.state.pdfOpen = true;
  blocked = true;
  await f.state.refreshItems('batch');
  assert.equal(f.state.pdfOpen, false);
  assert.equal(f.state.canDeliverPdf, false);
});

test('同身份撤销批次预览权限立即清空详情并拒绝迟到状态', async (context) => {
  const late = deferred();
  let delayed = false;
  const f = mount('batch', {
    listBatches: async () => ({ data: [{ id: 'batch', owner: exactId }] }),
    batchItems: async () => (delayed ? late.promise : { data: [{ number: 0, jobId: 'job', state: 'SUCCEEDED' }] })
  });
  context.after(f.unmount);
  await flush();
  await f.state.open('batch');
  delayed = true;
  const pending = f.state.refreshItems('batch');
  f.fixture.permissions.splice(f.fixture.permissions.indexOf('report:runtime:preview'), 1);
  assert.equal(f.state.items.length, 0);
  assert.equal(f.state.selected, '');
  late.resolve({ data: [{ number: 0, jobId: 'late-job', state: 'SUCCEEDED' }] });
  await pending;
  assert.equal(f.state.items.length, 0);
});

test('模板契约首次加载期间替换参数只为新单据创建运行', async (context) => {
  const late = deferred();
  let reads = 0;
  const calls = [];
  const f = mount(
    'viewer',
    {
      publishedReport: async () => (++reads === 1 ? late.promise : { data: published() }),
      createRun: async (...args) => {
        calls.push(args);
        return { data: { id: 'current', mode: 'SNAPSHOT', version: 'version-A' } };
      }
    },
    {},
    { id: 'doc', orderId: 'old', auto: '1' }
  );
  context.after(f.unmount);
  await flush();
  f.fixture.route.query = { id: 'doc', orderId: exactId, auto: '1' };
  await flush();
  late.resolve({ data: published() });
  await flush();
  assert.equal(calls.length, 1);
  assert.equal(calls[0][2].orderId, exactId);
  assert.equal(f.state.parameters.orderId, exactId);
});

test('导出明确返回业务范围失效时清除原业务数据与排版', async (context) => {
  const f = mount(
    'viewer',
    {
      enqueueExport: async () => {
        throw { response: { status: 403, data: { errorCode: 'SCOPE_CHANGED' } } };
      }
    },
    {},
    { id: 'doc', orderId: exactId }
  );
  context.after(f.unmount);
  await flush();
  await f.state.execute();
  await f.state.printPdf();
  assert.equal(f.state.run, undefined);
  assert.equal(f.state.data, undefined);
  assert.equal(f.state.plan, undefined);
  assert.equal(f.state.published, undefined);
  assert.match(f.state.loadError, /权限/);
});

test('已有冻结批次时清空或超量勾选仍可重开原提交', async (context) => {
  const f = mount(
    'button',
    {
      createBatch: async () => ({ data: { id: 'known', owner: exactId } }),
      startBatch: async () => ({})
    },
    { datasetCode: 'ERP_SALES_ORDER_DOCUMENT', parameter: 'orderId', recordIds: [exactId] }
  );
  context.after(f.unmount);
  await f.state.submission.submit('原批次', [{ reportVersionId: 'v', parameters: { orderId: exactId } }]);
  f.props.recordIds = [];
  await flush();
  assert.equal(f.state.canOpenBatch, true);
  f.props.recordIds = Array.from({ length: 51 }, (_, index) => String(index + 1));
  await flush();
  assert.equal(f.state.canOpenBatch, true);
  assert.equal(f.state.submission.created.value.id, 'known');
});

test('单据确认撤权后结果和返回确认都不再展示原单号及客户', async (context) => {
  const f = mount(
    'dialog',
    {
      businessReportTemplates: async () => ({ data: [{ id: 'doc', kind: 'DOCUMENT', versionId: 'version-A' }] }),
      createBatch: async () => ({ data: { id: 'known', owner: exactId } }),
      startBatch: async () => ({}),
      batchItems: async () => ({ data: [] })
    },
    {
      open: true,
      datasetCode: 'ERP_SALES_ORDER_DOCUMENT',
      parameter: 'orderId',
      recordIds: [exactId],
      documents: [{ id: exactId, code: 'SO-SECRET', counterparty: '合成客户' }]
    }
  );
  context.after(f.unmount);
  await flush();
  f.state.versionId = 'version-A';
  await f.state.submit();
  f.state.result.items.value = [{ number: 0, state: 'BLOCKED', error: 'SCOPE_DENIED', jobId: null }];
  await flush();
  assert.equal(f.state.displayRecords[0].code, '不可访问单据');
  assert.equal(f.state.displayRecords[0].counterparty, undefined);
  assert.equal(f.state.displayRecords[0].id, '');
  f.state.viewingResult = false;
  await flush();
  assert.equal(f.state.displayRecords[0].code, '不可访问单据');
});

test('补充入队缺少preview或export时不发出写请求', async (context) => {
  let starts = 0;
  const f = mount('batch', {
    listBatches: async () => ({ data: [{ id: 'own', owner: exactId }] }),
    startBatch: async () => {
      starts++;
    }
  });
  context.after(f.unmount);
  await flush();
  await f.state.open('own');
  f.fixture.permissions.splice(f.fixture.permissions.indexOf('report:runtime:export'), 1);
  await f.state.start('own');
  assert.equal(starts, 0);
  f.fixture.permissions.push('report:runtime:export');
  f.fixture.permissions.splice(f.fixture.permissions.indexOf('report:runtime:preview'), 1);
  await f.state.start('own');
  assert.equal(starts, 0);
});

test('业务数据集切换时旧批次创建响应不能继续入队或跳转', async (context) => {
  const creating = deferred();
  let starts = 0;
  const f = mount(
    'dialog',
    {
      businessReportTemplates: async () => ({ data: [{ id: 'doc', kind: 'DOCUMENT', versionId: 'version-A' }] }),
      createBatch: () => creating.promise,
      startBatch: async () => {
        starts++;
      }
    },
    { open: true, datasetCode: 'ERP_PURCHASE_ORDER_DOCUMENT', parameter: 'orderId', recordIds: [exactId] }
  );
  context.after(f.unmount);
  await flush();
  const writing = f.state.submit();
  f.props.datasetCode = 'ERP_SALES_ORDER_DOCUMENT';
  await flush();
  creating.resolve({ data: { id: 'old-batch', owner: exactId } });
  await writing;
  assert.equal(starts, 0);
  assert.equal(f.fixture.routes.length, 0);
});

test('账号切换撤销旧批次创建的后续入队且清空旧提交内容', async (context) => {
  const creating = deferred();
  let starts = 0;
  const f = mount('batch', {
    createBatch: () => creating.promise,
    startBatch: async () => {
      starts++;
    }
  });
  context.after(f.unmount);
  await flush();
  f.state.documents = [{ key: 'one', reportVersionId: 'version-A', template: published(), parameters: { orderId: exactId } }];
  const writing = f.state.create();
  f.fixture.user.userId = 'new-user';
  await flush();
  creating.resolve({ data: { id: 'old-user-batch', owner: exactId } });
  await writing;
  assert.equal(starts, 0);
  assert.equal(f.state.pending, undefined);
  assert.equal(f.state.selected, '');
});

test('批次PDF领取同时要求preview和export权限', async (context) => {
  const f = mount('batch', { listBatches: async () => ({ data: [{ id: 'own', owner: exactId }] }) });
  context.after(f.unmount);
  await flush();
  await f.state.open('own');
  assert.equal(f.state.canReadPdf, true);
  f.fixture.permissions.splice(f.fixture.permissions.indexOf('report:runtime:preview'), 1);
  await flush();
  assert.equal(f.state.canReadPdf, false);
});

test('API使用合并PDF的blob长超时，重试发送显示的预期轮次', async () => {
  const requests = [];
  globalThis.__businessPrintRequests = requests;
  const apiModules = {};
  for (const [name, entry] of [
    ['batch', 'src/api/report/batch.ts'],
    ['report', 'src/api/report/index.ts']
  ]) {
    const outfile = `node_modules/.tmp/report-business-api-${name}.mjs`;
    await build({
      entryPoints: [entry],
      outfile,
      bundle: true,
      platform: 'node',
      format: 'esm',
      plugins: [
        {
          name: 'request-boundary',
          /** 只记录真实API包装器构造的请求。 */
          setup(builder) {
            builder.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'request', namespace: 'fixture' }));
            builder.onLoad({ filter: /.*/, namespace: 'fixture' }, () => ({
              contents: 'export default config=>{globalThis.__businessPrintRequests.push(config);return Promise.resolve({});};'
            }));
          }
        }
      ]
    });
    apiModules[name] = await import(pathToFileURL(resolve(outfile)));
  }
  await apiModules.batch.batchPdf(exactId);
  assert.deepEqual(requests[0], { url: `/report/batches/${exactId}/pdf`, method: 'get', responseType: 'blob', timeout: 120000 });
  await apiModules.report.retryExport(exactId, 4);
  assert.deepEqual(requests[1].data, { retryRound: 4 });
  assert.equal(requests[1].url, `/report/runtime/exports/${exactId}/retry`);
});

test('批次创建缺少安全随机能力时不发出任何写请求', async (context) => {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'crypto');
  Object.defineProperty(globalThis, 'crypto', { configurable: true, value: undefined });
  context.after(() => Object.defineProperty(globalThis, 'crypto', original));
  let calls = 0;
  const f = mount('batch', {
    createBatch: async () => {
      calls++;
    }
  });
  context.after(f.unmount);
  await flush();
  f.state.documents = [{ key: 'one', reportVersionId: 'version-A', template: published(), parameters: { orderId: exactId } }];
  await f.state.create();
  assert.equal(calls, 0);
  assert.equal(f.state.busy, false);
  assert.ok(f.fixture.messages.some((message) => String(message).includes('安全随机')));
});

test('业务入口拒绝未在本次授权目录中的模板', async (context) => {
  const f = mount(
    'button',
    { businessReportTemplates: async () => ({ data: [{ id: 'allowed', kind: 'DOCUMENT', versionId: 'v' }] }) },
    { datasetCode: 'ERP_PURCHASE_ORDER_DOCUMENT', parameter: 'orderId', recordId: exactId }
  );
  context.after(f.unmount);
  f.state.open('allowed');
  assert.equal(f.fixture.routes.length, 0);
  await f.state.load();
  f.state.open('unlisted');
  assert.equal(f.fixture.routes.length, 0);
  f.fixture.permissions.splice(0);
  f.state.open('allowed');
  assert.equal(f.fixture.routes.length, 0);
});

test('PDF读取期间owner失效应撤销晚响应和文件访问', async (context) => {
  const late = deferred();
  const f = mount('pdf', {}, { open: true, sourceId: 'job', filename: 'same.pdf', canDownload: true, load: () => late.promise });
  context.after(f.unmount);
  await flush();
  f.props.canDownload = false;
  await flush();
  late.resolve(new Blob(['%PDF-1.7\nfixture'], { type: 'application/pdf' }));
  await flush();
  assert.equal(f.state.url, '');
  assert.equal(f.state.loading, false);
});

test('创建批次的晚响应不能把用户切回旧批次', async (context) => {
  const starting = deferred();
  const f = mount('batch', {
    createBatch: async () => ({ data: { id: 'created', owner: exactId } }),
    startBatch: () => starting.promise,
    listBatches: async () => ({
      data: [
        { id: 'created', owner: exactId },
        { id: 'new-selection', owner: exactId }
      ]
    })
  });
  context.after(f.unmount);
  await flush();
  f.state.documents = [{ key: 'one', reportVersionId: 'version-A', template: published(), parameters: { orderId: exactId } }];
  const writing = f.state.create();
  await flush();
  await f.state.open('new-selection');
  starting.resolve();
  await writing;
  assert.equal(f.state.selected, 'new-selection');
});

test('批次切换后旧详情晚响应不会覆盖新结果', async (context) => {
  const old = deferred();
  const f = mount('batch', {
    batchItems: (id) =>
      id === 'old' ? old.promise : Promise.resolve({ data: [{ number: 0, reportVersionId: 'new-version', state: 'SUCCEEDED', jobId: 'new-job' }] })
  });
  context.after(f.unmount);
  await flush();
  const reading = f.state.open('old');
  await f.state.open('new');
  old.resolve({ data: [{ number: 0, state: 'FAILED', jobId: 'old-job' }] });
  await reading;
  assert.equal(f.state.selected, 'new');
  assert.equal(f.state.items[0].jobId, 'new-job');
});

test('模板发布版本变化时不能把新DSL绑定到旧version', async (context) => {
  const f = mount('batch', { publishedReport: async () => ({ data: published('new-published') }) });
  context.after(f.unmount);
  await flush();
  f.state.reports = [{ id: 'doc', publishedVersion: 'version-A' }];
  const item = f.state.documents[0];
  item.reportVersionId = 'version-A';
  await f.state.selectTemplate(item);
  assert.equal(item.template, undefined);
  assert.ok(item.failed);
  assert.throws(() => f.state.fixedDocuments());
});

test('新路由的单据自动预览不受旧模板晚响应影响', async (context) => {
  const old = deferred();
  const runs = [];
  const f = mount(
    'viewer',
    {
      publishedReport: (id) => (id === 'old' ? old.promise : Promise.resolve({ data: published() })),
      createRun: async (...args) => {
        runs.push(args);
        return { data: { id: 'new-run', mode: 'SNAPSHOT' } };
      }
    },
    {},
    { id: 'old', orderId: 'old-id', auto: '1' }
  );
  context.after(f.unmount);
  f.fixture.route.query = { id: 'new', orderId: exactId, auto: '1' };
  await flush();
  old.resolve({ data: published('old-version') });
  await flush();
  assert.deepEqual(runs, [['new', 'SNAPSHOT', { orderId: exactId }, 'version-A']]);
  assert.equal(f.state.run.id, 'new-run');
});

test('切换参数后PDF入队晚响应不打开原文件', async (context) => {
  const late = deferred();
  const f = mount('viewer', { enqueueExport: () => late.promise }, {}, { id: 'doc', orderId: exactId });
  context.after(f.unmount);
  await flush();
  await f.state.execute();
  const printing = f.state.printPdf();
  f.state.parameters = { orderId: 'other' };
  late.resolve({ data: { id: 'old-job', runId: 'run', state: 'SUCCEEDED', format: 'PDF' } });
  await printing;
  assert.equal(f.state.printJob, undefined);
  assert.equal(f.state.pdfOpen, false);
});

test('单张打印入队断网重试复用同一个安全请求键', async (context) => {
  const keys = [];
  const f = mount(
    'viewer',
    {
      enqueueExport: async (_id, _format, key) => {
        keys.push(key);
        if (keys.length === 1) throw Error('合成断网');
        return { data: { id: 'job', runId: 'run', state: 'SUCCEEDED', format: 'PDF' } };
      }
    },
    {},
    { id: 'doc', orderId: exactId }
  );
  context.after(f.unmount);
  await flush();
  await f.state.execute();
  await f.state.printPdf();
  await f.state.printPdf();
  assert.equal(keys.length, 2);
  assert.equal(keys[0], keys[1]);
  assert.equal(f.state.pdfOpen, true);
});

test('批次参数使用固定发布DSL，迟到的模板响应不能覆盖新选择', async (context) => {
  const old = deferred();
  const f = mount('batch', { publishedReport: (id) => (id === 'A' ? old.promise : Promise.resolve({ data: published('version-B') })) });
  context.after(f.unmount);
  await flush();
  f.state.reports = [
    { id: 'A', publishedVersion: 'version-A' },
    { id: 'B', publishedVersion: 'version-B' }
  ];
  const item = f.state.documents[0];
  item.reportVersionId = 'version-A';
  const pending = f.state.selectTemplate(item);
  item.reportVersionId = 'version-B';
  await f.state.selectTemplate(item);
  old.resolve({ data: published('version-A') });
  await pending;
  assert.equal(item.template.versionId, 'version-B');
  item.parameters = { orderId: exactId };
  assert.deepEqual(f.state.fixedDocuments(), [{ reportVersionId: 'version-B', parameters: { orderId: exactId } }]);
});
test('批次create网络失败后重试固定code和内容，start失败只补start', async (context) => {
  const creates = [];
  const starts = [];
  const f = mount('batch', {
    createBatch: async (...args) => {
      creates.push(structuredClone(args));
      if (creates.length === 1) throw Error('合成断网');
      return { data: { id: 'batch', owner: exactId } };
    },
    startBatch: async (id) => {
      starts.push(id);
      if (starts.length === 1) throw Error('合成入队失败');
    }
  });
  context.after(f.unmount);
  await flush();
  f.state.title = '原始批次';
  f.state.documents = [{ key: 'one', reportVersionId: 'version-A', template: published(), parameters: { orderId: exactId } }];
  await f.state.create();
  f.state.title = '不可覆盖原始提交';
  f.state.documents[0].parameters.orderId = 'other';
  await f.state.create();
  await f.state.create();
  assert.equal(creates.length, 2);
  assert.deepEqual(creates[0], creates[1]);
  assert.match(creates[0][0], /^BATCH_[A-F0-9]{32}$/);
  assert.deepEqual(starts, ['batch', 'batch']);
});
test('创建运行期间发布版本变化时拒绝展示另一版本的数据与打印文件', async (context) => {
  let pages = 0;
  const f = mount(
    'viewer',
    {
      createRun: async () => ({ data: { id: 'changed-run', version: 'version-B', mode: 'SNAPSHOT' } }),
      runPage: async () => {
        pages++;
        return { data: { rows: [], columns: [], total: 1, summaries: [] } };
      }
    },
    {},
    { id: 'doc', versionId: 'version-A', orderId: exactId }
  );
  context.after(f.unmount);
  await flush();
  await f.state.execute();
  assert.equal(pages, 0);
  assert.equal(f.state.run, undefined);
  assert.match(f.state.runFailed, /版本已变化/);
});

test('业务批量确认保留字符串ID和调整后的提交顺序', async (context) => {
  let payload;
  const f = mount(
    'dialog',
    {
      businessReportTemplates: async () => ({ data: [{ id: 'doc', name: '单据', kind: 'DOCUMENT', versionId: 'version-A' }] }),
      createBatch: async (...args) => {
        payload = args;
        return { data: { id: 'batch', owner: exactId } };
      },
      startBatch: async () => {}
    },
    { open: true, datasetCode: 'ERP_PURCHASE_ORDER_DOCUMENT', parameter: 'orderId', recordIds: [exactId, '9223372036854775806'] }
  );
  context.after(f.unmount);
  await flush();
  f.state.versionId = 'version-A';
  f.state.move(1, -1);
  await f.state.submit();
  assert.deepEqual(payload[2], [
    { reportVersionId: 'version-A', parameters: { orderId: '9223372036854775806' } },
    { reportVersionId: 'version-A', parameters: { orderId: exactId } }
  ]);
});
test('批量超过50项时禁止调用create', async (context) => {
  let calls = 0;
  const f = mount(
    'dialog',
    {
      createBatch: async () => {
        calls++;
      }
    },
    {
      open: true,
      datasetCode: 'ERP_PURCHASE_ORDER_DOCUMENT',
      parameter: 'orderId',
      recordIds: Array.from({ length: 51 }, (_, index) => String(index + 1))
    }
  );
  context.after(f.unmount);
  await flush();
  f.state.versionId = 'v';
  await f.state.submit();
  assert.equal(calls, 0);
});
test('可恢复批次项使用显示的expectedRound重试原任务，未入队失败补start', async (context) => {
  const retries = [];
  const starts = [];
  const f = mount('batch', {
    retryExport: async (...args) => retries.push(args),
    startBatch: async (id) => starts.push(id),
    listBatches: async () => ({ data: [{ id: 'batch', owner: exactId }] })
  });
  context.after(f.unmount);
  await flush();
  await f.state.open('batch');
  await f.state.retryItem({ number: 0, jobId: 'job', state: 'FAILED', canRetry: true, retryRound: 3, expires: '2099-01-01T00:00:00Z' });
  assert.deepEqual(retries, [['job', 3]]);
  await f.state.retryItem({ number: 1, jobId: null, state: 'FAILED', canRetry: false });
  assert.deepEqual(starts, ['batch']);
  await f.state.retryItem({ number: 2, jobId: 'expired', state: 'EXPIRED', canRetry: false });
  assert.equal(retries.length, 1);
  assert.equal(starts.length, 1);
});
test('共享可见批次不能下载，owner按字符串与userstore比较', async (context) => {
  const f = mount('batch', {
    listBatches: async () => ({
      data: [
        { id: 'shared', owner: 'other' },
        { id: 'own', owner: exactId }
      ]
    })
  });
  context.after(f.unmount);
  await flush();
  await f.state.open('shared');
  assert.equal(f.state.isOwner, false);
  await f.state.open('own');
  assert.equal(f.state.isOwner, true);
});
test('PDF预览切换、关闭和卸载回收URL，晚响应不产生URL', async (context) => {
  const originalCreate = URL.createObjectURL;
  const originalRevoke = URL.revokeObjectURL;
  const created = [];
  const revoked = [];
  URL.createObjectURL = () => {
    const url = 'blob:fixture-' + created.length;
    created.push(url);
    return url;
  };
  URL.revokeObjectURL = (url) => revoked.push(url);
  context.after(() => {
    URL.createObjectURL = originalCreate;
    URL.revokeObjectURL = originalRevoke;
  });
  const late = deferred();
  let calls = 0;
  const f = mount(
    'pdf',
    {},
    {
      open: true,
      sourceId: 'one',
      canDownload: true,
      filename: 'test.pdf',
      load: () => (++calls === 2 ? late.promise : Promise.resolve(new Blob(['%PDF-1.7\nfixture'], { type: 'application/pdf' })))
    }
  );
  await flush();
  assert.equal(created.length, 1);
  f.props.sourceId = 'two';
  await flush();
  assert.deepEqual(revoked, ['blob:fixture-0']);
  f.props.open = false;
  await flush();
  late.resolve(new Blob(['%PDF-1.7']));
  await flush();
  assert.equal(created.length, 1);
  f.props.open = true;
  await flush();
  assert.equal(created.length, 2);
  f.unmount();
  assert.deepEqual(revoked, created);
});
test('JSON错误信封不能创建PDF地址或触发下载', async (context) => {
  const f = mount(
    'pdf',
    {},
    {
      open: true,
      sourceId: 'bad',
      canDownload: true,
      filename: 'bad.pdf',
      load: async () => new Blob(['{"code":403,"msg":"无权限"}'], { type: 'application/json' })
    }
  );
  context.after(f.unmount);
  await flush();
  assert.equal(f.state.url, '');
  assert.ok(f.state.failed);
  await f.state.download();
  assert.equal(f.fixture.downloads.length, 0);
});
test('打印PDF只入队当前run并轮询同一job，不重新查询业务', async (context) => {
  const enqueues = [];
  let runs = 0;
  const f = mount(
    'viewer',
    {
      createRun: async () => {
        runs++;
        return { data: { id: 'run-fixed', mode: 'SNAPSHOT' } };
      },
      enqueueExport: async (...args) => {
        enqueues.push(args);
        return { data: { id: 'pdf-job', runId: 'run-fixed', state: 'QUEUED', format: 'PDF' } };
      },
      listExportJobs: async () => ({ data: [{ id: 'pdf-job', runId: 'run-fixed', state: 'SUCCEEDED', format: 'PDF' }] })
    },
    {},
    { id: 'doc', orderId: exactId }
  );
  context.after(f.unmount);
  await flush();
  await f.state.execute();
  await f.state.printPdf();
  await f.state.pollPrint();
  assert.equal(runs, 1);
  assert.equal(enqueues.length, 1);
  assert.equal(enqueues[0][0], 'run-fixed');
  assert.equal(enqueues[0][1], 'PDF');
  assert.equal(f.state.printJob.id, 'pdf-job');
  assert.equal(f.state.pdfOpen, true);
});
test('四业务列表分别接入行内单张与toolbar多选入口', async () => {
  for (const [path, dataset, parameter, field] of [
    ['pu/order', 'ERP_PURCHASE_ORDER_DOCUMENT', 'orderId', 'orderId'],
    ['so/orderheader', 'ERP_SALES_ORDER_DOCUMENT', 'orderId', 'soId'],
    ['wm/sales-issue', 'ERP_DELIVERY_DOCUMENT', 'issueId', 'issueId'],
    ['wm/receive', 'ERP_PURCHASE_RECEIVE_DOCUMENT', 'receiveId', 'receiveId']
  ]) {
    const source = await readFile(`src/views/erp/${path}/index.vue`, 'utf8');
    const tags = source.match(/<BusinessReportButton\b[^>]*\/>/gs) || [];
    assert.ok(
      tags.some(
        (tag) =>
          tag.includes(`dataset-code="${dataset}"`) && tag.includes(`parameter="${parameter}"`) && tag.includes(`:record-id="scope.row.${field}"`)
      ),
      path + ' 缺少行内入口'
    );
    assert.ok(
      tags.some((tag) => tag.includes(`dataset-code="${dataset}"`) && tag.includes(':record-ids="ids"')),
      path + ' 缺少多选入口'
    );
  }
});

test('业务入口只列出有固定版本的DOCUMENT并原样传递单据参数', async (context) => {
  const f = mount(
    'button',
    {
      businessReportTemplates: async () => ({
        data: [
          { id: 'table', name: '台账', kind: 'TABLE', versionId: 'v1' },
          { id: 'doc', name: '单据', kind: 'DOCUMENT', versionId: 'v2' }
        ]
      })
    },
    { datasetCode: 'ERP_PURCHASE_ORDER_DOCUMENT', parameter: 'orderId', recordId: exactId }
  );
  context.after(f.unmount);
  await f.state.load();
  assert.deepEqual(
    f.state.reports.map((report) => report.id),
    ['doc']
  );
  f.state.open('doc');
  assert.equal(f.fixture.routes.length, 0);
  assert.equal(f.state.previewParameters.orderId, exactId);
  assert.equal(f.state.previewOpen, true);
  assert.equal(f.state.selectedTemplate.versionId, 'v2');
});
test('业务入口不接受已丢失精度的数字ID', async (context) => {
  const f = mount(
    'button',
    { businessReportTemplates: async () => ({ data: [{ id: 'doc', kind: 'DOCUMENT', versionId: 'v' }] }) },
    { datasetCode: 'ERP_PURCHASE_ORDER_DOCUMENT', parameter: 'orderId', recordId: 9223372036854776000 }
  );
  context.after(f.unmount);
  await f.state.load();
  f.state.open('doc');
  assert.equal(f.fixture.routes.length, 0);
});
test('单据入口只自动创建一次SNAPSHOT，无关query变化保留当前run', async (context) => {
  const calls = [];
  const f = mount(
    'viewer',
    {
      createRun: async (...args) => {
        calls.push(args);
        return { data: { id: 'run', mode: 'SNAPSHOT' } };
      }
    },
    {},
    { id: 'doc', orderId: exactId, auto: '1' }
  );
  context.after(f.unmount);
  await flush();
  await flush();
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0], ['doc', 'SNAPSHOT', { orderId: exactId }, 'version-A']);
  f.fixture.route.query.tab = 'unrelated';
  await flush();
  assert.equal(calls.length, 1);
  assert.equal(f.state.run.id, 'run');
});
test('自动预览失败后无关query变化不能重复创建run', async (context) => {
  let calls = 0;
  const f = mount(
    'viewer',
    {
      createRun: async () => {
        calls++;
        throw Error('合成网络失败');
      }
    },
    {},
    { id: 'doc', orderId: exactId, auto: '1' }
  );
  context.after(f.unmount);
  await flush();
  f.fixture.route.query.tab = 'other';
  await flush();
  assert.equal(calls, 1);
});
