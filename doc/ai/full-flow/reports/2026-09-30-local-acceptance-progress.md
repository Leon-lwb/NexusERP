# 2026-09-30 本地闭环续测阶段记录

> 开发分支 `codex/full-flow-acceptance-20260928`，HEAD `120727a8`。尚未提交、推送或合并 `plus`。上一阶段证据见 `2026-09-29-local-acceptance-progress.md`。本文件不是生产部署或真实制造工时证明。

## 09:20 用户 IDEA 后端启动排障

- 09:16:44 用户运行后端时 `ruoyi-admin/target/classes/application.yml` 的 `spring.profiles.active` 为**空**，日志显示未激活 dev 且 dynamic-datasource 只加载了 **0** 个数据源，Flowable 初始化报 `CannotFindDataSourceException`。该问题是 Maven 用 `-Dprofiles.active=` 跑测试/编译时把**过滤后的输出资源**覆盖为空，不是源配置缺失；保留用户已修改的 `ruoyi-admin/src/main/resources/application.yml` 和 `application-dev.yml` 原样。
- 离线受限内存执行 `mvn -o -pl ruoyi-admin -am compile -DskipTests=true -Dprofiles.active=` 得到 reactor BUILD SUCCESS（48 模块）；运行资源 `active` 的 RED 检查确认为 `<empty>`。随后只执行 `mvn -o -pl ruoyi-admin -P dev resources:resources -DskipTests=true`，退出0；复核运行资源为 `active: dev`、源码仍为 `@profiles.active@`、运行资源含 `application-dev.yml`。未启动/关闭用户服务。
- 用户在 IDEA 09:20:40 自行重启后，日志确认 `dev` 已激活、`erp/mes/master/iot` 4 个数据源成功初始化、`NexusApplication` 09:22:08 完成启动，8080 监听 PID 23532。独立无敏信息 GET `http://127.0.0.1:8080/auth/code` 为 200 且 `captchaEnabled=false`；依照用户原来的本地账号以加密登录请求验证 `/system/user/getInfo` 为业务 200 且租户 `000000`，没有记录/输出 Token 与密码。用户前端 `http://10.151.240.161:80/` 及经 `/dev-api/auth/code` 代理均 HTTP 200；`localhost:80` 不通，前端必须使用用户提供的 VPN IP。再做 `ruoyi-admin` Maven 空 profile 测试后须重建 `dev` 过滤资源，避免重演；不能把后端已启动等同于新 Task3 代码已经由 JVM 加载。

## 源与测试复核

- 09:16:21 对 `127.0.0.1:3306/ry-erp` 独立 `READ ONLY` 事务回查：两条既有方案 `APS202609290001`、`APS202609300001` 均为 `COMPLETED` 且旧口径版本 **0**；逐日来源及预约表均 **0 行**，7 个日历计划/实际累计均为 **0**，事务回滚。结构增量 2026-09-29 **已经执行**，不重放、不改旧数据。只读结果保存在 `.codex/full-flow-20260928/aps-db-readback-20260930.json`。
- `mvn -o -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false -Dprofiles.active= -Dtest=DeliveryContextAssemblerTest -Dsurefire.failIfNoSpecifiedTests=false`：当日 **52/52**，0失败0跳过；这是单元测试，不是数据库事务验收或用户 IDEA 已加载代码的证明。Python `python -m unittest discover -s doc/ai/full-flow/scripts -p test_*.py` 当日 **317/317** 退出0，纯离线合同测试，不代表采购收货、MES 加工和 40 个独立真实案例。
- 09:38:46 基于新登录会话的四个**GET** 列表路由均 HTTP/业务 200：ERP 排程 2 条、MES 工单 1 条、IoT 设备总数 25、AI 决策运行 3 条；仅验证现存数据可读，不代表写流程、班次凭据、审批或页面交互通过。摘要证据 `.codex/full-flow-20260928/api-list-readback-20260930.json` 不含 Token/密码。
- Task4 新增“多工单销售范围不能选只含一个工单的较新方案当已保存基线”回归 RED 后改为只选择覆盖当前范围的发布版方案，`DeliveryContextAssemblerTest` 最新一次 **53/53**，0失败0跳过，见 `.codex/full-flow-20260928/aps-full-scope-green2-20260930.log`。独立审查另指出同一计划完全缺少任务来源行时可能漏检，已加入断言尚待独占 RED→GREEN 和复审。**运行中 JVM 尚未加载这些最新源改动。**
- 10:23 以一个临时 Edge/Playwright 浏览器、浏览器内存存储和所有非登录业务请求只读的 guard 检查真实 `http://10.151.240.161:80/erp/prdExec/schedule`；首次脚本把显式 `:80` 当唯一 Host，拦截正常浏览器规范化后的无端口登录，已修复本地测试脚本；第二次在甘特 GET 返回前过早断言，增加有界等待后**同一脚本第三轮退出0**。两条旧口径 `APS202609290001`/`APS202609300001` 都显示历史标签，行内发布缺失、工具栏发布禁用，抽屉标题/历史提醒绑定该条实际甘特记录，通用 AI 文字解读已禁用；两次甘特 GET 200，浏览器报错0、业务变更请求0、浏览器已关闭。证据 `.codex/full-flow-20260928/ui-aps-schedule-20260930.json` 仅存布尔结果，不含 Token/HTML/密码；这不证明新版v2真实发布及所有页面已验收。
- 本轮 `npm run test:unit` 新版前端修改后 107/107、0失败；`npm run type-check` 先在2GB、3GB Node堆**均因单进程堆内存不足退出134**，而非 TS 错误/整机OOM；确认可用物理内存>7GB、无并行 Maven 后用 Node 4GB 上限串行重跑退出0；该次检查完成于 09:58，**早于 10:06 的甘特/tooltip 修正，尚需针对最终 UI 重跑**。未据此宣称最终构建/生产通过。`DeliveryContextAssemblerTest` 新添范围内全部工单覆盖、全方案任务来源及事务注解RED→GREEN，最新 **54/54** 退出0；真实MySQL事务与用户当前JVM加载仍需独立验证。
- 10:30 串行 `mvn -o -pl ruoyi-modules/ruoyi-flow -am test` 配合指定工业决策类（Maven 768MB、Surefire 512MB）退出0：**公共AI 35、业务AI 28、Flow 44**，各 0失败/跳过。注意该 reactor **不包括 ERP 模块**，不可表述为 ERP 全量回归；Task3 Worker 最近一次独立定向称73/73、Adapter 最后54/54 的工作区验证均还需完成组合 reactor 和真实数据库。证据 `.codex/full-flow-20260928/aps-ai-flow-cross-module-20260930.log`。
- 10:23–10:24 实际浏览器页面脚本首次因 guard 错把默认端口登录拦截、再次因抽屉异步 GET 尚未完成过早断言，两者仅影响测试脚本；更正后第三次两旧方案检查全部 true。其余 ERP/MES/IoT/AI 页面仅有 authenticated GET 列表健康检查，**还未全部浏览器巡检**。最新 UI 在 10:06 修正后，`npm run test:unit` 重跑 **107/107**；旧 09:58 的类型检查不覆盖这些 UI 改动，待安全内存窗口重跑。
- APS 发布并发/假期/跨夜源产能仍在 TDD/规格复审中；真实服务端编号的新排程、发布占用/凭据同事务、AI 归属审批/执行、MES/IoT 全链路及前端其他页面再次巡检尚未验收。不修改历史 FAILED run；完成全部代码与测试、真实链路再考虑合并 `plus`。

