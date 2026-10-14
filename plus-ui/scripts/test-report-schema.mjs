import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
/**
 * 保存当前页面或测试的root状态。
 */
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
/**
 * 保存当前页面或测试的fileCache状态。
 */
const fileCache = new Map();
/** 读取并检查当前租户私有产物。 */
const read = (path) => {
  if (!fileCache.has(path)) fileCache.set(path, readFileSync(resolve(root, path), 'utf8'));
  return fileCache.get(path);
};
/**
 * 保存当前页面或测试的legacyTables状态。
 */
const legacyTables = [
  'jimu_dict',
  'jimu_dict_item',
  'jimu_report',
  'jimu_report_category',
  'jimu_report_data_source',
  'jimu_report_db',
  'jimu_report_db_field',
  'jimu_report_db_param',
  'jimu_report_export_job',
  'jimu_report_export_log',
  'jimu_report_ext_data',
  'jimu_report_icon_lib',
  'jimu_report_link',
  'jimu_report_map',
  'jimu_report_share',
  'jimu_report_sheet',
  'test_monthly_report',
  'tmp_report_data_1',
  'tmp_report_data_income'
];
/** 验证当前测试标题所声明的报表行为，使用隔离合成数据。 */
test('unused Jimu starters and authentication bridge are removed while module identity is retained', () => {
  const pom = read('ruoyi-modules/ruoyi-report/pom.xml');
  assert.match(pom, /<artifactId>ruoyi-report<\/artifactId>/);
  assert.equal(/jimureport|jimubi|jeecg/.test(pom), false, 'legacy starters absent');
  assert.equal(existsSync(resolve(root, 'ruoyi-modules/ruoyi-report/src/main/java/com/nexus/report/config/JimuReportTokenService.java')), false);
});
/** 验证当前测试标题所声明的报表行为，使用隔离合成数据。 */
test('new report endpoints stay in the normal authentication chain', () => {
  const config = read('ruoyi-admin/src/main/resources/application.yml');
  assert.equal(/- \/(?:jmreport|jmubi|jmreport-designer|drag|report)\/\*\*/.test(config), false, 'report authentication bypass absent');
  assert.equal(/^\s+- jm_(?:report|drag)/m.test(config), false, 'legacy tenant exclusions absent');
});
/** 验证当前测试标题所声明的报表行为，使用隔离合成数据。 */
test('new-install schema removes only unused report product tables', () => {
  const schema = read('script/sql/ry-vue.sql');
  const names = [...schema.matchAll(/CREATE TABLE\s+`([^`]+)`/g)].map((x) => x[1]);
  for (const name of legacyTables) assert.equal(names.includes(name), false, name);
  for (const name of ['sys_user', 'sys_role', 'sys_menu', 'ai_provider']) assert.equal(names.includes(name), true, name);
});
/** 验证当前测试标题所声明的报表行为，使用隔离合成数据。 */
test('nine report core tables have mandatory tenant ownership and version/task constraints', () => {
  const schema = read('script/sql/ry-vue.sql');
  const tables = new Map([...schema.matchAll(/CREATE TABLE(?: IF NOT EXISTS)?\s+`(rpt_[^`]+)`\s*\(([\s\S]*?)\) ENGINE/g)].map((x) => [x[1], x[2]]));
  const expected = [
    'rpt_catalog',
    'rpt_definition',
    'rpt_version',
    'rpt_dataset',
    'rpt_dataset_version',
    'rpt_acl',
    'rpt_run',
    'rpt_export_job',
    'rpt_audit'
  ];
  assert.equal(expected.filter((name) => tables.has(name)).length, 9);
  for (const name of expected) assert.match(tables.get(name) || '', /`tenant_id`[^,\n]*NOT NULL/, name);
  assert.match(tables.get('rpt_definition'), /draft_revision/);
  assert.match(tables.get('rpt_version'), /UNIQUE KEY/);
  assert.match(tables.get('rpt_export_job'), /idempotency_key/);
  assert.match(tables.get('rpt_export_job'), /lease_until/);
});
/** 验证当前测试标题所声明的报表行为，使用隔离合成数据。 */
test('authorized update drops only the explicit unused Jimu table list', () => {
  const path = 'script/sql/update/2026-10-14-report-p0.sql';
  assert.equal(existsSync(resolve(root, path)), true, 'P0 update exists');
  const update = read(path);
  const drops = [...update.matchAll(/DROP TABLE IF EXISTS `([^`]+)`/g)].map((x) => x[1]);
  assert.deepEqual(drops.sort(), [...legacyTables].sort());
  assert.doesNotMatch(update, /DROP (?:DATABASE|SCHEMA)/i);
  assert.match(update, /CREATE TABLE IF NOT EXISTS `rpt_definition`/);
});

/**
 * 保存当前页面或测试的knownLegacyMenuIds状态。
 */
const knownLegacyMenuIds = ['3020', '3021', '3022', '2053846181246812161'];
/**
 * 保存当前页面或测试的updatePath状态。
 */
const updatePath = 'script/sql/update/2026-10-14-report-p0.sql';
/** 提供报表 `sqlList` 操作入口。 */
const sqlList = (text) => {
  const values = [];
  let start = 0,
    depth = 0,
    quoted = false;
  for (let i = 0; i < text.length; i++) {
    if (quoted && text[i] === '\\') {
      i++;
      continue;
    }
    if (text[i] === "'") {
      if (quoted && text[i + 1] === "'") {
        i++;
        continue;
      }
      quoted = !quoted;
    }
    if (quoted) continue;
    if (text[i] === '(') depth++;
    if (text[i] === ')') depth--;
    if (text[i] === ',' && depth === 0) {
      values.push(text.slice(start, i).trim());
      start = i + 1;
    }
  }
  values.push(text.slice(start).trim());
  return values;
};
/** 提供报表 `sqlLiteral` 操作入口。 */
const sqlLiteral = (text) => (text === 'NULL' ? null : text.startsWith("'") ? text.slice(1, -1).replace(/''/g, "'") : text);
/** 提供报表 `seedRows` 操作入口。 */
const seedRows = (schema, table) => {
  const rows = [];
  let columns;
  for (const source of schema.split(/\r?\n/)) {
    let line = source;
    if (line.startsWith('INSERT INTO ' + String.fromCharCode(96) + table + String.fromCharCode(96))) {
      const header = line.match(/^INSERT INTO .+? \((.*?)\) VALUES\s*(.*)$/);
      assert.ok(header, 'seed INSERT has explicit columns');
      columns = sqlList(header[1]).map((x) => x.replace(/\x60/g, ''));
      line = header[2];
      if (!line) continue;
    } else if (!columns) continue;
    const row = line.match(/^\((.*)\)[,;]$/);
    if (!row) {
      columns = undefined;
      continue;
    }
    const values = sqlList(row[1]).map(sqlLiteral);
    rows.push(Object.fromEntries(columns.map((column, i) => [column, values[i]])));
    if (line.endsWith(';')) columns = undefined;
  }
  return rows;
};
/** 提供报表 `nativeMenus` 操作入口。 */
const nativeMenus = (sql) =>
  [...sql.matchAll(/^INSERT INTO sys_menu \((.*?)\) SELECT (.*?) FROM DUAL /gm)].map((match) => {
    const values = sqlList(match[2]).map(sqlLiteral);
    return Object.fromEntries(sqlList(match[1]).map((column, i) => [column, values[i]]));
  });
/** 提供报表 `legacyUrl` 操作入口。 */
const legacyUrl = (path) => /\/(?:jmreport|jmubi|jimubi|drag|jmreport-designer)\//.test(path || '');
/** 提供报表 `like` 操作入口。 */
const like = (value, pattern) =>
  new RegExp(
    '^' +
      pattern
        .split('%')
        .map((part) => part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
        .join('.*') +
      '$'
  ).test(value || '');

/** 在不连接数据库的情况下解释迁移脚本中的套餐查询表达式。 */
/** 此测试解释器仅支持当前迁移脚本需要的函数。 */
const sqlExpression = (expression, bindings) => {
  const text = expression.trim();
  if (Object.hasOwn(bindings, text)) return bindings[text];
  if (/^-?\d+$/.test(text)) return Number(text);
  if (/^'(?:[^']|'')*'$/.test(text)) return sqlLiteral(text);
  const call = text.match(/^(CHAR_LENGTH|REPLACE|SUBSTRING_INDEX|TRIM)\(([\s\S]*)\)$/i);
  assert.ok(call, 'supported SQL expression: ' + text);
  const args = sqlList(call[2]).map((arg) => sqlExpression(arg, bindings));
  switch (call[1].toUpperCase()) {
    case 'CHAR_LENGTH':
      return args[0].length;
    case 'REPLACE':
      return args[0].split(args[1]).join(args[2]);
    case 'TRIM':
      return args[0].trim();
    case 'SUBSTRING_INDEX': {
      const parts = args[0].split(args[1]);
      return (args[2] >= 0 ? parts.slice(0, args[2]) : parts.slice(args[2])).join(args[1]);
    }
  }
};
/** 提供报表 `cleanupFixture` 操作入口。 */
const cleanupFixture = (sql, input) => {
  const state = structuredClone(input);
  const seed = sql.match(/INSERT INTO rpt_p0_legacy_menu_ids\s*\(menu_id\)\s*VALUES\s*([\s\S]*?);/);
  assert.ok(seed, 'known legacy IDs must survive independently of sys_menu');
  const ids = new Set([...seed[1].matchAll(/\((\d+)\)/g)].map((match) => match[1]));
  const discovery = sql.match(/INSERT IGNORE INTO rpt_p0_legacy_menu_ids\s*\(menu_id\)\s*SELECT m\.menu_id FROM sys_menu m\s*WHERE ([\s\S]*?);/);
  assert.ok(discovery, 'capture URL IDs before deleting their menu rows');
  const patterns = [...discovery[1].matchAll(/m\.path LIKE '([^']+)'/g)].map((match) => match[1]);
  assert.ok(patterns.length > 0, 'legacy URL predicates are present');
  for (const menu of state.menus) if (patterns.some((pattern) => like(menu.path, pattern))) ids.add(menu.menu_id);

  const projection = sql.match(/CASE WHEN legacy\.menu_id IS NULL THEN\s*([\s\S]*?)\s*END\s*ORDER BY sequence\.n SEPARATOR ','/);
  assert.ok(projection, 'aggregate all surviving tokens in their original order');
  const join = sql.match(
    /ON CAST\(legacy\.menu_id AS CHAR CHARACTER SET utf8mb4\) COLLATE utf8mb4_unicode_ci =\s*([\s\S]*?)\s*WHERE p\.menu_ids IS NOT NULL\s*GROUP BY p\.package_id;/
  );
  assert.ok(join, 'filter only exact legacy IDs and group once per package');
  assert.match(sql, /sequence\.n <= CHAR_LENGTH\(p\.menu_ids\) -\s*CHAR_LENGTH\(REPLACE\(p\.menu_ids, ',', ''\)\) \+ 1/);
  assert.match(sql, /SELECT ones\.n \+ tens\.n \* 10 \+ hundreds\.n \* 100 \+ thousands\.n \* 1000 \+ 1 AS n/);
  for (const alias of ['ones', 'tens', 'hundreds']) {
    assert.match(
      sql,
      new RegExp(
        '\\(SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4\\s*UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9\\) ' +
          alias
      )
    );
  }
  assert.match(sql, /\(SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2\) thousands/);
  assert.match(sql, /SET SESSION group_concat_max_len = GREATEST\(@@SESSION\.group_concat_max_len, 12000\);/);
  assert.match(sql, /SET SESSION group_concat_max_len = @rpt_p0_group_concat_max_len;/);
  assert.match(
    sql,
    /UPDATE sys_tenant_package p JOIN rpt_p0_package_menus cleaned ON cleaned\.package_id = p\.package_id\s*SET p\.menu_ids = cleaned\.menu_ids;/
  );
  assert.match(sql, /DELETE rm FROM sys_role_menu rm JOIN rpt_p0_legacy_menu_ids legacy ON legacy\.menu_id = rm\.menu_id;/);
  assert.match(sql, /DELETE m FROM sys_menu m JOIN rpt_p0_legacy_menu_ids legacy ON legacy\.menu_id = m\.menu_id;/);
  assert.ok(
    sql.indexOf('SET p.menu_ids = cleaned.menu_ids;') < sql.indexOf('DELETE m FROM sys_menu'),
    'clean package references before deleting menu rows'
  );

  for (const entry of state.packages) {
    if (entry.menu_ids === null) continue;
    const bindings = { 'p.menu_ids': entry.menu_ids };
    const tokenCount = entry.menu_ids.length - entry.menu_ids.replaceAll(',', '').length + 1;
    const kept = [];
    for (let n = 1; n <= Math.min(tokenCount, 3000); n++) {
      bindings['sequence.n'] = n;
      if (!ids.has(sqlExpression(join[1], bindings))) kept.push(sqlExpression(projection[1], bindings));
    }
    entry.menu_ids = kept.join(',');
  }
  state.grants = state.grants.filter((grant) => !ids.has(grant.menu_id));
  state.menus = state.menus.filter((menu) => !ids.has(menu.menu_id));
  return state;
};
/** 提供报表 `sampleMenu` 操作入口。 */
const sampleMenu = (menu_id, path) => ({ menu_id, path });
/** 提供报表 `fixture` 操作入口。 */
const fixture = (menu_ids, menus = []) => ({ menus, grants: [], packages: [{ package_id: '1', menu_ids }] });

/** 验证当前测试标题所声明的报表行为，使用隔离合成数据。 */
test('bootstrap has one native report root and no legacy relative routes, URL menus or references', () => {
  const schema = read('script/sql/ry-vue.sql');
  const menus = [...seedRows(schema, 'sys_menu'), ...nativeMenus(schema)];
  assert.deepEqual(
    menus.filter((menu) => menu.parent_id === '0' && menu.path === 'report').map((menu) => menu.menu_id),
    ['23000']
  );
  for (const menu of menus) {
    assert.equal(knownLegacyMenuIds.includes(menu.menu_id), false, 'legacy menu ID ' + menu.menu_id);
    assert.equal(['reportDesign', 'reportDashboard'].includes(menu.path), false, 'legacy relative route');
    assert.equal(legacyUrl(menu.path), false, 'legacy URL menu ID ' + menu.menu_id);
  }
  for (const grant of seedRows(schema, 'sys_role_menu')) assert.equal(knownLegacyMenuIds.includes(grant.menu_id), false, 'legacy role reference');
  for (const entry of seedRows(schema, 'sys_tenant_package')) {
    for (const id of (entry.menu_ids || '').split(',')) assert.equal(knownLegacyMenuIds.includes(id.trim()), false, 'legacy package reference');
  }
});
/** 验证当前测试标题所声明的报表行为，使用隔离合成数据。 */
test('updater removes the old relative menu tree while preserving native and ERP/MES report menus', () => {
  const keep = [sampleMenu('23000', 'report'), sampleMenu('4001', '/erp/report/stock'), sampleMenu('4002', '/mes/report/quality')];
  const input = fixture('3020,23000,3021,4001,3022,4002', [
    sampleMenu('3020', 'report'),
    sampleMenu('3021', 'reportDesign'),
    sampleMenu('3022', 'reportDashboard'),
    ...keep
  ]);
  input.grants = input.menus.map((menu) => ({ role_id: '7', menu_id: menu.menu_id }));
  const result = cleanupFixture(read(updatePath), input);
  assert.deepEqual(result.menus, keep);
  assert.equal(result.packages[0].menu_ids, '23000,4001,4002');
  assert.deepEqual(
    result.grants.map((grant) => grant.menu_id),
    keep.map((menu) => menu.menu_id)
  );
  const roots = [
    ...result.menus,
    ...nativeMenus(read(updatePath)).filter((menu) => !result.menus.some((existing) => existing.menu_id === menu.menu_id))
  ];
  assert.deepEqual(
    roots.filter((menu) => menu.path === 'report').map((menu) => menu.menu_id),
    ['23000']
  );
});
/** 验证当前测试标题所声明的报表行为，使用隔离合成数据。 */
test('updater removes every discovered URL ID and preserves unrelated IDs and ordering', () => {
  const input = fixture('901,13020,3020,902,23000,903,3021,3022,2053846181246812161,777', [
    sampleMenu('901', '/jmreport/list'),
    sampleMenu('902', '/drag/view'),
    sampleMenu('903', '/jmubi/design'),
    sampleMenu('777', '/erp/report/list')
  ]);
  input.grants = ['901', '902', '903', '777', '3021'].map((menu_id) => ({ role_id: '8', menu_id }));
  const result = cleanupFixture(read(updatePath), input);
  assert.equal(result.packages[0].menu_ids, '13020,23000,777');
  assert.deepEqual(result.menus, [sampleMenu('777', '/erp/report/list')]);
  assert.deepEqual(result.grants, [{ role_id: '8', menu_id: '777' }]);
  assert.deepEqual(cleanupFixture(read(updatePath), result), result, 'second replay with legacy menus gone');
});
/** 验证当前测试标题所声明的报表行为，使用隔离合成数据。 */
test('replay repairs known orphan references when every old menu row is already gone', () => {
  const input = fixture('3022,3020,23000,3021,2053846181246812161', [sampleMenu('23000', 'report')]);
  input.grants = knownLegacyMenuIds.map((menu_id) => ({ role_id: '9', menu_id })).concat({ role_id: '9', menu_id: '23000' });
  const result = cleanupFixture(read(updatePath), input);
  assert.equal(result.packages[0].menu_ids, '23000');
  assert.deepEqual(result.grants, [{ role_id: '9', menu_id: '23000' }]);
  assert.deepEqual(cleanupFixture(read(updatePath), result), result);
});
/** 验证当前测试标题所声明的报表行为，使用隔离合成数据。 */
test('package cleanup handles NULL, empty, all-legacy, duplicate and boundary IDs', () => {
  const input = {
    menus: [],
    grants: [],
    packages: [null, '', '3020,3021,3022', '3020,3020,23000,3021,3021', '13020,30201,3022,23000,30221'].map((menu_ids, i) => ({
      package_id: String(i),
      menu_ids
    }))
  };
  const result = cleanupFixture(read(updatePath), input);
  assert.deepEqual(
    result.packages.map((entry) => entry.menu_ids),
    [null, '', '', '23000', '13020,30201,23000,30221']
  );
});
/** 验证当前测试标题所声明的报表行为，使用隔离合成数据。 */
test('package cleanup preserves lists beyond GROUP_CONCAT default and sequence 1000', () => {
  const keep = Array.from({ length: 1100 }, (_, i) => String(i % 10));
  const before = [...keep.slice(0, 1050), '3020', ...keep.slice(1050), '3021', '3022'].join(',');
  assert.ok(before.length > 1024 && before.length <= 3000);
  assert.equal(cleanupFixture(read(updatePath), fixture(before)).packages[0].menu_ids, keep.join(','));
});

/** 扩展DDL与主安装一致，所有新资源、执行和容量记录都有隔离或全局命名策略。 */
test('E2 to E7 extension tables and native menus match fresh install', () => {
  const schema = read('script/sql/ry-vue.sql'),
    increment = read('script/sql/update/2026-10-15-report-e2-e7.sql');
  const names = [...increment.matchAll(/CREATE TABLE IF NOT EXISTS `([^`]+)`/g)].map((match) => match[1]);
  assert.equal(names.length, 25);
  for (const name of names) {
    assert.ok(schema.includes('CREATE TABLE IF NOT EXISTS `' + name + '`'), name);
    assert.ok(increment.includes(name));
  }
  assert.doesNotMatch(increment, /DROP TABLE|DROP DATABASE|DELETE FROM/i);
  for (const path of ['query', 'pivot', 'assets', 'batch', 'dashboard', 'subscription', 'delivery']) {
    assert.ok(increment.includes("'report/" + path + "/index'"), path);
    assert.ok(existsSync(resolve(root, 'plus-ui/src/views/report/' + path + '/index.vue')), path);
  }
  assert.match(increment, /uk_rpt_subscription_occurrence/);
  assert.match(increment, /uk_rpt_delivery_execution/);
  assert.match(increment, /idx_rpt_manifest_recovery/);
});
