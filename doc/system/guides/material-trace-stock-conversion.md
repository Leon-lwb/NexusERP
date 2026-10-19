# 当前库存身份转换（2026-10-19）

本切片限定于`E:/Nexus/.worktrees/material-trace-20261018/nexus-erp`，convert-*入口仍只处理离线JSON及待审SQL，不建立数据库连接。另有用户明确授权的自有容器SQL验证，见[独立MySQL验证](material-trace-converter-mysql57-validation.md)；该轮未修改Java/UI或仓库DDL，未触碰现有库/服务或提交。`convert-*`是独立契约，原五表repair写白名单不变。

## 实际库存与计价依据

已读取当前`WmSerialInventoryRepository.inboundPool/updateIdentity/verifyMaterialPools`及`WmSerialInventoryService.rebalance/post`。运行时按租户、物料、仓库、储位、批次查池，供应商条件视业务入口而定；同范围零库存旧行也会被扫描。`wm_valuation_group.quantity/amount`是冻结基线，后续余额等于该基线加关联库存行的v2流水效果。`TraceRules.allocateAmount`按正式SN ID数值顺序分配四位尾差。

实现步骤为：先写失败回归，再实现census与整物料门禁、零净变化转换、完整日志/审计桥、单库SQL、真实CLI及合成样例。已跑通数量→SN、批次→批次+SN以及原来已唯一对应单件的SN迁移；多个明确独立的库位成本范围可以同一次整物料转换。

## 输入与证据

完整例子见[输入快照](../../../script/tools/material_trace/examples/stock-conversion/before.json)和[批准计划](../../../script/tools/material_trace/examples/stock-conversion/plan.json)。这些只含T1、SYNTH码、虚拟UUID，绝非真实历史证据。

- 快照沿用schema_version=1、target、server_ids、tenant_scope、captured_at、水位及35张业务表完整声明。转换要求每行包含metadata全部列，ID/版本/数量/金额为字符串或null。35表含只读的pro_product_lot_output实际产出和wm_serial_lifecycle_command身份退休审计；它们的新行、同数量行的版本/原因变化均阻止旧日志直接回退。旧30/31表日志不能通过补空表升级。
- 额外必需`conversion_run_states`，即使为空也必须显式提供。每行完整包含`run_id,tenant_id,material_id,status,version,plan_hash,bridge_hash`，状态含全部APPLIED和REVERTED运行，不得只导出本物料。它是独立转换审计账的只读状态投影。
- 已应用转换同时提供`stock_conversion_bridges`原冻结桥，且必须与APPLIED运行状态一一匹配；已回退运行保留状态但不继续套用桥。完整日志单独保管，不把含前快照的journal_json塞进业务tables造成循环摘要。
- 计划字段：`schema_version=1,converter=CURRENT_STOCK_TO_SN,run_id,target,tenant_id,material_id,snapshot_hash,policy_before_hash,expected_policy_version,cutover_at,sources,census`。
- 每个source包含`stock_id,before_hash,valuation_group_id,cost_scope,targets`。`before_hash`绑定原库存全部列；`cost_scope`明确MOVING_AVERAGE、原凭据引用、原六位单位成本及tenant/material/warehouse/area/location/batch/vendor/wo/base_uom维度。每个target明确已有ERP`identity_id`、原身份完整行`identity_hash`和新的`stock_id`。
- 新stock/group ID只是明确提供的正64位元数据ID，必须当前不存在；工具不生成Snowflake、不发号、不插入wm_sn、不编造业务凭据。正式SN集合必须同租户同物料、原码唯一、实盘逐件数量等于原整数数量，每件1。
- census必须为`PHYSICAL_COUNT`，包含非空`evidence_id,reference,approved_by,movement_freeze_reference`，`observed_at=cutover_at`、`inventory_state=AVAILABLE_ONLY`和历史边界。`conversion`断言精确覆盖整个计划业务意图，包括target与完整snapshot_hash；artifact是其冻结JSON，artifact_sha256必须匹配。不能只给一个无内容的hash或把空白审核人当批准。
- `history_boundary`包含through、COMPLETE_AGGREGATE或GAPPED_AGGREGATE、原凭据reference及该租户物料原流水集合ledger_hash。已导出流水不得在census之后，原行、版本、旧号和原流水粒度都不得改写。

