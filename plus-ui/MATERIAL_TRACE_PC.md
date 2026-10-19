# PC 物料身份与追溯切片实施记录

任务日期：2026-10-18。工作目录：`E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui`。分支 `codex/material-trace-20261018`，基线 `1ab08b09`。

此记录仅描述 PC 前端及本地隔离验证，不表示数据库已迁移、生产已部署或现场扫码已验收。原PC切片没有提交、推送、数据库连接或写入、服务启停；后续授权的后端原URL适配另见适配报告，没有修改SQL。`src/views/mes/label/**`、`src/api/mes/label/**` 是标签协作者范围，未参与编辑或格式化。执行期间观察到这两个文件由协作者继续更新，已保留其当前版本，没有按旧哈希回滚；最后构建与类型检查包含当前版本。

## 最终收敛功能（2026-10-19）

- 库存事实页`src/views/erp/wm/stock/index.vue`移除新增、修改、删除和可写dialog；只保留真实GET详情、分类查询、导出与来源单据更正说明。库存流水实际位于`src/views/erp/fin/cost/index.vue`，无库存CRUD；保留独立财务过账/期间暂估原接口并说明其作用。
- 条码主对象明确SN/BATCH/MATERIAL/DOCUMENT，长ID为字符串。新手工登记必选主对象与对应已有ID；SN使用ERP identityId。DOCUMENT只接受WM_MATERIAL_RECEIVE/WM_PRODUCTION_RECPT/WM_PRODUCTION_ISSUE/WM_SALES_ISSUE/WM_TRANSFER/WM_MATERIAL_RETURN和bizId，不接bizLineId、SN/批次/物料/库存上下文；生成/绑定时间由服务端决定。旧生成URL按SN、批次、物料、单据由精到粗校验主对象，保留原字符串参数供服务端最终推导。已有主码、主体和来源全部锁定，旧NULL仅保留原映射；只维护status/expireTime/remark。原DELETE入口在PC显示为停用，保留号码和映射，支持INACTIVE及原ACTIVE/USED/EXPIRED。
- 批次列表及独立`batch/form.vue`锁定已有原码、主体、来源（包括原仓库）和生产日期；quantity/receivedQty/issuedQty始终只读，新登记仅零累计数量。使用已有物料选择器取得真实materialId；编辑API核对原详情快照，仅维护status/expiryDate/remark，不用当前仓库主数据覆盖旧来源。PC停用使用原PUT /erp/wm/batch/changeStatus、INACTIVE、wm:batch:edit，不调用物理DELETE；与Kant的具体状态/接口边界见协调记录。
- 原生产入库列表接`ProductionReceiptDialog`，重读真实原单后执行原URL，SN和batch-only均保留实际来源核验。ACK丢失后即使单据/库存变化或另一个业务动作插入，也复用原commandId、opaque detailVersion、原源单头和原实物集合；刷新只更新显示状态和submissionSnapshot，不重建原请求。
- 完工取消后提供`ComponentCorrectionDialog`：读取真实本地SN、不可变CONSUME/REVERSE、实际工单消耗及已完成工序，按精确净额追加冲销/新分配。版本、原修订、原因和命令在首次尝试后锁定，ACK丢失原样重放；修订不直接入库或自动refreeze。
- 手工SN弃用在ACK丢失后关闭再开同一身份，恢复原命令/原版本/原原因，不读取新状态重建请求；成功才解除待核验记录，号码永久保留。
- 共享MaterialSelectDialog及MaterialQuery的warehouseId/areaId/locationId已支持原字符串ID，并原样传给查询；没有Number强转。该修复针对productionrecpt/index.vue:235/236原TS2322。

## 2026-10-19增量

召回接scopeCursor和scopeImpacts，工单范围独立分页并原样保留消耗/已分配/未分配数量；身份行明确标注逐件确认、批次可能影响、工单可能影响。真实回环HTTP验证两组游标互不覆盖、精确文本、缓存返回及迟到响应；缺工单分页时不宣称零影响。

