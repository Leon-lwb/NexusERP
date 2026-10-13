<template>
  <el-table :data="rows" border stripe :max-height="maxHeight" @sort-change="sortChange">
    <el-table-column
      v-for="column in columns"
      :key="column.field"
      :prop="column.field"
      :label="column.label"
      :min-width="Math.max(100, column.width)"
      :align="alignment(column)"
      :sortable="sortable && !column.expression ? 'custom' : false"
    >
      <template #default="scope"
        ><span>{{ formatTyped(scope.row[column.field], column.scale) }}</span></template
      >
    </el-table-column>
    <template #empty><el-empty description="当前筛选范围没有数据" :image-size="64" /></template>
  </el-table>
</template>
<script setup lang="ts">
import type { ReportColumn, TypedRow } from '@/types/report';
import { formatTyped } from '@/views/report/model';
withDefaults(defineProps<{ columns: ReportColumn[]; rows: TypedRow[]; sortable?: boolean; maxHeight?: number }>(), {
  sortable: false,
  maxHeight: 560
});
const emit = defineEmits<{ sort: [field: string | undefined, descending: boolean] }>();
function alignment(column: ReportColumn): 'left' | 'center' | 'right' {
  return column.style.align.toLowerCase() as 'left' | 'center' | 'right';
}
function sortChange(event: { prop?: string; order?: string | null }) {
  emit('sort', event.order ? event.prop : undefined, event.order === 'descending');
}
</script>
