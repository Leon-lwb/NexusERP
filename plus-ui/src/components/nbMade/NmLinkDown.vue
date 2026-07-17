<template>
  <el-select
    v-model="localValue"
    clearable
    :placeholder="nm.tableTypeDesc"
    @change="commonComponent"
  >
    <el-option
      v-for="(itemCDC, cdc) in nm.commonLinkDownCode"
      :key="cdc"
      :label="itemCDC.name"
      :value="itemCDC"
    />
  </el-select>
</template>

<script>
import { ref, watch, onMounted } from 'vue';

export default {
  name: "NmLinkDown",
  props: {
    nm: {
      type: Object,
      required: true
    },
    value: {
      type: [String, Object],
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
    const commonComponent = (event) => {
      if (localValue.value) {
        props.nm.commonDataInfo = localValue.value.id;
        props.nm.commonLinkDownCodeChild = localValue.value.children || [];
      } else {
        props.nm.commonDataInfo = null;
        props.nm.commonLinkDownCodeChild = [];
      }
      emit('commonComponent', { ...props.nm });
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
