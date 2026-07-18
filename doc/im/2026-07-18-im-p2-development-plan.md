# P2 IM 模块性能优化开发任务

> **分支**: `feature/im-p0-stability-fix`（在 P1 commit 基础上继续）
> **日期**: 2026-07-18
> **依据**: [2026-07-17-vim-vs-nexus-im-analysis.md](./2026-07-17-vim-vs-nexus-im-analysis.md) §四 M1/M6/M9 + §五 P2.5
> **范围**: 高频接口性能优化 + 缓存重构 + 竞态修复，1 周交付
> **前置**: P0 五项 + P1 七项改造已完成（commits c6a74933 / 0b74b27b / ed0cace）

---

## 一、任务总览

P2 聚焦性能优化与竞态修复，共 7 项任务，按风险/复杂度分三档实施：

| 档位 | 任务 | 风险 | 当前问题 | 预期收益 | 状态 |
|---|---|---|---|---|---|
| **A 易** | P2-1 listConversations N+1 批量化 | 低（单接口改造） | 5 类 N+1，10 个群组 = 60+ SQL | SQL 数降 80%（60→6） | 待实施 |
| **A 易** | P2-2 buildSenderMessageRow 批量化 | 低（单方法改造） | 历史接口每条消息查一次 user + 一次 oss | 50 条历史从 100+ SQL 降到 3 条 | 待实施 |
| **A 易** | P2-3 im-cache.js 增量更新 | 低（前端工具类） | appendMessage 全量序列化整个 Map | 写盘体积降 95%+ | 待实施 |
| **B 中** | P2-4 addGroupMembers TOCTOU 修复 | 中（并发语义变化） | 先 count 后 insert，并发下重复插入 | 消除 DuplicateKeyException | 待实施 |
| **B 中** | P2-5 createGroup 批量插入群成员 | 中（事务边界） | for 循环 insert，10 成员 = 10 SQL | 单次 INSERT VALUES 批量 | 待实施 |
| **C 大** | P2-6 listGroupMembers 批量化 | 高（多 Mapper 联动） | 每个成员查一次 user + 一次 oss | 50 成员从 100+ SQL 降到 3 条 | 待实施 |
| **C 大** | P2-7 移动端 im-cache LRU 淘汰 | 高（缓存语义变化） | 无大小上限，长期使用 storage 膨胀 | 固定上限 50 会话 × 200 条 | 待实施 |

本文档先实施 A 档三项（P2-1/P2-2/P2-3），B/C 档在 A 档验证稳定后单独推进。

---

## 二、A 档详细方案

### P2-1 listConversations N+1 批量化

