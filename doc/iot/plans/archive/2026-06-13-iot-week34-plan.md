# NexusERP — IoT 阶段一 · 第二周（Week 34）开发计划

> 关联文档：`doc/iot/NexusERP_IoT文档.md`（第十八节 18.1 Week 34）
> 上周计划：`./2026-06-12-iot-week33-plan.md`
> 计划制定：2026-06-13（执行周：Week 34）
> 工作量基准：3 人天（与文档 Week 34 一致）
> 范围边界：**仅后端**。前端实时仪表盘属 Week 35-36，不在本周。SSE 实时数据端点（`/iot/device/{id}/realtime`）按文档归 Week 35-36，本周不做。

---

## 0. 本周目标（一句话）

打通**数据管道**：设备 MQTT 属性上报 → Moquette `onPublish` 解析并 `XADD` Redis Stream → `StreamConsumer`（`XREADGROUP`）批量落 `iot_device_data` + 更新 `iot:latest:{deviceId}` 缓存 + 硬编码告警阈值检查并 SSE 推送；并固化遗嘱消息处理与 MES↔IoT 接口契约。

**上周衔接点**：Week 33 的 `IotMqttInterceptHandler.onPublish` 目前仅 `log.debug`，本周将其升级为「解析 + XADD」；连接/断开状态更新（`IotDeviceStatusCache` + `updateConnectionStatus`）已在 Week 33 落地，本周不重做，仅补遗嘱消息分支。

**不在本周范围**（属 Week 35-36）：SSE 实时数据端点、前端页面、SnailJob 定时任务（清理/心跳/Stream 修剪）、真实设备端到端联调。

---

## 1. 前置对齐决策（制定计划时已审，需你确认）

本周是在 Week 33 已落地代码上增量开发，不存在 DDL 规范级偏差。但有几处**设计决策**需先对齐，避免实现到一半返工：

| # | 决策点 | 选项 | 本计划默认 | 理由 |
|---|---|---|---|---|
| A | **Stream 消费线程模型** | (1) `@PostConstruct` 起独立线程 + `while` + `XREADGROUP BLOCK 1000`；(2) `@Scheduled` 固定频率轮询 | **(1) 独立线程 BLOCK 读** | BLOCK 模式实时性高、空转 CPU 占用低；`@PreDestroy` 优雅停止。Week 36 的 Stream 修剪由独立 SnailJob 任务负责，互不干扰 |
| B | **批量落库策略** | (1) 每批 `COUNT=100` 一次性 `batch insert` + 单事务；(2) 攒满 N 条或超时 T ms 才 flush | **(1) 每批一次 batch insert** | 第一版求简，Redis Stream 本身已是缓冲层；攒批+超时 flush 留待有性能数据后再优化 |
| C | **消费者组与消费者命名** | group=`iot-consumers`，consumer=`iot-consumer-1` | **固定单消费者** | 单机开发阶段足够；多消费者横向扩展时改 `iot-consumer-{instanceId}`，本周不引入实例标识 |
| D | **告警推送目标用户** | (1) 推该设备租户下所有在线用户；(2) 推设备创建人；(3) 推全量 | **(1) 租户内在线用户** | 多租户隔离；设备创建人未必在线。第一版用 `SseMessageUtils.publishMessage` 推租户在线用户集合（查 `OnlineService`），无在线用户则仅落日志 |
| E | **`IMesAndonService` 接口归属** | (1) 放 `ruoyi-iot` 内 + 默认 no-op 实现；(2) 抽到 `ruoyi-common` 新建 `ruoyi-common-iot`；(3) Spring 事件解耦 | **(2) 抽到 `ruoyi-common-iot`** ← 你已确认 | MES 与 IoT 并行开发，契约必须放公共模块：`ruoyi-common-iot` 仅含 `IotAlert` DTO + `IMesAndonService` 接口（纯契约，无 Bean）；`ruoyi-iot` 与未来 `ruoyi-mes` 各自依赖它，互不依赖。默认 no-op 实现留在 `ruoyi-iot`（`@ConditionalOnMissingBean`），MES 提供真实 Bean（`@Primary`）时自动让位。参照 `ruoyi-common-ai` 先例 |
| F | **`iot_device_data` 写入是否走 `@DS("iot")`** | StreamConsumer 是普通 Spring Bean，非 Service 层 | **显式 `@DS("iot")` 标注写入方法** | 与 Week 33 Service 一致；消费线程无 HTTP 上下文，但 `@DS` 注解在方法上仍生效（dynamic-datasource 基于 AOP） |
| G | **多租户 tenantId 来源** | (1) 从 Topic 解析 `iot/{tenantId}/{deviceId}/...`；(2) 从设备记录反查 | **Topic 解析 + 设备记录交叉校验** | Topic 中的 tenantId 与设备实际 tenantId 不一致时以设备记录为准并告警，防止伪造 Topic 越权 |

