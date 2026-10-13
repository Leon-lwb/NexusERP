# Nexus-ERP 自研报表建设与验收路线

> 文档编号：NexusERP-REPORT-2026-003
> 版本：v1.0
> 状态：历史首版路线；2026-10-14 用户批准并调整，当前执行见 [首期实施计划](./2026-10-14-system-report-p0-implementation-plan.md)
> 创建 / 更新日期：2026-10-13
> 基线：`plus@c51e0f13`；方案分支 `codex/report-module-plan`
> 设计依据：[模块建设设计](../specs/2026-10-13-system-reporting-design.md)
> 调研依据：[源码与开源系统分析](../specs/2026-10-13-system-reporting-research.md)

**目标：** 建设由 Nexus 拥有模板格式、设计交互、取数规则和运行引擎的独立报表模块，直接清理未投入使用的积木资产。

**架构：** common-report 提供契约，report 实现自研平台与引擎，业务模块提供受控只读数据集；首期由 admin 组装。使用原生 Vue 3 设计器与服务器 RenderPlan，不把第三方完整报表引擎作为核心。

**技术基线：** Java 17 / 当前 Spring Boot 3、Sa-Token、MyBatis-Plus / MySQL 5.7、现有私有 OSS、Vue 3 / TypeScript / Element Plus；按阶段使用 FastExcel、POI、PDFBox 和 ECharts。

本路线用于确定范围、依赖与阶段验收。每个子项目在设计确认后再编写含具体实现代码的执行单；下面的文件和命令是未来落点，不表示文件、脚本或 API 已存在。本次没有建表、初始化服务或部署新模块。

## 1. 阶段与人员假设

以下为 2 名后端 + 2 名前端 + 1 名测试，业务负责人/产品约半人投入下的初步工作周估算；不把人员可用性、历史报表数量或上游兼容难度当作已确认事实。

| 阶段                   | 状态 | 建议周期 | 交付与退出条件                                                                                            |
| ---------------------- | ---- | -------- | --------------------------------------------------------------------------------------------------------- |
| G0：清单与技术探针     | ⬜   | 1–2 周   | 样本、权限口径、模块契约、分页/字体/导出与容量探针通过；冻结 P0 支持矩阵                                  |
| P0：可用核心           | ⬜   | 6–8 周   | TABLE + 单增长区 DOCUMENT、业务 provider、发布版本、细粒度权限、PDF/两类 XLSX、持久导出任务、首批业务报表 |
| G1：试点迁移           | ⬜   | 1–2 周   | 使用原生模板试点，记录业务签收；不做遗留双跑或转换                                                        |
| P1：查询目录与分析增强 | ⬜   | 4–6 周   | 受控查询目录、初步交叉汇总/横向展开、仪表盘、钻取和容量调优                                               |
| P2：扩展与整体退役     | ⬜   | 4–6 周起 | 经批准的复杂排版、订阅、分享/嵌入治理，剩余报表迁移与旧模块退出                                           |

首批试点预计 G0 + P0 + G1 共 8–12 周；后续完整范围为 16–24 周起。必须由 G0 的探针、资产清单和团队实际投入调整；复杂交叉报表或大量历史脚本重建可单独延长，不牺牲安全和正确性门槛压缩排期。

后端 A 主负责契约、数据集与权限，后端 B 主负责版本、引擎和导出；前端 A 主负责列表/目录/运行界面，前端 B 主负责打印设计与预览。测试从 G0 开始维护脱敏样本、权限矩阵和版式基准，而不是等功能完成后才加入。

## 2. G0 的具体产物

