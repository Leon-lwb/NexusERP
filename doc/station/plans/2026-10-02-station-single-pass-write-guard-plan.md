# Station 单件过站写入口及客户端结果真实性实施计划

> 日期：2026-10-02（Asia/Shanghai）。用户已批准共享业务校验、事务内锁定重读、严格重放比对，并批准未登记工位写入拒绝；同时授权优化 E:/Nexus/nexus-station。For agentic workers: use subagent-driven-development for the independent client task; backend critical-path implementation remains local. 不自动提交、推送、合并或执行业务数据库写入。

**Goal:** 封堵单件过站写入绕过四重校验，保证已提交请求严格幂等，并让 Station 客户端真实区分提交成功、业务拒绝和网络待确认。

**Architecture:** 查询与写入共用四重检查函数。单件写在实际 MES REQUIRES_NEW / REPEATABLE_READ 写事务中先锁 SN，再锁卡、工序、工位、报工及质量来源并重读；固定 SELECT 显式限定租户及删除状态，质量范围读不能只锁最新一行。旧已提交同内容请求可重放，冲突内容拒绝；所有写及 SN 推进同事务，失败回滚。客户端不弱校验放行、不把业务拒绝放入离线队列、不把待回放显示成成功。

**Tech Stack:** Java 17 源码级别（JDK 21 执行）、Spring 事务/动态数据源、MyBatis-Plus/MyBatis、JUnit 5、H2 私有内存测试；Electron/Vue 3/TypeScript/Vitest。H2 仅证明其支持的真实事务、行锁及 SQL 行为，不冒充 MySQL5.7 间隙锁或现场验收。

## 范围与保护

- ERP 基线：codex/full-flow-acceptance-20260928 / 77b044fe；两份 application 配置保留原摘要。
- Station 起点：master / f28b366；创建 codex/station-pass-guard-20261002，不提交。
- 保留共享 X-API-KEY 与控制器固定 000000 租户协议；不凭空新增逐设备认证协议。已登记工位绑定证明工序来源，不等于逐设备身份认证。
- 不改批量报工业务规则、历史数据、采购门禁或旧 AI 运行。质量审查证明必须同时保留原请求身份与解析人员快照，因此补充单列请求指纹的仓库 DDL；不执行任何业务库迁移。
- 质量锁必须覆盖相关结果范围（含空范围），避免先前快照和新增 NCR 绕过；未声明真实数据库运行态或实际传感器/制造验收。

## Task 1：后端行为反例（先 RED）

**Files:** E:/Nexus/nexus-erp/ruoyi-modules/ruoyi-station/src/test/java/com/nexus/station/service/StationPassServiceTest.java；新 StationSinglePassWriteTest.java；StationPassTransactionTest.java；模块 pom.xml 的 test-scope H2。

- [x] 先运行既有 Station 测试建立非零基线，保存新日志。
- [x] 增加写入口反例：前序未完、错工位、未登记或冲突绑定、最新 FAIL/未关闭 NCR、重复 PASS、错误租户、无真实写事务、错误路由、来源缺失/异常、同键内容冲突。拒绝后断言无报工、数量和 SN 写入。
- [x] 增加有效 PASS/FAIL、复测 PASS 后放行、已提交同内容重放及六段工位归一化正例。
- [x] 新断言在旧实现实际失败才进入实现；缺类编译错误不计有效 RED。

## Task 2：共享检查与完整事务写入口

**Files:** StationPassService.java；新增 service/StationPassChecks.java；mapper/StationPassGuardMapper.java；controller/StationDeviceController.java；新增 StationSinglePassFingerprint.java / MesStationReport.requestFingerprint / MES 主脚本及一次性增量 / 数据库登记；同密钥 Station 通道及客户端实际心跳使用的 MES FlashReportController 只改拒绝日志。

