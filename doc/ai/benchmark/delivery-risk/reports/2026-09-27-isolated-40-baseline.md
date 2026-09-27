# 工业交付风险 40 案例真实 HTTP 基线（2026-09-27）

> **结果：40/40 个隔离 ERP 工单完成 ID/编号核对、40/40 个分析请求到达终态；仅 16/40 个与既定黄金期望一致。**
> 本次为本机隔离环境的**模板降级模式**实测，不是“40/40 通过”、不是正常 LLM 推理性能、更不是未来 2026-10 的竞赛最终验收。

## 环境与可重复口径

- 测量完成时间：**2026-09-27 13:30:22 +08:00**；后端编译自提交 `68e04aed`（Benchmark Runner 增补同次修订，见本报告所在分支）。租户 `000000`。
- 新建的干净系统库 `nexus_ai_decision_benchmark_sys`（从 `script/sql/ry-vue.sql` 初始化，198 张表，测前决策运行数 0），业务库 `nexus_ai_decision_demo_erp`：40 个唯一 DEMO 工单。它们都在独立 MySQL 5.7.14 `127.0.0.1:13307`；Redis `6385`、RabbitMQ `5685` 为独立演示容器；不读写 3306/plus。
- 安全前置核验曾发现独立 13307 被手动以 `skip-grant-tables` 状态启动；**在任何基线测量之前**先仅关闭该隔离进程，再由准备器按 `--no-defaults` 启动原独立数据目录，并验证受限账号对非演示表访问被拒绝、plus schema 不存在。未经权限校验的环境没有参与本报告。
- 本次后端 `dev,decision-demo` 在 `8081`，仅对该测量进程将 `nexus.ai.decision.max-concurrent-runs` 调为 48，避免“待审批”占满默认的 4 个运行槽；没有自动审批、执行或回滚。原 8080 后端继续保持原配置。 **8081 与原有 8080 共用这套隔离演示 ERP/Redis/RabbitMQ，而非各自独占资源**；故此处“隔离”仅指与 plus/生产库隔离，不可当作完全独占的竞赛压测环境。
- 新库初始化后先做一次 `DELIVERY-001` 烟测（通过），随后的 40 案例 Runner 对每例重新 POST `forceRestart=true`；因此本次是**顺序、热身后**的请求→终态耗时，不是冷启动或并发压测。ERP 种子由前期 `CURDATE()` 导入，未在本轮重置，故时间敏感结果须结合实际库中日期核对。
- 每例以只读 `GET /erp/prd/work/{bizId}` 在 ERP 独立验证精确字符串 ID/编号，再 POST `/ai/decision/delivery-risk/run` 并每 500 ms GET `/ai/decision/run/{runId}`，最多等待 90 秒；按独立 `expected-results.jsonl` 比较状态、风险、置信度、根因、方案、动作/禁止动作。系统运行记录未填 `bizCode` 时**不得**伪造：以已验证的 ERP 工单编号为准；记录若有非空编号必须一致。
- 记录证据：`reports/latest.md` 为本地原始 Runner 输出（保留本地，不提交敏感运行记录）；下表运行 ID/动作状态由只读查询隔离系统库逐例对账。服务级 `DeliveryDecisionBenchmarkTest` **53 测试、0 失败**；Runner 模拟 HTTP **11 测试、0 失败**。

## 实测摘要与限制

| 指标 | 实测 |
|---|---|
| 业务 ID/编号独立可解析 / 已发起 / 已到终态 | 40 / 40 / 40 |
| 完整黄金契约通过 | **16/40（40.0%）**；Runner 退出码 1，剩余 24 例均保留 FAIL |
| 终态分布（本轮各业务最新运行） | SUCCEEDED 5；WAITING_APPROVAL 25；FAILED 10 |
| 失败分类 | 推荐方案不匹配 14；意外节点失败 4；预期拒绝但错误原因不可核实 6 |
| HTTP 请求→终态 P50/P95（仅 16 个 PASS，最近秩法） | **2.216 / 2.385 秒**；仅反映**模型鉴权失败后的模板降级链路**，不是 LLM 正常推理耗时，也不是 40 例全量分位数 |
| LLM / 摘要 | 本轮 39 个 LLM 节点失败（抽查为模型凭据认证失败），`039` 在上游失败而 LLM SKIPPED；30 条完成分析并标记 `TEMPLATE_FALLBACK`，另 10 条分析失败无摘要 |
| 动作安全 | 45 条建议动作均 `NOT_STARTED`；未执行审批/执行/回滚；未发现黄金集合中的禁止动作进入建议清单。六条预期拒绝均无动作，但服务端只给通用错误码，**不能算预期拒绝通过** |

