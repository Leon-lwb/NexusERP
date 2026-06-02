# NexusERP 剩余开发任务计划

> 编制日期: 2026-06-02
> 基于: Week 1-10 执行记录 + 项目代码深度分析
> 前置状态: Week 9-10 已全部完成 (M6 代码重构 + 销售增强已达成)
> 总目标: ERP 完工 → MES 建设 → IoT 建设，实现 ERP+MES+IoT 一体化平台

---

## 一、当前项目状态总览

### 1.1 已完成里程碑

| 里程碑 | 时间点 | 状态 |
|--------|--------|------|
| M1: ERP P0清零 | Week 1 | ✅ 已达成 |
| M2: 采购闭环 | Week 2 | ✅ 已达成 |
| M3: 仓储策略层 | Week 3 | ✅ 已达成 |
| M4: 生产深度 | Week 5 | ✅ 已达成 |
| M5: MQ基建完成 | Week 8 | ✅ 已达成 |
| M6: ERP代码重构完成 | Week 10 | ✅ 已达成 |
| M7: ERP全部交付 | Week 12 | ✅ 已达成 |

### 1.2 各模块完成度 (代码验证后)

| 模块 | 后端完成度 | 前端完成度 | 关键缺口 |
|------|-----------|-----------|----------|
| **bd** 基础数据 | 95% | 90% | 工艺路线/ECN/替代BOM (MES阶段) |
| **pu** 采购管理 | 90% | 85% | 后端完整，前端基本完整 |
| **so** 销售管理 | 95% | 90% | 价格表前端已完成，阶梯价待扩展 |
| **wm** 仓储管理 | 95% | 90% | 序列号/批次/追溯/盘点审批流已完成 |
| **prd** 生产管理 | 90% | 75% | 后端完整，部分前端页面需完善 |
| **sch** 排班调度 | 85% | 80% | 高级排产/甘特图 (MES阶段) |
| **qc** 质量管理 | 0% | 0% | MES统一实现 |
| **eq** 设备管理 | 0% | 0% | MES统一实现 |
| **fi** 财务管理 | 0% | 0% | P3优先级，后期规划 |

### 1.3 后端已实现但前端缺失的功能

| 功能 | 后端状态 | 前端状态 | 优先级 |
|------|---------|---------|--------|
| 价格表管理 (SoPriceList) | ✅ 完整 (含PricingEngine) | ✅ 已完成 | ~~P0~~ |
| 信用检查 (CreditCheck) | ✅ 完整 (冻结/释放) | ❌ 无独立页面 | P1 |
| 委外打印模板 | ✅ 后端API就绪 | ❌ print.vue 缺失 | P2 |

---

## 二、Phase 1 剩余: ERP 完工 (Week 11-12)

### 2.0 DDL 规范说明

> **重要**: 以下所有 DDL 均遵循 `ry-erp.sql` 的项目规范:
> - `ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC`
> - 主键 `bigint(20) NOT NULL`，应用层雪花ID生成，不自增
> - 第2列为 `tenant_id varchar(20) DEFAULT '000000'`，建索引 `idx_tenant_id`
> - 每个 varchar 列显式声明 `CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`
> - 表末尾固定审计字段: `attr1`~`attr4` → `create_dept` → `create_by` → `create_time` → `update_by` → `update_time` → `del_flag`
> - 无数据库级外键约束，表间关系由应用层维护

### 2.1 Week 11: 序列号管理 + 批次管理 + 价格表前端 + Jasypt密文生成

#### 任务 11.1: 序列号管理 (R-041, 2人天)

**当前状态**: 完全缺失。`bd_material.is_serial_manage` 字段已预留，`BdSerialNumberRule` 编码规则已就绪，但无 `wm_sn` 表和业务代码。

