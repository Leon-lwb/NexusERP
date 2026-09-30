import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { build } from 'esbuild';

const file = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

async function loadHelper(input, outfile) {
  await build({
    entryPoints: [new URL(`../${input}`, import.meta.url).pathname.replace(/^\/(?=[A-Z]:)/, '')],
    outfile: new URL(`../node_modules/.tmp/${outfile}`, import.meta.url).pathname.replace(/^\/(?=[A-Z]:)/, ''),
    bundle: true,
    format: 'esm',
    logLevel: 'silent'
  });
  return import(`../node_modules/.tmp/${outfile}?t=${Date.now()}`);
}

test('ERP MRP work order source label, dictionary fallback, and other source types', async () => {
  const { workOrderSourceNoLabel, withMaterialRequirementSource } = await loadHelper(
    'src/views/erp/prd/prdwork/workOrderDisplay.ts',
    'task7-work-order-display.mjs'
  );
  assert.equal(workOrderSourceNoLabel('MATERIAL_REQUIREMENT'), '物料需求单号');
  assert.equal(workOrderSourceNoLabel('SALES'), '销售订单编号');
  assert.equal(workOrderSourceNoLabel('MANUAL'), '来源单号');
  const existing = [{ value: 'SALES', label: '销售订单' }];
  assert.deepEqual(withMaterialRequirementSource(existing), [
    ...existing,
    { value: 'MATERIAL_REQUIREMENT', label: '物料需求', elTagType: 'primary' }
  ]);
  assert.equal(existing.length, 1, 'original backend dictionary should not be mutated');
  assert.equal(withMaterialRequirementSource([...existing, { value: 'MATERIAL_REQUIREMENT', label: '已维护' }]).length, 2);
});

test('ERP work order list and form expose correct source fields', () => {
  const list = file('src/views/erp/prd/prdwork/index.vue');
  const form = file('src/views/erp/prd/prdwork/form.vue');
  assert.match(list, /<el-table-column label="来源单号"[^>]*prop="sourceNo"/);
  assert.match(list, /:options="workOrderSourceTypeOptions"\s+:value="scope.row.sourceType"/);
  assert.match(form, /:label="workOrderSourceNoLabel\(form.sourceType\)"\s+prop="sourceNo"/);
  assert.match(form, /v-for="dict in workOrderSourceTypeOptions"/);
});

test('AI zero-risk chart has a true empty state instead of four equal slices', async () => {
  const { decisionRiskSeriesData } = await loadHelper('src/views/ai/decision/riskChartData.ts', 'task7-risk-chart-data.mjs');
  assert.deepEqual(decisionRiskSeriesData([]), []);
  assert.deepEqual(decisionRiskSeriesData([{ riskLevel: 'HIGH' }, { riskLevel: 'HIGH' }, { riskLevel: 'LOW' }]), [
    { name: '严重风险', value: 0 },
    { name: '高风险', value: 2 },
    { name: '中风险', value: 0 },
    { name: '低风险', value: 1 }
  ]);
  const board = file('src/views/ai/decision/index.vue');
  assert.match(board, /decisionRiskSeriesData\(rows\.value\)/);
  assert.match(board, /<el-empty[^>]+description="暂无决策运行数据"/);
  assert.match(board, /v-show="rows\.length > 0"/);
});
test('IoT realtime heading states the bounded SSE window without a fictional polling interval', () => {
  const source = file('src/views/iot/device/realtime.vue');
  assert.match(source, /实时数据（最多 \{\{ realtimePoints \}\} 个采样点）/);
  assert.doesNotMatch(source, /updateInterval|3s interval/);
});

let workOrderQuantityModule;
async function loadWorkOrderQuantityFields() {
  workOrderQuantityModule ??= loadHelper('src/views/erp/prd/prdwork/workOrderDisplay.ts', 'task7-work-order-quantities.mjs');
  const { normalizeWorkOrderQuantityFields } = await workOrderQuantityModule;
  assert.equal(typeof normalizeWorkOrderQuantityFields, 'function', 'work order API quantities need a targeted normalizer');
  return normalizeWorkOrderQuantityFields;
}

const workOrderQuantityFields = ['plannedQty', 'completedQty', 'scrapQty'];

