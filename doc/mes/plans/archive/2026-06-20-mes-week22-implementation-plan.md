# MES Phase 2 Week 22 开发实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **实施日期**: 2026-06-20
> **阶段**: MES Phase 2 启动 — 派工引擎 + 车间执行（扫码 + Lock4j 锁控制）
> **状态**: 已审批

**Goal:** 实现 MES 派工引擎（pro_dispatch DDL + Entity/Mapper/Service/Controller + 前端），打通"流转卡工序 → 派工 → 操作员执行 → 完工聚合回工序 → 自动完成工序"链路；叠加车间执行层（流转卡条码生成 + 扫码查找 + Lock4j 分布式锁防并发），使车间操作员可通过派工单或扫码两条路径完成工序执行。

**Architecture:** Week 20 已完成流转卡引擎。本周在其上叠加派工 layer：派工单从流转卡工序创建，分配操作员+数量；完工时增量累加数量到工序+流转卡，全部派工完成后自动完成工序（CAS），复用现有 completeCard→completeWo→MQ 链路。扫码路径复用现有 startStep/completeStep 直接执行。Lock4j `@Lock4j(keys={"#cardId"})` 防并发状态错乱。操作员数据来自 ERP SchWorker（前端选择后传 id+name，避免 MES 跨库）。

**Tech Stack:** Java 17 + Spring Boot + MyBatis-Plus + Lock4j (Redisson) + dynamic-datasource (`@DS("mes")`)；Vue 3.5 + Element Plus + TypeScript；MySQL (ry-mes 库)。

**前置条件：** Week 17-21 已完成 MES 模块骨架、工单（Flowable + MQ）、流转卡引擎（状态机 + 完工聚合）、Phase 1 前端收尾。

**架构决策（已确认）：**
- 派工完工后**自动完成工序**（当工序下所有派工均 COMPLETED 时，CAS 工序→COMPLETED，再触发流转卡完工检查）
- 派工与直接执行（现有 completeStep）为**互斥替代路径**：一个工序要么走派工路径（多操作员分派），要么走直接路径（单人一次完工）
- 操作员数据来自 ERP `SchWorker`（`/erp/sch/worker/list`），前端选择后传 `operatorId + operatorName` 到后端
- Lock4j 分布式锁加在状态转换方法上（`@Lock4j(keys={"#cardId"})` / `@Lock4j(keys={"#dispatchId"})`）

---

## 一、任务清单

| # | 任务 | 优先级 | 交付物 | 验收标准 |
|---|------|--------|--------|----------|
| 1 | pro_dispatch DDL + barcode 增强 | 高 | `mes-week22-03-dispatch.sql` | 表结构对齐 Week17/20 约定 + barcode 唯一索引 |
| 2 | ProDispatch Entity/BO/VO/Mapper | 高 | `ProDispatch` + BO/VO + Mapper | 字段与 DDL 完全对应 |
| 3 | IProFlowCardService 扩展（barcode + checkAndCompleteStep + Lock4j） | 高 | 接口 + 实现扩展 | barcode 生成/查询、工序自动完成、锁保护 |
| 4 | IProDispatchService + 实现（状态机 + 聚合） | 高 | 接口 + 实现类 | 创建/开工/完工/取消状态流转 + 完工聚合到工序 |
| 5 | ProDispatchController | 高 | REST 控制器 | list/get/create/start/complete/cancel/by-operator |
| 6 | ProFlowCardController 扫码接口 | 高 | 扩展控制器 | GET /by-barcode/{barcode} |
| 7 | 前端：派工 API + 类型 | 高 | `dispatch.ts` | 前端可调用派工 + 扫码接口 |
| 8 | 前端：派工列表 + 详情页 | 高 | `dispatch/index.vue` + `detail.vue` | 列表 + 搜索 + 开工/完工/取消操作 |
| 9 | 前端：扫码执行对话框 | 中 | `components/ScanExecute.vue` | 扫码 → 查找流转卡 → 显示当前工序 → 开工/完工 |
| 10 | 菜单权限 SQL | 中 | `mes-week22-16-menu.sql` | 派工菜单 + 扫码按钮权限 |
| 11 | 编译验证 | 高 | `mvn compile` + `npm run lint` 通过 | 无编译错误 |

---

## 二、详细任务分解

### 任务 1：pro_dispatch DDL + barcode 增强

**Files:**
- Create: `doc/mes/sql/mes-week22-03-dispatch.sql`

