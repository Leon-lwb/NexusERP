# 2026-09-30 工业决策采购防重阶段实施与重启复核

> 分支：`codex/full-flow-acceptance-20260928`；HEAD：`120727a8`。时间均为 Asia/Shanghai。依据已批准的《2026-09-30-工业决策量纲与采购防重规格》及对应实施计划。本文件是本地开发阶段证据，不是 P1/P2 全部完成、生产部署、40 个独立案例或物理制造验收。未提交、推送、合并 `plus`。

## 1. 断电重启后的现场（18:29–19:10 原检查点；最新状态见第 6–7 节）

- 用户自行重启前后端。后端 8080 监听 PID `34072`，Vite 前端监听 PID `14032`；本轮没有启动第二套服务，也没有停止这两个用户服务。
- 2026-09-30 **18:29:06** 对授权本地 3306 的只读事务复核：MySQL `5.7.20-log`；AI 的 `decision_contract_version` / `purchase_payload_version`、ERP UOM/供应商核验列均未迁移；三张新 guard/ledger/event 表仍不存在。既有系统库 `mq_dead_letter_record` 及本轮审计使用的全部列存在。
- Java 实体已有新列映射且用户已经重新编译启动；因此 AI 决策、UOM/供应商等相关查询可能报缺列。不能将“服务启动”或离线编译通过当成新功能运行态已验收。**不在仍有业务/MQ/任务写入的现场执行 P1 DDL。**
- VPN 地址已经变化：旧 `http://10.151.240.161/` 的认证代理探测超时；新 `http://10.152.240.20/` 和 IPv6 回环前端可达。19:10 新地址 `/dev-api/auth/code` 为 HTTP/业务 200、`captchaEnabled=false`；后端 `127.0.0.1:8080/auth/code` 同样通过。本轮没有进行登录 POST，也未记录 Token/密码。
- 只读结构证据：`.codex/full-flow-20260928/restart-p1-schema-readonly-20260930.json`；公开认证探测和地址证据分别为 `resume-public-auth-probe-20260930.json`、`resume-frontend-address-proof-20260930.json`。

## 2. 本轮代码收口

### 2.1 采购申请编辑防来源伪造

`PuPurchaseRequisitionServiceImpl.updateByBo`：

- 已有 AI/决策身份及尚未核验的来源不能经普通编辑改写；普通 MANUAL 单也不能在编辑时新增 AI 头、run/action/idempotency 身份。
- 在替换明细前按稳定顺序 `SELECT ... FOR UPDATE` 读取原明细，拒绝旧 AI 行、未核来源、无法核验的行归属或带旧 `source_id` 的来源；不重释旧字段，也不让 MANUAL 请求洗掉原来源。
- 正常无来源手工编辑仍保留，父表版本 CAS 失败不替换明细；公开 AI 草稿创建的 readiness guard 默认仍拒绝。
- 已转单来源不可删除；存在下游订单的来源不可作废或条件补偿删除；APPROVED 但已有旧下游承接的申请也不能再次转单。

### 2.2 采购订单新增/编辑防转单旁路

`PuPurchaseOrderServiceImpl`：

- 新增订单只要携带申请头 ID 或申请行 ID，必须通过申请转单入口，不能直接保存，也不能先建无来源单后编辑补入来源。
- 编辑按持久化头、原订单行 ID 和原申请行/物料关系校验，禁止新增、改变、移除、重复或转贴来源；原明细缺失/重复/不属本订单、来源物料未知等均在写入前失败。
- 保留无来源手工编辑、来源不变的转单订单非来源字段编辑，以及 `lineList == null` 的仅表头编辑；不默默丢弃来源。
- 当前旧式明细保存仍是删除重插；**这不是新版账本已接入的证明**，账本启用前仍须实现行更新/承接/释放的完整同事务协议。

### 2.3 MES 低库存暂停消息与确认/退避

`MesSpareErpConsumer`、`MesSparePurchasePauseAuditService`、`MesSpareConsumerConfiguration`：

