# 供应商字典口径对齐实施规格（bd_vendor）

> 日期：2026-09-23
> 范围：`ry-erp.bd_vendor`（种子数据 + 存量数据）、`ry-vue` 字典、供应商管理前后端、重建生成器
> 无结构性 DDL（不改表结构、不改列类型、不加索引）
> 例外：1 条列注释更新 —— `bd_vendor.industry` 注释由「行业」改为「供货品类」，否则注释会继续误导后来者（本次缺陷的成因之一）

## 1. 背景

菜单「ERP 系统 → 业务伙伴 → 供应商管理」中，`bd_vendor` 的行业/地区/分类/合作状态/默认发票类型五个字段的值与对应字典表值对不上，列表列渲染为空、筛选失效，且部分供应商实际无法参与业务。

### 1.1 实测现状（本地库逐行核对）

| 字段 | 字典 | 字典值域 | 现值 | 命中率 |
|---|---|---|---|---|
| `region` | `bd_region` | `1`~`7` | 12 行全为「华东」(label) | 0/12 |
| `industry` | `bd_industry` | `A`~`T`（国民经济门类） | 10 行为物料分类名，V1=`B`、V2=`C` | 2/12 |
| `category` | `bd_vendor_category` | `1`~`4` | 10 行为物料分类名，V1/V2=`1` | 2/12 |
| `status` | `bd_vendor_status` | `ACTIVE`/`FROZEN` | 10 行为 `1` | 2/12 |
| `default_invoice_type` | `pu_invoice_type` | `VAT_SPECIAL`/`VAT_NORMAL`/`OTHER` | 10 行为 `1` | 2/12 |

### 1.2 已产生的实际影响

- **硬阻断**：`PuPurchaseOrderServiceImpl.validateVendor()`（`ruoyi-modules/ruoyi-erp/.../pu/service/impl/PuPurchaseOrderServiceImpl.java:552`）对 `status != "ACTIVE"` 直接抛「供应商【X】状态异常（1），不可下单」→ 10/12 家供应商无法下采购订单（当前 `pu_purchase_order` 为 0 条，属尚未引爆）。
- **静默漏算**：`PuVendorEvaluationServiceImpl.java:169` 只取 `status='ACTIVE'`，供应商评估仅覆盖 V1/V2（实测 `pu_vendor_evaluation` 仅 2 个 vendor）。
- **筛选失效**：`BdVendorServiceImpl.buildQueryWrapper()`（`:113-121`）只过滤 `industry`/`region`，未过滤 `status`，前端「合作状态」下拉传参不生效。
- **展示为空**：前端 `dict-tag` 对不上字典，行业/地区/状态列渲染为空。

### 1.3 根因

`script/tools/rebuild/generate.py:274-284`：

```python
for i, (name, ind, desc) in enumerate(VENDORS, start=1):
    vals.append(row([i, TENANT, "V%05d" % i, name, None, "浙江省杭州市余杭区",
                     None, "0571-8888%04d" % i, None, None, None, None, 1,
                     ind, "华东", ind, 1, desc, BY, DEPT, "0"]))
```

即：把「物料二级分类名」同时写进 `industry` 与 `category`，`region` 硬编码为大区 **label**，`default_invoice_type` 与 `status` 写成占位整数 `1`。V1/V2 是后来在界面上手改过的（`sys_oper_log` 有 3 条 `BdVendorController.edit()` 记录），因此只有它们看起来「对」。

### 1.4 口径矛盾

`bd_industry` 采用 GB/T 4754 国民经济行业门类（A~T），整个制造业只有一个 `C`，粒度过粗；而重建脚本自身的注释与映射表表明设计意图是**「供应商行业 = 供货品类」**：

```python
# generate.py:585 按原材料叶子分类匹配供应商行业
VENDOR_BY_CAT = {"METAL_PLATE": 0, ..., "EL_CHIP": 3, "BT_CELL": 5, ...}
```

供应商行业需与 `bd_material_category` 对齐，才能支撑「按品类找供应商 / 比价」。

## 2. 设计决策

