import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { readFile, mkdir } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { parse, compileScript } from '@vue/compiler-sfc';
import { createSSRApp, h, Fragment } from 'vue';
import { renderToString } from '@vue/server-renderer';
await mkdir('node_modules/.tmp', { recursive: true });
await build({
  entryPoints: ['src/components/report/ReportActions.vue'],
  outfile: 'node_modules/.tmp/report-actions.mjs',
  bundle: true,
  format: 'esm',
  platform: 'node',
  external: ['vue'],
  plugins: [
    {
      name: 'report-actions-permissions',
      /** 编译真实模板，只用合成账号替换用户存储。 */
      setup(builder) {
        builder.onResolve({ filter: /^@\/store\/modules\/user$/ }, () => ({ path: 'user', namespace: 'fixture' }));
        builder.onLoad({ filter: /.*/, namespace: 'fixture' }, () => ({
          contents: 'export const useUserStore=()=>({permissions:globalThis.__reportActionPermissions,roles:[]});'
        }));
        builder.onResolve({ filter: /^@\// }, (args) => ({ path: resolve('src', args.path.slice(2) + (args.path.endsWith('.vue') ? '' : '.ts')) }));
        builder.onLoad({ filter: /\.vue$/ }, async (args) => {
          const { descriptor } = parse(await readFile(args.path, 'utf8'));
          return {
            contents: compileScript(descriptor, { id: args.path, inlineTemplate: true }).content,
            loader: 'ts',
            resolveDir: dirname(args.path)
          };
        });
      }
    }
  ]
});
/** 挂载实际菜单模板，权限辅助函数使用真实代码和合成角色。 */
const actions = (await import(pathToFileURL(resolve('node_modules/.tmp/report-actions.mjs')))).default;
/** 输出菜单的全部插槽，覆盖 Element Plus 弹层和 Fragment 根节点边界。 */
async function render(permissions, status = 'PUBLISHED') {
  globalThis.__reportActionPermissions = permissions;
  const app = createSSRApp(actions, { row: { id: '9223372036854775807', status }, copying: '' });
  app.directive('hasPermi', {});
  for (const name of ['ElButton', 'ElDropdownMenu'])
    app.component(name, {
      /** 渲染隔离插槽以检查权限控制的实际菜单项。 */
      setup(_props, { slots }) {
        return () => h('div', slots.default?.());
      }
    });
  app.component('ElDropdown', {
    /** 渲染隔离插槽以检查权限控制的实际菜单项。 */
    setup(_props, { slots }) {
      return () => h('div', [slots.default?.(), slots.dropdown?.()]);
    }
  });
  app.component('ElDropdownItem', {
    props: ['command'],
    /** 用 Fragment 复现菜单项的根节点结构。 */
    setup(props, { slots }) {
      return () => h(Fragment, [h('li', { 'data-command': props.command }, slots.default?.())]);
    }
  });
  app.config.warnHandler = () => {};
  const html = await renderToString(app);
  return [...html.matchAll(/data-command="([^"]+)"/g)].map((match) => match[1]);
}
/** 缺少 add 权限时，编辑者不能复制，也不能访问发布、授权和归档。 */
test('列表和编辑权限只显示发布历史，复制仍需新增权限', async () => {
  assert.deepEqual(await render(['report:definition:list', 'report:definition:edit']), ['history']);
});
/** 复制必须同时满足编辑与新增，单一新增权限不开放入口。 */
test('复制菜单同时要求 edit 与 add', async () => {
  assert.deepEqual(await render(['report:definition:edit', 'report:definition:add']), ['copy']);
  assert.deepEqual(await render(['report:definition:add']), []);
});
/** 发布者只看到发布和已发布资源的下线操作。 */
test('发布权限不开放复制、ACL或归档', async () => {
  assert.deepEqual(await render(['report:definition:publish']), ['publish', 'offline']);
  assert.deepEqual(await render(['report:definition:publish'], 'DRAFT'), ['publish']);
});
/** 维护权限分开检查，不因同处更多菜单而共享授权。 */
test('ACL与归档分别检查资源管理权限', async () => {
  assert.deepEqual(await render(['report:acl:manage']), ['acl']);
  assert.deepEqual(await render(['report:definition:remove']), ['archive']);
});
/** 无权限时根本不渲染操作菜单。 */
test('无操作权限时菜单为空', async () => {
  assert.deepEqual(await render([]), []);
});
/** 保留平台超级管理员通配符的既有行为。 */
test('超级管理员保留全部已发布资源操作', async () => {
  assert.deepEqual(await render(['*:*:*']), ['history', 'copy', 'publish', 'offline', 'acl', 'archive']);
});
