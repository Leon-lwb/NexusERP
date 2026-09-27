# 黄金 v2 与安全拒绝码：40 案例真实回归（2026-09-27）

> v2 批准黄金契约 **40/40 PASS**。不得将模型节点成功、模型摘要通过、安全预期拒绝与黄金契约通过混为一谈。

## 部署与证据口径

- 开始预检 `2026-09-27T20:27:26.098396+08:00`；最后一份 HTTP Runner 报告完成 `2026-09-27T20:36:30+08:00`。
- 源基线 `bc72309c + reviewed v2 golden/typed evidence fixes`；部署 JAR SHA256 `5f41a22ff7bc67aa0bb154ef8788e4dcb53f38c22a86b9f6573e9661b27aeec8`。
- 全新系统库 `nexus_ai_decision_v2_20260927_sys`（198 表、开始时零运行）；仅使用独立 MySQL `127.0.0.1:13307`，ERP 为 `nexus_ai_decision_demo_erp`。plus/3306 未读取或写入。
- 临时后端 `8081`，dev+decision-demo；原 8080 保留。Redis 6385、RabbitMQ 5685、演示 ERP 与 8080 共享，因此不是独占压测环境。
- 默认真实模型 `deepseek-v4.1-flash` / `2103871286244564993`，`OPENAI_RESPONSES`；模型密文只由数据库服务器复制，没有公开凭据。连接测试 1721ms。
- 四个不重叠分片，每片十例，最多四个并发分析；不是与上轮顺序测试可直接比较的性能基线。
- 每例先 GET 精确工单 ID/编号，再发起真实分析；保留 90 秒终态验收上限、500ms 轮询和 v2 批准黄金。CONTROL-001..005 仅为服务级单测，不宣称完成 HTTP 审批/执行安全验证。
- 本轮 v2 fixture SHA256：cases `43e5a9d0974cbae50e39ff58e1dd4a49f4fcde496812d1875b882fc39162fff8`；expected `f4fcf3c4dd5978b72776980990efd10f7e177a459e624ba1137b1acd180c94ea`。测试前后文件和部署 JAR 均核验不变。
- 20 张 ERP seed 来源表 CHECKSUM 前后一致；全部动作 NOT_STARTED；未调用审批、执行或回滚。历史库/报告保留。

## 结果

| 指标 | 实测 |
|---|---|
| HTTP 发起 / 数据库唯一运行 | 40 / 40 |
| v2 批准黄金通过 | 40/40（100.0%） |
| 最终运行状态 | FAILED 6；SUCCEEDED 5；WAITING_APPROVAL 29 |
| LLM 节点状态 | SKIPPED 6；SUCCEEDED 34 |
| 摘要模式 | FLOW_LLM 31；NULL 6；TEMPLATE_FALLBACK 3 |
| 摘要 Guard 拒绝诊断 | 无 |
| 全案例请求→Runner 观察 P50 / P95 | 23.745 / 52.584 秒 |
| v2 批准黄金 PASS 请求→终态 P50 / P95 | 23.745 / 52.584 秒 |
| 成功 LLM 节点 P50 / P95 | 23.644 / 55.439 秒 |
| 真实模型 Token | 输入 53500；输出 98150 |
| 动作 | 49 条建议草稿，全部 NOT_STARTED，零禁止动作 |

## 逐例对账

| 案例 | 运行 ID | Runner 观察 → 数据库终态 | v2 批准黄金 | 摘要模式 | 耗时(s) | 差异说明 |
|---|---|---|---|---|---:|---|
| DELIVERY-001 | `2104187268494450690` | SUCCEEDED → SUCCEEDED | PASS | FLOW_LLM | 21.109 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-002 | `2104187136076079106` | SUCCEEDED → SUCCEEDED | PASS | FLOW_LLM | 28.657 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-003 | `2104187167076179970` | SUCCEEDED → SUCCEEDED | PASS | FLOW_LLM | 32.280 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-004 | `2104187245232840706` | SUCCEEDED → SUCCEEDED | PASS | FLOW_LLM | 24.278 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-005 | `2104187357099122689` | SUCCEEDED → SUCCEEDED | PASS | FLOW_LLM | 52.584 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-006 | `2104187256200945665` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 14.282 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-007 | `2104187302468313090` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 15.868 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-008 | `2104187347238313986` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 14.306 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-009 | `2104187577618849794` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 16.933 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-010 | `2104187316108189698` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 25.321 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-011 | `2104187369015140353` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 44.231 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-012 | `2104187065615966209` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 48.412 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-013 | `2104187065615966211` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 16.825 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-014 | `2104187068497453057` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 23.530 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-015 | `2104187069080461315` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 42.012 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-016 | `2104187407082643458` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 24.283 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-017 | `2104187648666165249` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 23.745 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-018 | `2104187422375075842` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 17.929 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-019 | `2104187554537594881` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 19.008 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-020 | `2104187508966481921` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 38.429 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-021 | `2104187748264108034` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 37.306 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-022 | `2104187497557975042` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 79.391 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-023 | `2104187634321645570` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 22.153 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-024 | `2104187670086475777` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | TEMPLATE_FALLBACK | 15.345 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-025 | `2104187904673898497` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 31.076 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-026 | `2104187058573729796` | FAILED → FAILED | PASS | NULL | 1.840 | 预期安全拒绝：ROUTING_EVIDENCE_MISSING；零结论/动作 |
| DELIVERY-027 | `2104187058573729794` | FAILED → FAILED | PASS | NULL | 1.994 | 预期安全拒绝：ROUTING_EVIDENCE_MISSING；零结论/动作 |
| DELIVERY-028 | `2104187058573729793` | FAILED → FAILED | PASS | NULL | 1.808 | 预期安全拒绝：ROUTING_EVIDENCE_MISSING；零结论/动作 |
| DELIVERY-029 | `2104187830501826561` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | TEMPLATE_FALLBACK | 27.372 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-030 | `2104187727242256386` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 37.882 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-031 | `2104187734506790914` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 37.345 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-032 | `2104188035079004162` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 56.338 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-033 | `2104187945308315650` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | TEMPLATE_FALLBACK | 15.888 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-034 | `2104187886067965954` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 24.777 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-035 | `2104187891080159233` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 24.244 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-036 | `2104188271314788354` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 38.407 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-037 | `2104188012001943554` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 19.532 | 状态、风险、根因、方案及动作符合 v2 |
| DELIVERY-038 | `2104187058573729795` | FAILED → FAILED | PASS | NULL | 1.764 | 预期安全拒绝：SCHEDULE_EVIDENCE_MISSING；零结论/动作 |
| DELIVERY-039 | `2104187065615966210` | FAILED → FAILED | PASS | NULL | 0.705 | 预期安全拒绝：SHORTAGE_SIMULATION_INCOMPLETE；零结论/动作 |
| DELIVERY-040 | `2104187066261889025` | FAILED → FAILED | PASS | NULL | 0.682 | 预期安全拒绝：CAPACITY_EVIDENCE_MISSING；零结论/动作 |

