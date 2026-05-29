# Week 5 开发计划：生产深度功能

> **计划日期**: 2026-05-30 ~ 2026-06-05
> **计划人天**: 4人天 (R-021: 1.5d + R-022: 1.5d + R-023: 1d)
> **前置依赖**: Week 1-4 已完成，WmStockOperationService/StrategyFactory已就绪

---

## 一、现有代码基线分析

### 1.1 生产工单状态机（当前已实现）

```
CREATED(新建) --提交审批--> APPROVING(审批中) --审批通过--> APPROVED(已批准)
                                                        |
                                                        v
                                            RELEASED(已下达) --startProduction()--> IN_PROGRESS(生产中)
                                                                                        |
                                                                                        v
                                                                            COMPLETED(已完工) --close()--> CLOSED(已关闭)
```

### 1.2 库存操作基础设施（当前已实现）

| 组件 | 状态 | 说明 |
|------|------|------|
| `WmStockOperationServiceImpl` | 已就绪 | 策略模式委派，事务边界 |
| `StockOperationStrategyFactory` | 已就绪 | 策略工厂，按枚举分发 |
| `BaseStockOperationStrategy` | 已就绪 | 抽象基类，含buildBaseTxFromStock/hasPosted |
| `ProductionIssueStrategy` | 已就绪 | 生产领料库存扣减+流水，FIFO扣减 |
| `WmProductionIssue/WmProductionIssueLine` | 已就绪 | 生产领料单领域模型 |
| `PrdMaterialRequirement/Detail` | 已就绪 | 物料需求单，支持BOM展开+净需求计算 |

### 1.3 报工模块（当前状态）

- `PrdWorkReportServiceImpl` 为纯CRUD，**未与库存联动**
- 报工状态：DRAFT → SUBMITTED → APPROVED → REJECTED
- 报工含字段：reportedQty/goodQty/scrapQty/cumulativeQty/remainingQty
- **缺失**：报工审核通过后自动触发倒冲/入库

### 1.4 排班模块（当前状态）

- `SchPlan`: 排班计划（planCode/planName/startDate/endDate/periodType/rotationType）
- `SchShift`: 班次定义（shiftCode/shiftName/startTime/endTime/workHours/isCrossDay/restTime）
- `SchTeamSchedule`: 班组排班
- **缺失**：产能日历（按日期+工作中心记录可用产能）

---

## 二、任务分解

### 任务 R-021: 生产消耗自动过账（完工倒冲领料）

#### 2.1.1 业务背景

**倒冲(Backflush)**：适用于低值易耗品、紧固件等难以按单精确计量的物料。在工单/报工完工时，系统根据BOM定额自动计算物料消耗并扣减库存，无需人工单独开具领料单。

**触发时机**：
- 方案A（工单级倒冲）：工单完工(complete)时一次性倒冲全部物料
- 方案B（报工级倒冲）：每次报工审核通过后，按本次报工数量比例倒冲

**建议采用方案B**（更精细，与MES报工模式一致），但Week 5先实现方案A作为基础，预留方案B扩展接口。

#### 2.1.2 数据模型变更

**扩展 `prd_work_order` 表**:
```sql
ALTER TABLE prd_work_order ADD COLUMN backflush_flag TINYINT DEFAULT 0 COMMENT '是否倒冲工单: 0-否, 1-是';
ALTER TABLE prd_work_order ADD COLUMN completed_qty DECIMAL(14,4) DEFAULT 0 COMMENT '累计完工数量';
ALTER TABLE prd_work_order ADD COLUMN issued_qty DECIMAL(14,4) DEFAULT 0 COMMENT '累计发料数量';
```

**扩展 `prd_material_requirement_detail` 表**:
```sql
ALTER TABLE prd_material_requirement_detail ADD COLUMN backflush_flag TINYINT DEFAULT 0 COMMENT '是否倒冲物料: 0-否, 1-是';
ALTER TABLE prd_material_requirement_detail ADD COLUMN actual_consumed_qty DECIMAL(14,4) DEFAULT 0 COMMENT '实际消耗数量';
```

#### 2.1.3 核心实现步骤

**Step 1: 扩展 PrdWorkOrder 实体** (0.2d)
- 在 `PrdWorkOrder.java` 增加 `backflushFlag`, `completedQty`, `issuedQty` 字段

**Step 2: 扩展 PrdMaterialRequirementDetail 实体** (0.1d)
- 增加 `backflushFlag`, `actualConsumedQty` 字段

**Step 3: 实现 BackflushService** (0.5d)

新建 `PrdBackflushServiceImpl.java`:

