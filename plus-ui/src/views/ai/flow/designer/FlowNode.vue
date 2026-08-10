<script setup lang="ts">
import { computed } from 'vue';
import { Handle, Position } from '@vue-flow/core';
import type { NodeProps } from '@vue-flow/core';
import { Aim, CopyDocument, Delete, VideoPlay } from '@element-plus/icons-vue';
import { getNodeTypeConfig } from '../nodeTypes';
import { useFlowStore } from './useFlowStore';

/**
 * 流程画布节点组件（slot 注册，非 nodeTypes 对象）
 * <p>运行态高亮：RUNNING 旋转渐变边框 / SUCCEEDED 绿框 / FAILED 红框 / SKIPPED 灰化
 * 底部展示运行输出摘要（SSE outputSummary，失败时显示错误）</p>
 */
interface FlowNodeData {
  label: string;
  type: string;
  status?: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED';
  onError?: 'STOP' | 'CONTINUE' | 'ERROR_PORT';
}

const props = defineProps<NodeProps<FlowNodeData>>();
const store = useFlowStore();

const typeConfig = computed(() => getNodeTypeConfig(props.data.type));
const nodeColor = computed(() => typeConfig.value?.color ?? '#909399');

/** 节点运行摘要（输出/错误文本） */
const summary = computed(() => store.nodeSummaryMap[props.id] ?? '');

/** merge 节点输入端口数（按实际入边数，至少 1） */
const mergeInputCount = computed(() => {
  const count = store.edges.filter((e) => e.target === props.id).length;
  return Math.max(1, count);
});

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

/** 路径高亮 class（hover 时：上游蓝框 / 下游绿框 / 自身加强 / 无关调暗） */
const pathClass = computed(() => {
  switch (pathRole.value) {
    case 'upstream':
      return 'flow-node--path-up';
    case 'downstream':
      return 'flow-node--path-down';
    case 'self':
      return 'flow-node--path-self';
    case 'dimmed':
      return 'flow-node--path-dim';
    default:
      return '';
  }
});

/** 时间线重放：是否当前播放节点（显示播放徽标） */
const isPlaying = computed(() => store.playingNodeId === props.id);

/**
 * 上下游路径高亮角色（hover 节点时）：
 * self=hover 目标 / upstream=上游链 / downstream=下游链 / dimmed=无关（调暗）
 */
const pathRole = computed<'self' | 'upstream' | 'downstream' | 'dimmed' | 'none'>(() => {
  const hoverId = store.hoverNodeId;
  if (!hoverId || hoverId === props.id) return hoverId === props.id ? 'self' : 'none';
  const upstream = store.getUpstreamIds(hoverId);
  if (upstream.has(props.id)) return 'upstream';
  const downstream = store.getDownstreamIds(hoverId);
  if (downstream.has(props.id)) return 'downstream';
  return 'dimmed';
});

/** hover 浮动工具栏（事件总线 → FlowCanvas/designer 处理：测试/复制/删除/聚焦） */
function toolbarAction(action: 'test' | 'duplicate' | 'delete' | 'focus') {
  window.dispatchEvent(new CustomEvent('flow-node-action', { detail: { nodeId: props.id, action } }));
}
</script>

<template>
  <div class="flow-node" :class="[statusClass, pathClass, { 'is-selected': props.selected }]">
    <!-- 合并节点：多输入端口（按实际入边数动态渲染，带序号标签） -->
    <template v-if="props.data.type === 'merge'">
      <template v-for="i in mergeInputCount" :key="'in' + i">
        <Handle
          :id="'inputs/main/' + (i - 1)"
          type="target"
          :position="Position.Left"
          class="flow-node__handle"
          :style="{ top: 20 + i * 20 + '%' }"
        />
        <span class="flow-node__port-label flow-node__port-label--left" :style="{ top: 20 + i * 20 + '%' }">{{ i }}</span>
      </template>
    </template>
    <!-- 普通节点：单输入端口 -->
    <Handle v-else id="inputs/main/0" type="target" :position="Position.Left" class="flow-node__handle" />
    <!-- 条件节点：双输出端口 true/false（带文字标签） -->
    <template v-if="props.data.type === 'if'">
      <Handle
        id="outputs/if/true"
        type="source"
        :position="Position.Right"
        class="flow-node__handle flow-node__handle--true"
        :style="{ top: '35%' }"
      />
      <span class="flow-node__port-label flow-node__port-label--right flow-node__port-label--true" :style="{ top: '35%' }">true</span>
      <Handle
        id="outputs/if/false"
        type="source"
        :position="Position.Right"
        class="flow-node__handle flow-node__handle--false"
        :style="{ top: '65%' }"
      />
      <span class="flow-node__port-label flow-node__port-label--right flow-node__port-label--false" :style="{ top: '65%' }">false</span>
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
      <span class="flow-node__port-label flow-node__port-label--right flow-node__port-label--false" :style="{ top: '65%' }">错误</span>
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
      <span class="flow-node__port-label flow-node__port-label--right flow-node__port-label--loop" :style="{ top: '35%' }">loop</span>
      <Handle
        id="outputs/loop/done"
        type="source"
        :position="Position.Right"
        class="flow-node__handle flow-node__handle--done"
        :style="{ top: '65%' }"
      />
      <span class="flow-node__port-label flow-node__port-label--right flow-node__port-label--done" :style="{ top: '65%' }">done</span>
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
    <div v-if="summary" class="flow-node__summary" :class="{ 'is-error': props.data.status === 'FAILED' }" :title="summary">
      {{ summary }}
    </div>
    <!-- 时间线重放：当前播放节点徽标（脉冲圆点 + 播放中） -->
    <div v-if="isPlaying" class="flow-node__playing">
      <span class="flow-node__playing-dot"></span>
      <span class="flow-node__playing-text">播放中</span>
    </div>
    <!-- hover 浮动工具栏（对齐 n8n CanvasNodeToolbar） -->
    <div class="flow-node__toolbar">
      <el-tooltip content="测试此节点" placement="top" :show-after="300">
        <el-icon class="flow-node__toolbar-btn" @click.stop="toolbarAction('test')"><VideoPlay /></el-icon>
      </el-tooltip>
      <el-tooltip content="复制节点" placement="top" :show-after="300">
        <el-icon class="flow-node__toolbar-btn" @click.stop="toolbarAction('duplicate')"><CopyDocument /></el-icon>
      </el-tooltip>
      <el-tooltip content="聚焦节点" placement="top" :show-after="300">
        <el-icon class="flow-node__toolbar-btn" @click.stop="toolbarAction('focus')"><Aim /></el-icon>
      </el-tooltip>
      <el-tooltip content="删除节点" placement="top" :show-after="300">
        <el-icon class="flow-node__toolbar-btn is-danger" @click.stop="toolbarAction('delete')"><Delete /></el-icon>
      </el-tooltip>
    </div>
  </div>
