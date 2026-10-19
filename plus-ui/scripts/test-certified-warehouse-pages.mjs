/** 真实通用 SFC script handlers 执行；独立服务端保护事实不能被编辑 form 覆盖。 */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import ts from 'typescript';
import axios from 'axios';
import * as Vue from 'vue';
import * as sfc from '@vue/compiler-sfc';
const ID = '9007199254740993';
function evaluate(source, require) {
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const exports = {};
  new Function('exports', 'require', code)(exports, require);
  return exports;
}
async function tick() {
  for (let i = 0; i < 8; i++) {
    await Vue.nextTick();
    await new Promise((done) => setImmediate(done));
  }
}
async function mount(path, domain, protection = 'CERTIFIED', options = {}) {
  const log = [];
  const route = Vue.reactive({
    fullPath: `/${domain}/form?id=${ID}`,
    path: `/${domain}/form`,
    query: { id: ID, receiveId: ID, lineId: '11', action: 'edit' }
  });
  const user = Vue.reactive({ userId: '101', tenantId: '000001' });
  let token = 'offline-page-token';
  const native = {
    [domain === 'arrivalnotice' ? 'noticeId' : 'receiveId']: ID,
    status: 'DRAFT',
    attr1: '',
    selectFlowId: 'flow',
    wmArrivalNoticeLineList: [],
    wmMaterialReceiveLineList: [],
    receiveId: ID,
    lineId: '11',
    detailId: '12'
  };
  const detail = {
    domain,
    kind: domain === 'arrivalnotice' ? 'NOTICE' : 'RECEIVE',
    documentId: ID,
    protection,
    sourceComplete: false,
    flowState: 'UNKNOWN',
    native,
    order: null,
    orderLines: [],
    lines: [],
    details: []
  };
  const apis = new Proxy(
    {},
    {
      get(target, name) {
        if (String(name).startsWith('getCertified'))
          return async () => {
            log.push(['read', name]);
            return { code: 200, data: String(name).endsWith('Context') ? { domain, tenantId: '000001', userId: '101' } : structuredClone(detail) };
          };
        if (String(name).startsWith('list')) return async () => ({ code: 200, data: [], rows: [], total: 0 });
        if (String(name).startsWith('get')) return async () => ({ code: 200, data: structuredClone(native) });
        return async (...args) => {
          log.push(['write', name, args]);
          return options.write ? options.write(name, args) : { code: 200 };
        };
      }
    }
  );
  // detail API 与 Axios transform/adapter 是真实代码；adapter 只返回隔离原生 DTO，从不联网。
  const request = axios.create({
    adapter: async (config) => {
      let body;
      if (config.method === 'get') {
        body = config.url.endsWith('/list') ? { code: 200, rows: [], total: 0 } : { code: 200, data: structuredClone(native) };
      } else {
        const name = config.method === 'put' ? 'updateDetail' : config.method === 'post' ? 'addDetail' : 'delDetail';
        const args = config.method === 'delete' ? [config.url.split('/').at(-1)] : [JSON.parse(config.data)];
        log.push(['write', name, args]);
        body = options.write ? await options.write(name, args) : { code: 200 };
      }
      return { status: 200, statusText: 'OK', headers: {}, config, data: JSON.stringify(body) };
    }
  });
  request.interceptors.request.use(async (config) => {
    if (config.url === '/erp/wm/detail' && ['post', 'put'].includes(config.method)) await options.beforeDispatch?.(config);
    return config;
  });
  request.interceptors.response.use((response) => response.data);
  const detailApi = evaluate(readFileSync(new URL('../src/api/erp/wm/detail/index.ts', import.meta.url), 'utf8'), (name) => {
    assert.equal(name, '@/utils/request');
    return { default: request };
  });
  const helper = evaluate(readFileSync(new URL('../src/utils/certified-business.ts', import.meta.url), 'utf8'), (name) => {
    assert.equal(name, '@/utils/request');
    return { default: request };
  });
  const workflow = evaluate(readFileSync(new URL('../src/views/erp/wm/certified/warehouseWorkflow.ts', import.meta.url), 'utf8'), (name) => {
    assert.equal(name, '@/utils/certified-business');
    return helper;
  });
  const { descriptor, errors } = sfc.parse(readFileSync(new URL(path, import.meta.url), 'utf8'));
  assert.deepEqual(errors, []);
  const compiled = sfc.compileScript(descriptor, { id: `warehouse-${domain}` });
  const vueNames = [
    'ref',
    'shallowRef',
    'reactive',
    'computed',
    'watch',
    'onMounted',
    'onActivated',
    'onBeforeUnmount',
    'toRefs',
    'getCurrentInstance',
    'nextTick'
  ];
  const header = `import { ${vueNames.filter((name) => !Object.hasOwn(compiled.bindings, name)).join(',')} } from 'vue';\n`;
  /** 执行真实追溯纯逻辑，新增解析与命令依赖仍不访问网络。 */
  const traceModel = evaluate(readFileSync(new URL('../src/components/material-trace/model.ts', import.meta.url), 'utf8'), (name) =>
    assert.fail(`追溯纯逻辑不应加载运行时依赖 ${name}`)
  );
  const traceSubmission = evaluate(readFileSync(new URL('../src/components/material-trace/submission.ts', import.meta.url), 'utf8'), (name) => {
    assert.equal(name, './model');
    return traceModel;
  });
  const modules = {
    '@/components/material-trace/submission': traceSubmission,
    '@/components/material-trace/model': traceModel,
    vue: Vue,
    'vue-router': { useRoute: () => route, useRouter: () => ({ push: (...args) => log.push(['route', ...args]) }) },
    '@/utils/auth': { getToken: () => token },
    '@/utils/request': { default: request },
    '@/api/erp/wm/detail': detailApi,
    '@/store/modules/user': { useUserStore: () => user },
    '@/plugins/auth': { default: { hasPermi: () => true } },
    '@/utils/certified-business': helper,
    '@/views/erp/wm/certified/warehouseWorkflow': workflow,
    '../certified/warehouseWorkflow': workflow
  };
  const crud = () => ({
    loading: Vue.ref(false),
    dataList: Vue.ref([]),
    list: Vue.ref([]),
    total: Vue.ref(0),
    open: Vue.ref(false),
    title: Vue.ref(''),
    ids: Vue.ref([]),
    single: Vue.ref(true),
    multiple: Vue.ref(true),
    showSearch: Vue.ref(true),
    form: Vue.ref({}),
    getList() {},
    handleQuery() {},
    handleAdd() {},
    submitForm() {
      log.push(['write', 'crudSubmit']);
    },
    cancel() {},
    handleDelete() {
      log.push(['write', 'crudDelete']);
    },
    handleExport() {},
    handleSelectionChange() {}
  });
  const component = evaluate(header + compiled.content, (name) => {
    if (Object.hasOwn(modules, name)) return modules[name];
    if (name.endsWith('.vue')) return { default: {} };
    if (name.includes('useCrud')) return { useCrud: crud };
    assert.ok(name.startsWith('@/api/'), `未隔离依赖 ${name}`);
    return apis;
  }).default;
  component.render = () => Vue.h('div');
  const renderer = Vue.createRenderer({
    createElement: () => ({}),
    createText: () => ({}),
    createComment: () => ({}),
    setText() {},
    setElementText() {},
    parentNode: () => null,
    nextSibling: () => null,
    insert() {},
    remove() {},
    patchProp() {}
  });
  const app = renderer.createApp(component, { disabled: false, customFormData: {} });
  app.config.warnHandler = () => {};
  Object.assign(app.config.globalProperties, {
    useDict: () => Vue.reactive({}),
    resetForm() {},
    $auth: { hasPermi: () => true },
    $modal: { confirm: async () => true, msgWarning: (m) => log.push(['warning', m]), msgError() {}, msgSuccess() {} },
    $tab: { closeOpenPage() {} }
  });
  const vm = app.mount({});
  const setup = vm.$.setupState;
  await tick();
  // 测试真正 validate 回调，不用禁用按钮掩盖可直接调用 handler 的漏洞。
  const formRef = { validate: options.validate ?? ((done) => done(true)) };
  vm.$.refs = { arrivalnoticeRef: formRef, receiveRef: formRef, detailRef: formRef, shelfRef: formRef };
  return { setup, log, native, route, user, app, request, setToken: (value) => (token = value) };
}
for (const [domain, idField] of [
  ['arrivalnotice', 'noticeId'],
  ['receive', 'receiveId']
]) {
  test(`${domain} 通用真实表单：认证/UNKNOWN 不能通过改 attr/status/ID 解保护`, async () => {
    for (const protection of ['CERTIFIED', 'UNKNOWN']) {
      const f = await mount(`../src/views/erp/wm/${domain}/form.vue`, domain, protection);
      Object.assign(f.setup.form, f.native, { attr1: '', status: 'DRAFT', [idField]: ID });
      if (f.setup.wmArrivalNoticeLineList) f.setup.wmArrivalNoticeLineList = [];
      if (f.setup.wmMaterialReceiveLineList) f.setup.wmMaterialReceiveLineList = [];
      f.log.length = 0;
      await f.setup.submitForm();
      await tick();
      assert.equal(
        f.log.some((x) => x[0] === 'write'),
        false,
        `${domain} ${protection} 旧保存必须封闭`
      );
      if (f.setup.handleSubmitFlow) {
        await f.setup.handleSubmitFlow();
        await tick();
        assert.equal(
          f.log.some((x) => x[0] === 'write'),
          false
        );
      }
      f.app.unmount();
    }
  });
  test(`${domain} 通用真实列表：认证或未知单禁止旧编辑/删除路由及写`, async () => {
    const f = await mount(`../src/views/erp/wm/${domain}/index.vue`, domain);
    f.log.length = 0;
    await f.setup.handleUpdate({ ...f.native, [idField]: ID });
    await tick();
    assert.equal(
      f.log.some((x) => x[0] === 'route'),
      false
    );
    await f.setup.handleDelete({ ...f.native, [idField]: ID });
    await tick();
    assert.equal(
      f.log.some((x) => x[0] === 'write'),
      false
    );
    f.app.unmount();
  });
}
test('独立收货明细真实新增/保存/删除入口认证或未知父归属封闭', async () => {
  const f = await mount('../src/views/erp/wm/receive/detail.vue', 'receive');
  Object.assign(f.setup.form, f.native);
  f.log.length = 0;
  await f.setup.submitForm();
  await tick();
  await f.setup.handleDelete(f.native);
  await tick();
  assert.equal(
    f.log.some((x) => x[0] === 'write'),
    false
  );
  f.app.unmount();
});

