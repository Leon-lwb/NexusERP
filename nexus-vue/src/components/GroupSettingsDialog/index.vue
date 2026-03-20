<template>
  <el-dialog
    v-model="visible"
    title="群聊设置"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="group-settings" v-if="groupInfo">
      <!-- 群基本信息 -->
      <div class="section">
        <div class="section-title">群信息</div>
        <div class="info-item">
          <div class="info-label">群名称</div>
          <div class="info-value">
            <el-input
              v-if="editingName"
              v-model="editForm.groupName"
              @blur="saveGroupName"
              @keyup.enter="saveGroupName"
              ref="nameInput"
            />
            <span v-else @click="startEditName" class="editable-text">
              {{ groupInfo.groupName }}
              <el-icon class="edit-icon"><Edit /></el-icon>
            </span>
          </div>
        </div>
        
        <div class="info-item">
          <div class="info-label">群描述</div>
          <div class="info-value">
            <el-input
              v-if="editingDesc"
              v-model="editForm.description"
              type="textarea"
              :rows="3"
              @blur="saveDescription"
              ref="descInput"
            />
            <span v-else @click="startEditDesc" class="editable-text">
              {{ groupInfo.description || '暂无描述' }}
              <el-icon class="edit-icon"><Edit /></el-icon>
            </span>
          </div>
        </div>
        
        <div class="info-item">
          <div class="info-label">群头像</div>
          <div class="info-value">
            <el-input
              v-if="editingAvatar"
              v-model="editForm.avatar"
              @blur="saveAvatar"
              @keyup.enter="saveAvatar"
              ref="avatarInput"
            />
            <span v-else @click="startEditAvatar" class="editable-text">
              {{ groupInfo.avatar || '暂无头像' }}
              <el-icon class="edit-icon"><Edit /></el-icon>
            </span>
          </div>
        </div>
      </div>
      
      <!-- 群成员管理 -->
      <div class="section">
        <div class="section-title">
          群成员 ({{ groupInfo.memberCount }}人)
          <el-button
            v-if="canManageMembers"
            type="primary"
            size="small"
            @click="showAddMemberDialog = true"
          >
            添加成员
          </el-button>
        </div>
        
        <div class="member-list">
          <div
            v-for="member in members"
            :key="member.userId"
            class="member-item"
          >
            <el-avatar :src="member.avatar" :size="40" class="member-avatar">
              {{ member.nickName?.charAt(0) || 'U' }}
            </el-avatar>
            <div class="member-info">
              <div class="member-name">
                {{ member.nickName || member.userName }}
                <el-tag v-if="member.role === 'owner'" type="danger" size="small">群主</el-tag>
                <el-tag v-else-if="member.role === 'admin'" type="warning" size="small">管理员</el-tag>
              </div>
              <div class="member-join-time">
                加入时间：{{ formatTime(member.joinTime) }}
              </div>
            </div>
            <div class="member-actions" v-if="canManageMembers && member.role !== 'owner'">
              <el-button
                type="danger"
                size="small"
                @click="removeMember(member)"
              >
                移除
              </el-button>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 群设置 -->
      <div class="section">
        <div class="section-title">群设置</div>
        <div class="setting-item">
          <div class="setting-label">允许群成员邀请</div>
          <el-switch
            v-model="groupSettings.allowMemberInvite"
            @change="updateGroupSetting('allowMemberInvite', $event)"
            :disabled="!isOwner"
          />
        </div>
        <div class="setting-item">
          <div class="setting-label">允许群成员修改群信息</div>
          <el-switch
            v-model="groupSettings.allowMemberModify"
            @change="updateGroupSetting('allowMemberModify', $event)"
            :disabled="!isOwner"
          />
        </div>
      </div>
      
      <!-- 危险操作 -->
      <div class="section danger-section" v-if="isOwner">
        <div class="section-title">危险操作</div>
        <div class="danger-actions">
          <el-button type="danger" @click="handleDisbandGroup">
            解散群聊
          </el-button>
        </div>
      </div>
      
      <!-- 普通成员操作 -->
      <div class="section" v-else>
        <div class="section-title">群聊操作</div>
        <div class="member-actions">
          <el-button type="danger" @click="handleQuitGroup">
            退出群聊
          </el-button>
        </div>
      </div>
    </div>
    
    <!-- 添加成员弹窗 -->
    <AddMemberDialog
      v-model:visible="showAddMemberDialog"
      :group-id="groupInfo?.groupId"
      @success="handleMemberAdded"
    />
    
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit } from '@element-plus/icons-vue'
import { getGroupInfo, getGroupMembers, updateGroupInfo, removeGroupMember, quitGroup, disbandGroup } from '@/api/im'
import AddMemberDialog from './AddMemberDialog.vue'

