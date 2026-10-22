package com.nexus.ai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.hutool.extra.spring.SpringUtil;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.config.impl.AiConfigServiceImpl;
import com.nexus.ai.service.query.ErpTableSchemaManager;
import com.nexus.ai.service.rag.VectorStoreService;
import com.nexus.common.ai.domain.bo.AiConfigBo;
import com.nexus.common.ai.domain.entity.AiConfig;
import com.nexus.common.ai.mapper.AiConfigMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.redis.utils.RedisUtils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

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
            spring.when(() -> SpringUtil.getBean(RedissonClient.class)).thenReturn(mock(RedissonClient.class));
            Class.forName(JsonUtils.class.getName(), true, JsonUtils.class.getClassLoader());
            Class.forName(RedisUtils.class.getName(), true, RedisUtils.class.getClassLoader());
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
        bo.getDecision().setRequireApprovalLevels(List.of("MEDIUM"));
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
    void nullApprovalLevelIsRejectedAsServiceErrorBeforePersistence() {
        AiConfigBo bo = completeRequest();
        bo.getDecision().setRequireApprovalLevels(Arrays.asList("HIGH", "CRITICAL", null));
        assertThrows(ServiceException.class, () -> service.updateConfig(bo));
        verify(mapper, never()).insert(any(AiConfig.class));
    }

    @Test
    void databaseFailureDoesNotPublishUncommittedDecisionConfig() {
        AiConfigBo bo = completeRequest();
        bo.getDecision().setMaxScenarios(3);
        when(mapper.selectById(any())).thenReturn(null);
        doAnswer(invocation -> {
            AiConfig config = invocation.getArgument(0);
            if (AiConfigServiceImpl.GROUP_DECISION.equals(config.getConfigGroup())) {
                throw new IllegalStateException("db down");
            }
            return 1;
        }).when(mapper).insert(any(AiConfig.class));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(IllegalStateException.class, () -> service.updateConfig(bo));
            assertEquals(4, properties.getDecision().getMaxScenarios());
            assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void decisionConfigIsPublishedOnlyAfterCommitAndCannotBeMutatedThroughGetterOrVo() {
        AiConfigBo bo = completeRequest();
        bo.getDecision().setMaxScenarios(3);
        bo.getDecision().setRequireApprovalLevels(new ArrayList<>(List.of("HIGH", "CRITICAL")));
        when(mapper.selectById(any())).thenReturn(null);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateConfig(bo);
            assertEquals(4, properties.getDecision().getMaxScenarios(), "提交前不得发布新配置");
            commitRegisteredSynchronizations();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertEquals(3, properties.getDecision().getMaxScenarios());
        assertThrows(UnsupportedOperationException.class,
            () -> properties.getDecision().getRequireApprovalLevels().clear());
        var voLevels = service.getConfig().getDecision().getRequireApprovalLevels();
        voLevels.clear();
        assertTrue(properties.getDecision().getRequireApprovalLevels().contains("HIGH"));
    }

    @Test
    void validDecisionConfigIsPersistedAndCanBeReloaded() {
        AiConfigBo bo = completeRequest();
        bo.getDecision().setMaxScenarios(3);
        when(mapper.selectById(any())).thenReturn(null);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateConfig(bo);
            assertEquals(4, properties.getDecision().getMaxScenarios());
            commitRegisteredSynchronizations();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertEquals(3, properties.getDecision().getMaxScenarios());
        var captor = ArgumentCaptor.forClass(AiConfig.class);
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

    private static void commitRegisteredSynchronizations() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertFalse(synchronizations.isEmpty());
        synchronizations.forEach(sync -> sync.beforeCommit(false));
        synchronizations.forEach(TransactionSynchronization::beforeCompletion);
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
            redis.verify(
                    () ->
                            RedisUtils.publish(
                                    ArgumentMatchers.eq("ai:config:refresh"),
                                    ArgumentMatchers.anyString()));
        }
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private AiConfigBo completeRequest() {
        return new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .convertValue(service.getConfig(), AiConfigBo.class);
    }
}
