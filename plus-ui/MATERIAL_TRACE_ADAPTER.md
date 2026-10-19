# 既有业务 URL SN 明细适配交付记录

复验日期：2026-10-19。目标工作树：`E:/Nexus/.worktrees/material-trace-20261018/nexus-erp`。未提交、未运行数据库脚本、未启停业务服务；除SelectionService入口最小修复外未写库存service/strategy/repository，也未写标签worker两个保护路径。

## 当前集成状态

原URL保存/读取/冻结/执行适配已落地。最新隔离验证132/132通过，0失败、0跳过，包含未修改的真实selection入口“提交成功丢ACK、随后实物移库、原payload重放”回归。该回归在106项基线上曾唯一失败；按用户优先修主路径指令，对SelectionService作最小例外修复：先按认证tenant+完整command核对原source_hash/SN集合/fingerprint及原逐件结果证明，旧命令不再比较当前来源版本或当前库存；新命令仍要求当前opaque CAS。

first/replay均返回submissionState、原commandId、原postingGroupIds和submissionWarnings。无事件为SERVER_COMMITTED；有事件但尚未全部APPLIED（包括缺组/NEW/BROKER_SENT/CONFLICT）为CROSS_MODULE_PENDING；仅全部原组均有ERP owner STOCK_POSTING事件且全APPLIED为CONFIRMED。保留原PDA ok/nextAction等字段；普通无SN响应兼容。GET详情用独立submissionSnapshot展示本次来源组凭据。

直接queue Kant/Lorentz未匹配到活动会话，未声称消息已送达。所有协调合同和最小SelectionService所有权例外已记录在MATERIAL_TRACE_ADAPTER_COORDINATION.md；未改库存InventoryService/Repository/strategy，也未触已取消Hume服务。

## 实际行为

- 保留原controller、URL、权限及原BO业务处理。过滤器保留完整原JSON，具体解析原行集合和PDA扁平行载荷，避免实体行忽略扩展字段。顶层SN汇总必须与原行归属一致，多行无归属集合明确拒绝。
- 在原ERP本地事务内捕获原mapper的insert/insertBatch实际生成ID，既兼容整批重建行，也兼容生产领料保留旧行与新增行混合；不按物料码猜行，不覆盖原ID生成器。
- `wm_document_sn` 的 `detail_version=0`、`posting_group_id IS NULL` 明确表示可修改草稿，数量每SN为1；不产生库存和正式过账组。原表已是bigint且无正数CHECK，不新增DDL。草稿command绑定真实来源hash和策略版本，不能用于表示在库或完成。
- 原执行/嵌套PDA库存调用前，将本次选择写入真实源行，调用 `WmSerialSelectionService.freeze(BO)`；opaque detailVersion保留原值，整数detailRevision使用独立协议字段。冻结、原库存效果和原业务状态在同一ERP事务提交/回滚。
- 原执行器未消费SN却返回成功时，适配器拒绝并回滚；幂等返回必须重读同一POSTED集合，身份、来源库存、目标库位、原流水、批次均保持冻结值。已过账重放不会另存成新草稿。普通数量/无SN旧请求保留原处理。
- 原PC/PDA GET返回真实 `snItems`、SN编码、policy、opaque detailVersion、字符串detailRevision和选择状态。收货独立行/分配详情读URL同样补齐；PDA退料VO原缺materialId时从真实源行补齐。部分生产领料返回下一次空集合，并单列previousSnItems，避免复用已发实物。
- 审批/MQ无需HTTP请求上下文：实际库存调用边界从持久草稿冻结，或沿用原PREPARED/POSTED冻结明细。原PDA幂等切面包围适配事务，提交后才置DONE；命令和SN进入原幂等键，重放不重复执行旧版本CAS。
- 已提交命令使用完整`BUS/<源命令与来源哈希前32>/<原URL和规范JSON的sha256>`，长度101。原HTTP中的改量前版本和原group的改量后source_hash分别核验；同号改数量/SN/源单头/原版本文本、清空或省略SN均拒绝。合法重放只核验原结果，不调用原controller改量或库存post；真实freeze入口已先识别原命令并核验原证明。
- 原动作按实际controller映射：PC盘点postAdjustment、PDA盘点post、返工issue/receive也进入消费检查/重放；返工complete只保留状态流转，不误报库存效果。
- `allowMixedLot`仅约束同一产品SN内同组件的逐批耗用；适配器没有把它应用到仓储成品SN集合。来源行明确批次约束继续交原库存门禁核验。

## 原路径与真实来源

