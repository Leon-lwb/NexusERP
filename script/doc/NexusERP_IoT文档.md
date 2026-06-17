# NexusERP - IoT模块综合文档

> **文档状态**: 📋 实施开发文档 — 本文档为IoT模块的完整设计方案与可执行开发计划。
> **Week 33 进度**：`ruoyi-iot` 模块已创建并编译通过，`@DS("iot")` 数据源已配置，Moquette 内嵌 Broker + 设备认证 + 产品/设备 CRUD 已实现；3 张核心表 DDL 脚本已产出（`script/sql/ry-iot-week33.sql`），待开发者本地执行建库。
> **Week 34 进度**：数据管道已打通——MQTT onPublish→Redis Stream XADD→`IotStreamConsumer`(XREADGROUP)→批量落 `iot_device_data`+`iot:latest` 缓存；硬编码告警(限频+租户在线用户 SSE 推送)、遗嘱消息、MES↔IoT 接口契约(抽 `ruoyi-common-iot` 公共模块) 均已实现并编译通过。SSE 实时端点/SnailJob 定时任务/前端属 Week 35-36。
> 参考实施计划：[IoT 阶段一 Week 33 开发计划](../superpowers/plans/2026-06-17-iot-week33-plan.md)、[IoT 阶段一 Week 34 开发计划](../superpowers/plans/2026-06-23-iot-week34-plan.md)
>
> **数据采集归属**: IoT负责自动设备采集（MQTT/传感器数据），MES负责手工/业务采集（质检、工序录入）。
> IoT采集的数据写入 `iot_device_data`，MES的 `dc_collection_point` 通过 `point_type=AUTO` 关联IoT设备。
> SPC消费MES归档后的采集结果，不直接消费IoT原始数据。

## 一、IoT模块总体设计

### 1.1 IoT定位

IoT(Internet of Things)物联网平台，负责设备接入、数据采集、实时推送和基础告警，为MES提供设备数据支撑。

### 1.2 设计原则

| 原则 | 说明 |
|------|------|
| 最小链路先行 | MQTT→入库→前端可见，3-4周内让一台真实设备的数据出现在仪表盘上 |
| 已有基础设施优先 | Redis Streams替代RabbitMQ，SSE替代WebSocket，SnailJob替代自研调度 |
| 按需引入重量级组件 | TDengine、边缘网关、规则引擎都是"有了瓶颈再加" |
| 不为不存在的模块预留耦合 | MES集成点用接口抽象，不建外键 |

### 1.3 IoT模块结构（精简版）

```
ruoyi-iot/
└── com/nexus/iot/
    ├── device/     # 设备管理(产品/设备)
    ├── mqtt/       # MQTT接入(Moquette内嵌Broker)
    ├── pipeline/   # 数据管道(Redis Streams消费)
    ├── realtime/   # 实时推送(SSE)
    └── task/       # 定时任务(SnailJob: 清理/心跳)
```

> **重要变更**: 原规划8个子模块（config/common/device/protocol/data/rule/alert/scene/gateway）精简为5个。规则引擎、告警系统、边缘网关、场景联动均为按需扩展模块。

### 1.4 数据源配置

- 数据库: ry-iot (独立库) — 📋 待创建
- 注解: `@DS("iot")` — 📋 待配置

### 1.5 数据流架构

```
设备 → MQTT Publish → Moquette Broker(内嵌)
  → MqttMessageListener → Redis Stream "iot:data:stream" (XADD)
    → StreamConsumer (XREADGROUP, 消费者组)
      ├── 写入 iot_device_data (MySQL)
      ├── 更新 Redis 缓存 "iot:latest:{deviceId}:{propertyCode}"
      └── 硬编码告警阈值检查 → SSE推送前端

前端 EventSource("/iot/device/{id}/realtime")
  → 后端 SseEmitter → 从Redis缓存读取最新值 → 定期推送
```

### 1.6 技术栈复用

| 组件 | 来源 | 用途 | 替代原方案 | 状态 |
|------|------|------|-----------|------|
| Redisson (Redis Streams) | ruoyi-common-redis | 设备数据管道 | 替代RabbitMQ新vhost | ✅ 可用 |
| SseEmitter | ruoyi-common-sse | 实时数据推送 | 替代WebSocket | ✅ 可用 |
| SnailJob | ruoyi-common-job | 定时任务(清理/心跳) | 替代自研调度 | ✅ 可用 |
| Redis缓存 | ruoyi-common-redis | 设备最新值/在线状态 | - | ✅ 可用 |

---

## 二、IoT数据表设计（3张核心表）

> **精简说明**: 原规划15张+时序库精简为3张核心表。协议配置、规则引擎、告警系统、边缘网关、场景联动均为按需扩展，不在初始版本中建表。

### 2.1 产品与设备