```java
@Service
@RequiredArgsConstructor
@DS("erp")
public class PrdBackflushServiceImpl {

    private final PrdWorkOrderMapper workOrderMapper;
    private final PrdMaterialRequirementMapper requirementMapper;
    private final PrdMaterialRequirementDetailMapper detailMapper;
    private final IBdBomDetailService bomDetailService;
    private final WmMaterialStockMapper stockMapper;
    private final IWmStockOperationService stockOperationService;

    /**
     * 工单完工倒冲领料
     * @param woId 工单ID
     * @param completionQty 本次完工数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void backflushOnCompletion(Long woId, BigDecimal completionQty) {
        // 1. 查询工单
        PrdWorkOrder wo = workOrderMapper.selectById(woId);
        if (wo == null || wo.getBackflushFlag() == null || wo.getBackflushFlag() != 1) {
            return; // 非倒冲工单，跳过
        }

        // 2. 查询物料需求明细（含BOM展开的子项）
        List<PrdMaterialRequirementDetail> details = queryBackflushDetails(woId);
        if (details.isEmpty()) {
            log.warn("工单[{}]无倒冲物料需求明细", wo.getWoNo());
            return;
        }

        // 3. 创建生产领料单头
        WmProductionIssue issue = createProductionIssueHeader(wo);

        // 4. 按BOM定额计算消耗并生成领料行
        List<WmProductionIssueLine> lines = new ArrayList<>();
        Date now = new Date();

        for (PrdMaterialRequirementDetail detail : details) {
            if (detail.getBackflushFlag() == null || detail.getBackflushFlag() != 1) {
                continue; // 非倒冲物料跳过
            }

            // 消耗量 = 完工数量 × BOM单位用量 × (1 + 损耗率)
            BigDecimal bomQty = detail.getBomQuantity() != null ? detail.getBomQuantity() : BigDecimal.ONE;
            BigDecimal lossRate = detail.getLossRate() != null ? detail.getLossRate() : BigDecimal.ZERO;
            BigDecimal consumeQty = completionQty
                .multiply(bomQty)
                .multiply(BigDecimal.ONE.add(lossRate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)));

            // 校验库存
            BigDecimal stockQty = queryStockQty(detail.getMaterialId());
            if (stockQty.compareTo(consumeQty) < 0) {
                throw new ServiceException("倒冲物料[" + detail.getMaterialCode() + "]库存不足，需要"
                    + consumeQty + "，现有" + stockQty);
            }

            // 创建领料行
            WmProductionIssueLine line = new WmProductionIssueLine();
            line.setIssueId(issue.getIssueId());
            line.setMaterialId(detail.getMaterialId());
            line.setMaterialCode(detail.getMaterialCode());
            line.setMaterialName(detail.getMaterialName());
            line.setMaterialSpec(detail.getMaterialSpec());
            line.setBaseUomId(detail.getUnit());
            line.setQuantity(consumeQty);
            lines.add(line);

            // 回写实际消耗
            detail.setActualConsumedQty(
                detail.getActualConsumedQty() != null
                    ? detail.getActualConsumedQty().add(consumeQty)
                    : consumeQty);
            detailMapper.updateById(detail);
        }

        // 5. 执行库存扣减（逐行调用策略）
        for (WmProductionIssueLine line : lines) {
            stockOperationService.postProductionIssue(line, now);
        }

        // 6. 回写工单累计完工数量
        wo.setCompletedQty(
            wo.getCompletedQty() != null
                ? wo.getCompletedQty().add(completionQty)
                : completionQty);
        workOrderMapper.updateById(wo);

        log.info("工单[{}]完工倒冲完成，完工数量:{}，倒冲物料:{}种", wo.getWoNo(), completionQty, lines.size());
    }
}
```

**Step 4: 修改 PrdWorkOrderServiceImpl.complete()** (0.2d)

在 `complete()` 方法末尾增加倒冲调用：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void complete(Long woId) {
    // ... 原有校验和状态更新 ...
    wo.setWoStatus(STATUS_COMPLETED);
    wo.setActualEndDate(new Date());
    baseMapper.updateById(wo);

    // ★ 新增：完工倒冲领料
    BigDecimal completionQty = wo.getPlannedQty(); // 默认按计划数量倒冲
    // TODO: 后续支持部分完工时按实际完工数量倒冲
    backflushService.backflushOnCompletion(woId, completionQty);
}
```

**Step 5: 报工审核联动（预留接口）** (0.2d)

在 `PrdWorkReportServiceImpl` 中增加 `afterReportApproved()` 方法：

```java
/**
 * 报工审核通过后回调（供审批流调用）
 */
