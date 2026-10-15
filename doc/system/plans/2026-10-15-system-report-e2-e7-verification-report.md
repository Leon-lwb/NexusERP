# 自研报表 E2–E7 实施与验证报告

> 日期：2026-10-15；分支：codex/report-module-plan；实施前基线：bed171aed3816e709cc51b7ddd8c7e0882e92981。
> 用户授权：E2至E7代码开发、此前及新增报表代码中文Javadoc/JSDoc、Git提交；允许核实当前项目后停止/重启后端。模块名继续ruoyi-report。
> 结论：C0及E2–E7实现、原生页面、固定版本边界、隔离回归与应用装配完成。**现有开发库/生产库未执行E2–E7增量，真实通知未发送，现场打印与生产集群验收未完成。**

## 1. 实施结果

| 范围        | 已完成行为                                                                                                                                                                | 关键实现                                                                                                                                           |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| C0 中文文档 | 覆盖此次报表相关185个Java源文件，具名类型/方法/字段/记录组件均使用中文Javadoc；记录使用类型@param，方法补参数和返回值；TS/Vue/API/交互测试使用中文JSDoc，转换已有英文说明 | common-report、report、ERP/MES/System适配器及报表前端；AST最终审计3306个具名声明，缺失0                                                            |
| E2 受控查询 | 代码固定逻辑目录、批准关系、类型参数、实体各自业务范围、敏感筛选范围；固定查询发布生成CQ数据集；自然日DATE，聚合维度原值；独立SELECT-only源默认关闭                       | common/report/catalog、erp/report/catalog、report/query及dataset；规划语义1.0.0写入固定依赖和范围指纹                                              |
| E3 交叉分析 | schema2/engine2.0.0与v1并存；精确SUM/COUNT/COUNT_ROWS/MIN/MAX/AVG、空维度、小计、分区总计、精确TopN其他桶；原始快照服务端坐标钻取                                         | pivot、V2ReportEngineExecutor、PivotTable；200列/20000单元格在展开前阻断；总计作为共享投影数据行进入明细XLSX/PDF/样式XLSX                          |
| E4 打印     | PNG/JPEG解码前限额、归一化私有版本、ACL/checksum；QR/Code128批准字段绑定；横向、分组、精确小计、重复页脚；每批最多50固定单据且逐份鉴权                                    | asset、print、batch、RenderPlan.media；原始业务行绑定固定区；自动扫码解码/实际PDF和XLSX媒体测试                                                    |
| E5 仪表盘   | 指标/柱/线/饼/表/交叉组件1，固定报告发布、参数映射、个人收藏；服务器精确指标/变化率和受控投影；逐组件错误/时点/一致性；暂停及隐藏停刷                                     | dashboard、DashboardWidget及原生页面；清除联动废弃旧回包；全局/租户/用户主库刷新额度                                                               |
| E6 存储治理 | LOCAL默认及S3逻辑storeId、应用代理流/受限物化、真实checksum；主库硬配额/公平执行；快照分块索引与Range；按租户checksum字体共享、租约和计划引用                             | storage、ReportSnapshotIndexService、ReportFontResourceStore；确定暂存路径先登记后创建、DELETE_PENDING先提交、永久物理引用围栏、ADOPTING可恢复纳管 |
| E7 自动交付 | 固定订阅版本与接收人、cron/ZoneId/DST/补跑、整体occurrence幂等、逐人outbox/作业/交付、失败重试与不可复活取消、当前权限领取                                                | subscription、SnailJob适配、独立maintenance、system通知适配及common端口；默认调度/真实通知关闭                                                     |
| 资源授权    | REPORT/DATASET及QUERY/DASHBOARD/ASSET/BATCH/SUBSCRIPTION统一弹窗和服务端校验                                                                                              | AclDialog及五类扩展资源页面                                                                                                                        |

## 2. 关键回归修复

按失败用例复现后修复，并在最终受影响模块全量重跑：

- 查询：父表敏感范围继承到行实体；聚合仅保留原值维度别名；DATE在选择、过滤、排序、分组统一cast；离线查询不阻塞无关目录。
- 交叉：TopN排序不使用显示舍入值；每列/整行总计进入统一导出；容量检查在构造单元格前生效，避免先生成超限矩阵。
- 打印/仪表盘：固定区域始终绑定原始业务行；多个汇总分区各自显示；页脚不盖页码；最后返回前再验全部成功组件；不同单位/币种比较拒绝混算；清除联动和卸载页面拒绝迟到副作用。
- 配额/恢复：实际文件mtime触发的foreign暂存删除被阻断；部分创建失败/清理失败保留配额；预留后强杀已具确定路径；迟到暂存分配和业务提交都受物理封存拒绝；慢物理IO不持GLOBAL锁。
- 删除：数据库持久DELETE_PENDING在物理删除前提交，断联不会回滚为可pin；Local跨进程文件锁和永久封存、S3条件创建与零字节retired封存阻止迟到PUT。桶版本/自动生命周期拒绝启用，围栏元数据必须保留。
- 纳管：陈旧inventory和预留后二次HEAD封存不复活旧对象；ADOPTING网络中断可在重启完成；未登记回收与纳管在同一清单键竞争，删除失败保留容量且下轮重试。
- 订阅：日期默认值在最终区间校验前按计划时点冻结；跨午夜补跑不按恢复当天提前拒绝；手动键重放不能扩充接收人；取消与prepare同锁且失败补偿不能覆盖终态；关通知不关调度或访问；满200失败记录不阻塞后续；管理员代发布指纹仍属于原创建者。

