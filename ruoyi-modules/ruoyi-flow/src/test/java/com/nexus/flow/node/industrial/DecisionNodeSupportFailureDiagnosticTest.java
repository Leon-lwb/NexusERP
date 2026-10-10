package com.nexus.flow.node.industrial;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.decision.DecisionActionExecutionService.InitialExecutionCheckUnavailableException;
import com.nexus.ai.service.decision.DecisionActionExecutionService.PurchaseExecutionPendingException;
import com.nexus.ai.service.decision.DecisionPersistenceService;
import com.nexus.ai.service.sse.AiDecisionSseMessageHelper;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.exception.DecisionEvidenceException;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.state.FlowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 约束工业节点净化前的有限内部诊断，不把诊断缺口修复解释为业务根因修复。
 * 所有身份和敏感文本均为合成夹具；不读取业务日志、不连接数据库、不发送真实 SSE。
 * 通过真实 Logback appender 验证日志边界，并保留原公开拒绝及采购执行断点行为。
 */
class DecisionNodeSupportFailureDiagnosticTest {
    private static final long RUN_ID = 900000000000021L;
    private static final long USER_ID = 900000000000007L;
    private static final String TENANT = "tenant-fixture-private";
    private static final String NODE_ID = "node-fixture-private";
    private static final String NODE_NAME = "node-name-fixture-private";
    private static final String SAFE_CODE = "DECISION_NODE_FAILED";
    private static final String SAFE_MESSAGE = "工业决策节点执行失败";
    private static final String PRIVATE = "FAKE_PRIVATE_ONLY jdbc:mysql://invalid/private_table "
        + "Bearer FAKE_TOKEN_ONLY password=FAKE_PASSWORD_ONLY";

