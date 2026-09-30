import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { compileScript, parse } from '@vue/compiler-sfc';
import { createSSRApp, h } from 'vue';
import { renderToString } from 'vue/server-renderer';

const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(import.meta.url);
const warning = '—（单位待核）';
const evidence = [
  { gap: '14.659', unit: '千克' },
  { gap: '5.4292', unit: '个' },
  { gap: '17.9165', unit: '千克' },
  { gap: '0.2715', unit: '升' }
];
/** 上报的汇总值混合了不兼容的计量单位，不能视为件数。 */
assert.equal(new Set(evidence.map((line) => line.unit)).size, 3);
const unsafeTotal = evidence.reduce((sum, line) => sum + Number(line.gap), 0).toFixed(4);
assert.equal(unsafeTotal, '38.2762');

const kpis = {
  delayedWorkOrderCount: 2,
  totalTardinessDays: 3,
  shortageMaterialCount: 4,
  totalShortageGap: unsafeTotal,
  maxResourceUtilization: '80',
  changedWorkOrderCount: 1,
  actionCost: '5'
};
const scenario = {
  scenarioCode: 'APS_EDD',
  scenarioName: '最早交期',
  feasible: true,
  rank: 1,
  kpis,
  evidenceKeys: [],
  schedulePreview: []
};

async function component(name) {
  const file = resolve(uiRoot, `src/components/ai/decision/${name}.vue`);
  const { descriptor, errors } = parse(readFileSync(file, 'utf8'), { filename: file });
  assert.deepEqual(errors, []);
  const compiled = compileScript(descriptor, { id: `test-${name}`, inlineTemplate: true });
  const result = await build({
    stdin: { contents: compiled.content, resolveDir: dirname(file), sourcefile: file, loader: 'ts' },
    alias: { '@': resolve(uiRoot, 'src') },
    bundle: true,
    platform: 'node',
    format: 'cjs',
    external: ['vue'],
    write: false,
    logLevel: 'silent'
  });
  const module = { exports: {} };
  new Function('require', 'module', 'exports', result.outputFiles[0].text)(require, module, module.exports);
  return module.exports.default;
}

const children = (_props, { slots }) => h('section', slots.default?.());
const table = {
  props: ['data'],
  setup(props, { slots }) {
    return () => {
      const columns = slots.default?.() ?? [];
      return h(
        'table',
        props.data.map((row) =>
          h(
            'tr',
            columns.map((column) =>
              h(
                'td',
                {
                  'data-label': column.props?.label,
                  'data-overflow-tooltip': Object.hasOwn(column.props ?? {}, 'show-overflow-tooltip') ? 'true' : undefined
                },
                column.children?.default?.({ row }) ?? String(row[column.props?.prop] ?? '')
              )
            )
          )
        )
      );
    };
  }
};
const descriptionsItem = {
  props: ['label'],
  setup(props, { slots }) {
    return () => h('p', { 'data-label': props.label }, slots.default?.());
  }
};

async function render(name, props) {
  const app = createSSRApp(await component(name), props);
  app.component('el-card', children);
  app.component('el-table', table);
  app.component('el-table-column', children);
  app.component('el-descriptions', children);
  app.component('el-descriptions-item', descriptionsItem);
  app.component('el-dialog', children);
  app.component('el-drawer', children);
  for (const tag of ['el-tag', 'el-button', 'el-empty', 'el-collapse', 'el-collapse-item']) app.component(tag, children);
  app.component('el-alert', {
    props: ['title', 'description'],
    setup(props) {
      return () => h('aside', [props.title, props.description]);
    }
  });
  return renderToString(app);
}

function shortageRow(markup) {
  const row = (markup.match(/<tr>.*?<\/tr>/gs) ?? []).find((item) => item.includes('缺料缺口'));
  assert.ok(row, 'the total shortage gap KPI row must stay visible');
  return row;
}

