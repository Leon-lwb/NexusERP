# MES 备件↔ERP 打通收口（后端）修复报告（2026-09-20）

> 对应: `doc/audit/2026-09-20-feat-mes-spare-erp.md` §2 未修复项 | 分支: `feature/erp-mes-iot-audit-fix`
> 增量脚本: `script/sql/update/2026-09-20-mes-spare-closeout.sql`（幂等）
> 前端契约: 备件退库单 6 个端点 + 菜单/字典种子（前端由另一子代理并行实现，本报告 §1.5 为冻结契约）

## 1. 修复清单

| 编号 | 内容 | 涉及文件:行 | 是否需 DDL | 测试用例 |
|---|---|---|---|---|
| **(a)** 定时对账/自动重投 | 新增 `EqSpareErpSyncTask`：`@JobExecutor("eqSpareErpSyncTask")` + `@Scheduled("0 */10 * * * ?")` 薄壳（`SchedulerFallbackUtils.runIfFallbackEnabled(snailJobEnabled, "eqSpareErpSyncTask", 600000L, () -> jobExecute(null))`，不改生产 `snail-job.enabled`）；`TenantHelper.ignore` 跨租户扫描 `eq_spare_erp_sync` 中 `sync_status IN ('PENDING','SENT','FAILED')` 且 `last_publish_time` 为空或早于 `now - N 分钟`（`nexus.mes.eq.spare.erp-sync-stale-minutes`，默认 5）的记录，**逐条**调 `IEqSparePartService.retryErpSync(syncId)`，单条失败只记 WARN；类标 `@DS("mes")` | 新增 `eq/task/EqSpareErpSyncTask.java`（全文件） | 否（表已由 MVP 脚本建） | 静态核对（见 §5 说明）；关键幂等性由既有 `retryErpSync` 语义保证，见 §1.6 |
| **(b)** `warehouse_id` 存量回填入口 | 端点 `POST /mes/eq/spare-part/backfill-warehouse`（权限 `mes:eq:spare:edit`，无请求体、**不接受客户端传仓库**），服务端读 `nexus.mes.eq.spare.default-warehouse-id`（`@Value` 默认空，**未改任何 yml**）；配置空/非数字抛 `ServiceException`；跨库只读校验 ERP `wm_warehouse` **存在且启用**（新增 `countEnabledWarehouse`：`status = 1 AND del_flag='0' AND tenant_id`）；仅回填 `warehouse_id IS NULL AND del_flag='0'`（幂等），返回影响行数 | `eq/controller/EqSparePartController.java:216-231`、`eq/service/IEqSparePartService.java:158-171`、`eq/service/impl/EqSparePartServiceImpl.java:102-108,500-533`、`eq/mapper/ErpSpareStockQueryMapper.java:66-77` | 否（列已由 MVP 脚本建） | `EqSparePartServiceImplTest#backfillWarehouseRejectsWhenDefaultWarehouseNotConfigured`、`#backfillWarehouseBackfillsOnlyUnmappedSparesForEnabledErpWarehouse`、`#backfillWarehouseRejectsDisabledOrMissingErpWarehouse` |
| **(c1)** 备件参考单价落库 | `eq_spare_part` 新增 `unit_cost decimal(18,4) NULL DEFAULT 0`；实体/BO/VO 同步加字段；`insertByBo` 缺省 0、`updateByBo` 按主数据维护 | `eq/domain/EqSparePart.java:66-72`、`eq/domain/bo/EqSparePartBo.java:59-64`、`eq/domain/vo/EqSparePartVo.java:70-73`、`eq/service/impl/EqSparePartServiceImpl.java:174-176,206-208` | **是**（1 列） | 随 (c3) 用例覆盖 |
| **(c2)** MQ 契约加 `unitCost` | `MesSpareErpMessage` 新增 `unitCost`（`BigDecimal`，可空）并 `fromMap` 解析；MES 侧 `buildStockPayload` 从**服务端读出的** `eq_spare_part.unit_cost` 填充（`EqSpareStockBo` **没有** `unitCost` 入参，客户端无法伪造） | `erp/wm/service/mq/MesSpareErpMessage.java:66-70,101`、`eq/service/impl/EqSparePartServiceImpl.java:736-739` | 否 | `WmSpareStockPostingServiceImplTest#messageFromMapParsesUnitCostContract`、`EqSparePartServiceImplTest#stockTransactionPublishesServerSideUnitCostIntoErpPayload` |
| **(c3)** 备件成本计价接入 ERP 移动平均 | 过账服务注入 `IInventoryValuationService`：**入库/退库**在数量原子增加成功后调 `updateStockCost(stockId, qty, unitCost)`（单价为空或 ≤0 **跳过并记 WARN**，不用 0 拉低均价；新建库存行同样按该语义设初值）；**出库/维修领用**调 `issueStockCost(stockId, deductQty)` 取出库移动平均单位成本写入 `wm_material_stock_tx.unit_cost`（**替换**旧的 `stock.getUnitCost()` 快照），并把该成本写入既有字段 `tx_amount = 单价 × 数量`、追加到流水 `remark`；计价与数量增减在**同一 `@Transactional`** 内，计价异常**不 try/catch**（整单回滚） | `erp/wm/service/impl/WmSpareStockPostingServiceImpl.java:11-13,49-61,169-178,199-206,258-262,281-315,325-345,362-372` | 否 | `WmSpareStockPostingServiceImplTest#inboundAppliesMovingAverageCostWhenUnitCostPresent`、`#inboundSkipsValuationWhenUnitCostMissingOrZero`、`#inboundCreatesStockRowAndInitialisesCostViaMovingAverage`、`#outboundWritesIssuedMovingAverageCostToLedger` |
| **(d)** 备件退库单实体 + 端点 | 新表 `eq_spare_return` / `eq_spare_return_line`；实体/Mapper/BO/VO/Service/Controller 全套；单号 `MESRT+yyyyMMdd+4位当日序号`（Redis 原子自增，抽成可注入组件 `EqSpareReturnNoGenerator`）；6 个端点全部 `@SaCheckPermission`；服务类标 `@DS("mes")`；`confirm` = CAS(`DRAFT→CONFIRMED`) 先占位 → 逐行 `stockTransaction(TXN_RETURN, refType='SPARE_RETURN', refId=returnId)` → 任一行失败整单回滚保持 DRAFT；**重复 confirm 幂等成功**（返回 true 不重复过账）；**同单同 `spare_id` 重复行合并**（数量累加）；明细数量 ≤0 / 备件不存在 / 非 DRAFT 改删 均拒绝 | 新增 `eq/domain/EqSpareReturn.java`、`eq/domain/EqSpareReturnLine.java`、`eq/domain/bo/EqSpareReturnBo.java`、`eq/domain/bo/EqSpareReturnLineBo.java`、`eq/domain/vo/EqSpareReturnVo.java`、`eq/domain/vo/EqSpareReturnLineVo.java`、`eq/mapper/EqSpareReturnMapper.java`、`eq/mapper/EqSpareReturnLineMapper.java`、`eq/service/IEqSpareReturnService.java`、`eq/service/impl/EqSpareReturnServiceImpl.java`、`eq/service/support/EqSpareReturnNoGenerator.java`、`eq/controller/EqSpareReturnController.java` | **是**（2 表） | `EqSpareReturnServiceImplTest`（8 例，见 §5） |
| **(d-seed)** 菜单/字典/任务种子 | 菜单 4381（过账台账，复用 `mes:eq:spare:query`）、4383（备件退库单）+ 4384~4389（6 按钮）；字典 `mes_spare_return_status`(600214)/`mes_spare_return_type`(600215) 与 6 条数据(600274~600279)；`sj_job` id=30（`biz_30`, `eqSpareErpSyncTask`, `0 */10 * * * ?`）+ `sj_job_executor` id=46 | `script/sql/update/2026-09-20-mes-spare-closeout.sql` §4/§5/§6 | 是（DML 种子） | — |

