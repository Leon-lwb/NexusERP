import 'vue';
import { writeFile, unlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';

/** 打包实际业务模块，测试产物仅保存在内存，不污染共享依赖。 */
export async function loadEngineeringModule(relative, plugins = []) {
  const result = await build({
    entryPoints: [fileURLToPath(new URL('../' + relative, import.meta.url))],
    absWorkingDir: fileURLToPath(new URL('../', import.meta.url)),
    bundle: true,
    platform: 'node',
    format: 'esm',
    write: false,
    plugins,
    logLevel: 'silent'
  });
  const temporary = join(tmpdir(), `nexus-engineering-${randomUUID()}.mjs`);
  await writeFile(temporary, result.outputFiles[0].text, { flag: 'wx' });
  try {
    return await import(pathToFileURL(temporary).href);
  } finally {
    await unlink(temporary);
  }
}

/** 让测试逐步控制真实异步读取的完成顺序。 */
export function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

/** 记录轮询计划并手动推进，避免依赖墙钟和长时间等待。 */
export function clock() {
  const timers = new Map();
  let id = 0;
  return {
    timers,
    setTimeout(fn, delay) {
      const key = ++id;
      timers.set(key, { fn, delay });
      return key;
    },
    clearTimeout(key) {
      timers.delete(key);
    },
    async tick() {
      const [key, entry] = timers.entries().next().value;
      timers.delete(key);
      await entry.fn();
    }
  };
}

/** 使用 Vue 实际生命周期执行组件脚本，认证边界由测试的 HTTP 响应控制。 */
export async function loadEngineeringUI(relative, enabled = true, options = {}) {
  const { readFile } = await import('node:fs/promises');
  const { parse, compileScript } = await import('@vue/compiler-sfc');
  const { createRequire } = await import('node:module');
  const { pathToFileURL } = await import('node:url');
  const require = createRequire(import.meta.url);
  return loadEngineeringModule(relative, [
    ...(options.plugins || []),
    {
      name: 'engineering-ui-boundaries',
      setup(builder) {
        builder.onResolve({ filter: /^vue$/ }, () => ({ path: pathToFileURL(require.resolve('vue')).href, external: true }));
        builder.onResolve({ filter: /^@\/api\/ai\/engineering$|^@\/utils\/permission$|^vue-router$|^.*\/engineering-config$/ }, ({ path }) => ({
          path,
          namespace: 'engineering-test'
        }));
        builder.onLoad({ filter: /.*/, namespace: 'engineering-test' }, ({ path }) => {
          if (path === '@/utils/permission')
            return { contents: 'export const checkPermi = (permissions) => globalThis.__engineeringUI.allowed(permissions);' };
          if (path === 'vue-router')
            return {
              contents: 'export const useRouter=()=>globalThis.__engineeringUI.router;export const useRoute=()=>globalThis.__engineeringUI.route;'
            };
          if (path.endsWith('/engineering-config'))
            return { contents: `export const ENGINEERING_ENABLED=${enabled};export const ENGINEERING_DISABLED_NOTICE='工程功能未启用';` };
          const methods = [
            'listEngineeringDevices',
            'createEngineeringPairingCode',
            'unpairEngineeringDevice',
            'listEngineeringTemplates',
            'createEngineeringTask',
            'listEngineeringTasks',
            'getEngineeringTask',
            'cancelEngineeringTask',
            'getEngineeringManifest',
            'getEngineeringArtifactContent',
            'getEngineeringArtifactReport'
          ];
          return {
            contents:
              'export const ENGINEERING_PROTOCOL="nexus-engineering/1";' +
              methods.map((method) => `export const ${method}=(...args)=>globalThis.__engineeringUI.${method}(...args);`).join('\n')
          };
        });
        builder.onLoad({ filter: /\.vue$/ }, async ({ path }) => {
          const { descriptor } = parse(await readFile(path, 'utf8'));
          const compiled = compileScript(descriptor, { id: path, inlineTemplate: options.renderTemplate === true });
          return { contents: compiled.content, loader: 'ts' };
        });
      }
    }
  ]);
}

/** 可选编译真实模板，验证条件渲染和 DOM ref 的先后顺序；WebGL 仍单独验收。 */
export async function mountEngineeringUI(component, props = {}, options = {}) {
  const { createRenderer, h, reactive, nextTick } = await import('vue');
  const renderer = createRenderer({
    createElement: (tag) => ({ tag, children: [], props: {}, clientWidth: 800, clientHeight: 480 }),
    createText: (text) => ({ text }),
    createComment: (text) => ({ comment: text }),
    insert: (node, parent, anchor) => {
      if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1);
      node.parent = parent;
      const index = anchor ? parent.children.indexOf(anchor) : -1;
      if (index >= 0) parent.children.splice(index, 0, node);
      else parent.children.push(node);
    },
    remove: (node) => {
      if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1);
      node.parent = undefined;
    },
    setText: (node, text) => (node.text = text),
    setElementText: (node, text) => (node.text = text),
    parentNode: (node) => node.parent,
    nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1],
    patchProp: (node, key, _previous, value) => (node.props[key] = value)
  });
  const state = reactive(props);
  // 保持组件类型稳定，更新 props 必须触发更新，而不是重新挂载掩盖生命周期问题。
  const mountedComponent = options.renderTemplate ? component : { ...component, render: () => null };
  let instance;
  const app = renderer.createApp({
    render: () => h(mountedComponent, { ...state, ref: (value) => (instance = value) })
  });
  if (options.renderTemplate) {
    for (const name of ['ElAlert', 'ElEmpty', 'ElSelect', 'ElOption', 'ElButton', 'ElTag'])
      app.component(name, {
        setup:
          (_props, { attrs, slots }) =>
          () =>
            h(name, attrs, slots.default?.())
      });
    app.directive('loading', {});
  }
  app.config.globalProperties.$modal = { confirm: async () => {}, msgSuccess: () => {}, msgWarning: () => {}, msgError: () => {} };
  const root = { children: [] };
  app.mount(root);
  const flush = async () => {
    await new Promise((resolve) => setImmediate(resolve));
    for (let i = 0; i < 20; i++) {
      await Promise.resolve();
      await nextTick();
    }
  };
  await flush();
  const walk = (node) => [node, ...(node.children || []).flatMap(walk)];
  return {
    state,
    get setup() {
      return instance.$.setupState;
    },
    findAll: (predicate) => walk(root).filter(predicate),
    unmount: () => app.unmount(),
    flush
  };
}

