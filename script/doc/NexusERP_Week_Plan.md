# NexusERP 下一周开发计划（修订版 v4）

> **计划周期**: 2026年6月16日 — 6月20日（5个工作日）
> **制定日期**: 2026年6月14日
> **当前分支**: plus
> **每日预估工时**: 4-5h/天（非全天开发），合计 24h

---

## 一、功能缺失分析

### 1.1 文档标记"已完成"但代码审查确认已完成的功能

| 功能 | 文档状态 | 代码实际状态 |
|------|---------|------------|
| pu/return 采购退货 | "前端待开发" | ✅ 已有 form.vue(399行) + index.vue(193行) |
| pu/vendor-evaluation 供应商评估 | "前端待开发" | ✅ 已有 form.vue + index.vue + report.vue(678行) |
| wm 批次/SN/追溯 | P2 待开发 | ✅ 已有完整 Controller + Service + 前端 |
| wm 盘点差异审批流 | 问题清单中 | ✅ 已有 Flowable 集成 |
| prd 倒冲领料 | 问题清单中 | ✅ PrdBackflushServiceImpl 已实现 |

### 1.2 真正缺失的主要功能

经逐文件审查（76个Service接口、72个Service实现、62个Controller、54+前端页面、80张数据库表），发现以下 **主要功能缺失**：

#### 🔴 缺失 1：`bd_material_vendor` 物料-供应商关系管理
- **SQL**: `bd_material_vendor` 表已存在（含供货周期、最小订货量、单价、币种、优先级等字段）
- **代码**: ❌ 无 Domain 实体、❌ 无 Mapper、❌ 无 Service、❌ 无 Controller、❌ 无前端页面
- **影响**: 采购模块无法查询"哪个供应商供应某物料"，无法做价格对比，无法自动推荐供应商
- **关联**: PU 采购订单选供应商时缺少物料维度筛选

#### 🔴 缺失 2：SO 合同管理
- **SQL**: ❌ 无合同相关表
- **代码**: ❌ 无 Domain、❌ 无 Service、❌ 无 Controller、❌ 无前端页面
- **影响**: 销售订单缺少合同关联，无法管理合同生命周期
- **文档**: ERP文档明确标记 "so前端补全 — 合同/开票前端页面" 为 P1 待开发

#### 🔴 缺失 3：SO 开票管理
- **SQL**: ❌ 无发票相关表
- **代码**: ❌ 无 Domain、❌ 无 Service、❌ 无 Controller、❌ 无前端页面
- **影响**: 销售流程不完整（报价→订单→发货→**开票**→收款）
- **文档**: ERP文档明确标记 "开票缺失" 为问题项 PB-020

#### 🟡 缺失 4：`po_requisition` 部门请购单
- **SQL**: `po_requisition` + `po_requisition_detail` 表已存在
- **代码**: ❌ 无 Domain、❌ 无 Mapper、❌ 无 Service、❌ 无 Controller、❌ 无前端页面
- **影响**: 部门级请购→采购申请的流程断裂（当前仅有 `pu_purchase_requisition` 采购申请）
- **说明**: AI知识库文档标注流程为 "请购(po_requisition) → 采购订单(pu_purchase_order)"，说明这是设计中的独立模块

#### 🟡 缺失 5：MES 集成 MQ 消息
- **代码**: `PrdWorkOrderServiceImpl.afterFlowHandle()` 审批通过后仅更新状态，不发 MQ 消息
- **影响**: MES 模块无法消费工单审批通过事件
- **文档**: ERP文档明确标记 "MES集成MQ" 为 P1 待开发

---

## 二、本周开发任务

### Day 1（周一）：物料-供应商关系管理 ✅

#### 任务 1.1：创建 bd_material_vendor 后端 ✅
- **优先级**: 🔴 高
- **预估工时**: 3h
- **内容**:
  - 创建 `BdMaterialVendor.java` Domain 实体（对应 `bd_material_vendor` 表）
  - 创建 `BdMaterialVendorMapper.java` Mapper 接口
  - 创建 `IBdMaterialVendorService.java` 接口 + `BdMaterialVendorServiceImpl.java` 实现
  - 创建 `BdMaterialVendorController.java` Controller（CRUD + 导出）
  - 创建 `BdMaterialVendorBo.java` + `BdMaterialVendorVo.java`
- **验收标准**: Swagger 可调用所有接口，支持按物料ID/供应商ID筛选，导出Excel正常

#### 任务 1.2：创建物料-供应商关系前端 ✅
- **优先级**: 🔴 高
- **预估工时**: 2h
- **内容**:
  - 创建 `plus-ui/src/api/erp/bd/material-vendor/index.ts` + `types.ts`
  - 创建 `plus-ui/src/views/erp/bd/material-vendor/index.vue` — 物料-供应商关系列表页
  - 创建 `plus-ui/src/views/erp/bd/material-vendor/form.vue` — 新增/编辑表单
  - 物料选择器 + 供应商选择器联动
