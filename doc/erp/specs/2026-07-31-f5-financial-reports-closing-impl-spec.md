# F5 财务报表与期末关账 实施规格

> **文档编号**: NexusERP-SPEC-2026-005-F5
> **创建日期**: 2026-07-31
> **状态**: 设计待评审
> **所属计划**: [ERP 模块主文档](../NexusERP_ERP文档.md)（原 07-29 财务开发总计划已清理，当前仓库无原件）(财务核心 F5 阶段)
> **前置**: F0 GL 总账(凭证引擎/科目余额/期间)、F1 AR/AP(发票/收付款过账数据已入 GL)。F5 报表只读 GL,不依赖 F2/F3 的业务凭证(缺业务凭证的科目自然为零)。
> **后置依赖方**: 无(F5 是报表与关账终点;F6 对账、F7 电票与其无强依赖,可并行)
> **核心约束**: **F0/F1 零改动**——不修改 GlVoucherService/GlAccountBalanceService/GlPeriodService 代码。F5 通过新增 `fin.report` 包 + `GlAccountBalanceService.carryForward` 增强(新增接口方法,不改动既有方法)与 F0 衔接。

---

## 一、范围与目标

### 1.1 范围

`com.nexus.erp.fin.report` 子包,落地财务报表与期末关账:

| 能力 | 交付 |
|---|---|
| 报表取数引擎 | 按期间读取 GL 余额 + 科目类型/报表项目映射,统一取数服务(试算/三大报表共用) |
| 资产负债表 | 资产/负债/权益三栏,期末余额 = 期初 + 本期变动(读 `gl_account_balance`) |
| 利润表 | 本期/本年累计:收入 - 成本 - 费用 = 净利润(读本期发生 + 年初至今发生) |
| 现金流量表 | **基础版**:直接法,本期/本年累计,以"销售商品收现/购买商品付现/支付税费/支付工资/购建固定资产"等核心项目为限,取数走科目区间映射(间接法、逐单归集后置) |
| 期末损益结转 | 损益类科目(INCOME/EXPENSE)余额 → 本年利润 4103,生成结转凭证(复用 `createAndPost` 幂等) |
| 外币期末重估 | 外币科目按期末汇率重估,汇兑损益入 6603,生成重估凭证(幂等,按期间+币种+科目) |
| 月结流程向导 | 步骤编排:试算校验 → 损益结转 → 外币重估 → 报表快照 → 期间关账;支持逐步骤执行与整体执行 |
| 年结 | 本年利润 4103 → 未分配利润 4104;资产/负债/权益科目余额结转为下年期初(`carryForward`) |
| 报表快照 | 关账时冻结当期报表数据(可选,保证结账后报表与余额一致) |

### 1.2 不含(后置)

- 现金流量表**间接法**与逐单归集(存货/应收应付变动分析)-> 后续增强
- 合并报表、多币种分币种报表 -> 后置
- 报表项目自定义拖拽编排 -> 后续增强(先做项目-科目映射配置表)
- 预算对比列(F4 预算执行报表负责)-> F4

### 1.3 目标

在 F0/F1 数据基础上补齐法定报表与期末流程,使财务人员可完成"月结"闭环:校验 → 结转 → 重估 → 出表 → 关账 → 年结。

---

## 二、模式参考(grounding)

| 关注点 | 参考源 | 关键证据 |
|---|---|---|
| 报表 Controller | `GlReportController`(`/erp/fin/gl/report`)+ `@DS("erp")`+`R<List<T>>` | `GlReportController.java:20-36` |
| 试算取数 | `IGlAccountBalanceService.trialBalance`(Java 层聚合,无 XML) | `IGlAccountBalanceService.java:42` |
| 业务自动制单 | `IGlVoucherService.createAndPost`(幂等:同 source+sourceId 返回既有) | `IGlVoucherService.java:99-106` |
| Poster 模式 | `SoInvoicePoster`(`getAccountId(code)` 按编码查科目) | `SoInvoicePoster.java:37-82` |
| 期间状态机 | `GlPeriodService`(OPEN→CLOSED→LOCKED,closePeriod 校验无草稿) | `IGlPeriodService.java:28-83` |
| 异步租户恢复 | `GlPostingConsumer`/`PayReceivedConsumer`(`TenantHelper.dynamic`) | 见 [[ai-async-tenant-context]] |
| 字典/菜单段位 | F0 `6005xx`/`6100-6117`;F1 `6006xx`/`6120-6129` | F0/F1 dict-menu SQL |
| 乐观锁坑 | `setVersion(0)` 初始化;`setSql` 原子累加 | 见 [[wm-stock-version-optimistic-lock]] |
| 会计期间 `gl_period` | `fiscal_year`/`period_no`/`start_date`/`end_date`/`status` | `GlPeriod.java:28-56` |