**DDL 对齐项（同 Week 20）：**

| 文档设计 | Week 17/20 实际约定 | 对齐后 |
|----------|---------------------|--------|
| `wo_id` BIGINT | `mes_wo_id` BIGINT | `mes_wo_id` |
| `tenant_id` BIGINT DEFAULT 0 | `varchar(20)` DEFAULT '000000' | `varchar(20)` |
| `quantity` DECIMAL(14,4) | DECIMAL(18,4) | `DECIMAL(18,4)` |
| `completed_qty` DECIMAL(14,4) | DECIMAL(18,4) | `DECIMAL(18,4)` |
| 无 del_flag | `del_flag char(1)` DEFAULT '0' | 增加 del_flag |
| `process_name` VARCHAR(128) | VARCHAR(200) | VARCHAR(200) |

**SQL 内容：**

```sql
USE `ry-mes`;

-- 1. pro_dispatch: MES派工单
-- 状态机: PENDING -> IN_PROGRESS -> COMPLETED / CANCELLED
CREATE TABLE pro_dispatch (
    dispatch_id      BIGINT NOT NULL COMMENT '派工单ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    dispatch_no      VARCHAR(64) NOT NULL COMMENT '派工单编号',
    mes_wo_id        BIGINT NOT NULL COMMENT '关联MES工单ID',
    wo_no            VARCHAR(64) COMMENT '工单编号(冗余)',
    card_id          BIGINT COMMENT '关联流转卡ID',
    card_no          VARCHAR(64) COMMENT '流转卡编号(冗余)',
    step_record_id   BIGINT COMMENT '关联流转卡工序记录ID',
    sequence_num     INT COMMENT '工序序号(冗余)',
    process_id       BIGINT NOT NULL COMMENT '工序ID',
    process_code     VARCHAR(64),
    process_name     VARCHAR(200),
    workcenter_id    BIGINT,
    workcenter_code  VARCHAR(64),
    operator_id      BIGINT COMMENT '操作员ID(SchWorker.workerId)',
    operator_name    VARCHAR(64) COMMENT '操作员姓名',
    planned_qty      DECIMAL(18,4) DEFAULT 0 COMMENT '派工数量',
    completed_qty    DECIMAL(18,4) DEFAULT 0 COMMENT '已完成数量',
    good_qty         DECIMAL(18,4) DEFAULT 0 COMMENT '良品数量',
    defect_qty       DECIMAL(18,4) DEFAULT 0 COMMENT '不良品数量',
    scrap_qty        DECIMAL(18,4) DEFAULT 0 COMMENT '报废数量',
    dispatch_status  VARCHAR(32) DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED/CANCELLED',
    plan_start_time  DATETIME,
    plan_end_time    DATETIME,
    actual_start_time DATETIME,
    actual_end_time  DATETIME,
    remark           VARCHAR(500),
    create_dept      BIGINT,
    create_by        BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    del_flag         CHAR(1) DEFAULT '0',
    PRIMARY KEY (dispatch_id),
    UNIQUE INDEX uk_dispatch_no (dispatch_no),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_card_id (card_id),
    INDEX idx_step_record_id (step_record_id),
    INDEX idx_operator_id (operator_id),
    INDEX idx_dispatch_status (dispatch_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES派工单';

-- 2. pro_flow_card barcode 唯一索引增强
-- Week 20 DDL 已有 barcode 列但无唯一约束，本周补充
-- 注意: 现有流转卡 barcode 为 NULL, MySQL NULL 不参与唯一性检查, 旧数据不受影响
ALTER TABLE pro_flow_card ADD UNIQUE INDEX uk_barcode (barcode);
```

---

### 任务 2：ProDispatch Entity/BO/VO/Mapper

**Files:**
- `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/ProDispatch.java`
- `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/bo/ProDispatchBo.java`
- `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/vo/ProDispatchVo.java`
- `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/mapper/ProDispatchMapper.java`

**模式：** 与 `ProFlowCard` / `ProWorkOrder` 完全一致（参考现有文件）。

