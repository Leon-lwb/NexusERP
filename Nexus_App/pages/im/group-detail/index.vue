<template>
  <view class="group-detail-page">
    <view class="page-header">
      <view class="header-left" @click="handleBack">
        <text class="back-icon">←</text>
      </view>
      <text class="header-title">群信息</text>
      <view class="header-right"></view>
    </view>
    
    <view class="group-header">
      <image class="group-avatar" :src="groupInfo.avatar || defaultAvatar" mode="aspectFill" />
      <text class="group-name">{{ groupInfo.groupName }}</text>
      <text class="group-desc">{{ groupInfo.description || '暂无描述' }}</text>
    </view>
    
    <view class="info-section">
      <view class="info-item">
        <text class="info-label">群成员</text>
        <text class="info-value">{{ groupInfo.memberCount || 0 }}人</text>
      </view>
      <view class="info-item">
        <text class="info-label">创建时间</text>
        <text class="info-value">{{ formatDate(groupInfo.createTime) }}</text>
      </view>
      <view class="info-item">
        <text class="info-label">我的身份</text>
        <text class="info-value" :class="myRole">{{ getRoleText(myRole) }}</text>
      </view>
    </view>
    
    <view class="member-section">
      <view class="section-header">
        <text class="section-title">群成员</text>
        <view v-if="canManageMembers" class="section-action" @click="handleAddMembers">
          <text class="action-text">添加成员</text>
        </view>
      </view>
      
      <view class="member-list">
        <view 
          v-for="member in memberList" 
          :key="member.userId"
          class="member-item"
        >
          <image class="member-avatar" :src="member.avatar || defaultAvatar" mode="aspectFill" />
          <view class="member-info">
            <text class="member-name">{{ member.nickName || member.userName }}</text>
            <text class="member-role" :class="member.role">{{ getRoleText(member.role) }}</text>
          </view>
          <view v-if="canRemoveMember(member)" class="member-action" @click="handleRemoveMember(member)">
            <text class="remove-text">移除</text>
          </view>
        </view>
      </view>
    </view>
    
    <view class="action-section">
      <button class="action-btn primary" @click="handleStartChat">
        发送消息
      </button>
      <button v-if="isOwner" class="action-btn danger" @click="handleDisbandGroup">
        解散群聊
      </button>
      <button v-else class="action-btn default" @click="handleQuitGroup">
        退出群聊
      </button>
    </view>
  </view>
</template>

<script>
import { ref, computed, onMounted } from 'vue'
import { useImStore } from '@/store/modules/im'
import { getGroupInfo, getGroupMembers, addGroupMembers, removeGroupMember, quitGroup, disbandGroup } from '@/api/im/group'

