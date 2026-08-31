<template>
  <div style="display: flex; align-items: center; gap: 10px">
    <el-input
      :model-value="modelValue"
      :placeholder="placeholder"
      :disabled="autoGenFlag || disabled"
      style="flex: 1"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <el-switch
      v-model="autoGenFlag"
      inline-prompt
      active-color="#13ce66"
      active-text="自动"
      inactive-text="自动生成"
      :disabled="disabled"
      @change="onAutoGenChange"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 自动生成编码字段
 * <p>
 * 统一封装"编码输入框 + 自动生成开关"组合，替代 30+ 页面内联的 autoGenFlag 逻辑。
 * 开关打开时调用 previewCode(ruleCode) 预览编码并禁用输入；提交前调用 expose 的
 * applyAutoGen() 清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号。
 */
import { ref } from 'vue';
import { previewCode } from '@/api/system/autocode/rule';

const props = withDefaults(
  defineProps<{
    /** 编码值（v-model） */
    modelValue: string | null | undefined;
    /** 编码规则码（对应 previewCode 的 ruleCode） */
    ruleCode: string;
    /** 是否禁用（查看模式） */
    disabled?: boolean;
    /** 输入框占位提示 */
    placeholder?: string;
  }>(),
  {
    disabled: false,
    placeholder: '请输入编码'
  }
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | null | undefined): void;
  (e: 'auto-gen-change', flag: boolean): void;
}>();

/** 自动生成开关 */
const autoGenFlag = ref(false);

/** 开关变更：打开则预览编码，关闭则清空 */
function onAutoGenChange(flag: boolean) {
  if (flag) {
    previewCode(props.ruleCode).then((response: any) => {
      emit('update:modelValue', response);
    });
  } else {
    emit('update:modelValue', null);
  }
  emit('auto-gen-change', flag);
}

/** 提交前调用：自动生成开关打开时清空编码，由后端兜底生成，避免预览消耗流水号 */
function applyAutoGen() {
  if (autoGenFlag.value) {
    emit('update:modelValue', undefined);
  }
}

/** 复位开关状态（对话框关闭/新增时调用） */
function reset() {
  autoGenFlag.value = false;
}

defineExpose({ applyAutoGen, reset, autoGenFlag });
</script>