| 决策点 | 结论 |
|---|---|
| 行业口径 | 改为**供货品类**，与 `bd_material_category` 二级分类对齐，新建 `bd_vendor_industry` 字典 |
| `bd_industry` | **保留不删、仅停止被供应商页引用**（`sys_dict_type` 无启停列；删除会影响未来通用场景） |
| 地区 | **由省份自动推导并置只读**，值存字典 value（如 `2`） |
| 分类 | 保留 `bd_vendor_category`(1~4) 语义 = 供应商性质（原材料/标准件/服务类/综合型） |
| 存量回填 | 12 行顺带回填 `province/city/district_area_id` |
| 字典值校验 | **不加**硬编码 `@Pattern`（会把字典变成两处真相）；`validateVendor` 已在消费点兜底 |

## 3. 字典设计：新增 `bd_vendor_industry`

值直接复用 `bd_material_category.category_code`，使「按品类找供应商」成为可 join 的机器校验关系。

| value | label | 对应物料分类（`category_code`） |
|---|---|---|
| `METAL` | 金属材料 | METAL |
| `PLASTIC` | 塑料材料 | PLASTIC |
| `RUBBER_COMP` | 橡胶与复合材料 | RUBBER_COMP |
| `ELECTRONIC` | 电子元器件 | ELECTRONIC |
| `BATTERY` | 电池与电源 | BATTERY |
| `HARDWARE` | 五金件 | HARDWARE |
| `PACKAGING` | 包装材料 | PACKAGING |
| `CHEMICAL` | 化工辅料 | CHEMICAL |
| `SM_CAST` | 铸锻件 | SM_CAST |
| `OTHER` | 其他 | 兜底，唯一 `is_default='Y'` |

主键分配（现 `sys_dict_type` 最大 `dict_id=2075000000000000010`、`sys_dict_data` 最大 `dict_code=2075000000000000131`）：

- `sys_dict_type.dict_id = 2075000000000000011`
- `sys_dict_data.dict_code = 2075000000000000132` ~ `2075000000000000141`

## 4. 数据修复映射（12 行逐行确定）

映射采用**名称→代码的显式 CASE**，不使用模糊匹配。

| id | 名称 | industry | region | category | status | default_invoice_type |
|---|---|---|---|---|---|---|
| 1 | 华东钢材供应链有限公司 | `B` → `METAL` | 华东 → `2` | `1`（不变） | ACTIVE（不变） | 不变 |
| 2 | 江苏精密紧固件制造厂 | `C` → `HARDWARE` | 华东 → `2` | `1` → `2`（标准件） | ACTIVE（不变） | 不变 |
| 3 | 宁波工程塑料有限公司 | 塑料材料 → `PLASTIC` | 华东 → `2` | 塑料材料 → `1` | `1` → `ACTIVE` | `1` → `VAT_SPECIAL` |
| 4 | 深圳华芯电子科技有限公司 | 电子元器件 → `ELECTRONIC` | 华东 → `2` | 电子元器件 → `1` | `1` → `ACTIVE` | `1` → `VAT_SPECIAL` |
| 5 | 东莞连接器制造有限公司 | 电子元器件 → `ELECTRONIC` | 华东 → `2` | 电子元器件 → `1` | `1` → `ACTIVE` | `1` → `VAT_SPECIAL` |
| 6 | 惠州新能源电池有限公司 | 电池与电源 → `BATTERY` | 华东 → `2` | 电池与电源 → `1` | `1` → `ACTIVE` | `1` → `VAT_SPECIAL` |
| 7 | 苏州精密轴承有限公司 | 五金件 → `HARDWARE` | 华东 → `2` | 五金件 → `2`（标准件） | `1` → `ACTIVE` | `1` → `VAT_SPECIAL` |
| 8 | 广州表面处理材料有限公司 | 化工辅料 → `CHEMICAL` | 华东 → `2` | 化工辅料 → `1` | `1` → `ACTIVE` | `1` → `VAT_SPECIAL` |
| 9 | 青岛橡胶密封件有限公司 | 橡胶与复合材料 → `RUBBER_COMP` | 华东 → `2` | 橡胶与复合材料 → `1` | `1` → `ACTIVE` | `1` → `VAT_SPECIAL` |
| 10 | 武汉包装制品有限公司 | 包装材料 → `PACKAGING` | 华东 → `2` | 包装材料 → `1` | `1` → `ACTIVE` | `1` → `VAT_SPECIAL` |
| 11 | 常州铸造材料有限公司 | 金属材料 → `METAL` | 华东 → `2` | 金属材料 → `1` | `1` → `ACTIVE` | `1` → `VAT_SPECIAL` |
| 12 | 天津精密铸造厂 | 铸锻件 → `SM_CAST` | 华东 → `2` | 铸锻件 → `1` | `1` → `ACTIVE` | `1` → `VAT_SPECIAL` |

