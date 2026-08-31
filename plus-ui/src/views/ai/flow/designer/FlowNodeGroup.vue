<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { Close, Expand, Fold } from '@element-plus/icons-vue';
import { useFlowStore, type FlowGroup } from './useFlowStore';

/**
 * 节点分组框（对齐 n8n node groups 简化版）
 * <p>画布覆盖层 div（不参与 VueFlow 节点体系）：包围框 + 标题栏。
 * 标题栏：内联命名（双击）/ 拖拽移动整组 / 折叠展开 / 状态聚合徽标 / 取消分组。
 * 坐标经 viewport 投影（flow→屏幕，与播放小球同公式），缩放平移实时跟随。</p>
 */

const props = defineProps<{
  group: FlowGroup;
  viewport: { x: number; y: number; zoom: number };
  selected: boolean;
}>();

const store = useFlowStore();

/** 标题栏高度（折叠态组框仅显示标题栏） */
const TITLEBAR_H = 34;

/** 组框屏幕样式（flow 坐标 × zoom + translate） */
const style = computed(() => {
  const { x, y, zoom } = props.viewport;
  const h = props.group.collapsed ? TITLEBAR_H : props.group.bounds.height;
  return {
    left: props.group.bounds.x * zoom + x + 'px',
    top: props.group.bounds.y * zoom + y + 'px',
    width: props.group.bounds.width * zoom + 'px',
    height: h * zoom + 'px'
  };
});

/** 组内节点执行状态聚合（标题栏徽标） */
const summary = computed(() => store.groupStatusSummary(props.group.id));

/* ==================== 标题栏拖拽（整组移动，rAF 节流） ==================== */

const dragging = ref(false);
let startScreen = { x: 0, y: 0 };
let lastDx = 0;
let lastDy = 0;
let pendingDx = 0;
let pendingDy = 0;
let rafId: number | null = null;

function onDragStart(e: MouseEvent) {
  // 拖拽开始即选中组（点击标题栏任意处 = 选中，Delete 可解除分组）
  store.selectedGroupId = props.group.id;
  dragging.value = true;
  startScreen = { x: e.clientX, y: e.clientY };
  lastDx = 0;
  lastDy = 0;
  pendingDx = 0;
  pendingDy = 0;
  window.addEventListener('mousemove', onDragMove);
  window.addEventListener('mouseup', onDragEnd);
}

function onDragMove(e: MouseEvent) {
  // 屏幕像素增量 → flow 坐标增量（÷ zoom）
  pendingDx = (e.clientX - startScreen.x) / props.viewport.zoom;
  pendingDy = (e.clientY - startScreen.y) / props.viewport.zoom;
  // rAF 节流：每次 mousemove 整体替换节点数组代价高，合并为每帧一次
  if (rafId === null) {
    rafId = requestAnimationFrame(() => {
      rafId = null;
      const dx = pendingDx - lastDx;
      const dy = pendingDy - lastDy;
      if (dx !== 0 || dy !== 0) {
        store.moveGroup(props.group.id, dx, dy);
      }
      lastDx = pendingDx;
      lastDy = pendingDy;
    });
  }
}

function onDragEnd() {
  dragging.value = false;
  if (rafId !== null) {
    cancelAnimationFrame(rafId);
    rafId = null;
  }
  window.removeEventListener('mousemove', onDragMove);
  window.removeEventListener('mouseup', onDragEnd);
}

onBeforeUnmount(onDragEnd);

/* ==================== 内联命名（双击标题栏） ==================== */

const editing = ref(false);
const editName = ref('');

function startRename() {
  editName.value = props.group.name;
  editing.value = true;
}

function commitRename() {
  store.renameGroup(props.group.id, editName.value);
  editing.value = false;
}

/** 点击组框：选中（标题栏拖拽时已选中，此处处理非标题栏区域；组框背景 pointer-events:none 通常不可达） */
function onGroupClick() {
  store.selectedGroupId = props.group.id;
}
</script>