导出`conversion_run_states`时应从新审计表读取包含租户的完整状态字段，版本按数据库CAST为字符串，包含已回退记录。导出者必须核实全部范围与两库停写/消息边界；工具无法独立证明“完整导出”声明。

## 门禁与首版支持边界

启用SN必须覆盖该tenant/material的全部正库存，不允许只挑容易拆的行。物料必须启用、处于AUDIT、策略和完整前行hash匹配；策略版本递增1，SN启用，保持AUDIT，batch/mixed规则不变。STRICT为后续独立授权，转换不会自动切换。

原库存基本单位必须与物料基本单位ID一致，不做隐式单位换算；原库存必须是整数可用量、预留0、未冻结、没有工单/在制关联，且尚无不明计价组。即使库存reserved计数为0，原实际预留头/行仍未完成也会阻止转换；未完成冻结单据、未应用跨库消息、在制/报废/已出库身份同样阻止。应先通过真实业务单据释放/消费/移出在制，重新导出并实盘，不把预留或在制量按SN平均分。原预留只有同物料父记录已核实为CONSUMED且0≤consumed_quantity≤quantity时才允许继续；该终态按当前真实完工核销服务处理剩余释放，不能反推全部已消费。PENDING、仍RESERVED或未经核实的CANCELLED均出差异任务，等待真实释放/取消凭据。任何原批次引用都须是同物料且del_flag=0的正式批次，即使当前batch开关关闭也不接受已删除或未知批次。

首版每个实际查池范围只接受一条明确的历史正库存，同行零库存必须有相同已核实供应商/库区/基本单位等范围，金额和预留为0。多条成本来源落在同一查池范围、已有混合v1/v2池、原行有关联v2/未知版本/跨物料流水、原单位成本不等于该明确范围余额的六位平均成本，都输出待核实任务；不乱合并或改价。已有部分v2的混合物料也不自动转换。

工单关联库存即使可能已经完工，本首版也不凭wo_id猜测其可用性，需另核原业务后处理。无正库存的单纯策略开关、在制归属、复杂多来源成本池不属于本首版转换闭环。

历史qty>1且带旧sn_id的共享SN输入一律拒绝，输出LEGACY_MULTI_SN_REVIEW。原共享身份不能自动挑选其中一件继承；即使其被列入census或替换集合全部是REGISTERED，也不放行、不清零旧stock、不改旧IN_STOCK/stock_id、不生成新pool。原码及聚合流水保持歧义和历史缺口，须先凭真实单据与逐件归属证据单独处理。示例见shared-before.json/shared-plan.json/shared-review.json。原来qty=1、唯一原正库存且原码一致的身份可保留；原stock指针为空时仅允许凭本次精确实盘修复当前位置，不倒写历史。

## 生成的业务变化

[转换后快照](../../../script/tools/material_trace/examples/stock-conversion/after.json)和[完整日志](../../../script/tools/material_trace/examples/stock-conversion/journal.json)可逐项审查。

- 创建原范围的wm_valuation_group，冻结原数量、金额、单位成本、原库存ID、策略版本及census引用。
- 原库存清零数量和金额，保留旧号、SN引用及原维度，提升追踪版本并归入同池；同范围零行也映射，避免运行时查池再次遇到旧行而失败。
- 复制原维度建立每件1的当前SN库存；新行使用已有正式SN原码，正库存唯一。原单位成本保持；四位金额向下均分后按SN数值升序分配0.0001尾差。例如3件/10.0000得到3.3334、3.3333、3.3333。
- 更新现有正式身份的当前stock/仓库/储位/批次/IN_STOCK状态，identity_version递增；原注册命令、正式SN原码及原来源不变。
- 更新本物料SN开关和tracking_version。日志计算并校验整物料转换前后数量、金额相等，净数量0.0000、净金额0.0000，无会计凭证。

原wm_material_stock_tx没有INSERT/UPDATE/DELETE。转换不伪造收货、发货、逐件历史或损益。

## Census切点与审计桥

新`stock_conversion_bridges`冻结原库存前行、精确摘要、当前逐件映射、原流水摘要、计划及census。旧行末笔聚合流水余额通过已验证桥解释为本次当前库存身份转换，避免把旧行清零误报成流水丢失；总物料数量/金额仍直接与原流水对账，不用假期初或假收益填平。

