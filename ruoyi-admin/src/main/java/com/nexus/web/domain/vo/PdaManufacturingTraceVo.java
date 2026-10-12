package com.nexus.web.domain.vo;

import com.nexus.mes.flash.domain.vo.TestRecordVo;
import com.nexus.mes.pro.domain.vo.ProFlowCardStepVo;
import com.nexus.mes.pro.domain.vo.ProSnVo;
import com.nexus.mes.pro.domain.vo.TraceabilityResult;
import com.nexus.mes.qc.domain.vo.QcIpqcVo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** Read-only manufacturing evidence; card/work-order inspections are not SN-level verdicts. */
@Data
public class PdaManufacturingTraceVo {
    private String type = "MANUFACTURING_SN";
    private String source = "MES_PRO_SN";
    private ProSnVo sn;
    private TraceabilityResult workOrder;
    private List<ProFlowCardStepVo> steps = List.of();
    private List<StationReportVo> stationReports = List.of();
    private List<TestRecordVo> tests = List.of();
    private List<QcIpqcVo> inspections = List.of();
    /** Query scope; each inspection retains its actual cardId/mesWoId provenance. */
    private String inspectionScope = "UNKNOWN";
    private boolean testsAuthorized;
    private boolean ipqcAuthorized;

    /** Public station history excludes device idempotency keys and request fingerprints. */
    @Data
    public static class StationReportVo {
        private Long reportId;
        private String stationCode;
        private Long stepRecordId;
        private String result;
        private String defectCodes;
        private BigDecimal goodQty;
        private BigDecimal defectQty;
        private String operatorName;
        private Date reportTime;
    }
}
