<template>
  <div>
    <template v-for="(item, index) in options">
      <template v-if="values.includes(String(item.id ?? item.value ?? ''))">
        <el-tag :disable-transitions="true" :key="(item.id ?? item.value ?? '') + ''" :index="index">
          {{ (item.name ?? item.label ?? '') + ' ' }}
        </el-tag>
      </template>
    </template>
    <template v-if="unmatch && showValue">
      {{ unmatchArray }}
    </template>
  </div>
</template>

<script setup lang="ts">
import { propTypes } from '@/utils/propTypes';

const props = defineProps({
  // 数据
  options: {
    type: Array as PropType<DictDataOption[]>,
    default: null
  },
  // 当前的值
  value: [Number, String, Array] as PropType<number | string | Array<number | string>>,
  // 当未找到匹配的数据时，显示value
  showValue: propTypes.bool.def(true),
  separator: propTypes.string.def(',')
});
const values = computed(() => {
  if (props.value === '' || props.value === null || typeof props.value === 'undefined') return [];
  // 将所有值转换为字符串，确保与 options 中的 id 类型一致
  const result = Array.isArray(props.value) ? props.value.map((item) => String(item)) : String(props.value).split(props.separator);
  return result;
});

const unmatch = computed(() => {
  if (props.options?.length == 0 || props.value === '' || props.value === null || typeof props.value === 'undefined') return false;
  // 检查是否有未匹配的项
  for (const item of values.value) {
    if (!props.options.some((v) => String(v.id ?? v.value ?? '') === item)) {
      return true; // 如果有未匹配项，返回true
    }
  }
  return false; // 所有项都匹配
});

const unmatchArray = computed(() => {
  // 记录未匹配的项
  const itemUnmatchArray: Array<string | number> = [];
  if (props.value !== '' && props.value !== null && typeof props.value !== 'undefined') {
    values.value.forEach((item) => {
      if (!props.options.some((v) => String(v.id ?? v.value ?? '') === item)) {
        itemUnmatchArray.push(item);
      }
    });
  }
  // 没有value不显示
  return handleArray(itemUnmatchArray);
});

const handleArray = (array: Array<string | number>) => {
  if (array.length === 0) return '';
  return array.reduce((pre, cur) => {
    return pre + ' ' + cur;
  });
};
</script>

<style scoped>
.el-tag + .el-tag {
  margin-left: 10px;
}
</style>
