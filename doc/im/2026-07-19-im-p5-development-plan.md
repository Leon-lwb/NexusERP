# P5 IM 模块稳定性、可观测性与扩展性开发任务

> **分支**: `feature/im-p5-stability`（从 `feature/im-p0-stability-fix` 的 P4 commit `ff1e7012` / Nexus_App `af915e0` 切出）
> **日期**: 2026-07-19
> **依据**: [2026-07-18-im-p3-development-plan.md](./2026-07-18-im-p3-development-plan.md) 第十章 P5 候选方向 + [2026-07-18-im-p4-development-plan.md](./2026-07-18-im-p4-development-plan.md) 第十章 P5 候选方向
> **范围**: 稳定性兜底（推送背压）+ 可观测性深化（慢 SQL/缓存命中率/压测基线）+ 安全审计 + 前端体验补齐（虚拟滚动/E2E）
> **前置**: P0/P1/P2/P3/P4 共 41 项改造已完成（nexus-erp: `ff1e7012` / Nexus_App: `af915e0`），IM 模块核心路径已建立单元测试基线（69 项用例）
> **2026-07-19 更新**: C 档（MQ 死信队列/ES 搜索/WS 集群/多端同步）价值过低不实施，仅保留 A+B 档共 7 项任务

---

## 一、背景与目标

### 1.1 现状（P4 完成后）

| 维度 | 已达成 | 仍待解决 |
|---|---|---|
| 性能 | 1000 人群 sendGroup 已异步化；listConversations Redis 缓存命中 < 50ms；私聊会话 DB 侧去重 | 缺乏压测基线，无 P95/P99 延迟数据；前端千条消息仍卡顿（虚拟滚动未落地） |
| 可靠性 | App syncToken 增量同步；pendingQueue 持久化；WS 断线 30s fallback；Web IndexedDB 缓存 | 推送异步化后 JVM 崩溃可能丢推送（无死信队列）；WS 推送无背压（慢消费者可能 OOM） |
| 可观测性 | Micrometer 指标 `im.push.success/failure/latency` + `im.message.send` 已接入 | 慢 SQL 监控未启；Redis 缓存命中率未采集；无告警阈值 |
| 安全 | 基础鉴权 + 数据权限 | 未做越权/SQL 注入/XSS 全量扫描；多租户隔离未验证 |
| 测试 | 69 项单元测试 + JaCoCo 50% 门槛 | 无 E2E 测试；前端组件测试缺失 |
| 扩展性 | 单实例 WS + SSE | 多实例部署 session 不一致（无 Redis pub/sub 广播）；LIKE 搜索限 7 天（无 ES 索引） |

### 1.2 目标

- **稳定性兜底**: WS 推送背压（每用户有界队列 + 慢消费者丢弃策略）；推送失败死信队列（可选 MQ）
- **可观测性深化**: 慢 SQL > 200ms 自动记录；Redis 缓存命中率采集；建立 P95/P99 延迟基线
- **安全合规**: 越权 / SQL 注入 / XSS 全量扫描通过；多租户隔离单元测试覆盖
- **前端体验**: 千条消息流畅滚动（虚拟滚动）；Emoji 面板秒开；图片预加载
- **扩展性预备**: WS 集群方案设计（Redis pub/sub）；ES 消息搜索方案设计（不强制实现）
- **测试**: 核心聊天流程 E2E 覆盖（发消息/撤回/已读/搜索）

### 1.3 范围与边界

**纳入 P5**:
- WS 推送背压（B8 升级版）+ 异步推送线程池统一
- 慢 SQL 监控 + Redis 缓存命中率监控
- 性能压测基线（JMeter/wrk 脚本 + 基线报告）
- 安全审计深化（越权 / SQL 注入 / XSS 全量扫描）+ 多租户隔离验证
- 前端虚拟滚动 + Emoji 分页 + 图片预加载（P4-11 全量落地）
- 前端 E2E 测试（Playwright）

**P5 评估方向（不强制实现，仅方案设计）**:
- MQ 死信队列（RabbitMQ/RocketMQ 接入评估）
- ES 消息搜索（全文检索方案设计）
- WebSocket 集群（多实例 session 一致性方案设计）
- 多端状态同步协议重构（设备标识 + 消息漫游）

**不纳入 P5**:
- 消息加密存储（按需，无明确业务诉求前不实施）

---

