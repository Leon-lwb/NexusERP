# 业务单据设计与打印优化验证记录

日期：2026-10-17；分支：`plus`；基线提交：`7b885892`。代码交付以本地 `plus` 分支 Git 历史为准；提交不代表部署或生产验收。实施范围见[计划](2026-10-17-report-business-print-plan.md)。

## 交付结果

- 原生设计器拆出纸张网格、毫米纸张设置、固定区域矩形操作与私有图库；A4/A5、自定义尺寸、方向、列宽守恒、行高、媒体边界及一次手势一次历史共用独立命令。
- 新增销售订单和采购收货单固定provider/lookup与基础模板，原数据集及发布契约保留。代码目录为16个固定数据集、19类基础模板；本轮没有向现有库写入新增模板。
- 四类业务列表提供行内单张与工具栏多选入口；批次参数改为固定发布版本表单，保留顺序、字符串ID、创建/入队分阶段重试及原任务重试。
- 单张PDF使用当前run；合并PDF复用已有逐单任务，重新检查创建者、租户、报表/业务/资产权限和有效期，保留纸型与单据内页码，私有物化、PDFBox缓存与输出受配额并在交付结束回收。
- 新接口为`GET /report/batches/{id}/pdf`；模板追加`kind/versionId`，批次项追加重试轮次、可重试标志和过期时间，无新增物理表。

## 实际验证

| 命令或检查                                                                                                                             | 结果                                                                                 |
| -------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| `mvn -pl "ruoyi-modules/ruoyi-report,ruoyi-modules/ruoyi-erp" -am test "-DskipTests=false" "-Dprofiles.active=" "-Dstyle.color=never"` | BUILD SUCCESS，5分58秒；15个实际执行测试的模块共6328项，失败/错误/跳过均0            |
| `ruoyi-erp` Maven测试汇总                                                                                                              | 4974项通过，0失败、0错误、0跳过                                                      |
| `ruoyi-report` Maven测试汇总                                                                                                           | 391项通过，0失败、0错误、0跳过                                                       |
| `npm run test:report`                                                                                                                  | 182项通过，0失败、0跳过；原142项基线及新增40项全部执行                               |
| `npm run type-check`                                                                                                                   | 通过，vue-tsc无错误                                                                  |
| `npm run build:prod`                                                                                                                   | 通过，Vite约1分15秒；保留原有Sass导入弃用、stream外置、静态/动态导入混用及大分包提示 |
| 本次修改的Vue/TS/MJS定向ESLint                                                                                                         | 通过                                                                                 |
| 本次修改前端文件的定向Prettier检查                                                                                                     | 通过                                                                                 |
| `git diff --check`                                                                                                                     | 通过                                                                                 |

### 本地提交前复核

2026-10-17 再次执行 `npm run test:report`，182项通过，0失败、0跳过；再次执行以下后端定向命令，ERP模块10项、报表模块67项，共77项通过，0失败、0错误、0跳过，BUILD SUCCESS，耗时39.862秒：

```powershell
mvn -pl "ruoyi-modules/ruoyi-report,ruoyi-modules/ruoyi-erp" -am test "-DskipTests=false" "-Dprofiles.active=" "-Dtest=ErpDocument*Test,ReportBusinessDatasetTest,ReportLookupProviderTest,ReportDocumentTemplateTest,ReportBatch*Test,ReportPrivatePdfCacheTest,ReportDefinitionServiceTest,ReportDefinitionControllerTest,ReportRuntimeControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dstyle.color=never"
```

本次复核沿用上述已完成全量验证的实现，仅同步提交说明和验证文档；没有重新执行类型检查、生产构建或全量Maven测试。提交前再次检查 `git diff --check`；用户既有的工程演示导入计划和结果文档不纳入本次提交。

