# NexusERP 文档贡献规范

本规范适用于 `doc/` 下所有文档的创建与维护，旨在避免文档漂移、路径失效、命名混乱等问题。

## 1. 文档命名规范

按文档性质分两类命名：

| 类别 | 格式 | 示例 |
|------|------|------|
| 时间相关（计划/方案/分析/报告/变更/变更日志） | `YYYY-MM-DD-<模块>-<描述>.md` | `2026-07-28-mes-p0-4-ncr-rework-implementation-plan.md` |
| 常青参考（模块总文档/设计规范/指南/手册） | `NexusERP_<描述>.md` | `NexusERP_MES文档.md`、`NexusERP_IoT学习指南.md` |

- `<模块>` 取值：ai / erp / iot / mes / im / system
- 日期前缀用文档首次提交日期（`git log --diff-filter=A --date=short -- <file>`）
- 计划/报告类放各模块 `plans/`，设计规范放 `specs/`，指南放 `guides/`
- 已完成或废弃的文档移入对应 `plans/archive/` 或 `specs/archive/`，文件名保留
- `report`/`verification-report` 后缀用于区分事后报告

## 2. 文档元信息模板

每个文档头部建议包含以下信息块（erp 的 BOM 变更文档为范本）：

```markdown
> 文档编号: NexusERP-XXX-2026-NNN
> 版本: v1.0
> 状态: 草稿 / 待审 / 已发布 / 已废弃
> 创建日期: 2026-06-26
> 最后更新: 2026-06-26
> 作者: <name>
> 关联文档: <相对路径>
```

状态流转：草稿 → 待审 → 已发布；废弃需保留文件并在顶部标注"已废弃，替代文档：xxx"。

## 3. 状态标记规范

文档内任务/章节状态统一使用以下标记，禁止混用"待开始/已完成/⬜/✅"等多种方式：

| 标记 | 含义 |
|------|------|
| ✅ | 已完成 |
| 🚧 | 进行中 |
| ⬜ | 待开始 |
| ❌ | 阻塞 |
| 🗑️ | 已废弃 |

## 4. 路径引用规范