- [x] 提取既有四重规则到纯检查函数，validate 与 reportSingle 共同调用，保留原返回结构。
- [x] 新增参数化固定锁定 SELECT，显式 tenant_id、del_flag、真实归属；测试直接执行生产 Mapper SQL。
- [x] reportSingle 使用 REQUIRES_NEW/RR，核对当前租户、MES 路由及实际非只读事务；锁 SN 后读已提交报工，严格比对规范化载荷。报告唯一键冲突不能无条件吞为成功。
- [x] 新报工必须有无冲突的同租户登记绑定；锁卡/工序及全部相关测试、NCR 来源，在同事务检查后写报告/累计/推进；检查写入影响行数和必要归属，失败回滚。
- [x] API 密钥失败日志移除输入密钥；保留失败关闭。
- [x] 重跑定向行为 GREEN；真实 Spring/动态数据源代理＋私有 H2 测事务回滚、跨库悬挂、并发同 SN、同键重放与不同键重复请求。

## Task 3：独立 Station 客户端配套（独立写集）

**Files:** E:/Nexus/nexus-station/src/main/mes/client.ts、types.ts、job/offline-queue.ts、ipc.ts、renderer/src/views/pass.vue、相关 preload/renderer 接口及新增/现有 Vitest 测试。

- [x] 先测试旧逻辑：400/404 不得弱校验放行；HTTP200 的业务失败不能入队；网络异常保持原 clientTs/payload 入队，结果不得 pass=true；仅服务器确认写成功才绿屏。
- [x] 引入可辨识的业务/网络错误与可测试编排函数；IPC 使用真实函数，不只测替身。
- [x] 正向提交状态明确为 COMMITTED 或 QUEUED；FAIL 上报也不得把待回放称为已上报。
- [x] 离线 flush 重入/flush期间 enqueue 不得丢任务；网络断开不得因五次定时重试永久丢任务；业务拒绝保持可见的隔离记录，不继续发送或丢弃。
- [x] 校验必须拒绝降级放行；保留原载荷和租户/设备协议，不假定已取得逐设备认证。
- [ ] vitest 实际 RED→GREEN，完成 typecheck/build；不启动 Electron/用户服务，不安装插件或新依赖。

## Task 4：审查、相关回归及阶段证据

- [x] 规格与质量审查分别核对安全边界、并发、回滚和接口兼容，不以审查替代实际测试。
- [x] 串行运行 `mvn -o -B -pl ruoyi-modules/ruoyi-station -am test -DskipTests=false -Dprofiles.active= -DforkCount=1 -DreuseForks=true -Dmaven.test.failure.ignore=false`，堆受限；检查新 XML 用例非零、失败/错误及禁用数。
- [ ] Station `npm test`、`npm run typecheck`、`npm run build`，保留原有告警及未验证项。
- [x] 校验两个工作区 git diff --check、配置摘要及分支；登记文件、真实命令、RED/GREEN 和未验范围。
- [x] 本包不替代另外八个任务包，不声称数据库锁的生产实测、现场硬件或全流程验收完成。
## 2026-10-02 执行检查点

- 既有 Station 基线 70 实际／70 通过；第一组业务 RED 为 17 实际／16 预期断言失败、0 错误/跳过。
- 第一轮真实 H2 集成暴露同 SN 换同工序工位重复计数；另外两个 H2 RR 40001 序列化回滚与直连 master 测试准备问题分开处理，不当作有效业务 RED。
- 规格审查首次提出查询/写入重复条件不一致、工序工单归属遗漏；真实 SQL RED 分别重现后修复。规格复核只读 PASS，不冒充测试重跑。
- 当前后端 Station 定向 120 实际、0 失败/错误/跳过；覆盖 14 个实际私有数据库/代理场景、23 个新业务矩阵、7 个单件控制器分类、6 个同通道日志脱敏参数化场景以及 70 个既有用例。
- 代码级幂等重试支持事务资源故障及提交结果不确定：控制器退出服务代理后返回固定 JSON code=503（不是 HTTP 状态变更），不得在失败事务内部重试；只允许保持同一原始载荷回查。
- 完整扩展 reactor 正在按最终源码重跑；前端及质量复核未闭合前不标整体包验收完成。首轮 reactor 只代表当时 107 用例源码，不能转授当前来源。

