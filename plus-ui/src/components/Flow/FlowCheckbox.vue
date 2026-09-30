<template>
  <el-checkbox :disabled="disabled" v-model="localValue" :dictCode="dictcode" @change="commonComponent" />
</template>

<script lang="ts">
import { ElCheckbox } from 'element-plus';
import { ref, watch, onMounted } from 'vue';

export default {
  name: 'nmCheckbox',
  components: {
    ElCheckbox
  },
  props: {
    nm: {
      type: Object,
      required: true
    },
    value: {
      type: [String, Boolean],
      required: false
    },
    disabled: {
      type: Boolean,
      default: false
    }
  },
  setup(props, { emit }) {
    const dictcode = ref(props.nm.dictField);
    const localValue = ref(props.value);

    /** 监听 props.value 的变化，确保 localValue 与 props.value 保持同步 */
    watch(
      () => props.value,
      (newValue) => {
        localValue.value = newValue;
      }
    );

    /** 监听 localValue 的变化，触发 update:value 事件 */
    watch(localValue, (newValue) => {
      emit('update:value', newValue);
    });

    /** 处理 change 事件 */
    const commonComponent = (event) => {
      emit('commonComponent', { ...props.nm, commonDataInfo: localValue.value });
    };

    /** 初始化时设置 localValue 和 dictcode */
    onMounted(() => {
      localValue.value = props.value;
      dictcode.value = props.nm.dictField;
    });

    return {
      dictcode,
      localValue,
      commonComponent
    };
  }
};
</script>

<style scoped></style>