**行政区划回填**：12 行 `address` 均为「浙江省杭州市余杭区」，确定回填 `province_area_id=330000`、`city_area_id=330100`、`district_area_id=330110`（`street_id`/经纬度保持 NULL）。

## 5. 前端改造

### 5.1 `plus-ui/src/views/erp/bd/vendor/index.vue`

- `useDict('bd_industry', ...)` → `useDict('bd_vendor_industry', ...)`；筛选下拉、列表 `dict-tag`、表单 select 三处同步替换。
- `initForm()` 的 `status` / `defaultInvoiceType` 由 `null` 改为 `'ACTIVE'` / `'VAT_SPECIAL'`，对齐 DB 默认值，避免新建时必手选。
- 地区 `el-select` 加 `disabled`（只读，由省份驱动）。
- 省份非必填（现有 `rules` 只校验 `vendorCode`/`vendorName`），因此未选省份时 `region` 允许为空；一旦选了省份即自动写入对应大区，不允许手改。

### 5.2 `plus-ui/src/composables/useAreaCascade.ts`

新增**可选**参数 `regionField?: string`，仅当传入时生效（客户/仓库页不传，行为完全不变）：

- 省份变更后、编辑态级联回显后，按省份 `areaId` 的**前两位 GB 码**推导大区并写入 `form[regionField]`。
- 大区映射（7 大区 / 34 个省级单位）：

| 大区 value | label | 省份 GB 码前缀 |
|---|---|---|
| `1` | 华北 | 11 北京、12 天津、13 河北、14 山西、15 内蒙古 |
| `2` | 华东 | 31 上海、32 江苏、33 浙江、34 安徽、35 福建、36 江西、37 山东、71 台湾 |
| `3` | 东北 | 21 辽宁、22 吉林、23 黑龙江 |
| `4` | 华中 | 41 河南、42 湖北、43 湖南 |
| `5` | 华南 | 44 广东、45 广西、46 海南、81 香港、82 澳门 |
| `6` | 西南 | 50 重庆、51 四川、52 贵州、53 云南、54 西藏 |
| `7` | 西北 | 61 陕西、62 甘肃、63 青海、64 宁夏、65 新疆 |

以 GB 码而非省份名匹配，避免名称变更导致映射失效。

## 6. 后端改造

`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdVendorServiceImpl.java` 的 `buildQueryWrapper()` 补一行：

```java
lqw.eq(StringUtils.isNotBlank(bo.getStatus()), BdVendor::getStatus, bo.getStatus());
```

修复「合作状态」筛选完全失效。不加字典值硬编码校验（见 §2）。

## 7. 生成器改造

### 7.1 `script/tools/rebuild/catalog.py`

`VENDORS` 元组由 `(名称, 品类名, 备注)` 改为 `(名称, 品类代码, 供应商分类, 备注)`：

