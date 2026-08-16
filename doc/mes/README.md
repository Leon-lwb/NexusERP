# MES 模块文档

本目录包含 NexusERP MES 制造执行系统的设计文档与 Week 17-28 实施计划。MES 覆盖 7 子模块：工单、流转卡、安灯、SN 追溯、质量、设备、数据采集+SPC。

## 文档清单

### 主文档
| 文档 | 主题 |
|------|------|
| [NexusERP_MES文档.md](./NexusERP_MES文档.md) | MES 综合设计文档（19 章，Phase 1-3） |

### 周计划（plans/）
| 文档 | Week | 类型 | 主题 |
|------|------|------|------|
| [2026-06-16-mes-iot-phase-plan.md](./plans/2026-06-16-mes-iot-phase-plan.md) | — | 总计划 | MES Phase 1-3 + IoT 阶段一总规划 |
| [2026-06-16-mes-week17-implementation-report.md](./plans/archive/2026-06-16-mes-week17-implementation-report.md) | 17 | report | 模块骨架 |
| [2026-06-17-mes-week18-implementation-plan.md](./plans/archive/2026-06-17-mes-week18-implementation-plan.md) | 18 | plan | ERP 桥接 + 工单全链路 |
| [2026-06-17-mes-week19-implementation-plan.md](./plans/archive/2026-06-17-mes-week19-implementation-plan.md) | 19 | plan | 主数据前端 + Flowable 审批 |
| [2026-06-18-mes-week20-implementation-plan.md](./plans/archive/2026-06-18-mes-week20-implementation-plan.md) | 20 | plan | 流转卡引擎 + Phase 1 收尾 |
| [2026-06-19-mes-week21-implementation-plan.md](./plans/archive/2026-06-19-mes-week21-implementation-plan.md) | 21 | plan | 前端补全 + 菜单清理 |
| [2026-06-19-mes-week21-verification-report.md](./plans/archive/2026-06-19-mes-week21-verification-report.md) | 21 | report | Phase 1 静态验证 |
| [2026-06-20-mes-week22-implementation-plan.md](./plans/archive/2026-06-20-mes-week22-implementation-plan.md) | 22 | plan | 派工引擎 |
| [2026-06-21-mes-week23-implementation-plan.md](./plans/archive/2026-06-21-mes-week23-implementation-plan.md) | 23 | plan | 安灯系统 |
| [2026-06-22-mes-week24-implementation-plan.md](./plans/archive/2026-06-22-mes-week24-implementation-plan.md) | 24 | plan | SN 追溯 |
| [2026-06-23-mes-week25-implementation-plan.md](./plans/archive/2026-06-23-mes-week25-implementation-plan.md) | 25 | plan | 质量管理 |
| [2026-06-24-mes-week26-implementation-plan.md](./plans/archive/2026-06-24-mes-week26-implementation-plan.md) | 26 | plan | 设备管理 |
| [2026-06-25-mes-week27-implementation-plan.md](./plans/archive/2026-06-25-mes-week27-implementation-plan.md) | 27 | plan | 数据采集 + SPC（Phase 3 收尾） |

### SQL 脚本（sql/）
命名规范：`mes-weekNN-<序号>-<名称>.sql`，序号 01-09 业务表 DDL，10+ 菜单/流程配置。

| 序号 | Week | 脚本 | 用途 |
|------|------|------|------|
| 01 | 17 | [mes-week17-01-init.sql](./sql/archive/mes-week17-01-init.sql) | pro_work_order + pro_work_order_step |
| 02 | 20 | [mes-week20-02-flowcard.sql](./sql/archive/mes-week20-02-flowcard.sql) | pro_flow_card + step |
| 03 | 22 | [mes-week22-03-dispatch.sql](./sql/archive/mes-week22-03-dispatch.sql) | pro_dispatch |
| 04 | 23 | [mes-week23-04-andon.sql](./sql/archive/mes-week23-04-andon.sql) | pro_andon |
| 05 | 24 | [mes-week24-05-sntrace.sql](./sql/archive/mes-week24-05-sntrace.sql) | pro_sn |
| 06 | 25 | [mes-week25-06-quality.sql](./sql/archive/mes-week25-06-quality.sql) | qc_iqc/ipqc/oqc |
| 07 | 26 | [mes-week26-07-equipment.sql](./sql/archive/mes-week26-07-equipment.sql) | eq_equipment/maintenance/repair |
| 08 | 27 | [mes-week27-08-collection-spc.sql](./sql/archive/mes-week27-08-collection-spc.sql) | dc_collection + spc_control_chart |
| 09 | 28 | [mes-week28-09-mbom.sql](./sql/archive/mes-week28-09-mbom.sql) | mes_mbom + line |
| 10-22 | 17-28 | mes-weekNN-{10~22}-menu.sql | 各 Week 菜单权限（4000 段） |

