# MES 备件与 ERP 库存打通（MES-EQ-04）功能补齐报告（2026-09-20）

> 对应审计: `doc/audit/2026-09-20-audit-mes-eq.md` 的 **MES-EQ-04**（§2 / §4 第 6 项），
> 以及 `doc/audit/2026-09-20-fix-mes-eq.md` §2.2 的「只出方案未动代码」欠账。
> 分支: `feature/erp-mes-iot-audit-fix` | 执行规范: `doc/audit/2026-09-20-修复执行规范.md`

## 1. 修复清单

| 审计编号 | 修复内容 | 涉及文件 | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| **MES-EQ-04 ①** | **物料映射**：`eq_spare_part` 新增 `material_id`（→`bd_material`）+ `warehouse_id`（→`wm_warehouse`）+ `idx_material_id`；新增/编辑备件时经 ERP 只读 Mapper 校验**物料存在且 `status=1` 启用**、仓库存在；提供按 `spare_code = material_code` 回填的存量迁移 SQL（含前置检查、重复物料检查、影响行数核对） | `eq/domain/EqSparePart.java`、`eq/domain/bo/EqSparePartBo.java`、`eq/domain/vo/EqSparePartVo.java`、`eq/service/impl/EqSparePartServiceImpl.java`、`script/sql/update/2026-09-20-mes-spare-erp.sql` §1/§3 | **是**（2 列 + 1 索引） | `EqSparePartServiceImplTest#generatePurchaseRequestRejectsUnmappedSpare` 等 |
| **MES-EQ-04 ②** | **出入库统一走 ERP（采用方案② MQ）**：`stockTransaction`（入库/领用/退库）在同一事务内写 `eq_spare_erp_sync` 过账台账，事务提交后发 `QueueConstants.MES_SPARE_ERP_QUEUE`；ERP 侧新增可复用过账入口 `IWmSpareStockPostingService` 落到 `wm_material_stock` + `wm_material_stock_tx`。**幂等**＝`eq_spare_erp_sync.uk_tenant_biz_key` 唯一约束 + ERP 侧 Redis 原子占位 + 流水查重；**原子扣减**＝复用 `deductStockAvailable`（可用量守卫）/`addStock`（`setSql` + `version`），未新增 Java 先算后写；**失败可见**＝投递失败写 `FAILED`+`last_error`，提供台账列表与重投端点，消费端 ERROR 日志 + 有限重投 | `eq/mq/EqSpareErpMqPublisher.java`、`eq/domain/EqSpareErpSync.java`、`eq/mapper/EqSpareErpSyncMapper.java`、`eq/service/impl/EqSparePartServiceImpl.java`、`ruoyi-common-mq/.../QueueConstants.java`（仅新增 1 个常量）、`wm/service/IWmSpareStockPostingService.java`、`wm/service/impl/WmSpareStockPostingServiceImpl.java`、`wm/service/mq/MesSpareErpConsumer.java`、`wm/service/mq/MesSpareErpMessage.java`、`wm/service/mq/WmSpareStockDedupGuard.java`、增量 SQL §2 | 是（新增台账表） | `WmSpareStockPostingServiceImplTest`（7 例：重复消息幂等 ×2、可用量扣减、不足整单回滚、无可用行、入库 version=0、RETURN 按入库） |
| **MES-EQ-04 ③** | **口径一致**：`eq_spare_part.current_stock` 明确降级为「ERP 库存的**派生冗余缓存**（只读视图语义）」——同步方向 ERP→MES、时机＝①出入库按同一增量本地维护 ②ERP 落账确认后（`retryErpSync`）以 ERP 可用量整体覆盖回写；列注释与实体/服务 javadoc 同步改写。`selectLowStock` 改为读 ERP 可用量 `availableOf`（在库 − 已预留，且 `frozen_flag=0`），列表/详情同样填充 `erpAvailableStock` | `eq/domain/EqSparePart.java`、`eq/service/impl/EqSparePartServiceImpl.java`、`eq/mapper/ErpSpareStockQueryMapper.java`、增量 SQL §1（列注释更正） | 是（注释） | `selectLowStockReadsErpAvailableInsteadOfMesCache`、`erpAvailableSqlExcludesReservedAndFrozenRows`（SQL 契约） |
| **MES-EQ-04 ④** | **采购建议**：新增端点 `POST /mes/eq/spare-part/purchase-request`（权限 `mes:eq:spare:purchase`），把低库存备件行经 MQ 交由 **ERP 采购申请服务** `IPuPurchaseRequisitionService.insertByBo` 生成 `DRAFT` 请购单（单号 `MESSP+yyyyMMdd+4位当日序号`，来源 `MES_SPARE_LOW_STOCK`）；未映射物料显式拒绝并列出编码 | `eq/controller/EqSparePartController.java`、`eq/service/impl/EqSparePartServiceImpl.java`、`wm/service/mq/MesSpareErpConsumer.java`、增量 SQL §4（菜单/权限种子） | 是（DML 种子） | `generatePurchaseRequestPublishesPurchaseMessage`、`generatePurchaseRequestRejectsUnmappedSpare` |
| **MES-EQ-04 ⑤** | **DDL**：`eq_spare_part.material_id/warehouse_id` + `idx_material_id`、`eq_spare_erp_sync` 新表（含 `uk_tenant_biz_key`）、`sys_menu` 权限种子、可选 `wm_material_stock_tx` 唯一索引（默认注释待确认） | `script/sql/update/2026-09-20-mes-spare-erp.sql` | 是 | — |
| **附：映射缺失行为明确** | 严格模式（默认开启，`nexus.mes.eq.spare.erp-strict`，配置项无需改 yml）下，未映射 `material_id`/`warehouse_id` 的备件出入库**拒绝并提示**（不静默只改 MES 台账）；`selectLowStock` 对未映射备件以 `lowStockBasis=NOT_MAPPED` 显式返回 | `eq/service/impl/EqSparePartServiceImpl.java` | 否 | `stockTransactionRejectsUnmappedSpareWithoutTouchingAnyLedger`、`stockTransactionRejectsMissingWarehouseMapping`、`selectLowStockFlagsUnmappedSpareExplicitly` |

