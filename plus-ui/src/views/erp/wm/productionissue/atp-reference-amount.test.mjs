import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { test } from 'node:test';
import { parse } from '@vue/compiler-sfc';
import ts from 'typescript';

/** 只运行本地领料页面与纯函数，不挂载页面、不连接业务服务。 */
const source = readFileSync(new URL('./form.vue', import.meta.url), 'utf8');
const { descriptor, errors } = parse(source, { filename: 'form.vue' });
assert.deepEqual(errors, [], '领料页面应能正确解析');
const script = ts.createSourceFile('form.ts', descriptor.scriptSetup.content, ts.ScriptTarget.ES2022, true, ts.ScriptKind.TS);
const template = descriptor.template.content;
const utilityUrl = new URL('./referenceAmount.ts', import.meta.url);
const display = {};
const quantityCompiled = ts.transpileModule(readFileSync(new URL('./quantity.ts', import.meta.url), 'utf8'), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
}).outputText;
new Function('exports', quantityCompiled)(display);
if (existsSync(utilityUrl)) {
  const compiled = ts.transpileModule(readFileSync(utilityUrl, 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
  }).outputText;
  new Function('exports', 'require', compiled)(display, () => display);
}
function utility(name, ...args) {
  assert.equal(typeof display[name], 'function', `应提供${name}以统一参考金额口径`);
  return display[name](...args);
}
/** 提取实际范围函数并注入其真实依赖，不模拟或放宽范围校验结果。 */
function scopeDependencies(context) {
  const dependencies = { ...display, ...context };
  for (const name of ['lineStockScope', 'scopedAvailableQuantity', 'loadLineAvailableStock']) {
    if (!(name in dependencies)) dependencies[name] = (...args) => handler(name, dependencies)(...args);
  }
  return dependencies;
}
function handler(name, context) {
  const fn = script.statements.find((statement) => ts.isFunctionDeclaration(statement) && statement.name?.text === name);
  assert.ok(fn, `领料页面应保留${name}处理函数`);
  const compiled = ts.transpileModule(fn.getText(script), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None }
  }).outputText;
  const dependencies = scopeDependencies(context);
  return new Function(...Object.keys(dependencies), `${compiled}\nreturn ${name};`)(...Object.values(dependencies));
}
function stockContext(line) {
  return {
    ...display,
    lineList: { value: [line] },
    stockQuery: { value: null },
    stockRequestId: { value: 0 },
    stockList: { value: [] },
    stockLoading: { value: false },
    currentRowIndex: { value: 0 },
    stockSelectOpen: { value: true },
    canSelectStock: () => true,
    recalcRow: () => {},
    proxy: { $modal: { msgWarning: () => {} } }
  };
}

/** 夹具只建立最新成功查询身份，不放宽实际选择守卫。 */
function chooseStock(line, stock) {
  const context = stockContext(line);
  const row = { materialId: line.materialId, stockId: '100', stockQuantity: 10, reservedQuantity: 3, availableQuantity: 7, ...stock };
  context.stockList.value = [row];
  context.stockQuery.value = {
    requestId: 0,
    row: line,
    materialId: String(line.materialId),
    successful: true,
    identities: new Map([[row, display.stockCandidateIdentity(row)]])
  };
  handler('selectStock', context)(row);
  return context;
}

test('候选页分别展示账面量、预留量和 ATP，不把账面量标成可用量', () => {
  assert.match(template, /<el-table-column[^>]*label="账面数量"[^>]*prop="stockQuantity"/, '应明确保留账面量');
  assert.match(template, /<el-table-column[^>]*label="预留数量"[^>]*prop="reservedQuantity"/, '应展示预留量');
  assert.match(template, /<el-table-column[^>]*label="可用数量"[^>]*prop="availableQuantity"/, '可用列应读取 ATP');
  assert.doesNotMatch(template, /label="可用数量"[^>]*prop="stockQuantity"/, '账面库存不能冒充 ATP');
});

