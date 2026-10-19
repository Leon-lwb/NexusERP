# 物料身份与追溯数据库契约（2026-10-18）

状态：按用户批准计划完成结构同步；离线工具自身不连接数据库，未接触任何既有业务库。独立自有MySQL5.7合成库已验证converter、审批导入与严格租户SQL，见[实际验证边界](material-trace-converter-mysql57-validation.md)；目标环境迁移仍须独立审核。工作区为 `E:/Nexus/.worktrees/material-trace-20261018/nexus-erp`。

本文同时作为向主实现发送的字段协调记录。公共 `CompletionManifest`、`CompletionManifestLine`、`CompletionMaterialAllocation`、`MaterialTraceEvent` records 是跨模块字段来源。ERP库存真实表名为 `wm_material_stock` 和 `wm_material_stock_tx`。

初始SQL工具实施步骤：先建立纯Python合成回归；生成MySQL 5.7增量与同步主安装DDL；实现离线对账、证据修复、CAS及回退；输出受保护待审SQL；验证非零测试并记录未验证边界。该初始工具切片不修改Java、POM、UI；后续本轮授权的MES标签Java/PC与持久任务结构在下文单独登记，不修改其他业务Java。

- 发号：`wm_serial_reservation`、`wm_serial_sequence`、`wm_sn_alias` 使用任务指定字段；预留增加 `rule_snapshot LONGTEXT NOT NULL`，原规则表增加 `rule_version BIGINT NOT NULL DEFAULT 1`。`wm_sn.registration_command` 关联原命令，不补造历史命令。别名增加 `UNIQUE(tenant_id,command_id)`，一个命令只产生一个别名；外码按 `utf8mb4_bin` 区分大小写。
- 单据实物：`wm_document_sn(document_sn_id,tenant_id,command_id,line_no,source_doc_type,source_doc_id,source_doc_line_id,detail_version,identity_id,material_id,batch_id,source_stock_id,target_location_id,original_tx_id,posting_group_id,quantity,created_at)`。命令行号及单据行版本内身份分别唯一。
- 过账证明：`wm_trace_posting_group(posting_group_id,tenant_id,command_id,source_doc_type,source_doc_id,source_doc_line_id,detail_version,tx_type,material_id,valuation_group_id,quantity,amount,fingerprint,content_hash,source_hash,policy_version,original_posting_group_id,status,created_at,updated_at)`。命令唯一，单据行版本及事务类型唯一；补偿引用原组，新建业务事实。
- 原计价：`wm_valuation_group(valuation_group_id,tenant_id,material_id,warehouse_id,batch_id,source_stock_id,quantity,amount,unit_cost,valuation_method,policy_version,valuation_snapshot,created_at)`。冻结原数量、四位总金额及六位单位成本，不按SN重新计价。
- 最终交付：`pro_completion_manifest` 逐字段映射当前头record，并增加创建时间；`manifest_id` 为主键，`tenant_id+mes_wo_id+revision`唯一。`pro_completion_manifest_line` 增加 `tenant_id,manifest_id,revision` 定位头，其余逐字段映射行record；主键包含租户、清单、修订、行号。修订产生新清单身份，不覆盖旧清单。离线对账默认把 `GOOD/SCRAP` 作为合格/报废结果；SQL不设置业务CHECK，目标服务值域须在接入时逐项核实。分页上限500由公共/业务服务控制，SQL不假装执行分页校验。
- 组件账：`lt_sn_consumption` 的 `entry_id` 为分录主键；`tenant_id+command_id+line_no`唯一。数量存正绝对值，`CONSUME/REVERSE`决定方向，冲正必须引用原分录。`lt_sn_material_guard` 主键为租户、成品身份、组件物料。
- 批次分配：`lt_lot_allocation(allocation_id,tenant_id,command_id,line_no,lot_id,erp_batch_id,material_id,quantity,source_type,source_id,source_line_id,allocation_version,evidence_json,evidence_hash,created_at)`。命令行号唯一。`lt_lot_allocation.erp_batch_id`允许NULL，SN-only来源必须有ERP正式组件identityId、sourceStockId及quantity=1的原授权证据；batchManaged仍由事务守卫强制批次，不造批次。`lt_lot.erp_batch_id`只用于有一一证据的直接映射；拆分/合并分配保留在分配账，不用同码推断。
- 每库事件表：ERP `wm_trace_outbox/inbox`，MES `mes_trace_outbox/inbox`；事件ID为主键，租户+来源模块+聚合类型+聚合ID+聚合版本唯一。`payload/result_payload LONGTEXT`；租约、重试、摘要均在所属库事务内维护。已对照 `common-trace/jdbc/JdbcTraceJournal.java` 的实际INSERT/UPDATE核对全套20字段：事件ID与聚合类型均为64位（聚合类型包含ACK前缀）；`next_attempt_at`必须可空，兼容ACK重新激活；状态为可容纳NEW/WAITING/BROKER_SENT/APPLIED/CONFLICT的VARCHAR，不将发送成功当作应用成功。
- 每库修复日志：ERP `wm_trace_repair_run/item`，MES `mes_trace_repair_run/item`。运行保存目标、备份引用、计划/快照/日志摘要及状态版本；项保存包含租户的目标键、完整前后行、证据JSON和三份摘要。

