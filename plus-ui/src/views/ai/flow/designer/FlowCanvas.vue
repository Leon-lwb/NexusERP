<script setup lang="ts">
import { VueFlow, useVueFlow, SelectionMode } from '@vue-flow/core';
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
import FlowNodeGroup from './FlowNodeGroup.vue';
import FlowNote from './FlowNote.vue';
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
  /** 框选变化（多选状态暴露给父组件：选择工具栏/分组） */
  (e: 'selection-change', nodes: FlowCanvasNode[]): void;
  /** 节点 hover 工具栏动作（test/duplicate/delete 由父组件处理） */
  (e: 'node-action', payload: { nodeId: string; action: string }): void;
  /** 快捷键帮助请求（? 键） */
  (e: 'shortcut-help'): void;
}>();

const store = useFlowStore();
const { screenToFlowCoordinate, viewport, fitBounds, getSelectedNodes, addSelectedNodes, removeSelectedNodes } = useVueFlow();

/* ==================== 节点 hover 工具栏动作（事件总线） ==================== */

/** 接收 FlowNode 浮动工具栏动作：focus 就地 fitBounds，其余转发父组件 */
function onNodeActionEvent(e: Event) {
  const detail = (e as CustomEvent).detail;
  if (!detail?.nodeId) return;
  if (detail.action === 'focus') {
    const node = props.nodes.find((n) => n.id === detail.nodeId);
    if (node) {
      const bounds = {
        x: node.position.x,
        y: node.position.y,
        width: 160,
        height: 80
      };
      fitBounds(bounds, { padding: 0.4 });
    }
    return;
  }
  emit('node-action', { nodeId: detail.nodeId, action: detail.action });
}

onMounted(() => {
  window.addEventListener('keydown', onKeyDown);
  window.addEventListener('flow-node-action', onNodeActionEvent);
});
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown);
  window.removeEventListener('flow-node-action', onNodeActionEvent);
});

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
const nodeTypes = { 'flow-node': FlowNode, 'flow-note': FlowNote };

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

/** 节点点击：通知父组件（选中）+ 同步内部选中（单击=单选清工具栏；Ctrl+点击追加走内部状态） */
function onNodeClick(event: NodeMouseEvent) {
  emit('node-click', event.node);
  emit('selection-change', getSelectedNodeList());
}

/** 节点双击：通知父组件（查看运行数据 NDV） */
function onNodeDoubleClick(event: NodeMouseEvent) {
  emit('node-dblclick', event.node);
}

/* ==================== 多选/框选（选择工具栏 + 分组） ==================== */

/** 框选结束（@vue-flow 事件名是 selection-end，SelectionChange 参数含 nodes/edges） */
function onSelectionEnd(e: { nodes: FlowCanvasNode[] } | MouseEvent) {
  emit('selection-change', 'nodes' in e ? e.nodes : []);
}

/* ==================== 节点分组（Ctrl+G 创建 / Delete 解除选中组） ==================== */

/** 用当前选中节点创建分组（≥2 个才创建） */
function groupSelection() {
  const selected = getSelectedNodeList();
  if (selected.length < 2) {
    store.selectedGroupId = null;
    return;
  }
  const group = store.createGroup(selected.map((n) => n.id));
  if (group) {
    // 清空节点选中态（避免组内节点高亮干扰组选中）+ 通知父组件收起框选工具栏
    addSelectedNodes([] as never[]);
    emit('selection-change', []);
  }
}

