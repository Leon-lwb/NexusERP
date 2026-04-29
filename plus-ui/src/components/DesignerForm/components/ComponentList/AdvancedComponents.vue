<template>
  <div class="component-group">
    <div class="group-title">高级组件</div>
    <div class="component-items">
      <div 
        v-for="(item, index) in advancedComponents" 
        :key="index"
        class="component-item" 
        draggable="true"
        @dragstart="handleDragStart($event, item)"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { markRaw } from 'vue'
import {
  Plus,
  OfficeBuilding,
  User
} from '@element-plus/icons-vue'
import type { ComponentDefinition } from '../../types'

const emit = defineEmits(['dragstart'])

const advancedComponents = ref<ComponentDefinition[]>([
  {
    label: '子表',
    type: 'sub-table',
    icon: markRaw(Plus),
    props: {
      placeholder: '子表'
    },
    subTableColumns: [],
    dbInfo: {
      tableName: '',
      columnName: '',
      columnType: '',
      columnComment: '',
      isNullable: 'YES'
    }
  },
  {
    label: '部门选择',
    type: 'dept-select',
    icon: markRaw(OfficeBuilding),
    component: markRaw(OfficeBuilding),
    props: { 
      placeholder: '请选择部门',
      multiple: false,
      clearable: true
    }
  },
  {
    label: '人员选择',
    type: 'user-select',
    icon: markRaw(User),
    component: markRaw(User),
    props: { 
      placeholder: '请选择人员',
      multiple: false,
      clearable: true
    }
  }
])

const handleDragStart = (e: DragEvent, item: ComponentDefinition) => {
  emit('dragstart', e, item)
}
</script>

<style lang="scss" scoped>
.component-group {
  margin-bottom: 20px;

  .group-title {
    font-size: 14px;
    color: #909399;
    margin-bottom: 10px;
    padding-left: 10px;
    border-left: 3px solid #409eff;
  }

  .component-items {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
    padding: 0 10px;

    .component-item {
      display: flex;
      align-items: center;
      padding: 8px;
      border: 1px solid #dcdfe6;
      border-radius: 4px;
      cursor: pointer;
      transition: all 0.3s;
      background-color: #fff;

      &:hover {
        border-color: #409eff;
        color: #409eff;
      }

      .el-icon {
        margin-right: 8px;
      }

      span {
        font-size: 12px;
      }
    }
  }
}
</style> 