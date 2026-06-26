# MES Phase 1 Week 20 开发实施计划

> **实施日期**: 2026-06-19
> **阶段**: MES Phase 1 收尾 - Flowable 审批流配置 + 流转卡引擎 + Phase 1 端到端验证
> **状态**: 待审核

**目标：** 配置 MES 工单 Flowable 审批流（wf_custom_form 注册 + BPMN 部署），实现流转卡引擎（DDL + Entity/Mapper/Service/Controller + 前端骨架），打通工单完工聚合 → MQ 通知 ERP 链路，完成 Phase 1 端到端验证。

**前置条件：** Week 18-19 已完成 MES 工单 CRUD + Flowable 回调接口 + 审批前端组件 + 主数据只读页面。

---

## 一、任务清单

| # | 任务 | 优先级 | 交付物 | 验收标准 |
|---|------|--------|--------|----------|
| 1 | 流转卡 DDL（对齐 Week17 schema 约定） | 高 | `mes-week20-02-flowcard.sql` | 表结构与 pro_work_order 约定一致 |
| 2 | 流转卡 Entity/BO/VO | 高 | `ProFlowCard`、`ProFlowCardStep` + BO/VO | 字段与 DDL 完全对应 |
| 3 | 流转卡 Mapper | 高 | `ProFlowCardMapper`、`ProFlowCardStepMapper` | 继承 BaseMapperPlus |
| 4 | 流转卡 Service（状态机 + 工序自动生成 + 完工聚合） | 高 | `IProFlowCardService` + 实现 | 状态流转正确，完工聚合到工单 |
| 5 | 流转卡 Controller | 高 | `ProFlowCardController` | list/get/create/release/startStep/completeStep/completeCard |
| 6 | MES 工单完工 → MQ 通知 ERP | 高 | `ProWorkOrderServiceImpl.completeWo()` + `MesMqSendService` 调用 | 工单完工后发送 MQ 消息 |
| 7 | 流转卡前端骨架 | 中 | `flow-card/index.vue` + `flow-card/detail.vue` + API | 列表 + 详情 + 创建/下达/开工/完工按钮 |
| 8 | Flowable 审批流配置 SQL | 高 | `mes-week20-13-flow-deploy.sql` | wf_custom_form 插入 MES 工单审批流记录 |
| 9 | 菜单权限补充 SQL | 低 | `mes-week20-14-menu.sql` | 流转卡菜单 + 按钮权限 |
| 10 | 编译验证 | 高 | `mvn compile` 通过 | 无编译错误 |

---

## 二、详细任务分解

### 任务 1：流转卡 DDL（对齐 Week 17 schema 约定）

**Files:**
- Create: `doc/mes/sql/mes-week20-02-flowcard.sql`

**关键对齐项（文档 DDL → 实际约定）：**

| 文档设计 | Week 17 实际约定 | 对齐后 |
|----------|------------------|--------|
| `wo_id` BIGINT | `mes_wo_id` BIGINT | `mes_wo_id` |
| `tenant_id` BIGINT DEFAULT 0 | `varchar(20)` DEFAULT '000000' | `varchar(20)` |
| `status` VARCHAR(16) | `wo_status`/`step_status` | `card_status`/`step_status` |
| `quantity` DECIMAL(14,4) | `planned_qty` DECIMAL(18,4) | `planned_qty` |
| 无 del_flag | `del_flag` char(1) DEFAULT '0' | 增加 del_flag |
| `material_name` VARCHAR(128) | VARCHAR(200) | VARCHAR(200) |

**DDL 内容（2张表）：**

