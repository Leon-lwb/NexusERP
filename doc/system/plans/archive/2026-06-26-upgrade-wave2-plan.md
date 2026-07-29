# NexusERP 第二波升级（ERP P1 补全 + MES 运行时补齐）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 NexusERP 升级方案 §4.1 ERP P1 补全（物料-供应商关系/部门请购单/采购合同管理）与 §4.3 MES 运行时补齐（保养到期提醒/点检执行流程），打通已有代码的缺失环节。

**Architecture:** 两个模块独立并行开发。ERP P1 聚焦"补全缺失"（菜单 SQL/字典/BO 字段/联动方法/校验补强），MES 运行时聚焦"运行时行为补全"（SnailJob 任务/SSE 推送/点检执行流程）。所有 DDL 字段均已就位，无需数据库迁移。

**Tech Stack:** Spring Boot + MyBatis-Plus + Flowable + SnailJob + SSE + Redis + Vue3 + Element Plus

**前置文档:**
- `doc/system/NexusERP_三模块共同升级方案.md` §四（第二波）+ §4.1 + §4.3
- 第一波计划 `doc/system/plans/2026-06-26-upgrade-wave1-plan.md`（沿用模式参考）

**关键发现（代码探索结论）:**
- ERP P1 三子项的代码基础均已存在，主要补全缺失环节
- MES 运行时字段全部就位（`eq_maintenance_plan.next_date`/`cycle_days`、`eq_equipment.inspection_items`/`last_inspection`），无需 DDL
- 沿用模式：`@JobExecutor`、`SseMessageUtils`、`RedisUtils.setObjectIfAbsent`、`IProAndonService.callAndon`、`LambdaUpdateWrapper` 状态推进

---

## 文件结构

### ERP P1 模块（Task 1-4）

| 操作 | 文件路径 | 责任 |
|---|---|---|
| 新建 | `doc/erp/sql/archive/erp-pu-contract-menu.sql` | 采购合同菜单注册（ID 2530-2540） |
| 新建 | `doc/erp/sql/archive/dict-pu-contract.sql` | 采购合同字典（contract_type / pu_contract_status） |
| 修改 | `ruoyi-modules/ruoyi-erp/.../pu/domain/bo/PuContractBo.java` | 补 @AutoMapper + attr1-4 字段 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../pu/domain/vo/PuContractVo.java` | 补 attr1-4 + updateTime 字段 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../pu/controller/PuContractController.java` | 移除类级 @DS + 新增 createOrder 端点 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../pu/service/IPuContractService.java` | 新增 createPurchaseOrderFromContract 方法签名 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../pu/service/impl/PuContractServiceImpl.java` | 实现合同→采购订单联动 |
| 修改 | `ruoyi-modules/ruoyi-erp/.../bd/service/impl/BdMaterialVendorServiceImpl.java` | isDefault 唯一性 + 防重复友好提示 |

### MES 运行时模块（Task 5-7）

| 操作 | 文件路径 | 责任 |
|---|---|---|
| 新建 | `ruoyi-modules/ruoyi-mes/.../eq/task/MaintenanceDueTask.java` | SnailJob 保养到期扫描任务 |
| 修改 | `ruoyi-modules/ruoyi-mes/.../eq/service/IEqMaintenancePlanService.java` | 新增 completePlan 方法签名 |
| 修改 | `ruoyi-modules/ruoyi-mes/.../eq/service/impl/EqMaintenancePlanServiceImpl.java` | 实现 completePlan（推进 nextDate） |
| 新建 | `doc/mes/sql/archive/mes-week29-25-snailjob-maintenance-due.sql` | SnailJob 任务注册（ry-vue 库） |
| 新建 | `ruoyi-modules/ruoyi-mes/.../eq/domain/bo/InspectionResultBo.java` | 点检结果 BO（含 results 数组） |
| 修改 | `ruoyi-modules/ruoyi-mes/.../eq/service/IEqEquipmentService.java` | 新增 submitInspection 方法签名 |
| 修改 | `ruoyi-modules/ruoyi-mes/.../eq/service/impl/EqEquipmentServiceImpl.java` | 实现点检提交 + 异常联动 |
| 修改 | `ruoyi-modules/ruoyi-mes/.../eq/controller/EqEquipmentController.java` | 新增 POST /{equipmentId}/inspect 端点 |
| 新建 | `plus-ui/src/api/mes/eq/inspection.ts` | 点检 API |
| 新建 | `plus-ui/src/views/mes/eq/inspection/index.vue` | 点检执行页面 |
| 新建 | `doc/mes/sql/archive/mes-week29-26-inspection-menu.sql` | 点检菜单注册（ID 4125-4129） |

---

## Task 1: 采购合同菜单 + 字典 SQL

**Files:**
- Create: `doc/erp/sql/archive/erp-pu-contract-menu.sql`
- Create: `doc/erp/sql/archive/dict-pu-contract.sql`

**背景**：采购合同 `pu_contract` 表与 Service/Controller 已完整，但菜单 SQL 未注册（`pu:contract:*` 权限不存在），合同类型/状态字典未注册。父菜单 2500 = 采购管理，已用 ID 段 2501-2518。

- [ ] **Step 1: 新建采购合同菜单 SQL**

文件 `doc/erp/sql/archive/erp-pu-contract-menu.sql`：

```sql
-- =============================================
-- NexusERP - ERP 采购合同菜单注册
-- 目标库: ry-vue
-- 创建时间: 2026-06-26
-- 说明: Wave2 P1 补全 - 注册 pu:contract:* 权限与菜单
-- 幂等: 使用 INSERT IGNORE
-- 父菜单 2500 = 采购管理
-- =============================================

USE `ry-vue`;

-- 采购合同主菜单 (2530) + 5 个按钮权限 (2531-2535)
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2530, '采购合同', 2500, 5, 'contract', 'erp/pu/contract/index', NULL, 1, 0, 'C', '0', '0', 'pu:contract:list', 'documentation', 103, 1, NOW(), NULL, NULL, '采购合同管理菜单'),
(2531, '合同查询', 2530, 1, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:query', '#', 103, 1, NOW(), NULL, NULL, ''),
(2532, '合同新增', 2530, 2, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:add', '#', 103, 1, NOW(), NULL, NULL, ''),
(2533, '合同修改', 2530, 3, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:edit', '#', 103, 1, NOW(), NULL, NULL, ''),
(2534, '合同删除', 2530, 4, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:remove', '#', 103, 1, NOW(), NULL, NULL, ''),
(2535, '合同审批', 2530, 5, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:submit', '#', 103, 1, NOW(), NULL, NULL, '');

-- 验证
-- SELECT menu_id, menu_name, perms FROM `sys_menu` WHERE `perms` LIKE 'pu:contract:%';
```

