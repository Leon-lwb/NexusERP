<template>
  <div class="legacy">
    <el-card shadow="never" class="rule-card">
      <div class="legacy-header">
        <el-checkbox v-model="codeEnabled">onCodeGenerate</el-checkbox>
      </div>
      <div v-if="codeEnabled" class="legacy-body">
        <el-input v-model="codeLocal.autoGenerateRule" placeholder="autoGenerateRule（如 DD-{timestamp}）" clearable />
        <el-input v-model="codeLocal.generateParams.format" placeholder="generateParams.format（如 YYYYMMDDHHmmssSSS）" clearable />
      </div>
    </el-card>

    <el-card shadow="never" class="rule-card">
      <div class="legacy-header">
        <el-checkbox v-model="mainEnabled">onMainChange</el-checkbox>
      </div>
      <div v-if="mainEnabled" class="legacy-body">
        <el-input v-model="mainLocal.formula" placeholder="formula（如 {{price}} * {{num}}）" clearable />
        <el-select v-model="mainTargets" multiple filterable clearable collapse-tags placeholder="targetFields" style="width: 520px">
          <el-option v-for="t in targets" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-input v-model="mainLocal.triggerCondition" placeholder="triggerCondition（旧表达式，可选）" clearable />
      </div>
    </el-card>

    <el-card shadow="never" class="rule-card">
      <div class="legacy-header">
        <el-checkbox v-model="subEnabled">onSubChange</el-checkbox>
      </div>
      <div v-if="subEnabled" class="legacy-body">
        <el-input v-model="subLocal.formula" placeholder="formula（如 {{price}} * {{num}}）" clearable />
        <el-select v-model="subTargets" multiple filterable clearable collapse-tags placeholder="targetFields" style="width: 520px">
          <el-option v-for="t in targets" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-input v-model="subLocal.triggerCondition" placeholder="triggerCondition（旧表达式，可选）" clearable />
      </div>
    </el-card>

    <el-card shadow="never" class="rule-card">
      <div class="legacy-header">
        <el-checkbox v-model="triggerEnabled">onSubTrigger</el-checkbox>
      </div>
      <div v-if="triggerEnabled" class="legacy-body">
        <el-select v-model="triggerLocal.triggerType" placeholder="triggerType" style="width: 220px">
          <el-option label="aggregate" value="aggregate" />
          <el-option label="sum" value="sum" />
          <el-option label="count" value="count" />
          <el-option label="average" value="average" />
          <el-option label="max" value="max" />
          <el-option label="min" value="min" />
        </el-select>
        <el-input v-model="triggerLocal.formula" placeholder="formula（如 {{zong_price}}）" clearable />
        <el-select v-model="triggerTargets" multiple filterable clearable collapse-tags placeholder="targetFields" style="width: 520px">
          <el-option v-for="t in targets" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import type { FieldOption } from '../WhenBuilder.vue';

type CodeGenerate = { autoGenerateRule?: string; generateParams?: Record<string, any> };
type FormulaBlock = { formula?: string; targetFields?: string[] | string; triggerCondition?: string };
type SubTrigger = { triggerType?: 'aggregate' | 'sum' | 'count' | 'average' | 'max' | 'min'; formula?: string; targetFields?: string[] | string };

const props = defineProps<{
  code: CodeGenerate | null;
  main: FormulaBlock | null;
  sub: FormulaBlock | null;
  trigger: SubTrigger | null;
  targets: FieldOption[];
}>();

const emit = defineEmits<{
  (e: 'update:code', v: CodeGenerate | null): void;
  (e: 'update:main', v: FormulaBlock | null): void;
  (e: 'update:sub', v: FormulaBlock | null): void;
  (e: 'update:trigger', v: SubTrigger | null): void;
}>();

const codeEnabled = ref(!!props.code);
const mainEnabled = ref(!!props.main);
const subEnabled = ref(!!props.sub);
const triggerEnabled = ref(!!props.trigger);

const codeLocal = reactive<CodeGenerate>({ autoGenerateRule: '', generateParams: { format: '' }, ...(props.code || {}) });
const mainLocal = reactive<FormulaBlock>({ formula: '', targetFields: [], triggerCondition: '', ...(props.main || {}) });
const subLocal = reactive<FormulaBlock>({ formula: '', targetFields: [], triggerCondition: '', ...(props.sub || {}) });
const triggerLocal = reactive<SubTrigger>({ triggerType: 'aggregate', formula: '', targetFields: [], ...(props.trigger || {}) });

const toArr = (v: any): string[] => {
  if (Array.isArray(v)) return v;
  if (typeof v === 'string' && v)
    return v
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
  return [];
};

const mainTargets = computed<string[]>({
  get: () => toArr(mainLocal.targetFields),
  set: (v) => {
    mainLocal.targetFields = v;
  }
});
const subTargets = computed<string[]>({
  get: () => toArr(subLocal.targetFields),
  set: (v) => {
    subLocal.targetFields = v;
  }
});
const triggerTargets = computed<string[]>({
  get: () => toArr(triggerLocal.targetFields),
  set: (v) => {
    triggerLocal.targetFields = v;
  }
});

watch(
  () => props.code,
  (v) => {
    codeEnabled.value = !!v;
    Object.assign(codeLocal, { autoGenerateRule: '', generateParams: { format: '' }, ...(v || {}) });
  },
  { deep: true }
);
watch(
  () => props.main,
  (v) => {
    mainEnabled.value = !!v;
    Object.assign(mainLocal, { formula: '', targetFields: [], triggerCondition: '', ...(v || {}) });
  },
  { deep: true }
);
watch(
  () => props.sub,
  (v) => {
    subEnabled.value = !!v;
    Object.assign(subLocal, { formula: '', targetFields: [], triggerCondition: '', ...(v || {}) });
  },
  { deep: true }
);
watch(
  () => props.trigger,
  (v) => {
    triggerEnabled.value = !!v;
    Object.assign(triggerLocal, { triggerType: 'aggregate', formula: '', targetFields: [], ...(v || {}) });
  },
  { deep: true }
);

watch([codeEnabled, mainEnabled, subEnabled, triggerEnabled], () => {
  emit('update:code', codeEnabled.value ? { ...codeLocal, generateParams: { ...(codeLocal.generateParams || {}) } } : null);
  emit('update:main', mainEnabled.value ? { ...mainLocal, targetFields: toArr(mainLocal.targetFields) } : null);
  emit('update:sub', subEnabled.value ? { ...subLocal, targetFields: toArr(subLocal.targetFields) } : null);
  emit('update:trigger', triggerEnabled.value ? { ...triggerLocal, targetFields: toArr(triggerLocal.targetFields) } : null);
});

watch(
  codeLocal,
  () => {
    if (codeEnabled.value) emit('update:code', { ...codeLocal, generateParams: { ...(codeLocal.generateParams || {}) } });
  },
  { deep: true }
);
watch(
  mainLocal,
  () => {
    if (mainEnabled.value) emit('update:main', { ...mainLocal, targetFields: toArr(mainLocal.targetFields) });
  },
  { deep: true }
);
watch(
  subLocal,
  () => {
    if (subEnabled.value) emit('update:sub', { ...subLocal, targetFields: toArr(subLocal.targetFields) });
  },
  { deep: true }
);
watch(
  triggerLocal,
  () => {
    if (triggerEnabled.value) emit('update:trigger', { ...triggerLocal, targetFields: toArr(triggerLocal.targetFields) });
  },
  { deep: true }
);
</script>

<style scoped>
.rule-card {
  margin-bottom: 10px;
}
.legacy-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}
.legacy-header {
  font-weight: 600;
}
</style>