| 工作包              | 产物                                                                                             | 通过条件                                                                            |
| ------------------- | ------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------- |
| G0-1 实际资产清单   | 部署版本、报表 ID/名称/租户/负责人、入口、参数、数据源类别、脚本、频率、导出格式；凭证只记录引用 | 覆盖全部声明在用报表；每份有租户归属和负责人；无法确定的列明责任人及核实路径        |
| G0-2 升级故障归因   | 至少一组旧/新版本、构建依赖、库结构差异、脱敏日志和复现步骤                                      | 区分产品升级、数据库迁移、静态资源/路径、Token/权限、导出依赖；证据不足保持“未复现” |
| G0-3 首批样本       | 三类列表、两类单据、一类 MES 质量报告；空、单行、多页、长中文、精度边界的合成/脱敏数据           | 业务确定字段、分组与总计口径；声明哪些为 P0、哪些需 P1/P2                           |
| G0-4 安全取数探针   | 同一数据集在两租户、同租户两部门、撤权前后的结果；后台执行样本                                   | 明细/聚合/导出范围一致；无请求登录上下文的任务也正确隔离                            |
| G0-5 排版探针       | 固定字体、多页长文本、合并表头、页码/总页数、尾部汇总的预览与 PDF                                | 字段值、断行、页数和关键位置一致；超大行明确定义失败而非丢内容                      |
| G0-6 Excel/容量探针 | FastExcel/POI 依赖树、流式 10 万行、精确类型/前导零、安全文本、并发测试记录                      | 不冲突、不把文本写公式、内存受控；提出容量预算实测值                                |
| G0-7 冻结接口与 DSL | v1 schema、provider/authorization 契约、状态机、API、支持矩阵                                    | 对每项能力有明确输入、结果与拒绝行为；不支持项不可发布                              |

只在明确隔离、可恢复的开发/测试环境执行探针；读取现有部署信息与写测试数据的授权分别处理。不运行既有库的主初始化 SQL。G0 结果保存到新的阶段记录，不能用本研究文档充当探针已通过的证据。

## 3. P0 工作包与文件落点

以下路径均相对仓库根目录。公共契约、平台、业务适配和 UI 是独立子项目；每项先校验契约再实施，并用所列关键测试证明行为。

### P0-1 契约与模块组装

- 新增 `ruoyi-common/ruoyi-common-report/pom.xml`、`src/main/java/com/nexus/common/report/`。
- 契约文件：`ReportExecutionContext.java`、`ReportDatasetProvider.java`、`ReportAuthorizationPort.java`、`DatasetDescriptor.java`、`DatasetQuery.java`、`DatasetPage.java`、`TypedValue.java`、`ReportScope.java`。
- 新增 `ruoyi-modules/ruoyi-report/pom.xml` 和 `com/nexus/report/config/ReportConfiguration.java`、`config/ReportProviderRegistry.java`。
- 更新 common、modules、admin 与根 POM 的模块/依赖管理；保留旧 ruoyi-report。
- 验收：无 reporting ↔ 业务模块依赖环；provider 重名启动失败；缺失 provider 的模板不能发布。
- 测试落点：`ruoyi-modules/ruoyi-report/src/test/java/com/nexus/report/config/ReportProviderRegistryTest.java`；公共契约序列化测试使用 string ID / DECIMAL，不绕过全局 Long 规则。

### P0-2 元数据、发布与资源权限

- 新增 `com/nexus/report/domain/`、`domain/bo/`、`domain/vo/`、`mapper/`、`service/definition/`、`service/dataset/`、`service/security/`、`controller/`。
- 关键入口：`ReportDefinitionService.java`、`ReportDatasetService.java`、`ReportPublishService.java`、`ReportAccessGuard.java`、`ReportDefinitionController.java`。
- 建立 9 张 P0 核心表；新装 SQL 写入 `script/sql/ry-vue.sql`，迁移登记写入 `doc/system/数据库增量变更记录.md`；存量脚本另经基线/备份审查。
- 建菜单及 action 权限；菜单 ID 先核对当前分配，不沿用猜测的编号段；reporting 路由保持正常登录拦截。
- 验收：draftRevision 冲突 409；发布原子生成不可变版本；菜单权限与 ACL 同时生效；下线和回滚正确。
- 测试落点：`.../definition/ReportPublishServiceTest.java`、`.../security/ReportAccessGuardTest.java`、`.../controller/ReportDefinitionControllerTest.java`。

