<template>
  <view class="friend-request-message">
    <view class="request-header">
      <image class="user-avatar" :src="getAvatarUrl(fromUserInfo.avatar)" mode="aspectFill" />
      <view class="user-info">
        <text class="user-name">{{ fromUserInfo.nickName || fromUserInfo.userName }}</text>
        <text class="request-tip">请求添加你为好友</text>
      </view>
    </view>
    
    <view v-if="requestMessage" class="request-content">
      <text class="content-text">{{ requestMessage }}</text>
    </view>
    
    <view v-if="status === 'pending'" class="request-actions">
      <button class="btn-reject" @click="handleReject">拒绝</button>
      <button class="btn-accept" @click="handleAccept">同意</button>
    </view>
    
    <view v-else class="request-result">
      <view v-if="status === 'accepted'" class="result-accepted">
        <text class="result-icon">✅</text>
        <text class="result-text">已同意</text>
      </view>
      <view v-else class="result-rejected">
        <text class="result-icon">❌</text>
        <text class="result-text">已拒绝</text>
      </view>
    </view>
    
    <view class="request-time">
      {{ formatTime(message.createTime) }}
    </view>
  </view>
</template>

<script>
import { ref, computed } from 'vue'
import { handleFriendRequest } from '@/api/im/friend-request'
import { getAvatarUrl, defaultAvatar } from '@/utils/avatar'

export default {
  name: 'FriendRequestMessage',
  props: {
    message: {
      type: Object,
      required: true
    }
  },
  emits: ['accept', 'reject', 'refresh'],
  setup(props, { emit }) {
    const processing = ref(false)

    const extraData = computed(() => {
      try {
        return JSON.parse(props.message.extraData || '{}')
      } catch (e) {
        return {}
      }
    })

    const fromUserInfo = computed(() => {
      return extraData.value.fromUserInfo || {}
    })

    const requestMessage = computed(() => {
      return extraData.value.requestMessage || props.message.content || ''
    })

    const status = computed(() => {
      return props.message.status || 'pending'
    })

    const handleAccept = async () => {
      if (processing.value) return
      
      try {
        processing.value = true
        uni.showLoading({ title: '处理中...' })
        
        const res = await handleFriendRequest({
          messageId: props.message.messageId,
          accept: true,
          remark: ''
        })
        
        uni.hideLoading()
        
        if (res.code === 200) {
          uni.showToast({
            title: '已同意好友申请',
            icon: 'success'
          })
          emit('accept', props.message)
          emit('refresh')
        } else {
          uni.showToast({
            title: res.msg || '操作失败',
            icon: 'none'
          })
        }
      } catch (error) {
        uni.hideLoading()
        console.error('Accept friend request failed:', error)
        uni.showToast({
          title: '操作失败',
          icon: 'none'
        })
      } finally {
        processing.value = false
      }
    }

    const handleReject = async () => {
      if (processing.value) return
      
      uni.showModal({
        title: '确认拒绝',
        content: '确定要拒绝该好友申请吗？',
        success: async (res) => {
          if (res.confirm) {
            try {
              processing.value = true
              uni.showLoading({ title: '处理中...' })
              
              const result = await handleFriendRequest({
                messageId: props.message.messageId,
                accept: false,
                remark: ''
              })
              
              uni.hideLoading()
              
              if (result.code === 200) {
                uni.showToast({
                  title: '已拒绝好友申请',
                  icon: 'success'
                })
                emit('reject', props.message)
                emit('refresh')
              } else {
                uni.showToast({
                  title: result.msg || '操作失败',
                  icon: 'none'
                })
              }
            } catch (error) {
              uni.hideLoading()
              console.error('Reject friend request failed:', error)
              uni.showToast({
                title: '操作失败',
                icon: 'none'
              })
            } finally {
              processing.value = false
            }
          }
        }
      })
    }

    const formatTime = (time) => {
      if (!time) return ''
      
      const date = new Date(time)
      const now = new Date()
      const diff = now.getTime() - date.getTime()
      
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
      if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
      
      const month = (date.getMonth() + 1).toString().padStart(2, '0')
      const day = date.getDate().toString().padStart(2, '0')
      const hours = date.getHours().toString().padStart(2, '0')
      const minutes = date.getMinutes().toString().padStart(2, '0')
      
      return `${month}-${day} ${hours}:${minutes}`
    }

    return {
      defaultAvatar,
      getAvatarUrl,
      processing,
      fromUserInfo,
      requestMessage,
      status,
      handleAccept,
      handleReject,
      formatTime
    }
  }
}
</script>

<style scoped lang="scss">
.friend-request-message {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16rpx;
  padding: 24rpx;
  min-width: 480rpx;
  box-shadow: 0 8rpx 24rpx rgba(102, 126, 234, 0.3);
}

.request-header {
  display: flex;
  align-items: center;
  margin-bottom: 24rpx;
}

.user-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 48rpx;
  border: 4rpx solid rgba(255, 255, 255, 0.3);
  margin-right: 24rpx;
}

.user-info {
  flex: 1;
}

.user-name {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
  margin-bottom: 8rpx;
}

.request-tip {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.8);
}

.request-content {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 12rpx;
  padding: 20rpx 24rpx;
  margin-bottom: 24rpx;
}

.content-text {
  font-size: 28rpx;
  color: #fff;
  line-height: 1.5;
}

.request-actions {
  display: flex;
  gap: 24rpx;
  margin-bottom: 16rpx;
}

.btn-reject,
.btn-accept {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 36rpx;
  font-size: 28rpx;
  font-weight: 500;
  border: none;
}

.btn-reject {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  
  &:active {
    background: rgba(255, 255, 255, 0.3);
  }
}

.btn-accept {
  background: #fff;
  color: #667eea;
  
  &:active {
    opacity: 0.9;
  }
}

.request-result {
  padding: 16rpx 0;
}

.result-accepted,
.result-rejected {
  display: flex;
  align-items: center;
  justify-content: center;
}

.result-icon {
  font-size: 32rpx;
  margin-right: 12rpx;
}

.result-text {
  font-size: 28rpx;
  color: #fff;
  font-weight: 500;
}

.request-time {
  text-align: center;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.6);
  margin-top: 16rpx;
}
</style>