const emit = defineEmits(['success', 'close'])

const visible = ref(false)
const groupInfo = ref(null)
const members = ref([])
const showAddMemberDialog = ref(false)
const editingName = ref(false)
const editingDesc = ref(false)
const editingAvatar = ref(false)

const editForm = reactive({
  groupName: '',
  description: '',
  avatar: ''
})

const groupSettings = reactive({
  allowMemberInvite: true,
  allowMemberModify: false
})

const isOwner = computed(() => {
  return groupInfo.value?.ownerId === getCurrentUserId()
})

const canManageMembers = computed(() => {
  return isOwner.value || groupSettings.value.allowMemberInvite
})

const getCurrentUserId = () => {
  // 从store或localStorage获取当前用户ID
  return parseInt(localStorage.getItem('userId') || '0')
}

const loadGroupInfo = async (groupId: string) => {
  try {
    const response = await getGroupInfo(groupId)
    if (response.code === 200) {
      groupInfo.value = response.data
      groupSettings.allowMemberInvite = response.data.allowInvite === 1
      groupSettings.allowMemberModify = response.data.allowMemberModify === 1
    } else {
      ElMessage.error('获取群信息失败')
    }
  } catch (error) {
    console.error('获取群信息失败:', error)
    ElMessage.error('获取群信息失败')
  }
}

const loadMembers = async (groupId: string) => {
  try {
    const response = await getGroupMembers(groupId)
    if (response.code === 200) {
      members.value = response.data || []
    } else {
      ElMessage.error('获取群成员失败')
    }
  } catch (error) {
    console.error('获取群成员失败:', error)
    ElMessage.error('获取群成员失败')
  }
}

const startEditName = () => {
  if (!isOwner.value && !groupSettings.allowMemberModify) {
    ElMessage.warning('您没有修改权限')
    return
  }
  editingName.value = true
  editForm.groupName = groupInfo.value.groupName
  nextTick(() => {
    const input = document.querySelector('.info-item .el-input__inner') as HTMLInputElement
    if (input) input.focus()
  })
}

const saveGroupName = async () => {
  if (editForm.groupName === groupInfo.value.groupName) {
    editingName.value = false
    return
  }
  
  try {
    const response = await updateGroupInfo(groupInfo.value.groupId, {
      groupName: editForm.groupName
    })
    if (response.code === 200) {
      groupInfo.value.groupName = editForm.groupName
      ElMessage.success('群名称修改成功')
    } else {
      ElMessage.error(response.msg || '修改失败')
    }
  } catch (error) {
    ElMessage.error('修改失败')
  } finally {
    editingName.value = false
  }
}

const startEditDesc = () => {
  if (!isOwner.value && !groupSettings.allowMemberModify) {
    ElMessage.warning('您没有修改权限')
    return
  }
  editingDesc.value = true
  editForm.description = groupInfo.value.description || ''
}

const saveDescription = async () => {
  try {
    const response = await updateGroupInfo(groupInfo.value.groupId, {
      description: editForm.description
    })
    if (response.code === 200) {
      groupInfo.value.description = editForm.description
      ElMessage.success('群描述修改成功')
    } else {
      ElMessage.error(response.msg || '修改失败')
    }
  } catch (error) {
    ElMessage.error('修改失败')
  } finally {
    editingDesc.value = false
  }
}

