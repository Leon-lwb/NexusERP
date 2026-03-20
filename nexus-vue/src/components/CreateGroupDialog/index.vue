<template>
  <el-dialog
    v-model="visible"
    title="创建群聊"
    width="500px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
      <el-form-item label="群名称" prop="groupName">
        <el-input
          v-model="form.groupName"
          placeholder="请输入群名称"
          maxlength="20"
          show-word-limit
        />
      </el-form-item>
      
      <el-form-item label="群描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          placeholder="请输入群描述"
          :rows="3"
          maxlength="100"
          show-word-limit
        />
      </el-form-item>
      
      <el-form-item label="群头像" prop="avatar">
        <el-input
          v-model="form.avatar"
          placeholder="请输入群头像URL（可选）"
        />
      </el-form-item>
      
      <el-form-item label="选择成员" prop="memberIds">
        <div class="member-selector">
          <el-button type="primary" @click="showUserSelectDialog = true" class="select-members-btn">
            <el-icon><UserFilled /></el-icon>
            选择成员 ({{ selectedMembers.length }})
          </el-button>
          
          <div v-if="selectedMembers.length > 0" class="selected-members">
            <div class="selected-title">已选择 {{ selectedMembers.length }} 人</div>
            <div class="selected-list">
              <el-tag
                v-for="member in selectedMembers"
                :key="member.userId"
                closable
                @close="removeMember(member.userId)"
                class="selected-tag"
              >
                {{ member.nickname || member.userName }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-form-item>
    </el-form>
    
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="loading">
          创建群聊
        </el-button>
      </div>
    </template>
  </el-dialog>

  <!-- 人员选择对话框 -->
  <UserSelectDialog
    v-model:visible="showUserSelectDialog"
    :exclude-user-id="currentUserId"
    @confirm="handleUserSelect"
  />
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, UserFilled } from '@element-plus/icons-vue'
import { getImUserList } from '@/api/im'
import { createGroup } from '@/api/im'
import UserSelectDialog from '@/components/UserSelectDialog/index.vue'
import useUserStore from '@/store/modules/user'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['success', 'close', 'update:visible'])

const visible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const userStore = useUserStore()
const currentUserId = computed(() => {
  const userId = userStore.userId
  console.log('当前用户ID:', userId, '类型:', typeof userId)
  console.log('用户store:', userStore)
  return userId
})

const loading = ref(false)
const formRef = ref()
const showUserSelectDialog = ref(false)
const selectedMembers = ref([])

const form = reactive({
  groupName: '',
  description: '',
  avatar: '',
  memberIds: []
})

const rules = {
  groupName: [
    { required: true, message: '请输入群名称', trigger: 'blur' },
    { min: 1, max: 20, message: '群名称长度在1到20个字符', trigger: 'blur' }
  ],
  memberIds: [
    { required: true, message: '请选择群成员', trigger: 'change' }
  ]
}

// 处理用户选择
const handleUserSelect = (users) => {
  selectedMembers.value = users
  form.memberIds = users.map(user => user.userId)
  console.log('选择的成员:', users)
  console.log('成员ID列表:', form.memberIds)
}

// 移除成员
const removeMember = (userId) => {
  const index = selectedMembers.value.findIndex(member => member.userId === userId)
  if (index > -1) {
    selectedMembers.value.splice(index, 1)
    form.memberIds = selectedMembers.value.map(member => member.userId)
  }
}

const handleCreate = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    
    if (selectedMembers.value.length === 0) {
      ElMessage.warning('请至少选择一个群成员')
      return
    }
    
    loading.value = true
    
    // 保持成员ID为字符串，避免精度丢失
    const memberIds = selectedMembers.value.map(member => String(member.userId))
    console.log('选择的成员:', selectedMembers.value)
    console.log('转换后成员ID:', memberIds)
    console.log('成员ID类型:', memberIds.map(id => typeof id))
    
    const requestData = {
      groupName: form.groupName,
      description: form.description,
      avatar: form.avatar,
      memberIds: memberIds
    }
    console.log('发送的请求数据:', requestData)
    
    const response = await createGroup(requestData)
    
    if (response.code === 200) {
      ElMessage.success('群聊创建成功')
      emit('success', response.data)
      handleClose()
    } else {
      ElMessage.error(response.msg || '创建群聊失败')
    }
  } catch (error) {
    console.error('创建群聊失败:', error)
    ElMessage.error('创建群聊失败')
  } finally {
    loading.value = false
  }
}

const handleClose = () => {
  visible.value = false
  // 重置表单
  form.groupName = ''
  form.description = ''
  form.avatar = ''
  form.memberIds = []
  selectedMembers.value = []
  showUserSelectDialog.value = false
  emit('close')
}

// 监听 visible 变化
watch(() => props.visible, (newVal) => {
  if (newVal) {
    // 对话框打开时的初始化逻辑
  }
})

// 组件已通过 v-model 控制显示，无需暴露方法
</script>

<style scoped>
.member-selector {
  width: 100%;
}

.select-members-btn {
  width: 100%;
  margin-bottom: 16px;
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