### P0-3 系统授权解析与可信业务数据集

- 新增 `ruoyi-modules/ruoyi-system/src/main/java/com/nexus/system/report/adapter/SystemReportAuthorizationAdapter.java`。
- 首批 ERP 适配器：`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/report/adapter/InventoryBalanceDatasetProvider.java`、`SalesExecutionDatasetProvider.java`、`PurchaseExecutionDatasetProvider.java`。
- 打印数据集：同一 ERP 适配目录新增 `PurchaseOrderDocumentDatasetProvider.java`、`DeliveryDocumentDatasetProvider.java`，按订单/送货单 ID 一次获取受权限约束的单据头与明细，不用列表汇总代替打印单据数据。
- MES 试点：`ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/report/adapter/QualityTraceDatasetProvider.java`。
- 每个 provider 定义参数、typed projection、业务权限和数据时间；必要时新增专用 reporting Mapper / XML。现有 Service 只在权限与查询模式完全匹配时复用。
- 平台文件：`com/nexus/report/query/ProviderDatasetExecutor.java`、`query/DatasetQueryGuard.java`；限制页大小、时间范围、排序列和结果规模。
- 验收：页、汇总、快照取数共享相同范围；后台不依赖 JWT；财务/库存公式仍由业务领域给出。
- 关键测试：每个业务模块的 `report/adapter/*DatasetProviderTest.java`；真实隔离测试须使用独立测试库验证 tenant + dept + field 三层结果，而不是仅检查 mock 被调用。

### P0-4 模板编译与精确计算

- 新增 `com/nexus/report/definition/ReportDsl.java`、`ReportDslValidator.java`、`compiler/ReportCompiler.java`、`compiler/CompiledReport.java`。
- 新增 `expression/ReportExpressionParser.java`、`expression/ReportExpressionEvaluator.java`、`expression/ReportDependencyGraph.java`。
- 实施 v1 schema 和支持矩阵；字段、参数、区域、合并与容量校验；BigDecimal、NULL、舍入和限定聚合作用域。
- 验收：未知字段/函数/版本、公式循环、除零、越界合并被定位并拒绝；汇总不因翻页改变；混币种/单位不能错误相加。
- 测试：`.../compiler/ReportCompilerTest.java`、`.../expression/ReportExpressionEvaluatorTest.java`、`.../expression/ReportDependencyGraphTest.java`；用精确结果断言，不使用浮点近似掩盖金额误差。

### P0-5 区域展开、测量与 RenderPlan

- 新增 `layout/RegionExpansionEngine.java`、`layout/TextMeasurer.java`、`layout/DocumentPaginator.java`、`renderer/RenderPlan.java`、`renderer/DocumentRenderer.java`。
- 先实现单个纵向增长区、静态合并、纸张尺寸、页眉页脚、重复表头、页码/总页数和尾部汇总；禁止动态区跨界合并。
- 使用同一批准字体、单位、断行与服务器坐标；图片仅支持批准的私有资源，不支持 URL 任意取图。
- 验收：0/1/多行、中文换行、边界页高和尾部汇总不漏行/重行；超高单行明确失败；同一数据结果稳定。
- 测试：`.../layout/RegionExpansionEngineTest.java`、`.../layout/DocumentPaginatorTest.java`、`.../layout/DocumentGoldenSampleTest.java`；黄金文件放本模块 `src/test/resources/report/golden/`，仅使用合成/脱敏资料。

### P0-6 运行快照、持久任务与输出

