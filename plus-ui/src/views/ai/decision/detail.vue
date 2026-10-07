<template>
  <div class="app-container" v-loading="loading && !detail">
    <div class="flex justify-between items-center mb-4">
      <div>
        <h2 class="m-0">工业交付风险决策</h2>
        <small>运行 ID {{ runId || '—' }}</small>
      </div>
      <div>
        <el-button @click="refresh">刷新</el-button>
        <el-button type="primary" v-hasPermi="['ai:decision:export']" @click="download">导出 Markdown</el-button>
        <el-button type="warning" v-hasPermi="['ai:decision:run']" @click="recalculate">重新计算</el-button>
      </div>
    </div>
    <template v-if="detail">
      <DecisionProgressPanel :run="detail.run" :timeline="timeline" :live-nodes="liveNodes" class="mb-4" />
      <DecisionSummaryCard :run="detail" class="mb-4" @open-evidence="evidenceOpen = true" @recalculate="recalculate" />
      <DecisionKpiCompare :baseline="result?.baselineKpis" :projected="projected" :actual="outcome?.actualAfterKpis" class="mb-4" />
      <DecisionScenarioTable
        :scenarios="result?.scenarios || []"
        :recommended-code="result?.recommendedScenarioCode"
        class="mb-4"
        @view-preview="previewScenario"
      />
      <el-card v-if="result?.scenarios?.length" class="mb-4">
        <template #header>量纲安全排名依据</template>
        <el-table :data="result.scenarios" size="small">
          <el-table-column prop="scenarioCode" label="方案" min-width="150" />
          <el-table-column label="逐物料缺口比例 / 排名资格" min-width="320">
            <template #default="scope">{{ decisionShortageRankingDisplay(scope.row) }}</template>
          </el-table-column>
        </el-table>
        <small class="block text-gray-500 mt-2">原始缺口聚合只供审计，不参与新版排名；相对归一化为零不代表无缺料。历史结论不重写。</small>
      </el-card>
      <DecisionActionApproval
        v-if="canApproveRun({ status: detail.run.status, stale: isStale })"
        :run="detail.run"
        :actions="detail.actions"
        :action-block-reasons="detail.actionBlockReasons"
        :loading="submitting"
        class="mb-4"
        @approve="approve"
        @reject="reject"
      />
      <el-button
        v-if="canApproveRun({ status: detail.run.status, stale: isStale }) && !detail.actions.length"
        type="warning"
        v-hasPermi="['ai:decision:approve']"
        class="mb-4"
        @click="rejectWithoutActions"
      >
        无可执行动作：驳回并重新分析
      </el-button>
      <DecisionActionExecutionResults :actions="detail.actions" :run-status="detail.run.status" class="mb-4" @navigate="navigateResult" />
      <DecisionRunTimeline :items="timeline" />
      <DecisionEvidenceDrawer v-model="evidenceOpen" :evidence="evidence" :status="detail.run.status" @navigate="navigateEvidence" />
      <DecisionScenarioPreview v-model="previewOpen" :scenario="preview" />
    </template>
    <el-empty v-else-if="!loading" description="暂无可查看的决策运行" />
  </div>
</template>
<script setup lang="ts" name="AiDecisionDetail">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import FileSaver from 'file-saver';
import {
  approveDecision,
  exportDecisionMarkdown,
  getDecisionRun,
  listDecisionEvidence,
  listDecisionTimeline,
  recalculateDecision,
  rejectDecision,
  type DecisionApprovalRequest,
  type DecisionEvidence,
  type DecisionOutcome,
  type DecisionResult,
  type DecisionRunDetail,
  type DecisionScenario,
  type DecisionTimelineItem
} from '@/api/ai/decision';
import { applyDecisionFlowEvent, canApproveRun, flowProgressEventId, shouldPollDecision } from './decisionViewModel';
import { decisionShortageRankingDisplay } from './decisionDisplay';
import DecisionSummaryCard from '@/components/ai/decision/DecisionSummaryCard.vue';
import DecisionKpiCompare from '@/components/ai/decision/DecisionKpiCompare.vue';
import DecisionScenarioTable from '@/components/ai/decision/DecisionScenarioTable.vue';
import DecisionEvidenceDrawer from '@/components/ai/decision/DecisionEvidenceDrawer.vue';
import DecisionActionApproval from '@/components/ai/decision/DecisionActionApproval.vue';
import DecisionActionExecutionResults from '@/components/ai/decision/DecisionActionExecutionResults.vue';
import type { DecisionErpDestination } from './decisionActionExecutionView';
import DecisionRunTimeline from '@/components/ai/decision/DecisionRunTimeline.vue';
import DecisionProgressPanel from '@/components/ai/decision/DecisionProgressPanel.vue';
import DecisionScenarioPreview from '@/components/ai/decision/DecisionScenarioPreview.vue';

