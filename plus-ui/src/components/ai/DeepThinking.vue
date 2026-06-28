<template>
  <el-tooltip
    :content="modelValue ? '深度思考已开启：输出推理过程' : '开启深度思考（输出推理过程，适用于 DeepSeek-R1 等推理模型）'"
    placement="top"
  >
    <span class="deep-thinking" :class="{ active: modelValue }" @click="$emit('update:modelValue', !modelValue)">
      <el-icon :size="14"><MagicStick /></el-icon>
      <span>深度思考</span>
    </span>
  </el-tooltip>
</template>

<script setup lang="ts">
import { MagicStick } from '@element-plus/icons-vue'

defineProps<{ modelValue: boolean }>()
defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()
</script>

<style scoped>
.deep-thinking {
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

.deep-thinking:hover {
  color: #409eff;
  background: #ecf5ff;
}

.deep-thinking.active {
  color: #fff;
  background: #409eff;
  box-shadow: 0 1px 3px rgba(64, 158, 255, 0.3);
}
</style>
