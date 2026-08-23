<template>
  <div class="tinymce-editor">
    <editor :placeholder="nm.tableTypeDesc" v-model="editorContent" :disabled="disabled" @update:modelValue="handleModelValueUpdate" />
  </div>
</template>

<script lang="ts">
import Editor from '@/components/Editor/index.vue';

export default {
  name: 'NmEditor',
  components: {
    Editor
  },
  props: {
    nm: {
      type: Object,
      required: true
    },
    value: {
      type: String,
      required: false
    },
    disabled: {
      type: Boolean,
      default: false
    }
  },
  data() {
    // 编辑器内容本地状态：原实现直接写入 nm.commonDataInfo（变更 prop），
    // 现改为本地持有，内容变更时通过 update:value / common-component 事件同步给父组件
    return {
      editorContent: ''
    };
  },
  watch: {
    value: {
      immediate: true,
      handler(newValue) {
        this.editorContent = newValue || '';
      }
    }
  },
  methods: {
    handleModelValueUpdate(newContent) {
      this.$emit('update:value', newContent); // 确保触发 update:value 事件
      // 触发自定义事件：负载为携带最新内容的 nm 副本（与原实现中已更新 commonDataInfo 的 nm 保持一致）
      this.$emit('common-component', { ...this.nm, commonDataInfo: newContent });
    }
  }
};
</script>

<style scoped></style>
