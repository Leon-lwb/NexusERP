# 严重问题修复清单（全量代码审查汇总）

> 审查时间: 2026-08-01，范围: 全部后端模块 + 前端 plus-ui
> 总计 163 问题（CRITICAL 15, HIGH 55, MEDIUM 93）→ 分模块明细见 issues-*.md
> 修复优先级: 先 CRITICAL（短信验证码除外，用户确认测试功能暂留）

## 修复进度

| # | 问题 | 严重度 | 模块 | 状态 |
|---|------|--------|------|------|
| C1 | generator/online 任意 SQL 执行（UnifiedGenController:355 + TableDesignController:104） | CRITICAL | generator/online | ✅ 已修（白名单+权限注解） |
| C2 | online 动态 SQL 跨租户 IDOR（OnlineMapper.xml:7） | CRITICAL | online | ✅ 已修（全部动态 SQL 强制 tenant_id） |
| C3 | 短信验证码明文返回（AppAuthController:71） | CRITICAL | admin | ⏸ 暂缓（测试用，用户确认） |
| C4 | 微信支付回调未校验订单号/金额（WxPayServiceImpl:110） | CRITICAL | pay | ✅ 已修 |
| C5 | 支付宝回调验签失败返回 success/无幂等（AliPayServiceImpl:110） | CRITICAL | pay | ✅ 已修 |
| C6 | 返工入库幂等键错配（ReworkReceiveStrategy:93） | CRITICAL | wm | ✅ 已修 |
| C7 | 盘点过账双通道无幂等（WmStockTakingPlanServiceImpl:238） | CRITICAL | wm | ✅ 已修 |
| C8 | 批次号 JVM 计数器集群重复（WmBatchServiceImpl:261） | CRITICAL | wm | ✅ 已修 |
| C9 | 采购暂估入账无过滤（CostController:52） | CRITICAL | fin | ✅ 已修 |
| C10 | 工艺路线步骤永不保存（routing/form.vue:306） | CRITICAL | 前端 | ✅ 已修 |
| C11 | NCR ID v-model.number 截断（ncr/defect/index.vue:51） | CRITICAL | 前端 | ✅ 已修 |
| C12 | IM 登出缓存不清除（im-cache.ts:232） | CRITICAL | 前端 | ✅ 已修 |
| C13 | AI 回复 v-html XSS（AiChatDrawer.vue:24） | CRITICAL | 前端 | ✅ 已修 |
| C14 | AI SQL 租户注入绕过（ExecuteSqlQueryTool:110） | CRITICAL | ai | ✅ 已修 |
| C15 | 收钱吧回调不更新订单状态（SQBServiceImpl:309） | HIGH(按CRITICAL处理) | pay | ⏳ 待修 |

## 修复记录

### ✅ C1+C2 已完成（2026-08-01）
- **C1**: `UnifiedGenServiceImpl.executeCreateTableSql` + `TableDesignServiceImpl.createTables` 增加 CREATE TABLE 白名单校验（拒绝 DROP/ALTER/UPDATE/DELETE 等）；`UnifiedGenController.createTable/importToGen` + `TableDesignController.create-table/import-to-gen` 加 `@SaCheckPermission`（tool:gen:design / online:design:create）
- **C2**: `OnlineMapper.xml` 6 个动态语句（dynamicUpdate/deleteDynamicTable/selectDynamicTableById/deleteSubByForeignKey/selectSubTableByFk/dynamicDelete）+ Mapper 接口 6 方法 + `DynamicSqlProvider.buildDeleteSql` + `batchDelete` 全部强制 `AND tenant_id = #{tenantId}`；FormServiceImpl 9 处调用点 + UnifiedGenServiceImpl 2 处 jdbcTemplate 查询传 `TenantHelper.getTenantId()`
- 编译验证: generator+online 通过

### ✅ C4+C5 已完成（2026-08-01）
- **C4 微信**: notify 增加 (1) out_trade_no 与路径订单号一致校验 (2) 回调金额与本地订单金额（分）一致校验 (3) 终态重复回调幂等忽略；任一不符返回 "fail"
- **C5 支付宝**: notify 重构为 订单号缺失/订单不存在→fail；**验签失败→fail**（不再返回 success）；已支付/已退款/已关闭重复回调幂等忽略；仅 TRADE_SUCCESS/TRADE_FINISHED 终态才更新；total_amount 与本地金额比对不符→fail
- 编译验证: pay 通过

