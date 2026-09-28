# ERP 模块文档

本目录包含 NexusERP ERP 模块的业务设计、业务流转分析、设计规格与测试脚本。

## 文档清单

### 设计与分析
| 文档 | 主题 | 日期 |
|------|------|------|
| [NexusERP_ERP文档.md](./NexusERP_ERP文档.md) | ERP 综合文档（各历史章节按日期阅读） | 章节时点不一 |
| [NexusERP_业务流转分析.md](./NexusERP_业务流转分析.md) | 全业务流转分析（5 大流程图 + 跨模块总图） | 2026-06-03 |
| [NexusERP_编码生成规则设计方案.md](./NexusERP_编码生成规则设计方案.md) | 序列号/批次号/条码编码规则 | 2026-06-04 |
| [pcba-subcontract-feasibility-and-plan.md](./pcba-subcontract-feasibility-and-plan.md) | PCBA 委外订单功能完善方案（可行性与工作量评估） | 2026-08 |

> 历史实施计划（3D 仓库、SO 联动、QC 联动等）作为开发日志保留在 [plans/archive/](./plans/archive/)（截至 2026-09-28 共 11 篇）；原财务核心总计划等旧原件已清理，财务各规格仍保留。08-29 后存在面向存量库的新迁移脚本，新装主 SQL 与升级脚本不能混用。数据库变更摘要见 [../system/数据库增量变更记录.md](../system/数据库增量变更记录.md)。

### 活跃计划与历史交接

- [第二梯队分段计划](./plans/2026-09-02-tier2-competition-plan.md)：仍含存量环境执行/验收待确认项，不因代码存在而归档为全部完成。
- [供应商字典对齐实施计划](./plans/2026-09-23-bd-vendor-dict-align-plan.md)：代码相关提交见 09-23，但任务复核/存量库记录与环境验收须独立核对，原勾选未全部完成。
- [销售模块交接报告](./plans/2026-09-19-so-module-handover.md)：09-19 六批次已交付，保留遗留风险和复审建议；[原优化计划](./plans/archive/2026-09-19-so-module-optimization-plan.md) 已归档。
- [APS + PdM 第一梯队原计划](./plans/archive/2026-08-30-tier1-aps-pdm-plan.md)：09-02 后端交付后归档；不代表各环境上线验收。
### 设计规格（specs/）
| 文档 | 主题 | 日期 |
|------|------|------|
| [2026-06-30-list-page-standard-design.md](./specs/2026-06-30-list-page-standard-design.md) | 列表页标准设计 | 2026-06-30 |
| [2026-07-15-invoicing-ar-ap-redesign.md](./specs/2026-07-15-invoicing-ar-ap-redesign.md) | 开票与应收应付优化设计（发票域 P0-P5） | 2026-07-15 |
| [2026-07-29-f0-gl-ledger-impl-spec.md](./specs/2026-07-29-f0-gl-ledger-impl-spec.md) | F0 GL 总账详细实施规格 | 2026-07-29 |
| [2026-07-30-f1-ar-ap-reconciliation-impl-spec.md](./specs/2026-07-30-f1-ar-ap-reconciliation-impl-spec.md) | F1 应收应付核销实施规格 | 2026-07-30 |
| [2026-07-31-f5-financial-reports-closing-impl-spec.md](./specs/2026-07-31-f5-financial-reports-closing-impl-spec.md) | F5 财务报表与期末关账实施规格 | 2026-07-31 |
| [2026-08-01-f2-fixed-assets-impl-spec.md](./specs/2026-08-01-f2-fixed-assets-impl-spec.md) | F2 固定资产实施规格 | 2026-08-01 |
| [2026-08-01-f3a-inventory-cost-impl-spec.md](./specs/2026-08-01-f3a-inventory-cost-impl-spec.md) | F3a 库存成本实施规格 | 2026-08-01 |
| [2026-08-02-vision-integration-impl-spec.md](./specs/2026-08-02-vision-integration-impl-spec.md) | 视觉识别整体接入规格 | 2026-08-02 |

### 其他
- [guides/rack-test-flow-guide.md](./guides/rack-test-flow-guide.md) — 货架测试流程指南
- [sql/test-data/](./sql/test-data/) — 演示/测试数据脚本（3D 仓库、E2E）
- [scripts/](./scripts/) — CV 视觉识别 E2E 测试脚本

## 阅读顺序

1. 新人：[NexusERP_ERP文档.md](./NexusERP_ERP文档.md) 第一、二章（架构 + 完成度）
2. 业务全貌：[NexusERP_业务流转分析.md](./NexusERP_业务流转分析.md)
3. 财务模块设计：specs/ 下 F0/F1/F2/F3a/F5 系列
4. 编码规则：[NexusERP_编码生成规则设计方案.md](./NexusERP_编码生成规则设计方案.md)

## 文档时点说明

- [业务流转分析](./NexusERP_业务流转分析.md) 的 `BdBomDetail` 字段表已按当前实体更正；它仍是 2026-06-03 的流程分析，而非实时数据库字典。
- [ERP 主文档](./NexusERP_ERP文档.md) §14.3 是原阶段工作量估算，已注明不代表今日待办；§13.1 中物料-供应商关系管理已完成。
- [erp-cockpit.html](./erp-cockpit.html) 是独立的 HTML/JS 驾驶舱静态原型，引用 CDN 和本地样例数据；不作为真实 API 已接入的证据。
