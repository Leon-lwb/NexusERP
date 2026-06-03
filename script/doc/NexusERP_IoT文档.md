# NexusERP - IoT模块综合文档

> **文档状态**: 📋 规划设计文档 — IoT模块尚未开始实施，本文档为开发前的完整设计方案。
> 当前项目中 `ruoyi-iot` Maven模块尚未创建，`@DS("iot")` 数据源尚未配置，15张数据表DDL尚未执行。
> IoT开发建议在MES模块（Phase 1-2）完成后启动，预计Week 35开始。

## 一、IoT模块总体设计

### 1.1 IoT定位

IoT(Internet of Things)物联网平台，负责设备接入、数据采集、规则引擎、告警通知、边缘网关和场景联动，为MES提供设备数据支撑。

### 1.2 IoT模块结构（8个子模块）

```
ruoyi-iot/
└── com/nexus/iot/
    ├── config/     # 平台配置(租户/全局)
    ├── common/     # 公共组件(物模型/协议适配器基类)
    ├── device/     # 设备管理(产品/设备/分组/标签)
    ├── protocol/   # 协议适配(MQTT/OPC UA/Modbus/HTTP)
    ├── data/       # 数据管道(原始数据/处理数据/时序存储)
    ├── rule/       # 规则引擎(规则定义/执行引擎)
    ├── alert/      # 告警系统(告警规则/告警日志)
    └── scene/      # 场景联动(场景/触发器/动作)
    └── gateway/    # 边缘网关(网关管理/边缘脚本)
```

### 1.3 数据源配置

- 数据库: ry-iot (独立库) — 📋 待创建
- 注解: `@DS("iot")` — 📋 待配置
- 时序数据库: TDengine 或 InfluxDB (设备时序数据) — 📋 待选型部署

### 1.4 数据流架构

```
设备 → 协议适配(MQTT/OPC UA/Modbus/HTTP) → MQ消息 → 规则引擎/数据管道/告警 → 存储(MySQL+TDengine+Redis) → WebSocket → 前端 → MES
```

---

## 二、IoT数据表设计

### 2.1 产品与设备管理

```sql
-- IoT产品(设备模板)
CREATE TABLE iot_product (
    product_id       BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    product_code     VARCHAR(64) NOT NULL,
    product_name     VARCHAR(128) NOT NULL,
    product_type     VARCHAR(32) COMMENT 'DEVICE/GATEWAY',
    protocol_type    VARCHAR(32) NOT NULL COMMENT 'MQTT/OPC_UA/MODBUS/HTTP',
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

-- IoT产品属性(物模型)
CREATE TABLE iot_product_property (
    property_id      BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    product_id       BIGINT NOT NULL,
    property_code    VARCHAR(64) NOT NULL,
    property_name    VARCHAR(128) NOT NULL,
    property_type    VARCHAR(16) NOT NULL COMMENT 'PROPERTY/SERVICE/EVENT',
    data_type        VARCHAR(16) NOT NULL COMMENT 'INT/FLOAT/DOUBLE/STRING/BOOL/ENUM/STRUCT',
    unit             VARCHAR(16),
    access_mode      VARCHAR(16) DEFAULT 'RW' COMMENT 'R/W/RW',
    default_value    VARCHAR(256),
    enum_values      JSON COMMENT '枚举值定义',
    struct_definition JSON COMMENT '结构体定义',
    description      VARCHAR(500),
    create_by        BIGINT,
    create_time      DATETIME,
    update_time      DATETIME,
    PRIMARY KEY (property_id),
    INDEX idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT产品属性(物模型)';

-- IoT设备实例
CREATE TABLE iot_device (
    device_id        BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    device_code      VARCHAR(64) NOT NULL,
    device_name      VARCHAR(128) NOT NULL,
    product_id       BIGINT NOT NULL,
    product_name     VARCHAR(128),
    protocol_type    VARCHAR(32) NOT NULL,
    gateway_id       BIGINT COMMENT '所属网关ID(子设备时)',
    parent_device_id BIGINT COMMENT '父设备ID',
    connection_status VARCHAR(16) DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE',
    last_online_time DATETIME,
    device_secret    VARCHAR(128) COMMENT '设备密钥',
    ip_address       VARCHAR(64),
    firmware_version VARCHAR(64),
    location         VARCHAR(256),
    mes_equipment_id BIGINT COMMENT '关联MES设备ID',
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

-- IoT设备分组
CREATE TABLE iot_device_group (
    group_id         BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    group_name       VARCHAR(128) NOT NULL,
    group_code       VARCHAR(64) NOT NULL,
    parent_group_id  BIGINT COMMENT '父分组',
    description      VARCHAR(500),
    create_by        BIGINT,
    create_time      DATETIME,
    update_time      DATETIME,
    PRIMARY KEY (group_id),
    UNIQUE KEY uk_code (group_code, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT设备分组';

-- IoT设备分组关联
CREATE TABLE iot_device_group_relation (
    id               BIGINT NOT NULL,
    group_id         BIGINT NOT NULL,
    device_id        BIGINT NOT NULL,
    create_time      DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_device (group_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT设备分组关联';

-- IoT设备标签
CREATE TABLE iot_device_tag (
    tag_id           BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    tag_key          VARCHAR(64) NOT NULL,
    tag_value        VARCHAR(256),
    device_id        BIGINT NOT NULL,
    create_time      DATETIME,
    PRIMARY KEY (tag_id),
    INDEX idx_device (device_id),
    INDEX idx_key_value (tag_key, tag_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT设备标签';
```