## 逐例结果（运行 ID 为本轮真实 ID）

| 案例 | 运行 ID | 期望 → 实际状态 | 结果 | 耗时(s) | 主要差异 |
|---|---|---|---|---:|---|
| DELIVERY-001 | `2104080864223842306` | SUCCEEDED → SUCCEEDED | **PASS** | 2.385 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-002 | `2104080874000764929` | SUCCEEDED → SUCCEEDED | **PASS** | 2.293 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-003 | `2104080883626692609` | SUCCEEDED → SUCCEEDED | **PASS** | 2.241 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-004 | `2104080893013544961` | SUCCEEDED → SUCCEEDED | **PASS** | 2.263 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-005 | `2104080902492672001` | SUCCEEDED → SUCCEEDED | **PASS** | 2.260 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-006 | `2104080911975993345` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 2.258 | 真实推荐 APS_CR_EXPEDITE 可行；黄金 APS_EDD 因缺料不可行 |
| DELIVERY-007 | `2104080921404788737` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 2.209 | 全部方案缺料不可行，真实无推荐；黄金要求 APS_EDD |
| DELIVERY-008 | `2104080930707755009` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 2.227 | 全部方案缺料不可行，真实无推荐；黄金要求 APS_EDD |
| DELIVERY-009 | `2104080940040081410` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 2.246 | 全部方案缺料不可行，真实无推荐；黄金要求 APS_EDD |
| DELIVERY-010 | `2104080949464682498` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 2.250 | 全部方案缺料不可行，真实无推荐；黄金要求 APS_EDD |
| DELIVERY-011 | `2104080958906060801` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 2.222 | 全部方案缺料不可行，真实无推荐；黄金要求 APS_EDD |
| DELIVERY-012 | `2104080968221609986` | WAITING_APPROVAL → FAILED | **FAIL** | 1.664 | 动作建议节点失败；非预期 FAILED，未产生动作 |
| DELIVERY-013 | `2104080975205126145` | WAITING_APPROVAL → FAILED | **FAIL** | 1.656 | 动作建议节点失败；非预期 FAILED，未产生动作 |
| DELIVERY-014 | `2104080982138310658` | WAITING_APPROVAL → FAILED | **FAIL** | 1.664 | 动作建议节点失败；非预期 FAILED，未产生动作 |
| DELIVERY-015 | `2104080989151186946` | WAITING_APPROVAL → FAILED | **FAIL** | 1.651 | 动作建议节点失败；非预期 FAILED，未产生动作 |
| DELIVERY-016 | `2104080996084371457` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 2.213 | 真实推荐 APS_CR_EXPEDITE；黄金要求 APS_EDD |
| DELIVERY-017 | `2104081005362171906` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 1.673 | 真实推荐 APS_CR_EXPEDITE；黄金要求 APS_EDD |
| DELIVERY-018 | `2104081012446351362` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 2.278 | 真实推荐 APS_CR_EXPEDITE；黄金要求 APS_EDD |
| DELIVERY-019 | `2104081021933867009` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 2.216 | 真实推荐 APS_CR_EXPEDITE；黄金要求 APS_EDD |
| DELIVERY-020 | `2104081031224250369` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 2.230 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-021 | `2104081040573353986` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 2.216 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-022 | `2104081049867931650` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 1.700 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-023 | `2104081057015025666` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 2.224 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-024 | `2104081066330574850` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 1.674 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-025 | `2104081073343451137` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 1.691 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-026 | `2104081080465379329` | FAILED → FAILED | **FAIL** | 1.664 | 终态 FAILED 且无动作，但服务端仅返回通用错误，未命中黄金拒绝原因 |
| DELIVERY-027 | `2104081087415341058` | FAILED → FAILED | **FAIL** | 1.649 | 终态 FAILED 且无动作，但服务端仅返回通用错误，未命中黄金拒绝原因 |
| DELIVERY-028 | `2104081094335942658` | FAILED → FAILED | **FAIL** | 1.637 | 终态 FAILED 且无动作，但服务端仅返回通用错误，未命中黄金拒绝原因 |
| DELIVERY-029 | `2104081101197824002` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 2.216 | 真实推荐 APS_CR；黄金要求 APS_EDD |
| DELIVERY-030 | `2104081110504984577` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 2.204 | 真实推荐 APS_CR；黄金要求 APS_EDD |
| DELIVERY-031 | `2104081119740841985` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 1.703 | 真实推荐 APS_CR；黄金要求 APS_EDD |
| DELIVERY-032 | `2104081126925684738` | WAITING_APPROVAL → WAITING_APPROVAL | **FAIL** | 1.690 | 真实推荐 APS_CR；黄金要求 APS_EDD |
| DELIVERY-033 | `2104081133972115458` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 2.209 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-034 | `2104081143237332994` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 1.685 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-035 | `2104081150304735234` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 1.683 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-036 | `2104081157359554562` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 1.664 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-037 | `2104081164330487809` | WAITING_APPROVAL → WAITING_APPROVAL | **PASS** | 2.221 | 结构化风险、根因、方案、动作均匹配 |
| DELIVERY-038 | `2104081173658619905` | FAILED → FAILED | **FAIL** | 1.637 | 终态 FAILED 且无动作，但服务端仅返回通用错误，未命中黄金拒绝原因 |
| DELIVERY-039 | `2104081180512112641` | FAILED → FAILED | **FAIL** | 0.588 | 终态 FAILED 且无动作，但服务端仅返回通用错误，未命中黄金拒绝原因 |
| DELIVERY-040 | `2104081182978363393` | FAILED → FAILED | **FAIL** | 1.626 | 终态 FAILED 且无动作，但服务端仅返回通用错误，未命中黄金拒绝原因 |

