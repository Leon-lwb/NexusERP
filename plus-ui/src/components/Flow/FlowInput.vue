<template>
  <el-input v-if="isTextType" :disabled="disabled" :placeholder="nm.columnComment" v-model="localValue" @change="commonComponent"></el-input>
  <el-input-number
    v-else-if="isIntType"
    :disabled="disabled"
    :placeholder="nm.columnComment"
    v-model="localValue"
    controls-position="right"
    @change="commonComponent"
  />
  <el-input-number
    v-else-if="isDecimalType"
    :disabled="disabled"
    :placeholder="nm.columnComment"
    v-model="localValue"
    :precision="2"
    controls-position="right"
    @change="commonComponent"
  />
</template>

<script lang="ts" setup>
import { ref, watch, onMounted, computed } from 'vue';

defineOptions({ name: 'FlowInput' });

const props = defineProps({
  nm: {
    type: Object,
    required: true
  },
  value: {
    type: [String, Number],
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:value', 'commonComponent']);

const localValue = ref<any>(null);

// javaType 归类必须覆盖模板/watch/输出三处使用同一口径，
// 否则会出现某类型（如 Integer/Double 大小写差异）不渲染任何控件的断层
const isIntType = computed(() => ['Long', 'Integer', 'int', 'long'].includes(String(props.nm?.javaType)));
const isDecimalType = computed(() => ['double', 'Double', 'float', 'Float', 'BigDecimal'].includes(String(props.nm?.javaType)));
const isTextType = computed(() => !isIntType.value && !isDecimalType.value);

// 增强watch处理
watch(
  () => props.value,
  (newVal) => {
    // 根据类型转换输入值
    if (isIntType.value) {
      localValue.value = Number(newVal) || 0;
    } else if (isDecimalType.value) {
      localValue.value = parseFloat(String(newVal)) || 0;
    } else {
      localValue.value = String(newVal || '');
    }
  }
);

const commonComponent = () => {
  // 输出时转换类型
  let outputValue: any = localValue.value;
  if (isIntType.value) {
    outputValue = Number(outputValue) || 0;
  } else if (props.nm?.javaType === 'BigDecimal') {
    outputValue = parseFloat(outputValue).toFixed(2);
  }
  emit('update:value', outputValue);
  emit('commonComponent', {
    ...props.nm,
    commonDataInfo: { [props.nm.columnName]: outputValue }
  });
};

onMounted(() => {
  localValue.value = props.value;
});
</script>

<style scoped></style>
