# F2 固定资产 FA 实施规格

> **文档编号**: NexusERP-SPEC-2026-006-F2
> **创建日期**: 2026-08-01
> **状态**: 设计待评审
> **所属计划**: [ERP 模块主文档](../NexusERP_ERP文档.md)（原 07-29 财务开发总计划已清理，当前仓库无原件）(财务核心 F2 阶段)
> **前置**: F0 GL 总账(凭证引擎/科目余额/期间状态机)、F1 AR/AP(Poster 模式)、F5 报表/关账(期间联动)。科目 1601 固定资产(统驭 FA)/1602 累计折旧已由 F0 seed 提供。
> **后置依赖方**: F5 资产负债表 `BS_FA_NET`(1601+1602)自动纳入固定资产净值;F5 现金流量表 `CF_FA_PAY`(1601 借方发生)自动归集购建支出。
> **核心约束**: **F0/F1/F5 代码零改动**——F2 仅新增 `fin.fa` 包与一个 `FaDepreciationPoster` 实现 `AccountingPoster` 接口;F0 预留的 `SOURCE_DEPRECIATION`/`RECON_FA` 常量(GlConstants.java:27,45)直接使用。

---

## 一、范围与目标

### 1.1 范围

`com.nexus.erp.fin.fa` 子包,落地固定资产全生命周期:

| 能力 | 交付 |
|---|---|
| 资产类别 | 类别定义折旧政策:费用科目/累计折旧科目/处置损益科目/折旧方法/预计使用年限/净残值率 |
| 资产卡片 | 新增/编辑/删除,购置、在建(CWIP)、状态(IN_USE/CWIP/DISPOSED),原值/净残值/使用年限 |
| 折旧引擎 | 按月计提:直线法(平均年限法)/双倍余额递减法/年数总和法,按资产卡片逐项计算月折旧额 |
| 折旧过账 | 按期间批量计提,生成 GL 凭证(借费用科目/贷累计折旧 1602),复用 `createAndPost` 幂等 |
| 资产处置 | 出售/报废:计算净值与处置损益,生成处置凭证(借银行/现金、累计折旧转出、贷固定资产,差额入处置损益科目) |
| 资产变动 | 原值调整、状态变更(转固 CWIP→IN_USE),联动后续折旧 |
| 台账与折旧报表 | 资产台账、折旧计划表、已计提/未计提汇总 |

### 1.2 不含(后置)

- 资产盘点、减值计提 -> 后续
- 折旧方法的年中切换(变更折旧政策)-> 后续
- 批量导入资产卡片(Excel)-> 后续(FastExcel 已具,可后补)

### 1.3 目标

使财务人员可完成固定资产"建卡 → 月度计提 → 处置"闭环,折旧/处置自动生成 GL 凭证,台账与 GL 余额勾稽一致(与 F5 报表联动)。

---

## 二、模式参考(grounding)

| 关注点 | 参考源 | 关键证据 |
|---|---|---|
| Poster 模式 | `ReceiptPoster`(`getAccountId(code)` 按编码查科目 + `createAndPost`) | `ReceiptPoster.java:37-118` |
| 幂等过账 | `IGlVoucherService.createAndPost`(同 source+sourceId 返回既有) | `IGlVoucherService.java:106` |
| 来源常量 | `SOURCE_DEPRECIATION`/`RECON_FA` F0 已预留 | `GlConstants.java:27,45` |
| 期间状态机 | `IGlPeriodService`(OPEN→CLOSED→LOCKED;closed 期间不可过账) | `IGlPeriodService.java` |
| 期间查询 | `GlPeriodMapper` 按 fiscal_year/period_no 查期间列表(报表取数用) | `ReportDataServiceImpl.java:223-233` |
| 乐观锁坑 | `setVersion(0)` 初始化;`setSql` 原子累加 | 见 [[wm-stock-version-optimistic-lock]] |
| 幂等键段位 | F5 月结 `sourceId = periodId*100+N`(N=1 结转/2 重估/3 年结);F2 用 `periodId*100+N`(N=1 折旧批次/2 处置) | `FinClosingServiceImpl` |
| 字典/菜单段位 | F0 `6005xx`/`6100-6117`;F1 `6006xx`/`6120-6129`;F5 `6007xx`/`6130-6160` | F0/F1/F5 dict-menu SQL |
| 通用字段 | 全部表带 `tenant_id`/审计字段/`version`/`del_flag` | 既有表结构 |

> **菜单段位**:F2 用 `6161-6180`(避让 F0 6100-6117 / F1 6120-6129 / F5 6130-6160 / NCR 4241-4260 / 条码 26xx)。
> **字典段位**:F2 用 `6008xx`(避让 F0 6005xx / F1 6006xx / F5 6007xx / 发票 6004xx)。

---

## 三、数据模型(DDL)

