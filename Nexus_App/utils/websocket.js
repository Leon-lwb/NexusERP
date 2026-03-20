import config from '@/config'
import { getToken } from '@/utils/auth'
import store from '@/store'

class ImWebSocket {
  constructor() {
    this.ws = null
    this.reconnectInterval = 3000
    this.heartbeatInterval = 30000
    this.heartbeatTimer = null
    this.reconnectTimer = null
    this.messageHandlers = new Map()
    this.isConnected = false
    this.userName = ''
    this.baseUrl = config.baseUrl
    this.socketTask = null
    this.isManualClose = false
    this.reconnectCount = 0
    this.maxReconnectCount = 5
  }

  getWsUrl() {
    const protocol = this.baseUrl.includes('https') ? 'wss:' : 'ws:'
    const host = this.baseUrl.replace(/https?:\/\//, '')
    return `${protocol}//${host}/websocket/${this.userName}`
  }

  connect(userName) {
    if (!userName) {
      console.error('WebSocket: username is required')
      return
    }

    if (this.socketTask && this.isConnected) {
      console.log('WebSocket already connected')
      return
    }

    this.userName = userName
    this.isManualClose = false

    const wsUrl = this.getWsUrl()
    console.log('Connecting to WebSocket:', wsUrl)

    try {
      this.socketTask = uni.connectSocket({
        url: wsUrl,
        success: () => {
          console.log('WebSocket connecting...')
        },
        fail: (err) => {
          console.error('WebSocket connect failed:', err)
          this.handleConnectError()
        }
      })

      if (this.socketTask) {
        this.socketTask.onOpen(() => {
          console.log('WebSocket connected')
          this.isConnected = true
          this.reconnectCount = 0
          this.startHeartbeat()
          this.emit('connected', { type: 'connected' })
        })

        this.socketTask.onMessage((res) => {
          try {
            const message = JSON.parse(res.data)
            console.log('WebSocket message received:', message)
            this.handleMessage(message)
          } catch (e) {
            console.error('Parse message failed:', e)
          }
        })

        this.socketTask.onClose(() => {
          console.log('WebSocket closed')
          this.isConnected = false
          this.stopHeartbeat()
          this.emit('disconnected', { type: 'disconnected' })

          if (!this.isManualClose) {
            this.reconnect()
          }
        })

        this.socketTask.onError((error) => {
          console.error('WebSocket error:', error)
          this.isConnected = false
          this.emit('error', { type: 'error', error })

          if (!this.isManualClose) {
            this.handleConnectError()
          }
        })
      }
    } catch (e) {
      console.error('Create WebSocket failed:', e)
      this.handleConnectError()
    }
  }

  handleConnectError() {
    if (this.reconnectCount < this.maxReconnectCount) {
      this.reconnectCount++
      console.log(`WebSocket reconnect attempt ${this.reconnectCount}/${this.maxReconnectCount}`)
      this.reconnect()
    } else {
      console.error('WebSocket max reconnect attempts reached')
      this.emit('reconnect-failed', { type: 'reconnect-failed' })
    }
  }

  reconnect() {
    if (this.reconnectTimer || this.isManualClose) return

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      if (!this.isManualClose && this.userName) {
        console.log('Attempting to reconnect WebSocket...')
        this.connect(this.userName)
      }
    }, this.reconnectInterval)
  }

  disconnect() {
    this.isManualClose = true
    this.stopHeartbeat()

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }

    if (this.socketTask) {
      uni.closeSocket()
      this.socketTask = null
    }

    this.isConnected = false
    console.log('WebSocket disconnected manually')
  }

  send(message) {
    if (this.socketTask && this.isConnected) {
      const msgStr = typeof message === 'string' ? message : JSON.stringify(message)
      this.socketTask.send({
        data: msgStr,
        success: () => {
          console.log('WebSocket message sent')
        },
        fail: (err) => {
          console.error('WebSocket send failed:', err)
        }
      })
    } else {
      console.warn('WebSocket is not connected')
    }
  }

  on(event, handler) {
    if (!this.messageHandlers.has(event)) {
      this.messageHandlers.set(event, [])
    }
    this.messageHandlers.get(event).push(handler)
  }

  off(event, handler) {
    const handlers = this.messageHandlers.get(event)
    if (handlers) {
      const index = handlers.indexOf(handler)
      if (index > -1) {
        handlers.splice(index, 1)
      }
    }
  }

  emit(event, message) {
    const handlers = this.messageHandlers.get(event)
    if (handlers) {
      handlers.forEach(handler => {
        try {
          handler(message)
        } catch (e) {
          console.error(`Handler for event ${event} failed:`, e)
        }
      })
    }
  }

  handleMessage(message) {
    switch (message.type) {
      case 'new_message':
        this.emit('new_message', message)
        break
      case 'new_group_message':
        this.emit('new_group_message', message)
        break
      case 'group_notification':
        this.emit('group_notification', message)
        break
      case 'pong':
        break
      case 'ping':
        this.send({ type: 'pong' })
        break
      default:
        this.emit('message', message)
    }
  }

  startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      this.send({ type: 'ping' })
    }, this.heartbeatInterval)
  }

  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  getConnectionStatus() {
    return this.isConnected
  }
}

const imWebSocket = new ImWebSocket()

export default imWebSocket
