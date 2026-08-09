<script setup lang="ts">
import { computed, ref } from 'vue';
import type { NodeParamSchema } from '@/api/ai/flow';
import type { FlowCanvasNode } from './useFlowStore';

/**
 * 通用参数表单：按 NodeParamSchema.type 渲染（对齐 n8n ParameterInputFull）
 * <p>支持类型：string / number / boolean / options / textarea / json
 * textarea 输入 {{ 时弹出变量选择器（input.* + 上游节点输出字段）</p>
 */

const props = defineProps<{
  /** 参数 schema */
  schema: NodeParamSchema;
  /** 当前值 */
  modelValue: unknown;
  /** 节点类型（用于变量提示） */
  nodeType?: string;
  /** 上游节点列表（变量选择器候选） */
  upstreamNodes?: FlowCanvasNode[];
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: unknown): void;
}>();

const value = computed({
  get: () => props.modelValue,
  set: (v: unknown) => emit('update:modelValue', v)
});

/** 变量选择器状态 */
const selectorVisible = ref(false);
const selectorPosition = ref({ top: 0, left: 0 });
const selectorFilter = ref('');
const activeTextarea = ref<HTMLTextAreaElement | null>(null);
const textareaRef = ref();

/** number 类型的输入处理 */
function onNumberInput(e: Event) {
  const el = e.target as HTMLInputElement;
  value.value = el.value === '' ? undefined : Number(el.value);
}

/** json 文本 → 对象（宽松处理） */
function onJsonInput(text: string) {
  if (!text || !text.trim()) {
    value.value = undefined;
    return;
  }
  try {
    value.value = JSON.parse(text);
  } catch {
    // 保留字符串，保存时后端校验会报错
    value.value = text;
  }
}

/** 变量候选列表（{{ 前缀过滤） */
const variableCandidates = computed(() => {
  const filter = selectorFilter.value.trim();
  const candidates: Array<{ label: string; insert: string }> = [];
  // input.* 运行入参
  if (!filter || 'input'.includes(filter)) {
    candidates.push({ label: '运行入参 input', insert: '{{input.xxx}}' });
    candidates.push({ label: 'input.name', insert: '{{input.name}}' });
    candidates.push({ label: 'input.amount', insert: '{{input.amount}}' });
    candidates.push({ label: 'input.items', insert: '{{input.items}}' });
  }
  // 上游节点输出（按类型静态枚举常见字段）
  for (const n of props.upstreamNodes ?? []) {
    const prefix = `nodes.${n.id}.output`;
    const type = n.data.type;
    const label = n.data.label || n.id;
    const commonFields: Array<[string, string]> = [];
    if (type === 'llm' || type === 'agent') {
      commonFields.push(['content', `${prefix}.content`], ['tokenUsage', `${prefix}.tokenUsage`]);
    } else if (type === 'rag') {
      commonFields.push(['chunks', `${prefix}.chunks`], ['count', `${prefix}.count`], ['augmentedPrompt', `${prefix}.augmentedPrompt`]);
    } else if (type === 'sql') {
      commonFields.push(['rows', `${prefix}.rows`], ['count', `${prefix}.count`], ['columns', `${prefix}.columns`]);
    } else if (type === 'set' || type === 'code') {
      // 动态字段：config.variables / expressions 的键
      const vars = (n.data.config as Record<string, unknown>) ?? {};
      const map = (vars.variables ?? vars.expressions ?? {}) as Record<string, unknown>;
      Object.keys(map).forEach((k) => commonFields.push([k, `${prefix}.${k}`]));
    } else if (type === 'if') {
      commonFields.push(['result', `${prefix}.result`]);
    } else if (type === 'merge') {
      commonFields.push(['inputs', `${prefix}.inputs`], ['count', `${prefix}.count`]);
    } else if (type === 'split') {
      commonFields.push(['batch', `${prefix}.batch`], ['index', `${prefix}.index`], ['done', `${prefix}.done`], ['total', `${prefix}.total`]);
    }
    for (const [field, path] of commonFields) {
      if (!filter || `${label}.${field}`.toLowerCase().includes(filter.toLowerCase()) || path.includes(filter)) {
        candidates.push({ label: `${label}.${field}`, insert: `{{${path}}}` });
      }
    }
  }
  return candidates;
});

/** textarea 输入监听：检测 "{{" 触发选择器（el-input 的 @input 回传字符串值） */
function onTextInput(text: string) {
  value.value = text;
  // 通过组件实例查找 textarea DOM（el-input 内部）
  const textareaEl = textareaRef.value?.$el?.querySelector?.('textarea') as HTMLTextAreaElement | null;
  if (!textareaEl) return;
  const caret = textareaEl.selectionStart ?? text.length;
  // 最近 10 个字符内包含 {{ 且未闭合
  const before = text.slice(Math.max(0, caret - 10), caret);
  const openIdx = before.lastIndexOf('{{');
  const closeIdx = before.lastIndexOf('}}');
  if (openIdx >= 0 && (closeIdx === -1 || closeIdx < openIdx)) {
    // 打开选择器
    selectorFilter.value = before.slice(openIdx + 2);
    selectorPosition.value = { top: textareaEl.offsetTop + textareaEl.offsetHeight, left: textareaEl.offsetLeft };
    activeTextarea.value = textareaEl;
    selectorVisible.value = true;
  } else {
    selectorVisible.value = false;
  }
}

