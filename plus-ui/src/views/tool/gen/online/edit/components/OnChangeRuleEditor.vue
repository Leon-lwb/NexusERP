<template>
  <div class="onchange-editor">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="可视化配置会生成并覆盖 onChange 的 JSON；未知 key 会被保留（在高级模式可编辑）。"
      class="mb-2"
    />

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="基础（兼容旧版）" name="legacy">
        <LegacyRuleEditor
          v-model:code="legacy.code"
          v-model:main="legacy.main"
          v-model:sub="legacy.sub"
          v-model:trigger="legacy.trigger"
          :targets="targets"
        />
      </el-tab-pane>

      <el-tab-pane label="显隐 / 禁用 / 必填" name="toggle">
        <RuleList title="onVisible（显隐）" @add="addToggle('visible')" />
        <ToggleRuleEditor
          v-for="(r, idx) in onVisible"
          :key="`vis-${idx}`"
          v-model="onVisible[idx]"
          :targets="targets"
          :fields="fields"
          :show-clear-on-false="true"
          @remove="removeAt(onVisible, idx)"
        />

        <RuleList title="onDisable（禁用）" @add="addToggle('disable')" />
        <ToggleRuleEditor
          v-for="(r, idx) in onDisable"
          :key="`dis-${idx}`"
          v-model="onDisable[idx]"
          :targets="targets"
          :fields="fields"
          @remove="removeAt(onDisable, idx)"
        />

        <RuleList title="onRequired（必填）" @add="addToggle('required')" />
        <ToggleRuleEditor
          v-for="(r, idx) in onRequired"
          :key="`req-${idx}`"
          v-model="onRequired[idx]"
          :targets="targets"
          :fields="fields"
          @remove="removeAt(onRequired, idx)"
        />
      </el-tab-pane>

      <el-tab-pane label="清空 / 赋值" name="value">
        <RuleList title="onClear（清空字段）" @add="addClear" />
        <ClearRuleEditor
          v-for="(r, idx) in onClear"
          :key="`clr-${idx}`"
          v-model="onClear[idx]"
          :targets="targets"
          :fields="fields"
          @remove="removeAt(onClear, idx)"
        />

        <RuleList title="onSetValue（赋值）" @add="addSetValue" />
        <SetValueRuleEditor
          v-for="(r, idx) in onSetValue"
          :key="`sv-${idx}`"
          v-model="onSetValue[idx]"
          :targets="targets"
          :fields="fields"
          @remove="removeAt(onSetValue, idx)"
        />
      </el-tab-pane>

      <el-tab-pane label="校验" name="validate">
        <RuleList title="onValidate（运行时校验）" @add="addValidate" />
        <ValidateRuleEditor
          v-for="(r, idx) in onValidate"
          :key="`val-${idx}`"
          v-model="onValidate[idx]"
          :fields="fields"
          @remove="removeAt(onValidate, idx)"
        />
      </el-tab-pane>

      <el-tab-pane label="字典级联" name="dict">
        <RuleList title="onDictCascade（字典/枚举级联）" @add="addDictCascade" />
        <DictCascadeRuleEditor
          v-for="(r, idx) in onDictCascade"
          :key="`dc-${idx}`"
          v-model="onDictCascade[idx]"
          :targets="targets"
          :fields="fields"
          @remove="removeAt(onDictCascade, idx)"
        />
      </el-tab-pane>

      <el-tab-pane label="Popup 查询联动" name="popup">
        <RuleList title="onPopupQuery（给 popup 注入查询参数）" @add="addPopupQuery" />
        <PopupQueryRuleEditor
          v-for="(r, idx) in onPopupQuery"
          :key="`pq-${idx}`"
          v-model="onPopupQuery[idx]"
          :targets="targets"
          :fields="fields"
          @remove="removeAt(onPopupQuery, idx)"
        />
      </el-tab-pane>

      <el-tab-pane label="子表行规则" name="row">
        <RuleList title="onRowAdd（新增子表行默认值）" @add="addRowAdd" />
        <SetValueRuleEditor
          v-for="(r, idx) in onRowAdd"
          :key="`ra-${idx}`"
          v-model="onRowAdd[idx]"
          :targets="targets"
          :fields="fields"
          @remove="removeAt(onRowAdd, idx)"
        />

        <RuleList title="onRowValidate（子表行校验）" @add="addRowValidate" />
        <ValidateRuleEditor
          v-for="(r, idx) in onRowValidate"
          :key="`rv-${idx}`"
          v-model="onRowValidate[idx]"
          :fields="fields"
          @remove="removeAt(onRowValidate, idx)"
        />
      </el-tab-pane>

      <el-tab-pane label="高级" name="advanced">
        <el-alert
          v-if="unknownKeys.length"
          type="warning"
          show-icon
          :closable="false"
          :title="`检测到未知 key（会被保留）：${unknownKeys.join(', ')}`"
          class="mb-2"
        />

        <div class="advanced-actions">
          <el-button size="small" @click="syncRawFromStructured">从可视化生成 JSON</el-button>
          <el-button size="small" type="primary" @click="applyRawToStructured">用 JSON 覆盖可视化</el-button>
          <el-button size="small" type="danger" plain @click="resetAll">清空全部规则</el-button>
        </div>

        <el-input v-model="raw" type="textarea" :rows="14" placeholder="onChange JSON" />

        <el-alert
          v-if="validationErrors.length"
          class="mt-2"
          type="error"
          show-icon
          :closable="false"
          title="JSON 校验失败"
        >
          <template #default>
            <div v-for="(e, i) in validationErrors" :key="i">{{ e }}</div>
          </template>
        </el-alert>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { validateOnChange } from '@/composables/online/fieldConfig'