test('KPI comparison suppresses cross-unit baseline, projected, actual and delta, preserving material count and other deltas', async () => {
  const markup = await render('DecisionKpiCompare', {
    baseline: kpis,
    projected: { ...kpis, delayedWorkOrderCount: 1 },
    actual: kpis
  });
  const row = shortageRow(markup);
  assert.doesNotMatch(row, /38\.2762|件|无变化|改善|增加/);
  for (const label of ['当前基线', '推荐方案预测', '执行后实测']) {
    assert.ok(row.includes(`data-label="${label}" data-overflow-tooltip="true">${warning}</td>`), `${label} should display the warning`);
  }
  assert.ok(row.includes('data-label="变化">—</td>'), 'cross-unit delta must not be computed');
  const materialRow = (markup.match(/<tr>.*?<\/tr>/gs) ?? []).find((item) => item.includes('缺料物料（项）'));
  assert.ok(materialRow?.includes('data-label="当前基线" data-overflow-tooltip="true">4</td>'), 'valid shortageMaterialCount stays visible');
  assert.match(markup, /改善 1 个/);
});

test('scenario list suppresses the mixed-unit aggregate without affecting other KPI columns', async () => {
  const markup = await render('DecisionScenarioTable', { scenarios: [scenario], recommendedCode: scenario.scenarioCode });
  assert.ok(markup.includes(`data-label="缺料缺口" data-overflow-tooltip="true">${warning}</td>`));
  assert.doesNotMatch(markup, /38\.2762|件/);
  assert.ok(markup.includes('data-label="拖期天数">3</td>'));
});

test('scenario preview suppresses the mixed-unit aggregate and its pieces suffix', async () => {
  const markup = await render('DecisionScenarioPreview', { scenario, modelValue: true });
  assert.ok(markup.includes(`data-label="预测缺料">${warning}</p>`));
  assert.doesNotMatch(markup, /38\.2762|件/);
  assert.ok(markup.includes('data-label="预测拖期">3 天</p>'));
});

test('absent total shortage gap stays unknown, not a fabricated total, across all three surfaces', async () => {
  const missing = { ...kpis, totalShortageGap: null };
  const comparison = shortageRow(await render('DecisionKpiCompare', { baseline: missing, projected: missing, actual: null }));
  for (const label of ['当前基线', '推荐方案预测', '执行后实测', '变化']) {
    const marker = label === '变化' ? '' : ' data-overflow-tooltip="true"';
    assert.ok(comparison.includes(`data-label="${label}"${marker}>—</td>`), `${label} should remain unknown`);
  }
  const list = await render('DecisionScenarioTable', { scenarios: [{ ...scenario, kpis: missing }] });
  assert.ok(list.includes('data-label="缺料缺口" data-overflow-tooltip="true">—</td>'));
  const preview = await render('DecisionScenarioPreview', { scenario: { ...scenario, kpis: missing }, modelValue: true });
  assert.ok(preview.includes('data-label="预测缺料">—</p>'));
  assert.doesNotMatch(preview, /件/);
});

test('zero shortage count and exact zero aggregate display no shortage without inventing a unit', async () => {
  for (const totalShortageGap of ['0', '0.0000']) {
    const zero = { ...kpis, shortageMaterialCount: 0, totalShortageGap };
    const comparison = shortageRow(await render('DecisionKpiCompare', { baseline: zero, projected: zero, actual: zero }));
    for (const label of ['当前基线', '推荐方案预测', '执行后实测']) {
      assert.ok(comparison.includes(`data-label="${label}" data-overflow-tooltip="true">0（无缺料）</td>`), `${label} should show proven zero`);
    }
    assert.ok(comparison.includes('data-label="变化">—</td>'), 'do not infer a change from aggregate units');
    const list = await render('DecisionScenarioTable', { scenarios: [{ ...scenario, kpis: zero }] });
    assert.ok(list.includes('data-label="缺料缺口" data-overflow-tooltip="true">0（无缺料）</td>'));
    const preview = await render('DecisionScenarioPreview', { scenario: { ...scenario, kpis: zero }, modelValue: true });
    assert.ok(preview.includes('data-label="预测缺料">0（无缺料）</p>'));
    assert.doesNotMatch(`${comparison}${list}${preview}`, /件|不同单位/);
  }
});