```sql
-- pro_flow_card: MES流转卡
-- 状态机: CREATED -> RELEASED -> IN_PROGRESS -> COMPLETED -> CLOSED
CREATE TABLE pro_flow_card (
    card_id          BIGINT NOT NULL COMMENT '流转卡ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    card_no          VARCHAR(64) NOT NULL COMMENT '流转卡编号',
    mes_wo_id        BIGINT NOT NULL COMMENT '关联MES工单ID',
    wo_no            VARCHAR(64) COMMENT '工单编号(冗余)',
    material_id      BIGINT COMMENT '物料ID',
    material_code    VARCHAR(64) COMMENT '物料编码(冗余)',
    material_name    VARCHAR(200) COMMENT '物料名称(冗余)',
    planned_qty      DECIMAL(18,4) DEFAULT 0 COMMENT '计划数量',
    completed_qty    DECIMAL(18,4) DEFAULT 0 COMMENT '已完成数量',
    scrap_qty        DECIMAL(18,4) DEFAULT 0 COMMENT '报废数量',
    current_step_id  BIGINT COMMENT '当前工序步骤ID',
    current_step_seq INT COMMENT '当前工序序号',
    card_status      VARCHAR(32) DEFAULT 'CREATED' COMMENT 'CREATED/RELEASED/IN_PROGRESS/COMPLETED/CLOSED',
    barcode          VARCHAR(128) COMMENT '流转卡条码',
    -- 审计字段 + del_flag (同 Week 17)
    ...
    PRIMARY KEY (card_id),
    UNIQUE INDEX uk_card_no (card_no),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_card_status (card_status)
);

-- pro_flow_card_step: MES流转卡工序记录
-- 状态: PENDING -> IN_PROGRESS -> COMPLETED/SKIPPED
CREATE TABLE pro_flow_card_step (
    step_record_id   BIGINT NOT NULL COMMENT '工序记录ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    card_id          BIGINT NOT NULL COMMENT '关联流转卡ID',
    card_no          VARCHAR(64) COMMENT '流转卡编号(冗余)',
    mes_wo_id        BIGINT NOT NULL COMMENT '关联MES工单ID(冗余)',
    wo_step_id       BIGINT COMMENT '关联工单工序步骤ID(pro_work_order_step.step_id)',
    sequence_num     INT NOT NULL COMMENT '工序序号',
    process_id       BIGINT COMMENT '工序ID(来自ERP)',
    process_code     VARCHAR(64),
    process_name     VARCHAR(200),
    workcenter_id    BIGINT COMMENT '工作中心ID',
    workcenter_code  VARCHAR(64),
    is_key_process   TINYINT(1) DEFAULT 0,
    step_status      VARCHAR(32) DEFAULT 'PENDING',
    operator_id      BIGINT COMMENT '操作员ID',
    operator_name    VARCHAR(64),
    planned_qty      DECIMAL(18,4) DEFAULT 0,
    good_qty         DECIMAL(18,4) DEFAULT 0 COMMENT '良品数量',
    defect_qty       DECIMAL(18,4) DEFAULT 0 COMMENT '不良品数量',
    scrap_qty        DECIMAL(18,4) DEFAULT 0 COMMENT '报废数量',
    actual_start_time DATETIME,
    actual_end_time  DATETIME,
    remark           VARCHAR(500),
    -- 审计字段 + del_flag
    ...
    PRIMARY KEY (step_record_id),
    INDEX idx_card_id (card_id),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_step_status (step_status)
);
```

---

### 任务 2-3：流转卡 Entity/BO/VO/Mapper

**Files:**
- `pro/domain/ProFlowCard.java` — extends TenantEntity, @TableName("pro_flow_card")
- `pro/domain/ProFlowCardStep.java` — extends TenantEntity, @TableName("pro_flow_card_step")
- `pro/domain/bo/ProFlowCardBo.java` — @AutoMapper(target = ProFlowCard.class)
- `pro/domain/vo/ProFlowCardVo.java` — @AutoMapper, 含 `List<ProFlowCardStepVo> steps`
- `pro/domain/vo/ProFlowCardStepVo.java`
- `pro/mapper/ProFlowCardMapper.java` — extends BaseMapperPlus
- `pro/mapper/ProFlowCardStepMapper.java`