增量脚本位于 `script/sql/update/2026-10-18-material-trace-{erp,mes}.sql`。完整字段类型与索引以这些脚本及 `script/tools/material_trace/schema_contract.py` 为准。只修改默认值使新SN默认为 `REGISTERED`，不更新历史状态或编码。库存 `active_sn_id` 仅在 `tracking_version>=2 AND stock_quantity>0` 时生成；重复新版活跃身份会中止迁移，不静默修复。旧版库存默认1；STRICT只能按物料完成审计后由授权业务流程切换。

MySQL 5.7不依靠CHECK校验业务值域；MES `mes_wo_id`只放宽可空，服务必须核实来源为SUBCONTRACT。迁移前必须由操作者设置已核实目标库与备份引用，不允许强制跳过错误。DDL可能隐式提交，失败后应核对已完成结构再重跑；主安装文件用于空库，绝不导入现有业务库。

标签最终契约已于同轮收到主实现确认：`mes_label_template.template_revision BIGINT NOT NULL DEFAULT 1`；打印日志新增可空的 `template_revision,original_print_log_id BIGINT`、`template_snapshot,field_snapshot,rendered_payload LONGTEXT`、`content_hash,payload_hash CHAR(64)`、`payload_charset,snapshot_mode VARCHAR(32)`、`reprint_reason VARCHAR(500)`。`retry_count`允许NULL，新行默认NULL表达未知；`result`保留VARCHAR以兼容SENT/FAIL/历史SUCCESS，不更新旧值。打印日志SN仅扩至128位，不改写原码。旧日志新增快照、摘要、模式和原因均留空，不把当前模板当作历史模板。SENT只表示TCP发送留痕，不等于实际出纸验收。

新增共25张表（ERP14/MES11），扩展10张既有表（ERP5/MES5）。两份只读预检文件 `2026-10-18-material-trace-preflight-{erp,mes}.sql` 不做结构或业务修复。增量对既有列的类型/长度/默认/可空/排序规则、生成列表达式及已有索引核对元数据；偏离契约立即停止，不静默吞掉CREATE IF NOT EXISTS造成的漂移。


2026-10-18同轮持久打印与完工用料协调已统一纳入generator，并同步两库主安装、增量及只读预检：

