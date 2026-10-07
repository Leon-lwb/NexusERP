import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { test } from 'node:test';
import { parse } from '@vue/compiler-sfc';
import ts from 'typescript';
import { ref as vueRef, computed as vueComputed } from 'vue';

/** 第二候选反例直接执行本地页面函数与纯工具，不挂载 Vue 或连接接口。 */
const source = readFileSync(new URL('./form.vue', import.meta.url), 'utf8');
const types = readFileSync(new URL('../../../../api/erp/wm/productionissue/types.ts', import.meta.url), 'utf8');
const listSource = readFileSync(new URL('./index.vue', import.meta.url), 'utf8');
const { descriptor, errors } = parse(source, { filename: 'form.vue' });
assert.deepEqual(errors, [], '表单应能解析');
const script = ts.createSourceFile('form.ts', descriptor.scriptSetup.content, ts.ScriptTarget.ES2022, true, ts.ScriptKind.TS);
const template = descriptor.template.content;
const utilities = {};
for (const file of ['referenceAmount.ts', 'quantity.ts']) {
  const url = new URL('./' + file, import.meta.url);
  if (!existsSync(url)) continue;
  const compiled = ts.transpileModule(readFileSync(url, 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
  }).outputText;
  new Function('exports', 'require', compiled)(utilities, () => utilities);
}
function utility(name, ...args) {
  assert.equal(typeof utilities[name], 'function', `必须提供${name}实现批准口径`);
  return utilities[name](...args);
}
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
  assert.ok(fn, `表单应提供${name}函数`);
  const compiled = ts.transpileModule(fn.getText(script), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None }
  }).outputText;
  const dependencies = scopeDependencies(context);
  return new Function(...Object.keys(dependencies), `${compiled}\nreturn ${name};`)(...Object.values(dependencies));
}
const computed = (callback) => ({
  get value() {
    return callback();
  }
});
function computedValue(name, context) {
  const declarations = script.statements.filter(ts.isVariableStatement).flatMap((statement) => statement.declarationList.declarations);
  const node = declarations.find((declaration) => declaration.name.text === name);
  assert.ok(node?.initializer, `必须提供${name}计算状态`);
  const code = ts.transpileModule(`const ${name} = ${node.initializer.getText(script)};`, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None }
  }).outputText;
  const dependencies = scopeDependencies(context);
  return new Function(...Object.keys(dependencies), `${code}\nreturn ${name};`)(...Object.values(dependencies));
}
const drain = () => new Promise((resolve) => setImmediate(resolve));
function pickerContext(line) {
  const warnings = [];
  const context = {
    ...utilities,
    lineList: { value: [line] },
    currentRowIndex: { value: 0 },
    stockSelectOpen: { value: false },
    stockLoading: { value: false },
    stockList: { value: [] },
    stockQuery: { value: null },
    stockRequestId: { value: 0 },
    form: { value: { warehouseId: '7', status: 'CREATED' } },
    canSelectStock: () => true,
    recalcRow: () => {},
    proxy: { $modal: { msgWarning: (message) => warnings.push(message) } },
    warnings,
    getAvailableStock: () => Promise.resolve({ data: [] })
  };
  context.isViewMode = { value: false };
  context.canEditLine = computedValue('canEditLine', { computed, form: context.form });
  context.canSelectStock = (row) => handler('canSelectStock', context)(row);
  return context;
}
const candidate = (materialId = '23') => ({
  stockId: '100',
  materialId,
  warehouseId: '7',
  batchId: '55',
  batchCode: '批次甲',
  stockQuantity: '10.0000',
  reservedQuantity: '4.0000',
  availableQuantity: '6.0000'
});

test('R1：没有有效明细不能证明参考零金额', () => {
  assert.equal(utility('referenceTotalAmount', []), null, '空明细应返回未知参考金额');
});

test('R2：未知累计数量不转换为参考零金额', () => {
  assert.equal(utility('referenceLineAmount', { currentQty: 0, issuedQty: null, unitPrice: 1, amount: null }), null, '未知数量应保持未计价');
});

test('R3：主动清价携带明确标志并清除旧金额', () => {
  const row = { lineId: '42', currentQty: 6, issuedQty: 0, unitPrice: null, amount: 6 };
  handler('handleReferencePriceChange', utilities)(row);
  assert.equal(row.clearReferencePrice, true, '用户清价必须明确携带标志');
  assert.equal(row.unitPrice, null, '主动清空参考单价');
  assert.equal(row.amount, null, '主动清空旧参考金额');
});