### 2.2 数据管道

```sql
-- IoT设备原始数据
CREATE TABLE iot_device_data_raw (
    data_id          BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    device_id        BIGINT NOT NULL,
    device_code      VARCHAR(64),
    product_id       BIGINT,
    property_code    VARCHAR(64),
    property_name    VARCHAR(128),
    data_type        VARCHAR(16),
    numeric_value    DECIMAL(14,4),
    text_value       VARCHAR(500),
    bool_value       TINYINT(1),
    collect_time     DATETIME NOT NULL,
    receive_time     DATETIME NOT NULL,
    quality          VARCHAR(16) DEFAULT 'GOOD' COMMENT 'GOOD/BAD/UNCERTAIN',
    source           VARCHAR(32) COMMENT 'MQTT/OPC_UA/MODBUS/HTTP',
    create_time      DATETIME,
    PRIMARY KEY (data_id),
    INDEX idx_device_time (device_id, collect_time),
    INDEX idx_property (device_id, property_code, collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT设备原始数据';

-- IoT设备处理数据(聚合/计算后)
CREATE TABLE iot_device_data_processed (
    data_id          BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    device_id        BIGINT NOT NULL,
    property_code    VARCHAR(64),
    agg_type         VARCHAR(16) COMMENT 'AVG/MAX/MIN/SUM/COUNT',
    agg_value        DECIMAL(14,4),
    agg_period       VARCHAR(16) COMMENT 'MINUTE/HOUR/DAY',
    period_start     DATETIME NOT NULL,
    period_end       DATETIME NOT NULL,
    create_time      DATETIME,
    PRIMARY KEY (data_id),
    INDEX idx_device_period (device_id, property_code, agg_period, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT设备处理数据';
```

### 2.3 协议配置

```sql
-- IoT协议配置
CREATE TABLE iot_protocol_config (
    config_id        BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    protocol_type    VARCHAR(32) NOT NULL COMMENT 'MQTT/OPC_UA/MODBUS/HTTP',
    config_name      VARCHAR(128) NOT NULL,
    config_content   JSON NOT NULL COMMENT '协议连接配置(JSON)',
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (config_id),
    INDEX idx_type (protocol_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT协议配置';

-- IoT协议映射(设备属性→协议地址)
CREATE TABLE iot_protocol_mapping (
    mapping_id       BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    device_id        BIGINT NOT NULL,
    property_id      BIGINT NOT NULL,
    protocol_type    VARCHAR(32) NOT NULL,
    address          VARCHAR(256) NOT NULL COMMENT 'MQTT Topic/OPC UA NodeId/Modbus Register/HTTP URL',
    data_converter   VARCHAR(256) COMMENT '数据转换表达式',
    read_write       VARCHAR(8) DEFAULT 'R' COMMENT 'R/W/RW',
    create_time      DATETIME,
    update_time      DATETIME,
    PRIMARY KEY (mapping_id),
    INDEX idx_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT协议映射';
```

