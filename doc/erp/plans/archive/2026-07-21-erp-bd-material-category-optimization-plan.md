# 物料分类（bd_material_category）优化方案

> 创建日期: 2026-07-21
> 模块: ruoyi-erp / bd（基础数据）
> 前端页面: `plus-ui/src/views/erp/bd/category/index.vue`
> 范围: 清理+正确性 / UX 打磨 / 分类属性模板 / 拖拽移动+停用级联 / 表结构与存量数据评估

---

## 一、背景与现状

### 1.1 现状
- 本地库 `ry-erp`：16 个分类 / 3 级 / 全部启用 / 无软删。
- 物料分布：半成品(12)、原材料(6)、紧固件(5)、产成品(5)、金属材料(1)、润滑材料(1)；其余分类下无物料。
- 后端 `BdMaterialCategory` 设计扎实：物化路径 `path_ids`/`path_names`/`level_num` 服务端权威计算、循环引用检测（`find_in_set` on `path_ids`）、移动/重命名级联刷新子孙路径（精确 substring 替换，非 SQL `REPLACE()`）、Redis 缓存 `BD_CATEGORY_TREE`（30d、租户隔离、写时 evict）、删除阻断校验（有子分类/有物料则禁删）、`@DS("erp")` 多数据源、`tenant_id varchar(20)`（已修复）。

### 1.2 发现的问题

| # | 类别 | 问题 |
|---|------|------|
| 1 | Bug·多租户 | `uk_category_code(category_code)` 全局唯一，未含 `tenant_id` → 不同租户无法共用相同分类编码（租户 A 占 `RAW`，租户 B 被拦截）。当前仅 `000000` 单租户未暴露。索引亦未含 `del_flag`，软删后同编码不可重建。 |
| 2 | 死代码 | 后端 `/erp/bd/category/treeselect` 接口 + `selectCategoryTreeList()` + `buildCategoryTreeSelect()` 全链路无前端调用（前端用 `listCategory`+`handleTree` 自建树；全仓 `treeselect` 调用仅 `system/menu`）。 |
| 3 | 前端重复 | 分类管理页 inline 重复了 `useCategoryTree` composable 已封装的建树逻辑——该 composable 已被物料页等 6+ 页面复用，唯独管理页自身没用。 |
| 4 | UX 缺口 | 树表不显示各分类物料数；启停需开编辑表单；删除被阻断时只给笼统提示（已有 `allChildrenIds` 接口能给出具体阻塞项却未接）；按编码/名称搜索丢失父级上下文（只返匹配孤节点）；`pathNames` 未展示。 |
| 5 | 数据 | 电气材料(id12) 与 木材(id14) `sort_order` 均为 6，重复。 |
| 6 | 功能缺口 | 无"分类属性模板"（真实 ERP 常需：钢材分类挂"材质/规格/产地"属性、物料继承填写）。`attr1-4` 是全 `bd_` 表通用预留字段，非此用途，不足以承载。 |

### 1.3 约束与既定事实（影响设计）
- `useCrud`/`AutoGenCodeField` 是新扁平页标准，但 `useCrud` 面向分页平表（依赖 `res.rows`/`total`、多选批量），**不适合树表**——同为树表的 storagelocation 亦未迁移。故分类页不强行套 `useCrud`，而是复用 `useCategoryTree`。
- 全仓**无任何拖拽改父级**实现（menu/dept/bom/workshop 均无 `draggable`+`node-drop`），Track 4 拖拽需从零设计。
- 全仓**无 `*_attribute`/`*_spec` 模板表**；最接近的范式：MES `dc_collection_point`（模板定义：`data_type`+`unit`，值 `NUMERIC/TEXT/ENUM`）、IoT `iot_device_data` / MES `dc_data_record`（值存储：分类型列 `numeric_value`/`text_value`/`bool_value`）。本方案属性表照此范式。
- 无属性数据类型字典，需新增 `bd_attr_data_type`。
- `BdMaterial` 无 JSON/扩展列承载属性值，需新表 `bd_material_attribute_value`。
- 唯一索引项目惯例不一致：新表（process/routing/rework/subcontract/invoice）含 `tenant_id`，老表（material/category/vendor/workshop）不含；**均不含 `del_flag`**。本方案向新表惯例对齐。