**Entity 关键内容：**
```java
@Data
@Accessors(chain = true)
@TableName("pro_dispatch")
public class ProDispatch extends TenantEntity {
    @TableId(value = "dispatch_id")
    private Long dispatchId;
    private String dispatchNo;
    private Long mesWoId;
    private String woNo;
    private Long cardId;
    private String cardNo;
    private Long stepRecordId;
    private Integer sequenceNum;
    private Long processId;
    private String processCode;
    private String processName;
    private Long workcenterId;
    private String workcenterCode;
    private Long operatorId;
    private String operatorName;
    private BigDecimal plannedQty;
    private BigDecimal completedQty;
    private BigDecimal goodQty;
    private BigDecimal defectQty;
    private BigDecimal scrapQty;
    private String dispatchStatus;
    private Date planStartTime;
    private Date planEndTime;
    private Date actualStartTime;
    private Date actualEndTime;
    private String remark;
    @TableLogic
    private String delFlag;
}
```

**BO 校验：** `@NotNull mesWoId`, `@NotNull stepRecordId`, `@NotNull @DecimalMin("0.0001") plannedQty`，`operatorId` 可空（创建时可未指派，开工时确定）。

**VO：** 镜像 Entity + `@ExcelIgnoreUnannotated` + `@AutoMapper(target = ProDispatch.class)`。

**Mapper：** `extends BaseMapperPlus<ProDispatch, ProDispatchVo>`。

---

### 任务 3：IProFlowCardService 扩展（barcode + checkAndCompleteStep + Lock4j）

**Files:**
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/IProFlowCardService.java`
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProFlowCardServiceImpl.java`
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/controller/ProFlowCardController.java`

**3a. 接口新增方法：**

```java
/** 按条码查询流转卡(含工序) */
ProFlowCardVo selectByBarcode(String barcode);

/** 检查并完成工序(派工完工聚合后调用) — CAS工序→COMPLETED + 检查所有工序完成→completeCard */
void checkAndCompleteStep(Long cardId, Long stepRecordId);
```

**3b. createFlowCard 增强 — 生成 barcode：**

在现有 `createFlowCard` 方法中，创建流转卡时设置 barcode（仅加一行，不改其它逻辑）：
```java
flowCard.setBarcode("FC" + String.format("%016d", flowCard.getCardId()));
```
（cardId 为雪花算法生成的大数，格式化为 16 位数字前缀加 FC，可被条码扫描枪识别为 Code128 或 QR 码）

**3c. selectByBarcode 实现：**

```java
@Override
public ProFlowCardVo selectByBarcode(String barcode) {
    ProFlowCard card = baseMapper.selectOne(Wrappers.<ProFlowCard>lambdaQuery()
        .eq(ProFlowCard::getBarcode, barcode));
    if (card == null) {
        throw new RuntimeException("条码对应的流转卡不存在: " + barcode);
    }
    ProFlowCardVo vo = BeanUtil.copyProperties(card, ProFlowCardVo.class);
    vo.setSteps(stepMapper.selectVoList(Wrappers.<ProFlowCardStep>lambdaQuery()
        .eq(ProFlowCardStep::getCardId, card.getCardId())
        .orderByAsc(ProFlowCardStep::getSequenceNum)));
    return vo;
}
```
（`BeanUtil` 用 `cn.hutool.core.bean.BeanUtil`，与项目一致；若 ProFlowCardVo 用了 `@AutoMapper`，可用 `MapstructUtils.convert`，参照现有 `selectById` 方法用哪种）

**3d. checkAndCompleteStep 实现（派工完工聚合后自动完成工序）：**

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void checkAndCompleteStep(Long cardId, Long stepRecordId) {
    // CAS 工序 IN_PROGRESS -> COMPLETED
    int rows = stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
        .set(ProFlowCardStep::getStepStatus, STEP_COMPLETED)
        .set(ProFlowCardStep::getActualEndTime, new Date())
        .eq(ProFlowCardStep::getStepRecordId, stepRecordId)
        .eq(ProFlowCardStep::getCardId, cardId)
        .eq(ProFlowCardStep::getStepStatus, STEP_IN_PROGRESS));
    if (rows == 0) {
        return; // 工序已被其他路径完成，幂等跳过
    }
    // 检查所有工序完成 -> completeCard
    Long pendingCount = stepMapper.selectCount(Wrappers.<ProFlowCardStep>lambdaQuery()
        .eq(ProFlowCardStep::getCardId, cardId)
        .ne(ProFlowCardStep::getStepStatus, STEP_COMPLETED)
        .ne(ProFlowCardStep::getStepStatus, STEP_SKIPPED));
    if (pendingCount == 0) {
        completeCard(cardId);
    }
}
```

