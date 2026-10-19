# 物料追溯 MySQL 5.7 隔离验证

> **最终冻结输入复验完成。** 2026-10-19（Asia/Shanghai），本轮通用 MySQL **268/268 PASS、0 FAIL、0 BLOCKED**；工具自测 **57/57**；独立 `--release 17` 编译后的取消/journal/payload/兼容契约 **63/63**、MES ACK **21/21**，均零跳过。运行开始、结束及签署前源码一致，SQL/Java 交集哈希一致。仅本节及最终取证目录代表当前结论；下文候选记录保留为历史。

## 最终取证与哈希

工作树 `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp`，基线 `1ab08b09c1be29f65c25d31a4c9b92957a00775c`。最终报告目录：`script/tools/material_trace/tests_mysql57/results/final-frozen-matrix-20261019-10/`；独立 Java 目录：`script/tools/material_trace/tests_mysql57/results/final-java-contracts-20261019-04/`。

- `final-attestation.json`：最终结果、执行范围、命令、七份DDL哈希及清理证明引用。
- `report.json`：268个非零case及原生SQL/参数/返回值/错误/锁证据。
- `source-snapshot-index.json`、`source-snapshot/`：85个SQL矩阵输入的原始字节；Java目录另保存96个实际编译源码的原始字节。
- `cleanup-proof.json`：本轮专用容器清理及删除后精确ID/name/run标签检查。
- `delivered-source-paths.md`、`delivered-source-paths.json`：本线程31项交付源路径（含前续取消契约/适配/测试和本次工具/报告）。

组合源码取并集共 **144个输入**，未包含Station离线客户端。规范哈希文件是UTF-8、按源码路径排序的紧凑JSON路径/哈希对象、无末尾换行；可直接对对应文件执行 `Get-FileHash -Algorithm SHA256`：

| 清单 | 输入数 | SHA256 |
|---|---:|---|
| `final-input-sha256-canonical.json` | 144 | `fe9e9628a9b019ab9219d44f06ee9eafc9419b7d6e0900e988bff301d3637992` |
| `sql-source-sha256-canonical.json` | 85 | `8d63dd4ab40286a9450d8cce94c6e8381cac780a8605f8985977d74a793bc468` |
| `java-source-sha256-canonical.json` | 96 | `dee9f92abb25dfe3badedb120c9dff7fa8004e1bc61650e4294e6450bdd5b71e` |

这是限定输入与实际执行范围的SHA256校验记录，不替代未执行的生产验收或完整应用验证。

## 本轮实际覆盖

实际服务端 **MySQL 5.7.44 Community Server**，REPEATABLE-READ，sql_mode 为 `ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION`。仅使用缓存镜像 `4bc6bc963e6d` 的精确完整digest，专用唯一名、network=none、无端口、无既有卷，数据/tmp均在tmpfs。

真实主安装只提取CREATE TABLE，在临时空库建成 ERP **162表**、MES **89表**；真实HEAD基线DDL先加入合成历史行，再执行当前ERP/MES增量与重放，结构元数据与新装一致。generator/当前增量/preflight/独立strict脚本均匹配；新表 **30张（ERP18/MES12）**。完整快照 **35表**在新装和迁移库完成 **70次**列名/基础类型比对。原始主安装DROP/种子INSERT与真实业务历史均未导入。

实际Java **167个SELECT变体**在新装/迁移库各执行一遍，共 **334次SELECT + 334次EXPLAIN**。覆盖四个reader、ERP canonical find、WO bank/adoption、readiness、barcode绑定及六个DOCUMENT目标、取消/产出/ACK存储查询，以及新 `MesProductionDetailFactsService` 全部 **17条SELECT**；三处LT服务只声明JOIN/EXISTS范围。动态IN宽度1/2/500、布尔分支、只读/锁读和固定单据目标均按完整已知表达式展开，未知拼接拒绝。逐句烟测大多零匹配参数；报告明确每个占位符角色/值与完整绑定SQL。

非零数据行为复跑包括：SN唯一且多个零库存历史允许、tenant隔离、首次缺sequence跨material两连接gap-lock/死锁及新事务重试、号段CAS/锁等待/超时、正式码和历史del_flag码不能复用、ERP↔MES outbox/inbox回滚和重复幂等、JSON空值/长ID精度、SN-only精确关联与冲正、batch-only无SN本体库存/出入流水/客户去向、一个ERP batch多个MES子lot稳定分页、四种未分配WO scope、ERP canonical与MES历史码区分、产品产出约束、WO bank和adoption存储/回滚/两连接CAS，以及零库存非零成本/孤立IN_STOCK/估值组反向余额检查。