> 结论：以上默认值若你无异议即按此实现。**D / E 两项**如有偏好请在审批时指出。

---

## 2. 任务分解（按执行顺序）

### T1 · 消息负载契约固化与解析（0.3d）
- 新建 `com.nexus.iot.pipeline.payload.IotPropertyPayload`：字段对齐文档 16.3（`property`、`value`、`dataType`、`unit`、`timestamp`、`quality`），用 Jackson 反序列化。
- 新建 `IotMqttTopicParser`：解析 Topic `iot/{tenantId}/{deviceId}/{bizType}/{action}`，提取 tenantId / deviceId / bizType（property/event）/ action（post/set）；非法 Topic 返回 null 由上层跳过。
- `value` 为 Object，按 `dataType` 落到 `numericValue`/`textValue`/`boolValue` 三列之一（NUMERIC→BigDecimal，BOOL→Boolean，其余→textValue）。
- **验收**：单元测试覆盖正常负载、缺字段（timestamp 缺失用服务端时间）、非法 JSON、非法 Topic 四种情况。

### T2 · onPublish 升级：解析 + Redis Stream XADD（0.5d）
- 改造 `IotMqttInterceptHandler.onPublish`：保留 debug 日志，新增调用 `IotMessagePipelineService.publishToStream(...)`。
- 新建 `pipeline/IotMessagePipelineService`（`@DS` 无关，纯 Redis 操作）：
  - `RStream<Object,Object> stream = RedisUtils.getClient().getStream("iot:data:stream")`
  - `stream.add(StreamAddArgs.entry("device_id", ...).entry("device_code", ...).entry("property_code", ...).entry("property_name", ...).entry("data_type", ...).entry("value", ...).entry("collect_time", ...).entry("receive_time", now).entry("quality", ...).entry("tenant_id", ...).entry("source","MQTT"))`，字段对齐文档 16.4。
- **容错**：解析异常 → `log.warn` 记录原始 payload 后 return，**不抛出**（避免拖垮 Broker 会话线程）；XADD 异常 → 落错误日志，消息丢失可接受（QoS1 场景下设备侧重发或后续补采，第一版不引入死信）。
- 仅处理 `property/post` Topic；`event/post` 本周记录日志不进 Stream（事件上报属扩展）。
- **验收**：`mosquitto_pub` 发属性上报 → `redis-cli XLEN iot:data:stream` 递增；`XRANGE` 可见结构化字段。

