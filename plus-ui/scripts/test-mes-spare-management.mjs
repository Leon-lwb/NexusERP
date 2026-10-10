/**
 * MES 备件四菜单离线回归：执行真实 SFC setup 与 useCrud，API/弹窗为隔离 port。
 * 不发 HTTP、不访问 DB、不写共享构建目录；转译和模板解析不等于全量类型检查。
 * node --test --test-concurrency=1 scripts/test-mes-spare-management.mjs
 */
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { test } from 'node:test';
import { parse as parseSfc, compileTemplate } from '@vue/compiler-sfc';
import ts from 'typescript';
import * as Vue from 'vue';

const ID = '9007199254740993';
const WAREHOUSE_ID = '9007199254740995';
const OTHER_WAREHOUSE_ID = '9007199254740997';
const displayUrl = new URL('../src/utils/mesSpareDisplay.ts', import.meta.url);
const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const pageSource = (name) => read(`src/views/mes/eq/${name}/index.vue`);
const copy = (value) => structuredClone(Vue.toRaw(value));
const flush = async () => {
  for (let i = 0; i < 8; i++) await Promise.resolve();
};
const deferred = () => {
  let resolve;
  const promise = new Promise((done) => (resolve = done));
  return { promise, resolve };
};

function evaluate(source, modules = {}, globals = {}, filename = 'offline.ts') {
  const { outputText, diagnostics } = ts.transpileModule(source, {
    fileName: filename,
    reportDiagnostics: true,
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS }
  });
  assert.deepEqual(
    diagnostics.filter((entry) => entry.category === ts.DiagnosticCategory.Error),
    [],
    'fixture 转译应成功'
  );
  const exports = {};
  new Function('exports', 'require', ...Object.keys(globals), outputText)(
    exports,
    (name) => {
      assert.ok(Object.hasOwn(modules, name), `禁止未隔离依赖：${name}`);
      return modules[name];
    },
    ...Object.values(globals)
  );
  return exports;
}

function display() {
  assert.ok(existsSync(displayUrl), '应提供共享的备件四位格式与库存口径工具');
  return evaluate(readFileSync(displayUrl, 'utf8'));
}

async function setupPage(name, ports = {}) {
  const calls = [];
  const messages = [];
  const api = new Proxy(
    {},
    {
      get:
        (_, key) =>
        (...args) => {
          calls.push({ name: key, args: args.map(copy) });
          if (ports[key]) return ports[key](...args);
          if (String(key).startsWith('list')) return Promise.resolve({ rows: [], total: 0 });
          if (key === 'erpSyncList') return Promise.resolve({ data: [] });
          if (String(key).startsWith('get')) return Promise.resolve({ data: {} });
          return Promise.resolve({ data: true });
        }
    }
  );
  const modal = {
    confirm: async (...args) => {
      calls.push({ name: 'confirm', args });
      return ports.confirm?.(...args);
    },
    msgSuccess: (message) => messages.push({ type: 'success', message }),
    msgWarning: (message) => messages.push({ type: 'warning', message }),
    msgError: (message) => messages.push({ type: 'error', message })
  };
  const modules = {
    vue: Vue,
    'element-plus': {
      ElMessage: { success: modal.msgSuccess, warning: modal.msgWarning, error: modal.msgError },
      ElMessageBox: { confirm: modal.confirm }
    },
    '@/utils/request': { download: () => {} },
    '@/api/mes/eq/spare-part': api,
    '@/api/mes/eq/spare-return': api,
    '@/api/erp/bd/material/index': api,
    '@/api/erp/wm/warehouse/index': api,
    '@/api/system/autocode/rule': api
  };
  if (existsSync(displayUrl)) modules['@/utils/mesSpareDisplay'] = display();
  modules['@/composables/useCrud'] = evaluate(read('src/composables/useCrud.ts'), modules);
  const { descriptor, errors } = parseSfc(pageSource(name));
  assert.deepEqual(errors, [], 'fixture SFC 解析应成功');
  const names = [
    'form',
    'formRef',
    'handleSubmit',
    'submitForm',
    'handleOpenStock',
    'stockDialog',
    'stockForm',
    'submitStock',
    'stockSubmitting',
    'submitting',
    'handleConfirm',
    'handleRetry',
    'handleBackfillWarehouse',
    'spareOptions',
    'warehouseOptions',
    'handleSpareChange',
    'canClearWarehouse',
    'queryParams',
    'getList',
    'pagedList',
    'total',
    'dataList',
    'open',
    'stockRef',
    'rules',
    'handleDelete',
    'handleUpdate',
    'autoGenFlag',
    'spareCodePreview',
    'handleAutoGenChange',
    'cancel'
  ];
  const footer = `\nexports.bindings = { ${names.map((key) => `${key}: typeof ${key} === 'undefined' ? undefined : ${key}`).join(', ')} };`;
  const bindings = evaluate(descriptor.scriptSetup.content + footer, modules, {
    ref: Vue.ref,
    reactive: Vue.reactive,
    computed: Vue.computed,
    toRefs: Vue.toRefs,
    watch: Vue.watch,
    getCurrentInstance: () => ({
      proxy: {
        $modal: modal,
        useDict: (...keys) => Vue.reactive(Object.fromEntries(keys.map((key) => [key, []]))),
        resetForm: () => {}
      }
    })
  }).bindings;
  if (bindings.formRef)
    bindings.formRef.value = {
      clearValidate: () => {},
      validate: async (callback) => {
        const valid = ports.valid ?? true;
        if (callback) return callback(valid);
        if (!valid) throw { field: 'invalid' };
        return true;
      }
    };
  await flush();
  return { ...bindings, calls, messages, count: (key) => calls.filter((entry) => entry.name === key).length };
}

