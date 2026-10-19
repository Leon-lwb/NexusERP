import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';

const calls = [];
globalThis.__traceApiCalls = calls;
const policy = { batchManaged: false, serialManaged: true, allowMixedLot: false, version: 'policy:01', stage: 'STRICT' };
const line = {
  materialId: '9223372036854775807',
  quantity: '1',
  quantityIssue: '1',
  currentQty: '1',
  actualQuantity: '1',
  differenceQuantity: '1',
  policy,
  detailVersion: 'line:opaque',
  snItems: [{ identityId: '9223372036854775806', materialId: '9223372036854775807' }]
};
const header = {
  ...line,
  sourceHeader: { sourceId: '9223372036854775805', version: 'source:opaque' },
  detailVersion: 'doc:opaque',
  wmMaterialReceiveLineList: [{ ...line, detailList: [line] }],
  wmMiscIssueLineList: [line],
  wmMiscRecptLineList: [line],
  wmTransferLineList: [line],
  wmProductionIssueLineList: [line],
  wmStockTakingLineList: [line],
  wmSalesIssueLineList: [line]
};
globalThis.__traceApiResult = () => ({ data: header });
const output = await build({
  stdin: {
    contents: `export * from './src/api/erp/wm/batch/index.ts'; export * from './src/api/erp/wm/barcode/index.ts'; export * from './src/api/erp/bd/serial-number-rule/index.ts'; export * from './src/api/erp/wm/identity/index.ts'; export * from './src/api/erp/wm/miscissue/index.ts'; export * from './src/api/erp/wm/miscrecpt/index.ts'; export * from './src/api/erp/wm/receive/index.ts'; export * from './src/api/mes/lt/consumption.ts'; export * from './src/api/mes/pro/completion.ts'; export * from './src/api/erp/wm/trace/repair.ts'; export * from './src/api/erp/wm/trace/index.ts'; export * from './src/api/erp/wm/transfer/index.ts'; export * from './src/api/erp/wm/productionissue/index.ts'; export * from './src/api/erp/wm/productionrecpt/index.ts'; export * from './src/api/erp/wm/stocktaking/index.ts'; export * from './src/api/erp/wm/sales-issue/index.ts'; export * from './src/api/erp/wm/return/index.ts';`,
    resolveDir: process.cwd(),
    loader: 'ts'
  },
  bundle: true,
  write: false,
  format: 'esm',
  platform: 'node',
  plugins: [
    {
      name: 'request-fixture',
      setup(build) {
        build.onResolve({ filter: /^@\/utils\/certified-business$/ }, () => ({ path: 'certified', namespace: 'fixture' }));
        build.onResolve({ filter: /^@\/utils\/request$/ }, () => ({ path: 'request', namespace: 'fixture' }));
        build.onLoad({ filter: /.*/, namespace: 'fixture' }, ({ path }) => ({
          contents:
            path === 'certified'
              ? 'export const readCertifiedBusiness = () => {}; export const postCertifiedBusiness = () => {}; export const normalizeBusinessId = (value) => value;'
              : 'function request(config) { globalThis.__traceApiCalls.push(config); return Promise.resolve(globalThis.__traceApiResult(config)); } request.defaults = { transformRequest: [] }; export default request;',
          loader: 'js'
        }));
      }
    }
  ]
});
const api = await import(`data:text/javascript;base64,${Buffer.from(output.outputFiles[0].text).toString('base64')}`);

