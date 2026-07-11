# NexusERP 前瞻开发计划

> 创建日期: 2026-07-11
> 数据来源: 各模块 `plans/` 原始计划文档未完成项提取 + [PROGRESS.md](./PROGRESS.md) 未开发功能清单
> 状态图例: 🔴 P0 紧急 | 🟠 P1 高 | 🟡 P2 中 | 🟢 P3 低 | ⚪ 远期

---

## 一、计划总览

| 优先级 | 数量 | 说明 |
|--------|------|------|
| 🔴 P0 紧急 | 3 | 阻塞运行时验证的待办，影响已交付代码的可信度 |
| 🟠 P1 高 | 9 | 近期应完成的功能缺口与关键 Bug |
| 🟡 P2 中 | 18 | 已知中低优先级 Bug、功能增强 |
| 🟢 P3 低 | 10 | 延后项、可选重构、范围排除项 |
| ⚪ 远期 | 4 | AI Phase 3-6，依赖 MES/IoT 成熟 |
| **合计** | **44** | |

---

## 二、🔴 P0 紧急 - 运行时验证待办

> 已交付代码缺少运行时验证，需在真实环境中执行确认。

### P0-1 AI 模块 SQL 落库与端到端验证
- **来源**: [ai/plans/NexusERP_AI_Week12_Plan_S2D.md](./ai/plans/NexusERP_AI_Week12_Plan_S2D.md)、[ai/plans/NexusERP_AI_S2D_验证报告.md](./ai/plans/NexusERP_AI_S2D_验证报告.md)
- **待办**:
  - [ ] 执行 4 个 SQL 脚本: `ai-stage2-a.sql`、`ai-stage2-b.sql`、`ai-stage2-c.sql`、`ai-stage2-image.sql`
  - [ ] S2-A 端到端验证: 上传文档 → 构建图谱 → 验证实体/关系/社区数据 → 检索增强 → KnowledgeGraphTool
  - [ ] S2-B 回归: 8 厂商切换、thinking token 流、缓存键隔离、专用 Rerank vs LLM-as-judge
  - [ ] S2-C 回归: 联网搜索(Tavily/Bing)、MCP 市场刷新+加载、文生图(OSS)
  - [ ] 异步租户上下文运行时验证(3 文件 5 处修复后 SQL 验证 tenant_id)
  - [ ] 成本/性能基线采集(9 项指标)
- **阻塞原因**: 需真实环境 + LLM API Keys + OSS 配置
- **预估**: 2d

### P0-2 MES Phase 1 端到端运行时验证
- **来源**: [mes/plans/2026-06-19-mes-week21-verification-report.md](./mes/plans/2026-06-19-mes-week21-verification-report.md)
- **待办**:
  - [ ] Flowable BPMN 流程部署(MES 工单审批流，运行时 UI 操作)
  - [ ] MES 模块启动验证
  - [ ] 9 项端到端检查点: 菜单权限、MQ 联动、流转卡执行、工单完工聚合、MQ 回写 ERP
  - [ ] Phase 1 退出标准"演示通过"确认
- **预估**: 1d

### P0-3 MES-IoT-ERP 跨模块联动运行时验证
- **来源**: [system/plans/archive/2026-06-26-upgrade-wave1-plan.md](./system/plans/archive/2026-06-26-upgrade-wave1-plan.md)、Wave2
- **待办**:
  - [ ] MES-IoT 设备软链接运行时确认
  - [ ] IoT 自动归档 dc_data_record 端到端
  - [ ] SPC OOC → 安灯触发链路
  - [ ] ERP 收货审批 → MES IQC 自动创建(幂等)
  - [ ] 保养到期 SnailJob + SSE + 安灯(24h 限频)
- **预估**: 1d

---

## 三、🟠 P1 高 - 功能缺口与关键修复

### P1-1 ERP 库存预留前端页面
- **来源**: [system/plans/archive/2026-06-27-erp-phase-b-plan.md](./system/plans/archive/2026-06-27-erp-phase-b-plan.md) (后端已完成)
- **待办**: [ ] 库存预留前端管理页面(后端 Service/事务类型已就绪)
- **预估**: 1d

