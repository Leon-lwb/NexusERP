# NexusERP MES 客户定制化发布包方案（方案D）

> **文档版本**: v1.0
> **制定日期**: 2026-08-22
> **状态**: 已实现（后端 + 前端 + SQL + 单测）
> **依据**: 用户提供的两份方案文档（`release-package-design.md` 发布包方案 + `firmware-flashing-solution-c.md` 方案C）+ 既有刷写模块代码审查
> **前序文档**: `doc/mes/plans/2026-08-17-mes-flash-abstraction-plan.md`（刷写能力抽象）

---

## 〇、方案定位与决策

以**发布包方案为骨架**，融合**方案C的"工单固件确认 + 下达门禁"**，并修正两份原始文档中与现有代码不符或存在缺陷的部分：

| 决策点 | 结论 | 理由 |
|--------|------|------|
| 销售订单行固件子表（方案C `so_sales_order_line_firmware`） | ✂️ 不做 | 发布包是技术配置唯一来源，避免双来源冲突；一次性指定走 MES 工单固件编辑（用户确认） |
| 客户-物料-固件预设表（方案C `mes_firmware_customer_binding`） | ✂️ 不做 | 唯一键 `(client_id, material_id)` 无法表达一机多固件（BIOS+SSD+网卡），发布包固件映射天然支持 |
| BOM选配定位方式 | 🔧 改进 | 按 `child_material_id`（原物料）定位而非 `bom_detail_id`——抗 BOM/MBOM 版本变更，语义为"把某物料换成某物料" |
| 配置项消费落地 | 🔧 补全 | 原文档未定义非 FLASH 配置去向：全量快照到 `pro_work_order.release_config` JSON + FLASH 组按工序注入 `stepFlashConfig` |
| FLASH 下达门禁挂载点 | 🔧 修正 | 挂 `release()` 直达路径与 `submit()` 审批提交前**双入口**——`afterFlowHandle` 审批通过直接置 RELEASED 不经过 `doReleaseAndSnapshot`，原始方案会漏掉审批路径 |
| ERP 侧 client 落点 | 🔧 改进 | `generateWorkOrders` 生成工单时一次性落到 `prd_work_order`（单一落点），而非 MES 消费时 4 跳回查（erpWoId→plan→line→header） |
| 前端路由 | 🔧 修正 | 本仓库菜单为 sys_menu 数据库驱动，原文档写的"改 router/index.ts"不适用，只需菜单 SQL |
| 执行层 | ✅ 零改动 | `checkFlashStepGate`/Provider 校验链/刷写记录完全复用，`contentRefId` 级门禁天然支持覆写后的固件 |

**核心链路**：

```
发布包(RELEASED) ←──工程维护+Flowable审批
     ↑ 匹配(客户+物料+客户产品代号 → 默认包)
SO行(客户产品代号) → 生产计划 → ERP工单(client落库) → MQ消息(client透传)
     → MES工单创建(发布包快照: 固件覆写/BOM选配/配置快照)
     → 生产负责人确认/更改工序固件(CREATED)
     → 下达(FLASH门禁双入口) → 冻结 → 流转卡执行(既有门禁)
```

---

## 一、数据模型

### 1.1 新增表（ry-mes 库）

| 表 | 说明 |
|----|------|
| `mes_release_package` | 发布包主表：client+material+customer_product_code+version，状态机 DRAFT/IN_REVIEW/RELEASED/ARCHIVED，`is_default` 客户+物料内唯一，生效/过期日期控制匹配窗口 |
| `mes_release_pkg_firmware` | 固件映射：`routing_step_id`(精确定位)/`process_id`(宽泛定位) → `firmware_id`(仅RELEASED)；PCBA物料字段为追溯性冗余 |
| `mes_release_pkg_bom_item` | BOM选配：action_type REPLACE/ADD/REMOVE；`child_material_id` 原物料定位键(抗BOM版本变更)，`bom_detail_id` 可选精确定位 |
| `mes_release_pkg_config` | 配置项：config_group FLASH/TEST_PARAM/LABEL/PACKAGING/PROCESS，键值对，`process_id` 空=全局 |

同客户+物料+版本唯一由 Service 校验（同固件先例），不建 DB 唯一键以避开逻辑删后重建冲突；编码唯一 `uk_tenant_package_code(tenant_id, package_code)`。

### 1.2 修改表

