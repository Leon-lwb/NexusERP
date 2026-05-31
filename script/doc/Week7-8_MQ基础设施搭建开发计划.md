# Week 7-8: MQ 基础设施搭建开发计划

> 编制日期: 2026-05-31
> 依据: nexus-erp 代码库深度分析 + 跨模块通信现状调研
> 目标里程碑: M5 (MQ基建完成)
> 预计工期: 2周 (~10人天)

---

## 一、现状分析与问题诊断

### 1.1 当前跨模块通信方式

| 通信方式 | 使用情况 | 风险等级 | 说明 |
|---------|---------|---------|------|
| 直接注入其他模块 Mapper | **大量使用** (15+ 处) | 🔴 高 | 绕过 Service 层业务校验，破坏模块封装 |
| 通过 Service 接口调用 | 部分使用 | 🟢 低 | 如 IWmStockOperationService，但仍有跨数据源事务风险 |
| Spring 事件机制 | **ERP 模块零使用** | 🔴 高 | 无松耦合通信手段 |
| 异步任务协调 | 仅 2 个单模块内部任务 | 🟡 中 | SnailJob 定时任务，无实时异步能力 |

### 1.2 核心问题

```
问题1: 双向依赖循环
  wm <--Mapper--> prd  (PrdBackflushServiceImpl 注入 3 个 wm Mapper)
  wm <--Mapper--> pu   (WmMaterialReceiveServiceImpl 注入 2 个 pu Mapper)
  wm <--Strategy--> prd (委外/返工策略注入 prd Mapper)

问题2: 事务粒度过大
  审批通过 → 入库过账 → 采购回写 (5-6层调用，同一事务)
  工单完工 → 倒冲领料 → 成品入库 (跨3个模块，同一事务)

问题3: 缺乏最终一致性保障
  审批回调中同步执行全部逻辑，中间步骤失败导致整体回滚但审批状态已变更
  无补偿机制，无重试机制，无对账机制

问题4: 风格不统一
  PrdWorkOrderServiceImpl 同时使用: 直接注入 wm Mapper + Service 接口调用
  PrdBackflushServiceImpl 同时使用: 3 个 wm Mapper + IWmStockOperationService
```

### 1.3 MQ 需求场景矩阵

| 场景 | 当前方式 | MQ 改造后 | 优先级 | 说明 |
|------|---------|----------|--------|------|
| 采购收货→库存过账 | 同步调用链 | 异步入库 + 补偿 | P0 | 核心业务，调用链最深 |
| 工单完工→倒冲领料→成品入库 | 同步大事务 | 分步异步 + 补偿 | P0 | 事务粒度最大 |
| 委外/返工→库存出入库 | 策略模式同步 | 事件驱动 + 异步 | P1 | 跨模块 Mapper 依赖 |
| 供应商评估→批量计算 | SnailJob 定时 | MQ 触发 + 批量消费 | P2 | 已有定时任务，可优化 |
| 安全库存预警→采购建议 | SnailJob 定时 | 事件触发 + 实时响应 | P2 | 低库存事件驱动采购 |
| MES 工单联动 (未来) | 不存在 | MQ 跨库通信 | P3 | MES Phase 1 前置依赖 |

---

## 二、技术选型决策

### 2.1 RabbitMQ vs Kafka 对比

| 维度 | RabbitMQ | Kafka | 决策 |
|------|----------|-------|------|
| **定位** | 消息代理 (Broker) | 分布式事件流平台 | - |
| **消息模型** | Exchange → Queue → Consumer | Topic → Partition → Consumer Group | - |
| **适用场景** | 业务解耦/异步任务/事件通知 | 日志采集/流处理/大数据管道 | - |
| **消息可靠性** | ACK + 持久化 + 死信队列 | 副本 + Offset 提交 | RabbitMQ 更适合业务消息 |
| **消息顺序** | 单 Queue 有序 | 单 Partition 有序 | 均可满足 |
| **延迟消息** | 原生支持 (TTL + 死信) | 需外部组件 | RabbitMQ 原生支持 |
| **事务消息** | 支持 (Publisher Confirm) | 支持 (Exactly Once) | 均可 |
| **运维复杂度** | 低 (单节点即可) | 高 (Zookeeper/KRaft 集群) | RabbitMQ 更轻量 |
| **Spring 生态** | spring-boot-starter-amqp | spring-kafka | 均有官方 Starter |
| **与 RuoYi-Vue-Plus 兼容** | 框架无内置，需自行集成 | 框架无内置，需自行集成 | 相同 |

### 2.2 最终选型: RabbitMQ 为主 + Spring AMQP

**决策理由**:

1. **业务场景匹配**: ERP 系统的核心需求是业务解耦和异步任务，不是大数据流处理
2. **延迟消息支持**: 库存预警、超时自动关闭等场景需要延迟投递，RabbitMQ 原生支持
3. **死信队列**: 业务消息消费失败可进入 DLX，便于补偿和重试
4. **运维成本低**: 单节点即可满足当前规模，未来可集群化
5. **Spring AMQP 成熟**: 与 Spring Boot 无缝集成，@RabbitListener 声明式消费

**不选 Kafka 的理由**: 当前无流处理需求，Kafka 集群运维成本过高，不适合单体 ERP 应用

**不选 Spring Cloud Stream 的理由**: 增加抽象层但当前只有一个 Broker，过度设计；直接使用 Spring AMQP 更直观可控

---

## 三、架构设计

### 3.1 MQ 模块架构

```
ruoyi-common/
  └── ruoyi-common-mq/                    # 新增 MQ 公共模块
      ├── pom.xml
      └── src/main/java/com/nexus/common/mq/
          ├── config/
          │   ├── RabbitMqAutoConfiguration.java    # MQ 自动配置
          │   ├── RabbitMqProperties.java            # MQ 配置属性
          │   └── RabbitMqInitializer.java           # 交换机/队列/绑定声明
          ├── constant/
          │   ├── ExchangeConstants.java             # 交换机常量
          │   ├── QueueConstants.java                # 队列常量
          │   └── RoutingKeyConstants.java           # 路由键常量
          ├── domain/
          │   ├── BaseMessage.java                   # 消息基类(含messageId/timestamp/traceId)
          │   └── MessageHeader.java                 # 消息头(租户ID/数据源/来源模块)
          ├── producer/
          │   └── ReliableMessageProducer.java       # 可靠消息生产者(含确认+本地记录)
          ├── consumer/
          │   ├── BaseMessageConsumer.java           # 消费者基类(含幂等+日志+异常处理)
          │   └── DeadLetterMessageConsumer.java     # 死信消费者(告警+重试+人工介入)
          ├── retry/
          │   └── MessageRetryService.java           # 消息重试服务(指数退避)
          └── compensation/
              ├── MessageRecord.java                 # 消息记录实体
              ├── MessageRecordMapper.java            # 消息记录 Mapper
              └── MessageCompensationTask.java        # 消息补偿定时任务
```

### 3.2 消息模型设计

#### 交换机与队列拓扑

```
                    ┌──────────────────────────────────────────────────────┐
                    │              RabbitMQ Broker                         │
                    │                                                      │
  erp-producer ───►│  erp.direct.exchange (Direct)                       │
                    │    ├── stock.in.queue        ← stock.in.#           │
                    │    ├── stock.out.queue       ← stock.out.#          │
                    │    ├── purchase.receive.queue ← purchase.receive.#  │
                    │    ├── production.complete.queue ← production.complete.# │
                    │    └── vendor.evaluation.queue ← vendor.evaluation.# │
                    │                                                      │
  erp-producer ───►│  erp.topic.exchange (Topic)                         │
                    │    ├── stock.alert.queue     ← stock.alert.#        │
                    │    └── notification.queue    ← notification.#       │
                    │                                                      │
  erp-producer ───►│  erp.delay.exchange (Direct + TTL)                  │
                    │    └── erp.delay.queue → erp.delay.dlx → erp.delay.dlx.exchange │
                    │                                    → 目标队列        │
                    │                                                      │
                    │  erp.dlx.exchange (死信)                             │
                    │    └── erp.dlx.queue (死信队列，人工处理)             │
                    └──────────────────────────────────────────────────────┘
```

#### 核心消息定义

| 消息类型 | Exchange | RoutingKey | 生产者 | 消费者 | 说明 |
|---------|----------|------------|--------|--------|------|
| `StockInMessage` | erp.direct | stock.in.receive | WmMaterialReceiveServiceImpl | StockInConsumer | 采购入库过账 |
| `StockInMessage` | erp.direct | stock.in.production | PrdWorkOrderServiceImpl | StockInConsumer | 完工入库过账 |
| `StockInMessage` | erp.direct | stock.in.rework | PrdReworkOrderServiceImpl | StockInConsumer | 返工入库过账 |
| `StockOutMessage` | erp.direct | stock.out.issue | PrdWorkOrderServiceImpl | StockOutConsumer | 生产领料出库 |
| `StockOutMessage` | erp.direct | stock.out.rework | PrdReworkOrderServiceImpl | StockOutConsumer | 返工出库 |
| `PurchaseReceiveMessage` | erp.direct | purchase.receive.writeback | WmMaterialReceiveServiceImpl | PurchaseReceiveConsumer | 收货回写采购订单 |
| `ProductionCompleteMessage` | erp.direct | production.complete.backflush | PrdWorkOrderServiceImpl | ProductionBackflushConsumer | 完工倒冲领料 |
| `VendorEvaluationMessage` | erp.direct | vendor.evaluation.calculate | 定时任务/手动触发 | VendorEvaluationConsumer | 供应商评估计算 |
| `StockAlertMessage` | erp.topic | stock.alert.low | StockAlertTask | StockAlertConsumer | 低库存预警→采购建议 |
| `OrderTimeoutMessage` | erp.delay | order.timeout.check | PuPurchaseOrderServiceImpl | OrderTimeoutConsumer | 订单超时自动关闭 |

