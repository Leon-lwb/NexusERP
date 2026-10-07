import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { parse } from '@vue/compiler-sfc';
import ts from 'typescript';

/** 执行实际 SFC 处理函数，不挂载 Vue，也不调用在线服务。 */
const source = readFileSync(new URL('../src/views/erp/wm/productionissue/form.vue', import.meta.url), 'utf8');
const { descriptor, errors } = parse(source, { filename: 'form.vue' });
assert.deepEqual(errors, [], '领料页面应能正确解析');
const script = ts.createSourceFile('form.ts', descriptor.scriptSetup.content, ts.ScriptTarget.ES2022, true, ts.ScriptKind.TS);
const template = descriptor.template.content;
const transpile = (code) => ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None } }).outputText;
/** 注入实际纯工具，而非伪造其结果；非空更新、数量比较及身份校验仍由生产代码执行。 */
const utilities = {};
for (const file of ['quantity.ts', 'referenceAmount.ts']) {
  const code = readFileSync(new URL('../src/views/erp/wm/productionissue/' + file, import.meta.url), 'utf8');
  const compiled = ts.transpileModule(code, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
  }).outputText;
  new Function('exports', 'require', compiled)(utilities, () => utilities);
}
const drain = () => new Promise((resolve) => setImmediate(resolve));
/** 提取实际范围函数并注入其真实依赖，不模拟或放宽范围校验结果。 */
function scopeDependencies(context) {
  const dependencies = { ...utilities, ...context };
  for (const name of ['lineStockScope', 'scopedAvailableQuantity', 'loadLineAvailableStock']) {
    if (!(name in dependencies)) dependencies[name] = (...args) => handler(name, dependencies)(...args);
  }
  return dependencies;
}
function handler(name, context) {
  const fn = script.statements.find((statement) => ts.isFunctionDeclaration(statement) && statement.name?.text === name);
  assert.ok(fn, `页面应提供${name}处理函数`);
  const dependencies = scopeDependencies(context);
  return new Function(...Object.keys(dependencies), `${transpile(fn.getText(script))}\nreturn ${name};`)(...Object.values(dependencies));
}
function scriptComputed(name, context) {
  const declarations = script.statements.filter(ts.isVariableStatement).flatMap((statement) => statement.declarationList.declarations);
  const declaration = declarations.find((entry) => ts.isIdentifier(entry.name) && entry.name.text === name);
  assert.ok(declaration?.initializer, `页面应提供${name}计算属性`);
  const dependencies = scopeDependencies(context);
  return new Function(...Object.keys(dependencies), `${transpile(`const ${name} = ${declaration.initializer.getText(script)};`)}\nreturn ${name};`)(
    ...Object.values(dependencies)
  );
}
const computed = (fn) => ({
  get value() {
    return fn();
  }
});
const modal = () => ({
  warnings: [],
  confirmations: [],
  msgWarning(value) {
    this.warnings.push(value);
  },
  confirm(value) {
    this.confirmations.push(value);
  }
});

test('workshop-backed work order never invents a warehouse ID or name', () => {
  const form = { value: { warehouseId: null, warehouseCode: null, warehouseName: null } };
  const workOrderSelectOpen = { value: true };
  let filled = 0;
  handler('selectWorkOrder', {
    form,
    workOrderSelectOpen,
    handleAutoFillFromWorkOrder: () => {
      filled++;
    }
  })({ woId: '8123456789012345678', woNo: 'WO202609290001', workshopId: '3', workshopName: '第一车间' });
  assert.equal(form.value.woId, '8123456789012345678');
  assert.deepEqual([form.value.warehouseId, form.value.warehouseCode, form.value.warehouseName], [null, null, null]);
  assert.equal(workOrderSelectOpen.value, false);
  assert.equal(filled, 1);
});

test('choosing a work order leaves an existing real header warehouse untouched', () => {
  const form = { value: { warehouseId: '7', warehouseCode: 'WH007', warehouseName: '原料仓' } };
  handler('selectWorkOrder', { form, workOrderSelectOpen: { value: true }, handleAutoFillFromWorkOrder: () => {} })({
    woId: '42',
    woNo: 'WO202609290002',
    workshopId: '3',
    workshopName: '第一车间'
  });
  assert.deepEqual([form.value.warehouseId, form.value.warehouseCode, form.value.warehouseName], ['7', 'WH007', '原料仓']);
});

