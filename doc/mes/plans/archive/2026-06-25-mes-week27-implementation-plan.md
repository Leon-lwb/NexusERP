# MES Phase 3 Week 27 开发实施计划 — 数据采集 + SPC 模块（采集点 + 数据记录 + 控制图 + OOC 判定，Phase 3 收尾）

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（任务级 checkbox 跟踪）。Steps 用 `- [ ]` 标记。

> **实施日期**: 2026-06-25
> **阶段**: MES Phase 3 收尾 — 数据采集 + SPC（对应主文档 Week 31-32 任务）
> **状态**: 待 Claude 自审 → 待用户审查

---

## 一、Context（背景与目标）

### 1.1 为什么做这一周

Phase 3 质量管理（Week 25）与设备管理（Week 26）已完成。按主计划《NexusERP_MES文档.md》§10.3 / §15.3，Phase 3 最后一块为 Week 31-32 数据采集 + SPC（4 人天）。实际进度领先主计划约 5 周，故 **Week 27 = 数据采集 + SPC 模块**，本周完成 Phase 3 全部闭合。对应主文档 §2.4（dc_collection_point/dc_data_record/spc_control_chart/spc_data_point 四表 DDL）、§8.4（SPC 设计 + 5 种控制图 + 4 条 OOC 规则）、§14.4（/mes/dc/* + /mes/spc/* 接口）、§15.3 Week 31-32 任务表、§16.3 验收标准。

### 1.2 本周要解决的问题

1. 车间过程参数缺乏统一采集载体。需落地 `dc_collection_point`（采集点，区分手动/自动）+ `dc_data_record`（采集数据记录，数值/文本双值）+ 关联工单/流转卡/工序。
2. 缺乏统计过程控制（SPC）能力。需落地 `spc_control_chart`（控制图，5 种类型 X_BAR_R/X_BAR_S/P/C/U，UCL/CL/LCL + 样本大小）+ `spc_data_point`（数据点，样本组号 + OOC 标记 + 违规规则）。
3. 缺乏 OOC（out-of-control）自动判定。需硬编码 4 条 OOC 规则，分析时自动标注超限点并记录违规规则。
4. 缺乏控制图可视化。需前端 ECharts 绘制控制图（UCL/CL/LCL 参考线 + OOC 点红色标注）。

### 1.3 关键边界（已确认）

- **全五种控制图**（你的选择）：X_BAR_R（均值-极差）/X_BAR_S（均值-标准差）/P（不良率）/C（缺陷数）/U（单位缺陷数），各按对应公式计算 UCL/CL/LCL。
- **仅手动采集**（你的选择）：`dc_data_record` 仅支持手动录入（source=MANUAL），`point_type=AUTO` 仅预留字段，**不实现 IoT 自动采集归档**（不引入 ruoyi-iot 依赖、不调 IotDeviceDataQueryService）。IoT 联动列为后续增强。
- **SPC 消费 dc_data_record**（§8.4 设计）：SPC 控制图分析基于 `dc_data_record` 归档数据按样本大小分组，**不直接消费 IoT 原始数据 / pro_flow_card_step**。P 图的不良率数据来源 = 采集点录入的数值（操作员录入不良数/检验数），而非工序聚合。
- **OOC 规则硬编码 4 条**（§8.4）：规则 1 = 1 点超出控制限；规则 2 = 连续 9 点在中心线同侧；规则 3 = 连续 6 点递增或递减；规则 4 = 连续 14 点上下交替。
- **控制限存储**：UCL/CL/LCL 存于 `spc_control_chart` 行（analyze 时计算并回写），数据点 OOC 判定基于该图控制限。
- **数据量**：dc_data_record 先存 MySQL（§2.4 注：超 500 万行后考虑迁移 TDengine），本周不做分库。

### 1.4 预期结果

- 操作员可配置采集点（手动/自动、数值/文本/枚举、单位、频率）并手动录入采集数据，关联工单/流转卡/工序。
- 可创建 5 种类型 SPC 控制图，analyze 时按样本大小分组计算 UCL/CL/LCL 并回写，自动判定 4 条 OOC 规则标注超限点。
- 前端 ECharts 绘制控制图，UCL/CL/LCL 参考线 + OOC 点红色高亮。
- `mvn compile`（mes）与前端 `eslint` 通过。

---

## 二、关键事实（探索结论，非假设）

| 事实 | 依据 |
|------|------|
| dc/spc 四表 DDL 规范在主文档 §2.4（未执行），原 `tenant_id BIGINT` 需改为 Week25/26 约定 `VARCHAR(20) DEFAULT '000000'` + `del_flag CHAR(1)` | 主文档 §2.4 + `mes-week26-07-equipment.sql` |
| dc/ 与 spc/ 包完全空白：无 Java、无 DDL、无前端、无子菜单 | `ruoyi-mes/.../com/nexus/mes/` 仅 common/pro/qc/eq 四包 |
| 控制图类型 5 种：X_BAR_R/X_BAR_S/P/C/U；OOC 规则 4 条硬编码；sample_size 默认 5 | 主文档 §8.4 + DDL §2.4 |
| dc_data_record 双值：numeric_value(DECIMAL) + text_value(VARCHAR)；source MANUAL/IoT；关联 wo_id/card_id/step_record_id | 主文档 §2.4 |
| 采集点 point_type MANUAL/AUTO；data_type NUMERIC/TEXT/ENUM；status ACTIVE | 主文档 §2.4 |
| SPC 消费 dc_data_record，不直接消费 IoT/pro_flow_card_step；MES 手动采集，IoT 自动采集（本周不做联动） | 主文档 §8.4 + IoT 文档分工说明 |
| 菜单结构：4004 数据采集（Week17 已建父菜单，**零子菜单**）、4005 SPC分析（**零子菜单**）；Week26 用到 4124，**Week27 取 4130-4145**（留 4125-4129 空隙对齐接续习惯） | `mes-week17-10-menu.sql:4004/4005` + `mes-week26-20-menu.sql` |
| §14.4 接口：/mes/dc/collection-point(list/get/add/edit)、/mes/dc/data-record(list/get/add)、/mes/spc/control-chart(list/get/add/edit/analyze) | 主文档 §14.4 |
| §11.1 前端目录：mes/dc/collection-point/index.vue、mes/dc/data-record/index.vue、mes/spc/control-chart/index.vue（ECharts，§11 注） | 主文档 §11.1 |
| §16.3 验收：控制图可创建，OOC 点自动标注；MES 全链路 | 主文档 §16.3 |
| ECharts 前端模式：`import * as echarts from 'echarts'` + `echarts.init(domRef)` + resize 监听 | `plus-ui/src/views/iot/device/realtime.vue` |
| MES Entity/Service/Controller 模式：TenantEntity + @TableLogic delFlag + @DS("mes") @Service + @SaCheckPermission + BaseMapperPlus | Week26 `EqEquipment` 一族 + Week25 `QcIqc` 一族 |
| 编号生成模式：`前缀-yyyyMMdd-3位序号`（uk 唯一索引兜底） | Week25/26 generateNo |
| spc_data_point OOC 字段：is_ooc TINYINT(1) + ooc_rule VARCHAR(32) | 主文档 §2.4 |

---

## 三、任务清单

| # | 任务 | 优先级 | 交付物 | 验收标准 |
|---|------|--------|--------|----------|
| 1 | dc/spc DDL | 高 | `mes-week27-08-collection-spc.sql` | 四表对齐 Week26 约定 |
| 2 | Dc/Dc/Spc/Spc Entity/BO/VO/Mapper | 高 | 16 个 Java 文件 | 字段对齐 DDL |
| 3 | DC Service + 实现（采集点 CRUD + 数据记录录入） | 高 | 2 接口 + 2 实现 | 采集点 CRUD + 数据记录录入 |
| 4 | SPC Service + 实现（控制图 CRUD + analyze 计算控制限 + OOC 判定） | 高 | 2 接口 + 2 实现 | 5 种图控制限计算 + 4 条 OOC 规则 |
| 5 | 4 个 Controller（DC 2 + SPC 2） | 高 | 4 REST 控制器 | list/get/add/edit + analyze |
| 6 | 前端 3 API + 3 页面（含 ECharts 控制图） | 高 | 6 文件 | 采集点/数据记录列表 + 控制图 ECharts 可视化 |
| 7 | 菜单权限 SQL | 中 | `mes-week27-21-menu.sql` | 4130-4145，父菜单 4004/4005 |
| 8 | 编译 + lint 验证 | 高 | - | mvn compile + eslint 通过 |

---

## 四、详细任务分解

### 任务 1：dc/spc DDL

**File:** Create `doc/mes/sql/mes-week27-08-collection-spc.sql`

对齐主文档 §2.4 字段，按 Week25/26 约定调整（`tenant_id` BIGINT→VARCHAR(20)、加 `del_flag`、状态列 VARCHAR、is_ooc 用 BIT/CHAR）。四表：

```sql
USE `ry-mes`;

-- dc_collection_point: MES数据采集点
CREATE TABLE dc_collection_point (
    point_id         BIGINT NOT NULL COMMENT '采集点ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    point_code       VARCHAR(64) NOT NULL COMMENT '采集点编码',
    point_name       VARCHAR(128) NOT NULL COMMENT '采集点名称',
    point_type       VARCHAR(16) NOT NULL COMMENT 'MANUAL/AUTO',
    equipment_id     BIGINT COMMENT '关联设备ID',
    process_id       BIGINT COMMENT '关联工序ID',
    data_type        VARCHAR(16) COMMENT 'NUMERIC/TEXT/ENUM',
    unit             VARCHAR(16) COMMENT '单位',
    frequency        INT COMMENT '采集频率(秒)',
    status           VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    remark           VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (point_id),
    UNIQUE INDEX uk_point_code (point_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES数据采集点';

-- dc_data_record: MES采集数据记录(先存MySQL,超500万行后考虑TDengine)
CREATE TABLE dc_data_record (
    record_id        BIGINT NOT NULL COMMENT '记录ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    point_id         BIGINT NOT NULL COMMENT '采集点ID',
    point_code       VARCHAR(64) COMMENT '采集点编码(冗余)',
    mes_wo_id        BIGINT COMMENT '关联MES工单ID',
    card_id          BIGINT COMMENT '关联流转卡ID',
    step_record_id   BIGINT COMMENT '关联工序记录ID',
    numeric_value    DECIMAL(14,4) COMMENT '数值',
    text_value       VARCHAR(500) COMMENT '文本值',
    collect_time     DATETIME NOT NULL COMMENT '采集时间',
    collect_by       BIGINT COMMENT '采集人(手动时)',
    source           VARCHAR(16) DEFAULT 'MANUAL' COMMENT 'MANUAL/IoT',
    remark           VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (record_id),
    INDEX idx_point_id (point_id), INDEX idx_mes_wo_id (mes_wo_id), INDEX idx_collect_time (collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES采集数据记录';

-- spc_control_chart: MES SPC控制图
CREATE TABLE spc_control_chart (
    chart_id         BIGINT NOT NULL COMMENT '控制图ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    chart_name       VARCHAR(128) NOT NULL COMMENT '控制图名称',
    chart_type       VARCHAR(16) NOT NULL COMMENT 'X_BAR_R/X_BAR_S/P/C/U',
    point_id         BIGINT COMMENT '关联采集点ID',
    material_id      BIGINT COMMENT '关联物料ID',
    process_id       BIGINT COMMENT '关联工序ID',
    ucl              DECIMAL(14,4) COMMENT '上控制限(analyze计算回写)',
    cl               DECIMAL(14,4) COMMENT '中心线',
    lcl              DECIMAL(14,4) COMMENT '下控制限',
    sample_size      INT DEFAULT 5 COMMENT '样本大小',
    status           VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    remark           VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (chart_id),
    INDEX idx_point_id (point_id), INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SPC控制图';

-- spc_data_point: MES SPC数据点
CREATE TABLE spc_data_point (
    data_id          BIGINT NOT NULL COMMENT '数据点ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    chart_id         BIGINT NOT NULL COMMENT '控制图ID',
    sample_group     INT NOT NULL COMMENT '样本组号',
    sample_index     INT COMMENT '组内序号',
    value            DECIMAL(14,4) NOT NULL COMMENT '值(组均值/不良率/缺陷数等)',
    is_ooc           CHAR(1) DEFAULT '0' COMMENT '是否超控制限(0/1)',
    ooc_rule         VARCHAR(32) COMMENT '违规规则(RULE1/2/3/4)',
    collect_time     DATETIME COMMENT '采集时间',
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (data_id),
    INDEX idx_chart_id (chart_id), INDEX idx_group (chart_id, sample_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SPC数据点';
```

> 与主文档差异：`tenant_id` BIGINT→VARCHAR(20)；加 `del_flag`/`remark`；`wo_id`→`mes_wo_id`；`is_ooc` TINYINT(1)→CHAR(1)（对齐 del_flag 习惯）。均对齐 Week25/26 既定约定。

### 任务 2：Entity/BO/VO/Mapper（16 文件）

**Packages:** `com.nexus.mes.dc.*` + `com.nexus.mes.spc.*`（新建两包，镜像 qc/eq 布局）

每张表 4 文件（domain + bo + vo + mapper），共 16 个：

- `dc/domain/DcCollectionPoint.java` / `DcDataRecord.java`
- `spc/domain/SpcControlChart.java` / `SpcDataPoint.java`
- 对应 `domain/bo/`、`domain/vo/`、`mapper/`（各 4 文件）
- Entity：`@Data @EqualsAndHashCode(callSuper=true) @TableName` extends `TenantEntity`，`@TableId` + `@TableLogic delFlag`。
- BO：`@AutoMapper(target=..., reverseConvertGenerate=false)` extends `BaseEntity`，校验 `@NotBlank pointCode/pointName/chartName`、`@NotNull pointId/dataId`，查询字段 + `Long[] ids`。
- VO：`@ExcelIgnoreUnannotated @AutoMapper(target=...)`。SpcDataPointVo 含 `isOoc`/`oocRule`。
- Mapper：`extends BaseMapperPlus<Xxx, XxxVo>`。

### 任务 3：DC Service + 实现（4 文件）

**Files:** `dc/service/IDcCollectionPointService.java` + impl、`dc/service/IDcDataRecordService.java` + impl

**IDcCollectionPointService**：`selectPageList`、`selectById`、`insertByBo`、`updateByBo`、`deleteByIds`。状态常量 `STATUS_ACTIVE/INACTIVE`、`POINT_TYPE_MANUAL/AUTO`、`DATA_TYPE_NUMERIC/TEXT/ENUM`。类头 `@DS("mes") @Service("dcCollectionPointService")`。

**IDcDataRecordService**：
- `selectPageList(bo, pageQuery)`、`selectById(recordId)`、`insertByBo(bo)`（手动录入，source=MANUAL，collectBy=LoginHelper）。
- **按采集点聚合查询**（供 SPC analyze 用）：`listByPointId(Long pointId)` 返回该采集点全部记录（按 collect_time 升序），或 `listByPointIdAndTimeRange(pointId, start, end)`。
- source 常量 `SOURCE_MANUAL/IoT`。
- 类头 `@DS("mes") @Service("dcDataRecordService")`。
- insertByBo 时冗余 point_code（查采集点），collect_time 默认 now。

### 任务 4：SPC Service + 实现（4 文件，核心）

**Files:** `spc/service/ISpcControlChartService.java` + impl、`spc/service/ISpcDataPointService.java` + impl

**ISpcControlChartService**：
- `selectPageList`、`selectById`、`insertByBo`、`updateByBo`、`deleteByIds`。
- **`analyze(Long chartId)`**（核心）：分析控制图 — 拉采集点数据 → 按样本大小分组 → 计算 UCL/CL/LCL → 回写图表 → 删除旧数据点 → 生成新 spc_data_point + OOC 判定。
- 图表类型常量 `TYPE_X_BAR_R/X_BAR_S/P/C/U`。

**analyze 算法（核心）：**
1. 查图表，取 pointId / sampleSize / chartType。
2. 查 `dc_data_record` by pointId（按 collect_time 升序，仅 numeric_value 非空）。
3. 按 `sampleSize` 分组：X_BAR_R/X_BAR_S 每组 sampleSize 个样本；P/C/U 每组 = 一个检验批（样本量 n + 不良数/缺陷数）。
4. **计算控制限**（按类型，BigDecimal，4 位小数）：
   - **X_BAR_R**：每组均值 x̄；CL=grand mean（所有 x̄ 均值）；R̄=所有组极差均值；UCL=x̄̄ + A2(n)×R̄；LCL=x̄̄ - A2(n)×R̄。A2 系数表（n=2..10）：A2={1.880,1.023,0.729,0.577,0.483,0.419,0.373,0.337,0.308}（index 0=n=2）。**值存组均值 x̄**。
   - **X_BAR_S**：CL=grand mean；S̄=所有组标准差均值；UCL=x̄̄ + A3(n)×S̄；LCL=x̄̄ - A3(n)×S̄。A3={2.659,1.954,1.628,1.427,1.287,1.182,1.099,1.032,0.975}。**值存组均值 x̄**。
   - **P**（不良率）：每组 p=不良数/检验数；CL=p̄（总不良数/总检验数）；UCL=p̄ + 3√(p̄(1-p̄)/n̄)；LCL=p̄ - 3√(...)（<0 取0）。**值存组不良率 p**。
   - **C**（缺陷数）：每组 c=缺陷数；CL=c̄（均值）；UCL=c̄ + 3√c̄；LCL=c̄ - 3√c̄（<0 取0）。**值存组缺陷数 c**。
   - **U**（单位缺陷数）：每组 u=缺陷数/单位数；CL=ū；UCL=ū + 3√(ū/n̄)；LCL=ū - 3√(...)。**值存组单位缺陷数 u**。
5. 回写图表 ucl/cl/lcl。
6. 删除该图旧 spc_data_point，按组生成新数据点（value=组统计值，sample_group=组号）。
7. **OOC 判定**（4 条规则，对每个数据点 value 判定）：
   - RULE1：value > UCL 或 value < LCL。
   - RULE2：连续 9 点在 CL 同侧。
   - RULE3：连续 6 点严格递增或递减。
   - RULE4：连续 14 点交替上下（zig-zag）。
   - 命中任一规则 → is_ooc=1，ooc_rule 记首个命中规则（多规则用逗号拼接）。
8. 返回分析结果 Map：`{ chart, points:[...], oocCount, ucl, cl, lcl }`。

**ISpcDataPointService**：`selectByChartId(chartId)`（按 sample_group 升序，供前端绘图）、`selectPageList`、`selectById`。类头 `@DS("mes") @Service("spcDataPointService")`。

> 控制限系数表 A2/A3 等硬编码为私有静态 Map（n=2..10）；n>10 或 n<2 抛参数异常。BigDecimal 计算用 RoundingMode.HALF_UP，4 位小数。

### 任务 5：4 个 Controller

**Files:**
- `dc/controller/DcCollectionPointController.java` — `/mes/dc/collection-point`：list/get/add/edit/del
- `dc/controller/DcDataRecordController.java` — `/mes/dc/data-record`：list/get/add
- `spc/controller/SpcControlChartController.java` — `/mes/spc/control-chart`：list/get/add/edit/del + **GET /analyze/{chartId}**
- `spc/controller/SpcDataPointController.java` — `/mes/spc/data-point`：list/get（按 chartId 查，供前端绘图）

镜像 `EqEquipmentController` / `QcIqcController`：`@Validated @RequiredArgsConstructor @RestController`，`@SaCheckPermission`，`@Log`。

| 端点 | 权限 |
|------|------|
| GET /collection-point/list | mes:dc:point:list |
| POST /collection-point | mes:dc:point:add |
| PUT /collection-point | mes:dc:point:edit |
| DELETE /collection-point/{ids} | mes:dc:point:remove |
| GET /data-record/list | mes:dc:record:list |
| POST /data-record | mes:dc:record:add |
| GET /control-chart/list | mes:spc:chart:list |
| POST /control-chart | mes:spc:chart:add |
| PUT /control-chart | mes:spc:chart:edit |
| DELETE /control-chart/{ids} | mes:spc:chart:remove |
| GET /control-chart/analyze/{chartId} | mes:spc:chart:analyze |
| GET /data-point/list (by chartId) | mes:spc:point:list |

### 任务 6：前端 3 API + 3 页面（含 ECharts）

**Files (plus-ui/src/):**
- `api/mes/dc/collection-point.ts` / `data-record.ts` + `api/mes/spc/control-chart.ts` / `data-point.ts`（4 API 文件，对齐 `eq/equipment.ts`）。analyze 接口返回 Map。
- `views/mes/dc/collection-point/index.vue` — 采集点列表 + 新增/编辑弹窗（编码/名称/类型 MANUAL-AUTO/数据类型/单位/频率/关联设备/工序）。
- `views/mes/dc/data-record/index.vue` — 数据记录列表 + 录入弹窗（选采集点 + 数值/文本 + 关联工单/流转卡 + 采集时间）。
- `views/mes/spc/control-chart/index.vue` — 控制图列表 + 新增/编辑弹窗（名称/类型 5 种/采集点/样本大小）+ **"分析"按钮** → 调 analyze → 跳转或弹窗 ECharts 控制图。
- **ECharts 控制图**（核心可视化，参考 `iot/device/realtime.vue`）：
  - `import * as echarts from 'echarts'` + `echarts.init(ref)` + resize 监听。
  - 折线图：x 轴 = sample_group，y 轴 = value。
  - **三条 markLine**：UCL（红色虚线）、CL（绿色实线）、LCL（红色虚线）。
  - **OOC 点高亮**：is_ooc=1 的点用红色大圆点 + 标注 ooc_rule（scatter 系列，symbolSize 10）。
  - 正常点蓝色折线。
  - 顶部显示 UCL/CL/LCL 数值 + OOC 点数。

> control-chart 页面分析结果直接在弹窗/抽屉内渲染 ECharts（不单独建 hidden 路由页，简化），或用 el-drawer 展示。data-point 列表页可选（按 chartId 查表格）。

### 任务 7：菜单权限 SQL

**File:** Create `doc/mes/sql/mes-week27-21-menu.sql`

父菜单 **4004（数据采集，零子菜单）** + **4005（SPC分析，零子菜单）**。菜单 ID 段 **4130-4145**（Week26 用到 4124，留 4125-4129 空隙）。

```sql
USE `ry-vue`;
-- 数据采集点 (parent=4004 数据采集)
INSERT INTO sys_menu VALUES
(4130,'数据采集点',4004,1,'dc/collection-point','mes/dc/collection-point/index','',1,0,'C','0','0','mes:dc:point:list','monitor',103,1,NOW(),NULL,NULL,'MES数据采集点'),
(4131,'采集点查询',4130,1,'','','',1,0,'F','0','0','mes:dc:point:query','#',103,1,NOW(),NULL,NULL,''),
(4132,'采集点新增',4130,2,'','','',1,0,'F','0','0','mes:dc:point:add','#',103,1,NOW(),NULL,NULL,''),
(4133,'采集点编辑',4130,3,'','','',1,0,'F','0','0','mes:dc:point:edit','#',103,1,NOW(),NULL,NULL,''),
(4134,'采集点删除',4130,4,'','','',1,0,'F','0','0','mes:dc:point:remove','#',103,1,NOW(),NULL,NULL,'');
-- 采集数据记录
INSERT INTO sys_menu VALUES
(4135,'采集数据记录',4004,2,'dc/data-record','mes/dc/data-record/index','',1,0,'C','0','0','mes:dc:record:list','document',103,1,NOW(),NULL,NULL,'MES采集数据记录'),
(4136,'记录查询',4135,1,'','','',1,0,'F','0','0','mes:dc:record:query','#',103,1,NOW(),NULL,NULL,''),
(4137,'记录录入',4135,2,'','','',1,0,'F','0','0','mes:dc:record:add','#',103,1,NOW(),NULL,NULL,'');
-- SPC控制图 (parent=4005 SPC分析)
INSERT INTO sys_menu VALUES
(4138,'SPC控制图',4005,1,'spc/control-chart','mes/spc/control-chart/index','',1,0,'C','0','0','mes:spc:chart:list','chart',103,1,NOW(),NULL,NULL,'MES SPC控制图'),
(4139,'控制图查询',4138,1,'','','',1,0,'F','0','0','mes:spc:chart:query','#',103,1,NOW(),NULL,NULL,''),
(4140,'控制图新增',4138,2,'','','',1,0,'F','0','0','mes:spc:chart:add','#',103,1,NOW(),NULL,NULL,''),
(4141,'控制图编辑',4138,3,'','','',1,0,'F','0','0','mes:spc:chart:edit','#',103,1,NOW(),NULL,NULL,''),
(4142,'控制图删除',4138,4,'','','',1,0,'F','0','0','mes:spc:chart:remove','#',103,1,NOW(),NULL,NULL,''),
(4143,'控制图分析',4138,5,'','','',1,0,'F','0','0','mes:spc:chart:analyze','#',103,1,NOW(),NULL,NULL,'');
-- SPC数据点
INSERT INTO sys_menu VALUES
(4144,'SPC数据点',4005,2,'spc/data-point','mes/spc/data-point/index','',1,0,'C','0','0','mes:spc:point:list','tickets',103,1,NOW(),NULL,NULL,'MES SPC数据点'),
(4145,'数据点查询',4144,1,'','','',1,0,'F','0','0','mes:spc:point:query','#',103,1,NOW(),NULL,NULL,'');
```

> 验证：`SELECT menu_id,menu_name,perms FROM sys_menu WHERE menu_id BETWEEN 4130 AND 4145 ORDER BY menu_id;` 执行后为角色勾选。共 4 个 C 菜单 + 11 个 F 按钮。

### 任务 8：编译 + lint 验证

- [ ] `mvn compile -pl ruoyi-modules/ruoyi-mes -am` 通过
- [ ] 前端 `eslint src/views/mes/dc src/views/mes/spc src/api/mes/dc src/api/mes/spc --ext .vue,.ts` 无 error

---

## 五、端到端链路

```
数据采集:
  配置采集点(编码/类型MANUAL/数据类型NUMERIC/单位/频率)
    → 操作员录入采集数据(dc_data_record, source=MANUAL, 关联工单/流转卡/工序, numeric_value)
    → 仅手动采集(IoT AUTO 归档为后续增强)

SPC 分析:
  创建控制图(名称/类型X_BAR_R/采集点pointId/样本大小5)
    → "分析"按钮 → analyze(chartId)
        → 查 dc_data_record by pointId(升序) → 按 sampleSize 分组
        → 计算 UCL/CL/LCL(按类型公式 + A2/A3 系数) → 回写图表
        → 删除旧 spc_data_point → 生成新数据点(value=组统计值)
        → OOC 判定(4 条规则) → is_ooc=1 + ooc_rule 标注
    → 前端 ECharts 绘制(UCL/CL/LCL 参考线 + OOC 红点高亮 + 违规规则标注)

图表类型对应:
  X_BAR_R/X_BAR_S: 计量型, 每组 sampleSize 个样本, value=组均值 x̄
  P: 计数型不良率, 每组一检验批, value=组不良率 p
  C: 计数型缺陷数, value=组缺陷数 c
  U: 计数型单位缺陷数, value=组单位缺陷数 u
```

---

## 六、风险与注意事项

| 风险 | 应对 |
|------|------|
| 5 种图表控制限公式实现复杂 | 系数表 A2/A3 硬编码 Map(n=2..10)；n 越界抛异常；BigDecimal HALF_UP 4 位小数；单元测试覆盖关键公式 |
| OOC 规则边界（数据点不足） | 规则 2/3/4 需连续 N 点，数据点不足时跳过该规则（不报错）；仅 RULE1 单点判定 |
| analyze 数据量与分组 | 采集记录数非 sampleSize 整数倍时，末组不足 sampleSize 丢弃（X_BAR_R/S）；P/C/U 每组独立检验批 |
| SPC 消费数据来源边界 | 仅消费 dc_data_record（手动录入），不直接查 IoT/pro_flow_card_step；P 图不良率由操作员录入数值，非工序聚合 |
| ECharts 控制图渲染时机 | analyze 返回后 nextTick 初始化 echarts；onUnmounted dispose；resize 监听 |
| IoT 联动越界 | point_type=AUTO 仅预留字段，不引入 ruoyi-iot 依赖，不调 IotDeviceDataQueryService |
| 数据量增长 | dc_data_record 先 MySQL，§2.4 注超 500 万行迁移 TDengine；本周不做分库 |
| spc_data_point 重建并发 | analyze 内先删后插，同一 chartId 无并发（操作员触发）；不加 Lock4j |
| dc/spc 两包共存编译 | 全新两包，无现有引用改动；仅新增文件 |

---

## 七、明确排除项（本周不做）

- IoT 自动采集归档到 dc_data_record（point_type=AUTO 仅预留，后续增强）
- 数据迁移 TDengine（先 MySQL，超 500 万行后续）
- 实时数据流/SSE 推送采集数据（本周手动录入）
- 控制限系数动态扩展（n>10 硬编码不支持，后续）
- SPC 报警/安灯联动（OOC 触发安灯为后续增强）
- 控制图导出/打印
- el-table → vxe-table 迁移
- Phase 3 集成测试报告（运行时人工）

---

## 八、交付物汇总

| 交付物 | 类型 | 路径 |
|--------|------|------|
| `mes-week27-08-collection-spc.sql` | SQL | `doc/mes/sql/mes-week27-08-collection-spc.sql` |
| Dc/Spc Entity/BO/VO/Mapper | Java×16 | `ruoyi-mes/.../{dc,spc}/{domain,domain/bo,domain/vo,mapper}/` |
| IDcCollectionPointService + IDcDataRecordService + 实现 | Java×4 | `ruoyi-mes/.../dc/{service,service/impl}/` |
| ISpcControlChartService + ISpcDataPointService + 实现 | Java×4 | `ruoyi-mes/.../spc/{service,service/impl}/` |
| 4 Controller | Java×4 | `ruoyi-mes/.../{dc,spc}/controller/` |
| `collection-point.ts`/`data-record.ts`/`control-chart.ts`/`data-point.ts` | TS×4 | `plus-ui/src/api/mes/{dc,spc}/` |
| `collection-point/index.vue`/`data-record/index.vue`/`control-chart/index.vue`/`data-point/index.vue` | Vue×4 | `plus-ui/src/views/mes/{dc,spc}/` |
| `mes-week27-21-menu.sql` | SQL | `doc/mes/sql/mes-week27-21-menu.sql` |

> 注：前端 4 API + 4 页面（含 SPC 数据点列表页），共 8 前端文件。

---

## 九、验证方法（端到端）

> 运行时验证依赖：`ry-vue`/`ry-mes` 两库 + Redis 就绪，并已执行 Week17/25/26 DDL 与菜单 SQL。

1. **DDL 执行**：`mes-week27-08-collection-spc.sql` 建四表；`mes-week27-21-menu.sql` 注入菜单并为角色勾选 4130-4145。
2. **采集点 + 数据录入**：新增采集点（编码 TEMP-01/类型 MANUAL/数据类型 NUMERIC/单位 ℃）→ 录入 25 条采集数据（模拟正态分布 + 1 个异常值）。
3. **X_BAR_R 分析**：创建控制图（类型 X_BAR_R/采集点 TEMP-01/样本大小 5）→ "分析" → UCL/CL/LCL 计算回写 → 5 个数据点（25 条/5）→ ECharts 显示三参考线 + 折线；异常值点若超 UCL 标红 + RULE1 标注。
4. **P 图分析**：创建 P 图（采集点录入不良数/检验数）→ analyze → 不良率控制限 → OOC 判定。
5. **OOC 规则**：构造连续 9 点同侧数据 → 分析 → RULE2 标注；构造 6 点递增 → RULE3。
6. **幂等**：同一控制图重复 analyze → 旧数据点删除后重建（不累积）。
7. **静态**：`mvn compile`（mes）+ `eslint` 均通过。

---

**文档生成时间**: 2026-06-25
**执行者**: AI Agent
**审核状态**: 待 Claude 自审 → 待用户审查
