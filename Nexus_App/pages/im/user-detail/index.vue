<template>
  <view class="user-detail-page">
    <view class="user-header">
      <image class="user-avatar" :src="userInfo.avatar || defaultAvatar" mode="aspectFill" />
      <text class="user-name">{{ userInfo.nickName || userInfo.userName }}</text>
      <text class="user-dept">{{ userInfo.dept?.deptName || '暂无部门' }}</text>
    </view>
    
    <view class="user-info-section">
      <view class="info-item">
        <text class="info-label">用户名</text>
        <text class="info-value">{{ userInfo.userName }}</text>
      </view>
      <view class="info-item">
        <text class="info-label">手机号</text>
        <text class="info-value">{{ userInfo.phonenumber || '未设置' }}</text>
      </view>
      <view class="info-item">
        <text class="info-label">邮箱</text>
        <text class="info-value">{{ userInfo.email || '未设置' }}</text>
      </view>
      <view class="info-item">
        <text class="info-label">状态</text>
        <text class="info-value" :class="userInfo.status === '0' ? 'status-normal' : 'status-disabled'">
          {{ userInfo.status === '0' ? '正常' : '停用' }}
        </text>
      </view>
    </view>
    
    <view class="action-section">
      <button class="action-btn primary" @click="handleStartChat">
        发送消息
      </button>
      <button 
        v-if="!isFriend" 
        class="action-btn default" 
        @click="handleAddFriend"
      >
        添加好友
      </button>
      <button 
        v-else 
        class="action-btn danger" 
        @click="handleRemoveFriend"
      >
        删除好友
      </button>
    </view>
  </view>
</template>

<script>
import { ref, onMounted } from 'vue'
import { getUserProfile } from '@/api/system/user'
import { addFriend, removeFriend, getFriendList } from '@/api/im/user'

export default {
  setup() {
    const userId = ref(null)
    const userInfo = ref({})
    const isFriend = ref(false)
    const defaultAvatar = '/static/images/profile.jpg'

    const loadUserInfo = async () => {
      try {
        const res = await getUserProfile()
        if (res.code === 200) {
          userInfo.value = res.data || res.user || {}
        }
      } catch (error) {
        console.error('Load user info failed:', error)
        uni.showToast({
          title: '加载用户信息失败',
          icon: 'none'
        })
      }
    }

    const checkFriendStatus = async () => {
      try {
        const res = await getFriendList()
        if (res.code === 200 && Array.isArray(res.data)) {
          isFriend.value = res.data.some(friend => friend.friendUserId === userId.value)
        }
      } catch (error) {
        console.error('Check friend status failed:', error)
      }
    }

    const handleStartChat = () => {
      const conversationId = `private_${userId.value}`
      const name = userInfo.value.nickName || userInfo.value.userName
      uni.navigateTo({
        url: `/pages/im/chat/index?id=${conversationId}&name=${name}&type=private&userId=${userId.value}`
      })
    }

    const handleAddFriend = async () => {
      try {
        const res = await addFriend({
          friendUserId: userId.value,
          remark: userInfo.value.nickName || userInfo.value.userName
        })
        
        if (res.code === 200) {
          uni.showToast({
            title: '添加好友成功',
            icon: 'success'
          })
          isFriend.value = true
        }
      } catch (error) {
        console.error('Add friend failed:', error)
        uni.showToast({
          title: '添加好友失败',
          icon: 'none'
        })
      }
    }

    const handleRemoveFriend = async () => {
      uni.showModal({
        title: '确认删除',
        content: '确定要删除该好友吗？',
        success: async (res) => {
          if (res.confirm) {
            try {
              const result = await removeFriend(userId.value)
              if (result.code === 200) {
                uni.showToast({
                  title: '删除好友成功',
                  icon: 'success'
                })
                isFriend.value = false
              }
            } catch (error) {
              console.error('Remove friend failed:', error)
              uni.showToast({
                title: '删除好友失败',
                icon: 'none'
              })
            }
          }
        }
      })
    }

    onMounted(() => {
      const pages = getCurrentPages()
      const currentPage = pages[pages.length - 1]
      const options = currentPage.options || {}
      
      userId.value = options.userId ? Number(options.userId) : null
      
      if (userId.value) {
        loadUserInfo()
        checkFriendStatus()
      }
    })

    return {
      userId,
      userInfo,
      isFriend,
      defaultAvatar,
      handleStartChat,
      handleAddFriend,
      handleRemoveFriend
    }
  }
}
</script>

<style scoped lang="scss">
.user-detail-page {
  min-height: 100vh;
  background-color: #f5f5f5;
}

.user-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 32rpx;
  background-color: #fff;
  margin-bottom: 24rpx;
}

.user-avatar {
  width: 160rpx;
  height: 160rpx;
  border-radius: 80rpx;
  background-color: #f0f0f0;
  margin-bottom: 24rpx;
}

.user-name {
  font-size: 36rpx;
  font-weight: 600;
  color: #333;
  margin-bottom: 12rpx;
}

.user-dept {
  font-size: 28rpx;
  color: #999;
}

.user-info-section {
  background-color: #fff;
  margin-bottom: 24rpx;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
  
  &:last-child {
    border-bottom: none;
  }
}

.info-label {
  font-size: 28rpx;
  color: #666;
}

.info-value {
  font-size: 28rpx;
  color: #333;
  
  &.status-normal {
    color: #52c41a;
  }
  
  &.status-disabled {
    color: #ff4d4f;
  }
}

.action-section {
  padding: 32rpx;
}

.action-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  margin-bottom: 24rpx;
  border-radius: 12rpx;
  font-size: 32rpx;
  border: none;
  
  &.primary {
    background-color: #1890ff;
    color: #fff;
  }
  
  &.default {
    background-color: #fff;
    color: #1890ff;
    border: 2rpx solid #1890ff;
  }
  
  &.danger {
    background-color: #fff;
    color: #ff4d4f;
    border: 2rpx solid #ff4d4f;
  }
}
</style>