## 故障定位及下一轮门槛

1. **模型鉴权**：隔离库所配模型凭据当前不可用，本轮执行的 39 个 LLM 节点均失败，另 1 个跳过。应由环境负责人提供可用且获授权的模型配置后，在**另一个新的隔离系统库**重测；目前的 P50/P95 不可宣传为 AI 模型响应性能。日志/报告不记录 API Key、Token 或密文。
2. **方案黄金冲突**：`006`、`016–019` 实测 APS_CR_EXPEDITE 可行而黄金要求不可行的 APS_EDD；`007–011` 缺料时所有方案不可行；`029–032` 真实排名首选 APS_CR 而黄金要求 APS_EDD。逐案核对 ERP seed、候选约束与业务验收口径，**不能为了通过率静默修改黄金期望或让不可行方案被推荐**。
3. **错误可验证性**：`012–015` 在建议动作节点意外失败；`026–028`、`038–040` 虽以 FAILED 且零动作安全退出，公开详情只有 `DECISION_NODE_FAILED` 通用原因，不符合预期拒绝匹配规则。应在不泄露内部异常/模型密钥的前提下定义稳定、可审计的拒绝代码，定位具体失败节点后再复测。
4. **并发与遗留审批**：旧演示系统库里的 `006` 已批准，不允许强制重算；待审批运行又占据默认 4 个活跃槽。原库首轮仅 5/40 PASS、32 例根本无法启动，**未计入本次可信基线**。本轮使用全新系统库和仅本进程的 48 上限；重测时不要在本库直接强制重启待审批运行，须保留审计并使用新隔离库。
5. **审批与安全 CONTROL**：CONTROL-001..005 的 53 项服务级测试通过，不等于五个真实 HTTP 人工安全动作验证；本次未模拟越权、故障注入或真实审批。比赛验收需独立完成安全复核，之后才能冻结正式提交数字。

此快照只陈述 **2026-09-27** 本地真实执行结果。未来的 2026-10 文件仍为计划中的竞赛最终基线，不能将本次降级模式失败样本改写为正式 40/40。