## 二、任务总览

P5 共 **11 项任务**，按风险/价值分三档实施：

| 档位 | 任务 | 风险 | 来源 | 端 | 状态 |
|---|---|---|---|---|---|
| **A 高价值** | P5-1 WS 推送背压 + 异步推送线程池统一 | 中（改共享推送路径） | P4-10 B8 延后 | 后端 | 待实施 |
| **A 高价值** | P5-2 慢 SQL 监控 + Redis 缓存命中率监控 | 低（纯新增） | P3 第十章 | 后端 | 待实施 |
| **A 高价值** | P5-3 性能压测基线 + P95/P99 延迟告警 | 低（独立环境） | P3+P4 第十章 | 后端+测试 | 待实施 |
| **A 高价值** | P5-4 安全审计深化（越权/SQLi/XSS） | 中（可能发现 P0 漏洞） | P3 第十章 | 全端 | 待实施 |
| **A 高价值** | P5-5 多租户隔离验证 | 低（测试为主） | P3 第十章 | 后端 | 待实施 |
| **B 中价值** | P5-6 前端虚拟滚动 + Emoji 分页 + 图片预加载 | 高（影响渲染） | P4-11 延后 | 全端 | ✅ 已完成 (commit fd6fd6b) |
| **B 中价值** | P5-7 前端 E2E 测试（Playwright） | 低（独立测试） | P4 第十章 | Web | ✅ 脚手架完成 (commit fd6fd6b，待运行环境就绪) |
| **C 评估方向** | P5-8 MQ 死信队列方案评估 | — | P4-1 风险缓解 | 后端 | 待评估 |
| **C 评估方向** | P5-9 ES 消息搜索方案评估 | — | P4-8 升级 | 后端 | 待评估 |
| **C 评估方向** | P5-10 WebSocket 集群方案评估 | — | P4 第十章 | 后端+运维 | 待评估 |
| **C 评估方向** | P5-11 多端状态同步协议评估 | — | P3+P4 第十章 | 全端 | 待评估 |

本文档先实施 A 档五项（P5-1 ~ P5-5），B 档在 A 档验证稳定后单独推进，C 档仅出评估报告。

---

## 三、A 档详细方案

### P5-1 WS 推送背压 + 异步推送线程池统一

**问题**: [ImRealtimePush.publishToUsers](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-im/src/main/java/com/nexus/im/push/ImRealtimePush.java) 当前在调用线程同步执行：
- 慢消费者（用户网络差）会阻塞发送线程，群消息扇出时累计放大
- 无队列上限，突发流量可能 OOM
- P4-1 异步化后，推送失败仅记 `im.push.failure` 计数，无重试兜底

**改造方案**:

#### 3.1.1 引入 UserPushQueue

```java
@Component
@RequiredArgsConstructor
public class UserPushQueue {
    /** 每用户队列上限（满了走丢弃策略 + 计数） */
    private static final int MAX_QUEUE_SIZE = 500;
    private final Map<Long, ArrayBlockingQueue<String>> queues = new ConcurrentHashMap<>();
    private final ImMetrics imMetrics;

    /** 入队，满了返回 false（计数 im.push.dropped） */
    public boolean offer(Long userId, String json) {
        ArrayBlockingQueue<String> queue = queues.computeIfAbsent(userId,
            k -> new ArrayBlockingQueue<>(MAX_QUEUE_SIZE));
        boolean ok = queue.offer(json);
        if (!ok) {
            imMetrics.recordPushFailure("queue_full", "dropped");
        }
        return ok;
    }

    /** 出队批量消费（WS online 时触发） */
    public List<String> drain(Long userId, int max) {
        ArrayBlockingQueue<String> queue = queues.get(userId);
        if (queue == null) return List.of();
        List<String> batch = new ArrayList<>(max);
        queue.drainTo(batch, max);
        return batch;
    }

    /** 用户下线时清理（防内存泄漏） */
    public void remove(Long userId) {
        queues.remove(userId);
    }
}
```

#### 3.1.2 异步推送线程池统一

新建 `ImPushExecutor` 配置类，替代 P4-1 在 `@Async("imGroupFanOutExecutor")` 的零散配置：

