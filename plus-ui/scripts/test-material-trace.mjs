import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';

// 内存打包实际实现，不生成共享 node_modules 临时文件，也不连接服务。
const output = await build({ entryPoints: ['src/components/material-trace/model.ts'], bundle: true, write: false, format: 'esm', platform: 'node' });
const m = await import(`data:text/javascript;base64,${Buffer.from(output.outputFiles[0].text).toString('base64')}`);
const policy = { batchManaged: true, serialManaged: true, allowMixedLot: false, version: '9007199254740993123', stage: 'STRICT' };
const result = (overrides = {}) => ({
  status: 'RESOLVED',
  objectType: 'SN',
  identityId: '9007199254740993123',
  materialId: '9007199254740993124',
  materialCode: 'MAT-1',
  canonicalSnCode: 'SN-1',
  batchId: '9007199254740993125',
  batchCode: 'LOT-1',
  policy,
  ...overrides
});
const row = (overrides = {}) => ({
  materialId: '9007199254740993124',
  materialCode: 'MAT-1',
  batchId: '9007199254740993125',
  quantity: '1.000',
  detailVersion: 'facts:sha256:opaque-01',
  policy,
  ...overrides
});

/** 四种策略组合均由主数据决定，不把允许混批用于库存 SN 收集。 */
test('四种批次与序列管理组合', () => {
  assert.equal(m.policyCombination({ ...policy, batchManaged: false, serialManaged: false }), '数量管理');
  assert.equal(m.policyCombination({ ...policy, serialManaged: false }), '批次管理');
  assert.equal(m.policyCombination({ ...policy, batchManaged: false }), '序列号管理');
  assert.equal(m.policyCombination(policy), '批次 + 序列号管理');
});
test('完整 OCR 对象提交解析，保留匹配对象和原始证据', () => {
  const ocr = {
    materialCode: 'MAT-1',
    snCode: 'EXT-1',
    matchedMaterial: { materialId: '9007199254740993124' },
    rawText: '原始标签',
    confidence: '0.97'
  };
  assert.deepEqual(JSON.parse(m.rawCodeText(ocr)), ocr);
});
test('解析歧义、无结果、无效码均不能绑定', () => {
  for (const status of ['AMBIGUOUS', 'NOT_FOUND', 'INVALID']) assert.throws(() => m.bindResolvedObject(row(), result({ status })), /解析|候选|识别/);
});
test('不能部分替换物料编码并保留旧物料 ID', () => {
  const original = row();
  assert.throws(() => m.bindResolvedObject(original, result({ materialId: '99', materialCode: 'OTHER' })), /物料/);
  assert.equal(original.materialCode, 'MAT-1');
  assert.equal(original.materialId, '9007199254740993124');
});
test('完整解析对象原子绑定并保留来源及 opaque 详情版本', () => {
  const original = row({ sourceDocId: '9223372036854775806', sourceDocType: 'PURCHASE_RECEIVE', lineId: '9223372036854775805' });
  const bound = m.bindResolvedObject(original, result());
  assert.equal(bound.materialId, original.materialId);
  assert.equal(bound.detailVersion, original.detailVersion);
  assert.equal(bound.sourceDocId, original.sourceDocId);
  assert.equal(bound.lineId, original.lineId);
  assert.equal(bound.snCode, 'SN-1');
});
test('缺失身份、物料或批次证据时禁止部分绑定', () => {
  for (const field of ['identityId', 'materialId', 'canonicalSnCode', 'batchId'])
    assert.throws(() => m.bindResolvedObject(row(), result({ [field]: undefined, snCode: undefined })), /完整|身份|批次|物料/);
});
test('正式身份和外部别名重复扫描不会产生第二件', () => {
  const first = m.appendIdentity([], result(), row());
  assert.throws(() => m.appendIdentity(first, result({ snCode: 'SUPPLIER-ALIAS' }), row()), /重复/);
});
test('来源行分配批次约束不受允许混批开关影响', () => {
  for (const allowMixedLot of [false, true])
    assert.throws(() => m.appendIdentity([], result({ batchId: 'other' }), row({ policy: { ...policy, allowMixedLot } })), /批次/);
});
test('未分配批次的库存集合允许多成品批次，不能误用组件禁止混批规则', () => {
  const current = row({ batchId: undefined, batchCode: undefined, quantity: '2' });
  const first = m.appendIdentity([], result(), current);
  const second = m.appendIdentity(first, result({ identityId: '2', canonicalSnCode: 'SN-2', batchId: '3', batchCode: 'LOT-2' }), current);
  assert.equal(m.inventoryLineError({ ...current, snItems: second }), '');
});
test('SN 数量按精确整数比较，拒绝小数和数量不匹配', () => {
  const snItems = m.appendIdentity([], result(), row());
  assert.equal(m.inventoryLineError(row({ snItems })), '');
  assert.match(m.inventoryLineError(row({ snItems, quantity: '1.5' })), /整数/);
  assert.match(m.inventoryLineError(row({ snItems, quantity: '9007199254740993123' })), /数量/);
});
test('来源库存、目标库位、原交易保持字符串', () => {
  const items = m.appendIdentity(
    [],
    result(),
    row({ sourceStockId: '9223372036854775806', toLocationId: '9223372036854775805', originalTxId: '9223372036854775804' })
  );
  assert.equal(items[0].sourceStockId, '9223372036854775806');
  assert.equal(items[0].targetLocationId, '9223372036854775805');
  assert.equal(items[0].originalTxId, '9223372036854775804');
});
test('同一请求重放使用原命令，修改内容生成新命令', () => {
  let sequence = 0;
  const command = m.createCommandSession(() => `command-${++sequence}`);
  assert.equal(command.forPayload({ quantity: '1', materialId: '99' }), command.forPayload({ materialId: '99', quantity: '1' }));
  assert.notEqual(command.forPayload({ quantity: '2', materialId: '99' }), 'command-1');
});
test('原单据字段、源单头和 opaque 版本整体透传', () => {
  const line = row({ snItems: m.appendIdentity([], result(), row()) });
  const header = {
    detailVersion: 'header:abc-9',
    sourceHeader: { revision: 'base64:abc==' },
    receiveId: '9223372036854775801',
    wmMaterialReceiveLineList: [line]
  };
  const body = m.prepareTraceDocument(header, 'command-original');
  assert.equal(body.commandId, 'command-original');
  assert.equal(body.detailVersion, 'header:abc-9');
  assert.deepEqual(body.sourceHeader, header.sourceHeader);
  assert.equal(body.wmMaterialReceiveLineList[0].detailVersion, line.detailVersion);
  assert.equal(body.wmMaterialReceiveLineList[0].snItems[0].identityId, '9007199254740993123');
});
test('过账时缺失服务端详情版本时拒绝伪造版本', () => {
  assert.throws(() => m.postingBody(row({ snItems: [], detailVersion: undefined }), [], 'quantity', 'cmd'), /版本/);
});
test('同产品 SN 同组件跨批次消耗遵循策略，数量精确保留', () => {
  const entries = [
    { productIdentityId: 'p1', componentMaterialId: 'm1', lotId: 'l1', quantity: '0.100000000000000001' },
    { productIdentityId: 'p1', componentMaterialId: 'm1', lotId: 'l2', quantity: '0.2' }
  ];
  assert.match(m.consumptionError(entries, false), /混批/);
  assert.equal(m.consumptionError(entries, true), '');
  assert.equal(entries[0].quantity, '0.100000000000000001');
});
test('不同产品 SN 可使用不同组件批次', () => {
  assert.equal(
    m.consumptionError(
      [
        { productIdentityId: 'p1', componentMaterialId: 'm1', lotId: 'l1', quantity: '1' },
        { productIdentityId: 'p2', componentMaterialId: 'm1', lotId: 'l2', quantity: '1' }
      ],
      false
    ),
    ''
  );
});
test('组件禁止混批检查包含已有不可变分录', () => {
  const prior = [{ productIdentityId: 'p1', componentMaterialId: 'm1', lotId: 'l1', quantity: '1' }];
  assert.match(m.consumptionError([{ productIdentityId: 'p1', componentMaterialId: 'm1', lotId: 'l2', quantity: '1' }], false, prior), /混批/);
});
test('零、负数、指数数量和不完整组件证据不能提交', () => {
  for (const quantity of ['0', '-1', '1e3', 'NaN'])
    assert.match(m.consumptionError([{ productIdentityId: 'p1', componentMaterialId: 'm1', lotId: 'l1', quantity }], true), /数量/);
});
test('追溯没有实物证明时明确展示范围与历史缺口', () => {
  assert.match(m.traceScopeText({}), /未声明|未提供/);
  assert.match(m.traceScopeText({ scope: 'BATCH', legacyGaps: ['缺少历史实物证据'] }), /批次/);
});
test('只有经过审阅且带摘要的清单才允许下载', () => {
  assert.throws(() => m.reviewedManifest({ status: 'AUDITED', manifest: { sql: 'unsafe' } }), /审阅|摘要/);
  assert.deepEqual(
    m.reviewedManifest({ status: 'REVIEWED', manifestDigest: 'sha256:abc', reviewedManifest: { runId: '9223372036854775807', changes: [] } }),
    { runId: '9223372036854775807', changes: [] }
  );
});
/** 十进制比较按字符串缩放，组件数量不经浮点数截断。 */
test('组件消耗数量不能超出批次余额，长小数保持精确', () => {
  assert.equal(m.decimalCompare('0.100000000000000002', '0.100000000000000001'), 1);
  assert.equal(m.decimalCompare('9007199254740993.1', '9007199254740993.10'), 0);
  assert.equal(m.decimalCompare('0.09', '0.1'), -1);
});
/** 延迟批次生成结果不能覆盖后来扫描或切换物料后形成的完整身份。 */
test('延迟批次生成不覆盖新 OCR 身份或新来源行', () => {
  const original = row({ lineId: 'line-1', batchId: undefined, batchCode: undefined, snItems: [] });
  const batch = { batchId: '9223372036854775807', batchCode: 'NEW-LOT' };
  assert.equal(m.generatedBatchBinding(original, original, batch).batchId, batch.batchId);
  assert.equal(m.generatedBatchBinding({ ...original, snItems: [{ identityId: 'sn-later' }] }, original, batch), undefined);
  assert.equal(m.generatedBatchBinding({ ...original, materialId: 'other' }, original, batch), undefined);
  assert.equal(m.generatedBatchBinding({ ...original, lineId: 'other' }, original, batch), undefined);
});
/** 详情版本属于来源行时，原单头不能被首行版本替换。 */
test('多行详情保留各自行版本且不伪造单头版本', () => {
  const first = row({ snItems: m.appendIdentity([], result(), row()), detailVersion: 'line:A' });
  const second = row({ snItems: [{ identityId: '2', materialId: first.materialId, batchId: first.batchId }], detailVersion: 'line:B' });
  const header = { transferId: '9223372036854775807', sourceHeader: { warehouseId: '9223372036854775806' }, wmTransferLineList: [first, second] };
  const body = m.postingBody(header, [first, second], 'quantity', 'command-rows');
  assert.equal(body.detailVersion, undefined);
  assert.equal(body.wmTransferLineList[0].detailVersion, 'line:A');
  assert.equal(body.wmTransferLineList[1].detailVersion, 'line:B');
  assert.deepEqual(body.sourceHeader, header.sourceHeader);
});

