# 第二周开发计划 - 部门请购单 + 采购价格对比

## 一、部门请购单 (po_requisition)

### 1.1 表结构分析

**po_requisition 主表**
| 字段 | 类型 | 说明 |
|------|------|------|
| requisition_id | bigint | 主键 |
| requisition_no | varchar(64) | 请购单号（唯一索引） |
| status | varchar(32) | 状态（draft/submitted/approved/rejected/closed） |
| department_name | varchar(100) | 部门 |
| applicant_name | varchar(100) | 申请人 |
| required_date | datetime | 需求日期 |
| remark | varchar(500) | 备注 |
| tenant_id | bigint | 租户ID |

**po_requisition_detail 明细表**
| 字段 | 类型 | 说明 |
|------|------|------|
| detail_id | bigint | 主键 |
| requisition_id | bigint | 请购单ID（外键） |
| material_id | bigint | 物料ID |
| material_code | varchar(64) | 物料编码 |
| material_name | varchar(128) | 物料名称 |
| material_spec | varchar(256) | 物料规格 |
| unit | varchar(32) | 单位 |
| required_quantity | decimal | 需求数量 |
| issued_quantity | decimal | 已发料数量 |
| stock_quantity | decimal | 库存数量 |
| shortage_quantity | decimal | 缺料数量 |
| required_date | datetime | 需求日期 |
| status | varchar(32) | 状态 |
| source_type | varchar(32) | 来源类型 |

### 1.2 代码结构

**后端路径**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/po/`

```
po/
├── domain/
│   ├── PoRequisition.java              # 主表实体
│   ├── PoRequisitionDetail.java        # 明细表实体
│   ├── bo/
│   │   └── PoRequisitionBo.java        # 业务对象
│   └── vo/
│       └── PoRequisitionVo.java        # 视图对象
├── mapper/
│   ├── PoRequisitionMapper.java        # 主表Mapper
│   └── PoRequisitionDetailMapper.java  # 明细Mapper
├── service/
│   ├── IPoRequisitionService.java      # 服务接口
│   └── impl/
│       └── PoRequisitionServiceImpl.java # 服务实现
└── controller/
    └── PoRequisitionController.java    # 控制器
```

**前端路径**: `plus-ui/src/`

```
api/erp/po/
├── requisition/
│   ├── index.ts        # API函数
│   └── types.ts        # 类型定义

views/erp/po/
└── requisition/
    ├── index.vue       # 列表页
    └── form.vue        # 表单页
```

### 1.3 实现要点

1. **Domain 实体**: 继承 TenantEntity，使用 @TableName 映射表名
2. **Bo 对象**: 使用 @AutoMapper 注解，@NotBlank 校验必填字段
3. **Vo 对象**: 使用 @ExcelProperty 注解支持导出
4. **Service**: @DS("erp") 数据源，支持明细行级联操作
5. **Controller**: /erp/po/requisition 路径，权限前缀 po:requisition
6. **前端**: 列表页支持查询筛选，表单页支持明细行增删

### 1.4 菜单ID分配

| ID | 名称 | 类型 | 权限标识 |
|----|------|------|---------|
| 2080 | 部门请购单 | C (菜单) | po:requisition:list |
| 2081 | 查询 | F (按钮) | po:requisition:query |
| 2082 | 新增 | F (按钮) | po:requisition:add |
| 2083 | 修改 | F (按钮) | po:requisition:edit |
| 2084 | 删除 | F (按钮) | po:requisition:remove |
| 2085 | 导出 | F (按钮) | po:requisition:export |

---

## 二、采购价格对比

### 2.1 功能设计

基于 `bd_material_vendor` 表的历史价格数据，提供以下功能：

1. **价格对比查询**: 按物料查询所有供应商报价
2. **价格趋势分析**: 按时间维度统计价格变化
3. **价格排名报表**: 物料价格排名（Top N 供应商）

### 2.2 代码结构

**后端**: 在现有 BdMaterialVendorController 基础上扩展

```
bd/
├── controller/
│   └── BdMaterialVendorReportController.java  # 报表控制器
└── domain/
    └── vo/
        └── PriceComparisonVo.java              # 价格对比视图
```

**前端**:
```
views/erp/pu/
├── price-comparison/
│   └── index.vue       # 价格对比页面
└── price-report/
    └── index.vue       # 价格报表页面
```

### 2.3 实现要点

1. **价格对比接口**: 按物料ID查询所有供应商报价，支持排序
2. **价格趋势接口**: 按月份统计价格变化（需要历史价格表）
3. **报表导出**: 支持 Excel 导出

### 2.4 菜单ID分配

| ID | 名称 | 类型 | 权限标识 |
|----|------|------|---------|
| 2090 | 价格对比 | C (菜单) | pu:priceComparison:list |
| 2091 | 价格报表 | C (菜单) | pu:priceReport:list |

---

## 三、文件清单

### 后端 Java 文件（11个）

| 文件 | 路径 |
|------|------|
| PoRequisition.java | ruoyi-modules/ruoyi-erp/.../po/domain/ |
| PoRequisitionDetail.java | ruoyi-modules/ruoyi-erp/.../po/domain/ |
| PoRequisitionBo.java | ruoyi-modules/ruoyi-erp/.../po/domain/bo/ |
| PoRequisitionVo.java | ruoyi-modules/ruoyi-erp/.../po/domain/vo/ |
| PoRequisitionMapper.java | ruoyi-modules/ruoyi-erp/.../po/mapper/ |
| PoRequisitionDetailMapper.java | ruoyi-modules/ruoyi-erp/.../po/mapper/ |
| IPoRequisitionService.java | ruoyi-modules/ruoyi-erp/.../po/service/ |
| PoRequisitionServiceImpl.java | ruoyi-modules/ruoyi-erp/.../po/service/impl/ |
| PoRequisitionController.java | ruoyi-modules/ruoyi-erp/.../po/controller/ |
| PriceComparisonVo.java | ruoyi-modules/ruoyi-erp/.../bd/domain/vo/ |
| BdMaterialVendorReportController.java | ruoyi-modules/ruoyi-erp/.../bd/controller/ |

### 前端文件（6个）

| 文件 | 路径 |
|------|------|
| types.ts | plus-ui/src/api/erp/po/requisition/ |
| index.ts | plus-ui/src/api/erp/po/requisition/ |
| index.vue | plus-ui/src/views/erp/po/requisition/ |
| form.vue | plus-ui/src/views/erp/po/requisition/ |
| price-comparison/index.vue | plus-ui/src/views/erp/pu/ |
| price-report/index.vue | plus-ui/src/views/erp/pu/ |

### SQL 文件（2个）

| 文件 | 内容 |
|------|------|
| erp-po-requisition-menu.sql | 请购单菜单数据 |
| erp-price-comparison-menu.sql | 价格对比菜单数据 |
