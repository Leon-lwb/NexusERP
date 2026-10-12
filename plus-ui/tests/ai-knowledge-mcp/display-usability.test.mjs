import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { build } from 'esbuild';
import { compileScript, compileStyle, parse } from '@vue/compiler-sfc';
import { createRenderer, h, nextTick, reactive } from 'vue';
import { find, flush, mountPage, text } from './component-harness.mjs';

const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const require = createRequire(import.meta.url);
const origin = 'http://ui.test';
const defaultAvatar = '/assets/existing-profile-fixture.jpg';
const knowledge = { id: '2100000000000000101', knowledgeName: '制造运营知识库', embeddingModel: 'qwen3.7-text-embedding', status: '0' };
const files = ['采购与库存作业规范.md', '生产与质量作业规范.md', '设备与工位作业指引.md'];

function source(relativePath) {
  // 指定基线目录只读复验RED，不回退、覆盖或改写当前实现。
  const baseline = process.env.AI_DISPLAY_BASELINE_DIR;
  const path = baseline
    ? resolve(baseline, relativePath.endsWith('Navbar.vue') ? 'Navbar.vue' : 'knowledge-index.vue')
    : resolve(uiRoot, relativePath);
  return readFileSync(path, 'utf8');
}

async function knowledgePage(t) {
  const page = await mountPage('src/views/ai/knowledge/index.vue', ({ routes }) => {
    routes.set('/ai/knowledge/list', () => ({ code: 200, rows: [knowledge], total: 1 }));
    routes.set('/ai/knowledge/document/listByKnowledge/' + knowledge.id, () => ({
      code: 200,
      data: files.map((fileName, index) => ({
        id: String(index + 1),
        fileName,
        fileSize: 1000,
        chunkCount: 2,
        status: '2'
      }))
    }));
  });
  t.after(page.unmount);
  return page;
}

for (const [prop, width, value] of [
  ['knowledgeName', 160, knowledge.knowledgeName],
  ['embeddingModel', 200, knowledge.embeddingModel]
]) {
  test(`真实知识表格 ${prop} 有明确最小宽度并显示完整换行文本`, async (t) => {
    const { descriptor, errors } = parse(source('src/views/ai/knowledge/index.vue'));
    assert.deepEqual(errors, []);
    // 基线重放先验证真实模板，不把修复后的渲染冒充旧模板RED。
    assert.ok(new RegExp(`prop="${prop}"[^>]*min-width="${width}"`).test(descriptor.template.content), `真实模板缺少 ${prop} 的最小宽度 ${width}`);
    const page = await knowledgePage(t);
    const column = page.nodes('el-table-column').find((node) => node.props.prop === prop);
    assert.ok(Number(column.props['min-width']) >= width);
    const cell = find(column, 'span').find((node) => node.props.class?.includes('knowledge-readable-cell'));
    assert.ok(cell, '生产模板必须实际渲染完整文本单元格');
    assert.equal(text(cell), value);
    assert.equal(cell.props.title, value);
    assert.notEqual(column.props['show-overflow-tooltip'], true, '名称不依赖省略号才能显示');
  });
}

test('真实文档表格为三份正式名称保留宽度、完整文本和原生可见提示', async (t) => {
  const { descriptor } = parse(source('src/views/ai/knowledge/index.vue'));
  assert.ok(/prop="fileName"[^>]*min-width="260"/.test(descriptor.template.content), '真实文件名模板缺少260px最小宽度');
  const page = await knowledgePage(t);
  page.nodes('el-table')[0].props.onCurrentChange(knowledge);
  await flush();
  const column = page.nodes('el-table-column').find((node) => node.props.prop === 'fileName');
  assert.ok(Number(column.props['min-width']) >= 260);
  const cells = find(column, 'span').filter((node) => node.props.class?.includes('knowledge-readable-cell'));
  assert.deepEqual(
    cells.map((node) => text(node)),
    files
  );
  assert.deepEqual(
    cells.map((node) => node.props.title),
    files
  );
});