```sql
-- IoT产品(设备模板)
CREATE TABLE iot_product (
    product_id       BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    product_code     VARCHAR(64) NOT NULL,
    product_name     VARCHAR(128) NOT NULL,
    product_type     VARCHAR(32) DEFAULT 'DEVICE' COMMENT 'DEVICE/GATEWAY',
    protocol_type    VARCHAR(32) NOT NULL DEFAULT 'MQTT' COMMENT 'MQTT/OPC_UA/MODBUS/HTTP',
    description      VARCHAR(500),
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (product_id),
    UNIQUE KEY uk_code (product_code, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT产品';

-- IoT设备实例
CREATE TABLE iot_device (
    device_id        BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    device_code      VARCHAR(64) NOT NULL,
    device_name      VARCHAR(128) NOT NULL,
    product_id       BIGINT NOT NULL,
    product_name     VARCHAR(128),
    protocol_type    VARCHAR(32) NOT NULL DEFAULT 'MQTT',
    connection_status VARCHAR(16) DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE',
    last_online_time DATETIME,
    device_secret    VARCHAR(128) COMMENT '设备密钥',
    ip_address       VARCHAR(64),
    firmware_version VARCHAR(64),
    location         VARCHAR(256),
    mes_equipment_id BIGINT COMMENT '关联MES设备ID(接口查询,无外键)',
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (device_id),
    UNIQUE KEY uk_code (device_code, tenant_id),
    INDEX idx_product (product_id),
    INDEX idx_status (connection_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT设备实例';
```

### 2.2 设备数据

```sql
-- IoT设备数据(原始数据,先存MySQL)
-- 触发迁移条件: 表行数超过1000万 → 引入TDengine
CREATE TABLE iot_device_data (
    data_id          BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    device_id        BIGINT NOT NULL,
    device_code      VARCHAR(64),
    property_code    VARCHAR(64) NOT NULL,
    property_name    VARCHAR(128),
    data_type        VARCHAR(16),
    numeric_value    DECIMAL(14,4),
    text_value       VARCHAR(500),
    bool_value       TINYINT(1),
    collect_time     DATETIME NOT NULL,
    receive_time     DATETIME NOT NULL,
    quality          VARCHAR(16) DEFAULT 'GOOD' COMMENT 'GOOD/BAD/UNCERTAIN',
    source           VARCHAR(32) DEFAULT 'MQTT' COMMENT 'MQTT/OPC_UA/MODBUS/HTTP',
    create_time      DATETIME,
    PRIMARY KEY (data_id),
    INDEX idx_device_time (device_id, collect_time),
    INDEX idx_property (device_id, property_code, collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT设备数据';
```

---

## 三、MQTT协议接入

### 3.1 MQTT Broker选型

| 方案 | 结论 | 理由 |
|------|------|------|
| Moquette内嵌 | ✅ 开发阶段首选 | 轻量级，Spring Boot Starter集成，单JAR部署 |
| EMQX独立部署 | 📋 生产环境备选 | 集群模式，高吞吐，适合大规模设备 |

### 3.2 Topic规范

| Topic | 方向 | 说明 |
|-------|------|------|
| `iot/{tenantId}/{deviceId}/property/post` | 设备→平台 | 属性上报 |
| `iot/{tenantId}/{deviceId}/property/set` | 平台→设备 | 属性设置 |
| `iot/{tenantId}/{deviceId}/event/post` | 设备→平台 | 事件上报 |

### 3.3 连接参数

- QoS: 1(至少一次)
- 认证: deviceCode + deviceSecret
- 遗嘱消息: 设备离线通知 → 更新 iot_device.connection_status = OFFLINE
- 端口: 1883 (TCP)

### 3.4 Moquette集成

```java
/**
 * Moquette MQTT Broker 内嵌配置
 * Spring Boot Starter方式启动
 * 依赖: org.eclipse.moquette:moquette-broker:0.17
 */
@Configuration
public class MqttBrokerConfig {
    // 启动内嵌Broker，监听1883端口
    // 注册消息监听器 MqttMessageListener
    // 设备连接/断开时更新Redis在线状态 + 数据库connection_status
}
```

---

## 四、数据管道 — Redis Streams

### 4.1 为什么用Redis Streams替代RabbitMQ

| 维度 | RabbitMQ | Redis Streams |
|------|---------|--------------|
| 新增基础设施 | 需要新vhost + exchange + queue | 零新增，复用现有Redis |
| 消费者组 | 原生支持 | 原生支持(XREADGROUP) |
| 消息确认 | ACK机制 | XACK机制 |
| 持久化 | 磁盘持久化 | Redis AOF + RDB |
| 运维成本 | 独立中间件 | 复用Redis实例 |
| 适用场景 | 复杂路由/延迟消息 | 简单发布订阅/流处理 |

IoT场景的设备数据上报属于简单的发布订阅模式，Redis Streams完全满足需求。

### 4.2 数据流设计

```
Step 1: 设备MQTT发布 → Moquette接收 → MqttMessageListener
Step 2: Listener解析消息 → XADD iot:data:stream * device_id xxx property_code xxx value xxx
Step 3: StreamConsumer(XREADGROUP GROUP iot-consumers consumer-1 BLOCK 1000)
  ├── 写入 iot_device_data 表 (MySQL批量INSERT)
  ├── HSET iot:latest:{deviceId} {propertyCode} {value} (Redis缓存最新值)
  ├── SET iot:status:{deviceId} ONLINE EX 3600 (设备在线状态)
  └── 告警阈值检查(硬编码) → SseMessageUtils推送
Step 4: XACK iot:data:stream iot-consumers {messageId}
```

### 4.3 Redisson API使用

