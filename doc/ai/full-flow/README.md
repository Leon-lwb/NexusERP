# 本机 3306 全流程验收工具与运行边界

本目录包含本机 ERP/MES/IoT/AI 全流程验收的护栏、只读盘点、具备显式单步执行门禁的 Runner、断言和阶段报告。只读盘点不会创建业务数据或导入 SQL；业务单据状态由获得授权的业务 API、MQTT、RabbitMQ 或受保护 Flow 改变，不通过直接 UPDATE 库存、审批或 AI 结果来制造成功。

## 当前批次的最高优先级运行约定

- 用户自行在 IDEA 和 VS Code 运行后端、前端。本批次 Agent **不得**另起服务、停止或自动重启这些服务，也不自动启停用户 Docker 依赖；需要加载新的 Java 修复时由用户重编译、冷重启。
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
