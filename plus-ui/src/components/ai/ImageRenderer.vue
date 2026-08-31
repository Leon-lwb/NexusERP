<template>
  <div class="ai-image-renderer">
    <el-image
      :src="url"
      :preview-src-list="[url]"
      :preview-teleported="true"
      fit="contain"
      :style="{ maxWidth: width, maxHeight: height }"
      class="ai-image"
    >
      <template #placeholder>
        <div class="image-placeholder">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>图片加载中...</span>
        </div>
      </template>
      <template #error>
        <div class="image-error">
          <el-icon><Picture /></el-icon>
          <span>图片加载失败</span>
        </div>
      </template>
    </el-image>
  </div>
</template>

<script setup lang="ts">
import { Loading, Picture } from '@element-plus/icons-vue';

withDefaults(
  defineProps<{
    url: string;
    width?: string;
    height?: string;
  }>(),
  {
    width: '100%',
    height: '320px'
  }
);
</script>

<style scoped>
.ai-image-renderer {
  margin: 8px 0;
}

.ai-image {
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  display: block;
  cursor: zoom-in;
}

.image-placeholder,
.image-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 200px;
  color: #909399;
  font-size: 13px;
  background: #f5f7fa;
  border-radius: 8px;
}
</style>