/** 盘点过账选择差异实物，盘亏同样使用数量绝对值，不拿盘点总数充当差异。 */
test('盘点 SN 数量匹配正负差异绝对值', () => {
  const snItems = m.appendIdentity([], result(), row());
  assert.equal(m.inventoryLineError(row({ snItems, actualQuantity: '9', bookQuantity: '10', differenceQuantity: '-1' }), 'differenceQuantity'), '');
  assert.match(m.inventoryLineError(row({ snItems, differenceQuantity: '-2' }), 'differenceQuantity'), /数量/);
});

/** DOCUMENT 使用独立单据字段，不能把旧 SN 字段冒充单据身份。 */
test('DOCUMENT 解析保留 objectId/documentType/documentCode，不借用 SN 身份', () => {
  const document = m.resolvedObject({
    status: 'RESOLVED',
    objectType: 'DOCUMENT',
    objectId: '9223372036854775807',
    documentType: 'WM_TRANSFER',
    documentCode: 'TR-001'
  });
  assert.equal(document.objectId, '9223372036854775807');
  assert.equal(document.identityId, undefined);
  assert.equal(document.documentCode, 'TR-001');
  assert.throws(() => m.resolvedObject({ status: 'RESOLVED', objectType: 'DOCUMENT', identityId: '9', snCode: 'DOC-FAKE' }), /单据|完整/);
});