test('真实scoped CSS允许完整名称/长Embedding换行，不使用省略号截断', () => {
  const { descriptor, errors } = parse(source('src/views/ai/knowledge/index.vue'));
  assert.deepEqual(errors, []);
  const compiled = compileStyle({ source: descriptor.styles[0].content, id: 'ai-display-regression', scoped: true });
  assert.deepEqual(compiled.errors, []);
  const rule = compiled.code.match(/\.knowledge-readable-cell[^{}]*\{([^}]+)\}/)?.[1];
  assert.ok(rule, '真实SFC必须具有可读文本样式');
  assert.match(rule, /white-space:\s*normal/);
  assert.match(rule, /overflow-wrap:\s*anywhere/);
  assert.doesNotMatch(rule, /text-overflow:\s*ellipsis|white-space:\s*nowrap/);
});

// 编译生产Navbar，仅在store/API/子组件边界替身，不提取或重写头像实现。
let navbarBundle;
async function loadNavbar() {
  if (!navbarBundle) {
    const filename = resolve(uiRoot, 'src/layout/components/Navbar.vue');
    const { descriptor, errors } = parse(source('src/layout/components/Navbar.vue'), { filename });
    assert.deepEqual(errors, []);
    const compiled = compileScript(descriptor, { id: 'navbar-avatar-regression', inlineTemplate: true });
    const imports = 'import { computed, getCurrentInstance, onMounted, reactive, ref, watch } from "vue";';
    navbarBundle = build({
      stdin: { contents: imports + '\n' + compiled.content, loader: 'ts', resolveDir: dirname(filename) },
      bundle: true,
      write: false,
      platform: 'node',
      format: 'cjs',
      packages: 'external',
      logLevel: 'silent',
      define: { 'import.meta.env': '{}' },
      plugins: [
        {
          name: 'navbar-boundary-only',
          setup(builder) {
            builder.onResolve({ filter: /^@\/|\.vue$/ }, ({ path }) => ({ path, external: true }));
          }
        }
      ]
    }).then((result) => result.outputFiles[0].text);
  }
  return navbarBundle;
}

async function navbar(t, avatar) {
  const previousWindow = globalThis.window;
  globalThis.window = { location: { origin } };
  const user = reactive({ userId: 2, avatar });
  const notice = reactive({
    state: { unreadCount: 0 },
    setUnreadCount(value) {
      this.state.unreadCount = value;
    }
  });
  const stores = {
    '@/store/modules/app': { useAppStore: () => ({ sidebar: { opened: false }, device: 'mobile' }) },
    '@/store/modules/user': { useUserStore: () => user },
    '@/store/modules/settings': { useSettingsStore: () => ({ showSettings: false }) },
    '@/store/modules/theme': { useThemeStore: () => ({ navType: 'left', sidebarLogo: false }) },
    '@/store/modules/notice': { useNoticeStore: () => notice },
    '@/api/im/notification': { unreadNotificationCount: async () => ({ data: 0 }) },
    '@/enums/NavTypeEnum': { NavTypeEnum: { LEFT: 'left', MIX: 'mix', TOP: 'top' } },
    '@/assets/images/profile.jpg': defaultAvatar
  };
  const emptyChild = { render: () => null };
  const module = { exports: {} };
  new Function('require', 'module', 'exports', 'useRouter', await loadNavbar())(
    (name) => (name in stores ? stores[name] : name.startsWith('@/') || name.endsWith('.vue') ? emptyChild : require(name)),
    module,
    module.exports,
    () => ({ push: async () => {}, replace: async () => {}, currentRoute: { value: {} } })
  );
  const host = (tag, value = '') => ({ tag, text: value, props: {}, children: [], parent: null });
  const srcChanges = [];
  const renderer = createRenderer({
    createElement: (tag) => host(tag),
    createText: (value) => host('#text', value),
    createComment: (value) => host('#comment', value),
    setText: (node, value) => {
      node.text = value;
    },
    setElementText: (node, value) => {
      node.text = value;
      node.children = [];
    },
    patchProp(node, key, _old, value) {
      node.props[key] = value;
      if (node.tag === 'img' && key === 'src') srcChanges.push(value);
    },
    insert(node, parent, anchor) {
      if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1);
      node.parent = parent;
      const at = anchor ? parent.children.indexOf(anchor) : -1;
      parent.children.splice(at < 0 ? parent.children.length : at, 0, node);
    },
    remove(node) {
      node.parent?.children.splice(node.parent.children.indexOf(node), 1);
    },
    parentNode: (node) => node.parent,
    nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] || null
  });
  const app = renderer.createApp(module.exports.default);
  for (const tag of [
    'hamburger',
    'breadcrumb',
    'el-dropdown',
    'el-icon',
    'caret-bottom',
    'el-dropdown-menu',
    'router-link',
    'el-dropdown-item',
    'top-nav',
    'el-option',
    'svg-icon',
    'el-select',
    'el-tooltip',
    'el-badge',
    'el-popover',
    'screenfull',
    'size-select'
  ]) {
    app.component(tag, {
      setup(_, { slots }) {
        return () => h(tag, {}, slots.default?.());
      }
    });
  }
  const root = host('root');
  t.after(() => {
    app.unmount();
    if (previousWindow === undefined) delete globalThis.window;
    else globalThis.window = previousWindow;
  });
  app.mount(root);
  await flush();
  const image = () => find(root, 'img').find((node) => node.props.class?.includes('user-avatar'));
  assert.ok(image(), '必须找到生产Navbar实际img');
  return { user, image, srcChanges };
}