```java
// 写入Stream
RStream<Object, Object> stream = redissonClient.getStream("iot:data:stream");
stream.add(StreamAddArgs.entry("device_id", deviceId)
    .entry("property_code", propertyCode)
    .entry("value", value)
    .entry("collect_time", collectTime));

// 消费者组读取
StreamReadGroupArgs args = StreamReadGroupArgs.greaterThan(
    StreamMessageId.NEWEST).count(100).timeout(Duration.ofSeconds(1));
Map<StreamMessageId, Map<Object, Object>> messages =
    stream.readGroup("iot-consumers", "consumer-1", args);

// 确认消息
stream.ack("iot-consumers", messageId);
```

### 4.4 Redis缓存结构

| Key | 类型 | 用途 | TTL |
|-----|------|------|-----|
| `iot:latest:{deviceId}` | Hash | 设备最新属性值 | 无(覆盖更新) |
| `iot:status:{deviceId}` | String | 设备在线状态(ONLINE/OFFLINE) | 1小时 |
| `iot:data:stream` | Stream | 设备数据管道 | 由清理任务控制 |

---

## 五、实时推送 — SSE

### 5.1 为什么用SSE替代WebSocket

| 维度 | WebSocket | SSE |
|------|-----------|-----|
| 方向 | 双向 | 单向(服务端→客户端) |
| 复杂度 | 需要连接管理/心跳/重连 | 原生HTTP，简单 |
| 已有基础设施 | ruoyi-common-websocket | ruoyi-common-sse ✅ |
| IoT需求 | 实时数据推送(单向即可) | 完全满足 |

IoT场景只需要服务端向客户端推送设备数据，不需要客户端向服务端发送消息，SSE完全满足需求。

### 5.2 实时数据推送实现

```java
/**
 * IoT设备实时数据SSE端点
 * 前端: new EventSource("/iot/device/{deviceId}/realtime")
 */
@GetMapping("/{deviceId}/realtime")
public SseEmitter realtime(@PathVariable Long deviceId) {
    SseEmitter emitter = sseEmitterManager.connect(userId, token);
    // 定时从Redis "iot:latest:{deviceId}" 读取最新值
    // 通过emitter.send()推送给前端
    return emitter;
}
```

### 5.3 告警推送

硬编码告警阈值检查发现异常时：
```java
// 通过已有SSE基础设施推送告警
SseMessageUtils.publishMessage(Arrays.asList(userId), alertMessage);
```

---

## 六、定时任务 — SnailJob

### 6.1 任务清单

| 任务类 | 频率 | 说明 |
|--------|------|------|
| `IotDataCleanupTask` | 每天凌晨2:00 | 清理 iot_device_data 中超过90天的数据 |
| `IotDeviceHeartbeatTask` | 每5分钟 | 检查设备last_online_time，超时(>15分钟)标记OFFLINE |
| `IotStreamTrimTask` | 每小时 | XTRIM iot:data:stream MAXLEN 100000 (控制Stream长度) |

### 6.2 SnailJob配置

复用已有 `ruoyi-common-job` 模块，在SnailJob后台注册任务：
- 任务组: `iot_group`
- 任务类型: BEAN
- 执行模式: 单机

---

## 七、告警机制（第一版: 硬编码）

### 7.1 设计思路

第一版不建告警规则表，直接在StreamConsumer中硬编码阈值检查：

```java
// 示例: 温度传感器告警
if ("temperature".equals(propertyCode) && numericValue > 80) {
    // 1. 日志记录
    log.warn("设备{}温度过高: {}°C", deviceId, numericValue);
    // 2. SSE推送前端
    SseMessageUtils.publishMessage(targetUserIds, "设备温度过高告警");
}
```

### 7.2 告警升级路径

当硬编码不满足需求时，按需引入告警规则表：

```sql
-- 按需创建: 告警规则表
CREATE TABLE iot_alert_rule ( ... );
-- 按需创建: 告警日志表
CREATE TABLE iot_alert_log ( ... );
```

告警生命周期: `ACTIVE → ACKNOWLEDGED → RESOLVED`
通知方式: SSE推送 + 邮件(ruoyi-common-mail)

---

## 八、按需扩展模块

> 以下模块按实际业务瓶颈驱动，非必须全部完成。

### 8.1 OPC UA / Modbus 协议适配 (按需, ~4天)

**触发条件**: 需要连接PLC/SCADA设备时。

- OPC UA: Eclipse Milo客户端，订阅MonitoredItem
- Modbus: modbus4j TCP模式，轮询读取寄存器
- 统一适配器接口:

```java
public abstract class BaseProtocolAdapter {
    public abstract void connect(Map<String, String> config);
    public abstract void disconnect();
    public abstract Object readProperty(String address);
    public abstract void subscribe(List<String> addresses, Consumer<DeviceData> callback);
}
```

### 8.2 规则引擎 (按需, ~5天)

**触发条件**: 硬编码告警阈值不满足需求（多条件组合/用户可配置）。

- `iot_rule` 表 + JSON条件/动作配置
- 简单条件评估引擎（AND/OR组合）
- 动作: 告警/设备命令/HTTP回调

### 8.3 时序数据库迁移 (按需, ~3天)

**触发条件**: `iot_device_data` 表行数超过1000万。

