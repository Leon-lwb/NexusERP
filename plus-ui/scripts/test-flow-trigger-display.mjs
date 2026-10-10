import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { test } from 'node:test';
import { build } from 'esbuild';
import { parse } from '@vue/compiler-sfc';

const root = new URL('../', import.meta.url);
const helperPath = new URL('src/views/ai/flow/flowDisplay.ts', root);
const migrationPath = new URL('../../script/sql/update/2026-10-08-industrial-flow-display-names-ry-vue.sql', import.meta.url);
let helperPromise;
async function helper() {
  assert.ok(existsSync(helperPath), 'a shared trigger dictionary must replace divergent inline labels');
  helperPromise ??= (async () => {
    const output = await build({
      entryPoints: [helperPath.pathname.replace(/^\/(\w:)/, '$1')],
      bundle: true,
      write: false,
      format: 'esm',
      platform: 'neutral',
      logLevel: 'silent'
    });
    return import(`data:text/javascript;base64,${Buffer.from(output.outputFiles[0].contents).toString('base64')}`);
  })();
  return helperPromise;
}
function source(path) {
  return readFileSync(new URL(path, root), 'utf8');
}
function sfc(path) {
  const text = source(path);
  const { descriptor, errors } = parse(text, { filename: path });
  assert.equal(errors.length, 0);
  return { text, template: descriptor.template.ast, script: descriptor.scriptSetup.content };
}
function find(node, predicate) {
  const result = [];
  function visit(current) {
    if (predicate(current)) result.push(current);
    for (const child of current.children ?? []) visit(child);
  }
  visit(node);
  return result;
}
function attribute(node, name) {
  return node.props.find((p) => p.type === 6 && p.name === name)?.value?.content;
}
function directive(node, name, argument) {
  return node.props.find((p) => p.type === 7 && p.name === name && (argument === undefined || p.arg?.content === argument))?.exp?.content;
}
test('trigger dictionary includes every currently emitted backend trigger, including DECISION', async () => {
  const { FLOW_TRIGGER_OPTIONS } = await helper();
  assert.deepEqual(
    FLOW_TRIGGER_OPTIONS.map((x) => x.value),
    ['MANUAL', 'SCHEDULE', 'TEST', 'WEBHOOK', 'DECISION']
  );
  assert.equal(new Set(FLOW_TRIGGER_OPTIONS.map((x) => x.value)).size, 5);
});
test('DECISION has a Chinese display value in both table and detail', async () => {
  const { flowTriggerLabel } = await helper();
  assert.equal(flowTriggerLabel('DECISION'), '工业决策');
  assert.equal(flowTriggerLabel('DECISION', true), '工业决策触发');
});
test('all old trigger labels remain correct and Webhook detail is not manual', async () => {
  const { flowTriggerLabel } = await helper();
  for (const [value, label, detail] of [
    ['MANUAL', '手动', '手动触发'],
    ['SCHEDULE', '定时', '定时触发'],
    ['TEST', '测试', '测试运行'],
    ['WEBHOOK', 'Webhook', 'Webhook触发']
  ]) {
    assert.equal(flowTriggerLabel(value), label);
    assert.equal(flowTriggerLabel(value, true), detail);
  }
});
test('empty triggers show a dash and unknown values are not mislabeled as MANUAL', async () => {
  const { flowTriggerLabel } = await helper();
  for (const value of [undefined, null, '']) {
    assert.equal(flowTriggerLabel(value), '-');
    assert.equal(flowTriggerLabel(value, true), '-');
  }
  assert.equal(flowTriggerLabel('FUTURE_EVENT'), 'FUTURE_EVENT');
  assert.equal(flowTriggerLabel('FUTURE_EVENT', true), 'FUTURE_EVENT');
});
test('history selector and trigger column use the same dictionary', () => {
  const page = sfc('src/views/ai/flow/monitor/index.vue');
  const select = find(page.template, (n) => n.type === 1 && n.tag === 'el-select' && directive(n, 'model') === 'query.triggerType')[0];
  assert.ok(select);
  const options = find(select, (n) => n.type === 1 && n.tag === 'el-option');
  assert.equal(options.length, 1, 'do not maintain another hardcoded partial option list');
  assert.match(directive(options[0], 'for') ?? '', /FLOW_TRIGGER_OPTIONS/);
  assert.equal(directive(options[0], 'bind', 'label'), 'option.label');
  assert.equal(directive(options[0], 'bind', 'value'), 'option.value');
  const column = find(page.template, (n) => n.type === 1 && n.tag === 'el-table-column' && attribute(n, 'label') === '触发方式')[0];
  assert.ok(column);
  assert.ok(find(column, (n) => n.type === 5 && n.content.content === 'flowTriggerLabel(row.triggerType)').length);
  assert.match(page.script, /import\s*\{[^}]*FLOW_TRIGGER_OPTIONS[^}]*flowTriggerLabel[^}]*\}\s*from\s*['"]\.\.\/flowDisplay['"]/);
});
test('history detail uses shared detailed labels instead of a manual default ternary', () => {
  const page = sfc('src/views/ai/flow/monitor/InstanceDetail.vue');
  assert.ok(find(page.template, (n) => n.type === 5 && n.content.content === 'flowTriggerLabel(instance.triggerType, true)').length);
  assert.match(page.script, /import\s*\{[^}]*flowTriggerLabel[^}]*\}\s*from\s*['"]\.\.\/flowDisplay['"]/);
  assert.doesNotMatch(page.text, /instance\.triggerType\s*===/);
});
test('instance DTO accepts DECISION without changing the wire values', () => {
  const api = source('src/api/ai/flow.ts');
  assert.match(api, /export type AiFlowTriggerType\s*=\s*'MANUAL'\s*\|\s*'SCHEDULE'\s*\|\s*'TEST'\s*\|\s*'WEBHOOK'\s*\|\s*'DECISION'/);
  assert.match(api, /triggerType:\s*AiFlowTriggerType/);
});
test('regression is registered in the normal unit entry', () => {
  const scripts = JSON.parse(source('package.json')).scripts;
  assert.match(scripts['test:unit'], /test:full-flow-ui/);
  assert.ok(scripts['test:full-flow-ui'].split(/\s+/).includes('scripts/test-flow-trigger-display.mjs'));
});
test('existing-name migration is tenant-bound, exact-name guarded and changes only display metadata', () => {
  assert.ok(existsSync(migrationPath), 'existing system flows need a replay-safe metadata migration');
  const text = readFileSync(migrationPath, 'utf8');
  const statements = [...text.matchAll(/UPDATE\s+ai_flow\s+SET\s+([\s\S]*?)\s+WHERE\s+([\s\S]*?);/gi)];
  assert.equal(statements.length, 2);
  for (const [, set, where] of statements) {
    assert.match(set, /flow_name\s*=/);
    assert.doesNotMatch(set, /\b(?:flow_key|config_json|version|status|trigger_type)\s*=/i);
    assert.match(where, /tenant_id\s*=\s*@target_tenant/);
    assert.match(where, /flow_name\s*=\s*(?:BINARY\s*)?'system_delivery_risk_(?:analysis|execution)_v1'/);
    assert.match(where, /flow_key\s+IN/i);
    assert.match(where, /CONCAT\(/i);
  }
  assert.doesNotMatch(text, /\b(?:DROP|DELETE|TRUNCATE|ALTER)\s+(?:TABLE|FROM)/i);
  assert.match(text, /工业交付风险分析/);
  assert.match(text, /工业交付风险执行/);
});

test('migration declares the matching connection and tenant-variable collation', () => {
  const text = readFileSync(migrationPath, 'utf8');
  assert.match(text, /SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;/);
  assert.match(text, /SET @target_tenant = _utf8mb4'000000' COLLATE utf8mb4_unicode_ci;/);
});
test('old names and keys use byte-exact guards so case variants and trailing spaces remain custom', () => {
  const text = readFileSync(migrationPath, 'utf8');
  const guards = [...text.matchAll(/UPDATE\s+ai_flow\s+SET\s+[\s\S]*?\s+WHERE\s+([\s\S]*?);/gi)];
  assert.equal(guards.length, 2);
  for (const [, where] of guards) {
    assert.match(where, /BINARY\s+flow_name\s*=\s*BINARY\s*'system_delivery_risk_(?:analysis|execution)_v1'/);
    assert.match(where, /BINARY\s+flow_key\s+IN/);
  }
});
