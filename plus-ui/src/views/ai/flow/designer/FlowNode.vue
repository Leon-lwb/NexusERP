<script setup lang="ts">
import { computed } from 'vue';
import { Handle, Position } from '@vue-flow/core';
import type { NodeProps } from '@vue-flow/core';
import { getNodeTypeConfig } from '../nodeTypes';

/**
 * 流程画布节点组件（slot 注册，非 nodeTypes 对象）
 * <p>运行态高亮：RUNNING 旋转渐变边框 / SUCCEEDED 绿框 / FAILED 红框 / SKIPPED 灰化</p>
 */
interface FlowNodeData {
  label: string;
  type: string;
  status?: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED';
  onError?: 'STOP' | 'CONTINUE' | 'ERROR_PORT';
}

const props = defineProps<NodeProps<FlowNodeData>>();

const typeConfig = computed(() => getNodeTypeConfig(props.data.type));
const nodeColor = computed(() => typeConfig.value?.color ?? '#909399');

const statusClass = computed(() => {
  switch (props.data.status) {
    case 'RUNNING':
      return 'flow-node--running';
    case 'SUCCEEDED':
      return 'flow-node--succeeded';
    case 'FAILED':
      return 'flow-node--failed';
    case 'SKIPPED':
      return 'flow-node--skipped';
    default:
      return '';
  }
});
</script>

<template>
  <div class="flow-node" :class="[statusClass, { 'is-selected': props.selected }]">
    <!-- 合并节点：多输入端口（inputs/main/0, /1... 按入边数） -->
    <template v-if="props.data.type === 'merge'">
      <Handle
        v-for="i in 3"
        :key="'in' + i"
        :id="'inputs/main/' + (i - 1)"
        type="target"
        :position="Position.Left"
        class="flow-node__handle"
        :style="{ top: 20 + i * 20 + '%' }"
      />
    </template>
    <!-- 普通节点：单输入端口 -->
    <Handle v-else id="inputs/main/0" type="target" :position="Position.Left" class="flow-node__handle" />
    <!-- 条件节点：双输出端口 true/false -->
    <template v-if="props.data.type === 'if'">
      <Handle
        id="outputs/if/true"
        type="source"
        :position="Position.Right"
        class="flow-node__handle flow-node__handle--true"
        :style="{ top: '35%' }"
      />
      <Handle
        id="outputs/if/false"
        type="source"
        :position="Position.Right"
        class="flow-node__handle flow-node__handle--false"
        :style="{ top: '65%' }"
      />
    </template>
    <!-- ERROR_PORT 节点：main + error 双输出端口 -->
    <template v-else-if="props.data.onError === 'ERROR_PORT'">
      <Handle id="outputs/main/0" type="source" :position="Position.Right" class="flow-node__handle" :style="{ top: '35%' }" />
      <Handle
        id="outputs/error/0"
        type="source"
        :position="Position.Right"
        class="flow-node__handle flow-node__handle--error"
        :style="{ top: '65%' }"
      />
    </template>
    <!-- 循环节点：loop/main + loop/done 双输出端口 -->
    <template v-else-if="props.data.type === 'split'">
      <Handle
        id="outputs/loop/main"
        type="source"
        :position="Position.Right"
        class="flow-node__handle flow-node__handle--loop"
        :style="{ top: '35%' }"
      />
      <Handle
        id="outputs/loop/done"
        type="source"
        :position="Position.Right"
        class="flow-node__handle flow-node__handle--done"
        :style="{ top: '65%' }"
      />
    </template>
    <!-- 普通节点：单输出端口 -->
    <Handle v-else id="outputs/main/0" type="source" :position="Position.Right" class="flow-node__handle" />

    <div class="flow-node__body" :style="{ borderColor: nodeColor }">
      <div class="flow-node__icon">
        <component :is="typeConfig?.icon" :style="{ color: nodeColor }" />
      </div>
      <div class="flow-node__text">
        <div class="flow-node__label">{{ props.data.label || typeConfig?.label || props.id }}</div>
        <div class="flow-node__type">{{ typeConfig?.label ?? props.data.type }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.flow-node {
  width: 160px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  transition: box-shadow 0.2s;
}

.flow-node.is-selected {
  box-shadow:
    0 0 0 2px #409eff,
    0 2px 8px rgba(0, 0, 0, 0.12);
}

.flow-node__body {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-left: 3px solid;
  border-radius: 8px;
}

.flow-node__icon {
  display: flex;
  align-items: center;
  font-size: 18px;
}

.flow-node__text {
  min-width: 0;
}

.flow-node__label {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.flow-node__type {
  font-size: 11px;
  color: #909399;
}

.flow-node__handle {
  width: 10px !important;
  height: 10px !important;
  background: #fff !important;
  border: 2px solid #409eff !important;
}

.flow-node__handle--true {
  border-color: #52c41a !important;
}

.flow-node__handle--false {
  border-color: #fa541c !important;
}

.flow-node__handle--error {
  border-color: #fa541c !important;
}

.flow-node__handle--loop {
  border-color: #2f54eb !important;
}

.flow-node__handle--done {
  border-color: #52c41a !important;
}

/* 运行中：旋转渐变边框（对齐 n8n CanvasNodeDefault） */
.flow-node--running .flow-node__body {
  animation: flow-node-border-rotate 1.5s linear infinite;
}

@keyframes flow-node-border-rotate {
  0% {
    filter: hue-rotate(0deg);
  }
  100% {
    filter: hue-rotate(360deg);
  }
}

/* 成功 */
.flow-node--succeeded .flow-node__body {
  border-left-color: #52c41a !important;
}

/* 失败 */
.flow-node--failed .flow-node__body {
  border-left-color: #fa541c !important;
  background: #fff1f0;
}

/* 跳过 */
.flow-node--skipped {
  opacity: 0.5;
  filter: grayscale(1);
}
</style>