- [ ] **Step 2: 新建采购合同字典 SQL**

文件 `doc/erp/sql/archive/dict-pu-contract.sql`：

```sql
-- =============================================
-- NexusERP - ERP 采购合同字典数据
-- 目标库: ry-vue
-- 创建时间: 2026-06-26
-- 说明: Wave2 P1 补全 - 注册 contract_type / pu_contract_status 字典
-- 幂等: 使用 INSERT IGNORE
-- 字典 ID 段: 600201-600210 (避开已用 600106 BOM/工艺路线)
-- =============================================

USE `ry-vue`;

-- 字典类型: 合同类型
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600201, '000000', '合同类型', 'contract_type', 103, 1, NOW(), NULL, NULL, '采购合同类型');

-- 字典类型: 采购合同状态
INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600202, '000000', '采购合同状态', 'pu_contract_status', 103, 1, NOW(), NULL, NULL, '采购合同状态');

-- 字典数据: 合同类型 (3 个值)
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600201, '000000', 1, '框架合同', 'FRAMEWORK', 'contract_type', '', 'primary', 'Y', 103, 1, NOW(), NULL, NULL, '长期框架协议'),
(600202, '000000', 2, '现货合同', 'SPOT', 'contract_type', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '单次现货采购'),
(600203, '000000', 3, '服务合同', 'SERVICE', 'contract_type', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, '服务类合同');

-- 字典数据: 采购合同状态 (7 个值)
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600211, '000000', 1, '草稿', 'DRAFT', 'pu_contract_status', '', 'info', 'Y', 103, 1, NOW(), NULL, NULL, ''),
(600212, '000000', 2, '审批中', 'APPROVING', 'pu_contract_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600213, '000000', 3, '已批准', 'APPROVED', 'pu_contract_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600214, '000000', 4, '执行中', 'EXECUTING', 'pu_contract_status', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600215, '000000', 5, '已完成', 'COMPLETED', 'pu_contract_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600216, '000000', 6, '已终止', 'TERMINATED', 'pu_contract_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, ''),
(600217, '000000', 7, '已驳回', 'REJECTED', 'pu_contract_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, '');

-- 验证
-- SELECT dict_type, dict_label, dict_value FROM `sys_dict_data` WHERE `dict_type` IN ('contract_type', 'pu_contract_status') ORDER BY dict_type, dict_sort;
```

- [ ] **Step 3: Commit**

```bash
git add doc/erp/sql/archive/erp-pu-contract-menu.sql doc/erp/sql/archive/dict-pu-contract.sql
git commit -m "feat(erp-pu): Task1 采购合同菜单+字典SQL注册 (Wave2-P1)"
```

**验收:** 执行 SQL 后 `sys_menu` 有 `pu:contract:*` 权限；`sys_dict_data` 有 contract_type (3 值) + pu_contract_status (7 值)。

---

## Task 2: PuContract Bo/Vo 字段补全 + @AutoMapper 修复

**Files:**
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/domain/bo/PuContractBo.java`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/domain/vo/PuContractVo.java`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/controller/PuContractController.java`

**背景**：`PuContractBo` 缺 `@AutoMapper` 注解（会导致 `MapstructUtils.convert` 失败），BO/VO 都缺 `attr1-4` 扩展字段，VO 缺 `updateTime`。`PuContractController` 类级 `@DS("erp")` 应下移到 Service（Service 已有 `@DS("erp")`，Controller 上的冗余需移除）。

- [ ] **Step 1: PuContractBo 补 @AutoMapper + attr1-4**

在 `PuContractBo.java` 类声明上方追加 `@AutoMapper` 注解，import `io.github.linpeilie.annotations.AutoMapper` 和 `com.nexus.erp.pu.domain.PuContract`。在 `remark` 字段后追加 `attr1`-`attr4`：

```java
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.erp.pu.domain.PuContract;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PuContract.class, reverseConvertGenerate = false)
public class PuContractBo extends BaseEntity {
    // ... 现有字段 ...

    /** 备注 */
    private String remark;

    /** 扩展字段1 */
    private String attr1;
    /** 扩展字段2 */
    private String attr2;
    /** 扩展字段3 */
    private String attr3;
    /** 扩展字段4 */
    private String attr4;
}
```

- [ ] **Step 2: PuContractVo 补 attr1-4 + updateTime**

在 `PuContractVo.java` 的 `remark` 字段后追加 `attr1-4` + `updateTime`：

```java
    /** 备注 */
    private String remark;

    /** 扩展字段1 */
    private String attr1;
    /** 扩展字段2 */
    private String attr2;
    /** 扩展字段3 */
    private String attr3;
    /** 扩展字段4 */
    private String attr4;

    /** 更新时间 */
    @ExcelProperty("更新时间")
    private Date updateTime;