## 10:45 APS 组合回归、前端安全构建与新发现（仍待运行态验收）

- 已复核开发分支与工作区原有未提交修改，未提交/推送/合并。用户后端仍为 09:20 启动的 PID 23532，`/auth/code` HTTP/业务 200、验证码关闭，前端 `http://10.151.240.161:80/dev-api/auth/code` HTTP/业务 200；运行中的 JVM 启动早于 10:26 新编译的 `PrdScheduleServiceImpl.class` 和 `DeliveryContextAssembler.class`，不可用于真实新版排程发布。
- 串行运行包含公共 AI、ERP、业务 AI、Flow 的 **同一次 Maven reactor**：`mvn -o -pl ruoyi-common/ruoyi-common-ai,ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-ai,ruoyi-modules/ruoyi-flow -am test -DskipTests=false -Dprofiles.active= -Dtest=... -Dsurefire.failIfNoSpecifiedTests=false` 退出 0；4 个被选模块的用例分别为 **59、185、28、44**，均 0 失败/错误/跳过。日志 `.codex/full-flow-20260928/aps-all-four-reactor-20260930.log`；此 reactor 不含 `ruoyi-admin`，没有覆盖运行时 dev 过滤资源。
- 前端 APS 多选/陈旧选择修改后的 `npm run test:unit` **118/118** 退出 0；4GB Node 堆 `vue-tsc --noEmit` 退出 0。带内存监测、无并行 Maven 的 production Vite 构建退出 0，观测最低剩余 RAM **3349MB**、虚拟内存 **5753MB**，未触发保护阈值。证据 `.codex/full-flow-20260928/frontend-unit-after-ui-20260930.log`、`frontend-typecheck-after-ui-20260930.log`、`frontend-build-after-ui-20260930.json`。**前端复审随后提出 P2；这些结果早于该修正，需重跑。**
- 纯离线制造合同 `python -B -m unittest discover -s doc/ai/full-flow/scripts -p 'test_*.py' -q` **317/317** 退出 0；不等于完成真实采购/MES/IoT/AI 动作。证据 `.codex/full-flow-20260928/python-contracts-all-20260930.log`。
- 前端独立复审指出行内甘特按钮使工具栏静默选择、列表 GET 失败时可见勾选与按钮状态不同步、历史/未发布方案仍可发起必然无可信基线的风险分析。限界前端 worker 正按 TDD 修复，同时审计删除确认期间选择变化；未宣称通过真实浏览器。
- APS 后端只读复审指出终态工单仍可能发布并占用、无有效班次日历可创建、零工时工艺的错误语义，以及 `count+1` 方案编号并发/软删除复用。终态及班次/零工时 worker 正按回归验证；编号仍待实现。独立 localhost:3306 `READ ONLY` 事务确认目前仅 2 条排程、均符合 `APS+8位日期+4位序号`，无重复租户编号/空租户，新序列表尚不存在；9 月 29/30 两个日桶的最大序号各为 1。**未执行额外 DDL/业务写入。**
- 后端真实 MySQL 5.7 的 API 发布事务、并发回滚、逐日/逐班次凭据及可信 AI 正向分析尚未执行；旧方案保持原状，禁止把代码单测当成数据库验收。后端审查、编号增量和冷重启后方可开展下一次真实写流程。