test('R3：设置真实零参考价不能被当作清价', () => {
  const row = { currentQty: 6, issuedQty: 0, unitPrice: 0, amount: null, clearReferencePrice: true };
  handler('handleReferencePriceChange', utilities)(row);
  assert.equal(row.clearReferencePrice, false, '已知零价不是清价');
  assert.equal(row.amount, 0, '真实零价应计算真实零金额');
});

test('R4：线协议 decimal 声明 string 与 number，不伪称只有 number', () => {
  assert.match(types, /type\s+ProductionIssueDecimal\s*=\s*(?:string\s*\|\s*number|number\s*\|\s*string)/, '必须如实声明十进制线协议');
  assert.match(types, /availableQuantity:\s*ProductionIssueDecimal\s*\|\s*null/, '候选 ATP 类型必须支持字符串');
});

test('R4：严格十进制归一拒绝空白、布尔、十六进制及非有限值', () => {
  for (const value of [null, undefined, '', ' ', true, false, '0x10', 'Infinity', '6件', NaN, Infinity]) {
    assert.equal(utility('readDecimalNumber', value), null, '非可靠十进制应保持未知');
  }
  assert.equal(utility('readDecimalNumber', '10.0000'), 10, '真实十进制字符串应转为数值');
  assert.equal(utility('readDecimalNumber', '0.0000'), 0, '真实零字符串不能变成未知');
});

test('R4：字符串数量的 ATP 比较为数值比较', () => {
  assert.equal(utility('quantityWithinAtp', '10.0000', '6.0000'), false, '10 必须大于 6，不能按字符串比较');
  assert.equal(utility('quantityWithinAtp', '6.0000', '6.0000'), true, '上限相等仍可执行');
  assert.equal(utility('quantityWithinAtp', '1.0000', null), false, '未知 ATP 不可确认');
});

test('R4：实际 getInfo 边界归一输入数量与参考单价', async () => {
  const lineList = { value: [] },
    form = { value: {} };
  handler('getInfo', {
    ...utilities,
    lineList,
    form,
    loadAvailableStocks: () => {},
    getProductionIssue: () =>
      Promise.resolve({
        data: {
          referenceAmountKnown: true,
          wmProductionIssueLineList: [{ currentQty: '10.0000', issuedQty: '0.0000', unitPrice: '0.4200', amount: null, availableQty: '6.0000' }]
        }
      })
  })('41');
  await drain();
  assert.equal(lineList.value[0].currentQty, 10, '输入本次量必须归一为数值');
  assert.equal(lineList.value[0].unitPrice, 0.42, '参考输入价格必须归一为数值');
});

test('R5：本次领料输入 max 明确使用独立 ATP', () => {
  assert.match(template, /:max="scopedAvailableQuantity\(row\) \?\? undefined"/, '输入框须设置 ATP 上限');
  assert.match(template, /:disabled="scopedAvailableQuantity\(row\) == null"/, '未知 ATP 输入应标为不可确认');
  assert.match(template, /!executionAtpReady/, '执行按钮应由 ATP 可信上限控制');
});

test('R5：执行就绪状态拒绝超量及未知 ATP', () => {
  for (const availableQty of [6, null, '未知']) {
    const ready = computedValue('executionAtpReady', {
      ...utilities,
      computed,
      form: { value: {} },
      lineList: { value: [{ materialId: '23', warehouseId: '7', currentQty: '10.0000', availableQty, atpScopeKey: '23/7//' }] }
    });
    assert.equal(ready.value, false, '未知或不足 ATP 不得允许执行');
  }
  const ready = computedValue('executionAtpReady', {
    ...utilities,
    computed,
    form: { value: {} },
    lineList: { value: [{ materialId: '23', warehouseId: '7', currentQty: 6, availableQty: 6, atpScopeKey: '23/7//' }] }
  });
  assert.equal(ready.value, true, '可信 ATP 正控须允许上限相等');
});