/** 鼠标悬停节点/边：记录 hover（上下游路径高亮） */
function onNodeMouseEnter(event: NodeMouseEvent) {
  store.hoverNodeId = event.node.id;
}
function onNodeMouseLeave() {
  store.hoverNodeId = null;
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

/** 节点拖拽结束：入撤销栈 + 同步 position 到 store + 重算分组包围框 */
function onNodeDragStop(event: { node: FlowCanvasNode }) {
  const node = event.node;
  const index = props.nodes.findIndex((n) => n.id === node.id);
  if (index >= 0) {
    store.snapshot();
    const updated = [...props.nodes];
    updated[index] = { ...updated[index], position: { ...node.position } };
    emit('update:nodes', updated);
    store.syncGroupBounds();
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

/* ==================== 方向键遍历导航（对齐 n8n useCanvasTraversal 简化版） ==================== */

/** 按 Y 坐标排序（上游取最近=Y 最大，下游取最近=Y 最小） */
function byY(list: FlowCanvasNode[], dir: 'up' | 'down'): FlowCanvasNode[] {
  return [...list].sort((a, b) => (dir === 'down' ? a.position.y - b.position.y : b.position.y - a.position.y));
}

/** 方向键选中：← 上游 / → 下游 / ↑↓ 兄弟（共享 target 或 source，按 Y） */
function navigateDir(dir: 'up' | 'down' | 'left' | 'right') {
  const current = store.selectedNodeId;
  if (!current) return;
  const cur = props.nodes.find((n) => n.id === current);
  if (!cur) return;
  let next: FlowCanvasNode | undefined;
  if (dir === 'left') {
    const ups = props.nodes.filter((n) => props.edges.some((e) => e.target === current && e.source === n.id));
    next = byY(ups, 'up')[0];
  } else if (dir === 'right') {
    const downs = props.nodes.filter((n) => props.edges.some((e) => e.source === current && e.target === n.id));
    next = byY(downs, 'down')[0];
  } else {
    // 兄弟：共享 target（同目标的上游们）或共享 source（同源的下游们）
    const brotherIds = new Set<string>();
    props.edges.forEach((e) => {
      if (e.target === current || e.source === current) {
        props.edges.forEach((e2) => {
          if (e2.target === e.target && e2.target === current) brotherIds.add(e2.source);
          if (e2.source === e.source && e2.source === current) brotherIds.add(e2.target);
        });
      }
    });
    const brothers = props.nodes.filter((n) => brotherIds.has(n.id) && n.id !== current);
    const sorted = byY(brothers, dir);
    const curY = cur.position.y;
    // 取目标方向最近的（↑ 取 Y 小于当前的最大值，↓ 取 Y 大于当前的最小值）；无则取该方向最后一个
    next = sorted.find((n) => (dir === 'up' ? n.position.y < curY : n.position.y > curY)) ?? sorted[sorted.length - 1];
  }
  if (next) {
    // 设置应用选中态 + 画布高亮：清空旧选中，仅选中目标（VueFlow 内部选中状态决定节点高亮）
    removeSelectedNodes(getSelectedNodeList() as never[]);
    store.selectedNodeId = next.id;
    addSelectedNodes([next] as unknown as never[]);
    emit('node-click', next);
  }
}

/** Shift+方向键：批量选中上游/下游全部节点 */
function selectPath(dir: 'left' | 'right') {
  const current = store.selectedNodeId;
  if (!current) return;
  const ids = dir === 'left' ? store.getUpstreamIds(current) : store.getDownstreamIds(current);
  const list = props.nodes.filter((n) => ids.has(n.id));
  if (list.length) addSelectedNodes(list as unknown as never[]);
}

function onKeyDown(e: KeyboardEvent) {
  if (props.readonly) return;
  // 输入框/文本域聚焦时不拦截（保留编辑行为）
  const target = e.target as HTMLElement | null;
  if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) return;
  // Delete：优先解除选中的分组
  if (e.key === 'Delete' && store.selectedGroupId) {
    store.removeGroup(store.selectedGroupId);
    e.preventDefault();
    return;
  }
  // 方向键导航（无修饰键）
  const dirKey = e.key.toLowerCase();
  if (!e.ctrlKey && !e.metaKey) {
    if (dirKey === 'arrowup' || dirKey === 'arrowdown' || dirKey === 'arrowleft' || dirKey === 'arrowright') {
      if (e.shiftKey) {
        if (dirKey === 'arrowleft' || dirKey === 'arrowright') {
          selectPath(dirKey === 'arrowleft' ? 'left' : 'right');
          e.preventDefault();
          return;
        }
      } else {
        navigateDir(dirKey.slice(5) as 'up' | 'down' | 'left' | 'right');
        e.preventDefault();
        return;
      }
    }
    // Shift+S：新建便利贴
    if (e.shiftKey && e.key.toLowerCase() === 's') {
      store.addNote();
      e.preventDefault();
      return;
    }
    // ?：快捷键帮助
    if (e.key === '?') {
      emit('shortcut-help');
      e.preventDefault();
      return;
    }
  }
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
  } else if (key === 'g') {
    groupSelection();
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
      :selection-mode="SelectionMode.Partial"
      @connect="onConnect"
      @pane-click="onPaneClick"
      @node-click="onNodeClick"
      @node-double-click="onNodeDoubleClick"
      @node-drag-stop="onNodeDragStop"
      @node-key-delete="(e) => onRemoveNode(e.node.id)"
      @selection-end="onSelectionEnd"
      @node-mouse-enter="onNodeMouseEnter"
      @node-mouse-leave="onNodeMouseLeave"
    >
      <template #node-flow-node="nodeProps">
        <FlowNode v-bind="nodeProps" />
      </template>
      <template #node-flow-note="noteProps">
        <FlowNote v-bind="noteProps" />
      </template>
      <Background pattern-color="#e4e7ed" :gap="20" />
      <Controls v-if="!props.readonly" />
      <MiniMap pannable zoomable />
    </VueFlow>
    <!-- 节点分组覆盖层（flow 坐标经 viewport 投影，缩放平移实时跟随） -->
    <FlowNodeGroup v-for="g in store.groups" :key="g.id" :group="g" :viewport="viewport" :selected="store.selectedGroupId === g.id" />
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
