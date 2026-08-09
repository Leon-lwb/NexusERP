<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { Delete, Plus } from '@element-plus/icons-vue';
import type { NodeParamSchema } from '@/api/ai/flow';
import { listKnowledge } from '@/api/ai/knowledge';
import { listModelOptions } from '@/api/ai/model';
import { useVariablePicker, buildVariableCandidates, type VariableCandidate } from './useVariablePicker';
import type { FlowCanvasNode } from './useFlowStore';
import KeyValueEditor from './KeyValueEditor.vue';

/**
 * 通用参数表单：按 NodeParamSchema 渲染（对齐 n8n ParameterInputFull）
 * <p>支持类型：string / number / boolean / options / textarea / json / conditionList
 * 增强标记：editor=keyvalue（键值对行编辑）/ toolArgs（工具参数表单）；
 * optionsSource=aiModel/aiKnowledge（实体下拉选择器）
 * textarea 与各值输入框支持 {{ 变量选择器（运行时真实输出优先）</p>
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
  /** 节点运行时输出缓存（变量选择器候选，真实数据优先） */
  nodeOutputs?: Record<string, unknown> | null;
  /** 当前节点完整 config（erp_tool 的 args 需要 toolName 字段联动） */
  config?: Record<string, unknown>;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: unknown): void;
}>();

const value = computed({
  get: () => props.modelValue,
  set: (v: unknown) => emit('update:modelValue', v)
});

const picker = useVariablePicker();

/** 变量候选（运行时真实输出优先） */
const variableCandidates = computed(() => buildVariableCandidates(props.upstreamNodes ?? [], props.nodeOutputs ?? null));

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

/** textarea 输入监听：检测 "{{" 触发变量选择器 */
function onTextInput(text: string) {
  value.value = text;
  const el = document.activeElement as HTMLTextAreaElement | null;
  if (el && el.tagName === 'TEXTAREA') {
    picker.open(el, variableCandidates.value, (t) => (value.value = t));
  }
}

/* ==================== conditionList 条件行编辑器 ==================== */

interface ConditionRow {
  variable: string;
  operator: string;
  value: string;
}

const operators = computed<string[]>(() => {
  const ops = props.schema.attrs?.operators;
  return Array.isArray(ops) ? (ops as string[]) : ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE'];
});

const condRows = ref<ConditionRow[]>([]);

function parseConditions(): ConditionRow[] {
  const raw = props.modelValue;
  if (Array.isArray(raw)) {
    return raw.map((c) => ({
      variable: String((c as any)?.variable ?? ''),
      operator: String((c as any)?.operator ?? 'EQ'),
      value: String((c as any)?.value ?? '')
    }));
  }
  if (typeof raw === 'string' && raw.trim()) {
    try {
      const arr = JSON.parse(raw);
      if (Array.isArray(arr)) {
        return arr.map((c) => ({
          variable: String(c?.variable ?? ''),
          operator: String(c?.operator ?? 'EQ'),
          value: String(c?.value ?? '')
        }));
      }
    } catch {
      /* 非法 JSON，回退空 */
    }
  }
  return [{ variable: '', operator: 'EQ', value: '' }];
}

watch(
  () => props.modelValue,
  () => {
    condRows.value = parseConditions();
  },
  { immediate: true }
);

function emitConditions() {
  const list = condRows.value.filter((r) => r.variable.trim());
  emit('update:modelValue', list.length ? list : undefined);
}

function updateCond(i: number, field: 'variable' | 'operator' | 'value', v: string) {
  condRows.value[i] = { ...condRows.value[i], [field]: v };
  emitConditions();
}

function addCond() {
  condRows.value = [...condRows.value, { variable: '', operator: 'EQ', value: '' }];
}

function removeCond(i: number) {
  condRows.value = condRows.value.filter((_, idx) => idx !== i);
  emitConditions();
}