- **验收标准**: 页面可正常增删改查，物料-供应商关联正确

---

### Day 2（周二）：物料-供应商集成 + 采购订单增强 ✅

#### 任务 2.1：物料页面集成供应商 Tab ✅
- **优先级**: 🔴 高
- **预估工时**: 2h
- **内容**:
  - 在 `plus-ui/src/views/erp/bd/material/index.vue` 中增加"供应商" Tab
  - 展示该物料关联的供应商列表（从 `bd_material_vendor` 查询）
  - 支持在物料详情中直接管理供应商关系
- **验收标准**: 物料详情页可查看和管理关联供应商

#### 任务 2.2：采购订单选供应商时集成物料关系 ✅
- **优先级**: 🟡 中
- **预估工时**: 2h
- **内容**:
  - 在 `plus-ui/src/views/erp/pu/order/form.vue` 的供应商选择弹窗中
  - 增加"仅显示该物料供应商"筛选选项
  - 自动填充供应商报价（从 `bd_material_vendor.price`）
- **验收标准**: 采购订单可按物料筛选供应商，自动带出价格

#### 任务 2.3：SQL 脚本更新 ✅
- **优先级**: 🟡 中
- **预估工时**: 1h
- **内容**:
  - 在 `script/sql/erp-material-vendor-menu.sql` 中补充 `bd_material_vendor` 表的菜单数据 INSERT
  - 补充权限标识（`bd:materialVendor:list/add/edit/remove/export`）
- **验收标准**: 新租户初始化后菜单和权限自动创建

---

### Day 3（周三）：SO 合同管理 ✅

#### 任务 3.1：SO 合同数据库设计 ✅
- **优先级**: 🔴 高
- **预估工时**: 1h
- **内容**:
  - 设计 `so_contract` 合同主表（合同编号、客户ID、合同名称、合同类型、签订日期、生效日期、失效日期、合同金额、状态等）
  - 设计 `so_contract_line` 合同明细行（物料、数量、单价、交货日期等）
  - 在 `so_sales_order_header` 表中新增 `contract_id` 字段（关联合同）
  - 将 DDL 添加到 `script/sql/erp-so-contract-invoice.sql`
- **验收标准**: DDL 可正常执行，字段命名符合项目规范（snake_case）

#### 任务 3.2：SO 合同后端开发 ✅
- **优先级**: 🔴 高
- **预估工时**: 3h
- **内容**:
  - 创建 `SoContract.java` + `SoContractLine.java` Domain 实体
  - 创建 Mapper + Service + Controller（CRUD + 审批流集成）
  - 合同状态流转：草稿→审批中→已生效→已到期→已终止
  - 合同与销售订单关联（需在 `so_sales_order_header` 表和 Domain 中新增 `contract_id` 字段）
- **验收标准**: Swagger 可调用，状态流转正确

#### 任务 3.3：SO 合同前端列表页 ✅
- **优先级**: 🔴 高
- **预估工时**: 1h
- **内容**:
  - 创建 `plus-ui/src/api/erp/so/contract/index.ts` + `types.ts`
  - 创建 `plus-ui/src/views/erp/so/contract/index.vue` — 合同列表页（查询条件 + 表格 + 操作按钮）
- **验收标准**: 合同列表页可正常查询、分页、删除

---

### Day 4（周四）：SO 合同表单 + 开票管理 ✅

#### 任务 4.1：SO 合同表单页 ✅
- **优先级**: 🔴 高
- **预估工时**: 2h
- **内容**:
  - 创建 `plus-ui/src/views/erp/so/contract/form.vue` — 合同表单页
  - 合同基本信息 + 明细行（物料、数量、单价）
  - 审批流集成（提交审批、查看流程）
  - 客户选择器联动
- **验收标准**: 合同可正常创建、编辑、提交审批，明细行可增删，审批流可正常发起

#### 任务 4.2：SO 开票数据库设计 + 后端 ✅
- **优先级**: 🔴 高
- **预估工时**: 3h
- **内容**:
  - 设计 `so_invoice` 发票主表（发票号、客户ID、开票日期、发票类型、金额、税额、状态等）
  - 设计 `so_invoice_line` 发票明细行（物料、数量、单价、金额、税率等）
  - 创建 Domain + Mapper + Service + Controller
  - 发票与销售订单/合同关联
  - DDL 添加到 `script/sql/erp-so-contract-invoice.sql`
- **验收标准**: Swagger 可调用，发票状态流转正确（草稿→已开→已核销），金额自动计算

---

### Day 5（周五）：开票前端 + 菜单注册 + 文档 ✅