public void afterReportApproved(Long reportId) {
    PrdWorkReport report = baseMapper.selectById(reportId);
    if (report == null || report.getWoId() == null) return;

    // 更新工单累计完工数量
    PrdWorkOrder wo = workOrderMapper.selectById(report.getWoId());
    if (wo != null) {
        wo.setCompletedQty(
            (wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO)
                .add(report.getReportedQty() != null ? report.getReportedQty() : BigDecimal.ZERO));
        workOrderMapper.updateById(wo);
    }

    // 倒冲领料（如为倒冲工单）
    // backflushService.backflushOnCompletion(report.getWoId(), report.getReportedQty());
}
```

**Step 6: 字典与菜单** (0.1d)
- 字典：`prd_backflush_flag` (0-否, 1-是)
- 无需新增菜单（倒冲为自动逻辑，无独立页面）

#### 2.1.4 验收标准

| 验收项 | 标准 |
|--------|------|
| 非倒冲工单完工 | 正常完工，不触发倒冲 |
| 倒冲工单完工 | 自动按BOM展开物料，按定额计算消耗，扣减库存 |
| 库存不足 | 抛ServiceException，事务回滚，工单状态不变 |
| 库存流水 | 生成WmMaterialStockTx记录，sourceDocType=WM_PRODUCTION_ISSUE |
| 物料需求回写 | prd_material_requirement_detail.actual_consumed_qty更新正确 |
| 工单回写 | prd_work_order.completed_qty更新正确 |

---

### 任务 R-022: 产能日历

#### 2.2.1 业务背景

产能日历定义每个工作中心（车间/工位/生产线）在每个工作日的可用产能（工时），用于：
1. 生产排程时校验产能是否足够
2. 计算工单计划开始/结束日期
3. 为MES派工提供产能约束

与现有 `SchPlan/SchShift` 的关系：
- `SchPlan` 定义排班规则（如：三班倒、两班倒）
- `SchShift` 定义班次时间段（如：早班 08:00-16:00）
- `PrdCapacityCalendar` 是按日期展开后的**产能结果表**，记录每一天每个工作中心可用多少工时

#### 2.2.2 数据模型设计

**新建 `prd_capacity_calendar` 表**:

```sql
CREATE TABLE prd_capacity_calendar (
    calendar_id     BIGINT NOT NULL AUTO_INCREMENT COMMENT '日历ID',
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    calendar_date   DATE NOT NULL COMMENT '日期',
    object_type     VARCHAR(32) NOT NULL COMMENT '对象类型: WORKSHOP-车间, WORKSTATION-工位, LINE-生产线',
    object_id       BIGINT NOT NULL COMMENT '对象ID',
    object_code     VARCHAR(64) COMMENT '对象编码',
    object_name     VARCHAR(128) COMMENT '对象名称',
    shift_id        BIGINT COMMENT '班次ID',
    shift_code      VARCHAR(64) COMMENT '班次编码',
    shift_name      VARCHAR(128) COMMENT '班次名称',
    available_hours DECIMAL(6,2) DEFAULT 0 COMMENT '可用工时(小时)',
    planned_hours   DECIMAL(6,2) DEFAULT 0 COMMENT '已计划工时(小时)',
    actual_hours    DECIMAL(6,2) DEFAULT 0 COMMENT '实际已用工时(小时)',
    efficiency      DECIMAL(5,2) DEFAULT 100 COMMENT '效率系数(%)',
    effective_hours DECIMAL(6,2) DEFAULT 0 COMMENT '有效产能 = available_hours × efficiency / 100',
    remaining_hours DECIMAL(6,2) DEFAULT 0 COMMENT '剩余产能 = effective_hours - planned_hours',
    status          VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-有效, CLOSED-已关闭',
    remark          VARCHAR(512) COMMENT '备注',
    create_by       VARCHAR(64) COMMENT '创建者',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64) COMMENT '更新者',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        VARCHAR(2) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (calendar_id),
    UNIQUE KEY uk_date_object_shift (calendar_date, object_type, object_id, shift_id, tenant_id),
    INDEX idx_date (calendar_date),
    INDEX idx_object (object_type, object_id)
) COMMENT = '产能日历表';
```

#### 2.2.3 核心实现步骤

**Step 1: 实体类** (0.2d)
- `PrdCapacityCalendar.java` (Domain)
- `PrdCapacityCalendarBo.java` (BO)
- `PrdCapacityCalendarVo.java` (VO)

**Step 2: Mapper + XML** (0.2d)
- `PrdCapacityCalendarMapper.java`
- `PrdCapacityCalendarMapper.xml`
- 自定义查询：按日期范围+对象类型+对象ID查询

**Step 3: Service + Impl** (0.4d)

核心方法：

```java
public interface IPrdCapacityCalendarService {
    // CRUD
    TableDataInfo<PrdCapacityCalendarVo> selectPageList(PrdCapacityCalendarBo bo, PageQuery pageQuery);
    int insertByBo(PrdCapacityCalendarBo bo);
    int updateByBo(PrdCapacityCalendarBo bo);
    int deleteByIds(Long[] ids);

    // ★ 核心：根据排班计划生成产能日历
    void generateFromSchedule(Long planId, Date startDate, Date endDate);

    // ★ 核心：查询可用产能
    List<PrdCapacityCalendarVo> queryAvailableCapacity(String objectType, Long objectId,
                                                        Date startDate, Date endDate);

    // ★ 核心：占用产能（排程时调用）
    void occupyCapacity(Long calendarId, BigDecimal hours);

