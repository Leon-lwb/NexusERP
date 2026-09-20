# ERP 基础数据域（bd）审计报告（2026-09-20）

> 审计人: 子代理 | 范围: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd`（126 个 java 文件：17 controller / 18 service impl / domain+bo+vo / mapper）
> 覆盖文件数: 约 34（含 DDL 局部与 `sys_menu`/`sys_dict_data` 检索） | 分支: feature/erp-mes-iot-audit-fix
> 说明：全部结论均基于本次**实际读过的当前代码**，未采信历史报告。DDL 行号来自 `script/sql/ry-erp.sql`，菜单/字典行号来自 `script/sql/ry-vue.sql`（按表名 grep 后局部 read，未整读大文件）。

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| ERP-BD-01 | P0 | 功能缺失/跨模块链路 | `bd_bom_detail.supply_type`（自备/外协厂供/客供）在 BO/VO 与前端类型中都不存在，永远写不进也读不出，委外快照恒为 `SELF_PROVIDED` | `BdBomDetail.java:109`、`BdBomDetailBo.java:51`、`PrdSubcontractClosureServiceImpl.java:181` |
| ERP-BD-02 | P0 | 状态机/数据错 | 低库存自动生成采购申请把 `tinyint status` 与字符串 `'ACTIVE'` 比较，实际只命中**停用**物料，启用物料全部被排除 | `PuPurchaseRequisitionServiceImpl.java:455`、`ry-erp.sql:1060` |
| ERP-BD-03 | P1 | 功能不可用/NULL | `BdBomDetailServiceImpl.selectById` 用 `List.of(selectVoById(id))`，查不到即 `List.of(null)` 抛 NPE（E11 同型残留，其余 service 已加判空） | `BdBomDetailServiceImpl.java:75`、`BdBomServiceImpl.java:113-116` |
| ERP-BD-04 | P1 | 数量/单位口径 | 主/辅单位换算体系整体未接线：`purchase_uom_id`/`inventory_uom_id`/`sale_uom_id` 与 `conversion_rate` 全仓零消费，所有数量恒按基本单位解释 | `ry-erp.sql:1047-1050`、`BdUnitOfMeasure.java:78`、`BdBomDetailServiceImpl.java:172` |
| ERP-BD-05 | P1 | 功能缺失 | `is_batch_manage`/`is_serial_manage` 只有实体/BO/VO 字段，全后端无任何读取点，批次/序列号管控开关不驱动物流行为 | `BdMaterial.java:104,110`、`BdMaterialBo.java:101,106` |
| ERP-BD-06 | P1 | 租户/DDL | `bd_material.uk_material_code`、`bd_bom.uk_parent_version`、`bd_material_vendor.uk_material_supplier` 唯一键缺 `tenant_id`，而同域的 `bd_routing.uk_routing_code_version` 含 `tenant_id` | `ry-erp.sql:1079,49,1628`、`ry-erp.sql:2035` |
| ERP-BD-07 | P1 | 状态机 | `updateByBo` 允许把唯一当前版本改为 `is_current=0`，无「至少一个当前版本」不变量，下游 MRP/计划/销售按 `is_current=1` 取 BOM 时静默取不到 | `BdBomServiceImpl.java:304-314`、`PrdProductionPlanServiceImpl.java:359,705` |
| ERP-BD-08 | P1 | 状态机/跨模块 | BOM **明细**编辑走「全删重插」且无已下达工单保护，而 BOM 删除有保护 → 删除守卫可被"改明细"绕过 | `BdBomDetailServiceImpl.java:130-136`、`BdBomServiceImpl.java:340-346` |
| ERP-BD-09 | P1 | 金额精度/健壮性 | 信用额度列为 `int(11)`（无法表示小数），且 `client_id` 无唯一索引却用 `selectOne` → 同客户多行信用记录时销售订单确认 500 | `ry-erp.sql:1003,1018`、`CreditCheckServiceImpl.java:47,55` |
| ERP-BD-10 | P1 | 校验/功能缺失 | 物料「停用」状态在 BOM 明细选择、按分类取物料、供应商报价报表等选择路径均未过滤，停用物料仍可进入 BOM/成本卷积 | `BdMaterialServiceImpl.java:144-147`、`BdBomDetailServiceImpl.java:162-165`、`WmStockAlertServiceImpl.java:154` |
| ERP-BD-11 | P2 | 契约/一致性 | 同一父项 BOM 的"是否有子级"判定与"取当前 BOM"两处 SQL 对 `status` 的过滤不一致，停用 BOM 仍显示展开箭头 | `BdBomMapper.java:27`、`BdBomMapper.java:48` |
| ERP-BD-12 | P2 | 租户 | `BdMaterialServiceImpl` 手写 `inSql` 子查询未带 `tenant_id`（MyBatis-Plus 租户插件不重写 `inSql`） | `BdMaterialServiceImpl.java:209-221` |
| ERP-BD-13 | P2 | 幂等/并发 | 物料-供应商默认供应商标记在 update 路径依赖前端回传 `materialId`，且无 DB 约束，可产生多个默认供应商 | `BdMaterialVendorServiceImpl.java:112-118` |
| ERP-BD-14 | P2 | 幂等 | 信用冻结幂等为"先查后写"，`so_credit_usage` 无 (so_id,status) 唯一键，并发确认可双占额度 | `CreditCheckServiceImpl.java:71-85` |
| ERP-BD-15 | P2 | 并发 | `activateVersion`（BOM/Routing）用逐行读-改-写而非条件更新，并发激活可产生多个 `is_current=1` | `BdBomServiceImpl.java:396-406`、`BdRoutingServiceImpl.java:264-274` |
| ERP-BD-16 | P2 | 校验/契约 | 物料-供应商新增先查后写，DB 唯一索引兜底会把 `DuplicateKeyException` 暴露成 500 而非可读提示 | `BdMaterialVendorServiceImpl.java:86-91`、`ry-erp.sql:1628` |
| ERP-BD-17 | P2 | 功能缺失 | 客户账期 `client_time`、客户等级 `client_level`、客户评分 `client_score` 无任何后端消费（等级字典 `bd_client_level` 为死字典） | `ry-erp.sql:1004-1006`、`ry-vue.sql:89983-89987` |
| ERP-BD-18 | P2 | DDL/脏数据 | `bd_bom_detail` 无 `(bom_id, child_material_id)` 唯一约束；且种子数据存在列数错位行（`ry-erp.sql:533` 出现 `NU` 字面量） | `ry-erp.sql:420-424`、`ry-erp.sql:533` |
| ERP-BD-19 | P2 | 字典 | `bd_source_type` 字典值仍是 `1/2/3`（自制/外购/委外）且注释标"已废弃"，与 `bd_material.source_type` 的 `PROCESS/PROCUREMENT/...` 编码不符（前端已改用 `prd_material_source_type`，影响有限） | `ry-vue.sql:90030-90032`、`ry-vue.sql:90876` |
| ERP-BD-20 | P2 | 健壮性 | `BdRoutingServiceImpl.saveSteps` 先删后插，步骤写入侧无序号连续性强制，`verifyRouting` 仅在手动校验时检查 | `BdRoutingServiceImpl.java:185-200`、`BdRoutingServiceImpl.java:337-345` |

## 1. P0 详述

### ERP-BD-01 BOM 供应方（supply_type）字段完全不可达，委外链路供应方语义恒为「自备」

- **证据**:
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/domain/BdBomDetail.java:109`（实体有 `private String supplyType;`）
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/domain/bo/BdBomDetailBo.java:51`（BO **无** `supplyType`，字段止于 `lossRate`）
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/domain/vo/BdBomDetailVo.java:62`（VO **无** `supplyType`，字段止于 `lossRate`）
  - `script/sql/ry-erp.sql:419`（列定义 `supply_type ... DEFAULT 'SELF_PROVIDED'`，注释「委外快照用」）
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/impl/PrdSubcontractClosureServiceImpl.java:181`（消费点：`line.setSupplyType(StringUtils.isBlank(d.getSupplyType()) ? SUPPLY_SELF : d.getSupplyType());`）
- **现状**: `grep supplyType` 在 `bd` 包内**只命中 1 处**（`BdBomDetail.java:109` 实体声明）。BO 与 VO 都没有该字段，`plus-ui/src/api/erp/bd/bom/types.ts` 等前端类型中也无 `supplyType`（`grep supplyType plus-ui/src` 零命中）。BOM 明细的全部写入口是 `BdBomDetailController` 的 `add/edit/saveBatch`（入参均为 `BdBomDetailBo`），因此该列只能取 DDL 默认值 `SELF_PROVIDED`；读出口（`BdBomDetailVo`）也不返回，前端无法展示或维护。
- **触发路径**: 任何用户想在 BOM 明细上把某行标成「客供 / 外协厂供」→ 表单无此控件；即使直接改库，`PrdSubcontractClosureServiceImpl` 重建委外 BOM 行时读到的也永远是 `SELF_PROVIDED`。
- **后果**: 委外/客供物料的供应方语义在下游完全丢失。委外快照 `prd_subcontract_bom_line.supply_type` 恒为 `SUPPLY_SELF`，导致委外发料/核销无法区分「我方提供」与「客供/外协厂提供」，客供料会被当作自备料参与发料与成本计算 —— 账面对不上，且因为列默认值合法而不会报错，问题静默。
- **修复建议**:
  1. 在 `BdBomDetailBo` 与 `BdBomDetailVo` 补 `private String supplyType;`（含 `@Size(max=32)`），使 BO→实体→VO 三向打通；`BdBomDetailServiceImpl.buildQueryWrapper` 增加 `supplyType` 等值筛选。
  2. 前端 `plus-ui/src/api/erp/bd/bom/types.ts` 与 `views/erp/bd/bom/form.vue` 明细行增加供应方下拉，并在 `sys_dict_data` 补 `bd_supply_type`（`SELF_PROVIDED/VENDOR_PROVIDED/CUSTOMER_PROVIDED`）——当前该字典类型不存在。
  3. 对 `PrdSubcontractClosureServiceImpl:181` 增加告警：当快照结果全为 `SUPPLY_SELF` 但物料 `source_type='SUBCONTRACT'` 时记录 warn，便于回归验证。
- **影响面**: `BdBomDetailBo/Vo`、`BdBomDetailServiceImpl`、前端 BOM 表单/明细表；**不需要 DDL**（列已存在）；需要补一条「BO 字段能写库、VO 能读回」的单测（`ruoyi-erp/src/test` 下现无 bd 包测试）。
- **测试现状**: 无。`ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/` 下只有 `so`/`pu` 包测试，bd 包零覆盖。

### ERP-BD-02 低库存自动生成采购申请：状态比较类型不符，实际只命中「停用」物料

- **证据**:
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/service/impl/PuPurchaseRequisitionServiceImpl.java:453-457`
    ```java
    List<BdMaterial> materials = bdMaterialMapper.selectList(
        new LambdaQueryWrapper<BdMaterial>()
            .eq(BdMaterial::getStatus, "ACTIVE")
            .isNotNull(BdMaterial::getSafetyStock)
            .gt(BdMaterial::getSafetyStock, BigDecimal.ZERO));
    ```
  - `script/sql/ry-erp.sql:1060`（`status tinyint(4) NULL DEFAULT 1 COMMENT '状态 0:停用 1:启用'`）
  - 对照正确写法：`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmStockAlertServiceImpl.java:154`（`.eq(BdMaterial::getStatus, 1L)`）
