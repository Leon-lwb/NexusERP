package com.nexus.erp.sch;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.domain.bo.SchShiftBo;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchPlanMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import com.nexus.erp.sch.mapper.SchTeamScheduleMapper;
import com.nexus.erp.sch.service.impl.SchShiftServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("dev")
@ExtendWith(MockitoExtension.class)
class SchShiftCapacityProtectionTest {
    @Mock SchShiftMapper shiftMapper;
    @Mock SchPlanMapper planMapper;
    @Mock SchTeamScheduleMapper teamScheduleMapper;
    @Mock SchEmployeeScheduleMapper employeeScheduleMapper;
    @Mock PrdCapacityCalendarMapper calendarMapper;
    @InjectMocks SchShiftServiceImpl subject;

    @BeforeAll static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SchShift.class);
        TableInfoHelper.initTableInfo(assistant, PrdCapacityCalendar.class);
    }

    @Test void shiftBoundToAnyCalendarCannotChangeHistoricalTiming() {
        when(shiftMapper.selectOne(any(Wrapper.class))).thenReturn(shift());
        when(calendarMapper.selectList(any(Wrapper.class))).thenReturn(List.of(calendar()));
        SchShiftBo request = new SchShiftBo();
        request.setShiftId(20L);
        request.setShiftName("late shift");
        request.setStartTime(java.sql.Time.valueOf("09:00:00"));
        request.setEndTime(java.sql.Time.valueOf("17:00:00"));
        request.setIsCrossDay(0);

        assertThrows(ServiceException.class, () -> subject.updateByBo(request));

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Wrapper<PrdCapacityCalendar>> lockedCalendar =
            (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
        verify(calendarMapper).selectList(lockedCalendar.capture());
        assertTrue(lockedCalendar.getValue().getSqlSegment().toUpperCase().contains("FOR UPDATE"));
        verify(shiftMapper, never()).updateById(any(SchShift.class));
    }

    @Test void shiftBoundToAnyCalendarCannotBeDeleted() {
        when(shiftMapper.selectOne(any(Wrapper.class))).thenReturn(shift());
        when(calendarMapper.selectList(any(Wrapper.class))).thenReturn(List.of(calendar()));

        assertThrows(ServiceException.class, () -> subject.deleteByIds(new Long[]{20L}));

        verify(shiftMapper, never()).deleteBatchIds(any());
    }

    private SchShift shift() {
        SchShift shift = new SchShift();
        shift.setShiftId(20L);
        shift.setTenantId("000000");
        shift.setStartTime(java.sql.Time.valueOf("08:00:00"));
        shift.setEndTime(java.sql.Time.valueOf("16:00:00"));
        shift.setIsCrossDay(0);
        return shift;
    }

    private PrdCapacityCalendar calendar() {
        PrdCapacityCalendar calendar = new PrdCapacityCalendar();
        calendar.setCalendarId(70L);
        calendar.setShiftId(20L);
        calendar.setCalendarDate(java.sql.Date.valueOf(LocalDate.of(2026, 9, 29)));
        calendar.setTenantId("000000");
        calendar.setEffectiveHours(new BigDecimal("8.00"));
        calendar.setPlannedHours(BigDecimal.ZERO);
        return calendar;
    }
}
