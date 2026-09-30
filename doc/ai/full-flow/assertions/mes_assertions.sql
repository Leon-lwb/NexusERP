/** 仅限 MES 数据源的只读验收证据；每条查询必须在该业务库分别执行。 */
/** 所有位置参数均须绑定，雪花主键使用精确字符串，并与 ERP 独立读回比对。 */
/** 消息下发来源核验，参数依次为 ERP 工单主键、租户，必须恰好返回一行。 */
SELECT COUNT(*) AS work_order_count FROM pro_work_order WHERE erp_wo_id = ? AND tenant_id = ? AND del_flag = '0';
/** 将 MES 工单和数量与已下达的 ERP 工单比对，参数依次为 ERP 工单主键、租户。 */
SELECT mes_wo_id, erp_wo_id, wo_no, material_id, planned_qty, wo_status, routing_id FROM pro_work_order WHERE erp_wo_id = ? AND tenant_id = ? AND del_flag = '0';
/** 工艺路线快照核验，参数依次为 MES 工单主键、租户。 */
SELECT step_id, mes_wo_id, sequence_num, process_id, process_code, step_status FROM pro_work_order_step WHERE mes_wo_id = ? AND tenant_id = ? AND del_flag = '0' ORDER BY sequence_num;
/** 核验分配不重复，参数依次为 MES 工单主键、租户。 */
SELECT card_id, card_no, mes_wo_id, planned_qty, completed_qty, scrap_qty, card_status FROM pro_flow_card WHERE mes_wo_id = ? AND tenant_id = ? AND del_flag = '0';
/** 工序顺序与状态核验，参数依次为流转卡主键、租户。 */
SELECT step_record_id, card_id, wo_step_id, sequence_num, step_status, good_qty, defect_qty, scrap_qty FROM pro_flow_card_step WHERE card_id = ? AND tenant_id = ? AND del_flag = '0' ORDER BY sequence_num;
/** 核验重复派工和超额派工，参数依次为工序记录主键、租户。 */
SELECT dispatch_id, card_id, step_record_id, operator_id, planned_qty, good_qty, defect_qty, scrap_qty, dispatch_status FROM pro_dispatch WHERE step_record_id = ? AND tenant_id = ? AND del_flag = '0';
/** 核验流转卡序列号生成，参数依次为流转卡主键、租户。 */
SELECT sn_id, sn_code, card_id, mes_wo_id, material_id, sn_status FROM pro_sn WHERE card_id = ? AND tenant_id = ? AND del_flag = '0';
/** 核验采集记录不重复，参数依次为采集点主键、流转卡主键、租户。 */
SELECT record_id, point_id, card_id, step_record_id, numeric_value, text_value, collect_time, source FROM dc_data_record WHERE point_id = ? AND card_id = ? AND tenant_id = ? AND del_flag = '0';
/** 核验有效及人工安灯呼叫，参数依次为 MES 工单主键、流转卡主键、租户。 */
SELECT andon_id, mes_wo_id, card_id, step_record_id, call_type, call_reason, andon_status, caller_id, responder_id, notify_time, resolve_time FROM pro_andon WHERE mes_wo_id = ? AND card_id = ? AND tenant_id = ? AND del_flag = '0';
/** 核验过程检验结果与不合格报告关联，参数依次为 MES 工单主键、工序主键、租户。 */
SELECT ipqc_id, ipqc_no, mes_wo_id, process_id, inspect_result, sample_qty, defect_qty, status FROM qc_ipqc WHERE mes_wo_id = ? AND process_id = ? AND tenant_id = ? AND del_flag = '0';
/** 不合格报告必须来自实际检验失败或退出事实，参数依次为来源类型、来源主键、租户。 */
SELECT ncr_id, source_type, source_id, wo_id, card_id, step_record_id, defect_qty, status FROM ncr_order WHERE source_type = ? AND source_id = ? AND tenant_id = ? AND del_flag = '0';
/** 来料检验仅核验实际 ERP 收货后的记录，参数依次为收货主键、租户。 */
SELECT iqc_id, receive_id, material_id, inspect_result, status FROM qc_iqc WHERE receive_id = ? AND tenant_id = ? AND del_flag = '0';
/** 出货检验仅核验实际销售订单关联，参数依次为销售订单主键、租户。 */
SELECT oqc_id, so_id, material_id, inspect_result, status FROM qc_oqc WHERE so_id = ? AND tenant_id = ? AND del_flag = '0';
