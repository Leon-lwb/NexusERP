# 报表代码与界面操作优化验证记录

日期：2026-10-17；分支：`plus`；基线：`e365bb1a`。本记录对应[实施计划](./2026-10-17-system-report-ui-code-optimization-plan.md)的P1–P6。源码、文档及合成证据的交付以本地Git历史为准，未推送远程；提交不代表数据库初始化、部署或真实打印机验收。

## 实施结果

- 把完整预览页拆成运行、打印逻辑和共用内容；四类业务列表在原页抽屉预览，完整页通过既有run状态接口重新鉴权并复用同一运行。抽屉显式安装共用报表样式作用域，保证主题变量和分区布局一致。
- 业务批量确认展示单号、往来单位和完整标识；提交后原位显示逐项结果。关闭重开保留code、已知batchId和首次冻结内容，创建后失败只补入队；恢复沿用原任务轮次与有效期。
- 设计器增加统一逻辑选区、上下文工具栏、媒体属性、专注编辑和窄屏侧栏；0.5/5mm键盘移动、一次连续动作一次历史，Escape及输入法按各自语义处理。
- 服务端追加可选 `expectedVersionId`，在provider查询、运行和快照创建前检查；保留旧请求构造及服务重载。增加固定图片版本元数据接口，仅输出授权资源名称、标识和该版本像素尺寸。
- 报表范围内解析JSON及Blob错误，权限变化清除旧结果，过期阻止合并领取；安全投影服务端问题项序号，不把内部错误消息直接显示给用户。
- 浏览器夹具和CDP驱动纳入版本管理，生成物放忽略目录；测试仅使用独立回环服务、合成API和任务自有浏览器配置。

## 后端全量验证

在仓库根执行：

```powershell
mvn -pl "ruoyi-modules/ruoyi-report,ruoyi-modules/ruoyi-erp" -am test "-DskipTests=false" "-Dprofiles.active=" "-Dstyle.color=never"
```

BUILD SUCCESS；耗时5分10秒，2026-10-17 19:20:31（Asia/Shanghai）结束。按Surefire模块汇总实际执行6354项，0失败、0错误、0跳过；15个模块执行非零测试：

| 模块                      | 实际用例数 | 失败 / 错误 / 跳过 |
| ------------------------- | ---------: | ------------------ |
| ruoyi-common-satoken      |         28 | 0 / 0 / 0          |
| ruoyi-common-sse          |          4 | 0 / 0 / 0          |
| ruoyi-common-tenant       |          8 | 0 / 0 / 0          |
| ruoyi-common-web          |         46 | 0 / 0 / 0          |
| ruoyi-common-ai           |        190 | 0 / 0 / 0          |
| runtime-attestation-agent |         32 | 0 / 0 / 0          |
| ruoyi-common-websocket    |          9 | 0 / 0 / 0          |
| ruoyi-common-sign         |        224 | 0 / 0 / 0          |
| ruoyi-common-report       |         16 | 0 / 0 / 0          |
| ruoyi-system              |        196 | 0 / 0 / 0          |
| ruoyi-common-flowable     |         21 | 0 / 0 / 0          |
| ruoyi-flowable            |        174 | 0 / 0 / 0          |
| ruoyi-common-cv           |         15 | 0 / 0 / 0          |
| ruoyi-erp                 |       4974 | 0 / 0 / 0          |
| ruoyi-report              |        417 | 0 / 0 / 0          |

新增版本及元数据边界先以定向测试观察失败，再实现；定向命令实际执行33项并全部通过：

```powershell
mvn -pl ruoyi-modules/ruoyi-report -am test "-DskipTests=false" "-Dprofiles.active=" "-Dtest=ReportRuntimeExpectedVersionTest,ReportRuntimeControllerTest,ReportAssetTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dstyle.color=never"
```

其中预期版本17项、运行控制器8项、私有资产8项。覆盖LIVE/SNAPSHOT版本冲突无查询及无运行/快照副作用、版本检查后的固定绑定、旧调用兼容、身份/租户/资源权限、空白版本、固定图片旧版本、字段白名单、下线及版本篡改。后端原有PDF页数、文本、条码、混合纸型及私有存储回收测试继续执行。

## 前端脚本、类型与构建

| 命令 / 检查                                  | 实际结果                                                   |
| -------------------------------------------- | ---------------------------------------------------------- |
| `npm run test:report`                        | 最后一轮228项通过，0失败、0跳过；保留原182项基线，新增46项 |
| `npm run test:report:browser`                | 默认all模式36项通过，0失败、0跳过；页面及Mock边界异常均0   |
| `npm run type-check`                         | 通过，vue-tsc无错误                                        |
| `npm run build:prod`                         | 最后一轮通过，Vite耗时1分20秒                              |
| 父代理修改的Vue/TS/MJS定向ESLint             | 通过                                                       |
| 设计器11个文件定向ESLint、Prettier及模板编译 | 通过                                                       |

构建保留既有Sass导入弃用、stream浏览器外置、静态/动态导入混用和大分包提示。它们没有导致本轮构建失败，也没有借此改造无关模块。

