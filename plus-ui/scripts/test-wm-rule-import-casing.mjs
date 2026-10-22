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
    assertMenuSeed(sql, { name, directory, menuId });
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

function assertMenuSeed(sql, { name, directory, menuId }) {
  // Restrict rows to actual sys_menu VALUES statements, including historical multirow dumps.
  // Quoted semicolons/parentheses are data, not statement or tuple boundaries.
  const statements = [...sql.matchAll(/\bINSERT\s+INTO\s+`?sys_menu`?\s+VALUES\s*((?:'(?:''|\\.|[^'\\])*'|[^';])*);/gi)];
  const rows = statements.flatMap((statement) => [...statement[1].matchAll(/\((?:'(?:''|\\.|[^'\\])*'|[^'()])*\)/g)].map((match) => match[0]));
  const matches = rows.filter((row) => new RegExp(`^\\(\\s*${menuId}\\s*,`).test(row));
  assert.equal(matches.length, 1, `${name}菜单 ${menuId} 必须唯一存在于主安装 SQL`);
  const row = matches[0].match(new RegExp(`^\\(\\s*${menuId}, '[^']*', (\\d+), \\d+, '([^']+)', '([^']+)'`));
  assert.ok(row, `${name}菜单 ${menuId} 必须有可解析的父级、path 和组件`);
  const [, parentId, path, component] = row;
  assert.equal(parentId, '2966', `${name}必须保留仓储策略父菜单`);
  assert.equal(path, directory, `${name}页面 URL 的末级 path 不得改变`);
  assert.equal(component, `erp/wm/${directory}/index`, `${name}菜单组件目录必须与真实 Vue 目录大小写精确一致`);
  assert.ok(trackedPaths.has(`plus-ui/src/views/${component}.vue`), `${name}菜单组件必须逐字符命中 Git 跟踪路径`);
}

for (const rule of rules) {
  const tuple = (parent = 2966, path = rule.directory, component = `erp/wm/${rule.directory}/index`) =>
    `(${rule.menuId}, '${rule.name}', ${parent}, 1, '${path}', '${component}', NULL)`;
  test(`${rule.name}解析器支持当前完整 INSERT 与历史多行 INSERT`, () => {
    assertMenuSeed(`INSERT INTO ` + '`sys_menu`' + ` VALUES ${tuple()};`, rule);
    assertMenuSeed(`INSERT INTO ` + '`sys_menu`' + ` VALUES\n${tuple()},\n(9999, 'other', 0, 1, '', '', NULL);`, rule);
  });
  test(`${rule.name}解析器拒绝错误父级、URL 和组件大小写`, () => {
    for (const row of [
      tuple(2965),
      tuple(2966, rule.directory.toLowerCase()),
      tuple(2966, rule.directory, `erp/wm/${rule.directory.toLowerCase()}/index`)
    ]) {
      for (const prefix of ['INSERT INTO `sys_menu` VALUES ', 'INSERT INTO `sys_menu` VALUES\n']) {
        assert.throws(() => assertMenuSeed(`${prefix}${row};`, rule), assert.AssertionError);
      }
    }
  });
  test(`${rule.name}解析器拒绝其他表、相似 ID 和重复菜单行`, () => {
    for (const invalid of [
      `INSERT INTO other_table VALUES\n${tuple()};`,
      `INSERT INTO ` + '`sys_menu`' + ` VALUES ${tuple().replace(`(${rule.menuId},`, `(${rule.menuId}0,`)};`,
      `INSERT INTO ` + '`sys_menu`' + ` VALUES\n${tuple()},\n${tuple()};`
    ])
      assert.throws(() => assertMenuSeed(invalid, rule), assert.AssertionError);
  });
}
