# 多租户唯一键维度修复 + 幂等唯一约束补齐（DDL 批次）修复报告（2026-09-20）

> 对应审计：`doc/audit/_raw-ddl-tenant-check.txt`（143 唯一键 / 77 缺 tenant_id / 18 表 tenant_id 类型非 varchar(20)）、
> `2026-09-20-audit-erp-bd.md` ERP-BD-06/09、`2026-09-20-audit-erp-so-fin.md` ERP-SO-03、
> `2026-09-20-audit-mes-qc.md` MES-QC-04/MES-NCR-05、`2026-09-20-audit-mes-pro.md` MES-PRO-10、
> `2026-09-20-audit-mes-spc-dc-lt.md` MES-LT-05、`2026-09-20-audit-erp-pu-prd.md` ERP-PU-02
> 分支：`feature/erp-mes-iot-audit-fix`　｜　规范：`doc/system/specs/charset-encoding-standard.md` §5

**结论摘要**：77 个缺 `tenant_id` 的唯一键 → **50 个已补 `tenant_id`（ry-erp 25 + ry-mes 25）、27 个豁免（全部在 ry-vue.sql，全局/第三方/框架表）**；
另补 **5 个幂等唯一键**、收敛 **8 张表 `tenant_id` 类型为 varchar(20)**。改动文件：`script/sql/ry-erp.sql`、`script/sql/ry-mes.sql`（本批次独占授权），新增存量库脚本 `script/sql/update/2026-09-20-uk-tenant-dimension.sql`。
未触碰 `ry-iot.sql` / `ry-vue.sql` / 任何 java·vue·yml 文件，**主脚本合并清单为空**。

---

## 1. 逐条处置表（77 条扫描记录）

列顺序风格：ry-erp 沿用「tenant_id 置末位」（参照 `ry-erp.sql:2036` `uk_routing_code_version(routing_code, routing_version, tenant_id)`）；
ry-mes 沿用「tenant_id 置首位」（参照 `ry-mes.sql:657` `uk_tenant_content_code(tenant_id, content_code)`）。**索引名保持不变**，仅扩展列（便于回滚与评审）。

### 1.1 ry-erp.sql —— 25 条：改（补 tenant_id）

| # | 表.唯一键（原定义） | 处置 | 理由 |
|---|---|---|---|
| 1 | `bd_bom.uk_parent_version(parent_material_id, bom_version)` | 改 → `+tenant_id` | ERP-BD-06；同域 `bd_routing` 已按 `(业务键…,tenant_id)` 建模；种子数据 ID 为 1..N，跨租户同 ID 碰撞会导致租户 B 无法建 BOM |
| 2 | `bd_material.uk_material_code(material_code)` | 改 → `+tenant_id` | ERP-BD-06；物料编码形如 `IF20260914001`，租户无关格式，跨租户极易重码（`DuplicateKeyException`→500） |
| 3 | `bd_material_vendor.uk_material_supplier(material_id, vendor_id)` | 改 → `+tenant_id` | ERP-BD-06；物料/供应商 ID 在小 ID 场景跨租户碰撞 |
| 4 | `bd_serial_number_rule.uk_rule_code(rule_code)` | 改 → `+tenant_id` | 编码类业务键，多租户规则各租户自建（同表 `idx_tenant_id` 存在） |
| 5 | `bd_unit_of_measure.uk_uom_code(uom_code)` | 改 → `+tenant_id` | 计量单位编码属租户级基础数据（租户可自建同码单位） |
| 6 | `bd_vendor.uk_vendor_code(vendor_code)` | 改 → `+tenant_id` | 供应商编码业务键；租户各自维护供应商主数据 |
| 7 | `bd_workshop.uk_workshop_code(workshop_code)` | 改 → `+tenant_id` | 车间编码业务键；多租户各自建模车间 |
| 8 | `bd_workstation.uk_workstation_code(workstation_code)` | 改 → `+tenant_id` | 工位编码业务键；同上 |
| 9 | `po_requisition.uk_po_requisition_no(requisition_no)` | 改 → `+tenant_id` | 请购单号业务键（`po_` 前缀虽未列入任务前缀清单，但属 ry-erp 业务库单据表且含 tenant_id，按同口径处理） |
| 10 | `prd_material_requirement.uk_requirement_no(requirement_no)` | 改 → `+tenant_id` | 物料需求单号业务键 |
| 11 | `prd_production_plan.uk_plan_no(plan_no)` | 改 → `+tenant_id` | 生产计划单号业务键 |
| 12 | `prd_work_order.uk_wo_no(wo_no)` | 改 → `+tenant_id` | 工单号业务键；跨租户同号直接插入失败 |
| 13 | `prd_work_report.uk_report_no(report_no)` | 改 → `+tenant_id` | 报工单号业务键 |
| 14 | `pu_purchase_order.uk_order_no(order_no)` | 改 → `+tenant_id` | 采购订单号业务键（同时收敛 tenant_id 类型，见 §1.4） |
| 15 | `pu_purchase_requisition.uk_requisition_no(requisition_no)` | 改 → `+tenant_id` | 采购申请单号业务键 |
| 16 | `pu_purchase_return.uk_return_no(return_no)` | 改 → `+tenant_id` | 采购退货单号业务键 |
| 17 | `sch_team_member.uk_team_worker(team_id, worker_id)` | 改 → `+tenant_id` | 班组成员业务键（同表风格：`sch_team` 用 `uk_tenant_team_code`） |
| 18 | `so_price_list_line.uk_pl_line_tier(price_list_id, material_id, min_qty)` | 改 → `+tenant_id` | 价目表阶梯业务键；`material_id` 在小 ID 场景跨租户碰撞 |
| 19 | `wm_barcode.uk_barcode_code(barcode_code)` | 改 → `+tenant_id` | 条码值业务键（条码前缀/流水号按租户配置，跨租户可重码） |
| 20 | `wm_barcode_config.uk_barcode_config_code(config_code)` | 改 → `+tenant_id` | 条码规则编码业务键 |
| 21 | `wm_batch.uk_batch_code(batch_code)` | 改 → `+tenant_id` | 批次编码业务键（与 `lt_lot.uk_lot_code` 同型） |
| 22 | `wm_material_handover.uk_handover_no(handover_no)` | 改 → `+tenant_id` | 交接单号业务键 |
| 23 | `wm_sales_issue.uk_issue_code(issue_code)` | 改 → `+tenant_id` | 销售出库单号业务键 |
| 24 | `wm_sn.uk_sn_code(sn_code)` | 改 → `+tenant_id` | SN 编码业务键（SN 规则按租户配置） |
| 25 | `wm_warehouse.uk_warehouse_code(warehouse_code)` | 改 → `+tenant_id` | 仓库编码业务键 |

