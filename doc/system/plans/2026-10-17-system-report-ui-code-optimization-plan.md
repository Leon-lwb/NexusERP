# 自研报表下一阶段代码与界面操作优化实施计划

> 执行提示：后续实施使用 `executing-plans` 按工作包推进，先补行为回归，再修改实现。本文件记录拟实施方案；当前任务只提交一期成果和本计划，不启动下一阶段开发。

**状态：** 待审，全部工作包尚未实施。

**日期：** 2026-10-17。

**代码基线：** `plus` 分支，`47180947`（业务单据设计与批量PDF打印）。

**Goal：** 减少设计和打印的操作切换，使四类业务单据在原列表内完成预览、批量确认、进度查看和打印，并使前端交互回归可在干净检出中复现。

**Architecture：** 延续现有报表引擎、服务端 `RenderPlan`、固定发布版本、逐单快照、私有资产和导出队列；抽取页面内可复用的内容组件及请求状态逻辑，以现有页面和业务抽屉共同承载。版本检查前移到运行创建前，资产属性仅增加受控元数据投影。

**Tech Stack：** Java/Maven、Vue 3/TypeScript、Element Plus、Vite、Node测试脚本和本机Edge/Chrome CDP；继续使用现有依赖。

## 1. 现场依据与方案选择

本计划基于实际代码及[一期验证记录](./2026-10-17-report-business-print-verification.md)，不把上一阶段的本地验证视为生产部署或真实打印机验收。

| 当前代码事实                                                                          | 下一阶段改进                                                 |
| ------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| `viewer/index.vue`、`batch/index.vue` 各约600行，混合路由、参数、请求、轮询及文件交付 | 按运行、打印、批次结果拆分逻辑；完整页和抽屉复用相同内容组件 |
| 前端在运行创建后检查发布版本；服务端 `RunRequest` 没有预期版本字段                    | 在调用provider及创建run之前检查 `expectedVersionId`          |
| `PaperGrid.vue` 内部维护媒体选中项，父级属性面板只识别单元格和列                      | 统一编辑选区，支持直接编辑图片和条码属性                     |
| 设计器顶栏及网格工具栏同时显示大量操作                                                | 保留常用主操作，按当前选中对象显示编辑工具                   |
| 业务入口跳转完整预览页，批量确认主要展示完整ID                                        | 原列表打开预览抽屉，确认时优先展示单号和往来单位             |
| 固定图片重新打开后名称退化为通用提示                                                  | 根据固定版本读取受控元数据，区分正常、不可访问和下线         |
| 一期实际浏览器夹具位于忽略目录 `plus-ui/node_modules/.tmp/report-print-browser/`      | 将夹具和驱动移入受版本管理的测试目录，生成物继续隔离         |

业务操作承载方式比较：

| 方案                             | 收益与代价                                         | 选择               |
| -------------------------------- | -------------------------------------------------- | ------------------ |
| 仅优化现有完整页面               | 改动较少，但用户仍需离开业务列表                   | 保留为完整页入口   |
| 抽取共用内容，增加列表内预览抽屉 | 保留勾选及筛选上下文，页面与抽屉共享权限和请求逻辑 | 本计划采用         |
| 重建独立打印向导或新版设计器     | 需要重复建设状态与权限处理，回归范围明显扩大       | 留在本阶段范围之外 |

## 2. 交互默认方案

### 设计器

- 顶栏常驻返回、撤销/重做、预览、保存草稿、发布；校验和模板包操作收进“更多”。发布仍走既有保存、校验和权限流程。
- 桌面保持“字段 / 纸张画布 / 属性”结构，选中媒体后右侧直接出现位置、尺寸、绑定字段或固定图片版本。批量样式工具只在有效固定区选区出现。
- 提供应用内“专注编辑”模式以扩大画布，不依赖浏览器全屏权限。预览可与画布并排；缩放、选区、面板状态均属于编辑器状态。
- 小于1024px时字段与属性改为抽屉，主操作分组折行；纸张在画布容器内部滚动，不扩大整个页面宽度。390px窄屏仍可保存、预览、切换对象属性。
- 媒体方向键移动步长为0.5mm，Shift为5mm；输入框、选择器、可编辑区域和输入法组合输入不触发画布快捷键。沿用二维码正方形及页面边界约束。
- Escape优先取消当前拖动或键盘移动，再关闭当前侧栏，最后退出专注编辑；退出编辑页继续使用已有未保存提示。

