package com.nexus.common.ai.decision;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nexus.common.ai.domain.bo.AiDecisionRunBo;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.entity.AiDecisionEvidence;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.ai.domain.vo.AiDecisionActionVo;
import com.nexus.common.ai.domain.vo.AiDecisionEvidenceVo;
import com.nexus.common.ai.domain.vo.AiDecisionRunVo;
import com.nexus.common.ai.mapper.AiDecisionActionMapper;
import com.nexus.common.ai.mapper.AiDecisionEvidenceMapper;
import com.nexus.common.ai.mapper.AiDecisionRunMapper;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            Matcher createTable = Pattern.compile("CREATE TABLE `" + table + "` \\((.*?)\\) ENGINE=", Pattern.DOTALL)
                .matcher(sql);
            assertTrue(createTable.find(), table);
            Matcher columns = Pattern.compile("(?m)^  `([a-z0-9_]+)` ").matcher(createTable.group(1));
            Set<String> expected = new TreeSet<>();
            while (columns.find()) {
                expected.add(columns.group(1));
            }
            Set<String> actual = new TreeSet<>();
            Stream.concat(Arrays.stream(entity.getDeclaredFields()), Arrays.stream(BaseEntity.class.getDeclaredFields()))
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getAnnotation(TableField.class) == null || field.getAnnotation(TableField.class).exist())
                .forEach(field -> actual.add(field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT)));
            assertEquals(expected, actual, table);
        }
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
    private static void assertMapper(Class<?> mapper, Class<?> entity, Class<?> vo) {
        assertEquals(BaseMapperPlus.class, ((ParameterizedType) mapper.getGenericInterfaces()[0]).getRawType());
        var args = ((ParameterizedType) mapper.getGenericInterfaces()[0]).getActualTypeArguments();
        assertEquals(entity, args[0]);
        assertEquals(vo, args[1]);
    }
}