export default {
  setup() {
    const imStore = useImStore()
    const groupId = ref('')
    const groupInfo = ref({})
    const memberList = ref([])
    const defaultAvatar = '/static/images/profile.jpg'

    const myRole = computed(() => {
      const myMember = memberList.value.find(m => m.userId === imStore.currentUser?.userId)
      return myMember?.role || 'member'
    })

    const isOwner = computed(() => myRole.value === 'owner')

    const canManageMembers = computed(() => {
      return myRole.value === 'owner' || myRole.value === 'admin'
    })

    const canRemoveMember = (member) => {
      if (member.role === 'owner') return false
      if (myRole.value === 'owner') return true
      if (myRole.value === 'admin' && member.role === 'member') return true
      return false
    }

    const loadGroupInfo = async () => {
      try {
        const res = await getGroupInfo(groupId.value)
        if (res.code === 200) {
          groupInfo.value = res.data || {}
        }
      } catch (error) {
        console.error('Load group info failed:', error)
      }
    }

    const loadMembers = async () => {
      try {
        const res = await getGroupMembers(groupId.value)
        if (res.code === 200) {
          memberList.value = res.data || []
        }
      } catch (error) {
        console.error('Load members failed:', error)
      }
    }

    const handleBack = () => {
      uni.navigateBack()
    }

    const handleStartChat = () => {
      uni.navigateTo({
        url: `/pages/im/chat/index?id=${groupId.value}&name=${groupInfo.value.groupName}&type=group&groupId=${groupId.value}&memberCount=${groupInfo.value.memberCount}`
      })
    }

    const handleAddMembers = () => {
      uni.navigateTo({
        url: `/pages/im/group-add-members/index?groupId=${groupId.value}`
      })
    }

    const handleRemoveMember = (member) => {
      uni.showModal({
        title: '确认移除',
        content: `确定要移除成员 ${member.nickName || member.userName} 吗？`,
        success: async (res) => {
          if (res.confirm) {
            try {
              const result = await removeGroupMember({
                groupId: groupId.value,
                userId: member.userId
              })
              
              if (result.code === 200) {
                uni.showToast({
                  title: '移除成功',
                  icon: 'success'
                })
                loadMembers()
              }
            } catch (error) {
              console.error('Remove member failed:', error)
              uni.showToast({
                title: '移除失败',
                icon: 'none'
              })
            }
          }
        }
      })
    }

    const handleQuitGroup = () => {
      uni.showModal({
        title: '确认退出',
        content: '确定要退出该群聊吗？',
        success: async (res) => {
          if (res.confirm) {
            try {
              const result = await quitGroup({ groupId: groupId.value })
              
              if (result.code === 200) {
                uni.showToast({
                  title: '已退出群聊',
                  icon: 'success'
                })
                
                setTimeout(() => {
                  uni.navigateBack({ delta: 2 })
                }, 1500)
              }
            } catch (error) {
              console.error('Quit group failed:', error)
              uni.showToast({
                title: '退出失败',
                icon: 'none'
              })
            }
          }
        }
      })
    }

    const handleDisbandGroup = () => {
      uni.showModal({
        title: '确认解散',
        content: '确定要解散该群聊吗？此操作不可恢复！',
        success: async (res) => {
          if (res.confirm) {
            try {
              const result = await disbandGroup({ groupId: groupId.value })
              
              if (result.code === 200) {
                uni.showToast({
                  title: '群聊已解散',
                  icon: 'success'
                })
                
                setTimeout(() => {
                  uni.navigateBack({ delta: 2 })
                }, 1500)
              }
            } catch (error) {
              console.error('Disband group failed:', error)
              uni.showToast({
                title: '解散失败',
                icon: 'none'
              })
            }
          }
        }
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

    const formatDate = (date) => {
      if (!date) return ''
      const d = new Date(date)
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    }

    onMounted(() => {
      const pages = getCurrentPages()
      const currentPage = pages[pages.length - 1]
      const options = currentPage.options || {}
      
      groupId.value = options.groupId || ''
      
      if (groupId.value) {
        loadGroupInfo()
        loadMembers()
      }
    })

    return {
      groupId,
      groupInfo,
      memberList,
      defaultAvatar,
      myRole,
      isOwner,
      canManageMembers,
      canRemoveMember,
      handleBack,
      handleStartChat,
      handleAddMembers,
      handleRemoveMember,
      handleQuitGroup,
      handleDisbandGroup,
      getRoleText,
      formatDate
    }
  }
}
</script>

<style scoped lang="scss">
.group-detail-page {
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

.header-left,
.header-right {
  min-width: 64rpx;
}

.back-icon {
  font-size: 40rpx;
  color: #333;
}

.header-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
}

.group-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 32rpx;
  background-color: #fff;
  margin-bottom: 24rpx;
}

.group-avatar {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  background-color: #f0f0f0;
  margin-bottom: 24rpx;
}

.group-name {
  font-size: 36rpx;
  font-weight: 600;
  color: #333;
  margin-bottom: 12rpx;
}

.group-desc {
  font-size: 28rpx;
  color: #999;
  text-align: center;
}

.info-section {
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
  
  &.owner {
    color: #fa8c16;
  }
  
  &.admin {
    color: #1890ff;
  }
  
  &.member {
    color: #999;
  }
}

.member-section {
  background-color: #fff;
  margin-bottom: 24rpx;
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

.section-action {
  padding: 8rpx 16rpx;
}

.action-text {
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

.member-role {
  display: block;
  font-size: 24rpx;
  
  &.owner {
    color: #fa8c16;
  }
  
  &.admin {
    color: #1890ff;
  }
  
  &.member {
    color: #999;
  }
}

.member-action {
  padding: 8rpx 16rpx;
}

.remove-text {
  font-size: 24rpx;
  color: #ff4d4f;
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
