# IM 模块优化点记录清单（Backlog）

> **创建日期**: 2026-07-18
> **来源**: P3 完成后的全模块代码审查（后端 nexus-erp/ruoyi-im + Web plus-ui + App Nexus_App）
> **用途**: 作为后续开发完善清单，每项完成后在"状态"列打勾并填写 commit hash
> **维护规则**: 新发现的优化点追加到对应优先级末尾；已实施的项目不删除，保留作为历史记录

---

## 一、文档说明

本清单基于 2026-07-18 对 IM 模块完成的全面代码审查，覆盖：
- **后端**：`ruoyi-modules/ruoyi-im`（消息推送、会话聚合、群聊扇出、定时清理、可观测性）
- **Web 前端**：`plus-ui/src/store/modules/im.ts` + `api/im/chat.ts`
- **App 前端**：`Nexus_App/store/modules/im.js` + `utils/im/im-cache.js` + `utils/websocket.js` + `pages/im/`

每项优化点包含：
- **编号**：B=后端 / W=Web / A=App
- **优先级**：P0（高价值，强烈建议立即实施）/ P1（中价值，下个迭代）/ P2（低价值，按需）
- **现状**：当前实现的简述
- **问题**：存在的性能瓶颈、可靠性风险或 UX 缺陷
- **优化方案**：可执行的改进路径
- **关联文件**：代码位置（含行号链接）
- **状态**：⏳ 待实施 / 🚧 进行中 / ✅ 已完成（commit hash）

---

## 二、后端优化点（nexus-erp/ruoyi-im）

### P0 高优先级

#### B1. 群消息扇出在事务内循环推送

