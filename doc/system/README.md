# 系统级文档

> 导航核对：2026-09-28。审查报告仅反映其落款时间的状态；不要将历史报告当作今日待办清单。

## 当前入口

2026-10-19物料身份与追溯：[接口和事件契约](guides/material-trace-api.md)、[历史对账与修复](guides/material-trace-repair.md)、[库存身份转换](guides/material-trace-stock-conversion.md)、[分库结构](guides/material-trace-schema.md)、[实施检查点](plans/2026-10-19-material-trace-integration-status.md)、[最终验证](plans/2026-10-19-material-trace-verification.md)、[现场验收表](guides/material-trace-rollout-checklist.md)。代码与隔离验证不代表现有库迁移或现场验收完成。

2026-10-15报表扩展：[E2–E7实现计划](plans/2026-10-14-system-report-e2-e7-implementation-plan.md)、[E2–E7验证报告](plans/2026-10-15-system-report-e2-e7-verification-report.md)、[使用指南](guides/NexusERP_自研报表模块使用指南.md)。扩展DDL尚未在现有库执行。

| 文档                                                                                                                               | 定位                                           | 边界                                                                                                 |
| ---------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| [2026-09-06-全项目审查报告.md](./2026-09-06-全项目审查报告.md)                                                                     | 最近一次全项目复核                             | 以 2026-09-06 代码为准；原 08-16 基线报告在当前仓库与可访问 Git 历史中均未找到，正文已有缺失来源说明 |
| [../audit/README.md](../audit/README.md)                                                                                           | 09-20/21 ERP/MES/IoT 专项审计、交接、优化计划  | 是后续专项补充，不等于重新做了全项目审查                                                             |
| [数据库增量变更记录.md](./数据库增量变更记录.md)                                                                                   | 2026-07~09 数据库变更登记                      | 全新安装主脚本与存量库一次性迁移分别核对                                                             |
| [2026-09-22-行政区划模块优化计划.md](./2026-09-22-行政区划模块优化计划.md)                                                         | 行政区划实施过程和前置缺陷                     | 开头“未修”是修复前基线；09-22~23 已有相关提交，仍需按环境验收                                        |
| [specs/2026-10-13-system-reporting-design.md](./specs/2026-10-13-system-reporting-design.md)                                       | 自研报表模块架构与建设设计                     | 2026-10-14 用户批准；首期实施见下方执行记录                                                          |
| [specs/2026-10-13-system-reporting-research.md](./specs/2026-10-13-system-reporting-research.md)                                   | 现有积木集成、AJ-Report 源码与官方开源资料调研 | 只读分析；不代表升级故障已复现                                                                       |
| [plans/2026-10-13-system-reporting-construction-plan.md](./plans/2026-10-13-system-reporting-construction-plan.md)                 | 分阶段工作包、文件落点与测试矩阵               | 历史首版；以 2026-10-14 执行计划为准                                                                 |
| [plans/2026-10-14-system-report-p0-implementation-plan.md](./plans/2026-10-14-system-report-p0-implementation-plan.md)             | 自研报表 P0 实施计划                           | 用户批准的 TABLE/DOCUMENT 首期范围                                                                   |
| [plans/2026-10-14-system-report-p0-verification-report.md](./plans/2026-10-14-system-report-p0-verification-report.md)             | 自研报表实施与验证结果                         | 本机测试与运行证据；不代表生产部署                                                                   |
| [plans/2026-10-14-system-report-p0-continuation-verification.md](./plans/2026-10-14-system-report-p0-continuation-verification.md) | 自研报表首期续开发验证                         | 不可变排版/字体与持续存储恢复                                                                        |
| [plans/2026-10-14-system-report-extension-plan.md](./plans/2026-10-14-system-report-extension-plan.md)                             | 自研报表分阶段扩展计划                         | E0/E1代码与本机验证已完成，E2+待审                                                                   |
| [E0/E1 实施记录](./plans/2026-10-14-system-report-e0-e1-implementation-plan.md)                                                    | 自研报表版本、设计、复用、业务接入与任务扩展   | 完成代码与本机验证                                                                                   |
| [HTTP修复与本地基础报表初始化记录](./plans/2026-10-15-report-http-runtime-fixes-verification.md) | 导出兼容、运行条件校验及基础模板初始化 | 本机ry-vue租户000000为14个数据集、17份基础报表；非生产验收 |
| [报表中文注释与菜单图标记录](./plans/2026-10-16-report-schema-comments-icons-verification.md) | 表及字段中文说明、报表菜单SVG | 本地备份原位补齐；安装与增量结构同步，合并plus |
| [E0/E1 验证报告](./plans/2026-10-14-system-report-e0-e1-verification-report.md)                                                    | 实际测试、集成、基准、迁移及未验证范围         | 2026-10-14，本机合成证据                                                                             |
| [guides/NexusERP\_自研报表模块使用指南.md](./guides/NexusERP_自研报表模块使用指南.md)                                              | 报表用户操作、配置与扩展入口                   | 首期支持边界及后续扩展说明                                                                           |
| [specs/2026-09-15-esignature-impl-spec.md](./specs/2026-09-15-esignature-impl-spec.md)                                             | 电子签章 P1 设计与实施差异                     | Task 1–16 已实施，人工端到端验收未完成                                                               |
| [plans/2026-09-15-esignature-p1-implementation-plan.md](./plans/2026-09-15-esignature-p1-implementation-plan.md)                   | 电子签章分任务执行记录                         | Task 17 待人工验收，暂不归档为“全部完成”                                                             |
| [2026-08-19-Nexus客户端生态规划.md](./2026-08-19-Nexus客户端生态规划.md)                                                           | 客户端生态规划                                 | 规划不等于已交付                                                                                     |
| [NexusERP_plus-ui前端优化方案.md](./NexusERP_plus-ui前端优化方案.md)                                                               | 前端优化分阶段方案                             | 以文中标注的执行阶段及新提交核对                                                                     |
| [workflow-regression-checklist.md](./workflow-regression-checklist.md)                                                             | 发版前工作流手工回归清单                       | 检查清单不等于执行证据                                                                               |

其他历史审查、重建/清理执行报告、品牌方案保留原目录供溯源；完成的开发计划见 [plans/archive/](./plans/archive/)；本次处理范围、验证和遗留事项见 [2026-09-28 文档整理记录](./plans/archive/2026-09-28-document-curation-plan.md)；规范见 [specs/charset-encoding-standard.md](./specs/charset-encoding-standard.md)。

## 部署与资源

- [部署操作步骤](./deployment/docker部署文档.md) / [新服务器部署指南](./deployment/新服务器部署指南.md)：执行前核对实际服务器、密钥、数据库版本与运行环境；旧命令不可直接复制到生产。
- **唯一编排来源**：[仓库根目录主服务器 Compose](../../docker-compose-main.yml) / [仓库根目录辅助服务器 Compose](../../docker-compose-assist.yml)。原本目录下的主服务器副本缺少根目录现有的安全环境变量，已撤掉以防误部署。仓库编排目前使用 `mysql:5.7`；不要凭旧介绍将其擅自升级到 8.0。
- [Redis 配置样例](./deployment/docker/redis/conf/redis.conf) 仅供参考；部署时以实际挂载文件及环境变量为准。
- [营销资料](./marketing/) / [系统截图](./screenshots/) / [用户手册](./user-manual/) 均为展示或历史材料，不作为功能验收依据。