**改动规模**：新增主代码 Java **13** 个文件 + 增量 SQL 脚本 1 个 + 新增测试类 1 个；修改主代码 **9** 个文件（MES 7 + ERP 2）+ 追加用例至既有测试类 2 个；新增修复报告 1 个。合计 27 个文件。

### 1.1 【P0 缺陷修复】`@DS("mes")` 服务内 `push("erp")` 跨库读在事务内失效

评审发现的**运行期 P0**（其中 `insertByBo`/`updateByBo` 是上一批次 MVP 遗留缺陷）：Spring `DataSourceTransactionManager.doBegin` 在**事务开启时**即按类级 `@DS("mes")` 把 mes 连接绑定到线程；之后 `DynamicDataSourceContextHolder.push("erp")` 只是压栈，`DataSourceUtils.getConnection()` **复用已绑定连接**、不经过 `DynamicRoutingDataSource` 路由 → 查询打到 `ry-mes` → `Table 'ry-mes.bd_material' doesn't exist`。**单测 mock 了 `ErpSpareStockQueryMapper`，因此完全测不出来。**

| 方法 | 修复前 | 修复后 | 影响 |
|---|---|---|---|
| `insertByBo` | `@Transactional` → `validateErpMapping` 跨库读失效 | **去掉 `@Transactional`**（只有 1 条写语句，单语句原子；编码唯一性由 DB 唯一键 `uk_tenant_code(tenant_id, spare_code)` 兜底） | 只要填了 `materialId`/`warehouseId` 就报错 → 现可正常保存 |
| `updateByBo` | 同上 | **去掉 `@Transactional`**（同上） | 同上 |
| `backfillWarehouse` | 本批次新增时误标 `@Transactional` | **去掉 `@Transactional`**（1 次跨库只读 + 1 条 UPDATE，UPDATE 自身原子） | 回填入口从"必然报错"变为可用 |