- Map-only 监听器不能继承全局 MANUAL ACK 却没有 Channel；改用命名专用 factory 和 AUTO ACK，不替换全局工厂。
- `SPARE_PURCHASE` 在业务分发、Redis 占位和库存重试计数之前处理。不得创建草稿或把暂停当成采购成功；通过另一 Bean 的 `@DS("master") + REQUIRES_NEW` 将完整解码载荷存入既有 `mq_dead_letter_record`，状态为 `PENDING`。
- 要求插入 1 行并获得正数审计 ID；代理审计调用失败/提交失败或无有效凭据时，监听器不能正常返回。注入应用 `ObjectMapper`，保留全局雪花 ID 序列化；超过审计容量时不截断原消息。
- 审计不可用时，每次投递总尝试 **3 次**，中间退避 **1 秒、2 秒**，耗尽后明确 requeue 保留原消息；不假设 DLX 已配置而 reject-without-requeue。
- 库存过账原有租户传播、幂等服务、成功清理计数和原失败累计规则未替换为采购逻辑。实际 broker 的 ACK/requeue 和独立 master 提交仍需运行态验证。
- **已知未集成**：现有死信管理重投仅接受 `BaseMessage` 子类，新 Map 审计不能声称已支持页面人工重投；目前只是保存待人工处理。旧 private 采购 helper 仍在代码中，但入口在其前已返回/抛错、当前不可达；将来不能只删除暂停分支恢复该旧路径。

### 2.4 页面与前置来源证明

- MES 监控页一键低库存请购置灰并常显原因，移除错误的提交成功提示。
- ERP 请购表单显示尚未接入的来源占用限制，保留正常 MANUAL/合法来源及历史来源识别；订单表单移除“只预填 BO 却提示已生成订单”的入口，提示到请购列表转单后编辑。
- 断电前已添加的 MRP 只读来源核验器、逐物料 guard、纯需求分配/订货策略继续保留并参与本轮回归。它们**不等于采购事务占用已经集成**，不自动将展示快照或历史运行提升到 v2。

## 3. 本轮新鲜验证

### 后端：定向相关模块 reactor

执行：`mvn -o -pl ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-iot,ruoyi-modules/ruoyi-ai,ruoyi-modules/ruoyi-flow -am test -DskipTests=false -Dprofiles.active= -Dtest=<下列目标类> -Dsurefire.failIfNoSpecifiedTests=false`。

目标类：`AiDecisionP1VersionMappingContractTest,PuPurchaseRequisitionServiceImplTest,PuPurchaseOrderServiceImplTest,PuDecisionDraftReadinessGuardTest,PuRequirementMaterialLockServiceTest,PuRequirementP1MappingContractTest,DeliveryContextAssemblerTest,ErpMrpSnapshotSourceVerifierTest,ErpPurchaseDemandAllocatorTest,ErpPurchaseQuantityPolicyTest,ErpDecisionActionExecutorTest,ErpDecisionActionExecutorTransactionTest,MesSpareErpConsumerTest,MesSpareErpConsumerAcknowledgementTest,MesSparePurchasePauseAuditServiceTest,MesSpareConsumerConfigurationTest,WmSpareStockPostingServiceImplTest,EqSparePartServiceImplTest,MesAndonServiceImplTest,IotDeviceDataQueryServiceImplLatestTest,IotDeviceDataLimitTest,DecisionApprovalServiceTest,DecisionActionExecutionServiceTest,DecisionQueryServiceTest,DecisionSourceFactVerifierTest,DecisionShortageRatioCalculatorTest,IndustrialDecisionNodeProviderTest,IndustrialDecisionProvidersSmokeTest,DecisionWorkflowWiringTest`。

| 模块 | 实际 Tests run | 失败/错误/跳过 |
|---|---:|---:|
| common-AI | 2 | 0/0/0 |
| ERP | 346 | 0/0/0 |
| MES | 50 | 0/0/0 |
| IoT | 4 | 0/0/0 |
| AI | 68 | 0/0/0 |
| Flow | 23 | 0/0/0 |
| 合计 | **493** | **0/0/0** |