const spare = (patch = {}) => ({
  spareId: ID,
  spareCode: 'SP-M-001',
  spareName: '深沟球轴承',
  materialId: '9007199254740999',
  warehouseId: WAREHOUSE_ID,
  unit: '个',
  currentStock: '10.0000',
  erpAvailableStock: '8.0000',
  lowStockBasis: 'ERP_AVAILABLE',
  ...patch
});
const returnDraft = (patch = {}) => ({
  returnId: ID,
  status: 'DRAFT',
  warehouseId: WAREHOUSE_ID,
  returnDate: '2026-10-10',
  returnType: 'EXCESS_RETURN',
  lines: [{ spareId: ID, returnQty: 1, unitCost: 0.1234 }],
  ...patch
});

for (const [file, fields] of [
  ['src/api/mes/eq/spare-part.ts', ['spareId', 'logId', 'refId']],
  ['src/api/mes/eq/repairSpare.ts', ['id', 'repairId', 'spareId']]
]) {
  test(`${file} 的响应及表单雪花 ID 不声明为 number`, () => {
    const source = read(file);
    for (const field of fields) assert.ok(!new RegExp(`\\b${field}\\??:\\s*number\\s*;`).test(source), `${field} 必须保留 string`);
  });
}

test('出库预校验使用 ERP 可用量而非 MES 派生缓存，雪花 ID 原样发送', async () => {
  const page = await setupPage('spare-part');
  await page.handleOpenStock(spare({ currentStock: 0 }), 'OUT');
  page.stockForm.quantity = 2;
  await page.submitStock();
  assert.equal(page.count('stockTransaction'), 1, '缓存为 0 不应否决 ERP 有可用量的出库');
  assert.equal(page.calls.find((entry) => entry.name === 'stockTransaction').args[0].spareId, ID);
});

test('ERP 可用量已知为 0 时不能使用正的 MES 缓存放行出库', async () => {
  const page = await setupPage('spare-part');
  await page.handleOpenStock(spare({ erpAvailableStock: '0.0000' }), 'OUT');
  page.stockForm.quantity = 1;
  await page.submitStock();
  assert.equal(page.count('stockTransaction'), 0);
  assert.ok(page.messages.some((entry) => /ERP/.test(entry.message)));
});

for (const basis of ['ERP_UNAVAILABLE', 'NOT_MAPPED']) {
  test(`${basis} 的库存未知不得转换为零或缓存出库上限`, async () => {
    const page = await setupPage('spare-part');
    await page.handleOpenStock(spare({ lowStockBasis: basis, erpAvailableStock: null, currentStock: null }), 'OUT');
    assert.notEqual(page.stockDialog.currentStock, 0);
    assert.ok(page.stockDialog.erpAvailableStock == null);
    if (basis === 'ERP_UNAVAILABLE') {
      page.stockForm.quantity = 1;
      await page.submitStock();
      assert.equal(page.count('stockTransaction'), 1, '未知 ERP 库存由后端权威校验，不能以缓存零阻断');
    }
  });
}

