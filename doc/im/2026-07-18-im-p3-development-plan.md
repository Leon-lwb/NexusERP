# P3 IM 模块测试覆盖开发任务

> **分支**: `feature/im-p0-stability-fix`（在 P2 commit 基础上继续）
> **日期**: 2026-07-18
> **依据**: [2026-07-17-im-p0-stability-fix-plan.md](./2026-07-17-im-p0-stability-fix-plan.md) + [2026-07-18-im-p1-development-plan.md](./2026-07-18-im-p1-development-plan.md) + [2026-07-18-im-p2-development-plan.md](./2026-07-18-im-p2-development-plan.md)
> **范围**: 为 P0/P1/P2 改造补充单元测试 + 切片测试 + 集成测试，覆盖越权修复/事务推送/幂等/批量化/TOCTOU/LRU 等关键路径，保证回归安全
> **前置**: P0 五项 + P1 七项 + P2 七项改造已完成并审查通过（nexus-erp: `19b23fd1` / Nexus_App: `5a4482f`）

---

## 一、背景与目标

### 1.1 现状

| 维度 | 后端 nexus-erp | 前端 Nexus_App |
|---|---|---|
| 已有测试代码 | ❌ `**/src/test/**/*.java` 无任何文件 | ❌ 无 test 目录 |
| 测试框架依赖 | ❌ `ruoyi-im/pom.xml` 未声明 `spring-boot-starter-test` | ❌ package.json 无 jest/vitest |
| Surefire 配置 | ✅ 根 pom 已配 `maven-surefire-plugin 3.5.3` + `@Tag` 过滤 | N/A |
| 默认打包跳过测试 | ✅ `<skipTests>true</skipTests>` | N/A |

P0/P1/P2 共 **19 项改造**（含审查修复）已合并到 `feature/im-p0-stability-fix` 分支，但**全部依赖人工回归**：
- P0-1 4 处越权修复无自动化用例，任何重构都可能重新引入越权
- P0-2 事务提交后推送的逻辑若被误改回事务内，幽灵消息问题立即复发
- P2-1 listConversations 批量化若被回退到 N+1，性能立即劣化 90%
- P2-4 addGroupMembers TOCTOU 修复若 `selectCount` 被加回，并发加群又会重复插入

### 1.2 目标

- **回归安全网**:为 P0/P1/P2 的 19 项关键改造建立自动化测试，重构时能立即发现回退
- **测试分层**:单元测试（纯 Mockito，快）+ 切片测试（@WebMvcTest/@MybatisPlusTest，中）+ 集成测试（@SpringBootTest，慢，标记 @Tag("integration") 不在默认 build 执行）
- **覆盖率基线**:IM 模块关键 Service/Controller 类行覆盖率 ≥ 70%，关键路径（越权/幂等/批量化）方法覆盖率 100%
- **CI 友好**:单元测试 < 30s 完成，可在每次 commit 触发；集成测试单独 job 触发

### 1.3 范围与边界

**纳入 P3**:
- 后端 `ruoyi-im` 模块测试基础设施搭建
- 后端 P0/P1/P2 关键路径单元测试 + 切片测试
- 后端少量集成测试（标记 @Tag，可选手动触发）

**不纳入 P3（留待 P4）**:
- 前端 Nexus_App 测试基础设施（uni-app 测试需要小程序运行时模拟器，单独评估）
- 全模块端到端测试（需要测试环境数据库 + Redis + WebSocket，P4 评估）
- 性能压测基线（需要独立压测环境，P4 评估）

---

## 二、任务总览

P3 聚焦测试覆盖，共 **10 项任务**，按风险/价值分三档实施：

| 档位 | 任务 | 风险 | 覆盖路径 | 测试类型 | 状态 |
|---|---|---|---|---|---|
| **A 高价值** | P3-1 测试基础设施搭建 | 低（新增依赖 + 目录） | 全模块基础 | 基础设施 | 待实施 |
| **A 高价值** | P3-2 P0 越权修复回归测试 | 低（纯新增测试） | P0-1 deleteMessage/forwardMessage/revokeRequest/sendFriendRequest | 单元 + 切片 | 待实施 |
| **A 高价值** | P3-3 P0 事务推送与雪花 ID 测试 | 低 | P0-2 publishToUserAfterCommit + P0-3 ImEnvelopeHelper | 单元 | 待实施 |
| **A 高价值** | P3-4 P2 TOCTOU + 批量插入幂等测试 | 低 | P2-4 addGroupMembers + P2-5 createGroup | 单元 | 待实施 |
| **B 中价值** | P3-5 P2-1 listConversations 批量化回归测试 | 中（需 mock 多 Mapper） | P2-1 6 步批量化 + selectMaps 子查询 | 单元 | 待实施 |
| **B 中价值** | P3-6 P2-2/P2-6 buildSenderMessagePage + listGroupMembers 测试 | 中 | P2-2 历史消息 + P2-6 群成员批量化 | 单元 | 待实施 |
| **B 中价值** | P3-7 P1 消息类型策略 + @提及 + 幂等测试 | 中 | P1-2 ImMessageTypeHandler + P1-4 client_msg_id + P1-5 mention | 单元 | 待实施 |
| **C 基础设施** | P3-8 P2-7 im-cache.js 前端单元测试 | 中（需引入 vitest） | P2-3 防抖 + P2-7 LRU 淘汰 | 单元（vitest） | 待实施 |
| **C 基础设施** | P3-9 集成测试样板（@Tag("integration")） | 高（需测试环境） | P0-2 完整事务 + P1-4 完整幂等 | 集成 | 待实施 |
| **C 基础设施** | P3-10 CI 集成 + 覆盖率报告 | 低（仅配置） | 全模块 | 基础设施 | 待实施 |

