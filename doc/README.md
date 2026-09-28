# NexusERP 文档中心

> 导航整理日期：2026-09-28；文档里的“完成”须结合所在分支、测试结果与部署/人工验收环境理解。

本目录是 NexusERP 项目的文档总入口，按业务模块组织。覆盖 AI、ERP、IoT、MES、IM、工位机（station）业务模块、流程编排（flow）与系统级（审查/部署/营销）文档。

## 快速入口

| 文档 | 说明 |
|------|------|
| **[PROGRESS.md](./PROGRESS.md)** | **📊 功能进度与跨模块流程** — 299 项为 2026-08-29 的历史统计，后续增量单列，未重算当前总数 |
| [PLANS.md](./PLANS.md) | **📅 2026-08-29 待办快照** — 优先级与数量为历史口径，现行遗留需结合后续审计核对 |
| [system/2026-09-06-全项目审查报告.md](./system/2026-09-06-全项目审查报告.md) | **🔍 最近一次全项目复核**（以当日代码为准，后续见审计/交接报告） |
| [system/数据库增量变更记录.md](./system/数据库增量变更记录.md) | 🗄️ 数据库变更登记（新装主 SQL / 存量迁移需区分） |

## 目录结构

| 目录 | 定位 | 入口 |
|------|------|------|
| [ai/](./ai/) | AI 模块、工业决策、竞赛材料及基准测试 | [ai/README.md](./ai/README.md) |
| [erp/](./erp/) | ERP 模块业务设计、财务规格、业务流转分析 | [erp/README.md](./erp/README.md) |
| [iot/](./iot/) | IoT 物联网平台设计与阶段一实施 | [iot/README.md](./iot/README.md) |
| [mes/](./mes/) | MES 制造执行系统设计、固件刷写操作指南 | [mes/README.md](./mes/README.md) |
| [im/](./im/) | IM 即时通讯升级记录、优化清单与性能测试资产 | [im/README.md](./im/README.md) |
| [station/](./station/) | 工位机（设备通道/上料）开发计划与测试报告 | [station/README.md](./station/README.md) |
| [flowcharts/](./flowcharts/) | 跨模块业务流程图（drawio 源文件 + 预览 + 生成脚本） | [flowcharts/README.md](./flowcharts/README.md) |
| [system/](./system/) | 项目级审查、规划、Docker 部署、营销、用户手册 | [system/README.md](./system/README.md) |
| [audit/](./audit/) | 2026-09-20~21 ERP/MES/IoT 专项审计、交接及后续优化 | [audit/README.md](./audit/README.md) |
| [security/](./security/) | 安全工具（Jasypt 密钥轮换） | — |

## 按角色阅读路径

### 新加入的开发者
1. 本 README 了解文档全貌
2. [CONTRIBUTING.md](./CONTRIBUTING.md) 了解文档规范
3. 进入对应模块目录的 README，按"阅读顺序"学习

### 运维人员
1. [system/deployment/docker部署文档.md](./system/deployment/docker部署文档.md)
2. [system/deployment/docker/](./system/deployment/docker/) 配置文件目录
3. 以仓库根目录 `docker-compose-main.yml` / `docker-compose-assist.yml` 为编排来源，先核对运行环境及 `.env` 安全变量；文档不保证生产配置已验真

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

- 2026-08-29 文档精简：历史开发日志保留在各模块 `plans/archive/`；当时的 44 个旧增量脚本已核对并入主 SQL 后清理；2026-08-29 之后又有面向存量库的一次性脚本，不能据旧说明认定 `script/sql/update/` 已不存在（见 [system/数据库增量变更记录.md](./system/数据库增量变更记录.md)）
- 各模块进度按文档标注的截至日期阅读；本地迁移、审查分支或代码提交均不自动等同于生产部署/人工验收