### 2.4 规则引擎

```sql
-- IoT规则
CREATE TABLE iot_rule (
    rule_id          BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    rule_name        VARCHAR(128) NOT NULL,
    rule_type        VARCHAR(16) NOT NULL COMMENT 'ALERT/FORWARD/TRANSFORM',
    description      VARCHAR(500),
    conditions       JSON NOT NULL COMMENT '触发条件(AND/OR组合)',
    actions          JSON NOT NULL COMMENT '执行动作列表',
    enabled          TINYINT(1) DEFAULT 1,
    priority         INT DEFAULT 0,
    last_trigger_time DATETIME,
    trigger_count    INT DEFAULT 0,
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (rule_id),
    INDEX idx_type (rule_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT规则';
```

### 2.5 告警系统

```sql
-- IoT告警规则
CREATE TABLE iot_alert_rule (
    alert_rule_id    BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    rule_name        VARCHAR(128) NOT NULL,
    device_id        BIGINT,
    product_id       BIGINT,
    property_code    VARCHAR(64),
    condition_type   VARCHAR(16) NOT NULL COMMENT 'GT/LT/EQ/NE/BETWEEN/CHANGE',
    threshold_value  DECIMAL(14,4),
    threshold_value2 DECIMAL(14,4) COMMENT 'BETWEEN上限',
    duration_seconds INT DEFAULT 0 COMMENT '持续时长(秒)',
    severity         VARCHAR(16) NOT NULL COMMENT 'INFO/WARNING/CRITICAL/FATAL',
    notify_type      VARCHAR(32) COMMENT 'EMAIL/SMS/WEBHOOK/WEBSOCKET',
    notify_targets   JSON COMMENT '通知目标列表',
    enabled          TINYINT(1) DEFAULT 1,
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (alert_rule_id),
    INDEX idx_device (device_id),
    INDEX idx_severity (severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT告警规则';

-- IoT告警日志
CREATE TABLE iot_alert_log (
    alert_id         BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    alert_rule_id    BIGINT NOT NULL,
    rule_name        VARCHAR(128),
    device_id        BIGINT NOT NULL,
    device_code      VARCHAR(64),
    device_name      VARCHAR(128),
    property_code    VARCHAR(64),
    property_name    VARCHAR(128),
    current_value    DECIMAL(14,4),
    threshold_value  DECIMAL(14,4),
    severity         VARCHAR(16),
    status           VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ACKNOWLEDGED/RESOLVED',
    alert_time       DATETIME NOT NULL,
    acknowledge_time DATETIME,
    resolve_time     DATETIME,
    acknowledge_by   BIGINT,
    resolve_by       BIGINT,
    notify_status    VARCHAR(16) COMMENT 'PENDING/SENT/FAILED',
    notify_detail    VARCHAR(500),
    create_time      DATETIME,
    PRIMARY KEY (alert_id),
    INDEX idx_device (device_id),
    INDEX idx_status (status),
    INDEX idx_time (alert_time),
    INDEX idx_severity (severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT告警日志';
```

### 2.6 边缘网关