本文档先实施 A 档四项（P3-1/P3-2/P3-3/P3-4），B/C 档在 A 档验证稳定后单独推进。

---

## 三、A 档详细方案

### P3-1 测试基础设施搭建

**问题**: `ruoyi-im/pom.xml` 未声明 `spring-boot-starter-test`，项目无 `src/test/java` 目录，无 BaseTest 基类，无测试资源配置。

**改造方案**:

#### 3.1.1 pom.xml 增加测试依赖

`ruoyi-modules/ruoyi-im/pom.xml` 在 `<dependencies>` 末尾追加：

```xml
<!-- 测试依赖（test scope，不打入生产包） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
    <exclusions>
        <!-- 排除 JUnit Vintage，只用 JUnit 5 -->
        <exclusion>
            <groupId>org.junit.vintage</groupId>
            <artifactId>junit-vintage-engine</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-inline</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

**关键说明**:
- `spring-boot-starter-test` 已包含 JUnit 5 + Mockito + AssertJ + Spring Test + JSONassert
- `mockito-inline` 用于 mock 静态方法（如 `LoginHelper.getUserId()`、`TenantHelper`）
- 排除 `junit-vintage` 确保只用 JUnit 5

#### 3.1.2 测试目录结构

```
ruoyi-modules/ruoyi-im/src/test/
├── java/com/nexus/im/
│   ├── support/                     # BaseTest 与公共工具
│   │   ├── ImUnitTestBase.java      # 单元测试基类（Mockito extends）
│   │   └── ImTestFixture.java       # 公共 fixture（构造测试数据）
│   ├── controller/
│   │   └── AppImControllerSecurityTest.java  # P3-2 切片测试
│   ├── push/
│   │   ├── ImRealtimePushTest.java           # P3-3 事务推送
│   │   └── ImEnvelopeHelperTest.java         # P3-3 雪花 ID 序列化
│   ├── service/impl/support/
│   │   ├── ImGroupChatServiceTest.java       # P3-4 TOCTOU + 批量插入
│   │   ├── ImConversationServiceTest.java    # P3-5 listConversations 批量化
│   │   ├── ImChatSupportTest.java            # P3-6 buildSenderMessagePage
│   │   ├── ImPrivateChatServiceTest.java     # P3-7 client_msg_id 幂等
│   │   └── ImMessageLifecycleServiceTest.java # P3-7 已读回执
│   ├── msgtype/
│   │   └── ImMessageTypeRegistryTest.java    # P3-7 消息类型策略
│   └── task/
│       └── ImMessageClearTaskTest.java       # P3-7 消息清理任务
└── resources/
    └── logback-test.xml             # 测试环境日志配置（DEBUG 级别）
```

#### 3.1.3 BaseTest 基类

```java
package com.nexus.im.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * IM 单元测试基类。
 * <p>
 * 提供 LoginHelper / TenantHelper 静态 mock 的统一管理，
 * 子类通过 protectedLoginAs(userId) 设置当前登录用户。
 */
@ExtendWith(MockitoExtension.class)
public abstract class ImUnitTestBase {

    protected static final Long TENANT_ID = 1L;
    protected static final Long ME_USER_ID = 100L;
    protected static final Long PEER_USER_ID = 200L;
    protected static final Long OTHER_TENANT_USER_ID = 300L;

    protected MockedStatic<com.nexus.common.tenant.helper.TenantHelper> tenantHelperMock;
    protected MockedStatic<com.nexus.common.satoken.utils.LoginHelper> loginHelperMock;

    @BeforeEach
    void setUpBase() {
        loginHelperMock = Mockito.mockStatic(com.nexus.common.satoken.utils.LoginHelper.class);
        loginHelperMock.when(com.nexus.common.satoken.utils.LoginHelper::getUserId).thenReturn(ME_USER_ID);
        loginHelperMock.when(com.nexus.common.satoken.utils.LoginHelper::getTenantId).thenReturn(TENANT_ID);

        tenantHelperMock = Mockito.mockStatic(com.nexus.common.tenant.helper.TenantHelper.class);
        tenantHelperMock.when(com.nexus.common.tenant.helper.TenantHelper::getTenantId).thenReturn(TENANT_ID);
    }

