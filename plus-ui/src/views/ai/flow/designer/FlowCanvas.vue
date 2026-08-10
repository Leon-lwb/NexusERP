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
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
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
  /** 时间线重放当前播放节点 ID（画布上显示移动小球指示执行位置） */
  playingNodeId?: string | null;
}>();

const emit = defineEmits<{
  (e: 'update:nodes', nodes: FlowCanvasNode[]): void;
  (e: 'update:edges', edges: Edge[]): void;
  (e: 'node-click', node: FlowCanvasNode): void;
  (e: 'node-dblclick', node: FlowCanvasNode): void;
}>();

const store = useFlowStore();
const { screenToFlowCoordinate, viewport, getSelectedNodes, addSelectedNodes } = useVueFlow();

/* ==================== 时间线播放小球（跟随当前播放节点移动） ==================== */

/** 节点渲染宽度（与 FlowNode .flow-node 一致，居中偏移需乘 zoom） */
const NODE_WIDTH = 160;

const ballPos = ref({ x: -100, y: -100 });

/**
 * 播放小球屏幕坐标（flow 坐标 → 屏幕坐标）
 * <p>注意不能用 useVueFlow 的 project()：它是反向投影（屏幕→flow，(x-tx)/zoom）。
 * 正确公式 x*zoom+translate；居中偏移 80px 也需乘 zoom（fitView 后 zoom≠1）。</p>
 */
watch(
  [() => props.playingNodeId, () => props.nodes, viewport],
  () => {
    if (!props.playingNodeId) return;
    const node = props.nodes.find((n) => n.id === props.playingNodeId);
    if (!node) return;
    const vp = viewport.value;
    ballPos.value = {
      x: node.position.x * vp.zoom + vp.x + (NODE_WIDTH * vp.zoom) / 2,
      y: node.position.y * vp.zoom + vp.y
    };
  },
  { deep: true }
);

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
    <!-- 时间线播放小球（绝对定位于画布容器，跟随当前播放节点移动） -->
    <div v-if="props.playingNodeId" class="flow-canvas__ball" :style="{ transform: `translate(${ballPos.x}px, ${ballPos.y}px)` }">
      <span class="flow-canvas__ball-dot"></span>
      <span class="flow-canvas__ball-ring"></span>
    </div>
  </div>
</template>

<style scoped>
.flow-canvas {
  position: relative;
  flex: 1;
  height: 100%;
  background: #f5f7fa;
}

/* 时间线播放小球（容器即圆心锚点，内联 style 控制 translate 位置；子元素相对 -7px 居中） */
.flow-canvas__ball {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 20;
  width: 0;
  height: 0;
  pointer-events: none;
  transition: transform 0.45s cubic-bezier(0.25, 0.9, 0.4, 1.2);
}

/* 小球本体：红色实心圆点 + 白色描边 */
.flow-canvas__ball-dot {
  position: absolute;
  top: -7px;
  left: -7px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #f56c6c;
  border: 2px solid #fff;
  box-shadow: 0 2px 6px rgba(245, 108, 108, 0.5);
  animation: flow-ball-bounce 0.6s ease-in-out infinite alternate;
}

/* 脉冲光环 */
.flow-canvas__ball-ring {
  position: absolute;
  top: -7px;
  left: -7px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(245, 108, 108, 0.55);
  animation: flow-ball-ping 1.2s cubic-bezier(0, 0, 0.2, 1) infinite;
}

@keyframes flow-ball-bounce {
  from {
    transform: translateY(0);
  }
  to {
    transform: translateY(-4px);
  }
}

@keyframes flow-ball-ping {
  0% {
    transform: scale(1);
    opacity: 0.9;
  }
  75%,
  100% {
    transform: scale(2.6);
    opacity: 0;
  }
}
</style>