严格tenant门禁本轮包含 **14个PASS case**，已计入268而非额外相加：错schema/UUID、缺备份/停写/清理确认拒绝；两表NULL/空白tenant、正式码重复、新版正库存SN缺失/重复在ALTER前停止且原行/列保留。合法基线收紧后两表tenant NOT NULL且无默认回填，显式NULL报 **1048**，重复码/活跃SN报 **1062**，两个零历史行仍允许，脚本重放无元数据变化。旧扩展阶段nullable行为只作历史边界，不能套用为严格入口执行后的漏洞。真实清理凭据、备份、停写窗口仍需目标环境独立核实。

## 取消契约及Java兼容复验

独立runner用本地现有依赖、实际96个当前Java源码，`javac --release 17` 编译；检查关键class major=61，JUnit运行时为现有JDK21。**63/63 + 21/21**通过，源码签署前再核无漂移，没有启动Maven或修imports。公共DTO显式审计三字段、旧四参数/四字段JSON兼容、长actor字符串、部分审计拒绝、损坏source/target payload失败关闭、C1迟到ACK、NO_GOOD矛盾取消及六种投递顺序/重新冻结后重复均已复验。MES测试使用真实H2 journal/projection及模拟ERP source-proof adapter，不能据此声称MQ/真实双库AOP全链路通过。

主线程两项TEST-ONLY更正 `WmBatchFactWriteBoundaryTest`、`ErpDocumentCodeResolverTest` 均不在本轮SQL85/Java96输入集合内，对本签署无影响。主全Maven reactor09及2357个编译源冻结、真实MapStruct/GTIN/VO回归为主线程证据，本任务没有重复启动或认证其最终完成状态。

## 清理与可复现命令

本轮容器 `nexus-mtrace-mysql57-20261018204257-b459c3f27d0d`，ID `988abbbff9e552e4b8c833cef479fc74b2898918e1520e6b2ce41ee373c3cdf8`。harness在删除前校验精确ID、name、run/workspace标签、镜像、network和存储归属，报告 `removed_after_ownership_verification=true`。独立删除后检查确认Docker daemon可达、精确ID不存在、精确name和本run标签均无容器；原始标签值/命令边界见 `cleanup-proof.json`。未启停任何既有容器、库或服务。

以下为实际命令，从本工作树根执行；复跑SQL矩阵必须换未使用结果目录，保留本轮已签证据：

```powershell
$env:PYTHONUTF8='1'
python -B -m unittest discover -s script/tools/material_trace/tests_mysql57 -p 'test_*.py' -q
python -B script/tools/material_trace/tests_mysql57/run.py --output results/final-frozen-matrix-20261019-10
javac '-J-Duser.language=en' '-J-Duser.country=US' '@script/tools/material_trace/tests_mysql57/results/final-java-contracts-20261019-04/compile17.args'
java '@script/tools/material_trace/tests_mysql57/results/final-java-contracts-20261019-04/payload-run17.args'
java '@script/tools/material_trace/tests_mysql57/results/final-java-contracts-20261019-04/cancellation-run17.args'
```

未重复converter apply/revert（由Mill独立验证），未执行生产迁移、真实数据/备份恢复、实际HTTP/权限/MQ全链路、跨独立MySQL实例、物理打印/工位或高负载性能验收。Kant batch MyBatis wrapper只冻结源码，不计为已执行的字面量SQL；Station离线客户端独立。没有提交。结果目录被git忽略，交付时需连同本机取证文件保存。

---

## 历史候选与交接记录（不是当前待办）


> **候选验证，尚未最终签署。** 最新完整隔离 MySQL 候选为 `tests_mysql57/results/schema-barcode-adoption-current-20261019-09/report.json`：**251/251 PASS、0 FAIL、0 BLOCKED**，MySQL **5.7.44**，69 个冻结输入，150 个实际 Java SELECT 变体在新装/迁移库各执行 SELECT + EXPLAIN。ERP/MES 新表 18/12，主安装表 162/89，完整导出 35 表。该轮包括 adoption 实际 SQL/CAS/回滚/两连接锁、barcode 旧主对象 NULL 不回填、六个 DOCUMENT 目标以及独立严格 tenant 入口；专用容器已清理。运行后输入复核无漂移。随后公共取消 DTO 审计契约扩展和 barcode/service/API DTO、batch 写入口冻结范围已追加，因此 251 项仍是候选历史，不作最终源码签署。当前工具离线自测 **57/57**；取消审计扩展独立 **63/63**、MES ACK **21/21**，都零跳过。barcode GTIN→物料映射分支的原两个审查探针已原样独立复跑全绿；当前 guard 加原两探针 **15/15、0 skip**，证据在 `tests_mysql57/results/barcode-review-20261019-02/summary.json`。主端报告 WmSnVo.identityVersion 的真实 MapStruct/长版本字符串回归已补齐、104/104 绿；本任务未独立复跑全部104。等待 Kant batch、Lorentz 两个真实读取字段及 Mill final metadata/DDL 就绪，再统一重跑与签署。旧 199/165/111、旧 18/21 和各旧 source hash 均保留为**历史证据**。



