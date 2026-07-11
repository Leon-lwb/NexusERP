# NexusERP 文档中心

本目录是 NexusERP 项目的文档总入口，按业务模块组织。截至 2026-07-10，文档覆盖 AI、ERP、IoT、MES 四大业务模块及系统级（部署/审计/营销）文档。

## 快速入口

| 文档 | 说明 |
|------|------|
| **[PROGRESS.md](./PROGRESS.md)** | **📊 功能开发进度总览** — 258 个功能点状态、跨模块业务流程图、未开发/搁置功能清单 |
| [PLANS.md](./PLANS.md) | **📅 前瞻开发计划** - 44 项待办按 P0~P3 优先级排列，含预估工时与执行建议 |
| [devlog/](./devlog/) | 按模块分卷开发日志（含前期概要 + 正式开发期详情链接） |

## 目录结构

| 目录 | 定位 | 入口 |
|------|------|------|
| [PROGRESS.md](./PROGRESS.md) | 功能开发进度总览（258功能点 + 流程图） | 直接查看 |
| [PLANS.md](./PLANS.md) | 前瞻开发计划（44 项待办，P0~P3 优先级） | 直接查看 |
| [devlog/](./devlog/) | 按模块分卷开发日志 | [devlog/README.md](./devlog/README.md) |
| [ai/](./ai/) | AI 模块（LangChain4j + 知识图谱 + MCP）设计与开发计划 | [ai/README.md](./ai/README.md) |
| [erp/](./erp/) | ERP 模块业务设计、变更说明、业务流转分析 | [erp/README.md](./erp/README.md) |
| [iot/](./iot/) | IoT 物联网平台设计与阶段一实施 | [iot/README.md](./iot/README.md) |
| [mes/](./mes/) | MES 制造执行系统设计与 Week 17-28 实施记录 | [mes/README.md](./mes/README.md) |
| [system/](./system/) | 跨模块升级方案、代码审计、Docker 部署、营销文档 | [system/README.md](./system/README.md) |

## 按角色阅读路径

### 新加入的开发者
1. 本 README 了解文档全貌
2. [CONTRIBUTING.md](./CONTRIBUTING.md) 了解文档规范
3. 进入对应模块目录的 README，按"阅读顺序"学习

### 运维人员
1. [system/deployment/docker部署文档.md](./system/deployment/docker部署文档.md)
2. [system/deployment/docker/](./system/deployment/docker/) 配置文件目录
3. ⚠️ 部署文档与 `docker-compose.yml` 存在多处不一致，执行前需核对（详见 [system/README.md](./system/README.md) 已知问题）

### 业务/产品人员
1. [erp/NexusERP_业务流转分析.md](./erp/NexusERP_业务流转分析.md) — 全业务流转
2. [mes/NexusERP_MES文档.md](./mes/NexusERP_MES文档.md) 第一章 总体设计
3. [iot/NexusERP_IoT文档.md](./iot/NexusERP_IoT文档.md) 第一章 总体设计

### 对外宣传/演示
1. [system/marketing/](./system/marketing/) — 公众号文章
2. [system/screenshots/](./system/screenshots/) — 系统截图
3. [system/user-manual/](./system/user-manual/) — 用户操作手册（docx）

## 跨模块文档

| 文档 | 说明 |
|------|------|
| [system/NexusERP_三模块共同升级方案.md](./system/NexusERP_三模块共同升级方案.md) | ERP/MES/IoT 协同升级路线图（三波次，约 13 周） |
| [system/NexusERP_代码审计报告与修复进度.md](./system/NexusERP_代码审计报告与修复进度.md) | 全模块代码审计（42 问题，CRITICAL 已修复，HIGH/MEDIUM/LOW 待修复） |

## 文档规范

新增或修改文档请遵循 [CONTRIBUTING.md](./CONTRIBUTING.md)，涵盖：
- 命名规范、元信息模板
- 状态标记、路径引用规范
- SQL 脚本规范、内容维护机制

## 说明

- 各模块实施进度以对应主文档"实施进度更新"章节为准
- 历史文档中存在绝对路径（`E:\Nexus`、`C:\Users\...`）与错误引用，正在逐步修复，详见各子目录 README 的"已知问题"