## 11:10 APS 编号一致性修复与本轮总回归（待维护窗口）

- 进一步只读复审发现原 `count+1` 业务编号在同日并发和草案软删除后可能重复。已将新排程编号改为 ERP 同事务、按租户+**排程起始日**预留 `APSyyyyMMddNNNN`，同一事务完成编号、方案、任务及逐日来源；旧方案编号和状态不改。新装 SQL 定义及一次性存量脚本 `doc/ai/full-flow/scripts/2026-09-30-aps-plan-number-sequence.sql` 增加租户/日期序列表、包含软删除历史的最大序号种子、租户/编号唯一索引并收紧方案租户列 NOT NULL。两条历史方案的编号分别仍为 `APS202609290001`、`APS202609300001`。二次只读审查仍在进行，**未执行编号增量**；旧后端 PID23532 仍运行，按维护门槛拒绝 DDL，用户暂停旧后端写入并冷重启前不可进行新版 API 写入。
- 番号 TDD：新分配器 3 项 RED 后 **5/5**，排程服务接入验证 36 项中 1 项 RED 后 **36/36**；原有溯源 DDL 合同 4/4；新本地 SQL 合同 **2/2**。编号迁移只读预检 `.codex/full-flow-20260928/aps-no-sequence-preflight.json` 显示 MySQL 5.7.20 / 3306、旧方案 2 条、编号按日期各1、序列表/新索引未存在；该文件只记录 schema/业务摘要与行投影摘要，不含账号密码。需维持用户备份和在暂停 APS 写窗口单次执行，失败时不得盲目重跑。
- 所有 6 个主业务模块同一次完整 Maven reactor `mvn -o -pl ruoyi-common/ruoyi-common-ai,ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-iot,ruoyi-modules/ruoyi-ai,ruoyi-modules/ruoyi-flow -am test -DskipTests=false -Dprofiles.active=` **41 模块 BUILD SUCCESS**；公共 AI106、ERP891、MES635（**跳过2项、实际633通过**）、IoT224、AI335、Flow67，均0失败/错误。证据 `.codex/full-flow-20260928/full-business-reactor-20260930.log`。此 reactor 不含 admin，保留 `ruoyi-admin/target/classes/application.yml` 的 `dev` 过滤资源。
- 前端最后一轮 `npm run test:unit` **128/128**，`vue-tsc --noEmit` 退出0；production Vite 构建在4GB Node堆、无并行 Maven和RAM阈值守护下退出0，最低剩余 RAM5083MB。只读真实 Edge/Playwright 浏览器再次核验两条旧版方案：历史标签和发布禁用、多选禁甘特与发布、勾选方案B后点击A目标仍B、筛选重置清空旧选择、行内甘特不误选、旧版风险分析禁用；JS错误0、业务写请求0，已关闭浏览器。证据 `.codex/full-flow-20260928/frontend-unit-final-ui-20260930.log`、`frontend-typecheck-final-ui-20260930.log`、`frontend-build-final-ui-20260930.json`、`ui-aps-schedule-20260930.json`。该页面仍未验证新版 v2 发布与凭据。
- ERP/MES/IoT/AI 的真实全链、40 个独立正式案例、现场工时与审批/执行仍待用户冷重启及逐笔事实验证；当前所有测试不能冒充生产/人工验收。开发分支仍未提交或合并 `plus`。

## 11:31 用户暂停后端维护窗口：编号增量已在本地 3306 执行一次

- 用户明确回报 IDEA 后端已停止；执行前实测 8080 无监听、MySQL 3306 在本机监听、`ry-erp` **其他连接为0**，并维持不启动其他 APS 写方。新鲜只读预检：MySQL 5.7.20，旧方案 2 条（均历史版本0），旧编号与起始日和租户匹配、无重复、当前不存在序列表/新索引。现行主表单表新鲜备份放在仓库外临时目录，仅将摘要/哈希记录于 `.codex/full-flow-20260928/aps-no-sequence-backup.json`；历史备份未重放。
- 使用带 `python -O` 下仍有效的非 assert 闸门和最新备份 SHA 检查，**单次**执行 `doc/ai/full-flow/scripts/2026-09-30-aps-plan-number-sequence.sql` 三步（建表→含软删除历史最大尾号播种→收紧租户并建唯一键），三步均记录到 `.codex/full-flow-20260928/aps-no-sequence-applied.json`。增量文件**字节** SHA-256 `F095AD3ABB91A57BF24768464019421FA25BEF6E0BE7AE2C0319F2F131E61C28`；旧 09-29 产能增量未重放、主初始化脚本未导入。后端保持停止。
- 独立新 `READ ONLY` 连接验证 `prd_schedule_plan.tenant_id` NOT NULL、租户+方案编号唯一索引、2026-09-29 与 2026-09-30 序列表最大值各为 1，历史 2 条 plan 的身份/状态/版本不变；14 任务、7 日历的 planned/actual=0，day_load/booking=0。仅在显式回滚的独立事务中对已有日桶原子加1，观测 1→2→回滚1；同租户重复编号在 MySQL 5.7 被 1062 唯一键拒绝且回滚，旧方案数仍2。证据 `.codex/full-flow-20260928/aps-no-sequence-{readback,rollback-probe}.json`。这些**不**证明 Spring/MyBatis 运行态已加载新分配器。
- 后端源码 Locale 非ASCII尾号回归先 RED（阿拉伯数字环境出现非ASCII序号）再用 `Locale.ROOT` 强制 ASCII；`PrdScheduleNumberAllocatorTest` 最新 **6/6**，服务 `PrdScheduleServiceImplTest` **36/36**，老 SQL 合同 **4/4**，绿色 reactor 退出0；SQL 本地合同2/2。完整六模块 41 模块回归在该一行 Locale 修订前执行并通过，需在最终关闭全部改动后按需重跑。默认序列保留允许幂等并发冲突时产生缺号；编号格式/租户唯一得到保护，但不承诺无间断连续流水或尚未发生的 API 写入。
- 下一步仅在用户**IDE 重编译并冷重启**当前分支后，核对新 PID、`dev` 配置与实际新编译产物，再重新登录，并通过真实 ERP API 生成 v2 新方案→受控发布→只读读回逐日、班次占用和日历 planned/actual→受保护 AI/Flow；旧方案仍保留版本0不可发布。全链、40例及合并未完成。