- **禁止使用绝对路径**：不得出现 `C:\`、`E:\`、`D:\` 等盘符路径
- **统一使用相对路径**：同目录用 `./file.md`，父目录用 `../`，子目录用 `./subdir/`
- **文件迁移后必须同步更新引用**：若移动文件，需全局搜索并更新所有引用该文件的位置
- **跨模块引用**：从 `doc/ai/` 引用 `doc/mes/` 用 `../mes/NexusERP_MES文档.md`

历史文档中的绝对路径（如 `E:\Nexus\nexus-erp`、`C:\Users\MyPC\.claude\plans\`）需逐步替换为相对路径。

## 5. SQL 脚本规范

> **重要变更（2026-07-29）**：开发期增量 SQL 不再以独立文件维护。DDL/菜单/字典直接合并进权威 schema 文件；发布后的增量迁移走 `script/sql/update/`。历史开发期脚本已归档至 `doc/<模块>/sql/archive/`，仅供追溯，不再执行。

### 5.1 权威 schema 位置
| 文件 | 内容 |
|------|------|
| `script/sql/ry-vue.sql` | 系统库（ai_*、im_*、sys_menu、sys_dict_*、sj_job 等） |
| `script/sql/ry-erp.sql` | ERP 业务表（bd_/so_/pu_/prd_/wm_/sch_） |
| `script/sql/ry-mes.sql` | MES 业务表（pro_/dc_/eq_/qc_/spc_/lt_/mes_） |
| `script/sql/ry-iot.sql` | IoT 业务表（iot_*） |
| `script/sql/app.sql` | 移动端 App 表 |
| `script/sql/update/` | 发布后增量迁移：`YYYY-MM-DD-<模块>-<描述>.sql` |

新增表/列/菜单/字典时，直接编辑对应 `ry-*.sql`；如属发布后修复或增强，另加 `script/sql/update/YYYY-MM-DD-<模块>-<描述>.sql`。

### 5.2 幂等性
`update/` 下脚本必须支持重复执行不报错，推荐：
- 建表用 `CREATE TABLE IF NOT EXISTS`
- 加列用存储过程 + `information_schema` 检查（见 [erp-production-issue.sql](./erp/sql/archive/erp-production-issue.sql) 范本）
- 插入字典用 `INSERT ... WHERE NOT EXISTS`

### 5.3 文件头注释
`update/` 脚本必须包含：
```sql
-- =====================================================
-- 脚本名称: 2026-07-23-iqc-shelf-gate-schema.sql
-- 用途: IQC 货架期门禁字段
-- 适用数据库: ry-erp
-- 特性: 幂等可重复执行
-- 创建日期: 2026-07-23
-- 作者: <name>
-- =====================================================
```

### 5.4 历史脚本
`doc/<模块>/sql/archive/` 下的开发期脚本（如 `mes-week28-09-mbom.sql`）已全部执行并并入上述权威文件，仅作历史追溯，不要再次执行。演示/测试数据脚本在 `doc/erp/sql/test-data/`。

## 6. 内容维护原则

### 6.1 单一数据源
- 同一信息只在一处维护，其他位置用链接引用
- DDL 以 SQL 脚本为权威来源，主文档正文 DDL 仅作说明，并标注"以 sql/ 下脚本为准"
- 实施进度统一在主文档"实施进度更新"章节维护，其他文档引用该章节

### 6.2 代码与文档同步
- SQL 变更合并时，同步更新对应主文档的 DDL 章节
- 菜单 ID 段变更时，同步更新 plan 文档
- 字段重命名时，全局搜索旧名并更新

### 6.3 避免内容重复
- 主文档与子文档（QuickStart、周计划）内容重叠时，子文档用"详见 [主文档章节](链接)"代替重复
- 跨周问题跟踪：审查发现的问题在独立跟踪表或 issue 中闭环，周文档仅记录本周处理结果

## 7. 目录结构约定

doc/
├── README.md              # 本顶层索引
├── CONTRIBUTING.md        # 本规范
├── ai/
│   ├── plans/             # 活跃计划（主计划、二阶段计划）
│   │   └── archive/       # 已归档周计划 / QuickStart / S2A-D
│   ├── knowledge-base/    # RAG 知识源（11 主题，.txt + .annotated.txt）
│   └── sql/archive/       # 历史增量 SQL（已并入 ry-vue.sql）
├── erp/
│   ├── *.md               # ERP 综合文档、业务流转分析、编码规则
│   ├── plans/             # 实施计划
│   │   └── archive/
│   ├── specs/             # 设计规范
│   │   └── archive/
│   ├── archive/           # 已归档变更说明
│   └── sql/
│       ├── archive/       # 历史增量 SQL（18 个，已并入 ry-erp/ry-vue.sql）
│       └── test-data/     # 演示/测试数据 SQL（3 个）
├── iot/
│   ├── *.md               # IoT 文档、学习指南、验收报告
│   ├── plans/archive/
│   └── sql/archive/       # 历史增量 SQL（已并入 ry-iot/ry-vue.sql）
├── mes/
│   ├── *.md               # MES 主文档、设备管理方案
│   ├── plans/             # 总规划、P0 开发计划 v1.0、P0-9 计划
│   │   └── archive/       # Week17-28 报告 + P0-2/4/5/7/8/9 实施计划
│   └── sql/archive/       # 历史增量 SQL（33 个，已并入 ry-mes/ry-vue.sql）
└── system/
    ├── deployment/        # 部署文档与 docker 配置
    ├── marketing/         # 公众号文章
    ├── plans/archive/     # 已归档计划与分析报告
    ├── screenshots/       # 系统截图（36 张）
    ├── specs/             # 编码规范
    └── user-manual/       # 用户操作手册
```
