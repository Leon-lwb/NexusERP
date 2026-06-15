# 第三周开发计划 - BOM循环检测 + 条码优化 + 遗留修复

## 一、BOM循环引用检测加固

### 1.1 现状分析

**BdBomServiceImpl** 中：
- `verifyBom()` + `checkCircularRef()` — **已有循环检测**，使用 `HashSet<Long>` 记录已访问物料ID
- `buildTreeNode()` — **无循环保护**，可能无限递归导致栈溢出
- `buildIndentedLines()` — **无循环保护**，可能无限递归导致栈溢出

### 1.2 实现方案

#### buildTreeNode 添加循环保护

```java
private static final int MAX_BOM_DEPTH = 20;

private BomTreeNodeVo buildTreeNode(Long materialId, Set<Long> visited, int depth) {
    // 深度保护
    if (depth > MAX_BOM_DEPTH) {
        throw new ServiceException("BOM层级超过最大深度限制(" + MAX_BOM_DEPTH + ")，请检查是否存在异常数据");
    }
    // 循环引用检测
    if (visited.contains(materialId)) {
        throw new ServiceException("BOM循环引用检测: 物料ID " + materialId + " 存在循环依赖");
    }
    visited.add(materialId);

    BomTreeNodeVo node = new BomTreeNodeVo();
    node.setMaterialId(materialId);
    // ... 现有构建逻辑 ...

    for (BdBomDetail detail : details) {
        // 传递 visited 副本给子节点（兄弟节点间不应共享访问记录）
        BomTreeNodeVo child = buildTreeNode(detail.getComponentId(), new HashSet<>(visited), depth + 1);
        node.getChildren().add(child);
    }
    return node;
}
```

#### buildIndentedLines 添加循环保护

```java
private void buildIndentedLines(Long materialId, int level, BigDecimal parentQty,
                                 List<IndentedBomLineVo> result, Set<Long> visited, int depth) {
    if (depth > MAX_BOM_DEPTH) {
        throw new ServiceException("BOM层级超过最大深度限制(" + MAX_BOM_DEPTH + ")，请检查是否存在异常数据");
    }
    if (visited.contains(materialId)) {
        throw new ServiceException("BOM循环引用检测: 物料ID " + materialId + " 存在循环依赖");
    }
    visited.add(materialId);

    // ... 现有构建逻辑 ...

    for (BdBomDetail detail : details) {
        buildIndentedLines(detail.getComponentId(), level + 1,
            detail.getQuantity(), result, new HashSet<>(visited), depth + 1);
    }
}
```

#### 入口方法修改

```java
// getBomTree 入口
public BomTreeNodeVo getBomTree(Long materialId) {
    return buildTreeNode(materialId, new HashSet<>(), 0);
}

// getIndentedBom 入口
public List<IndentedBomLineVo> getIndentedBom(Long materialId) {
    List<IndentedBomLineVo> result = new ArrayList<>();
    buildIndentedLines(materialId, 0, BigDecimal.ONE, result, new HashSet<>(), 0);
    return result;
}
```

### 1.3 测试场景

| 用例 | 场景 | 预期结果 |
|------|------|---------|
| TC-01 | 正常BOM（A→B→C） | 正常展开 |
| TC-02 | 直接循环（A→B→A） | 抛出 ServiceException |
| TC-03 | 间接循环（A→B→C→A） | 抛出 ServiceException |
| TC-04 | 超深BOM（21层） | 抛出 ServiceException |
| TC-05 | 多子节点含循环（A→B, A→C→B→A） | 抛出 ServiceException |

---

## 二、条码生成器并发安全优化

### 2.1 现状分析

**DatabaseSequenceProvider** 当前实现：
```java
@Transactional
public long nextSequence(String configCode) {
    WmBarcodeConfig config = wmBarcodeConfigMapper.selectOne(
        Wrappers.lambdaQuery<WmBarcodeConfig>()
            .eq(WmBarcodeConfig::getConfigCode, configCode)
    );
    config.setCurrentSequence(config.getCurrentSequence() + 1);
    wmBarcodeConfigMapper.updateById(config);
    return config.getCurrentSequence();
}
```

**问题**：`selectOne + updateById` 无行锁，两个线程可能同时读到相同的 `currentSequence`，导致序列号重复。

### 2.2 实现方案

**WmBarcodeConfigMapper.java** 新增方法：
```java
@Select("SELECT * FROM wm_barcode_config WHERE config_code = #{configCode} FOR UPDATE")
WmBarcodeConfig selectForUpdate(@Param("configCode") String configCode);
```

**DatabaseSequenceProvider.java** 修改：
```java
@Transactional
public long nextSequence(String configCode) {
    // 使用悲观锁获取配置行
    WmBarcodeConfig config = wmBarcodeConfigMapper.selectForUpdate(configCode);
    if (config == null) {
        throw new ServiceException("条码配置不存在: " + configCode);
    }
    config.setCurrentSequence(config.getCurrentSequence() + 1);
    wmBarcodeConfigMapper.updateById(config);
    return config.getCurrentSequence();
}
```

