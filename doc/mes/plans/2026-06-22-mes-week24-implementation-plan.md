# MES Phase 2 Week 24 开发实施计划 — SN 追溯体系（序列号 + 组装追溯 + 前向/后向追溯树）

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（任务级 checkbox 跟踪）。Steps 用 `- [ ]` 标记。

> **实施日期**: 2026-06-22
> **阶段**: MES Phase 2 — SN 追溯体系（Phase 2 收尾）
> **状态**: 待 Claude 自审 + 用户审查

---

## 一、Context（背景与目标）

### 1.1 为什么做这一周

主计划《NexusERP_MES文档》§10.2 Phase 2 划为：流转卡(22-23)、派工(24-25)、安灯(26)、SN(27)。但实际进度领先——流转卡 Week 20、派工 Week 22、安灯 Week 23 均已完成。按 Week 22 计划"明确排除项"约定的滚动排期（SN Week 24），**Week 24 = SN 追溯体系**，对应主文档 §七（SN 追溯）、§2.1 `pro_sn` 表、§14.4 `/mes/pro/sn` 接口、§11.1 前端 `pro/sn/`。本周完成后 Phase 2（流转卡+派工+安灯+SN）全部闭合，可进入 Phase 3（质量/设备/采集/SPC）。

### 1.2 本周要解决的问题

1. 产品缺乏唯一序列号追溯。需落地 `pro_sn` 表，每个完工/在制产品分配唯一 SN 码，关联工单/流转卡/物料，支持状态流转 IN_PRODUCTION→COMPLETED→SCRAPPED→SHIPPED。
2. 组装场景缺乏父子追溯。SN 需支持 `parent_sn_id` 父子关系（组件 SN → 成品 SN），构建组装追溯树。
3. 缺乏前向/后向追溯查询。需提供追溯 API：前向（SN→流转卡工序→操作员 + 子 SN）、后向（父 SN 链 + 物料→SN），前端用 el-tree 可视化。

### 1.3 与 ERP 既有 SN 表的关系（关键边界）

ERP 库已存在两张 SN 相关表（**不在 MES 库，本周不改动**）：
- `bd_serial_number_rule` — 序列号/条码**生成规则**引擎（rule_type=PRODUCT_SN，含 prefix/date_format/seq_length/current_seq）。MES 本周**不跨库复用**该规则引擎（跨库原子自增 current_seq 风险高），改用本地编号生成；复用规则引擎列为后续增强。
- `wm_sn` — ERP**仓储**序列号表（status=IN_STOCK 等，跟踪库存出入库）。

MES `pro_sn` 是**生产追溯**序列号表（status=IN_PRODUCTION/COMPLETED/SCRAPPED/SHIPPED + parent_sn 组装追溯），定位不同，主文档 §12.2 明确"SN 追溯仅 MES"。两者**不合并、不同步**（避免双写），未来完工入库时由 ERP 侧自行生成 wm_sn。本周 `pro_sn` 自包含。

### 1.4 预期结果

- 操作员可从流转卡批量生成 SN（`generateSnForCard`）或手动单建 SN（含 parent_sn 组装）；SN 状态机正确流转。
- 前向/后向追溯 API 返回完整追溯链，前端 el-tree 可视化。
- SN 与工单/流转卡/物料正确关联，支持按 SN/工单/物料/状态查询。
- `mvn compile` 与前端 `eslint` 通过。

---

## 二、关键事实（探索结论，非假设）