18:54:17 返回码 0、`BUILD SUCCESS`。日志：`.codex/full-flow-20260928/resume-boundary-integrated-reactor-20260930.log`。只跑一个 Maven；Maven 768MB、测试 JVM 512MB 堆限额；未构建 `ruoyi-admin`，没有用空 profile 改写它的运行资源。

这不是未指定 `-Dtest` 的全套 reactor，也不是实体制造/IoT 信号验证。ERP 中申请 **52**、订单 **74** 个用例；MQ 范围包含事务注解、序列化、配置/AOP、业务 mock，不代表实际跨库提交或 broker 已验收。

### 前端与 SQL 合同

- `npm run test:unit`：**164/164**，0 失败/跳过；日志 `resume-frontend-unit-20260930.log`。
- 六个本轮页面/脚本定向 ESLint：退出 0；日志 `resume-ui-eslint-20260930.log`。
- 类型检查首次以 2GB 单进程堆限额运行触发 Node heap exhaustion，**不是类型错误，也未停止用户服务**。随后在独立、监护的 4GB 堆 Node 进程执行 `vue-tsc --noEmit`，返回 0；最低可用物理内存 **5628MB**。
- 同样受监护执行 Vite production build，返回 0；最低可用物理内存 **5148MB**。两次任务的用户服务 PID 均仍为运行状态；只可能结束本工具创建的构建/检查进程，未触发低内存终止。构建仍有既有 Sass、chunk 大小/导入等告警，不作全无告警声明。
- 监护证据：`resume-frontend-typecheck-guarded-20260930.json`、`resume-frontend-build-guarded-20260930.json`，同名前缀 stdout/stderr；辅助脚本保留在 Git 忽略的本地工具目录，不提交服务配置。
- `python -m unittest discover -s script/sql/tests -p test_p1_purchase_ddl_contract.py -v`：**6/6**。只验证 DDL 文本/基线合同，**没有执行迁移**；日志 `resume-p1-sql-contract-20260930.log`。
- `git diff --check` 退出 0；保留原有未提交修改，不自动提交/推送/合并。

## 4. 独立业务安全读回

19:04:16 本地 3306 同租户只读事务（回滚后关闭连接）复核：

- run `2105159630382362626` 仍 `WAITING_APPROVAL`；采购动作仍 `PROPOSED / NOT_STARTED`。
- 该 run 对应的未删除 ERP 采购申请数量为 **0**。
- 既有 `CGSQ202609290001` 仍 `DRAFT`。本轮没有替采购负责人处置该申请，也没有重新解释它的来源或单位。

证据：`.codex/full-flow-20260928/resume-legacy-run-safety-readonly-20260930.json`。这只证实列出的本地记录状态，不外推为所有业务行完全不变或真实收货/制造完成。

## 5. 尚未完成及下一检查点（19:04 快照；迁移最新状态见第 6 节）

1. P1 两份一次性增量仍未执行；须先让后端及所有采购/MQ/定时写入口停写，复核可恢复备份及新鲜 schema，才执行已批准的分库增量。MySQL 5.7 DDL 隐式提交，不能宣称整体回滚或直接重放；绝不导入带 DROP 的主脚本，不重放已执行 APS 增量。
2. Task 4–6 仍需接入可信来源/条款冻结、全局竞争池当前读、人工/AI 统一 guard，以及草稿/申请行/占用的同一 ERP 事务；转单、取消、部分到货/退货、补偿和跨库幂等恢复还未做完整运行态验收。
3. P2 的比例纯算法通过不等于评分器/DTO/证据已换口径；旧运行不重排，仍不得把跨单位的历史总缺口当成统一数量。
4. 真实业务采购正向仍缺已核 MOQ 单位/包装倍数及原未结申请处置事实；不能猜成 6 或 510 个。只允许验证安全拒绝，不造收货、报工、IoT 物理事件补齐案例。
5. P1/P2 及原 ERP/MES/IoT/AI 全流程、真实页面、40 个独立来源案例均未完成；不满足合并 `plus` 门禁。

## 6. 20:29 本地停写增量实际落地

