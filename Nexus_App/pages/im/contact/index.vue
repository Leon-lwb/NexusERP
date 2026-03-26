<template>
  <view class="contact-page">
    <!-- 状态栏占位 -->
    <view class="status-bar-placeholder"></view>
    <view class="page-header">
      <text class="header-title">通讯录</text>
      <view class="header-right" @click="handleSearchUser">
        <text class="search-icon">🔍</text>
      </view>
    </view>
    
    <view class="contact-tabs">
      <view 
        class="tab-item" 
        :class="{ active: activeTab === 'organization' }"
        @click="activeTab = 'organization'"
      >
        <text>组织架构</text>
      </view>
      <view 
        class="tab-item" 
        :class="{ active: activeTab === 'friends' }"
        @click="activeTab = 'friends'"
      >
        <text>好友列表</text>
      </view>
    </view>
    
    <scroll-view class="contact-content" scroll-y>
      <view v-if="activeTab === 'organization'" class="organization-tab">
        <view 
          v-for="dept in departmentList" 
          :key="dept.deptId"
          class="department-item"
        >
          <view class="department-header" @click="toggleDepartment(dept.deptId)">
            <text class="department-icon">{{ dept.expanded ? '▼' : '▶' }}</text>
            <text class="department-name">{{ dept.deptName }}</text>
            <text class="department-count">{{ dept.userCount || 0 }}人</text>
          </view>
          
          <view v-if="dept.expanded" class="user-list">
            <view 
              v-for="user in dept.users" 
              :key="user.userId"
              class="user-item"
              @click="handleUserClick(user)"
            >
              <image class="user-avatar" :src="getAvatarUrl(user.avatar)" mode="aspectFill" />
              <view class="user-info">
                <text class="user-name">{{ user.nickName || user.userName }}</text>
                <text class="user-position">{{ user.post || '员工' }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
      
      <view v-else class="friends-tab">
        <view 
          v-for="friend in friendList" 
          :key="friend.friendUserId"
          class="user-item"
          @click="handleFriendClick(friend)"
        >
          <image class="user-avatar" :src="getAvatarUrl(friend.friendInfo?.avatar)" mode="aspectFill" />
          <view class="user-info">
            <text class="user-name">{{ friend.remark || friend.friendInfo?.nickName || friend.friendInfo?.userName }}</text>
            <text class="user-status" :class="friend.onlineStatus">
              {{ formatOnlineStatus(friend.onlineStatus, friend.lastActiveTime) }}
            </text>
          </view>
        </view>
        
        <view v-if="friendList.length === 0" class="empty-state">
          <text class="empty-icon">👥</text>
          <text class="empty-text">暂无好友</text>
          <text class="empty-tip">去搜索添加好友吧</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { ref, onMounted } from 'vue'
import { listUser } from '@/api/system/user'
import { getFriendList } from '@/api/im/user'
import { getAvatarUrl, defaultAvatar } from '@/utils/avatar'

export default {
  setup() {
    const activeTab = ref('organization')
    const departmentList = ref([])
    const friendList = ref([])

    const loadDepartments = async () => {
      try {
        const res = await listUser({ pageNum: 1, pageSize: 1000 })
        if (res.code === 200) {
          const users = res.rows || []
          const deptMap = new Map()
          
          users.forEach(user => {
            const deptId = user.dept?.deptId || 'default'
            if (!deptMap.has(deptId)) {
              deptMap.set(deptId, {
                deptId,
                deptName: user.dept?.deptName || '未分配部门',
                expanded: false,
                users: [],
                userCount: 0
              })
            }
            deptMap.get(deptId).users.push(user)
            deptMap.get(deptId).userCount++
          })
          
          departmentList.value = Array.from(deptMap.values())
        }
      } catch (error) {
        console.error('Load departments failed:', error)
      }
    }

    const toggleDepartment = async (deptId) => {
      const dept = departmentList.value.find(d => d.deptId === deptId)
      if (dept) {
        dept.expanded = !dept.expanded
      }
    }

    const loadFriends = async () => {
      try {
        const res = await getFriendList()
        console.log('好友列表响应:', res)
        if (res.code === 200) {
          friendList.value = res.data || []
          console.log('好友列表数据:', friendList.value)
          // 打印第一个好友的在线状态
          if (friendList.value.length > 0) {
            console.log('第一个好友:', friendList.value[0])
            console.log('onlineStatus:', friendList.value[0].onlineStatus)
            console.log('lastActiveTime:', friendList.value[0].lastActiveTime)
          }
        }
      } catch (error) {
        console.error('Load friends failed:', error)
      }
    }

    const handleSearchUser = () => {
      uni.navigateTo({
        url: '/pages/im/user-search/index'
      })
    }

    const handleUserClick = (user) => {
      uni.navigateTo({
        url: `/pages/im/user-detail/index?userId=${user.userId}`
      })
    }

    const handleFriendClick = (friend) => {
      uni.navigateTo({
        url: `/pages/im/user-detail/index?userId=${friend.friendUserId}`
      })
    }

    // 格式化在线状态显示
    const formatOnlineStatus = (onlineStatus, lastActiveTime) => {
      if (onlineStatus === 'online') {
        return '在线'
      }
      if (!lastActiveTime) {
        return '离线'
      }
      // 计算时间差
      const now = new Date()
      const lastActive = new Date(lastActiveTime.replace(/-/g, '/'))
      const diffMinutes = Math.floor((now - lastActive) / (1000 * 60))
      
      if (diffMinutes < 1) {
        return '刚刚'
      } else if (diffMinutes < 60) {
        return `${diffMinutes}分钟前`
      } else if (diffMinutes < 1440) {
        const hours = Math.floor(diffMinutes / 60)
        return `${hours}小时前`
      } else {
        const days = Math.floor(diffMinutes / 1440)
        if (days < 7) {
          return `${days}天前`
        } else {
          return lastActiveTime.substring(5, 16) // 显示 MM-DD HH:mm
        }
      }
    }

    onMounted(() => {
      loadDepartments()
      loadFriends()
    })

    return {
      activeTab,
      departmentList,
      friendList,
      defaultAvatar,
      getAvatarUrl,
      toggleDepartment,
      handleSearchUser,
      handleUserClick,
      handleFriendClick,
      formatOnlineStatus
    }
  }
}
</script>

<style scoped lang="scss">
.contact-page {
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

.header-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #333;
}

.header-right {
  padding: 8rpx;
}

.search-icon {
  font-size: 40rpx;
}

.contact-tabs {
  display: flex;
  background-color: #fff;
  border-bottom: 1rpx solid #e8e8e8;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  font-size: 28rpx;
  color: #666;
  
  &.active {
    color: #1890ff;
    border-bottom: 4rpx solid #1890ff;
  }
}

.contact-content {
  height: calc(100vh - 200rpx);
}

.department-item {
  background-color: #fff;
  margin-bottom: 16rpx;
}

.department-header {
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.department-icon {
  font-size: 24rpx;
  margin-right: 16rpx;
  color: #999;
}

.department-name {
  flex: 1;
  font-size: 32rpx;
  color: #333;
}

.department-count {
  font-size: 24rpx;
  color: #999;
}

.user-list {
  padding-left: 48rpx;
}

.user-item {
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx;
  background-color: #fff;
  border-bottom: 1rpx solid #f0f0f0;
  
  &:active {
    background-color: #f5f5f5;
  }
}

.user-avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 8rpx;
  background-color: #f0f0f0;
  margin-right: 24rpx;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  display: block;
  font-size: 28rpx;
  color: #333;
  margin-bottom: 8rpx;
}

.user-position,
.user-status {
  display: block;
  font-size: 24rpx;
  color: #999;
}

.user-status.online {
  color: #52c41a;
}

.user-status.offline {
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
  margin-bottom: 16rpx;
}

.empty-tip {
  font-size: 24rpx;
  color: #ccc;
}
</style>
