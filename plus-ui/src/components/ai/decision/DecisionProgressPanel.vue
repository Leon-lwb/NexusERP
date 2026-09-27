<template>
  <el-card shadow="never" class="decision-progress">
    <template #header>
      <div class="flex flex-wrap items-center justify-between gap-2">
        <strong>分析进度</strong>
        <div class="flex items-center gap-3 text-sm text-gray-500">
          <el-tag :type="failed ? 'danger' : running ? 'primary' : 'success'" size="small">{{ statusText }}</el-tag>
          <span>已用 {{ elapsedText }}</span>
        </div>
      </div>
    </template>
    <el-progress
      :percentage="100"
      :indeterminate="running"
      :duration="2"
      :show-text="false"
      :aria-valuetext="running ? '正在分析，剩余时间不可预估' : statusText"
      :status="failed ? 'exception' : running ? undefined : 'success'"
      :stroke-width="8"
    />
    <p class="my-3 text-sm text-gray-600">
      {{
        running ? `当前阶段：${activeLabel}。剩余时间无法精确预测；可稍后从运行记录返回查看。` : '分析阶段已结束，以下仅显示服务端确认的节点状态。'
      }}
    </p>
    <div class="flex flex-wrap items-center gap-2" aria-live="polite">
      <el-tag v-for="node in visibleNodes" :key="node" :type="stageTag(statuses[node])" effect="plain" size="small">
        {{ decisionNodeLabel(node) }} · {{ stageText(statuses[node]) }}
      </el-tag>
    </div>
    <div class="mt-3 text-xs text-gray-500">
      已记录 {{ completedCount }} 个结束节点；网络中断时每 5 秒向服务器核对状态。
      <span v-if="run.summaryMode === 'TEMPLATE_FALLBACK'">模型摘要使用服务端模板降级，风险数值仍来自业务规则。</span>
    </div>
  </el-card>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import type { DecisionRunSummary, DecisionTimelineItem } from '@/api/ai/decision';
import {
  decisionAnalysisElapsedSeconds,
  decisionNodeLabel,
  decisionRunStatusLabel,
  formatDecisionElapsed,
  isDecisionAnalysisRunning
} from '@/views/ai/decision/decisionViewModel';
const props = defineProps<{ run: DecisionRunSummary; timeline: DecisionTimelineItem[]; liveNodes: Record<string, string> }>();
const stageOrder = ['context', 'baseline', 'shortage', 'aps', 'merge', 'rank', 'rag', 'llm', 'guard', 'actionProposal'];
const clock = ref(Date.now());
let timer: ReturnType<typeof setInterval> | undefined;
onMounted(() => {
  timer = setInterval(() => {
    clock.value = Date.now();
  }, 1000);
});
onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
const running = computed(() => isDecisionAnalysisRunning(props.run.status));
const failed = computed(() => props.run.status === 'FAILED');
const statusText = computed(() => decisionRunStatusLabel(props.run.status));
const startedAt = computed(
  () => props.timeline.find((item) => item.type === 'RUN' && item.status === 'RUNNING')?.time ?? Date.parse(props.run.createTime || '')
);
const elapsedText = computed(() => {
  const seconds = decisionAnalysisElapsedSeconds(props.run.status, startedAt.value, Date.parse(props.run.analysisFinishedAt || ''), clock.value);
  return seconds === null ? '—' : formatDecisionElapsed(seconds);
});
const statuses = computed(() => {
  const recorded: Record<string, string> = {};
  for (const item of props.timeline) {
    if (item.type === 'NODE' && item.code && stageOrder.includes(item.code)) recorded[item.code] = item.status;
  }
  for (const [code, status] of Object.entries(props.liveNodes)) {
    if (stageOrder.includes(code) && !(recorded[code] === 'SUCCEEDED' || recorded[code] === 'FAILED')) recorded[code] = status;
  }
  return recorded;
});
const visibleNodes = computed(() => stageOrder.filter((code) => (code !== 'rag' && code !== 'llm') || !!statuses.value[code]));
const completedCount = computed(() => Object.values(statuses.value).filter((status) => status === 'SUCCEEDED' || status === 'FAILED').length);
const activeLabel = computed(() => {
  const node = visibleNodes.value.find((code) => statuses.value[code] === 'RUNNING') ?? visibleNodes.value.find((code) => !statuses.value[code]);
  return node === 'guard' && !statuses.value.llm ? '模型摘要／策略校验' : node ? decisionNodeLabel(node) : '写入决策结果';
});
function stageTag(status?: string): 'success' | 'warning' | 'primary' | 'info' {
  return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' ? 'warning' : status === 'RUNNING' ? 'primary' : 'info';
}
function stageText(status?: string): string {
  return status === 'SUCCEEDED' ? '完成' : status === 'FAILED' ? '已降级或失败' : status === 'RUNNING' ? '进行中' : '等待中';
}
</script>