---

## 二、Track 1 — 清理 + 正确性（低风险，独立可交付）

### 2.1 多租户唯一索引修复
- **DDL**（`bd_material_category`）：
  ```sql
  ALTER TABLE bd_material_category DROP INDEX uk_category_code;
  ALTER TABLE bd_material_category ADD UNIQUE INDEX uk_category_code(category_code, tenant_id) USING BTREE;
  ```
- **存量校验**：当前所有记录 `tenant_id='000000'` 且 `category_code` 全局唯一 ⇒ `(category_code, tenant_id)` 必然唯一，重建索引无冲突（已核对本地库）。
- **软删复用说明**：索引不含 `del_flag`（与项目全仓惯例一致），软删后同编码仍不可重建。这是项目通病（material/vendor/workshop 同），不在本方案单独引入 `del_flag` 以免破坏一致性；应用层 `checkCategoryCodeUnique`（MyBatis-Plus 逻辑删除自动排除 `del_flag='1'`）会判定"可用"但 DB 层会抛主键冲突——**作为已知限制记录，不在本方案修复**。

### 2.2 删除 treeselect 死代码
- `BdMaterialCategoryController`：删除 `treeselect()` 方法（`@GetMapping("/treeselect")`）。
- `IBdMaterialCategoryService`：删除 `selectCategoryTreeList()`、`buildCategoryTreeSelect()`。
- `BdMaterialCategoryServiceImpl`：删除对应实现；移除 `cn.hutool.core.lang.tree.Tree` 等不再使用的 import。
- `CacheNames.BD_CATEGORY_TREE` 保留（`selectList` 仍用），仅 `'treeselect'` key 不再写入。

### 2.3 分类管理页复用 useCategoryTree
- `category/index.vue` 的 `getList()` 改为复用 `useCategoryTree(proxy, { queryParams: {} })` 返回的 `getCategoryTree`/`categoryTree`（管理页需全量含停用分类，故传 `queryParams: {}` 而非默认 `{status:1}`）。
- 删除页面内 inline 的 `proxy.handleTree(...)` 调用。
- 父分类下拉 `getTreeselect()` 仍需"排除自身及子孙"语义，保留 `listCategoryExcludeChild` 调用，但建树同样走 composable（或抽取一个 `buildTreeFrom(list)` 工具）。

### 2.4 修 sortOrder 重复数据
```sql
UPDATE bd_material_category SET sort_order = 7 WHERE category_id = 14 AND sort_order = 6; -- 木材，与电气材料(6)去重
```

---

## 三、Track 2 — UX 打磨（中低风险）

### 3.1 物料数列（直系 + 含子孙累计）
- **VO**：`BdMaterialCategoryVo` 增 `private Long materialCount;`（直系）、`private Long materialCountTotal;`（含子孙累计）。
- **Service**：新增 `Map<Long,Long> countMaterialByCategoryIds(List<Long> ids)`（一条 `SELECT category_id, COUNT(*) FROM bd_material WHERE del_flag='0' AND category_id IN (...) GROUP BY category_id`）。在 `selectList` 组装 VO 时填充直系计数；累计计数由前端在 `handleTree` 后做后序累加（O(n)，避免 SQL `find_in_set` 全表扫描）。
- **前端**：表格新增"物料数"列，显示 `materialCountTotal`（累计），`materialCount`（直系）作 tooltip。

### 3.2 内联启停开关
- 表格 `status` 列由 `dict-tag` 改为 `el-switch`（`active-value=1`/`inactive-value=0`），`@change` 触发更新。
- **后端**：新增轻量端点 `PUT /erp/bd/category/{id}/status`（仅传 `status`），避免走全量 `updateByBo` 的 path 级联开销；内部复用 `@CacheEvict`。
- 与 Track 4.2 联动：若开关从 1→0，需确认级联停用子孙（见 5.2）。