/** 实际请求适配器经请求边界捕获，既有 URL、源字段和原 opaque 版本均需保留。 */
test('解析请求保留完整 rawCode、字符串身份及来源上下文', async () => {
  const data = {
    rawCode: JSON.stringify({ materialCode: 'M1', rawTexts: ['all', 'evidence'] }),
    scene: 'RECEIVE',
    materialId: '9223372036854775807',
    supplierId: '9223372036854775806',
    sourceDocId: '9223372036854775805'
  };
  await api.resolveMaterialCode(data);
  assert.deepEqual(calls.at(-1).data, data);
  assert.equal(calls.at(-1).url, '/erp/wm/code/resolve');
});
test('正式发号与别名重放均使用 POST 和原命令', async () => {
  const request = { commandId: 'reserve-original', materialId: '9223372036854775807', quantity: 2, sourceType: 'ERP_MANUAL', sourceId: null };
  await api.reserveSerialIdentities(request);
  await api.reserveSerialIdentities(request);
  assert.equal(calls.at(-1).url, '/erp/wm/sn/reserve');
  assert.deepEqual(calls.at(-1).data, request);
  assert.equal(calls.at(-1).data.status, undefined);
  const alias = { commandId: 'alias-original', sourceType: 'SUPPLIER', sourceId: '9223372036854775805', code: 'EXT-123' };
  await api.registerSerialAlias('9223372036854775806', alias);
  assert.equal(calls.at(-1).url, '/erp/wm/sn/9223372036854775806/aliases');
  assert.deepEqual(calls.at(-1).data, alias);
});
for (const [method, url] of [
  ['executeMiscIssue', '/erp/wm/miscissue/execute/9223372036854775805'],
  ['executeMiscRecpt', '/erp/wm/miscrecpt/execute/9223372036854775805'],
  ['executeTransfer', '/erp/wm/transfer/execute/9223372036854775805'],
  ['executeProductionIssue', '/erp/wm/productionissue/execute/9223372036854775805'],
  ['executeProductionRecpt', '/erp/wm/productionrecpt/execute/9223372036854775805'],
  ['postAdjustment', '/erp/wm/stocktaking/postAdjustment/9223372036854775805'],
  ['completeSalesIssue', '/erp/wm/salesIssue/complete/9223372036854775805']
]) {
  test(`${method} 向原执行 URL 发送完整追溯请求体`, async () => {
    await api[method]('9223372036854775805', header);
    const call = calls.at(-1);
    assert.equal(call.url, url);
    assert.equal(call.data.detailVersion, 'doc:opaque');
    assert.deepEqual(call.data.sourceHeader, header.sourceHeader);
    assert.equal(call.data.snItems[0].identityId, '9223372036854775806');
    assert.ok(call.data.commandId);
  });
}
test('列表执行重新读取服务端详情而不把列表版本当作详情版本', async () => {
  calls.length = 0;
  await api.executeTransfer('9223372036854775805');
  assert.equal(calls[0].method, 'get');
  assert.equal(calls[0].url, '/erp/wm/transfer/9223372036854775805');
  assert.equal(calls.at(-1).data.detailVersion, 'doc:opaque');
});
test('既有退料草稿端点携带幂等命令和原身份字符串', async () => {
  const draft = { ...header, lines: [line] };
  await api.addReturn(draft);
  const first = calls.at(-1);
  await api.addReturn(draft);
  assert.equal(first.url, '/erp/wm/return');
  assert.ok(first.data.commandId);
  assert.equal(first.data.commandId, calls.at(-1).data.commandId);
  assert.equal(first.data.lines[0].snItems[0].identityId, '9223372036854775806');
});
test('审计页面只调用查询与 audit，不提供实时 apply', async () => {
  calls.length = 0;
  await api.listRepairRuns({ pageNum: 1, pageSize: 10 });
  await api.getRepairRun('9223372036854775807');
  await api.auditMaterialTrace({ materialId: '9223372036854775806' });
  assert.deepEqual(
    calls.map(({ url, method }) => [url, method]),
    [
      ['/erp/wm/trace/repair/runs', 'get'],
      ['/erp/wm/trace/repair/runs/9223372036854775807', 'get'],
      ['/erp/wm/trace/repair/audit', 'post']
    ]
  );
});

