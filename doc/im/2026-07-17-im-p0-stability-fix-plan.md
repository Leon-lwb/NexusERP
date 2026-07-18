# P0 稳定性修复开发任务

> **分支**: `feature/im-p0-stability-fix`（从 `plus` 切出）
> **日期**: 2026-07-17
> **目标**: 修复 nexus-erp `ruoyi-im` 模块的严重安全漏洞与稳定性问题
> **约束**: 不影响主分支，不动既有 API 契约（仅补归属校验、不改路径/参数）
> **依据**: [2026-07-17-vim-vs-nexus-im-analysis.md](./2026-07-17-vim-vs-nexus-im-analysis.md)

---

## 任务清单

| ID | 任务 | 涉及文件 | 风险 | 状态 |
|---|---|---|---|---|
| P0-1 | 修复 3 处越权 + 跨租户好友申请 | AppImController.java | 低 | ⏳ |
| P0-2 | 推送移到事务提交后（消除幽灵消息） | ImRealtimePush.java、ImChatServiceImpl.java、SysUserNotificationServiceImpl.java | 中 | ⏳ |
| P0-3 | 修复雪花 ID 推送精度丢失 | ImEnvelopeHelper.java、AppImController.java | 低 | ⏳ |
| P0-4 | 补 im_message is_read 复合索引 | doc/im/sql/2026-07-17-im-p0-read-index.sql | 极低 | ⏳ |
| P0-5 | 移动端响应式 + 群聊去重 + 重连退避 | Nexus_App: chat/index.vue、im.js、websocket.js | 低 | ⏳ |

状态图例: ⏳ 待办 / 🚧 进行中 / ✅ 完成 / ❌ 阻塞

---

## P0-1 修复 3 处越权 + 跨租户好友申请

### 漏洞描述

