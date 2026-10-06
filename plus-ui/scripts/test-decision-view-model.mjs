import assert from 'node:assert/strict';
import { test } from 'node:test';
import { pathToFileURL } from 'node:url';
const {
  riskTagType,
  formatDelta,
  canApproveRun,
  sortScenarios,
  summarizeActionPayload,
  decisionSummaryText,
  shouldPollDecision,
  isDecisionAnalysisRunning,
  decisionAnalysisElapsedSeconds,
  flowProgressEventId,
  applyDecisionFlowEvent,
  formatDecisionElapsed,
  previewScheduleRows,
  decisionNodeLabel,
  decisionRiskLabel,
  decisionRunStatusLabel
} = await import(
  process.env.DECISION_VIEW_MODEL_BUNDLE
    ? pathToFileURL(process.env.DECISION_VIEW_MODEL_BUNDLE).href
    : new URL('../node_modules/.tmp/decision-view-model.mjs', import.meta.url).href
);

test('既有决策展示规则回归', () => {
  assert.equal(riskTagType('CRITICAL'), 'danger');
  assert.equal(riskTagType('HIGH'), 'danger');
  assert.equal(riskTagType('MEDIUM'), 'warning');
  assert.equal(riskTagType('LOW'), 'success');
  assert.equal(formatDelta(-3, '天'), '改善 3 天');
  assert.equal(formatDelta(2, '天'), '增加 2 天');
  assert.equal(canApproveRun({ status: 'WAITING_APPROVAL', stale: false }), true);
  assert.equal(canApproveRun({ status: 'STALE', stale: true }), false);
  assert.equal(decisionSummaryText('RUNNING'), '分析正在进行，请稍后刷新。');
  assert.match(decisionSummaryText('FAILED'), /分析失败/);
  assert.doesNotMatch(decisionSummaryText('FAILED'), /api.?key|Bearer|token/i);
  assert.match(decisionSummaryText('STALE'), /重新分析/);
  assert.equal(decisionSummaryText('SUCCEEDED', '已验证的总结'), '已验证的总结');
  assert.match(decisionSummaryText('WAITING_APPROVAL'), /等待审批/);
  assert.match(decisionSummaryText('PARTIAL_SUCCESS'), /部分/);
  assert.match(decisionSummaryText('ROLLED_BACK'), /回滚/);
  assert.match(decisionSummaryText('CANCELLED'), /取消/);
  assert.match(decisionSummaryText('ROLLING_BACK'), /回滚/);
  for (const status of ['CREATED', 'RUNNING']) {
    assert.equal(isDecisionAnalysisRunning(status), true, `${status} is an active analysis`);
  }
  for (const status of ['WAITING_APPROVAL', 'APPROVED', 'EXECUTING', 'ROLLING_BACK', 'SUCCEEDED', 'FAILED']) {
    assert.equal(isDecisionAnalysisRunning(status), false, `${status} must not keep analysis running`);
  }
  assert.equal(decisionAnalysisElapsedSeconds('RUNNING', 0, Number.NaN, 75_000), 75);
  assert.equal(decisionAnalysisElapsedSeconds('APPROVED', 0, 62_000, 75_000), 62);
  assert.equal(decisionAnalysisElapsedSeconds('APPROVED', 0, 62_000, 80_000), 62);
  assert.equal(decisionAnalysisElapsedSeconds('APPROVED', 0, Number.NaN, 80_000), null);
  assert.equal(decisionAnalysisElapsedSeconds('RUNNING', Number.NaN, Number.NaN, 80_000), null);
  assert.equal(shouldPollDecision('APPROVED'), true, 'approval still needs status reconciliation');
  assert.equal(shouldPollDecision('RUNNING'), true);
  assert.equal(shouldPollDecision('EXECUTING'), true);
  assert.equal(shouldPollDecision('ROLLING_BACK'), true);
  assert.equal(shouldPollDecision('FAILED'), false);
  assert.equal(shouldPollDecision('WAITING_APPROVAL'), false);
  const flowId = '2103881041356783618';
  const start = flowProgressEventId(
    {
      type: 'AI_FLOW_NODE_START',
      content: '{"instanceId":2103881041356783618,"nodeId":"llm","seq":17}'
    },
    flowId
  );
  assert.deepEqual(start, { nodeId: 'llm', status: 'RUNNING', seq: 17 });
  assert.equal(
    flowProgressEventId(
      {
        type: 'AI_FLOW_NODE_START',
        content: '{"instanceId":2103881041356783619,"nodeId":"llm","seq":18}'
      },
      flowId
    ),
    null
  );
  assert.deepEqual(
    flowProgressEventId(
      {
        type: 'AI_FLOW_NODE_END',
        content: '{"instanceId":2103881041356783618,"nodeId":"llm","status":"FAILED","seq":18}'
      },
      flowId
    ),
    { nodeId: 'llm', status: 'FAILED', seq: 18 }
  );
  assert.deepEqual(applyDecisionFlowEvent(applyDecisionFlowEvent({}, { nodeId: 'llm', status: 'FAILED', seq: 18 }), start), { llm: 'FAILED' });
  assert.equal(formatDecisionElapsed(75), '1分15秒');
  assert.equal(decisionNodeLabel('llm'), '模型摘要');
  assert.equal(decisionRiskLabel('HIGH'), '高风险');
  assert.equal(decisionRunStatusLabel('WAITING_APPROVAL'), '待审批');
  const schedule = previewScheduleRows([
    {
      woId: '9007199254745006',
      woNo: 'DEMO-006',
      stepId: '9007199254745222',
      resourceName: 'A-01',
      startTime: '2026-09-27',
      endTime: '2026-09-30',
      processHours: '12.5',
      unexpected: 'secret'
    }
  ]);
  assert.equal(schedule[0].woId, '9007199254745006');
  assert.equal(schedule[0].resourceName, 'A-01');
  assert.equal('unexpected' in schedule[0], false);
  assert.equal(previewScheduleRows([{ woId: Number.MAX_SAFE_INTEGER + 100 }])[0].woId, '—');

  assert.deepEqual(
    sortScenarios([
      { scenarioCode: 'B', rank: 2 },
      { scenarioCode: 'A', rank: 1 }
    ]).map((item) => item.scenarioCode),
    ['A', 'B']
  );
  assert.match(
    summarizeActionPayload(
      '{"runId":"21","materialLines":[{"materialId":"9007199254740993","gap":"5","requiredDate":"2026-09-26"}],"requiredDate":"2026-09-26"}'
    ),
    /9007199254740993/
  );
  assert.match(
    summarizeActionPayload(
      '{"runId":"21","materialLines":[{"materialId":"9007199254740993","gap":"5","requiredDate":"2026-09-26"}],"requiredDate":"2026-09-26"}'
    ),
    /缺口 5/
  );
  assert.equal(summarizeActionPayload('{'), '载荷不可验证，请勿批准');
});

