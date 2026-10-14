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
/** 原生报表页面组件，沿用权限、精确值和固定契约行为。 */

import type { ReportColumn, TypedRow } from '@/types/report';
import { formatTyped } from '@/views/report/model';
withDefaults(
  defineProps<{
    /** 保存当前 `columns` 值。 */
    columns: ReportColumn[];
    /** 保存待处理业务行。 */
    rows: TypedRow[];
    /** 保存当前 `sortable` 值。 */
    sortable?: boolean;
    /** 保存当前 `maxHeight` 值。 */
    maxHeight?: number;
  }>(),
  {
    sortable: false,
    maxHeight: 560
  }
);
/**
 * 保存当前页面或测试的emit状态。
 */
const emit = defineEmits<{
  /** 保存当前 `sort` 值。 */
  sort: [field: string | undefined, descending: boolean];
}>();
/** 处理当前报表的 `alignment` 操作。 */
function alignment(column: ReportColumn): 'left' | 'center' | 'right' {
  return column.style.align.toLowerCase() as 'left' | 'center' | 'right';
}
/** 处理当前报表的 `sortChange` 操作。 */
function sortChange(event: {
  /** 保存当前 `prop` 值。 */
  prop?: string;
  /** 保存当前 `order` 值。 */
  order?: string | null;
}) {
  emit('sort', event.order ? event.prop : undefined, event.order === 'descending');
}
</script>