**模式：** 与 ProWorkOrder/ProWorkOrderStep 完全一致。

---

### 任务 4：流转卡 Service（核心）

**Files:**
- `pro/service/IProFlowCardService.java`
- `pro/service/impl/ProFlowCardServiceImpl.java`

**接口方法（参照 MES 文档 5.2 节）：**

```java
public interface IProFlowCardService {
    // 状态常量
    String STATUS_CREATED = "CREATED";
    String STATUS_RELEASED = "RELEASED";
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    String STATUS_COMPLETED = "COMPLETED";
    String STATUS_CLOSED = "CLOSED";

    // CRUD
    TableDataInfo<ProFlowCardVo> selectPageList(ProFlowCardBo bo, PageQuery pageQuery);
    ProFlowCardVo selectById(Long cardId);
    List<ProFlowCardVo> selectByWoId(Long mesWoId);

    // 状态机操作
    ProFlowCardVo createFlowCard(Long mesWoId, BigDecimal quantity);
    void releaseCard(Long cardId);
    void startStep(Long cardId, Long stepRecordId, Long operatorId);
    void completeStep(Long cardId, Long stepRecordId,
                      BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty);
    void completeCard(Long cardId);
    void closeCard(Long cardId);
}
```

**核心逻辑：**

| 方法 | 逻辑 |
|------|------|
| `createFlowCard(mesWoId, qty)` | 1. 查工单(校验RELEASED状态) 2. 创建流转卡(CREATED) 3. 查工单工序步骤 4. 批量生成流转卡工序记录(PENDING) |
| `releaseCard(cardId)` | CAS更新 CREATED→RELEASED |
| `startStep(cardId, stepRecordId, operatorId)` | 1. CAS更新工序 PENDING→IN_PROGRESS 2. 若流转卡=RELEASED，CAS更新→IN_PROGRESS 3. 记录 actual_start_time + operator |
| `completeStep(cardId, stepRecordId, good/defect/scrap)` | 1. CAS更新工序 IN_PROGRESS→COMPLETED 2. 累加 good/defect/scrap 3. 更新流转卡 completed_qty 4. 检查是否所有工序完成→completeCard |
| `completeCard(cardId)` | 1. CAS更新流转卡→COMPLETED 2. 聚合到工单 completed_qty 3. 检查工单所有流转卡完成→触发工单完工 |
| `closeCard(cardId)` | CAS更新 COMPLETED→CLOSED |

**完工聚合 → 工单完工 → MQ：**
```
completeCard(cardId)
  → 更新 pro_work_order.completed_qty += card.completed_qty
  → 查工单下是否还有非COMPLETED流转卡
  → 若全部完成 → 调用 proWorkOrderService.completeWo(mesWoId)
```

**依赖注入：**
- `ProFlowCardMapper`、`ProFlowCardStepMapper`
- `ProWorkOrderMapper`（更新工单完工数量）
- `IProWorkOrderService`（触发工单完工）

---

### 任务 5：流转卡 Controller

**Files:**
- `pro/controller/ProFlowCardController.java`

**接口：**
```
GET  /mes/pro/flow-card/list              — 分页查询
GET  /mes/pro/flow-card/{cardId}          — 详情(含工序)
GET  /mes/pro/flow-card/by-wo/{mesWoId}   — 按工单查询流转卡列表
POST /mes/pro/flow-card                   — 创建流转卡(mesWoId + quantity)
PUT  /mes/pro/flow-card/release/{cardId}  — 下达
PUT  /mes/pro/flow-card/start-step        — 开工(body: cardId, stepRecordId, operatorId)
PUT  /mes/pro/flow-card/complete-step     — 完工(body: cardId, stepRecordId, goodQty, defectQty, scrapQty)
PUT  /mes/pro/flow-card/complete/{cardId} — 完成流转卡
PUT  /mes/pro/flow-card/close/{cardId}    — 关闭
```

