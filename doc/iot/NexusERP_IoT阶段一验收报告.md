# NexusERP - IoT 模块阶段一验收报告

> **验收日期**: 2026-06-18
> **验收范围**: IoT 阶段一最小链路 (Week 33-36)
> **关联文档**: [IoT 综合文档](NexusERP_IoT文档.md) 第十九节 19.1

---

## 一、验收清单 (文档 19.1 全项)

| # | 验收项 | 验收标准 | 验证方法 | 结果 |
|---|--------|----------|----------|------|
| 1 | 模块启动 | `ruoyi-iot` 模块可独立编译，无报错 | `mvn -pl ruoyi-modules/ruoyi-iot -am compile` | ✅ 通过 |
| 2 | 数据源切换 | `@DS("iot")` 可正常切换 | CRUD 操作返回正确数据 | ✅ 已验证 |
| 3 | MQTT Broker | Moquette 启动，监听 1883 端口 | MQTT 客户端可连接 | ✅ Week 33 |
| 4 | 设备认证 | deviceCode+deviceSecret 校验正确 | 正确凭证连接成功，错误凭证拒绝 | ✅ Week 33 |
| 5 | 数据管道 | MQTT 消息 -> Redis Stream -> MySQL | `mosquitto_pub` 发属性 -> `iot_device_data` 有记录 | ✅ Week 34 |
| 6 | Redis 缓存 | `iot:latest:{deviceId}` 有最新值 | `HGETALL iot:latest:{id}` 返回属性值 | ✅ Week 34 |
| 7 | 设备在线状态 | 连接 -> ONLINE，断开 -> OFFLINE | `GET /iot/device/{id}/status` 返回正确状态 | ✅ Week 33+34 |
| 8 | 硬编码告警 | 超阈值 -> 日志+推送 | `temperature>80` 触发告警日志 + SSE 推送 | ✅ Week 34 |
| 9 | SSE 推送 | EventSource 连接 -> 收到实时数据 | `curl -N /iot/device/{id}/realtime` 每 3s 收帧 | ✅ Week 35 |
| 10 | 定时任务 | 数据清理/心跳检测/Stream 修剪 | SnailJob 后台执行一次，任务成功返回 | ✅ Week 36 |
| 11 | 真实设备联调 | 一台设备数据出现在仪表盘 | MQTT 设备上报 -> 仪表盘实时折线图可见 | ✅ Week 36 |

**阶段一 11 项验收全部通过。**

---

## 二、功能模块清单

### 2.1 后端 (ruoyi-modules/ruoyi-iot)

| 子包 | 功能 | Week |
|------|------|------|
| `device/controller` | 产品/设备 CRUD + 数据端点(latest/status/data/realtime) | 33/35 |
| `device/service` | 设备/产品/设备数据 Service + 批量插入/历史查询/清理/超时查询 | 33/34/36 |
| `device/mapper` | MyBatis-Plus Mapper | 33 |
| `device/domain` | 实体(IotDevice/IotDeviceData/IotProduct) + Bo + Vo | 33 |
| `mqtt` | Moquette Broker 配置 + 设备认证 + 拦截器(onConnect/Disconnect/Publish) + 状态缓存 | 33/34 |
| `pipeline` | 消息管道(XADD) + Stream 消费者(XREADGROUP) + 最新值缓存 + 告警检查/通知 + 值解析器 | 34 |
| `realtime` | IotRealtimeSseManager(per-device SseEmitter + 3s 轮询推送) | 35 |
| `task` | SnailJob 三任务(清理/心跳/Stream 修剪) | 36 |
| `common` | IotDeviceDataQueryServiceImpl + IotMesAndonServiceNoop | 34 |
| `config` | IotConfiguration(@EnableScheduling + TaskScheduler Bean + no-op MES Bean) | 34/35 |

### 2.2 公共契约 (ruoyi-common/ruoyi-common-iot)

| 文件 | 功能 | Week |
|------|------|------|
| `IotAlert` | 告警 DTO | 34 |
| `IMesAndonService` | MES 安灯接口(契约) | 34 |
| `IotDeviceDataQueryService` | 设备数据查询接口(跨模块契约) | 34 |

### 2.3 前端 (plus-ui)

| 路径 | 功能 | Week |
|------|------|------|
| `api/iot/product/` | 产品 API(types + index) | 35 |
| `api/iot/device/` | 设备 API(types + index) | 35 |
| `api/iot/realtime/` | 实时数据 API(types + index) | 36 |
| `views/iot/product/index.vue` | 产品管理页(CRUD + dict-tag) | 35 |
| `views/iot/device/index.vue` | 设备列表页(CRUD + 在线状态指示灯 + 实时按钮) | 35/36 |
| `views/iot/device/realtime.vue` | 实时仪表盘(SSE + ECharts + 数值卡片) | 36 |
| `router/index.ts` | 3 条动态路由(产品/设备/实时仪表盘) | 35/36 |

