# IM 聊天页优化 — 实施计划

> 日期：2026-07-07
> 范围：前端 `plus-ui/src/views/im/chat/index.vue` + `plus-ui/src/api/im/chat.ts`；后端 `ruoyi-modules/ruoyi-im`（仅 P1-1 接线）
> 决策：**分三批落地**，第一批（P0 纯前端 + P1-1 后端接线）优先实施

## 一、背景与现状缺口

`plus-ui/src/views/im/chat/index.vue`（577 行，单文件）是即时通讯 Web 端聊天页，当前标记 Beta，可用但体感粗糙。后端实时推送基础设施已完备（SSE + Redis Pub/Sub 跨节点广播），且推送 payload 已携带完整消息体，但前端未充分利用。

| 缺口 | 证据 |
|------|------|
| 收到推送全量重拉历史 + 会话 | `handleRealtime` L357-376：`loadMessages()` + `loadConversations()` |
| 推送 payload 含完整消息体却丢弃 | 后端 `buildPrivatePayload`（`ImChatServiceImpl.java:481`）/ `buildGroupPayload`（:497）已含 messageId/fromUserId/content/msgType/createTime/senderName/senderAvatar |
| 历史接口返回裸 `ImMessage`（无 senderName） | `ImChatController.java:55-64` 暴露的是 `privateHistory`/`groupHistory`；`privateHistoryWithSender`/`groupHistoryWithSender`（Service 已实现 `ImChatServiceImpl.java:165/191`）**未接线** |
| 群未读恒为 0、无群标已读接口 | `listConversations` 群 `unreadCount` 写死 `0L`（:227）；`markGroupRead`（:339）Service 已有但 Controller 未暴露 |
| 消息只渲染 `{{ m.content }}`，忽略 `msgType`/`senderName` | `index.vue:110-118` |
| 布局写死像素、不自动滚底、草稿不保留、发送失败丢消息 | `index.vue:20/107/265/176` |

## 二、实施范围

### 第一批（P0 纯前端 + P1-1 后端接线）— 本次实施

| 项 | 内容 | 文件 |
|---|------|------|
| P0-1 | 增量实时：推送 payload 直接追加，去重，不再全量重拉 | `index.vue` `handleRealtime` |
| P0-2 | 发送乐观渲染 + 失败回填（不丢消息） | `index.vue` `onSend` |
| P0-3 | 自动滚底（加载/发送/接收后） | `index.vue` |
| P0-4 | 布局自适应（flex 列布局，去掉写死 max-height） | `index.vue` `<style>` |
| P0-5 | 草稿按会话保留 | `index.vue` `draft` → `Map` |
| P0-6 | 时间格式化（今天 HH:mm / 昨天 / MM-DD） | `index.vue` `formatTime` |
| P0-7 | 选中私聊会话自动标已读、清 badge | `index.vue` `onSelectConv` |
| P1-1 | 历史带发送者：暴露 withSender 端点 + 前端改用、渲染 senderName/senderAvatar | `ImChatController.java` + `chat.ts` + `index.vue` |

### 第二批（P1，后续）

- P1-2 群未读真实计数 + `PUT /im/chat/group/read`（Controller 接线 `markGroupRead`；`listConversations` 群 unreadCount 改真实计数）
- P1-3 图片/文件消息渲染（约定 `content` 存 ossId/URL，复用 `ruoyi-common-oss`）
- P1-4 向上滚动加载更多历史（`@scroll` 到顶、`pageNum++`、前插保持滚动位置）
- P1-5 真实头像（用 `avatar`/`memberAvatars`/`senderAvatar`，无图回落首字母）

### 第三批（P2，可选）

- P2-1 抽 IM Pinia store（仿 `notice.ts`，跨路由保留状态）
- P2-2 用户选择器替代手输 ID（复用 `SysUser` 选择组件）
- P2-3 群成员列表（后端补接口 + 抽屉渲染）
- P2-4 消息撤回（`revoked` 字段已存在，补接口 + UI）
- P2-5 消息转发（`forwardFrom` + 发送 BO 已支持，补 UI）
- P2-6 已读回执（渲染 `isRead`）
- P2-7 新消息提醒（活跃聚焦抑制 `ElNotification`、非聚焦标题闪烁）
- P2-8 会话内搜索（keyword 同时匹配 title 与 lastContent）

## 三、第一批详细改动

### 改动 1（P1-1 后端）：暴露 withSender 端点

**文件** `ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/controller/ImChatController.java`

Service 层 `privateHistoryWithSender` / `groupHistoryWithSender`（`ImChatServiceImpl.java:165/191`）已实现，返回 `TableDataInfo<Map<String,Object>>`，每行含 `messageId/fromUserId/toUserId/groupId/content/msgType/messageType/isRead/revoked/forwardFrom/createTime/senderId/senderName/senderAvatar`。仅需 Controller 接线：

```java
@SaCheckLogin
@GetMapping("/private/history/withSender")
public TableDataInfo<Map<String, Object>> privateHistoryWithSender(@NotNull @RequestParam Long peerUserId, PageQuery pageQuery) {
    return imChatService.privateHistoryWithSender(peerUserId, pageQuery);
}

@SaCheckLogin
@GetMapping("/group/history/withSender")
public TableDataInfo<Map<String, Object>> groupHistoryWithSender(@NotNull @RequestParam Long groupId, PageQuery pageQuery) {
    return imChatService.groupHistoryWithSender(groupId, pageQuery);
}
```

### 改动 2（前端 API）：新增 withSender 接口

**文件** `plus-ui/src/api/im/chat.ts`

