# NexusERP 第三周开发计划

> **计划周期**: 2026年6月30日 — 7月4日（5个工作日）
> **制定日期**: 2026年6月15日
> **当前分支**: plus
> **每日预估工时**: 4-5h/天（非全天开发），合计 17h

---

## 一、本周开发任务

### Day 1（周一）：BOM循环引用检测加固

#### 任务 1.1：BOM展开方法添加循环引用保护
- **优先级**: 🔴 高（RISK-02）
- **预估工时**: 2h
- **内容**:
  - 修改 `BdBomServiceImpl.buildTreeNode()` 方法，添加 `visited` 集合参数
  - 修改 `BdBomServiceImpl.buildIndentedLines()` 方法，添加 `visited` 集合参数
  - 递归进入子节点前检查是否已访问，若已访问则抛出 `ServiceException` 并提示循环引用路径
  - 添加最大递归深度保护（20层）
  - 参照现有 `checkCircularRef()` 的 `HashSet<Long>` 模式
- **验收标准**: 构造循环BOM数据后，调用 getBomTree/getIndentedBom 抛出异常而非栈溢出

#### 任务 1.2：BOM循环引用测试验证
- **优先级**: 🟡 中
- **预估工时**: 1h
- **内容**:
  - 测试场景1：正常BOM树展开（无循环）
  - 测试场景2：直接循环（A→B→A）
  - 测试场景3：间接循环（A→B→C→A）
  - 测试场景4：超深BOM（21层）
  - 测试场景5：多子节点含循环
- **验收标准**: 所有场景结果符合预期

---

### Day 2（周二）：条码生成器并发安全优化

#### 任务 2.1：DatabaseSequenceProvider 并发安全优化
- **优先级**: 🔴 高（RISK-06）
- **预估工时**: 2h
- **内容**:
  - 修改 `DatabaseSequenceProvider.nextSequence()` 方法
  - 将 `selectOne + updateById` 改为 `SELECT ... FOR UPDATE` 悲观锁
  - 新增 Mapper 方法 `selectForUpdate(String configCode)`
  - 在 `wm_barcode_config` 表查询时加行锁，确保并发安全
- **验收标准**: 并发场景下序列号不重复

#### 任务 2.2：条码并发安全测试验证
- **优先级**: 🟡 中
- **预估工时**: 1h
- **内容**:
  - 单线程序列号递增验证
  - 多线程并发获取序列号，验证结果不重复
  - configCode 不存在时异常处理验证
- **验收标准**: 并发场景序列号唯一

---

### Day 3（周三）：价格对比API补全 + 图表增强

#### 任务 3.1：价格对比前端 API 补全
- **优先级**: 🟡 中（Week 2 遗留）
- **预估工时**: 1h
- **内容**:
  - 创建 `plus-ui/src/api/erp/pu/price-comparison/types.ts`
  - 创建 `plus-ui/src/api/erp/pu/price-comparison/index.ts`
  - 封装价格对比相关 API（查询物料供应商报价、价格趋势、价格排名）
  - 修改 `price-comparison/index.vue`，将直接 `request` 调用替换为 API 模块调用
- **验收标准**: 价格对比页面通过 API 模块调用后端接口

#### 任务 3.2：价格对比页面增加图表
- **优先级**: 🟡 中
- **预估工时**: 2h
- **内容**:
  - 在 `price-comparison/index.vue` 中集成 ECharts
  - 新增价格柱状图（各供应商单价对比）
  - 新增价格趋势折线图（按时间维度，依赖后端趋势接口）
  - 参照 `vendor-evaluation/report.vue` 的 ECharts 使用模式
- **验收标准**: 图表正常显示，数据与表格一致

---

### Day 4（周四）：客户选择弹窗 + 采购订单合同关联

#### 任务 4.1：销售合同/发票客户选择弹窗
- **优先级**: 🟡 中（Week 1 遗留）
- **预估工时**: 2.5h
- **内容**:
  - 创建通用客户选择弹窗组件 `plus-ui/src/views/erp/so/components/ClientSelectDialog.vue`
  - 弹窗支持客户搜索（编码/名称）、数据表格展示、单选模式
  - 实现 `so/contract/index.vue` 的 `openClientDialog()` 方法
  - 实现 `so/invoice/index.vue` 的 `openClientDialog()` 方法
  - 选择后回填客户信息到表单
- **验收标准**: 合同/发票页面可通过弹窗选择客户

#### 任务 4.2：采购订单增加合同关联字段（预留）
- **优先级**: 🟢 低
- **预估工时**: 1.5h
- **内容**:
  - `pu_purchase_order` 表新增 `contract_id`、`contract_no` 字段（预留，未来关联合同）
  - `PuPurchaseOrder.java` 实体新增对应字段
  - `PuPurchaseOrderBo.java` / `PuPurchaseOrderVo.java` 新增对应字段
  - 前端采购订单表单页增加合同编号显示（只读，暂无可选合同）