```python
VENDORS = [
    ("华东钢材供应链有限公司",   "METAL",       "1", "钢铁及有色金属材料供应"),
    ("江苏精密紧固件制造厂",     "HARDWARE",    "2", "标准紧固件与连接件"),
    ("宁波工程塑料有限公司",     "PLASTIC",     "1", "ABS/PC/POM/PA 工程塑料粒子"),
    ("深圳华芯电子科技有限公司", "ELECTRONIC",  "1", "芯片、被动件、连接器代理"),
    ("东莞连接器制造有限公司",   "ELECTRONIC",  "1", "板对板/线对线连接器"),
    ("惠州新能源电池有限公司",   "BATTERY",     "1", "锂电芯与电池辅料"),
    ("苏州精密轴承有限公司",     "HARDWARE",    "2", "轴承、销轴、弹簧件"),
    ("广州表面处理材料有限公司", "CHEMICAL",    "1", "电镀液、阳极氧化剂、油漆涂料"),
    ("青岛橡胶密封件有限公司",   "RUBBER_COMP", "1", "硅胶密封件与橡胶制品"),
    ("武汉包装制品有限公司",     "PACKAGING",   "1", "纸箱、缓冲材料、标签"),
    ("常州铸造材料有限公司",     "METAL",       "1", "铸造生铁、铸造辅料"),
    ("天津精密铸造厂",           "SM_CAST",     "1", "铝合金压铸件与铸钢件"),
]
```

### 7.2 `script/tools/rebuild/generate.py:274-284`

写入改为：`industry=品类代码`、`category=供应商分类`、`status='ACTIVE'`、`default_invoice_type='VAT_SPECIAL'`、`region='2'`，并回填 `province_area_id=330000` / `city_area_id=330100` / `district_area_id=330110`（与硬编码地址「浙江省杭州市余杭区」一致）。

`VENDOR_BY_CAT` 结构保持不变（仍按供应商下标选供应关系）。

## 8. 交付物

| 文件 | 动作 |
|---|---|
| `script/sql/ry-vue.sql` | 新增 `bd_vendor_industry` 字典类型 + 10 条字典数据 |
| `script/sql/ry-erp.sql` | 12 行 `bd_vendor` INSERT 改为字典合法值 + 回填行政区划 ID |
| `script/sql/update/2026-09-23-bd-vendor-dict-align.sql` | 存量库修复脚本（幂等 + 镜像备份表 + 回滚语句） |
| `doc/system/数据库增量变更记录.md` | 登记 09-23 批次 |
| `script/tools/rebuild/catalog.py`、`generate.py` | 根因修复 |
| `plus-ui/src/views/erp/bd/vendor/index.vue`、`plus-ui/src/composables/useAreaCascade.ts` | 前端接入 |
| `ruoyi-modules/ruoyi-erp/.../BdVendorServiceImpl.java` | 补 `status` 筛选 |
| 本文件 | 设计落档 |

## 9. 验收标准

1. **SQL 层**：`bd_vendor` 左连 `bd_region`/`bd_vendor_industry`/`bd_vendor_category`/`bd_vendor_status`/`pu_invoice_type`，孤儿值 **0 行**（修复前 12+10+10+10+10）。
2. **幂等性**：增量脚本重跑影响 **0 行**。
3. **前端**：供应商列表行业/地区/状态列均渲染出中文标签；「合作状态 = 合作中」筛选生效；地区下拉只读且随省份变化。
4. **业务**：新建采购订单选 V00003~V00012 不再报「状态异常(1)」；`pu_vendor_evaluation` 批量计算覆盖 12 家（当前 2 家）。
5. **回归**：客户 / 仓库页地址级联行为不变（composable 改动为 opt-in）。
6. **缓存**：验证前清 Redis 字典缓存 `000000:sys_dict`。

## 10. 非目标

- 不改表结构、不改列类型、不动 `bd_industry` 字典数据、不重构 `VENDOR_BY_CAT`。
- 本次不处理（顺带发现）：
  - `VENDOR_BY_CAT` 未覆盖 `SM_CAST`/`PURCHASED`，故 V12（铸锻件）在 `bd_material_vendor` 中无供应关系；
  - `bd_client` 种子地址形如「华东区产业大道 1 号」，非真实地址。

## 11. 回滚

增量脚本在表尾附完整回滚语句，并在执行前建立镜像备份表
`ry-erp.bd_vendor_bak_20260923`（结构与数据同源）。回滚 = 从备份表还原 5 个字典字段与 3 个行政区划字段。