最终矩阵准备记录：`tests_mysql57/results/final-matrix-preparation-20261019-01/preparation.json`。Mill READY handoff 与当前30新表（ERP18/MES12）、35完整快照、18全列只读源及七份SQL哈希一致；generator 与当前增量/preflight/独立strict脚本逐字匹配。主端10文件暂冻清单无漂移。矩阵现准备85个输入、167个实际SELECT变体，其中新增 `MesProductionDetailFactsService` 全部17条SELECT；对应两个service、FlowCard/Step/WO VO和Kant batch guard/服务进入源码漂移检查。Kant batch guard 使用MyBatis wrapper，仅作源码冻结，本工具不声称执行其运行时生成SQL。准备阶段仅运行离线57/57，未启动MySQL/container/Maven、未重复converter；待Kant和Lorentz最终冻结后才执行完整MySQL矩阵与最终签署。

主端本批暂冻源码已保存原始字节：`tests_mysql57/results/main-backend-freeze-20261019-01/source-freeze.json`。本批10文件（barcode guard/service/entity/BO/VO、三个 resolver、readiness guard、WmSnVo）在捕获结束再次核对无漂移。按源码路径排序的紧凑 JSON SHA256 映射清单摘要为 `2a819b4fe27b1463cc591af19b481133e050c15cd3ea065db760a8d8241a6131`；WmSnVo 原字节 SHA256 为 `b84bbe34b224ff45b7a446a469c87182d3317df920817a95289a8af3997134c3`。这是主端子集暂冻清单，不是尚未完成的最终 MySQL 矩阵签署。

common DTO ownership 交接：本 agent 持有 `CompletionCancellationRequest.java` 与 `ErpCompletionCancellationContractTest.java`；Lorentz 持有 MES producer/projection。现公共 DTO 显式字段顺序为 `manifestId, revision, manifestHash, originalEvent, commandId, reason, actorId`，actorId 为 Long、JSON 字符串；旧四参数构造函数和旧四字段 JSON 兼容。审计字段全缺失仅用于历史兼容；一旦出现任一项则三项须完整且合法，原始源 outbox JSON 仍是完整审计/hash 证据。Lorentz 已追加源 JSON 三字段，现有 producer 无需变动即可解析为该公共契约。三条审计新回归先红后绿，长 actor ID `9007199254741997` 字符串往返精确，旧四字段序列化不新增 null 字段。当前独立复验目录为 `tests_mysql57/results/cancellation-audit-contract-20261019-03/`。

251 项实际命令：

```powershell
python -B script/tools/material_trace/tests_mysql57/run.py --output results/schema-barcode-adoption-current-20261019-09
```


199 项候选实际命令：

```powershell
$env:PYTHONUTF8='1'
python -B -m unittest discover -s script/tools/material_trace/tests_mysql57 -p 'test_*.py' -q
python -B script/tools/material_trace/tests_mysql57/run.py --output results/readiness-current-20261019-07
```

新增 readiness 非零行为：同物料零库存 `total_cost=5` 返回异常计数 1、结清后为 0；无库存的估值组 quantity=1/amount=5 返回 1，其他租户库存不能覆盖，历史 0/0 组返回 0。报告保留实际 SQL、每个占位符角色和值以及结果。

候选容器 `nexus-mtrace-mysql57-20261018194258-b845cf685beb`，精确 ID `3f400512875ca7cde4f5ffbd0449fe6b1fd90769e1a7c0dc78d2a17cf3a1dfa6`；`network=none`、无端口、tmpfs、缓存镜像，`removed_after_ownership_verification=true`。运行原始字节、source SHA256 和索引保留在候选目录，不修改旧报告冒充新输入。

旧 ERP stock/SN NULL tenant 观测仅属于**扩展迁移阶段历史边界**。严格阶段已走 owner 新增的独立入口实测：对 NULL/冲突停止，不补造 tenant；合法行加约束成功后 NULL 直接插入被拒绝。不能将旧观测定性为新严格库可绕过漏洞。


