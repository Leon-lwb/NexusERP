# 物料身份与追溯 API、事件及发布契约

更新日期：2026-10-19。本文件记录当前实现的接口边界；实际执行结果见集成检查点及验证报告。源码、隔离测试和离线迁移包不等于现有业务库已迁移或现场设备已验收。

## 身份与权限

正式实物身份为 ERP `wm_sn.sn_id`，对外字段为 `identityId`。MES 本地 `snId/localSnId` 保留本地含义。所有长整型标识、版本和十进制数量金额均按字符串传输；客户端不得先转 Number 再转字符串。协议版本和清单 int 修订号仍为数值。

物料的数量、批次、SN、批次＋SN策略独立于组件混批开关。`allowMixedLot` 约束同一产品身份的同一种组件，不禁止一张仓储单据包含不同成品批次。库存位置从当前正数量库存读取，批次主档仓库仅表示来源。

PC/App/PDA 使用既有登录、租户及对象权限；Station 继续使用设备密钥和既有固定租户通道，不能从请求体指定租户。统一查询不会授予测试、IPQC 或成本权限。测试、IPQC和成本字段分别保留其原权限边界。

## 只读扫码

| 入口 | 方法 |
| --- | --- |
| PC/App | `POST /erp/wm/code/resolve` |
| PDA | `POST /app/pda/code/resolve` |
| Station | `POST /mes/station/code/resolve`，沿用 `X-API-KEY` |

请求包含 `rawCode,scene,materialId,supplierId,customerId,sourceDocType,sourceDocId,lineId`。不同时指定供应商和客户别名上下文。返回 `RESOLVED / AMBIGUOUS / NOT_FOUND / INVALID`，以及类型、稳定身份、模块本地身份、正式码、物料、ERP批次、策略版本与授权候选。

同码匹配多对象或多实物时没有默认选中项。未标记GS1的原码会同时尝试完整原文和GS1身份解释；两种解释指向不同授权对象时返回歧义，不能因数字前缀选择其一。任一101行身份候选探针溢出时明确要求补充上下文，不把截断候选经权限过滤后的单项视为唯一结果。解析不生成库存效果。GS1 的 GTIN、批次及序列断言共同核验；不能因为 SN 存在而忽略另一项不一致。结构化 OCR 对象保留原码、识别字段及匹配 ID，由服务端取交集确认；只替换编码或忽略矛盾 `rawCode` 均被拒绝。已登记 JSON 载体保留其主对象，字段仍须相符。

## 条码主对象与原标签保留

`/erp/wm/barcode`保留原URL。新登记增加`primaryObjectType=SN/BATCH/MATERIAL/DOCUMENT`，创建时明确且不可重绑；读取不再因附带SN上下文就把物料或批次载体改判为单件。SN使用ERP正式身份，批次/物料及当前库存引用均由服务端核验，库存行不能独立充当稳定主体。

单据载体只接收服务端目录内的六种类型及真实`bizId`，按原业务权限解析；没有行级来源证据时不接受物料、SN、库存或`bizLineId`断言。旧生成URL仍可由适配器根据已核实输入明确新载体的主对象；新手工登记必须显式选择。历史空类型保持原映射和兼容读取，不自动推算主对象。

编辑仅维护状态、有效期与备注；原码、主对象、身份上下文和生成/绑定时间冻结。旧删除URL执行停用，原记录保留，原码不复用。绑定时的库存引用后续可变为历史引用；扫码仍按主对象身份读取，不能因库存移位把原标签重绑给另一件。

## 注册、别名与未使用身份弃用

`POST /erp/wm/sn/reserve` 接收持久化 `commandId`。PC 手工注册仅允许 `ERP_MANUAL` 且无伪造来源 ID。业务/MES 使用内部来源编排和同一 ERP 发号注册服务。数据库按租户、用途、规则及周期原子预留完整号段；预留失败空号不回收。

`POST /erp/wm/sn/{identityId}/aliases` 追加外部或历史别名；不修改正式身份。别名可重复，解析必须保留来源及歧义。

