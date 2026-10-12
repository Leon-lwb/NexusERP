package com.nexus.web.service;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.handle.PlusTenantLineHandler;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.tenant.properties.TenantProperties;
import com.nexus.mes.flash.domain.TestRecord;
import com.nexus.mes.flash.domain.vo.TestRecordVo;
import com.nexus.mes.flash.mapper.TestRecordMapper;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.vo.ProFlowCardStepVo;
import com.nexus.mes.pro.domain.vo.ProSnVo;
import com.nexus.mes.pro.domain.vo.TraceabilityResult;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.service.IProSnService;
import com.nexus.mes.pro.service.IProTraceabilityService;
import com.nexus.mes.qc.domain.QcIpqc;
import com.nexus.mes.qc.domain.vo.QcIpqcVo;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.station.domain.MesStationReport;
import com.nexus.station.mapper.StationReportMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdaManufacturingTraceServiceTest {
    private static final String TRACE = "mes:pro:trace:query";
    @Mock IProSnService sns;
    @Mock IProTraceabilityService components;
    @Mock ProFlowCardStepMapper steps;
    @Mock StationReportMapper passes;
    @Mock TestRecordMapper tests;
    @Mock QcIpqcMapper inspections;
    @InjectMocks PdaManufacturingTraceService service;
    private MockedStatic<StpUtil> permissions;
    private MockedStatic<TenantHelper> tenant;
    private final Set<String> granted = new HashSet<>();

    @BeforeAll
    static void tables() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "pda-manufacturing");
        for (Class<?> type : List.of(ProFlowCardStep.class, MesStationReport.class, TestRecord.class, QcIpqc.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    @BeforeEach
    void context() {
        granted.add(TRACE);
        permissions = mockStatic(StpUtil.class);
        permissions.when(() -> StpUtil.hasPermission(anyString()))
            .thenAnswer(call -> granted.contains(call.getArgument(0)));
        permissions.when(() -> StpUtil.checkPermission(TRACE)).thenAnswer(call -> {
            if (!granted.contains(TRACE)) throw new NotPermissionException(TRACE);
            return null;
        });
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        tenant.when(TenantHelper::getTenantId).thenReturn("000007");
    }

    @AfterEach
    void clear() { permissions.close(); tenant.close(); }

    private ProSnVo sn(Long cardId, Long woId) {
        var sn = new ProSnVo();
        sn.setSnId(9007199254740993L);
        sn.setSnCode("SN-MFG-001");
        sn.setMesWoId(woId);
        sn.setCardId(cardId);
        sn.setWoNo("MES-WO-001");
        sn.setSnStatus("SHIPPED");
        return sn;
    }

    @Test
    void manufacturingHitReadsActualWorkOrderProcessesPassesAndQuality() {
        granted.addAll(List.of("mes:qc:ipqc:query", "mes:flash:testrecord:query"));
        var sn = sn(3L, 7L);
        var workOrder = new TraceabilityResult();
        workOrder.setMesWoId(7L);
        workOrder.setWoNo("MES-WO-001");
        var step = new ProFlowCardStepVo();
        step.setStepRecordId(10L);
        step.setProcessCode("SMT");
        var pass = new MesStationReport();
        pass.setReportId(20L);
        pass.setStationCode("ST-001");
        pass.setResult("FAIL");
        pass.setCreateTime(new Date(1000));
        var test = new TestRecordVo();
        test.setTestRecordId(30L);
        test.setResult("FAIL");
        test.setNumericValue(BigDecimal.ZERO);
        var inspection = new QcIpqcVo();
        inspection.setIpqcId(40L);
        inspection.setInspectResult("UNQUALIFIED");
        when(sns.selectByCode("SN-MFG-001")).thenReturn(sn);
        when(components.backward(7L, "SN-MFG-001")).thenReturn(workOrder);
        when(steps.selectVoList(any())).thenReturn(List.of(step));
        when(passes.selectList(any())).thenReturn(List.of(pass));
        when(tests.selectVoList(any())).thenReturn(List.of(test));
        when(inspections.selectVoList(any())).thenReturn(List.of(inspection));

        var result = service.trace("SN-MFG-001");

        assertNotNull(result, "manufacturing SN must resolve without any warehouse SN");
        assertEquals("MANUFACTURING_SN", result.getType());
        assertEquals("MES_PRO_SN", result.getSource());
        assertSame(sn, result.getSn());
        assertSame(workOrder, result.getWorkOrder());
        assertEquals("SMT", result.getSteps().get(0).getProcessCode());
        assertEquals("FAIL", result.getStationReports().get(0).getResult());
        assertEquals(new Date(1000), result.getStationReports().get(0).getReportTime());
        assertEquals(BigDecimal.ZERO, result.getTests().get(0).getNumericValue());
        assertEquals("UNQUALIFIED", result.getInspections().get(0).getInspectResult());
        assertEquals("FLOW_CARD_OR_WORK_ORDER", result.getInspectionScope());
        assertTrue(result.isTestsAuthorized());
        assertTrue(result.isIpqcAuthorized());
        verify(components).backward(7L, "SN-MFG-001");
    }

    @Test
    void missingSnHasNoDownstreamReadsOrInventedRecords() {
        assertNull(service.trace("MISSING"));
        verify(sns).selectByCode("MISSING");
        verifyNoInteractions(components, steps, passes, tests, inspections);
    }

    @Test
    void missingAssociationsRemainNullOrEmpty() {
        granted.addAll(List.of("mes:qc:ipqc:query", "mes:flash:testrecord:query"));
        when(sns.selectByCode("SN-MFG-001")).thenReturn(sn(null, null));
        var result = service.trace("SN-MFG-001");
        assertNotNull(result);
        assertNull(result.getWorkOrder());
        assertTrue(result.getSteps().isEmpty());
        assertTrue(result.getStationReports().isEmpty());
        assertTrue(result.getTests().isEmpty());
        assertTrue(result.getInspections().isEmpty());
        assertEquals("UNKNOWN", result.getInspectionScope());
        verifyNoInteractions(components, steps, inspections);
    }

    @Test
    void noMesTracePermissionFailsBeforeSnLookup() {
        granted.clear();
        assertThrows(NotPermissionException.class, () -> service.trace("SN-MFG-001"));
        verifyNoInteractions(sns, components, steps, passes, tests, inspections);
    }

    @Test
    void missingTenantFailsBeforeSnLookup() {
        tenant.when(TenantHelper::getTenantId).thenReturn(" ");
        assertThrows(ServiceException.class, () -> service.trace("SN-MFG-001"));
        verifyNoInteractions(sns, components, steps, passes, tests, inspections);
    }

    @Test
    void qualityPermissionsAreCheckedIndividuallyAndNeverWidenedByTracePermission() {
        when(sns.selectByCode("SN-MFG-001")).thenReturn(sn(3L, 7L));
        var result = service.trace("SN-MFG-001");
        assertNotNull(result);
        assertFalse(result.isTestsAuthorized());
        assertFalse(result.isIpqcAuthorized());
        assertTrue(result.getTests().isEmpty());
        assertTrue(result.getInspections().isEmpty());
        verifyNoInteractions(tests, inspections);
    }

    @Test
    void ipqcWithoutCardIsExplicitlyWorkOrderEvidence() {
        granted.add("mes:qc:ipqc:query");
        when(sns.selectByCode("SN-MFG-001")).thenReturn(sn(null, 7L));
        var result = service.trace("SN-MFG-001");
        assertNotNull(result);
        assertEquals("WORK_ORDER", result.getInspectionScope());
        ArgumentCaptor<Wrapper<QcIpqc>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(inspections).selectVoList(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("mes_wo_id"));
        assertFalse(query.getValue().getSqlSegment().contains("material_id"));
        verifyNoInteractions(steps);
    }

    @Test
    void unknownStationAndTestResultsStayUnknown() {
        granted.add("mes:flash:testrecord:query");
        when(sns.selectByCode("SN-MFG-001")).thenReturn(sn(null, null));
        var pass = new MesStationReport();
        pass.setReportId(20L);
        var test = new TestRecordVo();
        test.setTestRecordId(30L);
        when(passes.selectList(any())).thenReturn(List.of(pass));
        when(tests.selectVoList(any())).thenReturn(List.of(test));
        var result = service.trace("SN-MFG-001");
        assertNotNull(result);
        assertNull(result.getStationReports().get(0).getResult());
        assertNull(result.getTests().get(0).getResult());
    }

    @Test
    void readsUseMesDatasourceAndActualTenantInterceptorOnExactSnAndCardQueries() {
        assertNotNull(PdaManufacturingTraceService.class.getAnnotation(DS.class));
        assertEquals("mes", PdaManufacturingTraceService.class.getAnnotation(DS.class).value());
        granted.addAll(List.of("mes:qc:ipqc:query", "mes:flash:testrecord:query"));
        when(sns.selectByCode("SN-MFG-001")).thenReturn(sn(3L, 7L));
        assertNotNull(service.trace("SN-MFG-001"));
        ArgumentCaptor<Wrapper<ProFlowCardStep>> stepQuery = ArgumentCaptor.forClass(Wrapper.class);
        ArgumentCaptor<Wrapper<MesStationReport>> passQuery = ArgumentCaptor.forClass(Wrapper.class);
        ArgumentCaptor<Wrapper<TestRecord>> testQuery = ArgumentCaptor.forClass(Wrapper.class);
        ArgumentCaptor<Wrapper<QcIpqc>> inspectionQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(steps).selectVoList(stepQuery.capture());
        verify(passes).selectList(passQuery.capture());
        verify(tests).selectVoList(testQuery.capture());
        verify(inspections).selectVoList(inspectionQuery.capture());
        assertTrue(stepQuery.getValue().getSqlSegment().contains("card_id"));
        assertTrue(passQuery.getValue().getSqlSegment().contains("sn_code"));
        assertTrue(testQuery.getValue().getSqlSegment().contains("sn_code"));
        assertTrue(inspectionQuery.getValue().getSqlSegment().contains("card_id"));
        assertTrue(inspectionQuery.getValue().getSqlSegment().contains("mes_wo_id"));
        assertTenantSql(ProFlowCardStepMapper.class, stepQuery.getValue(), true);
        assertTenantSql(StationReportMapper.class, passQuery.getValue(), false);
        assertTenantSql(TestRecordMapper.class, testQuery.getValue(), true);
        assertTenantSql(QcIpqcMapper.class, inspectionQuery.getValue(), true);
    }

    @Test
    void cardBoundSnIncludesUnboundWorkOrderInspectionsButExcludesAnotherCard() {
        granted.add("mes:qc:ipqc:query");
        when(sns.selectByCode("SN-MFG-001")).thenReturn(sn(3L, 7L));
        var result = service.trace("SN-MFG-001");
        assertNotNull(result);
        assertEquals("FLOW_CARD_OR_WORK_ORDER", result.getInspectionScope());
        ArgumentCaptor<Wrapper<QcIpqc>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(inspections).selectVoList(query.capture());
        var sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("mes_wo_id"));
        assertTrue(sql.contains("card_id IS NULL"), "work-order IPQC without a card must remain visible: " + sql);
        assertTrue(sql.matches("(?s).*\\(card_id = .* OR card_id IS NULL\\).*"),
            "another card must not match the work-order fallback: " + sql);
        assertFalse(sql.contains("material_id"));
        assertTenantSql(QcIpqcMapper.class, query.getValue(), true);
    }

    @Test
    void historicalCardStepsDoNotRequireRedundantWorkOrderId() {
        when(sns.selectByCode("SN-MFG-001")).thenReturn(sn(3L, 7L));
        assertNotNull(service.trace("SN-MFG-001"));
        ArgumentCaptor<Wrapper<ProFlowCardStep>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(steps).selectVoList(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("card_id"));
        assertFalse(query.getValue().getSqlSegment().contains("mes_wo_id"),
            "existing ProSn trace links steps by card; a missing redundant WO must not hide history");
    }

    private void assertTenantSql(Class<?> mapper, Wrapper<?> query, boolean logicDeleted) {
        var config = new MybatisConfiguration();
        config.addMapper(mapper);
        var statement = config.getMappedStatement(mapper.getName() + ".selectList");
        var sql = statement.getBoundSql(Map.of("ew", query)).getSql();
        var properties = new TenantProperties();
        properties.setExcludes(List.of());
        var interceptor = new TenantLineInnerInterceptor(new PlusTenantLineHandler(properties));
        var scoped = interceptor.parserSingle(sql, null);
        assertTrue(scoped.contains("tenant_id = '000007'"), scoped);
        if (logicDeleted) assertTrue(scoped.matches("(?s).*del_flag\\s*=\\s*'?0'?.*"), scoped);
        tenant.when(TenantHelper::getTenantId).thenReturn("000008");
        var other = interceptor.parserSingle(sql, null);
        assertTrue(other.contains("tenant_id = '000008'"), other);
        assertFalse(other.contains("tenant_id = '000007'"), other);
        tenant.when(TenantHelper::getTenantId).thenReturn("000007");
    }
}