- 新增 `runtime/ReportRunService.java`、`runtime/ReportActorScopeResolver.java`、`runtime/ReportSnapshotStore.java`、`runtime/ReportTaskWorker.java`。
- 新增 `export/DataXlsxExporter.java`、`export/StyledXlsxExporter.java`、`export/PdfReportExporter.java`、`service/export/ReportExportService.java`。
- 实施 LIVE / SNAPSHOT、固定 runId、私有 OSS、数据库 lease、有界 executor、幂等、取消、超时、最多 3 次尝试及过期清理。
- 权限变化使旧快照/导出不可交付；下载走鉴权服务，不能只检查创建任务时的权限。
- 验收：节点中断可恢复，重复请求不会写两个有效文件；取消不与成功终态相互覆盖；大导出分块写入，ID/DECIMAL/文本类型正确；预览与 PDF 分页一致。
- 测试：`.../runtime/ReportTaskWorkerTest.java`、`.../runtime/ReportSnapshotScopeTest.java`、`.../export/ReportExportTypeTest.java`、`.../export/ReportExportGoldenTest.java`、`.../runtime/ReportTaskLeaseIntegrationTest.java`。

### P0-7 原生前端与设计器

- 新增 `plus-ui/src/api/report/{definition,dataset,runtime,export}.ts`、`src/types/report/index.ts`。
- 页面：`src/views/report/{center,designer,viewer,exports,history}/index.vue`。
- 组件：`src/components/report/{ParameterForm,ColumnEditor,DocumentGrid,PropertyPanel,ReportPagePreview}.vue`。
- 模型：`src/views/report/designer/reportEditorModel.ts`、`documentGridModel.ts`、`reportSerializer.ts`；编辑器内部状态与发布 DSL 分开。
- 打印设计支持区域/字段绑定、静态合并、样式、页设置、撤销/重做和保存冲突提示；列表设计使用列与参数配置。
- 页面支持受控样本、读取时间、版本、运行模式、任务进度与失败定位；不在产品界面暴露 SQL/凭证。
- 验收：无权限隐藏按钮且后端拒绝操作；Snowflake ID 不转 Number；DECIMAL 不做浏览器重算；保存→读取→再保存 DSL 无损；撤销不污染发布模型。
- 测试：在现有 esbuild + node --test 风格下新增 `plus-ui/scripts/test-reporting-model.mjs`、`test-reporting-serialization.mjs`，注册 `test:reporting`；补充 Playwright 或现有批准 E2E 驱动的实际设计/预览流程，不能用静态源码匹配代替交互验收。

### P0-8 首批报表、集成回归与业务验收

- 建立经过业务签收的首批模板和 provider，记录每份版本、字段、范围、数据时间与输出用途。
- 阶段验收报告放在 `doc/system/plans/`，按实际执行日期和仓库规范命名；不提前创建空验收报告。
- 验收：本路线第 6 节 P0 条目通过；业务核对真实业务口径与打印样本；当前 ERP/MES 主流程无回归。
- 未交付的交叉表/大屏/脚本归入 P1/P2；旧积木入口按 2026-10-14 授权删除。

## 4. 实施顺序与可并行工作

关键依赖为 G0 → P0-1 → P0-2/P0-3 → P0-4 → P0-5/P0-6 → P0-8。P0-7 可在契约与 DSL 冻结后，与后端按相同 schema 分头开发，并使用明确标注的合成样本进行联调。

权限、数据类型和版本是引擎的前置条件。先做表格跑通“取数→版本→运行→导出”的完整链路，再完成 DOCUMENT 排版；不能只交付设计画布截图作为核心能力证明。默认每周演示可运行的切片，测试持续验证已声明范围。

## 5. P1 / P2 扩展边界

