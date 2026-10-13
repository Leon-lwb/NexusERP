# 自研报表 P0 实施与验证记录

> 文档编号：NexusERP-REPORT-2026-005
> 日期：2026-10-14；分支：codex/report-module-plan；实施起点：c51e0f13。
> 状态：首期代码与本机验证完成。生产部署、业务人员签收和规模化性能 SLA 不属于本机完成声明。
> 用户授权：删除未投入使用的积木代码与物理表，不做备份/转换/迁移；保留模块名 ruoyi-report；自研定义、运行引擎、原生设计器并使用基础库；完成后提交 Git；自主启动必要服务。

## 1. 已交付范围

- 自有 JSON DSL v1、engine 1.0.0；TABLE 列表和单增长明细区 DOCUMENT 打印模板。
- 精确 ID/DECIMAL 传输与 BigDecimal 运算；有界受限公式、依赖循环与非法字段检查、维度受约束的全量汇总。
- 服务端中文字体测量、换行和分页；固定区内合并、续页表头、页码、末页区域；预览、PDF、样式 XLSX 共用 RenderPlan，明细 XLSX 保留原始精确值。
- 九张 rpt\_\* 元数据表，租户隔离、资源 ACL、业务角色与部门范围、敏感字段权限；导出同时要求报表与数据集 EXPORT 授权；草稿 revision、不可改写的发布版本、回滚、下线和归档。
- LIVE 分页和 SNAPSHOT 运行；固定发布/数据契约版本、参数和权限指纹；执行、读取和交付均重新检查有效账户与权限。
- 持久导出任务、事务内准入与幂等、最多三次执行尝试、attempt/lease 栅栏、取消、过期和私有文件下载。快照数据时间记录实际取得执行权的时点，不把排队时点显示为取数时点。
- 私有文件存储、写入预算、校验与路径守卫。过期文件删除失败保留引用供重试；补充进程中断后的临时/孤立文件有界回收，数据库不可用时保留待核查对象。
- 六个原生 Vue 页面：报表管理、数据集目录、设计器、运行预览、发布历史、导出中心；参数、精确值表格、纸张预览及 ACL 组件。支持字段拖入、列排序、计算列、固定区编辑、纸张/参数/汇总设置、定义文件和最多 50 个状态的撤销/重做。

六类初始数据集和模板已在本机创建、发布：

| 数据集代码                  | 模板         | 数据源与业务权限         |
| --------------------------- | ------------ | ------------------------ |
| ERP_STOCK_BALANCE           | 库存余额     | erp；wm:stock:list       |
| ERP_SALES_EXECUTION         | 销售执行     | erp；so:orderheader:list |
| ERP_PURCHASE_EXECUTION      | 采购执行     | erp；pu:order:list       |
| ERP_PURCHASE_ORDER_DOCUMENT | 采购订单打印 | erp；pu:order:query      |
| ERP_DELIVERY_DOCUMENT       | 送货单打印   | erp；wm:salesIssue:query |
| MES_IQC_TRACE               | 来料质量追溯 | mes；mes:qc:iqc:query    |

首期不开放任意 SQL、脚本、数据库连接串或外部数据源。仪表盘、交叉表、查询目录、订阅、分享属于后续范围。

## 2. 积木资产清理与本机数据库

删除积木/JimuBI Starter、Jeecg 专用 Maven 仓库、JimuReportTokenService、旧路由放行/租户排除、两处 token iframe 页面及专用前端环境变量。继续使用 ruoyi-report 与 com.nexus.report，并新增 ruoyi-common-report；ERP、MES、system 只依赖公共契约。

已核对本机配置并仅操作本地开发数据库：master=ry-vue，ERP=ry-erp，MES=ry-mes；MySQL 5.7.14。按授权执行 [P0 增量](../../../script/sql/update/2026-10-14-report-p0.sql)，删除显式 16 张 jimu\_\* 表及 test_monthly_report/tmp_report_data_1/tmp_report_data_income；建立九张原生报表表及 23000 菜单树。旧菜单、角色和租户套餐引用同步清理，重复执行通过。

最终回读：旧报表表 0；原生报表表 9；原生菜单根 1；已发布初始模板 6；启用业务数据集 6；本次合成报告/数据集/运行/导出任务均为 0。保留六类业务模板，清理仅涉及本次合成资源；没有清理 ERP/MES 业务单据、库存或真实用户资料。