```sql
-- IoT网关
CREATE TABLE iot_gateway (
    gateway_id       BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    gateway_code     VARCHAR(64) NOT NULL,
    gateway_name     VARCHAR(128) NOT NULL,
    gateway_type     VARCHAR(32) COMMENT 'EDGE/PROTOCOL_BRIDGE',
    protocol_type    VARCHAR(32) COMMENT '支持的协议类型',
    ip_address       VARCHAR(64),
    port             INT,
    connection_status VARCHAR(16) DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE',
    last_heartbeat   DATETIME,
    firmware_version VARCHAR(64),
    location         VARCHAR(256),
    config_content   JSON COMMENT '网关配置',
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (gateway_id),
    UNIQUE KEY uk_code (gateway_code, tenant_id),
    INDEX idx_status (connection_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT网关';

-- IoT边缘脚本
CREATE TABLE iot_edge_script (
    script_id        BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    gateway_id       BIGINT NOT NULL,
    script_name      VARCHAR(128) NOT NULL,
    script_type      VARCHAR(16) NOT NULL COMMENT 'FILTER/TRANSFORM/AGGREGATE',
    script_content   TEXT NOT NULL COMMENT '脚本内容(JavaScript/Groovy)',
    script_lang      VARCHAR(16) DEFAULT 'JAVASCRIPT',
    version          INT DEFAULT 1,
    enabled          TINYINT(1) DEFAULT 1,
    create_by        BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (script_id),
    INDEX idx_gateway (gateway_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT边缘脚本';
```

### 2.7 场景联动

```sql
-- IoT场景
CREATE TABLE iot_scene (
    scene_id         BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    scene_name       VARCHAR(128) NOT NULL,
    scene_type       VARCHAR(16) NOT NULL COMMENT 'MANUAL/AUTO',
    description      VARCHAR(500),
    enabled          TINYINT(1) DEFAULT 1,
    last_trigger_time DATETIME,
    trigger_count    INT DEFAULT 0,
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (scene_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT场景';

-- IoT场景触发器
CREATE TABLE iot_scene_trigger (
    trigger_id       BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    scene_id         BIGINT NOT NULL,
    trigger_type     VARCHAR(16) NOT NULL COMMENT 'DEVICE/CRON/MANUAL',
    device_id        BIGINT COMMENT '设备触发时',
    property_code    VARCHAR(64),
    condition_type   VARCHAR(16) COMMENT 'GT/LT/EQ/CHANGE',
    threshold_value  DECIMAL(14,4),
    cron_expression  VARCHAR(64) COMMENT '定时触发时',
    create_time      DATETIME,
    PRIMARY KEY (trigger_id),
    INDEX idx_scene (scene_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT场景触发器';

-- IoT场景动作
CREATE TABLE iot_scene_action (
    action_id        BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    scene_id         BIGINT NOT NULL,
    action_type      VARCHAR(16) NOT NULL COMMENT 'DEVICE_COMMAND/ALERT/NOTIFICATION/HTTP_CALLBACK',
    target_device_id BIGINT COMMENT '设备命令时',
    command_code     VARCHAR(64) COMMENT '设备命令编码',
    command_params   JSON COMMENT '命令参数',
    alert_rule_id    BIGINT COMMENT '触发告警时',
    notify_type      VARCHAR(32) COMMENT '通知类型',
    notify_targets   JSON COMMENT '通知目标',
    callback_url     VARCHAR(256) COMMENT 'HTTP回调时',
    callback_method  VARCHAR(8) COMMENT 'HTTP方法',
    callback_body    TEXT COMMENT 'HTTP请求体',
    sequence_num     INT DEFAULT 0 COMMENT '执行顺序',
    create_time      DATETIME,
    PRIMARY KEY (action_id),
    INDEX idx_scene (scene_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT场景动作';
```

---

## 三、协议适配

### 3.1 MQTT协议

- Broker: Moquette(内嵌) 或 EMQX(生产推荐)
- Topic规范: `iot/{tenantId}/{deviceId}/property/post` (属性上报)
- Topic规范: `iot/{tenantId}/{deviceId}/property/set` (属性设置)
- Topic规范: `iot/{tenantId}/{deviceId}/service/invoke` (服务调用)
- Topic规范: `iot/{tenantId}/{deviceId}/event/post` (事件上报)
- QoS: 1(至少一次)
- 认证: deviceCode + deviceSecret
- 遗嘱消息: 设备离线通知

### 3.2 OPC UA协议

- 库: Eclipse Milo
- 连接模式: 客户端模式(连接OPC UA Server)
- 数据订阅: Subscription + MonitoredItem
- 节点映射: iot_protocol_mapping.address = OPC UA NodeId
- 安全模式: None/Sign/SignAndEncrypt

