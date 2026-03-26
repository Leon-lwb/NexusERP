import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getConversationList,
  getMessageHistory,
  sendMessage,
  sendGroupMessage,
  markMessagesAsRead,
  getUnreadMessages
} from '@/api/im/message'
import {
  loginToIm,
  logoutFromIm,
  getUserImInfo
} from '@/api/im/user'
import imWebSocket from '@/utils/websocket'
import constant from '@/utils/constant'
import storage from '@/utils/storage'

export const useImStore = defineStore('im', () => {
  const isConnected = ref(false)
  const currentUser = ref(null)
  const conversations = ref([])
  const currentConversation = ref(null)
  const messages = ref(new Map())
  const unreadTotal = ref(0)
  const loading = ref(false)

  const totalUnreadCount = computed(() => {
    return conversations.value.reduce((total, conv) => total + (conv.unreadCount || 0), 0)
  })

  async function login() {
    try {
      loading.value = true
      console.log('IM登录开始...')
      const res = await loginToIm()
      console.log('IM登录响应:', res)
      if (res.code === 200) {
        currentUser.value = res.data
        storage.set(constant.imUser, res.data)
        console.log('IM登录成功，准备连接WebSocket, userId:', res.data.userId)

        initWebSocket(res.data.userId)
        await loadConversations()
        await syncUnreadMessages()

        return true
      }
      console.error('IM登录失败:', res.msg)
      return false
    } catch (error) {
      console.error('IM login failed:', error)
      return false
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      await logoutFromIm()
      disconnectWebSocket()
      
      isConnected.value = false
      currentUser.value = null
      conversations.value = []
      messages.value.clear()
      storage.remove(constant.imUser)
    } catch (error) {
      console.error('IM logout failed:', error)
    }
  }

  function initWebSocket(userId) {
    imWebSocket.on('connected', () => {
      isConnected.value = true
      console.log('IM WebSocket connected')
    })

    imWebSocket.on('disconnected', () => {
      isConnected.value = false
      console.log('IM WebSocket disconnected')
    })

    imWebSocket.on('new_message', (message) => {
      handleNewMessage(message)
    })

    imWebSocket.on('new_group_message', (message) => {
      handleNewGroupMessage(message)
    })

    imWebSocket.on('group_notification', (message) => {
      handleGroupNotification(message)
    })

    imWebSocket.on('error', (error) => {
      console.error('IM WebSocket error:', error)
    })

    imWebSocket.on('reconnect-failed', () => {
      uni.showToast({
        title: '消息服务连接失败，请重新登录',
        icon: 'none',
        duration: 3000
      })
    })

    imWebSocket.connect(userId)
  }

  function disconnectWebSocket() {
    imWebSocket.disconnect()
  }

  async function loadConversations() {
    try {
      const res = await getConversationList()
      if (res.code === 200) {
        conversations.value = res.data || []
      }
    } catch (error) {
      console.error('Load conversations failed:', error)
    }
  }

  async function loadMessages(targetId, pageNum = 1, pageSize = 20) {
    try {
      loading.value = true
      const res = await getMessageHistory({ targetId, pageNum, pageSize })
      if (res.code === 200) {
        const messageList = res.data.records || res.data.list || []
        messages.value.set(targetId, messageList.reverse())
        messages.value = new Map(messages.value)
        return messageList
      }
      return []
    } catch (error) {
      console.error('Load messages failed:', error)
      return []
    } finally {
      loading.value = false
    }
  }

  async function sendPrivateMessage(toUserId, content, messageType = 'text') {
    try {
      const res = await sendMessage({ toUserId, content, messageType })
      if (res.code === 200) {
        const targetId = `private_${toUserId}`
        await loadMessages(targetId)
        return res.data
      }
      return null
    } catch (error) {
      console.error('Send message failed:', error)
      uni.showToast({
        title: '发送失败',
        icon: 'none'
      })
      return null
    }
  }

  async function sendGroupMessageApi(groupId, content, messageType = 'text') {
    try {
      const res = await sendGroupMessage({ groupId, content, messageType })
      if (res.code === 200) {
        await loadMessages(groupId)
        return true
      }
      return false
    } catch (error) {
      console.error('Send group message failed:', error)
      uni.showToast({
        title: '发送失败',
        icon: 'none'
      })
      return false
    }
  }

  async function markAsRead(toUserId, groupId) {
    try {
      await markMessagesAsRead({ toUserId, groupId })
      await loadConversations()
    } catch (error) {
      console.error('Mark as read failed:', error)
    }
  }

  async function syncUnreadMessages() {
    try {
      const res = await getUnreadMessages()
      if (res.code === 200 && Array.isArray(res.data)) {
        res.data.forEach(message => {
          processIncomingMessage(message)
        })
      }
    } catch (error) {
      console.error('Sync unread messages failed:', error)
    }
  }

  function handleNewMessage(message) {
    const targetId = `private_${message.fromUserId}`
    appendMessage(targetId, message)
    updateConversation(targetId, message)
    showNotification(message)
  }

  function handleNewGroupMessage(message) {
    const groupId = message.groupId
    if (!groupId) return

    appendMessage(groupId, message)
    updateConversation(groupId, message)
    showNotification(message)
  }

  function handleGroupNotification(message) {
    uni.showToast({
      title: message.content || '群通知',
      icon: 'none',
      duration: 3000
    })
    loadConversations()
  }

  function processIncomingMessage(message) {
    if (message.groupId) {
      handleNewGroupMessage(message)
    } else {
      handleNewMessage(message)
    }
  }

  function appendMessage(targetId, message) {
    const existingMessages = messages.value.get(targetId) || []
    if (!existingMessages.some(item => item.messageId === message.messageId)) {
      messages.value.set(targetId, [...existingMessages, message])
      messages.value = new Map(messages.value)
    }
  }

  function updateConversation(targetId, message) {
    const conversation = conversations.value.find(c => c.id === targetId)
    if (conversation) {
      conversation.lastMessage = message.content
      conversation.lastTime = message.createTime

      const isActive = currentConversation.value?.id === targetId
      if (!isActive) {
        conversation.unreadCount = (conversation.unreadCount || 0) + 1
      } else {
        if (conversation.type === 'private') {
          markAsRead(message.fromUserId)
        } else {
          markAsRead(null, message.groupId)
        }
      }
    } else {
      loadConversations()
    }
  }

  function showNotification(message) {
    if (currentConversation.value?.id === message.groupId || 
        currentConversation.value?.id === `private_${message.fromUserId}`) {
      return
    }

    uni.showToast({
      title: `${message.senderName || '新消息'}: ${message.content}`,
      icon: 'none',
      duration: 2000
    })

    if (uni.getSystemInfoSync().platform !== 'devtools') {
      plus.push.createMessage(message.content, '', {
        title: message.senderName || '新消息'
      })
    }
  }

  function setCurrentConversation(conversation) {
    currentConversation.value = conversation
    if (conversation) {
      conversation.unreadCount = 0
    }
  }

  function clearMessages(targetId) {
    messages.value.delete(targetId)
    messages.value = new Map(messages.value)
  }

  function clearAllMessages() {
    messages.value.clear()
    messages.value = new Map(messages.value)
  }

  return {
    isConnected,
    currentUser,
    conversations,
    currentConversation,
    messages,
    unreadTotal,
    totalUnreadCount,
    loading,
    login,
    logout,
    loadConversations,
    loadMessages,
    sendPrivateMessage,
    sendGroupMessage: sendGroupMessageApi,
    markAsRead,
    setCurrentConversation,
    clearMessages,
    clearAllMessages,
    syncUnreadMessages
  }
})
