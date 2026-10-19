# 原业务 URL SN 适配写集协调（2026-10-19）

致 Kant / 主线程：本任务负责既有 ERP PC/PDA/App 业务 URL 的 SN 明细适配。新增实现限定在 `com.nexus.erp.wm.controller.serial`、原 `WmSerialDetailAdvice` 以及必要 BO/VO/controller 和测试；PC 沿用已有 plus-ui 写集。除用户随后优先授权的SelectionService入口最小例外外，不修改库存service / strategy / repository，也不修改 WmSn / SerialIdentity / TraceRepair / Code / TraceAuthorization controller。

适配器在原保存、PDA 行确认与执行请求中读取真实 body，将 `commandId / detailVersion / snItems` 接到 `WmSerialSelectionService.freeze`；执行前用原 ERP 本地事务封闭保存、冻结和原执行。PDA pick 等先修改事实再 execute 的入口需要在嵌套执行边界接入。审批/MQ 从现有 `wm_document_sn` 和 posting group 重读冻结详情。普通数量模式保留。

依赖 Kant 保持 `WmSerialSelectionService.freeze/snapshot`、`WmSerialInventoryRepository.request/sourceHash/group/items` 与现有真实 source Spec 契约。opaque `detailVersion` 与整数 `detailRevision` 分离；冻结明细不可静默变更。`allowMixedLot` 仅产品 SN 的组件耗用，不限制仓储不同成品批 SN 集合。

直接投递 `codex queue --thread Kant` 未匹配到活动会话，故此文档作为可复核转达记录；未声称已送达。

前一轮基线（ACK丢失真实入口回归追加前）：8个实现、7个测试/夹具已落地；当时85项Java回归通过，PC69项追溯和165项旧回归、61文件lint、build→type-check通过。适配采用revision=0可编辑SN草稿；正式freeze仍revision>=1。真正执行边界才冻结，并从原自动审批/MQ路径重读。最终生产收货只核对原已冻结manifest；普通SelectionService的PRD_WORK_ORDER来源仍属于专用完工服务，未改Kant库存写集。详细路径及验证记录见 `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/MATERIAL_TRACE_ADAPTER.md`。

## ACK丢失后重放合同（2026-10-19历史基线，后文已修复）

原接口适配器已实现：持久命令 `BUS/<sha256(rootCommand/type/source/line)前32>/<原URL及规范化原JSON的sha256>`，长度101；请求指纹包含原detailVersion、行数量、SN集合及原源单头。PDA先改量后freeze导致原HTTP detailVersion可能不同于group.source_hash，因此重放须先用命令后缀核对原HTTP请求，再把group.source_hash / 原policy_version / 原revision传回SelectionService核验。查tenant+command前缀并核对source type/id/line；POSTED全部通过才短路原controller业务，绝不再pick/post。新command仍用当前源版本CAS。

请Kant改SelectionService.freeze：`requireWrite`后首先按tenant+完整commandId查询组（不是从当前request/group(latest)推导）。校验source type/id/line、原source_hash等于BO.detailVersion、原revision/policy_version、SN完整集合及原请求fingerprint，POSTED核验原逐件事务/valuation/outbox凭据，允许SN已移动/发出/后续命令已发生；不比较当前SN余额、原source当前hash，不执行库存写。PREPARED只返回原冻结结果，真实post仍由原执行守卫检验。找不到command才读取当前source并要求当前opaque detailVersion一致。

适配器测试已走真实旧PDA/ERP controller + 真实Spring提交边界：首次原payload执行成功但丢弃ACK，随后移库并改变sourceHash，再重放完全相同原payload；验证原gid返回、原pick/post计数不增加。同号改数量/SN/来源/旧版本文本必须失败。另保留直接调用当前SelectionService的红色契约回归，避免mock掩盖入口缺口。本线程不修改selectionSvc/库存service/strategy/repository。

再次执行 `codex queue --thread Kant` 返回 `No active session found matching Kant`；未声称已直接送达。

## 原入口失败证据（2026-10-19历史基线，后文已修复）

