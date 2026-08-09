<script setup lang="ts">
import { VueFlow, useVueFlow } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { Controls } from '@vue-flow/controls';
import { MiniMap } from '@vue-flow/minimap';
import { MarkerType } from '@vue-flow/core';
import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';
import '@vue-flow/controls/dist/style.css';
import '@vue-flow/minimap/dist/style.css';
import type { Connection, Edge, Node, NodeMouseEvent } from '@vue-flow/core';
import { computed, watch } from 'vue';
import FlowNode from './FlowNode.vue';
import { getNodeTypeConfig } from '../nodeTypes';
import type { FlowCanvasNode } from './useFlowStore';

/**
 * 流程画布（@vue-flow 封装）
 * <p>受控模式（apply-changes=false）：store 单向下发 nodes/edges。
 * 连线约束：只允许 outputs/* → inputs/*（同 type 反 mode）。
 * 节点拖入：接收 NodePalette 的 HTML5 draggable 数据。</p>
 */

const props = defineProps<{
  /** 画布节点（vue-flow Node 结构，data 含 label/type/status） */
  nodes: FlowCanvasNode[];
  /** 画布边（vue-flow Edge 结构，sourceHandle/targetHandle 编码 outputs/main/0） */
  edges: Edge[];
  /** 是否只读（运行监控重放用） */
  readonly?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:nodes', nodes: FlowCanvasNode[]): void;
  (e: 'update:edges', edges: Edge[]): void;
  (e: 'node-click', node: FlowCanvasNode): void;
  (e: 'node-dblclick', node: FlowCanvasNode): void;
}>();

const { screenToFlowCoordinate, addEdges, addNodes, removeNodes, project } = useVueFlow();

/** slot 注册自定义节点（n8n 同款方式，非 nodeTypes 对象） */
const nodeTypes = { 'flow-node': FlowNode };

/** 连线校验：source 与 target 的 handle type 必须相反（outputs→inputs） */
function isValidConnection(connection: Connection): boolean {
  const source = connection.sourceHandle ?? '';
  const target = connection.targetHandle ?? '';
  return source.startsWith('outputs/') && target.startsWith('inputs/');
}

/** 连接创建：写入 store */
function onConnect(connection: Connection) {
  const edge: Edge = {
    id: `edge_${Date.now()}_${Math.floor(Math.random() * 10000)}`,
    source: connection.source,
    target: connection.target,
    sourceHandle: connection.sourceHandle ?? 'outputs/main/0',
    targetHandle: connection.targetHandle ?? 'inputs/main/0',
    markerEnd: MarkerType.ArrowClosed,
    type: 'default'
  };
  emit('update:edges', [...props.edges, edge]);
}

/** 画布点击空白：清空选中 */
function onPaneClick() {
  emit('node-click', null as unknown as FlowCanvasNode);
}

/** 节点点击：通知父组件（选中） */
function onNodeClick(event: NodeMouseEvent) {
  emit('node-click', event.node);
}

/** 节点双击：通知父组件（查看运行数据 NDV） */
function onNodeDoubleClick(event: NodeMouseEvent) {
  emit('node-dblclick', event.node);
}

/** 节点拖入（NodePalette HTML5 draggable） */
function onDrop(event: DragEvent) {
  const type = event.dataTransfer?.getData('application/x-flow-node-type');
  if (!type) return;
  const config = getNodeTypeConfig(type);
  if (!config) return;

  const position = screenToFlowCoordinate({ x: event.clientX, y: event.clientY });
  const node: FlowCanvasNode = {
    id: `node_${type}_${Date.now()}`,
    type: 'flow-node',
    position,
    data: {
      label: config.label,
      type,
      config: JSON.parse(JSON.stringify(config.defaultConfig))
    }
  };
  emit('update:nodes', [...props.nodes, node]);
}

function onDragOver(event: DragEvent) {
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy';
  }
}

/** 节点拖拽结束：同步 position 到 store */
function onNodeDragStop(event: { node: FlowCanvasNode }) {
  const node = event.node;
  const index = props.nodes.findIndex((n) => n.id === node.id);
  if (index >= 0) {
    const updated = [...props.nodes];
    updated[index] = { ...updated[index], position: { ...node.position } };
    emit('update:nodes', updated);
  }
}

/** 删除节点：级联删除关联边 */
function onRemoveNode(nodeId: string) {
  const remainingEdges = props.edges.filter((e) => e.source !== nodeId && e.target !== nodeId);
  emit('update:edges', remainingEdges);
  emit(
    'update:nodes',
    props.nodes.filter((n) => n.id !== nodeId)
  );
}

defineExpose({ onRemoveNode });
</script>

<template>
  <div class="flow-canvas" @drop="onDrop" @dragover="onDragOver">
    <VueFlow
      :nodes="props.nodes"
      :edges="props.edges"
      :apply-changes="false"
      :is-valid-connection="isValidConnection"
      :connection-line-options="{ markerEnd: MarkerType.ArrowClosed }"
      snap-to-grid
      :snap-grid="[10, 10]"
      fit-view-on-init
      :nodes-draggable="!props.readonly"
      :nodes-connectable="!props.readonly"
      :elements-selectable="!props.readonly"
      @connect="onConnect"
      @pane-click="onPaneClick"
      @node-click="onNodeClick"
      @node-double-click="onNodeDoubleClick"
      @node-drag-stop="onNodeDragStop"
      @node-key-delete="(e) => onRemoveNode(e.node.id)"
    >
      <template #node-flow-node="nodeProps">
        <FlowNode v-bind="nodeProps" />
      </template>
      <Background pattern-color="#e4e7ed" :gap="20" />
      <Controls v-if="!props.readonly" />
      <MiniMap pannable zoomable />
    </VueFlow>
  </div>
</template>

<style scoped>
.flow-canvas {
  flex: 1;
  height: 100%;
  background: #f5f7fa;
}
</style>
