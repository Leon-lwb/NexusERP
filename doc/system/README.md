# 系统级文档

> 导航核对：2026-09-28。审查报告仅反映其落款时间的状态；不要将历史报告当作今日待办清单。

## 当前入口

| 文档 | 定位 | 边界 |
|---|---|---|
| [2026-09-06-全项目审查报告.md](./2026-09-06-全项目审查报告.md) | 最近一次全项目复核 | 以 2026-09-06 代码为准；原 08-16 基线报告在当前仓库与可访问 Git 历史中均未找到，正文已有缺失来源说明 |
| [../audit/README.md](../audit/README.md) | 09-20/21 ERP/MES/IoT 专项审计、交接、优化计划 | 是后续专项补充，不等于重新做了全项目审查 |
| [数据库增量变更记录.md](./数据库增量变更记录.md) | 2026-07~09 数据库变更登记 | 全新安装主脚本与存量库一次性迁移分别核对 |
| [2026-09-22-行政区划模块优化计划.md](./2026-09-22-行政区划模块优化计划.md) | 行政区划实施过程和前置缺陷 | 开头“未修”是修复前基线；09-22~23 已有相关提交，仍需按环境验收 |
| [specs/2026-09-15-esignature-impl-spec.md](./specs/2026-09-15-esignature-impl-spec.md) | 电子签章 P1 设计与实施差异 | Task 1–16 已实施，人工端到端验收未完成 |
| [plans/2026-09-15-esignature-p1-implementation-plan.md](./plans/2026-09-15-esignature-p1-implementation-plan.md) | 电子签章分任务执行记录 | Task 17 待人工验收，暂不归档为“全部完成” |
| [2026-08-19-Nexus客户端生态规划.md](./2026-08-19-Nexus客户端生态规划.md) | 客户端生态规划 | 规划不等于已交付 |
| [NexusERP_plus-ui前端优化方案.md](./NexusERP_plus-ui前端优化方案.md) | 前端优化分阶段方案 | 以文中标注的执行阶段及新提交核对 |
| [workflow-regression-checklist.md](./workflow-regression-checklist.md) | 发版前工作流手工回归清单 | 检查清单不等于执行证据 |

其他历史审查、重建/清理执行报告、品牌方案保留原目录供溯源；完成的开发计划见 [plans/archive/](./plans/archive/)；本次处理范围、验证和遗留事项见 [2026-09-28 文档整理记录](./plans/archive/2026-09-28-document-curation-plan.md)；规范见 [specs/charset-encoding-standard.md](./specs/charset-encoding-standard.md)。

## 部署与资源

- [部署操作步骤](./deployment/docker部署文档.md) / [新服务器部署指南](./deployment/新服务器部署指南.md)：执行前核对实际服务器、密钥、数据库版本与运行环境；旧命令不可直接复制到生产。
- **唯一编排来源**：[仓库根目录主服务器 Compose](../../docker-compose-main.yml) / [仓库根目录辅助服务器 Compose](../../docker-compose-assist.yml)。原本目录下的主服务器副本缺少根目录现有的安全环境变量，已撤掉以防误部署。仓库编排目前使用 `mysql:5.7`；不要凭旧介绍将其擅自升级到 8.0。
- [Redis 配置样例](./deployment/docker/redis/conf/redis.conf) 仅供参考；部署时以实际挂载文件及环境变量为准。
- [营销资料](./marketing/) / [系统截图](./screenshots/) / [用户手册](./user-manual/) 均为展示或历史材料，不作为功能验收依据。