### T3 · Redis Stream 消费者组 + StreamConsumer（1.0d）— 本周核心
- 新建 `pipeline/IotStreamConsumer`（`@Component`）：
  - `@PostConstruct`：幂等创建消费者组 —— `XGROUP CREATE iot:data:stream iot-consumers $ MKSTREAM`，捕获 `BUSYGROUP` 异常视为已存在（幂等）。
  - 启动独立消费线程（`IotThreadPoolConfig` 复用？否——用自定义单线程 `ExecutorService`，避免与 AI 线程池混用），`while(!stopped)` 执行：
    ```
    XREADGROUP GROUP iot-consumers iot-consumer-1 COUNT 100 BLOCK 1000 STREAMS iot:data:stream >
    ```
  - 每批返回的消息：
    1. 组装 `List<IotDeviceData>`（tenantId 交叉校验，见决策 G）
    2. 调用 `IotDeviceDataService.batchInsert(list)`（`@DS("iot")`，单事务）
    3. 逐条 `HSET iot:latest:{deviceId} {propertyCode} {value}`（扩展 `IotDeviceStatusCache` 或新建 `IotDeviceLatestCache`）
    4. 调用 `IotAlertChecker.check(deviceData)`（T4）
    5. 全部成功后批量 `XACK iot:data:stream iot-consumers {id1} {id2} ...`
  - **失败处理**：batch insert 抛异常 → 整批不 ACK，下次 `XREADGROUP >` 会重新投递未 ACK 的消息（pending entries）；连续失败 N 次记 ERROR 并跳过该消息（`XACK` 单条 + 告警日志），防止毒消息阻塞。**第一版：连续 3 次失败即 ACK 跳过 + ERROR 日志**。
  - `@PreDestroy`：`stopped=true` + 中断线程 + 等待退出，避免应用停机时丢消息。
- 新建 `device/service/IIotDeviceDataService` + `impl`：`batchInsert(List<IotDeviceData>)`（`@DS("iot")`），用 MyBatis-Plus `saveBatch` 或 mapper 自定义批量 insert。新增 `IotDeviceDataMapper`（继承 `BaseMapperPlus`）。
- 新建 `IotDeviceLatestCache`：`HSET`/`HGETALL iot:latest:{deviceId}`，无 TTL（覆盖更新）。
- **验收**：
  - 发 100 条属性上报 → `iot_device_data` 表新增 100 行，`collect_time`/`numeric_value` 正确
  - `HGETALL iot:latest:{deviceId}` 返回最新属性值
  - `XPENDING iot:data:stream iot-consumers` 为空（已 ACK）
  - 杀掉消费线程模拟重启 → pending 消息被重新消费（at-least-once）

### T4 · 硬编码告警阈值检查 + SSE 推送（0.3d）
- 新建 `pipeline/IotAlertChecker`（`@Component`）：
  - `check(IotDeviceData data)`：第一版硬编码规则——`propertyCode="temperature" && numericValue > 80` → 构造 `IotAlert`（level=HIGH）。
  - 阈值表先硬编码在类内 `Map<String, BigDecimal>`，可由 `nexus.iot.alert.thresholds` 配置覆盖（`@ConfigurationProperties`）。
  - 命中告警：`log.warn` + 构造 `IotAlert` DTO + 调用 `IotAlertNotifier.notify(alert)`。
- 新建 `pipeline/IotAlertNotifier`：通过 `SseMessageUtils.publishMessage(SseMessageDto)` 推送；目标用户为该设备租户在线用户（决策 D），无在线用户仅落日志。SSE 消息体 JSON：`{type:"IOT_ALERT", ...alert}`。
- **验收**：发 `temperature=85` → 日志见告警；若前端 SSE 连接（用 curl 模拟 `EventSource`）可收到 `IOT_ALERT` 推送。

### T5 · 遗嘱消息（Will Message）处理（0.3d）
- Week 33 `onConnectionLost` 已将设备置 OFFLINE，本周补**遗嘱消息语义**：
  - `onConnect` 时读取 `InterceptConnectMessage` 的 will（`msg.getWillMessage()` / willTopic/willQos）；若设备声明了 will，记录到 Redis `iot:will:{deviceId}`（TTL 与 statusTtl 一致），便于排查。
  - `onConnectionLost`：区分「正常 DISCONNECT」（onDisconnect 触发，无 will）与「异常断开」（onConnectionLost 触发，可能伴随 will 投递）；异常断开时在设备状态更新日志中标注 `reason=CONNECTION_LOST`，并清空 `iot:will:{deviceId}`。
  - 若 will payload 为标准事件格式（`event=post`），按事件处理（本周仅日志）。