/** 收货原认证会话仍应用在最终发送边界。 */
test('收货执行及上架原端点携带详情版本且保留固定会话守卫', async () => {
  let guards = 0;
  const session = {
    token: 'synthetic-test-only',
    assertCurrent() {
      guards++;
    }
  };
  await api.execute('9223372036854775805', {}, session);
  assert.equal(calls.at(-1).url, '/erp/wm/receive/execute/9223372036854775805');
  assert.equal(calls.at(-1).data.detailVersion, 'doc:opaque');
  assert.equal(calls.at(-1).headers.Authorization, 'Bearer synthetic-test-only');
  assert.ok(guards > 0);
  await api.confirmShelf('9223372036854775805', session);
  assert.equal(calls.at(-1).url, '/erp/wm/receive/shelf/9223372036854775805');
  assert.equal(calls.at(-1).data.snItems[0].identityId, '9223372036854775806');
});
test('版本缺失或解析歧义阻断执行，不发送写请求', async () => {
  calls.length = 0;
  await assert.rejects(
    api.executeTransfer('1', { ...header, detailVersion: undefined, wmTransferLineList: [{ ...line, detailVersion: undefined }] }),
    /版本/
  );
  assert.equal(calls.length, 0);
  await assert.rejects(api.executeTransfer('1', { ...header, wmTransferLineList: [{ ...line, _traceError: '解析有歧义' }] }), /歧义/);
  assert.equal(calls.length, 0);
});
test('MES 原 pick 端点保留正式身份、本地 snId、逐批精确数量与原版本', async () => {
  await api.confirmPick({
    woId: '9223372036854775807',
    materialId: '9223372036854775806',
    productIdentityId: '9223372036854775805',
    snId: '9223372036854775804',
    detailVersion: 'mes:opaque',
    picks: [{ lotId: '9223372036854775803', consumedQty: '0.100000000000000001' }]
  });
  const call = calls.at(-1);
  assert.equal(call.url, '/mes/lt/consumption/pick');
  assert.equal(call.data.productIdentityId, '9223372036854775805');
  assert.equal(call.data.snId, '9223372036854775804');
  assert.equal(call.data.picks[0].consumedQty, '0.100000000000000001');
  assert.equal(call.data.detailVersion, 'mes:opaque');
  assert.ok(call.data.commandId);
});

/** 编号规则编辑的服务端原版本必须作为字符串原样发送。 */
test('编号规则 CAS 保留原始 ruleVersion，缺少版本时不发送更新', async () => {
  const data = { ruleId: '9223372036854775807', ruleCode: 'RULE-1', ruleName: '正式SN', ruleType: 'PRODUCT_SN', ruleVersion: '9223372036854775806' };
  await api.updateSerialNumberRule(data);
  assert.equal(calls.at(-1).data.ruleVersion, '9223372036854775806');
  assert.equal(calls.at(-1).url, '/erp/bd/serial-number-rule');
  const before = calls.length;
  await assert.rejects(api.updateSerialNumberRule({ ...data, ruleVersion: undefined }), /版本/);
  assert.equal(calls.length, before);
});
test('PRODUCT_SN 不再调用旧发号，BARCODE 与 BATCH_NO 仍使用原接口', async () => {
  const before = calls.length;
  await assert.rejects(api.generateSerialNumber('SN-RULE', 'PRODUCT_SN'), /reserve|正式/);
  assert.equal(calls.length, before);
  for (const type of ['BARCODE', 'BATCH_NO']) {
    await api.generateSerialNumber(type, type);
    assert.equal(calls.at(-1).url, `/erp/bd/serial-number-rule/generate/${type}`);
  }
});
/** 新建两个相同内容的草稿是两次有意操作，不能被全局内容缓存合并。 */
test('不同草稿会话生成不同命令，同一草稿网络重试保持原命令', async () => {
  const draftA = { warehouseId: '9', lines: [{ materialId: '1', quantity: '1' }] };
  const draftB = { warehouseId: '9', lines: [{ materialId: '1', quantity: '1' }] };
  await api.addReturn(draftA);
  const first = calls.at(-1).data.commandId;
  await api.addReturn(draftA);
  assert.equal(calls.at(-1).data.commandId, first);
  await api.addReturn(draftB);
  assert.notEqual(calls.at(-1).data.commandId, first);
});

/** 首次统一身份追溯只传身份与页大小，授权模块由服务端决定。 */
test('统一 identity 追溯首次请求不提前指定模块，分节页保留字符串游标', async () => {
  await api.traceIdentity('9223372036854775807');
  assert.equal(calls.at(-1).url, '/erp/wm/trace/identity/9223372036854775807');
  assert.equal(Object.hasOwn(calls.at(-1).params, 'module'), false);
  await api.traceIdentity('9223372036854775807', { module: 'MES', section: 'CONSUMPTION', cursor: '9223372036854775806', limit: 500 });
  assert.deepEqual(calls.at(-1).params, { limit: 500, module: 'MES', section: 'CONSUMPTION', cursor: '9223372036854775806' });
});
test('批次组件召回使用原 batchId 与 afterIdentity，不将游标转成 Number', async () => {
  await api.recallBatch('9223372036854775807', { afterIdentity: '9223372036854775806', limit: 500, depth: 10 });
  assert.equal(calls.at(-1).url, '/erp/wm/trace/recall');
  assert.deepEqual(calls.at(-1).params, { batchId: '9223372036854775807', afterIdentity: '9223372036854775806', limit: 500, depth: 10 });
});

