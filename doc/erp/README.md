# ERP 模块文档

本目录包含 NexusERP ERP 模块的业务设计、变更说明、业务流转分析及 SQL 脚本。

## 文档清单

### 设计与变更
| 文档 | 编号 | 主题 | 日期 |
|------|------|------|------|
| [NexusERP_ERP文档.md](./NexusERP_ERP文档.md) | — | ERP 综合文档（架构/完成度/业务流程/MQ/Week1-16 记录） | 持续更新 |
| [NexusERP_业务流转分析.md](./NexusERP_业务流转分析.md) | — | 全业务流转分析（5 大流程图 + 跨模块总图） | 2026-06-03 |
| [NexusERP_编码生成规则设计方案.md](./NexusERP_编码生成规则设计方案.md) | v1.1 | 序列号/批次号/条码编码规则 | 2026-06-04 |
| [BOM管理增强-需求变更文档.md](./BOM管理增强-需求变更文档.md) | NexusERP-CHG-2026-001 | BOM 增强 Phase 1-5 | 2026-06-17 |
| [BOM优化-ERP成本融合MES制造BOM-变更说明文档.md](./BOM优化-ERP成本融合MES制造BOM-变更说明文档.md) | NexusERP-CHG-2026-002 | ERP 成本 BOM 与 MES 制造 BOM 融合 | 2026-06-25 |

### 实施计划（plans/）
| 文档 | 编号 | 主题 | 日期 |
|------|------|------|------|
| [2026-06-30-erp-wm-3d-warehouse-plan.md](./plans/archive/2026-06-30-erp-wm-3d-warehouse-plan.md) | NexusERP-PLAN-2026-003 | 仓储管理（wm）3D 可视化仓库实施计划 | 2026-06-30 |

### SQL 脚本（sql/）
| 脚本 | 用途 | 幂等 | 执行顺序 |
|------|------|------|----------|
| [erp-bom-routing-enhancement.sql](./sql/erp-bom-routing-enhancement.sql) | 建 4 表 + BOM 加字段 | ❌ | 1（先执行） |
| [erp-bom-cleanup.sql](./sql/erp-bom-cleanup.sql) | 删除 BOM 制造字段 | ❌ | 2（必须在 enhancement 之后） |
| [dict-bom-routing-process.sql](./sql/dict-bom-routing-process.sql) | BOM/工艺路线字典 | ❌ | 3 |
| [erp-process-routing-menu.sql](./sql/erp-process-routing-menu.sql) | 工序/工艺路线菜单 | ❌ | 4 |
| [erp-production-issue.sql](./sql/erp-production-issue.sql) | 生产领料单加字段 | ✅ | — |
| [erp-production-issue-dict.sql](./sql/erp-production-issue-dict.sql) | 生产领料单状态字典 | ✅ | — |
| [erp-purchase-order-contract.sql](./sql/erp-purchase-order-contract.sql) | 采购订单合同字段 | ✅ | — |
| [erp-wm-3d-demo-seed.sql](./sql/erp-wm-3d-demo-seed.sql) | 3D仓库可视化演示数据（仓库/库区/库位/库存） | ✅ | — |

⚠️ **关键执行顺序**：`erp-bom-routing-enhancement.sql` 会给 `bd_bom_detail` 增加 `process_id`/`routing_step_id`，而 `erp-bom-cleanup.sql` 会删除这两个字段。必须按 enhancement → cleanup 顺序执行，否则会失败。

## 阅读顺序

1. 新人：[NexusERP_ERP文档.md](./NexusERP_ERP文档.md) 第一、二章（架构 + 完成度）
2. 业务全貌：[NexusERP_业务流转分析.md](./NexusERP_业务流转分析.md)
3. BOM 演进：先读 CHG-2026-001（BOM 增强），再读 CHG-2026-002（BOM 优化融合）
4. 编码规则：[NexusERP_编码生成规则设计方案.md](./NexusERP_编码生成规则设计方案.md)

## 已知问题

1. **孤儿 SQL 脚本**：`erp-production-issue.sql`、`erp-production-issue-dict.sql`、`erp-purchase-order-contract.sql` 无对应变更说明文档（待补）
2. **文档引用 SQL 文件名错误**：CHG-2026-001 引用 `erp-bd-process-routing-menu.sql`，实际为 `erp-process-routing-menu.sql`；路径写 `script/sql/` 实际为 `sql/`
3. **绝对路径**：CHG-2026-001 引用 `C:\Users\MyPC\.claude\plans\erp-bom-routing-enhancement.md`
4. **内容过时**：[NexusERP_业务流转分析.md](./NexusERP_业务流转分析.md)（2026-06-03）的 `BdBomDetail` 仍含 `workstationId`/`processId`，但 2026-06-25 已删除
5. **自相矛盾**：[NexusERP_ERP文档.md](./NexusERP_ERP文档.md) 13.1 节表格标记"物料-供应商关系管理"全 ❌，但注释说"Week 13 已完成"
6. **SQL 幂等性不一致**：4 个旧脚本不幂等，3 个新脚本幂等
7. **文档体量过大**：[NexusERP_ERP文档.md](./NexusERP_ERP文档.md) 1300+ 行，建议增加目录跳转
8. **孤儿文件**：[erp-cockpit.html](./erp-cockpit.html) 用途未说明