### 业务列表与打印

- 单张入口先选可访问的 `DOCUMENT` 模板，在抽屉内自动带入单据参数并预览；提供“打开完整页”。打开完整页优先通过既有run状态接口复用当前运行，并重新鉴权。
- 批量入口在抽屉内完成模板选择、顺序调整、提交和进度展示。成功后在同一位置显示合并PDF操作；单项失败显示对应单据及恢复按钮。
- 确认表默认显示“顺序 / 单号 / 客户或供应商 / 状态 / 操作”，完整字符串ID作为辅助信息保留；缺少展示字段时回退为ID。
- 关闭抽屉停止本地轮询并回收Blob，不取消后台任务。已创建的批次可以通过现有批次页重新打开；网络状态未确认的提交保留原code、规范化内容和已知batchId，重开后先恢复原提交。
- 加载、无模板、无数据、权限不足、版本变化、任务过期均有明确状态。仅在当前状态允许时展示重试操作。

## 3. 接口与状态边界

### 3.1 运行创建的预期版本

在现有 `POST /report/runtime/runs` 请求中追加可选字段，旧客户端保持兼容：

```ts
/** 明确当前界面加载的发布版本，省略时沿用旧调用语义。 */
interface ReportRunRequest {
    reportId: string;
    mode: string;
    parameters: Record<string, string>;
    expectedVersionId?: string;
}
```

服务端保留旧 `create` 重载和 `RunRequest` 三参数构造入口，新重载先解析当前身份及可访问发布版本，再执行：

```java
if (expectedVersionId != null && expectedVersionId.isBlank()) {
    throw ReportFault.invalid("预期发布版本不能为空");
}
if (expectedVersionId != null && !expectedVersionId.equals(version.id())) {
    throw new ReportFault("REPORT_VERSION_CHANGED", "模板发布版本已变化，请重新选择模板", 409);
}
return createBound(current, version, requestedMode, input);
```

版本不匹配必须在provider查询、快照创建和run落库之前终止，返回现有 `{ code, msg, errorCode }` 格式。检查通过后固定该次解析得到的不可变版本；后续发布不替换本次运行的版本。浏览器不自动切到新版本后重试，防止用户确认的模板发生变化。

### 3.2 固定图片版本元数据

新增 `GET /report/assets/versions/{id}/metadata`，返回 `R<ReportAssetMetadata>`：

```ts
/** 名称为当前授权资源标题，尺寸及标识对应请求的固定版本。 */
interface ReportAssetMetadata {
    versionId: string;
    resourceId: string;
    name: string;
    width: number;
    height: number;
}
```

复用当前身份解析、`report:runtime:preview`、资源 `VIEW` ACL、租户和下线检查；宽高从该版本读取，不回退到最新版本。响应设置私有禁止缓存，不返回存储引用、路径、checksum、令牌或外链。尺寸单位明确为图片像素，画布位置和打印尺寸继续显示毫米。无权限或已下线的绑定保持原版本，并显示不可访问状态。

### 3.3 前端状态与业务展示

```ts
/** 业务列表提供的展示信息，不参与服务端授权及参数推导。 */
interface BusinessDocumentRef {
    id: string;
    code: string;
    counterparty?: string;
}
```

`BusinessReportButton` 增加可选 `document`、`documents` 展示属性，保留原 `recordId/recordIds`；匹配完整ID后才使用展示信息，参数仍从原标识构造。批次内容继续只提交固定版本、参数、code和顺序，不信任客户端单号或往来单位。

`RequestGate` 继续阻止迟到响应；新抽取逻辑必须把报表、版本、参数、账号、租户及组件激活状态纳入请求有效性。抽屉和完整页都通过既有 `GET /report/runtime/runs/{id}` 重新读取run状态；run与加载模板版本不符时展示冲突，不套用当前DSL或自动重新取数。

## 4. 实施工作包

### P1：将浏览器回归变为可重复执行的命令

**修改文件：** `plus-ui/package.json`。

