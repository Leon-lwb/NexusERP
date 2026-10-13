# Nexus-ERP 报表源码与开源系统调研

> 文档编号：NexusERP-REPORT-2026-002
> 版本：v1.0
> 状态：2026-10-13 只读源码调研记录；首期运行验证另见 2026-10-14 记录
> 创建 / 更新日期：2026-10-13
> 仓库基线：`plus@c51e0f13`；方案分支 `codex/report-module-plan`
> 关联：[建设设计](./2026-10-13-system-reporting-design.md)、[建设路线](../plans/2026-10-13-system-reporting-construction-plan.md)

## 1. 方法、证据范围与限制

本次核对 Git 状态、近期提交、现有报表依赖与鉴权、相关配置的结构键、主安装 SQL 的表定义及已有业务报表代码；对用户提供的本地 `report` 参考目录只读分析，再查阅开源系统的官方源码和文档。

参考目录实际路径见会话；本文中的 AJ-Report 源文件路径均相对其根目录，不是 Nexus 仓库内的链接。未启动 AJ-Report、执行其部署脚本或初始化数据库，未连接生产数据库；没有统计真实活跃报表数或复现升级故障。主 SQL 中的报表示例不能作为生产使用证据。

外部网页与分支内容可能变化，记录查阅日期为 2026-10-13；只对实际读取内容作判断，不宣称各项目的“最新发行版”。本地 Git 快照具有明确 SHA，可复核。

## 2. Nexus 当前集成事实

| 编号 | 文件 / 入口                                                                                                                                                                                                                                                                                | 核实结果                                                                                                                                  |
| ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
| N1   | [ruoyi-report/pom.xml](../../../ruoyi-modules/ruoyi-report/pom.xml)                                                                                                                                                                                                                        | 同时依赖 `jimureport-spring-boot3-starter`、`jimubi-spring-boot3-starter`，固定 `2.3.2`                                                   |
| N2   | 历史 `JimuReportTokenService.java`（c51e0f13；2026-10-14 已删除）                                                                                                                                                                                                                          | 实现第三方 TokenService 接口；Sa-Token 登录校验、租户与用户解析、旧产品权限码映射，管理类权限已限管理员                                   |
| N3   | [application.yml](../../../ruoyi-admin/src/main/resources/application.yml)                                                                                                                                                                                                                 | 放行 `/jmreport/**`、`/drag/**`、`/jmreport-designer/**`、`/jmubi/**`，由第三方内部鉴权；租户排除表名以 `jm_report*` / `jm_drag*` 为主    |
| N4   | 开发、生产环境配置（仅核对键名，不摘录凭证）                                                                                                                                                                                                                                               | 动态数据源均声明 master / erp / mes / iot；未发现独立 report 数据源                                                                       |
| N5   | [ry-vue.sql](../../../script/sql/ry-vue.sql)                                                                                                                                                                                                                                               | 定义 `jimu_report`、`jimu_report_db`、`jimu_report_data_source`、`jimu_report_sheet`、导出和分享等表；命名与 N3 注释/排除清单并不完全一致 |
| N6   | [WmInventoryReportServiceImpl](../../../ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/report/service/WmInventoryReportServiceImpl.java)、[SoSalesReportServiceImpl](../../../ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/so/report/service/SoSalesReportServiceImpl.java) | 自有统计 Service 已存在，并使用 ERP 数据源；直接 SQL 路径须单独审查组织/字段权限，不能仅凭租户条件认定安全完整                            |
| N7   | [ReportDataServiceImpl](../../../ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/fin/report/service/impl/ReportDataServiceImpl.java)                                                                                                                                                   | 资产负债表、利润表、现金流等已有业务计算，使用 BigDecimal，应保留在财务领域                                                               |
| N8   | [PlusDataPermissionHandler](../../../ruoyi-common/ruoyi-common-mybatis/src/main/java/com/nexus/common/mybatis/handler/PlusDataPermissionHandler.java)                                                                                                                                      | 处理过程使用 LoginHelper / 数据权限上下文；脱离请求线程不自动恢复用户身份，直接 JDBC 也不经过此插件                                       |
| N9   | [plus-ui/package.json](../../../plus-ui/package.json)、[root pom](../../../pom.xml)                                                                                                                                                                                                        | 当前技术栈可支持原生 Vue 3 设计器和 PDFBox；common-excel 已使用 FastExcel                                                                 |