test('reselecting the same persisted work order does not overwrite its saved line warehouse', () => {
  const form = { value: { issueId: '91', woId: '8123456789012345678', warehouseId: null } };
  const lineList = { value: [{ lineId: '92', warehouseId: '9', warehouseName: '二号原料仓' }] };
  let refilled = 0;
  handler('selectWorkOrder', {
    form,
    lineList,
    workOrderSelectOpen: { value: true },
    handleAutoFillFromWorkOrder: () => {
      refilled++;
    }
  })({ woId: '8123456789012345678', woNo: 'WO202609290002', workshopId: '3' });
  assert.equal(refilled, 0, 'BOM must not replace saved issue lines on a same-order reselect');
  assert.deepEqual([lineList.value[0].lineId, lineList.value[0].warehouseId], ['92', '9']);
});

test('opening an existing issue preserves real header and line warehouse from the read API', async () => {
  const form = { value: {} };
  const lineList = { value: [] };
  const data = {
    issueId: '91',
    warehouseId: '7',
    warehouseName: '原料仓',
    wmProductionIssueLineList: [{ lineId: '92', warehouseId: '9', warehouseName: '二号原料仓', issuedQty: 1, requiredQty: 3 }]
  };
  let refreshed = 0;
  handler('getInfo', {
    form,
    lineList,
    getProductionIssue: async () => ({ data }),
    loadAvailableStocks: () => {
      refreshed++;
    }
  })('91');
  await Promise.resolve();
  assert.equal(form.value.warehouseId, '7');
  assert.deepEqual([lineList.value[0].lineId, lineList.value[0].warehouseId, lineList.value[0].warehouseName], ['92', '9', '二号原料仓']);
  assert.equal(refreshed, 1);
});

test('header warehouse must be selected from the active warehouse picker', () => {
  const form = { value: { warehouseId: null } };
  const warehouseSelectOpen = { value: true };
  handler('handleWarehouseSelect', { form, lineList: { value: [] }, warehouseSelectOpen, proxy: { $modal: modal() } })({
    warehouseId: '7',
    warehouseCode: 'WH007',
    warehouseName: '原料仓',
    status: 1
  });
  assert.deepEqual([form.value.warehouseId, form.value.warehouseCode, form.value.warehouseName], ['7', 'WH007', '原料仓']);
  assert.equal(warehouseSelectOpen.value, false);
});

test('picker rejects inactive or unidentified warehouses', () => {
  for (const selected of [
    { warehouseId: '3', warehouseCode: 'WH003', warehouseName: '停用库', status: 0 },
    { warehouseId: null, status: 1 }
  ]) {
    const form = { value: { warehouseId: null } },
      warehouseSelectOpen = { value: true },
      notice = modal();
    handler('handleWarehouseSelect', { form, warehouseSelectOpen, proxy: { $modal: notice } })(selected);
    assert.equal(form.value.warehouseId, null);
    assert.equal(warehouseSelectOpen.value, true);
    assert.equal(notice.warnings.length, 1);
  }
});

test('posting readiness tracks picked row warehouse, header fallback, and empty picks reactively', () => {
  const form = { value: { warehouseId: null } },
    lineList = { value: [{ warehouseId: null, currentQty: 2 }] };
  const ready = scriptComputed('executionWarehouseReady', { computed, form, lineList });
  assert.equal(ready.value, false);
  form.value.warehouseId = '7';
  assert.equal(ready.value, true);
  form.value.warehouseId = null;
  lineList.value = [
    { warehouseId: '8', currentQty: 2 },
    { warehouseId: '9', currentQty: 1 }
  ];
  assert.equal(ready.value, true);
  lineList.value.push({ warehouseId: null, currentQty: 3 });
  assert.equal(ready.value, false);
  lineList.value[2].currentQty = 0;
  assert.equal(ready.value, true);
  lineList.value = [{ warehouseId: null, currentQty: 0 }];
  assert.equal(ready.value, false);
});