### 原请求身份质量检查点

- 质量审查发现 code-only 请求重放不应依赖今天的员工账号关联；忽略可空 ID 会削弱严格原输入比较，替换审计 ID 又会丢失已核验的操作员快照，因此增加 nullable 请求指纹列。
- 初次反例准备遗漏 TenantHelper.dynamic 的 Supplier 执行，导致模拟来源始终 NULL；该尝试不计有效业务 RED。修正事实准备后、在旧解析结果比较规则下，17 实际／3 预期断言失败、0 错误/跳过，分别重现关联改变、关联删除及原始输入 ID 改变被同一解析值掩盖。
- 指纹固定版本、字段长度分帧、UTF-8 SHA-256；包括租户、SN、归一工位、clientTs、result、原 operatorId/operatorCode 和规范化缺陷集，不含当前解析人员姓名或关联。
- 当前定向后端 125 实际全部通过，含 19 私有真实 SQL/代理用例。旧 NULL 指纹不猜测升级；一次性迁移在自动销毁的私有 H2 中验证 NULL 保留和重复执行拒绝，不能转授真实 MySQL 迁移。
- 存量数据库必须先由用户审核备份并应用 `2026-10-02-station-single-request-fingerprint-ry-mes.sql` 后再加载新后端。本轮未执行该迁移。此前 120 用例源码的完整 reactor 仅为中间证据，不转授新指纹源码。

### 最终后端验证（2026-10-02 14:14，Asia/Shanghai）

- 指纹最终源码被冻结并执行完整 Station/ERP/MES/IoT/AI/Flow reactor：286 份新 XML、3822 发现／3820 实际，0 失败／错误、2 个既有 NCR 禁用用例。Station 125、ERP 1901、MES 632 的用例分别按新时间窗统计，不重复累加定向测试。
- 同进程 Maven 原生退出码 0；所有纳入验证的 Station/MES 修改源码及 SQL 摘要从执行前到执行后保持一致。定向测试不重复累加到3820。
- 前端规格复核已通过四个完整唯一正向检查的修复。独立质量审查发现持久化隔离失败会使下轮重复发送已拒绝请求，正在加入进程内拒绝/持久化故障锁存与只读可见状态；不在该修复及最终前端验证前标任务包完整验收。

### 收尾检查点：最终源码隔离验证完成，运行态未验

- 原请求指纹非法Unicode严格拒绝的4个用例RED2/GREEN4；最终扩展reactor在新时间窗287份XML／3824实际、0失败/错误、2既有NCR禁用，Station129已包含其中。源码/SQL摘要未变，原生退出码0。
- 前端规格/质量复核发现的问题都按真实反例修复并由原审查者只读复核；主线程最终145实际通过、Node检查及替代vue-tsc3.2.8检查exit0、构建exit0，源码摘要未变。
- 原生 `npm run typecheck` 因Station本地没有vue-tsc而exit1，因此以上两条包含原生类型检查的复选项仍保留未闭合；不安装依赖、不把替代版本叫原生通过。
- 两仓库保留全部未提交改动，配置摘要与plus指针不变；没有写业务数据库、启停服务、提交/推送/合并。MES迁移和统一最终构建运行态复验必须另经审核，不在本轮伪装完成。
- 详见 `E:/Nexus/nexus-erp/doc/station/2026-10-02-单件过站写入口与客户端配套阶段报告.md`。


### 用户授权分段提交续接

2026-10-02用户明确要求按之前的提交逻辑保存代码。以上“不自动提交”的实施边界仍保留为当时约定，本次新增显式授权只用于分段保存已验证代码；不授权推送、合并或清理配置。提交前完整回归与独立Station检查重跑，原生vue-tsc缺失保持限制。代码保存为 ERP `b63f1950` / `5ae0e4e6`、Station `26749107` / `e8af6f3c`；后续全模块修复继续沿既有P1/P2计划，真实数据写入仍须目标环境和来源门禁。
