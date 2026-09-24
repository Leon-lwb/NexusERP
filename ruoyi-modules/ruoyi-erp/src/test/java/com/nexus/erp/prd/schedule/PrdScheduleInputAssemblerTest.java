package com.nexus.erp.prd.schedule;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdRoutingStep;
import com.nexus.erp.bd.domain.BdWorkstation;
import com.nexus.erp.bd.domain.vo.BdRoutingVo;
import com.nexus.erp.bd.mapper.BdRoutingStepMapper;
import com.nexus.erp.bd.mapper.BdWorkstationMapper;
import com.nexus.erp.bd.service.IBdRoutingService;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleInput;
import com.nexus.erp.prd.schedule.ScheduleModel.StepInfo;
import com.nexus.erp.prd.schedule.ScheduleModel.Strategy;
import com.nexus.erp.sch.domain.SchEmployeeSchedule;
import com.nexus.erp.sch.domain.SchHoliday;
import com.nexus.erp.sch.domain.SchLeaveApply;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.domain.SchTeam;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchHolidayMapper;
import com.nexus.erp.sch.mapper.SchLeaveApplyMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import com.nexus.erp.sch.mapper.SchTeamMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * APS 输入装配单元测试：正式排产与 What-if 必须共享同一口径，scoped 查询必须 fail-closed。
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PrdScheduleInputAssemblerTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 24);

    @Mock
    private PrdWorkOrderMapper workOrderMapper;
    @Mock
    private PrdCapacityCalendarMapper capacityCalendarMapper;
    @Mock
    private SchHolidayMapper holidayMapper;
    @Mock
    private SchEmployeeScheduleMapper employeeScheduleMapper;
    @Mock
    private SchShiftMapper shiftMapper;
    @Mock
    private SchTeamMapper teamMapper;
    @Mock
    private SchLeaveApplyMapper leaveApplyMapper;
    @Mock
    private BdWorkstationMapper workstationMapper;
    @Mock
    private IBdRoutingService routingService;
    @Mock
    private BdRoutingStepMapper routingStepMapper;

    @InjectMocks
    private PrdScheduleInputAssembler assembler;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, PrdWorkOrder.class);
        TableInfoHelper.initTableInfo(assistant, BdRoutingStep.class);
        TableInfoHelper.initTableInfo(assistant, PrdCapacityCalendar.class);
        TableInfoHelper.initTableInfo(assistant, SchHoliday.class);
        TableInfoHelper.initTableInfo(assistant, SchEmployeeSchedule.class);
        TableInfoHelper.initTableInfo(assistant, SchShift.class);
        TableInfoHelper.initTableInfo(assistant, SchTeam.class);
        TableInfoHelper.initTableInfo(assistant, SchLeaveApply.class);
        TableInfoHelper.initTableInfo(assistant, BdWorkstation.class);
    }

    @Test
    void assembleBuildsCrInputWithOneWorkOrderAndOneRoutingStep() {
        PrdSchedulePlanBo bo = scheduleBo(14, "CR");
        bo.setWorkshopId(10L);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder(1L, 100L, "CREATED")));
        stubRouting(100L);
        stubEmptyCapacityInputs();

        ScheduleInput input = assembler.assemble(bo);

        assertEquals(START_DATE, input.getStartDate());
        assertEquals(14, input.getHorizonDays());
        assertEquals(Strategy.CR, input.getStrategy());
        assertEquals(1, input.getWorkOrders().size());
        assertEquals(1L, input.getWorkOrders().get(0).getWoId());
        assertEquals(100L, input.getWorkOrders().get(0).getMaterialId());
        assertEquals(0, BigDecimal.TEN.compareTo(input.getWorkOrders().get(0).getRemainingQty()));
        assertTrue(input.getCapacityByDate().isEmpty());
        assertTrue(input.getHolidays().isEmpty());

        List<StepInfo> steps = input.getRoutingProvider().apply(100L);
        assertEquals(1, steps.size());
        StepInfo step = steps.get(0);
        assertEquals(10, step.getSeqNum());
        assertEquals("P-1", step.getProcessCode());
        assertEquals("加工", step.getProcessName());
        assertEquals(400L, step.getWorkstationId());
        assertEquals("CNC-01", step.getWorkstationName());
        assertEquals(0, BigDecimal.ONE.compareTo(step.getStandardHours()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PrdWorkOrder>> queryCaptor =
            ArgumentCaptor.forClass(Wrapper.class);
        verify(workOrderMapper).selectList(queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("wo_status"), sql);
        assertTrue(sql.contains("workshop_id"), sql);
        assertTrue(queryValues(queryCaptor.getValue()).contains(10L));
        verify(routingService).getCurrentRouting(100L);
    }

    @Test
    void assembleDefaultsHorizonToFourteen() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder(1L, 100L, "CREATED")));
        stubRouting(100L);
        stubEmptyCapacityInputs();

        ScheduleInput input = assembler.assemble(scheduleBo(null, "EDD"));

        assertEquals(14, input.getHorizonDays());
    }

    @Test
    void assembleClampsHorizonToAtLeastOneDay() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder(1L, 100L, "CREATED")));
        stubRouting(100L);
        stubEmptyCapacityInputs();

        ScheduleInput input = assembler.assemble(scheduleBo(0, "PRIORITY"));

        assertEquals(1, input.getHorizonDays());
        assertEquals(Strategy.PRIORITY, input.getStrategy());
    }

    @Test
    void scopedAssemblyUsesOnlyDeduplicatedRequestedIdsInRequestedOrder() {
        PrdWorkOrder firstFromMapper = workOrder(1L, 100L, "CREATED");
        PrdWorkOrder secondFromMapper = workOrder(2L, 100L, "APPROVED");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(firstFromMapper, secondFromMapper));
        stubRouting(100L);
        stubEmptyCapacityInputs();

        ScheduleInput input = assembler.assemble(scheduleBo(14, "CR"), List.of(2L, 1L, 2L));

        assertEquals(List.of(2L, 1L), input.getWorkOrders().stream()
            .map(ScheduleModel.WoInfo::getWoId)
            .toList());
        verify(workOrderMapper, times(1)).selectList(any());
        verify(routingService, times(1)).getCurrentRouting(100L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<PrdWorkOrder>> queryCaptor =
            ArgumentCaptor.forClass(Wrapper.class);
        verify(workOrderMapper).selectList(queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("wo_id"), sql);
        Collection<?> queryValues = queryValues(queryCaptor.getValue());
        assertTrue(queryValues.contains(1L), queryValues.toString());
        assertTrue(queryValues.contains(2L), queryValues.toString());
    }

    @Test
    void scopedAssemblyRejectsNullOrEmptyIdsWithoutFallingBackToAllWorkOrders() {
        ServiceException nullIds = assertThrows(ServiceException.class,
            () -> assembler.assemble(scheduleBo(14, "EDD"), null));
        ServiceException emptyIds = assertThrows(ServiceException.class,
            () -> assembler.assemble(scheduleBo(14, "EDD"), List.of()));

        assertTrue(nullIds.getMessage().contains("工单ID"), nullIds.getMessage());
        assertTrue(emptyIds.getMessage().contains("工单ID"), emptyIds.getMessage());
        verifyNoInteractions(workOrderMapper);
    }

    @Test
    void scopedAssemblyRejectsMissingIdsWithoutFallingBackToAllWorkOrders() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder(1L, 100L, "CREATED")));

        ServiceException error = assertThrows(ServiceException.class,
            () -> assembler.assemble(scheduleBo(14, "EDD"), List.of(1L, 2L)));

        assertTrue(error.getMessage().contains("2"), error.getMessage());
        verify(workOrderMapper, times(1)).selectList(any());
        verifyNoInteractions(routingService);
    }

    @Test
    void scopedAssemblyRejectsUnschedulableIdsWithoutFallingBackToAllWorkOrders() {
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder(1L, 100L, "COMPLETED")));

        ServiceException error = assertThrows(ServiceException.class,
            () -> assembler.assemble(scheduleBo(14, "EDD"), List.of(1L)));

        assertTrue(error.getMessage().contains("不可排"), error.getMessage());
        assertTrue(error.getMessage().contains("1"), error.getMessage());
        verify(workOrderMapper, times(1)).selectList(any());
        verifyNoInteractions(routingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BASELINE_CURRENT", "CURRENT", " baseline_current "})
    void assembleRejectsCurrentBaselineStrategies(String strategy) {
        PrdSchedulePlanBo bo = scheduleBo(14, strategy);

        ServiceException error = assertThrows(ServiceException.class, () -> assembler.assemble(bo));

        assertTrue(error.getMessage().contains("基线"), error.getMessage());
        verify(workOrderMapper, never()).selectList(any());
    }

    private void stubRouting(Long materialId) {
        BdRoutingVo routing = new BdRoutingVo();
        routing.setRoutingId(200L);
        routing.setRoutingCode("R-1");
        when(routingService.getCurrentRouting(materialId)).thenReturn(routing);

        BdRoutingStep step = new BdRoutingStep();
        step.setStepId(201L);
        step.setRoutingId(200L);
        step.setSequenceNum(10);
        step.setProcessId(300L);
        step.setProcessCode("P-1");
        step.setProcessName("加工");
        step.setWorkstationId(400L);
        step.setWorkstationName("CNC-01");
        step.setStandardHours(BigDecimal.ONE);
        step.setSetupHours(BigDecimal.ZERO);
        step.setIsOutsource(0);
        when(routingStepMapper.selectList(any())).thenReturn(List.of(step));
    }

    private void stubEmptyCapacityInputs() {
        when(capacityCalendarMapper.selectList(any())).thenReturn(List.of());
        when(holidayMapper.selectList(any())).thenReturn(List.of());
        when(employeeScheduleMapper.selectList(any())).thenReturn(List.of());
    }

    private PrdSchedulePlanBo scheduleBo(Integer horizonDays, String strategy) {
        PrdSchedulePlanBo bo = new PrdSchedulePlanBo();
        bo.setStartDate(toDate(START_DATE));
        bo.setHorizonDays(horizonDays);
        bo.setStrategy(strategy);
        return bo;
    }

    private PrdWorkOrder workOrder(Long woId, Long materialId, String status) {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(woId);
        wo.setWoNo("WO-" + woId);
        wo.setMaterialId(materialId);
        wo.setMaterialCode("MAT-" + materialId);
        wo.setMaterialName("物料-" + materialId);
        wo.setPlannedQty(BigDecimal.TEN);
        wo.setCompletedQty(BigDecimal.ZERO);
        wo.setPriority(1L);
        wo.setWoStatus(status);
        wo.setWorkshopId(10L);
        wo.setWorkshopName("一车间");
        return wo;
    }

    private Collection<?> queryValues(Wrapper<?> wrapper) {
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().values();
    }

    private Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