用户停止后端后，重新复核无 8080/1883 监听、活动业务事务或客户端；本机 MySQL 5.7.20、实例哈希、旧表基线、批准 SQL 和四份仓库外备份 SHA256 未变。只读审查放行后，20:23:58 分别执行系统/ERP 库 4 ALTER+3 CREATE（7 条 DDL），未 DML、未重放 APS、未导入带 DROP 的初始化脚本。

- 实际恢复范围：同实例独立恢复 schema 中的受影响 **7 张原表**，不是独立实例/全库恢复；验证旧字段定义/索引/全部旧字段行投影 SHA256 后演练同批 DDL。备份及恢复库保留且无凭证/业务备份正文入仓库。
- 活库独立读回：旧 7 表旧定义/索引/行数/旧字段投影不变；15 新列全 NULL，3 新表为空。另以独立工具逐项比对批准 SQL：新增字段及新表 **39 列/13 索引**、默认、注释、列序、InnoDB/排序规则/行格式均符合。初轮验收工具因漏解析 unsigned 与继承字符集失败，经只读证据修正工具后通过，未改 SQL/重跑 DDL。
- tenant `000000` 旧 run 仍 WAITING_APPROVAL、动作 PROPOSED/NOT_STARTED，新版本均 NULL，该 run ERP 申请 0；原 CGSQ202609290001 仍 DRAFT/1 行。旧来源、意图及证据未处置/升级。
- 恢复库 SQL 持久化探针 **6/6**：唯一键、tenant/version CAS、guard 并发竞争及账本/事件同事务回滚。全部 fixture 回滚，三表再验为空；不等于应用层额度/完整采购事务验证。DDL 文本合同另 **6/6**。

本轮命令：

```powershell
python .codex/full-flow-20260928/tools/p1_guarded_migration_20260930.py apply-live
python .codex/full-flow-20260928/tools/p1-full-schema-readback-20260930.py
python .codex/full-flow-20260928/tools/p1-business-safety-readback-20260930.py
python .codex/full-flow-20260928/tools/p1-isolated-persistence-probes-20260930.py
python -m unittest discover -s script/sql/tests -p test_p1_purchase_ddl_contract.py -v
```

各命令实际退出 0（独立结构工具修正后的复测）；凭证仅用授权进程环境，未存配置/报告。JSON/log 文件位于 Git 忽略的 `.codex/full-flow-20260928/`。活库执行工具现在会拒绝再次重放；不得修改 manifest 绕过。

本地结构门禁通过，**业务写门禁未开启**。Task4–6 的统一来源服务/账本接入、订单完整生命周期、跨库恢复、P2 排名与真实页面/API/全流程仍未完成；没有主动启动用户服务，未提交/推送/合并 plus。


## 7. 21:09 账本基础策略、两阶段审查及完整回归

### 7.1 本轮新增代码（尚未接通业务写入）

- `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/service/PuRequirementAllocationPolicy.java`：纯 Java 三个状态转换 `createRequestIntent/commitToOrder/releaseRequestIntent`。显式 MRP_DETAIL 与完整身份、基本数量精确符合 DECIMAL(18,4) 后才能形成 REQUEST_INTENT；订单承接只成为 ORDER_COMMITTED_NOT_TIMELY_SUPPLY 并保持占用；有订单行/承接/释放事实的账项一律不能因申请关闭而释放或重新占用。返回前态/结果及 Date 的防御性副本，归属/幂等键不变，CAS 版本由未来同事务 Mapper 推进。
- `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/pu/PuRequirementAllocationPolicyTest.java`：30 个纯策略契约测试，无数据库/Spring 应用/业务执行。20:35 RED 为 30 正常断言失败、0 error/skip，全部因策略类尚未实现；20:46 GREEN 30/30。
- 它**不是采购授权或可信来源证明**，不查询/锁需求与库存、不计算全局余额、不识别真实下游订单、也不持久化账本/事件。真实订单、单位/额度/权限及下游事实仍须上层在 ERP 同事务资源锁下核验；不得仅凭此类的合法返回解除旧写门禁。

