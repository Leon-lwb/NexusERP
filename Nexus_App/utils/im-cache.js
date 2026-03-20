import constant from '@/utils/constant'
import storage from '@/utils/storage'

const CACHE_PREFIX = 'im_cache_'
const MESSAGE_CACHE_KEY = CACHE_PREFIX + 'messages'
const CONVERSATION_CACHE_KEY = CACHE_PREFIX + 'conversations'
const CACHE_EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000 // 7天

class ImCache {
  constructor() {
    this.messageCache = new Map()
    this.conversationCache = null
    this.initCache()
  }

  initCache() {
    try {
      const cachedMessages = storage.get(MESSAGE_CACHE_KEY)
      if (cachedMessages) {
        this.messageCache = new Map(Object.entries(cachedMessages))
      }
      
      this.conversationCache = storage.get(CONVERSATION_CACHE_KEY) || null
    } catch (error) {
      console.error('Init IM cache failed:', error)
    }
  }

  saveMessage(targetId, messages) {
    try {
      const cacheData = {
        messages,
        timestamp: Date.now()
      }
      
      this.messageCache.set(targetId, cacheData)
      
      const cacheObject = Object.fromEntries(this.messageCache)
      storage.set(MESSAGE_CACHE_KEY, cacheObject)
    } catch (error) {
      console.error('Save message cache failed:', error)
    }
  }

  getMessage(targetId) {
    try {
      const cacheData = this.messageCache.get(targetId)
      
      if (!cacheData) return null
      
      if (Date.now() - cacheData.timestamp > CACHE_EXPIRE_TIME) {
        this.messageCache.delete(targetId)
        return null
      }
      
      return cacheData.messages
    } catch (error) {
      console.error('Get message cache failed:', error)
      return null
    }
  }

  appendMessage(targetId, message) {
    try {
      const messages = this.getMessage(targetId) || []
      
      if (!messages.some(m => m.messageId === message.messageId)) {
        messages.push(message)
        this.saveMessage(targetId, messages)
      }
    } catch (error) {
      console.error('Append message cache failed:', error)
    }
  }

  saveConversations(conversations) {
    try {
      const cacheData = {
        conversations,
        timestamp: Date.now()
      }
      
      this.conversationCache = cacheData
      storage.set(CONVERSATION_CACHE_KEY, cacheData)
    } catch (error) {
      console.error('Save conversation cache failed:', error)
    }
  }

  getConversations() {
    try {
      if (!this.conversationCache) return null
      
      if (Date.now() - this.conversationCache.timestamp > CACHE_EXPIRE_TIME) {
        this.conversationCache = null
        return null
      }
      
      return this.conversationCache.conversations
    } catch (error) {
      console.error('Get conversation cache failed:', error)
      return null
    }
  }

  clearMessageCache(targetId) {
    try {
      if (targetId) {
        this.messageCache.delete(targetId)
      } else {
        this.messageCache.clear()
      }
      
      const cacheObject = Object.fromEntries(this.messageCache)
      storage.set(MESSAGE_CACHE_KEY, cacheObject)
    } catch (error) {
      console.error('Clear message cache failed:', error)
    }
  }

  clearAllCache() {
    try {
      this.messageCache.clear()
      this.conversationCache = null
      storage.remove(MESSAGE_CACHE_KEY)
      storage.remove(CONVERSATION_CACHE_KEY)
    } catch (error) {
      console.error('Clear all cache failed:', error)
    }
  }

  getCacheSize() {
    let size = 0
    this.messageCache.forEach((value, key) => {
      size += JSON.stringify(value).length
    })
    
    if (this.conversationCache) {
      size += JSON.stringify(this.conversationCache).length
    }
    
    return size
  }

  cleanExpiredCache() {
    const now = Date.now()
    let cleaned = 0
    
    this.messageCache.forEach((value, key) => {
      if (now - value.timestamp > CACHE_EXPIRE_TIME) {
        this.messageCache.delete(key)
        cleaned++
      }
    })
    
    if (cleaned > 0) {
      const cacheObject = Object.fromEntries(this.messageCache)
      storage.set(MESSAGE_CACHE_KEY, cacheObject)
    }
    
    return cleaned
  }
}

const imCache = new ImCache()

export default imCache