**3e. Lock4j 锁保护：**

在 `startStep` 和 `completeStep` 方法上添加 `@Lock4j(keys = {"#cardId"})`：
```java
@Lock4j(keys = {"#cardId"})
@Override
@Transactional(rollbackFor = Exception.class)
public void startStep(Long cardId, Long stepRecordId, Long operatorId) { ... }

@Lock4j(keys = {"#cardId"})
@Override
@Transactional(rollbackFor = Exception.class)
public void completeStep(Long cardId, Long stepRecordId, BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty) { ... }
```

Import: `com.baomidou.lock.annotation.Lock4j`

**3f. Controller 新增扫码接口：**

```java
@GetMapping("/by-barcode/{barcode}")
public R<ProFlowCardVo> getByBarcode(@PathVariable String barcode) {
    return R.ok(proFlowCardService.selectByBarcode(barcode));
}
```

---

### 任务 4：IProDispatchService + 实现（核心）

**Files:**
- `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/IProDispatchService.java`
- `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProDispatchServiceImpl.java`

**接口方法：**

```java
public interface IProDispatchService {
    String STATUS_PENDING = "PENDING";
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    String STATUS_COMPLETED = "COMPLETED";
    String STATUS_CANCELLED = "CANCELLED";

    TableDataInfo<ProDispatchVo> selectPageList(ProDispatchBo bo, PageQuery pageQuery);
    ProDispatchVo selectById(Long dispatchId);
    List<ProDispatchVo> selectByStepRecordId(Long stepRecordId);
    List<ProDispatchVo> selectByOperatorId(Long operatorId);

    ProDispatchVo createDispatch(Long cardId, Long stepRecordId, Long operatorId,
                                  String operatorName, BigDecimal quantity);
    void startDispatch(Long dispatchId);
    void completeDispatch(Long dispatchId, BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty);
    void cancelDispatch(Long dispatchId);
}
```

**核心逻辑：**

| 方法 | 逻辑 |
|------|------|
| `createDispatch(cardId, stepRecordId, operatorId, operatorName, qty)` | 1. 查流转卡工序记录(校验存在) 2. 校验工序状态为 PENDING 或 IN_PROGRESS 3. 校验工序下已有派工的 planned_qty 之和 + 本次 qty ≤ 工序 planned_qty(防超派) 4. 创建派工单(PENDING)，冗余 wo_no/card_no/process/workcenter/sequence_num，生成 dispatch_no(`DIS-` + cardNo + `-` + 时间戳后4位) |
| `startDispatch(dispatchId)` | 1. CAS PENDING→IN_PROGRESS，设 actualStartTime + operator(若 BO 中有) 2. 若关联工序为 PENDING，调用 `proFlowCardService.startStep(cardId, stepRecordId, operatorId)` 同步开工工序 3. 若工序已 IN_PROGRESS(多派工场景)，仅更新派工状态 + 补充设置工序 operatorName |
| `completeDispatch(dispatchId, good/defect/scrap)` | 1. CAS IN_PROGRESS→COMPLETED，设 actualEndTime + good/defect/scrap/completed_qty 2. setSql 增量累加到工序: `good_qty += goodQty, defect_qty += defectQty, scrap_qty += scrapQty` 3. setSql 增量累加到流转卡: `completed_qty += goodQty, scrap_qty += scrapQty` 4. 查工序下所有派工是否均 COMPLETED → 若是，调用 `proFlowCardService.checkAndCompleteStep(cardId, stepRecordId)` 自动完成工序 |
| `cancelDispatch(dispatchId)` | CAS PENDING→CANCELLED（仅待开始可取消） |

**completeDispatch 聚合核心代码：**

