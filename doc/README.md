# NexusERP 文档中心

本目录是 NexusERP 项目的文档总入口，按业务模块组织。覆盖 AI、ERP、IoT、MES、IM、工位机（station）业务模块、流程编排（flow）与系统级（审查/部署/营销）文档。

## 快速入口

| 文档 | 说明 |
|------|------|
| **[PROGRESS.md](./PROGRESS.md)** | **📊 功能开发进度总览** — 299 个功能点状态、跨模块业务流程图、未开发/搁置功能清单 |
| [PLANS.md](./PLANS.md) | **📅 前瞻开发计划** — 待办按 P0~P3 优先级排列 + 08-16 审查遗留待办摘要 |
| [system/2026-08-16-全项目审查报告.md](./system/2026-08-16-全项目审查报告.md) | **🔍 最新全项目审查**（缺失功能 + 待修复清单，权威审计基线） |
| [system/数据库增量变更记录.md](./system/数据库增量变更记录.md) | 🗄️ 数据库增量变更摘要（增量脚本已并入主 SQL） |

## 目录结构

| 目录 | 定位 | 入口 |
|------|------|------|
| [ai/](./ai/) | AI 模块（LangChain4j + 知识图谱 + MCP + 流程编排）设计与开发计划 | [ai/README.md](./ai/README.md) |
| [erp/](./erp/) | ERP 模块业务设计、财务规格、业务流转分析 | [erp/README.md](./erp/README.md) |
| [iot/](./iot/) | IoT 物联网平台设计与阶段一实施 | [iot/README.md](./iot/README.md) |
| [mes/](./mes/) | MES 制造执行系统设计、固件刷写操作指南 | [mes/README.md](./mes/README.md) |
| [im/](./im/) | IM 即时通讯升级记录、优化清单与性能测试资产 | — |
| [station/](./station/) | 工位机（设备通道/上料）开发计划与测试报告 | [station/README.md](./station/README.md) |
| [flowcharts/](./flowcharts/) | 跨模块业务流程图（drawio 源文件 + 预览 + 生成脚本） | [flowcharts/README.md](./flowcharts/README.md) |
| [system/](./system/) | 项目审查、规划、Docker 部署、营销、用户手册 | [system/README.md](./system/README.md) |
| [security/](./security/) | 安全工具（Jasypt 密钥轮换） | — |

## 按角色阅读路径

### 新加入的开发者
1. 本 README 了解文档全貌
2. [CONTRIBUTING.md](./CONTRIBUTING.md) 了解文档规范
3. 进入对应模块目录的 README，按"阅读顺序"学习

### 运维人员
1. [system/deployment/docker部署文档.md](./system/deployment/docker部署文档.md)
2. [system/deployment/docker/](./system/deployment/docker/) 配置文件目录
3. ⚠️ 部署文档与 `docker-compose.yml` 存在多处不一致，执行前需核对

### 业务/产品人员
1. [erp/NexusERP_业务流转分析.md](./erp/NexusERP_业务流转分析.md) — 全业务流转
2. [flowcharts/](./flowcharts/) — 各模块业务流程图预览
3. [system/user-manual/](./system/user-manual/) — 用户操作手册（docx）

### 对外宣传/演示
1. [system/marketing/](./system/marketing/) — 公众号文章
2. [system/screenshots/](./system/screenshots/) — 系统截图

## 文档规范

新增或修改文档请遵循 [CONTRIBUTING.md](./CONTRIBUTING.md)，涵盖：
- 命名规范、元信息模板
- 状态标记、路径引用规范
- SQL 脚本规范、内容维护机制

## 说明

- 2026-08-29 文档精简：历史开发日志保留在各模块 `plans/archive/`；已完成的一次性实施计划与 `script/sql/update/` 历史增量脚本（已并入主 SQL，摘要见 [system/数据库增量变更记录.md](./system/数据库增量变更记录.md)）已删除
- 各模块实施进度以 [PROGRESS.md](./PROGRESS.md) 与对应主文档为准
