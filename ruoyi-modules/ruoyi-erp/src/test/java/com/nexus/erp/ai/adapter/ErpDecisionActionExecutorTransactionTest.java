package com.nexus.erp.ai.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import com.nexus.erp.prd.mapper.PrdSchedulePlanMapper;
import com.nexus.erp.prd.service.IPrdScheduleService;
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import com.nexus.erp.pu.service.IPuPurchaseRequisitionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

/** 仅证明 execute 外层 Spring 本地事务可回滚同一 DataSource 写入；不把 H2 当 MySQL DDL 验证。 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ErpDecisionActionExecutorTransactionTest.Config.class)
@Tag("dev")
class ErpDecisionActionExecutorTransactionTest {

    @Autowired
    private ErpDecisionActionExecutor executor;
    @Autowired
    private IPuPurchaseRequisitionService purchaseService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabaseAndMocks() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS decision_tx_probe (id BIGINT PRIMARY KEY)");
        jdbcTemplate.update("DELETE FROM decision_tx_probe");
        Mockito.reset(purchaseService);
    }

    @Test
    void executeRollsBackCreatedErpRowWhenPostCreateRereadFails() {
        when(purchaseService.createDraft(any(PuPurchaseRequisitionBo.class))).thenAnswer(invocation -> {
            jdbcTemplate.update("INSERT INTO decision_tx_probe(id) VALUES (7001)");
            return 7001L;
        });

        assertThrows(RuntimeException.class, () -> executor.execute(purchaseAction(), actor()));
        Mockito.verify(purchaseService).selectByIdForDecision(7001L);

        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM decision_tx_probe", Integer.class));
    }

    private DecisionActionProposal purchaseAction() {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("materialId", "101");
        line.put("quantity", "5");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", "99");
        payload.put("requiredDate", "2026-09-30");
        payload.put("materialLines", List.of(line));
        return new DecisionActionProposal(1L, DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            "补货", "缺料", new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 42L, "SO-42"),
            payload, DecisionRiskLevel.HIGH, true, "pu:requisition:add", "tx-idem",
            "purchaseRequisitionDraft", 1, List.of());
    }

    private DecisionActor actor() {
        return new DecisionActor("000000", 7L, "tester", Set.of("pu:requisition:add"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:erp_decision_tx;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        IPuPurchaseRequisitionService purchaseService() {
            return Mockito.mock(IPuPurchaseRequisitionService.class);
        }

        @Bean
        IPrdScheduleService scheduleService() {
            return Mockito.mock(IPrdScheduleService.class);
        }

        @Bean
        PrdSchedulePlanMapper planMapper() {
            return Mockito.mock(PrdSchedulePlanMapper.class);
        }

        @Bean
        ErpDecisionActionExecutor executor(IPuPurchaseRequisitionService purchaseService,
                                           IPrdScheduleService scheduleService,
                                           PrdSchedulePlanMapper planMapper) {
            return new ErpDecisionActionExecutor(purchaseService, scheduleService, planMapper,
                Mockito.mock(DecisionPurchaseDraftEnricher.class),
                Mockito.mock(ErpDecisionPurchaseGate.class));
        }
    }
}
