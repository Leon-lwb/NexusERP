# NexusERP 文档贡献规范

本规范适用于 `doc/` 下所有文档的创建与维护，旨在避免文档漂移、路径失效、命名混乱等问题。

## 1. 文档命名规范

统一格式：`NexusERP_<模块>_<类型>_<标识>.md`

| 字段 | 取值 | 示例 |
|------|------|------|
| 模块 | ai / erp / iot / mes / system | mes |
| 类型 | Plan / Report / Design / Change / Deploy / Guide / Review | Plan |
| 标识 | WeekNN / 2026-NNN / PhaseN | Week28 |

示例：
- `NexusERP_MES_Plan_Week28.md`
- `NexusERP_ERP_Change_2026-002.md`
- `NexusERP_AI_QuickStart_Phase0.md`（历史命名保留）

周计划文件放在各模块 `plans/` 子目录，命名沿用 `YYYY-MM-DD-<模块>-weekNN-plan.md`（mes/iot 现有规范）。`report`/`verification-report` 后缀用于区分事后报告。

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

### 5.1 命名
`<模块>-weekNN-<序号>-<名称>.sql`，如 `mes-week28-09-mbom.sql`、`iot-week33-01-init.sql`。
- 序号 01-09：业务表 DDL
- 序号 10+：菜单/字典/流程配置

### 5.2 幂等性
所有脚本必须支持重复执行不报错，推荐：
- 建表用 `CREATE TABLE IF NOT EXISTS`
- 加列用存储过程 + `information_schema` 检查（见 [erp-production-issue.sql](./erp/sql/erp-production-issue.sql) 范本）
- 插入字典用 `INSERT ... WHERE NOT EXISTS`

### 5.3 文件头注释
必须包含：
```sql
-- =====================================================
-- 脚本名称: mes-week28-09-mbom.sql
-- 用途: MBOM 子系统建表
-- 对应文档: NexusERP-CHG-2026-002（BOM优化-ERP成本融合MES制造BOM）
-- 适用数据库: ry-erp
-- 执行顺序: 依赖 mes-week17-01-init.sql，先于 mes-week28-22-menu.sql
-- 特性: 幂等可重复执行
-- 创建日期: 2026-06-25
-- 作者: <name>
-- =====================================================
```

### 5.4 执行顺序
有依赖的脚本必须在头注释"执行顺序"字段说明。复杂依赖建议在 `sql/README.md` 中绘制执行顺序图。

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

```
doc/
├── README.md              # 本顶层索引
├── CONTRIBUTING.md        # 本规范
├── ai/
│   ├── README.md
│   ├── *.md               # AI 开发计划、周计划、QuickStart
│   ├── plans/             # 周计划（如有）
│   └── sql/               # SQL 脚本（如有）
├── erp/
│   ├── README.md
│   ├── *.md               # ERP 设计、变更说明、业务流转
│   └── sql/
├── iot/
│   ├── README.md
│   ├── *.md               # IoT 设计、验收报告、学习指南
│   ├── plans/
│   └── sql/
├── mes/
│   ├── README.md
│   ├── *.md               # MES 主文档
│   ├── plans/             # Week 17-28 实施计划
│   └── sql/
└── system/
    ├── README.md
    ├── deployment/
    │   └── docker/        # 部署文档与配置
    ├── marketing/         # 公众号文章
    ├── screenshots/       # 系统截图
    └── user-manual/      # 用户操作手册
```