独立严格入口实际结果：`tests_mysql57/results/strict-current-20261019-08/report.json`，**19/19 PASS、0 FAIL、0 BLOCKED**，MySQL 5.7.44，67 个冻结输入。入口为 `script/sql/update/2026-10-19-material-trace-strict-constraints-erp.sql`；与 owner `schema_contract.strict_constraints()` 逐字一致。错 schema/UUID、缺备份/停写/清理确认均报 1644；两表 NULL/空白 tenant、正式码重复、活跃 SN 重复或缺失均在 ALTER 前拒绝，坏行保留、tenant 列未改变。合法基线执行后两表 tenant NOT NULL 且无默认回填，显式 NULL 报 1048，重复码/活跃 SN 报 1062，两个零库存历史行允许，跨租户正式码允许，脚本重放不改元数据。独立专用容器 `nexus-mtrace-mysql57-20261018195513-c852d60ff4b0` 已核验归属并删除。全部仅合成数据；备份与维护引用是隔离测试标记，不代表真实备份/停写验收。

取消/payload 与 resolver 的当前独立 Java 证据：`tests_mysql57/results/independent-review-20261019-02/`。从 **96 个当前 Java 源码**独立 javac 编译到该目录，未启动 Maven；编译后输入无漂移。journal/ERP cancellation/损坏载荷 **60/60**，MES cancellation ACK 与独立审查探针 **21/21**，resolver/readiness **23/23**，均零跳过。三个旧红回归已绿；六种三路投递顺序和重新冻结后迟到重复仍只完成一次终态投影。MES fixture 使用实际 H2 journal/projection、模拟 ERP source-proof adapter，不声称实际 MQ/双库 AOP 全链路通过。旧 `18/21` 与旧 source hash 继续保留为历史，不覆盖旧日志。

```powershell
javac '-J-Duser.language=en' '-J-Duser.country=US' '@script/tools/material_trace/tests_mysql57/results/independent-review-20261019-02/compile.args'
java '@script/tools/material_trace/tests_mysql57/results/independent-review-20261019-02/payload-run.args'
java '@script/tools/material_trace/tests_mysql57/results/independent-review-20261019-02/cancellation-run.args'
java '@script/tools/material_trace/tests_mysql57/results/independent-review-20261019-02/resolver-run.args'
python -B script/tools/material_trace/tests_mysql57/results/strict-current-20261019-08/run-strict-only.py
```

严格用例已接入完整 `run.py` 矩阵。独立 strict-only 目录保存本次驱动脚本及报告；它的输出目录限本次取证，重复完整矩阵请换未使用目录。

## 历史：2026-10-19 批次事实、产出及取消快照验证（165 项）

本轮实际报告：`script/tools/material_trace/tests_mysql57/results/latest-readers-batch-cancel-20261019-04/report.json`。**165/165 PASS、0 FAIL、0 BLOCKED，退出码 0**；MySQL **5.7.44**、REPEATABLE-READ，严格 sql_mode。纯离线 harness 安全/解析/参数映射自测 **43/43**。历史 111 项不是当前输入的结论。

当前生成器新增 28 表（ERP 16 / MES 12）；真实主安装 CREATE-only DDL 建成 ERP 160 表、MES 89 表。真实 HEAD 基线空库加合成历史行，再执行本轮实际增量与重放，主安装/迁移元数据一致；32 张导出契约表完成 64 次物理列名/基础类型比对。未运行 converter apply/revert，此部分由 Mill 独立验证。

共 **85 个实际 Java SQL 变体**，在新装和迁移库分别执行，共 **170 次 SELECT + 170 次 EXPLAIN**。四个 reader 覆盖全部 SELECT；ERP cancellation、MES product output/cancellation/store 同样覆盖全部 SELECT；另三处 LT 服务限定 JOIN/EXISTS。动态 IN 保留 1/2/500 分支，召回 predicate 保留 serial true/false 分支。报告每条实际查询的源码行、哈希、SQL、占位符角色、实际值和完整绑定 SQL；smoke 的合成值不代表 Java/HTTP 编排已执行。

非零业务合成用例已通过：

- batch-only 没有任何正式 SN 时，仍返回批次本体的两处正数量库存、来源仓库、IN/OUT/UNKNOWN 历史方向及客户/销售订单；过滤其他租户、删除行和零库存。来源仓库 111 与当前库存仓库 222/333 分开验证，金额精确到四位。
- 一个 ERP 批次对应两个 MES 子 lot、三个 allocation，包含同一 lot 的两条分配；按 allocation_id 跨页无遗漏/重复，排除其他租户 lot。
- 四个 WO scope 实际查询分别验证 SN-only/批次未分配量、完全分配排除、冲正恢复缺口、删除/租户隔离、游标，以及产品 SN 与经精确 allocation 映射的产出批次。SN-only 使用字符串身份 `8000000000000000677`，完整参数与返回值见报告对应 case。
- pro_product_lot_output 的命令行唯一、同租户 lot 唯一、跨租户 lot 可用和 DECIMAL/来源 ID 保真。
- 原有 SN 唯一索引/允许多条零历史、首次不存在 sequence 的两连接 gap-lock 死锁与新事务重试、跨物料号段、正式码/历史删除码不能复用、两库 outbox/inbox 回滚及幂等均重新实跑。