### 3.3 Modbus协议

- 库: modbus4j
- 模式: TCP/RTU
- 寄存器类型: Coil/Discrete/Input/Holding
- 节点映射: iot_protocol_mapping.address = "slaveId:functionCode:offset:length"
- 轮询间隔: 可配置(默认1秒)
- 数据转换: 支持字节序转换和缩放因子

### 3.4 HTTP协议

- 方式: 设备主动上报(POST) / 平台主动拉取(GET)
- 数据格式: JSON
- 认证: Token/Header
- 适合场景: 第三方系统对接、不支持MQTT的设备

### 3.5 协议适配器基类

```java
public abstract class BaseProtocolAdapter {
    /** 初始化连接 */
    public abstract void connect(ProtocolConfig config);

    /** 断开连接 */
    public abstract void disconnect();

    /** 读取属性 */
    public abstract Object readProperty(Long deviceId, String propertyCode);

    /** 写入属性 */
    public abstract void writeProperty(Long deviceId, String propertyCode, Object value);

    /** 订阅属性变化 */
    public abstract void subscribe(Long deviceId, List<String> propertyCodes, Consumer<DeviceData> callback);

    /** 取消订阅 */
    public abstract void unsubscribe(Long deviceId);
}
```

---

## 四、数据管道与时序数据库

### 4.1 数据流处理

```
设备数据 → 协议适配器 → MQ消息(iot.data.exchange) → 数据管道消费者
    ├── 原始数据存储(iot_device_data_raw)
    ├── 时序数据库写入(TDengine/InfluxDB)
    ├── Redis缓存(最新值)
    ├── 规则引擎评估
    └── 告警检查
```

### 4.2 数据聚合

- 聚合类型: AVG/MAX/MIN/SUM/COUNT
- 聚合周期: MINUTE/HOUR/DAY
- 聚合结果存储: iot_device_data_processed

### 4.3 时序数据库

- 推荐: TDengine (高性能、低存储成本)
- 备选: InfluxDB
- 用途: 存储设备历史时序数据
- 超级表设计: 按产品类型建超级表，设备为子表

### 4.4 Redis缓存

- 最新值缓存: `iot:latest:{deviceId}:{propertyCode}` → value
- 设备在线状态: `iot:status:{deviceId}` → ONLINE/OFFLINE
- 缓存TTL: 可配置(默认1小时)

---

## 五、规则引擎

### 5.1 规则类型

- ALERT: 告警触发规则
- FORWARD: 数据转发规则
- TRANSFORM: 数据转换规则

### 5.2 规则条件(conditions JSON)

```json
{
  "logic": "AND",
  "conditions": [
    {
      "type": "DEVICE_PROPERTY",
      "deviceId": 1001,
      "propertyCode": "temperature",
      "operator": "GT",
      "value": 80
    },
    {
      "type": "DEVICE_PROPERTY",
      "deviceId": 1001,
      "propertyCode": "humidity",
      "operator": "LT",
      "value": 30
    }
  ]
}
```

### 5.3 规则动作(actions JSON)

```json
{
  "actions": [
    {
      "type": "ALERT",
      "severity": "CRITICAL",
      "message": "温度过高且湿度过低"
    },
    {
      "type": "DEVICE_COMMAND",
      "deviceId": 2001,
      "commandCode": "turnOnFan",
      "params": { "speed": 3 }
    },
    {
      "type": "HTTP_CALLBACK",
      "url": "http://mes/api/equipment/alert",
      "method": "POST",
      "body": "{\"deviceId\": ${deviceId}, \"alert\": \"${message}\"}"
    }
  ]
}
```

### 5.4 规则执行引擎

- 基于Rete算法或简单条件评估
- 支持AND/OR逻辑组合
- 支持嵌套条件
- 规则优先级排序
- 规则启用/禁用

---

## 六、告警系统

### 6.1 告警规则

