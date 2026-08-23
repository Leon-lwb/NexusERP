<template>
  <el-date-picker
    v-model="localValue"
    type="datetime"
    :disabled="disabled"
    :placeholder="nm.tableTypeDesc"
    value-format="YYYY-MM-DD HH:mm:ss"
    @change="commonmomponent"
  />
</template>

<script>
import { ref, watch, onMounted } from 'vue';

export default {
  name: 'nmDateTime',
  props: {
    nm: {
      type: Object,
      required: true
    },
    value: {
      type: String,
      required: false
    },
    disabled: {
      type: Boolean,
      default: false
    }
  },
  setup(props, { emit }) {
    const localValue = ref(props.value);

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
    const commonmomponent = (nmInfo) => {
      localValue.value = nmInfo;
      emit('commonmomponent', { ...props.nm, commonDataInfo: localValue.value });
    };

    // 初始化时设置 localValue
    onMounted(() => {
      localValue.value = props.value;
    });

    return {
      localValue,
      commonmomponent
    };
  }
};
</script>

<style scoped></style>