for (const domain of ['arrivalnotice', 'receive'])
  test(`${domain} 明确 LEGACY 普通表单路径保留`, async () => {
    const f = await mount(`../src/views/erp/wm/${domain}/form.vue`, domain, 'LEGACY');
    Object.assign(f.setup.form, f.native);
    f.log.length = 0;
    await f.setup.submitForm();
    await tick();
    assert.equal(f.log.filter((x) => x[0] === 'write' && String(x[1]).startsWith('update')).length, 1);
    f.app.unmount();
  });

test('普通明确 LEGACY update→submit 的迟到 ack 不跨原 ID/路由/账号发送第二次 POST，body快照独立', async () => {
  for (const change of [
    (f) => {
      f.setup.form.receiveId = '99';
    },
    (f) => {
      f.route.fullPath = '/receive/form?id=99';
      f.route.query.id = '99';
    },
    (f) => {
      f.user.userId = '999';
    }
  ]) {
    let resolve;
    const f = await mount('../src/views/erp/wm/receive/form.vue', 'receive', 'LEGACY', {
      write: (name) => (String(name).startsWith('update') ? new Promise((done) => (resolve = done)) : { code: 200 })
    });
    await f.setup.handleSubmitFlow();
    await tick();
    assert.equal(typeof resolve, 'function');
    const first = f.log.find((x) => x[0] === 'write' && String(x[1]).startsWith('update'));
    change(f);
    resolve({ code: 200 });
    await tick();
    assert.equal(
      f.log.some((x) => x[0] === 'write' && x[1] === 'submitReceive'),
      false
    );
    assert.equal(first[2][0].receiveId, ID);
    f.app.unmount();
  }
});

