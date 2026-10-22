# AI 第二阶段实施与本地验证记录

> 基线 plus / b0a37949；实施跨 2026-10-22 至 2026-10-23（Asia/Shanghai）。本报告只记录本次代码与本机自动化验证，不作为生产发布或真实厂商验收。

## 数据库执行范围

一阶段于 2026-10-22 22:39:51 +08:00 在 localhost:3306/ry-vue（MySQL 5.7.14）完成四权限增量：新增 4、回读 4、重复新增 0。详见[本地迁移记录](2026-10-22-ai-stage-one-local-migration.md)。未修改角色和租户套餐授权，未执行 ERP/MES/IoT 脚本。

第二阶段的 13 张新表及两个独立权限种子已同步安装基线和增量，**未执行到现有业务库**。仅由专用启动器新建隔离库，校验随机所有者标记后测试并删除。最终结构 SHA256：008b7db44a70157d63334c64f5183b921895715febf381a99e686a6dc7289518。

## 批次实现

| 批次 | 实现与验证入口 | 结果 |
| --- | --- | --- |
| 2A | common-ai/governance；AI service/governance/AiCallLedgerService、AiGovernedModels；ChatModelFactory 和七协议提供者 | 独立 callId、价格冻结、分币种汇总、未知用量、重复回调、独立 master 事务、迟到用量审计及输出上限 |
| 2B | AiExecutionRepository、AiChatExecutionService、AiChatSseLifecycle、AiChatRequestController；页面/抽屉与 AiRequestCancel | 持久化请求、所有者与代次、唯一终态、跨节点取消意图、部分正文/历史完成状态及旧事件隔离 |
| 2C | common-flow/execution；FlowExecutionGovernance、FlowEngineImpl、节点恢复策略和 HTTP 稳定键；实例前端 | 独立有界池、30/10/5 秒租约机制、停止受理/确认、持久化等待、冻结配置与模式、循环/并行冷恢复、尝试审计 |
| 2D | AiAbRunService、AiAbRunController、AiAbRunPanel、AiGovernancePanel | 冻结批次/题序/槽位、原子认领、部分成功、保守恢复、批次评分/报告、按币种预占/结算与独立管理权限 |

## 当次验证结果

| 检查 | 实际命令或入口 | 当次结果 |
| --- | --- | --- |
| 后端完整回归 | 实施计划规定的 AI/Flow 四包 Maven test 命令 | 159 个 suite，2,116 项通过，0 失败/错误/跳过 |
| 公共 AI | 上述命令选中 ruoyi-common-ai | 190 项 |
| 公共 Flow | 上述命令选中 ruoyi-common-flow | 59 项 |
| AI | 上述命令选中 ruoyi-ai | 1,580 项 |
| Flow | 上述命令选中 ruoyi-flow | 287 项 |
| 隔离真实 MySQL | python -X utf8 script/ai/verify-stage-two-mysql.py | MySQL 5.7.14，15 项通过，0 失败/错误/跳过；临时库已删除 |
| AI 定向前端 | npm run test:ai | 689 项通过，0 失败/取消/跳过 |
| 标准前端单测 | npm run test:unit | 2,205 项通过，0 失败/取消/跳过 |
| 非增量类型检查 | npm run type-check -- --incremental false | 通过 |
| 生产构建 | npm run build:prod -- --outDir node_modules/.tmp/ai-stage-two-build | 通过，产物位于隔离输出目录 |
| 聊天契约往返 | npm run test:ai:roundtrip | Java 生成场景 1 项 + 前端消费 38 项通过，0 失败/错误/跳过 |
| 定向 lint | ESLint 实际修改的 15 个 TS/Vue 文件，--max-warnings 0 | 通过 |
| 定向格式检查 | Prettier --check 实际修改的 18 个前端文件 | 通过 |
| 主应用依赖装配/授权与配置 | Maven ruoyi-admin package，定向授权与配置测试 | 12 项通过，0 失败/错误/跳过；51 模块打包成功 |

