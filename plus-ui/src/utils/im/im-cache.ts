/**
 * P4-6 W1: Web 端 IM 消息本地缓存（IndexedDB）
 *
 * <p>原 im.ts store 的 messagesMap 是纯内存 ref，F5 刷新即丢，秒开体验差。
 * <p>本模块用原生 IndexedDB API（零依赖，不引入 idb 库）实现持久化：
 * <ul>
 *   <li>会话列表（key='conversations'）+ 每会话消息（key=chatId）</li>
 *   <li>LRU 淘汰：最多 50 个会话，每会话保留最近 50 条</li>
 *   <li>防抖 flush：500ms 内多次写入合并为一次</li>
 *   <li>隐私模式降级：IndexedDB 不可用时降级到内存 Map</li>
 * </ul>
 *
 * <p>接口与 App 端 im-cache.js 保持一致（异步版），便于 store 层统一调用。
 */

const DB_NAME = 'nexus-im-cache';
const DB_VERSION = 1;
const STORE_KV = 'kv'; // 单一 KV store：conversations + 每个 chatId 一条
const KEY_CONVERSATIONS = '__conversations__';
const KEY_LRU = '__lru__'; // 会话 LRU 顺序（最近访问的 chatId 在末尾）
const MAX_CONVERSATIONS = 50;
const MAX_MESSAGES_PER_CONVERSATION = 50;
const FLUSH_DEBOUNCE_MS = 500;

let dbPromise: Promise<IDBDatabase | null> | null = null;

/** 内存降级缓存（IndexedDB 不可用时使用） */
const memoryFallback = new Map<string, any>();

/** 防抖写入队列：key -> 待写入的值 */
const pendingWrites = new Map<string, any>();
let flushTimer: ReturnType<typeof setTimeout> | null = null;

/**
 * 打开数据库（懒加载，失败时降级到内存缓存）。
 * 返回 null 表示 IndexedDB 不可用（隐私模式 / 旧浏览器），调用方需走内存降级。
 */
