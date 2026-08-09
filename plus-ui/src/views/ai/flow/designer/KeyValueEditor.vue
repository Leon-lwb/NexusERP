<script setup lang="ts">
import { ref, watch } from 'vue';
import { Delete, Plus } from '@element-plus/icons-vue';
import { useVariablePicker, buildVariableCandidates, type VariableCandidate } from './useVariablePicker';
import type { FlowCanvasNode } from './useFlowStore';

/**
 * 键值对行编辑器（对齐 n8n KeyValue 参数输入）
 * <p>替代裸 JSON textarea：行编辑（键 + 值 + 删除 + 添加），值支持 {{ }} 变量选择器。
 * 用于 set.variables / code.expressions / http.headers / sms.params 等字段</p>
 */

const props = defineProps<{
  /** 当前值（对象 / JSON 字符串 / undefined） */
  modelValue: unknown;
  /** 键占位符 */
  keyPlaceholder?: string;
  /** 值占位符 */
  valuePlaceholder?: string;
  /** 值提示（变量引用说明） */
  valueTip?: string;
  /** 上游节点（变量候选） */
  upstreamNodes?: FlowCanvasNode[];
  /** 节点运行时输出缓存（变量候选，真实数据优先） */
  nodeOutputs?: Record<string, unknown> | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: unknown): void;
}>();

const picker = useVariablePicker();

interface Row {
  key: string;
  value: string;
}

const rows = ref<Row[]>([]);

/** 解析 modelValue 为行数组 */
function parseToRows(): Row[] {
  let obj: Record<string, unknown> = {};
  const raw = props.modelValue;
  if (raw && typeof raw === 'object') {
    obj = raw as Record<string, unknown>;
  } else if (typeof raw === 'string' && raw.trim()) {
    try {
      obj = JSON.parse(raw);
    } catch {
      obj = {};
    }
  }
  const list = Object.entries(obj).map(([key, v]) => ({ key, value: String(v ?? '') }));
  if (!list.length) list.push({ key: '', value: '' });
  return list;
}

watch(
  () => props.modelValue,
  () => {
    rows.value = parseToRows();
  },
  { immediate: true }
);

/** 行变更 → 组装对象 emit（剔除空键行；保留空值行） */
function emitRows() {
  const obj: Record<string, string> = {};
  for (const r of rows.value) {
    const key = r.key.trim();
    if (key) obj[key] = r.value;
  }
  emit('update:modelValue', Object.keys(obj).length ? obj : undefined);
}

function updateRow(i: number, field: 'key' | 'value', v: string) {
  rows.value[i] = { ...rows.value[i], [field]: v };
  emitRows();
}

function addRow() {
  rows.value = [...rows.value, { key: '', value: '' }];
}

function removeRow(i: number) {
  rows.value = rows.value.filter((_, idx) => idx !== i);
  emitRows();
}

/** 值输入 {{ 检测 → 打开变量选择器（输入时焦点必在目标 input 上） */
function onValueInput(i: number, text: string) {
  updateRow(i, 'value', text);
  const el = document.activeElement as HTMLInputElement | null;
  if (el && el.tagName === 'INPUT') {
    picker.open(el, buildVariableCandidates(props.upstreamNodes ?? [], props.nodeOutputs ?? null), (t) => updateRow(i, 'value', t));
  }
}
</script>

<template>
  <div class="kv-editor">
    <div v-for="(row, i) in rows" :key="i" class="kv-editor__row">
      <el-input
        :model-value="row.key"
        class="kv-editor__key"
        :placeholder="keyPlaceholder || '字段名'"
        size="small"
        @update:model-value="(v: string) => updateRow(i, 'key', v)"
      />
      <el-input
        :model-value="row.value"
        class="kv-editor__value-input"
        :placeholder="valuePlaceholder || '值（支持 {{input.xxx}} 变量引用）'"
        size="small"
        @update:model-value="(v: string) => onValueInput(i, v)"
      />
      <el-button link type="danger" :icon="Delete" size="small" @click="removeRow(i)" />
    </div>
    <div class="kv-editor__footer">
      <el-button type="primary" plain size="small" :icon="Plus" @click="addRow">添加字段</el-button>
      <span v-if="valueTip" class="kv-editor__tip">{{ valueTip }}</span>
    </div>

    <!-- 变量选择器弹层（fixed，视口翻转） -->
    <div
      v-if="picker.visible.value"
      class="node-param-form__selector"
      :style="{ top: picker.position.value.top + 'px', left: picker.position.value.left + 'px' }"
    >
      <div class="node-param-form__selector-search">
        <el-input v-model="picker.filter.value" placeholder="过滤变量..." size="small" />
      </div>
      <div class="node-param-form__selector-list">
        <div
          v-for="c in picker.filteredCandidates.value"
          :key="c.insert"
          class="node-param-form__selector-item"
          @mousedown.prevent="picker.insert(c as VariableCandidate)"
        >
          <span class="node-param-form__selector-name">{{ c.label }}</span>
          <code class="node-param-form__selector-path">{{ c.insert }}</code>
        </div>
        <div v-if="!picker.filteredCandidates.value.length" class="node-param-form__selector-empty">无匹配变量</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kv-editor {
  width: 100%;
}

.kv-editor__row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.kv-editor__key {
  width: 88px;
  flex-shrink: 0;
}

.kv-editor__value-input {
  flex: 1;
}

.kv-editor__footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.kv-editor__tip {
  font-size: 11px;
  color: #909399;
}

/* 选择器弹层样式（与 NodeParameterForm 一致） */
.node-param-form__selector {
  position: fixed;
  z-index: 3000;
  width: 300px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  padding: 6px;
}

.node-param-form__selector-search {
  margin-bottom: 4px;
}

.node-param-form__selector-list {
  max-height: 220px;
  overflow-y: auto;
}

.node-param-form__selector-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}

.node-param-form__selector-item:hover {
  background: #f0f7ff;
}

.node-param-form__selector-name {
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-param-form__selector-path {
  color: #409eff;
  font-size: 11px;
  white-space: nowrap;
}

.node-param-form__selector-empty {
  padding: 12px;
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
}
</style>