**新增文件：** `plus-ui/scripts/test-report-browser.mjs`、`plus-ui/scripts/report-browser/server.mjs`、`cdp.mjs`、`api.mjs`、`index.html`、`main.ts`、`App.vue`。后六个文件位于同一个 `report-browser/` 目录。

- [ ] 从一期隔离夹具迁出合成数据、Vite入口和CDP驱动；先固化现有设计器、图库、拖动、Escape及窄屏场景。
- [ ] 增加 `test:report:browser` 命令：`node scripts/test-report-browser.mjs`。动态分配回环端口，只启动任务自有Vite及浏览器进程；Windows后台进程隐藏窗口。
- [ ] 支持 `REPORT_TEST_BROWSER` 指定浏览器可执行文件，默认探测本机Edge/Chrome。当前工具验证环境为Node v24.21.0；启动时检查原生WebSocket能力及浏览器，缺失时明确非零退出，不把未执行写成跳过或通过。前端现有Node基线保持不变。
- [ ] Mock API验证真实资源code格式、字符串ID、固定版本和权限规则；版本不符返回409。添加脚本异常、未处理Promise拒绝、意外真实网络请求及页面横向溢出的失败检查。
- [ ] 所有生成物写入既有忽略目录 `node_modules/.tmp/report-browser/`；成功、断言失败及中断都清理本次PID、端口和临时浏览器配置。禁止关闭用户浏览器或服务。

**验证：** 在清空任务自有生成物后运行 `npm run test:report:browser`，实际执行非零浏览器场景；故意传入不存在的浏览器路径时返回失败并给出诊断。新检出不依赖一期忽略目录。

### P2：前移版本检查并提供安全图片元数据

**修改后端：** `ReportRuntimeController.java`、`ReportRuntimeService.java`、`ReportAssetController.java`、`ReportAssetService.java`，分别位于现有 `controller/`、`runtime/`、`asset/` 包。

**修改前端：** `plus-ui/src/api/report/index.ts`、`assets.ts`；新增 `plus-ui/src/api/report/errors.ts`。

**测试：** 新增 `ruoyi-modules/ruoyi-report/src/test/java/com/nexus/report/runtime/ReportRuntimeExpectedVersionTest.java`，扩展既有 `controller/ReportRuntimeControllerTest.java` 和 `asset/ReportAssetTest.java`。

- [ ] 先写版本不符的失败用例，断言HTTP409、`REPORT_VERSION_CHANGED`、provider零调用、没有新run和快照；再覆盖版本一致、字段省略、空串及跨租户情况。
- [ ] 按3.1实现可选版本检查；Java旧重载和旧三参数构造仍可用，TS `createRun` 增加第四个可选参数。
- [ ] 先写元数据权限及字段白名单测试，再实现3.2；覆盖旧固定版本、撤权、下线、跨租户，断言序列化结果不包含内部存储字段。
- [ ] 在报表API范围内解析已有失败信封，包括Blob请求返回的JSON错误；保留Axios原始错误与全局认证逻辑，不改写全站请求行为。无法解析时显示通用错误并保留原操作上下文。

**验证：** 在仓库根执行：

```powershell
mvn -pl ruoyi-modules/ruoyi-report -am test "-DskipTests=false" "-Dprofiles.active=" "-Dtest=ReportRuntimeExpectedVersionTest,ReportRuntimeControllerTest,ReportAssetTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dstyle.color=never"
```

新增失败用例先观察到预期失败，再实现并确认非零用例通过；省略字段的旧调用继续通过。

### P3：抽取运行、打印和批次结果状态

**新增文件：** `plus-ui/src/components/report/viewer/ReportViewerPanel.vue`、`useReportRun.ts`、`useReportPrint.ts`；`plus-ui/src/components/report/batch/BatchResultPanel.vue`、`useBatchResult.ts`。各逻辑文件与其内容组件放在同一目录。

**修改文件：** `plus-ui/src/views/report/viewer/index.vue`、`batch/index.vue`、`plus-ui/src/api/report/index.ts`。

**测试：** 新增 `plus-ui/scripts/test-report-ui-state.mjs`，加入现有 `test:report` 命令；扩展 `test-report-business-print.mjs`。