test('选定库存后本行可用量改为该候选 ATP，保持参考单价未知', () => {
  const line = { materialId: '23', currentQty: 6, availableQty: 100, unitPrice: null };
  chooseStock(line, { warehouseId: '7', stockQuantity: 10, reservedQuantity: 3, availableQuantity: 7, unitCost: 1.4 });
  assert.equal(line.availableQty, 7, '本行应使用候选 ATP 而不是全仓账面数量');
  assert.equal(line.unitPrice, null, '库存单位成本不得自动补参考单价');
});

test('选库存不得把已有零参考单价替换成真实成本', () => {
  const line = { materialId: '23', currentQty: 6, unitPrice: 0 };
  chooseStock(line, { warehouseId: '7', availableQuantity: 7, unitCost: 1.4 });
  assert.equal(line.unitPrice, 0, '已知参考价为零应保留真实零值');
});

test('缺失候选 ATP 保持未知，不回退到账面量或可靠零', () => {
  const line = { materialId: '23', currentQty: 6, availableQty: 100 };
  chooseStock(line, { warehouseId: '7', stockQuantity: 10, reservedQuantity: null, availableQuantity: null });
  assert.equal(line.batchId, undefined, '缺失 ATP 不可选择库存');
});

test('清除参考价格时同时清除旧参考金额', () => {
  const row = { currentQty: 6, issuedQty: 0, unitPrice: null, amount: 2.52 };
  handler('recalcRow', display)(row);
  assert.equal(row.amount, null, '清除参考价格不能继续展示旧金额');
});

test('完成行的本次量为零时参考金额按累计已领量计算', () => {
  const row = { currentQty: 0, issuedQty: 6, unitPrice: 0.42, amount: 2.52 };
  handler('recalcRow', display)(row);
  assert.equal(row.amount, 2.52, '清零本次量不能把已领参考金额变成零');
});

test('原三行参考价未知的历史单头零总额不能展示为完整总价', () => {
  const lines = [6, 6, 12].map((issuedQty) => ({ currentQty: 0, issuedQty, unitPrice: null, amount: null }));
  assert.equal(utility('referenceTotalAmount', lines), null, '三行未计价应令参考总额未知');
  assert.equal(utility('formatReferenceAmount', null), '未计价', '未知参考金额应清楚展示未计价');
});

test('混合已计价和未计价行不漏掉未知行宣称完整总价', () => {
  const lines = [
    { currentQty: 6, unitPrice: 0.42, amount: 2.52 },
    { currentQty: 6, unitPrice: null, amount: null }
  ];
  assert.equal(utility('referenceTotalAmount', lines), null, '部分金额已知仍不能生成完整参考总价');
});

test('已知零单价和零金额均应真实展示零', () => {
  assert.equal(utility('referenceLineAmount', { currentQty: 6, unitPrice: 0, amount: null }), 0, '已知零价应计算为零');
  assert.equal(utility('referenceTotalAmount', [{ unitPrice: null, amount: 0 }]), 0, '已知零金额应参与合计');
  assert.equal(utility('formatReferenceAmount', 0, true), '0.0000', '真实零不能展示未计价');
});

test('仅使用参考明细金额或参考价，不读取库存成本', () => {
  assert.equal(utility('referenceLineAmount', { currentQty: 6, unitPrice: null, amount: null, unitCost: 1.4 }), null, '真实库存成本不能成为参考价');
  assert.equal(utility('referenceLineAmount', { currentQty: 6, unitPrice: 0.42, amount: null }), 2.52, '已知参考单价可计算参考金额');
  assert.equal(utility('referenceLineAmount', { currentQty: 6, unitPrice: 0.42, amount: 3 }), 3, '既有已知参考金额应优先保留');
});

