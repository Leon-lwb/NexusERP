<template>
  <div class="rich-editor">
    <Editor
      v-model="content"
      :height="height"
      :min-height="minHeight"
      :read-only="readOnly"
      :file-size="fileSize"
      :type="type"
      @update:modelValue="handleModelValueUpdate"
    />
  </div>
</template>

<script lang="ts" setup>
import { ref, watch, nextTick } from 'vue'
import Editor from '@/components/Editor/index.vue'
import { propTypes } from '@/utils/propTypes'

const props = defineProps({
  /* 编辑器的内容 */
  modelValue: propTypes.string,
  /* 高度 */
  height: propTypes.number.def(400),
  /* 最小高度 */
  minHeight: propTypes.number.def(400),
  /* 只读 */
  readOnly: propTypes.bool.def(false),
  /* 上传文件大小限制(MB) */
  fileSize: propTypes.number.def(5),
  /* 类型（base64格式、url格式） */
  type: propTypes.string.def('url')
})

const emit = defineEmits(['update:modelValue', 'change'])

const content = ref('')

// 监听 modelValue 的变化
watch(() => props.modelValue, (newValue) => {
  content.value = newValue || ''
}, { immediate: true })

// 处理内容更新
const handleModelValueUpdate = (value: string) => {
  emit('update:modelValue', value)
  // 确保在更新 modelValue 后触发 change 事件
  nextTick(() => {
    emit('change', value)
  })
}
</script>

<style lang="scss" scoped>
.rich-editor {
  width: 100%;
  
  :deep(.ql-container) {
    min-height: 200px;
  }
  
  :deep(.ql-editor) {
    min-height: 200px;
  }
}
</style> 