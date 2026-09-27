<template>
  <el-card shadow="never">
    <template #header>KPI 对比（预测与实际分开）</template>
    <el-table :data="rows" stripe>
      <el-table-column prop="label" label="指标" min-width="170" />
      <el-table-column prop="before" label="当前基线" min-width="110" />
      <el-table-column prop="projected" label="推荐方案预测" min-width="120" />
      <el-table-column label="变化" min-width="140"
        ><template #default="scope">{{ scope.row.delta }}</template></el-table-column
      >
      <el-table-column prop="actual" label="执行后实测" min-width="120" />
    </el-table>
    <small class="text-gray-500">草稿创建不等于业务收益。无独立复测结果时，实测值显示为“—”。</small>
  </el-card>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { DecisionKpis } from '@/api/ai/decision';
import { formatDelta } from '@/views/ai/decision/decisionViewModel';
const props = defineProps<{ baseline?: DecisionKpis | null; projected?: DecisionKpis | null; actual?: DecisionKpis | null }>();
const metrics = [
  ['delayedWorkOrderCount', '拖期工单', '个'],
  ['totalTardinessDays', '总拖期', '天'],
  ['shortageMaterialCount', '缺料物料', '项'],
  ['totalShortageGap', '缺料缺口', '件'],
  ['maxResourceUtilization', '资源最高利用率', '%'],
  ['changedWorkOrderCount', '变更工单', '个']
] as const;
const rows = computed(() =>
  metrics.map(([key, label, unit]) => {
    const before = props.baseline?.[key];
    const projected = props.projected?.[key];
    const actual = props.actual?.[key];
    const a = before == null ? null : Number(before);
    const b = projected == null ? null : Number(projected);
    return {
      label: `${label}（${unit}）`,
      before: before ?? '—',
      projected: projected ?? '—',
      actual: actual ?? '—',
      delta: a != null && b != null && Number.isFinite(a) && Number.isFinite(b) ? formatDelta(b - a, unit) : '—'
    };
  })
);
</script>