**33 个输入的原始字节和 SHA256 已冻结**，见本轮目录 `source-snapshot-index.json`、`source-snapshot-sha256.json` 和扁平 `source-snapshot/`；扁平名称避免 Windows MAX_PATH。`final_schema_inputs_unchanged` 以及运行后再核对均通过。专用容器 `nexus-mtrace-mysql57-20261018180757-74bf4fb3d3ac` 使用缓存镜像、network=none、tmpfs、无端口/既有卷；核验精确 name/id/标签/镜像后删除，`removed_after_ownership_verification=true`。未碰任何既有容器、数据库或服务。

本轮实际命令（从 worktree 根目录运行，复跑换未使用的输出目录）：

```powershell
$env:PYTHONUTF8='1'
python -B -m unittest discover -s script/tools/material_trace/tests_mysql57 -p 'test_*.py' -v
python -B script/tools/material_trace/tests_mysql57/run.py --output results/latest-readers-batch-cancel-20261019-04
```

Journal 完整性独立 H2/JUnit **60/60 通过**，日志在 `ruoyi-modules/ruoyi-erp/target/completion-cancellation-20261019-a0898a1d/payload-review-green.log`。新增三个物理行破坏回归：target inbox payload 已坏/hash 列未变时拒绝只读重放和 ACK 重发；source append/claim/verifier 拒绝损坏行；source acknowledge 原先存在缺口，已先红再补存储 payload SHA256 校验，不改现有正确 source verifier 或 Coordinator。

取消 ACK **历史**独立 review 证据在 `tests_mysql57/results/cancellation-ack-review-20261019-01/`（相对此工具目录）；该目录 `source-sha256-reactor06.json` 为旧输入哈希，未签作最终版本。历史取消测试加探针 **18/21 通过**：六种 typed result / cancel ACK / original ACK 到达顺序及重新冻结后的重复均未再次解冻；另三项失败已交主线程/MES owner，未修改 MES 业务源码：

1. MES cancellation source outbox payload 尚未携带 commandId/reason/actorId；当前只在 command inbox 留审计，与要求的 source 原载荷审计有差距。
2. C1 typed COMPENSATION_REQUIRED 先投影，C2 已取消成功后，迟到 C1 generic ACK 会被 CANCELLED 分支拒绝，导致该回执重试回滚；应承认旧请求结果但不改变新终态。
3. 原 completion NO_GOOD_OUTPUT 已确认后，矛盾的 CANCELLED 结果仍可通过仅检查 receipt/group ID 的防线。此探针真实执行 MES 投影、模拟 ERP 来源实证；这是防御性契约缺口，不代表当前 ERP 服务会生成该组合。

SQL 结果只证明本轮隔离 MySQL/合成数据与冻结源码；不证明 Java 实际双库路由、认证/权限、HTTP/MQ 全链路、生产部署或存量业务库迁移。当前未发现须改 owner reader SQL 的 MySQL 错误。以下原记录保留为历史，表数/用例/输入哈希以本节和当前报告为准。

---

本轮日期：2026-10-19（Asia/Shanghai）。工作树 `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp`，分支 `codex/material-trace-20261018`。

此前冻结轮次结果：**工具单测 23/23；实际 MySQL 矩阵 111/111 PASS、0 FAIL、0 BLOCKED，退出码 0**。本轮主线程 MesIdentityTraceReader 和 Lorentz 的 LT JOIN 修复均已进入验证快照。此前 1267 查询失败已在实际新装库、真实基线迁移库消除；不得继续把旧报告的 1267/阻塞状态当成本轮结论。约束边界观察用例的 PASS 只表示实际行为被记录，不代表应用守卫已经验收。

完整证据：`script/tools/material_trace/tests_mysql57/results/all-readers-binary80-20261019-01/report.json`。`results/` 忽略跟踪，保留本机证据；`results/latest.json` 随最近一次运行更新。旧轮次保留用于复现历史缺列/1267 与定位工具问题，不能冒充当前结果。

2026-10-19后续输入已变化：generator现有28张新表（ERP16/MES12），完整业务快照32表，新增实际产品产出及身份退休审计。当前harness纯离线自测为33/33；新增召回SQL两个布尔分支、产品产出writer、ERP取消服务、MES取消请求及ACK投影和公共取消契约/journal，源码快照也包含harness自身五个执行文件，共32个输入。以下111项及17输入哈希仅代表原轮次，不能用作当前最终验证。ERP/MES取消及其他owner源码冻结后，主线程需冻结最终源码和六份DDL另起结果目录重跑，并确保final_schema_inputs_unchanged通过；本次schema/converter切片只生成文件，没有运行真实MySQL或服务。