- 防回归注释：类 javadoc「⚠ 跨库只读与事务互斥（重要，勿回退）」+ `queryErpAvailable`/`queryErpMaterial`/`validateErpMapping` 三个 helper 的 javadoc 均写明「调用方不得处于事务中」及报错文本。
- 防回归测试：`EqSparePartServiceImplTest#erpCrossDbReadMethodsMustNotBeTransactional`（反射断言三个方法上**没有** `@Transactional`）。
- **不受影响、已核实无需改动**：`selectPageList`/`fillErpStock`/`queryErpAvailable`、`selectLowStock`、`retryErpSync`（无事务）、`selectErpSyncList`、`generatePurchaseRequest`（无事务）、`stockTransaction`（有事务但只读 VO、不做跨库读）、`deleteByIds`（有事务、无跨库读）。
- **自查同类（任务 E）**：`EqSpareErpSyncTask` 未标 `@Transactional`；`EqSpareReturnServiceImpl` 标了 `@DS("mes")`，其 `insertByBo`/`updateByBo`/`confirm` 的事务**只访问 mes 库**（`eq_spare_return*` + `stockTransaction` 只读 mes VO），**不含任何跨库 ERP 读**，因此事务安全，无需改动。

### 1.2 (a) 关键结论：`retryErpSync` 对已落账记录是幂等的（已核实）

- `EqSparePartServiceImpl.retryErpSync`（`EqSparePartServiceImpl.java:442-472`）第一句即 `if (SYNC_SYNCED.equals(sync.getSyncStatus())) return true;` —— 已确认落账的记录**直接早返回，不重投**。
- 本任务的扫描条件本身把 `SYNCED` 排除在外（`sync_status IN ('PENDING','SENT','FAILED')`），构成**双重保险**；ERP 侧另有 Redis 原子占位（7 天）+ `wm_material_stock_tx` 流水查重兜底。
- 因此**无需**额外加 `sync_status != 'SYNCED'` 过滤（任务要求的"若发现重复投递必须加过滤"未触发）。
- 额外纳入 `last_publish_time IS NULL` 的停滞记录：这类记录是"事务已提交但投递前进程退出"，若不扫描将**永久停滞**（严格按"仅 `last_publish_time` 早于阈值"会漏掉它们）。
- **跨租户正确性**：扫描用 `TenantHelper.ignore(...)`；但逐条执行时包了 `TenantHelper.dynamic(sync.getTenantId(), ...)`。原因是 `retryErpSync` 内部 `currentTenantId()` 在定时线程无登录上下文时会退化为 `000000`，若在 `ignore` 上下文里直接调用，`countErpPosting(bizKey)` 与 `refreshDerivedStock` 会以错误租户查 ERP，导致对账误判/派生缓存串租户。
- 熔断逃生口：`nexus.mes.eq.spare.erp-sync-max-retry`（默认 `0` = 不限制，保持默认行为），配置后超限记录保留 `FAILED` 待人工在过账台账页处理。

### 1.3 (c) 计价语义与已知风险