新增交互回归先复现了版本未传、完整页重复取数、业务入口离开列表、已完成批次重开丢失、同身份权限撤销时接受迟到结果、创建返回后仍继续入队，以及合并错误丢失定位等问题，再逐项修正。测试使用真实Vue响应式、组件脚本、网格命令和请求围栏，仅替换网络、身份及下载边界。

## 浏览器与独立审查

通过既有Node v24.21.0、隔离Edge及真实Vue/Element Plus组件执行 `npm run test:report:browser`，默认all模式36项通过，0失败、0跳过。覆盖桌面1440×1100、窄屏390×1000、深色模式、真实指针与键盘、固定版本图片、业务抽屉、顺序调整、网络重试、JSON/410文件、已缓存PDF撤权回收和专注模式切换。脚本异常、未处理拒绝、意外网络及页面横向溢出保持严格检查。

最后的同模板闭环实际点击编辑及“保存并发布”：保存与发布各一次，revision由1到2再到3，发布产生新不可变版本；原发布快照保持不变。随后模板目录、单张run及两项批次均使用该新版本，单张和批次分别领取带PDF签名的合成Blob，全部关闭后Blob URL残留为0。该链路验证UI协议与交付生命周期，真实PDF生成、分页、文本、条码及合并仍由后端回归独立验证。

截图等待字体、图片、动画、加载层与布局/计算颜色稳定；深色可见卡片验证背景和文字对比，抽屉验证共享主题变量及标题flex排版。稳定截图不会靠禁用动画或过滤错误放行。精选证据：

- [桌面设计器](../assets/report-ui-optimization/designer-desktop-synthetic.png)
- [390px深色画面](../assets/report-ui-optimization/designer-mobile-dark-synthetic.png)
- [业务列表内单张预览](../assets/report-ui-optimization/business-single-drawer-synthetic.png)
- [批次确认与提交顺序](../assets/report-ui-optimization/batch-confirm-reordered-synthetic.png)
- [BLOCKED后回收PDF与遮罩标签](../assets/report-ui-optimization/batch-blocked-pdf-revoked-synthetic.png)
- [同模板保存发布](../assets/report-ui-optimization/designer-saved-published-synthetic.png)
- [场景、版本、稳定样式及清理摘要](../assets/report-ui-optimization/browser-verification.json)

最后一轮浏览器PID已退出，Vite/CDP端口均关闭，profile及运行目录已移除，待处理CDP命令0；独立审计确认自有浏览器、运行目录及端口监听残留均0。原始运行文件保留在忽略目录，永久摘要移除机器绝对路径。演示导入文档和用户现有浏览器/服务没有被修改或清理。

两项独立审查共复现8个问题，已补回归并修复，复核确认全部闭合：

- 合并领取资格贯穿文件组件生命周期，BLOCKED刷新会回收旧URL、移除iframe、清空Blob并阻止下载；结果和返回确认都遮罩受影响单据的原标签。
- 未知模板契约加载期间更换参数，只允许新上下文创建运行；同身份撤销预览权限清空批次详情并拒绝迟到回包。
- 入队、轮询、重试、普通导出和PDF下载确认ACCESS失效时，运行方清除原数据、排版、模板和参数。单独撤销导出权限也会停止在途后续动作，恢复时保留原幂等键。
- 原批次入口在当前勾选为空或超过50项时仍可恢复冻结提交；新建仍检查1至50份。
- 数字控件步进立即提交，Enter/change/重复blur去重；Escape让展开控件先处理，活动画布候选仍优先取消。

复核证据包括19个状态探针、3组实际组件脚本与模板串联断言，以及6项使用实际ElInputNumber/ElSelect状态和事件链的回归。缓存串联创建URL3次、回收3次，撤权后下载0次；这些是隔离内存验证，不冒充浏览器或真实后端联调。

实际浏览器还暴露了专注模式切换时的ResizeObserver循环通知：画布在观察回调中同步更新缩放，导致同一帧再次改变尺寸。已改为仅响应可用宽度变化、下一帧合并适应操作，手动缩放及卸载取消候选帧；新增回归先观察同步写布局失败，再确认延迟合并、仅高度通知不重排及卸载回收全部通过。保留浏览器异常的严格失败检查。

## 环境与兼容边界

- 没有写现有数据库、执行初始化SQL或bootstrap、操作真实S3或打印机，没有启停用户服务。
- 固定数据集与基础模板仍为16个及19类，本轮不新增模板、物理表、DSL或引擎版本；schema1/2及历史发布保持兼容。
- 单张打印复用当前run，批次合并继续使用逐单固定快照及已有PDF；1–50份、1000页、60秒及120秒文件超时沿用原约束。
- 新增图片属性接口不提供存储引用、路径、checksum、令牌或外链；新增预期版本字段省略时保留旧行为。
- 已有工程演示导入计划和结果文档保持原样，不纳入本轮文件范围；浏览器生成目录及构建产物不纳入源码交付。

## 最终检查与文件清单

最终定向ESLint检查47个Vue/TS/MJS文件；Prettier检查54个实际修改的前端、文档、JSON和HTML文件，`git diff --check`通过。以下67个文件构成本轮交付，原有两份工程演示导入文档明确排除：