import type { FieldOption } from './WhenBuilder.vue'
import LegacyRuleEditor from './rules/LegacyRuleEditor.vue'
import RuleList from './rules/RuleList.vue'
import ToggleRuleEditor from './rules/ToggleRuleEditor.vue'
import ClearRuleEditor from './rules/ClearRuleEditor.vue'
import SetValueRuleEditor from './rules/SetValueRuleEditor.vue'
import ValidateRuleEditor from './rules/ValidateRuleEditor.vue'
import DictCascadeRuleEditor from './rules/DictCascadeRuleEditor.vue'
import PopupQueryRuleEditor from './rules/PopupQueryRuleEditor.vue'

type AnyObj = Record<string, any>

type CodeGenerate = { autoGenerateRule?: string; generateParams?: Record<string, any> }
type FormulaBlock = { formula?: string; targetFields?: string[] | string; triggerCondition?: string }
type SubTrigger = { triggerType?: 'aggregate'|'sum'|'count'|'average'|'max'|'min'; formula?: string; targetFields?: string[] | string }

type ToggleRule = { when?: any; targets: string[] | string; value: boolean; elseValue?: boolean; clearOnFalse?: boolean; priority?: number }
type ClearRule = { when?: any; targets: string[] | string; priority?: number }
type SetValueRule = { when?: any; targets: string[] | string; mode: 'const'|'copy'|'formula'; value?: any; from?: string; formula?: string; overwrite?: boolean; priority?: number }
type ValidateRule = { when?: any; type: 'regex'|'range'|'compare'|'in'|'notIn'; field?: string; fields?: string[]; message: string; params?: AnyObj; priority?: number }
type DictCascadeRule = { when?: any; targets: string[] | string; from: string; optionMatchField?: string; emptyMode?: 'none'|'all'; clearOnChange?: boolean; priority?: number }
type PopupQueryRule = { when?: any; targets: string[] | string; params: AnyObj | any[]; merge?: boolean; priority?: number }

type OnChangeConfig = {
  onCodeGenerate?: CodeGenerate
  onMainChange?: FormulaBlock
  onSubChange?: FormulaBlock
  onSubTrigger?: SubTrigger
  onVisible?: ToggleRule[]
  onDisable?: ToggleRule[]
  onRequired?: ToggleRule[]
  onClear?: ClearRule[]
  onSetValue?: SetValueRule[]
  onValidate?: ValidateRule[]
  onDictCascade?: DictCascadeRule[]
  onPopupQuery?: PopupQueryRule[]
  onRowAdd?: SetValueRule[]
  onRowValidate?: ValidateRule[]
  [k: string]: any
}

