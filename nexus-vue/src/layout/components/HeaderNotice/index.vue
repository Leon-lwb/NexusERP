<template>
  <div class="header-notice"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { ElNotification } from 'element-plus'
import useUserStore from '@/store/modules/user'

const userStore = useUserStore()
let websocket: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let heartbeatTimer: ReturnType<typeof setInterval> | null = null
const lockReconnect = ref(false)
const isConnected = ref(false)

console.log('HeaderNotice 组件初始化')

function initWebSocket() {
  const username = userStore.name
  console.log('initWebSocket - userStore.name:', username)
  
  if (!username) {
    console.log('用户未登录，跳过WebSocket初始化')
    return
  }

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const wsUrl = `${protocol}//${host}/websocket/${username}`

  console.log('初始化WebSocket连接:', wsUrl)

  if (websocket && websocket.readyState === WebSocket.OPEN) {
    console.log('WebSocket 已连接')
    return
  }

  try {
    if (websocket) {
      websocket.close()
    }
    
    websocket = new WebSocket(wsUrl)
    
    websocket.onopen = () => {
      console.log('WebSocket连接成功')
      isConnected.value = true
      startHeartbeat()
      window.dispatchEvent(new CustomEvent('im-ws-connected'))
    }
    
    websocket.onerror = (e) => {
      console.error('WebSocket连接错误:', e)
      isConnected.value = false
      window.dispatchEvent(new CustomEvent('im-ws-disconnected'))
    }
    
    websocket.onclose = (e) => {
      console.log('WebSocket连接关闭:', e.code, e.reason)
      isConnected.value = false
      stopHeartbeat()
      window.dispatchEvent(new CustomEvent('im-ws-disconnected'))
      reconnect()
    }
    
    websocket.onmessage = (e) => {
      console.log('WebSocket收到消息:', e.data)
      handleMessage(e.data)
    }
  } catch (error) {
    console.error('创建WebSocket失败:', error)
    reconnect()
  }
}

function handleMessage(dataStr: string) {
  try {
    console.log('处理WebSocket消息:', dataStr)
    
    if (dataStr.includes('ping') || dataStr.includes('heartbeat') || dataStr.includes('pong')) {
      console.log('心跳消息，跳过')
      return
    }
    
    const data = JSON.parse(dataStr)
    console.log('解析后的消息:', data)
    
    if (data.type === 'new_message' || data.type === 'message' || data.type === 'new_group_message') {
      console.log('触发 im-message-received 事件')
      window.dispatchEvent(new CustomEvent('im-message-received', { detail: data }))
    } else if (data.type === 'group_notification') {
      window.dispatchEvent(new CustomEvent('im-group-notification', { detail: data }))
    }
  } catch (error) {
    console.error('解析WebSocket消息失败:', error)
  }
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (websocket && websocket.readyState === WebSocket.OPEN) {
      websocket.send(JSON.stringify({ type: 'ping' }))
    }
  }, 30000)
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

function reconnect() {
  if (lockReconnect.value) return
  lockReconnect.value = true
  
  reconnectTimer = setTimeout(() => {
    console.log('尝试WebSocket重连...')
    initWebSocket()
    lockReconnect.value = false
  }, 5000)
}

function closeWebSocket() {
  stopHeartbeat()
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (websocket) {
    websocket.close()
    websocket = null
  }
}

watch(() => userStore.name, (newName) => {
  console.log('userStore.name 变化:', newName)
  if (newName) {
    initWebSocket()
  }
})

onMounted(() => {
  console.log('HeaderNotice mounted')
  if (userStore.name) {
    initWebSocket()
  }
})

onUnmounted(() => {
  closeWebSocket()
})
</script>

<style scoped>
.header-notice {
  display: none;
}
</style>