- **验收**：`mosquitto_sub` 订阅 will topic 验证遗嘱投递；设备异常 kill（非 graceful disconnect）→ 日志见 `CONNECTION_LOST` + 设备 OFFLINE。

### T6 · MES↔IoT 接口契约固化（0.4d）— 抽公共模块
> 你已确认：MES 与 IoT 并行开发，契约必须放公共模块，避免业务模块互相依赖。

**T6.1 新建 `ruoyi-common-iot` 公共契约模块：**
- `ruoyi-common/ruoyi-common-iot/pom.xml`：parent=`ruoyi-common`，依赖仅需 `ruoyi-common-core`（DTO 用 `Serializable` 即可，不引 mybatis/redis）。参照 `ruoyi-common-ai/pom.xml` 最小集。
- `ruoyi-common/pom.xml` 的 `<modules>` 追加 `<module>ruoyi-common-iot</module>`。
- `ruoyi-common-bom/pom.xml` 的 `dependencyManagement` 追加 `ruoyi-common-iot` 条目（参照 `ruoyi-common-ai`）。
- 包 `com.nexus.common.iot`：
  - `dto/IotAlert`：字段对齐文档 17.2（deviceId/deviceCode/mesEquipmentId/alertType/alertLevel/alertMessage/value/threshold/tenantId），`Serializable` + Lombok `@Data`。
  - `service/IMesAndonService`：`Long createAndonFromAlert(IotAlert alert)`（IoT 调 MES）。
  - `service/IotDeviceDataQueryService`（可选预留）：MES 查 IoT 最新数据/在线状态/历史数据的统一入口接口，方法签名对齐文档 17.1 的三个 GET。本周仅定义接口，实现在 `ruoyi-iot`。
- **不写 Spring Bean / 不写 AutoConfiguration.imports**：纯契约模块，靠主应用 `com.nexus.**` 扫描即可（与 `ruoyi-common-ai` 一致，无 `META-INF/spring/...`）。

**T6.2 `ruoyi-iot` 接入公共契约 + no-op 实现：**
- `ruoyi-iot/pom.xml` 增加 `ruoyi-common-iot` 依赖。
- 新建 `com.nexus.iot.common.IotMesAndonServiceNoop`（`ruoyi-iot` 内）：`@Component @ConditionalOnMissingBean(IMesAndonService.class)`，方法体 `log.warn("MES 安灯接口未实现，IoT 告警已忽略")` 返回 null。MES Phase 2 提供 `@Primary @Service` 真实实现时自动让位。
- 新建 `com.nexus.iot.common.IotDeviceDataQueryServiceImpl`：实现 `IotDeviceDataQueryService`，从 Redis 缓存/MySQL 查询（本周可先实现 latest/status，history 留 TODO）。
- 在 `IotAlertNotifier` 中预留调用点（注释 `// TODO Phase2: mesAndonService.createAndonFromAlert(alert)`），本周不实际调用（no-op 调了也无副作用，但保持显式 TODO 边界）。
- **验收**：
  - `ruoyi-common-iot` 独立编译通过；`ruoyi-iot` 引入后编译通过。
  - `IotAlert` 字段与文档 17.2 一致。
  - 启动时 no-op `IMesAndonService` Bean 注入成功。
  - 未来 `ruoyi-mes` 仅需依赖 `ruoyi-common-iot` 即可实现接口，**无需依赖 `ruoyi-iot`**。

### T7 · 配置项 + 集成自测 + 文档收尾（0.3d）
- `application.yml` 新增 `nexus.iot.pipeline` 段：
  ```yaml
  nexus:
    iot:
      mqtt: { enabled: true, port: 1883, ... }   # Week 33 已有
      pipeline:
        stream-key: iot:data:stream
        consumer-group: iot-consumers
        consumer-name: iot-consumer-1
        batch-size: 100
        block-ms: 1000
        max-retry: 3
      alert:
        thresholds: { temperature: 80 }   # propertyCode → 阈值
  ```