| 事实 | 依据 |
|------|------|
| MES Entity/BO/VO/Mapper/Service/Controller 模式（TenantEntity、@DS("mes")、@Lock4j+CAS、@SaCheckPermission、BaseMapperPlus） | Week22 `ProDispatch` 一族 + Week23 `ProAndon` 一族（已实现） |
| DDL 约定：`tenant_id VARCHAR(20) DEFAULT '000000'`、`del_flag CHAR(1)`、状态列 `VARCHAR(32)`、`mes_wo_id` 命名 | `doc/mes/sql/mes-week22-03-dispatch.sql` / `mes-week23-04-andon.sql` |
| 流转卡完工聚合链路 completeStep→completeCard→completeWo（SN 生成挂载点的上下文） | `ProFlowCardServiceImpl.completeCard()` |
| 主文档 `pro_sn` DDL 字段：sn_id/sn_code/wo_id/card_id/material_id/material_code/material_name/current_step_seq/status/parent_sn_id | 主文档 §2.1 |
| SN 状态：IN_PRODUCTION/COMPLETED/SCRAPPED/SHIPPED | 主文档 §7.2 |
| 追溯方向：前向 SN→工序→物料→设备→操作员；后向 物料→SN→工序→产品 | 主文档 §7.1 |
| 前端列表页模式（el-table + 搜索 + v-hasPermi + $modal.prompt `{ value }` 解构 + onMounted getList） | `views/mes/pro/dispatch/index.vue`、`andon/index.vue` |
| 前端详情/树页可用 el-tree + el-descriptions | Element Plus 通用 |
| MES 页面菜单驱动（sys_menu），无需改 router；菜单 ID 段 Week22=4060-4067、Week23=4070-4075，**Week24 取 4080-4089** | `mes-week22-16-menu.sql / mes-week23-17-menu.sql` |
| `pro_sn` 表尚未在任何 mes-weekNN-*.sql 中创建 | grep 无命中 |
| ERP `bd_serial_number_rule` / `wm_sn` 存在但属 ERP 库，本周不复用/不同步 | `script/sql/ry-erp.sql:587,4117` |

---

## 三、任务清单

| # | 任务 | 优先级 | 交付物 | 验收标准 |
|---|------|--------|--------|----------|
| 1 | pro_sn DDL | 高 | `mes-week24-05-sntrace.sql` | 表结构对齐 Week22/23 约定 |
| 2 | ProSn Entity/BO/VO/Mapper | 高 | 4 个 Java 文件 | 字段对齐 DDL |
| 3 | IProSnService + 实现（CRUD + 生成 + 状态机 + 追溯） | 高 | 接口 + 实现类 | generateSnForCard/createSn/complete/scrap/ship + 前向/后向追溯 |
| 4 | ProSnController | 高 | REST 控制器 | list/get/generate/add/trace/complete/scrap/ship |
| 5 | 前端：sn API + 类型 | 高 | `sn.ts` | 前端可调用全部接口 |
| 6 | 前端：SN 列表页 | 高 | `sn/index.vue` | 列表+搜索+生成/手动建/状态操作 |
| 7 | 前端：追溯树页 | 高 | `sn/trace.vue` | el-tree 前向/后向追溯可视化 |
| 8 | 菜单权限 SQL | 中 | `mes-week24-18-menu.sql` | SN 菜单 4080 + 按钮权限 4081-4087 |
| 9 | 编译 + lint 验证 | 高 | - | `mvn compile` + `eslint` 通过 |

---

## 四、详细任务分解

### 任务 1：pro_sn DDL

**File:** Create `doc/mes/sql/mes-week24-05-sntrace.sql`

对齐主文档 §2.1 字段，按 Week22/23 约定调整命名与精度：