- 引入TDengine
- 历史数据迁移脚本
- 读写分离: 写入走TDengine，查询走TDengine + MySQL混合
- TDengine超级表设计: 按产品类型建超级表，设备为子表

### 8.4 边缘网关 (按需, ~2天)

**触发条件**: 设备数量超过100台或需要协议转换。

- `iot_gateway` 表
- MQTT桥接（网关→云端Broker）
- 心跳检测

### 8.5 场景联动 (按需, ~4天)

**触发条件**: 需要设备间自动联动（如温度超限→自动开启风扇）。

- `iot_scene` 场景表 + `iot_scene_trigger` 触发器 + `iot_scene_action` 动作

---

## 九、IoT实施计划

### 9.1 阶段一: 最小链路 (Week 33-36, ~12人天)

**目标: 3-4周内让一台真实设备的数据出现在前端仪表盘上。**

| 周次 | 任务 | 人天 |
|------|------|------|
| Week 33 | 创建ruoyi-iot模块+数据源配置+Moquette内嵌Broker+产品/设备CRUD | 3 |
| Week 34 | 数据管道: MQTT消息→Redis Streams→MySQL+Redis缓存+硬编码告警 | 3 |
| Week 35-36 | 前端: 产品管理+设备列表+实时数据仪表盘(SSE+ECharts) | 4 |
| Week 36 | SnailJob任务: 数据清理+心跳检测+Stream修剪 | 2 |

**阶段一小计: 12人天**

### 9.2 阶段二: 按需扩展 (Week 37-40, ~18人天)

> 以下任务按实际业务瓶颈驱动，非必须全部完成。

| 任务 | 人天 | 触发条件 |
|------|------|---------|
| OPC UA / Modbus协议适配 | 4 | 需要连接PLC/SCADA设备 |
| 规则引擎 | 5 | 硬编码告警不满足需求 |
| 告警系统(规则表+日志表) | 4 | 需要完整告警生命周期管理 |
| 时序数据库迁移(TDengine) | 3 | 数据量超1000万行 |
| 边缘网关 | 2 | 设备超100台或需协议转换 |

**阶段二小计: ~18人天(按需)**

### 9.3 工作量汇总

| 阶段 | 周次 | 人天 |
|------|------|------|
| 阶段一(最小链路) | Week 33-36 | 12 |
| 阶段二(按需扩展) | Week 37-40 | ~18 |
| **合计** | **Week 33-40 (8周)** | **~30** |

> **对比原计划**: 原67人天/16周 → 修订30人天/8周（削减55%，按需扩展另计）

---

## 十、IoT前端规划

### 10.1 目录结构

```
plus-ui/src/views/iot/
├── product/               # 产品管理
│   └── index.vue          # 产品列表
├── device/                # 设备管理
│   ├── index.vue          # 设备列表(含在线状态)
│   └── realtime.vue       # 实时数据仪表盘
```

### 10.2 按需扩展页面

```
├── rule/index.vue         # 规则管理(按需)
├── alert/                 # 告警管理(按需)
│   ├── rule.vue
│   └── log.vue
└── gateway/index.vue      # 网关管理(按需)
```

### 10.3 核心页面估算

| 页面 | 说明 |
|------|------|
| 产品管理 | 1页面 |
| 设备管理 | 2页面(列表+实时仪表盘) |
| **阶段一合计** | **3页面** |

### 10.4 关键前端组件

- 实时数据仪表盘: SSE + ECharts实时刷新
- 数值卡片: 显示设备最新属性值
- 历史趋势图: ECharts折线图，查询MySQL历史数据
- 设备状态指示灯: 绿色ONLINE / 灰色OFFLINE

---

## 十一、IoT与MES集成

### 11.1 数据流向

IoT设备数据 → Redis缓存 → MES数据采集模块(mes_dc)

### 11.2 集成方式

| 方式 | 说明 |
|------|------|
| REST API | MES查询IoT设备最新数据(从Redis缓存) |
| 接口抽象 | `IMesAndonService.createAndonFromAlert()` — IoT告警触发MES安灯 |
| 字段预留 | `iot_device.mes_equipment_id` ↔ `mes_eq_equipment.iot_device_id` |

> **关键原则**: MES与IoT之间不建外键，通过接口抽象解耦。两个模块可独立开发和部署。

### 11.3 集成场景

- 设备状态实时监控: IoT设备在线状态 → MES安灯看板
- 设备数据自动采集: IoT Redis缓存 → MES数据采集点读取
- 设备故障联动: IoT告警 → MES安灯呼叫(通过接口)

---

## 十二、技术架构决策

### 12.1 为什么用Redis Streams替代RabbitMQ

- IoT当前规模不需要独立消息中间件
- Redisson已原生支持Stream API（RStream）
- 消费者组（XREADGROUP）天然支持多消费者并行
- 消息确认（XACK）+ 持久化（AOF+RDB）保证可靠性
- 零新增基础设施，复用现有Redis实例

### 12.2 为什么用SSE替代WebSocket

- IoT场景只需服务端→客户端单向推送
- 已有 ruoyi-common-sse 基础设施
- SSE基于HTTP，无需额外端口和连接管理
- 自动重连由浏览器EventSource原生支持

### 12.3 为什么MySQL先用而非直接上TDengine