```java
@Lock4j(keys = {"#dispatchId"})
@Override
@Transactional(rollbackFor = Exception.class)
public void completeDispatch(Long dispatchId, BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty) {
    ProDispatch dispatch = baseMapper.selectById(dispatchId);
    if (dispatch == null) throw new RuntimeException("派工单不存在");
    
    // 1. CAS 派工 IN_PROGRESS -> COMPLETED
    int rows = baseMapper.update(null, Wrappers.<ProDispatch>lambdaUpdate()
        .set(ProDispatch::getDispatchStatus, STATUS_COMPLETED)
        .set(ProDispatch::getGoodQty, goodQty)
        .set(ProDispatch::getDefectQty, defectQty)
        .set(ProDispatch::getScrapQty, scrapQty)
        .set(ProDispatch::getCompletedQty, goodQty)
        .set(ProDispatch::getActualEndTime, new Date())
        .eq(ProDispatch::getDispatchId, dispatchId)
        .eq(ProDispatch::getDispatchStatus, STATUS_IN_PROGRESS));
    if (rows == 0) throw new RuntimeException("派工状态已变更，仅进行中可完工");

    Long cardId = dispatch.getCardId();
    Long stepRecordId = dispatch.getStepRecordId();

    // 2. 累加到工序(增量)
    stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
        .setSql("good_qty = good_qty + " + goodQty.toPlainString())
        .setSql("defect_qty = defect_qty + " + defectQty.toPlainString())
        .setSql("scrap_qty = scrap_qty + " + scrapQty.toPlainString())
        .eq(ProFlowCardStep::getStepRecordId, stepRecordId));

    // 3. 累加到流转卡(增量)
    flowCardMapper.update(null, Wrappers.<ProFlowCard>lambdaUpdate()
        .setSql("completed_qty = completed_qty + " + goodQty.toPlainString())
        .setSql("scrap_qty = scrap_qty + " + scrapQty.toPlainString())
        .eq(ProFlowCard::getCardId, cardId));

    // 4. 检查工序下所有派工是否均完成 -> 自动完成工序
    Long activeCount = baseMapper.selectCount(Wrappers.<ProDispatch>lambdaQuery()
        .eq(ProDispatch::getStepRecordId, stepRecordId)
        .ne(ProDispatch::getDispatchStatus, STATUS_COMPLETED)
        .ne(ProDispatch::getDispatchStatus, STATUS_CANCELLED));
    if (activeCount == 0) {
        proFlowCardService.checkAndCompleteStep(cardId, stepRecordId);
    }
}
```

**依赖注入：**
- `ProDispatchMapper baseMapper`
- `ProFlowCardStepMapper stepMapper`（累加到工序）
- `ProFlowCardMapper flowCardMapper`（累加到流转卡）
- `IProFlowCardService proFlowCardService`（开工工序 + 自动完成工序）

**类注解：** `@DS("mes")` `@Service("proDispatchService")` `@RequiredArgsConstructor` `@Slf4j`（与 ProFlowCardServiceImpl 一致）。

---

### 任务 5：ProDispatchController

**Files:**
- `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/controller/ProDispatchController.java`

**接口：**
```
GET  /mes/pro/dispatch/list              — 分页查询(@SaCheckPermission mes:pro:dispatch:list)
GET  /mes/pro/dispatch/{dispatchId}      — 详情(@SaCheckPermission mes:pro:dispatch:query)
GET  /mes/pro/dispatch/by-step/{stepRecordId} — 按工序查询派工列表
GET  /mes/pro/dispatch/by-operator/{operatorId} — 按操作员查询派工列表
POST /mes/pro/dispatch                   — 创建派工(params: cardId, stepRecordId, operatorId, operatorName, quantity) @SaCheckPermission mes:pro:dispatch:add
PUT  /mes/pro/dispatch/start/{dispatchId} — 开工 @SaCheckPermission mes:pro:dispatch:start
PUT  /mes/pro/dispatch/complete/{dispatchId} — 完工(params: goodQty, defectQty, scrapQty) @SaCheckPermission mes:pro:dispatch:complete
PUT  /mes/pro/dispatch/cancel/{dispatchId} — 取消 @SaCheckPermission mes:pro:dispatch:cancel
```

**模式：** 参考 `ProFlowCardController`（@RestController @RequestMapping("/mes/pro/dispatch") @RequiredArgsConstructor，注入 `IProDispatchService`）。

---

### 任务 6：ProFlowCardController 扫码接口

已在任务 3f 描述。仅新增 `GET /mes/pro/flow-card/by-barcode/{barcode}`，权限 `mes:pro:flowcard:query`（复用现有）。

---

### 任务 7：前端派工 API + 类型

**Files:**
- Create: `plus-ui/src/api/mes/pro/dispatch.ts`（类型 + API 合并文件，对齐 `flowcard.ts` 模式）

