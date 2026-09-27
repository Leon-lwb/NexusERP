# 工业交付风险 40 案例模型凭据修复后真实重跑（2026-09-27）

> **40/40 业务 ID/编号通过只读 ERP 核对并发起真实分析；黄金契约 16/40 PASS（40.0%）。**
> 此报告单独保存本轮运行，不覆盖此前模板降级基线。真实 LLM 节点成功与最终摘要模式/业务契约通过是不同指标，不能互相替代。

## 实测环境与口径

- 测前核验：`2026-09-27T18:03:00.876190+08:00`；原始 Runner 报告完成时间：**2026-09-27 18:23:07 +08:00**。租户 `000000`，新系统库 `nexus_ai_decision_rerun_20260927_sys`（198 张表、测前 0 条决策运行）。
- 后端部署工件源提交 `68e04aed`，Runner 修复提交 `038a846b`，模型配置修复核验提交 `bdc5a206`；凭据不在仓库。`ruoyi-admin.jar` SHA256：`23be68776d7ac2416d4e9945dc0a0d8017b54af50ecd6d039722c2ccd9c88d45`。
- 默认 TEXT 模型固定为 `2103871286244564993` / `deepseek-v4.1-flash`，协议 `OPENAI_RESPONSES`。只复制对应厂商/模型配置及厂商密文；目标后端连接测试 `success=true`，**1142 ms**，没有读取/公开 Key 明文。
- 独立 MySQL 5.7.14 `127.0.0.1:13307`、演示 ERP 库 `nexus_ai_decision_demo_erp`、Redis `6385`、RabbitMQ `5685`；基线应用 `dev,decision-demo` 在 `8081`，仅本进程将活跃运行上限调为 48。未读取/写入 3306 或 plus 库，原 8080 未重配。
- **8081 与原 8080 共享这套非 plus 的演示 ERP/Redis/RabbitMQ，不是各自独占资源。**系统库干净新建，但 ERP 沿用原演示 seed，本轮未 reset/reseed，源日期保持原样；因此不是完全独占、冷启动或并发压测。
- 顺序执行 `DELIVERY-001..040`，先 GET `/erp/prd/work/{bizId}` 独立核对精确 ID/编号，再 POST 分析、每 500ms 查询运行；保持原 **90 秒**终态验收上限、既定 `expected-results.jsonl`，超时即 FAIL，不为提高通过率改变参数/黄金期望。只有模型连接测试预热，没有额外工单分析混入本轮。
- 固定输入 SHA256：`cases.jsonl=f5f0d089e51130a84156fb77c441dd53aa433fb9dad87ba0abf08bdee32fd3d3`；`expected-results.jsonl=e05859947ea468cc14dd5cb99c55f3ced0f0b62370bc1361dbb37da1587275c8`。测试结束重新核对，两文件及部署 JAR 均未变化。
- 20 张 ERP seed 来源表测试前后校验：**一致**；所有审批/执行/回滚端点未被 Runner 调用。旧系统库/报告不 reset，不以覆盖历史方式重新计算通过率。

## 结果与性能

| 指标 | 本轮实测 |
|---|---|
| 独立可解析业务 / 已发起 / 最终对账运行数 | 40 / 40 / 40 |
| 黄金契约通过 | **16/40（40.0%）**；失败 24；Runner 退出码按契约为 1 |
| 数据库最终运行状态 | FAILED 10；SUCCEEDED 5；WAITING_APPROVAL 25 |
| LLM 节点状态（仅本轮 40 个实例） | SKIPPED 1；SUCCEEDED 39 |
| LLM 失败分类 | 无；不公开上游错误原文/凭据 |
| HTTP 验收超时 / 未启动 | 0 / 0；40 条均在 90 秒上限内被观察到终态 |
| 摘要模式（独立指标） | NULL 10；TEMPLATE_FALLBACK 30 |
| 请求→验收终态 P50/P95（仅 16 个 PASS，最近秩法） | **32.417 / 41.088 秒**；含 ERP 核对、请求、规则/仿真、模型及轮询，并非纯 LLM 时延 |
| 40 条请求→Runner 停止观察 P50/P95（包括 FAIL） | 31.095 / 41.088 秒；若超时则计至验收截止，不冒充后续真实完成耗时 |
| 成功 LLM 节点 P50/P95（39 条） | 30.400 / 42.064 秒；只统计成功模型节点，与请求→终态分位数口径不同 |
| 成功模型 Token 总量 | 输入 81741；输出 310670（不记录密钥和生成正文） |
| 建议动作及实际执行状态 | 45 条；NOT_STARTED 45；禁止动作命中 0 |