const invalidPayload = '载荷不可验证，请勿批准';
const frozenReminder = '冻结意图，审批和执行仍由ERP重新核验';
const purchaseFields = [
  'purchase_payload_version',
  'runId',
  'materialId',
  'vendorId',
  'windowEnd',
  'materialVersion',
  'vendorVersion',
  'supplierTermsId',
  'supplierTermsVersion',
  'baseUomId',
  'baseUomVersion',
  'orderUomId',
  'orderUomVersion',
  'conversionVersion',
  'requirement_detail_ids',
  'frozenLines',
  'expectedSourceFingerprint'
];
const lineFields = ['detailId', 'baseUomId', 'coveredDemandBase', 'orderUomId', 'requestedOrderQty', 'requiredDate'];
const versionFields = ['materialVersion', 'vendorVersion', 'supplierTermsVersion', 'baseUomVersion', 'orderUomVersion', 'conversionVersion'];

function purchasePayload() {
  return {
    purchase_payload_version: 2,
    runId: '2103881041356783618',
    materialId: '9007199254741011',
    vendorId: '9007199254741021',
    windowEnd: '2026-10-31',
    materialVersion: 'M-20261006',
    vendorVersion: 'V-20261006',
    supplierTermsId: '9007199254741031',
    supplierTermsVersion: 'T-20261006',
    baseUomId: '26',
    baseUomVersion: 'Q-20261006',
    orderUomId: '26',
    orderUomVersion: 'Q-20261006',
    conversionVersion: 'Q-20261006',
    requirement_detail_ids: ['9007199254740993', '9007199254740994', '9007199254740995'],
    frozenLines: [
      {
        detailId: '9007199254740993',
        baseUomId: '26',
        coveredDemandBase: '6.0000',
        orderUomId: '26',
        requestedOrderQty: '6',
        requiredDate: '2026-10-07'
      },
      {
        detailId: '9007199254740994',
        baseUomId: '26',
        coveredDemandBase: '6',
        orderUomId: '26',
        requestedOrderQty: '6.00000',
        requiredDate: '2026-10-08'
      },
      {
        detailId: '9007199254740995',
        baseUomId: '26',
        coveredDemandBase: '12',
        orderUomId: '26',
        requestedOrderQty: '12.0000',
        requiredDate: '2026-10-31'
      }
    ],
    expectedSourceFingerprint: 'ab'.repeat(32)
  };
}
function summarizePurchase(payload) {
  return summarizeActionPayload(JSON.stringify(payload));
}
function expectedLine(line, quantity) {
  return (
    '需求来源ID ' +
    line.detailId +
    '：需求日 ' +
    line.requiredDate +
    '，冻结覆盖量 ' +
    quantity +
    '（基本单位ID ' +
    line.baseUomId +
    '），订购量 ' +
    quantity +
    '（订购单位ID ' +
    line.orderUomId +
    '）'
  );
}
function manySources(count) {
  const payload = purchasePayload();
  payload.frozenLines = Array.from({ length: count }, (_, index) => ({
    ...payload.frozenLines[0],
    detailId: (9007199254740993n + BigInt(index)).toString()
  }));
  payload.requirement_detail_ids = payload.frozenLines.map((line) => line.detailId);
  return payload;
}