function assertEmptyWorkOrderQuantities(normalize, value) {
  const result = normalize({ plannedQty: value, completedQty: value, scrapQty: value });
  for (const field of workOrderQuantityFields) {
    assert.equal(result[field], undefined, `${field} must stay empty for ${String(value)}, not become 0 or NaN`);
  }
}

test('ERP work order quantities normalize decimal strings without rounding', async () => {
  const normalize = await loadWorkOrderQuantityFields();
  assert.deepEqual(normalize({ plannedQty: '10.0000', completedQty: '0.0000', scrapQty: '1.2345' }), {
    plannedQty: 10,
    completedQty: 0,
    scrapQty: 1.2345
  });
  assert.deepEqual(normalize({ plannedQty: 10, completedQty: 0, scrapQty: 1.2345 }), {
    plannedQty: 10,
    completedQty: 0,
    scrapQty: 1.2345
  });
  assert.deepEqual(normalize({ plannedQty: '0', completedQty: '0.0001', scrapQty: '1.2300' }), {
    plannedQty: 0,
    completedQty: 0.0001,
    scrapQty: 1.23
  });
});

test('ERP work order missing, blank, negative, non-finite and non-decimal quantities stay empty', async () => {
  const normalize = await loadWorkOrderQuantityFields();
  const absent = normalize({});
  for (const field of workOrderQuantityFields) assert.equal(absent[field], undefined);
  for (const value of [
    undefined,
    null,
    '',
    '   ',
    '\t',
    -1,
    '-0.0001',
    'not-a-number',
    NaN,
    'NaN',
    Infinity,
    -Infinity,
    'Infinity',
    '1e3',
    '1e-4',
    '0x10',
    true,
    {},
    []
  ]) {
    assertEmptyWorkOrderQuantities(normalize, value);
  }
});

test('ERP work order excessive decimal precision and overflow are not silently rounded', async () => {
  const normalize = await loadWorkOrderQuantityFields();
  for (const value of [
    '1.23456',
    1.23456,
    '0.00001',
    0.00001,
    0.30000000000000004,
    '900719925474.0992',
    '99999999999999.9999',
    '100000000000000.0000',
    '1'.repeat(400),
    Number.MAX_VALUE,
    Number.MAX_SAFE_INTEGER + 1
  ]) {
    assertEmptyWorkOrderQuantities(normalize, value);
  }
});

test('ERP work order four-place safe boundary also requires exact display round-trip', async () => {
  const normalize = await loadWorkOrderQuantityFields();
  for (const decimal of ['0.0001', '1.2345', '900719925474.0991']) {
    const result = normalize({ plannedQty: decimal, completedQty: decimal, scrapQty: decimal });
    for (const field of workOrderQuantityFields) {
      assert.equal(typeof result[field], 'number');
      assert.equal(result[field].toFixed(4), decimal, `${decimal} must retain its actual four-place quantity`);
    }
  }
  /** 虽未超过放大后的安全整数上限，双精度数却将 .0987 显示为 .0988。 */
  assertEmptyWorkOrderQuantities(normalize, '900719925474.0987');
});

test('ERP work order normalization preserves 19-digit IDs, source, UOM, versions and other quantities', async () => {
  const normalize = await loadWorkOrderQuantityFields();
  const raw = {
    woId: '9223372036854775806',
    materialId: '8134567890123456789',
    sourceId: '7234567890123456789',
    baseUomId: '6334567890123456789',
    unitId: '5434567890123456789',
    woNo: 'WO-SERVER-0007',
    sourceType: 'MATERIAL_REQUIREMENT',
    sourceNo: 'MRP-0007',
    sourceLabel: '已配置来源',
    unitName: 'kg',
    bomVersion: '0007',
    version: '19',
    moq: '12.34567',
    packagingQty: '2.50000',
    plannedQty: '10.0000',
    completedQty: '0.0000',
    scrapQty: '1.2345'
  };
  const result = normalize(raw);
  assert.deepEqual(result, { ...raw, plannedQty: 10, completedQty: 0, scrapQty: 1.2345 });
  for (const field of ['woId', 'materialId', 'sourceId', 'baseUomId', 'unitId']) {
    assert.equal(result[field], raw[field]);
    assert.equal(typeof result[field], 'string');
  }
});