### 3.3 消息基类设计

```java
public abstract class BaseMessage implements Serializable {
    private String messageId;       // 消息唯一ID (UUID)
    private Long timestamp;         // 消息时间戳
    private String traceId;         // 链路追踪ID
    private Long tenantId;          // 租户ID
    private String sourceModule;    // 来源模块 (pu/prd/wm/so/bd)
    private Integer retryCount;     // 重试次数
    private Integer maxRetry;       // 最大重试次数 (默认3)
}
```

### 3.4 可靠消息保障机制

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      可靠消息投递流程                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  1. 生产者确认 (Publisher Confirm)                                      │
│     producer.send() → channel.waitForConfirms() → 本地记录消息状态       │
│                                                                         │
│  2. 消息持久化 (Message Persistence)                                    │
│     Exchange durable=true, Queue durable=true, deliveryMode=2           │
│                                                                         │
│  3. 消费者手动ACK (Manual ACK)                                          │
│     @RabbitListener(ackMode=MANUAL) → channel.basicAck/nacl            │
│                                                                         │
│  4. 幂等消费 (Idempotent Consumer)                                      │
│     BaseMessageConsumer.checkProcessed(messageId) → Redis SETNX        │
│                                                                         │
│  5. 死信队列 (Dead Letter Queue)                                        │
│     消费失败3次 → DLX → 人工处理/告警                                    │
│                                                                         │
│  6. 补偿任务 (Compensation Task)                                        │
│     SnailJob定时扫描 message_record 表 → 重新投递超时未确认消息           │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 7.5 跨数据源事务方案

```
当前问题:
  @DS("erp") + @Transactional → 单数据源事务，无法跨库

方案: 最终一致性 + 补偿
  ┌──────────┐    MQ     ┌──────────┐
  │ 模块A    │ ───────► │ 模块B    │
  │ @DS("erp")│         │ @DS("erp")│
  └──────────┘          └──────────┘
       │                      │
       ▼                      ▼
  本地事务提交            消费消息+本地事务
  + 发送MQ消息            + ACK/NACK
       │                      │
       │  失败时               │  失败时
       ▼                      ▼
  消息记录表             重试3次→DLX
  + 补偿任务             + 人工介入

关键: 生产者本地事务与消息发送的原子性
  → 使用 "本地消息表" 模式:
    1. 业务操作 + 写入message_record 在同一本地事务中
    2. 异步线程发送MQ消息
    3. 发送成功则更新message_record状态
    4. 补偿任务扫描未发送的消息重新投递
```

---

## 四、详细开发任务

### Week 7: MQ 基础设施 + 核心框架 (5人天)

#### 任务 7.1: 创建 ruoyi-common-mq 公共模块 (1人天)

**任务编号**: I-001-1

**详细内容**:

1. 创建 `ruoyi-common-mq` 模块目录结构
2. 编写 `pom.xml`，引入依赖:
   - `spring-boot-starter-amqp`
   - `ruoyi-common-core` (工具类)
   - `ruoyi-common-redis` (幂等检查)
   - `ruoyi-common-mybatis` (消息记录表)
3. 在根 `pom.xml` 的 `dependencyManagement` 中添加版本管理
4. 在 `ruoyi-common-bom` 的 `pom.xml` 中添加模块声明
5. 在 `ruoyi-erp` 的 `pom.xml` 中引入 `ruoyi-common-mq` 依赖

**验收标准**: 模块可编译，依赖无冲突

**涉及文件**:
- 新增: `ruoyi-common/ruoyi-common-mq/pom.xml`
- 修改: `pom.xml` (根), `ruoyi-common/pom.xml`, `ruoyi-common/ruoyi-common-bom/pom.xml`, `ruoyi-modules/ruoyi-erp/pom.xml`

---

#### 任务 7.2: MQ 自动配置与基础设施声明 (1人天)

**任务编号**: I-001-2

**详细内容**:

1. **RabbitMqProperties**: 配置属性类，支持自定义交换机前缀、是否开启消息记录、重试策略等
2. **RabbitMqAutoConfiguration**: 自动配置类
   - 配置 `RabbitTemplate` 的 ConfirmCallback 和 ReturnsCallback
   - 配置消息序列化器 (Jackson2JsonMessageConverter)
   - 配置全局重试策略
3. **RabbitMqInitializer**: 应用启动时声明所有交换机、队列和绑定关系
   - `erp.direct.exchange` (Direct)
   - `erp.topic.exchange` (Topic)
   - `erp.delay.exchange` (Direct + TTL)
   - `erp.dlx.exchange` (死信)
   - 所有队列及绑定
4. **spring.factories / AutoConfiguration**: 注册自动配置

**验收标准**: 应用启动后 RabbitMQ 中自动创建所有交换机和队列

**涉及文件**:
- 新增: `RabbitMqAutoConfiguration.java`, `RabbitMqProperties.java`, `RabbitMqInitializer.java`
- 新增: `ExchangeConstants.java`, `QueueConstants.java`, `RoutingKeyConstants.java`
- 修改: `application-dev.yml` (添加 RabbitMQ 连接配置)

**YAML 配置模板**:
```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: nexus
    password: nexus
    virtual-host: /erp
    publisher-confirm-type: correlated
    publisher-returns: true
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 10
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1000ms
```

---

#### 任务 7.3: 消息基类与可靠消息生产者 (1人天)

**任务编号**: I-001-3

**详细内容**:

1. **BaseMessage**: 消息基类
   - `messageId` (UUID), `timestamp`, `traceId`, `tenantId`, `sourceModule`, `retryCount`, `maxRetry`
   - 实现 `Serializable`
2. **MessageHeader**: 消息头工具类
   - 从当前上下文提取租户ID、用户ID、链路追踪ID
   - 注入到 MessageProperties 的 headers 中
3. **ReliableMessageProducer**: 可靠消息生产者
   - `send(exchange, routingKey, message)`: 发送消息
   - 内部流程:
     a. 填充 messageId/timestamp/traceId/tenantId
     b. 写入 `mq_message_record` 表 (状态=SENDING)
     c. 调用 `rabbitTemplate.convertAndSend()`
     d. ConfirmCallback 回调更新状态为 SENT/FAILED
   - 支持延迟消息: `sendDelay(exchange, routingKey, message, delayMillis)`
4. **MessageRecord**: 消息记录实体
   - `id`, `messageId`, `exchange`, `routingKey`, `messageBody`, `status` (SENDING/SENT/CONSUMED/FAILED)
   - `retryCount`, `maxRetry`, `nextRetryTime`, `createTime`, `updateTime`
5. **MessageRecordMapper**: 消息记录 Mapper

**验收标准**: 消息发送后可在 `mq_message_record` 表中查到记录，ConfirmCallback 正确更新状态

**涉及文件**:
- 新增: `BaseMessage.java`, `MessageHeader.java`, `ReliableMessageProducer.java`
- 新增: `MessageRecord.java`, `MessageRecordMapper.java`
- 新增: `mq_message_record.sql` (DDL)

---

#### 任务 7.4: 消费者基类与死信处理 (1人天)

**任务编号**: I-001-4

**详细内容**:

1. **BaseMessageConsumer**: 消费者抽象基类
   - `processMessage(message, channel, deliveryTag)`: 模板方法
   - 内部流程:
     a. 幂等检查: Redis SETNX `mq:consumed:{messageId}` (TTL=24h)
     b. 设置租户上下文 (TenantHelper)
     c. 调用 `doProcess(message)` (子类实现)
     d. 手动 ACK: `channel.basicAck(deliveryTag, false)`
     e. 更新 `mq_message_record` 状态为 CONSUMED
   - 异常处理:
     a. 业务异常: NACK + requeue=false → 进入死信
     b. 临时异常: NACK + requeue=true (重试次数内)
     c. 超过重试次数: NACK + requeue=false → 死信
2. **DeadLetterMessageConsumer**: 死信消费者
   - 记录死信消息到 `mq_dead_letter_log` 表
   - 发送告警通知 (日志/邮件/WebSocket)
   - 提供管理接口查询和重发死信消息
3. **MessageRetryService**: 消息重试服务
   - 指数退避: 1s → 2s → 4s → 8s → 16s
   - 更新 nextRetryTime 和 retryCount
4. **MessageCompensationTask**: 补偿定时任务
   - SnailJob `@JobExecutor`
   - 扫描 `mq_message_record` 中状态为 SENDING 且超过 60s 的记录
   - 重新投递消息

**验收标准**: 消费失败3次后消息进入死信队列，补偿任务可重新投递超时消息

