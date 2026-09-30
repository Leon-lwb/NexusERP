package com.nexus.common.ai.decision;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.nexus.common.ai.domain.bo.AiDecisionRunBo;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.entity.AiDecisionActionToAiDecisionActionVoMapperImpl;
import com.nexus.common.ai.domain.entity.AiDecisionEvidence;
import com.nexus.common.ai.domain.entity.AiDecisionEvidenceToAiDecisionEvidenceVoMapperImpl;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.ai.domain.entity.AiDecisionRunToAiDecisionRunVoMapperImpl;
import com.nexus.common.ai.domain.vo.AiDecisionActionVo;
import com.nexus.common.ai.domain.vo.AiDecisionEvidenceVo;
import com.nexus.common.ai.domain.vo.AiDecisionRunVo;
import com.nexus.common.ai.mapper.AiDecisionActionMapper;
import com.nexus.common.ai.mapper.AiDecisionEvidenceMapper;
import com.nexus.common.ai.mapper.AiDecisionRunMapper;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import io.github.linpeilie.Converter;
import io.github.linpeilie.mapstruct.SpringConverterFactory;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("local")
@Tag("dev")
@Tag("prod")
class DecisionEntityContractTest {

    private static final List<Class<?>> ENTITIES = List.of(AiDecisionRun.class, AiDecisionEvidence.class,
        AiDecisionAction.class);

    @Test
    void tableNamesAndPrimaryKeysMatchDdl() throws NoSuchFieldException {
        assertEquals("ai_decision_run", AiDecisionRun.class.getAnnotation(TableName.class).value());
        assertEquals("ai_decision_evidence", AiDecisionEvidence.class.getAnnotation(TableName.class).value());
        assertEquals("ai_decision_action", AiDecisionAction.class.getAnnotation(TableName.class).value());
        for (Class<?> entity : ENTITIES) {
            assertEquals(BaseEntity.class, entity.getSuperclass());
            assertEquals("id", entity.getDeclaredField("id").getAnnotation(TableId.class).value());
        }
    }

    @Test
    void tenantIdRemainsExactStringWithLeadingZeros() throws NoSuchFieldException {
        for (Class<?> entity : ENTITIES) {
            assertEquals(String.class, entity.getDeclaredField("tenantId").getType(), entity.getName());
        }
    }

    @Test
    void mutableStateEntitiesUseOptimisticVersionButEvidenceDoesNot() throws NoSuchFieldException {
        for (Class<?> entity : List.of(AiDecisionRun.class, AiDecisionAction.class)) {
            Field version = entity.getDeclaredField("version");
            assertEquals(Integer.class, version.getType());
            assertNotNull(version.getAnnotation(Version.class));
        }
        assertFalse(Arrays.stream(AiDecisionEvidence.class.getDeclaredFields())
            .anyMatch(field -> field.getName().equals("version")));
    }

    @Test
    void softDeleteAndReservedColumnsAreDeclaredOnEveryEntity() throws NoSuchFieldException {
        for (Class<?> entity : ENTITIES) {
            Field delFlag = entity.getDeclaredField("delFlag");
            assertEquals(String.class, delFlag.getType());
            assertNotNull(delFlag.getAnnotation(TableLogic.class));
            for (int index = 1; index <= 4; index++) {
                assertEquals(String.class, entity.getDeclaredField("attr" + index).getType());
            }
        }
    }