### 与原降级基线的对比

| 维度 | 原 2026-09-27 降级基线 | 本轮 |
|---|---|---|
| 契约通过 | 16/40（40.0%） | 16/40（40.0%） |
| LLM 节点 | 39 次鉴权失败、1 次上游失败跳过 | SKIPPED 1；SUCCEEDED 39 |
| 请求→终态 PASS P50/P95 | 2.216 / 2.385 秒（鉴权失败后模板降级） | 32.417 / 41.088 秒 |
| 解释 | 不含成功 LLM 正常生成，不可宣传为模型性能 | 本轮指标须结合上述 LLM 状态与摘要模式；两轮来源相同但预热/系统库不同，不作“性能提升”推断 |

## 逐例真实结果

| 案例 | 运行 ID | Runner 观察 → 数据库终态 | 契约 | 请求耗时(s) | LLM 状态/耗时(s) | 主要差异 |
|---|---|---|---|---:|---|---|
| DELIVERY-001 | `2104149808007753729` | SUCCEEDED → SUCCEEDED | **PASS** | 29.141 | SUCCEEDED / 27.253 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-002 | `2104149929609015298` | SUCCEEDED → SUCCEEDED | **PASS** | 32.417 | SUCCEEDED / 31.555 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-003 | `2104150065579962370` | SUCCEEDED → SUCCEEDED | **PASS** | 33.382 | SUCCEEDED / 32.763 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-004 | `2104150205598412801` | SUCCEEDED → SUCCEEDED | **PASS** | 33.895 | SUCCEEDED / 33.147 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-005 | `2104150347734986753` | SUCCEEDED → SUCCEEDED | **PASS** | 23.347 | SUCCEEDED / 22.340 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-006 | `2104150445671985153` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 23.878 | SUCCEEDED / 23.087 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-007 | `2104150545806798849` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 38.043 | SUCCEEDED / 37.252 | recommendation: APS_EDD → NULL |
| DELIVERY-008 | `2104150705370705922` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 31.114 | SUCCEEDED / 30.236 | recommendation: APS_EDD → NULL |
| DELIVERY-009 | `2104150835872280577` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 31.606 | SUCCEEDED / 30.821 | recommendation: APS_EDD → NULL |
| DELIVERY-010 | `2104150968441647105` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 31.095 | SUCCEEDED / 30.400 | recommendation: APS_EDD → NULL |
| DELIVERY-011 | `2104151098855141378` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 35.406 | SUCCEEDED / 34.726 | recommendation: APS_EDD → NULL |
| DELIVERY-012 | `2104151247362863105` | FAILED → FAILED | **FAIL** | 34.384 | SUCCEEDED / 33.916 | 黄金要求成功分析，实际在 actionProposal 失败 |
| DELIVERY-013 | `2104151391575617538` | FAILED → FAILED | **FAIL** | 26.943 | SUCCEEDED / 26.149 | 黄金要求成功分析，实际在 actionProposal 失败 |
| DELIVERY-014 | `2104151504578555905` | FAILED → FAILED | **FAIL** | 25.833 | SUCCEEDED / 25.459 | 黄金要求成功分析，实际在 actionProposal 失败 |
| DELIVERY-015 | `2104151612921622529` | FAILED → FAILED | **FAIL** | 29.052 | SUCCEEDED / 28.774 | 黄金要求成功分析，实际在 actionProposal 失败 |
| DELIVERY-016 | `2104151734787125249` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 42.732 | SUCCEEDED / 42.064 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-017 | `2104151914026512386` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 47.510 | SUCCEEDED / 46.918 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-018 | `2104152113285312513` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 29.089 | SUCCEEDED / 28.146 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-019 | `2104152235301810178` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 28.586 | SUCCEEDED / 27.592 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-020 | `2104152355195990017` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 24.327 | SUCCEEDED / 23.611 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-021 | `2104152457239212034` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 33.815 | SUCCEEDED / 32.840 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-022 | `2104152599057018882` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 29.603 | SUCCEEDED / 28.965 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-023 | `2104152723229388802` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 31.704 | SUCCEEDED / 31.025 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-024 | `2104152856205602817` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 25.379 | SUCCEEDED / 24.431 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-025 | `2104152962657038338` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 36.466 | SUCCEEDED / 35.691 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-026 | `2104153115598139393` | FAILED → FAILED | **FAIL** | 22.175 | SUCCEEDED / 21.927 | FAILED 且零动作仍需验证拒绝原因；通用错误不能算预期拒绝通过 |
| DELIVERY-027 | `2104153208606830594` | FAILED → FAILED | **FAIL** | 23.775 | SUCCEEDED / 23.092 | FAILED 且零动作仍需验证拒绝原因；通用错误不能算预期拒绝通过 |
| DELIVERY-028 | `2104153308334796801` | FAILED → FAILED | **FAIL** | 17.467 | SUCCEEDED / 16.886 | FAILED 且零动作仍需验证拒绝原因；通用错误不能算预期拒绝通过 |
| DELIVERY-029 | `2104153381596704770` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 25.932 | SUCCEEDED / 25.259 | recommendation: APS_EDD → APS_CR |
| DELIVERY-030 | `2104153490371784706` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 35.900 | SUCCEEDED / 35.069 | recommendation: APS_EDD → APS_CR |
| DELIVERY-031 | `2104153640888578049` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 36.963 | SUCCEEDED / 36.318 | recommendation: APS_EDD → APS_CR |
| DELIVERY-032 | `2104153795977162754` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 37.450 | SUCCEEDED / 36.856 | recommendation: APS_EDD → APS_CR |
| DELIVERY-033 | `2104153953049653249` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 41.088 | SUCCEEDED / 40.058 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-034 | `2104154125326495745` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 37.418 | SUCCEEDED / 36.582 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-035 | `2104154282331877378` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 33.726 | SUCCEEDED / 32.865 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-036 | `2104154423784779777` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 34.276 | SUCCEEDED / 33.455 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-037 | `2104154567531966465` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 26.902 | SUCCEEDED / 25.886 | 结构化状态、风险、根因、推荐与动作全部匹配 |
| DELIVERY-038 | `2104154680383909890` | FAILED → FAILED | **FAIL** | 19.481 | SUCCEEDED / 19.224 | FAILED 且零动作仍需验证拒绝原因；通用错误不能算预期拒绝通过 |
| DELIVERY-039 | `2104154762038620162` | FAILED → FAILED | **FAIL** | 0.584 | SKIPPED / 0.000 | FAILED 且零动作仍需验证拒绝原因；通用错误不能算预期拒绝通过 |
| DELIVERY-040 | `2104154764492288001` | FAILED → FAILED | **FAIL** | 23.688 | SUCCEEDED / 23.224 | FAILED 且零动作仍需验证拒绝原因；通用错误不能算预期拒绝通过 |

