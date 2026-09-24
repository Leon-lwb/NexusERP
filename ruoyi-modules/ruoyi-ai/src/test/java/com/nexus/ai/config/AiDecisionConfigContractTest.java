package com.nexus.ai.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.extra.spring.SpringUtil;
import com.nexus.ai.service.config.impl.AiConfigServiceImpl;
import com.nexus.ai.service.query.ErpTableSchemaManager;
import com.nexus.ai.service.rag.VectorStoreService;
import com.nexus.common.ai.domain.bo.AiConfigBo;
import com.nexus.common.ai.domain.entity.AiConfig;
import com.nexus.common.ai.mapper.AiConfigMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.json.utils.JsonUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("local")
@Tag("dev")
@Tag("prod")
class AiDecisionConfigContractTest {

    private final AiConfigMapper mapper = mock(AiConfigMapper.class);
    private final AiProperties properties = new AiProperties();
    private final AiConfigServiceImpl service = new AiConfigServiceImpl(
        mapper, properties, mock(VectorStoreService.class), mock(ErpTableSchemaManager.class));

    @BeforeAll
    static void initializeJsonUtilsBeforeAnyTestCanPoisonItsStaticInitializer() throws Exception {
        // JsonUtils 从 SpringUtil 缓存 ObjectMapper；纯 Mockito 测试需先在短暂静态桩中预热。
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(ObjectMapper.class))
                .thenReturn(new ObjectMapper().findAndRegisterModules());
            Class.forName(JsonUtils.class.getName(), true, JsonUtils.class.getClassLoader());
        }
    }

    @Test
    void decisionDefaultsMatchCompetitionGuardrails() {
        AiProperties.DecisionConfig config = new AiProperties.DecisionConfig();
        assertTrue(config.isEnabled());
        assertEquals(4, config.getMaxConcurrentRuns());
        assertEquals(4, config.getMaxScenarios());
        assertEquals(90, config.getMaxHorizonDays());
        assertEquals(100, config.getRisk().pointSum());
        assertEquals(1.0, config.getRanking().weightSum(), 0.000001);
    }

    @Test
    void getConfigExposesDecisionGroupWithoutLeakingSettings() {
        var group = service.getConfig().getDecision();
        assertNotNull(group);
        assertEquals(30, group.getDefaultHorizonDays());
        assertEquals(0.30, group.getRanking().getTardinessWeight(), 0.000001);
        assertEquals(40, group.getRisk().getTardinessPoints());
    }

    @Test
    void invalidRankingSumFailsBeforeMutatingActiveConfig() {
        AiConfigBo bo = completeRequest();
        bo.getDecision().getRanking().setActionCostWeight(0.40);
        assertThrows(ServiceException.class, () -> service.updateConfig(bo));
        assertEquals(4, properties.getDecision().getMaxScenarios());
        verify(mapper, never()).insert(any(AiConfig.class));
    }

    @Test
    void invalidRiskPointsFailBeforeMutatingActiveConfig() {
        AiConfigBo bo = completeRequest();
        bo.getDecision().getRisk().setTardinessPoints(50);
        assertThrows(ServiceException.class, () -> service.updateConfig(bo));
        assertEquals(40, properties.getDecision().getRisk().getTardinessPoints());
        verify(mapper, never()).insert(any(AiConfig.class));
    }

    @Test
    void removingHighRiskApprovalIsRejectedBeforeAnyPersistence() {
        AiConfigBo bo = completeRequest();
        bo.getDecision().setRequireApprovalLevels(java.util.List.of("MEDIUM"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(ServiceException.class, () -> service.updateConfig(bo));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(mapper, never()).insert(any(AiConfig.class));
    }

    @Test
    void riskPointsCannotOverflowAndMasqueradeAsOneHundred() {
        AiConfigBo bo = completeRequest();
        bo.getDecision().getRisk().setTardinessPoints(Integer.MAX_VALUE);
        bo.getDecision().getRisk().setShortagePoints(Integer.MAX_VALUE);
        bo.getDecision().getRisk().setCapacityPoints(2);
        bo.getDecision().getRisk().setSupplierPoints(100);
        assertThrows(ServiceException.class, () -> service.updateConfig(bo));
        verify(mapper, never()).insert(any(AiConfig.class));
    }

    @Test
    void unsafeTimeoutOrKnowledgeIdIsRejectedBeforeAnyWrite() {
        AiConfigBo bo = completeRequest();
        bo.getDecision().setAnalysisTimeoutSeconds(0);
        assertThrows(ServiceException.class, () -> service.updateConfig(bo));
        bo.getDecision().setAnalysisTimeoutSeconds(60);
        bo.getDecision().setPolicyKnowledgeId(-2L);
        assertThrows(ServiceException.class, () -> service.updateConfig(bo));
        verify(mapper, never()).insert(any(AiConfig.class));
    }

    @Test
    void validDecisionConfigIsPersistedAndCanBeReloaded() {
        AiConfigBo bo = completeRequest();
        bo.getDecision().setMaxScenarios(3);
        when(mapper.selectById(any())).thenReturn(null);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateConfig(bo);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertEquals(3, properties.getDecision().getMaxScenarios());
        var captor = org.mockito.ArgumentCaptor.forClass(AiConfig.class);
        verify(mapper, times(6)).insert(captor.capture());
        AiConfig stored = captor.getAllValues().stream()
            .filter(c -> AiConfigServiceImpl.GROUP_DECISION.equals(c.getConfigGroup()))
            .findFirst().orElseThrow();
        assertTrue(stored.getConfigValue().contains("\"maxScenarios\":3"));
        properties.setDecision(new AiProperties.DecisionConfig());
        ReflectionTestUtils.invokeMethod(service, "applyGroup", AiConfigServiceImpl.GROUP_DECISION, stored.getConfigValue());
        assertEquals(3, service.getConfig().getDecision().getMaxScenarios());
    }

    @Test
    void decisionExecutorRejectsOverloadInsteadOfRunningOnHttpThread() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AiThreadPoolConfig().decisionExecutor();
        try {
            assertEquals(2, executor.getCorePoolSize());
            assertEquals(4, executor.getMaxPoolSize());
            assertEquals(20, executor.getThreadPoolExecutor().getQueue().remainingCapacity());
            assertTrue(executor.getThreadPoolExecutor().getRejectedExecutionHandler()
                instanceof ThreadPoolExecutor.AbortPolicy);
        } finally {
            executor.shutdown();
        }
    }

    private AiConfigBo completeRequest() {
        return new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .convertValue(service.getConfig(), AiConfigBo.class);
    }
}