<template>
  <div
    class="flow-group"
    :class="{ 'is-selected': selected, 'is-collapsed': group.collapsed, 'is-dragging': dragging }"
    :style="style"
    @mousedown.stop.prevent="onGroupClick"
  >
    <!-- 标题栏：拖拽移动整组（mousedown 即选中）/ 双击命名 -->
    <div class="flow-group__titlebar" @mousedown.stop.prevent="onDragStart" @dblclick.stop.prevent="startRename">
      <template v-if="editing">
        <input
          v-model="editName"
          class="flow-group__input"
          @mousedown.stop
          @keydown.enter.stop.prevent="commitRename"
          @keydown.esc.stop.prevent="editing = false"
          @blur="commitRename"
        />
      </template>
      <template v-else>
        <span class="flow-group__name">{{ group.name }}</span>
        <span v-if="group.collapsed" class="flow-group__count">{{ group.nodeIds.length }}</span>
      </template>

      <!-- 状态聚合徽标 -->
      <span v-if="summary.success || summary.failed || summary.running" class="flow-group__stats">
        <span v-if="summary.running" class="flow-group__stat is-running">{{ summary.running }}</span>
        <span v-if="summary.success" class="flow-group__stat is-success">{{ summary.success }}</span>
        <span v-if="summary.failed" class="flow-group__stat is-failed">{{ summary.failed }}</span>
      </span>

      <!-- 按钮：mousedown.stop 防止冒泡到标题栏拖拽 -->
      <el-icon
        class="flow-group__action"
        :title="group.collapsed ? '展开分组' : '折叠分组'"
        @mousedown.stop.prevent
        @click.stop.prevent="store.toggleGroupCollapse(group.id)"
      >
        <component :is="group.collapsed ? Expand : Fold" />
      </el-icon>
      <el-icon class="flow-group__action" title="取消分组" @mousedown.stop.prevent @click.stop.prevent="store.removeGroup(group.id)">
        <Close />
      </el-icon>
    </div>
  </div>
</template>

<style scoped>
.flow-group {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 1;
  border: 2px solid rgba(64, 158, 255, 0.35);
  border-radius: 10px;
  background: rgba(64, 158, 255, 0.06);
  pointer-events: none; /* 不挡节点交互，仅标题栏可交互 */
  transition:
    border-color 0.15s,
    background 0.15s;
}

.flow-group.is-selected {
  border-color: #409eff;
  background: rgba(64, 158, 255, 0.1);
}

.flow-group.is-collapsed {
  background: rgba(64, 158, 255, 0.14);
}

.flow-group.is-dragging {
  border-style: dashed;
  border-color: #409eff;
}

/* 标题栏：唯一可交互区 */
.flow-group__titlebar {
  position: absolute;
  top: -2px;
  left: -2px;
  right: -2px;
  height: 34px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 6px 0 10px;
  background: rgba(64, 158, 255, 0.9);
  border-radius: 8px 8px 0 0;
  cursor: grab;
  pointer-events: auto;
  user-select: none;
  box-sizing: border-box;
}

.flow-group.is-collapsed .flow-group__titlebar {
  border-radius: 8px;
}

.flow-group__titlebar:active {
  cursor: grabbing;
}

.flow-group__name {
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 140px;
}

.flow-group__count {
  font-size: 10px;
  color: #fff;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 8px;
  padding: 0 6px;
  line-height: 14px;
  flex-shrink: 0;
}

.flow-group__input {
  flex: 1;
  min-width: 0;
  height: 22px;
  border: none;
  outline: none;
  border-radius: 4px;
  padding: 0 6px;
  font-size: 12px;
}

/* 状态聚合徽标 */
.flow-group__stats {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-left: auto;
  flex-shrink: 0;
}

.flow-group__stat {
  font-size: 10px;
  color: #fff;
  border-radius: 8px;
  padding: 0 5px;
  line-height: 14px;
  background: rgba(255, 255, 255, 0.2);
}

.flow-group__stat.is-success {
  background: #52c41a;
}

.flow-group__stat.is-failed {
  background: #f56c6c;
}

.flow-group__stat.is-running {
  background: #e6a23c;
}

.flow-group__action {
  font-size: 12px;
  color: #fff;
  cursor: pointer;
  flex-shrink: 0;
  padding: 2px;
  border-radius: 4px;
}

.flow-group__action:hover {
  background: rgba(255, 255, 255, 0.25);
}
</style>
