# 摘要闭合与真实 HTTP CONTROL 验收（2026-09-27）

## 结论与口径

- v2 四十例首轮 HTTP：**39/40 PASS**。034 的 LLM 用时 **112.991 秒**，超过既定 90 秒窗口；之后原运行正常到达 WAITING_APPROVAL。原 TIMEOUT 报告保持原样，未增加窗口或改成 PASS。
- 034 在同一 90 秒窗口单独复测 **1/1 PASS**，LLM 25.771 秒（运行 `2104210984469221378`）。结合 39 个首轮通过项，逐案例结构化黄金一致 **40/40**；**这不等于单轮性能验收 40/40**。
- 首轮最终业务状态：5 SUCCEEDED、29 WAITING_APPROVAL、6 预期证据拒绝 FAILED。34 个正例摘要全部 **FLOW_LLM**（含024/029/033），6 个负例未调用模型；本轮正常分析未审批动作。
- CONTROL 独立统计：**11 个安全子项通过，另有基线固定与库存对账两项，共13项**。通过真实登录、HTTP 请求、真实 ERP 服务写入/独立核验/补偿及数据库对账验收，不是服务 mock 的结果。
- Java/Maven **572 tests / 0 failures / 0 errors / 0 skipped**；fixture 17、Runner 15、Flow DDL 1、离线 SQL 护栏 2，共 **35 Python tests**通过。打包成功；CONTROL 配置类仅在 test-classes，不在生产 JAR。

## 本轮修复

### 摘要解析：兼容语义，保持安全拒绝

1. 029/033 历史模型输出用 `scenarioTradeoffs: [{scenarioCode, explanation}]`。只接受这个 exact 对象并映射 explanation → tradeoff；与 tradeoff 共存、额外字段、空值/空白、非字符串均拒绝。映射后仍经过原完整策略 Guard，不改风险分、根因、KPI、排名、推荐或动作。
2. 024 历史输出包含提示和服务端证据集合都不存在的引用，不是本次提示截断造成。**保持拒绝，不猜测、不补齐、不过滤后冒充可信摘要。**新增未知引用负例验证固定安全诊断；本轮重新生成的024引用有效，不代表历史错误引用变合法。
3. 模板降级在现有 uncertaintyNotes 给出有限原因 `SUMMARY_REJECTED:FORMAT_INVALID / SCHEMA_INVALID / UNKNOWN_EVIDENCE_CITATION / MODEL_UNAVAILABLE`。不增加 Narrative 顶层字段，不回显错误引用、模型正文或上游凭据。
4. 5000字符提示预算按完整行截断；超长/含换行证据键不发送截断前缀。没有引入额外模型重试或放宽引用白名单。

### 真实 HTTP 暴露的跨层缺陷

- **执行卡 APPROVED**：`industrial.executeApprovedActions` 有33字符，系统表 `ai_flow_node_run.node_type` 仅 VARCHAR(30)，审计行写入报 Data too long，执行未开始。主 SQL 扩为 VARCHAR(64)，补所有工业 SPI 节点长度契约测试，迁移登记见 `doc/system/数据库增量变更记录.md`。
- **查询被误要求分析权限**：ERP 装配器的存在性检查共享了 RUN-only 校验，使 query-only 用户无法看自己的决策，尽管 Port 的服务级测试用 mock 通过。现将存在性与装配权限分离：query+业务读取可检查存在性；不能做分析或指纹计算。execute+业务读取可重核审批/执行快照，无需额外赋予 run。
- **禁用类型在审批之前拒绝**：批准动作先校验有限枚举、可信 executor/permission 和必要审批标记，再绑定审批人和启动 Flow。允许风险策略给原本可选通知增加人工审批；不允许把采购/排产必需审批降为 N。未知类型/伪造元数据不会产生 APPROVED 悬挂或业务副作用。

## 隔离边界与验证码

- MySQL：127.0.0.1:13307，四库 `nexus_ai_decision_control_20260927_sys/erp/mes/iot`。ERP 从原 demo ERP 只读 dump 复制，保留真实 fixture 日期；MES/IoT 仅初始化空业务表。账号 decision_control 仅这四库 DML，不把 MySQL root 传给应用。
- Redis：新实例127.0.0.1:6386；不是通过 logical DB 假隔离 Pub/Sub。MQ 使用私有 vhost `decision-control-20260927`，原 vhost不变。
- 8081 只覆盖四个隔离数据源。原8080服务、其 dev YAML、原 demo 系统库和 plus 不修改、不重启。原 demo ERP 20表 checksum 前后完全一致；控制副本库存 checksum `3845202052`不变。
- 有效模型/provider的密文仅服务器侧复制，原厂商/模型不改，原始密钥不读取或写报告；最终默认模型恢复 `2103871286244564993`。
- 根据用户2026-09-27补充授权，最后一阶段仅在隔离启动命令加 `--captcha.enable=false`，已验证真实免验证码登录。基础 `application.yml` 仍 enable:true；退出隔离服务即恢复日常启动默认行为，未修改生产/dev验证码设置。
- 故障注入仅本地 marker，由外部 test-classes 的明确 Profile+开关控制，并强制校验四DS、同日期库组、独立Redis/MQ；prod Profile拒绝。不新增 HTTP 后门、不伪造 execute/verify/compensate 返回成功。