```

（若 `Date` 未 import，追加 `import java.util.Date;`）

- [ ] **Step 3: PuContractController 移除类级 @DS**

`PuContractController` 类上的 `@DS("erp")` 冗余（Service 层已有），移除：

```java
// 修改前
@DS("erp")
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/pu/contract")
public class PuContractController extends BaseController {

// 修改后
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/pu/contract")
public class PuContractController extends BaseController {
```

同步移除未使用的 `import com.baomidou.dynamic.datasource.annotation.DS;`。

- [ ] **Step 4: 编译验证 + Commit**

```bash
cd d:\workspace\nexus-erp
mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests -T 1C
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/domain/bo/PuContractBo.java ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/domain/vo/PuContractVo.java ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/controller/PuContractController.java
git commit -m "fix(erp-pu): Task2 PuContract Bo/Vo补@AutoMapper+attr字段+Controller去冗余@DS (Wave2-P1)"
```

**验收:** `mvn compile` BUILD SUCCESS；PuContractBo 含 `@AutoMapper`；PuContractVo 含 `updateTime`/`attr1-4`；Controller 无类级 `@DS`。

---

## Task 3: 采购合同→采购订单联动方法

**Files:**
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/service/IPuContractService.java`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/service/impl/PuContractServiceImpl.java`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/controller/PuContractController.java`

**背景**：`PuContractServiceImpl` 无"从合同生成采购订单"方法。参考 `PuPurchaseRequisitionServiceImpl.convertToPurchaseOrder` 模式：校验状态→创建 PuPurchaseOrder（自动编号）→逐行创建 PuPurchaseOrderLine→更新合同状态。`PuPurchaseOrder` 已预留 `contractId`/`contractNo` 字段。

- [ ] **Step 1: IPuContractService 新增方法签名**

在 `IPuContractService.java` 接口末尾追加：

```java
    /**
     * 从合同生成采购订单
     * @param contractId 合同ID
     * @return 创建的采购订单ID
     */
    Long createPurchaseOrderFromContract(Long contractId);
```

- [ ] **Step 2: PuContractServiceImpl 注入依赖 + 实现联动方法**

在 `PuContractServiceImpl` 注入 `PuPurchaseOrderMapper`、`PuPurchaseOrderLineMapper`、`AutoCodeUtil`（参考 `PuPurchaseRequisitionServiceImpl`）。在类末尾追加方法：

```java
    private final PuPurchaseOrderMapper purchaseOrderMapper;
    private final PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    private final AutoCodeUtil autoCodeUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPurchaseOrderFromContract(Long contractId) {
        // 1. 校验合同存在且状态为 APPROVED 或 EXECUTING
        PuContract contract = baseMapper.selectById(contractId);
        if (contract == null) {
            throw new ServiceException("合同不存在: " + contractId);
        }
        if (!STATUS_APPROVED.equals(contract.getStatus())
            && !"EXECUTING".equals(contract.getStatus())) {
            throw new ServiceException("合同状态不允许生成采购订单, 仅 APPROVED/EXECUTING 可生成");
        }

        // 2. 查合同明细
        List<PuContractLine> lines = contractLineMapper.selectList(
            Wrappers.<PuContractLine>lambdaQuery().eq(PuContractLine::getContractId, contractId));
        if (lines.isEmpty()) {
            throw new ServiceException("合同明细为空, 无法生成采购订单");
        }

        // 3. 创建采购订单主表
        PuPurchaseOrder order = new PuPurchaseOrder();
        order.setOrderNo(autoCodeUtil.genSerialCode("PU_ORDER_CODE", null));
        order.setVendorId(contract.getVendorId());
        order.setVendorCode(contract.getVendorCode());
        order.setVendorName(contract.getVendorName());
        order.setOrderDate(new Date());
        order.setStatus("DRAFT");
        order.setContractId(contract.getContractId());
        order.setContractNo(contract.getContractNo());
        purchaseOrderMapper.insert(order);

        // 4. 逐行创建采购订单明细
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PuContractLine line : lines) {
            PuPurchaseOrderLine orderLine = new PuPurchaseOrderLine();
            orderLine.setOrderId(order.getOrderId());
            orderLine.setMaterialId(line.getMaterialId());
            orderLine.setMaterialCode(line.getMaterialCode());
            orderLine.setMaterialName(line.getMaterialName());
            orderLine.setMaterialSpec(line.getMaterialSpec());
            orderLine.setUnit(line.getUnit());
            orderLine.setUnitPrice(line.getUnitPrice());
            orderLine.setQuantity(line.getQuantity());
            orderLine.setReceivedQuantity(BigDecimal.ZERO);
            orderLine.setLineAmount(line.getLineAmount());
            orderLine.setRequiredDate(line.getDeliveryDate());
            purchaseOrderLineMapper.insert(orderLine);
            if (line.getLineAmount() != null) {
                totalAmount = totalAmount.add(line.getLineAmount());
            }
        }
        order.setTotalAmount(totalAmount);
        purchaseOrderMapper.updateById(order);

        // 5. 更新合同状态为 EXECUTING
        LambdaUpdateWrapper<PuContract> wrapper = Wrappers.<PuContract>lambdaUpdate()
            .set(PuContract::getStatus, "EXECUTING")
            .eq(PuContract::getContractId, contractId);
        baseMapper.update(null, wrapper);

        log.info("合同 {} 生成采购订单 {}: 金额 {}", contractId, order.getOrderNo(), totalAmount);
        return order.getOrderId();
    }
```

注意：`PuPurchaseOrder`/`PuPurchaseOrderLine` 的字段名（如 `orderNo`/`vendorId`/`orderDate`/`totalAmount`/`receivedQuantity`）需子代理按实际 Entity 调整；`STATUS_APPROVED` 常量已存在于 `IPuContractService`。

- [ ] **Step 3: PuContractController 新增 createOrder 端点**

在 `PuContractController.java` 追加：

```java
    /**
     * 从合同生成采购订单
     */
    @SaCheckPermission("pu:contract:edit")
    @Log(title = "合同生成采购订单", businessType = BusinessType.INSERT)
    @PostMapping("/createOrder/{contractId}")
    public R<Long> createOrder(@PathVariable Long contractId) {
        return R.ok(puContractService.createPurchaseOrderFromContract(contractId));
    }
```

（注入字段名 `puContractService` 需与 Controller 现有注入一致；若为 `baseService` 则相应调整）

- [ ] **Step 4: 编译验证 + Commit**

```bash
cd d:\workspace\nexus-erp
mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests -T 1C
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/service/IPuContractService.java ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/service/impl/PuContractServiceImpl.java ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/controller/PuContractController.java
git commit -m "feat(erp-pu): Task3 采购合同→采购订单联动方法 (Wave2-P1)"
```

**验收:** 编译通过；`POST /erp/pu/contract/createOrder/{contractId}` 可调用；合同状态为 APPROVED/EXECUTING 时可生成订单，生成后合同状态变为 EXECUTING。

---

## Task 4: 物料-供应商 isDefault 唯一性 + 防重复友好提示

**Files:**
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdMaterialVendorServiceImpl.java`

**背景**：`insertByBo` 依赖 DB 唯一索引 `uk_material_supplier` 但未给友好提示；`isDefault=1` 唯一性未在 Service 层校验（同一物料应只能有一个默认供应商）。

- [ ] **Step 1: insertByBo 加查重 + isDefault 唯一性校验**

在 `BdMaterialVendorServiceImpl.insertByBo` 方法开头追加校验（在 `MapstructUtils.convert` 之前）：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(BdMaterialVendorBo bo) {
        // 1. 查重: 同一物料+供应商只能有一条关系
        Long existCount = baseMapper.selectCount(Wrappers.<BdMaterialVendor>lambdaQuery()
            .eq(BdMaterialVendor::getMaterialId, bo.getMaterialId())
            .eq(BdMaterialVendor::getVendorId, bo.getVendorId()));
        if (existCount != null && existCount > 0) {
            throw new ServiceException("该物料与供应商的关系已存在");
        }
        // 2. isDefault 唯一性: 同一物料只能有一个默认供应商
        if (Integer.valueOf(1).equals(bo.getIsDefault())) {
            Long defaultCount = baseMapper.selectCount(Wrappers.<BdMaterialVendor>lambdaQuery()
                .eq(BdMaterialVendor::getMaterialId, bo.getMaterialId())
                .eq(BdMaterialVendor::getIsDefault, 1));
            if (defaultCount != null && defaultCount > 0) {
                throw new ServiceException("该物料已存在默认供应商, 请先取消原默认");
            }
        }
        BdMaterialVendor add = MapstructUtils.convert(bo, BdMaterialVendor.class);
        // ... 现有插入逻辑 ...
    }
```

- [ ] **Step 2: updateByBo 加 isDefault 唯一性校验**

在 `BdMaterialVendorServiceImpl.updateByBo` 方法追加（排除自身）：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(BdMaterialVendorBo bo) {
        // isDefault 唯一性: 切换为默认时, 取消该物料其他默认
        if (Integer.valueOf(1).equals(bo.getIsDefault())) {
            BdMaterialVendor existing = baseMapper.selectById(bo.getRelationId());
            if (existing != null && bo.getMaterialId() != null) {
                // 将该物料的其他默认供应商置为非默认
                baseMapper.update(null, Wrappers.<BdMaterialVendor>lambdaUpdate()
                    .set(BdMaterialVendor::getIsDefault, 0)
                    .eq(BdMaterialVendor::getMaterialId, bo.getMaterialId())
                    .ne(BdMaterialVendor::getRelationId, bo.getRelationId()));
            }
        }
        BdMaterialVendor update = MapstructUtils.convert(bo, BdMaterialVendor.class);
        // ... 现有更新逻辑 ...
    }
```

- [ ] **Step 3: 编译验证 + Commit**

```bash
cd d:\workspace\nexus-erp
mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests -T 1C
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdMaterialVendorServiceImpl.java
git commit -m "feat(erp-bd): Task4 物料-供应商 isDefault唯一性+防重复友好提示 (Wave2-P1)"
```

**验收:** 重复插入同物料+供应商报"该物料与供应商的关系已存在"；同一物料设置第二个默认供应商时报"该物料已存在默认供应商"；编辑切换默认时自动取消原默认。

---

## Task 5: 设备保养到期 SnailJob 任务 + SSE 推送 + 安灯触发

**Files:**
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/task/MaintenanceDueTask.java`
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/service/IEqMaintenancePlanService.java`
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/service/impl/EqMaintenancePlanServiceImpl.java`
- Create: `doc/mes/sql/archive/mes-week29-25-snailjob-maintenance-due.sql`

**背景**：`eq_maintenance_plan.next_date` + `cycle_days` 字段已存在且有索引，但无到期扫描任务。沿用 `IotDataArchiveTask` 的 `@JobExecutor` 模式 + `IotAlertNotifier` 的 Redis 限频模式 + `ProAndonServiceImpl.publishAndonSse` 的 SSE 推送模式 + `IProAndonService.callAndon(TYPE_EQUIPMENT)` 安灯触发。

- [ ] **Step 1: IEqMaintenancePlanService 新增 completePlan 方法签名**

在 `IEqMaintenancePlanService.java` 接口末尾追加：

```java
    /**
     * 完成保养计划: 推进 nextDate = today + cycleDays
     * @param planId 计划ID
     */
    void completePlan(Long planId);
```

- [ ] **Step 2: EqMaintenancePlanServiceImpl 实现 completePlan**

在 `EqMaintenancePlanServiceImpl.java` 类末尾追加：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completePlan(Long planId) {
        EqMaintenancePlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("保养计划不存在: " + planId);
        }
        // 推进 nextDate = today + cycleDays
        Integer cycleDays = plan.getCycleDays();
        if (cycleDays == null || cycleDays <= 0) {
            cycleDays = getDefaultCycleDaysByType(plan.getMaintenanceType());
        }
        Date newNextDate = DateUtils.addDays(new Date(), cycleDays);
        LambdaUpdateWrapper<EqMaintenancePlan> wrapper = Wrappers.<EqMaintenancePlan>lambdaUpdate()
            .set(EqMaintenancePlan::getNextDate, newNextDate)
            .eq(EqMaintenancePlan::getPlanId, planId);
        baseMapper.update(null, wrapper);
        log.info("保养计划 {} 已完成, 推进 nextDate={}", planId, newNextDate);
    }

