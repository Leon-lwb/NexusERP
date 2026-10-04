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

// v2 数学展示 fixture 仅用于组件契约，不是正式业务案例。
const v2Kpis = { ...kpis, shortageGapRatio: '0.25000000', shortageBasis: { runId: '9007199254740993' } };

test('v2 KPI 比较展示无量纲原始比例和百分点差，不把混单位原值当收益', async () => {
  const html = await render('DecisionKpiCompare', { baseline: v2Kpis, projected: { ...v2Kpis, shortageGapRatio: '0.12500000' }, actual: null });
  assert.match(html, /缺口比例/);
  assert.match(html, /25\.00%/);
  assert.match(html, /12\.50%/);
  assert.match(html, /百分点/);
  assert.doesNotMatch(html, /38\.2762/);
});

test('v2 方案列表展示比例而不隐藏旧的不可合计提示', async () => {
  const html = await render('DecisionScenarioTable', { scenarios: [{ ...scenario, kpis: v2Kpis }] });
  assert.match(html, /25\.00%/);
  assert.match(html, /单位待核/);
  assert.doesNotMatch(html, /38\.2762/);
});

test('v2 预览展示比例且相对零分不等于无缺料', async () => {
  const html = await render('DecisionScenarioPreview', { modelValue: true, scenario: { ...scenario, objectiveScore: '0', kpis: v2Kpis } });
  assert.match(html, /25\.00%/);
  assert.match(html, /归一化/);
  assert.doesNotMatch(html, /38\.2762/);
});

test('新合同缺单位或裸比例仍显示不可排名，不显示零比例或可比较缺口', async () => {
  for (const name of ['DecisionKpiCompare', 'DecisionScenarioTable', 'DecisionScenarioPreview']) {
    const unproven = { ...kpis, shortageGapRatio: '0.25', shortageBasis: null };
    const blocked = { ...scenario, rank: 0, infeasibleReasons: ['SHORTAGE_GAP_RATIO_UNRANKABLE'], kpis: unproven };
    const props =
      name === 'DecisionKpiCompare'
        ? { baseline: unproven }
        : name === 'DecisionScenarioTable'
          ? { scenarios: [blocked] }
          : { modelValue: true, scenario: blocked };
    const html = await render(name, props);
    assert.match(html, /不可排名/);
    assert.doesNotMatch(html, /25\.00%|0\.00%/);
  }
});

// 新版展示回归只使用组件合同样本，不把历史总量或归一化得分当作新比例。
const ratioRow = (markup) => (markup.match(/<tr>.*?<\/tr>/gs) ?? []).find((row) => row.includes('缺口比例（无量纲）'));
const propsFor = (name, data) =>
  name === 'DecisionKpiCompare'
    ? { baseline: data, projected: data, actual: data }
    : name === 'DecisionScenarioTable'
      ? { scenarios: [{ ...scenario, kpis: data }] }
      : { modelValue: true, scenario: { ...scenario, kpis: data } };
const ratioSurfaces = ['DecisionKpiCompare', 'DecisionScenarioTable', 'DecisionScenarioPreview'];

test('纯新版比较、预览、表格只展示核验比例，不再展示旧缺口 KPI', async () => {
  const data = { ...v2Kpis, shortageMaterialCount: 0, totalShortageGap: '0' };
  for (const name of ratioSurfaces) {
    const html = await render(name, propsFor(name, data));
    assert.match(html, /25\.00%（逐物料等权）/);
    assert.doesNotMatch(html, /0（无缺料）/, `${name} 不能沿用旧总量合成新版结论`);
    if (name === 'DecisionKpiCompare') {
      assert.ok(ratioRow(html));
      assert.ok(!(html.match(/<tr>.*?<\/tr>/gs) ?? []).some((row) => row.includes('缺料缺口')));
    } else {
      assert.doesNotMatch(html, /data-label="(?:缺料缺口|预测缺料)"/);
    }
  }
});

