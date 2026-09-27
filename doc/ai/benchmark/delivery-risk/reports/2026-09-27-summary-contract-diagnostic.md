# 摘要契约首轮诊断与动作节点修复实测（2026-09-27）

> 原黄金契约 **16/40 PASS**。不得将模型节点成功、模型摘要通过、安全预期拒绝与黄金契约通过混为一谈。

## 部署与证据口径

- 开始预检 `2026-09-27T19:07:20.279660+08:00`；最后一份 HTTP Runner 报告完成 `2026-09-27T19:20:31+08:00`。
- 源基线 `a9eb59ab + uncommitted node/summary fixes`；部署 JAR SHA256 `d58eee5cdd1b1544a87fa5bd9db7ed0636cfd836f3011a2df71c7910eaa2c528`。
- 全新系统库 `nexus_ai_decision_fix_20260927_sys`（198 表、开始时零运行）；仅使用独立 MySQL `127.0.0.1:13307`，ERP 为 `nexus_ai_decision_demo_erp`。plus/3306 未读取或写入。
- 临时后端 `8081`，dev+decision-demo；原 8080 保留。Redis 6385、RabbitMQ 5685、演示 ERP 与 8080 共享，因此不是独占压测环境。
- 默认真实模型 `deepseek-v4.1-flash` / `2103871286244564993`，`OPENAI_RESPONSES`；模型密文只由数据库服务器复制，没有公开凭据。连接测试 1450ms。
- 顺序 40 案例，优先 001 与 012–015。此为中间提示版本，用于定位剩余摘要降级。
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
| 摘要模式 | FLOW_LLM 17；NULL 6；TEMPLATE_FALLBACK 17 |
| 摘要 Guard 拒绝诊断 | SUMMARY_GUARD_REJECTED:FORBIDDEN_STATUS 17 |
| 全案例请求→Runner 观察 P50 / P95 | 19.640 / 24.834 秒 |
| 原黄金 PASS 请求→终态 P50 / P95 | 20.677 / 29.625 秒 |
| 成功 LLM 节点 P50 / P95 | 19.724 / 25.733 秒 |
| 真实模型 Token | 输入 50406；输出 138755 |
| 动作 | 49 条建议草稿，全部 NOT_STARTED，零禁止动作 |

## 逐例对账