### ✅ C6-C9 已完成（2026-08-01）
- **C6 返工入库幂等**: ReworkReceiveStrategy 幂等检查与流水插入统一用 `PRD_REWORK_ORDER_RECEIVE`，与出库 `PRD_REWORK_ORDER` 区分，重复入库被拦截、出库不再被误判
- **C7 盘点过账**: postAdjustment + executeCountAdjust 双入口都加状态条件更新抢占（仅 GENERATED/APPROVING → POSTING），行级跳过已 POSTED/BALANCED
- **C8 批次号**: generateBatchCode 改为 Redis RAtomicLong 原子自增（`erp:batch:seq:{yyyyMMdd}`），首次初始化按当日最大已有批次号兜底，跨实例/重启唯一
- **C9 暂估入账**: CostController 仅查 SHELVED/FINISHED 收货单；CostService.provision 按 PuInvoiceLine.grLineId 排除已开票收货单
- 编译验证: erp 通过

### ✅ C10-C13 已完成（2026-08-01）
- **C10 步骤保存**: routing/form.vue handleSave 把 stepItems 并入提交体（后端 insert/update 已支持 saveSteps 先删后插）；types.ts RoutingForm 补 steps 字段
- **C11 NCR ID**: defect/index.vue 去掉 v-model.number，ncrId 保持字符串透传（queryParams/form/reset 同步改 string）
- **C12 IM 登出**: user.ts logout 改为 try/catch 容错 + 调用 imCache.clearAllCache() + 重置用户全字段；顺带修复 C4(token 过期重登录死循环) 根因——logout 接口失败不再阻塞本地清理
- **C13 XSS**: AiChatDrawer renderMarkdown 返回前调 sanitizeHtml（与 assistant/index.vue 对齐）
- 验证: eslint 通过

### ✅ C14 已完成（2026-08-01）
- **C14 AI SQL 租户注入绕过**: ExecuteSqlQueryTool 第 4-7 步（租户注入、LIMIT、EXPLAIN 校验、执行）统一改用**清洗注释后的 SQL**；`/* tenant_id */`、`-- WHERE` 注释绕过全部失效（判断与执行同一份干净文本）
- 编译验证: ai 通过

## H1 安全批（2026-08-01，全部完成）

| # | 问题 | 状态 |
|---|------|------|
| H1-1 | 角色授权接口可给任意用户授超管角色（SysRoleServiceImpl insertAuthUsers/deleteAuthUsers/deleteAuthUser） | ✅ 三方法加 `LoginHelper.isSuperAdmin(roleId)` 拦截 |
| H1-2 | 员工排班接口路径大小写不匹配（前端 employee-schedule vs 后端 employeeSchedule） | ✅ 前端 5 个接口统一为后端 camelCase 路径 |
| H1-3 | IM App 好友请求 IDOR（AppImController:314-418） | ✅ 处理前校验当前用户 == 接收方 |
| H1-4 | IM App 群消息搜索越权（AppImController:743-751） | ✅ GROUP 分支校验 targetId ∈ 我的群组 |
| H1-5 | IM App groupInfo/groupMembers 越权（AppImController:887-922） | ✅ 复用 isGroupMember 校验 |
| H1-6 | AI 会话归属未校验（AiServiceFacade:150-166） | ✅ 复用会话时校验 session.userId == 当前用户 |
| H1-7 | AI 文生图删除越权（AiImageController:106-111） | ✅ 删除前逐条校验记录归属 |
| H1-8 | 知识库 URL 导入无 SSRF 防护（KnowledgeImportService:65-144） | ✅ sanitizeUrl（协议白名单+拒绝内网/环回/链路本地）+ 禁用自动重定向 |
| H1-9 | 自动编码接口无权限注解（SysAutoCodeController） | ✅ get/preview 加 @SaCheckPermission |

编译验证: system/im/ai 均通过

## H2 批（MQ/租户上下文，2026-08-01，全部完成）

| # | 问题 | 状态 |
|---|------|------|
| H2-1 | ReworkStockConsumer 缺 TenantHelper.dynamic | ✅ doProcess 内 TenantHelper.dynamic 包裹 |
| H2-2 | SubcontractStockConsumer 缺 TenantHelper.dynamic | ✅ 同上 |
| H2-3 | StockInConsumer writeback 消息未传 tenantIdStr | ✅ 同步设置 6 位零填充 tenantIdStr |
| H2-4 | 收货回写不幂等（PuPurchaseReceiveWritebackServiceImpl:71-105） | ✅ Redis 幂等键 `erp:writeback:receive:{receiveId}` 24h |
| H2-5 | 倒冲双事务重试双倒冲（ProductionBackflushConsumer:536-548） | ✅ Redis 幂等键 `erp:backflush:wo:{woId}:{qty}` 24h |
| H2-6 | VendorEvaluationTask 无租户上下文 | ✅ 遍历 sys_tenant + TenantHelper.dynamic |
| H2-7 | StockAlertTask 无租户上下文 | ✅ 遍历 sys_tenant + TenantHelper.dynamic |
| H2-8 | 补货申请去重失效（PuPurchaseRequisitionServiceImpl:502-556） | ✅ 按已有未完结申请行 materialId 集合跳过 |