- 单价取值：**只**从 `eq_spare_part.unit_cost`（服务端读库）→ MQ `unitCost` → ERP 计价。`EqSpareStockBo`/`EqSpareReturnLineBo` 均**没有**单价入参。
- 入库（`IN`/`RETURN`）：`unitCost` 为空或 ≤0 → **跳过计价并记 WARN**，`wm_material_stock_tx.unit_cost` 退回库存行当前均价（保持旧行为，不写 0）。
- 新建库存行：插入时仍写 `unit_cost/total_cost = 0`，随后按 `updateStockCost` 语义调用一次（数量已含本次入库 → `unit_cost = 本次单价`、`total_cost = 数量 × 单价`），**未自行发明公式**。
- 出库（`OUT`/`REPAIR_USE`）：`wm_material_stock_tx.unit_cost` **替换**为 `issueStockCost` 返回的出库移动平均单位成本；同时把该成本写入既有字段 `tx_amount`（事务金额 = 单价 × 数量）并在 `remark` 追加 `单位成本=...`（`WmMaterialStockTx` 无专门"成本来源"字段，故复用既有字段 + 备注）。
- ⚠ **未重构的并发风险（按要求只报告）**：`MovingAverageValuationServiceImpl.updateStockCost` 是 **Java 先读后写**（`selectById` → 计算 → `updateById`），并发入库存在丢失更新（均价偏低/偏高）风险；`issueStockCost` 的成本结转 SQL 本身是原子相对扣减，但其**返回的 unitCost 是读值**，并发下可能读到旧均价。本批次**未重构该服务**（任务明确要求）。建议单开：把 `updateStockCost` 下沉为原子 SQL（`total_cost = total_cost + qty*price; unit_cost = total_cost / stock_quantity`）或对该行加行锁。

### 1.4 (d) 冻结契约实现细节（供前端对齐）

| 端点 | 权限码 | 返回 | 说明 |
|---|---|---|---|
| `GET /mes/eq/spare-return/list` | `mes:eq:spare:return:list` | `TableDataInfo<EqSpareReturnVo>` | query: `returnNo`(like)/`status`/`returnType`/`beginTime`/`endTime`；按 `return_date DESC`；**不填充 lines**（控制开销） |
| `GET /mes/eq/spare-return/{returnId}` | `mes:eq:spare:return:query` | `R<EqSpareReturnVo>` | 含 `lines: List<EqSpareReturnLineVo>` |
| `POST /mes/eq/spare-return` | `mes:eq:spare:return:add` | `R<Long>`（returnId） | 新增草稿；`returnNo` 留空则服务端生成；`totalQty` 服务端汇总 |
| `PUT /mes/eq/spare-return` | `mes:eq:spare:return:edit` | `R<Void>` | 仅 `DRAFT`；明细**全量替换** |
| `DELETE /mes/eq/spare-return/{ids}` | `mes:eq:spare:return:remove` | `R<Void>` | 仅 `DRAFT`；明细一并逻辑删除 |
| `PUT /mes/eq/spare-return/{returnId}/confirm` | `mes:eq:spare:return:confirm` | `R<Boolean>` | 幂等；已 `CONFIRMED` 再调返回 `true` |

- **重复 confirm 的取舍**：选「**幂等成功**」（返回 `true`，提示"退库单已确认"），不抛业务错误——前端重试/双击不会报错，也不会重复过账。
- **同单重复 `spare_id` 的取舍**：选「**合并**」（数量累加为一行，`totalQty` 同步），避免同单重复过账；`returnNo` 唯一性由 `uk_spare_return_no(tenant_id, return_no)` 兜底。
- **VO 未提供 `erpAvailableStock`**：任务允许在实现成本高时省略。理由：明细已冻结 `unitCost` 快照，退库数量校验依赖 `stockTransaction` 的严格模式与 ERP 可用量守卫（真实扣减/入账时判定），在 VO 里再查一次 ERP 属于重复跨库读（且 `confirm` 在事务内**不能**做跨库读，见 §1.1）。如需展示请单开只读接口。
- 退库单据**未接入前端菜单之外的路由**（`plus-ui/**` 不在本代理所有权内）。

## 2. 未修复 / 部分修复项

