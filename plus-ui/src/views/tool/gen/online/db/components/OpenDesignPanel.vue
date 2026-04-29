<template>
    <div class="open-design-panel">
      <div class="panel-header">
        <h3>打开设计</h3>
        <el-button type="primary" size="small" @click="handleRefresh">刷新</el-button>
      </div>
      <div class="panel-content">
        <div class="search-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索设计名称或表名"
            clearable
            @input="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
        
        <div class="design-list">
          <div v-if="loading" class="loading-container">
            <el-skeleton :rows="5" animated />
          </div>
          
          <div v-else-if="filteredDesigns.length === 0" class="empty-container">
            <el-empty description="暂无设计文件" />
          </div>
          
          <div v-else class="design-items">
            <div
              v-for="design in filteredDesigns"
              :key="design.id"
              class="design-item"
              :class="{ active: selectedDesign?.id === design.id }"
              @click="handleSelectDesign(design)"
            >
              <div class="design-header">
                <div class="design-name">{{ design.designName }}</div>
                <div class="design-actions">
                  <el-button
                    type="text"
                    size="small"
                    @click.stop="handleEdit(design)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    type="text"
                    size="small"
                    @click.stop="handleDelete(design)"
                  >
                    删除
                  </el-button>
                </div>
              </div>
              <div class="design-info">
                <div class="info-item">
                  <span class="label">表名:</span>
                  <span class="value">{{ design.tableName || '未设置' }}</span>
                </div>
                <div class="info-item">
                  <span class="label">表数量:</span>
                  <span class="value">{{ design.tableCount || 0 }}</span>
                </div>
                <div class="info-item">
                  <span class="label">创建时间:</span>
                  <span class="value">{{ formatDate(design.createTime) }}</span>
                </div>
                <div class="info-item">
                  <span class="label">更新时间:</span>
                  <span class="value">{{ formatDate(design.updateTime) }}</span>
                </div>
              </div>
              <div class="design-description">
                {{ design.description || '暂无描述' }}
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="panel-footer">
        <el-button @click="handleCancel">取消</el-button>
        <el-button
          type="primary"
          :disabled="!selectedDesign"
          @click="handleOpen"
        >
          打开设计
        </el-button>
      </div>
    </div>
  </template>
  
  <script setup name="OpenDesignPanel" lang="ts">
  import { ref, onMounted, computed } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { Search } from '@element-plus/icons-vue'
  import { getDesignList, deleteDesign } from '@/api/online/db'
  
  interface DesignItem {
    id: string
    designName: string
    tableName?: string
    tableCount?: number
    description?: string
    createTime: string
    updateTime: string
    designData?: any
  }
  
  const emit = defineEmits<{
    open: [design: DesignItem]
    cancel: []
  }>()
  
  const loading = ref(false)
  const searchKeyword = ref('')
  const designList = ref<DesignItem[]>([])
  const selectedDesign = ref<DesignItem | null>(null)
  
  // 过滤后的设计列表
  const filteredDesigns = computed(() => {
    if (!searchKeyword.value) {
      return designList.value
    }
    
    const keyword = searchKeyword.value.toLowerCase()
    return designList.value.filter(design => 
      design.designName.toLowerCase().includes(keyword) ||
      (design.tableName && design.tableName.toLowerCase().includes(keyword)) ||
      (design.description && design.description.toLowerCase().includes(keyword))
    )
  })
  
  // 加载设计列表
  const loadDesignList = async () => {
    try {
      loading.value = true
      const response = await getDesignList()
      const list = (response.data || []) as any[]
      designList.value = list.map((it: any) => ({
        ...it,
        id: it.id ?? it.designId
      }))
    } catch (error) {
      console.error('加载设计列表失败:', error)
      ElMessage.error('加载设计列表失败')
    } finally {
      loading.value = false
    }
  }
  
  // 搜索处理
  const handleSearch = () => {
    // 搜索逻辑在 computed 中处理
  }
  
  // 选择设计
  const handleSelectDesign = (design: DesignItem) => {
    selectedDesign.value = design
  }
  
  // 编辑设计
  const handleEdit = (design: DesignItem) => {
    selectedDesign.value = design
    handleOpen()
  }
  
  // 删除设计
  const handleDelete = async (design: DesignItem) => {
    try {
      await ElMessageBox.confirm(
        `确定要删除设计 "${design.designName}" 吗？`,
        '确认删除',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
      const delId: any = (design as any).id ?? (design as any).designId
      await deleteDesign(String(delId))
      ElMessage.success('删除成功')
      await loadDesignList()
      
      // 如果删除的是当前选中的设计，清空选择
      if ((selectedDesign.value as any)?.id === delId) {
        selectedDesign.value = null
      }
    } catch (error) {
      if (error !== 'cancel') {
        console.error('删除设计失败:', error)
        ElMessage.error('删除设计失败')
      }
    }
  }
  
  // 打开设计
  const handleOpen = () => {
    if (selectedDesign.value) {
      emit('open', selectedDesign.value)
    }
  }
  
  // 取消
  const handleCancel = () => {
    emit('cancel')
  }
  
  // 刷新
  const handleRefresh = () => {
    loadDesignList()
  }
  
  // 格式化日期
  const formatDate = (dateStr: string) => {
    if (!dateStr) return '未知'
    const date = new Date(dateStr)
    return date.toLocaleString('zh-CN')
  }
  
  // 组件挂载时加载数据
  onMounted(() => {
    loadDesignList()
  })
  
  // 暴露方法给父组件
  defineExpose({
    loadDesignList,
    refresh: handleRefresh
  })
  </script>
  
  <style lang="scss" scoped>
  .open-design-panel {
    height: 100%;
    display: flex;
    flex-direction: column;
    background: #f5f5f5;
  }
  
  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px;
    background: white;
    border-bottom: 1px solid #e4e7ed;
    
    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 500;
    }
  }
  
  .panel-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  
  .search-bar {
    padding: 16px;
    background: white;
    border-bottom: 1px solid #e4e7ed;
  }
  
  .design-list {
    flex: 1;
    padding: 16px;
    overflow-y: auto;
  }
  
  .loading-container {
    padding: 20px;
  }
  
  .empty-container {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 200px;
  }
  
  .design-items {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  
  .design-item {
    background: white;
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    padding: 16px;
    cursor: pointer;
    transition: all 0.3s ease;
    
    &:hover {
      border-color: #409eff;
      box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
    }
    
    &.active {
      border-color: #409eff;
      background: #f0f9ff;
    }
    
    .design-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
      
      .design-name {
        font-size: 16px;
        font-weight: 500;
        color: #303133;
      }
      
      .design-actions {
        display: flex;
        gap: 8px;
        opacity: 0;
        transition: opacity 0.3s ease;
      }
    }
    
    &:hover .design-actions {
      opacity: 1;
    }
    
    .design-info {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px;
      margin-bottom: 12px;
      
      .info-item {
        display: flex;
        align-items: center;
        
        .label {
          font-size: 12px;
          color: #909399;
          margin-right: 8px;
          min-width: 60px;
        }
        
        .value {
          font-size: 12px;
          color: #606266;
          flex: 1;
        }
      }
    }
    
    .design-description {
      font-size: 12px;
      color: #909399;
      line-height: 1.4;
      max-height: 40px;
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
    }
  }
  
  .panel-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    padding: 16px;
    background: white;
    border-top: 1px solid #e4e7ed;
  }
  
  :deep(.el-skeleton) {
    .el-skeleton__item {
      margin-bottom: 12px;
    }
  }
  </style>