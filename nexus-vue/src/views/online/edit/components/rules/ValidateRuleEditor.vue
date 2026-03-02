<template>
  <el-card shadow="never" class="rule-card">
    <div class="rule-row">
      <el-select v-model="local.type" placeholder="type" style="width: 140px">
        <el-option label="regex" value="regex" />
        <el-option label="range" value="range" />
        <el-option label="compare" value="compare" />
        <el-option label="in" value="in" />
        <el-option label="notIn" value="notIn" />
      </el-select>

      <el-select
        v-model="local.field"
        filterable
        clearable
        allow-create
        default-first-option
        placeholder="field（默认当前字段）"
        style="width: 240px"
      >
        <el-option v-for="f in fields" :key="f.value" :label="f.label" :value="f.value" />
      </el-select>

      <el-input v-model="local.message" placeholder="message" style="width: 280px" clearable />

      <el-input-number v-model="local.priority" controls-position="right" placeholder="priority" />

      <el-button link type="danger" @click="$emit('remove')">删除</el-button>
    </div>

    <!-- type specific -->
    <div v-if="local.type === 'regex'" class="rule-row">
      <el-input v-model="local.params.pattern" placeholder="pattern（正则）" style="width: 520px" clearable />
    </div>

    <div v-else-if="local.type === 'range'" class="rule-row">
      <el-input-number v-model="local.params.min" placeholder="min" />
      <el-input-number v-model="local.params.max" placeholder="max" />
    </div>

    <div v-else-if="local.type === 'compare'" class="rule-row">
      <el-select
        v-model="local.params.left"
        filterable
        clearable
        allow-create
        default-first-option
        placeholder="left"
        style="width: 220px"
      >
        <el-option v-for="f in fields" :key="f.value" :label="f.label" :value="f.value" />
      </el-select>
      <el-select v-model="local.params.op" placeholder="op" style="width: 120px">
        <el-option label="eq" value="eq" />
        <el-option label="ne" value="ne" />
        <el-option label="gt" value="gt" />
        <el-option label="ge" value="ge" />
        <el-option label="lt" value="lt" />
        <el-option label="le" value="le" />
      </el-select>
      <el-select
        v-model="local.params.right"
        filterable
        clearable
        allow-create
        default-first-option
        placeholder="right"
        style="width: 220px"
      >
        <el-option v-for="f in fields" :key="f.value" :label="f.label" :value="f.value" />
      </el-select>
    </div>

    <div v-else-if="local.type === 'in' || local.type === 'notIn'" class="rule-row">
      <el-select
        v-model="local.params.list"
        multiple
        filterable
        clearable
        allow-create
        default-first-option
        collapse-tags
        placeholder="list（多个值）"
        style="width: 520px"
      />
    </div>

    <WhenBuilder v-model="local.when" :fields="fields" />
  </el-card>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import WhenBuilder, { type FieldOption, type WhenClause } from '../WhenBuilder.vue'

type ValidateRule = {
  when?: WhenClause
  type: 'regex' | 'range' | 'compare' | 'in' | 'notIn'
  field?: string
  fields?: string[]
  message: string
  params?: Record<string, any>
  priority?: number
}

const props = defineProps<{
  modelValue: ValidateRule
  fields: FieldOption[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: ValidateRule): void
  (e: 'remove'): void
}>()

const local = reactive<any>({
  type: 'regex',
  message: '校验失败',
  params: {},
  ...props.modelValue,
})

const ensureParamsShape = () => {
  if (!local.params || typeof local.params !== 'object') local.params = {}
  if (local.type === 'regex') {
    local.params.pattern = local.params.pattern ?? ''
  } else if (local.type === 'range') {
    local.params.min = local.params.min ?? undefined
    local.params.max = local.params.max ?? undefined
  } else if (local.type === 'compare') {
    local.params.left = local.params.left ?? ''
    local.params.right = local.params.right ?? ''
    local.params.op = local.params.op ?? 'eq'
  } else if (local.type === 'in' || local.type === 'notIn') {
    local.params.list = Array.isArray(local.params.list) ? local.params.list : []
  }
}

watch(
  () => props.modelValue,
  (v) => {
    Object.assign(local, v || {})
    ensureParamsShape()
  },
  { deep: true, immediate: true }
)

watch(
  () => local.type,
  () => ensureParamsShape()
)

watch(
  local,
  () => emit('update:modelValue', { ...local, params: { ...(local.params || {}) } }),
  { deep: true }
)
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
</style>

