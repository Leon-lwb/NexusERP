"""物料追溯的 SQL 唯一来源。仅渲染文件，绝不连接或执行数据库。"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[3]
SQL = ROOT / "script/sql"

def col(name, definition, comment):
    return f"  `{name}` {definition} COMMENT '{comment}'"

def table(name, columns, keys, comment):
    return (f"CREATE TABLE IF NOT EXISTS `{name}` (\n" + ",\n".join(columns + keys)
            + f"\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='{comment}';")

def c(spec):
    return [col(*line) for line in spec]

ID = "bigint(20) NOT NULL"
OPT_ID = "bigint(20) DEFAULT NULL"
TENANT = ("tenant_id", "varchar(20) NOT NULL", "租户ID，必须由认证上下文提供")
COMMAND = ("command_id", "varchar(128) COLLATE utf8mb4_bin NOT NULL", "幂等业务命令ID")
CREATED = ("created_at", "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP", "创建时间")
UPDATED = ("updated_at", "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", "更新时间")
HASH = "char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL"
OPT_HASH = "char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL"
TABLES = {"erp": {}, "mes": {}}

def add(ds, name, spec, keys, comment):
    TABLES[ds][name] = table(name, c(spec), keys, comment)

add("erp", "wm_serial_reservation", [
    ("reservation_id", ID, "发号预留ID"), TENANT, COMMAND,
    ("fingerprint", HASH, "规范请求摘要，相同命令不能改变参数"),
    ("material_id", ID, "物料ID"), ("quantity", "int(11) NOT NULL", "本次预留件数"),
    ("rule_id", OPT_ID, "号码规则ID"), ("rule_version", "bigint(20) NOT NULL DEFAULT 1", "预留时规则版本"),
    ("rule_snapshot", "longtext NOT NULL", "预留时冻结规则JSON，重试不得读取新规则"),
    ("source_type", "varchar(32) COLLATE utf8mb4_bin NOT NULL", "业务来源类型"),
    ("source_id", OPT_ID, "业务来源ID"), ("cycle_key", "varchar(32) COLLATE utf8mb4_bin NOT NULL", "号段周期键"),
    ("biz_date", "date NOT NULL", "发号业务日期"),
    ("status", "varchar(24) NOT NULL DEFAULT 'RESERVED'", "预留状态，取消不回收号码"), CREATED, UPDATED
], ["  PRIMARY KEY (`reservation_id`)", "  UNIQUE KEY `uk_serial_reservation_command` (`tenant_id`,`command_id`)",
    "  KEY `idx_serial_reservation_source` (`tenant_id`,`source_type`,`source_id`)"], "正式实物发号预留，号码永久占用")
# 对应registry手工退休契约；身份状态和不可变审计同ERP事务，号码永不回收。
add("erp", "wm_serial_lifecycle_command", [
    ("operation_id", ID, "正式身份生命周期操作ID"), TENANT, COMMAND,
    ("fingerprint", HASH, "身份、操作人、原因、版本及动作的规范摘要"),
    ("action", "varchar(32) NOT NULL", "RETIRE_UNUSED_MANUAL等已核实生命周期动作"),
    ("identity_id", ID, "原正式身份wm_sn.sn_id"), ("reservation_id", ID, "原正式发号预留ID"),
    ("actor_id", ID, "认证上下文中的操作人"), ("reason", "varchar(500) NOT NULL", "明确人工退休原因"),
    ("before_version", ID, "原身份版本"), ("after_version", ID, "动作提交后的身份版本"),
    ("before_status", "varchar(16) NOT NULL", "原身份状态"), ("after_status", "varchar(16) NOT NULL", "动作提交后的身份状态"),
    ("reservation_status", "varchar(24) NOT NULL", "动作提交后的原预留状态，取消不回收号码"),
    ("source_type", "varchar(32) COLLATE utf8mb4_bin NOT NULL", "核实后的原注册来源类型"),
    ("source_id", OPT_ID, "原业务来源ID，ERP_MANUAL为空"),
    ("before_json", "longtext NOT NULL", "完整原身份冻结JSON"),
    ("after_json", "longtext NOT NULL", "动作后身份冻结JSON"),
    ("result_json", "longtext NOT NULL", "同命令幂等回执冻结JSON"), CREATED
], ["  PRIMARY KEY (`operation_id`)",
    "  UNIQUE KEY `uk_serial_lifecycle_command` (`tenant_id`,`command_id`)",
    "  KEY `idx_serial_lifecycle_identity` (`tenant_id`,`identity_id`,`created_at`)"], "正式SN生命周期命令审计，状态与审计同ERP事务提交，不回收号码")
add("erp", "wm_serial_sequence", [
    ("sequence_id", ID, "号段计数器ID"), TENANT,
    ("namespace", "varchar(32) COLLATE utf8mb4_bin NOT NULL", "计数器命名空间"),
    ("rule_key", "varchar(64) COLLATE utf8mb4_bin NOT NULL", "规则或共享范围键"),
    ("cycle_key", "varchar(32) COLLATE utf8mb4_bin NOT NULL", "周期键"),
    ("current_value", "bigint(20) NOT NULL DEFAULT 0", "已分配最大序号，只增不回收"),
    ("version", "bigint(20) NOT NULL DEFAULT 1", "乐观锁版本"), CREATED, UPDATED
], ["  PRIMARY KEY (`sequence_id`)", "  UNIQUE KEY `uk_serial_sequence_scope` (`tenant_id`,`namespace`,`rule_key`,`cycle_key`)"], "事务号段计数器")
add("erp", "wm_sn_alias", [
    ("alias_id", ID, "别名ID"), TENANT, ("identity_id", ID, "正式身份wm_sn.sn_id"),
    ("source_type", "varchar(32) COLLATE utf8mb4_bin NOT NULL", "供应商、客户或旧凭据来源类型"),
    ("source_id", "bigint(20) NOT NULL DEFAULT 0", "来源主体ID，无主体使用0避免空值绕过唯一键"),
    ("material_id", ID, "物料ID"),
    ("alias_code", "varchar(128) COLLATE utf8mb4_bin NOT NULL", "外部原码，区分大小写且不改写"),
    COMMAND, CREATED
], ["  PRIMARY KEY (`alias_id`)",
    "  UNIQUE KEY `uk_sn_alias_identity_source` (`tenant_id`,`identity_id`,`source_type`,`source_id`,`material_id`,`alias_code`)",
    "  UNIQUE KEY `uk_sn_alias_command` (`tenant_id`,`command_id`)",
    "  KEY `idx_sn_alias_lookup` (`tenant_id`,`alias_code`)"], "外部原码与正式实物身份的证据映射")
add("erp", "wm_document_sn", [
    ("document_sn_id", ID, "单据实物明细ID"), TENANT, COMMAND,
    ("line_no", "bigint(20) NOT NULL", "命令内稳定行号"),
    ("source_doc_type", "varchar(32) COLLATE utf8mb4_bin NOT NULL", "来源单据类型"),
    ("source_doc_id", ID, "来源单据ID"), ("source_doc_line_id", "bigint(20) NOT NULL DEFAULT 0", "来源单据行ID"),
    ("detail_version", "bigint(20) NOT NULL DEFAULT 1", "提交时单据明细版本"),
    ("identity_id", ID, "正式实物身份"), ("material_id", ID, "物料ID"),
    ("batch_id", OPT_ID, "ERP批次ID"), ("source_stock_id", OPT_ID, "来源库存ID"),
    ("target_location_id", OPT_ID, "目标储位ID"), ("original_tx_id", OPT_ID, "退回或冲正所引用的原事务ID"),
    ("posting_group_id", OPT_ID, "过账证明组ID"),
    ("quantity", "decimal(18,4) NOT NULL DEFAULT 1.0000", "基本单位数量，SN行由服务校验为1"), CREATED
], ["  PRIMARY KEY (`document_sn_id`)", "  UNIQUE KEY `uk_document_sn_command_line` (`tenant_id`,`command_id`,`line_no`)",
    "  UNIQUE KEY `uk_document_sn_version_identity` (`tenant_id`,`source_doc_type`,`source_doc_id`,`source_doc_line_id`,`detail_version`,`identity_id`)",
    "  KEY `idx_document_sn_identity` (`tenant_id`,`identity_id`)"], "版本化单据实物明细，不改写历史凭据")
add("erp", "wm_trace_posting_group", [
    ("posting_group_id", ID, "过账证明组ID"), TENANT, COMMAND,
    ("source_doc_type", "varchar(32) COLLATE utf8mb4_bin NOT NULL", "来源单据类型"),
    ("source_doc_id", ID, "来源单据ID"), ("source_doc_line_id", "bigint(20) NOT NULL DEFAULT 0", "来源单据行ID"),
    ("detail_version", "bigint(20) NOT NULL DEFAULT 1", "冻结明细版本"),
    ("tx_type", "varchar(64) COLLATE utf8mb4_bin NOT NULL", "库存业务类型"),
    ("material_id", ID, "物料ID"), ("valuation_group_id", OPT_ID, "原计价组ID"),
    ("quantity", "decimal(18,4) NOT NULL", "冻结基本单位数量"),
    ("amount", "decimal(18,4) NOT NULL", "冻结四位金额，单件拆分合计保持不变"),
    ("fingerprint", HASH, "幂等请求摘要"), ("content_hash", HASH, "冻结明细内容摘要"),
    ("source_hash", HASH, "冻结原业务单据行内容摘要，不补造历史来源"),
    ("policy_version", ID, "冻结时物料追踪策略版本，不推定旧证明版本"),
    ("original_posting_group_id", OPT_ID, "补偿所引用的原过账组ID"),
    ("status", "varchar(24) NOT NULL DEFAULT 'PREPARED'", "过账状态"), CREATED, UPDATED
], ["  PRIMARY KEY (`posting_group_id`)", "  UNIQUE KEY `uk_trace_posting_command` (`tenant_id`,`command_id`)",
    "  UNIQUE KEY `uk_trace_posting_document_version` (`tenant_id`,`source_doc_type`,`source_doc_id`,`source_doc_line_id`,`detail_version`,`tx_type`)"], "原子库存过账证明，补偿另建新组")
add("erp", "wm_valuation_group", [
    ("valuation_group_id", ID, "计价组ID"), TENANT, ("material_id", ID, "物料ID"),
    ("warehouse_id", ID, "原计价仓库ID"), ("batch_id", OPT_ID, "原计价批次ID"),
    ("source_stock_id", OPT_ID, "拆分前原库存ID"),
    ("quantity", "decimal(18,4) NOT NULL", "原计价数量"),
    ("amount", "decimal(18,4) NOT NULL", "原四位总金额"),
    ("unit_cost", "decimal(18,6) NOT NULL", "冻结原单位成本，禁止按SN重新计价"),
    ("valuation_method", "varchar(32) NOT NULL DEFAULT 'MOVING_AVERAGE'", "原计价方式"),
    ("policy_version", "bigint(20) NOT NULL DEFAULT 1", "原计价策略版本"),
    ("valuation_snapshot", "longtext NOT NULL", "冻结计价依据JSON"), CREATED
], ["  PRIMARY KEY (`valuation_group_id`)", "  KEY `idx_valuation_group_material` (`tenant_id`,`material_id`,`warehouse_id`,`batch_id`)"], "原计价范围冻结快照，拆分数量金额守恒")
add("mes", "pro_completion_manifest", [
    ("manifest_id", ID, "最终交付清单ID"), ("revision", "int(11) NOT NULL DEFAULT 1", "冻结修订号"), TENANT,
    ("mes_wo_id", ID, "MES工单ID"), ("erp_wo_id", OPT_ID, "ERP工单ID"), ("material_id", ID, "成品物料ID"),
    ("line_count", "bigint(20) NOT NULL", "冻结清单行数"),
    ("good_qty", "decimal(18,4) NOT NULL", "合格基本单位数量"),
    ("scrap_qty", "decimal(18,4) NOT NULL DEFAULT 0.0000", "报废基本单位数量"),
    ("content_hash", HASH, "冻结清单规范内容SHA256"),
    ("material_line_count", OPT_ID, "实际用料分配行数，旧清单未知保持NULL"),
    ("material_hash", OPT_HASH, "实际用料分配规范内容SHA256，旧清单不回填"),
    ("status", "varchar(24) NOT NULL DEFAULT 'FROZEN'", "冻结交付状态"), CREATED
], ["  PRIMARY KEY (`manifest_id`)", "  UNIQUE KEY `uk_completion_manifest_revision` (`tenant_id`,`mes_wo_id`,`revision`)"], "最终工单一次交付的不可变修订清单")
add("mes", "pro_completion_manifest_line", [TENANT,
    ("manifest_id", ID, "冻结清单ID"), ("revision", "int(11) NOT NULL", "冻结修订号"),
    ("line_no", "bigint(20) NOT NULL", "稳定清单行号"), ("identity_id", OPT_ID, "ERP正式实物身份，非SN行可空"),
    ("local_sn_id", OPT_ID, "MES本地SN主键"), ("sn_code", "varchar(128) DEFAULT NULL", "冻结正式SN原码"),
    ("batch_id", OPT_ID, "ERP批次ID"), ("local_lot_id", OPT_ID, "MES本地批次ID"), ("material_id", ID, "物料ID"),
    ("quantity", "decimal(18,4) NOT NULL", "基本单位数量，SN行一件"),
    ("result", "varchar(16) NOT NULL", "质量结果GOOD或SCRAP"),
    ("source_version", "bigint(20) NOT NULL", "冻结时来源身份或批次版本")
], ["  PRIMARY KEY (`tenant_id`,`manifest_id`,`revision`,`line_no`)",
    "  UNIQUE KEY `uk_manifest_identity` (`tenant_id`,`manifest_id`,`revision`,`identity_id`)"], "公共CompletionManifestLine逐字段映射")
add("mes", "lt_sn_consumption", [
    ("entry_id", ID, "组件消耗账分录ID"), TENANT, COMMAND, ("line_no", "bigint(20) NOT NULL", "命令内稳定行号"),
    ("identity_id", ID, "成品正式实物身份"), ("local_sn_id", ID, "MES本地成品SN主键"),
    ("component_material_id", ID, "组件物料ID"), ("component_lot_id", ID, "组件MES批次ID"),
    ("quantity", "decimal(18,4) NOT NULL", "正数绝对数量，方向由分录类型确定"),
    ("wo_consumption_id", ID, "原工单消耗记录lt_wo_consumption主键"),
    ("process_id", OPT_ID, "工序ID"), ("operation_ref", "varchar(128) COLLATE utf8mb4_bin NOT NULL", "工位操作或离线操作凭据"),
    ("entry_type", "varchar(16) NOT NULL", "CONSUME消耗或REVERSE冲正，不覆盖原分录"),
    ("original_entry_id", OPT_ID, "冲正所引用的原CONSUME分录"),
    ("policy_version", "bigint(20) NOT NULL DEFAULT 1", "消耗时混批策略版本"),
    ("created_by", ID, "操作人ID"), CREATED
], ["  PRIMARY KEY (`entry_id`)", "  UNIQUE KEY `uk_sn_consumption_command_line` (`tenant_id`,`command_id`,`line_no`)",
    "  KEY `idx_sn_consumption_identity` (`tenant_id`,`identity_id`,`component_material_id`)",
    "  KEY `idx_sn_consumption_source` (`tenant_id`,`wo_consumption_id`)",
    "  KEY `idx_sn_consumption_reverse` (`tenant_id`,`original_entry_id`)"], "实物组件消耗不可变流水账，冲正必须引用原分录")
add("mes", "lt_sn_material_guard", [TENANT, ("identity_id", ID, "成品正式身份"),
    ("component_material_id", ID, "组件物料ID"), ("selected_lot_id", OPT_ID, "禁止混批时首次选定批次"),
    ("allow_mixed_lot", "tinyint(1) NOT NULL DEFAULT 1", "冻结混批策略，默认允许"),
    ("policy_version", "bigint(20) NOT NULL DEFAULT 1", "策略版本"),
    ("version", "bigint(20) NOT NULL DEFAULT 1", "并发互斥与乐观锁版本")
], ["  PRIMARY KEY (`tenant_id`,`identity_id`,`component_material_id`)"], "同实物同组件物料的混批并发守卫")
add("mes", "lt_lot_allocation", [
    ("allocation_id", ID, "批次分配ID"), TENANT, COMMAND, ("line_no", "bigint(20) NOT NULL", "命令内分配行号"),
    ("lot_id", ID, "MES本地批次ID"), ("erp_batch_id", OPT_ID, "有证据的ERP批次ID，SN-only来源允许空值"), ("material_id", ID, "物料ID"),
    ("quantity", "decimal(18,4) NOT NULL", "分配基本单位数量"),
    ("source_type", "varchar(32) COLLATE utf8mb4_bin NOT NULL", "分配凭据来源类型"),
    ("source_id", ID, "分配凭据ID"), ("source_line_id", "bigint(20) NOT NULL DEFAULT 0", "凭据行ID"),
    ("allocation_version", "bigint(20) NOT NULL DEFAULT 1", "批次分配版本"),
    ("evidence_json", "longtext NOT NULL", "原凭据及分配依据JSON，不能仅用同码匹配"),
    ("evidence_hash", HASH, "冻结证据摘要"), CREATED
], ["  PRIMARY KEY (`allocation_id`)", "  UNIQUE KEY `uk_lot_allocation_command_line` (`tenant_id`,`command_id`,`line_no`)",
    "  KEY `idx_lot_allocation_mapping` (`tenant_id`,`lot_id`,`erp_batch_id`,`allocation_version`)"], "MES批次到ERP批次的带证据数量分配")

for ds, prefix in (("erp", "wm"), ("mes", "mes")):
    for kind in ("outbox", "inbox"):
        add(ds, prefix+"_trace_"+kind, [
            ("event_id", "varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL", "跨库稳定事件ID"), TENANT,
            ("source_module", "varchar(16) COLLATE utf8mb4_bin NOT NULL", "事件源模块"),
            ("aggregate_type", "varchar(64) COLLATE utf8mb4_bin NOT NULL", "来源业务聚合类型"),
            ("aggregate_id", ID, "来源业务聚合ID"), ("aggregate_version", "bigint(20) NOT NULL", "同来源聚合单调版本"),
            ("event_type", "varchar(64) NOT NULL", "事件类型"), ("schema_version", "int(11) NOT NULL DEFAULT 1", "消息契约版本"),
            ("payload", "longtext NOT NULL", "原始冻结载荷JSON，禁止重试时重新计算"), ("content_hash", HASH, "原始载荷UTF8字节SHA256"),
            ("status", "varchar(24) NOT NULL DEFAULT 'NEW'", "NEW、WAITING、BROKER_SENT、APPLIED或CONFLICT状态"),
            ("attempts", "int(11) NOT NULL DEFAULT 0", "已尝试次数"),
            ("next_attempt_at", "datetime DEFAULT NULL", "下次可重试时间，ACK重新激活时允许空值"),
            ("lease_owner", "varchar(128) COLLATE utf8mb4_bin DEFAULT NULL", "当前租约持有者"),
            ("lease_until", "datetime DEFAULT NULL", "租约截止时间"),
            ("last_error", "text", "脱敏后的最近错误"), ("result_payload", "longtext", "幂等消费结果JSON"),
            CREATED, UPDATED, ("applied_at", "datetime DEFAULT NULL", "成功应用时间")
        ], ["  PRIMARY KEY (`event_id`)",
            "  UNIQUE KEY `uk_trace_event_stream_version` (`tenant_id`,`source_module`,`aggregate_type`,`aggregate_id`,`aggregate_version`)",
            "  KEY `idx_trace_event_retry` (`status`,`next_attempt_at`,`lease_until`)",
            "  KEY `idx_trace_event_tenant` (`tenant_id`,`created_at`)"], "本数据源事务内冻结的追溯"+("发件箱" if kind=="outbox" else "幂等收件箱"))
    add(ds, prefix+"_trace_repair_run", [
        ("run_id", "varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL", "修复运行ID"),
        ("target_database", "varchar(64) NOT NULL", "显式核实的目标业务库"),
        ("target_environment", "varchar(128) NOT NULL", "环境标识，不保存口令或连接串"),
        ("backup_reference", "varchar(255) NOT NULL", "已核实的离线备份引用"),
        ("plan_hash", HASH, "批准计划摘要"), ("before_snapshot_hash", HASH, "完整修复前导出摘要"),
        ("after_snapshot_hash", HASH, "离线预演结果摘要"), ("journal_hash", HASH, "批准运行日志摘要"),
        ("status", "varchar(24) NOT NULL DEFAULT 'PLANNED'", "PLANNED、APPLIED、REVERTED或待补偿"),
        ("version", "bigint(20) NOT NULL DEFAULT 1", "运行状态CAS版本"), CREATED, UPDATED
    ], ["  PRIMARY KEY (`run_id`)"], "本数据源历史修复运行日志，跨库不伪装原子事务")
    add(ds, prefix+"_trace_repair_item", [
        ("run_id", "varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL", "修复运行ID"),
        ("item_id", "varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL", "计划内修复项ID"), TENANT,
        ("target_table", "varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL", "白名单目标表"),
        ("target_key", "longtext NOT NULL", "包含租户的完整主键JSON"),
        ("before_json", "longtext NOT NULL", "修复前完整行JSON"), ("after_json", "longtext NOT NULL", "修复后完整行JSON"),
        ("evidence_json", "longtext NOT NULL", "冻结来源凭据及审核依据JSON"),
        ("before_hash", HASH, "修复前规范行摘要，CAS比较依据"), ("after_hash", HASH, "修复后规范行摘要"),
        ("evidence_hash", HASH, "证据摘要"),
        ("status", "varchar(24) NOT NULL DEFAULT 'PLANNED'", "修复项状态"),
        ("version", "bigint(20) NOT NULL DEFAULT 1", "修复项CAS版本"), CREATED, UPDATED
    ], ["  PRIMARY KEY (`run_id`,`item_id`)", "  KEY `idx_trace_repair_target` (`tenant_id`,`target_table`)"], "历史修复前后镜像与证据，不覆盖原业务凭据")

# 标签任务与审计工件均为新增账，不回填旧业务或把历史发送伪装成设备确认。
add("mes", "mes_label_print_task", [
    ("task_id", ID, "打印任务ID，同时作为本次终态打印日志ID"), TENANT,
    ("task_key", HASH, "租户内稳定任务键，自动任务固定SN及模板版本"),
    ("request_fingerprint", HASH, "提交业务意图摘要，命令不得更换原任务或补打原因"),
    ("sn_id", OPT_ID, "MES本地SN，测试任务可空"),
    ("template_id", ID, "请求打印模板ID"),
    ("template_revision", OPT_ID, "冻结修订，历史未知保持空值"),
    ("print_source", "varchar(16) NOT NULL", "AUTO、TEST或REPRINT"),
    ("original_print_log_id", OPT_ID, "补打引用原任务或旧日志，保留原审计"),
    ("reprint_reason", "varchar(500) DEFAULT NULL", "人工补打原因，不补造历史原因"),
    ("frozen_record", "longtext NOT NULL", "不可变完整打印快照JSON，包含布局字段载荷及来源"),
    ("record_hash", HASH, "完整冻结记录UTF8字节SHA256"),
    ("status", "varchar(24) NOT NULL DEFAULT 'PENDING'", "PENDING、SENDING、SENT、FAIL、UNKNOWN、LEGACY_SENT"),
    ("attempt_count", "int(11) NOT NULL DEFAULT 0", "实际发送领取次数，只允许0转1"),
    ("lease_token", "varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL", "一次领取令牌，禁止发送中任务重新领取"),
    ("lease_until", "datetime DEFAULT NULL", "数据库时钟计算的发送租约，过期只能转UNKNOWN"),
    ("sending_started_at", "datetime DEFAULT NULL", "领取发送时刻"),
    ("completed_at", "datetime DEFAULT NULL", "终态记录时刻，不代表实际出纸"),
    ("last_error", "varchar(500) DEFAULT NULL", "脱敏错误或待人工核验原因"), CREATED, UPDATED
], ["  PRIMARY KEY (`task_id`)",
    "  UNIQUE KEY `uk_label_task_key` (`tenant_id`,`task_key`)",
    "  KEY `idx_label_task_pending` (`tenant_id`,`status`,`created_at`)",
    "  KEY `idx_label_task_lease` (`tenant_id`,`status`,`lease_until`)",
    "  KEY `idx_label_task_sn` (`tenant_id`,`sn_id`,`template_id`)"], "MES事务内冻结的持久化打印任务，发送不等于出纸确认")

add("erp", "wm_trace_audit_run", [
    ("audit_id", "varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL", "审计工件ID"), TENANT,
    ("material_id", OPT_ID, "审计物料，未限定时为空"),
    ("status", "varchar(24) NOT NULL DEFAULT 'REVIEW_REQUIRED'", "只读审计结论状态，不修改原业务"),
    ("summary", "text", "审计摘要"),
    ("report_json", "longtext NOT NULL", "完整审计报告工件"),
    ("content_hash", HASH, "审计报告原始UTF8字节SHA256"), CREATED
], ["  PRIMARY KEY (`audit_id`)",
    "  KEY `idx_trace_audit_tenant_created` (`tenant_id`,`created_at`)"], "物料追溯审计工件，只追加报告不改原业务")

# 完工实际用料与ERP预留账逐字段对应当前事务服务INSERT，不覆盖旧清单。
add("mes", "pro_completion_material_allocation", [TENANT,
    ("manifest_id", ID, "冻结完工清单ID"), ("revision", "int(11) NOT NULL", "冻结清单修订"),
    ("allocation_id", ID, "原实际消耗lt_wo_consumption.consumption_id，不是额度分配ID"),
    ("material_id", ID, "实际组件物料ID"),
    ("reservation_id", OPT_ID, "ERP实际库存预留头"), ("reservation_line_id", OPT_ID, "ERP实际库存预留行"),
    ("posting_group_id", OPT_ID, "已发料ERP原过账证明组"), ("identity_id", OPT_ID, "ERP正式组件身份"),
    ("source_stock_id", OPT_ID, "ERP实际来源库存ID"), ("erp_batch_id", OPT_ID, "ERP批次ID，SN-only来源允许空值"),
    ("quantity", "decimal(18,4) NOT NULL", "实际消耗基本单位数量"),
    ("source_version", "bigint(20) NOT NULL", "冻结时实际分配来源版本"),
    ("evidence_hash", HASH, "实际来源证据SHA256"),
    ("source_event_id", "varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL", "ERP原授权或过账事件ID")
], ["  PRIMARY KEY (`tenant_id`,`manifest_id`,`revision`,`allocation_id`)"], "最终完工清单的实际用料不可变快照")

# 实际产品批次产出只冻结原额度分配；ERP批次必须沿source_allocation_id读原凭据。
add("mes", "pro_product_lot_output", [
    ("output_id", ID, "实际产品批次产出ID"), TENANT, COMMAND,
    ("line_no", "bigint(20) NOT NULL", "原命令内实际产出行号"),
    ("mes_wo_id", ID, "真实MES生产工单"), ("card_id", ID, "实际生产流转卡"),
    ("step_record_id", ID, "实际报工工序记录"), ("lot_id", ID, "实际MES产品批次"),
    ("source_allocation_id", ID, "原PRODUCTION_OUTPUT额度分配ID，ERP批次由该原凭据取得"),
    ("material_id", ID, "实际产品物料"), ("quantity", "decimal(18,4) NOT NULL", "实际基本单位产出数量"),
    ("result", "varchar(16) COLLATE utf8mb4_bin NOT NULL", "实际GOOD或SCRAP，不以授权额度推定合格产出"),
    ("source_version", "bigint(20) NOT NULL DEFAULT 1", "实际产出冻结版本"),
    ("created_by", ID, "认证上下文中的实际报工人"), CREATED
], ["  PRIMARY KEY (`output_id`)",
    "  UNIQUE KEY `uk_product_output_command` (`tenant_id`,`command_id`,`line_no`)",
    "  UNIQUE KEY `uk_product_output_lot` (`tenant_id`,`lot_id`)",
    "  KEY `idx_product_output_work_order` (`tenant_id`,`mes_wo_id`,`card_id`,`step_record_id`,`lot_id`,`output_id`)",
    "  KEY `idx_product_output_step` (`tenant_id`,`step_record_id`)",
    "  KEY `idx_product_output_source` (`tenant_id`,`source_allocation_id`)"], "真实批次型产品产出，来源额度不是质量证明，不补造历史产出")

add("erp", "wm_trace_material_reservation", [
    ("reservation_id", ID, "实际库存预留ID"), TENANT, COMMAND,
    ("erp_wo_id", ID, "原ERP生产工单"), ("mes_wo_id", ID, "原MES生产工单"),
    ("requirement_detail_id", ID, "原确认物料需求行"), ("material_id", ID, "预留组件物料ID"),
    ("policy_version", "bigint(20) NOT NULL", "授权时物料追踪策略版本"),
    ("quantity", "decimal(18,4) NOT NULL", "实际预留合计数量"),
    ("content_hash", HASH, "原命令和实际选择的规范摘要"),
    ("status", "varchar(24) NOT NULL DEFAULT 'RESERVED'", "RESERVED预留或CONSUMED完工核销")
], ["  PRIMARY KEY (`reservation_id`)",
    "  UNIQUE KEY `uk_material_reservation_command` (`tenant_id`,`command_id`)",
    "  KEY `idx_material_reservation_wo` (`tenant_id`,`erp_wo_id`,`status`)",
    "  KEY `idx_material_reservation_requirement` (`tenant_id`,`requirement_detail_id`,`status`)"], "ERP倒冲实际库存预留头，与原库存同事务")

add("erp", "wm_trace_material_reservation_line", [
    ("reservation_line_id", ID, "实际库存预留行ID"), TENANT,
    ("reservation_id", ID, "原预留头ID"), ("line_no", "bigint(20) NOT NULL", "原命令内预留行号"),
    ("source_stock_id", ID, "实际选定的原库存行"), ("identity_id", OPT_ID, "ERP正式组件身份"),
    ("erp_batch_id", OPT_ID, "ERP批次ID，SN-only来源允许空值"),
    ("valuation_group_id", OPT_ID, "原库存计价组，不按SN重新计价"),
    ("quantity", "decimal(18,4) NOT NULL", "本行实际预留数量"),
    ("consumed_quantity", "decimal(18,4) NOT NULL DEFAULT 0.0000", "完工实际核销数量"),
    ("version", "bigint(20) NOT NULL DEFAULT 1", "原预留行并发版本")
], ["  PRIMARY KEY (`reservation_line_id`)",
    "  UNIQUE KEY `uk_material_reservation_line` (`tenant_id`,`reservation_id`,`line_no`)",
    "  KEY `idx_material_reservation_stock` (`tenant_id`,`source_stock_id`)"], "ERP实际预留库存明细，保留正式组件与批次证据")

add("erp", "wm_trace_material_consumption", [
    ("consumption_id", ID, "ERP实际消耗接收记录ID"), TENANT,
    ("manifest_id", ID, "原MES完工清单ID"), ("manifest_revision", "int(11) NOT NULL", "原清单修订"),
    ("allocation_id", ID, "原实际消耗lt_wo_consumption.consumption_id，不是额度分配ID"),
    ("erp_wo_id", ID, "原ERP生产工单"), ("material_id", ID, "实际组件物料ID"),
    ("reservation_id", OPT_ID, "原实际库存预留头"), ("reservation_line_id", OPT_ID, "原实际库存预留行"),
    ("posting_group_id", OPT_ID, "原发料过账证明组"), ("identity_id", OPT_ID, "ERP正式组件身份"),
    ("source_stock_id", OPT_ID, "实际来源库存行"), ("erp_batch_id", OPT_ID, "ERP批次ID，SN-only来源允许空值"),
    ("quantity", "decimal(18,4) NOT NULL", "实际消耗基本单位数量"),
    ("source_version", "bigint(20) NOT NULL", "MES冻结的实际来源版本"),
    ("evidence_hash", HASH, "原实际来源证据SHA256"),
    ("source_event_id", "varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL", "ERP原授权或过账事件ID")
], ["  PRIMARY KEY (`consumption_id`)",
    "  UNIQUE KEY `uk_material_consumption_allocation` (`tenant_id`,`manifest_id`,`manifest_revision`,`allocation_id`)",
    "  KEY `idx_material_consumption_wo` (`tenant_id`,`erp_wo_id`,`material_id`)"], "ERP按原MES实际消耗分配接收，不按BOM推算")

# 当前库存转换使用独立审计契约，不扩展原五表repair写权限。
add("erp", "wm_trace_stock_conversion_run", [
    ("run_id", "varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL", "当前库存转换运行ID，不重用已回退运行"), TENANT,
    ("material_id", ID, "整物料当前库存转换范围"),
    ("target_database", "varchar(64) NOT NULL", "显式核实的ERP目标库"),
    ("target_environment", "varchar(128) NOT NULL", "显式核实的目标环境"),
    ("backup_reference", "varchar(255) NOT NULL", "操作者核实的备份引用，不含连接凭据"),
    ("cutover_at", "datetime NOT NULL", "真实physical census切点，不代表补造历史收货"),
    ("plan_hash", HASH, "批准转换计划摘要"),
    ("before_snapshot_hash", HASH, "完整转换前快照摘要"),
    ("after_snapshot_hash", HASH, "完整转换后快照摘要"),
    ("bridge_hash", HASH, "census当前身份桥摘要，保留原聚合流水"),
    ("journal_json", "longtext NOT NULL", "不可变完整转换日志，不含journal_hash自身；回退不覆盖"),
    ("journal_hash", HASH, "原完整日志原始UTF8字节SHA256，回退保持原值"),
    ("revert_hash", OPT_HASH, "实际回退日志摘要，未回退保持NULL"),
    ("status", "varchar(24) NOT NULL DEFAULT 'PREPARED'", "同事务PREPARED到APPLIED，安全回退后REVERTED"),
    ("version", "bigint(20) NOT NULL DEFAULT 1", "审计状态CAS，回退后为2"), CREATED, UPDATED
], ["  PRIMARY KEY (`run_id`)",
    "  KEY `idx_stock_conversion_material` (`tenant_id`,`material_id`,`created_at`)"], "当前库存到正式SN的零损益转换审计，仅追加工件并CAS状态")

# 工单release原策略证据；历史UNKNOWN只能凭原始证据采纳，不能从当前主数据回填。
add("erp", "wm_work_order_tracking_policy", [
    ("snapshot_id", ID, "不可变工单策略记录ID"), TENANT,
    ("erp_wo_id", ID, "真实ERP工单来源ID"), ("material_id", ID, "根物料或原组件物料ID"),
    ("snapshot_version", "int(11) NOT NULL", "策略证据格式版本，不推定历史版本"),
    ("policy_version", ID, "原追踪策略版本"),
    ("policy_json", "longtext NOT NULL", "原追踪策略冻结JSON"), ("policy_hash", HASH, "原策略JSON原始UTF8字节摘要"),
    ("source_json", "longtext NOT NULL", "原工单及完整物料来源范围JSON"), ("source_hash", HASH, "原工单来源JSON原始字节摘要"),
    ("snapshot_hash", HASH, "同工单整组策略证据摘要"),
    ("provenance", "varchar(32) COLLATE utf8mb4_bin NOT NULL", "ERP_RELEASE或有明确原证据的SOURCE_POLICY_ADOPTION"),
    ("evidence_ref", "varchar(255) NOT NULL", "原release或经批准历史策略凭据引用"),
    ("frozen_at", "datetime(3) NOT NULL", "原策略证据冻结时点"),
    ("effective_from", "datetime(3) NOT NULL", "明确原生效切点，不以当前时间补造历史"),
    ("actor_id", OPT_ID, "明确采纳操作人；原自动release可空")
], ["  PRIMARY KEY (`snapshot_id`)",
    "  UNIQUE KEY `uk_wo_tracking_policy` (`tenant_id`,`erp_wo_id`,`material_id`)",
    "  KEY `idx_wo_tracking_policy_material` (`tenant_id`,`material_id`,`erp_wo_id`)"], "不可变工单原追踪策略证据，旧工单缺失保持UNKNOWN")

add("erp", "wm_work_order_policy_adoption", [
    ("adoption_id", ID, "已独立审批的采纳申请ID"), TENANT, COMMAND,
    ("erp_wo_id", ID, "真实在途ERP工单ID"), ("expected_wo_hash", HASH, "已核实WO/BOM/需求来源预览摘要"),
    ("source_json", "longtext NOT NULL", "完整原始策略归档集合，不使用当前主数据"), ("source_hash", HASH, "原始策略归档字节摘要"),
    ("approval_json", "longtext NOT NULL", "独立审核人对原申请意图的批准工件"), ("approval_hash", HASH, "原批准工件字节摘要"),
    ("request_hash", HASH, "工单来源、原策略、双方操作人及审批时间窗的长度分帧摘要"),
    ("proposed_by", ID, "明确申请人，与批准人不同"), ("approved_by", ID, "独立批准人"),
    ("approved_at", "datetime(3) NOT NULL", "原批准时间"), ("expires_at", "datetime(3) NOT NULL", "批准有效期截止，过期拒绝"),
    ("status", "varchar(16) NOT NULL DEFAULT 'PENDING_REVIEW'", "只有显式独立批准后才可APPROVED，实际服务CAS到APPLIED"),
    ("version", "bigint(20) NOT NULL DEFAULT 1", "批准/执行版本CAS"),
    ("applied_at", "datetime(3) DEFAULT NULL", "实际执行后未来生效时间，不回填历史生效"),
    ("snapshot_hash", OPT_HASH, "实际服务冻结后的整组策略摘要，未执行保持NULL")
], ["  PRIMARY KEY (`adoption_id`)","  UNIQUE KEY `uk_wo_policy_adoption_command` (`tenant_id`,`command_id`)",
    "  KEY `idx_wo_policy_adoption_state` (`tenant_id`,`erp_wo_id`,`status`)"], "原工单策略独立审批采纳账，实际生效由ERP业务事务执行")

ADDITIONS = {
    "erp": {
        "wm_barcode": c([("primary_object_type", "varchar(16) COLLATE utf8mb4_bin DEFAULT NULL", "不可重绑的主对象类型SN/BATCH/MATERIAL/DOCUMENT，历史NULL保持未知")]),
        "bd_material": c([("allow_mixed_lot", "tinyint(1) NOT NULL DEFAULT 1", "同实物同组件物料是否允许混批，默认允许"),
            ("tracking_version", "bigint(20) NOT NULL DEFAULT 1", "物料追踪策略版本"),
            ("tracking_stage", "varchar(16) NOT NULL DEFAULT 'AUDIT'", "AUDIT只审计，STRICT按物料逐步启用")]),
        "bd_serial_number_rule": c([("rule_version", "bigint(20) NOT NULL DEFAULT 1", "发号规则版本，规则变更递增")]),
        "wm_sn": c([("registration_command", "varchar(128) COLLATE utf8mb4_bin DEFAULT NULL", "原发号预留命令ID，不补造历史命令"),
            ("identity_version", "bigint(20) NOT NULL DEFAULT 1", "正式身份版本")]),
        "wm_material_stock": c([("tracking_version", "bigint(20) NOT NULL DEFAULT 1", "历史默认1，已对账单件库存才升级2"),
            ("valuation_group_id", OPT_ID, "原计价组ID，不改变原金额"),
            ("active_sn_id", "bigint(20) GENERATED ALWAYS AS (CASE WHEN tracking_version >= 2 AND stock_quantity > 0 THEN sn_id ELSE NULL END) STORED", "仅新版正库存占用正式身份")]),
        "wm_material_stock_tx": c([("tracking_version", "bigint(20) NOT NULL DEFAULT 1", "事务追踪版本，历史保留1"),
            ("posting_group_id", OPT_ID, "冻结过账证明组ID")])
    },
    "mes": {
        "lt_wo_consumption": c([("source_allocation_id", OPT_ID, "实际消耗引用的原额度分配ID，旧记录未知保持NULL")]),
        "pro_sn": c([("identity_id", OPT_ID, "ERP正式身份wm_sn.sn_id，本地sn_id语义不变"),
            ("product_lot_id", OPT_ID, "MES成品批次lt_lot.lot_id"),
            ("identity_version", "bigint(20) NOT NULL DEFAULT 1", "身份映射版本"),
            ("handoff_status", "varchar(24) NOT NULL DEFAULT 'NONE'", "交付状态，历史默认NONE")]),
        "mes_label_template": c([("template_revision", "bigint(20) NOT NULL DEFAULT 1", "模板修订号，保存时递增，打印时冻结")]),
        "mes_label_print_log": c([
            ("template_revision", OPT_ID, "打印时模板修订号，历史未知留空"),
            ("original_print_log_id", OPT_ID, "补打引用的原任务ID，历史不补造"),
            ("template_snapshot", "longtext COLLATE utf8mb4_unicode_ci", "冻结模板布局及修订JSON，历史留空"),
            ("field_snapshot", "longtext COLLATE utf8mb4_unicode_ci", "冻结SN物料工单等字段JSON，历史留空"),
            ("rendered_payload", "longtext COLLATE utf8mb4_unicode_ci", "冻结完整打印指令，补打不读取新模板"),
            ("content_hash", "char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL", "原模板快照UTF8字节SHA256，历史不补造"),
            ("payload_hash", "char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL", "实际编码发送字节SHA256，历史不补造"),
            ("payload_charset", "varchar(32) COLLATE utf8mb4_bin DEFAULT NULL", "实际发送字符集名称"),
            ("snapshot_mode", "varchar(32) COLLATE utf8mb4_bin DEFAULT NULL", "冻结或历史兼容模式，旧记录保持空值"),
            ("reprint_reason", "varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL", "人工补打原因，历史缺失不补造")]),
        "lt_lot": c([("erp_batch_id", OPT_ID, "有明确凭据的ERP批次身份，不按同码猜测"),
            ("allocation_version", "bigint(20) NOT NULL DEFAULT 1", "批次分配版本")])
    }
}
INDEXES = {
    "erp": {"wm_material_stock": [("uk_material_stock_active_sn", ["tenant_id", "active_sn_id"], True)],
            "wm_sn": [("idx_sn_registration_command", ["tenant_id", "registration_command"], False)]},
    "mes": {"lt_wo_consumption": [("idx_wo_consumption_source_allocation", ["tenant_id", "source_allocation_id"], False)],
            "pro_sn": [("uk_pro_sn_identity", ["tenant_id", "identity_id"], True)],
            "lt_lot": [("idx_lot_erp_batch", ["tenant_id", "erp_batch_id"], False)]}
}

# 早期追溯表已存在时只执行已授权的无回填结构演进；定义仍从TABLES读取。
TABLE_ADDITIONS = {"pro_completion_manifest": ("material_line_count", "material_hash")}
TABLE_NULLABLE_RELAXATIONS = {"lt_lot_allocation": ("erp_batch_id",)}

def column_name(definition):
    return re.match(r"\s*`([^`]+)`", definition)[1]

def _column_condition(definition):
    """元数据只核对语义类型，不依赖5.7的整数显示宽度。"""
    name=column_name(definition)
    kind=re.search(r"`\w+` (\w+)(?:\(([^)]+)\))?",definition)
    predicates=[f"COLUMN_NAME='{name}'",f"DATA_TYPE='{kind[1].lower()}'",
                "IS_NULLABLE='"+("NO" if "NOT NULL" in definition else "YES")+"'"]
    if kind[1] in ("char","varchar"):
        predicates.append("CHARACTER_MAXIMUM_LENGTH="+kind[2])
    if kind[1]=="decimal":
        precision,scale=kind[2].split(",")
        predicates += ["NUMERIC_PRECISION="+precision,"NUMERIC_SCALE="+scale]
    if kind[1] in ("char","varchar","text","longtext"):
        charset=re.search(r"CHARACTER SET (\w+)",definition)
        charset=charset[1] if charset else "utf8mb4"
        collation=re.search(r"COLLATE (\w+)",definition)
        collation=collation[1] if collation else ("utf8mb4_unicode_ci" if charset=="utf8mb4" else "ascii_bin")
        predicates += [f"CHARACTER_SET_NAME='{charset}'",f"COLLATION_NAME='{collation}'"]
    default=re.search(r"\bDEFAULT (NULL|'[^']*'|CURRENT_TIMESTAMP|-?\d+(?:\.\d+)?)",definition)
    if default:
        value=default[1]
        if value=="NULL":predicates.append("COLUMN_DEFAULT IS NULL")
        elif value=="CURRENT_TIMESTAMP":predicates.append("LOWER(COLUMN_DEFAULT)='current_timestamp'")
        else:
            raw=value.strip("'").replace("'","''")
            predicates.append(f"COLUMN_DEFAULT='{raw}'")
    elif "GENERATED ALWAYS" not in definition:
        predicates.append("COLUMN_DEFAULT IS NULL")
    return "("+" AND ".join(predicates)+")"


def _table_evolutions(name, ddl):
    """只增可空事实字段或放宽已知整型列，不回填未知历史业务。"""
    definitions = {column_name(d): d.rstrip(",") for d in re.findall(r"^  `[^`]+` [^\n]+", ddl, re.M)}
    out = []
    for column in TABLE_ADDITIONS.get(name, ()):
        definition = definitions[column].strip()
        out += [f"  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND COLUMN_NAME='{column}') = 0 THEN",
                f"    ALTER TABLE `{name}` ADD COLUMN {definition};", "  END IF;"]
    for column in TABLE_NULLABLE_RELAXATIONS.get(name, ()):
        scope = f"TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND COLUMN_NAME='{column}'"
        out += [f"  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE {scope} AND IS_NULLABLE='NO') = 1 THEN",
                f"    IF (SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE {scope}) <> 'bigint'",
                f"       OR (SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE {scope}) LIKE '%unsigned%'",
                f"       OR (SELECT EXTRA FROM information_schema.COLUMNS WHERE {scope}) <> ''",
                f"       OR (SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE {scope}) IS NOT NULL THEN",
                f"      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表{name}字段{column}旧定义不兼容，禁止隐式转换';", "    END IF;",
                f"    ALTER TABLE `{name}` MODIFY COLUMN {definitions[column].strip()};", "  END IF;"]
    return out


def _new_table_guards(name,ddl):
    definitions=re.findall(r"^  `[^`]+` [^\n]+",ddl,re.M)
    definitions=[d.rstrip(",") for d in definitions]
    conditions=" OR ".join(_column_condition(d) for d in definitions)
    out=[f"  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND ENGINE='InnoDB') <> 1",
         f"     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND ({conditions})) <> {len(definitions)} THEN",
         f"    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表{name}字段定义偏离契约';","  END IF;"]
    expected=[]
    for line in ddl.splitlines():
        if "PRIMARY KEY" in line:idx="PRIMARY";unique=True
        else:
            m=re.search(r"(UNIQUE )?KEY `([^`]+)`",line)
            if not m:continue
            idx=m[2];unique=bool(m[1])
        columns=re.search(r"\(([^)]+)\)",line)[1].replace("`","")
        scope=f"TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND INDEX_NAME='{idx}'"
        expected += [f"COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE {scope}),'') <> '{columns}'",
                     f"COALESCE((SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE {scope}),-1) <> {0 if unique else 1}",
                     f"(SELECT COUNT(*) FROM information_schema.STATISTICS WHERE {scope} AND SUB_PART IS NOT NULL) <> 0"]
    out += ["  IF "+"\n     OR ".join(expected)+" THEN",
            f"    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表{name}索引偏离契约';","  END IF;"]
    return out


def preflight(ds):
    inspected = dict.fromkeys((*ADDITIONS[ds], *TABLES[ds]))
    out=[f"-- 2026-10-18 {ds.upper()}只读预检；仅供另行授权的操作者核实，不由开发工具执行。",
         "-- 不建立连接、不改表、不修复、不切换STRICT。先核对目标及导出边界。",
         "SELECT DATABASE() AS selected_database,@@server_uuid AS server_uuid,VERSION() AS mysql_version;",
         "SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ("+",".join("'"+t+"'" for t in inspected)+");",
         "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,COLLATION_NAME,EXTRA,GENERATION_EXPRESSION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ("+",".join("'"+t+"'" for t in inspected)+") ORDER BY TABLE_NAME,ORDINAL_POSITION;",
         "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ("+",".join("'"+t+"'" for t in inspected)+") ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;"]
    if ds=="erp":
        out += ["-- 旧版重复只报告，不能据此自动升级或补造单件。",
                "SELECT tenant_id,sn_id,COUNT(*) AS active_rows,SUM(stock_quantity) AS quantity FROM wm_material_stock WHERE stock_quantity>0 AND sn_id IS NOT NULL GROUP BY tenant_id,sn_id HAVING COUNT(*)>1;",
                "SELECT s.tenant_id,s.stock_id,s.material_id,s.sn_id,s.stock_quantity FROM wm_material_stock s JOIN bd_material m ON m.tenant_id=s.tenant_id AND m.material_id=s.material_id WHERE m.is_serial_manage=1 AND s.stock_quantity>0 AND (s.sn_id IS NULL OR s.stock_quantity<>1);",
                "SET @trace_preflight_sql=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND COLUMN_NAME='tracking_version')=1,'SELECT tenant_id,sn_id,COUNT(*) AS conflicting_rows FROM wm_material_stock WHERE tracking_version>=2 AND stock_quantity>0 AND sn_id IS NOT NULL GROUP BY tenant_id,sn_id HAVING COUNT(*)>1','SELECT ''旧基线尚无tracking_version，新增默认1不会升级旧库存'' AS preflight_note');"]
    else:
        out += ["SELECT tenant_id,sn_id,source,mes_wo_id FROM pro_sn WHERE mes_wo_id IS NULL AND COALESCE(source,'')<>'SUBCONTRACT';",
                "SET @trace_preflight_sql=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND COLUMN_NAME='identity_id')=1,'SELECT tenant_id,identity_id,COUNT(*) AS conflicting_rows FROM pro_sn WHERE identity_id IS NOT NULL GROUP BY tenant_id,identity_id HAVING COUNT(*)>1','SELECT ''旧基线尚无identity_id，不能根据同码补造映射'' AS preflight_note');"]
    out += ["PREPARE trace_preflight_stmt FROM @trace_preflight_sql;","EXECUTE trace_preflight_stmt;","DEALLOCATE PREPARE trace_preflight_stmt;",""]
    return "\n".join(out)


def migration(ds):
    label = "ERP" if ds=="erp" else "MES"
    proc = "material_trace_20261018_"+ds
    out = [f"-- 2026-10-18 物料身份与追溯增量；仅适用已核实的{label}业务数据源。",
        "-- 本文件只提供待审SQL；离线工具不执行SQL，未接触既有业务库。",
        "-- 操作者须先显式设置 @material_trace_expected_schema 与 @material_trace_backup_reference。",
        "-- 先停写并核对备份及基线；禁止 --force。DDL隐式提交，失败后按元数据检查安全续跑。",
        "-- 禁止以含DROP TABLE的主安装SQL迁移存量库；本文件不改写历史业务行。",
        "SET NAMES utf8mb4;", f"DROP PROCEDURE IF EXISTS `{proc}`;", "DELIMITER $$",
        f"CREATE PROCEDURE `{proc}`()", "BEGIN",
        "  IF DATABASE() IS NULL OR @material_trace_expected_schema IS NULL",
        "     OR BINARY DATABASE() <> BINARY @material_trace_expected_schema",
        "     OR @material_trace_backup_reference IS NULL OR CHAR_LENGTH(TRIM(@material_trace_backup_reference)) = 0 THEN",
        f"    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '必须显式核实{label}目标库及备份引用';", "  END IF;"]
    for name in ADDITIONS[ds]:
        out += [f"  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND ENGINE='InnoDB') <> 1 THEN",
                f"    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '基线表{name}不存在或不是InnoDB';", "  END IF;"]
    for name, columns in ADDITIONS[ds].items():
        for definition in columns:
            cn=column_name(definition)
            out += [f"  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND COLUMN_NAME='{cn}') = 0 THEN",
                    f"    ALTER TABLE `{name}` ADD COLUMN {definition.strip()};", "  END IF;"]
            condition=_column_condition(definition)
            out += [f"  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND {condition}) <> 1 THEN",
                    f"    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '字段{name}.{cn}类型偏离契约';", "  END IF;"]
    if ds=="erp":
        out += ["  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND COLUMN_NAME='active_sn_id' AND EXTRA LIKE '%STORED GENERATED%' AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(GENERATION_EXPRESSION,'`',''),' ',''),'(',''),')',''))='casewhentracking_version>=2andstock_quantity>0thensn_idelsenullend') <> 1 THEN",
                "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'active_sn_id必须为约定的新版正库存生成列';","  END IF;"]
        for name in ("wm_material_stock","wm_material_stock_tx"):
            out += [f"  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND COLUMN_NAME='sn_code' AND DATA_TYPE='varchar') <> 1 THEN",
                "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SN列基线必须为varchar';", "  END IF;",
                f"  IF (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND COLUMN_NAME='sn_code') < 128 THEN",
                f"    ALTER TABLE `{name}` MODIFY COLUMN `sn_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '正式SN原码，最长128位';", "  END IF;"]
        out += ["  -- 只变更新行默认值，历史IN_STOCK等状态保持原值。",
            "  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_sn' AND COLUMN_NAME='status' AND COLUMN_DEFAULT='REGISTERED') = 0 THEN",
            "    ALTER TABLE `wm_sn` ALTER COLUMN `status` SET DEFAULT 'REGISTERED';", "  END IF;",
            "  -- 不静默修复；新版活跃身份重复或缺失时立即停止，旧版库存不强制迁移。",
            "  IF EXISTS (SELECT 1 FROM wm_material_stock WHERE tracking_version >= 2 AND stock_quantity > 0 AND sn_id IS NOT NULL GROUP BY tenant_id,sn_id HAVING COUNT(*) > 1) THEN",
            "    SELECT tenant_id,sn_id,COUNT(*) AS conflicting_rows FROM wm_material_stock WHERE tracking_version >= 2 AND stock_quantity > 0 AND sn_id IS NOT NULL GROUP BY tenant_id,sn_id HAVING COUNT(*) > 1;",
            "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '新版活跃SN存在重复，须先审计和凭据修复';", "  END IF;",
            "  IF EXISTS (SELECT 1 FROM wm_material_stock s LEFT JOIN bd_material m ON m.tenant_id=s.tenant_id AND m.material_id=s.material_id WHERE s.tracking_version >= 2 AND s.stock_quantity > 0 AND (s.sn_id IS NOT NULL OR m.is_serial_manage=1) AND (s.tenant_id IS NULL OR s.sn_id IS NULL OR s.stock_quantity <> 1 OR NOT EXISTS (SELECT 1 FROM wm_sn n WHERE n.tenant_id=s.tenant_id AND n.sn_id=s.sn_id AND n.material_id=s.material_id))) THEN",
            "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '新版SN库存缺身份或不是单件，禁止静默升级';", "  END IF;"]
    else:
        out += ["  -- 标签只扩展结构及新行默认值，不补造旧快照、不重写历史SUCCESS。",
            "  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='sn_code' AND DATA_TYPE='varchar') <> 1 THEN",
            "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '标签SN列基线必须为varchar';","  END IF;",
            "  IF (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='sn_code') < 128 THEN",
            "    ALTER TABLE `mes_label_print_log` MODIFY COLUMN `sn_code` varchar(128) DEFAULT NULL COMMENT '正式SN原码，最长128位';","  END IF;",
            "  IF (SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='retry_count') = 'NO' THEN",
            "    ALTER TABLE `mes_label_print_log` MODIFY COLUMN `retry_count` int(11) DEFAULT NULL COMMENT '同SN模板既有发送尝试数，未知保留空值';","  END IF;",
            "  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='retry_count' AND COLUMN_DEFAULT IS NOT NULL) > 0 THEN",
            "    ALTER TABLE `mes_label_print_log` ALTER COLUMN `retry_count` SET DEFAULT NULL;","  END IF;",
            "  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mes_label_print_log' AND COLUMN_NAME='result' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH>=7) <> 1 THEN",
            "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '标签结果必须兼容SENT及历史SUCCESS，禁止重写旧值';","  END IF;",
            "  -- 只有SUBCONTRACT来源允许空工单；服务必须核实来源，不依赖5.7不执行的CHECK。",
            "  IF (SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pro_sn' AND COLUMN_NAME='mes_wo_id') = 'NO' THEN",
            "    ALTER TABLE `pro_sn` MODIFY COLUMN `mes_wo_id` bigint(20) DEFAULT NULL COMMENT '工单ID，仅已核实SUBCONTRACT来源可空';", "  END IF;",
            "  IF EXISTS (SELECT 1 FROM pro_sn WHERE identity_id IS NOT NULL GROUP BY tenant_id,identity_id HAVING COUNT(*) > 1) THEN",
            "    SELECT tenant_id,identity_id,COUNT(*) AS conflicting_rows FROM pro_sn WHERE identity_id IS NOT NULL GROUP BY tenant_id,identity_id HAVING COUNT(*) > 1;",
            "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'MES正式身份重复，须先凭据审计';", "  END IF;"]
    for name,indexes in INDEXES[ds].items():
        for idx,cols,unique in indexes:
            column_sql=",".join("`"+x+"`" for x in cols)
            signature=",".join(cols)
            out += [f"  IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND INDEX_NAME='{idx}') = 0 THEN",
                f"    ALTER TABLE `{name}` ADD {'UNIQUE ' if unique else ''}KEY `{idx}` ({column_sql});", "  ELSE",
                f"    IF (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND INDEX_NAME='{idx}') <> '{signature}'",
                f"       OR (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND INDEX_NAME='{idx}') <> {0 if unique else 1} THEN",
                f"      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '索引{idx}定义偏离契约';", "    END IF;", "  END IF;"]
    for name,ddl in TABLES[ds].items():
        out += ["",ddl]
        out += _table_evolutions(name, ddl)
        # CREATE IF NOT EXISTS不能掩盖同名残缺表，逐列检查完整性。
        names = re.findall(r"^  `([^`]+)`",ddl,re.M)
        quoted = ",".join("'"+n+"'" for n in names)
        out += [f"  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND COLUMN_NAME IN ({quoted})) <> {len(names)} THEN",
                f"    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '表{name}字段不完整，禁止盲目续跑';", "  END IF;"]
        out += _new_table_guards(name,ddl)
    out += ["END$$", "DELIMITER ;",f"CALL `{proc}`();",f"DROP PROCEDURE `{proc}`;",""]
    return "\n".join(out)

def synchronize_install(ds):
    p=SQL/("ry-"+ds+".sql")
    raw=p.read_bytes();s=raw.decode("utf-8-sig")
    nl="\r\n" if "\r\n" in s else "\n"
    for name,cols in ADDITIONS[ds].items():
        pattern=r"CREATE TABLE `"+name+r"` \([\s\S]*?\n\)[^;]*;"
        m=re.search(pattern,s)
        if not m: raise ValueError("缺少安装基线表"+name)
        block=m[0]
        new_cols=[d for d in cols if not re.search(r"^\s*`"+column_name(d)+r"`",block,re.M)]
        if new_cols:block=block.replace("  PRIMARY KEY",(","+nl).join(new_cols)+","+nl+"  PRIMARY KEY",1)
        if ds=="erp" and name in ("wm_material_stock","wm_material_stock_tx"):
            block=re.sub(r"(`sn_code` varchar)\(\d+\)",r"\1(128)",block)
        if ds=="erp" and name=="wm_sn":block=block.replace("DEFAULT 'IN_STOCK'", "DEFAULT 'REGISTERED'")
        if ds=="mes" and name=="mes_label_print_log":
            block=re.sub(r"(`sn_code` varchar)\(\d+\)",r"\1(128)",block)
            block=re.sub(r"(?m)^  `retry_count`[^\r\n]*", "  `retry_count` int(11) DEFAULT NULL COMMENT '同SN模板既有发送尝试数，未知保留空值',", block)
            block=re.sub(r"(`result` varchar\(16\) NOT NULL COMMENT )'[^']*'",r"\1'结果：SENT已发送未确认出纸、FAIL、历史SUCCESS保持原值'",block)
        if ds=="mes" and name=="pro_sn":block=block.replace("`mes_wo_id` bigint(20) NOT NULL", "`mes_wo_id` bigint(20) DEFAULT NULL")
        for idx,cols2,unique in INDEXES[ds].get(name,[]):
            if "`"+idx+"`" not in block:
                line=f"  {'UNIQUE ' if unique else ''}KEY `{idx}` ("+",".join("`"+x+"`" for x in cols2)+")"
                block=re.sub(r"\r?\n\)(?= ENGINE)",","+nl+line+nl+")",block)
        s=s[:m.start()]+block+s[m.end():]
    start="-- material-trace-20261018:begin"
    end="-- material-trace-20261018:end"
    block=(start+"\n-- 追溯增量对应的新装表；存量库只能使用独立增量脚本。\n"+"\n\n".join(TABLES[ds].values())+"\n"+end+"\n").replace("\n",nl)
    if start in s:s=re.sub(re.escape(start)+r"[\s\S]*?"+re.escape(end)+r"\r?\n?",lambda _:block,s)
    else:s+=nl+block
    p.write_bytes(s.encode("utf-8"))

def strict_constraints():
    """清理后独立收紧原两表租户；无DML、无默认租户回填，兼容扩展阶段不自动执行。"""
    proc="material_trace_strict_tenants_20261019"
    out=["-- 严格阶段：必须先完成扩展、逐行租户凭据清理、完整审计、可恢复备份及独占停写窗口。",
         "-- 本文件只生成供人工审核；MySQL5.7 DDL隐式提交，失败须保全部分完成状态，禁止--force。",
         "-- 明确设置expected_schema/expected_server_uuid/backup_reference/maintenance_reference及cleanup_confirmed。",
         "SET NAMES utf8mb4;",f"DROP PROCEDURE IF EXISTS `{proc}`;","DELIMITER $$",f"CREATE PROCEDURE `{proc}`() SQL SECURITY INVOKER","BEGIN",
         "  IF DATABASE() IS NULL OR @material_trace_expected_schema IS NULL OR BINARY DATABASE()<>BINARY @material_trace_expected_schema",
         "     OR @material_trace_expected_server_uuid IS NULL OR BINARY @@server_uuid<>BINARY @material_trace_expected_server_uuid",
         "     OR @material_trace_backup_reference IS NULL OR CHAR_LENGTH(TRIM(@material_trace_backup_reference))=0",
         "     OR @material_trace_maintenance_reference IS NULL OR CHAR_LENGTH(TRIM(@material_trace_maintenance_reference))=0",
         "     OR @material_trace_strict_tenant_cleanup_confirmed IS NULL OR BINARY @material_trace_strict_tenant_cleanup_confirmed<>BINARY 'TENANT_CLEANUP_VERIFIED' THEN",
         "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='严格租户约束须显式核实目标、UUID、备份、停写及清理确认';","  END IF;"]
    for name in ("wm_sn","wm_material_stock"):
        out += [f"  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND ENGINE='InnoDB')<>1",
                f"     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND COLUMN_NAME='tenant_id' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=20 AND CHARACTER_SET_NAME='utf8mb4' AND COLLATION_NAME='utf8mb4_unicode_ci' AND IS_NULLABLE IN ('YES','NO'))<>1 THEN",
                f"    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='{name}租户列或基线引擎偏离契约';","  END IF;",
                f"  IF EXISTS (SELECT 1 FROM `{name}` WHERE tenant_id IS NULL OR CHAR_LENGTH(TRIM(tenant_id))=0) THEN",
                f"    SELECT '{name}' AS source_table,COUNT(*) AS unresolved_tenants FROM `{name}` WHERE tenant_id IS NULL OR CHAR_LENGTH(TRIM(tenant_id))=0;",
                "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='仍有NULL或空白租户，必须凭原证据清理；禁止回填未知租户';","  END IF;"]
    out += ["  IF EXISTS (SELECT 1 FROM wm_sn GROUP BY tenant_id,sn_code HAVING COUNT(*)>1) THEN",
            "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='正式码存在旧唯一性冲突，保持原行并先核实';","  END IF;",
            "  IF EXISTS (SELECT 1 FROM wm_material_stock WHERE tracking_version>=2 AND stock_quantity>0 AND sn_id IS NULL)",
            "     OR EXISTS (SELECT 1 FROM wm_material_stock WHERE tracking_version>=2 AND stock_quantity>0 GROUP BY tenant_id,sn_id HAVING COUNT(*)>1) THEN",
            "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='新版正库存身份缺失或重复，禁止收紧前掩盖旧冲突';","  END IF;",
            "  IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wm_material_stock' AND COLUMN_NAME='active_sn_id' AND EXTRA LIKE '%STORED GENERATED%' AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(GENERATION_EXPRESSION,'`',''),' ',''),'(',''),')',''))='casewhentracking_version>=2andstock_quantity>0thensn_idelsenullend')<>1 THEN",
            "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='扩展阶段active_sn_id生成列尚未正确安装';","  END IF;"]
    for name,index,columns in (("wm_sn","uk_sn_code","sn_code,tenant_id"),("wm_material_stock","uk_material_stock_active_sn","tenant_id,active_sn_id")):
        out += [f"  IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND INDEX_NAME='{index}')<>2",
                f"     OR (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND INDEX_NAME='{index}')<>'{columns}'",
                f"     OR (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND INDEX_NAME='{index}')<>0 THEN",
                f"    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='{index}唯一键尚未正确安装';","  END IF;"]
    # 全部数据与结构预检都在任何ALTER之前；不添加猜测租户或默认值。
    for name in ("wm_sn","wm_material_stock"):
        out += [f"  IF (SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND COLUMN_NAME='tenant_id')='YES'",
                f"     OR (SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='{name}' AND COLUMN_NAME='tenant_id') IS NOT NULL THEN",
                f"    ALTER TABLE `{name}` MODIFY COLUMN `tenant_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户ID，已凭原证据完成清理；禁止未知租户默认值';","  END IF;"]
    out += ["END$$","DELIMITER ;",f"CALL `{proc}`();",f"DROP PROCEDURE `{proc}`;",""]
    return "\n".join(out)


if __name__=="__main__":
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--strict-constraints",action="store_true",help="仅生成独立的租户清理后严格约束脚本；不执行")
    args=parser.parse_args()
    if not args.strict_constraints:
        for ds in ("erp","mes"):
            (SQL/"update"/("2026-10-18-material-trace-"+ds+".sql")).write_text(migration(ds),encoding="utf-8")
            (SQL/"update"/("2026-10-18-material-trace-preflight-"+ds+".sql")).write_text(preflight(ds),encoding="utf-8")
            synchronize_install(ds)
    (SQL/"update/2026-10-19-material-trace-strict-constraints-erp.sql").write_text(strict_constraints(),encoding="utf-8")
    print("已渲染"+("独立严格租户约束" if args.strict_constraints else "ERP/MES增量、主安装DDL及独立严格约束")+"；未执行任何SQL。")
