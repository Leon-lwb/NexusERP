# MES P0-7 单元测试补齐 + P0-3 进站/出站+流程指引 并行实施方案

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（任务级 checkbox 跟踪）。Steps 用 `- [ ]` 标记。
>
> **编制日期**: 2026-07-27
> **计划周期**: Week 28-30（2026-08-03 ~ 08-19，P0-9/P0-2/P0-5 提前完工可提前启动）
> **所属计划**: [2026-07-24-mes-p0-development-plan-v1.0.md](./2026-07-24-mes-p0-development-plan-v1.0.md) §3.7（P0-7）+ §3.3（P0-3）
> **状态**: 待用户批准 -> 进入执行

---

## 实施结果摘要

| 任务 | 状态 | 交付物 |
|------|------|--------|
| P0-7 测试基础设施 | ⬜ | MES pom.xml JaCoCo + surefire 覆盖配置（参考 IM 模块） |
| P0-7 spc/qc/eq/lt/common 测试 | ⬜ | ~12 测试类，SPC 算法 80%+ 覆盖 |
| P0-7 pro 测试 | ⬜ | ~7 测试类（含 P0-3 新增 entry/exit 行为） |
| P0-3 DDL 扩展 + 2 新表 | ⬜ | ry-mes.sql 并入：pro_flow_card_step 扩展 + pro_step_guide + pro_step_check_rule |
| P0-3 后端规则引擎 + entry/exit/skip | ⬜ | ProEntryCheckService 规则引擎 + entry/exit/skip 编排 |
| P0-3 前端 | ⬜ | 工序指引配置页 + 校验规则配置页 + 详情页进站/出站/跳站交互 |
| P0-3 菜单/字典 SQL | ⬜ | 菜单 4200+4207-4215 + 2 字典 |
| 编译 + lint + 测试验证 | ⬜ | mvn test 全绿 + eslint 0 错误 |

---

## 一、Context（背景与目标）

### 1.1 为什么做这一阶段

P0-9（批次）、P0-2（工装）、P0-5（E-SOP）已于 2026-07-24~26 提前完整交付，三者的契约方法（`checkLotUsable`/`checkToolingAvailable`/`getSopForStep`）均已就绪。这为 **P0-3 进站/出站校验**扫清了全部前置依赖。同时，核心模块（pro/qc/spc/lt）**零单元测试**的回归风险日益突出，**P0-7** 必须尽快补齐质量底线。

并行推进两者的理由：
- **无文件冲突**：P0-7 仅改 `src/test/` + `pom.xml`；P0-3 改 `src/main/` pro 包 + 前端。两条线文件不交叠。
- **唯一交叠点**：P0-7 的 `ProFlowCardServiceImplTest` 须测试 P0-3 改造后的 `startStep/completeStep` 行为。因此 **pro 模块测试放在 P0-3 编码完成后**（见 §五 并行策略）。

### 1.2 本阶段要解决的问题

**P0-7（质量底线）**：
1. MES pom.xml 无 JaCoCo，无法度量覆盖率
2. 根 pom surefire `<groups>${profiles.active}</groups>` 过滤——现有 3 个 eq 测试无 `@Tag`，`mvn test` 在 dev profile 下**根本不执行**
3. pro/qc/spc/lt/common 五模块零单元测试，SPC 算法核心无任何回归保护

**P0-3（生产执行核心）**：
1. `startStep()`（line 200）当前纯状态推进，**无任何进站校验**——物料不齐套/工装不可用/SOP 未发布都能开工
2. `completeStep()`（line 239）当前无出站校验——数量未达标/异常未关闭也能完工
3. `stepStatus` 已定义 `SKIPPED` 但**跳站逻辑未实现**，无防错
4. 工序级流程指引（作业提示/SOP 链接）无配置能力
5. 进站/出站校验规则不可配置，硬编码无弹性

### 1.3 关键边界（已确认）

#### P0-3 边界

