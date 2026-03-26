<template>
  <view class="conversation-list">
    <view 
      v-for="conv in conversations" 
      :key="conv.id"
      class="conversation-item"
      :class="{ active: currentId === conv.id }"
      @click="handleSelect(conv)"
    >
      <view class="avatar-wrapper">
        <image
          class="avatar"
          :src="getAvatarUrl(conv.avatar)"
          mode="aspectFill"
        />
        <view v-if="conv.unreadCount > 0" class="unread-badge">
          {{ conv.unreadCount > 99 ? '99+' : conv.unreadCount }}
        </view>
      </view>
      
      <view class="conversation-content">
        <view class="conversation-header">
          <text class="conversation-name">{{ conv.name }}</text>
          <text class="conversation-time">{{ formatTime(conv.lastTime) }}</text>
        </view>
        <view class="conversation-message">
          <text class="last-message">{{ conv.lastMessage || '暂无消息' }}</text>
        </view>
      </view>
    </view>
    
    <view v-if="conversations.length === 0" class="empty-state">
      <text class="empty-text">暂无会话</text>
    </view>
  </view>
</template>

<script>
import { ref, computed } from 'vue'
import { getAvatarUrl, defaultAvatar } from '@/utils/avatar'

export default {
  name: 'ConversationList',
  props: {
    conversations: {
      type: Array,
      default: () => []
    },
    currentId: {
      type: String,
      default: ''
    }
  },
  emits: ['select'],
  setup(props, { emit }) {

    const handleSelect = (conv) => {
      emit('select', conv)
    }

    const formatTime = (time) => {
      if (!time) return ''
      
      const date = new Date(time)
      const now = new Date()
      const diff = now.getTime() - date.getTime()
      
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
      if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
      if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
      
      const month = (date.getMonth() + 1).toString().padStart(2, '0')
      const day = date.getDate().toString().padStart(2, '0')
      return `${month}-${day}`
    }

    return {
      defaultAvatar,
      getAvatarUrl,
      handleSelect,
      formatTime
    }
  }
}
</script>

<style scoped lang="scss">
.conversation-list {
  background: #fff;
}

.conversation-item {
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
  transition: background-color 0.2s;
  
  &:active {
    background-color: #f5f5f5;
  }
  
  &.active {
    background-color: #e8f4ff;
  }
}

.avatar-wrapper {
  position: relative;
  margin-right: 24rpx;
}

.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 12rpx;
  background-color: #f0f0f0;
}

.unread-badge {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  min-width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  padding: 0 8rpx;
  background-color: #ff4d4f;
  color: #fff;
  font-size: 20rpx;
  text-align: center;
  border-radius: 16rpx;
}

.conversation-content {
  flex: 1;
  min-width: 0;
}

.conversation-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}

.conversation-name {
  font-size: 32rpx;
  font-weight: 500;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.conversation-time {
  font-size: 24rpx;
  color: #999;
  margin-left: 16rpx;
}

.conversation-message {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.last-message {
  font-size: 28rpx;
  color: #999;
}

.empty-state {
  padding: 120rpx 0;
  text-align: center;
}

.empty-text {
  font-size: 28rpx;
  color: #999;
}
</style>