const outputContext = {
  cardId: 'CARD',
  stepRecordId: 'STEP',
  mesWoId: 'WO',
  materialId: 'MAT',
  isFinalStep: true,
  policy: { batchManaged: true, serialManaged: false, allowMixedLot: false, version: '7', stage: 'STRICT' },
  sources: [
    {
      lotId: 'LOT-G',
      lotCode: 'GOOD-LOT',
      sourceAllocationId: 'ALLOC-G',
      mesWoId: 'WO',
      materialId: 'MAT',
      quantity: '0.100000000000000001',
      status: 'IN_STOCK',
      qcStatus: 'PASS'
    },
    {
      lotId: 'LOT-S',
      lotCode: 'SCRAP-LOT',
      sourceAllocationId: 'ALLOC-S',
      mesWoId: 'WO',
      materialId: 'MAT',
      quantity: '0.000000000000000001',
      status: 'SCRAPPED',
      qcStatus: 'FAIL'
    }
  ],
  outputs: []
};
const outputLines = [
  { lotId: 'LOT-G', sourceAllocationId: 'ALLOC-G', quantity: '0.100000000000000001', result: 'GOOD' },
  { lotId: 'LOT-S', sourceAllocationId: 'ALLOC-S', quantity: '0.000000000000000001', result: 'SCRAP' }
];
test('batch-only真实产出按原来源及整lot的GOOD/SCRAP数量精确守恒，不套用组件混批', () => {
  assert.doesNotThrow(() =>
    m.validateProductOutputs(outputLines, outputContext, { goodQty: '0.100000000000000001', scrapQty: '0.000000000000000001' })
  );
  assert.equal(m.decimalSum(['0.1', '0.000000000000000001']), '0.100000000000000001');
});
test('产出lot缺原allocation、错物料/工单、拆分数量或重复分类均拒绝', () => {
  for (const change of [{ sourceAllocationId: undefined }, { sourceAllocationId: 'OTHER' }, { quantity: '0.1' }, { result: 'SCRAP' }])
    assert.throws(() =>
      m.validateProductOutputs([{ ...outputLines[0], ...change }], outputContext, { goodQty: '0.100000000000000001', scrapQty: '0' })
    );
  for (const change of [{ materialId: 'OTHER' }, { mesWoId: 'OTHER' }])
    assert.throws(() =>
      m.validateProductOutputs(
        [outputLines[0]],
        { ...outputContext, sources: [{ ...outputContext.sources[0], ...change }] },
        { goodQty: '0.100000000000000001', scrapQty: '0' }
      )
    );
  assert.throws(
    () => m.validateProductOutputs([outputLines[0], { ...outputLines[0], result: 'SCRAP' }], outputContext, { goodQty: '0.1', scrapQty: '0.1' }),
    /重复|整批/
  );
});
test('已登记lot不重复产出，非末工序不能登记GOOD，原数量模式保留', () => {
  assert.throws(
    () =>
      m.validateProductOutputs(
        [outputLines[0]],
        { ...outputContext, outputs: [{ ...outputLines[0], outputId: 'OLD' }] },
        { goodQty: '0.100000000000000001', scrapQty: '0' }
      ),
    /已登记|重复/
  );
  assert.throws(
    () => m.validateProductOutputs([outputLines[0]], { ...outputContext, isFinalStep: false }, { goodQty: '0.100000000000000001', scrapQty: '0' }),
    /末工序/
  );
  assert.equal(m.productOutputRequirement({ ...outputContext.policy, serialManaged: true }, true, '2', '0').required, false);
  assert.equal(m.productOutputRequirement(outputContext.policy, true, '2', '0').required, true);
  assert.equal(m.productOutputRequirement(outputContext.policy, false, '2', '0').required, false);
  assert.equal(m.productOutputRequirement(outputContext.policy, false, '2', '1').required, true);
});