- **规则驱动而非硬编码**：进站/出站校验项由 `pro_step_check_rule` 配置（check_type + check_stage=ENTRY/EXIT + enabled），规则引擎按配置执行。`pro_flow_card_step` 无需预绑 tooling_id/lot_id——校验上下文由 entry API 入参（操作员扫码带入 toolingId/lotId）或按工序所属工位/工单查询。
- **entry/exit 是编排层，startStep/completeStep 保持纯状态机**：`entry(ProEntryBo)` = ENTRY 规则校验 → 通过则调 `startStep` + 记录 `entry_check_result=PASS` + 推送 SOP；`exit(ProExitBo)` = EXIT 规则校验 → 通过则调 `completeStep` + 记录 `exit_check_result=PASS`。现有 `startStep/completeStep` 签名不变（向后兼容 + 供测试直接调用）。
- **DDL 复用既有字段**：`pro_flow_card_step` 已有 `actual_start_time`（=进站时间）/`actual_end_time`（=出站时间）/`step_status`（含 SKIPPED）。**仅新增 5 字段**：`entry_check_result`/`entry_check_remark`/`exit_check_result`/`exit_check_remark`/`skip_reason`。**不重复加 entry_time/exit_time**（主计划 §3.3.3 DDL 此项冗余，校正）。
- **pro_step_guide 收敛为 TEXT + SOP_LINK 两类**（P0-5 联合评审结论）：去掉 IMAGE/VIDEO，删除 `media_url`。富媒体作业指导统一走 P0-5 `sop_document`；SOP_LINK 类型校验 `sop_id` 在 `sop_process_binding` 已绑定到同 `process_id`。
- **v1.0 实现的校验类型**（其余规则建表支持但默认禁用，返回 PASS+"暂未实现"）：
  - ENTRY 实现：`SERIAL_GATE`(内置 always-on，上一工序 COMPLETED) / `SOP_VERSION`(P0-5 就绪) / `TOOLING_AVAILABLE`(P0-2 就绪，入参 toolingId) / `EQUIPMENT_STATUS`(eq 就绪，查工位设备 NORMAL) / `LOT_AVAILABLE`(P0-9 就绪，入参 lotId+materialId)
  - ENTRY 存根：`MATERIAL_KITTING`(依赖 P0-1 未建) / `OPERATOR_SKILL`(无资质表)
  - EXIT 实现：`QUANTITY_REACHED`(good+defect+scrap=planned) / `INSPECTION_REQUIRED`(关键工序 IPQC PASS) / `EXCEPTION_CLOSED`(本工序无活跃安灯)
  - EXIT 存根：`PARAMETER_OK`(dc 参数达标，算法复杂延后)
- **跳站防错**：`skip(ProSkipBo)` 校验 `@SaCheckPermission('mes:pro:flowcard:skip')` + 必填 reason + 记录 `skip_reason` + 置 `step_status=SKIPPED` + 推进到下一工序。首道工序不允许跳过（防跳过 mandatory 工序）。
- **SOP 推送触发点**：`entry` 通过后调 `ISopProcessBindingService.getSopForStep(stepRecordId)`，若返回非空，通过 `SseMessageUtils.publishMessage` 推送到工位终端（P0-5 已建契约，本周接入触发时机）。

#### P0-7 边界

- **纯单元测试为主**（沿用既有 eq 测试模式）：`@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`，无 Spring 上下文，无 `@SpringBootTest`。例外：`WorkOrderReleaseConsumer` 依赖 `@RabbitListener`/`BaseMessageConsumer` 框架，用 `@SpringBootTest` 或拆分测试核心逻辑。
- **断言沿用 JUnit5 + Mockito verify**（既有模式），不强制 AssertJ（虽 spring-boot-starter-test 已含）。SPC 算法测试可用 AssertJ 流式断言提升可读性。
- **surefire 覆盖配置**（关键）：参考 `ruoyi-im/pom.xml`，在 MES pom.xml 覆盖 `maven-surefire-plugin` 的 `<groups>` 配置，使无 `@Tag` 的测试也能在 dev profile 执行；或给所有测试加 `@Tag("dev")`。**采用 IM 模块方案**（属性化 groups + 排除 integration tag）。
- **JaCoCo 配置**（参考 IM 模块）：`jacoco-maven-plugin 0.8.12` prepare-agent + report + check，初始门槛 `mes.jacoco.lineRatio=0.50`（达标 50%，目标 60%+，SPC 80%+ 可后续调高）。
- **SPC 重点**：`SpcControlChartServiceImpl` 的 `analyze`/`buildGroups`/`calcControlLimit`/`judgeOoc` 四核心方法 236 行，参数化测试覆盖 5 种图表类型 × 边界样本 × 4 条 OOC 规则。依赖 `IDcDataRecordService`/`IProAndonService` mock + `RedisUtils` mockStatic。
- **`IMesMbomService` 在 `pro/service/` 下**（非 md/），主计划 §3.7 此项有误，校正。

### 1.4 预期结果

**P0-7**：
- `mvn test -pl ruoyi-modules/ruoyi-mes -am -DskipTests=false` 全绿
- JaCoCo 报告生成，行覆盖 ≥50%（初始门槛），SPC 算法 ≥80%
- ~19 测试类、≥87 用例覆盖 pro/qc/spc/eq/lt/common

**P0-3**：
- 进站校验拦截不合规开工（如 SOP 未发布/工装不可用/上一工序未完成）
- 出站校验拦截不合规完工（如数量未达标/异常未关闭）
- 跳站需权限+原因，首道工序不可跳
- 工序指引与校验规则可配置（CRUD 页面）
- `mvn compile` + eslint 通过