> **菜单段位**:F5 用 `6130-6160`(避让 F0 6100-6117 / F1 6120-6129 / NCR 4241-4260 / 条码 26xx)。
> **字典段位**:F5 用 `6007xx`(避让 F0 6005xx / F1 6006xx / 发票 6004xx / GL 6005xx)。

---

## 三、数据模型(DDL)

> 全部落 `ry-erp` 库,带 `tenant_id`/审计字段/`version` 乐观锁/`del_flag`。增量脚本 `script/sql/update/2026-07-31-f5-report-closing.sql`,权威 schema 并入 `script/sql/ry-erp.sql`。F0 seed 追加科目/报表项目映射/期间月结字段走同一脚本。

### 3.1 科目表 seed 追加(F5 结转/报表所需,账务已缺)

```sql
-- 权益类:未分配利润(年结目标科目)
INSERT IGNORE INTO `gl_account`(`account_id`,`tenant_id`,`account_code`,`account_name`,`account_type`,`balance_dir`,`parent_id`,`level_num`,`path_ids`,`path_names`,`is_leaf`,`is_recon`,`recon_type`,`is_open_item`,`status`,`sort_order`,`version`,`create_time`) VALUES
(6005032,'000000','4104','未分配利润','EQUITY','CREDIT',0,1,'6005032','未分配利润','1','0',NULL,'0','0',32,0,NOW());
-- 损益类(结转/重估/报表需用,按准则新增一级科目)
(6005054,'000000','6603','财务费用','EXPENSE','DEBIT',0,1,'6005054','财务费用','1','0',NULL,'0','0',54,0,NOW()),
(6005055,'000000','6603.01','财务费用-利息收入','EXPENSE','DEBIT',6005054,2,'6005054,6005055','财务费用,财务费用-利息收入','1','0',NULL,'0','0',1,0,NOW()),
(6005056,'000000','6603.02','财务费用-汇兑损益','EXPENSE','DEBIT',6005054,2,'6005054,6005056','财务费用,财务费用-汇兑损益','1','0',NULL,'0','0',2,0,NOW()),
(6005057,'000000','6403','税金及附加','EXPENSE','DEBIT',0,1,'6005057','税金及附加','1','0',NULL,'0','0',57,0,NOW()),
(6005058,'000000','6711','营业外支出','EXPENSE','DEBIT',0,1,'6005058','营业外支出','1','0',NULL,'0','0',58,0,NOW()),
(6005059,'000000','6301','营业外收入','INCOME','CREDIT',0,1,'6005059','营业外收入','1','0',NULL,'0','0',59,0,NOW());
```

> 追加采用 `INSERT IGNORE` + `6005xx` 段,不重跑旧 seed。财务费用下设利息/汇兑子科目以承接重估凭证(重估固定过 6603.02)。

### 3.2 report_item(报表项目-科目映射)

报表取数引擎的配置基座:每个报表项目绑定一组科目(按 `account_code` 前缀/精确),支持借贷方向与符号。

```sql
DROP TABLE IF EXISTS `report_item`;
CREATE TABLE `report_item` (
  `item_id` bigint(20) NOT NULL COMMENT '项目ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `report_type` varchar(20) NOT NULL COMMENT '报表类型 BALANCE-资产负债表 INCOME-利润表 CASH-现金流量表',
  `item_code` varchar(30) NOT NULL COMMENT '项目编码(如 BS_ASSET_TOTAL_CURRENT)',
  `item_name` varchar(100) NOT NULL COMMENT '项目名称(如 流动资产合计)',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '显示顺序',
  `parent_item_id` bigint(20) NULL DEFAULT 0 COMMENT '父项目ID(分组行,0=顶级)',
  `is_summary` char(1) NULL DEFAULT '0' COMMENT '是否合计行 0-否 1-是(由子项汇总)',
  `account_code_match` varchar(500) NULL DEFAULT NULL COMMENT '科目编码匹配(逗号分隔;支持前缀如 1001,1002 或 100% 结尾通配)',
  `sign` varchar(5) NULL DEFAULT '1' COMMENT '取值符号 1-正 -1-负(余额方向差异补偿)',
  `status` char(1) NULL DEFAULT '0' COMMENT '状态 0-正常 1-停用',
  `version` int(11) NULL DEFAULT 0,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0',
  PRIMARY KEY (`item_id`) USING BTREE,
  UNIQUE INDEX `uk_item`(`report_type`, `item_code`, `tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '报表项目-科目映射' ROW_FORMAT = DYNAMIC;