### 3.3 删除阻断详情
- `handleDelete` 改为：先调 `getAllChildrenIds(id)` 获取子分类 + 关联物料；若非空，弹 `el-dialog` 列出"阻塞项"（子分类 N 个 / 物料 M 个，可点击跳转），不直接 `R.warn` 笼统提示。
- 后端 `allChildrenIds` 接口已存在，直接复用；无需后端改动。

### 3.4 搜索保留父级上下文
- **现状**：`getList` 把 `categoryCode/categoryName/status` 传后端，后端过滤后前端 `handleTree` 重建——匹配的子节点失去父链。
- **改为**：`getList` 始终拉全量（不传 code/name/status 给后端）→ 命中 `selectList` 的 `'list_all'` 缓存 → 前端在已建树上做客户端过滤，**保留匹配节点及其所有祖先链**（递归标记 + 剪枝）。
- 收益：搜索体验正确 + 缓存命中率提升。`status` 过滤亦改客户端。

### 3.5 路径展示
- 表格新增"分类路径"列（`pathNames`，`show-overflow-tooltip`），搜索/孤节点场景尤其有用；或并入"分类名称"列 tooltip。二选一，倾向独立列。

---

## 四、Track 3 — 分类属性模板（最高复杂度，建议最后实施）

### 4.1 新表 `bd_category_attribute`（属性定义，照 `dc_collection_point` 范式）
```sql
CREATE TABLE bd_category_attribute (
  attr_id        bigint(20) NOT NULL COMMENT '属性ID',
  tenant_id      varchar(20) DEFAULT '000000',
  category_id    bigint(20) NOT NULL COMMENT '所属分类ID',
  attr_code      varchar(64) NOT NULL COMMENT '属性编码',
  attr_name      varchar(128) NOT NULL COMMENT '属性名称',
  data_type      varchar(16) NOT NULL DEFAULT 'STRING' COMMENT 'STRING/NUMBER/ENUM/DATE/BOOLEAN',
  enum_values    varchar(1000) NULL COMMENT '枚举可选项(逗号分隔,data_type=ENUM时用)',
  unit           varchar(32) NULL COMMENT '单位(NUMBER时用)',
  required_flag  tinyint(1) DEFAULT 0 COMMENT '是否必填 0否1是',
  sort_order     int(11) DEFAULT 0,
  remark         varchar(500) NULL,
  attr1..attr4 / create_dept/create_by/create_time/update_by/update_time / del_flag,
  PRIMARY KEY (attr_id),
  UNIQUE INDEX uk_category_attr_code(attr_code, category_id, tenant_id),
  INDEX idx_category_id(category_id)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料分类属性模板';
```

### 4.2 新表 `bd_material_attribute_value`（属性值，照 `iot_device_data` 范式·分类型列）
```sql
CREATE TABLE bd_material_attribute_value (
  value_id       bigint(20) NOT NULL COMMENT '值ID',
  tenant_id      varchar(20) DEFAULT '000000',
  material_id    bigint(20) NOT NULL COMMENT '物料ID',
  attr_id        bigint(20) NOT NULL COMMENT '属性ID',
  data_type      varchar(16) NOT NULL COMMENT '冗余自模板,便于查询',
  numeric_value  decimal(14,4) NULL,
  text_value     varchar(1000) NULL,
  bool_value     tinyint(1) NULL,
  date_value     datetime NULL,
  attr1..attr4 / 标准审计字段 / del_flag,
  PRIMARY KEY (value_id),
  UNIQUE INDEX uk_material_attr(material_id, attr_id, tenant_id),
  INDEX idx_material_id(material_id),
  INDEX idx_attr_id(attr_id)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料扩展属性值';
```

### 4.3 新字典 `bd_attr_data_type`
| label | value |
|-------|-------|
| 字符串 | STRING |
| 数值 | NUMBER |
| 枚举 | ENUM |
| 日期 | DATE |
| 布尔 | BOOLEAN |

写入 `sys_dict_type` + `sys_dict_data`（主脚本 `ry-vue.sql` + 增量脚本）。