### 2.3 验收要点

1. 单线程：序列号正常递增
2. 并发场景：多线程同时获取序列号，结果不重复
3. 异常场景：configCode 不存在时抛出 ServiceException

---

## 三、价格对比前端 API 补全 + 图表增强

### 3.1 当前问题

`price-comparison/index.vue` 直接使用 `request` 调用后端接口，不符合项目 API 模块化规范。

### 3.2 修复方案

创建 `plus-ui/src/api/erp/pu/price-comparison/types.ts`：
```typescript
export interface PriceComparisonVO {
  vendorId: number;
  vendorCode: string;
  vendorName: string;
  unitPrice: number;
  currency: string;
  leadTime: number;
  minOrderQty: number;
  isDefault: boolean;
  priority: number;
  status: string;
}

export interface PriceComparisonQuery {
  materialId: number;
  vendorCategory?: string;
  timeRange?: string[];
}
```

创建 `plus-ui/src/api/erp/pu/price-comparison/index.ts`：
```typescript
import request from '@/utils/request';
import { PriceComparisonVO, PriceComparisonQuery } from './types';

// 查询物料供应商价格对比
export function listPriceComparison(query: PriceComparisonQuery) {
  return request({
    url: '/erp/bd/materialVendor/report/priceComparison',
    method: 'get',
    params: query
  });
}

// 查询价格趋势
export function getPriceTrend(materialId: number, months?: number) {
  return request({
    url: '/erp/bd/materialVendor/report/priceTrend',
    method: 'get',
    params: { materialId, months }
  });
}

// 查询价格排名
export function getPriceRanking(materialId: number) {
  return request({
    url: '/erp/bd/materialVendor/report/priceRanking',
    method: 'get',
    params: { materialId }
  });
}
```

修改 `price-comparison/index.vue`：将 `request` 调用替换为 API 模块函数。

### 3.3 图表增强

参照 `vendor-evaluation/report.vue` 的 ECharts 模式，在价格对比页面增加：

1. **价格柱状图**：X轴=供应商名称，Y轴=单价，最低价高亮
2. **价格趋势折线图**：X轴=时间（月），Y轴=单价，多供应商对比线

---

## 四、销售合同/发票客户选择弹窗

### 4.1 当前问题

`so/contract/index.vue` 和 `so/invoice/index.vue` 的 `openClientDialog()` 仅显示提示信息。

### 4.2 修复方案

创建通用客户选择弹窗 `plus-ui/src/views/erp/so/components/ClientSelectDialog.vue`：
- 搜索条件：客户编码、客户名称
- 数据表格：客户编码、客户名称、联系人、联系电话
- 单选模式，选择后回填到表单
- 通过 `v-model` + `@confirm` 事件与父组件交互

---

## 五、采购订单合同关联字段（预留）

### 5.1 说明

采购合同模块暂不开发，但在采购订单上预留合同关联字段，为未来扩展做准备。

### 5.2 改动

**数据库**：
```sql
ALTER TABLE pu_purchase_order ADD COLUMN contract_id bigint(20) NULL COMMENT '合同ID';
ALTER TABLE pu_purchase_order ADD COLUMN contract_no varchar(50) NULL COMMENT '合同编号';
```

**后端**：
- `PuPurchaseOrder.java` 新增 `contractId`、`contractNo` 字段
- `PuPurchaseOrderBo.java` 新增对应字段
- `PuPurchaseOrderVo.java` 新增对应字段

**前端**：
- 采购订单表单页增加合同编号只读显示字段

---

## 六、文件清单

### 后端 Java 文件 — 修改（5个）

| 文件 | 修改内容 |
|------|---------|
| BdBomServiceImpl.java | buildTreeNode/buildIndentedLines 添加 visited + depth 保护 |
| DatabaseSequenceProvider.java | 改用 SELECT FOR UPDATE |
| WmBarcodeConfigMapper.java | 新增 selectForUpdate 方法 |
| PuPurchaseOrder.java | 新增 contractId/contractNo 字段 |
| PuPurchaseOrderBo.java | 新增 contractId/contractNo 字段 |
| PuPurchaseOrderVo.java | 新增 contractId/contractNo 字段 |

### 前端文件 — 新增（3个）

| 文件 | 路径 |
|------|------|
| types.ts | plus-ui/src/api/erp/pu/price-comparison/ |
| index.ts | plus-ui/src/api/erp/pu/price-comparison/ |
| ClientSelectDialog.vue | plus-ui/src/views/erp/so/components/ |

### 前端文件 — 修改（4个）

| 文件 | 修改内容 |
|------|---------|
| price-comparison/index.vue | 替换 request 为 API 模块 + 增加图表 |
| so/contract/index.vue | 实现 openClientDialog |
| so/invoice/index.vue | 实现 openClientDialog |
| pu/order/form.vue | 增加合同编号只读字段 |