| 既有路径前缀                                                        | 来源 / 行集合                                                                                            |
| ------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `/erp/wm/receive`                                                   | WM_MATERIAL_RECEIVE / wmMaterialReceiveLineList及wmMaterialReceiveLineDetailList、detailList             |
| `/erp/wm/productionissue`                                           | WM_PRODUCTION_ISSUE / wmProductionIssueLineList                                                          |
| `/erp/wm/return`                                                    | WM_MATERIAL_RETURN / lines                                                                               |
| `/erp/wm/transfer`                                                  | WM_TRANSFER / wmTransferLineList                                                                         |
| `/erp/wm/stocktaking`                                               | WM_STOCK_TAKING / wmStockTakingLineList                                                                  |
| `/erp/wm/salesIssue`                                                | WM_SALES_ISSUE / wmSalesIssueLineList                                                                    |
| `/erp/wm/miscissue`、`/erp/wm/miscrecpt`                            | WM_MISC_ISSUE、WM_MISC_RECPT / 原对应行集合                                                              |
| `/erp/so/return`、`/erp/pu/return`                                  | SO_RETURN、PU_PURCHASE_RETURN / 原对应行集合                                                             |
| `/erp/prd/rework`                                                   | PRD_REWORK_ORDER、PRD_REWORK_ORDER_RECEIVE / 真头单据，line=0                                            |
| `/erp/wm/sc-issue`、`/erp/wm/sc-receive`                            | WM_SUBCONTRACT_ISSUE / lines；WM_SUBCONTRACT_RECEIVE / 真头单据，line=0                                  |
| `/erp/wm/productionrecpt`                                           | PRD_WORK_ORDER / 真实woId，line=0，不能误用recptId                                                       |
| `/app/pda/wm/receive`、`issue`、`return`、`transfer`、`stocktaking` | 原confirm/pick/takeoff/count/shelf/finish/execute/post的真实URL和扁平BO；PC/App使用原ERP路径也走同一适配 |

没有增加“先调用新freeze URL”的客户端前置条件。独立收货行/分配CRUD写接口不在PC原表单保存链中；本次写入链覆盖原完整业务单和PDA原行动作，独立详情读接口提供同源SN回显。

## 生产入库边界

`WmSerialSelectionService.freeze`的普通来源目录不接受任意PRD_WORK_ORDER请求；最终生产来源由既有 `WmSerialProductionReceiptService` 的真实完工manifest冻结。原生产入库URL携带的SN在服务端与该PREPARED/POSTED集合逐项核对，缺少manifest或身份/目标/批次不符明确失败并回滚，不能由PC补造完工事实或第二套冻结组。普通数量模式仍走原生产收货校验。

## 验证

- `python plus-ui/scripts/check-original-sn-adapter.py --inventory-regressions`：132项启动、132通过、0失败/跳过，含原库存/来源/事务回归。日志material-trace-adapter-final.log。隔离javac+JUnit真实Spring事务/JDBC夹具，不启动Maven、业务服务或数据库。
- 新增18项原selection证明测试先17失败再全绿；验证同command异来源/版本/策略/集合、伪造原fingerprint、损坏原quantity/amount/tenant证明均失败，合法旧partial issue及销售原reservation source重放可核对原记录。
- 新增8项提交状态测试先确认缺字段失败，再验证首次Void/PDA、后续移库后replay、多组缺事件、多个领料事件、冲突原原因、异租户/模块不能冒充APPLIED、普通数量返回兼容。未删或弱化主线程原错误回归。
- PC专项100/100通过，覆盖R5无SN下游批次分页/按需客户去向、真实HTTP产出顺序/ACK丢失与manifest状态门禁。PC全量检查结果记录在MATERIAL_TRACE_PC.md。
- 未运行完整reactor；主线程负责整合验证。没有把夹具通过写成现场MySQL或ERP/MES两库验收。

## 写集

本任务累计129个文件：plus-ui 107个，ERP Java 22个（含实现、测试/夹具）。排除标签协作者、其他worker后端/SQL/POM变更、构建缓存和日志。PC完整清单见MATERIAL_TRACE_PC.md。

- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/WmSerialDetailAdvice.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/serial/SerialBusinessRoute.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/serial/SerialRequestFingerprint.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/serial/WmSerialAdvisorOrder.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/serial/WmSerialBusinessAdapter.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/serial/WmSerialBusinessAspect.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/serial/WmSerialBusinessPayload.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/serial/WmSerialBusinessStore.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/serial/WmSerialPayloadFilter.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/vo/pda/PdaActionResultVo.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/WmSerialSelectionService.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/controller/serial/SerialAdapterFixture.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/controller/serial/WmOriginalSerialUrlRegressionTest.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/controller/serial/WmSerialBusinessPayloadTest.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/controller/serial/WmSerialBusinessPersistenceTest.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/controller/serial/WmSerialOfflineCommandReplayTest.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/controller/serial/WmSerialOfflineSelectionEntryTest.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/controller/serial/WmSerialOriginalRoutesTest.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/controller/serial/WmSerialPdaIdempotencyTest.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/controller/serial/WmSerialProductionOriginalUrlTest.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/controller/serial/WmSerialSelectionReplayProofTest.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/controller/serial/WmSerialSubmissionStateTest.java`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/MATERIAL_TRACE_ADAPTER.md`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/MATERIAL_TRACE_ADAPTER_COORDINATION.md`
- `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp/plus-ui/scripts/check-original-sn-adapter.py`