```sql
USE `ry-mes`;

-- pro_sn: MES SN序列号追溯
-- 状态机: IN_PRODUCTION -> COMPLETED -> SHIPPED
--                  └-> SCRAPPED (终态)
-- 组装追溯: parent_sn_id 关联父SN(组件->成品)
CREATE TABLE pro_sn (
    sn_id           BIGINT NOT NULL COMMENT 'SN ID(主键)',
    tenant_id       VARCHAR(20) NOT NULL DEFAULT '000000',
    sn_code         VARCHAR(128) NOT NULL COMMENT 'SN序列号编码',
    mes_wo_id       BIGINT NOT NULL COMMENT '关联MES工单ID',
    wo_no           VARCHAR(64) COMMENT '工单编号(冗余)',
    card_id         BIGINT COMMENT '关联流转卡ID',
    card_no         VARCHAR(64) COMMENT '流转卡编号(冗余)',
    material_id     BIGINT NOT NULL COMMENT '物料ID',
    material_code   VARCHAR(64) COMMENT '物料编码(冗余)',
    material_name   VARCHAR(128) COMMENT '物料名称(冗余)',
    current_step_seq INT COMMENT '当前工序序号',
    sn_status       VARCHAR(32) DEFAULT 'IN_PRODUCTION' COMMENT 'IN_PRODUCTION/COMPLETED/SCRAPPED/SHIPPED',
    parent_sn_id    BIGINT COMMENT '父SN ID(组装追溯, 组件SN指向成品SN)',
    parent_sn_code  VARCHAR(128) COMMENT '父SN编码(冗余)',
    source          VARCHAR(16) DEFAULT 'MANUAL' COMMENT 'MANUAL/BATCH(来源: 手动/批量生成)',
    production_date DATE COMMENT '生产日期',
    remark          VARCHAR(500),
    create_dept     BIGINT,
    create_by       BIGINT,
    create_time     DATETIME,
    update_by       BIGINT,
    update_time     DATETIME,
    del_flag        CHAR(1) DEFAULT '0',
    PRIMARY KEY (sn_id),
    UNIQUE INDEX uk_sn_code (sn_code),
    INDEX idx_sn_status (sn_status),
    INDEX idx_mes_wo_id (mes_wo_id),
    INDEX idx_card_id (card_id),
    INDEX idx_material_id (material_id),
    INDEX idx_parent_sn_id (parent_sn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES SN序列号追溯';
```

> 与主文档差异：`wo_id`→`mes_wo_id`、`status`→`sn_status`、新增 `wo_no/card_no/parent_sn_code/source/production_date` 冗余列、`tenant_id` 用 VARCHAR(20)、增加 `del_flag`。均对齐 Week17/22/23 既定约定。

---

### 任务 2：ProSn Entity/BO/VO/Mapper

**Files (全部新建，包 `com.nexus.mes.pro.*`):**
- `domain/ProSn.java`
- `domain/bo/ProSnBo.java`
- `domain/vo/ProSnVo.java`
- `mapper/ProSnMapper.java`

完全镜像 `ProAndon`/`ProDispatch` 一族。

**Entity 字段**（对应 DDL）：`snId(@TableId)`、`snCode`、`mesWoId`、`woNo`、`cardId`、`cardNo`、`materialId`、`materialCode`、`materialName`、`currentStepSeq`、`snStatus`、`parentSnId`、`parentSnCode`、`source`、`productionDate`、`remark`、`@TableLogic delFlag`。

**BO 校验**：`@NotNull materialId`、`@NotBlank snCode`（手动单建时必填；批量生成时由 Service 生成）；`mesWoId`、`cardId`、`parentSnId` 可空；查询用 `snCode`、`snStatus`、`mesWoId`、`materialId`、`parentSnId`。

**VO**：镜像 Entity + `@ExcelIgnoreUnannotated @AutoMapper(target=ProSn.class)`，并新增**非 DB 的追溯辅助字段**（仅 trace 用，普通查询不填）：`List<ProSnVo> children`、`List<Map<String,Object>> steps`（前向追溯的工序链）。VO 加这两个 transient 字段（不映射 DB 列，MapStruct 忽略）。

**Mapper**：`extends BaseMapperPlus<ProSn, ProSnVo>`。

---

### 任务 3：IProSnService + 实现（核心）

**Files:**
- `service/IProSnService.java`
- `service/impl/ProSnServiceImpl.java`

**接口（状态常量 + 方法）：**