test('ERP work order normalization does not mutate API data and is idempotent', async () => {
  const normalize = await loadWorkOrderQuantityFields();
  const workshopList = Object.freeze(['8134567890123456789']);
  const raw = Object.freeze({ plannedQty: '10.0000', completedQty: null, scrapQty: '1.2345', workshopList });
  const once = normalize(raw);
  assert.notEqual(once, raw);
  assert.equal(once.workshopList, workshopList, 'unrelated nested data should retain its identity');
  assert.deepEqual(raw, { plannedQty: '10.0000', completedQty: null, scrapQty: '1.2345', workshopList });
  assert.deepEqual(once, { plannedQty: 10, completedQty: undefined, scrapQty: 1.2345, workshopList });
  assert.deepEqual(normalize(once), once);
  const empty = normalize({ plannedQty: '1e3', completedQty: '1.23456', scrapQty: '-1' });
  assert.deepEqual(normalize(empty), empty);
});

test('ERP production-plan quantity uses the same scalar normalization policy', async () => {
  const normalize = await loadWorkOrderQuantityFields();
  const { normalizeWorkOrderQuantity } = await workOrderQuantityModule;
  assert.equal(typeof normalizeWorkOrderQuantity, 'function', 'plan selection needs the same value-only normalizer');
  for (const value of [
    10,
    '10.0000',
    0,
    '0.0001',
    '1.2345',
    null,
    '',
    -1,
    '1e3',
    '1.23456',
    '900719925474.0991',
    '900719925474.0992',
    '900719925474.0987'
  ]) {
    assert.equal(normalizeWorkOrderQuantity(value), normalize({ plannedQty: value }).plannedQty);
  }
});

test('ERP work order getInfo and selectPlan actually wire the bounded quantity normalizers', () => {
  const source = file('src/views/erp/prd/prdwork/form.vue');
  assert.ok(
    /import\s*\{[^}]*\bnormalizeWorkOrderQuantityFields\b[^}]*\}\s*from ['"]\.\/workOrderDisplay['"]/.test(source),
    'form must import the field normalizer'
  );
  assert.ok(
    /import\s*\{[^}]*\bnormalizeWorkOrderQuantity\b[^}]*\}\s*from ['"]\.\/workOrderDisplay['"]/.test(source),
    'form must import the scalar normalizer'
  );
  const getInfo = source.match(/function getInfo\(id\) \{([\s\S]*?)\n\}/)?.[1];
  assert.ok(getInfo, 'getInfo must remain the API response entry point');
  assert.match(getInfo, /form\.value\s*=\s*normalizeWorkOrderQuantityFields\(response\.data\)/);
  assert.doesNotMatch(getInfo, /form\.value\s*=\s*response\.data\s*;/);
  const selectPlan = source.match(/async function selectPlan\(row\) \{([\s\S]*?)\n\}/)?.[1];
  assert.ok(selectPlan, 'selectPlan must remain the production-plan entry point');
  assert.match(selectPlan, /form\.value\.plannedQty\s*=\s*normalizeWorkOrderQuantity\(row\.totalQuantity\)/);
  assert.match(selectPlan, /form\.value\.planId\s*=\s*row\.planId/);
  assert.match(selectPlan, /form\.value\.sourceId\s*=\s*row\.planId/);
  assert.match(selectPlan, /form\.value\.sourceNo\s*=\s*row\.referenceNo/);
});

test('ERP work order quantity inputs display four places and preserve read-only guards', () => {
  const source = file('src/views/erp/prd/prdwork/form.vue');
  for (const field of workOrderQuantityFields) {
    const tag = source.match(new RegExp(`<el-input-number\\b[^>]*v-model="form\\.${field}"[^>]*>`))?.[0];
    assert.ok(tag, `${field} must retain its input-number binding`);
    assert.match(tag, /:precision="4"/);
    assert.match(tag, /:min="0"/);
    if (field !== 'plannedQty') assert.match(tag, /\bdisabled(?:\s|\/?>)/);
  }
  assert.match(source, /<el-form\b[^>]*:disabled="formDisabled"/);
  assert.match(source, /const formDisabled = computed\(\(\) => isViewMode\.value \|\| props\.disabled\)/);
  assert.match(source, /@click="submitForm" v-if="!isViewMode && !props\.disabled"/);
});
