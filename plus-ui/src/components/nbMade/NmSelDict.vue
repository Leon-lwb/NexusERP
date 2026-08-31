<template>
  <el-select :disabled="disabled" v-model="localValue" size="large" style="width: 240px" :placeholder="nm.columnComment" @change="commonComponent">
    <el-option v-for="item in options" :key="item.id" :label="item.name" :value="item.id" />
  </el-select>
</template>

<script lang="ts" setup>
import { computed, ref, watch, onMounted } from 'vue';

defineOptions({ name: 'NmSelDict' });

const props = defineProps({
  nm: {
    type: Object,
    required: true
  },
  value: {
    type: [String, Number],
    required: false
  },
  disabled: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:value', 'commonComponent']);

const localValue = ref(props.value);

// runtime override for dict cascade
const options = computed<any[]>(() => {
  const nm: any = props.nm as any;
  return (Array.isArray(nm?.runtimeDictCode) ? nm.runtimeDictCode : nm?.commonDictCode) || [];
});

// 监听 props.value 的变化，确保 localValue 与 props.value 保持同步
watch(
  () => props.value,
  (newValue) => {
    localValue.value = newValue;
  }
);

// 监听 localValue 的变化，触发 update:value 事件
watch(localValue, (newValue) => {
  emit('update:value', newValue);
});

// 处理 change 事件
const commonComponent = () => {
  emit('commonComponent', { ...props.nm, commonDataInfo: localValue.value });
};

// 初始化时设置 localValue
onMounted(() => {
  localValue.value = props.value;
});
</script>

<style scoped></style>