### 1.2 ry-mes.sql —— 25 条：改（补 tenant_id）

| # | 表.唯一键（原定义） | 处置 | 理由 |
|---|---|---|---|
| 26 | `dc_collection_point.uk_point_code(point_code)` | 改 → `tenant_id+` | MES-LT-05；`point_code` 常按设备位号命名，跨租户撞码概率高；应用层 `exists(pointCode)` 受租户插件过滤（查重在租户内、约束在租户间，口径不一致） |
| 27 | `dc_data_record.uk_point_collect_time(point_id, collect_time)` | 改 → `tenant_id+` | 采集记录业务键；同表 `idx_tenant_id` 存在，属多租户业务表 |
| 28 | `eq_equipment.uk_equipment_code(equipment_code)` | 改 → `tenant_id+` | 设备编码业务键 |
| 29 | `eq_repair.uk_repair_no(repair_no)` | 改 → `tenant_id+` | 维修单号业务键 |
| 30 | `lt_lot.uk_lot_code(lot_code)` | 改 → `tenant_id+` | MES-LT-05 正条；`LtLotServiceImpl.insertByBo` 用 `exists(lotCode)` 租户内查重 |
| 31 | `lt_sn_binding.uk_sn_material(sn_id, material_id)` | 改 → `tenant_id+` | MES-LT-05 要求「一并核对口径」；SN 绑定关系属租户内业务数据 |
| 32 | `mes_label_template.uk_template_code(template_code)` | 改 → `tenant_id+` | 标签模板编码业务键 |
| 33 | `mes_mbom.uk_bom_id(bom_id)` | 改 → `tenant_id+` | MBOM 业务键（引用 ERP BOM；小 ID/种子数据场景跨租户碰撞） |
| 34 | `mes_station_device.uk_device_code(device_code)` | 改 → `tenant_id+` | 工位终端设备编码业务键 |
| 35 | `ncr_defect_code.uk_code(code)` | 改 → `tenant_id+` | 不良代码字典属租户级主数据（各租户自定义代码集） |
| 36 | `ncr_order.uk_ncr_code(ncr_code)` | 改 → `tenant_id+` | NCR 单号业务键 |
| 37 | `ncr_order.uk_source(source_type, source_id)` | 改 → `tenant_id+`（**部分修复**，见 §2.2） | MES-NCR-05：DDL 只能补租户维度；`source_id` 为 NULL 时 MySQL 唯一键不去重，需应用层传 `stepRecordId` 才能根治 |
| 38 | `ncr_rework_order.uk_rework_code(rework_code)` | 改 → `tenant_id+` | 返工单号业务键 |
| 39 | `pro_andon.uk_andon_no(andon_no)` | 改 → `tenant_id+` | MES-PRO-10；跨租户同工单/安灯号直接插入失败，ERP→MES 下达链路对该租户不可用 |
| 40 | `pro_dispatch.uk_dispatch_no(dispatch_no)` | 改 → `tenant_id+` | MES-PRO-10 |
| 41 | `pro_flow_card.uk_card_no(card_no)` | 改 → `tenant_id+` | MES-PRO-10 |
| 42 | `pro_flow_card.uk_barcode(barcode)` | 改 → `tenant_id+` | MES-PRO-10（流转卡条码） |
| 43 | `pro_sn.uk_sn_code(sn_code)` | 改 → `tenant_id+` | MES-PRO-10 |
| 44 | `pro_work_order.uk_wo_no(wo_no)` | 改 → `tenant_id+` | MES-PRO-10 正条 |
| 45 | `qc_inspect_template.uk_template_code(template_code)` | 改 → `tenant_id+` | 检验模板编码业务键（与 `mes_label_template` 同型） |
| 46 | `qc_ipqc.uk_ipqc_no(ipqc_no)` | 改 → `tenant_id+` | 检验单号业务键（`qc_ipqc` 属 pro_ 系 P1 清单外的 mes 表，按同口径处理） |
| 47 | `qc_iqc.uk_iqc_no(iqc_no)` | 改 → `tenant_id+` | 同上 |
| 48 | `qc_oqc.uk_oqc_no(oqc_no)` | 改 → `tenant_id+` | 同上 |
| 49 | `sop_document.uk_sop_code(sop_code)` | 改 → `tenant_id+` | SOP 编码业务键 |
| 50 | `tg_tooling.uk_tooling_code(tooling_code)` | 改 → `tenant_id+` | 工装编码业务键 |