const KNOWN_KEYS = new Set([
  'onCodeGenerate',
  'onMainChange',
  'onSubChange',
  'onSubTrigger',
  'onVisible',
  'onDisable',
  'onRequired',
  'onClear',
  'onSetValue',
  'onValidate',
  'onDictCascade',
  'onPopupQuery',
  'onRowAdd',
  'onRowValidate',
])

const props = defineProps<{
  modelValue?: string | AnyObj | null
  fields: FieldOption[]
  targets?: FieldOption[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
}>()

const activeTab = ref('legacy')

const targets = computed(() => props.targets?.length ? props.targets : props.fields)

const legacy = reactive<{
  code: CodeGenerate | null
  main: FormulaBlock | null
  sub: FormulaBlock | null
  trigger: SubTrigger | null
}>({
  code: null,
  main: null,
  sub: null,
  trigger: null,
})

const onVisible = ref<ToggleRule[]>([])
const onDisable = ref<ToggleRule[]>([])
const onRequired = ref<ToggleRule[]>([])
const onClear = ref<ClearRule[]>([])
const onSetValue = ref<SetValueRule[]>([])
const onValidate = ref<ValidateRule[]>([])
const onDictCascade = ref<DictCascadeRule[]>([])
const onPopupQuery = ref<PopupQueryRule[]>([])
const onRowAdd = ref<SetValueRule[]>([])
const onRowValidate = ref<ValidateRule[]>([])
const extras = ref<AnyObj>({})

const raw = ref<string>('{}')

const safeParseObject = (input: any): AnyObj => {
  if (input == null) return {}
  if (typeof input === 'object' && !Array.isArray(input)) return input as AnyObj
  if (typeof input !== 'string') return {}
  const s = input.trim()
  if (!s) return {}
  try {
    const obj = JSON.parse(s)
    return (obj && typeof obj === 'object' && !Array.isArray(obj)) ? obj : {}
  } catch {
    return {}
  }
}

const removeAt = <T>(arr: { value: T[] }, idx: number) => {
  arr.value.splice(idx, 1)
}

const normalizeArr = <T>(v: any): T[] => (Array.isArray(v) ? v.filter(Boolean) : [])

const pickExtras = (cfg: AnyObj): AnyObj => {
  const out: AnyObj = {}
  Object.keys(cfg || {}).forEach((k) => {
    if (!KNOWN_KEYS.has(k)) out[k] = cfg[k]
  })
  return out
}

const syncFromConfig = (cfg: OnChangeConfig) => {
  legacy.code = cfg.onCodeGenerate ?? null
  legacy.main = cfg.onMainChange ?? null
  legacy.sub = cfg.onSubChange ?? null
  legacy.trigger = cfg.onSubTrigger ?? null

  onVisible.value = normalizeArr<ToggleRule>(cfg.onVisible)
  onDisable.value = normalizeArr<ToggleRule>(cfg.onDisable)
  onRequired.value = normalizeArr<ToggleRule>(cfg.onRequired)
  onClear.value = normalizeArr<ClearRule>(cfg.onClear)
  onSetValue.value = normalizeArr<SetValueRule>(cfg.onSetValue)
  onValidate.value = normalizeArr<ValidateRule>(cfg.onValidate)
  onDictCascade.value = normalizeArr<DictCascadeRule>(cfg.onDictCascade)
  onPopupQuery.value = normalizeArr<PopupQueryRule>(cfg.onPopupQuery)
  onRowAdd.value = normalizeArr<SetValueRule>(cfg.onRowAdd)
  onRowValidate.value = normalizeArr<ValidateRule>(cfg.onRowValidate)

  extras.value = pickExtras(cfg as any)
}

const buildConfig = (): OnChangeConfig => {
  const out: OnChangeConfig = { ...(extras.value || {}) }

  if (legacy.code) out.onCodeGenerate = { ...(legacy.code as any) }
  if (legacy.main) out.onMainChange = { ...(legacy.main as any) }
  if (legacy.sub) out.onSubChange = { ...(legacy.sub as any) }
  if (legacy.trigger) out.onSubTrigger = { ...(legacy.trigger as any) }

  if (onVisible.value.length) out.onVisible = onVisible.value.map(r => ({ ...(r as any) }))
  if (onDisable.value.length) out.onDisable = onDisable.value.map(r => ({ ...(r as any) }))
  if (onRequired.value.length) out.onRequired = onRequired.value.map(r => ({ ...(r as any) }))
  if (onClear.value.length) out.onClear = onClear.value.map(r => ({ ...(r as any) }))
  if (onSetValue.value.length) out.onSetValue = onSetValue.value.map(r => ({ ...(r as any) }))
  if (onValidate.value.length) out.onValidate = onValidate.value.map(r => ({ ...(r as any) }))
  if (onDictCascade.value.length) out.onDictCascade = onDictCascade.value.map(r => ({ ...(r as any) }))
  if (onPopupQuery.value.length) out.onPopupQuery = onPopupQuery.value.map(r => ({ ...(r as any) }))
  if (onRowAdd.value.length) out.onRowAdd = onRowAdd.value.map(r => ({ ...(r as any) }))
  if (onRowValidate.value.length) out.onRowValidate = onRowValidate.value.map(r => ({ ...(r as any) }))

  return out
}

const stringify = (obj: AnyObj) => {
  try { return JSON.stringify(obj || {}, null, 2) } catch { return '{}' }
}

const unknownKeys = computed(() => Object.keys(extras.value || {}).sort())

const syncRawFromStructured = () => {
  raw.value = stringify(buildConfig())
}

const applyRawToStructured = () => {
  const cfg = safeParseObject(raw.value) as OnChangeConfig
  syncFromConfig(cfg)
}

const resetAll = () => {
  syncFromConfig({})
  raw.value = '{}'
}

const validationErrors = computed(() => {
  const vr = validateOnChange(raw.value)
  return vr.ok ? [] : (vr.errors || [])
})

let syncing = false
watch(
  () => props.modelValue,
  (v) => {
    syncing = true
    const cfg = safeParseObject(v) as OnChangeConfig
    syncFromConfig(cfg)
    raw.value = stringify(cfg)
    syncing = false
  },
  { immediate: true, deep: false }
)

watch(
  () => [
    legacy.code,
    legacy.main,
    legacy.sub,
    legacy.trigger,
    onVisible.value,
    onDisable.value,
    onRequired.value,
    onClear.value,
    onSetValue.value,
    onValidate.value,
    onDictCascade.value,
    onPopupQuery.value,
    onRowAdd.value,
    onRowValidate.value,
    extras.value,
  ],
  () => {
    if (syncing) return
    const cfg = buildConfig()
    const s = stringify(cfg)
    raw.value = s
    emit('update:modelValue', s)
  },
  { deep: true }
)

const addToggle = (kind: 'visible'|'disable'|'required') => {
  const base: ToggleRule = { targets: [], value: true, priority: 100 }
  if (kind === 'visible') onVisible.value.push({ ...base, clearOnFalse: false })
  else if (kind === 'disable') onDisable.value.push({ ...base })
  else onRequired.value.push({ ...base })
}

const addClear = () => {
  onClear.value.push({ targets: [], priority: 100 })
}

const addSetValue = () => {
  onSetValue.value.push({ targets: [], mode: 'copy', from: '', overwrite: true, priority: 100 })
}

const addValidate = () => {
  onValidate.value.push({ type: 'regex', message: '校验失败', params: { pattern: '' }, priority: 100 })
}

const addDictCascade = () => {
  onDictCascade.value.push({ targets: [], from: '', optionMatchField: 'parentId', emptyMode: 'none', clearOnChange: true, priority: 100 })
}

const addPopupQuery = () => {
  onPopupQuery.value.push({ targets: [], merge: true, params: [], priority: 100 })
}

const addRowAdd = () => {
  onRowAdd.value.push({ targets: [], mode: 'copy', from: '', overwrite: true, priority: 100 })
}

const addRowValidate = () => {
  onRowValidate.value.push({ type: 'regex', message: '校验失败', params: { pattern: '' }, priority: 100 })
}
</script>

<style scoped>
.onchange-editor :deep(.el-tabs__content) {
  padding: 12px;
}
.advanced-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.mb-2 { margin-bottom: 10px; }
.mt-2 { margin-top: 10px; }
</style>
