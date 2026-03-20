<template>
  <el-dialog
    v-model="visible"
    title="邀请成员"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="add-group-members-dialog">
      <!-- 群聊信息 -->
      <div class="group-info-section">
        <div class="group-avatar">
          <el-avatar :src="groupInfo?.avatar" :size="50">
            {{ groupInfo?.groupName?.charAt(0) }}
          </el-avatar>
        </div>
        <div class="group-details">
          <div class="group-name">{{ groupInfo?.groupName }}</div>
          <div class="group-member-count">{{ groupInfo?.memberCount || 0 }}人</div>
        </div>
      </div>

      <!-- 用户搜索 -->
      <div class="search-section">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索用户"
          clearable
          @input="searchUsers"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <!-- 用户列表 -->
      <div class="user-list">
        <div class="list-header">
          <span>选择用户</span>
          <span class="selected-count">已选择 {{ selectedCount }} 人</span>
        </div>
        
        <div class="user-list-content">
          <div
            v-for="user in filteredUsers"
            :key="user.userId"
            class="user-item"
            :class="{ 'selected': isUserSelected(user.userId) }"
            @click="toggleUser(user)"
          >
            <el-avatar :src="user.avatar" :size="40" class="user-avatar">
              {{ user.nickname?.charAt(0) || user.userName?.charAt(0) }}
            </el-avatar>
            <div class="user-info">
              <div class="user-name">{{ user.nickname || user.userName }}</div>
              <div class="user-desc">{{ user.userName }}</div>
            </div>
            <div class="user-actions">
              <el-checkbox 
                :model-value="isUserSelected(user.userId)"
                @change="toggleUser(user)"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- 已选择的用户 -->
      <div class="selected-users" v-if="selectedUsers.length > 0">
        <div class="selected-header">已选择的用户</div>
        <div class="selected-list">
          <div
            v-for="user in selectedUsers"
            :key="user.userId"
            class="selected-user"
          >
            <el-avatar :src="user.avatar" :size="30">
              {{ user.nickname?.charAt(0) || user.userName?.charAt(0) }}
            </el-avatar>
            <span class="selected-name">{{ user.nickname || user.userName }}</span>
            <el-button 
              type="text" 
              size="small" 
              @click="removeUser(user.userId)"
              class="remove-btn"
            >
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button 
          type="primary" 
          @click="handleAddMembers" 
          :loading="loading"
          :disabled="selectedCount === 0"
        >
          邀请加入 ({{ selectedCount }})
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Close } from '@element-plus/icons-vue'
import { getImUserList } from '@/api/im'
import { addGroupMembers } from '@/api/im'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  groupInfo: {
    type: Object,
    default: () => ({})
  },
  currentUserId: {
    type: [String, Number],
    default: null
  }
})

const emit = defineEmits(['update:visible', 'success', 'close'])

const visible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const loading = ref(false)
const searchKeyword = ref('')
const allUsers = ref([])
const selectedUserIds = ref([])