    @AfterEach
    void tearDownBase() {
        if (loginHelperMock != null) loginHelperMock.close();
        if (tenantHelperMock != null) tenantHelperMock.close();
    }

    /** 切换当前登录用户（用于模拟其他用户视角） */
    protected void loginAs(Long userId) {
        loginHelperMock.when(com.nexus.common.satoken.utils.LoginHelper::getUserId).thenReturn(userId);
    }
}
```

#### 3.1.4 ImTestFixture 公共 fixture

```java
package com.nexus.im.support;

import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.ImGroup;
import com.nexus.im.domain.ImGroupMember;
import java.util.Date;
import java.util.List;
import java.util.stream.LongStream;

/**
 * IM 测试数据构造工具。
 * <p>
 * 集中管理测试数据构造逻辑，避免每个测试类重复 new ImMessage() + set 字段。
 */
public class ImTestFixture {

    public static ImMessage buildMessage(Long messageId, Long fromUserId, Long toUserId, Long groupId, String content) {
        ImMessage m = new ImMessage();
        m.setMessageId(messageId);
        m.setFromUserId(fromUserId);
        m.setToUserId(toUserId);
        m.setGroupId(groupId);
        m.setContent(content);
        m.setMsgType("text");
        m.setIsRead("0");
        m.setCreateTime(new Date());
        return m;
    }

    public static ImGroup buildGroup(Long groupId, String groupName, String status) {
        ImGroup g = new ImGroup();
        g.setGroupId(groupId);
        g.setGroupName(groupName);
        g.setStatus(status);
        return g;
    }

    public static List<ImGroupMember> buildGroupMembers(Long groupId, List<Long> userIds) {
        Date now = new Date();
        return userIds.stream().map(uid -> {
            ImGroupMember m = new ImGroupMember();
            m.setGroupId(groupId);
            m.setUserId(uid);
            m.setJoinTime(now);
            return m;
        }).toList();
    }

    /** 构造雪花 ID（19 位长整型，JS Number 精度边界） */
    public static Long snowflakeId(long seq) {
        return 1900000000000000000L + seq;
    }
}
```

**验证**:
- `mvn -pl ruoyi-modules/ruoyi-im -am test-compile` 通过
- `src/test/java` 目录被 Maven 识别为测试源码根
- 空测试类（`@Test void dummy() {}`）能执行

---

### P3-2 P0 越权修复回归测试

**问题**: P0-1 修复了 4 处越权漏洞（deleteMessage/forwardMessage/revokeRequest/sendFriendRequest），无自动化用例守护，任何重构都可能重新引入越权。

**改造目标**: 4 个接口 × 2 种场景（归属用户通过 / 非归属用户拒绝）= 8 个用例

**改造方案**:

#### 3.2.1 单元测试（Mockito mock service）

`AppImControllerSecurityTest.java` 使用 `@WebMvcTest` 切片测试，mock `IImChatService` 与 `SysUserNotificationService`，验证 Controller 层归属校验逻辑：

```java
@WebMvcTest(controllers = AppImController.class)
@Import({SecurityConfig.class, ImUnitTestBase.class})
class AppImControllerSecurityTest extends ImUnitTestBase {

    @Autowired private MockMvc mockMvc;
    @MockBean private IImChatService imChatService;
    @MockBean private SysUserNotificationService notificationService;
    @MockBean private ITenantEntityService tenantEntityService;

    // ── P0-1 deleteMessage 越权校验 ──

    @Test
    void deleteMessage_nonOwner_returnsFail() throws Exception {
        // 消息归属人是 PEER_USER_ID，当前登录 ME_USER_ID
        ImMessage msg = ImTestFixture.buildMessage(1L, PEER_USER_ID, PEER_USER_ID, null, "hello");
        when(imChatService.selectById(1L)).thenReturn(msg);

        mockMvc.perform(delete("/im/app/message/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.msg").value("无权删除该消息"));

        verify(imChatService, never()).deleteById(any());
    }