E2、E3–E5、E6、E7均经过独立只读代码审查及修复复核；最终已确认的阻断均关闭。审查不代替部署、长时负载或人工验收。

## 3. 最终验证

| 验证               | 命令/方式                                                                                                                                                                                                                       | 最终结果                                                                                          |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| 受影响后端55测试类 | mvn -pl ruoyi-modules/ruoyi-report,ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-system -am test -DskipTests=false -Dprofiles.active= -Dtest=（55个实际报告类名） -Dsurefire.failIfNoSpecifiedTests=false | **340项通过、0失败、0错误、0跳过**；common-report9、system3、ERP3、MES1、report324；BUILD SUCCESS |
| 真实S3协议与双JVM  | mvn -pl ruoyi-modules/ruoyi-report -am test -DskipTests=false -Dprofiles.active= -Dtest=ReportS3IntegrationIT -Dsurefire.failIfNoSpecifiedTests=false -Dreport.test.minio.env=（忽略目录合成凭证文件）                          | **3项通过、0失败、0跳过**；实际SDK/MinIO私有桶、封存拒写、伪封存校验、双进程强杀重启              |
| 报表前端           | npm run test:report                                                                                                                                                                                                             | **95项通过、0失败、0跳过**，含8项真实Vue扩展交互                                                  |
| 全前端             | npm run test:unit                                                                                                                                                                                                               | **1850项通过、0失败**；包含报表专项，不与95重复相加                                               |
| 类型               | npm run type-check                                                                                                                                                                                                              | vue-tsc --noEmit退出0                                                                             |
| 定向样式检查       | 对45个修改的前端文件运行Prettier及ESLint                                                                                                                                                                                        | 检查通过；未批量格式化仓库无关文件                                                                |
| 前端产物           | npm run build:prod                                                                                                                                                                                                              | Vite生产构建成功；存在既有chunk体积提示，不作为失败                                               |
| 应用装配           | mvn -pl ruoyi-admin -am package -DskipTests=true                                                                                                                                                                                | 最终BUILD SUCCESS；此命令跳测，不计测试证据                                                       |
| 打包一致性         | 比较最终admin嵌套report JAR与target/classes五个关键实现class字节                                                                                                                                                                | 全部一致，覆盖配额、包装器、本地存储、订阅、聚合                                                  |
| 未登录鉴权         | 最终8080七个报表接口HTTP探针                                                                                                                                                                                                    | HTTP200业务code401，无data；沿用项目响应约定                                                      |
| 浏览器             | 真实Vue组件+ElementPlus/ECharts，隔离合成API                                                                                                                                                                                    | 查询精确预览、透视总计/钻取、仪表盘、订阅编辑/执行、交付目录核验；截图见第5节                     |

运行后端测试的精确选择器来自本轮185源文件中的55个实际Test类；额外真实S3集成类显式执行，未用零用例BUILD SUCCESS代替测试。首轮全回归曾暴露旧Mock未适配Materialized生命周期及围栏目录改变扫描预算，已修复并重跑；下载测试新增物化期间撤权且仍释放资源的回归。

### 精确后端回归选择器

本轮实际命令的55类选择器如下，可在仓库根目录复现（Maven与其他Maven任务保持串行）：

```powershell
$reportTests = 'ReportAssetTest,ReportBatchTest,ReportDefinitionControllerTest,ReportExceptionHandlerTest,ReportRuntimeControllerTest,ReportDashboardTest,ReportWidgetCalculationsTest,DocumentLayoutTest,ReportCalculatorTest,ReportCompilerTest,ReportEngineRegressionTest,ReportExpressionTest,ReportExporterTest,ReportExportWorkerTest,ReportPrivateSpreadsheetTempTest,ReportStoreConcurrencyTest,ReportStoreTest,ReportPivotTest,ReportGroupedPrintTest,ReportPrintMediaTest,ReportCatalogExecutionTest,ReportQueryDateTest,ReportQueryPlannerTest,ReportQueryVersionTest,ReportDispatchFairnessTest,ReportFontReuseTest,ReportJobProgressTest,ReportPivotRuntimeTest,ReportQueryDatasetRuntimeTest,ReportRenderPlanRuntimeTest,ReportRenderPlanStoreTest,ReportRuntimeMetricsTest,ReportRuntimeParameterPrecisionTest,ReportRuntimeSecurityTest,ReportRuntimeServiceTest,ReportSnapshotIndexTest,ReportSnapshotStoreTest,ReportDatasetVersionTest,ReportDefinitionSecurityTest,ReportDefinitionServiceTest,ReportLookupSecurityTest,ReportTemplatePackageTest,PrivateArtifactStoreTest,ReportArtifactRecoveryTest,ReportManifestStoreTest,ReportQuotaTest,ReportSubscriptionTest,ReportSubscriptionTimeTest,ReportEngineCompatibilityTest,JdbcReportProviderTest,ReportContractsTest,ReportBusinessDatasetTest,ReportLookupProviderTest,QualityTraceDatasetProviderTest,SystemReportAuthorizationAdapterTest'
mvn -pl ruoyi-modules/ruoyi-report,ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-system -am test '-DskipTests=false' '-Dprofiles.active=' "-Dtest=$reportTests" '-Dsurefire.failIfNoSpecifiedTests=false'
```

