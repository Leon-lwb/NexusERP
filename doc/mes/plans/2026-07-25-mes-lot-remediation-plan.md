# MES 批次(lt)模块整改计划

> **创建时间**: 2026-07-25
> **关联**: [2026-08-03-mes-p0-9-lot-management-implementation-plan.md](./2026-08-03-mes-p0-9-lot-management-implementation-plan.md)（未提交的 lt 模块原方案）、[ERP-MES批次轻量引用集成方案.md](file:///D:/workspace/.trae/documents/ERP-MES批次轻量引用集成方案.md)（.trae 方案，本计划暂不采纳）
> **本计划范围**:
> - **(a) 实施** = A 代码质量修复（6 条）+ B 五条 MES 内部链路（按可行性分级实施/契约）—— ✅ **已实施完毕**（2026-07-25）：后端 `mvn -pl ruoyi-modules/ruoyi-mes -am compile` BUILD SUCCESS；前端 lt 四文件 eslint 0 错误（prettier --fix 后）。运行时/端到端验证待 DDL 执行 + 联调。
> - **(b)(c) 仅写范围，不实施**（见第四节）

---

## 0. 探索结论（决定可行性的关键事实）

5 条"内部链路"的可行性，取决于目标模块/流程是否存在。已核实：

| 链路 | 目标 | 是否存在 | 结论 |
|---|---|---|---|
| 1 SN↔批次绑定 | `ProSn` 创建时绑定 | `ProSn` 存在，但**无 `lotId` 字段**，`createSn`/`generateSnForCard` 不收 lotId | ✅ 可实施（机制层：加可选 lotId + 事件） |
| 2 工单 FEFO 消耗 | 工单领料/消耗流 | **`pro` 无任何领料/消耗/倒冲流**（`IProTraceabilityService` 自述"需未来组件消费记录表"）；`wm/tg/sop/nr` 包均不存在 | ⚠️ 无现成挂载点 → **新增一个自包含的"工单批次领料"小流程**，作为未来 P0-1 的调用入口 |
| 3 IQC→qc_status | `QcIqc.inspect()` 完成回写 | `qc` 模块存在，`inspect()` 有 afterCommit 钩子；但 **`QcIqc` 无 `lotId`**，且 lot 与收货行无关联 | ✅ 可实施（机制层：lt_lot 加 `iqc_id` + inspect 钩子回写；**自动触发受限**，需先手动/未来流关联 lot↔IQC） |
| 4 NCR→隔离 | `nr` 模块 NCR 创建 | **`nr` 包不存在**（P0-4 未建） | ⏸ 仅契约（便利方法 + 文档），不接流 |
| 5 进站校验 | P0-3 进站出站 | **P0-3 未建**（`pro` 无进站/出站流） | ⏸ 仅契约（校验 helper + 文档），不接流 |

**包存在性核实**：`ruoyi-mes` 下仅 `common/dc/eq/lt/pro/qc/spc` 存在；`nr/sop/wm/tg` 均不存在。
**lt 模块当前是孤岛**：`lt` 包外无任何调用方（仅 `LtWoConsumptionServiceImpl`→`ILtLotService.consume` 内部联动）。

> 因此"接通 5 条链路"的诚实表述是：**2 条全接通（#2 新建小流程、#3 机制+钩子）、1 条机制就绪（#1）、2 条仅契约（#4#5 目标模块未建）**。这是模块现状决定的，不是缩减范围。

---

## 一、A 部分：代码质量修复（实施，自包含）

### A1 批次编码生成：随机→顺序（`LtLotServiceImpl.generateLotCode`）
- **现状**：`ThreadLocalRandom.nextInt(100,1000)` + 5 次重试 + 兜底 `System.currentTimeMillis()%1000`。注释写"3位序号"实为随机，并发易碰撞、靠 uk 硬扛。
- **修复**：对齐 MES 兄弟模块 `QcIqc.generateNo` 的顺序模式——`count(当日)+1` → `LOT-yyyyMMdd-NNN`；在 `insertByBo` 加 `@Lock4j` 串行化创建（关闭 count→insert 竞态），`uk_lot_code` 兜底。
- **未来硬化（不在本次）**：接入 `AutoCodeUtil.genSerialCode`（ruoyi-system，规则驱动），需配 `sys_auto_code_rule`。
- **文件**：`LtLotServiceImpl.java`

### A2 编辑数量守卫（`LtLotServiceImpl.updateByBo`）
- **现状**：允许把 `quantity` 改到小于 `consumed_qty`（IN_USE 态），导致剩余为负、`consume` 不变量失效。
- **修复**：`bo.getQuantity() != null && bo.getQuantity().compareTo(lot.getConsumedQty()) < 0` 时抛异常"批次数量不可小于已消耗数量"。
- **文件**：`LtLotServiceImpl.java`

### A3 删除孤儿守卫（`LtLotServiceImpl.deleteByIds`）
- **现状**：删批次后 `lt_wo_consumption.lot_id`/`lt_sn_binding.lot_id` 变孤儿（无 FK 无级联），追溯链断裂。
- **修复**：删除前查 `consumptionMapper`/`bindingMapper` 是否存在该 lotId 的记录；存在则抛"批次存在消耗/绑定记录，不可删除（追溯完整性）"。
- **文件**：`LtLotServiceImpl.java`（注入 `LtWoConsumptionMapper`/`LtSnBindingMapper`，已注入）

### A4 放行状态语义（`LtLotServiceImpl.release`）
- **现状**：`QUARANTINED→放行`时 `remaining>0→IN_STOCK`，丢失"部分消耗=使用中"语义。
- **修复**：`remaining==0→CONSUMED`；`consumedQty>0→IN_USE`；否则 `IN_STOCK`。无需加列，按 `consumedQty` 推断。
- **文件**：`LtLotServiceImpl.java`

### A5 DDL 整合（消除事后改名）
- **现状**：4 个 SQL 是事后补救——`mes-p0-09-01-lot.sql` 建了错名 `lt_sn_lot_binding`/`lt_wo_lot_consumption`，`mes-p0-09-04-rename-tables.sql` 再改名；先跑 01+02 的环境会建出错表名。
- **修复**：重写 `mes-p0-09-01-lot.sql`，三表直接用最终名 `lt_lot`/`lt_sn_binding`/`lt_wo_consumption`（`CREATE TABLE IF NOT EXISTS` 保持幂等）；`lt_lot` 加 `iqc_id` 列（见 B3）；删除 `mes-p0-09-04-rename-tables.sql`（逻辑并入 01）。已部署环境附"已改名则跳过"说明。
- **文件**：`doc/mes/sql/mes-p0-09-01-lot.sql`（重写）、删除 `mes-p0-09-04-rename-tables.sql`

### A6 菜单整合（消除 ID 冲突补救）
- **现状**：`mes-p0-09-02-menu.sql` 用了被占用的 4150/4151（`INSERT IGNORE` 静默跳过致路由缺失），`mes-p0-09-03-menu-fix.sql` 改用 4170/4171 + 重挂按钮 + 授权 role_id=3。
- **修复**：重写 `mes-p0-09-02-menu.sql` 为单一干净版——父目录 4006、批次主档 C 页 4170、查询/按钮 F 用 4171+/4152~4168、`sys_role_menu` 授权；删除 `mes-p0-09-03-menu-fix.sql`（逻辑并入 02）。执行前校验 SQL 保留。
- **文件**：`doc/mes/sql/mes-p0-09-02-menu.sql`（重写）、删除 `mes-p0-09-03-menu-fix.sql`

---

## 二、B 部分：五条 MES 内部链路（按可行性实施/契约）

### B1 链路1：SN↔批次绑定（机制，事件驱动避免循环依赖）
- **问题**：`ProSn` 无 lotId，`createSn`/`generateSnForCard` 不收 lotId；`lt_sn_binding` 只能手动建。
- **方案**：
  - `IProSnService.createSn`/`generateSnForCard` 增加可选 `Long lotId` 入参（重载或 nullable）。
  - `ProSnServiceImpl`：若 `lotId != null`，在 insert 后用 `ApplicationEventPublisher` 发 `SnCreatedEvent(snId, lotId, materialId, snCode)`。
  - 新增 `lt` 包 `LtSnBindingEventListener`（`@EventListener`），收事件→调 `ILtSnBindingService.insertByBo`（构造 bo）。
  - `ProSnController` 透传可选 `lotId`。
- **为何事件**：`lt` 已依赖 `pro`（`LtSnBindingServiceImpl` 注入 `ProSnMapper`）；若 `pro` 再构造注入 `ILtSnBindingService` 会成环。事件单向 `pro→lt`，无环。
- **不改 `ProSn` 表结构**（绑定关系只存 `lt_sn_binding`）。
- **文件**：`IProSnService.java`、`ProSnServiceImpl.java`、`ProSnController.java`、新 `SnCreatedEvent.java`（放 `pro/event` 或 `common`）、新 `LtSnBindingEventListener.java`（`lt/listener`）

### B2 链路2：工单 FEFO 批次消耗（新建自包含小流程）⭐ 最有价值
- **问题**：`pro` 无领料/消耗流，`recommendFefo`/`consume`/`lt_wo_consumption` 无人调用，FEFO 与消耗形同虚设。
- **方案**：在 `lt` 内新增"工单批次领料"两步流程，作为未来 P0-1 `wm` 物料拉动的调用入口：
  - `ILtWoConsumptionService.recommendForWo(woId, materialId, requiredQty)` → 委托 `ltLotService.recommendFefo(materialId, requiredQty)` 返回推荐批次。
  - `ILtWoConsumptionService.confirmPick(woId, materialId, List<PickLine{lotId, consumedQty}>, snId?)` → 事务内：逐条 `lotService.consume(lotId, qty)` + 写 `lt_wo_consumption`（冗余 woNo/lotCode/materialCode/operator）；若 `snId` 传入，顺带写 `lt_sn_binding`（复用 B1 的绑定）。
  - Controller：`GET /mes/lt/consumption/fefo/{woId}/{materialId}?requiredQty=`、`POST /mes/lt/consumption/pick`。
  - 前端：`consumption/index.vue` 加"FEFO 领料"按钮 + 弹窗（查推荐→填消耗→确认）；`api/mes/lt/consumption.ts` 加两函数。
- **自包含**：`LtWoConsumptionServiceImpl` 已调 `ILtLotService.consume`，不引入跨包环。
- **文件**：`ILtWoConsumptionService.java`、`LtWoConsumptionServiceImpl.java`、`LtWoConsumptionController.java`、`plus-ui/src/api/mes/lt/consumption.ts`、`plus-ui/src/views/mes/lt/consumption/index.vue`

### B3 链路3：IQC→批次 QC 状态（机制 + inspect 钩子）
- **问题**：`lt_lot.qc_status` 只能手动下拉；MES `QcIqc` 才是 PASS/FAIL 天然来源，但 `QcIqc` 无 lotId、与 lot 无关联。
- **方案**：
  - DDL：`lt_lot` 加 `iqc_id BIGINT NULL` + `idx_iqc_id`（新表，低风险，并入 A5）。
  - `LtLot`/`LtLotBo`/`LtLotVo` 加 `iqcId`。
  - `ILtLotService.applyIqcResult(lotId, iqcId, inspectResult)`：校验 lot 存在；映射 `QUALIFIED→PASS`/`UNQUALIFIED→FAIL`；置 `qc_status`（仅当前 PENDING 可置，或同 iqc 可重置）；回填 `iqc_id`；`FAIL` 自动 `quarantine`（复用现有逻辑）。
  - `QcIqcServiceImpl.inspect()`：CAS 更新后、`afterCommit` 内，按 `iqc_id=iqcId` 查 `lt_lot`，命中则调 `applyIqcResult`。
- **依赖方向**：`qc → lt`（`QcIqcServiceImpl` 注入 `ILtLotService`）；`lt` 不注入 `qc`，无环。同 `@DS("mes")`。
- **诚实边界**：自动触发仅在 lot 已关联 iqc_id 时生效（当前需手动关联，或未来收货→lot 流自动）。**全量自动待 (b)/(c)**。
- **文件**：`LtLot.java`、`LtLotBo.java`、`LtLotVo.java`、`ILtLotService.java`、`LtLotServiceImpl.java`、`QcIqcServiceImpl.java`、`mes-p0-09-01-lot.sql`（加列）、前端 `lot/index.vue`（表单可选 iqcId，可选）

### B4 链路4：NCR→隔离（仅契约，`nr` 不存在）
- **方案**：`ILtLotService.quarantineByNcr(lotId, ncrId, reason)` 便利方法（调 `quarantine`，remark 拼接 `NCR({ncrId}): {reason}`）。文档注明：P0-4 `nr` 落地后，NCR 创建流调本方法。
- **文件**：`ILtLotService.java`、`LtLotServiceImpl.java`

### B5 链路5：进站批次校验（仅契约，P0-3 不存在）
- **方案**：`ILtLotService.checkLotUsable(materialId, lotId)` 返回 `{usable, reason, lot}`：校验 lot 存在、`status=IN_STOCK`、`qc_status=PASS`、`remaining>0`。文档注明：P0-3 进站流调用本方法门禁。
- **文件**：`ILtLotService.java`、`LtLotServiceImpl.java`

---

## 三、交付物清单

| 类别 | 文件 | 动作 |
|---|---|---|
| A1 | `LtLotServiceImpl.java` | 改 generateLotCode + insertByBo 加 @Lock4j |
| A2/A3/A4 | `LtLotServiceImpl.java` | 加守卫/改 release |
| A5 | `mes-p0-09-01-lot.sql` | 重写（最终表名 + iqc_id） |
| A5 | `mes-p0-09-04-rename-tables.sql` | 删除 |
| A6 | `mes-p0-09-02-menu.sql` | 重写（单一干净版） |
| A6 | `mes-p0-09-03-menu-fix.sql` | 删除 |
| B1 | `IProSnService.java`/`ProSnServiceImpl.java`/`ProSnController.java` | 加可选 lotId + 发事件 |
| B1 | `SnCreatedEvent.java`（新）、`LtSnBindingEventListener.java`（新） | 新增 |
| B2 | `ILtWoConsumptionService.java`/`LtWoConsumptionServiceImpl.java`/`LtWoConsumptionController.java` | 加 recommendForWo/confirmPick |
| B2 | `consumption.ts`/`consumption/index.vue` | 加领料 API + UI |
| B3 | `LtLot.java`/`LtLotBo.java`/`LtLotVo.java`/`ILtLotService.java`/`LtLotServiceImpl.java` | 加 iqcId + applyIqcResult |
| B3 | `QcIqcServiceImpl.java` | inspect 钩子回写 |
| B4/B5 | `ILtLotService.java`/`LtLotServiceImpl.java` | 加 quarantineByNcr / checkLotUsable |

---

## 四、(b)(c) 范围（仅写入计划，不实施）

### (b) ERP 采购收货建批——让 `wm_batch` 成为可信主档
- **现状（已核实）**：`wm_batch` 仅完工入库 `WmProductionRecptServiceImpl:161,224` 自动建批；采购收货 `PurchaseReceiveWritebackConsumer`（零批次引用）/`WmMaterialReceiveExecutorServiceImpl`（只透传 batchId 不建批）**不建批**；`received_qty/issued_qty` 无业务流维护（`updateQuantity` 仅测试/REST）；种子 8 行 warehouse/vendor/dates 全 NULL、qty 账全 0。ERP 有批次前端页（`views/erp/wm/batch`）。
- **范围（不实施）**：
  1. 采购收货确认/上架时调 `IWmBatchService.createBatchByMaterial(materialId, "PURCHASE_RECEIVE", receiveId, receiveNo, warehouseId, vendorBatchCode)` 建批，回填 `WmMaterialReceiveLine.batchId/batchCode`。
  2. 出入库策略（`ReceiveInStrategy`/`SalesOutStrategy`/`ProductionIssueStrategy` 等）调 `updateQuantity` 维护 `received_qty/issued_qty`。
  3. 补全 warehouse/vendor/production_date/expiry_date 字段来源（收货单需有供应商批次/仓库/日期）。
  4. ERP 批次前端页补录入项。
- **前置确认**：采购收货单是否已有 vendorBatchCode/warehouseId/生产日期字段；若无需先扩。

### (c) 双权威批次模型——采购件 ERP 主档 / WIP 件 MES 主档
- **方向**（不实施，待 (b) 落地后启动）：
  - **采购件**：ERP `wm_batch` 权威（收货建批，由 (b) 补全）→ MES `lt_lot` 加 `erp_batch_id` 引用（对齐 .trae 方案，但主档先可信）→ 车间消耗走 MES `consume` → MQ 回写 ERP `issued_qty`。
  - **WIP/车间件**：MES `lt_lot` 权威（工单/流转卡产生）→ MQ 回写 ERP（对齐 P0 主计划 §4.4.1 原方向"工单批次绑定回传 ERP"）。
  - **数据流**：收货(ERP建批)→MES引用→车间消耗(MES)→回写ERP；完工入库(ERP建成品批)→MES引用。
- **与 .trae 方案差异**：不把 ERP 当"不可编辑主档"；`erp_batch_id` 作可选交叉引用；方向按集成点谁权威决定；列表 N+1 改批量；跨库回填先确认同实例+权限。
- **触发条件**：(b) 完成 + P0-1 物料拉动真实需要按批次消耗 ERP 库存时启动。

---

## 五、实施顺序与验证

1. **A5/A6 SQL 整合** → 重写 01/02，删 03/04（含 iqc_id 列）。
2. **A1–A4 lt 代码修复** → `LtLotServiceImpl` 一次改完。
3. **B3 lt_lot iqc_id + applyIqcResult** → 实体/BO/VO/Service + `QcIqcServiceImpl` 钩子。
4. **B1 SN 绑定事件** → `ProSn*` + 事件 + 监听器。
5. **B2 工单 FEFO 领料** → Service/Controller/前端。
6. **B4/B5 契约方法** → `ILtLotService` 两方法。
7. **验证**：`mvn -pl ruoyi-modules/ruoyi-mes -am compile`；前端 `eslint`（仅 lt 相关文件，参考 `vue-tsc-baseline-noisy` 记忆用 grep 自查）；DDL 在 ry-mes 执行；菜单 SQL 在 ry-vue 执行；端到端：建批→FEFO 领料→消耗→(手动关联 iqc)IQC 回写 qc_status→隔离/放行→追溯。

## 六、风险

| 风险 | 应对 |
|---|---|
| B1 事件监听事务边界 | `@EventListener` 默认同步、同事务；若需独立事务用 `@TransactionalEventListener(AFTER_COMMIT)`。SN insert 回滚则绑定不写，符合预期。 |
| B2 confirmPick 多批次部分消耗 | 逐条 `consume`（已 `@Lock4j`），整体 `@Transactional`，任一失败回滚全部。 |
| B3 qc→lt 新依赖方向 | 确认 `lt` 不反向依赖 `qc`；同数据源 `ry-mes`。 |
| A5 重写 SQL 对已部署环境 | `CREATE TABLE IF NOT EXISTS` 幂等；附"已跑过旧 01+04 则跳过建表/改名"说明。 |
| B3 自动触发受限 | 文档明确：需先关联 lot↔iqc；全量自动属 (b)/(c)。 |
| 编译循环依赖 | B1 用事件、B3 单向 qc→lt，规避构造注入环。 |