**改动规模**：新增主代码 8 个文件、修改主代码 6 个文件（MES 5 + 公共 1）、新增增量脚本 1 个、新增测试类 1 个 + 重写测试类 1 个（**共 20 例，MES 13 + ERP 7，全部通过**）。

## 2. 未修复 / 部分修复项（含理由与建议）

| 项 | 状态 | 理由与建议 |
|---|---|---|
| **未映射备件的存量数据** | 部分 | 增量脚本只做「可回填的按编码回填」；`spare_code ≠ material_code` 的备件需人工映射（严格模式下这些备件出入库会被拒绝，这是有意为之：避免继续维护两套账）。脚本 §3.2 给出了未映射清单查询。 |
| **`warehouse_id` 存量回填** | 未做 | 需要业务决定「备件默认仓库」，脚本 §3.1 留了带 `@warehouseId` 占位的 UPDATE（默认注释）。 |
| **MES 侧定时对账/自动重投任务** | 未做 | 本轮提供**手动**对账与重投（`GET /mes/eq/spare-part/erp-sync?syncStatus=FAILED` + `POST /mes/eq/spare-part/erp-sync/{syncId}/retry`，重投前先查 ERP 流水，已落账则只标记 `SYNCED` 并回写派生缓存）。按用户决策「定时任务需 `@Scheduled` 兜底 + Lock4j」，新增任务应走 `SchedulerFallbackUtils` 模板，本批次未纳入以控制 diff；建议单开（可复用 `StockAlertTask` 的写法）。 |
| **`eq_spare_stock_log` 增加 `erp_sync_status` 列** | 未做 | 改为独立台账 `eq_spare_erp_sync`（避免改动既有流水表的语义与索引），代价是库存流水页看不到同步状态——前端如需展示，建议按 `log_id` 关联台账（见 §前端需求）。 |
| **`wm_material_stock_tx` 唯一索引** | 未执行 | 与 `fix-wm-core` 一致：对共享大表加唯一索引需先确认存量无重复（脚本 §5 给了前置检查 SQL），默认注释、由主代理确认后执行。当前幂等由「Redis 占位 + 流水查重」保证。 |
| **`wm_material_stock` 唯一维度键** | 未做 | ERP-WM-08 的范围，本批次未纳入；备件入库在「同物料同仓库」维度上按既有行累加（含冻结行），不新建重复维度行，规避了 WM-07/08 的重复行触发条件。 |
| **备件成本核算进 ERP** | 未做 | 过账只写数量与流水（`unit_cost` 取自库存行快照），未触发 `IInventoryValuationService` 计价；审计「维修备件成本无法进 ERP 成本核算」只解决到「数量账统一 + 流水可追溯」。建议单开：备件入库按移动平均计价。 |
| **审计 §4 第 7 项「备件正式退库单」** | 部分 | `stockTransaction` 已支持 `RETURN` 类型（ERP 侧按入库过账，`MES_SPARE_IN` 流水），但未加「退库单」实体与前端入口（`TXN_RETURN` 仅经 `/stock` 端点传入）。 |
| **ERP 采购申请的申请人/部门** | 部分 | 消费者生成的请购单 `applicantName='MES备件预警'`、`applicantId` 为空（MQ 线程无登录用户上下文）；如需精确到人，应在消息体携带操作人并在消费端回填。 |

