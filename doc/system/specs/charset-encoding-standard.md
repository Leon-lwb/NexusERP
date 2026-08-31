# Nexus-ERP 数据库字符集与编码规范

> 适用范围：ry-vue / ry-erp / ry-mes / ry-iot 四个 MySQL 库
> 生效日期：2026-07-10
> 配套迁移脚本：`script/sql/update/2026-07-10-charset-utf8mb4-unicode-ci-standardization.sql`

---

## 1. 标准

| 项目 | 取值 |
|------|------|
| **字符集 (CHARACTER SET)** | `utf8mb4` |
| **排序规则 (COLLATE)** | `utf8mb4_unicode_ci` |
| **存储引擎** | `InnoDB` |
| **行格式** | `Dynamic` |

**统一标准：`utf8mb4` + `utf8mb4_unicode_ci`。** 四个库的库默认、所有表、所有字符列均应遵循此标准。

### 为什么是这一组

- **`utf8mb4` 而非 `utf8`**：MySQL 的 `utf8` 是 3 字节截断版，无法存储 4 字节字符（emoji、部分 CJK 扩展汉字、某些符号）。`utf8mb4` 才是真正的 UTF-8。历史导出中 ry-vue 的 `sys_*` 等表仍是 3 字节 `utf8`，本规范统一升级。
- **`utf8mb4_unicode_ci` 而非 `utf8mb4_general_ci`**：`unicode_ci` 基于 Unicode 标准排序算法，对重音、大小写、德语 ß 等处理正确；`general_ci` 是旧的、更快但不准确的方案。统一用 `unicode_ci` 可消除「排序/比较行为不一致」与跨表 JOIN 的 `Illegal mix of collations` 报错。
- **不选 MySQL 8.0 的 `utf8mb4_0900_ai_ci`**：导出源为 5.7，`unicode_ci` 在 5.7/8.0 行为一致，避免跨版本排序差异；且与连接层已声明的 `connectionCollation` 对齐。
- **`InnoDB` + `Dynamic`**：支持事务、外键、3072 字节索引前缀；Dynamic 行格式将长字段溢出页外存储，避免行长度溢出。

---

## 2. 强制例外（保留原样，不得转换）

| 表前缀 | 所属 | 原字符集/排序规则 | 原因 |
|--------|------|-------------------|------|
| `act_*` | Flowable BPMN 引擎 | `utf8` / `utf8_bin` | Flowable 引擎 DDL 硬编码，改动破坏流程引擎 |
| `flw_*` | Flowable 7 事件/批处理引擎 | `utf8` / `utf8_bin` | 同上，Flowable 7 新增表 |

> 这两组表由 Flowable 引擎自行管理，升级 Flowable 版本时由其 DDL 维护。**任何字符集统一脚本都必须排除 `act_%` 与 `flw_%`。**

---

## 3. 连接层规范（JDBC）

四个数据源（master=ry-vue / erp / mes / iot）的 JDBC URL 应统一包含：

```
useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci
```

- `connectionCollation=utf8mb4_unicode_ci`：连接握手时强制协商到 `utf8mb4` + `unicode_ci`，是消除 collation 混用的根本。
- `characterEncoding=UTF-8`：Java 侧字符编码（`UTF-8` 是 Java 标准名，等价于 MySQL 的 `utf8mb4` 全 Unicode）。
  - 当前 `application-dev.yml` 中为 `characterEncoding=utf8`，功能上亦可（Java 容忍 `utf8` 作为 `UTF-8` 别名，配合 `connectionCollation` 实际仍走 utf8mb4），但建议统一改为显式的 `UTF-8` 以消除歧义。
- `serverTimezone`、`useSSL`、`rewriteBatchedStatements` 等其他参数维持现状。

> 连接层字符集必须与库/表字符集一致，否则会出现写入乱码或 `?` 占位符。

---

## 4. 建表 DDL 规范

### 4.1 新建库

```sql
CREATE DATABASE `ry-xxx`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
```

### 4.2 新建表（标准模板）

```sql
CREATE TABLE `bd_xxx` (
    `xxx_id`      bigint        NOT NULL                  COMMENT '主键ID',
    `tenant_id`   varchar(20)   NOT NULL DEFAULT '000000' COMMENT '租户ID',
    `xxx_code`    varchar(64)   NOT NULL                  COMMENT '编码',
    `xxx_name`    varchar(200)  NOT NULL                  COMMENT '名称',
    `remark`      varchar(500)  NULL DEFAULT NULL         COMMENT '备注',
    `create_dept` bigint        NULL DEFAULT NULL,
    `create_by`   bigint        NULL DEFAULT NULL,
    `create_time` datetime      NULL DEFAULT NULL,
    `update_by`   bigint        NULL DEFAULT NULL,
    `update_time` datetime      NULL DEFAULT NULL,
    PRIMARY KEY (`xxx_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = Dynamic
  COMMENT = '业务表';
```

**要点**：
- 表级必须显式声明 `DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci`，不依赖库默认值（防库默认被改后表跟随漂移）。
- 字符列无需逐列声明 `CHARACTER SET`，继承表级即可；仅在需要区分大小写时才用 `COLLATE utf8mb4_bin`（须在代码评审中说明理由）。
- 多租户列 `tenant_id` 统一为 `varchar(20) DEFAULT '000000'`（见 §5）。
- 索引列 `varchar` 长度 ≤ 768（`768×4=3072` 字节，达 InnoDB 索引前缀上限）；超过则改用前缀索引或拆分。

### 4.3 新增列 / 修改列

```sql
ALTER TABLE `bd_xxx`
    ADD COLUMN `new_field` varchar(100) NULL DEFAULT NULL COMMENT '新字段';