- `doc/system/assets/report-ui-optimization/batch-blocked-pdf-revoked-synthetic.png`
- `doc/system/assets/report-ui-optimization/batch-confirm-reordered-synthetic.png`
- `doc/system/assets/report-ui-optimization/browser-verification.json`
- `doc/system/assets/report-ui-optimization/business-single-drawer-synthetic.png`
- `doc/system/assets/report-ui-optimization/designer-desktop-synthetic.png`
- `doc/system/assets/report-ui-optimization/designer-mobile-dark-synthetic.png`
- `doc/system/assets/report-ui-optimization/designer-saved-published-synthetic.png`
- `doc/system/guides/NexusERP_自研报表模块使用指南.md`
- `doc/system/plans/2026-10-17-system-report-ui-code-optimization-plan.md`
- `doc/system/plans/2026-10-17-system-report-ui-code-optimization-verification.md`
- `plus-ui/package.json`
- `plus-ui/scripts/report-browser/api.mjs`
- `plus-ui/scripts/report-browser/App.vue`
- `plus-ui/scripts/report-browser/business-api.mjs`
- `plus-ui/scripts/report-browser/business-scenarios.mjs`
- `plus-ui/scripts/report-browser/cdp.mjs`
- `plus-ui/scripts/report-browser/designer-scenarios.mjs`
- `plus-ui/scripts/report-browser/index.html`
- `plus-ui/scripts/report-browser/lifecycle-scenario.mjs`
- `plus-ui/scripts/report-browser/main.ts`
- `plus-ui/scripts/report-browser/README.md`
- `plus-ui/scripts/report-browser/scenarios.mjs`
- `plus-ui/scripts/report-browser/server.mjs`
- `plus-ui/scripts/report-browser/user.mjs`
- `plus-ui/scripts/report-browser/visual.mjs`
- `plus-ui/scripts/test-report-browser.mjs`
- `plus-ui/scripts/test-report-business-print.mjs`
- `plus-ui/scripts/test-report-grid-interactions.mjs`
- `plus-ui/scripts/test-report-grid.mjs`
- `plus-ui/scripts/test-report-interactions.mjs`
- `plus-ui/scripts/test-report-ui-state.mjs`
- `plus-ui/src/api/report/assets.ts`
- `plus-ui/src/api/report/errors.ts`
- `plus-ui/src/api/report/index.ts`
- `plus-ui/src/components/report/batch/BatchResultPanel.vue`
- `plus-ui/src/components/report/batch/useBatchResult.ts`
- `plus-ui/src/components/report/BusinessBatchDialog.vue`
- `plus-ui/src/components/report/businessDocument.ts`
- `plus-ui/src/components/report/BusinessReportButton.vue`
- `plus-ui/src/components/report/BusinessReportDrawer.vue`
- `plus-ui/src/components/report/designer/AssetPicker.vue`
- `plus-ui/src/components/report/designer/DesignerToolbar.vue`
- `plus-ui/src/components/report/designer/editorSelection.ts`
- `plus-ui/src/components/report/designer/FieldPalette.vue`
- `plus-ui/src/components/report/designer/PaperGrid.vue`
- `plus-ui/src/components/report/designer/SelectionInspector.vue`
- `plus-ui/src/components/report/PrintSettings.vue`
- `plus-ui/src/components/report/ReportPdfPreview.vue`
- `plus-ui/src/components/report/useBatchSubmission.ts`
- `plus-ui/src/components/report/viewer/ReportViewerPanel.vue`
- `plus-ui/src/components/report/viewer/useReportPrint.ts`
- `plus-ui/src/components/report/viewer/useReportRun.ts`
- `plus-ui/src/views/erp/pu/order/index.vue`
- `plus-ui/src/views/erp/so/orderheader/index.vue`
- `plus-ui/src/views/erp/wm/receive/index.vue`
- `plus-ui/src/views/erp/wm/sales-issue/index.vue`
- `plus-ui/src/views/report/batch/index.vue`
- `plus-ui/src/views/report/designer/gridCommands.ts`
- `plus-ui/src/views/report/designer/index.vue`
- `plus-ui/src/views/report/viewer/index.vue`
- `ruoyi-modules/ruoyi-report/src/main/java/com/nexus/report/asset/ReportAssetService.java`
- `ruoyi-modules/ruoyi-report/src/main/java/com/nexus/report/controller/ReportAssetController.java`
- `ruoyi-modules/ruoyi-report/src/main/java/com/nexus/report/controller/ReportRuntimeController.java`
- `ruoyi-modules/ruoyi-report/src/main/java/com/nexus/report/runtime/ReportRuntimeService.java`
- `ruoyi-modules/ruoyi-report/src/test/java/com/nexus/report/asset/ReportAssetTest.java`
- `ruoyi-modules/ruoyi-report/src/test/java/com/nexus/report/controller/ReportRuntimeControllerTest.java`
- `ruoyi-modules/ruoyi-report/src/test/java/com/nexus/report/runtime/ReportRuntimeExpectedVersionTest.java`