for (const quantity of [undefined, null, 0, -1, NaN, Infinity, 0.00001, 10000000000]) {
  test(`出入库拒绝无效数量 ${String(quantity)}`, async () => {
    const page = await setupPage('spare-part');
    await page.handleOpenStock(spare(), 'IN');
    page.stockForm.quantity = quantity;
    await page.submitStock();
    assert.equal(page.count('stockTransaction'), 0);
    assert.ok(page.messages.some((entry) => entry.type === 'warning'));
  });
}

test('出入库连续点击只发送一次，成功仅表示 MES 已登记而非 ERP 已落账', async () => {
  const pending = deferred();
  const page = await setupPage('spare-part', { stockTransaction: () => pending.promise });
  await page.handleOpenStock(spare(), 'IN');
  const requests = [page.submitStock(), page.submitStock()];
  await flush();
  assert.equal(page.count('stockTransaction'), 1);
  pending.resolve({ data: '11.0000' });
  await Promise.all(requests);
  assert.equal(page.stockSubmitting.value, false);
  assert.ok(page.messages.some((entry) => /过账台账/.test(entry.message)));
});

for (const name of ['spare-part', 'spare-return']) {
  test(`${name} 保存加载态覆盖校验与请求，重复点击只提交一次`, async () => {
    const pending = deferred();
    const apiName = name === 'spare-part' ? 'addSparePart' : 'addSpareReturn';
    const page = await setupPage(name, { [apiName]: () => pending.promise });
    page.form.value = name === 'spare-part' ? { spareCode: 'SP-M-001', spareName: '深沟球轴承' } : returnDraft({ returnId: undefined });
    const save = page.handleSubmit ?? page.submitForm;
    const requests = [save(), save()];
    await flush();
    assert.equal(page.count(apiName), 1);
    assert.equal(page.submitting.value, true);
    pending.resolve({ data: {} });
    await Promise.all(requests);
    assert.equal(page.submitting.value, false);
  });
}

test('备件自动编码只展示预览，正式新增由后端取号', async () => {
  const page = await setupPage('spare-part', { previewCode: () => Promise.resolve('SP20261010001') });
  assert.equal(typeof page.handleAutoGenChange, 'function');
  await page.handleAutoGenChange(true);
  assert.equal(page.spareCodePreview.value, 'SP20261010001');
  page.form.value = { spareCode: 'SP20261010001', spareName: '深沟球轴承 6204-2RS' };

  await page.handleSubmit();

  const request = page.calls.find((entry) => entry.name === 'addSparePart');
  assert.ok(request, '应提交新增备件请求');
  assert.equal(request.args[0].spareCode, '', '自动模式必须清空预览编码，由后端正式取号');
});

for (const quantity of [undefined, 0, -1, NaN, Infinity, 0.00001, 10000000000]) {
  test(`退库保存拒绝无效数量 ${String(quantity)}`, async () => {
    const page = await setupPage('spare-return');
    page.form.value = returnDraft({ returnId: undefined, lines: [{ spareId: ID, returnQty: quantity }] });
    await page.handleSubmit();
    await flush();
    assert.equal(page.count('addSpareReturn'), 0);
  });
}

test('退库已知映射仓库与单头不一致时拒绝保存，不能假装写入另一个仓库', async () => {
  const page = await setupPage('spare-return');
  page.spareOptions.value = [spare({ warehouseId: OTHER_WAREHOUSE_ID })];
  page.form.value = returnDraft({ returnId: undefined });
  await page.handleSubmit();
  await flush();
  assert.equal(page.count('addSpareReturn'), 0);
  assert.ok(page.messages.some((entry) => /仓库/.test(entry.message)));
});

test('退库仓库可不填，不擅自以残缺选项替代后端权威校验', async () => {
  const page = await setupPage('spare-return');
  page.form.value = returnDraft({ returnId: undefined, warehouseId: undefined });
  await page.handleSubmit();
  await flush();
  assert.equal(page.count('addSpareReturn'), 1);
  assert.ok(!page.rules.warehouseId?.some((rule) => rule.required));
});

