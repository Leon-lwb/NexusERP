<template>
  <section class="field-palette-content" aria-label="可用字段面板">
    <div class="palette-heading">
      <h3>可用字段</h3>
      <span>{{ fields.length }}</span>
    </div>
    <el-input
      :model-value="search"
      clearable
      size="small"
      placeholder="搜索字段或类型"
      aria-label="搜索可用字段"
      @update:model-value="emit('update:search', $event)"
    />
    <p class="palette-help">点击添加，或拖入明细区。</p>
    <div class="field-list">
      <button
        v-for="field in fields"
        :key="field.name"
        type="button"
        class="field-item"
        draggable="true"
        :disabled="disabled"
        :aria-label="'添加字段：' + field.label"
        @dragstart="$event.dataTransfer?.setData('report-field', field.name)"
        @click="emit('add', field)"
      >
        <span class="field-name"
          >{{ field.label }}<small>{{ field.name }}</small></span
        ><span class="field-type">{{ field.type }}</span>
      </button>
    </div>
    <el-empty v-if="!fields.length" :image-size="50" description="没有匹配字段"
      ><el-button v-if="search" link type="primary" @click="emit('update:search', '')">清空搜索</el-button></el-empty
    >
    <div class="palette-actions">
      <el-button :disabled="disabled" @click="emit('formula')">添加计算列</el-button
      ><el-button v-if="document" :disabled="disabled" @click="emit('fit')">适配纸张宽度</el-button>
    </div>
  </section>
</template>
<script setup lang="ts">
import type { DatasetField } from '@/types/report';
/** 桌面与侧栏抽屉共用同一字段面板，筛选状态由编辑页持有。 */
defineProps<{ fields: DatasetField[]; search: string; disabled: boolean; document: boolean }>();
/** 字段操作只报告当前契约字段，不计算业务值。 */
const emit = defineEmits<{ 'update:search': [string]; add: [DatasetField]; formula: []; fit: [] }>();
</script>
<style scoped>
.field-palette-content {
  min-width: 0;
}
.palette-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
h3 {
  margin: 0 0 14px;
  font-size: 15px;
}
.palette-help {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.field-list {
  max-height: 54vh;
  overflow: auto;
  margin: 12px -4px;
  padding: 4px;
}
.field-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 9px;
  margin-bottom: 6px;
  text-align: left;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font: inherit;
  cursor: grab;
}
.field-item:hover {
  border-color: var(--el-color-primary);
}
.field-name {
  min-width: 0;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.field-name small {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-family: Consolas, monospace;
}
.field-type {
  font-size: 10px;
  color: var(--el-text-color-secondary);
}
.palette-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.palette-actions :deep(.el-button) {
  margin-left: 0;
}
</style>
