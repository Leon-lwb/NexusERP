# 第二梯队开发计划：AI+工业软件赛道冲奖（APS/PdM 之后的深化）

> 分支: `feat/aps-intelligent-scheduling` | 创建: 2026-09-02 | 执行方式: **分段执行**
> 前置: 第一梯队已完成（APS 智能排产 + PdM 预测性维护，见 `doc/erp/plans/archive/2026-08-30-tier1-aps-pdm-plan.md`）
> 本文档是第二梯队的总计划：已完成的项标记 ✅ 并附 commit，未完成的项给出**可直接开工的分段任务清单**，逐段执行、逐段提交。

---

## 总览

| # | 任务 | 状态 | commit | 赛道呼应 |
|---|------|------|--------|----------|
| S0 | 排产 Agent 工具入口（AI 从查询升级到决策） | ✅ 已完成 | `eac1f9bd` | 方向02 + AI 决策 |
| S1 | SPC 补 Cp/Cpk 过程能力分析 | ✅ 已完成 | `a9e4bf52` | 方向03 质量检测 |
| S2 | QC 检验标准自动判定 | ✅ 已完成 | `932a1b0a`+`70530eeb`+`b72a6490` | 方向03 核心短板 |
| S3 | 供应链缺料风险预测 | ✅ 已完成 | `caf3eabf`+`1140fab6`+`7adefe00` | 赛道正文点名 |

**执行约定**: 每个 Task 完成后编译验证（`mvn compile -pl ruoyi-modules/ruoyi-mes -DskipTests -o`）+ 前端类型检查（`cd plus-ui && npm run type-check:changed`）+ 独立 commit。Task 之间无强依赖，可跳段，但同 Task 内步骤有顺序。

---

## Part A. 已完成项摘要（验收要点）

### S0 排产 Agent 工具（commit eac1f9bd）
- `ApsScheduleTool`（工具名 `aps_schedule`）4 个 `@Tool`：排产方案列表 / 拖期工单清单 / 瓶颈资源利用率 / 工单工序排程明细；全部参数化查询 + 租户隔离
- 甘特图抽屉"AI 解读本方案"按钮 → `AiChatDrawer.open(prompt)` 预填分析指令
- **验收**: 执行 `script/sql/update/2026-09-02-ai-aps-tool.sql`（ry-vue 库 ai_tool 表注册）→ 重启 → AI 对话框问"下周哪些订单会拖期？"应看到 Agent 调用 aps_schedule 工具
- 已知边界: ai 工具走 `@DS("erp")` 数据源，**MES/IoT 库的数据 AI 暂不可查**（SPC/健康数据若要进 AI 需扩展跨库查询管道，暂不做）

### S1 SPC Cp/Cpk（commit a9e4bf52）
- `SpcCapabilityCalculator`：R̄/d2 与 S̄/c4 两种组内σ估计；Cp/Cpk/等级（A+≥1.67 / A≥1.33 / B≥1.0 / C<1.0）
- 控制图新增 usl/lsl（用户维护）+ cp/cpk/cpk_grade（analyze 回写）
- **验收**: 执行 `script/sql/update/2026-09-02-spc-cpk.sql`（ry-mes 库，chart2 已种子 10±0.05 规格限）→ 重启 → SPC 控制图列表出现 Cpk/能力等级列；编辑维护 USL/LSL 后点"分析"，结果对话框显示 Cp/Cpk/等级
- 单测 5/5（`SpcCapabilityCalculatorTest`）

---

## Part B. S2 QC 检验标准自动判定（方向03 核心短板）

### 目标
把 IQC/IPQC/OQC 从"人工录入判定结果"升级为"系统按检验标准自动判定"：
检验模板（检验项目 + 标准值 + 抽样方案）→ 检验单生成时带出项目明细 → 录入实测值 → **系统比对标准自动判定合格/不合格** → 判定结果仍走现有 NCR/MQ 回写链路（零改动）。

### 现状约束（开工前必读）
- `qc_iqc/qc_ipqc/qc_oqc` 三张主表已存在，`inspect()` 状态机 + NCR 自动建单 + MQ 回写全部真实可用（不要动）
- 调研确认: **无检验项目表**，`inspect()` 的 QUALIFIED/UNQUALIFIED 由人工传入
- `PrdScheduleTaskVo` 的教训: 新 VO 必须加 `@AutoMapper`；驼峰带数字的字段（如 xxx90d）必须 `@TableField` 显式列名
- MES 库表统一 `@DS("mes")`、TenantEntity、`del_flag` 逻辑删除