    @Test
    void deleteMessage_owner_returnsOk() throws Exception {
        ImMessage msg = ImTestFixture.buildMessage(1L, ME_USER_ID, PEER_USER_ID, null, "hello");
        when(imChatService.selectById(1L)).thenReturn(msg);

        mockMvc.perform(delete("/im/app/message/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(imChatService).deleteById(1L);
    }

    // ── P0-1 forwardMessage 越权校验 ──

    @Test
    void forwardMessage_nonReader_returnsFail() throws Exception {
        ImMessage origin = ImTestFixture.buildMessage(1L, PEER_USER_ID, OTHER_TENANT_USER_ID, null, "secret");
        when(imChatService.selectById(1L)).thenReturn(origin);

        ForwardBo bo = new ForwardBo();
        bo.setMessageId(1L);
        bo.setToUserId(PEER_USER_ID);

        mockMvc.perform(post("/im/app/message/forward")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(bo)))
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.msg").value("无权转发该消息"));

        verify(imChatService, never()).sendPrivate(any());
    }

    // ── P0-1 revokeRequest 越权校验 ──

    @Test
    void revokeRequest_nonOwner_returnsFail() throws Exception {
        SysUserNotification n = new SysUserNotification();
        n.setId(1L);
        n.setUserId(PEER_USER_ID);  // 归属人是别人
        when(notificationService.selectById(1L)).thenReturn(n);

        mockMvc.perform(post("/im/app/friend/request/revoke/1"))
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.msg").value("无权操作"));
    }

    // ── P0-1 sendFriendRequest 跨租户校验 ──

    @Test
    void sendFriendRequest_crossTenant_returnsFail() throws Exception {
        doThrow(new ServiceException("目标用户不在当前租户"))
            .when(tenantEntityService).requireSameTenantUser(OTHER_TENANT_USER_ID);

        FriendRequestBo bo = new FriendRequestBo();
        bo.setToUserId(OTHER_TENANT_USER_ID);

        mockMvc.perform(post("/im/app/friend/request/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(bo)))
            .andExpect(jsonPath("$.code").value(500));
    }
}
```

**关键设计**:
- `@WebMvcTest` 只加载 Controller 层，自动 mock 所有 Service，启动快（< 3s）
- `loginAs()` 切换登录用户视角，覆盖"我是消息归属人"vs"我不是"两种场景
- 断言 `R.code=500` + `msg` 文案，而非仅断言 HTTP 状态码（防止误改错误码）
- `verify(service, never())` 确保拒绝路径**未实际执行业务操作**（防止校验失败但仍写库）

**验证**:
- 8 个用例全部通过
- 故意注释掉 `deleteMessage` 的归属校验代码 → 对应用例失败（验证测试有效性）

---

### P3-3 P0 事务推送与雪花 ID 测试

**问题**:
- P0-2 `ImRealtimePush.publishToUserAfterCommit` 依赖 `TransactionSynchronizationManager`，若误改回 `publishToUser` 会重新出现幽灵消息
- P0-3 `ImEnvelopeHelper` 用 Spring `ObjectMapper` 替代 fastjson2，需验证 `BigNumberSerializer` 对 19 位长整型的序列化

**改造方案**:

#### 3.3.1 ImRealtimePushTest

```java
@ExtendWith(MockitoExtension.class)
class ImRealtimePushTest extends ImUnitTestBase {

    @Mock private WebSocketClient webSocketClient;
    @Mock private ImEnvelopeHelper envelopeHelper;
    @InjectMocks private ImRealtimePush imRealtimePush;

    @Test
    void publishToUserAfterCommit_withinTransaction_defersUntilAfterCommit() {
        // 模拟事务上下文
        TransactionSynchronizationManager.initSynchronization();
        try {
            imRealtimePush.publishToUserAfterCommit(PEER_USER_ID, Map.of("msg", "hello"));

            // 事务未提交时，不应实际推送
            verify(webSocketClient, never()).send(any(), any());

            // 模拟事务提交
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

            // 提交后应触发推送
            verify(webSocketClient).send(eq(PEER_USER_ID.toString()), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishToUserAfterCommit_transactionRolledBack_neverPushes() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            imRealtimePush.publishToUserAfterCommit(PEER_USER_ID, Map.of("msg", "hello"));

            // 模拟事务回滚（不调用 afterCommit，直接清理）
            // 期望：推送永不发生
            verify(webSocketClient, never()).send(any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishToUserAfterCommit_noTransaction_pushesImmediately() {
        // 不在事务上下文中，应立即推送
        imRealtimePush.publishToUserAfterCommit(PEER_USER_ID, Map.of("msg", "hello"));

        verify(webSocketClient).send(eq(PEER_USER_ID.toString()), any());
    }
}
```

#### 3.3.2 ImEnvelopeHelperTest

```java
@ExtendWith(MockitoExtension.class)
class ImEnvelopeHelperTest {

    private ObjectMapper objectMapper;
    private ImEnvelopeHelper envelopeHelper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // 注册 BigNumberSerializer（与生产配置一致）
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);

        envelopeHelper = new ImEnvelopeHelper(objectMapper);
    }

    @Test
    void envelope_snowflakeId_serializedAsString() throws Exception {
        Long snowflakeId = ImTestFixture.snowflakeId(123L);  // 1900000000000000123L

        String json = envelopeHelper.envelope("im_message", Map.of("messageId", snowflakeId));

        // 断言 Long 被序列化为字符串，而非 JS Number
        assertThat(json).contains("\"messageId\":\"1900000000000000123\"");
        assertThat(json).doesNotContain("\"messageId\":1900000000000000123");
        assertThat(json).doesNotContain("1.9");
    }

    @Test
    void envelope_multipleLongFields_allSerializedAsString() {
        Long fromUserId = ImTestFixture.snowflakeId(1L);
        Long toUserId = ImTestFixture.snowflakeId(2L);
        Long groupId = ImTestFixture.snowflakeId(3L);

        Map<String, Object> payload = Map.of(
            "messageId", ImTestFixture.snowflakeId(0L),
            "fromUserId", fromUserId,
            "toUserId", toUserId,
            "groupId", groupId
        );

        String json = envelopeHelper.envelope("im_message", payload);

        assertThat(json).contains("\"fromUserId\":\"" + fromUserId + "\"");
        assertThat(json).contains("\"toUserId\":\"" + toUserId + "\"");
        assertThat(json).contains("\"groupId\":\"" + groupId + "\"");
    }

    @Test
    void envelope_envelopeStructure_valid() {
        String json = envelopeHelper.envelope("im_message", Map.of("content", "hello"));

        assertThat(json).startsWith("{\"v\":1");
        assertThat(json).contains("\"type\":\"im_message\"");
        assertThat(json).contains("\"payload\":");
    }
}
```

**关键设计**:
- `TransactionSynchronizationManager.initSynchronization()` 手动模拟事务上下文，无需启动 Spring 容器
- `afterCommit` 回调触发后才 `verify` 推送发生，验证延迟语义
- 雪花 ID 序列化断言**字符串字面量**，而非数值（防止精度丢失静默发生）
- `doesNotContain("1.9")` 防御科学计数法表示

**验证**:
- 6 个用例通过
- 故意将 `publishToUserAfterCommit` 改回 `publishToUser` → 用例失败
- 故意移除 `BigNumberSerializer` → 雪花 ID 用例失败

---

### P3-4 P2 TOCTOU + 批量插入幂等测试

**问题**:
- P2-4 `addGroupMembers` 移除 `selectCount` 改为 catch `DuplicateKeyException`，若被误改回将重新引入并发竞态
- P2-5 `createGroup` 改用 `insertBatch`，若被改回 for 循环将重新出现 N 次插入

**改造方案**:

```java
@ExtendWith(MockitoExtension.class)
class ImGroupChatServiceTest extends ImUnitTestBase {

    @Mock private ImGroupMapper imGroupMapper;
    @Mock private ImGroupMemberMapper imGroupMemberMapper;
    @Mock private ImChatSupport support;
    @InjectMocks private ImGroupChatService imGroupChatService;

    // ── P2-4 addGroupMembers TOCTOU 幂等 ──

    @Test
    void addGroupMembers_duplicateUser_skipsAndReturnsCount() {
        Long groupId = 1L;
        List<Long> userIds = List.of(101L, 102L, 103L);
        // 模拟 101 和 103 已存在（唯一索引冲突）
        when(imGroupMemberMapper.insert(argThat(m -> m.getUserId().equals(101L))))
            .thenThrow(new DuplicateKeyException("uk_group_user"));
        when(imGroupMemberMapper.insert(argThat(m -> m.getUserId().equals(102L)))).thenReturn(1);
        when(imGroupMemberMapper.insert(argThat(m -> m.getUserId().equals(103L))))
            .thenThrow(new DuplicateKeyException("uk_group_user"));

        R<Void> result = imGroupChatService.addGroupMembers(groupId, userIds);

        assertThat(result.isSuccess()).isTrue();
        // 验证"新增 1 条，已存在 2 条"提示语
        assertThat(result.getMsg()).contains("新增 1 条");
        assertThat(result.getMsg()).contains("已存在 2 条");
        // 验证未抛异常（幂等）
        verify(imGroupMemberMapper, times(3)).insert(any());
    }

    @Test
    void addGroupMembers_allNew_insertsAll() {
        Long groupId = 1L;
        List<Long> userIds = List.of(101L, 102L);
        when(imGroupMemberMapper.insert(any())).thenReturn(1);

        R<Void> result = imGroupChatService.addGroupMembers(groupId, userIds);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMsg()).contains("新增 2 条");
        verify(imGroupMemberMapper, times(2)).insert(any());
    }

    @Test
    void addGroupMembers_doesNotPreSelectCount() {
        // 验证 TOCTOU 修复：不应调用 selectCount
        when(imGroupMemberMapper.insert(any())).thenReturn(1);

        imGroupChatService.addGroupMembers(1L, List.of(101L));

        verify(imGroupMemberMapper, never()).selectCount(any());
    }

    // ── P2-5 createGroup 批量插入 ──

    @Test
    void createGroup_multipleMembers_callsInsertBatchOnce() {
        ImGroupBo bo = new ImGroupBo();
        bo.setGroupName("test");
        bo.setMemberIds(List.of(101L, 102L, 103L, 104L, 105L));

        ImGroup saved = new ImGroup();
        saved.setGroupId(1L);
        when(imGroupMapper.insert(any(ImGroup.class))).thenReturn(1);
        when(imGroupMapper.selectById(1L)).thenReturn(saved);

        imGroupChatService.createGroup(bo);

        // 验证只调用一次 insertBatch（而非 N 次 insert）
        verify(imGroupMemberMapper, times(1)).insertBatch(anyList());
        verify(imGroupMemberMapper, never()).insert(any());
    }

    @Test
    void createGroup_10members_insertBatchCalledWithAllMembers() {
        ImGroupBo bo = new ImGroupBo();
        bo.setGroupName("test");
        bo.setMemberIds(LongStream.rangeClosed(101L, 110L).boxed().toList());

        when(imGroupMapper.insert(any(ImGroup.class))).thenReturn(1);
        when(imGroupMapper.selectById(any())).thenReturn(new ImGroup());

        imGroupChatService.createGroup(bo);

        ArgumentCaptor<List<ImGroupMember>> captor = ArgumentCaptor.forClass(List.class);
        verify(imGroupMemberMapper).insertBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(10);
    }
}
```

**关键设计**:
- `thenThrow(new DuplicateKeyException(...))` 模拟唯一索引冲突
- `verify(mapper, never()).selectCount(any())` **守护 TOCTOU 修复**——防止有人误加回预检查
- `verify(mapper, times(1)).insertBatch(anyList())` 守护批量插入优化，防止改回 for 循环
- `ArgumentCaptor` 捕获传入的批量集合，断言包含全部 10 个成员

**验证**:
- 5 个用例通过
- 故意在 `addGroupMembers` 前加 `selectCount` → 用例失败
- 故意把 `insertBatch` 改回 for 循环 → 用例失败

---

## 四、B 档任务概要（待 A 档验证后实施）

### P3-5 P2-1 listConversations 批量化回归测试

**目标**: 守护 P2-1 的 6 步批量化逻辑，防止回退到 N+1。

**关键用例**:
- 10 群 + 10 私聊场景下，断言每个 Mapper 调用次数 ≤ 上限
  - `imGroupMemberMapper.selectList` 调用 1 次（用户群组）
  - `imGroupMapper.selectVoByIds` 调用 1 次（批量群组信息）
  - `imMessageMapper.selectMaps` 调用 1 次（每群最大 messageId）
  - `imMessageMapper.selectList` 调用 1 次（最后消息详情）
  - `sysUserMapper.selectVoByIds` 调用 ≤ 2 次（群成员头像 + 私聊对端）
- 断言 `selectMaps` 返回的 key 用驼峰（`groupId` / `fromUserId`）读取，防止再次踩 `mapUnderscoreToCamelCase` 坑
- 断言无群组时走快速路径（`listPrivateConversations`）

**实现要点**:
```java
@Test
void listConversations_10groups10private_mapperCallsBounded() {
    // mock 10 群组成员关系
    when(imGroupMemberMapper.selectList(any())).thenReturn(buildMemberships(10));
    when(imGroupMapper.selectVoByIds(any())).thenReturn(buildGroups(10));
    // mock selectMaps 返回驼峰 key
    Map<String, Object> maxIdRow = new HashMap<>();
    maxIdRow.put("maxId", 1000L);
    when(imMessageMapper.selectMaps(any())).thenReturn(List.of(maxIdRow));
    // ... 其他 mock

    List<ImConversationVo> result = imConversationService.listConversations();

    // 守护 N+1 修复
    verify(imGroupMapper, atMost(1)).selectVoByIds(any());
    verify(sysUserMapper, atMost(2)).selectVoByIds(any());
    verify(imMessageMapper, atMost(2)).selectMaps(any());
    assertThat(result).hasSize(20);
}

@Test
void listConversations_selectMaps_readsCamelCaseKey() {
    // 守护 P2-1 审查修复：mapUnderscoreToCamelCase=true 导致 key 为驼峰
    Map<String, Object> unreadRow = new HashMap<>();
    unreadRow.put("groupId", 1L);  // 驼峰，非 group_id
    unreadRow.put("cnt", 5L);
    when(imMessageMapper.selectMaps(any())).thenReturn(List.of(unreadRow));

    List<ImConversationVo> result = imConversationService.listConversations();

    // 断言未读数正确读取（若用 row.get("group_id") 会返回 null，未读数为 0）
    assertThat(result.get(0).getUnreadCount()).isEqualTo(5L);
}
```

---

### P3-6 P2-2/P2-6 buildSenderMessagePage + listGroupMembers 测试

**目标**: 守护历史消息和群成员列表的批量化。

**关键用例**:
- `buildSenderMessagePage` 50 条消息只调用 1 次 `batchLoadUsers` + 1 次 `batchResolveAvatarUrls`
- `listGroupMembers` 50 成员只调用 1 次 `batchLoadUsers` + 1 次 `batchResolveAvatarUrls`
- 单条 `buildSenderMessageRow(ImMessage)` 兼容路径仍可工作（向后兼容）

---

### P3-7 P1 消息类型策略 + @提及 + 幂等测试

**目标**: 守护 P1-2/P1-4/P1-5 三项功能改造。

**关键用例**:
- `ImMessageTypeRegistry.require("text")` 返回 `TextMessageHandler`，未知类型抛 `ServiceException`
- `TextMessageHandler.normalize(null/空白/超长)` 抛异常
- `sendPrivate` 携带 `clientMsgId` 重复调用，第二次返回相同 `messageId`（不重新插入）
- `sendGroup` 携带 `mentionUserIds`，校验 mention 用户均为群成员（非成员抛异常）

---

## 五、C 档任务概要（待 B 档验证后实施）

### P3-8 P2-7 im-cache.js 前端单元测试

**目标**: 守护 P2-3 防抖写盘 + P2-7 LRU 淘汰。

**改造方案**:
- 在 Nexus_App 引入 `vitest`（devDependency）
- mock `@/utils/storage`（uni-app storage 在 Node 环境不可用）
- 使用 `vi.useFakeTimers()` 控制 500ms 防抖

**关键用例**:
```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'
vi.mock('@/utils/storage', () => ({
  default: {
    get: vi.fn(() => ({})),
    set: vi.fn(),
    remove: vi.fn()
  }
}))

describe('ImCache', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
  })

  it('P2-3: 连续 appendMessage 10 次只触发 1 次 storage.set', () => {
    const cache = new ImCache()
    for (let i = 0; i < 10; i++) {
      cache.appendMessage('user-1', { messageId: i, content: `msg${i}` })
    }
    // 防抖窗口内不应写盘
    expect(storage.set).not.toHaveBeenCalled()
    // 触发防抖
    vi.advanceTimersByTime(500)
    expect(storage.set).toHaveBeenCalledTimes(1)
  })

  it('P2-7: 超过 50 会话触发 LRU 淘汰', () => {
    const cache = new ImCache()
    // 填满 50 个会话
    for (let i = 0; i < 50; i++) {
      cache.saveMessage(`conv-${i}`, [{ messageId: i }])
    }
    vi.advanceTimersByTime(500)
    vi.clearAllMocks()
    // 第 51 个会话应淘汰 conv-0（最久未访问）
    cache.saveMessage('conv-50', [{ messageId: 50 }])
    vi.advanceTimersByTime(500)
    // 验证 conv-0 被从 storage 删除
    expect(storage.set).toHaveBeenCalledWith(
      'im_cache_messages',
      expect.not.objectContaining({ 'conv-0': expect.anything() })
    )
  })

  it('P2-7: getMessage 命中将会话移到末尾', () => {
    const cache = new ImCache()
    cache.saveMessage('old', [{ messageId: 1 }])
    cache.saveMessage('new', [{ messageId: 2 }])
    vi.advanceTimersByTime(500)
    // 访问 old，应移到末尾
    cache.getMessage('old')
    // 再添加 49 个会话填满
    for (let i = 0; i < 49; i++) {
      cache.saveMessage(`fill-${i}`, [{ messageId: i }])
    }
    // 下一次添加应淘汰 'new'（最久未访问），而非 'old'
    cache.saveMessage('trigger', [{ messageId: 99 }])
    // ... 断言 'new' 被淘汰，'old' 保留
  })
})
```

---

### P3-9 集成测试样板（@Tag("integration")）

**目标**: 提供 2-3 个端到端集成测试样板，验证完整事务/幂等流程。

**改造方案**:
- 使用 `@SpringBootTest` + `@Tag("integration")` 标记
- 配置 H2 内存数据库 或 Testcontainers MySQL（待评估）
- 默认 `mvn test` 不执行（根 pom `excludedGroups` 可配置），手动 `mvn test -Dgroups=integration` 触发

**关键用例**:
- `sendPrivate_withinTransaction_rollback_neverPushes`:完整事务回滚场景，断言 WebSocket 推送未发生
- `sendPrivate_duplicateClientMsgId_returnsSameMessageId`:完整幂等场景，两次调用返回相同 ID
- `createGroup_with10members_allMembersPersisted`:完整群创建场景，断言数据库有 10 条成员记录

**注意**:
- 集成测试依赖完整 Spring 上下文，启动慢（~30s）
- 不在 A/B 档范围内，仅作样板，后续按需扩展

---

### P3-10 CI 集成 + 覆盖率报告

**目标**: 让单元测试在每次 commit 自动运行，并产出覆盖率报告。

**改造方案**:
- 根 pom 增加 `jacoco-maven-plugin`
- CI 配置（GitHub Actions / Gitee Go / Jenkins）：
  ```yaml
  - name: IM Unit Tests
    run: mvn -pl ruoyi-modules/ruoyi-im -am test -DskipTests=false
  - name: Upload Coverage
    uses: codecov/codecov-action@v3
    with:
      file: ruoyi-modules/ruoyi-im/target/site/jacoco/jacoco.xml
  ```
- 覆盖率门槛：关键 Service 类行覆盖率 ≥ 70%，否则构建失败

**注意**:
- 此项依赖 CI 环境，本地可不配置
- 覆盖率门槛建议分阶段提升（初期 50%，稳定后 70%）

---

## 六、验证清单

### A 档验证
- [ ] P3-1 `mvn -pl ruoyi-modules/ruoyi-im -am test-compile` 通过
- [ ] P3-1 `src/test/java` 目录被识别为测试源码根
- [ ] P3-2 AppImControllerSecurityTest 8 个用例全部通过
- [ ] P3-3 ImRealtimePushTest 3 个用例 + ImEnvelopeHelperTest 3 个用例通过
- [ ] P3-4 ImGroupChatServiceTest 5 个用例通过
- [ ] 故意回退 P0-1 越权校验 → P3-2 对应用例失败（验证测试有效性）
- [ ] 故意回退 P2-4 TOCTOU 修复 → P3-4 对应用例失败
- [ ] `mvn -pl ruoyi-modules/ruoyi-im -am test` 全部通过，耗时 < 30s

### B/C 档验证（待 A 档完成后补充）
- [ ] P3-5 listConversations 批量化回归测试通过
- [ ] P3-6 buildSenderMessagePage + listGroupMembers 测试通过
- [ ] P3-7 消息类型策略 + 幂等 + @提及测试通过
- [ ] P3-8 Nexus_App `npm run test` 通过 im-cache 测试
- [ ] P3-9 集成测试样板 `mvn test -Dgroups=integration` 通过
- [ ] P3-10 CI 自动执行单元测试 + 覆盖率报告生成

---

## 七、提交规范

```
test(im): P3-X 简述

- 覆盖: P0-X / P1-X / P2-X 关键路径
- 用例数: N
- 依据: doc/im/2026-07-18-im-p3-development-plan.md P3-X
```

---

## 八、风险评估与回滚

### 风险点

1. **mockito-inline 与 Java 17 兼容性**: `mockito-inline` 用于 mock 静态方法（`LoginHelper`/`TenantHelper`），在 Java 17 上需配置 `--add-opens`。**缓解**: 在 `maven-surefire-plugin` 的 `argLine` 追加 `--add-opens=java.base/java.lang=ALL-UNNAMED` 等参数，或升级到 `mockito-core 5.x`（已内置 inline，无需 mockito-inline）。
2. **@WebMvcTest 加载 SecurityConfig 失败**: 若 `SecurityConfig` 依赖 `satoken` Bean，切片测试启动可能失败。**缓解**: 用 `@MockBean` 注入 sa-token 关键 Bean，或排除 SecurityConfig 只测 Controller 逻辑。
3. **selectMaps mock 复杂**: P3-5 需 mock `imMessageMapper.selectMaps(any())` 返回指定 Map，但 `QueryWrapper` 参数匹配复杂。**缓解**: 使用 `ArgumentMatchers.any()` 宽松匹配，不校验具体 SQL。
4. **前端引入 vitest 增加 devDependencies**: Nexus_App 目前零测试依赖，引入 vitest 增加 ~30MB node_modules。**缓解**: 仅作为 devDependency，不影响生产构建。
5. **集成测试依赖完整环境**: P3-9 需要数据库 + Redis + WebSocket，本地无环境无法运行。**缓解**: 标记 `@Tag("integration")`，默认不执行，CI 单独 job 触发。

### 回滚策略

- 每项测试任务独立提交，可单独 revert
- 测试代码与生产代码物理隔离（`src/test/java` vs `src/main/java`），不影响生产包
- 若 mockito-inline 兼容性问题严重，可降级为不 mock 静态方法，改用 `LoginHelper.setTestUserId()` 测试钩子（需在生产代码加测试专用 setter，标记 `@VisibleForTesting`）

---

## 九、与 P0/P1/P2 的对应关系

| P3 测试任务 | 守护的 P0/P1/P2 改造 | 关键守护点 |
|---|---|---|
| P3-2 | P0-1 越权修复 | 4 接口非归属用户返回 R.fail |
| P3-3 | P0-2 事务推送 + P0-3 雪花 ID | 事务回滚不推送 + Long 序列化为字符串 |
| P3-4 | P2-4 TOCTOU + P2-5 批量插入 | 无 selectCount + insertBatch 调用 1 次 |
| P3-5 | P2-1 listConversations 批量化 | Mapper 调用次数上限 + selectMaps 驼峰 key |
| P3-6 | P2-2 buildSenderMessagePage + P2-6 listGroupMembers | batchLoadUsers 调用 1 次 |
| P3-7 | P1-2 消息类型 + P1-4 幂等 + P1-5 @提及 | 未知类型抛异常 + 重复 clientMsgId 返回相同 ID |
| P3-8 | P2-3 防抖 + P2-7 LRU | storage.set 调用 1 次 + 50 会话上限淘汰 |
| P3-9 | P0-2 完整事务 + P1-4 完整幂等 | 端到端事务回滚 + 数据库层幂等 |
| P3-10 | 全模块 | CI 自动执行 + 覆盖率门槛 |

---

## 十、后续阶段预告（P4）

P3 完成后，P4 可考虑以下方向（待 P3 验证后选择）：

1. **前端测试基础设施扩展**: uni-app 组件测试 + vuex store 测试 + E2E（H5 模式）
2. **性能压测基线**: JMeter / wrk 压测高频接口，建立 P95/P99 延迟基线
3. **可观测性增强**: 慢 SQL 监控 + 推送延迟监控 + 缓存命中率监控
4. **安全审计深化**: 越权漏洞全量扫描 + SQL 注入检测 + XSS 防护
5. **多租户隔离验证**: 跨租户数据泄露全量测试
