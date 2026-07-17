<template>
  <el-select
    v-model="localValue"
    :placeholder="nm.tableTypeDesc"
    ref="selectTree"
    @change="commonComponent"
    class="setstyle"
  >
    <el-option
      :key="nm.commonDataInfo"
      :value="nm.commonDataInfo"
      :label="nm.tableTypeDesc"
      hidden
    />
    <el-tree
      :data="nm.commonCateDictCode"
      :props="defaultProps"
      @node-click="handleClickNode"
      node-key="id"
      class="setstyle"
    />
  </el-select>
</template>

<script>
import { ref, watch, onMounted } from 'vue';

export default {
  name: "nmCatTree",
  props: {
    nm: {
      type: Object,
      required: true
    },
    value: {
      type: [String, Number],
      required: false
    },
    defaultProps: {
      type: Object,
      default: () => ({
        children: 'children',
        label: 'label',
        id: 'id'
      })
    }
  },
  setup(props, { emit }) {
    const localValue = ref(props.value);
    const selectTree = ref(null);

    // 监听 props.value 的变化，确保 localValue 与 props.value 保持同步
    watch(() => props.value, (newValue) => {
      localValue.value = newValue;
    });

    // 监听 localValue 的变化，触发 update:value 事件
    watch(localValue, (newValue) => {
      emit('update:value', newValue);
    });

    // 处理 change 事件
    const commonComponent = (nmInfo) => {
      localValue.value = nmInfo.toString();
      emit('commonComponent', { ...props.nm, commonDataInfo: localValue.value });
    };

    // 处理节点点击事件
    const handleClickNode = (data) => {
      localValue.value = data.id;
      props.nm.tableTypeDesc = data.label;
      // 选择器执行完成后，使其失去焦点隐藏下拉框的效果
      selectTree.value.blur();
    };

    // 初始化时设置 localValue
    onMounted(() => {
      localValue.value = props.value;
    });

    return {
      localValue,
      selectTree,
      commonComponent,
      handleClickNode
    };
  }
};
</script>

<style lang="scss">
.setstyle {
  min-height: 200px;
  padding: 0 !important;
  margin: 0;
  overflow: auto;
  cursor: default !important;
}
</style>
