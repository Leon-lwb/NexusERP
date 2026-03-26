<template>
  <view class="user-search-page">
    <!-- 状态栏占位 -->
    <view class="status-bar-placeholder"></view>
    <view class="search-header">
      <view class="search-input-wrapper">
        <input 
          v-model="searchKeyword"
          class="search-input"
          placeholder="搜索用户名或手机号"
          @confirm="handleSearch"
        />
        <text class="search-icon">🔍</text>
      </view>
      <text class="cancel-btn" @click="handleCancel">取消</text>
    </view>
    
    <view class="search-result">
      <view v-if="loading" class="loading-state">
        <text>搜索中...</text>
      </view>
      
      <view v-else-if="userList.length > 0" class="user-list">
        <view 
          v-for="user in userList" 
          :key="user.userId"
          class="user-item"
          @click="handleUserClick(user)"
        >
          <image class="user-avatar" :src="user.avatar || defaultAvatar" mode="aspectFill" />
          <view class="user-info">
            <text class="user-name">{{ user.nickName || user.userName }}</text>
            <text class="user-dept">{{ user.dept?.deptName || '暂无部门' }}</text>
          </view>
          <view class="user-actions">
            <text class="action-btn" @click.stop="handleStartChat(user)">发消息</text>
          </view>
        </view>
      </view>
      
      <view v-else-if="searchKeyword && !loading" class="empty-state">
        <text class="empty-icon">🔍</text>
        <text class="empty-text">未找到相关用户</text>
      </view>
      
      <view v-else class="empty-state">
        <text class="empty-icon">👥</text>
        <text class="empty-text">输入关键词搜索用户</text>
      </view>
    </view>
  </view>
</template>

<script>
import { ref } from 'vue'
import { listUser } from '@/api/system/user'

export default {
  setup() {
    const searchKeyword = ref('')
    const userList = ref([])
    const loading = ref(false)
    const defaultAvatar = '/static/images/profile.jpg'

    const handleSearch = async () => {
      if (!searchKeyword.value.trim()) {
        userList.value = []
        return
      }

      loading.value = true
      try {
        const res = await listUser({
          userName: searchKeyword.value,
          nickName: searchKeyword.value,
          phonenumber: searchKeyword.value,
          status: '0',
          pageNum: 1,
          pageSize: 20
        })
        
        if (res.code === 200) {
          userList.value = res.rows || []
        }
      } catch (error) {
        console.error('Search user failed:', error)
        uni.showToast({
          title: '搜索失败',
          icon: 'none'
        })
      } finally {
        loading.value = false
      }
    }

    const handleUserClick = (user) => {
      uni.navigateTo({
        url: `/pages/im/user-detail/index?userId=${user.userId}`
      })
    }

    const handleStartChat = (user) => {
      const conversationId = `private_${user.userId}`
      uni.navigateTo({
        url: `/pages/im/chat/index?id=${conversationId}&name=${user.nickName || user.userName}&type=private&userId=${user.userId}`
      })
    }

    const handleCancel = () => {
      uni.navigateBack()
    }

    return {
      searchKeyword,
      userList,
      loading,
      defaultAvatar,
      handleSearch,
      handleUserClick,
      handleStartChat,
      handleCancel
    }
  }
}
</script>

<style scoped lang="scss">
.user-search-page {
  min-height: 100vh;
  background-color: #f5f5f5;

  // 状态栏占位
  .status-bar-placeholder {
    height: var(--status-bar-height);
  }
}

.search-header {
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx;
  background-color: #fff;
  border-bottom: 1rpx solid #e8e8e8;
}

.search-input-wrapper {
  flex: 1;
  position: relative;
  margin-right: 24rpx;
}

.search-input {
  width: 100%;
  height: 72rpx;
  padding: 0 80rpx 0 24rpx;
  background-color: #f5f5f5;
  border-radius: 36rpx;
  font-size: 28rpx;
}

.search-icon {
  position: absolute;
  right: 24rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 32rpx;
}

.cancel-btn {
  font-size: 28rpx;
  color: #1890ff;
}

.search-result {
  margin-top: 24rpx;
}

.user-list {
  background-color: #fff;
}

.user-item {
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
  
  &:active {
    background-color: #f5f5f5;
  }
}

.user-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 12rpx;
  background-color: #f0f0f0;
  margin-right: 24rpx;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  display: block;
  font-size: 32rpx;
  color: #333;
  margin-bottom: 8rpx;
}

.user-dept {
  display: block;
  font-size: 24rpx;
  color: #999;
}

.user-actions {
  margin-left: 24rpx;
}

.action-btn {
  display: inline-block;
  padding: 12rpx 24rpx;
  background-color: #1890ff;
  color: #fff;
  font-size: 24rpx;
  border-radius: 24rpx;
}

.loading-state {
  padding: 80rpx 0;
  text-align: center;
  font-size: 28rpx;
  color: #999;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 0;
}

.empty-icon {
  font-size: 120rpx;
  margin-bottom: 24rpx;
}

.empty-text {
  font-size: 28rpx;
  color: #999;
}
</style>
