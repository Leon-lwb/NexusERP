import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const repositoryRoot = new URL('../../', import.meta.url);
// 使用 Git 索引中的原始拼写，不能用 Windows 上不区分大小写的文件存在检查替代。
const trackedPaths = new Set(
  execFileSync('git', ['ls-files', '-z', '--', 'plus-ui/src/api/erp/wm', 'plus-ui/src/views/erp/wm'], {
    cwd: fileURLToPath(repositoryRoot),
    encoding: 'utf8'
  })
    .split('\0')
    .filter(Boolean)
);
const readSource = (path) => readFileSync(new URL(path, repositoryRoot), 'utf8');
const rules = [
  { name: '拣货规则', directory: 'pickingRule', endpoint: 'pickingrule', menuId: 2621 },
  { name: '上架规则', directory: 'putawayRule', endpoint: 'putawayrule', menuId: 2616 }
];

for (const { name, directory, endpoint, menuId } of rules) {
  const viewPath = `plus-ui/src/views/erp/wm/${directory}/index.vue`;
  const apiPath = `plus-ui/src/api/erp/wm/${directory}/index.ts`;

  test(`${name}菜单种子必须保持页面 URL 并精确命中真实视图组件`, () => {
    const sql = readSource('script/sql/ry-vue.sql');
    const row = sql.match(new RegExp(`^\\(${menuId}, '[^']*', (\\d+), \\d+, '([^']+)', '([^']+)'`, 'm'));
    assert.ok(row, `${name}菜单 ${menuId} 必须存在于主安装 SQL`);
    const [, parentId, path, component] = row;
    assert.equal(parentId, '2966', `${name}必须保留仓储策略父菜单`);
    assert.equal(path, directory, `${name}页面 URL 的末级 path 不得改变`);
    assert.equal(component, `erp/wm/${directory}/index`, `${name}菜单组件目录必须与真实 Vue 目录大小写精确一致`);
    assert.ok(trackedPaths.has(`plus-ui/src/views/${component}.vue`), `${name}菜单组件必须逐字符命中 Git 跟踪路径`);
  });

  test(`${name}页面导入必须与 Git 跟踪路径大小写精确一致`, () => {
    assert.ok(trackedPaths.has(viewPath), `${name}页面路径必须与 Git 跟踪拼写一致`);
    const source = readSource(viewPath);
    // 定位时允许发现错误的大小写，后续断言必须逐字符比较，不能统一转小写后放行。
    const imports = [...source.matchAll(/\bfrom\s+(['"])(@\/api\/erp\/wm\/(?:pickingrule|putawayrule)(?:\/[^'"]*)?)\1/gi)].map((match) => match[2]);
    assert.deepEqual(imports, [`@/api/erp/wm/${directory}`], `${name}页面只能使用 Git 实际的小驼峰规则目录导入`);
    const importedPath = `plus-ui/src/${imports[0].slice(2)}/index.ts`;
    assert.equal(importedPath, apiPath, `${name}导入解析后的路径必须精确匹配目标文件`);
    assert.ok(trackedPaths.has(importedPath), `${name}导入必须逐字符命中 Git 索引，不能依赖文件系统大小写容错`);
  });

  test(`${name}请求及导出 URL 必须保持既有小写拼写`, () => {
    const apiSource = readSource(apiPath);
    const baseUrl = `/erp/wm/${endpoint}`;
    const urls = [...apiSource.matchAll(/\burl\s*:\s*(['"])([^'"]+)\1/g)].map((match) => match[2]);
    assert.deepEqual(urls, [`${baseUrl}/list`, `${baseUrl}/`, baseUrl, baseUrl, `${baseUrl}/`], `${name}五项业务请求 URL 不得改为文件目录拼写`);
    const exportUrls = [...readSource(viewPath).matchAll(/\bexportUrl\s*:\s*(['"])([^'"]+)\1/g)].map((match) => match[2]);
    assert.deepEqual(exportUrls, [`${baseUrl}/export`], `${name}导出 URL 必须保留原有小写路径`);
  });
}