for (const [name, handler, apiName, row] of [
  ['spare-part', 'handleBackfillWarehouse', 'backfillWarehouse', undefined],
  ['spare-return', 'handleConfirm', 'confirmSpareReturn', returnDraft()],
  ['spare-erp-sync', 'handleRetry', 'retryErpSync', { syncId: ID, spareCode: 'SP-M-001', syncStatus: 'FAILED' }]
]) {
  test(`${name} ${handler} 取消确认不显示失败、不发送业务请求`, async () => {
    const page = await setupPage(name, { confirm: () => Promise.reject('cancel') });
    await page[handler](row);
    assert.equal(page.count(apiName), 0);
    assert.equal(page.messages.filter((entry) => entry.type === 'error').length, 0);
  });
  test(`${name} ${handler} 在确认弹窗和提交期间均阻止重复请求`, async () => {
    const confirmation = deferred();
    const pending = deferred();
    const page = await setupPage(name, { confirm: () => confirmation.promise, [apiName]: () => pending.promise });
    const requests = [page[handler](row), page[handler](row)];
    await flush();
    assert.equal(page.count('confirm'), 1, '同一操作不能并发打开两个确认弹窗');
    confirmation.resolve();
    await flush();
    assert.equal(page.count(apiName), 1);
    pending.resolve({ data: true });
    await Promise.all(requests);
  });
}

test('ERP 过账仍读取 R<List> 并在本地分页，SENT 不冒充 SYNCED', async () => {
  const rows = Array.from({ length: 15 }, (_, i) => ({ syncId: `${ID}-${i}`, syncStatus: 'SENT' }));
  const page = await setupPage('spare-erp-sync', { erpSyncList: () => Promise.resolve({ data: rows }) });
  assert.equal(page.total.value, 15);
  assert.equal(page.pagedList.value.length, 10);
  page.queryParams.value.pageNum = 2;
  assert.equal(page.pagedList.value.length, 5);
  assert.equal(page.calls.find((entry) => entry.name === 'erpSyncList').args[0], undefined);
});

test('数量最多四位、金额保留两至四位，未知值不显示为零', () => {
  const { formatQuantity, formatAmount, decimalValue } = display();
  for (const value of [undefined, null, '', '   ', NaN, Infinity, 'not-a-number']) {
    assert.equal(formatQuantity(value), '-');
    assert.equal(formatAmount(value), '-');
    assert.equal(decimalValue(value), undefined);
  }
  assert.equal(formatQuantity('1234.5000'), '1,234.5');
  assert.equal(formatQuantity('0.0001'), '0.0001');
  assert.equal(formatQuantity('0.0000'), '0');
  assert.equal(formatAmount('1234.5678'), '1,234.5678');
  assert.equal(formatAmount('6.5'), '6.50');
});

test('未知 ERP 库存口径与未知状态不能伪装为正常零库存', () => {
  const { erpStockValue, stockBasisLabel, txnTypeLabel, refTypeLabel, syncStatusLabel } = display();
  assert.equal(erpStockValue(spare({ lowStockBasis: 'ERP_UNAVAILABLE', erpAvailableStock: 9 })), undefined);
  assert.equal(erpStockValue(spare({ lowStockBasis: 'NOT_MAPPED', erpAvailableStock: 9 })), undefined);
  assert.equal(erpStockValue(spare({ lowStockBasis: 'NEW_BASIS', erpAvailableStock: 9 })), undefined);
  assert.equal(erpStockValue(spare({ erpAvailableStock: '0.0000' })), 0);
  assert.match(stockBasisLabel('ERP_UNAVAILABLE'), /不可用|不可达/);
  assert.match(stockBasisLabel('NOT_MAPPED'), /未映射/);
  assert.equal(txnTypeLabel('RETURN'), '退库');
  assert.equal(refTypeLabel('REPAIR_CANCEL'), '维修领用撤销');
  assert.equal(refTypeLabel('SPARE_RETURN'), '退库单');
  assert.match(syncStatusLabel('SENT'), /待.*确认|待.*回执/);
  assert.match(syncStatusLabel('NEW_STATE'), /未知/);
});