### 4.4 后端
- 全套 `BdCategoryAttribute`（entity/bo/vo/mapper/service/impl/controller）：按 `category_id` CRUD，`@DS("erp")`、`@CacheEvict(BD_CATEGORY_TREE)` 联动。
- 全套 `BdMaterialAttributeValue`（entity/bo/vo/mapper/service/impl）：按 `material_id` 批量查询/保存。
- **物料切换分类联动**（`BdMaterialServiceImpl.update`）：分类变更时，物理删除旧分类的属性值（`WHERE material_id=? AND attr_id NOT IN (新分类属性集)`），为新分类的必填属性预校验。
- **必填校验**：物料保存时，按 `required_flag=1` 校验对应值非空。
- **查询**：物料 VO 可选携带 `List<attrValueVo>`（详情接口返回；列表接口不携带，避免 N+1）。

### 4.5 前端
- **分类编辑对话框**：新增"分类属性"Tab，对当前分类做属性定义 CRUD（表格：编码/名称/类型[字典下拉]/枚举值/单位/必填/排序）。类型=ENUM 时显示 `enum_values` 输入。
- **物料编辑对话框**：新增"扩展属性"Tab，按 `form.categoryId` 拉取属性模板，类型化渲染：
  - STRING→`el-input`；NUMBER→`el-input-number`；ENUM→`el-select`（选项=`enum_values`）；DATE→`el-date-picker`；BOOLEAN→`el-switch`。
  - 切换 `categoryId` 时重新拉属性集并丢弃旧值（前端提示）。
- **类型/API**：新增 `plus-ui/src/api/erp/bd/category-attribute/` 与 `plus-ui/src/api/erp/bd/material-attribute-value/`（index.ts + types.ts）。

### 4.6 权限（sys_menu）
- 分类属性模板按钮权限：`bd:category:attr:list/add/edit/remove`（挂在分类菜单下）。
- 物料属性值读写复用 `bd:material:edit/query`，不新增。

---

## 五、Track 4 — 拖拽移动 + 停用级联（中风险）

### 5.1 拖拽改父级
- **后端**：新增 `PUT /erp/bd/category/{id}/move`（入参 `newParentId`、`newSortOrder`）。从 `updateByBo` 抽取公共方法 `moveCategory(entity, newParentId)`，复用 `fillPathAndLevel` + `validateCategory`（循环引用检测）+ `updateCategoryChildren`（子孙 path 级联）。`@CacheEvict`、`@Transactional`。
- **前端**：因 `el-table` 树不支持原生行拖拽，新增"拖拽视图"开关。开启后以 `el-tree`（`draggable` + `@node-drop` + `:allow-drop` 受控 prev/next/inner）渲染，落下时调 `move` 接口，成功后刷新。默认仍为表格视图。
- **循环拦截**：`:allow-drop` 前端预判 + 后端 `validateCategory` 兜底。

### 5.2 停用级联子孙
- **后端**：`updateByBo`/`changeStatus` 中若 `status: 1→0`，级联停用所有子孙（`find_in_set(self, path_ids)` 命中的子孙 `status=0`），单条 SQL 批量更新。
- **启用不级联**：`0→1` 仅启用自身；若其存在停用祖先，前端提示"存在停用的上级分类，启用后可能不可用"（不自动启用祖先链，避免误操作）。
- **前端**：内联开关 1→0 时弹确认"将同时停用 N 个子分类"（N 由 `allChildrenIds` 计算）。

---

## 六、表结构与存量数据评估（用户专项要求）

### 6.1 表结构是否需要优化
- **`bd_material_category`**：基础表结构健康，**无需增删列**。物化路径（path_ids/path_names/level_num）设计优秀；`attr1-4` 为全 `bd_` 表通用预留约定，保留。仅修复 `uk_category_code`（加 `tenant_id`，见 2.1）。其余索引（`idx_parent_id`/`idx_status`/`idx_level_sort`/`idx_tenant_id`）合理，保留。
- **新增 2 表**：`bd_category_attribute`、`bd_material_attribute_value`（Track 3）。
- **新增 1 字典**：`bd_attr_data_type`（Track 3）。
- **结论**：基础表仅索引微调；结构性扩展集中在属性模板（新表）。