### 1.3 ry-vue.sql —— 27 条：豁免（未编辑该文件）

| # | 表.唯一键 | 处置 | 豁免理由 |
|---|---|---|---|
| 51 | `act_hi_procinst.PROC_INST_ID_` | 豁免 | Flowable 引擎表（规范 §2 强制例外：`act_*` 由引擎 DDL 自管，且该表无 tenant_id） |
| 52 | `act_id_priv.ACT_UNIQ_PRIV_NAME(NAME_)` | 豁免 | Flowable 引擎表，同上 |
| 53 | `act_procdef_info.ACT_UNIQ_INFO_PROCDEF(PROC_DEF_ID_)` | 豁免 | Flowable 引擎表，同上 |
| 54 | `ai_flow.uk_flow_key(flow_key)` | 豁免（待业务确认） | AI 流程定义属 `ai_*` 全局共享域；`flow_key` 为设计器内唯一标识，跨租户共享同一流程模板是既有语义。**若产品要求租户级流程模板隔离，应改为 `(tenant_id, flow_key)`——ry-vue.sql 不在本批次授权，已列入 §3 待办** |
| 55 | `ai_tool.uk_tool_name(tool_name)` | 豁免（待业务确认） | 同上：MCP/内置工具为全局注册表，工具名跨租户唯一是设计意图 |
| 56 | `im_group_member.uk_group_user(group_id, user_id)` | 豁免 | `group_id` 为雪花主键（全局唯一），跨租户不可能碰撞；IM 群成员属全局会话数据 |
| 57 | `jimu_dict.uk_sd_dict_code(dict_code)` | 豁免 | 积木报表（JimuReport）第三方表，`jimu_*` 由报表引擎管理 |
| 58 | `jimu_report.uniq_jmreport_code(code)` | 豁免 | 同上 |
| 59 | `jimu_report_map.uniq_jmreport_map_name(name)` | 豁免 | 同上（该表无 tenant_id） |
| 60 | `jimu_report_share.uniq_report_id(report_id)` | 豁免 | 同上（该表无 tenant_id） |
| 61 | `mq_message_record.uk_message_id(message_id)` | 豁免 | 已在 `application.yml:158` `tenant.excludes`（实体无 tenant_id，全局共享） |
| 62 | `onl_drag_share.uniq_ods_drag_id(drag_id)` | 豁免 | 在线低代码（onl_*）第三方表，`drag_id` 为雪花主键 |
| 63 | `sj_group_config.uk_namespace_id_group_name` | 豁免 | SnailJob 调度中心表（`sj_*`），按 namespace/group 组织，非业务租户模型 |
| 64 | `sj_job.uk_sj_job_01(namespace_id, biz_id)` | 豁免 | 同上 |
| 65 | `sj_job_summary.uk_trigger_at_system_task_type_business_id` | 豁免 | 同上 |
| 66 | `sj_namespace.uk_unique_id(unique_id)` | 豁免 | 同上 |
| 67 | `sj_retry.uk_scene_tasktype_idempotentid_deleted` | 豁免 | 同上（幂等键已含 idempotent_id + deleted） |
| 68 | `sj_retry_scene_config.uk_namespace_id_group_name_scene_name` | 豁免 | 同上 |
| 69 | `sj_retry_summary.uk_scene_name_trigger_at` | 豁免 | 同上 |
| 70 | `sj_server_node.uk_host_id_host_ip(host_id, host_ip)` | 豁免 | 同上（集群节点表，全局） |
| 71 | `sj_system_user.uk_username(username)` | 豁免 | 同上（调度中心独立账号体系，与业务租户无关） |
| 72 | `sj_system_user_permission.uk_namespace_id_group_name_system_user_id` | 豁免 | 同上 |
| 73 | `sj_workflow.uk_sj_workflow_01(namespace_id, biz_id)` | 豁免 | 同上 |
| 74 | `sys_auto_code_rule.uk_rule_code(rule_code)` | 豁免（待业务确认） | `sys_*` 系统域；自动编码规则当前按全局维护（`tenant.excludes` 未排除本表，但 sys 域规则通常全局）。**若要求租户级编码规则，应改 `(rule_code, tenant_id)`——ry-vue.sql 不在本批次授权** |
| 75 | `sys_migration_log.uk_script_name(script_name)` | 豁免 | 迁移日志表（无 tenant_id，全局），脚本名全局唯一是设计意图 |
| 76 | `wf_category.codeIndex(code)` | 豁免（待业务确认） | warm-flow 流程引擎表（`wf_*`），流程分类为全局字典；引擎按 `code` 唯一定位。改动需同步引擎侧语义，**建议保持全局** |
| 77 | `wf_my_business.dataid(data_id)` | 豁免 | 同上：warm-flow「我的业务」关联表，`data_id` 为业务单据 ID（全局唯一雪花） |

