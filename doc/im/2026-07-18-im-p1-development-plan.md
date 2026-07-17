# P1 IM 模块近期借鉴开发任务

> **分支**: `feature/im-p0-stability-fix`（在 P0 commit 基础上继续）
> **日期**: 2026-07-18
> **依据**: [2026-07-17-vim-vs-nexus-im-analysis.md](./2026-07-17-vim-vs-nexus-im-analysis.md) §五 P1
> **范围**: 功能补齐 + 性能优化，1-2 周交付
> **前置**: P0 五项改造已完成（commits 57699704 / 39dd41d9 / bd2356db / f5c9e2df / Nexus_App 0cfe971）

---

## 一、任务总览

P1 共 6 项任务，按风险/复杂度分三档实施：

| 档位 | 任务 | 风险 | 依赖 | 状态 |
|---|---|---|---|---|
| **A 易** | P1-1 消息清理任务 MessageClearTask | 低（新增独立类） | 无 | 待实施 |
| **A 易** | P1-2 消息类型策略模式 | 低（小范围重构） | 无 | 待实施 |
| **A 易** | P1-3 未读消息分页 + 死代码清理 | 低（接口小改） | 无 | 待实施 |
| **B 中** | P1-4 重发幂等 client_msg_id | 中（DDL 变更 + 客户端协议） | im_message 加列 | 待实施 |
| **B 中** | P1-5 @提及能力 | 中（DDL 变更 + 解析逻辑） | im_message 加列 | 待实施 |
| **B 中** | P1-6 已读回执推送 | 中（推送协议扩展） | 无 | 待实施 |
| **C 大** | P1-7 消息分发器抽象 | 高（ImChatServiceImpl 624 行拆分） | 全模块回归 | 待实施 |

本文档先实施 A 档三项（P1-1/P1-2/P1-3），B/C 档在 A 档验证稳定后单独推进。

---

## 二、A 档详细方案

### P1-1 消息清理任务 MessageClearTask

**问题**: `im_message` 和 `sys_user_notification` 两表无任何清理策略，随时间无限增长，拖慢历史查询和磁盘 IO。

