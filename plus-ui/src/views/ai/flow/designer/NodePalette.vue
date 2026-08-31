<script setup lang="ts">
import { computed, ref } from 'vue';
import { Search } from '@element-plus/icons-vue';
import { NODE_CATEGORIES, NODE_TYPE_CONFIGS } from '../nodeTypes';

/**
 * 左侧节点面板：搜索 + 分类分组 + 可拖拽卡片（HTML5 draggable）
 * <p>拖拽时在 dataTransfer 中携带节点类型，FlowCanvas 的 drop 事件消费；
 * 搜索按 label/type/description 模糊匹配，空组自动隐藏</p>
 */
const keyword = ref('');

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return NODE_TYPE_CONFIGS;
  return NODE_TYPE_CONFIGS.filter(
    (c) => c.label.toLowerCase().includes(kw) || c.type.toLowerCase().includes(kw) || c.description.toLowerCase().includes(kw)
  );
});

/** 分组展示：有匹配的分组 + 该组节点 */
const groups = computed(() =>
  NODE_CATEGORIES.map((cat) => ({
    cat,
    items: filtered.value.filter((c) => c.category === cat.key)
  })).filter((g) => g.items.length)
);

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
    <el-input v-model="keyword" size="small" placeholder="搜索节点..." clearable :prefix-icon="Search" class="node-palette__search" />
    <div class="node-palette__scroll">
      <template v-for="g in groups" :key="g.cat.key">
        <div class="node-palette__group">{{ g.cat.label }}</div>
        <div
          v-for="cfg in g.items"
          :key="cfg.type"
          class="node-palette__item"
          draggable="true"
          :title="cfg.description"
          @dragstart="onDragStart($event, cfg.type)"
        >
          <el-icon :style="{ color: cfg.color }" class="node-palette__icon">
            <component :is="cfg.icon" />
          </el-icon>
          <span>{{ cfg.label }}</span>
        </div>
      </template>
      <div v-if="!groups.length" class="node-palette__empty">无匹配节点</div>
    </div>
    <div class="node-palette__hint">拖拽节点到画布</div>
  </div>
</template>

<style scoped>
.node-palette {
  width: 160px;
  border-right: 1px solid #e4e7ed;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  padding: 8px;
  gap: 6px;
}

.node-palette__title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  padding: 4px 8px;
}

.node-palette__search {
  flex-shrink: 0;
}

.node-palette__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.node-palette__group {
  font-size: 11px;
  color: #909399;
  padding: 6px 8px 2px;
}

.node-palette__item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
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

.node-palette__empty {
  padding: 16px 0;
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
}

.node-palette__hint {
  margin-top: auto;
  font-size: 11px;
  color: #c0c4cc;
  text-align: center;
  padding: 8px 0;
  flex-shrink: 0;
}
</style>