test('未知数量、空字符串和非有限金额不能成为可靠零', () => {
  for (const value of [null, undefined, '', '未知', NaN, Infinity]) {
    assert.equal(utility('formatReferenceAmount', value), '未计价', '未知或无效值应展示未计价');
  }
  assert.equal(
    utility('referenceLineAmount', { currentQty: null, issuedQty: null, unitPrice: 1, amount: null }),
    null,
    '未知领料数量不能参与可靠金额计算'
  );
});

test('无明细保持未知与已计价三行合计分别正确', () => {
  assert.equal(utility('referenceTotalAmount', []), null, '无有效明细不能证明参考已知零');
  const lines = [
    { currentQty: 0, issuedQty: 6, unitPrice: 0.42 },
    { currentQty: 0, issuedQty: 6, unitPrice: 0.67 },
    { currentQty: 0, issuedQty: 12, unitPrice: 1.4 }
  ];
  assert.equal(utility('referenceTotalAmount', lines), 23.34, '仅已知参考价的三行可形成完整参考合计');
});

test('页面显式标注参考价与参考金额，不把库存成本混为参考金额', () => {
  assert.match(template, /label="参考单价"/, '单价列应明确参考口径');
  assert.match(template, /label="参考金额"/, '金额列应明确参考口径');
  assert.match(template, /formatReferenceAmount\(referenceLineAmount\(row\), referenceLineAmount\(row\) != null\)/, '明细未知金额应使用未计价展示');
  assert.match(template, /formatReferenceAmount\(referenceTotal, referenceKnown\)/, '单头参考总额应统一展示');
  assert.match(template, /参考金额[^<]*不代表[^<]*库存成本/, '页面应明确参考金额不是库存成本');
});

test('候选数量未知或无正 ATP 时界面禁止选料', () => {
  assert.match(template, /:disabled="[^"]*availableQuantity[^"]*== null/, 'ATP 未知时不能启用选料按钮');
  assert.match(template, /:disabled="[^"]*reservedQuantity[^"]*== null/, '预留量未知时不能启用选料按钮');
  assert.match(template, /:disabled="[^"]*stockQuantity[^"]*== null/, '账面量未知时不能启用选料按钮');
});

test('刷新库存失败时清除旧可靠数量，不能留下过期可用量', async () => {
  const lineList = { value: [{ materialId: '23', availableQty: 100 }] };
  handler('loadAvailableStocks', {
    ...display,
    form: { value: {} },
    lineList,
    getAvailableStockBatch: () => Promise.reject(new Error('离线测试异常'))
  })();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(lineList.value[0].availableQty, null, '查询失败应将原可用量重置为未知');
});
test('只改变仓库批次时保留既有参考金额，不重新按价格覆盖', () => {
  const line = { materialId: '23', currentQty: 6, issuedQty: 0, unitPrice: 0.42, amount: 3 };
  chooseStock(line, { warehouseId: '7', stockQuantity: 10, reservedQuantity: 3, availableQuantity: 7 });
  assert.equal(line.amount, 3, '选库存不改变参考价格或数量，不应覆盖已知参考金额');
});

test('加载未知已领数量时不兜底成可靠零参考金额', async () => {
  const form = { value: {} };
  const lineList = { value: [] };
  handler('getInfo', {
    ...display,
    form,
    lineList,
    loadAvailableStocks: () => {},
    getProductionIssue: () =>
      Promise.resolve({
        data: {
          issueId: '41',
          totalAmount: null,
          wmProductionIssueLineList: [{ currentQty: 0, issuedQty: null, requiredQty: 6, shortQty: null, unitPrice: 1, amount: null }]
        }
      })
  })('41');
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(lineList.value[0].issuedQty, null, '未知已领量不能在页面载入时变成零');
  assert.equal(lineList.value[0].shortQty, null, '未知已领量不能据此推算可靠欠领量');
  assert.equal(utility('referenceTotalAmount', lineList.value), null, '载入后参考总额仍应保持未知');
});
