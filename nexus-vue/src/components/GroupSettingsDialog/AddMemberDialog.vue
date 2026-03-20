<template>
  <el-dialog
    :model-value="visible"
    title="添加群成员"
    width="500px"
    :close-on-click-modal="false"
    @update:model-value="(val) => emit('update:visible', val)"
    @close="handleClose"
  >
    <div class="add-member">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索用户"
        clearable
        @input="filterUsers"
        class="search-input"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      
      <div class="member-list">
        <div
          v-for="user in filteredUsers"
          :key="user.userId"
          class="member-item"
          :class="{ 'selected': selectedMemberIds.includes(user.userId) }"
          @click="toggleMember(user)"
        >
          <el-checkbox
            :model-value="selectedMemberIds.includes(user.userId)"
            @change="toggleMember(user)"
          />
          <el-avatar :src="user.avatar" :size="32" class="member-avatar">
            {{ user.nickname?.charAt(0) || 'U' }}
          </el-avatar>
          <div class="member-info">
            <div class="member-name">{{ user.nickname || user.userName }}</div>
            <div class="member-status" :class="user.status === 'online' ? 'online' : 'offline'">
              {{ user.status === 'online' ? '在线' : '离线' }}
            </div>
          </div>
        </div>
      </div>
      
      <div class="selected-members" v-if="selectedMemberIds.length > 0">
        <div class="selected-title">已选择 {{ selectedMemberIds.length }} 人</div>
        <div class="selected-list">
          <el-tag
            v-for="userId in selectedMemberIds"
            :key="userId"
            closable
            @close="removeMember(userId)"
            class="selected-tag"
          >
            {{ getUserName(userId) }}
          </el-tag>
        </div>
      </div>
    </div>
    
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleAdd" :loading="loading">
          添加成员
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
import { addGroupMembers } from '@/api/im'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  groupId: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['update:visible', 'success', 'close'])

const loading = ref(false)
const searchKeyword = ref('')
const allUsers = ref([])
const selectedMemberIds = ref([])

const filteredUsers = computed(() => {
  if (!searchKeyword.value) {
    return allUsers.value
  }
  return allUsers.value.filter(user => 
    (user.nickname && user.nickname.includes(searchKeyword.value)) ||
    (user.userName && user.userName.includes(searchKeyword.value))
  )
})

const loadUsers = async () => {
  try {
    const response = await getImUserList()
    if (response.code === 200) {
      allUsers.value = response.data || []
    } else {
      ElMessage.error('加载用户列表失败')
    }
  } catch (error) {
    console.error('加载用户列表失败:', error)
    ElMessage.error('加载用户列表失败')
  }
}

const filterUsers = () => {
  // 搜索逻辑在computed中处理
}

const toggleMember = (user: any) => {
  const userId = user.userId
  const index = selectedMemberIds.value.indexOf(userId)
  if (index > -1) {
    selectedMemberIds.value.splice(index, 1)
  } else {
    selectedMemberIds.value.push(userId)
  }
}

const removeMember = (userId: number) => {
  const index = selectedMemberIds.value.indexOf(userId)
  if (index > -1) {
    selectedMemberIds.value.splice(index, 1)
  }
}

const getUserName = (userId: number) => {
  const user = allUsers.value.find(u => u.userId === userId)
  return user ? (user.nickname || user.userName) : '未知用户'
}

const handleAdd = async () => {
  if (selectedMemberIds.value.length === 0) {
    ElMessage.warning('请至少选择一个用户')
    return
  }
  
  try {
    loading.value = true
    
    const response = await addGroupMembers(props.groupId, {
      memberIds: selectedMemberIds.value
    })
    
    if (response.code === 200) {
      ElMessage.success('成员添加成功')
      emit('success')
      handleClose()
    } else {
      ElMessage.error(response.msg || '添加成员失败')
    }
  } catch (error) {
    console.error('添加成员失败:', error)
    ElMessage.error('添加成员失败')
  } finally {
    loading.value = false
  }
}

const handleClose = () => {
  emit('update:visible', false)
  selectedMemberIds.value = []
  searchKeyword.value = ''
  emit('close')
}

watch(() => props.visible, (newVal) => {
  if (newVal) {
    loadUsers()
  }
})
</script>

<style scoped>
.add-member {
  width: 100%;
}

.search-input {
  margin-bottom: 16px;
}

.member-list {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 8px;
}

.member-item {
  display: flex;
  align-items: center;
  padding: 8px;
  cursor: pointer;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.member-item:hover {
  background-color: #f5f7fa;
}

.member-item.selected {
  background-color: #e6f7ff;
}

.member-item .el-checkbox {
  margin-right: 12px;
}

.member-avatar {
  margin-right: 12px;
}

.member-info {
  flex: 1;
}

.member-name {
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
}

.member-status {
  font-size: 12px;
  color: #909399;
}

.member-status.online {
  color: #67c23a;
}

.selected-members {
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
</style>