手工reserve只发送ERP_MANUAL与null来源ID，不允许自由来源输入。SN原列表与详情页增加wm:sn:remove权限的需原因弃用弹窗：先GET原详情，再POST原commandId/identityVersion/reason，结果和号码永久保留；网络ACK丢失仍复用原请求，不调用DELETE。当前后端WmSnVo尚缺identityVersion，已明确登记为依赖，前端缺原版本时拒绝弃用。

## 本轮闭环与R5增量（2026-10-19）

- P5完成按钮按submissionState提示：SERVER_COMMITTED与CROSS_MODULE_PENDING明确待确认，保留服务端异常原因，CONFIRMED才显示跨模块已确认；SN详情展示原提交快照。
- recall/where-used新增batchImpacts与batchCursor，身份、工单、受影响批次分别分页/缓存/错误处理。BATCH_POSSIBLE和WORK_ORDER_POSSIBLE均警示为可能影响，列表只加载ID/范围，点击一批才GET其库存/流转客户去向。原根批次facts继续独立展示，不将source仓库当当前库存。真实R5无SN下游批次HTTP回归已覆盖。
- MES原流转卡出站/工序完工/扫码完工/派工完工/整卡完工前补齐真实产出，先POST product-output，再执行原complete；GOOD/SCRAP和原sourceAllocationId仅取真实来源行，整lot精确数量，GOOD=0且SCRAP>0可处理。ACK丢失重试同命令，产出保存后完成失败只重试原完成。工单详情提供取消/refreeze状态和原因操作。
- **MES服务端读取依赖尚未完成**：现有FlowCard/Lot详情未返回产品sourceAllocationId和已产出记录；manifest controller无当前态读取。PC当前在既有GET flow-card/{id}的step.productOutputContext和GET /mes/pro/work/{id}的completionState上接可选富集，未发明新GET。缺字段时明确阻断batch-only产出/取消修订，不声称业务可实际完成。所需字段与Lorentz协调边界见MATERIAL_TRACE_ADAPTER_COORDINATION.md。
- 手工SN弃用仍依赖原WmSnVo公开identityVersion；当前缺少时拒绝操作。未修改该worker文件。

## 身份追溯码元数据补充（2026-10-19）

IdentityTracePart增加可选localSnCode，并准确接受canonicalSnCode=null。面板只有本地原码与正式码不同时单列本地原码，缺正式登记证据时不将其回填为正式SN。分节分页只按正式identityId匹配，并接收服务端显式null清除旧正式码；省略字段仍兼容旧响应。未新增按编码查询/配对或修改任何后端。

该metadata阶段新增4项真实Vue交互回归先失败再通过，当时专项104/104、3个TS/Vue定向lint、4,719模块构建及完整type-check通过。增量日志使用material-trace-local-code-前缀；最终收敛后的全量复验见下方验证节。

## 已实现

- 物料原表单增加 `allowMixedLot`、只读 `trackingVersion`、`trackingStage`，显示数量/批次/序列/批次+序列四种组合。混批默认允许，策略切换由服务端验证。
- 正式发号统一调用 `/erp/wm/sn/reserve`；登记不作为库存凭据。原生成命令可以重放，输入改变产生新命令；新建两张同内容草稿不会共用命令。别名登记保留来源主体及原编码。PC 不提供自由绑定库存/修改 SN 状态及 MES 直接发货入口。
- 编号规则 `ruleVersion` 按原字符串加载、展示并参与更新；缺失原版本时阻断编辑。PRODUCT_SN 规则改用正式登记，BARCODE/BATCH_NO 保留原生成接口。
- 原收货/上架、调拨、生产领料、退料、盘点、完工入库、销售出库、杂项出入库表单接入可复用 `MaterialTraceLines`/`SnSelector`。原保存/执行 URL 保留，扩展 `commandId`、原服务端 `detailVersion/detailRevision`、`snItems`。源单头字段完整保留；行级版本不挪到单头，所有雪花身份不经 Number 转换。
- OCR 将含 `matchedMaterial`、原文本等证据的完整对象送统一解析。只有完整对象与当前来源匹配才原子绑定，避免只替换编码而留下原 ID；迟到自动批次响应不能覆盖后来的 OCR 身份。歧义展示候选并阻断，必须明确选择或放弃。
- `allowMixedLot` 只影响同一产品 SN 内同一组件的消耗。库存 SN 集合可以包含不同成品批次；来源行已分配批次时仍要求匹配。盘点按差异的绝对件数校验，不把盘点总数当过账差异。
- MES 原 FEFO 面板按一个正式产品身份及其独立 MES 本地身份逐批填写十进制数量，检查同产品同组件禁止混批、已有可证明分录和精确余额。历史工单记录不补造单件关系。
- 现有追溯页解析 SN 后调用 `/erp/wm/trace/identity/{identityId}`，首次不指定模块；之后按明确 module/section/cursor 分页，保留其它分节。IPQC 明确标为工单范围。解析 BATCH 后调用 `/erp/wm/trace/recall`，保留 afterIdentity 字符串，展示深度、容量、分页截断及证据缺口；可打开受影响正式身份。旧仓储查询保留为显式汇总入口，不自动冒充统一追溯。DOCUMENT 独立使用 `objectId/documentType/documentCode`。
- `/erp/wm/trace/repair` 隐藏路由沿用 `wm:trace:query`，审计按钮使用 `wm:trace:audit`。查询和只读审计对齐在线 ERP 快照，展示 `REVIEW_REQUIRED`、范围、证据、legacyGaps 与 truncated。只有服务端提供 REVIEWED、摘要和 reviewedManifest 时允许下载原清单，没有 live apply。