const startEditAvatar = () => {
  if (!isOwner.value && !groupSettings.allowMemberModify) {
    ElMessage.warning('您没有修改权限')
    return
  }
  editingAvatar.value = true
  editForm.avatar = groupInfo.value.avatar || ''
}

const saveAvatar = async () => {
  try {
    const response = await updateGroupInfo(groupInfo.value.groupId, {
      avatar: editForm.avatar
    })
    if (response.code === 200) {
      groupInfo.value.avatar = editForm.avatar
      ElMessage.success('群头像修改成功')
    } else {
      ElMessage.error(response.msg || '修改失败')
    }
  } catch (error) {
    ElMessage.error('修改失败')
  } finally {
    editingAvatar.value = false
  }
}

const updateGroupSetting = async (key: string, value: boolean) => {
  try {
    const response = await updateGroupInfo(groupInfo.value.groupId, {
      [key]: value ? 1 : 0
    })
    if (response.code === 200) {
      ElMessage.success('设置更新成功')
    } else {
      ElMessage.error(response.msg || '更新失败')
      // 回滚设置
      groupSettings[key] = !value
    }
  } catch (error) {
    ElMessage.error('更新失败')
    groupSettings[key] = !value
  }
}

const removeMember = async (member: any) => {
  try {
    await ElMessageBox.confirm(
      `确定要移除成员 ${member.nickName || member.userName} 吗？`,
      '确认移除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const response = await removeGroupMember(groupInfo.value.groupId, member.userId)
    if (response.code === 200) {
      ElMessage.success('成员移除成功')
      loadMembers(groupInfo.value.groupId)
      groupInfo.value.memberCount--
    } else {
      ElMessage.error(response.msg || '移除失败')
    }
  } catch (error) {
    // 用户取消
  }
}

const handleMemberAdded = () => {
  loadMembers(groupInfo.value.groupId)
  groupInfo.value.memberCount++
}

const handleQuitGroup = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要退出群聊吗？',
      '确认退出',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const response = await quitGroup(groupInfo.value.groupId)
    if (response.code === 200) {
      ElMessage.success('已退出群聊')
      emit('success')
      handleClose()
    } else {
      ElMessage.error(response.msg || '退出失败')
    }
  } catch (error) {
    // 用户取消
  }
}

const handleDisbandGroup = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要解散群聊吗？此操作不可撤销！',
      '确认解散',
      {
        confirmButtonText: '确定解散',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
    
    const response = await disbandGroup(groupInfo.value.groupId)
    if (response.code === 200) {
      ElMessage.success('群聊已解散')
      emit('success')
      handleClose()
    } else {
      ElMessage.error(response.msg || '解散失败')
    }
  } catch (error) {
    // 用户取消
  }
}

const formatTime = (time: string) => {
  if (!time) return '未知'
  return new Date(time).toLocaleString()
}

const handleClose = () => {
  visible.value = false
  groupInfo.value = null
  members.value = []
  editingName.value = false
  editingDesc.value = false
  editingAvatar.value = false
  emit('close')
}

const open = (groupId: string) => {
  visible.value = true
  loadGroupInfo(groupId)
  loadMembers(groupId)
}

defineExpose({
  open
})
</script>

<style scoped>
.group-settings {
  max-height: 600px;
  overflow-y: auto;
}

.section {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

.section:last-child {
  border-bottom: none;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-item {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

.info-label {
  width: 80px;
  color: #606266;
  font-size: 14px;
}

.info-value {
  flex: 1;
}

.editable-text {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.editable-text:hover {
  background-color: #f5f7fa;
}

.edit-icon {
  font-size: 12px;
  color: #909399;
}

.member-list {
  max-height: 300px;
  overflow-y: auto;
}

.member-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  margin-bottom: 8px;
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
  display: flex;
  align-items: center;
  gap: 8px;
}

.member-join-time {
  font-size: 12px;
  color: #909399;
}

.member-actions {
  margin-left: 12px;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.setting-label {
  font-size: 14px;
  color: #606266;
}

.danger-section {
  border-color: #f56c6c;
}

.danger-actions {
  text-align: center;
}

.dialog-footer {
  text-align: right;
}
</style>