主安装 SQL 已同步。其他环境没有执行，不把本机结果视作生产迁移；也没有向已有库导入主初始化 SQL。

## 3. 自动验证命令与结果

Maven 构建串行执行，避免共享 target 目录被多个进程覆盖。测试关闭 skipTests 并设置空 profiles.active，核查用例数非零。

```powershell
mvn -pl ruoyi-modules/ruoyi-report,ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes -am test '-DskipTests=false' '-Dprofiles.active=' '-Dtest=*Report*Test,QualityTraceDatasetProviderTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dstyle.color=never'
mvn -pl ruoyi-admin -am package '-Dmaven.test.skip=true' '-Dstyle.color=never'
cd plus-ui
npm run test:report
npm run test:unit
npm run type-check
npm run build:prod
```

| 验证              | 结果与口径                                                                                                           |
| ----------------- | -------------------------------------------------------------------------------------------------------------------- |
| 后端相关选择器    | 252 用例，失败/错误/跳过均 0；common-report 9、system 3、ERP 30、MES 10、ruoyi-report 200                            |
| 报表核心          | 编译、公式/权限、布局、版本、DAO 并发、运行、持久任务、存储与 REST 回归均通过                                        |
| 十万行明细 XLSX   | 实际写出 100,000 明细行，使用 ZIP64 兼容的流式 XML 读取器核对 100,001 行（含表头）；未把整个工作簿载入内存           |
| 导出精度          | 超长 ID、超过 Excel 精度的 DECIMAL 保留文本；合法小数按数值写入；字符串不转公式；PDF/样式 XLSX 保留计划舍入与断行    |
| 前端 test:report  | 60 用例，0 失败/跳过；含模型、schema 清理和实际 Vue setup/watch/路由守卫行为                                         |
| 前端 test:unit    | 完整命令通过；Node test runner 各批合计报告 1,815 个用例，另有项目自定义断言入口；不把断言数伪报为统一 runner 用例数 |
| 全仓类型检查      | vue-tsc --noEmit 通过                                                                                                |
| 生产前端构建      | Vite build:prod 通过；既有包体积提示保留                                                                             |
| admin 打包        | 完整聚合构建通过；此命令跳过测试，测试证据取自前面的独立测试命令                                                     |
| 定向格式/静态检查 | 前端报表范围 ESLint/Prettier、文档格式和 git diff --check 通过                                                       |

首轮整合曾暴露 LIVE 测试夹具错误地使用 SNAPSHOT，以及未经过 RUNNING 转换的问题；修正夹具并保留原竞争断言。版本完整性新增回归先复现未拒绝未知引擎/校验篡改，再增加运行与回滚保护。记录红绿过程，不把首轮失败报告覆盖成从未发生。

## 4. 真实 Spring/MySQL 与浏览器验证

使用最新 admin 构建启动 Spring 服务，连接上述本地 MySQL、Redis、RabbitMQ；关闭本机不需要的 SnailJob/Monitor 注册。只在隔离验证配置中添加合成 provider，正式构建没有测试 provider、认证旁路或演示接口。

真实链路已验证：

1. 有效租户和账户的重新授权；六个代码 provider 在 erp/mes 真实数据源上执行完整字段投影，不输出业务记录。
2. 创建原生合成模板、发布、建立快照；最大 long ID、超安全整数 DECIMAL 精确保留；生成打印布局。
3. 匿名访问被正常认证链拒绝；使用仅存于内存的独立临时测试会话访问原生接口。创建会话时在测试进程隔离用户登录副作用，未改写真实用户登录资料；没有把 token 写入文档、Git 或界面。
4. PDF、XLSX_DATA、XLSX_LAYOUT 逐一进入持久队列、完成并经正常鉴权 HTTP 接口下载；同请求键返回同任务。PDF 中文文本可读取，两类 XLSX 可由 POI 打开。
5. 验证结束注销测试会话，限定清理本次随机合成代码产生的元数据与私有文件，保留六类正式初始模板。

浏览器使用纯合成请求夹具运行真实页面组件，实际点击设计/编辑/保存、撤销/重做、运行、导出中心、纸张分页；控制台 error 为 0。额外将真实 Java 引擎产生的 55 行、三页 RenderPlan 接入纸张组件，核对中文长文本、续页表头和第 1/3、2/3 页码。夹具只存在于忽略的开发目录，没有放入生产入口。

