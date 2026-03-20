<template>
  <view class="message-bubble" :class="isSelf ? 'self' : 'other'">
    <view v-if="!isSelf" class="avatar-wrapper">
      <image class="avatar" :src="message.senderAvatar || defaultAvatar" mode="aspectFill" />
    </view>
    
    <view class="message-content">
      <view v-if="!isSelf" class="sender-name">
        {{ message.senderName || '未知用户' }}
      </view>
      
      <view class="bubble-wrapper">
        <view class="bubble" :class="messageType">
          <text v-if="message.messageType === 'text'" class="text-content">
            {{ message.content }}
          </text>
          
          <image 
            v-else-if="message.messageType === 'image'" 
            class="image-content"
            :src="message.content" 
            mode="widthFix"
            @click="previewImage(message.content)"
          />
          
          <view v-else class="file-content" @click="handleFileClick">
            <text class="file-icon">📎</text>
            <text class="file-name">文件</text>
          </view>
        </view>
        
        <view class="message-time">
          {{ formatTime(message.createTime) }}
        </view>
      </view>
    </view>
    
    <view v-if="isSelf" class="avatar-wrapper">
      <image class="avatar" :src="currentUserAvatar || defaultAvatar" mode="aspectFill" />
    </view>
  </view>
</template>

<script>
import { computed } from 'vue'

export default {
  name: 'MessageBubble',
  props: {
    message: {
      type: Object,
      required: true
    },
    currentUserId: {
      type: [Number, String],
      required: true
    },
    currentUserAvatar: {
      type: String,
      default: ''
    }
  },
  setup(props) {
    const defaultAvatar = '/static/images/profile.jpg'

    const isSelf = computed(() => {
      return props.message.fromUserId === props.currentUserId
    })

    const messageType = computed(() => {
      return props.message.messageType || 'text'
    })

    const formatTime = (time) => {
      if (!time) return ''
      
      const date = new Date(time)
      const hours = date.getHours().toString().padStart(2, '0')
      const minutes = date.getMinutes().toString().padStart(2, '0')
      return `${hours}:${minutes}`
    }

    const previewImage = (url) => {
      uni.previewImage({
        urls: [url],
        current: url
      })
    }

    const handleFileClick = () => {
      uni.showToast({
        title: '文件下载功能开发中',
        icon: 'none'
      })
    }

    return {
      defaultAvatar,
      isSelf,
      messageType,
      formatTime,
      previewImage,
      handleFileClick
    }
  }
}
</script>

<style scoped lang="scss">
.message-bubble {
  display: flex;
  align-items: flex-start;
  margin-bottom: 32rpx;
  padding: 0 32rpx;
  
  &.self {
    flex-direction: row-reverse;
    
    .bubble {
      background-color: #1890ff;
      color: #fff;
    }
    
    .message-time {
      text-align: right;
    }
  }
  
  &.other {
    .bubble {
      background-color: #fff;
      color: #333;
    }
  }
}

.avatar-wrapper {
  flex-shrink: 0;
}

.avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 8rpx;
  background-color: #f0f0f0;
}

.message-content {
  max-width: 520rpx;
  margin: 0 16rpx;
}

.sender-name {
  font-size: 24rpx;
  color: #999;
  margin-bottom: 8rpx;
}

.bubble-wrapper {
  display: flex;
  flex-direction: column;
}

.bubble {
  padding: 20rpx 24rpx;
  border-radius: 12rpx;
  word-wrap: break-word;
  word-break: break-all;
  
  &.image {
    padding: 8rpx;
    background-color: transparent !important;
  }
}

.text-content {
  font-size: 28rpx;
  line-height: 1.5;
}

.image-content {
  max-width: 400rpx;
  border-radius: 8rpx;
}

.file-content {
  display: flex;
  align-items: center;
  
  .file-icon {
    font-size: 32rpx;
    margin-right: 12rpx;
  }
  
  .file-name {
    font-size: 28rpx;
  }
}

.message-time {
  font-size: 20rpx;
  color: #999;
  margin-top: 8rpx;
}
</style>