test('posting handler warns without confirmation or API call when warehouse is missing', () => {
  const notice = modal(),
    form = { value: { issueId: '8123456789012345678', warehouseId: null } };
  const lineList = { value: [{ currentQty: 2, warehouseId: null }] };
  const executionWarehouseReady = scriptComputed('executionWarehouseReady', { computed, form, lineList });
  let updated = 0,
    posted = 0;
  handler('handleExecute', {
    form,
    lineList,
    executionWarehouseReady,
    proxy: { $modal: notice },
    executeLoading: { value: false },
    updateProductionIssue: () => {
      updated++;
    },
    executeProductionIssue: () => {
      posted++;
    }
  })();
  assert.equal(notice.warnings.length, 1);
  assert.match(notice.warnings[0], /仓库/);
  assert.deepEqual([notice.confirmations.length, updated, posted], [0, 0, 0]);
});

/** 绑定实际 SFC 守卫；仅将读取 API 和 Vue 的 ref 替换为内存测试夹具。 */
function stockPickerContext(line, status = 'PARTIAL', readOnly = false) {
  const queried = [];
  const context = {
    form: { value: { issueId: '91', warehouseId: '7', status } },
    lineList: { value: [line] },
    stockSelectOpen: { value: false },
    currentRowIndex: { value: -1 },
    stockLoading: { value: false },
    stockList: { value: [] },
    stockQuery: { value: null },
    stockRequestId: { value: 0 },
    isViewMode: { value: readOnly },
    proxy: { $modal: modal() },
    recalcRow: handler('recalcRow', {}),
    getAvailableStock: (materialId) => {
      queried.push(materialId);
      return Promise.resolve({ data: [nextBatch()] });
    },
    queried
  };
  context.canEditLine = scriptComputed('canEditLine', { computed, form: context.form });
  context.canCheckLine = handler('canCheckLine', {});
  /** 延迟绑定实际守卫，查询和选择阶段都读取当前只读及单据状态。 */
  context.canSelectStock = (row) => handler('canSelectStock', context)(row);
  return context;
}

const partialPick = () => ({
  lineId: '8123456789012345678',
  issueId: '91',
  materialId: '23',
  warehouseId: '7',
  warehouseName: '原料仓',
  batchId: '55',
  batchCode: 'B26092901',
  locationId: '64',
  requiredQty: 10,
  issuedQty: 4,
  shortQty: 6,
  currentQty: 6,
  lineStatus: 'PARTIAL'
});
const nextBatch = () => ({
  stockId: '100',
  materialId: '23',
  stockQuantity: '10.0000',
  reservedQuantity: '4.0000',
  availableQuantity: '6.0000',
  warehouseId: '9',
  warehouseCode: 'WH009',
  warehouseName: '二号原料仓',
  batchId: '56',
  batchCode: 'B26092902',
  locationId: '65',
  locationCode: 'A13',
  unitCost: 2
});

test('PARTIAL row can pick batch B after batch A is consumed without changing its source ID or issued facts', async () => {
  const line = partialPick();
  const context = stockPickerContext(line);
  const issuedFacts = {
    lineId: line.lineId,
    issueId: line.issueId,
    issuedQty: line.issuedQty,
    requiredQty: line.requiredQty,
    shortQty: line.shortQty,
    lineStatus: line.lineStatus
  };
  handler('openStockSelect', context)(0);
  assert.equal(context.stockSelectOpen.value, true, 'remaining 6 units must be allowed to select a new batch');
  assert.deepEqual(context.queried, ['23']);
  await drain();
  assert.equal(context.stockQuery.value.successful, true, '只允许成功读取的最新候选进入选择');
  handler('selectStock', context)(context.stockList.value[0]);
  assert.deepEqual([line.warehouseId, line.batchId, line.locationId], ['9', '56', '65']);
  assert.deepEqual(
    {
      lineId: line.lineId,
      issueId: line.issueId,
      issuedQty: line.issuedQty,
      requiredQty: line.requiredQty,
      shortQty: line.shortQty,
      lineStatus: line.lineStatus
    },
    issuedFacts
  );
  assert.equal(line.currentQty, 6);
  assert.equal(context.form.value.status, 'PARTIAL', 'picker must not reopen or complete the issue');
  assert.equal(context.form.value.warehouseId, '7');
  assert.equal(context.canCheckLine(line), false, 'issued line must remain non-deletable');
  assert.equal(context.proxy.$modal.warnings.length, 0);
});