**借鉴**: V-IM [MessageClearTask.java#L22-L48](file:///d:/workspace/V-IM/v-im-server-2025/v-im-server/src/main/java/com/vim/tio/task/MessageClearTask.java#L22-L48) `@Scheduled(cron = "0 0 4 * * ?")` 每天凌晨 4 点清理。

**改造**:

新建 `ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/task/ImMessageClearTask.java`：

```java
/**
 * IM 消息清理任务
 * <p>
 * 借鉴 V-IM MessageClearTask，每天凌晨 4 点清理超过保留期的消息和通知。
 * 清理策略：
 *   - im_message: 保留最近 180 天
 *   - sys_user_notification: 保留最近 90 天（已读 30 天 / 未读 90 天）
 * <p>
 * 注意：清理是物理删除，不可恢复。生产环境应先备份再清理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImMessageClearTask {

    /** 消息保留天数（默认 180 天） */
    private static final int MESSAGE_RETENTION_DAYS = 180;
    /** 已读通知保留天数（默认 30 天） */
    private static final int NOTIFICATION_READ_RETENTION_DAYS = 30;
    /** 未读通知保留天数（默认 90 天） */
    private static final int NOTIFICATION_UNREAD_RETENTION_DAYS = 90;
    /** 单批删除上限，避免长事务锁表 */
    private static final int BATCH_SIZE = 1000;

    private final ImMessageMapper messageMapper;
    private final SysUserNotificationMapper notificationMapper;

    /**
     * 每天凌晨 4 点执行清理
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void clearExpiredMessages() {
        // ... 分批删除逻辑
    }
}
```

**关键设计**:
- 使用 `@Scheduled(cron = "0 0 4 * * ?")` 每天 4 点执行（与 V-IM 一致）
- 分批删除（BATCH_SIZE=1000），避免长事务锁表
- 物理删除（`DELETE FROM`），非逻辑删除
- 清理前打 INFO 日志记录待删条数，清理后打 INFO 记录实际删除条数
- 单租户/多租户通用：直接 DELETE WHERE create_time < ?，TenantHelper 不参与（清理跨所有租户）

**验证**: 启动后人工触发（或在测试库手动跑 SQL）确认 DELETE 语句命中预期行。

---

### P1-2 消息类型策略模式

**问题**: [ImChatServiceImpl.java#L57](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/ImChatServiceImpl.java#L57) `SUPPORTED_MSG_TYPES = Set.of("text", "image", "file")` 硬编码，新增类型需改 Service。

**借鉴**: V-IM 未显式策略模式，但 nexus-erp 应做正交拆分，便于后续 P2 扩展 @提及/引用/语音等。

**改造**:

新建 `ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/msgtype/ImMessageTypeHandler.java` 接口和 3 个默认实现：

```java
public interface ImMessageTypeHandler {
    /** 消息类型 key，如 "text" / "image" / "file" */
    String type();

    /** 内容规范化（长度/格式校验），返回处理后的 content */
    String normalize(String content);

    /** 是否需要 OSS 预处理（图片/文件类） */
    default boolean requiresOss() { return false; }
}

@Component
public class TextMessageHandler implements ImMessageTypeHandler {
    private static final int MAX_TEXT = 2000;
    @Override public String type() { return "text"; }
    @Override public String normalize(String content) {
        String v = StrUtil.trim(content);
        if (StrUtil.isBlank(v)) throw new ServiceException("消息内容不能为空");
        if (v.length() > MAX_TEXT) throw new ServiceException("消息内容不能超过" + MAX_TEXT + "个字符");
        return v;
    }
}

@Component
public class ImageMessageHandler implements ImMessageTypeHandler { ... }
@Component
public class FileMessageHandler implements ImMessageTypeHandler { ... }
```

新增 `ImMessageTypeRegistry`：
```java
@Component
public class ImMessageTypeRegistry {
    private final Map<String, ImMessageTypeHandler> handlers;

    public ImMessageTypeRegistry(List<ImMessageTypeHandler> list) {
        this.handlers = list.stream().collect(Collectors.toUnmodifiableMap(
            ImMessageTypeHandler::type, h -> h));
    }

    public ImMessageTypeHandler require(String type) {
        String normalized = StrUtil.blankToDefault(StrUtil.trim(type), "text").toLowerCase();
        ImMessageTypeHandler h = handlers.get(normalized);
        if (h == null) throw new ServiceException("不支持的消息类型");
        return h;
    }

    public Set<String> supportedTypes() { return handlers.keySet(); }
}
```

**ImChatServiceImpl 改造**:
- 删除 `SUPPORTED_MSG_TYPES`、`MAX_TEXT_CONTENT_LENGTH`、`normalizeContent`、`normalizeMsgType` 私有方法
- 注入 `ImMessageTypeRegistry`
- `sendPrivate`/`sendGroup` 内：
  ```java
  ImMessageTypeHandler handler = registry.require(bo.getMsgType());
  String content = handler.normalize(bo.getContent());
  String msgType = handler.type();
  ```

**收益**: 新增消息类型只需新增 Handler 类 + Spring 自动注册，无需改 ImChatServiceImpl。

---

### P1-3 未读消息分页 + 死代码清理

**问题 1**: [AppImController.java#L547-L556](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/controller/AppImController.java#L547-L556) `unreadMessages` 返回全部未读，未读 1000+ 条时单次响应过大。

**问题 2**: [im.js#L112](file:///d:/workspace/Nexus_App/store/modules/im.js#L112) 注册 `group_notification` 监听但 [websocket.js](file:///d:/workspace/Nexus_App/utils/websocket.js) 从未 emit 此事件，是死代码。

**改造**:

1. **未读消息分页**：`unreadMessages` 改为接受 `PageQuery`，返回 `TableDataInfo<ImMessage>`：
   ```java
   @GetMapping("/message/unread")
   public TableDataInfo<ImMessage> unreadMessages(PageQuery pageQuery) {
       Long me = LoginHelper.getUserId();
       LambdaQueryWrapper<ImMessage> w = new LambdaQueryWrapper<ImMessage>()
           .eq(ImMessage::getToUserId, me)
           .eq(ImMessage::getIsRead, "0")
           .isNull(ImMessage::getGroupId)
           .orderByDesc(ImMessage::getCreateTime);
       Page<ImMessage> page = messageMapper.selectPage(pageQuery.build(), w);
       return TableDataInfo.build(page);
   }
   ```

2. **死代码清理**：删除 `im.js` 中 `uni.$on('group_notification', ...)` 监听注册。

**兼容性**: 未读接口响应格式从 `R<List<ImMessage>>` 改为 `TableDataInfo<ImMessage>`，前端调用方需同步适配。Nexus_App 是唯一调用方，需同步改 `api/im/message.js` 中的对应方法。

---

## 三、B/C 档任务概要（暂不实施）

### P1-4 重发幂等 client_msg_id

- im_message 表加 `client_msg_id varchar(64) NULL` + `UNIQUE KEY uk_client_msg_id(tenant_id, client_msg_id)`
- ImMessage 实体加 `clientMsgId` 字段
- ImPrivateSendBo/ImGroupSendBo 加 `clientMsgId` 字段
- sendPrivate/sendGroup 内：若 clientMsgId 非空，先 `SELECT message_id WHERE client_msg_id=?`，命中则直接返回旧 messageId
- Nexus_App `sendPrivateMessage`/`sendGroupMessage` 生成 UUID 作为 clientMsgId 传入

### P1-5 @提及能力

- im_message 表加 `mention_user_ids varchar(500) NULL`（逗号分隔的 user ID 列表）
- ImMessage 实体加 `mentionUserIds` 字段
- 新增 `mention` 消息类型 Handler（或扩展 text 类型）
- sendGroup 接口校验 mention 用户均为群成员
- 推送 payload 携带 mentionUserIds，前端高亮显示
- 移动端 `@` 按钮组件 + 用户选择器

### P1-6 已读回执推送

- markPrivateRead/markGroupRead 成功后，向原发送方推送 `im_message_read` 类型信封
- payload: `{ messageIds: [...], readAt: timestamp, readBy: userId }`
- 前端收到后更新消息状态为"已读"
- 借鉴 V-IM [VimMessageServiceImpl.java#L280-L361](file:///d:/workspace/V-IM/v-im-server-2025/v-im-server/src/main/java/com/vim/sdk/service/impl/VimMessageServiceImpl.java#L280-L361) Redis 时间戳方案

### P1-7 消息分发器抽象

- 拆分 ImChatServiceImpl 624 行为：
  - `ImPrivateChatService` 私聊相关
  - `ImGroupChatService` 群聊相关
  - `ImConversationService` 会话聚合
  - `ImMessageReadService` 已读标记
- 保持 IImChatService 接口不变，内部委派到新 Service
- 高风险，需全模块回归测试，建议 P1 末或 P2 初实施

---

## 四、验证清单

- [ ] P1-1 ImMessageClearTask 编译通过，定时任务注册成功（启动日志可见）
- [ ] P1-2 消息类型策略模式：发送 text/image/file 均正常，发送未知类型返回"不支持的消息类型"
- [ ] P1-3 未读消息分页：默认 10 条/页，可传 pageNum/pageSize
- [ ] P1-3 死代码清理：im.js 无 `group_notification` 监听，编译通过
- [ ] mvn -pl ruoyi-modules/ruoyi-im -am compile -DskipTests 通过
- [ ] Nexus_App 前端调用方同步适配未读分页接口

---

## 五、提交规范

```
feat(im): P1-X 简述

- 问题: ...
- 改造: ...
- 依据: doc/im/2026-07-18-im-p1-development-plan.md P1-X
```