---

### 任务 6：MES 工单完工 → MQ 通知 ERP

**Files:**
- Modify: `IProWorkOrderService.java` — 新增 `completeWo(Long mesWoId)`
- Modify: `ProWorkOrderServiceImpl.java` — 实现 `completeWo()`
- 调用 `MesMqSendService.sendWorkOrderCompleted()`

**逻辑：**
```java
void completeWo(Long mesWoId) {
    // CAS更新工单 IN_PROGRESS→COMPLETED
    int rows = baseMapper.update(null, Wrappers.<ProWorkOrder>lambdaUpdate()
        .set(ProWorkOrder::getWoStatus, STATUS_COMPLETED)
        .set(ProWorkOrder::getActualEndDate, new Date())
        .eq(ProWorkOrder::getMesWoId, mesWoId)
        .eq(ProWorkOrder::getWoStatus, STATUS_IN_PROGRESS));
    if (rows > 0) {
        ProWorkOrder wo = baseMapper.selectById(mesWoId);
        // 事务提交后发送MQ消息
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override public void afterCommit() {
                    String tenantId = TenantHelper.getTenantId();
                    mesMqSendService.sendWorkOrderCompleted(
                        mesWoId, wo.getErpWoId(), wo.getWoNo(),
                        wo.getCompletedQty(), wo.getScrapQty(), tenantId);
                }
            });
    }
}
```

**注意：** 需要注入 `MesMqSendService`，并恢复 `TransactionSynchronization` 的 import。

---

### 任务 7：流转卡前端骨架

**Files:**
- `api/mes/pro/flowcard.ts` + `types.ts` 扩展
- `views/mes/pro/flow-card/index.vue` — 列表页
- `views/mes/pro/flow-card/detail.vue` — 详情页(含工序进度)

**列表页功能：**
- 搜索栏（流转卡编号/工单编号/状态）
- 表格（编号/工单/产品/计划数量/完成数量/状态/操作）
- 操作按钮：详情/下达/开工/完工（按状态显示）

**详情页功能：**
- 流转卡基本信息
- 工序进度表格（序号/工序/状态/操作员/良品/不良/报废/操作）
- 开工/完工操作按钮

---

### 任务 8：Flowable 审批流配置 SQL

**Files:**
- Create: `doc/mes/sql/mes-week20-13-flow-deploy.sql`

**内容：**
```sql
-- 注册 MES 工单审批流到 wf_custom_form
-- 注意：deploy_id 和 flow_name 在 BPMN 部署后由系统自动填充
INSERT INTO wf_custom_form (id, business_name, business_service, flow_name, deploy_id,
    route_name, component, create_by, create_time, tenant_id)
VALUES (
    {自定义ID},
    'MES工单审批',
    'proWorkOrderService',
    '',
    '',
    'mes/pro/workorder/detail',
    '',
    1, NOW(), '000000'
) ON DUPLICATE KEY UPDATE business_name = VALUES(business_name);
```

**BPMN 配置说明（需在运行系统中通过 UI 操作）：**
1. 访问 Flowable 流程模型管理页面 `/workflow/model`
2. 新建流程模型，分类选择"自定义业务"（ZDYYW）
3. 设计审批节点（如：车间主任审批 → 生产主管审批）
4. StartEvent 的 formKey 设为 `key_{上述插入的id}`
5. 点击"部署"按钮，系统自动填充 deploy_id 和 flow_name
6. 验证：`GET /workflow/process/getProcesss?serviceName=proWorkOrderService` 返回新流程

---

### 任务 9：菜单权限补充 SQL

**Files:**
- Create: `doc/mes/sql/mes-week20-14-menu.sql`

**菜单 ID 段：4040~4050（对齐 Week 17 的 4000 段）**

