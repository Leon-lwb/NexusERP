<template>
  <div class="tinymce-editor">
    <editor :placeholder="nm.tableTypeDesc" v-model="nm.commonDataInfo" :disabled="disabled" @update:modelValue="handleModelValueUpdate" />
  </div>
</template>

<script>
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
  watch: {
    value: {
      immediate: true,
      handler(newValue) {
        this.nm.commonDataInfo = newValue || '';
      }
    }
  },
  methods: {
    handleModelValueUpdate(newContent) {
      this.nm.commonDataInfo = newContent;
      this.$emit('update:value', newContent); // 确保触发 update:value 事件
      this.$emit('common-component', this.nm); // 触发自定义事件
    }
  }
};
</script>

<style scoped></style>