> 说明：第 51–77 条全部位于 `script/sql/ry-vue.sql`，**本批次禁止编辑该文件**，因此「豁免」同时意味着「本批次不产出任何 ry-vue 改动」；其中 54/55/74/76 标记为「待业务确认」，若主代理决定收敛，请另行派单（改动为 ry-vue.sql 的 4 行 `UNIQUE INDEX`）。

### 1.4 tenant_id 类型收敛（18 张 BADTYPE 表）

| 表 | 原类型 | 处置 | 理由 |
|---|---|---|---|
| `pu_purchase_order` | varchar(64) | **改 → varchar(20)** | 规范 §5 一致性收敛；`'000000'` 不受影响 |
| `pu_purchase_order_line` | varchar(64) | **改 → varchar(20)** | 同上 |
| `pu_purchase_requisition` | varchar(64) | **改 → varchar(20)** | 同上 |
| `pu_purchase_requisition_line` | varchar(64) | **改 → varchar(20)** | 同上 |
| `pu_purchase_return` | varchar(64) | **改 → varchar(20)** | 同上 |
| `pu_purchase_return_line` | varchar(64) | **改 → varchar(20)** | 同上 |
| `wm_barcode` | varchar(64) | **改 → varchar(20)** | 同上（任务点名 `wm_barcode*`） |
| `wm_barcode_config` | varchar(64) | **改 → varchar(20)** | 同上 |
| `jimu_dict` | varchar(10) | **不改（待确认）** | 第三方积木报表表，可能已被租户插件排除（`jimu_*` 非业务域），改动收益低、风险不可控 |
| `jimu_report` | varchar(10) | **不改（待确认）** | 同上 |
| `jimu_report_category` | varchar(11) | **不改（待确认）** | 同上 |
| `jimu_report_data_source` | varchar(10) | **不改（待确认）** | 同上 |
| `jimu_report_export_job` | varchar(10) | **不改（待确认）** | 同上 |
| `jimu_report_export_log` | varchar(10) | **不改（待确认）** | 同上 |
| `jimu_report_icon_lib` | int(11) | **不改（待确认）** | 同上；且 int 型 tenant_id 与租户拦截器注入的 `'000000'` 不兼容，若该表真被纳入多租户需整体评估 |
| `onl_drag_dataset_head` | int(10) | **不改（待确认）** | 在线低代码（onl_*）第三方表，同上 |
| `onl_drag_page` | int(10) | **不改（待确认）** | 同上 |
| `onl_drag_table_relation` | int(11) | **不改（待确认）** | 同上 |

> 8 改 + 10 不改 = 18，与扫描一致。**保留下 `NULL` 语义**（未收紧为 `NOT NULL`）：存量库可能存在 NULL 行，`MODIFY ... NOT NULL` 会直接失败；收紧步骤与回填 SQL 已写进存量脚本第 A 部分注释，待存量 NULL 核查后单独执行。

### 1.5 新增幂等唯一键（5 个，均为「真正缺」）

| 审计编号 | 表.新增唯一键 | 主脚本位置 | 理由 |
|---|---|---|---|
| MES-QC-04 | `qc_iqc.uk_iqc_receive_material(tenant_id, receive_id, material_id)` | `ry-mes.sql:2524` | IQC 自动建单是「先 selectCount 再 insert」TOCTOU；MQ 重投/并发消费会建出重复 IQC（重复检验、重复回写 ERP、重复冻库） |
| MES-QC-04 | `qc_ipqc.uk_ipqc_wo_process(tenant_id, mes_wo_id, process_id)` | `ry-mes.sql:2486` | 同上（IPQC 自动建单）；**注意 `process_id` 可空，见 §2.1** |
| MES-QC-04 | `qc_oqc.uk_oqc_so_material(tenant_id, so_id, material_id)` | `ry-mes.sql:2560` | 同上（OQC 自动建单） |
| ERP-BD-09 | `bd_client_credit.uk_client_tenant(client_id, tenant_id)` | `ry-erp.sql:1018` | `CreditCheckServiceImpl:47` 用 `selectOne(clientId)`，同客户多行 → `TooManyResultsException` → 销售订单确认 500；该表原本无任何 UNIQUE |
| ERP-SO-03 | `so_credit_usage.uk_credit_so_status(tenant_id, so_id, status)` | `ry-erp.sql:5165` | 信用额度 TOCTOU：并发 `confirm()` 可各自读到相同已用额度并各插一条 FROZEN；原本只有普通索引 |