```sql
-- 流转卡菜单 (parent=4001 生产执行)
INSERT INTO sys_menu VALUES (4040, '流转卡', 4001, 2, 'flow-card', 'mes/pro/flow-card/index', ...);
-- 流转卡详情 (hidden)
INSERT INTO sys_menu VALUES (4041, '流转卡详情', 4040, 1, 'flow-card/detail/:cardId', 'mes/pro/flow-card/detail', ...);
-- 按钮权限
INSERT INTO sys_menu VALUES (4042, '流转卡查询', 4040, 1, '', '', ..., 'mes:pro:flowcard:query');
INSERT INTO sys_menu VALUES (4043, '流转卡创建', 4040, 2, '', '', ..., 'mes:pro:flowcard:add');
INSERT INTO sys_menu VALUES (4044, '流转卡下达', 4040, 3, '', '', ..., 'mes:pro:flowcard:release');
INSERT INTO sys_menu VALUES (4045, '流转卡开工', 4040, 4, '', '', ..., 'mes:pro:flowcard:start');
INSERT INTO sys_menu VALUES (4046, '流转卡完工', 4040, 5, '', '', ..., 'mes:pro:flowcard:complete');
```

---

### 任务 10：编译验证

- [ ] `mvn compile -pl ruoyi-modules/ruoyi-mes -am` 通过
- [ ] 前端 `npm run dev` 无报错

---

## 三、Week 20 交付物汇总

| 交付物 | 类型 | 说明 |
|--------|------|------|
| `mes-week20-02-flowcard.sql` | SQL | 流转卡2张表DDL |
| `ProFlowCard.java` + `ProFlowCardStep.java` | Java | 实体类 |
| `ProFlowCardBo/Vo` + `ProFlowCardStepVo` | Java | BO/VO |
| `ProFlowCardMapper` + `ProFlowCardStepMapper` | Java | Mapper |
| `IProFlowCardService` + `ProFlowCardServiceImpl` | Java | 状态机+聚合 |
| `ProFlowCardController` | Java | REST接口 |
| `ProWorkOrderServiceImpl` 扩展 | Java | completeWo + MQ |
| `flowcard.ts` + `types.ts` | TS | 前端API |
| `flow-card/index.vue` + `detail.vue` | Vue | 前端页面 |
| `mes-week20-13-flow-deploy.sql` | SQL | Flowable配置 |
| `mes-week20-14-menu.sql` | SQL | 菜单权限 |

---

## 四、Phase 1 端到端验证场景

```
1. ERP创建工单 → ERP审批通过 → MQ消息
2. MES消费消息 → 创建MES工单(CREATED) + 工序步骤
3. MES工单提交审批 → Flowable审批 → 通过 → RELEASED
4. 创建流转卡 → 下达流转卡 → 开工 → 完工
5. 所有流转卡完成 → MES工单 COMPLETED
6. MES发送完工MQ消息 → ERP更新工单状态
```

---

## 五、风险与注意事项

| 风险 | 说明 | 应对 |
|------|------|------|
| 流转卡 DDL 字段对齐 | 文档设计与 Week17 实际不一致 | 已在任务1列出对齐表 |
| 完工聚合并发 | 多张流转卡同时完成时工单 completed_qty 竞争 | 使用 CAS UPDATE 或 Redisson 分布式锁 |
| MQ 消息时序 | completeWo 中 MQ 发送需在事务提交后 | 使用 TransactionSynchronizationManager.afterCommit |
| Flowable BPMN 需 UI 操作 | BPMN 流程设计需在运行系统中通过 modeler UI 完成 | SQL 仅注册 wf_custom_form，BPMN 部署在验证阶段手动操作 |
| 跨数据源事务 | completeStep 更新流转卡(mes) + 调用工单完工(mes) | 同库操作，无跨库问题 |

---

**文档生成时间**: 2026-06-18
**执行者**: AI Agent
**审核状态**: 待人工确认
