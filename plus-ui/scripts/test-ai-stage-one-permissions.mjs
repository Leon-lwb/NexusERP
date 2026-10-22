import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { test } from 'node:test';

const repositoryRoot = new URL('../../', import.meta.url);
const read = (path) => readFileSync(new URL(path, repositoryRoot), 'utf8');
const sql = read('script/sql/ry-vue.sql');
// Only inspect menu seeds; assertion failures must never print the initialization dump.
const menus = [...sql.matchAll(/^INSERT INTO `sys_menu` VALUES \((.*)\);\r?$/gm)].map((match) => {
  const fields = match[1].match(/'(?:''|\\.|[^'\\])*'|NULL|-?\d+/g).map((field) => (field.startsWith("'") ? field.slice(1, -1) : field));
  return {
    id: fields[0],
    parent: fields[2],
    order: fields[3],
    path: fields[4],
    component: fields[5],
    type: fields[9],
    status: fields[11],
    permission: fields[12]
  };
});
const expected = [
  { id: '1725', parent: '1702', permission: 'ai:model:export', component: 'ai/model/index', pagePath: 'model', pageParent: '1707' },
  { id: '1734', parent: '1703', permission: 'ai:provider:export', component: 'ai/model/provider', pagePath: 'provider', pageParent: '1707' },
  { id: '1745', parent: '1704', permission: 'ai:knowledge:export', component: 'ai/knowledge/index', pagePath: 'knowledge', pageParent: '1708' },
  { id: '1843', parent: '1831', permission: 'ai:flow:query', component: 'ai/flow/index', pagePath: 'index', pageParent: '1830' }
];

for (const entry of expected) {
  test(`${entry.permission} is a unique enabled button under its existing page, without a role grant`, () => {
    const matches = menus.filter((menu) => menu.permission === entry.permission);
    assert.equal(matches.length, 1, `${entry.permission} needs exactly one seed`);
    const [menu] = matches;
    assert.equal(menu.id, entry.id);
    assert.equal(menus.filter((candidate) => candidate.id === entry.id).length, 1, `menu ID ${entry.id} collision`);
    assert.equal(menu.parent, entry.parent);
    assert.equal(menu.type, 'F');
    assert.equal(menu.status, '0');
    const page = menus.find((candidate) => candidate.id === menu.parent);
    assert.equal(page.type, 'C');
    assert.equal(page.parent, entry.pageParent);
    assert.equal(page.path, entry.pagePath);
    assert.equal(page.component, entry.component);
    assert.ok(existsSync(new URL(`plus-ui/src/views/${page.component}.vue`, repositoryRoot)), 'existing page must exist');
    const grants = [...sql.matchAll(/INSERT INTO `?sys_role_menu`?(?:\s*\([^;]*?\))?\s+VALUES\s+([^;]*);/g)];
    assert.ok(
      !grants.some((grant) => new RegExp(`\\(\\s*\\d+\\s*,\\s*${entry.id}\\s*\\)`).test(grant[1])),
      'new permissions require explicit role authorization'
    );
  });
}

test('every protected AI and Flow controller permission can be assigned through menu seeds', () => {
  const registered = new Set(menus.map((menu) => menu.permission));
  const missing = [];
  let checked = 0;
  for (const directory of ['ruoyi-ai/src/main/java/', 'ruoyi-flow/src/main/java/']) {
    const folder = new URL(`ruoyi-modules/${directory}`, repositoryRoot);
    for (const file of readdirSync(folder, { recursive: true }).filter((name) => name.endsWith('.java'))) {
      const source = readFileSync(new URL(file.replaceAll('\\', '/'), folder), 'utf8');
      if (!source.includes('@RestController')) continue;
      for (const annotation of source.matchAll(/@SaCheckPermission\(\s*(?:value\s*=\s*)?("[^"]+"|\{[^}]+\})/g)) {
        for (const permission of annotation[1].matchAll(/"(ai:[^"]+)"/g)) {
          checked++;
          if (!registered.has(permission[1])) missing.push(`${file}: ${permission[1]}`);
        }
      }
    }
  }
  assert.ok(checked > 90, `nonzero protected endpoint coverage: ${checked}`);
  assert.deepEqual([...new Set(missing)].sort(), []);
});

test('migration is additive, guarded for exact parents and conflicts, and does not grant roles', () => {
  const path = new URL('script/sql/update/2026-10-22-ai-stage-one-permissions.sql', repositoryRoot);
  assert.ok(existsSync(path), 'reviewable migration must exist; never execute it in tests');
  const migration = readFileSync(path, 'utf8').replace(/^--.*$/gm, '');
  assert.ok(!/\b(?:UPDATE|DELETE|DROP|TRUNCATE|REPLACE|ALTER|CREATE|CALL)\b/i.test(migration), 'no schema or existing record mutations');
  assert.ok(!/\bsys_role_menu\b|\bsys_tenant_package\b/i.test(migration), 'no automatic role/package grant');
  const inserts = [...migration.matchAll(/INSERT INTO sys_menu\s*\([^;]*?\)\s*SELECT\s+([^;]+);/g)];
  assert.equal(inserts.length, 4);
  for (const entry of expected) {
    const insert = inserts.find((candidate) => new RegExp(`^${entry.id},`).test(candidate[1]));
    assert.ok(insert, `missing migration for ${entry.permission}`);
    const body = insert[1];
    assert.ok(body.includes(`'${entry.permission}'`));
    assert.ok(body.includes(`parent.menu_id = ${entry.parent}`));
    assert.ok(body.includes(`parent.parent_id = ${entry.pageParent}`));
    assert.ok(body.includes("parent.menu_type = 'C'"));
    assert.ok(body.includes("parent.status = '0'"));
    assert.ok(body.includes(`BINARY parent.path = BINARY '${entry.pagePath}'`));
    assert.ok(body.includes(`BINARY parent.component = BINARY '${entry.component}'`));
    assert.ok(body.includes('NOT EXISTS'));
    assert.ok(body.includes(`current_menu.menu_id = ${entry.id}`));
    assert.ok(body.includes(`OR BINARY current_menu.perms = BINARY '${entry.permission}'`));
    const seed = menus.find((menu) => menu.id === entry.id);
    assert.ok(seed, 'new install and migration must agree');
    assert.ok(
      new RegExp(`^${entry.id}, '[^']+', ${entry.parent}, ${seed.order}, '', '', '', 1, 0,\\s*'F', '0', '0', '${entry.permission}'`).test(body),
      `migration fields differ for ${entry.permission}`
    );
  }
});