N5 中 `jimu_report`、`jimu_report_data_source` 等主表有 tenant_id，但 `jimu_report_db`、字段、参数、sheet、share 等子表没有该列。是否由旧产品正确继承主表归属，需要实际版本和运行验证；新模块不得照抄表结构后假定全链路多租户已成立。

当前 `plus-ui/src` 未检出积木特定页面/调用代码；系统有通用 InnerLink iframe 组件，菜单也可由数据库驱动。不能据此断言线上没有报表入口，G0 仍须对实际菜单和外链作清单。

## 3. 本地参考项目：AJ-Report

### 3.1 项目身份

| 项目            | 读取结果                                                                                                             |
| --------------- | -------------------------------------------------------------------------------------------------------------------- |
| 原始仓库        | `https://gitee.com/belief-team/report.git`                                                                           |
| 本地分支 / 状态 | `dev`；本次读取时工作区干净                                                                                          |
| HEAD            | `29286533997f9d41e08f36f959e5e1444e9c7d0d`，提交时间 `2026-08-13T02:40:25Z`，提交说明 update README.md               |
| 模块            | `report-core` / `report-ui`；根 POM 仅聚合 report-core                                                               |
| 后端            | `report-core/pom.xml` 为 Spring Boot 2.7.14，Gaea 2.9.4.RELEASE；包含 POI、iText 5 和 Groovy 依赖                    |
| 前端            | `report-ui/package.json` 为 Vue 2.7.16、Element UI、Vuex、Webpack 4；界面包 version 是 1.7.1，不作为整个仓库发布版号 |
| 根许可证        | LICENSE 为 Apache-2.0；NOTICE 有第三方组件说明，仍需逐项核对依赖许可证                                               |

### 3.2 数据集链路

读取入口：

- `report-core/src/main/java/com/anjiplus/template/gaea/business/modules/dataset/service/impl/DataSetServiceImpl.java`：`getData`（约 256 行）。
- `.../datasetparam/service/impl/DataSetParamServiceImpl.java`：`transform`（约 50 行）。
- `.../datasetparam/util/ParamsResolverHelper.java`：`resolveParams`（约 19 行）。
- `.../datasource/service/impl/DataSourceServiceImpl.java`：`executeRelationalDb`（约 229 行）。
- `.../datasource/service/impl/JdbcServiceImpl.java`：数据源 ID 到连接池的缓存。
- `.../datasettransform/service/impl/DataSetTransformServiceImpl.java`：转换策略注册与执行。

执行步骤为：取数据集定义 → 校验参数 → 替换动态语句 → 选择 JDBC / HTTP 等数据源 → 查询并转换成 JSON 行 → 依次执行数据转换。查询语句、参数定义、数据源和转换逻辑分别持久化，设计界面可复用数据集而不为每个图表重新开发接口。

这条链路值得借鉴的是“数据集作为复用单元”和明确的类型/参数目录。不能直接沿用的是参数执行机制：`ParamsResolverHelper` 进行 `${...}` 文本替换，之后 `executeRelationalDb` 用替换后的字符串构造 PreparedStatement；所查调用未使用参数 setXxx 绑定。使用 PreparedStatement 的类名本身不保证已完成安全绑定。本次未执行攻击验证，不推断该项目所有版本/所有路径的漏洞状态。

### 3.3 大屏设计链路

核心文件：

- `.../dashboard/service/impl/ReportDashboardServiceImpl.java`：`getDetail`、`insertDashboard`、`getChartData`。
- `.../dashboard/dao/entity/ReportDashboard.java`：尺寸、背景、刷新周期。
- `.../dashboardwidget/dao/entity/ReportDashboardWidget.java`：组件信息。
- `report-ui/src/views/bigscreenDesigner/designer/`、`screenDesigner/`：设计页面与组件。

大屏模型由页面尺寸/背景和若干组件组成，组件配置拆为 setup、data、position、collapse 和 options。组件绑定数据集，运行时查询并交给图表展示；布局与数据配置分别保存。这适合借鉴到原生 ECharts 仪表盘及组件 schema。

Nexus 中仪表盘不应与纸张报表共享一套自由坐标布局规则：仪表盘处理响应式大小与刷新，打印报表处理确定纸张、文本测量与分页。可以共享数据集、权限和类型，不强求编辑器与布局算法相同。

### 3.4 Excel 式报表链路

核心文件：