test('旧物料行的日期、小数及结论逐字保持原样', () => {
  const legacy = {
    runId: '21',
    materialLines: [{ materialId: '9007199254740993', gap: '5.00000', requiredDate: '2026-09-26' }],
    requiredDate: '2026-09-26'
  };
  assert.equal(
    summarizePurchase(legacy),
    'runId: 21 · 物料 9007199254740993 缺口 5.00000 需求日 2026-09-26 · requiredDate: 2026-09-26',
    '旧载荷不能被升级为新冻结合同'
  );
  assert.equal(summarizePurchase({ ...legacy, purchase_payload_version: 2 }), invalidPayload, '不能仅凭版本旗号升级旧物料行');
});

test('完整三来源新版合同展示冻结六、六、十二及真实单位', () => {
  const payload = purchasePayload();
  const expected = [
    '物料ID ' + payload.materialId,
    '供应商ID ' + payload.vendorId,
    '冻结来源 3 条',
    ...payload.frozenLines.map((line, index) => expectedLine(line, ['6', '6', '12'][index])),
    frozenReminder
  ].join(' · ');
  assert.equal(summarizePurchase(payload), expected, '完整新版合同不得继续显示不可验证');
});

test('新版摘要不披露完整指纹、版本元数据、原始对象或来源认证结论', () => {
  const payload = purchasePayload();
  const summary = summarizePurchase(payload);
  assert.notEqual(summary, invalidPayload, '合法新版合同应有安全摘要');
  assert.ok(summary.includes(frozenReminder), '必须提醒由ERP重新核验');
  for (const hidden of [
    payload.expectedSourceFingerprint,
    ...versionFields.map((field) => payload[field]),
    'expectedSourceFingerprint',
    'supplierTermsId'
  ]) {
    assert.ok(!summary.includes(hidden), '指纹及版本元数据不得进入摘要');
  }
  assert.doesNotMatch(summary, /[{}]|来源可信|已认证|可批准|件/, '不能显示原始对象、授予批准结论或猜测单位');
  assert.equal(canApproveRun({ status: 'STALE', stale: true }), false, '摘要成功不改变过期运行权限');
});

const validQuantities = [
  ['6.0000', '6', '6'],
  ['6.00000', '6', '6'],
  ['2.5000', '2.5', '2.5'],
  ['0.125000', '0.125', '0.125'],
  ['0.0001', '0.00010000', '0.0001'],
  ['7.9999', '7.9999000', '7.9999'],
  ['90071992547409.1234', '90071992547409.123400', '90071992547409.1234'],
  ['99999999999999.9999', '99999999999999.99990000', '99999999999999.9999'],
  ['99999999999999', '99999999999999.00000', '99999999999999']
];
for (const [covered, ordered, canonical] of validQuantities) {
  test('数量字符串规范化且不舍入：' + covered + ' 与 ' + ordered, () => {
    const payload = manySources(1);
    Object.assign(payload.frozenLines[0], { coveredDemandBase: covered, requestedOrderQty: ordered });
    assert.ok(summarizePurchase(payload).includes(expectedLine(payload.frozenLines[0], canonical)), '必须保留精确数量且允许无意义尾零');
  });
}

