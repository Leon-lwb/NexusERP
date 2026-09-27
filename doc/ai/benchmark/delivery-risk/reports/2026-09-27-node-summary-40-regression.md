# 动作节点与摘要 Guard 修复：40 案例最终真实回归（2026-09-27）

> 原黄金契约 **16/40 PASS**。不得将模型节点成功、模型摘要通过、安全预期拒绝与黄金契约通过混为一谈。

## 部署与证据口径

- 开始预检 `2026-09-27T19:35:00.119360+08:00`；最后一份 HTTP Runner 报告完成 `2026-09-27T19:39:22+08:00`。
- 源基线 `a9eb59ab + final node/summary/concurrent-publication fixes`；部署 JAR SHA256 `bf2167c6d9e8555cc79c8d507c426ba080cc88baf47b5229e236bd11a6af0de3`。
- 全新系统库 `nexus_ai_decision_verified_20260927_sys`（198 表、开始时零运行）；仅使用独立 MySQL `127.0.0.1:13307`，ERP 为 `nexus_ai_decision_demo_erp`。plus/3306 未读取或写入。
- 临时后端 `8081`，dev+decision-demo；原 8080 保留。Redis 6385、RabbitMQ 5685、演示 ERP 与 8080 共享，因此不是独占压测环境。
- 默认真实模型 `deepseek-v4.1-flash` / `2103871286244564993`，`OPENAI_RESPONSES`；模型密文只由数据库服务器复制，没有公开凭据。连接测试 1129ms。
- 四个不重叠分片，每片十例，最多四个并发分析；不是与上轮顺序测试可直接比较的性能基线。
- 每例先 GET 精确工单 ID/编号，再发起真实分析；保留 90 秒终态验收上限、500ms 轮询和原黄金。CONTROL-001..005 仅为服务级单测，不宣称完成 HTTP 审批/执行安全验证。
- 原 fixture SHA256：cases `f5f0d089e51130a84156fb77c441dd53aa433fb9dad87ba0abf08bdee32fd3d3`；expected `e05859947ea468cc14dd5cb99c55f3ced0f0b62370bc1361dbb37da1587275c8`。测试前后文件和部署 JAR 均核验不变。
- 20 张 ERP seed 来源表 CHECKSUM 前后一致；全部动作 NOT_STARTED；未调用审批、执行或回滚。历史库/报告保留。

## 结果

| 指标 | 实测 |
|---|---|
| HTTP 发起 / 数据库唯一运行 | 40 / 40 |
| 原黄金通过 | 16/40（40.0%） |
| 最终运行状态 | FAILED 6；SUCCEEDED 5；WAITING_APPROVAL 29 |
| LLM 节点状态 | SKIPPED 6；SUCCEEDED 34 |
| 摘要模式 | FLOW_LLM 34；NULL 6 |
| 摘要 Guard 拒绝诊断 | 无 |
| 全案例请求→Runner 观察 P50 / P95 | 20.434 / 30.115 秒 |
| 原黄金 PASS 请求→终态 P50 / P95 | 22.198 / 33.277 秒 |
| 成功 LLM 节点 P50 / P95 | 20.176 / 29.856 秒 |
| 真实模型 Token | 输入 52616；输出 152864 |
| 动作 | 49 条建议草稿，全部 NOT_STARTED，零禁止动作 |

## 修复验收与旧报告对照

- `012–015` 四个 actionProposal 节点均为 SUCCEEDED，运行进入 WAITING_APPROVAL，推荐均为 null。每例只有一份采购申请草稿、两条真实缺料行，逐项核对物料 ID、缺口与需要日期等于 shortage 节点；没有不可行排程草稿。
- 四路冷首次请求再次实际发生两次同系统键 INSERT，但只有一个规范图胜者；全部请求取得有效流程实例，无 FLOW_START_FAILED。重复键恢复经过原有模板/租户/发布状态/版本校验，而非吞掉任意数据库故障。
- 上一轮凭据修复报告为 30 次 TEMPLATE_FALLBACK、4 个意外动作节点失败；本次为 34 次 FLOW_LLM、0 次 TEMPLATE_FALLBACK、0 个正常数据案例运行错误。源证据不足的六例在调用模型前拒绝，因此 LLM 次数由原 39 次变为 34 次；并非漏跑。
- 50 份来自最后一轮的 Surefire XML：AI/Flow 及依赖 466 项，零失败/错误/跳过；`mvn -pl ruoyi-admin -am package -DskipTests -q` 成功。两次补丁静态审查均无阻断项。
- 本次零摘要回退是实测结果，不是关闭 Guard 或保证模型永不违规。回归测试仍证明未知根因、捏造数字、非法状态/推荐会安全回退或拒绝，风险/KPI/排名/证据保持不变，二次 Guard 仍拒绝时分析失败关闭。
- 本次使用隔离演示 ERP seed 与真实 HTTP/真实模型，不代表生产客户订单或正式竞赛验收。由于最终运行四路并发，性能分位数不能与旧顺序报告直接视为性能提升。

## 逐例对账

