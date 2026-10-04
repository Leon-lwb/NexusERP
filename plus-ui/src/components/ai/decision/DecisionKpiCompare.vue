<template>
  <el-card shadow="never">
    <template #header>KPI 对比（预测与实际分开）</template>
    <el-table :data="rows" stripe>
      <el-table-column prop="label" label="指标" min-width="170" />
      <el-table-column prop="before" label="当前基线" min-width="110" show-overflow-tooltip />
      <el-table-column prop="projected" label="推荐方案预测" min-width="120" show-overflow-tooltip />
      <el-table-column label="变化" min-width="140"
        ><template #default="scope">{{ scope.row.delta }}</template></el-table-column
      >
      <el-table-column prop="actual" label="执行后实测" min-width="120" show-overflow-tooltip />
    </el-table>
    <small class="block text-gray-500"
      >历史口径：缺口原值如有返回，因单位待核不能确认可合计；请核对物料主档与来源。旧记录保持原展示，不回填新版比例或结论。</small
    >
    <small v-if="[baseline, projected, actual].some(hasShortageRatioContract)" class="block text-gray-500"
      >v2 为逐物料等权无量纲原始比例；相对归一化零不代表无缺料，原始跨单位缺口不能合计。</small
    >
    <small class="text-gray-500">草稿创建不等于业务收益。无独立复测结果时，实测值显示为“—”。</small>
  </el-card>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { DecisionKpis } from '@/api/ai/decision';
import { formatDelta } from '@/views/ai/decision/decisionViewModel';
import { decisionShortageKpiDisplay, hasShortageRatioContract } from '@/views/ai/decision/decisionDisplay';
const props = defineProps<{ baseline?: DecisionKpis | null; projected?: DecisionKpis | null; actual?: DecisionKpis | null }>();
const metrics = [
  ['delayedWorkOrderCount', '拖期工单', '个'],
  ['totalTardinessDays', '总拖期', '天'],
  ['shortageMaterialCount', '缺料物料', '项'],
  ['totalShortageGap', '缺料缺口', ''],
  ['maxResourceUtilization', '资源最高利用率', '%'],
  ['changedWorkOrderCount', '变更工单', '个']
] as const;
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
/** 只复用现有核验展示合同；缺失或被拒绝的比例明确未知。 */
const displayShortageRatio = (kpis?: DecisionKpis | null): string => {
  const value = decisionShortageKpiDisplay(kpis);
  return value.startsWith('不可排名') ? `未知：${value}` : value;
};
const rows = computed(() => {
  const hasLegacy = [props.baseline, props.projected, props.actual].some((kpis) => kpis && !hasShortageRatioContract(kpis));
  const values = metrics
    .filter(([key]) => key !== 'totalShortageGap' || hasLegacy)
    .map(([key, label, unit]) => {
      const before = props.baseline?.[key];
      const projected = props.projected?.[key];
      const actual = props.actual?.[key];
      if (key === 'totalShortageGap') {
        return {
          label,
          before: displayShortageGap(props.baseline),
          projected: displayShortageGap(props.projected),
          actual: displayShortageGap(props.actual),
          delta: '—'
        };
      }
      const a = before == null ? null : Number(before);
      const b = projected == null ? null : Number(projected);
      return {
        label: `${label}（${unit}）`,
        before: before ?? '—',
        projected: projected ?? '—',
        actual: actual ?? '—',
        delta: a != null && b != null && Number.isFinite(a) && Number.isFinite(b) ? formatDelta(b - a, unit) : '—'
      };
    });
  if ([props.baseline, props.projected, props.actual].some(hasShortageRatioContract)) {
    const before = props.baseline?.shortageGapRatio;
    const after = props.projected?.shortageGapRatio;
    const beforeDisplay = displayShortageRatio(props.baseline);
    const projectedDisplay = displayShortageRatio(props.projected);
    // 只有 helper 确认双方比例可展示时才显示百分点差，空字符串不能被 Number 当成零。
    const comparable = beforeDisplay.endsWith('%（逐物料等权）') && projectedDisplay.endsWith('%（逐物料等权）');
    values.push({
      label: '缺口比例（无量纲）',
      before: beforeDisplay,
      projected: projectedDisplay,
      actual: displayShortageRatio(props.actual),
      delta: comparable ? `${((Number(after) - Number(before)) * 100).toFixed(2)} 百分点` : '—'
    });
  }
  return values;
});
</script>
