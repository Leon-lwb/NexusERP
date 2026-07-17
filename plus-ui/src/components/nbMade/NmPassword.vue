<template>
  <el-input
    type="password"
    :placeholder="nm.tableTypeDesc"
    v-model="localValue"
    autocomplete="off"
    @change="commonComponent"
  />
</template>

<script>
import { ref, watch, onMounted } from 'vue';

export default {
  name: "NmPassword",
  props: {
    nm: {
      type: Object,
      required: true
    },
    value: {
      type: String,
      required: false
    }
  },
  setup(props, { emit }) {
    const localValue = ref(props.value);

    // 监听 props.value 的变化，确保 localValue 与 props.value 保持同步
    watch(() => props.value, (newValue) => {
      localValue.value = newValue;
    });

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

    return {
      localValue,
      commonComponent
    };
  }
};
</script>

<style scoped>
</style>