---

## 2. 未修复 / 部分修复项（含理由与建议）

### 2.1 部分修复：MES-QC-16（`qc_ipqc` 幂等键对 `process_id IS NULL` 失效）
- 已做：`uk_ipqc_wo_process(tenant_id, mes_wo_id, process_id)`。
- 未做：`process_id` 为 NULL 时 MySQL 唯一键不去重（NULL 互不相等），关键工序 `processId` 为空时仍可重复建单。
- 建议（应用侧，非本批次）：`QcIpqcServiceImpl.existsByWoAndProcess` 改用 `isNull` 分支；`autoCreateForKeyProcess` 在 `processId == null` 时按 `stepRecordId` 幂等；DB 侧可再追加 `uk_ipqc_wo_step(tenant_id, mes_wo_id, step_record_id)`（本批次未加，因 `step_record_id` 亦可空且需先确认业务是否允许同工单多卡）。

### 2.2 部分修复：MES-NCR-05（`uk_source` 对 NULL 不去重）
- 已做：`uk_source` 补 `tenant_id` 维度。
- **DDL 无法单独修好该问题**，理由：`NcrOrderServiceImpl.autoCreateFromExit` 的 PROCESS 分支把 `source_id` 传 null。若把列改为 `NOT NULL DEFAULT 0`，则所有 PROCESS 来源 NCR 共用 `source_id = 0` 而**互相撞键**（一个租户只能存在一张 PROCESS NCR），比现状更糟；用 `IFNULL(source_id,0)` 生成列同理。
- 建议（应用侧）：PROCESS 分支传 `stepRecordId` 作为 `source_id`（审计原建议），随后 DB 唯一键即自然生效。

### 2.3 未修复：ERP-PU-02（回写幂等键先占位、事务内不回滚）
- **本条不是 DDL 项**：根因是 `PuPurchaseReceiveWritebackServiceImpl:53-58` 在方法入口就 `RedisUtils.setObjectIfAbsent(..., 24h)`，后续校验失败/异常回滚时 Redis key 不回滚 → 24h 内永久跳过回写。审计建议亦为纯应用层（占位后移到校验之后 + `TransactionSynchronization.afterCommit` 写 key / 失败删 key，`purId == null` 由静默 return 改抛异常）。
- 本批次未落任何 DDL（`pu_purchase_order_line.received_quantity` 的累加本身无需唯一键）；**请主代理确认该条已由 ERP 采购修复代理承接**。

### 2.4 未修复（超出本批次范围，建议另派）
| 项 | 说明 |
|---|---|
| MES-PRO-19 | `pro_work_order.erp_wo_id` 仍为普通索引；建议升为 `UNIQUE(tenant_id, erp_wo_id)`（与 MES-PRO-10 同源，本次未做：ERP 改单重发新号是否合法需业务确认，改错会让 ERP 改单直接失败） |
| ERP-BD-09 的金额部分 | `bd_client_credit.client_credit` 仍为 `int(11)`，与 `so_credit_usage.amount decimal(14,2)` 口径不一致；需同步改实体为 `BigDecimal`，属应用+DDL 混合改动，未纳入本批次 |
| `bd_client_credit` 软删除占用唯一键 | 新加 `uk_client_tenant` 后，`del_flag='2'` 的历史行会挡住同 `client_id` 的新行；该表当前无写入 Service/Controller，风险极低；若后续加维护入口，建议改物理删除或把 `del_flag` 纳入键设计（存量脚本已写明前置检查） |
| `so_credit_usage` 二次冻结/释放周期 | `(tenant_id, so_id, status)` 下，订单「冻结→已用→释放」后再「冻结→已用→释放」的第二条 RELEASED 会撞键；`release()` 只更新 FROZEN/USED 行，正常流程不触发（详见 §6 风险） |
| `jimu_*` / `onl_drag_*` 共 10 张表的 tenant_id 类型 | 未改，待业务确认是否纳入多租户（见 §1.4） |
| `ai_flow` / `ai_tool` / `sys_auto_code_rule` / `wf_category` 唯一键 | 待业务确认是否需租户维度；改动位于 `ry-vue.sql`（本批次禁止编辑），见 §1.3 第 54/55/74/76 条 |

---

## 3. 主脚本合并清单

**本批次无需主代理合并任何内容。**