| 表 | 变更 |
|----|------|
| `so_sales_order_line` (ry-erp) | + `customer_product_code` / `customer_product_name`（匹配键，随订单行删重建自动流转） |
| `prd_work_order` (ry-erp) | + `client_id` / `client_name` / `customer_product_code`（生成工单时从 SO 头/行落库，单一落点） |
| `pro_work_order` (ry-mes) | + `release_package_id/code/ver`（快照可追溯）+ `release_config` TEXT（配置项快照JSON） |
| `pro_work_order_step` (ry-mes) | + `firmware_source`（PACKAGE/ROUTING/MATERIAL_DEFAULT/MANUAL，前端来源标记） |

### 1.3 MQ 消息

`WorkOrderReleaseMessage`（ruoyi-common-mq）+ `clientId` / `clientName` / `customerProductCode`。

---

## 二、核心逻辑

### 2.1 匹配规则（`ReleasePackageServiceImpl.matchPackage`）

```
① 客户+物料+客户产品代号 精确匹配 → 最新 RELEASED（有效期内）
② 客户+物料 is_default=1 → RELEASED（有效期内）
③ 无匹配 → null（按标准工艺路线生成，完全兼容存量行为）
有效期 = (effective_date 空或≤now) AND (expiry_date 空或>now)
```

### 2.2 固件覆写优先级（`generateStepsFromRouting`）

```
FLASH 工序: ① 发布包映射(routingStepId精确→processId宽泛, fail-loudly) 
           → ② 工艺路线步骤固件(bd_routing_step.firmware_id) 
           → ③ 物料默认 RELEASED 固件
非FLASH工序: 保持原行为(工艺路线显式固件仍生效)
```

**fail-loudly**：映射固件非 RELEASED 或物料不匹配时**抛错拒绝创建工单**，不静默回退默认固件——定制工单刷错固件的代价远大于停线排查。配套守卫：`FirmwareServiceImpl.archive` 拒绝归档被 RELEASED 发布包映射的固件。

**processId 宽泛匹配不要求映射行 routingStepId 为空**：工艺路线升版后 stepId 变化，按工序匹配仍能命中（定制意图挂在工序维度）。

### 2.3 FLASH 配置注入与门禁一致性

`stepFlashConfig` 多类型JSON 注入时**固件项同时写入数组首位**——门禁解析 `stepFlashConfig` 优先于单类型 `contentRefId`，只写配置项会导致固件刷写漏检。`flashTypeCode=firmware_file` 的配置项跳过（固件绑定走固件映射表，避免双来源）。

### 2.4 BOM 选配（`snapshotBomLines`）

在 MBOM 查询结果的**深拷贝副本**上执行 REPLACE/ADD/REMOVE（浅拷贝会泄漏修改 MBOM 行对象——单测发现并修复），覆写行备注追加"发布包选配"标记供前端展示；MBOM 主数据不受影响。

### 2.5 FLASH 下达门禁（双入口）

```
release() 直达路径:  doReleaseAndSnapshot: 幂等快照 → FLASH门禁 → CAS冻结
submit() 审批路径:   启动流程前预校验 FLASH门禁
（afterFlowHandle 审批通过直接置 RELEASED 不再重复校验, 由提交时点校验兜底）
```

门禁标准：每个 FLASH 工序必须有 `contentRefId` 或非空 `stepFlashConfig`，未绑定则拦截并提示具体工序名。

### 2.6 工单固件编辑（`updateStepFirmware`）

生产负责人在 CREATED 工单上更改 FLASH 工序固件：校验链（工单CREATED → 步骤属于该工单且FLASH → 固件RELEASED → 物料匹配），更新单类型绑定 + `firmware_source=MANUAL`，多类型JSON中的固件项同步改写（门禁一致性）。

---

## 三、代码清单

### 后端新增（19 文件）

- `com.nexus.mes.release`：4 实体 + 4 Bo + 4 Vo + 4 Mapper + `IReleasePackageService` + `ReleasePackageServiceImpl`（Flowable 审批完全镜像 `FirmwareServiceImpl` 模式：先启动流程后持久化 selectFlowId 防死锁、`@DS("mes")+REQUIRES_NEW` 回调）+ `ReleasePackageController`（`/mes/release/package`）
- `UpdateStepFirmwareBo` + `IProWorkOrderService.updateStepFirmware`

### 后端修改