/** 原单据提交与跨模块确认必须区分，未知状态不能显示已确认。 */
test('提交提示保留原命令和字符串组ID，NEW/冲突与缺事件均不显示已确认', () => {
  const base = { commandId: 'original', postingGroupIds: ['9223372036854775807'], submissionWarnings: ['原消费冲突'] };
  const pending = m.submissionNotice({ data: { ...base, submissionState: 'CROSS_MODULE_PENDING' } }, '执行成功');
  assert.equal(pending.type, 'warning');
  assert.match(pending.message, /跨模块.*待确认/);
  assert.match(pending.message, /原消费冲突/);
  assert.deepEqual(pending.postingGroupIds, base.postingGroupIds);
  assert.equal(pending.commandId, 'original');
  assert.equal(m.submissionNotice({ ...base, submissionState: 'SERVER_COMMITTED' }).type, 'warning');
  assert.equal(m.submissionNotice({ ...base, submissionState: 'CONFIRMED', submissionWarnings: [] }).type, 'success');
  assert.equal(m.submissionNotice({ ...base, submissionState: 'FUTURE_STATE' }).type, 'warning');
  assert.equal(m.submissionNotice({ data: null }, '普通数量执行成功').message, '普通数量执行成功');
});

test('实际产出ACK丢失重试原payload，原complete失败只重试complete且不再次登记lot', async () => {
  const payload = {
    commandId: 'output-original',
    cardId: '9223372036854775807',
    stepRecordId: '9223372036854775806',
    lines: [{ lotId: 'l', sourceAllocationId: 'a', quantity: '0.123456789012345678', result: 'SCRAP' }]
  };
  const writes = [];
  let loseAck = true;
  let failComplete = true;
  let completions = 0;
  const attempt = m.createProductOutputAttempt(payload, async (body) => {
    writes.push(structuredClone(body));
    if (loseAck) {
      loseAck = false;
      throw new Error('ACK lost');
    }
    return { data: ['9223372036854775805'] };
  });
  const finish = async () => {
    completions++;
    if (failComplete) {
      failComplete = false;
      throw new Error('original complete failed');
    }
    return 'complete';
  };
  await assert.rejects(attempt.submit(finish), /ACK lost/);
  assert.equal(completions, 0);
  await assert.rejects(attempt.submit(finish), /original complete failed/);
  assert.equal(attempt.saved, true);
  assert.equal(await attempt.submit(finish), 'complete');
  assert.deepEqual(writes, [payload, payload]);
  assert.equal(completions, 2);
});
test('重读实际产出后仅补缺少数量，已保存报废与良品精确比较', () => {
  const context = {
    outputs: [
      { result: 'GOOD', quantity: '0.1' },
      { result: 'SCRAP', quantity: '0.000000000000000001' }
    ]
  };
  assert.deepEqual(m.remainingProductOutputs(context, { goodQty: '0.3', scrapQty: '0.000000000000000001' }), { goodQty: '0.2', scrapQty: '0' });
  assert.throws(() => m.remainingProductOutputs(context, { goodQty: '0', scrapQty: '1' }), /已登记.*超出/);
});