/** 条件 variable 输入 {{ 检测 */
function onCondVariableInput(i: number, text: string) {
  updateCond(i, 'variable', text);
  const el = document.activeElement as HTMLInputElement | null;
  if (el && el.tagName === 'INPUT') {
    picker.open(el, variableCandidates.value, (t) => updateCond(i, 'variable', t));
  }
}

/* ==================== optionsSource 实体下拉 ==================== */

interface EntityOption {
  name: string;
  value: string;
}

const entityOptions = ref<EntityOption[]>([]);
const entityLoading = ref(false);

/** 按 optionsSource 拉取候选 */
async function loadEntityOptions() {
  const source = props.schema.optionsSource;
  if (!source) return;
  entityLoading.value = true;
  try {
    if (source === 'aiModel') {
      const res = await listModelOptions('llm');
      const list = res.data ?? res.rows ?? [];
      entityOptions.value = (Array.isArray(list) ? list : []).map((m: any) => ({
        name: `${m.modelName ?? m.name ?? m.id}（${m.providerName ?? ''}）`,
        value: String(m.id)
      }));
    } else if (source === 'aiKnowledge') {
      const res = await listKnowledge({ pageNum: 1, pageSize: 100 });
      const list = res.rows ?? [];
      entityOptions.value = (Array.isArray(list) ? list : []).map((k: any) => ({
        name: k.knowledgeName ?? String(k.id),
        value: String(k.id)
      }));
    }
  } catch (e) {
    console.warn('[Flow] 加载实体选项失败:', e);
  } finally {
    entityLoading.value = false;
  }
}

watch(
  () => props.schema.optionsSource,
  () => loadEntityOptions(),
  { immediate: true }
);

/* ==================== erp_tool 工具参数表单 ==================== */

/** 当前选中工具的参数定义 */
const toolArgsForSelected = computed<Array<{ name: string; type: string }>>(() => {
  const toolName = (props.config as any)?.toolName as string | undefined;
  const all = props.schema.attrs?.toolArgs as Record<string, Array<{ name: string; type: string }>> | undefined;
  if (!toolName || !all) return [];
  return all[toolName] ?? [];
});

/** args 对象（键→值） */
const toolArgsObj = computed<Record<string, string>>(() => {
  const raw = props.modelValue;
  if (raw && typeof raw === 'object') {
    const obj: Record<string, string> = {};
    for (const [k, v] of Object.entries(raw as Record<string, unknown>)) {
      obj[k] = String(v ?? '');
    }
    return obj;
  }
  if (typeof raw === 'string' && raw.trim()) {
    try {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') {
        const obj: Record<string, string> = {};
        for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
          obj[k] = String(v ?? '');
        }
        return obj;
      }
    } catch {
      /* 忽略 */
    }
  }
  return {};
});

function setToolArg(name: string, v: string) {
  const next = { ...toolArgsObj.value, [name]: v };
  emit('update:modelValue', Object.keys(next).length ? next : undefined);
}

/** 工具参数输入 {{ 检测 */
function onToolArgInput(name: string, text: string) {
  setToolArg(name, text);
  const el = document.activeElement as HTMLInputElement | null;
  if (el && el.tagName === 'INPUT') {
    picker.open(el, variableCandidates.value, (t) => setToolArg(name, t));
  }
}

