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

      <el-switch v-model="local.value" active-text="true" inactive-text="false" />

      <el-input-number v-model="local.priority" controls-position="right" placeholder="priority" />

      <el-button link type="danger" @click="$emit('remove')">删除</el-button>
    </div>

    <div class="rule-row">
      <el-switch v-model="hasElse" active-text="elseValue启用" inactive-text="elseValue不启用" />
      <el-switch
        v-if="hasElse"
        v-model="elseValue"
        active-text="else=true"
        inactive-text="else=false"
      />

      <el-checkbox v-if="showClearOnFalse" v-model="local.clearOnFalse">clearOnFalse</el-checkbox>
    </div>

    <WhenBuilder v-model="local.when" :fields="fields" />
  </el-card>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import WhenBuilder, { type FieldOption, type WhenClause } from '../WhenBuilder.vue'

type ToggleRule = {
  when?: WhenClause
  targets: string[] | string
  value: boolean
  elseValue?: boolean
  clearOnFalse?: boolean
  priority?: number
}

const props = defineProps<{
  modelValue: ToggleRule
  targets: FieldOption[]
  fields: FieldOption[]
  showClearOnFalse?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: ToggleRule): void
  (e: 'remove'): void
}>()

const local = reactive<ToggleRule>({ ...props.modelValue })

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
  set: (arr) => {
    local.targets = arr
  }
})

const hasElse = ref(local.elseValue !== undefined)
const elseValue = ref<boolean>(local.elseValue ?? false)

watch(
  () => props.modelValue?.elseValue,
  (v) => {
    hasElse.value = v !== undefined
    elseValue.value = v ?? false
  }
)

watch([hasElse, elseValue], () => {
  local.elseValue = hasElse.value ? elseValue.value : undefined
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

