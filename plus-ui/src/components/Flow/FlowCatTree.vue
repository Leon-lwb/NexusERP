<template>
  <el-select v-model="localValue" :placeholder="tableTypeDesc" ref="selectTree" @change="commonComponent" class="setstyle">
    <el-option :key="nm.commonDataInfo" :value="nm.commonDataInfo" :label="tableTypeDesc" hidden />
    <el-tree :data="nm.commonCateDictCode" :props="defaultProps" @node-click="handleClickNode" node-key="id" class="setstyle" />
  </el-select>
</template>

<script lang="ts">
import { ref, watch, onMounted } from 'vue';

export default {
  name: 'nmCatTree',
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
    // 选中节点的显示文案本地状态：原实现直接写入 nm.tableTypeDesc（变更 prop），现改为本地持有并同步
    const tableTypeDesc = ref(props.nm.tableTypeDesc);

    // 监听 props.value 的变化，确保 localValue 与 props.value 保持同步
    watch(
      () => props.value,
      (newValue) => {
        localValue.value = newValue;
      }
    );

    // 监听 props.nm.tableTypeDesc 的变化，保持本地显示文案与配置同步
    watch(
      () => props.nm.tableTypeDesc,
      (newValue) => {
        tableTypeDesc.value = newValue;
      }
    );

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
      tableTypeDesc.value = data.label;
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
      tableTypeDesc,
      commonComponent,
      handleClickNode
    };
  }
};
</script>

<!-- el-tree 渲染在 el-select 的 teleport 弹层内、不在本组件子树，
     scoped/:deep 均无法命中，故保持全局；靠 .setstyle 自定义类名限定影响面 -->
<style lang="scss">
.setstyle {
  min-height: 200px;
  padding: 0 !important;
  margin: 0;
  overflow: auto;
  cursor: default !important;
}
</style>
