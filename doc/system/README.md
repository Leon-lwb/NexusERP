# 系统级文档

本目录包含跨模块的系统级文档：项目审查、规划、部署、营销与用户手册。

## 文档清单

### 审查与规划
| 文档 | 主题 | 状态 |
|------|------|------|
| [2026-08-16-全项目审查报告.md](./2026-08-16-全项目审查报告.md) | 全项目审查：缺失功能 + 待修复清单（安全/并发/前端），当前权威审计基线 | ✅ 最新 |
| [数据库增量变更记录.md](./数据库增量变更记录.md) | 2026-07~08 数据库增量变更摘要（原 `script/sql/update/` 44 个脚本的收口记录） | ✅ 最新 |
| [2026-08-19-Nexus客户端生态规划.md](./2026-08-19-Nexus客户端生态规划.md) | 桌面工位端/手持终端/边缘网关客户端生态规划 | 📋 规划 |
| [NexusERP_plus-ui前端优化方案.md](./NexusERP_plus-ui前端优化方案.md) | plus-ui 前端优化（阶段 0-4 已完成，推广批次待开始） | 🚧 |
| [2026-07-29-system-gap-analysis-vs-mainstream.md](./2026-07-29-system-gap-analysis-vs-mainstream.md) | 与主流 MES/ERP/IoT/AI 功能差距分析 | ✅ 已发布 |
| [NexusERP_品牌Logo设计方案.md](./NexusERP_品牌Logo设计方案.md) | 品牌 Logo 设计方案（已定稿） | ✅ |
| [workflow-regression-checklist.md](./workflow-regression-checklist.md) | 工作流模块回归测试清单（发版前手工回归） | ✅ |
| [specs/charset-encoding-standard.md](./specs/charset-encoding-standard.md) | 字符集编码规范 | ✅ |

> 2026-08-29 精简：三模块共同升级方案、06-24 代码审计报告（已被 08-16 审查报告覆盖）已删除；历史计划与分析报告作为开发日志保留在 [plans/archive/](./plans/archive/)（9 篇）。

### 部署（deployment/）
| 文档/配置 | 路径 | 说明 |
|-----------|------|------|
| Docker 部署文档 | [deployment/docker部署文档.md](./deployment/docker部署文档.md) | v2.1，2026-07-26（主服务器） |
| 新服务器部署指南 | [deployment/新服务器部署指南.md](./deployment/新服务器部署指南.md) | 新服务器部署步骤 |
| docker-compose-main | [deployment/docker/docker-compose-main.yml](./deployment/docker/docker-compose-main.yml) | 主服务器编排 |
| redis.conf | [deployment/docker/redis/conf/redis.conf](./deployment/docker/redis/conf/redis.conf) | Redis 配置 |

### 营销（marketing/）
| 文档 | 主题 |
|------|------|
| [NexusERP_1.0上线公众号文章.md](./marketing/NexusERP_1.0上线公众号文章.md) | 1.0 上线宣传（六大核心模块） |
| [NexusERP_1.1上线公众号文章.md](./marketing/NexusERP_1.1上线公众号文章.md) | 1.1 上线宣传（MES + IoT 双引擎） |

### 资源
- [screenshots/](./screenshots/) - 系统截图（PNG + GIF，登录页与各业务模块界面）
- [user-manual/](./user-manual/) - 用户操作手册（docx + 编写大纲）

## 阅读顺序

1. 项目现状与缺口：[2026-08-16-全项目审查报告.md](./2026-08-16-全项目审查报告.md)
2. 客户端规划：[2026-08-19-Nexus客户端生态规划.md](./2026-08-19-Nexus客户端生态规划.md)
3. 部署运维：[deployment/docker部署文档.md](./deployment/docker部署文档.md) + 配置文件
