<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>AI 模型配置</span>
          <el-button type="primary" @click="handleAdd">新增模型</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="list">
        <el-table-column label="模型名称" prop="modelName" />
        <el-table-column label="显示名称" prop="displayName" />
        <el-table-column label="类型" prop="modelType" />
        <el-table-column label="最大 Token" prop="maxTokens" />
        <el-table-column label="温度" prop="temperature" />
        <el-table-column label="默认" prop="isDefault">
          <template #default="{ row }">
            <el-tag :type="row.isDefault === '1' ? 'success' : 'info'">{{ row.isDefault === '1' ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status">
          <template #default="{ row }">
            <el-tag :type="row.status === '1' ? 'success' : 'danger'">{{ row.status === '1' ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const loading = ref(false)
const list = ref<any[]>([])

const getList = () => {
  loading.value = true
  // TODO: Phase 1 接入后端接口
  setTimeout(() => {
    list.value = [
      { modelName: 'deepseek-chat', displayName: 'DeepSeek Chat', modelType: 'CHAT', maxTokens: 4096, temperature: 0.70, isDefault: '1', status: '1' }
    ]
    loading.value = false
  }, 300)
}

const handleAdd = () => {
  // TODO
}
const handleEdit = (row: any) => {
  // TODO
}
const handleDelete = (row: any) => {
  // TODO
}

onMounted(() => {
  getList()
})
</script>
