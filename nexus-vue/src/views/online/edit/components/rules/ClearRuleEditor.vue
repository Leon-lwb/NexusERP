<template>
  <el-card shadow="never" class="rule-card">
    <div class="rule-row">
      <el-select
        v-model="targetsModel"
        multiple
        filterable
        clearable
        collapse-tags
        placeholder="targets（清空字段）"
        style="width: 360px"
      >
        <el-option v-for="t in targets" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>

      <el-input-number v-model="local.priority" controls-position="right" placeholder="priority" />

      <el-button link type="danger" @click="$emit('remove')">删除</el-button>
    </div>

    <WhenBuilder v-model="local.when" :fields="fields" />
  </el-card>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import WhenBuilder, { type FieldOption, type WhenClause } from '../WhenBuilder.vue'

type ClearRule = { when?: WhenClause; targets: string[] | string; priority?: number }

const props = defineProps<{
  modelValue: ClearRule
  targets: FieldOption[]
  fields: FieldOption[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: ClearRule): void
  (e: 'remove'): void
}>()

const local = reactive<ClearRule>({ ...props.modelValue })

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

