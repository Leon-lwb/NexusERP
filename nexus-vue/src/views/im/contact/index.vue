<template>
  <div class="im-contact-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>通讯录</span>
          <el-input
            v-model="searchKeyword"
            placeholder="搜索用户"
            style="width: 200px"
            clearable
            @input="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
      </template>
      
      <el-table :data="userList" v-loading="loading">
        <el-table-column label="用户" width="250">
          <template #default="{ row }">
            <div class="user-info">
              <el-avatar :size="40" :src="row.avatar">
                {{ row.nickName?.charAt(0) || row.userName?.charAt(0) }}
              </el-avatar>
              <div class="user-detail">
                <div class="nickname">{{ row.nickName || row.userName }}</div>
                <div class="username">@{{ row.userName }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="phonenumber" label="手机号" />
        <el-table-column prop="dept.deptName" label="部门" />
        
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <!-- <el-button
              v-if="!isFriend(row.userId)"
              type="primary"
              link
              @click="addFriend(row)"
            >
              添加好友
            </el-button>
            <el-button
              v-else
              type="success"
              link
              @click="startChat(row)"
            >
              发消息
            </el-button> -->
            <el-button
              type="success"
              link
              @click="startChat(row)"
            >
              发消息
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { listUser } from '@/api/system/user'
import { addFriend as addFriendApi } from '@/api/im'
import type { SysUser } from '@/api/system/user/types'

const router = useRouter()
const loading = ref(false)
const searchKeyword = ref('')
const userList = ref<SysUser[]>([])
const total = ref(0)
const friendList = ref<number[]>([])

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  userName: '',
  nickName: '',
  phonenumber: '',
  status: '0'
})

async function getList() {
  loading.value = true
  try {
    const res = await listUser(queryParams.value)
    if (res.code === 200) {
      userList.value = res.rows
      total.value = res.total
    }
  } catch (error) {
    console.error('Get user list failed:', error)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.value.userName = searchKeyword.value
  queryParams.value.nickName = searchKeyword.value
  queryParams.value.pageNum = 1
  getList()
}

function isFriend(userId: number) {
  return friendList.value.includes(userId)
}

async function addFriend(user: SysUser) {
  try {
    const res = await addFriendApi({
      friendUserId: user.userId,
      remark: user.nickName || user.userName
    })
    if (res.code === 200) {
      ElMessage.success('添加好友成功')
      friendList.value.push(user.userId)
    }
  } catch (error) {
    console.error('Add friend failed:', error)
  }
}

function startChat(user: SysUser) {
  router.push({
    path: '/im/chat',
    query: { userId: user.userId, name: user.nickName || user.userName }
  })
}

onMounted(() => {
  getList()
})
</script>

<style scoped lang="scss">
.im-contact-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-detail {
  .nickname {
    font-weight: 500;
  }
  
  .username {
    font-size: 12px;
    color: #909399;
  }
}
</style>
