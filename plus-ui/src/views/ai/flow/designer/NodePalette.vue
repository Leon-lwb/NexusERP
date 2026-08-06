<script setup lang="ts">
import { NODE_TYPE_CONFIGS } from '../nodeTypes';

/**
 * 左侧节点面板：从注册表渲染可拖拽卡片（HTML5 draggable）
 * <p>拖拽时在 dataTransfer 中携带节点类型，FlowCanvas 的 drop 事件消费</p>
 */
function onDragStart(e: DragEvent, type: string) {
  if (e.dataTransfer) {
    e.dataTransfer.setData('application/x-flow-node-type', type);
    e.dataTransfer.effectAllowed = 'copy';
  }
}
</script>

<template>
  <div class="node-palette">
    <div class="node-palette__title">节点</div>
    <div v-for="cfg in NODE_TYPE_CONFIGS" :key="cfg.type" class="node-palette__item" draggable="true" @dragstart="onDragStart($event, cfg.type)">
      <el-icon :style="{ color: cfg.color }" class="node-palette__icon">
        <component :is="cfg.icon" />
      </el-icon>
      <span>{{ cfg.label }}</span>
    </div>
    <div class="node-palette__hint">拖拽节点到画布</div>
  </div>
</template>

<style scoped>
.node-palette {
  width: 120px;
  border-right: 1px solid #e4e7ed;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  padding: 8px;
  gap: 4px;
  overflow-y: auto;
}

.node-palette__title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  padding: 4px 8px;
}

.node-palette__item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
  cursor: grab;
  font-size: 13px;
  color: #303133;
  transition: all 0.2s;
}

.node-palette__item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.node-palette__item:active {
  cursor: grabbing;
}

.node-palette__icon {
  font-size: 16px;
}

.node-palette__hint {
  margin-top: auto;
  font-size: 11px;
  color: #c0c4cc;
  text-align: center;
  padding: 8px 0;
}
</style>
