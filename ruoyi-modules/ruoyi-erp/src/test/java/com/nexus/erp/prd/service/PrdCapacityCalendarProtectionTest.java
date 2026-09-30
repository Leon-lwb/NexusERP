package com.nexus.erp.prd.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.erp.testsupport.SpringStaticBootstrap;
import cn.hutool.core.bean.BeanUtil;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.PrdScheduleCapacityBooking;
import com.nexus.erp.prd.domain.bo.PrdCapacityCalendarBo;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.mapper.PrdScheduleCapacityBookingMapper;
import com.nexus.erp.sch.domain.SchPlan;
import com.nexus.erp.prd.service.impl.PrdCapacityCalendarServiceImpl;
import com.nexus.erp.sch.mapper.SchPlanMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@Tag("dev")
@ExtendWith(MockitoExtension.class)
class PrdCapacityCalendarProtectionTest {
    @Mock PrdCapacityCalendarMapper mapper;
    @Mock PrdScheduleCapacityBookingMapper bookingMapper;
    @Mock SchPlanMapper planMapper;
    @Mock SchShiftMapper shiftMapper;
    @InjectMocks PrdCapacityCalendarServiceImpl subject;

    @BeforeAll static void metadata() {
        SpringStaticBootstrap.ensureActive();
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PrdCapacityCalendar.class);
        TableInfoHelper.initTableInfo(assistant, PrdScheduleCapacityBooking.class);
        TableInfoHelper.initTableInfo(assistant, com.nexus.erp.sch.domain.SchShift.class);
    }

    @Test void activeCalendarInsertRequiresExistingSameTenantShiftBeforeWriting() {
        PrdCapacityCalendarBo bo = newCalendar("ACTIVE", 20L);
        try (MockedStatic<MapstructUtils> mapping = mapCalendarBo()) {
            ServiceException missing = assertThrows(ServiceException.class, () -> subject.insertByBo(bo));
            org.junit.jupiter.api.Assertions.assertTrue(missing.getMessage().contains("班次"), missing.getMessage());
            verify(mapper, never()).insert(any(PrdCapacityCalendar.class));
        }
    }

    @Test void activeCalendarInsertRefusesNullShiftAndForeignTenantShift() {
        PrdCapacityCalendarBo bo = newCalendar("ACTIVE", null);
        try (MockedStatic<MapstructUtils> mapping = mapCalendarBo()) {
            ServiceException missing = assertThrows(ServiceException.class, () -> subject.insertByBo(bo));
            org.junit.jupiter.api.Assertions.assertTrue(missing.getMessage().contains("班次"), missing.getMessage());
            verifyNoInteractions(shiftMapper);
            bo.setShiftId(20L);
            com.nexus.erp.sch.domain.SchShift foreign = validShift();
            foreign.setTenantId("other");
            when(shiftMapper.selectOne(any(Wrapper.class))).thenReturn(foreign);
            ServiceException mismatch = assertThrows(ServiceException.class, () -> subject.insertByBo(bo));
            org.junit.jupiter.api.Assertions.assertTrue(mismatch.getMessage().contains("班次"), mismatch.getMessage());
            verify(mapper, never()).insert(any(PrdCapacityCalendar.class));
        }
    }

    @Test void closedZeroCapacityCalendarMayOmitShift() {
        PrdCapacityCalendarBo bo = newCalendar("CLOSED", null);
        bo.setAvailableHours(BigDecimal.ZERO);
        when(mapper.insert(any(PrdCapacityCalendar.class))).thenReturn(1);
        try (MockedStatic<MapstructUtils> mapping = mapCalendarBo()) {
            org.junit.jupiter.api.Assertions.assertTrue(subject.insertByBo(bo));
            verifyNoInteractions(shiftMapper);
        }
    }

    @Test void activeCalendarInsertLocksShiftBeforeWritingToPreventConcurrentDeletion() {
        PrdCapacityCalendarBo bo = newCalendar("ACTIVE", 20L);
        com.nexus.erp.sch.domain.SchShift shift = validShift();
        when(shiftMapper.selectOne(any(Wrapper.class))).thenReturn(shift);
        when(mapper.insert(any(PrdCapacityCalendar.class))).thenReturn(1);
        try (MockedStatic<MapstructUtils> mapping = mapCalendarBo()) {
            org.junit.jupiter.api.Assertions.assertTrue(subject.insertByBo(bo));
            org.mockito.ArgumentCaptor<Wrapper<com.nexus.erp.sch.domain.SchShift>> lock =
                org.mockito.ArgumentCaptor.forClass(Wrapper.class);
            org.mockito.InOrder writes = inOrder(shiftMapper, mapper);
            writes.verify(shiftMapper).selectOne(lock.capture());
            org.junit.jupiter.api.Assertions.assertTrue(lock.getValue().getSqlSegment().toUpperCase().contains("FOR UPDATE"));
            writes.verify(mapper).insert(any(PrdCapacityCalendar.class));
        }
    }

    @Test void activeCalendarEditLocksShiftBeforeCalendarRow() {
        PrdCapacityCalendarBo bo = newCalendar("ACTIVE", 20L);
        bo.setCalendarId(70L);
        PrdCapacityCalendar existing = row("0.00");
        existing.setShiftId(20L);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(shiftMapper.selectOne(any(Wrapper.class))).thenReturn(validShift());
        when(mapper.updateById(any(PrdCapacityCalendar.class))).thenReturn(1);
        try (MockedStatic<MapstructUtils> mapping = mapCalendarBo()) {
            org.junit.jupiter.api.Assertions.assertTrue(subject.updateByBo(bo));
            org.mockito.ArgumentCaptor<Wrapper<PrdCapacityCalendar>> reads =
                org.mockito.ArgumentCaptor.forClass(Wrapper.class);
            org.mockito.InOrder locks = inOrder(shiftMapper, mapper);
            locks.verify(mapper).selectOne(reads.capture()); /** 乐观读取的快照，此时尚未持有行锁。 */
            org.junit.jupiter.api.Assertions.assertFalse(reads.getValue().getSqlSegment().toUpperCase().contains("FOR UPDATE"));
            locks.verify(shiftMapper).selectOne(any(Wrapper.class));
            locks.verify(mapper).selectOne(reads.capture()); /** 取得班次锁后读取的当前行。 */
            org.junit.jupiter.api.Assertions.assertTrue(reads.getValue().getSqlSegment().toUpperCase().contains("FOR UPDATE"));
            locks.verify(mapper).updateById(any(PrdCapacityCalendar.class));
        }
    }

    @Test void activeCalendarEditRejectsMissingShiftBeforeWriting() {
        PrdCapacityCalendarBo bo = newCalendar("ACTIVE", 20L);
        bo.setCalendarId(70L);
        PrdCapacityCalendar existing = row("0.00");
        existing.setShiftId(20L);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        try (MockedStatic<MapstructUtils> mapping = mapCalendarBo()) {
            ServiceException missing = assertThrows(ServiceException.class, () -> subject.updateByBo(bo));
            org.junit.jupiter.api.Assertions.assertTrue(missing.getMessage().contains("班次"), missing.getMessage());
            verify(mapper, never()).updateById(any(PrdCapacityCalendar.class));
        }
    }

    private PrdCapacityCalendarBo newCalendar(String status, Long shiftId) {
        PrdCapacityCalendarBo bo = new PrdCapacityCalendarBo();
        bo.setCalendarDate(java.sql.Date.valueOf(LocalDate.of(2026, 9, 24)));
        bo.setObjectType("WORKSTATION");
        bo.setObjectId(401L);
        bo.setShiftId(shiftId);
        bo.setAvailableHours(new BigDecimal("1.00"));
        bo.setStatus(status);
        return bo;
    }

    private com.nexus.erp.sch.domain.SchShift validShift() {
        com.nexus.erp.sch.domain.SchShift shift = new com.nexus.erp.sch.domain.SchShift();
        shift.setShiftId(20L);
        shift.setTenantId("000000");
        shift.setStartTime(java.sql.Time.valueOf("08:00:00"));
        shift.setEndTime(java.sql.Time.valueOf("16:00:00"));
        shift.setIsCrossDay(0);
        return shift;
    }

    private MockedStatic<MapstructUtils> mapCalendarBo() {
        return mockStatic(MapstructUtils.class, invocation ->
            BeanUtil.copyProperties(invocation.getArgument(0), invocation.getArgument(1)));
    }

    @Test void deleteCannotIgnoreBookedCalendarEvenWhenValidationFlagIsFalse() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(row("0.00"));
        lenient().when(bookingMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThrows(ServiceException.class,
            () -> subject.deleteWithValidByIds(List.of(70L), false));
        verify(mapper, never()).deleteByIds(any());
    }

    @Test void deleteRefusesNonZeroPlannedHoursWithoutBookingRows() {
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(row("0.10"));
        assertThrows(ServiceException.class,
            () -> subject.deleteWithValidByIds(List.of(70L), false));
        verify(mapper, never()).deleteByIds(any());
    }

    @Test void editRefusesBookedOrPlannedCalendar() {
        PrdCapacityCalendarBo bo = new PrdCapacityCalendarBo();
        bo.setCalendarId(70L);
        bo.setCalendarDate(java.sql.Date.valueOf(LocalDate.of(2026, 9, 24)));
        bo.setObjectType("WORKSTATION");
        bo.setObjectId(401L);
        bo.setAvailableHours(new BigDecimal("1.00"));
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(row("0.10"));
        assertThrows(ServiceException.class, () -> subject.updateByBo(bo));
        verify(mapper, never()).updateById(any(PrdCapacityCalendar.class));
    }

    @Test void bookedCalendarCannotBeReleasedThroughService() {
        PrdCapacityCalendar existing = row("0.50");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        PrdScheduleCapacityBooking booked = new PrdScheduleCapacityBooking();
        booked.setCalendarId(70L);
        booked.setTenantId("000000");
        when(bookingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(booked));

        assertThrows(ServiceException.class,
            () -> subject.releaseCapacity(70L, new BigDecimal("0.10")));

        verify(mapper, never()).updateById(any(PrdCapacityCalendar.class));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test void occupyUpdatesOnlyPlannedAndRemainingNeverActualHours() {
        PrdCapacityCalendar existing = row("0.00");
        existing.setActualHours(new BigDecimal("0.30"));
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        subject.occupyCapacity(70L, new BigDecimal("0.20"));

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Wrapper<PrdCapacityCalendar>> updates =
            (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), updates.capture());
        String columns = ((LambdaUpdateWrapper<PrdCapacityCalendar>) updates.getValue()).getSqlSet().toLowerCase();
        org.junit.jupiter.api.Assertions.assertTrue(columns.contains("planned_hours"));
        org.junit.jupiter.api.Assertions.assertTrue(columns.contains("remaining_hours"));
        org.junit.jupiter.api.Assertions.assertFalse(columns.contains("actual_hours"));
        verify(mapper, never()).updateById(any(PrdCapacityCalendar.class));
    }

    @Test void calendarGenerationLocksShiftDefinitionsBeforeUsingTheirTimes() {
        SchPlan plan = new SchPlan();
        plan.setPlanId(1L);
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(shiftMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThrows(ServiceException.class, () -> subject.generateFromSchedule(1L,
            java.sql.Date.valueOf(LocalDate.of(2026, 9, 29)),
            java.sql.Date.valueOf(LocalDate.of(2026, 9, 29)),
            "WORKSTATION", 401L, "WS-401", "工位401"));

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Wrapper<com.nexus.erp.sch.domain.SchShift>> reads =
            (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
        verify(shiftMapper).selectList(reads.capture());
        org.junit.jupiter.api.Assertions.assertTrue(reads.getValue().getSqlSegment().toUpperCase().contains("FOR UPDATE"));
    }

    @Test void overnightBreakAcrossMidnightDeductsOneHourOnlyForNewCalendarRows() {
        SchPlan plan = new SchPlan();
        plan.setPlanId(1L);
        when(planMapper.selectById(1L)).thenReturn(plan);
        com.nexus.erp.sch.domain.SchShift shift = new com.nexus.erp.sch.domain.SchShift();
        shift.setShiftId(20L);
        shift.setStartTime(java.sql.Time.valueOf("22:00:00"));
        shift.setEndTime(java.sql.Time.valueOf("06:00:00"));
        shift.setIsCrossDay(1);
        shift.setRestStartTime(java.sql.Time.valueOf("23:30:00"));
        shift.setRestEndTime(java.sql.Time.valueOf("00:30:00"));
        when(shiftMapper.selectList(any(Wrapper.class))).thenReturn(List.of(shift));
        when(mapper.insert(any(PrdCapacityCalendar.class))).thenReturn(1);
        java.sql.Date day = java.sql.Date.valueOf(LocalDate.of(2026, 9, 24));

        subject.generateFromSchedule(1L, day, day, "WORKSTATION", 401L, "WS-401", "工位401");

        ArgumentCaptor<PrdCapacityCalendar> saved = ArgumentCaptor.forClass(PrdCapacityCalendar.class);
        verify(mapper).insert(saved.capture());
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("7.00").compareTo(saved.getValue().getAvailableHours()));
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("7.00").compareTo(saved.getValue().getRemainingHours()));
        verify(mapper, never()).updateById(any(PrdCapacityCalendar.class)); /** 不补改既有行 */
    }

    @Test void calendarGenerationPropagatesDeadlockRatherThanTreatingItAsDuplicate() {
        SchPlan plan = new SchPlan();
        plan.setPlanId(1L);
        when(planMapper.selectById(1L)).thenReturn(plan);
        com.nexus.erp.sch.domain.SchShift shift = new com.nexus.erp.sch.domain.SchShift();
        shift.setShiftId(20L);
        when(shiftMapper.selectList(any(Wrapper.class))).thenReturn(List.of(shift));
        org.springframework.dao.DeadlockLoserDataAccessException deadlock =
            new org.springframework.dao.DeadlockLoserDataAccessException("deadlock", new java.sql.SQLException("1213"));
        when(mapper.insert(any(PrdCapacityCalendar.class))).thenThrow(deadlock);
        java.sql.Date day = java.sql.Date.valueOf(LocalDate.of(2026, 9, 24));

        org.junit.jupiter.api.Assertions.assertSame(deadlock, assertThrows(
            org.springframework.dao.DeadlockLoserDataAccessException.class,
            () -> subject.generateFromSchedule(1L, day, day, "WORKSTATION", 401L, "WS-401", "工位401")));
    }

    @Test void calendarBatchDeleteLocksPrimaryKeysInGlobalAscendingOrder() {
        PrdCapacityCalendar high = row("0.00");
        PrdCapacityCalendar low = row("0.00");
        low.setCalendarId(10L);
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> query = invocation.getArgument(0);
            query.getSqlSegment();
            return query.getParamNameValuePairs().containsValue(10L) ? low : high;
        });
        when(mapper.deleteByIds(any())).thenReturn(2);

        org.junit.jupiter.api.Assertions.assertTrue(subject.deleteWithValidByIds(List.of(70L, 10L), true));

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Wrapper<PrdCapacityCalendar>> locks = (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.InOrder order = inOrder(mapper);
        order.verify(mapper, times(2)).selectOne(locks.capture());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(10L, 70L), locks.getAllValues().stream().map(query -> {
            query.getSqlSegment();
            return ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) query)
                .getParamNameValuePairs().values().stream().filter(Long.class::isInstance)
                .map(Long.class::cast).findFirst().orElseThrow();
        }).toList());
        org.junit.jupiter.api.Assertions.assertTrue(locks.getAllValues().stream().allMatch(
            query -> query.getSqlSegment().toUpperCase().contains("FOR UPDATE")));
    }

    private PrdCapacityCalendar row(String planned) {
        PrdCapacityCalendar row = new PrdCapacityCalendar();
        row.setCalendarId(70L);
        row.setTenantId("000000");
        row.setStatus("ACTIVE");
        row.setPlannedHours(new BigDecimal(planned));
        row.setRemainingHours(new BigDecimal("1.00"));
        row.setEffectiveHours(new BigDecimal("1.00"));
        return row;
    }
}
