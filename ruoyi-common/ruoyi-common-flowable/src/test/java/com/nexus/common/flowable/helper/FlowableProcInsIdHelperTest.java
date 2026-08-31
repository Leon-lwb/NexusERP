package com.nexus.common.flowable.helper;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * FlowableProcInsIdHelper 三级降级查询单元测试。
 * <p>覆盖：业务桥接表命中 → RuntimeService 命中 → HistoryService 命中 → 未找到 → 桥接表异常降级。</p>
 */
@ExtendWith(MockitoExtension.class)
class FlowableProcInsIdHelperTest {

    @Mock
    private RuntimeService runtimeService;
    @Mock
    private HistoryService historyService;
    @InjectMocks
    private FlowableProcInsIdHelper helper;

    // ==================== 第一级：业务桥接表 ====================

    @Test
    void resolveReturnsFromBusinessLookupWhenFound() {
        Supplier<String> lookup = () -> "proc-001";

        String result = helper.resolve("BIZ-001", lookup);

        assertEquals("proc-001", result);
        // 不应查询 RuntimeService 或 HistoryService
        verifyNoInteractions(runtimeService);
        verifyNoInteractions(historyService);
    }

    @Test
    void resolveFallsThroughToRuntimeWhenBusinessLookupReturnsNull() {
        Supplier<String> lookup = () -> null;

        ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceBusinessKey("BIZ-001")).thenReturn(runtimeQuery);
        when(runtimeQuery.singleResult()).thenReturn(null);

        HistoricProcessInstanceQuery histQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQuery);
        when(histQuery.processInstanceBusinessKey("BIZ-001")).thenReturn(histQuery);
        when(histQuery.singleResult()).thenReturn(null);

        String result = helper.resolve("BIZ-001", lookup);

        assertNull(result);
        verify(runtimeService).createProcessInstanceQuery();
        verify(historyService).createHistoricProcessInstanceQuery();
    }

    @Test
    void resolveFallsThroughToRuntimeWhenBusinessLookupReturnsEmpty() {
        Supplier<String> lookup = () -> "";

        ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceBusinessKey("BIZ-002")).thenReturn(runtimeQuery);
        when(runtimeQuery.singleResult()).thenReturn(null);

        HistoricProcessInstanceQuery histQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQuery);
        when(histQuery.processInstanceBusinessKey("BIZ-002")).thenReturn(histQuery);
        when(histQuery.singleResult()).thenReturn(null);

        String result = helper.resolve("BIZ-002", lookup);

        assertNull(result);
    }

    // ==================== 第二级：RuntimeService ====================

    @Test
    void resolveReturnsFromRuntimeWhenFound() {
        Supplier<String> lookup = () -> null;
        ProcessInstance runtimePi = mock(ProcessInstance.class);
        when(runtimePi.getId()).thenReturn("runtime-proc-123");

        ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceBusinessKey("BIZ-003")).thenReturn(runtimeQuery);
        when(runtimeQuery.singleResult()).thenReturn(runtimePi);

        String result = helper.resolve("BIZ-003", lookup);

        assertEquals("runtime-proc-123", result);
        // 不应查询 HistoryService
        verifyNoInteractions(historyService);
    }

    // ==================== 第三级：HistoryService ====================

    @Test
    void resolveReturnsFromHistoryWhenRuntimeNotFound() {
        Supplier<String> lookup = () -> null;

        ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceBusinessKey("BIZ-004")).thenReturn(runtimeQuery);
        when(runtimeQuery.singleResult()).thenReturn(null);

        HistoricProcessInstance histPi = mock(HistoricProcessInstance.class);
        when(histPi.getId()).thenReturn("hist-proc-456");

        HistoricProcessInstanceQuery histQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQuery);
        when(histQuery.processInstanceBusinessKey("BIZ-004")).thenReturn(histQuery);
        when(histQuery.singleResult()).thenReturn(histPi);

        String result = helper.resolve("BIZ-004", lookup);

        assertEquals("hist-proc-456", result);
    }

    @Test
    void resolveReturnsNullWhenNotFoundInAnyLevel() {
        Supplier<String> lookup = () -> null;

        ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceBusinessKey(anyString())).thenReturn(runtimeQuery);
        when(runtimeQuery.singleResult()).thenReturn(null);

        HistoricProcessInstanceQuery histQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQuery);
        when(histQuery.processInstanceBusinessKey(anyString())).thenReturn(histQuery);
        when(histQuery.singleResult()).thenReturn(null);

        String result = helper.resolve("BIZ-999", lookup);

        assertNull(result);
    }

    // ==================== 异常降级 ====================

    @Test
    void resolveFallsThroughWhenBusinessLookupThrowsException() {
        Supplier<String> lookup = () -> { throw new RuntimeException("DB connection failed"); };

        ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceBusinessKey("BIZ-ERR")).thenReturn(runtimeQuery);
        when(runtimeQuery.singleResult()).thenReturn(null);

        HistoricProcessInstanceQuery histQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQuery);
        when(histQuery.processInstanceBusinessKey("BIZ-ERR")).thenReturn(histQuery);
        when(histQuery.singleResult()).thenReturn(null);

        String result = helper.resolve("BIZ-ERR", lookup);

        // 异常被捕获，降级到 RuntimeService → HistoryService → null
        assertNull(result);
        verify(runtimeService).createProcessInstanceQuery();
        verify(historyService).createHistoricProcessInstanceQuery();
    }

    // ==================== 边界 ====================

    @Test
    void resolveWithNullBusinessKey() {
        Supplier<String> lookup = () -> null;

        ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceBusinessKey(null)).thenReturn(runtimeQuery);
        when(runtimeQuery.singleResult()).thenReturn(null);

        HistoricProcessInstanceQuery histQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQuery);
        when(histQuery.processInstanceBusinessKey(null)).thenReturn(histQuery);
        when(histQuery.singleResult()).thenReturn(null);

        String result = helper.resolve(null, lookup);

        assertNull(result);
    }
}