当时命令 `python plus-ui/scripts/check-original-sn-adapter.py --inventory-regressions`：106项实际启动，105通过、1失败、0跳过。唯一失败为 `WmSerialOfflineSelectionEntryTest.realSelectionEntryMustVerifyOriginalCommandAfterAckLossAndLaterMove`；栈为 `WmSerialBusinessAdapter.replay -> WmSerialBusinessStore.verifyCommand -> WmSerialSelectionService.freeze:19`，错误是“单据明细版本已改变，请重新读取服务端详情”。不是主线程structured OCR尚未编译造成，也没有借mock把此回归标绿。

精确复现：`python plus-ui/scripts/check-original-sn-adapter.py --class WmSerialOfflineSelectionEntryTest`。适配器先找到原完整命令及其原source_hash，再委托freeze；现有freeze仍先`repository.request/sourceHash`而不是查tenant+command。请求前v1、原PDA改量后freeze的v2、提交及后续移库后的v3是不同事实版本；客户端原v1和group原v2均已由适配器分别保留。

请Kant按前述入口合同接入。构造历史证明时不能用`proofRequest(type,source,line,tx)`的最新组代替原command对应组，否则旧部分领料命令在同一行新revision之后仍会误认。POSTED核验原组的明细、逐件流水、valuation/outbox等原结果即可，不要求当前SN仍在原仓，也不能再次post。只有新命令才CAS当前opaque版本。

适配器自身补充的真实动作映射回归已先9红后全部21绿：PC盘点postAdjustment、PDA盘点post、返工issue/receive均进入消费检查与重放，返工complete仅状态流转。未写Kant服务/策略/仓储文件，也未触及已取消的Hume服务。

## PC增量契约及另一个精确依赖

- 召回已接可选scopeCursor、scopeImpacts独立工单范围分页；BATCH_POSSIBLE/WORK_ORDER_POSSIBLE与省略scope的逐件确认分开标识，工单数量不冒充单件证明。
- 手工reserve固定sourceType=ERP_MANUAL、sourceId=null；新增需原因的retire弹窗，GET原身份详情后原样提交identityVersion作为expectedVersion，原命令可在ACK丢失后重试。
- 当前WmSn实体有identityVersion，而WmSnVo未返回该字段。需要Nietzsche/主线程在原SN详情VO暴露原identityVersion字符串；PC缺字段时明确阻断，不补造版本。生命周期审计表结构由SQL owner集成，本线程不改SQL或这些后端写集。

## Lorentz：PC批次实际产出/完工修订读取合同（2026-10-19）

PC已确认现有真实POST：`/mes/lt/trace/product-output`输入commandId/cardId/stepRecordId/lines[{lotId,sourceAllocationId,quantity,result:GOOD|SCRAP}]；原complete-step/exit、dispatch complete和最后complete-card均保留。只在batch-only产品的实际末工序良品/各工序报废路径采集实物；每个lot整体分类，不能同lot拆成GOOD+SCRAP；先保存产出，再执行原complete，不另建库存入库调用。

现读模型缺口：LtLotVo及原lot trace没有sourceAllocationId；MesCompletionManifestController当前没有GET。PC不能从lotCode猜allocation，也不会要求操作员填sourceEventId/eventID。请Lorentz给出真实读取URL/字段，或将下列字段并入原业务GET并告知确切位置：

1. 按cardId + stepRecordId读取实际产品产出上下文：真实mesWoId/materialId/isFinalStep及策略；可选来源[{lotId,lotCode,sourceAllocationId,materialId,quantity,status,qcStatus,erpBatchId}]；该操作已记录的outputs[{outputId,commandId,lotId,sourceAllocationId,quantity,result}]。来源必须来自该ERP/MES工单的PRODUCTION_OUTPUT授权，排除已占用lot；所有ID/数量字符串。若分页请提供hasMore/nextCursor，不能默认拿第一页就是全集。
2. 按mesWoId读取最新完工清单/修订历史，返回manifestId/revision/status/goodQty/scrapQty/contentHash、取消等待/拒绝状态及可核对原因。PC从服务端原manifest/revision提交request-cancel，等待真实CANCELLED后才允许refreeze，绝不把request-cancel的HTTP成功当作已取消。没有清单也应返回明确空结果。