**SQL DDL** (遵循 ry-erp.sql 规范):
```sql
DROP TABLE IF EXISTS `wm_sn`;
CREATE TABLE `wm_sn` (
  `sn_id`           bigint(20)   NOT NULL                    COMMENT '序列号ID',
  `tenant_id`       varchar(20)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '000000' COMMENT '租户ID',
  `sn_code`         varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '序列号编码',
  `material_id`     bigint(20)   NOT NULL                    COMMENT '物料ID',
  `material_code`   varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物料编码',
  `material_name`   varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物料名称',
  `batch_id`        bigint(20)   DEFAULT NULL                COMMENT '关联批次ID',
  `batch_code`      varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联批次编码',
  `warehouse_id`    bigint(20)   DEFAULT NULL                COMMENT '当前仓库ID',
  `location_id`     bigint(20)   DEFAULT NULL                COMMENT '当前库位ID',
  `stock_id`        bigint(20)   DEFAULT NULL                COMMENT '关联库存记录ID',
  `status`          varchar(16)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'IN_STOCK' COMMENT '状态(IN_STOCK/OUT/SCRAP/RESERVED)',
  `source_doc_type` varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单据类型',
  `source_doc_id`   bigint(20)   DEFAULT NULL                COMMENT '来源单据ID',
  `rule_id`         bigint(20)   DEFAULT NULL                COMMENT '生成规则ID(关联bd_serial_number_rule)',
  `production_date` date         DEFAULT NULL                COMMENT '生产日期',
  `expiry_date`     date         DEFAULT NULL                COMMENT '有效期',
  `attr1`           varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预留字段1',
  `attr2`           varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预留字段2',
  `attr3`           int(11)      DEFAULT NULL                COMMENT '预留字段3',
  `attr4`           int(11)      DEFAULT NULL                COMMENT '预留字段4',
  `create_dept`     bigint(20)   DEFAULT NULL                COMMENT '创建部门',
  `create_by`       bigint(20)   DEFAULT NULL                COMMENT '创建者',
  `create_time`     datetime     DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  `update_by`       bigint(20)   DEFAULT NULL                COMMENT '更新者',
  `update_time`     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`        char(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`sn_id`),
  UNIQUE KEY `uk_sn_code` (`sn_code`, `tenant_id`) USING BTREE,
  INDEX `idx_tenant_id` (`tenant_id`) USING BTREE,
  INDEX `idx_material` (`material_id`) USING BTREE,
  INDEX `idx_batch` (`batch_id`) USING BTREE,
  INDEX `idx_status` (`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '序列号管理';

-- 字典: 序列号状态
INSERT INTO `sys_dict_type` VALUES (xxx, '000000', '序列号状态', 'wm_sn_status', 103, 1, NOW(), NULL, NULL, '序列号状态');
INSERT INTO `sys_dict_data` VALUES (xxx, '000000', 1, '在库', 'IN_STOCK', 'wm_sn_status', '', 'primary', 'Y', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_dict_data` VALUES (xxx, '000000', 2, '已出库', 'OUT', 'wm_sn_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_dict_data` VALUES (xxx, '000000', 3, '报废', 'SCRAP', 'wm_sn_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_dict_data` VALUES (xxx, '000000', 4, '已预留', 'RESERVED', 'wm_sn_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '');
```

**后端开发**:
1. 新增 `WmSn` 实体 + `WmSnBo` + `WmSnVo` (含 @AutoMapper 注解)
2. 新增 `WmSnMapper` + XML
3. 新增 `IWmSnService` / `WmSnServiceImpl` (extends ServiceImpl)
4. 新增 `WmSnController` (RESTful API `/erp/wm/sn`)
5. 改造出入库逻辑: `is_serial_manage=1` 的物料出入库时必须绑定序列号
6. 改造 `WmMaterialReceiveExecutorServiceImpl`: 入库时根据规则生成序列号
7. 改造 `WmSalesIssueServiceImpl` / `WmProductionIssueServiceImpl` / `WmMiscIssueServiceImpl`: 出库时选择序列号
8. 改造 `WmMiscRecptServiceImpl`: 杂收时绑定序列号

**核心业务逻辑**:
- 序列号生成: 调用 `BdSerialNumberRuleServiceImpl.generateSerialNumber()` 批量生成
- 入库绑定: 收货/杂收时，序列号管理物料自动生成SN并绑定库存
- 出库解绑: 销售/生产出库时，选择SN出库，状态变为OUT
- 状态流转: IN_STOCK → RESERVED → OUT / IN_STOCK → SCRAP
- 序列号查询: 支持按物料/批次/状态/仓库筛选

**验收标准**:
- `wm_sn` 表创建完成，CRUD 可用
- 序列号管理物料入库时自动生成SN
- 序列号管理物料出库时必须选择SN
- SN状态流转正确
- 字典数据可用

**涉及文件**:
- 新增: `WmSn.java`, `WmSnBo.java`, `WmSnVo.java`, `WmSnMapper.java`, `IWmSnService.java`, `WmSnServiceImpl.java`, `WmSnController.java`
- 修改: `WmMaterialReceiveExecutorServiceImpl.java` (入库绑定), `WmSalesIssueServiceImpl.java` (出库解绑), `WmProductionIssueServiceImpl.java` (出库解绑), `WmMiscIssueServiceImpl.java` (出库解绑), `WmMiscRecptServiceImpl.java` (杂收绑定)
- SQL: `script/sql/week11_serial_number.sql`

---

#### 任务 11.2: 批次管理增强 (R-042, 1.5人天)

**当前状态**: 完全缺失。`bd_material.is_batch_manage` 字段已预留，各业务表 `batch_id`/`batch_code` 字段已存在，但无 `wm_batch` 批次主表。

**SQL DDL** (遵循 ry-erp.sql 规范):
```sql
DROP TABLE IF EXISTS `wm_batch`;
CREATE TABLE `wm_batch` (
  `batch_id`        bigint(20)   NOT NULL                    COMMENT '批次ID',
  `tenant_id`       varchar(20)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '000000' COMMENT '租户ID',
  `batch_code`      varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '批次编码',
  `material_id`     bigint(20)   NOT NULL                    COMMENT '物料ID',
  `material_code`   varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物料编码',
  `material_name`   varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物料名称',
  `vendor_id`       bigint(20)   DEFAULT NULL                COMMENT '供应商ID',
  `vendor_code`     varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商编码',
  `production_date` date         DEFAULT NULL                COMMENT '生产日期',
  `expiry_date`     date         DEFAULT NULL                COMMENT '有效期',
  `quantity`        decimal(14,4) DEFAULT 0                  COMMENT '批次数量',
  `received_qty`    decimal(14,4) DEFAULT 0                  COMMENT '已收数量',
  `status`          varchar(16)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/EXPIRED/LOCKED/CLOSED)',
  `source_doc_type` varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单据类型',
  `source_doc_id`   bigint(20)   DEFAULT NULL                COMMENT '来源单据ID',
  `rule_id`         bigint(20)   DEFAULT NULL                COMMENT '生成规则ID',
  `attr1`           varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预留字段1',
  `attr2`           varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预留字段2',
  `attr3`           int(11)      DEFAULT NULL                COMMENT '预留字段3',
  `attr4`           int(11)      DEFAULT NULL                COMMENT '预留字段4',
  `create_dept`     bigint(20)   DEFAULT NULL                COMMENT '创建部门',
  `create_by`       bigint(20)   DEFAULT NULL                COMMENT '创建者',
  `create_time`     datetime     DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  `update_by`       bigint(20)   DEFAULT NULL                COMMENT '更新者',
  `update_time`     datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`        char(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_batch_code` (`batch_code`, `tenant_id`) USING BTREE,
  INDEX `idx_tenant_id` (`tenant_id`) USING BTREE,
  INDEX `idx_material` (`material_id`) USING BTREE,
  INDEX `idx_expiry` (`expiry_date`) USING BTREE,
  INDEX `idx_status` (`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '批次管理';

-- 字典: 批次状态
INSERT INTO `sys_dict_type` VALUES (xxx, '000000', '批次状态', 'wm_batch_status', 103, 1, NOW(), NULL, NULL, '批次状态');
INSERT INTO `sys_dict_data` VALUES (xxx, '000000', 1, '有效', 'ACTIVE', 'wm_batch_status', '', 'primary', 'Y', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_dict_data` VALUES (xxx, '000000', 2, '已过期', 'EXPIRED', 'wm_batch_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_dict_data` VALUES (xxx, '000000', 3, '已锁定', 'LOCKED', 'wm_batch_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO `sys_dict_data` VALUES (xxx, '000000', 4, '已关闭', 'CLOSED', 'wm_batch_status', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, '');
```

**后端开发**:
1. 新增 `WmBatch` 实体 + `WmBatchBo` + `WmBatchVo` (含 @AutoMapper 注解)
2. 新增 `WmBatchMapper` + XML
3. 新增 `IWmBatchService` / `WmBatchServiceImpl` (extends ServiceImpl)
4. 新增 `WmBatchController` (RESTful API `/erp/wm/batch`)
5. 改造入库逻辑: `is_batch_manage=1` 的物料入库时自动创建/关联批次
6. 改造 `FefoPickingStrategy`: FEFO策略关联批次有效期
7. 批次有效期检查: SnailJob定时任务扫描即将过期批次并更新状态

**核心业务逻辑**:
- 批次创建: 收货时根据 `BdSerialNumberRule` 规则生成批次号，关联供应商和有效期
- 批次关联: 出入库时自动关联批次，更新批次数量
- 批次状态: ACTIVE → EXPIRED (过期) / LOCKED (锁定) / CLOSED (关闭)
- FEFO出库: 拣货策略优先选择即将过期批次
- 批次预警: 定时扫描30天内即将过期批次，生成预警通知

**验收标准**:
- `wm_batch` 表创建完成，CRUD 可用
- 批次管理物料入库时自动创建批次
- 批次数量与库存数量一致
- FEFO拣货策略正确选择即将过期批次
- 批次过期自动状态更新

**涉及文件**:
- 新增: `WmBatch.java`, `WmBatchBo.java`, `WmBatchVo.java`, `WmBatchMapper.java`, `IWmBatchService.java`, `WmBatchServiceImpl.java`, `WmBatchController.java`
- 新增: `BatchExpiryCheckTask.java` (SnailJob定时任务)
- 修改: `WmMaterialReceiveExecutorServiceImpl.java` (入库创建批次), `FefoPickingStrategy.java` (FEFO关联批次)
- SQL: `script/sql/week11_batch_management.sql`

---

#### 任务 11.3: Jasypt 密文生成 (0.5人天)

**当前状态**: Jasypt 3.0.5 依赖已添加，application.yml 已配置加密器，但 application-dev.yml 中 3 处密码仍为明文。

**执行步骤**:
1. 使用 Maven 命令生成密文:
   ```bash
   mvn jasypt:encrypt -Djasypt.encryptor.password=nexus-erp-dev -Dinput=root
   mvn jasypt:encrypt -Djasypt.encryptor.password=nexus-erp-dev -Dinput=nexus
   ```
2. 修改 `application-dev.yml`:
   - 主库密码: `root` → `ENC(密文)`
   - ERP库密码: `root` → `ENC(密文)`
   - RabbitMQ密码: `nexus` → `ENC(密文)`
3. 验证应用启动正常，日志中无明文密码

**涉及文件**:
- 修改: `ruoyi-admin/src/main/resources/application-dev.yml`

---

#### 任务 11.4: 价格表前端页面 (1.5人天)

**当前状态**: 后端完整 (SoPriceList + PricingEngine + CreditCheck + Controller)，前端完全缺失。

**前端开发**:
1. 新增 `api/erp/so/price-list/index.ts` (API接口)
2. 新增 `api/erp/so/price-list/types.ts` (类型定义)
3. 新增 `views/erp/so/price-list/index.vue` (价格表列表页)
4. 新增 `views/erp/so/price-list/form.vue` (价格表表单页，含行编辑)
5. 路由注册: `router/index.ts` 添加价格表表单路由
6. 改造 `views/erp/so/orderheader/form.vue`: 订单行选择物料时自动调用价格引擎

**价格表页面功能**:
- 列表页: 支持按类型(基价/客户专属/促销)筛选，展示有效期、状态
- 表单页: 头信息(类型/客户/有效期) + 行信息(物料/单价/数量区间/折扣率) 可编辑表格
- 订单集成: 销售订单行选择物料后自动取价，展示价格来源

**验收标准**:
- 价格表 CRUD 页面完整可用
- 3种价格类型(基价/客户专属/促销)全部支持
- 销售订单自动取价功能可用
- 数量区间匹配正确

**涉及文件**:
- 新增: `api/erp/so/price-list/index.ts`, `api/erp/so/price-list/types.ts`
- 新增: `views/erp/so/price-list/index.vue`, `views/erp/so/price-list/form.vue`
- 修改: `router/index.ts` (添加路由)
- 修改: `views/erp/so/orderheader/form.vue` (价格自动带出)

---

### 2.2 Week 12: 追溯服务 + 追溯前端 + 盘点审批流 + 单元测试

> **依赖关系**: 追溯服务(R-043)依赖序列号(R-041)和批次管理(R-042)的数据表

#### 任务 12.1: 追溯查询服务 (R-043, 2人天)

**当前状态**: 完全缺失。`wm_material_stock_tx` 表记录了库存事务(含 `source_doc_type`/`source_doc_id`)，是追溯的数据基础，但无追溯查询接口。

**后端开发**:
1. 新增 `IWmTraceService` / `WmTraceServiceImpl` (追溯查询服务)
2. 新增 `WmTraceController` (RESTful API `/erp/wm/trace`)
3. 新增 `WmTraceNode` / `WmTraceResult` (追溯结果DTO)

**追溯算法**:

```
反向追溯 (成品→原材料):
  输入: 物料ID + 批次号/序列号
  1. 查 wm_sn/wm_batch 找到该物料的入库记录
  2. 查 wm_material_stock_tx 找到来源单据 (source_doc_type/id)
  3. 递归追溯到原材料采购入库
  输出: 树形结构追溯链

正向追溯 (原材料→成品):
  输入: 物料ID + 批次号/序列号
  1. 查 wm_sn/wm_batch 找到该原材料被哪些工单/生产入库消耗
  2. 查 wm_material_stock_tx 找到消耗去向
  3. 递归追溯到最终成品出库
  输出: 树形结构追溯链
```

**API 设计**:
- `GET /erp/wm/trace/backward?materialId=&batchCode=&snCode=` 反向追溯
- `GET /erp/wm/trace/forward?materialId=&batchCode=&snCode=` 正向追溯
- `GET /erp/wm/trace/detail?traceId=` 追溯节点详情

**验收标准**:
- 反向追溯: 从成品可追溯到原材料采购入库
- 正向追溯: 从原材料可追溯到成品出库
- 追溯链完整，无断链
- 支持按批次号/序列号追溯

**涉及文件**:
- 新增: `IWmTraceService.java`, `WmTraceServiceImpl.java`, `WmTraceController.java`
- 新增: `WmTraceNode.java`, `WmTraceResult.java` (DTO)
- SQL: 无新表，基于现有 `wm_material_stock_tx` / `wm_sn` / `wm_batch` 查询

---

#### 任务 12.2: 追溯前端 (R-044, 1人天)

**前端开发**:
1. 新增 `api/erp/wm/trace/index.ts` (API接口)
2. 新增 `api/erp/wm/trace/types.ts` (类型定义)
3. 新增 `views/erp/wm/trace/index.vue` (追溯查询页面)

**页面功能**:
- 查询表单: 物料选择 + 批次号/序列号输入 + 追溯方向(正向/反向)
- 结果展示: 树形结构展开 (El-Tree)，节点显示物料/批次/数量/来源单据
- 节点详情: 点击节点展示详细信息 (入库时间/供应商/工单号等)
- 表格模式: 切换为表格视图展示追溯链

**验收标准**:
- 追溯查询页面可用
- 正向/反向追溯切换正常
- 树形结构正确展开
- 节点详情可查看

**涉及文件**:
- 新增: `api/erp/wm/trace/index.ts`, `api/erp/wm/trace/types.ts`
- 新增: `views/erp/wm/trace/index.vue`

---

#### 任务 12.3: 盘点差异审批流 (R-055, 2人天)

**当前状态**: 盘点CRUD完整 (WmStockTakingPlan全套)，但未集成Flowable审批流。同模块的收货/调拨/销售出库均已集成审批流。**项目中无任何 BPMN 流程定义文件**，需同步创建。

**改造内容**:

**Part A: 创建 BPMN 流程定义** (0.5人天):
1. 新增 `processes/stocktaking-approval.bpmn20.xml` 流程定义文件
2. 定义流程节点: 发起审批 → 部门主管审批 → 仓库主管审批 → 结束
3. 配置 Flowable 表单变量: 差异金额、差异原因、处理意见

**Part B: 后端代码改造** (1.5人天):
1. `WmStockTakingPlan.java`: 新增 `actProcessId` 字段
2. `WmStockTakingPlanServiceImpl.java`:
   - 注入 `IWfProcessService`, `RuntimeService`, `HistoryService`
   - 新增 `submit()` 方法: 提交审批
   - 新增 `afterFlowHandle()` 方法: 审批通过后执行盘点调整
   - 新增 `getProcInsId()` 方法
   - 新增 `getBusinessDataById()` 方法
3. `WmStockTakingPlanController.java`: 新增提交审批接口
4. `WmStockTakingPlanVo.java`: 新增审批状态字段

**审批流集成模式** (参照 `WmMaterialReceiveCrudServiceImpl`):
```java
// 提交审批
public void submit(Long id) {
    WmStockTakingPlan plan = baseMapper.selectById(id);
    // 校验状态
    if (!"DRAFT".equals(plan.getStatus())) {
        throw new ServiceException("只有草稿状态可提交审批");
    }
    // 发起流程
    wfProcessService.startProcess(plan, ...);
    // 更新状态
    plan.setStatus("PENDING_APPROVAL");
    baseMapper.updateById(plan);
}

// 审批回调
public void afterFlowHandle(WmStockTakingPlan plan, ActStatus actStatus) {
    if (ActStatus.pass == actStatus) {
        // 审批通过: 执行盘点调整
        countAdjustStrategy.execute(plan);
        plan.setStatus("COMPLETED");
    } else {
        plan.setStatus("REJECTED");
    }
    baseMapper.updateById(plan);
}
```

**验收标准**:
- BPMN 流程定义文件可部署
- 盘点计划可提交审批
- 审批通过后自动执行盘点调整
- 审批拒绝后状态回退
- 审批状态可查询

**涉及文件**:
- 新增: `processes/stocktaking-approval.bpmn20.xml` (流程定义)
- 修改: `WmStockTakingPlan.java` (新增字段)
- 修改: `WmStockTakingPlanServiceImpl.java` (审批流集成)
- 修改: `WmStockTakingPlanController.java` (新增接口)
- 修改: `WmStockTakingPlanVo.java` (新增字段)
- SQL: `script/sql/week12_stocktaking_approval.sql` (ALTER TABLE + 流程定义)

---

#### 任务 12.4: 核心业务单元测试补齐 (1人天)

**当前状态**: ERP模块零测试覆盖，仅有4个框架级示例测试。

**优先测试范围**:

| 测试类 | 测试目标 | 测试用例数 |
|--------|---------|-----------|
| `PricingEngineServiceImplTest` | 价格引擎四级取价 + 数量区间 + 折扣率 | 8 |
| `CreditCheckServiceImplTest` | 信用冻结/释放/超限/告警 | 6 |
| `WmMaterialReceiveExecutorServiceImplTest` | 入库执行 + 库存转移 + 序列号绑定 | 5 |
| `WmTraceServiceImplTest` | 正向/反向追溯 | 4 |
| `PuPurchaseReceiveWritebackServiceImplTest` | 采购回写逻辑 | 3 |

**验收标准**:
- 核心Service单元测试通过
- 测试覆盖率 > 60% (目标Service)

**涉及文件**:
- 新增: `src/test/java/com/nexus/erp/so/PricingEngineServiceImplTest.java`
- 新增: `src/test/java/com/nexus/erp/so/CreditCheckServiceImplTest.java`
- 新增: `src/test/java/com/nexus/erp/wm/WmMaterialReceiveExecutorServiceImplTest.java`
- 新增: `src/test/java/com/nexus/erp/wm/WmTraceServiceImplTest.java`
- 新增: `src/test/java/com/nexus/erp/pu/PuPurchaseReceiveWritebackServiceImplTest.java`

---

#### 任务 12.5: 前端构建验证 + 性能基线 (0.5人天)

**当前状态**: Week 9-10 验收标准中"前端构建"和"代码检查"标记为"待验证"。

**执行步骤**:
1. 运行 `npm run build:prod` 验证前端构建
2. 运行 `npm run lint:eslint` 验证代码规范
3. 对关键接口进行简单压测建立性能基线:
   - 价格引擎查询 (`PricingEngineService.resolvePrice`)
   - 库存查询 (`WmMaterialStockController.selectPageList`)
   - 采购订单列表 (`PuPurchaseOrderController.selectPageList`)

**验收标准**:
- 前端构建无错误
- ESLint 检查通过
- 关键接口响应时间 < 500ms

---

### 2.3 Week 11-12 任务汇总

| 周次 | 任务编号 | 任务内容 | 模块 | 人天 | 优先级 | 依赖 | 状态 |
|------|---------|---------|------|------|--------|------|------|
| Week 11 | R-041 | 序列号管理 (wm_sn表+CRUD+出入库绑定) | wm | 2 | P0 | - | ✅ 已完成 |
| Week 11 | R-042 | 批次管理增强 (wm_batch表+生命周期) | wm | 1.5 | P0 | - | ✅ 已完成 |
| Week 11 | Q-007 | Jasypt 密文生成 | infra | 0.5 | P1 | - | ✅ 已完成 |
| Week 11 | F-041 | 价格表前端页面 (CRUD+订单集成) | so前端 | 1.5 | P0 | - | ✅ 已完成 |
| Week 12 | R-043 | 追溯查询服务 (正向+反向) | wm | 2 | P0 | R-041, R-042 | ✅ 已完成 |
| Week 12 | R-044 | 追溯前端 (树图/表格) | wm前端 | 1 | P1 | R-043 | ✅ 已完成 |
| Week 12 | R-055 | 盘点差异审批流 (BPMN+Flowable集成) | wm | 2 | P1 | - | ✅ 已完成 |
| Week 12 | T-001 | 核心业务单元测试补齐 | 全模块 | 1 | P1 | R-043 | ✅ 已完成 |
| Week 12 | V-001 | 前端构建验证 + 性能基线 | 全模块 | 0.5 | P2 | - | ✅ 已完成 |
| | | **合计** | | **12** | | | |

**里程碑 M7 验收标准**:
- ✅ ERP全部P0/P1任务完成 (qc/eq由MES统一实现)
- ✅ 序列号/批次/追溯/盘点审批流功能可用
- ✅ 价格表前端页面完整
- ✅ Jasypt 密文已生成，密码不再明文
- ✅ 核心Service单元测试通过 (26个测试全部通过)
- ✅ ESLint检查通过 (前端构建失败为已有问题: DispTag组件缺失)
- ✅ Maven编译通过，无breaking change

---

## 三、Phase 2: MES 建设 (Week 13-34)

### 3.1 MES Phase 1: 基础骨架 + 主数据 (Week 13-20)

#### Week 13: MES模块创建 + 数据源配置 + MQ消息定义

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-001 | 创建ruoyi-mes模块、pom.xml、包结构 | 模块可编译 | 1 |
| MES-002 | 配置ry-mes数据源 (MySQL独立库) | 数据源可用 | 0.5 |
| MES-003 | MES公共类: 枚举(MesStatus/MesProcessType) + 常量 + 工具 | 基础类就绪 | 0.5 |
| MES-004 | mes_md_*全部表DDL + 初始化SQL | 数据库表就绪 | 1 |
| MES-005 | ERP→MES MQ消息类型定义 (MesWorkorderMessage/MesWoCompleteMessage) | MQ消息就绪 | 0.5 |
| MES-006 | MQ交换机/队列/路由键注册 (ruoyi-common-mq常量扩展) | MQ基础设施就绪 | 0.5 |

**MQ消息定义** (ERP→MES联动):
```java
// ERP工单下达 → MES创建工单
public class MesWorkorderMessage extends BaseMessage {
    private Long erpWoId;       // ERP工单ID
    private Long materialId;    // 产品物料ID
    private BigDecimal quantity; // 计划数量
    private Long routeId;       // 工艺路线ID
}

// MES工单完工 → ERP工单完工
public class MesWoCompleteMessage extends BaseMessage {
    private Long erpWoId;       // ERP工单ID
    private BigDecimal completedQty; // 完工数量
}
```

**MES模块结构**:
```
ruoyi-modules/ruoyi-mes/
  pom.xml
  src/main/java/com/nexus/mes/
    ├── config/          # MES自动配置
    ├── common/          # 枚举/常量/工具
    ├── md/              # 主数据 (扩展ERP bd)
    ├── pro/             # 生产执行 (核心模块)
    ├── wm/              # 仓储扩展
    ├── qc/              # 质量控制
    ├── eq/              # 设备管理
    ├── dc/              # 数据采集
    └── spc/             # 统计过程控制
```

**MES主数据表 (mes_md_*)**:
```sql
-- MES工位资源 (扩展ERP bd_workstation)
CREATE TABLE mes_md_workstation (
    workstation_id  BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    workstation_code VARCHAR(64) NOT NULL,
    workstation_name VARCHAR(128) NOT NULL,
    workshop_id     BIGINT COMMENT '车间ID(关联ERP bd_workshop)',
    bd_workstation_id BIGINT COMMENT 'ERP工位ID',
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (workstation_id),
    UNIQUE KEY uk_code (workstation_code, tenant_id)
);

-- MES工序BOM (工序级投料)
CREATE TABLE mes_md_product_bom (
    bom_id          BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    material_id     BIGINT NOT NULL,
    process_id      BIGINT NOT NULL,
    component_id    BIGINT NOT NULL COMMENT '子件物料ID',
    quantity        DECIMAL(14,4) NOT NULL,
    PRIMARY KEY (bom_id),
    INDEX idx_material_process (material_id, process_id)
);

-- MES SOP (标准作业指导)
CREATE TABLE mes_md_product_sop (
    sop_id          BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    material_id     BIGINT NOT NULL,
    process_id      BIGINT NOT NULL,
    sop_name        VARCHAR(128) NOT NULL,
    sop_url         VARCHAR(512) COMMENT '文件URL',
    PRIMARY KEY (sop_id)
);

-- MES SIP (标准检验指导)
CREATE TABLE mes_md_product_sip (
    sip_id          BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    material_id     BIGINT NOT NULL,
    process_id      BIGINT NOT NULL,
    sip_name        VARCHAR(128) NOT NULL,
    sip_url         VARCHAR(512) COMMENT '文件URL',
    PRIMARY KEY (sip_id)
);
```

---

#### Week 14: 主数据CRUD + 工序定义 + 工艺路线

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-007 | MES主数据CRUD: 工位资源(mes_md_workstation) + SOP/SIP | MD可用 | 2 |
| MES-008 | 工序定义 (mes_pro_process) CRUD | 工序管理可用 | 1.5 |
| MES-009 | 工艺路线 (mes_pro_route + mes_pro_route_process) CRUD | 路线管理可用 | 1.5 |

**核心表**:
```sql
-- 工序定义
CREATE TABLE mes_pro_process (
    process_id      BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    process_code    VARCHAR(64) NOT NULL,
    process_name    VARCHAR(128) NOT NULL,
    process_type    VARCHAR(32) COMMENT '加工/检验/搬运/存储',
    standard_time   DECIMAL(10,2) COMMENT '标准工时(分钟)',
    description     VARCHAR(512),
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (process_id),
    UNIQUE KEY uk_code (process_code, tenant_id)
);

-- 工艺路线
CREATE TABLE mes_pro_route (
    route_id        BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    route_code      VARCHAR(64) NOT NULL,
    route_name      VARCHAR(128) NOT NULL,
    route_version   VARCHAR(16) NOT NULL DEFAULT 'V1.0',
    material_id     BIGINT NOT NULL COMMENT '产品物料ID',
    status          VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/INACTIVE',
    PRIMARY KEY (route_id),
    UNIQUE KEY uk_code_version (route_code, route_version, tenant_id)
);

-- 工艺路线工序 (路线-工序关联)
CREATE TABLE mes_pro_route_process (
    rp_id           BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    route_id        BIGINT NOT NULL,
    process_id      BIGINT NOT NULL,
    seq_no          INT NOT NULL COMMENT '工序顺序',
    is_key_process  TINYINT DEFAULT 0 COMMENT '是否关键工序',
    is_inspection   TINYINT DEFAULT 0 COMMENT '是否检验点',
    workstation_id  BIGINT COMMENT '默认工位',
    PRIMARY KEY (rp_id),
    INDEX idx_route (route_id)
);
```

---

#### Week 15: MES工单 CRUD + ERP→MES联动

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-010 | MES工单 (mes_pro_workorder) CRUD，关联ERP工单 | MES工单可用 | 2.5 |
| MES-011 | ERP→MES工单联动: ERP工单下达时MQ消息触发MES创建工单 | 联动可用 | 1 |
| MES-012 | MES→ERP工单回写: MES工单完工时MQ消息触发ERP工单完工 | 回写可用 | 0.5 |

**核心表**:
```sql
-- MES工单
CREATE TABLE mes_pro_workorder (
    mes_wo_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    wo_no           VARCHAR(64) NOT NULL,
    erp_wo_id       BIGINT NOT NULL COMMENT 'ERP工单ID',
    erp_wo_no       VARCHAR(64) COMMENT 'ERP工单编号',
    material_id     BIGINT NOT NULL COMMENT '产品物料ID',
    material_code   VARCHAR(64),
    material_name   VARCHAR(128),
    quantity        DECIMAL(14,4) NOT NULL COMMENT '计划数量',
    completed_qty   DECIMAL(14,4) DEFAULT 0 COMMENT '完成数量',
    route_id        BIGINT NOT NULL COMMENT '工艺路线ID',
    plan_start_time DATETIME,
    plan_end_time   DATETIME,
    status          VARCHAR(16) DEFAULT 'CREATED' COMMENT 'CREATED/RELEASED/STARTED/COMPLETED/CLOSED',
    PRIMARY KEY (mes_wo_id),
    UNIQUE KEY uk_wo_no (wo_no, tenant_id),
    INDEX idx_erp_wo (erp_wo_id)
);
```

**ERP→MES联动**:
- ERP工单下达 (status=RELEASED) → MQ消息 → MES消费者创建MES工单
- MES工单状态回写: COMPLETED → MQ消息 → ERP工单完工

---

#### Week 16: 排班日历复用 + MES工位排班

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-013 | MES工位排班: 复用sch模块排班数据 | 排班可用 | 2 |

---

#### Week 17-18: MES前端页面

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-014 | 前端: MD(BOM/SOP/SIP/工位资源)页面 | 主数据可视 | 2.5 |
| MES-015 | 前端: 工序/路线管理页面 | 工序/路线可视 | 2.5 |
| MES-016 | 前端: MES工单 + 排班日历页面 | 工单/排班可视 | 2.5 |
| MES-017 | 前端路由注册 + 菜单SQL | 前端路由就绪 | 0.5 |

---

#### Week 19-20: 联调测试 + 缓冲

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-018 | 联调测试 + Swagger文档 + API文档 | — | 3 |
| MES-019 | 缓冲周: 性能优化+回归测试 | — | 4 |

**MES Phase 1 里程碑 M8**: MES基础上线，主数据/工单/工序/路线管理可用

---

### 3.2 MES Phase 2: 流转卡 + 执行核心 (Week 21-27)

#### Week 21: 流转卡引擎

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-020 | 流转卡引擎: generateCards() + releaseCard() + 状态机 | 流转卡生成可用 | 5 |

**流转卡核心表**:
```sql
-- 流转卡 (MES最核心设计)
CREATE TABLE mes_pro_card (
    card_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    card_no         VARCHAR(64) NOT NULL,
    mes_wo_id       BIGINT NOT NULL,
    route_id        BIGINT NOT NULL,
    material_id     BIGINT NOT NULL,
    quantity        DECIMAL(14,4) NOT NULL,
    completed_qty   DECIMAL(14,4) DEFAULT 0,
    scrap_qty       DECIMAL(14,4) DEFAULT 0,
    status          VARCHAR(16) DEFAULT 'CREATED' COMMENT 'CREATED/RELEASED/STARTED/COMPLETED/CLOSED',
    PRIMARY KEY (card_id),
    UNIQUE KEY uk_card_no (card_no),
    INDEX idx_wo (mes_wo_id)
);

-- 流转卡工序记录 (最核心的MES数据表)
CREATE TABLE mes_pro_card_process (
    ccp_id          BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    card_id         BIGINT NOT NULL,
    rp_id           BIGINT NOT NULL COMMENT '路线工序ID',
    seq_no          INT NOT NULL,
    process_code    VARCHAR(64),
    process_name    VARCHAR(128),
    workstation_id  BIGINT,
    equipment_id    BIGINT,
    operator_id     BIGINT,
    qty_processed   DECIMAL(14,4) DEFAULT 0,
    qty_good        DECIMAL(14,4) DEFAULT 0,
    qty_scrap       DECIMAL(14,4) DEFAULT 0,
    start_time      DATETIME,
    end_time        DATETIME,
    status          VARCHAR(16) DEFAULT 'WAITING' COMMENT 'WAITING/PROCESSING/COMPLETED/SKIPPED',
    PRIMARY KEY (ccp_id),
    INDEX idx_card (card_id)
);
```

**流转卡状态机**:
```
CREATED → RELEASED → STARTED → COMPLETED → CLOSED
              ↓          ↓
           CANCELED   CANCELED
```

---

#### Week 22: 流转卡工序执行

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-021 | 流转卡工序: startProcess() + completeProcess() + 工序报工 | 工序执行可用 | 5 |

---

#### Week 23: SN级追溯

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-022 | SN级追溯: ProSnProcess + 序列号绑定 | SN追溯可用 | 4 |

```sql
-- SN工序追溯
CREATE TABLE mes_pro_sn_process (
    sn_process_id   BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    sn_code         VARCHAR(128) NOT NULL,
    card_id         BIGINT NOT NULL,
    ccp_id          BIGINT NOT NULL COMMENT '流转卡工序ID',
    process_code    VARCHAR(64),
    workstation_id  BIGINT,
    operator_id     BIGINT,
    in_time         DATETIME,
    out_time        DATETIME,
    result          VARCHAR(16) DEFAULT 'PASS' COMMENT 'PASS/FAIL',
    PRIMARY KEY (sn_process_id),
    INDEX idx_sn (sn_code),
    INDEX idx_card (card_id)
);
```

---

#### Week 24: 派工任务 + 工序转移

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-023 | 派工任务 (mes_pro_task) CRUD | 派工可用 | 2 |
| MES-024 | 工序转移 (mes_pro_trans_order) | 转移可用 | 2 |

---

#### Week 25: 安灯系统

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-025 | 安灯系统: 触发/响应/解决/升级 | 安灯可用 | 4 |

```sql
-- 安灯记录
CREATE TABLE mes_pro_andon (
    andon_id        BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    andon_type      VARCHAR(32) NOT NULL COMMENT 'QUALITY/EQUIPMENT/MATERIAL/OTHER',
    card_id         BIGINT,
    ccp_id          BIGINT,
    workstation_id  BIGINT,
    description     VARCHAR(512),
    trigger_by      BIGINT,
    trigger_time    DATETIME NOT NULL,
    response_by     BIGINT,
    response_time   DATETIME,
    resolve_by      BIGINT,
    resolve_time    DATETIME,
    status          VARCHAR(16) DEFAULT 'TRIGGERED' COMMENT 'TRIGGERED/RESPONDED/RESOLVED/ESCALATED',
    PRIMARY KEY (andon_id)
);
```

---

#### Week 26-27: 前端 + 集成测试

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-026 | 前端: 流转卡管理/工序执行/派工看板/安灯看板 | 执行核心可视 | 5 |
| MES-027 | 集成测试 + 联调 | — | 3 |

**MES Phase 2 里程碑 M9**: MES执行核心上线，流转卡/派工/报工/安灯可用

---

### 3.3 MES Phase 3: 仓库/质量/设备/数据采集 (Week 28-34)

#### Week 28: MES仓库扩展

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-028 | MES仓库扩展: 领料/调拨/盘点/委外(三级单据模式) | 仓库扩展可用 | 5 |

---

#### Week 29: MES质量控制

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-029 | IQC来料检验 CRUD + 检验方案 | IQC可用 | 2 |
| MES-030 | IPQC过程检验 CRUD + 关联流转卡 | IPQC可用 | 2 |
| MES-031 | OQC出厂检验 CRUD | OQC可用 | 1 |

**质量检验核心表**:
```sql
-- IQC来料检验
CREATE TABLE mes_qc_iqc (
    iqc_id          BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    iqc_no          VARCHAR(64) NOT NULL,
    receive_id      BIGINT COMMENT 'ERP收货单ID',
    material_id     BIGINT NOT NULL,
    batch_id        BIGINT,
    sample_qty      DECIMAL(14,4),
    qualified_qty   DECIMAL(14,4) DEFAULT 0,
    unqualified_qty DECIMAL(14,4) DEFAULT 0,
    result          VARCHAR(16) COMMENT 'PASS/FAIL/CONDITIONAL',
    status          VARCHAR(16) DEFAULT 'PENDING',
    PRIMARY KEY (iqc_id),
    UNIQUE KEY uk_iqc_no (iqc_no, tenant_id)
);

-- IPQC过程检验
CREATE TABLE mes_qc_ipqc (
    ipqc_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    ipqc_no         VARCHAR(64) NOT NULL,
    card_id         BIGINT COMMENT '流转卡ID',
    ccp_id          BIGINT NOT NULL COMMENT '流转卡工序ID',
    sample_qty      DECIMAL(14,4),
    qualified_qty   DECIMAL(14,4) DEFAULT 0,
    unqualified_qty DECIMAL(14,4) DEFAULT 0,
    result          VARCHAR(16) COMMENT 'PASS/FAIL',
    status          VARCHAR(16) DEFAULT 'PENDING',
    PRIMARY KEY (ipqc_id),
    UNIQUE KEY uk_ipqc_no (ipqc_no, tenant_id)
);

-- OQC出厂检验
CREATE TABLE mes_qc_oqc (
    oqc_id          BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    oqc_no          VARCHAR(64) NOT NULL,
    card_id         BIGINT COMMENT '流转卡ID',
    material_id     BIGINT NOT NULL,
    quantity        DECIMAL(14,4),
    sample_qty      DECIMAL(14,4),
    qualified_qty   DECIMAL(14,4) DEFAULT 0,
    unqualified_qty DECIMAL(14,4) DEFAULT 0,
    result          VARCHAR(16) COMMENT 'PASS/FAIL',
    status          VARCHAR(16) DEFAULT 'PENDING',
    PRIMARY KEY (oqc_id),
    UNIQUE KEY uk_oqc_no (oqc_no, tenant_id)
);

-- 缺陷记录
CREATE TABLE mes_qc_defect (
    defect_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    qc_type         VARCHAR(16) NOT NULL COMMENT 'IQC/IPQC/OQC',
    qc_id           BIGINT NOT NULL,
    defect_code     VARCHAR(64) NOT NULL,
    defect_name     VARCHAR(128),
    defect_qty      DECIMAL(14,4) DEFAULT 0,
    severity        VARCHAR(16) DEFAULT 'MINOR' COMMENT 'CRITICAL/MAJOR/MINOR',
    PRIMARY KEY (defect_id)
);
```

---

#### Week 30: MES设备管理

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-032 | 设备台账 CRUD + 设备分类 | 设备台账可用 | 2 |
| MES-033 | 点检计划 + 点检执行 | 点检可用 | 1 |
| MES-034 | 保养计划 + 保养记录 + 维修工单 | 保养维修可用 | 1 |

**设备管理核心表**:
```sql
-- 设备台账
CREATE TABLE mes_equipment (
    equipment_id    BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    equipment_code  VARCHAR(64) NOT NULL,
    equipment_name  VARCHAR(128) NOT NULL,
    type_id         BIGINT NOT NULL COMMENT '设备分类ID',
    model           VARCHAR(128),
    manufacturer    VARCHAR(128),
    purchase_date   DATE,
    workstation_id  BIGINT COMMENT '绑定工位ID',
    status          VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/MAINTENANCE/SCRAPPED',
    PRIMARY KEY (equipment_id),
    UNIQUE KEY uk_code (equipment_code, tenant_id)
);

-- 设备分类
CREATE TABLE mes_equipment_type (
    type_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    type_code       VARCHAR(64) NOT NULL,
    type_name       VARCHAR(128) NOT NULL,
    PRIMARY KEY (type_id),
    UNIQUE KEY uk_code (type_code, tenant_id)
);

-- 点检计划
CREATE TABLE mes_check_plan (
    plan_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    plan_name       VARCHAR(128) NOT NULL,
    equipment_id    BIGINT NOT NULL,
    check_frequency VARCHAR(16) COMMENT 'DAILY/WEEKLY/MONTHLY',
    check_items     JSON COMMENT '点检项目',
    next_check_date DATE,
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (plan_id)
);

-- 点检记录
CREATE TABLE mes_check_record (
    record_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    plan_id         BIGINT NOT NULL,
    equipment_id    BIGINT NOT NULL,
    check_date      DATE NOT NULL,
    checker_id      BIGINT,
    result          VARCHAR(16) COMMENT 'NORMAL/ABNORMAL',
    remark          VARCHAR(512),
    PRIMARY KEY (record_id)
);

-- 保养计划
CREATE TABLE mes_maintenance_plan (
    plan_id         BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    plan_name       VARCHAR(128) NOT NULL,
    equipment_id    BIGINT NOT NULL,
    maintenance_type VARCHAR(16) COMMENT 'PREVENTIVE/CORRECTIVE',
    frequency       VARCHAR(16) COMMENT 'DAILY/WEEKLY/MONTHLY/QUARTERLY',
    next_date       DATE,
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (plan_id)
);

-- 保养记录
CREATE TABLE mes_maintenance_record (
    record_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    plan_id         BIGINT NOT NULL,
    equipment_id    BIGINT NOT NULL,
    maintenance_date DATE NOT NULL,
    maintainer_id   BIGINT,
    content         TEXT,
    status          VARCHAR(16) DEFAULT 'COMPLETED',
    PRIMARY KEY (record_id)
);

-- 维修工单
CREATE TABLE mes_repair_order (
    repair_id       BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    repair_no       VARCHAR(64) NOT NULL,
    equipment_id    BIGINT NOT NULL,
    fault_desc      VARCHAR(512),
    reporter_id     BIGINT,
    repairer_id     BIGINT,
    repair_start    DATETIME,
    repair_end      DATETIME,
    status          VARCHAR(16) DEFAULT 'REPORTED' COMMENT 'REPORTED/REPAIRING/COMPLETED',
    PRIMARY KEY (repair_id),
    UNIQUE KEY uk_repair_no (repair_no, tenant_id)
);
```

---

#### Week 31: 数据采集 + SPC控制图

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-035 | 数据采集 (mes_dc_collect) + 手动/自动采集 | DC可用 | 3 |
| MES-036 | SPC控制图 + 超控告警 | SPC可用 | 2 |

```sql
-- 数据采集
CREATE TABLE mes_dc_collect (
    collect_id      BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    card_id         BIGINT,
    ccp_id          BIGINT,
    param_code      VARCHAR(64) NOT NULL COMMENT '参数编码',
    param_name      VARCHAR(128) COMMENT '参数名称',
    param_value     DECIMAL(14,4) COMMENT '参数值',
    unit            VARCHAR(32) COMMENT '单位',
    collect_type    VARCHAR(16) DEFAULT 'MANUAL' COMMENT 'MANUAL/AUTO',
    collect_time    DATETIME NOT NULL,
    collector_id    BIGINT,
    PRIMARY KEY (collect_id),
    INDEX idx_card (card_id),
    INDEX idx_param_time (param_code, collect_time)
);

-- SPC控制图
CREATE TABLE mes_spc_chart (
    chart_id        BIGINT NOT NULL,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    chart_name      VARCHAR(128) NOT NULL,
    param_code      VARCHAR(64) NOT NULL,
    ucl             DECIMAL(14,4) COMMENT '控制上限',
    cl              DECIMAL(14,4) COMMENT '中心线',
    lcl             DECIMAL(14,4) COMMENT '控制下限',
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    PRIMARY KEY (chart_id)
);
```

---

#### Week 32: QC/EQ/DC/SPC前端页面

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-037 | 前端: qc(IQC/IPQC/OQC/缺陷)页面 | 质量前端完整 | 2 |
| MES-038 | 前端: eq(台账/点检/保养/维修)页面 | 设备前端完整 | 2 |
| MES-039 | 前端: dc/spc页面 | DC/SPC前端完整 | 1 |

---

#### Week 33-34: 全模块集成测试 + 缓冲

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| MES-040 | ERP↔MES跨库事务/MQ联动/数据一致性 | 集成问题修复 | 4 |
| MES-041 | 缓冲周: 性能优化+回归测试+文档补齐 | — | 4 |

**MES Phase 3 里程碑 M10**: MES全部功能交付，质量/设备/数据采集/SPC完整

---

### 3.4 MES 建设人天汇总

| 阶段 | 周次 | 人天 |
|------|------|------|
| MES Phase 1: 基础骨架 | Week 13-20 | ~30 |
| MES Phase 2: 执行核心 | Week 21-27 | ~30 |
| MES Phase 3: 扩展模块 | Week 28-34 | ~30 |
| **合计** | **Week 13-34** | **~90** |

---

## 四、Phase 3: IoT 建设 (Week 35-50)

### 4.1 IoT Phase 1: 基础设施 + 设备管理 (Week 35-39)

#### Week 35: IoT模块创建 + 设备管理

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-001 | 创建ruoyi-iot模块、pom.xml、包结构 | 模块可编译 | 1 |
| IoT-002 | 配置ry-iot数据源 | 数据源可用 | 0.5 |
| IoT-003 | iot_device + iot_product + iot_thing_model核心表+CRUD | 设备管理可用 | 3.5 |

**IoT模块结构**:
```
ruoyi-modules/ruoyi-iot/
  pom.xml
  src/main/java/com/nexus/iot/
    ├── config/          # IoT自动配置+协议配置
    ├── common/          # 枚举/常量/DTO
    ├── device/          # 设备管理
    ├── protocol/        # 协议适配
    │   ├── mqtt/        # MQTT协议
    │   ├── opcua/       # OPC UA协议
    │   ├── modbus/      # Modbus协议
    │   └── http/        # HTTP协议
    ├── data/            # 数据处理
    │   ├── pipeline/    # 数据管道
    │   ├── tsdb/        # 时序数据库
    │   └── realtime/    # 实时数据
    ├── rule/            # 规则引擎
    ├── alert/           # 告警管理
    ├── scene/           # 场景联动
    └── gateway/         # 边缘网关
```

---

#### Week 36: MQTT Broker集成

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-004 | MQTT Broker集成(Moquette)+设备认证+设备注册/密钥管理 | MQTT连接可用 | 5 |

---

#### Week 37: HTTP数据上报

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-005 | HTTP数据上报端点+设备在线状态管理(心跳检测) | HTTP上报可用 | 4 |

---

#### Week 38: 设备分组/标签 + 物模型

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-006 | 设备分组/标签+设备日志+物模型(产品→属性) | 设备管理完整 | 4 |

---

#### Week 39: IoT前端页面

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-007 | 前端: 设备管理/分组/产品/物模型页面 | 前端可视可用 | 5 |

---

### 4.2 IoT Phase 2: 协议 + 数据管道 (Week 40-44)

#### Week 40: OPC UA客户端

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-008 | OPC UA客户端(Eclipse Milo): 连接/浏览/订阅/重连 | OPC UA可用 | 5 |

---

#### Week 41: Modbus驱动

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-009 | Modbus驱动: TCP/RTU+轮询调度+寄存器映射 | Modbus可用 | 5 |

---

#### Week 42: 数据管道

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-010 | 数据管道: 清洗→聚合→转发(DataPipelineService) | 数据管道可用 | 4 |

---

#### Week 43: 时序数据库 + 实时数据

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-011 | 时序数据库集成(InfluxDB)+Redis实时缓存+WebSocket推送 | 实时数据可用 | 5 |

---

#### Week 44: 实时数据前端

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-012 | 前端: 实时数据页面(ECharts)+协议配置页面 | 数据可视可用 | 4 |

**IoT Phase 2 里程碑 M11**: IoT多协议数据采集完成

---

### 4.3 IoT Phase 3: 规则 + 告警 + 场景联动 + 边缘 (Week 45-50)

#### Week 45: 规则引擎

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-013 | 规则引擎(Easy-Rules): 阈值/趋势/表达式规则 | 规则引擎可用 | 5 |

---

#### Week 46: 告警系统

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-014 | 告警系统: 规则→触发→通知(系统/邮件/Webhook)+告警升级 | 告警完整闭环 | 5 |

---

#### Week 47: 边缘网关

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-015 | 边缘网关管理+边缘脚本引擎(Groovy)+数据缓冲 | 边缘计算可用 | 5 |

---

#### Week 48: 场景联动

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-016 | 场景联动: 触发器→条件→动作+固件OTA | 场景联动+OTA可用 | 4 |

---

#### Week 49: 全链路集成

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-017 | IoT↔MQ↔MES全链路集成: 设备数据→MES联动(自动报工/质量触发) | 端到端集成 | 5 |

---

#### Week 50: 整体测试 + 大屏看板

| 任务编号 | 任务内容 | 产出 | 人天 |
|---------|---------|------|------|
| IoT-018 | 整体测试+IoT大屏看板+文档 | 全部上线 | 5 |

**IoT Phase 3 里程碑 M12**: ERP+MES+IoT一体化平台交付

---

### 4.4 IoT 建设人天汇总

| 阶段 | 周次 | 人天 |
|------|------|------|
| IoT Phase 1: 基础设施 | Week 35-39 | ~19.5 |
| IoT Phase 2: 协议+数据 | Week 40-44 | ~23 |
| IoT Phase 3: 智能+集成 | Week 45-50 | ~29 |
| **合计** | **Week 35-50** | **~71.5** |

---

## 五、总体时间线与里程碑

```
Week 11-12  ████ Phase 1 剩余: ERP完工
             ├── Week 11: 序列号管理+批次管理+Jasypt密文+价格表前端
             └── Week 12: 追溯服务+追溯前端+盘点审批流+单元测试+构建验证
             ★ 里程碑 M7: ERP全部交付

Week 13-20  ████ MES Phase 1: 基础骨架+主数据
             ├── Week 13: 模块创建+数据源+MQ消息定义
             ├── Week 14: 主数据CRUD+工序+路线
             ├── Week 15: MES工单+ERP→MES联动
             ├── Week 16: 排班日历复用
             ├── Week 17-18: 前端页面+路由注册
             └── Week 19-20: 联调测试+缓冲
             ★ 里程碑 M8: MES基础上线

Week 21-27  ████ MES Phase 2: 执行核心
             ├── Week 21-23: 流转卡引擎+工序执行+SN追溯
             ├── Week 24-25: 派工+工序转移+安灯
             └── Week 26-27: 前端+集成测试
             ★ 里程碑 M9: MES执行核心上线

Week 28-34  ████ MES Phase 3: 扩展模块
             ├── Week 28-30: 仓库扩展+质量(qc)+设备(eq)
             ├── Week 31-32: 数据采集+SPC+前端
             └── Week 33-34: 全模块集成测试+缓冲
             ★ 里程碑 M10: MES全部交付

Week 35-39  ████ IoT Phase 1: 基础设施+设备管理
             ├── Week 35-36: 模块创建+设备管理+MQTT
             └── Week 37-39: HTTP+分组/标签+物模型+前端
             ★ 里程碑: IoT基础设施就绪

Week 40-44  ████ IoT Phase 2: 协议+数据管道
             ├── Week 40-41: OPC UA+Modbus
             └── Week 42-44: 数据管道+时序数据库+实时数据+前端
             ★ 里程碑 M11: IoT多协议数据采集完成

Week 45-50  ████ IoT Phase 3: 智能+集成
             ├── Week 45-48: 规则引擎+告警+边缘+场景联动
             └── Week 49-50: 全链路集成+测试+大屏+文档
             ★ 里程碑 M12: ERP+MES+IoT一体化平台交付
```

---

## 六、总工作量评估

| 阶段 | 周次 | 人天 | 人员配置 |
|------|------|------|----------|
| **Phase 1 剩余: ERP完工** | Week 11-12 | ~12 | 2人(后端+前端) |
| **MES Phase 1: 基础骨架** | Week 13-20 | ~32 | 3人(2后端+1前端) |
| **MES Phase 2: 执行核心** | Week 21-27 | ~30 | 3人(2后端+1前端) |
| **MES Phase 3: 扩展模块** | Week 28-34 | ~30 | 3人(2后端+1前端) |
| **IoT Phase 1: 基础设施** | Week 35-39 | ~19.5 | 3人(2后端+1前端) |
| **IoT Phase 2: 协议+数据** | Week 40-44 | ~23 | 3人(2后端+1前端) |
| **IoT Phase 3: 智能+集成** | Week 45-50 | ~29 | 3人(2后端+1前端) |
| **合计** | **Week 11-50** | **~175.5** | **3-4人** |

---

## 七、技术风险与应对

| 风险项 | 影响 | 概率 | 应对策略 |
|--------|------|------|---------|
| **ERP→MES跨库事务一致性** | 高 | 中 | MQ最终一致性+补偿操作+对账机制(Week 7-8已建) |
| **流转卡引擎性能** | 中 | 中 | 大批量SN使用批量INSERT+异步生成 |
| **MES与ERP数据边界不清** | 高 | 中 | 严格遵循边界划分原则(§九)，库存统一wm_material_stock |
| **OPC UA连接稳定性** | 中 | 中 | 指数退避重连+断点数据缓存+心跳检测 |
| **时序数据库选型** | 中 | 低 | 开发期InfluxDB 2.x(Docker)，生产可切换TDengine |
| **MQTT Broker运维** | 中 | 中 | 开发期嵌入Moquette，生产独立部署EMQX |
| **前端开发量大(~75页面)** | 中 | 中 | 代码生成器+列表页模板加速 |
| **多租户数据隔离** | 高 | 低 | 所有DDL含tenant_id，MyBatis-Plus多租户插件自动注入 |

---

## 八、ERP与MES边界划分 (关键参考)

| 原则 | 说明 |
|------|------|
| **主数据统一** | 物料/供应商/客户/计量单位/仓库库位 → ERP独有，MES只引用 |
| **工位分工** | ERP的`bd_workstation`管基础信息，MES的`mes_md_workstation`管资源绑定 |
| **BOM分工** | ERP的`bd_bom`管设计BOM，MES的`mes_md_product_bom`管工序级投料BOM |
| **库存统一** | `wm_material_stock`是唯一库存台账，MES工序产出回写到ERP |
| **质量合并** | IQC/IPQC/OQC/缺陷只用MES版（mes_qc_*），ERP不建qc模块 |
| **设备合并** | 设备台账/点检/保养/维修只用MES版（mes_eq_*），ERP不建eq模块 |
| **条码统一** | 条码管理统一用`wm_barcode`，MES不重复 |
| **生产分工** | ERP建工单，MES工单引用ERP工单并增加工序管控 |
| **MES独有** | 流转卡/派工/工序转移/安灯/SPC/数据采集 → 仅MES |

---

## 九、新增数据库汇总

### 9.1 ERP Phase 1 剩余新增表 (Week 11-12)

| 表名 | 说明 | 任务编号 |
|------|------|---------|
| `wm_sn` | 序列号管理 | R-041 |
| `wm_batch` | 批次管理 | R-042 |

### 9.2 MES 新增表 (Week 13-34)

| 表名 | 说明 | 阶段 |
|------|------|------|
| `mes_md_workstation` | MES工位资源 | Phase 1 |
| `mes_md_product_bom` | 工序级投料BOM | Phase 1 |
| `mes_md_product_sop` | 标准作业指导 | Phase 1 |
| `mes_md_product_sip` | 标准检验指导 | Phase 1 |
| `mes_pro_process` | 工序定义 | Phase 1 |
| `mes_pro_route` | 工艺路线 | Phase 1 |
| `mes_pro_route_process` | 路线-工序关联 | Phase 1 |
| `mes_pro_workorder` | MES工单 | Phase 1 |
| `mes_pro_card` | 流转卡 | Phase 2 |
| `mes_pro_card_process` | 流转卡工序记录 | Phase 2 |
| `mes_pro_sn_process` | SN工序追溯 | Phase 2 |
| `mes_pro_task` | 派工任务 | Phase 2 |
| `mes_pro_trans_order` | 工序转移 | Phase 2 |
| `mes_pro_andon` | 安灯记录 | Phase 2 |
| `mes_qc_iqc` | IQC来料检验 | Phase 3 |
| `mes_qc_ipqc` | IPQC过程检验 | Phase 3 |
| `mes_qc_oqc` | OQC出厂检验 | Phase 3 |
| `mes_qc_defect` | 缺陷记录 | Phase 3 |
| `mes_equipment` | 设备台账 | Phase 3 |
| `mes_equipment_type` | 设备分类 | Phase 3 |
| `mes_check_plan` | 点检计划 | Phase 3 |
| `mes_check_record` | 点检记录 | Phase 3 |
| `mes_maintenance_plan` | 保养计划 | Phase 3 |
| `mes_maintenance_record` | 保养记录 | Phase 3 |
| `mes_repair_order` | 维修工单 | Phase 3 |
| `mes_dc_collect` | 数据采集 | Phase 3 |
| `mes_spc_chart` | SPC控制图 | Phase 3 |

### 9.3 IoT 新增表 (Week 35-50)

| 表名 | 说明 | 阶段 |
|------|------|------|
| `iot_product` | 产品/物模型 | Phase 1 |
| `iot_device` | 设备主表 | Phase 1 |
| `iot_thing_model` | 物模型属性 | Phase 1 |
| `iot_device_group` | 设备分组 | Phase 1 |
| `iot_device_log` | 设备日志 | Phase 1 |
| `iot_alert_rule` | 告警规则 | Phase 3 |
| `iot_alert_log` | 告警日志 | Phase 3 |
| `iot_scene` | 场景联动 | Phase 3 |
| `iot_gateway` | 边缘网关 | Phase 3 |

---

> **文档版本**: v1.2 (Week 11-12 完成版)
> **编制日期**: 2026-06-02
> **修订日期**: 2026-06-03
> **编制依据**: Week 1-10 执行记录 + 项目代码深度分析 + 架构完善执行计划
> **剩余总工期**: 40周 (Week 11-50)
> **剩余总工作量**: ~175.5人天
> **推荐团队**: 3-4人 (1架构+2后端+1前端)
>
> **v1.2 修订内容** (Week 11-12 完成后更新):
> - 里程碑 M7 (ERP全部交付) 已达成 ✅
> - R-041 序列号管理: 已完成 (wm_sn表 + Domain/Bo/Vo/Mapper/Service/Controller)
> - R-042 批次管理增强: 已完成 (wm_batch表 + Domain/Bo/Vo/Mapper/Service/Controller)
> - F-041 价格表前端: 已完成 (API/Types/列表页/表单页/路由注册)
> - R-043 追溯查询服务: 已完成 (正向+反向追溯 Service/Controller/DTO)
> - R-044 追溯前端: 已完成 (API/Types/查询页面)
> - R-055 盘点审批流: 已完成 (Flowable集成 + ServiceImpl改造 + Controller新增)
> - Q-007 Jasypt密文: 已完成 (3处密码已加密为ENC())
> - T-001 单元测试: 已完成 (26个测试全部通过)
> - V-001 构建验证: 已完成 (Maven编译通过, ESLint通过, 前端构建失败为已有问题)
> - so模块完成度: 85%→95% (后端), 60%→90% (前端)
> - wm模块完成度: 80%→95% (后端), 80%→90% (前端)
>
> **v1.1 修订内容**:
> - 修正所有DDL遵循ry-erp.sql规范 (tenant_id类型/审计字段/字符集/索引命名)
> - 补充Jasypt密文生成任务 (Q-007)
> - 补充Flowable BPMN流程定义创建任务 (R-055 Part A)
> - 补充ERP→MES MQ消息类型定义 (MES-005/MES-006)
> - 补充前端构建验证+性能基线任务 (V-001)
> - 调整序列号管理工时 1.5→2人天 (涉及6个出入库策略类改造)
> - 调整批次管理工时 1→1.5人天 (含FEFO策略改造+过期检查定时任务)
> - 调整盘点审批流工时 1.5→2人天 (含BPMN流程定义创建)
> - 优化MES任务编号连续性 (MES-001~MES-041)
> - 补充任务依赖关系
> - 补充字典数据INSERT语句
> - MES设备管理补充维修工单任务
