package com.nexus.station.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProWorkOrderBomLine;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderBomLineMapper;
import com.nexus.station.domain.MesMaterialLoad;
import com.nexus.station.domain.dto.StationMaterialLoadDto;
import com.nexus.station.mapper.MesMaterialLoadMapper;
import com.nexus.station.mapper.StationErpBatchMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 工位物料上料防错服务 (M3 契约 C19/C20)
 * <p>比对基准为工单 MBOM 快照 pro_work_order_bom_line(工单生命周期内不可变,
 * 冗余 process_code/workstation_code): 工位工序码 → 唯一 IN_PROGRESS 工序(与
 * reportBatch 定位语义一致) → 工单快照行按 process_code 过滤(NULL 工序行并入兜底),
 * 替代料经 bd_bom_alternative(bom_detail_id) 实时扩展允许集合。</p>
 * <p>扫码两套体系按前缀分流: B→wm_batch(批次码), RC→wm_barcode(条码实例)。
 * 匹配写 mes_material_load 台账(同工单+工位+物料旧 ACTIVE 置 REPLACED 换料语义)。</p>
 * <p>数据源: 类级 @DS("mes"); wm_batch/wm_barcode/bd_bom_alternative 在 ry-erp 库,
 * 编程式 push("erp") 读取(对齐 resolveOperator 模式)。注意 ERP 读取全部在写库之前完成
 * (本方法不加事务: 事务会把连接钉在 ry-mes 导致跨库读失败; 台账 update+insert
 * 无强一致需求, 换料置 REPLACED 失败仅延迟到下次上料)。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class StationMaterialService {

    /** 台账状态: 在机 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 台账状态: 已换料 */
    public static final String STATUS_REPLACED = "REPLACED";

    private final StationPassService stationPassService;
    private final ProFlowCardStepMapper stepMapper;
    private final ProFlowCardMapper flowCardMapper;
    private final ProWorkOrderBomLineMapper bomLineMapper;
    private final MesMaterialLoadMapper loadMapper;
    private final StationErpBatchMapper erpBatchMapper;

    /**
     * 上料校验+登记(C19): 扫批次码/条码 → 按 MBOM 快照(含替代料)比对 → 匹配写台账。
     *
     * @param dto {stationCode, barcode, feederSlot?, operatorCode?, operatorName?}
     * @return 匹配: {pass:true, processCode, woNo, matched, batch};
     *         不匹配: {pass:false, message, scanned, allowed}; 无法定位工单: {pass:false, message}
     */
    public Map<String, Object> load(StationMaterialLoadDto dto) {
        String station = StationPassService.normalizeStationCode(dto == null ? null : dto.getStationCode());
        String barcode = dto == null ? null : (dto.getBarcode() == null ? null : dto.getBarcode().trim());
        if (StringUtils.isBlank(station) || StringUtils.isBlank(barcode)) {
            throw new ServiceException("stationCode 与 barcode 不能为空");
        }
        Map<String, Object> data = new LinkedHashMap<>(8);
        data.put("stationCode", station);
        data.put("barcode", barcode);

        // 0. 条码前缀校验(先行快速失败, 免得无效码走到工单定位): B=批次码, RC=条码实例
        if (!barcode.startsWith("B") && !barcode.startsWith("RC")) {
            throw new ServiceException("无法识别的条码(仅支持 B 批次码 / RC 条码实例): " + barcode);
        }

        // 1. 工位工序码
        String processCode = stationPassService.resolveProcessCode(station);
        if (processCode == null) {
            data.put("pass", false);
            data.put("message", "无法解析工位工序编码(工位未心跳注册且工位码格式不符): " + station);
            return data;
        }
        data.put("processCode", processCode);

        // 2. 定位在制工序(唯一 IN_PROGRESS, 与 reportBatch 同语义: 计数工位同一时刻仅一张在制卡)
        ProFlowCardStep step = locateInProgressStep(processCode);
        if (step == null) {
            data.put("pass", false);
            data.put("message", "工位工序(" + processCode + ")当前无唯一在制工序, 无法定位上料工单(请先开工流转卡工序)");
            return data;
        }
        ProFlowCard card = flowCardMapper.selectById(step.getCardId());
        if (card == null) {
            data.put("pass", false);
            data.put("message", "在制工序所属流转卡不存在: stepRecordId=" + step.getStepRecordId());
            return data;
        }
        data.put("woNo", card.getWoNo());

        // 3. 允许物料清单: 快照行(本工序 + 未绑工序的行兜底)
        List<ProWorkOrderBomLine> lines = bomLineMapper.selectList(Wrappers.<ProWorkOrderBomLine>lambdaQuery()
            .eq(ProWorkOrderBomLine::getMesWoId, card.getMesWoId())
            .and(w -> w.eq(ProWorkOrderBomLine::getProcessCode, processCode)
                .or().isNull(ProWorkOrderBomLine::getProcessCode)));
        if (lines.isEmpty()) {
            data.put("pass", false);
            data.put("message", "工单 " + card.getWoNo() + " 无 MBOM 快照(或快照无本工序物料), 无法防错比对");
            return data;
        }

        // 4. 替代料扩展允许集合(按快照行 bom_detail_id 跨库查 bd_bom_alternative)
        List<Long> detailIds =
                lines.stream()
                        .map(ProWorkOrderBomLine::getBomDetailId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        Map<Long, Map<String, Object>> altByMaterialId = new HashMap<>();
        if (!detailIds.isEmpty()) {
            for (Map<String, Object> alt : queryErp(() -> erpBatchMapper.selectAlternativesByDetailIds(detailIds))) {
                Long altMaterialId = toLong(alt.get("altMaterialId"));
                if (altMaterialId != null) {
                    altByMaterialId.putIfAbsent(altMaterialId, alt);
                }
            }
        }

        // 5. 扫码解析(前缀分流: B=批次码→wm_batch, RC=条码→wm_barcode)
        Map<String, Object> batch;
        if (barcode.startsWith("B")) {
            batch = queryErp(() -> erpBatchMapper.selectBatchByCode(barcode));
        } else {
            batch = queryErp(() -> erpBatchMapper.selectBarcodeByCode(barcode));
        }
        if (batch == null) {
            throw new ServiceException("批次/条码不存在: " + barcode);
        }
        Long materialId = toLong(batch.get("materialId"));
        if (materialId == null) {
            throw new ServiceException("批次/条码未关联物料: " + barcode);
        }

        // 6. 匹配判定: BOM 主料优先, 其次替代料
        ProWorkOrderBomLine matchedLine = lines.stream()
            .filter(l -> materialId.equals(l.getChildMaterialId()))
            .findFirst().orElse(null);
        Map<String, Object> matchedAlt = null;
        if (matchedLine == null) {
            matchedAlt = altByMaterialId.get(materialId);
            if (matchedAlt != null) {
                Long detailId = toLong(matchedAlt.get("bomDetailId"));
                matchedLine = lines.stream()
                    .filter(l -> detailId != null && detailId.equals(l.getBomDetailId()))
                    .findFirst().orElse(null);
            }
        }
        if (matchedLine == null) {
            data.put("pass", false);
            data.put("message", "物料不匹配: 该批次物料不属于工单 " + card.getWoNo() + " 工序 " + processCode + " 的允许上料清单");
            Map<String, Object> scanned = new LinkedHashMap<>(4);
            scanned.put("materialId", materialId);
            scanned.put("materialCode", batch.get("materialCode"));
            scanned.put("materialName", batch.get("materialName"));
            scanned.put("batchCode", batch.get("batchCode") != null ? batch.get("batchCode") : barcode);
            data.put("scanned", scanned);
            data.put("allowed", allowedRows(lines));
            return data;
        }

        // 7. 匹配 → 换料(同工单+工位+物料旧 ACTIVE 置 REPLACED) + 写台账
        boolean isAlternative = matchedAlt != null;
        Date now = new Date();
        List<MesMaterialLoad> actives = loadMapper.selectList(Wrappers.<MesMaterialLoad>lambdaQuery()
            .eq(MesMaterialLoad::getMesWoId, card.getMesWoId())
            .eq(MesMaterialLoad::getStationCode, station)
            .eq(MesMaterialLoad::getMaterialId, materialId)
            .eq(MesMaterialLoad::getStatus, STATUS_ACTIVE));
        for (MesMaterialLoad old : actives) {
            loadMapper.update(null, Wrappers.<MesMaterialLoad>lambdaUpdate()
                .set(MesMaterialLoad::getStatus, STATUS_REPLACED)
                .set(MesMaterialLoad::getRemark, "换料 → " + batch.get("batchCode"))
                .eq(MesMaterialLoad::getLoadId, old.getLoadId()));
        }
        MesMaterialLoad load = new MesMaterialLoad();
        load.setMesWoId(card.getMesWoId());
        load.setWoNo(card.getWoNo());
        load.setStationCode(station);
        load.setProcessCode(processCode);
        load.setWorkstationCode(matchedLine.getWorkstationCode());
        load.setMaterialId(materialId);
        load.setMaterialCode(matchedLine.getChildMaterialCode());
        load.setMaterialName(matchedLine.getChildMaterialName());
        load.setBatchId(toLong(batch.get("batchId")));
        load.setBatchCode(batch.get("batchCode") == null ? barcode : String.valueOf(batch.get("batchCode")));
        load.setFeederSlot(dto.getFeederSlot());
        load.setIsAlternative(isAlternative ? "Y" : "N");
        load.setOperatorCode(dto.getOperatorCode());
        load.setOperatorName(dto.getOperatorName());
        load.setLoadTime(now);
        load.setStatus(STATUS_ACTIVE);
        loadMapper.insert(load);
        log.info("上料登记: station={}, wo={}, material={}, batch={}, alternative={}",
            station, card.getWoNo(), matchedLine.getChildMaterialCode(), load.getBatchCode(), isAlternative);

        Map<String, Object> matched = new LinkedHashMap<>(8);
        matched.put("materialCode", matchedLine.getChildMaterialCode());
        matched.put("materialName", matchedLine.getChildMaterialName());
        matched.put("quantityPerParent", matchedLine.getQuantityPerParent());
        matched.put("totalQuantity", matchedLine.getTotalQuantity());
        matched.put("processCode", matchedLine.getProcessCode());
        matched.put("workstationCode", matchedLine.getWorkstationCode());
        matched.put("isAlternative", isAlternative);
        if (isAlternative) {
            matched.put("substitutionType", matchedAlt.get("substitutionType"));
        }
        Map<String, Object> batchInfo = new LinkedHashMap<>(6);
        batchInfo.put("batchCode", load.getBatchCode());
        batchInfo.put("batchId", load.getBatchId());
        batchInfo.put("expiryDate", batch.get("expiryDate"));
        batchInfo.put("vendorName", batch.get("vendorName"));
        data.put("pass", true);
        data.put("matched", matched);
        data.put("batch", batchInfo);
        return data;
    }

    /**
     * 当前上料状态(C20): 应上物料清单(快照行, 逐项合并台账 ACTIVE 批次) + 当前台账。
     *
     * @param stationCode 工位编码
     * @return {stationCode, processCode, woNo?, message?, required: [{materialCode, materialName,
     *         quantityPerParent, processCode, loaded, batchCode?, loadTime?, operatorName?, isAlternative?}], loads}
     */
    public Map<String, Object> current(String stationCode) {
        String station = StationPassService.normalizeStationCode(stationCode);
        Map<String, Object> data = new LinkedHashMap<>(6);
        data.put("stationCode", station);
        data.put("required", List.of());
        data.put("loads", List.of());
        if (StringUtils.isBlank(station)) {
            throw new ServiceException("stationCode 不能为空");
        }
        String processCode = stationPassService.resolveProcessCode(station);
        data.put("processCode", processCode);
        if (processCode == null) {
            data.put("message", "无法解析工位工序编码: " + station);
            return data;
        }
        ProFlowCardStep step = locateInProgressStep(processCode);
        if (step == null) {
            data.put("message", "工位工序(" + processCode + ")当前无唯一在制工序, 暂无应上物料清单");
            return data;
        }
        ProFlowCard card = flowCardMapper.selectById(step.getCardId());
        if (card == null) {
            data.put("message", "在制工序所属流转卡不存在");
            return data;
        }
        data.put("woNo", card.getWoNo());
        List<ProWorkOrderBomLine> lines = bomLineMapper.selectList(Wrappers.<ProWorkOrderBomLine>lambdaQuery()
            .eq(ProWorkOrderBomLine::getMesWoId, card.getMesWoId())
            .and(w -> w.eq(ProWorkOrderBomLine::getProcessCode, processCode)
                .or().isNull(ProWorkOrderBomLine::getProcessCode))
            .orderByAsc(ProWorkOrderBomLine::getSortOrder));
        // 台账 ACTIVE(该工位该工单), 物料 → 最新一条
        List<MesMaterialLoad> loads = loadMapper.selectList(Wrappers.<MesMaterialLoad>lambdaQuery()
            .eq(MesMaterialLoad::getMesWoId, card.getMesWoId())
            .eq(MesMaterialLoad::getStationCode, station)
            .eq(MesMaterialLoad::getStatus, STATUS_ACTIVE)
            .orderByDesc(MesMaterialLoad::getLoadTime));
        Map<Long, MesMaterialLoad> loadByMaterial = new HashMap<>();
        for (MesMaterialLoad load : loads) {
            loadByMaterial.putIfAbsent(load.getMaterialId(), load);
        }
        List<Map<String, Object>> required = new ArrayList<>(lines.size());
        for (ProWorkOrderBomLine line : lines) {
            Map<String, Object> row = new LinkedHashMap<>(10);
            row.put("materialCode", line.getChildMaterialCode());
            row.put("materialName", line.getChildMaterialName());
            row.put("quantityPerParent", line.getQuantityPerParent());
            row.put("totalQuantity", line.getTotalQuantity());
            row.put("processCode", line.getProcessCode());
            MesMaterialLoad load = loadByMaterial.get(line.getChildMaterialId());
            row.put("loaded", load != null);
            if (load != null) {
                row.put("batchCode", load.getBatchCode());
                row.put("loadTime", load.getLoadTime());
                row.put("operatorName", load.getOperatorName());
                row.put("isAlternative", "Y".equals(load.getIsAlternative()));
                row.put("feederSlot", load.getFeederSlot());
            }
            required.add(row);
        }
        data.put("required", required);
        data.put("loads", loadRows(loads));
        return data;
    }

    // ==================== 辅助 ====================

    /** 定位工序唯一在制工序(与 StationPassService.reportBatch 同语义) */
    private ProFlowCardStep locateInProgressStep(String processCode) {
        List<ProFlowCardStep> steps = stepMapper.selectList(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getProcessCode, processCode)
            .eq(ProFlowCardStep::getStepStatus, "IN_PROGRESS"));
        return steps.size() == 1 ? steps.get(0) : null;
    }

    /** 允许上料清单(去重主料行) */
    private List<Map<String, Object>> allowedRows(List<ProWorkOrderBomLine> lines) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ProWorkOrderBomLine line : lines) {
            if (line.getChildMaterialId() == null || !seen.add(line.getChildMaterialId())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(6);
            row.put("materialCode", line.getChildMaterialCode());
            row.put("materialName", line.getChildMaterialName());
            row.put("quantityPerParent", line.getQuantityPerParent());
            row.put("processCode", line.getProcessCode());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> loadRows(List<MesMaterialLoad> loads) {
        List<Map<String, Object>> rows = new ArrayList<>(loads.size());
        for (MesMaterialLoad load : loads) {
            Map<String, Object> row = new LinkedHashMap<>(10);
            row.put("materialCode", load.getMaterialCode());
            row.put("materialName", load.getMaterialName());
            row.put("batchCode", load.getBatchCode());
            row.put("feederSlot", load.getFeederSlot());
            row.put("isAlternative", "Y".equals(load.getIsAlternative()));
            row.put("operatorName", load.getOperatorName());
            row.put("loadTime", load.getLoadTime());
            rows.add(row);
        }
        return rows;
    }

    /** ry-erp 库查询: 编程式切数据源(类级 @DS("mes") 下访问 ERP 表, 对齐 resolveOperator 模式) */
    private <T> T queryErp(Supplier<T> supplier) {
        DynamicDataSourceContextHolder.push("erp");
        try {
            return supplier.get();
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }
}
