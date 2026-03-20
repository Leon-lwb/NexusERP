<template>
  <view class="conversation-page">
    <view class="page-header">
      <text class="header-title">消息</text>
      <view class="header-right">
        <view class="header-icon" @click="handleGoToContact">
          <text class="icon-text">👥</text>
        </view>
        <text class="unread-total" v-if="totalUnreadCount > 0">
          {{ totalUnreadCount > 99 ? '99+' : totalUnreadCount }}
        </text>
      </view>
    </view>
    
    <conversation-list
      :conversations="conversations"
      :current-id="currentConversationId"
      @select="handleSelectConversation"
    />
    
    <view class="empty-state" v-if="conversations.length === 0 && !loading">
      <text class="empty-icon">💬</text>
      <text class="empty-text">暂无会话</text>
      <text class="empty-tip">去通讯录找朋友聊天吧</text>
    </view>
    
    <view class="loading-state" v-if="loading">
      <text>加载中...</text>
    </view>
  </view>
</template>

<script>
import { ref, computed, onMounted } from 'vue'
import { useImStore } from '@/store/modules/im'
import ConversationList from '@/components/im/conversation-list.vue'

export default {
  components: {
    ConversationList
  },
  setup() {
    const imStore = useImStore()
    const loading = ref(false)

    const conversations = computed(() => {
      return imStore.conversations
    })

    const currentConversationId = computed(() => {
      return imStore.currentConversation?.id || ''
    })

    const totalUnreadCount = computed(() => {
      return imStore.totalUnreadCount
    })

    const handleSelectConversation = (conv) => {
      imStore.setCurrentConversation(conv)
      
      uni.navigateTo({
        url: `/pages/im/chat/index?id=${conv.id}&name=${conv.name}&type=${conv.type}&userId=${conv.userId || ''}&groupId=${conv.groupId || ''}&memberCount=${conv.memberCount || 0}`
      })
    }

    const handleGoToContact = () => {
      uni.navigateTo({
        url: '/pages/im/contact/index'
      })
    }

    const loadData = async () => {
      loading.value = true
      try {
        if (!imStore.isConnected) {
          await imStore.login()
        }
        await imStore.loadConversations()
      } catch (error) {
        console.error('Load data failed:', error)
        uni.showToast({
          title: '加载失败',
          icon: 'none'
        })
      } finally {
        loading.value = false
      }
    }

    onMounted(() => {
      loadData()
    })

    return {
      loading,
      conversations,
      currentConversationId,
      totalUnreadCount,
      handleSelectConversation,
      handleGoToContact
    }
  }
}
</script>

<style scoped lang="scss">
.conversation-page {
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
  position: relative;
  display: flex;
  align-items: center;
}

.header-icon {
  padding: 8rpx;
  margin-right: 16rpx;
}

.icon-text {
  font-size: 40rpx;
}

.unread-total {
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

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 200rpx 0;
}

.empty-icon {
  font-size: 120rpx;
  margin-bottom: 32rpx;
}

.empty-text {
  font-size: 32rpx;
  color: #333;
  margin-bottom: 16rpx;
}

.empty-tip {
  font-size: 28rpx;
  color: #999;
}

.loading-state {
  padding: 40rpx;
  text-align: center;
  font-size: 28rpx;
  color: #999;
}
</style>