### P1-2 MES 不合格品处理流程 (MRB)
- **来源**: PROGRESS.md 未开发项
- **待办**: [ ] IQC/IPQC/OQC 不合格 → MRB 评审 → 返工/退货/报废处置流程
- **预估**: 2d

### P1-3 MES 质量追溯(批次→检验记录)
- **来源**: PROGRESS.md 未开发项
- **待办**: [ ] 批次/序列号 → 关联检验记录的正反向追溯
- **预估**: 1d

### P1-4 ERP SO 履行链路收尾
- **来源**: [erp/plans/archive/2026-07-07-so-fulfillment-linkage-plan.md](./erp/plans/archive/2026-07-07-so-fulfillment-linkage-plan.md)
- **待办**: [ ] 销售订单行 → 生产完工 → 入库联动运行时验证(后端已重构)
- **预估**: 1d

### P1-5 AI S2-D 文档闭环
- **来源**: [ai/plans/NexusERP_AI_Week12_Plan_S2D.md](./ai/plans/NexusERP_AI_Week12_Plan_S2D.md) Task 7
- **待办**:
  - [ ] AI README 已知问题 6 项更新
  - [ ] S2-D 验证报告补全运行时结果
  - [ ] 主计划 v6.1 中 S2-A 状态从"待运行时验证"更新为"已验证"(验证后)
- **预估**: 0.5d

### P1-6 AI 知识图谱已知限制处理
- **来源**: [ai/plans/archive/NexusERP_AI二阶段实施计划_S2A.md](./ai/plans/archive/NexusERP_AI二阶段实施计划_S2A.md)
- **待办**:
  - [ ] JSON 解析不稳定(影响图谱抽取可靠性)
  - [ ] 实体合并误判(同义实体未合并)
  - [ ] MySQL 1-2 跳性能(大图查询慢)
  - [ ] 并发构建竞态
- **预估**: 2d

### P1-7 AI 中优先级 Bug 修复(11 项)
- **来源**: [ai/plans/NexusERP_AI_S2D_验证报告.md](./ai/plans/NexusERP_AI_S2D_验证报告.md)
- **待办**:
  - [ ] S2-A-4: KnowledgeGraphService 缺 @Transactional
  - [ ] S2-A-5: 关系消歧义
  - [ ] S2-A-6: 并发构建竞态修复
  - [ ] S2-B-D: 末次 token 用量覆盖
  - [ ] S2-B-A: Rerank API fallback 误用 model
  - [ ] S2-C-4: client 锁
  - [ ] S2-C-5: InputStream 泄漏
  - [ ] S2-C-6: null tempUrl 静默
  - [ ] S2-C-7: 删除缺所有权校验
  - [ ] S2-C-8: WebSearchAgent 缺 ERP 工具
  - [ ] S2-C-9: 缺权限校验
- **预估**: 3d

### P1-8 MES 控制图趋势分析前端可视化
- **来源**: PROGRESS.md 进行中项
- **待办**: [ ] SPC 控制图前端趋势分析完善(后端数据点已就绪)
- **预估**: 1d

### P1-9 ERP 完工入库与计划状态流转运行时验证
- **来源**: [erp/plans/archive/2026-07-10-mes-completion-linkage-fix.md](./erp/plans/archive/2026-07-10-mes-completion-linkage-fix.md)
- **待办**: [ ] 工单完工 → 倒冲 + 入库 + 计划状态流转全链路运行时验证
- **预估**: 1d

---

## 四、🟡 P2 中 - 功能增强与低优先级 Bug

### P2-1 MES 设备 OEE 综合效率分析
- **待办**: [ ] 设备可用率 × 性能率 × 质量率 综合分析
- **预估**: 2d

### P2-2 MES 过程能力分析 (Cp/Cpk)
- **待办**: [ ] 基于 SPC 数据点计算 Cp/Cpk 并展示
- **预估**: 1d

### P2-3 IoT 设备 OTA 固件升级
- **待办**: [ ] 远程固件升级通道
- **预估**: 3d

