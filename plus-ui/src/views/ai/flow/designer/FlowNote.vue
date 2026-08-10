<script setup lang="ts">
import { ref } from 'vue';

/**
 * 便利贴（对齐 n8n StickyNote 简化版）
 * <p>画布注释用：黄色便签 + 可编辑文本。内容为本地视图状态（不随 config 保存，
 * toConfigJson 过滤 flow-note 类型）。位置/尺寸可拖拽（VueFlow 节点），Delete 可删。</p>
 */
const text = ref('');
</script>

<template>
  <div class="flow-note">
    <textarea v-model="text" class="flow-note__area" placeholder="输入便利贴内容..." spellcheck="false" />
  </div>
</template>

<style scoped>
.flow-note {
  width: 200px;
  height: 140px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(250, 173, 20, 0.15);
  padding: 8px;
  box-sizing: border-box;
}

.flow-note__area {
  width: 100%;
  height: 100%;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  font-size: 12px;
  line-height: 1.6;
  color: #614700;
  font-family: inherit;
}

.flow-note__area::placeholder {
  color: #d4b106;
}
</style>
