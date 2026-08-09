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
import { onBeforeUnmount, onMounted } from 'vue';
import FlowNode from './FlowNode.vue';
import { getNodeTypeConfig } from '../nodeTypes';
import { useFlowStore, type FlowCanvasNode } from './useFlowStore';

/**
 * 流程画布（@vue-flow 封装）
 * <p>受控模式（apply-changes=false）：store 单向下发 nodes/edges。
 * 连线约束：只允许 outputs/* → inputs/*（同 type 反 mode）。
 * 节点拖入：接收 NodePalette 的 HTML5 draggable 数据。
 * 编辑能力：框选（selection-on-drag）+ 快捷键（Ctrl+C/V/D 复制粘贴、Ctrl+A 全选、
 * Ctrl+Z/Ctrl+Shift+Z 撤销重做）+ 结构性变更入撤销栈。</p>
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

const store = useFlowStore();
const { screenToFlowCoordinate, getSelectedNodes, addSelectedNodes } = useVueFlow();

/** slot 注册自定义节点（n8n 同款方式，非 nodeTypes 对象） */
const nodeTypes = { 'flow-node': FlowNode };

/** 连线校验：source 与 target 的 handle type 必须相反（outputs→inputs） */
function isValidConnection(connection: Connection): boolean {
  const source = connection.sourceHandle ?? '';
  const target = connection.targetHandle ?? '';
  return source.startsWith('outputs/') && target.startsWith('inputs/');
}

/** 连接创建：入撤销栈 + 写入 store */
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
  store.snapshot();
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
  store.snapshot();
  emit('update:nodes', [...props.nodes, node]);
}

function onDragOver(event: DragEvent) {
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy';
  }
}

/** 节点拖拽结束：入撤销栈 + 同步 position 到 store */
function onNodeDragStop(event: { node: FlowCanvasNode }) {
  const node = event.node;
  const index = props.nodes.findIndex((n) => n.id === node.id);
  if (index >= 0) {
    store.snapshot();
    const updated = [...props.nodes];
    updated[index] = { ...updated[index], position: { ...node.position } };
    emit('update:nodes', updated);
  }
}

/** 删除节点：级联删除关联边（入撤销栈） */
function onRemoveNode(nodeId: string) {
  store.snapshot();
  const remainingEdges = props.edges.filter((e) => e.source !== nodeId && e.target !== nodeId);
  emit('update:edges', remainingEdges);
  emit(
    'update:nodes',
    props.nodes.filter((n) => n.id !== nodeId)
  );
}

/* ==================== 快捷键（Ctrl+C/V/D/A + 撤销重做） ==================== */

/** 当前选中的节点列表（vue-flow 内部选中状态） */
function getSelectedNodeList(): FlowCanvasNode[] {
  return (getSelectedNodes.value ?? []) as unknown as FlowCanvasNode[];
}

/** Ctrl+C 复制选中 */
function copy() {
  store.copySelection(getSelectedNodeList());
}

/** Ctrl+V 粘贴（含连边，偏移 40px） */
function paste() {
  const result = store.pasteClipboard();
  if (!result) return;
  store.snapshot();
  emit('update:nodes', [...props.nodes, ...result.nodes]);
  emit('update:edges', [...props.edges, ...result.edges]);
}

/** Ctrl+A 全选 */
function selectAll() {
  addSelectedNodes(props.nodes as unknown as never[]);
}

function onKeyDown(e: KeyboardEvent) {
  if (props.readonly) return;
  // 输入框/文本域聚焦时不拦截（保留编辑行为）
  const target = e.target as HTMLElement | null;
  if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) return;
  const mod = e.ctrlKey || e.metaKey;
  if (!mod) return;
  const key = e.key.toLowerCase();
  if (key === 'c') {
    copy();
    e.preventDefault();
  } else if (key === 'v') {
    paste();
    e.preventDefault();
  } else if (key === 'd') {
    copy();
    paste();
    e.preventDefault();
  } else if (key === 'z') {
    if (e.shiftKey) {
      store.redo();
    } else {
      store.undo();
    }
    e.preventDefault();
  } else if (key === 'y') {
    store.redo();
    e.preventDefault();
  } else if (key === 'a') {
    selectAll();
    e.preventDefault();
  }
}

onMounted(() => window.addEventListener('keydown', onKeyDown));
onBeforeUnmount(() => window.removeEventListener('keydown', onKeyDown));

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
      :selection-on-drag="!props.readonly"
      :selection-mode="'partial'"
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
