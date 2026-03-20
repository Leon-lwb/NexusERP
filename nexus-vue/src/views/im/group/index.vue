<template>
  <div class="im-group-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>群组管理</span>
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon>
            创建群组
          </el-button>
        </div>
      </template>
      
      <el-row :gutter="20">
        <el-col :span="8" v-for="group in groupList" :key="group.groupId">
          <el-card class="group-card" shadow="hover" @click="enterGroup(group)">
            <div class="group-info">
              <el-avatar :size="60" :src="group.avatar">
                {{ group.groupName?.charAt(0) || 'G' }}
              </el-avatar>
              <div class="group-detail">
                <div class="group-name">{{ group.groupName }}</div>
                <div class="group-desc">{{ group.description || '暂无描述' }}</div>
                <div class="group-meta">
                  <span>{{ group.memberCount }}人</span>
                  <el-tag v-if="group.role === 'owner'" type="warning" size="small">群主</el-tag>
                  <el-tag v-else-if="group.role === 'admin'" size="small">管理员</el-tag>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
      
      <el-empty v-if="!groupList.length && !loading" description="暂无群组" />
    </el-card>
    
    <el-dialog v-model="createDialogVisible" title="创建群组" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="群名称" required>
          <el-input v-model="createForm.groupName" placeholder="请输入群名称" />
        </el-form-item>
        <el-form-item label="群描述">
          <el-input v-model="createForm.description" type="textarea" placeholder="请输入群描述" />
        </el-form-item>
        <el-form-item label="群头像">
          <el-input v-model="createForm.avatar" placeholder="群头像URL" />
        </el-form-item>
        <el-form-item label="初始成员">
          <el-select
            v-model="createForm.memberIds"
            multiple
            filterable
            placeholder="选择初始成员"
            style="width: 100%"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.userId"
              :label="user.nickName || user.userName"
              :value="user.userId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="createLoading">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getUserGroups, createGroup } from '@/api/im'
import { listUser } from '@/api/system/user'
import type { ImGroup } from '@/api/im/types'
import type { SysUser } from '@/api/system/user/types'

const router = useRouter()
const loading = ref(false)
const createLoading = ref(false)
const groupList = ref<ImGroup[]>([])
const userOptions = ref<SysUser[]>([])
const createDialogVisible = ref(false)

const createForm = ref({
  groupName: '',
  description: '',
  avatar: '',
  memberIds: [] as number[]
})

async function getGroupList() {
  loading.value = true
  try {
    const res = await getUserGroups()
    if (res.code === 200) {
      groupList.value = res.data || []
    }
  } catch (error) {
    console.error('Get group list failed:', error)
  } finally {
    loading.value = false
  }
}

async function getUserOptions() {
  try {
    const res = await listUser({ pageNum: 1, pageSize: 100, status: '0' })
    if (res.code === 200) {
      userOptions.value = res.rows
    }
  } catch (error) {
    console.error('Get user options failed:', error)
  }
}

function showCreateDialog() {
  createForm.value = {
    groupName: '',
    description: '',
    avatar: '',
    memberIds: []
  }
  createDialogVisible.value = true
}

async function handleCreate() {
  if (!createForm.value.groupName) {
    ElMessage.warning('请输入群名称')
    return
  }
  
  createLoading.value = true
  try {
    const res = await createGroup(createForm.value)
    if (res.code === 200) {
      ElMessage.success('创建群组成功')
      createDialogVisible.value = false
      getGroupList()
    }
  } catch (error) {
    console.error('Create group failed:', error)
  } finally {
    createLoading.value = false
  }
}

function enterGroup(group: ImGroup) {
  router.push({
    path: '/im/chat',
    query: { groupId: group.groupId, name: group.groupName, type: 'group' }
  })
}

onMounted(() => {
  getGroupList()
  getUserOptions()
})
</script>

<style scoped lang="scss">
.im-group-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.group-card {
  margin-bottom: 20px;
  cursor: pointer;
  
  &:hover {
    border-color: #409eff;
  }
}

.group-info {
  display: flex;
  gap: 16px;
}

.group-detail {
  flex: 1;
  
  .group-name {
    font-weight: 500;
    font-size: 16px;
    margin-bottom: 8px;
  }
  
  .group-desc {
    font-size: 13px;
    color: #909399;
    margin-bottom: 8px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  
  .group-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: #606266;
  }
}
</style>