test('R6：列表格式化缺失 known 不能把头零显示为已计价', () => {
  assert.equal(utility('formatReferenceAmount', 0, undefined), '未计价', '状态缺失不能当作已知');
  assert.equal(utility('formatReferenceAmount', 0, false), '未计价', '明确未知不能展示可靠零');
  assert.equal(utility('formatReferenceAmount', 0, true), '0.0000', '明确已知的合法零必须展示');
  assert.match(types, /referenceAmountKnown:\s*boolean/, '返回类型须显式布尔');
  assert.match(listSource, /formatReferenceAmount\(row\.totalAmount, row\.referenceAmountKnown\)/, '列表应贯通明确 known 状态');
});

test('R6：参考总额状态显式已知或未知，不用缺失代替 false', () => {
  assert.deepEqual(utility('referenceAmountState', []), { amount: null, referenceAmountKnown: false }, '无明细证据应明确 false');
  assert.deepEqual(utility('referenceAmountState', [{ amount: 0 }]), { amount: 0, referenceAmountKnown: true }, '已知零应明确 true');
});

test('R7：读取新物料失败清空旧候选，不能绑定上一物料库存', async () => {
  const line = { materialId: '24', currentQty: 1 },
    context = pickerContext(line);
  const old = candidate('23');
  context.stockList.value = [old];
  context.getAvailableStock = () => Promise.reject(new Error('离线读取失败'));
  handler('openStockSelect', context)(0);
  await drain();
  assert.deepEqual(context.stockList.value, [], '新查询失败不得留旧候选');
  handler('selectStock', context)(old);
  assert.equal(line.batchId, undefined, '上一物料候选不得绑定本行');
});

test('R7：并发打开另一物料后旧响应不能覆盖最新候选', async () => {
  const context = pickerContext({ materialId: '23', currentQty: 1 });
  context.lineList.value.push({ materialId: '24', currentQty: 1 });
  let resolveFirst, resolveSecond;
  context.getAvailableStock = (materialId) =>
    new Promise((resolve) => {
      if (materialId === '23') resolveFirst = resolve;
      else resolveSecond = resolve;
    });
  const open = handler('openStockSelect', context);
  open(0);
  open(1);
  const second = candidate('24');
  resolveSecond({ data: [second] });
  await drain();
  resolveFirst({ data: [candidate('23')] });
  await drain();
  assert.equal(context.stockList.value[0].materialId, '24', '旧响应不得替换最新物料候选');
});

test('R7：只接受本次成功候选且拒绝物料或仓库身份被改变', async () => {
  for (const mutate of [
    (row) => {
      row.materialId = '24';
    },
    (row) => {
      row.warehouseId = '9';
    }
  ]) {
    const line = { materialId: '23', currentQty: 1 },
      context = pickerContext(line);
    context.getAvailableStock = () => Promise.resolve({ data: [candidate()] });
    handler('openStockSelect', context)(0);
    await drain();
    const picked = context.stockList.value[0];
    mutate(picked);
    handler('selectStock', context)(picked);
    assert.equal(line.batchId, undefined, '候选身份漂移须失败关闭');
  }
});

test('R7：最新成功且身份一致候选可选择并使用数值 ATP', async () => {
  const line = { materialId: '23', currentQty: 1, unitPrice: null },
    context = pickerContext(line);
  context.getAvailableStock = () => Promise.resolve({ data: [candidate()] });
  handler('openStockSelect', context)(0);
  await drain();
  handler('selectStock', context)(context.stockList.value[0]);
  assert.equal(line.batchId, '55', '合法成功查询仍可选料');
  assert.equal(line.availableQty, 6, '选料后 ATP 应归一为数值');
  assert.equal(line.unitPrice, null, '合法选料不得填参考价');
});

test('R5：真实执行处理函数对未知或超量失败关闭且不调用保存过账', () => {
  for (const ready of [false]) {
    let confirmation = 0,
      writes = 0;
    const warnings = [];
    handler('handleExecute', {
      form: { value: { issueId: '41', warehouseId: '7' } },
      executionWarehouseReady: { value: true },
      executionAtpReady: { value: ready },
      lineList: { value: [{ currentQty: 10, availableQty: 6 }] },
      proxy: {
        $modal: {
          msgWarning: (value) => warnings.push(value),
          confirm: () => {
            confirmation++;
            return Promise.resolve();
          }
        }
      },
      updateProductionIssue: () => {
        writes++;
      },
      executeProductionIssue: () => {
        writes++;
      }
    })();
    assert.equal(confirmation, 0, '不可信 ATP 不得进入确认后保存流程');
    assert.equal(writes, 0, '不可信 ATP 不得调用任何写请求');
    assert.equal(warnings.length, 1, '失败原因应提示用户');
  }
});

