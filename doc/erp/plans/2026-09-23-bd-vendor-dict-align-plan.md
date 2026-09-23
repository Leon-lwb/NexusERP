# 供应商字典口径对齐实施计划（bd_vendor）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `bd_vendor` 的行业/地区/分类/合作状态/默认发票类型五个字段全部命中字典值，并把「行业」口径从国民经济门类改为与 `bd_material_category` 对齐的供货品类。

**Architecture:** 新建 `bd_vendor_industry` 字典（值 = `bd_material_category.category_code`），供应商页改用该字典；地区改为由省份 GB 码推导的只读字段；存量 12 行数据用「临时映射表 + 空值安全守卫」的幂等 UPDATE 修正；根因（重建生成器写占位整数）一并修掉。主脚本与增量脚本必须成对修改，否则会出现「主脚本与库长期不一致」的历史缺陷。

**Tech Stack:** MySQL 5.7 / MyBatis-Plus / Spring Boot（ruoyi-erp）/ Vue 3 + Element Plus + TypeScript / Python 3（重建生成器）

**规格:** `doc/erp/specs/2026-09-23-bd-vendor-dict-align-impl-spec.md`

---

## 文件结构

| 文件 | 职责 | 动作 |
|---|---|---|
| `script/sql/update/2026-09-23-bd-vendor-dict-align.sql` | 存量库可执行修复（字典 + 数据 + 备份 + 回滚） | 创建 |
| `script/sql/ry-vue.sql` | 全新安装的字典种子 | 修改 |
| `script/sql/ry-erp.sql` | 全新安装的供应商种子 + 列注释 | 修改 |
| `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdVendorServiceImpl.java` | 供应商查询条件 | 修改 |
| `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/bd/BdVendorServiceImplTest.java` | 查询条件单测 | 创建 |
| `plus-ui/src/composables/useAreaCascade.ts` | 省/市/区/街道级联（新增可选的省份→大区派生） | 修改 |
| `plus-ui/src/views/erp/bd/vendor/index.vue` | 供应商管理页 | 修改 |
| `script/tools/rebuild/catalog.py` | 供应商主数据定义 | 修改 |
| `script/tools/rebuild/generate.py` | 重建 SQL 生成器 | 修改 |
| `doc/system/数据库增量变更记录.md` | 变更登记 | 修改 |

**执行顺序**：Task 1 先建可执行脚本并落库（后面所有验证都依赖它）→ Task 2/3 同步主脚本 → Task 4 后端 → Task 5/6 前端 → Task 7 生成器 → Task 8 登记与端到端验收。

**环境事实（已验证）**
- 本地 MySQL：`localhost:3306`，`root` 空密码，库 `ry-vue` / `ry-erp`。
- MySQL 客户端：`D:\software\mysql-5.7.14\bin\mysql.exe`（PowerShell 里 `--host=localhost` 可用，`-h127.0.0.1` 会被解析成主机 `127` 而失败）。
- 修复前基线：`bd_vendor` 12 行；`region` 全为「华东」、`industry` 10 行为物料分类名、`category` 10 行为物料分类名、`status` 10 行为 `1`、`default_invoice_type` 10 行为 `1`；`province_area_id` 全 NULL。
- 字典主键余量：`sys_dict_type` 最大 `dict_id=2075000000000000010`，`sys_dict_data` 最大 `dict_code=2075000000000000131`。
- 行政区划 ID：浙江省 `330000`、杭州市 `330100`、余杭区 `330110`。
- 后端单测基线：无 `BdVendorServiceImplTest`；ERP 模块测试用 `@ExtendWith(MockitoExtension.class)` + `@Tag("dev")`，默认 Maven profile 即 `dev`。
- 后端测试命令（`ruoyi-erp` 依赖的兄弟模块需先装进本地仓库，否则离线模式下 `Cannot access ... in offline mode`）：
  ```powershell
  mvn -o install "-DskipTests=true" -pl ruoyi-modules/ruoyi-erp -am
  mvn -o test "-DskipTests=false" -pl ruoyi-modules/ruoyi-erp "-Dtest=BdVendorServiceImplTest"
  ```
  > PowerShell 下所有 `-D` 参数必须加引号：不加引号时 `-Dsurefire.failIfNoSpecifiedTests=false` 会被拆成 `.failIfNoSpecifiedTests=false`，Maven 报 `Unknown lifecycle phase`。
- 前端无测试工程，验收靠 `npm run type-check`（`vue-tsc --noEmit`）。

---

### Task 1: 增量修复脚本（字典 + 存量数据）

**Files:**
- Create: `script/sql/update/2026-09-23-bd-vendor-dict-align.sql`

- [ ] **Step 1: 建立失败基线（修复前必须先看到非法值）**

```powershell
& 'D:\software\mysql-5.7.14\bin\mysql.exe' --host=localhost --port=3306 --user=root --default-character-set=utf8mb4 --table -e "SELECT 'region' f, COUNT(*) total, SUM(d.dict_value IS NULL) orphan FROM ``ry-erp``.bd_vendor v LEFT JOIN ``ry-vue``.sys_dict_data d ON d.dict_type='bd_region' AND d.dict_value=v.region UNION ALL SELECT 'status', COUNT(*), SUM(d.dict_value IS NULL) FROM ``ry-erp``.bd_vendor v LEFT JOIN ``ry-vue``.sys_dict_data d ON d.dict_type='bd_vendor_status' AND d.dict_value=v.status UNION ALL SELECT 'invoice', COUNT(*), SUM(d.dict_value IS NULL) FROM ``ry-erp``.bd_vendor v LEFT JOIN ``ry-vue``.sys_dict_data d ON d.dict_type='pu_invoice_type' AND d.dict_value=v.default_invoice_type;"
```

Expected（修复前）：`region` orphan=12、`status` orphan=10、`invoice` orphan=10。

- [ ] **Step 2: 写脚本**

创建 `script/sql/update/2026-09-23-bd-vendor-dict-align.sql`，内容如下（`ry-erp` / `ry-vue` 均显式限定库名，避免依赖 `USE`）：

```sql
-- ============================================================================
-- 供应商字典口径对齐（bd_vendor）
-- 日期   : 2026-09-23
-- 规格   : doc/erp/specs/2026-09-23-bd-vendor-dict-align-impl-spec.md
-- 落库   : ry-vue（字典） + ry-erp（供应商数据）
-- DDL    : 仅 1 条列注释更新（bd_vendor.industry），无结构/类型/索引变更
-- 幂等   : 是（字典 INSERT IGNORE；数据 UPDATE 带 <=> 空值安全守卫，重跑 0 行）
-- 备份   : 执行前自动建立 ry-erp.bd_vendor_bak_20260923
-- 回滚   : 见文件末尾 §4
-- 注意   : 执行后必须清 Redis 字典缓存 000000:sys_dict
-- ============================================================================

-- ---------------------------------------------------------------------------
-- §0 备份（首次执行时灌数据；重跑因主键冲突被 INSERT IGNORE 跳过）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ry-erp`.`bd_vendor_bak_20260923` LIKE `ry-erp`.`bd_vendor`;
INSERT IGNORE INTO `ry-erp`.`bd_vendor_bak_20260923` SELECT * FROM `ry-erp`.`bd_vendor`;

-- ---------------------------------------------------------------------------
-- §1 新增字典：供应商供货品类 bd_vendor_industry
--    值复用 bd_material_category.category_code，使「按品类找供应商」可 join
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `ry-vue`.`sys_dict_type` VALUES
  (2075000000000000011, '000000', '供应商供货品类', 'bd_vendor_industry', 103, 1,
   '2026-09-23 09:00:00', NULL, NULL, '供应商供货品类，值对齐 bd_material_category.category_code');

INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES
  (2075000000000000132, '000000',  1, '金属材料',       'METAL',       'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000133, '000000',  2, '塑料材料',       'PLASTIC',     'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000134, '000000',  3, '橡胶与复合材料', 'RUBBER_COMP', 'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000135, '000000',  4, '电子元器件',     'ELECTRONIC',  'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000136, '000000',  5, '电池与电源',     'BATTERY',     'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000137, '000000',  6, '五金件',         'HARDWARE',    'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000138, '000000',  7, '包装材料',       'PACKAGING',   'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000139, '000000',  8, '化工辅料',       'CHEMICAL',    'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000140, '000000',  9, '铸锻件',         'SM_CAST',     'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000141, '000000', 10, '其他',           'OTHER',       'bd_vendor_industry', NULL, 'info',    'Y', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL);

-- ---------------------------------------------------------------------------
-- §2 列注释修正（原注释「行业」正是本次口径混乱的成因之一）
-- ---------------------------------------------------------------------------
ALTER TABLE `ry-erp`.`bd_vendor`
  MODIFY COLUMN `industry` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
  NULL DEFAULT NULL COMMENT '供货品类（字典 bd_vendor_industry，值对齐 bd_material_category.category_code）';

-- ---------------------------------------------------------------------------
-- §3 存量数据修复
--    逐行显式映射（不做模糊匹配），用临时表 + <=> 空值安全守卫保证幂等
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS `_bd_vendor_map`;
CREATE TEMPORARY TABLE `_bd_vendor_map` (
  `tenant_id`         varchar(20) NOT NULL,
  `vendor_code`       varchar(64) NOT NULL,
  `industry`          varchar(64) NOT NULL,
  `region`            varchar(64) NOT NULL,
  `category`          varchar(64) NOT NULL,
  `status`            varchar(64) NOT NULL,
  `default_invoice_type` varchar(20) NOT NULL,
  `province_area_id`  bigint NULL,
  `city_area_id`      bigint NULL,
  `district_area_id`  bigint NULL,
  PRIMARY KEY (`tenant_id`, `vendor_code`)
) ENGINE = InnoDB;

INSERT INTO `_bd_vendor_map` VALUES
  ('000000', 'V00001', 'METAL',       '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00002', 'HARDWARE',    '2', '2', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00003', 'PLASTIC',     '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00004', 'ELECTRONIC',  '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00005', 'ELECTRONIC',  '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00006', 'BATTERY',     '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00007', 'HARDWARE',    '2', '2', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00008', 'CHEMICAL',    '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00009', 'RUBBER_COMP', '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00010', 'PACKAGING',   '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00011', 'METAL',       '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00012', 'SM_CAST',     '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110);

UPDATE `ry-erp`.`bd_vendor` v
  JOIN `_bd_vendor_map` m
    ON m.`vendor_code` = v.`vendor_code` AND m.`tenant_id` = v.`tenant_id`
   SET v.`industry`              = m.`industry`,
       v.`region`                = m.`region`,
       v.`category`              = m.`category`,
       v.`status`                = m.`status`,
       v.`default_invoice_type`  = m.`default_invoice_type`,
       v.`province_area_id`      = m.`province_area_id`,
       v.`city_area_id`          = m.`city_area_id`,
       v.`district_area_id`      = m.`district_area_id`
 WHERE NOT (v.`industry`             <=> m.`industry`)
    OR NOT (v.`region`               <=> m.`region`)
    OR NOT (v.`category`             <=> m.`category`)
    OR NOT (v.`status`               <=> m.`status`)
    OR NOT (v.`default_invoice_type` <=> m.`default_invoice_type`)
    OR NOT (v.`province_area_id`     <=> m.`province_area_id`)
    OR NOT (v.`city_area_id`         <=> m.`city_area_id`)
    OR NOT (v.`district_area_id`     <=> m.`district_area_id`);

DROP TEMPORARY TABLE `_bd_vendor_map`;

-- ---------------------------------------------------------------------------
-- §3.1 自检：以下 4 个查询必须全部返回 0 行
-- ---------------------------------------------------------------------------
SELECT 'region'   AS field, v.vendor_id, v.region               AS bad_value FROM `ry-erp`.`bd_vendor` v LEFT JOIN `ry-vue`.`sys_dict_data` d ON d.dict_type='bd_region'          AND d.dict_value=v.region               WHERE d.dict_value IS NULL
UNION ALL
SELECT 'industry', v.vendor_id, v.industry                      FROM `ry-erp`.`bd_vendor` v LEFT JOIN `ry-vue`.`sys_dict_data` d ON d.dict_type='bd_vendor_industry' AND d.dict_value=v.industry             WHERE d.dict_value IS NULL
UNION ALL
SELECT 'category', v.vendor_id, v.category                      FROM `ry-erp`.`bd_vendor` v LEFT JOIN `ry-vue`.`sys_dict_data` d ON d.dict_type='bd_vendor_category' AND d.dict_value=v.category             WHERE d.dict_value IS NULL
UNION ALL
SELECT 'status',   v.vendor_id, v.status                        FROM `ry-erp`.`bd_vendor` v LEFT JOIN `ry-vue`.`sys_dict_data` d ON d.dict_type='bd_vendor_status'   AND d.dict_value=v.status               WHERE d.dict_value IS NULL
UNION ALL
SELECT 'invoice',  v.vendor_id, v.default_invoice_type          FROM `ry-erp`.`bd_vendor` v LEFT JOIN `ry-vue`.`sys_dict_data` d ON d.dict_type='pu_invoice_type'     AND d.dict_value=v.default_invoice_type WHERE d.dict_value IS NULL;

-- ============================================================================
-- §4 回滚（仅在需要撤销时手工执行）
-- ============================================================================
-- UPDATE `ry-erp`.`bd_vendor` v JOIN `ry-erp`.`bd_vendor_bak_20260923` b ON b.vendor_id = v.vendor_id
--    SET v.industry = b.industry, v.region = b.region, v.category = b.category,
--        v.status = b.status, v.default_invoice_type = b.default_invoice_type,
--        v.province_area_id = b.province_area_id, v.city_area_id = b.city_area_id,
--        v.district_area_id = b.district_area_id;
-- DELETE FROM `ry-vue`.`sys_dict_data` WHERE dict_type = 'bd_vendor_industry';
-- DELETE FROM `ry-vue`.`sys_dict_type` WHERE dict_type = 'bd_vendor_industry';
-- ALTER TABLE `ry-erp`.`bd_vendor` MODIFY COLUMN `industry` varchar(64)
--   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '行业';
-- -- 备份表确认无误后删除：DROP TABLE `ry-erp`.`bd_vendor_bak_20260923`;
```

- [ ] **Step 3: 执行脚本**

```powershell
& 'D:\software\mysql-5.7.14\bin\mysql.exe' --host=localhost --port=3306 --user=root --default-character-set=utf8mb4 --table -e "source D:/workspace/nexus-erp/script/sql/update/2026-09-23-bd-vendor-dict-align.sql"
```

Expected：§3.1 的 5 个自检查询返回**空结果集**（无行），无 `ERROR`。

- [ ] **Step 4: 验证数据已修正**

```powershell
& 'D:\software\mysql-5.7.14\bin\mysql.exe' --host=localhost --port=3306 --user=root --default-character-set=utf8mb4 --table -e "SELECT vendor_id, vendor_code, industry, region, category, status, default_invoice_type, province_area_id, city_area_id, district_area_id FROM ``ry-erp``.bd_vendor ORDER BY vendor_id;"
```

Expected：12 行，`industry` 为 `METAL`/`HARDWARE`/`PLASTIC`/`ELECTRONIC`/`BATTERY`/`RUBBER_COMP`/`PACKAGING`/`CHEMICAL`/`SM_CAST`，`region` 全为 `2`，`category` 全为 `1` 或 `2`，`status` 全为 `ACTIVE`，`default_invoice_type` 全为 `VAT_SPECIAL`，`province_area_id` 全为 `330000`。

- [ ] **Step 5: 验证幂等（重跑必须 0 行）**

```powershell
& 'D:\software\mysql-5.7.14\bin\mysql.exe' --host=localhost --port=3306 --user=root --default-character-set=utf8mb4 -e "source D:/workspace/nexus-erp/script/sql/update/2026-09-23-bd-vendor-dict-align.sql" 2>&1 | Select-String -Pattern "ERROR"
```

Expected：无输出（无 ERROR）。再用 Step 4 的查询确认数据未变、Step 1 的查询确认 orphan 全为 0。

- [ ] **Step 6: 清 Redis 字典缓存**

```powershell
docker exec -i redis redis-cli -n 0 DEL 000000:sys_dict
```

Expected：返回 `0` 或 `1`（该键可能不存在）。若本机 Redis 非 Docker 部署，改用对应的 `redis-cli`；无法执行时记录并在验收时重启应用替代。

- [ ] **Step 7: 提交**

```powershell
git add script/sql/update/2026-09-23-bd-vendor-dict-align.sql
git commit -m "fix(erp-bd): 供应商字典口径对齐增量脚本(供货品类字典+12行存量修复)"
```

---

### Task 2: 同步主脚本 `ry-vue.sql`

**Files:**
- Modify: `script/sql/ry-vue.sql:19487`（`sys_dict_data`，`bd_vendor_status` 行之后）
- Modify: `script/sql/ry-vue.sql:20444`（`sys_dict_type`，`bd_vendor_status` 行之后）

- [ ] **Step 1: 插入字典类型**

在 `script/sql/ry-vue.sql` 第 20444 行（`INSERT INTO `sys_dict_type` VALUES (107, '000000', '合作状态', 'bd_vendor_status', ...)`）之后插入：

```sql
INSERT INTO `sys_dict_type` VALUES (2075000000000000011, '000000', '供应商供货品类', 'bd_vendor_industry', 103, 1, '2026-09-23 09:00:00', NULL, NULL, '供应商供货品类，值对齐 bd_material_category.category_code');
```

- [ ] **Step 2: 插入字典数据**

在 `script/sql/ry-vue.sql` 第 19487 行（`INSERT INTO `sys_dict_data` VALUES (150, '000000', 2, '已冻结', 'FROZEN', 'bd_vendor_status', ...)`）之后插入：

```sql
INSERT INTO `sys_dict_data` VALUES (2075000000000000132, '000000', 1, '金属材料', 'METAL', 'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (2075000000000000133, '000000', 2, '塑料材料', 'PLASTIC', 'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (2075000000000000134, '000000', 3, '橡胶与复合材料', 'RUBBER_COMP', 'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (2075000000000000135, '000000', 4, '电子元器件', 'ELECTRONIC', 'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (2075000000000000136, '000000', 5, '电池与电源', 'BATTERY', 'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (2075000000000000137, '000000', 6, '五金件', 'HARDWARE', 'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (2075000000000000138, '000000', 7, '包装材料', 'PACKAGING', 'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (2075000000000000139, '000000', 8, '化工辅料', 'CHEMICAL', 'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (2075000000000000140, '000000', 9, '铸锻件', 'SM_CAST', 'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (2075000000000000141, '000000', 10, '其他', 'OTHER', 'bd_vendor_industry', NULL, 'info', 'Y', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL);
```

- [ ] **Step 3: 与库逐行核对**

```powershell
& 'D:\software\mysql-5.7.14\bin\mysql.exe' --host=localhost --port=3306 --user=root --default-character-set=utf8mb4 --table -e "SELECT dict_code, dict_sort, dict_label, dict_value, list_class, is_default FROM ``ry-vue``.sys_dict_data WHERE dict_type='bd_vendor_industry' ORDER BY dict_sort; SELECT dict_id, dict_name, dict_type FROM ``ry-vue``.sys_dict_type WHERE dict_type='bd_vendor_industry';"
```

Expected：10 行字典数据（`dict_sort` 1~10，`OTHER` 的 `is_default='Y'` 且 `list_class='info'`）+ 1 行字典类型（`dict_id=2075000000000000011`），与脚本内容完全一致。

- [ ] **Step 4: 提交**

```powershell
git add script/sql/ry-vue.sql
git commit -m "fix(erp-bd): 主脚本补 bd_vendor_industry 供货品类字典"
```

---

### Task 3: 同步主脚本 `ry-erp.sql`（12 行种子 + 列注释）

**Files:**
- Modify: `script/sql/ry-erp.sql:2541`（`industry` 列注释）
- Modify: `script/sql/ry-erp.sql:2574-2585`（12 行 `bd_vendor` INSERT）

- [ ] **Step 1: 修正列注释**

把 `script/sql/ry-erp.sql` 第 2541 行：

```sql
  `industry` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '行业',
```

改为：

```sql
  `industry` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '供货品类（字典 bd_vendor_industry，值对齐 bd_material_category.category_code）',
```

- [ ] **Step 2: 替换 12 行 INSERT**

把 `script/sql/ry-erp.sql` 第 2574-2585 行整块替换为（注意第 14 列 `industry`、第 15 列 `region`、第 16 列 `category`、第 17 列 `status`、第 13 列 `default_invoice_type`，以及第 23-25 列 `province/city/district_area_id`）：

```sql
INSERT INTO `bd_vendor` VALUES (1, '000000', 'V00001', '华东钢材供应链有限公司', NULL, '浙江省杭州市余杭区', NULL, '0571-88880001', NULL, NULL, NULL, NULL, 'VAT_SPECIAL', 'METAL', '2', '1', 'ACTIVE', '钢铁及有色金属材料供应', NULL, NULL, NULL, NULL, 330000, 330100, 330110, NULL, NULL, NULL, 103, 1, '2026-09-14 15:07:27', 1, '2026-09-19 12:50:13', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (2, '000000', 'V00002', '江苏精密紧固件制造厂', NULL, '浙江省杭州市余杭区', NULL, '0571-88880002', NULL, NULL, NULL, NULL, 'VAT_SPECIAL', 'HARDWARE', '2', '2', 'ACTIVE', '标准紧固件与连接件', NULL, NULL, NULL, NULL, 330000, 330100, 330110, NULL, NULL, NULL, 103, 1, '2026-09-14 15:07:27', 1, '2026-09-19 12:49:53', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (3, '000000', 'V00003', '宁波工程塑料有限公司', NULL, '浙江省杭州市余杭区', NULL, '0571-88880003', NULL, NULL, NULL, NULL, 'VAT_SPECIAL', 'PLASTIC', '2', '1', 'ACTIVE', 'ABS/PC/POM/PA 工程塑料粒子', NULL, NULL, NULL, NULL, 330000, 330100, 330110, NULL, NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (4, '000000', 'V00004', '深圳华芯电子科技有限公司', NULL, '浙江省杭州市余杭区', NULL, '0571-88880004', NULL, NULL, NULL, NULL, 'VAT_SPECIAL', 'ELECTRONIC', '2', '1', 'ACTIVE', '芯片、被动件、连接器代理', NULL, NULL, NULL, NULL, 330000, 330100, 330110, NULL, NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (5, '000000', 'V00005', '东莞连接器制造有限公司', NULL, '浙江省杭州市余杭区', NULL, '0571-88880005', NULL, NULL, NULL, NULL, 'VAT_SPECIAL', 'ELECTRONIC', '2', '1', 'ACTIVE', '板对板/线对线连接器', NULL, NULL, NULL, NULL, 330000, 330100, 330110, NULL, NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (6, '000000', 'V00006', '惠州新能源电池有限公司', NULL, '浙江省杭州市余杭区', NULL, '0571-88880006', NULL, NULL, NULL, NULL, 'VAT_SPECIAL', 'BATTERY', '2', '1', 'ACTIVE', '锂电芯与电池辅料', NULL, NULL, NULL, NULL, 330000, 330100, 330110, NULL, NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (7, '000000', 'V00007', '苏州精密轴承有限公司', NULL, '浙江省杭州市余杭区', NULL, '0571-88880007', NULL, NULL, NULL, NULL, 'VAT_SPECIAL', 'HARDWARE', '2', '2', 'ACTIVE', '轴承、销轴、弹簧件', NULL, NULL, NULL, NULL, 330000, 330100, 330110, NULL, NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (8, '000000', 'V00008', '广州表面处理材料有限公司', NULL, '浙江省杭州市余杭区', NULL, '0571-88880008', NULL, NULL, NULL, NULL, 'VAT_SPECIAL', 'CHEMICAL', '2', '1', 'ACTIVE', '电镀液、阳极氧化剂、油漆涂料', NULL, NULL, NULL, NULL, 330000, 330100, 330110, NULL, NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (9, '000000', 'V00009', '青岛橡胶密封件有限公司', NULL, '浙江省杭州市余杭区', NULL, '0571-88880009', NULL, NULL, NULL, NULL, 'VAT_SPECIAL', 'RUBBER_COMP', '2', '1', 'ACTIVE', '硅胶密封件与橡胶制品', NULL, NULL, NULL, NULL, 330000, 330100, 330110, NULL, NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (10, '000000', 'V00010', '武汉包装制品有限公司', NULL, '浙江省杭州市余杭区', NULL, '0571-88880010', NULL, NULL, NULL, NULL, 'VAT_SPECIAL', 'PACKAGING', '2', '1', 'ACTIVE', '纸箱、缓冲材料、标签', NULL, NULL, NULL, NULL, 330000, 330100, 330110, NULL, NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (11, '000000', 'V00011', '常州铸造材料有限公司', NULL, '浙江省杭州市余杭区', NULL, '0571-88880011', NULL, NULL, NULL, NULL, 'VAT_SPECIAL', 'METAL', '2', '1', 'ACTIVE', '铸造生铁、铸造辅料', NULL, NULL, NULL, NULL, 330000, 330100, 330110, NULL, NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL, NULL, NULL, NULL);
INSERT INTO `bd_vendor` VALUES (12, '000000', 'V00012', '天津精密铸造厂', NULL, '浙江省杭州市余杭区', NULL, '0571-88880012', NULL, NULL, NULL, NULL, 'VAT_SPECIAL', 'SM_CAST', '2', '1', 'ACTIVE', '铝合金压铸件与铸钢件', NULL, NULL, NULL, NULL, 330000, 330100, 330110, NULL, NULL, NULL, 103, 1, '2026-09-14 15:07:27', NULL, '2026-09-14 15:07:27', '0', NULL, NULL, NULL, NULL);
```

- [ ] **Step 3: 列数与主脚本/库三方一致核对**

```powershell
& 'D:\software\mysql-5.7.14\bin\mysql.exe' --host=localhost --port=3306 --user=root --default-character-set=utf8mb4 -e "SELECT COUNT(*) AS cols FROM information_schema.columns WHERE table_schema='ry-erp' AND table_name='bd_vendor';"
$lines = Select-String -Path 'script/sql/ry-erp.sql' -Pattern '^INSERT INTO ``bd_vendor`` VALUES' | ForEach-Object { $_.Line }
$lines.Count
($lines | ForEach-Object { ($_ -split ',').Count } | Sort-Object -Unique) -join ' / '
```

Expected：`cols=38`；`12` 行；每行的逗号数一致（说明 12 行都是 38 列，无错位）。

- [ ] **Step 4: 提交**

```powershell
git add script/sql/ry-erp.sql
git commit -m "fix(erp-bd): 主脚本修正 bd_vendor 12 行种子为字典合法值并回填行政区划"
```

---

### Task 4: 后端补 `status` 查询条件（TDD）

**Files:**
- Create: `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/bd/BdVendorServiceImplTest.java`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdVendorServiceImpl.java:113-121`

- [ ] **Step 1: 先装依赖模块（只需一次）**

```powershell
mvn -o install "-DskipTests=true" -pl ruoyi-modules/ruoyi-erp -am 2>&1 | Select-String -Pattern "BUILD|ERROR" | Select-Object -Last 10
```

Expected：`BUILD SUCCESS`。

- [ ] **Step 2: 写失败测试**

创建 `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/bd/BdVendorServiceImplTest.java`：

```java
package com.nexus.erp.bd;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.erp.bd.domain.BdVendor;
import com.nexus.erp.bd.domain.bo.BdVendorBo;
import com.nexus.erp.bd.domain.vo.BdVendorVo;
import com.nexus.erp.bd.mapper.BdVendorMapper;
import com.nexus.erp.bd.service.impl.BdVendorServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 供应商列表查询条件单元测试。
 * <p>
 * 回归点：前端「合作状态」下拉传入的 {@code status} 曾被 {@code buildQueryWrapper} 忽略，
 * 导致按合作状态筛选完全无效（ERP-BD 供应商页）。
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class BdVendorServiceImplTest {

    @Mock
    private BdVendorMapper baseMapper;

    @InjectMocks
    private BdVendorServiceImpl vendorService;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 解析 BdVendor::getStatus 依赖 TableInfo 的 lambda 缓存，
        // 未初始化会抛 "can not find lambda cache for this entity"
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BdVendor.class);
    }

    /** 传了 status 就必须出现在 SQL 条件与参数里 */
    @Test
    void selectPageList_filtersByStatus() {
        when(baseMapper.selectVoPage(any(), any())).thenReturn(new Page<BdVendorVo>());

        BdVendorBo bo = new BdVendorBo();
        bo.setStatus("ACTIVE");

        vendorService.selectPageList(bo, new PageQuery(10, 1));

        LambdaQueryWrapper<BdVendor> lqw = capturedWrapper();
        assertTrue(lqw.getTargetSql().contains("status"), "SQL 条件应包含 status，实际: " + lqw.getTargetSql());
        assertTrue(lqw.getParamNameValuePairs().containsValue("ACTIVE"),
            "查询参数应包含 ACTIVE，实际: " + lqw.getParamNameValuePairs());
    }

    /** status 为空时不得产生 status 条件（避免筛出空结果） */
    @Test
    void selectPageList_omitsStatusWhenBlank() {
        when(baseMapper.selectVoPage(any(), any())).thenReturn(new Page<BdVendorVo>());

        vendorService.selectPageList(new BdVendorBo(), new PageQuery(10, 1));

        LambdaQueryWrapper<BdVendor> lqw = capturedWrapper();
        assertFalse(lqw.getTargetSql().contains("status"), "未传 status 时不应有 status 条件，实际: " + lqw.getTargetSql());
    }

    /** 行业/地区筛选（既有行为，防回归） */
    @Test
    void selectPageList_filtersByIndustryAndRegion() {
        when(baseMapper.selectVoPage(any(), any())).thenReturn(new Page<BdVendorVo>());

        BdVendorBo bo = new BdVendorBo();
        bo.setIndustry("METAL");
        bo.setRegion("2");

        vendorService.selectPageList(bo, new PageQuery(10, 1));

        LambdaQueryWrapper<BdVendor> lqw = capturedWrapper();
        assertTrue(lqw.getParamNameValuePairs().containsValue("METAL"));
        assertTrue(lqw.getParamNameValuePairs().containsValue("2"));
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<BdVendor> capturedWrapper() {
        ArgumentCaptor<LambdaQueryWrapper<BdVendor>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(baseMapper).selectVoPage(any(), captor.capture());
        return captor.getValue();
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

```powershell
mvn -o test "-DskipTests=false" -pl ruoyi-modules/ruoyi-erp "-Dtest=BdVendorServiceImplTest"
```

Expected：`Tests run: 3, Failures: 2` —— `selectPageList_filtersByStatus` 与 `selectPageList_filtersByIndustryAndRegion`… 注意：**只有 `selectPageList_filtersByStatus` 会失败**（`status` 未进条件），另两个应通过。若 `selectPageList_omitsStatusWhenBlank` 也失败，说明 `status` 被误加成了其它列，需先排查。

- [ ] **Step 4: 补查询条件**

把 `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdVendorServiceImpl.java` 的 `buildQueryWrapper`：

```java
    private LambdaQueryWrapper<BdVendor> buildQueryWrapper(BdVendorBo bo) {
        LambdaQueryWrapper<BdVendor> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getVendorCode()), BdVendor::getVendorCode, bo.getVendorCode());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), BdVendor::getVendorName, bo.getVendorName());
        lqw.eq(StringUtils.isNotBlank(bo.getIndustry()), BdVendor::getIndustry, bo.getIndustry());
        lqw.eq(StringUtils.isNotBlank(bo.getRegion()), BdVendor::getRegion, bo.getRegion());
        lqw.eq(bo.getDeptId() != null, BdVendor::getDeptId, bo.getDeptId());
        return lqw;
    }
```

改为（新增一行 `status`，放在 `region` 之后）：

```java
    private LambdaQueryWrapper<BdVendor> buildQueryWrapper(BdVendorBo bo) {
        LambdaQueryWrapper<BdVendor> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getVendorCode()), BdVendor::getVendorCode, bo.getVendorCode());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), BdVendor::getVendorName, bo.getVendorName());
        lqw.eq(StringUtils.isNotBlank(bo.getIndustry()), BdVendor::getIndustry, bo.getIndustry());
        lqw.eq(StringUtils.isNotBlank(bo.getRegion()), BdVendor::getRegion, bo.getRegion());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), BdVendor::getStatus, bo.getStatus());
        lqw.eq(bo.getDeptId() != null, BdVendor::getDeptId, bo.getDeptId());
        return lqw;
    }
```

- [ ] **Step 5: 运行测试确认通过**

```powershell
mvn -o test "-DskipTests=false" -pl ruoyi-modules/ruoyi-erp "-Dtest=BdVendorServiceImplTest"
```

Expected：`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS`。

- [ ] **Step 6: 顺带把实体与 BO 的注释同步为「供货品类」**

`BdVendor.java` 中：

```java
    /**
     * 所属行业
     */
    private String industry;
```

改为：

```java
    /**
     * 供货品类（字典 bd_vendor_industry，值对齐 bd_material_category.category_code）
     */
    private String industry;
```

`BdVendorBo.java` 中：

```java
    /**
     * 行业
     */
    private String industry;
```

改为：

```java
    /**
     * 供货品类（字典 bd_vendor_industry）
     */
    private String industry;
```

- [ ] **Step 7: 提交**

```powershell
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/service/impl/BdVendorServiceImpl.java ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/domain/BdVendor.java ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/domain/bo/BdVendorBo.java ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/bd/BdVendorServiceImplTest.java
git commit -m "fix(erp-bd): 供应商列表补 status 筛选条件 + 单测回归"
```

---

### Task 5: 前端 `useAreaCascade` 支持省份→大区派生

**Files:**
- Modify: `plus-ui/src/composables/useAreaCascade.ts`

- [ ] **Step 1: 新增大区映射与导出函数**

在 `useAreaCascade.ts` 的 `import` 之后、`export interface AreaAddressForm` 之前插入：

```ts
/**
 * 省级行政区划 GB 码前两位 → 大区字典值（`bd_region`）。
 * <p>
 * 用 GB 码而非省份名匹配：省份名可能变更或带「省/市/自治区」后缀差异，GB 码稳定。
 */
const PROVINCE_REGION: Record<string, string> = {
  '11': '1', '12': '1', '13': '1', '14': '1', '15': '1', // 华北
  '21': '3', '22': '3', '23': '3',                        // 东北
  '31': '2', '32': '2', '33': '2', '34': '2', '35': '2', '36': '2', '37': '2', '71': '2', // 华东
  '41': '4', '42': '4', '43': '4',                        // 华中
  '44': '5', '45': '5', '46': '5', '81': '5', '82': '5',  // 华南
  '50': '6', '51': '6', '52': '6', '53': '6', '54': '6',  // 西南
  '61': '7', '62': '7', '63': '7', '64': '7', '65': '7'   // 西北
};

/**
 * 由省份行政区划 ID（GB 码）推导大区字典值。
 *
 * @param provinceAreaId 省份 areaId，如 330000
 * @returns 大区字典值（`1`~`7`），无法识别时返回空串
 */
export function regionOfProvince(provinceAreaId: unknown): string {
  if (provinceAreaId == null) return '';
  return PROVINCE_REGION[String(provinceAreaId).slice(0, 2)] ?? '';
}
```

- [ ] **Step 2: 扩展可选项接口**

把 `UseAreaCascadeOptions`：

```ts
/** useAreaCascade 的可选项 */
export interface UseAreaCascadeOptions {
  /**
   * 拼接结果写入表单的哪个字段，默认 `address`。
   * 仓库页 wm/warehouse 用的是 `location`（WarehouseVO.location），
   * 若沿用默认值会导致该页地址不再回填。
   */
  addressField?: string;
}
```

改为：

```ts
/** useAreaCascade 的可选项 */
export interface UseAreaCascadeOptions {
  /**
   * 拼接结果写入表单的哪个字段，默认 `address`。
   * 仓库页 wm/warehouse 用的是 `location`（WarehouseVO.location），
   * 若沿用默认值会导致该页地址不再回填。
   */
  addressField?: string;
  /**
   * 大区字段名。传入时按省份自动派生并写入 `bd_region` 字典值（只读派生，不接受手工输入）。
   * 供应商页传 `'region'`；客户页 / 仓库页不传，行为与改动前完全一致。
   */
  regionField?: string;
}
```

- [ ] **Step 3: 实现派生逻辑并在两处挂钩**

把：

```ts
export function useAreaCascade(form: Ref<AreaAddressForm>, options: UseAreaCascadeOptions = {}) {
  const addressField = options.addressField ?? 'address';
  const provinceOptions = ref<AreaVO[]>([]);
```

改为：

```ts
export function useAreaCascade(form: Ref<AreaAddressForm>, options: UseAreaCascadeOptions = {}) {
  const addressField = options.addressField ?? 'address';
  const regionField = options.regionField;
  const provinceOptions = ref<AreaVO[]>([]);
```

在 `updateAddress()` 函数之后插入：

```ts
  /**
   * 省份 → 大区派生（仅在配置了 regionField 时生效）。
   * <p>
   * 省份为空时把大区一并清空，避免残留上一次的手工值或旧标签。
   */
  function syncRegion() {
    if (!regionField) return;
    const region = regionOfProvince(form.value.provinceAreaId);
    form.value[regionField] = region === '' ? null : region;
  }
```

把 `onProvinceChange`：

```ts
  async function onProvinceChange(value: string | number | null) {
    resetBelow('province');
    // 子级加载失败也必须回填地址，否则会残留上一次的拼接结果
    try {
      if (value) await loadChildren('city', value);
    } finally {
      updateAddress();
    }
  }
```

改为：

```ts
  async function onProvinceChange(value: string | number | null) {
    resetBelow('province');
    // 子级加载失败也必须回填地址，否则会残留上一次的拼接结果
    try {
      if (value) await loadChildren('city', value);
    } finally {
      updateAddress();
      syncRegion();
    }
  }
```

把 `loadAddressCascade` 开头：

```ts
  async function loadAddressCascade() {
    await loadProvinces();
    if (!form.value.provinceAreaId) return;
```

改为：

```ts
  async function loadAddressCascade() {
    await loadProvinces();
    // 先派生一次：省份为空时也要清掉历史遗留的大区标签（如旧数据的「华东」）
    syncRegion();
    if (!form.value.provinceAreaId) return;
```

- [ ] **Step 4: 类型检查**

```powershell
cd plus-ui; npm run type-check 2>&1 | Select-Object -Last 20
```

Expected：无新增报错（改动前若已存在历史报错，需比对报错条数不增加）。

- [ ] **Step 5: 提交**

```powershell
git add plus-ui/src/composables/useAreaCascade.ts
git commit -m "feat(plus-ui): useAreaCascade 支持按省份派生大区(opt-in)"
```

---

### Task 6: 供应商页接入新字典与派生地区

**Files:**
- Modify: `plus-ui/src/views/erp/bd/vendor/index.vue`

- [ ] **Step 1: 切换行业字典**

把第 369-371 行：

```ts
const { bd_industry, bd_region, bd_vendor_status, bd_vendor_category, pu_invoice_type } = toRefs<any>(
  proxy.useDict('bd_industry', 'bd_region', 'bd_vendor_status', 'bd_vendor_category', 'pu_invoice_type')
);
```

改为：

```ts
const { bd_vendor_industry, bd_region, bd_vendor_status, bd_vendor_category, pu_invoice_type } = toRefs<any>(
  proxy.useDict('bd_vendor_industry', 'bd_region', 'bd_vendor_status', 'bd_vendor_category', 'pu_invoice_type')
);
```

- [ ] **Step 2: 替换三处 `bd_industry` 引用**

搜索表单（第 17-19 行）：

```html
            <el-select v-model="queryParams.industry" placeholder="请选择行业" clearable style="width: 100%">
              <el-option v-for="dict in bd_industry" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
```

改为：

```html
            <el-select v-model="queryParams.industry" placeholder="请选择供货品类" clearable style="width: 100%">
              <el-option v-for="dict in bd_vendor_industry" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
```

列表列（第 69-73 行）：

```html
      <el-table-column label="行业" align="center" prop="industry">
        <template #default="scope">
          <dict-tag :options="bd_industry" :value="scope.row.industry" />
        </template>
      </el-table-column>
```

改为：

```html
      <el-table-column label="供货品类" align="center" prop="industry">
        <template #default="scope">
          <dict-tag :options="bd_vendor_industry" :value="scope.row.industry" />
        </template>
      </el-table-column>
```

表单（第 248-252 行）：

```html
                <el-form-item label="行业" prop="industry">
                  <el-select v-model="form.industry" placeholder="请选择行业" style="width: 100%" :disabled="isViewMode">
                    <el-option v-for="dict in bd_industry" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
                  </el-select>
                </el-form-item>
```

改为：

```html
                <el-form-item label="供货品类" prop="industry">
                  <el-select v-model="form.industry" placeholder="请选择供货品类" style="width: 100%" :disabled="isViewMode">
                    <el-option v-for="dict in bd_vendor_industry" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
                  </el-select>
                </el-form-item>
```

- [ ] **Step 3: 地区改为只读派生**

把第 254-260 行：

```html
              <el-col :span="12">
                <el-form-item label="地区" prop="region">
                  <el-select v-model="form.region" placeholder="请选择地区" style="width: 100%" :disabled="isViewMode">
                    <el-option v-for="dict in bd_region" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
```

改为（`disabled` 恒真，去掉 `:disabled="isViewMode"`；提示语说明来源）：

```html
              <el-col :span="12">
                <el-form-item label="地区" prop="region">
                  <el-select v-model="form.region" placeholder="由省份自动带出" disabled style="width: 100%">
                    <el-option v-for="dict in bd_region" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
```

- [ ] **Step 4: 启用大区派生**

把第 480-491 行：

```ts
const {
  provinceOptions,
  cityOptions,
  districtOptions,
  streetOptions,
  loadProvinces,
  loadAddressCascade,
  onProvinceChange,
  onCityChange,
  onDistrictChange,
  onStreetChange
} = useAreaCascade(form);
```

改为：

```ts
const {
  provinceOptions,
  cityOptions,
  districtOptions,
  streetOptions,
  loadProvinces,
  loadAddressCascade,
  onProvinceChange,
  onCityChange,
  onDistrictChange,
  onStreetChange
} = useAreaCascade(form, { regionField: 'region' });
```

- [ ] **Step 5: 表单默认值对齐 DB 默认**

把 `initForm` 中（第 432、436 行）：

```ts
    defaultInvoiceType: null,
    industry: null,
    region: null,
    category: null,
    status: null,
```

改为：

```ts
    defaultInvoiceType: 'VAT_SPECIAL',
    industry: null,
    region: null,
    category: null,
    status: 'ACTIVE',
```

- [ ] **Step 6: 类型检查与 lint**

```powershell
cd plus-ui; npm run type-check 2>&1 | Select-Object -Last 20
npx eslint src/views/erp/bd/vendor/index.vue src/composables/useAreaCascade.ts
```

Expected：`type-check` 无新增报错；`eslint` 退出码 0、无输出。

- [ ] **Step 7: 确认无残留旧字典引用**

```powershell
Select-String -Path 'plus-ui/src/views/erp/bd/vendor/index.vue' -Pattern 'bd_industry'
```

Expected：无匹配。

- [ ] **Step 8: 提交**

```powershell
git add plus-ui/src/views/erp/bd/vendor/index.vue
git commit -m "feat(plus-ui): 供应商页改用供货品类字典, 地区由省份只读派生"
```

---

### Task 7: 修正重建生成器（根因）

**Files:**
- Modify: `script/tools/rebuild/catalog.py:201-214`
- Modify: `script/tools/rebuild/generate.py:274-284`

- [ ] **Step 1: 改 `catalog.py` 的 `VENDORS`**

把 `script/tools/rebuild/catalog.py` 第 201-214 行：

```python
VENDORS = [
    ("华东钢材供应链有限公司", "金属材料", "钢铁及有色金属材料供应"),
    ("江苏精密紧固件制造厂", "五金件", "标准紧固件与连接件"),
    ("宁波工程塑料有限公司", "塑料材料", "ABS/PC/POM/PA 工程塑料粒子"),
    ("深圳华芯电子科技有限公司", "电子元器件", "芯片、被动件、连接器代理"),
    ("东莞连接器制造有限公司", "电子元器件", "板对板/线对线连接器"),
    ("惠州新能源电池有限公司", "电池与电源", "锂电芯与电池辅料"),
    ("苏州精密轴承有限公司", "五金件", "轴承、销轴、弹簧件"),
    ("广州表面处理材料有限公司", "化工辅料", "电镀液、阳极氧化剂、油漆涂料"),
    ("青岛橡胶密封件有限公司", "橡胶与复合材料", "硅胶密封件与橡胶制品"),
    ("武汉包装制品有限公司", "包装材料", "纸箱、缓冲材料、标签"),
    ("常州铸造材料有限公司", "金属材料", "铸造生铁、铸造辅料"),
    ("天津精密铸造厂", "铸锻件", "铝合金压铸件与铸钢件"),
]
```

改为（第二列由「物料分类名」改为「供货品类代码」，第三列新增「供应商分类」= `bd_vendor_category` 字典值）：

```python
# (名称, 供货品类代码 bd_vendor_industry, 供应商分类 bd_vendor_category, 备注)
# 供货品类代码必须与 bd_material_category.category_code 对齐（见 generate.py 的 VENDOR_BY_CAT）
VENDORS = [
    ("华东钢材供应链有限公司", "METAL", "1", "钢铁及有色金属材料供应"),
    ("江苏精密紧固件制造厂", "HARDWARE", "2", "标准紧固件与连接件"),
    ("宁波工程塑料有限公司", "PLASTIC", "1", "ABS/PC/POM/PA 工程塑料粒子"),
    ("深圳华芯电子科技有限公司", "ELECTRONIC", "1", "芯片、被动件、连接器代理"),
    ("东莞连接器制造有限公司", "ELECTRONIC", "1", "板对板/线对线连接器"),
    ("惠州新能源电池有限公司", "BATTERY", "1", "锂电芯与电池辅料"),
    ("苏州精密轴承有限公司", "HARDWARE", "2", "轴承、销轴、弹簧件"),
    ("广州表面处理材料有限公司", "CHEMICAL", "1", "电镀液、阳极氧化剂、油漆涂料"),
    ("青岛橡胶密封件有限公司", "RUBBER_COMP", "1", "硅胶密封件与橡胶制品"),
    ("武汉包装制品有限公司", "PACKAGING", "1", "纸箱、缓冲材料、标签"),
    ("常州铸造材料有限公司", "METAL", "1", "铸造生铁、铸造辅料"),
    ("天津精密铸造厂", "SM_CAST", "1", "铝合金压铸件与铸钢件"),
]
```

- [ ] **Step 2: 改 `generate.py` 的写入逻辑**

把 `script/tools/rebuild/generate.py` 第 273-284 行：

```python
ven_id, vals = {}, []
for i, (name, ind, desc) in enumerate(VENDORS, start=1):
    ven_id[name] = i
    vals.append(row([i, TENANT, "V%05d" % i, name, None, "浙江省杭州市余杭区",
                     None, "0571-8888%04d" % i, None, None, None, None, 1,
                     ind, "华东", ind, 1, desc, BY, DEPT, "0"]))
w("INSERT INTO `ry-erp`.bd_vendor")
w("  (vendor_id, tenant_id, vendor_code, vendor_name, tax_id, address, contact_person,")
w("   phone, bank_account, invoice_title, invoice_address_phone, invoice_bank_name,")
w("   default_invoice_type, industry, region, category, status, remark,")
w("   create_by, create_dept, del_flag) VALUES")
```

改为（`default_invoice_type`/`status` 写真实字典值而非占位整数 `1`；`region` 写字典值 `2`；补行政区划 ID 与硬编码地址「浙江省杭州市余杭区」保持一致）：

```python
ven_id, vals = {}, []
for i, (name, ind_code, cat, desc) in enumerate(VENDORS, start=1):
    ven_id[name] = i
    # region 固定 2=华东、省市区固定 浙江省/杭州市/余杭区 —— 与上面的硬编码 address 一致
    vals.append(row([i, TENANT, "V%05d" % i, name, None, "浙江省杭州市余杭区",
                     None, "0571-8888%04d" % i, None, None, None, None, "VAT_SPECIAL",
                     ind_code, "2", cat, "ACTIVE", desc,
                     330000, 330100, 330110, BY, DEPT, "0"]))
w("INSERT INTO `ry-erp`.bd_vendor")
w("  (vendor_id, tenant_id, vendor_code, vendor_name, tax_id, address, contact_person,")
w("   phone, bank_account, invoice_title, invoice_address_phone, invoice_bank_name,")
w("   default_invoice_type, industry, region, category, status, remark,")
w("   province_area_id, city_area_id, district_area_id, create_by, create_dept, del_flag) VALUES")
```

- [ ] **Step 3: 干跑生成器（写临时文件，绝不覆盖主脚本）**

```powershell
python script/tools/rebuild/generate.py > $env:TEMP\rebuild_check.sql
Select-String -Path $env:TEMP\rebuild_check.sql -Pattern "INSERT INTO ``ry-erp``.bd_vendor" -Context 0,14 | Select-Object -First 1
```

Expected：`bd_vendor` 的 INSERT 片段中 12 行均为 `'VAT_SPECIAL', 'METAL'|'HARDWARE'|…, '2', '1'|'2', 'ACTIVE'`，且行政区划列为 `330000, 330100, 330110`。

- [ ] **Step 4: 断言每行 24 列（防列数错位）**

```powershell
$vendorBlock = (Get-Content $env:TEMP\rebuild_check.sql -Raw) -split "INSERT INTO ``ry-erp``\.bd_vendor" | Select-Object -Skip 1 -First 1
$vendorBlock = ($vendorBlock -split ';')[0]
$rows = [regex]::Matches($vendorBlock, '\(([^()]*)\)') | ForEach-Object { $_.Groups[1].Value }
$rows.Count
($rows | ForEach-Object { ($_ -split ',').Count } | Sort-Object -Unique) -join ' / '
```

Expected：`12`；`24`（列清单 24 列、每行 24 个值，唯一值只有一个）。

- [ ] **Step 5: 清理临时文件**

```powershell
Remove-Item $env:TEMP\rebuild_check.sql
```

- [ ] **Step 6: 提交**

```powershell
git add script/tools/rebuild/catalog.py script/tools/rebuild/generate.py
git commit -m "fix(rebuild): 生成器改写字典合法值, 修供应商种子脏值根因"
```

---

### Task 8: 变更登记与端到端验收

**Files:**
- Modify: `doc/system/数据库增量变更记录.md`

- [ ] **Step 1: 登记变更**

在 `doc/system/数据库增量变更记录.md` 的 `## 2026-09` 表格末尾追加一行（表格为 `| 日期 | 变更 | 落库 |`）：

```markdown
| 09-23 | **供应商字典口径对齐（`2026-09-23-bd-vendor-dict-align.sql`，含 1 条列注释 DDL）**：① `bd_vendor` 的 `industry` 口径由「国民经济行业门类」改为**供货品类**，新建字典 `bd_vendor_industry`（dict_id `2075000000000000011`，dict_code `2075000000000000132`~`0141`，10 项：金属材料/塑料材料/橡胶与复合材料/电子元器件/电池与电源/五金件/包装材料/化工辅料/铸锻件/其他），**值复用 `bd_material_category.category_code`**，使「按品类找供应商」成为可 join 的关系；`bd_industry`(A~T) 保留不删、仅不再被供应商页引用。② 12 行存量数据逐行显式映射修正：`region` 由 label「华东」改为字典值 `2`、`industry` 由物料分类名改为品类代码、`category` 由物料分类名改为 `1`/`2`（V2 紧固件、V7 轴承归标准件）、`status` 由 `1` 改为 `ACTIVE`、`default_invoice_type` 由 `1` 改为 `VAT_SPECIAL`，并按地址「浙江省杭州市余杭区」回填 `province/city/district_area_id = 330000/330100/330110`。③ **修复前 `status='1'` 是硬阻断**：`PuPurchaseOrderServiceImpl.validateVendor()` 对非 `ACTIVE` 抛「状态异常(1)，不可下单」，10/12 家供应商无法下采购订单；`PuVendorEvaluationServiceImpl` 只取 `ACTIVE`，供应商评估仅覆盖 2 家。④ 根因 `script/tools/rebuild/generate.py:274-284` 已修（原把物料分类名同时写进 `industry`/`category`、`region` 写 label、`default_invoice_type`/`status` 写占位整数 `1`）。⑤ 顺带修复 `BdVendorServiceImpl.buildQueryWrapper` 缺失 `status` 条件导致「合作状态」筛选完全失效，并补单测。**无结构性 DDL**（仅 `industry` 列注释更新），**幂等**（字典 `INSERT IGNORE`、数据 UPDATE 带 `<=>` 空值安全守卫），执行前自动建备份表 `ry-erp.bd_vendor_bak_20260923`，回滚语句见脚本末尾。执行后需清 Redis 字典缓存 `000000:sys_dict` | ry-vue + ry-erp |
```

- [ ] **Step 2: 端到端验收 — 字典孤儿值归零**

```powershell
& 'D:\software\mysql-5.7.14\bin\mysql.exe' --host=localhost --port=3306 --user=root --default-character-set=utf8mb4 --table -e "SELECT 'region' f, COUNT(*) total, SUM(d.dict_value IS NULL) orphan FROM ``ry-erp``.bd_vendor v LEFT JOIN ``ry-vue``.sys_dict_data d ON d.dict_type='bd_region' AND d.dict_value=v.region UNION ALL SELECT 'industry', COUNT(*), SUM(d.dict_value IS NULL) FROM ``ry-erp``.bd_vendor v LEFT JOIN ``ry-vue``.sys_dict_data d ON d.dict_type='bd_vendor_industry' AND d.dict_value=v.industry UNION ALL SELECT 'category', COUNT(*), SUM(d.dict_value IS NULL) FROM ``ry-erp``.bd_vendor v LEFT JOIN ``ry-vue``.sys_dict_data d ON d.dict_type='bd_vendor_category' AND d.dict_value=v.category UNION ALL SELECT 'status', COUNT(*), SUM(d.dict_value IS NULL) FROM ``ry-erp``.bd_vendor v LEFT JOIN ``ry-vue``.sys_dict_data d ON d.dict_type='bd_vendor_status' AND d.dict_value=v.status UNION ALL SELECT 'invoice', COUNT(*), SUM(d.dict_value IS NULL) FROM ``ry-erp``.bd_vendor v LEFT JOIN ``ry-vue``.sys_dict_data d ON d.dict_type='pu_invoice_type' AND d.dict_value=v.default_invoice_type;"
```

Expected：5 行，`total` 全为 12，`orphan` 全为 **0**。

- [ ] **Step 3: 端到端验收 — 供货品类与物料分类可 join**

```powershell
& 'D:\software\mysql-5.7.14\bin\mysql.exe' --host=localhost --port=3306 --user=root --default-character-set=utf8mb4 --table -e "SELECT v.vendor_code, v.vendor_name, v.industry, c.category_name FROM ``ry-erp``.bd_vendor v LEFT JOIN ``ry-erp``.bd_material_category c ON c.category_code = v.industry GROUP BY v.vendor_code, v.vendor_name, v.industry, c.category_name ORDER BY v.vendor_code;"
```

Expected：12 行，`category_name` 全部非空（`METAL`→金属材料、`SM_CAST`→铸锻件等），证明字典值与物料分类对齐。

- [ ] **Step 4: 端到端验收 — 业务阻断解除**

```powershell
& 'D:\software\mysql-5.7.14\bin\mysql.exe' --host=localhost --port=3306 --user=root --default-character-set=utf8mb4 --table -e "SELECT COUNT(*) AS non_active FROM ``ry-erp``.bd_vendor WHERE status <> 'ACTIVE' OR status IS NULL;"
```

Expected：`0`（修复前为 10）。这直接对应「10 家供应商无法下采购订单」的解除。

- [ ] **Step 5: 端到端验收 — 前端构建通过**

```powershell
cd plus-ui; npm run type-check 2>&1 | Select-Object -Last 10
```

Expected：无新增报错。

- [ ] **Step 6: 手工界面验收（需已登录的浏览器会话）**

在 `http://127.0.0.1:80`（或当前前端入口）执行：

1. 打开「ERP 系统 → 业务伙伴 → 供应商管理」→ 列表「供货品类」「地区」「合作状态」三列均显示中文标签（不再空白）。
2. 搜索区选「合作状态 = 合作中」→ 查询 → 返回 **12** 条（修复前该筛选无效，恒返回全部；若数据只有 12 条需先临时把某一行改为 `FROZEN` 验证筛选真的生效，验证后改回 `ACTIVE`）。
3. 打开 V00003 编辑 → 「分类信息」Tab：供货品类=塑料材料、地区=华东（灰显不可改）、合作状态=合作中；「基本信息」Tab 的省/市/区已回显为 浙江省/杭州市/余杭区。
4. 把省份从「浙江省」改为「广东省」→ 地区自动变为「华南」→ 取消退出（不保存）。
5. 回归：打开「客户管理」与「仓库管理」新增页 → 省/市/区级联行为与改动前一致（无「地区」字段出现）。

- [ ] **Step 7: 提交**

```powershell
git add doc/system/数据库增量变更记录.md doc/erp/specs/2026-09-23-bd-vendor-dict-align-impl-spec.md doc/erp/plans/2026-09-23-bd-vendor-dict-align-plan.md
git commit -m "docs(erp-bd): 登记供应商字典口径对齐变更 + 落档规格与实施计划"
```

---

## 自查记录

**1. 规格覆盖**

| 规格章节 | 对应任务 |
|---|---|
| §3 新增 `bd_vendor_industry` 字典 | Task 1 §1、Task 2 |
| §4 12 行数据修复映射 + 行政区划回填 | Task 1 §3、Task 3 |
| §5.1 供应商页改字典 / 默认值 / 地区只读 | Task 6 |
| §5.2 `useAreaCascade` 大区派生 | Task 5 |
| §6 后端 `status` 筛选 | Task 4 |
| §7 生成器 `catalog.py` + `generate.py` | Task 7 |
| §8 交付物（含变更记录、规格落档） | Task 8 |
| §9 验收标准 1~6 | Task 8 Step 2~6 |
| §11 回滚 | Task 1 §4（脚本末尾回滚段） |
| 列注释更新（规格 §1 头部例外条款） | Task 1 §2、Task 3 Step 1 |

无遗漏。

**2. 占位符扫描**：无 TBD/TODO；每个代码步骤都给出了完整可粘贴内容。

**3. 类型/命名一致性**：`regionField`（Task 5 定义 → Task 6 使用）、`regionOfProvince`（Task 5 导出）、`bd_vendor_industry`（Task 1/2 建 → Task 6 用）、`_bd_vendor_map`（Task 1 内建内销）、`BdVendorServiceImplTest`（Task 4 单一类名）在各任务间一致。

**4. 已识别的执行风险**

- Task 1 Step 6 的 Redis 清缓存命令假设 Redis 以容器名 `redis` 运行；若不符需按实际部署调整，无法执行时以重启应用替代（字典走 Spring Cache，重启即失效）。
- Task 8 Step 6 需要已登录的浏览器会话，属人工验收步骤；自动化只能覆盖到 Step 2~5。
- Task 7 只做「干跑 + 断言」，不重新生成 `ry-erp.sql` —— 该生成器覆盖整库 250+ 物料等全部主数据，重新生成会覆盖人工调整过的内容，故仅修正生成逻辑并验证其输出片段。