test('明确 LEGACY 独立明细编辑保留，父身份由真实只读 getter 绑定', async () => {
  const f = await mount('../src/views/erp/wm/receive/detail.vue', 'receive', 'LEGACY');
  await f.setup.handleUpdate({ detailId: '12' });
  await tick();
  assert.equal(f.setup.form.receiveId, ID);
  f.log.length = 0;
  await f.setup.submitForm();
  await tick();
  assert.equal(f.log.filter((x) => x[0] === 'write' && x[1] === 'updateDetail').length, 1);
  f.app.unmount();
});

for (const [label, mutate] of [
  [
    'editable receiveId',
    (f) => {
      f.setup.form.receiveId = '9007199254740995';
    }
  ],
  [
    'editable detailId',
    (f) => {
      f.setup.form.detailId = '13';
    }
  ],
  [
    'editable lineId',
    (f) => {
      f.setup.form.lineId = '14';
    }
  ],
  [
    'native parent',
    (f) => {
      f.setup.nativeParentId = '9007199254740995';
    }
  ],
  [
    'native detail',
    (f) => {
      f.setup.nativeDetailId = '13';
    }
  ],
  [
    'selection',
    (f) => {
      f.setup.ids = ['13'];
    }
  ],
  [
    'receiveId A→B→A',
    (f) => {
      f.setup.form.receiveId = '9007199254740995';
      f.setup.form.receiveId = ID;
    }
  ],
  [
    'detailId A→B→A',
    (f) => {
      f.setup.form.detailId = '13';
      f.setup.form.detailId = '12';
    }
  ],
  [
    'lineId A→B→A',
    (f) => {
      f.setup.form.lineId = '14';
      f.setup.form.lineId = '11';
    }
  ]
])
  test(`review LEGACY A delayed validate：${label} 改变取消，不能写未查询 B`, async (t) => {
    let validated;
    const f = await mount('../src/views/erp/wm/receive/detail.vue', 'receive', 'LEGACY', {
      validate: (done) => {
        validated = done;
      }
    });
    t.after(() => f.app.unmount());
    await f.setup.handleUpdate({ detailId: '12' });
    await tick();
    assert.equal(f.setup.form.receiveId, ID);
    assert.equal(f.setup.form.lineId, '11');
    f.log.length = 0;
    await f.setup.submitForm();
    assert.equal(typeof validated, 'function');
    mutate(f);
    assert.doesNotThrow(() => validated(true), '过期 validate 应封闭而非抛出未处理会话异常');
    await tick();
    assert.equal(f.log.filter((x) => x[0] === 'write').length, 0);
  });