| 项 | 状态 | 理由与建议 |
|---|---|---|
| `updateStockCost` 的 Java 读改写并发问题 | 未修（有意） | 任务明确「不要在本任务重构它，只在报告里写明风险」。建议单开：原子 SQL 或行锁。见 §1.3。 |
| `wm_material_stock_tx` 的 MES 备件唯一索引 | 未做 | 与 MVP 报告 §5 一致：对共享大表加唯一索引需先确认存量无重复，当前幂等由「Redis 占位 + 流水查重」保证。 |
| 定时任务的单元测试 | 未做 | `EqSpareErpSyncTask` 的扫描条件/租户切换是 MyBatis Wrapper + `TenantHelper` 静态调用，单测需要 mock 静态（项目规范不推荐）；关键幂等性已在 `retryErpSync` 侧核实（§1.2）并有既有用例覆盖。建议后续用集成测试（Testcontainers）覆盖。 |
| `CANCELLED` 状态无写入入口 | 部分 | 契约只要求 `DRAFT`/`CONFIRMED` 的流转（无取消端点），故 `CANCELLED` 仅作为字典与守卫中的终态存在；如需作废单请单开 `PUT /{returnId}/cancel`。 |
| 退库单不校验「退库数量 ≤ 现场领用量」 | 部分 | 退库是"现场→仓库"，数量由人工确认；`stockTransaction` 侧只做备件映射/仓库校验，ERP 侧按入库过账不做上限校验。若需按维修单/领用单核销，应带 `refType/refId` 关联领用来源。 |
| 备件主数据列表未展示 `unitCost` 列 | 部分 | 后端 `EqSparePartVo.unitCost` 已返回（含 Excel 导出列），前端展示由前端代理承接。 |
| `sj_job id=29`（IoT OTA）`deleted=1` | **观察项（非本批次文件）** | 编写本批次种子时核对 `sj_job` 列序发现 `script/sql/update/2026-09-20-iot-ota.sql:120` 的 VALUES 第 28 位（`deleted`）为 `1`，即该任务被标记为**已删除**，即使 `snail-job.enabled=true` 也不会触发（本地 `@Scheduled` 兜底仍生效）。本批次种子用 `deleted=0`。建议由 IoT 侧确认。 |

## 3. 主脚本合并清单

> 本代理**未编辑**任何主脚本/yml/`plus-ui`；以下由主代理串行合并。

### 3.1 `script/sql/ry-mes.sql`（库 `ry-mes`）

**(a) `eq_spare_part` 增加 `unit_cost`**
- 锚点（`eq_spare_part` 建表段，`script/sql/ry-mes.sql:493` 整行）：
```
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP仓库ID(wm_warehouse.warehouse_id)',
```
- 在该行之后插入 1 行：
```
  `unit_cost` decimal(18, 4) NULL DEFAULT 0.0000 COMMENT '备件参考单价(人工维护; 出入库过账时由服务端读库填入 MQ, 供 ERP 移动平均计价)',
```

**(b) 新增 3 张表**
- 锚点：`eq_spare_stock_log` 建表段结束行（`script/sql/ry-mes.sql:537`）：
```
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES备件库存流水' ROW_FORMAT = Dynamic;
```
- 在该行之后，按以下顺序插入：
  1. `eq_spare_erp_sync`（MVP 报告 §3.1(b) 已登记，若尚未合并则一并补上）；
  2. 增量脚本 §2 的 `CREATE TABLE eq_spare_return`（去掉 `IF NOT EXISTS`，与主脚本风格一致）；
  3. 增量脚本 §2 的 `CREATE TABLE eq_spare_return_line`（同上）。

### 3.2 `script/sql/ry-vue.sql`（库 `ry-vue`）

**(a) `sys_menu`**：锚点 `INSERT INTO \`sys_menu\` VALUES (4203, '备件管理', ...)` 所在块之后，插入增量脚本 §4 的 8 行（`menu_id` 4381/4383/4384~4389；`INSERT IGNORE` 改为普通 `INSERT`，与主脚本风格一致）。

**(b) `sys_dict_type`**：锚点 `script/sql/ry-vue.sql:90356`（`iot_forward_status` 行）之后，插入：
```
INSERT INTO `sys_dict_type` VALUES (600214, '000000', '备件退库单状态', 'mes_spare_return_status', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'MES备件退库单状态: DRAFT/CONFIRMED/CANCELLED');
INSERT INTO `sys_dict_type` VALUES (600215, '000000', '备件退库类型', 'mes_spare_return_type', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'MES备件退库类型: REPAIR_RETURN/EXCESS_RETURN/OTHER');
```
（已核对：`sys_dict_type.dict_id` 600214/600215 在主脚本中**空闲**；600214/600215 在 `sys_dict_data` 中被 IoT 占用的是 `dict_code`，与 `dict_id` 不冲突。）