| 案例 | 运行 ID | Runner 观察 → 数据库终态 | 原黄金 | 摘要模式 | 耗时(s) | 差异说明 |
|---|---|---|---|---|---:|---|
| DELIVERY-001 | `2104173173468712963` | SUCCEEDED → SUCCEEDED | PASS | FLOW_LLM | 22.682 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-002 | `2104173307027935234` | SUCCEEDED → SUCCEEDED | PASS | FLOW_LLM | 19.084 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-003 | `2104173267165270017` | SUCCEEDED → SUCCEEDED | PASS | FLOW_LLM | 21.183 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-004 | `2104173258483060737` | SUCCEEDED → SUCCEEDED | PASS | FLOW_LLM | 26.468 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-005 | `2104173374191325185` | SUCCEEDED → SUCCEEDED | PASS | FLOW_LLM | 23.247 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-006 | `2104173387067838466` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 24.297 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-007 | `2104173355992240129` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 18.521 | recommendation: APS_EDD → NULL |
| DELIVERY-008 | `2104173369481121794` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 20.091 | recommendation: APS_EDD → NULL |
| DELIVERY-009 | `2104173471679533057` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 16.381 | recommendation: APS_EDD → NULL |
| DELIVERY-010 | `2104173488972648449` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 29.580 | recommendation: APS_EDD → NULL |
| DELIVERY-011 | `2104173433666555906` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 25.319 | recommendation: APS_EDD → NULL |
| DELIVERY-012 | `2104173173468712961` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 32.034 | recommendation: APS_EDD → NULL；动作集合差异 |
| DELIVERY-013 | `2104173173468712964` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 22.504 | recommendation: APS_EDD → NULL；动作集合差异 |
| DELIVERY-014 | `2104173173468712962` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 20.434 | recommendation: APS_EDD → NULL；动作集合差异 |
| DELIVERY-015 | `2104173267811192833` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 25.364 | recommendation: APS_EDD → NULL；动作集合差异 |
| DELIVERY-016 | `2104173453748883458` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 17.464 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-017 | `2104173540399009793` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 18.539 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-018 | `2104173613040160770` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 20.628 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-019 | `2104173539862138881` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 18.567 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-020 | `2104173526985625602` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 27.495 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-021 | `2104173618157211649` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 6.946 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-022 | `2104173699560263682` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 30.115 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-023 | `2104173617737781250` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 25.926 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-024 | `2104173642320596994` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 27.987 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-025 | `2104173647290847234` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 21.196 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-026 | `2104173825892700161` | FAILED → FAILED | FAIL | NULL | 0.611 | 证据负例安全拒绝，但 API 通用错误未满足黄金可核验失败原因 |
| DELIVERY-027 | `2104173726479306753` | FAILED → FAILED | FAIL | NULL | 0.592 | 证据负例安全拒绝，但 API 通用错误未满足黄金可核验失败原因 |
| DELIVERY-028 | `2104173759706583042` | FAILED → FAILED | FAIL | NULL | 0.584 | 证据负例安全拒绝，但 API 通用错误未满足黄金可核验失败原因 |
| DELIVERY-029 | `2104173736189120513` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 19.033 | recommendation: APS_EDD → APS_CR |
| DELIVERY-030 | `2104173828426059778` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 19.585 | recommendation: APS_EDD → APS_CR |
| DELIVERY-031 | `2104173728949751809` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 23.245 | recommendation: APS_EDD → APS_CR |
| DELIVERY-032 | `2104173762147667969` | WAITING_APPROVAL → WAITING_APPROVAL | FAIL | FLOW_LLM | 19.011 | recommendation: APS_EDD → APS_CR |
| DELIVERY-033 | `2104173816023502850` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 21.137 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-034 | `2104173910575697921` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 33.277 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-035 | `2104173826450542594` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 14.316 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-036 | `2104173841881387009` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 22.198 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-037 | `2104173904674312194` | WAITING_APPROVAL → WAITING_APPROVAL | PASS | FLOW_LLM | 16.935 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-038 | `2104174050153746434` | FAILED → FAILED | FAIL | NULL | 0.590 | 证据负例安全拒绝，但 API 通用错误未满足黄金可核验失败原因 |
| DELIVERY-039 | `2104173886512975873` | FAILED → FAILED | FAIL | NULL | 0.595 | 证据负例安全拒绝，但 API 通用错误未满足黄金可核验失败原因 |
| DELIVERY-040 | `2104173934994935809` | FAILED → FAILED | FAIL | NULL | 0.585 | 证据负例安全拒绝，但 API 通用错误未满足黄金可核验失败原因 |

## 验收结论

- `012–015` 动作节点、null 推荐、两条真实缺料采购草稿已逐案核验成功；原黄金 EDD/排程动作仍不匹配，继续 FAIL。
- 黄金差异单列审查，不修改原 cases/expected、不推荐缺料不可行方案；业务确认之前，18 条方案/动作契约差异继续 FAIL。
- `026–028、038–040` 仍为安全拒绝且零动作；失败节点已提前至验证事实的 rank/context，但 API 缺稳定可核验拒绝原因仍 FAIL，不把任意异常算通过。
- 临时验证后端 8081 已关闭，原 8080 保留且不重配/不重启；三个本轮隔离系统库及所有旧报告、原始证据均保留。用户的 IDEA 后端需重启才能加载新代码，不需要改用临时验证系统库。
- 最终原始四片 Runner 输出与前后元数据在本机 Temp 中保存；合并结果与数据库运行 ID 已逐例对账，未改写任何 Runner FAIL。
