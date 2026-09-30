package com.nexus.erp.prd.schedule;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.mapper.PrdScheduleNoSequenceMapper;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Insert;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 单元测试边界：数据库唯一性与并发行为在 MySQL 中另行验证。 */
@ExtendWith(MockitoExtension.class)
class PrdScheduleNumberAllocatorTest {
    @Mock PrdScheduleNoSequenceMapper mapper;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 30);

    @Test void reservesPerTenantAndStartDateBeforeFormattingFourDigitPlanNo() {
        when(mapper.reserveNext("000000", DATE)).thenReturn(2);
        when(mapper.currentSequence("000000", DATE)).thenReturn(2);
        assertEquals("APS202609300002", new PrdScheduleNumberAllocator(mapper).nextNumber("000000", DATE));
        verify(mapper).reserveNext("000000", DATE);
        verify(mapper).currentSequence("000000", DATE);
    }

    @Test void neverUsesNonAsciiDigitsUnderArabicDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            when(mapper.reserveNext("000000", DATE)).thenReturn(2);
            when(mapper.currentSequence("000000", DATE)).thenReturn(12);
            assertEquals("APS202609300012", new PrdScheduleNumberAllocator(mapper).nextNumber("000000", DATE));
        } finally {
            Locale.setDefault(original);
        }
    }
    @Test void reservesUniqueTenantDateKeyAtomicallyAndRequiresCallerTransaction() throws ReflectiveOperationException {
        var method = PrdScheduleNoSequenceMapper.class.getMethod("reserveNext", String.class, LocalDate.class);
        String sql = method.getAnnotation(Insert.class).value()[0];
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE last_seq = last_seq + 1"));
        assertTrue(sql.contains("tenant_id, plan_date, last_seq"));
        var next = PrdScheduleNumberAllocator.class.getMethod("nextNumber", String.class, LocalDate.class);
        assertEquals(Propagation.MANDATORY, next.getAnnotation(Transactional.class).propagation(),
            "reserve and read must use the outer plan-insert transaction");
    }

    @Test void refusesInvalidTenantOrDateWithoutIssuingDatabaseCommand() {
        PrdScheduleNumberAllocator allocator = new PrdScheduleNumberAllocator(mapper);
        assertThrows(ServiceException.class, () -> allocator.nextNumber(null, DATE));
        assertThrows(ServiceException.class, () -> allocator.nextNumber("", DATE));
        assertThrows(ServiceException.class, () -> allocator.nextNumber("000000", null));
        verifyNoInteractions(mapper);
    }

    @Test void refusesNonPositiveReservationOrUnobservableSequence() {
        when(mapper.reserveNext("000000", DATE)).thenReturn(0);
        assertThrows(ServiceException.class, () -> new PrdScheduleNumberAllocator(mapper).nextNumber("000000", DATE));
    }

    @Test void refusesOverflowInsteadOfCreatingFiveDigitNumber() {
        when(mapper.reserveNext("000000", DATE)).thenReturn(2);
        when(mapper.currentSequence("000000", DATE)).thenReturn(10000);
        assertThrows(ServiceException.class, () -> new PrdScheduleNumberAllocator(mapper).nextNumber("000000", DATE));
    }
}