- 开发阶段数据量小，MySQL完全够用
- TDengine需要额外部署和运维
- 当数据量超1000万行时再迁移，有明确的触发条件
- 迁移方案已规划（阶段二按需任务）

### 12.4 为什么内嵌Moquette而非独立EMQX

- 开发阶段简化部署，单JAR启动
- Moquette轻量级，适合中小规模(<1000设备)
- 生产环境可切换为EMQX集群（接口兼容）

---

## 十三、数据库规划

### 13.1 关系型数据库

- ry-iot: IoT业务数据(产品/设备/数据)
- 字符集: utf8mb4
- 引擎: InnoDB

### 13.2 时序数据库（按需引入）

- TDengine (推荐) 或 InfluxDB
- 触发条件: `iot_device_data` 行数 > 1000万
- 保留策略: 原始数据90天，聚合数据1年

### 13.3 缓存

- Redis: 最新值缓存(`iot:latest:{deviceId}`)、设备在线状态(`iot:status:{deviceId}`)、Stream数据管道(`iot:data:stream`)

---

## 十四、开发前置条件与检查清单

> **关键原则**: 以下每一项都是 IoT 阶段一开始编码前必须完成的前置条件。

### 14.1 基础设施检查清单

| 检查项 | 当前状态 | 说明 |
|--------|----------|------|
| `ruoyi-iot` Maven模块已创建 | ✅ 已完成 | Week 33 完成，参照 `ruoyi-mes` 模块结构创建 |
| `ruoyi-modules/pom.xml` 注册 iot 模块 | ✅ 已完成 | `<module>ruoyi-iot</module>` |
| 根 `pom.xml` 注册 iot 模块 | ✅ 已完成 | dependencyManagement 注册 `ruoyi-iot`，`ruoyi-admin` 引入 |
| `application-dev.yml` 配置 iot 数据源 | ✅ 已完成 | `spring.datasource.dynamic.datasource.iot`（prod 同步占位） |
| `@DS("iot")` 注解可用 | ✅ 已完成 | `IotProductServiceImpl`/`IotDeviceServiceImpl` 标注 `@DS("iot")`，编译通过 |
| `ry-iot` 数据库已创建 | ⏳ 待执行 | DDL 脚本已产出 `script/sql/ry-iot-week33.sql`，由开发者本地执行 |
| 3张核心DDL已执行 | ⏳ 待执行 | iot_product + iot_device + iot_device_data，脚本内含修正版 DDL（见脚本头注释） |
| Moquette依赖已引入 | ✅ 已完成 | `io.moquette:moquette-broker:0.17`（注：0.17 groupId 为 `io.moquette`，非 `io.github.moquette`），T6 已排查 Netty 收敛无冲突 |
| Redis Streams消费者组已创建 | ✅ 已完成 | Week 34 `IotStreamConsumer` 启动时幂等创建 `iot-consumers` 组（`StreamCreateGroupArgs.name(...).id(NEWEST).makeStream()`），BUSYGROUP 幂等忽略 |
| 数据管道(MQTT→Stream→MySQL+缓存) | ✅ 已完成 | Week 34：`IotMqttInterceptHandler.onPublish`→`IotMessagePipelineService`(XADD)→`IotStreamConsumer`(XREADGROUP)→`IotDeviceDataService.batchInsert`+`IotDeviceLatestCache`(HSET) |
| 硬编码告警 | ✅ 已完成 | Week 34 `IotAlertChecker`(阈值 `nexus.iot.pipeline.alert-thresholds`)+`IotAlertNotifier`(限频+租户在线用户 SSE 推送) |
| 遗嘱消息处理 | ✅ 已完成 | Week 34 `onConnect` 缓存 will、`onConnectionLost` 标注 `reason=CONNECTION_LOST` 并清 will |
| MES↔IoT 接口契约 | ✅ 已完成 | Week 34 抽 `ruoyi-common-iot` 公共契约模块：`IotAlert`/`IMesAndonService`/`IotDeviceDataQueryService`；`ruoyi-iot` 提供 no-op 默认实现 |
| SSE 实时数据端点 | ❌ 待完成 | 属 Week 35-36 前端实时仪表盘 |
| SnailJob 定时任务(清理/心跳/Stream修剪) | ❌ 待完成 | 属 Week 36 |

> **Week 33 DDL 修正说明**：与本文档第二节 DDL 的差异已记录在 `script/sql/ry-iot-week33.sql` 头注释——`tenant_id` 改 `VARCHAR(20) DEFAULT '000000'` 对齐项目规范；业务表补 `del_flag` + 审计列；`iot_device_data` 不补审计列（实体独立）。以脚本为准。

### 14.2 已有基础设施确认

| 依赖项 | 来源 | 状态 | 说明 |
|--------|------|------|------|
| Redisson (Redis Streams) | `ruoyi-common-redis` | ✅ 可用 | RStream API |
| SseEmitter | `ruoyi-common-sse` | ✅ 可用 | 实时数据推送 |
| SnailJob | `ruoyi-common-job` | ✅ 可用 | 定时任务 |
| Redis缓存 | `ruoyi-common-redis` | ✅ 可用 | 设备最新值/在线状态 |

### 14.3 MES侧依赖确认（联动阶段）