test('nonzero gap stays hidden with an unverified-unit notice even for a single shortage material', async () => {
  const single = { ...kpis, shortageMaterialCount: 1, totalShortageGap: '5.4292' };
  const comparison = shortageRow(await render('DecisionKpiCompare', { baseline: single, projected: single, actual: single }));
  for (const label of ['当前基线', '推荐方案预测', '执行后实测']) {
    assert.ok(comparison.includes(`data-label="${label}" data-overflow-tooltip="true">${warning}</td>`), `${label} must not infer a unit`);
  }
  assert.ok(comparison.includes('data-label="变化">—</td>'));
  const list = await render('DecisionScenarioTable', { scenarios: [{ ...scenario, kpis: single }] });
  assert.ok(list.includes(`data-label="缺料缺口" data-overflow-tooltip="true">${warning}</td>`));
  const preview = await render('DecisionScenarioPreview', { scenario: { ...scenario, kpis: single }, modelValue: true });
  assert.ok(preview.includes(`data-label="预测缺料">${warning}</p>`));
  assert.doesNotMatch(`${comparison}${list}${preview}`, /5\.4292|不同单位|件/);
  for (const uncertain of [
    { ...single, totalShortageGap: '0' },
    { ...single, shortageMaterialCount: 0 }
  ]) {
    const row = shortageRow(await render('DecisionKpiCompare', { baseline: uncertain }));
    assert.ok(
      row.includes(`data-label="当前基线" data-overflow-tooltip="true">${warning}</td>`),
      'both count and exact zero must agree before claiming no shortage'
    );
  }
});
test('short shortage cells and nearby note explain unverified source units without suggesting evidence can resolve them', async () => {
  const sources = [
    { name: 'DecisionKpiCompare', props: { baseline: kpis, projected: kpis, actual: kpis } },
    { name: 'DecisionScenarioTable', props: { scenarios: [scenario] } },
    { name: 'DecisionScenarioPreview', props: { scenario, modelValue: true } }
  ];
  for (const { name, props } of sources) {
    const markup = await render(name, props);
    assert.match(markup, /—（单位待核）/, `${name}: compact cell text`);
    assert.equal((markup.match(/缺口原值如有返回/g) ?? []).length, 1, `${name}: one nearby visible note`);
    assert.match(markup, /缺口原值如有返回，因单位待核不能确认可合计；请核对物料主档与来源。/);
    assert.doesNotMatch(markup, /请查看分项证据|38\.2762/, `${name}: no unverified total or unsupported evidence claim`);
  }
});

test('shortage cells avoid multi-line growth in narrow comparison columns', async () => {
  const kpi = shortageRow(await render('DecisionKpiCompare', { baseline: kpis, projected: kpis, actual: kpis }));
  for (const label of ['当前基线', '推荐方案预测', '执行后实测']) {
    assert.ok(kpi.includes(`data-label="${label}" data-overflow-tooltip="true">${warning}</td>`), `${label}: one-line overflow`);
  }
  const table = await render('DecisionScenarioTable', { scenarios: [scenario] });
  assert.ok(table.includes(`data-label="缺料缺口" data-overflow-tooltip="true">${warning}</td>`));
});
test('evidence drawer preserves raw aggregate for audit but warns that its unit is unverified', async () => {
  const evidence = [
    { sourceDomain: 'DERIVED', sourceType: 'CALC', metricCode: 'total_shortage_gap', metricValue: unsafeTotal, unit: null },
    { sourceDomain: 'DERIVED', sourceType: 'CALC', metricCode: 'shortage_material_count', metricValue: '4', unit: '项' }
  ];
  const markup = await render('DecisionEvidenceDrawer', { modelValue: true, evidence, status: 'WAITING_APPROVAL' });
  assert.match(markup, /total_shortage_gap/);
  assert.match(markup, /38\.2762/); /** 审计数据保留实际来源值。 */
  assert.match(markup, /单位.*?未核|未核.*?单位|不同单位不可合计/);
  const rows = markup.match(/<tr>.*?<\/tr>/gs) ?? [];
  const rawRow = rows.find((row) => row.includes('total_shortage_gap'));
  assert.ok(rawRow?.includes('38.2762'));
  assert.match(rawRow, /单位未核|不可合计/, 'warn beside the unsafe source value, not only outside the table');
  const countRow = rows.find((row) => row.includes('shortage_material_count'));
  assert.ok(countRow?.includes('4'), 'unrelated count evidence stays visible');
  assert.doesNotMatch(countRow, /单位未核|不可合计/);
});