| 可复核样例                                                                                                                  | 内容                                     |
| --------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| [设计器截图](../assets/report-p0/designer-synthetic.png)                                                                    | 原生固定区/字段/属性、撤销重做与保存状态 |
| [列表截图](../assets/report-p0/viewer-synthetic.png)                                                                        | 超过 JS 安全整数的精确 DECIMAL 展示      |
| [打印第一页](../assets/report-p0/document-page1-synthetic.png) / [第二页](../assets/report-p0/document-page2-synthetic.png) | 服务端 RenderPlan 的中文换行和分页       |
| [原生 PDF](../assets/report-p0/native-print-synthetic.pdf)                                                                  | 同一合成 DSL 和 Java 布局产生的三页 PDF  |
| [导出中心](../assets/report-p0/exports-synthetic.png)                                                                       | 个人任务与格式/状态；该截图使用合成队列  |

所有样例均为合成数据。没有宣称真实业务人员、生产账户 UI、全部角色组合、生产集群或现场设备已人工验收。

## 5. P0 场景证据映射

历史路线的 29 个 P0 场景在下表归组；场景数与实际用例数不同。详细断言见各测试源码，真实服务验证只覆盖上节列出的链路。

| 场景         | 主要证据                                                                                                                                                                                                                                      |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| S01–S07、S12 | SystemReportAuthorizationAdapterTest、JdbcReportProviderTest、ReportBusinessDatasetTest、QualityTraceDatasetProviderTest、ReportDefinitionSecurityTest、ReportRuntimeSecurityTest、ReportExportWorkerTest、控制器/异常测试；真实匿名/鉴权下载 |
| T01–T04      | ReportContractsTest、ReportExpressionTest、ReportCalculatorTest、ReportCompilerTest、ReportEngineRegressionTest、ReportExporterTest、前端精确值模型；真实快照与格式下载                                                                       |
| L01–L05      | DocumentLayoutTest、ReportEngineRegressionTest、ReportExporterTest；真实引擎三页合成 PDF 和浏览器纸张组件                                                                                                                                     |
| V01–V04      | ReportStoreTest、ReportStoreConcurrencyTest、ReportDefinitionSecurityTest、ReportRuntimeSecurityTest；新增 engine/schema/checksum/数据集引用保护及独立数据集 EXPORT/撤权回归                                                                  |
| E01–E06      | ReportRuntimeSecurityTest、ReportExportWorkerTest、ReportSnapshotStoreTest、ReportArtifactRecoveryTest、DAO 并发/清理确认、十万行 XLSX；真实持久队列与下载                                                                                    |
| U01–U02      | test-report-model/test-report-interactions（撤销重做、dirty、保存/发布/路由竞争）；合成实际页面交互                                                                                                                                           |

没有测量十万行导出的峰值 JVM、完整多租户压力、长时集群恢复或生产 SLA；结构/行为通过不等于这些指标已验收。G1 的积木迁移/双跑场景已按用户授权取消。

## 6. 运行与后续扩展

默认存储 ./data/report 由授权 API 代理读取，禁止公开挂载；Compose 新增私有持久目录。Alpine 的 font-wqy-zenhei 包安装和路径已实际核实，文泉驿 TrueType Collection 已由 PDFBox 成功嵌入中文 PDF；字体未复制进 Git。生产 Docker 镜像部署未执行。

当前是单节点私有文件存储；跨节点读取会检查存储标识并拒绝不匹配对象。共享/私有对象存储可通过 ReportArtifactStore 实现，完成后须做集群访问与清理验证。用户/租户准入由 canonical catalog 锁保护；跨租户全局队列限额仍为近似限额，不作为严格集群 SLA。

扩展采用独立 provider 契约版本、自有 schema/engine 版本、导出适配接口和私有存储接口。未知版本被明确拒绝，已发布 JSON 不被改写；后续新增旧引擎兼容执行器、查询目录/交叉表/仪表盘/订阅/分享按独立设计和验收推进。

使用方法和配置见 [模块使用指南](../guides/NexusERP_自研报表模块使用指南.md)。Git 提交包含本任务源码、测试、安装 SQL、指南、方案与合成样例；不包含临时日志、凭证、真实数据、目标构建目录，不推送远端。