Subagent-Driven：实现 Hubble → Popper 规格审查通过 → Cicero 独立质量审查无阻断/重要问题。唯一 P3 是公共 API 测试只按名称去重，额外同名重载会漏检；现增加公共方法计数 ==3，并在**隔离编译副本**中实证“旧测试+重载变异通过 → 强化测试+同变异以 expected3/actual4 失败 → 原策略+强化测试通过”。生产源码/target 类均未用于变异或改写；复审确认 P3 闭合。策略最终 SHA256 `da0ca3cc6ef697e33ab58b08e5141cfd00691f9f9a9434f667aa2b6401e639a9`，测试 `c238db7dacf862be4c2686649985219541cf59e69ccacec701fef2b1af20e918`。

### 7.2 审查修正后的新鲜非定向 reactor

```powershell
mvn -o -pl ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-iot,ruoyi-modules/ruoyi-ai,ruoyi-modules/ruoyi-flow -am test '-DskipTests=false' '-Dprofiles.active='
```

没有 `-Dtest` 类名过滤；一次一个 Maven，Maven 堆 768MB、Surefire 堆 512MB，未构建/启动 ruoyi-admin，未并行跑前端重型检查。21:09:20 返回 0、BUILD SUCCESS；只统计开始时间之后新写的 269 份 Surefire XML，避免混入旧报告：

| 模块 | 发现 Tests | 实际执行 | 失败/错误/跳过 |
|---|---:|---:|---:|
| common-AI | 108 | 108 | 0/0/0 |
| ERP | 1137 | 1137 | 0/0/0 |
| MES | 634 | 632 | 0/0/2 |
| IoT | 224 | 224 | 0/0/0 |
| AI | 380 | 380 | 0/0/0 |
| Flow | 67 | 67 | 0/0/0 |
| 其余相关依赖模块 | 377 | 377 | 0/0/0 |
| 合计 | **2927** | **2925** | **0/0/2** |

策略 30/30 包含在 ERP 1137 中，不重复累计。两项跳过是既有 `NcrOrderServiceImplTest.autoCreateFromIqc_defectInfoParseFail_safe/autoCreateFromIqc_parsesDefectInfo_defectCodeIdNull` 的 @Disabled；本轮没有运行它们、替换 Spring 上下文测试或复验注释中宣称的旧 E2E。不是“所有测试零跳过”；POM 既有 excludedGroups 也未改。

前端 `npm run test:unit` 新鲜 **164/164**、0 失败/跳过，进程返回 0、1GB 堆限额，串行在 Maven 结束后执行；本轮未改前端源码，没有重复 4GB 类型检查/生产构建，第 3 节是此前的类型/构建证据而非本轮新跑。旧 7 表/状态及新表空表在 reactor 后独立只读回查仍一致。另有离线迁移重放拒绝测试 1/1：已执行 manifest 在连接数据库/写任何文件前拒绝，连接尝试与 DDL 执行均为 0；不是再次连接活库重跑增量。

证据：`.codex/full-flow-20260928/` 的 `p1-full-related-reactor-after-review-20260930.log`、`p1-full-related-reactor-after-review-result-20260930.json`、`p1-allocation-policy-tdd-20260930.json`（首轮 RED/GREEN）、`p1-allocation-api-mutation-regression-20260930.json`、`p1-allocation-policy-two-stage-review-20260930.json`、`p1-post-migration-frontend-unit-20260930.log`、`p1-post-reactor-final-parity-readonly-20260930.json`、`p1-live-replay-refusal-offline-20260930.json`。

### 7.3 当前门禁/下一依赖

- 本轮只有本地结构迁移与账本局部不变量通过，**完整统一申请占用服务尚未接入**。Task4–6 的全局竞争池当前读、可信条款冻结、申请/订单/到货/撤销/补偿同事务，以及跨库幂等恢复仍需实施与运行态验收；P2 DTO/评分/证据接线、真实页面/API及原 ERP/MES/IoT/AI 全流程、40 个独立来源案例仍未完成。
- 新版采购生成/批准/执行门禁仍关闭；没有猜 MOQ、包装、510 个申请的处置，也没有制造收货、报工或物理 IoT 数据。旧 run/action/申请不升级或处置。
- 下一运行态步骤需要用户在 IDEA 按原开发配置重新编译并冷启动后端，确认实际 classpath 含最新门禁与实体；再重新登录复验旧采购动作拒绝、正常手工入口/页面、MQ 暂停审计等。测试编译通过不代表新后端已经加载代码；本任务不自行启停用户服务。
- 全部既有未提交改动保留，仍在开发分支；未提交/推送/合并 plus。整条验收链未完成，不进入 finishing/merge 门禁。


