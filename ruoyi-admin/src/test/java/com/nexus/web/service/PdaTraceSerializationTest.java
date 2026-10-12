package com.nexus.web.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nexus.common.web.config.JacksonConfig;
import com.nexus.mes.flash.domain.vo.TestRecordVo;
import com.nexus.mes.pro.domain.vo.ProFlowCardStepVo;
import com.nexus.mes.pro.domain.vo.ProSnVo;
import com.nexus.mes.pro.domain.vo.TraceabilityResult;
import com.nexus.mes.qc.domain.vo.QcIpqcVo;
import com.nexus.web.domain.vo.PdaManufacturingTraceVo;
import com.nexus.web.domain.vo.PdaTraceVo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PdaTraceSerializationTest {
    @Test
    void existingGlobalSerializerKeepsWarehouseManufacturingAndNestedSnowflakeIdsAsStrings() throws Exception {
        long id = 9007199254740993L;
        var sn = new ProSnVo();
        sn.setSnId(id);
        sn.setMesWoId(id);
        sn.setCardId(id);
        var workOrder = new TraceabilityResult();
        workOrder.setMesWoId(id);
        var step = new ProFlowCardStepVo();
        step.setStepRecordId(id);
        var pass = new PdaManufacturingTraceVo.StationReportVo();
        pass.setReportId(id);
        var test = new TestRecordVo();
        test.setTestRecordId(id);
        var inspection = new QcIpqcVo();
        inspection.setIpqcId(id);
        var manufacturing = new PdaManufacturingTraceVo();
        manufacturing.setSn(sn);
        manufacturing.setWorkOrder(workOrder);
        manufacturing.setSteps(List.of(step));
        manufacturing.setStationReports(List.of(pass));
        manufacturing.setTests(List.of(test));
        manufacturing.setInspections(List.of(inspection));
        var result = new PdaTraceVo();
        var resolved = new PdaTraceVo.ResolvedVo();
        resolved.setManufacturingSn(sn);
        var warehouseSn = new PdaTraceVo.SnVo();
        warehouseSn.setSnId(id);
        resolved.setSn(warehouseSn);
        result.setResolved(resolved);
        result.setMes(Map.of("manufacturing", manufacturing, "woIds", List.of(id), "snIds", List.of(id)));

        var mapper = JsonMapper.builder().addModule(new JacksonConfig().bigNumberModule()).build();
        var json = mapper.readTree(mapper.writeValueAsString(result));

        for (String path : List.of("/resolved/sn/snId", "/resolved/manufacturingSn/snId",
                "/resolved/manufacturingSn/mesWoId", "/resolved/manufacturingSn/cardId",
                "/mes/manufacturing/workOrder/mesWoId", "/mes/manufacturing/steps/0/stepRecordId",
                "/mes/manufacturing/stationReports/0/reportId", "/mes/manufacturing/tests/0/testRecordId",
                "/mes/manufacturing/inspections/0/ipqcId", "/mes/woIds/0", "/mes/snIds/0")) {
            assertTrue(json.at(path).isTextual(), path);
            assertEquals(Long.toString(id), json.at(path).textValue(), path);
        }
    }
}