---

## 二、关键事实（探索结论，非假设）

| 事实 | 依据 |
|------|------|
| `startStep()` line 200-231 当前 3 步纯状态推进，零校验 | [ProFlowCardServiceImpl.java:200](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProFlowCardServiceImpl.java) |
| `completeStep()` line 239-277 当前 4 步（状态推进+IPQC触发+数量更新+完工聚合），零出站校验 | 同上 line 239 |
| `pro_flow_card_step` 已有 actual_start_time/actual_end_time/step_status(含SKIPPED)/operator_id/is_key_process/process_id | [ry-mes.sql:865](../../script/sql/ry-mes.sql) |
| `checkToolingAvailable(Long toolingId)` 返回 `{usable, reason, toolingId, toolingCode, status}`，仅 IDLE 可用 | [ITgToolingService.java:100](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/tg/service/ITgToolingService.java) + Impl line 362 |
| `checkLotUsable(Long materialId, Long lotId)` 返回 `{usable, reason, lotId, lotCode, remainingQty}`，校验 IN_STOCK+QC PASS+余量>0 | [ILtLotService.java:139](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/service/ILtLotService.java) + Impl line 413 |
| `getSopForStep(Long stepRecordId)` 经 ProFlowCardStep.processId 桥接返回 SopDocumentVo（含签名 URL） | [ISopProcessBindingService.java:74](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/sop/service/ISopProcessBindingService.java) |
| 既有 eq 测试模式：JUnit5 + Mockito(@ExtendWith(MockitoExtension.class)) + 纯单元测试，无 Spring，方法命名 `{method}{Condition}{Expected}` | [EqRepairSpareServiceImplTest.java](../../ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/eq/service/impl/EqRepairSpareServiceImplTest.java) |
| MES pom.xml 无 JaCoCo，根 pom surefire `<groups>${profiles.active}</groups>` 过滤无 @Tag 测试 | [ruoyi-mes/pom.xml:117](../../ruoyi-modules/ruoyi-mes/pom.xml) + 根 pom line 502 |
| IM 模块已有完整 JaCoCo + surefire 覆盖配置可参考（jacoco 0.8.12, lineRatio 0.50） | [ruoyi-im/pom.xml](../../ruoyi-modules/ruoyi-im/pom.xml) |
| SPC 算法核心 analyze/buildGroups/calcControlLimit/judgeOoc 236 行，5 图表类型 + 4 OOC 规则 | [SpcControlChartServiceImpl.java:184-497](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/spc/service/impl/SpcControlChartServiceImpl.java) |
| 前端 flowcard.ts API + detail.vue 已有开工/完工按钮，进站出站在此基础上增强 | [plus-ui/src/views/mes/pro/flow-card/detail.vue](../../plus-ui/src/views/mes/pro/flow-card/detail.vue) |
| 菜单 4201-4206 已被 eq 子目录/工装M-dir/文档M-dir 占用；4200 + 4207-4215 可用 | [ry-vue.sql](../../script/sql/ry-vue.sql) 核实 |
| MES 模块 `@DS("mes")`，ERP 桥接用编程式 `DynamicDataSourceContextHolder.push("erp")` | [MesErpBridgeServiceImpl.java](../../ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common/bridge/MesErpBridgeServiceImpl.java) |

---

## 三、任务清单

### P0-7 单元测试补齐

| # | 任务 | 优先级 | 交付物 | 验收标准 | 状态 |
|---|------|--------|--------|----------|------|
| T7-1 | pom.xml 测试基础设施 | 高 | MES pom.xml | JaCoCo + surefire 覆盖配置，`mvn test` 能执行无 @Tag 测试 | ⬜ |
| T7-2 | spc 测试（算法核心） | 高 | SpcControlChartServiceImplTest + SpcDataPointServiceImplTest | 5 图表类型 + 4 OOC 规则 + 边界，行覆盖 ≥80% | ⬜ |
| T7-3 | qc 测试 | 高 | QcIqcServiceImplTest + QcIpqcServiceImplTest + QcOqcServiceImplTest | CRUD + inspect 状态机 + exists 守卫 | ⬜ |
| T7-4 | lt 测试 | 高 | LtLotServiceImplTest + LtWoConsumptionServiceImplTest | 状态机 5 态 + FEFO + consume 并发 + checkLotUsable 契约 | ⬜ |
| T7-5 | eq 补充测试 | 中 | EqEquipmentServiceImplTest + EqMaintenancePlanServiceImplTest | 状态联动 + 保养日期计算 | ⬜ |
| T7-6 | common 测试 | 中 | WorkOrderReleaseConsumerTest（或拆分） | MQ 消费 + 租户上下文 + 幂等 | ⬜ |
| T7-7 | pro 测试（P0-3 后） | 高 | 7 测试类 | 含 entry/exit/skip 新行为 + 既有状态机 | ⬜ |
| T7-8 | 覆盖率验证 | 高 | JaCoCo 报告 | 行 ≥50%（门槛），SPC ≥80%，`mvn test` 全绿 | ⬜ |