    private final DecisionPersistenceService store = mock(DecisionPersistenceService.class);
    private final AiDecisionSseMessageHelper events = mock(AiDecisionSseMessageHelper.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final DecisionNodeSupport support = spy(new DecisionNodeSupport(
        store, mock(IndustrialDecisionDataPort.class), mapper, events));
    private final FlowExecutionContext ctx = new FlowExecutionContext();
    private MockedStatic<SpringUtil> spring;

    @BeforeEach
    void setup() {
        ctx.setTenantId(TENANT);
        ctx.setNodeId(NODE_ID);
        ctx.setNodeName(NODE_NAME);
        ctx.setState(new FlowState(mapper.createObjectNode().put("runId", Long.toString(RUN_ID))));
        doReturn(new DecisionActor(TENANT, USER_ID, "trusted", Set.of("ai:decision:run")))
            .when(support).actor(ctx);
        spring = mockStatic(SpringUtil.class);
        spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("false");
    }

    @AfterEach
    void close() {
        spring.close();
    }

    @Test
    void ordinaryFailureLogsOnlyOriginalTypeAndThreeApplicationFrames() {
        var original = new SensitiveFailure();
        original.initCause(new IllegalStateException(PRIVATE));
        original.addSuppressed(new IllegalArgumentException(PRIVATE));
        original.setStackTrace(new StackTraceElement[] {
            frame("java.sql.Statement", "execute", 1),
            frame("com.nexus.erp.ai.adapter.ErpDecisionPurchaseScopeGuard", "readSafely", 11),
            frame("org.mybatis.spring.SqlSessionTemplate", "selectList", 2),
            frame("com.nexus.ai.service.decision.DecisionPurchaseProposalService", "freeze", 22),
            frame("com.nexus.flow.node.industrial.DecisionNodeSupport", "output", 33),
            frame("com.nexus.flow.engine.FlowEngineImpl", "runNodeWithRetry", 44)
        });
        try (var capture = new Capture()) {
            assertPublicFailure(runFailure(original), SAFE_CODE, SAFE_MESSAGE, true);
            assertDiagnostic(capture, original.getClass().getName(),
                "[com.nexus.erp.ai.adapter.ErpDecisionPurchaseScopeGuard#readSafely:11, "
                    + "com.nexus.ai.service.decision.DecisionPurchaseProposalService#freeze:22, "
                    + "com.nexus.flow.node.industrial.DecisionNodeSupport#output:33]");
            assertEquals(0, original.forbiddenReads.get(), "不得读取原异常内容或格式化原异常");
        }
    }

    @Test
    void positionScanStopsAtSixtyFourFrames() {
        var trace = new StackTraceElement[66];
        Arrays.fill(trace, frame("java.sql.Statement", "execute", 1));
        trace[63] = frame("com.nexus.flow.node.industrial.DecisionNodeSupport", "output", 64);
        trace[64] = frame("com.nexus.erp.ai.adapter.OutsideDiagnosticScan", "readLocked", 65);
        trace[65] = frame("com.nexus.erp.ai.adapter.OutsideDiagnosticScan", "previewLocked", 66);
        var original = new SensitiveFailure();
        original.setStackTrace(trace);
        try (var capture = new Capture()) {
            assertPublicFailure(runFailure(original), SAFE_CODE, SAFE_MESSAGE, true);
            assertDiagnostic(capture, original.getClass().getName(),
                "[com.nexus.flow.node.industrial.DecisionNodeSupport#output:64]");
        }
    }

    @Test
    void malformedOverlongAndLookalikeNamesCannotEnterDiagnostic() {
        var original = new SensitiveFailure();
        original.setStackTrace(new StackTraceElement[] {
            frame("com.nexusx.erp.NotApplication", "readLocked", 1),
            frame("com.nexus.erp." + PRIVATE, "readLocked", 2),
            frame("com.nexus.erp.ValidOwner", PRIVATE, 3),
            frame("com.nexus." + "x".repeat(241), "readLocked", 4),
            frame("com.nexus.erp.ValidOwner", "x".repeat(81), 5),
            frame("com.nexus.flow.node.industrial.DecisionNodeSupport", "output", 6)
        });
        try (var capture = new Capture()) {
            assertPublicFailure(runFailure(original), SAFE_CODE, SAFE_MESSAGE, true);
            assertDiagnostic(capture, original.getClass().getName(),
                "[com.nexus.flow.node.industrial.DecisionNodeSupport#output:6]");
        }
    }

    @Test
    void emptyStackLogsTypeWithoutInventingApplicationPositions() {
        var original = new SensitiveFailure();
        original.setStackTrace(new StackTraceElement[0]);
        try (var capture = new Capture()) {
            assertPublicFailure(runFailure(original), SAFE_CODE, SAFE_MESSAGE, true);
            assertDiagnostic(capture, original.getClass().getName(), "[]");
        }
    }

    @Test
    void failedPositionExtractionCannotInterruptFailurePersistenceOrSse() {
        var original = new UnreadableStackFailure();
        try (var capture = new Capture()) {
            assertPublicFailure(runFailure(original), SAFE_CODE, SAFE_MESSAGE, true);
            assertEquals(1, original.stackReads.get(), "必须实际尝试有限位置提取，不能跳过诊断");
            assertEquals(0, original.forbiddenReads.get(), "提取失败也不得读取原异常内容");
            assertTrue(capture.events.list.isEmpty(), "提取未知不得打印原 Throwable 兜底");
        }
    }

    @Test
    void failedAppenderCannotInterruptFailureOrPoisonLaterDiagnostic() {
        var broken = new FailingAppender();
        try (var capture = new Capture(broken)) {
            assertPublicFailure(runFailure(new SensitiveFailure()), SAFE_CODE, SAFE_MESSAGE, true);
            assertEquals(1, broken.attempts.get(), "必须实际触发 appender 失败");
            assertTrue(capture.events.list.isEmpty());
        }
        clearInvocations(store, events);
        var later = new SensitiveFailure();
        later.setStackTrace(new StackTraceElement[0]);
        try (var capture = new Capture()) {
            assertPublicFailure(runFailure(later), SAFE_CODE, SAFE_MESSAGE, true);
            assertDiagnostic(capture, later.getClass().getName(), "[]");
        }
    }

    @Test
    void successfulOutputEmitsNoFailureDiagnostic() {
        try (var capture = new Capture()) {
            var output = support.output(ctx, () -> Map.of("ready", true));
            assertTrue(output.path("ready").asBoolean());
            assertTrue(capture.events.list.isEmpty());
            verify(store, never()).fail(anyLong(), anyString(), anyString());
            verify(events, never()).sendError(anyLong(), anyLong(), anyString(), anyString());
            verify(events).sendNodeEnd(eq(USER_ID), eq(RUN_ID), eq(0L), eq(NODE_ID), eq(NODE_NAME),
                eq("SUCCEEDED"), anyLong(), eq("工业节点计算完成"));
        }
    }

    @Test
    void exactEvidenceReasonKeepsItsPublicCodePersistenceAndSse() {
        var reason = DecisionEvidenceException.Reason.ROUTING_EVIDENCE_MISSING;
        var original = new DecisionEvidenceException(reason);
        original.setStackTrace(new StackTraceElement[0]);
        try (var capture = new Capture()) {
            assertPublicFailure(runFailure(original), reason.code(), reason.publicMessage(), true);
            assertDiagnostic(capture, original.getClass().getName(), "[]");
        }
    }

    @Test
    void typedCauseIsNotReadOrPromotedIntoTrustedEvidenceReason() {
        var original = new SensitiveFailure();
        original.initCause(new DecisionEvidenceException(
            DecisionEvidenceException.Reason.ROUTING_EVIDENCE_MISSING));
        original.setStackTrace(new StackTraceElement[0]);
        try (var capture = new Capture()) {
            assertPublicFailure(runFailure(original), SAFE_CODE, SAFE_MESSAGE, true);
            assertDiagnostic(capture, original.getClass().getName(), "[]");
            assertEquals(0, original.forbiddenReads.get(), "不能通过 cause 推断领域错误码");
        }
    }

    @Test
    void pendingPurchaseExecutionKeepsExecutingCheckpoint() {
        prepareExecutionCheckpoint("EXECUTING");
        when(store.listActions(RUN_ID)).thenReturn(null);
        var original = new PurchaseExecutionPendingException();
        original.setStackTrace(new StackTraceElement[0]);
        try (var capture = new Capture()) {
            assertPublicFailure(runFailure(original), SAFE_CODE, SAFE_MESSAGE, false);
            verify(store).requireRun(RUN_ID, TENANT);
            verify(store).listActions(RUN_ID);
            assertDiagnostic(capture, original.getClass().getName(), "[]");
        }
    }

    @Test
    void initialExecutionCheckKeepsApprovedCheckpoint() {
        prepareExecutionCheckpoint("APPROVED");
        var original = new InitialExecutionCheckUnavailableException();
        original.setStackTrace(new StackTraceElement[0]);
        try (var capture = new Capture()) {
            assertPublicFailure(runFailure(original), SAFE_CODE, SAFE_MESSAGE, false);
            verify(store).requireRun(RUN_ID, TENANT);
            verify(store, never()).listActions(anyLong());
            assertDiagnostic(capture, original.getClass().getName(), "[]");
        }
    }

    @Test
    void originalFatalErrorStaysOutsideRuntimeDiagnosticBranch() {
        var original = new OutOfMemoryError("合成致命错误夹具，不分配额外内存");
        try (var capture = new Capture()) {
            assertSame(original, assertThrows(OutOfMemoryError.class,
                () -> support.output(ctx, () -> { throw original; })));
            assertTrue(capture.events.list.isEmpty());
            verify(store, never()).fail(anyLong(), anyString(), anyString());
            verify(events, never()).sendError(anyLong(), anyLong(), anyString(), anyString());
        }
    }

    /** 只调用真实节点包装器，业务依赖保持内存 mock。 */
    private ServiceException runFailure(RuntimeException original) {
        return assertThrows(ServiceException.class,
            () -> support.output(ctx, () -> { throw original; }));
    }

    /** 诊断不能改变既有公开消息、持久化拒绝、SSE 或执行断点保护。 */
    private void assertPublicFailure(ServiceException outward, String code, String message, boolean persistFailure) {
        assertEquals(ServiceException.class, outward.getClass());
        assertEquals(message, outward.getMessage());
        assertNull(outward.getCause());
        assertEquals(0, outward.getSuppressed().length);
        if (persistFailure) {
            verify(store).fail(RUN_ID, code, message);
        } else {
            verify(store, never()).fail(anyLong(), anyString(), anyString());
        }
        verify(events).sendError(USER_ID, RUN_ID, code, message);
        verify(events).sendNodeEnd(eq(USER_ID), eq(RUN_ID), eq(0L), eq(NODE_ID), eq(NODE_NAME),
            eq("FAILED"), anyLong(), eq(message));
    }

    /** 有限诊断只允许两个安全字符串参数，不允许传原 Throwable 或任何业务身份。 */
    private static void assertDiagnostic(Capture capture, String type, String positions) {
        assertEquals(1, capture.events.list.size(), "缺少有限诊断，或同一次失败被重复诊断");
        var event = capture.events.list.get(0);
        assertNull(event.getThrowableProxy(), "logger 不得接收 Throwable");
        var arguments = event.getArgumentArray();
        assertNotNull(arguments);
        assertEquals(2, arguments.length, "只记录原类名与限定帧，不追加业务身份或异常内容");
        for (Object argument : arguments) assertInstanceOf(String.class, argument);
        assertEquals(type, arguments[0]);
        assertEquals(positions, arguments[1]);
        String formatted = event.getFormattedMessage();
        for (String forbidden : new String[] {
            PRIVATE, "FAKE_PRIVATE_ONLY", "FAKE_TOKEN_ONLY", "private_table", "password=", "jdbc:mysql://",
            TENANT, NODE_ID, NODE_NAME, Long.toString(RUN_ID), Long.toString(USER_ID)
        }) assertFalse(formatted.contains(forbidden), "安全位置日志包含禁止输出的夹具内容");
    }

    private void prepareExecutionCheckpoint(String status) {
        ctx.setNodeType("industrial.executeApprovedActions");
        var run = new AiDecisionRun();
        run.setDecisionContractVersion(2);
        run.setStatus(status);
        when(store.requireRun(RUN_ID, TENANT)).thenReturn(run);
    }

    /** 文件名、模块及类加载器同样携带合成敏感文本，不能借 StackTraceElement.toString 泄漏。 */
    private static StackTraceElement frame(String owner, String method, int line) {
        return new StackTraceElement(PRIVATE, PRIVATE, PRIVATE, owner, method, PRIVATE, line);
    }

    /** 原异常的内容访问即失败，证明诊断只使用类名及有限帧。 */
    private static class SensitiveFailure extends RuntimeException {
        final AtomicInteger forbiddenReads = new AtomicInteger();
        SensitiveFailure() { super(PRIVATE); }
        @Override public String getMessage() { forbiddenReads.incrementAndGet(); throw new AssertionError("禁止读取原 message"); }
        @Override public String getLocalizedMessage() { forbiddenReads.incrementAndGet(); throw new AssertionError("禁止读取原 localizedMessage"); }
        @Override public synchronized Throwable getCause() { forbiddenReads.incrementAndGet(); throw new AssertionError("禁止读取原 cause"); }
        @Override public String toString() { forbiddenReads.incrementAndGet(); throw new AssertionError("禁止格式化原 Throwable"); }
    }

    /** 提取设施故障不能替换原公开拒绝，也不能退回打印完整异常。 */
    private static final class UnreadableStackFailure extends SensitiveFailure {
        final AtomicInteger stackReads = new AtomicInteger();
        @Override public StackTraceElement[] getStackTrace() {
            stackReads.incrementAndGet();
            throw new AssertionError("合成堆栈提取故障");
        }
    }

    /** 用 Error 穿过 Logback 自身的普通 Exception 防护，检验诊断的尽力而为边界。 */
    private static final class FailingAppender extends ListAppender<ILoggingEvent> {
        final AtomicInteger attempts = new AtomicInteger();
        @Override protected void append(ILoggingEvent event) {
            attempts.incrementAndGet();
            throw new AssertionError("合成 appender 故障");
        }
    }

    /** 仅捕获当前类日志且阻止向外部 appender 传播，结束后恢复原 logger 配置。 */
    private static final class Capture implements AutoCloseable {
        final Logger logger = (Logger) LoggerFactory.getLogger(DecisionNodeSupport.class);
        final ListAppender<ILoggingEvent> events;
        final Level oldLevel = logger.getLevel();
        final boolean oldAdditive = logger.isAdditive();

        Capture() { this(new ListAppender<>()); }
        Capture(ListAppender<ILoggingEvent> events) {
            this.events = events;
            events.setContext(logger.getLoggerContext());
            events.start();
            logger.setLevel(Level.TRACE);
            logger.setAdditive(false);
            logger.addAppender(events);
        }
        @Override public void close() {
            logger.detachAppender(events);
            logger.setLevel(oldLevel);
            logger.setAdditive(oldAdditive);
            events.stop();
        }
    }
}