for (const name of ['spare-part', 'spare-stock-log', 'spare-erp-sync', 'spare-return']) {
  test(`${name} 模板合法、具有空状态和四位格式入口`, () => {
    const { descriptor, errors } = parseSfc(pageSource(name));
    assert.deepEqual(errors, []);
    const template = compileTemplate({ source: descriptor.template.content, filename: `${name}.vue`, id: name });
    assert.deepEqual(template.errors, []);
    assert.ok(/empty-text=|#empty/.test(descriptor.template.content), '应提供业务空状态');
    assert.ok(/formatQuantity/.test(descriptor.template.content), '数量应统一最多四位格式');
  });
}

test('备件页说明 ERP 权威、缓存非出库依据、自动请购仍暂停，四页不开放采购入口', () => {
  const source = pageSource('spare-part');
  assert.ok(/ERP.*权威/.test(source));
  assert.ok(/MES.*缓存/.test(source));
  assert.ok(/低库存.*请购.*已暂停/.test(source));
  assert.ok(/MES.*缓存/.test(pageSource('spare-stock-log')));
  for (const name of ['spare-part', 'spare-stock-log', 'spare-erp-sync', 'spare-return']) {
    assert.ok(!/\bpurchaseRequest\b|\bhandlePurchaseRequest\b/.test(pageSource(name)));
  }
});

test('退库已确认单据保留只读查看入口，查看模式不显示保存或添加行', () => {
  const source = pageSource('spare-return');
  assert.ok(source.includes('@click="handleView(scope.row)"'), '应能追溯已确认单据的明细');
  assert.ok(/v-if="!isViewMode"[^>]*@click="handleSubmit"/.test(source), '只读查看不应提交');
  assert.ok(/v-if="!isViewMode"[^>]*@click="addLine"/.test(source), '只读查看不应添加行');
});

for (const unitCost of [-1, Infinity, 0.00001, 100000000000000]) {
  test(`退库保存忽略客户端参考成本 ${String(unitCost)}`, async () => {
    const page = await setupPage('spare-return');
    page.form.value = returnDraft({ returnId: undefined, lines: [{ spareId: ID, returnQty: 1, unitCost }] });
    await page.handleSubmit();
    await flush();
    assert.equal(page.count('addSpareReturn'), 1);
    assert.ok(!Object.hasOwn(page.calls.find((entry) => entry.name === 'addSpareReturn').args[0].lines[0], 'unitCost'));
  });
}

test('成本 decimal(18,4) 接受十四位整数且保持末尾四位精度，不沿用数量上限', () => {
  const tools = display();
  assert.equal(typeof tools.isNonNegativeCost, 'function');
  assert.equal(tools.isNonNegativeCost('99999999999999.9999'), true);
  assert.equal(tools.isNonNegativeCost('10000000000.1234'), true);
  assert.equal(tools.isNonNegativeCost('100000000000000'), false);
  assert.equal(tools.isNonNegativeCost('0.00001'), false);
  assert.equal(tools.formatAmount('99999999999999.9999'), '99,999,999,999,999.9999');
});

for (const unitCost of ['10000000000.1234', '99999999999999.9999']) {
  test(`退库成本 ${unitCost} 只展示，不作为客户端写入值`, async () => {
    const page = await setupPage('spare-return');
    page.form.value = returnDraft({ returnId: undefined, lines: [{ spareId: ID, returnQty: 1, unitCost }] });
    await page.handleSubmit();
    await flush();
    assert.equal(page.count('addSpareReturn'), 1);
    assert.ok(!Object.hasOwn(page.calls.find((entry) => entry.name === 'addSpareReturn').args[0].lines[0], 'unitCost'));
  });
}

test('每种备件单位按行展示，不将不同物料/单位合计称为库存件数', async () => {
  const page = await setupPage('spare-part');
  await page.handleOpenStock(spare({ unit: '条' }), 'IN');
  assert.equal(page.stockDialog.unit, '条');
  await page.handleOpenStock(spare({ unit: '件' }), 'IN');
  assert.equal(page.stockDialog.unit, '件');
  for (const name of ['spare-part', 'spare-stock-log', 'spare-erp-sync', 'spare-return']) {
    assert.ok(!/库存件数|总库存件数|库存总件数/.test(pageSource(name)));
  }
});

for (const name of ['spare-part', 'spare-return']) {
  test(`${name} 编辑加载时只转换数量，不转换高精度成本或雪花 ID`, async () => {
    const cost = '99999999999999.9999';
    const detail =
      name === 'spare-part'
        ? spare({ safetyStock: '2.0000', unitCost: cost })
        : returnDraft({ lines: [{ spareId: ID, returnQty: '2.0000', unitCost: cost }] });
    const apiName = name === 'spare-part' ? 'getSparePart' : 'getSpareReturn';
    const page = await setupPage(name, { [apiName]: () => Promise.resolve({ data: detail }) });
    await page.handleUpdate(detail);
    assert.equal(name === 'spare-part' ? page.form.value.unitCost : page.form.value.lines[0].unitCost, cost);
    assert.equal(name === 'spare-part' ? page.form.value.spareId : page.form.value.lines[0].spareId, ID);
    assert.equal(name === 'spare-part' ? page.form.value.safetyStock : page.form.value.lines[0].returnQty, 2);
  });
}

test('维修明细 ID 适配与现有维修页类型断言兼容（仅内存类型投影，不运行全量 type-check）', () => {
  const declaration = (source, name) => {
    const file = ts.createSourceFile('contract.ts', source, ts.ScriptTarget.ES2020, true);
    const node = file.statements.find((entry) => ts.isInterfaceDeclaration(entry) && entry.name.text === name);
    assert.ok(node, `应找到 ${name} 的真实接口声明`);
    return node.getText(file);
  };
  const source = [
    declaration(read('src/api/mes/eq/repair.ts'), 'EqRepairVO'),
    declaration(read('src/api/mes/eq/repairSpare.ts'), 'EqRepairSpareVO'),
    'declare const row: EqRepairSpareVO;',
    'const patch = { repairId: row.repairId, repairNo: "MR-20261010-001" } as EqRepairVO;'
  ].join('\n');
  const filename = 'mes-spare-repair-contract-projection.ts';
  const options = { noLib: true, noEmit: true, strict: false, target: ts.ScriptTarget.ES2020 };
  const host = ts.createCompilerHost(options);
  host.getSourceFile = (name) => (name === filename ? ts.createSourceFile(name, source, options.target, true) : undefined);
  const program = ts.createProgram([filename], options, host);
  const diagnostics = program.getSemanticDiagnostics(program.getSourceFile(filename));
  assert.deepEqual(
    diagnostics.map((entry) => ts.flattenDiagnosticMessageText(entry.messageText, '\n')),
    []
  );
});

test('退库参考单价只读展示主数据成本，切换备件后刷新成本', async () => {
  const page = await setupPage('spare-return');
  const second = '9007199254741001';
  page.spareOptions.value = [spare({ unitCost: '42.5000' }), spare({ spareId: second, unitCost: '96.0000' })];
  page.form.value = returnDraft({ lines: [{ spareId: second, returnQty: 1, unitCost: '42.5000' }] });
  page.handleSpareChange(second, 0);
  assert.equal(page.form.value.lines[0].unitCost, '96.0000');
  assert.ok(!/v-model="scope.row.unitCost"/.test(pageSource('spare-return')), '退库单价由服务端主数据生成，不能提供会被忽略的改价输入框');
});

test('编辑有单头仓库的退库草稿时禁止清空，并按原仓库校验明细', async () => {
  const detail = returnDraft();
  const page = await setupPage('spare-return', { getSpareReturn: () => Promise.resolve({ data: detail }) });
  await page.handleUpdate(detail);
  assert.equal(page.canClearWarehouse?.value, false);
  page.spareOptions.value = [spare({ warehouseId: OTHER_WAREHOUSE_ID })];
  page.form.value.warehouseId = undefined;
  await page.handleSubmit();
  assert.equal(page.form.value.warehouseId, WAREHOUSE_ID);
  assert.equal(page.count('updateSpareReturn'), 0);
});

test('新增或原单头仓库为空的退库单允许按明细映射办理', async () => {
  const detail = returnDraft({ warehouseId: undefined });
  const page = await setupPage('spare-return', { getSpareReturn: () => Promise.resolve({ data: detail }) });
  await page.handleUpdate(detail);
  assert.equal(page.canClearWarehouse?.value, true);
});
