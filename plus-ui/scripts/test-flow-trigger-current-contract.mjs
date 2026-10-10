/** 流程显示的现行契约回归；旧草稿和已退役迁移不作为标准入口依赖。 */
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { test } from 'node:test';
import { build } from 'esbuild';
import { parse } from '@vue/compiler-sfc';

const root = new URL('../', import.meta.url);
const helperPath = new URL('src/views/ai/flow/flowTriggerDisplay.ts', root);
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
  assert.match(page.script, /import\s*\{[^}]*FLOW_TRIGGER_OPTIONS[^}]*flowTriggerLabel[^}]*\}\s*from\s*['"]\.\.\/flowTriggerDisplay['"]/);
});
test('history detail uses shared detailed labels instead of a manual default ternary', () => {
  const page = sfc('src/views/ai/flow/monitor/InstanceDetail.vue');
  assert.ok(find(page.template, (n) => n.type === 5 && n.content.content === 'flowTriggerLabel(instance.triggerType, true)').length);
  assert.match(page.script, /import\s*\{[^}]*flowTriggerLabel[^}]*\}\s*from\s*['"]\.\.\/flowTriggerDisplay['"]/);
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
  assert.ok(scripts['test:full-flow-ui'].split(/\s+/).includes('scripts/test-flow-trigger-current-contract.mjs'));
});

/** 工厂是新增受保护系统图的唯一来源，不依赖已退役的一次性名称迁移。 */
function backendSource(file) {
  return readFileSync(new URL(`../../${file}`, import.meta.url), 'utf8');
}
const factoryPath = 'ruoyi-modules/ruoyi-flow/src/main/java/com/nexus/flow/adapter/DecisionFlowDefinitionFactory.java';

/** 对真正运行的工厂常量严格匹配，不接受交换分析／执行身份或退回技术名展示。 */
function assertFactoryNames(factory) {
  for (const [constant, value] of [
    ['ANALYSIS_KEY', 'system_delivery_risk_analysis_v1'],
    ['EXECUTION_KEY', 'system_delivery_risk_execution_v1'],
    ['ANALYSIS_NAME', '工业交付风险分析'],
    ['EXECUTION_NAME', '工业交付风险执行']
  ]) {
    assert.ok(factory.includes(`public static final String ${constant} = "${value}";`), `invalid factory constant ${constant}`);
  }
}
test('current protected flow factory keeps Chinese display names and stable system keys', () => {
  assertFactoryNames(backendSource(factoryPath));
});
test('name contract rejects technical names and swapped protected identities', () => {
  const factory = backendSource(factoryPath);
  for (const invalid of [
    factory.replace('"工业交付风险分析"', '"system_delivery_risk_analysis_v1"'),
    factory.replace('ANALYSIS_KEY = "system_delivery_risk_analysis_v1"', 'ANALYSIS_KEY = "system_delivery_risk_execution_v1"'),
    factory.replace('"工业交付风险执行"', '"工业交付风险分析"')
  ])
    assert.throws(() => assertFactoryNames(invalid));
});
/** 当前安装表仍须保留文字租户身份及系统图版本，不以删掉迁移为结构升级证明。 */
test('current installation preserves text tenant identity and protected flow persistence fields', () => {
  const sql = backendSource('script/sql/ry-vue.sql');
  const match = sql.match(/CREATE TABLE `ai_flow`\s*\(([\s\S]*?)\n\) ENGINE[^;]*;/);
  assert.ok(match, 'actual ai_flow installation definition missing');
  assert.match(match[1], /`tenant_id`\s+varchar\(20\)(?:\s+COLLATE\s+\w+)?[^\n]*DEFAULT '000000'/);
  for (const column of ['flow_key', 'flow_name', 'config_json', 'status', 'trigger_type', 'version']) {
    assert.ok(match[1].includes('`' + column + '`'), `missing persisted flow field ${column}`);
  }
});
test('normal unit entry uses the tracked current-contract suite rather than a retired migration dependency', () => {
  const scripts = JSON.parse(source('package.json')).scripts;
  const command = scripts['test:full-flow-ui'].split(/\s+/);
  assert.ok(command.includes('scripts/test-flow-trigger-current-contract.mjs'));
  assert.ok(!command.includes('scripts/test-flow-trigger-display.mjs'));
});