### P0-3 进站/出站+流程指引

| # | 任务 | 优先级 | 交付物 | 验收标准 | 状态 |
|---|------|--------|--------|----------|------|
| T3-1 | DDL 扩展 + 2 新表 | 高 | ry-mes.sql 并入 | pro_flow_card_step +5 字段 + pro_step_guide + pro_step_check_rule | ⬜ |
| T3-2 | Entity/BO/VO/Mapper | 高 | ProStepGuide + ProStepCheckRule 各一套 + ProFlowCardStep 扩字段 | 字段对齐 DDL | ⬜ |
| T3-3 | 规则引擎 Service | 高 | IProEntryCheckService + Impl | 9 种 check_type 处理器 + ENTRY/EXIT 编排 | ⬜ |
| T3-4 | entry/exit/skip 编排 | 高 | ProFlowCardServiceImpl 扩展 + 3 新方法 + Controller 端点 | entry→startStep+推送SOP；exit→completeStep；skip 防错 | ⬜ |
| T3-5 | 指引/规则配置 CRUD | 高 | ProStepGuideController + ProStepCheckRuleController | CRUD + SOP_LINK 校验 | ⬜ |
| T3-6 | 前端 API + 页面 | 高 | 2 配置页 + detail.vue 进站/出站/跳站交互 | 全选择器化 + dict-tag | ⬜ |
| T3-7 | 菜单/字典 SQL | 中 | ry-mes.sql 并入 | 菜单 4200+4207-4215 + 2 字典 | ⬜ |
| T3-8 | 编译 + lint 验证 | 高 | - | mvn compile + eslint 通过 | ⬜ |

---

## 四、详细任务分解

### P0-3 任务 1：DDL（扩展 1 表 + 新增 2 表）

**File:** 并入 `script/sql/ry-mes.sql`（不再建 doc/mes/sql 增量脚本，依据 memory `mes-doc-sql-cleanup`）

```sql
-- ① 扩展 pro_flow_card_step（仅加 5 字段，复用 actual_start_time/actual_end_time）
ALTER TABLE pro_flow_card_step
    ADD COLUMN entry_check_result VARCHAR(16) NULL DEFAULT NULL COMMENT '进站校验结果(PASS/FAIL)',
    ADD COLUMN entry_check_remark VARCHAR(500) NULL DEFAULT NULL COMMENT '进站校验备注(失败原因)',
    ADD COLUMN exit_check_result  VARCHAR(16) NULL DEFAULT NULL COMMENT '出站校验结果(PASS/FAIL)',
    ADD COLUMN exit_check_remark  VARCHAR(500) NULL DEFAULT NULL COMMENT '出站校验备注(失败原因)',
    ADD COLUMN skip_reason        VARCHAR(500) NULL DEFAULT NULL COMMENT '跳站原因';

-- ② 工序流程指引配置（收敛 TEXT + SOP_LINK，无 media_url）
CREATE TABLE IF NOT EXISTS pro_step_guide (
    guide_id      BIGINT NOT NULL COMMENT '指引ID',
    tenant_id     VARCHAR(20) NOT NULL DEFAULT '000000',
    process_id    BIGINT NOT NULL COMMENT '工序ID(ERP bd_process)',
    guide_type    VARCHAR(16) NOT NULL COMMENT 'TEXT/SOP_LINK',
    guide_title   VARCHAR(128) NULL COMMENT '指引标题',
    guide_content TEXT NULL COMMENT '文本内容(TEXT类型用)',
    sop_id        BIGINT NULL COMMENT '关联SOP文档(SOP_LINK类型用,须在sop_process_binding已绑定同process_id)',
    sort_order    INT DEFAULT 0,
    enabled       CHAR(1) DEFAULT '1',
    create_dept BIGINT, create_by BIGINT, create_time DATETIME,
    update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (guide_id), INDEX idx_process_id (process_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序流程指引配置';

-- ③ 工序校验规则配置
CREATE TABLE IF NOT EXISTS pro_step_check_rule (
    rule_id       BIGINT NOT NULL COMMENT '规则ID',
    tenant_id     VARCHAR(20) NOT NULL DEFAULT '000000',
    process_id    BIGINT NOT NULL COMMENT '工序ID',
    check_type    VARCHAR(32) NOT NULL COMMENT 'MATERIAL_KITTING/OPERATOR_SKILL/EQUIPMENT_STATUS/SOP_VERSION/TOOLING_AVAILABLE/LOT_AVAILABLE/INSPECTION_REQUIRED/QUANTITY_REACHED/PARAMETER_OK/EXCEPTION_CLOSED',
    check_stage   VARCHAR(16) NOT NULL COMMENT 'ENTRY/EXIT',
    enabled       CHAR(1) DEFAULT '1',
    error_msg     VARCHAR(200) NULL COMMENT '校验失败提示',
    sort_order    INT DEFAULT 0,
    create_dept BIGINT, create_by BIGINT, create_time DATETIME,
    update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (rule_id), INDEX idx_process_id (process_id), INDEX idx_stage (check_stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序校验规则配置';
```