```java
public interface IProSnService {
    String STATUS_IN_PRODUCTION = "IN_PRODUCTION";
    String STATUS_COMPLETED = "COMPLETED";
    String STATUS_SCRAPPED = "SCRAPPED";
    String STATUS_SHIPPED = "SHIPPED";

    String SOURCE_MANUAL = "MANUAL";
    String SOURCE_BATCH = "BATCH";

    TableDataInfo<ProSnVo> selectPageList(ProSnBo bo, PageQuery pageQuery);
    ProSnVo selectById(Long snId);
    ProSnVo selectByCode(String snCode);

    /** 从流转卡批量生成SN(操作员触发, 不自动耦合completeCard) */
    List<ProSnVo> generateSnForCard(Long cardId, Integer quantity, String initialStatus);
    /** 手动创建单个SN(可指定parentSnId用于组装追溯) */
    ProSnVo createSn(Long mesWoId, Long cardId, Long materialId,
                     String snCode, Long parentSnId, String remark);

    /** 完工(IN_PRODUCTION -> COMPLETED) */
    void completeSn(Long snId);
    /** 报废(IN_PRODUCTION/COMPLETED -> SCRAPPED) */
    void scrapSn(Long snId, String remark);
    /** 发货(COMPLETED -> SHIPPED) */
    void shipSn(Long snId);

    /** 追溯树: 前向(SN->工序->操作员 + 子SN) + 后向(父SN链) */
    Map<String, Object> trace(Long snId);
}
```

**实现要点：**

- 类头：`@Slf4j @RequiredArgsConstructor @DS("mes") @Service("proSnService")`。
- 依赖注入：`ProSnMapper baseMapper`、`ProFlowCardMapper flowCardMapper`（查流转卡冗余字段）、`ProFlowCardStepMapper stepMapper`（前向追溯的工序链）、`ProWorkOrderMapper workOrderMapper`（查工单物料冗余）。
- `generateSnForCard(cardId, quantity, initialStatus)`：
  1. 查流转卡（校验存在），取 mesWoId/woNo/materialId/materialCode/materialName/cardNo。
  2. 循环 quantity 次，生成 sn_code（`SN-` + cardNo + `-` + 4位序号，序号基于该卡已有 SN 数 +1），设 snStatus=initialStatus（默认 IN_PRODUCTION；若卡已 COMPLETED 可传 COMPLETED）、source=BATCH、productionDate=今天。
  3. 批量 insert，返回 VO 列表。
  - `@Lock4j(keys={"#cardId"})` 防并发生成重复序号。
- `createSn(...)`：校验 sn_code 唯一；若 parentSnId 非空，查父 SN 冗余 parent_sn_code 并校验同物料族；insert。callerId/callerName 取 `LoginHelper`（可空）。
- `completeSn`/`scrapSn`/`shipSn`：CAS 状态转换，参考 ProAndon 状态机模式。scrap 设 remark；ship 需先 COMPLETED。
- `trace(snId)`：
  - **前向**：查 SN 自身 → 查其流转卡的所有工序记录 `pro_flow_card_step`（stepMapper by cardId，含 operator/good_qty 等）→ 查子 SN 列表（parent_sn_id=snId）。
  - **后向**：沿 parent_sn_id 向上递归查父 SN 链（限深 10 层防环）。
  - 返回 Map：`{ sn, forwardSteps:[...], children:[...], parentChain:[...] }`。
  - 注意：前向"工序→物料→设备→操作员"中，工序记录已含 operatorId/operatorName；物料来自 SN 本身冗余；设备暂无直接关联（设备在 eq 模块 Phase 3），追溯中以工序 workcenter 代替，列为后续增强。

**SN 编号生成私有方法**：
```java
private String generateSnCode(String cardNo, int seq) {
    return "SN-" + cardNo + "-" + String.format("%04d", seq);
}
```
序号 seq = 该卡已有 SN 数 + 当前批次内序号（@Lock4j 保证并发安全）。

---

### 任务 4：ProSnController

**File:** `pro/controller/ProSnController.java`

镜像 `ProAndonController`：