- **现状**: `BdMaterial.status` 是 `Long` 映射 `tinyint`，取值只有 `0/1`；`BdVendor.status` 才是字符串 `'ACTIVE'/'FROZEN'`（`BdVendor.java:90`，被 `PuPurchaseOrderServiceImpl.java:548-552` 正确使用）。这里把供应商的状态约定误用到了物料上。
- **触发路径**: 调用 `generateFromLowStock()`（低库存补货自动生成采购申请）。
- **后果**: MySQL 比较 `tinyint` 与字符串时把 `'ACTIVE'` 隐式转成数字 `0`，故条件实际等价于 `status = 0`：**只挑出已停用且安全库存 > 0 的物料**，而所有启用物料（`status = 1`）被全部排除。该功能要么一条都不生成（数据正常时），要么为停用物料生成采购申请（数据异常时）；两种情况都是错的且不报错。（若部署在隐式转换更严格的库上则会直接 SQL 报错。）
- **修复建议**: 把 `PuPurchaseRequisitionServiceImpl.java:455` 改为 `.eq(BdMaterial::getStatus, 1L)`；并顺手排查该文件内是否还有其它沿用字符串状态的位置。建议在 bd 包补一个常量/枚举（如 `BdMaterialStatus.ENABLED = 1L`）避免再次误用。
- **影响面**: `PuPurchaseRequisitionServiceImpl.generateFromLowStock()` 及其调用方（采购申请自动生成入口）；**不需要 DDL**；需要补一条「生成结果只含启用物料」的单测。
- **测试现状**: 无。`ruoyi-erp/src/test/java/com/nexus/erp/pu/` 下有契约测试但不覆盖本方法。

## 2. P1 详述

### ERP-BD-03 `BdBomDetailServiceImpl.selectById` 残留 `List.of(null)` NPE

- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdBomDetailServiceImpl.java:73-77`
  ```java
  public BdBomDetailVo selectById(Long id) {
      List<BdBomDetailVo> list = enrichMaterialName(List.of(baseMapper.selectVoById(id)));
      return list.isEmpty() ? null : list.get(0);
  }
  ```
  对照已修好的同型实现：`BdBomServiceImpl.java:112-116`（`if (vo == null) throw new ServiceException(...)`）、`BdMaterialVendorServiceImpl.java:71-76`（`if (vo != null)` 才富集）。
- **现状**: `selectVoById` 未命中返回 `null`，`List.of(null)` 在构造 `List` 时立刻抛 `NullPointerException`（`List.of` 拒绝 null 元素），`enrichMaterialName` 的 `list == null || list.isEmpty()` 空值保护根本来不及生效；第 76 行的 `list.isEmpty()` 判空是无效代码。这是规范 §3 提到的 E11「`List.of(null)` NPE」同型残留（E11 修复只覆盖了其它 service）。
- **触发路径**: `GET /erp/bd/bom/detail/{id}`，传一个已删除/不存在的 `detailId`（前端明细行删除后浏览器回退、或双击已删除行）。
- **后果**: 接口返回 500（`GlobalExceptionHandler` 捕获 NPE）而非 404/空结果；若该端点被前端轮询或批量调用会刷错误日志。不会造成数据损坏。
- **修复建议**: 改为
  ```java
  BdBomDetailVo vo = baseMapper.selectVoById(id);
  if (vo == null) { return null; }
  enrichMaterialName(new ArrayList<>(List.of(vo)));
  return vo;
  ```
  或统一改为 `BdBomServiceImpl` 的抛 `ServiceException("BOM明细不存在")` 风格（需同步前端处理文案）。
- **影响面**: 仅 `BdBomDetailController:74`；**不需要 DDL**；需要补一条「不存在 id 返回 null/业务异常而非 NPE」的单测。
- **测试现状**: 无。

### ERP-BD-04 主/辅计量单位换算体系整体未接线

- **证据**:
  - `script/sql/ry-erp.sql:1047-1050`：`base_uom_id` / `purchase_uom_id` / `inventory_uom_id` / `sale_uom_id` 四列并存
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/domain/BdUnitOfMeasure.java:78`：`private BigDecimal conversionRate;`
  - `grep getPurchaseUomId|getInventoryUomId|getSaleUomId` 于 `ruoyi-modules` → **0 命中**
  - `grep conversionRate|getConversionRate` 于 `ruoyi-modules` → 仅命中 `BdUnitOfMeasure.java:78`、`BdUnitOfMeasureVo.java:77`、`BdUnitOfMeasureBo.java:70`（纯 DTO，无业务消费）
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdBomDetailServiceImpl.java:172`（BOM 明细回填的是 `material.getBaseUomId()`，即明细单位恒为基本单位）
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdUnitOfMeasureServiceImpl.java:112-131`（`insertByBo/updateByBo` 只校验编码唯一，**不校验 `conversionRate` 是否 > 0**）
- **现状**: 换算率字段存在且可维护，但没有任何一处代码用它做过数量换算；采购/销售/库存三个辅单位列也没有任何读写点，库存与采购一律使用 `base_uom_id`（见 `wm/strategy/*` 与 `PuPurchaseReceivingServiceImpl.java:126`）。
- **触发路径**: 维护一个「采购单位 = 箱（1 箱 = 12 个）、基本单位 = 个」的物料 → 采购订单数量填 1（箱），系统按 1 个入库/记账。
- **后果**: 单位换算是纯装饰字段。凡采购/销售单位与基本单位不一致的物料，数量与金额会整体差一个换算倍数（金额错、库存错），且因为换算率字段看起来"已配置"而不会被使用者怀疑。属于规范 §2.1「单位换算（主单位/辅助单位）是否一致」的明确缺陷。
- **修复建议**: 二选一，不要维持现状：
  1. **接线**：在 `BdUnitOfMeasureServiceImpl` 增加 `convert(fromUomId, toUomId, qty)`（经 `primaryId` 归一，`conversionRate > 0` 校验，`BigDecimal` 高精度 + `HALF_UP`），在采购收货、销售发货、库存收发（`wm/strategy/*`、`PuPurchaseReceivingServiceImpl`）与 BOM 明细单位处调用；同时让 `purchase_uom_id/inventory_uom_id/sale_uom_id` 真正参与单据默认单位。
  2. **明确降级**：若本轮不接线，则在 `bd_material` 与 `bd_unit_of_measure` 的字段注释与前端表单上明确标注"暂不支持换算"，并把 `conversion_rate` 从可编辑表单移除，避免误配置；同时给 `conversionRate` 补 `> 0` 的 BO 校验。
- **影响面**: `BdUnitOfMeasureServiceImpl`、`BdMaterialBo/Vo`、`wm`/`pu`/`so` 所有数量入口；**不需要 DDL**（列已存在）；需要补换算单测（含 `rate=0`、跨基本单位换算）。
- **测试现状**: 无。

### ERP-BD-05 批次/序列号管控开关无任何后端消费

- **证据**:
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/domain/BdMaterial.java:104`（`private Long isBatchManage;`）、`:110`（`private Long isSerialManage;`）
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/domain/bo/BdMaterialBo.java:101,106`、`domain/vo/BdMaterialVo.java:115,121`（仅 DTO）
  - `script/sql/ry-erp.sql:1053-1054`（`is_batch_manage` / `is_serial_manage` 列）
  - `grep IsBatchManage|IsSerialManage|batchManage|serialManage|getBatchFlag|getSnFlag` 于 `ruoyi-modules` → **0 处业务消费**（仅上述实体/BO/VO 声明）
- **现状**: 两个开关可维护、可展示，但没有任何业务代码读取它们。`PdaIssueServiceImpl.java:150,178` 的 `isBatchUnqualified` 是按批次号查质量状态，与 `is_batch_manage` 无关。
- **触发路径**: 把物料设为「批次管理」→ 收发货、领料、报工流程完全不校验批次号/序列号，仍可无批次出入库。
- **后果**: 批次/序列号追溯链在该物料上形同虚设：用户可以不给批次号就入库出库，后续质量追溯（`pro` 侧 SN/批次追踪）拿不到数据。属"已建字段但无实现"的功能缺失。
- **修复建议**: 在收发货与领料入口按开关做**强制校验**：`wm` 收货/发货、`PdaIssueServiceImpl`、生产领料（`WmProductionIssueServiceImpl`）在 `is_batch_manage=1` 时要求 `batch_code` 非空、`is_serial_manage=1` 时要求 SN 明细数量与数量一致；并在 `BdMaterialController` 保存时禁止在已有库存/在途单据时把开关由 1 改为 0（需先消耗完）。**不需要 DDL**。
- **影响面**: `bd`（校验点声明）、`wm`/`pu`/`so`/`prd` 收发货与领料入口、MES `pro` 报工；需要补"开关开启时缺批次应报错"的单测。
- **测试现状**: 无。

### ERP-BD-06 基础数据唯一键缺 `tenant_id`（与同域 Routing 的约定不一致）

- **证据**:
  - `script/sql/ry-erp.sql:1079`：`UNIQUE INDEX uk_material_code(material_code)` —— 无 `tenant_id`
  - `script/sql/ry-erp.sql:49`：`UNIQUE INDEX uk_parent_version(parent_material_id, bom_version)` —— 无 `tenant_id`
  - `script/sql/ry-erp.sql:1628`：`UNIQUE INDEX uk_material_supplier(material_id, vendor_id)` —— 无 `tenant_id`
  - 对照正确实现：`script/sql/ry-erp.sql:2035`：`UNIQUE INDEX uk_routing_code_version(routing_code, routing_version, tenant_id)`
  - 多租户开启：`AGENTS.md`「多租户默认开启 (`tenant.enable: true`)」
