import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { compileScript, parse } from '@vue/compiler-sfc';
import { createRenderer, defineComponent, h, nextTick, reactive, ref } from 'vue';

const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(import.meta.url);
const compiled = new Map();
const vueGlobals = ['ref', 'computed', 'watch', 'watchEffect', 'onMounted', 'onBeforeUnmount', 'nextTick', 'getCurrentInstance'];

/** 编译实际组件；仅隔离全局状态、浏览器 API 和不属于本次范围的子组件。 */
async function component(relativePath) {
  if (compiled.has(relativePath)) return compiled.get(relativePath);
  const result = await build({
    entryPoints: [resolve(uiRoot, relativePath)],
    bundle: true,
    format: 'cjs',
    platform: 'node',
    write: false,
    logLevel: 'silent',
    external: ['vue', '@element-plus/icons-vue'],
    define: { 'import.meta.env': JSON.stringify({ VITE_APP_CONTEXT_PATH: '/' }) },
    plugins: [
      {
        name: 'layout-component-boundaries',
        setup(build) {
          build.onResolve({ filter: /^@\/store\/modules\// }, ({ path }) => ({ path: path.split('/').at(-1), namespace: 'store' }));
          build.onLoad({ filter: /.*/, namespace: 'store' }, ({ path }) => ({
            contents: `export const use${path[0].toUpperCase() + path.slice(1)}Store = () => globalThis.__layoutTest.${path};`
          }));
          build.onResolve({ filter: /^@\/api\// }, ({ path }) => ({ path, namespace: 'api' }));
          build.onLoad({ filter: /.*/, namespace: 'api' }, () => ({
            contents: `export const getTenantList = async () => {globalThis.__layoutTest.tenantListCalls++; return {data: {tenantEnabled:true, voList:[]}};}; export const dynamicTenant = async () => globalThis.__layoutTest.tenantAction(); export const dynamicClear = async () => globalThis.__layoutTest.tenantAction(); export const unreadNotificationCount = async () => ({data: 0});`
          }));
          build.onResolve({ filter: /^@\/utils\/(dynamicTitle|sse|websocket)$/ }, () => ({ path: 'dynamic-title', namespace: 'fixture' }));
          build.onLoad({ filter: /.*/, namespace: 'fixture' }, () => ({
            contents: 'export const useDynamicTitle = () => {}; export const initSSE = () => {}; export const initWebSocket = () => {};'
          }));
          build.onResolve({ filter: /\.(jpg|png|svg)$/ }, () => ({ path: 'avatar', namespace: 'asset' }));
          build.onLoad({ filter: /.*/, namespace: 'asset' }, () => ({ contents: 'export default "/fixture-avatar.jpg";' }));
          build.onResolve({ filter: /^@\// }, ({ path }) => ({ path: resolve(uiRoot, 'src', path.slice(2)) + (path.endsWith('.vue') ? '' : '.ts') }));
          build.onLoad({ filter: /\.vue$/ }, ({ path }) => {
            if (
              !path.endsWith('Navbar.vue') &&
              !path.replaceAll('\\', '/').endsWith('ThemeSettings/index.vue') &&
              !path.replaceAll('\\', '/').endsWith('src/layout/index.vue')
            ) {
              return { contents: 'export default {setup(_, {expose}) {expose({open() {}, openSearch() {}}); return () => null;}};' };
            }
            const { descriptor, errors } = parse(readFileSync(path, 'utf8'), { filename: path });
            assert.deepEqual(errors, []);
            const script = compileScript(descriptor, { id: 'layout-regression', inlineTemplate: true });
            const missing = vueGlobals.filter((name) => !script.imports[name]);
            return {
              loader: 'ts',
              contents: `import { ${missing.join(', ')} } from 'vue';\nconst useRouter = () => globalThis.__layoutTest.router;\n${script.imports.useFullscreen ? '' : 'const useFullscreen = () => ({isFullscreen: ref(false), isSupported: ref(true), toggle() {}});'}\nconst ElMessage = {success() {}, error() {}};\nconst ElMessageBox = {confirm: (...args) => globalThis.__layoutTest.confirm(...args)};
const useWindowSize = () => ({width: globalThis.__layoutTest.viewportWidth});\n${script.content}`
            };
          });
        }
      }
    ]
  });
  const module = { exports: {} };
  new Function('require', 'module', 'exports', result.outputFiles[0].text)(require, module, module.exports);
  compiled.set(relativePath, module.exports.default);
  return module.exports.default;
}

/** 离线宿主提供 Vue 事件和响应式渲染，不调用真实 DOM 或业务服务。 */
function host(tag, text = '') {
  return { tag, text, props: {}, children: [], parent: null };
}
const renderer = createRenderer({
  createElement: host,
  createText: (text) => host('#text', text),
  createComment: (text) => host('#comment', text),
  setText: (node, text) => {
    node.text = text;
  },
  setElementText: (node, text) => {
    node.text = text;
    node.children = [];
  },
  patchProp: (node, key, _old, value) => {
    node.props[key] = value;
  },
  insert(node, parent, anchor) {
    if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1);
    node.parent = parent;
    const index = anchor ? parent.children.indexOf(anchor) : -1;
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node);
  },
  remove(node) {
    node.parent?.children.splice(node.parent.children.indexOf(node), 1);
    node.parent = null;
  },
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null
});
function nodes(node, predicate) {
  return [...(predicate(node) ? [node] : []), ...node.children.flatMap((child) => nodes(child, predicate))];
}
function stub(name) {
  return defineComponent({
    inheritAttrs: false,
    props: ['modelValue'],
    setup(props, { attrs, slots }) {
      return () =>
        ['el-drawer', 'el-dialog'].includes(name) && !props.modelValue
          ? null
          : h(name, { ...attrs, modelValue: props.modelValue }, [
              slots.default?.(),
              slots.dropdown?.(),
              slots.reference?.(),
              slots.header?.(),
              slots.footer?.()
            ]);
    }
  });
}
function fixture() {
  globalThis.window = { location: { origin: 'http://layout.test' }, open() {} };
  const theme = reactive({
    themeMode: 'light',
    brandColor: '#409EFF',
    sideTheme: 'theme-dark',
    navType: 'left',
    contentDensity: 'comfortable',
    tabsMode: 'standard',
    tabsIcon: false,
    fixedHeader: true,
    sidebarLogo: true,
    animationEnable: false,
    isDark: false,
    isTabsEnabled: true,
    brandColorOptions: [
      { label: '科技蓝', value: '#409EFF', color: '#409EFF' },
      { label: '护眼绿', value: '#67C23A', color: '#67C23A' }
    ],
    setThemeMode(value) {
      this.themeMode = value;
    },
    setBrandColor(value) {
      this.brandColor = value;
    },
    setNavType(value) {
      this.navType = value;
    },
    setContentDensity(value) {
      this.contentDensity = value;
    },
    setTabsMode(value) {
      this.tabsMode = value;
    },
    setSideTheme(value) {
      this.sideTheme = value;
    },
    saveConfig() {},
    resetConfig() {
      this.themeMode = 'light';
      this.brandColor = '#409EFF';
    }
  });
  const state = {
    viewportWidth: ref(1440),
    tenantListCalls: 0,
    tenantAction: () => Promise.resolve(),
    confirm: () => Promise.resolve(),
    im: { init() {} },
    permission: reactive({
      defaultRoutes: [{ path: '/sales' }, { path: '/warehouse' }],
      sidebarRouters: [{ path: '/sales' }],
      setSidebarRouters(value) {
        this.sidebarRouters = value;
      }
    }),
    theme,
    settings: reactive({ dynamicTitle: false, showSettings: true }),
    app: reactive({
      device: 'desktop',
      size: 'default',
      sidebar: { opened: true },
      setSize(value) {
        this.size = value;
      },
      toggleSideBar() {},
      toggleSideBarHide(value) {
        this.sidebar.hide = value;
      },
      closeSideBar() {
        this.sidebar.opened = false;
      },
      toggleDevice(value) {
        this.device = value;
      }
    }),
    user: reactive({ userId: '1', avatar: '', nickname: '测试用户', name: 'fixture' }),
    notice: reactive({
      state: { unreadCount: 3 },
      setUnreadCount(value) {
        this.state.unreadCount = value;
      }
    }),
    router: { currentRoute: ref({ fullPath: '/' }), push: async () => {}, replace: async () => {} }
  };
  globalThis.__layoutTest = state;
  return state;
}
async function mount(relativePath) {
  const root = host('root');
  const app = renderer.createApp(await component(relativePath));
  for (const name of [
    'el-drawer',
    'el-tabs',
    'el-tab-pane',
    'el-tooltip',
    'el-icon',
    'el-scrollbar',
    'el-divider',
    'el-color-picker',
    'el-radio-group',
    'el-radio-button',
    'el-switch',
    'el-button',
    'el-select',
    'el-option',
    'el-popover',
    'el-badge',
    'el-dropdown',
    'el-dropdown-menu',
    'el-dropdown-item',
    'el-dialog',
    'el-form-item',
    'svg-icon',
    'breadcrumb',
    'top-nav',
    'hamburger',
    'screenfull',
    'size-select',
    'caret-bottom',
    'router-link'
  ])
    app.component(name, stub(name));
  app.config.globalProperties.$tab = { closeAllPage: async () => {}, refreshPage: async () => {} };
  const instance = app.mount(root);
  await nextTick();
  return { root, instance, unmount: () => app.unmount() };
}

test('主题面板打开期间，外部颜色更新立即同步到颜色选择器', async () => {
  const state = fixture();
  const view = await mount('src/layout/components/ThemeSettings/index.vue');
  view.instance.open();
  await nextTick();
  state.theme.setBrandColor('#67C23A');
  await nextTick();
  const picker = nodes(view.root, (node) => node.tag === 'el-color-picker')[0];
  assert.equal(picker?.props.modelValue, '#67C23A');
  view.unmount();
});

test('主题面板有可通过键盘触发的关闭和主题模式按钮', async () => {
  fixture();
  const view = await mount('src/layout/components/ThemeSettings/index.vue');
  view.instance.open();
  await nextTick();
  const close = nodes(view.root, (node) => node.tag === 'button' && node.props['aria-label'] === '关闭主题设置')[0];
  assert.ok(close, '应提供有名称的原生关闭按钮');
  const mode = nodes(view.root, (node) => node.tag === 'button' && node.props['aria-pressed'] !== undefined);
  assert.ok(mode.length >= 3, '模式按钮应报告选中状态');
  close.props.onClick();
  await nextTick();
  assert.equal(nodes(view.root, (node) => node.tag === 'el-drawer').length, 0);
  view.unmount();
});

test('布局入口打开统一抽屉的布局分组', async () => {
  fixture();
  const view = await mount('src/layout/components/ThemeSettings/index.vue');
  view.instance.open('layout');
  await nextTick();
  const tabs = nodes(view.root, (node) => node.tag === 'el-tabs')[0];
  assert.equal(tabs?.props.modelValue, 'layout');
  view.unmount();
});

test('移动端保留搜索、通知和主题设置的原生按钮', async () => {
  const state = fixture();
  state.app.device = 'mobile';
  const view = await mount('src/layout/components/Navbar.vue');
  const labels = nodes(view.root, (node) => node.tag === 'button').map((node) => node.props['aria-label']);
  for (const label of ['搜索菜单', '主题设置', '更多工具']) assert.ok(labels.includes(label), label + ' 在移动端应可访问');
  assert.ok(
    labels.some((label) => label?.startsWith('查看通知')),
    '通知按钮应可访问'
  );
  view.unmount();
});

test('字符串超级管理员 ID 可以访问租户切换器', async () => {
  fixture();
  const view = await mount('src/layout/components/Navbar.vue');
  const selector = nodes(view.root, (node) => node.tag === 'el-select' && node.props['aria-label'] === '切换租户')[0];
  assert.ok(selector, '后端字符串 ID 1 应显示有名称的租户切换控件');
  view.unmount();
});

/** 按渲染结果识别操作，不依赖源代码字符串。 */
function textOf(node) {
  return (node.tag === '#comment' ? '' : (node.text ?? '')) + node.children.map(textOf).join('');
}

test('恢复默认同时重置主题、组件尺寸和动态标题，不刷新页面', async () => {
  const state = fixture();
  state.theme.brandColor = '#67C23A';
  state.app.size = 'large';
  state.settings.dynamicTitle = true;
  const view = await mount('src/layout/components/ThemeSettings/index.vue');
  view.instance.open();
  await nextTick();
  const reset = nodes(view.root, (node) => node.tag === 'el-button' && textOf(node).includes('恢复默认'))[0];
  await reset.props.onClick();
  await nextTick();
  assert.equal(state.theme.brandColor, '#409EFF');
  assert.equal(state.app.size, 'default');
  assert.equal(state.settings.dynamicTitle, false);
  assert.ok(nodes(view.root, (node) => node.tag === 'el-drawer').length, '恢复默认后仍停留在当前面板');
  view.unmount();
});

test('取消恢复默认后保留所有界面设置', async () => {
  const state = fixture();
  state.theme.brandColor = '#67C23A';
  state.app.size = 'large';
  state.settings.dynamicTitle = true;
  state.confirm = () => Promise.reject('cancel');
  const view = await mount('src/layout/components/ThemeSettings/index.vue');
  view.instance.open();
  await nextTick();
  const reset = nodes(view.root, (node) => node.tag === 'el-button' && textOf(node).includes('恢复默认'))[0];
  await reset.props.onClick();
  await nextTick();
  assert.equal(state.theme.brandColor, '#67C23A');
  assert.equal(state.app.size, 'large');
  assert.equal(state.settings.dynamicTitle, true);
  view.unmount();
});

test('手机混合导航使用完整菜单，仍可访问其他业务模块', async () => {
  const state = fixture();
  state.theme.navType = 'mix';
  state.viewportWidth.value = 375;
  const view = await mount('src/layout/index.vue');
  await nextTick();
  assert.deepEqual(
    state.permission.sidebarRouters.map((route) => route.path),
    ['/sales', '/warehouse']
  );
  assert.equal(state.app.sidebar.hide, false);
  view.unmount();
});

test('普通用户初始化导航栏时不调用租户管理接口', async () => {
  const state = fixture();
  state.user.userId = '9007199254753012345';
  const view = await mount('src/layout/components/Navbar.vue');
  await view.instance.initTenantList();
  assert.equal(state.tenantListCalls, 0);
  assert.equal(nodes(view.root, (node) => node.tag === 'el-select').length, 0);
  view.unmount();
});

test('租户切换及清空失败时，选择器恢复到最后成功的会话租户', async () => {
  const state = fixture();
  const view = await mount('src/layout/components/Navbar.vue');
  const selector = () => nodes(view.root, (node) => node.tag === 'el-select')[0];
  selector().props['onUpdate:modelValue']('synthetic-tenant-ok');
  await selector().props.onChange('synthetic-tenant-ok');
  await nextTick();
  state.tenantAction = () => Promise.reject(new Error('synthetic failure'));
  selector().props['onUpdate:modelValue']('synthetic-tenant-denied');
  await selector().props.onChange('synthetic-tenant-denied');
  await nextTick();
  assert.equal(selector().props.modelValue, 'synthetic-tenant-ok');
  selector().props['onUpdate:modelValue'](undefined);
  await selector().props.onClear();
  await nextTick();
  assert.equal(selector().props.modelValue, 'synthetic-tenant-ok');
  view.unmount();
});