### P0-3 任务 2：Entity/BO/VO/Mapper

- `ProStepGuide` / `ProStepGuideBo` / `ProStepGuideVo` / `ProStepGuideMapper`（镜像 sop/tg 模式）
- `ProStepCheckRule` / `ProStepCheckRuleBo` / `ProStepCheckRuleVo` / `ProStepCheckRuleMapper`
- `ProFlowCardStep` 实体 +5 字段（entryCheckResult/entryCheckRemark/exitCheckResult/exitCheckRemark/skipReason）
- 入参 BO：`ProEntryBo{cardId, stepRecordId, operatorId, toolingId?, lotId?, materialId?}`、`ProExitBo{cardId, stepRecordId, goodQty, defectQty, scrapQty}`、`ProSkipBo{cardId, stepRecordId, reason}`
- 结果 VO：`ProCheckResultVo{passed, failures:[{checkType, errorMsg}], stepRecordId, sopPushed?}`

### P0-3 任务 3：规则引擎 Service

**File:** `pro/service/IProEntryCheckService.java` + `pro/service/impl/ProEntryCheckServiceImpl.java`

```java
public interface IProEntryCheckService {
    /** 执行进站校验（ENTRY 规则 + 内置 SERIAL_GATE） */
    ProCheckResultVo runEntryChecks(Long stepRecordId, ProEntryBo context);
    /** 执行出站校验（EXIT 规则） */
    ProCheckResultVo runExitChecks(Long stepRecordId, ProExitBo context);
}
```

**规则处理器设计**（策略模式，每 check_type 一个 private 方法）：

| check_type | stage | 实现 | 依赖 |
|------------|-------|------|------|
| SERIAL_GATE | ENTRY(内置) | 查 sequenceNum-1 的 step，须 COMPLETED 或 SKIPPED | pro_flow_card_step |
| SOP_VERSION | ENTRY | getSopForStep != null && sop.status=PUBLISHED | ISopProcessBindingService |
| TOOLING_AVAILABLE | ENTRY | context.toolingId != null → checkToolingAvailable(toolingId).usable | ITgToolingService |
| LOT_AVAILABLE | ENTRY | context.lotId != null → checkLotUsable(materialId, lotId).usable | ILtLotService |
| EQUIPMENT_STATUS | ENTRY | 查工位关联 eq_equipment.status=NORMAL（无设备则 PASS） | IEqEquipmentService |
| MATERIAL_KITTING | ENTRY | 存根：PASS + "待P0-1"（默认 enabled=0） | - |
| OPERATOR_SKILL | ENTRY | 存根：PASS + "待资质表"（默认 enabled=0） | - |
| QUANTITY_REACHED | EXIT | goodQty+defectQty+scrapQty == plannedQty（容差） | pro_flow_card_step |
| INSPECTION_REQUIRED | EXIT | isKeyProcess=1 → 查 QcIpqc inspectResult=PASS | IQcIpqcService |
| EXCEPTION_CLOSED | EXIT | 本 stepRecordId 无活跃安灯(CALLED/RESPONDED) | IProAndonService |
| PARAMETER_OK | EXIT | 存根：PASS + "待dc参数"（默认 enabled=0） | - |

**编排**：加载 process_id 的 enabled 规则（按 sort_order）→ 逐个执行 → 收集 failures → 任一 FAIL 则 passed=false。SERIAL_GATE 始终执行（不查表）。

### P0-3 任务 4：entry/exit/skip 编排

**File:** `pro/service/impl/ProFlowCardServiceImpl.java` 扩展 + `pro/controller/ProFlowCardController.java` 新端点

```java
// IProFlowCardService 新增
ProCheckResultVo entry(ProEntryBo bo);    // runEntryChecks → PASS则 startStep + 记录entry_check_result + pushSop
ProCheckResultVo exit(ProExitBo bo);      // runExitChecks → PASS则 completeStep + 记录exit_check_result
void skip(ProSkipBo bo);                  // 权限+reason+首道禁跳 → step_status=SKIPPED + skip_reason + 推进下一工序
```

