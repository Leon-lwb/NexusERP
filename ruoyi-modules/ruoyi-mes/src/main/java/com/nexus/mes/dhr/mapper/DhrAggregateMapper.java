package com.nexus.mes.dhr.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * DHR 批记录聚合查询 Mapper（<b>只读</b>）。
 *
 * <p>所有语句显式携带 {@code tenant_id}（原生 SQL 不受多租户插件保护，必须手写）；
 * 结果以 {@code List<Map<String,Object>>} 返回，key 为数据库列名（snake_case）——
 * 批记录是「当时那一行的快照」，保留原始列名反而便于审计逐列核对。</p>
 *
 * <p>列名口径已按 {@code script/sql/ry-mes.sql} 逐个核对：
 * MES 工单主键是 {@code pro_work_order.mes_wo_id}（<b>不是</b> work_order_id）；
 * {@code lt_wo_consumption / ncr_order / sop_view_log / tg_usage_record} 用 {@code wo_id}；
 * {@code qc_iqc / qc_oqc / eq_* / mes_station_device / lt_sn_binding} 无工单外键，
 * 只能走间接链（见各方法注释与 {@code linkage} 字段标注）。</p>
 *
 * @author Nexus
 */
public interface DhrAggregateMapper {

    // ==================== WO_INFO：pro_work_order 系（直接带 mes_wo_id） ====================

    /**
     * 工单主档。
     */
    @Select("SELECT mes_wo_id, erp_wo_id, wo_no, material_id, material_code, material_name, "
        + "planned_qty, completed_qty, scrap_qty, unit_name, plan_start_date, plan_end_date, "
        + "actual_start_date, actual_end_date, workshop_name, equipment_id, bom_id, routing_id, "
        + "wo_status, source_type, release_package_code, release_package_ver, remark "
        + "FROM pro_work_order WHERE tenant_id = #{tenantId} AND del_flag = '0' AND mes_wo_id = #{woId}")
    List<Map<String, Object>> selectWorkOrder(@Param("woId") Long woId, @Param("tenantId") String tenantId);

    /**
     * 工单工序步骤。
     */
    @Select("SELECT step_id, sequence_num, step_type, process_code, process_name, workcenter_code, "
        + "planned_qty, completed_qty, scrap_qty, step_status, is_key_process, start_time, end_time, firmware_source "
        + "FROM pro_work_order_step WHERE tenant_id = #{tenantId} AND del_flag = '0' AND mes_wo_id = #{woId} "
        + "ORDER BY sequence_num")
    List<Map<String, Object>> selectWorkOrderSteps(@Param("woId") Long woId, @Param("tenantId") String tenantId);

    /**
     * 工单 BOM 明细快照。
     */
    @Select("SELECT wo_bom_line_id, child_material_code, child_material_name, quantity_per_parent, loss_rate, "
        + "total_quantity, process_code, process_name, workstation_code, operation_content, backflush_flag, sort_order "
        + "FROM pro_work_order_bom_line WHERE tenant_id = #{tenantId} AND del_flag = '0' AND mes_wo_id = #{woId} "
        + "ORDER BY sort_order")
    List<Map<String, Object>> selectWorkOrderBomLines(@Param("woId") Long woId, @Param("tenantId") String tenantId);

    // ==================== PROCESS：流转卡/派工/工位报工（直接带 mes_wo_id） ====================

