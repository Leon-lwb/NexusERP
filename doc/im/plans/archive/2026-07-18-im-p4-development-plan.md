# P4 IM 模块性能优化与体验补齐开发任务

> **分支**: `feature/im-p0-stability-fix`（在 P3 commit `12b0a996` 基础上继续）
> **日期**: 2026-07-18
> **依据**: [2026-07-18-im-optimization-backlog.md](../../2026-07-18-im-optimization-backlog.md) + [2026-07-18-im-p3-development-plan.md](./2026-07-18-im-p3-development-plan.md) 第十章 P4 预告
> **范围**: 性能优化（群消息扇出/会话缓存/已读回执）+ 可靠性补齐（增量同步/队列持久化/断线 fallback）+ UX 补全（撤回提示/消息搜索）+ 可观测性增强
> **前置**: P0/P1/P2/P3 共 29 项改造已完成（nexus-erp: `12b0a996` / Nexus_App: `5c2d058`），单元测试覆盖率基线已建立

---

## 一、背景与目标

### 1.1 现状

| 维度 | 后端 nexus-erp | Web plus-ui | App Nexus_App |
|---|---|---|---|
| 性能 | 群消息扇出在事务内串行，1000 人群发送延迟 5s+ | 无虚拟滚动，千条消息卡顿 | 消息去重 O(n²)，批量消息卡顿 |
| 可靠性 | listConversations 无缓存，每次 8 条 SQL | 刷新丢消息，WS 断线无 fallback | pendingQueue 不持久化，重连全量拉 200 条 |
| 可观测性 | 仅 log.warn，无 metrics | 无性能采集 | 无性能采集 |
| 功能 | 撤回事件按成员循环推送 | 无消息搜索，撤回无 UI 提示 | 无消息搜索，撤回未处理 |

P3 已为 19 项关键改造建立了测试基线，但代码审查发现 28 项性能/可靠性/UX 优化点（详见 [optimization-backlog](../../2026-07-18-im-optimization-backlog.md)）。P4 聚焦其中**高价值与中价值共 20 项**，分三档实施。

### 1.2 目标

- **性能**: 1000 人群消息发送延迟 < 500ms；listConversations 首屏 < 50ms（缓存命中）；App 重连拉取量降低 90%
- **可靠性**: 离线消息不丢失（App 杀进程后恢复）；WS 断线 30s 内自动 fallback；Web 刷新不丢消息
- **可观测性**: 推送成功率/延迟/QPS 三大核心指标接入 Micrometer，暴露 Prometheus 端点
- **功能补全**: 消息撤回双端 UI 提示；消息搜索（关键词 + 时间范围）

### 1.3 范围与边界

**纳入 P4**:
- 后端群消息扇出异步化 + 批量通知 + Redis 缓存
- 后端已读回执水位线模式 + Micrometer 指标
- Web 消息本地缓存（IndexedDB）+ WS 断线 fallback + pageInfo LRU
- App syncToken 增量同步 + pendingQueue 持久化 + 消息去重 O(1)
- 双端撤回 UI 提示 + 消息搜索（含后端接口）

**不纳入 P4（留待 P5）**:
- 前端虚拟滚动与 Emoji 分页（影响面广，需独立评估）
- 性能压测基线（需独立压测环境）
- 前端组件测试与 E2E
- 多端状态同步协议重构

---

## 二、任务总览

P4 聚焦性能与可靠性，共 **12 项任务**，按风险/价值分三档实施：

| 档位 | 任务 | 风险 | 优化点来源 | 端 | 状态 |
|---|---|---|---|---|---|
| **A 高价值** | P4-1 群消息扇出异步化 + 批量通知 | 高（改事务边界） | B1+B2 | 后端 | 待实施 |
| **A 高价值** | P4-2 listConversations Redis 缓存 | 中（引入缓存一致性） | B4 | 后端 | 待实施 |
| **A 高价值** | P4-3 markGroupRead 水位线模式 | 中（改协议） | B3 | 后端+App | 待实施 |
| **A 高价值** | P4-4 App syncToken 增量同步 | 中（新增接口） | A2 | 后端+App | 待实施 |
| **A 高价值** | P4-5 App 消息去重 O(1) + 本地状态更新 | 低（纯前端） | A3+A4+A5 | App | 待实施 |
| **B 中价值** | P4-6 Web 消息本地缓存 + 断线 fallback | 中（引入 IndexedDB） | W1+W2+W3 | Web | 待实施 |
| **B 中价值** | P4-7 App pendingQueue 持久化 + 并行重发 | 中（改队列模型） | A1+A6+A8 | App | 待实施 |
| **B 中价值** | P4-8 消息撤回 UI 提示 + 消息搜索 | 中（含后端接口） | W7+W8+A11+A12 | 全端 | 待实施 |
| **B 中价值** | P4-9 后端可观测性 Micrometer 指标 | 低（纯新增） | B5 | 后端 | 待实施 |
| **C 基础设施** | P4-10 后端 SQL 优化与任务分桶 | 中（改 SQL） | B6+B7+B8 | 后端 | 待实施 |
| **C 基础设施** | P4-11 前端虚拟滚动与图片优化 | 高（影响渲染） | W4+W5+W6+A9+A10+A7 | 全端 | 待实施 |
| **C 基础设施** | P4-12 P4 回归测试与文档完善 | 低 | 全部 | 全端 | 待实施 |

