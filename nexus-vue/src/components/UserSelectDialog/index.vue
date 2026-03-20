<template>
  <el-dialog
    v-model="visible"
    title="选择人员"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="user-select-content">
      <!-- 搜索框 -->
      <div class="search-section">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索人员姓名或用户名"
          clearable
          class="search-input"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <!-- 用户列表 -->
      <div class="user-list">
        <div
          v-for="user in filteredUsers"
          :key="user.userId"
          class="user-item"
          :class="{ 'selected': selectedUserIds.includes(user.userId) }"
          @click="toggleUser(user)"
        >
          <el-checkbox
            :model-value="selectedUserIds.includes(user.userId)"
            @change="toggleUser(user)"
          />
          <el-avatar :src="user.avatar" :size="40" class="user-avatar">
            {{ user.nickname?.charAt(0) || user.userName?.charAt(0) || 'U' }}
          </el-avatar>
          <div class="user-info">
            <div class="user-name">{{ user.nickname || user.userName }}</div>
            <div class="user-detail">{{ user.userName }}</div>
          </div>
          <div class="user-status" :class="user.imStatus === 'online' ? 'online' : 'offline'">
            {{ user.imStatus === 'online' ? '在线' : '离线' }}
          </div>
        </div>
        
        <!-- 无数据提示 -->
        <div v-if="filteredUsers.length === 0" class="no-data">
          <el-empty description="暂无人员数据" />
        </div>
      </div>

      <!-- 已选择人员 -->
      <div v-if="selectedUsers.length > 0" class="selected-section">
        <div class="selected-title">已选择 {{ selectedUsers.length }} 人</div>
        <div class="selected-list">
          <el-tag
            v-for="user in selectedUsers"
            :key="user.userId"
            closable
            @close="removeUser(user.userId)"
            class="selected-tag"
          >
            {{ user.nickname || user.userName }}
          </el-tag>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm" :disabled="selectedUsers.length === 0">
          确定 ({{ selectedUsers.length }})
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getImUserList } from '@/api/im'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  multiple: {
    type: Boolean,
    default: true
  },
  maxCount: {
    type: Number,
    default: 0 // 0表示无限制
  },
  excludeUserId: {
    type: [String, Number],
    default: null // 要排除的用户ID
  }
})

const emit = defineEmits(['update:visible', 'confirm', 'close'])

const visible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const searchKeyword = ref('')
const allUsers = ref([])
const selectedUserIds = ref([])

// 过滤后的用户列表
const filteredUsers = computed(() => {
  let users = allUsers.value
  
  // 排除指定的用户ID
  if (props.excludeUserId) {
    console.log('排除用户ID:', props.excludeUserId, '类型:', typeof props.excludeUserId)
    console.log('过滤前用户数量:', users.length)
    users = users.filter(user => {
      const isExcluded = user.userId == props.excludeUserId
      if (isExcluded) {
        console.log('排除用户:', user.userName, 'ID:', user.userId, '类型:', typeof user.userId)
      }
      return !isExcluded
    })
    console.log('过滤后用户数量:', users.length)
  }
  
  // 搜索过滤
  if (!searchKeyword.value) {
    return users
  }
  const keyword = searchKeyword.value.toLowerCase()
  return users.filter(user => 
    (user.nickname && user.nickname.toLowerCase().includes(keyword)) ||
    (user.userName && user.userName.toLowerCase().includes(keyword))
  )
})

// 已选择的用户列表
const selectedUsers = computed(() => {
  return allUsers.value.filter(user => selectedUserIds.value.includes(user.userId))
})

// 加载用户列表
const loadUsers = async () => {
  try {
    console.log('开始加载用户列表...')
    const response = await getImUserList()
    console.log('用户列表响应:', response)
    
    if (response.code === 200) {
      allUsers.value = response.data || []
      console.log('加载用户列表成功，用户数量:', allUsers.value.length)
    } else {
      console.error('加载用户列表失败:', response.msg)
      ElMessage.error('加载用户列表失败: ' + response.msg)
    }
  } catch (error) {
    console.error('加载用户列表失败:', error)
    ElMessage.error('加载用户列表失败: ' + error.message)
  }
}

// 切换用户选择
const toggleUser = (user) => {
  const userId = user.userId
  const index = selectedUserIds.value.indexOf(userId)
  
  if (index > -1) {
    // 取消选择
    selectedUserIds.value.splice(index, 1)
  } else {
    // 检查是否超过最大选择数量
    if (props.maxCount > 0 && selectedUserIds.value.length >= props.maxCount) {
      ElMessage.warning(`最多只能选择 ${props.maxCount} 人`)
      return
    }
    // 添加选择
    selectedUserIds.value.push(userId)
  }
}

// 移除用户
const removeUser = (userId) => {
  const index = selectedUserIds.value.indexOf(userId)
  if (index > -1) {
    selectedUserIds.value.splice(index, 1)
  }
}

// 确认选择
const handleConfirm = () => {
  if (selectedUsers.value.length === 0) {
    ElMessage.warning('请至少选择一个人')
    return
  }
  
  emit('confirm', selectedUsers.value)
  handleClose()
}

// 关闭对话框
const handleClose = () => {
  visible.value = false
  // 重置状态
  searchKeyword.value = ''
  selectedUserIds.value = []
}

// 监听对话框显示状态
watch(() => props.visible, (newVal) => {
  if (newVal) {
    loadUsers()
  }
})
</script>

<style scoped>
.user-select-content {
  max-height: 500px;
  display: flex;
  flex-direction: column;
}

.search-section {
  margin-bottom: 16px;
}

.search-input {
  width: 100%;
}

.user-list {
  flex: 1;
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 8px;
}

.user-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 4px;
}

.user-item:hover {
  background-color: #f5f7fa;
}

.user-item.selected {
  background-color: #e3f2fd;
  border: 1px solid #2196f3;
}

.user-avatar {
  margin: 0 12px;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}

.user-detail {
  font-size: 12px;
  color: #909399;
}

.user-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 12px;
  background-color: #f0f0f0;
  color: #666;
}

.user-status.online {
  background-color: #e8f5e8;
  color: #67c23a;
}

.user-status.offline {
  background-color: #f0f0f0;
  color: #999;
}

.no-data {
  text-align: center;
  padding: 40px 0;
}

.selected-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.selected-title {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.selected-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.selected-tag {
  margin: 0;
}

.dialog-footer {
  text-align: right;
}

/* 自定义滚动条 */
.user-list::-webkit-scrollbar {
  width: 6px;
}

.user-list::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.user-list::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.user-list::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}
</style>
