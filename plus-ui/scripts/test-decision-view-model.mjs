import assert from 'node:assert/strict';
import {
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
} from '../node_modules/.tmp/decision-view-model.mjs';

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
process.stdout.write('Decision view model checks passed\n');