**涉及文件**:
- 新增: `BaseMessageConsumer.java`, `DeadLetterMessageConsumer.java`
- 新增: `MessageRetryService.java`, `MessageCompensationTask.java`
- 新增: `mq_dead_letter_log.sql` (DDL)

---

#### 任务 7.5: application.yml 配置与 Docker Compose (0.5人天)

**任务编号**: I-001-5

**详细内容**:

1. 在 `application-dev.yml` 中添加 RabbitMQ 连接配置
2. 在 `application.yml` 中添加 MQ 通用配置 (交换机前缀、重试策略等)
3. 编写 `docker/docker-compose-rabbitmq.yml`:
   - RabbitMQ 3.13 + Management Plugin
   - 端口: 5672 (AMQP) + 15672 (Management)
   - 持久化数据卷
   - 默认用户: nexus/nexus, vhost: /erp

**验收标准**: Docker Compose 启动后可通过 Management UI 访问，应用可连接

**涉及文件**:
- 修改: `application-dev.yml`, `application.yml`
- 新增: `docker/docker-compose-rabbitmq.yml`

---

### Week 8: 业务场景改造 + 跨库事务框架 (5人天)

#### 任务 8.1: 采购收货→库存过账 异步化改造 (1.5人天)

**任务编号**: I-001-6

**当前问题**: `WmMaterialReceiveServiceImpl.afterFlowHandle()` 审批通过后同步执行入库过账+采购回写，5-6层调用链在同一事务中

**改造方案**:

```
改造前:
  afterFlowHandle() → executeReceive() → processStockTransfer() → writeBackPurchaseOrder()
  [同一事务，5-6层调用，跨wm/pu模块]

改造后:
  afterFlowHandle() → 更新状态为 APPROVED → 发送 StockInMessage
  [轻量级，只更新状态+发消息]

  StockInConsumer.doProcess() → executeReceive() → processStockTransfer()
  [独立事务，专注库存过账]

  过账完成后 → 发送 PurchaseReceiveWritebackMessage
  PurchaseReceiveWritebackConsumer.doProcess() → writeBackPurchaseOrderReceivedQty()
  [独立事务，专注采购回写]
```

**详细内容**:

1. 新增 `StockInMessage` (extends BaseMessage):
   - `receiveId`, `warehouseId`, `locationId`, `sourceType` (PURCHASE/PRODUCTION/REWORK)
2. 新增 `PurchaseReceiveWritebackMessage` (extends BaseMessage):
   - `receiveId`, `purchaseOrderId`, `orderLineId`, `receivedQty`
3. 改造 `WmMaterialReceiveServiceImpl.afterFlowHandle()`:
   - 审批通过后只更新状态为 APPROVED
   - 注入 `ReliableMessageProducer`，发送 `StockInMessage`
   - 移除同步调用 `executeReceive()` 和 `writeBackPurchaseOrderReceivedQty()`
4. 新增 `StockInConsumer` (extends BaseMessageConsumer):
   - 消费 `StockInMessage`，调用 `executeReceive()`
   - 过账完成后发送 `PurchaseReceiveWritebackMessage`
5. 新增 `PurchaseReceiveWritebackConsumer` (extends BaseMessageConsumer):
   - 消费 `PurchaseReceiveWritebackMessage`，调用 `writeBackPurchaseOrderReceivedQty()`
6. 移除 `WmMaterialReceiveServiceImpl` 中对 `PuPurchaseOrderMapper` 和 `PuPurchaseOrderLineMapper` 的直接注入

**验收标准**:
- 审批通过后采购订单状态正确更新
- 库存数量正确增减
- 消费失败时消息进入死信，不影响审批状态
- 移除了 wm→pu 的直接 Mapper 依赖

**涉及文件**:
- 新增: `StockInMessage.java`, `PurchaseReceiveWritebackMessage.java`, `StockInConsumer.java`, `PurchaseReceiveWritebackConsumer.java`
- 修改: `WmMaterialReceiveServiceImpl.java` (解耦 + 异步化)

---

#### 任务 8.2: 工单完工→倒冲领料→成品入库 异步化改造 (1.5人天)

**任务编号**: I-001-7

**当前问题**: `PrdWorkOrderServiceImpl.complete()` 在同一事务中执行工单状态更新+倒冲领料+成品入库

**改造方案**:

```
改造前:
  complete() → 更新状态 → backflushService.backflushOnCompletion() → stockOperationService.postProductionCompletion()
  [同一大事务，跨prd/wm模块，直接注入3个wm Mapper]

改造后:
  complete() → 更新状态为 COMPLETED → 发送 ProductionCompleteMessage
  [轻量级，只更新工单状态]

  ProductionBackflushConsumer.doProcess() → backflushService.backflushOnCompletion()
  [独立事务，专注倒冲领料]

  倒冲完成后 → 发送 StockInMessage (sourceType=PRODUCTION)
  StockInConsumer.doProcess() → stockOperationService.postProductionCompletion()
  [独立事务，专注成品入库]
```

**详细内容**:

1. 新增 `ProductionCompleteMessage` (extends BaseMessage):
   - `woId`, `completionQty`, `materialId`, `materialCode`, `materialName`
2. 改造 `PrdWorkOrderServiceImpl.complete()`:
   - 完工后只更新状态为 COMPLETED
   - 发送 `ProductionCompleteMessage`
   - 移除同步调用 `backflushService.backflushOnCompletion()` 和 `stockOperationService.postProductionCompletion()`
3. 新增 `ProductionBackflushConsumer` (extends BaseMessageConsumer):
   - 消费 `ProductionCompleteMessage`，调用倒冲领料
   - 倒冲完成后发送 `StockInMessage` (sourceType=PRODUCTION)
4. 改造 `PrdBackflushServiceImpl`:
   - 移除对 `WmMaterialStockMapper`, `WmProductionIssueMapper`, `WmProductionIssueLineMapper` 的直接注入
   - 改为通过 `IWmStockOperationService` 接口调用（已有部分使用）
5. 改造 `PrdWorkOrderServiceImpl.release()`:
   - 工单下达时创建领料单的逻辑保持同步（下达是用户主动操作，需即时反馈）

**验收标准**:
- 工单完工后倒冲领料和成品入库正确执行
- 移除了 prd→wm 的直接 Mapper 依赖 (PrdBackflushServiceImpl)
- 消费失败时工单状态不受影响

**涉及文件**:
- 新增: `ProductionCompleteMessage.java`, `ProductionBackflushConsumer.java`
- 修改: `PrdWorkOrderServiceImpl.java`, `PrdBackflushServiceImpl.java`

---

#### 任务 8.3: 委外/返工库存联动 异步化改造 (1人天)

**任务编号**: I-001-8

**当前问题**: 委外/返工策略类直接注入 prd 模块 Mapper，形成 wm→prd 双向依赖

**改造方案**:

```
改造前:
  SubcontractIssueStrategy.execute() → 直接操作 PrdSubcontractMapper
  ReworkIssueStrategy.execute() → 直接操作 PrdReworkOrderMapper

改造后:
  策略类只处理库存操作（wm模块内部）
  库存操作完成后 → 发送消息通知 prd 模块更新状态
  prd 模块消费消息 → 更新委外/返工订单状态
```

**详细内容**:

1. 新增 `SubcontractStockMessage` (extends BaseMessage):
   - `subcontractId`, `operationType` (ISSUE/RECEIVE), `materialId`, `quantity`
2. 新增 `ReworkStockMessage` (extends BaseMessage):
   - `reworkId`, `operationType` (ISSUE/RECEIVE), `materialId`, `quantity`
3. 改造 `SubcontractIssueStrategy` / `SubcontractReceiveStrategy`:
   - 移除 `PrdSubcontractMapper` 注入
   - 库存操作完成后发送 `SubcontractStockMessage`
4. 改造 `ReworkIssueStrategy` / `ReworkReceiveStrategy`:
   - 移除 `PrdReworkOrderMapper` 注入
   - 库存操作完成后发送 `ReworkStockMessage`
5. 新增 `SubcontractStockConsumer`:
   - 消费消息，更新委外订单状态
6. 新增 `ReworkStockConsumer`:
   - 消费消息，更新返工订单状态

**验收标准**:
- 委外发料/收货后订单状态正确更新
- 返工出库/入库后订单状态正确更新
- 移除了策略类对 prd Mapper 的直接依赖

**涉及文件**:
- 新增: `SubcontractStockMessage.java`, `ReworkStockMessage.java`, `SubcontractStockConsumer.java`, `ReworkStockConsumer.java`
- 修改: `SubcontractIssueStrategy.java`, `SubcontractReceiveStrategy.java`, `ReworkIssueStrategy.java`, `ReworkReceiveStrategy.java`

---

#### 任务 8.4: 跨库事务补偿框架 (1人天)

**任务编号**: I-002

**详细内容**:

1. **补偿记录表**: `mq_compensation_record`
   - `id`, `messageId`, `businessType`, `businessId`, `compensationStatus` (PENDING/SUCCESS/FAILED)
   - `compensationMethod`, `compensationParams`, `retryCount`, `nextRetryTime`
   - `createTime`, `updateTime`