新Java回归覆盖主子表租户/删除条件、业务范围、金额字段、必填参数、精确数量/ID、lookup及增量bootstrap历史保留；批次覆盖重复与并发code、顺序、混合纸型、损坏/空PDF、撤权/过期、大小/页数/期限、共享物化和真实H2硬配额、断连回收。S3路径使用共享物化语义的隔离适配器，未连接真实桶。

新增前端回归覆盖网格命令、布局手势、固定版本、输入/路由/账号切换、权限与owner、创建后只补start、PDF复用及Blob URL回收。按TDD观察过失败后修正的集成问题包括：原UUID不符合批次资源编码格式、创建run期间模板发布版本变化、拆分后旧锚点扩大Shift选区，以及工具栏焦点阻断Escape取消。

## 隔离浏览器证据

使用仓库实际Vue、Element Plus和本次设计组件，通过独立Vite与Edge CDP运行合成夹具；没有调用业务后端或写真实数据库。桌面1440×1100、窄屏390×1000及深色模式无页面水平溢出，控制台无脚本异常。实际指针验证列宽守恒、松开一次提交、工具栏有焦点时Escape取消，以及私有图片选择和媒体拖动。最终[机器记录](../assets/report-business-print/browser-verification.json)与截图均标记为合成数据。

- [桌面网格](../assets/report-business-print/paper-grid-desktop-synthetic.png)
- [私有图库](../assets/report-business-print/asset-picker-desktop-synthetic.png)
- [媒体定位](../assets/report-business-print/paper-grid-media-synthetic.png)
- [深色网格](../assets/report-business-print/paper-grid-dark-synthetic.png)
- [窄屏网格](../assets/report-business-print/paper-grid-mobile-synthetic.png)

临时夹具在`plus-ui/node_modules/.tmp/report-print-browser/`，执行`node node_modules/.tmp/report-print-browser/verify.mjs`进行浏览器检查；该命令依赖任务启动的隔离Vite和Edge实例。端口18089的预览服务和18298的隔离浏览器均已关闭，未停止用户服务。仓库自动回归不依赖该临时夹具。

## 审查与边界

后端独立静态审查未发现确定新增缺陷；设计器审查发现的锚点和Escape问题已修复，并完成组件与真实Edge复验。源代码与测试中未新增真实密钥、URL令牌或业务备份。

本记录是代码、仓库回归与隔离合成浏览器证据；没有执行现有数据库初始化/迁移、真实S3连接、连接真实业务库的跨页面端到端联调、生产发布或真实打印机验收。新增模板通过既有管理员初始化入口显式增量创建，既有同编码定义及用户修改保持不变。合并及流式交付的60秒预算在检查点执行，单次底层阻塞由存储和服务器超时控制。

日志保留在本机临时目录，前缀为`nexus-report-print-`：`full-maven.log`、`frontend-tests.log`、`type-check.log`、`build.log`；不将完整测试日志复制到仓库。

## 改动文件

下列清单仅列本轮交付；开始时已存在的两份演示导入计划/结果文件未改动。