后端计数按本轮开始时间 2026-10-23 01:43:05 +08:00 筛选新生成 XML；完成于 01:44:17。隔离 MySQL 于 01:45:43 至 01:45:55 运行。前端各入口分别列出，存在覆盖重叠，**不相加宣称唯一用例总数**。构建中的既有体积/浏览器 externalization 提示及组件替身警告没有写成测试失败；没有真实厂商请求或用户服务启动。

## 已覆盖的重要反例

- 每请求多次调用独立记录，价格更改不重算历史，分币种汇总不换汇；未知金额保留预占，迟到可信用量只追加一次审计。
- 月预算与批次预算共同预占；并发不会突破可用额度；无匹配币种、不可信上界、不可控重试/输出路径拒绝；普通已认证文本多轮历史可通过。
- 取消与完成竞争、旧所有者消息回调、事件池拒绝、迟到流输出；先锁定执行权再写助手消息，消息/终态事务回滚。
- 节点排队期间租约失效不能进入外部调用；并行普通错误不能覆盖 RECOVERY_REQUIRED；不可重放节点被中断，即使工作线程随后退出，也保留副作用不明。
- 超时取消后的退出等待再次被中断时，仍先写待核对审计并保留线程中断标志；启动与取消使用同一临界区。
- 等待越过截止时间和取消所有者丢失不再永久停留；恢复队列拒绝不把 WAITING 控制行与实例投影写成相反状态；过期代次不能创建节点投影。
- 串行等待、并行独立分支、循环批次跨冷重启恢复；恢复节点的并行配置改变不改变原快照模式；两个独立循环使用自己的游标和操作键。
- A/B 相同问题文本与相同模型双侧区分槽位；重复启动、部分失败、安全续跑保留成功题目；定义只绑定可编辑字段，未确认批次阻止修改删除。

只读复核针对原八项 Flow 问题及清理再次中断的遗漏重放隔离 H2 反例；最终执行、实例与尝试均保持 RECOVERY_REQUIRED，未发现该整改范围内的新增必要修复。

## 数据库兼容修复与证据位置

真实 MySQL 运行捕获并修复了驱动 LocalDateTime/Timestamp 返回差异，以及数字 ID 文本转换导致的连接/字段排序规则冲突；最终 15 项测试重新全部通过。失败运行的临时库同样校验所有权并清理，未改写既有业务库。

可复核原始日志与摘要均位于 ruoyi-modules/ruoyi-ai/target/ai-stage-two-evidence/：backend-full.log、backend-summary.json、mysql-it.log、mysql-it-summary.json、frontend-ai.log、frontend-unit.log、frontend-summary.json、frontend-types.log、frontend-build.log、frontend-roundtrip.log、frontend-lint.log、frontend-format.log、admin-assembly.log 和 admin-summary.json。target 是本地产物目录，本报告固化计数和结构摘要；未将凭据、业务备份或真实用户数据写入文档。

## 运行默认值与后续边界

nexus.ai.governance.enabled / chat-enabled / flow-enabled / ab-enabled 均为 false，预算政策默认禁用，budget-verified-model-ids 默认为空。迁移与兼容核查通过后按 2A → 2B → 2C → 2D 显式启用。严格预算只有已核实协议、输入上界、输出封顶和无隐藏重试的路径获准；未认证路径在启用额度时拒绝。

本次未改写私有配置，未提交/推送、部署或启动服务，保留已有 .dd/。真实模型价格/计量准确性、传输取消的供应商行为、跨进程实机集群故障与生产迁移另行验收。旧历史统计不当作已核实账单，也不据此宣称工业决策正式提交、全量 SLA 或第三阶段评测完成。

后续 Git 交付说明：上述未提交状态为 AI 实施结束时的快照。2026-10-23 用户已另行授权在全仓导入整改后提交并推送，见[整改及完整回归记录](../../system/plans/2026-10-23-system-java-import-cleanup-report.md)。