for (const [label, mutate] of [
  [
    'receiveId',
    (f) => {
      f.setup.form.receiveId = '9007199254740995';
    }
  ],
  [
    'detailId',
    (f) => {
      f.setup.form.detailId = '13';
    }
  ],
  [
    'lineId',
    (f) => {
      f.setup.form.lineId = '14';
    }
  ],
  [
    'route',
    (f) => {
      f.route.fullPath = '/receive/detail?id=13';
    }
  ],
  [
    'user',
    (f) => {
      f.user.userId = '102';
    }
  ],
  [
    'tenant',
    (f) => {
      f.user.tenantId = '000002';
    }
  ],
  [
    'token',
    (f) => {
      f.setToken('offline-other-token');
    }
  ]
])
  test(`review LEGACY A Axios 已分配、adapter 未派发：${label} 改变也必须取消`, async (t) => {
    let dispatch;
    const f = await mount('../src/views/erp/wm/receive/detail.vue', 'receive', 'LEGACY', {
      beforeDispatch: () =>
        new Promise((done) => {
          dispatch = done;
        })
    });
    t.after(() => f.app.unmount());
    await f.setup.handleUpdate({ detailId: '12' });
    await tick();
    f.log.length = 0;
    await f.setup.submitForm();
    await tick();
    assert.equal(typeof dispatch, 'function', '真实 Axios request interceptor 暂停派发');
    mutate(f);
    dispatch();
    await tick();
    assert.equal(f.log.filter((x) => x[0] === 'write').length, 0, 'transform 后不能让 adapter 收到旧 scope 的写');
  });

test('review LEGACY A delayed validate 正常对照：固定 parent/detail/line，合法普通单仍 update 一次', async (t) => {
  let validated;
  const f = await mount('../src/views/erp/wm/receive/detail.vue', 'receive', 'LEGACY', {
    validate: (done) => {
      validated = done;
    }
  });
  t.after(() => f.app.unmount());
  await f.setup.handleUpdate({ detailId: '12' });
  await tick();
  f.setup.form.quantity = '2.0000';
  f.setup.form.remark = '人工普通单资料';
  f.log.length = 0;
  await f.setup.submitForm();
  assert.equal(typeof validated, 'function');
  validated(true);
  await tick();
  const writes = f.log.filter((x) => x[0] === 'write');
  assert.equal(writes.length, 1);
  assert.equal(writes[0][1], 'updateDetail');
  assert.equal(writes[0][2][0].receiveId, ID);
  assert.equal(writes[0][2][0].detailId, '12');
  assert.equal(writes[0][2][0].lineId, '11');
  assert.equal(writes[0][2][0].quantity, '2.0000');
});

