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

/** 仅解析安装契约用到的建表结构；IF NOT EXISTS 不改变表和列的含义。 */
function tableDefinitions(schema) {
  return new Map(
    [...schema.matchAll(/^CREATE TABLE(?: IF NOT EXISTS)?\s+`([^`]+)`\s*\(([\s\S]*?)\)\s*ENGINE\b/gim)].map((match) => [match[1], match[2]])
  );
}

function tableColumns(body) {
  return sqlList(body).flatMap((definition) => {
    const column = /^`([^`]+)`\s+/.exec(definition);
    return column ? [column[1]] : [];
  });
}

/** UNIQUE KEY 与 UNIQUE INDEX 等价，但唯一性及有序键列必须一致。 */
function tableIndexes(body) {
  const indexes = new Map();
  const columns = tableColumns(body);
  for (const definition of sqlList(body)) {
    if (!/^(?:PRIMARY|UNIQUE|KEY|INDEX)\b/i.test(definition)) continue;
    const match = /^(PRIMARY\s+KEY|UNIQUE\s+(?:KEY|INDEX)|KEY|INDEX)\s*(?:`([^`]+)`)?\s*\(([\s\S]*)\)(?:\s+USING\s+\w+)?$/i.exec(definition);
    assert.ok(match, '索引声明必须能完整解析');
    const name = match[2] || 'PRIMARY';
    assert.equal(indexes.has(name), false, `索引名称不得重复：${name}`);
    const keyColumns = sqlList(match[3]).map((column) => {
      const identifier = /^(?:`([^`]+)`|(\w+))(?:\s+(?:ASC|DESC))?$/i.exec(column);
      assert.ok(identifier, `索引 ${name} 必须使用完整列，不能借前缀或表达式冒充`);
      const field = identifier[1] || identifier[2];
      assert.ok(columns.includes(field), `索引 ${name} 引用了未声明列 ${field}`);
      return field;
    });
    indexes.set(name, { unique: /^(?:PRIMARY|UNIQUE)\b/i.test(match[1]), columns: keyColumns });
  }
  return indexes;
}

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
  assertCoreTables(read('script/sql/ry-vue.sql'));
});

/** 同一语义契约用于安装文件与合成语法、负例夹具，不执行 SQL。 */
function assertCoreTables(schema) {
  const tables = tableDefinitions(schema);
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
  assert.ok(tableColumns(tables.get('rpt_definition')).includes('draft_revision'));
  assert.ok(
    [...tableIndexes(tables.get('rpt_version')).values()].some(
      (index) => index.unique && JSON.stringify(index.columns) === JSON.stringify(['tenant_id', 'report_id', 'version_no'])
    ),
    '报表版本必须按租户、报表及版本号唯一，不能由无关索引替代'
  );
  for (const column of ['idempotency_key', 'lease_until']) assert.ok(tableColumns(tables.get('rpt_export_job')).includes(column), column);
}
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
function sqlList(text) {
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
  assert.equal(quoted, false, 'SQL 列表中的字符串必须闭合');
  assert.equal(depth, 0, 'SQL 列表中的括号必须配对');
  values.push(text.slice(start).trim());
  return values;
}
/** 提供报表 `sqlLiteral` 操作入口。 */
function sqlLiteral(text) {
  if (text.toUpperCase() === 'NULL') return null;
  if (!text.startsWith("'")) return text;
  return text.slice(1, -1).replace(/''|\\([\s\S])/g, (escaped, character) => {
    if (escaped === "''") return "'";
    return { '0': '\0', b: '\b', n: '\n', r: '\r', t: '\t', Z: '\x1a' }[character] ?? character;
  });
}