### Task 1: 数据模型 + SQL（半天）
1. 新表 `qc_inspect_template`（检验模板头）: templateId, templateCode(唯一), templateName, templateType(IQC/IPQC/OQC), materialId/materialCode/materialName(可空=通用), versionNo, status(DRAFT/ACTIVE), remark + 审计列
2. 新表 `qc_inspect_item`（检验项目）: itemId, templateId, itemCode, itemName, dataType(QUANTITATIVE/QUALITATIVE), unit, standardType(NOMINAL/LSL_ONLY/USL_ONLY/LSL_USL), nominalValue, lslValue, uslValue, tolUpper, tolLower(名义值±公差), aqlLevel(II等), samplePlanCode(如 AQL1.0), criticality(CRITICAL/MAJOR/MINOR), seqNum, enabled, remark + 审计列
3. 新表 `qc_inspect_detail`（检验单明细）: detailId, checkId(关联 qc_iqc/ipqc/oqc), checkType(IQC/IPQC/OQC), itemId, itemCode, itemName, dataType, standardDesc(冗余快照"10±0.05mm"), sampleQty, measuredValue(实测均值/最大最小), judgeResult(PASS/FAIL/PENDING), remark + 审计列
4. 增量脚本 `script/sql/update/2026-09-02-qc-inspect-standard.sql` + 权威 schema `ry-mes.sql` 同步 + 模板/按钮菜单（挂在现有质检菜单下，找空闲 menu_id，**执行前 grep 确认**）
5. 种子: 给现有 IQC 物料造 1 个含 2 个定量项目的 ACTIVE 模板（演示闭环）

### Task 2: 检验模板 CRUD（1 天）
1. 实体 `QcInspectTemplate`/`QcInspectItem` + bo/vo（**VO 加 @AutoMapper**）+ mapper（BaseMapperPlus）
2. `IQcTemplateService`: 模板分页/详情(带项目)/新增/修改/删除(校验无引用)/启用停用; 版本复制(copy 生成新版本)
3. Controller `/mes/qc/template`: 标准 CRUD + item 子表随模板一起保存(JSON 整体提交)
4. 前端: `plus-ui/src/api/mes/qc/template.ts` + `src/views/mes/qc/template/index.vue`（模板列表 + 抽屉编辑项目明细表格，参考 `maintenance` 页面的主子表模式）

### Task 3: 判定引擎 + 检验单明细生成（1 天）
1. 纯函数判定器 `QcJudgeEngine`（放 `qc/service`，可单测）:
   - 定量: standardType=LSL_USL → `lsl ≤ measured ≤ usl`; NOMINAL → `nominal−tolLower ≤ measured ≤ nominal+tolUpper`; 单边同理
   - 定性: measuredValue ∈ 合格枚举（"OK"/"合格"）→ PASS
   - 明细级 PASS/FAIL → 单级判定: 全 PASS=QUALIFIED, 任一 CRITICAL 项 FAIL=UNQUALIFIED(整单拒收), 仅 MAJOR/MINOR FAIL=PARTIAL
2. 单测 `QcJudgeEngineTest`: 各 standardType 边界值、CRITICAL 一票否决、PARTIAL
3. `QcInspectDetailService`: 检验单创建时（IqcAutoCreateConsumer 或人工建单处）按 物料+templateType 匹配 ACTIVE 模板（物料专属优先，无则通用模板），快照生成明细行（judgeResult=PENDING）
4. 兼容: 无匹配模板的检验单保持现状（无明细、人工判定），**不阻断现有流程**

### Task 4: 明细录入 + 自动判定接入 inspect()（1 天）
1. `IqcServiceImpl.inspect()` 改造: 请求 BO 增加 `details` 列表（itemId + measuredValue）；
   - 有明细: 逐条调 `QcJudgeEngine` 判 PASS/FAIL 落库 → 汇总出单级 QUALIFIED/UNQUALIFIED/PARTIAL → 继续走原有 NCR/MQ 逻辑
   - 无明细: 兼容旧行为（人工传入的 inspectResult 直接用）
2. Controller: 检验单详情接口附明细列表（`selectDetailById` 返回 header+details）
3. 前端质检页(iqc/index.vue 优先): 详情对话框加"检验项目"标签页——列出项目/标准/输入框，录完点"提交判定"调新 inspect；判定后每项显示 PASS/FAIL 色点。IPQC/OQC 同构改造（可复用同一明细组件）