2. **CompensationService**: 补偿服务接口
   - `registerCompensation(messageId, businessType, businessId, method, params)`: 注册补偿操作
   - `executeCompensation(recordId)`: 执行补偿
   - `markCompensationSuccess(recordId)`: 标记补偿成功
3. **CompensationTask**: 补偿定时任务
   - SnailJob `@JobExecutor`
   - 扫描 PENDING 且 nextRetryTime <= now 的记录
   - 执行补偿，失败则更新 retryCount 和 nextRetryTime
   - 超过最大重试次数则标记为 FAILED 并告警
4. **对账机制**: `ReconciliationTask`
   - SnailJob `@JobExecutor` (每天凌晨执行)
   - 对比 `mq_message_record` 与业务表状态
   - 识别不一致数据并生成补偿记录

**验收标准**:
- 消息消费失败后可自动补偿
- 补偿超过重试次数后可告警
- 对账任务可识别不一致数据

**涉及文件**:
- 新增: `mq_compensation_record.sql`, `CompensationRecord.java`, `CompensationRecordMapper.java`
- 新增: `CompensationService.java`, `CompensationServiceImpl.java`
- 新增: `CompensationTask.java`, `ReconciliationTask.java`

---

## 五、任务汇总与排期

### Week 7 排期

| 天数 | 任务编号 | 任务内容 | 产出 |
|------|---------|---------|------|
| Day 1 | I-001-1 | 创建 ruoyi-common-mq 公共模块 | 模块可编译 |
| Day 2 | I-001-2 | MQ 自动配置与基础设施声明 | 启动后自动创建交换机/队列 |
| Day 3 | I-001-3 | 消息基类与可靠消息生产者 | 消息可发送+记录+确认 |
| Day 4 | I-001-4 | 消费者基类与死信处理 | 消费可幂等+失败进死信+补偿 |
| Day 5 | I-001-5 | YAML 配置与 Docker Compose | 环境可一键启动 |

### Week 8 排期

| 天数 | 任务编号 | 任务内容 | 产出 |
|------|---------|---------|------|
| Day 1-1.5 | I-001-6 | 采购收货→库存过账 异步化 | wm→pu 解耦 |
| Day 2.5-3 | I-001-7 | 工单完工→倒冲→入库 异步化 | prd→wm 解耦 |
| Day 4 | I-001-8 | 委外/返工库存联动 异步化 | 策略类解耦 |
| Day 5 | I-002 | 跨库事务补偿框架 | 补偿+对账可用 |

### 工作量统计

| 类别 | 任务数 | 人天 |
|------|--------|------|
| MQ 基础设施 (Week 7) | 5 | 4.5 |
| 业务场景改造 (Week 8) | 3 | 4 |
| 跨库事务补偿 (Week 8) | 1 | 1 |
| **合计** | **9** | **9.5** |

---

## 六、新增文件清单

### ruoyi-common-mq 模块 (~15个文件)

```
ruoyi-common/ruoyi-common-mq/
├── pom.xml
└── src/main/java/com/nexus/common/mq/
    ├── config/
    │   ├── RabbitMqAutoConfiguration.java
    │   ├── RabbitMqProperties.java
    │   └── RabbitMqInitializer.java
    ├── constant/
    │   ├── ExchangeConstants.java
    │   ├── QueueConstants.java
    │   └── RoutingKeyConstants.java
    ├── domain/
    │   ├── BaseMessage.java
    │   ├── MessageHeader.java
    │   └── MessageRecord.java
    ├── producer/
    │   └── ReliableMessageProducer.java
    ├── consumer/
    │   ├── BaseMessageConsumer.java
    │   └── DeadLetterMessageConsumer.java
    ├── retry/
    │   └── MessageRetryService.java
    └── compensation/
        ├── CompensationRecord.java
        ├── CompensationRecordMapper.java
        ├── CompensationService.java
        ├── CompensationServiceImpl.java
        ├── CompensationTask.java
        ├── ReconciliationTask.java
        └── MessageCompensationTask.java
```

### ruoyi-erp 消息与消费者 (~10个文件)

```
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/
├── mq/message/
│   ├── StockInMessage.java
│   ├── StockOutMessage.java
│   ├── PurchaseReceiveWritebackMessage.java
│   ├── ProductionCompleteMessage.java
│   ├── SubcontractStockMessage.java
│   ├── ReworkStockMessage.java
│   ├── VendorEvaluationMessage.java
│   └── StockAlertMessage.java
└── mq/consumer/
    ├── StockInConsumer.java
    ├── StockOutConsumer.java
    ├── PurchaseReceiveWritebackConsumer.java
    ├── ProductionBackflushConsumer.java
    ├── SubcontractStockConsumer.java
    ├── ReworkStockConsumer.java
    ├── VendorEvaluationConsumer.java
    └── StockAlertConsumer.java
```