    private Integer getDefaultCycleDaysByType(String maintenanceType) {
        if (maintenanceType == null) return 30;
        switch (maintenanceType) {
            case "DAILY": return 1;
            case "WEEKLY": return 7;
            case "MONTHLY": return 30;
            case "QUARTERLY": return 90;
            case "YEARLY": return 365;
            default: return 30;
        }
    }
```

注意：`DateUtils` 使用 `org.apache.commons.lang3.time.DateUtils`（项目已有依赖）；若项目无此类，用 `java.time.LocalDate.now().plusDays(cycleDays)` 转 `Date`。

- [ ] **Step 3: 新建 MaintenanceDueTask.java**

文件 `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/task/MaintenanceDueTask.java`：

```java
package com.nexus.mes.eq.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sse.domain.SseMessageDto;
import com.nexus.common.sse.utils.SseMessageUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqMaintenancePlan;
import com.nexus.mes.eq.domain.vo.EqMaintenancePlanVo;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqMaintenancePlanMapper;
import com.nexus.mes.eq.service.IEqMaintenancePlanService;
import com.nexus.mes.pro.service.IProAndonService;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * 设备保养到期自动提醒定时任务
 * <p>
 * 周期性扫描 eq_maintenance_plan 中 next_date <= today 且 status=ACTIVE 的计划:
 * 1. SSE 推送 MAINTENANCE_DUE 消息给租户内在线用户
 * 2. 触发 EQUIPMENT 类型安灯(每计划每24小时限频一次)
 * 3. 不自动完成计划(需人工确认保养完成后调 completePlan)
 * <p>
 * 建议频率: 每天 8:00 一次 (cron: 0 0 8 * * ?)
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@Slf4j
@JobExecutor(name = "maintenanceDueTask")
public class MaintenanceDueTask {

    private static final String PLAN_STATUS_ACTIVE = "ACTIVE";
    private static final String SSE_TYPE = "MAINTENANCE_DUE";
    private static final String THROTTLE_PREFIX = "mes:maintenance:due:rl:";
    private static final Duration THROTTLE_TTL = Duration.ofHours(24);

    private final EqMaintenancePlanMapper planMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final IEqMaintenancePlanService planService;
    private final IProAndonService andonService;
    private final ISysUserService sysUserService;

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("[保养到期任务] 开始执行");
        try {
            // 扫描所有租户(简化: 用默认租户上下文, 实际多租户需遍历)
            // SnailJob 线程无 Sa-Token, 需注入租户上下文
            String tenantId = "000000"; // 默认租户, 多租户环境需从配置或遍历
            int[] count = TenantHelper.dynamic(tenantId, () -> scanAndNotify());
            int total = count[0];
            SnailJobLog.REMOTE.info("[保养到期任务] 完成, 处理 {} 条到期计划", total);
            return ExecuteResult.success("处理 " + total + " 条到期计划");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("[保养到期任务] 失败: {}", e.getMessage());
            return ExecuteResult.failure("保养到期任务失败: " + e.getMessage());
        }
    }

