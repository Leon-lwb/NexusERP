# 本机 3306 全流程验收工具与运行边界

> **2026-10-08 仓库清理说明**：用户已授权删除 `script/sql/update/` 的历史增量。依赖这些固定输入的一次性迁移入口已退役，缺少输入时必须在连接数据库前拒绝；历史阶段报告只用于追溯，不是当前迁移操作手册。主 SQL 仅用于全新环境，不能作为存量库替代升级入口。见 [数据库变更记录](../../system/数据库增量变更记录.md)。

本目录包含本机 ERP/MES/IoT/AI 全流程验收的护栏、只读盘点、具备显式单步执行门禁的 Runner、断言和阶段报告。只读盘点不会创建业务数据或导入 SQL；业务单据状态由获得授权的业务 API、MQTT、RabbitMQ 或受保护 Flow 改变，不通过直接 UPDATE 库存、审批或 AI 结果来制造成功。

## 最新原业务主线终验（2026-10-10）

见 [原报价显式处置与三原单全履约闭环验证](reports/2026-10-10-ai-原报价显式处置与三原单全履约闭环验证.md)：2026-10-10T10:39:34.727693+08:00 本机新鲜只读对账确认9原采购RECEIVED、3原制造／IPQC／唯一自动入库和3原销售／OQC／履约均闭环，成品及已发各66。原UNKNOWN字节保留，最后原报价通过原键显式处置在当前时点录入；不声称历史未知成功。8935实际后端测试、前端类型／构建及新JAR健康通过。后续菜单补数、40独立案例／SLA及正式竞赛材料仍另验。旧阶段报告只代表其各自时点。

## 当前批次的最高优先级运行约定

- 服务生命周期遵守**当次任务的明确授权**。用户持续授权本任务中自行重启并直接运行JAR，无需重复询问。2026-10-10T03:45:42+08:00本轮仅停止本项目旧JVM3352，启动工作树新JAR JVM20252；03:47:27核8080、dev、两actor正常认证，新版本已实际支撑后续采购／制造接口，自动DDL／SQL初始化关闭。旧JVM3352及其历史health=UP只用于当时证据；本轮未取得监控健康认证，**不签发新health=UP，也不由匿名401判定DOWN**。最新运行身份及验证以[本轮续接报告](reports/2026-10-10-ai-原单版本恢复与采购制造主线续接验证.md)为准。此授权不用于启停Docker基础服务、其他项目或生产服务。

- 开发保留在独立分支；全部流程、回归和审查完成前不合并 `plus`，不把局部通过写成生产部署或人工验收。
- 本机数据库已获得用户明确读写授权。不要求把密码另存为 `.mysql-readonly.env`；既有正常登录的私有会话缓存仅留在 Git 忽略目录，密码、Token、私钥及原始认证输出不得进入报告或提交。
- 管理员只读盘点的 `writeEligible=false` 表示该快照不是写事务授权；它不否定用户另行授权的、经过业务权限/来源/数量守卫的 API 执行。禁止把只读 transport 的拒绝写操作改成无限制写入。
- 有编号规则的对象使用服务端编号；没有正式编号的采集、告警、AI/Flow、产能日历用真实内部 ID 留痕，不编造 `DEMO/TEST/MOCK` 业务名称或编号。
- 2026-10-02 Station 单件写入口及独立客户端配套已按用户授权分段保存，隔离回归/原生编译器缺失及未执行MES迁移边界见 [Station阶段报告](../../station/2026-10-02-单件过站写入口与客户端配套阶段报告.md)；不转授为用户JVM加载或现场验收。
- 最新修复及正式数据准备见 [库存未知来源、NCR与案例准备报告](reports/2026-10-02-ai-库存未知来源NCR与正式案例准备阶段报告.md)：接真实决策装配/缺料消费者，不再把缺行或NULL库存当可靠零；NCR两禁用占位已恢复真实解析及持久化失败回归；40例工具的计划/来源创建/现场实绩分开计数。该报告批次当时真实业务来源创建为0，不代表2026-10-06最新数量；仍不授予采购或40例SLA验收资格。
- 采购占用归属链前批见 `reports/2026-10-02-采购占用账本归属链核验进展.md`；其445定向及3695实际相关回归为该阶段证据，仍未认证完整库存／采购额度或接统一写；前阶段为 `reports/2026-10-01-ERP物理事务与目录来源核验进展.md`，此前为 `reports/2026-10-01-重启复验与采购事务基础进展.md`，更早进展见 `reports/2026-10-01-全模块正式数据与功能修复进展.md`，历史基线见 `reports/2026-09-29-local-acceptance-progress.md`；敏感的本地意向、只读快照、ID 清单、截图和日志仅留在被忽略的 `.codex/full-flow-20260928/`。

