# NexusERP STATION 模块文档（工位终端设备通道）

> **创建日期**: 2026-08-21
> **模块代码**: `ruoyi-modules/ruoyi-station`（包名 `com.nexus.station`）
> **服务对象**: [nexus-station](../../../nexus-station) 工位智能终端（Electron + Vue 3，烧录/FCT/装配工位工控机）
> **状态**: 🔨 开发中 — C1 已交付，C2-C12 待开发（见开发计划）

本目录包含 NexusERP 为 nexus-station 工位终端提供后端能力支持的接口契约与开发计划。全部接口为 **X-API-KEY 免登录设备通道**（请求头 `X-API-KEY`，密钥配置 `mes.api-key`），URL 前缀 `/mes/station/**`（`security.excludes` 已放行），租户固定 `000000`。

## 文档清单

### 开发计划（plans/）
| 文档 | 主题 | 状态 |
|------|------|------|
| [2026-08-21-station-device-channel-plan.md](./plans/2026-08-21-station-device-channel-plan.md) | 设备通道接口开发计划（契约 C1-C12 清单、模块设计、排期） | 🔨 执行中 |

## 模块概览

```
ruoyi-station/
└── com/nexus/station/
    ├── controller/   # StationDeviceController — 设备通道统一入口(/mes/station/**)
    └── mapper/       # 跨库只读查询(Station* 前缀命名, 避免与业务模块 Mapper bean 冲突)
```

### 数据源分布（多库架构，Mapper 必须显式 @DS）

| 数据 | 表 | 库 | @DS |
|------|----|----|-----|
| 工人档案 | `sch_worker` | ry-erp | `@DS("erp")` |
| 过站/流转卡/SN/测试记录/安灯 | `pro_flow_card_step` `pro_sn` `mes_test_record` `pro_andon` | ry-mes | `@DS("mes")` |
| 系统用户 | `sys_user` | ry-vue | master（默认） |
| 工位设备台账 | `mes_station_device`（待建表） | ry-mes | `@DS("mes")` |

### 契约状态一览

| # | 端点 | 状态 |
|---|------|------|
| C1 | `GET /mes/station/operator` 扫工号上岗 | ✅ 已交付 |
| C2 | `POST /mes/station/pass/validate` 过站四重校验 | 🔶 待开发 |
| C3 | `POST /mes/station/pass/report` 过站单件报工 | 🔶 待开发 |
| C4 | `POST /mes/station/pass/report-batch` 批量计数报工 | 🔶 待开发 |
| C5 | `POST /mes/station/sn/bind` 关键件绑定 | 🔶 待开发 |
| C6 | `POST /mes/station/sn/unbind` 关键件解绑/换板 | 🔶 待开发 |
| C7 | `POST /mes/station/andon/call` 安灯呼叫 | 🔶 待开发 |
| C8 | `GET /mes/station/andon/latest` 安灯状态查询 | 🔶 待开发 |
| C9 | `GET /mes/station/defect-codes` 不良代码字典 | 🔶 待开发 |
| C10 | `POST /mes/flash/report/station/heartbeat` 设备心跳 | 🔶 待开发（需建表） |
| C11 | `POST /mes/flash/report/test/auto` 综测上报 | ✅ 已存在（FlashReportController） |
| C12 | 刷写/测试上报 BO 增加 operator 字段 | 🔶 待开发 |

> 契约的权威定义以 nexus-station 侧 M2 计划为准：`nexus-station/doc/plans/2026-08-20-M2-过站综测安灯开发计划.md` 表 1。本目录文档为其后端实施视角的镜像与排期。
