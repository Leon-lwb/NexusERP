<template>
  <el-radio-group :disabled="disabled" v-model="localValue" @change="commonComponent">
    <el-radio v-for="(item, index) in options" :key="index" :label="item.id">
      {{ item.name }}
    </el-radio>
  </el-radio-group>
</template>

<script lang="ts">
import { computed, ref, watch, onMounted } from 'vue';

export default {
  name: 'FlowRadio',
  props: {
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
  },
  setup(props, { emit }) {
    const localValue = ref(props.value);

    const options = computed(() => {
      const nm = props.nm || {};
      return (Array.isArray(nm.runtimeDictCode) ? nm.runtimeDictCode : nm.commonDictCode) || [];
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
    const commonComponent = (event) => {
      emit('commonComponent', { ...props.nm, commonDataInfo: localValue.value });
    };

    // 初始化时设置 localValue
    onMounted(() => {
      localValue.value = props.value;
    });

    return {
      localValue,
      options,
      commonComponent
    };
  }
};
</script>

<style scoped></style>