| 目标文件 | 状态 | 说明 |
|---|---|---|
| `script/sql/ry-erp.sql` | ✅ 已由本代理直接编辑（本批次独占授权） | 25 个唯一键补 tenant_id（§1.1）、2 个新增唯一键（§1.5）、8 处 tenant_id 类型收敛（§1.4） |
| `script/sql/ry-mes.sql` | ✅ 已由本代理直接编辑（本批次独占授权） | 25 个唯一键补 tenant_id（§1.2）、3 个新增唯一键（§1.5） |
| `script/sql/ry-iot.sql` | ⛔ 未触碰（B3 IoT 代理独占） | 本批次无 IoT 表改动需求 |
| `script/sql/ry-vue.sql` | ⛔ 未触碰（禁止编辑） | 27 个唯一键全部豁免；若主代理决定收敛 `ai_flow`/`ai_tool`/`sys_auto_code_rule`/`wf_category`，需另行派单（4 行 UNIQUE INDEX） |
| `doc/system/数据库增量变更记录.md` | ⛔ 未触碰（禁止编辑） | 登记内容见 §4 |
| 任何 java / vue / yml | ⛔ 未触碰 | — |

---

## 4. DDL 登记（供主代理写入 `doc/system/数据库增量变更记录.md`）

> 建议登记条目（2026-09-20）：

| 库 | 表 | 变更 | 类型 |
|---|---|---|---|
| ry-erp | bd_bom | `uk_parent_version(parent_material_id,bom_version)` → `(parent_material_id,bom_version,tenant_id)` | 唯一键维度修复 |
| ry-erp | bd_material | `uk_material_code(material_code)` → `(material_code,tenant_id)` | 唯一键维度修复 |
| ry-erp | bd_material_vendor | `uk_material_supplier(material_id,vendor_id)` → `(+tenant_id)` | 唯一键维度修复 |
| ry-erp | bd_serial_number_rule / bd_unit_of_measure / bd_vendor / bd_workshop / bd_workstation | 唯一键补 `tenant_id`（各 1 个） | 唯一键维度修复 |
| ry-erp | po_requisition / prd_material_requirement / prd_production_plan / prd_work_order / prd_work_report | 唯一键补 `tenant_id`（各 1 个） | 唯一键维度修复 |
| ry-erp | pu_purchase_order / pu_purchase_requisition / pu_purchase_return | 唯一键补 `tenant_id`；`tenant_id` varchar(64)→varchar(20) | 唯一键维度修复 + 类型收敛 |
| ry-erp | pu_purchase_order_line / pu_purchase_requisition_line / pu_purchase_return_line | `tenant_id` varchar(64)→varchar(20) | 类型收敛 |
| ry-erp | sch_team_member / so_price_list_line | 唯一键补 `tenant_id` | 唯一键维度修复 |
| ry-erp | wm_barcode / wm_barcode_config | 唯一键补 `tenant_id`；`tenant_id` varchar(64)→varchar(20) | 唯一键维度修复 + 类型收敛 |
| ry-erp | wm_batch / wm_material_handover / wm_sales_issue / wm_sn / wm_warehouse | 唯一键补 `tenant_id`（各 1 个） | 唯一键维度修复 |
| ry-erp | bd_client_credit | 新增 `UNIQUE uk_client_tenant(client_id,tenant_id)`（ERP-BD-09） | 新增幂等/唯一约束 |
| ry-erp | so_credit_usage | 新增 `UNIQUE uk_credit_so_status(tenant_id,so_id,status)`（ERP-SO-03） | 新增幂等/唯一约束 |
| ry-mes | dc_collection_point / dc_data_record / eq_equipment / eq_repair / lt_lot / lt_sn_binding | 唯一键补 `tenant_id`（各 1 个，MES-LT-05） | 唯一键维度修复 |
| ry-mes | mes_label_template / mes_mbom / mes_station_device / ncr_defect_code / ncr_rework_order | 唯一键补 `tenant_id`（各 1 个） | 唯一键维度修复 |
| ry-mes | ncr_order | `uk_ncr_code`、`uk_source` 补 `tenant_id`（MES-NCR-05 的 DDL 部分） | 唯一键维度修复 |
| ry-mes | pro_andon / pro_dispatch / pro_flow_card(×2) / pro_sn / pro_work_order | 唯一键补 `tenant_id`（MES-PRO-10） | 唯一键维度修复 |
| ry-mes | qc_inspect_template / qc_ipqc / qc_iqc / qc_oqc / sop_document / tg_tooling | 唯一键补 `tenant_id` | 唯一键维度修复 |
| ry-mes | qc_iqc / qc_ipqc / qc_oqc | 新增 `uk_iqc_receive_material` / `uk_ipqc_wo_process` / `uk_oqc_so_material`（MES-QC-04） | 新增幂等唯一约束 |
| ry-iot | — | 无变更 | — |
| ry-vue | — | 无变更（27 个唯一键豁免） | — |

**存量库升级脚本**：`script/sql/update/2026-09-20-uk-tenant-dimension.sql`（幂等；在 ry-erp / ry-mes 分别执行，见 §5 执行须知）。

---

## 5. 验证记录（命令 + 结果）

无可用 DB 连接，**未编造任何执行结果**；以下为对主脚本的静态复核（`grep` 工具，ripgrep 语义）：