**entry 逻辑**：
1. `runEntryChecks(stepRecordId, bo)` → 若 passed=false，记 `entry_check_result=FAIL`+remark，返回结果（不转状态）
2. 若 passed=true → 调既有 `startStep(cardId, stepRecordId, operatorId)` → 记 `entry_check_result=PASS` → `getSopForStep` 非空则 `SseMessageUtils.publishMessage` 推送 + `sopPushed=true`

**exit 逻辑**：
1. `runExitChecks(stepRecordId, bo)` → FAIL 记 `exit_check_result=FAIL`，返回
2. PASS → 调既有 `completeStep(cardId, stepRecordId, goodQty, defectQty, scrapQty)` → 记 `exit_check_result=PASS`

**skip 逻辑**：
1. `sequenceNum==1` 抛"首道工序不可跳过"
2. CAS `step_status: PENDING→SKIPPED` + 写 `skip_reason`
3. 推进流转卡 `current_step_id` 到下一未完成工序

**Controller 端点**：
- `POST /mes/pro/flow-card/entry` → `entry`
- `POST /mes/pro/flow-card/exit` → `exit`
- `POST /mes/pro/flow-card/skip` → `skip`（`@SaCheckPermission('mes:pro:flowcard:skip')`）

### P0-3 任务 5：指引/规则配置 CRUD

**File:** `pro/controller/ProStepGuideController.java` + `pro/controller/ProStepCheckRuleController.java`

- 标准 list/get/add/edit/del（镜像 tg/sop 模式）
- `ProStepGuideServiceImpl.add/update` 时若 `guide_type=SOP_LINK` → 校验 `sop_id` 在 `sop_process_binding` 存在且 `process_id` 匹配
- 删除守卫：存在引用时拒绝（对齐 P0-2/P0-9 孤儿守卫）

### P0-3 任务 6：前端

- `api/mes/pro/stepGuide.ts` + `api/mes/pro/stepCheckRule.ts` + `api/mes/pro/flowcard.ts` 增 entry/exit/skip
- `views/mes/pro/step-guide/index.vue`（指引配置，工序选择器复用 ERP `/erp/bd/process/list`，SOP 选择器复用 P0-5）
- `views/mes/pro/step-check-rule/index.vue`（规则配置，check_type/check_stage dict-tag）
- `views/mes/pro/flow-card/detail.vue` 增强：开工按钮 → 调 entry（失败弹校验结果对话框）；完工按钮 → 调 exit；新增"跳站"按钮（带 reason 输入 + 权限）

### P0-3 任务 7：菜单/字典 SQL

并入 `script/sql/ry-mes.sql` + 独立菜单/字典 SQL（落库）：

- 菜单（父 4001 生产执行）：
  - 4200 C 工序指引配置（path=step-guide, component=mes/pro/step-guide/index）
  - 4207 C 校验规则配置（path=step-check-rule）
  - 4208-4215 F 按钮（指引 add/edit/remove + 规则 add/edit/remove + flowcard entry/exit/skip 权限）
- 字典：`mes_guide_type`(TEXT/SOP_LINK) + `mes_check_stage`(ENTRY/EXIT) + `mes_check_type`(10 项)
- 编码规则：pro_step_guide/pro_step_check_rule 编码可选（无强编码需求，主键雪花即可）

### P0-7 任务 1：pom.xml 测试基础设施

**File:** `ruoyi-modules/ruoyi-mes/pom.xml`（参考 `ruoyi-im/pom.xml`）

```xml
<properties>
    <mes.jacoco.lineRatio>0.50</mes.jacoco.lineRatio>
</properties>
<build>
    <plugins>
        <!-- JaCoCo -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.12</version>
            <executions>
                <execution><id>prepare-agent</id><goals><goal>prepare-agent</goal></goals></execution>
                <execution><id>report</id><phase>test</phase><goals><goal>report</goal></goals></execution>
                <execution><id>check</id><goals><goal>check</goal></goals>
                    <configuration>
                        <rules><rule>
                            <element>BUNDLE</element>
                            <limits><limit><counter>LINE</counter>
                                <minimum>${mes.jacoco.lineRatio}</minimum></limit></limits>
                        </rule></rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
        <!-- surefire 覆盖（使无 @Tag 测试在 dev profile 执行）-->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <argLine>@{argLine}</argLine>
                <groups combine.self="override"></groups>  <!-- 清空根 pom 的 groups 过滤 -->
                <excludedGroups>integration</excludedGroups>
            </configuration>
        </plugin>
    </plugins>
</build>
```