test('R7：最新成功候选的数量变成未知后仍不得选料', async () => {
  for (const field of ['stockQuantity', 'reservedQuantity', 'availableQuantity']) {
    const line = { materialId: '23', currentQty: 1 },
      context = pickerContext(line);
    context.getAvailableStock = () => Promise.resolve({ data: [candidate()] });
    handler('openStockSelect', context)(0);
    await drain();
    const row = context.stockList.value[0];
    row[field] = null;
    handler('selectStock', context)(row);
    assert.equal(line.batchId, undefined, '任一候选数量未知都不能通过成功标记绕过');
  }
});

test('既有终态、只读和已领满守卫在新查询状态下仍有效', () => {
  for (const change of [
    (context) => {
      context.form.value.status = 'COMPLETED';
    },
    (context) => {
      context.form.value.status = 'EXECUTED';
    },
    (context) => {
      context.isViewMode.value = true;
    },
    (context) => {
      context.lineList.value[0].lineStatus = 'ISSUED';
    },
    (context) => {
      context.lineList.value[0].requiredQty = 6;
      context.lineList.value[0].issuedQty = 6;
    }
  ]) {
    const context = pickerContext({ materialId: '23', currentQty: 1 });
    let requests = 0;
    context.getAvailableStock = () => {
      requests++;
      return Promise.resolve({ data: [] });
    };
    change(context);
    handler('openStockSelect', context)(0);
    assert.equal(context.stockSelectOpen.value, false, '终态、只读或无欠领量不允许选料');
    assert.equal(requests, 0, '禁止状态不得发起候选读取');
  }
});

test('已成功查询后切换只读仍不得选择，保留原行事实', async () => {
  const line = { materialId: '23', currentQty: 1, issuedQty: 4, requiredQty: 10, lineId: '42', lineStatus: 'PARTIAL' },
    context = pickerContext(line);
  context.getAvailableStock = () => Promise.resolve({ data: [candidate()] });
  handler('openStockSelect', context)(0);
  await drain();
  context.isViewMode.value = true;
  const before = { ...line };
  handler('selectStock', context)(context.stockList.value[0]);
  assert.deepEqual(line, before, '读取后权限或状态变化必须重核且不能修改行');
});

test('部分领料正控可更换有效候选但不改已领量和原单头仓库', async () => {
  const line = { materialId: '23', currentQty: 6, issuedQty: 4, requiredQty: 10, shortQty: 6, lineId: '42', lineStatus: 'PARTIAL' },
    context = pickerContext(line);
  context.getAvailableStock = () => Promise.resolve({ data: [{ ...candidate(), warehouseId: '9' }] });
  handler('openStockSelect', context)(0);
  await drain();
  handler('selectStock', context)(context.stockList.value[0]);
  assert.deepEqual(
    [line.lineId, line.issuedQty, line.requiredQty, line.shortQty, line.lineStatus],
    ['42', 4, 10, 6, 'PARTIAL'],
    '换批次不得更改原行来源与执行事实'
  );
  assert.equal(line.warehouseId, '9', '有效候选可绑定实际行仓库');
  assert.equal(context.form.value.warehouseId, '7', '不得隐式更改单头仓库');
  assert.equal(handler('canCheckLine', {})(line), false, '已领行仍不得勾选删除');
});

