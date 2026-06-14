# NexusERP 第二周开发计划

> **计划周期**: 2026年6月23日 — 6月27日（5个工作日）
> **制定日期**: 2026年6月14日
> **当前分支**: plus
> **每日预估工时**: 4-5h/天（非全天开发），合计 24h

---

## 一、本周开发任务

### Day 1（周一）：部门请购单后端

#### 任务 1.1：创建 po_requisition 后端
- **优先级**: 🔴 高
- **预估工时**: 3h
- **内容**:
  - 创建 `PoRequisition.java` Domain 实体（对应 `po_requisition` 表）
  - 创建 `PoRequisitionDetail.java` Domain 实体（对应 `po_requisition_detail` 表）
  - 创建 `PoRequisitionMapper.java` + `PoRequisitionDetailMapper.java` Mapper 接口
  - 创建 `IPoRequisitionService.java` 接口 + `PoRequisitionServiceImpl.java` 实现
  - 创建 `PoRequisitionController.java` Controller（CRUD + 导出）
  - 创建 `PoRequisitionBo.java` + `PoRequisitionVo.java`
- **验收标准**: Swagger 可调用所有接口，支持明细行增删改查

#### 任务 1.2：SQL 脚本更新
- **优先级**: 🟡 中
- **预估工时**: 1h
- **内容**:
  - 检查 `po_requisition` + `po_requisition_detail` 表结构
  - 创建 `script/sql/erp-po-requisition-menu.sql` 菜单数据
  - 补充权限标识（`po:requisition:list/add/edit/remove/export`）
- **验收标准**: 菜单ID无冲突，权限标识正确

---

### Day 2（周二）：部门请购单前端

#### 任务 2.1：创建 po_requisition 前端 API
- **优先级**: 🔴 高
- **预估工时**: 1h
- **内容**:
  - 创建 `plus-ui/src/api/erp/po/requisition/types.ts`
  - 创建 `plus-ui/src/api/erp/po/requisition/index.ts`
- **验收标准**: API 函数完整，类型定义准确

#### 任务 2.2：创建 po_requisition 列表页
- **优先级**: 🔴 高
- **预估工时**: 2h
- **内容**:
  - 创建 `plus-ui/src/views/erp/po/requisition/index.vue`
  - 查询条件：请购单号、部门、状态、日期范围
  - 表格展示：请购单号、部门、申请人、状态、总金额
  - 操作按钮：新增、修改、删除、导出
- **验收标准**: 列表页可正常查询、分页、删除

#### 任务 2.3：创建 po_requisition 表单页
- **优先级**: 🔴 高
- **预估工时**: 2h
- **内容**:
  - 创建 `plus-ui/src/views/erp/po/requisition/form.vue`
  - 基本信息：请购单号、部门、申请人、请购日期、备注
  - 明细行：物料选择、数量、单价、金额、用途说明
  - 审批流集成（提交审批、查看流程）
- **验收标准**: 请购单可正常创建、编辑、提交审批

---

### Day 3（周三）：采购价格对比后端

#### 任务 3.1：价格对比查询接口
- **优先级**: 🔴 高
- **预估工时**: 3h
- **内容**:
  - 在 `BdMaterialVendorController.java` 中增加价格对比接口
  - 接口1：按物料ID查询所有供应商报价（含历史价格）
  - 接口2：按供应商ID查询所有物料报价
  - 接口3：价格趋势分析（按时间维度统计价格变化）
  - 创建 `PriceComparisonVo.java` 视图对象
- **验收标准**: Swagger 可调用，返回数据结构清晰

#### 任务 3.2：价格统计报表接口
- **优先级**: 🟡 中
- **预估工时**: 2h
- **内容**:
  - 创建 `BdMaterialVendorReportController.java`
  - 接口1：物料价格排名（Top N 供应商）
  - 接口2：供应商价格波动分析
  - 接口3：采购成本节省分析（对比最低价）
- **验收标准**: 报表数据准确，支持时间范围筛选

---

### Day 4（周四）：采购价格对比前端

#### 任务 4.1：价格对比页面
- **优先级**: 🔴 高
- **预估工时**: 3h
- **内容**:
  - 创建 `plus-ui/src/views/erp/pu/price-comparison/index.vue`
  - 物料选择器 + 供应商列表对比
  - 表格展示：供应商、单价、币种、供货周期、最小订货量
  - 图表展示：价格柱状图、趋势折线图
  - 筛选条件：物料分类、供应商分类、时间范围