/** PC手工登记不能冒用MES、业务来源或任意sourceId。 */
test('手工reserve固定ERP_MANUAL和null来源，拒绝其它来源且不发HTTP', async () => {
  const payload = { commandId: 'manual-original', materialId: '9223372036854775807', quantity: 1, sourceType: 'ERP_MANUAL', sourceId: null };
  await api.reserveSerialIdentities(payload);
  assert.equal(calls.at(-1).data.sourceType, 'ERP_MANUAL');
  assert.equal(calls.at(-1).data.sourceId, null);
  const before = calls.length;
  await assert.rejects(api.reserveSerialIdentities({ ...payload, sourceType: 'MES' }), /手工|ERP_MANUAL/);
  await assert.rejects(api.reserveSerialIdentities({ ...payload, sourceId: '9223372036854775806' }), /手工|来源/);
  assert.equal(calls.length, before);
});
test('弃用API仅POST原身份及原字符串版本，必填原因且保留命令', async () => {
  const payload = { commandId: 'retire-original', expectedVersion: '9223372036854775806', reason: '手工登记错误，保留号码审计' };
  await api.retireSerialIdentity('9223372036854775807', payload);
  const call = calls.at(-1);
  assert.equal(call.url, '/erp/wm/sn/9223372036854775807/retire');
  assert.equal(call.method, 'post');
  assert.deepEqual(call.data, payload);
  const before = calls.length;
  for (const invalid of [
    { ...payload, reason: ' ' },
    { ...payload, reason: '字'.repeat(501) },
    { ...payload, expectedVersion: undefined },
    { ...payload, expectedVersion: 1 }
  ])
    await assert.rejects(api.retireSerialIdentity('9223372036854775807', invalid), /原因|版本/);
  assert.equal(calls.length, before);
});

test('MES实际产品产出POST携带原lot/allocation与精确GOOD/SCRAP数量', async () => {
  const payload = {
    commandId: 'actual-output-command',
    cardId: '9223372036854775807',
    stepRecordId: '9223372036854775806',
    lines: [{ lotId: '9223372036854775805', sourceAllocationId: '9223372036854775804', quantity: '0.100000000000000001', result: 'GOOD' }]
  };
  await api.recordProductOutputs(payload);
  assert.equal(calls.at(-1).url, '/mes/lt/trace/product-output');
  assert.equal(calls.at(-1).method, 'post');
  assert.deepEqual(calls.at(-1).data, payload);
});
test('MES取消和重新冻结使用原清单/修订与原因，不改写业务报工', async () => {
  const cancel = { commandId: 'cancel-original', revision: 7, reason: '核对实际产出后修订' };
  await api.requestCompletionCancellation('9223372036854775807', cancel);
  assert.equal(calls.at(-1).url, '/mes/pro/completion-manifest/9223372036854775807/request-cancel');
  assert.deepEqual(calls.at(-1).data, cancel);
  const refreeze = { commandId: 'refreeze-original', cancelledRevision: 7, reason: '依据原取消屏障重新冻结' };
  await api.refreezeCompletionManifest('9223372036854775806', refreeze);
  assert.equal(calls.at(-1).url, '/mes/pro/completion-manifest/work-order/9223372036854775806/refreeze');
  assert.deepEqual(calls.at(-1).data, refreeze);
});

test('批次自身事实独立GET仅传原module/section/cursor，不从来源仓推算现存仓', async () => {
  await api.traceBatch('9223372036854775807', { module: 'ERP', section: 'STOCK', cursor: 'batch/库存+9223372036854775806', limit: 500 });
  assert.equal(calls.at(-1).url, '/erp/wm/trace/batch/9223372036854775807');
  assert.deepEqual(calls.at(-1).params, { limit: 500, module: 'ERP', section: 'STOCK', cursor: 'batch/库存+9223372036854775806' });
});

test('MES修订POST使用实际原body，不将SN/清单/expectedVersion丢弃或转Number', async () => {
  const data = {
    commandId: 'correction-original',
    manifestId: '9223372036854775807',
    cancelledRevision: '17',
    snId: '9223372036854775806',
    componentMaterialId: '9223372036854775805',
    expectedVersion: '9223372036854775804',
    reversals: [{ originalEntryId: '9223372036854775803', quantity: '0.100000000000000001' }],
    allocations: [],
    reason: '实际消耗复核'
  };
  await api.correctSnComponent(data);
  assert.equal(calls.at(-1).url, '/mes/lt/trace/sn-consumption/correct');
  assert.equal(calls.at(-1).method, 'post');
  assert.deepEqual(calls.at(-1).data, data);
});