- 条件类型: GT(大于)/LT(小于)/EQ(等于)/NE(不等于)/BETWEEN(区间)/CHANGE(变化)
- 严重级别: INFO/WARNING/CRITICAL/FATAL
- 持续时长: 条件持续N秒后触发(避免瞬时波动)
- 通知方式: EMAIL/SMS/WEBHOOK/WEBSOCKET

### 6.2 告警生命周期

```
ACTIVE → ACKNOWLEDGED → RESOLVED
```

### 6.3 告警通知

- WebSocket实时推送(前端)
- 邮件通知
- 短信通知
- Webhook回调(MES/第三方系统)

### 6.4 告警聚合

- 相同设备+相同规则在未解决期间不重复告警
- 告警升级: 超时未确认自动升级严重级别

---

## 七、边缘网关

### 7.1 网关功能

- 协议转换: 将Modbus/OPC UA等协议转换为MQTT上报
- 数据过滤: 边缘侧过滤无效数据
- 数据聚合: 边缘侧聚合后上报
- 离线缓存: 断网时本地缓存，恢复后上报
- 远程配置: 平台远程下发网关配置

### 7.2 边缘脚本

- 脚本类型: FILTER(过滤)/TRANSFORM(转换)/AGGREGATE(聚合)
- 脚本语言: JavaScript/Groovy
- 版本管理: 支持多版本
- 热更新: 平台远程更新脚本

### 7.3 网关管理

- 心跳监控: 定时心跳检测在线状态
- 固件管理: 远程升级固件
- 子设备管理: 网关下的子设备注册和管理

---

## 八、场景联动

### 8.1 场景类型

- MANUAL: 手动触发
- AUTO: 自动触发(条件满足时)

### 8.2 触发器类型

- DEVICE: 设备属性触发
- CRON: 定时触发
- MANUAL: 手动触发

### 8.3 动作类型

- DEVICE_COMMAND: 发送设备命令
- ALERT: 触发告警
- NOTIFICATION: 发送通知
- HTTP_CALLBACK: HTTP回调

### 8.4 场景执行流程

1. 触发器条件评估
2. 条件满足时按顺序执行动作列表
3. 记录执行日志
4. 动作执行失败时记录错误

---

## 九、IoT实施计划

### 9.1 IoT Phase 1 (Week 35-39): 模块创建 + 设备管理 + MQTT + HTTP

| 周次 | 任务 | 人天 |
|------|------|------|
| Week 35 | 创建ruoyi-iot模块+数据源配置+基础框架 | 3 |
| Week 36 | 产品管理+物模型+设备管理CRUD | 5 |
| Week 37 | MQTT协议适配(Moquette内嵌Broker) | 5 |
| Week 38 | HTTP协议适配+设备分组/标签 | 4 |
| Week 39 | Phase 1前端页面+集成测试 | 4 |

### 9.2 IoT Phase 2 (Week 40-44): OPC UA + Modbus + 数据管道 + 时序DB

| 周次 | 任务 | 人天 |
|------|------|------|
| Week 40 | OPC UA协议适配(Eclipse Milo) | 5 |
| Week 41 | Modbus协议适配(modbus4j) | 4 |
| Week 42 | 数据管道(原始数据+处理数据+Redis缓存) | 5 |
| Week 43 | 时序数据库集成(TDengine/InfluxDB) | 4 |
| Week 44 | Phase 2前端页面+实时数据展示 | 4 |

### 9.3 IoT Phase 3 (Week 45-50): 规则引擎 + 告警 + 边缘网关 + 场景联动

| 周次 | 任务 | 人天 |
|------|------|------|
| Week 45-46 | 规则引擎(条件评估+动作执行) | 6 |
| Week 47 | 告警系统(规则+日志+通知) | 5 |
| Week 48 | 边缘网关+边缘脚本 | 4 |
| Week 49 | 场景联动(触发器+动作) | 4 |
| Week 50 | Phase 3前端+全平台集成测试 | 5 |

### 9.4 工作量汇总

| 阶段 | 周次 | 人天 |
|------|------|------|
| Phase 1 | Week 35-39 | 21 |
| Phase 2 | Week 40-44 | 22 |
| Phase 3 | Week 45-50 | 24 |
| **合计** | Week 35-50 (16周) | **67** |