```java
@Configuration
public class ImPushExecutorConfig {
    @Bean("imPushExecutor")
    public ThreadPoolTaskExecutor imPushExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(8);
        exec.setMaxPoolSize(32);
        exec.setQueueCapacity(2000);
        exec.setKeepAliveSeconds(60);
        exec.setThreadNamePrefix("im-push-");
        // 满了由调用线程同步执行（背压传导至调用方）
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }
}
```

#### 3.1.3 ImRealtimePush 集成

`publishToUsers` 改造：
- WS online → 直接推（沿用 P4-9 路径，含指标埋点）
- WS offline → `UserPushQueue.offer`，待用户重连时 `drain` 批量补推
- 推送动作封装为 `Runnable` 提交到 `imPushExecutor`

**改造影响**:
- `ImRealtimePush.publishToUsers(userIds, json, chatType)` 内部由同步改异步
- `WebSocketUtils` 需暴露 `isOnline(userId)` 判定方法（P4 评估时跳过的共享基础设施改造）
- WS 连接建立事件监听 → 触发 `UserPushQueue.drain`

#### 3.1.4 指标扩展

`ImMetrics` 新增：
- `im.push.queue.size` (Gauge): 当前各用户队列总大小
- `im.push.dropped` (Counter, tags: type, reason="queue_full"): 队列满丢弃计数

**验证**:
- 单元测试：1000 人群发送，队列大小 ≤ 500×N（N 为离线用户数），无 OOM
- 手动测试：单用户离线状态下发送 1000 条消息，重连后 30s 内补推完成

---

### P5-2 慢 SQL 监控 + Redis 缓存命中率监控

**问题**: P4 完成后 IM 模块无慢 SQL 监控；Redis 缓存（ImConversationCache）命中率未采集，无法判断缓存配置是否合理。

#### 3.2.1 慢 SQL 监控（MyBatis 拦截器）

```java
@Intercepts({@Signature(type = StatementHandler.class, method = "query",
                         args = {Statement.class, ResultHandler.class})})
@Component
@Slf4j
public class SlowSqlInterceptor implements Interceptor {
    private static final long SLOW_THRESHOLD_MS = 200;
    private final ImMetrics imMetrics;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > SLOW_THRESHOLD_MS) {
                String sql = extractSql(invocation);
                log.warn("Slow SQL [{}ms]: {}", elapsed, sql);
                imMetrics.recordSlowSql(elapsed);
            }
        }
    }
}
```

`ImMetrics` 新增 `im.db.slow.count` (Counter) + `im.db.query.latency` (Timer)。

#### 3.2.2 Redis 缓存命中率监控

`ImConversationCache` 改造：
- `get()` 命中 → `im.cache.hit(type="conversation")` +1
- `get()` 未命中 → `im.cache.miss(type="conversation")` +1
- 命中率 = hit / (hit + miss)，由 Prometheus 计算

**验证**:
- 手动测试：连续 10 次 listConversations，`im.cache.hit` ≥ 9（首次 miss + TTL 内全 hit）
- 手动测试：构造慢 SQL（`SELECT SLEEP(0.3)`），日志包含 "Slow SQL"

---

### P5-3 性能压测基线 + P95/P99 延迟告警

**问题**: P4 完成后无量化性能数据，无法判断优化效果是否达标。

#### 3.3.1 压测脚本

新建 `doc/im/perf/` 目录：
- `jmeter/im-list-conversations.jmx`: listConversations 压测脚本（100 并发，10 轮）
- `jmeter/im-send-group.jmx`: 1000 人群 sendGroup 压测脚本
- `wrk/im-sync-token.lua`: App syncToken 增量同步 wrk 脚本
- `README.md`: 压测执行说明 + 基线数据表

#### 3.3.2 基线指标

| 接口 | P95 目标 | P99 目标 | 备注 |
|---|---|---|---|
| listConversations（缓存命中） | < 30ms | < 50ms | P4 目标 |
| listConversations（缓存未命中） | < 200ms | < 500ms | 含 6 步批量化 |
| sendGroup（1000 人群） | < 500ms | < 1000ms | P4 异步化目标 |
| sendPrivate | < 100ms | < 200ms | — |
| syncToken 增量同步 | < 150ms | < 300ms | 离线 300 条 |
| 消息搜索（7 天内） | < 500ms | < 1000ms | LIKE 查询 |

#### 3.3.3 告警阈值

`application.yml` 配置 Micrometer 警戒线（通过 Grafana 或 Alertmanager）：
- P95 > 目标 × 2 → WARN
- P95 > 目标 × 5 → CRITICAL