**(c) `sys_dict_data`**：锚点 `script/sql/ry-vue.sql:90375`（`iot_device_status DECOMMISSIONED` 行）之后，插入增量脚本 §5 的 6 行（`dict_code` 600274~600279，已核对空闲）。

**(d) `sj_job`**：锚点 `script/sql/ry-vue.sql:10053`（`biz_200` 行）之后，插入增量脚本 §6 的 `sj_job` 行（`id=30`, `biz_30`, `executor_info='eqSpareErpSyncTask'`, `trigger_interval='0 */10 * * * ?'`, **`deleted=0`**）。已核对 `id=30` 空闲（现有 1-3、29、200）。

**(e) `sj_job_executor`**：锚点 `script/sql/ry-vue.sql:10188`（`soContractExpiryTask` 行）之后，插入：
```
INSERT INTO `sj_job_executor` VALUES (46, 'dev', 'ruoyi_group', 'eqSpareErpSyncTask', '1', '2026-09-20 00:00:00', '2026-09-20 00:00:00');
```
（已核对 `id=46` 空闲：现有 1-45、200。）

### 3.3 `doc/system/数据库增量变更记录.md`
- 追加 §4 的 4 条登记行。

## 4. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

| 日期 | 库 | 表 | 变更类型 | 变更内容 | 脚本 | 状态 |
|---|---|---|---|---|---|---|
| 2026-09-20 | `ry-mes` | `eq_spare_part` | 新增字段 ×1 | `unit_cost decimal(18,4) NULL DEFAULT 0`（备件参考单价，人工维护；出入库过账由服务端读库填入 MQ 供 ERP 移动平均计价） | `script/sql/update/2026-09-20-mes-spare-closeout.sql` §1 | **必须执行**（实体已含该列，缺列报 `Unknown column`） |
| 2026-09-20 | `ry-mes` | `eq_spare_return` | 新增表 | 备件退库单头；`uk_spare_return_no(tenant_id, return_no)`、`idx_spare_return_status(tenant_id, status)`、`idx_spare_return_date(tenant_id, return_date)` | 同上 §2 | **必须执行**（否则退库单接口全部 500） |
| 2026-09-20 | `ry-mes` | `eq_spare_return_line` | 新增表 | 备件退库单明细；`idx_spare_return_line(tenant_id, return_id)` | 同上 §2 | **必须执行** |
| 2026-09-20 | `ry-mes` ← `ry-erp` | `eq_spare_part.unit_cost` | 数据回填（DML） | 按 `material_id` 关联 `bd_material`，取 `COALESCE(NULLIF(reference_price,0), standard_cost)`（需 >0）回填；依赖 `material_id` 已由 `2026-09-20-mes-spare-erp.sql` §3 回填完成 | 同上 §3 | **需人工核对影响行数后执行**（不回填则计价等于没做） |
| 2026-09-20 | `ry-vue` | `sys_menu` | DML | 新增菜单 4381（过账台账，复用 `mes:eq:spare:query`）、4383（备件退库单）+ 按钮 4384~4389（`mes:eq:spare:return:list/query/add/edit/remove/confirm`） | 同上 §4 | **必须执行**（否则非超管 403 且前端无入口） |
| 2026-09-20 | `ry-vue` | `sys_dict_type` / `sys_dict_data` | DML | 字典 `mes_spare_return_status`(600214, 数据 600274~600276)、`mes_spare_return_type`(600215, 数据 600277~600279) | 同上 §5 | **必须执行**（前端下拉/状态标签依赖） |
| 2026-09-20 | `ry-vue` | `sj_job` / `sj_job_executor` | DML | 任务 `id=30`/`biz_30`/`eqSpareErpSyncTask`/`0 */10 * * * ?`（`deleted=0`）+ 执行器 `id=46` | 同上 §6 | **必须执行**（`snail-job.enabled=true` 的环境才生效；false 时由 `@Scheduled` 兜底） |