```
GET  /mes/pro/sn/list                mes:pro:sn:list
GET  /mes/pro/sn/{snId}              mes:pro:sn:query
GET  /mes/pro/sn/by-code/{snCode}    mes:pro:sn:query
GET  /mes/pro/sn/trace/{snId}        mes:pro:sn:query    (追溯树)
POST /mes/pro/sn/generate            mes:pro:sn:add      (params: cardId, quantity, initialStatus)
POST /mes/pro/sn                     mes:pro:sn:add      (手动单建, params: mesWoId,cardId,materialId,snCode,parentSnId,remark)
PUT  /mes/pro/sn/complete/{snId}     mes:pro:sn:complete
PUT  /mes/pro/sn/scrap/{snId}        mes:pro:sn:scrap    (params: remark)
PUT  /mes/pro/sn/ship/{snId}         mes:pro:sn:ship
```

`@Log` 记录生成/状态变更操作。

---

### 任务 5：前端 sn API + 类型

**File:** Create `plus-ui/src/api/mes/pro/sn.ts`（对齐 `andon.ts` 模式）

```typescript
export interface ProSnVO {
  snId: number; snCode: string; mesWoId: number; woNo?: string;
  cardId?: number; cardNo?: string; materialId: number; materialCode?: string; materialName?: string;
  currentStepSeq?: number; snStatus: string; parentSnId?: number; parentSnCode?: string;
  source?: string; productionDate?: string; remark?: string;
  // 追溯辅助(transient)
  children?: ProSnVO[]; steps?: any[];
}
export interface ProSnQuery extends PageQuery {
  snCode?: string; snStatus?: string; mesWoId?: number; materialId?: number; parentSnId?: number;
}
listSn / getSn / getSnByCode / traceSn
generateSnForCard(cardId, quantity, initialStatus)
createSn(mesWoId, cardId, materialId, snCode, parentSnId, remark)
completeSn(snId) / scrapSn(snId, remark) / shipSn(snId)
```

`request` from `@/utils/request`，`AxiosPromise`，url/method 对齐后端。

---

### 任务 6：前端 SN 列表页

**File:** Create `plus-ui/src/views/mes/pro/sn/index.vue`

镜像 `andon/index.vue` 结构：

- 搜索栏：SN 编号 / 状态（IN_PRODUCTION/COMPLETED/SCRAPPED/SHIPPED）/ 工单ID / 物料ID。
- 表格：SN 编号 / 工单 / 流转卡 / 物料 / 当前工序序号 / 父SN / 状态（el-tag 颜色编码）/ 来源 / 生产日期 / 操作。
- 操作按钮：追溯（跳 trace 页）/ 完工（IN_PRODUCTION）/ 报废（IN_PRODUCTION|COMPLETED，prompt remark，`{ value }` 解构）/ 发货（COMPLETED）。`v-hasPermi` 对应权限。
- 顶部"批量生成SN"按钮 → 弹 `el-dialog`：输入流转卡ID + 数量 + 初始状态（默认 IN_PRODUCTION），调 `generateSnForCard`。
- 顶部"手动建SN"按钮 → 弹 `el-dialog`：填 snCode/工单ID/物料ID/父SN(可选)/备注，调 `createSn`。
- 状态颜色：IN_PRODUCTION=warning、COMPLETED=success、SCRAPPED=danger、SHIPPED=info。

---

### 任务 7：前端追溯树页

**File:** Create `plus-ui/src/views/mes/pro/sn/trace.vue`

路由 `mes/pro/sn/trace/:snId`（菜单 hidden，由列表"追溯"按钮 `router.push` 跳转）。

- 顶部 SN 基本信息卡（el-descriptions）：SN 编号/物料/工单/流转卡/状态/父SN。
- **后向追溯树**（父 SN 链）：el-tree 展示 `parentChain`，根为当前 SN，向上展开父 SN（组装层级）。
- **前向追溯树**（SN→工序→子SN）：el-tree，根为当前 SN，子节点为"工序链 steps"（每道工序显示 工序名/操作员/良品数/时间）+ "子 SN 列表 children"（组装下级）。
- `onMounted` 调 `traceSn(snId)` 获取数据，构建 tree data。