编译验证: erp 通过

## H3 批（并发/幂等，2026-08-01，全部完成）

| # | 问题 | 状态 |
|---|------|------|
| H3-1 | wm 入库执行并发重复过账（WmMaterialReceiveExecutorServiceImpl:94） | ✅ SHELVED→EXECUTING 条件更新抢占 |
| H3-2 | 调拨执行并发（WmTransferServiceImpl:184） | ✅ 非 EXECUTED→EXECUTING 抢占 |
| H3-3 | 杂项出库/入库并发（WmMiscIssue/Recpt:146/148） | ✅ 同上 |
| H3-4 | 销售出库并发（WmSalesIssueServiceImpl:216） | ✅ CONFIRMED→EXECUTING 抢占 |
| H3-5 | 生产入库并发（WmProductionRecptServiceImpl:144） | ✅ 同上 |
| H3-6 | 预留释放版本冲突静默跳过（WmStockReservationServiceImpl:99） | ✅ 重读重试3次+失败抛异常回滚 |
| H3-7 | 预留不足静默部分预留 | ✅ 不足抛异常（禁止部分预留） |
| H3-8 | 生产领料绕穿预留（ProductionIssueStrategy:87） | ✅ 查询排除已预留量 |
| H3-9 | 杂项出库绕穿预留（MiscIssueStrategy:117） | ✅ 同上 |
| H3-10 | 调拨跨仓维度错乱（TransferStrategy:85） | ✅ 源/目标按仓库过滤 + 行补非表仓库字段从单头填充 + 目标库存用目标仓库维度 |
| H3-11 | SN 批量生成并发重复（WmSnServiceImpl:202） | ✅ Redis 原子自增 `erp:sn:seq:{yyyyMMdd}` |
| H3-12 | 序列号并发+周期重置失效（BdSerialNumberRuleServiceImpl:140-197） | ✅ DB 条件更新原子递增；周期重置用 attr1 存周期段 CASE WHEN 判定 |

编译验证: erp 通过

## H4 批（前端，2026-08-01，全部完成）

| # | 问题 | 状态 |
|---|------|------|
| H4-1 | 雪花 ID Number() 截断 14 处（sop/viewer、routing、requirement、storagelocation×5、WmLocationStockDialog、barcode×8、sn×3、pu/invoice×3、barcode/config、8d-report、ncr/rework、ScanExecute、capacity-calendar×4、ai/abTest×2） | ✅ 全部改字符串透传 / el-input |
| H4-2 | trace 追溯把物料编码当 materialId | ✅ 先按编码查物料再追溯 |
| H4-3 | wm/receive 工具栏删除传 Event 恒失败 | ✅ Event 兜底 + 批量空选提示 |
| H4-4 | pu/return 删除传 undefined | ✅ Event 判断 + ids 兜底 + catch |
| H4-5 | sales-issue 删除明细全删（index 未赋值） | ✅ rowLineIndex 写入 row.index |
| H4-6 | so/invoice + so/contract 物料对话框缺失 | ✅ 接入共享 MaterialSelectDialog |
| H4-7 | 委外 issue/receive/submit/logs 端点不存在 | ✅ 后端补齐 4 端点（issue→start、receive→complete、submit、logs 状态时间线） |
| H4-8 | AI API Key 明文展示 | ✅ 列表脱敏 sk-*** 尾4位 |
| H4-9 | 客户端秘钥明文展示 | ✅ 列表脱敏 + 编辑 show-password |

保留项（技术约束）:
- 报表 iframe / IoT SSE 的 token 走 URL query 是积木报表协议与 EventSource 无法设 header 的技术约束，后端已用 @SaIgnore + StpUtil 手动校验，日志侧需反代过滤

验证: eslint 全部通过；erp 编译通过

## M1 批（MEDIUM 后端数据一致性，2026-08-02，全部完成）