- **执行前提**：§1/§2 必须在应用启动前执行；§3 要求 `ry-mes`/`ry-erp` 同实例可跨库读写，且**先执行完 `2026-09-20-mes-spare-erp.sql` §3**（`material_id` 回填）。
- **Redis**：新增字典 `mes_spare_return_status` / `mes_spare_return_type`，若前端已加载过字典缓存需刷新字典缓存（或删 `sys_dict:*`）；其余无需清缓存。

## 5. 验证记录

```powershell
mvn -pl ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-erp -am test -DskipTests=false `
    "-Dtest=EqSparePartServiceImplTest,WmSpareStockPostingServiceImplTest,EqSpareReturnServiceImplTest" `
    "-Dsurefire.failIfNoSpecifiedTests=false"
```

**实测结果**：见下方"实测输出"。

| 测试类 | 模块 | `@Tag` | 用例数 | 新增用例覆盖点 |
|---|---|---|---|---|
| `com.nexus.mes.eq.service.impl.EqSparePartServiceImplTest` | ruoyi-mes | `local`+`dev` | 13 → **18** | 跨库只读方法**不得标 `@Transactional`**（反射防回归）；默认仓库未配置即拒绝且零副作用；配置启用仓库时回填 3 行；仓库不存在/停用拒绝且不 UPDATE；MQ 消息 `unitCost` 取自服务端读库（`EqSpareStockBo` 无该入参） |
| `com.nexus.erp.wm.service.impl.WmSpareStockPostingServiceImplTest` | ruoyi-erp | `dev` | 7 → **12** | 入库带单价调 `updateStockCost` 且流水 `unit_cost/tx_amount` 正确；单价 0 **跳过计价**；新建库存行按 `updateStockCost` 语义设初值；出库流水 `unit_cost` = `issueStockCost` 返回值；`fromMap` 解析 `unitCost` |
| `com.nexus.mes.eq.service.impl.EqSpareReturnServiceImplTest` | ruoyi-mes | `local`+`dev` | **8**（新增类） | 同单重复 `spare_id` 合并 + `totalQty` 汇总 + 单价快照；数量 ≤0 拒绝；confirm 逐行 `TXN_RETURN`/`SPARE_RETURN`/`refId`；已 CONFIRMED 幂等成功不重复过账；CAS 抢锁失败报错；已作废不可确认；非 DRAFT 不可改；非 DRAFT 不可删 |

**实测输出**

