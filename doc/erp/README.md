# ERP 模块文档

本目录包含 NexusERP ERP 模块的业务设计、业务流转分析、设计规格与测试脚本。

## 文档清单

### 设计与分析
| 文档 | 主题 | 日期 |
|------|------|------|
| [NexusERP_ERP文档.md](./NexusERP_ERP文档.md) | ERP 综合文档（架构/完成度/业务流程/MQ/开发记录） | 持续更新 |
| [NexusERP_业务流转分析.md](./NexusERP_业务流转分析.md) | 全业务流转分析（5 大流程图 + 跨模块总图） | 2026-06-03 |
| [NexusERP_编码生成规则设计方案.md](./NexusERP_编码生成规则设计方案.md) | 序列号/批次号/条码编码规则 | 2026-06-04 |
| [pcba-subcontract-feasibility-and-plan.md](./pcba-subcontract-feasibility-and-plan.md) | PCBA 委外订单功能完善方案（可行性与工作量评估） | 2026-08 |

> 历史实施计划（3D 仓库、SO 联动、QC 联动等）作为开发日志保留在 [plans/archive/](./plans/archive/)（9 篇）；BOM 增强/成本融合变更文档、财务核心开发计划与开发期增量 SQL（已并入 `script/sql/ry-erp.sql` / `ry-vue.sql`）已删除。数据库变更摘要见 [../system/数据库增量变更记录.md](../system/数据库增量变更记录.md)。

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

## 已知问题

1. **内容过时**：[NexusERP_业务流转分析.md](./NexusERP_业务流转分析.md)（2026-06-03）的 `BdBomDetail` 仍含 `workstationId`/`processId`，但 2026-06-25 已删除
2. **自相矛盾**：[NexusERP_ERP文档.md](./NexusERP_ERP文档.md) 13.1 节表格标记"物料-供应商关系管理"全 ❌，但注释说"Week 13 已完成"
3. **孤儿文件**：[erp-cockpit.html](./erp-cockpit.html) 用途未说明