## 最终验证与交接状态（2026-10-19）

**READY：PC源码与本地验证交付，源码冻结供主线程后端全量整合。** 此状态不表示下节三个服务端读取缺口已经补齐，也不表示实际两库业务或现场验收。

- `npm run test:material-trace`：**119/119通过，0失败/跳过**。执行真实esbuild模块、Vue运行时事件和隔离回环HTTP；覆盖完整OCR/歧义、精确数量/混批、正式登记、opaque版本与ACK丢失原命令重放、取消/修订/生产入库、独立召回分页、条码主体不可重绑/六种DOCUMENT白名单/legacy NULL、批次元数据与累计事实锁定。
- `npm run test:unit`：**2,091/2,091通过，7组Node汇总，0失败/跳过**；其余既有断言脚本均正常退出。数字来自最终代码本轮日志，不使用旧报告代替。
- 定向ESLint：**95个TS/Vue文件，退出0，0错误/警告**，排除标签两个保护路径。库存只读与常规状态选项没有新增镜像测试。
- `npm run build:prod`：**4,723模块，1m 25s，退出0**。构建先生成autoimports，随后完整`npm run type-check`实际执行`vue-tsc --noEmit`、退出0，含最新barcode/batch/stock和字符串ID props修复；原235/236 TS2322已不再出现。
- PC tracked `git diff --check`及全部107个自有文件空白检查通过。最终源码与报告哈希保存在忽略日志`material-trace-pc-final-source-sha256.log`；本写集随后冻结。

本轮日志：`material-trace-pc-final-test.log`、`material-trace-pc-final-unit.log`、`material-trace-pc-final-lint.log`、`material-trace-pc-final-build.log`、`material-trace-pc-final-typecheck.log`、`material-trace-pc-final-source-sha256.log`。均为本地忽略日志，不提交。

历史独立仓储回归165/165及本线程Java隔离132/132仅为此前阶段记录；本轮未启动Maven或重复移动端验证。主线程报告的reactor06成功和条码服务98/98归主线程验证，不冒充本PC线程再次运行。

## 服务端及现场边界

