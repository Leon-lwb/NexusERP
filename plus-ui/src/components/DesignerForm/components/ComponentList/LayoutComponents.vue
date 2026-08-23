<template>
  <div class="component-group">
    <div class="group-title">布局组件</div>
    <div class="component-items">
      <div v-for="(item, index) in layoutComponents" :key="index" class="component-item" draggable="true" @dragstart="handleDragStart($event, item)">
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { markRaw } from 'vue';
import { Grid, Menu, Tickets, Collection } from '@element-plus/icons-vue';
import type { ComponentDefinition } from '../../types';

const emit = defineEmits(['dragstart']);

const layoutComponents = ref<ComponentDefinition[]>([
  {
    label: '栅格布局',
    type: 'grid',
    icon: markRaw(Grid),
    component: markRaw(Grid),
    props: {
      gutter: 20,
      justify: 'start',
      align: 'top'
    }
  },
  {
    label: '表格布局',
    type: 'table',
    icon: markRaw(Menu),
    component: markRaw(Menu),
    props: {
      border: true,
      stripe: true
    }
  },
  {
    label: '标签页',
    type: 'tabs',
    icon: markRaw(Tickets),
    component: markRaw(Tickets),
    props: {
      type: 'card'
    }
  },
  {
    label: '卡片',
    type: 'card',
    icon: markRaw(Collection),
    component: markRaw(Collection),
    props: {
      header: '卡片标题',
      shadow: 'hover'
    }
  }
]);

const handleDragStart = (e: DragEvent, item: ComponentDefinition) => {
  emit('dragstart', e, item);
};
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