- 联调顺序（需 ry-iot 库已建 + Week 33 DDL 已执行）：
  1. 启动应用 → 日志见「消费者组已就绪」+ 消费线程启动
  2. `mosquitto_pub` 发 `temperature=25.5` → `XLEN` +1 → `iot_device_data` +1 → `HGETALL iot:latest` 有值 → `XPENDING` 空
  3. 发 `temperature=85` → 告警日志 + SSE 推送
  4. 模拟消费线程异常重启 → pending 消息重投
  5. 设备异常断开 → `CONNECTION_LOST` + OFFLINE
- 更新 `NexusERP_IoT文档.md` 第十四节检查清单：
  - `Redis Streams 消费者组已创建` → ✅
  - 新增行：`数据管道(MQTT→Stream→MySQL+缓存)` ✅、`硬编码告警` ✅、`遗嘱消息` ✅、`MES↔IoT 接口契约` ✅
  - SSE 实时端点 / 定时任务 / 前端保持 ❌（属 Week 35-36）
- 在 `doc/iot/sql/iot-week34-*.sql`（如需）记录本周 DDL 增量——**预计无新表**，仅可能为 `iot_device_data` 补一个 `idx_tenant_time` 索引（消费查询用），若无则不产出该文件。
- **验收**：对照文档 19.1 本周可验收项（数据管道、Redis 缓存、设备在线状态、硬编码告警）全部通过。

---

## 3. 交付物清单

| 类型 | 路径 |
|---|---|
| 消息解析 | `ruoyi-iot/.../pipeline/payload/IotPropertyPayload.java`、`IotMqttTopicParser.java` |
| Stream 生产 | `ruoyi-iot/.../pipeline/IotMessagePipelineService.java` |
| Stream 消费 | `ruoyi-iot/.../pipeline/IotStreamConsumer.java`、`IotDeviceLatestCache.java` |
| 数据落库 | `ruoyi-iot/.../device/service/IIotDeviceDataService.java` + impl、`IotDeviceDataMapper.java` |
| 告警 | `ruoyi-iot/.../pipeline/IotAlertChecker.java`、`IotAlertNotifier.java` |
| 接口契约（公共模块） | `ruoyi-common/ruoyi-common-iot/pom.xml`、`.../common/iot/dto/IotAlert.java`、`.../common/iot/service/IMesAndonService.java`、`.../common/iot/service/IotDeviceDataQueryService.java` |
| 接口契约（iot 侧实现） | `ruoyi-iot/.../common/IotMesAndonServiceNoop.java`、`IotDeviceDataQueryServiceImpl.java` |
| 模块注册 | `ruoyi-common/pom.xml`（加 module）、`ruoyi-common-bom/pom.xml`（dependencyManagement）、`ruoyi-iot/pom.xml`（引入 common-iot） |
| 改造 | `IotMqttInterceptHandler.java`（onPublish 升级）、`IotDeviceStatusCache.java`（will 缓存） |
| 配置 | `application.yml`（`nexus.iot.pipeline` + `nexus.iot.alert` 段） |
| DDL 增量（可选） | `doc/iot/sql/iot-week34-*.sql`（仅索引，预计无） |
| 文档更新 | `doc/iot/NexusERP_IoT文档.md`（检查清单状态） |

---

## 4. 自审查记录（计划制定者自查）

**与 Week 33 代码衔接核对：**
1. ✅ `IotMqttInterceptHandler.onPublish` 现为纯日志 → T2 升级为 XADD，不破坏现有 onConnect/Disconnect 逻辑。
2. ✅ `IotDeviceStatusCache` 已实现 `iot:status` → T3 新增 `IotDeviceLatestCache` 管 `iot:latest`，职责分离，不混入状态缓存。
3. ✅ `IotDeviceData` 实体已独立（不继承 BaseEntity，无审计列）→ T3 批量 insert 不会触发列不存在错误。
4. ✅ Week 33 `IIotDeviceService` 无 `batchInsert` → T3 新建独立 `IIotDeviceDataService`，不污染设备 CRUD Service。
5. ✅ `@DS("iot")` 在 Service 方法上 → 消费线程虽无 HTTP 上下文，dynamic-datasource 基于 AOP 仍生效（已与 Week 33 验证模式一致）。