- `report-ui/src/views/excelreport/designer/index.vue`：字段拖入单元格，保存 Luckysheet `getAllSheets()` 返回值。
- `report-ui/src/views/excelreport/viewer/index.vue`：预览并交给 Luckysheet 显示。
- `.../reportexcel/service/impl/ReportExcelServiceImpl.java`：`analysisReportData`（约 501 行）、`analysisSheetCellData`（583 行）、`analysisCellData`（618 行）、`handleDynamicCellObject`（659 行）。
- `.../reportexcel/util/XlsUtil.java`；Service 中的 `exportExcel`、`convertExcelToPdf`。
- `doc/docs/guide/excel.md`：明确描述基于 Luckysheet 的简单集成及已知使用限制。

实现保存前端 sheet JSON，读取形如 `#{数据集.字段}` 的绑定；后端查询数据，复制模板单元格，更新行号与合并配置，再把展开后的 sheet JSON 给前端显示。`setMap` 在一次报表解析内避免重复获取同一数据集；`colAddCntMap` 记录各列展开偏移。

关键学习点是“模板 → 数据 → 展开结果”三个对象，而难点在多个字段的共同主记录、空数据、动态合并、下方静态内容位移和分页。独立按列维护偏移会增加复杂数据关联的推理成本，新引擎改以明确的明细区域展开。该结论是本方案的设计取舍，不是运行测试得到的 AJ-Report 故障结论。

当前预览 JSON 与 Luckysheet 格式耦合；PDF 路径从 Excel 转换，导出还接收客户端传入的行样式信息。Nexus 应保存自有 DSL，通过服务器计算 RenderPlan，逐格式适配，不把编辑组件完整内部格式当作长期发布契约。

## 4. 官方开源系统对照

| 系统与来源                | 已核实的核心机制                                                                    | 对 Nexus 的启发                                      | 采用边界                                    |
| ------------------------- | ----------------------------------------------------------------------------------- | ---------------------------------------------------- | ------------------------------------------- |
| AJ-Report（本地源码；S1） | 数据源、数据集、组件配置；Excel 模板 JSON 后端展开                                  | 数据集复用、组件 schema、一次运行内取数复用          | 参考原理，单独实现；不搬用旧认证/执行栈     |
| UReport2（S2、S3）        | ReportBuilder 将设计与参数/数据集结合，再展开行列、计算单元格；计算与分页是引擎责任 | 增长区、依赖关系、分页和中间结果应独立于编辑器       | P0 纵向区域，后续扩展；不直接承接任意表达式 |
| JasperReports（S4、S5）   | 设计模板编译、数据填充为 JasperPrint、按 exporter 输出；7.0.0 有破坏性兼容变化      | 发布模板和运行结果分离、导出适配；升级须维护兼容契约 | 借鉴结构，不作为核心运行依赖                |
| Eclipse BIRT（S6）        | ReportDesign、ReportDocument、执行任务与格式渲染任务分开，支持参数和数据配置        | 把执行取数与渲染分开，运行快照可复用                 | 借鉴生命周期，不引入完整平台                |
| Apache Superset（S7）     | 数据集与角色/行级规则；官方安全文档指出平台不能替代数据库防护                       | 目录、查询权限、缓存范围、只读账号应同时设计         | 借鉴分析/安全治理；不能替代单据排版引擎     |

比较覆盖在线大屏、单元格报表、传统打印引擎和分析平台。它们关注点不同，不能以“支持拖拽”作为完整 ERP 报表能力的判断标准。

### 4.1 为什么替换另一种引擎仍不能解决全部升级问题

JasperReports 官方变更记录说明 7.0.0 对序列化后的编译模板和部分旧模板解析存在兼容变化。成熟开源引擎也需要升级迁移。自研的价值是项目能控制自身契约和升级节奏；它仍需锁定基础依赖、维护 schema 迁移器和回归样本。

Nexus 应维护“引擎支持的 schema 范围 + provider 契约 + 发布模板版本 + 数据快照版本”，避免前端组件升级、底层库升级或 SQL 变更直接影响所有已发布报表。

## 5. 基础组件与许可证边界