for (const unitId of ['1', '26', '9007199254740993', '9223372036854775807']) {
  test('相同合法单位ID原样展示：' + unitId, () => {
    const payload = manySources(1);
    payload.baseUomId = payload.orderUomId = unitId;
    payload.frozenLines[0].baseUomId = payload.frozenLines[0].orderUomId = unitId;
    const summary = summarizePurchase(payload);
    assert.ok(summary.includes('基本单位ID ' + unitId), '基本单位ID不能转换为数值或单位名称');
    assert.ok(summary.includes('订购单位ID ' + unitId), '订购单位ID不能转换为数值或单位名称');
    assert.doesNotMatch(summary, /件/, '未知单位不得写成件');
  });
}

test('正Long最大物料、供应商和来源身份完整保留', () => {
  const payload = manySources(1);
  for (const field of ['runId', 'materialId', 'vendorId', 'supplierTermsId']) payload[field] = '9223372036854775807';
  payload.requirement_detail_ids[0] = payload.frozenLines[0].detailId = '9223372036854775807';
  const summary = summarizePurchase(payload);
  for (const label of ['物料ID ', '供应商ID ', '需求来源ID ']) assert.ok(summary.includes(label + '9223372036854775807'), '雪花身份不得舍入');
});

test('版本可用非SHA的安全有限字符串且不显示在摘要中', () => {
  const payload = manySources(1);
  payload.materialVersion = 'M-冻结版本';
  payload.vendorVersion = 'V-1';
  payload.supplierTermsVersion = 'T-条款';
  payload.baseUomVersion = payload.orderUomVersion = payload.conversionVersion = 'Q-'.padEnd(256, 'q');
  assert.ok(summarizePurchase(payload).includes(frozenReminder), '真实版本不是完整哈希也应允许摘要');
});

for (const date of ['1000-01-01', '2000-02-29', '2028-02-29', '9999-12-31']) {
  test('允许真实规范业务日期：' + date, () => {
    const payload = manySources(1);
    payload.windowEnd = payload.frozenLines[0].requiredDate = date;
    assert.ok(summarizePurchase(payload).includes('需求日 ' + date), '必须按真实日历验证并允许窗口边界');
  });
}

test('来源集合按完整身份对应而不依赖对象字段或集合顺序', () => {
  const original = purchasePayload();
  const reversedFields = Object.fromEntries(Object.entries(original).reverse());
  assert.equal(summarizePurchase(reversedFields), summarizePurchase(original), '字段顺序不得改变摘要');
  reversedFields.requirement_detail_ids = [...original.requirement_detail_ids].reverse();
  assert.notEqual(summarizePurchase(reversedFields), invalidPayload, '摘要只核验来源集合，不替代后台签发与认证');
});

for (const version of ['2', 1, 3, 2.5, null, true, undefined]) {
  test('拒绝非整数二的采购版本：' + String(version), () => {
    const payload = purchasePayload();
    payload.purchase_payload_version = version;
    assert.equal(summarizePurchase(payload), invalidPayload, '只有显式整数二可以进入新版摘要');
  });
}
for (const field of purchaseFields) {
  test('拒绝缺失公共字段：' + field, () => {
    const payload = purchasePayload();
    delete payload[field];
    assert.equal(summarizePurchase(payload), invalidPayload, '公共wire必须完整');
  });
}
for (const field of ['materialLines', 'metadata', 'secret', 'approved', 'constructor', '__proto__']) {
  test('拒绝额外公共字段且不泄露：' + field, () => {
    const payload = { ...purchasePayload(), [field]: '测试隐藏内容' };
    assert.equal(summarizePurchase(payload), invalidPayload, '未知字段不得显示或提高信任结论');
  });
}