主线程在其独立MySQL验证授权内可使用新输出目录执行：

```powershell
$env:PYTHONUTF8='1'
python -B script/tools/material_trace/tests_mysql57/run.py --output results/final-cancel-output-lifecycle-20261019-01
```

## 隔离边界和版本

本任务仅写本测试目录及本指南。未改 generator、安装/增量 SQL、Java 或其他仓库，未提交，未读取用户连接配置，未接触既有数据库、容器或服务。

实际 MySQL **5.7.44 Community Server (GPL)**，隔离级别 **REPEATABLE-READ**；Docker client/server 29.5.3。只使用缓存镜像 `sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb`，不 pull、不安装其他依赖。实际 sql_mode：

```text
ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION
```

专用唯一容器 `nexus-mtrace-mysql57-20261018164642-5c8ddca56920`：network=none，无端口、bind mount 或既有卷；数据 tmpfs 768 MiB、/tmp tmpfs 64 MiB，内存 1 GiB。仅经 docker exec 本地 socket 使用合成数据。正式 mysqld 就绪要求 skip_networking=0，避免误用官方镜像启动时的临时 socket 服务。

每次操作及清理核验精确 ID、唯一名称、镜像、run-id/工作树标签、网络和存储；最终仅删除该容器并复核 ID 已不可 inspect。报告 `removed_after_ownership_verification=true`，标签筛选确认本测试容器剩余 0。拒绝远程 Docker endpoint，无既有容器或外部 DB 参数入口。

## 可重跑命令

```powershell
Set-Location E:/Nexus/.worktrees/material-trace-20261018/nexus-erp
python -B -m unittest discover -s script/tools/material_trace/tests_mysql57 -p 'test_*.py' -v
python -B script/tools/material_trace/tests_mysql57/run.py --output results/all-readers-binary80-20261019-01
```

以上是本轮实际命令。复验请换新的输出目录；省略 --output 时自动生成唯一时间/随机目录。输出不能逃出 tests_mysql57。-B 禁止跨目录生成 pyc；只使用 Python 标准库和已缓存 Docker 镜像。

真实迁移使用独立 mysql 客户端且不带 --force；只有断言预期负向错误的合成连接带 --force，以便观察错误后显式回滚。不得忽略非零退出码或删除失败用例来声称通过。

## DDL、迁移和导出契约

旧基线来自真实 Git HEAD `1ab08b09c1be29f65c25d31a4c9b92957a00775c` 的安装 DDL。只提取实际 CREATE TABLE，排除 DROP/种子 INSERT/业务数据。当前 ERP **158 表**、MES **88 表**建表成功；旧 ERP **144 表**、MES **77 表**先加合成历史行，再执行真实增量及复跑。安装/迁移元数据一致，preflight 能执行；缺目标库/备份确认、错库、列/索引漂移和已有活跃 SN 冲突都实际拒绝（1644）。此处备份引用只是合成空库基线标记，不是既有库备份恢复证明。

ERP 14 张、MES 11 张新增表均已覆盖，包括打印任务、ERP audit、ERP 实际预留头/行/消耗三表和 MES 冻结材料分配表。source_allocation_id 及来源索引已落地并通过安装/迁移对照。SN-only 允许 NULL 批次，长 ID 和 DECIMAL 精确保留；应用来源核验、额度、状态及冻结后不可修改不由这些 SQL 测试证明。

wm_trace_posting_group 新字段在两库路径都通过：source_hash 为 CHAR(64)、ascii/ascii_bin、NOT NULL；policy_version 为 BIGINT NOT NULL。显式 NULL 都报 1048，hash 大小写比较区分，9007199254740993 策略版本精确读取。未执行 Java hash 计算或策略授权。

30 张导出表从 core 的 REQUIRED_TABLES 与冻结 metadata 读取，在新装/迁移库进行 **60 次物理列名/基础类型对照**，并核对导出键定义存在。未导出真实业务数据、未执行 converter，既有中间态缺列及历史行转换不属于 HEAD 基线迁移证明。

## 所有实际 reader SQL 与 LT JOIN

直接从本轮源码提取 SELECT，包括 Java 文本块。动态 IN 扩展 1、2、500 个 identity；ERP 来源 SUM 覆盖批次分支和追加 SN/stock 条件分支。未知拼接形式或参数漂移直接报错，避免只测试静态前缀。提取/绑定有独立单测，忽略 SQL 字符串中的问号，保留长字符串 ID。

