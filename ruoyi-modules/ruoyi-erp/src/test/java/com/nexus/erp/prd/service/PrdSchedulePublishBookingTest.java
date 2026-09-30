package com.nexus.erp.prd.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.PrdScheduleCapacityBooking;
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import com.nexus.erp.prd.domain.PrdScheduleTask;
import com.nexus.erp.prd.domain.PrdScheduleTaskDayLoad;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.mapper.PrdScheduleCapacityBookingMapper;
import com.nexus.erp.prd.mapper.PrdSchedulePlanMapper;
import com.nexus.erp.prd.mapper.PrdScheduleTaskDayLoadMapper;
import com.nexus.erp.prd.mapper.PrdScheduleTaskMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.schedule.PrdScheduleInputAssembler;
import com.nexus.erp.prd.service.impl.PrdScheduleServiceImpl;
import com.nexus.erp.sch.domain.SchHoliday;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.mapper.SchHolidayMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
@ExtendWith(MockitoExtension.class)
class PrdSchedulePublishBookingTest {
    private static final LocalDate D1 = LocalDate.of(2026, 9, 24);
    private static final String TENANT = "000000";
    @Mock PrdSchedulePlanMapper planMapper;
    @Mock PrdScheduleTaskMapper taskMapper;
    @Mock PrdScheduleTaskDayLoadMapper dayMapper;
    @Mock PrdWorkOrderMapper workOrderMapper;
    @Mock PrdScheduleInputAssembler inputAssembler;
    @Mock PrdCapacityCalendarMapper calendarMapper;
    @Mock IPrdCapacityCalendarService calendarService;
    @Mock PrdScheduleCapacityBookingMapper bookingMapper;
    @Mock SchShiftMapper shiftMapper;
    @Mock SchHolidayMapper holidayMapper;
    @InjectMocks PrdScheduleServiceImpl subject;

