<template>
  <el-drawer :model-value="modelValue" title="可追溯证据" size="60%" @update:model-value="emit('update:modelValue', $event)">
    <el-empty v-if="!evidence.length" description="分析尚未归档证据，请等待完成后刷新" :image-size="70" />
    <el-collapse v-else>
      <el-collapse-item v-for="group in groups" :key="group.name" :title="`${group.name}（${group.items.length}）`" :name="group.name">
        <el-table :data="group.items" size="small">
          <el-table-column label="来源" min-width="160" show-overflow-tooltip
            ><template #default="scope">{{ scope.row.sourceName || scope.row.sourceCode || scope.row.sourceType }}</template></el-table-column
          >
          <el-table-column label="指标" min-width="160" show-overflow-tooltip
            ><template #default="scope">{{ scope.row.metricName || scope.row.metricCode }}</template></el-table-column
          >
          <el-table-column label="核验" width="90"
            ><template #default="scope"
              ><el-tag :type="scope.row.verifiable === 'Y' ? 'success' : 'warning'" size="small">{{
                scope.row.verifiable === 'Y' ? '可追溯' : '待核验'
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column prop="metricValue" label="数值/摘要" min-width="140" show-overflow-tooltip />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column prop="dataVersion" label="数据版本" min-width="160" show-overflow-tooltip />
          <el-table-column label="跳转" width="76"
            ><template #default="scope">
              <el-button link type="primary" :disabled="!safeInternalPath(scope.row.displayPath)" @click="navigate(scope.row.displayPath)"
                >打开</el-button
              >
            </template></el-table-column
          >
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </el-drawer>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { DecisionEvidence } from '@/api/ai/decision';
const props = defineProps<{ modelValue: boolean; evidence: DecisionEvidence[] }>();
const emit = defineEmits<{ (event: 'update:modelValue', open: boolean): void; (event: 'navigate', path: string): void }>();
const groups = computed(() =>
  Object.entries(
    props.evidence.reduce<Record<string, DecisionEvidence[]>>((result, item) => {
      const key = `${item.sourceDomain}/${item.sourceType}`;
      (result[key] ??= []).push(item);
      return result;
    }, {})
  ).map(([name, items]) => ({ name, items }))
);
function safeInternalPath(path?: string) {
  return !!path && /^\/(erp|mes|ai)\/[A-Za-z0-9_/?&=%.-]+$/.test(path) && !path.includes('//');
}
function navigate(path?: string) {
  if (safeInternalPath(path)) emit('navigate', path!);
}
</script>