> 全部落 `ry-erp` 库。增量脚本 `script/sql/update/2026-08-01-f2-fixed-assets.sql`,权威 schema 并入 `script/sql/ry-erp.sql`。

### 3.1 fa_asset_category 资产类别

```sql
DROP TABLE IF EXISTS `fa_asset_category`;
CREATE TABLE `fa_asset_category` (
  `category_id` bigint(20) NOT NULL COMMENT '类别ID',
  `tenant_id` varchar(20) NULL DEFAULT '000000',
  `category_name` varchar(100) NOT NULL COMMENT '类别名称(如 房屋建筑/机器设备/办公设备)',
  `category_code` varchar(30) NULL DEFAULT NULL COMMENT '类别编码',
  `depreciation_method` varchar(20) NOT NULL DEFAULT 'STRAIGHT_LINE' COMMENT '折旧方法 STRAIGHT_LINE-直线 DOUBLE_DECLINING-双倍余额 SUM_OF_YEARS-年数总和 MANUAL-手工',
  `useful_life` int(11) NOT NULL DEFAULT 10 COMMENT '预计使用年限(月)',
  `salvage_value_rate` decimal(10,4) NULL DEFAULT 0.0000 COMMENT '净残值率(0-1,默认0)',
  `debit_account_id` bigint(20) NULL DEFAULT NULL COMMENT '折旧费用科目ID(借:管理费用6602/制造费用等)',
  `credit_account_id` bigint(20) NULL DEFAULT NULL COMMENT '累计折旧科目ID(贷,默认1602)',
  `disposal_loss_account_id` bigint(20) NULL DEFAULT NULL COMMENT '处置损失科目ID(借,如 6711 营业外支出)',
  `disposal_gain_account_id` bigint(20) NULL DEFAULT NULL COMMENT '处置收益科目ID(贷,如 6301 营业外收入)',
  `status` char(1) NULL DEFAULT '0' COMMENT '状态 0-正常 1-停用',
  `sort_order` int(11) NULL DEFAULT 0,
  `version` int(11) NULL DEFAULT 0,
  `create_dept` bigint(20) NULL DEFAULT NULL, `create_by` bigint(20) NULL DEFAULT NULL, `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL, `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0',
  PRIMARY KEY (`category_id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`category_code`, `tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '固定资产类别(折旧政策)' ROW_FORMAT = DYNAMIC;
```

### 3.2 fa_asset 资产卡片

```sql
DROP TABLE IF EXISTS `fa_asset`;
CREATE TABLE `fa_asset` (
  `asset_id` bigint(20) NOT NULL COMMENT '资产ID',
  `tenant_id` varchar(20) NULL DEFAULT '000000',
  `asset_no` varchar(30) NOT NULL COMMENT '资产编号(自动生成)',
  `asset_name` varchar(100) NOT NULL COMMENT '资产名称',
  `category_id` bigint(20) NOT NULL COMMENT '类别ID',
  `original_value` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '原值',
  `salvage_value` decimal(18,2) NULL DEFAULT 0.00 COMMENT '净残值(原值×残值率,可手工调)',
  `useful_life` int(11) NOT NULL DEFAULT 10 COMMENT '使用年限(月)',
  `depreciation_method` varchar(20) NULL DEFAULT NULL COMMENT '折旧方法(空=随类别)',
  `acquisition_date` date NULL DEFAULT NULL COMMENT '购置日期',
  `in_use_date` date NULL DEFAULT NULL COMMENT '启用日期(开始计提)',
  `location` varchar(100) NULL DEFAULT NULL COMMENT '存放地点',
  `status` varchar(20) NOT NULL DEFAULT 'IN_USE' COMMENT '状态 IN_USE-使用中 CWIP-在建 DISPOSED-已处置',
  `cumulative_depreciation` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '累计已计提折旧(随折旧过账更新)',
  `net_book_value` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '账面净值(原值-累计折旧)',
  `version` int(11) NULL DEFAULT 0,
  `create_dept` bigint(20) NULL DEFAULT NULL, `create_by` bigint(20) NULL DEFAULT NULL, `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL, `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0',
  PRIMARY KEY (`asset_id`) USING BTREE,
  UNIQUE INDEX `uk_asset_no`(`asset_no`, `tenant_id`) USING BTREE,
  INDEX `idx_category`(`category_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '固定资产卡片' ROW_FORMAT = DYNAMIC;
