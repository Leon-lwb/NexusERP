package com.nexus.erp.pu;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.dynamic.datasource.tx.TransactionContext;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec.SourceChangedException;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpDecisionPurchaseScopeGuard;
import com.nexus.erp.ai.adapter.mapper.ErpDecisionPurchaseScopeMapper;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.mapper.PuRequirementMaterialGuardMapper;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.pu.service.PuRequirementMaterialLockService;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * 安全位置日志的专属离线回归：真实入口和 Spring 事务生命周期，JDBC 仅为非联网夹具。
 * 敏感哨兵是假数据；只允许阶段、类型与三处业务位置，不能把异常对象交给日志设施。
 * 本测试不证明来源认证、性能修复、运行服务已加载或真实数据库已验收。
 */
class PuRequirementErpTransactionFailureDiagnosticTest {
    private static final String TENANT = "000000";
    private static final String PRIVATE = "SELECT private_table; password=FAKE_ONLY; Bearer FAKE_TOKEN_ONLY";
    private static final String SAFE_MESSAGE = "ERP 专属物理事务未成功完成，不能确认提交结果";
    private static final String ROOT_STAGE = "PURCHASE_SCOPE_READ";

    @AfterEach
    void sixThreadProtectionsRemainClean() {
        assertAll("不主动清理来掩盖原事务的六项保护缺陷",
            () -> assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty()),
            () -> assertFalse(TransactionSynchronizationManager.isActualTransactionActive()),
            () -> assertFalse(TransactionSynchronizationManager.isSynchronizationActive()),
            () -> assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly()),
            () -> assertNull(DynamicDataSourceContextHolder.peek()),
            () -> assertNull(TransactionContext.getXID()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void callbackFailureLogsOnlySafePositionsAndKeepsRollback(boolean repeatableRead) throws SQLException {
        var fixture = new Fixture();
        var original = new SensitiveFailure();
        original.initCause(new IllegalStateException(PRIVATE));
        original.addSuppressed(new IllegalArgumentException(PRIVATE));
        original.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("java.sql.DriverManager", "getConnection", PRIVATE, 11),
            frame("com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader", "readLocked", 171),
            frame("com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService", "previewLocked", 58),
            frame("com.nexus.erp.ai.adapter.ErpCertifiedSourcePreviewService", "preview", 27),
            frame("com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor", "execute", 89)
        });
        try (var tenant = tenant(); var capture = new Capture()) {
            var outward = assertThrows(ServiceException.class, () -> {
                if (repeatableRead) fixture.executor.executeRepeatableRead(TENANT, () -> { throw original; });
                else fixture.executor.execute(TENANT, () -> { throw original; });
            });
            assertSanitized(outward);
            fixture.assertRollback();
            assertEquals(0, original.forbiddenReads.get(), "不能读取原 message/cause 或调用 toString");
            var event = singleEvent(capture, "CALLBACK_SCOPE", SensitiveFailure.class.getName());
            assertEquals("[com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader#readLocked:171, "
                + "com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService#previewLocked:58, "
                + "com.nexus.erp.ai.adapter.ErpCertifiedSourcePreviewService#preview:27]", event.getArgumentArray()[2]);
        }
    }

    @Test
    void malformedFrameNamesAndSensitiveFileNamesAreNotLogged() throws SQLException {
        var fixture = new Fixture();
        var original = new SensitiveFailure();
        original.setStackTrace(new StackTraceElement[] {
            frame("com.nexus.erp.Bad\nBearer FAKE_TOKEN_ONLY", "preview", 7),
            frame("com.nexus.erp.Valid", "preview\r\nSELECT private_table", 8),
            frame("com.nexusevil.erp.Valid", "preview", 9),
            frame("com.nexus.erp." + "A".repeat(300), "preview", 10),
            frame("com.nexus.erp.Valid", "preview", 12)
        });
        try (var tenant = tenant(); var capture = new Capture()) {
            assertSanitized(assertThrows(ServiceException.class,
                () -> fixture.executor.executeRepeatableRead(TENANT, () -> { throw original; })));
            var event = singleEvent(capture, "CALLBACK_SCOPE", SensitiveFailure.class.getName());
            assertEquals("[com.nexus.erp.Valid#preview:12]", event.getArgumentArray()[2]);
            fixture.assertRollback();
        }
    }

    @Test
    void emptyStackStillLogsTypeWithoutInventingBusinessPosition() throws SQLException {
        var fixture = new Fixture();
        var original = new SensitiveFailure();
        original.setStackTrace(new StackTraceElement[0]);
        try (var tenant = tenant(); var capture = new Capture()) {
            assertSanitized(assertThrows(ServiceException.class,
                () -> fixture.executor.executeRepeatableRead(TENANT, () -> { throw original; })));
            assertEquals("[]", singleEvent(capture, "CALLBACK_SCOPE", SensitiveFailure.class.getName()).getArgumentArray()[2]);
            fixture.assertRollback();
        }
    }

    @Test
    void ordinaryNonFatalErrorKeepsSafeFailureAndRollback() throws SQLException {
        var fixture = new Fixture();
        var original = new AssertionError(PRIVATE);
        original.initCause(new IllegalStateException(PRIVATE));
        original.addSuppressed(new IllegalArgumentException(PRIVATE));
        original.setStackTrace(new StackTraceElement[] {frame("com.nexus.erp.Valid", "preview", 15)});
        try (var tenant = tenant(); var capture = new Capture()) {
            assertSanitized(assertThrows(ServiceException.class,
                () -> fixture.executor.executeRepeatableRead(TENANT, () -> { throw original; })));
            singleEvent(capture, "CALLBACK_SCOPE", AssertionError.class.getName());
            fixture.assertRollback();
        }
    }

    @Test
    void borrowFailureIsLoggedAtTransactionScopeWithoutRunningCallback() throws SQLException {
        var fixture = new Fixture();
        when(fixture.erp.getConnection()).thenThrow(new SQLException(PRIVATE));
        var called = new AtomicBoolean();
        try (var tenant = tenant(); var capture = new Capture()) {
            assertSanitized(assertThrows(ServiceException.class, () -> fixture.executor.executeRepeatableRead(TENANT,
                () -> { called.set(true); return "不能到达"; })));
            assertFalse(called.get());
            singleEvent(capture, "TRANSACTION_SCOPE", "org.springframework.transaction.CannotCreateTransactionException");
            verify(fixture.connection, never()).commit();
            verify(fixture.connection, never()).rollback();
        }
    }

    @Test
    void commitFailureLogsTransactionScopeWithoutInventingRollbackSuccess() throws SQLException {
        var fixture = new Fixture();
        doThrow(new SQLException(PRIVATE)).when(fixture.connection).commit();
        try (var tenant = tenant(); var capture = new Capture()) {
            assertSanitized(assertThrows(ServiceException.class,
                () -> fixture.executor.executeRepeatableRead(TENANT, () -> "回调正常结束")));
            singleEvent(capture, "TRANSACTION_SCOPE", "org.springframework.transaction.TransactionSystemException");
            verify(fixture.connection).commit();
            verify(fixture.connection, never()).rollback();
            verify(fixture.connection).close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"RUNTIME", "ERROR", "FATAL"})
    void loggingFacilityFailureCannotReplaceSafeFailureOrRollback(String kind) throws SQLException {
        var fixture = new Fixture();
        var attempts = new AtomicInteger();
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var filter = new TurboFilter() {
            @Override public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
                if (!logger.getName().equals(PuRequirementErpTransactionExecutor.class.getName())) return FilterReply.NEUTRAL;
                attempts.incrementAndGet();
                assertNull(t, "日志设施不得收到原 Throwable");
                if ("FATAL".equals(kind)) throw new OutOfMemoryError("模拟日志设施失败，不是真实OOM");
                if ("ERROR".equals(kind)) throw new AssertionError("模拟日志设施失败");
                throw new IllegalStateException("模拟日志设施失败");
            }
        };
        filter.start(); context.addTurboFilter(filter);
        try (var tenant = tenant(); var capture = new Capture()) {
            assertSanitized(assertThrows(ServiceException.class,
                () -> fixture.executor.executeRepeatableRead(TENANT, () -> { throw new SensitiveFailure(); })));
            assertEquals(1, attempts.get(), "原普通失败只尝试诊断一次，不在外层重记已净化对象");
            assertTrue(capture.events.list.isEmpty());
            fixture.assertRollback();
        } finally {
            context.getTurboFilterList().remove(filter); filter.stop();
        }
    }

    @Test
    void failedPositionExtractionCannotReplaceSafeFailureOrRollback() throws SQLException {
        var fixture = new Fixture();
        var reads = new AtomicInteger();
        var original = new SensitiveFailure() {
            @Override public StackTraceElement[] getStackTrace() {
                reads.incrementAndGet(); throw new OutOfMemoryError("模拟栈提取失败，不是真实OOM");
            }
        };
        try (var tenant = tenant(); var capture = new Capture()) {
            assertSanitized(assertThrows(ServiceException.class,
                () -> fixture.executor.executeRepeatableRead(TENANT, () -> { throw original; })));
            assertEquals(1, reads.get());
            assertTrue(capture.events.list.isEmpty());
            fixture.assertRollback();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void originalFatalObjectDoesNotEnterDiagnosticBranch(boolean threadDeath) throws SQLException {
        var fixture = new Fixture();
        Error original = threadDeath ? new ThreadDeath() : new OutOfMemoryError("模拟原致命对象，不是真实OOM");
        try (var tenant = tenant(); var capture = new Capture()) {
            assertSame(original, assertThrows(Error.class,
                () -> fixture.executor.executeRepeatableRead(TENANT, () -> { throw original; })));
            assertTrue(capture.events.list.isEmpty(), "原致命分支不增加日志操作");
            fixture.assertRollback();
        }
    }

    @Test
    void provenSourceChangeKeepsItsClassificationAndDoesNotAddOrdinaryDiagnostic() throws SQLException {
        var fixture = new Fixture();
        var original = new SourceChangedException();
        original.initCause(new IllegalStateException(PRIVATE)); original.addSuppressed(new IllegalArgumentException(PRIVATE));
        try (var tenant = tenant(); var capture = new Capture()) {
            var outward = assertThrows(SourceChangedException.class, () -> fixture.executor.executeRepeatableRead(TENANT, () -> {
                new PuRequirementErpTransactionQualification(fixture.factory).requireSourceChainTransaction();
                throw original;
            }));
            assertNotSame(original, outward); assertNull(outward.getCause()); assertEquals(0, outward.getSuppressed().length);
            assertTrue(capture.events.list.isEmpty()); fixture.assertRollback();
        }
    }

    @Test
    void successWithoutOrdinaryFailureDoesNotEmitDiagnosticOrChangeCompletion() throws SQLException {
        var fixture = new Fixture();
        try (var tenant = tenant(); var capture = new Capture()) {
            assertEquals("原返回值", fixture.executor.executeRepeatableRead(TENANT, () -> "原返回值"));
            assertTrue(capture.events.list.isEmpty());
            verify(fixture.connection).commit(); verify(fixture.connection, never()).rollback(); verify(fixture.connection).close();
            verify(fixture.master, never()).getConnection();
        }
    }

    private static StackTraceElement frame(String type, String method, int line) {
        return new StackTraceElement(type, method, PRIVATE, line);
    }

    private static ILoggingEvent singleEvent(Capture capture, String stage, String type) {
        assertEquals(1, capture.events.list.size(), "缺少原失败的安全位置日志，或外层重复记录");
        var event = capture.events.list.get(0);
        assertSafeEvent(event, stage, type);
        return event;
    }

    /** 根与回调事件共用既有安全参数断言，不能因增加阶段而放松日志边界。 */
    private static void assertSafeEvent(ILoggingEvent event, String stage, String type) {
        assertNull(event.getThrowableProxy());
        assertEquals(Level.ERROR, event.getLevel());
        assertEquals(3, event.getArgumentArray().length);
        for (Object argument : event.getArgumentArray()) assertInstanceOf(String.class, argument);
        assertEquals(stage, event.getArgumentArray()[0]); assertEquals(type, event.getArgumentArray()[1]);
        assertFalse(event.getFormattedMessage().contains(PRIVATE));
        assertFalse(event.getFormattedMessage().contains("FAKE_TOKEN_ONLY"));
        assertFalse(event.getFormattedMessage().contains("private_table"));
        assertFalse(event.getFormattedMessage().contains("password="));
        assertFalse(event.getFormattedMessage().contains(TENANT));
    }

    private static void assertSanitized(ServiceException outward) {
        assertEquals(ServiceException.class, outward.getClass()); assertEquals(SAFE_MESSAGE, outward.getMessage());
        assertNull(outward.getCause()); assertEquals(0, outward.getSuppressed().length);
    }

    @Test
    void loggingReentryIsBoundedAndDoesNotPoisonLaterDiagnostics() throws SQLException {
        var fixture = new Fixture();
        when(fixture.erp.getConnection()).thenThrow(new SQLException(PRIVATE));
        var entries = new AtomicInteger();
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var filter = new TurboFilter() {
            @Override public FilterReply decide(Marker marker, Logger logger, Level level,
                String format, Object[] parameters, Throwable throwable) {
                if (logger.getName().equals(PuRequirementErpTransactionExecutor.class.getName())) {
                    /** 旧实现最多放行两层再入来形成有限RED，不用真实栈溢出拖垮测试JVM。 */
                    if (entries.incrementAndGet() <= 2) {
                        assertSanitized(assertThrows(ServiceException.class,
                            () -> fixture.executor.execute(TENANT, () -> { throw new AssertionError("不应进入回调"); })));
                    }
                }
                return FilterReply.NEUTRAL;
            }
        };
        try (var tenant = tenant(); var capture = new Capture()) {
            filter.start(); context.addTurboFilter(filter);
            try {
                assertSanitized(assertThrows(ServiceException.class,
                    () -> fixture.executor.execute(TENANT, () -> { throw new AssertionError("不应进入回调"); })));
                assertEquals(1, entries.get(), "嵌套失败不能再次进入日志设施");
                verify(fixture.erp, times(2)).getConnection();
                assertEquals(1, capture.events.list.size(), "只保留外层诊断，不递归产生诊断");
            } finally {
                context.getTurboFilterList().remove(filter); filter.stop();
            }
            assertSanitized(assertThrows(ServiceException.class,
                () -> fixture.executor.execute(TENANT, () -> { throw new AssertionError("不应进入回调"); })));
            assertEquals(2, capture.events.list.size(), "再入哨兵必须移除，不能污染后续正常诊断");
            verify(fixture.erp, times(3)).getConnection();
        }
    }


    /** 原异常经真实 Guard 净化前须留下根阶段；回调仍按原规则净化并回滚，不重记事务阶段。 */
    @Test
    void rootReadLogsOriginalTypeBeforeCallbackSanitizationAndKeepsRollback() throws SQLException {
        var fixture = new Fixture();
        var original = new SensitiveFailure();
        original.initCause(new IllegalStateException(PRIVATE));
        original.addSuppressed(new IllegalArgumentException(PRIVATE));
        original.setStackTrace(new StackTraceElement[] {
            frame("com.nexus.erp.ai.adapter.SyntheticRootSourceReader", "readLocked", 47)
        });
        var guard = fixture.rootReaderFailing(original);
        try (var tenant = tenant(); var capture = new Capture()) {
            assertSanitized(assertThrows(ServiceException.class, () -> fixture.executor.executeRepeatableRead(TENANT, () -> {
                readRoot(guard);
                return null;
            })));
            fixture.assertRollback();
            assertEquals(0, original.forbiddenReads.get());
            assertEquals(2, capture.events.list.size(), "必须保留原根阶段及既有回调阶段，不能只记录替代对象或重记事务阶段");
            assertSafeEvent(capture.events.list.get(0), ROOT_STAGE, original.getClass().getName());
            assertEquals("[com.nexus.erp.ai.adapter.SyntheticRootSourceReader#readLocked:47]",
                capture.events.list.get(0).getArgumentArray()[2]);
            assertSafeEvent(capture.events.list.get(1), "CALLBACK_SCOPE", ServiceException.class.getName());
        }
    }

    /** 提取自身故障必须确实被触发，不能因跳过诊断而假通过；之后原回调回滚路径不变。 */
    @ParameterizedTest
    @ValueSource(strings = {"RUNTIME", "ERROR", "FATAL"})
    void rootPositionExtractionFailureCannotReplaceSafeFailureOrRollback(String kind) throws SQLException {
        var fixture = new Fixture();
        var attempts = new AtomicInteger();
        var original = new SensitiveFailure() {
            @Override public StackTraceElement[] getStackTrace() {
                attempts.incrementAndGet();
                throw syntheticFacilityFailure(kind);
            }
        };
        var guard = fixture.rootReaderFailing(original);
        try (var tenant = tenant(); var capture = new Capture()) {
            assertSanitized(assertThrows(ServiceException.class, () -> fixture.executor.executeRepeatableRead(TENANT, () -> {
                readRoot(guard);
                return null;
            })));
            fixture.assertRollback();
            assertEquals(0, original.forbiddenReads.get());
            assertEquals(1, attempts.get(), "必须在根净化前实际尝试一次提取，不得因没调用诊断而假通过");
            singleEvent(capture, "CALLBACK_SCOPE", ServiceException.class.getName());
        }
        assertLaterRootDiagnosticAndRollback();
    }

    /** 只使根阶段 appender 失败，既有回调日志仍可见；设施 fatal 不能变成业务 fatal。 */
    @ParameterizedTest
    @ValueSource(strings = {"RUNTIME", "ERROR", "FATAL"})
    void rootAppenderFailureCannotReplaceSafeFailureRollbackOrLaterDiagnostic(String kind) throws SQLException {
        var fixture = new Fixture();
        var original = new SensitiveFailure();
        original.setStackTrace(new StackTraceElement[0]);
        var guard = fixture.rootReaderFailing(original);
        var broken = new RootFailingAppender(kind);
        try (var tenant = tenant(); var capture = new Capture(broken)) {
            assertSanitized(assertThrows(ServiceException.class, () -> fixture.executor.executeRepeatableRead(TENANT, () -> {
                readRoot(guard);
                return null;
            })));
            fixture.assertRollback();
            assertEquals(0, original.forbiddenReads.get());
            assertEquals(1, broken.attempts.get(), "必须实际触发根阶段 appender，不能只触发回调日志而假通过");
            assertNotNull(broken.attemptedRoot);
            assertSafeEvent(broken.attemptedRoot, ROOT_STAGE, original.getClass().getName());
            singleEvent(capture, "CALLBACK_SCOPE", ServiceException.class.getName());
        }
        assertLaterRootDiagnosticAndRollback();
    }

    /** 根层原样透传后，来源 RR 的既有可信变化分类、重新净化及回滚规则仍生效。 */
    @Test
    void sourceChangeFromRootReadKeepsExistingRrClassificationWithoutDiagnostic() throws SQLException {
        var fixture = new Fixture();
        var original = new SourceChangedException();
        original.initCause(new IllegalStateException(PRIVATE));
        original.addSuppressed(new IllegalArgumentException(PRIVATE));
        var guard = fixture.rootReaderFailing(original);
        try (var tenant = tenant(); var capture = new Capture()) {
            var outward = assertThrows(SourceChangedException.class, () -> fixture.executor.executeRepeatableRead(TENANT, () -> {
                readRoot(guard);
                return null;
            }));
            assertNotSame(original, outward, "原样透传契约在 Guard；事务入口仍保留原先的安全重新构造规则");
            assertEquals("采购冻结来源已变化，须重新分析", outward.getMessage());
            assertNull(outward.getCause());
            assertEquals(0, outward.getSuppressed().length);
            assertTrue(capture.events.list.isEmpty());
            fixture.assertRollback();
        }
    }

    /** 原 fatal 经过 Guard 与真实事务回调后仍按原对象传播，并先完成已有回滚清理。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void originalFatalFromRootReadKeepsIdentityRollbackAndNoDiagnostic(boolean threadDeath) throws SQLException {
        var fixture = new Fixture();
        Error original = threadDeath ? new ThreadDeath() : new OutOfMemoryError("离线模拟致命对象，不耗尽内存");
        var guard = fixture.rootReaderFailing(original);
        try (var tenant = tenant(); var capture = new Capture()) {
            assertSame(original, assertThrows(Error.class, () -> fixture.executor.executeRepeatableRead(TENANT, () -> {
                readRoot(guard);
                return null;
            })));
            assertTrue(capture.events.list.isEmpty(), "原业务 fatal 不得被根阶段诊断读取");
            fixture.assertRollback();
        }
    }

    /** 故障设施移除后重新走根入口，证明诊断防再入标记未污染后续事务。 */
    private static void assertLaterRootDiagnosticAndRollback() throws SQLException {
        var fixture = new Fixture();
        var later = new SensitiveFailure();
        later.setStackTrace(new StackTraceElement[0]);
        var guard = fixture.rootReaderFailing(later);
        try (var tenant = tenant(); var capture = new Capture()) {
            assertSanitized(assertThrows(ServiceException.class, () -> fixture.executor.executeRepeatableRead(TENANT, () -> {
                readRoot(guard);
                return null;
            })));
            fixture.assertRollback();
            assertEquals(2, capture.events.list.size(), "后续根诊断不能因残留诊断标记而被跳过");
            assertSafeEvent(capture.events.list.get(0), ROOT_STAGE, later.getClass().getName());
            assertEquals("[]", capture.events.list.get(0).getArgumentArray()[2]);
            assertSafeEvent(capture.events.list.get(1), "CALLBACK_SCOPE", ServiceException.class.getName());
            assertEquals(0, later.forbiddenReads.get());
        }
    }

    /** 所有身份均为合成夹具，仅调用既有冻结根入口，不提供外部授权或业务写入口。 */
    private static void readRoot(ErpDecisionPurchaseScopeGuard guard) {
        guard.requireRootSourcesLocked(TENANT, new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, null),
            139L, Set.of(21L), LocalDate.of(2026, 10, 6));
    }

    /** 模拟提取或日志设施自身的普通、Error 与 fatal 故障，不耗尽内存或操作服务。 */
    private static RuntimeException syntheticFacilityFailure(String kind) {
        if ("ERROR".equals(kind)) throw new AssertionError("合成诊断设施 Error");
        if ("FATAL".equals(kind)) throw new OutOfMemoryError("合成诊断设施 fatal，不耗尽内存");
        return new IllegalStateException("合成诊断设施普通故障");
    }

    /** 只对根阶段抛错，避免用既有回调诊断冒充新增早期诊断已经触发。 */
    private static final class RootFailingAppender extends ListAppender<ILoggingEvent> {
        final String kind;
        final AtomicInteger attempts = new AtomicInteger();
        ILoggingEvent attemptedRoot;
        RootFailingAppender(String kind) { this.kind = kind; }
        @Override protected void append(ILoggingEvent event) {
            Object[] arguments = event.getArgumentArray();
            if (arguments != null && arguments.length > 0 && ROOT_STAGE.equals(arguments[0])) {
                attemptedRoot = event;
                attempts.incrementAndGet();
                throw syntheticFacilityFailure(kind);
            }
            super.append(event);
        }
    }

    private static MockedStatic<TenantHelper> tenant() {
        var tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(true); tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
        return tenant;
    }

    private static class SensitiveFailure extends RuntimeException {
        final AtomicInteger forbiddenReads = new AtomicInteger();
        SensitiveFailure() { super(PRIVATE); }
        @Override public String getMessage() { forbiddenReads.incrementAndGet(); throw new AssertionError("禁止读取原message"); }
        @Override public String getLocalizedMessage() { forbiddenReads.incrementAndGet(); throw new AssertionError("禁止读取原message"); }
        @Override public synchronized Throwable getCause() { forbiddenReads.incrementAndGet(); throw new AssertionError("禁止读取原cause"); }
        @Override public String toString() { forbiddenReads.incrementAndGet(); throw new AssertionError("禁止格式化原Throwable"); }
    }

    private static final class Capture implements AutoCloseable {
        final Logger logger = (Logger) LoggerFactory.getLogger(PuRequirementErpTransactionExecutor.class);
        final ListAppender<ILoggingEvent> events;
        final Level oldLevel = logger.getLevel();
        final boolean oldAdditive = logger.isAdditive();
        Capture() { this(new ListAppender<>()); }
        Capture(ListAppender<ILoggingEvent> events) {
            this.events = events;
            events.setContext(logger.getLoggerContext());
            events.start();
            logger.setLevel(Level.ERROR);
            logger.setAdditive(false);
            logger.addAppender(events);
        }
        @Override public void close() { logger.detachAppender(events); logger.setLevel(oldLevel); logger.setAdditive(oldAdditive); events.stop(); }
    }

    /** 只模拟物理连接状态，不提供真实 URL、Driver、数据库写入或外部服务。 */
    private static final class Fixture {
        final HikariDataSource erp = mock(HikariDataSource.class);
        final HikariDataSource master = mock(HikariDataSource.class);
        final Connection connection = mock(Connection.class);
        final DefaultSqlSessionFactory factory;
        final PuRequirementErpTransactionExecutor executor;
        Fixture() throws SQLException {
            var autoCommit = new AtomicBoolean(true);
            var closed = new AtomicBoolean();
            var readOnly = new AtomicBoolean();
            var isolation = new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED);
            when(erp.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenAnswer(call -> autoCommit.get());
            when(connection.isClosed()).thenAnswer(call -> closed.get());
            when(connection.isReadOnly()).thenAnswer(call -> readOnly.get());
            when(connection.getTransactionIsolation()).thenAnswer(call -> isolation.get());
            doAnswer(call -> { autoCommit.set(call.getArgument(0)); return null; }).when(connection).setAutoCommit(anyBoolean());
            doAnswer(call -> { readOnly.set(call.getArgument(0)); return null; }).when(connection).setReadOnly(anyBoolean());
            doAnswer(call -> { isolation.set(call.getArgument(0)); return null; }).when(connection).setTransactionIsolation(anyInt());
            doAnswer(call -> { closed.set(true); return null; }).when(connection).close();
            var routing = new DynamicRoutingDataSource(List.of());
            routing.setPrimary("master"); routing.setStrict(true);
            routing.addDataSource("master", master); routing.addDataSource("erp", erp);
            var config = new Configuration(new Environment("offline", new SpringManagedTransactionFactory(), routing));
            config.addMapper(PuRequirementCompetitionPoolMapper.class); config.addMapper(PuRequirementMaterialGuardMapper.class);
            factory = new DefaultSqlSessionFactory(config); executor = new PuRequirementErpTransactionExecutor(factory);
        }
        /** 真实 Guard 及事务归属检查；来源与锁仅为合成替身，不模拟数据库认证结果。 */
        ErpDecisionPurchaseScopeGuard rootReaderFailing(Throwable original) {
            var mapper = mock(ErpDecisionPurchaseScopeMapper.class);
            when(mapper.pinScopeDefinition()).thenReturn(List.of());
            when(mapper.scopeTableEngine()).thenReturn("InnoDB");
            doAnswer(call -> { throw original; }).when(mapper).findRootWorkOrder(anyString(), anyLong());
            return new ErpDecisionPurchaseScopeGuard(mapper, new PuRequirementErpTransactionQualification(factory),
                mock(PuRequirementMaterialLockService.class));
        }

        void assertRollback() throws SQLException {
            verify(erp).getConnection(); verify(master, never()).getConnection();
            verify(connection).rollback(); verify(connection, never()).commit(); verify(connection).close();
            verify(connection, never()).getCatalog();
        }
    }
}