## 3. 主脚本合并清单（本代理未编辑 `ry-*.sql`）

### 3.1 `script/sql/ry-mes.sql`（库 `ry-mes`）

**(a) `eq_spare_part` 增加映射列与索引**
- 锚点（`eq_spare_part` 建表段，`script/sql/ry-mes.sql:491` 附近已存在的整行）：
```
  `current_stock` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '当前库存(冗余, 出入库流水维护)',
```
- 用下列 3 行**替换**该行（注释同步改为派生缓存口径）：
```
  `current_stock` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '当前库存(ERP库存的派生冗余缓存; 权威账本在 wm_material_stock, 可用量=在库-已预留且frozen_flag=0)',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP物料ID(bd_material.material_id), NULL=未映射',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP仓库ID(wm_warehouse.warehouse_id), 过账目标仓库',
```
- 并在该表 `INDEX idx_category(category)`（`:501`）之后插入：
```
  INDEX `idx_material_id`(`material_id`) USING BTREE,
```

**(b) 新增表 `eq_spare_erp_sync`**
- 锚点：`eq_spare_stock_log` 建表段结束行（`script/sql/ry-mes.sql:534`）：
```
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES备件库存流水' ROW_FORMAT = Dynamic;
```
- 在该行之后插入增量脚本 §2 的完整 `CREATE TABLE eq_spare_erp_sync`（含 `UNIQUE INDEX uk_tenant_biz_key(tenant_id, biz_key)`）。

### 3.2 `script/sql/ry-vue.sql`（库 `ry-vue`）

**(a) `sys_menu` 补 `mes:eq:spare:purchase` 按钮权限**
- 锚点（`mes:eq:spare:stock` 按钮行，搜索整行文本 `'mes:eq:spare:stock'`）：
```
INSERT INTO `sys_menu` VALUES (<id>, '备件出入库', <parentId>, <orderNum>, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:spare:stock', '#', 103, 1, '<时间>', NULL, NULL, '');
```
- 在该行之后插入（`menu_id` 请主代理确认未占用后分配；本代理选 **4128**，`parent_id` 取锚点行的 `parent_id`）：
```
INSERT INTO `sys_menu` VALUES (4128, '备件采购申请', <parentId>, 9, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:spare:purchase', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'MES-EQ-04 低库存一键生成 ERP 采购申请');
```

### 3.3 `script/sql/ry-erp.sql`（库 `ry-erp`）—— **可选，默认不合并**
- `wm_material_stock_tx` 增加 `UNIQUE INDEX uk_mes_spare_biz(tenant_id, source_doc_type, source_doc_code)`：需先确认存量无重复（脚本 §5 前置检查），否则会加索引失败。当前幂等不依赖该索引。

## 4. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