| 接口 | 漏洞 | 行号 |
|---|---|---|
| `DELETE /im/app/message/{messageId}` (deleteMessage) | 任意用户删任意消息，无归属校验 | [AppImController.java#L580-L584](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/controller/AppImController.java#L580-L584) |
| `POST /im/app/message/forward` (forwardMessage) | 不校验调用方是否有权读原消息 | [AppImController.java#L589-L618](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/controller/AppImController.java#L589-L618) |
| `POST /im/app/friend/request/revoke/{messageId}` (revokeRequest) | ID 域错配 + 无归属校验 | [AppImController.java#L432-L436](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/controller/AppImController.java#L432-L436) |
| `POST /im/app/friend/request/send` (sendFriendRequest) | 缺 `requireSameTenantUser(toUserId)` | [AppImController.java#L168-L224](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/controller/AppImController.java#L168-L224) |

### 改造方案

```java
// deleteMessage：增加归属校验
ImMessage msg = messageMapper.selectById(messageId);
if (msg == null) return R.fail("消息不存在");
Long me = LoginHelper.getUserId();
boolean canDelete = me.equals(msg.getFromUserId())
    || (msg.getToUserId() != null && me.equals(msg.getToUserId()))
    || (msg.getGroupId() != null && isGroupMember(msg.getGroupId(), me));
if (!canDelete) return R.fail("无权删除该消息");

// forwardMessage：校验原消息读取权
boolean canReadOrigin = me.equals(msg.getFromUserId())
    || (msg.getToUserId() != null && me.equals(msg.getToUserId()))
    || (msg.getGroupId() != null && isGroupMember(msg.getGroupId(), me));
if (!canReadOrigin) return R.fail("无权转发该消息");

// revokeRequest：按 notificationId 删除且校验归属
SysUserNotification n = notificationMapper.selectById(messageId);
if (n == null || !me.equals(n.getUserId())) return R.fail("无权操作");

// sendFriendRequest：补租户校验
tenantEntityService.requireSameTenantUser(toUserId);
```

### 验证

- 单测：模拟非归属用户调用 4 个接口，断言返回 `R.fail`
- 集成：A 租户用户尝试向 B 租户用户发好友申请，断言拒绝

---

## P0-2 推送移到事务提交后（消除幽灵消息）

### 问题描述

`@Transactional` 内先 `imRealtimePush.publishToUser` 后提交。若事务回滚，已推送的 messageId 在 DB 不存在，客户端收"幽灵消息"。

证据:
- [ImChatServiceImpl.java#L102-L103](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/ImChatServiceImpl.java#L102-L103)（sendPrivate）
- [ImChatServiceImpl.java#L148-L150](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/ImChatServiceImpl.java#L148-L150)（sendGroup）
- [ImChatServiceImpl.java#L433](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/ImChatServiceImpl.java#L433)（revokeMessage）
- [SysUserNotificationServiceImpl.java#L46-L47](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/SysUserNotificationServiceImpl.java#L46-L47)

### 改造方案

在 `ImRealtimePush` 增加 `publishToUserAfterCommit` 方法，使用 `TransactionSynchronizationManager.registerSynchronization` 在 `afterCommit` 阶段统一发送。原 `publishToUser` 保留向后兼容，标记 `@Deprecated`。

```java
public class ImRealtimePush {
    private static final ThreadLocal<List<Runnable>> PENDING = ThreadLocal.withInitial(ArrayList::new);

    public void publishToUserAfterCommit(Long userId, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            PENDING.get().add(() -> doPublish(userId, payload));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    PENDING.get().forEach(Runnable::run);
                    PENDING.remove();
                }
            });
        } else {
            doPublish(userId, payload);
        }
    }
}
```

`ImChatServiceImpl` 内所有 `imRealtimePush.publishToUser(...)` 调用替换为 `publishToUserAfterCommit(...)`。

### 验证

- 集成测试：模拟群发中第 N 个成员写入失败触发回滚，断言客户端未收到任何推送
- 日志：检查推送日志全部在事务日志之后

---

## P0-3 修复雪花 ID 推送精度丢失

### 问题描述

`ImEnvelopeHelper` 用 fastjson2 序列化 payload，绕过全局 `BigNumberSerializer`，导致 `messageId/fromUserId/toUserId/groupId` 等 Long 值在 JS 端精度丢失（末 3 位被截断）。

证据: [ImEnvelopeHelper.java#L31-L38](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/push/ImEnvelopeHelper.java#L31-L38)、[AppImController.java#L185](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/controller/AppImController.java#L185) 手拼 JSON

### 改造方案

`ImEnvelopeHelper` 注入 Spring 容器的 `ObjectMapper`（已注册 `BigNumberSerializer`），替换 fastjson2:

```java
@Component
@RequiredArgsConstructor
public class ImEnvelopeHelper {
    private final ObjectMapper objectMapper;  // Spring 注入，已配置 BigNumberSerializer

    public String envelope(String type, Object payload) {
        try {
            Map<String, Object> env = new HashMap<>();
            env.put("v", 1);
            env.put("type", type);
            env.put("payload", payload);
            return objectMapper.writeValueAsString(env);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("信封序列化失败", e);
        }
    }
}
```

`AppImController#L185` 手拼 JSON 改为 `Map` + `ImEnvelopeHelper.envelope(...)`。

### 验证

- 单测：构造 messageId = `1912345678901234567L`，序列化后断言字符串包含 `"1912345678901234567"` 而非 `1.912345678901234e18`
- 前端：撤回/转发后 console.log 收到的 messageId 与服务端一致

---

## P0-4 补 im_message is_read 复合索引

### 问题描述

`im_message` 现有 3 个索引（[ry-vue.sql#L4113-L4116](file:///d:/workspace/nexus-erp/script/sql/ry-vue.sql#L4113-L4116)）均不含 `is_read`，高频已读/未读查询全表扫。

### 改造方案

新增 `doc/im/sql/2026-07-17-im-p0-read-index.sql`:

```sql
-- P0-4: IM 消息已读/未读查询性能优化
-- 依据: doc/im/2026-07-17-vim-vs-nexus-im-analysis.md M2
-- 影响: markPrivateRead/markGroupRead/countUnreadPrivate/countUnreadGroup/unreadMessages/unreadCount 接口

ALTER TABLE im_message ADD INDEX idx_private_unread (tenant_id, to_user_id, from_user_id, is_read, create_time);
ALTER TABLE im_message ADD INDEX idx_group_unread (tenant_id, group_id, is_read);
```

### 验证

- `EXPLAIN SELECT * FROM im_message WHERE tenant_id=? AND to_user_id=? AND is_read='0'` 命中 `idx_private_unread`
- `EXPLAIN SELECT * FROM im_message WHERE tenant_id=? AND group_id=? AND is_read='0'` 命中 `idx_group_unread`

---

## P0-5 移动端响应式 + 群聊去重 + 重连退避

### 问题描述

| 子项 | 问题 | 证据 |
|---|---|---|
| 5.1 Map 响应式失效 | `messageList` computed 未读 `messagesVersion` 哨兵 | [chat/index.vue#L168-L171](file:///d:/workspace/Nexus_App/pages/im/chat/index.vue#L168-L171) |
| 5.2 群聊去重弱 | 仅 messageId 一层，自发群消息回显与乐观插入并存 | [im.js#L619-L629](file:///d:/workspace/Nexus_App/store/modules/im.js#L619-L629) |
| 5.3 WebSocket 重连无退避 | 固定 3s × 5 次后放弃，无抖动 | [websocket.js#L8](file:///d:/workspace/Nexus_App/utils/websocket.js#L8)、[L107-L116](file:///d:/workspace/Nexus_App/utils/websocket.js#L107-L116) |

### 改造方案

#### 5.1 chat/index.vue messageList computed

```js
const messageList = computed(() => {
  void imStore.messagesVersion  // 触发响应式依赖
  if (!conversationId.value) return []
  return imStore.messages.get(conversationId.value) || []
})
```

#### 5.2 im.js handleNewGroupMessage

参考 [handleNewMessage L517-L532](file:///d:/workspace/Nexus_App/store/modules/im.js#L517-L532) 私聊回显匹配逻辑，给群聊加 `_tempId + content` 兜底:

```js
function handleNewGroupMessage(message) {
  if (!message.groupId) return
  const targetId = String(message.groupId)
  const myId = String(getMyUserId() || '')
  const fromId = String(message.fromUserId || '')
  const isSelfSent = myId !== '' && fromId === myId
  const normalizedMsg = normalizeMessage(message)
  const existing = messages.value.get(targetId) || []

  const isDuplicate = existing.some(m => {
    if (m.messageId && normalizedMsg.messageId
        && String(m.messageId) === String(normalizedMsg.messageId)) return true
    // 自发群消息回显匹配乐观插入的临时消息
    if (isSelfSent && !m.messageId && m._tempId && m.content === normalizedMsg.content) {
      m.messageId = normalizedMsg.messageId
      m._status = 'sent'
      messagesVersion.value++
      return true
    }
    return false
  })

  if (!isDuplicate) {
    messages.value.set(targetId, [...existing, normalizedMsg])
    messagesVersion.value++
    imCache.appendMessage(targetId, normalizedMsg)
  }
  updateConversation(targetId, message)
  showNotification(message)
}
```

#### 5.3 websocket.js reconnect 指数退避 + 抖动

```js
reconnect() {
  if (this.reconnectTimer || this.isManualClose) return
  // 指数退避 + 抖动: 3s, 4.5s, 6.75s, 10.1s, 15.2s，上限 30s
  const base = this.reconnectInterval
  const exp = Math.min(base * Math.pow(1.5, this.reconnectCount), 30000)
  const jitter = Math.random() * 1000
  this.reconnectTimer = setTimeout(() => {
    this.reconnectTimer = null
    if (!this.isManualClose && this.userId) {
      this.connect(this.userId)
    }
  }, exp + jitter)
}
```

同时 `maxReconnectCount` 从 5 提升到 10。

### 验证

- 5.1：发送消息后断言滚动到底部事件触发
- 5.2：自发群消息后 WebSocket 推送先到，断言只显示 1 条
- 5.3：网络抖动场景下断言重连间隔递增，5 次后仍未连接则继续重试至 10 次

---

## 验证总览

| 验证类型 | 范围 | 命令 |
|---|---|---|
| 后端编译 | ruoyi-im 模块 | `mvn -pl ruoyi-modules/ruoyi-im -am compile` |
| 前端构建 | Nexus_App | `npm run build`（在 Nexus_App 目录） |
| 单元测试 | ruoyi-im | `mvn -pl ruoyi-modules/ruoyi-im test` |
| SQL 演练 | 索引脚本 | 在测试库执行 + EXPLAIN |

---

## 提交规范

每个 P0 子任务独立 commit，提交信息格式:

```
fix(im): P0-X 简述

- 问题: ...
- 改造: ...
- 依据: doc/im/2026-07-17-im-p0-stability-fix-plan.md P0-X
```

最终合并到 `plus` 主分支前需通过:
1. 所有 P0 验证项
2. Code review
3. 测试环境回归