审计始终报告`CENSUS_IDENTITY_HISTORY_BOUNDARY`：逐件身份追踪从此次实盘开始，过去的聚合流转没有因此补成逐件历史。原流水存在缺口时，原数量/金额差异仍为ERROR。桥或来源被篡改时报告INVALID_STOCK_CONVERSION_BRIDGE并停止用桥解释差额。后续v2业务按“原池基线+后续v2流水”对账，已用合成出库回归验证。

## 实际CLI

所有输出必须是新文件，不能覆盖输入或已有证据。以下从仓库根运行，仅处理合成样例；local-*输出名需不存在。

```powershell
$env:PYTHONUTF8='1'
python -B script/tools/material_trace/cli.py convert-check --snapshot script/tools/material_trace/examples/stock-conversion/before.json --plan script/tools/material_trace/examples/stock-conversion/plan.json
python -B script/tools/material_trace/cli.py convert-apply --snapshot script/tools/material_trace/examples/stock-conversion/before.json --plan script/tools/material_trace/examples/stock-conversion/plan.json --output script/tools/material_trace/examples/stock-conversion/local-after.json --journal-output script/tools/material_trace/examples/stock-conversion/local-journal.json
python -B script/tools/material_trace/cli.py convert-emit-sql --mode apply --snapshot script/tools/material_trace/examples/stock-conversion/before.json --journal script/tools/material_trace/examples/stock-conversion/journal.json --target erp:fixture_erp --backup-reference SYNTHETIC-BACKUP-ONLY --allow-db-writes --output script/tools/material_trace/examples/stock-conversion/local-apply.sql
python -B script/tools/material_trace/cli.py convert-revert --snapshot script/tools/material_trace/examples/stock-conversion/after.json --journal script/tools/material_trace/examples/stock-conversion/journal.json --output script/tools/material_trace/examples/stock-conversion/local-restored.json --journal-output script/tools/material_trace/examples/stock-conversion/local-reverted.json --compensation-output script/tools/material_trace/examples/stock-conversion/local-revert-report.json
python -B script/tools/material_trace/cli.py convert-emit-sql --mode revert --snapshot script/tools/material_trace/examples/stock-conversion/after.json --journal script/tools/material_trace/examples/stock-conversion/journal.json --target erp:fixture_erp --backup-reference SYNTHETIC-BACKUP-ONLY --allow-db-writes --output script/tools/material_trace/examples/stock-conversion/local-revert.sql
```

返回码0表示离线结果生成成功；3表示预检有差异任务或必须业务补偿；2表示输入/路径/授权参数不合法。`convert-apply --previous-journal`仅在同一计划、原转换后完整快照下幂等返回；已回退run_id不能重用。

[差异任务](../../../script/tools/material_trace/examples/stock-conversion/difference-tasks.json)来自预留库存真实CLI拒绝；[新业务后快照](../../../script/tools/material_trace/examples/stock-conversion/after-new-business.json)仅为合成后续出库，用来生成[补偿任务](../../../script/tools/material_trace/examples/stock-conversion/compensation.json)。新业务情况下convert-revert只输出compensation，不输出恢复快照或回退日志；convert-emit-sql也不生成恢复SQL，可用--compensation-output保存报告。

## 单库待审SQL与日志

[apply.sql](../../../script/tools/material_trace/examples/stock-conversion/apply.sql)和[revert.sql](../../../script/tools/material_trace/examples/stock-conversion/revert.sql)已由实际CLI生成，未执行。两者绑定显式ERP库、原server_uuid和备份引用，使用SERIALIZABLE、完整业务表范围锁/计数、全列行摘要、受影响行数检查及异常ROLLBACK。另核对conversion_run_states，包含其他物料已回退运行，防止漏掉转换来源状态。

新增`wm_trace_stock_conversion_run`独立审计表，保存目标/备份/cutover、计划与完整前后快照摘要、bridge_hash、不可变journal_json及journal_hash、可空revert_hash、状态/版本、时间；tenant/material/created_at索引。journal_json不含journal_hash自身，SHA2原UTF8字节等于独立列。回退保留原JSON/原hash，仅CAS状态APPLIED→REVERTED、version1→2并另存回退摘要。