onMounted(loadEntityOptions);
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

      <!-- optionsSource=aiModel/aiKnowledge：实体下拉 -->
      <el-select
        v-else-if="schema.optionsSource"
        :model-value="String(value ?? '')"
        @update:model-value="(v) => (value = v ? Number(v) : undefined)"
        :loading="entityLoading"
        :placeholder="'选择' + schema.label"
        clearable
        filterable
        style="width: 100%"
      >
        <el-option v-for="opt in entityOptions" :key="opt.value" :label="opt.name" :value="opt.value" />
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

      <!-- editor=keyvalue：键值对行编辑 -->
      <KeyValueEditor
        v-else-if="schema.editor === 'keyvalue'"
        :model-value="value"
        :upstream-nodes="upstreamNodes"
        :node-outputs="nodeOutputs"
        @update:model-value="(v) => (value = v)"
      />

      <!-- editor=toolArgs：ERP 工具参数表单 -->
      <div v-else-if="schema.editor === 'toolArgs'" class="node-param-form__toolargs">
        <div v-if="toolArgsForSelected.length">
          <div v-for="arg in toolArgsForSelected" :key="arg.name" class="node-param-form__toolarg">
            <span class="node-param-form__toolarg-name">{{ arg.name }}</span>
            <el-input
              :model-value="toolArgsObj[arg.name] ?? ''"
              size="small"
              :placeholder="arg.type === 'number' ? '数字' : arg.type === 'boolean' ? 'true/false' : '值（支持 {{}} 变量）'"
              @update:model-value="(v: string) => onToolArgInput(arg.name, v)"
            />
          </div>
        </div>
        <div v-else class="node-param-form__toolargs-empty">请先在上方选择工具</div>
      </div>

      <!-- conditionList：条件行编辑（IF 节点） -->
      <div v-else-if="schema.type === 'conditionList'" class="node-param-form__conds">
        <div v-for="(row, i) in condRows" :key="i" class="node-param-form__cond">
          <el-input
            :model-value="row.variable"
            size="small"
            placeholder="变量（如 input.amount / nodes.node_1.output.x）"
            @update:model-value="(v: string) => onCondVariableInput(i, v)"
          />
          <el-select
            :model-value="row.operator"
            size="small"
            style="width: 110px; flex-shrink: 0"
            @update:model-value="(v: string) => updateCond(i, 'operator', v)"
          >
            <el-option v-for="op in operators" :key="op" :label="op" :value="op" />
          </el-select>
          <el-input :model-value="row.value" size="small" placeholder="比较值" @update:model-value="(v: string) => updateCond(i, 'value', v)" />
          <el-button link type="danger" :icon="Delete" size="small" @click="removeCond(i)" />
        </div>
        <el-button type="primary" plain size="small" :icon="Plus" @click="addCond">添加条件</el-button>
        <div class="node-param-form__conds-tip">条件使用 {{ schema.attrs?.logicHint ?? '节点输出字段' }} 判断，true 走绿色分支、false 走红色分支</div>
      </div>

      <!-- textarea：多行文本（支持 {{ 变量选择器 }}） -->
      <div v-else-if="schema.type === 'textarea'" class="node-param-form__textarea-wrap">
        <el-input
          :model-value="String(value ?? '')"
          @update:model-value="(v) => (value = v)"
          @input="onTextInput"
          type="textarea"
          :rows="5"
          :placeholder="schema.placeholder || '支持 {{input.xxx}} 变量引用，输入 {{ 弹出选择器'"
        />
        <div v-if="value && String(value).includes('{{')" class="node-param-form__var-tip">
          检测到变量引用，支持：&#123;&#123;input.xxx&#125;&#125;（运行入参）、&#123;&#123;nodes.节点ID.output.xxx&#125;&#125;（前序输出）
        </div>
      </div>

      <!-- json：JSON 编辑器（无增强标记时兜底） -->
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
    </el-form-item>

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
.node-param-form {
  width: 100%;
}

.node-param-form__textarea-wrap {
  position: relative;
  width: 100%;
}

/* 条件行编辑器 */
.node-param-form__conds {
  width: 100%;
}

.node-param-form__cond {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.node-param-form__cond .el-input {
  flex: 1;
}

.node-param-form__conds-tip {
  font-size: 11px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}

/* 工具参数表单 */
.node-param-form__toolargs {
  width: 100%;
}

.node-param-form__toolarg {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.node-param-form__toolarg-name {
  font-size: 12px;
  color: #303133;
  white-space: nowrap;
  flex-shrink: 0;
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-param-form__toolargs-empty {
  font-size: 12px;
  color: #c0c4cc;
  text-align: center;
  padding: 8px 0;
}

/* 变量选择器弹层 */
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

.node-param-form__var-tip {
  font-size: 11px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