| 命令（工具调用） | 结果摘要 |
|---|---|
| `grep "UNIQUE INDEX" script/sql/ry-erp.sql` | **67 条** UNIQUE INDEX，**全部包含 `tenant_id`**（改动前 65 条中 40 条含、25 条缺；+2 新增 = 67）。逐行确认：`uk_parent_version(…,tenant_id)` L49、`uk_material_code(material_code,tenant_id)` L1080、`uk_material_supplier(…,tenant_id)` L1629、`uk_client_tenant(client_id,tenant_id)` L1018、`uk_credit_so_status(tenant_id,so_id,status)` L5165 等 |
| `grep "UNIQUE INDEX" script/sql/ry-mes.sql` | **40 条** UNIQUE INDEX，**全部包含 `tenant_id`**（改动前 37 条中 16 条含、21 条缺；+3 新增 = 40）。逐行确认：`uk_point_code(tenant_id,point_code)` L47、`uk_lot_code(tenant_id,lot_code)` L561、`uk_wo_no(tenant_id,wo_no)` L1987、`uk_ipqc_wo_process(tenant_id,mes_wo_id,process_id)` L2486、`uk_iqc_receive_material(tenant_id,receive_id,material_id)` L2524、`uk_oqc_so_material(tenant_id,so_id,material_id)` L2560 |
| `grep "tenant_id\` varchar\(64\)" script/sql/ry-erp.sql` | **0 匹配** → 8 张表的 `tenant_id` 已全部收敛为 varchar(20) |
| `grep "UNIQUE INDEX \`uk_(parent_version\|material_code\|material_supplier\|uom_code\|vendor_code\|workshop_code\|workstation_code)\`\(" script/sql` | 7 条命中，均为新定义（含 tenant_id），无残留旧定义 |

**未执行项**：无 DB 可连，故未执行 `SHOW INDEX` / 未验证 `ALTER` 实际耗时；存量脚本已在文件头写明「低峰执行 + 先备份」，并附执行后核验 SQL（第 C 部分）。

---

## 6. 存量库执行须知（前置检查与回滚）

脚本：`script/sql/update/2026-09-20-uk-tenant-dimension.sql`

### 6.1 执行顺序
1. **备份** `ry-erp`、`ry-mes`（`mysqldump`）。唯一键/索引变更本身可逆（DROP INDEX + 恢复原定义），但执行期间的锁等待可能影响业务。
2. 用支持 `DELIMITER` 的客户端（mysql CLI / Navicat / DBeaver）：第 0 部分（辅助存储过程）在 **ry-erp 与 ry-mes 各执行一次**。
3. 在 **ry-erp** 执行第 A 部分（25 个唯一键 + 2 个新增唯一键 + 8 处类型收敛）。
4. 在 **ry-mes** 执行第 B 部分（25 个唯一键 + 3 个新增唯一键）。
5. 执行第 C 部分核验（只读）。
6. 确认无误后执行第 E 部分清理辅助存储过程。

### 6.2 前置检查要点（脚本内已逐表给出注释版 SQL）
- **45 个「补 tenant_id」改动无需清数**：旧唯一键是新键的**严格子集**，旧键已保证唯一 ⇒ 新键不可能产生重复，`ADD UNIQUE` 不会因数据失败（脚本仍为每表附上重复检查 SQL 作留痕）。
- **5 个新增唯一键必须先查重**，否则 `ADD UNIQUE` 直接失败：
  - `bd_client_credit`：`SELECT tenant_id, client_id, COUNT(*) c, GROUP_CONCAT(credit_id), GROUP_CONCAT(del_flag) FROM bd_client_credit GROUP BY tenant_id, client_id HAVING c>1;`（**逻辑删除行同样占用唯一键**）
  - `so_credit_usage`：`GROUP BY tenant_id, so_id, status HAVING c>1`，另查同订单多条 FROZEN
  - `qc_iqc` / `qc_oqc` / `qc_ipqc`：分别按 `(tenant_id, receive_id, material_id)` / `(tenant_id, so_id, material_id)` / `(tenant_id, mes_wo_id, process_id)` 查重
  - 三张 QC 表虽有 `del_flag`，但当前**没有** iqc/ipqc/oqc 的删除入口（仅 `QcTemplateServiceImpl:105` 有 `deleteByIds`），逻辑删除行占用键的风险可忽略。
- **NULL 语义**：`qc_iqc.receive_id`、`qc_oqc.so_id`、`qc_ipqc.process_id`、`ncr_order.source_id` 均可为 NULL，唯一键对 NULL 不去重（脚本注释已逐条标注受影响场景）。
- **类型收敛前**：检查 `tenant_id IS NULL` 与 `LENGTH(tenant_id) > 20` 的存量行（脚本第 0.6 部分给出 SQL）。

### 6.3 停写 / 缓存
- **建议低峰执行，无需停写**：MySQL 5.7 在「同一 ALTER 内 DROP INDEX + ADD UNIQUE INDEX」场景可能退化为 COPY 算法（重建表 + 持写锁），大表（`pu_purchase_order_line`、`dc_data_record`、`pro_work_order`、`wm_barcode`）请低峰执行；不能接受锁时改用 `pt-online-schema-change` / `gh-ost`。
- **无需清缓存**：多租户拦截器与 MyBatis-Plus 不缓存索引元数据，无需重启应用或清 Redis。