- 2026-10-06 采购接线开发检查点见 [AI逐来源采购开发核验](reports/2026-10-06-ai-逐来源采购整链接线与恢复开发核验.md)；该报告的根编码拒绝/待重启只代表其当时状态，不作为2026-10-07仍未执行的结论。
- 2026-10-07 重启后实际仓储八阶段及剩余范围见 [八阶段实测与任务核对](reports/2026-10-07-erp-重启后仓储八阶段实测与剩余任务核对.md)：已存在本地软件采购→制造→销售主链，统一writer/AI v2/P2排名已有代码及限定运行；不把缺口误报整体未实现，不以重叠保护记录充独立案例。
- 当前生产工单单位、报工自动编号及来源失败定位开发见 [生产单位/报工编号/来源诊断](reports/2026-10-07-erp-生产工单单位与报工编号修复及来源诊断进展.md)。最新代码仍需正常构建/重启后的API验证；新SCJH20260010尚无工单，来源预览尚未取得成功；代码/单测不等于全来源、全部业务或40独立来源SLA通过。

## 最新续接：原单版本恢复与采购制造主线（2026-10-10）

见[原单版本恢复与采购制造主线续接验证](reports/2026-10-10-ai-原单版本恢复与采购制造主线续接验证.md)：七原订单出生版本已受控恢复COMMITTED，正常Mapper不再把NULL默作0；完整后端8,903及公共Python175新鲜通过，已运行工作树新JAR JVM20252，不沿用下文旧运行身份。三原MES工单及3原流转卡已正常RELEASED，第二／第三根已完成实际领料、4工序派工报工、2 IPQC、MES／ERP完工及2唯一自动成品入库合计44，2原OQC及对应原销售出库／履约完成，合计已发44；第一根缺料仍未制造。原九采购单已有8张完成IQC／入库／永久CONSUMED并原需求预留，第二原单UNKNOWN保留不重发；主线未闭合，不开始其他菜单批量补数，不把普通本机构建写成受信SLA或生产部署。

## 最新业务进展：资料补齐、原单入库与制造准备（2026-10-10）

见[本机业务资料补齐与原单入库制造准备验证](reports/2026-10-10-ai-本机业务资料补齐与原单入库制造准备验证.md)：用户明确范围为本机业务测试数据，9份正常业务名称供货资料全部生成；2原单完成独立actor报价确认、正常批准、原到货／收货审批、2原生IQC／6项目、批次／储位、认证入库与永久CONSUMED回写，数量22／44，采购RECEIVED、收货FINISHED。6单位通过现有服务补齐／确认，26／27冻结族和14材料／134MRP原生行不变；3原制造主工单正常下达，产生3领料草稿／9行、MQ实际建立3精确ERP引用MES工单／6工序，但MES仍CREATED、实际已领／已完0，未开工／报工／成品入库或发货。