- `doc/system/assets/report-business-print/asset-picker-desktop-synthetic.png`
- `doc/system/assets/report-business-print/browser-verification.json`
- `doc/system/assets/report-business-print/paper-grid-dark-synthetic.png`
- `doc/system/assets/report-business-print/paper-grid-desktop-synthetic.png`
- `doc/system/assets/report-business-print/paper-grid-media-synthetic.png`
- `doc/system/assets/report-business-print/paper-grid-mobile-synthetic.png`
- `doc/system/guides/NexusERP_自研报表模块使用指南.md`
- `doc/system/guides/报表基础模板目录与筛选口径.md`
- `doc/system/plans/2026-10-17-report-business-print-plan.md`
- `doc/system/plans/2026-10-17-report-business-print-verification.md`
- `plus-ui/package.json`
- `plus-ui/scripts/test-report-business-print.mjs`
- `plus-ui/scripts/test-report-grid-interactions.mjs`
- `plus-ui/scripts/test-report-grid.mjs`
- `plus-ui/scripts/test-report-interactions.mjs`
- `plus-ui/src/api/report/assets.ts`
- `plus-ui/src/api/report/batch.ts`
- `plus-ui/src/api/report/index.ts`
- `plus-ui/src/components/report/BusinessBatchDialog.vue`
- `plus-ui/src/components/report/BusinessReportButton.vue`
- `plus-ui/src/components/report/DesignerBatchTools.vue`
- `plus-ui/src/components/report/PrintSettings.vue`
- `plus-ui/src/components/report/ReportPaper.vue`
- `plus-ui/src/components/report/ReportPdfPreview.vue`
- `plus-ui/src/components/report/designer/AssetPicker.vue`
- `plus-ui/src/components/report/designer/PaperGrid.vue`
- `plus-ui/src/components/report/designer/PaperSettings.vue`
- `plus-ui/src/components/report/useBatchSubmission.ts`
- `plus-ui/src/views/erp/pu/order/index.vue`
- `plus-ui/src/views/erp/so/orderheader/index.vue`
- `plus-ui/src/views/erp/wm/receive/index.vue`
- `plus-ui/src/views/erp/wm/sales-issue/index.vue`
- `plus-ui/src/views/report/batch/index.vue`
- `plus-ui/src/views/report/designer/gridCommands.ts`
- `plus-ui/src/views/report/designer/index.vue`
- `plus-ui/src/views/report/model.ts`
- `plus-ui/src/views/report/viewer/index.vue`
- `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/report/adapter/ErpReportLookupProvider.java`
- `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/report/adapter/PurchaseReceiveDocumentDatasetProvider.java`
- `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/report/adapter/SalesOrderDocumentDatasetProvider.java`
- `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/report/adapter/ErpDocumentDatasetTest.java`
- `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/report/adapter/ErpDocumentLookupTest.java`
- `ruoyi-modules/ruoyi-report/src/main/java/com/nexus/report/batch/ReportBatchPdfService.java`
- `ruoyi-modules/ruoyi-report/src/main/java/com/nexus/report/batch/ReportBatchService.java`
- `ruoyi-modules/ruoyi-report/src/main/java/com/nexus/report/batch/ReportPrivatePdfCache.java`
- `ruoyi-modules/ruoyi-report/src/main/java/com/nexus/report/config/ReportConfiguration.java`
- `ruoyi-modules/ruoyi-report/src/main/java/com/nexus/report/config/ReportProperties.java`
- `ruoyi-modules/ruoyi-report/src/main/java/com/nexus/report/controller/ReportBatchController.java`
- `ruoyi-modules/ruoyi-report/src/main/java/com/nexus/report/service/ReportDefinitionService.java`
- `ruoyi-modules/ruoyi-report/src/main/java/com/nexus/report/service/ReportTemplates.java`
- `ruoyi-modules/ruoyi-report/src/test/java/com/nexus/report/batch/ReportBatchArtifacts.java`
- `ruoyi-modules/ruoyi-report/src/test/java/com/nexus/report/batch/ReportBatchCompatibilityTest.java`
- `ruoyi-modules/ruoyi-report/src/test/java/com/nexus/report/batch/ReportBatchFixture.java`
- `ruoyi-modules/ruoyi-report/src/test/java/com/nexus/report/batch/ReportBatchPdfQuotaTest.java`
- `ruoyi-modules/ruoyi-report/src/test/java/com/nexus/report/batch/ReportBatchPdfTest.java`
- `ruoyi-modules/ruoyi-report/src/test/java/com/nexus/report/batch/ReportPrivatePdfCacheTest.java`
- `ruoyi-modules/ruoyi-report/src/test/java/com/nexus/report/controller/ReportBatchControllerTest.java`
- `ruoyi-modules/ruoyi-report/src/test/java/com/nexus/report/service/ReportDocumentTemplateTest.java`