本文档先实施 A 档五项（P4-1 ~ P4-5），B/C 档在 A 档验证稳定后单独推进。

---

## 三、A 档详细方案

### P4-1 群消息扇出异步化 + 批量通知

**问题**: [ImGroupChatService.sendGroup](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImGroupChatService.java#L67-L127) 在 `@Transactional` 内 `for (ImGroupMember m : members)` 循环：
- 每成员 1 次 `sysUserNotificationService.addAndPush`（INSERT + 推送注册）
- 1000 人群 = 1000 次 INSERT + 1000 次 afterCommit 钩子注册
- 事务持有时间 ∝ 群规模，长事务锁 `im_message` / `sys_user_notification`

**改造方案**:

#### 3.1.1 通知表批量 INSERT

`ImGroupChatService.sendGroup` 内移除循环 `addAndPush`，改为：

```java
// 1. 批量构造通知实体
List<SysUserNotification> notifications = members.stream()
    .filter(m -> !m.getUserId().equals(fromUserId))  // 排除发送者本人
    .map(m -> buildNotification(m.getUserId(), messageId, group))
    .toList();

// 2. 一次 SQL 批量插入（MyBatis-Plus saveBatch 默认 1000 条/批）
sysUserNotificationService.saveBatch(notifications);
```

#### 3.1.2 推送改为异步并行

新增 `ImPushExecutor` 推送线程池（独立于业务线程池）：

```java
@Component
public class ImPushExecutor {
    private final ExecutorService executor = Executors.newFixedThreadPool(8,
        new ThreadFactoryBuilder().setNameFormat("im-push-%d").build());

    public void publishToUsersAsync(List<Long> userIds, String json) {
        // 按 100 用户分片，并行推送
        Lists.partition(userIds, 100).forEach(batch ->
            executor.submit(() -> batch.forEach(uid ->
                imRealtimePush.publishToUser(uid, json))));
    }
}
```

#### 3.1.3 事务边界收窄

`sendGroup` 事务仅覆盖：
1. `imMessageMapper.insert(message)` — 消息持久化
2. `sysUserNotificationService.saveBatch(notifications)` — 通知批量插入

推送完全移出事务：
```java
@Transactional(rollbackFor = Exception.class)
public Long sendGroup(...) {
    imMessageMapper.insert(message);
    sysUserNotificationService.saveBatch(notifications);
    return message.getMessageId();
}
// 调用方在事务提交后触发
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override public void afterCommit() {
        imPushExecutor.publishToUsersAsync(targetUserIds, envelopeJson);
    }
});
```

#### 3.1.4 群撤回事件同样改造（B2）

[ImMessageLifecycleService.revokeMessage](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImMessageLifecycleService.java#L73-L95) 同构问题，改为：
- 撤回事件只构造一次 envelope，包含 `messageId` + `operatorId` + `revokedAt`
- 通过 `imPushExecutor.publishToUsersAsync(memberUserIds, revokeEnvelope)` 单次批量推送

**验证**:
- 单元测试：1000 人群 sendGroup 调用，`imMessageMapper.insert` 调用 1 次，`saveBatch` 调用 1 次，`publishToUsersAsync` 调用 1 次
- 性能基准：1000 人群发送延迟 < 500ms（本地 JMH 或手动计时）

**风险**:
- 推送异步化后，若 JVM 崩溃，已提交事务的推送可能丢失。**缓解**: 推送失败入 MQ 死信队列（P5 评估），或接受最终一致（用户重连时 syncToken 拉取补偿）

---

### P4-2 listConversations Redis 缓存

**问题**: [ImConversationService.listConversations](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImConversationService.java#L57-L68) 每次调用 8 条 SQL，前端登录 + 每次重连 + 每次 `markAsRead` 都会触发全量重算。

**改造方案**:

#### 3.2.1 缓存结构

```
Key:   im:conv:{tenantId}:{userId}
Value: JSON 序列化后的 List<ConversationVO>
TTL:   60s
```

#### 3.2.2 缓存读写策略

```java
public List<ConversationVO> listConversations() {
    String key = "im:conv:" + TenantHelper.getTenantId() + ":" + LoginHelper.getUserId();
    List<ConversationVO> cached = redisCache.getCacheObject(key);
    if (cached != null) {
        return cached;  // 缓存命中，直接返回
    }
    List<ConversationVO> result = doListConversationsFromDb();  // 原 8 步批量化逻辑
    redisCache.setCacheObject(key, result, 60, TimeUnit.SECONDS);
    return result;
}
```

#### 3.2.3 缓存失效策略

在以下场景主动 `redisCache.delete(key)`：
- `sendPrivate` / `sendGroup` 后（新消息进入会话）
- `markPrivateRead` / `markGroupRead` 后（未读数变化）
- `createGroup` / `addGroupMembers` / `leaveGroup` 后（群列表变化）

#### 3.2.4 Stale-While-Revalidate（可选）

高并发场景下，缓存过期瞬间会有多个请求同时回源。可加 `redisCache.setIfAbsent(lockKey, "1", 5s)` 分布式锁，仅一个请求回源，其他请求返回旧值（需双 key：`im:conv:xxx` + `im:conv:xxx:stale`，TTL 分别 60s/120s）。

**验证**:
- 单元测试：连续调用 2 次 listConversations，`imMessageMapper.selectMaps` 仅调用 1 次（第 2 次命中缓存）
- 集成测试：发送消息后缓存被失效，下次查询返回新数据

**风险**:
- 缓存与 DB 不一致：若失效失败（如 Redis 网络抖动），用户看到旧未读数。**缓解**: TTL 60s 兜底，最多 1 分钟不一致

---

### P4-3 markGroupRead 水位线模式

**问题**: [ImGroupChatService.markGroupRead](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImGroupChatService.java#L164-L181) 一次性 SELECT 全部未读消息 ID，推送给原发送方。活跃群可能有数千条未读，messageIds 数组体积过大。

**改造方案**:

#### 3.3.1 协议变更

旧协议（按 messageId 列表标记）：
```json
{"action": "read", "messageIds": [101, 102, 103, ...]}
```

新协议（按发送者水位线标记）：
```json
{"action": "read", "groupId": 100, "readerId": 200, "watermarks": [
  {"fromUserId": 301, "maxMessageId": 105},
  {"fromUserId": 302, "maxMessageId": 108}
]}
```

#### 3.3.2 后端实现

```java
public void markGroupRead(Long groupId) {
    Long me = LoginHelper.getUserId();
    // 1. 按 from_user_id 聚合取 MAX(message_id)
    List<Map<String, Object>> watermarks = imMessageMapper.selectMaps(
        new QueryWrapper<ImMessage>()
            .select("from_user_id", "MAX(message_id) AS max_message_id")
            .eq("group_id", groupId)
            .ne("from_user_id", me)
            .eq("is_read", "0")
            .groupBy("from_user_id"));

    // 2. 批量更新已读（一次 SQL）
    imMessageMapper.update(null, new LambdaUpdateWrapper<ImMessage>()
        .eq(ImMessage::getGroupId, groupId)
        .ne(ImMessage::getFromUserId, me)
        .eq(ImMessage::getIsRead, "0")
        .set(ImMessage::getIsRead, "1"));

    // 3. 推送水位线给所有群成员（用于更新对方"已读"标记）
    imPushExecutor.publishToUsersAsync(memberUserIds, buildReadWatermarkEnvelope(groupId, me, watermarks));
}
```

#### 3.3.3 前端适配

App `im.js` 与 Web `im.ts` 的 `handleNewMessage` 增加 `action=read_watermark` 分支：
- 收到水位线后，对本会话内 `fromUserId == watermark.fromUserId && messageId <= maxMessageId` 的消息标记"已读"

**验证**:
- 单元测试：1000 条未读消息，`selectMaps` 返回 ≤ 群成员数条水位线记录（而非 1000 条 messageId）
- 集成测试：发送方收到水位线推送后，对应消息显示"已读"标记

**风险**:
- 协议变更需前后端协同发布。**缓解**: 后端兼容旧协议（同时返回 messageIds 和 watermarks），前端灰度切换后再移除 messageIds

---

### P4-4 App syncToken 增量同步

**问题**: [Nexus_App im.js syncUnreadMessages](file:///d:/workspace/Nexus_App/store/modules/im.js#L571-L582) 每次重连全量拉 200 条未读，未读超过 200 条会漏，少于 200 条会重复处理。

**改造方案**:

#### 3.4.1 后端新增增量同步接口

`AppImController` 新增：
```java
@GetMapping("/messages/sync")
public R<SyncResult> syncMessages(@RequestParam(required = false) Long sinceMessageId) {
    Long me = LoginHelper.getUserId();
    // 1. 查询 sinceMessageId 之后的所有消息（私聊 + 群聊）
    List<ImMessage> newMessages = imMessageMapper.selectList(
        new LambdaQueryWrapper<ImMessage>()
            .and(w -> w.eq(ImMessage::getToUserId, me).or().eq(ImMessage::getGroupId, groupId))
            .gt(ImMessage::getMessageId, sinceMessageId)
            .orderByAsc(ImMessage::getMessageId)
            .last("limit 500"));

    // 2. 返回新消息 + 新的 syncToken（最后一条消息的 messageId）
    Long newSyncToken = newMessages.isEmpty() ? sinceMessageId
        : newMessages.get(newMessages.size() - 1).getMessageId();
    return R.ok(new SyncResult(newMessages, newSyncToken, newMessages.size() == 500));
}
```

#### 3.4.2 App 端 syncToken 持久化

```javascript
// im-cache.js 新增
const SYNC_TOKEN_KEY = 'im_sync_token'

export function getSyncToken() {
  return uni.getStorageSync(SYNC_TOKEN_KEY) || 0
}

export function setSyncToken(token) {
  uni.setStorageSync(SYNC_TOKEN_KEY, token)
}
```

#### 3.4.3 App 端 syncUnreadMessages 改造

```javascript
async function syncUnreadMessages() {
  const since = imCache.getSyncToken()
  const result = await imApi.syncMessages(since)
  // 处理增量消息
  result.messages.forEach(msg => handleNewMessage(msg))
  imCache.setSyncToken(result.newSyncToken)
  // 若有更多，继续拉取
  if (result.hasMore) {
    await syncUnreadMessages()
  }
}
```

**验证**:
- 单元测试：本地 syncToken = 100，后端返回 messageId 101-150 的 50 条，syncToken 更新为 150
- 集成测试：离线期间收到 300 条消息，重连后分 1 次拉取（500 条以内），syncToken 更新为最后一条

**风险**:
- 消息被撤回后，syncToken 已超过撤回的 messageId，撤回事件丢失。**缓解**: 撤回事件单独走 WS 推送（P4-8 实现），不依赖 syncToken

---

### P4-5 App 消息去重 O(1) + 本地状态更新

**问题**:
- [im.js handleNewMessage](file:///d:/workspace/Nexus_App/store/modules/im.js#L600-L615) 每条新消息 `existing.some(...)` 扫描，批量消息 O(n²)
- [im.js markAsRead](file:///d:/workspace/Nexus_App/store/modules/im.js#L562-L569) 强制刷新整个会话列表，浪费 API
- [im.js updateConversation](file:///d:/workspace/Nexus_App/store/modules/im.js#L786-L788) 找不到会话时调 `loadConversations()`，并发新会话消息触发 N 次

**改造方案**:

#### 3.5.1 消息去重 Set 索引

```javascript
// store/modules/im.js 新增
const messageIdSet = new Set()

function ensureMessageIdIndex(chatId) {
  // 初始化或重建索引（首次访问会话时）
  if (!messageIdSet.has(chatId)) {
    const messages = messagesMap.value[chatId] || []
    messages.forEach(m => messageIdSet.add(`${chatId}:${m.messageId}`))
  }
}

function isMessageExist(chatId, messageId) {
  return messageIdSet.has(`${chatId}:${messageId}`)
}

function addMessageId(chatId, messageId) {
  messageIdSet.add(`${chatId}:${messageId}`)
}
```

`handleNewMessage` 改为：
```javascript
function handleNewMessage(message) {
  const chatId = message.groupId ? `g_${message.groupId}` : `p_${message.fromUserId}`
  ensureMessageIdIndex(chatId)
  if (isMessageExist(chatId, message.messageId)) return  // O(1) 去重
  // ... 原有逻辑
  addMessageId(chatId, message.messageId)
}
```

#### 3.5.2 markAsRead 本地更新

```javascript
async function markAsRead(chatType, targetId) {
  await imApi.markRead(chatType, targetId)
  // 不再调 loadConversations()，直接本地更新未读数
  const convKey = chatType === 'group' ? `g_${targetId}` : `p_${targetId}`
  const conv = conversations.value.find(c => c.key === convKey)
  if (conv) {
    conv.unreadCount = 0
    conv.lastReadTime = Date.now()
  }
  // 总未读数同步更新
  totalUnread.value = conversations.value.reduce((sum, c) => sum + (c.unreadCount || 0), 0)
}
```

#### 3.5.3 新会话本地 push + 防抖

```javascript
const loadConversationsDebounced = debounce(loadConversations, 500)

function updateConversation(message) {
  const convKey = message.groupId ? `g_${message.groupId}` : `p_${message.fromUserId}`
  let conv = conversations.value.find(c => c.key === convKey)
  if (!conv) {
    // 本地直接 push 新会话对象（基于消息内容构造）
    conv = buildConversationFromMessage(message)
    conversations.value.unshift(conv)
    // 防抖触发一次完整重拉（补全头像、群名等字段）
    loadConversationsDebounced()
  }
  // 更新最后一条消息预览 + 未读数
  conv.lastMessage = message
  if (!isCurrentChat(convKey)) conv.unreadCount = (conv.unreadCount || 0) + 1
}
```

**验证**:
- 单元测试：连续 100 条重复消息，`handleNewMessage` 仅处理 1 次（Set 命中 99 次）
- 单元测试：`markAsRead` 后 `loadConversations` 不被调用（mock 验证）
- 单元测试：5 条不同新会话消息并发到达，`loadConversations` 仅调用 1 次（防抖合并）

---

## 四、B 档详细方案

### P4-6 Web 消息本地缓存 + 断线 fallback

**问题**:
- [im.ts messagesMap](file:///d:/workspace/nexus-erp/plus-ui/src/store/modules/im.ts#L115-L117) 纯内存 ref，刷新即丢
- [im.ts init](file:///d:/workspace/nexus-erp/plus-ui/src/store/modules/im.ts#L182-L189) 仅页面加载调用一次，WS 断线无 fallback
- [im.ts pageInfoMap/draftMap](file:///d:/workspace/nexus-erp/plus-ui/src/store/modules/im.ts#L117-L118) 不清理，内存泄漏

**改造方案**:

#### 4.6.1 IndexedDB 消息缓存

新建 `plus-ui/src/utils/im/im-cache.ts`，复用 App 端 `im-cache.js` 接口设计：

```typescript
import { openDB } from 'idb'

const DB_NAME = 'nexus-im-cache'
const STORE_MESSAGES = 'messages'
const MAX_CONVERSATIONS = 50
const MAX_MESSAGES_PER_CONVERSATION = 50

// 接口与 App 端 im-cache.js 保持一致
export const imCache = {
  async getMessage(chatId: string): Promise<ImMessage[] | null> { /* ... */ },
  async appendMessage(chatId: string, message: ImMessage): Promise<void> { /* ... */ },
  async saveMessage(chatId: string, messages: ImMessage[]): Promise<void> { /* ... */ },
  async clearMessageCache(chatId?: string): Promise<void> { /* ... */ },
  // LRU + 防抖 flush（与 App 端一致）
}
```

#### 4.6.2 store 初始化加载本地缓存

```typescript
async function init() {
  if (initialized.value) return
  initialized.value = true

  // 1. 先从 IndexedDB 加载本地缓存（毫秒级）
  const cachedConvIds = await imCache.listConversationIds()
  for (const chatId of cachedConvIds) {
    messagesMap.value[chatId] = await imCache.getMessage(chatId)
  }

  // 2. 后台拉取服务器最新数据（不阻塞 UI）
  await loadConversations()

  // 3. 监听 WS 重连事件
  useEventBus('ws-reconnect').on(() => {
    loadConversations()
    syncUnreadMessages()
  })

  // 4. WS 不可达时 30s 兜底轮询
  setInterval(() => {
    if (!isWsConnected()) {
      loadConversations()
    }
  }, 30_000)
}
```

#### 4.6.3 pageInfo/draft LRU

```typescript
const pageInfoLRU = new LRUMap<string, PageInfo>(20)
const draftLRU = new LRUMap<string, string>(20)
```

LRUMap 实现：Map 的插入顺序即 LRU 顺序，访问时 `delete` + `set` 移到末尾，超容量时删除 `keys().next().value`。

**验证**:
- 手动测试：发 10 条消息，F5 刷新页面，消息仍在
- 手动测试：WS 断线 30s 后，会话列表未读数仍会更新

---

### P4-7 App pendingQueue 持久化 + 并行重发

**问题**:
- [im.js pendingQueue](file:///d:/workspace/Nexus_App/store/modules/im.js#L46) 纯内存，App 杀进程后丢失
- [im.js flushPendingQueue](file:///d:/workspace/Nexus_App/store/modules/im.js#L520-L558) 串行重发，10 条消息需 10×RTT
- [websocket.js send](file:///d:/workspace/Nexus_App/utils/websocket.js#L146-L156) 不连接时静默失败

**改造方案**:

#### 4.7.1 pendingQueue 持久化

```javascript
const PENDING_QUEUE_KEY = 'im_pending_queue'

function persistPendingQueue() {
  uni.setStorageSync(PENDING_QUEUE_KEY, pendingQueue.value)
}

// 防抖持久化（500ms 内多次入队只写一次盘）
const persistDebounced = debounce(persistPendingQueue, 500)

function enqueuePending(item) {
  pendingQueue.value.push(item)
  persistDebounced()
}

function dequeuePending() {
  const item = pendingQueue.value.shift()
  persistDebounced()
  return item
}

// App 启动时恢复
function restorePendingQueue() {
  const saved = uni.getStorageSync(PENDING_QUEUE_KEY)
  if (Array.isArray(saved)) {
    pendingQueue.value = saved
  }
}
```

#### 4.7.2 并行重发（按 targetId 分组）

```javascript
async function flushPendingQueue() {
  if (pendingQueue.value.length === 0) return
  const queue = [...pendingQueue.value]
  pendingQueue.value = []
  persistDebounced()

  // 按 targetId 分组
  const groups = groupBy(queue, item => item.targetId)

  // 不同 target 并行，同 target 串行（保序）
  await Promise.all(
    Object.values(groups).map(async (groupItems) => {
      for (const item of groupItems) {
        try {
          await sendMessage(item)
        } catch (e) {
          // 失败重新入队
          enqueuePending(item)
        }
      }
    })
  )
}
```

#### 4.7.3 WebSocket send 失败反馈

```javascript
// websocket.js
send(data) {
  if (!this.socketTask || !this.isConnected) {
    return false  // 返回 false 让调用方决定入队
  }
  try {
    this.socketTask.send({ data: JSON.stringify(data) })
    return true
  } catch (e) {
    console.error('[WS] send failed', e)
    return false
  }
}
```

调用方：
```javascript
function sendViaWs(message) {
  const ok = ws.send(message)
  if (!ok) {
    enqueuePending({ targetId, message })
  }
}
```

**验证**:
- 手动测试：离线发 5 条消息，杀进程重启 App，消息仍在队列中，登录后自动重发
- 手动测试：3 个不同 target 各 5 条消息，重发时间从 15×RTT 降至 5×RTT

---

### P4-8 消息撤回 UI 提示 + 消息搜索

**问题**:
- Web [im.ts handleRealtime](file:///d:/workspace/nexus-erp/plus-ui/src/store/modules/im.ts#L523-L527) 撤回事件静默替换，无 UI 提示
- App [im.js handleNewMessage](file:///d:/workspace/Nexus_App/store/modules/im.js#L586-L625) 未处理 `action=revoke`
- 双端均无消息搜索功能

**改造方案**:

#### 4.8.1 后端新增消息搜索接口

`AppImController` 新增：
```java
@GetMapping("/messages/search")
public R<SearchResult> searchMessages(
    @RequestParam String keyword,
    @RequestParam(required = false) String chatType,
    @RequestParam(required = false) Long targetId,
    @RequestParam(defaultValue = "1") Integer pageNum,
    @RequestParam(defaultValue = "20") Integer pageSize) {

    Long me = LoginHelper.getUserId();
    // LIKE 查询，限制 7 天内
    List<ImMessage> messages = imMessageMapper.selectList(
        new LambdaQueryWrapper<ImMessage>()
            .and(w -> w.eq(ImMessage::getToUserId, me).or().eq(ImMessage::getFromUserId, me)
                .or().inSql(ImMessage::getGroupId, "SELECT group_id FROM im_group_member WHERE user_id = " + me))
            .like(ImMessage::getContent, keyword)
            .ge(ImMessage::getCreateTime, Date.from(Instant.now().minus(7, ChronoUnit.DAYS)))
            .orderByDesc(ImMessage::getCreateTime)
            .last("limit " + ((pageNum - 1) * pageSize) + "," + pageSize));
    return R.ok(new SearchResult(messages, pageNum, pageSize));
}
```

#### 4.8.2 Web 撤回 UI 提示

```typescript
// im.ts handleRealtime 增加 revoke 分支
function handleRealtime(envelope: ImEnvelope) {
  if (envelope.action === 'revoke') {
    const chatId = envelope.groupId ? `g_${envelope.groupId}` : `p_${envelope.fromUserId}`
    const messages = messagesMap.value[chatId] || []
    const idx = messages.findIndex(m => m.messageId === envelope.messageId)
    if (idx >= 0) {
      // 替换为系统提示消息
      messages[idx] = {
        ...messages[idx],
        msgType: 'system',
        content: `${envelope.operatorName} 撤回了一条消息`,
        revoked: '1'
      }
    }
    return
  }
  // ... 原有逻辑
}
```

#### 4.8.3 App 撤回 UI 提示

`im.js handleNewMessage` 开头增加：
```javascript
if (message.action === 'revoke') {
  handleRevokeEvent(message)
  return
}

function handleRevokeEvent(message) {
  const chatId = message.groupId ? `g_${message.groupId}` : `p_${message.fromUserId}`
  const messages = messagesMap.value[chatId] || []
  const idx = messages.findIndex(m => m.messageId === message.messageId)
  if (idx >= 0) {
    messages[idx] = {
      ...messages[idx],
      msgType: 'system',
      content: `${message.operatorName || '对方'} 撤回了一条消息`,
      revoked: '1'
    }
    messagesMap.value[chatId] = [...messages]
  }
}
```

#### 4.8.4 双端搜索 UI

Web：会话列表顶部加搜索框，回车触发 `searchMessages(keyword)`，结果在新面板展示，点击跳转到对应消息位置（带高亮）。

App：群信息页加"搜索聊天记录"入口，复用相同接口。

**验证**:
- 单元测试：撤回事件处理后，消息变为 system 类型，内容包含"撤回"
- 集成测试：搜索关键词返回 7 天内匹配消息，分页正确

---

### P4-9 后端可观测性 Micrometer 指标

**问题**: [ImRealtimePush](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/push/ImRealtimePush.java) 仅 `log.warn`，无 metrics。

**改造方案**:

#### 4.9.1 引入 Micrometer

`ruoyi-im/pom.xml` 已传递依赖 `spring-boot-starter-actuator`（通过 `ruoyi-common-web`），无需新增依赖。

#### 4.9.2 指标埋点

```java
@Component
public class ImMetrics {
    private final MeterRegistry registry;

    public ImMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordPushSuccess(String chatType) {
        registry.counter("im.push.success", "type", chatType).increment();
    }

    public void recordPushFailure(String chatType, String reason) {
        registry.counter("im.push.failure", "type", chatType, "reason", reason).increment();
    }

    public Timer.Sample startPushTimer() {
        return Timer.start(registry);
    }

    public void stopPushTimer(Timer.Sample sample, String chatType) {
        sample.stop(registry.timer("im.push.latency", "type", chatType));
    }

    public void recordMessageSend(String chatType) {
        registry.counter("im.message.send", "type", chatType).increment();
    }
}
```

#### 4.9.3 埋点位置

- `ImRealtimePush.publishToUser` 成功后 `recordPushSuccess` / 失败 `recordPushFailure`
- `ImRealtimePush.publishToUser` 前后 `startPushTimer` / `stopPushTimer`
- `ImPrivateChatService.sendPrivate` / `ImGroupChatService.sendGroup` 末尾 `recordMessageSend`

#### 4.9.4 暴露 Prometheus 端点

`application.yml` 已有 `management.endpoints.web.exposure.include: *`，无需额外配置。访问 `/actuator/prometheus` 即可获取指标。

**验证**:
- 手动测试：发送 10 条消息后，`/actuator/prometheus` 包含 `im_message_send_total{type="private"}` 10
- 手动测试：推送失败后，`im_push_failure_total` 计数增加

---

## 五、C 档详细方案

### P4-10 后端 SQL 优化与任务分桶

**优化点**: B6（私聊会话去重 SQL 改写）+ B7（清理任务分桶）+ B8（WS 推送背压）

#### 5.10.1 B6 私聊会话去重 SQL 改写

[ImConversationService.listPrivateConversations](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImConversationService.java#L230-L256) 改为：
```sql
SELECT m.* FROM im_message m
INNER JOIN (
    SELECT MAX(message_id) AS max_id FROM im_message
    WHERE (from_user_id = #{me} OR to_user_id = #{me})
    GROUP BY LEAST(from_user_id, to_user_id), GREATEST(from_user_id, to_user_id)
) t ON m.message_id = t.max_id
ORDER BY m.create_time DESC
LIMIT 200
```

#### 5.10.2 B7 清理任务分桶

[ImMessageClearTask](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/task/ImMessageClearTask.java#L57) 改为每小时执行，每次只清理 1 小时前的过期数据：
```java
@Scheduled(cron = "0 0 * * * ?")  // 每小时
public void cleanupExpiredMessages() {
    Date threshold = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
    // 分批删除，每批 1000 条
    while (true) {
        List<Long> ids = imMessageMapper.selectList(
            new LambdaQueryWrapper<ImMessage>()
                .lt(ImMessage::getCreateTime, threshold)
                .select(ImMessage::getMessageId)
                .last("limit 1000"));
        if (ids.isEmpty()) break;
        imMessageMapper.deleteBatchIds(ids.stream().map(ImMessage::getMessageId).toList());
    }
}
```

#### 5.10.3 B8 WS 推送背压

每用户维护有界队列：
```java
@Component
public class UserPushQueue {
    private final Map<Long, ArrayBlockingQueue<String>> queues = new ConcurrentHashMap<>();
    private static final int MAX_QUEUE_SIZE = 1000;

    public boolean offer(Long userId, String json) {
        ArrayBlockingQueue<String> queue = queues.computeIfAbsent(userId,
            k -> new ArrayBlockingQueue<>(MAX_QUEUE_SIZE));
        return queue.offer(json);  // 满了返回 false
    }

    public void drainTo(Long userId, Consumer<String> consumer) {
        ArrayBlockingQueue<String> queue = queues.get(userId);
        if (queue != null) {
            queue.drainTo(new ArrayList<>(queue.size()));
            queue.forEach(consumer);
        }
    }
}
```

---

### P4-11 前端虚拟滚动与图片优化

**优化点**: W4（虚拟滚动）+ W5（标题闪烁）+ W6（免打扰）+ A7（imCache 双 touch）+ A9（Emoji 分页）+ A10（图片预加载）

此档影响面广，需独立评估与设计，本档仅列出方向：

- W4：Web 引入 `vue-virtual-scroller`，App 用 `scroll-view` 的 `@scroll` 实现简易虚拟渲染
- W5：标题闪烁改 `requestAnimationFrame` + 2s 间隔
- W6：全局勿扰配置 + 按会话 mute
- A7：`appendMessage` 内联 `messageCache.get`，避免重复 touch
- A9：Emoji 分类 tab + 分页（每页 50 条）
- A10：图片消息加 `thumbnailUrl`（后端 OSS `?imageView2/2/w/200`），点击查看原图

---

### P4-12 P4 回归测试与文档完善

**任务**:
- 为 P4-1 ~ P4-9 新增单元测试（延续 P3 测试规范）
- 更新 [optimization-backlog](../../2026-07-18-im-optimization-backlog.md) 中已完成项的状态与 commit hash
- 更新 [P0 稳定性分析](./2026-07-17-im-p0-stability-fix-plan.md) 中的"后续阶段"章节，标记 P4 已完成项
- 性能对比：P4 前后 1000 人群发送延迟、listConversations 首屏延迟、App 重连拉取量三项指标

---

## 六、验证清单

| 任务 | 验证方式 | 通过标准 |
|---|---|---|
| P4-1 | 单元测试 + 手动计时 | 1000 人群 sendGroup < 500ms，`insert` 1 次，`saveBatch` 1 次 |
| P4-2 | 单元测试 + 集成测试 | 连续 2 次 listConversations，`selectMaps` 仅调用 1 次 |
| P4-3 | 单元测试 + 协议测试 | 1000 条未读，watermarks 记录数 ≤ 群成员数 |
| P4-4 | 单元测试 + 集成测试 | 离线 300 条，重连后 1 次拉取，syncToken 正确更新 |
| P4-5 | 单元测试 | 100 条重复消息仅处理 1 次；markAsRead 不调 loadConversations |
| P4-6 | 手动测试 | F5 刷新后消息仍在；WS 断线 30s 内未读数更新 |
| P4-7 | 手动测试 | 离线 5 条杀进程后恢复，登录后自动重发 |
| P4-8 | 单元测试 + 手动测试 | 撤回事件显示"XX 撤回了一条消息"；搜索返回匹配结果 |
| P4-9 | 手动测试 | `/actuator/prometheus` 包含 `im_message_send_total` 等指标 |
| P4-10 | 单元测试 + 手动测试 | 私聊会话不漏 peer；清理任务每小时执行；慢消费者不阻塞 |
| P4-11 | 手动测试 | 千条消息流畅滚动；Emoji 面板秒开；图片预加载有效 |
| P4-12 | 全量回归 | P4 所有任务通过 + 文档更新完成 |

---

## 七、提交规范

- **A 档**：5 项任务完成后合并为 1 个 commit，commit message 前缀 `perf(im): P4 A 档...`
- **B 档**：4 项任务完成后合并为 1 个 commit，commit message 前缀 `feat(im): P4 B 档...`
- **C 档**：3 项任务完成后合并为 1 个 commit，commit message 前缀 `perf(im): P4 C 档...`
- **分支**：继续在 `feature/im-p0-stability-fix` 分支开发，或视情况切出 `feature/im-p4-perf` 子分支

---

## 八、风险评估与回滚

### 风险点

1. **P4-1 事务边界收窄导致推送丢失**: 推送异步化后，JVM 崩溃可能丢失已提交事务的推送。**缓解**: 接受最终一致（syncToken 补偿），P5 评估引入 MQ 死信队列
2. **P4-2 缓存一致性**: 失效失败导致用户看到旧未读数。**缓解**: TTL 60s 兜底，最多 1 分钟不一致
3. **P4-3 协议变更兼容性**: 旧客户端无法解析 watermarks 字段。**缓解**: 后端兼容期同时返回 messageIds 和 watermarks，前端灰度切换后再移除
4. **P4-4 syncToken 持久化失败**: 本地存储损坏导致 syncToken 丢失，重连拉取全量。**缓解**: syncToken 丢失时回退到全量拉取模式（与 P3 行为一致）
5. **P4-6 IndexedDB 兼容性**: 隐私模式下 IndexedDB 不可用。**缓解**: try-catch 降级到内存缓存
6. **P4-8 搜索 LIKE 查询性能**: 大表 LIKE 全表扫描。**缓解**: 限制 7 天内 + 分页，P5 评估引入 ES 索引

### 回滚策略

- 每项任务独立提交，可单独 revert
- P4-2 缓存可配置开关（`im.conversation.cache.enabled`），故障时关闭即回退到直查 DB
- P4-3 协议兼容期保留旧字段，回滚只需前端切换解析逻辑
- P4-4 syncToken 失败自动回退到全量拉取，无需手动回滚

---

## 九、与优化点记录文档的对应关系

| P4 任务 | 对应优化点 | 完成后 backlog 状态 |
|---|---|---|
| P4-1 | B1 + B2 | ✅ B1 + B2 |
| P4-2 | B4 | ✅ B4 |
| P4-3 | B3 | ✅ B3 |
| P4-4 | A2 | ✅ A2 |
| P4-5 | A3 + A4 + A5 | ✅ A3 + A4 + A5 |
| P4-6 | W1 + W2 + W3 | ✅ W1 + W2 + W3 |
| P4-7 | A1 + A6 + A8 | ✅ A1 + A6 + A8 |
| P4-8 | W7 + W8 + A11 + A12 | ✅ W7 + W8 + A11 + A12 |
| P4-9 | B5 | ✅ B5 |
| P4-10 | B6 + B7 + B8 | ✅ B6 + B7 + B8 |
| P4-11 | W4 + W5 + W6 + A7 + A9 + A10 | ✅ 全部 |
| P4-12 | — | backlog 全量复审更新 |

**未纳入 P4 的优化点**: 无（backlog 中 28 项全部在 P4 范围内）

---

## 十、后续阶段预告（P5）

P4 完成后，P5 可考虑以下方向（待 P4 验证后选择）：

1. **MQ 死信队列**: P4-1 推送异步化的可靠性兜底，引入 RabbitMQ/RocketMQ 处理推送失败重试
2. **ES 消息搜索**: P4-8 LIKE 查询的升级，支持全文检索 + 历史消息搜索（不限 7 天）
3. **多端状态同步协议**: 统一设备标识 + 端到端加密 + 消息漫游
4. **性能压测基线**: JMeter/wrk 压测高频接口，建立 P95/P99 延迟基线与回归告警
5. **前端 E2E 测试**: Playwright/Cypress 覆盖核心聊天流程（发消息/撤回/已读/搜索）
6. **WebSocket 集群**: 多实例部署下的 session 一致性（Redis pub/sub 广播）
7. **消息加密存储**: 敏感消息内容加密落库，前端按需解密