### 7.4 21:21 最终交接只读复核

最终审查/完整 reactor 之后，21:21:14 再次只读复核原 7 表旧投影完全匹配、15 个新增列历史值全 NULL、3 张新表均 0 行；旧 run 为 WAITING_APPROVAL、动作 PROPOSED/NOT_STARTED、既有申请 DRAFT，新版本仍 NULL。分支/HEAD 未变，8080/1883 无监听，用户前端 PID14032 仍在运行，git diff --check 返回0。证据 `p1-final-handoff-readonly-20260930.json`；后端仍由用户负责按原配置重新编译冷启动，不能把这些只读/离线结果当作登录/页面或新版真实采购已验收。


## 8. 21:27 重启后真实页面与服务端门禁复验（续接至 23:30）

### 8.1 新进程、登录及实际拒绝

用户重启后，本任务未启停服务。后端 PID **7344**、NexusApplication/Java，启动 UTC 13:27:14（Asia/Shanghai 21:27）；前端 PID14032，loopback代理PID12024。21:36 读取当前 JVM classpath/标准加载参数，仅保存 PID、时间、16 个门禁/实体/消费者类源及字节码哈希；每类实际唯一 provider，源编辑早于编译且编译早于进程启动，过滤运行资源仍为 dev。原 VM 属性/启动命令/密钥不输出。随后使用当前本地登录配置重新登录，captchaEnabled=false；凭证/token 只在临时内存。

AI 旧运行详情/90 个互异证据/时间线、UOM、新供应条款实体查询、原申请、MES 工单和 IoT 设备 GET 均 HTTP/业务200。真实 HTTP 负向复验（业务 code500 为预期明确拒绝，不当作服务宕机）：

| 路径 | 实际结果及回查边界 |
|---|---|
| 旧采购动作 approve | 冻结版本缺失拒绝；run 不变为 STALE，不创建执行 Flow/ERP 草稿 |
| 客户端额外自称 v2 | 仍从持久化动作判版本，同样拒绝，不升级旧 action |
| PROPOSED/NOT_STARTED 动作 retry | 重试资格拒绝；**不是 FAILED/APPROVED 动作重试门禁的运行态证明** |
| 通用手工新增伪造 AI 头/run/action/key | 来源拒绝，未分配新业务编号/草稿 |
| 未核 MRP 来源新增 | 来源占用拒绝，未生成申请 |
| 原 CGSQ202609290001 改成 MANUAL/清 sourceId | 原头 MATERIAL_REQUIREMENT 已冻结，读取持久化头后拒绝；不等于已在此实库 case 走到仅行来源 FOR UPDATE 分支 |
| generate-from-low-stock | 暂停入口拒绝，不创建/合并/取消原申请 |

前五项每次独立只读对比 tenant000000 的 **21 个相关业务表**（该 run 的 run/action/evidence、该租户 Flow instance/node、ERP 采购/账本/需求/APS/库存、MES 工单/派工/安灯、IoT data）行数及完整行投影 SHA256，均不变；后二项对比8个相关ERP表不变。登录及失败操作日志允许按原系统写入，不能将“业务未变”写成“整个MySQL实例零写入”。未驳回/取消旧运行或原申请，未伪造真实收货、报工或物理信号。

预检工具首次误用不存在的 `/mes/pro/workOrder/list`，修正为实际 Controller `/mes/pro/work/list`；retry 期望文案也按实际服务修正。两次初始失败证据保留；前者尚未发业务负向请求，后者已有拒绝和不变投影。它们是工具假设问题，不冒充产品漏洞或隐藏失败。

### 8.2 真实 UI、已修问题和剩余 warning