## CONTROL 逐项证据

| 子项 | 结果与可核对证据 |
|---|---|
| 001：伪版本/快照 | 运行 `2104198732882001921`，两请求应用code500；保持WAITING_APPROVAL，零执行 |
| 001：源数据过期 | 运行 `2104198732882001921`；仅控制副本工单数量+1后真实审批 → STALE，无execution Flow/草稿；随后恢复数量和原更新时间 |
| 002：只读越权 | 自有详情200，审批403、动作retry403；没有用“所有接口均无权”冒充越权验收 |
| 002：租户隔离 | 租户B自有查询fixture9100001成功200，访问/审批/重试租户A目标均拒绝；fixture由服务器种子构造，不声称完成了真实租户B分析 |
| 002：禁用动作 | 运行 `2104207758080577538`；控制库动作类型注入PUBLISH_SCHEDULE_PLAN，审批拒绝；WAITING_APPROVAL、无execution Flow/草稿 |
| 003：模型不可用 | 运行 `2104208280971874305`；loopback HTTP401 stub收到2次明确假key请求，未收到真key；TEMPLATE_FALLBACK，确定性WAITING_APPROVAL、所有动作NOT_STARTED、零业务写入 |
| 004：并发审批/幂等 | 运行 `2104207863596683265`；并发响应一成功一拒绝；按execution图及input.runId独立对账只有1实例，同idempotencyKey只1采购草稿；成功动作retry200但无新草稿 |
| 005：部分成功 | 运行 `2104210538635677698`；真实采购草稿成功，排产动作写入前注入故障 → PARTIAL_SUCCESS；排产草稿0；终态失败动作retry拒绝 |
| 005：真实补偿 | 运行 `2104208045860163585`；真实创建并独立核验采购草稿后注入失败，真实ERP补偿将草稿逻辑删除，action COMPENSATED、run ROLLED_BACK，保留审计 |
| 005：非DRAFT保护 | 运行 `2104208168614858753`；核验后测试注入APPROVING状态，补偿拒绝COMPENSATION_NOT_ALLOWED，业务行保留。仅状态故障注入，没有真的提交采购审批 |
| 005：版本保护 | 运行 `2104198747583037441`；核验后version+1，补偿拒绝，DRAFT行保留，不误删被修改的记录 |

响应表的200/403/500是R对象**应用code**；后续复测阶段实际HTTP transport另存 `http-transport.ndjson`，不把HTTP200包裹的业务拒绝误称传输失败。没有发布排产、扣库存、采购自动审批或调用供应商外部催交。

## 失败保全、构建与复现限制

- 修复前节点列长度不足的失败 Flow / APPROVED运行保留在控制库，不以SQL改状态或删除记录刷结果。重新选未进入执行阶段的案例，通过新的分析/审批验证。初轮只观察到禁用动作没有业务写入但未观察到完整拒绝，因此**不采用**初轮该项“passed”，仅采用修复后审批前拒绝证据。
- 初次模型故障测试厂商/协议组合不合法（OPENAI+RESPONSES），属于验收脚本配置失败，已改为合法OPENAI_CHAT_COMPLETIONS+假凭据401；并未修改厂商协议限制。
- 中间一次审批元数据检查过于严格，误拒绝风险策略加强人工审批的通知；有红测和修复后真实PARTIAL_SUCCESS证据，不删除失败记录。
- 最终 JAR SHA256：`13c01f95a0a18e8ca52b5976d460e82dbbbc0c0c78995dace909dec4b2824825`；fixture hashes 与源文件 hashes 见 final-build.json。
- 首轮四十例在摘要修复后、ACL/审批控制修复前运行，**其JAR SHA未在运行前记录**。最终JAR hash仅适用于最后部分成功/034复测，不伪称覆盖全部早期运行；中间阶段运行有精确ID/时间及数据库证据。若要求单一最终JAR全40的严格重测，需新ERP副本，不能在已创建/补偿过草稿的控制业务库重跑后刷黄金。
- 决策证据目录：`reports/2026-09-27-summary-control-evidence/`。保留四份原始首轮报告、034复测报告、三轮CONTROL journal、基线run IDs、transport及独立database audit；未带token、真实密钥、密码hash或完整模型正文。

## 部署前必须处理（尚未自动执行到原dev库）

**现有 `nexus_ai_decision_demo_sys.ai_flow_node_run.node_type` 仍为30**；旧v2基线sys也为30。只在新control sys已扩为64。现有dev要验证审批执行，必须按增量记录先备份/核对数据库，再执行单列扩容并重启IDE后端。禁止重导整份初始化脚本、触碰plus或用SQL将旧APPROVED改成SUCCEEDED。

本轮代码、摘要/权限/审批回归和隔离CONTROL验收完成；部署迁移与“最终单一JAR全40+严格90秒稳定性”应作为明确后续，不因成功复测而宣称已经完成。

## 清理与自审

自建8081后端、测试登录客户端和loopback401 helper均已停止；原8080仍在监听。四个控制库和独立Redis/vhost保留供审计，不清空草稿或失败运行。源DB/库存对账完成；Git diff/源文件哈希/生产JAR排除CONTROL类检查通过。完成本地静态自审，没有将自审表述为独立外部审查。