## 失败分类与下一步

- **14 条**：风险/置信度/推荐方案不符。
- **4 条**：状态不匹配：期望 WAITING_APPROVAL，实际 FAILED。
- **6 条**：失败原因不符或失败运行产生了动作。

- 模型凭据问题要以真实 LLM 节点/鉴权分类核验；即使 LLM 成功而摘要仍 `TEMPLATE_FALLBACK`，也不能算摘要解析/Guard 验收完成。
- 推荐方案/根因/动作不匹配须逐案核对 seed、真实可行约束、排序及黄金设计；**不得把不可行方案改成推荐或静默修改黄金**。
- 缺证据的负例必须提供稳定、可核验且脱敏的拒绝代码；只返回通用 `DECISION_NODE_FAILED` 不能按黄金要求记为预期拒绝 PASS。
- CONTROL-001..005 本轮仍只在 `DeliveryDecisionBenchmarkTest` 的 53 项服务级单测中通过，不等于真实 HTTP 人工越权/审批/幂等/故障注入验证；本轮没有执行业务动作。
- Runner 模拟 HTTP 11 项、离线 fixture 12 项通过。所有实测时延、失败及模型统计来自本轮 HTTP/隔离数据库对账，不用服务桩数字替代。

原始 Runner 输出位于本地 `reports/latest.md`（Git 忽略），40 个真实运行/节点轨迹保存在本轮独立系统库，可按表中 ID 对账。旧的 [16/40 降级基线](2026-09-27-isolated-40-baseline.md) 与 [模型凭据单案例修复核验](2026-09-27-model-credential-verification.md) 均保留；本报告不是未来 2026-10 竞赛正式回执。

## 收尾与保留现场

仅停止本次临时 8081 后端并结束持有临时 Token 的测试会话；原 8080 后端仍返回 HTTP 200，四个既有演示库及专用 Redis/RabbitMQ 再次通过权限/运行预检。本轮新系统库保留 40 条决策与节点轨迹，供按运行 ID 复核；未删除、覆盖或重置旧基线库。