    // ★ 核心：释放产能
    void releaseCapacity(Long calendarId, BigDecimal hours);
}
```

`generateFromSchedule()` 实现逻辑：

```java
@Transactional(rollbackFor = Exception.class)
public void generateFromSchedule(Long planId, Date startDate, Date endDate) {
    // 1. 查询排班计划
    SchPlan plan = schPlanMapper.selectById(planId);
    if (plan == null) throw new ServiceException("排班计划不存在");

    // 2. 查询该计划下的所有班次
    List<SchShift> shifts = schShiftMapper.selectList(
        Wrappers.lambdaQuery(SchShift.class).eq(SchShift::getPlanId, planId));

    // 3. 查询该计划关联的工作中心（车间/工位/生产线）
    // TODO: 需要确认SchPlanTeam或其他关联表

    // 4. 按日期循环生成产能日历
    Calendar cal = Calendar.getInstance();
    cal.setTime(startDate);
    Calendar endCal = Calendar.getInstance();
    endCal.setTime(endDate);

    while (!cal.after(endCal)) {
        Date currentDate = cal.getTime();
        for (SchShift shift : shifts) {
            // 计算可用工时 = 班次总工时 - 休息工时
            BigDecimal availableHours = calculateAvailableHours(shift);

            PrdCapacityCalendar calendar = new PrdCapacityCalendar();
            calendar.setCalendarDate(currentDate);
            calendar.setShiftId(shift.getShiftId());
            calendar.setShiftCode(shift.getShiftCode());
            calendar.setShiftName(shift.getShiftName());
            calendar.setAvailableHours(availableHours);
            calendar.setEfficiency(new BigDecimal("100"));
            calendar.setEffectiveHours(availableHours);
            calendar.setRemainingHours(availableHours);
            // ... 设置object_type/object_id
            baseMapper.insert(calendar);
        }
        cal.add(Calendar.DATE, 1);
    }
}
```

**Step 4: Controller** (0.2d)
- `PrdCapacityCalendarController.java`
- 接口：增删改查 + generate + queryAvailable + occupy/release

**Step 5: 前端页面** (0.5d，可延至Week 6)
- 产能日历列表页（按日期+工作中心筛选）
- 产能甘特图/日历视图（可选，Week 6实现）

#### 2.2.4 验收标准

| 验收项 | 标准 |
|--------|------|
| 生成产能日历 | 根据SchPlan+SchShift按日期展开，生成正确可用工时 |
| 查询可用产能 | 按日期范围+工作中心查询，返回剩余产能>0的记录 |
| 占用产能 | 排程后planned_hours增加，remaining_hours减少 |
| 并发安全 | 占用产能时考虑并发，避免超卖（乐观锁或数据库CAS） |
| 唯一索引 | 同一日期+对象+班次不能重复生成 |

---

### 任务 R-023: 领料与完工入库联动

#### 2.3.1 业务背景

当前工单状态变更与库存操作完全脱节：
- 工单下达(release) → 不触发领料
- 工单完工(complete) → 不触发成品入库
- 报工审核 → 不更新工单累计产量

需要建立以下联动：
1. **工单下达 → 创建生产领料单**：根据物料需求自动生成WmProductionIssue
2. **报工审核 → 更新工单产量**：回写completedQty
3. **工单完工 → 成品入库**：将完工产品入到仓库/线边库
4. **倒冲工单完工 → 自动倒冲领料**（R-021已覆盖）

#### 2.3.2 联动时序图

```
┌─────────┐     ┌─────────────┐     ┌──────────────┐     ┌──────────┐
│  工单   │     │  物料需求   │     │  生产领料单  │     │  库存    │
└────┬────┘     └──────┬──────┘     └──────┬───────┘     └────┬─────┘
     │                 │                   │                  │
     │ release()       │                   │                  │
     │────────────────>│ 查询需求明细       │                  │
     │                 │──────────────────>│                  │
     │                 │                   │ 创建领料单       │
     │                 │                   │─────────────────>│
     │                 │                   │  等待人工发料    │
     │                 │                   │                  │
     │ startProduction()│                  │                  │
     │────────────────>│ 校验已领料         │                  │
     │                 │                   │                  │
     │ 报工/complete()  │                   │                  │
     │────────────────>│                   │                  │
     │                 │                   │ 倒冲领料(R-021)  │
     │                 │                   │─────────────────>│ 扣减原材料
     │                 │                   │                  │
     │                 │                   │ 完工入库          │
     │                 │                   │─────────────────>│ 增加成品
     │                 │                   │                  │
