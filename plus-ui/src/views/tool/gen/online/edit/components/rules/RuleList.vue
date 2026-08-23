<template>
  <div class="rule-list">
    <div class="rule-list-header">
      <div class="rule-list-title">{{ title }}</div>
      <el-button size="small" type="primary" @click="$emit('add')">新增</el-button>
    </div>
    <div class="rule-list-body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ title: string }>();
defineEmits<{ (e: 'add'): void }>();
</script>

<style scoped>
.rule-list {
  margin-bottom: 14px;
}
.rule-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 10px 0;
}
.rule-list-title {
  font-weight: 600;
}
</style>