公开离线新鲜111测试、同50个-O复跑、私有once18及正常API／原生对账通过；中文注释两工具经过限定独立审查，无新Maven模块、Java／前端生产代码改动、重启或DDL。7旧单仍DRAFT／NULL：已补当前完整头行／命令／21因果事件／已查下游空集，但原canonical前像未找到、现场ON UPDATE审计列和真实事务Port未解决，资料不可提交、补缀未放行。两个本轮UNKNOWN原字节保留，未重发／换键；原P07/P08/P09脚本不运行。仍仅3原来源／9原动作／915证据，不扩充40案例／受信SLA、实物或生产验收；详细剩余任务见阶段报告。

## 前一阶段闭环：业务功能线PC与关机恢复验证（2026-10-09）

见[业务功能线PC闭环与关机恢复验证](reports/2026-10-09-ai-业务功能线PC闭环与关机恢复验证.md)：认证采购订单、到货／收货工作台及单位数量核验入口已在现有模块接线，共享固定会话／UNKNOWN护栏、旧CRUD／Flowable及迟到回调保护和本阶段已发现P1/P2收口，无新Maven模块。dateSQL真机1064已定位并最小加入FROM DUAL；重打包后4类context及9原单／两类仓储详情正常认证读取成功。

最终后端545报告／8,885实际testcase、前端标准1,642及type-check／build／定向lint通过，离线38通过；新JAR JVM3352在2026-10-09T20:30:48.407943+08:00健康UP。原9ORDERED/v2申请／9未定价DRAFT订单、旧7NULL／新2零版本、3来源／915证据不变，90event的NULL序号0但列仍nullable，无DDL。本轮报价／单位确认／收货／制造／发货新增0。运行证明仍412／BLOCKED／ATTESTATION_BUILD_UNPROVEN，历史JDBC/AOP UNKNOWN、真实资料／履约、40案例／受信SLA及正式提交材料仍未完成；不把页面、单测或健康检查当人工E2E／生产验收。

## 前一阶段闭环：认证采购生命周期 PC 与真实 JDBC（2026-10-09）

见[认证采购生命周期PC接线与真实JDBC验证](reports/2026-10-09-ai-认证采购生命周期PC接线与真实JDBC验证.md)：已补query独立只读主体、版本化批准／转单专属对话框和列表／表单旧路径保护；UNKNOWN作用域锁及迟到回调、稳定回执归属、producer真实33字状态兼容P2均经回归和独立复审关闭。真实production Mapper／MyBatis／H2两会话认领10例及相关160通过；不是MySQL、生产租户插件或Milvus网络故障。

新鲜完整构建541报告／8,828实际testcase、前端标准973项及类型／构建、精确定向lint／format、离线守卫38通过。挂载39含1 bootstrap＋38行为，不把测试数充工业案例。真实前端传输P08为API成功而旧UI UNKNOWN，未重发；修复后P09一次回查SUCCEEDED／replayed，原生数据未变。14:43九单API／原生只读对账仍9申请ORDERED/v2、9未定价DRAFT、3原来源／915证据、全表90事件NULL序号0；旧7版本NULL、新2版本0，列仍nullable，无DDL，本阶段业务新增0。

新JVM27972曾UP后于14:15:42发生native memory退出；同一JAR14:30隐藏窗口恢复，实际子JVM31696于14:46健康／正常认证UP，自动DDL／SQL init关闭。历史JDBC根因UNKNOWN、长期稳定性未证明；浏览器全持久记录丢失且写全失败后硬reload的UNKNOWN恢复／跨进程CAS、真实报价／履约、数量库存补证、存量DDL、40独立案例／受信SLA和正式PDF／视频／回执仍未完成。

## 前一阶段闭环：采购转单与执行器隔离故障矩阵（2026-10-09）

