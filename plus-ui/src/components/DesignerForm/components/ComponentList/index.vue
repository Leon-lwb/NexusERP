<template>
  <div class="components-panel">
    <div class="panel-header">
      <h3>组件列表</h3>
    </div>
    <div class="components-list">
      <el-scrollbar>
        <BasicComponents @dragstart="handleDragStart" />
        <LayoutComponents @dragstart="handleDragStart" />
        <AdvancedComponents @dragstart="handleDragStart" />
      </el-scrollbar>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, markRaw } from 'vue'
import BasicComponents from './BasicComponents.vue'
import LayoutComponents from './LayoutComponents.vue'
import AdvancedComponents from './AdvancedComponents.vue'
import { Plus } from '@element-plus/icons-vue'

const emit = defineEmits(['dragstart'])

const handleDragStart = (e: DragEvent, item: any) => {
  emit('dragstart', e, item)
}

const advancedComponents = ref([
  {
    label: '子表',
    type: 'sub-table',
    props: {
      placeholder: '子表'
    },
    icon: markRaw(Plus),
    subTableColumns: [],
    dbInfo: {
      tableName: '',
      columnName: '',
      columnType: '',
      columnComment: '',
      isNullable: 'YES'
    }
  }
])
</script>

<style lang="scss" scoped>
.components-panel {
  width: 250px;
  margin-right: 10px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: #fff;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);

  .panel-header {
    height: 40px;
    padding: 0 15px;
    border-bottom: 1px solid #dcdfe6;
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-shrink: 0;

    h3 {
      margin: 0;
      font-size: 14px;
      font-weight: 500;
    }
  }

  .components-list {
    flex: 1;
    overflow: hidden;
    padding: 10px;

    .el-scrollbar {
      height: 100%;
    }
  }
}
</style> 