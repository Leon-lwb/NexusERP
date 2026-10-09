# Nexus-ERP 仓库元数据与增量 SQL 清理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按用户 2026-10-08 确认的范围完成仓库清理，并避免删除后出现失效测试与默认迁移入口。

**Architecture:** 在用户指定工作区内修改，不切换分支、不自动提交或推送。维护全新安装 SQL、现有业务行为及其他任务的源码；历史迁移从工作树移除后，仅由 Git 历史追溯，不能用主初始化脚本覆盖存量库。

**Tech Stack:** Git、PowerShell、YAML、Markdown、Python unittest、Java/JUnit/Maven。

## 已确认范围与现场

- 分支 `plus`；起始提交 `005560ce`；已有其他任务未提交文件，不作通用清理。
- 用户在审查后明确授权直接删除增量 SQL；这次授权替代此前建议保留增量的处理方式，不代表任何环境已经迁移。
- 删除范围为 `script/sql/update/` 下的增量 SQL，包括审查时已存在的 80 份受跟踪文件及用户已知的 1 份未提交增量。其他任务的文档、Python、前端文件保留。
- `.github/` 按仅维护 Gitee 的选择删除；`.run/` 保留且内容不变。
- `.claude/` 取消 Git 跟踪但不删除本地文件；不输出其内容。
- 只清理确认是下载缓存的 `script/mcp/manufacturing-documents/.npm-cache/`，保留 node_modules、部署脚本、SOP、MCP 及可复用维护工具。

## Task 1: Nexus 化 Gitee 协作模板

**Files:** `.gitee/ISSUE_TEMPLATE/{bug,feature,config}.yml`、`.gitee/PULL_REQUEST_TEMPLATE.zh-CN.md`。

- [x] 将模板及联系入口改为 Nexus-ERP，使用仓库已有文档和维护分支信息，不编造外部地址。
- [x] Bug 表单包含可填写复现、实际/预期结果、模块、提交和脱敏环境信息；PR 说明覆盖权限、租户、迁移、MQ 与非零测试证据。
- [x] 用 PyYAML 解析 3 份 YAML，检查字段 ID 唯一、必填项及过时上游支持链接移除。

## Task 2: 删除授权目录与解除 Claude 跟踪

**Files:** `.github/workflows/*.yml`、`script/sql/update/*.sql`、`.gitignore`、Git 索引中的 `.claude/settings.local.json`。

- [x] 删除前解析绝对路径并核对位于指定工作区；不用跨 shell 删除，不运行数据库或部署命令。
- [x] 删除 `.github/` 和授权增量 SQL；保留主 SQL 和 `.run/`。
- [ ] MCP 下载缓存清理：工具安全策略拒绝递归删除，目录仍保留，不绕过限制。
- [x] 明确目录忽略规则，并用 `git rm --cached -- .claude/settings.local.json` 仅解除跟踪；保留本地文件。
- [x] 核对全部原有未提交文件，除明确授权的增量 SQL 外内容不变。

## Task 3: 维护 SQL 契约测试与退役入口

**Files:** 直接读取已删增量的 Java/Python 契约测试；`doc/ai/full-flow/scripts/{procurement_additive_migration,stock_native_precision_migration}.py` 及相关单测。

- [x] 删除前运行新装 schema 的现有离线基线，记录两项历史逐字比较失败。
- [x] 保留当前 schema/映射/索引/租户/默认值的有效断言；改为检查 `script/sql/ry-*.sql` 的当前安装契约，不以跳过或捕获缺文件掩盖问题。
- [x] 已退役的一次性迁移入口在连接数据库前明确拒绝；保留有价值的纯算法、安全守卫测试，使用临时合成夹具，不恢复真实增量 SQL。
- [x] 执行相关 Python unittest 与指定 Java 契约测试，记录用例数和退出码。

## Task 4: 文档与最终验证

**Files:** `script/README.md`、`doc/system/数据库增量变更记录.md`、`doc/CONTRIBUTING.md`、`AGENTS.md`（仅相关清理政策段落）。