在当前 `http://10.152.240.20` 使用本机 Edge，逐次一个 browser/context，临时内存 storage、关闭记住我，无 storage_state/HAR/trace/cookie/token 持久化，截图只业务页。除正常一次登录 POST 外，页面业务非 GET 实际 forwarded/observed/response/blocked 均0，未点击审批/驳回/新分析/保存/下达/领料执行。

- 旧 AI run：真实 DOM 主键字符串精确、WAITING_APPROVAL、1个PROPOSED/NOT_STARTED采购动作、90证据；采购 checkbox 禁用且未选、批准按钮禁用、驳回入口仍可见且未点击，GET 回读状态不变。最终无创建承诺 check 通过。一个外部非API图片GET因安全边界被阻止，不误写成“所有请求0阻止”。
- ERP/MES/领料/IoT：9个列表/详情/工序/实时页面，20项只读/状态/ID/布局检查通过；MES7工序与响应匹配，领料仓库缺失明确空态而不伪造默认仓库，IoT设备及图表读到当前已有历史数据，**未发送新 MQTT 或声明物理实时采集已验收**。
- **修复1**：工单 `plannedQty/completedQty/scrapQty` API 十进制字符串直接传数字控件会触发3条modelValue类型warning。只在详情回填/选计划回填转换这三个数量及对应 scalar，非负/最多4位/安全 scaled 整数/toFixed 精确往返后才给 number；未知/不支持不猜0，ID/UOM/来源/版本/其他字段原样。三控件precision4对齐真实decimal(18,4)，查看禁写、完成/报废禁用保留。
- **修复2**：采购卡片 error 已说明不可批准，但 amber 仍写“审批会启动创建草稿”。只改提示为门禁仍关闭/不会创建草稿/资料核验后重新分析，未放开批准或改驳回。
- 最终真页复验已消除3条数字字符串warning，9页全部检查仍通过；**仍有16条已有warning（12未分类、4其他value prop类型）**，不宣称控制台零warning或所有页面缺陷全修复。未捕获本轮JS error/HTTP业务错误。
- 首轮AI UI工具把所有sourceId强制要求string，实际全局BigNumberSerializer只把超JS安全整数的Long转string；29条CALENDAR/STOCK证据的sourceId是合法小整数，不是精度丢失。工具新增安全小整数/布尔浮点与不安全数字拒绝回归，run/action/evidence雪花主键仍要求精确string；原失败保留，修正后15个离线工具测试及真实页面通过。未修改全局serializer或冻结载荷的ID合同。

### 8.3 新增 P1 当前读基础（不是统一占用事务已完成）

新增 `PuRequirementCompetitionPoolMapper`、`PuRequirementCompetitionPoolReader`、`PuRequirementCompetitionPoolReaderTest`：内部 @DS(erp)+MANDATORY，在已有实际事务/租户匹配/正物料/合法日期下先单物料guard，再固定表序和各表PK排序当前读 MRP明细/头、库存、订单行/头、申请行/头、ledger。无run工单/窗口开始过滤，结束日包含全天；NULL/未知状态/单位/数量/删除标记保留，缺头/跨租户/身份异常整池拒绝。原source_id不重释，released账项保留，不将raw事实标成verified/complete或额度资格，不接公开API/allocator、不启采购门禁。

15测试 RED 为缺类导致的15普通断言失败、0error/skip；GREEN15/15。主线程另在现库 **READ ONLY** 对8个SELECT去掉FOR UPDATE后核验SQL/别名/列均执行成功；没有调用新service、插guard或声称真实锁/AOP/result mapping/完整池认证。只读probe初次去锁顺序没处理script闭合标签而停止，修正工具后通过，未改产品SQL或补数据。

实现Ohm → Mill规格审查合规 → Kepler质量无阻断/重要问题。唯一P3是SQL测试没保护NULL/非canonical del_flag分支：在隔离编译副本证实原测试漏检删分支mutant，再加2断言使mutant失败、批准mapper通过；mapper源/target类未被变异，复审关闭。列表结构只读、raw投影/Date仍是可变未解释事实，不能跨事务作为可信快照。@DS不能将已有master事务自动变ERP同库事务，guard外写/phantom/新鲜度CAS/权限投影仍是后续工作。

