package com.nexus.erp.sch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.PrdScheduleCapacityBooking;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.mapper.PrdScheduleCapacityBookingMapper;
import com.nexus.erp.sch.domain.SchHoliday;
import com.nexus.erp.sch.domain.bo.SchHolidayBo;
import com.nexus.erp.sch.mapper.SchHolidayMapper;
import com.nexus.erp.sch.service.impl.SchHolidayServiceImpl;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DeadlockLoserDataAccessException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

// 同名类型说明：本文件同时使用 java.sql.Date 和 java.util.Date，后者保留全限定名以区分。

@Tag("dev")
@ExtendWith(MockitoExtension.class)
class SchHolidayCapacityProtectionTest {
    private static final LocalDate DAY = LocalDate.of(2026, 9, 24);
    @Mock SchHolidayMapper holidayMapper;
    @Mock PrdCapacityCalendarMapper calendarMapper;
    @Mock PrdScheduleCapacityBookingMapper bookingMapper;
    @InjectMocks SchHolidayServiceImpl subject;

    @BeforeAll static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SchHoliday.class);
        TableInfoHelper.initTableInfo(assistant, PrdCapacityCalendar.class);
        TableInfoHelper.initTableInfo(assistant, PrdScheduleCapacityBooking.class);
    }

    @Test void insertOnAbsentHolidayDateLocksIndexedGapAndRefusesBookedCalendar() {
        lenient().when(holidayMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        lenient().when(calendarMapper.selectList(any(Wrapper.class))).thenReturn(List.of(calendar("0.50")));
        lenient().when(calendarMapper.selectOne(any(Wrapper.class))).thenReturn(calendar("0.50"));

        assertThrows(ServiceException.class, () -> subject.insertByBo(bo(DAY)));

        ArgumentCaptor<Wrapper<SchHoliday>> dateLock = wrapperCaptor();
        verify(holidayMapper).selectList(dateLock.capture());
        String sql = dateLock.getValue().getSqlSegment().toUpperCase();
        assertTrue(sql.contains("FOR UPDATE") && sql.contains("TENANT_ID") && sql.contains("HOLIDAY_DATE"));
        verify(holidayMapper, never()).insert(any(SchHoliday.class));
    }

    @Test void movingHolidayToBookedDayLocksBothDatesInAscendingOrderAndRejects() {
        LocalDate old = DAY.minusDays(1);
        SchHoliday existing = holiday(old);
        lenient().when(holidayMapper.selectById(1L)).thenReturn(existing);
        lenient().when(holidayMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        lenient().when(holidayMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        lenient().when(calendarMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(), List.of(calendar("0.50")));
        lenient().when(calendarMapper.selectOne(any(Wrapper.class))).thenReturn(calendar("0.50"));
        SchHolidayBo bo = bo(DAY);
        bo.setHolidayId(1L);

        assertThrows(ServiceException.class, () -> subject.updateByBo(bo));

        ArgumentCaptor<Wrapper<SchHoliday>> dates = wrapperCaptor();
        verify(holidayMapper, times(2)).selectList(dates.capture());
        assertEquals(
                List.of(old, DAY),
                dates.getAllValues().stream()
                        .map(
                                query -> {
                                    query.getSqlSegment();
                                    return ((AbstractWrapper<?, ?, ?>) query)
                                            .getParamNameValuePairs().values().stream()
                                                    .filter(java.util.Date.class::isInstance)
                                                    .map(java.util.Date.class::cast)
                                                    .map(
                                                            date ->
                                                                    Instant.ofEpochMilli(
                                                                                    date.getTime())
                                                                            .atZone(
                                                                                    ZoneId.of(
                                                                                            "Asia/Shanghai"))
                                                                            .toLocalDate())
                                                    .min(LocalDate::compareTo)
                                                    .orElseThrow();
                                })
                        .toList());
        verify(holidayMapper, never()).updateById(any(SchHoliday.class));
    }

    @Test void deleteOfBookedHolidayDateRejectsEvenWhenCalendarPlannedWasZero() {
        SchHoliday existing = holiday(DAY);
        lenient().when(holidayMapper.selectById(1L)).thenReturn(existing);
        lenient().when(holidayMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        lenient().when(holidayMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
        lenient().when(calendarMapper.selectList(any(Wrapper.class))).thenReturn(List.of(calendar("0.00")));
        lenient().when(calendarMapper.selectOne(any(Wrapper.class))).thenReturn(calendar("0.00"));
        PrdScheduleCapacityBooking booked = new PrdScheduleCapacityBooking();
        booked.setCalendarId(70L);
        lenient().when(bookingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(booked));

        ServiceException failure = assertThrows(ServiceException.class, () -> subject.deleteByIds(new Long[]{1L}));
        assertTrue(failure.getMessage().contains("预约凭据"));
        ArgumentCaptor<Wrapper<PrdScheduleCapacityBooking>> bookingLock = wrapperCaptor();
        verify(bookingMapper).selectList(bookingLock.capture());
        assertTrue(bookingLock.getValue().getSqlSegment().toUpperCase().contains("FOR UPDATE"));
        verify(holidayMapper, never()).deleteBatchIds(any());
    }

    @Test void movingHolidayLocksCalendarRowsAcrossDaysInGlobalIdOrder() {
        LocalDate old = DAY.minusDays(1);
        SchHoliday existing = holiday(old);
        lenient().when(holidayMapper.selectById(1L)).thenReturn(existing);
        lenient().when(holidayMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        lenient().when(holidayMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        PrdCapacityCalendar high = calendar("0.00");
        high.setCalendarId(100L);
        high.setCalendarDate(Date.valueOf(old));
        PrdCapacityCalendar low = calendar("0.00");
        lenient()
                .when(calendarMapper.selectList(any(Wrapper.class)))
                .thenAnswer(
                        invocation -> {
                            Wrapper<?> query = invocation.getArgument(0);
                            query.getSqlSegment();
                            return ((AbstractWrapper<?, ?, ?>) query)
                                            .getParamNameValuePairs()
                                            .containsValue(Date.valueOf(old))
                                    ? List.of(high)
                                    : List.of(low);
                        });
        lenient().when(calendarMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            query.getSqlSegment();
            return ((AbstractWrapper<?, ?, ?>) query).getParamNameValuePairs().containsValue(70L) ? low : high;
        });
        lenient().when(bookingMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        lenient().when(holidayMapper.updateById(any(SchHoliday.class))).thenReturn(1);
        SchHolidayBo bo = bo(DAY);
        bo.setHolidayId(1L);

        assertEquals(1, subject.updateByBo(bo));

        ArgumentCaptor<Wrapper<PrdCapacityCalendar>> locks = wrapperCaptor();
        InOrder order = inOrder(calendarMapper);
        order.verify(calendarMapper, times(2)).selectOne(locks.capture());
        assertEquals(List.of(70L, 100L), locks.getAllValues().stream().map(query -> {
            query.getSqlSegment();
            return ((AbstractWrapper<?, ?, ?>) query).getParamNameValuePairs().values().stream()
                .filter(Long.class::isInstance).map(Long.class::cast).findFirst().orElseThrow();
        }).toList());
    }

    @Test void dateEditCannotUseOldSnapshotWhenCalendarWasInsertedAndBookedAfterIt() {
        SchHoliday existing = holiday(DAY);
        lenient().when(holidayMapper.selectById(1L)).thenReturn(existing); /** 先创建 RR 快照 */
        lenient().when(holidayMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        lenient().when(holidayMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        PrdCapacityCalendar newlyBooked = calendar("0.50");
        lenient().when(calendarMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            return query.getSqlSegment().toUpperCase().contains("FOR UPDATE")
                ? List.of(newlyBooked) : List.of();
        });
        lenient().when(holidayMapper.updateById(any(SchHoliday.class))).thenReturn(1);
        SchHolidayBo bo = bo(DAY);
        bo.setHolidayId(1L);

        assertThrows(ServiceException.class, () -> subject.updateByBo(bo));

        ArgumentCaptor<Wrapper<PrdCapacityCalendar>> range = wrapperCaptor();
        verify(calendarMapper, atLeastOnce()).selectList(range.capture());
        assertTrue(range.getAllValues().stream().anyMatch(query ->
            query.getSqlSegment().toUpperCase().contains("FOR UPDATE")));
        verify(holidayMapper, never()).updateById(any(SchHoliday.class));
    }

    @Test void deadlockWhileLockingHolidayGapPropagatesForWholeTransactionRetry() {
        DeadlockLoserDataAccessException deadlock = new DeadlockLoserDataAccessException("1213", new SQLException("deadlock"));
        lenient().when(holidayMapper.selectList(any(Wrapper.class))).thenThrow(deadlock);

        assertSame(deadlock, assertThrows(DeadlockLoserDataAccessException.class,
            () -> subject.insertByBo(bo(DAY))));
        verify(holidayMapper, never()).insert(any(SchHoliday.class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> ArgumentCaptor<Wrapper<T>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    private SchHolidayBo bo(LocalDate date) {
        SchHolidayBo bo = new SchHolidayBo();
        bo.setHolidayDate(Date.valueOf(date));
        bo.setIsWorkingDay(0);
        return bo;
    }

    private SchHoliday holiday(LocalDate date) {
        SchHoliday holiday = new SchHoliday();
        holiday.setHolidayId(1L);
        holiday.setTenantId("000000");
        holiday.setHolidayDate(Date.valueOf(date));
        holiday.setIsWorkingDay(0);
        return holiday;
    }

    private PrdCapacityCalendar calendar(String planned) {
        PrdCapacityCalendar calendar = new PrdCapacityCalendar();
        calendar.setCalendarId(70L);
        calendar.setTenantId("000000");
        calendar.setCalendarDate(Date.valueOf(DAY));
        calendar.setPlannedHours(new BigDecimal(planned));
        return calendar;
    }
}