## 11:48 代码复审 P2 收口（仍等待用户 IDEA 重新编译和启动）

- 独立只读审查提出：手工 `/erp/prd/schedule/run` 可夹带 AI 来源字段；无日历时页面给出可发布错觉；零工时内部工序错误会落入未知异常；全无可排工序仍生成空 v2 草稿。后端先新增3个定向 RED 回归（39项中3项失败），再修正：普通排程拒绝所有 caller-supplied Decision 来源字段；将排程引擎可预期 `IllegalArgumentException` 包装为 `ServiceException` 供用户看到业务原因；无任务时在编号预留/写主表之前失败关闭。最新定向 ERP 60/60 通过，旧 AI 内部显式 scope/幂等分支保持原逻辑。前端文案标明“缺日历时8h仅为生成阶段估算；发布需真实 ACTIVE 班次日历并受事务核验；可信 AI 需已发布”，新增文案测试且总前端 `npm run test:unit` **130/130**。
- 本轮六模块 Maven 完整 reactor 在执行中；不能将 11:10 上一次完整回归当成最新源码的完成证据。用户后端持续停止中，不能调用新服务 API，也**不再重跑已应用的编号增量**。文案修改后尚需最新类型检查/构建/只读页面复核，后端启动后核对运行态并重登录后才允许新方案写入。

## 11:53 P2 改动后回归确认，等待用户在 IDEA 编译并冷重启

- 上述三个后端入口用例从 RED(39 项中 3 失败) → GREEN：`PrdScheduleServiceImplTest`39、`PrdScheduleNumberAllocatorTest`6、`PrdScheduleEngineTest`15，均0失败。最新**同一次**六模块 Maven 41 项 reactor 退出0：公共AI106、ERP895、MES635(跳过2项、实际633通过)、IoT224、AI335、Flow67，均0失败/错误。证据 `.codex/full-flow-20260928/full-business-reactor-final-20260930.log`。
- 最后前端变更后 `npm run test:unit` **130/130**，4GB `vue-tsc --noEmit` 退出0，带内存护栏的 production Vite build 退出0（最低剩余RAM 5791MB），证据 `.codex/full-flow-20260928/frontend-{unit,typecheck,build}-after-review-20260930.{log,json}`；不是后端新运行态验收。`git diff --check` 未报新增空白错误，Git 的 LF→CRLF 警告不属于用例失败。旧版真实浏览器的最后一次 GET 巡检早于这次一处提示文案变动，需后端启动后再次复测。
- 用户已停止后端；11:53 复核 8080 仍无监听。编号增量绝不重放；**请用户在 IDEA 当前分支重编译并冷重启一次**，之后再读取真正的监听 PID/classpath、通过新登录会话做版2排程/发布/凭据/受保护AI流程验证。完整 40 实例、生产采购/加工事实与合并仍未完成。

## 12:29 IDEA 新 JVM 真实 APS 发布与受保护 AI 的可复核边界