见[采购转单闭环与执行器故障矩阵验证](reports/2026-10-09-ai-采购转单闭环与执行器故障矩阵验证.md)：阶段累计9张未定价DRAFT订单，9申请均ORDERED/v2、原AI绑定不变；旧JAR创建7单的NULL版本原样保留，新JAR续接2单实际版本0。九单精确来源／数量／单位、COMMITTED永久command及0/1/2追加事件经API／原生独立对账；P08成功转单重放回收同一订单，台账／事件不变，不重发P07未知标签。全表90采购事件、NULL序号0，列仍nullable；没有补报价、ETA、收货或现场实绩。

订单出生版本和最早根读取有限诊断已TDD／独立审查，知识真实小型executor隔离矩阵10例及保真P2修复经独立复审。新鲜完整构建539报告／8804实际测试、前端可计数Node788、类型／构建及离线守卫38通过；新JAR的dev JVM21372已健康UP，自动DDL／SQL初始化关闭。首次运行JAR锁导致打包失败的证据保留；真实root／MySQL CAS／Milvus网络故障未注入，历史精确根因仍UNKNOWN。原3业务来源／915当前证据不扩充40案例，真实报价、数量与库存补证、履约、存量DDL、受信SLA及正式PDF／视频／回执仍未完成。

## 前一阶段闭环：重启后审批执行与有限诊断（2026-10-09）

见 [采购建议执行闭环与安全失败诊断验证](reports/2026-10-09-ai-采购建议执行闭环与安全失败诊断验证.md)：三个原业务来源的9个采购草稿动作均已正常审批／执行成功，9草稿及其9行／占用／创建事件均经API与原生独立对账。第二、三组先被正常freshness置STALE，再重新分析和审批；当前三成功run共915证据，重算与动作数量不扩充独立来源。

正常收口释放额度后已取得SHORTAGE_SIMULATION_INCOMPLETE实际运行拒绝（无风险分数／动作），成功采购retry正常复核既有结果而不重复建单。新增Flow有限内部失败诊断已TDD与独立审查，日志不含原异常内容／业务身份，不改公开码或执行检查点；上轮未知读取失败的精确根因仍UNKNOWN。最终新鲜537类／8766后端测试通过，前端类型／构建通过；新JAR的dev JVM33568已健康UP，重启后9草稿与typed拒绝／安全诊断路径复验通过。数量来源、下游真实履约、40案例／SLA和正式竞赛材料仍未完成。

## 前一阶段运行复验：直接 JAR、重算与知识导入（2026-10-09）

见 [JAR 直接启动与重算／知识导入运行验证](reports/2026-10-09-ai-JAR直接启动与重算知识导入运行验证.md)：重新 package 的 536 类／8754 测试通过，复核增量复用的 JAR 后已直接启动，实际 8080 JVM 为 56944，正常认证及配置 Basic 下健康 UP。三个当前同源重算结果共 915 条证据／9 个未执行提议，原三份 SUPERSEDED 历史结果的 927 条证据摘要不变。

本轮保留一次采购冻结／动作提议失败，同哈希单独重算成功仍不等于已定位或修复并发根因；缺料 typed 负例被活跃额度提前拒绝，没有运行覆盖该错误码。另正常导入 1 份内部规范核验文档／3 片段，关联正确且完成后 retry 被拒绝，向量关联未变。新增独立业务来源、审批／执行、数量认证及 DDL 均为 0；剩余真实来源、40 独立案例／同构建 SLA 和竞赛提交材料仍未完成。

## 前一阶段：前端与数量来源核验（2026-10-09）

见 [前端契约回归与数量来源只读核验报告](reports/2026-10-09-ai-前端契约回归与数量来源只读核验.md)：流程触发显示及标准回归入口已脱离未跟踪草稿／退役迁移；格式化后完整前端可计数 Node 测试 788 例通过，类型检查和生产构建成功。原 13 个未跟踪文件保持不变。