test('deletion guard still prohibits every issued row, including a valid PARTIAL next pick', () => {
  const canCheckLine = handler('canCheckLine', {});
  assert.equal(canCheckLine(partialPick()), false);
  assert.equal(canCheckLine({ lineStatus: 'ISSUED', issuedQty: 0 }), false);
  assert.equal(canCheckLine({ lineStatus: 'PENDING', issuedQty: 0 }), true);
});

test('stock picker keeps terminal rows, terminal documents and readonly pages locked', () => {
  const cases = [
    ['COMPLETED', partialPick(), false],
    ['EXECUTED', partialPick(), false],
    ['PARTIAL', { ...partialPick(), lineStatus: 'ISSUED' }, false],
    ['PARTIAL', { ...partialPick(), lineStatus: 'COMPLETED', issuedQty: 0 }, false],
    ['PARTIAL', { ...partialPick(), lineStatus: 'EXECUTED', issuedQty: 0 }, false],
    ['PARTIAL', partialPick(), true]
  ];
  for (const [status, line, readOnly] of cases) {
    const context = stockPickerContext(line, status, readOnly);
    handler('openStockSelect', context)(0);
    assert.equal(context.stockSelectOpen.value, false, `${status}/${line.lineStatus}/readonly=${readOnly} must be locked`);
    assert.deepEqual(context.queried, []);
    assert.equal(context.proxy.$modal.warnings.length, 1);
  }
});

test('库存范围恢复允许未填和零量，仍拒绝非法数量或已无欠领需求', () => {
  const rows = [
    ...[null, 0, -1, 'invalid', Infinity].map((currentQty) => ({ materialId: '23', currentQty, issuedQty: 0, lineStatus: 'PENDING' })),
    { ...partialPick(), issuedQty: 10, shortQty: 6 } /** 过期的 shortQty 不能重新开放已完成的需求。 */,
    { ...partialPick(), requiredQty: null, shortQty: 0 },
    { ...partialPick(), requiredQty: null, shortQty: null }
  ];
  for (const line of rows) {
    const context = stockPickerContext(line);
    handler('openStockSelect', context)(0);
    const recovery = line.lineStatus === 'PENDING' && (line.currentQty == null || line.currentQty === 0);
    assert.equal(context.stockSelectOpen.value, recovery, '仅合法未填或零量可先恢复来源，非法量与无欠领仍关闭');
    assert.deepEqual(context.queried, recovery ? ['23'] : [], '恢复范围仍从真实候选查询建立');
    assert.equal(context.proxy.$modal.warnings.length, recovery ? 0 : 1, '错误数量或需求应给出明确提示');
  }
});

test('legacy partial row can use trustworthy outstanding shortQty, and a new manual pick is still selectable', () => {
  for (const line of [
    { ...partialPick(), requiredQty: null, shortQty: 6 },
    { materialId: '23', requiredQty: null, shortQty: null, issuedQty: 0, currentQty: 2, lineStatus: 'PENDING' }
  ]) {
    const context = stockPickerContext(line);
    handler('openStockSelect', context)(0);
    assert.equal(context.stockSelectOpen.value, true);
    assert.deepEqual(context.queried, ['23']);
  }
});