### 6.4 幂等性
- 脚本**可重复执行**：辅助过程先查 `information_schema.STATISTICS`，索引已含 `tenant_id` → `[SKIP]`；索引不存在 → `[SKIP]` 并提示人工核对；类型已合规 → `[SKIP]`。不使用 `ADD INDEX IF NOT EXISTS`（MySQL 5.7 不支持）。
- 中途失败后重跑安全：已成功的表会被跳过。
- 若确实需要一次性、不可重复的等价写法，可忽略 `[SKIP]` 逻辑直接执行脚本第 D 部分注释里的原生 `ALTER`（**那些是一次性语句，重复执行会在 DROP/ADD 处报错**）——本脚本已提供动态 SQL 幂等实现，故不推荐该路径。

### 6.5 回滚方法
1. 在目标库（ry-erp / ry-mes）执行第 0 部分重建 `_nexus_restore_uk`。
2. 取消注释第 D 部分对应库的回滚块并执行：`CALL _nexus_restore_uk('<表>','<索引>','<原列清单>')` → 内部 `DROP INDEX` + `ADD UNIQUE INDEX(原定义)`；**每个表节注释中 `->` 左侧的列清单即原定义**（例：`bd_material.uk_material_code` 回滚为 `(material_code)`）。
3. 新增唯一键直接删除：`ALTER TABLE bd_client_credit DROP INDEX uk_client_tenant;`、`ALTER TABLE so_credit_usage DROP INDEX uk_credit_so_status;`、`ALTER TABLE qc_iqc DROP INDEX uk_iqc_receive_material;`、`ALTER TABLE qc_ipqc DROP INDEX uk_ipqc_wo_process;`、`ALTER TABLE qc_oqc DROP INDEX uk_oqc_so_material;`
4. `tenant_id` 类型收敛**不回滚**（varchar(20)→varchar(64) 无业务意义）；如确需回滚，脚本第 D 部分给出 `MODIFY COLUMN` 示例。

### 6.6 风险与后果
| 风险 | 影响 | 缓解 |
|---|---|---|
| 新增唯一键遇存量重复数据 | `ALTER` 失败（无数据损坏），脚本在该表处中断 | 先跑 §6.2 查重；清理后再执行（幂等重跑会跳过已成功的表） |
| `bd_client_credit` 软删除行占用键 | 同客户重建信用记录失败 | 该表当前无写入入口；若加维护入口需改物理删除 |
| `so_credit_usage` 二次周期 | 「冻结→已用→释放」再重复一遍时第二条 RELEASED 撞键 | `release()` 仅更新 FROZEN/USED 行，正常流程不触发；如需支持反复周期，应用层改为复用同一行 |
| `qc_*` 自动建单撞键 | 并发/重投的第二次插入抛 `DuplicateKeyException`（当前未捕获 → 消费者报错重投） | 应用侧需按 MES-QC-04 建议捕获 `DuplicateKeyException` 视为幂等成功；**DDL 先行不会造成数据错误，只会把「静默重复建单」变成「显式冲突」** |
| 索引前缀变化 | `uk_wo_no` 等索引左前缀由业务键变为 `tenant_id`，仅按业务键单列查询不再走该索引 | 多租户插件始终注入 `tenant_id`，正常业务查询不受影响；纯后台 SQL 若只按业务键查需注意 |
| COPY 算法锁表 | 大表执行期间写入阻塞 | 低峰执行 / 使用 gh-ost |

---

## 7. 与审计/任务描述的差异说明

1. **`so_credit_usage` 唯一键列序**：任务描述为 `(tenant_id, so_id, status)`，审计 ERP-SO-03 建议为 `uk_credit_so(so_id, tenant_id)`。本批次采用**任务描述的 `(tenant_id, so_id, status)`**：现有 `CreditCheckServiceImpl` 在同一订单上原地更新状态（`release`/`convertToUsed` 用 `updateById`），且 `checkAndFreeze` 的幂等分支条件正是 `so_id + status='FROZEN'`；用 `(so_id, tenant_id)` 会让「释放后重新确认」直接撞键，而 `(tenant_id, so_id, status)` 与现有代码语义完全对齐。详见存量脚本 A-新增 2 注释与 §2.4/§6.6 残余风险。
2. **新增键数量**：任务列出的 MES-QC-04 建议 3 个（iqc/ipqc/oqc）、ERP-BD-09 建议 1 个（`bd_client_credit` 按 client_id 唯一）、ERP-SO-03 建议 1 个（`so_credit_usage`）= 5 个，与实际扫描一致，**无冲突**；`ry-erp.sql` / `ry-mes.sql` 中此前确实不存在这 5 个键（已逐表 `grep` 确认）。
3. **`po_requisition`**：任务给的前缀清单未含 `po_`，但该表位于 ry-erp 业务库、含 `tenant_id`、且 `requisition_no` 为单据号业务键，故按多租户业务表处理（补 `tenant_id`）。
4. **MES-NCR-05 / ERP-PU-02**：前者只能部分落地（§2.2），后者非 DDL 项（§2.3），均已在报告中标明，避免主代理误以为「已闭环」。
