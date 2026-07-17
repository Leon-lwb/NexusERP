<template>
  <el-input
    v-if="nm.javaType == 'String'"
    :disabled="disabled"
    :placeholder="nm.columnComment"
    v-model="localValue"
    @change="commonComponent"
  ></el-input>
  <el-input-number
    v-else-if="nm.javaType == 'Long'"
    :disabled="disabled"
    :placeholder="nm.columnComment"
    v-model="localValue"
    controls-position="right"
    @change="commonComponent"
  />
  <el-input-number
    v-else-if="nm.javaType == 'double' || nm.javaType == 'BigDecimal'"
    :disabled="disabled"
    :placeholder="nm.columnComment"
    v-model="localValue"
    :precision="2"
    controls-position="right"
    @change="commonComponent"
  />
</template>

<script lang="ts" setup>
import { ref, watch, onMounted } from 'vue';

defineOptions({ name: 'NmInput' });

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

const localValue = ref<any>(null)

// 增强watch处理
watch(() => props.value, (newVal) => {
  // 根据类型转换输入值
  switch(props.nm.javaType) {
    case 'Long':
    case 'Integer':
      localValue.value = Number(newVal) || 0
      break
    case 'BigDecimal':
    case 'Double':
      localValue.value = parseFloat(newVal) || 0
      break
    default:
      localValue.value = String(newVal || '')
  }
})

const commonComponent = () => {
  // 输出时转换类型
  let outputValue: any = localValue.value
  switch(props.nm.javaType) {
    case 'Long':
    case 'Integer':
      outputValue = Number(outputValue) || 0
      break
    case 'BigDecimal':
      outputValue = parseFloat(outputValue).toFixed(2)
      break
  }
  emit('update:value', outputValue)
  emit('commonComponent', {
    ...props.nm,
    commonDataInfo: { [props.nm.columnName]: outputValue }
  })
}

onMounted(() => {
  localValue.value = props.value;
});
</script>

<style scoped>
</style>