1. 统一解析、发号、别名、统一身份追溯、组件召回及 repair 已按共享契约接线；测试使用隔离请求边界，没有请求现场服务，不能当作权限、跨库事件或历史数据验证。
2. 2026-10-19 已落地原业务 URL 服务端适配：原保存持久化 SN 草稿，详情返回真实集合和 opaque 版本，原执行在同一 ERP 本地事务内经 `WmSerialSelectionService.freeze(BO)` 冻结后进入实际库存门禁。PDA 内部先改量再即时执行同样覆盖；审批/MQ 无 HTTP 上下文时从持久明细重读。不要求四端预先调用独立冻结端点。原执行器若未消费 SN 不能以200伪装成功，适配器拒绝并回滚。生产入库核对既有最终完工清单，不允许PC代造manifest。最新132项服务端隔离回归全通过，真实freeze入口已修为先核对原命令和原证明；具体写集和验证见 `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/MATERIAL_TRACE_ADAPTER.md`。
3. MES 需要在原 pick 入口消费正式身份、逐批数量与原版本，返回单件不可变分录。详情缺少服务端版本时前端明确阻断，不补造版本；历史工单记录不作为单件实际消耗证据。
4. 当前在线 repair 只产生 ERP 只读快照，未人工审阅不会返回 reviewedManifest，因此下载会保持禁用；离线联合快照、正式修复及回退继续由主线程独立处理。
5. 没有运行浏览器连接真实服务、MySQL、扫码硬件、打印或跨端端到端验收。构建中现有 Sass import、依赖外置和体积提示不等于生产错误或验收结论。

## 实际改动文件

plus-ui累计107个文件，其中TS/Vue 95个；ERP Java另22个见MATERIAL_TRACE_ADAPTER.md，合计129个。排除标签协作者与构建/缓存/日志。按扩展名：.json: 1, .md: 3, .mjs: 7, .py: 1, .ts: 46, .vue: 49。

- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/MATERIAL_TRACE_ADAPTER.md`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/MATERIAL_TRACE_ADAPTER_COORDINATION.md`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/MATERIAL_TRACE_PC.md`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/package.json`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/scripts/check-original-sn-adapter.py`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/scripts/material-trace-ui-fixture.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/scripts/test-certified-warehouse-pages.mjs`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/scripts/test-certified-warehouse-workflow.mjs`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/scripts/test-material-trace-api.mjs`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/scripts/test-material-trace-paging.mjs`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/scripts/test-material-trace-ui.mjs`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/scripts/test-material-trace.mjs`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/scripts/test-mes-sn-trace-route.mjs`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/bd/material/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/bd/serial-number-rule/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/bd/serial-number-rule/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/barcode/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/barcode/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/batch/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/batch/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/detail/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/identity/document.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/identity/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/identity/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/miscissue/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/miscissue/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/miscrecpt/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/miscrecpt/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/productionissue/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/productionissue/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/productionrecpt/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/productionrecpt/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/receive/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/receive/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/return/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/return/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/sales-issue/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/sales-issue/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/sn/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/sn/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/stocktaking/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/stocktaking/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/trace/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/trace/repair.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/trace/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/transfer/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/erp/wm/transfer/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/mes/lt/consumption.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/mes/pro/completion.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/mes/pro/dispatch.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/mes/pro/flowcard.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/mes/pro/flowcardApi.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/mes/pro/sn.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/api/mes/pro/types.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/MaterialSelectDialog/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/cv/LabelCapture.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/BatchRecallPanel.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/BatchTracePanel.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/CodeResolver.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/CompletionManifestPanel.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/ComponentCorrectionDialog.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/IdentityTracePanel.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/MaterialTraceLines.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/ProductOutputDialog.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/ProductionReceiptDialog.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/SerialAliasDialog.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/SerialRegistration.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/SerialRetireDialog.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/SnSelector.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/SubmissionStateNotice.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/TraceEvidence.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/TraceSectionTable.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/model.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/submission.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/components/material-trace/tracePaging.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/router/index.ts`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/bd/material/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/bd/serial-number-rule/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/fin/cost/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/barcode/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/batch/form.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/batch/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/miscissue/form.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/miscrecpt/form.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/productionissue/form.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/productionrecpt/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/receive/form.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/receive/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/return/form.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/sales-issue/form.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/sales-issue/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/sn/form.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/sn/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/stock/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/stocktaking/form.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/trace/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/trace/repair/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/transfer/form.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/erp/wm/transfer/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/mes/lt/consumption/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/mes/pro/dispatch/components/ScanExecute.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/mes/pro/dispatch/detail.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/mes/pro/dispatch/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/mes/pro/flow-card/detail.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/mes/pro/sn/index.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/mes/pro/sn/trace.vue`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/src/views/mes/pro/workorder/detail.vue`