</template>

<style scoped>
.flow-node {
  position: relative;
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

/* 端口文字标签（对齐 n8n 端口命名） */
.flow-node__port-label {
  position: absolute;
  font-size: 10px;
  line-height: 12px;
  transform: translateY(-50%);
  pointer-events: none;
  user-select: none;
  white-space: nowrap;
}

.flow-node__port-label--left {
  left: -34px;
  color: #909399;
}

.flow-node__port-label--right {
  right: -40px;
  font-weight: 600;
}

.flow-node__port-label--true {
  color: #52c41a;
}

.flow-node__port-label--false {
  color: #fa541c;
}

.flow-node__port-label--loop {
  color: #2f54eb;
}

.flow-node__port-label--done {
  color: #52c41a;
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

/* ==================== 上下游路径高亮（hover 节点时） ==================== */

.flow-node--path-dim {
  opacity: 0.28;
  filter: grayscale(0.5);
}

.flow-node--path-up {
  box-shadow:
    0 0 0 2px #409eff,
    0 2px 8px rgba(64, 158, 255, 0.25);
}

.flow-node--path-down {
  box-shadow:
    0 0 0 2px #52c41a,
    0 2px 8px rgba(82, 196, 26, 0.25);
}

.flow-node--path-self {
  box-shadow:
    0 0 0 2px #409eff,
    0 0 0 5px rgba(64, 158, 255, 0.15),
    0 2px 8px rgba(64, 158, 255, 0.35);
}

/* ==================== hover 浮动工具栏（对齐 n8n CanvasNodeToolbar） ==================== */

.flow-node__toolbar {
  position: absolute;
  top: -14px;
  left: 50%;
  transform: translate(-50%, -100%);
  display: none;
  align-items: center;
  gap: 2px;
  padding: 3px 6px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  z-index: 20;
}

.flow-node:hover .flow-node__toolbar {
  display: flex;
}

.flow-node__toolbar-btn {
  font-size: 14px;
  color: #606266;
  cursor: pointer;
  padding: 3px;
  border-radius: 4px;
}

.flow-node__toolbar-btn:hover {
  background: #f0f7ff;
  color: #409eff;
}

.flow-node__toolbar-btn.is-danger:hover {
  background: #fef0f0;
  color: #f56c6c;
}

/* 运行摘要（底部小字） */
.flow-node__summary {
  font-size: 10px;
  color: #909399;
  padding: 2px 10px 6px;
  max-width: 156px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

.flow-node__summary.is-error {
  color: #fa541c;
  font-weight: 500;
}

/* 时间线重放：当前播放节点徽标（右上角，脉冲圆点 + 文字） */
.flow-node__playing {
  position: absolute;
  top: -8px;
  right: -8px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px 2px 6px;
  border-radius: 10px;
  background: #f56c6c;
  color: #fff;
  font-size: 10px;
  line-height: 14px;
  box-shadow: 0 2px 8px rgba(245, 108, 108, 0.45);
  z-index: 5;
  animation: flow-playing-glow 0.8s ease-in-out infinite alternate;
}

.flow-node__playing-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
  animation: flow-playing-blink 0.8s ease-in-out infinite;
}

.flow-node__playing-text {
  white-space: nowrap;
  user-select: none;
}

@keyframes flow-playing-blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

@keyframes flow-playing-glow {
  from {
    box-shadow: 0 2px 8px rgba(245, 108, 108, 0.45);
  }
  to {
    box-shadow: 0 2px 14px rgba(245, 108, 108, 0.85);
  }
}
</style>
