import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { readFile, mkdir } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { parse, compileScript } from '@vue/compiler-sfc';
import { createRenderer, reactive, nextTick, h } from 'vue';
await mkdir('node_modules/.tmp', { recursive: true });
const apiNames = [
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
const mockPlugin = {
  name: 'report-ui-fixtures',
  setup(b) {
    b.onResolve({ filter: /^(@\/api\/report|vue-router|element-plus|file-saver|@element-plus\/icons-vue)$/ }, (a) => ({
      path: a.path,
      namespace: 'fixtures'
    }));
    b.onLoad({ filter: /.*/, namespace: 'fixtures' }, (a) => {
      let contents = '';
      if (a.path === '@/api/report')
        contents = apiNames.map((n) => 'export const ' + n + '=(...args)=>globalThis.__reportUI.api["' + n + '"](...args);').join('\n');
      if (a.path === 'vue-router')
        contents =
          'export const useRoute=()=>globalThis.__reportUI.route; export const useRouter=()=>globalThis.__reportUI.router; export const onBeforeRouteLeave=f=>{globalThis.__reportUI.guards.leave=f;};export const onBeforeRouteUpdate=f=>{globalThis.__reportUI.guards.update=f;};';
      if (a.path === 'element-plus')
        contents =
          'export const ElMessage={success:(...a)=>globalThis.__reportUI.messages.push(a),warning:(...a)=>globalThis.__reportUI.messages.push(a),error:(...a)=>globalThis.__reportUI.messages.push(a)};export const ElMessageBox={confirm:(...a)=>globalThis.__reportUI.confirm(...a),prompt:(...a)=>globalThis.__reportUI.prompt(...a)};';
      if (a.path === 'file-saver') contents = 'export const saveAs=(...args)=>globalThis.__reportUI.downloads.push(args);';
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
const componentPaths = {
  acl: 'AclDialog',
  contract: 'DatasetContractBar',
  batch: 'DesignerBatchTools',
  lookup: 'ParameterLookup',
  business: 'BusinessReportButton',
  package: 'TemplatePackageDialog'
};
for (const name of ['viewer', 'designer', 'dataset', 'history', 'exports', 'center', ...Object.keys(componentPaths)])
  await build({
    entryPoints: [componentPaths[name] ? 'src/components/report/' + componentPaths[name] + '.vue' : 'src/views/report/' + name + '/index.vue'],
    outfile: 'node_modules/.tmp/report-' + name + '-interaction.mjs',
    bundle: true,
    format: 'esm',
    platform: 'node',
    external: ['vue'],
    plugins: [mockPlugin]
  });
const viewer = (await import(pathToFileURL(resolve('node_modules/.tmp/report-viewer-interaction.mjs')))).default;
const designer = (await import(pathToFileURL(resolve('node_modules/.tmp/report-designer-interaction.mjs')))).default;
const dataset = (await import(pathToFileURL(resolve('node_modules/.tmp/report-dataset-interaction.mjs')))).default;
const history = (await import(pathToFileURL(resolve('node_modules/.tmp/report-history-interaction.mjs')))).default;
const exportsPage = (await import(pathToFileURL(resolve('node_modules/.tmp/report-exports-interaction.mjs')))).default;
const center = (await import(pathToFileURL(resolve('node_modules/.tmp/report-center-interaction.mjs')))).default;
const contract = (await import(pathToFileURL(resolve('node_modules/.tmp/report-contract-interaction.mjs')))).default;
const batch = (await import(pathToFileURL(resolve('node_modules/.tmp/report-batch-interaction.mjs')))).default;
const lookup = (await import(pathToFileURL(resolve('node_modules/.tmp/report-lookup-interaction.mjs')))).default;
const templateDialog = (await import(pathToFileURL(resolve('node_modules/.tmp/report-package-interaction.mjs')))).default;
const business = (await import(pathToFileURL(resolve('node_modules/.tmp/report-business-interaction.mjs')))).default;
const acl = (await import(pathToFileURL(resolve('node_modules/.tmp/report-acl-interaction.mjs')))).default;
const renderer = createRenderer({
  createElement: () => ({}),
  createComment: () => ({}),
  createText: () => ({}),
  insert: () => {},
  remove: () => {},
  setElementText: () => {},
  setText: () => {},
  setComment: () => {},
  patchProp: () => {},
  parentNode: () => null,
  nextSibling: () => null
});
const dsl = {
  schemaVersion: 1,
  code: 'REPORT_A',
  title: '合成样本',
  kind: 'TABLE',
  datasetCode: 'TEST',
  datasetVersionId: '9223372036854775807',
  columns: [
    {
      field: 'id',
      label: 'ID',
      width: 100,
      scale: 0,
      expression: null,
      style: { fontSize: 10, bold: false, align: 'LEFT', color: '#000000', background: '#ffffff' }
    }
  ],
  parameters: [],
  page: { width: 595.28, height: 841.89, marginTop: 36, marginRight: 36, marginBottom: 36, marginLeft: 36, rowHeight: 24 },
  headerRows: [],
  footerRows: [],
  summaries: [],
  repeatHeader: true
};
const detail = () => ({
  definition: { id: '9223372036854775806', code: 'REPORT_A', name: '合成样本', kind: 'TABLE', catalogId: null, revision: 7, status: 'DRAFT' },
  dsl: structuredClone(dsl)
});
const defer = () => {
  let resolve;
  let reject;
  const promise = new Promise((a, b) => {
    resolve = a;
    reject = b;
  });
  return { promise, resolve, reject };
};
async function flush() {
  await new Promise((r) => setImmediate(r));
  await nextTick();
}
function mount(component, id = '9223372036854775806', overrides = {}, props = {}) {
  const fixture = {
    route: reactive({ query: { id } }),
    guards: {},
    messages: [],
    downloads: [],
    errors: [],
    confirm: async () => true,
    prompt: async () => ({ value: 'calc_1' }),
    router: {
      push: async () => {},
      replace: async (value) => {
        fixture.route.query = value.query;
      }
    },
    api: {
      publishedReport: async () => ({ data: { versionId: 'version', number: 1, dsl: structuredClone(dsl) } }),
      listDatasets: async () => ({ data: [] }),
      reportLookupBindings: async () => ({ data: [] }),
      reportLookup: async () => ({ data: { options: [], total: 0 } }),
      datasetVersion: async () => ({
        data: {
          id: dsl.datasetVersionId,
          code: dsl.datasetCode,
          number: 1,
          current: true,
          descriptor: { code: dsl.datasetCode, fields: [], parameters: [] }
        }
      }),
      datasetVersionDiff: async () => ({ data: { from: dsl.datasetVersionId, to: dsl.datasetVersionId, breaking: false, changes: [] } }),
      listReports: async () => ({ data: [] }),
      listVersions: async () => ({ data: [] }),
      listGrants: async () => ({ data: [] }),
      listExportJobs: async () => ({ data: [] }),
      listCatalogs: async () => ({ data: [] }),
      reportDetail: async () => ({ data: detail() }),
      validateDraft: async () => ({ data: { valid: true } }),
      ...overrides
    }
  };
  globalThis.__reportUI = fixture;
  const inputs = reactive(props);
  const subject = { ...component, render: () => null };
  const app = renderer.createApp({ render: () => h(subject, inputs) });
  app.config.errorHandler = (error) => fixture.errors.push(error);
  const vm = app.mount({});
  return { fixture, props: inputs, state: vm.$.subTree.component.setupState, unmount: () => app.unmount() };
}
test('document viewer completes loading when default mode changes to SNAPSHOT', async () => {
  const x = mount(viewer, 'doc', {
    publishedReport: async () => ({ data: { number: 1, versionId: 'version', dsl: { ...structuredClone(dsl), kind: 'DOCUMENT' } } })
  });
  await flush();
  assert.equal(x.state.mode, 'SNAPSHOT');
  assert.equal(x.state.loading, false);
  assert.equal(x.state.published.dsl.kind, 'DOCUMENT');
  x.unmount();
});
test('late published definition cannot override a newer route', async () => {
  const a = defer();
  const b = defer();
  const x = mount(viewer, 'A', { publishedReport: (id) => (id === 'A' ? a.promise : b.promise) });
  x.fixture.route.query.id = 'B';
  await nextTick();
  b.resolve({ data: { number: 2, versionId: 'B', dsl: { ...structuredClone(dsl), title: 'B' } } });
  await flush();
  a.resolve({ data: { number: 1, versionId: 'A', dsl: { ...structuredClone(dsl), title: 'A' } } });
  await flush();
  assert.equal(x.state.published.dsl.title, 'B');
  assert.equal(x.state.loading, false);
  x.unmount();
});
test('changing input discards a run reply before fetching its page', async () => {
  const pending = defer();
  let pages = 0;
  const x = mount(viewer, 'A', {
    createRun: () => pending.promise,
    runPage: async () => {
      pages++;
      return { data: { rows: [], columns: [], total: 0 } };
    }
  });
  await flush();
  const execution = x.state.execute();
  x.state.parameters = { orderId: '9223372036854775807' };
  pending.resolve({ data: { id: 'old-run', mode: 'LIVE' } });
  await execution;
  assert.equal(x.state.run, undefined);
  assert.equal(pages, 0);
  x.unmount();
});
test('failed designer validation cannot write a definition', async () => {
  let writes = 0;
  const x = mount(designer, '9223372036854775806', {
    validateDraft: async () => {
      throw new Error('invalid');
    },
    saveReport: async () => {
      writes++;
    }
  });
  await flush();
  assert.equal(await x.state.save(), false);
  assert.equal(writes, 0);
  assert.equal(x.state.definition.revision, 7);
  x.unmount();
});
test('optimistic save uses loaded revision and preserves edits made during the request', async () => {
  const pending = defer();
  let sent;
  const x = mount(designer, '9223372036854775806', {
    saveReport: (id, revision, value) => {
      sent = { id, revision, value };
      return pending.promise;
    }
  });
  await flush();
  const request = x.state.save();
  await flush();
  x.state.dsl.title = '编辑中的新标题';
  pending.resolve({ data: { ...detail().definition, revision: 8 } });
  await request;
  assert.equal(sent.id, '9223372036854775806');
  assert.equal(sent.revision, 7);
  assert.equal(sent.value.datasetVersionId, '9223372036854775807');
  assert.equal(x.state.dsl.title, '编辑中的新标题');
  assert.equal(x.state.dirty, true);
  x.unmount();
});
test('publishing submits saved revision and never the stale revision', async () => {
  const sequence = [];
  const x = mount(designer, '9223372036854775806', {
    saveReport: async (id, rev) => {
      sequence.push(['save', rev]);
      return { data: { ...detail().definition, revision: 8 } };
    },
    publishReport: async (id, rev) => {
      sequence.push(['publish', rev]);
      return { data: { id: 'version' } };
    }
  });
  await flush();
  await x.state.publish();
  assert.deepEqual(sequence, [
    ['save', 7],
    ['publish', 8]
  ]);
  x.unmount();
});

const reportAt = (id) => ({ definition: { ...detail().definition, id, name: id }, dsl: { ...structuredClone(dsl), title: id } });
const previewResult = () => ({ rows: [], total: 0, summaries: [], plan: null, dataTime: '2026-10-14T00:00:00Z' });
const datasetFixture = (id) => ({
  dataset: { id, code: id, status: 'ACTIVE' },
  descriptor: { code: id, parameters: [], defaultColumns: [], fields: [] }
});

test('route change during validation cannot save the prior DSL into the next report', async (t) => {
  const pending = defer();
  const writes = [];
  const x = mount(designer, 'A', {
    reportDetail: async (id) => ({ data: reportAt(id) }),
    validateDraft: () => pending.promise,
    saveReport: async (...args) => {
      writes.push(args);
      return { data: reportAt(args[0]).definition };
    }
  });
  t.after(x.unmount);
  await flush();
  const request = x.state.save();
  x.fixture.route.query.id = 'B';
  await flush();
  pending.resolve({ data: { valid: true } });
  await request;
  assert.deepEqual(writes, []);
  assert.equal(x.state.definition.id, 'B');
  assert.equal(x.state.dsl.title, 'B');
});
test('late save response cannot replace a newer report or redirect its route', async (t) => {
  const pending = defer();
  const x = mount(designer, 'A', { reportDetail: async (id) => ({ data: reportAt(id) }), saveReport: () => pending.promise });
  t.after(x.unmount);
  await flush();
  const request = x.state.save();
  await flush();
  x.fixture.route.query.id = 'B';
  await flush();
  pending.resolve({ data: { ...reportAt('A').definition, revision: 8 } });
  await request;
  await flush();
  assert.equal(x.fixture.route.query.id, 'B');
  assert.equal(x.state.definition.id, 'B');
  assert.equal(x.state.dsl.title, 'B');
});
test('save and publish does not publish a stale draft when edits arrive during saving', async (t) => {
  const pending = defer();
  let publishes = 0;
  const x = mount(designer, 'A', {
    reportDetail: async (id) => ({ data: reportAt(id) }),
    saveReport: () => pending.promise,
    publishReport: async () => {
      publishes++;
      return { data: {} };
    }
  });
  t.after(x.unmount);
  await flush();
  const request = x.state.publish();
  await flush();
  x.state.dsl.title = 'later edit';
  pending.resolve({ data: { ...reportAt('A').definition, revision: 8 } });
  await request;
  assert.equal(publishes, 0);
  assert.equal(x.state.dirty, true);
  assert.equal(x.state.dsl.title, 'later edit');
});
test('unapplied definition text is dirty and cannot silently save the previous canvas', async (t) => {
  let writes = 0;
  const x = mount(designer, 'A', {
    reportDetail: async (id) => ({ data: reportAt(id) }),
    saveReport: async () => {
      writes++;
      return { data: reportAt('A').definition };
    }
  });
  t.after(x.unmount);
  await flush();
  x.state.tab = 'json';
  await nextTick();
  x.state.jsonText = JSON.stringify({ ...structuredClone(dsl), title: 'new definition text' });
  assert.equal(x.state.dirty, true);
  assert.equal(await x.state.save(), false);
  assert.equal(writes, 0);
});
test('navigation is blocked while a draft write is in flight', async (t) => {
  const pending = defer();
  const x = mount(designer, 'A', { reportDetail: async (id) => ({ data: reportAt(id) }), saveReport: () => pending.promise });
  t.after(x.unmount);
  await flush();
  const request = x.state.save();
  await flush();
  const mayLeave = await x.fixture.guards.leave();
  pending.resolve({ data: { ...reportAt('A').definition, revision: 8 } });
  await request;
  assert.equal(mayLeave, false);
  assert.equal(await x.fixture.guards.leave(), true);
});
test('late validated JSON cannot overwrite a newly selected report', async (t) => {
  const pending = defer();
  const x = mount(designer, 'A', { reportDetail: async (id) => ({ data: reportAt(id) }), validateDraft: () => pending.promise });
  t.after(x.unmount);
  await flush();
  x.state.jsonText = JSON.stringify({ ...structuredClone(dsl), title: 'A JSON' });
  const request = x.state.applyJson();
  x.fixture.route.query.id = 'B';
  await flush();
  pending.resolve({ data: { valid: true } });
  await request;
  assert.equal(x.state.dsl.title, 'B');
});
test('changing draft preview parameters discards pending data and releases the spinner', async (t) => {
  const pending = defer();
  const x = mount(designer, 'A', { reportDetail: async (id) => ({ data: reportAt(id) }), previewDraft: () => pending.promise });
  t.after(x.unmount);
  await flush();
  const request = x.state.fetchPreview();
  x.state.parameters = { orderId: 'new-input' };
  await nextTick();
  pending.resolve({ data: previewResult() });
  await request;
  assert.equal(x.state.previewData, undefined);
  assert.equal(x.state.previewing, false);
});
test('changing dataset sample parameters discards pending data', async (t) => {
  const pending = defer();
  const x = mount(dataset, '', { sampleDataset: () => pending.promise });
  t.after(x.unmount);
  await flush();
  x.state.inspect(datasetFixture('A'));
  const request = x.state.sample();
  x.state.parameters = { orderId: 'new-input' };
  await nextTick();
  pending.resolve({ data: previewResult() });
  await request;
  assert.equal(x.state.sampleData, undefined);
  assert.equal(x.state.sampling, false);
});
test('switching datasets releases the previous sample spinner', async (t) => {
  const pending = defer();
  const x = mount(dataset, '', { sampleDataset: () => pending.promise });
  t.after(x.unmount);
  await flush();
  x.state.inspect(datasetFixture('A'));
  const request = x.state.sample();
  x.state.inspect(datasetFixture('B'));
  pending.resolve({ data: previewResult() });
  await request;
  assert.equal(x.state.sampling, false);
  assert.equal(x.state.sampleData, undefined);
  assert.equal(x.state.selected.dataset.id, 'B');
});
test('ACL opens with its grants loaded and never saves after a failed read', async (t) => {
  let writes = 0;
  const x = mount(
    acl,
    '',
    {
      listGrants: async () => {
        throw new Error('denied');
      },
      saveGrants: async () => {
        writes++;
      }
    },
    { open: true, resourceType: 'REPORT', resourceId: 'A' }
  );
  t.after(x.unmount);
  await flush();
  assert.equal(await x.state.save(), false);
  assert.equal(writes, 0);
  assert.equal(x.fixture.errors.length, 0);
});
test('late ACL save cannot close authorization for another resource', async (t) => {
  const pending = defer();
  const closes = [];
  const x = mount(
    acl,
    '',
    {
      listGrants: async (type, id) => ({ data: [{ subjectType: 'ROLE', subjectId: '1', action: id === 'A' ? 'EDIT' : 'VIEW' }] }),
      saveGrants: () => pending.promise
    },
    { open: false, resourceType: 'REPORT', resourceId: 'A', 'onUpdate:open': (value) => closes.push(value) }
  );
  t.after(x.unmount);
  x.props.open = true;
  await flush();
  const request = x.state.save();
  x.props.resourceId = 'B';
  await flush();
  pending.resolve({ data: null });
  await request;
  assert.deepEqual(closes, []);
  assert.equal(x.state.grants[0].action, 'VIEW');
  assert.equal(x.state.saving, false);
});
test('rollback confirmation does not write into a newly selected report', async (t) => {
  const pending = defer();
  const writes = [];
  const x = mount(history, 'A', {
    listReports: async () => ({ data: [reportAt('A').definition, reportAt('B').definition] }),
    rollbackReport: async (...args) => {
      writes.push(args);
    }
  });
  t.after(x.unmount);
  x.fixture.confirm = () => pending.promise;
  await flush();
  const request = x.state.rollback({ id: 'A-v1', number: 1 });
  x.fixture.route.query.id = 'B';
  await flush();
  pending.resolve(true);
  await request;
  assert.deepEqual(writes, []);
});
test('failed export refresh releases loading so the user can retry', async (t) => {
  const x = mount(exportsPage, '', {
    listExportJobs: async () => {
      throw new Error('offline');
    }
  });
  t.after(x.unmount);
  await flush();
  assert.equal(x.state.loading, false);
  x.fixture.api.listExportJobs = async () => ({ data: [{ id: 'job', state: 'QUEUED' }] });
  await x.state.load();
  assert.equal(x.state.jobs[0].id, 'job');
  assert.equal(x.state.loading, false);
});
test('clearing a viewer route during metadata loading releases its overlay', async (t) => {
  const pending = defer();
  const x = mount(viewer, 'A', { publishedReport: () => pending.promise });
  t.after(x.unmount);
  x.fixture.route.query.id = '';
  await nextTick();
  pending.resolve({ data: { number: 1, versionId: 'A', dsl: structuredClone(dsl) } });
  await flush();
  assert.equal(x.state.published, undefined);
  assert.equal(x.state.loading, false);
});

test('viewer defaults to SNAPSHOT for server-supported lower-case and unary aggregates', async (t) => {
  const x = mount(viewer, 'A', {
    publishedReport: async () => ({
      data: { number: 1, versionId: 'A', dsl: { ...structuredClone(dsl), columns: [{ ...dsl.columns[0], expression: 'min(id)' }] } }
    })
  });
  t.after(x.unmount);
  await flush();
  assert.equal(x.state.mode, 'SNAPSHOT');
});
test('center refresh retains the newest list when responses arrive backwards', async (t) => {
  const a = defer();
  const b = defer();
  let calls = 0;
  const x = mount(center, '', { listReports: () => (++calls === 1 ? a.promise : b.promise) });
  t.after(x.unmount);
  const request = x.state.load();
  b.resolve({ data: [reportAt('B').definition] });
  await request;
  a.resolve({ data: [reportAt('A').definition] });
  await flush();
  assert.equal(x.state.reports[0].id, 'B');
});
test('a dataset refresh cannot be overwritten by the preceding response', async (t) => {
  const a = defer();
  const b = defer();
  let calls = 0;
  const x = mount(dataset, '', { listDatasets: () => (++calls === 1 ? a.promise : b.promise) });
  t.after(x.unmount);
  const request = x.state.load();
  b.resolve({ data: [datasetFixture('B')] });
  await request;
  a.resolve({ data: [datasetFixture('A')] });
  await flush();
  assert.equal(x.state.datasets[0].dataset.id, 'B');
});
test('validated JSON does not overwrite canvas edits made during validation', async (t) => {
  const pending = defer();
  const x = mount(designer, 'A', { reportDetail: async (id) => ({ data: reportAt(id) }), validateDraft: () => pending.promise });
  t.after(x.unmount);
  await flush();
  x.state.jsonText = JSON.stringify({ ...structuredClone(dsl), title: 'JSON' });
  const request = x.state.applyJson();
  x.state.dsl.title = 'later canvas edit';
  pending.resolve({ data: { valid: true } });
  await request;
  assert.equal(x.state.dsl.title, 'later canvas edit');
});
test('ACL save captures its submitted grants and handles a denied write without closing', async (t) => {
  const pending = defer();
  let sent;
  const closes = [];
  const x = mount(
    acl,
    '',
    {
      listGrants: async () => ({ data: [{ subjectType: 'ROLE', subjectId: '1', action: 'VIEW' }] }),
      saveGrants: (type, id, grants) => {
        sent = grants;
        return pending.promise;
      }
    },
    { open: true, resourceType: 'REPORT', resourceId: 'A', 'onUpdate:open': (value) => closes.push(value) }
  );
  t.after(x.unmount);
  await flush();
  const request = x.state.save();
  x.state.grants[0].action = 'EDIT';
  pending.reject(new Error('denied'));
  assert.equal(await request, false);
  assert.equal(sent[0].action, 'VIEW');
  assert.deepEqual(closes, []);
  assert.equal(x.state.saving, false);
});

test('export polling resumes after a failed refresh is retried successfully', async (t) => {
  const polls = [];
  t.mock.method(globalThis, 'setInterval', (callback) => {
    polls.push(callback);
    return polls.length;
  });
  t.mock.method(globalThis, 'clearInterval', () => {});
  const x = mount(exportsPage, '', {
    listExportJobs: async () => {
      throw new Error('offline');
    }
  });
  t.after(x.unmount);
  await flush();
  x.fixture.api.listExportJobs = async () => ({ data: [{ id: 'job', state: 'QUEUED' }] });
  await x.state.load();
  assert.equal(polls.length, 2);
});
test('slow export polling does not repeatedly invalidate an in-flight response', async (t) => {
  let poll;
  let calls = 0;
  const pending = defer();
  t.mock.method(globalThis, 'setInterval', (callback) => {
    poll = callback;
    return 1;
  });
  t.mock.method(globalThis, 'clearInterval', () => {});
  const x = mount(exportsPage, '', {
    listExportJobs: () => (++calls === 1 ? Promise.resolve({ data: [{ id: 'job', state: 'QUEUED' }] }) : pending.promise)
  });
  t.after(x.unmount);
  await flush();
  poll();
  poll();
  assert.equal(calls, 2);
  pending.resolve({ data: [{ id: 'job', state: 'SUCCEEDED' }] });
  await flush();
  assert.equal(x.state.jobs[0].state, 'SUCCEEDED');
});

test('designer undo and redo restore saved dirty state and preserve exact IDs', async (t) => {
  const x = mount(designer, 'A', { reportDetail: async (id) => ({ data: reportAt(id) }) });
  t.after(x.unmount);
  await flush();
  x.state.dsl.title = 'edit';
  await nextTick();
  assert.equal(x.state.canUndo, true);
  x.state.undo();
  await nextTick();
  assert.equal(x.state.dsl.title, 'A');
  assert.equal(x.state.dirty, false);
  assert.equal(x.state.canRedo, true);
  x.state.redo();
  await nextTick();
  assert.equal(x.state.dsl.title, 'edit');
  assert.equal(x.state.dirty, true);
  assert.equal(x.state.dsl.datasetVersionId, '9223372036854775807');
});
test('designer new edits clear redo and route loading resets history without false dirty', async (t) => {
  const x = mount(designer, 'A', { reportDetail: async (id) => ({ data: reportAt(id) }) });
  t.after(x.unmount);
  await flush();
  x.state.dsl.title = 'one';
  await nextTick();
  x.state.dsl.title = 'two';
  await nextTick();
  x.state.undo();
  await nextTick();
  x.state.dsl.title = 'new branch';
  await nextTick();
  assert.equal(x.state.canRedo, false);
  x.fixture.route.query.id = 'B';
  await flush();
  assert.equal(x.state.canUndo, false);
  assert.equal(x.state.canRedo, false);
  assert.equal(x.state.dirty, false);
});
test('undo reaches the saved snapshot after an edit made while saving and history is excluded from writes', async (t) => {
  const pending = defer();
  let sent;
  const x = mount(designer, 'A', {
    reportDetail: async (id) => ({ data: reportAt(id) }),
    saveReport: (id, revision, value) => {
      sent = value;
      return pending.promise;
    }
  });
  t.after(x.unmount);
  await flush();
  x.state.dsl.title = 'sent';
  await nextTick();
  const request = x.state.save();
  await flush();
  x.state.dsl.title = 'later edit';
  await nextTick();
  x.state.undo();
  assert.equal(x.state.dsl.title, 'later edit');
  pending.resolve({ data: { ...reportAt('A').definition, revision: 8 } });
  await request;
  x.state.undo();
  await nextTick();
  assert.equal(x.state.dsl.title, 'sent');
  assert.equal(x.state.dirty, false);
  assert.deepEqual(Object.keys(sent), Object.keys(dsl));
});
test('save conflict retains undo and redo while the server revision stays unchanged', async (t) => {
  const x = mount(designer, 'A', {
    reportDetail: async (id) => ({ data: reportAt(id) }),
    saveReport: async () => {
      throw { response: { status: 409 } };
    }
  });
  t.after(x.unmount);
  await flush();
  x.state.dsl.title = 'conflicting edit';
  await nextTick();
  assert.equal(await x.state.save(), false);
  assert.equal(x.state.definition.revision, 7);
  x.state.undo();
  await nextTick();
  assert.equal(x.state.dirty, false);
  x.state.redo();
  await nextTick();
  assert.equal(x.state.dsl.title, 'conflicting edit');
});
test('applied JSON participates in history and undo refreshes the JSON editor without false dirty', async (t) => {
  const x = mount(designer, 'A', { reportDetail: async (id) => ({ data: reportAt(id) }) });
  t.after(x.unmount);
  await flush();
  x.state.tab = 'json';
  await nextTick();
  x.state.jsonText = JSON.stringify({ ...structuredClone(dsl), title: 'JSON edit' });
  await x.state.applyJson();
  await nextTick();
  x.state.undo();
  await nextTick();
  assert.equal(x.state.dsl.title, 'A');
  assert.equal(JSON.parse(x.state.jsonText).title, 'A');
  assert.equal(x.state.dirty, false);
  x.state.redo();
  await nextTick();
  assert.equal(x.state.dsl.title, 'JSON edit');
  assert.equal(JSON.parse(x.state.jsonText).title, 'JSON edit');
  assert.equal(x.state.jsonDirty, false);
});

test('creating a report resets history that could restore a now-immutable kind or code', async (t) => {
  const source = {
    dataset: { id: 'dataset', code: 'TEST', publishedVersion: '9223372036854775807', status: 'ACTIVE' },
    descriptor: {
      code: 'TEST',
      name: 'new',
      document: false,
      fields: [{ name: 'id', label: 'ID', type: 'ID' }],
      defaultColumns: ['id'],
      parameters: []
    }
  };
  const x = mount(designer, '', {
    listDatasets: async () => ({ data: [source] }),
    createReport: async (value) => ({ data: { ...detail().definition, id: 'created', code: value.code, kind: value.kind } })
  });
  t.after(x.unmount);
  await flush();
  x.state.chooseDataset('dataset');
  x.state.dsl.kind = 'DOCUMENT';
  await nextTick();
  assert.equal(await x.state.save(), true);
  await nextTick();
  assert.equal(x.state.canUndo, false);
  assert.equal(x.state.dirty, false);
  x.state.dsl.title = 'edit after create';
  await nextTick();
  x.state.undo();
  await nextTick();
  assert.equal(x.state.dsl.kind, 'DOCUMENT');
  assert.equal(x.state.dirty, false);
});
test('late export enqueue does not announce success for a different report', async (t) => {
  const pending = defer();
  const x = mount(viewer, 'A', { enqueueExport: () => pending.promise });
  t.after(x.unmount);
  await flush();
  x.state.run = { id: 'run-A', mode: 'SNAPSHOT' };
  const request = x.state.exportReport('PDF');
  x.fixture.route.query.id = 'B';
  await flush();
  pending.resolve({ data: { id: 'job-A', state: 'QUEUED' } });
  await request;
  assert.equal(x.fixture.messages.length, 0);
  assert.equal(x.state.exporting, false);
});

test('contract control uses the pinned version and discards a late old-version response', async (t) => {
  const old = defer();
  const next = defer();
  const x = mount(
    contract,
    'A',
    { datasetVersion: (id) => (id === 'old' ? old.promise : next.promise) },
    {
      modelValue: { ...structuredClone(dsl), datasetVersionId: 'old' },
      datasets: [{ dataset: { code: 'TEST', publishedVersion: 'new' }, descriptor: { version: 2 } }],
      busy: false
    }
  );
  t.after(x.unmount);
  x.props.modelValue = { ...x.props.modelValue, datasetVersionId: 'new' };
  await nextTick();
  next.resolve({ data: { id: 'new', code: 'TEST', number: 2, descriptor: { fields: [] } } });
  await flush();
  old.resolve({ data: { id: 'old', code: 'TEST', number: 1, descriptor: { fields: [] } } });
  await flush();
  assert.equal(x.state.version.id, 'new');
  assert.equal(x.state.isCurrent, true);
});
test('contract upgrade does not apply a confirmation to a changed draft', async (t) => {
  const confirmation = defer();
  const updates = [];
  const x = mount(
    contract,
    'A',
    {},
    {
      modelValue: { ...structuredClone(dsl), datasetVersionId: 'old' },
      datasets: [{ dataset: { code: 'TEST', publishedVersion: 'new' }, descriptor: { version: 2 } }],
      busy: false,
      'onUpdate:modelValue': (value) => updates.push(value)
    }
  );
  t.after(x.unmount);
  await flush();
  x.fixture.confirm = () => confirmation.promise;
  const changing = x.state.upgrade();
  await flush();
  x.props.modelValue = { ...x.props.modelValue, title: 'new edit' };
  confirmation.resolve(true);
  await changing;
  assert.equal(updates.length, 0);
});
test('remote lookup discards results from an older search and keeps IDs exact', async (t) => {
  const first = defer();
  const last = defer();
  const x = mount(
    lookup,
    'A',
    { reportLookup: (v, p, k) => (k === 'first' ? first.promise : last.promise) },
    { modelValue: '', version: 'v', parameter: 'orderId', label: '单据' }
  );
  t.after(x.unmount);
  const a = x.state.search('first');
  const b = x.state.search('last');
  last.resolve({ data: { options: [{ value: '9223372036854775807', label: '合成' }], total: 1 } });
  await b;
  first.resolve({ data: { options: [{ value: 'old', label: 'old' }], total: 1 } });
  await a;
  assert.equal(x.state.options[0].value, '9223372036854775807');
});
test('business entry transfers the exact document ID and only the approved parameter', async (t) => {
  const x = mount(business, 'A', {}, { datasetCode: 'ERP_PURCHASE_ORDER_DOCUMENT', parameter: 'orderId', recordId: '9223372036854775807' });
  t.after(x.unmount);
  let route;
  x.fixture.router.push = (value) => {
    route = value;
  };
  x.state.open('report1');
  assert.equal(route.query.orderId, '9223372036854775807');
  assert.deepEqual(Object.keys(route.query).sort(), ['id', 'orderId']);
});
test('manual retry sends the displayed round and keeps the original job identity', async (t) => {
  let request;
  const x = mount(exportsPage, 'A', {
    retryExport: async (...args) => {
      request = args;
      return { data: null };
    }
  });
  t.after(x.unmount);
  await flush();
  await x.state.retry({ id: 'job1', retryRound: 1 });
  assert.deepEqual(request, ['job1', 1]);
});

test('lookup popup visibility changes cannot restart the initial query or clear loaded options', async (t) => {
  const pending = defer();
  let calls = 0;
  const x = mount(
    lookup,
    'A',
    {
      reportLookup: () => {
        calls++;
        return pending.promise;
      }
    },
    { modelValue: '', version: 'v', parameter: 'orderId', label: '单据' }
  );
  t.after(x.unmount);
  const opening = x.state.opened(true);
  await x.state.opened(true);
  assert.equal(calls, 1);
  pending.resolve({ data: { options: [{ value: '9223372036854775807', label: '合成' }], total: 1 } });
  await opening;
  await x.state.opened(false);
  await x.state.opened(true);
  assert.equal(calls, 1);
  assert.equal(x.state.options[0].value, '9223372036854775807');
  assert.equal(x.state.loading, false);
});

test('late failure of an old lookup page cannot roll back the new search page', async (t) => {
  const pending = defer();
  const calls = [];
  const result = { data: { options: [{ value: '9223372036854775807', label: '合成' }], total: 40 } };
  const x = mount(
    lookup,
    'A',
    {
      reportLookup: (version, parameter, keyword, page) => {
        calls.push({ keyword, page });
        return keyword === 'old' && page === 2 ? pending.promise : Promise.resolve(result);
      }
    },
    { modelValue: '', version: 'v', parameter: 'orderId', label: '单据' }
  );
  t.after(x.unmount);
  await x.state.search('old');
  const oldPage = x.state.more();
  await x.state.search('new');
  pending.reject(new Error('late old page'));
  await oldPage;
  await x.state.more();
  assert.deepEqual(calls.at(-1), { keyword: 'new', page: 2 });
});

test('closing and reopening import discards the previous session response without disturbing the new submission', async (t) => {
  const first = defer();
  const second = defer();
  const imported = [];
  const x = mount(
    templateDialog,
    'A',
    { importTemplatePackage: (input) => (input.code === 'A' ? first.promise : second.promise) },
    {
      modelValue: true,
      datasets: [],
      'onUpdate:modelValue': (value) => {
        x.props.modelValue = value;
      },
      onImported: (id) => imported.push(id)
    }
  );
  t.after(x.unmount);
  x.state.pack = { synthetic: true };
  x.state.version = 'v';
  x.state.code = 'A';
  x.state.title = 'A';
  const old = x.state.submit();
  x.props.modelValue = false;
  await nextTick();
  x.props.modelValue = true;
  await nextTick();
  x.state.pack = { synthetic: true };
  x.state.version = 'v';
  x.state.code = 'B';
  x.state.title = 'B';
  const current = x.state.submit();
  first.resolve({ data: { id: 'old-created' } });
  await old;
  assert.equal(x.props.modelValue, true);
  assert.deepEqual(imported, []);
  assert.equal(x.state.saving, true);
  assert.equal(x.state.code, 'B');
  second.resolve({ data: { id: 'new-created' } });
  await current;
  assert.deepEqual(imported, ['new-created']);
  assert.equal(x.props.modelValue, false);
});

test('late template file read cannot restore a package into a reopened import session', async (t) => {
  const pending = defer();
  const x = mount(templateDialog, 'A', {}, { modelValue: true, datasets: [] });
  t.after(x.unmount);
  const reading = x.state.read({ target: { files: [{ size: 30, text: () => pending.promise }] } });
  x.props.modelValue = false;
  await nextTick();
  x.props.modelValue = true;
  await nextTick();
  x.state.pack = { current: true };
  pending.resolve('{"old":true}');
  await reading;
  assert.deepEqual(x.state.pack, { current: true });
});

test('late package export for another editor context cannot download under the new report code', async (t) => {
  const pending = defer();
  const x = mount(designer, 'A', { reportDetail: async (id) => ({ data: reportAt(id) }), exportTemplatePackage: () => pending.promise });
  t.after(x.unmount);
  await flush();
  const exporting = x.state.exportPackage();
  x.fixture.route.query.id = 'B';
  await flush();
  pending.resolve({ data: { source: 'A' } });
  await exporting;
  assert.equal(x.fixture.downloads.length, 0);
});

test('late retry after leaving and reentering exports cannot refresh or announce an old action', async (t) => {
  const pending = defer();
  let reads = 0;
  const x = mount(exportsPage, 'A', {
    retryExport: () => pending.promise,
    listExportJobs: async () => {
      reads++;
      return { data: [] };
    }
  });
  t.after(x.unmount);
  await flush();
  const retrying = x.state.retry({ id: 'job1', retryRound: 0 });
  x.state.stop();
  x.state.start();
  await flush();
  const currentReads = reads;
  pending.resolve({ data: null });
  await retrying;
  assert.equal(reads, currentReads);
  assert.deepEqual(x.fixture.messages, []);
});

test('cancelled contract upgrade cannot discard a still-loading historical descriptor', async (t) => {
  const pending = defer();
  const x = mount(
    contract,
    'A',
    {
      datasetVersion: (id) =>
        id === 'old' ? pending.promise : Promise.resolve({ data: { id, code: 'TEST', number: 2, descriptor: { parameters: [], fields: [] } } })
    },
    {
      modelValue: { ...structuredClone(dsl), datasetVersionId: 'old' },
      datasets: [{ dataset: { code: 'TEST', publishedVersion: 'new' } }],
      busy: false
    }
  );
  t.after(x.unmount);
  x.fixture.confirm = async () => {
    throw new Error('cancelled');
  };
  await x.state.upgrade();
  pending.resolve({ data: { id: 'old', code: 'TEST', number: 1, descriptor: { parameters: [], fields: [] } } });
  await flush();
  assert.equal(x.state.version?.id, 'old');
});

test('changing a declared business parameter on the same viewer route discards the previous document', async (t) => {
  const x = mount(viewer, 'A', {
    publishedReport: async () => ({
      data: {
        versionId: 'v',
        number: 1,
        dsl: { ...structuredClone(dsl), parameters: [{ name: 'orderId', label: '单据', type: 'ID', required: true }] }
      }
    })
  });
  t.after(x.unmount);
  x.fixture.route.query.orderId = '9223372036854775807';
  await flush();
  assert.equal(x.state.parameters.orderId, '9223372036854775807');
  x.state.run = { id: 'previous' };
  x.fixture.route.query.orderId = '9223372036854775806';
  await flush();
  assert.equal(x.state.parameters.orderId, '9223372036854775806');
  assert.equal(x.state.run, undefined);
});

test('copy confirmation completed after unmount cannot create another draft', async () => {
  const confirmation = defer();
  let writes = 0;
  const x = mount(center, 'A', {
    copyReport: async () => {
      writes++;
      return { data: { id: 'copy' } };
    }
  });
  x.fixture.prompt = () => confirmation.promise;
  const copying = x.state.duplicate({ id: 'A', name: 'A', catalogId: null });
  x.unmount();
  confirmation.resolve({ value: 'COPY_A' });
  await copying;
  assert.equal(writes, 0);
});

test('copy response completed after unmount cannot force navigation', async () => {
  const pending = defer();
  const navigation = [];
  const x = mount(center, 'A', { copyReport: () => pending.promise });
  x.fixture.router.push = (route) => navigation.push(route);
  const copying = x.state.duplicate({ id: 'A', name: 'A', catalogId: null });
  await flush();
  x.unmount();
  pending.resolve({ data: { id: 'old-copy' } });
  await copying;
  assert.deepEqual(navigation, []);
});

test('batch selection follows field identity when columns are reordered', async (t) => {
  const first = { ...structuredClone(dsl.columns[0]), field: 'a', label: 'A' };
  const second = { ...structuredClone(dsl.columns[0]), field: 'b', label: 'B' };
  const updates = [];
  const x = mount(
    batch,
    'A',
    {},
    { modelValue: { ...structuredClone(dsl), columns: [first, second] }, disabled: false, 'onUpdate:modelValue': (value) => updates.push(value) }
  );
  t.after(x.unmount);
  x.state.selected = ['a'];
  x.props.modelValue = { ...x.props.modelValue, columns: [second, first] };
  await nextTick();
  x.state.style({ bold: true });
  assert.equal(updates.length, 1);
  assert.equal(updates[0].columns.find((c) => c.field === 'a').style.bold, true);
  assert.equal(updates[0].columns.find((c) => c.field === 'b').style.bold, false);
});

test('confirmed contract upgrade exposes newly required parameters in the normal editor model', async (t) => {
  const updates = [];
  const required = { name: 'orderId', label: '单据', type: 'ID', required: true, defaultValue: null };
  const x = mount(
    contract,
    'A',
    {
      datasetVersion: async (id) => ({
        data: { id, code: 'TEST', number: id === 'new' ? 2 : 1, descriptor: { fields: [], parameters: id === 'new' ? [required] : [] } }
      })
    },
    {
      modelValue: { ...structuredClone(dsl), datasetVersionId: 'old' },
      datasets: [{ dataset: { code: 'TEST', publishedVersion: 'new' } }],
      busy: false,
      'onUpdate:modelValue': (value) => updates.push(value)
    }
  );
  t.after(x.unmount);
  await flush();
  await x.state.upgrade();
  assert.equal(updates.length, 1);
  assert.equal(updates[0].datasetVersionId, 'new');
  assert.deepEqual(updates[0].parameters, [required]);
});