---

### 任务 8：菜单权限 SQL

**File:** Create `doc/mes/sql/mes-week24-18-menu.sql`

菜单 ID 段 **4080-4087**（Week23 用到 4075，接续）。父菜单 4001（生产执行）。

```sql
USE `ry-vue`;
-- SN追溯菜单 (parent=4001)
INSERT INTO sys_menu VALUES
(4080,'SN追溯',4001,5,'sn','mes/pro/sn/index','',1,0,'C','0','0','mes:pro:sn:list','barcode',103,1,NOW(),NULL,NULL,'MES SN序列号追溯');
-- SN追溯树(hidden, 由列表跳转)
INSERT INTO sys_menu VALUES
(4081,'SN追溯树',4080,1,'sn/trace/:snId','mes/pro/sn/trace','',1,0,'C','1','0','mes:pro:sn:query','#',103,1,NOW(),NULL,NULL,'');
-- 按钮权限
INSERT INTO sys_menu VALUES
(4082,'SN查询',4080,1,'','','',1,0,'F','0','0','mes:pro:sn:query','#',103,1,NOW(),NULL,NULL,''),
(4083,'SN生成',4080,2,'','','',1,0,'F','0','0','mes:pro:sn:add','#',103,1,NOW(),NULL,NULL,''),
(4084,'SN完工',4080,3,'','','',1,0,'F','0','0','mes:pro:sn:complete','#',103,1,NOW(),NULL,NULL,''),
(4085,'SN报废',4080,4,'','','',1,0,'F','0','0','mes:pro:sn:scrap','#',103,1,NOW(),NULL,NULL,''),
(4086,'SN发货',4080,5,'','','',1,0,'F','0','0','mes:pro:sn:ship','#',103,1,NOW(),NULL,NULL,'');
```

> 验证：`SELECT menu_id,menu_name,perms FROM sys_menu WHERE menu_id BETWEEN 4080 AND 4086;` 执行后为角色勾选 4080-4086。

---

### 任务 9：编译 + lint 验证

- [ ] `mvn compile -pl ruoyi-modules/ruoyi-mes -am` 通过
- [ ] 前端 `eslint src/views/mes src/api/mes --ext .vue,.ts` 无 error
- [ ] `npm run dev` 启动无报错（可选，依赖完整运行栈）

---

## 五、端到端链路

```
SN 生成路径:
  流转卡生产中/完工 → SN列表"批量生成SN"(输入cardId+数量) → generateSnForCard()
    → 按流转卡冗余(工单/物料)批量 insert N 条 pro_sn(sn_code=SN-cardNo-seq, status=IN_PRODUCTION, source=BATCH)
  或 手动建SN(组装场景: 指定 parentSnId=成品SN) → createSn() → parent_sn_id 关联

SN 状态流转:
  IN_PRODUCTION --完工--> COMPLETED --发货--> SHIPPED
       └--报废--> SCRAPPED(终态)
  COMPLETED --报废--> SCRAPPED

追溯路径:
  前向: SN → 流转卡工序链(pro_flow_card_step: 工序/操作员/数量/时间) → 子SN(parent_sn_id=当前SN)
  后向: SN → 沿 parent_sn_id 向上递归父SN链(组装层级)
  前端: trace.vue 调 traceSn(snId) → el-tree 双树可视化
```

---

## 六、风险与注意事项