现有写合同：POST /mes/pro/completion-manifest/{manifestId}/request-cancel {commandId,revision,reason}；POST /mes/pro/completion-manifest/work-order/{woId}/refreeze {commandId,cancelledRevision,reason}。PC将从真实业务字段构造，不暴露技术eventID输入，不擅自取消/重置报工数量，原最终一次入库由后台清单控制。

`codex queue --thread Lorentz`未找到活动会话；本段是明确转达/字段协调记录，未声称直接消息已送达。PC继续完成组件、原路径接线和HTTP顺序测试，读取接口最终以owner实际源码为准，不自行修改MES后端。

## reactor04唯一失败优先修复：本轮selection入口最小写集声明

依据主线程2026-10-19最新“优先协同修主路径、保留真实回归、isolated后告知05”要求，本轮在无法匹配Kant活动会话时，将新增写入限定为WmSerialSelectionService.freeze历史命令分支。写前核对SHA256=F6CF426078645B87071846DAFBE9F091A275F3701A9469CDDADF005615D7CEC1，避免覆盖并发修改；不修改WmSerialInventoryService/Repository/策略。请Kant接续以该入口为新基线。

先查租户+完整command原组；原source_hash/revision/policy/来源及完整SN hash一致后，用原指纹恢复原请求方向，POSTED进入既有真实verifyProof。销售预留来源从原组CONSUME_RESERVE不可变流水恢复；PREPARED只返回原冻结，不假报业务完成。新命令保持原当前详情CAS和原inventory.freeze。补18项真实selection+真实verifyProof回归已见17红1绿，原ACK丢失真实入口测试不改不跳过。

## P5 提交状态闭环（2026-10-19）

最小改动已接入原adapter的first/replay响应：`submissionState / commandId / postingGroupIds / submissionWarnings`；普通无SN响应兼容。首次按实际freeze获得的组ID追踪，不用当前库存或后续最新组代替。只读`wm_trace_outbox`的tenant+ERP owner+STOCK_POSTING全部事件；每个原组均有事件且全部APPLIED才CONFIRMED；无任何事件SERVER_COMMITTED并提示缺凭据，有事件但缺组/NEW/BROKER_SENT/CONFLICT为CROSS_MODULE_PENDING并保留last_error。PDA原ok/nextAction等字段保留；详情增加独立submissionSnapshot。合法原命令仍通过真实freeze/verifyProof，不增加库存动作。

隔离验证：`python plus-ui/scripts/check-original-sn-adapter.py --inventory-regressions` 132/132、0跳过/失败，包含未修改的ACK丢失后实际移库再重放回归和新增8项提交状态回归。日志`material-trace-adapter-final.log`。新增写集仅adapter/store/detail advice、PdaActionResultVo、对应测试/runner。主线程05若已完成，后续整合需要纳入这次P5增量；本线程未运行Maven。

## PC MES原入口读取依赖（2026-10-19补充）

再次核对：当前`MesMaterialTraceController`只有POST，`MesCompletionManifestController`只有request-cancel/refreeze两个POST，原FlowCard/Lot/WorkOrder详情没有实际产出来源和manifest当前态。PC不会发明GET URL或将component consumption的sourceAllocationId充作产品产出分配。

请Lorentz给出现实读取契约，或在既有`GET /mes/pro/flow-card/{cardId}`的每个step增加`productOutputContext`（本PC当前采用的可选补充字段）：cardId/stepRecordId/mesWoId/materialId/isFinalStep/policy/sources/outputs；sources必须包含真实lotId、lotCode、sourceAllocationId、materialId、mesWoId、整lot quantity、status/qcStatus；outputs包括outputId、commandId和原line字段。若候选裁剪请明确hasMore/nextCursor并提供实际分页URL。

既有`GET /mes/pro/work/{mesWoId}`可补`completionState`（PC暂用可选补充字段）：显式latest(无manifest为null)、revisions、truncated/hasMore/nextCursor、取消待确认/拒绝原因。latest保留manifestId/revision/status/goodQty/scrapQty/contentHash等。PC读取缺失时会显示精确未提供状态并阻断batch-only产出声明，不猜测allocation或已取消revision；取消请求成功也不视为CANCELLED。若主线程/Lorentz采用独立真实GET，请告知契约后替换现有reader，不修改PC实际POST/原complete顺序。

