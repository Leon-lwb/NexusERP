/**
 * 四个真实列表页面的生命周期回归：执行真实 Vue / KeepAlive / useCrud。
 * 仅隔离 API、Element Plus 弹窗和 router；不访问 HTTP 或数据库。
 * Run: node --test --test-concurrency=1 scripts/test-list-initialization.mjs
 */
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { build } from 'esbuild';
import { compileScript, parse } from '@vue/compiler-sfc';
import * as Vue from 'vue';

const require = createRequire(import.meta.url);
const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const pages = [
  { name: 'ERP生产工单', file: 'erp/prd/prdwork', listApi: 'listWork', listState: 'workList', id: 'woId' },
  { name: '工艺路线', file: 'erp/bd/routing', listApi: 'listRouting', listState: 'list', id: 'routingId' },
  { name: '物料需求', file: 'erp/prd/requirement', listApi: 'listRequirement', listState: 'requirementList', id: 'requirementId' },
  { name: 'MES工单', file: 'mes/pro/workorder', listApi: 'listWorkOrder', listState: 'workOrderList', id: 'mesWoId' }
];
const bundles = new Map();

async function loadPage(page, apis) {
  if (!bundles.has(page.file)) {
    bundles.set(
      page.file,
      build({
        absWorkingDir: uiRoot,
        entryPoints: [resolve(uiRoot, 'src/views', page.file, 'index.vue')],
        bundle: true,
        write: false,
        platform: 'node',
        format: 'cjs',
        packages: 'external',
        logLevel: 'silent',
        plugins: [
          {
            name: 'list-lifecycle-regression',
            setup(builder) {
              builder.onResolve({ filter: /^@\// }, ({ path }) =>
                /^@\/(api\/|utils\/request$)/.test(path) ? { path, external: true } : { path: resolve(uiRoot, 'src', path.slice(2) + '.ts') }
              );
              builder.onLoad({ filter: /\.vue$/ }, ({ path }) => {
                const { descriptor, errors } = parse(readFileSync(path, 'utf8'), { filename: path });
                assert.deepEqual(errors, [], '真实 SFC 必须解析成功');
                const script = compileScript(descriptor, { id: 'list-lifecycle-regression' });
                const autoImports = {
                  vue: ['ref', 'computed', 'toRefs', 'getCurrentInstance', 'onMounted', 'onActivated', 'onDeactivated'],
                  'vue-router': ['useRouter']
                };
                const header = Object.entries(autoImports)
                  .map(([module, names]) => {
                    const missing = names.filter((name) => !script.imports?.[name]);
                    return missing.length ? `import { ${missing.join(', ')} } from '${module}';` : '';
                  })
                  .join('\n');
                return { contents: header + '\n' + script.content, loader: 'ts', resolveDir: dirname(path) };
              });
            }
          }
        ]
      }).then((result) => result.outputFiles[0].text)
    );
  }
  const failWrite = () => {
    throw new Error('列表生命周期不得执行业务写操作');
  };
  const module = { exports: {} };
  new Function('require', 'module', 'exports', await bundles.get(page.file))(
    (name) => {
      if (name.startsWith('@/api/')) return apis;
      if (name === '@/utils/request') return { download: failWrite };
      if (name === 'element-plus') return { ElMessage: { error: failWrite }, ElMessageBox: { confirm: failWrite } };
      if (name === 'vue-router') return { useRouter: () => ({ push: failWrite }) };
      return require(name);
    },
    module,
    module.exports
  );
  const Page = module.exports.default;
  // 列表请求属于 setup/lifecycle；模板外观由真实浏览器复测。
  Page.render = () => Vue.h('list-page');
  return Page;
}

function node(tag, text = '') {
  return { tag, text, children: [], parent: null };
}
function renderer() {
  return Vue.createRenderer({
    createElement: (tag) => node(tag),
    createText: (text) => node('#text', text),
    createComment: (text) => node('#comment', text),
    setText: (element, text) => {
      element.text = text;
    },
    setElementText: (element, text) => {
      element.text = text;
      element.children = [];
    },
    patchProp() {},
    parentNode: (element) => element.parent,
    nextSibling: (element) => element.parent?.children[element.parent.children.indexOf(element) + 1] ?? null,
    insert(element, parent, anchor) {
      if (element.parent) element.parent.children.splice(element.parent.children.indexOf(element), 1);
      element.parent = parent;
      const index = anchor ? parent.children.indexOf(anchor) : -1;
      parent.children.splice(index < 0 ? parent.children.length : index, 0, element);
    },
    remove(element) {
      element.parent?.children.splice(element.parent.children.indexOf(element), 1);
      element.parent = null;
    }
  });
}
async function flush() {
  for (let index = 0; index < 8; index++) await Promise.resolve();
  await Vue.nextTick();
}
async function mount(page, cache) {
  const calls = [];
  let rows = [{ [page.id]: '9007199254740993', materialName: '本地回归物料' }];
  const apis = {
    [page.listApi]: async (query) => {
      calls.push(JSON.parse(JSON.stringify(query)));
      return { rows: rows.map((row) => ({ ...row })), total: rows.length };
    }
  };
  const Page = await loadPage(page, apis);
  const visible = Vue.ref(true);
  const Other = { name: 'OtherPage', render: () => Vue.h('other-page') };
  let instance;
  const child = () =>
    visible.value
      ? Vue.h(Page, {
          key: 'list',
          ref: (value) => {
            if (value) instance = value;
          }
        })
      : Vue.h(Other, { key: 'other' });
  const app = renderer().createApp({
    setup: () => () =>
      cache === 'none'
        ? child()
        : Vue.h(Vue.KeepAlive, { include: cache === 'matching' ? [Page.__name] : ['MenuRouteWithDifferentName'] }, { default: child })
  });
  Object.assign(app.config.globalProperties, {
    useDict: (...names) => Vue.reactive(Object.fromEntries(names.map((name) => [name, []]))),
    $modal: {},
    resetForm() {}
  });
  app.mount(node('root'));
  await flush();
  return {
    calls,
    unmount: () => app.unmount(),
    get state() {
      return instance.$.setupState;
    },
    setRows(value) {
      rows = value;
    },
    async activate(value) {
      visible.value = value;
      await flush();
    }
  };
}

for (const page of pages) {
  for (const cache of ['none', 'mismatched']) {
    test(`${page.name}: ${cache} 首次挂载自动加载，重新进入重新查询`, async (t) => {
      const fixture = await mount(page, cache);
      t.after(fixture.unmount);
      assert.equal(fixture.calls.length, 1, '未缓存页面也应在首次挂载发送一次列表请求');
      assert.equal(fixture.state[page.listState][0][page.id], '9007199254740993');
      assert.equal(fixture.state.total, 1);
      assert.equal(fixture.state.loading, false);
      await fixture.activate(false);
      fixture.setRows([{ [page.id]: '9007199254740995' }]);
      await fixture.activate(true);
      assert.equal(fixture.calls.length, 2, '非缓存页面重新挂载应重新查询');
      assert.equal(fixture.state[page.listState][0][page.id], '9007199254740995');
    });
  }
  test(`${page.name}: 缓存首次只加载一次，返回刷新并保留页码`, async (t) => {
    const fixture = await mount(page, 'matching');
    t.after(fixture.unmount);
    assert.equal(fixture.calls.length, 1, 'onMounted 和首次 onActivated 不得重复请求');
    assert.equal(fixture.state.total, 1);
    fixture.state.queryParams.pageNum = 3;
    await fixture.activate(false);
    fixture.setRows([{ [page.id]: '9007199254740997' }]);
    assert.equal(fixture.calls.length, 1, '停用期间不得自动查询');
    await fixture.activate(true);
    assert.equal(fixture.calls.length, 2, '从缓存返回应刷新一次');
    assert.equal(fixture.calls[1].pageNum, 3, '缓存返回保留筛选与分页');
    assert.equal(fixture.state[page.listState][0][page.id], '9007199254740997');
    await fixture.activate(false);
    await fixture.activate(true);
    assert.equal(fixture.calls.length, 3, '每次缓存返回都应刷新');
  });
}