> ⚠️ `<groups combine.self="override">` 清空根 pom 的 `${profiles.active}` 过滤是关键，否则测试不执行。验证：`mvn test -pl ruoyi-modules/ruoyi-mes -am -DskipTests=false` 应执行测试类。

### P0-7 任务 2-7：测试类清单

| 测试类 | 模块 | 核心用例 | 目标覆盖 |
|--------|------|---------|---------|
| `SpcControlChartServiceImplTest` | spc | 5 图表类型控制限 + 4 OOC 规则 + 边界(n<2/n>10/数据不足) + 安灯限频 | ≥80% |
| `SpcDataPointServiceImplTest` | spc | listByChartId 分组 | 60% |
| `QcIqcServiceImplTest` | qc | inspect 状态机 + existsByReceiveAndMaterial 守卫 | 65% |
| `QcIpqcServiceImplTest` | qc | autoCreateForKeyProcess + inspect | 65% |
| `QcOqcServiceImplTest` | qc | inspect + exists 守卫 | 65% |
| `LtLotServiceImplTest` | lt | 5 态状态机 + FEFO 排序 + consume 并发超卖 + checkLotUsable 契约 + applyIqcResult 钩子 | 65% |
| `LtWoConsumptionServiceImplTest` | lt | confirmPick 多批次部分消耗 + 事务回滚 | 65% |
| `EqEquipmentServiceImplTest` | eq | updateStatus 联动维修 + SCRAPPED 终态保护 | 60% |
| `EqMaintenancePlanServiceImplTest` | eq | 下次保养日期计算（cycleDays 空/非空） | 60% |
| `WorkOrderReleaseConsumerTest` | common | doProcess 租户上下文 + 幂等（@SpringBootTest 或拆分） | 60% |
| `ProWorkOrderServiceImplTest` | pro | CRUD + release + completeWo + createFromErpMessage | 70% |
| `ProFlowCardServiceImplTest` | pro | 状态机 5 态 + **entry/exit/skip 新行为** + 自动流转 | 70% |
| `ProDispatchServiceImplTest` | pro | createDispatch + start/complete + cancel | 65% |
| `ProSnServiceImplTest` | pro | generateSnForCard + 父子关系 + trace | 65% |
| `MesMbomServiceImplTest` | pro | activate + verify | 60% |
| `ProAndonServiceImplTest` | pro | callAndon + respond/resolve/cancel + dashboardStats | 60% |
| `ProTraceabilityServiceImplTest` | pro | backward + forward 追溯 | 60% |
| `ProEntryCheckServiceImplTest` | pro(P0-3) | 9 规则处理器 + ENTRY/EXIT 编排 + SERIAL_GATE | 70% |

**命名规范**（沿用既有）：`{ServiceImplName}Test`，方法 `{methodName}{Condition}{Expected}`，如 `entryBlocksWhenSopUnpublished`、`analyzeCalculatesControlLimitForXBarR`。

**fixture**：`@BeforeEach` 准备 BO/Entity（SPC 用参数化 `@ParameterizedTest`+`@MethodSource`），mock mapper/service，断言用 JUnit5 + AssertJ（SPC 算法）。

---

## 五、并行执行策略

```
时间线 ──────────────────────────────────────────────────────►

P0-7 Track A:
  [T7-1 pom基础设施]─►[T7-2 spc]─►[T7-3 qc]─►[T7-4 lt]─►[T7-5 eq]─►[T7-6 common]──┐
                                                                                    │
P0-3 Track B:                                                                       │
  [T3-1 DDL]─►[T3-2 Entity]─►[T3-3 规则引擎]─►[T3-4 entry/exit/skip]─►[T3-5 CRUD]─►[T3-6 前端]─►[T3-7 菜单]─►[T3-8 验证]
                                                                                                            │
汇合:                                                                                                        │
  ◄────────────────────────────────────[T7-7 pro 测试(含P0-3新行为)]◄─────────────────────────────────────────┘
  [T7-8 覆盖率验证]
```

- **Track A（P0-7 spc/qc/lt/eq/common）与 Track B（P0-3）完全并行**，文件无交叠
- **T7-7 pro 测试必须等 T3-4 完成**（测试 entry/exit/skip 新行为），是唯一串行点
- 两条线可由不同会话/agent 推进，最后汇合做 pro 测试 + 覆盖率验证

---

## 六、菜单 ID 段分配

| 模块 | ID 段 | 父菜单 | 备注 |
|------|-------|--------|------|
| P0-3 工序指引配置 | 4200 (C) | 4001 生产执行 | path=step-guide |
| P0-3 校验规则配置 | 4207 (C) | 4001 | path=step-check-rule |
| P0-3 功能按钮 | 4208-4215 (F) | - | 指引/规则 CRUD + entry/exit/skip 权限 |
| P0-1 物料拉动（后续） | 4216-4240 | 4006 | 避让，不变 |