// 过滤后的用户列表（排除群主和已存在的成员）
const filteredUsers = computed(() => {
  let users = allUsers.value
  
  // 排除群主
  if (props.currentUserId) {
    users = users.filter(user => user.userId != props.currentUserId)
  }
  
  // 排除已存在的群成员
  if (props.groupInfo?.members) {
    const existingMemberIds = props.groupInfo.members.map(member => member.userId)
    users = users.filter(user => !existingMemberIds.includes(user.userId))
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

// 已选择用户数量
const selectedCount = computed(() => selectedUserIds.value.length)

// 加载用户列表
const loadUsers = async () => {
  try {
    console.log('开始加载用户列表...')
    const response = await getImUserList()
    console.log('用户列表响应:', response)
    
    if (response.code === 200) {
      allUsers.value = response.data || []
      console.log('加载用户列表成功，数量:', allUsers.value.length)
    } else {
      console.error('加载用户列表失败:', response.message)
      ElMessage.error('加载用户列表失败: ' + response.message)
    }
  } catch (error) {
    console.error('加载用户列表异常:', error)
    ElMessage.error('加载用户列表失败: ' + error.message)
  }
}

// 搜索用户
const searchUsers = () => {
  // 搜索逻辑在 computed 中处理
}

// 检查用户是否已选择
const isUserSelected = (userId: string | number) => {
  return selectedUserIds.value.includes(userId)
}

// 切换用户选择状态
const toggleUser = (user: any) => {
  const userId = user.userId
  const index = selectedUserIds.value.indexOf(userId)
  if (index > -1) {
    selectedUserIds.value.splice(index, 1)
  } else {
    selectedUserIds.value.push(userId)
  }
}

// 移除已选择的用户
const removeUser = (userId: string | number) => {
  const index = selectedUserIds.value.indexOf(userId)
  if (index > -1) {
    selectedUserIds.value.splice(index, 1)
  }
}

// 添加成员到群聊
const handleAddMembers = async () => {
  if (selectedCount.value === 0) {
    ElMessage.warning('请选择要邀请的用户')
    return
  }

  try {
    loading.value = true
    console.log('开始添加群成员，群ID:', props.groupInfo.groupId, '成员IDs:', selectedUserIds.value)
    console.log('成员ID类型:', selectedUserIds.value.map(id => ({ id, type: typeof id })))
    
    // 确保所有ID都是数字类型，使用BigInt处理大整数
    const memberIds = selectedUserIds.value.map(id => {
      let numId
      if (typeof id === 'string') {
        // 对于大整数，先检查是否超过安全整数范围
        if (id.length > 15 || parseInt(id) > Number.MAX_SAFE_INTEGER) {
          // 使用BigInt处理大整数，然后转换为字符串发送
          numId = BigInt(id).toString()
          console.log(`大整数转换: ${id} -> ${numId} (BigInt)`)
        } else {
          numId = parseInt(id, 10)
          console.log(`普通整数转换: ${id} -> ${numId}`)
        }
      } else {
        numId = id
      }
      console.log(`转换ID: ${id} (${typeof id}) -> ${numId} (${typeof numId})`)
      return numId
    })
    
    console.log('转换后的成员IDs:', memberIds)
    
    const response = await addGroupMembers(props.groupInfo.groupId, {
      memberIds: memberIds
    })
    
    console.log('添加群成员响应:', response)
    
    if (response.code === 200) {
      ElMessage.success(`成功邀请 ${selectedCount.value} 名成员加入群聊`)
      emit('success')
      handleClose()
    } else {
      console.error('添加群成员失败:', response.message)
      ElMessage.error('添加群成员失败: ' + response.message)
    }
  } catch (error) {
    console.error('添加群成员异常:', error)
    ElMessage.error('添加群成员失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

// 关闭对话框
const handleClose = () => {
  selectedUserIds.value = []
  searchKeyword.value = ''
  emit('close')
}

// 监听对话框显示状态
watch(() => props.visible, (newVal) => {
  if (newVal) {
    loadUsers()
  }
})
</script>

<style scoped>
.add-group-members-dialog {
  max-height: 500px;
  overflow-y: auto;
}

.group-info-section {
  display: flex;
  align-items: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 16px;
}

.group-avatar {
  margin-right: 12px;
}

.group-details {
  flex: 1;
}

.group-name {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}

.group-member-count {
  font-size: 14px;
  color: #909399;
}

.search-section {
  margin-bottom: 16px;
}

.user-list {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  font-size: 14px;
  font-weight: 500;
}

.selected-count {
  color: #409eff;
}

.user-list-content {
  max-height: 300px;
  overflow-y: auto;
}

.user-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background-color 0.2s;
}

.user-item:hover {
  background-color: #f5f7fa;
}

.user-item.selected {
  background-color: #ecf5ff;
}

.user-item:last-child {
  border-bottom: none;
}

.user-avatar {
  margin-right: 12px;
}

.user-info {
  flex: 1;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 2px;
}

.user-desc {
  font-size: 12px;
  color: #909399;
}

.user-actions {
  margin-left: 12px;
}

.selected-users {
  margin-top: 16px;
  padding: 16px;
  background: #f9f9f9;
  border-radius: 8px;
}

.selected-header {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 12px;
}

.selected-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.selected-user {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 20px;
  font-size: 12px;
}

.selected-name {
  margin: 0 6px;
  color: #303133;
}

.remove-btn {
  padding: 0;
  margin-left: 4px;
  color: #f56c6c;
}

.dialog-footer {
  text-align: right;
}

.dialog-footer .el-button {
  margin-left: 8px;
}
</style>