| 案例 | 运行 ID | 终态 | 原黄金 | 摘要模式 | 耗时(s) | 差异说明 |
|---|---|---|---|---|---:|---|
| DELIVERY-001 | `2104166419003486210` | SUCCEEDED | PASS | TEMPLATE_FALLBACK | 23.413 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-002 | `2104166884621561858` | SUCCEEDED | PASS | TEMPLATE_FALLBACK | 14.862 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-003 | `2104166946965696513` | SUCCEEDED | PASS | FLOW_LLM | 19.106 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-004 | `2104167027085291522` | SUCCEEDED | PASS | TEMPLATE_FALLBACK | 29.625 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-005 | `2104167151345741826` | SUCCEEDED | PASS | TEMPLATE_FALLBACK | 11.154 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-006 | `2104167198141591553` | WAITING_APPROVAL | FAIL | FLOW_LLM | 20.711 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-007 | `2104167284993044481` | WAITING_APPROVAL | FAIL | TEMPLATE_FALLBACK | 20.648 | recommendation: APS_EDD → NULL |
| DELIVERY-008 | `2104167371630587905` | WAITING_APPROVAL | FAIL | FLOW_LLM | 13.814 | recommendation: APS_EDD → NULL |
| DELIVERY-009 | `2104167429541343234` | WAITING_APPROVAL | FAIL | FLOW_LLM | 19.581 | recommendation: APS_EDD → NULL |
| DELIVERY-010 | `2104167511674204161` | WAITING_APPROVAL | FAIL | TEMPLATE_FALLBACK | 19.640 | recommendation: APS_EDD → NULL |
| DELIVERY-011 | `2104167594067111937` | WAITING_APPROVAL | FAIL | FLOW_LLM | 16.438 | recommendation: APS_EDD → NULL |
| DELIVERY-012 | `2104166516600745986` | WAITING_APPROVAL | FAIL | TEMPLATE_FALLBACK | 20.212 | recommendation: APS_EDD → NULL；动作集合差异 |
| DELIVERY-013 | `2104166601363435522` | WAITING_APPROVAL | FAIL | FLOW_LLM | 21.262 | recommendation: APS_EDD → NULL；动作集合差异 |
| DELIVERY-014 | `2104166690563698690` | WAITING_APPROVAL | FAIL | TEMPLATE_FALLBACK | 23.493 | recommendation: APS_EDD → NULL；动作集合差异 |
| DELIVERY-015 | `2104166789146619905` | WAITING_APPROVAL | FAIL | FLOW_LLM | 22.782 | recommendation: APS_EDD → NULL；动作集合差异 |
| DELIVERY-016 | `2104167662992109569` | WAITING_APPROVAL | FAIL | FLOW_LLM | 16.931 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-017 | `2104167734031036418` | WAITING_APPROVAL | FAIL | FLOW_LLM | 21.145 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-018 | `2104167822698622977` | WAITING_APPROVAL | FAIL | FLOW_LLM | 17.484 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-019 | `2104167896052805634` | WAITING_APPROVAL | FAIL | TEMPLATE_FALLBACK | 18.519 | recommendation: APS_EDD → APS_CR_EXPEDITE |
| DELIVERY-020 | `2104167973693566978` | WAITING_APPROVAL | PASS | TEMPLATE_FALLBACK | 22.319 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-021 | `2104168067314626562` | WAITING_APPROVAL | PASS | FLOW_LLM | 16.989 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-022 | `2104168138567462913` | WAITING_APPROVAL | PASS | TEMPLATE_FALLBACK | 13.804 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-023 | `2104168196465635330` | WAITING_APPROVAL | PASS | TEMPLATE_FALLBACK | 26.439 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-024 | `2104168307363033089` | WAITING_APPROVAL | PASS | TEMPLATE_FALLBACK | 23.252 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-025 | `2104168404905766914` | WAITING_APPROVAL | PASS | TEMPLATE_FALLBACK | 24.834 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-026 | `2104168509037752321` | FAILED | FAIL | NULL | 0.588 | 证据负例安全拒绝，但 API 通用错误未满足黄金可核验失败原因 |
| DELIVERY-027 | `2104168511499808770` | FAILED | FAIL | NULL | 0.589 | 证据负例安全拒绝，但 API 通用错误未满足黄金可核验失败原因 |
| DELIVERY-028 | `2104168513987031041` | FAILED | FAIL | NULL | 0.601 | 证据负例安全拒绝，但 API 通用错误未满足黄金可核验失败原因 |
| DELIVERY-029 | `2104168516507807745` | WAITING_APPROVAL | FAIL | FLOW_LLM | 23.818 | recommendation: APS_EDD → APS_CR |
| DELIVERY-030 | `2104168616399351810` | WAITING_APPROVAL | FAIL | FLOW_LLM | 20.681 | recommendation: APS_EDD → APS_CR |
| DELIVERY-031 | `2104168703150141442` | WAITING_APPROVAL | FAIL | FLOW_LLM | 22.789 | recommendation: APS_EDD → APS_CR |
| DELIVERY-032 | `2104168798729940993` | WAITING_APPROVAL | FAIL | TEMPLATE_FALLBACK | 18.571 | recommendation: APS_EDD → APS_CR |
| DELIVERY-033 | `2104168876626554882` | WAITING_APPROVAL | PASS | TEMPLATE_FALLBACK | 20.677 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-034 | `2104168963347984385` | WAITING_APPROVAL | PASS | TEMPLATE_FALLBACK | 19.643 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-035 | `2104169045736697858` | WAITING_APPROVAL | PASS | FLOW_LLM | 18.576 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-036 | `2104169123683643394` | WAITING_APPROVAL | PASS | FLOW_LLM | 21.196 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-037 | `2104169212544167937` | WAITING_APPROVAL | PASS | FLOW_LLM | 21.209 | 状态、风险、根因、方案及动作符合原黄金 |
| DELIVERY-038 | `2104169301513744386` | FAILED | FAIL | NULL | 0.597 | 证据负例安全拒绝，但 API 通用错误未满足黄金可核验失败原因 |
| DELIVERY-039 | `2104169304000966657` | FAILED | FAIL | NULL | 0.577 | 证据负例安全拒绝，但 API 通用错误未满足黄金可核验失败原因 |
| DELIVERY-040 | `2104169306425274369` | FAILED | FAIL | NULL | 0.585 | 证据负例安全拒绝，但 API 通用错误未满足黄金可核验失败原因 |

## 验收结论

- `012–015` 需逐案检查动作节点成功、null 推荐及只含真实缺料采购草稿；不能以 WAITING_APPROVAL 冒充 EDD 黄金匹配。
- 黄金差异单列审查，不修改原 cases/expected、不推荐缺料不可行方案；业务确认之前，18 条方案/动作契约差异继续 FAIL。
- `026–028、038–040` 仍为安全拒绝且零动作；失败节点已提前至验证事实的 rank/context，但 API 缺稳定可核验拒绝原因仍 FAIL，不把任意异常算通过。
- 本轮单测与打包结果见修复计划；临时验证后端测试后关闭，用户的 8080 不重启/不重配。