```

### 3.3 fa_depreciation 折旧计划/记录

```sql
DROP TABLE IF EXISTS `fa_depreciation`;
CREATE TABLE `fa_depreciation` (
  `depreciation_id` bigint(20) NOT NULL COMMENT '折旧ID',
  `tenant_id` varchar(20) NULL DEFAULT '000000',
  `asset_id` bigint(20) NOT NULL COMMENT '资产ID',
  `period_id` bigint(20) NOT NULL COMMENT '期间ID',
  `depreciation_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '本期折旧额',
  `cumulative_depreciation` decimal(18,2) NULL DEFAULT 0.00 COMMENT '计提后累计折旧',
  `status` varchar(20) NOT NULL DEFAULT 'PLANNED' COMMENT '状态 PLANNED-计划 DONE-已过账',
  `posted_voucher_id` bigint(20) NULL DEFAULT NULL COMMENT '过账凭证ID',
  `posted_voucher_no` varchar(50) NULL DEFAULT NULL COMMENT '过账凭证号',
  `version` int(11) NULL DEFAULT 0,
  `create_dept` bigint(20) NULL DEFAULT NULL, `create_by` bigint(20) NULL DEFAULT NULL, `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL, `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0',
  PRIMARY KEY (`depreciation_id`) USING BTREE,
  UNIQUE INDEX `uk_asset_period`(`asset_id`, `period_id`, `tenant_id`) USING BTREE,
  INDEX `idx_period`(`period_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '固定资产折旧记录' ROW_FORMAT = DYNAMIC;
```

### 3.4 fa_disposal 资产处置

```sql
DROP TABLE IF EXISTS `fa_disposal`;
CREATE TABLE `fa_disposal` (
  `disposal_id` bigint(20) NOT NULL COMMENT '处置ID',
  `tenant_id` varchar(20) NULL DEFAULT '000000',
  `asset_id` bigint(20) NOT NULL COMMENT '资产ID',
  `disposal_type` varchar(20) NOT NULL COMMENT '类型 SALE-出售 SCRAP-报废',
  `disposal_date` date NOT NULL COMMENT '处置日期',
  `disposal_price` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '处置收入(售价/残值回收)',
  `net_book_value` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '处置时账面净值',
  `gain_loss` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '处置损益(正=收益 负=损失)',
  `posted_voucher_id` bigint(20) NULL DEFAULT NULL COMMENT '过账凭证ID',
  `posted_voucher_no` varchar(50) NULL DEFAULT NULL COMMENT '过账凭证号',
  `status` varchar(20) NOT NULL DEFAULT 'POSTED' COMMENT '状态 POSTED-已过账',
  `remark` varchar(500) NULL DEFAULT NULL COMMENT '备注',
  `version` int(11) NULL DEFAULT 0,
  `create_dept` bigint(20) NULL DEFAULT NULL, `create_by` bigint(20) NULL DEFAULT NULL, `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL, `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NULL DEFAULT '0',
  PRIMARY KEY (`disposal_id`) USING BTREE,
  INDEX `idx_asset`(`asset_id`) USING BTREE,
  INDEX `idx_date`(`disposal_date`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '固定资产处置记录' ROW_FORMAT = DYNAMIC;
```

---

## 四、核心设计

### 4.1 折旧方法

| 方法 | 月折旧额公式(按卡片,年限月 `n`、原值 `o`、残值 `s`) |
|---|---|
| STRAIGHT_LINE 直线 | `(o - s) / n`,每月恒定 |
| DOUBLE_DECLINING 双倍余额递减 | 当期月折旧 = `期初净值 × 2/n`;**后 1/3 年限改为直线摊完**(确保残值):若已计提月数 ≥ n×2/3,月折旧 = `(净值 - s) / 剩余月数` |
| SUM_OF_YEARS 年数总和 | 月折旧 = `(o - s) × 剩余月数 / (n×(n+1)/2)`(分母为全年限月总和) |
| MANUAL 手工 | 不自动计提,手工录入折旧记录 |

- **计提范围**:资产状态 `IN_USE`、启用日期 ≤ 期间末日、本期未产生折旧记录(唯一键 asset_id+period_id)。
- **最后期限约束**:任何方法月折旧 = min(公式值, 本期期初净值 - s)。
- **净残值** = 原值 × 类别残值率(卡片可覆盖)。

### 4.2 折旧批量过账(期间幂等)

- 接口 `POST /erp/fin/fa/depreciation/run`,参数 `periodId`。
- 流程:校验期间存在且未 CLOSED → 查询本期应计提资产(IN_USE 且启用日期 ≤ 期末) → 逐资产计算 → 写入 `fa_depreciation`(PLANNED)→ **合并一张凭证过账**(借:各资产费用科目汇总 / 贷:1602 累计折旧)→ 凭证 `voucherSource = DEPRECIATION`、`sourceId = periodId*100 + 1`(幂等键,同期间重跑返回既有凭证)→ 回填每行 `status=DONE`、`posted_voucher_id`、`cumulative_depreciation`,并累加 `fa_asset.cumulative_depreciation`(乐观锁 `setSql`)。
- **期间状态门禁**:期间 `CLOSED/LOCKED` 抛 ServiceException("期间已关账,不能计提折旧")。
- **手工法卡片**:不入自动计提清单。

### 4.3 处置过账(单资产)

- 接口 `POST /erp/fin/fa/disposal`,资产处置后自动过账:
  - 借:累计折旧 1602(累计已提)、借/贷差额:处置损益科目;贷:固定资产 1601(原值);借:银行存款 1002(售价,出售时)。
  - 出售 SALE:借 1002 售价 + 借 1602 累计折旧 + 借(或贷)处置损失/收益科目 = 贷 1601 原值。
  - 报废 SCRAP:同,无售价(售价 0)。
  - 处置损益 = 售价 + 累计折旧 - 原值(正=收益入 6301,负=损失入 6711)。
- 凭证 `voucherSource = DEPRECIATION`(复用 F0 预留常量;处置也是折旧域)、`sourceId = periodId*100 + 2`(幂等键),行摘要含资产编号/名称。
- 处置后资产状态 `DISPOSED`,记录 `posted_voucher_id`。
- **期间校验**:处置日期所在期间须 OPEN。

### 4.4 资产变动

- 原值调整:更新 `original_value`/`net_book_value`,后续折旧按新原值(直线法:剩余年限重新摊)。
- 转固:状态 CWIP→IN_USE,设 `in_use_date`(启用当月开始计提)。

### 4.5 与 F5 联动

- 折旧凭证入账后,F5 资产负债表 `BS_FA_NET`(1601+1602)与现金流量表 `CF_FA_PAY`(1601 借方发生)自动反映,无需 F5 改动。
- 月结向导(费用分摊→…→折旧→…)中"折旧"一步对应本模块的期间计提(F5 月结流程编排后续增强,本期提供独立 `run` 接口)。

---

## 五、Controller 签名

`/erp/fin/fa/*`,全部 `@DS("erp")` + `@SaCheckPermission("fin:fa:*")`:

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/erp/fin/fa/category/list` | 类别分页(含全部,下拉用) |
| GET | `/erp/fin/fa/category/{id}` | 类别详情 |
| POST | `/erp/fin/fa/category` | 新增类别 |
| PUT | `/erp/fin/fa/category` | 修改类别 |
| DELETE | `/erp/fin/fa/category/{ids}` | 删除类别(有卡片禁止删) |
| GET | `/erp/fin/fa/asset/list` | 资产分页(条件:类别/状态/名称关键字) |
| GET | `/erp/fin/fa/asset/{id}` | 资产详情(含类别名) |
| POST | `/erp/fin/fa/asset` | 新增卡片(自动生成 asset_no,如 FA-2026-0001) |
| PUT | `/erp/fin/fa/asset` | 修改卡片(未计提前可改) |
| DELETE | `/erp/fin/fa/asset/{ids}` | 删除卡片(已计提/已处置禁止) |
| PUT | `/erp/fin/fa/asset/adjustValue` | 原值调整 |
| PUT | `/erp/fin/fa/asset/activate` | 转固(CWIP→IN_USE) |
| GET | `/erp/fin/fa/depreciation/list` | 折旧记录分页(按资产/期间) |
| GET | `/erp/fin/fa/depreciation/plan` | 折旧计划预览(periodId:应计提明细+合计) |
| POST | `/erp/fin/fa/depreciation/run` | 按期间计提并过账(幂等) |
| GET | `/erp/fin/fa/disposal/list` | 处置记录分页(按资产) |
| POST | `/erp/fin/fa/disposal` | 处置资产并过账 |
| GET | `/erp/fin/fa/asset/report` | 资产台账(按期间:原值/累计折旧/净值汇总) |

---

## 六、验收标准

1. 资产类别 CRUD 正常(含折旧政策),删除被引用类别被拒。
2. 新建卡片自动编号;原值/残值/年限正确,净值=原值。
3. 按期间计提:直线法月折旧精确;双倍余额递减法在 2/3 年限后转直线;年数总和法递减。
4. 计提后自动生成 GL 凭证(借费用/贷 1602),凭证号回填折旧记录;台账累计折旧 = GL 1602 余额。
5. 同期间重复计提幂等:不生成第二张凭证、不重复累加。
6. 期间已关账时计提被拒。
7. 处置(出售/报废):损益计算正确(售价+累计折旧-原值),凭证借 1002/1602、贷 1601、差额入 6301/6711;资产状态 DISPOSED。
8. F5 资产负债表固定资产净值 = 1601 余额 - 1602 余额;现金流量表购建固定资产付现 = 1601 借方发生(自动联动)。

---

## 七、简化与后续

- 简化:不做折旧计划表的分期滚动视图(只做"计划预览"一次性);不做资产盘点/减值;不做 Excel 导入(后续 FastExcel 补充)。
- 后续:折旧方法变更历史、资产条码/RFID 关联、与 MES 设备档案(EqEquipment)对接(同物双卡问题后置决策)。