| 源文件 | 验证范围 | SQL 变体 |
| --- | --- | --- |
| `ErpIdentityTraceReader.java` | all SELECTs | 8 |
| `MesIdentityTraceReader.java` | all SELECTs | 10 |
| `MesWorkOrderReferenceReader.java` | all SELECTs | 1 |
| `MesErpSourceReader.java` | all SELECTs | 9 |
| `MesMaterialTraceWriter.java` | LT JOIN/EXISTS | 1 |
| `MesCompletionManifestService.java` | LT JOIN/EXISTS | 1 |
| `MesTrackingUsageService.java` | LT JOIN/EXISTS | 5 |

共 **35 个实际 SQL 变体**，分别在新装和迁移库执行 SELECT + EXPLAIN，即 **70 次实际 SELECT 和 70 次 EXPLAIN**。四个 reader 覆盖全部 SELECT；三处 LT 服务覆盖其 JOIN/EXISTS 查询，不声称执行了这些服务的完整写入方法。大多数逐句烟测使用零匹配参数，以覆盖解析、列/函数和 collation；有数据行为由下述独立用例覆盖。

主线程 identity/consumption/manifest/forward/batch 查询以及 Lorentz 的实耗来源、冲正提示、完工核销读取和策略就绪 JOIN 已实际通过，不再出现 1267。没有修改 SQL 来规避错误，运行的二进制比较来自 owner 源码。

SN-only 行为证据：

- 正向关联只返回证据中准确组件身份，9007199254740992/9007199254740993 不混淆；同本地 lot 的其他 SN 不扩大关联。全额冲正排除、部分冲正保留、DISTINCT、三个 JOIN 租户边界、缺路径/null/空身份、游标及 limit+1 通过。
- 实际反向 consumption 查询保留 NULL ERP batch、组件正式 ID 9007199254740993、数量 1.2500；跨租户无结果，entry cursor 不重复。实际冲正提示 SQL 和完工实耗 JOIN 也读到原记录。
- 实际 ERP 来源数量 SQL 在 NULL batch 下，批次范围总量 3.5000，指定 SN+原库存行后为 2.5000，错误原库存行为 0.0000；其他批次/租户不混入。未调用 Java 事件授权方法。

## BINARY(80) 比较与执行计划提醒

实际 MySQL 验证同 tenant 相等、大小写差异和尾部空格不相等；20 个四字节字符恰好 80 字节，最后一个字符不同仍不相等。该修法消除了新旧列的隐式 collation 冲突。

固定 BINARY 会补零。实测下面比较为 1：

```sql
SELECT CAST('901001' AS BINARY(80)) =
       CAST(CONCAT('901001', CHAR(0)) AS BINARY(80));
```

因此这里的“精确”以规范 tenant 不含 NUL 为前提；未验证认证入口的 tenant 校验。没有把此观测写成已发现应用越权。H2 未在本任务执行，不能由 MySQL 结果代替 H2 验收。

当前 affectedByBatch 的 allocation EXISTS 在合成数据 EXPLAIN 中使用 idx_lot_allocation_mapping、type=index、估算 11 行。只在验证查询中试验增加 a.tenant_id=? 并保留二进制 guard 后，变为 type=ref，key_len=99，ref=const/component_lot_id/const、估算 1 行。建议 owner 评估同步增加该租户等值谓词与实际 Java 参数；本任务没有改 owner SQL。此为小数据计划观察，不是生产耗时或 SLA 结论。完整原始/试验计划在 actual_batch_recall_binary_tenant_explain 证据中。

## 锁、码占用和消息事务

- 有租户、tracking_version=2、正库存的活跃 SN 重复被 1062 拒绝；零库存历史行允许，version=1 旧正库存不静默升级。租户限定库存更新、每租户正式码和 MES identity 唯一性通过。
- 两连接已初始化 CAS：先提交者取得 1–3，陈旧 CAS 更新 0 行，新事务取得 4–5；FOR UPDATE 阻塞/释放和 1205 超时有原生证据。
- 首次缺 sequence 的旧锁读变体复现 1213；当前路径包含历史码 LIKE FOR UPDATE 时，也能形成 wm_sn gap 与 sequence 的死锁。完整回滚后新事务读取已提交序列、跳过首次扫描，两物料最终取得不重叠 [1,2]、[3,4]，计数/版本为 (4,2)。不调用 Java TransactionTemplate 重试器。
- 正式码和 del_flag=2 历史码均不能按新物料/规则复用，唯一键报 1062，旧 identity 不变。保存点撤销冲突半批、保留外部已推进的 1–3 空号窗口，下一段 4–6 成功。
- ERP→MES、MES→ERP 都验证本地业务+outbox 回滚、源提交后消费业务+inbox 回滚、重试、重复回执不二次生效、stream 版本冲突及 tenant/event 唯一键。两个 schema 在同一隔离实例，分别连接和提交；不是分布式数据库、Java/Spring 或 RabbitMQ 完整链路。