本机只读核验的 14 个物料／5 个声明单位仍未满足严格根单位合同；3 个待审批 run 的 `valid_until` 为 NULL，快照已跨日，但没有运行时 freshness／重算证据，不能断言过期或继续有效。存量 `event_sequence` 仍允许 NULL，63 行中 NULL 为 0 不等于已迁移。该前端／只读阶段尚受启动方式限制且业务新增为 0；后续 JAR 已恢复，最新运行状态以本页上节为准；续接顺序见 [续接计划](../plans/2026-10-09-ai-运行恢复与前端回归续接计划.md)。

## 正式案例就绪离线盘点（2026-10-08）

`formal_case_readiness.py` 只读取显式提供的案例、黄金期望和逐来源证据，不联网、不写库、不创建业务对象。它分别报告计划数、独立业务来源、分析通过、预期安全拒绝、执行闭环和单构建性能资格；同一工单的多个 runId 不算多个来源，旧 SHA／其他 buildId 不能拼入本轮 SLA。

以下是**空证据负例**，应返回 `BLOCKED` 和退出码 `2`，不是现库工单为 0，也不是验收通过：

```powershell
$head = git rev-parse HEAD
'{"sources":[],"runs":[]}' | python -X utf8 -B doc/ai/full-flow/scripts/formal_case_readiness.py `
  --cases doc/ai/benchmark/delivery-risk/cases.jsonl `
  --expected doc/ai/benchmark/delivery-risk/expected-results.jsonl `
  --evidence - --tenant-id 000000 --commit-sha $head --build-id evidence-not-collected
python -X utf8 -B -m unittest discover -s doc/ai/full-flow/scripts -p test_formal_case_readiness.py -v
```

完整输入格式见脚本顶部中文说明。只有完整 40 个独立来源、同租户／提交／构建、黄金结果及应执行动作的独立回查齐备，才会 `officialFullReady=true`；此标志仍不是现实比赛提交回执、现场验收或自动签发的 SLA 达标。

## 前置条件

- 数据库目标必须是 `localhost` 或 `127.0.0.1` 的 TCP `3306`。
- 运行 profile 固定为 `dev`；脚本拒绝 `prod`、`decision-demo` 及其他 profile。
- 已构建 `ruoyi-admin/target/ruoyi-admin.jar`；预检会以 `[ok]` 明确确认该 jar 存在，不会构建或启动它。
- 本机 PATH 中可找到 `mysql`、`java`、`mvn`、`node`、`python`，以及 `mosquitto_pub` 或 `mqttx`。

## 只读预检

```powershell
powershell -ExecutionPolicy Bypass -File .\doc\ai\full-flow\scripts\preflight-local3306.ps1 `
  -Host 127.0.0.1 -Port 3306 -Profile dev
```

预检会确认现有 `ruoyi-admin/target/ruoyi-admin.jar`，并报告以下本机 TCP 端点：Redis `127.0.0.1:6379`、RabbitMQ `127.0.0.1:5672`、内嵌 MQTT `127.0.0.1:1883` 和后端 `127.0.0.1:8080`。已监听端点输出 `[ok]`，尚未启动的后端/MQTT 或其他不可达端点输出 `[warn]`，不会把它们误报为 MySQL 失败。MySQL 使用只读 `SHOW DATABASES`，这是必要检查；若 MySQL 连接或查询失败，预检才失败。

预检不构建、不启动、停止或写入任何服务，不传递或打印密码、Token、JDBC 连接串，也不执行写 SQL。MySQL 客户端可使用本机已有 defaults 配置或安全的交互式认证方式。

## 历史启动/停止脚本（当前用户自管服务批次禁止执行）

> 下方是原 Task 1 的隔离进程护栏说明，不是本批次运行建议。当前只可使用 `-ValidateOnly` 或其离线单测，不执行启动/停止命令。

```powershell
powershell -ExecutionPolicy Bypass -File .\doc\ai\full-flow\scripts\run-local-full-flow.ps1
powershell -ExecutionPolicy Bypass -File .\doc\ai\full-flow\scripts\run-local-full-flow.ps1 -DisableCaptcha
powershell -ExecutionPolicy Bypass -File .\doc\ai\full-flow\scripts\run-local-full-flow.ps1 -ValidateOnly -DisableCaptcha
powershell -ExecutionPolicy Bypass -File .\doc\ai\full-flow\scripts\run-local-full-flow.ps1 -Stop
```