> 4201-4206 已被 eq 子目录/工装M-dir(4205)/文档M-dir(4206) 占用，P0-3 避让。4200 + 4207-4215 共 10 ID，经济使用（2C + 8F）。

---

## 七、验收标准

### P0-3 功能验收

| 场景 | 验收标准 |
|------|---------|
| 进站拦截-SOP未发布 | SOP 状态非 PUBLISHED 时 entry 返回 passed=false，failures 含 SOP_VERSION |
| 进站拦截-工装不可用 | toolingId 状态非 IDLE 时 entry 拦截，failures 含 TOOLING_AVAILABLE |
| 进站拦截-首道未完成 | 非首道工序且上一工序未 COMPLETED 时 SERIAL_GATE 拦截 |
| 进站通过+推送 | 全部规则 PASS → step_status=IN_PROGRESS + entry_check_result=PASS + SSE 推送 SOP |
| 出站拦截-数量未达标 | good+defect+scrap ≠ planned 时 exit 拦截 |
| 出站拦截-异常未关闭 | 本工序有活跃安灯时 EXCEPTION_CLOSED 拦截 |
| 跳站防错 | 首道工序跳过被拒；无权限跳过被拒；跳过需 reason |
| 规则可配置 | 禁用某规则后该规则不执行 |
| SOP_LINK 校验 | 指引 guide_type=SOP_LINK 时 sop_id 须在 sop_process_binding 绑定同 process_id |

### P0-7 验收

| 场景 | 验收标准 |
|------|---------|
| 测试全绿 | `mvn test -pl ruoyi-modules/ruoyi-mes -am -DskipTests=false` EXIT 0 |
| 测试被执行 | surefire 报告显示 ≥19 测试类运行（修复 @Tag 过滤问题） |
| 覆盖率门槛 | JaCoCo check 通过（BUNDLE LINE ≥50%） |
| SPC 覆盖 | SpcControlChartServiceImpl LINE ≥80% |
| OOC 规则 | 4 条规则各有正/反用例 |

### 编译/lint

- `mvn compile -pl ruoyi-modules/ruoyi-mes -am` BUILD SUCCESS
- `eslint` 0 错误，vue-tsc 仅基线噪声

---

## 八、风险与应对

| 风险 | 等级 | 应对 |
|------|------|------|
| R1: surefire `<groups>` 覆盖不生效，测试仍不执行 | 高 | 参考 IM 模块 `combine.self="override"`；首次 `mvn test` 验证测试类被执行 |
| R2: P0-7 pro 测试与 P0-3 改造冲突 | 中 | T7-7 严格在 T3-4 后启动（见 §五） |
| R3: SPC 算法 mockStatic(RedisUtils) 失败 | 中 | spring-boot-starter-test 内置 inline mock maker 支持 mockStatic；参考 eq 测试 |
| R4: entry 校验上下文（toolingId/lotId）来源不清 | 中 | v1.0 由 entry API 入参传入（操作员扫码）；无入参则该规则 PASS 跳过 |
| R5: JaCoCo 50% 门槛初始过高导致构建失败 | 中 | 先设 0.50，逐步提；或首版设 0.40 过渡 |
| R6: WorkOrderReleaseConsumer 难纯单元测试 | 低 | 用 @SpringBootTest 或仅测 doProcess 核心逻辑（mock proWorkOrderService） |
| R7: 菜单 4200 已被占用 | 低 | 实施前 SELECT 核实；若占用改用 4207-4216 |

---

## 九、交付物清单

| 类型 | 数量 | 路径 |
|------|------|------|
| DDL（并入 ry-mes.sql） | 1 处 | `script/sql/ry-mes.sql` |
| Java 源文件（P0-3） | ~20 | `ruoyi-mes/.../com/nexus/mes/pro/{domain,service,controller}` |
| Java 测试文件（P0-7） | ~19 | `ruoyi-mes/src/test/java/com/nexus/mes/{pro,qc,spc,lt,eq,common}` |
| Vue 页面（P0-3） | 2 新 + 1 增强 | `plus-ui/src/views/mes/pro/{step-guide,step-check-rule,flow-card/detail}` |
| TS API（P0-3） | 2 新 + 1 增强 | `plus-ui/src/api/mes/pro/` |
| 菜单/字典 SQL | 2 | 落库 + 并入 ry-mes.sql |
| pom.xml | 1 改 | `ruoyi-modules/ruoyi-mes/pom.xml` |

---

**文档生成时间**: 2026-07-27
**编制者**: AI Agent
**审核状态**: 待用户批准 -> 进入执行