- `mes_label_print_task(task_id,tenant_id,task_key,request_fingerprint,sn_id,template_id,template_revision,print_source,original_print_log_id,reprint_reason,frozen_record,record_hash,status,attempt_count,lease_token,lease_until,sending_started_at,completed_at,last_error,created_at,updated_at)`。任务ID同时作为终态日志ID；`UNIQUE(tenant_id,task_key)`。自动任务键固定为租户+本地SN+模板ID+模板修订；手动requestId映射稳定键，另保存业务意图摘要，重复提交不得更换原任务或补打原因。`frozen_record LONGTEXT NOT NULL`保存模板/字段/内容与载荷摘要、精确载荷及编码、路由、来源、操作人、原任务和原因，整份记录另存`record_hash CHAR(64) ascii_bin NOT NULL`。业务字段仅INSERT，状态维护不会重新读当前模板。
- `wm_trace_audit_run(audit_id VARCHAR(64) ascii_bin PK,tenant_id VARCHAR(20) NOT NULL,material_id BIGINT NULL,status VARCHAR(24) NOT NULL DEFAULT 'REVIEW_REQUIRED',summary TEXT,report_json LONGTEXT NOT NULL,content_hash CHAR(64) ascii_bin NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)`；`KEY(tenant_id,created_at)`。仅保存审计工件，不更新原业务行。
- `pro_completion_manifest.material_line_count BIGINT NULL`和`material_hash CHAR(64) ascii_bin NULL`：旧清单保持NULL，不能写0或按当前数据回填假完整。已存在的早期清单表只在缺列时ADD COLUMN，再核对定义。
- `pro_completion_material_allocation(tenant_id,manifest_id,revision,allocation_id,material_id,reservation_id,reservation_line_id,posting_group_id,identity_id,source_stock_id,erp_batch_id,quantity,source_version,evidence_hash,source_event_id)`；主键为`(tenant_id,manifest_id,revision,allocation_id)`。`allocation_id`固定为原实际`lt_wo_consumption.consumption_id`，不是额度`lt_lot_allocation.allocation_id`；reservation/line/posting/identity/stock/batch引用可空，`quantity DECIMAL(18,4)`、`source_version BIGINT`、`evidence_hash CHAR(64) ascii_bin`、`source_event_id VARCHAR(64) ascii_bin`均NOT NULL。ERP批次可空以兼容正式SN-only组件来源。
- `wm_trace_material_reservation(reservation_id,tenant_id,command_id,erp_wo_id,mes_wo_id,requirement_detail_id,material_id,policy_version,quantity,content_hash,status)`，原预留ID主键、租户命令唯一；索引覆盖租户+工单+状态及租户+需求行+状态。`wm_trace_material_reservation_line(reservation_line_id,tenant_id,reservation_id,line_no,source_stock_id,identity_id,erp_batch_id,valuation_group_id,quantity,consumed_quantity,version)`，原行ID主键、租户+预留+行号唯一，真实来源库存NOT NULL，identity/batch/valuation可空，消费数量默认0、版本默认1。
- `wm_trace_material_consumption(consumption_id,tenant_id,manifest_id,manifest_revision,allocation_id,erp_wo_id,material_id,reservation_id,reservation_line_id,posting_group_id,identity_id,source_stock_id,erp_batch_id,quantity,source_version,evidence_hash,source_event_id)`；ERP接收记录主键，租户+清单+修订+原实际消耗分配唯一，不额外跨修订限制。引用可空、数量和来源版本/摘要/事件NOT NULL；字段逐项对照当前`WmTraceAuthorizationService`和`WmTraceMaterialConsumptionService`的实际INSERT/UPDATE/重放查询。

`lt_lot_allocation.erp_batch_id`既有NOT NULL只在确认为无默认/无生成表达式的有符号BIGINT时MODIFY为NULL，其他漂移停止；不转换业务值、不伪造批次。新用料分配及ERP预留行/消耗的ERP批次列也允许NULL。

打印任务由同步造码事件在原可写MES事务中落账；原afterCommit内存发送路径已移除。任务提交后才短事务领取，网络写出不持有事务；只允许`PENDING/attempt_count=0`领取一次。120秒租约到期仅封存UNKNOWN，独立回收调度不依赖发送线程；终态与审计同事务提交，晚到工作线程不能覆盖UNKNOWN。显式补打须原任务/旧日志引用及非空原因，保留原冻结内容；缺快照旧日志只能以LEGACY_RECORD明确标注当前模板降级。SENT与历史SUCCESS都不表示打印机确认出纸，UNKNOWN不得自动重打，不触碰signedDHR。

离线验证：`PYTHONUTF8=1 python -B -m unittest discover -s script/tools/material_trace/tests`共100例通过，其中SQL契约27例、来源安全13例；schema generator重复生成一致。标签Java另以`javac --release 17`隔离编译并实际运行JUnit：79例通过，失败/跳过均0；运行工作目录为`ruoyi-modules/ruoyi-mes`，夹具向上定位本工作树DDL，覆盖Surefire路径回归。编译仅有既存SpringStaticBootstrap两条unchecked警告；没有写入Maven target或运行全Maven。临时编译目录与验证脚本已清理。此结果不代表真实MySQL 5.7存量迁移、生产部署或现场打印验收。本轮没有连接或写入现有库。

