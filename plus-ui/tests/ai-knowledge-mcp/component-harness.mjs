import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { compileScript, parse } from '@vue/compiler-sfc';
import { computed, createRenderer, h, inject, nextTick, provide, reactive } from 'vue';

const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const require = createRequire(import.meta.url);
const bundles = new Map();
const baselineRef = process.env.AI_KNOWLEDGE_MCP_BASELINE_REF;
if (baselineRef && !/^[0-9a-f]{7,40}$/.test(baselineRef)) throw new Error('基线必须是明确的 Git 提交哈希');

function readPageSource(path) {
  if (!baselineRef) return readFileSync(path, 'utf8');
  // 可复验 RED 只读指定基线的页面 SFC，不回退或改写任何工作树源码。
  const source = `plus-ui/${relative(uiRoot, path).replaceAll('\\', '/')}`;
  return execFileSync('git', ['show', `${baselineRef}:${source}`], { cwd: uiRoot, encoding: 'utf8' });
}
const vueImports = 'import { computed, defineAsyncComponent, getCurrentInstance, onMounted, onUnmounted, reactive, ref, toRefs, watch } from "vue";';

// 只编译真实页面、API、通用 CRUD 和响应拦截器；网络与消息弹窗在边界替身中隔离。
export async function loadProductionModule(relativePath, overrides = {}) {
  const entry = resolve(uiRoot, relativePath);
  if (!bundles.has(entry)) {
    bundles.set(
      entry,
      build({
        absWorkingDir: uiRoot,
        entryPoints: [entry],
        bundle: true,
        write: false,
        format: 'cjs',
        platform: 'node',
        packages: 'external',
        logLevel: 'silent',
        define: { 'import.meta.env': '{}' },
        plugins: [
          {
            name: 'ai-knowledge-mcp-offline',
            setup(builder) {
              builder.onResolve({ filter: /^@\// }, ({ path }) => {
                if (/^@\/(api\/ai\/(knowledge|mcp|mcpTool)|composables\/useCrud|enums\/RespEnum|utils\/errorCode)$/.test(path)) {
                  return { path: resolve(uiRoot, 'src', path.slice(2) + '.ts') };
                }
                return { path, external: true };
              });
              builder.onLoad({ filter: /\.vue$/ }, ({ path }) => {
                if (path.endsWith('graph.vue')) return { contents: 'export default { render() { return null; } };', loader: 'js' };
                const { descriptor, errors } = parse(readPageSource(path), { filename: path });
                assert.deepEqual(errors, [], '组件必须真实编译成功');
                const compiled = compileScript(descriptor, { id: 'ai-knowledge-mcp-regression', inlineTemplate: true });
                return { contents: vueImports + '\n' + compiled.content, loader: 'ts', resolveDir: dirname(path) };
              });
            }
          }
        ]
      }).then((result) => result.outputFiles[0].text)
    );
  }
  const module = { exports: {} };
  new Function('require', 'module', 'exports', 'ElMessage', 'ElMessageBox', 'ElNotification', await bundles.get(entry))(
    (name) => (name in overrides ? overrides[name] : name.startsWith('@/') ? {} : require(name)),
    module,
    module.exports,
    overrides['element-plus']?.ElMessage,
    overrides['element-plus']?.ElMessageBox,
    { error: (options) => overrides['element-plus']?.ElMessage({ message: options.title, type: 'error' }) }
  );
  return module.exports;
}

export function deferred() {
  let resolvePromise;
  let rejectPromise;
  const promise = new Promise((resolve, reject) => {
    resolvePromise = resolve;
    rejectPromise = reject;
  });
  return { promise, resolve: resolvePromise, reject: rejectPromise };
}

export function http500() {
  return Object.assign(new Error('Request failed with status code 500'), { response: { status: 500 } });
}

export async function flush() {
  // 穿过真实请求拦截器、CRUD 和渲染队列，不使用定时等待或外部服务。
  for (let index = 0; index < 12; index++) await Promise.resolve();
  await nextTick();
}

export async function offlineApi() {
  const routes = new Map();
  const calls = [];
  const messages = [];
  const ElMessage = (value) => messages.push(typeof value === 'string' ? { type: 'info', message: value } : value);
  for (const type of ['success', 'error', 'warning']) ElMessage[type] = (message) => messages.push({ type, message });
  const element = { ElMessage, ElMessageBox: { confirm: async () => true } };
  let fulfilled;
  let rejected;
  const service = (config) => {
    calls.push(JSON.parse(JSON.stringify(config)));
    return Promise.resolve()
      .then(() => (routes.get(config.url) ?? (() => ({ code: 200, rows: [], total: 0, data: [] })))(config))
      .then(
        (data) => fulfilled({ data, headers: {}, request: {}, config: { ...config, headers: config.headers ?? {} } }),
        (error) => rejected(error)
      );
  };
  service.interceptors = {
    request: { use() {} },
    response: {
      use(onFulfilled, onRejected) {
        fulfilled = onFulfilled;
        rejected = onRejected;
      }
    }
  };
  const axios = { defaults: { headers: {} }, create: () => service };
  const requestModule = await loadProductionModule('src/utils/request.ts', { axios, 'element-plus': element });
  return { routes, calls, messages, element, requestModule };
}