## 仍保留的约束边界与未测范围

以下为旧轮次**扩展阶段**物理行为：ERP wm_material_stock、wm_sn 的 tenant_id 当时可 NULL；显式 NULL 时租户组合唯一键不拒绝这些行。不得据此推断 owner 新增独立严格阶段入口执行后的约束行为。报告 constraint_boundaries 和 boundary-null-tenant-*.sql 保留复现。MES pro_sn 已改为 NOT NULL，实测 1048。23 张含 tenant 的新增表拒绝 NULL；两个 repair_run 本就不含 tenant 列，不对此断言。应用租户守卫、数据库行级安全没有因此被证明。

JSON_UNQUOTE/JSON_EXTRACT 的 10 组烟测通过：SQL NULL/缺路径为 SQL NULL，JSON null 为文本 null，空字符串保留；三个长 ID 的 JSON 字符串/整数形式均精确提取。evidence_json 为 LONGTEXT，非法 JSON 实际报 3141，仍需应用保证合法 JSON。

未执行 Java/Spring 事务模板和 AOP/JDBC/HTTP、H2、权限入口、RabbitMQ、完整业务过账、跨独立 MySQL 部署、converter/中间态历史转换、真实备份恢复、生产迁移、打印机/工位验收。种子 INSERT 和真实业务历史未导入。不以本轮通过替代这些验收。

## 输入快照

运行开始冻结 **17 个输入**，结束全部复核未变。下列哈希确定本轮所验证版本，不等待其他流程完成；后续 owner 源码变更应保存新目录重跑。

| 输入 | SHA256 |
| --- | --- |
| `script/tools/material_trace/schema_contract.py` | `6e8e787df951a54d4809eed038f57cc7fdef150f93f9a9b66b071a62fabb7a4c` |
| `script/tools/material_trace/core.py` | `75086b6260d1e9c00ae34207dabd33c7e1af1e94db70cb68ff1d92d988f4a3a5` |
| `script/tools/material_trace/metadata.py` | `26db202dcdcfe21e92a099c44018ebc4ee9cb58ca0498fd9d1799250cce2d460` |
| `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpSerialIdentityService.java` | `0b42637cb326d4ff84cc51834754a48cbb087027eb54db0bb9831d6f17d324ca` |
| `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/integration/trace/MesIdentityTraceReader.java` | `1ff4713c4dff78f5a671be76b2d8a67b92ee0e6cdcbefc3176acd52f42702f90` |
| `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpIdentityTraceReader.java` | `8ab1fd60d6cd9e9e8afa92327d23478340286b1f3e663195bd69982e86a3400d` |
| `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/integration/trace/MesWorkOrderReferenceReader.java` | `d874a0c4726f5b366ef8525d162c3864210d0ff06e17bd88319c8486b0ebd5e5` |
| `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesErpSourceReader.java` | `6d1b9b66e565d84582839d0494a7344015dad16fd41f06c47eed2f69c8ad8d41` |
| `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesMaterialTraceWriter.java` | `85ee25d526c284b3d8da61083f6ff301c511a58b50ec003deee9923bcfefe640` |
| `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesCompletionManifestService.java` | `0f58d4160793a5bc5ab5216828c500760f1796b092dc4456bbb81b260efdc641` |
| `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesTrackingUsageService.java` | `0482acc6b76125339aae756fc2824771e68fb46a29954e75af0c481b3e31d7cc` |
| `script/sql/ry-erp.sql` | `56707a5f726b8130b7eaf5d32f3239e4f499cfad48ffe2af0e038de0baa33d97` |
| `script/sql/ry-mes.sql` | `e16035849f72e879390cbcbdaf5933691c95c37b6e3360abc43bd4f3d049c0a1` |
| `script/sql/update/2026-10-18-material-trace-erp.sql` | `1b153b4fb0d62d9cf446961102d3dc1c8df0fcc07591f058f8ba0b109d7a4889` |
| `script/sql/update/2026-10-18-material-trace-preflight-erp.sql` | `f69a7eabdbab2976e0bf9eef9f6067be3f04997d5cfc916ca7933a670ab50f60` |
| `script/sql/update/2026-10-18-material-trace-mes.sql` | `3335ca904eb0ba0fc4ac7cd0568daaf7010c85962f3f3a374309fc94a596a801` |
| `script/sql/update/2026-10-18-material-trace-preflight-mes.sql` | `80987158329cebbfe54e123c32c920bcfea211d365056d57d2f6947ba9d34a89` |