test('review LEGACY A body 整体冻结，不只是新原 ID 字段：validate 期间量/备注编辑不替换已捕获 payload', async (t) => {
  let validated;
  const f = await mount('../src/views/erp/wm/receive/detail.vue', 'receive', 'LEGACY', {
    validate: (done) => {
      validated = done;
    }
  });
  t.after(() => f.app.unmount());
  await f.setup.handleUpdate({ detailId: '12' });
  await tick();
  Object.assign(f.setup.form, { quantity: '2.0000', remark: '原人工内容', batchId: '25', warehouseId: '26', areaId: '27', locationId: '28' });
  const original = JSON.parse(JSON.stringify(f.setup.form));
  f.log.length = 0;
  await f.setup.submitForm();
  assert.equal(typeof validated, 'function');
  Object.assign(f.setup.form, { quantity: '9.0000', remark: '迟到新内容', batchId: '29', locationId: '30' });
  validated(true);
  await tick();
  const writes = f.log.filter((x) => x[0] === 'write');
  assert.equal(writes.length, 1);
  assert.deepEqual(writes[0][2][0], original);
  f.setup.form.quantity = '10.0000';
  f.setup.form.remark = '再次修改';
  assert.deepEqual(writes[0][2][0], original);
});

test('review LEGACY A 原生 line=11，提交前改为未读取 line=14 也不保存', async (t) => {
  const f = await mount('../src/views/erp/wm/receive/detail.vue', 'receive', 'LEGACY');
  t.after(() => f.app.unmount());
  await f.setup.handleUpdate({ detailId: '12' });
  await tick();
  f.setup.form.lineId = '14';
  f.log.length = 0;
  await f.setup.submitForm();
  await tick();
  assert.equal(f.log.filter((x) => x[0] === 'write').length, 0);
});

test('review LEGACY A 新增正常对照：人工量、明确父/行的普通明细仍 add 一次', async (t) => {
  const f = await mount('../src/views/erp/wm/receive/detail.vue', 'receive', 'LEGACY');
  t.after(() => f.app.unmount());
  await f.setup.handleAddFromReceive();
  await tick();
  Object.assign(f.setup.form, { quantity: '2.0000', materialId: '3', baseUomId: '4' });
  f.log.length = 0;
  await f.setup.submitForm();
  await tick();
  const writes = f.log.filter((x) => x[0] === 'write');
  assert.equal(writes.length, 1);
  assert.equal(writes[0][1], 'addDetail');
  assert.equal(writes[0][2][0].receiveId, ID);
  assert.equal(writes[0][2][0].lineId, '11');
  assert.equal(writes[0][2][0].quantity, '2.0000');
});

test('review LEGACY A 新增暂停 validate：receiveId 改为未 query B 时不能 add', async (t) => {
  let validated;
  const f = await mount('../src/views/erp/wm/receive/detail.vue', 'receive', 'LEGACY', {
    validate: (done) => {
      validated = done;
    }
  });
  t.after(() => f.app.unmount());
  await f.setup.handleAddFromReceive();
  await tick();
  Object.assign(f.setup.form, { quantity: '2.0000', materialId: '3', baseUomId: '4' });
  f.log.length = 0;
  await f.setup.submitForm();
  assert.equal(typeof validated, 'function');
  f.setup.form.receiveId = '9007199254740995';
  assert.doesNotThrow(() => validated(true));
  await tick();
  assert.equal(f.log.filter((x) => x[0] === 'write').length, 0);
});

test('review LEGACY A 分配时固定 Authorization：actual transform 前仍用同一已查询 A 的会话', async (t) => {
  let allocated;
  const f = await mount('../src/views/erp/wm/receive/detail.vue', 'receive', 'LEGACY', {
    beforeDispatch: (config) => {
      allocated = config;
    }
  });
  t.after(() => f.app.unmount());
  await f.setup.handleUpdate({ detailId: '12' });
  await tick();
  f.setup.form.quantity = '2.0000';
  f.log.length = 0;
  await f.setup.submitForm();
  await tick();
  assert.equal(allocated.headers.Authorization, 'Bearer offline-page-token');
  assert.equal(allocated.url, '/erp/wm/detail');
  assert.equal(allocated.method, 'put');
  assert.equal(f.log.filter((x) => x[0] === 'write').length, 1);
});