/** 为工程界面的事件与网络建立隔离环境。 */
export function engineeringUIEnvironment(overrides = {}) {
  const doc = new EventTarget();
  doc.hidden = false;
  globalThis.document = doc;
  globalThis.window = new EventTarget();
  globalThis.__engineeringUI = {
    allowed: () => true,
    route: { params: { taskId: '2' } },
    router: { push: async () => {} },
    listEngineeringDevices: async () => ({ data: [] }),
    listEngineeringTasks: async () => ({ rows: [], total: 0 }),
    getEngineeringTask: async (taskId) => ({
      data: { taskId, deviceId: '1', sessionId: '11', action: 'CREATE_PCB_DRAFT', state: 'WAITING_DEVICE', revision: 1 }
    }),
    getEngineeringManifest: async (taskId) => ({
      data: {
        taskId,
        protocolVersion: 'nexus-engineering/1',
        artifacts: [],
        missingTypes: [],
        revision: 1,
        state: 'WAITING_DEVICE',
        canManufacture: false
      }
    }),
    ...overrides
  };
  return globalThis.__engineeringUI;
}

/** 执行真实 API 封装，唯一替身是 Nexus 认证 request 边界。 */
export function loadEngineeringApi() {
  return loadEngineeringModule('src/api/ai/engineering.ts', [
    {
      name: 'engineering-authenticated-api',
      setup(builder) {
        builder.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'request', namespace: 'request-test' }));
        builder.onLoad({ filter: /.*/, namespace: 'request-test' }, () => ({
          contents: `export default { get: (url, config) => globalThis.__engineeringApiRequest('get', url, config), post: (url, data) => globalThis.__engineeringApiRequest('post', url, { data }), delete: (url) => globalThis.__engineeringApiRequest('delete', url, {}) };`
        }));
      }
    }
  ]);
}