真实S3类另需专用隔离MinIO及合成凭证文件，不应指向现有业务桶。完整前端单测包含95项报表专项，统计不重复相加。

## 4. 隔离双进程证据与实际限制

专用MinIO只绑定127.0.0.1:19000，测试桶随机生成。H2通过隔离TCP主库供两个独立Java进程访问；凭证只存忽略文件、未输出或提交。

1. JVM A使用配额包装器写入1600行typed精确快照，数量样例9007199254740993.005，登记共享字体和故意残留的本机暂存。
2. 完成登记后强杀A，实际调旧暂存文件mtime与清单租约。
3. JVM B从同一S3逻辑store读取第1501行开始的30行，使用真实Range偏移96173、精确值保持，字体按checksum复用；B不会删除A暂存。
4. JVM A原节点重启，读取同一快照并回收本节点过期暂存；字体资源1、索引1、暂存清单0，物化关闭后文件消失。

最终单次合成样本测量：写入约781ms、范围读取/字体复用约628ms。它不是吞吐承诺、SLA或生产峰值RSS测试。未专门执行MySQL会话强制断连；断联围栏结论来自持久状态时序审查及暂停旧尝试/物理拒写测试。生产S3供应商、跨机文件锁、真实MySQL5.7 DDL、长时负载和节点时钟偏差仍需在目标环境验证。

永久围栏元数据：Local.fences为哈希命名锁/1字节标志，S3.retired为零字节对象；不属于可下载业务对象，不纳入业务产物对象额度。必须保留，不得由外部生命周期清理；对象服务本身的元数据费用与长期压缩策略是后续运维范围。

## 5. 原生界面截图

全部截图顶部标注“隔离合成数据”，使用合成ID和数值，不含真实业务数据。

- [受控查询](../assets/report-e27/query-synthetic.png)
- [交叉矩阵](../assets/report-e27/pivot-synthetic.png) 与 [同快照钻取](../assets/report-e27/pivot-drill-synthetic.png)
- [仪表盘](../assets/report-e27/dashboard-synthetic.png)
- [订阅编辑及执行](../assets/report-e27/subscription-synthetic.png)
- [私有交付](../assets/report-e27/delivery-synthetic.png)

## 6. SQL、服务和开关

- [E2–E7增量](../../../script/sql/update/2026-10-15-report-e2-e7.sql)：25张扩展表，核心九表保留，总34张；菜单23030–23090及操作权限，原生根23000不变。新安装ry-vue.sql仅追加当前扩展块，已逐字节验证原94,886,909字节前缀不变。
- **未对现有ry-vue/ry-erp/ry-mes执行该增量或任何业务写入**。H2只安装隔离结构，不等于目标MySQL部署；当前开发库P0/E0/E1历史基线仍有效。目标库安装必须先核对环境与执行授权，不能重导含DROP的主安装SQL。
- query独立只读源默认关闭，storage.manifestEnabled默认false，subscription.schedulingEnabled/notificationsEnabled默认false；S3必须启用清单、专用私有桶及固定storageId。现有核心九表环境可启动，未自动创建扩展表、模板或通知。
- 打包首次被旧运行JAR文件锁阻止后，核实launcher16752/JVM31028属于当前workspace，按用户授权停止；最终JAR按原启动参数恢复。现端口8080 JVM33296，Started NexusApplication，启动核查ERROR0、扩展缺表错误0。前端80原进程11624/16700保持。本轮合成预览18087及专用MinIO容器已清理，仅保留正常前后端服务。
- 最终七个鉴权探针仅匿名GET，不获取token、不进行登录或写库。完整E2–E7登录后真实库联调需要先部署扩展结构，本轮浏览器使用隔离合成响应，后端服务链路使用H2/MinIO隔离测试。

## 7. 扩展接缝与未验收范围

固定QueryModel/catalog/planner版本、schema/engine注册、provider与授权SPI、组件版本、RenderPlan格式1/2、ArtifactStore流/确定路径/物理围栏、通知port均保留明确升级点。后续可按独立需求增加实体目录、图表组件、多个增长区域、横向分页、批次合并、对象围栏元数据压缩及外部受限分享。

本轮不宣称生产部署、真实定时消息已送达、现场打印/扫码验收、跨数据库同一时点账务或完整共享存储长期SLA。待部署项见[使用指南](../guides/NexusERP_自研报表模块使用指南.md)和[数据库登记](../数据库增量变更记录.md)。
