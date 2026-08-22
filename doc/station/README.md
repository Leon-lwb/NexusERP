# NexusERP STATION 模块文档（工位终端设备通道）

> **创建日期**: 2026-08-21
> **模块代码**: `ruoyi-modules/ruoyi-station`（包名 `com.nexus.station`）
> **服务对象**: [nexus-station](../../../nexus-station) 工位智能终端（Electron + Vue 3，烧录/FCT/装配工位工控机）
> **状态**: ✅ C1-C18 + M1-a/M1-b 全部交付（2026-08-21，批次0-6 + 阶段4 批次1/2/3）

本目录包含 NexusERP 为 nexus-station 工位终端提供后端能力支持的接口契约与开发计划。全部接口为 **X-API-KEY 免登录设备通道**（请求头 `X-API-KEY`，密钥配置 `mes.api-key`），URL 前缀 `/mes/station/**`（`security.excludes` 已放行），租户固定 `000000`。

## 文档清单

### 开发计划（plans/）
| 文档 | 主题 | 状态 |
|------|------|------|
| [2026-08-21-station-device-channel-plan.md](./plans/2026-08-21-station-device-channel-plan.md) | 设备通道接口开发计划（契约 C1-C12 清单、模块设计、排期） | ✅ 批次0-6 已交付（commit c3c27907） |
| [2026-08-21-station-code-review-and-next-plan.md](./plans/2026-08-21-station-code-review-and-next-plan.md) | 双端代码审查（3 个集成缺陷 F-1~F-4 已修复）+ 下一阶段计划 | ✅ 阶段0 完成 |
| [2026-08-21-station-smoke-test-report.md](./plans/2026-08-21-station-smoke-test-report.md) | 阶段1 API 级联调冒烟报告：C1-C12+M1-a/b 全链验通；联调新发现并修复 F-5~F-8（跨库×事务×数据源注解类缺陷）；沉淀 4 条开发约定 | ✅ 阶段1 完成 |

## 模块概览

```
ruoyi-station/
└── com/nexus/station/
    ├── controller/   # StationDeviceController(/mes/station/**), StationHeartbeatController(C10),
    │                 # StationFlashPrepareController(M1-a prepare/M1-b 固件下载)
    ├── domain/       # MesStationDevice/MesStationReport/MesSnBindLog 实体 + dto/ 请求 DTO
    ├── mapper/       # Station* 前缀跨库 Mapper(@DS 切库)
    └── service/      # StationPassService(C2-C4)/StationSnService(C5-C6)/StationAndonService(C7-C8)
```

> 模块依赖 ruoyi-mes（2026-08-21 决策：放弃物理独立，直接复用 callAndon/工序状态机/SN 服务，避免业务逻辑漂移）。

### 数据源分布（多库架构，Mapper 必须显式 @DS）

| 数据 | 表 | 库 | @DS |
|------|----|----|-----|
| 工人档案 | `sch_worker` | ry-erp | `@DS("erp")` |
| 过站/流转卡/SN/测试记录/安灯 | `pro_flow_card_step` `pro_sn` `mes_test_record` `pro_andon` | ry-mes | `@DS("mes")` |
| 系统用户 | `sys_user` | ry-vue | master（默认） |
| 工位设备台账 | `mes_station_device` | ry-mes | `@DS("mes")` |
| 设备通道报工记录（幂等） | `mes_station_report` | ry-mes | `@DS("mes")` |
| 关键件绑定日志 | `mes_sn_bind_log` | ry-mes | `@DS("mes")` |
| 不良代码字典 | `ncr_defect_code` | ry-mes | `@DS("mes")` |

### 契约状态一览

| # | 端点 | 状态 |
|---|------|------|
| C1 | `GET /mes/station/operator` 扫工号上岗 | ✅ 已交付 |
| C2 | `POST /mes/station/pass/validate` 过站四重校验 | ✅ 已交付 |
| C3 | `POST /mes/station/pass/report` 过站单件报工 | ✅ 已交付 |
| C4 | `POST /mes/station/pass/report-batch` 批量计数报工 | ✅ 已交付 |
| C5 | `POST /mes/station/sn/bind` 关键件绑定 | ✅ 已交付 |
| C6 | `POST /mes/station/sn/unbind` 关键件解绑/换板 | ✅ 已交付 |
| C7 | `POST /mes/station/andon/call` 安灯呼叫 | ✅ 已交付 |
| C8 | `GET /mes/station/andon/latest` 安灯状态查询 | ✅ 已交付 |
| C9 | `GET /mes/station/defect-codes` 不良代码字典 | ✅ 已交付 |
| C10 | `POST /mes/flash/report/station/heartbeat` 设备心跳 | ✅ 已交付 |
| C11 | `POST /mes/flash/report/test/auto` 综测上报 | ✅ 已存在（FlashReportController） |
| C12 | 刷写/测试上报 BO 增加 operator 字段 | ✅ 已交付 |
| M1-a | `POST /mes/flash/report/station/prepare` 内容预取 | ✅ 已交付 |
| M1-b | `GET /mes/flash/report/firmware/{id}/download` 固件下载 | ✅ 已交付 |
| C13 | `GET /mes/station/trace/{snCode}` SN 全履历追溯（M3，四源时间线：刷写/综测/过站/绑定） | ✅ 已交付 |
| C14 | `GET /mes/station/inspection/tasks` 工位当日点检任务（M3，联动链定位设备 + inspection_items 新旧格式兼容） | ✅ 已交付 |
| C15 | `POST /mes/station/inspection/submit` 点检提交（M3，eq_inspection_record 留痕 + 不合格联动 FAULT+安灯） | ✅ 已交付 |
| C16 | `GET /mes/station/sop/current` 工位当前 SOP 文档列表（M3，按工序绑定顺序返回 PUBLISHED 多文档，登记查阅留痕） | ✅ 已交付 |
| C17 | `GET /mes/station/sop/{sopId}/download` SOP 文件流式代理下载（M3，MinIO 私有桶，仿固件下载 M1-b） | ✅ 已交付 |
| C18 | `GET /mes/station/sop/version-check` SOP 版本轻量查询（M3，客户端 5 分钟轮询比对触发版本强控） | ✅ 已交付 |

> 契约的权威定义以 nexus-station 侧 M2 计划为准：`nexus-station/doc/plans/2026-08-20-M2-过站综测安灯开发计划.md` 表 1。本目录文档为其后端实施视角的镜像与排期。