    @BeforeAll static void init() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PrdSchedulePlan.class);
        TableInfoHelper.initTableInfo(assistant, PrdScheduleTask.class);
        TableInfoHelper.initTableInfo(assistant, PrdScheduleTaskDayLoad.class);
        TableInfoHelper.initTableInfo(assistant, PrdScheduleCapacityBooking.class);
        TableInfoHelper.initTableInfo(assistant, PrdCapacityCalendar.class);
        TableInfoHelper.initTableInfo(assistant, PrdWorkOrder.class);
        TableInfoHelper.initTableInfo(assistant, SchShift.class);
        TableInfoHelper.initTableInfo(assistant, SchHoliday.class);
    }

    @Test void booksOnlySavedDaysAndRealShiftChronologyWithServerIds() {
        PrdScheduleTask task = task(D1, D1.plusDays(2), "0.20", "0.10");
        setup(task, List.of(day(task, D1, "0.10"), day(task, D1.plusDays(2), "0.20")),
            List.of(calendar(2L, D1, 20L, "0.10"), calendar(1L, D1, 10L, "0.10"),
                calendar(3L, D1.plusDays(1), 10L, "8.00"), calendar(4L, D1.plusDays(2), 20L, "0.20")),
            List.of(shift(20L, "08:00:00"), shift(10L, "16:00:00")));
        when(bookingMapper.insert(any(PrdScheduleCapacityBooking.class))).thenReturn(1);

        assertTrue(subject.publish(8001L));

        ArgumentCaptor<PrdScheduleCapacityBooking> saved = ArgumentCaptor.forClass(PrdScheduleCapacityBooking.class);
        verify(bookingMapper, times(2)).insert(saved.capture());
        List<PrdScheduleCapacityBooking> bookings = saved.getAllValues();
        assertEquals(List.of(2L, 4L), bookings.stream().map(PrdScheduleCapacityBooking::getCalendarId).toList());
        assertTrue(bookings.stream().allMatch(b -> b.getBookingId() != null && b.getBookingId() > 0
            && TENANT.equals(b.getTenantId()) && b.getTaskId().equals(task.getTaskId())));
        assertEquals(2, bookings.stream().map(PrdScheduleCapacityBooking::getBookingId).distinct().count());
        verify(calendarService, never()).occupyCapacity(eq(1L), any());
        verify(calendarService, never()).occupyCapacity(eq(3L), any());
    }

    @Test void staleSnapshotOfAnotherPublishedPlanCannotPassCurrentRead() {
        PrdScheduleTask task = task(D1, D1, "0.10", "0.00");
        setup(task, List.of(day(task, D1, "0.10")),
            List.of(calendar(1L, D1, 20L, "0.10")), List.of(shift(20L, "08:00:00")));
        PrdScheduleTask otherTask = task(D1, D1, "0.10", "0.00");
        otherTask.setPlanId(9999L);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task), List.of(otherTask));
        PrdSchedulePlan stale = plan();
        stale.setPlanId(9999L);
        PrdSchedulePlan current = plan();
        current.setPlanId(9999L);
        current.setStatus("PUBLISHED");
        lenient().when(planMapper.selectBatchIds(any())).thenReturn(List.of(stale));
        lenient().when(planMapper.selectOne(any(Wrapper.class))).thenReturn(current);
        lenient().when(bookingMapper.insert(any(PrdScheduleCapacityBooking.class))).thenReturn(1);

        assertThrows(ServiceException.class, () -> subject.publish(8001L));

        InOrder locks = inOrder(workOrderMapper, planMapper);
        locks.verify(workOrderMapper).selectOne(any(Wrapper.class));
        ArgumentCaptor<Wrapper<PrdSchedulePlan>> otherPlanLock = wrapperCaptor();
        locks.verify(planMapper).selectOne(otherPlanLock.capture());
        assertTrue(otherPlanLock.getValue().getSqlSegment().toUpperCase().contains("FOR UPDATE"));
        verify(planMapper, never()).selectBatchIds(any());
        verify(bookingMapper, never()).insert(any(PrdScheduleCapacityBooking.class));
    }

    @Test void invalidShiftEndTimeOrCrossDayFlagFailsClosed() {
        PrdScheduleTask task = task(D1, D1, "0.10", "0.00");
        SchShift noEnd = shift(20L, "08:00:00");
        noEnd.setEndTime(null);
        SchShift reversed = shift(20L, "16:00:00");
        reversed.setEndTime(java.sql.Time.valueOf("08:00:00"));
        reversed.setIsCrossDay(0);
        SchShift falseCross = shift(20L, "22:00:00");
        falseCross.setEndTime(java.sql.Time.valueOf("06:00:00"));
        falseCross.setIsCrossDay(0);
        SchShift unknownFlag = shift(20L, "08:00:00");
        unknownFlag.setIsCrossDay(null);
        SchShift invalidCross = shift(20L, "08:00:00");
        invalidCross.setIsCrossDay(1);
        for (SchShift invalid : List.of(noEnd, reversed, falseCross, unknownFlag, invalidCross)) {
            setup(task, List.of(day(task, D1, "0.10")),
                List.of(calendar(1L, D1, 20L, "0.10")), List.of(invalid));
            assertThrows(ServiceException.class, () -> subject.publish(8001L));
        }
        verifyNoInteractions(bookingMapper, calendarService);
    }

    @Test void explicitCrossDayShiftWithEndBeforeStartIsValid() {
        PrdScheduleTask task = task(D1, D1, "0.10", "0.00");
        SchShift overnight = shift(20L, "22:00:00");
        overnight.setEndTime(java.sql.Time.valueOf("06:00:00"));
        overnight.setIsCrossDay(1);
        setup(task, List.of(day(task, D1, "0.10")),
            List.of(calendar(1L, D1, 20L, "0.10")), List.of(overnight));
        when(bookingMapper.insert(any(PrdScheduleCapacityBooking.class))).thenReturn(1);
        assertTrue(subject.publish(8001L));
        verify(bookingMapper).insert(any(PrdScheduleCapacityBooking.class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> ArgumentCaptor<Wrapper<T>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    @Test void otherTaskInsertedAfterSnapshotIsSeenByCurrentRead() {
        PrdScheduleTask own = task(D1, D1, "0.10", "0.00");
        setup(own, List.of(day(own, D1, "0.10")),
            List.of(calendar(1L, D1, 20L, "0.10")), List.of(shift(20L, "08:00:00")));
        PrdScheduleTask competing = task(D1, D1, "0.10", "0.00");
        competing.setPlanId(9999L);
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        when(taskMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            if (reads.getAndIncrement() == 0) return List.of(own);
            Wrapper<?> query = invocation.getArgument(0);
            return query.getSqlSegment().toUpperCase().contains("FOR UPDATE")
                ? List.of(competing) : List.of(); /** RR 普通读复用旧快照 */
        });
        PrdSchedulePlan published = plan();
        published.setPlanId(9999L);
        published.setStatus("PUBLISHED");
        lenient().when(planMapper.selectOne(any(Wrapper.class))).thenReturn(published);
        lenient().when(bookingMapper.insert(any(PrdScheduleCapacityBooking.class))).thenReturn(1);

        assertThrows(ServiceException.class, () -> subject.publish(8001L));

        ArgumentCaptor<Wrapper<PrdScheduleTask>> scans = wrapperCaptor();
        verify(taskMapper, times(2)).selectList(scans.capture());
        assertTrue(scans.getAllValues().get(1).getSqlSegment().toUpperCase().contains("FOR UPDATE"));
        verify(bookingMapper, never()).insert(any(PrdScheduleCapacityBooking.class));
    }

    @Test void holidayInsertedAfterInitialSnapshotIsSeenByLockingRangeRead() {
        PrdScheduleTask own = task(D1, D1, "0.10", "0.00");
        setup(own, List.of(day(own, D1, "0.10")),
            List.of(calendar(1L, D1, 20L, "0.10")), List.of(shift(20L, "08:00:00")));
        SchHoliday newlyClosed = new SchHoliday();
        newlyClosed.setHolidayDate(java.sql.Date.valueOf(D1));
        newlyClosed.setIsWorkingDay(0);
        newlyClosed.setTenantId(TENANT);
        when(holidayMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            return query.getSqlSegment().toUpperCase().contains("FOR UPDATE")
                ? List.of(newlyClosed) : List.of();
        });
        lenient().when(bookingMapper.insert(any(PrdScheduleCapacityBooking.class))).thenReturn(1);

        assertThrows(ServiceException.class, () -> subject.publish(8001L));

        ArgumentCaptor<Wrapper<SchHoliday>> scans = wrapperCaptor();
        verify(holidayMapper).selectList(scans.capture());
        String sql = scans.getValue().getSqlSegment().toUpperCase();
        assertTrue(sql.contains("FOR UPDATE") && sql.contains("HOLIDAY_DATE"));
        verify(bookingMapper, never()).insert(any(PrdScheduleCapacityBooking.class));
    }

    @Test void calendarRowsAcrossResourcesAreLockedInGlobalIdOrder() {
        PrdScheduleTask first = task(D1, D1, "0.10", "0.00");
        PrdScheduleTask second = task(D1, D1, "0.10", "0.00");
        second.setTaskId(9002L);
        second.setResourceId(402L);
        PrdCapacityCalendar high = calendar(100L, D1, 20L, "0.10");
        PrdCapacityCalendar low = calendar(10L, D1, 20L, "0.10");
        low.setObjectId(402L);
        setup(first, List.of(day(first, D1, "0.10"), day(second, D1, "0.10")),
            List.of(high, low), List.of(shift(20L, "08:00:00")));
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
        doAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            return queryValues(query).contains(10L) ? low : high;
        }).when(calendarMapper).selectOne(any(Wrapper.class));
        when(bookingMapper.insert(any(PrdScheduleCapacityBooking.class))).thenReturn(1);

        assertTrue(subject.publish(8001L));

        ArgumentCaptor<Wrapper<PrdCapacityCalendar>> locks = wrapperCaptor();
        InOrder order = inOrder(calendarMapper);
        order.verify(calendarMapper, times(2)).selectOne(locks.capture());
        assertEquals(List.of(10L, 100L), locks.getAllValues().stream()
            .map(this::queryValues).map(values -> values.stream().filter(Long.class::isInstance)
                .map(Long.class::cast).findFirst().orElseThrow()).toList());
    }

    private java.util.Collection<Object> queryValues(Wrapper<?> query) {
        query.getSqlSegment();
        return ((AbstractWrapper<?, ?, ?>) query).getParamNameValuePairs().values();
    }

    @Test void earlierShiftAddedAfterSnapshotCannotBeSkippedByPublish() {
        assertCandidatePhantomRefusesPublication("08:00:00");
    }

    @Test void sameStartShiftAddedAfterSnapshotCannotEscapeDuplicateValidation() {
        assertCandidatePhantomRefusesPublication("16:00:00");
    }

    private void assertCandidatePhantomRefusesPublication(String newStartTime) {
        PrdScheduleTask task = task(D1, D1, "0.10", "0.00");
        PrdCapacityCalendar visible = calendar(100L, D1, 10L, "0.10");
        PrdCapacityCalendar newlyInserted = calendar(10L, D1, 20L, "0.10");
        setup(task, List.of(day(task, D1, "0.10")), List.of(visible),
            List.of(shift(10L, "16:00:00"), shift(20L, newStartTime)));
        lenient().doAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            java.util.Collection<Object> ids = queryValues(query);
            return ids.contains(20L)
                ? List.of(shift(10L, "16:00:00"), shift(20L, newStartTime))
                : List.of(shift(10L, "16:00:00"));
        }).when(shiftMapper).selectList(any(Wrapper.class));
        /** RR 初始快照只有晚班。后续当前范围读必须看见手工新增的班次日历。 */
        doAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            return query.getSqlSegment().toUpperCase().contains("FOR UPDATE")
                ? List.of(newlyInserted, visible) : List.of(visible);
        }).when(calendarMapper).selectList(any(Wrapper.class));
        lenient().when(bookingMapper.insert(any(PrdScheduleCapacityBooking.class))).thenReturn(1);

        assertThrows(ServiceException.class, () -> subject.publish(8001L));

        ArgumentCaptor<Wrapper<PrdCapacityCalendar>> scans = wrapperCaptor();
        verify(calendarMapper, atLeast(2)).selectList(scans.capture());
        assertTrue(scans.getAllValues().stream().anyMatch(query -> {
            String sql = query.getSqlSegment().toUpperCase();
            return sql.contains("FOR UPDATE") && sql.contains("CALENDAR_DATE")
                && sql.contains("OBJECT_ID");
        }));
        verify(bookingMapper, never()).insert(any(PrdScheduleCapacityBooking.class));
        verify(calendarService, never()).occupyCapacity(any(), any());
    }

    @Test void deletingCandidateLocksWorkOrderAndTasksBeforeThePlan() {
        PrdScheduleTask task = task(D1, D1, "0.10", "0.00");
        lenient().when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(11L);
        wo.setTenantId(TENANT);
        lenient().when(workOrderMapper.selectOne(any(Wrapper.class))).thenReturn(wo);
        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(plan());
        when(planMapper.deleteByIds(any())).thenReturn(1);

        assertTrue(subject.deleteByIds(new Long[]{8001L}));

        InOrder locks = inOrder(taskMapper, workOrderMapper, planMapper);
        locks.verify(taskMapper).selectList(any(Wrapper.class)); /** 发现工单 ID，不加锁 */
        locks.verify(workOrderMapper).selectOne(any(Wrapper.class));
        ArgumentCaptor<Wrapper<PrdScheduleTask>> current = wrapperCaptor();
        locks.verify(taskMapper).selectList(current.capture());
        assertTrue(current.getValue().getSqlSegment().toUpperCase().contains("FOR UPDATE"));
        locks.verify(planMapper).selectOne(any(Wrapper.class));
        verify(taskMapper).delete(any(Wrapper.class));
    }

    @Test void missingPlanCannotCommitEarlierTaskAndDayLoadDeletes() {
        lenient().when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThrows(ServiceException.class, () -> subject.deleteByIds(new Long[]{8001L}));

        verify(dayMapper, never()).delete(any(Wrapper.class));
        verify(taskMapper, never()).delete(any(Wrapper.class));
        verify(planMapper, never()).deleteByIds(any());
    }

    @Test void partialBatchPlanDeleteThrowsSoTaskDeletesCanRollBack() throws NoSuchMethodException {
        PrdSchedulePlan low = plan();
        PrdSchedulePlan high = plan();
        high.setPlanId(9002L);
        lenient().when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(planMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation ->
            queryValues(invocation.getArgument(0)).contains(8001L) ? low : high);
        when(planMapper.deleteByIds(any())).thenReturn(1); /** 期望2行，但只写成1行 */

        assertThrows(ServiceException.class, () -> subject.deleteByIds(new Long[]{8001L, 9002L}));
        verify(dayMapper, times(2)).delete(any(Wrapper.class));
        Transactional tx = PrdScheduleServiceImpl.class
            .getMethod("deleteByIds", Long[].class).getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertArrayEquals(new Class<?>[]{Exception.class}, tx.rollbackFor());
    }

    @Test void batchDeletionLocksPlanIdsAndDeletesInAscendingOrder() {
        PrdSchedulePlan low = plan();
        PrdSchedulePlan high = plan();
        high.setPlanId(9002L);
        lenient().when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(planMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            return queryValues(query).contains(8001L) ? low : high;
        });
        when(planMapper.deleteByIds(any())).thenReturn(2);

        assertTrue(subject.deleteByIds(new Long[]{9002L, 8001L}));

        ArgumentCaptor<Wrapper<PrdSchedulePlan>> locks = wrapperCaptor();
        verify(planMapper, times(2)).selectOne(locks.capture());
        assertEquals(List.of(8001L, 9002L), locks.getAllValues().stream()
            .map(this::queryValues).map(values -> values.stream().filter(Long.class::isInstance)
                .map(Long.class::cast).findFirst().orElseThrow()).toList());
        verify(planMapper).deleteByIds(List.of(8001L, 9002L));
    }

    @Test void missingShiftOrUnknownStartTimeFailsClosed() {
        PrdScheduleTask task = task(D1, D1, "0.10", "0.00");
        for (Long shiftId : new Long[]{null, 20L}) {
            setup(task, List.of(day(task, D1, "0.10")),
                List.of(calendar(1L, D1, shiftId, "0.10")),
                shiftId == null ? List.of() : List.of(shift(20L, null)));
            assertThrows(ServiceException.class, () -> subject.publish(8001L));
        }
        verifyNoInteractions(bookingMapper, calendarService);
    }

    @Test void wrongTenantCalendarOrShiftMustNotBeBooked() {
        PrdScheduleTask task = task(D1, D1, "0.10", "0.00");
        PrdCapacityCalendar other = calendar(1L, D1, 20L, "0.10");
        other.setTenantId("other");
        setup(task, List.of(day(task, D1, "0.10")), List.of(other), List.of(shift(20L, "08:00:00")));
        assertThrows(ServiceException.class, () -> subject.publish(8001L));
        verifyNoInteractions(bookingMapper, calendarService);
    }

    @Test void shiftNotFoundOrDoesNotMatchCalendarIsRejected() {
        PrdScheduleTask task = task(D1, D1, "0.10", "0.00");
        setup(task, List.of(day(task, D1, "0.10")),
            List.of(calendar(1L, D1, 20L, "0.10")), List.of(shift(10L, "08:00:00")));
        assertThrows(ServiceException.class, () -> subject.publish(8001L));
        verifyNoInteractions(bookingMapper, calendarService);
    }

    @Test void closedHolidayCannotAcquirePositiveSavedDayLoad() {
        PrdScheduleTask task = task(D1, D1, "0.10", "0.00");
        setup(task, List.of(day(task, D1, "0.10")),
            List.of(calendar(1L, D1, 20L, "0.10")), List.of(shift(20L, "08:00:00")));
        SchHoliday holiday = new SchHoliday();
        holiday.setHolidayDate(java.sql.Date.valueOf(D1));
        holiday.setIsWorkingDay(0);
        lenient().when(holidayMapper.selectList(any(Wrapper.class))).thenReturn(List.of(holiday));
        assertThrows(ServiceException.class, () -> subject.publish(8001L));
        verifyNoInteractions(bookingMapper, calendarService);
    }

    @Test void missingOrExhaustedCalendarNeverCreatesBooking() {
        PrdScheduleTask task = task(D1, D1, "0.10", "0.00");
        setup(task, List.of(day(task, D1, "0.10")), List.of(), List.of());
        assertThrows(ServiceException.class, () -> subject.publish(8001L));
        verifyNoInteractions(bookingMapper, calendarService);
    }

    @Test void duplicatePublishedPlanOnSameWorkOrderRejectsBeforeBooking() {
        PrdScheduleTask task = task(D1, D1, "0.10", "0.00");
        setup(task, List.of(day(task, D1, "0.10")),
            List.of(calendar(1L, D1, 20L, "0.10")), List.of(shift(20L, "08:00:00")));
        PrdScheduleTask other = task(D1, D1, "0.10", "0.00");
        other.setPlanId(9999L);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task), List.of(other));
        PrdSchedulePlan published = plan();
        published.setPlanId(9999L);
        published.setStatus("PUBLISHED");
        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(published);
        assertThrows(ServiceException.class, () -> subject.publish(8001L));
        verifyNoInteractions(bookingMapper, calendarService);
    }

    @Test void invalidDailyTotalsOrMissingCapacityRejectWithoutPartialBooking() {
        PrdScheduleTask task = task(D1, D1, "0.07", "0.10");
        setup(task, List.of(day(task, D1, "0.07")),
            List.of(calendar(1L, D1, 20L, "0.17")), List.of(shift(20L, "08:00:00")));
        assertThrows(ServiceException.class, () -> subject.publish(8001L));
        verifyNoInteractions(bookingMapper, calendarService);
    }

    @Test void failedSecondBookingThrowsWithinTransactionalPublish() throws NoSuchMethodException {
        PrdScheduleTask task = task(D1, D1, "0.20", "0.00");
        setup(task, List.of(day(task, D1, "0.20")),
            List.of(calendar(1L, D1, 20L, "0.10"), calendar(2L, D1, 10L, "0.10")),
            List.of(shift(20L, "08:00:00"), shift(10L, "16:00:00")));
        when(bookingMapper.insert(any(PrdScheduleCapacityBooking.class))).thenReturn(1, 0);
        assertThrows(ServiceException.class, () -> subject.publish(8001L));
        verify(bookingMapper, times(2)).insert(any(PrdScheduleCapacityBooking.class));
        Transactional tx = PrdScheduleServiceImpl.class.getMethod("publish", Long.class).getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertArrayEquals(new Class<?>[]{Exception.class}, tx.rollbackFor());
    }

    @Test void deletingCandidateClearsOnlyItsDayLoads() {
        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(plan());
        when(planMapper.deleteByIds(any())).thenReturn(1);
        assertTrue(subject.deleteByIds(new Long[]{8001L}));
        verify(dayMapper).delete(any(Wrapper.class));
        verify(bookingMapper, never()).delete(any(Wrapper.class));
    }

    private void setup(PrdScheduleTask task, List<PrdScheduleTaskDayLoad> loads,
                       List<PrdCapacityCalendar> rows, List<SchShift> shifts) {
        lenient().when(planMapper.selectById(8001L)).thenReturn(plan());
        lenient().when(planMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        lenient().when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        lenient().when(dayMapper.selectList(any(Wrapper.class))).thenReturn(loads);
        lenient().when(calendarMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            java.util.Collection<Object> params = queryValues(query);
            LocalDate date = params.stream().filter(Date.class::isInstance).map(Date.class::cast)
                .map(value -> value instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate()
                    : value.toInstant().atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate())
                .findFirst().orElseThrow();
            return rows.stream().filter(row -> params.contains(row.getObjectId())
                && date.equals(((java.sql.Date) row.getCalendarDate()).toLocalDate())).toList();
        });
        lenient().when(shiftMapper.selectList(any(Wrapper.class))).thenReturn(shifts);
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(11L);
        wo.setTenantId(TENANT);
        wo.setWoStatus("RELEASED");
        lenient().when(workOrderMapper.selectById(11L)).thenReturn(wo);
        lenient().when(workOrderMapper.selectOne(any(Wrapper.class))).thenReturn(wo);
        lenient().when(workOrderMapper.updateById(any(PrdWorkOrder.class))).thenReturn(1);
        Map<Long, PrdCapacityCalendar> byId = new java.util.HashMap<>();
        for (PrdCapacityCalendar row : rows) byId.put(row.getCalendarId(), row);
        lenient().when(calendarMapper.selectOne(any(Wrapper.class))).thenAnswer(i -> {
            Wrapper<?> query = i.getArgument(0);
            return queryValues(query).stream().filter(Long.class::isInstance)
                .map(Long.class::cast).map(byId::get).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        });
        lenient().when(calendarMapper.selectById(any())).thenAnswer(i -> byId.get(i.getArgument(0)));
        lenient().doAnswer(i -> {
            PrdCapacityCalendar row = byId.get(i.getArgument(0));
            BigDecimal hours = i.getArgument(1);
            row.setPlannedHours(row.getPlannedHours().add(hours));
            row.setRemainingHours(row.getRemainingHours().subtract(hours));
            return null;
        }).when(calendarService).occupyCapacity(any(), any());
    }

    private PrdSchedulePlan plan() {
        PrdSchedulePlan p = new PrdSchedulePlan();
        p.setPlanId(8001L); p.setStatus("COMPLETED"); p.setCapacityCalcVersion(2);
        p.setTenantId(TENANT); p.setVersion(0);
        return p;
    }
    private PrdScheduleTask task(LocalDate from, LocalDate to, String process, String setup) {
        PrdScheduleTask t = new PrdScheduleTask();
        t.setTaskId(9001L); t.setPlanId(8001L); t.setWoId(11L); t.setTenantId(TENANT);
        t.setResourceType("WORKSTATION"); t.setResourceId(401L);
        t.setStartTime(java.sql.Date.valueOf(from)); t.setEndTime(java.sql.Date.valueOf(to));
        t.setProcessHours(new BigDecimal(process)); t.setSetupHours(new BigDecimal(setup));
        return t;
    }
    private PrdScheduleTaskDayLoad day(PrdScheduleTask task, LocalDate date, String hours) {
        PrdScheduleTaskDayLoad l = new PrdScheduleTaskDayLoad();
        l.setLoadId(50_000L + date.toEpochDay());
        l.setPlanId(task.getPlanId()); l.setTaskId(task.getTaskId()); l.setTenantId(TENANT);
        l.setResourceType(task.getResourceType()); l.setResourceId(task.getResourceId());
        l.setLoadDate(java.sql.Date.valueOf(date)); l.setPlannedHours(new BigDecimal(hours));
        return l;
    }
    private PrdCapacityCalendar calendar(Long id, LocalDate date, Long shift, String hours) {
        PrdCapacityCalendar c = new PrdCapacityCalendar();
        c.setCalendarId(id); c.setTenantId(TENANT); c.setStatus("ACTIVE");
        c.setObjectType("WORKSTATION"); c.setObjectId(401L);
        c.setCalendarDate(java.sql.Date.valueOf(date)); c.setShiftId(shift);
        c.setEffectiveHours(new BigDecimal(hours)); c.setRemainingHours(new BigDecimal(hours));
        c.setPlannedHours(BigDecimal.ZERO);
        return c;
    }
    private SchShift shift(Long id, String start) {
        SchShift s = new SchShift();
        s.setShiftId(id); s.setTenantId(TENANT);
        if (start != null) {
            s.setStartTime(java.sql.Time.valueOf(start));
            s.setEndTime(java.sql.Time.valueOf(start.equals("16:00:00") ? "23:00:00" : "16:00:00"));
            s.setIsCrossDay(0);
        }
        return s;
    }
}