### P2-4 IoT 设备分组/标签管理
- **待办**: [ ] 设备批量分组与标签
- **预估**: 1d

### P2-5 AI RAG + 知识图谱混合检索优化
- **待办**: [ ] GraphRAG 混合检索效果调优(当前 🚧)
- **预估**: 2d

### P2-6 AI Agent 多轮自主工具调用
- **待办**: [ ] LangChain4j Agentic 多轮工具调用(当前 🚧)
- **预估**: 3d

### P2-7 AI 低优先级 Bug 修复(12 项)
- **来源**: [ai/plans/NexusERP_AI_S2D_验证报告.md](./ai/plans/NexusERP_AI_S2D_验证报告.md)
- **待办**:
  - [ ] S2-A-7: regexFallback 字段顺序
  - [ ] S2-A-8: N+1 查询
  - [ ] S2-A-9: 无跳数限制
  - [ ] S2-B-F: 跨轮消息污染
  - [ ] S2-B-G: 尾斜杠
  - [ ] S2-B-H: JWT key split 脆弱
  - [ ] S2-B-I: HttpResponse 泄漏
  - [ ] S2-B-J: ThreadLocal 跨请求残留
  - [ ] S2-B-K: MiMo providerType
  - [ ] S2-C-10: 无 size 限制
  - [ ] S2-C-11: 每次新建 ObjectMapper
  - [ ] S2-C-12: String.intern 锁
- **预估**: 2d

### P2-8 IM 消息已读/未读状态
- **待办**: [ ] 私聊/群聊已读未读状态(当前 🚧)
- **预估**: 1d

### P2-9 ERP 按仓库/库区/库位过滤库存查询收尾
- **待办**: [ ] 库存清单多维过滤完善(当前 🚧，已部分实现)
- **预估**: 0.5d

### P2-10 AI S2-A v1.5 升级
- **来源**: [ai/plans/archive/NexusERP_AI二阶段实施计划_S2A.md](./ai/plans/archive/NexusERP_AI二阶段实施计划_S2A.md)
- **待办**:
  - [ ] 实体 gleaning(多轮抽取)
  - [ ] embedding 相似度匹配
  - [ ] Neo4j 存储策略(替代 MySQL)
  - [ ] Leiden 社区检测(替代标签传播)
- **预估**: 5d

### P2-11 ~ P2-18 其余增强项
| 编号 | 功能 | 预估 |
|------|------|------|
| P2-11 | AI 联网搜索/文生图仅 auto 路由模式限制解除 | 1d |
| P2-12 | AI MCP 市场刷新应用 auth_config | 0.5d |
| P2-13 | AI Agent 模式支持 thinking token 流 | 1d |
| P2-14 | MES el-table → vxe-table 迁移 | 2d |
| P2-15 | MES pro_sn → wm_sn 同步 | 1d |
| P2-16 | MES IoT 自动采集归档增强 | 1d |
| P2-17 | MES SPC 告警/安灯联动 | 1d |
| P2-18 | MQ 死信队列处理 | 1d |

---

## 五、🟢 P3 低 - 延后项与可选重构

### P3-1 ERP 3D 仓库 InstancedMesh 优化
- **来源**: [erp/plans/archive/2026-07-04-erp-wm-3d-warehouse-m2m3-plan.md](./erp/plans/archive/2026-07-04-erp-wm-3d-warehouse-m2m3-plan.md)
- **待办**: [ ] 库位 > 500 时启用 InstancedMesh 渲染(当前环境无法验证)
- **触发条件**: 真实大仓库数据
- **预估**: 2d

### P3-2 ERP 3D 仓库 composable 抽取
- **来源**: 同上
- **待办**: [ ] 3D 渲染逻辑抽取为 composable(纯重构，回归风险高于收益)
- **预估**: 1d

### P3-3 ERP 3D 仓库实施前确认项
- **待办**:
  - [ ] sys_menu 中 wm 仓库菜单 menu_id 确认
  - [ ] 3D 目标用户角色 wm:stock:list 权限分配
  - [ ] 大仓库压测库位尺寸一致性确认