test('R6：详情缺少 known 字段即使明细零金额也不能推断已知', async () => {
  const lineList = { value: [] },
    form = { value: {} };
  handler('getInfo', {
    ...utilities,
    lineList,
    form,
    loadAvailableStocks: () => {},
    getProductionIssue: () =>
      Promise.resolve({
        data: {
          issueId: '41',
          totalAmount: '0.0000',
          wmProductionIssueLineList: [{ currentQty: '0.0000', issuedQty: '6.0000', unitPrice: '0.0000', amount: '0.0000' }]
        }
      })
  })('41');
  await drain();
  const known = computedValue('referenceKnown', { ...utilities, computed, form, lineList });
  assert.equal(form.value.referenceAmountKnown, false, '契约缺失必须归一为明确未知');
  assert.equal(known.value, false, '详情 UI 不可绕开未知契约使用头零');
});
test('R5/R7：候选读取失败后本行旧可靠 ATP 失效，不能继续执行', async () => {
  const line = { materialId: '23', currentQty: 1, availableQty: 100 },
    context = pickerContext(line);
  context.getAvailableStock = () => Promise.reject(new Error('离线读取失败'));
  handler('openStockSelect', context)(0);
  await drain();
  assert.equal(line.availableQty, null, '失败读取不能保留本行旧可用量');
  assert.equal(computedValue('executionAtpReady', { ...context, computed }).value, false, '旧可靠 ATP 失效后必须禁止执行');
});
function bindScopeFunctions(context) {
  for (const name of ['lineStockScope', 'scopedAvailableQuantity', 'loadLineAvailableStock']) {
    const fn = script.statements.find((statement) => ts.isFunctionDeclaration(statement) && statement.name?.text === name);
    if (fn) context[name] = (...args) => handler(name, context)(...args);
  }
  return context;
}

/** 独立 R5 反例使用真实 Vue 响应式，不用普通对象代替候选 Map 的代理行为。 */
function reactiveScopeContext(context) {
  for (const name of ['lineList', 'form', 'stockSelectOpen', 'stockLoading', 'stockList', 'stockQuery', 'stockRequestId', 'currentRowIndex']) {
    context[name] = vueRef(context[name].value);
  }
  return bindScopeFunctions(context);
}

test('R5范围：较早全仓汇总返回一百不能覆盖后来选定批次的六件上限', async () => {
  const line = { materialId: '23', currentQty: 6, issuedQty: 0, availableQty: 100 };
  const context = reactiveScopeContext(pickerContext(line));
  let finishSummary;
  context.getAvailableStockBatch = () =>
    new Promise((resolve) => {
      finishSummary = resolve;
    });
  context.getAvailableStock = () => Promise.resolve({ data: [{ ...candidate(), locationId: '64' }] });
  handler('loadAvailableStocks', context)();
  handler('openStockSelect', context)(0);
  await drain();
  handler('selectStock', context)(context.stockList.value[0]);
  assert.equal(line.availableQty, 6, '成功选料后应使用真实六件范围上限');
  finishSummary({ data: { '23': '100.0000' } });
  await drain();
  assert.equal(line.availableQty, 6, '全仓参考量不得恢复或扩大已绑定范围上限');
  line.currentQty = 10;
  context.executionAtpReady = computedValue('executionAtpReady', { ...context, computed: vueComputed });
  assert.equal(context.executionAtpReady.value, false, '十件超过本行范围六件，不得允许执行');
  const writes = [];
  context.form.value.issueId = '41';
  context.executionWarehouseReady = { value: true };
  context.executeLoading = { value: false };
  context.proxy.$modal.confirm = () => Promise.resolve();
  context.updateProductionIssue = () => {
    writes.push('保存');
    return Promise.resolve();
  };
  context.executeProductionIssue = () => {
    writes.push('过账');
    return Promise.resolve();
  };
  context.getInfo = () => {};
  handler('handleExecute', context)();
  await drain();
  assert.deepEqual(writes, [], '超过已绑定范围上限不能调用本地写请求替身');
});