- **现状**: 同属 `bd` 域的 `bd_routing` 唯一键已按 `(业务键..., tenant_id)` 建模，`bd_material`/`bd_bom`/`bd_material_vendor` 仍是单租户假设。种子数据里 `bd_material.material_code` 形如 `IF20260914001`、`bd_routing.routing_code` 形如 `RT20260914001`，均为租户无关的通用编码格式，各租户极易重码。
- **触发路径**: 租户 B 新建物料编码 `IF20260914001`（或沿用同一编码规范），而租户 A 已存在该编码。
- **后果**: 租户 B 的物料新增被 DB 唯一键拦下（`DuplicateKeyException` → 500），业务上表现为"这个编码明明在我这边没用过却建不了"。`bd_bom` 同理：`parent_material_id` 在小 ID 场景（种子数据 ID 为 1..N，非雪花）跨租户碰撞时，租户 B 无法为同 ID 物料建同版本 BOM。属规范 §2.2 明确要求核查的「唯一键缺 `tenant_id` 维度」。
- **修复建议**: 存量库改造脚本（需 DDL，按 `doc/system/数据库增量变更记录.md` 登记）：
  ```sql
  ALTER TABLE bd_material DROP INDEX uk_material_code,
    ADD UNIQUE INDEX uk_material_code_tenant(material_code, tenant_id);
  ALTER TABLE bd_bom DROP INDEX uk_parent_version,
    ADD UNIQUE INDEX uk_parent_version_tenant(parent_material_id, bom_version, tenant_id);
  ALTER TABLE bd_material_vendor DROP INDEX uk_material_supplier,
    ADD UNIQUE INDEX uk_material_supplier_tenant(material_id, vendor_id, tenant_id);
  ```
  同步更新主脚本 `script/sql/ry-erp.sql` 的 `CREATE TABLE`。执行前需先扫描是否已存在跨租户重码数据（否则加索引会失败）。
- **影响面**: `script/sql/ry-erp.sql`、存量库升级脚本；需要补一条"两个租户可用同一物料编码"的集成测试。
- **测试现状**: 无多租户隔离测试。

### ERP-BD-07 无「至少一个当前 BOM 版本」不变量，`updateByBo` 可把唯一当前版本置为非当前

- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdBomServiceImpl.java:304-314`
  ```java
  public int updateByBo(BdBomBo bo) {
      checkVersionUnique(bo.getParentMaterialId(), bo.getBomVersion(), bo.getBomId());
      if (bo.getIsCurrent() != null && bo.getIsCurrent() == 1L) {
          clearCurrentFlag(bo.getParentMaterialId());   // 只在置 1 时处理
      }
      BdBom entity = MapstructUtils.convert(bo, BdBom.class);
      return baseMapper.updateById(entity);             // isCurrent=0 直接落库，无兜底
  }
  ```
  消费端按 `is_current = 1` 取 BOM：`PrdProductionPlanServiceImpl.java:359`、`:705`、`SoSalesOrderHeaderServiceImpl.java:1297`、`PrdSubcontractClosureServiceImpl.java:93`、`BdBomMapper.java:27,48`。
- **现状**: `clearCurrentFlag`（`:319-327`）只在 `isCurrent=1` 时先清同父项其它版本；`isCurrent=0` 时不校验"清掉之后是否还有当前版本"，也没有 DB 层面的部分唯一索引或应用层兜底。`activateVersion`（`:387-407`）是唯一保证"有且仅有一个当前版本"的入口，但它是可选操作。
- **触发路径**: 在 BOM 编辑表单把「是否当前版本」从"是"改为"否"并保存（前端若原样回传 `isCurrent=0` 即触发）。
- **后果**: 该物料变成"没有任何当前版本 BOM"。下游 MRP/生产计划/销售订单的 BOM 查询（均带 `isCurrent=1`）返回空，结果是**静默地不展开 BOM**——物料需求为空、成本卷积为 0，而不是报错。这类静默空结果会直接导致漏采购/漏发料。
- **修复建议**: 在 `updateByBo` 中，当 `isCurrent == 0` 且该 BOM 原本 `isCurrent == 1` 时，先统计同父项其它 `isCurrent=1` 的数量，为 0 则抛 `ServiceException("该物料至少需要一个当前版本 BOM，请先激活其它版本")`；并在 `BdBomController` 的 `activateVersion` 前端提示中明确该约束。更稳妥可在 `bd_bom` 上加生成列 + 唯一索引做 DB 级约束（需 DDL，可选）。
- **影响面**: `BdBomServiceImpl.updateByBo`、BOM 编辑表单（`plus-ui/src/views/erp/bd/bom/form.vue`）、所有按 `isCurrent=1` 取 BOM 的 `prd`/`so` 调用方；**不需要 DDL**（应用层即可）；需要补"取消唯一当前版本应报错"的单测。
- **测试现状**: 无。

### ERP-BD-08 BOM 明细编辑无已下达工单保护，删除守卫可被绕过

- **证据**:
  - 删除有保护：`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdBomServiceImpl.java:337-346`
    ```java
    Long referencingCount = workOrderMapper.selectCount(Wrappers.lambdaQuery(PrdWorkOrder.class)
        .in(PrdWorkOrder::getBomId, idList)
        .notIn(PrdWorkOrder::getWoStatus, WORK_ORDER_TERMINAL_STATUS));
    if (referencingCount != null && referencingCount > 0) {
        throw new ServiceException("所选BOM已被" + referencingCount + "张未完成的生产工单引用，无法删除");
    }
    ```
  - 明细编辑**无**保护：`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdBomDetailServiceImpl.java:125-137`
    ```java
    Long bomId = list.get(0).getBomId();
    baseMapper.delete(Wrappers.<BdBomDetail>lambdaQuery().eq(BdBomDetail::getBomId, bomId));
    List<BdBomDetail> entities = list.stream().map(...).toList();
    entities.forEach(baseMapper::insert);
    ```
  - 计划侧实时读 BOM 明细：`PrdProductionPlanServiceImpl.java:71`（注入 `BdBomDetailMapper`）；委外快照重建读实时明细：`PrdSubcontractClosureServiceImpl.java:98,153`。
- **现状**: 删除整张 BOM 时校验未完成工单，但"改明细"走 `BdBomDetailController` 的 `/batch`（`saveBatch`，全删重插）或 `edit`，完全不查工单引用。二者对同一份数据的破坏力等价（都会改变工单的用料基准），守卫强度却完全不同。
- **触发路径**: BOM 已被 3 张 `IN_PROGRESS` 工单引用 → 直接删 BOM 会被拒；改为进入明细页把所有行删掉重填（或改单耗/损耗率）保存 → 成功。
- **后果**: 已下达工单的物料需求基准被静默改写。若工单下发时未做 BOM 快照（委外路径有快照，普通工单路径无对应证据），则领料/倒冲会按新 BOM 执行，出现"实际发料与工单评审时不一致"，成本与损耗核算失真，且无审计痕迹。
- **修复建议**: 把 `BdBomServiceImpl.deleteByIds` 中的工单引用校验抽成 `private void assertNoOpenWorkOrderReference(List<Long> bomIds)`，在 `BdBomDetailServiceImpl.saveBatch/updateByBo/deleteByIds` 以及 `BdBomAlternativeServiceImpl` 的批量保存前一并调用（依据 `detailId → bomId` 反查）。若业务上允许改，则必须在改前把受影响工单的用料明细做一次快照，并记录变更日志。**不需要 DDL**。
- **影响面**: `BdBomDetailServiceImpl`、`BdBomAlternativeServiceImpl`、`prd` 工单用料路径；需要补"有在制工单时改明细应被拒"的单测。
- **测试现状**: 无。

### ERP-BD-09 客户信用额度：整数列 + `selectOne` 无唯一键保障

- **证据**:
  - `script/sql/ry-erp.sql:1003`：`client_credit int(11) NULL DEFAULT NULL COMMENT '信用额度'`
  - `script/sql/ry-erp.sql:1018`：`INDEX idx_client_id(client_id)` —— **非唯一**，`bd_client_credit` 无任何 `UNIQUE` 约束
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/so/service/impl/CreditCheckServiceImpl.java:45-55`
    ```java
    creditLqw.eq(BdClientCredit::getClientId, clientId);
    BdClientCredit credit = bdClientCreditMapper.selectOne(creditLqw);   // 多行即抛异常
    ...
    BigDecimal creditLimit = BigDecimal.valueOf(credit.getClientCredit());
    ```