```
[INFO] Building ruoyi-erp 5.6.0                                          [34/36]
[INFO] Running com.nexus.erp.wm.service.impl.WmSpareStockPostingServiceImplTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.064 s
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building ruoyi-mes 5.6.0                                          [36/36]
[INFO] Running com.nexus.mes.eq.service.impl.EqSparePartServiceImplTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.171 s
[INFO] Running com.nexus.mes.eq.service.impl.EqSpareReturnServiceImplTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.206 s
[INFO] Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**合计 38 例全绿**（ERP 12 + MES 26）。`ruoyi-erp` / `ruoyi-mes` 主代码随 `-am` 全量编译通过（含上游 34 个模块），说明新增的 `EqSpareReturn*` 全套类、`EqSpareErpSyncTask`、`IInventoryValuationService` 注入、`MesSpareErpMessage.unitCost` 与 `ErpSpareStockQueryMapper.countEnabledWarehouse` 未破坏模块间编译契约。

> 说明 1：ERP 侧运行日志中的 `ERROR ... 投递失败(已记账, 待重投): bizKey=EQSP:000000:1000` 是既有用例
> `stockTransactionMarksSyncFailedWhenPublishFails` 的**预期日志**（断言 `sync_status=FAILED` 且 `last_error` 非空），非失败。
> 说明 2：首轮编译暴露 1 个真实缺陷并已修复 —— `EqSpareErpSyncTask` 类 javadoc 里写了字面量 `cron {@code 0 */10 * * * ?}`，
> 其中 `*/` **提前终止了 javadoc 注释**，导致后续 12 个语法错误；已改写为文字描述（不再在 javadoc 中出现 `*/` 字面量）。
> 说明 3：`EqSpareReturnServiceImplTest` 为新增测试类（`@Tag("local")`+`@Tag("dev")`）；既有 2 个测试类为**追加用例**，原有用例全部保持通过。
> 说明 4：`RedisUtils` 未做静态打桩 —— 退库单号生成抽成可注入组件 `EqSpareReturnNoGenerator`（与既有 `WmSpareStockDedupGuard` 同一模式）；
> MyBatis-Plus 静态 lambda 列缓存统一用 `TableInfoHelper.initTableInfo`；`insert(any())` 全部写作 `insert(any(实体.class))`。

## 6. 风险与回滚说明

| 风险 | 说明 | 缓解 / 回滚 |
|---|---|---|
| **R1（高）未执行 §1 DDL 即启动** | 实体已含 `unit_cost`，备件主数据查询/新增/编辑与出入库过账全部报 `Unknown column 'unit_cost'` | 上线前执行 §1；回滚：`ALTER TABLE eq_spare_part DROP COLUMN unit_cost`（会一并丢失人工维护的单价，先备份） |
| **R2（高）未执行 §2 DDL 即启动** | 退库单 6 个接口全部报 `Table 'ry-mes.eq_spare_return' doesn't exist` | 执行 §2；回滚：`DROP TABLE eq_spare_return_line, eq_spare_return` |
| **R3（中）未执行 §3 回填** | 存量备件 `unit_cost = 0` → 入库计价被跳过（WARN），移动平均等于没做 | 执行 §3（先看前置检查影响行数）；回滚见脚本末尾 |
| **R4（中）移动平均 Java 读改写并发丢失更新** | `updateStockCost` 先读后写；并发入库可能算错均价（本批次未重构） | 见 §1.3；建议单开原子 SQL 重构。业务影响：均价偏差，可用盘点/成本调整纠正 |
| **R5（低）重投无上限** | 默认 `erp-sync-max-retry=0`（不限制），持续失败会每 10 分钟重试并刷 WARN 日志 | 配置 `nexus.mes.eq.spare.erp-sync-max-retry` 熔断，超限记录留 `FAILED` 待人工处理 |
| **R6（低）菜单权限** | 4381 复用 `mes:eq:spare:query`、4383 用新权限码 `mes:eq:spare:return:list`；非超管角色需在「角色管理」勾选授权 | 由运维在角色中授权；`INSERT IGNORE` 命中已占用 `menu_id` 会静默跳过 → 主代理合并时确认 id 空闲 |
| **R7（低）`unit_cost` 被客户端写入** | `EqSparePartBo.unitCost` 是主数据维护字段，具备 `mes:eq:spare:add/edit` 权限的用户可改 | 这是设计意图（人工维护参考单价）；出入库过账的单价**不**走 BO，无法在出入库时伪造 |
| **R8（低）退库单确认与 MQ 投递** | `confirm` 事务提交后才投递 MQ（`publishAfterCommit`）；若进程在提交后、投递前退出，台账停 `PENDING` | 由本批次 (a) 定时对账任务兜底重投（`last_publish_time IS NULL` 也在扫描范围内） |
| **R9（中）去掉 `@Transactional` 后的原子性变化** | `insertByBo`/`updateByBo`/`backfillWarehouse` 不再有事务包裹（见 §1.1）。**为何去掉**：事务一开，类级 `@DS("mes")` 已把连接绑定到线程，`push("erp")` 失效 → 跨库读打到 `ry-mes` 报 `Table ... doesn't exist`；而 Spring 的跨库只读**无法**在既有事务内切换数据源。**影响面**：三者各自只有 1 条写语句（`insert` / `updateById` / 单条 `UPDATE`），**单语句本身即原子**，不存在"写一半"的中间态；备件编码唯一性由 DB 唯一键 `uk_tenant_code(tenant_id, spare_code)` 兜底（并发下由数据库抛唯一键冲突，不会产生重复备件）。因此去掉事务**不降低**这三条路径的数据一致性 | 若未来这三条路径演化为"多次写"，必须改为「非事务公开方法先做跨库校验 → 自注入代理调 `@Transactional` 私有方法落库」，**不得**在事务内做跨库读。防回归断言：`EqSparePartServiceImplTest#erpCrossDbReadMethodsMustNotBeTransactional` |

**整体回滚**：代码改动集中在 `com.nexus.mes.eq.**`（MES）与 `com.nexus.erp.wm.service.**`（ERP 过账服务）；按文件粒度回退即可。DDL 回滚见各节；`unit_cost` 回填不可逆（无法区分人工维护与脚本回填），如需彻底回滚请先备份该列。
