<script setup lang="ts">
import { computed } from 'vue';
import type { NodeParamSchema } from '@/api/ai/flow';

/**
 * 通用参数表单：按 NodeParamSchema.type 渲染（对齐 n8n ParameterInputFull）
 * <p>支持类型：string / number / boolean / options / textarea / json</p>
 */

const props = defineProps<{
  /** 参数 schema */
  schema: NodeParamSchema;
  /** 当前值 */
  modelValue: unknown;
  /** 节点类型（用于变量提示） */
  nodeType?: string;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: unknown): void;
}>();

const value = computed({
  get: () => props.modelValue,
  set: (v: unknown) => emit('update:modelValue', v)
});

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

      <!-- textarea：多行文本（支持 {{ 变量 }}） -->
      <el-input
        v-else-if="schema.type === 'textarea'"
        :model-value="String(value ?? '')"
        @update:model-value="(v) => (value = v)"
        type="textarea"
        :rows="5"
        :placeholder="schema.placeholder || '支持 {{input.xxx}} 变量引用'"
      />

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

.node-param-form__var-tip {
  font-size: 11px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