- **验收标准**: 字段可正常读写，为未来合同模块预留接口

---

### Day 5（周五）：集成测试 + 文档更新

#### 任务 5.1：功能联调测试
- **优先级**: 🔴 高
- **预估工时**: 2h
- **内容**:
  - 测试 BOM 循环引用检测（正常/循环/超深场景）
  - 测试条码并发安全性
  - 测试价格对比 API + 图表
  - 测试客户选择弹窗
  - 修复发现的 Bug
- **验收标准**: 核心功能正常运行

#### 任务 5.2：更新 ERP 文档
- **优先级**: 🟢 低
- **预估工时**: 1h
- **内容**:
  - 更新 `NexusERP_ERP文档.md`：
    - 将 RISK-02 BOM循环引用标记为已完成
    - 将 RISK-06 条码生成器标记为已完成
    - 更新模块完成度矩阵（bd 90%→95%）
    - 更新路线图状态
  - 更新本文档（NexusERP_Week3_Plan.md）的完成状态
- **验收标准**: 文档与实际功能一致

---

## 二、任务总览

| 日 | 主题 | 任务 | 预估工时 |
|:--:|------|------|:-------:|
| 周一 | BOM循环引用检测 | 循环保护 + 测试验证 | 3h |
| 周二 | 条码并发安全优化 | SELECT FOR UPDATE + 测试验证 | 3h |
| 周三 | 价格对比增强 | API补全 + ECharts图表 | 3h |
| 周四 | 客户弹窗+合同预留 | 客户选择弹窗 + 采购订单合同字段 | 4h |
| 周五 | 集成测试+收尾 | 联调测试 + 文档更新 | 3h |
| **合计** | | **9 个任务** | **16h** |

---

## 三、新增/修改文件清单

### 后端（Java）— 修改

```
# BOM循环引用
ruoyi-modules/ruoyi-erp/.../bd/service/impl/BdBomServiceImpl.java      # buildTreeNode/buildIndentedLines 添加visited+depth

# 条码并发安全
ruoyi-modules/ruoyi-erp/.../wm/barcode/DatabaseSequenceProvider.java   # 改用 SELECT FOR UPDATE
ruoyi-modules/ruoyi-erp/.../wm/mapper/WmBarcodeConfigMapper.java       # 新增 selectForUpdate 方法

# 采购订单合同关联预留
ruoyi-modules/ruoyi-erp/.../pu/domain/PuPurchaseOrder.java             # 新增 contractId/contractNo 字段
ruoyi-modules/ruoyi-erp/.../pu/domain/bo/PuPurchaseOrderBo.java        # 新增 contractId/contractNo 字段
ruoyi-modules/ruoyi-erp/.../pu/domain/vo/PuPurchaseOrderVo.java        # 新增 contractId/contractNo 字段
```

### 前端（Vue/TS）— 新增

```
# 价格对比API（Week 2遗留）
plus-ui/src/api/erp/pu/price-comparison/types.ts
plus-ui/src/api/erp/pu/price-comparison/index.ts

# 客户选择弹窗
plus-ui/src/views/erp/so/components/ClientSelectDialog.vue
```

### 前端（Vue/TS）— 修改

```
# 价格对比增强
plus-ui/src/views/erp/pu/price-comparison/index.vue    # 替换request为API模块+增加图表

# 客户选择弹窗
plus-ui/src/views/erp/so/contract/index.vue             # 实现 openClientDialog
plus-ui/src/views/erp/so/invoice/index.vue              # 实现 openClientDialog

# 采购订单合同关联预留
plus-ui/src/views/erp/pu/order/form.vue                 # 增加合同编号只读字段
```

### SQL 更新

```
# 采购订单合同关联字段
ALTER TABLE pu_purchase_order ADD COLUMN contract_id bigint(20) NULL COMMENT '合同ID';
ALTER TABLE pu_purchase_order ADD COLUMN contract_no varchar(50) NULL COMMENT '合同编号';
```

---

## 四、与路线图对应关系

| 路线图计划 | 本周覆盖 | 状态 |
|-----------|---------|------|
| Phase A: RISK-02 BOM循环引用 | ✅ Day 1 | 本周完成 |
| Phase A: RISK-06 条码生成器 | ✅ Day 2 | 本周完成 |
| Week 2遗留: 价格对比API | ✅ Day 3 | 本周补全 |
| Week 1遗留: 客户选择弹窗 | ✅ Day 4 | 本周补全 |
| 采购合同预留字段 | ✅ Day 4 | 本周预留 |

---

## 五、风险提示

1. **BOM循环引用检测性能**：多级BOM递归检测可能影响性能，建议限制最大递归深度（如20层）
2. **条码并发测试**：并发安全优化需通过压测验证，建议使用 JMeter 或类似工具
3. **采购合同预留字段**：仅预留 contractId/contractNo 字段，合同模块本身待后续开发