test('选择时重核只读与终态，清零恢复只建立范围且保持已领事实', async () => {
  const changes = [
    (context) => {
      context.isViewMode.value = true;
    },
    (context) => {
      context.form.value.status = 'COMPLETED';
    },
    (context) => {
      context.lineList.value[0].lineStatus = 'ISSUED';
    },
    (context) => {
      context.lineList.value[0].currentQty = 0;
    }
  ];
  for (const change of changes) {
    const context = stockPickerContext(partialPick());
    handler('openStockSelect', context)(0);
    await drain();
    assert.equal(context.stockQuery.value.successful, true, '先建立真实成功读取，才能证明选择时重核守卫');
    const candidate = context.stockList.value[0];
    change(context);
    const before = { ...context.lineList.value[0] };
    handler('selectStock', context)(candidate);
    if (before.currentQty === 0 && !context.isViewMode.value && context.form.value.status === 'PARTIAL' && before.lineStatus === 'PARTIAL') {
      assert.equal(context.lineList.value[0].batchId, candidate.batchId, '清零后仍能确认下一真实批次');
      assert.equal(context.lineList.value[0].currentQty, 0, '选范围不擅自填写本次量');
      assert.equal(context.lineList.value[0].issuedQty, before.issuedQty, '选范围不能重置已领事实');
      assert.equal(context.lineList.value[0].lineId, before.lineId, '选范围不能另造明细身份');
      assert.equal(context.proxy.$modal.warnings.length, 0, '合法恢复不能误报数量错误');
    } else {
      assert.deepEqual(context.lineList.value[0], before, '只读或终态仍不得被旧候选改写');
      assert.equal(context.proxy.$modal.warnings.length, 1, '只读或终态应明确拒绝');
    }
  }
});

test('stock selection sets actual line warehouse and keeps header warehouse and stock batch intact', async () => {
  const line = { warehouseId: '7', warehouseName: '原料仓', materialId: '23', currentQty: 2 };
  const context = stockPickerContext(line, 'CREATED');
  context.getAvailableStock = (materialId) => {
    context.queried.push(materialId);
    return Promise.resolve({ data: [{ ...nextBatch(), batchId: '55', batchCode: 'B26092901', locationId: '64', locationCode: 'A12' }] });
  };
  handler('openStockSelect', context)(0);
  await drain();
  assert.equal(context.stockQuery.value.successful, true, '必须通过本次真实成功读取，而非手工伪造成功标记');
  handler('selectStock', context)(context.stockList.value[0]);
  assert.deepEqual([line.warehouseId, line.warehouseCode, line.warehouseName], ['9', 'WH009', '二号原料仓']);
  assert.equal(context.form.value.warehouseId, '7');
  assert.equal(line.batchId, '55');
  assert.equal(context.stockSelectOpen.value, false);
});

test('stock without a warehouse is rejected before mutating a line', async () => {
  const line = { warehouseId: null, materialId: '23', currentQty: 2 };
  const context = stockPickerContext(line, 'CREATED');
  const unidentified = { ...nextBatch(), warehouseId: null };
  context.getAvailableStock = (materialId) => {
    context.queried.push(materialId);
    return Promise.resolve({ data: [unidentified] });
  };
  handler('openStockSelect', context)(0);
  await drain();
  assert.deepEqual(context.stockList.value, [], '缺少真实仓库的记录不得成为本次合法候选');
  handler('selectStock', context)(unidentified);
  assert.equal(line.batchId, undefined, '拒绝后不得绑定库存批次');
  assert.equal(context.stockSelectOpen.value, true, '无效选择不得冒充选料成功');
  assert.equal(context.proxy.$modal.warnings.length, 1, '应明确提示无效候选');
});

test('template disables unsafe posting, explains why, and shows each row warehouse', () => {
  assert.match(template, /:disabled="!executionWarehouseReady \|\| !executionAtpReady"/, '仓库及 ATP 任一不可靠均须关闭执行按钮');
  assert.match(template, /v-if="!isViewMode && canExecute && !executionWarehouseReady"[^>]*title="[^"]*仓库[^"]*"/);
  assert.match(template, /<el-table-column[^>]*label="本行仓库"/);
  assert.match(template, /@click="openStockSelect\(\$index\)"/);
  assert.match(template, /:disabled="!canSelectStock\(row\)"/);
  assert.match(template, /v-if="canSelectStock\(row\)"/);
  assert.match(template, /:selectable="canCheckLine"/, 'deletion still uses its original guard');
  assert.match(template, /@current-change="handleWarehouseSelect"/);
  assert.match(script.text, /status:\s*1\s*[,}]/, 'picker must request enabled warehouses only');
});
