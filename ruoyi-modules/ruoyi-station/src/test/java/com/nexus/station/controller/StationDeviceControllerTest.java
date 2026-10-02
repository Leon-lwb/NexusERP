package com.nexus.station.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.station.domain.dto.StationPassReportDto;
import com.nexus.station.mapper.StationDefectCodeMapper;
import com.nexus.station.mapper.StationSchWorkerMapper;
import com.nexus.station.service.*;
import com.nexus.system.service.ISysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 设备通道事务外的重试结果分类与拒绝日志脱敏。 */
class StationDeviceControllerTest {
    StationPassService pass=mock(StationPassService.class);
    StationDeviceController controller() {
        var c=new StationDeviceController(mock(StationSchWorkerMapper.class),mock(StationDefectCodeMapper.class),mock(ISysUserService.class),
            mock(StationAndonService.class),pass,mock(StationSnService.class),mock(StationTraceService.class),mock(StationInspectionService.class));
        ReflectionTestUtils.setField(c,"apiKey","test-only-key"); return c;
    }
    @Test void rejectedHeaderValueIsNeverWrittenToLog() {
        var log=(Logger)LoggerFactory.getLogger(StationDeviceController.class); var appender=new ListAppender<ILoggingEvent>();appender.start();log.addAppender(appender);
        try {
            assertThrows(ServiceException.class,()->controller().passReport("test-rejected-marker",new StationPassReportDto(),mock(HttpServletRequest.class)));
            assertTrue(appender.list.stream().noneMatch(e->e.getFormattedMessage().contains("test-rejected-marker")));
            verifyNoInteractions(pass);
        } finally { log.detachAppender(appender); appender.stop(); }
    }
    static java.util.stream.Stream<RuntimeException> retryableFailures() {
        return java.util.stream.Stream.of(new CannotAcquireLockException("test-only-database-detail"),
            new org.springframework.dao.DataAccessResourceFailureException("test-only-database-detail"),
            new org.springframework.dao.RecoverableDataAccessException("test-only-database-detail"),
            new org.springframework.transaction.CannotCreateTransactionException("test-only-database-detail"),
            new org.springframework.transaction.TransactionSystemException("test-only-database-detail"));
    }
    @ParameterizedTest @MethodSource("retryableFailures")
    void transientDatabaseAbortIsRetryableOnlyAfterServiceTransactionReturns(RuntimeException failure) {
        try(var tenant=mockStatic(TenantHelper.class)) {
            tenant.when(()->TenantHelper.dynamic(eq("000000"),any(Supplier.class))).thenAnswer(i->i.getArgument(1,Supplier.class).get());
            when(pass.reportSingle(any(),any())).thenThrow(failure);
            var response=controller().passReport("test-only-key",new StationPassReportDto(),mock(HttpServletRequest.class));
            assertEquals(503,response.getCode()); assertFalse(response.getMsg().contains("test-only-database-detail"));
        }
    }
    @Test void businessRejectionIsNotClassifiedAsRetryableTransactionFailure() {
        try(var tenant=mockStatic(TenantHelper.class)) {
            tenant.when(()->TenantHelper.dynamic(eq("000000"),any(Supplier.class))).thenAnswer(i->i.getArgument(1,Supplier.class).get());
            when(pass.reportSingle(any(),any())).thenThrow(new ServiceException("业务拒绝"));
            assertThrows(ServiceException.class,()->controller().passReport("test-only-key",new StationPassReportDto(),mock(HttpServletRequest.class)));
        }
    }

}