test('R5范围：重载已绑定仓库批次库位时全仓一百不是本行可靠上限', async () => {
  const context = reactiveScopeContext(pickerContext({}));
  context.getProductionIssue = () =>
    Promise.resolve({
      data: {
        issueId: '41',
        warehouseId: '7',
        referenceAmountKnown: false,
        wmProductionIssueLineList: [
          { materialId: '23', warehouseId: '7', batchId: '55', locationId: '64', currentQty: '10.0000', issuedQty: '0.0000' }
        ]
      }
    });
  context.getAvailableStockBatch = () => Promise.resolve({ data: { '23': '100.0000' } });
  let finishScope;
  context.getAvailableStock = () =>
    new Promise((resolve) => {
      finishScope = resolve;
    });
  context.loadAvailableStocks = () => handler('loadAvailableStocks', context)();
  handler('getInfo', context)('41');
  await drain();
  const line = context.lineList.value[0];
  assert.equal(line.availableQty, null, '范围读取待确认时即使全仓总量已到达也必须未知');
  const ready = computedValue('executionAtpReady', { ...context, computed: vueComputed });
  assert.equal(ready.value, false, '未确认绑定范围前必须关闭执行');
  assert.equal(typeof finishScope, 'function', '应调用现有库存接口确认绑定范围，而不是全仓量兜底');
  finishScope({ data: [{ ...candidate(), locationId: '64' }] });
  await drain();
  assert.equal(line.availableQty, 6, '重载后应确认绑定范围的真实六件 ATP');
  assert.equal(ready.value, false, '本次十件仍超过已确认范围六件');
});
test('R5范围：切换单头仓须重查未绑定行，先前范围回包不能恢复旧上限', async () => {
  const line = { materialId: '23', batchId: '55', locationId: '64', currentQty: 2, issuedQty: 0 };
  const context = bindScopeFunctions(pickerContext(line));
  context.warehouseSelectOpen = { value: true };
  const requests = [];
  const responses = {};
  context.getAvailableStock = (materialId, warehouseId) =>
    new Promise((resolve) => {
      requests.push(String(warehouseId));
      responses[String(warehouseId)] = resolve;
    });
  context.loadLineAvailableStock(line);
  handler('handleWarehouseSelect', context)({ warehouseId: '9', warehouseCode: '仓九', warehouseName: '离线仓九', status: 1 });
  assert.deepEqual(requests, ['7', '9'], '真实单头仓切换后必须重新确认新范围，不得保留旧仓上限');
  responses['7']({ data: [{ ...candidate(), locationId: '64', availableQuantity: '100.0000' }] });
  await drain();
  assert.equal(line.availableQty, null, '旧仓范围响应不得把新仓待确认状态恢复为可靠');
  responses['9']({
    data: [{ ...candidate(), warehouseId: '9', locationId: '64', stockQuantity: '6.0000', reservedQuantity: '3.0000', availableQuantity: '3.0000' }]
  });
  await drain();
  assert.equal(context.scopedAvailableQuantity(line), 3, '只有新仓匹配批次库位的范围量可成为上限');
});

test('R5范围：只汇总本仓本批次本库位，不借其他范围库存扩大上限', async () => {
  const line = { materialId: '23', warehouseId: '7', batchId: '55', locationId: '64', currentQty: 6, issuedQty: 0 };
  const context = reactiveScopeContext(pickerContext(line));
  const requests = [];
  context.getAvailableStock = (materialId, warehouseId) => {
    requests.push([String(materialId), String(warehouseId)]);
    return Promise.resolve({
      data: [
        { ...candidate(), locationId: '64' },
        { ...candidate(), warehouseId: '9', locationId: '64', availableQuantity: '100.0000' },
        { ...candidate(), batchId: '56', locationId: '64', availableQuantity: '100.0000' },
        { ...candidate(), locationId: '65', availableQuantity: '100.0000' }
      ]
    });
  };
  context.loadLineAvailableStock(context.lineList.value[0]);
  await drain();
  assert.deepEqual(requests, [['23', '7']], '先以物料和真实行仓限定读取接口');
  assert.equal(context.scopedAvailableQuantity(context.lineList.value[0]), 6, '其他仓、批次和库位均不得进入本行六件上限');
  context.lineList.value[0].locationId = '65';
  assert.equal(context.scopedAvailableQuantity(context.lineList.value[0]), null, '绑定身份漂移后旧上限必须立即未知');
});

test('R5范围：较早范围读取也不能覆盖随后选定候选的可靠上限', async () => {
  const context = reactiveScopeContext(pickerContext({ materialId: '23', currentQty: 6, issuedQty: 0 }));
  let finishEarlier;
  context.getAvailableStock = (materialId, warehouseId) =>
    warehouseId == null
      ? Promise.resolve({ data: [{ ...candidate(), locationId: '64' }] })
      : new Promise((resolve) => {
          finishEarlier = resolve;
        });
  context.getAvailableStockBatch = () => Promise.resolve({ data: { '23': '100.0000' } });
  handler('loadAvailableStocks', context)();
  handler('openStockSelect', context)(0);
  await drain();
  handler('selectStock', context)(context.stockList.value[0]);
  const line = context.lineList.value[0];
  assert.equal(context.scopedAvailableQuantity(line), 6, '新候选已确认六件上限');
  finishEarlier({
    data: [{ ...candidate(), locationId: '64', stockQuantity: '100.0000', reservedQuantity: '0.0000', availableQuantity: '100.0000' }]
  });
  await drain();
  assert.equal(context.scopedAvailableQuantity(line), 6, '被新选择作废的范围请求不得回写旧上限');
});

