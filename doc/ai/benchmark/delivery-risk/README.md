# 工业交付风险 40 案例 Benchmark（Task 18）

> **最新实测（2026-09-27 18:23:07 +08:00）**：模型凭据修复后已在另一全新隔离系统库完整重跑 40 条，**39 个 LLM 节点成功、1 个上游失败而跳过、0 次鉴权失败/验收超时**；黄金契约仍 **16/40 PASS**，30 条完成分析的摘要仍为模板降级。PASS 请求→终态 P50/P95 为 **32.417/41.088 秒**，详见 [凭据修复后完整重跑报告](reports/2026-09-27-isolated-40-real-model-rerun.md)。原报告及黄金期望不覆盖、不修改。

> 2026-09-26：这是**确定性服务级用例 + 隔离数据库演示种子 + 可重放的 HTTP Runner**，
> **不是**真实 MySQL/Redis/RabbitMQ/LLM 端到端实测，也不是比赛提交结果。
> **2026-09-27 更新**：已在隔离 MySQL/Redis/RabbitMQ/Flow/HTTP 链路实际运行 40 条，**16/40 PASS**；39 个执行到的 LLM 节点鉴权失败，因此 [有日期的实测报告](reports/2026-09-27-isolated-40-baseline.md) 仅是模板降级基线，不能算正常 LLM 性能或正式竞赛回执。后文 9 月 26 日的预检与未实测描述为历史检查点。

## 边界与数据

- `cases.jsonl`：`DELIVERY-001..040`，唯一 `caseId`、`bizCode`、**十进制字符串** `bizId`（超过 JS 安全整数）。`expected-results.jsonl` 是单独的黄金期望。
- 五类正常/缺料/瓶颈/紧急/组合风险及六个“证据缺失、必须失败关闭”的案例。`026..028` 缺路由，`038` 缺已发布基线，`039` 缺 MRP，`040` 缺产能日历；这些案例期望 `FAILED`，只有错误原因与 `expectedFailurePattern` 匹配且没有动作才可计为**预期拒绝**。它们没有已发布的 LOW 风险结论。
- `CONTROL-001..005` 由 `DeliveryDecisionBenchmarkTest` 覆盖：过期审批、禁用动作、模型回退、重复审批、部分执行失败。Runner **不自动审批/执行**，不把这 5 项算入 40 条 HTTP 案例通过率。
- `seed/` 是从案例 JSONL 生成的 **ERP 业务库** SQL，按精确 schema `nexus_ai_decision_demo_erp`、租户 `000000` 和 `DEMO-AI-DECISION-*` 限定写入及重置。业务主 SQL `script/sql/ry-erp.sql` 仍是表定义来源；**不要**向现有 plus 或任何生产库导入主 SQL、seed 或 reset。
- 每条记录的工单 ID 固定，日期由 `CURDATE()` 相对生成（+7 日需求，+14 日承诺，+18 日逾期采购）。重新执行之前先按 demo 前缀重置并导入；若更改了已插入的案例，仅依赖 `NOT EXISTS` 不会更新旧记录。

## 离线复核（不连接数据库）