test('原生产入库客户端冻结命令跨其他业务提交后仍原样重放，不把下划线状态发给服务端', async () => {
  const snapshot = { ...header, _traceCommandId: 'receipt-original-command', sourceHeader: { woId: '9223372036854775807', version: 'opaque/事实' } };
  await api.executeProductionRecpt('9223372036854775806', snapshot);
  const first = structuredClone(calls.at(-1).data);
  await api.executeTransfer('9223372036854775805', header);
  await api.executeProductionRecpt('9223372036854775806', snapshot);
  assert.deepEqual(calls.at(-1).data, first);
  assert.equal(first.commandId, 'receipt-original-command');
  assert.equal(Object.hasOwn(first, '_traceCommandId'), false);
  assert.deepEqual(first.sourceHeader, snapshot.sourceHeader);
});

/** 原条码接口必须维持主对象和上下文映射，不把NULL历史补成新类型。 */
test('手工条码必须显式选择主对象且提供对应已有字符串ID', async () => {
  calls.length = 0;
  const base = { barcodeCode: 'BC-P4', configCode: 'BARCODE', materialId: '9223372036854775807' };
  for (const value of [
    base,
    { ...base, primaryObjectType: 'SN' },
    { ...base, primaryObjectType: 'BATCH' },
    { ...base, primaryObjectType: 'DOCUMENT', bizId: '9223372036854775806' }
  ]) {
    assert.throws(() => api.addBarcode(value), /主对象|正式SN|批次|业务类型/);
  }
  assert.throws(() => api.addBarcode({ ...base, primaryObjectType: 'MATERIAL', materialId: 9223372036854775000 }), /字符串/);
  assert.equal(calls.length, 0);
  await api.addBarcode({ ...base, primaryObjectType: 'SN', snId: '9223372036854775805', batchId: '9223372036854775804' });
  assert.equal(calls[0].url, '/erp/wm/barcode');
  assert.equal(calls[0].method, 'post');
  assert.equal(calls[0].data.primaryObjectType, 'SN');
  assert.equal(calls[0].data.snId, '9223372036854775805');
  assert.equal(calls[0].data.materialId, base.materialId);
});
test('条码修改保留原NULL主体及完整旧映射，只更新状态有效期备注', async () => {
  calls.length = 0;
  const original = {
    barcodeId: '9223372036854775807',
    barcodeCode: 'LEGACY-BC',
    primaryObjectType: null,
    configId: '9223372036854775806',
    bizType: 'OLD_IMPORT',
    bizId: '9223372036854775805',
    materialId: '9223372036854775804',
    snId: null,
    status: 'ACTIVE',
    remark: '原历史映射'
  };
  await api.updateBarcode({ ...original, status: 'EXPIRED', expireTime: '2026-10-20 12:34:56', remark: '仅补充审核说明' }, original);
  const sent = calls.at(-1);
  assert.equal(sent.method, 'put');
  assert.equal(sent.data.primaryObjectType, null);
  for (const key of ['barcodeId', 'barcodeCode', 'configId', 'bizType', 'bizId', 'materialId', 'snId']) assert.equal(sent.data[key], original[key]);
  assert.equal(sent.data.expireTime, '2026-10-20 12:34:56');
  assert.equal(sent.data.status, 'EXPIRED');
  assert.equal(original.remark, '原历史映射');
});
test('条码不可修改正式主码主体或上下文；历史NULL不能自行推造typed主体', async () => {
  const original = {
    barcodeId: '9223372036854775807',
    barcodeCode: 'ORIGINAL',
    primaryObjectType: null,
    materialId: '9223372036854775806',
    status: 'ACTIVE'
  };
  for (const change of [
    { primaryObjectType: 'MATERIAL' },
    { barcodeCode: 'REPLACED' },
    { materialId: '9223372036854775805' },
    { batchId: '9223372036854775804' },
    { bizType: 'WM_RECEIVE' }
  ]) {
    calls.length = 0;
    await assert.rejects(() => api.updateBarcode({ ...original, ...change }, original), /不可重绑|不可修改/);
    assert.equal(calls.length, 0);
  }
  const typed = { ...original, primaryObjectType: 'SN', snId: '9223372036854775804' };
  await assert.rejects(() => api.updateBarcode({ ...typed, primaryObjectType: 'BATCH' }, typed), /不可重绑|不可修改/);
  calls.length = 0;
  await api.updateBarcode({ barcodeId: original.barcodeId, status: 'USED' }, original);
  assert.equal(calls.at(-1).data.barcodeCode, original.barcodeCode);
  assert.equal(calls.at(-1).data.primaryObjectType, null);
  assert.equal(calls.at(-1).data.materialId, original.materialId);
});
test('条码单据主对象仅用bizType与bizId，旧生成URL保留全部长ID参数供服务端推导', async () => {
  calls.length = 0;
  const data = {
    barcodeCode: 'DOC-BC',
    primaryObjectType: 'DOCUMENT',
    bizType: 'WM_MATERIAL_RECEIVE',
    bizId: '9223372036854775807',
    generateTime: '2000-01-01 00:00:00',
    bindTime: '2000-01-01 00:00:00'
  };
  await api.addBarcode(data);
  const { generateTime, bindTime, ...expected } = data;
  assert.deepEqual(calls.at(-1).data, expected);
  assert.equal(calls.at(-1).data.generateTime, undefined);
  assert.equal(calls.at(-1).data.bindTime, undefined);
  assert.throws(() => api.addBarcode({ ...data, bizType: 'WM_RECEIVE' }), /单据类型|业务类型/);
  for (const key of ['bizLineId', 'snId', 'batchId', 'materialId', 'stockId'])
    assert.throws(() => api.addBarcode({ ...data, [key]: '9223372036854775806' }), /单据主对象|上下文/);
  for (const bizType of ['WM_MATERIAL_RECEIVE', 'WM_PRODUCTION_RECPT', 'WM_PRODUCTION_ISSUE', 'WM_SALES_ISSUE', 'WM_TRANSFER', 'WM_MATERIAL_RETURN'])
    await api.addBarcode({ ...data, bizType });
  const request = { configCode: 'BARCODE', batchId: '9223372036854775804', materialId: '9223372036854775805' };
  await api.generateBarcode(request);
  assert.equal(calls.at(-1).url, '/erp/wm/barcode/generateByCode');
  assert.deepEqual(calls.at(-1).params, request);
});

