# MES Phase 1 Week 17 开发实施报告

> **实施日期**: 2026-06-16
> **阶段**: MES Phase 1 - 模块骨架 + 数据源 + 契约清理
> **状态**: ✅ 已完成

---

## 一、任务完成清单

| 任务 | 状态 | 交付物 |
|------|------|--------|
| 创建 `ruoyi-mes` Maven 模块 | ✅ | [ruoyi-mes/pom.xml](file:///e:/Nexus/nexus-erp/ruoyi-modules/ruoyi-mes/pom.xml) |
| 注册模块到父 POM | ✅ | [ruoyi-modules/pom.xml](file:///e:/Nexus/nexus-erp/ruoyi-modules/pom.xml) + [pom.xml](file:///e:/Nexus/nexus-erp/pom.xml) |
| 引入模块到启动入口 | ✅ | [ruoyi-admin/pom.xml](file:///e:/Nexus/nexus-erp/ruoyi-admin/pom.xml) |
| 配置 `mes` 数据源 | ✅ | [application-dev.yml](file:///e:/Nexus/nexus-erp/ruoyi-admin/src/main/resources/application-dev.yml) |
| 创建 ERP 桥接服务 | ✅ | [IMesErpBridgeService](file:///e:/Nexus/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common/bridge/IMesErpBridgeService.java) |
| 创建 ERP 只读 Mapper | ✅ | [ErpMaterialMapper](file:///e:/Nexus/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common/mapper/ErpMaterialMapper.java) 等 |
| 迁移消息 DTO 到共享模块 | ✅ | [WorkOrderReleaseMessage](file:///e:/Nexus/nexus-erp/ruoyi-common/ruoyi-common-mq/src/main/java/com/nexus/common/mq/message/WorkOrderReleaseMessage.java) |
| 补充 MQ 队列/路由键常量 | ✅ | [QueueConstants](file:///e:/Nexus/nexus-erp/ruoyi-common/ruoyi-common-mq/src/main/java/com/nexus/common/mq/constant/QueueConstants.java) + [RoutingKeyConstants](file:///e:/Nexus/nexus-erp/ruoyi-common/ruoyi-common-mq/src/main/java/com/nexus/common/mq/constant/RoutingKeyConstants.java) |
| 注册 MES 队列绑定 | ✅ | [RabbitMqInitializer](file:///e:/Nexus/nexus-erp/ruoyi-common/ruoyi-common-mq/src/main/java/com/nexus/common/mq/config/RabbitMqInitializer.java) |
| 修正 MQ 消息发送时序 | ✅ | [PrdWorkOrderServiceImpl](file:///e:/Nexus/nexus-erp/ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/impl/PrdWorkOrderServiceImpl.java) |
| 禁用 ERP 占位消费者 | ✅ | [WorkOrderReleaseConsumer](file:///e:/Nexus/nexus-erp/ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/mq/consumer/WorkOrderReleaseConsumer.java) |
| 生成 DDL SQL | ✅ | [ry-mes-week17.sql](file:///e:/Nexus/nexus-erp/doc/mes/sql/mes-week17-01-init.sql) |
| 生成菜单权限 SQL | ✅ | [ry-vue-mes-menu-week17.sql](file:///e:/Nexus/nexus-erp/doc/mes/sql/mes-week17-10-menu.sql) |
| 编译验证 | ✅ | `mvn compile -pl ruoyi-modules/ruoyi-mes -am` 通过 |

---

## 二、关键变更说明

### 2.1 模块结构

```
ruoyi-modules/ruoyi-mes/
├── pom.xml                          # Maven 模块定义
└── src/main/java/com/nexus/mes/
    ├── common/
    │   ├── bridge/
    │   │   ├── IMesErpBridgeService.java       # ERP 桥接接口
    │   │   └── MesErpBridgeServiceImpl.java   # 桥接实现(编程式数据源切换)
    │   ├── mapper/
    │   │   ├── ErpMaterialMapper.java         # ERP 物料只读 Mapper
    │   │   ├── ErpBomLineMapper.java          # ERP BOM 行只读 Mapper
    │   │   └── ErpRoutingStepMapper.java      # ERP 工艺步骤只读 Mapper
    │   └── mq/message/
    │   │   ├── MesWorkOrderCompletedMessage.java  # MES→ERP 工单完工消息
    │   │   └── IqcCompletedMessage.java           # MES→ERP IQC 完成消息
    ├── pro/                          # 生产执行(Week 18+)
    ├── qc/                           # 质量管理(Phase 3)
    ├── eq/                           # 设备管理(Phase 3)
    ├── dc/                           # 数据采集(Phase 3)
    └── spc/                          # SPC 分析(Phase 3)
```

### 2.2 数据源配置

新增 `mes` 数据源，指向 `ry-mes` 库：

```yaml
# application-dev.yml
spring.datasource.dynamic.datasource:
  master:  # ry-vue (系统管理)
  erp:     # ry-erp (ERP业务)
  mes:     # ry-mes (MES制造执行) ← 新增
```

### 2.3 MQ 契约变更

| 契约 | 变更 |
|------|------|
| `WorkOrderReleaseMessage` | 迁移到 `ruoyi-common-mq`，新增 `routingId` 字段（可空） |
| `ERP_WORKORDER_COMPLETED_QUEUE` | 新增队列常量 |
| `ERP_IQC_COMPLETED_QUEUE` | 新增队列常量 |
| `ERP_WORKORDER_COMPLETED` | 新增路由键常量 |
| `ERP_IQC_COMPLETED` | 新增路由键常量 |

### 2.4 ERP 侧修复

**MQ 消息发送时序修正**：

```java
// PrdWorkOrderServiceImpl.afterFlowHandle()
// 旧: sendWorkOrderReleaseMessage(entity); // 在事务内发送
// 新: TransactionSynchronizationManager.registerSynchronization(
//       new TransactionSynchronization() {
//         @Override public void afterCommit() { sendWorkOrderReleaseMessage(entity); }
//       });
```

**占位消费者禁用**：

```java
// WorkOrderReleaseConsumer
@ConditionalOnProperty(name = "mes.placeholder-consumer.enabled", havingValue = "true", matchIfMissing = false)
// 默认禁用，MES 模块上线后不再消费
```

---

## 三、SQL 脚本说明

### 3.1 DDL SQL

**文件**: [ry-mes-week17.sql](file:///e:/Nexus/nexus-erp/doc/mes/sql/mes-week17-01-init.sql)

**内容**:
- 创建 `ry-mes` 数据库
- 创建 `pro_work_order` 表（MES 工单主表）
- 创建 `pro_work_order_step` 表（MES 工单工序步骤表）

**执行顺序**:
1. 先执行 `ry-mes-week17.sql` 创建数据库和表结构
2. 后执行 `ry-vue-mes-menu-week17.sql` 创建菜单权限

### 3.2 菜单权限 SQL

**文件**: [ry-vue-mes-menu-week17.sql](file:///e:/Nexus/nexus-erp/doc/mes/sql/mes-week17-10-menu.sql)

**菜单 ID 分配**:
| ID | 名称 | 类型 | 父级 |
|----|------|------|------|
| 3000 | MES系统 | M(目录) | 0 |
| 3001 | 生产执行 | M(目录) | 3000 |
| 3002 | 质量管理 | M(目录) | 3000 |
| 3003 | 设备管理 | M(目录) | 3000 |
| 3004 | 数据采集 | M(目录) | 3000 |
| 3005 | SPC分析 | M(目录) | 3000 |
| 3010 | MES工单 | C(菜单) | 3001 |
| 3011~3017 | MES工单按钮 | F(按钮) | 3010 |

**冲突检查**: 已确认 3000~3017 段空闲，与现有 ID 无冲突。

---

## 四、验证步骤

### 4.1 编译验证

```bash
mvn compile -pl ruoyi-modules/ruoyi-mes -am
# 结果: ✅ 通过 (exit code 0)
```

### 4.2 启动验证（待执行）

```bash
# 1. 创建数据库
mysql -u root -p < doc/mes/sql/mes-week17-01-init.sql

# 2. 创建菜单
mysql -u root -p ry-vue < doc/mes/sql/mes-week17-10-menu.sql

# 3. 启动应用
mvn spring-boot:run -pl ruoyi-admin

# 验证点:
# - ruoyi-admin 能正常启动
# - mes 数据源连接成功
# - MES 菜单在系统中可见
```

---

## 五、Week 18 预告

| 任务 | 关键内容 |
|------|---------|
| MES 工单后端 | Domain/Mapper/Service/Controller，消费 `WorkOrderReleaseMessage` |
| ERP 桥接完善 | 物料/BOM/工艺路线/工序完整桥接 |
| 只读前端 | MES 侧主数据只读页面（或复用 ERP 选择器） |

---

## 六、风险与注意事项

| 风险 | 说明 | 应对 |
|------|------|------|
| `WorkOrderReleaseMessage.routingId` 可空 | ERP `prd_work_order` 表无 `routing_id` 字段 | MES 桥接服务根据 `materialId` 查默认工艺路线 |
| MQ 消息记录表在 `ry-vue`(master) | MES 发送 MQ 时需切换数据源 | 参照 `WmMqSendService` 的 `@Async` + `push("master")` 模式 |
| ERP 占位消费者已禁用 | 若 MES 未上线，消息堆积 | MES 上线前可临时启用：`mes.placeholder-consumer.enabled=true` |

---

**文档生成时间**: 2026-06-16
**执行者**: AI Agent
**审核状态**: 待人工确认