<template>
  <view class="chat-page">
    <view class="chat-header">
      <view class="header-left" @click="handleBack">
        <text class="back-icon">←</text>
      </view>
      <view class="header-title">
        <text class="title">{{ conversationName }}</text>
        <text v-if="conversationType === 'group'" class="member-count">
          {{ memberCount }}人
        </text>
      </view>
      <view class="header-right" @click="handleShowInfo">
        <text class="info-icon">⋯</text>
      </view>
    </view>
    
    <scroll-view 
      class="message-list"
      scroll-y
      :scroll-top="scrollTop"
      @scrolltoupper="handleLoadMore"
    >
      <view v-if="loading" class="loading-tip">
        <text>加载中...</text>
      </view>
      
      <message-bubble
        v-for="msg in messageList"
        :key="msg.messageId"
        :message="msg"
        :current-user-id="currentUserId"
        :current-user-avatar="currentUserAvatar"
      />
      
      <view v-if="messageList.length === 0 && !loading" class="empty-tip">
        <text>暂无消息，开始聊天吧</text>
      </view>
    </scroll-view>
    
    <message-input 
      @send="handleSendMessage"
      @focus="handleInputFocus"
      @blur="handleInputBlur"
    />
  </view>
</template>

<script>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useImStore } from '@/store/modules/im'
import MessageBubble from '@/components/im/message-bubble.vue'
import MessageInput from '@/components/im/message-input.vue'

export default {
  components: {
    MessageBubble,
    MessageInput
  },
  setup() {
    const imStore = useImStore()
    
    const conversationId = ref('')
    const conversationName = ref('')
    const conversationType = ref('private')
    const targetUserId = ref(null)
    const groupId = ref('')
    const memberCount = ref(0)
    const scrollTop = ref(0)
    const loading = ref(false)
    const pageNum = ref(1)
    const pageSize = 20

    const currentUserId = computed(() => {
      return imStore.currentUser?.userId
    })

    const currentUserAvatar = computed(() => {
      return imStore.currentUser?.avatar
    })

    const messageList = computed(() => {
      if (!conversationId.value) return []
      return imStore.messages.get(conversationId.value) || []
    })

    const loadConversationInfo = () => {
      const pages = getCurrentPages()
      const currentPage = pages[pages.length - 1]
      const options = currentPage.options || {}
      
      conversationId.value = options.id || ''
      conversationName.value = options.name || '聊天'
      conversationType.value = options.type || 'private'
      targetUserId.value = options.userId ? Number(options.userId) : null
      groupId.value = options.groupId || ''
      memberCount.value = options.memberCount ? Number(options.memberCount) : 0
    }

    const loadMessages = async () => {
      if (!conversationId.value) return
      
      loading.value = true
      try {
        await imStore.loadMessages(conversationId.value, pageNum.value, pageSize)
        scrollToBottom()
      } catch (error) {
        console.error('Load messages failed:', error)
      } finally {
        loading.value = false
      }
    }

    const handleLoadMore = async () => {
      if (loading.value) return
      
      pageNum.value++
      await loadMessages()
    }

    const handleSendMessage = async (data) => {
      try {
        if (conversationType.value === 'private' && targetUserId.value) {
          await imStore.sendPrivateMessage(targetUserId.value, data.content, data.messageType)
        } else if (conversationType.value === 'group' && groupId.value) {
          await imStore.sendGroupMessage(groupId.value, data.content, data.messageType)
        }
        
        scrollToBottom()
      } catch (error) {
        console.error('Send message failed:', error)
      }
    }

    const scrollToBottom = () => {
      nextTick(() => {
        scrollTop.value = scrollTop.value + 9999
      })
    }

    const handleInputFocus = () => {
      setTimeout(() => {
        scrollToBottom()
      }, 300)
    }

    const handleInputBlur = () => {
    }

    const handleBack = () => {
      uni.navigateBack()
    }

    const handleShowInfo = () => {
      if (conversationType.value === 'group') {
        uni.showToast({
          title: '群信息功能开发中',
          icon: 'none'
        })
      } else {
        uni.showToast({
          title: '用户信息功能开发中',
          icon: 'none'
        })
      }
    }

    onMounted(() => {
      loadConversationInfo()
      loadMessages()
      
      if (conversationId.value) {
        const conv = {
          id: conversationId.value,
          type: conversationType.value,
          userId: targetUserId.value,
          groupId: groupId.value
        }
        imStore.setCurrentConversation(conv)
      }
    })

    return {
      conversationName,
      conversationType,
      memberCount,
      scrollTop,
      loading,
      currentUserId,
      currentUserAvatar,
      messageList,
      handleLoadMore,
      handleSendMessage,
      handleInputFocus,
      handleInputBlur,
      handleBack,
      handleShowInfo
    }
  }
}
</script>

<style scoped lang="scss">
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f5f5f5;
}

.chat-header {
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
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-icon,
.info-icon {
  font-size: 40rpx;
  color: #333;
}

.header-title {
  flex: 1;
  text-align: center;
}

.title {
  font-size: 32rpx;
  font-weight: 500;
  color: #333;
}

.member-count {
  font-size: 24rpx;
  color: #999;
  margin-left: 8rpx;
}

.message-list {
  flex: 1;
  padding: 24rpx 0;
}

.loading-tip,
.empty-tip {
  padding: 40rpx;
  text-align: center;
  font-size: 28rpx;
  color: #999;
}
</style>