2026-10-19集成补充：`lt_wo_consumption.source_allocation_id BIGINT NULL`及`KEY(tenant_id,source_allocation_id)`已加入既有表扩展，旧行保持NULL，不从额度分配猜测实际消耗来源；该引用与完工分配的实际consumption_id语义不同。新增来源账只读metadata与完整快照范围同步到30表：打印任务、完工实际用料分配、ERP实际预留头/行及消耗、发号预留/序列都必须明确导出，即使为空。上述7新来源表与既有实际消耗表均要求全部列，不能省略状态、租约、消费量或source_allocation_id。仍只有原5张映射表可受证据修复，来源账不加入写白名单。任何新增来源行、同行状态/数量/序列或来源引用变化都会触发COMPENSATION_REQUIRED；旧23表journal不能通过补空表升级成可直接回退的日志。SQL在所属数据源对全部来源范围计数加锁，并逐行比较完整状态摘要；跨库仍必须另行保证停写及一致边界。

离线工具、操作边界及验证证据见 [历史对账与修复指南](material-trace-repair.md)。

2026-10-19后续当前库存converter补充：新增ERP `wm_trace_stock_conversion_run`，generator当前共30张新表（ERP18/MES12），包括同轮实际产品产出与手工身份退休审计，原25表字段不删减；converter独立保存完整前后镜像日志、目标/备份、census切点、计划/桥/快照摘要及状态CAS，回退不覆盖原journal_json。35业务表完整快照之外，converter强制导出全部转换运行的conversion_run_states，包含REVERTED；不扩展原repair五表写权限。生成器/主安装/增量/metadata已同步，脚本未执行。

同轮核对ERP当前三条过账证明INSERT：`wm_trace_posting_group`首次正式建表补齐`source_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL`与`policy_version BIGINT NOT NULL`。旧中间态表缺列会由完整字段守卫停止，不为legacy proof默认或推算回填。当前离线套件178例通过，扩展后的实际验证与来源hash见工具README；converter细节见[当前库存转换指南](material-trace-stock-conversion.md)。

2026-10-19产出/生命周期协调：`pro_product_lot_output(output_id,tenant_id,command_id,line_no,mes_wo_id,card_id,step_record_id,lot_id,source_allocation_id,material_id,quantity,result,source_version,created_by,created_at)`逐项对应当前MesProductLotOutputWriter及其真实测试契约。主键output_id，租户+命令+行号唯一、租户+产品lot唯一，索引覆盖工单报工顺序、工序记录及原额度分配；quantity为DECIMAL(18,4)，source_version为BIGINT。没有冗余erp_batch_id：召回批次通过原source_allocation_id连接lt_lot_allocation取得，授权额度不等于GOOD/SCRAP实际质量结果，不回填旧产品产出。

`wm_serial_lifecycle_command`逐项采用Nietzsche交接的20列：operation_id,tenant_id,command_id,fingerprint,action,identity_id,reservation_id,actor_id,reason,before_version,after_version,before_status,after_status,reservation_status,source_type,source_id,before_json,after_json,result_json,created_at。主键operation_id、UNIQUE(tenant_id,command_id)、KEY(tenant_id,identity_id,created_at)；fingerprint为ascii_bin CHAR(64)，原来源ID可空，三个冻结JSON均LONGTEXT NOT NULL，人工原因VARCHAR(500) NOT NULL，不覆盖原镜像或回收号码。当前实际INSERT及其余追溯INSERT均有源代码列契约回归。

上述两账已加入metadata、必需完整导出及只读来源集合，业务快照现为35表；原repair五表写白名单不变。新增产出/退休命令或同行数量、原因、引用、版本等变化都会阻止旧journal直接回退；样例JSON、日志及单库待审SQL已由真实CLI重新生成。新增业务表若遗漏完整快照或metadata会触发契约测试失败。