| 依赖项 | 来源 | 状态 | 说明 |
|--------|------|------|------|
| 设备台账 | `mes/eq/equipment` | ❌ MES Phase 3 | IoT设备关联MES设备 |
| 安灯接口 | `IMesAndonService` | ❌ MES Phase 2 | IoT告警触发MES安灯 |
| 数据采集点 | `mes/dc/collection-point` | ❌ MES Phase 3 | IoT自动采集关联 |

---

## 十五、阶段一最小可运行范围

### 15.1 最小链路定义

**目标**: 3-4周内让一台真实设备的数据出现在前端仪表盘上。

**最小链路**:
```
真实设备 → MQTT Publish → Moquette Broker
  → MqttMessageListener → Redis Stream XADD
    → StreamConsumer XREADGROUP
      ├── 写入 iot_device_data (MySQL)
      ├── HSET iot:latest:{deviceId} {propertyCode} {value}
      └── SET iot:status:{deviceId} ONLINE EX 3600

前端 EventSource("/iot/device/{deviceId}/realtime")
  → 后端从 Redis 读取最新值 → SSE推送
```

### 15.2 阶段一包含范围

| 能力 | 包含 | 说明 |
|------|------|------|
| 产品管理 | ✅ | 产品CRUD |
| 设备管理 | ✅ | 设备CRUD + 在线状态 |
| MQTT接入 | ✅ | Moquette内嵌Broker + 设备认证 |
| 数据管道 | ✅ | MQTT→Redis Streams→MySQL+Redis |
| 实时推送 | ✅ | SSE + Redis最新值缓存 |
| 硬编码告警 | ✅ | StreamConsumer中阈值检查 |
| 定时任务 | ✅ | 数据清理/心跳检测/Stream修剪 |
| 实时仪表盘 | ✅ | SSE+ECharts |

### 15.3 阶段一不包含范围

| 能力 | 不包含 | 触发条件 |
|------|--------|----------|
| OPC UA / Modbus | ❌ | 需要连接PLC/SCADA设备 |
| 规则引擎 | ❌ | 硬编码告警不满足需求 |
| 告警规则表 | ❌ | 需要完整告警生命周期 |
| 时序数据库 | ❌ | iot_device_data 超1000万行 |
| 边缘网关 | ❌ | 设备超100台 |
| 场景联动 | ❌ | 需要设备间自动联动 |
| MES深度联动 | ❌ | MES Phase 3 设备模型稳定后 |

---

## 十六、设备接入与数据链路契约

### 16.1 MQTT连接契约

| 参数 | 值 | 说明 |
|------|------|------|
| Broker地址 | `localhost:1883` | 开发阶段内嵌Moquette |
| QoS | 1 | 至少一次 |
| 认证方式 | deviceCode + deviceSecret | 连接时校验 |
| 遗嘱消息 | 设备离线通知 | 更新 connection_status = OFFLINE |
| ClientID | `{deviceCode}` | 唯一标识 |

### 16.2 Topic契约

| Topic | 方向 | 负载格式 | 说明 |
|-------|------|----------|------|
| `iot/{tenantId}/{deviceId}/property/post` | 设备→平台 | `{"property":"temperature","value":25.5,"timestamp":1234567890}` | 属性上报 |
| `iot/{tenantId}/{deviceId}/property/set` | 平台→设备 | `{"property":"switch","value":"on"}` | 属性设置 |
| `iot/{tenantId}/{deviceId}/event/post` | 设备→平台 | `{"event":"alarm","level":"HIGH","msg":"温度过高"}` | 事件上报 |

### 16.3 消息负载格式

```json
{
  "property": "temperature",
  "value": 25.5,
  "dataType": "NUMERIC",
  "unit": "°C",
  "timestamp": 1234567890,
  "quality": "GOOD"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| property | String | 是 | 属性编码 |
| value | Object | 是 | 属性值（数值/字符串/布尔） |
| dataType | String | 否 | NUMERIC/TEXT/BOOL，默认NUMERIC |
| unit | String | 否 | 单位 |
| timestamp | Long | 否 | 设备采集时间戳（毫秒），缺失则用服务端时间 |
| quality | String | 否 | GOOD/BAD/UNCERTAIN，默认GOOD |

### 16.4 Redis Streams数据格式

| 字段 | 说明 |
|------|------|
| `device_id` | 设备ID |
| `device_code` | 设备编码 |
| `property_code` | 属性编码 |
| `property_name` | 属性名称（可选） |
| `data_type` | 数据类型 |
| `value` | 属性值 |
| `collect_time` | 采集时间 |
| `quality` | 数据质量 |

### 16.5 Redis缓存契约

| Key | 类型 | 字段 | TTL | 说明 |
|-----|------|------|-----|------|
| `iot:latest:{deviceId}` | Hash | `{propertyCode}` → `{value}` | 无（覆盖更新） | 设备最新属性值 |
| `iot:status:{deviceId}` | String | `ONLINE` / `OFFLINE` | 1小时 | 设备在线状态 |
| `iot:data:stream` | Stream | 见16.4 | 由清理任务控制 | 设备数据管道 |

---

## 十七、与MES的接口边界

### 17.1 IoT→MES接口（预留）

| 接口 | 方法 | 路径 | 说明 | 状态 |
|------|------|------|------|------|
| 查询设备最新数据 | GET | `/iot/device/{deviceId}/latest` | MES从Redis缓存读取 | ❌ 阶段二实现 |
| 查询设备在线状态 | GET | `/iot/device/{deviceId}/status` | MES从Redis缓存读取 | ❌ 阶段二实现 |
| 查询设备历史数据 | GET | `/iot/device/{deviceId}/data?start=&end=` | MES查询MySQL历史 | ❌ 阶段二实现 |

### 17.2 IoT→MES安灯接口（预留）

```java
/**
 * MES安灯服务接口（IoT调用MES）
 * IoT发现设备异常时，通过此接口触发MES安灯呼叫
 */