| 日期 | 库 | 表 | 变更类型 | 变更内容 | 脚本 | 状态 |
|---|---|---|---|---|---|---|
| 2026-09-20 | `ry-mes` | `eq_spare_part` | 新增字段 ×2 + 索引 | `material_id bigint NULL`（ERP 物料映射）、`warehouse_id bigint NULL`（ERP 仓库）、`INDEX idx_material_id(material_id)`；`current_stock` 列注释更正为「ERP 库存的派生冗余缓存」 | `script/sql/update/2026-09-20-mes-spare-erp.sql` §1 | **必须执行**（实体已含新列，缺列报 `Unknown column`） |
| 2026-09-20 | `ry-mes` | `eq_spare_erp_sync` | 新增表 | MES→ERP 过账同步台账；`UNIQUE INDEX uk_tenant_biz_key(tenant_id, biz_key)`；索引 `idx_spare_id`、`idx_sync_status` | 同上 §2 | **必须执行** |
| 2026-09-20 | `ry-mes` / `ry-erp` | `eq_spare_part` ← `bd_material` | 数据迁移（DML） | 按 `spare_code = material_code AND tenant_id AND del_flag='0' AND status=1` 回填 `material_id`（跨库 UPDATE，需同实例） | 同上 §3 | **需人工核对影响行数后执行** |
| 2026-09-20 | `ry-vue` | `sys_menu` | DML | 新增按钮 `menu_id=4128`，`perms='mes:eq:spare:purchase'`，`parent_id` 取 `mes:eq:spare:stock` 的父菜单 | 同上 §4 | **必须执行**（否则非超管 403） |
| 2026-09-20 | `ry-erp` | `wm_material_stock_tx` | 新增唯一索引（**建议，未执行**） | `UNIQUE KEY uk_mes_spare_biz(tenant_id, source_doc_type, source_doc_code)` | 同上 §5 | **待确认**：需存量无重复 |

- 增量脚本：**`script/sql/update/2026-09-20-mes-spare-erp.sql`**（幂等：`information_schema` 预检 + `PREPARE` 动态 DDL + `CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE` + `WHERE material_id IS NULL` 守卫）。
- **执行前提**：§1/§2 必须在应用启动前执行；§3 要求 `ry-mes`/`ry-erp` 同实例可跨库读写。
- **无需清 Redis 缓存**（不涉及字典/缓存键）。

## 5. 前端需求（`plus-ui/**` 不在本代理所有权内，请前端代理承接）

1. **备件主数据表单**：新增「ERP 物料」选择器（调 ERP 物料下拉，建议只列 `status=1`）与「ERP 仓库」选择器；列表增加 `materialId/erpMapped` 列（未映射高亮，提示"未映射 ERP 物料，无法出入库"）。
2. **列表/详情**：增加「ERP 可用量」列（`erpAvailableStock`）与口径提示；`lowStock` 判定改为展示 `lowStockBasis`（`ERP_AVAILABLE`/`NOT_MAPPED`/`ERP_UNAVAILABLE`），其中 `ERP_UNAVAILABLE` 表示 ERP 不可达已降级为 MES 派生缓存。
3. **低库存页**：增加「一键生成采购申请」按钮 → `POST /mes/eq/spare-part/purchase-request`（body `{spareIds, remark}`），成功后提示"已提交 ERP 采购申请生成请求"，并引导到 ERP 采购申请列表查看草稿单。
4. **过账台账页（可选但推荐）**：`GET /mes/eq/spare-part/erp-sync?syncStatus=FAILED` + `POST /mes/eq/spare-part/erp-sync/{syncId}/retry`，展示 `syncStatus/retryCount/lastError/lastPublishTime/erpAvailableAfter`，作为「失败可见」的人工兜底入口。
5. **库存流水页**：如需显示同步状态，按 `logId` 关联 `eq_spare_erp_sync`（当前流水 VO 未含该字段）。
6. **api 层**：`plus-ui/src/api/mes/eq/sparePart.ts`（或等价文件）补 `purchaseRequest`、`erpSyncList`、`retryErpSync` 三个函数（审计 MES-EQ-26 同类问题：后端有端点前端无函数）。

## 6. 口径说明（写进代码注释，供评审对齐）