| 风险 | 应对 |
|------|------|
| 批量生成 SN 序号并发重复 | `generateSnForCard` 加 `@Lock4j(keys={"#cardId"})`，序号基于该卡已有 SN 数计算 |
| SN 编号全局唯一 | DDL `uk_sn_code` 唯一索引 + 生成含 cardNo+seq，手动建 SN 校验唯一 |
| parent_sn 父子环（A→B→A） | 后向追溯限深 10 层 + 不允许将 SN 的 parent 设为其后代（createSn 时校验 parentSnId 不在当前 SN 的子树中） |
| 追溯工序链数据来源 | 复用 `pro_flow_card_step`（已含 operator/good_qty/时间），通过 card_id 关联，无需新表 |
| 设备追溯缺失 | eq 模块 Phase 3 未实现，前向追溯暂以工序 workcenter 代替，列为后续增强 |
| 不自动耦合 completeCard | 避免大批量 SN 自动生成 + 循环依赖（FlowCard→Sn），改为操作员触发；completeCard 不改 |
| ERP wm_sn 不同步 | 边界明确：MES pro_sn 仅生产追溯，完工入库由 ERP 自行生成 wm_sn，本周不跨库 |
| 不复用 bd_serial_number_rule | 跨库原子自增 current_seq 风险高，本地生成 SN 编号；规则引擎列为后续增强 |

---

## 七、明确排除项（本周不做）

- 复用 ERP `bd_serial_number_rule` 规则引擎生成 SN（跨库原子自增，后续增强）
- MES pro_sn ↔ ERP wm_sn 同步/完工入库联动（Phase 3 质量模块或后续）
- 设备维度追溯（依赖 eq 模块 Phase 3）
- SN 自动生成（耦合 completeCard）— 改为操作员触发
- SN 条码打印（依赖打印机硬件，后续）
- el-table → vxe-table 迁移（沿用 el-table 模式）
- Phase 3 全部模块（qc/eq/dc/spc）— Week 25 起进入

---

## 八、交付物汇总

| 交付物 | 类型 |
|--------|------|
| `mes-week24-05-sntrace.sql` | SQL（pro_sn DDL） |
| `ProSn.java`/BO/VO/Mapper | Java（4 文件） |
| `IProSnService` + `ProSnServiceImpl` | Java（CRUD + 生成 + 状态机 + 追溯） |
| `ProSnController.java` | Java（REST） |
| `sn.ts` | TS（前端 API） |
| `sn/index.vue` | Vue（列表+生成+状态操作） |
| `sn/trace.vue` | Vue（追溯树） |
| `mes-week24-18-menu.sql` | SQL（菜单 4080-4086） |

---

## 九、验证方法（端到端）

> 运行时验证依赖：`ry-vue`/`ry-erp`/`ry-mes`/`ry-iot` 四库 + Redis 就绪，并已执行 Week17/20/22/23 DDL 与菜单 SQL。

1. **DDL 执行**：`mes-week24-05-sntrace.sql` 建 `pro_sn`；`mes-week24-18-menu.sql` 注入菜单并为角色勾选 4080-4086。
2. **批量生成**：选一个已下达/生产中的流转卡 → "批量生成SN"(数量=3) → 列表出现 3 条 `SN-{cardNo}-0001~0003`，status=IN_PRODUCTION，关联工单/物料正确。
3. **手动建SN + 组装**：手动建一个成品 SN（无 parent）→ 再建一个组件 SN，parentSnId 指向成品 SN → 组件 SN 的 parent_sn_code 正确冗余。
4. **状态流转**：SN 完工 → COMPLETED → 发货 → SHIPPED；另一 SN 报废（prompt remark）→ SCRAPPED。对 SHIPPED 再调完工 → 报错"状态已变更"。
5. **追溯树**：列表"追溯"跳 trace 页 → 后向树显示父 SN 链（组装层级）→ 前向树显示当前 SN 的工序链 + 子 SN。
6. **唯一性**：手动建 SN 输入已存在 sn_code → 报错；批量生成并发两次（同卡）→ 序号不重复（Lock4j）。
7. **静态**：`mvn compile`（mes）+ `eslint` 均通过。

---

**文档生成时间**: 2026-06-22
**执行者**: AI Agent
**审核状态**: 待 Claude 自审 → 待用户审查
