# MES 模块文档

本目录包含 NexusERP MES 制造执行系统的设计文档。MES 覆盖工单、流转卡、安灯、SN 追溯、质量、设备、数据采集+SPC、固件刷写 (fw) 等子模块。

## 文档清单

### 设计与分析
| 文档 | 主题 |
|------|------|
| [NexusERP_MES文档.md](./NexusERP_MES文档.md) | MES 综合设计文档（19+ 章，Phase 1-3 + MBOM） |
| [NexusERP_设备管理优化方案.md](./NexusERP_设备管理优化方案.md) | 设备管理优化方案 |
| [hardware-production-flow-analysis.md](./hardware-production-flow-analysis.md) | 硬件生产全流程与 Nexus-ERP 实现差异分析 |

### 操作指南与方案（plans/）
| 文档 | 主题 |
|------|------|
| [2026-08-17-mes-flash-fullchain-guide.md](./plans/2026-08-17-mes-flash-fullchain-guide.md) | 刷写能力全链路操作流程（PCBA 下单到生产） |
| [2026-08-17-mes-flash-operation-guide.md](./plans/2026-08-17-mes-flash-operation-guide.md) | 刷写能力操作流程指南 |

> Week 17-27 周实施计划/验证报告作为开发日志保留在 [plans/archive/](./plans/archive/)（21 篇）；P0 开发计划、刷写抽象与发布包方案、开发期增量 SQL（已并入 `script/sql/ry-mes.sql` / `ry-vue.sql`）已删除。数据库变更摘要见 [../system/数据库增量变更记录.md](../system/数据库增量变更记录.md)。

## 交付进度（截至 2026-08）

| 阶段 | 内容 | 状态 |
|------|------|------|
| Phase 1-3 | 骨架/工单/流转卡/派工/安灯/SN追溯/质量/设备/SPC | ✅ |
| Week 28+ | MBOM 子系统 | ✅ |
| P0 扩展 | NCR/工装/ESOP/批次/进站出站 | ✅ |
| 固件刷写 (fw) | 固件档案/刷写记录/测试项/测试记录/工序门禁 | ✅ |
| 发布包 | 客户定制化发布包 + 工单固件确认 | ✅ |

## 阅读顺序

1. 新人：[NexusERP_MES文档.md](./NexusERP_MES文档.md) 第一章 总体设计 + 实施进度章节
2. 刷写/产线对接：先看全链路指南，再看操作流程指南
3. 跨模块边界：主文档第十二章 ERP↔MES 边界

## 自动化测试脚本（scripts/）

e2e 回归脚本（需本机 redis 读验证码 + 后端已启动，登录走 RSA+AES 加密；RSA 公钥均已与 `ruoyi-admin/application.yml` 的 `api-decrypt` 配套，2026-08 批量对齐）：

| 脚本 | 覆盖 |
|------|------|
| [p0-4-e2e-test.py](./scripts/p0-4-e2e-test.py) | 工单/流转卡 P0-4 e2e |
| [p0-4-e2e-advanced.py](./scripts/p0-4-e2e-advanced.py) | P0-4 进阶场景 |
| [p0-8-e2e-test.py](./scripts/p0-8-e2e-test.py) | P0-8 IoT 采集点绑定收尾 |
| [fw-e2e-test.js](./scripts/fw-e2e-test.js) | 固件刷写/测试项/测试记录/AUTO 上报，27 断言 |
| [fw-step-gate-test.js](./scripts/fw-step-gate-test.js) | 刷写工序门禁，9 断言 |
| [fw-route-flash-test.js](./scripts/fw-route-flash-test.js) | 固件刷写工序+工艺链路，8 断言 |
| [flash-smoke-test.js](./scripts/flash-smoke-test.js) | 免登录设备通道冒烟（模拟上位机） |
| [flash-fullchain-datagen.js](./scripts/flash-fullchain-datagen.js) / [toothbrush-datagen.js](./scripts/toothbrush-datagen.js) | 全链路/电动牙刷演示数据生成 |

固件脚本跑法：`node doc/mes/scripts/fw-e2e-test.js`（跑前建议清理 E2E 残留：`DELETE FROM fw_test_item WHERE item_code LIKE 'FM-%'` 等，见脚本注释）。