- **验收标准**: 价格对比清晰直观，图表正常显示

#### 任务 4.2：价格报表页面
- **优先级**: 🟡 中
- **预估工时**: 2h
- **内容**:
  - 创建 `plus-ui/src/views/erp/pu/price-report/index.vue`
  - 物料价格排名报表
  - 供应商价格波动报表
  - 采购成本节省报表
  - 支持导出 Excel
- **验收标准**: 报表数据准确，导出功能正常

---

### Day 5（周五）：集成测试 + 文档更新

#### 任务 5.1：功能联调测试
- **优先级**: 🔴 高
- **预估工时**: 2h
- **内容**:
  - 测试部门请购单完整流程（创建→审批→生成采购订单）
  - 测试采购价格对比功能
  - 测试物料-供应商关系与采购订单联动
  - 修复发现的 Bug
- **验收标准**: 核心流程正常运行

#### 任务 5.2：菜单注册 + SQL 更新
- **优先级**: 🟡 中
- **预估工时**: 1h
- **内容**:
  - 补充采购价格对比菜单数据
  - 补充价格报表菜单数据
  - 检查所有菜单ID无冲突
- **验收标准**: 新租户初始化后所有菜单自动创建

#### 任务 5.3：更新 ERP 文档
- **优先级**: 🟢 低
- **预估工时**: 1h
- **内容**:
  - 更新 `NexusERP_ERP文档.md`：
    - 将"部门请购单"标记为已完成
    - 将"采购价格对比"标记为已完成
    - 更新功能清单和完成度
  - 更新本文档（NexusERP_Week2_Plan.md）的完成状态
- **验收标准**: 文档与实际功能一致

---

## 二、任务总览

| 日 | 主题 | 任务 | 预估工时 |
|:--:|------|------|:-------:|
| 周一 | 部门请购单后端 | Domain/Service/Controller + SQL | 4h |
| 周二 | 部门请购单前端 | API + 列表页 + 表单页 | 5h |
| 周三 | 采购价格对比后端 | 价格查询接口 + 报表接口 | 5h |
| 周四 | 采购价格对比前端 | 价格对比页面 + 报表页面 | 5h |
| 周五 | 集成测试+收尾 | 联调测试 + 菜单 + 文档 | 4h |
| **合计** | | **13 个任务** | **23h** |

---

## 三、新增文件清单

### 后端（Java）
```
# 部门请购单
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/po/domain/PoRequisition.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/po/domain/PoRequisitionDetail.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/po/domain/bo/PoRequisitionBo.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/po/domain/vo/PoRequisitionVo.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/po/mapper/PoRequisitionMapper.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/po/mapper/PoRequisitionDetailMapper.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/po/service/IPoRequisitionService.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/po/service/impl/PoRequisitionServiceImpl.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/po/controller/PoRequisitionController.java

# 采购价格对比
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/domain/vo/PriceComparisonVo.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/controller/BdMaterialVendorReportController.java
```

### 前端（Vue/TS）
```
# 部门请购单
plus-ui/src/api/erp/po/requisition/types.ts
plus-ui/src/api/erp/po/requisition/index.ts
plus-ui/src/views/erp/po/requisition/index.vue
plus-ui/src/views/erp/po/requisition/form.vue

# 采购价格对比
plus-ui/src/views/erp/pu/price-comparison/index.vue
plus-ui/src/views/erp/pu/price-report/index.vue
```

### SQL 更新
```
script/sql/erp-po-requisition-menu.sql  — 请购单菜单数据
script/sql/erp-price-comparison-menu.sql  — 价格对比菜单数据
```

---

## 四、后续规划（第三周及以后）

| 优先级 | 功能 | 说明 |
|:------:|------|------|
| P1 | 财务基础 | 应收/应付/总账（需详细设计） |
| P2 | 采购审批流完善 | 请购单→采购订单的完整审批链路 |
| P2 | 销售订单→发货→开票联动 | 完整销售流程串联 |
| P3 | Dashboard 首页数据接入 | 系统整体完成后统一制作 |
| P3 | 报表中心 | 统一的报表查询和导出功能 |

---

## 五、风险提示

1. **po_requisition 表结构**：需确认现有表字段是否满足业务需求，可能需要补充字段
2. **价格对比数据量**：历史价格数据较多时，需考虑分页和缓存优化
3. **审批流集成**：请购单审批流需要与现有 Flowable 配置保持一致