/** 插入变量到光标位置 */
function insertVariable(candidate: { insert: string }) {
  const el = activeTextarea.value;
  if (!el) return;
  const caret = el.selectionStart ?? el.value.length;
  // 移除已输入的 "{{filter" 部分
  const before = el.value.slice(0, caret);
  const openIdx = before.lastIndexOf('{{');
  const start = openIdx >= 0 ? openIdx : caret;
  const newText = el.value.slice(0, start) + candidate.insert + el.value.slice(caret);
  el.value = newText;
  value.value = newText;
  selectorVisible.value = false;
  // 恢复光标到插入后
  requestAnimationFrame(() => {
    el.focus();
    el.setSelectionRange(start + candidate.insert.length, start + candidate.insert.length);
  });
}
</script>

<template>
  <div class="node-param-form">
    <el-form-item :label="schema.label" :required="schema.required" label-width="110px">
      <!-- options：下拉选择 -->
      <el-select
        v-if="schema.type === 'options'"
        :model-value="value as string"
        @update:model-value="(v) => (value = v)"
        :placeholder="schema.placeholder || '请选择'"
        clearable
        style="width: 100%"
      >
        <el-option v-for="opt in schema.options" :key="opt.value" :label="opt.name" :value="opt.value" />
      </el-select>

      <!-- boolean：开关 -->
      <el-switch v-else-if="schema.type === 'boolean'" :model-value="Boolean(value)" @update:model-value="(v) => (value = v)" />

      <!-- number：数字输入 -->
      <el-input-number
        v-else-if="schema.type === 'number'"
        :model-value="Number(value ?? 0)"
        @update:model-value="(v) => (value = v)"
        :placeholder="schema.placeholder"
        controls-position="right"
        style="width: 100%"
      />

      <!-- textarea：多行文本（支持 {{ 变量选择器 }}） -->
      <div v-else-if="schema.type === 'textarea'" class="node-param-form__textarea-wrap">
        <el-input
          ref="textareaRef"
          :model-value="String(value ?? '')"
          @update:model-value="(v) => (value = v)"
          @input="onTextInput"
          type="textarea"
          :rows="5"
          :placeholder="schema.placeholder || '支持 {{input.xxx}} 变量引用，输入 {{ 弹出选择器'"
        />
        <!-- 变量选择器弹层 -->
        <div
          v-if="selectorVisible"
          class="node-param-form__selector"
          :style="{ top: selectorPosition.top + 'px', left: selectorPosition.left + 'px' }"
        >
          <div class="node-param-form__selector-search">
            <el-input v-model="selectorFilter" placeholder="过滤变量..." size="small" />
          </div>
          <div class="node-param-form__selector-list">
            <div v-for="c in variableCandidates" :key="c.insert" class="node-param-form__selector-item" @mousedown.prevent="insertVariable(c)">
              <span class="node-param-form__selector-name">{{ c.label }}</span>
              <code class="node-param-form__selector-path">{{ c.insert }}</code>
            </div>
            <div v-if="!variableCandidates.length" class="node-param-form__selector-empty">无匹配变量</div>
          </div>
        </div>
      </div>

      <!-- json：JSON 编辑器 -->
      <el-input
        v-else-if="schema.type === 'json'"
        :model-value="typeof value === 'string' ? value : JSON.stringify(value ?? {}, null, 2)"
        type="textarea"
        :rows="6"
        placeholder="JSON 格式"
        @update:model-value="onJsonInput"
      />

      <!-- string：单行输入 -->
      <el-input v-else :model-value="String(value ?? '')" @update:model-value="(v) => (value = v)" :placeholder="schema.placeholder" clearable />

      <div v-if="schema.type === 'textarea' && value && String(value).includes('{{')" class="node-param-form__var-tip">
        检测到变量引用，支持：&#123;&#123;input.xxx&#125;&#125;（运行入参）、&#123;&#123;nodes.节点ID.output.xxx&#125;&#125;（前序输出）
      </div>
    </el-form-item>
  </div>
</template>

<style scoped>
.node-param-form {
  width: 100%;
}

.node-param-form__textarea-wrap {
  position: relative;
  width: 100%;
}

.node-param-form__selector {
  position: absolute;
  z-index: 3000;
  width: 280px;
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

.node-param-form__var-tip {
  font-size: 11px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