## 六条预期拒绝的独立证据

API Runner 与数据库均检查 FAILED、精确码、旧消息模式、risk_score/risk_level/推荐/result_json 为空、零动作；六个模型节点 SKIPPED。未知异常、同名字符串、wrapped cause 或 SQL/权限故障不能冒充这些拒绝。

| 案例 | 运行 ID | 精确错误码 | 固定公开消息 |
|---|---|---|---|
| DELIVERY-026 | `2104187058573729796` | `ROUTING_EVIDENCE_MISSING` | 缺少生产工艺路线证据，不能发布风险结论。 |
| DELIVERY-027 | `2104187058573729794` | `ROUTING_EVIDENCE_MISSING` | 缺少生产工艺路线证据，不能发布风险结论。 |
| DELIVERY-028 | `2104187058573729793` | `ROUTING_EVIDENCE_MISSING` | 缺少生产工艺路线证据，不能发布风险结论。 |
| DELIVERY-038 | `2104187058573729795` | `SCHEDULE_EVIDENCE_MISSING` | 缺少当前基线排程证据，不能发布风险结论。 |
| DELIVERY-039 | `2104187065615966210` | `SHORTAGE_SIMULATION_INCOMPLETE` | 缺少工单 MRP 需求证据，不能发布风险结论。 |
| DELIVERY-040 | `2104187066261889025` | `CAPACITY_EVIDENCE_MISSING` | 缺少可信产能日历证据，不能发布风险结论。 |

## 版本与验收边界

- 用户明确批准的十八例推荐/动作修订已应用到 active v2。原始 v1 cases/expected 精确保存在 `golden/versions/v1/`，旧 16/40 报告与审查 proposal 不改写；active v2 的批准范围/哈希见 `golden/2026-09-27-v2-approval.json`。旧 proposal 的 inactive 表示历史审查状态，不是当前版本未批准。
- v2 比 v1 仅修改十八例推荐/动作期望并为六条负例添加精确错误码。输入 facts、ID/编号、风险/置信度/根因、原失败消息模式及 ERP seed 未修改；没有放宽可行性、风险 Guard 或动作白名单。
- 活跃 JSONL 固定 LF，历史 v1 CRLF archive 禁止 Git text 归一化；17 项离线测试验证生成器复现、批准变更范围、输入不变及 Git 过滤前后历史字节一致。
- 最后一轮 AI/Flow 及依赖 **474 项**全绿；ERP 三个修改关联类 **80 项**全绿；HTTP Runner 模拟契约 **15 项**全绿；admin package 成功。静态审查发现的真实缺基线 null-KPI 形状与跨平台换行问题已补红/绿回归并复核通过。
- `012–015` 推荐 null，采购草稿保留、排程草稿不生成；其 v2 期望与实际匹配。十八例不再要求缺料不可行的 EDD，平局仍按既定 lexical scenarioCode，不依据模型文本或黄金强行制造 KPI。
- 本轮 **31 FLOW_LLM / 3 TEMPLATE_FALLBACK**，三条为 `024、029、033`，对应 LLM 节点都 SUCCEEDED。模型调用成功不等于摘要契约通过；已确认 024 存在非已核验集合的引用，其他解析/契约原因留作独立诊断，不猜测成凭据错误/超时，也不宣称全部模型摘要通过。安全模板降级不更改任何确定性风险、KPI、推荐、证据或动作，因此这些案例仍可通过黄金业务契约。
- 二十张 ERP 来源表 checksum 前后一致；49 建议动作全部 NOT_STARTED，零禁止动作；无审批、执行或回滚。只使用隔离 demo seed/真实模型和 HTTP，不代表生产客户订单或正式竞赛提交。
- 四路并发与旧顺序基线不具直接性能可比性；分位数仅是本轮观测。原始四片报告与前后元数据保留于本机 Temp，逐例 ID 已对账，不把未执行、超时或后续完成改写为 PASS。
- 临时验证后端 8081 测后关闭，原 8080 保留、连接配置不改。用户在 IDEA 重启加载新代码，无需切换临时系统库；报告里的运行 ID 属于 v2 独立系统库，不在原 demo_sys 界面。

## 后续独立工作

CONTROL-001..005 的真实人工 HTTP 审批/执行、幂等、越权/多租户和故障注入验证，以及三条摘要契约降级的进一步诊断，仍需单列任务；本次 40/40 不替代这些验收。未来 2026-10 正式竞赛、视频/公开授权回执未由本机回归自动完成。