apply先建原计价组、清零旧正库存再插入新SN正库存，避免唯一键冲突；在同一ERP事务内保存审计、业务值与物料策略。revert先移除本运行新增的SN当前行，再恢复原库存/身份/物料业务值，最后移除本运行新组；所有新行先核对完整原后镜像，原流水和原库存行从不删除。恢复业务值后仍保留REVERTED审计投影，因此不是删除审计痕迹的字节级原始文件恢复。

每次执行前必须用新鲜完整导出再次核实；不能持旧SQL绕过新业务检查。单ERP库SQL无法锁住另一MES数据源，MES事件/打印/消费者及ERP写入口必须维持已核实冻结窗口。输入JSON中的新MES任务会阻止生成回退SQL，但不能据此宣称已生成SQL能在执行时跨库原子验证MES。切换AUDIT期间也要维持门禁，后续STRICT审批及恢复业务另行核实。

## 验证与剩余边界

2026-10-19实际运行：`PYTHONUTF8=1 python -B -m unittest discover -s script/tools/material_trace/tests`共178例通过，无跳过；覆盖当前converter、策略审批导入、schema及真实CLI契约。TDD先复现缺实现、未绑定完整census、错误历史余额解释、漏转换审计/跨物料v2来源及空白批准等失败，再修复。合成样例有与当前converter及SQL生成器字节一致回归。

已覆盖整物料、多独立范围、尾差、原号/原流水、原物料规则、计价基线+后续v2效果、审批/前行/策略CAS、重复与跨租户、预留/在制/未知成本门禁、安全回退、状态变更补偿及真实CLI拒绝后不生成恢复文件。未运行全Maven，后续统一回归由主线程负责。

已在自有MySQL5.7.44合成库实际执行converter SQL，最终32/32通过，21个输入hash一致；未验证存量迁移、真实并发/死锁、大表范围锁时长、max_allowed_packet上限、长期大表内存占用、现场实盘/签名真实性或生产切换。approved_by与artifact/hash是受保护的明确审批输入，不等于工具已经认证审核人或验签；需外部审批流程核实原凭据。复杂成本范围、工单/在制、部分已v2场景只给差异任务，不虚构闭环。没有制作历史逐件DHR、会计凭证或Printer ACK。

2026-10-19续补已先复现未知/未关闭预留、非法消费数量、跨物料预留父记录及已删除/未知批次通过门禁的失败，再修复。当前离线178例通过；产品产出与MES取消/ACK及harness来源已纳入独立hash交接。converter真实MySQL结果见专门报告，Hume通用矩阵仍须按其最终源码输入复验。

取消DDL已核对ERP/MES实际实现，复用既有manifest及事件账，不新增猜测的取消字段。原四张inbox/outbox与manifest必须全部列导出，回执payload变化也是新来源状态；相关哈希见[schema交接](../../../script/tools/material_trace/examples/schema-handoff.json)。

本轮独立SQL验证保留十轮报告及最终32/32证据，包括两处真实SQL/行序失败与修复；共享多件SN现明确拒绝，冻结journal及原hash不变。完整覆盖、命令、报告哈希与数据库边界见[converter MySQL验证记录](material-trace-converter-mysql57-validation.md)，不替代Hume的通用SQL矩阵。


切换前还反向核对整个tenant/material的IN_STOCK声明：无正库存、重复或位置冲突输出IN_STOCK_IDENTITY_REVIEW，不允许留下旧stock已0而旧SN仍称在库。任何库位的零库存金额残留输出ZERO_QUANTITY_COST_REVIEW。所有原计价组按原冻结quantity/amount加已导出v2真实流水净效果，与其库存成员反向求和；孤立非零余额输出VALUATION_GROUP_REVIEW，0/0历史空组保留。审计同时报告ZERO_QUANTITY_COST_RESIDUE、ORPHAN_IN_STOCK_IDENTITY及VALUATION_GROUP_MISMATCH。转换不会消除或遮蔽这些差异。

主安装DDL与扩展阶段暂保留原租户列兼容性。正式切换仍须独立运行经批准的strict-constraints阶段关闭wm_sn/wm_material_stock的NULLtenant组合唯一键缺口；转换本身不执行DDL或自动切STRICT。条码与WO策略/采纳审批现在均在35表完整基线内，任何新增、停用或主对象字段变化都阻止旧journal恢复。