```

#### 2.3.3 核心实现步骤

**Step 1: 工单下达时创建领料单** (0.3d)

修改 `PrdWorkOrderServiceImpl.release()`：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void release(Long woId) {
    // ... 原有校验 ...
    wo.setWoStatus(STATUS_RELEASED);
    baseMapper.updateById(wo);

    // ★ 新增：创建生产领料单
    createProductionIssueOnRelease(wo);
}

private void createProductionIssueOnRelease(PrdWorkOrder wo) {
    // 1. 查询物料需求明细
    PrdMaterialRequirement reqQuery = new PrdMaterialRequirement();
    reqQuery.setPlanId(wo.getPlanId());
    List<PrdMaterialRequirement> reqs = requirementMapper.selectList(
        Wrappers.lambdaQuery(PrdMaterialRequirement.class)
            .eq(PrdMaterialRequirement::getPlanId, wo.getPlanId()));

    if (reqs.isEmpty()) return;

    // 2. 创建领料单头
    WmProductionIssue issue = new WmProductionIssue();
    issue.setWoId(wo.getWoId());
    issue.setWoNo(wo.getWoNo());
    issue.setIssueDate(new Date());
    issue.setStatus("CREATED"); // 已创建，等待发料
    issue.setWarehouseId(wo.getWorkshopId()); // 默认车间仓库，后续可配置
    productionIssueMapper.insert(issue);

    // 3. 创建领料单行（排除倒冲物料）
    for (PrdMaterialRequirement req : reqs) {
        List<PrdMaterialRequirementDetail> details = detailMapper.selectList(
            Wrappers.lambdaQuery(PrdMaterialRequirementDetail.class)
                .eq(PrdMaterialRequirementDetail::getRequirementId, req.getRequirementId()));

        for (PrdMaterialRequirementDetail detail : details) {
            // 倒冲物料不生成领料单行（完工时自动倒冲）
            if (detail.getBackflushFlag() != null && detail.getBackflushFlag() == 1) {
                continue;
            }

            WmProductionIssueLine line = new WmProductionIssueLine();
            line.setIssueId(issue.getIssueId());
            line.setMaterialId(detail.getMaterialId());
            line.setMaterialCode(detail.getMaterialCode());
            line.setMaterialName(detail.getMaterialName());
            line.setMaterialSpec(detail.getMaterialSpec());
            line.setBaseUomId(detail.getUnit());
            line.setQuantity(detail.getRequiredQuantity());
            // locationId 从物料默认库位获取，或从前端传入
            productionIssueLineMapper.insert(line);
        }
    }

    log.info("工单[{}]下达后自动生成领料单[{}]", wo.getWoNo(), issue.getIssueCode());
}
```

**Step 2: 开始生产时校验领料状态** (0.15d)

修改 `PrdWorkOrderServiceImpl.startProduction()`：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void startProduction(Long woId) {
    // ... 原有状态校验 ...

    // ★ 新增：校验是否已领料（非倒冲物料）
    // 查询该工单关联的领料单是否已全部发料
    // 或：简化校验，只校验是否有领料单
    List<WmProductionIssue> issues = productionIssueMapper.selectList(
        Wrappers.lambdaQuery(WmProductionIssue.class)
            .eq(WmProductionIssue::getWoId, woId));

    if (issues.isEmpty() && !isBackflushOrder(woId)) {
        throw new ServiceException("工单未生成领料单，请先下达工单");
    }

    wo.setWoStatus(STATUS_IN_PROGRESS);
    wo.setActualStartDate(new Date());
    baseMapper.updateById(wo);
}
```

**Step 3: 完工时成品入库** (0.3d)

新建 `ProductionCompletionStrategy`（库存策略）：

```java
@Component
@Slf4j
public class ProductionCompletionStrategy extends BaseStockOperationStrategy {

    private static final String TX_TYPE_PRODUCTION_IN = "PRODUCTION_IN";
    private static final String SOURCE_DOC_TYPE_WORK_ORDER = "PRD_WORK_ORDER";
    private static final int TX_FLAG_IN = 1;

    private final WmMaterialStockMapper stockMapper;
    private final WmMaterialStockTxMapper stockTxMapper;

    public ProductionCompletionStrategy(WmMaterialStockTxMapper stockTxMapper,
                                        WmMaterialStockMapper stockMapper) {
        super(stockTxMapper);
        this.stockMapper = stockMapper;
        this.stockTxMapper = stockTxMapper;
    }

    @Override
    public StockOperationTypeEnum getSupportedType() {
        // 新增枚举值 PRODUCTION_IN
        return StockOperationTypeEnum.PRODUCTION_IN;
    }

    @Override
    public void execute(Object... args) {
        Long woId = (Long) args[0];
        Long materialId = (Long) args[1];
        String materialCode = (String) args[2];
        String materialName = (String) args[3];
        BigDecimal quantity = (BigDecimal) args[4];
        Long warehouseId = (Long) args[5];
        Long locationId = (Long) args[6];
        Date now = (Date) args[7];

        // 1. 查询或创建库存记录
        WmMaterialStock stock = findOrCreateStock(materialId, warehouseId, locationId);

        // 2. 增加库存数量
        BigDecimal beforeQty = stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO;
        BigDecimal afterQty = beforeQty.add(quantity);
        stock.setStockQuantity(afterQty);
        stockMapper.updateById(stock);

        // 3. 生成库存流水
        WmMaterialStockTx tx = buildBaseTxFromStock(stock, TX_TYPE_PRODUCTION_IN, TX_FLAG_IN,
            quantity, beforeQty, afterQty, now);
        tx.setSourceDocType(SOURCE_DOC_TYPE_WORK_ORDER);
        tx.setSourceDocId(woId);
        tx.setRemark("工单完工入库");
        stockTxMapper.insert(tx);

        log.info("工单[{}]完工入库，物料[{}]，数量[{}]", woId, materialCode, quantity);
    }