```ts
export function privateHistoryWithSender(params: { peerUserId: number; pageNum?: number; pageSize?: number }) {
  return request({ url: '/im/chat/private/history/withSender', method: 'get', params });
}
export function groupHistoryWithSender(params: { groupId: string | number; pageNum?: number; pageSize?: number }) {
  return request({ url: '/im/chat/group/history/withSender', method: 'get', params });
}
```

### 改动 3（P0-1）：增量实时

`handleRealtime` 不再 `loadMessages()`，改为：用 payload 的 `messageId` 去重后 `messages.value.push(payload)`；仅就地更新对应会话 `lastContent`/`lastTime` 并置顶；非活跃会话 `unreadCount++`，活跃会话追加后自动滚底 + 标已读。`loadConversations` 仅在建群/加成员/发送后调用。

### 改动 4（P0-2）：乐观渲染 + 失败回填

`onSend`：发送前 `messages.value.push({ clientTempId, fromUserId: me, content, msgType:'text', status:'sending' })`、清空输入、滚底；成功用返回 `messageId` 替换临时消息；失败回填 `draft` 并标记 `status:'failed'`，提供重试。保留一次静默 `loadMessages` 兜底。

### 改动 5（P0-3）：自动滚底

`el-scrollbar` 加 `ref`，在 `loadMessages` 完成、`onSend`、`handleRealtime` 追加后 `nextTick(() => scrollbarRef.setScrollTop(极大值))`。向上加载更多时（P1-4）保留原位置。

### 改动 6（P0-4）：布局自适应

`<style>` 改 flex 列布局：`.im-page` 固定高、卡片 `:deep(.el-card__body)` 设 `flex:1; min-height:0; display:flex; flex-direction:column`；消息区 `flex:1; min-height:0`、editor 固定高。去掉 `max-height="560px/460px"`，scrollbar `height:100%`。

### 改动 7（P0-5）：草稿按会话保留

`draft: ref('')` → `draftMap: ref<Record<string, string>>({})`；`computed draft` 读写当前会话 key；切换会话自动恢复。

### 改动 8（P0-6）：时间格式化

`formatTime` 升级：今天 `HH:mm`、昨天 `昨天 HH:mm`、今年 `MM-DD HH:mm`、往年 `YYYY-MM-DD`。会话列表与消息时间统一。

### 改动 9（P0-7）：私聊自动标已读

`onSelectConv` 私聊分支：加载消息后调 `markPrivateRead(peerUserId)` 并清该会话 `unreadCount`（本地置 0，避免等 `loadConversations`）。

## 四、风险与注意事项

| 风险 | 应对 |
|------|------|
| 历史（withSender）有 senderName、推送 payload 也有 → 字段一致，可统一渲染 | P0-1 与 P1-1 同批做，避免历史/新增不一致 |
| 乐观渲染临时消息无 `messageId` | 用客户端 `clientTempId` 区分，成功后按 id 替换 |
| 雪花 ID 精度 | `messageId`/`groupId`/`fromUserId` 已由全局 `BigNumberSerializer` 序列化为字符串；**比较用字符串相等**，勿转 Number 比较长 ID |
| 增量渲染纯前端无写库 | 不涉及多租户上下文问题 |
| `listConversations` N+1 性能 | P0 减少其调用频次（不再每条消息都调），本身已降负载 |

## 五、验收

- 后端 `mvn -pl ruoyi-modules/ruoyi-im compile` 通过
- 前端 `npm run lint:eslint` 无新增错误
- 手动验证：发消息即时出现（乐观渲染）、收到消息增量追加不闪、自动滚底、切会话草稿保留、私聊选中清未读、历史消息显示发送者名称头像、布局随窗口自适应

## 六、实施进度

- **第一批（P0 + P1-1）**：已完成（2026-07-07）。`ImChatController` 暴露 withSender 端点；`index.vue` 重写落地增量实时/乐观渲染/自动滚底/布局 flex/草稿保留/时间格式/私聊自动已读。
- **第二批（P1）+ 第三批（P2）**：已完成（2026-07-07）。
  - 后端：`ImChatController`/`IImChatService`/`ImChatServiceImpl` 新增群已读 `PUT /group/read`、群成员列表 `GET /group/{id}/members`、移除成员 `DELETE /group/{id}/members/{userId}`、撤回 `PUT /message/{id}/revoke`（仅发送者、2 分钟内、实时推送 revoke）；`listConversations` 群未读改为真实计数；新增 `ImGroupMemberVo`。
  - 前端：新建 `store/modules/im.ts`（全局 realtime 监听 + 通知抑制 + 标题闪烁 + 会话/消息/草稿状态）；`api/im/chat.ts` 补 `markGroupRead`/`listGroupMembers`/`removeGroupMember`/`revokeMessage`/`uploadOss`，send 加 `forwardFrom`；`utils/sse.ts`、`utils/websocket.ts` 移除 `IM_MESSAGE` 的 `ElNotification`（交由 store 统一处理）；`layout/index.vue` 挂载 `useImStore().init()`；`views/im/chat/index.vue` 重写为 store 视图，整合图片/文件消息、撤回、转发、已读回执、向上加载更多、用户选择器（复用 `UserSelect`）、群成员抽屉、会话内搜索、真实头像。
  - 校验：后端 `mvn -pl ruoyi-modules/ruoyi-im -am compile` 通过；前端 eslint 改动文件零错误；`vue-tsc --noEmit` 改动文件零类型错误（全项目有既有错误，与本次无关）。
- **未做**：运行时手动验证（需起服务点测）；撤回的对方实时更新依赖 SSE 通道在线。