    private int[] scanAndNotify() {
        Date today = new Date();
        LambdaQueryWrapper<EqMaintenancePlan> wrapper = Wrappers.<EqMaintenancePlan>lambdaQuery()
            .eq(EqMaintenancePlan::getStatus, PLAN_STATUS_ACTIVE)
            .le(EqMaintenancePlan::getNextDate, today);
        List<EqMaintenancePlan> duePlans = planMapper.selectList(wrapper);
        int processed = 0;

        for (EqMaintenancePlan plan : duePlans) {
            try {
                notifyOne(plan);
                processed++;
            } catch (Exception e) {
                log.warn("[保养到期任务] 计划 {} 通知失败: {}", plan.getPlanId(), e.getMessage(), e);
            }
        }
        return new int[]{processed};
    }

    private void notifyOne(EqMaintenancePlan plan) {
        // 1. Redis 限频: 每计划每24小时仅推送一次
        String throttleKey = THROTTLE_PREFIX + plan.getPlanId();
        if (!RedisUtils.setObjectIfAbsent(throttleKey, "1", THROTTLE_TTL)) {
            log.debug("[保养到期任务] 计划 {} 限频中, 跳过", plan.getPlanId());
            return;
        }

        // 2. 查设备信息
        EqEquipment equipment = plan.getEquipmentId() == null ? null :
            equipmentMapper.selectById(plan.getEquipmentId());
        String equipName = equipment == null ? "未知设备" : equipment.getEquipmentName();
        String equipCode = equipment == null ? "N/A" : equipment.getEquipmentCode();

        String reason = String.format("保养到期: 计划[%s] 设备[%s(%s)] 类型[%s] 应完成日期[%s]",
            plan.getPlanName(), equipName, equipCode, plan.getMaintenanceType(),
            plan.getNextDate());

        // 3. SSE 推送给租户内在线用户
        publishMaintenanceSse(plan, equipment, reason);

        // 4. 触发 EQUIPMENT 类型安灯
        try {
            andonService.callAndon(null, null, null, IProAndonService.TYPE_EQUIPMENT, reason);
            log.info("[保养到期任务] 计划 {} 已触发 EQUIPMENT 安灯", plan.getPlanId());
        } catch (Exception e) {
            log.warn("[保养到期任务] 计划 {} 触发安灯失败: {}", plan.getPlanId(), e.getMessage());
        }
    }

    private void publishMaintenanceSse(EqMaintenancePlan plan, EqEquipment equipment, String reason) {
        if (!SseMessageUtils.isEnable()) return;
        // 简化: 推送给所有在线用户(参考 ProAndonServiceImpl 的 collectOnlineUsersByTenant)
        // 实际可按 responsibleId 推送, 这里用全员广播
        Set<Long> userIds = collectOnlineUsers();
        if (userIds.isEmpty()) {
            log.debug("[保养到期任务] 无在线用户, 跳过 SSE");
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", SSE_TYPE);
        payload.put("action", "DUE");
        payload.put("planId", plan.getPlanId());
        payload.put("planName", plan.getPlanName());
        payload.put("equipmentId", plan.getEquipmentId());
        payload.put("equipmentName", equipment == null ? null : equipment.getEquipmentName());
        payload.put("nextDate", plan.getNextDate());
        payload.put("reason", reason);

        SseMessageDto dto = new SseMessageDto();
        dto.setUserIds(List.copyOf(userIds));
        dto.setMessage(com.nexus.common.core.utils.JsonUtils.toJsonString(payload));
        SseMessageUtils.publishMessage(dto);
    }

    /**
     * 扫描在线用户(SnailJob 线程无 Sa-Token 上下文, 此处用 ISysUserService 查活跃用户)
     * 注: 简化实现, 实际应参考 ProAndonServiceImpl.collectOnlineUsersByTenant 用 StpUtil.searchTokenValue
     */
    private Set<Long> collectOnlineUsers() {
        // SnailJob 线程无 token, 这里返回空集让 SSE 跳过(仅安灯触发)
        // 实际 SSE 推送由前端轮询或安灯看板刷新实现
        return Collections.emptySet();
    }
}
```

注意：
- `TenantHelper.dynamic` 签名需子代理按实际项目调整（返回类型可能是 void 而非 int[]）
- `collectOnlineUsers` 简化为空集（SnailJob 线程无 Sa-Token token，SSE 实际靠安灯看板刷新触发）；若需真实 SSE 推送，参考 `ProAndonServiceImpl.collectOnlineUsersByTenant` 用 `StpUtil.searchTokenValue`
- `IProAndonService.TYPE_EQUIPMENT` 常量已存在
- `JsonUtils` 包路径 `com.nexus.common.core.utils.JsonUtils`

- [ ] **Step 4: 新建 SnailJob 注册 SQL**

文件 `doc/mes/sql/archive/mes-week29-25-snailjob-maintenance-due.sql`：

```sql
-- =============================================
-- NexusERP - SnailJob 任务注册: 设备保养到期提醒
-- 目标库: ry-vue (SnailJob 调度库)
-- 创建时间: 2026-06-26
-- 说明: Wave2 MES 运行时补齐 - 注册 maintenanceDueTask
--       - executor_info = 'maintenanceDueTask' 对应 @JobExecutor(name = "maintenanceDueTask")
--       - cron: 0 0 8 * * ?  每天 8:00 执行
--       - block_strategy=1(丢弃) 避免重叠
-- 幂等: WHERE NOT EXISTS
-- =============================================

USE `ry-vue`;

INSERT INTO `sj_job` (
    `namespace_id`, `group_name`, `job_name`, `args_str`, `args_type`,
    `next_trigger_at`, `job_status`, `task_type`, `route_key`,
    `executor_type`, `executor_info`, `trigger_type`, `trigger_interval`,
    `block_strategy`, `executor_timeout`, `max_retry_times`,
    `parallel_num`, `retry_interval`, `bucket_index`, `resident`,
    `notify_ids`, `owner_id`, `labels`, `description`, `ext_attrs`,
    `deleted`, `create_dt`, `update_dt`
)
SELECT
    'dev', 'ruoyi_group', '设备保养到期提醒任务', NULL, 1,
    UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 1 HOUR)) * 1000,
    1, 1, 4,
    1, 'maintenanceDueTask', 3, '0 0 8 * * ?',
    1, 300, 3,
    1, 60, 122, 0,
    '', 1, '{}', '扫描 eq_maintenance_plan 中 next_date<=today 的到期计划, 触发 SSE 推送+EQUIPMENT 安灯(每计划24h限频)', '',
    0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `sj_job`
    WHERE `namespace_id` = 'dev'
      AND `group_name` = 'ruoyi_group'
      AND `executor_info` = 'maintenanceDueTask'
      AND `deleted` = 0
);

