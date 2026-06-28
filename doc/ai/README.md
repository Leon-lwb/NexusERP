# AI 模块文档

本目录包含 NexusERP AI 模块（LangChain4j + 知识图谱 + MCP）的设计与开发计划文档。

## 文档清单

### 总体计划
| 文档 | 主题 | 状态 |
|------|------|------|
| [NexusERP_AI开发计划.md](./NexusERP_AI开发计划.md) | 项目总开发计划 v6.1（Phase 0-6） | 🚧 Phase 0/1/2 完成 |
| [NexusERP_AI二阶段开发计划_AI平台能力深化.md](./NexusERP_AI二阶段开发计划_AI平台能力深化.md) | 二阶段：S2-A 知识图谱 / S2-B 推理增强 / S2-C 联网+MCP+多模态 | ✅ S2-B/S2-C 完成 / S2-A 搁置 |

### 周计划 / 审查
| 文档 | 周次 | 主题 | 状态 |
|------|------|------|------|
| [NexusERP_AI_QuickStart_Phase0.md](./NexusERP_AI_QuickStart_Phase0.md) | Phase 0 | 5 天最小可运行版本实施手册 | ✅ |
| [NexusERP_AI_Week2_Plan.md](./NexusERP_AI_Week2_Plan.md) | Week 2 | 前端对接 + 流式推送 + ErpReportAgent | — |
| [NexusERP_AI_Week3_Review.md](./NexusERP_AI_Week3_Review.md) | Week 3 | RAG 知识库审查（对照 ruoyi-ai） | — |
| [NexusERP_AI_Week4_Plan.md](./NexusERP_AI_Week4_Plan.md) | Week 4 | 修复补齐 + 重排序集成 | ✅ |
| [NexusERP_AI_Week5_Plan.md](./NexusERP_AI_Week5_Plan.md) | Week 5 | Schema 增强 + 业务 Tool + MCP（含两轮代码审查） | — |
| [NexusERP_AI_Week6_Plan.md](./NexusERP_AI_Week6_Plan.md) | Week 6 | MCP 外部工具 + Agent 流式 + 质量评估 | ✅ |
| [NexusERP_AI_Week7_Plan.md](./NexusERP_AI_Week7_Plan.md) | Week 7 | Text2SQL 优化 + Prompt 模板 | — |
| [NexusERP_AI_Week8_Plan.md](./NexusERP_AI_Week8_Plan.md) | Week 8 | 业务 Tool 增强 + SQL Few-Shot | — |
| [NexusERP_AI_Week9_Plan.md](./NexusERP_AI_Week9_Plan.md) | Week 9 | Schema Token 优化 + 知识库导入 + 前端补齐 | — |
| [NexusERP_AI_Week10_Plan.md](./NexusERP_AI_Week10_Plan.md) | Week 10 | MCP 动态选择 + A/B 测试（2026-06-16~06-20） | — |
| [NexusERP_AI_Week11_Plan.md](./NexusERP_AI_Week11_Plan.md) | Week 11 | Token 用量统计与成本分析（2026-06-23~06-27） | — |

## 阅读顺序

1. 新人：先读 [NexusERP_AI开发计划.md](./NexusERP_AI开发计划.md) 第一、二章了解架构与技术栈
2. 快速上手 Phase 0：读 [NexusERP_AI_QuickStart_Phase0.md](./NexusERP_AI_QuickStart_Phase0.md)
3. 二阶段规划：读 [NexusERP_AI二阶段开发计划_AI平台能力深化.md](./NexusERP_AI二阶段开发计划_AI平台能力深化.md)
4. 周计划按 Week 序号顺序阅读

## 已知问题

1. **Week 1 计划缺失**：周计划序列从 Week 2 开始，推测 Week 1 即 QuickStart_Phase0，但未建立明确关联（待确认）
2. **路径不一致**：QuickStart 与主计划使用 `E:\Nexus\nexus-erp`，Week3_Review 使用 `d:\workspace\nexus-erp`；参照项目路径 `E:\ruoyi-ai` vs `D:\ruoyi-ai` 大小写不一
3. **状态标记不统一**：Week 2-9 多数无状态标记与日期，仅 Week 10/11 有明确日期
4. **内容重复**：QuickStart 与主计划 Phase 0 内容大量重叠，构成双重维护
5. **跨周问题不闭环**：Week 5 二次审查遗留 6 个 Critical 未在 Week 6 交代；Week 7/8 前端欠债仅由 Week 9 揭示
6. **Phase 2 收尾缺失**：Week 11 为 Phase 2 最后一周，无整体收尾/总结文档，与二阶段（Week 12 起）无过渡说明

## 二阶段（S2-B/S2-C）实施记录

> 更新日期：2026-06-28

### S2-B 推理模式 + 厂商增强 + 专用 Rerank ✅ 完成

- 后端 + 前端全部完成，深度思考已在 Chat/RAG 模式验证可用
- 实施计划详见 `doc/ai/NexusERP_AI二阶段实施计划_S2B_S2C.md`
- 关键文件：`ChatServiceProvider` 策略层（8 厂商）、`RerankProvider`（3 API）、`DeepThinking.vue`
- 注意：Agent 模式暂不支持 thinking token 流（设计限制，使用同步 ChatModel）

### S2-C 联网搜索 + MCP 市场 + 文生图 ✅ 完成

- 后端：WebSearchTool/Agent、McpMarketService/Controller、TongYiWanxImageService 全部编译通过
- 前端：MCP 市场页（`views/ai/mcp/market.vue`）、文生图渲染（`ImageRenderer.vue` + `AI_IMAGE` 接线）、联网开关+来源卡（`WebSearchToggle.vue`）全部完成
- 代码审查发现并修复 2 个 Bug（MCP 加载状态值、crawlWebpage 超时）

### S2-A 知识图谱 — 暂搁

- 用户决定暂搁（工作量最大、需自研），后续单独排期