**当前reader是在本次进程启动后编译的新增基础件；本轮没有证明用户服务实际注册/调用它，更没有运行新采购占用写事务。** 后续在接线/运行态验收时需再核实际构建和ERP绑定，不将这里的编译或只读SELECT当作完整Task5上线。

### 8.4 新鲜回归/内存/审查

两处前端补丁由Mendel规格及Descartes独立质量审查通过，无阻断/重要问题；当前读基础由Mill/Kepler两阶段审查通过，P3已闭合。各worker不跑重型工具，主线程单独串行Maven/重型前端任务，未启停用户服务。

| 命令/验证 | 实际结果 |
|---|---|
| 完整相关 Maven reactor，无-Dtest、空profiles.active | 270新鲜XML；2942发现、**2940实际执行**，0失败/错误、2既有MES NCR @Disabled未验证；23:13:46 exit0 |
| 新reader定向 | 15/15，包含在最终ERP1152中，不重复累计 |
| 数量UI定向 | 13/13（原4+新9），RED曾4pass/9fail |
| 采购提示SSR/交互 | 14/14，RED曾11pass/3fail |
| npm run test:unit（定向格式修正后再跑） | **174/174**，0失败/跳过，exit0 |
| 五个实际修改Vue/TS/脚本定向ESLint | 格式问题仅修2个测试脚本，复测exit0；未全仓format |
| vue-tsc --noEmit，监护4GB堆 | exit0，最低freeRAM6620MB |
| Vite production build，监护4GB堆 | exit0，最低freeRAM5809MB；既有构建告警不称全无 |
| git diff --check | exit0；未提交/推送/合并 |

相关核心模块实际执行：common-AI108、ERP1152、MES632（另2禁用）、IoT224、AI380、Flow67；其余依赖377。所有计数只从本轮命令开始后写出的XML取值，不用旧报告。类型/构建监护仅可结束本任务自己创建的检查进程；服务7344/14032/12024始终保留，未触发低内存终止。

### 8.5 23:30 安全读回与后续依赖

23:30:52 独立只读复核原7表旧投影完全一致；15新增列历史值全NULL、三表均0行；旧run WAITING_APPROVAL、action PROPOSED/NOT_STARTED、新版本NULL，此run采购申请0；原CGSQ202609290001仍DRAFT。此次未再执行增量，不生成业务新单号，不处置510个意图、不伪造实收/制造。分支/HEAD未变，用户后端/IoT及前端仍在运行。

**Task4–6完整可信条款/来源净额度、统一写事务、订单承接/批准/部分到货/撤销/补偿及跨库幂等恢复仍未接完；P2排名接线、全流程实绩/40独立来源验收仍未完成。** 本轮完成的是P0运行态拒绝、两处真实UI问题和当前读基础，不是“所有ERP/MES/IoT/AI流程完成”；原数据缺包装/MOQ单位/订购倍数时继续fail closed，不猜6或510。不满足合并plus门禁。

主要本地证据（Git忽略目录 `.codex/full-flow-20260928/`）：
- `p1-restart-runtime-class-proof-20260930.json`、`p1-after-restart-api-gates-20260930.json`、`p1-after-restart-legacy-edit-low-stock-20260930.json`。
- `p1-restart-non-ai-ui-e7bf30df/report.json`及业务页截图；`ui-p1-legacy-gate-after-restart-20260930-f7e0db6fd234495fbe787b7c3614a224/report.json`及两张决策截图。
- `p1-competition-pool-reader-red/green-20260930.log`、`p1-competition-pool-sql-existing-schema-readonly-20260930.json`、`p1-competition-delete-marker-mutation-regression-20260930.json`。
- `p1-restart-final-reactor-20260930.log`/`p1-restart-final-reactor-result-20260930.json`、`p1-restart-final-frontend-unit-after-format-20260930.log`、`p1-restart-final-ui-eslint-after-format-20260930.log`。
- `p1-restart-frontend-typecheck/build-guarded-20260930.json`及stdout/stderr；`p1-restart-final-two-stage-reviews-20260930.json`、`p1-restart-final-business-readonly-20260930.json`。