- 用户按要求在 IDEA 重新编译/冷重启：8080 监听 PID **46120**、开始时间 **2026-09-30 11:56:20 +08:00**；独立运行态检查 ERP APS Service、编号分配器/Mapper、AI 快照装配类的 `.java` mtime ≤ `.class` mtime < JVM 开始时间，实际 JVM classpath 各由当前 `ruoyi-erp/target/classes` 唯一提供；`ruoyi-admin/target/classes/application.yml` 为 `dev`，后端及 `10.151.240.161:80/dev-api` `/auth/code` 均200且验证码关闭。登录会话仅内存，不记录/输出 Token、私钥或密码。证据 `.codex/full-flow-20260928/aps-runtime-class-proof-20260930.json`。**该 JVM 尚未加载后续来源 ID 修复**。
- 写之前真实 GET 显示冲压车间范围恰好一个工单 `SCGD202609280001`，状态 RELEASED、有剩余10；数据库独立只读检查 2026-09-30 七处工位同一真实有效班次，均8h有效/余额、计划/实际0；历史两条 APS 仍是 COMPLETED/版本0，无预约。仅经受权限保护的 `POST /erp/prd/schedule/run` **一次**创建方案「九月末紧固件加工白班排程」，服务端生成 `APS202609300002`（ID `2105146586965131265`、版本2、COMPLETED），7任务与7逐日行1.15h，序列号从1到2，草案未占用/实际工时0。GET 任务与甘特图匹配，独立 `READ ONLY` 事务逐笔确认来源任务、工序加工+准备和日负荷一一匹配。意图、响应及独立回查：`.codex/full-flow-20260928/aps-v2-{run-intent,run-result,draft-readback}-20260930.json`。
- 经 `PUT /erp/prd/schedule/publish/{planId}` **一次**（非前端绕过）业务200；独立新只读连接逐笔核对 `PUBLISHED`/方案 version1，7 逐日行 = 7 班次预约 = 七日历计划工时合计 **1.15h**，每条预约关联原任务/日期/资源/真实班次日历且计划余额相减正确；7 个日历实际工时仍**0**，工单保持 RELEASED、计划日期回写，旧两方案状态、版本0与业务编号均原样，序列表2。证据 `.codex/full-flow-20260928/aps-v2-{publish-intent,publish-result,published-readback}-20260930.json`。此为计划占用，不是人工制造、设备实测或收入确认；未测试双 API 并发提交/异常强制回滚的全部事务路径。
- 同一已发布方案走受保护 `POST /ai/decision/delivery-risk/run`：第一次 `horizonDays=1`，仅到9月30日，真实 MRP 明细需求日**10月9日**不在窗口内，`SHORTAGE_SIMULATION_INCOMPLETE` 结束 FAILED（无证据/动作）。在只读核对真实 MRP 日期后，第二次显式 `horizonDays=30,forceRestart=true` 生成另一 run：context、baseline、shortage、aps、merge SUCCEEDED，rank FAILED/`DECISION_NODE_FAILED`，后续节点跳过，证据0/动作0；两个 FAILED run 原样保留，不审批、不执行、不把失败当已分析完成。意图及节点码 `.codex/full-flow-20260928/aps-v2-ai-{intent,result,30day-intent,30day-result}-20260930.json`。
- 不改旧 AI 运行：只读取得其**本地持久化快照**到 Git 忽略目录，在单独离线 JUnit 中重放（无 DB 连接/业务写），确认为 `SavedScheduleCapacity.measure=2.8`、4个方案进入排名，失败实际发生在 `DecisionSourceFactVerifier.numericFacts`：相同工单/物料/需求日的不同 MRP 明细按通用聚合键撞成不同数量，校验失败关闭。已经新增 `RequirementFact.detailId`（兼容旧 Java 构造器/JSON）、ERP 装配透传真实 `prd_material_requirement_detail.detail_id`、AI 源数值叶子用该行 ID 做证据键且拒绝非法 ID；旧快照仍没有行 ID，故不能把离线旧 run 强行验成成功。两项新单测 RED(找不到行标识构造器)→GREEN(两行独立证据键及相同行 ID 矛盾仍拒绝)，ERP `DeliveryContextAssemblerTest` 54 项及 AI 装配12项定向绿色；**新代码尚未由 PID46120 JVM 加载，需第二次 IDEA 冷重启后重跑真实 AI，不得断言 AI 正向成功。**
- 生产收货/工序报工、IoT 同设备关联、采购单位核验、审批执行和40个相互独立正式案例仍未完成；`plus` 分支尚未合并、推送或提交。本节只能证明本机单条正常业务数据的排产计划与受控失败路径。

## 12:47 MRP 明细级证据护栏复审、最终模块回归与第二次运行态门槛

