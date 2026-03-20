<template>
  <view class="group-list-page">
    <view class="page-header">
      <text class="header-title">群聊</text>
      <view class="header-right" @click="handleCreateGroup">
        <text class="create-icon">+</text>
      </view>
    </view>
    
    <scroll-view class="group-content" scroll-y>
      <view class="group-list">
        <view 
          v-for="group in groupList" 
          :key="group.groupId"
          class="group-item"
          @click="handleGroupClick(group)"
        >
          <image class="group-avatar" :src="group.avatar || defaultGroupAvatar" mode="aspectFill" />
          <view class="group-info">
            <text class="group-name">{{ group.groupName }}</text>
            <text class="group-member-count">{{ group.memberCount || 0 }}人</text>
          </view>
          <view class="group-role">
            <text class="role-tag" :class="group.role">
              {{ getRoleText(group.role) }}
            </text>
          </view>
        </view>
      </view>
      
      <view v-if="groupList.length === 0 && !loading" class="empty-state">
        <text class="empty-icon">👥</text>
        <text class="empty-text">暂无群聊</text>
        <text class="empty-tip">点击右上角创建群聊</text>
      </view>
      
      <view v-if="loading" class="loading-state">
        <text>加载中...</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { ref, onMounted } from 'vue'
import { getGroupList } from '@/api/im/group'

export default {
  setup() {
    const groupList = ref([])
    const loading = ref(false)
    const defaultGroupAvatar = '/static/images/profile.jpg'

    const loadGroups = async () => {
      loading.value = true
      try {
        const res = await getGroupList()
        if (res.code === 200) {
          groupList.value = res.data || []
        }
      } catch (error) {
        console.error('Load groups failed:', error)
        uni.showToast({
          title: '加载群聊列表失败',
          icon: 'none'
        })
      } finally {
        loading.value = false
      }
    }

    const handleCreateGroup = () => {
      uni.navigateTo({
        url: '/pages/im/group-create/index'
      })
    }

    const handleGroupClick = (group) => {
      uni.navigateTo({
        url: `/pages/im/group-detail/index?groupId=${group.groupId}`
      })
    }

    const getRoleText = (role) => {
      const roleMap = {
        owner: '群主',
        admin: '管理员',
        member: '成员'
      }
      return roleMap[role] || '成员'
    }

    onMounted(() => {
      loadGroups()
    })

    return {
      groupList,
      loading,
      defaultGroupAvatar,
      handleCreateGroup,
      handleGroupClick,
      getRoleText
    }
  }
}
</script>

<style scoped lang="scss">
.group-list-page {
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

.create-icon {
  font-size: 48rpx;
  color: #1890ff;
}

.group-content {
  height: calc(100vh - 88rpx);
}

.group-list {
  background-color: #fff;
}

.group-item {
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
  
  &:active {
    background-color: #f5f5f5;
  }
}

.group-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 12rpx;
  background-color: #f0f0f0;
  margin-right: 24rpx;
}

.group-info {
  flex: 1;
  min-width: 0;
}

.group-name {
  display: block;
  font-size: 32rpx;
  color: #333;
  margin-bottom: 8rpx;
}

.group-member-count {
  display: block;
  font-size: 24rpx;
  color: #999;
}

.group-role {
  margin-left: 16rpx;
}

.role-tag {
  display: inline-block;
  padding: 8rpx 16rpx;
  font-size: 24rpx;
  border-radius: 8rpx;
  
  &.owner {
    background-color: #fff7e6;
    color: #fa8c16;
  }
  
  &.admin {
    background-color: #e6f7ff;
    color: #1890ff;
  }
  
  &.member {
    background-color: #f5f5f5;
    color: #999;
  }
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

.loading-state {
  padding: 80rpx 0;
  text-align: center;
  font-size: 28rpx;
  color: #999;
}
</style>