const signedAvatar = 'https://minio.example.test/bucket/avatar.png?X-Amz-Signature=test-signature&X-Amz-Expires=120';

for (const [label, avatar, expected] of [
  ['远程 HTTPS 签名地址保持查询参数', signedAvatar, signedAvatar],
  ['不同端口的 HTTP 对象存储', 'http://ui.test:9000/bucket/avatar.png', 'http://ui.test:9000/bucket/avatar.png'],
  ['协议相对远程地址', '//minio.example.test/avatar.png', 'http://minio.example.test/avatar.png'],
  ['同源完整地址', origin + '/avatars/user.jpg', origin + '/avatars/user.jpg'],
  ['站点根相对地址', '/avatars/user.jpg', origin + '/avatars/user.jpg'],
  ['普通相对地址', 'avatars/user.jpg', origin + '/avatars/user.jpg']
]) {
  test(`实际Navbar允许有效头像：${label}`, async (t) => {
    const page = await navbar(t, avatar);
    assert.equal(page.image().props.src, expected);
    assert.equal(page.user.avatar, avatar, '展示时不改写用户头像数据或签名');
  });
}

for (const avatar of [
  undefined,
  null,
  '',
  '   ',
  'data:image/svg+xml,<svg/>',
  'javascript:alert(1)',
  'file:///tmp/avatar.png',
  'ftp://minio.example.test/avatar.png',
  'https://user:pass@minio.example.test/avatar.png',
  'http://[invalid'
]) {
  test(`实际Navbar空白或非法头像回退：${JSON.stringify(avatar)}`, async (t) => {
    const page = await navbar(t, avatar);
    assert.equal(page.image().props.src, defaultAvatar);
    assert.equal(page.user.avatar, avatar, '回退不修改用户头像数据');
  });
}

test('实际Navbar默认头像保留可读中文alt', async (t) => {
  const page = await navbar(t, '');
  assert.equal(page.image().props.src, defaultAvatar);
  assert.equal(page.image().props.alt, '用户头像');
});

test('实际Navbar远程头像首次失败回退，重复error不循环加载默认资产', async (t) => {
  const page = await navbar(t, signedAvatar);
  assert.equal(page.image().props.src, signedAvatar);
  assert.equal(typeof page.image().props.onError, 'function', '真实模板必须绑定加载错误');
  page.image().props.onError(new Error('图片加载失败'));
  await nextTick();
  assert.equal(page.image().props.src, defaultAvatar);
  const updates = page.srcChanges.length;
  page.image().props.onError(new Error('默认资源重复error'));
  await nextTick();
  assert.equal(page.image().props.src, defaultAvatar);
  assert.equal(page.srcChanges.length, updates);
  assert.equal(page.user.avatar, signedAvatar, '加载失败只改变展示状态，不写用户数据');
});

test('实际Navbar头像变化后恢复远程图片，不遗留上一来源失败状态', async (t) => {
  const page = await navbar(t, origin + '/avatars/old.jpg');
  page.image().props.onError(new Error('旧头像失败'));
  await nextTick();
  assert.equal(page.image().props.src, defaultAvatar);
  page.user.avatar = signedAvatar;
  await flush();
  assert.equal(page.image().props.src, signedAvatar);
});