/** 批次累计过账事实不可被元数据编辑改写，停用走原状态业务入口。 */
test('批次元数据更新拒绝重绑主体来源或改写累计事实', async () => {
  const original = {
    batchId: '9223372036854775807',
    batchCode: 'BATCH-ORIGINAL',
    materialId: '9223372036854775806',
    warehouseId: '9223372036854775805',
    sourceDocType: 'WM_MATERIAL_RECEIVE',
    sourceDocId: '9223372036854775804',
    quantity: '0.300000000000000001',
    receivedQty: '1.300000000000000001',
    issuedQty: '1',
    status: 'ACTIVE'
  };
  for (const change of [
    { batchCode: 'REPLACED' },
    { materialId: '9223372036854775803' },
    { warehouseId: '9223372036854775802' },
    { sourceDocId: '9223372036854775801' },
    { quantity: '999' },
    { receivedQty: '999' },
    { issuedQty: '0' }
  ]) {
    calls.length = 0;
    await assert.rejects(() => api.updateBatch({ ...original, ...change }, original), /批次.*不可修改|累计.*不可修改/);
    assert.equal(calls.length, 0);
  }
  calls.length = 0;
  await api.updateBatch({ ...original, status: 'INACTIVE', expiryDate: '2026-10-20', remark: '仅维护元数据' }, original);
  assert.equal(calls.at(-1).url, '/erp/wm/batch');
  assert.equal(calls.at(-1).method, 'put');
  for (const key of ['batchId', 'batchCode', 'materialId', 'warehouseId', 'sourceDocType', 'sourceDocId', 'quantity', 'receivedQty', 'issuedQty'])
    assert.equal(calls.at(-1).data[key], original[key]);
  assert.equal(calls.at(-1).data.expiryDate, '2026-10-20');
  assert.equal(calls.at(-1).data.status, 'INACTIVE');
});
test('批次停用复用原changeStatus并保持长ID，不触发物理DELETE', async () => {
  calls.length = 0;
  await api.changeStatus('9223372036854775807', 'INACTIVE');
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/erp/wm/batch/changeStatus');
  assert.equal(calls[0].method, 'put');
  assert.deepEqual(calls[0].params, { batchId: '9223372036854775807', status: 'INACTIVE' });
});