`POST /erp/wm/sn/{identityId}/retire` 输入 `commandId,expectedVersion,reason`。仅从未使用的手工 REGISTERED 身份可经完整来源核验弃用。身份、原码及审计保留，号码不复用；旧直接删除、任意状态和绑定写入口已关闭。

## 工单策略来源与历史在途采纳

新协议下ERP下达工单时冻结产品及组件的完整策略集合和来源摘要；MES写入前读取对应工单的已提交快照。缺旧来源时返回UNKNOWN，不按当前物料主数据补造历史规则。

有原始归档证据的在途工单可走[历史原策略采纳](material-trace-policy-adoption.md)。`GET /erp/prd/work/tracking-policy/preview/{woId}`返回当时WO/BOM/需求范围及CAS摘要；`POST /erp/prd/work/tracking-policy/adopt`只接收已导入的`adoptionId,commandId,expectedVersion`。两者要求`prd:work:trackingPolicyAdopt`，执行采纳的人必须是该独立审批记录中的真实批准人。接口不接受客户端直接提交一套新策略并自批。

离线导入只登记批准凭据；业务入口在同一真实ERP事务中重新核对来源、原档、审批、时间窗及版本，一次冻结整组SOURCE_POLICY_ADOPTION并CAS应用。策略从实际采纳时点用于后续作业，原档时间只作为证据；不修改以前的消耗、库存、DHR，也不直接启用STRICT。没有可信原档的工单保持待核实。

## 单据实物与投递状态

原业务 URL 保留。追踪单据附加 `commandId,detailVersion,detailRevision,policyVersion,snItems` 等字段，原明细和身份必须由服务端重新核验。核心单件字段是 `identityId,sourceStockId,targetLocationId,originalTxId,batchId`；编码和数量不替代库存事实。退货引用原真实出库事务，调拨保留身份及账面价值。

同一命令同一原请求返回已冻结结果；同一命令改变来源、内容或版本应拒绝。已提交后移库、发货或当前单据详情变化，不得令原命令补库存。普通数量物料的旧模式保留；严格 SN 物料缺少逐件明细不得按数量模式过账。

提交状态区分本地暂存、源库已提交和关联业务确认。业务已提交而关联业务待确认时，不能重建新命令重做库存效果；应沿原命令查询或重放。消息 Broker 确认不等于目标业务提交。

## 统一身份、批次和召回

以下入口同时提供 `/erp/wm/trace` 与 `/app/pda/trace` 前缀。

| 入口 | 说明 |
| --- | --- |
| `GET /identity/{identityId}` | 同一稳定实物的 ERP/MES 分节事实 |
| `GET /batch/{batchId}` | 批次来源、当前库存、流水/客户及有据 MES 子 lot |
| `GET /recall?batchId=...` | 由实际组件批次展开产品影响 |
| `GET /where-used/{identityId}` | 由实际组件 SN 展开产品影响 |

身份和批次查询的分节页使用 `module,section,cursor,limit`；每页最多500行，原游标绑定租户、根对象及分节。身份包含 `STOCK/MOVEMENTS/ALIASES/CONSUMPTION/TEST/IPQC_WORK_ORDER/MANIFEST`；批次包含 ERP 的 `SOURCE/STOCK/MOVEMENTS` 和 MES 的 `LOTS`。`parts` 中的 `scope` 分别为 IDENTITY 或 BATCH，不能把批次当作某件实物。

召回响应区分三组独立页：

- `rows,hasMore,nextCursor`：正式实物结果，后续请求用 `afterIdentity`。
- `scopeImpacts`：尚未分配到单件的真实消耗，包含工单、消费记录、总量、已分配量和未分配量，后续请求用 `scopeCursor`。
- `batchImpacts`：可达的批次型产品，即使没有SN也保留批次身份及影响精度，后续请求用 `batchCursor`。点击该批次后按 `/batch/{batchId}` 独立读取库存与客户事实，避免一次加载所有批次的全部流水。