- [ ] 先覆盖“请求中改参数/账号/租户、组件停用、轮询失败、导出成功后关闭”等行为，断言旧数据清空、迟到响应被拒绝及定时器/Blob回收。
- [ ] `useReportRun` 只处理模板、参数、run、分页和布局；`useReportPrint` 只处理当前run的导出、重试及文件读取；`useBatchResult` 只处理指定batchId的详情、补入队、单项重试和合并领取。
- [ ] 内容组件通过显式props、事件使用逻辑，不直接读取业务路由；完整页面负责解析路由和导航。继续复用 `useBatchSubmission`、`RequestGate`、`ReportPdfPreview` 和现有打印权限判断。
- [ ] 为已有run状态接口补前端 `runStatus(id)` 方法，增加可选 `runId` 路由读取；仅在已鉴权且模板版本匹配时复用运行，打印始终复用该run的PDF。
- [ ] 保持原路由与旧调用可用。批次确认、重试轮次、过期时间、每批1–50份及120秒文件超时沿用原约束；不让关闭组件改变后端任务状态。

**验证：** `npm run test:report`、`npm run type-check`；浏览器分别在完整页和共用内容中触发相同预览与打印动作，断言一次用户预览只创建一次run、关闭后不再轮询。

### P4：统一设计选区与上下文属性面板

**新增文件：** `plus-ui/src/components/report/designer/editorSelection.ts`、`DesignerToolbar.vue`、`SelectionInspector.vue`。

**修改文件：** 同目录 `PaperGrid.vue`、`AssetPicker.vue`，以及 `plus-ui/src/components/report/PrintSettings.vue`、`plus-ui/src/views/report/designer/index.vue`、`gridCommands.ts`。

**测试：** 扩展 `test-report-grid.mjs`、`test-report-grid-interactions.mjs` 和P1浏览器夹具。

- [ ] 先补媒体选中、属性改值、删除后选区失效、撤销后重新解析选区的失败回归。统一选择类型为列、固定格、同区域矩形和媒体；固定格按逻辑网格坐标解析，媒体按当前数组位置校验。
- [ ] 按2节分组顶栏与上下文工具；右侧毫米属性共用 `gridCommands` 校验，不能绕过列宽、页边距、二维码及字段白名单规则。媒体名称和像素尺寸来自P2元数据。
- [ ] 一次指针手势或一次连续方向键移动形成一次历史；方向键重复先修改本地候选，在keyup/失焦提交，Escape恢复起点。坐标转换统一使用 `mmToPt/ptToMm`。
- [ ] 实现专注编辑、侧栏抽屉和快捷键优先级。输入属性在Enter/失焦时提交一次；不把缩放、选区、专注模式和辅助线写入DSL。
- [ ] 图标按钮提供可访问名称，抽屉打开后移动焦点、关闭后回到触发点。媒体不可访问时不切换绑定；保留原固定版本并提示重新选择。

**验证：** `npm run test:report`、`npm run test:report:browser`；实测0.5/5mm移动、边界、长按、Escape取消、一次撤销恢复整次动作、合并拆分后的Shift选区，以及桌面/390px/深色模式。

### P5：业务页预览抽屉、单据标签及错误恢复

**新增文件：** `plus-ui/src/components/report/BusinessReportDrawer.vue`、`businessDocument.ts`。

**修改文件：** 同目录 `BusinessReportButton.vue`、`BusinessBatchDialog.vue`；四类业务列表及P3内容组件。

**测试：** 扩展 `test-report-business-print.mjs`、`test-report-ui-state.mjs` 和P1浏览器夹具。

四类列表仅提供现有字段的展示投影：

| 列表文件（相对 `plus-ui/src/views/erp/`） | 参数 / ID字段             | 单号          | 往来单位     |
| ----------------------------------------- | ------------------------- | ------------- | ------------ |
| `pu/order/index.vue`                      | `orderId` / `orderId`     | `orderNo`     | `vendorName` |
| `so/orderheader/index.vue`                | `orderId` / `soId`        | `soNo`        | `clientName` |
| `wm/sales-issue/index.vue`                | `issueId` / `issueId`     | `issueCode`   | `clientName` |
| `wm/receive/index.vue`                    | `receiveId` / `receiveId` | `receiveCode` | `vendorName` |

