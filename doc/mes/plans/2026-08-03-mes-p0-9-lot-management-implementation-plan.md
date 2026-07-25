# P0-9 批次(Lot)管理模块详细实施方案

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans（任务级 checkbox 跟踪）。Steps 用 `- [ ]` 标记。
>
> **实施日期**: 2026-08-03（Phase 4-A Week 28）
> **所属计划**: [2026-07-24-mes-p0-development-plan-v1.0.md](./2026-07-24-mes-p0-development-plan-v1.0.md) §3.9
> **状态**: ✅ 编码完成，`mvn compile` 通过（39 个 class），待运行时验证

## 实施结果摘要

| 任务 | 状态 | 交付物 |
|------|------|--------|
| 1. DDL | ✅ | [mes-p0-09-01-lot.sql](../sql/mes-p0-09-01-lot.sql) — 三表对齐既定约定 |
| 2. Entity/BO/VO/Mapper | ✅ | 12 个 Java 文件，字段对齐 DDL |
| 3. Service + 实现 | ✅ | 3 接口 + 3 实现，含状态机/FEFO/隔离/追溯/Lock4j |
| 4. Controller | ✅ | 3 REST 控制器，端点对齐 §四 |
| 5. 前端 API + 页面 | ✅ | 3 API + 3 页面，含 FEFO 推荐 UI |
| 6. 菜单权限 SQL | ✅ | [mes-p0-09-02-menu.sql](../sql/mes-p0-09-02-menu.sql) — 实际 ID 段 4006+4150~4168 |
| 7. 编译验证 | ✅ | `mvn compile` 通过，39 个 class 生成 |

> 注：菜单 ID 段实际分配为 4006(M目录) + 4150~4168(3 C页面 + 15 F按钮)，原计划估算 4166-4175 因 Week29(4125/4126) 占用后接续调整。

---

## 一、Context（背景与目标）

### 1.1 为什么做这一周

当前 MES 已实现 SN 级追溯（[ProSnServiceImpl](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProSnServiceImpl.java)），但缺乏批次（Lot）级管理。对标 MESA-10 谱系管理、鼎捷"批次追溯"、华天"FEFO 出库"，批次管理是 P0 级必做项，是后续 P0-3（进站出站校验）、P0-4（NCR 不良品隔离）、P0-1（物料拉动 FEFO）的依赖基础。

### 1.2 本周要解决的问题

1. **批次主档缺失**：物料批次号、生产日期、有效期、供应商批次、状态无处记录
2. **工单-批次绑定缺失**：工单领料时未绑定物料批次，无法追溯
3. **批次追溯链断裂**：批次→SN→工序→设备→人员→检验 全链路无法贯通
4. **FEFO 出库缺失**：仓库发料无先到期先出推荐，存在过期风险
5. **批次隔离缺失**：质量异常时无法冻结批次

### 1.3 关键边界（已确认）

- **3 张表**：`lt_lot`（批次主档）+ `lt_wo_consumption`（工单批次消耗）+ `lt_sn_binding`（SN-批次绑定）
- **批次状态机**：IN_STOCK → IN_USE → CONSUMED；分支 → QUARANTINED → SCRAPPED
- **QC 状态**：PENDING → PASS/FAIL（独立于批次状态，IQC 检验结果回写）
- **FEFO 算法**：按 `expiry_date ASC, produced_date ASC, lot_id ASC` 排序推荐，过滤 `status=IN_STOCK` 且 `qc_status=PASS`
- **批次隔离**：调用 `quarantine(lotId)` 后状态变更为 QUARANTINED，禁止出库
- **与 SN 集成**：SN 创建/绑定批次时，自动写入 `lt_sn_binding`
- **跨库查询**：物料信息冗余存储（material_code/material_name），不跨库查 ERP
- **编号生成**：`LOT-yyyyMMdd-3位序号`（uk 唯一索引兜底）

### 1.4 预期结果