- [x] 标明清理日期、用户授权、历史文件状态和 Git 追溯方式；历史审计报告不批量改写。
- [x] 明确主初始化 SQL 仍只用于全新环境，删除增量不表示生产已部署、旧库可重导或数据可清理。
- [x] 检查受跟踪代码的 SQL 读取引用、`.claude` 无受跟踪文件、`.run` 和主 SQL 未改动。
- [ ] 原有未提交的 `plus-ui/scripts/test-flow-trigger-display.mjs` 仍有 3 项读取已删 SQL 的测试：保留该文件内容，已询问用户是否允许同步调整。
- [x] 完成两轮独立审查（元数据 / 文档与 SQL 契约 / 退役守卫均无 actionable 问题）并记录最终 `git diff --check`；不自动提交/推送。


## 2026-10-08 验证记录与剩余边界

### 已验证

| 检查 | 实际结果 |
| --- | --- |
| 模板、目录、忽略规则和原有文件保护检查（Python unittest + PyYAML） | 10 项通过；19 个受保护文件 SHA-256 均未改变，包括其他任务源码、5 份主 SQL、3 份 `.run` 与本地 Claude 配置。 |
| 六组相关 Python 单测 | 139 项通过，0 失败、0 错误、0 跳过；合并复跑期间 socket 连接被阻断，实际网络调用 0。 |
| Java 定向 Maven（12 个测试类，包含依赖模块，空 `profiles.active`） | 实际 78 项通过，0 失败、0 错误、0 跳过；不是仅依据 BUILD SUCCESS。 |
| 前端 `npm run type-check` | 退出码 0；仅 npm 用户配置兼容性警告。 |
| 两轮独立审查：元数据 / 文档 / 注释，以及 SQL 契约 / 退役守卫 | 均无 actionable 问题；许可证和上游归属保留，业务控制流未改变。 |
| staged / unstaged `git diff --check` | 退出码均为 0。 |

相关 Python 命令（仓库根目录）：

```powershell
python -B -m unittest discover -s script/sql/tests -p test_p1_purchase_ddl_contract.py -v
python -B -m unittest discover -s doc/ai/full-flow/scripts -p test_formal_entry_schema.py -v
python -B -m unittest discover -s doc/ai/full-flow/scripts -p test_inventory_precision_migration.py -v
python -B -m unittest discover -s doc/ai/full-flow/scripts -p test_stock_native_precision_migration.py -v
python -B -m unittest discover -s doc/ai/full-flow/scripts -p test_procurement_additive_migration.py -v
python -B -m unittest discover -s doc/ai/full-flow/scripts -p test_retired_migration_inputs.py -v
```

Java 定向验证命令（仓库根目录）：

```powershell
mvn --batch-mode --no-transfer-progress -o -pl ruoyi-modules/ruoyi-ai,ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-station -am test -DskipTests=false -Dprofiles.active= -Dtest=AiDecisionP1VersionMappingContractTest,AiToolTenantUniqueSchemaTest,ErpCertifiedSourceBirthContractTest,PuCertifiedPurchaseLifecycleSqlContractTest,PuCertifiedPurchaseOrderWorkflowMapperTest,PuCertifiedPurchaseOrderWorkflowPresenceTest,PuCertifiedPurchaseWriteSqlContractTest,PuRequirementP1MappingContractTest,WmCertifiedArrivalSchemaTest,WmReceiptIqcEvidenceMapperTest,QcReceiptIqcBirthMapperTest,StationPassTransactionTest -Dsurefire.failIfNoSpecifiedTests=false
```

### 尚未完成 / 未验证

- `script/mcp/manufacturing-documents/.npm-cache/` 约 3.2 MiB，递归删除被工具安全策略拒绝，实际仍存在；不声称缓存已清理。
- 原有未提交的 `plus-ui/scripts/test-flow-trigger-display.mjs` 保持原样。执行 `node --test scripts/test-flow-trigger-display.mjs`（在 `plus-ui/`）实际 11 项中 8 通过、3 失败；这 3 项失败是本次按授权删除工业流程名称 SQL 后产生，不是清理前已存在的失败。已向用户询问是否允许调整这份既有未提交文件，未以跳过测试或恢复增量来隐藏问题。
- 清理前另有两项 Python 历史逐字比较断言失败，已随历史迁移专属检查退役而替换为当前主 schema 的有效契约；不是通过改写主 SQL 或跳过用例解决。
- 未执行 SQL、未连接既有数据库、未启停任何用户服务、未验证 Gitee 在线表单与生产部署；Java 涉及数据库的测试仅使用独立 H2 内存夹具。
- 未自动提交或推送。`git rm` 产生的删除与 `.claude` 取消跟踪在索引中，其他修改 / 新增文件尚未暂存。