取消链路以落盘代码核对：MES取消请求及ACK投影复用pro_completion_manifest现有VARCHAR状态、原清单修订和mes_trace_outbox/inbox冻结payload/result_payload；CANCEL_REQUESTED/CANCELLED不需要枚举列迁移。MesCompletionCancellationService、MesTraceStore、MesProductLotOutputWriter及harness自身代码已纳入MySQL输入快照，召回两个布尔SQL分支完整展开，consumption_id稳定分页，产品批次只读原额度来源。Hume的ErpCompletionCancellationService/Handler及公共CompletionCancellationRequest/Result/Proof/Port、JdbcTraceJournal现已落盘并逐列核对：ERP取消通过preemptCompletion竞争原inbox终态和真实工单锁，只复用既有事件列，没有新增取消表或列。四张inbox/outbox及pro_completion_manifest已强制全部列投影，取消ACK即使不改变APPLIED状态也会因冻结result_payload变化阻止旧journal及SQL直接回退。ERP取消服务6条实际SELECT也已纳入SQL解析/EXPLAIN矩阵；公共journal的动态表名SQL仅纳入来源快照及列核对，不伪装成已执行Java事务验证。主线程应在所有owner源码冻结后重跑最新MySQL harness；离线工具不连接数据库，独立自有合成MySQL验证另见专门报告。字段/来源哈希交接见[schema-handoff.json](../../../script/tools/material_trace/examples/schema-handoff.json)。


## WO策略、采纳审批与条码主对象

当前本轮新表30张（ERP18/MES12），35张必需业务快照、18张全部列只读来源表；以schema_contract.py、metadata.py和core.py为准，不扩大原5表repair写集。

- `wm_work_order_tracking_policy`采用实际writer的16列，唯一(tenant_id,erp_wo_id,material_id)，冻结原策略JSON/hash、原来源JSON/hash、整组hash、provenance、evidence_ref及datetime(3)时点；旧WO不填假默认，缺失保持UNKNOWN。
- `wm_work_order_policy_adoption`采用实际服务18列，主键adoption_id、唯一(tenant_id,command_id)，包含expected_wo_hash、原策略归档、独立approval及各自hash、request_hash、不同的申请/批准人、批准时间窗、status/version和可空applied_at/snapshot_hash。默认PENDING_REVIEW，离线只能凭原批准导入APPROVED；实际执行由Kant服务CAS到APPLIED并在当时未来生效，不倒写历史。
- 既有`wm_barcode`新增`primary_object_type VARCHAR(16) utf8mb4_bin NULL`，SN/BATCH/MATERIAL/DOCUMENT的业务校验由主实现负责，DOCUMENT不扩原六类白名单。旧NULL不回填；stock_id仅为当前引用，不推定主对象。完整29列包含该字段，任何停用、主体/原码等变化均参与CAS。

## 清理后严格租户约束

独立生成入口为`python -B script/tools/material_trace/schema_contract.py --strict-constraints`，输出`script/sql/update/2026-10-19-material-trace-strict-constraints-erp.sql`；默认完整生成也同步该文件。主安装/扩展阶段保留原两表兼容NULL，不能据扩展成功宣称严格唯一已闭环。

操作者必须显式设置`@material_trace_expected_schema`、`@material_trace_expected_server_uuid`、`@material_trace_backup_reference`、`@material_trace_maintenance_reference`以及`@material_trace_strict_tenant_cleanup_confirmed='TENANT_CLEANUP_VERIFIED'`。两表NULL/空白租户、原码冲突、新版正库存身份缺失/重复、生成列表达式及两个唯一键完整预检全部发生在首个ALTER前；任一失败即停，绝不回填未知租户。确认后仅将wm_sn与wm_material_stock.tenant_id改成NOT NULL并去掉默认租户。重复执行安全，所有业务行不变。

MySQL5.7 DDL隐式提交；独占停写和可恢复备份必须另行核实，第二个ALTER失败时保全已有结构并审查恢复，不声称事务能回滚DDL。此阶段只收紧这两张身份/库存主表的租户列，不冒称所有旧表都已完成约束升级。真实NULL拒绝/旧冲突停止的自有MySQL证据见[独立验证报告](material-trace-converter-mysql57-validation.md)。