### SQL 脚本

```
script/sql/
├── week7_mq_infrastructure.sql       # mq_message_record + mq_dead_letter_log + mq_compensation_record
└── docker/docker-compose-rabbitmq.yml
```

### 修改文件

```
修改:
├── pom.xml (根)                      # dependencyManagement 添加 ruoyi-common-mq
├── ruoyi-common/pom.xml              # 子模块声明
├── ruoyi-common/ruoyi-common-bom/pom.xml  # BOM 声明
├── ruoyi-modules/ruoyi-erp/pom.xml   # 引入 ruoyi-common-mq
├── application-dev.yml               # RabbitMQ 连接配置
├── application.yml                   # MQ 通用配置
├── WmMaterialReceiveServiceImpl.java # 解耦 pu Mapper + 异步化
├── PrdWorkOrderServiceImpl.java      # 解耦 wm Mapper + 异步化
├── PrdBackflushServiceImpl.java      # 移除 wm Mapper 直接注入
├── SubcontractIssueStrategy.java     # 移除 prd Mapper 直接注入
├── SubcontractReceiveStrategy.java   # 移除 prd Mapper 直接注入
├── ReworkIssueStrategy.java          # 移除 prd Mapper 直接注入
└── ReworkReceiveStrategy.java        # 移除 prd Mapper 直接注入
```

---

## 七、风险与应对

| 风险 | 影响 | 概率 | 应对策略 |
|------|------|------|---------|
| RabbitMQ 单点故障 | 高 | 低 | Docker 部署时配置镜像队列；生产环境建议集群化 |
| 消息丢失 | 高 | 低 | Publisher Confirm + 本地消息表 + 补偿任务三重保障 |
| 消息重复消费 | 中 | 中 | BaseMessageConsumer 幂等检查 (Redis SETNX) |
| 异步化后用户体验变化 | 中 | 高 | 前端轮询/SSE 推送操作结果；关键操作提供"查看处理进度" |
| 事务拆分后数据不一致 | 高 | 中 | 补偿任务 + 对账任务 + 死信告警 |
| 改造影响现有功能 | 高 | 低 | 渐进式改造，每个场景独立验证；保留同步调用作为降级方案 |

---

## 八、验收标准

### 功能验收

| 验收项 | 验收方法 | 预期结果 |
|--------|---------|---------|
| RabbitMQ 连接 | 启动应用，查看 Management UI | 连接正常，交换机/队列自动创建 |
| 消息发送 | 触发采购收货审批 | mq_message_record 有记录，状态为 SENT |
| 消息消费 | 查看消费者日志 | 消费成功，业务数据正确，状态为 CONSUMED |
| 幂等性 | 重复发送同一 messageId | 第二次消费被跳过 |
| 死信处理 | 模拟消费失败3次 | 消息进入死信队列，告警通知 |
| 补偿机制 | 模拟消息发送后未确认 | 补偿任务重新投递 |
| 采购收货异步化 | 端到端测试: 审批→入库→回写 | 库存正确，采购订单状态正确 |
| 工单完工异步化 | 端到端测试: 完工→倒冲→入库 | 库存正确，工单状态正确 |
| 委外/返工异步化 | 端到端测试: 发料/收货 | 库存正确，订单状态正确 |

### 性能验收

| 指标 | 目标 | 测试方法 |
|------|------|---------|
| 消息发送延迟 | < 50ms | 单条消息发送耗时 |
| 消息消费延迟 | < 500ms | 从发送到消费完成 |
| 吞吐量 | > 1000 msg/s | 批量发送测试 |
| 消息可靠性 | 0 丢失 | 断电/断网恢复后验证 |

### 架构验收

| 验收项 | 验收方法 | 预期结果 |
|--------|---------|---------|
| wm→pu Mapper 依赖消除 | 编译检查 | WmMaterialReceiveServiceImpl 不再注入 pu Mapper |
| prd→wm Mapper 依赖消除 | 编译检查 | PrdBackflushServiceImpl 不再注入 wm Mapper |
| 策略类 prd Mapper 依赖消除 | 编译检查 | 4个策略类不再注入 prd Mapper |
| 跨模块调用统一 | 代码审查 | 所有跨模块调用通过 MQ 或 Service 接口 |

---

> **文档版本**: v1.0
> **编制日期**: 2026-05-31
> **预计开始**: Week 7
> **预计完成**: Week 8
> **依赖**: 无外部依赖，可直接开始
> **后续关联**: Week 9-10 代码重构 (Q-003 Service拆分 可基于 MQ 解耦后进一步优化)