- **溯源精度与 fail-closed**：两笔不同 `detail_id`、同工单/物料/日期的 MRP 净需求保留两条独立 ERP 数值叶子，来源 ID 为数据库行主键，主数据/租户与 ERP 读取上下文不变，`snapshotHash` 本来就覆盖来源 detail ID。第一次新测试2项 RED（旧 DTO 无行主键构造器）后 GREEN；独立规格审查指出同 detail ID 即使同值也不能去重（否则缺料仿真会重复计需求），新增回归 RED→GREEN；二审指出旧无 ID 和新有 ID 的混合快照可双计需求，再新增 RED→GREEN 的混合来源拒绝。最终 `DecisionSourceFactVerifierTest` **4/4**、`DecisionAnalysisAssemblyServiceTest` **12/12**。旧 JSON 可反序列化，但旧 RUNNING 快照与新 ERP 明细行证据键不一致时**安全拒绝续跑**，必须由受保护入口显式 forceRestart 产生**新**运行；禁止将已 FAILED 的两条本机运行改写或补字段。
- **人工排程审计防冒用**：前端手工 `/erp/prd/schedule/run` 的业务对象可能夹带 createBy/updateBy；仅手工单参数路径改取服务端登录用户 ID，受保护 AI 双参数内部路径沿用由 DecisionActor 赋值的来源；相关类 `PrdScheduleServiceImplTest`41项绿色，源码差异已复核。没有改变旧排程身份、产能凭据或已完成 API 发布。
- 代码修正后的最终六模块同一次 Maven reactor **41 模块 BUILD SUCCESS**，公共AI **106**、ERP **897**、MES **635（其中2项跳过，633实际执行）**、IoT **224**、业务AI **339**、Flow **67**，均零失败/错误；日志 `.codex/full-flow-20260928/full-business-reactor-final-evidence-20260930.log`。之前有一份**只用于定位已持久化失败快照**的临时 JUnit，它确认旧快照在新代码下依旧失败（符合未回填/不可伪造设计），已从源码目录单文件移除，不在最终 reactor。TDD RED/单测不等于当前 PID46120 已加载新版 AI 证据类。
- 前端发布按钮针对待发布 v2 草案增加“发布（需核验）”与 tooltip，明确生成时无日历8h推估 ≠ 可发布，真实班次/有效余额由后端事务核验；旧版仍隐藏发布、已发布 v2 不允许再次行内发布。变更后 `npm run test:unit` **131/131**，4GB `vue-tsc --noEmit` 退出0，带内存监控的 production Vite 构建退出0（最低剩余RAM4544MB），证据 `.codex/full-flow-20260928/frontend-{unit,typecheck,build}-after-list-hint-20260930.{log,json}`。真实 Edge 单浏览器只读巡检三条排程：两旧版历史标签/禁止发布、新版已发布标签/对应甘特和风险分析入口、勾选歧义护栏与筛选重置均为 true；无业务写请求/JS错误，浏览器已关闭；证据 `.codex/full-flow-20260928/ui-aps-schedule-20260930.json`。
- 在两条受保护 AI 运行 FAILED 后再次独立只读连接确认新方案仍 PUBLISHED/版本2、7占用1.15h、实际工时0，旧版/流水号2不变（`.codex/full-flow-20260928/aps-v2-post-ai-readback-20260930.json`）；系统库独立只读核验两个运行仍各自 FAILED，证据/动作0（`.codex/full-flow-20260928/aps-v2-ai-failure-readback-20260930.json`）。后端当前 PID46120 启动早于本轮 common-ai/ERP/业务AI 改动，**下一步需用户在 IDEA 重编译并冷重启，再取得新登录会话，不能现在重跑新 AI 当作修复已验证。**

## 12:58 第二次冷重启后的真实受保护 AI 正向回查

- 用户在 IDEA 自行重编译、冷重启后，监听中的 JVM 为 PID **39820**；`dev` 过滤资源有效。逐项核对 common-AI 的需求快照、ERP 的来源装配/排程、业务 AI 的数值验证及排程编号分配类：编译时间早于进程启动，运行 classpath 各只有一个来源。证据 `.codex/full-flow-20260928/aps-runtime-after-mrp-20260930.json`；仅能证明当时该进程的运行态，不能替代部署检查。
- ERP API 创建并发布**新的**排程 `APS202609300002`（ID `2105146586965131265`）：独立只读回查显示工时口径版本 **2**，7 条逐日负荷和 7 条逐班次占用凭据，计划占用 **1.15 小时**、实际工时 **0 小时**；前两条版本 0 的历史方案未改。证据 `.codex/full-flow-20260928/aps-v2-published-readback-20260930.json`，该发布属于本地受控测试，不代表实体车间已生产。
- 新登录会话对同一已发布方案重新发起**一次新的** 30 天受保护分析，运行 ID `2105159630382362626` 到达 **WAITING_APPROVAL**，风险 **MEDIUM / 20.00**，分析 Flow **SUCCEEDED**（9 节点成功、0 失败）。独立只读事务逐库回查：**90 条互异证据键**；其中 6 条 MRP 需求明细的数值叶子逐行匹配 ERP 的真实 `detail_id` 来源；仅 1 条 `CREATE_PURCHASE_REQUISITION_DRAFT` 动作为 **PROPOSED / NOT_STARTED**，未产生采购草稿，实际工时仍为 0。原两条 FAILED 运行保持原状，未通过更新旧快照伪造成功。证据 `.codex/full-flow-20260928/aps-v2-ai-success-readback-20260930.json`。
- **待人工决策**：本次只是分析与建议，不自动对该 run 审批、执行或创建采购申请草稿；须先核对物料包装用量、离散单位与动作载荷的数量依据并取得该 run 的明确授权。ERP/MES/IoT 采购收货、质检、派工报工、设备关联及 40 个独立正式案例单构建 SLA 尚未验收，不能将 1.15 小时计划占用当作实体制造工时，不能将该次正向运行描述为全链路通过。分支仍未提交、推送或合并 `plus`。
## 13:05 待审采购建议的单位/重复来源门禁与 MES–IoT 只读就绪检查