    private WmMaterialStock findOrCreateStock(Long materialId, Long warehouseId, Long locationId) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, materialId);
        lqw.eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId);
        lqw.eq(locationId != null, WmMaterialStock::getLocationId, locationId);
        WmMaterialStock stock = stockMapper.selectOne(lqw);

        if (stock == null) {
            stock = new WmMaterialStock();
            stock.setMaterialId(materialId);
            // ... 设置仓库/库位信息 ...
            stock.setStockQuantity(BigDecimal.ZERO);
            stockMapper.insert(stock);
        }
        return stock;
    }
}
```

修改 `PrdWorkOrderServiceImpl.complete()` 增加成品入库：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void complete(Long woId) {
    // ... 原有校验 ...
    PrdWorkOrder wo = baseMapper.selectById(woId);

    wo.setWoStatus(STATUS_COMPLETED);
    wo.setActualEndDate(new Date());
    baseMapper.updateById(wo);

    // ★ 新增：完工倒冲领料（R-021）
    backflushService.backflushOnCompletion(woId, wo.getPlannedQty());

    // ★ 新增：成品入库
    BigDecimal completionQty = wo.getPlannedQty(); // 或实际完工数量
    stockOperationService.postProductionCompletion(
        woId, wo.getMaterialId(), wo.getMaterialCode(), wo.getMaterialName(),
        completionQty, wo.getWorkshopId(), null, new Date());
}
```

**Step 4: 报工审核回写产量** (0.15d)

修改 `PrdWorkReportServiceImpl`，增加审批状态变更回调：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public int updateByBo(PrdWorkReportBo bo) {
    PrdWorkReport entity = MapstructUtils.convert(bo, PrdWorkReport.class);

    // 状态从SUBMITTED变为APPROVED时，回写工单产量
    if ("APPROVED".equals(entity.getReportStatus())) {
        PrdWorkReport old = baseMapper.selectById(entity.getReportId());
        if (old != null && !"APPROVED".equals(old.getReportStatus())) {
            syncWorkOrderQuantity(old);
        }
    }

    return baseMapper.updateById(entity);
}

private void syncWorkOrderQuantity(PrdWorkReport report) {
    if (report.getWoId() == null || report.getReportedQty() == null) return;

    PrdWorkOrder wo = workOrderMapper.selectById(report.getWoId());
    if (wo == null) return;

    // 累计完工数量
    BigDecimal completedQty = wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO;
    wo.setCompletedQty(completedQty.add(report.getReportedQty()));

    // 报废数量
    if (report.getScrapQty() != null) {
        BigDecimal scrapQty = wo.getScrapQty() != null ? wo.getScrapQty() : BigDecimal.ZERO;
        wo.setScrapQty(scrapQty.add(report.getScrapQty()));
    }

    workOrderMapper.updateById(wo);
}
```

**Step 5: 库存枚举扩展** (0.1d)

```java
// StockOperationTypeEnum 新增
PRODUCTION_IN("PRODUCTION_IN", "完工入库"),
```

**Step 6: WmStockOperationService 扩展** (0.1d)

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void postProductionCompletion(Long woId, Long materialId, String materialCode,
                                      String materialName, BigDecimal quantity,
                                      Long warehouseId, Long locationId, Date now) {
    strategyFactory.getStrategy(StockOperationTypeEnum.PRODUCTION_IN)
        .execute(woId, materialId, materialCode, materialName, quantity, warehouseId, locationId, now);
}
```

#### 2.3.4 验收标准

| 验收项 | 标准 |
|--------|------|
| 工单下达 | 自动生成WmProductionIssue，行项目来自物料需求（排除倒冲物料） |
| 开始生产校验 | 无领料单且非倒冲工单，抛异常 |
| 报工审核 | 审核通过后，工单completedQty/scrapQty正确累加 |
| 工单完工 | 倒冲领料+成品入库同时完成，事务一致 |
| 库存流水 | 完工入库生成WmMaterialStockTx，txType=PRODUCTION_IN |
| 事务一致性 | 任一环节失败，全部回滚 |

---

## 三、Week 5 开发排期

