<template>
  <el-card shadow="never" class="rule-card">
    <div class="rule-row">
      <el-select
        v-model="targetsModel"
        multiple
        filterable
        clearable
        collapse-tags
        placeholder="targets（popup 字段）"
        style="width: 360px"
      >
        <el-option v-for="t in targets" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>

      <el-checkbox v-model="local.merge">merge</el-checkbox>
      <el-input-number v-model="local.priority" controls-position="right" placeholder="priority" />
      <el-button link type="danger" @click="$emit('remove')">删除</el-button>
    </div>

    <div class="sublist">
      <div class="sublist-header">
        <span>params（查询参数）</span>
        <el-button size="small" type="primary" @click="addParam">新增参数</el-button>
      </div>

      <div v-if="paramsIsObject" class="sublist-hint">
        <el-alert type="warning" :closable="false" show-icon title="检测到 params 是对象形式，已自动转换为数组形式用于可视化编辑。" />
      </div>

      <div v-for="(p, idx) in params" :key="idx" class="sublist-row">
        <el-input v-model="p.key" placeholder="key" style="width: 180px" clearable />
        <el-select v-model="p.mode" placeholder="mode" style="width: 120px">
          <el-option label="const" value="const" />
          <el-option label="copy" value="copy" />
          <el-option label="formula" value="formula" />
        </el-select>

        <el-input v-if="p.mode === 'const'" v-model="p.value" placeholder="value" style="width: 260px" clearable />

        <el-select
          v-else-if="p.mode === 'copy'"
          v-model="p.from"
          filterable
          clearable
          allow-create
          default-first-option
          placeholder="from"
          style="width: 260px"
        >
          <el-option v-for="f in fields" :key="f.value" :label="f.label" :value="f.value" />
        </el-select>

        <el-input v-else-if="p.mode === 'formula'" v-model="p.formula" placeholder="formula" style="width: 260px" clearable />

        <el-button link type="danger" @click="removeParam(idx)">删除</el-button>
      </div>
    </div>

    <WhenBuilder v-model="local.when" :fields="fields" />
  </el-card>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import WhenBuilder, { type FieldOption, type WhenClause } from '../WhenBuilder.vue'

type PopupQueryParam = { key: string; mode: 'const' | 'copy' | 'formula'; value?: any; from?: string; formula?: string }
type PopupQueryRule = { when?: WhenClause; targets: string[] | string; params: Record<string, any> | PopupQueryParam[]; merge?: boolean; priority?: number }

const props = defineProps<{
  modelValue: PopupQueryRule
  targets: FieldOption[]
  fields: FieldOption[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: PopupQueryRule): void
  (e: 'remove'): void
}>()

const local = reactive<PopupQueryRule>({
  merge: true,
  params: [],
  ...props.modelValue,
})

const params = ref<PopupQueryParam[]>([])
const paramsIsObject = ref(false)

const toArrayParams = (v: any): PopupQueryParam[] => {
  if (Array.isArray(v)) return v as any
  if (v && typeof v === 'object') {
    return Object.keys(v).map((k) => ({ key: k, mode: 'const', value: (v as any)[k] }))
  }
  return []
}

const syncParamsFromLocal = () => {
  paramsIsObject.value = !!(local.params && typeof local.params === 'object' && !Array.isArray(local.params))
  params.value = toArrayParams(local.params).map((p) => ({
    key: String(p.key || ''),
    mode: (p.mode || 'copy') as any,
    value: p.value,
    from: p.from,
    formula: p.formula,
  }))
  local.params = params.value
}

watch(
  () => props.modelValue,
  (v) => {
    Object.assign(local, v || {})
    syncParamsFromLocal()
  },
  { deep: true, immediate: true }
)

watch(
  params,
  () => { local.params = params.value },
  { deep: true }
)

watch(
  local,
  () => emit('update:modelValue', { ...local, targets: local.targets, params: local.params }),
  { deep: true }
)

const targetsModel = computed<string[]>({
  get: () => {
    const v: any = local.targets
    if (Array.isArray(v)) return v
    if (typeof v === 'string' && v) return v.split(',').map(s => s.trim()).filter(Boolean)
    return []
  },
  set: (arr) => { local.targets = arr }
})

const addParam = () => {
  params.value.push({ key: '', mode: 'copy', from: '' })
}
const removeParam = (idx: number) => {
  params.value.splice(idx, 1)
}
</script>

<style scoped>
.rule-card {
  margin-bottom: 10px;
}
.rule-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.sublist {
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 10px;
}
.sublist-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.sublist-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.sublist-hint {
  margin-bottom: 8px;
}
</style>