- 同一 `WAITING_APPROVAL` run 的动作载荷是 **4 条原始 MRP 缺口**，不是经供应商起订量换算后的采购量：物料代码 `IF20260914139` 14.659 千克、`IF20260914140` **5.4292 个**、`IF20260914145` 17.9165 千克、`IF20260914262` 0.2715 升。6 条租户内 MRP 明细各自与动作缺口之和逐条一致，**不代表每条可直接下单**。四种物料的有效供应关系各自 `min_order_qty` 数值是 500、510、560、830；数值虽高于缺口，但关系表未标明 MOQ 所属单位，不能证明与缺口同口径或据此自动放大申请量；对“个”这个离散单位的 5.4292 个需要业务负责人核对 BOM 用量/单件包装与计量口径，不能凭测试自动取整或放大到 MOQ。到货要求日是 **2026-10-09**，部分有效供应关系提前期分别为 20、12、17 天；从 2026-09-30 算起不能默认满足交期。独立只读、显式回滚证据 `.codex/full-flow-20260928/aps-v2-purchase-quantity-readback-20260930.json`。
- 同源 MRP 明细 `2104506638993903618` 已对应一条此前创建且仍为 **DRAFT** 的采购申请 `CGSQ202609290001`，物料 `IF20260914140` 数量 **510 个**；如果本动作直接创建草稿，会对该来源产生**重复申请风险**。既有的按 decision idempotency key 防重仅约束同一 AI 动作重试，不能据此当成跨人工申请去重；此处必须先人工核对/修正来源与业务数量，**不审批或执行** run `2105159630382362626`。既有单也不擅自修改、审批或入库。
- 另对 ERP/MES/IoT 跨库做独立只读快照：同一 ERP 工单的 MES 工单 `SCGD202609280001` 状态 RELEASED、计划 10、实做 **0**；已派工 1 条仍 PENDING、实做 **0**，MES 工单尚无设备绑定。IoT 设备 `DEV0025` 映射 MES 设备 `EQ0001`，设备当前 OFFLINE、反向 `iot_device_id` 尚空；该 IoT 设备当前活跃告警 0。四种短缺物料在 ERP 可用物料库存表无行（不等于完整库存/在途扣减审计）。证据 `.codex/full-flow-20260928/mes-iot-erp-readiness-20260930.json`。没有真实上料/到货/过站事实，不把待派工与历史 MQTT 数据伪称实体车间已完成制造；应按原计划继续补齐真实可证明来源，避免越过领料、IQC 与采购数量门禁。
- 新鲜运行 `python -B -m unittest discover -s doc/ai/full-flow/scripts -p 'test_*.py' -q`：**319 项、0 失败/错误**，退出 0；只覆盖脚本离线合同，不代表服务端业务流程或 40 个真实独立案例已运行。本轮未重放增量 SQL、未新增业务写入，分支没有提交或合并。

## 13:06 真实决策详情页面只读验收与新发现

- 单次 Edge 浏览器通过用户正在运行的前端 `http://10.151.240.161:80/` 登录（验证码已由用户关闭），仅请求该 run 的详情、时间线、证据 GET：页面风险 **MEDIUM / 20.00**、待审批、单条待审采购建议及不可在未勾选动作时批准；证据分组共 **90**（40+14+7+6+7+15+1）。浏览器 **0 个 JS/控制台错误、0 个业务变更请求**，只发生 1 次登录认证 POST；未点击审批/执行，浏览器已关闭。独立页面/GET 证据和截图见 `.codex/full-flow-20260928/ui-decision-readonly-20260930-130636-2ae8ee96/report.json`（截图同目录）。
- **实际展示缺陷（待代码修复与复测）**：KPI 区把千克、个、升等不同维度数值直接相加的 `totalShortageGap = 38.2762` 标为“缺料缺口（件）”，场景比较与预览也将该混合值当可比较数量；这是源头 `ErpDecisionShortageSimulation` 对各物料 `maxGap` 求和、跨模块 `DecisionKpiSet` 与排名器继续消费无单位数值造成的。前端需先停止误导性展示，不擅自修改历史快照或对不同单位做未经定义的换算；**前端修复不能证明后端指标/排名口径已解决**，后者需另行设计可追溯的单位分组/量纲安全合同与测试。

## 13:37 混合单位 KPI 展示修复、审计提示与复测门槛