/** 显式列按列名映射；完整 VALUES 行只能使用实际建表列序，拒绝猜测或截断。 */
function seedRows(schema, table) {
  const body = tableDefinitions(schema).get(table);
  const declaredColumns = body ? tableColumns(body) : null;
  const rows = [];
  const statements = new RegExp(
    '^INSERT\\s+INTO\\s+`?' + table + "`?\\s*(?:\\(([^)]*)\\))?\\s*VALUES\\s*((?:'(?:''|\\\\.|[^'\\\\])*'|[^';])*);",
    'gmi'
  );
  for (const statement of schema.matchAll(statements)) {
    const columns = statement[1]
      ? sqlList(statement[1]).map((column) => {
          const name = /^(?:`([^`]+)`|(\w+))$/.exec(column);
          assert.ok(name, `${table} 显式列名必须完整解析`);
          return name[1] || name[2];
        })
      : declaredColumns;
    assert.ok(columns?.length, `${table} 完整 VALUES 行必须有实际建表列序`);
    assert.equal(new Set(columns).size, columns.length, `${table} 显式列不得重复`);
    if (declaredColumns) for (const column of columns) assert.ok(declaredColumns.includes(column), `${table} 未声明列 ${column}`);
    const tuplePattern = /\((?:'(?:''|\\.|[^'\\])*'|[^'()])*\)/g;
    const tuples = [...statement[2].matchAll(tuplePattern)];
    assert.ok(tuples.length > 0, `${table} VALUES 必须包含数据行`);
    let previousEnd = 0;
    for (const [index, tuple] of tuples.entries()) {
      const separator = statement[2].slice(previousEnd, tuple.index).trim();
      assert.ok(separator === (index === 0 ? '' : ','), `${table} VALUES 行间必须使用单个逗号，不得残留表达式`);
      previousEnd = tuple.index + tuple[0].length;
    }
    assert.ok(statement[2].slice(previousEnd).trim() === '', `${table} VALUES 结尾不得残留逗号或表达式`);
    for (const tuple of tuples) {
      const literals = sqlList(tuple[0].slice(1, -1));
      assert.equal(literals.length, columns.length, `${table} 行值数量必须与列数量一致`);
      assert.ok(
        literals.every((literal) => /^(?:NULL|-?\d+(?:\.\d+)?|'(?:''|\\.|[^'\\])*')$/i.test(literal)),
        `${table} 种子值只能使用受支持的字面量`
      );
      rows.push(Object.fromEntries(columns.map((column, index) => [column, sqlLiteral(literals[index])])));
    }
  }
  return rows;
}
/** 解析已授权迁移中的具名列 SELECT 种子，仅读取元数据。 */
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
  assertFreshMenus(read('script/sql/ry-vue.sql'));
});

/** 同一语义契约用于安装文件与合成语法、负例夹具，不执行 SQL。 */
function assertFreshMenus(
  schema,
  expectedMenus = [...nativeMenus(read(updatePath)), ...nativeMenus(read('script/sql/update/2026-10-15-report-e2-e7.sql'))]
) {
  const menus = [...seedRows(schema, 'sys_menu'), ...nativeMenus(schema)];
  assert.equal(new Set(menus.map((menu) => menu.menu_id)).size, menus.length, '菜单 ID 必须唯一');
  for (const expected of expectedMenus) {
    const actual = menus.find((menu) => menu.menu_id === expected.menu_id);
    assert.ok(actual, `缺少原生报表菜单 ${expected.menu_id}`);
    for (const field of ['parent_id', 'path', 'component', 'menu_type', 'perms'])
      assert.equal(actual[field], expected[field], `菜单 ${expected.menu_id} 的 ${field} 必须保留实际契约`);
  }
  assert.deepEqual(
    menus.filter((menu) => menu.parent_id === '0' && menu.path === 'report').map((menu) => menu.menu_id),
    ['23000']
  );
  for (const menu of menus) {
    assert.equal(knownLegacyMenuIds.includes(menu.menu_id), false, 'legacy menu ID ' + menu.menu_id);
    assert.equal(['reportDesign', 'reportDashboard'].includes(menu.path), false, 'legacy relative route');
    assert.equal(legacyUrl(menu.path), false, 'legacy URL menu ID ' + menu.menu_id);
  }
  for (const grant of seedRows(schema, 'sys_role_menu')) {
    assert.ok(Object.hasOwn(grant, 'menu_id'), '角色菜单种子必须保留 menu_id 列');
    assert.equal(knownLegacyMenuIds.includes(grant.menu_id), false, 'legacy role reference');
  }
  for (const entry of seedRows(schema, 'sys_tenant_package')) {
    assert.ok(Object.hasOwn(entry, 'menu_ids'), '租户套餐种子必须保留 menu_ids 列');
    for (const id of (entry.menu_ids || '').split(',')) assert.equal(knownLegacyMenuIds.includes(id.trim()), false, 'legacy package reference');
  }
}
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
  assertExtensions(read('script/sql/ry-vue.sql'), read('script/sql/update/2026-10-15-report-e2-e7.sql'));
});

/** 同一语义契约用于安装文件与合成语法、负例夹具，不执行 SQL。 */
function assertExtensions(schema, increment) {
  const names = [...increment.matchAll(/CREATE TABLE IF NOT EXISTS `([^`]+)`/g)].map((match) => match[1]);
  assert.equal(names.length, 25);
  const installedTables = tableDefinitions(schema);
  const expectedTables = tableDefinitions(increment);
  for (const name of names) {
    const installed = installedTables.get(name);
    const expected = expectedTables.get(name);
    assert.ok(installed, `安装 SQL 缺少扩展表 ${name}`);
    assert.deepEqual(tableColumns(installed).sort(), tableColumns(expected).sort(), `${name} 列集合必须与增量一致`);
    const installedIndexes = tableIndexes(installed);
    for (const [indexName, index] of tableIndexes(expected))
      assert.deepEqual(installedIndexes.get(indexName), index, `${name}.${indexName} 的唯一性及键列必须与增量一致`);
  }
  assert.doesNotMatch(increment, /DROP TABLE|DROP DATABASE|DELETE FROM/i);
  for (const path of ['query', 'pivot', 'assets', 'batch', 'dashboard', 'subscription', 'delivery']) {
    assert.ok(increment.includes("'report/" + path + "/index'"), path);
    assert.ok(existsSync(resolve(root, 'plus-ui/src/views/report/' + path + '/index.vue')), path);
  }
  assert.match(increment, /uk_rpt_subscription_occurrence/);
  assert.match(increment, /uk_rpt_delivery_execution/);
  assert.match(increment, /idx_rpt_manifest_recovery/);
}

/** 合成数据只保留本契约用到的字段，涵盖当前导出及历史显式列种子。 */
const menuFixtureColumns = ['menu_id', 'menu_name', 'parent_id', 'path', 'component', 'menu_type', 'perms'];
const expectedFixtureMenus = [
  { menu_id: '23000', parent_id: '0', path: 'report', component: null, menu_type: 'M', perms: null },
  { menu_id: '23001', parent_id: '23000', path: 'definition', component: 'report/definition/index', menu_type: 'C', perms: 'report:definition:list' }
];
function menuFixture(explicit = true) {
  const header = (table, columns) => `INSERT INTO \`${table}\`${explicit ? ` (${columns.map((column) => `\`${column}\``).join(',')})` : ''} VALUES\n`;
  return [
    'CREATE TABLE `sys_menu` (`menu_id` bigint, `menu_name` varchar(50), `parent_id` bigint, `path` varchar(200), `component` varchar(255), `menu_type` char(1), `perms` varchar(100)) ENGINE=InnoDB;',
    header('sys_menu', menuFixtureColumns) +
      "(23000,'native',0,'report',NULL,'M',NULL),\n(23001,'definition',23000,'definition','report/definition/index','C','report:definition:list');",
    'CREATE TABLE `sys_role_menu` (`role_id` bigint, `menu_id` bigint) ENGINE=InnoDB;',
    header('sys_role_menu', ['role_id', 'menu_id']) + '(7,23000);',
    'CREATE TABLE `sys_tenant_package` (`package_id` bigint, `menu_ids` varchar(3000)) ENGINE=InnoDB;',
    header('sys_tenant_package', ['package_id', 'menu_ids']) + "(1,'23000,23001');"
  ].join('\n');
}

function coreFixture(indexKeyword = 'KEY', ifNotExists = false) {
  return ['rpt_catalog', 'rpt_definition', 'rpt_version', 'rpt_dataset', 'rpt_dataset_version', 'rpt_acl', 'rpt_run', 'rpt_export_job', 'rpt_audit']
    .map((name) => {
      const fields = ['`tenant_id` varchar(20) NOT NULL'];
      if (name === 'rpt_definition') fields.push('`draft_revision` bigint NOT NULL');
      if (name === 'rpt_version')
        fields.push(
          '`report_id` bigint NOT NULL',
          '`version_no` int NOT NULL',
          `UNIQUE ${indexKeyword} \`uk_report_version\` (\`tenant_id\`,\`report_id\`,\`version_no\`)`
        );
      if (name === 'rpt_export_job') fields.push('`idempotency_key` varchar(80) NOT NULL', '`lease_until` datetime NULL');
      return `CREATE TABLE ${ifNotExists ? 'IF NOT EXISTS ' : ''}\`${name}\` (${fields.join(',')}) ENGINE=InnoDB;`;
    })
    .join('\n');
}

test('schema parser accepts UNIQUE KEY/INDEX and optional fresh-install IF NOT EXISTS without changing constraints', () => {
  for (const keyword of ['KEY', 'INDEX']) for (const conditional of [false, true]) assertCoreTables(coreFixture(keyword, conditional));
});

test('version uniqueness must cover tenant/report/version, not a nonunique or unrelated key', () => {
  const schema = coreFixture();
  for (const mutation of [
    schema.replace('UNIQUE KEY', 'KEY'),
    schema.replace('(`tenant_id`,`report_id`,`version_no`)', '(`report_id`,`version_no`)'),
    schema.replace('(`tenant_id`,`report_id`,`version_no`)', '(`tenant_id`,`report_id`)')
  ])
    assert.throws(() => assertCoreTables(mutation), assert.AssertionError);
});

test('SQL compatibility cannot hide nullable tenant ownership or missing revision/task columns', () => {
  const schema = coreFixture();
  for (const mutation of [
    schema.replace('`tenant_id` varchar(20) NOT NULL', '`tenant_id` varchar(20) NULL'),
    schema.replace('`draft_revision`', '`wrong_revision`'),
    schema.replace('`idempotency_key`', '`wrong_request_key`'),
    schema.replace('`lease_until`', '`wrong_lease`')
  ])
    assert.throws(() => assertCoreTables(mutation), assert.AssertionError);
});

test('VALUES parser maps full rows and reordered explicit-column rows to declared table columns', () => {
  const schema = menuFixture(false);
  const columns = '`path`,`menu_id`,`parent_id`,`menu_name`,`component`,`menu_type`,`perms`';
  const explicit = schema.replace(
    /INSERT INTO `sys_menu` VALUES[\s\S]*?;(?=\nCREATE TABLE)/,
    `INSERT INTO \`sys_menu\` (${columns}) VALUES\n('report',23000,0,'native',NULL,'M',NULL),\n('definition',23001,23000,'definition','report/definition/index','C','report:definition:list');`
  );
  assert.deepEqual(seedRows(schema, 'sys_menu'), seedRows(explicit, 'sys_menu'));
  assert.equal(seedRows(schema, 'sys_menu').length, 2);
  assertFreshMenus(schema, expectedFixtureMenus);
  assertFreshMenus(explicit, expectedFixtureMenus);
});

test('VALUES parser preserves quoted commas, semicolons, parentheses and escaped apostrophes', () => {
  const schema =
    'CREATE TABLE `sys_menu` (`menu_id` bigint, `menu_name` varchar(80)) ENGINE=InnoDB;\n' +
    "INSERT INTO `sys_menu` VALUES (23000,'a, b; (c) and ''quoted'''),(23001,'a\\'b');";
  assert.deepEqual(seedRows(schema, 'sys_menu'), [
    { menu_id: '23000', menu_name: "a, b; (c) and 'quoted'" },
    { menu_id: '23001', menu_name: "a'b" }
  ]);
});

test('VALUES parser rejects unknown/duplicate columns, wrong row widths and missing implicit table order', () => {
  const schema = menuFixture();
  const rows = 'INSERT INTO `sys_menu` VALUES (23000,0);';
  for (const mutation of [
    schema.replace('(`menu_id`,`menu_name`', '(`wrong_id`,`menu_name`'),
    schema.replace('(`menu_id`,`menu_name`', '(`menu_id`,`menu_id`'),
    schema.replace("(23000,'native',0,'report',NULL,'M',NULL)", "(23000,'native',0,'report',NULL,'M')"),
    rows,
    schema.replace('NULL),\n(23001', 'NULL),,\n(23001')
  ])
    assert.throws(() => seedRows(mutation, 'sys_menu'), assert.AssertionError);
});

test('fresh native menu contract retains parent/path/component relationships and uniqueness', () => {
  const schema = menuFixture();
  assertFreshMenus(schema, expectedFixtureMenus);
  for (const mutation of [
    schema.replace("23000,'native',0", "23000,'native',7"),
    schema.replace("23001,'definition',23000", "23001,'definition',7"),
    schema.replace("'report/definition/index'", "'report/Definition/index'"),
    schema +
      "\nINSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`path`,`component`,`menu_type`,`perms`) VALUES\n(23001,'duplicate',23000,'definition','report/definition/index','C','report:definition:list');"
  ])
    assert.throws(() => assertFreshMenus(mutation, expectedFixtureMenus), assert.AssertionError);
});

test('legacy menu, URL, role and tenant-package references remain forbidden in fresh installation', () => {
  const schema = menuFixture();
  for (const mutation of [
    schema.replace("23001,'definition'", "3020,'definition'"),
    schema.replace("'definition','report/definition/index'", "'/jmreport/list','report/definition/index'"),
    schema.replace('(7,23000)', '(7,3020)'),
    schema.replace("(1,'23000,23001')", "(1,'23000,3020,23001')")
  ])
    assert.throws(() => assertFreshMenus(mutation, expectedFixtureMenus), assert.AssertionError);
});

test('extension fresh definitions accept equivalent CREATE and UNIQUE syntax while retaining declared columns and indexes', () => {
  const increment = read('script/sql/update/2026-10-15-report-e2-e7.sql');
  const currentStyle = increment.replaceAll('CREATE TABLE IF NOT EXISTS', 'CREATE TABLE').replaceAll('UNIQUE KEY', 'UNIQUE INDEX');
  assertExtensions(currentStyle, increment);
  assertExtensions(increment, increment);
});

test('extension equivalence rejects missing tables, wrong column sets or weakened unique indexes', () => {
  const increment = read('script/sql/update/2026-10-15-report-e2-e7.sql');
  for (const mutation of [
    increment.replace('CREATE TABLE IF NOT EXISTS `rpt_query_definition`', 'CREATE TABLE IF NOT EXISTS `wrong_query_definition`'),
    increment.replace('`tenant_id` varchar(20) NOT NULL', '`wrong_tenant` varchar(20) NOT NULL'),
    increment.replace('UNIQUE KEY `uk_rpt_subscription_occurrence`', 'KEY `uk_rpt_subscription_occurrence`'),
    increment.replace('(`tenant_id`,`execution_id`)', '(`execution_id`)')
  ]) {
    assert.ok(mutation !== increment, 'negative fixture must perform a real mutation');
    assert.throws(() => assertExtensions(mutation, increment), assert.AssertionError);
  }
});

test('format compatibility never permits destructive changes in the additive E2-E7 migration', () => {
  const increment = read('script/sql/update/2026-10-15-report-e2-e7.sql');
  for (const unsafe of ['DROP TABLE `rpt_delivery`;', 'DROP DATABASE example;', 'DELETE FROM rpt_delivery;']) {
    assert.throws(() => assertExtensions(increment, increment + '\n' + unsafe), assert.AssertionError);
  }
});