- **现状**: `bd` 域只提供 `BdClientCreditMapper`（`mapper/BdClientCreditMapper.java:12`），没有对应 service/controller 做写入与唯一性维护，因此 `client_id` 重复行完全可以由 SQL 或后续功能引入。`selectOne` 在结果 >1 行时抛 `TooManyResultsException`。此外额度是 `int`，与 `SoCreditUsage.amount`（`BigDecimal`）口径不一致。
- **触发路径**: ①同一 `client_id` 存在两行信用配置（如按年度/组织各插一行）→ 该客户任一销售订单确认即 500；②额度需要小数（如 100000.50）→ 无法录入。
- **后果**: ①**销售订单确认直接失败**（业务中断），且错误是 MyBatis 异常而非可读提示；②信用额度只能精确到 1 元，与应收金额的小数口径不一致，风控边界有 ±1 元级别偏差（影响小，但口径不统一）。
- **修复建议**:
  1. 加唯一约束（需 DDL，登记增量变更）：`ALTER TABLE bd_client_credit ADD UNIQUE INDEX uk_client_tenant(client_id, tenant_id);`（执行前先清理重复行）。
  2. `CreditCheckServiceImpl:47` 改为 `selectList` + 取首行并 `log.warn` 重复，或保留 `selectOne` 但在唯一约束上线后再切换。
  3. 额度列改 `decimal(18,2)`（需 DDL），`CreditCheckServiceImpl:55` 改为直接使用 `BigDecimal`，避免 Integer→long 隐式提升的语义混淆。
  4. 为 `bd_client_credit` 补一个 bd 域内的维护入口（当前无 controller/service），否则额度只能靠手工改库。
- **影响面**: `script/sql/ry-erp.sql`、`CreditCheckServiceImpl`、`so` 订单确认链路、前端客户信用维护页（当前缺失）；需要补"重复信用行不应 500"的单测。
- **测试现状**: 无（`ruoyi-erp/src/test` 下无 `CreditCheckServiceImpl` 相关用例）。

### ERP-BD-10 物料「停用」状态在多数选择路径未被过滤