- **预估**: 0.5d

### P3-4 ERP 部门请购 → 采购申请联动
- **待办**: [ ] PoRequisition → PuRequisition 转单
- **预估**: 1d

### P3-5 ERP 多仓库间自动调拨建议
- **待办**: [ ] 基于库存均衡的自动调拨建议
- **预估**: 2d

### P3-6 ERP WiFi/蓝牙位置验证打卡
- **待办**: [ ] 打卡位置范围校验
- **预估**: 2d

### P3-7 ERP 考勤异常自动告警
- **待办**: [ ] 迟到/早退/缺卡自动告警
- **预估**: 1d

### P3-8 Pay 对账功能
- **待办**: [ ] 支付订单与第三方对账
- **预估**: 2d

### P3-9 Report 自定义数据源报表
- **待办**: [ ] 积木报表自定义数据源
- **预估**: 1d

### P3-10 Workflow 高级审批功能
- **待办**:
  - [ ] 自定义审批规则引擎
  - [ ] 催办/加签/转办
- **预估**: 5d

### P3-11 MES 范围排除项(按需)
- **来源**: 各 MES 周计划标注的范围排除
- **待办**:
  - [ ] ERP SN 规则引擎复用
  - [ ] TDengine 时序库迁移
  - [ ] 工序派工调度优化
  - [ ] 设备备件管理
- **预估**: 按需

---

## 六、⚪ 远期规划 - AI Phase 3-6

> 依赖 MES/IoT 模块运行时成熟后启动。详见 [ai/plans/NexusERP_AI开发计划.md](./ai/plans/NexusERP_AI开发计划.md) v6.1。

| 阶段 | 周次 | 主题 | 核心交付 |
|------|------|------|----------|
| Phase 3 | Week 23-30 | MES 智能赋能 | MesProductionTool、MesQualityTool、MesEquipmentTool、MesTraceTool、MesAnalysisAgent、MES 知识库 |
| Phase 4 | Week 31-38 | IoT 智能赋能 | IotDeviceTool、IotAlertTool、IotMonitorAgent、预测性维护、IoT 知识库 |
| Phase 5 | Week 39-44 | 跨系统融合决策 | CrossSystemTool(CTP/ATP)、动态成本分析、质量闭环追溯、供应链风险 |
| Phase 6 | Week 45+ | AI 工作流演进 | LangGraph4j 工作流引擎、自动补货、质量异常处理、设备故障响应、月度经营分析 |

**前置条件**:
- [ ] P0-1 ~ P0-3 运行时验证完成
- [ ] MES/IoT 端到端稳定运行
- [ ] ai_token_usage、ai_workflow 表落库

---

## 七、执行建议

### 近期(本周)
1. 优先完成 **P0-1 ~ P0-3** 运行时验证 - 这是已交付代码可信度的基础
2. 同步推进 **P1-1** 库存预留前端(后端已就绪，投入小收益快)

### 中期(2-3 周)
3. 集中清理 **P1-7** AI 中优先级 Bug(11 项)
4. 完成 **P1-2、P1-3** MES 质量闭环(MRB + 追溯)
5. 推进 **P2-5、P2-6** AI 检索与 Agent 增强

### 远期(1 月+)
6. 视 MES/IoT 运行时稳定性，启动 **AI Phase 3**
7. 按需推进 P3 延后项

---

## 八、原始计划文档归档说明

以下原始计划文档已完成使命，归档至各模块 `plans/archive/`:
- AI: Week2~Week11 周计划、S2A/S2B_S2C 实施计划(代码已完成)
- MES: Week17~Week27 实施计划(代码已完成)
- ERP: 5 个计划(均已实施)
- IoT: Week33~Week36(阶段一已验收)
- System: Wave1/Wave2/PhaseB(均已实施)
- IM: 聊天优化计划(已实施)

**保留不归档**:
- 各模块 MASTER 主计划(仍含远期规划)
- AI S2-D 验证报告(含未修复 Bug 清单，P1-7/P2-7 依据)
- MES Week21 验证报告(P0-2 依据)