```

> 默认映射 seed 内置(资产负债表/利润表/现金流量表-直接法),实施时按 §4 取数规则逐项核对。`is_summary=1` 的行不取科目,由引擎汇总子项目。**不新增** `report_item_line` 子表:科目匹配规则直接在 `account_code_match` 字段,避免过度设计。

### 3.3 gl_period 追加字段(月结状态)

```sql
ALTER TABLE `gl_period`
  ADD COLUMN `closing_step` varchar(30) NULL DEFAULT NULL COMMENT '月结进度(CLOSING_PENDING/CLOSING_REPORT/CLOSING_CARRY/CLOSING_DONE,CLOSED 时冗余)',
  ADD COLUMN `closed_voucher_count` int(11) NULL DEFAULT 0 COMMENT '本期凭证数(结账时快照)',
  ADD COLUMN `closing_voucher_id` bigint(20) NULL DEFAULT NULL COMMENT '损益结转凭证ID(幂等回溯)',
  ADD COLUMN `revaluation_voucher_id` bigint(20) NULL DEFAULT NULL COMMENT '重估凭证ID(幂等回溯,多币种合并一张)';
```

> 幂等回溯优先:**不依赖新字段**——结转/重估凭证本身带 `voucher_source`+`source_id`,`createAndPost` 已按 (source, sourceId) 去重;新字段仅作结账进度展示与向导状态。ALGORITHM=INPLACE,存量数据兼容。

### 3.4 月结/年结执行记录(可选日志表)

```sql
DROP TABLE IF EXISTS `fin_close_log`;
CREATE TABLE `fin_close_log` (
  `log_id` bigint(20) NOT NULL COMMENT '日志ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000',
  `period_id` bigint(20) NOT NULL COMMENT '期间ID',
  `close_type` varchar(20) NOT NULL COMMENT '类型 MONTHLY-月结 YEARLY-年结',
  `step` varchar(30) NOT NULL COMMENT '步骤 TRIAL_CHECK/CARRY_PL/PREPARE_REPORT/REVALUATION/CLOSE_PERIOD/CARRY_FORWARD',
  `status` varchar(20) NOT NULL COMMENT '状态 SUCCESS/FAILED',
  `message` varchar(500) NULL DEFAULT NULL COMMENT '结果/错误信息',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`) USING BTREE,
  INDEX `idx_period`(`period_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '期末关账执行日志' ROW_FORMAT = DYNAMIC;
```

> 向导每一步落一条日志,失败可定位重跑步骤。

---

## 四、报表取数规则(核心设计)

### 4.1 统一取数服务 `ReportDataService`

| 方法 | 说明 |
|---|---|
| `List<ReportLineVo> balanceSheet(periodId, includeZero)` | 资产负债表:读 `gl_account_balance` 期末余额,按 `report_item(BALANCE)` 映射 |
| `List<ReportLineVo> incomeStatement(periodId, yearToDate)` | 利润表:本期发生(period)或本年累计(1月~本期 sum),按 `report_item(INCOME)` |
| `List<ReportLineVo> cashFlow(periodId, yearToDate)` | 现金流量表直接法:科目发生额归集(本期/本年),按 `report_item(CASH)` |
| `List<ReportLineVo> closingSnapshot(periodId)` | 结账快照:当期试算数据落库(`fin_report_snapshot`) |

**取数逻辑**(全部 Java 层聚合,不写 XML——与 `trialBalance` 一致):

```
balanceSheet: 期末余额 = closing_debit - closing_credit(按 balance_dir 归一)
  → 资产类:借方向科目取 closing_debit;贷方向科目(累计折旧)取 -closing_credit
  → 负债/权益:取 closing_credit - closing_debit
incomeStatement: 本期发生 = SUM(period_debit/period_credit);
  收入类(CREDIT 方向):本期额 = period_credit - period_debit
  费用类(DEBIT 方向):本期额 = period_debit - period_credit
  本年累计 = 1..periodNo 各期发生合计(读取该年度全部已生成期间的 balance,逐期聚合)
cashFlow(直接法,科目区间映射):
  销售商品收现 ≈ 银行存款/库存现金 贷方发生(与收入挂勾的收现科目区间)
  购买商品付现 ≈ 银行存款/库存现金 借方发生(采购付现科目区间)
  支付税费 = 应交税费 借方发生
  支付工资 = 应付职工薪酬 借方发生
  购建固定资产 = 固定资产 借方发生
  → 精确归集(按供应商/客户逐单)后置,本期先按科目区间汇总
```

### 4.2 报表 VO

```java
public class ReportLineVo {
    private String reportType;      // BALANCE/INCOME/CASH
    private String itemCode;        // 项目编码
    private String itemName;        // 项目名称
    private Long parentItemId;      // 父项目
    private Boolean isSummary;      // 是否合计行
    private Integer sortOrder;
    private BigDecimal amount;      // 本期/期末金额(符号已按 sign 处理)
    private BigDecimal yearAmount;  // 本年累计(利润表/现金流量表;资产负债表为 null)
}
```

### 4.3 映射 seed(节选,实施时全量)

```sql
-- 资产负债表(节选)
(7000001,'000000','BALANCE','BS_MONETARY','货币资金',10,0,'0','1001,1002','1','0',0,NOW()),
(7000002,'000000','BALANCE','BS_AR','应收账款',20,0,'0','1122','1','0',0,NOW()),
(7000003,'000000','BALANCE','BS_PREPAY','预付账款',30,0,'0','1123','1','0',0,NOW()),
(7000004,'000000','BALANCE','BS_INVENTORY','存货',40,0,'0','1401,1403,1405','1','0',0,NOW()),
(7000005,'000000','BALANCE','BS_FA','固定资产净值',50,0,'0','1601,1602','-1','0',0,NOW()), -- 净值=原值-累计折旧
(7000010,'000000','BALANCE','BS_AP','应付账款',60,0,'0','2202','1','0',0,NOW()),
(7000011,'000000','BALANCE','BS_ADVANCE_RECEIVED','预收账款',70,0,'0','2203','1','0',0,NOW()),
(7000012,'000000','BALANCE','BS_TAX_PAYABLE','应交税费',80,0,'0','2221','1','0',0,NOW()),
(7000020,'000000','BALANCE','BS_EQUITY','实收资本+权益',90,0,'0','4001,4103,4104','1','0',0,NOW());
-- 利润表(节选)
(7000101,'000000','INCOME','IS_REVENUE','营业收入',10,0,'0','6001','1','0',0,NOW()),
(7000102,'000000','INCOME','IS_COST','营业成本',20,0,'0','6401','1','0',0,NOW()),
(7000103,'000000','INCOME','IS_TAX','税金及附加',30,0,'0','6403','1','0',0,NOW()),
(7000104,'000000','INCOME','IS_SELL_EXP','销售费用',40,0,'0','6601','1','0',0,NOW()),
(7000105,'000000','INCOME','IS_ADMIN_EXP','管理费用',50,0,'0','6602','1','0',0,NOW()),
(7000106,'000000','INCOME','IS_FIN_EXP','财务费用',60,0,'0','6603','1','0',0,NOW()),
(7000107,'000000','INCOME','IS_NET_PROFIT','净利润(合计行)',70,1,'0',NULL,'1','0',0,NOW());
-- 现金流量表(节选,直接法)
(7000201,'000000','CASH','CF_SALES_RECEIPT','销售商品、提供劳务收到的现金',10,0,'0','1002','1','0',0,NOW()),  -- 贷方(收现)
(7000202,'000000','CASH','CF_PURCHASE_PAY','购买商品、接受劳务支付的现金',20,0,'0','1002','-1','0',0,NOW()), -- 借方(付现)
(7000203,'000000','CASH','CF_TAX_PAY','支付的各项税费',30,0,'0','2221','1','0',0,NOW()),
(7000204,'000000','CASH','CF_SALARY_PAY','支付给职工的现金',40,0,'0','2211','1','0',0,NOW()),
(7000205,'000000','CASH','CF_FA_PAY','购建固定资产支付的现金',50,0,'0','1601','1','0',0,NOW());
```

> 现金流的收/付现方向:同一科目(银行存款)按"收现=贷方发生/付现=借方发生"双项归集——`account_code_match` 仅能表达科目区间,方向语义由 `sign` + 引擎内约定(CF 项目下 `sign=-1` 表示取借方发生,`sign=1` 表示取贷方发生)。实施时在引擎内以 report_type=CASH 特判该语义,映射 seed 中写注释。

---

## 五、损益结转与外币重估

### 5.1 损益结转(月结步骤)

**触发**:月结向导"损益结转"步骤(或独立接口)。

```
1. 校验:目标期间 OPEN(未关账);存在上一未结转期间则先结转(顺序执行)
2. 取数:该期间所有 INCOME/EXPENSE 科目余额 = closing_debit - closing_credit(净额)
3. 制单:createAndPost(VoucherBuildRequest)
   借:主营业务收入 6001 / 营业外收入 6301(余额>0 时借收入)
   贷:本年利润 4103(收入合计)
   借:本年利润 4103
   贷:主营业务成本 6401 / 销售费用 6601 / 管理费用 6602 / 财务费用 6603 / 税金及附加 6403 / 营业外支出 6711(费用余额>0 时贷费用)
   voucherSource=ADJUSTMENT(沿用 F0 常量 SOURCE_ADJUSTMENT),sourceId=periodId*100+1(幂等键),summary="期间损益结转 2026-07"
4. 凭证过账成功 → 损益类科目余额归零(period 发生额已结转至 4103)
5. 幂等:同期间重复执行 → createAndPost 命中 (ADJUSTMENT, sourceId) 返回既有凭证,不重复
```

> **损益类余额方向**:收入科目 balance_dir=CREDIT,净额=closing_credit-closing_debit(结转时借收入贷本年利润);费用科目 balance_dir=DEBIT,净额=closing_debit-closing_credit(结转时借本年利润贷费用)。红字(负)余额反向结转,同一模板兼容(净额<0 时借/贷互换)。

### 5.2 外币期末重估(月结步骤,可选执行)

**前置**:存在 `gl_currency_rate` 期末汇率(rate_date=期间最后一天或最近),否则跳过并提示。

```
1. 取数:该期间所有 currency≠CNY 且金额非零的外币科目余额行(按 account_id+currency 聚合原币 net)
   → 本币账面余额 = Σ(amount_currency×入账汇率),或直接取 gl_voucher_line 中 currency≠CNY 行的 debit/credit 净额
2. 重估:按期末汇率计算重估后本币值;差额 = 重估后 - 账面
3. 制单(差额≠0):
   借/贷:原外币科目(补差)
   借/贷:财务费用-汇兑损益 6603.02(反方向)
   voucherSource=ADJUSTMENT,sourceId=periodId*100+2(幂等键),summary="外币期末重估 {currency}"
4. 多币种差额并入一张凭证(多行),逐币种行摘要区分
```

> **简化**:F5 仅重估"外币科目本币余额与按期末汇率折算之差",不逐行 open_item 重估(未清项外币重估与核销联动的复杂场景后置,记录于"简化与后续")。`amount_currency` 双存储是 F0 已建基础,重估取数直接读凭证行。

### 5.3 期间结转 carryForward(年结)

```java
// IGlAccountBalanceService 新增方法(F0 接口预留,未实现)
/** 期末结转:某期间 closing -> 下期 opening(年结调用) */
void carryForward(Long fromPeriodId, Long toPeriodId);
```

**实现要点**(`GlAccountBalanceServiceImpl` 新增,不改既有方法):
```
1. 读 from 期间全部余额行(closing_debit/closing_credit 非零)
2. 目标期间 upsert:opening_debit += closing_debit;opening_credit += closing_credit;year_opening_* 同步
3. 幂等:重复调用不重复累加(以"to 期间 opening 已含 from 期末"为判定,或加 fromPeriodId 来源标记——实施时选定一种,推荐加 `source_period_id` 冗余列避免重跑风险)
```

> **年结顺序**:损益结转(4103 归集全年)→ 本年利润→未分配利润(借 4103 / 贷 4104,或反向)→ 资产/负债/权益余额 carryForward 到次年 1 月 opening → 12 月期间 LOCKED。

### 5.4 月结向导 `FinClosingService`

```java
public interface IFinClosingService {
    /** 试算校验:当期借贷合计相等 + 无 DRAFT 凭证 + 损益未结(提示) */
    Boolean trialCheck(Long periodId);
    /** 损益结转(幂等) */
    Long carryPnl(Long periodId);
    /** 外币重估(幂等,无汇率时跳过) */
    Long revaluate(Long periodId);
    /** 关账(调 GlPeriodService.closePeriod) */
    Boolean closePeriod(Long periodId);
    /** 月结整体执行:trialCheck→carryPnl→revaluate→closePeriod,逐步失败即中止并记日志 */
    Boolean monthlyClosing(Long periodId);
    /** 年结:12 月月结 → 结转本年利润 → 4103→4104 → carryForward(12→次年1) → 次年期间生成 */
    Boolean yearlyClosing(Integer fiscalYear);
    /** 关账步骤日志 */
    List<FinCloseLogVo> closeLogs(Long periodId);
}
```

> **事务边界**:每个步骤独立事务(日志单独提交),步骤间失败可重跑;`monthlyClosing` 不包大事务,靠幂等保证。

---

## 六、Controller 层

```java
// /erp/fin/report  (报表查询,只读)
@Validated @RequiredArgsConstructor @RestController
@DS("erp")
@RequestMapping("/erp/fin/report")
public class FinReportController extends BaseController {
    @SaCheckPermission("fin:report:list") @GetMapping("/balance")        // ?periodId=&includeZero=
    public R<List<ReportLineVo>> balance(...);
    @SaCheckPermission("fin:report:list") @GetMapping("/income")         // ?periodId=&yearToDate=
    public R<List<ReportLineVo>> income(...);
    @SaCheckPermission("fin:report:list") @GetMapping("/cashFlow")       // ?periodId=&yearToDate=
    public R<List<ReportLineVo>> cashFlow(...);
    @SaCheckPermission("fin:report:list") @GetMapping("/itemConfig")     // 报表项目映射配置查询
    public R<List<ReportItemVo>> itemConfig(@RequestParam String reportType);
    @SaCheckPermission("fin:report:edit") @PutMapping("/itemConfig")     // 映射维护
    public R<Void> updateItemConfig(@RequestBody ReportItemBo bo);
}

// /erp/fin/closing  (期末关账)
@Validated @RequiredArgsConstructor @RestController
@DS("erp")
@RequestMapping("/erp/fin/closing")
public class FinClosingController extends BaseController {
    @SaCheckPermission("fin:closing:list")  @GetMapping("/steps")        // 期间关账步骤状态(向导页)
    public R<ClosingStepVo> steps(@RequestParam Long periodId);
    @SaCheckPermission("fin:closing:carry") @PostMapping("/carryPnl")    // 损益结转
    public R<Void> carryPnl(@RequestBody @Valid ClosingRequestBo bo);
    @SaCheckPermission("fin:closing:reval") @PostMapping("/revaluate")   // 外币重估
    public R<Void> revaluate(@RequestBody @Valid ClosingRequestBo bo);
    @SaCheckPermission("fin:closing:close") @PostMapping("/close")       // 关账(整体执行)
    public R<Void> monthlyClosing(@RequestBody @Valid ClosingRequestBo bo);
    @SaCheckPermission("fin:closing:yearly") @PostMapping("/yearly")     // 年结
    public R<Void> yearlyClosing(@RequestBody @Valid ClosingYearlyBo bo);
    @SaCheckPermission("fin:closing:list")  @GetMapping("/logs")         // 关账日志
    public R<List<FinCloseLogVo>> logs(@RequestParam Long periodId);
}
```

> **权限前缀**:`fin:report:*`/`fin:closing:*`,菜单按钮见 §七。`ClosingRequestBo` 含 periodId;`ClosingYearlyBo` 含 fiscalYear。

---

## 七、字典与菜单 SQL

### 7.1 字典(`6007xx` 段)

```sql
-- 报表类型(report_type)
(600701,'000000','报表类型','fin_report_type',NOW());
(600711,'000000',1,'资产负债表','BALANCE','fin_report_type','N',NOW()),
(600712,'000000',2,'利润表','INCOME','fin_report_type','N',NOW()),
(600713,'000000',3,'现金流量表','CASH','fin_report_type','N',NOW());
-- 月结步骤(closing_step)
(600702,'000000','关账步骤','fin_closing_step',NOW());
(600721,'000000',1,'待执行','PENDING','fin_closing_step','Y',NOW()),
(600722,'000000',2,'试算校验','TRIAL_CHECK','fin_closing_step','N',NOW()),
(600723,'000000',3,'损益结转','CARRY_PL','fin_closing_step','N',NOW()),
(600724,'000000',4,'外币重估','REVALUATION','fin_closing_step','N',NOW()),
(600725,'000000',5,'报表快照','REPORT_SNAPSHOT','fin_closing_step','N',NOW()),
(600726,'000000',6,'关账完成','CLOSED','fin_closing_step','N',NOW());
```

> 执行后 `DEL sys_dict:fin_report_type sys_dict:fin_closing_step`(Redis stale 坑,见 [[mes-p0-overall-progress]])。

### 7.2 菜单(`6130-6160` 段)

```sql
-- 财务管理(6100) 下:财务报表目录(6130) + 期末关账(6131)
(6130,'财务报表',6100,3,'report',NULL,1,0,'M','0','0',NULL,'chart',103,1,NOW(),...),
(6131,'资产负债表',6130,1,'balance','erp/fin/report/balance',1,0,'C','0','0','fin:report:list','table',103,1,NOW(),...),
(6132,'利润表',6130,2,'income','erp/fin/report/income',1,0,'C','0','0','fin:report:list','table',103,1,NOW(),...),
(6133,'现金流量表',6130,3,'cashFlow','erp/fin/report/cashFlow',1,0,'C','0','0','fin:report:list','table',103,1,NOW(),...),
(6134,'报表项目配置',6130,4,'itemConfig','erp/fin/report/itemConfig',1,0,'C','0','0','fin:report:edit','settings',103,1,NOW(),...),
(6140,'期末关账',6100,4,'closing',NULL,1,0,'M','0','0',NULL,'lock',103,1,NOW(),...),
(6141,'月结向导',6140,1,'monthly','erp/fin/closing/monthly',1,0,'C','0','0','fin:closing:list','guide',103,1,NOW(),...),
(6142,'年结',6140,2,'yearly','erp/fin/closing/yearly',1,0,'C','0','0','fin:closing:yearly','calendar',103,1,NOW(),...);
-- 按钮:损益结转(6143)/外币重估(6144)/关账执行(6145)/关账日志(6146)
```

---

## 八、前端(plus-ui)

```
src/views/erp/fin/report/
  balance.vue        - 资产负债表(期间选择,资产/负债/权益三栏,合计校验)
  income.vue         - 利润表(本期/本年累计切换)
  cashFlow.vue       - 现金流量表(本期/本年累计)
  itemConfig.vue     - 报表项目映射配置(报表类型 tab,项目-科目维护)
src/views/erp/fin/closing/
  monthly.vue        - 月结向导(期间选择→步骤状态卡片→试算校验/结转/重估/关账逐步骤执行+日志)
  yearly.vue         - 年结(年度选择,说明与确认,执行日志)

src/api/erp/fin/report/index.ts    - balance/income/cashFlow/itemConfig
src/api/erp/fin/closing/index.ts   - steps/carryPnl/revaluate/close/yearly/logs
```

> 报表页三栏联动:资产负债表合计行须"资产总计=负债+权益"勾稽校验,不平衡标红提示(数据问题引导查凭证)。月结向导步骤卡片复用 F0 `gl_period` 状态展示(OPEN 未结/CLOSED 已结)。

---

## 九、关键技术决议

| # | 决议 | 理由 |
|---|---|---|
| 1 | **F0/F1 零改动**:新增 `fin.report`/`fin.closing` 包 + `IGlAccountBalanceService.carryForward` 新增方法 | 不动既有凭证引擎/余额/期间代码,报表只读,结转复用 createAndPost 幂等 |
| 2 | **报表取数读 `gl_account_balance`**(Java 聚合,不写 XML) | 与 trialBalance 同构;期末/期初/发生已落表,避免每次全量扫凭证行 |
| 3 | **报表项目-科目映射配置表 `report_item`** | 报表项目与科目松耦合,新增科目/调整映射免改代码(映射 seed 内置) |
| 4 | **结转/重估走 `createAndPost` + 幂等键**(ADJUSTMENT + sourceId=periodId*100+N) | 复用 F0 幂等/借贷平衡/期间校验/余额写入,零新增过账逻辑 |
| 5 | **损益结转方向**:收入借出/费用贷出,净额归集本年利润 4103 | 准则标准做法;红字负余额反向结转兼容 |
| 6 | **外币重估**:按凭证行原币净额×期末汇率差入 6603.02 | F0 双存储基础;open_item 逐行重估后置 |
| 7 | **现金流量表直接法科目区间归集**(基础版) | 先满足报表底线,逐单归集/间接法后置(记录于简化) |
| 8 | **月结步骤独立事务 + 日志表** | 步骤失败可重跑(幂等),日志可定位;不包大事务 |
| 9 | **年结**:4103→4104 + carryForward 资产/负债/权益 | 与准则年结流程一致;12 月 LOCKED 防改写 |
| 10 | **不新建凭证类型**:结转/重估均 ADJUSTMENT | F0 常量已含,无需扩展状态机 |

---

## 十、风险与坑

| 风险/坑 | 应对 |
|---|---|
| **结转/重估重复执行** | createAndPost (ADJUSTMENT, sourceId) 幂等;sourceId 固定推导(periodId*100+N),重跑命中既有凭证 |
| **损益结转后再录损益凭证** | 结转前 trialCheck 校验 + 向导顺序执行;关账后期间 CLOSED 不可过账(F0 已强制) |
| **carryForward 重跑重复累加** | 实施时在 gl_account_balance 加 `source_period_id`(或 `carried` 标记)防重;见 §5.3 |
| **报表勾稽不平** | balance 页合计校验标红;trialBalance 交叉验证;定位到余额行 |
| **外币重估汇率缺失** | `gl_currency_rate` 无期末汇率时跳过重估并日志提示,不阻塞月结 |
| **年结期间跨越** | 年结前校验 12 月已月结;次年期间 generateYearPeriods 先行 |
| **异步租户上下文** | 月结/结转走同步请求(向导人工触发),如未来异步化须 `TenantHelper.dynamic`(见 [[ai-async-tenant-context]]) |
| **字典 Redis stale** | 新字典 SQL 执行后 `DEL sys_dict:fin_*`(见 [[mes-p0-overall-progress]]) |
| **菜单段位冲突** | 6130-6160 避让已用段(6100-6117 F0/6120-6129 F1/4241-4260 NCR);实施前 grep sys_menu 复核 |
| **报表大期间全量扫描** | 余额表按 period_id+account_id 唯一索引,查询走索引;本年累计逐期聚合 ≤12 次 |

---

## 十一、验收标准与调用序列

### 11.1 验收标准

1. **科目 seed 追加**:4104/6603/6403/6301/6711 可查;`INSERT IGNORE` 重跑不报错。
2. **报表**:资产负债表按期间取数,资产=负债+权益(勾稽);利润表本期/本年累计与试算平衡表发生额交叉一致;现金流量表直接法项目有数。
3. **损益结转**:结转后损益类科目期末余额为 0;4103 余额=当期净利润;重复执行幂等(凭证不重复)。
4. **外币重估**:存在外币凭证+期末汇率时生成重估凭证(借/贷 6603.02);无汇率跳过;重复执行幂等。
5. **月结向导**:步骤状态正确流转;closePeriod 后期间 CLOSED,该期间不可再过账(回归 F0 校验);日志完整。
6. **年结**:4103→4104;次年 1 月 opening=上年底 closing(资产/负债/权益);12 月 LOCKED。
7. **编译**:`mvn -pl ruoyi-modules/ruoyi-erp -am compile` EXIT 0;前端 `npx eslint` 零新增。

### 11.2 调用序列(月结主链路)

1. `POST /erp/fin/closing/carryPnl {periodId}` → 损益结转凭证(ADJUSTMENT,幂等) → 损益科目归零
2. `POST /erp/fin/closing/revaluate {periodId}` → 外币重估凭证(有汇率时) → 6603.02 差额
3. `GET /erp/fin/report/income?periodId=&yearToDate=false` → 利润表本期
4. `GET /erp/fin/report/balance?periodId=` → 资产负债表(资产=负债+权益)
5. `POST /erp/fin/closing/close {periodId}` → 向导执行(校验→结转→重估→关账) → 期间 CLOSED
6. `GET /erp/fin/closing/logs?periodId=` → 步骤日志
7. `POST /erp/fin/closing/yearly {fiscalYear}` → 年结(4103→4104 + carryForward + 次年期间生成 + 12 月 LOCKED)

---

## 十二、文件清单

| 类型 | 文件 |
|---|---|
| SQL | `script/sql/update/2026-07-31-f5-report-closing.sql`(report_item/fin_close_log DDL + gl_period 追加字段 + 科目 seed 追加 + 报表映射 seed;并入 `script/sql/ry-erp.sql`) |
| SQL | `script/sql/update/2026-07-31-f5-report-dict-menu.sql`(字典 6007xx + 菜单 6130-6160,并入 `script/sql/ry-vue.sql`) |
| 实体 | `fin/report/domain/`:`ReportItem`/`FinCloseLog` + BO(`ReportItemBo`/`ClosingRequestBo`/`ClosingYearlyBo`)+ VO(`ReportLineVo`/`ReportItemVo`/`ClosingStepVo`/`FinCloseLogVo`) |
| Mapper | `fin/report/mapper/`:`ReportItemMapper`/`FinCloseLogMapper` |
| Service | `fin/report/service/`:`ReportDataService`(取数引擎)+ `IFinClosingService` + impl;`GlAccountBalanceServiceImpl` 新增 `carryForward` |
| Controller | `fin/report/controller/`:`FinReportController`/`FinClosingController` |
| 常量 | `fin/report/constant/ReportConstants`(reportType/step/幂等键 sourceId 偏移) |
| 前端 | `views/erp/fin/report/`(4 页)+ `views/erp/fin/closing/`(2 页)+ `api/erp/fin/report/index.ts` + `api/erp/fin/closing/index.ts` |

---

## 十三、简化与后续

| 项 | 说明 | 后续 |
|---|---|---|
| 现金流量表 | 直接法科目区间归集,未逐单归集 | 间接法 + 按供应商/客户逐单收付现归集 |
| 外币重估 | 仅重估科目本币余额差额,未逐 open_item | open_item 外币重估与核销联动 |
| carryForward 幂等 | 依赖 source_period_id 标记(实施时加列) | 如不需要可退化"重复调用仅首次生效" |
| 报表快照 | 关账时快照可选(默认不做,报表实时取数) | 结账后报表锁定可选 |
| 财务费用科目 | 6603.01 利息/6603.02 汇兑,按需扩展 | — |
| 月结自动调度 | 向导人工触发 | 可挂 SnailJob 定时执行(带租户恢复) |

---

## 十四、与 F0-F7 衔接

| 阶段 | 衔接 |
|---|---|
| F0 | 只读 gl_account_balance/gl_period/gl_voucher_line;`carryForward` 为 F0 接口预留新增方法 |
| F1 | 报表取数天然含 F1 发票/收付款过账数据;未清项不参与报表(余额口径一致) |
| F2/F3 | 缺业务凭证时相关科目为零,报表不报错;F2 折旧/F3 成本过账后报表自动含数 |
| F4 | 预算执行报表另起(可复用 ReportDataService 取实际数) |
| F6/F7 | 无强依赖,可并行 |

---

## 十五、下一步

1. **评审本 spec**,确认报表项目映射、损益结转方向、幂等键设计、年结顺序
2. 落地 SQL(表 DDL + 科目追加 + 映射 seed + 字典菜单)到 `script/sql/update/`,执行本地 ry-erp/ry-vue
3. 实现 `fin.report` 包(实体/BO/VO/Mapper/Service/Controller/常量)+ `carryForward`,`mvn -pl ruoyi-modules/ruoyi-erp -am compile` 通过
4. 前端 6 页面 + 2 api,eslint 零新增
5. 按 11.2 调用序列端到端验证(F0 造数:手工凭证 + F1 发票过账数据)
6. F5 闭合后按计划第十二节顺序推进 **F2 固定资产 / F3-a 存货成本**(DepreciationPoster/CostPoster 复用同一 SPI)