| 工作包              | 具体范围与文件落点                                                                                                                                                   | 单独门槛                                                                              |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| P1-1 查询目录       | `query/catalog/EntityCatalog.java`、`CatalogQueryCompiler.java`、`QueryPolicyRewriter.java`、`query/jdbc/ReadonlyDatasetExecutor.java`；前端 `views/report/datasets` | 单库、每实体行列隔离、LEFT JOIN 语义、只读账户验证与攻击语料测试通过后开放            |
| P1-2 分析与布局增强 | `layout/HorizontalExpansionEngine.java`、`query/aggregation/PivotPlanner.java`；交叉汇总、受控钻取                                                                   | 声明最大维度/基数、排序/小计位置、稳定分页与两格式输出；unsupported 仍拒绝            |
| P1-3 仪表盘         | `views/report/dashboard`、`components/report/charts`；复用 ECharts 和数据集                                                                                          | 组件 schema、刷新限流、隐藏页暂停、相同 scope 缓存正确；服务器图表 PDF 未验证前不承诺 |
| P1-4 容量优化       | 索引、keyset、快照分块、线程/租户配额、明确缓存键与失效                                                                                                              | 使用 G0 同口径样本回归，并记录真实数据量/硬件/冷暖缓存                                |
| P2-1 复杂单据       | 嵌套主从、复杂分组/动态合并、跨页规则及额外格式                                                                                                                      | 每种布局写独立规格和黄金样本；避免“支持 Excel 全部公式”类无限范围                     |
| P2-2 订阅/分发      | `schedule/ReportSubscriptionJob.java`，现有 SnailJob；逐租户与逐接收人权限                                                                                           | 撤权/禁用账户立即阻断，收件人权限与生成者权限均校验；外部发送须经用户授权             |
| P2-3 分享/嵌入      | 单独的短期可撤销访问能力，资源、参数范围、次数与到期控制                                                                                                             | 不复用登录 token，不提供默认匿名业务数据；业务授权和审计明确                          |
| P2-4 全量迁移/退役  | 迁移映射、按份双跑、移除旧依赖与旧配置                                                                                                                               | 清单无未处理在用资产，或负责人明确接受归档；数据库清理另行授权                        |

AI 可在独立后续范围内辅助生成模板建议，产物仍经同一 DSL/字段权限校验；模型不能决定财务/制造 KPI、运行任意 SQL 或绕过发布流程。

## 6. 必测场景与明确断言

这些是未来验收场景，不是本次已运行测试。P0/P1 标签用于防止把后期功能混入首期完成声明。