const invalidIds = [0, 26, null, {}, '0', '-1', '01', '+1', '1.0', '1e3', ' 26', '26\n', '9223372036854775808', '12345678901234567890', '件'];
for (const field of ['runId', 'materialId', 'vendorId', 'supplierTermsId', 'baseUomId', 'orderUomId']) {
  test('拒绝非正Long字符串身份：' + field, () => {
    for (const id of invalidIds) {
      const payload = purchasePayload();
      payload[field] = id;
      assert.equal(summarizePurchase(payload), invalidPayload, '身份必须是完整、正值且不溢出的十进制字符串');
    }
  });
}
for (const field of ['detailId', 'baseUomId', 'orderUomId']) {
  test('拒绝行内非正Long字符串身份：' + field, () => {
    for (const id of invalidIds) {
      const payload = purchasePayload();
      payload.frozenLines[0][field] = id;
      if (field === 'detailId') payload.requirement_detail_ids[0] = id;
      assert.equal(summarizePurchase(payload), invalidPayload, '逐来源身份必须逐项核验');
    }
  });
}

const invalidQuantities = [
  0,
  6,
  null,
  true,
  {},
  '0',
  '0.00000',
  '-1',
  '-0.01',
  '+6',
  '06',
  '6e0',
  '.5',
  '6.',
  ' 6',
  '6\n',
  '0.00001',
  '6.00001',
  '99999999999999.99999',
  '100000000000000',
  '测试隐藏数量'
];
for (const quantity of invalidQuantities) {
  test('拒绝非正或不可精确表达的数量：' + JSON.stringify(quantity), () => {
    const payload = manySources(1);
    payload.frozenLines[0].coveredDemandBase = payload.frozenLines[0].requestedOrderQty = quantity;
    assert.equal(summarizePurchase(payload), invalidPayload, '规范数量必须为正且去尾零后不超DECIMAL(18,4)');
  });
}

test('大数小数末位不同不得被浮点等值化', () => {
  const payload = manySources(1);
  payload.frozenLines[0].coveredDemandBase = '99999999999999.9998';
  payload.frozenLines[0].requestedOrderQty = '99999999999999.9999';
  assert.equal(summarizePurchase(payload), invalidPayload, '冻结覆盖量与订购量必须精确相等');
});

for (const field of versionFields) {
  test('拒绝空白、控制字符或超限版本：' + field, () => {
    for (const version of ['', ' ', 'Q-1 ', ' Q-1', 'Q-\n1', 'Q-\u007f1', 'Q-\u00851', 'q'.repeat(257), null, 1]) {
      const payload = purchasePayload();
      payload[field] = version;
      assert.equal(summarizePurchase(payload), invalidPayload, '版本只接受有限、非空且无控制字符的字符串');
    }
  });
}
for (const field of ['orderUomId', 'orderUomVersion', 'conversionVersion']) {
  test('拒绝顶层单位或版本不同：' + field, () => {
    const payload = purchasePayload();
    payload[field] = field.endsWith('Id') ? '27' : 'Q-另一个版本';
    assert.equal(summarizePurchase(payload), invalidPayload, '首包只展示同根单位同版本冻结意图');
  });
}

test('来源指纹必须独立为六十四位小写十六进制', () => {
  for (const fingerprint of ['', 'a'.repeat(63), 'a'.repeat(65), 'A'.repeat(64), 'g'.repeat(64), 'a'.repeat(64) + '\n', null, {}]) {
    const payload = purchasePayload();
    payload.expectedSourceFingerprint = fingerprint;
    assert.equal(summarizePurchase(payload), invalidPayload, '版本字符串不能替代来源指纹');
  }
});
for (const field of ['windowEnd', 'requiredDate']) {
  test('拒绝不存在或不规范的日期：' + field, () => {
    for (const date of [
      '2026-02-29',
      '2100-02-29',
      '2026-04-31',
      '2026-13-01',
      '2026-00-01',
      '2026-10-00',
      '2026-10-32',
      '2026-1-01',
      '0999-12-31',
      '0000-01-01',
      '10000-01-01',
      '2026-10-07T00:00:00Z',
      '2026-10-07\n',
      null,
      20261007
    ]) {
      const payload = purchasePayload();
      if (field === 'windowEnd') payload.windowEnd = date;
      else payload.frozenLines[0].requiredDate = date;
      assert.equal(summarizePurchase(payload), invalidPayload, '日期必须按真实日历核验且不转换时区口径');
    }
  });
}

test('拒绝来源日期晚于冻结窗口', () => {
  const payload = purchasePayload();
  payload.frozenLines[0].requiredDate = '2026-11-01';
  assert.equal(summarizePurchase(payload), invalidPayload, '不能展示窗口外需求为完整冻结意图');
});