## Week 进度总览

| Phase | Week | 模块 | 状态 |
|-------|------|------|------|
| Phase 1 | 17-21 | 骨架+工单+流转卡+前端 | ✅ 已完成（领先计划） |
| Phase 2 | 22-24 | 派工+安灯+SN追溯 | ✅ 已完成 |
| Phase 3 | 25-27 | 质量+设备+SPC | ✅ 已完成 |
| 后续 | 28 | MBOM 子系统 | ✅ SQL 已就绪，主文档已补 §20 章节 |

## 阅读顺序

1. 新人：[NexusERP_MES文档.md](./NexusERP_MES文档.md) 第一章 总体设计 + 第十九章 实施进度
2. 总规划：[2026-06-16-mes-iot-phase-plan.md](./plans/2026-06-16-mes-iot-phase-plan.md)
3. 周计划按 Week 17→27 顺序阅读
4. 跨模块边界：主文档第十二章 ERP↔MES 边界

## 已知问题

1. ✅ **Week 28 MBOM 已补章节**（已修复）：主文档已新增 §二十「Week 28 MBOM 子系统扩展」（背景/职责划分/DDL/菜单/交付状态），原 SQL 脚本 [mes-week28-09-mbom.sql](./sql/archive/mes-week28-09-mbom.sql) 与 [mes-week28-22-menu.sql](./sql/archive/mes-week28-22-menu.sql) 已纳入主文档说明
2. ✅ **主文档 DDL 已与 SQL 对齐**（已修复）：§2 已同步 17 张表，`wo_id`→`mes_wo_id`、`tenant_id BIGINT`→`VARCHAR(20)`、`status`→`wo_status`、`is_ooc TINYINT`→`CHAR(1)`，已补 `del_flag` 与审计列，已加「DDL 已与 sql/archive/mes-weekNN-NN-xxx.sql 对齐」备注
3. ✅ **路径引用已修复**（已修复）：主文档已改为 `./plans/2026-06-16-mes-iot-phase-plan.md`
4. **早期 plan 菜单 ID 引用过时**：Week 17 report / Week 18 plan 引用 3000 段，实际 SQL 已用 4000 段（Week 21 已修正 SQL，但 plan 文档未更新）— 低优先级
5. ✅ **plan 文档 SQL 文件名引用已统一**（已修复）：12 个 plan 文件已全部改为 `mes-weekNN-{seq}-{name}.sql` 命名
6. **IoT 阶段一详细计划位置**：总计划含 IoT 阶段一（Week 33-36），对应文档在 [doc/iot/plans/](../iot/plans/) — 信息提示项
7. **验证报告不连续**：仅 Week 17 report 与 Week 21 verification-report，其他 Week 完成后未生成报告
8. **Week 23 命名与内容不符**：文件名为 `implementation-plan` 但内容是 report 性质

## 自动化测试脚本（scripts/）

e2e 回归脚本（需本机 redis 读验证码 + 后端已启动，登录走 RSA+AES 加密；RSA 公钥均已与 `ruoyi-admin/application.yml` 的 `api-decrypt` 配套，2026-08 批量对齐）：

| 脚本 | 覆盖 |
|------|------|
| [p0-4-e2e-test.py](./scripts/p0-4-e2e-test.py) | 工单/流转卡 P0-4 e2e |
| [p0-4-e2e-advanced.py](./scripts/p0-4-e2e-advanced.py) | P0-4 进阶场景 |
| [p0-8-e2e-test.py](./scripts/p0-8-e2e-test.py) | P0-8 IoT 采集点绑定收尾 |
| [fw-e2e-test.js](./scripts/fw-e2e-test.js) | 固件刷写(confirm/重刷/校验) + 测试项(3类型/唯一/限值) + 测试记录(开单/判定/追溯) + AUTO 上报(密钥鉴权/宽松批量/去重末值覆盖)，27 断言全绿 |
| [fw-step-gate-test.js](./scripts/fw-step-gate-test.js) | 刷写工序门禁(工单步骤设 firmware_id → 完工前卡上全部在制SN须固件PASS刷写，否则拒绝过站)，9 断言 |
| [fw-route-flash-test.js](./scripts/fw-route-flash-test.js) | 固件刷写工序+工艺链路(ERP bd_process FLASH 工序 + 工艺路线固件步骤 → 工单下达生成固件步骤落库)，8 断言 |

固件脚本跑法：`node doc/mes/scripts/fw-e2e-test.js`（跑前建议清理 E2E 残留：`DELETE FROM fw_test_item WHERE item_code LIKE 'FM-%'` 等三条，见脚本注释/交付说明）。