function host(tag, text = '') {
  return { tag, text, style: {}, props: {}, children: [], parent: null };
}

function renderer() {
  return createRenderer({
    createElement: (tag) => host(tag),
    createText: (text) => host('#text', text),
    createComment: (text) => host('#comment', text),
    setText: (node, text) => (node.text = text),
    setElementText(node, text) {
      node.text = text;
      node.children = [];
    },
    patchProp: (node, key, _old, value) => (node.props[key] = value),
    insert(child, parent, anchor) {
      if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1);
      child.parent = parent;
      const index = anchor ? parent.children.indexOf(anchor) : -1;
      parent.children.splice(index < 0 ? parent.children.length : index, 0, child);
    },
    remove(child) {
      child.parent?.children.splice(child.parent.children.indexOf(child), 1);
      child.parent = null;
    },
    parentNode: (node) => node.parent,
    nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null
  });
}

export function find(root, tag) {
  return [...(root.tag === tag ? [root] : []), ...root.children.flatMap((node) => find(node, tag))];
}

export function text(root) {
  return (root.tag === '#comment' ? '' : root.text) + root.children.map(text).join('');
}

export async function mountPage(relativePath, configure = () => {}) {
  const api = await offlineApi();
  configure(api);
  const { default: Page } = await loadProductionModule(relativePath, {
    '@/utils/request': api.requestModule,
    '@/api/ai/model': { listModel: async () => ({ rows: [] }) },
    'element-plus': api.element
  });
  const root = host('root');
  const app = renderer().createApp(Page);
  app.config.globalProperties.useDict = (...names) => reactive(Object.fromEntries(names.map((name) => [name, []])));
  app.config.globalProperties.parseTime = () => '';
  app.config.globalProperties.$modal = {
    confirm: async () => true,
    msgSuccess: api.element.ElMessage.success,
    msgError: api.element.ElMessage.error,
    msgWarning: api.element.ElMessage.warning
  };
  for (const name of [
    'el-row',
    'el-col',
    'el-card',
    'el-form-item',
    'el-input',
    'el-select',
    'el-option',
    'el-radio',
    'el-radio-group',
    'el-button',
    'el-tooltip',
    'el-divider',
    'el-link',
    'el-icon',
    'el-input-number',
    'el-slider',
    'el-switch',
    'el-tabs',
    'el-tag',
    'el-empty',
    'el-alert',
    'el-progress',
    'el-upload',
    'dict-tag',
    'pagination',
    'right-toolbar'
  ]) {
    app.component(name, {
      props: ['modelValue', 'label', 'description', 'title', 'loading', 'disabled', 'placeholder'],
      setup(props, { attrs, slots }) {
        return () => h(name, { ...attrs, ...props }, [props.description ?? '', ...[slots.header?.(), slots.default?.()].flat().filter(Boolean)]);
      }
    });
  }
  app.component('el-form', {
    props: ['model'],
    setup(props, { attrs, slots, expose }) {
      expose({ validate: async (callback) => callback(true), clearValidate() {} });
      return () => h('el-form', { ...attrs, model: props.model }, slots.default?.());
    }
  });
  app.component('el-table', {
    props: ['data'],
    setup(props, { attrs, slots }) {
      provide(
        'offline:rows',
        computed(() => props.data ?? [])
      );
      return () => h('el-table', { ...attrs, data: props.data }, slots.default?.());
    }
  });
  app.component('el-table-column', {
    props: ['label'],
    setup(props, { attrs, slots }) {
      const rows = inject('offline:rows');
      return () =>
        h(
          'el-table-column',
          { ...attrs, label: props.label },
          rows?.value.flatMap((row) => slots.default?.({ row }) ?? [])
        );
    }
  });
  app.component('el-tab-pane', {
    props: ['name'],
    setup(props, { slots }) {
      return () => (props.name === 'graph' ? null : h('el-tab-pane', { name: props.name }, slots.default?.()));
    }
  });
  for (const name of ['el-dialog', 'el-drawer']) {
    app.component(name, {
      props: ['modelValue', 'title'],
      setup(props, { attrs, slots }) {
        return () => (props.modelValue ? h(name, { ...attrs, ...props }, [slots.default?.(), slots.footer?.()].flat().filter(Boolean)) : null);
      }
    });
  }
  for (const name of ['loading', 'hasPermi']) app.directive(name, {});
  app.mount(root);
  await flush();
  return {
    ...api,
    root,
    unmount: () => app.unmount(),
    nodes: (tag) => find(root, tag),
    button(label) {
      const node = find(root, 'el-button').find((node) => text(node).trim() === label);
      assert.ok(node, `真实页面按钮必须存在：${label}`);
      return node;
    }
  };
}
