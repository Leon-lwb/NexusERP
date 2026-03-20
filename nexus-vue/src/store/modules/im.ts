import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ImMessage, ImConversation, ImUserInfo } from '@/api/im/types'
import { loginToIm, logoutFromIm, getConversationList, getMessageHistory, sendMessage, sendGroupMessage, markMessagesAsRead, getUnreadMessages } from '@/api/im'
import imWebSocket from '@/utils/im-websocket'
import { ElNotification } from 'element-plus'

let eventListenerBound = false
let webSocketBound = false

export const useImStore = defineStore('im', () => {
  const isConnected = ref(false)
  const currentUser = ref<ImUserInfo | null>(null)
  const conversations = ref<ImConversation[]>([])
  const currentConversation = ref<ImConversation | null>(null)
  const messages = ref<Map<string, ImMessage[]>>(new Map())
  const unreadTotal = ref(0)

  const totalUnreadCount = computed(() => {
    return conversations.value.reduce((total, conv) => total + conv.unreadCount, 0)
  })

  async function login() {
    try {
      const res = await loginToIm()
      if (res.code === 200) {
        currentUser.value = res.data
        connectWebSocket(res.data.username)
        await loadConversations()
        await syncUnreadMessages()
        return true
      }
      return false
    } catch (error) {
      console.error('IM login failed:', error)
      return false
    }
  }

  async function logout() {
    try {
      await logoutFromIm()
      isConnected.value = false
      currentUser.value = null
      conversations.value = []
      messages.value.clear()
    } catch (error) {
      console.error('IM logout failed:', error)
    }
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

  async function loadMessages(targetId: string, pageNum = 1, pageSize = 20) {
    try {
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
    }
  }

  async function sendPrivateMessage(toUserId: number, content: string, messageType = 'text') {
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
      return null
    }
  }

  async function sendGroupMessageApi(groupId: string, content: string, messageType = 'text') {
    try {
      const res = await sendGroupMessage({ groupId, content, messageType })
      if (res.code === 200) {
        await loadMessages(groupId)
        return true
      }
      return false
    } catch (error) {
      console.error('Send group message failed:', error)
      return false
    }
  }

  async function markAsRead(toUserId?: number, groupId?: string) {
    try {
      await markMessagesAsRead({ toUserId, groupId })
      await loadConversations()
    } catch (error) {
      console.error('Mark as read failed:', error)
    }
  }

  function handleNewMessage(message: ImMessage) {
    const targetId = `private_${message.fromUserId}`
    appendMessage(targetId, message)
    
    const conversation = conversations.value.find(c => c.id === targetId)
    if (conversation) {
      conversation.lastMessage = message.content
      conversation.lastTime = message.createTime
      const isActive = currentConversation.value?.type === 'private' && currentConversation.value.userId === message.fromUserId
      if (isActive) {
        conversation.unreadCount = 0
        markAsRead(message.fromUserId)
      } else {
        conversation.unreadCount++
      }
    } else {
      loadConversations()
    }
    
    showNotification(message)
  }

  function handleNewGroupMessage(message: ImMessage) {
    const groupId = message.groupId
    if (!groupId) return
    
    appendMessage(groupId, message)
    
    const conversation = conversations.value.find(c => c.id === groupId)
    if (conversation) {
      conversation.lastMessage = message.content
      conversation.lastTime = message.createTime
      const isActive = currentConversation.value?.type === 'group' && currentConversation.value.groupId === groupId
      if (isActive) {
        conversation.unreadCount = 0
        markAsRead(undefined, groupId)
      } else {
        conversation.unreadCount++
      }
    }
    
    showNotification(message)
  }

  function handleGroupNotification(message: any) {
    ElNotification({
      title: '群通知',
      message: message.content,
      type: 'info',
      duration: 3000
    })
    loadConversations()
  }

  function showNotification(message: ImMessage) {
    ElNotification({
      title: message.senderName || '新消息',
      message: message.content,
      type: 'info',
      duration: 3000
    })
  }

  function appendMessage(targetId: string, message: ImMessage) {
    const existingMessages = messages.value.get(targetId) || []
    if (existingMessages.some(item => item.messageId === message.messageId)) {
      return
    }
    messages.value.set(targetId, [...existingMessages, message])
    messages.value = new Map(messages.value)
  }

  function processIncomingMessage(message: ImMessage) {
    if (message.groupId) {
      handleNewGroupMessage(message)
    } else {
      handleNewMessage(message)
    }
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('im-message-received', { detail: message }))
    }
  }

  async function syncUnreadMessages() {
    try {
      const res = await getUnreadMessages()
      if (res.code === 200 && Array.isArray(res.data)) {
        res.data.forEach((message: ImMessage) => {
          processIncomingMessage(message)
        })
      }
    } catch (error) {
      console.error('Sync unread messages failed:', error)
    }
  }

  function setCurrentConversation(conversation: ImConversation | null) {
    currentConversation.value = conversation
    if (conversation) {
      conversation.unreadCount = 0
    }
  }

  function connectWebSocket(username: string) {
    if (!username) return
    if (!webSocketBound) {
      imWebSocket.on('connected', () => {
        isConnected.value = true
      })
      imWebSocket.on('disconnected', () => {
        isConnected.value = false
      })
      imWebSocket.on('new_message', (message: ImMessage) => {
        processIncomingMessage(message)
      })
      imWebSocket.on('new_group_message', (message: ImMessage) => {
        processIncomingMessage(message)
      })
      imWebSocket.on('group_notification', handleGroupNotification)
      webSocketBound = true
    }
    imWebSocket.connect(username)
  }

  if (!eventListenerBound && typeof window !== 'undefined') {
    window.addEventListener('im-group-notification', (event) => {
      const detail = (event as CustomEvent).detail
      if (detail) {
        handleGroupNotification(detail)
      }
    })
    window.addEventListener('im-ws-connected', () => {
      isConnected.value = true
    })
    window.addEventListener('im-ws-disconnected', () => {
      isConnected.value = false
    })
    eventListenerBound = true
  }

  return {
    isConnected,
    currentUser,
    conversations,
    currentConversation,
    messages,
    unreadTotal,
    totalUnreadCount,
    login,
    logout,
    loadConversations,
    loadMessages,
    sendPrivateMessage,
    sendGroupMessage: sendGroupMessageApi,
    markAsRead,
    setCurrentConversation
  }
})