**问题**: [ImConversationService.java#L43-L100](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImConversationService.java#L43-L100) `listConversations` 存在 5 类 N+1 查询：

| # | N+1 点 | 行号 | 当前调用 | 10 群组 + 10 私聊 SQL 数 |
|---|---|---|---|---|
| 1 | 群组信息 | L51 | `imGroupMapper.selectById(gm.getGroupId())` | 10 |
| 2 | 群最后消息 | L55-L58 | `imMessageMapper.selectOne(... limit 1)` per group | 10 |
| 3 | 群成员头像 | L64 → [ImChatSupport.java#L100-L114](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImChatSupport.java#L100-L114) | `loadGroupMemberAvatars` 内循环 `sysUserService.selectUserById` | 10×4 = 40 |
| 4 | 私聊对端用户 | L86 | `sysUserService.selectUserById(peer)` | 10 |
| 5 | 未读数 | L67 / L94 → `countUnreadGroup` / `countUnreadPrivate` | `selectCount` per conversation | 20 |
| **合计** | | | | **90 SQL** |

**改造目标**: 90 SQL → 6 SQL（降 93%）

**改造方案**:

`ImConversationService.listConversations` 重写为 6 步批量化：

```java
public List<ImConversationVo> listConversations() {
    Long me = LoginHelper.getUserId();

    // ── 第 1 步：用户参与的群组（1 SQL） ──
    List<ImGroupMember> memberships = imGroupMemberMapper.selectList(...);
    List<Long> groupIds = memberships.stream().map(ImGroupMember::getGroupId).distinct().toList();
    if (groupIds.isEmpty()) return listPrivateConversations(me); // 无群聊快速路径

    // ── 第 2 步：批量查群组信息（1 SQL） ──
    Map<Long, ImGroup> groupMap = imGroupMapper.selectVoByIds(groupIds).stream()
        .filter(g -> "0".equals(g.getStatus()))
        .collect(Collectors.toMap(ImGroup::getGroupId, g -> g));

    // ── 第 3 步：批量查每个群的最后消息（1 SQL，利用雪花 ID 单调递增特性） ──
    // 子查询取每群最大 messageId，再 JOIN 回主表拿 content/createTime
    // 用 ImMessageMapper.selectList + 自定义 SQL 或 MyBatis-Plus selectList + IN + 后处理
    List<ImMessage> lastMsgs = imMessageMapper.selectList(new LambdaQueryWrapper<ImMessage>()
        .select(ImMessage::getMessageId, ImMessage::getGroupId, ImMessage::getContent, ImMessage::getCreateTime)
        .in(ImMessage::getGroupId, groupIds)
        .orderByDesc(ImMessage::getMessageId));
    // 后处理：按 groupId 分组取第一个（messageId 最大 = 最新）
    Map<Long, ImMessage> lastMsgByGroup = new LinkedHashMap<>();
    for (ImMessage m : lastMsgs) {
        lastMsgByGroup.putIfAbsent(m.getGroupId(), m);
    }

    // ── 第 4 步：批量查未读数（1 SQL，GROUP BY） ──
    // 需要自定义 Mapper 方法：selectUnreadCountByGroup(me, groupIds) → List<Map<groupId, count>>
    // 或使用 MyBatis-Plus selectMaps + GROUP BY
    List<Map<String, Object>> groupUnread = imMessageMapper.selectMaps(new QueryWrapper<ImMessage>()
        .select("group_id", "count(*) as cnt")
        .in("group_id", groupIds)
        .ne("from_user_id", me)
        .eq("is_read", "0")
        .groupBy("group_id"));
    Map<Long, Long> groupUnreadMap = groupUnread.stream()
        .collect(Collectors.toMap(r -> ((Number) r.get("group_id")).longValue(),
                                    r -> ((Number) r.get("cnt")).longValue()));

    // ── 第 5 步：批量加载所有群前 4 名成员 + 批量查用户头像（1+1 SQL） ──
    // 5.1 一次查所有群的成员（按 join_time 升序），后处理每组取前 4
    List<ImGroupMember> allMembers = imGroupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
        .in(ImGroupMember::getGroupId, groupIds)
        .orderByAsc(ImGroupMember::getJoinTime));
    Map<Long, List<ImGroupMember>> membersByGroup = allMembers.stream()
        .collect(Collectors.groupingBy(ImGroupMember::getGroupId));
    // 5.2 收集所有需要查头像的 userId，批量查用户
    Set<Long> avatarUserIds = new LinkedHashSet<>();
    Map<Long, List<ImGroupMember>> top4ByGroup = new LinkedHashMap<>();
    for (var e : membersByGroup.entrySet()) {
        List<ImGroupMember> top4 = e.getValue().stream().limit(4).toList();
        top4ByGroup.put(e.getKey(), top4);
        top4.forEach(m -> avatarUserIds.add(m.getUserId()));
    }
    Map<Long, SysUserVo> userMap = batchLoadUsers(avatarUserIds);

    // ── 第 6 步：组装群聊 VO（无 SQL） ──
    for (Long gid : groupIds) {
        ImGroup g = groupMap.get(gid);
        if (g == null) continue;
        ImMessage last = lastMsgByGroup.get(gid);
        // ... 组装 VO
    }

    // ── 私聊会话同样批量化（复用 batchLoadUsers + 批量未读数） ──
    // ...

    list.sort(...);
    return list;
}
```

**新增 ImChatSupport 工具方法**:

```java
/**
 * 批量加载用户信息（避免 N+1）。
 * 使用 SysUserMapper.selectVoByIds 获取完整 VO（含 avatar），不走 @DataPermission。
 */
public Map<Long, SysUserVo> batchLoadUsers(Collection<Long> userIds) {
    if (CollUtil.isEmpty(userIds)) return Collections.emptyMap();
    return sysUserMapper.selectVoByIds(userIds).stream()
        .collect(Collectors.toMap(SysUserVo::getUserId, u -> u, (a, b) -> a));
}

/**
 * 批量解析头像 OSS URL（避免 N+1）。
 */
public Map<Long, String> batchResolveAvatarUrls(Collection<Long> ossIds) {
    Set<Long> validIds = ossIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    if (validIds.isEmpty()) return Collections.emptyMap();
    List<SysOssVo> list = sysOssService.listByIds(validIds);
    return list.stream().collect(Collectors.toMap(SysOssVo::getOssId, SysOssVo::getUrl, (a, b) -> a));
}
```

**关键设计**:
- 利用雪花 ID 单调递增特性：`MAX(message_id)` = 最新消息，避免 `ORDER BY create_time LIMIT 1` per group
- 群成员前 4 名一次查全部群成员，后处理按 `limit(4)` 取
- 未读数用 `GROUP BY group_id` 一次聚合
- 用户与 OSS 头像解析独立批量，避免循环调用

**验证**:
- 单元测试：mock 10 群组 + 10 私聊，断言 mapper 调用次数 ≤ 8
- 接口测试：`GET /im/chat/conversations` P95 延迟从 ~300ms 降到 ~50ms

---

### P2-2 buildSenderMessageRow 批量化

**问题**: [ImChatSupport.java#L210-L233](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImChatSupport.java#L210-L233) `buildSenderMessageRow` 在 `buildSenderMessagePage` 中循环调用，每条消息：
- 1 次 `sysUserService.selectUserById(fromUserId)` 查发送者
- 1 次 `sysOssService.listByIds(List.of(avatarId))` 查头像 URL

50 条历史消息 = **100+ SQL**。

**改造方案**:

`buildSenderMessagePage` 重写为批量化：

```java
public TableDataInfo<Map<String, Object>> buildSenderMessagePage(Page<ImMessage> page) {
    List<ImMessage> records = page.getRecords();
    if (records.isEmpty()) {
        return new TableDataInfo<>(Collections.emptyList(), page.getTotal());
    }

    // ── 第 1 步：收集所有 fromUserId，批量查用户（1 SQL） ──
    Set<Long> senderIds = records.stream()
        .map(ImMessage::getFromUserId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<Long, SysUserVo> userMap = batchLoadUsers(senderIds);

    // ── 第 2 步：收集所有 avatarId，批量查 OSS URL（1 SQL） ──
    Set<Long> avatarIds = userMap.values().stream()
        .map(SysUserVo::getAvatar)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<Long, String> avatarUrlMap = batchResolveAvatarUrls(avatarIds);

    // ── 第 3 步：组装行（无 SQL） ──
    List<Map<String, Object>> rows = records.stream()
        .map(m -> buildSenderMessageRow(m, userMap, avatarUrlMap))
        .toList();
    return new TableDataInfo<>(rows, page.getTotal());
}

/**
 * 单行构建（无 SQL，依赖外部批量加载的 Map）。
 */
public Map<String, Object> buildSenderMessageRow(ImMessage msg,
                                                  Map<Long, SysUserVo> userMap,
                                                  Map<Long, String> avatarUrlMap) {
    Map<String, Object> row = new LinkedHashMap<>();
    // ... 基础字段
    SysUserVo sender = msg.getFromUserId() == null ? null : userMap.get(msg.getFromUserId());
    if (sender != null) {
        row.put("senderId", sender.getUserId());
        row.put("senderName", StrUtil.blankToDefault(sender.getNickName(), sender.getUserName()));
        row.put("senderAvatar", avatarUrlMap.getOrDefault(sender.getAvatar(), ""));
    }
    return row;
}
```

**兼容性**: 保留原 `buildSenderMessageRow(ImMessage)` 单条版本，内部 fallback 到 N+1 调用（仅 `loadGroupMemberAvatars` 等少数非批量路径使用）。新增 `buildSenderMessageRow(ImMessage, Map, Map)` 重载供批量路径使用。

**验证**:
- `privateHistoryWithSender` / `groupHistoryWithSender` 50 条消息从 100+ SQL 降到 3 SQL

---

### P2-3 im-cache.js 增量更新

**问题**: [im-cache.js#L29-L43](file:///d:/workspace/Nexus_App/utils/im/im-cache.js#L29-L43) `saveMessage` 每次都把整个 `messageCache` Map 序列化到 storage：

```javascript
saveMessage(targetId, messages) {
    this.messageCache.set(targetId, cacheData)
    // ❌ 即使只更新了一个会话，也把所有会话全量写盘
    const cacheObject = Object.fromEntries(this.messageCache)
    storage.set(MESSAGE_CACHE_KEY, cacheObject)
}
```

`appendMessage` 调用 `saveMessage` → 触发全量序列化。10 个会话 × 200 条消息 = 2000 条消息每次新增都全量 JSON.stringify + storage.set。

**改造方案**:

引入"脏会话"标记 + 防抖批量写盘：

```javascript
class ImCache {
    constructor() {
        this.messageCache = new Map()
        this.conversationCache = null
        this.dirtyTargets = new Set()  // 脏会话标记
        this.flushTimer = null
        this.initCache()
    }

    /**
     * 标记某会话为脏，延迟 500ms 批量写盘（避免高频消息全量序列化）
     */
    markDirty(targetId) {
        this.dirtyTargets.add(targetId)
        this.scheduleFlush()
    }

    scheduleFlush() {
        if (this.flushTimer) return
        this.flushTimer = setTimeout(() => {
            this.flushDirty()
            this.flushTimer = null
        }, 500)
    }

    /**
     * 仅序列化脏会话，patch 到 storage（增量更新）
     */
    flushDirty() {
        if (this.dirtyTargets.size === 0) return
        try {
            // 从 storage 读当前缓存对象（避免内存与 storage 不一致）
            const existing = storage.get(MESSAGE_CACHE_KEY) || {}
            // 仅更新脏会话
            for (const targetId of this.dirtyTargets) {
                const cacheData = this.messageCache.get(targetId)
                if (cacheData) {
                    existing[targetId] = cacheData
                } else {
                    delete existing[targetId]
                }
            }
            storage.set(MESSAGE_CACHE_KEY, existing)
            this.dirtyTargets.clear()
        } catch (error) {
            console.error('Flush dirty cache failed:', error)
        }
    }

    saveMessage(targetId, messages) {
        const cacheData = { messages, timestamp: Date.now() }
        this.messageCache.set(targetId, cacheData)
        this.markDirty(targetId)  // 标记脏，延迟写盘
    }

    appendMessage(targetId, message) {
        const messages = this.getMessage(targetId) || []
        if (!messages.some(m => m.messageId === message.messageId)) {
            messages.push(message)
            this.saveMessage(targetId, messages)
        }
    }

    // 退出前强制 flush
    flushOnExit() {
        if (this.flushTimer) {
            clearTimeout(this.flushTimer)
            this.flushTimer = null
        }
        this.flushDirty()
    }
}

// 应用 onUnload / onHide 时调用 imCache.flushOnExit()
```

**关键设计**:
- 500ms 防抖：连续 appendMessage 只触发一次写盘
- 增量 patch：仅序列化脏会话，未变会话不参与 JSON.stringify
- 退出强制 flush：避免延迟写盘丢失

**验证**:
- 单元测试：连续 append 10 条消息，storage.set 调用次数 = 1（原 = 10）
- storage 写入体积：从 ~200KB 降到 ~20KB（单会话）

---

## 三、B/C 档任务概要（待 A 档验证后实施）

### P2-4 addGroupMembers TOCTOU 修复

**问题**: [ImGroupChatService.java#L223-L234](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImGroupChatService.java#L223-L234) 先 `selectCount` 检查再 `insert`，并发下两个请求都通过 count 检查，第二个 insert 触发 `DuplicateKeyException`（若 (group_id, user_id) 有唯一索引）或产生重复行（若无唯一索引）。

**改造**:
1. DDL: `im_group_member` 加 `UNIQUE KEY uk_group_user (group_id, user_id)`
2. 移除 `selectCount` 检查，直接 `insert`
3. 捕获 `DuplicateKeyException`，视为"已存在"跳过（幂等）
4. 返回值明确告知"新增 N 条 / 已存在 M 条"

### P2-5 createGroup 批量插入群成员

**问题**: [ImGroupChatService.java#L197-L203](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImGroupChatService.java#L197-L203) for 循环 `insert`，10 成员 = 10 SQL。

**改造**:
- 新增 `ImGroupMemberMapper.insertBatchSomeColumn(List<ImGroupMember>)`（MyBatis-Plus 扩展）
- 或使用 `SqlSession` 批量模式
- 单次 `INSERT INTO im_group_member (group_id, user_id, join_time) VALUES (?,?,?), (?,?,?), ...`

### P2-6 listGroupMembers 批量化

**问题**: [ImGroupChatService.java#L247-L262](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImGroupChatService.java#L247-L262) 每个成员查一次 `sysUserService.selectUserById` + 一次 `resolveAvatarUrl`。50 成员 = 100 SQL。

**改造**:
- 复用 P2-1 的 `batchLoadUsers` + `batchResolveAvatarUrls`
- 50 成员从 100 SQL 降到 3 SQL

### P2-7 移动端 im-cache LRU 淘汰

**问题**: im-cache 无大小上限，长期使用 storage 膨胀，每次全量序列化越来越慢。

**改造**:
- 固定上限：最多缓存 50 个会话 × 每个 200 条消息
- LRU 淘汰：`messageCache` 改为 `Map`（已天然按插入顺序），超限时 `delete` 最老的 key
- 单会话上限：`appendMessage` 时若 `messages.length > 200`，丢弃最旧的

---

## 四、验证清单

### A 档验证
- [ ] P2-1 listConversations SQL 数从 90+ 降到 ≤ 8（10 群组 + 10 私聊场景）
- [ ] P2-1 接口 P95 延迟从 ~300ms 降到 ~50ms
- [ ] P2-2 privateHistoryWithSender 50 条消息 SQL 数从 100+ 降到 3
- [ ] P2-3 im-cache.js 连续 append 10 条消息 storage.set 调用次数 = 1
- [ ] mvn -pl ruoyi-modules/ruoyi-im -am compile -DskipTests 通过
- [ ] Nexus_App 前端缓存功能正常（首次加载秒开 + 退出 flush 不丢消息）

### B/C 档验证（待 A 档完成后补充）
- [ ] P2-4 并发 addGroupMembers 同一用户不抛 DuplicateKeyException
- [ ] P2-5 createGroup 10 成员 SQL 数从 11 降到 2
- [ ] P2-6 listGroupMembers 50 成员 SQL 数从 100+ 降到 3
- [ ] P2-7 im-cache 50+ 会话时自动 LRU 淘汰最老会话

---

## 五、提交规范

```
perf(im): P2-X 简述

- 问题: ...
- 改造: ...
- 收益: SQL 数 / 延迟 / 体积变化
- 依据: doc/im/2026-07-18-im-p2-development-plan.md P2-X
```

---

## 六、风险评估与回滚

### 风险点
1. **P2-1 雪花 ID 单调性假设**: 若 messageId 不严格单调递增（如时钟回拨），`MAX(message_id)` 可能不是最新消息。**缓解**: 后处理时按 `createTime` 二次校验，取 `MAX(create_time)` 对应的消息。
2. **P2-3 防抖延迟丢失**: 应用崩溃时未 flush 的脏数据丢失。**缓解**: `onHide` / `onUnload` 强制 flush；crash 概率极低，丢失单条缓存可接受（服务端有完整历史）。
3. **P2-4 唯一索引**: 若历史数据有重复 (group_id, user_id)，加索引会失败。**缓解**: 先执行 `DELETE FROM im_group_member WHERE id NOT IN (SELECT min_id FROM ...)` 清理重复，再加索引。

### 回滚策略
- 每项任务独立提交，可单独 revert
- P2-1 保留原 `listConversations` 方法作为 `listConversationsLegacy`，验证 1 周后删除
- P2-3 保留原 `saveMessage` 全量序列化方法作为 `saveMessageLegacy`，配置开关切换

---

## 七、代码审查与修复记录

### 审查时间
2026-07-18，A/B/C 三档全部完成后统一审查。

### 审查方式
- 工具: TRAE-code-review skill
- 范围: P2 阶段全部 commit (nexus-erp: `81885ad3`/`21ec3faf`/`30ef7ddb`；Nexus_App: `c5948f3`/`5a4482f`)
- 交叉验证: 2 个 search 子代理独立验证所有问题，2/2 高置信度确认

### 审查通过项
- ✅ **P2-1 ImChatSupport.batchLoadUsers/batchResolveAvatarUrls**: 批量加载逻辑正确，注入 `SysUserMapper` 走 `selectVoByIds` 拿完整 VO（含 avatar），避开 `@DataPermission` 裁剪
- ✅ **P2-2 buildSenderMessagePage**: 批量化正确，抽 `buildSenderMessageRowBase` 共用基础字段，保留单条版本兼容非批量路径
- ✅ **P2-3 im-cache.js 防抖+增量 patch**: `dirtyTargets` Set + `flushTimer` 500ms 防抖 + `flushDirty` 仅序列化脏会话，`App.vue onHide` 触发 `flushOnExit`
- ✅ **P2-4 addGroupMembers TOCTOU 修复**: 移除 `selectCount` 检查，catch `DuplicateKeyException` 幂等跳过，`R.ok(msg)` 携带"新增 N 条/已存在 M 条"
- ✅ **P2-5 createGroup 批量插入**: `insertBatch` 一次插入所有成员，`group_id` 为新生成雪花 ID 无冲突风险
- ✅ **P2-6 listGroupMembers 批量化**: 复用 `batchLoadUsers` + `batchResolveAvatarUrls`，50 成员 100+ SQL → 3 SQL
- ✅ **P2-7 im-cache LRU 淘汰**: `evictIfNeeded` 按 `Map.keys().next()` 取首项淘汰，`touchCacheEntry` 用 `delete+set` 刷新顺序，单会话 200 条上限 `slice` 保留最新

### 审查发现问题与修复

#### 问题 1 (CRITICAL): listConversations 群消息查询无 LIMIT
- **位置**: [ImConversationService.java#L105-L115](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImConversationService.java#L105-L115)
- **问题**: 第 3 步 `lastMsgs` 查询 `selectList(... in(groupId, validGroupIds) orderByDesc(messageId))` 无 LIMIT 子句。10 群 × 10000 消息 = 10 万行加载到内存，比原 N+1（10 行）更糟
- **根因**: 文档方案描述"子查询取每群最大 messageId，再 JOIN 回主表"，但实现时直接全量加载 + 内存 `putIfAbsent` 取首条
- **修复**: 改为先 `selectMaps` 取每群 `MAX(message_id)`（N 行），再 `in(messageId, maxIds)` 查完整数据（N 行）。2 SQL 但每条 bounded，内存 O(N) 而非 O(N×M)
- **修复 commit**: `19b23fd1`

#### 问题 2 (CRITICAL): selectMaps + row.get("group_id") 返回 null
- **位置**: [ImConversationService.java#L118-L131](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImConversationService.java#L118-L131)（群未读数）+ [L252-L266](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImConversationService.java#L252-L266)（私聊未读数）
- **问题**: 项目配置 `mapUnderscoreToCamelCase=true`（[common-mybatis.yml#L9](file:///d:/workspace/nexus-erp/ruoyi-common/ruoyi-common-mybatis/src/main/resources/common-mybatis.yml#L9)），`selectMaps` 返回的 map key 自动转为驼峰（"group_id"→"groupId"、"from_user_id"→"fromUserId"）。原代码 `row.get("group_id")` 返回 null → 未读数始终为 0（功能静默失败）
- **根因**: 对 MyBatis `mapUnderscoreToCamelCase` 配置在 `selectMaps` 上的作用范围理解不足
- **修复**: 群未读数改 `row.get("groupId")`；私聊未读数改 `row.get("fromUserId")`
- **修复 commit**: `19b23fd1`

### 审查结论
P2 阶段 7 项任务中 5 项审查通过，2 项 CRITICAL 问题已修复并重新编译验证。修复后 P2 阶段代码可进入测试阶段。

### P2 阶段最终提交清单
| Commit | 仓库 | 内容 |
|---|---|---|
| `81885ad3` | nexus-erp | P2-1/P2-2 listConversations + buildSenderMessagePage 批量化 |
| `c5948f3` | Nexus_App | P2-3 im-cache.js 增量更新 + App.vue onHide flush |
| `21ec3faf` | nexus-erp | P2-4/P2-5 addGroupMembers TOCTOU + createGroup 批量插入 |
| `30ef7ddb` | nexus-erp | P2-6 listGroupMembers 批量化 |
| `5a4482f` | Nexus_App | P2-7 im-cache LRU 淘汰 + 单会话 200 条上限 |
| `19b23fd1` | nexus-erp | P2-1 审查修复（lastMsgs LIMIT + selectMaps key 驼峰） |