- **关联文件**：[ImGroupChatService.java:67-127](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImGroupChatService.java#L67-L127)
- **现状**：`@Transactional` 方法内 `for (ImGroupMember m : members)` 循环对每个成员调用 `sysUserNotificationService.addAndPush` + `imRealtimePush.publishToUserAfterCommit`
- **问题**：
  - 1000 人群 = 1000 次 INSERT 通知 + 1000 次注册 afterCommit 钩子
  - 事务持有时间 ∝ 群成员数，长事务会锁 `im_message` / `sys_user_notification` 行
  - afterCommit 钩子列表巨大，事务提交后串行执行 1000 次推送，可能阻塞主线程数秒
- **优化方案**：
  - 通知表批量 INSERT（`insertBatch`），一次 SQL 完成所有通知插入
  - 推送改为线程池异步并行（`@Async` 或 `ExecutorService.submit`），按 batchSize 分片
  - 事务范围仅覆盖 `im_message` 插入 + 通知批量插入，推送完全异步
- **预期收益**：1000 人群发送延迟从 ~5s 降至 ~200ms
- **状态**：⏳ 待实施

#### B2. 群撤回事件同样在事务内循环推送

- **关联文件**：[ImMessageLifecycleService.java:73-95](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImMessageLifecycleService.java#L73-L95)
- **现状**：与 B1 同构问题，群撤回时 `for (ImGroupMember m : members)` 注册 N 个 afterCommit 钩子
- **优化方案**：
  - 群撤回事件只推一次"群广播"通道，前端订阅群消息即可
  - 或改为 `publishToUsersAfterCommit(members.stream().map(...).toList(), json)` 单次批量推送
- **预期收益**：群撤回延迟与群规模解耦
- **状态**：⏳ 待实施

#### B3. markGroupRead 一次性 SELECT 全部未读消息 ID

- **关联文件**：[ImGroupChatService.java:164-181](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImGroupChatService.java#L164-L181)
- **现状**：`selectList(... ne(from_user_id, me).eq(is_read, "0"))` 无 LIMIT，活跃群可能有数千条未读
- **问题**：SELECT 结果巨大，推送给原发送方的 messageIds 数组体积过大
- **优化方案**：
  - 已读回执改为按 `from_user_id` GROUP BY 聚合，每发送者只取最新一条 messageId 作为水位线
  - 或推送只发"已读水位线"（`maxMessageId`），前端按 `<= maxMessageId` 标记，避免传整列 messageId
- **预期收益**：大群已读回执消息体积降低 99%
- **状态**：⏳ 待实施

#### B4. listConversations 无 Redis 缓存层

- **关联文件**：[ImConversationService.java:57-68](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImConversationService.java#L57-L68)
- **现状**：每次调用 8 条 SQL 查询。前端登录 + 每次重连 + 每次 `markAsRead` 都会触发全量重算
- **优化方案**：
  - Redis 缓存 key `im:conv:{userId}`，TTL 60s
  - 消息发送/已读标记时主动失效对应会话缓存（`@CacheEvict` 或手动 delete）
  - 首屏走缓存，后台静默刷新最新数据（stale-while-revalidate 模式）
- **预期收益**：首屏延迟从 ~200ms 降至 ~20ms
- **状态**：⏳ 待实施

### P1 中优先级

#### B5. 推送无可观测指标

- **关联文件**：[ImRealtimePush.java](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/push/ImRealtimePush.java)
- **现状**：`ImRealtimePush` 只 `log.warn`，无 metrics 采集
- **优化方案**：引入 Micrometer：
  - `im.push.success` / `im.push.failure` Counter（按 chatType 标签）
  - `im.push.latency` Timer
  - `im.message.send` Counter（按 chatType 标签）
  - `im.conversation.cache.hit` / `miss` Counter（依赖 B4）
- **状态**：⏳ 待实施

#### B6. listPrivateConversations 私聊取 200 条按 peer 去重可能漏会话

- **关联文件**：[ImConversationService.java:230-256](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImConversationService.java#L230-L256)
- **现状**：`ORDER BY create_time DESC LIMIT 200`，若与某 1 个 peer 聊天超过 200 条，其他 peer 会被挤掉
- **优化方案**：改用 `GROUP BY from_user_id, to_user_id` 子查询取每对 peer 的 MAX(create_time)，再 JOIN 取详情
- **状态**：⏳ 待实施

#### B7. ImMessageClearTask 清理时段单一

- **关联文件**：[ImMessageClearTask.java:57](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/task/ImMessageClearTask.java#L57)
- **现状**：每天 04:00 单次执行，海量数据时单次清理可能跑数小时锁表
- **优化方案**：改为每小时跑一次，每次只清理 1 小时前的过期数据（按 `create_time` 分桶），平摊负载
- **状态**：⏳ 待实施

#### B8. WebSocket 推送无背压控制

- **关联文件**：[ImRealtimePush.java:25-46](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/push/ImRealtimePush.java#L25-L46)
- **现状**：`WebSocketUtils.publishMessage` 一次性向所有 session 推送，慢消费者会堆积
- **优化方案**：
  - 每用户维护一个有界队列（如 1000 条），溢出时丢弃最旧或关闭连接
  - 推送前检查 session 是否活跃（`SseMessageUtils.isEnable()` 已有类似检查，WebSocket 侧需补充）
- **状态**：⏳ 待实施

---

## 三、Web 前端优化点（plus-ui）

### P0 高优先级

#### W1. 无消息本地缓存，刷新即丢

- **关联文件**：[im.ts:115-117](file:///d:/workspace/nexus-erp/plus-ui/src/store/modules/im.ts#L115-L117)
- **现状**：`messagesMap` 是纯内存 ref，页面刷新（F5）后全部丢失，必须重新请求 API。App 端有 `im-cache.js`，Web 端无对应实现
- **优化方案**：
  - 用 `localStorage`（小数据量）或 `IndexedDB`（推荐，存量大）持久化最近 50 个会话 × 50 条消息
  - 复用 App 端 `im-cache.js` 的 LRU + 防抖 flush 设计
  - 新建 `plus-ui/src/utils/im/im-cache.ts`，与 App 端保持接口一致
- **状态**：⏳ 待实施

#### W2. WebSocket 断线后无 fallback 拉取

- **关联文件**：[im.ts:182-189](file:///d:/workspace/nexus-erp/plus-ui/src/store/modules/im.ts#L182-L189)
- **现状**：`init()` 只在页面加载时调用一次，WS 断线重连依赖全局 `im-realtime` 事件。若 WS 长时间断连，会话列表不会自动刷新未读数
- **优化方案**：
  - 监听 WS 重连成功事件，触发 `loadConversations()` + `syncUnreadMessages()`
  - 30s 兜底轮询会话列表（仅当 WS 不可达时）
- **状态**：⏳ 待实施

#### W3. pageInfoMap / draftMap 不清理，内存泄漏

- **关联文件**：[im.ts:117-118](file:///d:/workspace/nexus-erp/plus-ui/src/store/modules/im.ts#L117-L118)
- **现状**：切换会话不删除旧会话的 pageInfo 和 draft，长时间使用后 Map 无限增长
- **优化方案**：LRU 策略，仅保留最近 20 个会话的 page/draft 状态
- **状态**：⏳ 待实施

### P1 中优先级

#### W4. 消息列表无虚拟滚动

- **关联文件**：消息列表渲染组件（待定位）
- **现状**：`activeMessages` 直接 `v-for` 渲染，千条消息会卡顿
- **优化方案**：引入 `vue-virtual-scroller` 或 `@tanstack/vue-virtual`，仅渲染可视区域 ± 10 条
- **状态**：⏳ 待实施

#### W5. 标题闪烁 setInterval 1s 切换无电池优化

- **关联文件**：[im.ts:642-644](file:///d:/workspace/nexus-erp/plus-ui/src/store/modules/im.ts#L642-L644)
- **现状**：1s 间隔切换标题，移动设备耗电
- **优化方案**：用 `requestAnimationFrame` + 时间戳判断，或切换间隔改为 2s
- **状态**：⏳ 待实施

#### W6. ElNotification 无免打扰模式

- **关联文件**：[im.ts:629-634](file:///d:/workspace/nexus-erp/plus-ui/src/store/modules/im.ts#L629-L634)
- **现状**：所有非活跃会话消息都弹 ElNotification
- **优化方案**：
  - 全局"勿扰时段"配置（如 22:00-08:00 仅计数不弹窗）
  - 按会话级别 mute（群消息免打扰）
- **状态**：⏳ 待实施

#### W7. 无消息搜索功能

- **现状**：store 无 search action，用户无法找历史消息
- **优化方案**：
  - 新增 `searchMessages(keyword, chatType?, targetId?)` action
  - 调后端搜索接口（需后端补 `LIKE` 查询接口或 ES 索引）
- **状态**：⏳ 待实施

#### W8. 无消息撤回事件 UI 提示

- **关联文件**：[im.ts:523-527](file:///d:/workspace/nexus-erp/plus-ui/src/store/modules/im.ts#L523-L527)
- **现状**：`revoke()` 主动撤回有处理，但 WS 推送的 `action=revoke` 事件只在 `handleRealtime` 内 `markRevoked`，没有提示用户"对方撤回了一条消息"
- **优化方案**：撤回事件触发时，在消息位置显示灰色"XX 撤回了一条消息"提示，而非静默替换
- **状态**：⏳ 待实施

---

## 四、App 前端优化点（Nexus_App）

### P0 高优先级

#### A1. pendingQueue 离线消息队列不持久化

- **关联文件**：[im.js:46](file:///d:/workspace/Nexus_App/store/modules/im.js#L46)
- **现状**：`pendingQueue = ref([])` 纯内存，App 被系统杀掉后丢失，用户辛苦写好的离线消息消失
- **优化方案**：
  - 用 `uni.setStorageSync('im_pending_queue', ...)` 持久化
  - 与 `im-cache.js` 用相同的防抖 flush 策略
  - 登录后恢复队列并触发 `flushPendingQueue`
- **状态**：⏳ 待实施

#### A2. syncUnreadMessages 每次重连全量拉 200 条

- **关联文件**：[im.js:571-582](file:///d:/workspace/Nexus_App/store/modules/im.js#L571-L582)
- **现状**：WS 重连后调 `getUnreadMessages({ pageNum: 1, pageSize: 200 })`，未读消息超过 200 条会漏，少于 200 条会重复处理已读消息
- **优化方案**：引入 `syncToken`（最后一条消息的 messageId 或 createTime）：
  - 客户端记录本地 `lastSyncToken`
  - 重连时只拉 `messageId > lastSyncToken` 的增量
  - 后端新增 `/im/messages/sync?since={messageId}` 接口
- **预期收益**：重连拉取量降低 90%+
- **状态**：⏳ 待实施

#### A3. handleNewMessage 去重 O(n) 扫描，批量消息 O(n²)

- **关联文件**：[im.js:600-615](file:///d:/workspace/Nexus_App/store/modules/im.js#L600-L615)
- **现状**：每条新消息都 `existing.some(...)` 扫一遍现有列表。100 条批量消息 = 100×100 = 10000 次比较
- **优化方案**：维护 `Set<messageId>` 索引，去重 O(1)
- **状态**：⏳ 待实施

#### A4. markAsRead 强制刷新整个会话列表

- **关联文件**：[im.js:562-569](file:///d:/workspace/Nexus_App/store/modules/im.js#L562-L569)
- **现状**：`await markMessagesAsRead(...)` 后 `await loadConversations()`，仅为更新 1 个未读数，浪费一次 API
- **优化方案**：本地直接修改对应会话的 `unreadCount = 0`，不调用 `loadConversations`
- **状态**：⏳ 待实施

#### A5. updateConversation 找不到会话时调 loadConversations

- **关联文件**：[im.js:786-788](file:///d:/workspace/Nexus_App/store/modules/im.js#L786-L788)
- **现状**：新会话首条消息触发全量重拉会话列表。若同时收到 5 条不同新会话消息，会触发 5 次 `loadConversations`
- **优化方案**：
  - 本地直接 push 新会话对象到 `conversations.value`
  - 或加防抖（500ms 内合并多次 `loadConversations`）
- **状态**：⏳ 待实施

### P1 中优先级

#### A6. flushPendingQueue 串行重发

- **关联文件**：[im.js:520-558](file:///d:/workspace/Nexus_App/store/modules/im.js#L520-L558)
- **现状**：`for (const item of queue) await sendMessage(...)`，10 条待发消息串行需 10×RTT
- **优化方案**：按 `targetId` 分组，不同 target 用 `Promise.all` 并行；同 target 仍串行（保序）
- **状态**：⏳ 待实施

#### A7. imCache.appendMessage 双次 LRU touch

- **关联文件**：[im-cache.js:179-190](file:///d:/workspace/Nexus_App/utils/im/im-cache.js#L179-L190)
- **现状**：`appendMessage` 调 `getMessage`（触发 `touchCacheEntry`）→ `saveMessage`（再次触发 `touchCacheEntry`），同一会话被移动两次
- **优化方案**：`appendMessage` 内联 `messageCache.get` 不调 `getMessage`，避免重复 touch
- **状态**：⏳ 待实施

#### A8. WebSocket send 不连接时静默失败

- **关联文件**：[websocket.js:146-156](file:///d:/workspace/Nexus_App/utils/websocket.js#L146-L156)
- **现状**：`if (this.socketTask && this.isConnected)` 不满足直接 return，消息丢失
- **优化方案**：send 失败应返回 false 或 throw，让调用方决定入队重试
- **状态**：⏳ 待实施

#### A9. Emoji 面板一次性渲染全部 emoji

- **关联文件**：[chat/index.vue:78-96](file:///d:/workspace/Nexus_App/pages/im/chat/index.vue#L78-L96)
- **现状**：`v-for emoji in inputRef?.emojis` 全量渲染，常见 emoji 库 800+ 条目
- **优化方案**：分类 tab + 分页（每页 50 条），或用 `scroll-view` 触底加载
- **状态**：⏳ 待实施

#### A10. 无图片消息预加载与缓存策略

- **现状**：消息气泡图片直接 `<image src="...">`，无 disk cache 命中率统计，重复消息图片重复下载
- **优化方案**：
  - 用 `uni.getImageInfo` 预加载下一屏图片
  - 图片消息加 `thumbnailUrl` 字段（后端 OSS 裁剪参数 `?imageView2/2/w/200`），原图点击查看时再加载
- **状态**：⏳ 待实施

#### A11. 无消息搜索（与 W7 同）

- **现状**：聊天页无搜索入口
- **优化方案**：群信息页加"搜索聊天记录"入口，复用后端搜索接口
- **状态**：⏳ 待实施

#### A12. 无消息撤回 UI 提示（与 W8 同）

- **关联文件**：[im.js:586-625](file:///d:/workspace/Nexus_App/store/modules/im.js#L586-L625)
- **现状**：`handleNewMessage` / `handleNewGroupMessage` 未处理 `action=revoke`，撤回事件可能被当作普通消息处理
- **优化方案**：在 `handleNewMessage` / `handleNewGroupMessage` 内判断 `message.action === 'revoke'`，调专用 handler 替换原消息为"XX 撤回了一条消息"
- **状态**：⏳ 待实施

---

## 五、优先级汇总表

| 优先级 | 编号 | 优化点 | 端 | 预期收益 | 状态 |
|---|---|---|---|---|---|
| P0 | B1 | 群消息扇出异步化 + 批量通知 | 后端 | 1000 人群发送延迟 5s → 200ms | ⏳ |
| P0 | B2 | 群撤回事件单次广播 | 后端 | 同 B1 | ⏳ |
| P0 | B3 | markGroupRead 水位线模式 | 后端 | 大群已读回执体积降 99% | ⏳ |
| P0 | B4 | listConversations Redis 缓存 | 后端 | 首屏延迟 200ms → 20ms | ⏳ |
| P0 | W1 | Web 消息本地缓存 | Web | 刷新不丢消息 | ⏳ |
| P0 | W2 | WS 断线 fallback 轮询 | Web | 网络抖动时不漏消息 | ⏳ |
| P0 | W3 | pageInfo/draft LRU | Web | 内存泄漏修复 | ⏳ |
| P0 | A1 | pendingQueue 持久化 | App | 离线消息不丢失 | ⏳ |
| P0 | A2 | syncToken 增量同步 | App+后端 | 重连拉取量降 90% | ⏳ |
| P0 | A3 | 消息去重 O(1) 索引 | App | 批量消息处理性能 10× | ⏳ |
| P0 | A4 | markAsRead 不刷会话列表 | App | 减少 API 调用 | ⏳ |
| P0 | A5 | 新会话本地 push | App | 防止并发 loadConversations | ⏳ |
| P1 | B5 | Micrometer 推送指标 | 后端 | 可观测性 | ⏳ |
| P1 | B6 | 私聊会话去重 SQL 改写 | 后端 | 边缘用户不再漏会话 | ⏳ |
| P1 | B7 | 清理任务分桶 | 后端 | 锁表风险降低 | ⏳ |
| P1 | B8 | WS 推送背压 | 后端 | 慢消费者防护 | ⏳ |
| P1 | W4 | 消息列表虚拟滚动 | Web | 千条消息流畅渲染 | ⏳ |
| P1 | W5 | 标题闪烁优化 | Web | 移动设备省电 | ⏳ |
| P1 | W6 | 免打扰模式 | Web | UX | ⏳ |
| P1 | W7 | 消息搜索 | Web | 功能补全 | ⏳ |
| P1 | W8 | 撤回事件 UI 提示 | Web | UX | ⏳ |
| P1 | A6 | 队列并行重发 | App | 重发速度 N× | ⏳ |
| P1 | A7 | imCache 双 touch 修复 | App | 局部性能 | ⏳ |
| P1 | A8 | WS send 失败反馈 | App | 可靠性 | ⏳ |
| P1 | A9 | Emoji 面板分页 | App | 渲染性能 | ⏳ |
| P1 | A10 | 图片预加载与缩略图 | App | 流量与渲染 | ⏳ |
| P1 | A11 | 消息搜索 | App | 功能补全 | ⏳ |
| P1 | A12 | 撤回事件 UI 提示 | App | UX | ⏳ |

---

## 六、维护规则

1. **新增优化点**：在对应优先级末尾追加，编号顺延（如 B9、W9、A13）
2. **完成标记**：将状态改为 `✅ 已完成（commit hash: xxx）`，并简要说明实施偏差（如有）
3. **取消标记**：将状态改为 `❌ 不实施`，并说明原因（如已被其他方案替代）
4. **优先级调整**：可在表格末尾追加变更记录，注明调整原因
5. **定期复审**：每个迭代开始前复审本清单，确认是否调整优先级或废弃过时项

---

## 七、变更记录

| 日期 | 变更 | 操作人 |
|---|---|---|
| 2026-07-18 | 初始创建，从 P3 完成后的代码审查中归纳 28 项优化点（B1-B8 / W1-W8 / A1-A12） | nexus |