## R5无SN下游批次PC接线（2026-10-19）

已对齐主线程真实recall/where-used的batchImpacts/batchCursor。PC使用三组独立页缓存、游标、错误状态；BATCH_POSSIBLE/WORK_ORDER_POSSIBLE不升级为逐件确认。首次只显示批次ID及精度，点击单批才GET /erp/wm/trace/batch/{id}读取授权模块的STOCK/MOVEMENTS/SOURCE/LOTS和客户去向，保留原根批次事实，不批量嵌入所有批次的500行。统一SN页提供实际where-used入口。专项100/100通过，含新增R5真实HTTP原反例和旧契约未提供batchImpacts提示。

## 本轮最终验证（2026-10-19）

ERP隔离132/132、PC专项100/100、全unit 2,091/2,091（7组汇总，0跳过）、旧仓储回归165/165；82个TS/Vue定向lint通过，出站良品数量控件修复后单文件再次lint通过；最终生产构建4,719模块/1m18s，通过后完整type-check退出0。全部自有116文件空白检查与tracked diff-check通过。完整清单见MATERIAL_TRACE_PC.md（plus-ui94）及MATERIAL_TRACE_ADAPTER.md（ERP Java22）。实际MES读取与SN版本字段依赖仍如前述，无真实服务/数据库/两库端到端验收，无提交。

## 最后PC收敛补充（2026-10-19）

主线程已确认条码服务契约和98/98服务端定向结果；PC不重复后端验证。PC已落地六种canonical DOCUMENT白名单、禁行/实物上下文、ERP正式identityId、legacy NULL不可推造类型、INACTIVE停用及元数据编辑。旧DELETE条码URL仅按主确认语义显示停用，保留原映射；不发明新的条码接口。

批次服务由Kant拥有；PC不修改其service/strategy/repository。PC批次列表和独立表单不再调用DELETE；停用用已有PUT /erp/wm/batch/changeStatus，batchId为字符串，status=INACTIVE，权限wm:batch:edit。请Kant保持状态守卫与该入口一致；无法匹配活动会话，未声称直接消息已送达。已有批次只更新status/expiryDate/remark，主体/原来源仓库/累计数量必须与原详情逐字一致。旧API导出的PUT /erp/wm/batch/updateQuantity没有PC调用；Kant仍需在外部入口关闭绕过，不以本页只读代替服务器事实守卫。新建PC批次三个累计数量仅零字符串，不代表库存。

PC缺少实际读取字段的三个原依赖保持明确：GET /mes/pro/flow-card/{id}未提供step.productOutputContext（真实产品lot/sourceAllocationId/已产出事实）；GET /mes/pro/work/{id}未提供completionState（当前manifest/修订/取消确认）；ERP原SN详情VO未提供identityVersion。现有两个trace/completion controller仍只有写接口。PC缺这些读取事实时显示原因并阻断相应批次产出/取消修订/弃用，不补造ID、版本或已确认状态。主线程后续可补既有GET富集或给出现实独立GET契约；不需要改变已存在的PC POST顺序。

## 最终PC冻结交接（2026-10-19）

本轮包含最新条码INACTIVE、DOCUMENT六种canonical主对象契约、批次原码/主体/来源/累计量只读及stock事实只读。PC专项119/119、完整unit 2,091/2,091（7组Node汇总）、95个TS/Vue定向lint、4,723模块生产构建、构建生成autoimports后完整vue-tsc --noEmit均退出0，0失败/跳过。PC107个文件清单在MATERIAL_TRACE_PC.md；历史ERP Java22个在MATERIAL_TRACE_ADAPTER.md，累计129个。最终哈希日志material-trace-pc-final-source-sha256.log，PC源码随后冻结，不再自行追加范围，主线程可继续后端全量验证。

三个真实读取缺口仍如前述：productOutputContext、completionState、ERP identityVersion。PC拒绝在缺真实来源/版本/取消确认时编造成功；该READY为PC写集和本地验证交接，不能解释为实际业务端到端验收。无提交、无SQL/DB操作、无业务服务启停，本轮无Maven，标签/PDA/App/Station范围保持未修改。
