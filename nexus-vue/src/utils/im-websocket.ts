import { getToken } from '@/utils/auth'

export interface WebSocketMessage {
  type: string
  [key: string]: any
}

export type MessageHandler = (message: WebSocketMessage) => void

class ImWebSocket {
  private ws: WebSocket | null = null
  private reconnectInterval = 3000
  private heartbeatInterval = 30000
  private heartbeatTimer: number | null = null
  private reconnectTimer: number | null = null
  private messageHandlers: Map<string, MessageHandler[]> = new Map()
  private isConnected = false
  private userName = ''

  connect(userName: string) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      console.log('WebSocket already connected')
      return
    }

    this.userName = userName
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const wsUrl = `${protocol}//${host}/websocket/${userName}`

    console.log('Connecting to WebSocket:', wsUrl)

    try {
      this.ws = new WebSocket(wsUrl)

      this.ws.onopen = () => {
        console.log('WebSocket connected')
        this.isConnected = true
        this.startHeartbeat()
        this.emit('connected', { type: 'connected' })
      }

      this.ws.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data)
          console.log('WebSocket message received:', message)
          this.handleMessage(message)
        } catch (e) {
          console.error('Failed to parse WebSocket message:', e)
        }
      }

      this.ws.onclose = (event) => {
        console.log('WebSocket closed:', event)
        this.isConnected = false
        this.stopHeartbeat()
        this.emit('disconnected', { type: 'disconnected' })
        this.reconnect()
      }

      this.ws.onerror = (error) => {
        console.error('WebSocket error:', error)
        this.isConnected = false
        this.emit('error', { type: 'error', error })
      }
    } catch (e) {
      console.error('Failed to create WebSocket:', e)
      this.reconnect()
    }
  }

  disconnect() {
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    this.isConnected = false
  }

  send(message: WebSocketMessage) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message))
    } else {
      console.warn('WebSocket is not connected')
    }
  }

  on(event: string, handler: MessageHandler) {
    if (!this.messageHandlers.has(event)) {
      this.messageHandlers.set(event, [])
    }
    this.messageHandlers.get(event)!.push(handler)
  }

  off(event: string, handler: MessageHandler) {
    const handlers = this.messageHandlers.get(event)
    if (handlers) {
      const index = handlers.indexOf(handler)
      if (index > -1) {
        handlers.splice(index, 1)
      }
    }
  }

  private emit(event: string, message: WebSocketMessage) {
    const handlers = this.messageHandlers.get(event)
    if (handlers) {
      handlers.forEach((handler) => handler(message))
    }
  }

  private handleMessage(message: WebSocketMessage) {
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
      default:
        this.emit('message', message)
    }
  }

  private startHeartbeat() {
    this.heartbeatTimer = window.setInterval(() => {
      this.send({ type: 'ping' })
    }, this.heartbeatInterval)
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  private reconnect() {
    if (this.reconnectTimer) return

    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      if (this.userName) {
        console.log('Attempting to reconnect WebSocket...')
        this.connect(this.userName)
      }
    }, this.reconnectInterval)
  }

  getConnectionStatus() {
    return this.isConnected
  }
}

export const imWebSocket = new ImWebSocket()
export default imWebSocket