test('新合同没有 R 或缺核验来源时明确未知，历史零不能回填新 R', async () => {
  for (const shortageGapRatio of [null, '', 'NaN', '-0.1', '1.1']) {
    const data = { ...v2Kpis, shortageGapRatio, shortageMaterialCount: 0, totalShortageGap: '0' };
    for (const name of ratioSurfaces) {
      const html = await render(name, propsFor(name, data));
      assert.match(html, /未知.*不可排名/s, `${name}：未知必须明确展示`);
      assert.doesNotMatch(html, /0（无缺料）|历史口径：未记录无量纲比例|(?:\d+\.\d+)%/);
    }
  }
  for (const name of ratioSurfaces) {
    const html = await render(name, propsFor(name, { ...v2Kpis, shortageBasis: null }));
    assert.match(html, /未知.*不可排名/s);
    assert.doesNotMatch(html, /25\.00%/);
  }
});

test('比较的未知或空比例不参与百分点差计算，已核验比例差仍只作展示', async () => {
  for (const shortageGapRatio of [null, '', 'NaN']) {
    const html = await render('DecisionKpiCompare', {
      baseline: { ...v2Kpis, shortageGapRatio },
      projected: v2Kpis,
      actual: null
    });
    const row = ratioRow(html);
    assert.ok(row);
    assert.ok(row.includes('data-label="变化">—</td>'));
    assert.doesNotMatch(row, /百分点/);
  }
  const html = await render('DecisionKpiCompare', { baseline: v2Kpis, projected: { ...v2Kpis, shortageGapRatio: '0.12500000' } });
  assert.match(ratioRow(html), /-12\.50 百分点/);
});

test('混合新旧比较保留历史信息，但新版单元格不能借用旧数量，新旧排名保持后端原值', async () => {
  const legacy = { ...kpis, shortageMaterialCount: 0, totalShortageGap: '0' };
  const modern = { ...v2Kpis, shortageMaterialCount: 0, totalShortageGap: '0' };
  const comparison = await render('DecisionKpiCompare', { baseline: legacy, projected: modern, actual: null });
  assert.match(comparison, /历史口径/);
  const history = (comparison.match(/<tr>.*?<\/tr>/gs) ?? []).find((row) => row.includes('缺料缺口'));
  assert.ok(history.includes('data-label="当前基线" data-overflow-tooltip="true">0（无缺料）</td>'));
  assert.ok(history.includes('data-label="推荐方案预测" data-overflow-tooltip="true">—</td>'));
  assert.doesNotMatch(ratioRow(comparison), /0\.00%|百分点/);
  const scenarios = [
    { ...scenario, scenarioCode: 'OLD', rank: 7, kpis: legacy },
    { ...scenario, scenarioCode: 'NEW', rank: 3, kpis: modern }
  ];
  const original = structuredClone(scenarios);
  const tableHtml = await render('DecisionScenarioTable', { scenarios });
  const tableRows = tableHtml.match(/<tr>.*?<\/tr>/gs) ?? [];
  const oldRow = tableRows.find((row) => row.includes('>OLD</td>'));
  const newRow = tableRows.find((row) => row.includes('>NEW</td>'));
  assert.match(oldRow, /历史口径：未记录无量纲比例/);
  assert.ok(oldRow.includes('data-label="排名">7</td>'));
  assert.ok(newRow.includes('data-label="排名">3</td>'));
  assert.doesNotMatch(newRow, /0（无缺料）/);
  assert.deepEqual(scenarios, original, '不得改写后端 KPI 或重新计算排名');
});

test('旧 run 不插入新版比例或新结论，既有信息明确标为历史口径', async () => {
  for (const name of ratioSurfaces) {
    const html = await render(name, propsFor(name, kpis));
    assert.match(html, /历史口径/);
    assert.doesNotMatch(html, /缺口比例（无量纲）|逐物料等权|不可排名/);
    assert.match(html, /单位待核/);
  }
});