没有 `scope` 的实物结果是经现有单件证据连接的关联；`BATCH_POSSIBLE/WORK_ORDER_POSSIBLE` 是较粗范围的可能影响，不能显示成已确认单件用料。未逐件分配的消耗保留工单范围；不以同码或 BOM 估算补出单件关联。单件无批次的中间件仍沿实耗身份继续展开。

`truncatedByDepth`、`truncatedByCapacity`、各页 `hasMore` 和 `evidenceGaps` 必须保留展示。默认深度10、每页500，受控容量10000；页面不足500不等于全链路证据齐全。每个来源业务库对单个召回主体用同一 REPEATABLE_READ 快照读取逐件与未分配消耗；ERP/MES之间不宣称全局同一时刻快照。

## 完工、取消与更正

最终完工冻结产品清单与实际组件清单，均保留稳定行序、数量、条数、hash和分页游标。所有500行页面全部读取核验后才整体生产入库。零良品返回 `NO_GOOD_OUTPUT`，不得伪造收货单或过账组。

`POST /mes/pro/completion-manifest/{manifestId}/request-cancel` 输入 `commandId,revision,reason`。源MES先持久化取消请求；ERP验证原owner outbox，竞争同一工单和原inbox。在原业务未应用时，原事件冻结 CANCELLED 终态，迟到原消息只返回原终态与原ACK；已入库、已有组件库存效果或已消费完工时返回 COMPENSATION_REQUIRED，不自动冲销库存。

取消的 typed result 和 generic ACK 可以重复或乱序，但只投影同一个终态。新修订必须核对原请求、版本、hash和真实ERP取消结果，保留已完成报工、SN汇总、原清单及已签署DHR。`POST /mes/pro/completion-manifest/work-order/{woId}/refreeze` 是显式修订入口，输入原取消修订、稳定命令和原因。组件更正使用受控冲销与重录，不开放裸改旧消耗。

批次型产品须保存真实产品子lot的 GOOD/SCRAP 和数量，不能把 ERP 授权额度当作良品结果；`POST /mes/lt/trace/product-output` 由真实末工序/报工业务调用。一个ERP批次可对应多个有额度的MES子lot。

## 事件协议v1

信封字段为 `eventId,tenantId,sourceModule,eventType,aggregateType,aggregateId,aggregateVersion,schemaVersion,payload,contentHash`。业务状态与源 outbox 同库提交；目标业务效果、inbox 和 ACK outbox 同库提交。事件版本按来源模块与业务聚合递增，乱序等待，异载荷冲突进入待处理。master消息记录仅作为传输日志。

主要事件包含 SERIAL_RESERVATION_CONFIRM、COMPLETION_MANIFEST_FROZEN、实际批次/组件消耗与冲正、ERP来源及库存投递，以及 COMPLETION_MANIFEST_CANCEL_REQUESTED / COMPLETION_MANIFEST_CANCELLATION_RESULT。`TRACE_APPLIED` 带原事件ID、原hash及原业务结果；不能用当前库存重建旧结果。读取已应用终态仍先验证源实证，再在目标本地事务重发原ACK；不再次准备已取消的外部快照。

## 分阶段发布及外部验收边界

先审查分库增量、执行完整只读对账和隔离恢复演练，再部署兼容服务端与四端。`nexus.trace.relay.enabled` 是显式发布门禁；新协议打开时旧MES完工消费者停止产生业务效果，避免双路径。关闭时仅普通数量的兼容路径可继续，追踪物料不得降级为无身份数量过账。切换前处理旧离线任务、旧消息和在途单据并记录水位。

迁移包、当前库存身份转换、严格模式启用是不同操作。转换保持AUDIT，数量和金额守恒；严格模式还需已核实身份、批次、预留、计价组和MES就绪检查。没有新业务时按日志恢复；已有新业务则保留新身份并生成补偿任务，不覆盖旧流水或DHR。

真实环境仍需明确库基线、备份和维护窗口；禁止把主初始化SQL作为迁移入口。扫码枪、打印机出纸、离线重启、真机平台、生产消息水位和历史证据人工核实必须单独留验收记录，未实际验证的项目不能填“通过”。
