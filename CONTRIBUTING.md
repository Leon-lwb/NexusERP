# 参与 Nexus-ERP 开发

本仓库是基于 RuoYi-Vue-Plus v5.6.0 的制造业 ERP/MES/IoT/AI 后端与 PC 前端。移动端在独立 Nexus-App 仓库；跨仓库改动请分别提交并说明对应 SHA。

## 本地准备

- Java 17、Maven、Node >= 20.19.0；按根 `pom.xml` 与 `plus-ui/package.json` 的版本/锁文件安装。不要提交本地密钥、导出数据或 `logs/`。
- 阅读 [AGENTS.md](AGENTS.md)、[实施计划](doc/ai/plans/2026-09-23-工业交付风险闭环决策实施计划.md) 与 [数据库变更记录](doc/system/数据库增量变更记录.md)。数据库初始化 SQL 含 `DROP TABLE`；**绝不可在已部署/plus 库重新导入**。新业务变更先修改相应主 SQL，再记录变更；部署到现有库需另编写经审核、可回滚的增量 DDL。
- 本地双数据源、Redis、RabbitMQ、鉴权加密和多租户必须与运行环境一致；在隔离库验证，不向演示账号写真实客户数据。

## 分支、提交与审查

1. 从合适的基线建立功能分支；本竞赛功能使用 `codex/industrial-delivery-decision`，App 使用 `codex/industrial-delivery-decision-app`，不要直接向 `plus` 推送。
2. 按领域拆分变更，先加失败测试再实现。PR/评审说明应列出影响的租户/权限、ERP 与系统库边界、是否涉及 MQ 幂等、回滚与数据库迁移路径。
3. 仅暂存任务文件，运行 `git diff --cached --check`；提交信息使用 `feat(erp): ...`、`fix(ai): ...`、`test(ai): ...` 等具名范围。仓库合并/推送需维护者明确确认。

## 必跑检查（按改动范围）

```powershell
mvn -pl ruoyi-common/ruoyi-common-ai,ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-ai,ruoyi-modules/ruoyi-flow -am test -DskipTests=false
mvn -pl ruoyi-admin -am clean package -DskipTests
Set-Location plus-ui
npm run test:unit
npm run type-check
npm run lint:eslint
npm run build:prod
```

公共模块的 JUnit 测试请标 `@Tag("dev")`（根 POM 的 Surefire 按 `${profiles.active}` 筛选）；务必检查**实际执行数量**，不能用 `BUILD SUCCESS` 证明 0 项测试通过。App 在独立仓库执行 `npm test`、`npm run lint`、`npm run format:check`。PC/App 全量 lint 若因既有文件报错，记录具体文件和退出码，不要擅自格式化所有第三方文件或将失败改称通过。

## 安全与可复现性

- 决策动作只使用可信 ERP 快照、数值证据与人工审批；历史排程不能用假设 APS 利用率冒充。高风险/缺证据一律失败关闭。雪花 ID 序列化为字符串，异步线程显式传递租户。
- Benchmark seed 只允许独立 schema `nexus_ai_decision_demo_erp`；真实通过率/时延仅由隔离环境运行报告给出，不填预测值。参见 [Benchmark 使用说明](doc/ai/benchmark/delivery-risk/README.md)。
- 安全漏洞请遵循 [SECURITY.md](SECURITY.md)，避免在公开 Issue 贴令牌或复现脚本。