### 6.2 存量数据是否需要调整
- **sortOrder 重复**：1 条 `UPDATE`（id14: 6→7），见 2.4。
- **索引重建**：`DROP`+`CREATE` `uk_category_code`，非破坏性；存量 `(category_code, tenant_id)` 唯一，无冲突（已核对）。
- **属性表**：空表起步，**无数据迁移**；存量 16 分类 / 物料默认无属性，用户按需定义。
- **结论**：仅 1 条数据修正 + 索引重建，风险极低；无大批量数据迁移。

---

## 七、实施顺序与风险

| 阶段 | 内容 | 风险 | 交付物 |
|------|------|------|--------|
| P1 | Track 1（索引修复 + 死代码清理 + composable 复用 + 数据修正） | 低 | 增量 SQL + 前后端小改 |
| P2 | Track 2（UX 打磨：物料数/内联开关/删除详情/搜索上下文/路径列） | 中低 | 前后端 |
| P3 | Track 4（拖拽视图 + 停用级联） | 中 | 后端 move 端点 + 前端 el-tree 拖拽 |
| P4 | Track 3（属性模板：2 新表 + 字典 + 前后端 + 物料页联动） | 高 | 新表 + 全套 CRUD + 两处前端 Tab |

每阶段配套增量脚本 `script/sql/update/2026-07-21-bd-category-*.sql`（幂等），主脚本 `ry-erp.sql`/`ry-vue.sql` 同步。

---

## 八、文件变更清单

**后端（ruoyi-erp）**
- 改：`BdMaterialCategoryController`、`IBdMaterialCategoryService`、`BdMaterialCategoryServiceImpl`、`BdMaterialCategoryVo`（+materialCount/materialCountTotal）、`BdMaterialServiceImpl`（属性联动 + count 查询）。
- 新（Track 3）：`BdCategoryAttribute` 全套（entity/bo/vo/mapper/service/impl/controller）、`BdMaterialAttributeValue` 全套。
- `CacheNames`：评估是否新增 `BD_CATEGORY_ATTR` 缓存名（或复用 `BD_CATEGORY_TREE`）。

**DDL / 字典 / 菜单**
- `script/sql/ry-erp.sql`（主：索引、2 新表）、`script/sql/ry-vue.sql`（主：字典、sys_menu 权限）。
- `script/sql/update/2026-07-21-bd-category-optimization.sql`（增量：索引修复 + sortOrder + 新表 + 字典 + 菜单，分节、幂等）。

**前端（plus-ui）**
- 改：`views/erp/bd/category/index.vue`（composable 复用 + 物料数列 + 内联开关 + 删除详情 + 搜索上下文 + 路径列 + 拖拽视图 + 属性 Tab）、`api/erp/bd/category/index.ts`+`types.ts`（move/status/count 端点）。
- 改：`views/erp/bd/material/index.vue`（扩展属性 Tab）。
- 新：`api/erp/bd/category-attribute/`、`api/erp/bd/material-attribute-value/`。
- 可能微调：`composables/erp/useCategoryTree.ts`（支持管理页全量场景）。

---

## 九、测试要点
- **多租户**：两租户同 `category_code` 互不冲突（索引修复后）。
- **软删**：同编码软删后行为（按方案：不可复用，与项目一致）。
- **path 级联**：移动/重命名后子孙 `path_ids`/`path_names`/`level_num` 正确（已有逻辑，回归）。
- **拖拽**：循环引用拦截、跨层级移动 path 刷新、`:allow-drop` 边界。
- **级联停用**：1→0 同步停用子孙；0→1 仅自身。
- **属性**：必填校验、切换分类属性集变化、类型化渲染（5 种 data_type）、物料详情返回属性值。
- **缓存**：增删改后 `BD_CATEGORY_TREE` 失效；搜索改客户端后命中率提升。
- **搜索上下文**：搜子节点保留祖先链。