const correctionSource = () => ({
  manifest: { manifestId: '9223372036854775807', mesWoId: '9223372036854775806', revision: '17', status: 'CANCELLED' },
  sn: {
    snId: '9223372036854775805',
    identityId: '9223372036854775804',
    identityVersion: '9223372036854775803',
    mesWoId: '9223372036854775806',
    cardId: '9223372036854775802',
    snStatus: 'COMPLETED',
    handoffStatus: 'NONE'
  },
  entries: [
    {
      entry_id: '9223372036854775801',
      local_sn_id: '9223372036854775805',
      identity_id: '9223372036854775804',
      component_material_id: '9223372036854775800',
      component_lot_id: '9223372036854775799',
      quantity: '0.300000000000000001',
      entry_type: 'CONSUME'
    },
    {
      entry_id: '9223372036854775798',
      original_entry_id: '9223372036854775801',
      local_sn_id: '9223372036854775805',
      identity_id: '9223372036854775804',
      component_material_id: '9223372036854775800',
      component_lot_id: '9223372036854775799',
      quantity: '0.1',
      entry_type: 'REVERSE'
    }
  ],
  sources: [
    {
      consumptionId: '9223372036854775797',
      woId: '9223372036854775806',
      materialId: '9223372036854775800',
      lotId: '9223372036854775796',
      consumedQty: '0.200000000000000001'
    }
  ],
  steps: [{ stepRecordId: '9223372036854775795', stepStatus: 'COMPLETED' }],
  policy: { batchManaged: true, serialManaged: false, allowMixedLot: false, version: '1', stage: 'STRICT' }
});
const correctionDraft = () => ({
  commandId: 'correction-original',
  componentMaterialId: '9223372036854775800',
  reason: '核对实际批次后更正单件消耗',
  reversals: [{ originalEntryId: '9223372036854775801', quantity: '0.200000000000000001' }],
  allocations: [
    {
      consumptionId: '9223372036854775797',
      stepRecordId: '9223372036854775795',
      quantity: '0.200000000000000001',
      operationRef: '末工序实际消耗复核'
    }
  ]
});
test('修订用真实原分录净额、原SN版本、CANCELLED清单和真实工序，长ID与数量不Number化', () => {
  const context = correctionSource(),
    draft = correctionDraft();
  const rows = m.netComponentEntries(context.entries);
  assert.equal(rows[0].remainingQuantity, '0.200000000000000001');
  const payload = m.buildComponentCorrection(draft, context);
  assert.deepEqual(payload, {
    ...draft,
    manifestId: context.manifest.manifestId,
    cancelledRevision: '17',
    snId: context.sn.snId,
    expectedVersion: context.sn.identityVersion
  });
});
test('修订拒绝未确认取消、错误工单/单件/工序与Number版本，不能猜来源', () => {
  for (const change of [
    (c) => {
      c.manifest.status = 'CANCEL_REQUESTED';
    },
    (c) => {
      c.sn.mesWoId = '99';
    },
    (c) => {
      c.entries[0].local_sn_id = '99';
    },
    (c) => {
      c.sn.identityVersion = 1;
    },
    (c) => {
      c.sn.handoffStatus = 'RECEIVED';
    },
    (c) => {
      c.steps[0].stepStatus = 'IN_PROGRESS';
    }
  ]) {
    const context = correctionSource();
    change(context);
    assert.throws(() => m.buildComponentCorrection(correctionDraft(), context), /取消|工单|单件|版本|移交|工序/);
  }
});
test('修订原冲销不可超过已扣历史冲销后的净额，新增分配不可超过原实际用量', () => {
  const draft = correctionDraft();
  draft.reversals[0].quantity = '0.3';
  assert.throws(() => m.buildComponentCorrection(draft, correctionSource()), /净额|冲销/);
  const other = correctionDraft();
  other.allocations[0].quantity = '0.200000000000000002';
  assert.throws(() => m.buildComponentCorrection(other, correctionSource()), /实际消耗|上限/);
});
test('同产品组件禁止混批：冲销全部旧lot可换lot，保留旧lot净额不能叠新lot', () => {
  const draft = correctionDraft();
  draft.reversals[0].quantity = '0.1';
  assert.throws(() => m.buildComponentCorrection(draft, correctionSource()), /混批|跨批次/);
  const context = correctionSource();
  context.policy.allowMixedLot = true;
  assert.doesNotThrow(() => m.buildComponentCorrection(draft, context));
});
test('修订空白原因、重复原分录、空请求与历史分录缺口均阻断', () => {
  const draft = correctionDraft();
  draft.reason = ' ';
  assert.throws(() => m.buildComponentCorrection(draft, correctionSource()), /原因/);
  const repeated = correctionDraft();
  repeated.reversals.push({ ...repeated.reversals[0] });
  assert.throws(() => m.buildComponentCorrection(repeated, correctionSource()), /重复/);
  const empty = correctionDraft();
  empty.reversals = [];
  empty.allocations = [];
  assert.throws(() => m.buildComponentCorrection(empty, correctionSource()), /明细|冲销|分配/);
  assert.throws(() => m.netComponentEntries([{ entry_id: '1', entry_type: 'REVERSE', original_entry_id: '2', quantity: '1' }]), /原分录|缺口/);
});