| # | 问题 | 状态 |
|---|------|------|
| M1-1 | SPC judgeOoc 重复值定位错位（SpcControlChartServiceImpl:435） | ✅ 改遍历下标传入 |
| M1-2 | MES 报工数量无校验（ProFlowCard/ProDispatch） | ✅ 非负 + 合计 ≤ 计划量 |
| M1-3 | 备件出入库 quantity 未校验（EqSparePartServiceImpl:175） | ✅ 非空且 >0 |
| M1-4 | 批次创建 quantity 未校验（LtLotServiceImpl:112） | ✅ 非空且 >0 |
| M1-5 | 拣货/上架规则兜底选错类别（PickingResolution/PutawayStrategy） | ✅ 兜底仅通用规则，无匹配返回 null |
| M1-6 | SalesOutStrategy 流水 txQuantity 误用整行数量 | ✅ 传实际扣减 deductQty |
| M1-7 | CountAdjustStrategy afterQty 快照推算 | ✅ 原子更新后重读实际库存 |
| M1-8 | 完工入库增量完工数量丢失（WmProductionRecptServiceImpl:211） | ✅ 补差额入库 + 单据数量更新为累计 |
| M1-9 | 凭证负数借贷校验缺失（GlVoucherServiceImpl:317） | ✅ 拒绝负数分录 |
| M1-10 | 核销金额精度错配（FinSettlementServiceImpl） | ✅ settleAmount 统一 setScale(2) |
| M1-11 | 重开期间结转跳过（FinClosingServiceImpl:383） | ✅ 增量结转（closing 记录已结转净额） |
| M1-12 | 清账静默跳过（FinSettlementServiceImpl:261） | ✅ 凭证未生成抛异常回滚核销 |
| M1-13 | 折旧并发丢失更新（FaDepreciationServiceImpl:147） | ✅ 原子累加 + 乐观锁校验 |
| M1-14 | FaAsset queryById NPE | ✅ null 直接返回 |
| M1-15 | 工单双完工（PrdWorkOrderServiceImpl:502） | ✅ WHERE 带状态条件 |
| M1-16 | 发票草稿重复生成（So/Pu Invoice） | ✅ invoiced_qty + ? ≤ delivered/received 条件门禁 |
| M1-17 | 信用冻结双占（CreditCheckServiceImpl:66） | ✅ 已有 FROZEN 跳过 |
| M1-18 | 合同重复生成采购订单（PuContractServiceImpl:315） | ✅ 仅 APPROVED 可生成 + CAS 状态更新 |
| M1-19 | 多发票退货进项转出超冲（PuInvoiceServiceImpl:709） | ✅ 按发票行先消先冲逐张分配 |

编译验证: mes/erp 通过

---

### 2026-08-16 wm 条码/批次/SN 关联修复（已修，详见 doc/erp/plans/2026-08-16-wm-barcode-batch-sn-remediation.md）

- [x] wm 条码/批次/SN：snId贯通条码、batchGenerate关联批次、库存按SN落行、序号并发锁、库存事务SN维度、insertByBo自动编号（2026-08-16）

| 项 | 问题 | 修复 | 提交 | 验证记录 |
|----|------|------|------|---------|
| ① | 条码实例未落 snId，无法按 SN 追溯一 SN 一条码 | generateAndSave[ByCode] 增 snId 参数并落 sn_id；入库执行器传 stock.snId；前端生成弹窗/详情/筛选支持 SN | 2a392966 | WmBarcodeServiceImplTest 1/1 绿 |
| ② | SN 批量生成未关联批次 | batchGenerate(materialId, quantity, batchId, ...) 校验批次并回填 batch_id/batch_code；前端批次下拉 | d1ec9288 | WmSnServiceImplTest 12/12 绿 |
| ③ | 库存唯一键/查询缺 SN 维度，同批次多 SN 合并一行 | uk(material_id,batch_id,warehouse_id,location_id,sn_id)；buildStockKey/selectOneStock 带 snId | c3e5903a | WmMaterialReceiveStockTest 3/3 绿 |
| ④ | 批次/SN 序号初始化竞态 → 唯一键重复 | DailyCodeSequenceProvider：分布式锁内「读缓存→查库最大序号初始化→原子自增」；批次/SN 全部接入 | d7282786 | DailyCodeSequenceProviderTest 2/2 绿；旧 Redis 序号逻辑已删除 |
| ⑤ | 条码前端无 SN 维度 | 同 ①（生成弹窗「序列号ID」、详情展示、查询参数） | 2a392966 | 前端静态检查通过 |
| ⑥ | 库存事务流水缺 sn_id/sn_code | recordStockTransaction 追加 setSnId/setSnCode | 92280f6c | WmMaterialReceiveStockTest 3/3 绿 |
| ⑦ | 手工新增批次/SN 需手填编码 | insertByBo：编码留空自动编号（provider）、默认状态/数量、materialId 必填校验、SN 按 batchId 回填批次码 | d6fbedcd | WmBatchServiceImplTest 7/7 + WmSnServiceImplTest 12/12 绿 |

全套 wm 单测 25/25 通过（2026-08-16 回归）。
数据说明：历史已合并的 SN 库存行不自动拆分（需按带 SN 的 TARGET_IN 流水重建，二期处理）；wm_barcode 的 12 条 material_id=1 测试痕迹与 2026-07-31 缺省条码属测试产物，可保留。