```typescript
import request from '@/utils/request';
import { AxiosPromise } from 'axios';

export interface ProDispatchVO {
  dispatchId: number;
  dispatchNo: string;
  mesWoId: number;
  woNo: string;
  cardId: number;
  cardNo: string;
  stepRecordId: number;
  sequenceNum: number;
  processId: number;
  processCode: string;
  processName: string;
  workcenterId: number;
  workcenterCode: string;
  operatorId: number;
  operatorName: string;
  plannedQty: number;
  completedQty: number;
  goodQty: number;
  defectQty: number;
  scrapQty: number;
  dispatchStatus: string;
  planStartTime: string;
  planEndTime: string;
  actualStartTime: string;
  actualEndTime: string;
  remark: string;
}

export interface ProDispatchQuery extends PageQuery {
  dispatchNo?: string;
  operatorId?: number;
  workcenterId?: number;
  dispatchStatus?: string;
  mesWoId?: number;
  cardId?: number;
}

export const listDispatch = (query?: ProDispatchQuery): AxiosPromise<ProDispatchVO[]> =>
  request({ url: '/mes/pro/dispatch/list', method: 'get', params: query });

export const getDispatch = (dispatchId: string | number): AxiosPromise<ProDispatchVO> =>
  request({ url: `/mes/pro/dispatch/${dispatchId}`, method: 'get' });

export const listDispatchByStep = (stepRecordId: string | number): AxiosPromise<ProDispatchVO[]> =>
  request({ url: `/mes/pro/dispatch/by-step/${stepRecordId}`, method: 'get' });

export const listDispatchByOperator = (operatorId: string | number): AxiosPromise<ProDispatchVO[]> =>
  request({ url: `/mes/pro/dispatch/by-operator/${operatorId}`, method: 'get' });

export const addDispatch = (cardId: number, stepRecordId: number, operatorId: number, operatorName: string, quantity: number) =>
  request({ url: '/mes/pro/dispatch', method: 'post', params: { cardId, stepRecordId, operatorId, operatorName, quantity } });

export const startDispatch = (dispatchId: string | number) =>
  request({ url: `/mes/pro/dispatch/start/${dispatchId}`, method: 'put' });

export const completeDispatch = (dispatchId: string | number, goodQty: number, defectQty?: number, scrapQty?: number) =>
  request({ url: `/mes/pro/dispatch/complete/${dispatchId}`, method: 'put', params: { goodQty, defectQty, scrapQty } });

export const cancelDispatch = (dispatchId: string | number) =>
  request({ url: `/mes/pro/dispatch/cancel/${dispatchId}`, method: 'put' });

export const getFlowCardByBarcode = (barcode: string): AxiosPromise<any> =>
  request({ url: `/mes/pro/flow-card/by-barcode/${barcode}`, method: 'get' });
```

**操作员选择器 API（复用 ERP）：** 前端 import `listWorker` from `@/api/erp/sch/worker/index` 获取操作员列表。

---

### 任务 8：前端派工列表 + 详情页

**Files:**
- Create: `plus-ui/src/views/mes/pro/dispatch/index.vue`
- Create: `plus-ui/src/views/mes/pro/dispatch/detail.vue`

**列表页功能（dispatch/index.vue）：**
- 搜索栏：派工单号 / 操作员（el-select 调 `listWorker`）/ 工作中心 / 状态（PENDING/IN_PROGRESS/COMPLETED/CANCELLED）
- 表格：派工单号 / 工单编号 / 流转卡编号 / 工序 / 操作员 / 工作中心 / 派工数量 / 已完成 / 状态 / 操作
- 操作按钮：详情 / 开工（PENDING 时，`mes:pro:dispatch:start`）/ 完工（IN_PROGRESS 时，`mes:pro:dispatch:complete`，prompt 输入良品/不良/报废）/ 取消（PENDING 时，`mes:pro:dispatch:cancel`）
- 顶部"扫码执行"按钮（打开扫码对话框，见任务 9）
- 创建派工入口：从流转卡详情页跳转时带 `stepRecordId` 参数，列表页检测到后弹出创建对话框（用 prompt 简化：输入操作员ID+数量）

**详情页功能（dispatch/detail.vue）：**
- el-descriptions 展示派工单基本信息
- 状态操作按钮（开工/完工/取消，按状态显示）
- 关联流转卡/工序信息展示

---

### 任务 9：前端扫码执行对话框

**Files:**
- Create: `plus-ui/src/views/mes/pro/dispatch/components/ScanExecute.vue`

