<template>
  <el-card shadow="never" class="rule-card">
    <div class="rule-row">
      <el-select
        v-model="targetsModel"
        multiple
        filterable
        clearable
        collapse-tags
        placeholder="targets（目标字段）"
        style="width: 360px"
      >
        <el-option v-for="t in targets" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>

      <el-select v-model="local.mode" placeholder="mode" style="width: 140px">
        <el-option label="const" value="const" />
        <el-option label="copy" value="copy" />
        <el-option label="formula" value="formula" />
      </el-select>

      <el-checkbox v-model="local.overwrite">overwrite</el-checkbox>
      <el-input-number v-model="local.priority" controls-position="right" placeholder="priority" />
      <el-button link type="danger" @click="$emit('remove')">删除</el-button>
    </div>

    <div class="rule-row">
      <el-input
        v-if="local.mode === 'const'"
        v-model="local.value"
        placeholder="value（常量）"
        style="width: 520px"
        clearable
      />

      <el-select
        v-else-if="local.mode === 'copy'"
        v-model="local.from"
        filterable
        clearable
        allow-create
        default-first-option
        placeholder="from（拷贝来源字段）"
        style="width: 520px"
      >
        <el-option v-for="f in fields" :key="f.value" :label="f.label" :value="f.value" />
      </el-select>

      <el-input
        v-else-if="local.mode === 'formula'"
        v-model="local.formula"
        placeholder="formula（如 {{price}} * {{num}}）"
        style="width: 520px"
        clearable
      />
    </div>

    <WhenBuilder v-model="local.when" :fields="fields" />
  </el-card>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import WhenBuilder, { type FieldOption, type WhenClause } from '../WhenBuilder.vue'

type SetValueRule = {
  when?: WhenClause
  targets: string[] | string
  mode: 'const' | 'copy' | 'formula'
  value?: any
  from?: string
  formula?: string
  overwrite?: boolean
  priority?: number
}

const props = defineProps<{
  modelValue: SetValueRule
  targets: FieldOption[]
  fields: FieldOption[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: SetValueRule): void
  (e: 'remove'): void
}>()

const local = reactive<SetValueRule>({ overwrite: true, ...props.modelValue })

watch(
  () => props.modelValue,
  (v) => Object.assign(local, v || {}),
  { deep: true }
)

watch(
  local,
  () => emit('update:modelValue', { ...local, targets: local.targets }),
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