启动脚本只使用现有 jar，以 `dev` profile 和 `server.port=8080` 启动；验证码开关仅作为进程参数追加，不修改 `application-dev.yml` 或 `application-decision-demo.yml`。`-ValidateOnly` 只输出 Java 参数构造结果，不创建进程；`-JarPath` / `-RunDirectory` 覆盖仅允许在验证模式，真实启动/停止固定使用仓库 jar 和被忽略的运行目录。PID、stdout/stderr 日志和启动清单写入 `/.codex/full-flow-20260928/`，该目录已被 Git 忽略。

启动时 `backend.pid` 和 `backend.json` 必须同时存在或同时不存在；只存在一个时拒绝修改。两者同时存在但记录的进程已不存在时，只清理这两个本脚本元数据文件并保留日志，然后才启动新进程。

启动时把创建进程及随后的创建时间查询、PID/JSON 写入置于同一个失败处理区间：任一步失败只尝试终止本次启动返回的进程对象；确认进程退出后移除不完整的 PID/JSON，清理失败不会覆盖原始启动错误；若无法确认进程退出则保留元数据供人工核查。

启动时记录 Win32_Process 的精确创建时间；停止时读取 `backend.pid` 和 `backend.json`，要求两处 PID 都是整数且完全相等、创建时间存在且与当前 PID 的创建时间完全相等；再确认 PID 对应的是 `java`/`javaw`，并且命令行包含唯一的独立 `-jar`、完整等于 metadata jar 的 jar token、唯一且完整的 `--spring.profiles.active=dev` 和 `--server.port=<metadata.port>` token（重复或冲突时拒绝）。元数据或命令行不匹配时拒绝 `Stop-Process`，并保留现有 PID/JSON 文件；只有匹配后才停止该 PID，并仅删除 `backend.pid` 和 `backend.json`，不删除日志或其他文件。

本任务不代表后续 ERP/MES/IoT/AI/Flow 业务链路已执行或验收。


## Task 2：本机只读数据盘点与确定性案例事实

`inventory_local3306.py` 仅允许 `dev` + `127.0.0.1`/`localhost` TCP 3306，目标仅限仓库现行 `ry-vue`、`ry-erp`、`ry-mes`、`ry-iot`。先从 `information_schema` 核对 schema、表、必要列；缺失则在报告记录缺失项并以非零状态退出，不猜测租户、编号或基础资料。只输出 ID、业务编码、状态、聚合计数及规则元数据，不输出账号密码、Token、联系人、个人联系方式或连接串。默认运行请使用**经授权的非 root、仅具 SELECT 权限的本机数据库账号**；管理账号仅在人工明确授权后通过 `--allow-admin-readonly` 显式启用。不要把密码写入命令参数或仓库文件；没有凭证时脚本会明确报错且不会尝试默认 root 登录。

```powershell
$env:MYSQL_USER = Read-Host '只读数据库用户名'
try {
  $env:MYSQL_PWD = Read-Host '本机3306只读数据库密码' -AsSecureString | ConvertFrom-SecureString -AsPlainText
  python doc/ai/full-flow/scripts/inventory_local3306.py --host 127.0.0.1 --port 3306 --output .codex/full-flow-20260928/inventory.json
} finally {
  Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
  Remove-Item Env:MYSQL_USER -ErrorAction SilentlyContinue
}
```

