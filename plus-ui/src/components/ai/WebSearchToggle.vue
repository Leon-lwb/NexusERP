<template>
  <el-tooltip :content="modelValue ? '联网搜索已开启：回答带信息来源' : '开启联网搜索（获取最新信息，回答标注来源链接）'" placement="top">
    <span class="web-search-toggle" :class="{ active: modelValue }" @click="$emit('update:modelValue', !modelValue)">
      <el-icon :size="14"><Link /></el-icon>
      <span>联网</span>
    </span>
  </el-tooltip>
</template>

<script setup lang="ts">
import { Link } from '@element-plus/icons-vue';

defineProps<{ modelValue: boolean }>();
defineEmits<{ (e: 'update:modelValue', value: boolean): void }>();
</script>

<style scoped>
.web-search-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  background: #f5f7fa;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s;
  white-space: nowrap;
}

.web-search-toggle:hover {
  color: #67c23a;
  background: #f0f9eb;
}

.web-search-toggle.active {
  color: #fff;
  background: #67c23a;
  box-shadow: 0 1px 3px rgba(103, 194, 58, 0.3);
}
</style>