---

## 十、IoT前端规划

### 10.1 目录结构

```
plus-ui/src/views/iot/
├── product/               # 产品管理
│   ├── index.vue          # 产品列表
│   └── property.vue       # 物模型定义
├── device/                # 设备管理
│   ├── index.vue          # 设备列表
│   ├── detail.vue         # 设备详情
│   ├── group.vue          # 设备分组
│   └── tag.vue            # 设备标签
├── protocol/              # 协议配置
│   ├── config.vue         # 协议配置列表
│   └── mapping.vue        # 协议映射
├── data/                  # 数据管理
│   ├── realtime.vue       # 实时数据
│   ├── history.vue        # 历史数据
│   └── aggregate.vue      # 聚合数据
├── rule/                  # 规则引擎
│   └── index.vue          # 规则管理
├── alert/                 # 告警管理
│   ├── rule.vue           # 告警规则
│   └── log.vue            # 告警日志
├── scene/                 # 场景联动
│   ├── index.vue          # 场景管理
│   ├── trigger.vue        # 触发器配置
│   └── action.vue         # 动作配置
└── gateway/               # 边缘网关
    ├── index.vue          # 网关管理
    └── script.vue         # 边缘脚本
```

### 10.2 新增页面估算

- 产品管理: 2页面
- 设备管理: 4页面
- 协议配置: 2页面
- 数据管理: 3页面
- 规则引擎: 1页面
- 告警管理: 2页面
- 场景联动: 3页面
- 边缘网关: 2页面
- 合计: ~19页面

### 10.3 关键前端组件

- 实时数据仪表盘: WebSocket + ECharts实时刷新
- 设备拓扑图: 设备与网关关系可视化
- 告警看板: 实时告警展示
- 规则编辑器: 可视化条件/动作配置
- 物模型编辑器: 属性/服务/事件定义

---

## 十一、IoT与MES集成

### 11.1 数据流向

IoT设备数据 → 数据管道 → MES数据采集模块(mes_dc)

### 11.2 集成方式

- MQ消息: IoT数据管道 → MQ → MES数据采集消费者
- REST API: MES查询IoT设备最新数据
- 共享数据库: IoT设备表关联MES设备表(mes_eq_equipment.iot_device_id)

### 11.3 集成场景

- 设备状态实时监控(MES安灯+IoT告警)
- 设备数据自动采集(IoT→MES数据采集点)
- 设备故障联动(IoT告警→MES安灯呼叫)
- OEE计算(IoT设备运行数据→MES设备OEE)

---

## 十二、技术架构决策

### 12.1 为什么选择RabbitMQ而非Kafka

- IoT当前规模不需要Kafka的高吞吐
- RabbitMQ原生支持延迟消息和死信队列
- 运维成本低，单节点即可
- 与ERP模块MQ基础设施统一

### 12.2 为什么选择TDengine而非InfluxDB

- TDengine写入性能更优(10x+)
- 存储成本更低(压缩率高)
- 原生支持超级表/子表模型，适合设备数据
- 国产开源，社区活跃

### 12.3 为什么内嵌Moquette而非独立EMQX

- 开发阶段简化部署
- Moquette轻量级，适合中小规模
- 生产环境可切换为EMQX集群

### 12.4 与ktg-mes对比

- ktg-mes: Spring Cloud微服务架构，复杂度高
- NexusERP IoT: 单体模块化架构，简单可控
- ktg-mes: 自研MQTT Broker
- NexusERP IoT: Moquette/EMQX，成熟稳定

---

## 十三、数据库规划

### 13.1 关系型数据库

- ry-iot: IoT业务数据(产品/设备/规则/告警/场景)
- 字符集: utf8mb4
- 引擎: InnoDB

### 13.2 时序数据库

- TDengine (推荐) 或 InfluxDB
- 用途: 设备历史时序数据
- 保留策略: 原始数据90天，聚合数据1年

### 13.3 缓存

- Redis: 最新值缓存、设备在线状态、规则引擎缓存