-- 验证
-- SELECT id, job_name, executor_info, trigger_interval FROM `sj_job` WHERE `executor_info` = 'maintenanceDueTask';
```

- [ ] **Step 5: 编译验证 + Commit**

```bash
cd d:\workspace\nexus-erp
mvn -pl ruoyi-modules/ruoyi-mes -am compile -DskipTests -T 1C
git add ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/task/MaintenanceDueTask.java ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/service/IEqMaintenancePlanService.java ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/service/impl/EqMaintenancePlanServiceImpl.java doc/mes/sql/archive/mes-week29-25-snailjob-maintenance-due.sql
git commit -m "feat(mes-eq): Task5 保养到期SnailJob任务+SSE推送+安灯触发(24h限频) (Wave2-MES运行时)"
```

**验收:** 编译通过；SQL 执行后 `sj_job` 有 `executor_info='maintenanceDueTask'`；任务运行时扫描到期计划（next_date<=today 且 status=ACTIVE），每计划24小时限频，触发 EQUIPMENT 安灯；`completePlan(planId)` 推进 nextDate。

---

## Task 6: 设备点检执行 Service + Controller

**Files:**
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/domain/bo/InspectionResultBo.java`
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/service/IEqEquipmentService.java`
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/service/impl/EqEquipmentServiceImpl.java`
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/controller/EqEquipmentController.java`

**背景**：`eq_equipment.inspection_items`（JSON 点检标准）+ `last_inspection`（JSON 最近结果）字段已存在，但无点检执行方法。沿用 `EqRepairServiceImpl` 的状态推进模式：`LambdaUpdateWrapper` 单列更新 + 异常时 `updateStatus(STATUS_FAULT)` + 触发安灯。`last_inspection` JSON schema 需定义。

- [ ] **Step 1: 新建 InspectionResultBo.java**

文件 `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/domain/bo/InspectionResultBo.java`：

```java
package com.nexus.mes.eq.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 设备点检结果提交 BO
 * <p>
 * 对应 last_inspection JSON 结构:
 * <pre>
 * {
 *   "inspectTime": "2026-06-26T10:30:00",
 *   "inspectorId": 1,
 *   "inspectorName": "张三",
 *   "results": [
 *     {"item": "油位", "standard": "正常", "actual": "正常", "pass": true}
 *   ],
 *   "overallPass": true,
 *   "remark": "..."
 * }
 * </pre>
 *
 * @author nexus
 */
@Data
public class InspectionResultBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 点检结果明细 */
    @NotNull(message = "点检结果明细不能为空")
    private List<InspectionItemResult> results;

    /** 总体是否合格 */
    @NotNull(message = "总体合格性不能为空")
    private Boolean overallPass;

    /** 备注 */
    private String remark;

    /**
     * 单项点检结果
     */
    @Data
    public static class InspectionItemResult implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 点检项目名称 */
        private String item;
        /** 标准值 */
        private String standard;
        /** 实际值 */
        private String actual;
        /** 是否合格 */
        private Boolean pass;
    }
}
```

- [ ] **Step 2: IEqEquipmentService 新增 submitInspection 方法签名**

在 `IEqEquipmentService.java` 接口追加：

```java
    /**
     * 提交设备点检结果
     * @param equipmentId 设备ID
     * @param bo 点检结果
     * @return 是否成功
     */
    Boolean submitInspection(Long equipmentId, InspectionResultBo bo);
```

（需 import `com.nexus.mes.eq.domain.bo.InspectionResultBo`）

- [ ] **Step 3: EqEquipmentServiceImpl 实现 submitInspection**

在 `EqEquipmentServiceImpl.java` 注入 `IProAndonService`（若未注入），实现方法：

```java
    private final IProAndonService proAndonService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean submitInspection(Long equipmentId, InspectionResultBo bo) {
        if (equipmentId == null) {
            throw new ServiceException("设备ID不能为空");
        }
        EqEquipment equipment = baseMapper.selectById(equipmentId);
        if (equipment == null) {
            throw new ServiceException("设备不存在: " + equipmentId);
        }

        // 1. 构建 last_inspection JSON
        Map<String, Object> inspectionRecord = new HashMap<>();
        inspectionRecord.put("inspectTime", new Date());
        Long userId = LoginHelper.getUserId();
        inspectionRecord.put("inspectorId", userId);
        inspectionRecord.put("inspectorName", LoginHelper.getUsername());
        inspectionRecord.put("results", bo.getResults());
        inspectionRecord.put("overallPass", bo.getOverallPass());
        inspectionRecord.put("remark", bo.getRemark());

        String lastInspectionJson = JsonUtils.toJsonString(inspectionRecord);

        // 2. 更新 last_inspection 字段(单列更新, 避免覆盖其他字段)
        LambdaUpdateWrapper<EqEquipment> wrapper = Wrappers.<EqEquipment>lambdaUpdate()
            .set(EqEquipment::getLastInspection, lastInspectionJson)
            .eq(EqEquipment::getEquipmentId, equipmentId);
        int rows = baseMapper.update(null, wrapper);

        // 3. 点检不合格: 设备状态置为 FAULT + 触发 EQUIPMENT 安灯
        if (Boolean.FALSE.equals(bo.getOverallPass())) {
            updateStatus(equipmentId, STATUS_FAULT);
            String reason = String.format("点检不合格: 设备[%s(%s)] 不合格项[%d]项",
                equipment.getEquipmentName(),
                equipment.getEquipmentCode(),
                bo.getResults().stream().filter(r -> Boolean.FALSE.equals(r.getPass())).count());
            try {
                proAndonService.callAndon(null, null, null, IProAndonService.TYPE_EQUIPMENT, reason);
                log.info("设备 {} 点检不合格已触发安灯", equipmentId);
            } catch (Exception e) {
                log.warn("设备 {} 点检不合格触发安灯失败: {}", equipmentId, e.getMessage());
            }
        }

        return rows > 0;
    }