public interface IMesAndonService {
    /**
     * 从IoT告警创建安灯呼叫
     * @param alert IoT告警信息
     * @return 安灯ID
     */
    Long createAndonFromAlert(IotAlert alert);
}
```

**IotAlert 字段：**
```java
public class IotAlert implements Serializable {
    private Long deviceId;       // IoT设备ID
    private String deviceCode;   // 设备编码
    private Long mesEquipmentId; // 关联MES设备ID（可选）
    private String alertType;    // 告警类型：TEMPERATURE/VIBRATION/PRESSURE/OTHER
    private String alertLevel;   // 告警级别：HIGH/MEDIUM/LOW
    private String alertMessage; // 告警描述
    private BigDecimal value;    // 触发值
    private BigDecimal threshold;// 阈值
    private Long tenantId;
}
```

### 17.3 设备关联字段

| 表 | 字段 | 说明 |
|------|------|------|
| `iot_device` | `mes_equipment_id` | 关联MES设备ID（接口查询，无外键） |
| `eq_equipment` | `iot_device_id` | 关联IoT设备ID（接口查询，无外键） |

**关联方式**: 通过REST接口查询，不建外键。两个模块可独立开发和部署。

---

## 十八、详细实施任务表

### 18.1 IoT阶段一详细任务（Week 33-36）

#### Week 33：模块骨架 + MQTT接入（3人天）

| 任务 | 交付物 | 验收标准 |
|------|--------|----------|
| 创建 `ruoyi-iot` Maven模块 | pom.xml + 目录结构 | 模块可编译，无报错 |
| 注册到 `ruoyi-modules/pom.xml` 和根 `pom.xml` | `<module>ruoyi-iot</module>` | `mvn compile` 通过 |
| 配置 `application-dev.yml` iot 数据源 | `spring.datasource.dynamic.datasource.iot` | 启动无报错 |
| 创建 `ry-iot` 数据库 | `CREATE DATABASE ry-iot` | 可连接 |
| 执行3张核心DDL | iot_product + iot_device + iot_device_data | 表结构正确 |
| 创建公共包结构 | `com/nexus/iot/{device,mqtt,pipeline,realtime,task}` | 目录存在 |
| 引入Moquette依赖 | `pom.xml` 添加 moquette-broker | 依赖无冲突 |
| 实现Moquette Broker配置 | `MqttBrokerConfig.java` | Broker启动，监听1883端口 |
| 实现设备认证 | deviceCode + deviceSecret 校验 | 认证通过/拒绝正确 |
| 实现产品CRUD | `IotProductServiceImpl` | 可增删改查产品 |
| 实现设备CRUD | `IotDeviceServiceImpl` | 可增删改查设备 |

#### Week 34：数据管道 + 告警 + 缓存（3人天）

| 任务 | 交付物 | 验收标准 |
|------|--------|----------|
| 实现MqttMessageListener | `IotMqttInterceptHandler.onPublish` 升级 + `IotMessagePipelineService` | 收到MQTT消息并解析 ✅ |
| 实现Redis Stream XADD | 写入 `iot:data:stream`（`StreamAddArgs.entries(Map)`） | Stream中有消息 ✅ |
| 实现StreamConsumer XREADGROUP | `IotStreamConsumer`（pending=ALL/fresh=neverDelivered，毒消息隔离） | 消息被正确消费 ✅ |
| 实现写入 iot_device_data | `IotDeviceDataService.batchInsert`（@DS iot） | 数据库有记录 ✅ |
| 实现Redis缓存更新 | `IotDeviceLatestCache`(HSET `iot:latest`) + `IotDeviceStatusCache` | 缓存有最新值 ✅ |
| 实现硬编码告警阈值检查 | `IotAlertChecker` + `IotAlertNotifier`(限频+租户在线用户SSE) | 超限时日志+推送 ✅ |
| 实现设备连接/断开状态更新 | `IotMqttInterceptHandler` onConnect/Disconnect + 状态缓存 | 在线状态正确 ✅ |
| 实现遗嘱消息处理 | onConnect 缓存 will / onConnectionLost 标注 CONNECTION_LOST | 断开后状态变为OFFLINE ✅ |
| 固定MES↔IoT接口契约 | 抽 `ruoyi-common-iot`：`IotAlert` + `IMesAndonService` + `IotDeviceDataQueryService` | 接口编译通过 ✅ |

#### Week 35-36：前端 + 定时任务 + 联调（6人天）

| 任务 | 交付物 | 验收标准 |
|------|--------|----------|
| 实现SSE实时数据端点 | `GET /iot/device/{deviceId}/realtime` | EventSource连接成功 |
| 实现SSE定时推送 | 从Redis读取最新值→推送 | 前端收到实时数据 |
| 前端：产品管理页 | `iot/product/index.vue` | 可增删改查产品 |
| 前端：设备列表页 | `iot/device/index.vue` | 含在线状态指示灯 |
| 前端：实时数据仪表盘 | `iot/device/realtime.vue` | SSE+ECharts实时刷新 |
| 前端：数值卡片组件 | 设备最新属性值展示 | 数值正确显示 |
| 前端：历史趋势图 | ECharts折线图 | 查询MySQL历史数据 |
| 实现IotDataCleanupTask | SnailJob注册 | 每天凌晨2:00清理90天前数据 |
| 实现IotDeviceHeartbeatTask | SnailJob注册 | 每5分钟检查超时设备 |
| 实现IotStreamTrimTask | SnailJob注册 | 每小时XTRIM MAXLEN 100000 |
| 真实设备联调 | 一台设备接入 | 数据出现在仪表盘 |
| 阶段一集成测试 | 测试报告 | MQTT→Redis→MySQL→SSE 全链路 |

---

## 十九、上线前检查项与验收标准

### 19.1 阶段一验收标准

| 验收项 | 验收标准 | 验证方法 |
|--------|----------|----------|
| 模块启动 | `ruoyi-iot` 模块可独立启动，无报错 | 启动日志无ERROR |
| 数据源切换 | `@DS("iot")` 可正常切换 | CRUD操作返回正确数据 |
| MQTT Broker | Moquette启动，监听1883端口 | MQTT客户端可连接 |
| 设备认证 | deviceCode+deviceSecret校验正确 | 正确凭证连接成功，错误凭证拒绝 |
| 数据管道 | MQTT消息→Redis Stream→MySQL | 数据库有记录 |
| Redis缓存 | `iot:latest:{deviceId}` 有最新值 | Redis查询有数据 |
| 设备在线状态 | 连接→ONLINE，断开→OFFLINE | 状态变更正确 |
| 硬编码告警 | 超阈值→日志+推送 | 超限时有告警 |
| SSE推送 | EventSource连接→收到实时数据 | 前端数据实时更新 |
| 定时任务 | 数据清理/心跳检测/Stream修剪 | SnailJob任务执行正常 |
| 真实设备联调 | 一台设备数据出现在仪表盘 | 端到端验证 |

### 19.2 上线前安全检查

| 检查项 | 说明 |
|--------|------|
| MQTT认证 | 生产环境必须启用设备认证，禁止匿名连接 |
| MQTT端口 | 生产环境考虑TLS加密（端口8883） |
| Redis Stream | 确认Stream长度控制策略（XTRIM） |
| 数据清理 | 确认90天清理策略是否满足业务需求 |
| 告警阈值 | 确认硬编码阈值是否合理 |
| 多租户隔离 | 确认 `tenant_id` 过滤正确 |

---

## 二十、联调与测试策略

### 20.1 测试层次

| 层次 | 说明 | 工具 |
|------|------|------|
| 单元测试 | Service层逻辑测试 | JUnit + Mockito |
| MQTT测试 | Broker连接/消息收发 | MQTTX / mosquitto_pub |
| Redis Streams测试 | Stream读写/消费者组 | redis-cli |
| SSE测试 | 实时推送正确性 | 浏览器EventSource / curl |
| 接口测试 | REST API测试 | Postman / Swagger |
| 集成测试 | 全链路测试 | 真实设备 + 前端页面 |

### 20.2 联调顺序

```
阶段一联调:
  Step 1: Moquette启动 → MQTT客户端连接 → 认证通过
  Step 2: MQTT发布消息 → MqttMessageListener收到 → Redis Stream XADD
  Step 3: StreamConsumer消费 → MySQL写入 + Redis缓存更新
  Step 4: 前端EventSource连接 → SSE推送实时数据
  Step 5: SnailJob执行 → 数据清理/心跳检测/Stream修剪
  Step 6: 真实设备接入 → 数据出现在仪表盘
```

### 20.3 测试数据准备

| 数据 | 说明 |
|------|------|
| 测试产品 | 至少2个产品（DEVICE/GATEWAY） |
| 测试设备 | 至少3个设备（不同产品） |
| MQTT测试消息 | 模拟温度/湿度/压力等属性上报 |
| 告警测试数据 | 超阈值消息（验证告警逻辑） |

### 20.4 MQTT测试命令示例

```bash
# 安装mosquitto客户端
# 发布属性上报消息
mosquitto_pub -h localhost -p 1883 \
  -u "device001" -P "secret123" \
  -t "iot/1/device001/property/post" \
  -m '{"property":"temperature","value":25.5,"dataType":"NUMERIC","unit":"°C"}'

# 发布超温告警消息
mosquitto_pub -h localhost -p 1883 \
  -u "device001" -P "secret123" \
  -t "iot/1/device001/property/post" \
  -m '{"property":"temperature","value":85.0,"dataType":"NUMERIC","unit":"°C"}'

# 发布事件消息
mosquitto_pub -h localhost -p 1883 \
  -u "device001" -P "secret123" \
  -t "iot/1/device001/event/post" \
  -m '{"event":"alarm","level":"HIGH","msg":"温度过高"}'
```