#### 任务 5.1：SO 开票前端开发 ✅
- **优先级**: 🔴 高
- **预估工时**: 2h
- **内容**:
  - 创建 `plus-ui/src/views/erp/so/invoice/index.vue` — 发票列表页
  - 创建 `plus-ui/src/views/erp/so/invoice/form.vue` — 发票表单页
  - 创建 `plus-ui/src/api/erp/so/invoice/index.ts` + `types.ts`
  - 从销售订单/合同下推开票
- **验收标准**: 发票可正常创建、查询、分页，明细行自动汇总金额

#### 任务 5.2：菜单注册 + SQL 更新 ✅
- **优先级**: 🟡 中
- **预估工时**: 1h
- **内容**:
  - 在 `script/sql/erp-material-vendor-menu.sql` 中补充物料-供应商关系管理菜单
  - 在 `script/sql/erp-so-contract-invoice.sql` 中补充 SO 合同管理菜单和 SO 开票管理菜单
  - 配置权限标识
- **验收标准**: 新租户初始化后所有菜单自动创建

#### 任务 5.3：更新 ERP 文档 ✅
- **优先级**: 🟢 低
- **预估工时**: 1h
- **内容**:
  - 更新 `NexusERP_ERP文档.md`：
    - 将"物料-供应商关系"标记为已完成
    - 将"SO 合同管理"标记为已完成
    - 将"SO 开票管理"标记为已完成
    - 更新功能清单和完成度
  - 更新本文档（NexusERP_Week_Plan.md）的完成状态

---

## 三、任务总览

| 日 | 主题 | 任务 | 预估工时 |
|:--:|------|------|:-------:|
| 周一 | 物料-供应商关系 | 后端 API + 前端页面 | 5h |
| 周二 | 物料-供应商集成 | 物料页Tab + 采购订单联动 + SQL | 5h |
| 周三 | SO 合同管理 | 数据库设计 + 后端 + 前端列表 | 5h |
| 周四 | SO 合同+开票 | 合同表单 + 开票后端 | 5h |
| 周五 | 开票前端+收尾 | 开票前端 + 菜单 + 文档 | 4h |
| **合计** | | **16 个任务** | **24h** |

---

## 四、新增文件清单

### 后端（Java）
基础路径: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/`

```
# 物料-供应商关系
bd/domain/BdMaterialVendor.java
bd/domain/bo/BdMaterialVendorBo.java
bd/domain/vo/BdMaterialVendorVo.java
bd/mapper/BdMaterialVendorMapper.java
bd/service/IBdMaterialVendorService.java
bd/service/impl/BdMaterialVendorServiceImpl.java
bd/controller/BdMaterialVendorController.java

# SO 合同
so/domain/SoContract.java
so/domain/SoContractLine.java
so/domain/bo/SoContractBo.java
so/domain/vo/SoContractVo.java
so/mapper/SoContractMapper.java
so/mapper/SoContractLineMapper.java
so/service/ISoContractService.java
so/service/impl/SoContractServiceImpl.java
so/controller/SoContractController.java

# SO 开票
so/domain/SoInvoice.java
so/domain/SoInvoiceLine.java
so/domain/bo/SoInvoiceBo.java
so/domain/vo/SoInvoiceVo.java
so/mapper/SoInvoiceMapper.java
so/mapper/SoInvoiceLineMapper.java
so/service/ISoInvoiceService.java
so/service/impl/SoInvoiceServiceImpl.java
so/controller/SoInvoiceController.java
```

### 前端（Vue/TS）
```
# 物料-供应商关系
plus-ui/src/api/erp/bd/material-vendor/index.ts
plus-ui/src/api/erp/bd/material-vendor/types.ts
plus-ui/src/views/erp/bd/material-vendor/index.vue
plus-ui/src/views/erp/bd/material-vendor/form.vue

# SO 合同
plus-ui/src/api/erp/so/contract/index.ts
plus-ui/src/api/erp/so/contract/types.ts
plus-ui/src/views/erp/so/contract/index.vue
plus-ui/src/views/erp/so/contract/form.vue

# SO 开票
plus-ui/src/api/erp/so/invoice/index.ts
plus-ui/src/api/erp/so/invoice/types.ts
plus-ui/src/views/erp/so/invoice/index.vue
plus-ui/src/views/erp/so/invoice/form.vue
```

### SQL 更新
```
script/sql/ry-erp.sql  — 新增 DDL + 菜单数据
```

---

## 五、后续规划（不在本周范围）

| 优先级 | 功能 | 说明 |
|:------:|------|------|
| P1 | po_requisition 部门请购单 | SQL 表已存在，需实现完整 CRUD |
| P1 | MES 集成 MQ | 工单审批通过后发 MQ 消息 |
| P1 | 采购价格对比 | 基于 bd_material_vendor 的历史价格对比 |
| P2 | 质量管理 (IQC/IPQC/OQC) | 质量检验模块 |
| P2 | 设备管理 | 设备台账/保养/维修 |
| P3 | Dashboard 首页数据接入 | 系统整体完成后统一制作 |
| P3 | 财务基础 | 应收/应付/总账 |