```powershell
python doc/ai/benchmark/delivery-risk/scripts/build-fixtures.py
python doc/ai/benchmark/delivery-risk/scripts/build-seed.py
python doc/ai/benchmark/delivery-risk/scripts/test-fixtures.py
python doc/ai/benchmark/delivery-risk/scripts/test-isolated-init.py
python doc/ai/benchmark/delivery-risk/scripts/test-run-benchmark.py
mvn -pl ruoyi-modules/ruoyi-ai -am test '-DskipTests=false' '-Dtest=DeliveryDecisionBenchmarkTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

生成器由 `build-seed.py` 对照主 SQL 检查列名；测试检查每次写入的 schema/租户/业务前缀、40 个稳定业务 ID、多缺料与低置信证据缺口。HTTP Runner 测试启动**本机临时模拟 HTTP 服务**，不是 ERP 真环境。

## 已保存排程的可信产能口径（仍需真实 Flow 验真）

Task 19 已补来源计算：ERP 在租户/工单范围内读取已保存排程任务关联资源的 `prd_capacity_calendar.planned_hours / effective_hours`，要求从任务开始到结束**每天均有可信日历**，并比对任务工时与计划工时；日历缺失、产能为零或矛盾时返回 `null/NO_BASELINE_UTILIZATION`，风险评分继续失败关闭。`DecisionAnalysisAssemblyService` 会独立复算历史 KPI；源事实带任务/日历 ID 与资源日期绑定到快照，完成分析时重新读取并验证。**绝不**用 APS 候选利用率冒充历史值。

已生成 seed 补齐每例任务日期范围内的产能日历。**2026-09-26 仅在本机全新演示 schema 的 MySQL 5.7.14 做了 SQL 导入与 reset→seed 重放**：系统决策表 3 张、菜单 1850–1859 共 10 项；ERP 有 40 个唯一演示工单、39 个 MRP、39 个已存排程与 1053 条产能日历，所有演示工单租户为 `000000`。该段仅是最初 SQL 导入的历史检查点；此后已完成独立实例与后端临时启动烟测（见下文），仍未运行真实 Flow/40 案例 HTTP Benchmark，也不保证黄金场景排名吻合；真实通过率/时延仍为**未知**。
## 演示库安全初始化与边界

`init-isolated-demo.py` 默认只做静态预检；显式 `--apply` 才允许在本机 127.0.0.1:3306 **新建** `nexus_ai_decision_demo_sys` / `nexus_ai_decision_demo_erp`，并把主 SQL 分别导入指定的全新库后导入带数据库名约束的 ERP seed。目标任一已存在即拒绝执行；导入失败保留现场、**绝不**自动 DROP。脚本拒绝 SQL 切库、跨库限定表名、存储过程、动态 SQL 与全局授权。`ry-mes.sql` 含存储过程，故不在该初始化器允许范围；不要向 plus 库导入主 SQL。

```powershell
python doc/ai/benchmark/delivery-risk/scripts/init-isolated-demo.py  # 旧 SQL 静态预检；此机禁止对 3306 --apply
```

2026-09-26 本机隔离 SQL 导入初次因需求明细 `status` 列为 BIGINT、seed 曾给字符串而失败；生成器已增加整数列类型预检，修正后仅在演示 ERP 库重放业务 seed 并完成 reset→seed 验证。这两张演示 schema 已存在于旧 3306 实例，**再次运行旧初始化器的 `--apply` 会拒绝**。这是历史 SQL 检查点；当前隔离运行环境见下节，绝不能用本机 root 凭证启动指向 plus 的数据源。

## 隔离演示运行环境（不影响普通 dev）

**安全背景：**本机原有 127.0.0.1:3306 MySQL 5.7.14 以 `--skip-grant-tables` 运行，无法可靠限制访问范围。历史记录中的两个系统/ERP 演示库与本轮添加的两个**空** MES/IoT 演示库仍留在该实例，仅为 SQL 级验证；**不要**在此实例上使用 root 或普通开发账号启动决策演示，也不要再次执行旧 `init-isolated-demo.py --apply`。四个库的完整运行版由独立的本机 MySQL 5.7 进程在 127.0.0.1:13307 **从源 SQL 独立初始化**，数据目录为 `D:\workspace\nexus-ai-decision-demo-mysql\data`，不复制 plus 库，也不会停止/重配已有 3306、3307 实例。

从仓库根目录执行（`--apply` 仅创建缺失的演示资源；不会清空已有的 demo 库或启动后端）：

```powershell
python doc/ai/benchmark/delivery-risk/scripts/test-prepare-decision-demo.py
python doc/ai/benchmark/delivery-risk/scripts/prepare-decision-demo.py
python doc/ai/benchmark/delivery-risk/scripts/prepare-decision-demo.py --apply
python doc/ai/benchmark/delivery-risk/scripts/prepare-decision-demo.py --verify
```

准备器使用独立数据目录和隐藏后台进程启动本机 MySQL **13307**，并使用两个独立 Docker 容器启动 Redis **6385**、RabbitMQ **5685**（管理端口 **15685**）；所有端口仅绑定回环地址。准备器可重复执行，再次运行只检查已创建的库及账号，绝不删除或重置已有内容。只允许操作 `nexus_ai_decision_demo_{sys,erp,mes,iot}`：新系统/ERP 库从仓库主脚本及 40 条演示 seed 初始化，MES/IoT 只导入 76/16 张表定义，**不导入历史数据或 root DEFINER 存储过程**。账户 `decision_demo` 只获四个演示库的 SELECT/INSERT/UPDATE/DELETE，启动前检查 plus schema 不存在且演示账号不能读取 plus。

私密连接参数（包含仅供准备器使用的 MySQL root 密码）自动随机生成并保存在 `doc/ai/benchmark/delivery-risk/.env`，已被仓库 `.gitignore` 忽略；切勿提交/截图/公开此文件，勿在日志或命令行传递密码。应用启动脚本仅向 Java 传递受限演示账号，不传 MySQL root 密码。`ruoyi-admin/src/main/resources/application-dev.yml` 当前**单独启用 `dev` 即默认使用隔离 MySQL 13307 / Redis 6385 / RabbitMQ 5685**，原日常连接已注释保留；应用账号密码仅在 Git 忽略的 `ruoyi-admin/src/main/resources/application-secret.yml` 中追加，不包含 MySQL root 密码。旧的 `dev,decision-demo` 启动脚本仍可用，会从本地 `.env` 覆盖连接；两种模式都关闭 Flowable 自动改表、SnailJob 客户端、监控上报、内嵌 MQTT 和 IoT 流消费。`snail-job.enabled=false` 仍会触发本地调度兜底，但只连接隔离资源。

可直接在 IDEA 以 `dev` profile 运行 `com.nexus.NexusApplication`（模块 `ruoyi-admin`，需重新加载 Maven 资源/重启已有进程）；无需配置 IDEA 环境变量文件。执行前建议运行 `python doc/ai/benchmark/delivery-risk/scripts/prepare-decision-demo.py --verify`，确认隔离资源仍在。

若选择启动脚本，则从仓库根目录执行以下命令；脚本先确认容器健康、四个库、最小权限与端口占用，且不会把 MySQL root 凭证传给 Java：

```powershell
mvn -pl ruoyi-admin -am package -DskipTests
# 如果普通 dev 后端还在监听 8080，请在自己的终端或 IDE 手动停止它；脚本不会终止任何进程。
pwsh -NoProfile -File doc/ai/benchmark/delivery-risk/scripts/run-decision-demo.ps1
```

要先检查而不启动，可加 `-PreflightOnly`；如指定 `-Port 8081`，PC/App 代理也必须手动改指 8081 才能验证同一实例。修改配置不会重配已经运行的服务，须在 IDEA 或使用此脚本重新启动后端，并检查日志里的活动 profile。`--verify` 和 `-PreflightOnly` 都不对数据库做写操作；禁止用普通 dev 的 3306 数据执行 Benchmark。

## 模型凭据前置核验（2026-09-27）

新建系统库从主 SQL 恢复的默认模型与历史 Key，**不一定与当前日常演示库已经验证的运行配置一致**。本次已定位并修复这一漂移：只在隔离库之间复制密文及对应模型，来源/目标应用连接测试均成功，`DELIVERY-001` 的真实 LLM 节点成功；其摘要仍模板降级，应另行调查 Guard。详情见 [模型凭据修复核验](reports/2026-09-27-model-credential-verification.md)。**没有重跑完整 40 案例，原 16/40 基线不得改写。**后续重新建立基线库时，必须先核对唯一默认 TEXT 模型、厂商、协议并在目标应用真实测试；不要将 API Key 写进 SQL/脚本/报告。

**同日 18:23 完整重跑已完成：** 上文单案例凭据报告保留为修复检查点，新的 40 条结果见 [完整重跑报告](reports/2026-09-27-isolated-40-real-model-rerun.md)。凭据问题已在 39 次真实 LLM 调用中验证消除；剩余方案黄金差异、节点失败、摘要 Guard 与 CONTROL 人工验收仍未完成。

## 隔离环境真实测量（Task 19 待办）

1. 在专用 13307 实例执行上方 `--apply` 并通过 `--verify` 后，使用临时演示账号与专用 Redis/RabbitMQ 验证系统/ERP/Flow；验证帐号对租户 `000000` 有 `ai:decision:run`、`ai:decision:query` 及工单读权限。不要复用本机 plus 库，绝不在 plus 上执行主脚本（包含 DROP TABLE）。
2. 首次初始化由 `--apply` 负责导入；此后需重置时，先人工核对当前 MySQL 端口**确为 13307**且数据库**确为** `nexus_ai_decision_demo_erp`，再以受限演示账号在该隔离 ERP 库依次导入 `seed/reset-demo-data.sql`、`seed/demo-master-data.sql`、`seed/demo-business-data.sql`；SQL 有额外 `DATABASE()` 限定，错误 schema 下插入/删除为零，但它不是替代人工核验和最小权限的安全措施。
3. 使用单独的临时 Token：

```powershell
$env:AI_DECISION_BENCHMARK_TOKEN = '<从隔离环境临时获取，不提交到仓库>'
try {
  pwsh -NoProfile -File doc/ai/benchmark/delivery-risk/scripts/run-benchmark.ps1 `
    -BaseUrl 'http://localhost:8080' `
    -ClientId '<本次登录使用的 PC clientid（见 plus-ui/.env.development）>' `
    -Output 'doc/ai/benchmark/delivery-risk/reports/latest.md'
} finally {
  Remove-Item Env:AI_DECISION_BENCHMARK_TOKEN -ErrorAction SilentlyContinue
}
```

也可省略环境变量，在交互终端通过 `Read-Host -AsSecureString` 隐式输入；`-Token` 参数只接受 `SecureString`。**报告不要提交带环境身份/业务敏感信息的运行日志。**Runner 必须携带与临时 Token 登录时相同的 `clientid` 请求头；缺少或不匹配会被服务端拒绝。Runner 不连接 MySQL、不重置数据；每例先以只读 GET `/erp/prd/work/{bizId}` 独立核对工单 ID/编号，再调用 POST `/ai/decision/delivery-risk/run`、GET `/ai/decision/run/{runId}`。运行记录的 `bizCode` 可空，但非空时必须与已核对工单编号一致。每例先从 ERP 只读接口验真类型/编号/字符串 ID，再要求运行记录返回相同的类型与 ID（非空编号也须匹配），并在最多 90 秒后判定；`AI_DECISION_READY` 是 SSE 事件而非运行状态。未执行、超时、连接失败、找不到业务 ID 均记 FAIL。P50/P95 仅统计通过案例的请求至完成时长；不同于服务端纯推理耗时。
4. 人工另做 CONTROL 审批、幂等、执行故障注入和多租户/越权验证；复查报告中的预期拒绝原因。只有竞争演示隔离环境真实重放后，才在 [`reports/2026-10-competition-baseline.md`](reports/2026-10-competition-baseline.md) 写实际数值、环境信息、运行时间、提交 SHA 与证据。2026-10 的正式竞赛报告当前仍是**未实测模板**，本机 2026-09-27 降级基线单列于上文。

## 视频链接位置

视频、公开可访问链接及授权在 Task 19 提交核验清单中登记；目前**尚未制作/核验**，不得把 `videoCase=true` 误当视频已交付。拟录制案例：`DELIVERY-006`、`020`、`033`。

## 黄金 v2 与安全拒绝码（2026-09-27 用户批准）

用户已明确采用十八例的独立审查建议。当前 `cases.jsonl` / `expected-results.jsonl` 是 v2；批准范围、前后哈希与六码矩阵登记在 `golden/2026-09-27-v2-approval.json`。v1 原始测试字节保存在 `golden/versions/v1/`，此前所有 16/40 报告与 inactive 审查 proposal 保持原样，不改写历史通过率。

- 推荐/动作仅修订十八例：006、016–019 推荐 CR_EXPEDITE；007–015 推荐 null、012–015 不再要求不可行排程草稿；029–032 按已确定的 lexical tie-break 推荐 CR。
- 输入 facts、业务字符串 ID/编号、风险/置信度/根因、原 `expectedFailurePattern` 和 ERP seed 不变。六个负例新增精确 `expectedFailureCode`：026–028 `ROUTING_EVIDENCE_MISSING`，038 `SCHEDULE_EVIDENCE_MISSING`，039 `SHORTAGE_SIMULATION_INCOMPLETE`，040 `CAPACITY_EVIDENCE_MISSING`。
- Runner 必须同时满足 FAILED、精确白名单错误码、原消息模式、零风险/推荐/resultJson 结论、零动作；`DECISION_NODE_FAILED` 或任意含 routing 文本的未知异常不能算 PASS。
- 领域异常只在服务端明确证据缺失处分派有限码/固定消息。权限、SQL、模型、数值篡改及未知异常仍通用失败；不按错误文本或 cause 猜类型。SSE 失败不阻断拒绝记录。MRP 拒绝仍经过 ERP Port 的权限和新鲜度校验。
- 活跃 v2 使用跨平台固定 LF，生成器显式写 LF；v1 archive 用 `-text` 保留真实测试的 CRLF 字节。离线测试校验 Git 过滤前后历史字节哈希一致，避免 Windows/Linux checkout 改写记录。
- `build-fixtures.py` 可再生成相同 v2；服务级 benchmark 不再用期望推荐给候选人工制造最低 KPI。服务级模拟仍不替代真实 ERP HTTP 回归。

本轮最终真实回归单列于 `reports/2026-09-27-golden-v2-40-regression.md`；CONTROL 人工 HTTP 审批、幂等、执行、越权/多租户验收仍需后续独立进行，不能由模型分析通过代替。原 2026-10 正式竞赛文件仍是未来提交模板。


## plus 审查分支的环境及整理说明

- 单独 `dev` 恢复 plus 日常环境（MySQL 3306、Redis 6379、RabbitMQ 5672），验证码开启。
- 演示覆盖独立放在 `ruoyi-admin/src/main/resources/application-decision-demo.yml`；显式激活 `dev,decision-demo` 才生效。使用 `scripts/run-decision-demo.ps1 -Port 8081` 可从忽略的 `.env` 加载受限演示账号，不向应用传入 MySQL root 密码。不停止已有后端。
- IDEA 如需继续测试演示库，激活同样的两个环境配置，并通过本机环境变量配置 `DECISION_DEMO_*`（不包含准备器专用 root 密码）。不能只激活 `dev` 就运行演示数据。
- 演示环境缺少显式连接变量时必须失败，不能回退日常库。验证码关闭仅限该显式测试环境，不进入普通 dev。
- 数据库结构离线检查合并为 `scripts/test-database-schema.py`，保留节点长度与采购字典、字段容量全部三项测试。两个旧重复入口删除。
- 历史基线和控制验收报告保留；本次整合验收另见 `doc/ai/merge/2026-09-27-plus审查分支说明.md`，不以历史四十例通过率替代新分支真实验收。

启动前还会验证最终生效的隔离资源；逆序 `decision-demo,dev`、缺失变量、混用日常连接及 prod 组合均明确拒绝，不能回退到日常库。
