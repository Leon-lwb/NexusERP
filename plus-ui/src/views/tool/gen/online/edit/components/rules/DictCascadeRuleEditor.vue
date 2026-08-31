<template>
  <el-card shadow="never" class="rule-card">
    <div class="rule-row">
      <el-select v-model="targetsModel" multiple filterable clearable collapse-tags placeholder="targets（被级联字段）" style="width: 360px">
        <el-option v-for="t in targets" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>

      <el-select v-model="local.from" filterable clearable allow-create default-first-option placeholder="from（来源字段）" style="width: 220px">
        <el-option v-for="f in fields" :key="f.value" :label="f.label" :value="f.value" />
      </el-select>

      <el-input v-model="local.optionMatchField" placeholder="optionMatchField（默认 parentId）" style="width: 220px" clearable />

      <el-select v-model="local.emptyMode" placeholder="emptyMode" style="width: 140px">
        <el-option label="none" value="none" />
        <el-option label="all" value="all" />
      </el-select>

      <el-checkbox v-model="local.clearOnChange">clearOnChange</el-checkbox>
      <el-input-number v-model="local.priority" controls-position="right" placeholder="priority" />
      <el-button link type="danger" @click="$emit('remove')">删除</el-button>
    </div>

    <WhenBuilder v-model="local.when" :fields="fields" />
  </el-card>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import WhenBuilder, { type FieldOption, type WhenClause } from '../WhenBuilder.vue';

type DictCascadeRule = {
  when?: WhenClause;
  targets: string[] | string;
  from: string;
  optionMatchField?: string;
  emptyMode?: 'none' | 'all';
  clearOnChange?: boolean;
  priority?: number;
};

const props = defineProps<{
  modelValue: DictCascadeRule;
  targets: FieldOption[];
  fields: FieldOption[];
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', v: DictCascadeRule): void;
  (e: 'remove'): void;
}>();

const local = reactive<DictCascadeRule>({
  optionMatchField: 'parentId',
  emptyMode: 'none',
  clearOnChange: true,
  ...props.modelValue
});

watch(
  () => props.modelValue,
  (v) => Object.assign(local, v || {}),
  { deep: true }
);

watch(local, () => emit('update:modelValue', { ...local, targets: local.targets }), { deep: true });

const targetsModel = computed<string[]>({
  get: () => {
    const v: any = local.targets;
    if (Array.isArray(v)) return v;
    if (typeof v === 'string' && v)
      return v
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean);
    return [];
  },
  set: (arr) => {
    local.targets = arr;
  }
});
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