| 编号 | 阶段 | 场景                                            | 预期断言                                              |
| ---- | ---- | ----------------------------------------------- | ----------------------------------------------------- |
| S01  | P0   | 无登录、无操作权限、无资源 ACL                  | 分别拒绝且无业务数据；认证/鉴权错误不输出 token       |
| S02  | P0   | tenant A 传入 tenant B 的报表/版本/运行/任务 ID | 元数据与下载均拒绝，查询次数为 0 或业务取数被阻断     |
| S03  | P0   | 同租户两个部门查看同报表                        | 明细、count、汇总、字典和导出均只含各自允许范围       |
| S04  | P0   | 参数伪造 actor、tenant、排序字段或 warehouse ID | 保留字段不可覆盖，未授权关联 ID 拒绝或无结果          |
| S05  | P0   | 队列内任务等待时用户被停用/撤权                 | 启动执行前拒绝，不生成新快照                          |
| S06  | P0   | 文件生成后权限缩小或字段分类变化                | 原文件不能下载；不只复核报表菜单权限                  |
| S07  | P0   | 池化线程先执行 A 租户再执行 B 租户              | 第二次上下文、元数据、数据和审计无 A 残留             |
| S08  | P1   | 值参数包含 SQL 引号/注释/注入载荷               | 作为参数字面值绑定，SQL 结构和过滤条件不变化          |
| S09  | P1   | 系统表、多语句、UNION、CTE、文件/锁定/函数读取  | 在执行前按 v1 查询支持矩阵拒绝                        |
| S10  | P1   | LEFT JOIN、无租户列子表的合法/非法归属          | 正确保留无匹配主行；不能映射归属的查询拒绝            |
| S11  | P1   | 同角色不同个人范围、撤权、跨租户缓存碰撞        | 缓存无越权命中；策略变更触发失效                      |
| S12  | P0   | 同一报表声明/导出敏感字段                       | 查询与聚合前校验字段权；日志无字段值明文              |
| T01  | P0   | 大于 JS 安全整数的 ID、前导零物料码             | API/模板/预览/Excel 全链路值精确                      |
| T02  | P0   | 0.1+0.2、舍入、金额大数、NULL 与零              | 按 BigDecimal/舍入规则精确计算，NULL 不隐式变零       |
| T03  | P0   | 页汇总与全量汇总、多币种/单位                   | 全量不依赖当前页；没有批准转换时不混合相加            |
| T04  | P0   | 公式循环、未知函数、除零、深度超限              | 发布/执行定位错误，不能默默忽略                       |
| L01  | P0   | 0 / 1 / 多行明细                                | 字段按共同记录对齐，不产生多余业务行                  |
| L02  | P0   | 多列字段、静态合并与下方固定区域                | 展开后无错位、覆盖、丢失或重复                        |
| L03  | P0   | 中文长文本、多行和页高临界点                    | 按服务器测量分页，预览/PDF 断行与页数相同             |
| L04  | P0   | 超高单行、跨增长区合并、缺字字体                | 明确失败并定位；不缩字/截断/丢内容                    |
| L05  | P0   | 重复表头、页码/总页数和末页汇总                 | 页码完整、汇总位置正确，不覆盖页脚                    |
| V01  | P0   | 两人保存同一 draftRevision                      | 一人成功、一人 409，已发布版本不变                    |
| V02  | P0   | 发布后修改数据集/模板，切回旧版                 | 已发布引用不变；新版本独立；兼容旧版可回滚            |
| V03  | P0   | 不支持 schema/provider 版本                     | 无隐式猜测或降级，拒绝并给出可定位错误                |
| V04  | P0   | 下线/租户失效与进行中任务                       | 阻止新执行和后续交付，审计保留原因                    |
| E01  | P0   | 预览后业务数据变化，再导出同 SNAPSHOT run       | 输出继续使用原快照；新的 LIVE 导出标注新运行时间      |
| E02  | P0   | XLSX 文本以 =/+/-/@ 开头、合法负数              | 字符串保持文本、无公式；合法负数仍为数值              |
| E03  | P0   | 格式 XLSX、10 万行流式 XLSX                     | 字段/样式在支持范围正确；超过精度的值为文本，内存受控 |
| E04  | P0   | 重复 idempotencyKey 或同 key 换请求             | 同指纹返回原任务，异指纹拒绝                          |
| E05  | P0   | worker 崩溃、lease 到期、完成/取消竞争          | 兼容节点可恢复；只产生一个有效交付；终态不互相覆盖    |
| E06  | P0   | 快照/文件过期、取消与容量超限                   | 返回明确状态，关闭查询/流；无泄漏或未鉴权下载         |
| U01  | P0   | 保存、读取、重新序列化、撤销/重做               | 语义无损，内部选区/历史不进 DSL                       |
| U02  | P0   | 实际拖字段/改版式/预览/发布/导出                | 完整交互通过，按钮权限与服务端结果一致                |
| M01  | G1   | 旧/新报表同静态样本与参数双跑                   | 关键业务字段、行数和总计完全一致；版式经业务签收      |
| M02  | G1   | 新入口故障恢复旧入口                            | 无数据清理或写回；已发布版本、映射和旧资产可追溯      |

P0 明确要求 S01–S07、S12、T01–T04、L01–L05、V01–V04、E01–E06、U01–U02，共 29 个场景；G1 的 2 个历史迁移场景已取消，P1 增加 S08–S11。场景通常包含多条测试，不将场景数当作实际自动化用例数。

Golden 校验同时比较结构化字段/分页坐标和 PDF 栅格图；不比较含时间/压缩随机因素的文件字节完全相等。对批准字体允许声明的小范围抗锯齿差异，业务值和页面结构不允许差异。

## 7. 未来实施后的验证命令

以下在相关模块/脚本已经创建后执行；**本次文档任务未运行这些命令**。测试必须核查用例数量、失败数和跳过原因，不能仅凭 BUILD SUCCESS。

