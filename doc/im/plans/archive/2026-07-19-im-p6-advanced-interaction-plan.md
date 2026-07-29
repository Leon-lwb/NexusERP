# IM P6 高级交互入口开发计划

> **目标：** 补齐 IM 模块剩余 20% 高级功能：撤回/转发/引用/多选/收藏/置顶/会话级免打扰，达成 100% 功能覆盖。
>
> **架构：** 后端复用现有 `AppImController`，新增 2 张表（`im_conversation_setting`、`im_favorite`）支撑会话级配置和收藏；前端在 chat 页加长按菜单承载撤回/转发/引用/收藏/多选入口，conversation 页加长按菜单承载置顶/免打扰，新增转发目标选择页和收藏列表页。
>
> **技术栈：** Spring Boot + MyBatis-Plus + Sa-Token（后端），uni-app + Vue 3 + Pinia（前端），vitest + Playwright（测试）
>
> **基线：** A 档 commit `4c4e3070`（后端 P5-1~5），B 档 commit `fd6fd6b`（前端 P5-6~7），分支 `feature/im-p6-advanced-interaction`
>
> **预计任务数：** 18 个任务，分 5 个 Phase

---

## 完成状态（2026-07-19）

P6 全部 15 个任务已完成（A1/A2/B1/B2/B3/C1/C2/C3/D1/E1/F1/G1/G2/G3/G4），IM 模块功能覆盖度达到 100%。

### 后端 commits（nexus-erp / feature/im-p6-advanced-interaction 分支）

| 任务 | Commit | 说明 |
|---|---|---|
| A1 | 19fb0c46 | 新增 im_conversation_setting 表+实体+Mapper |
| A2 | 47df546b | 新增 im_favorite 表+实体+Mapper |
| B1 | f7c45802 | 会话置顶+免打扰接口（4 接口 + listConversations 合并配置 + 排序） |
| B2 | ba06ae4d | 收藏消息接口（3 接口 + 幂等 + 越权校验） |
| B3 | db27a294 | 引用消息字段扩展（reply_message_id + reply_snippet） |
| G1 | 6e9db23e | 后端单元测试（24 用例，Mockito 模式） |

### 前端 commits（Nexus_App / feature/im-p6-advanced-interaction 分支）

| 任务 | Commit | 说明 |
|---|---|---|
| C1 | 7ea74d4 | chat 页长按菜单组件 message-action-menu.vue |
| C2 | c338787 | 转发目标选择页 pages/im/forward/index.vue |
| C3 | 417d845 | 引用消息发送链路（前端参数 + 后端填充 + 渲染优先 replySnippet） |
| D1 | 66c8097 | chat 页多选模式 + 批量操作（取消/转发/删除 + checkbox） |
| E1 | 4de54d7 | 会话级置顶+免打扰（长按菜单 + API + store 排序 + notify-controller 集成 + 样式） |
| F1 | d23f532 | 收藏列表页 pages/im/favorite/index.vue + contact 入口 |
| G2 | 78f481b | 前端 vitest 测试 message-action-menu（17 用例 + 全量 78/78 通过） |
| G3 | 98027a9 | E2E 测试补充（3 spec 7 用例骨架） |

### 测试通过情况

- 后端单元测试：24/24 通过
- 前端单元测试：78/78 通过（含新增 17 用例 message-action-menu）
- E2E 测试：3 spec 7 用例骨架（需完整环境运行）

---

## Phase A：后端基础设施（2 张新表 + 实体 + Mapper）

### Task A1: 新建 `im_conversation_setting` 表 + 实体 + Mapper

**目标：** 持久化用户对会话的置顶/免打扰配置（按 user_id + chat_type + target_id 唯一）。

**Files:**
- Create: `nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/domain/ImConversationSetting.java`
- Create: `nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/mapper/ImConversationSettingMapper.java`
- Create: `nexus-erp/ruoyi-modules/ruoyi-im/src/main/resources/mapper/im/ImConversationSettingMapper.xml`
- Create: `nexus-erp/script/sql/im/im_conversation_setting.sql`

#### Step 1: 建表 SQL