-- 列继承表级 utf8mb4_unicode_ci，无需显式指定
```

---

## 5. 多租户列规范

`tenant_id` 是多租户隔离的核心列，全库统一：

```sql
`tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT '000000' COMMENT '租户ID'
```

- **类型必须为 `varchar(20)`**，不得为 `bigint`。租户拦截器 `PlusTenantLineHandler` 注入的是字符串 `'000000'`，若列为 `bigint` 会被截断落库为 `0`，导致租户数据隔离失效。（已有历史踩坑，见 `script/sql/update/2026-07-10-wm-production-recpt-tenant-varchar.sql`。）
- 默认值 `'000000'`（6 位租户编码，`000000` 为默认租户）。

---

## 6. 现状与迁移

### 6.1 迁移前现状（基于 `script/sql/` 导出文件统计）

| 库 | `utf8mb4_unicode_ci` | `utf8mb4_general_ci` | `utf8/utf8_general_ci` | `utf8/utf8_bin` | 表总数 |
|----|----|----|----|----|----|
| ry-vue | 28 | 59 | 56（sys_* 等） | 41（act_/flw_） | 184 |
| ry-erp | 75 | 17 | 0 | 0 | 92 |
| ry-mes | 4 | 16 | 0 | 0 | 20 |
| ry-iot | 3 | 0 | 0 | 0 | 3 |

主要问题：
- **ry-mes** 几乎全库为 `utf8mb4_general_ci`，与连接层声明的 `utf8mb4_unicode_ci` 不一致，跨表 JOIN 易触发 `Illegal mix of collations`。
- **ry-vue** 97 张表仍是 3 字节 `utf8`（56 张 `utf8_general_ci`，含 sys_ 系统表等；+ 41 张 `utf8_bin`，为 Flowable `act_/flw_` 引擎表），且 utf8mb4 表分裂为 general_ci / unicode_ci 两派。
- **ry-erp** 17 张 `pu_/so_/wm_` 表为 general_ci。
- **ry-iot** 已完全符合标准。

### 6.2 迁移方式

执行 `script/sql/update/2026-07-10-charset-utf8mb4-unicode-ci-standardization.sql`：

1. **第 0 部分**：预检查询，确认待转换表清单（应排除 `act_*/flw_*`）。
2. **第 1 部分**：`ALTER DATABASE` 修改四库默认字符集。
3. **第 2 部分**：存储过程动态 `CONVERT TO`，转换所有非 `act_*/flw_*` 表的表级与列级字符集；结果写入 `_charset_migration_log` 日志表。
4. **第 3 部分**：查看迁移结果（OK/FAIL 汇总）。
5. **第 4 部分**：核验查询，确认除 `act_*/flw_*` 外全部为 `utf8mb4_unicode_ci`。

### 6.3 迁移前置条件

- **完整备份**四个库（`mysqldump`），字符集升级不可逆回滚。
- **低峰期执行**：`CONVERT TO` 会重建表与索引、加表锁，大表（如 `sys_oper_log`）耗时较长。
- 已通过安全核查：act_/flw_ 之外无 `_bin` 列、无「被索引且长度 ≥ 769」的 varchar 列，`CONVERT TO` 不会抹掉刻意的大小写敏感列，也不会触发索引长度报错。

---

## 7. 常见陷阱

### 7.1 `Illegal mix of collations` 报错
**现象**：跨表 JOIN 或 WHERE 比较字符串列时报 `Illegal mix of collations (utf8mb4_unicode_ci) and (utf8mb4_general_ci)`。
**根因**：参与 JOIN/比较的列排序规则不一致（典型为 ry-mes 的 general_ci 表与 ry-erp 的 unicode_ci 表关联）。
**解决**：统一到 `utf8mb4_unicode_ci`（本规范）。临时规避可在 SQL 中用 `COLLATE utf8mb4_unicode_ci` 强制转换，但治本应统一列排序规则。

### 7.2 `utf8` vs `utf8mb4` 混用
**现象**：存 emoji 或 4 字节汉字时报 `Incorrect string value: '\xF0\x9F...'`，或存入后变 `?`。
**根因**：表/列为 3 字节 `utf8`。
**解决**：升级到 `utf8mb4`。ry-vue 的 `sys_*` 表历史上是 `utf8`，迁移后统一为 utf8mb4。

### 7.3 索引列过长
**现象**：`CONVERT TO` 或建索引时报 `Specified key was too long; max key length is 3072 bytes`。
**根因**：utf8mb4 下每个字符占 4 字节，索引列 `varchar(N)` 需 `4N ≤ 3072`，即 `N ≤ 768`。
**解决**：索引列 varchar ≤ 768；超长字段不要整列建索引，改用前缀索引 `KEY(col(255))` 或拆分。

### 7.4 连接字符集与库不一致
**现象**：写入中文乱码或变 `?`。
**根因**：JDBC URL 未设 `connectionCollation`，或 `characterEncoding` 与库字符集不符。
**解决**：URL 统一带 `connectionCollation=utf8mb4_unicode_ci&characterEncoding=UTF-8`。

### 7.5 Flowable 表误改
**现象**：改了 `act_*`/`flw_*` 表字符集后，流程引擎启动/部署报错。
**根因**：Flowable DDL 硬编码 `utf8/utf8_bin`，改动破坏引擎。
**解决**：永远排除 `act_%`、`flw_%`，交由 Flowable 引擎自管。

### 7.6 Navicat 导出脚本中的 `SET NAMES`
导出文件头部 `SET NAMES utf8mb4;` 仅设置会话字符集，不改变表结构。以表/列的实际 `CHARACTER SET`/`COLLATE` 声明为准。
