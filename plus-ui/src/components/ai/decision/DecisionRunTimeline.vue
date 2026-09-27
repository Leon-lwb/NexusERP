<template>
  <el-card shadow="never">
    <template #header>决策运行时间线</template>
    <el-timeline>
      <el-timeline-item
        v-for="(item, index) in sorted"
        :key="`${index}-${item.code || item.type}`"
        :timestamp="timestamp(item.time)"
        :type="item.status === 'FAILED' ? 'danger' : 'primary'"
      >
        {{ typeLabel(item.type) }} · {{ decisionNodeLabel(item.code) }} · {{ decisionRunStatusLabel(item.status) }}
      </el-timeline-item>
    </el-timeline>
  </el-card>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { DecisionTimelineItem } from '@/api/ai/decision';
import { decisionNodeLabel, decisionRunStatusLabel } from '@/views/ai/decision/decisionViewModel';
const props = defineProps<{ items: DecisionTimelineItem[] }>();
const sorted = computed(() => [...props.items].sort((a, b) => (a.time ?? 0) - (b.time ?? 0)));
function typeLabel(type: DecisionTimelineItem['type']): string {
  return ({ RUN: '决策运行', ANALYSIS: '分析', NODE: '流程节点', ACTION: '建议动作' } as Record<string, string>)[type] || '运行记录';
}
function timestamp(time?: number | null) {
  return time == null ? '等待记录' : new Date(time).toLocaleString('zh-CN');
}
</script>
