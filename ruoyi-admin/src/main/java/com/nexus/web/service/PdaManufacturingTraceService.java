package com.nexus.web.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.flash.domain.TestRecord;
import com.nexus.mes.flash.mapper.TestRecordMapper;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.service.IProSnService;
import com.nexus.mes.pro.service.IProTraceabilityService;
import com.nexus.mes.qc.domain.QcIpqc;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.station.domain.MesStationReport;
import com.nexus.station.mapper.StationReportMapper;
import com.nexus.web.domain.vo.PdaManufacturingTraceVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-only manufacturing SN aggregation in the application assembly module.
 * Existing MES services and tenant-aware mappers stay on the MES datasource;
 * ERP gains no dependency on MES or Station. No material/time-based quality inference.
 */
@RequiredArgsConstructor
@DS("mes")
@Service
public class PdaManufacturingTraceService {
    private final IProSnService snService;
    private final IProTraceabilityService traceabilityService;
    private final ProFlowCardStepMapper stepMapper;
    private final StationReportMapper stationReportMapper;
    private final TestRecordMapper testRecordMapper;
    private final QcIpqcMapper ipqcMapper;

    /** Null means no manufacturing SN in the current tenant; absent evidence stays empty. */
    public PdaManufacturingTraceVo trace(String snCode) {
        StpUtil.checkPermission("mes:pro:trace:query");
        if (TenantHelper.isEnable() && StringUtils.isBlank(TenantHelper.getTenantId())) {
            throw new ServiceException("无法获取有效租户，不能查询追溯信息");
        }
        if (StringUtils.isBlank(snCode)) {
            return null;
        }
        var sn = snService.selectByCode(snCode.trim());
        if (sn == null) {
            return null;
        }

        var result = new PdaManufacturingTraceVo();
        result.setSn(sn);
        if (sn.getMesWoId() != null) {
            result.setWorkOrder(traceabilityService.backward(sn.getMesWoId(), sn.getSnCode()));
        }
        // These are shared card process records, not proof that this individual SN passed.
        if (sn.getCardId() != null) {
            result.setSteps(orEmpty(stepMapper.selectVoList(Wrappers.<ProFlowCardStep>lambdaQuery()
                .eq(ProFlowCardStep::getCardId, sn.getCardId())
                .orderByAsc(ProFlowCardStep::getSequenceNum))));
        }
        result.setStationReports(orEmpty(stationReportMapper.selectList(Wrappers.<MesStationReport>lambdaQuery()
            .eq(MesStationReport::getSnCode, sn.getSnCode())
            .orderByAsc(MesStationReport::getCreateTime, MesStationReport::getReportId)))
            .stream().map(this::stationReport).toList());

        result.setTestsAuthorized(StpUtil.hasPermission("mes:flash:testrecord:query"));
        if (result.isTestsAuthorized()) {
            result.setTests(orEmpty(testRecordMapper.selectVoList(Wrappers.<TestRecord>lambdaQuery()
                .eq(TestRecord::getSnCode, sn.getSnCode())
                .orderByAsc(TestRecord::getTestTime, TestRecord::getTestRecordId))));
        }
        result.setIpqcAuthorized(StpUtil.hasPermission("mes:qc:ipqc:query"));
        if (sn.getCardId() != null || sn.getMesWoId() != null) {
            result.setInspectionScope(sn.getMesWoId() == null ? "FLOW_CARD"
                : sn.getCardId() == null ? "WORK_ORDER" : "FLOW_CARD_OR_WORK_ORDER");
            if (result.isIpqcAuthorized()) {
                var query = Wrappers.<QcIpqc>lambdaQuery();
                if (sn.getMesWoId() != null) {
                    query.eq(QcIpqc::getMesWoId, sn.getMesWoId());
                    if (sn.getCardId() != null) {
                        // IPQC may cover the whole WO (no card). Another card is not this SN's evidence.
                        query.and(card -> card.eq(QcIpqc::getCardId, sn.getCardId()).or().isNull(QcIpqc::getCardId));
                    }
                } else {
                    query.eq(QcIpqc::getCardId, sn.getCardId());
                }
                result.setInspections(orEmpty(ipqcMapper.selectVoList(
                    query.orderByAsc(QcIpqc::getInspectTime, QcIpqc::getIpqcId))));
            }
        }
        return result;
    }

    private PdaManufacturingTraceVo.StationReportVo stationReport(MesStationReport record) {
        var row = new PdaManufacturingTraceVo.StationReportVo();
        row.setReportId(record.getReportId());
        row.setStationCode(record.getStationCode());
        row.setStepRecordId(record.getStepRecordId());
        row.setResult(record.getResult());
        row.setDefectCodes(record.getDefectCodes());
        row.setGoodQty(record.getGoodQty());
        row.setDefectQty(record.getDefectQty());
        row.setOperatorName(record.getOperatorName());
        row.setReportTime(record.getCreateTime());
        return row;
    }

    private static <T> List<T> orEmpty(List<T> rows) {
        return rows == null ? List.of() : rows;
    }
}