| 文件 | 改动 |
|------|------|
| `WorkOrderReleaseMessage` (common-mq) | +3 客户字段 |
| `SoSalesOrderLine` 实体/Bo/Vo | +客户产品代号/名称 |
| `PrdWorkOrder` 实体/Bo/Vo | +client 三字段 |
| `PrdProductionPlanServiceImpl.generateWorkOrders` | 经 plan.orderLineId 查 SO 行/头落客户信息（直调 Mapper 避免 Service 循环依赖） |
| `PrdWorkOrderServiceImpl.sendWorkOrderReleaseMessage` | 透传客户字段 |
| `ProWorkOrder`/`ProWorkOrderVo`/`ProWorkOrderStep`/`ProWorkOrderStepVo`/`ProWorkOrderBo` | +发布包/固件来源字段 |
| `ProWorkOrderServiceImpl` | 匹配/覆写/选配/配置快照/门禁/固件编辑（详见第二章） |
| `ProWorkOrderController` | +`PUT /{mesWoId}/step/{stepId}/firmware` |
| `FirmwareServiceImpl.archive` | +发布包映射守卫 |

### SQL

- `script/sql/update/2026-08-22-mes-release-package.sql`：4新表 + 4表加列 + 字典(600910-913类型/6010xx数据) + 菜单(4310-4320) + 编码规则 MES_RP_CODE(294) + wf_custom_form 注册 `mesReleasePackageService`
- 基线同步：`ry-mes.sql`（新表+加列+种子行NULL对齐）、`ry-erp.sql`（加列+种子行）、`ry-vue.sql`（字典/菜单/编码/审批）

### 前端

- 新增：`api/mes/release/package.ts`、`views/mes/release/package/{index,form}.vue`（三子表动态行编辑）、`workorder/components/FirmwareSelectDialog.vue`
- 修改：工单 `detail.vue`（发布包信息/定制配置标签/固件来源标记/更改按钮/未绑定红色警示/BOM选配标记）、工单 `form.vue`（发布包选择器）、销售订单 `orderheader/form.vue`（行级客户产品代号/名称）

### 单测（20 个，全部通过）

- `ReleasePackageServiceImplTest`（11）：匹配三规则+空参、版本唯一、固件非RELEASED/物料不一致/定位键重复、BOM选配缺原物料、setDefault 清旧置新+状态守卫
- `ProWorkOrderServiceImplTest`（9）：发布包覆写固件（含 stepId 变化后 processId 兜底）、归档固件 fail-loudly、BOM 选配 REPLACE/ADD/REMOVE（含 MBOM 主数据不可变断言）、FLASH 门禁拦截/放行、固件编辑状态守卫/非RELEASED守卫/多类型JSON改写
- 测试基建：`SpringStaticBootstrap`（MapstructUtils/JsonUtils 静态初始化依赖 `SpringUtil.getBean`——静态桩必须注册在**声明类** hutool `SpringUtil` 上而非项目 `SpringUtils`）+ `TableInfoHelper.initTableInfo`（lambdaUpdate.set 立即解析列缓存）

---

## 四、测试要点（手工全链路）

| 场景 | 预期 |
|------|------|
| 发布包 DRAFT→提交审批(Flowable)→通过→RELEASED | 状态机流转+发布人/时间 |
| SO 行录入客户产品代号 → 生产计划 → ERP工单 → MES工单 | client/code 全链路透传 |
| 客户+物料+代号精确包存在 | MES 工单按该包生成（覆盖默认包） |
| 无匹配发布包 | 按标准工艺路线生成（存量行为回归） |
| 发布包映射 BIOS+SSD 双固件 | 各自命中对应 FLASH 工序，firmware_source=PACKAGE |
| 映射固件被归档 | 工单创建报错不静默回退（归档守卫应先拦截归档动作） |
| BOM REPLACE/ADD/REMOVE | 工单快照生效+备注"发布包选配"标记，MBOM 主数据不变 |
| FLASH 配置项(config_file/mac) | 注入 stepFlashConfig，与固件项共存 |
| FLASH 工序未绑定 → release 与 submit | 双入口拦截，提示工序名 |
| CREATED 工单更改固件 → 下达后再改 | 成功 / 拒绝（仅CREATED） |
| 发布包归档 | 不再匹配新工单；已生成工单快照不受影响 |

---

## 五、注意事项

1. `afterFlowHandle` 是 Flowable(master 事务)回调，发布包实现镜像固件的 `@DS("mes")+REQUIRES_NEW` 模式，否则写入落到 ry-vue 报表不存在。
2. ERP `generateWorkOrders` 查 SO 行/头是同库(ry-erp)操作，直调 Mapper，避免 Service 循环依赖。
3. 匹配在 MQ 消费线程执行，租户上下文由消费者恢复（BaseMessage 携带 tenantId）。
4. 雪花 ID 序列化已由全局 BigNumberSerializer 处理。
5. 执行增量脚本前需在 ry-erp / ry-mes / ry-vue 三个库分别执行对应段落；ALTER 加列不幂等，重复执行需先核对列不存在。