const sourceErrors = [
  [
    '空来源',
    (payload) => {
      payload.requirement_detail_ids = [];
      payload.frozenLines = [];
    }
  ],
  [
    '来源不是数组',
    (payload) => {
      payload.requirement_detail_ids = '9007199254740993';
    }
  ],
  [
    '行不是数组',
    (payload) => {
      payload.frozenLines = {};
    }
  ],
  [
    '来源重复',
    (payload) => {
      payload.requirement_detail_ids[1] = payload.requirement_detail_ids[0];
    }
  ],
  [
    '行身份重复',
    (payload) => {
      payload.frozenLines[1].detailId = payload.frozenLines[0].detailId;
    }
  ],
  [
    '行数量缺失',
    (payload) => {
      payload.frozenLines.pop();
    }
  ],
  [
    '行身份集合不同',
    (payload) => {
      payload.frozenLines[0].detailId = '9007199254740996';
    }
  ],
  [
    '行基本单位不同',
    (payload) => {
      payload.frozenLines[0].baseUomId = '27';
    }
  ],
  [
    '行订购单位不同',
    (payload) => {
      payload.frozenLines[0].orderUomId = '27';
    }
  ],
  [
    '行未知元数据',
    (payload) => {
      payload.frozenLines[0].metadata = '测试隐藏内容';
    }
  ]
];
for (const [label, mutate] of sourceErrors) {
  test('拒绝来源结构错误：' + label, () => {
    const payload = purchasePayload();
    mutate(payload);
    assert.equal(summarizePurchase(payload), invalidPayload, '全部来源与全部冻结行必须恰好对应');
  });
}
for (const field of lineFields) {
  test('拒绝缺失冻结行字段：' + field, () => {
    const payload = purchasePayload();
    delete payload.frozenLines[0][field];
    assert.equal(summarizePurchase(payload), invalidPayload, '逐来源六字段必须完整');
  });
}
for (const row of [null, [], '测试隐藏行', 6]) {
  test('拒绝非对象冻结行：' + JSON.stringify(row), () => {
    const payload = purchasePayload();
    payload.frozenLines[0] = row;
    assert.equal(summarizePurchase(payload), invalidPayload, '非法载荷不得抛出原始行或泄露内容');
  });
}

for (const count of [20, 21, 1000]) {
  test('完整核验有界来源并准确展示省略数量：' + count, () => {
    const payload = manySources(count);
    const summary = summarizePurchase(payload);
    assert.notEqual(summary, invalidPayload, '最多一千行的合法合同应能安全摘要');
    assert.equal((summary.match(/需求来源ID /g) || []).length, Math.min(count, 20), '展示最多二十行');
    assert.ok(summary.includes('冻结来源 ' + count + ' 条'), '来源总数必须准确');
    assert.ok(summary.includes(frozenReminder), '省略展示也不能替代ERP复核');
    if (count > 20) {
      assert.ok(
        summary.includes('仅展示前 20 条，另有 ' + (count - 20) + ' 条未展示；摘要不完整，请勿仅据此批准'),
        '必须明确省略行数且不得假装完整可批准'
      );
      assert.ok(!summary.includes(payload.frozenLines[20].detailId), '未展示行不得扩展输出长度');
    } else assert.doesNotMatch(summary, /未展示|摘要不完整/, '完整二十行不得误报省略');
    assert.ok(summary.length < 7000, '摘要输出必须有界');
  });
}
for (const [label, mutate] of [
  [
    '数量不相等',
    (payload) => {
      payload.frozenLines[999].requestedOrderQty = '7';
    }
  ],
  [
    '来源重复',
    (payload) => {
      payload.frozenLines[999].detailId = payload.frozenLines[0].detailId;
    }
  ],
  [
    '单位不一致',
    (payload) => {
      payload.frozenLines[999].baseUomId = '27';
    }
  ],
  [
    '隐藏字段',
    (payload) => {
      payload.frozenLines[999].secret = '测试隐藏内容';
    }
  ],
  [
    '日期不合法',
    (payload) => {
      payload.frozenLines[999].requiredDate = '2026-02-29';
    }
  ]
]) {
  test('省略区末行仍须严格验证：' + label, () => {
    const payload = manySources(1000);
    mutate(payload);
    assert.equal(summarizePurchase(payload), invalidPayload, '不能只验证已展示的二十行');
  });
}

test('超过后台一千来源上限失败关闭', () => {
  assert.equal(summarizePurchase(manySources(1001)), invalidPayload, '超限不能静默截断后作为完整合同');
});