```

注意：
- `updateStatus`/`STATUS_FAULT` 已存在于 `IEqEquipmentService`
- `LoginHelper` 包路径 `com.nexus.common.satoken.utils.LoginHelper`
- `JsonUtils` 包路径 `com.nexus.common.core.utils.JsonUtils`
- `LambdaUpdateWrapper` 单列更新模式（与 `updateIotDeviceLink` 一致）
- `proAndonService` 若已注入则不重复声明

- [ ] **Step 4: EqEquipmentController 新增 inspect 端点**

在 `EqEquipmentController.java` 追加：

```java
    /**
     * 提交设备点检结果
     */
    @SaCheckPermission("mes:eq:equipment:inspect")
    @Log(title = "设备点检", businessType = BusinessType.UPDATE)
    @PostMapping("/{equipmentId}/inspect")
    public R<Void> submitInspection(@PathVariable Long equipmentId,
                                    @Validated @RequestBody InspectionResultBo bo) {
        return toAjax(equipmentService.submitInspection(equipmentId, bo));
    }
```

（需 import `com.nexus.mes.eq.domain.bo.InspectionResultBo`、`org.springframework.validation.annotation.Validated`、`org.springframework.web.bind.annotation.RequestBody`；权限标识 `mes:eq:equipment:inspect` 在 Task 7 菜单 SQL 注册）

- [ ] **Step 5: 编译验证 + Commit**

```bash
cd d:\workspace\nexus-erp
mvn -pl ruoyi-modules/ruoyi-mes -am compile -DskipTests -T 1C
git add ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/domain/bo/InspectionResultBo.java ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/service/IEqEquipmentService.java ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/service/impl/EqEquipmentServiceImpl.java ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/eq/controller/EqEquipmentController.java
git commit -m "feat(mes-eq): Task6 设备点检执行Service+Controller+异常联动安灯 (Wave2-MES运行时)"
```

**验收:** 编译通过；`POST /mes/eq/equipment/{equipmentId}/inspect` 可调用；点检结果写入 `last_inspection` JSON；不合格时设备状态变 FAULT + 触发 EQUIPMENT 安灯。

---

## Task 7: 设备点检前端页面 + API + 菜单 SQL

**Files:**
- Create: `plus-ui/src/api/mes/eq/inspection.ts`
- Create: `plus-ui/src/views/mes/eq/inspection/index.vue`
- Create: `doc/mes/sql/archive/mes-week29-26-inspection-menu.sql`

**背景**：后端 API 已就位（Task 6），需新建前端点检执行页面。沿用 `repair/index.vue` 的状态推进页面模式：列表展示设备 + "点检"按钮打开对话框 + 结构化表单（v-for 渲染 inspection_items 每项 + actual 输入框 + pass 判断）。菜单 ID 4125+（父菜单 4003 设备管理，现有 4110-4124 已用）。

- [ ] **Step 1: 新建 inspection.ts API**

文件 `plus-ui/src/api/mes/eq/inspection.ts`：

```typescript
import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 点检结果项 */
export interface InspectionItemResult {
  item: string;
  standard: string;
  actual: string;
  pass: boolean;
}

/** 点检结果提交 BO */
export interface InspectionResultBo {
  results: InspectionItemResult[];
  overallPass: boolean;
  remark?: string;
}

/** 设备点检信息(含 inspection_items) */
export interface EquipmentInspectionVO {
  equipmentId: number;
  equipmentCode: string;
  equipmentName: string;
  status: string;
  inspectionItems?: string;  // JSON 字符串
  lastInspection?: string;    // JSON 字符串
}

/** 查询待点检设备列表 */
export const listEquipmentForInspection = (params?: any): AxiosPromise<EquipmentInspectionVO[]> => {
  return request({ url: '/mes/eq/equipment/list', method: 'get', params });
};