### Task 5: AQL 抽样量提示（半天，可选）
1. `QcAqlCalculator`: GB/T 2828.1 常用表字面量（批量区间 × 检验水平 II × AQL → 样本量字码与 n）
2. 明细生成时按 lotQty 自动算 sampleQty 填入；前端只读展示
3. 单测覆盖 3 个典型批量档

### 提交划分
- commit 1: Task 1+2（SQL+模板 CRUD）
- commit 2: Task 3+4（判定引擎+接入）
- commit 3: Task 5（AQL，如做）

---

## Part C. S3 供应链缺料风险预测（赛道正文点名）

### 目标
MRP 净需求（已有）→ 结合在途采购 + 安全库存 → **推演未来 N 天逐日缺料时间线** → 风险清单页面（最早缺料日/缺口/建议动作）+ Agent 工具（`erp_shortage_risk`，走 @DS("erp") 天然可达）。

### 现状约束
- `PrdMaterialRequirementServiceImpl.calculateNetRequirements` 已有: 净需求 = 毛需求 −(现有库存−安全库存)
- 在途采购: `pu_purchase_order` + 行表有 receivedQty/orderedQty 可算在途
- 数据都在 ry-erp 库 → **Agent 工具可直接查**（与 aps_schedule 同模式）

### Task 6: 缺料推演引擎（1 天）
1. 纯函数 `PrdShortageSimulator`（erp/prd/schedule 或 prd/service 下，可单测）:
   - 输入: 各物料 [需求日期→毛需求]（MRP 明细按 requiredDate 聚合）、当前库存、安全库存、在途([预计到货日→数量])
   - 逐日推演: 余额 = 库存 + Σ(截至当日到货) − Σ(截至当日需求) − 安全库存; 首次余额 < 0 的日期 = 最早缺料日, 最深负值 = 最大缺口
   - 输出: 物料级 [最早缺料日, 最大缺口, 缺口天数, 建议动作(催货/提前采购/调安全库存)]
2. 单测: 无缺口、恰好踩线、在途迟到导致缺口、多批次需求

### Task 7: Service + 接口（1 天）
1. `IPrdShortageService.predict(days)`: 取 MRP 净需求明细(未满足部分) + 库存 + 在途 → 引擎推演 → 结果缓存 Redis 30min（key 含租户）
2. Controller `/erp/prd/shortage/predict`（权限 `prd:shortage:query`）+ 菜单按钮 SQL
3. 不落库（推演型），结果 VO: materialCode/Name, earliestDate, maxGap, gapDays, onOrderQty, suggestion

### Task 8: 前端风险看板（1 天）
1. `views/erp/prd/shortage/index.vue`: 按最早缺料日升序的风险表格（红色=7 天内、橙色=30 天内）+ 趋势说明
2. 行操作: "去催货"跳采购订单页、"生成 AI 分析"调 AiChatDrawer.open(prompt)

### Task 9: Agent 工具（半天）
1. `ErpShortageRiskTool`（工具名 `erp_shortage_risk`）: 读 Redis 缓存(无则触发 predict)格式化 Markdown 表 + `ErpShortageRiskToolProvider` + `ai_tool` 种子 SQL
2. 验收: "未来两周有哪些物料会缺料？" → Agent 调工具 → 风险表 + LLM 建议采购提前期

### 提交划分
- commit 1: Task 6+7（引擎+接口）
- commit 2: Task 8（前端）
- commit 3: Task 9（Agent 工具）

---

## 附: 全部增量脚本执行清单（按顺序，全部 ry-vue/ry-erp/ry-mes 对应库）

| 脚本 | 库 | 状态 |
|---|---|---|
| `2026-08-30-aps-schedule.sql` | ry-erp + ry-vue | ✅ 已执行 |
| `2026-08-30-pdm-equipment-health.sql` | ry-mes + ry-vue | ✅ 已执行 |
| `2026-09-02-ai-aps-tool.sql` | ry-vue | ⬜ 待执行 |
| `2026-09-02-spc-cpk.sql` | ry-mes | ⬜ 待执行 |
| `2026-09-02-qc-inspect-standard.sql` | ry-mes + ry-vue | ⬜ 已生成待执行(分段: 建表+种子→ry-mes, 菜单→ry-vue) |
| `2026-09-02-erp-shortage-risk.sql` | ry-vue | ⬜ 已生成待执行(菜单+ai_tool 同库) |

> 注意: 混合脚本（建表在业务库+菜单在系统库）不能整文件灌进一个库——按注释分段执行，或参考已有的拆分执行方式。

## 附: SnailJob 任务注册提醒
- `equipmentHealthTask`（设备健康日评，建议 `0 0 7 * * ?`）— 未注册则只有手动评估