**风险识别与处理：**
6. ⚠️ **毒消息阻塞**：单条消息解析/落库失败会卡住整批 ACK。处理：连续失败 `max-retry` 次后单条 ACK 跳过 + ERROR 日志（T3）。
7. ⚠️ **消费线程生命周期**：`@PostConstruct` 起线程需在 `@PreDestroy` 优雅停止，否则应用停机丢消息/线程泄漏（T3 显式处理）。
8. ⚠️ **Moquette onPublish 线程**：Broker 会话线程调用 onPublish，XADD 若阻塞会影响其他设备消息投递。处理：XADD 是 Redis 单命令毫秒级，可接受；若后续量大再引入内部队列异步化（本周不做）。
9. ⚠️ **多租户伪造 Topic**：设备 A 伪造 `iot/{tenantB}/{deviceB}/...` Topic。处理：决策 G——以设备记录（按 deviceCode 查）的 tenantId 为准，Topic 中的 tenantId 仅作路由提示，不一致时告警（T1+T3）。
10. ⚠️ **告警推送无在线用户**：SSE 推送需用户在线，开发阶段可能无人在线。处理：无在线用户时仅落日志，不报错（T4）。

**遗留风险/需你拍板：**
- **R1**（已解决）：`IMesAndonService` / `IotAlert` 抽到新建的 `ruoyi-common-iot` 公共契约模块，`ruoyi-iot` + 未来 `ruoyi-mes` 均依赖它、互不依赖。no-op 默认实现留 `ruoyi-iot`，MES 提供 `@Primary` 真实实现时让位。
- **R2**：告警推送目标用户（决策 D）默认「租户在线用户」。若你希望第一版直接推全量或推特定角色（如设备管理员），请指定。
- **R3**：`iot_device_data` 是否需要补 `idx_tenant_time` 索引——取决于 Week 36 清理任务与 MES 历史查询的访问模式。本周默认不补（Week 33 已有 `idx_device_time`/`idx_property`），如你认为租户维度查询高频可现在加。
- **R4**：ry-iot 库与 Week 33 DDL **是否已在你本地执行**？本周 T3 联调强依赖 `iot_device_data` 表存在。若未执行，请先执行 `doc/iot/sql/archive/iot-week33-01-init.sql`，否则本周自测无法进行。

---

## 5. 验收标准（本周完成定义）

1. `mvn -pl ruoyi-modules/ruoyi-iot -am compile` 通过，无依赖冲突。
2. 应用启动：日志见消费者组创建 + 消费线程启动，无 ERROR。
3. **数据管道端到端**：`mosquitto_pub` 发属性上报 → Redis Stream `XLEN` 递增 → `iot_device_data` 表新增记录（字段正确）→ `HGETALL iot:latest:{deviceId}` 有最新值 → `XPENDING` 清空。
4. **至少一次语义**：消费线程异常重启后，pending 消息被重新消费且不丢。
5. **硬编码告警**：`temperature>80` 触发告警日志 + SSE 推送（有在线用户时）。
6. **遗嘱消息**：设备异常断开 → `CONNECTION_LOST` 日志 + 设备 OFFLINE。
7. **接口契约**：`ruoyi-common-iot` 模块独立编译通过，`IotAlert` + `IMesAndonService` 定义就绪；`ruoyi-iot` 引入后 no-op 实现注入成功；未来 MES 仅依赖 common-iot 即可实现接口。
8. 文档检查清单本周项状态更新为 ✅；SSE 端点/定时任务/前端保持 ❌（属 Week 35-36）。