**功能：**
```vue
<el-dialog v-model="visible" title="扫码执行" width="640px" append-to-body>
  <!-- 1. 扫码输入 -->
  <el-input v-model="barcode" placeholder="请扫描或输入流转卡条码" @keyup.enter="handleScan">
    <template #append><el-button icon="Search" @click="handleScan">查找</el-button></template>
  </el-input>
  
  <!-- 2. 扫描结果: 流转卡信息 + 当前工序 -->
  <el-descriptions v-if="flowCard.cardId" :column="2" border class="mt-2">
    <el-descriptions-item label="流转卡编号">{{ flowCard.cardNo }}</el-descriptions-item>
    <el-descriptions-item label="产品">{{ flowCard.materialName }}</el-descriptions-item>
    <el-descriptions-item label="状态">{{ statusLabel(flowCard.cardStatus) }}</el-descriptions-item>
    <el-descriptions-item label="当前工序">{{ currentStep?.processName }}</el-descriptions-item>
  </el-descriptions>
  
  <!-- 3. 当前工序操作按钮 -->
  <div v-if="currentStep" class="mt-2">
    <el-button type="primary" icon="VideoPlay" @click="handleStartStep"
      v-if="currentStep.stepStatus === 'PENDING' && flowCard.cardStatus === 'RELEASED'"
      v-hasPermi="['mes:pro:flowcard:start']">开工</el-button>
    <el-button type="success" icon="CircleCheck" @click="handleCompleteStep"
      v-if="currentStep.stepStatus === 'IN_PROGRESS'"
      v-hasPermi="['mes:pro:flowcard:complete']">完工</el-button>
  </div>
</el-dialog>
```

**逻辑：**
- `handleScan`: 调 `getFlowCardByBarcode(barcode)` → 设置 flowCard → 找到当前工序（第一个 PENDING 或 IN_PROGRESS 的 step）
- `handleStartStep`: prompt 操作员ID → 调 `startStep(cardId, stepRecordId, operatorId)` → 刷新（注意用 `{ value }` 解构，对齐 Week 21 修复的 prompt 模式）
- `handleCompleteStep`: prompt 良品/不良/报废 → 调 `completeStep(cardId, stepRecordId, goodQty, defectQty, scrapQty)` → 刷新

**暴露 `defineExpose({ open })`**，在 dispatch/index.vue 中通过 ref 调用。

---

### 任务 10：菜单权限 SQL

**Files:**
- Create: `doc/mes/sql/mes-week22-16-menu.sql`

**菜单 ID 段：4060~4070（对齐 Week 20 的 4040 段）**

```sql
USE `ry-vue`;

-- 派工管理菜单 (parent=4001 生产执行, Week17 已建)
INSERT INTO sys_menu VALUES 
(4060, '派工管理', 4001, 3, 'dispatch', 'mes/pro/dispatch/index', '', 1, 0, 'C', '0', '0', 'mes:pro:dispatch:list', 'people', 103, 1, NOW(), NULL, NULL, 'MES派工管理');
-- 派工详情 (hidden)
INSERT INTO sys_menu VALUES 
(4061, '派工详情', 4060, 1, 'dispatch/detail/:dispatchId', 'mes/pro/dispatch/detail', '', 1, 0, 'C', '1', '0', 'mes:pro:dispatch:query', '#', 103, 1, NOW(), NULL, NULL, '');
-- 按钮权限
INSERT INTO sys_menu VALUES 
(4062, '派工查询', 4060, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:dispatch:query', '#', 103, 1, NOW(), NULL, NULL, ''),
(4063, '派工创建', 4060, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:dispatch:add', '#', 103, 1, NOW(), NULL, NULL, ''),
(4064, '派工开工', 4060, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:dispatch:start', '#', 103, 1, NOW(), NULL, NULL, ''),
(4065, '派工完工', 4060, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:dispatch:complete', '#', 103, 1, NOW(), NULL, NULL, ''),
(4066, '派工取消', 4060, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:dispatch:cancel', '#', 103, 1, NOW(), NULL, NULL, ''),
(4067, '扫码执行', 4060, 6, '', '', '', 1, 0, 'F', '0', '0', 'mes:pro:dispatch:scan', '#', 103, 1, NOW(), NULL, NULL, '');
```

> **注意：** 执行前确认 4001（生产执行父菜单）存在（Week 17 创建）。执行后为相关角色勾选 4060-4067 权限。

---

### 任务 11：编译验证