```powershell
# 仓库根目录：平台及公共依赖
mvn -pl ruoyi-modules/ruoyi-report -am test "-DskipTests=false" "-Dprofiles.active="

# 仓库根目录：授权与业务适配器
mvn -pl ruoyi-modules/ruoyi-system,ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes -am test "-DskipTests=false" "-Dprofiles.active="

# plus-ui 目录：新增脚本注册后
npm run test:reporting
npm run test:unit
npm run type-check
npm run build:prod
```

MySQL/OSS 集成验证使用隔离环境，测试输出记录实际库版本、数据量、索引、应用硬件、读取窗口与权限。重导出验收测量 JVM/临时目录峰值与线程队列，验证任务超时真正终止取数。前端检查是完整 type-check，不用只检查变更文件的结果宣称全仓通过。

每个阶段报告至少给出 Git SHA、环境、实际命令、非零 Tests run/前端用例数、失败/跳过项、输出样本和未验证事项。若有历史既存失败，保留证据并区分引入情况，不改写为全绿。

## 8. 迁移执行门槛与回退

该历史迁移工作包已取消。用户在 2026-10-14 确认积木未投入使用，直接清理 Starter、鉴权桥、前端 iframe、菜单与显式旧表。当前执行和验证见首期实施记录，不建设遗留元数据转换或备份链路。

## 9. 风险、应对与停止条件

| 风险                                   | 早期验证 / 应对                                             | 不能继续宣称完成的条件                  |
| -------------------------------------- | ----------------------------------------------------------- | --------------------------------------- |
| 自研排版投入被低估                     | G0 真实多页样本，限定 1 个增长区，服务器字体与坐标固定      | 字段/分页不一致、长文本被截断           |
| 历史资产大量复杂脚本/交叉表            | P1/P2 单列；未使用的旧积木资产按授权清理                    | 清单没有责任人或不支持项未披露          |
| 业务 Service 只隔离租户而缺组织/列权限 | 每 provider 逐项审查、真实结果测试、显式 scope              | 聚合/导出出现超范围值                   |
| 新引擎升级重复第三方问题               | 独立 DSL、契约版本、依赖锁定、黄金样本、升级器              | 旧发布版被无声修改或无法解释运行差异    |
| 基础依赖/字体授权或兼容冲突            | G0 dependency tree、来源/版本核对、只引入需要的组件         | 未完成来源核对或破坏现有 PDF/Excel 模块 |
| 大导出挤占在线业务                     | 有界 executor/队列、租户配额、snapshot stream、私有对象清理 | SLA 不达标、任务不可取消、临时资源泄漏  |

## 10. 2026-10-13 方案阶段交付与实施状态

本节保留 2026-10-13 的方案阶段记录。该阶段产物为方案设计、源码调研、建设路线和文档索引。业务模块、9 张元数据表、上述 API/测试/脚本、迁移工具、性能 SLA 和生产替换均未实施或验证。

评审顺序建议先确认主路线 C 和 P0 支持范围，再通过 G0 的实际样本冻结契约、接口和容量。首期已于 2026-10-14 按用户授权实施，结果见首期验证记录；后期功能继续独立设计与验收。

本次实际验证：三份新增 Markdown 经过定向 Prettier 格式化与 `--check`，结果通过；`git diff --check` 对已跟踪索引改动通过。Node REPL 文档断言检查了 5 份文档、79 个相对链接、1 个 JSON DSL 示例、2 张 Mermaid 图的节点引用以及 29 / 4 / 2 个 P0 / P1 / G1 场景，结果通过。未运行 Java 测试、前端类型检查/构建或实际报表引擎测试，因为本次仅修改方案文档；这些检查仍列为后续实施验收要求。

格式检查使用仓库已有 Prettier：`node plus-ui/node_modules/prettier/bin/prettier.cjs --parser markdown --prose-wrap preserve --print-width 140 --end-of-line lf --check`，参数指定本次三份新增文档；未运行全仓自动格式化。新分支未提交或推送。
