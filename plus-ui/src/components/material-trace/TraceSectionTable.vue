<template>
  <el-table :data="page.rows" border size="small" max-height="520">
    <el-table-column v-for="column in columns" :key="column" :label="column" min-width="150" show-overflow-tooltip>
      <template #default="{ row }">{{ traceCell(row[column]) }}</template>
    </el-table-column>
  </el-table>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { TraceSectionPage } from '@/api/erp/wm/trace/types';
import { traceCell } from './tracePaging';
const props = defineProps<{ page: TraceSectionPage }>();
/** 只显示服务端已授权返回的列，不补造费用、仓库或身份映射。 */
const columns = computed(() => [...new Set(props.page.rows.flatMap((row) => Object.keys(row)))]);
</script>