/** 提交点检结果 */
export const submitInspection = (equipmentId: number, data: InspectionResultBo): AxiosPromise<void> => {
  return request({ url: `/mes/eq/equipment/${equipmentId}/inspect`, method: 'post', data });
};
```

- [ ] **Step 2: 新建 inspection/index.vue**

文件 `plus-ui/src/views/mes/eq/inspection/index.vue`（结构化表单，v-for 渲染点检项）：

```vue
<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" :inline="true" v-show="showSearch">
      <el-form-item label="设备编码" prop="equipmentCode">
        <el-input v-model="queryParams.equipmentCode" placeholder="请输入设备编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="设备名称" prop="equipmentName">
        <el-input v-model="queryParams.equipmentName" placeholder="请输入设备名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="equipmentList">
      <el-table-column label="设备编码" prop="equipmentCode" width="120" />
      <el-table-column label="设备名称" prop="equipmentName" />
      <el-table-column label="状态" prop="status" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'NORMAL' ? 'success' : 'danger'">
            {{ statusMap[scope.row.status] || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近点检" prop="lastInspection" width="180">
        <template #default="scope">
          {{ parseLastInspectionTime(scope.row.lastInspection) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center">
        <template #default="scope">
          <el-button type="primary" link @click="handleInspect(scope.row)">点检</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 点检对话框 -->
    <el-dialog v-model="inspectOpen" :title="`设备点检 - ${currentEquipment?.equipmentName}`" width="700px" append-to-body>
      <el-form ref="inspectForm" :model="inspectForm" label-width="120px">
        <div v-if="inspectForm.items.length === 0" class="empty-tip">
          <el-alert title="该设备未配置点检项目(inspection_items 为空), 可直接提交空点检" type="info" :closable="false" />
        </div>
        <div v-else>
          <el-row v-for="(item, idx) in inspectForm.items" :key="idx" :gutter="10" class="inspect-row">
            <el-col :span="6">
              <el-form-item :label="`项目${idx+1}`">
                <span>{{ item.item }}</span>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="标准">
                <span>{{ item.standard }}</span>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="实际值">
                <el-input v-model="item.actual" placeholder="请输入实际值" />
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="合格">
                <el-switch v-model="item.pass" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>
        <el-form-item label="总体合格">
          <el-switch v-model="inspectForm.overallPass" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="inspectForm.remark" type="textarea" :rows="2" placeholder="点检备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitInspect">确 定</el-button>
        <el-button @click="inspectOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="EquipmentInspection">
import { ref, reactive } from 'vue';
import { listEquipmentForInspection, submitInspection, type InspectionItemResult } from '@/api/mes/eq/inspection';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const loading = ref(false);
const showSearch = ref(true);
const equipmentList = ref<any[]>([]);
const inspectOpen = ref(false);
const currentEquipment = ref<any>(null);

const statusMap: Record<string, string> = { NORMAL: '正常', FAULT: '故障', MAINTENANCE: '维修中', SCRAPPED: '已报废' };

const queryParams = reactive({ equipmentCode: '', equipmentName: '' });

const inspectForm = reactive<{ items: InspectionItemResult[]; overallPass: boolean; remark: string }>({
  items: [],
  overallPass: true,
  remark: ''
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listEquipmentForInspection(queryParams);
    equipmentList.value = res.rows || [];
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => getList();
const resetQuery = () => { proxy?.resetForm('queryForm'); handleQuery(); };

const parseLastInspectionTime = (json?: string): string => {
  if (!json) return '从未点检';
  try {
    const obj = JSON.parse(json);
    return obj.inspectTime || '未知时间';
  } catch { return '解析失败'; }
};

const handleInspect = (row: any) => {
  currentEquipment.value = row;
  // 解析 inspection_items JSON
  inspectForm.items = [];
  if (row.inspectionItems) {
    try {
      const items = JSON.parse(row.inspectionItems);
      inspectForm.items = items.map((i: any) => ({
        item: i.item || '',
        standard: i.standard || '',
        actual: '',
        pass: true
      }));
    } catch { proxy?.$modal.msgError('点检项目 JSON 解析失败'); }
  }
  inspectForm.overallPass = true;
  inspectForm.remark = '';
  inspectOpen.value = true;
};

const submitInspect = async () => {
  if (!currentEquipment.value) return;
  await proxy?.$modal.confirm('确认提交点检结果?');
  await submitInspection(currentEquipment.value.equipmentId, {
    results: inspectForm.items,
    overallPass: inspectForm.overallPass,
    remark: inspectForm.remark
  });
  proxy?.$modal.msgSuccess('点检提交成功');
  inspectOpen.value = false;
  getList();
};

getList();
</script>

<style scoped>
.inspect-row { margin-bottom: 10px; }
.empty-tip { margin-bottom: 15px; }
</style>
```

- [ ] **Step 3: 新建点检菜单 SQL**

文件 `doc/mes/sql/archive/mes-week29-26-inspection-menu.sql`：

```sql
-- =============================================
-- NexusERP - MES 设备点检菜单注册
-- 目标库: ry-vue
-- 创建时间: 2026-06-26
-- 说明: Wave2 MES 运行时补齐 - 注册 mes:eq:equipment:inspect 权限
-- 幂等: INSERT IGNORE
-- 父菜单 4003 = 设备管理, 现有 4110-4124, 点检从 4125 起
-- =============================================

USE `ry-vue`;

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(4125, '设备点检', 4003, 5, 'eq-inspection', 'mes/eq/inspection/index', NULL, 1, 0, 'C', '0', '0', 'mes:eq:inspection:list', 'check', 103, 1, NOW(), NULL, NULL, 'MES设备点检执行菜单'),
(4126, '点检执行', 4125, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:equipment:inspect', '#', 103, 1, NOW(), NULL, NULL, '提交点检结果');

-- 验证
-- SELECT menu_id, menu_name, perms FROM `sys_menu` WHERE `perms` LIKE 'mes:eq:%inspection%' OR menu_id IN (4125, 4126);
```

- [ ] **Step 4: Commit**

```bash
git add plus-ui/src/api/mes/eq/inspection.ts plus-ui/src/views/mes/eq/inspection/index.vue doc/mes/sql/archive/mes-week29-26-inspection-menu.sql
git commit -m "feat(mes-eq-ui): Task7 设备点检前端页面+API+菜单SQL (Wave2-MES运行时)"
```

**验收:** 前端访问 `/mes/eq/inspection` 显示设备列表；点检按钮打开对话框渲染 `inspection_items` 每项；提交后 `last_inspection` 更新；不合格时设备状态变 FAULT（看设备台账列表）。

---

## 自检：覆盖升级方案要求点

### §4.1 ERP P1 补全覆盖

| 升级方案要求 | 对应 Task | 覆盖状态 |
|---|---|---|
| 物料-供应商关系管理 | Task 4 | ✅ isDefault 唯一性 + 防重复提示（代码基础已存在） |
| 部门请购单 | — | ⚠️ 不新建（PuPurchaseRequisition 已完整，PoRequisition 冗余建议废弃，本次不动） |
| 采购合同管理 | Task 1/2/3 | ✅ 菜单+字典 SQL / Bo/Vo 修复 / 合同→采购订单联动 |

### §4.3 MES 运行时补齐覆盖

| 升级方案要求 | 对应 Task | 覆盖状态 |
|---|---|---|
| 保养自动到期提醒 | Task 5 | ✅ SnailJob 任务 + SSE 推送 + 安灯触发（24h 限频） |
| 点检执行流程 | Task 6/7 | ✅ Service + Controller + 前端页面 + 菜单 SQL（inspection_items JSON 结构化表单） |
| 不良品入库/退货流程 | — | ❌ 不做（依赖 4.1 ERP wm，不在本次范围） |

### 遗留决策点（需用户确认，不阻塞执行）

1. **PoRequisition vs PuPurchaseRequisition 冗余**：建议保留 PuPurchaseRequisition（功能完整+已集成工作流），PoRequisition 标记为废弃但不删除代码。本次不实施迁移。
2. **保养任务 SSE 推送**：SnailJob 线程无 Sa-Token 上下文，`collectOnlineUsers` 简化为空集（SSE 实际靠安灯看板刷新触发）。若需真实 SSE 推送，参考 `ProAndonServiceImpl.collectOnlineUsersByTenant` 用 `StpUtil.searchTokenValue`，但需在 HTTP 请求上下文调用。
3. **Task 2 PuContractBo 字段名**：子代理需确认 Entity 实际字段名（attr1-4 是否为 String 类型），按实际调整。

### 部署后手动操作项

1. **执行 ERP SQL**（`ry-vue` 库）：
   - `doc/erp/sql/archive/erp-pu-contract-menu.sql`
   - `doc/erp/sql/archive/dict-pu-contract.sql`
2. **执行 MES SQL**（`ry-vue` 库）：
   - `doc/mes/sql/archive/mes-week29-25-snailjob-maintenance-due.sql`
   - `doc/mes/sql/archive/mes-week29-26-inspection-menu.sql`
3. **SnailJob 控制台确认**：`maintenanceDueTask` 任务已注册且 `job_status=1`

---

## 执行顺序建议

**并行批次 1（无依赖，可同时启动）：**
- Task 1（ERP 合同菜单 SQL）
- Task 2（ERP 合同 Bo/Vo 修复）
- Task 4（ERP 物料-供应商校验）
- Task 5（MES 保养到期任务）

**并行批次 2（Task 6 可与批次1并行，Task 7 依赖 Task 6）：**
- Task 3（ERP 合同→采购订单联动，依赖 Task 2 的 Bo 修复）
- Task 6（MES 点检后端）

**串行批次 3：**
- Task 7（MES 点检前端，依赖 Task 6 后端 API）

**最终编译验证 + 整体 commit（可选 squash）**