- **根因确认**：同一 30 天运行的 4 种物料在已核对的基本单位中分别是千克、个、千克、升；后台 `ErpDecisionShortageSimulation` 将四个不同单位 `maxGap` 求和得到 `38.2762`，`DecisionKpiSet.totalShortageGap` 无单位，`DecisionScenarioRankingService` 仍用该数参与标准化加权方案排名。4 条原始 `total_shortage_gap` 证据均为 `38.2762` 且 `unit=NULL`，属于不同场景的四条汇总证据，并非四个物料各自的证据；源明细仍按真实数据库逐行保留。不能用本轮的**显示修复**声称后台排名、结果口径已修好，也不能把此数标成“件”或直接用于采购。后端量纲安全与排名需要单独设计、版本化并重新评测；旧运行不回填。
- **前端保守修复**：KPI 基线/推荐/实测、方案列表、预览在缺料物料数为0且明确缺口0时仅展示“0（无缺料）”；其余非空原始合计不再作为跨单位量展示，提示单位未核并引导查看分项证据。证据抽屉继续保留原值供审计，但在表格该行及抽屉顶部注明单位未核/不可直接合计。采购审批卡片增加同源 MRP 未结申请与包装/MOQ/需求日核对及“审批会启动创建草稿”的提醒，未硬编码本机申请单号，未禁用合法审批入口。`test:full-flow-ui` 串行接入两套新测试并在接线测试中核验，覆盖 0/非0、缺值、非采购不提示、原始证据保真和采购提示；既有其他 KPI 继续展示。
- **导出代码（尚未加载到用户 JVM）**：业务 AI 的 Markdown 导出仅在证据含 `total_shortage_gap` 时加入单位未核说明，逐行保留原 metric/value；`DecisionQueryServiceTest` 定向 Maven reactor **10 项/10 项通过，0 失败、0 跳过**。但运行中 PID **39820** 不会自动加载新 Java 类，真实 Markdown 导出需用户下一次 IDEA 冷重启后才能复验，不将单测当运行态验收。
- **新鲜验证**：`npm run test:unit` **141/141**、`npm run type-check` 退出0、目标 ESLint 退出0；4GB Node 堆限制、启动前确认可用内存大于 6.5GB 的 production Vite 构建退出0（`.codex/full-flow-20260928/frontend-{unit,typecheck,build}-after-evidence-20260930.log`；这三份日志的实际前缀分别为 `frontend-unit-after-evidence-20260930.log`、`frontend-typecheck-after-evidence-20260930.log`、`frontend-build-after-evidence-20260930.log`）。本机仅用于独立 UI 只读复测的单个 Edge 已关闭，无新增或重启用户服务。
- **真实页面复测**：同一前端 URL、同一 run 在 1440×1000 浏览器下观察到 MEDIUM/20.00、待审批、90 条证据；KPI/四个方案/单个预览不再将 38.2762 当“件”；证据原值与量纲警示同在，超长警示可通过悬停完整查看；采购警示可见。0 JS 错误、0 HTTP 错误、0 业务写请求、0 横向视口溢出；只额外发生 1 次登录认证 POST，未点击审批/执行/导出。复测与截图 `.codex/full-flow-20260928/ui-decision-unit-retest-20260930-133337-a5608016/report.json`（同目录图片）。复测后独立数据库只读事务再验：run 仍 WAITING_APPROVAL、动作 PROPOSED/NOT_STARTED、AI 新采购草稿 0、原 510 个申请仍 DRAFT、MES 完工 0；证据 `.codex/full-flow-20260928/post-ui-stability-readback-20260930.json`。
- **不放行条件继续有效**：在真实包装倍数/MOQ 单位、同源未结申请处置和量纲安全排名修复前，不能批准或执行这个采购动作。ERP/MES/IoT 的采购收货、IQC、领料、报工/实绩和 40 个独立正式案例仍未完成。本轮未重放增量 SQL、未提交/推送/合并 `plus`。
## 13:52 页面可读性与 Markdown 审计归属收口（不等于业务闭环完成）

- 针对复审中证据抽屉缺乏可信单位的事实，三个 KPI/方案视图改为单元格短标记 **“—（单位待核）”**，近邻说明“原值如有返回，因单位待核不能确认可合计；请核对物料主档与来源”；不再承诺“查看分项证据”就能核准单位。数量明确为 0 且缺料物料数为 0 时仍显示 **“0（无缺料）”**。真实前端 1440×1000 只读复测 4 条方案/KPI 缺口行均 **48px**，无水平溢出/JS/HTTP 错误；90 证据、采购审批提示及原值“单位未核；不可合计”仍可见。未点击审批/执行/导出，0 业务非 GET 请求，浏览器已关闭；证据 `.codex/full-flow-20260928/ui-decision-final-readonly-20260930-134930-e9ffd078/report.json` 和同目录图片。
- Markdown 的新审计警示不再笼统声称导出表可区分同值场景：注明“同值场景归属须在受控详情页核对”。仅保留当前导出表原始 metric/value 与对量纲的警示，**没有修复后台汇总与排名**。追加断言的定向 JUnit 已观察到有效 RED（10 项中 1 失败，因缺归属说明）→GREEN（**10/10、0 失败/错误/跳过、BUILD SUCCESS**），日志 `.codex/full-flow-20260928/markdown-attribution-{RED,GREEN}-20260930.log`。运行中后端 PID39820 尚未加载此新 Java 类，禁止宣称真实 Markdown 已修好；仍需用户下一次冷重启后再只读导出验证。
- 最后一次代码变动之后完整前端 `npm run test:unit` **143/143**（日志 `.codex/full-flow-20260928/frontend-unit-final-20260930.log`），`npm run type-check` 和修改文件定向 ESLint 退出0，生产 Vite 构建以 4GB Node 堆执行退出0（启动前可用内存约 8.5GB；`frontend-typecheck-final-20260930.log`、`frontend-build-final-20260930.log`）；旧 Sass @import/包体积警告仍为告警而非构建失败。前端实际页面复测时加载了新展示代码，服务由用户管理，本任务未启动第二套前后端。
- 页面复测结束再次读取本地 MySQL 只读事务：run `2105159630382362626` 仍 `WAITING_APPROVAL`，其单条动作 `PROPOSED / NOT_STARTED`，该 run 创建的采购草稿数 **0**。此结果只是本地现场安全回查，不是“ERP/MES/IoT 全流程通过”。不对业务库执行新 SQL 迁移，不提交、推送或合并到 `plus`。下一阶段须先取得真实包装/供应商 MOQ 单位/同源申请处置事实，并设计、实现、评测服务端量纲安全排名与采购跨来源防重门禁，才可能安全授权审批执行和衔接物理收货/制造流程。
