<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import VueJsonPretty from 'vue-json-pretty';
import 'vue-json-pretty/lib/styles.css';

/**
 * JSON 数据查看器（对齐 n8n JSON/Table 双视图）
 * <p>JSON 模式：VueJsonPretty 树状；表格模式：
 * 数组 → 每行一个元素（键并集为列，最多 maxCols 列）；
 * 对象 → 键值两列。复杂单元格 JSON 截断 + tooltip 全文，支持关键字过滤。</p>
 */

const props = withDefaults(
  defineProps<{
    /** 数据（任意 JSON 值） */
    data: unknown;
    /** 表格模式最大列数（数组元素键并集） */
    maxCols?: number;
    /** 单元格值截断长度 */
    cellMax?: number;
  }>(),
  { maxCols: 8, cellMax: 60 }
);

const viewMode = ref<'json' | 'table'>('json');
const filterText = ref('');

const isArray = computed(() => Array.isArray(props.data));
const isObject = computed(() => props.data !== null && typeof props.data === 'object' && !Array.isArray(props.data));

interface TableRow {
  _key: string;
  [k: string]: unknown;
}

/** 表格行（数组 → 元素键并集；对象 → 键值行） */
const tableRows = computed<TableRow[]>(() => {
  const d = props.data;
  if (Array.isArray(d)) {
    return d.map((item, idx) => ({
      _key: `[${idx}]`,
      ...(item !== null && typeof item === 'object' ? (item as Record<string, unknown>) : { value: item })
    }));
  }
  if (d !== null && typeof d === 'object') {
    return Object.entries(d as Record<string, unknown>).map(([k, v]) => ({ _key: k, value: v }));
  }
  return [{ _key: 'value', value: d }];
});

/** 表格列（跳过 _key，取键并集前 maxCols） */
const tableColumns = computed(() => {
  const keys = new Set<string>();
  for (const row of tableRows.value) {
    for (const k of Object.keys(row)) {
      if (k !== '_key') keys.add(k);
    }
  }
  return [...keys].slice(0, props.maxCols);
});

/** 关键字过滤后的行 */
const filteredRows = computed(() => {
  const f = filterText.value.trim().toLowerCase();
  if (!f) return tableRows.value;
  return tableRows.value.filter((row) => {
    if (row._key.toLowerCase().includes(f)) return true;
    return tableColumns.value.some((c) =>
      String(row[c] ?? '')
        .toLowerCase()
        .includes(f)
    );
  });
});

/** 单元格显示文本（对象/数组 → JSON 截断） */
function cellText(v: unknown): string {
  if (v === null || v === undefined) return '-';
  if (typeof v === 'object') {
    try {
      return JSON.stringify(v);
    } catch {
      return String(v);
    }
  }
  return String(v);
}

function cellShort(v: unknown): string {
  const t = cellText(v);
  return t.length > props.cellMax ? t.slice(0, props.cellMax) + '…' : t;
}

const isComplex = (v: unknown) => v !== null && typeof v === 'object';

onBeforeUnmount(() => {
  /* 无清理 */
});
</script>

<template>
  <div class="json-viewer">
    <div class="json-viewer__toolbar">
      <el-radio-group v-model="viewMode" size="small">
        <el-radio-button value="json">JSON</el-radio-button>
        <el-radio-button value="table">表格</el-radio-button>
      </el-radio-group>
      <el-input v-if="viewMode === 'table'" v-model="filterText" size="small" placeholder="过滤字段/值..." clearable class="json-viewer__filter" />
    </div>

    <!-- JSON 模式：树状 -->
    <div v-if="viewMode === 'json'" class="json-viewer__json">
      <VueJsonPretty :data="data" :show-line="false" :deep="4" />
    </div>

    <!-- 表格模式 -->
    <div v-else-if="filteredRows.length" class="json-viewer__table-wrap">
      <el-table :data="filteredRows" border size="small" max-height="320">
        <el-table-column label="" prop="_key" width="90" fixed>
          <template #default="{ row }">
            <span class="json-viewer__key">{{ row._key }}</span>
          </template>
        </el-table-column>
        <el-table-column v-for="col in tableColumns" :key="col" :label="col" min-width="140">
          <template #default="{ row }">
            <el-tooltip v-if="isComplex(row[col])" :content="cellText(row[col])" placement="top" :show-after="200">
              <span class="json-viewer__cell">{{ cellShort(row[col]) }}</span>
            </el-tooltip>
            <span v-else class="json-viewer__cell">{{ cellShort(row[col]) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-empty v-else description="无匹配数据" :image-size="50" />
  </div>
</template>

<style scoped>
.json-viewer {
  width: 100%;
}

.json-viewer__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.json-viewer__filter {
  width: 180px;
}

.json-viewer__json {
  max-height: 320px;
  overflow-y: auto;
  padding: 4px;
}

.json-viewer__table-wrap {
  max-height: 320px;
  overflow-y: auto;
}

.json-viewer__key {
  font-weight: 600;
  color: #409eff;
  font-size: 12px;
}

.json-viewer__cell {
  font-size: 12px;
  color: #303133;
  word-break: break-all;
}
</style>