- **证据**:
  - `grep BdMaterial::getStatus` 于 `ruoyi-modules` → 仅 **3 处**：`PuPurchaseRequisitionServiceImpl.java:455`（且类型写错，见 ERP-BD-02）、`BdMaterialServiceImpl.java:201`（仅当调用方显式传 `status` 才生效）、`WmStockAlertServiceImpl.java:154`（`.eq(BdMaterial::getStatus, 1L)`，正确）
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdMaterialServiceImpl.java:144-147`（`selectByCategoryIds` 只按 `categoryId` 过滤，无 `status`）
  - `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdBomDetailServiceImpl.java:162-165`（`enrichMaterialName` 批量取物料时不带 `status`，明细可引用停用物料）
  - `BdMaterialVendorReportController.java:48,117`（直接 `materialMapper.selectById(materialId)`，无状态校验）
- **现状**: 只有"库存预警"一处强制只取启用物料。BOM 明细选料、按分类取物料（分类树点击）、物料-供应商报价报表等路径都不过滤停用物料；`BdMaterialController` 列表页依赖前端传 `status` 才过滤。
- **触发路径**: 停用某物料（`status=0`）→ 在 BOM 明细行选择该物料 → 成功加入；随后 BOM 展开、成本卷积、MRP 全部把它计入。
- **后果**: 「停用」语义不生效，停用物料仍能进入新 BOM 与需求计算，导致为已淘汰物料采购/备料；同时因为列表页默认不过滤，用户很难察觉。属规范 §2.1「物料状态是否在采购/销售/BOM 选择处被过滤」的明确缺口。
- **修复建议**: 在选择器语义的接口上强制 `status = 1`（新增 `selectEnabledByCategoryIds` 供 BOM/订单选料使用），`BdBomDetailServiceImpl` 保存前校验子物料 `status = 1` 并抛可读异常；列表页保持可查停用物料（这是合理的），但选择器必须过滤。**不需要 DDL**。
- **影响面**: `BdMaterialServiceImpl`、`BdBomDetailServiceImpl`、前端 BOM 明细选料组件；需要补"停用物料不能加入 BOM"的单测。
- **测试现状**: 无。

## 3. P2 详述

| 编号 | 结论 | 证据 | 后果与建议 |
|---|---|---|---|
| ERP-BD-11 | 同域两条 SQL 对 `status` 的过滤不一致 | `BdBomMapper.java:27`（`is_current = 1 AND status = 1 AND del_flag = '0'`）vs `:48`（`b.is_current = 1 AND b.del_flag = '0'`，无 `status`） | 停用 BOM 仍会被 `findBomIdsWithChildren` 计入，前端树形表格显示"可展开"箭头，点开后为空。建议 `:48` 补 `AND b.status = 1`。不需 DDL。 |
| ERP-BD-12 | 手写 `inSql` 子查询未带 `tenant_id` | `BdMaterialServiceImpl.java:209-221`（`"SELECT material_id FROM wm_material_stock WHERE del_flag = '0'"` + 字符串拼接） | MyBatis-Plus 租户插件**不重写** `inSql`/原生片段，注释里"同处 erp 数据源"的推论不成立。因外层查询已注入 `tenant_id`，跨租户实际泄漏面有限（`material_id` 全局唯一），但违反规范 §2.1 隔离规则，且该片段被复制到别处即会真泄漏。建议在子查询中显式加 `tenant_id` 条件（取 `TenantHelper.getTenantId()` 后**参数化**，勿再拼接）。 |
| ERP-BD-13 | 默认供应商标记可产生多行 | `BdMaterialVendorServiceImpl.java:112-118`（仅当 `bo.getIsDefault()==1 && bo.getRelationId()!=null && bo.getMaterialId()!=null` 才清其它默认；`ry-erp.sql:1631` 只有普通索引 `idx_default`） | 前端未回传 `materialId`（部分更新场景）时"同一物料只有一个默认供应商"的不变量失效，采购下单取默认供应商的结果不确定。建议按 `relationId` 反查 `materialId`（不信任入参），并加"每物料至多一个默认"的 DB 约束（需 DDL，可用生成列 + 唯一索引）。 |
| ERP-BD-14 | 信用冻结幂等为先查后写 | `CreditCheckServiceImpl.java:71-85`（`selectCount(...FROZEN)` 通过后 `insert`） | 两次并发确认（双击/重试）都通过检查 → 同一订单两条 `FROZEN` 记录，额度被双占。建议对 `so_credit_usage(so_id, status)` 加唯一索引（需 DDL）或改用 `INSERT ... ON DUPLICATE KEY`/分布式锁。 |
| ERP-BD-15 | 激活版本用逐行读-改-写 | `BdBomServiceImpl.java:396-406`、`BdRoutingServiceImpl.java:264-274`（先 `selectList` 再逐条 `updateById` 置 0，最后置 1） | 两个并发 `activateVersion` 交错时可留下两行 `is_current=1`，破坏"仅一个当前版本"假设。建议改为单条条件更新：`UPDATE ... SET is_current=0 WHERE parent_material_id=? AND is_current=1` 后再置目标行，并加唯一约束兜底（可选 DDL）。 |
| ERP-BD-16 | 唯一冲突暴露为 500 | `BdMaterialVendorServiceImpl.java:86-91`（先 `selectCount` 再 `insert`，`ry-erp.sql:1628` 有唯一索引） | 并发插入时 DB 抛 `DuplicateKeyException` → 全局处理器返回 500 而非"该物料与供应商的关系已存在"。参考 `BdRoutingServiceImpl.java:110-115` 的 `catch (DuplicateKeyException)` 写法补齐。 |
| ERP-BD-17 | 账期/等级/评分无消费 | `ry-erp.sql:1004-1006`（`client_time`/`client_level`/`client_score`）；`ry-vue.sql:89983-89987`（`bd_client_level` 字典）；`grep getClientTime\|getClientLevel\|getClientScore` 于 `ruoyi-modules` → 0 命中 | 客户账期不参与应收到期日/账龄计算，等级/评分不参与任何风控或排序（信用检查只用 `client_credit`），等级字典成为死字典。建议明确：要么把 `client_time` 接入应收账龄与信用检查（超期客户拦截），要么在前端标注"仅记录，不参与计算"。 |
| ERP-BD-18 | BOM 明细缺业务唯一键 + 种子数据脏行 | `ry-erp.sql:420-424`（仅 `idx_bom_id`/`idx_child_material`，无 `(bom_id, child_material_id)` 唯一）；`ry-erp.sql:533`（`bd_bom_detail` 插入行出现字面量 `NU`，列数错位） | 同一 BOM 下同一子物料可重复成行；`explodeBom`（`BdBomServiceImpl.java:741-745`）按 `materialId` 合并，故成本不会重复计，但明细页会出现重复行且 `sort_order`/`operation_step` 语义混乱。建议加唯一约束（需 DDL）并在保存时给出提示；`ry-erp.sql:533` 脏行属主脚本缺陷需修正。 |
| ERP-BD-19 | `bd_source_type` 字典与列取值不符 | `ry-vue.sql:90030-90032`（值为 `1/2/3`，自制/外购/委外）、`ry-vue.sql:90876`（注释"已废弃，统一使用 prd_material_source_type"）；`ry-erp.sql:1052`（列存 `PROCESS/PROCUREMENT/SUBCONTRACT/PHANTOM/CFM/TRANSFER`） | 任何仍绑 `bd_source_type` 的 `dict-tag` 都会显示原始码。经核查 `views/erp/bd/material/index.vue:463-464`、`views/erp/bd/bom/index.vue:382-383` 已改用 `prd_material_source_type`，故当前**前端无实际故障**，但死字典应清理以免后续误用。 |
| ERP-BD-20 | 工艺路线步骤写入侧无序号连续性强制 | `BdRoutingServiceImpl.java:185-200`（`saveSteps` 先删后插，直接按 `sequenceNum` 落库）；`verifyRouting`（`:337-345`）才校验"序号必须为 1..n 连续" | 校验只在用户手动点"校验"时执行，保存时不拦。存在 `sequenceNum` 缺号/重复的路线可正常保存，MES 侧按 `orderByAsc(sequenceNum)` 取步骤（`:311-315`）会出现跳序派工。建议在 `saveSteps` 内按入参顺序重排 `sequenceNum`，或保存时调用 `verifyRouting` 并抛异常。 |

## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | BOM 供应方（自备/外协厂供/客供）维护与传递 | `BdBomDetail.java:109` 有字段，BO/VO/前端类型均无；`PrdSubcontractClosureServiceImpl.java:181` 已在消费 | 列 `bd_bom_detail.supply_type`、实体字段、下游 `prd_subcontract_bom_line.supply_type` 快照 | 0.5~1 人日（补 BO/VO/前端 + 字典 `bd_supply_type`） |
| 2 | 主/辅计量单位换算 | `ry-erp.sql:1047-1050`、`BdUnitOfMeasure.java:78` 有字段，全仓 0 消费 | 4 个单位外键列 + `conversion_rate` + `primary_id`/`is_base` 层级模型 | 3~5 人日（换算服务 + 采购/销售/库存/BOM 全部数量入口接线 + 单测） |
| 3 | 批次/序列号管控开关驱动物流行为 | `BdMaterial.java:104,110` 有开关，0 消费 | 开关字段、`pro` 侧 SN/批次追踪表、PDA 收发货 | 2~3 人日（收发货/领料/报工强制校验 + 开关回退保护） |
| 4 | 客户信用额度的维护入口与唯一性 | `bd_client_credit` 仅有 Mapper（`BdClientCreditMapper.java:12`），无 controller/service；无唯一索引 | 表 + 实体 + `CreditCheckServiceImpl` 消费端 | 1~2 人日（CRUD + 唯一约束 + 重复行清理） |
| 5 | 客户账期参与应收账龄/信用拦截 | `ry-erp.sql:1004` 有 `client_time`，0 消费 | 账期列、`CreditCheckServiceImpl`、`so` 应收链路 | 1~2 人日 |
| 6 | 供应商评估字段的落库与展示 | `BdVendor.java:75-90` 仅 `industry/region/category/status`，无评估分/评级列；`PuVendorEvaluationServiceImpl.java:169` 只按 `status='ACTIVE'` 取供应商 | `pu` 侧评估服务（消费端存在，主数据侧无评估字段） | 1~2 人日（需 DDL 加评估列或建评估表） |
| 7 | BOM 变更对已下达工单的快照 | `BdBomDetailServiceImpl.java:130` 明细可随时改写；仅整表删除有工单守卫（`BdBomServiceImpl.java:340`） | `PrdWorkOrder`、`WORK_ORDER_TERMINAL_STATUS` 常量、委外侧已有快照范式（`PrdSubcontractBomLine`） | 2~4 人日（工单用料快照表 + 变更审计） |

## 5. 已核查确认无问题（证明覆盖范围）

- **BOM 循环引用防护**：`BdBomServiceImpl.getBomTree`（`:507-508`）与 `getIndentedBom`（`:638-639`）均以根物料初始化 `visited`，递归时传**副本**（`:576-577`、`:714-715`），兄弟分支互不污染；命中即抛 `ServiceException`（`:539-541`、`:668-670`）。另有 `MAX_BOM_DEPTH` 深度兜底（`:62`、`:535-537`、`:660-662`）。**防护有效**。
- **BOM 循环引用预校验**：`checkCircularRefWithContext`（`:448-472`）同样按路径传副本（`:464-465`），逻辑正确。
- **幽灵件（PHANTOM）展开**：`buildTreeNodeWithContext`（`:565-566`、`:584-587`）与 `buildIndentedLinesWithContext`（`:696-711`）均以物料 `is_phantom` 判定，虚拟件自身成本置 0（`:708-711`），下层用量按 `parentCumulativeQty` 相乘传递（`:717`）；`explodeBom`（`:733-748`）只取 `PART` 叶子并按物料合并累计用量与行成本。**跳层与成本不重复计的实现是自洽的**。
- **单耗与损耗率**：树/缩进/成本三处口径一致，均用 `qty * (1 + lossRate/100)` 且 `HALF_UP` 保留 6 位（`:553-555`、`:683-686`）；与 `PrdBackflushServiceImpl.java:94-97`、`PrdSubcontractClosureServiceImpl.java:173-178` 的百分数口径一致（未出现百分数/小数混用）。
- **BOM 成本卷积**：`costRollup`（`:1020-1098`）复用 `explodeBom` 叶子聚合，工序成本 `computeStepCost`（`:1104-1122`）对缺失费率返回 `null` 并置 `routingCostAvailable=false`、总工序成本归 0（`:1085-1095`），**不会把"不可计算"伪装成 0 成本**（前端可据 `routingCostAvailable` 提示）。`materialCost`（`:753-764`）优先标准成本、次参考价、均空返回 0，口径明确。
- **BOM 版本号唯一性**：`checkVersionUnique`（`:366-378`）在 insert/update 均做预校验，DB 侧 `uk_parent_version`（`ry-erp.sql:49`）兜底；`copyBom` 新版本显式置 `isCurrent=0`（`:838`）避免复制即抢占当前版本。
- **BOM 删除的工单保护**：`deleteByIds`（`:337-346`）用 `notIn(WORK_ORDER_TERMINAL_STATUS)`（`:75` 定义为 `COMPLETED/CLOSED/CANCELLED`）统计未完成工单并阻断，级联删除替代料与明细（`:348-354`）在同一 `@Transactional` 内。
- **`selectById` 判空（E11 复核）**：`BdBomServiceImpl.java:113-116`、`BdMaterialVendorServiceImpl.java:72-76`、`BdUnitOfMeasureServiceImpl.java:211-214`、`BdMaterialCategoryServiceImpl.java:236-238`、`BdRoutingServiceImpl.java:132-135`、`BdMaterialServiceImpl.java:85-87` 均已判空或抛业务异常 —— **唯 `BdBomDetailServiceImpl.java:75` 残留**（已列 ERP-BD-03）。
- **分类树循环引用**：`BdMaterialCategoryServiceImpl.validateCategory`（`:317-334`）同时校验"不以自身为父"（`:323-325`）与"新父级不能是自身子孙"（`:326-333`，`findInSet(cid, "path_ids")`）；`fillPathAndLevel`（`:339-349`）由服务端权威计算 `path_ids/path_names/level_num`，不信任客户端传入。
- **分类停用级联**：`updateStatus`（`:233-261`）停用时按 `find_in_set` 级联停用全部子孙（`:244-259`），启用时只启自身 —— `:231` 注释说明为有意设计（避免误启用祖先链），语义合理，非缺陷。
- **工艺路线编码自动生成与查重**：`insertByBo/updateByBo`（`:99-153`）编码为空时走 `autoCodeUtil.genSerialCode`（`:102`、`:137`），并做 `assertCodeVersionUnique`（`:164-173`）预校验 + `catch (DuplicateKeyException)` 兜底（`:110-115`、`:146-150`），且 DB 唯一索引 `uk_routing_code_version(routing_code, routing_version, tenant_id)`（`ry-erp.sql:2035`）真实存在 —— 是本仓库唯一键建模与兜底都做对的正面范例。
- **工艺路线总工时计算**：`computeTotals`（`:205-222`）把 `standardHours` 与 `setupHours` 分别累加且各自判空，空步骤时显式置 0，不会 NPE。
- **`@SaCheckPermission` 权限码种子完整性**：把 `bd` 包 17 个 controller 的 117 处 `@SaCheckPermission` 全部提取后与 `ry-vue.sql` 的 `sys_menu` 比对，**全部命中种子**：`bd:material:*`（`ry-vue.sql:91967-91972`）、`bd:bom:*`（`:91973-91978`）、`bd:client:*`（`:91979-91984`）、`bd:vendor:*`（`:91985-91990`）、`bd:category:*`（`:91961-91966`）、`bd:measure:*`（`:91991`、`:92001-92005`）、`bd:workshop:*`（`:91992`、`:92031-92035`）、`bd:workstation:*`（`:91993`、`:92036-92040`）、`bd:serialNumberRule:*`（`:91994-92000`）、`bd:materialVendor:*`（`:92006-92011`）、`bd:process:*`（`:92345-92350`）、`bd:routing:*`（`:92351-92356`）。**含 camelCase 的 `bd:materialVendor:*` / `bd:serialNumberRule:*` 也一致**，未出现本项目高发的"权限码无种子 → 403"。
- **`@Validated` 标注完整性**：`BdMaterialController:26`、`BdBomController:30`、`BdClientController:26`、`BdVendorController:26`、`BdRoutingController:27`、`BdProcessController:26`、`BdMaterialCategoryController:29`、`BdUnitOfMeasureController:26`、`BdWorkshopController:26`、`BdWorkstationController:26`、`BdSerialNumberRuleController:29`、`BdMaterialVendorController:30`、`BdBomDetailController:26`、`BdBomAlternativeController:22`、`BdCategoryAttributeController:23`、`BdMaterialVendorReportController:28` 均在类级标了 `@Validated`，且 add/edit 的 `@RequestBody` 参数上另有 `@Validated`（如 `BdMaterialController:88,103`、`BdBomController:92,107`），BO 校验生效。
- **前后端端点可达性**：把 17 个 controller 的 134 处映射与 `plus-ui/src/api/erp/bd/**` 的 95 个 `url:` 逐一比对，**全部可达**；`BdMaterialVendorReportController` 的三个报表端点虽不在 `api/erp/bd` 下，但已由 `plus-ui/src/api/erp/pu/price-comparison/index.ts:9`（`BASE = '/erp/bd/materialVendor/report'`，`:18` `/priceComparison`、`:43` `/priceRanking`）接入。未发现"后端有端点但前端无函数"。
- **批量删除的租户/逻辑删除**：`BdBomDetailServiceImpl.java:114`、`BdBomAlternativeServiceImpl.java:101`、`BdMaterialVendorServiceImpl.java:130`、`BdUnitOfMeasureServiceImpl.java:186` 均走 MP 的 `deleteBatchIds`/`delete(in ...)`，由租户插件与逻辑删除插件接管，无原生 SQL 拼接。

## 6. 前后端字段双向不一致清单

| 字段 | 后端(BO/VO/表) | 前端(types/表单/列表) | 结论 |
|---|---|---|---|
| `supplyType` | 表 `bd_bom_detail.supply_type`（`ry-erp.sql:419`）有；实体 `BdBomDetail.java:109` 有；**BO 无**（`BdBomDetailBo.java:51` 止于 `lossRate`）、**VO 无**（`BdBomDetailVo.java:62` 止于 `lossRate`） | `plus-ui/src/api/erp/bd/bom/types.ts` 及 `views/erp/bd/bom/form.vue` 均无（`grep supplyType plus-ui/src` 0 命中） | **三层全断**：列有、实体有、BO/VO/前端无 → 无法写入也无法展示，委外快照恒为默认值。见 ERP-BD-01 |
| `isBatchManage` / `isSerialManage` | 表有（`ry-erp.sql:1053-1054`）、实体/BO/VO 都有（`BdMaterial.java:104,110`） | 物料表单有开关（`views/erp/bd/material/index.vue` 绑定 `sys_true_or_false`） | 前后端字段齐全，但**后端无消费**（见 ERP-BD-05）—— 属"能填不能用"，不是字段不一致 |
| `conversionRate` | 表有、实体/BO/VO 都有（`BdUnitOfMeasure.java:78`） | `plus-ui/src/api/erp/bd/measure/types.ts` 有 | 字段齐全但**全仓无换算逻辑**（见 ERP-BD-04） |
| `purchaseUomId` / `inventoryUomId` / `saleUomId` | 表有（`ry-erp.sql:1048-1050`） | 后端 0 消费（`grep getPurchaseUomId` 0 命中） | 前端即使绑定也无实际效果（见 ERP-BD-04） |
| `clientTime` / `clientLevel` / `clientScore` | 表有（`ry-erp.sql:1004-1006`） | 无对应前端维护页（`bd` 包无 `BdClientCredit` 的 controller/service） | 仅存库、无读写入口（见 ERP-BD-17） |
| `sourceType` | 列存 `PROCESS/PROCUREMENT/...`（`ry-erp.sql:1052`） | 前端用 `prd_material_source_type`（`views/erp/bd/material/index.vue:463-464`、`bom/index.vue:382-383`） | **一致**；历史遗留的 `bd_source_type` 字典（值 `1/2/3`）未被使用，建议清理（见 ERP-BD-19） |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交）**
  - `ERP-BD-02`：`PuPurchaseRequisitionServiceImpl.java:455` 改 `.eq(BdMaterial::getStatus, 1L)`。单行改动，零风险，立即恢复低库存补货功能。
  - `ERP-BD-01`：补 `BdBomDetailBo/Vo.supplyType` + `bd_supply_type` 字典 + BOM 明细表单下拉，打通委外供应方链路。
- **批次 B（P1，按模块分批）**
  - `ERP-BD-03`（1 行改动，可与 A 合并提交）、`ERP-BD-10`（选择器状态过滤）、`ERP-BD-07`（当前版本不变量）、`ERP-BD-08`（明细变更工单守卫）。
  - `ERP-BD-06`（唯一键补 `tenant_id`，需 DDL + 存量数据扫描 + 登记 `doc/system/数据库增量变更记录.md`）。
  - `ERP-BD-09`（信用表唯一约束 + `decimal` 额度 + bd 域维护入口）。
- **批次 C（P1 大件，需排期）**
  - `ERP-BD-04`（单位换算接线，跨 `pu`/`so`/`wm`/`bd`）、`ERP-BD-05`（批次/序列号强制校验，跨 `wm`/`pu`/`pro`）。这两项需先与业务确认"是否本轮支持"，否则退化为明确降级 + 字段标注。
- **批次 D（P2，随手清理）**：`ERP-BD-11`~`ERP-BD-20`，其中 `ERP-BD-15`/`ERP-BD-14` 建议与批次 B 的状态机改造一起做（同为并发正确性）；`ERP-BD-18` 的 `ry-erp.sql:533` 脏行属主脚本缺陷，需单独修正。

## 8. 未覆盖范围

- **未逐一读取的 bd 文件**（约 90 个）：全部 `domain/vo/*`（除 `BdBomDetailVo`/`BdMaterialVo`/`BdUnitOfMeasureVo` 经 grep 核对字段）、`domain/BdCategoryAttribute`/`BdMaterialAttributeValue`/`BdSerialNumberRule`/`BdWorkshop`/`BdWorkstation`/`BdProcess`/`BdRouting`/`BdBomAlternative`、`mapper/*`（除 `BdBomMapper`/`BdBomDetailMapper`/`BdClientCreditMapper`），以及 `BdCategoryAttributeServiceImpl`、`BdMaterialAttributeValueServiceImpl`、`BdSerialNumberRuleServiceImpl`、`BdWorkshopServiceImpl`、`BdWorkstationServiceImpl`、`BdProcessServiceImpl`、`BdBomAlternativeServiceImpl`、`BdServiceHelper`。这些多为标准 CRUD，风险低于本报告覆盖部分，但**未做逐行核验**。
- **未验证的跨模块契约**：`MesErpBridgeService` 对 BOM/工艺路线的消费契约（仅从 `ErpBomHeaderMapper.java:15`、`ErpMaterialMapper.java:36`、`ErpRoutingHeaderMapper.java:16` 的 grep 结果确认它们都带 `is_current = 1`，未读完整 SQL 与 MES 侧映射）；`bd_bom` 变更对 MES 工单的实际影响未在 MES 侧验证。
- **未核验**：`script/sql/ry-erp.sql` 的 `bd_workshop`/`bd_workstation`/`bd_process`/`bd_category_attribute`/`bd_material_category`/`bd_unit_of_measure`/`bd_vendor`/`bd_client` 等 DDL（仅确认表存在与行号）；`bd_*` 表索引充分性；`ry-vue.sql` 中 `bd` 相关菜单的 `component` 路径与 `plus-ui/src/views/erp/bd/**` 实际文件名的逐一对齐（仅确认菜单行存在且权限码一致）。
- **未做的验证**：本报告为静态只读审计，未运行任何测试或启动应用；所有"后果"均为基于代码与 DDL 的推理。`ERP-BD-02` 的 MySQL 隐式类型转换行为建议在真实库上以一条 `SELECT status FROM bd_material WHERE status = 'ACTIVE'` 复核确认。