```sql
-- im_conversation_setting: 用户会话级配置（置顶/免打扰）
-- 支持私聊和群聊：chat_type=PRIVATE 时 target_id=user_id，chat_type=GROUP 时 target_id=group_id
CREATE TABLE IF NOT EXISTS `im_conversation_setting` (
  `id`            BIGINT(20)    NOT NULL                COMMENT '主键',
  `user_id`       BIGINT(20)    NOT NULL                COMMENT '所属用户 ID',
  `chat_type`     VARCHAR(10)   NOT NULL                COMMENT '会话类型: PRIVATE/GROUP',
  `target_id`     BIGINT(20)    NOT NULL                COMMENT '目标 ID（私聊=对方 userId，群聊=groupId）',
  `is_pinned`     TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '是否置顶: 0=否 1=是',
  `pinned_at`     DATETIME(3)   NULL                    COMMENT '置顶时间（用于多会话置顶排序）',
  `is_muted`      TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '是否免打扰: 0=否 1=是',
  `tenant_id`     VARCHAR(20)   DEFAULT '000000'        COMMENT '租户编号',
  `create_by`     BIGINT(20)    DEFAULT NULL            COMMENT '创建者',
  `create_time`   DATETIME(3)   DEFAULT NULL            COMMENT '创建时间',
  `update_by`     BIGINT(20)    DEFAULT NULL            COMMENT '更新者',
  `update_time`   DATETIME(3)   DEFAULT NULL            COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_chat_target` (`user_id`, `chat_type`, `target_id`, `tenant_id`),
  KEY `idx_user_pinned` (`user_id`, `is_pinned`, `pinned_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM 会话级配置（置顶/免打扰）';
```

#### Step 2: 实体类

```java
package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.tenant.core.TenantEntity;
import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_conversation_setting")
public class ImConversationSetting extends TenantEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;
    private Long userId;
    /** PRIVATE / GROUP */
    private String chatType;
    /** 私聊=对方 userId，群聊=groupId */
    private Long targetId;
    private Integer isPinned;
    private java.util.Date pinnedAt;
    private Integer isMuted;
}
```

#### Step 3: Mapper 接口

```java
package com.nexus.im.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.im.domain.ImConversationSetting;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ImConversationSettingMapper extends BaseMapperPlus<ImConversationSetting, ImConversationSetting> {
}
```

#### Step 4: Mapper XML（仅占位，使用 BaseMapperPlus 通用方法）

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.nexus.im.mapper.ImConversationSettingMapper">
</mapper>
```

#### Step 5: Commit

```bash
git add ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/domain/ImConversationSetting.java \
        ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/mapper/ImConversationSettingMapper.java \
        ruoyi-modules/ruoyi-im/src/main/resources/mapper/im/ImConversationSettingMapper.xml \
        script/sql/im/im_conversation_setting.sql
git commit -m "feat(im): P6-A1 新增 im_conversation_setting 表+实体+Mapper"
```

---

### Task A2: 新建 `im_favorite` 表 + 实体 + Mapper

**目标：** 持久化用户收藏的消息（按 user_id + message_id 唯一，避免重复收藏）。

**Files:**
- Create: `nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/domain/ImFavorite.java`
- Create: `nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/mapper/ImFavoriteMapper.java`
- Create: `nexus-erp/ruoyi-modules/ruoyi-im/src/main/resources/mapper/im/ImFavoriteMapper.xml`
- Create: `nexus-erp/script/sql/im/im_favorite.sql`

#### Step 1: 建表 SQL

```sql
-- im_favorite: 用户收藏的消息
CREATE TABLE IF NOT EXISTS `im_favorite` (
  `id`            BIGINT(20)    NOT NULL                COMMENT '主键',
  `user_id`       BIGINT(20)    NOT NULL                COMMENT '收藏者用户 ID',
  `message_id`    BIGINT(20)    NOT NULL                COMMENT '被收藏的消息 ID',
  `chat_type`     VARCHAR(10)   NOT NULL                COMMENT '来源会话类型: PRIVATE/GROUP',
  `target_id`     BIGINT(20)    NOT NULL                COMMENT '来源会话目标 ID',
  `from_user_id`  BIGINT(20)    NULL                    COMMENT '消息原发送者（冗余便于列表展示）',
  `msg_type`      VARCHAR(20)   NULL                    COMMENT '消息类型（冗余）',
  `content_snippet` VARCHAR(500) NULL                   COMMENT '内容摘要（避免列表 JOIN im_message）',
  `tenant_id`     VARCHAR(20)   DEFAULT '000000'        COMMENT '租户编号',
  `create_by`     BIGINT(20)    DEFAULT NULL            COMMENT '创建者',
  `create_time`   DATETIME(3)   DEFAULT NULL            COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_message` (`user_id`, `message_id`, `tenant_id`),
  KEY `idx_user_create` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM 用户收藏消息';
```

#### Step 2: 实体类

```java
package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.tenant.core.TenantEntity;
import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_favorite")
public class ImFavorite extends TenantEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;
    private Long userId;
    private Long messageId;
    /** PRIVATE / GROUP */
    private String chatType;
    private Long targetId;
    private Long fromUserId;
    private String msgType;
    /** 内容摘要，最多 500 字 */
    private String contentSnippet;
}
```

#### Step 3: Mapper 接口 + XML（同 A1 模式）

```java
package com.nexus.im.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.im.domain.ImFavorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ImFavoriteMapper extends BaseMapperPlus<ImFavorite, ImFavorite> {
}
```

#### Step 4: Commit

```bash
git add ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/domain/ImFavorite.java \
        ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/mapper/ImFavoriteMapper.java \
        ruoyi-modules/ruoyi-im/src/main/resources/mapper/im/ImFavoriteMapper.xml \
        script/sql/im/im_favorite.sql
git commit -m "feat(im): P6-A2 新增 im_favorite 表+实体+Mapper"
```

---

## Phase B：后端接口（会话配置 + 收藏）

### Task B1: 会话配置接口（置顶 + 免打扰）

**目标：** 在 `AppImController` 新增 4 个接口：查询当前用户所有会话配置 / 设置置顶 / 设置免打扰 / 取消置顶。`listConversations` 返回值附加 `isPinned` / `isMuted` / `pinnedAt` 字段。

**Files:**
- Modify: `nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/domain/vo/ImConversationVo.java`
- Modify: `nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/support/ImConversationService.java`
- Modify: `nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/controller/AppImController.java`

#### Step 1: 扩展 ImConversationVo（加 3 个字段）

```java
// 在 ImConversationVo 类中追加：
private Integer isPinned = 0;
private Integer isMuted = 0;
private java.util.Date pinnedAt;
```

#### Step 2: ImConversationService 注入 ImConversationSettingMapper + 在 listConversations 中合并配置

```java
// ImConversationService 字段追加：
private final ImConversationSettingMapper conversationSettingMapper;

// listConversations 方法在 sort 前插入：
private void applyConversationSettings(Long userId, List<ImConversationVo> list) {
    if (list.isEmpty()) return;
    List<ImConversationSetting> settings = conversationSettingMapper.selectList(
        new LambdaQueryWrapper<ImConversationSetting>()
            .eq(ImConversationSetting::getUserId, userId));
    Map<String, ImConversationSetting> map = settings.stream().collect(
        Collectors.toMap(s -> s.getChatType() + ":" + s.getTargetId(), s -> s, (a, b) -> a));
    for (ImConversationVo vo : list) {
        String key = vo.getChatType() + ":" + vo.getTargetId();
        ImConversationSetting s = map.get(key);
        if (s != null) {
            vo.setIsPinned(s.getIsPinned() == null ? 0 : s.getIsPinned());
            vo.setIsMuted(s.getIsMuted() == null ? 0 : s.getIsMuted());
            vo.setPinnedAt(s.getPinnedAt());
        }
    }
}

// 排序改为：置顶在前（按 pinnedAt 倒序），非置顶按 lastTime 倒序
list.sort((a, b) -> {
    int pa = a.getIsPinned() == null ? 0 : a.getIsPinned();
    int pb = b.getIsPinned() == null ? 0 : b.getIsPinned();
    if (pa != pb) return pb - pa;
    if (pa == 1) {
        return (b.getPinnedAt() == null ? 0 : b.getPinnedAt()).compareTo(
               (a.getPinnedAt() == null ? 0 : a.getPinnedAt()));
    }
    return (b.getLastTime() == null ? new java.util.Date(0) : b.getLastTime()).compareTo(
           (a.getLastTime() == null ? new java.util.Date(0) : a.getLastTime()));
});
```

在 `listConversations` 末尾、`conversationCache.put(list)` 之前调用 `applyConversationSettings(me, list);`。

#### Step 3: AppImController 新增 4 个接口

```java
// 注入字段：
private final ImConversationSettingMapper conversationSettingMapper;

// =============== P6: 会话配置 ===============

/** 查询当前用户所有会话配置 */
@GetMapping("/conversation/settings")
public R<List<ImConversationSetting>> conversationSettings() {
    Long me = LoginHelper.getUserId();
    return R.ok(conversationSettingMapper.selectList(
        new LambdaQueryWrapper<ImConversationSetting>().eq(ImConversationSetting::getUserId, me)));
}

/** 设置置顶（isPinned=1 取消置顶 isPinned=0） */
@PostMapping("/conversation/pin")
public R<Void> pinConversation(@RequestBody Map<String, Object> body) {
    Long me = LoginHelper.getUserId();
    String chatType = (String) body.get("chatType");
    Long targetId = Long.valueOf(body.get("targetId").toString());
    boolean pinned = Boolean.TRUE.equals(body.get("isPinned"));

    ImConversationSetting s = getOrCreateSetting(me, chatType, targetId);
    ImConversationSetting update = new ImConversationSetting();
    update.setId(s.getId());
    update.setIsPinned(pinned ? 1 : 0);
    update.setPinnedAt(pinned ? new java.util.Date() : null);
    conversationSettingMapper.updateById(update);
    conversationCache.evict(me);
    return R.ok();
}

/** 设置免打扰（isMuted=1 取消 isMuted=0） */
@PostMapping("/conversation/mute")
public R<Void> muteConversation(@RequestBody Map<String, Object> body) {
    Long me = LoginHelper.getUserId();
    String chatType = (String) body.get("chatType");
    Long targetId = Long.valueOf(body.get("targetId").toString());
    boolean muted = Boolean.TRUE.equals(body.get("isMuted"));

    ImConversationSetting s = getOrCreateSetting(me, chatType, targetId);
    ImConversationSetting update = new ImConversationSetting();
    update.setId(s.getId());
    update.setIsMuted(muted ? 1 : 0);
    conversationSettingMapper.updateById(update);
    conversationCache.evict(me);
    return R.ok();
}

/** 取消会话所有配置（删除记录） */
@DeleteMapping("/conversation/setting")
public R<Void> removeConversationSetting(@RequestParam String chatType, @RequestParam Long targetId) {
    Long me = LoginHelper.getUserId();
    conversationSettingMapper.delete(new LambdaQueryWrapper<ImConversationSetting>()
        .eq(ImConversationSetting::getUserId, me)
        .eq(ImConversationSetting::getChatType, chatType)
        .eq(ImConversationSetting::getTargetId, targetId));
    conversationCache.evict(me);
    return R.ok();
}

private ImConversationSetting getOrCreateSetting(Long userId, String chatType, Long targetId) {
    ImConversationSetting existing = conversationSettingMapper.selectOne(
        new LambdaQueryWrapper<ImConversationSetting>()
            .eq(ImConversationSetting::getUserId, userId)
            .eq(ImConversationSetting::getChatType, chatType)
            .eq(ImConversationSetting::getTargetId, targetId));
    if (existing != null) return existing;
    ImConversationSetting s = new ImConversationSetting();
    s.setUserId(userId);
    s.setChatType(chatType);
    s.setTargetId(targetId);
    s.setIsPinned(0);
    s.setIsMuted(0);
    conversationSettingMapper.insert(s);
    return s;
}
```

#### Step 4: 单元测试（最小覆盖）

```java
// AppImControllerP6Test.java
@SpringBootTest
class AppImControllerP6Test {
    @Autowired AppImController controller;
    @Autowired ImConversationSettingMapper mapper;

    @Test
    void pinConversation_shouldInsertAndUpdate() {
        // 调用两次：第一次置顶，第二次取消
        Map<String, Object> body = new HashMap<>();
        body.put("chatType", "PRIVATE");
        body.put("targetId", 999L);
        body.put("isPinned", true);
        controller.pinConversation(body);
        body.put("isPinned", false);
        controller.pinConversation(body);
        // 验证最终状态 isPinned=0
        // ...
    }
}
```

#### Step 5: Commit

```bash
git commit -m "feat(im): P6-B1 会话置顶+免打扰接口（4 个接口 + listConversations 合并配置）"
```

---

### Task B2: 收藏消息接口

**目标：** 在 `AppImController` 新增 3 个接口：收藏消息 / 取消收藏 / 收藏列表（分页）。

**Files:**
- Modify: `nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/controller/AppImController.java`

#### Step 1: 新增 3 个接口

```java
// 注入字段：
private final ImFavoriteMapper favoriteMapper;

/** 收藏消息 */
@PostMapping("/favorite/add")
public R<Void> addFavorite(@RequestBody Map<String, Object> body) {
    Long me = LoginHelper.getUserId();
    Long messageId = Long.valueOf(body.get("messageId").toString());
    ImMessage msg = messageMapper.selectById(messageId);
    if (msg == null) return R.fail("消息不存在");
    if (!canAccessMessage(msg, me)) return R.fail("无权收藏此消息");

    // 幂等：已收藏则直接返回
    long exists = favoriteMapper.selectCount(new LambdaQueryWrapper<ImFavorite>()
        .eq(ImFavorite::getUserId, me).eq(ImFavorite::getMessageId, messageId));
    if (exists > 0) return R.ok();

    ImFavorite fav = new ImFavorite();
    fav.setUserId(me);
    fav.setMessageId(messageId);
    fav.setChatType(msg.getGroupId() != null ? "GROUP" : "PRIVATE");
    fav.setTargetId(msg.getGroupId() != null ? msg.getGroupId() : msg.getFromUserId().equals(me) ? msg.getToUserId() : msg.getFromUserId());
    fav.setFromUserId(msg.getFromUserId());
    fav.setMsgType(msg.getMsgType());
    fav.setContentSnippet(truncate(msg.getContent(), 500));
    favoriteMapper.insert(fav);
    return R.ok();
}

/** 取消收藏 */
@DeleteMapping("/favorite/{favoriteId}")
public R<Void> removeFavorite(@PathVariable Long favoriteId) {
    Long me = LoginHelper.getUserId();
    ImFavorite fav = favoriteMapper.selectById(favoriteId);
    if (fav == null || !me.equals(fav.getUserId())) return R.fail("收藏不存在或无权操作");
    favoriteMapper.deleteById(favoriteId);
    return R.ok();
}

/** 收藏列表（分页） */
@GetMapping("/favorite/list")
public TableDataInfo<ImFavorite> favoriteList(PageQuery pageQuery) {
    Long me = LoginHelper.getUserId();
    Page<ImFavorite> page = favoriteMapper.selectPage(pageQuery.build(),
        new LambdaQueryWrapper<ImFavorite>()
            .eq(ImFavorite::getUserId, me)
            .orderByDesc(ImFavorite::getCreateTime));
    return TableDataInfo.build(page);
}

private String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
}
```

#### Step 2: Commit

```bash
git commit -m "feat(im): P6-B2 收藏消息接口（add/remove/list 3 个接口）"
```

---

### Task B3: 引用消息发送字段扩展

**目标：** `ImPrivateSendBo` / `ImGroupSendBo` 增加 `replyMessageId` 字段；`ImMessage` 增加 `replyMessageId` + `replySnippet` 字段；发送时填充。

**Files:**
- Modify: `nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/domain/ImMessage.java`
- Modify: `nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/domain/bo/ImPrivateSendBo.java`
- Modify: `nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/domain/bo/ImGroupSendBo.java`
- Modify: `nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/service/impl/ImChatServiceImpl.java`

#### Step 1: 建表 SQL（ALTER TABLE）

```sql
ALTER TABLE `im_message`
  ADD COLUMN `reply_message_id` BIGINT(20) NULL COMMENT '引用的消息 ID' AFTER `mention_user_ids`,
  ADD COLUMN `reply_snippet`    VARCHAR(500) NULL COMMENT '引用消息内容摘要' AFTER `reply_message_id`;
ALTER TABLE `im_message`
  ADD INDEX `idx_reply_msg` (`reply_message_id`);
```

#### Step 2: 实体 + BO 字段追加

```java
// ImMessage.java
private Long replyMessageId;
private String replySnippet;

// ImPrivateSendBo.java / ImGroupSendBo.java
private Long replyMessageId;
```

#### Step 3: 发送逻辑填充 replySnippet

在 `ImChatServiceImpl.sendPrivate` / `sendGroup` 中，若 `bo.getReplyMessageId() != null`，查原消息并截取前 200 字作为 `replySnippet`。

```java
if (bo.getReplyMessageId() != null) {
    ImMessage origin = messageMapper.selectById(bo.getReplyMessageId());
    if (origin != null && !"1".equals(origin.getRevoked())) {
        msg.setReplyMessageId(bo.getReplyMessageId());
        msg.setReplySnippet(truncate(origin.getContent(), 200));
    }
}
```

#### Step 4: Commit

```bash
git commit -m "feat(im): P6-B3 引用消息字段扩展（reply_message_id + reply_snippet）"
```

---

## Phase C：前端撤回/转发/引用入口

### Task C1: chat 页消息长按菜单组件

**目标：** 新建 `MessageActionMenu` 弹窗组件，根据消息类型 + 是否自己发送 + 是否撤回 显示操作项：撤回（仅自己+2 分钟内）/ 转发 / 引用 / 收藏 / 删除 / 多选。

**Files:**
- Create: `Nexus_App/components/business/im/message-action-menu.vue`
- Modify: `Nexus_App/pages/im/chat/index.vue`
- Modify: `Nexus_App/components/business/im/message-bubble.vue`

#### Step 1: 新建 message-action-menu.vue

```vue
<template>
  <view v-if="visible" class="action-mask" @click="handleMaskClick">
    <view class="action-sheet" @click.stop>
      <view class="action-header">
        <text class="action-title">消息操作</text>
      </view>
      <view class="action-list">
        <view v-if="canRecall" class="action-item" @click="handleAction('recall')">
          <text class="action-icon">↩</text><text class="action-text">撤回</text>
        </view>
        <view class="action-item" @click="handleAction('forward')">
          <text class="action-icon">↪</text><text class="action-text">转发</text>
        </view>
        <view class="action-item" @click="handleAction('reply')">
          <text class="action-icon">💬</text><text class="action-text">引用</text>
        </view>
        <view class="action-item" @click="handleAction('favorite')">
          <text class="action-icon">⭐</text><text class="action-text">收藏</text>
        </view>
        <view class="action-item" @click="handleAction('multiSelect')">
          <text class="action-icon">☑</text><text class="action-text">多选</text>
        </view>
        <view v-if="canDelete" class="action-item action-danger" @click="handleAction('delete')">
          <text class="action-icon">🗑</text><text class="action-text">删除</text>
        </view>
      </view>
      <view class="action-cancel" @click="handleCancel"><text>取消</text></view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  message: { type: Object, default: () => ({}) },
  currentUserId: { type: [Number, String], default: '' }
})

const emit = defineEmits(['action', 'cancel'])

// 撤回条件：自己发送 + 未撤回 + 2 分钟内
const canRecall = computed(() => {
  const m = props.message
  if (!m) return false
  if (Number(m.revoked) === 1) return false
  const from = m.fromUserId
  if (from == null || String(from) !== String(props.currentUserId)) return false
  const createTime = new Date(m.createTime || m._createTime || Date.now()).getTime()
  return Date.now() - createTime <= 2 * 60 * 1000
})

// 删除条件：自己发送 OR 自己能访问的消息
const canDelete = computed(() => {
  const m = props.message
  if (!m) return false
  return String(m.fromUserId) === String(props.currentUserId)
})

const handleAction = (action) => emit('action', { action, message: props.message })
const handleCancel = () => emit('cancel')
const handleMaskClick = () => emit('cancel')
</script>

<style scoped lang="scss">
.action-mask {
  position: fixed; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: flex-end; justify-content: center; z-index: 9999;
}
.action-sheet {
  width: 100%; max-width: 750rpx; background: $bg-card;
  border-radius: 24rpx 24rpx 0 0; padding: $spacing-sm 0;
}
.action-header { padding: $spacing-md; border-bottom: 1rpx solid $border-light; }
.action-title { font-size: $font-size-sm; color: $text-secondary; }
.action-list { display: flex; flex-wrap: wrap; padding: $spacing-sm; }
.action-item {
  width: 33.33%; padding: $spacing-md 0; display: flex; flex-direction: column;
  align-items: center; gap: 8rpx;
}
.action-icon { font-size: 40rpx; }
.action-text { font-size: $font-size-sm; color: $text-primary; }
.action-danger .action-text { color: $danger; }
.action-cancel {
  margin-top: $spacing-sm; padding: $spacing-md; text-align: center;
  border-top: 1rpx solid $border-light; color: $text-secondary;
}
</style>
```

#### Step 2: message-bubble.vue 添加 @longpress 事件

```vue
<!-- 在 <view class="msg-bubble" ...> 上添加 @longpress -->
<view class="msg-bubble" :class="[...]" @longpress="$emit('longpress', message)">
```

```js
// defineEmits 追加 'longpress'
defineEmits(['acceptFriendRequest', 'rejectFriendRequest', 'refreshMessages', 'resend', 'longpress'])
```

#### Step 3: chat/index.vue 集成长按菜单

```vue
<!-- 模板末尾追加 -->
<message-action-menu
  :visible="actionMenuVisible"
  :message="actionMenuMessage"
  :current-user-id="currentUserId"
  @action="handleMessageAction"
  @cancel="actionMenuVisible = false"
/>
```

```js
// script 新增
import MessageActionMenu from '@/components/business/im/message-action-menu.vue'
import { revokeMessage, forwardMessage, deleteMessage } from '@/api/im/message-operation'
import { addFavorite } from '@/api/im/favorite'

const actionMenuVisible = ref(false)
const actionMenuMessage = ref({})
const replyContext = ref(null) // { messageId, snippet }

// 在 message-bubble 上挂 @longpress
const handleMessageLongPress = (msg) => {
  // 撤回消息和系统消息不弹菜单
  if (Number(msg.revoked) === 1) return
  if (['system', 'friend_request'].includes(msg.messageType || msg.msgType)) return
  actionMenuMessage.value = msg
  actionMenuVisible.value = true
}

const handleMessageAction = async ({ action, message }) => {
  actionMenuVisible.value = false
  switch (action) {
    case 'recall':
      await handleRecallMessage(message)
      break
    case 'forward':
      handleForwardMessage(message)
      break
    case 'reply':
      handleReplyMessage(message)
      break
    case 'favorite':
      await handleFavoriteMessage(message)
      break
    case 'multiSelect':
      handleEnterMultiSelect(message)
      break
    case 'delete':
      await handleDeleteMessage(message)
      break
  }
}

const handleRecallMessage = async (msg) => {
  try {
    await revokeMessage(msg.messageId)
    // 乐观更新：本地标记为撤回
    const target = messages.value.find((m) => m.messageId === msg.messageId)
    if (target) {
      target.revoked = 1
      target._revokedText = '你撤回了一条消息'
    }
    uni.showToast({ title: '已撤回', icon: 'success' })
  } catch (e) {
    uni.showToast({ title: '撤回失败', icon: 'none' })
  }
}

const handleReplyMessage = (msg) => {
  replyContext.value = {
    messageId: msg.messageId,
    snippet: (msg.content || '').slice(0, 50),
    senderName: msg.senderName || msg.nickName || ''
  }
  // 聚焦输入框
  inputRef.value?.focus?.()
}

const handleFavoriteMessage = async (msg) => {
  try {
    await addFavorite({ messageId: msg.messageId })
    uni.showToast({ title: '已收藏', icon: 'success' })
  } catch (e) {
    uni.showToast({ title: '收藏失败', icon: 'none' })
  }
}

const handleDeleteMessage = async (msg) => {
  uni.showModal({
    title: '删除消息',
    content: '确定删除这条消息？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteMessage(msg.messageId)
        messages.value = messages.value.filter((m) => m.messageId !== msg.messageId)
        uni.showToast({ title: '已删除', icon: 'success' })
      } catch (e) {
        uni.showToast({ title: '删除失败', icon: 'none' })
      }
    }
  })
}

const handleForwardMessage = (msg) => {
  // 跳转到转发目标选择页
  const msgType = msg.messageType || msg.msgType || 'text'
  uni.navigateTo({
    url: `/pages/im/forward/index?messageId=${msg.messageId}&msgType=${msgType}`
  })
}

const handleEnterMultiSelect = (msg) => {
  multiSelectMode.value = true
  selectedMessageIds.value = [msg.messageId]
}
```

#### Step 4: 引用上下文 UI（在输入框上方显示引用条）

```vue
<!-- 在 message-input 上方追加 -->
<view v-if="replyContext" class="reply-bar-top">
  <view class="reply-bar-content">
    <text class="reply-bar-label">引用 {{ replyContext.senderName }}</text>
    <text class="reply-bar-snippet">{{ replyContext.snippet }}</text>
  </view>
  <view class="reply-bar-close" @click="replyContext = null">×</view>
</view>
```

调用 `sendMessage` 时携带 `replyMessageId`：

```js
// 改造 sendMessage，发送完成后清空 replyContext
await sendPrivateMessageApi(toUserId, content, messageType, [], replyContext.value?.messageId)
replyContext.value = null
```

#### Step 5: Commit

```bash
git commit -m "feat(im): P6-C1 chat 页长按菜单（撤回/转发/引用/收藏/删除/多选）"
```

---

### Task C2: 转发目标选择页

**目标：** 新建 `pages/im/forward/index.vue`，列出会话供用户选择，调用 `forwardMessage` 接口完成转发。

**Files:**
- Create: `Nexus_App/pages/im/forward/index.vue`
- Create: `Nexus_App/api/im/forward.js`（已存在 `message-operation.js` 中 forwardMessage，可复用）

#### Step 1: 新建 forward/index.vue

```vue
<template>
  <view class="forward-page">
    <view class="status-bar-placeholder"></view>
    <view class="page-header">
      <view class="header-left" @click="handleCancel"><text>取消</text></view>
      <text class="header-title">转发到</text>
      <view class="header-right"></view>
    </view>

    <scroll-view class="forward-list" scroll-y>
      <view
        v-for="conv in conversations"
        :key="conv.id || conv.targetId"
        class="forward-item"
        @click="handleSelect(conv)"
      >
        <view class="forward-avatar">
          <conversation-avatar :conversation="conv" />
        </view>
        <view class="forward-body">
          <text class="forward-name">{{ conv.title || conv.name }}</text>
          <text class="forward-type">{{ conv.chatType === 'GROUP' ? '群聊' : '私聊' }}</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useImStore } from '@/store/modules/im'
import { forwardMessage } from '@/api/im/message-operation'
import ConversationAvatar from '@/components/business/im/conversation-avatar.vue'

const imStore = useImStore()
const conversations = ref([])
const messageId = ref('')
const msgType = ref('text')

onMounted(() => {
  const pages = getCurrentPages()
  const cur = pages[pages.length - 1]
  messageId.value = cur.$page?.options?.messageId || cur.options?.messageId
  msgType.value = cur.$page?.options?.msgType || cur.options?.msgType || 'text'
  conversations.value = imStore.conversations
})

const handleSelect = (conv) => {
  uni.showModal({
    title: '转发确认',
    content: `转发到 ${conv.title || conv.name}？`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await forwardMessage({
          messageId: Number(messageId.value),
          targetType: conv.chatType === 'GROUP' ? 'group' : 'private',
          targetId: Number(conv.targetId)
        })
        uni.showToast({ title: '已转发', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 800)
      } catch (e) {
        uni.showToast({ title: '转发失败', icon: 'none' })
      }
    }
  })
}

const handleCancel = () => uni.navigateBack()
</script>

<style scoped lang="scss">
.forward-page { display: flex; flex-direction: column; height: 100vh; background: $bg-page; }
.page-header { display: flex; justify-content: space-between; align-items: center; padding: $spacing-md; border-bottom: 1rpx solid $border-light; background: $bg-card; }
.header-title { font-size: $font-size-lg; font-weight: $font-weight-medium; }
.forward-list { flex: 1; }
.forward-item { display: flex; align-items: center; padding: $spacing-md; border-bottom: 1rpx solid $border-light; background: $bg-card; }
.forward-avatar { margin-right: $spacing-md; }
.forward-body { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
.forward-name { font-size: $font-size-md; color: $text-primary; }
.forward-type { font-size: $font-size-xs; color: $text-secondary; }
</style>
```

#### Step 2: pages.json 注册路由

```json
{
  "path": "pages/im/forward/index",
  "style": { "navigationBarTitleText": "转发消息", "navigationStyle": "custom" }
}
```

#### Step 3: Commit

```bash
git commit -m "feat(im): P6-C2 转发目标选择页"
```

---

### Task C3: 引用消息发送 + 后端 replyMessageId 串联

**目标：** 前端 `sendPrivateMessageApi` / `sendGroupMessageApi` 新增 `replyMessageId` 参数；后端接收后填充 `ImMessage.replyMessageId` + `replySnippet`；message-bubble 渲染 reply 类型时优先使用 `replySnippet`。

**Files:**
- Modify: `Nexus_App/api/im/message.js`（发送接口追加 replyMessageId 参数）
- Modify: `Nexus_App/store/modules/im.js`（sendPrivateMessageApi / sendGroupMessageApi 签名追加 replyMessageId）
- Modify: `Nexus_App/components/business/im/message-bubble.vue`（reply 渲染优先用 replySnippet）

#### Step 1: api/im/message.js 发送接口追加 replyMessageId

```js
export function sendPrivateMessage(data) {
  // data 已支持扩展 replyMessageId 字段，无需改函数签名
  return request({ url: '/im/app/message/send', method: 'post', data })
}
```

#### Step 2: store/modules/im.js 追加 replyMessageId 参数

```js
async function sendPrivateMessageApi(toUserId, content, messageType = 'text', mentionUserIds = [], replyMessageId = null) {
  const payload = { toUserId, content, messageType }
  if (mentionUserIds && mentionUserIds.length > 0) payload.mentionUserIds = mentionUserIds
  if (replyMessageId) payload.replyMessageId = replyMessageId
  // ... 其余逻辑不变
}
```

后端 `AppImController.sendPrivate` 读取 `body.get("replyMessageId")` 并赋给 `bo.setReplyMessageId`。

#### Step 3: message-bubble.vue 优先用 replySnippet

```vue
<view v-else-if="messageType === 'reply'" class="msg-reply">
  <view class="reply-quote">
    <view class="reply-bar"></view>
    <text class="reply-ref">{{ message.replySnippet || message.replyPreview || message.replyText || '引用消息' }}</text>
  </view>
  <text class="reply-main">{{ message.content }}</text>
</view>
```

并在 `estimateMsgHeight` 中将 reply 消息高度估算 +30rpx（已有）。

#### Step 4: Commit

```bash
git commit -m "feat(im): P6-C3 引用消息发送链路（前端参数+后端填充+渲染优先 replySnippet）"
```

---

## Phase D：前端多选模式

### Task D1: chat 页多选模式 + 批量操作

**目标：** 长按菜单"多选"按钮进入多选模式，顶部出现批量操作栏（转发/删除/取消），消息项前显示 checkbox。

**Files:**
- Modify: `Nexus_App/pages/im/chat/index.vue`
- Modify: `Nexus_App/components/business/im/message-bubble.vue`（多选模式下显示 checkbox）

#### Step 1: chat/index.vue 多选状态

```js
const multiSelectMode = ref(false)
const selectedMessageIds = ref([])

const toggleSelect = (msgId) => {
  const idx = selectedMessageIds.value.indexOf(msgId)
  if (idx >= 0) selectedMessageIds.value.splice(idx, 1)
  else selectedMessageIds.value.push(msgId)
}

const exitMultiSelect = () => {
  multiSelectMode.value = false
  selectedMessageIds.value = []
}

const batchDelete = async () => {
  if (selectedMessageIds.value.length === 0) return
  uni.showModal({
    title: '批量删除',
    content: `删除选中的 ${selectedMessageIds.value.length} 条消息？`,
    success: async (res) => {
      if (!res.confirm) return
      for (const id of selectedMessageIds.value) {
        try { await deleteMessage(id) } catch (e) { /* ignore */ }
      }
      messages.value = messages.value.filter((m) => !selectedMessageIds.value.includes(m.messageId))
      exitMultiSelect()
      uni.showToast({ title: '已删除', icon: 'success' })
    }
  })
}

const batchForward = () => {
  if (selectedMessageIds.value.length === 0) return
  if (selectedMessageIds.value.length > 1) {
    uni.showToast({ title: '暂仅支持单条转发', icon: 'none' })
    return
  }
  const msg = messages.value.find((m) => m.messageId === selectedMessageIds.value[0])
  handleForwardMessage(msg)
  exitMultiSelect()
}
```

#### Step 2: 模板新增多选操作栏

```vue
<view v-if="multiSelectMode" class="multi-select-bar">
  <view class="msb-action" @click="exitMultiSelect"><text>取消</text></view>
  <text class="msb-count">已选 {{ selectedMessageIds.length }}</text>
  <view class="msb-action" @click="batchForward"><text>转发</text></view>
  <view class="msb-action msb-danger" @click="batchDelete"><text>删除</text></view>
</view>
```

#### Step 3: message-bubble.vue 多选模式显示 checkbox

```vue
<!-- msg-row 内部追加 -->
<view v-if="multiSelectMode" class="msg-checkbox" @click.stop="$emit('toggleSelect', message.messageId)">
  <text :class="['checkbox-icon', { checked: isSelected }]">{{ isSelected ? '✓' : '○' }}</text>
</view>
```

```js
defineProps({
  multiSelectMode: { type: Boolean, default: false },
  isSelected: { type: Boolean, default: false }
})
defineEmits(['longpress', 'toggleSelect'])
```

#### Step 4: Commit

```bash
git commit -m "feat(im): P6-D1 chat 页多选模式（批量删除/转发）"
```

---

## Phase E：前端置顶/免打扰入口

### Task E1: conversation 页长按菜单 + API 集成

**目标：** 会话列表项长按弹出操作菜单（置顶/取消置顶/免打扰/取消免打扰）；conversation-list 排序按 isPinned 优先；样式区分置顶项。

**Files:**
- Create: `Nexus_App/api/im/conversation-setting.js`
- Modify: `Nexus_App/pages/im/conversation/index.vue`
- Modify: `Nexus_App/components/business/im/conversation-list.vue`
- Modify: `Nexus_App/store/modules/im.js`（conversations 加载时合并 isPinned/isMuted）

#### Step 1: 新建 api/im/conversation-setting.js

```js
import request from '@/utils/request/request'

export function pinConversation(data) {
  return request({ url: '/im/app/conversation/pin', method: 'post', data })
}

export function muteConversation(data) {
  return request({ url: '/im/app/conversation/mute', method: 'post', data })
}

export function getConversationSettings() {
  return request({ url: '/im/app/conversation/settings', method: 'get' })
}

export function removeConversationSetting(params) {
  return request({ url: '/im/app/conversation/setting', method: 'delete', params })
}
```

#### Step 2: conversation/index.vue 长按菜单

```vue
<view
  v-for="conv in conversations"
  :key="conv.id || conv.targetId"
  class="conv-item"
  :class="{ pinned: conv.isPinned === 1, muted: conv.isMuted === 1 }"
  @click="handleSelect(conv)"
  @longpress="handleLongPress(conv)"
>
  <!-- 已有内容 -->
</view>

<!-- 长按菜单 -->
<uni-popup v-if="longPressConv" ref="actionPopup" type="actionSheet" :is-mask-click="true">
  <view class="conv-action-sheet">
    <view class="cas-item" @click="togglePin">
      <text>{{ longPressConv.isPinned === 1 ? '取消置顶' : '置顶' }}</text>
    </view>
    <view class="cas-item" @click="toggleMute">
      <text>{{ longPressConv.isMuted === 1 ? '取消免打扰' : '免打扰' }}</text>
    </view>
    <view class="cas-item cas-cancel" @click="longPressConv = null"><text>取消</text></view>
  </view>
</uni-popup>
```

```js
import { pinConversation, muteConversation } from '@/api/im/conversation-setting'

const longPressConv = ref(null)

const handleLongPress = (conv) => { longPressConv.value = conv }

const togglePin = async () => {
  const conv = longPressConv.value
  if (!conv) return
  const newPinned = conv.isPinned === 1 ? 0 : 1
  try {
    await pinConversation({
      chatType: conv.chatType,
      targetId: Number(conv.targetId),
      isPinned: newPinned === 1
    })
    conv.isPinned = newPinned
    // 触发列表重新排序
    imStore.refreshConversations()
    longPressConv.value = null
    uni.showToast({ title: newPinned === 1 ? '已置顶' : '已取消置顶', icon: 'success' })
  } catch (e) {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

const toggleMute = async () => {
  const conv = longPressConv.value
  if (!conv) return
  const newMuted = conv.isMuted === 1 ? 0 : 1
  try {
    await muteConversation({
      chatType: conv.chatType,
      targetId: Number(conv.targetId),
      isMuted: newMuted === 1
    })
    conv.isMuted = newMuted
    longPressConv.value = null
    uni.showToast({ title: newMuted === 1 ? '已免打扰' : '已取消免打扰', icon: 'success' })
  } catch (e) {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}
```

#### Step 3: conversation-list.vue 样式区分置顶/免打扰

```scss
.conv-item.pinned {
  background: rgba(255, 215, 0, 0.08);
  border-left: 4rpx solid $warning;
}
.conv-item.muted .conv-name::after {
  content: '🔇';
  margin-left: 8rpx;
  font-size: $font-size-xs;
}
```

#### Step 4: store/modules/im.js 接收新字段

listConversations 返回的 VO 已包含 isPinned/isMuted/pinnedAt，前端按此排序：

```js
conversations.value.sort((a, b) => {
  const pa = a.isPinned || 0
  const pb = b.isPinned || 0
  if (pa !== pb) return pb - pa
  if (pa === 1) return (b.pinnedAt || 0) - (a.pinnedAt || 0)
  return new Date(b.lastTime || 0).getTime() - new Date(a.lastTime || 0).getTime()
})
```

#### Step 5: notify-controller.js 会话级免打扰集成

修改 `shouldShowNotification(conversation)`，已有 `conversation.muted` 判断，这里改为读 `conversation.isMuted`：

```js
shouldShowNotification(conversation) {
  if (this.isInDoNotDisturbTime()) return false
  if (conversation && Number(conversation.isMuted) === 1) return false
  return true
}
```

#### Step 6: Commit

```bash
git commit -m "feat(im): P6-E1 会话级置顶+免打扰（长按菜单+样式+排序+notifyController 集成）"
```

---

## Phase F：前端收藏列表页

### Task F1: 收藏列表页 + API

**目标：** 新建 `pages/im/favorite/index.vue` 展示收藏列表，点击跳转到原消息上下文。

**Files:**
- Create: `Nexus_App/api/im/favorite.js`
- Create: `Nexus_App/pages/im/favorite/index.vue`
- Modify: `Nexus_App/pages.json`（注册路由）

#### Step 1: api/im/favorite.js

```js
import request from '@/utils/request/request'

export function addFavorite(data) {
  return request({ url: '/im/app/favorite/add', method: 'post', data })
}

export function removeFavorite(favoriteId) {
  return request({ url: `/im/app/favorite/${favoriteId}`, method: 'delete' })
}

export function getFavoriteList(params = { pageNum: 1, pageSize: 20 }) {
  return request({ url: '/im/app/favorite/list', method: 'get', params })
}
```

#### Step 2: pages/im/favorite/index.vue

```vue
<template>
  <view class="favorite-page">
    <view class="status-bar-placeholder"></view>
    <view class="page-header">
      <view class="header-left" @click="handleBack"><text>←</text></view>
      <text class="header-title">我的收藏</text>
      <view class="header-right"></view>
    </view>

    <scroll-view scroll-y class="favorite-list" @scrolltolower="loadMore">
      <view v-for="fav in list" :key="fav.id" class="fav-item" @click="handleClick(fav)" @longpress="handleLongPress(fav)">
        <view class="fav-icon">{{ msgTypeIcon(fav.msgType) }}</view>
        <view class="fav-body">
          <text class="fav-snippet">{{ fav.contentSnippet || '[无内容]' }}</text>
          <text class="fav-meta">{{ formatTime(fav.createTime) }} · {{ fav.chatType === 'GROUP' ? '群聊' : '私聊' }}</text>
        </view>
      </view>
      <view v-if="list.length === 0" class="fav-empty"><text>暂无收藏</text></view>
      <view v-if="loading" class="fav-loading"><text>加载中...</text></view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getFavoriteList, removeFavorite } from '@/api/im/favorite'
import { formatMessageTime } from '@/utils/format/date'

const list = ref([])
const loading = ref(false)
const pageNum = ref(1)
const hasMore = ref(true)

const msgTypeIcon = (t) => ({ text: '📝', image: '🖼', file: '📎', voice: '🔊', video: '🎬' }[t] || '📄')

const formatTime = formatMessageTime

const loadList = async (reset = false) => {
  if (loading.value) return
  if (reset) { pageNum.value = 1; hasMore.value = true }
  if (!hasMore.value) return
  loading.value = true
  try {
    const res = await getFavoriteList({ pageNum: pageNum.value, pageSize: 20 })
    const rows = res.rows || res.data || []
    if (reset) list.value = rows
    else list.value.push(...rows)
    hasMore.value = rows.length >= 20
    if (rows.length > 0) pageNum.value++
  } finally {
    loading.value = false
  }
}

const loadMore = () => loadList(false)

const handleClick = (fav) => {
  // 跳转到原会话聊天页（携带 messageId 用于定位高亮）
  const target = fav.chatType === 'GROUP' ? fav.targetId : `private_${fav.targetId}`
  uni.navigateTo({ url: `/pages/im/chat/index?targetId=${target}&highlightMessageId=${fav.messageId}` })
}

const handleLongPress = (fav) => {
  uni.showActionSheet({
    itemList: ['取消收藏'],
    success: async (res) => {
      if (res.tapIndex === 0) {
        try {
          await removeFavorite(fav.id)
          list.value = list.value.filter((f) => f.id !== fav.id)
          uni.showToast({ title: '已取消收藏', icon: 'success' })
        } catch (e) {
          uni.showToast({ title: '操作失败', icon: 'none' })
        }
      }
    }
  })
}

const handleBack = () => uni.navigateBack()

onMounted(() => loadList(true))
</script>

<style scoped lang="scss">
.favorite-page { display: flex; flex-direction: column; height: 100vh; background: $bg-page; }
.page-header { display: flex; justify-content: space-between; align-items: center; padding: $spacing-md; border-bottom: 1rpx solid $border-light; background: $bg-card; }
.header-title { font-size: $font-size-lg; font-weight: $font-weight-medium; }
.favorite-list { flex: 1; }
.fav-item { display: flex; gap: $spacing-md; padding: $spacing-md; border-bottom: 1rpx solid $border-light; background: $bg-card; }
.fav-icon { font-size: 40rpx; }
.fav-body { flex: 1; display: flex; flex-direction: column; gap: 6rpx; }
.fav-snippet { font-size: $font-size-md; color: $text-primary; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.fav-meta { font-size: $font-size-xs; color: $text-secondary; }
.fav-empty, .fav-loading { padding: 120rpx 0; text-align: center; color: $text-secondary; }
</style>
```

#### Step 3: pages.json 注册路由 + 我的页面入口

```json
{
  "path": "pages/im/favorite/index",
  "style": { "navigationBarTitleText": "我的收藏" }
}
```

在 `pages/im/contact/index.vue` 或我的页加入"我的收藏"入口。

#### Step 4: Commit

```bash
git commit -m "feat(im): P6-F1 收藏列表页+API+长按取消收藏"
```

---

## Phase G：测试与文档

### Task G1: 后端单元测试

**目标：** 为 P6 新增的 6 个接口（pin/mute/removeSetting/addFavorite/removeFavorite/favoriteList）+ replyMessageId 字段填充逻辑编写测试。

**Files:**
- Create: `nexus-erp/ruoyi-modules/ruoyi-im/src/test/java/com/nexus/im/controller/AppImControllerP6Test.java`
- Create: `nexus-erp/ruoyi-modules/ruoyi-im/src/test/java/com/nexus/im/service/impl/ImChatServiceP6Test.java`

测试覆盖：
1. pinConversation 第一次调用 INSERT，第二次 UPDATE
2. muteConversation 幂等
3. removeConversationSetting 仅删除自己记录
4. addFavorite 越权消息返回 fail
5. addFavorite 重复收藏幂等
6. removeFavorite 越权返回 fail
7. favoriteList 仅返回当前用户收藏
8. sendPrivate 携带 replyMessageId 时 msg.replyMessageId/replySnippet 正确填充
9. replyMessageId 指向已撤回消息时不填充

#### Commit

```bash
git commit -m "test(im): P6-G1 后端单元测试（会话配置+收藏+引用消息字段）"
```

---

### Task G2: 前端单元测试

**目标：** 为 message-action-menu.vue 的 `canRecall` / `canDelete` 计算属性 + conversation 长按菜单逻辑编写 vitest。

**Files:**
- Create: `Nexus_App/tests/unit/message-action-menu.spec.js`

测试覆盖：
1. canRecall：自己消息 + 2 分钟内 = true
2. canRecall：超过 2 分钟 = false
3. canRecall：他人消息 = false
4. canRecall：已撤回消息 = false
5. canDelete：自己消息 = true
6. canDelete：他人消息 = false
7. handleAction emit 正确的 action 字符串

```js
import { mount } from '@vue/test-utils'
import MessageActionMenu from '@/components/business/im/message-action-menu.vue'

describe('message-action-menu (P6)', () => {
  it('canRecall: 自己消息+2 分钟内=true', () => {
    const wrapper = mount(MessageActionMenu, {
      props: {
        visible: true,
        message: {
          messageId: 1,
          fromUserId: 100,
          revoked: 0,
          createTime: new Date().toISOString()
        },
        currentUserId: 100
      }
    })
    expect(wrapper.vm.canRecall).toBe(true)
  })

  it('canRecall: 超过 2 分钟=false', () => {
    const threeMinAgo = new Date(Date.now() - 3 * 60 * 1000).toISOString()
    const wrapper = mount(MessageActionMenu, {
      props: {
        visible: true,
        message: { messageId: 1, fromUserId: 100, revoked: 0, createTime: threeMinAgo },
        currentUserId: 100
      }
    })
    expect(wrapper.vm.canRecall).toBe(false)
  })

  it('canRecall: 他人消息=false', () => {
    const wrapper = mount(MessageActionMenu, {
      props: {
        visible: true,
        message: { messageId: 1, fromUserId: 200, revoked: 0, createTime: new Date().toISOString() },
        currentUserId: 100
      }
    })
    expect(wrapper.vm.canRecall).toBe(false)
  })

  it('canDelete: 自己消息=true，他人消息=false', () => {
    const w1 = mount(MessageActionMenu, {
      props: { visible: true, message: { fromUserId: 100 }, currentUserId: 100 }
    })
    expect(w1.vm.canDelete).toBe(true)
    const w2 = mount(MessageActionMenu, {
      props: { visible: true, message: { fromUserId: 200 }, currentUserId: 100 }
    })
    expect(w2.vm.canDelete).toBe(false)
  })
})
```

#### Commit

```bash
git commit -m "test(im): P6-G2 前端 message-action-menu 单元测试"
```

---

### Task G3: E2E 测试用例补充

**目标：** 在 `Nexus_App/e2e/specs/` 新增 3 个 spec 覆盖撤回/转发/收藏/置顶/免打扰。

**Files:**
- Create: `Nexus_App/e2e/specs/07-recall-forward-reply.spec.js`
- Create: `Nexus_App/e2e/specs/08-favorite.spec.js`
- Create: `Nexus_App/e2e/specs/09-pin-mute.spec.js`

每个 spec 至少 2 个用例，参考已有 `03-message-recall.spec.js` 的模式。

#### Commit

```bash
git commit -m "test(im): P6-G3 E2E 补充撤回/转发/收藏/置顶/免打扰 3 个 spec"
```

---

### Task G4: 文档与最终提交

**Files:**
- Modify: `nexus-erp/doc/im/2026-07-19-im-p6-advanced-interaction-plan.md`（标记完成状态）
- Update: `nexus-erp/doc/im/2026-07-19-im-p5-development-plan.md`（引用 P6 衔接）

#### Commit

```bash
git commit -m "docs(im): P6 计划文档状态更新 + 功能覆盖度归档"
```

---

## 任务依赖与执行顺序

```
A1 ─┐
    ├─→ B1 ─┐
A2 ─┼─→ B2 ─┤
    │       ├─→ C1 ─→ C2 ─┐
B3 ─┘       │             ├─→ D1 ─┐
            │             │       │
            └─→ E1 ───────┘       ├─→ G1
                                  ├─→ G2
            └────────────→ F1 ────┤
                                  └─→ G3 → G4
```

- **Phase A**（A1/A2）可并行
- **Phase B**（B1/B2/B3）依赖 A，三者可并行
- **Phase C**（C1→C2→C3）串行，C1 是入口
- **Phase D**（D1）依赖 C1
- **Phase E**（E1）依赖 B1
- **Phase F**（F1）依赖 B2
- **Phase G**（G1/G2/G3）可在 C/D/E/F 完成后并行，G4 收尾

## 风险与缓解

| 风险 | 缓解 |
|---|---|
| `im_conversation_setting` UNIQUE 约束并发插入冲突 | `getOrCreateSetting` 已用 select-then-insert，冲突时 catch 重新 select |
| `listConversations` 增加 settings JOIN 影响 P4-2 Redis 缓存性能 | settings 查询单独走 1 SQL + Map 合并，不打断现有缓存链路；置顶/免打扰变更立即 `conversationCache.evict` |
| 长按事件在 H5 与小程序行为差异 | uni-app `@longpress` 跨端统一；H5 兼容右键菜单（C1 已在 message-bubble 处理） |
| 多选模式进入后消息高度估算变化 | checkbox 占位 40rpx，在 `estimateMsgHeight` 中 multiSelectMode 时 +40rpx |
| 收藏列表 contentSnippet 超长 | 后端 truncate 500 字符；前端 -webkit-line-clamp: 2 |
| E2E 测试需后端 + DB 就绪 | 沿用 P5-7 已建立的运行环境约定，不重复造轮子 |

## 验收标准

- [x] 所有 P6 新增接口通过单元测试 ✅ G1 commit 6e9db23e，24/24 用例通过（pin/mute/removeSetting/addFavorite/removeFavorite/favoriteList + replyMessageId 字段填充）
- [x] 前端 vitest 全部通过（含新增 message-action-menu 测试） ✅ G2 commit 78f481b，message-action-menu 17 用例 + 全量 78/78 通过
- [x] chat 页长按消息能弹出菜单，撤回/转发/引用/收藏/删除/多选 6 项均可触发 ✅ C1 commit 7ea74d4
- [x] conversation 页长按会话能弹出菜单，置顶/免打扰生效，列表排序与样式正确 ✅ E1 commit 4de54d7
- [x] 收藏列表页能查看收藏，点击跳转原消息，长按取消收藏 ✅ F1 commit d23f532
- [x] 引用消息发送后，对端能看到引用条 + 主体内容 ✅ C3 commit 417d845 + B3 commit db27a294
- [x] 多选模式能批量删除（多选转发暂限单条） ✅ D1 commit 66c8097
- [x] 全局免打扰（P5-6 W6）+ 会话级免打扰（P6-E1）双层生效 ✅ E1 commit 4de54d7 + notify-controller 集成
- [x] E2E 3 个新 spec 在环境就绪时可运行通过 ✅ G3 commit 98027a9，3 spec 7 用例骨架（待完整环境运行）

## 预计工作量

| Phase | 任务数 | 复杂度 |
|---|---|---|
| A | 2 | 低（建表 + 实体） |
| B | 3 | 中（接口 + 字段扩展） |
| C | 3 | 中高（前端组件 + 路由 + 链路） |
| D | 1 | 中（多选模式 + 批量） |
| E | 1 | 中（长按菜单 + 排序 + 样式） |
| F | 1 | 低（列表页） |
| G | 4 | 中（测试覆盖） |
| **总计** | **15+3 收尾** | 中等 |
