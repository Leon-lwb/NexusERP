<template>
  <el-card class="decision-summary" shadow="never">
    <template #header>
      <div class="flex justify-between items-center">
        <strong>交付风险摘要</strong>
        <div>
          <el-button link type="primary" @click="emit('open-evidence')">查看证据</el-button>
          <el-button link type="warning" @click="emit('recalculate')">重新分析</el-button>
        </div>
      </div>
    </template>
    <div class="flex gap-4 items-center mb-3">
      <span>{{ result?.businessRef?.code || run.run.bizCode || run.run.bizType }} · {{ String(run.run.bizId ?? '全局时段') }}</span>
      <el-tag :type="riskTagType(run.run.riskLevel)">{{ decisionRiskLabel(run.run.riskLevel) }}</el-tag>
      <strong>风险分数 {{ run.run.riskScore ?? '—' }}</strong>
      <span>置信度 {{ run.run.confidenceLevel || '—' }}</span>
    </div>
    <el-alert v-if="run.run.status === 'FAILED'" title="本次分析失败，可点击重新分析" type="error" :closable="false" class="mb-3" />
    <p>{{ decisionSummaryText(run.run.status, result?.narrative?.executiveSummary) }}</p>
    <el-alert v-for="warning in result?.warnings || []" :key="warning" :title="warning" type="warning" :closable="false" class="mb-1" />
  </el-card>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { DecisionRunDetail, DecisionResult } from '@/api/ai/decision';
import { decisionRiskLabel, decisionSummaryText, riskTagType } from '@/views/ai/decision/decisionViewModel';
const props = defineProps<{ run: DecisionRunDetail }>();
const emit = defineEmits<{ (event: 'open-evidence'): void; (event: 'recalculate'): void }>();
const result = computed<DecisionResult | null>(() => {
  try {
    return props.run.run.resultJson ? (JSON.parse(props.run.run.resultJson) as DecisionResult) : null;
  } catch {
    return null;
  }
});
</script>