- 物料批次可创建/查询/隔离/报废，状态机正确流转
- 工单领料时可绑定批次，记录消耗数量
- SN 可绑定批次，追溯链完整
- 出库时调用 FEFO 接口返回推荐批次列表
- 批次隔离后无法出库，QC 不通过的批次无法出库
- `mvn compile`（mes）与前端 `eslint` 通过

---

## 二、关键事实（探索结论，非假设）

| 事实 | 依据 |
|------|------|
| 既定约定：tenant_id VARCHAR(20) DEFAULT '000000'、del_flag CHAR(1)、状态列 VARCHAR(16)、数量 DECIMAL(14,4)、备注 VARCHAR(500)、日期 DATE | [mes-week25-06-quality.sql](file:///d:/workspace/nexus-erp/doc/mes/sql/mes-week25-06-quality.sql) + [mes-week26-07-equipment.sql](file:///d:/workspace/nexus-erp/doc/mes/sql/mes-week26-07-equipment.sql) |
| Entity 模式：`@Data @EqualsAndHashCode(callSuper=true) @TableName` extends `TenantEntity` + `@TableId` + `@TableLogic delFlag` | [ProSn.java](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/ProSn.java) |
| BO 模式：`@AutoMapper(target=..., reverseConvertGenerate=false)` extends `BaseEntity` + `@NotBlank/@NotNull` + `Long[] ids` | [ProSnBo.java](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/bo/ProSnBo.java) |
| VO 模式：`@ExcelIgnoreUnannotated @AutoMapper(target=...)` implements Serializable + `@ExcelProperty` | [ProSnVo.java](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/vo/ProSnVo.java) |
| Mapper 模式：`extends BaseMapperPlus<X, XVo>` | [ProSnMapper.java](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/mapper/ProSnMapper.java) |
| ServiceImpl 模式：`@Slf4j @RequiredArgsConstructor @DS("mes") @Service("xxxService")` + `@Lock4j` + `@Transactional` + `Wrappers.lambdaQuery` + 私有 `buildQueryWrapper` | [ProSnServiceImpl.java](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProSnServiceImpl.java) |
| Controller 模式：`@Validated @RequiredArgsConstructor @RestController @RequestMapping("/mes/xxx/yyy")` extends `BaseController` + `@SaCheckPermission` + `@Log` + `R<T>` + `TableDataInfo<T>` | [ProSnController.java](file:///d:/workspace/nexus-erp/ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/controller/ProSnController.java) |
| 菜单 SQL 模式：`INSERT INTO sys_menu VALUES (id, name, parent_id, sort, path, component, ...)` 父菜单 4xxx，子菜单 ID 接续 | [mes-week25-19-menu.sql](file:///d:/workspace/nexus-erp/doc/mes/sql/mes-week25-19-menu.sql) |
| 前端 API 模式：`request({ url, method, params/data })` + TS 接口定义 | [sn.ts](file:///d:/workspace/nexus-erp/plus-ui/src/api/mes/pro/sn.ts) |
| 前端页面模式：el-card + el-form 查询 + el-table 列表 + el-dialog 弹窗 + el-tag 状态 + `v-hasPermi` | [sn/index.vue](file:///d:/workspace/nexus-erp/plus-ui/src/views/mes/pro/sn/index.vue) |
| SQL 命名：`mes-weekXX-YY-xxx.sql` + 菜单 `mes-weekXX-ZZ-menu.sql` | [doc/mes/sql](file:///d:/workspace/nexus-erp/doc/mes/sql) 既有约定 |
| 菜单 ID 段：Week 27 用到 4145，P0-9 分配 4166-4175 | P0 计划书 §7.3 |

---

## 三、任务清单

| # | 任务 | 优先级 | 交付物 | 验收标准 | 状态 |
|---|------|--------|--------|----------|------|
| 1 | DDL | 高 | `mes-p0-09-01-lot.sql` | 三表对齐既定约定 | ✅ |
| 2 | Entity/BO/VO/Mapper | 高 | 12 个 Java 文件 | 字段对齐 DDL | ✅ |
| 3 | Service + 实现 | 高 | 3 接口 + 3 实现 | CRUD + 状态机 + FEFO + 隔离 + 追溯 | ✅ |
| 4 | Controller | 高 | 3 REST 控制器 | list/get/add/edit/del + 业务动作 | ✅ |
| 5 | 前端 API + 页面 | 高 | 3 API + 3 页面 | 列表/表单/FEFO 推荐/追溯 | ✅ |
| 6 | 菜单权限 SQL | 中 | `mes-p0-09-02-menu.sql` | 4006+4150~4168，父菜单 4006 | ✅ |
| 7 | 编译 + lint 验证 | 高 | - | mvn compile + eslint 通过 | ✅(后端) |

---

## 四、详细任务分解

### 任务 1：DDL

**File:** Create `doc/mes/sql/mes-p0-09-01-lot.sql`

三表设计（见 §六 DDL 详情）：
- `lt_lot`：批次主档（11 业务字段 + 7 审计字段）
- `lt_wo_consumption`：工单-批次消耗记录
- `lt_sn_binding`：SN-批次绑定关系

### 任务 2：Entity/BO/VO/Mapper（12 文件）

**Packages:** `com.nexus.mes.lt.*`（新建包，镜像 pro/ 布局）

每张表 4 文件（domain + bo + vo + mapper），共 12 个：

- `lt/domain/LtLot.java` / `LtWoConsumption.java` / `LtSnBinding.java`
- 对应 `domain/bo/`、`domain/vo/`、`mapper/`（各 3 文件）

### 任务 3：Service + 实现（6 文件）

**核心算法 — FEFO 推荐**：

```java
/**
 * FEFO 推荐: 按 expiry_date ASC, produced_date ASC, lot_id ASC 排序
 * 过滤: status=IN_STOCK 且 qc_status=PASS 且 material_id 匹配
 */
public List<LtLotVo> recommendFefo(Long materialId, BigDecimal requiredQty) {
    List<LtLotVo> lots = baseMapper.selectVoList(Wrappers.<LtLot>lambdaQuery()
        .eq(LtLot::getMaterialId, materialId)
        .eq(LtLot::getStatus, STATUS_IN_STOCK)
        .eq(LtLot::getQcStatus, QC_STATUS_PASS)
        .gt(LtLot::getQuantity, BigDecimal.ZERO)
        .orderByAsc(LtLot::getExpiryDate)
        .orderByAsc(LtLot::getProducedDate)
        .orderByAsc(LtLot::getLotId));
    // 累加直到满足 requiredQty
    // ...
}
```

**状态机**：

```
                          ┌─────────────────┐
                          │   IN_STOCK      │ ← 创建
                          └────────┬────────┘
                                   │ 领料出库
                                   ▼
                          ┌─────────────────┐
              ┌───────────>│   IN_USE        │
              │            └────────┬────────┘
              │                     │ 全部消耗
              │                     ▼
              │            ┌─────────────────┐
              │            │   CONSUMED      │ ← 终态
              │            └─────────────────┘
              │
              │ 质量异常
              ▼
    ┌─────────────────┐
    │  QUARANTINED    │ ← 隔离
    └────────┬────────┘
             │ 判定报废
             ▼
    ┌─────────────────┐
    │   SCRAPPED       │ ← 终态
    └─────────────────┘
```

### 任务 4：Controller（3 个）

| 端点 | 权限 | 说明 |
|------|------|------|
| GET /mes/lt/lot/list | mes:lt:lot:list | 批次列表 |
| GET /mes/lt/lot/{lotId} | mes:lt:lot:query | 批次详情 |
| POST /mes/lt/lot | mes:lt:lot:add | 新增批次 |
| PUT /mes/lt/lot | mes:lt:lot:edit | 编辑批次 |
| DELETE /mes/lt/lot/{ids} | mes:lt:lot:remove | 删除批次 |
| PUT /mes/lt/lot/quarantine/{lotId} | mes:lt:lot:quarantine | 隔离批次 |
| PUT /mes/lt/lot/scrap/{lotId} | mes:lt:lot:scrap | 报废批次 |
| GET /mes/lt/lot/fefo/{materialId} | mes:lt:lot:fefo | FEFO 推荐 |
| GET /mes/lt/lot/trace/{lotId} | mes:lt:lot:trace | 批次追溯链 |
| POST /mes/lt/consumption | mes:lt:consumption:add | 工单批次消耗 |
| POST /mes/lt/binding | mes:lt:binding:add | SN-批次绑定 |

### 任务 5：前端 3 API + 3 页面

- `api/mes/lt/lot.ts` / `consumption.ts` / `binding.ts`
- `views/mes/lt/lot/index.vue` — 批次列表 + 新增/编辑 + 隔离/报废按钮
- `views/mes/lt/consumption/index.vue` — 工单消耗记录
- `views/mes/lt/binding/index.vue` — SN-批次绑定

### 任务 6：菜单 SQL

父菜单 **4006（主数据，需新建）**。菜单 ID 段 **4166-4175**。

---

## 五、端到端链路

```
批次管理:
  新建批次(LOT-20260803-001/物料/数量/生产日期/有效期/供应商批次)
    → IQC 检验 → qc_status=PASS
    → 状态 IN_STOCK

工单领料:
  工单需要物料 → 调用 FEFO 接口 → 返回推荐批次列表(按到期升序)
    → 操作员确认 → 创建 lt_wo_consumption 记录(扣减批次数量)
    → 批次剩余数量 = 0 时状态自动变更为 CONSUMED

SN 绑定:
  SN 创建/流转 → 绑定批次 → 写入 lt_sn_binding
    → 追溯时可从 SN → 批次 → 工序 → 设备 → 人员 → 检验 全链路

批次隔离:
  发现批次质量异常 → 调用 quarantine → 状态变更为 QUARANTINED
    → FEFO 推荐排除该批次 → 无法出库

批次追溯:
  调用 trace(lotId) → 返回 { lot, consumptions, snList, woList }
```

---

## 六、DDL 详情

### 6.1 lt_lot — 批次主档

```sql
USE `ry-mes`;

CREATE TABLE lt_lot (
    lot_id           BIGINT NOT NULL COMMENT '批次ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    lot_code         VARCHAR(64) NOT NULL COMMENT '批次编码',
    material_id      BIGINT NOT NULL COMMENT '物料ID',
    material_code   VARCHAR(64) COMMENT '物料编码(冗余)',
    material_name   VARCHAR(128) COMMENT '物料名称(冗余)',
    supplier_id      BIGINT COMMENT '供应商ID',
    supplier_name   VARCHAR(128) COMMENT '供应商名称(冗余)',
    supplier_lot    VARCHAR(64) COMMENT '供应商批次号',
    quantity         DECIMAL(14,4) NOT NULL COMMENT '批次数量',
    consumed_qty    DECIMAL(14,4) DEFAULT 0 COMMENT '已消耗数量',
    produced_date    DATE COMMENT '生产日期',
    expiry_date      DATE COMMENT '有效期',
    status           VARCHAR(16) DEFAULT 'IN_STOCK' COMMENT 'IN_STOCK/IN_USE/CONSUMED/QUARANTINED/SCRAPPED',
    qc_status        VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/PASS/FAIL',
    remark           VARCHAR(500) COMMENT '备注',
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (lot_id),
    UNIQUE INDEX uk_lot_code (lot_code),
    INDEX idx_material (material_id),
    INDEX idx_status (status),
    INDEX idx_expiry (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES批次主档';
```

### 6.2 lt_wo_consumption — 工单批次消耗

```sql
CREATE TABLE lt_wo_consumption (
    consumption_id   BIGINT NOT NULL COMMENT '消耗记录ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    wo_id            BIGINT NOT NULL COMMENT '工单ID',
    wo_no           VARCHAR(64) COMMENT '工单编号(冗余)',
    lot_id           BIGINT NOT NULL COMMENT '批次ID',
    lot_code        VARCHAR(64) COMMENT '批次编码(冗余)',
    material_id      BIGINT NOT NULL COMMENT '物料ID',
    material_code   VARCHAR(64) COMMENT '物料编码(冗余)',
    consumed_qty     DECIMAL(14,4) NOT NULL COMMENT '消耗数量',
    consumed_time    DATETIME NOT NULL COMMENT '消耗时间',
    operator_id      BIGINT COMMENT '操作员ID',
    operator_name   VARCHAR(64) COMMENT '操作员姓名(冗余)',
    remark           VARCHAR(500),
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (consumption_id),
    INDEX idx_wo_id (wo_id),
    INDEX idx_lot_id (lot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单批次消耗记录';
```

### 6.3 lt_sn_binding — SN-批次绑定

```sql
CREATE TABLE lt_sn_binding (
    binding_id       BIGINT NOT NULL COMMENT '绑定ID(主键)',
    tenant_id        VARCHAR(20) NOT NULL DEFAULT '000000',
    sn_id            BIGINT NOT NULL COMMENT 'SN ID',
    sn_code         VARCHAR(64) COMMENT 'SN编码(冗余)',
    lot_id           BIGINT NOT NULL COMMENT '批次ID',
    lot_code        VARCHAR(64) COMMENT '批次编码(冗余)',
    material_id      BIGINT NOT NULL COMMENT '物料ID',
    bound_time       DATETIME NOT NULL COMMENT '绑定时间',
    bound_by         BIGINT COMMENT '绑定人ID',
    create_dept BIGINT, create_by BIGINT, create_time DATETIME, update_by BIGINT, update_time DATETIME, del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (binding_id),
    INDEX idx_sn_id (sn_id),
    INDEX idx_lot_id (lot_id),
    UNIQUE INDEX uk_sn_material (sn_id, material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SN批次绑定关系';
```

---

## 七、风险与注意事项

| 风险 | 应对 |
|------|------|
| 批次数量并发扣减超卖 | `@Lock4j(keys = {"#lotId"})` 锁定批次 + 数据库乐观锁版本字段 |
| FEFO 推荐性能 | 索引 `idx_material + idx_expiry`；查询限制 LIMIT 100 |
| 批次隔离后已消耗部分无法回滚 | 文档明确"隔离仅影响未消耗部分"，已消耗记录保留追溯 |
| 状态机校验失败 | 所有状态转换使用 `.eq(status, currentStatus)` 条件更新，rows=0 时抛异常 |
| 与既有 ProSn 集成改动 | 仅新增 `lt_sn_binding` 表，不修改 pro_sn 表结构；通过 Service 调用集成 |
| 跨库查物料信息 | 冗余 material_code/material_name，避免跨库查询 |
| QC 状态回写 | 本周仅预留 qc_status 字段，IQC 自动回写列为后续增强 |

---

## 八、明确排除项（本周不做）

- IQC 自动回写 qc_status（仅预留字段，本周手动维护）
- 批次与 ERP 库存账实时同步（本周仅 MES 内部批次账）
- 批次条码生成与打印（后续 PDA 端）
- 批次预警定时任务（临期/超期，后续增强）
- 多层级批次（子批次/拆批/并批，v1.1）
- 批次与 P0-1 物料拉动的深度集成（FEFO 接口契约就绪，深度集成待 P0-1 实施）

---

## 九、交付物汇总

| 交付物 | 类型 | 路径 |
|--------|------|------|
| `mes-p0-09-01-lot.sql` | SQL | `doc/mes/sql/mes-p0-09-01-lot.sql` |
| LtLot/LtWoConsumption/LtSnBinding Entity/BO/VO/Mapper | Java×12 | `ruoyi-mes/.../lt/{domain,domain/bo,domain/vo,mapper}/` |
| 3 Service + 3 实现 | Java×6 | `ruoyi-mes/.../lt/{service,service/impl}/` |
| 3 Controller | Java×3 | `ruoyi-mes/.../lt/controller/` |
| `lot.ts`/`consumption.ts`/`binding.ts` | TS×3 | `plus-ui/src/api/mes/lt/` |
| `lot/index.vue`/`consumption/index.vue`/`binding/index.vue` | Vue×3 | `plus-ui/src/views/mes/lt/` |
| `mes-p0-09-02-menu.sql` | SQL | `doc/mes/sql/mes-p0-09-02-menu.sql` |

---

## 十、验证方法（端到端）

> 运行时验证依赖：`ry-vue`/`ry-mes` 两库 + Redis 就绪。

1. **DDL 执行**：建三表 + 菜单 SQL 注入 4166-4175
2. **批次创建**：新增批次（LOT-20260803-001/物料A/100/2026-08-01/2026-09-01），状态 IN_STOCK
3. **QC 通过**：手动更新 qc_status=PASS
4. **FEFO 推荐**：再创建 2 个不同有效期的批次，调用 `/fefo/{materialId}` 返回按到期升序的列表
5. **工单消耗**：创建消耗记录（消耗 30），批次 consumed_qty=30，quantity 不变
6. **SN 绑定**：创建 SN-批次绑定，查询追溯链返回 sn+lot+consumptions
7. **隔离**：调用 `/quarantine/{lotId}`，状态变更为 QUARANTINED，FEFO 推荐排除该批次
8. **报废**：调用 `/scrap/{lotId}`，状态变更为 SCRAPPED（终态）
9. **静态**：`mvn compile`（mes）+ `eslint` 均通过

---

**文档生成时间**: 2026-07-24
**执行者**: AI Agent
**审核状态**: ✅ 编码完成 + 后端编译通过，待运行时验证（DDL 执行 + 菜单注入 + 端到端联调）

---

## 十一、实施记录（编码阶段补充）

### 11.1 编译修复

- **问题**：`LtSnBindingServiceImpl.java:85` 调用 `bo.getBoundBy()`，但 `LtSnBindingBo` 未声明 `boundBy` 字段，编译报错"找不到符号"。
- **修复**：`boundBy`（绑定人）应取当前登录用户，非请求参数。引入 `com.nexus.common.satoken.utils.LoginHelper`，改为 `binding.setBoundBy(LoginHelper.getUserId())`。
- **连带改进**：`LtWoConsumptionServiceImpl` 的 `operatorId` 同理，改为"优先取 BO 传入，未传则取 `LoginHelper.getUserId()`"，与项目内 `DcDataRecordServiceImpl.setCollectBy(LoginHelper.getUserId())` 模式一致。

### 11.2 菜单 ID 段调整

| 项 | 计划估算 | 实际分配 |
|----|----------|----------|
| 父目录 | 4006 | 4006 ✅ |
| 子菜单段 | 4166-4175 | 4150-4168 |
| 原因 | — | Week29 已用到 4125/4126，接续从 4150 起更安全；本脚本 1 M + 3 C + 15 F 共 19 条 |

### 11.3 交付物清单（实际）

- 后端：`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/lt/` 下 domain(3) + bo(3) + vo(3) + mapper(3) + service(3 接口) + service/impl(3) + controller(3) = 21 个 Java 文件
- 前端：`plus-ui/src/api/mes/lt/`(3 ts) + `plus-ui/src/views/mes/lt/`(3 vue)
- SQL：`mes-p0-09-01-lot.sql`(DDL) + `mes-p0-09-02-menu.sql`(菜单)
- 编译产物：`ruoyi-modules/ruoyi-mes/target/classes/com/nexus/mes/lt/` 39 个 class
