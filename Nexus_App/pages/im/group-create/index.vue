<template>
  <view class="group-create-page">
    <!-- 状态栏占位 -->
    <view class="status-bar-placeholder"></view>
    <view class="page-header">
      <view class="header-left" @click="handleCancel">
        <text class="cancel-text">取消</text>
      </view>
      <text class="header-title">创建群聊</text>
      <view class="header-right" @click="handleCreate">
        <text class="create-text">创建</text>
      </view>
    </view>
    
    <view class="form-section">
      <view class="form-item">
        <text class="form-label">群名称</text>
        <input 
          v-model="groupName"
          class="form-input"
          placeholder="请输入群名称"
          maxlength="20"
        />
      </view>
      
      <view class="form-item">
        <text class="form-label">群描述</text>
        <textarea 
          v-model="description"
          class="form-textarea"
          placeholder="请输入群描述（选填）"
          maxlength="100"
        />
      </view>
    </view>
    
    <view class="member-section">
      <view class="section-header">
        <text class="section-title">选择成员</text>
        <text class="selected-count">已选 {{ selectedMembers.length }}人</text>
      </view>
      
      <view class="member-list">
        <view 
          v-for="user in userList" 
          :key="user.userId"
          class="member-item"
          @click="toggleMember(user)"
        >
          <view class="checkbox" :class="{ checked: isSelected(user.userId) }">
            <text v-if="isSelected(user.userId)" class="check-icon">✓</text>
          </view>
          <image class="member-avatar" :src="user.avatar || defaultAvatar" mode="aspectFill" />
          <view class="member-info">
            <text class="member-name">{{ user.nickName || user.userName }}</text>
            <text class="member-dept">{{ user.dept?.deptName || '暂无部门' }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { ref, computed, onMounted } from 'vue'
import { listUser } from '@/api/system/user'
import { createGroup } from '@/api/im/group'

export default {
  setup() {
    const groupName = ref('')
    const description = ref('')
    const userList = ref([])
    const selectedMembers = ref([])
    const loading = ref(false)
    const defaultAvatar = '/static/images/profile.jpg'

    const isSelected = (userId) => {
      return selectedMembers.value.some(m => m.userId === userId)
    }

    const toggleMember = (user) => {
      const index = selectedMembers.value.findIndex(m => m.userId === user.userId)
      if (index > -1) {
        selectedMembers.value.splice(index, 1)
      } else {
        selectedMembers.value.push(user)
      }
    }

    const loadUsers = async () => {
      try {
        const res = await listUser({ pageNum: 1, pageSize: 100 })
        if (res.code === 200) {
          userList.value = res.rows || []
        }
      } catch (error) {
        console.error('Load users failed:', error)
      }
    }

    const handleCancel = () => {
      uni.navigateBack()
    }

    const handleCreate = async () => {
      if (!groupName.value.trim()) {
        uni.showToast({
          title: '请输入群名称',
          icon: 'none'
        })
        return
      }
      
      if (selectedMembers.value.length === 0) {
        uni.showToast({
          title: '请至少选择一个成员',
          icon: 'none'
        })
        return
      }
      
      loading.value = true
      try {
        const memberIds = selectedMembers.value.map(m => m.userId)
        const res = await createGroup({
          groupName: groupName.value,
          description: description.value,
          memberIds
        })
        
        if (res.code === 200) {
          uni.showToast({
            title: '创建成功',
            icon: 'success'
          })
          
          setTimeout(() => {
            uni.navigateBack()
          }, 1500)
        }
      } catch (error) {
        console.error('Create group failed:', error)
        uni.showToast({
          title: '创建失败',
          icon: 'none'
        })
      } finally {
        loading.value = false
      }
    }

    onMounted(() => {
      loadUsers()
    })

    return {
      groupName,
      description,
      userList,
      selectedMembers,
      loading,
      defaultAvatar,
      isSelected,
      toggleMember,
      handleCancel,
      handleCreate
    }
  }
}
</script>

<style scoped lang="scss">
.group-create-page {
  min-height: 100vh;
  background-color: #f5f5f5;

  // 状态栏占位
  .status-bar-placeholder {
    height: var(--status-bar-height);
  }
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 32rpx;
  background-color: #fff;
  border-bottom: 1rpx solid #e8e8e8;
}

.header-left,
.header-right {
  min-width: 80rpx;
}

.cancel-text {
  font-size: 28rpx;
  color: #666;
}

.header-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
}

.create-text {
  font-size: 28rpx;
  color: #1890ff;
}

.form-section {
  background-color: #fff;
  margin-bottom: 24rpx;
}

.form-item {
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.form-label {
  display: block;
  font-size: 28rpx;
  color: #333;
  margin-bottom: 16rpx;
}

.form-input {
  width: 100%;
  height: 72rpx;
  padding: 0 24rpx;
  background-color: #f5f5f5;
  border-radius: 12rpx;
  font-size: 28rpx;
}

.form-textarea {
  width: 100%;
  min-height: 160rpx;
  padding: 24rpx;
  background-color: #f5f5f5;
  border-radius: 12rpx;
  font-size: 28rpx;
}

.member-section {
  background-color: #fff;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #333;
}

.selected-count {
  font-size: 24rpx;
  color: #1890ff;
}

.member-list {
  max-height: 600rpx;
  overflow-y: auto;
}

.member-item {
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
  
  &:active {
    background-color: #f5f5f5;
  }
}

.checkbox {
  width: 40rpx;
  height: 40rpx;
  border: 2rpx solid #d9d9d9;
  border-radius: 8rpx;
  margin-right: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  
  &.checked {
    background-color: #1890ff;
    border-color: #1890ff;
  }
}

.check-icon {
  font-size: 24rpx;
  color: #fff;
}

.member-avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 8rpx;
  background-color: #f0f0f0;
  margin-right: 24rpx;
}

.member-info {
  flex: 1;
  min-width: 0;
}

.member-name {
  display: block;
  font-size: 28rpx;
  color: #333;
  margin-bottom: 8rpx;
}

.member-dept {
  display: block;
  font-size: 24rpx;
  color: #999;
}
</style>
