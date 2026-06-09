<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>知识库管理</span>
          <el-button type="primary" @click="handleAdd">新增知识库</el-button>
        </div>
      </template>
      <el-empty description="知识库功能开发中，敬请期待">
        <el-button type="primary" @click="handleAdd">立即创建</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup lang="ts">
const handleAdd = () => {
  // TODO: Phase 2 实现知识库功能
}
</script>
