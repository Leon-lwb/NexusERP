<template>
  <view class="contact-page">
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
              <image class="user-avatar" :src="user.avatar || defaultAvatar" mode="aspectFill" />
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
          <image class="user-avatar" :src="friend.avatar || defaultAvatar" mode="aspectFill" />
          <view class="user-info">
            <text class="user-name">{{ friend.remark || friend.nickName || friend.userName }}</text>
            <text class="user-status">{{ friend.status === 'online' ? '在线' : '离线' }}</text>
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

export default {
  setup() {
    const activeTab = ref('organization')
    const departmentList = ref([])
    const friendList = ref([])
    const defaultAvatar = '/static/images/profile.jpg'

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
        if (res.code === 200) {
          friendList.value = res.data || []
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

    onMounted(() => {
      loadDepartments()
      loadFriends()
    })

    return {
      activeTab,
      departmentList,
      friendList,
      defaultAvatar,
      toggleDepartment,
      handleSearchUser,
      handleUserClick,
      handleFriendClick
    }
  }
}
</script>

<style scoped lang="scss">
.contact-page {
  min-height: 100vh;
  background-color: #f5f5f5;
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
