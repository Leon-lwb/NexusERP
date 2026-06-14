# 物料-供应商关系管理 (bd_material_vendor) 开发计划

## 目标
实现 `bd_material_vendor` 模块的完整 CRUD，包括后端 API + 前端页面 + SQL 菜单数据。

## 代码规范
- Javadoc 格式注释（类、方法、字段均需注释）
- 遵循现有 BdVendor 模块的代码风格
- Domain 继承 `TenantEntity`，Bo 继承 `BaseEntity`，Vo 实现 `Serializable`
- Service 使用 `@DS("erp")` 数据源注解
- Controller 使用 `@SaCheckPermission` 权限注解

## 后端文件清单
基础路径: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/`

| 文件 | 说明 |
|------|------|
| `domain/BdMaterialVendor.java` | Domain 实体 |
| `domain/bo/BdMaterialVendorBo.java` | 业务请求对象 |
| `domain/vo/BdMaterialVendorVo.java` | 视图响应对象 |
| `mapper/BdMaterialVendorMapper.java` | Mapper 接口 |
| `service/IBdMaterialVendorService.java` | Service 接口 |
| `service/impl/BdMaterialVendorServiceImpl.java` | Service 实现 |
| `controller/BdMaterialVendorController.java` | REST Controller |

## 前端文件清单
基础路径: `plus-ui/src/`

| 文件 | 说明 |
|------|------|
| `api/erp/bd/material-vendor/types.ts` | TypeScript 类型定义 |
| `api/erp/bd/material-vendor/index.ts` | API 请求函数 |
| `views/erp/bd/material-vendor/index.vue` | 列表页（含表单弹窗） |

## SQL 菜单数据
- 单独生成: `script/sql/erp-material-vendor-menu.sql`
- 菜单 ID 范围: 2050-2056 (已确认无冲突)
- 父菜单: 2001 (基础数据)

### 菜单 ID 分配
| ID | 名称 | 类型 | 权限标识 |
|----|------|------|---------|
| 2050 | 物料-供应商关系 | C (菜单) | bd:materialVendor:list |
| 2051 | 查询 | F (按钮) | bd:materialVendor:query |
| 2052 | 新增 | F (按钮) | bd:materialVendor:add |
| 2053 | 修改 | F (按钮) | bd:materialVendor:edit |
| 2054 | 删除 | F (按钮) | bd:materialVendor:remove |
| 2055 | 导出 | F (按钮) | bd:materialVendor:export |
