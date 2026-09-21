package com.nexus.mes.dhr.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.vo.SignatureVo;
import com.nexus.mes.dhr.domain.DhrSectionCode;
import com.nexus.mes.dhr.mapper.DhrAggregateMapper;
import com.nexus.mes.dhr.service.IDhrAggregateService;
import com.nexus.mes.dhr.support.DhrSignatureAccessor;
import com.nexus.mes.pro.domain.ProWorkOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * DHR 批记录分册聚合实现（<b>只读</b>）。
 *
 * <p><b>关联口径（审计可复核，绝不伪造工单外键）：</b></p>
 * <ul>
 *   <li>{@code BY_WORK_ORDER}：表自带 MES 工单列（{@code mes_wo_id}；{@code lt_wo_consumption}/
 *       {@code ncr_order}/{@code sop_view_log}/{@code tg_usage_record} 用 {@code wo_id}）；</li>
 *   <li>{@code BY_WO_CONSUMPTION}：经 {@code lt_wo_consumption.lot_id} 关联批次主档；</li>
 *   <li>{@code BY_INPUT_LOT}：构建入参显式指定 lotNo 时补充；</li>
 *   <li>{@code BY_LOT_IQC}：IQC 无工单列，链路 = 工单 → 消耗 → {@code lt_lot.iqc_id} → {@code qc_iqc}；</li>
 *   <li>{@code BY_MATERIAL_TIME}：IQC 回退口径与 OQC 唯一口径（物料 + 工单时间窗）；</li>
 *   <li>{@code BY_EQUIPMENT_TIME}：设备维保/维修/点检与工位终端（工单设备 + 工单时间窗）；</li>
 *   <li>{@code BY_SN}：{@code lt_sn_binding}/{@code mes_sn_bind_log} 无工单列，按工单内 SN 集合关联；</li>
 *   <li>{@code BY_NCR_ID}：NCR 缺陷明细按 ncr_id 关联。</li>
 * </ul>
 *
 * <p>每条条目的 key 为数据库列名（snake_case）——批记录是「当时那一行的快照」，
 * 保留原始列名便于审计逐列核对。</p>
 *
 * <p><b>数据源边界</b>：本类 {@code @DS("mes")}，但 SOP 分册要读 master 的 {@code es_signature}，
 * 故签名摘要一律经 {@link DhrSignatureAccessor}（{@code @DS("master") + REQUIRES_NEW}）读取 ——
 * 本类被 {@code DhrRecordServiceImpl#build} 的 MES 事务包住时，普通调用会因「事务内 @DS 不生效」
 * 打到 ry-mes 报 {@code Table 'ry-mes.es_signature' doesn't exist}。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("dhrAggregateService")
public class DhrAggregateServiceImpl implements IDhrAggregateService {

    /** 关联口径常量 */
    private static final String LINK_WO = "BY_WORK_ORDER";
    private static final String LINK_WO_CONSUMPTION = "BY_WO_CONSUMPTION";
    private static final String LINK_INPUT_LOT = "BY_INPUT_LOT";
    private static final String LINK_LOT_IQC = "BY_LOT_IQC";
    private static final String LINK_MATERIAL_TIME = "BY_MATERIAL_TIME";
    private static final String LINK_EQUIPMENT_TIME = "BY_EQUIPMENT_TIME";
    private static final String LINK_SN = "BY_SN";
    private static final String LINK_NCR_ID = "BY_NCR_ID";

    /** SOP 签名摘要逐 sopId 回查的上限（防止单工单查阅量过大拖慢构建） */
    private static final int SOP_SIGNATURE_LOOKUP_LIMIT = 50;

    private final DhrAggregateMapper aggregateMapper;
    private final DhrSignatureAccessor signatureAccessor;

    @Override
    public Map<String, List<Map<String, Object>>> aggregate(ProWorkOrder wo, String lotNo, String sn) {
        String tenantId = LoginHelper.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException("DHR 分册聚合缺少租户上下文（tenantId 为空），"
                + "异步线程调用前必须显式传播租户");
        }
        if (wo == null || wo.getMesWoId() == null) {
            throw new ServiceException("DHR 分册聚合缺少工单");
        }
        Long woId = wo.getMesWoId();
        String woNo = wo.getWoNo();
        Date start = windowStart(wo);
        Date end = windowEnd(wo);

        Map<String, List<Map<String, Object>>> sections = new LinkedHashMap<>();
        for (String code : DhrSectionCode.ordered()) {
            sections.put(code, new ArrayList<>());
        }

        // ---------- WO_INFO ----------
        addAll(sections.get(DhrSectionCode.WO_INFO), "WORK_ORDER", LINK_WO,
            aggregateMapper.selectWorkOrder(woId, tenantId));
        addAll(sections.get(DhrSectionCode.WO_INFO), "WO_STEP", LINK_WO,
            aggregateMapper.selectWorkOrderSteps(woId, tenantId));
        addAll(sections.get(DhrSectionCode.WO_INFO), "WO_BOM_LINE", LINK_WO,
            aggregateMapper.selectWorkOrderBomLines(woId, tenantId));

        // ---------- PROCESS ----------
        addAll(sections.get(DhrSectionCode.PROCESS), "FLOW_CARD", LINK_WO,
            aggregateMapper.selectFlowCards(woId, tenantId));
        addAll(sections.get(DhrSectionCode.PROCESS), "FLOW_CARD_STEP", LINK_WO,
            aggregateMapper.selectFlowCardSteps(woId, tenantId));
        addAll(sections.get(DhrSectionCode.PROCESS), "DISPATCH", LINK_WO,
            aggregateMapper.selectDispatches(woId, tenantId));
        List<Map<String, Object>> reports = aggregateMapper.selectStationReports(woId, tenantId);
        addAll(sections.get(DhrSectionCode.PROCESS), "STATION_REPORT", LINK_WO, reports);

        // ---------- MATERIAL ----------
        List<Map<String, Object>> consumptions = aggregateMapper.selectWoConsumptions(woId, tenantId);
        addAll(sections.get(DhrSectionCode.MATERIAL), "WO_CONSUMPTION", LINK_WO, consumptions);
        List<Long> consumedLotIds = distinctLongs(consumptions, "lot_id");
        List<Map<String, Object>> lots = consumedLotIds.isEmpty()
            ? new ArrayList<>() : aggregateMapper.selectLotsByIds(consumedLotIds, tenantId);
        addAll(sections.get(DhrSectionCode.MATERIAL), "LOT", LINK_WO_CONSUMPTION, lots);
        if (StringUtils.isNotBlank(lotNo)) {
            List<Map<String, Object>> inputLots = aggregateMapper.selectLotByCode(lotNo, tenantId);
            addAll(sections.get(DhrSectionCode.MATERIAL), "LOT_INPUT", LINK_INPUT_LOT, inputLots);
            for (Map<String, Object> row : inputLots) {
                if (!lots.contains(row)) {
                    lots.add(row);
                }
            }
        }
        addAll(sections.get(DhrSectionCode.MATERIAL), "MATERIAL_LOAD", LINK_WO,
            aggregateMapper.selectMaterialLoads(woId, tenantId));

        // ---------- EQUIPMENT（间接链：工单设备 + 本工单报工过的工位 → 工位终端/设备台账） ----------
        List<String> stationCodes = distinctStrings(reports, "station_code");
        if (!stationCodes.isEmpty()) {
            addAll(sections.get(DhrSectionCode.EQUIPMENT), "STATION_DEVICE", LINK_EQUIPMENT_TIME,
                aggregateMapper.selectStationDevicesByStations(stationCodes, tenantId));
        }
        List<Long> equipmentIds = new ArrayList<>();
        if (wo.getEquipmentId() != null) {
            equipmentIds.add(wo.getEquipmentId());
        }
        if (!stationCodes.isEmpty()) {
            List<Map<String, Object>> equipments =
                aggregateMapper.selectEquipmentByWorkcenterCodes(stationCodes, tenantId);
            addAll(sections.get(DhrSectionCode.EQUIPMENT), "EQUIPMENT_MASTER", LINK_EQUIPMENT_TIME, equipments);
            for (Long id : distinctLongs(equipments, "equipment_id")) {
                if (!equipmentIds.contains(id)) {
                    equipmentIds.add(id);
                }
            }
        }
        if (!equipmentIds.isEmpty()) {
            addAll(sections.get(DhrSectionCode.EQUIPMENT), "MAINTENANCE_RECORD", LINK_EQUIPMENT_TIME,
                aggregateMapper.selectMaintenanceRecords(equipmentIds, start, end, tenantId));
            addAll(sections.get(DhrSectionCode.EQUIPMENT), "REPAIR", LINK_EQUIPMENT_TIME,
                aggregateMapper.selectRepairs(equipmentIds, start, end, tenantId));
            addAll(sections.get(DhrSectionCode.EQUIPMENT), "INSPECTION_RECORD", LINK_EQUIPMENT_TIME,
                aggregateMapper.selectInspectionRecords(equipmentIds, start, end, tenantId));
        }

        // ---------- TOOLING ----------
        List<Map<String, Object>> usages = aggregateMapper.selectToolingUsages(woId, tenantId);
        addAll(sections.get(DhrSectionCode.TOOLING), "TOOLING_USAGE", LINK_WO, usages);
        List<Long> toolingIds = distinctLongs(usages, "tooling_id");
        if (!toolingIds.isEmpty()) {
            addAll(sections.get(DhrSectionCode.TOOLING), "TOOLING_MASTER", LINK_WO,
                aggregateMapper.selectToolings(toolingIds, tenantId));
        }

        // ---------- SOP（查阅记录在 ry-mes；签名摘要在 master，经 DhrSignatureAccessor 读） ----------
        List<Map<String, Object>> viewLogs = aggregateMapper.selectSopViewLogs(woId, tenantId);
        int lookup = 0;
        for (Map<String, Object> row : viewLogs) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("entry_type", "SOP_VIEW");
            item.put("linkage", LINK_WO);
            if (lookup < SOP_SIGNATURE_LOOKUP_LIMIT) {
                Long sopId = toLong(row.get("sop_id"));
                if (sopId != null) {
                    lookup++;
                    item.put("signature", latestSopViewSignature(sopId));
                }
            }
            sections.get(DhrSectionCode.SOP).add(item);
        }

        // ---------- QC（IPQC 直连；IQC 走批次链、回退物料时间窗；OQC 仅物料时间窗） ----------
        List<Map<String, Object>> ipqcRows = aggregateMapper.selectIpqcByWo(woId, tenantId);
        addAll(sections.get(DhrSectionCode.QC), "IPQC", LINK_WO, ipqcRows);
        List<Long> iqcIds = distinctLongs(lots, "iqc_id");
        List<Map<String, Object>> iqcRows;
        if (!iqcIds.isEmpty()) {
            iqcRows = aggregateMapper.selectIqcByIds(iqcIds, tenantId);
            addAll(sections.get(DhrSectionCode.QC), "IQC", LINK_LOT_IQC, iqcRows);
        } else if (wo.getMaterialId() != null) {
            iqcRows = aggregateMapper.selectIqcByMaterialTime(wo.getMaterialId(), start, end, tenantId);
            addAll(sections.get(DhrSectionCode.QC), "IQC", LINK_MATERIAL_TIME, iqcRows);
        } else {
            iqcRows = new ArrayList<>();
        }
        List<Map<String, Object>> oqcRows = wo.getMaterialId() == null
            ? new ArrayList<>()
            : aggregateMapper.selectOqcByMaterialTime(wo.getMaterialId(), start, end, tenantId);
        addAll(sections.get(DhrSectionCode.QC), "OQC", LINK_MATERIAL_TIME, oqcRows);
        List<String> checkTypes = new ArrayList<>();
        List<Long> checkIds = new ArrayList<>();
        collectChecks(ipqcRows, "ipqc_id", "IPQC", checkTypes, checkIds);
        collectChecks(iqcRows, "iqc_id", "IQC", checkTypes, checkIds);
        collectChecks(oqcRows, "oqc_id", "OQC", checkTypes, checkIds);
        if (!checkIds.isEmpty()) {
            addAll(sections.get(DhrSectionCode.QC), "INSPECT_DETAIL", LINK_WO,
                aggregateMapper.selectInspectDetails(checkTypes, checkIds, tenantId));
        }

        // ---------- DATA_COLLECT ----------
        addAll(sections.get(DhrSectionCode.DATA_COLLECT), "DATA_RECORD", LINK_WO,
            aggregateMapper.selectDataRecords(woId, tenantId));

        // ---------- SN_TRACE ----------
        List<Map<String, Object>> snRows = aggregateMapper.selectSnByWo(woId, sn, tenantId);
        addAll(sections.get(DhrSectionCode.SN_TRACE), "SN", LINK_WO, snRows);
        List<Long> snIds = distinctLongs(snRows, "sn_id");
        if (!snIds.isEmpty()) {
            addAll(sections.get(DhrSectionCode.SN_TRACE), "SN_LOT_BINDING", LINK_SN,
                aggregateMapper.selectSnBindingsBySnIds(snIds, tenantId));
        }
        List<String> snCodes = distinctStrings(snRows, "sn_code");
        if (!snCodes.isEmpty()) {
            addAll(sections.get(DhrSectionCode.SN_TRACE), "SN_BIND_LOG", LINK_SN,
                aggregateMapper.selectSnBindLogsBySnCodes(snCodes, tenantId));
        }

        // ---------- NCR ----------
        List<Map<String, Object>> ncrRows = aggregateMapper.selectNcrOrders(woId, woNo, tenantId);
        addAll(sections.get(DhrSectionCode.NCR), "NCR_ORDER", LINK_WO, ncrRows);
        List<Long> ncrIds = distinctLongs(ncrRows, "ncr_id");
        if (!ncrIds.isEmpty()) {
            addAll(sections.get(DhrSectionCode.NCR), "NCR_DEFECT", LINK_NCR_ID,
                aggregateMapper.selectNcrDefects(ncrIds, tenantId));
        }
        addAll(sections.get(DhrSectionCode.NCR), "NCR_REWORK", LINK_WO,
            aggregateMapper.selectNcrReworkOrders(woId, ncrIds, tenantId));

        return sections;
    }

    /**
     * 取该 SOP 最近一条 SOP_VIEW 签名摘要（读 master；失败不阻断构建 —— 摘要缺失比构建失败轻）。
     */
    private Map<String, Object> latestSopViewSignature(Long sopId) {
        try {
            List<SignatureVo> signatures = signatureAccessor.listByBiz(SignBizType.SOP_VIEW, sopId);
            if (signatures == null || signatures.isEmpty()) {
                return null;
            }
            SignatureVo latest = signatures.stream()
                .filter(s -> s.getSignedAt() != null)
                .max(Comparator.comparing(SignatureVo::getSignedAt))
                .orElse(signatures.get(signatures.size() - 1));
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("signature_id", latest.getSignatureId());
            summary.put("signer_user_id", latest.getSignerUserId());
            summary.put("signer_name", latest.getSignerName());
            summary.put("auth_method", latest.getAuthMethod());
            summary.put("signed_at", latest.getSignedAt());
            summary.put("chain_hash", latest.getChainHash());
            summary.put("doc_hash", latest.getDocHash());
            return summary;
        } catch (Exception e) {
            log.warn("DHR SOP 分册签名摘要读取失败(不阻断构建): sopId={}, cause={}", sopId, e.toString());
            return null;
        }
    }

    /**
     * 收集检验单ID与对应 check_type（qc_inspect_detail 按 check_type + check_id 定位）。
     */
    private void collectChecks(List<Map<String, Object>> rows, String idKey, String checkType,
                               List<String> checkTypes, List<Long> checkIds) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            Long id = toLong(row.get(idKey));
            if (id == null) {
                continue;
            }
            if (!checkTypes.contains(checkType)) {
                checkTypes.add(checkType);
            }
            checkIds.add(id);
        }
    }

    /**
     * 工单时间窗起点：实际开工 → 计划开工 → 建单时间。
     */
    private Date windowStart(ProWorkOrder wo) {
        if (wo.getActualStartDate() != null) {
            return wo.getActualStartDate();
        }
        if (wo.getPlanStartDate() != null) {
            return wo.getPlanStartDate();
        }
        return wo.getCreateTime();
    }

    /**
     * 工单时间窗终点：实际完工 → 计划完工 → 当前时间。
     */
    private Date windowEnd(ProWorkOrder wo) {
        if (wo.getActualEndDate() != null) {
            return wo.getActualEndDate();
        }
        if (wo.getPlanEndDate() != null) {
            return wo.getPlanEndDate();
        }
        return new Date();
    }

    /**
     * 把查询行追加进分册条目，并打上条目类型与关联口径。
     */
    private void addAll(List<Map<String, Object>> target, String entryType, String linkage,
                        List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("entry_type", entryType);
            item.put("linkage", linkage);
            target.add(item);
        }
    }

    /**
     * 去重取字符串列（保序）。
     */
    private List<String> distinctStrings(List<Map<String, Object>> rows, String key) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object value = row.get(key);
                if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                    set.add(String.valueOf(value));
                }
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 去重取数值列（保序，非数字静默跳过）。
     */
    private List<Long> distinctLongs(List<Map<String, Object>> rows, String key) {
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Long value = toLong(row.get(key));
                if (value != null) {
                    set.add(value);
                }
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 宽松转 Long（JDBC 可能返回 Long/Integer/BigDecimal/String）。
     */
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