1. **唯一账本**：ERP `wm_material_stock` 是实物库存的唯一权威账本；MES `eq_spare_part.current_stock` 是**派生冗余缓存**，同步方向 **ERP → MES**，时机＝① 出入库时按同一增量本地维护（保证页面连续可用）② ERP 落账确认后由 `retryErpSync` 用 ERP 可用量整体覆盖。
2. **可用量**：`availableOf = SUM(stock_quantity − IFNULL(reserved_quantity,0))`，且**仅统计 `frozen_flag = 0`** 的行；低库存、采购建议、过账后回写全部用该口径（与 `ERP-WM-02`/`ERP-WM-09` 统一）。
3. **为什么走 MQ（方案②）而非直接调用 ERP 服务（方案①）**：`ruoyi-mes` 的 pom **不依赖** `ruoyi-erp`（MES 读 ERP 一贯走 `DynamicDataSourceContextHolder` + 只读 Mapper 或 MQ），直接注入 ERP 服务会引入模块环依赖并跨 `@DS` 事务边界；MQ 还天然提供重投语义，配合业务幂等键即可满足幂等与失败可见。
4. **幂等键**：`bizKey = EQSP:<tenantId>:<logId>`（MES 库存流水主键，雪花），MES 侧 `uk_tenant_biz_key` 唯一约束，ERP 侧 Redis 原子占位（7 天）+ `wm_material_stock_tx(source_doc_type='MES_SPARE', source_doc_code=bizKey)` 查重。重复消息不会二次增减库存。
5. **退库**：`RETURN` 与 `IN` 同为入库方向，ERP 流水类型统一 `MES_SPARE_IN`，MES 流水类型保留 `RETURN` 以便区分来源。
6. **跨库租户**：MES 侧 ERP 只读 SQL 显式携带 `tenant_id`；ERP 消费者用 `TenantHelper.dynamic(tenantId)` 包裹，避免 `tenant_id` 落 0。

## 7. 验证记录（命令 + 结果）

```powershell
mvn -pl ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-erp -am test -DskipTests=false `
    "-Dtest=EqSparePartServiceImplTest,WmSpareStockPostingServiceImplTest" `
    "-Dsurefire.failIfNoSpecifiedTests=false"
```

**实测结果（BUILD SUCCESS，20 例全绿）**：

```
[INFO] Running com.nexus.erp.wm.service.impl.WmSpareStockPostingServiceImplTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.970 s
[INFO] Building ruoyi-mes 5.6.0                                         [36/36]
[INFO] Running com.nexus.mes.eq.service.impl.EqSparePartServiceImplTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.711 s
[INFO] BUILD SUCCESS
```

> 说明：`ruoyi-erp` 主代码 971 个源文件、`ruoyi-mes` 主代码 506 个源文件随 `-am` 全量编译通过（含上游 33 个模块），
> 说明本次新增的 `QueueConstants` 常量、ERP `wm/service/**` 新增过账入口、MES `eq` 改动未破坏模块间编译契约。
> 首轮编译暴露 1 个真实缺陷并已修复：Java **不支持 import 别名**（`MesSpareErpConsumer` 误用 `import ... Queue as RabbitQueue`）。
> 第二轮暴露 2 个测试断言缺陷并已修复：① `ArgumentCaptor` 捕获的是对象引用，`markSync` 回写后状态已变（改为断言 `updateById` 捕获值）；
> ② `selectVoById` 现被调用两次（入口 + 提交后组装消息体），`verify` 默认 `times(1)` 失败（改为 `atLeastOnce()`）。
> 测试类均带 `@Tag("local")`+`@Tag("dev")`（MES）/ `@Tag("dev")`（ERP）；`insert(any())` 全部写作 `any(实体.class)`；
> 未对 `RedisUtils` 做静态打桩（幂等闸门抽成可注入的 `WmSpareStockDedupGuard`）；静态 lambda 列缓存用 `TableInfoHelper.initTableInfo`。
> 注：ERP 侧单测运行日志中可见一条 `ERROR ... 投递失败(已记账, 待重投)`，这是 `stockTransactionMarksSyncFailedWhenPublishFails`
> 用例的**预期日志**（断言 `sync_status=FAILED` 且 `last_error` 非空），非失败。