    /**
     * 流转卡。
     */
    @Select("SELECT card_id, card_no, material_code, material_name, planned_qty, completed_qty, scrap_qty, "
        + "card_status, current_step_seq, barcode, create_time FROM pro_flow_card "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND mes_wo_id = #{woId} ORDER BY create_time")
    List<Map<String, Object>> selectFlowCards(@Param("woId") Long woId, @Param("tenantId") String tenantId);

    /**
     * 流转卡工序记录（含进/出站校验结论，是 DHR 的过程证据主体）。
     */
    @Select("SELECT step_record_id, card_no, sequence_num, process_code, process_name, workcenter_code, "
        + "is_key_process, step_status, operator_name, planned_qty, good_qty, defect_qty, scrap_qty, "
        + "actual_start_time, actual_end_time, entry_check_result, entry_check_remark, "
        + "exit_check_result, exit_check_remark, skip_reason FROM pro_flow_card_step "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND mes_wo_id = #{woId} ORDER BY sequence_num")
    List<Map<String, Object>> selectFlowCardSteps(@Param("woId") Long woId, @Param("tenantId") String tenantId);

    /**
     * 派工单。
     */
    @Select("SELECT dispatch_id, dispatch_no, card_no, sequence_num, process_code, process_name, workcenter_code, "
        + "operator_name, planned_qty, completed_qty, good_qty, defect_qty, scrap_qty, dispatch_status, "
        + "plan_start_time, plan_end_time, actual_start_time, actual_end_time FROM pro_dispatch "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND mes_wo_id = #{woId}")
    List<Map<String, Object>> selectDispatches(@Param("woId") Long woId, @Param("tenantId") String tenantId);

    /**
     * 工位终端报工记录（设备通道）。
     */
    @Select("SELECT report_id, report_type, sn_code, station_code, operator_code, operator_name, result, "
        + "defect_codes, good_qty, defect_qty, create_time FROM mes_station_report "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND mes_wo_id = #{woId} ORDER BY create_time")
    List<Map<String, Object>> selectStationReports(@Param("woId") Long woId, @Param("tenantId") String tenantId);

    // ==================== MATERIAL：消耗/批次/上料 ====================

    /**
     * 工单批次消耗记录（{@code wo_id} 即 MES 工单ID）。
     */
    @Select("SELECT consumption_id, wo_no, lot_id, lot_code, material_id, material_code, consumed_qty, "
        + "consumed_time, operator_name, remark FROM lt_wo_consumption "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND wo_id = #{woId} ORDER BY consumed_time")
    List<Map<String, Object>> selectWoConsumptions(@Param("woId") Long woId, @Param("tenantId") String tenantId);

    /**
     * 批次主档（按批次ID批量取；含 iqc_id 供 QC 分册走间接链）。
     */
    @Select("<script>SELECT lot_id, lot_code, material_id, material_code, material_name, supplier_name, "
        + "supplier_lot, quantity, consumed_qty, produced_date, expiry_date, status, qc_status, iqc_id "
        + "FROM lt_lot WHERE tenant_id = #{tenantId} AND del_flag = '0' AND lot_id IN "
        + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
        + "</script>")
    List<Map<String, Object>> selectLotsByIds(@Param("ids") List<Long> ids, @Param("tenantId") String tenantId);

    /**
     * 批次主档（按批次编码；构建入参显式指定 lotNo 时补充）。
     */
    @Select("SELECT lot_id, lot_code, material_id, material_code, material_name, supplier_name, supplier_lot, "
        + "quantity, consumed_qty, produced_date, expiry_date, status, qc_status, iqc_id FROM lt_lot "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND lot_code = #{lotCode}")
    List<Map<String, Object>> selectLotByCode(@Param("lotCode") String lotCode, @Param("tenantId") String tenantId);

    /**
     * 工位上料台账（上料防错证据）。
     */
    @Select("SELECT load_id, station_code, process_code, workstation_code, material_code, material_name, "
        + "batch_code, feeder_slot, is_alternative, operator_name, load_time, status FROM mes_material_load "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND mes_wo_id = #{woId} ORDER BY load_time")
    List<Map<String, Object>> selectMaterialLoads(@Param("woId") Long woId, @Param("tenantId") String tenantId);

    // ==================== EQUIPMENT：工位终端 + 设备维保/维修/点检（间接链） ====================

    /**
     * 工位终端设备（{@code mes_station_device} 无工单列，按本工单报工过的工位编码关联，
     * linkage=BY_EQUIPMENT_TIME）。
     */
    @Select("<script>SELECT device_id, device_code, station_code, process_code, app_version, operator_name, "
        + "last_heartbeat FROM mes_station_device WHERE tenant_id = #{tenantId} AND del_flag = '0' AND station_code IN "
        + "<foreach collection='stations' item='s' open='(' separator=',' close=')'>#{s}</foreach>"
        + " ORDER BY station_code</script>")
    List<Map<String, Object>> selectStationDevicesByStations(@Param("stations") List<String> stations,
                                                             @Param("tenantId") String tenantId);

    /**
     * 由工位编码反查设备台账ID（{@code eq_equipment.workcenter_code} 对齐工位/工作中心编码）。
     */
    @Select("<script>SELECT equipment_id, equipment_code, equipment_name, model, status, location "
        + "FROM eq_equipment WHERE tenant_id = #{tenantId} AND del_flag = '0' AND workcenter_code IN "
        + "<foreach collection='codes' item='c' open='(' separator=',' close=')'>#{c}</foreach>"
        + "</script>")
    List<Map<String, Object>> selectEquipmentByWorkcenterCodes(@Param("codes") List<String> codes,
                                                               @Param("tenantId") String tenantId);

    /**
     * 设备保养执行记录（linkage=BY_EQUIPMENT_TIME）。
     */
    @Select("<script>SELECT record_id, equipment_id, maintenance_type, executor_name, execute_time, content, "
        + "result, duration_minutes FROM eq_maintenance_record WHERE tenant_id = #{tenantId} AND del_flag = '0' "
        + "AND equipment_id IN <foreach collection='equipmentIds' item='e' open='(' separator=',' close=')'>#{e}</foreach>"
        + "<if test=\"start != null\"> AND execute_time &gt;= #{start}</if>"
        + "<if test=\"end != null\"> AND execute_time &lt;= #{end}</if></script>")
    List<Map<String, Object>> selectMaintenanceRecords(@Param("equipmentIds") List<Long> equipmentIds,
                                                      @Param("start") Date start, @Param("end") Date end,
                                                      @Param("tenantId") String tenantId);

    /**
     * 设备维修记录（linkage=BY_EQUIPMENT_TIME）。
     */
    @Select("<script>SELECT repair_id, repair_no, equipment_id, fault_type, fault_desc, reporter_name, "
        + "repairer_name, repair_start, repair_end, downtime_hours, status FROM eq_repair "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND equipment_id IN "
        + "<foreach collection='equipmentIds' item='e' open='(' separator=',' close=')'>#{e}</foreach>"
        + "<if test=\"start != null\"> AND repair_start &gt;= #{start}</if>"
        + "<if test=\"end != null\"> AND repair_start &lt;= #{end}</if></script>")
    List<Map<String, Object>> selectRepairs(@Param("equipmentIds") List<Long> equipmentIds,
                                            @Param("start") Date start, @Param("end") Date end,
                                            @Param("tenantId") String tenantId);

    /**
     * 设备点检记录（linkage=BY_EQUIPMENT_TIME）。
     */
    @Select("<script>SELECT record_id, equipment_id, station_code, inspector_name, inspect_time, results, "
        + "overall_pass FROM eq_inspection_record WHERE tenant_id = #{tenantId} AND del_flag = '0' "
        + "AND equipment_id IN <foreach collection='equipmentIds' item='e' open='(' separator=',' close=')'>#{e}</foreach>"
        + "<if test=\"start != null\"> AND inspect_time &gt;= #{start}</if>"
        + "<if test=\"end != null\"> AND inspect_time &lt;= #{end}</if></script>")
    List<Map<String, Object>> selectInspectionRecords(@Param("equipmentIds") List<Long> equipmentIds,
                                                      @Param("start") Date start, @Param("end") Date end,
                                                      @Param("tenantId") String tenantId);

    // ==================== TOOLING：工装领用 + 工装台账（校准字段） ====================

    /**
     * 工装使用记录（{@code wo_id} 即 MES 工单ID）。
     */
    @Select("SELECT usage_id, tooling_id, wo_no, workstation_name, operator_name, checkout_time, return_time, "
        + "usage_count, usage_hours, status FROM tg_usage_record "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND wo_id = #{woId} ORDER BY checkout_time")
    List<Map<String, Object>> selectToolingUsages(@Param("woId") Long woId, @Param("tenantId") String tenantId);

    /**
     * 工装台账（含校准字段：校准周期/上次/下次校准日期/校准结果）。
     */
    @Select("<script>SELECT tooling_id, tooling_code, tooling_name, tooling_type, spec, status, "
        + "total_life_count, used_count, calibration_cycle_days, last_calibration_date, next_calibration_date, "
        + "calibration_result, calibration_remark FROM tg_tooling WHERE tenant_id = #{tenantId} AND del_flag = '0' "
        + "AND tooling_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
        + "</script>")
    List<Map<String, Object>> selectToolings(@Param("ids") List<Long> ids, @Param("tenantId") String tenantId);

    // ==================== SOP：查阅记录（wo_id 即 MES 工单ID） ====================

    /**
     * SOP 查阅记录（签名摘要由 {@code DhrSignatureAccessor} 从 master 库补充）。
     */
    @Select("SELECT log_id, sop_id, version_no, viewer_id, viewer_name, view_time, process_name, "
        + "workstation_id, signature_id, ip_address, user_agent FROM sop_view_log "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND wo_id = #{woId} ORDER BY view_time")
    List<Map<String, Object>> selectSopViewLogs(@Param("woId") Long woId, @Param("tenantId") String tenantId);

    // ==================== QC：IPQC 直连 / IQC·OQC 间接链 ====================

    /**
     * 过程检验（{@code qc_ipqc} 直接带 mes_wo_id）。
     */
    @Select("SELECT ipqc_id, ipqc_no, wo_no, process_code, process_name, material_code, material_name, "
        + "sample_qty, defect_qty, defect_rate, inspect_result, inspector_name, inspect_time, status, photo_oss_ids "
        + "FROM qc_ipqc WHERE tenant_id = #{tenantId} AND del_flag = '0' AND mes_wo_id = #{woId} ORDER BY inspect_time")
    List<Map<String, Object>> selectIpqcByWo(@Param("woId") Long woId, @Param("tenantId") String tenantId);

    /**
     * 来料检验（按 iqc_id 批量取；链路 = 工单 → lt_wo_consumption.lot_id → lt_lot.iqc_id → qc_iqc）。
     */
    @Select("<script>SELECT iqc_id, iqc_no, receive_id, material_id, material_code, material_name, vendor_name, "
        + "quantity, sample_qty, inspect_result, inspector_name, inspect_time, status, photo_oss_ids FROM qc_iqc "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND iqc_id IN "
        + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
        + "</script>")
    List<Map<String, Object>> selectIqcByIds(@Param("ids") List<Long> ids, @Param("tenantId") String tenantId);

    /**
     * 来料检验（回退口径：物料 + 工单时间窗，linkage=BY_MATERIAL_TIME）。
     */
    @Select("<script>SELECT iqc_id, iqc_no, receive_id, material_id, material_code, material_name, vendor_name, "
        + "quantity, sample_qty, inspect_result, inspector_name, inspect_time, status FROM qc_iqc "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND material_id = #{materialId}"
        + "<if test=\"start != null\"> AND create_time &gt;= #{start}</if>"
        + "<if test=\"end != null\"> AND create_time &lt;= #{end}</if> LIMIT 200</script>")
    List<Map<String, Object>> selectIqcByMaterialTime(@Param("materialId") Long materialId,
                                                      @Param("start") Date start, @Param("end") Date end,
                                                      @Param("tenantId") String tenantId);

    /**
     * 出货检验（{@code qc_oqc} 无工单外键，只能按物料 + 工单时间窗，linkage=BY_MATERIAL_TIME）。
     */
    @Select("<script>SELECT oqc_id, oqc_no, so_id, material_id, material_code, material_name, quantity, "
        + "sample_qty, inspect_result, inspector_name, inspect_time, status FROM qc_oqc "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND material_id = #{materialId}"
        + "<if test=\"start != null\"> AND create_time &gt;= #{start}</if>"
        + "<if test=\"end != null\"> AND create_time &lt;= #{end}</if> LIMIT 200</script>")
    List<Map<String, Object>> selectOqcByMaterialTime(@Param("materialId") Long materialId,
                                                      @Param("start") Date start, @Param("end") Date end,
                                                      @Param("tenantId") String tenantId);

    /**
     * 检验明细（按 check_type + check_id 批量取）。
     */
    @Select("<script>SELECT detail_id, check_id, check_type, item_code, item_name, data_type, standard_desc, "
        + "unit, criticality, sample_qty, measured_value, nominal_value, lsl_value, usl_value, judge_result, seq_num "
        + "FROM qc_inspect_detail WHERE tenant_id = #{tenantId} AND del_flag = '0' AND check_type IN "
        + "<foreach collection='checkTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach>"
        + " AND check_id IN <foreach collection='checkIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
        + " ORDER BY check_type, check_id, seq_num</script>")
    List<Map<String, Object>> selectInspectDetails(@Param("checkTypes") List<String> checkTypes,
                                                  @Param("checkIds") List<Long> checkIds,
                                                  @Param("tenantId") String tenantId);

    // ==================== DATA_COLLECT：采集数据（直接带 mes_wo_id） ====================

    /**
     * 采集数据记录（按 mes_wo_id 直连，单工单上限 500 条防止快照膨胀）。
     */
    @Select("SELECT record_id, point_id, point_code, property_code, card_id, step_record_id, numeric_value, "
        + "text_value, collect_time, source, remark FROM dc_data_record "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND mes_wo_id = #{woId} "
        + "ORDER BY collect_time LIMIT 500")
    List<Map<String, Object>> selectDataRecords(@Param("woId") Long woId, @Param("tenantId") String tenantId);

    // ==================== SN_TRACE：SN / 批次绑定 / 关键件绑定日志 ====================

    /**
     * SN 主档（{@code pro_sn} 直接带 mes_wo_id；入参 sn 非空时精确到单件）。
     */
    @Select("<script>SELECT sn_id, sn_code, wo_no, card_no, material_code, material_name, current_step_seq, "
        + "sn_status, parent_sn_code, source, production_date, subcontract_no, create_time FROM pro_sn "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND mes_wo_id = #{woId}"
        + "<if test=\"sn != null and sn != ''\"> AND sn_code = #{sn}</if>"
        + " ORDER BY create_time LIMIT 500</script>")
    List<Map<String, Object>> selectSnByWo(@Param("woId") Long woId, @Param("sn") String sn,
                                           @Param("tenantId") String tenantId);

    /**
     * SN 批次绑定（{@code lt_sn_binding} 无工单外键，按工单内 SN 集合关联，linkage=BY_SN）。
     */
    @Select("<script>SELECT binding_id, sn_id, sn_code, lot_id, lot_code, material_id, bound_time FROM lt_sn_binding "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND sn_id IN "
        + "<foreach collection='snIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
        + "</script>")
    List<Map<String, Object>> selectSnBindingsBySnIds(@Param("snIds") List<Long> snIds,
                                                      @Param("tenantId") String tenantId);

    /**
     * 关键件绑定操作日志（按工单内 SN 编码集合关联，linkage=BY_SN）。
     */
    @Select("<script>SELECT log_id, action, parent_sn_code, component_sn_code, new_component_sn_code, reason, "
        + "operator_code, create_time FROM mes_sn_bind_log WHERE tenant_id = #{tenantId} AND del_flag = '0' "
        + "AND (parent_sn_code IN <foreach collection='snCodes' item='c' open='(' separator=',' close=')'>#{c}</foreach>"
        + " OR component_sn_code IN <foreach collection='snCodes' item='c2' open='(' separator=',' close=')'>#{c2}</foreach>)"
        + " ORDER BY create_time LIMIT 500</script>")
    List<Map<String, Object>> selectSnBindLogsBySnCodes(@Param("snCodes") List<String> snCodes,
                                                        @Param("tenantId") String tenantId);

    // ==================== NCR：不合格品单 / 缺陷 / 返工 ====================

    /**
     * NCR 单（{@code wo_id} 即 MES 工单ID，{@code wo_no} 作兜底）。
     */
    @Select("SELECT ncr_id, ncr_code, source_type, source_id, wo_no, process_name, material_code, material_name, "
        + "lot_code, defect_qty, disposition, status, review_time, disposition_time, disposition_signature_id, "
        + "created_time, closed_time, remark FROM ncr_order "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND (wo_id = #{woId} OR wo_no = #{woNo})")
    List<Map<String, Object>> selectNcrOrders(@Param("woId") Long woId, @Param("woNo") String woNo,
                                              @Param("tenantId") String tenantId);

    /**
     * NCR 缺陷明细。
     */
    @Select("<script>SELECT defect_id, ncr_id, defect_code, defect_name, defect_qty, defect_cause, root_cause_desc "
        + "FROM ncr_defect WHERE tenant_id = #{tenantId} AND del_flag = '0' AND ncr_id IN "
        + "<foreach collection='ncrIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
        + "</script>")
    List<Map<String, Object>> selectNcrDefects(@Param("ncrIds") List<Long> ncrIds, @Param("tenantId") String tenantId);

    /**
     * NCR 返工工单（原工单或本工单产生的 NCR）。
     */
    @Select("<script>SELECT rework_id, rework_code, ncr_id, original_wo_no, rework_wo_id, rework_wo_no, "
        + "rework_qty, rework_reason, status, create_time FROM ncr_rework_order "
        + "WHERE tenant_id = #{tenantId} AND del_flag = '0' AND (original_wo_id = #{woId}"
        + "<if test=\"ncrIds != null and ncrIds.size() > 0\"> OR ncr_id IN "
        + "<foreach collection='ncrIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>)"
        + "</script>")
    List<Map<String, Object>> selectNcrReworkOrders(@Param("woId") Long woId, @Param("ncrIds") List<Long> ncrIds,
                                                     @Param("tenantId") String tenantId);
}
