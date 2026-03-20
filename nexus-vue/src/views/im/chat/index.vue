<template>
  <div class="im-chat-container">
    <div class="sidebar">
      <div class="sidebar-header">
        <div class="user-info">
          <el-avatar :size="40" :src="imStore.currentUser?.avatar">
            {{ imStore.currentUser?.nickname?.charAt(0) || 'U' }}
          </el-avatar>
          <div class="user-detail">
            <div class="nickname">{{ imStore.currentUser?.nickname || imStore.currentUser?.username }}</div>
            <div class="status">
              <el-tag size="small" :type="imStore.isConnected ? 'success' : 'info'">
                {{ imStore.isConnected ? '在线' : '离线' }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>
      
      <div class="conversation-list">
        <div class="conversation-header">
          <span>会话列表</span>
          <el-badge :value="imStore.totalUnreadCount" :hidden="imStore.totalUnreadCount === 0" />
        </div>
        
        <el-scrollbar>
          <div
            v-for="conv in imStore.conversations"
            :key="conv.id"
            :class="['conversation-item', { active: currentConversation?.id === conv.id }]"
            @click="selectConversation(conv)"
          >
            <el-avatar :size="45" :src="conv.avatar">
              {{ conv.name?.charAt(0) || 'G' }}
            </el-avatar>
            <div class="conversation-content">
              <div class="conversation-name">
                <span>{{ conv.name }}</span>
                <span class="time">{{ formatTime(conv.lastTime) }}</span>
              </div>
              <div class="conversation-message">
                <span class="last-message">{{ conv.lastMessage }}</span>
                <el-badge :value="conv.unreadCount" :hidden="conv.unreadCount === 0" />
              </div>
            </div>
          </div>
        </el-scrollbar>
      </div>
    </div>
    
    <div class="chat-main">
      <template v-if="currentConversation">
        <div class="chat-header">
          <div class="chat-title">
            <span>{{ currentConversation.name }}</span>
            <el-tag v-if="currentConversation.type === 'group'" size="small">
              {{ currentConversation.memberCount }}人
            </el-tag>
          </div>
          <div class="chat-actions">
            <el-button v-if="currentConversation.type === 'group'" link @click="showGroupInfo">
              <el-icon><User /></el-icon>
            </el-button>
          </div>
        </div>
        
        <div class="message-list" ref="messageListRef">
          <el-scrollbar ref="scrollbarRef">
            <div
              v-for="msg in currentMessages"
              :key="msg.messageId"
              :class="['message-item', { self: msg.fromUserId === imStore.currentUser?.userId }]"
            >
              <el-avatar :size="36" :src="msg.senderAvatar">
                {{ msg.senderName?.charAt(0) || 'U' }}
              </el-avatar>
              <div class="message-content">
                <div class="message-header">
                  <span class="sender-name">{{ msg.senderName }}</span>
                  <span class="message-time">{{ formatTime(msg.createTime) }}</span>
                </div>
                <div class="message-body">
                  <template v-if="msg.messageType === 'text'">
                    <span>{{ msg.content }}</span>
                  </template>
                  <template v-else-if="msg.messageType === 'image'">
                    <el-image :src="msg.content" fit="cover" style="max-width: 200px; max-height: 200px" />
                  </template>
                  <template v-else>
                    <el-link :href="msg.content" target="_blank">文件</el-link>
                  </template>
                </div>
              </div>
            </div>
          </el-scrollbar>
        </div>
        
        <div class="message-input">
          <div class="input-toolbar">
            <el-button link>
              <el-icon><Picture /></el-icon>
            </el-button>
            <el-button link>
              <el-icon><FolderOpened /></el-icon>
            </el-button>
          </div>
          <div class="input-area">
            <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="3"
              placeholder="输入消息..."
              @keydown.enter.ctrl="sendMessage"
            />
            <el-button type="primary" @click="sendMessage" :disabled="!inputMessage.trim()">
              发送
            </el-button>
          </div>
        </div>
      </template>
      
      <template v-else>
        <div class="empty-chat">
          <el-empty description="选择一个会话开始聊天" />
        </div>
      </template>
    </div>
    
    <el-drawer v-model="groupInfoVisible" title="群信息" size="300px">
      <div v-if="currentConversation?.type === 'group'" class="group-info">
        <div class="group-header">
          <el-avatar :size="60" :src="groupInfo?.avatar" />
          <div class="group-name">{{ groupInfo?.groupName }}</div>
          <div class="group-desc">{{ groupInfo?.description }}</div>
        </div>
        
        <el-divider>群成员</el-divider>
        
        <div class="member-list">
          <div v-for="member in groupMembers" :key="member.userId" class="member-item">
            <el-avatar :size="36" :src="member.avatar">
              {{ member.nickName?.charAt(0) || 'U' }}
            </el-avatar>
            <div class="member-info">
              <span>{{ member.nickName }}</span>
              <el-tag v-if="member.role === 'owner'" size="small" type="warning">群主</el-tag>
              <el-tag v-else-if="member.role === 'admin'" size="small">管理员</el-tag>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useImStore } from '@/store/modules/im'