若确需使用经授权的本机管理员账号，只在上述命令末尾添加 `--allow-admin-readonly`，并在提示时输入该账号；仍仅允许 `dev` / localhost TCP 3306，执行 SELECT 盘点，不授权任何写操作。此模式接受管理员的非 SELECT grants，但**不验证最小权限**；报告标注 `admin read-only invocation, grants not least privilege verified`，始终设置 `usable_for_write_phase=false` 并以非零状态退出。不要把管理员盘点报告当作后续写入阶段的授权或验收，也不要将密码、原始 grants 保存到报告中。

报告仅保存至 Git 忽略的 `.codex/full-flow-20260928/inventory.json`，包含每个库表数量、目标表基线及当日新增行数、租户/账号/部门标识、编号规则、主数据候选、AI 模型/权限摘要。`created_on_db_date` 是快照内按数据库 `CURRENT_DATE()` 统计的当前计数，**没有历史快照时不能单凭它证明先前 Task 1 是否产生副作用**。缺失表/列或无凭证时不要把盘点写成成功；不要在生产或 decision-demo 环境执行。盘点不导入 SQL、不改数据，也不启动服务。

`flow_fixtures.py` 从历史 `doc/ai/benchmark/delivery-risk/cases.jsonl` 只读取事实/预期，不复用历史 `bizId`/`DEMO-` 编号，也不修改原始案例。生成的日期和业务名校验是后续步骤的输入约束，不意味着已创建真实业务数据。单测（无需数据库）：

```powershell
python -m unittest doc/ai/full-flow/scripts/test_flow_fixtures.py -v
```


### Task 2 审核补充：盘点可用性边界

- 活跃租户以 `sys_tenant.status='0' AND del_flag='0'` 为准；用户、部门、编码规则和基础资料候选必须归属该活跃租户，另按各表实际 `status` / `enable_flag` / `del_flag` 筛选。`bd_client` 没有可证明的启用状态，`wm_warehouse.status` 在初始化定义中未明确状态语义：这两类**只统计基线，不列为可用候选**，在报告的 `unverified_candidates` 中列出。报告因此标记 `usable_for_write_phase=false`，脚本以非零状态退出；后续写入阶段必须停下，等待人工核实状态语义与同快照盘点。
- `sys_auto_code_rule` 输出租户和 `rule_id`；`sys_auto_code_part` 输出租户、`rule_id`、`part_index`，只保留匹配同租户已启用父规则的部分；序列号规则包括 `rule_type`、`seq_length`、`check_digit_rule`。各候选含 `candidate_columns` 字段顺序，并按主键确定性排序（自动编码规则优先按租户/规则/部分序号排序，主键打破并列）；不使用可能截断活跃租户记录的固定 `LIMIT`。
- schema/列/引擎/主键信息先行核对；若缺失或非 InnoDB，报告不作可用性声明。正式业务基线、当日数量及候选由 **同一 MySQL 会话的单个 `REPEATABLE READ` / `WITH CONSISTENT SNAPSHOT, READ ONLY` 事务**读取，时间戳和日期来自数据库 `NOW(6)` / `CURRENT_DATE()`，不是本机 Python 时钟。元数据预检在事务之前，**不保证 DDL 的同快照一致性**；数据库迁移期间不要使用本报告。每次运行还在该认证会话内 `SHOW GRANTS`；默认仅明确的 `USAGE`/`SELECT` 授权被接受。显式管理员只读模式可接受非 SELECT grants，但不认定最小权限，始终拒绝写入阶段可用性；两种模式均不保存原始 grants。
- 输出仅为编码、ID、规则元数据及聚合数，不含用户名、姓名、联系方式、密钥或原始授权文本；异常格式或疑似联系方式/密钥的候选值会遮蔽并阻断后续写入。**业务编码仍可能属于内部敏感数据：报告始终留在 Git 忽略目录，切勿提交、分享或贴到工单。** `--no-defaults` 在部分 MySQL 客户端仍可能读取 `.mylogin.cnf`，不将其视为禁用登录路径的保证；默认必须显式提供经授权的非 root 只读账户与环境凭证；管理员账号仅按上述显式只读选项使用。