const route = useRoute();
const router = useRouter();
const runId = computed(() => (typeof route.query.runId === 'string' && /^[1-9][0-9]{0,18}$/.test(route.query.runId) ? route.query.runId : ''));
const detail = ref<DecisionRunDetail | null>(null);
const evidence = ref<DecisionEvidence[]>([]);
const timeline = ref<DecisionTimelineItem[]>([]);
const evidenceOpen = ref(false);
const previewOpen = ref(false);
const preview = ref<DecisionScenario | null>(null);
const loading = ref(false);
const submitting = ref(false);
const lastSeq = ref(0);
const lastFlowSeq = ref(0);
const liveNodes = ref<Record<string, string>>({});
const evidenceLoadedFor = ref('');
let pollTimer: ReturnType<typeof setInterval> | undefined;
const result = computed<DecisionResult | null>(() => {
  try {
    return detail.value?.run.resultJson ? (JSON.parse(detail.value.run.resultJson) as DecisionResult) : null;
  } catch {
    return null;
  }
});
const outcome = computed<DecisionOutcome | null>(() => {
  try {
    return detail.value?.run.outcomeJson ? (JSON.parse(detail.value.run.outcomeJson) as DecisionOutcome) : null;
  } catch {
    return null;
  }
});
const projected = computed(() => result.value?.scenarios.find((s) => s.scenarioCode === result.value?.recommendedScenarioCode)?.kpis ?? null);
const isStale = computed(() => detail.value?.run.status === 'STALE');
async function load() {
  const id = runId.value;
  if (!id || loading.value) return;
  loading.value = true;
  try {
    const [runRes, timelineRes] = await Promise.all([getDecisionRun(id), listDecisionTimeline(id)]);
    if (runId.value !== id) return;
    detail.value = runRes.data;
    timeline.value = timelineRes.data ?? [];
    /** 每五秒状态核对时不重复下载完整证据列表。 */
    if (runRes.data.run.status !== 'CREATED' && runRes.data.run.status !== 'RUNNING' && evidenceLoadedFor.value !== id) {
      const evidenceRes = await listDecisionEvidence(id);
      if (runId.value !== id) return;
      evidence.value = evidenceRes.data ?? [];
      evidenceLoadedFor.value = id;
    }
  } catch {
    ElMessage.error('决策信息加载失败，请检查权限后重试');
  } finally {
    loading.value = false;
  }
}
function refresh() {
  evidenceLoadedFor.value = '';
  void load();
}
function onSseMessage(event: Event) {
  const payload = (event as CustomEvent).detail as { type?: string; runId?: string; seq?: number; content?: string } | undefined;
  const flowEvent = flowProgressEventId(payload, detail.value?.run.analysisFlowInstanceId);
  if (flowEvent) {
    if (flowEvent.seq && flowEvent.seq <= lastFlowSeq.value) return;
    lastFlowSeq.value = flowEvent.seq || lastFlowSeq.value;
    liveNodes.value = applyDecisionFlowEvent(liveNodes.value, flowEvent);
    if (flowEvent.status !== 'RUNNING') void load();
    return;
  }
  if (!payload?.type?.startsWith('AI_DECISION_') || String(payload.runId ?? '') !== runId.value) return;
  const seq = Number(payload.seq ?? 0);
  if (seq && seq <= lastSeq.value) return;
  lastSeq.value = seq || lastSeq.value;
  if (
    ['AI_DECISION_READY', 'AI_DECISION_APPROVAL', 'AI_DECISION_ACTION', 'AI_DECISION_OUTCOME', 'AI_DECISION_DONE', 'AI_DECISION_ERROR'].includes(
      payload.type
    )
  )
    void load();
}
watch(runId, () => {
  detail.value = null;
  evidence.value = [];
  timeline.value = [];
  liveNodes.value = {};
  evidenceLoadedFor.value = '';
  lastSeq.value = 0;
  lastFlowSeq.value = 0;
  void load();
});
onMounted(() => {
  window.addEventListener('ai-sse-message', onSseMessage);
  pollTimer = setInterval(() => {
    if (shouldPollDecision(detail.value?.run.status)) void load();
  }, 5000);
  void load();
});
onBeforeUnmount(() => {
  window.removeEventListener('ai-sse-message', onSseMessage);
  if (pollTimer) clearInterval(pollTimer);
});
function previewScenario(scenario: DecisionScenario) {
  preview.value = scenario;
  previewOpen.value = true;
}
function navigateEvidence(path: string) {
  void router.push(path);
}
function navigateResult(destination: DecisionErpDestination) {
  void router.push(destination);
}
async function approve(selection: { approvedActionIds: string[]; rejectedActionIds: string[]; comment: string }) {
  if (!detail.value || !runId.value) return;
  submitting.value = true;
  try {
    const form: DecisionApprovalRequest = {
      runVersion: detail.value.run.version,
      expectedSnapshotHash: detail.value.run.inputSnapshotHash || '',
      ...selection
    };
    await approveDecision(runId.value, form);
    ElMessage.success('已提交审批，请等待执行记录');
    await load();
  } catch {
    ElMessage.error('审批未完成；请确认权限、业务状态及快照是否过期');
  } finally {
    submitting.value = false;
  }
}
async function reject(reason: string) {
  if (!runId.value) return;
  submitting.value = true;
  try {
    await rejectDecision(runId.value, reason);
    ElMessage.success('本次决策已驳回');
    await load();
  } catch {
    ElMessage.error('驳回失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
}
async function rejectWithoutActions() {
  try {
    const { value } = await ElMessageBox.prompt('请输入至少 2 字的驳回理由', '无可执行动作', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputValidator: (value) => value?.trim().length >= 2 || '理由至少两个字'
    });
    await reject(value.trim());
  } catch {
    /** 用户取消操作。 */
  }
}
async function recalculate() {
  if (!runId.value) return;
  try {
    const response = await recalculateDecision(runId.value);
    void router.push({ path: '/ai/decision/detail', query: { runId: String(response.data.runId) } });
  } catch {
    ElMessage.error('重新分析失败，请检查访问权限');
  }
}
async function download() {
  if (!runId.value) return;
  try {
    const content = await exportDecisionMarkdown(runId.value);
    FileSaver.saveAs(new Blob([content], { type: 'text/markdown;charset=utf-8' }), `decision-${runId.value}.md`);
  } catch {
    ElMessage.error('报告导出失败');
  }
}
</script>