test('Q1恢复：部分领料清零且原批次耗尽时能先选下一批次再填写本次数量', async () => {
  const line = {
    lineId: '42',
    materialId: '23',
    warehouseId: '7',
    batchId: '55',
    locationId: '64',
    requiredQty: 10,
    issuedQty: 4,
    shortQty: 6,
    currentQty: 0,
    lineStatus: 'PARTIAL',
    availableQty: null,
    atpScopeKey: null
  };
  const context = pickerContext(line);
  context.form.value.status = 'PARTIAL';
  context.canExecute = { value: true };
  const next = { ...candidate(), batchId: '56', batchCode: '批次乙', locationId: '64' };
  context.getAvailableStock = () => Promise.resolve({ data: [next] });
  assert.equal(context.canSelectStock(line), true, '不能要求先填正数才允许恢复耗尽的批次范围');
  handler('openStockSelect', context)(0);
  await drain();
  handler('selectStock', context)(context.stockList.value[0]);
  assert.equal(line.batchId, '56', '应绑定最新读取的下一真实批次');
  assert.equal(line.availableQty, 6, '行上限只能是下一批次独立的六件 ATP');
  assert.equal(line.currentQty, 0, '选范围不能擅自填写本次领料数量');
  assert.equal(line.issuedQty, 4, '续领选批次不得重置原累计已领数量');
  assert.equal(line.lineId, '42', '续领不能更换原持久明细身份');
  const readiness = computedValue('executionAtpReady', { ...context, computed });
  assert.equal(readiness.value, false, '本次零量仍不可执行');
  line.currentQty = 7;
  assert.equal(readiness.value, false, '超过独立批次 ATP 仍不可执行');
  line.currentQty = 6;
  assert.equal(readiness.value, true, '范围确认后合法正量才可通过数量守卫');
});

test('Q1恢复：手工行本次未填写且头仓未选择时仍可先建立真实行仓范围', async () => {
  const line = {
    materialId: '23',
    requiredQty: null,
    shortQty: null,
    issuedQty: 0,
    currentQty: null,
    lineStatus: 'PENDING',
    availableQty: null,
    atpScopeKey: null
  };
  const context = pickerContext(line);
  context.form.value.warehouseId = null;
  context.canExecute = { value: true };
  context.getAvailableStock = () => Promise.resolve({ data: [{ ...candidate(), locationId: '64' }] });
  assert.equal(context.canSelectStock(line), true, '不能隐藏首次确定库存来源的入口');
  handler('openStockSelect', context)(0);
  await drain();
  handler('selectStock', context)(context.stockList.value[0]);
  assert.equal(line.warehouseId, '7', '首次选择只确认真实候选的行仓身份');
  assert.equal(line.availableQty, 6, '缺头仓不能借全仓参考量作为执行上限');
  assert.equal(line.currentQty, null, '确定来源不代表已经输入领料数量');
  assert.equal(computedValue('executionAtpReady', { ...context, computed }).value, false, '未填正量不能变为执行就绪');
});

test('Q1恢复：允许零量建立范围不能放行已关闭欠料行', () => {
  const line = { materialId: '23', currentQty: 0, requiredQty: 10, issuedQty: 4, shortQty: 6, lineStatus: 'SHORT_CLOSED' };
  const context = pickerContext(line);
  assert.equal(context.canSelectStock(line), false, '历史欠料关闭状态不能被恢复入口重新激活');
});

test('Q1恢复：已有正量超出新候选 ATP 时仍不绑定该候选', async () => {
  const line = { materialId: '23', currentQty: '10.0000', issuedQty: 0, lineStatus: 'PENDING' };
  const context = pickerContext(line);
  context.getAvailableStock = () => Promise.resolve({ data: [candidate()] });
  handler('openStockSelect', context)(0);
  await drain();
  handler('selectStock', context)(context.stockList.value[0]);
  assert.equal(line.batchId, undefined, '恢复入口不放宽正数数量与 ATP 的已批准比较约束');
});
