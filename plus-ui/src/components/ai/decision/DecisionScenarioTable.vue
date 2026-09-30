<template>
  <el-card shadow="never">
    <template #header>方案对比</template>
    <el-table :data="sorted" stripe>
      <el-table-column prop="rank" label="排名" width="70" />
      <el-table-column prop="scenarioCode" label="方案编号" min-width="150" />
      <el-table-column prop="scenarioName" label="方案名称" min-width="150" />
      <el-table-column label="状态" min-width="120"
        ><template #default="scope">
          <el-tag :type="scope.row.feasible ? 'success' : 'danger'">{{ scope.row.feasible ? '可行' : '不可行' }}</el-tag>
          <el-tag v-if="scope.row.scenarioCode === recommendedCode" type="primary" class="ml-1">推荐</el-tag>
        </template></el-table-column
      >
      <el-table-column label="拖期天数" min-width="105"
        ><template #default="scope">{{ scope.row.kpis?.totalTardinessDays ?? '—' }}</template></el-table-column
      >
      <el-table-column label="缺料缺口" min-width="105" show-overflow-tooltip
        ><template #default="scope">{{ displayShortageGap(scope.row.kpis) }}</template></el-table-column
      >
      <el-table-column label="不可行原因" min-width="155" show-overflow-tooltip
        ><template #default="scope">{{ scope.row.infeasibleReasons?.join('、') || '—' }}</template></el-table-column
      >
      <el-table-column label="证据" min-width="90"
        ><template #default="scope">{{ scope.row.evidenceKeys?.length ?? 0 }} 条</template></el-table-column
      >
      <el-table-column label="预览" width="90"
        ><template #default="scope">
          <el-button link type="primary" @click="emit('view-preview', scope.row)">查看</el-button>
        </template></el-table-column
      >
    </el-table>
    <small class="block text-gray-500">缺口原值如有返回，因单位待核不能确认可合计；请核对物料主档与来源。</small>
  </el-card>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { DecisionKpis, DecisionScenario } from '@/api/ai/decision';
import { sortScenarios } from '@/views/ai/decision/decisionViewModel';
const props = defineProps<{ scenarios: DecisionScenario[]; recommendedCode?: string }>();
const emit = defineEmits<{ (event: 'view-preview', scenario: DecisionScenario): void }>();
const sorted = computed(() => sortScenarios(props.scenarios));
const displayShortageGap = (kpis?: DecisionKpis | null): string => {
  const gap: unknown = kpis?.totalShortageGap;
  if (gap == null) return '—';
  if (kpis?.shortageMaterialCount === 0 && (gap === 0 || (typeof gap === 'string' && /^0+(?:\.0+)?(?:[eE][+-]?\d+)?$/.test(gap)))) {
    return '0（无缺料）';
  }
  return '—（单位待核）';
};
</script>