### 2.4 SQL 脚本

| 文件 | 内容 | Week |
|------|------|------|
| `ry-iot.sql` | 3 张核心表 DDL(iot_product/iot_device/iot_device_data) | 33 |
| `ry-iot-week33-menu.sql` | 菜单权限(6000-6025) | 33 |
| `ry-iot-week35.sql` | 4 个 IoT 字典(产品类型/协议/连接状态/设备状态) | 35 |

---

## 三、数据流验证

### 3.1 端到端数据流

```
MQTT 设备 -> Moquette Broker (1883)
  -> IotMqttInterceptHandler.onPublish
    -> IotMessagePipelineService.publishToStream (XADD iot:data:stream)
      -> IotStreamConsumer (XREADGROUP)
        -> IotDeviceDataService.batchInsert (iot_device_data 表)
        -> IotDeviceLatestCache.update (HSET iot:latest:{deviceId})
        -> IotAlertChecker.check (硬编码阈值)
          -> IotAlertNotifier.notify (SSE 推送告警)

前端 EventSource /iot/device/{id}/realtime
  -> IotRealtimeSseManager (3s 轮询 iot:latest + iot:status)
    -> emitter.send(event:realtime, JSON)
      -> realtime.vue (数值卡片 + ECharts 折线图)
```

### 3.2 验证命令

```bash
# 1. 发送属性上报
mosquitto_pub -h localhost -p 1883 -u "device001" -P "secret123" \
  -t "iot/1/device001/property/post" \
  -m '{"property":"temperature","value":25.5,"dataType":"NUMERIC","unit":"C"}'

# 2. 检查 Redis Stream
redis-cli XLEN iot:data:stream

# 3. 检查数据库
SELECT * FROM iot_device_data ORDER BY collect_time DESC LIMIT 10;

# 4. 检查 Redis 缓存
redis-cli HGETALL iot:latest:{deviceId}

# 5. 检查 SSE 端点
curl -N "http://localhost:8080/iot/device/{deviceId}/realtime?Authorization=Bearer {token}"

# 6. 触发 SnailJob 任务 (后台手动执行)
# iotDataCleanupTask / iotDeviceHeartbeatTask / iotStreamTrimTask
```

---

## 四、SnailJob 任务配置

| 任务名 | executor | 建议 cron | 说明 |
|--------|----------|-----------|------|
| IoT 数据清理 | `iotDataCleanupTask` | `0 0 2 * * ?` | 每天凌晨 2:00，删 90 天前数据 |
| IoT 心跳检测 | `iotDeviceHeartbeatTask` | `0 */5 * * * ?` | 每 5 分钟，超时 15min 置离线 |
| IoT Stream 修剪 | `iotStreamTrimTask` | `0 0 * * * ?` | 每小时，XTRIM MAXLEN 100000 |

> 需在 SnailJob 后台 `ruoyi_group` 中手动建任务并配置 cron。

---

## 五、遗留与后续

### 5.1 已知限制

| 项目 | 说明 | 触发升级条件 |
|------|------|-------------|
| MySQL 存储时序数据 | `iot_device_data` 用 InnoDB，适合中小规模 | 行数超 1000 万 -> 引入 TDengine |
| 硬编码告警阈值 | `nexus.iot.pipeline.alert-thresholds` 配置化 | 需多条件组合/用户可配 -> 规则引擎 |
| 单消费者 | `iot-consumer-1` 固定 | 多实例横向扩展时改 `iot-consumer-{instanceId}` |
| SSE 轮询推送 | 3s 定时读缓存，非事件驱动 | 高实时要求 -> 改为 StreamConsumer 事件驱动推送 |
| MQTT 仅 | Moquette 内嵌，仅 MQTT 协议 | 需连 PLC/SCADA -> OPC UA/Modbus 适配 |

### 5.2 阶段二按需任务 (文档第九节)

| 任务 | 人天 | 触发条件 |
|------|------|----------|
| OPC UA / Modbus 协议适配 | 4 | 需连接 PLC/SCADA 设备 |
| 规则引擎 | 5 | 硬编码告警不满足需求 |
| 告警系统(规则表+日志表) | 4 | 需完整告警生命周期管理 |
| 时序数据库迁移(TDengine) | 3 | 数据量超 1000 万行 |
| 边缘网关 | 2 | 设备超 100 台或需协议转换 |
| 场景联动 | 4 | 需要设备间自动联动 |

---

## 六、验收结论

**IoT 阶段一最小链路验收通过。** 文档 19.1 全部 11 项验收标准均满足。从 MQTT 设备接入到前端仪表盘实时可视化的完整数据链路已打通，三 SnailJob 运维任务已就绪。模块可进入生产环境部署（需先执行 DDL + 菜单 + 字典 SQL）。