| 测试类 | 模块 | `@Tag` | 用例数 | 覆盖点（修复前必失败） |
|---|---|---|---|---|
| `com.nexus.mes.eq.service.impl.EqSparePartServiceImplTest` | ruoyi-mes | `local`+`dev` | 13 | 领用/入库触发 ERP 过账且幂等键稳定（`EQSP:000000:999`）；投递失败置 `FAILED` 且留失败原因；映射缺失/缺仓库拒绝且**零副作用**；库存不足拒绝且无流水/无台账/无消息；低库存按 ERP 可用量判定（MES 缓存 999 仍判低）；未映射备件 `NOT_MAPPED` 显式返回；ERP 可用量 SQL 契约（`reserved_quantity` + `frozen_flag = 0` + 显式 `tenant_id`）；采购建议投递与未映射拒绝；原有 MES-EQ-14 流水回读 3 例回归保留 |
| `com.nexus.erp.wm.service.impl.WmSpareStockPostingServiceImplTest` | ruoyi-erp | `dev` | 7 | 重复消息（Redis 占位命中 / 流水已存在）**不再动库存**；出库只按可用量扣减（在库 10/预留 8 只扣 2）且流水 before/after 正确；可用量不足整单回滚（不写流水 + 释放幂等占位）；无可用行拒绝；入库新建库存行 `version = 0`；`RETURN` 按入库过账 |

## 8. 风险与回滚说明

| 风险 | 说明 | 缓解 / 回滚 |
|---|---|---|
| **R1（高）未执行 DDL 即启动** | 实体/VO 已含 `material_id`/`warehouse_id`，且服务会读写 `eq_spare_erp_sync` → 缺列/缺表时备件域查询与出入库全部报错 | 上线前执行增量脚本 §1、§2；回滚：`DROP TABLE eq_spare_erp_sync` + `DROP COLUMN material_id, warehouse_id` |
| **R2（高）严格模式导致未映射备件无法出入库** | 默认 `nexus.mes.eq.spare.erp-strict=true`，回填未完成时老备件出入库会被拒绝（这是"不再维护两套账"的目标行为） | 过渡期可把 `nexus.mes.eq.spare.erp-strict` 设为 `false`（**未写入 yml**，需运维配置）；或先完成 §3 回填 + 人工映射 |
| **R3 ERP 不可达时低库存降级** | `ERP_UNAVAILABLE` 时低库存按 MES 派生缓存判定并显式标注口径，不静默 | 前端按 `lowStockBasis` 提示；恢复后重新查询即回正 |
| **R4 MQ 未启用/队列未声明** | 消费端用 `@RabbitListener(queuesToDeclare=...)` 声明队列，生产端发默认交换机；若 `nexus.mq.enabled=false` 则 `RabbitTemplate` 缺失、Bean 装配失败 | 与项目其它 MQ 消费者同风险等级（全仓大量使用）；如需关闭 MQ 需同时禁用本模块 publisher/consumer（`@ConditionalOnProperty` 未加，属已知取舍） |
| **R5 消费失败只重投 3 次** | 达上限后消息被确认（避免毒消息无限重投），依赖 MES 侧台账人工重投 | 台账 `FAILED` + `lastError` 可查，`/erp-sync/{id}/retry` 可重投；重投前会先查 ERP 流水，已落账只标记 `SYNCED`（不会重复扣减） |
| **R6 派生缓存双写** | `current_stock` 同时被本地增量与 ERP 覆盖回写；若 ERP 过账失败而本地已扣，缓存会短暂偏离 | 属"缓存"语义；`erpAvailableStock` 才是展示/判定口径，`retryErpSync` 成功后覆盖回正 |
| **R7 `menu_id=4128` 冲突** | `INSERT IGNORE` 命中已占用 id 时静默跳过 → 非超管调请购接口 403 | 主脚本合并时由主代理确认/分配空闲 id（§3.2 已标注） |

**整体回滚**：代码改动集中在 `com.nexus.mes.eq.**`（MES）、`com.nexus.erp.wm.service.**`（ERP 新增文件）、`QueueConstants`（新增 1 常量）；按文件粒度回退即可。唯一不可逆点是存量回填（`material_id` 覆盖），回滚可用 `UPDATE eq_spare_part SET material_id = NULL WHERE ...`（脚本末尾已给出列回滚语句）。