- [ ] 先补“保留列表筛选/勾选、模板切换、重复点击、关闭重开、网络重试、账号切换”用例，再把P3内容嵌入业务抽屉。入口每次打开仍重新检查权限和模板目录。
- [ ] 提交状态由工具栏入口实例持有，关闭抽屉只调用 `suspend`，重开沿用原提交；账号或租户切换调用 `reset` 并清空展示数据。页面离开不将参数写入浏览器持久存储，已有batchId通过批次页恢复。
- [ ] 依3.3接入单号与往来单位，提交顺序随上移/下移保持稳定；长单号省略显示并提供完整内容。旧入口只有ID时仍可正常使用。
- [ ] 批量确认成功后切到当前batchId的结果面板；创建后start失败只补start，结果未知沿用原code和原内容，可恢复导出失败使用原retryRound。
- [ ] 版本变化提示重新选模板；过期提示明确创建新批次；权限撤销清空敏感内容并定位受影响项；网络超时保留原提交以供恢复。错误文案不把普通失败标为可重试。
- [ ] 已知batchId在抽屉关闭后可从原入口恢复，参数或文档集合变更后要求显式开始新批次；不得悄悄覆盖待确认的原提交。合并按钮只在已有全部成功且有效的子任务下启用，最终仍由服务端复核。

**验证：** 四类入口分别跑单张与两张顺序调整；注入版本冲突、创建响应丢失、start失败、单项导出失败、过期、撤权和Blob错误，断言动作与真实状态对应且不产生重复批次。

### P6：完整回归、操作说明和验证记录

**修改文档：** `doc/system/guides/NexusERP_自研报表模块使用指南.md`。

**新增记录：** `doc/system/plans/2026-10-17-system-report-ui-code-optimization-verification.md`，实施日期跨日时按实际日期命名；精选合成截图置于 `doc/system/assets/report-ui-optimization/`。

- [ ] 在合成浏览器API中完成“编辑 → 保存 → 发布 → 业务列表预览 → 多选顺序确认 → 批次恢复 → PDF查看”，同时核对网络调用次数、固定版本、完整ID及清理行为。
- [ ] 后端继续以真实PDF解析测试验证页数、文本、条码和混合纸张，前端Mock流程单独标注为UI验收；不以Mock文件代替服务端PDF生成证据。
- [ ] 记录当前命令、实际执行用例数、失败/错误/跳过及运行环境；一期182项前端基线必须保留并实际执行，再叠加本阶段用例。
- [ ] 更新使用指南中的属性面板、快捷键、抽屉预览、批次恢复及错误处理。基础模板定义不变时只复核目录，不新增模板或伪造初始化结果。
- [ ] 检查差异、定向格式与文档链接；仅提交明确列出的本阶段文件。部署、真实数据库/S3及物理打印机仍作为独立验收记录。

## 5. 验收命令与完成条件

仓库根执行：

```powershell
mvn -pl "ruoyi-modules/ruoyi-report,ruoyi-modules/ruoyi-erp" -am test "-DskipTests=false" "-Dprofiles.active=" "-Dstyle.color=never"
git diff --check
```

在 `plus-ui/` 执行：

```powershell
npm run test:report
npm run test:report:browser
npm run type-check
npm run build:prod
```

对实际修改的Vue/TS/MJS文件使用本地ESLint，对实际修改的前端及文档文件使用Prettier检查；按差异清单逐项传参，禁止对全仓运行自动格式化。后端按模块核对Surefire汇总，不能把依赖模块零测试或环境Tag过滤当成测试通过。

完成条件为全部工作包有可复核结果、原接口兼容、权限与私有存储边界无回退、未出现浏览器异常或页面横向溢出、任务结束无自有进程残留。UI验收覆盖桌面1440×1100、窄屏390×1000、深色模式、键盘、加载/空/失败/权限状态，并保存注明合成数据的证据。

## 6. 范围与执行顺序

顺序为P1 → P2 → P3 → P4 → P5 → P6，每个工作包先完成自身定向验证再继续。优先让权限、版本和异步状态有稳定回归，再调整界面。

本阶段限报表页面与四类业务入口，不做ERP全站视觉改版，不新增数据集、单据类型、物理表、DSL或报表引擎，不改为异步持久合并。数据库初始化仍由管理员显式入口执行；本次提交授权不包含未来数据库写入、部署、真实打印机操作或远程推送。