import { getGroupInfo, getGroupMembers } from '@/api/im'
import type { ImConversation, ImMessage, ImGroup, ImGroupMember } from '@/api/im/types'
import { User, Picture, FolderOpened } from '@element-plus/icons-vue'

const imStore = useImStore()
const currentConversation = ref<ImConversation | null>(null)
const inputMessage = ref('')
const messageListRef = ref()
const scrollbarRef = ref()
const groupInfoVisible = ref(false)
const groupInfo = ref<ImGroup | null>(null)
const groupMembers = ref<ImGroupMember[]>([])

const currentMessages = computed(() => {
  if (!currentConversation.value) return []
  const targetId = currentConversation.value.type === 'private'
    ? `private_${currentConversation.value.userId}`
    : currentConversation.value.groupId
  return imStore.messages.get(targetId || '') || []
})

async function selectConversation(conv: ImConversation) {
  currentConversation.value = conv
  imStore.setCurrentConversation(conv)
  
  const targetId = conv.type === 'private' ? `private_${conv.userId}` : conv.groupId
  if (targetId) {
    await imStore.loadMessages(targetId)
    scrollToBottom()
    
    if (conv.unreadCount > 0) {
      if (conv.type === 'private') {
        imStore.markAsRead(conv.userId)
      } else {
        imStore.markAsRead(undefined, conv.groupId)
      }
    }
  }
}

async function sendMessage() {
  if (!inputMessage.value.trim() || !currentConversation.value) return
  
  const content = inputMessage.value.trim()
  inputMessage.value = ''
  
  if (currentConversation.value.type === 'private') {
    await imStore.sendPrivateMessage(currentConversation.value.userId!, content)
  } else {
    await imStore.sendGroupMessage(currentConversation.value.groupId!, content)
  }
  
  scrollToBottom()
}

function scrollToBottom() {
  nextTick(() => {
    if (scrollbarRef.value) {
      scrollbarRef.value.setScrollTop(scrollbarRef.value.wrapRef.scrollHeight)
    }
  })
}

function formatTime(time: string) {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  
  return date.toLocaleDateString()
}

async function showGroupInfo() {
  if (!currentConversation.value?.groupId) return
  
  try {
    const [infoRes, membersRes] = await Promise.all([
      getGroupInfo(currentConversation.value.groupId),
      getGroupMembers(currentConversation.value.groupId)
    ])
    
    if (infoRes.code === 200) {
      groupInfo.value = infoRes.data
    }
    if (membersRes.code === 200) {
      groupMembers.value = membersRes.data
    }
    
    groupInfoVisible.value = true
  } catch (error) {
    console.error('Load group info failed:', error)
  }
}

watch(() => imStore.isConnected, (connected) => {
  if (connected && !imStore.conversations.length) {
    imStore.loadConversations()
  }
})

onMounted(async () => {
  if (!imStore.isConnected) {
    await imStore.login()
  }
  await imStore.loadConversations()
})

onUnmounted(() => {
})
</script>

<style scoped lang="scss">
.im-chat-container {
  display: flex;
  height: calc(100vh - 84px);
  background: #f5f7fa;
}

.sidebar {
  width: 280px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-detail {
  .nickname {
    font-weight: 500;
    font-size: 14px;
  }
  
  .status {
    margin-top: 4px;
  }
}

.conversation-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.conversation-header {
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 500;
  border-bottom: 1px solid #e4e7ed;
}

.conversation-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.3s;
  gap: 12px;
  
  &:hover {
    background: #f5f7fa;
  }
  
  &.active {
    background: #ecf5ff;
  }
}

.conversation-content {
  flex: 1;
  overflow: hidden;
}

.conversation-name {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  
  .time {
    font-size: 12px;
    color: #909399;
  }
}

.conversation-message {
  display: flex;
  justify-content: space-between;
  align-items: center;
  
  .last-message {
    font-size: 13px;
    color: #606266;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 180px;
  }
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.chat-header {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  font-size: 16px;
}

.message-list {
  flex: 1;
  padding: 16px;
  overflow: hidden;
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  
  &.self {
    flex-direction: row-reverse;
    
    .message-content {
      align-items: flex-end;
    }
    
    .message-body {
      background: #409eff;
      color: #fff;
    }
  }
}

.message-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-header {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}

.message-body {
  padding: 10px 14px;
  background: #f5f7fa;
  border-radius: 8px;
  max-width: 400px;
  word-break: break-all;
}

.message-input {
  border-top: 1px solid #e4e7ed;
  padding: 12px;
}

.input-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.input-area {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  
  .el-textarea {
    flex: 1;
  }
}

.empty-chat {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.group-info {
  .group-header {
    text-align: center;
    margin-bottom: 20px;
    
    .group-name {
      font-size: 16px;
      font-weight: 500;
      margin-top: 12px;
    }
    
    .group-desc {
      font-size: 13px;
      color: #909399;
      margin-top: 8px;
    }
  }
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.member-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.member-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}
</style>