| 日期 | 任务 | 负责人 | 产出 | 依赖 |
|------|------|--------|------|------|
| **Day 1 (周一)** | R-021 Step 1-2: 扩展实体字段 | 后端 | PrdWorkOrder/PrdMaterialRequirementDetail新增字段 | - |
| **Day 1 (周一)** | R-021 Step 3: BackflushService核心实现 | 后端 | PrdBackflushServiceImpl.java | Step 1-2 |
| **Day 2 (周二)** | R-021 Step 4-5: 修改complete()+预留报工接口 | 后端 | PrdWorkOrderServiceImpl/PrdWorkReportServiceImpl修改 | Step 3 |
| **Day 2 (周二)** | R-021 Step 6: 字典+自测 | 后端 | 倒冲流程跑通 | Step 4-5 |
| **Day 3 (周三)** | R-022 Step 1-2: 产能日历实体+Mapper | 后端 | PrdCapacityCalendar*.java + SQL | - |
| **Day 3 (周三)** | R-022 Step 3: Service实现 | 后端 | PrdCapacityCalendarServiceImpl.java | Step 1-2 |
| **Day 4 (周四)** | R-022 Step 4: Controller | 后端 | PrdCapacityCalendarController.java | Step 3 |
| **Day 4 (周四)** | R-023 Step 1-2: 下达领料+开始校验 | 后端 | release()/startProduction()修改 | - |
| **Day 5 (周五)** | R-023 Step 3-4: 完工入库+报工回写 | 后端 | ProductionCompletionStrategy/complete()修改 | Step 1-2 |
| **Day 5 (周五)** | R-023 Step 5-6: 枚举扩展+Service扩展 | 后端 | StockOperationTypeEnum/接口扩展 | Step 3-4 |
| **Day 5 (周五)** | 全量联调+修复 | 后端 | Week 5全部功能验证通过 | 全部 |

---

## 四、SQL DDL 汇总

```sql
-- ============================================
-- Week 5 SQL 变更脚本: week5_production_depth.sql
-- ============================================

-- 1. 扩展 prd_work_order（倒冲标识+累计产量）
ALTER TABLE prd_work_order
    ADD COLUMN backflush_flag TINYINT DEFAULT 0 COMMENT '是否倒冲工单: 0-否, 1-是',
    ADD COLUMN completed_qty DECIMAL(14,4) DEFAULT 0 COMMENT '累计完工数量',
    ADD COLUMN issued_qty DECIMAL(14,4) DEFAULT 0 COMMENT '累计发料数量';

-- 2. 扩展 prd_material_requirement_detail（倒冲标识+实际消耗）
ALTER TABLE prd_material_requirement_detail
    ADD COLUMN backflush_flag TINYINT DEFAULT 0 COMMENT '是否倒冲物料: 0-否, 1-是',
    ADD COLUMN actual_consumed_qty DECIMAL(14,4) DEFAULT 0 COMMENT '实际消耗数量';

-- 3. 新增产能日历表
CREATE TABLE prd_capacity_calendar (
    calendar_id     BIGINT NOT NULL AUTO_INCREMENT COMMENT '日历ID',
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    calendar_date   DATE NOT NULL COMMENT '日期',
    object_type     VARCHAR(32) NOT NULL COMMENT '对象类型: WORKSHOP-车间, WORKSTATION-工位, LINE-生产线',
    object_id       BIGINT NOT NULL COMMENT '对象ID',
    object_code     VARCHAR(64) COMMENT '对象编码',
    object_name     VARCHAR(128) COMMENT '对象名称',
    shift_id        BIGINT COMMENT '班次ID',
    shift_code      VARCHAR(64) COMMENT '班次编码',
    shift_name      VARCHAR(128) COMMENT '班次名称',
    available_hours DECIMAL(6,2) DEFAULT 0 COMMENT '可用工时(小时)',
    planned_hours   DECIMAL(6,2) DEFAULT 0 COMMENT '已计划工时(小时)',
    actual_hours    DECIMAL(6,2) DEFAULT 0 COMMENT '实际已用工时(小时)',
    efficiency      DECIMAL(5,2) DEFAULT 100 COMMENT '效率系数(%)',
    effective_hours DECIMAL(6,2) DEFAULT 0 COMMENT '有效产能',
    remaining_hours DECIMAL(6,2) DEFAULT 0 COMMENT '剩余产能',
    status          VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '状态',
    remark          VARCHAR(512) COMMENT '备注',
    create_by       VARCHAR(64) COMMENT '创建者',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64) COMMENT '更新者',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        VARCHAR(2) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (calendar_id),
    UNIQUE KEY uk_date_object_shift (calendar_date, object_type, object_id, shift_id, tenant_id),
    INDEX idx_date (calendar_date),
    INDEX idx_object (object_type, object_id)
) COMMENT = '产能日历表';

-- 4. 字典
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark) VALUES
('是否倒冲', 'prd_backflush_flag', '0', 'admin', NOW(), '生产工单/物料是否倒冲');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, status, create_by, create_time) VALUES
(1, '否', '0', 'prd_backflush_flag', '0', 'admin', NOW()),
(2, '是', '1', 'prd_backflush_flag', '0', 'admin', NOW());

-- 5. 菜单（产能日历）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
('产能日历', 2000, 10, 'capacityCalendar', 'erp/prd/capacityCalendar/index', 0, 0, 'C', '0', '0', 'prd:capacityCalendar:list', 'date', 'admin', NOW(), '产能日历管理');

-- 产能日历按钮权限
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time) VALUES
('产能日历查询', LAST_INSERT_ID(), 1, '', '', 0, 0, 'F', '0', '0', 'prd:capacityCalendar:query', '#', 'admin', NOW());
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time) VALUES
('产能日历新增', LAST_INSERT_ID()-1, 2, '', '', 0, 0, 'F', '0', '0', 'prd:capacityCalendar:add', '#', 'admin', NOW());
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time) VALUES
('产能日历修改', LAST_INSERT_ID()-2, 3, '', '', 0, 0, 'F', '0', '0', 'prd:capacityCalendar:edit', '#', 'admin', NOW());
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time) VALUES
('产能日历删除', LAST_INSERT_ID()-3, 4, '', '', 0, 0, 'F', '0', '0', 'prd:capacityCalendar:remove', '#', 'admin', NOW());
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time) VALUES
('产能日历导出', LAST_INSERT_ID()-4, 5, '', '', 0, 0, 'F', '0', '0', 'prd:capacityCalendar:export', '#', 'admin', NOW());
```