**验证**:
- 压测脚本可在本地执行，输出基线报告
- 基线数据写入 `doc/im/perf/README.md`

---

### P5-4 安全审计深化

**问题**: P0~P4 聚焦性能与稳定性，未做全量安全扫描。

#### 3.4.1 越权检查清单

| 检查项 | 接口 | 风险点 |
|---|---|---|
| 私聊消息读取 | `GET /im/app/message/page` | toUserId 校验：消息 from/to 必须包含当前用户 |
| 群消息读取 | `GET /im/app/message/group/page` | groupId 校验：当前用户必须是群成员 |
| 撤回消息 | `POST /im/app/message/recall` | 仅 fromUserId 或管理员可撤回 |
| 已读回执 | `POST /im/app/message/read` | groupId 校验：当前用户必须是群成员 |
| 群成员列表 | `GET /im/app/group/members` | groupId 校验：当前用户必须是群成员 |
| 消息搜索 | `GET /im/app/message/search` | 仅返回当前用户参与的消息 |

每个检查项需有对应单元测试。

#### 3.4.2 SQL 注入扫描

- 检查所有 `@Select` / `@Update` 中使用 `${}` 拼接的位置（必须为 `#{}`）
- 检查 `LambdaQueryWrapper.apply()` 中字符串拼接
- 工具：CodeQL 或 Semgrep 静态扫描

#### 3.4.3 XSS 扫描

- 前端消息渲染必须用 `v-text` 或 `DOMPurify.sanitize`（消息内容、群名、用户昵称）
- 后端入库前对 `<script>` / `<iframe>` 等危险标签做转义（可配置开关）

**验证**:
- 单元测试：构造 `fromUserId != me` 的请求，断言 403
- 单元测试：消息内容含 `<script>alert(1)</script>`，前端渲染后不执行

---

### P5-5 多租户隔离验证

**问题**: IM 模块表（im_message / im_group / im_group_member）已加 `tenant_id` 字段，但未验证租户隔离是否生效。

#### 3.5.1 验证清单

- 租户 A 用户向租户 B 群发消息 → 拒绝（403）
- 租户 A 用户查询租户 B 的群成员列表 → 返回空
- 租户 A 用户搜索消息 → 仅返回租户 A 的消息
- `ImMessageClearTask` 清理时 → 自动带租户过滤

#### 3.5.2 测试用例

新增 `ImTenantIsolationTest`（集成测试，`@Tag("integration")`）：
- 注入两个租户的测试用户
- 交叉调用接口，断言隔离

**验证**:
- 集成测试通过（CI 集成测试 job 触发）
- 手动测试：用租户 A token 调用接口，看不到租户 B 数据

---

## 四、B 档详细方案

### P5-6 前端虚拟滚动 + Emoji 分页 + 图片预加载

**优化点**: W4（虚拟滚动）+ W5（标题闪烁）+ W6（免打扰）+ A7（imCache 双 touch）+ A9（Emoji 分页）+ A10（图片预加载）

#### 4.6.1 W4 虚拟滚动

- **Web**: 引入 `vue-virtual-scroller`，`DynamicScroller` 渲染消息列表
- **App**: `scroll-view` 的 `@scroll` 事件计算可视区域，动态渲染前后各 20 条

#### 4.6.2 W5 标题闪烁

- 未读数变化时 `requestAnimationFrame` + 2s 间隔节流
- 避免高频消息（如群消息刷屏）导致标题频繁闪烁

#### 4.6.3 W6 免打扰

- 全局配置：`settings.doNotDisturb` (Boolean) + `doNotDisturbStart`/`End` (HH:mm)
- 按会话 mute：`im_group_member.muted` 字段（已有）
- 免打扰期间不弹通知、不响铃、标题不闪烁

#### 4.6.4 A7 imCache 双 touch 优化