| 组件                   | 建议用途                          | 调研/实施边界                                                                     |
| ---------------------- | --------------------------------- | --------------------------------------------------------------------------------- |
| 已有 ECharts           | P1 原生图表展示                   | 不将浏览器图表能力自动等同于服务器 PDF 图表能力                                   |
| 已有 FastExcel         | 明细流式 XLSX                     | 需要验证数值类型、分块写入、容量与临时文件行为                                    |
| POI                    | 受支持的网格、静态合并和样式 XLSX | 通过 root POM 管理并确认与 FastExcel 传递依赖一致                                 |
| 已有 PDFBox            | PDF 输出、字体/图像基础操作       | 不自带完整报表布局器，分页、断行与表格由项目实现（S8）                            |
| 已有 JSQLParser 依赖链 | 受控查询结构验证                  | 不替代只读权限、参数绑定与每实体策略；兼容性在 P1 探针验证                        |
| 本地 AJ 使用的 iText 5 | 本方案不引入                      | 官方说明 iText 5 使用 AGPL / 商业双许可（S9）；根项目 Apache 许可不能替代依赖核对 |
| 中文字体、图片资源     | 批准资源后供测量与导出            | 分开核对字体/资源授权及嵌入条件，固定版本与校验和                                 |

本次不导入外部代码或新增依赖。如果实施选择复用开源代码，应逐文件记录来源、版本、改动及相应 LICENSE / NOTICE；第三方许可判断以对应版本原文为准。原创实现遵循 Nexus 当前仓库许可证，不能通过删除第三方声明把复用源码变成原创。

## 6. 核心问题与建设决策的对应关系

| 核心问题                     | 方案的解决机制                                          |
| ---------------------------- | ------------------------------------------------------- |
| 模板被前端或第三方升级牵动   | 自有 DSL、不可变发布版本、schema 升级器、黄金样本       |
| 数据集跨租户/组织取数        | 显式上下文、业务适配器、每实体策略、拒绝默认全量        |
| 异步线程失去权限             | 重新解析 actor 权限；后台 provider 接收显式 scope       |
| 单元格扩展/合并相互干扰      | 明细区域统一展开；分阶段开放复杂规则                    |
| 浏览器与 PDF 分页不同        | 服务器测量、固定字体、共享 RenderPlan                   |
| 预览与导出数据变化           | SNAPSHOT runId 与读取时间，LIVE 模式明确标注            |
| 巨量导出拖垮 Web 线程        | 私有快照、有界任务、分块流式处理、超时与容量预算        |
| 旧报表转换不完整             | 支持矩阵、人工重建清单、静态样本双跑、逐份切换          |
| 元数据和业务模块产生循环依赖 | common-report 仅契约，业务模块实现 provider，admin 组装 |

## 7. 来源索引

以下均为项目官方仓库、官方文档或库提供方资料。URL 放在代码中便于复核；外部分支链接需以读取日期理解，本地 AJ 的 SHA 已记录。

| 编号 | 原始资料                                                                                                                                                                                                      |
| ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| S1   | AJ-Report 原始仓库：`https://gitee.com/belief-team/report`；主要证据为第 3 节本地 HEAD 下列出的源码                                                                                                           |
| S2   | UReport2 README：`https://raw.githubusercontent.com/youseries/ureport/master/README.md`                                                                                                                       |
| S3   | UReport2 ReportBuilder：`https://raw.githubusercontent.com/youseries/ureport/master/ureport2-core/src/main/java/com/bstek/ureport/build/ReportBuilder.java`                                                   |
| S4   | JasperReports README：`https://raw.githubusercontent.com/Jaspersoft/jasperreports/master/README.md`；填充 API：`https://jasperreports.sourceforge.net/api/net/sf/jasperreports/engine/JasperFillManager.html` |
| S5   | JasperReports 官方变更记录：`https://raw.githubusercontent.com/Jaspersoft/jasperreports/master/changes.txt`，关注 7.0.0 的 Breaking changes                                                                   |
| S6   | BIRT Report Engine API：`https://eclipse-birt.github.io/birt-website/docs/integrating/reapi/`                                                                                                                 |
| S7   | Superset 安全文档源码：`https://raw.githubusercontent.com/apache/superset/master/docs/admin_docs/security/security.mdx`                                                                                       |
| S8   | PDFBox 官方功能说明：`https://pdfbox.apache.org/`；基础内容输出：`https://pdfbox.apache.org/3.0/getting-started.html`                                                                                         |
| S9   | iText 5 许可说明：`https://itextpdf.com/how-buy/AGPLv3-license`                                                                                                                                               |

使用过的只读命令包括 `git status --short --branch`、`git log`、参考目录的 `git remote get-url origin` / `git log -1`、定向 `rg` 和文件结构读取。豆包搜索 skill 脚本因 `AGENT_PLAN_URL` 未定义运行失败，改用可用网页工具读取上述官方资料；未修改搜索技能或要求用户提供凭证。