function openDB(): Promise<IDBDatabase | null> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise<IDBDatabase | null>((resolve) => {
    try {
      if (typeof indexedDB === 'undefined') {
        resolve(null);
        return;
      }
      const req = indexedDB.open(DB_NAME, DB_VERSION);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains(STORE_KV)) {
          db.createObjectStore(STORE_KV);
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
  return dbPromise;
}

/** 单条读取 */
async function rawGet<T>(key: string): Promise<T | null> {
  const mem = memoryFallback.get(key);
  if (mem !== undefined) return mem as T;
  const db = await openDB();
  if (!db) return null;
  return new Promise((resolve) => {
    try {
      const tx = db.transaction(STORE_KV, 'readonly');
      const store = tx.objectStore(STORE_KV);
      const req = store.get(key);
      req.onsuccess = () => resolve((req.result as T) ?? null);
      req.onerror = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
}

/** 调度防抖写入（500ms 内多次写入同一 key 合并） */
function scheduleWrite(key: string, value: any) {
  pendingWrites.set(key, value);
  if (flushTimer != null) return;
  flushTimer = setTimeout(() => {
    flushTimer = null;
    void flushPending();
  }, FLUSH_DEBOUNCE_MS);
}

/** 立即刷新所有挂起的写入 */
async function flushPending() {
  if (pendingWrites.size === 0) return;
  const entries = Array.from(pendingWrites.entries());
  pendingWrites.clear();
  const db = await openDB();
  if (!db) {
    // 降级：直接写内存
    for (const [k, v] of entries) memoryFallback.set(k, v);
    return;
  }
  await new Promise<void>((resolve) => {
    try {
      const tx = db.transaction(STORE_KV, 'readwrite');
      const store = tx.objectStore(STORE_KV);
      for (const [k, v] of entries) {
        store.put(v, k);
        memoryFallback.set(k, v); // 同步内存镜像，加速后续读取
      }
      tx.oncomplete = () => resolve();
      tx.onerror = () => resolve();
    } catch {
      resolve();
    }
  });
}

/** LRU 访问：将 chatId 移到末尾，淘汰超出容量的最旧会话 */
async function touchLru(chatId: string) {
  const list = ((await rawGet<string[]>(KEY_LRU)) || []).filter((x) => x !== chatId);
  list.push(chatId);
  while (list.length > MAX_CONVERSATIONS) {
    const evicted = list.shift();
    if (evicted && evicted !== chatId) {
      memoryFallback.delete(evicted);
      const db = await openDB();
      if (db) {
        await new Promise<void>((resolve) => {
          try {
            const tx = db.transaction(STORE_KV, 'readwrite');
            tx.objectStore(STORE_KV).delete(evicted);
            tx.oncomplete = () => resolve();
            tx.onerror = () => resolve();
          } catch {
            resolve();
          }
        });
      }
    }
  }
  scheduleWrite(KEY_LRU, list);
}

/** 强制刷新（页面隐藏/退出时调用） */
export async function flushOnExit(): Promise<void> {
  if (flushTimer != null) {
    clearTimeout(flushTimer);
    flushTimer = null;
  }
  await flushPending();
}

export const imCache = {
  /** 读取会话列表 */
  async getConversations<T = any>(): Promise<T | null> {
    return rawGet<T>(KEY_CONVERSATIONS);
  },

  /** 保存会话列表 */
  async saveConversations(list: any): Promise<void> {
    scheduleWrite(KEY_CONVERSATIONS, list);
  },

  /** 读取指定会话的消息 */
  async getMessage<T = any[]>(chatId: string): Promise<T | null> {
    const data = await rawGet<T>(chatId);
    if (data != null) await touchLru(chatId);
    return data;
  },

  /** 全量保存指定会话的消息（裁剪到最近 N 条） */
  async saveMessage(chatId: string, messages: any[]): Promise<void> {
    const trimmed = messages.length > MAX_MESSAGES_PER_CONVERSATION ? messages.slice(-MAX_MESSAGES_PER_CONVERSATION) : messages;
    scheduleWrite(chatId, trimmed);
    await touchLru(chatId);
  },

  /** 追加单条消息到指定会话末尾 */
  async appendMessage(chatId: string, message: any): Promise<void> {
    const existing = (await rawGet<any[]>(chatId)) || [];
    existing.push(message);
    await this.saveMessage(chatId, existing);
  },

  /** 清除指定会话消息缓存（不传则清空全部） */
  async clearMessageCache(chatId?: string): Promise<void> {
    if (chatId != null) {
      memoryFallback.delete(chatId);
      const db = await openDB();
      if (db) {
        await new Promise<void>((resolve) => {
          try {
            const tx = db.transaction(STORE_KV, 'readwrite');
            tx.objectStore(STORE_KV).delete(chatId);
            tx.oncomplete = () => resolve();
            tx.onerror = () => resolve();
          } catch {
            resolve();
          }
        });
      }
      // 从 LRU 中移除
      const list = ((await rawGet<string[]>(KEY_LRU)) || []).filter((x) => x !== chatId);
      scheduleWrite(KEY_LRU, list);
    } else {
      memoryFallback.clear();
      const db = await openDB();
      if (db) {
        await new Promise<void>((resolve) => {
          try {
            const tx = db.transaction(STORE_KV, 'readwrite');
            tx.objectStore(STORE_KV).clear();
            tx.oncomplete = () => resolve();
            tx.onerror = () => resolve();
          } catch {
            resolve();
          }
        });
      }
    }
  },

  /** 列出所有已缓存的会话 ID（用于 init 时预加载） */
  async listConversationIds(): Promise<string[]> {
    const list = await rawGet<string[]>(KEY_LRU);
    return list || [];
  },

  /** 清空全部缓存（登出时调用） */
  async clearAllCache(): Promise<void> {
    await this.clearMessageCache();
  },

  /** 清理过期缓存（本实现按 LRU 容量淘汰，无 TTL 概念，保留接口对齐 App 端） */
  async cleanExpiredCache(): Promise<void> {
    /* no-op：LRU 在 touchLru 中已处理 */
  }
};

export default imCache;
