<template>
  <el-dialog
    :model-value="modelValue"
    :title="`方案预览 · ${scenario?.scenarioName || scenario?.scenarioCode || ''}`"
    width="min(960px, 94vw)"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="scenario">
      <div class="flex flex-wrap items-center gap-3 mb-3">
        <el-tag :type="scenario.feasible ? 'success' : 'danger'">{{ scenario.feasible ? '可行' : '不可行' }}</el-tag>
        <span>方案编号 {{ scenario.scenarioCode }}</span>
        <span>排名 {{ scenario.rank ?? '—' }}</span>
      </div>
      <el-alert
        v-if="scenario.infeasibleReasons?.length"
        type="warning"
        :closable="false"
        class="mb-3"
        :title="`不可行原因：${scenario.infeasibleReasons.join('、')}`"
      />
      <el-descriptions :column="2" border class="mb-4">
        <el-descriptions-item label="预测拖期">{{ scenario.kpis?.totalTardinessDays ?? '—' }} 天</el-descriptions-item>
        <el-descriptions-item v-if="!hasShortageRatioContract(scenario.kpis)" label="预测缺料">{{
          displayShortageGap(scenario.kpis)
        }}</el-descriptions-item>
        <el-descriptions-item v-if="hasShortageRatioContract(scenario.kpis)" label="缺口比例（无量纲）">{{
          displayShortageRatio(scenario)
        }}</el-descriptions-item>
        <el-descriptions-item label="最高资源利用率">{{ scenario.kpis?.maxResourceUtilization ?? '—' }}%</el-descriptions-item>
        <el-descriptions-item label="动作成本">{{ scenario.kpis?.actionCost ?? '—' }}</el-descriptions-item>
      </el-descriptions>
      <small class="block text-gray-500 mb-4"
        >历史口径：缺口原值如有返回，因单位待核不能确认可合计；请核对物料主档与来源。旧记录保持原展示，不回填新版比例或结论。</small
      >
      <small v-if="hasShortageRatioContract(scenario.kpis)" class="block text-gray-500 mb-4"
        >v2 为逐物料等权原始比例；归一化零不代表无缺料，不能合计不同单位的原始缺口。</small
      >
      <div class="flex items-center justify-between mb-2">
        <strong>排程任务预览</strong>
        <span class="text-xs text-gray-500">{{ scenario.schedulePreview?.length || 0 }} 条；最多展示前 20 条</span>
      </div>
      <el-table v-if="rows.length" :data="rows" stripe max-height="330" size="small">
        <el-table-column prop="woNo" label="工单" min-width="155" show-overflow-tooltip />
        <el-table-column prop="woId" label="工单 ID" min-width="180" show-overflow-tooltip />
        <el-table-column prop="seqNum" label="工序序号" width="92" />
        <el-table-column prop="resourceName" label="工位/资源" min-width="155" show-overflow-tooltip />
        <el-table-column prop="startTime" label="计划开始" min-width="120" />
        <el-table-column prop="endTime" label="计划结束" min-width="120" />
        <el-table-column prop="processHours" label="加工工时" width="95" />
        <el-table-column prop="delayFlag" label="拖期" width="70" />
      </el-table>
      <el-empty v-else description="该方案暂无可预览的排程任务" :image-size="60" />
      <el-collapse class="mt-4">
        <el-collapse-item title="原始排程数据（只读核验）" name="audit">
          <pre class="overflow-auto max-h-60 text-xs whitespace-pre-wrap">{{ auditJson }}</pre>
        </el-collapse-item>
      </el-collapse>
      <p class="mt-3 text-xs text-gray-500">仿真预览不是已下发的排产；任何业务动作仍须单独审批。</p>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { DecisionKpis, DecisionScenario } from '@/api/ai/decision';
import { previewScheduleRows } from '@/views/ai/decision/decisionViewModel';
import { decisionShortageRankingDisplay, decisionShortageKpiDisplay, hasShortageRatioContract } from '@/views/ai/decision/decisionDisplay';
const props = defineProps<{ modelValue: boolean; scenario: DecisionScenario | null }>();
const emit = defineEmits<{ (event: 'update:modelValue', open: boolean): void }>();
const rows = computed(() => previewScheduleRows(props.scenario?.schedulePreview));
const auditJson = computed(() => JSON.stringify(props.scenario?.schedulePreview?.slice(0, 20) || [], null, 2));
/** 新合同缺 R 是未知，不按旧运行解释；保留后端明确的不可排名原因。 */
const displayShortageRatio = (scenario: DecisionScenario): string => {
  const value = scenario.infeasibleReasons?.includes('SHORTAGE_GAP_RATIO_UNRANKABLE')
    ? decisionShortageRankingDisplay(scenario)
    : decisionShortageKpiDisplay(scenario.kpis);
  return value.startsWith('不可排名') ? `未知：${value}` : value;
};
const displayShortageGap = (kpis?: DecisionKpis | null): string => {
  // 新合同不得借用历史聚合量，旧值仅在历史口径区域展示。
  if (hasShortageRatioContract(kpis)) return '—';
  const gap: unknown = kpis?.totalShortageGap;
  if (gap == null) return '—';
  if (kpis?.shortageMaterialCount === 0 && (gap === 0 || (typeof gap === 'string' && /^0+(?:\.0+)?(?:[eE][+-]?\d+)?$/.test(gap)))) {
    return '0（无缺料）';
  }
  return '—（单位待核）';
};
</script>