- [ ] `mvn compile -pl ruoyi-modules/ruoyi-mes -am` 通过
- [ ] 前端 `eslint src/views/mes src/api/mes --ext .vue,.ts` 无 error
- [ ] 前端 `npm run dev` 启动无报错（可选，依赖完整运行栈）

---

## 三、Week 22 交付物汇总

| 交付物 | 类型 | 说明 |
|--------|------|------|
| `mes-week22-03-dispatch.sql` | SQL | pro_dispatch DDL + barcode 唯一索引 |
| `ProDispatch.java` + BO/VO | Java | 实体类 |
| `ProDispatchMapper.java` | Java | Mapper |
| `IProDispatchService` + `ProDispatchServiceImpl` | Java | 状态机 + 聚合 |
| `ProDispatchController.java` | Java | REST 接口 |
| `IProFlowCardService` 扩展 | Java | barcode + checkAndCompleteStep + Lock4j |
| `ProFlowCardController` 扩展 | Java | 扫码接口 |
| `dispatch.ts` | TS | 前端 API + 类型 |
| `dispatch/index.vue` + `detail.vue` | Vue | 前端页面 |
| `ScanExecute.vue` | Vue | 扫码执行对话框 |
| `mes-week22-16-menu.sql` | SQL | 菜单权限 |

---

## 四、派工执行链路（端到端）

```
1. 流转卡下达 (RELEASED) → 流转卡工序均为 PENDING
2. 创建派工: 从流转卡工序创建派工单 (PENDING, 指定操作员 + 数量)
3. 开工派工: PENDING → IN_PROGRESS, 同步开工流转卡工序 (PENDING → IN_PROGRESS), 流转卡/工单转 IN_PROGRESS
4. 完工派工: IN_PROGRESS → COMPLETED
   → 累加 good/defect/scrap 到工序 (增量)
   → 累加 completed_qty/scrap_qty 到流转卡 (增量)
   → 检查工序下所有派工均 COMPLETED → 自动完成工序 (checkAndCompleteStep)
     → 检查流转卡所有工序完成 → 自动完成流转卡 (completeCard)
       → 检查工单所有流转卡完成 → 自动完成工单 (completeWo) → MQ 通知 ERP
5. 或扫码路径: 扫码 → 查找流转卡 → 显示当前工序 → 开工/完工 (走现有 completeStep 直接路径)
```

---

## 五、风险与注意事项

| 风险 | 说明 | 应对 |
|------|------|------|
| 派工与直接执行互斥 | 一个工序不能同时走派工和直接 completeStep | 文档约束 + 前端 UI 控制（有派工的工序隐藏直接完工按钮，Phase 2 后续完善） |
| 完工聚合并发 | 多操作员同时完工不同派工时工序 good_qty 竞争 | setSql 增量累加（原子操作）+ Lock4j 锁 dispatchId + checkAndCompleteStep CAS |
| 超派校验 | 创建派工时 planned_qty 之和可能超过工序 planned_qty | createDispatch 中校验已有派工数量之和 + 本次 ≤ 工序 planned_qty |
| Lock4j 与 @Transactional 顺序 | Lock4j 应在事务外层加锁 | @Lock4j 注解在方法上，Spring AOP 代理确保锁在事务开启前获取 |
| barcode 唯一性 | 现有流转卡 barcode 为空，加唯一索引后旧数据报错 | DDL 中 ALTER ADD UNIQUE INDEX 仅对新数据生效；旧数据 barcode 为 NULL 不受唯一约束（MySQL NULL 不参与唯一性检查） |
| 操作员跨库 | SchWorker 在 ERP 库，MES 不直接查 | 前端调 /erp/sch/worker/list 选择操作员，传 id+name 到 MES 后端，避免跨库 |
| startStep 不设 operatorName | 现有 startStep 只设 operatorId | 派工路径在 startDispatch 中额外更新工序的 operatorName（补充设置，不改 startStep 签名） |

---

## 六、明确排除项（本周不做）

- 安灯系统（ProAndon + SSE）— Week 23
- SN 追溯（ProSn）— Week 24
- ProDispatch 的"创建派工"完整前端对话框（从流转卡详情页创建派工的 UI）— 列表页提供入口，创建对话框用 prompt 简化，完整创建表单后置
- 派工排程优化（自动派工算法、负荷均衡）— 非 Phase 2 范围

---

**文档生成时间**: 2026-06-18
**执行者**: AI Agent (Subagent-Driven Development)
**审核状态**: 已审批
