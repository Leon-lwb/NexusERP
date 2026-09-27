import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const files = [
  ['src/views/erp/prd/schedule/index.vue', 'SCHEDULE_PLAN'],
  ['src/views/erp/prd/shortage/index.vue', 'SHORTAGE_HORIZON'],
  ['src/views/erp/prd/prdwork/index.vue', 'WORK_ORDER'],
  ['src/views/erp/so/orderheader/index.vue', 'SALES_ORDER']
];
for (const [file, kind] of files) {
  const source = readFileSync(file, 'utf8');
  assert.match(source, /runDeliveryRisk\(/, `${file} must call the decision service`);
  assert.ok(source.includes(`bizType: '${kind}'`), `${file} must pass exact business type`);
  assert.match(source, /ai:decision:run/, `${file} must permission-gate its entry`);
  assert.match(source, /\/ai\/decision\/detail/, `${file} must navigate to the result`);
}
const summaryCard = readFileSync('src/components/ai/decision/DecisionSummaryCard.vue', 'utf8');
assert.match(summaryCard, /decisionSummaryText\(/, 'decision summary must render terminal status correctly');
assert.match(summaryCard, /run\.run\.status\s*===\s*'FAILED'/, 'failed runs must be shown visibly');
const detail = readFileSync('src/views/ai/decision/detail.vue', 'utf8');
assert.match(
  detail,
  /shouldPollDecision\(detail\.value\?\.run\.status\)/,
  'active runs must poll for missed terminal events even when SSE stays connected'
);
assert.doesNotMatch(detail, /!isSseConnected\(\)/, 'connection state must not suppress status reconciliation');
assert.match(detail, /DecisionProgressPanel/, 'decision detail must display trustworthy stage progress');
const progressPanel = readFileSync('src/components/ai/decision/DecisionProgressPanel.vue', 'utf8');
assert.match(
  progressPanel,
  /isDecisionAnalysisRunning\(props\.run\.status\)/,
  'only the analysis state should animate and advance the analysis clock'
);
assert.doesNotMatch(progressPanel, /shouldPollDecision\(props\.run\.status\)/, 'polling state must not control analysis progress');
assert.doesNotMatch(progressPanel, /'执行业务动作'/, 'action handling is not a stage of analysis');
const scenarioPreview = readFileSync('src/components/ai/decision/DecisionScenarioPreview.vue', 'utf8');
assert.match(progressPanel, /模型摘要／策略校验/, 'unknown model stage must not be mislabeled as guard before event arrives');
assert.match(progressPanel, /aria-valuetext/, 'indeterminate progress must be accessible without inventing a percentage');
assert.doesNotMatch(progressPanel, /请勿关闭页面/, 'navigating away must not be presented as cancelling the server-side run');

assert.match(scenarioPreview, /prop="woId"/, 'schedule preview must retain exact work order IDs');

assert.match(detail, /v-loading=\"loading && !detail\"/, 'background polling must not block the visible progress panel');
assert.match(detail, /DecisionScenarioPreview/, 'decision preview must use a structured component');
const scenarios = readFileSync('src/components/ai/decision/DecisionScenarioTable.vue', 'utf8');
const evidence = readFileSync('src/components/ai/decision/DecisionEvidenceDrawer.vue', 'utf8');
assert.match(scenarios, /infeasibleReasons/, 'scenario comparison must explain infeasibility');
assert.match(evidence, /metricName/, 'evidence must show the human-readable metric name');
const actions = readFileSync('src/components/ai/decision/DecisionActionApproval.vue', 'utf8');
const timeline = readFileSync('src/components/ai/decision/DecisionRunTimeline.vue', 'utf8');
assert.match(actions, /el-descriptions/, 'actions should expose approved fields in a structured summary');
assert.match(timeline, /decisionNodeLabel/, 'timeline must show human-readable node names');
assert.match(summaryCard, /result\?\.businessRef\?\.code/, 'work order code must be displayed before generic business type');
const listView = readFileSync('src/views/ai/decision/index.vue', 'utf8');
assert.match(listView, /decisionRunStatusLabel\(/, 'run list should display status in a human language');
assert.match(listView, /scope\.row\.status === 'SUCCEEDED'\s*\?\s*'success'/, 'completed runs should be clearly marked as successful');

assert.match(listView, /scope\.row\.bizCode\s*\|\|\s*scope\.row\.bizId/, 'run list should show exact ID when business code is absent');

assert.doesNotMatch(detail, /<pre[^>]*>\{\{\s*JSON\.stringify/, 'raw JSON must not be the primary schedule preview');

process.stdout.write('Decision ERP entry checks: 4 passed\n');