---

## 五、文件变更清单

### 5.1 新增文件

| 文件路径 | 说明 |
|---------|------|
| `prd/service/impl/PrdBackflushServiceImpl.java` | 倒冲服务 |
| `prd/domain/PrdCapacityCalendar.java` | 产能日历实体 |
| `prd/domain/bo/PrdCapacityCalendarBo.java` | 产能日历BO |
| `prd/domain/vo/PrdCapacityCalendarVo.java` | 产能日历VO |
| `prd/mapper/PrdCapacityCalendarMapper.java` | 产能日历Mapper |
| `prd/service/IPrdCapacityCalendarService.java` | 产能日历Service接口 |
| `prd/service/impl/PrdCapacityCalendarServiceImpl.java` | 产能日历Service实现 |
| `prd/controller/PrdCapacityCalendarController.java` | 产能日历Controller |
| `wm/strategy/impl/ProductionCompletionStrategy.java` | 完工入库策略 |

### 5.2 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `prd/domain/PrdWorkOrder.java` | 新增backflushFlag/completedQty/issuedQty |
| `prd/domain/PrdMaterialRequirementDetail.java` | 新增backflushFlag/actualConsumedQty |
| `prd/service/impl/PrdWorkOrderServiceImpl.java` | release()增加领料单创建，complete()增加倒冲+入库，startProduction()增加领料校验 |
| `prd/service/impl/PrdWorkReportServiceImpl.java` | updateByBo()增加审核回写产量 |
| `wm/enums/StockOperationTypeEnum.java` | 新增PRODUCTION_IN |
| `wm/service/IWmStockOperationService.java` | 新增postProductionCompletion() |
| `wm/service/impl/WmStockOperationServiceImpl.java` | 实现postProductionCompletion() |
| `wm/strategy/StockOperationStrategyFactory.java` | 注册ProductionCompletionStrategy |

### 5.3 SQL脚本

| 文件路径 | 说明 |
|---------|------|
| `script/sql/week5_production_depth.sql` | DDL+字典+菜单 |

---

## 六、联调测试用例

### 6.1 倒冲领料测试

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 创建BOM，设置某子件为倒冲物料 | BOM保存成功 |
| 2 | 创建工单，backflush_flag=1 | 工单状态=CREATED |
| 3 | 工单审批通过→下达 | 自动生成领料单（不含倒冲物料） |
| 4 | 开始生产 | 状态变为IN_PROGRESS |
| 5 | 工单完工 | 倒冲物料按定额扣减库存，生成库存流水 |
| 6 | 查询库存 | 倒冲物料库存减少，成品库存增加 |

### 6.2 产能日历测试

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 调用generateFromSchedule(planId, 2026-06-01, 2026-06-07) | 生成7天产能日历记录 |
| 2 | 查询可用产能 | 返回remaining_hours > 0的记录 |
| 3 | 占用产能 | planned_hours增加，remaining_hours减少 |
| 4 | 重复生成同一天 | 抛异常（唯一索引约束） |

### 6.3 领料联动测试

| 步骤 | 操作 | 预期结果 |
|------|------|---------|
| 1 | 工单下达 | 自动生成WmProductionIssue，状态=CREATED |
| 2 | 未下达工单直接开始生产 | 抛异常"工单未生成领料单" |
| 3 | 报工审核通过 | 工单completedQty累加 |
| 4 | 工单完工 | 成品入库，库存增加 |

---

> **审查要点提示**:
> 1. 倒冲计算逻辑是否正确（BOM用量 × 完工数量 × (1+损耗率)）
> 2. 事务边界是否合理（complete()中倒冲+入库是否在同一事务）
> 3. 产能日历的object_type/object_id如何与SchPlanTeam关联
> 4. 报工回写产量是否考虑多次报工累加
> 5. 库存策略工厂是否正确注册新策略
