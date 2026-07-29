# IoT 模块入门学习指南

> **面向读者**: 没有接触过 MQTT 的新开发者
> **目标**: 理解 IoT 模块的整体架构、数据流转过程，以及如何基于现有代码新增功能

---

## 目录

1. [IoT 是什么？为什么要做这个模块？](#1)
2. [整体架构和数据流](#2)
3. [MQTT 协议基础](#3)
4. [数据库表设计](#4)
5. [核心代码逐层拆解](#5)
6. [端到端数据流转演练](#6)
7. [Redis 缓存与 Streams](#7)
8. [SSE 实时推送](#8)
9. [扩展开发：新增一种数据采集协议](#9)
10. [常见问题排查](#10)

---

<a name="1"></a>
## 1. IoT 是什么？为什么要做这个模块？

### 1.1 背景

工厂里有大量的设备——温度传感器、压力变送器、电机、PLC 控制器等等。这些设备在生产过程中会产生大量数据（温度多少度、压力多大、是否在运行），但以前这些数据只存在于设备本地，无法被 ERP/MES 系统直接消费。

IoT（Internet of Things）模块的作用就是**把这些设备连上来，采集数据，存起来，推给需要的人看**。

### 1.2 阶段一目标

**用最简单的方式，让一台真实设备的数据出现在前端仪表盘上。**

不追求大而全，只追求链路跑通：

```
设备 → MQTT → 后端接收 → 存入数据库 → 前端实时显示
```

### 1.3 核心能力一览

| 能力 | 说明 |
|------|------|
| 产品管理 | 定义"设备类型"——就像模板，定义了这个产品有哪些可上报的属性 |
| 设备管理 | 每个实际设备对应一条记录，包含连接凭证（设备编码+密钥） |
| MQTT 接入 | 内嵌 MQTT Broker（Moquette），设备通过 TCP 1883 端口连接上报数据 |
| 数据管道 | 收到 MQTT 消息 → 写入 Redis Stream → 消费者批量写入 MySQL |
| 实时推送 | 前端通过 SSE（Server-Sent Events）实时接收设备最新数据 |
| 定时任务 | 数据清理（90天）、心跳检测（15分钟超时离线）、Stream 修剪 |

### 1.4 不做的事（阶段一明确排除）

- 不实现 OPC UA / Modbus 协议（阶段二按需）
- 不做可视化规则引擎（硬编码阈值足够先用）
- 不上时序数据库 TDengine（数据超 1000 万行才迁移）
- 不做边缘网关（设备超 100 台才需要）

---

<a name="2"></a>
## 2. 整体架构和数据流

### 2.1 架构图

```
┌──────────────────────────────────────────────────────────────────┐
│                        前端 (plus-ui)                             │
│                                                                  │
│  ┌─────────────┐    ┌────────────────┐    ┌──────────────────┐  │
│  │ 产品管理页   │    │ 设备管理页      │    │ 实时数据仪表盘    │  │
│  │ el-table CRUD│    │ 在线状态指示灯   │    │ SSE + ECharts    │  │
│  └─────────────┘    └────────────────┘    └──────────────────┘  │
│         ↑                   ↑                      ↑             │
│         │ REST API          │ REST API            │ SSE          │
│         ↓                   ↓                      ↓             │
└─────────┼───────────────────┼──────────────────────┼──────────────┘
          │                   │                      │
┌─────────┼───────────────────┼──────────────────────┼──────────────┐
│         ▼                   ▼                      ▼             │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              ruoyi-iot (Spring Boot 模块)                 │    │
│  │                                                          │    │
│  │  ┌──────────┐  ┌─────────────┐  ┌───────────────────┐   │    │
│  │  │ Controller│  │   Service   │  │  Data Pipeline    │   │    │
│  │  │  REST端点 │  │  业务逻辑    │  │  Redis Stream     │   │    │
│  │  └──────────┘  └─────────────┘  └────────┬──────────┘   │    │
│  │                                           │               │    │
│  │  ┌─────────────────────┐    ┌────────────▼──────────┐   │    │
│  │  │ MQTT Broker         │    │ IotStreamConsumer     │   │    │
│  │  │ (Moquette 内嵌)      │───▶│ XREADGROUP → MySQL    │   │    │
│  │  │ 端口 1883           │    │ + Redis 缓存 + 告警   │   │    │
│  │  └─────────────────────┘    └───────────────────────┘   │    │
│  │           ▲                         │                      │    │
│  │           │ MQTT 协议              │ SSE                  │    │
│  │           │                        │ (3s 轮询推送)         │    │
│  │  ┌────────┴────────┐    ┌─────────▼──────────┐           │    │
│  │  │ MQTT 设备        │    │ IotRealtimeSseManager│           │    │
│  │  │ (温度传感器等)    │    │ SseEmitter 管理      │           │    │
│  │  └─────────────────┘    └─────────────────────┘           │    │
│  └───────────────────────────────────────────────────────────┘    │
│         │                    │                      │               │
│         ▼                    ▼                      ▼               │
│  ┌─────────────┐    ┌─────────────────┐    ┌──────────────┐       │
│  │  ry-iot 库   │    │  Redis          │    │  SnailJob     │       │
│  │  iot_product │    │  iot:latest:{id} │    │  清理/心跳/    │       │
│  │  iot_device  │    │  iot:status:{id} │    │  Stream修剪   │       │
│  │  iot_device_ │    │  iot:data:stream │    │              │       │
│  │    data      │    │                 │    │              │       │
│  └─────────────┘    └─────────────────┘    └──────────────┘       │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 关键组件清单

| 组件 | 文件路径 | 职责 |
|------|---------|------|
| MQTT Broker | `mqtt/MqttBrokerConfig.java` | 启动内嵌 Broker，监听 1883 端口 |
| 设备认证器 | `mqtt/IotMqttAuthenticator.java` | 校验设备编码+密钥 |
| 消息拦截器 | `mqtt/IotMqttInterceptHandler.java` | 监听连接/断开/消息事件 |
| 消息管道 | `pipeline/IotMessagePipelineService.java` | 把 MQTT 消息写入 Redis Stream |
| 流消费者 | `pipeline/IotStreamConsumer.java` | 从 Redis Stream 批量消费 → 入库+缓存+告警 |
| 最新值缓存 | `pipeline/IotDeviceLatestCache.java` | Redis Hash 存储设备最新属性 |
| 在线状态缓存 | `mqtt/IotDeviceStatusCache.java` | Redis String 存储设备在线/离线 |
| SSE 管理 | `realtime/IotRealtimeSseManager.java` | 管理 SSE 连接，3 秒轮询推送 |
| 设备控制器 | `device/controller/IotDeviceController.java` | REST API |
| 产品控制器 | `device/controller/IotProductController.java` | REST API |
| 定时任务 | `task/IotDeviceHeartbeatTask.java` 等 | SnailJob 调度 |

---

<a name="3"></a>
## 3. MQTT 协议基础

### 3.1 什么是 MQTT？

MQTT 是一种**轻量级的消息通信协议**，专为物联网设备设计。它采用 **发布/订阅（Pub/Sub）** 模式：

- 设备（发布者）把数据**发布**到一个叫做 **Topic（主题）** 的地址
- 平台（订阅者）**订阅**这个 Topic，就能收到所有发布到上面的消息

### 3.2 核心概念

| 概念 | 比喻 | 说明 |
|------|------|------|
| Broker | 邮局 | 接收消息并转发给订阅者的中间服务器 |
| Client | 寄信人/收信人 | 连接到 Broker 的设备或应用 |
| Topic | 邮件地址 | 消息的分类标签，如 `iot/1/device001/property/post` |
| QoS | 快递保障等级 | 0=最多一次，1=至少一次，2=恰好一次 |
| Publish | 发邮件 | 设备向 Topic 发送消息 |
| Subscribe | 订阅邮件 | 平台监听 Topic 接收消息 |
| Payload | 邮件内容 | 实际的业务数据（JSON） |

### 3.3 本项目中的 MQTT 配置

本项目使用 **Moquette** 作为内嵌 MQTT Broker（不需要单独部署 MQTT 服务器），启动在 `0.0.0.0:1883`。

**配置位置**: `application.yml` 中 `nexus.iot.mqtt` 段：

```yaml
nexus:
  iot:
    mqtt:
      enabled: true              # 是否启动 Broker
      host: 0.0.0.0              # 监听地址
      port: 1883                 # 监听端口
      allow-anonymous: false     # 禁止匿名连接（必须认证）
      status-ttl-seconds: 3600   # 在线状态 TTL（秒）
```

### 3.4 Topic 规范

本项目约定了一套 Topic 命名规则：

| Topic 格式 | 方向 | 说明 |
|-----------|------|------|
| `iot/{tenantId}/{deviceId}/property/post` | 设备 → 平台 | 属性上报（温度、压力等） |
| `iot/{tenantId}/{deviceId}/property/set` | 平台 → 设备 | 属性设置（下发命令） |
| `iot/{tenantId}/{deviceId}/event/post` | 设备 → 平台 | 事件上报（告警、故障） |

**示例**: 租户 ID 为 `1`、设备编码为 `sensor001` 的属性上报：

```
iot/1/sensor001/property/post
```

### 3.5 消息 Payload 格式

设备上报属性时的 JSON 格式：

```json
{
  "property": "temperature",
  "value": 25.5,
  "dataType": "NUMERIC",
  "unit": "°C",
  "timestamp": 1718822400000,
  "quality": "GOOD"
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| property | 是 | 属性编码（如 `temperature`、`humidity`） |
| value | 是 | 属性值（数值/字符串/布尔） |
| dataType | 否 | NUMERIC / TEXT / BOOL，默认 NUMERIC |
| unit | 否 | 单位（如 `°C`、`kPa`） |
| timestamp | 否 | 设备采集时间戳（毫秒），缺失则用服务端时间 |
| quality | 否 | GOOD / BAD / UNCERTAIN，默认 GOOD |

---

<a name="4"></a>
## 4. 数据库表设计

### 4.1 数据源

IoT 使用独立数据库 **`ry-iot`**，通过 `@DS("iot")` 注解动态切换。

### 4.2 三张核心表

#### (1) iot_product — 产品表（设备模板）

```sql
CREATE TABLE iot_product (
    product_id      bigint(20) NOT NULL COMMENT '产品ID(主键)',
    tenant_id       varchar(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
    product_code    varchar(64) NOT NULL COMMENT '产品编码(唯一)',
    product_name    varchar(128) NOT NULL COMMENT '产品名称',
    product_type    varchar(32) DEFAULT 'DEVICE' COMMENT 'DEVICE/GATEWAY',
    protocol_type   varchar(32) NOT NULL DEFAULT 'MQTT' COMMENT 'MQTT/OPC_UA/MODBUS/HTTP',
    description     varchar(500) COMMENT '描述',
    status          varchar(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    ...审计列...
    PRIMARY KEY (product_id),
    UNIQUE KEY uk_code (product_code, tenant_id)
);
```

**类比**: 类似于工厂里的"设备型号"——定义了这个型号的设备有哪些属性可以上报。

#### (2) iot_device — 设备表（实例）

```sql
CREATE TABLE iot_device (
    device_id           bigint(20) NOT NULL COMMENT '设备ID(主键)',
    tenant_id           varchar(20) NOT NULL DEFAULT '000000',
    device_code         varchar(64) NOT NULL COMMENT '设备编码(唯一, MQTT ClientID)',
    device_name         varchar(128) NOT NULL COMMENT '设备名称',
    product_id          bigint(20) NOT NULL COMMENT '关联产品ID',
    protocol_type       varchar(32) DEFAULT 'MQTT',
    connection_status   varchar(16) DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE',
    last_online_time    datetime COMMENT '最后在线时间',
    device_secret       varchar(128) COMMENT '设备密钥(MQTT认证)',
    ip_address          varchar(64),
    firmware_version    varchar(64),
    location            varchar(256),
    mes_equipment_id    bigint(20) COMMENT '关联MES设备ID(接口查询,无外键)',
    status              varchar(16) DEFAULT 'ACTIVE',
    ...审计列...
    PRIMARY KEY (device_id),
    UNIQUE KEY uk_code (device_code, tenant_id),
    INDEX idx_product (product_id),
    INDEX idx_status (connection_status)
);
```

**关键字段说明**:

| 字段 | 说明 |
|------|------|
| device_code | 设备唯一编码，也是 MQTT 连接的 ClientID |
| device_secret | 设备连接密码（自动生成 UUID），用于 MQTT 认证 |
| connection_status | 在线状态，由 Broker 连接事件驱动更新 |
| product_id | 关联产品表，继承产品的协议类型等信息 |

#### (3) iot_device_data — 设备数据表（时序数据）

```sql
CREATE TABLE iot_device_data (
    data_id          bigint(20) NOT NULL COMMENT '数据ID(主键)',
    tenant_id        varchar(20) NOT NULL DEFAULT '000000',
    device_id        bigint(20) NOT NULL COMMENT '设备ID',
    device_code      varchar(64) COMMENT '设备编码(冗余)',
    property_code    varchar(64) NOT NULL COMMENT '属性编码',
    property_name    varchar(128) COMMENT '属性名称',
    data_type        varchar(16) COMMENT 'NUMERIC/TEXT/BOOL',
    numeric_value    decimal(14,4) COMMENT '数值型值',
    text_value       varchar(500) COMMENT '文本型值',
    bool_value       tinyint(1) COMMENT '布尔型值',
    collect_time     datetime NOT NULL COMMENT '采集时间(设备侧)',
    receive_time     datetime NOT NULL COMMENT '接收时间(服务端)',
    quality          varchar(16) DEFAULT 'GOOD' COMMENT '数据质量',
    source           varchar(32) DEFAULT 'MQTT' COMMENT '数据来源',
    create_time      datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (data_id),
    INDEX idx_device_time (device_id, collect_time),
    INDEX idx_property (device_id, property_code, collect_time)
);
```

**设计要点**:

- 一张设备的一条数据占一行（窄表设计），不把多个属性挤在一行
- 三种数据类型用不同列存储（numeric_value / text_value / bool_value），查询时只取需要的列
- 不补审计列（create_by 等）——这是高频写入的时序数据，减少写入开销

### 4.3 表间关系

```
iot_product (1) ──< (N) iot_device (1) ──< (N) iot_device_data
  模板                    实例                    原始数据
```

---

<a name="5"></a>
## 5. 核心代码逐层拆解

### 5.1 模块结构

```
ruoyi-iot/src/main/java/com/nexus/iot/
├── mqtt/          # MQTT 接入层（Broker + 认证 + 拦截器 + 状态缓存）
├── device/        # 设备管理（产品 CRUD + 设备 CRUD + 数据查询）
├── pipeline/      # 数据管道（MQTT → Redis Stream → MySQL + 缓存 + 告警）
├── realtime/      # 实时推送（SSE 连接管理 + 定时推送）
├── task/          # 定时任务（数据清理 + 心跳检测 + Stream 修剪）
└── config/        # 配置类（Bean 注册 + 线程池）
```

还有一个独立的 **`ruoyi-common-iot`** 模块存放跨模块共享的接口：

```
ruoyi-common-iot/src/main/java/com/nexus/common/iot/
├── dto/IotAlert.java                    # 告警 DTO（IoT → MES 传递）
├── service/IMesAndonService.java        # MES 安灯接口（IoT 调用 MES）
└── service/IotDeviceDataQueryService.java # MES 查询接口（MES 调用 IoT）
```

### 5.2 MQTT Broker 启动 (`MqttBrokerConfig.java`)

```java
@Configuration
@ConditionalOnProperty(prefix = "nexus.iot.mqtt", name = "enabled", havingValue = "true")
public class MqttBrokerConfig {
    @Bean(destroyMethod = "stopServer")
    public Server mqttBrokerServer() throws IOException {
        // 1. 创建 Moquette 配置
        IConfig config = new MemoryConfig(new Properties());
        config.setProperty(IConfig.HOST_PROPERTY_NAME, "0.0.0.0");
        config.setProperty(IConfig.PORT_PROPERTY_NAME, "1883");
        config.setProperty(IConfig.ALLOW_ANONYMOUS_PROPERTY_NAME, "false");

        // 2. 创建 Server 并注册拦截器、认证器、授权策略
        Server server = new Server();
        server.startServer(config, handlers, null, authenticator, authorizatorPolicy);
        return server;
    }
}
```

**关键点**:
- `@ConditionalOnProperty` 允许通过配置开关 Broker
- `destroyMethod = "stopServer"` 确保 Spring 关闭时 Broker 也停止
- 注册了三个核心组件：认证器（谁可以连）、拦截器（连接/消息事件）、授权策略（能访问哪些 Topic）

### 5.3 设备认证 (`IotMqttAuthenticator.java`)

设备连接 MQTT Broker 时需要提供凭证：

| 参数 | 值 | 说明 |
|------|-----|------|
| ClientID | 设备编码（deviceCode） | 唯一标识 |
| Username | 设备编码（deviceCode） | 兼容 Moquette 的 username 参数 |
| Password | 设备密钥（deviceSecret） | 创建设备时自动生成的 UUID |

```java
public boolean checkValid(String clientId, String username, byte[] password) {
    String deviceCode = StringUtils.isNotBlank(username) ? username : clientId;
    String secret = new String(password);

    // 1. 按设备编码查询数据库
    IotDevice device = deviceService.selectEntityByCode(deviceCode);
    if (device == null) return false;           // 设备不存在
    if (!"ACTIVE".equals(device.getStatus())) return false;  // 设备未启用

    // 2. 常量时间比较密钥（防止时序攻击）
    if (!MessageDigest.isEqual(
        device.getDeviceSecret().getBytes(StandardCharsets.UTF_8),
        secret.getBytes(StandardCharsets.UTF_8))) {
        return false;  // 密钥不匹配
    }
    return true;  // 认证通过
}
```

**安全注意**: 使用 `MessageDigest.isEqual()` 而非 `String.equals()` 进行密钥比较，防止时序攻击（攻击者通过比较时间差推测密钥前缀）。

### 5.4 多租户 Topic 授权 (`IotMqttAuthorizatorPolicy.java`)

设备只能访问自己租户范围内的 Topic：

```
Topic 格式: iot/{tenantId}/{deviceCode}/{bizType}/{action}
```

例如租户 `1` 的设备 `sensor001` 只能订阅/发布 `iot/1/sensor001/...`，不能访问 `iot/2/...`。

### 5.5 消息拦截器 (`IotMqttInterceptHandler.java`)

这是 MQTT 事件的"中枢神经系统"，处理四类事件：

```java
public class IotMqttInterceptHandler extends AbstractInterceptHandler {

    // 1. 设备连接 → 更新为 ONLINE + 缓存遗嘱消息
    @Override
    public void onConnect(InterceptConnectMessage msg) {
        updateStatus(deviceCode, true);  // DB + Redis 更新
        if (msg.isWillFlag()) {
            statusCache.storeWill(deviceId, willPayload);  // 缓存遗嘱
        }
    }

    // 2. 设备正常断开 → 更新为 OFFLINE
    @Override
    public void onDisconnect(InterceptDisconnectMessage msg) {
        updateStatus(deviceCode, false);
    }

    // 3. 设备异常断开（网络中断等）→ 更新为 OFFLINE
    @Override
    public void onConnectionLost(InterceptConnectionLostMessage msg) {
        updateStatus(deviceCode, false);
    }

    // 4. 设备发布消息 → 交给数据管道
    @Override
    public void onPublish(InterceptPublishMessage msg) {
        messagePipelineService.publishToStream(deviceCode, topic, body);
    }
}
```

### 5.6 数据管道 — 生产者 (`IotMessagePipelineService.java`)

收到 MQTT 消息后，先解析 Topic，提取关键信息，再写入 Redis Stream：

```java
public void publishToStream(String deviceCode, String topic, String body) {
    try {
        // 1. 解析 Topic → 获取 tenantId, deviceCode, bizType
        IotTopicInfo topicInfo = IotMqttTopicParser.parse(topic);
        if (topicInfo == null) { log.warn("非法 Topic: {}", topic); return; }

        // 2. 解析 JSON Payload → 提取 property, value, dataType
        IotPropertyPayload payload = JsonUtils.parseObject(body, IotPropertyPayload.class);

        // 3. 构建 Redis Stream 条目
        Map<Object, Object> entries = new HashMap<>();
        entries.put("device_id", deviceId);
        entries.put("device_code", deviceCode);
        entries.put("property_code", payload.getProperty());
        entries.put("value", JsonUtils.toJsonString(payload.getValue()));
        entries.put("data_type", payload.getDataType());
        entries.put("collect_time", System.currentTimeMillis());
        entries.put("topic_tenant_id", topicInfo.getTenantId());

        // 4. XADD 写入 Redis Stream
        RStream<Object, Object> stream = redissonClient.getStream(STREAM_KEY);
        stream.add(entries);
    } catch (Exception e) {
        log.error("管道写入异常", e);  // 异常被吞掉，不影响 Broker
    }
}
```

**Redis Stream 键**: `iot:data:stream`

### 5.7 数据管道 — 消费者 (`IotStreamConsumer.java`)

这是管道的心脏，从 Redis Stream 消费消息并持久化：

```java
@Component
public class IotStreamConsumer {

    @PostConstruct
    public void init() {
        // 1. 获取 Redis Stream
        stream = RedisUtils.getClient().getStream("iot:data:stream");

        // 2. 幂等创建消费者组（BUSYGROUP 视为已存在，不报错）
        stream.createGroup(StreamCreateGroupArgs.name("iot-consumers")
            .id(StreamMessageId.NEWEST).makeStream());

        // 3. 启动单线程消费循环
        executor = Executors.newSingleThreadExecutor();
        executor.submit(this::consumeLoop);
    }

    private void consumeLoop() {
        while (running) {
            // 4. 优先回收 pending（异常重启后未 ACK 的消息）
            Map<StreamMessageId, Map<Object, Object>> messages =
                stream.readGroup("iot-consumers", "consumer-1", pendingArgs);

            // 5. 无 pending 则拉取新消息（BLOCK 阻塞等待）
            if (messages.isEmpty()) {
                messages = stream.readGroup("iot-consumers", "consumer-1", freshArgs);
            }

            // 6. 处理这批消息
            processBatch(messages);
        }
    }

    private void processBatch(Map<StreamMessageId, Map<Object, Object>> messages) {
        // 按设备编码批量查设备（每个 code 只查一次 DB）
        Map<String, IotDevice> deviceMap = queryDevices(messages);

        // 组装实体
        List<IotDeviceData> entities = buildEntities(messages, deviceMap);

        // 批量落库；失败则回退逐条（隔离毒消息）
        try {
            deviceDataService.batchInsert(entities);
            ackAll(messages);  // XACK 确认
        } catch (Exception e) {
            // 逐条插入，找出是哪条出了问题
            insertOneByOne(entities, messageIds);
        }
    }
}
```

**消费者组概念**:

Redis Stream 的消费者组（Consumer Group）是一种**多消费者并行消费模式**，类似于 Kafka 的 Consumer Group：
- 组内所有消费者**平分**消息，每条消息只被一个消费者处理
- 消费者处理完消息后需要 **XACK** 确认，未确认的消息可以被其他消费者"回收"（`>` vs `ALL` 读取）

本项目使用单消费者，逻辑更简单，但保留了消费者组框架以便将来扩展。

### 5.8 Redis 缓存

两种缓存结构：

| Key | 类型 | 用途 | TTL |
|-----|------|------|-----|
| `iot:latest:{deviceId}` | **Hash** | 设备最新属性值 | 无（覆盖更新） |
| `iot:status:{deviceId}` | **String** | 设备在线状态 | 在线 1 小时 / 离线 30 秒 |

```java
// 写入最新值（IotDeviceLatestCache）
// Key: iot:latest:123, Field: temperature, Value: "25.5"
redisTemplate.opsForHash().put("iot:latest:" + deviceId, propertyCode, value);

// 读取最新值
Map<Object, Object> props = redisTemplate.opsForHash().entries("iot:latest:" + deviceId);
```

### 5.9 实时推送 — SSE (`IotRealtimeSseManager.java`)

前端通过 `EventSource` 连接到 `/iot/device/{deviceId}/realtime`，后端每 3 秒从 Redis 缓存读取最新值推送给前端：

```java
@Component
public class IotRealtimeSseManager {
    // deviceId → (会话键 → SseEmitter)
    private final Map<Long, Map<String, SseEmitter>> deviceEmitters = new ConcurrentHashMap<>();

    // 建立 SSE 连接
    public SseEmitter connect(Long deviceId, String sessionKey) {
        SseEmitter emitter = new SseEmitter(0L);  // 不超时
        sessions.put(sessionKey, emitter);
        emitter.onCompletion(() -> removeSession(deviceId, sessionKey));
        emitter.onTimeout(() -> removeSession(deviceId, sessionKey));
        return emitter;
    }

    // 每 3 秒推送一次
    @Scheduled(fixedRate = 3000, scheduler = "iotTaskScheduler")
    public void pushRealtimeData() {
        for (Map.Entry<Long, Map<String, SseEmitter>> entry : deviceEmitters.entrySet()) {
            Long deviceId = entry.getKey();
            Map<String, String> properties = latestCache.getAll(deviceId);
            String status = statusCache.get(deviceId);

            // 构建 JSON 快照
            Map<String, Object> payload = Map.of(
                "deviceId", deviceId,
                "timestamp", System.currentTimeMillis(),
                "connectionStatus", status,
                "properties", properties
            );

            // 推送给所有订阅者
            for (SseEmitter emitter : entry.getValue().values()) {
                emitter.send(SseEmitter.event().name("realtime").data(payload));
            }
        }
    }
}
```

**推送频率**: 3 秒一次。频率由 `application.yml` 中的 `@Scheduled(fixedRate = 3000)` 控制。

### 5.10 定时任务（SnailJob）

三个 SnailJob 任务：

| 任务 | 频率 | 功能 |
|------|------|------|
| `IotDataCleanupTask` | 每天凌晨 2:00 | 删除超过 90 天的历史数据 |
| `IotDeviceHeartbeatTask` | 每 5 分钟 | 标记超时（15 分钟未更新）的在线设备为 OFFLINE |
| `IotStreamTrimTask` | 每小时 | 修剪 Redis Stream 长度到 10 万条，防止内存溢出 |

---

<a name="6"></a>
## 6. 端到端数据流转演练

让我们追踪一条真实数据从设备到前端的过程。

### 场景：温度传感器上报 25.5°C

#### Step 1: 设备连接 Broker

```
设备(sensor001) → TCP 1883 → Moquette Broker
   ClientID: sensor001
   Username: sensor001
   Password: abc123-xxx-xxx
```

**代码路径**: `IotMqttAuthenticator.checkValid()`

```java
// 查询设备表，校验密钥
IotDevice device = deviceService.selectEntityByCode("sensor001");
// device_secret = "abc123-xxx-xxx"  → 匹配成功 → 认证通过
```

认证通过后，触发 `onConnect` → 设备状态更新为 ONLINE。

#### Step 2: 设备发布消息

```
设备 → MQTT Publish
  Topic:   iot/1/sensor001/property/post
  Payload: {"property":"temperature","value":25.5,"dataType":"NUMERIC","unit":"°C"}
```

**代码路径**: `IotMqttInterceptHandler.onPublish()` → `IotMessagePipelineService.publishToStream()`

```java
// 解析 Topic
IotTopicInfo info = IotMqttTopicParser.parse("iot/1/sensor001/property/post");
// → tenantId="1", deviceCode="sensor001", bizType="property", action="post"

// 解析 Payload
IotPropertyPayload payload = {"property":"temperature","value":25.5,"dataType":"NUMERIC"}

// XADD 写入 Redis Stream
stream.add(Map.of(
    "device_id", 42,
    "device_code", "sensor001",
    "property_code", "temperature",
    "value", "25.5",
    "data_type", "NUMERIC",
    "collect_time", 1718822400000
));
```

#### Step 3: 消费者批量消费

```
Redis Stream "iot:data:stream" → IotStreamConsumer (XREADGROUP)
```

**代码路径**: `IotStreamConsumer.consumeLoop()` → `processBatch()`

```java
// 1. 从 Redis Stream 读取一批消息（最多 100 条）
Map<StreamMessageId, Map<Object, Object>> messages =
    stream.readGroup("iot-consumers", "consumer-1", args);

// 2. 按 device_code 批量查询设备（避免 N+1 查询）
Map<String, IotDevice> deviceMap = deviceService.selectEntityByCode("sensor001");

// 3. 组装 IotDeviceData 实体
IotDeviceData data = new IotDeviceData();
data.setDeviceId(42);
data.setPropertyCode("temperature");
data.setDataType("NUMERIC");
data.setNumericValue(new BigDecimal("25.5"));
data.setCollectTime(new Date(1718822400000));

// 4. 批量写入 MySQL
deviceDataService.batchInsert(List.of(data));

// 5. 更新 Redis 最新值缓存
latestCache.update(data);
// → HSET iot:latest:42 temperature "25.5"

// 6. 告警检查
alertChecker.check(data);
// → temperature(25.5) < threshold(80) → 不告警

// 7. XACK 确认消费
stream.ack("iot-consumers", messageId);
```

#### Step 4: SSE 推送到前端

```
后端 IotRealtimeSseManager (每 3s 轮询) → Redis iot:latest:42 → SseEmitter → 前端
```

**代码路径**: `IotRealtimeSseManager.pushRealtimeData()`

```java
// 从 Redis 读取最新值
Map<String, String> properties = latestCache.getAll(42);
// → {"temperature": "25.5"}

String status = statusCache.get(42);
// → "ONLINE"

// 推送 JSON 快照
String json = JsonUtils.toJsonString(Map.of(
    "deviceId", 42,
    "timestamp", 1718822403000,
    "connectionStatus", "ONLINE",
    "properties", Map.of("temperature", "25.5")
));
emitter.send(SseEmitter.event().name("realtime").data(json));
```

#### Step 5: 前端接收并展示

**代码路径**: `plus-ui/src/views/iot/device/realtime.vue`

```javascript
const eventSource = new EventSource('/iot/device/42/realtime?Authorization=Bearer xxx');

eventSource.addEventListener('realtime', (e) => {
    const frame = JSON.parse(e.data);
    // frame = { deviceId: 42, connectionStatus: "ONLINE", properties: { temperature: "25.5" } }

    // 更新状态指示灯
    connectionStatus.value = frame.connectionStatus;  // "ONLINE" → 绿色标签

    // 更新数值卡片
    latestProps.value = frame.properties;  // { temperature: "25.5" }

    // 追加到实时折线图（60 点滚动窗口）
    realtimeData['temperature'].times.push(time);
    realtimeData['temperature'].values.push(25.5);
    if (realtimeData['temperature'].times.length > 60) {
        realtimeData['temperature'].times.shift();
        realtimeData['temperature'].values.shift();
    }
});
```

---

<a name="7"></a>
## 7. Redis 缓存与 Streams

### 7.1 为什么用 Redis Streams 而不是 RabbitMQ？

| 维度 | RabbitMQ | Redis Streams |
|------|---------|--------------|
| 新增基础设施 | 需要新 vhost + exchange + queue | 零新增，复用现有 Redis |
| 消费者组 | 原生支持 | 原生支持（XREADGROUP） |
| 运维成本 | 独立中间件 | 复用 Redis 实例 |
| 适用场景 | 复杂路由 | 简单发布订阅/流处理 |

IoT 的数据上报属于简单的发布订阅，Redis Streams 完全够用。

### 7.2 Redis Stream 关键命令

```bash
# 写入消息
XADD iot:data:stream * device_id 42 property_code temperature value 25.5

# 创建消费者组
XGROUP CREATE iot:data:stream iot-consumers $ MKSTREAM

# 消费消息（消费者组模式）
XREADGROUP GROUP iot-consumers consumer-1 COUNT 100 BLOCK 1000 STREAMS iot:data:stream >

# 确认消费
XACK iot:data:stream iot-consumers 1718822400000-0

# 修剪 Stream 长度
XTRIM iot:data:stream MAXLEN 100000
```

### 7.3 三种 Redis 数据结构对比

| 数据结构 | 用途 | 示例 |
|---------|------|------|
| **Stream** | 数据管道（先进先出队列） | `iot:data:stream` |
| **Hash** | 设备最新属性值（字段 → 值） | `iot:latest:42` → `{temperature: "25.5", humidity: "60"}` |
| **String** | 设备在线状态（简单 key-value） | `iot:status:42` → `"ONLINE"` |

---

<a name="8"></a>
## 8. SSE 实时推送

### 8.1 为什么用 SSE 而不是 WebSocket？

| 维度 | WebSocket | SSE |
|------|-----------|-----|
| 通信方向 | 双向 | 单向（服务端 → 客户端） |
| 复杂度 | 需要连接管理/心跳/重连 | 基于 HTTP，浏览器原生支持 |
| 自动重连 | 需要自己实现 | `EventSource` 自动重连 |
| IoT 需求 | 实时数据推送（单向即可） | 完全满足 |

IoT 只需要服务端向客户端推送数据，不需要客户端发消息给服务端，SSE 更轻量。

### 8.2 SSE 连接流程

```
前端                              后端
  |                                 |
  |── new EventSource("/realtime") ──▶ IotDeviceController.realtime()
  |                                 │   创建 SseEmitter
  |                                 │   注册到 IotRealtimeSseManager
  |◀── SseEmitter (HTTP 200) ──────│
  |                                 |
  |──── 每 3 秒推送 ───────────────▶ pushRealtimeData()
  |◀── event: realtime ─────────────│  {"deviceId":42, "connectionStatus":"ONLINE",
  |     data: {...}                  │   "properties":{"temperature":"25.5"}}
  |                                 |
  |──── 设备断开 / 页面关闭 ────────▶ onCompletion() → 清理会话
```

### 8.3 前端 SSE 代码要点

```javascript
// 创建连接
const eventSource = new EventSource(url);

// 监听事件
eventSource.addEventListener('realtime', (e) => {
    const data = JSON.parse(e.data);
    // 更新 UI
});

// 处理断开
eventSource.onerror = () => {
    if (eventSource.readyState === EventSource.CLOSED) {
        // 永久关闭（如 401），停止重连
        eventSource.close();
    }
    // 否则 EventSource 自动重连
};
```

---

<a name="9"></a>
## 9. 扩展开发指南

### 9.1 场景：新增一种数据采集协议（如 HTTP 推送）

IoT 的管道设计天然支持多协议。如果设备不是通过 MQTT 上报，而是通过 HTTP POST，只需：

**第一步：新增 HTTP 接收端点**

```java
// 在 pipeline 包下新增
@RestController
@RequestMapping("/iot/ingest/http")
public class IotHttpIngestController {

    @PostMapping("/{deviceCode}")
    public void ingest(@PathVariable String deviceCode, @RequestBody String body) {
        // 复用已有的消息管道
        messagePipelineService.publishToStream(deviceCode,
            "iot/0/" + deviceCode + "/property/post", body);
    }
}
```

**第二步：在产品表中增加协议类型**

产品表已有 `protocol_type` 字段，值为 `MQTT/OPC_UA/MODBUS/HTTP`。创建产品时选择 `HTTP` 即可。

**第三步：设备认证适配**

HTTP 方式不使用 MQTT 的 `checkValid`，而是在 HTTP 端点中用 Token 或 API Key 认证。这可以在 `IotHttpIngestController` 中自行实现。

**第四步：无侵入原则**

不需要修改 `IotStreamConsumer`（消费者不关心数据来源），不需要修改数据库表，不需要修改前端。这就是管道架构的优势——**生产者和消费者解耦**。

### 9.2 场景：新增告警规则

当前告警是硬编码的（`IotAlertChecker` 中写死阈值）。如需支持用户配置：

**第一步：新建告警规则表**

```sql
CREATE TABLE iot_alert_rule (
    rule_id       bigint(20) NOT NULL,
    tenant_id     varchar(20) NOT NULL DEFAULT '000000',
    device_id     bigint(20) NOT NULL,
    property_code varchar(64) NOT NULL,
    condition     varchar(16) NOT NULL COMMENT 'GT/LT/GE/LE/EQ',
    threshold     decimal(14,4) NOT NULL,
    alert_level   varchar(16) NOT NULL COMMENT 'HIGH/MEDIUM/LOW',
    enabled       char(1) DEFAULT '1',
    ...审计列...
    PRIMARY KEY (rule_id)
);
```

**第二步：改造 IotAlertChecker**

从硬编码 `alertThresholds` 改为从数据库查询规则：

```java
// 旧：硬编码
BigDecimal threshold = properties.getAlertThresholds().get(propertyCode);

// 新：查询规则表
List<IotAlertRule> rules = alertRuleService.selectByDevice(deviceId);
for (IotAlertRule rule : rules) {
    if (RuleEvaluator.evaluate(value, rule.getCondition(), rule.getThreshold())) {
        // 触发告警
    }
}
```

### 9.3 场景：新增前端页面

遵循项目现有模式，以新增"规则管理页"为例：

**第一步：后端 Controller + Service**

```java
// ruoyi-iot/src/main/java/com/nexus/iot/alert/controller/IotAlertRuleController.java
@RestController
@RequestMapping("/iot/alert/rule")
@RequiredArgsConstructor
public class IotAlertRuleController {
    private final IIotAlertRuleService ruleService;
    // CRUD 端点...
}
```

**第二步：前端 API 层**

```typescript
// plus-ui/src/api/iot/alert/rule/index.ts
export function listRule(query: any) { return request({ url: '/iot/alert/rule/list', method: 'get', params: query }) }
export function addRule(data: any) { return request({ url: '/iot/alert/rule', method: 'post', data }) }
```

**第三步：前端页面**

```typescript
// plus-ui/src/views/iot/alert/rule/index.vue
// 参考 plus-ui/src/views/iot/device/index.vue 的 el-table CRUD 模式
```

**第四步：路由和菜单**

在 `router/index.ts` 中添加路由（参考 643-685 行的 IoT 路由），在 `script/sql/` 中添加菜单 SQL。

---

<a name="10"></a>
## 10. 常见问题排查

### Q1: 设备连不上 MQTT Broker

**排查步骤**:

1. 确认 Broker 已启动：查看启动日志是否有 `IoT MQTT Broker 已启动：host=0.0.0.0, port=1883`
2. 确认端口未被占用：`netstat -ano | findstr 1883`
3. 确认设备凭证正确：在 `iot_device` 表中查看 `device_code` 和 `device_secret`
4. 确认设备状态为 ACTIVE：`SELECT device_code, status FROM iot_device WHERE device_code = 'xxx'`
5. 查看认证日志：搜索 `MQTT 认证失败` 关键词

### Q2: 设备发消息了但数据库没有数据

**排查步骤**:

1. 确认 Redis Stream 有消息：`redis-cli XRANGE iot:data:stream - + COUNT 5`
2. 确认消费者组存在：`redis-cli XINFO GROUPS iot:data:stream`
3. 查看消费者日志：搜索 `IotStreamConsumer` 关键词
4. 检查是否是"毒消息"：日志中是否有 `毒消息重试` 或 `ACK 跳过`
5. 查看 Redis Stream pending 列表：`redis-cli XPENDING iot:data:stream iot-consumers`

### Q3: 前端实时仪表盘没有数据

**排查步骤**:

1. 确认 SSE 连接成功：浏览器 DevTools → Network → 查找 `/realtime` 请求，状态码 200
2. 确认 Redis 有最新值缓存：`redis-cli HGETALL iot:latest:42`（替换为实际 deviceId）
3. 确认设备在线：`redis-cli GET iot:status:42` → 应为 `ONLINE`
4. 查看 SSE 推送日志：搜索 `pushRealtimeData`
5. 如果连接断了：检查 Token 是否过期

### Q4: 设备状态一直是 OFFLINE

**排查步骤**:

1. 确认设备确实连接到了 Broker
2. 检查 `IotDeviceStatusCache` 是否正常：确认 Redis 中 `iot:status:42` 的值
3. 检查心跳任务是否正常运行：SnailJob 后台查看任务执行记录
4. 确认 `last_online_time` 被正确更新：`SELECT last_online_time FROM iot_device WHERE device_id = 42`

### Q5: Redis Stream 内存不断增长

**排查步骤**:

1. 确认 Stream 修剪任务正常运行：`redis-cli XLEN iot:data:stream` 查看长度
2. 检查 SnailJob 中 `IotStreamTrimTask` 的执行记录
3. 如果任务没执行，可手动执行：`redis-cli XTRIM iot:data:stream MAXLEN 100000`

### Q6: 如何本地测试 MQTT 消息？

使用 `mosquitto_pub` 命令行工具：

```bash
# 安装 mosquitto 客户端
# Windows: 下载 mosquitto 安装包
# macOS: brew install mosquitto
# Linux: apt install mosquitto-clients

# 发布属性上报消息
mosquitto_pub -h localhost -p 1883 \
  -u "sensor001" -P "设备密钥(从数据库查)" \
  -t "iot/1/sensor001/property/post" \
  -m '{"property":"temperature","value":25.5,"dataType":"NUMERIC","unit":"°C"}'

# 发布超温告警（测试告警阈值 80）
mosquitto_pub -h localhost -p 1883 \
  -u "sensor001" -P "设备密钥" \
  -t "iot/1/sensor001/property/post" \
  -m '{"property":"temperature","value":85.0,"dataType":"NUMERIC","unit":"°C"}'
```

---

## 附录：核心文件索引

### 后端文件

| 文件 | 路径 | 说明 |
|------|------|------|
| MQTT Broker 配置 | `ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/mqtt/MqttBrokerConfig.java` | 启动内嵌 Broker |
| 设备认证器 | `.../mqtt/IotMqttAuthenticator.java` | 设备连接时校验凭证 |
| 消息拦截器 | `.../mqtt/IotMqttInterceptHandler.java` | 监听连接/断开/消息事件 |
| 消息管道（生产者） | `.../pipeline/IotMessagePipelineService.java` | MQTT 消息 → Redis Stream |
| 流消费者 | `.../pipeline/IotStreamConsumer.java` | Redis Stream → MySQL + 缓存 + 告警 |
| 最新值缓存 | `.../pipeline/IotDeviceLatestCache.java` | Redis Hash 缓存 |
| 在线状态缓存 | `.../mqtt/IotDeviceStatusCache.java` | Redis String 缓存 |
| SSE 管理 | `.../realtime/IotRealtimeSseManager.java` | SSE 连接管理 + 定时推送 |
| 设备控制器 | `.../device/controller/IotDeviceController.java` | REST API（含 SSE 端点） |
| 设备实体 | `.../device/domain/IotDevice.java` | 设备表实体 |
| 设备数据实体 | `.../device/domain/IotDeviceData.java` | 设备数据表实体 |

### 前端文件

| 文件 | 路径 | 说明 |
|------|------|------|
| 设备管理页 | `plus-ui/src/views/iot/device/index.vue` | 设备列表 + CRUD |
| 实时仪表盘 | `plus-ui/src/views/iot/device/realtime.vue` | SSE + ECharts 实时图表 |
| 产品管理页 | `plus-ui/src/views/iot/product/index.vue` | 产品 CRUD |
| 设备 API | `plus-ui/src/api/iot/device/index.ts` | 设备接口调用 |
| 实时数据 API | `plus-ui/src/api/iot/realtime/index.ts` | 最新值/历史数据查询 |

### 配置文件

| 文件 | 说明 |
|------|------|
| `application.yml` (nexus.iot 段) | MQTT Broker + 管道配置 |
| `application-dev.yml` (datasource.iot) | IoT 数据库连接配置 |
| `doc/iot/sql/archive/iot-week33-01-init.sql` | 3 张核心表 DDL |
| `doc/iot/sql/archive/iot-week35-03-dict.sql` | 4 个 IoT 字典初始化 |

### 公共契约模块

| 文件 | 路径 | 说明 |
|------|------|------|
| IotAlert DTO | `ruoyi-common/ruoyi-common-iot/.../dto/IotAlert.java` | 告警数据传递 |
| IMesAndonService | `ruoyi-common/ruoyi-common-iot/.../service/IMesAndonService.java` | IoT → MES 安灯接口 |
| IotDeviceDataQueryService | `ruoyi-common/ruoyi-common-iot/.../service/IotDeviceDataQueryService.java` | MES → IoT 数据查询 |