[im-cache.js](file:///d:/workspace/Nexus_App/utils/im/im-cache.js) `appendMessage` 内联 `messageCache.get`，避免一次 `get` + 一次 `touch` 的重复 LRU 更新。

#### 4.6.5 A9 Emoji 分页

- Emoji 面板按分类 tab（笑脸/手势/动物/食物/活动/旅行/物品/符号）
- 每个 tab 内分页加载（每页 50 条）
- Lazy load Emoji JSON（按需请求，不入主包）

#### 4.6.6 A10 图片预加载

- 后端 OSS 返回 `thumbnailUrl`（`?imageView2/2/w/200`）
- 前端列表用缩略图，点击查看原图
- 进入会话时预加载最近 10 张图片缩略图

**验证**:
- 手动测试：千条消息流畅滚动（> 30 FPS）
- 手动测试：Emoji 面板秒开（< 100ms）
- 手动测试：图片列表渲染不卡顿

---

### P5-7 前端 E2E 测试（Playwright）

#### 4.7.1 测试范围

覆盖核心聊天流程：
1. 发送私聊消息 → 对端收到推送 → 已读回执
2. 发送群消息 → 所有成员收到推送 → 群未读数 +1
3. 撤回消息 → 双端显示"XX 撤回了一条消息"
4. 消息搜索 → 返回匹配结果
5. WS 断线重连 → 30s 内自动 fallback → 重连后增量同步
6. 多端登录 → 一端发送，另一端同步显示

#### 4.7.2 测试基础设施

- `plus-ui/e2e/` 目录，Playwright 配置
- CI 单独 job 触发（与单元测试隔离）
- 测试报告归档到 `doc/im/perf/e2e-report/`

**验证**:
- 6 个核心用例全部通过
- CI 集成（`mvn test -Dgroups=e2e` 或独立 Playwright job）

---

## 五、C 档评估方向（已取消）

> **2026-07-19 评估结论**: C 档 4 项（MQ 死信队列 / ES 消息搜索 / WebSocket 集群 / 多端状态同步）价值过低，不实施。原方案设计要点已归档至 P6 候选清单，待业务诉求明确后再启动。

---

## 六、验证清单

| 任务 | 验证方式 | 通过标准 |
|---|---|---|
| P5-1 | 单元测试 + 手动测试 | 1000 人群离线用户队列 ≤ 500；重连后 30s 内补推完成 |
| P5-2 | 手动测试 + 指标端点 | 慢 SQL 日志输出；`/actuator/prometheus` 含 `im_cache_hit_total` |
| P5-3 | 压测脚本执行 | 基线报告写入 `doc/im/perf/README.md`；6 项指标 P95 达标 |
| P5-4 | 单元测试 + 静态扫描 | 越权测试通过；CodeQL/Semgrep 无 HIGH 级别告警 |
| P5-5 | 集成测试 | 租户隔离测试通过（`-Dgroups=integration`） |
| P5-6 | 手动测试 | 千条消息 > 30 FPS；Emoji 面板 < 100ms；图片预加载有效 |
| P5-7 | E2E 测试 | 6 个核心用例全部通过 |

---

## 七、提交规范

- **A 档**：5 项任务完成后合并为 1 个 commit，commit message 前缀 `feat(im): P5 A 档...` 或 `perf(im): P5 A 档...`
- **B 档**：2 项任务完成后合并为 1 个 commit，commit message 前缀 `feat(im): P5 B 档...`
- **分支**：切出 `feature/im-p5-stability` 子分支，P5 验证稳定后合并回 `feature/im-p0-stability-fix`

---

## 八、风险评估与回滚

### 风险点

1. **P5-1 推送背压改造影响 P4-9 指标埋点**: 异步化后指标采集点变化。**缓解**: 保持 `publishToUsers` 入口埋点不变，仅内部执行模型改异步
2. **P5-1 改造共享 WebSocketUtils**: P4 评估时跳过的原因。**缓解**: 在 IM 模块封装 `ImPushExecutor`，不直接改 `WebSocketUtils`；通过 `WebSocketUtils.isOnline` 判定（如不暴露则用 `Session` 注册表替代）
3. **P5-4 安全审计可能发现 P0 漏洞**: 影响线上稳定性。**缓解**: 审计与修复同步进行；发现的漏洞按严重程度分级（CRITICAL 立即修复，HIGH 进入 P6，MEDIUM/LOW 进入 P7）
4. **P5-6 前端虚拟滚动兼容性**: vue-virtual-scroller 与现有组件库可能冲突。**缓解**: 在独立分支验证，确认无冲突再合并
5. **P5-7 E2E 测试环境依赖**: 需要独立测试数据库 + Redis。**缓解**: 使用 docker-compose 一键拉起测试环境

### 回滚策略

- P5-1 推送背压：`im.push.queue.enabled` 配置开关，故障时关闭回退到 P4-9 同步推送
- P5-2 慢 SQL 拦截器：`im.db.slow.enabled` 配置开关
- P5-4 安全修复：每项独立 commit，可单独 revert
- P5-6 前端虚拟滚动：feature flag 控制，灰度发布

---

## 九、与优化点记录文档的对应关系

> **注**: [optimization-backlog](./2026-07-18-im-optimization-backlog.md) 在 P4 计划中引用但实际未创建。P5 阶段需补建该文档，汇总 P0~P5 所有优化点的状态。

| P5 任务 | 来源 | 完成后状态 |
|---|---|---|
| P5-1 | P4-10 B8 延后 | ✅ B8 |
| P5-2 | P3 第十章 | ✅ 慢 SQL + 缓存命中率监控 |
| P5-3 | P3+P4 第十二章 性能对比延后 | ✅ 压测基线 |
| P5-4 | P3 第十章 | ✅ 安全审计深化 |
| P5-5 | P3 第十章 | ✅ 多租户隔离验证 |
| P5-6 | P4-11 延后 | ✅ W4+W5+W6+A7+A9+A10 |
| P5-7 | P4 第十章 | ✅ 前端 E2E |

**P5 完成后剩余未实施方向**:
- 消息加密存储（按需）
- MQ 死信队列 / ES 消息搜索 / WebSocket 集群 / 多端状态同步（C 档，价值过低不实施）

---

## 十、后续阶段预告（P6）

P5 完成后，P6 可考虑以下方向（待 P5 验证后选择）：

1. **IM 模块监控大盘**: Grafana 仪表盘（推送 QPS / 延迟分布 / 缓存命中率 / 慢 SQL Top10）
2. **IM 模块 SLA 体系**: 99.9% 可用性目标 + 故障演练（Chaos Engineering）
3. **消息已读回执扩展**: 私聊已读回执（P4-3 仅群消息实现 watermarks）
4. **离线消息推送集成**: 极光/友盟推送（App 后台存活时）
5. **IM 模块性能持续优化**: 基于 P5-3 压测基线，识别新的瓶颈点
6. **C 档方向重启评估**: 待业务诉求明确后再启动 MQ/ES/WS 集群/多端同步

---

## 附录：P0~P5 路线图

| 阶段 | 主题 | 任务数 | 完成状态 |
|---|---|---|---|
| P0 | 稳定性修复（基础 Bug） | 5 | ✅ 已完成 |
| P1 | 重发幂等 + 已读回执 + @提及 + 类型策略 | 8 | ✅ 已完成 |
| P2 | 批量化（listConversations / 群成员） | 6 | ✅ 已完成 |
| P3 | 测试基础设施 + 单元测试基线 | 12 | ✅ 已完成 |
| P4 | 性能优化 + 可靠性补齐 + 可观测性 | 12 | ✅ 已完成（2026-07-19） |
| P5 | 稳定性兜底 + 安全审计 + 前端体验补齐 | 7 | 🚧 实施中 |
| P6 | 监控大盘 + SLA 体系 | TBD | 🔮 待规划 |

---

## P6 衔接（2026-07-19 完成）

P5 B 档完成后，P6 高级交互入口开发已全部完成。详见 [2026-07-19-im-p6-advanced-interaction-plan.md](./2026-07-19-im-p6-advanced-interaction-plan.md)。

P6 覆盖 7 项缺失功能（撤回/转发/引用/多选/收藏/置顶/会话级免打扰），共 15 个任务（A1/A2/B1/B2/B3/C1/C2/C3/D1/E1/F1/G1/G2/G3/G4），分 7 个 Phase 实施：

- 后端：新建 2 张表（im_conversation_setting / im_favorite）+ 7 个新接口 + ImMessage 引用字段扩展
- 前端：新建 3 个组件/页面（message-action-menu / forward / favorite）+ chat/conversation 长按菜单集成 + 多选模式 + 引用链路
- 测试：后端 24 单元测试 + 前端 17 单元测试 + E2E 3 spec 骨架

后端 commits：19fb0c46 / 47df546b / f7c45802 / ba06ae4d / db27a294 / 6e9db23e
前端 commits：7ea74d4 / c338787 / 417d845 / 66c8097 / 4de54d7 / d23f532 / 78f481b / 98027a9

IM 模块功能覆盖度达到 100%。
