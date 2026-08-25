<template>
  <el-select v-model="localValue" clearable :placeholder="nm.tableTypeDesc" @change="commonComponent">
    <el-option v-for="(itemCDC, cdc) in nm.commonLinkDownCode" :key="cdc" :label="itemCDC.name" :value="itemCDC" />
  </el-select>
</template>

<script lang="ts">
import { ref, watch, onMounted } from 'vue';

export default {
  name: 'FlowLinkDown',
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

    // 处理 change 事件：原实现直接回写 nm 的 commonDataInfo/commonLinkDownCodeChild（变更 prop），
    // 现改为构建携带最新字段的 nm 副本随事件发出，事件负载与原实现保持一致
    const commonComponent = () => {
      // value 声明为 [String, Object]：运行时实际绑定的是选项对象（含 id/children），非对象时保持原行为取 undefined
      const val = localValue.value as Record<string, any> | null | undefined;
      const updatedFlow = val
        ? { ...props.nm, commonDataInfo: val.id, commonLinkDownCodeChild: val.children || [] }
        : { ...props.nm, commonDataInfo: null, commonLinkDownCodeChild: [] };
      emit('commonComponent', updatedFlow);
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

<style scoped></style>