    @Test
    void allDdlColumnsAreMappedExactlyOnceWithoutMapFields() throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.exists(directory.resolve("script/sql/ry-vue.sql"))) {
            directory = directory.getParent();
        }
        assertNotNull(directory, "找不到数据库主脚本");
        String sql = Files.readString(directory.resolve("script/sql/ry-vue.sql"));
        for (Class<?> entity : ENTITIES) {
            String table = entity.getAnnotation(TableName.class).value();
            Matcher createTable = decisionTableDdl(sql, table);
            Matcher columns = Pattern.compile("(?m)^  `([a-z0-9_]+)` ").matcher(createTable.group(1));
            List<String> expected = new ArrayList<>();
            while (columns.find()) {
                expected.add(columns.group(1));
            }
            List<String> actual = new ArrayList<>();
            Stream.concat(Arrays.stream(entity.getDeclaredFields()), Arrays.stream(BaseEntity.class.getDeclaredFields()))
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getAnnotation(TableField.class) == null || field.getAnnotation(TableField.class).exist())
                .forEach(field -> actual.add(field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT)));
            assertSameColumns(expected, actual, table);
        }
    }

    @Test
    void columnContractMustExposeRepeatedNamesOnEitherSide() {
        org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class,
            () -> assertSameColumns(List.of("id", "id"), List.of("id"), "duplicated DDL"));
        org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class,
            () -> assertSameColumns(List.of("id"), List.of("id", "id"), "duplicated entity"));
    }
    @Test
    void fieldTypesMatchQueryAndDisplayContracts() throws NoSuchFieldException {
        assertEquals(LocalDateTime.class, AiDecisionRun.class.getDeclaredField("snapshotTime").getType());
        assertEquals(java.math.BigDecimal.class, AiDecisionRun.class.getDeclaredField("riskScore").getType());
        assertEquals(java.math.BigDecimal.class, AiDecisionEvidence.class.getDeclaredField("confidence").getType());
        assertEquals(String.class, AiDecisionEvidence.class.getDeclaredField("derivedFromJson").getType());
        assertEquals(String.class, AiDecisionAction.class.getDeclaredField("dependencyIdsJson").getType());
        assertEquals(LocalDateTime.class, AiDecisionAction.class.getDeclaredField("approvedAt").getType());
        assertEquals(Long.class, AiDecisionRunBo.class.getDeclaredField("createBy").getType());
        assertEquals(LocalDateTime.class, AiDecisionRunBo.class.getDeclaredField("beginTime").getType());
        assertEquals(LocalDateTime.class, AiDecisionRunBo.class.getDeclaredField("endTime").getType());
        for (Class<?> vo : List.of(AiDecisionRunVo.class, AiDecisionEvidenceVo.class, AiDecisionActionVo.class)) {
            assertTrue(Serializable.class.isAssignableFrom(vo));
            assertEquals(Long.class, vo.getDeclaredField("id").getType());
        }
        assertEquals(String.class, AiDecisionRunVo.class.getDeclaredField("resultJson").getType());
        assertEquals(String.class, AiDecisionRunVo.class.getDeclaredField("outcomeJson").getType());
        assertNull(Arrays.stream(AiDecisionEvidenceVo.class.getDeclaredFields())
            .filter(field -> field.getName().equals("tenantId")).findAny().orElse(null));
        assertNull(Arrays.stream(AiDecisionActionVo.class.getDeclaredFields())
            .filter(field -> field.getName().equals("tenantId")).findAny().orElse(null));
    }

    @Test
    void mappersBindExpectedEntityAndVoTypes() {
        assertMapper(AiDecisionRunMapper.class, AiDecisionRun.class, AiDecisionRunVo.class);
        assertMapper(AiDecisionEvidenceMapper.class, AiDecisionEvidence.class, AiDecisionEvidenceVo.class);
        assertMapper(AiDecisionActionMapper.class, AiDecisionAction.class, AiDecisionActionVo.class);
    }

    @Test
    void runListProjectionMustNotFetchAuditJson() {
        QueryWrapper<AiDecisionRun> query = AiDecisionRunMapper.summarySelection(new QueryWrapper<>());
        String selected = query.getSqlSelect();
        assertNotNull(selected);
        assertTrue(selected.contains("risk_score"));
        assertTrue(selected.contains("recommended_scenario_code"));
        for (String bigColumn : List.of("input_snapshot_json", "score_config_json", "scenario_result_json",
            "narrative_json", "result_json", "outcome_json")) {
            assertFalse(Arrays.asList(selected.split(",")).contains(bigColumn), bigColumn);
        }
    }

    @Test
    void actionAndEvidenceListProjectionsSkipLargeJson() {
        String evidenceColumns = AiDecisionEvidenceMapper.summarySelection(new QueryWrapper<>()).getSqlSelect();
        assertTrue(evidenceColumns.contains("metric_value"));
        assertFalse(Arrays.asList(evidenceColumns.split(",")).contains("derived_from_json"));

        String actionColumns = AiDecisionActionMapper.summarySelection(new QueryWrapper<>()).getSqlSelect();
        assertTrue(actionColumns.contains("approval_status"));
        assertFalse(Arrays.asList(actionColumns.split(",")).contains("payload_json"));
        assertFalse(Arrays.asList(actionColumns.split(",")).contains("result_json"));
    }

    @Test
    void decisionH2DatabaseMustReleaseTablesBeforeReusingSameName() throws Exception {
        String url = "jdbc:h2:mem:decision_reuse_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        for (int attempt = 0; attempt < 2; attempt++) {
            var datasource = new DriverManagerDataSource(url, "sa", "");
            try {
                createDecisionTables(datasource);
                try (Connection connection = datasource.getConnection();
                     Statement statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT COUNT(*) FROM ai_decision_run")) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1));
                }
            } finally {
                shutdownDecisionDatabase(datasource);
            }
        }
    }

    @Test
    void evidenceKeyMustBeUniqueWithinRunInH2() throws Exception {
        var datasource = new DriverManagerDataSource(
            "jdbc:h2:mem:decision_evidence_unique_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try {
            createDecisionTables(datasource);
            try (Connection connection = datasource.getConnection(); Statement statement = connection.createStatement()) {
                String insert = "INSERT INTO ai_decision_evidence "
                    + "(id, run_id, evidence_key, source_domain, source_type, metric_code, metric_name, value_type) "
                    + "VALUES (%d, %d, 'same-key', 'ERP', 'ORDER', 'score', 'Score', 'NUMBER')";
                assertEquals(1, statement.executeUpdate(insert.formatted(1, 100)));
                assertEquals(1, statement.executeUpdate(insert.formatted(2, 101)));
                SQLException duplicate = assertThrows(SQLException.class,
                    () -> statement.executeUpdate(insert.formatted(3, 100)));
                assertEquals("23505", duplicate.getSQLState());
            }
        } finally {
            shutdownDecisionDatabase(datasource);
        }
    }

    @Test
    void actionIdempotencyKeyMustBeUniqueWithinTenantInH2() throws Exception {
        var datasource = new DriverManagerDataSource(
            "jdbc:h2:mem:decision_action_unique_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try {
            createDecisionTables(datasource);
            try (Connection connection = datasource.getConnection(); Statement statement = connection.createStatement()) {
                String insert = "INSERT INTO ai_decision_action "
                    + "(id, tenant_id, run_id, action_type, title, payload_json, risk_level, "
                    + "approval_status, execution_status, executor_code, idempotency_key) "
                    + "VALUES (%d, '%s', 100, 'NOTIFY', 'notify', '{}', 'LOW', 'PENDING', 'PENDING', 'TEST', 'same-key')";
                assertEquals(1, statement.executeUpdate(insert.formatted(1, "000000")));
                assertEquals(1, statement.executeUpdate(insert.formatted(2, "000001")));
                SQLException duplicate = assertThrows(SQLException.class,
                    () -> statement.executeUpdate(insert.formatted(3, "000000")));
                assertEquals("23505", duplicate.getSQLState());
            }
        } finally {
            shutdownDecisionDatabase(datasource);
        }
    }

    @Test
    void uninitializedVersionsMustStillGuardStaleUpdates() throws Exception {
        var datasource = new DriverManagerDataSource(
            "jdbc:h2:mem:decision_version_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try {
            verifyUninitializedVersionCas(datasource);
        } finally {
            shutdownDecisionDatabase(datasource);
        }
    }

    private static void verifyUninitializedVersionCas(DriverManagerDataSource datasource) throws Exception {
        createDecisionTables(datasource);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        var factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(datasource);
        factoryBean.setPlugins(interceptor);
        factoryBean.afterPropertiesSet();
        SqlSessionFactory factory = factoryBean.getObject();
        assertNotNull(factory);
        factory.getConfiguration().addMapper(AiDecisionRunMapper.class);
        factory.getConfiguration().addMapper(AiDecisionActionMapper.class);

        try (SqlSession session = factory.openSession(true)) {
            var runs = session.getMapper(AiDecisionRunMapper.class);
            var actions = session.getMapper(AiDecisionActionMapper.class);
            var run = new AiDecisionRun();
            run.setId(120L);
            run.setTenantId("000000");
            run.setSceneCode("DELIVERY_RISK");
            run.setBizType("ORDER");
            run.setTriggerType("MANUAL");
            run.setStatus("INITIAL");
            assertEquals(1, runs.insert(run)); /** 刻意不调用 setVersion。 */
            Integer insertedRunVersion = run.getVersion();
            var runWinner = runs.selectById(run.getId());
            assertEquals(0, runWinner.getVersion()); /** 读取时应能观察到数据库默认版本。 */
            runWinner.setStatus("WINNER");
            assertEquals(1, runs.updateById(runWinner));
            assertEquals(1, runWinner.getVersion());
            run.setStatus("STALE");
            int staleRunUpdate = runs.updateById(run);

            var action = new AiDecisionAction();
            action.setId(320L);
            action.setTenantId("000000");
            action.setRunId(run.getId());
            action.setActionType("NOTIFY");
            action.setTitle("notify");
            action.setPayloadJson("{}");
            action.setRiskLevel("LOW");
            action.setApprovalStatus("PENDING");
            action.setExecutionStatus("INITIAL");
            action.setExecutorCode("TEST");
            action.setIdempotencyKey("action-320");
            assertEquals(1, actions.insert(action)); /** 刻意不调用 setVersion。 */
            Integer insertedActionVersion = action.getVersion();
            var actionWinner = actions.selectById(action.getId());
            assertEquals(0, actionWinner.getVersion());
            actionWinner.setExecutionStatus("WINNER");
            assertEquals(1, actions.updateById(actionWinner));
            assertEquals(1, actionWinner.getVersion());
            action.setExecutionStatus("STALE");
            int staleActionUpdate = actions.updateById(action);

            org.junit.jupiter.api.Assertions.assertAll("new entities must retain their optimistic lock versions",
                () -> assertEquals(0, insertedRunVersion, "run.version after insert"),
                () -> assertEquals(0, staleRunUpdate, "stale run update count"),
                () -> assertEquals("WINNER", runs.selectById(run.getId()).getStatus()),
                () -> assertEquals(0, insertedActionVersion, "action.version after insert"),
                () -> assertEquals(0, staleActionUpdate, "stale action update count"),
                () -> assertEquals("WINNER", actions.selectById(action.getId()).getExecutionStatus()));
        }
    }

    @Test
    void mapperInsertSelectUpdateAndSummaryProjectionRoundTrip() throws Exception {
        /** H2 在 MySQL 兼容模式下验证映射 SQL 和类型；真实 MySQL 方言仍需任务 19 验收。 */
        var datasource = new DriverManagerDataSource(
            "jdbc:h2:mem:decision_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try {
            verifyMapperRoundTrip(datasource);
        } finally {
            shutdownDecisionDatabase(datasource);
        }
    }

    private static void verifyMapperRoundTrip(DriverManagerDataSource datasource) throws Exception {
        createDecisionTables(datasource);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        var factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(datasource);
        factoryBean.setPlugins(interceptor);
        factoryBean.afterPropertiesSet();
        SqlSessionFactory factory = factoryBean.getObject();
        assertNotNull(factory);
        factory.getConfiguration().addMapper(AiDecisionRunMapper.class);
        factory.getConfiguration().addMapper(AiDecisionEvidenceMapper.class);
        factory.getConfiguration().addMapper(AiDecisionActionMapper.class);

        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(SpringUtils.class);
            context.registerBean(Converter.class, () -> new Converter(new SpringConverterFactory(context)));
            context.registerBean(AiDecisionRunToAiDecisionRunVoMapperImpl.class);
            context.registerBean(AiDecisionEvidenceToAiDecisionEvidenceVoMapperImpl.class);
            context.registerBean(AiDecisionActionToAiDecisionActionVoMapperImpl.class);
            context.refresh();
            try (SqlSession session = factory.openSession(true)) {
                AiDecisionRunMapper runs = session.getMapper(AiDecisionRunMapper.class);
                AiDecisionEvidenceMapper evidences = session.getMapper(AiDecisionEvidenceMapper.class);
                AiDecisionActionMapper actions = session.getMapper(AiDecisionActionMapper.class);

                AiDecisionRun run = new AiDecisionRun();
                run.setId(100L);
                run.setTenantId("000000");
                run.setSceneCode("DELIVERY_RISK");
                run.setBizType("ORDER");
                run.setTriggerType("MANUAL");
                run.setStatus("RUNNING");
                run.setRiskScore(new java.math.BigDecimal("12.34"));
                run.setRecommendedScenarioCode("scenario-1");
                run.setInputSnapshotJson("{\"x\":1}");
                run.setScenarioResultJson("{\"scenario\":true}");
                run.setResultJson("{\"outcome\":true}");
                run.setVersion(0);
                assertEquals(1, runs.insert(run));
                AiDecisionRun readRun = runs.selectById(100L);
                assertEquals("000000", readRun.getTenantId());
                assertEquals(run.getInputSnapshotJson(), readRun.getInputSnapshotJson());
                assertNull(readRun.getScoreConfigJson());
                assertEquals(run.getResultJson(), readRun.getResultJson());
                assertNull(readRun.getOutcomeJson());
                readRun.setOutcomeJson("{\"done\":true}");
                readRun.setStatus("FINISHED");
                assertEquals(1, runs.updateById(readRun));
                assertEquals(1, readRun.getVersion());
                AiDecisionRun stale = new AiDecisionRun();
                stale.setId(100L);
                stale.setVersion(0);
                stale.setStatus("STALE_WRITE");
                assertEquals(0, runs.updateById(stale));
                assertEquals("FINISHED", runs.selectById(100L).getStatus());
                assertEquals("{\"done\":true}", runs.selectById(100L).getOutcomeJson());

                AiDecisionRun nullableRun = new AiDecisionRun();
                nullableRun.setId(101L);
                nullableRun.setTenantId("000000");
                nullableRun.setSceneCode("DELIVERY_RISK");
                nullableRun.setBizType("ORDER");
                nullableRun.setTriggerType("MANUAL");
                nullableRun.setStatus("PENDING");
                nullableRun.setVersion(0);
                assertEquals(1, runs.insert(nullableRun));
                assertNull(runs.selectById(101L).getResultJson());
                var runPage = runs.selectSummaryPage(new Page<AiDecisionRun>(1, 10),
                    new QueryWrapper<AiDecisionRun>().eq("tenant_id", "000000"));
                assertEquals(2, runPage.getTotal());
                assertEquals(2, runPage.getRecords().size());
                AiDecisionRunVo summary = runPage.getRecords().stream()
                    .filter(vo -> vo.getId() == 100L).findFirst().orElseThrow();
                assertEquals("000000", summary.getTenantId());
                assertEquals("scenario-1", summary.getRecommendedScenarioCode());
                assertNull(summary.getResultJson());
                assertNull(summary.getOutcomeJson());

                AiDecisionEvidence evidence = new AiDecisionEvidence();
                evidence.setId(200L);
                evidence.setTenantId("000000");
                evidence.setRunId(100L);
                evidence.setEvidenceKey("ev1");
                evidence.setSourceDomain("ERP");
                evidence.setSourceType("ORDER");
                evidence.setMetricCode("DELAY");
                evidence.setMetricName("Delay");
                evidence.setValueType("NUMBER");
                evidence.setDerivedFromJson("[\"ev0\"]");
                assertEquals(1, evidences.insert(evidence));
                assertEquals("000000", evidences.selectById(200L).getTenantId());
                assertEquals("[\"ev0\"]", evidences.selectById(200L).getDerivedFromJson());
                evidence.setDerivedFromJson("[\"ev2\"]");
                assertEquals(1, evidences.updateById(evidence));
                assertEquals("[\"ev2\"]", evidences.selectById(200L).getDerivedFromJson());
                AiDecisionEvidence nullableEvidence = new AiDecisionEvidence();
                nullableEvidence.setId(201L);
                nullableEvidence.setTenantId("000000");
                nullableEvidence.setRunId(100L);
                nullableEvidence.setEvidenceKey("ev2");
                nullableEvidence.setSourceDomain("ERP");
                nullableEvidence.setSourceType("ORDER");
                nullableEvidence.setMetricCode("DELAY");
                nullableEvidence.setMetricName("Delay");
                nullableEvidence.setValueType("NUMBER");
                assertEquals(1, evidences.insert(nullableEvidence));
                assertNull(evidences.selectById(201L).getDerivedFromJson());
                var evidencePage = evidences.selectSummaryPage(new Page<AiDecisionEvidence>(1, 10),
                    new QueryWrapper<AiDecisionEvidence>().eq("tenant_id", "000000"));
                assertEquals(2, evidencePage.getRecords().size());
                assertNull(evidencePage.getRecords().stream().filter(vo -> vo.getId() == 200L)
                    .findFirst().orElseThrow().getDerivedFromJson());

                AiDecisionAction action = new AiDecisionAction();
                action.setId(300L);
                action.setTenantId("000000");
                action.setRunId(100L);
                action.setActionType("NOTIFY");
                action.setTitle("notify");
                action.setPayloadJson("{\"k\":\"v\"}");
                action.setRiskLevel("LOW");
                action.setApprovalStatus("PENDING");
                action.setExecutionStatus("PENDING");
                action.setExecutorCode("TEST");
                action.setIdempotencyKey("action-300");
                action.setResultJson("{\"ok\":true}");
                action.setVersion(0);
                assertEquals(1, actions.insert(action));
                AiDecisionAction readAction = actions.selectById(300L);
                assertEquals("000000", readAction.getTenantId());
                assertEquals(action.getPayloadJson(), readAction.getPayloadJson());
                assertEquals(action.getResultJson(), readAction.getResultJson());
                readAction.setExecutionStatus("DONE");
                readAction.setResultJson(null); /** 选择性更新必须保留已有非空 JSON。 */
                assertEquals(1, actions.updateById(readAction));
                assertEquals(1, readAction.getVersion());
                assertEquals(action.getResultJson(), actions.selectById(300L).getResultJson());
                AiDecisionAction staleAction = new AiDecisionAction();
                staleAction.setId(300L);
                staleAction.setVersion(0);
                staleAction.setExecutionStatus("STALE_WRITE");
                assertEquals(0, actions.updateById(staleAction));
                assertEquals("DONE", actions.selectById(300L).getExecutionStatus());
                AiDecisionAction nullableAction = new AiDecisionAction();
                nullableAction.setId(301L);
                nullableAction.setTenantId("000000");
                nullableAction.setRunId(100L);
                nullableAction.setActionType("NOTIFY");
                nullableAction.setTitle("notify-2");
                nullableAction.setPayloadJson("{}"); /** 数据库定义中该字段禁止为空。 */
                nullableAction.setRiskLevel("LOW");
                nullableAction.setApprovalStatus("PENDING");
                nullableAction.setExecutionStatus("PENDING");
                nullableAction.setExecutorCode("TEST");
                nullableAction.setIdempotencyKey("action-301");
                nullableAction.setVersion(0);
                assertEquals(1, actions.insert(nullableAction));
                assertNull(actions.selectById(301L).getResultJson());
                var actionPage = actions.selectSummaryPage(new Page<AiDecisionAction>(1, 10),
                    new QueryWrapper<AiDecisionAction>().eq("tenant_id", "000000"));
                assertEquals(2, actionPage.getRecords().size());
                AiDecisionActionVo actionSummary = actionPage.getRecords().stream()
                    .filter(vo -> vo.getId() == 300L).findFirst().orElseThrow();
                assertNull(actionSummary.getPayloadJson());
                assertNull(actionSummary.getResultJson());
            }
        }
    }

    private static void shutdownDecisionDatabase(DriverManagerDataSource datasource) throws Exception {
        try (Connection connection = datasource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    private static void createDecisionTables(DriverManagerDataSource datasource) throws Exception {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.exists(directory.resolve("script/sql/ry-vue.sql"))) {
            directory = directory.getParent();
        }
        assertNotNull(directory);
        String sql = Files.readString(directory.resolve("script/sql/ry-vue.sql"));
        try (Connection connection = datasource.getConnection(); Statement statement = connection.createStatement()) {
            for (String table : List.of("ai_decision_run", "ai_decision_evidence", "ai_decision_action")) {
                Matcher ddl = decisionTableDdl(sql, table);
                /** 仅转换 MySQL 方言写法；保留生产列、类型、空值约束和默认值供 H2 验证。 */
                List<String> columns = Arrays.stream(ddl.group(1).split("\\R"))
                    .map(String::trim)
                    .filter(line -> line.startsWith("`"))
                    .map(line -> line.replaceFirst(",?$", "")
                        .replaceAll("(?i)\\b(bigint|int)\\(\\d+\\)", "$1")
                        .replaceAll("(?i)\\s+CHARACTER SET\\s+\\w+(?:\\s+COLLATE\\s+\\w+)?", "")
                        .replaceAll("(?i)\\s+COMMENT\\s+'(?:''|[^'])*'", ""))
                    .toList();
                List<String> uniqueConstraints = Arrays.stream(ddl.group(1).split("\\R"))
                    .map(String::trim)
                    .filter(line -> Pattern.compile("(?i)^UNIQUE\\s+(?:KEY|INDEX)\\b").matcher(line).find())
                    .map(line -> {
                        Matcher index = Pattern.compile("(?i)^UNIQUE\\s+(?:KEY|INDEX)\\s+(`[^`]+`)\\s*\\(([^)]+)\\)(?:\\s+USING\\s+BTREE)?\\s*,?$")
                            .matcher(line);
                        assertTrue(index.matches(), "无法解析唯一索引: " + line);
                        return "CONSTRAINT " + index.group(1) + " UNIQUE (" + index.group(2) + ")";
                    })
                    .toList();
                List<String> definitions = new ArrayList<>(columns);
                definitions.add("PRIMARY KEY (`id`)");
                definitions.addAll(uniqueConstraints);
                statement.execute("CREATE TABLE `" + table + "` (" + String.join(",", definitions) + ")");
            }
        }
    }

    private static Matcher decisionTableDdl(String sql, String table) {
        Matcher ddl = Pattern.compile("(?ms)^CREATE TABLE `" + Pattern.quote(table)
            + "`\\s*\\(((?:(?!^CREATE TABLE\\b).)*?)^\\)\\s*ENGINE\\s*=").matcher(sql);
        assertTrue(ddl.find(), table);
        return ddl;
    }

    private static void assertSameColumns(List<String> expected, List<String> actual, String table) {
        assertEquals(expected.stream().sorted().toList(), actual.stream().sorted().toList(), table);
    }

    private static void assertMapper(Class<?> mapper, Class<?> entity, Class<?> vo) {
        assertEquals(BaseMapperPlus.class, ((ParameterizedType) mapper.getGenericInterfaces()[0]).getRawType());
        var args = ((ParameterizedType) mapper.getGenericInterfaces()[0]).getActualTypeArguments();
        assertEquals(entity, args[0]);
        assertEquals(vo, args[1]);
    }
}
